import express from 'express';
import http from 'http';
import { Server } from 'socket.io';
import cors from 'cors';
import mqtt from 'mqtt';
import admin from 'firebase-admin';
import dotenv from 'dotenv';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

dotenv.config({ path: '../../robot/pi_master/.env' });

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
app.use(cors());
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});

// 1. Firebase Admin Initialization
try {
  const serviceAccountPath = path.join(__dirname, 'serviceAccountKey.json');
  if (fs.existsSync(serviceAccountPath)) {
    const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, 'utf8'));
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      databaseURL: "https://auto-delivery-e0327-default-rtdb.asia-southeast1.firebasedatabase.app"
    });
    console.log("Firebase Admin Initialized successfully.");
  } else {
    console.warn("WARNING: serviceAccountKey.json not found. Firebase features will be disabled.");
  }
} catch (error) {
  console.error("Firebase init error:", error);
}

// Global state to hold latest AGV data
let agvState = null;

async function sendPushNotification(uid, title, body) {
  if (!uid || admin.apps.length === 0) return;
  try {
    const tokenSnap = await admin.database().ref(`users/${uid}/fcmToken`).once('value');
    if (tokenSnap.exists()) {
      const token = tokenSnap.val();
      const message = {
        data: { 
          title: title, 
          message: body 
        },
        token: token,
      };
      await admin.messaging().send(message);
      console.log(`Sent push notification to ${uid}: ${title}`);
    }
  } catch (error) {
    console.error(`Error sending push to ${uid}:`, error);
  }
}

let activeMissions = {}; // Cache missions fetched from Firebase
let lastDispatchedTaskId = null; // Prevent dispatch spam
let taskUserMap = {}; // Maps taskId to { senderUid, receiverUid }

// 2. MQTT Connection
const brokerUrl = `mqtts://${process.env.MQTT_BROKER}:${process.env.MQTT_PORT || 8883}`;
console.log(`Connecting to MQTT broker at ${brokerUrl}...`);

const mqttClient = mqtt.connect(brokerUrl, {
  username: process.env.MQTT_USERNAME,
  password: process.env.MQTT_PASSWORD,
  clientId: `backend_server_${Math.random().toString(16).substring(2, 8)}`,
  protocolVersion: 5
});

mqttClient.on('connect', () => {
  console.log('Connected to MQTT Broker!');
  mqttClient.subscribe('agv/location', { qos: 0 });
  mqttClient.subscribe('agv/orders/status', { qos: 1 });
  mqttClient.subscribe('agv/debug/logs', { qos: 0 });
  mqttClient.subscribe('agv/status/compartment1', { qos: 1 });
});

mqttClient.on('error', (err) => {
  console.error('MQTT Connection Error:', err);
});

mqttClient.on('message', async (topic, message) => {
  try {
    const payloadStr = message.toString();
    
    if (topic === 'agv/debug/logs') {
      // Just forward logs to frontend
      if (agvState) {
        io.emit('agv_log', {
          id: `log-${Date.now()}`,
          timestamp: new Date().toLocaleTimeString([], { hour12: false }),
          agvId: agvState.id,
          type: 'info',
          message: payloadStr
        });
      }
      return;
    }

    const data = JSON.parse(payloadStr);

    // Initialize state if not present
    if (!agvState) {
      agvState = {
        id: 'AGV-01',
        name: 'AGV-CongVinh01',
        status: 'IDLE',
        battery: 100,
        lat: 0,
        lng: 0,
        speed: 0,
        heading: 0,
        lastPing: 0,
        type: 'Heavy-Duty',
        currentOrder: null,
        route: [],
        routeProgressIndex: 0,
        estimatedTimeRemaining: 'N/A',
        assignedStation: 'HOME',
        totalDistanceTraveled: 0,
        maintenanceScore: 100
      };
    }

    if (topic === 'agv/location') {
      agvState.lat = data.lat;
      agvState.lng = data.lon;
      if (data.battery !== undefined) agvState.battery = data.battery;
      if (data.speed !== undefined) agvState.speed = data.speed;
      if (data.heading !== undefined) agvState.heading = data.heading;
      if (data.homeLat !== undefined) agvState.homeLat = data.homeLat;
      if (data.homeLon !== undefined) agvState.homeLng = data.homeLon;
      agvState.lastPing = 0; // reset ping
      if (agvState.status === 'OFFLINE') {
        agvState.status = 'IDLE';
        lastDispatchedTaskId = null; // Reset to allow dispatching pending tasks
      }
      console.log(`[MQTT] Received Telemetry: Lat: ${data.lat}, Lon: ${data.lon}, Batt: ${agvState.battery}%, Speed: ${agvState.speed}m/s`);
      io.emit('agv_update', agvState);

      // Forward to Firebase
      if (agvState.currentOrder && agvState.currentOrder.id) {
        const orderId = agvState.currentOrder.id;
        const mapInfo = taskUserMap[orderId];
        if (mapInfo && admin.apps.length > 0) {
          const updates = {};
          updates[`tasks/${mapInfo.senderUid}/${orderId}/robotLat`] = data.lat;
          updates[`tasks/${mapInfo.senderUid}/${orderId}/robotLng`] = data.lon;
          if (data.heading !== undefined) {
            updates[`tasks/${mapInfo.senderUid}/${orderId}/robotHeading`] = data.heading;
          }
          if (data.homeLat !== undefined && data.homeLon !== undefined) {
            updates[`tasks/${mapInfo.senderUid}/${orderId}/homeLat`] = data.homeLat;
            updates[`tasks/${mapInfo.senderUid}/${orderId}/homeLng`] = data.homeLon;
          }
          if (mapInfo.receiverUid) {
            updates[`recipientTasks/${mapInfo.receiverUid}/${orderId}/robotLat`] = data.lat;
            updates[`recipientTasks/${mapInfo.receiverUid}/${orderId}/robotLng`] = data.lon;
            if (data.heading !== undefined) {
              updates[`recipientTasks/${mapInfo.receiverUid}/${orderId}/robotHeading`] = data.heading;
            }
            if (data.homeLat !== undefined && data.homeLon !== undefined) {
              updates[`recipientTasks/${mapInfo.receiverUid}/${orderId}/homeLat`] = data.homeLat;
              updates[`recipientTasks/${mapInfo.receiverUid}/${orderId}/homeLng`] = data.homeLon;
            }
          }
          admin.database().ref().update(updates).catch(e => console.error("Firebase update error:", e));
        }
      }
      
      // Always update global system home location
      if (data.homeLat !== undefined && data.homeLon !== undefined && admin.apps.length > 0) {
        admin.database().ref('system/agv_home').set({
          lat: data.homeLat,
          lng: data.homeLon
        }).catch(e => console.error("Firebase home update error:", e));
      }
    }
    else if (topic === 'agv/orders/status' || topic === 'agv/status/compartment1') {
      const { order_id, status } = data;
      console.log(`Order ${order_id} status updated to ${status}`);

      // Update Firebase tasks with new status
      const mapInfo = taskUserMap[order_id];
      if (mapInfo && admin.apps.length > 0) {
        let mappedStatus = status;
        if (status === 'GOING_TO_SENDER') mappedStatus = 'going_to_pickup';
        if (status === 'WAITING_SENDER') mappedStatus = 'arrived_pickup';
        if (status === 'LOADED') mappedStatus = 'sender_loaded';
        if (status === 'GOING_TO_RECEIVER') mappedStatus = 'going_to_destination';
        if (status === 'WAITING_RECEIVER') mappedStatus = 'arrived_dropoff';
        if (status === 'DONE') mappedStatus = 'delivered';
        if (status === 'CANCELLED' || status === 'CANCELED_TIMEOUT') mappedStatus = 'cancelled';
        
        const updates = {};
        updates[`tasks/${mapInfo.senderUid}/${order_id}/status`] = mappedStatus;
        updates[`tasks/${mapInfo.senderUid}/${order_id}/activeLeg`] = ['GOING_TO_SENDER', 'WAITING_SENDER'].includes(status) ? 'C_TO_A' : 'A_TO_B';
        if (mapInfo.receiverUid) {
          updates[`recipientTasks/${mapInfo.receiverUid}/${order_id}/status`] = mappedStatus;
          updates[`recipientTasks/${mapInfo.receiverUid}/${order_id}/activeLeg`] = updates[`tasks/${mapInfo.senderUid}/${order_id}/activeLeg`];
        }
        admin.database().ref().update(updates)
          .then(() => {
            // Send Push Notifications
            const title = "Order Update";
            let body = `Task ${order_id} status changed to ${mappedStatus}`;
            
            if (status === 'GOING_TO_SENDER') body = "The robot is on its way to pick up your item.";
            else if (status === 'WAITING_SENDER') body = "The robot has arrived! Please load your item.";
            else if (status === 'LOADED') body = "Item loaded successfully. Robot is heading to the destination.";
            else if (status === 'GOING_TO_RECEIVER') body = "The robot is on its way to deliver the item.";
            else if (status === 'WAITING_RECEIVER') body = "The robot has arrived at the destination! Please collect your item.";
            else if (status === 'DONE') body = "Delivery completed successfully!";
            
            sendPushNotification(mapInfo.senderUid, title, body);
            if (mapInfo.receiverUid) {
              sendPushNotification(mapInfo.receiverUid, title, body);
            }
          })
          .catch(e => console.error("Firebase status update error:", e));
      }

      
      // Update AGV general status based on mission status
      if (['GOING_TO_SENDER', 'WAITING_SENDER', 'LOADED', 'GOING_TO_RECEIVER', 'WAITING_RECEIVER', 'TIMEOUT_RETURNING'].includes(status)) {
        agvState.status = 'DELIVERING';
        
        // Try to fetch order details from Firebase if not cached
        if (admin.apps.length > 0 && order_id && !activeMissions[order_id]) {
          try {
            const mapInfo = taskUserMap[order_id];
            if (mapInfo) {
              const orderRef = admin.database().ref(`tasks/${mapInfo.senderUid}/${order_id}`);
              const snapshot = await orderRef.once('value');
              if (snapshot.exists()) {
                activeMissions[order_id] = snapshot.val();
                console.log(`Fetched order ${order_id} from Firebase.`);
              }
            } else {
              // Fallback to old path if mapInfo is not found
              const orderRef = admin.database().ref(`orders/${order_id}`);
              const snapshot = await orderRef.once('value');
              if (snapshot.exists()) {
                activeMissions[order_id] = snapshot.val();
                console.log(`Fetched order ${order_id} from Firebase (legacy path).`);
              }
            }
          } catch (e) {
            console.error("Firebase fetch error:", e);
          }
        }
        
        // Map firebase order to frontend Order format
        const fbOrder = activeMissions[order_id];
        if (fbOrder) {
          agvState.currentOrder = {
            id: fbOrder.orderId || fbOrder.id || order_id,
            senderName: fbOrder.senderName || fbOrder.sender_name || 'Sender',
            senderPhone: fbOrder.senderPhone || fbOrder.sender_phone || '',
            receiverName: fbOrder.receiverName || fbOrder.receiver_name || 'Receiver',
            receiverPhone: fbOrder.receiverPhone || fbOrder.receiver_phone || '',
            pickupTime: fbOrder.createdAt || fbOrder.created_at || new Date().toISOString(),
            estimatedArrival: 'Unknown',
            distanceRemaining: 0,
            progress: ['GOING_TO_SENDER', 'WAITING_SENDER'].includes(status) ? 'pickup' : 'transit'
          };
          
          // Provide coordinates so frontend can fetch OSRM route
          if (fbOrder.pickupLat && fbOrder.pickupLng && fbOrder.dropoffLat && fbOrder.dropoffLng) {
            agvState.route = [
              [parseFloat(fbOrder.pickupLat), parseFloat(fbOrder.pickupLng)],
              [parseFloat(fbOrder.dropoffLat), parseFloat(fbOrder.dropoffLng)]
            ];
          } else if (fbOrder.sender_lat && fbOrder.sender_lon && fbOrder.recv_lat && fbOrder.recv_lon) {
            agvState.route = [
              [parseFloat(fbOrder.sender_lat), parseFloat(fbOrder.sender_lon)],
              [parseFloat(fbOrder.recv_lat), parseFloat(fbOrder.recv_lon)]
            ];
          }
        }
      } 
      else if (['DONE', 'CANCELED_TIMEOUT', 'LOCKED_AT_HOME', 'CANCELLED', 'IDLE'].includes(status)) {
        agvState.status = 'IDLE';
        agvState.currentOrder = null;
        agvState.route = [];
        agvState.routeProgressIndex = 0;
        if (order_id) delete activeMissions[order_id];
        lastDispatchedTaskId = null;
      } 
      else if (status === 'BLOCKED') {
        agvState.status = 'STOPPED';
        agvState.speed = 0;
      }
      
      io.emit('agv_update', agvState);
    }
  } catch (err) {
    console.error('Error processing MQTT message:', err);
  }
});

// Socket.io for Frontend
io.on('connection', (socket) => {
  console.log('Frontend client connected');
  // Send initial state immediately if exists
  if (agvState) {
    socket.emit('agv_update', agvState);
  }
  
  socket.on('agv_command', (cmd) => {
    console.log('Received agv_command from frontend:', cmd);
    // Forward command to the AGV via MQTT
    mqttClient.publish('agv/commands', JSON.stringify(cmd), { qos: 1 });
    
    if (cmd.action === 'set_home') {
      if (agvState) {
        agvState.homeLat = cmd.lat;
        agvState.homeLng = cmd.lon;
        io.emit('agv_update', agvState);
      }
      if (admin.apps.length > 0 && cmd.lat !== undefined && cmd.lon !== undefined) {
        admin.database().ref('system/agv_home').set({
          lat: cmd.lat,
          lng: cmd.lon
        }).catch(e => console.error("Firebase direct home update error:", e));
      }
    }
  });

  socket.on('disconnect', () => {
    console.log('Frontend client disconnected');
  });
});

// Simulate ping aging
setInterval(() => {
  if (agvState) {
    agvState.lastPing += 1;
    if (agvState.lastPing > 5 && agvState.status !== 'OFFLINE') {
      agvState.status = 'OFFLINE';
      console.log('AGV connection lost. Status set to OFFLINE.');
    }
    io.emit('agv_update', agvState);
  }
}, 1000);

// Firebase Queue Manager Loop
setInterval(async () => {
  if (admin.apps.length > 0) {
    try {
      const tasksRef = admin.database().ref('tasks');
      const snapshot = await tasksRef.once('value');
      if (!snapshot.exists()) return;

      let oldestTask = null;
      let oldestTaskId = null;
      const allPendingTasks = [];

      snapshot.forEach((userSnap) => {
        userSnap.forEach((taskSnap) => {
          const task = taskSnap.val();
          if (task) {
            // Update task map
            taskUserMap[taskSnap.key] = {
              senderUid: userSnap.key,
              receiverUid: task.receiverUid
            };

            // Detect if a user cancelled an active task
            if ((task.status === 'cancelled' || task.status === 'CANCELLED') && 
                agvState && agvState.currentOrder && agvState.currentOrder.id === taskSnap.key) {
               console.log(`[Queue Manager] Task ${taskSnap.key} was cancelled by user. Sending CANCEL_TASK to AGV.`);
               mqttClient.publish('agv/commands', JSON.stringify({ action: "CANCEL_TASK", id: taskSnap.key }), { qos: 1 });
               // Set status to IDLE locally until the AGV responds with cancel status
            }

            // We look for tasks that are still "pending"
            if (task.status === 'pending') {
              // Add to all pending tasks list for web dashboard
              allPendingTasks.push({
                id: taskSnap.key,
                senderUid: userSnap.key,
                ...task
              });
              
              if (agvState && agvState.status === 'IDLE') {
                if (!oldestTask || task.createdAt < oldestTask.createdAt) {
                  oldestTask = task;
                  oldestTaskId = taskSnap.key;
                }
              }
            }
          }
        });
      });

      // Sort pending tasks by createdAt ascending (oldest first)
      allPendingTasks.sort((a, b) => a.createdAt - b.createdAt);
      io.emit('pending_orders', allPendingTasks);

      if (oldestTask && (oldestTaskId !== lastDispatchedTaskId || (Date.now() - (global.lastDispatchTime || 0)) > 10000)) {
        console.log(`[Queue Manager] Found pending task: ${oldestTaskId}. Dispatching to AGV...`);
        lastDispatchedTaskId = oldestTaskId;
        global.lastDispatchTime = Date.now();
        
        const payload = {
          id: oldestTaskId,
          sender_lat: oldestTask.pickupLat,
          sender_lon: oldestTask.pickupLng,
          recv_lat: oldestTask.dropoffLat,
          recv_lon: oldestTask.dropoffLng,
          token_gui: oldestTask.bleToken || "123456",
          token_nhan: oldestTask.bleToken || "123456"
        };
        
        mqttClient.publish('agv/orders/pending', JSON.stringify(payload), { qos: 1 });
      }
    } catch (err) {
      console.error("Queue Manager error:", err);
    }
  }
}, 3000);

// FCM Push Notification Manager
if (admin.apps.length > 0) {
  const notificationsRef = admin.database().ref('notifications');
  
  notificationsRef.on('child_added', (userSnap) => {
    const userId = userSnap.key;
    // Listen for new notifications for this user that haven't been pushed
    admin.database().ref(`notifications/${userId}`)
      .orderByChild('pushed')
      .equalTo(false)
      .on('child_added', async (notifSnap) => {
        const notif = notifSnap.val();
        const notifId = notifSnap.key;
        
        if (notif && notif.pushed === false) {
          try {
            // Get user's FCM token
            const userRef = admin.database().ref(`users/${userId}`);
            const userSnapshot = await userRef.once('value');
            const userData = userSnapshot.val();
            
            if (userData && userData.fcmToken) {
              const payload = {
                data: {
                  title: notif.title || 'New Notification',
                  message: notif.message || ''
                },
                token: userData.fcmToken
              };
              
              await admin.messaging().send(payload);
              console.log(`[FCM] Sent background push notification to user ${userId}`);
            } else {
              console.log(`[FCM] User ${userId} does not have an fcmToken registered.`);
            }
            
            // Mark as pushed to prevent duplicate pushes
            await admin.database().ref(`notifications/${userId}/${notifId}/pushed`).set(true);
          } catch (err) {
            console.error(`[FCM] Failed to send push notification to user ${userId}:`, err);
            // Even if FCM fails (e.g. invalid token), mark as pushed so we don't spam errors
            await admin.database().ref(`notifications/${userId}/${notifId}/pushed`).set(true);
          }
        }
      });
  });
}

const PORT = process.env.PORT || 3001;
server.listen(PORT, () => {
  console.log(`Backend server running on http://localhost:${PORT}`);
});
