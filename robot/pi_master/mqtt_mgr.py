import paho.mqtt.client as mqtt
import json
import logging
import os
from dotenv import load_dotenv
import ssl

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class MqttManager:
    def __init__(self):
        load_dotenv()
        self.broker = os.getenv('MQTT_BROKER')
        self.port = int(os.getenv('MQTT_PORT', 8883))
        self.username = os.getenv('MQTT_USERNAME')
        self.password = os.getenv('MQTT_PASSWORD')
        
        self.client = mqtt.Client(client_id="agv_pi4_master", protocol=mqtt.MQTTv5)
        
        # TLS/SSL is required for HiveMQ Cloud on Port 8883
        self.client.tls_set(tls_version=ssl.PROTOCOL_TLS)
        
        if self.username and self.password:
            self.client.username_pw_set(self.username, self.password)
            
        self.on_new_order_callback = None
        self.on_command_callback = None
        
        # Pending orders memory
        self.pending_orders = []
        
        # Callbacks
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message

    def connect(self):
        try:
            logger.info(f"Connecting to MQTT Broker {self.broker}:{self.port}...")
            self.client.connect(self.broker, self.port, 60)
            self.client.loop_start() # Run network loop in background thread
            return True
        except Exception as e:
            logger.error(f"MQTT Connection failed: {e}")
            return False

    def _on_connect(self, client, userdata, flags, rc, properties=None):
        if rc == 0:
            logger.info("Connected to HiveMQ Cloud!")
            # Subscribe to command topics
            self.client.subscribe("agv/orders/pending", qos=1)
            self.client.subscribe("agv/commands", qos=1)
            logger.info("Subscribed to agv/orders/pending and agv/commands")
        else:
            logger.error(f"Failed to connect, return code {rc}")

    def _on_message(self, client, userdata, msg):
        topic = msg.topic
        payload = msg.payload.decode('utf-8')
        logger.info(f"MQTT Received [Topic: {topic}]: {payload}")
        
        try:
            data = json.loads(payload)
            if topic == "agv/orders/pending":
                if self.on_new_order_callback:
                    # In MQTT there's no continuous DB state, so when we receive a pending order
                    # we just assume it's fresh and push it to the callback immediately.
                    data['status'] = 'PENDING'
                    
                    # Store in memory so get_next_pending_order() works if AGV is busy
                    self.pending_orders.append(data)
                    
                    self.on_new_order_callback(data)
                    
            elif topic == "agv/commands":
                if self.on_command_callback:
                    self.on_command_callback(data)
        except json.JSONDecodeError:
            logger.error("Failed to parse MQTT message payload as JSON.")

    def start_listening(self, order_callback, cmd_callback=None):
        self.on_new_order_callback = order_callback
        self.on_command_callback = cmd_callback

    def update_order_status(self, order_id, status, extra_data=None):
        # Publish to agv/orders/status
        payload = {
            "order_id": order_id,
            "status": status
        }
        if extra_data:
            payload.update(extra_data)
            
        try:
            self.client.publish("agv/orders/status", json.dumps(payload), qos=1)
            logger.info(f"MQTT Published Status: {status} for order {order_id}")
            
            # If the order is DONE or Cancelled, remove it from our local pending memory
            if status in ['DONE', 'CANCELED_TIMEOUT']:
                self.pending_orders = [o for o in self.pending_orders if o.get('id') != order_id]
                
        except Exception as e:
            logger.error(f"Failed to publish status: {e}")

    def update_compartment_status(self, order_id, status):
        # Publish to agv/status/compartment1
        payload = {
            "order_id": order_id,
            "status": status
        }
        try:
            self.client.publish("agv/status/compartment1", json.dumps(payload), qos=1)
            logger.info(f"MQTT Published Compartment 1: {status}")
        except Exception as e:
            logger.error(f"Failed to publish compartment status: {e}")

    def update_location(self, lat, lon, battery=100.0, speed=0.0, heading=0.0, home_lat=None, home_lon=None, status=None, order_id=None):
        payload = {
            "lat": lat,
            "lon": lon,
            "battery": battery,
            "speed": speed,
            "heading": heading
        }
        if status is not None:
            payload["status"] = status
        if order_id is not None:
            payload["order_id"] = order_id
        if home_lat is not None and home_lon is not None:
            payload["homeLat"] = home_lat
            payload["homeLon"] = home_lon
        try:
            # QoS 0 is enough for frequent location updates
            self.client.publish("agv/location", json.dumps(payload), qos=0)
        except Exception as e:
            logger.error(f"Failed to publish location: {e}")

    def get_next_pending_order(self):
        # If AGV was busy and couldn't handle the order immediately, it grabs it from here
        if len(self.pending_orders) > 0:
            return self.pending_orders[0] # Return the oldest pending order
        return None
