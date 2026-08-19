import time
import logging
import os
import math
import dotenv
from dotenv import load_dotenv
from mqtt_mgr import MqttManager
from gps_nav import GpsNavigator
from uart_comm import UartComm

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

load_dotenv()

# Tọa độ trạm gốc sẽ được lấy tự động tại thời điểm khởi động (first valid GPS fix)
# Có thể cấu hình dự phòng trong file .env nếu cần
env_home_lat = os.getenv('HOME_LAT')
env_home_lon = os.getenv('HOME_LON')
HOME_LAT = float(env_home_lat) if env_home_lat else None
HOME_LON = float(env_home_lon) if env_home_lon else None
TIMEOUT_SECONDS = int(os.getenv('TIMEOUT_SECONDS', 600))

class AgvStateMachine:
    def __init__(self):
        self.mqtt = MqttManager()
        
        gps_port = os.getenv('PORT_GPS', '/dev/ttyUSB0')
        self.gps = GpsNavigator(port=gps_port, baudrate=115200)
        
        esp32_port = os.getenv('PORT_ESP32', '/dev/ttyACM0')
        self.uart = UartComm(port=esp32_port, baudrate=115200)
        
        # Thiết lập callback lắng nghe log từ ESP32 và đẩy lên MQTT
        self.uart.on_log_callback = lambda esp_msg: self.log_debug(f"[ESP32] {esp_msg}")
        
        self.state = 'INIT'
        
        # Single order variables
        self.current_order = None
        self.arrival_time = None
        self.is_returning = False
        self.token_gui = None
        self.token_nhan = None
        
        self.last_location_publish_time = 0

    def log_debug(self, msg):
        """Hàm ghi log hợp nhất: In ra màn hình console của Pi 4 đồng thời publish lên MQTT debug topic"""
        formatted_msg = f"[{self.state}] {msg}"
        logger.info(formatted_msg)
        try:
            # Publish QoS 0 để truyền nhanh, không bị nghẽn mạng
            self.mqtt.client.publish("agv/debug/logs", formatted_msg, qos=0)
        except Exception as e:
            pass

    def start(self):
        if not self.mqtt.connect() or not self.uart.connect() or not self.gps.start_gps():
            logger.error("Initialization failed.")
            return

        self.mqtt.start_listening(self.on_new_order, self.on_agv_command)
        self.log_debug("System Ready. Waiting for commands...")
        try:
            self._run_loop()
        except KeyboardInterrupt:
            self.uart.disconnect()

    def on_new_order(self, order):
        if self.state in ['IDLE', 'ROUTING_TO_HOME', 'RETURNING'] and order:
            self.log_debug(f"New order {order['id']} received. Navigating to Sender.")
            self.current_order = order
            
            # Start routing to sender
            sender_lat = float(self.current_order.get('sender_lat', 0))
            sender_lon = float(self.current_order.get('sender_lon', 0))
            
            self.log_debug(f"Routing to Sender: {sender_lat}, {sender_lon}")
            if self.gps.fetch_route(sender_lat, sender_lon):
                self.mqtt.update_order_status(self.current_order['id'], 'GOING_TO_SENDER')
                self.state = 'GOING_TO_SENDER'
            else:
                self.log_debug("Failed to fetch route to Sender. State remains IDLE for retry.")

    def on_agv_command(self, cmd_data):
        action = cmd_data.get("action")
        if action == "force_state":
            new_state = cmd_data.get("state")
            if new_state:
                self.state = new_state
                # Gán giá trị mặc định để tránh crash nếu user nhảy tắt (skip state)
                self.arrival_time = time.time()
                if self.current_order is None:
                    self.current_order = {
                        'id': 'DUMMY_ORDER',
                        'sender_lat': self.gps.current_lat,
                        'sender_lon': self.gps.current_lon,
                        'recv_lat': self.gps.current_lat,
                        'recv_lon': self.gps.current_lon
                    }
                self.log_debug(f"Forced State to {new_state} from Web Admin")
        elif action == "ble_on":
            token = cmd_data.get("token", "TEST_TOKEN_123")
            self.uart.send_ble_on(token)
            self.log_debug(f"Testing BLE ON with token {token}")
        elif action == "ble_off":
            self.uart.send_ble_off()
            self.log_debug("Testing BLE OFF")
        elif action == "override_gps":
            disable = cmd_data.get("disable", False)
            if disable:
                self.gps.override_active = False
                self.log_debug("GPS Override disabled, using real RTK data.")
            else:
                lat = float(cmd_data.get("lat", 0.0))
                lon = float(cmd_data.get("lon", 0.0))
                self.gps.force_location(lat, lon)
                self.log_debug(f"GPS Overridden & Teleported to: {lat}, {lon}")
        elif action == "test_rotate":
            angle = float(cmd_data.get("angle", 90.0))
            self.target_test_angle = angle
            self.state = 'TEST_ROTATION'
            self.log_debug(f"Force testing rotation to {angle} degrees")
        elif action == "cancel_test_rotate":
            if self.state == 'TEST_ROTATION':
                self.state = 'IDLE'
                self.uart.send_speed(0, 0)
                self.log_debug("Cancelled Rotation Test. Returned to IDLE.")
        elif action == "set_home":
            global HOME_LAT, HOME_LON
            lat = float(cmd_data.get("lat", 0.0))
            lon = float(cmd_data.get("lon", 0.0))
            HOME_LAT = lat
            HOME_LON = lon
            
            # Persist to .env
            env_path = os.path.join(os.path.dirname(__file__), '.env')
            dotenv.set_key(env_path, "HOME_LAT", str(lat))
            dotenv.set_key(env_path, "HOME_LON", str(lon))
            
            self.log_debug(f"Home location permanently set to: {lat}, {lon}")
        elif action == "CANCEL_TASK":
            task_id = cmd_data.get("id")
            if self.current_order and self.current_order.get('id') == task_id:
                self.log_debug(f"Received CANCEL_TASK for active task {task_id}. Stopping in place and routing home.")
                self.uart.send_speed(0, 0) # Dừng lại tại chỗ ngay lập tức
                self.mqtt.update_order_status(task_id, 'CANCELLED')
                self.state = 'ROUTING_TO_HOME'
            else:
                self.log_debug(f"Received CANCEL_TASK for {task_id} but it is not active. Removing from pending.")
                self.mqtt.pending_orders = [o for o in self.mqtt.pending_orders if o.get('id') != task_id]

    def _run_loop(self):
        global HOME_LAT, HOME_LON
        blocked_msg_sent = False
        while True:
            telemetry = self.uart.get_telemetry()
            
            # Tự động ghi nhận tọa độ HOME dựa trên GPS fix đầu tiên khi xe khởi động
            if (HOME_LAT is None or HOME_LON is None) and self.gps.current_lat != 0.0 and self.gps.current_lon != 0.0:
                HOME_LAT = self.gps.current_lat
                HOME_LON = self.gps.current_lon
                self.log_debug(f"Dynamically recorded HOME coordinates from initial GPS fix: {HOME_LAT}, {HOME_LON}")
            
            if self.state == 'INIT':
                if not hasattr(self, 'init_start_time'):
                    self.init_start_time = time.time()

                sys_status = self.uart.sys_state
                if sys_status == 'READY':
                    self.log_debug("Hardware Check Passed. Entering IDLE.")
                    self.uart.send_buzzer(2) # 2 beeps to indicate system is ready
                    self.state = 'IDLE'
                elif sys_status.startswith('ERROR:'):
                    self.log_debug(f"Hardware Error: {sys_status}. Retrying...")
                    time.sleep(2)
                    self.uart.send_sys_check()
                else:
                    elapsed = time.time() - self.init_start_time
                    if elapsed > 10:
                        self.log_debug("CRITICAL TIMEOUT: ESP32 is NOT responding!")
                        self.log_debug("Attempting to hard-reset the serial connection...")
                        self.uart.disconnect()
                        time.sleep(2)
                        self.uart.connect()
                        self.init_start_time = time.time()
                        time.sleep(1)
                    else:
                        self.log_debug("Waiting for Hardware Ready...")
                        time.sleep(1)
                    
                    self.uart.send_sys_check()
                continue
            
            # Publish location every 1 second
            current_time = time.time()
            if current_time - self.last_location_publish_time >= 1.0:
                # Tốc độ: Tạm thời set 0.0 vì UartComm chưa hỗ trợ đọc vận tốc thực
                current_speed = 0.0
                
                # Nếu chưa có GPS (0.0), gửi tạm toạ độ rỗng hoặc giữ nguyên 0.0
                # Backend sẽ tự hiển thị hoặc ẩn tuỳ theo logic (ít nhất backend sẽ biết AGV online)
                self.mqtt.update_location(
                    self.gps.current_lat, 
                    self.gps.current_lon,
                    battery=self.uart.battery_pct,
                    speed=current_speed,
                    heading=self.uart.yaw,
                    home_lat=HOME_LAT,
                    home_lon=HOME_LON,
                    status=self.state,
                    order_id=self.current_order['id'] if self.current_order else None
                )
                self.log_debug(f"Published telemetry to Backend: {self.gps.current_lat}, {self.gps.current_lon} | Batt: {self.uart.battery_pct}% | Spd: {current_speed}m/s")
                self.last_location_publish_time = current_time

            if self.state == 'IDLE':
                self.uart.send_speed(0, 0)
                self.uart.send_ble_off()
                self.is_returning = False
                
                # Check if there is any pending order we missed
                pending = self.mqtt.get_next_pending_order()
                if pending:
                    self.on_new_order(pending)
                time.sleep(1)
                
            elif self.state == 'TEST_ROTATION':
                # Convert target_angle to radians
                target_rad = math.radians(self.target_test_angle)
                
                # Update localization to get latest theta_est
                self.gps.update_localization(telemetry)
                current_heading = self.gps.theta_est
                
                # Calculate error
                heading_error = (target_rad - current_heading + math.pi) % (2 * math.pi) - math.pi
                
                if abs(heading_error) < 0.1: # Within ~5.7 degrees
                    self.uart.send_speed(0, 0)
                    # Stay in TEST_ROTATION state to actively hold this heading
                else:
                    turn_speed = 15.0
                    if heading_error > 0:
                        self.uart.send_speed(-turn_speed, turn_speed) # Turn left
                    else:
                        self.uart.send_speed(turn_speed, -turn_speed) # Turn right
                time.sleep(0.05)
                    
            elif self.state == 'GOING_TO_SENDER':
                if telemetry.get('obstacle', False):
                    if not blocked_msg_sent:
                        self.mqtt.update_order_status(self.current_order['id'], 'BLOCKED')
                        blocked_msg_sent = True
                else:
                    if blocked_msg_sent:
                        self.mqtt.update_order_status(self.current_order['id'], 'GOING_TO_SENDER')
                        blocked_msg_sent = False

                v_left, v_right, arrived = self.gps.get_navigation_commands(telemetry)
                if arrived:
                    self.log_debug("Arrived at Sender.")
                    self.uart.send_speed(0, 0)
                    
                    self.token_gui = str(self.current_order.get('token_gui', self.current_order.get('pin_code', '1234')))
                    self.uart.send_ble_on(self.token_gui)
                    
                    self.mqtt.update_order_status(self.current_order['id'], 'WAITING_SENDER')
                    self.arrival_time = time.time()
                    self.state = 'WAITING_SENDER'
                else:
                    self.uart.send_speed(v_left, v_right)
                    time.sleep(0.1)
                    
            elif self.state == 'WAITING_SENDER':
                self.uart.send_speed(0, 0) # Force stop to prevent drifting
                if time.time() - self.arrival_time > TIMEOUT_SECONDS:
                    self.log_debug("Timeout waiting for sender. Routing to HOME.")
                    self.mqtt.update_order_status(self.current_order['id'], 'CANCELED_TIMEOUT')
                    self.uart.send_ble_off()
                    self.state = 'ROUTING_TO_HOME'
                    continue

                door_event = telemetry.get('door')
                if door_event == 'OPENED':
                    self.mqtt.update_order_status(self.current_order['id'], 'DOOR_OPENED')
                elif door_event in ['LOCKED', 'CLOSED']:
                    self.log_debug("Door closed. Order loaded successfully.")
                    self.uart.send_ble_off()
                    self.mqtt.update_compartment_status(self.current_order['id'], 'FULL')
                    self.mqtt.update_order_status(self.current_order['id'], 'LOADED')
                    
                    self.state = 'ROUTING_TO_RECEIVER'
                time.sleep(0.5)

            elif self.state == 'ROUTING_TO_RECEIVER':
                target_lat = float(self.current_order.get('recv_lat', 0))
                target_lon = float(self.current_order.get('recv_lon', 0))
                
                self.log_debug(f"Routing to Receiver: {target_lat}, {target_lon}")
                if self.gps.fetch_route(target_lat, target_lon):
                    self.mqtt.update_order_status(self.current_order['id'], 'GOING_TO_RECEIVER')
                    self.state = 'GOING_TO_RECEIVER'
                else:
                    self.log_debug("Failed to fetch route to Receiver. Retrying in 5s...")
                    time.sleep(5)
                    
            elif self.state == 'GOING_TO_RECEIVER':
                if telemetry.get('obstacle', False):
                    if not blocked_msg_sent:
                        self.mqtt.update_order_status(self.current_order['id'], 'BLOCKED')
                        blocked_msg_sent = True
                else:
                    if blocked_msg_sent:
                        self.mqtt.update_order_status(self.current_order['id'], 'GOING_TO_RECEIVER')
                        blocked_msg_sent = False

                v_left, v_right, arrived = self.gps.get_navigation_commands(telemetry)
                if arrived:
                    self.log_debug("Arrived at Receiver.")
                    self.uart.send_speed(0, 0)
                    
                    self.token_nhan = str(self.current_order.get('token_nhan', self.current_order.get('pin_code_nhan', '5678')))
                    self.uart.send_ble_on(self.token_nhan)
                    
                    self.mqtt.update_order_status(self.current_order['id'], 'WAITING_RECEIVER')
                    self.arrival_time = time.time()
                    self.state = 'WAITING_RECEIVER'
                else:
                    self.uart.send_speed(v_left, v_right)
                    time.sleep(0.1)
                
            elif self.state == 'WAITING_RECEIVER':
                self.uart.send_speed(0, 0) # Force stop to prevent drifting
                if time.time() - self.arrival_time > TIMEOUT_SECONDS:
                    self.log_debug("Timeout waiting for receiver. Routing back to HOME.")
                    self.mqtt.update_order_status(self.current_order['id'], 'TIMEOUT_RETURNING')
                    self.uart.send_ble_off()
                    self.state = 'ROUTING_TO_HOME'
                    continue

                door_event = telemetry.get('door')
                if door_event == 'OPENED':
                    self.mqtt.update_order_status(self.current_order['id'], 'DOOR_OPENED')
                elif door_event in ['LOCKED', 'CLOSED']:
                    self.log_debug("Door closed. Checking if compartment is empty...")
                    self.uart.send_ble_off()
                    self.uart.send_box_check()
                    
                    # Wait and read telemetry for box status (up to 5 seconds)
                    start_time = time.time()
                    box_status = None
                    while time.time() - start_time < 5.0:
                        time.sleep(0.1)
                        tel = self.uart.get_telemetry()
                        if tel.get('box') in ['EMPTY', 'FULL']:
                            box_status = tel.get('box')
                            break
                    
                    if box_status == 'EMPTY':
                        self.log_debug("Box verified empty. Order completed successfully.")
                        self.mqtt.update_compartment_status('', 'EMPTY')
                        self.mqtt.update_order_status(self.current_order['id'], 'DONE')
                        self.current_order = None
                    else:
                        self.log_debug("Box not empty! Package still inside compartment.")
                        self.mqtt.update_order_status(self.current_order['id'], 'LOCKED_AT_HOME')
                        
                    self.state = 'ROUTING_TO_HOME'
                time.sleep(0.5)

            elif self.state == 'ROUTING_TO_HOME':
                self.log_debug(f"Routing to HOME: {HOME_LAT}, {HOME_LON}")
                if self.gps.fetch_route(HOME_LAT, HOME_LON):
                    self.state = 'RETURNING'
                else:
                    self.log_debug("Failed to fetch route to HOME. Retrying in 5s...")
                    time.sleep(5)
            
            elif self.state == 'RETURNING':
                if telemetry.get('obstacle', False):
                    if not blocked_msg_sent:
                        self.mqtt.update_order_status(self.current_order['id'] if self.current_order else 'HOME', 'BLOCKED')
                        blocked_msg_sent = True
                else:
                    if blocked_msg_sent:
                        blocked_msg_sent = False
                    
                v_left, v_right, arrived = self.gps.get_navigation_commands(telemetry)
                if arrived:
                    self.log_debug("Arrived at HOME.")
                    self.uart.send_speed(0, 0)
                    self.uart.send_ble_off()
                    self.uart.send_buzzer(2) # 2 beeps to indicate system is ready
                    
                    if self.current_order is not None:
                        self.log_debug("Arrived home with stranded package.")
                        self.mqtt.update_order_status(self.current_order['id'], 'LOCKED_AT_HOME')
                    
                    self.state = 'IDLE'
                else:
                    self.uart.send_speed(v_left, v_right)
                    time.sleep(0.1)

if __name__ == '__main__':
    bot = AgvStateMachine()
    bot.start()
