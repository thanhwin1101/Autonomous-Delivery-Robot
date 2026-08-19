# SOURCE CLASS INDEX — CURRENT
> Generated: 2026-07-02 | Source: live Java source scan  
> Package root: `com.example.autodeliveryapp`

---

## GROUP: Activity

### LoginActivity
- **Path**: `app/src/main/java/.../LoginActivity.java`
- **Package**: `com.example.autodeliveryapp`
- **Role**: Entry point. Handles email+password login (Firebase Auth) and phone OTP login. Routes to MainActivity on success.
- **Group**: Activity
- **Key deps**: `FirebaseAuth`, `FirebaseDatabase`, `PhoneUtils`, `Constants`
- **Screens**: Login screen (LAUNCHER activity)
- **Flow**: Login → MainActivity

---

### RegisterActivity
- **Path**: `app/src/main/java/.../RegisterActivity.java`
- **Package**: `com.example.autodeliveryapp`
- **Role**: New user registration. Creates Firebase Auth account, writes to `/users/{uid}` and `/phoneIndex/{phoneNormalized}`.
- **Group**: Activity
- **Key deps**: `FirebaseAuth`, `FirebaseDatabase`, `PhoneUtils`, `Constants`
- **Screens**: Register screen
- **Flow**: Register → LoginActivity

---

### ForgotPasswordActivity
- **Path**: `app/src/main/java/.../ForgotPasswordActivity.java`
- **Package**: `com.example.autodeliveryapp`
- **Role**: Sends Firebase password reset email via `FirebaseAuth.sendPasswordResetEmail()`.
- **Group**: Activity
- **Key deps**: `FirebaseAuth`
- **Screens**: Forgot Password screen

---

### MainActivity
- **Path**: `app/src/main/java/.../MainActivity.java`
- **Package**: `com.example.autodeliveryapp`
- **Role**: Home/dashboard screen. Shows active tasks list for current user from Firebase `/tasks/{uid}`. Entry to CreateTask and TrackingActivity.
- **Group**: Activity
- **Key deps**: `FirebaseAuth`, `FirebaseDatabase`, `ActiveTaskAdapter`, `Constants`, `BottomNavActivity`
- **Screens**: Home tab (bottom nav)
- **Flow**: Home → CreateTask, Home → TrackingActivity (on task tap)

---

### HistoryActivity
- **Path**: `app/src/main/java/.../HistoryActivity.java`
- **Package**: `com.example.autodeliveryapp`
- **Role**: Reads completed/delivered tasks for current user. Displays in RecyclerView.
- **Group**: Activity
- **Key deps**: `FirebaseAuth`, `FirebaseDatabase`, `HistoryAdapter`, `Constants`
- **Screens**: History tab (bottom nav)

---

### NotificationActivity
- **Path**: `app/src/main/java/.../NotificationActivity.java`
- **Package**: `com.example.autodeliveryapp`
- **Role**: Shows notification inbox from Firebase `/notifications/{uid}`. Marks notifications as read.
- **Group**: Activity
- **Key deps**: `FirebaseAuth`, `FirebaseDatabase`, `NotificationAdapter`, `Constants`
- **Screens**: Notification tab (bottom nav)

---

### ProfileActivity
- **Path**: `app/src/main/java/.../ProfileActivity.java`
- **Package**: `com.example.autodeliveryapp`
- **Role**: Shows current user info (name, phone, email). Logout button clears session → LoginActivity.
- **Group**: Activity
- **Key deps**: `FirebaseAuth`, `FirebaseDatabase`, `Constants`
- **Screens**: Profile tab (bottom nav)

---

### CreateTaskActivity
- **Path**: `app/src/main/java/.../CreateTaskActivity.java`
- **Package**: `com.example.autodeliveryapp`
- **Role**: Full task creation flow. Resolves addresses via local Da Nang lookup. Fetches A→B route from OSRM (Haversine fallback). Slot selection with Firebase Transaction. Writes task + bleToken to Firebase. Publishes MQTT START_DELIVERY command.
- **Group**: Activity
- **Key deps**: `FirebaseAuth`, `FirebaseDatabase`, `MqttManager`, `MqttPayloadParser`, `BleTokenUtils`, `PhoneUtils`, `NotificationUtils`, `Constants`, `MapLibre`
- **Screens**: Create Task screen (from MainActivity fab/button)
- **Flow**: CreateTask → fires MQTT command → starts TrackingActivity

---

### TrackingActivity
- **Path**: `app/src/main/java/.../TrackingActivity.java`
- **Package**: `com.example.autodeliveryapp`
- **Role**: Live tracking UI. Firebase listener reads all task fields. MQTT listener gets telemetry/status/ack. BLE button for sender (confirm_loaded) or receiver (open_slot). MapLibre draws live route C→A (before pickup) or C→B (after pickup). Implements MqttListener.
- **Group**: Activity
- **Key deps**: `FirebaseAuth`, `FirebaseDatabase`, `MqttManager`, `MqttFirebaseBridge`, `MqttListener`, `BleManager`, `BlePermissionHelper`, `BleConstants`, `TaskStatus`, `Constants`, `MapLibre`
- **Screens**: Tracking screen (opened from MainActivity on task tap)
- **Flow**: Firebase listener → updateStatusUI → updateMapRoute → BLE action → MqttFirebaseBridge

---

### BottomNavActivity
- **Path**: `app/src/main/java/.../BottomNavActivity.java`
- **Package**: `com.example.autodeliveryapp`
- **Role**: Base class or helper for bottom navigation tab switching. Used by tab activities.
- **Group**: Activity
- **Key deps**: `BottomNavigationView`

---

## GROUP: Adapter

### ActiveTaskAdapter
- **Path**: `app/src/main/java/.../adapters/ActiveTaskAdapter.java`
- **Role**: RecyclerView adapter for active tasks. Binds task data (orderId, status, pickup, dropoff) to `item_active_task.xml`.
- **Group**: Adapter
- **Key deps**: `item_active_task.xml`, `MainActivity`

### HistoryAdapter
- **Path**: `app/src/main/java/.../adapters/HistoryAdapter.java`
- **Role**: RecyclerView adapter for history. Binds to `item_history.xml`.
- **Group**: Adapter
- **Key deps**: `item_history.xml`, `HistoryActivity`

### NotificationAdapter
- **Path**: `app/src/main/java/.../adapters/NotificationAdapter.java`
- **Role**: RecyclerView adapter for notifications. Binds to `item_notification.xml`.
- **Group**: Adapter
- **Key deps**: `item_notification.xml`, `NotificationActivity`

---

## GROUP: Model

### RobotTelemetry
- **Path**: `app/src/main/java/.../model/RobotTelemetry.java`
- **Role**: POJO for MQTT telemetry payload. Fields: `robotId, taskId, lat, lng, battery, speed, heading, obstacle, timestamp, seq`.
- **Group**: Model

### RobotStatusMessage
- **Path**: `app/src/main/java/.../model/RobotStatusMessage.java`
- **Role**: POJO for MQTT status payload. Fields: `robotId, taskId, status, slotId, message, timestamp`.
- **Group**: Model

### RobotAck
- **Path**: `app/src/main/java/.../model/RobotAck.java`
- **Role**: POJO for MQTT ack payload. Fields: `commandId, taskId, robotId, accepted, message, timestamp`.
- **Group**: Model

### RobotCommand
- **Path**: `app/src/main/java/.../model/RobotCommand.java`
- **Role**: POJO for MQTT command payload. Fields: `commandId, command, taskId, orderId, robotId, slotId, pickupLat, pickupLng, pickupAddress, dropoffLat, dropoffLng, dropoffAddress, bleToken, timestamp`.
- **Group**: Model

---

## GROUP: MQTT

### MqttConfig
- **Path**: `app/src/main/java/.../mqtt/MqttConfig.java`
- **Role**: Reads MQTT broker config from `BuildConfig` (injected from `local.properties`). Fields: HOST, PORT, USERNAME, PASSWORD, USE_SSL, ENV, DEFAULT_ROBOT_ID. `isConfigUsable()` guard.
- **Group**: MQTT

### MqttManager
- **Path**: `app/src/main/java/.../mqtt/MqttManager.java`
- **Role**: Singleton. Manages HiveMQ MQTT client lifecycle: connect, disconnect, subscribe, publish. Uses `MqttConfig`, dispatches events to `MqttListener`.
- **Group**: MQTT
- **Key deps**: `HiveMQ SDK (com.hivemq:hivemq-mqtt-client:1.3.15)`, `MqttConfig`, `MqttTopics`, `MqttPayloadParser`, `MqttListener`

### MqttTopics
- **Path**: `app/src/main/java/.../mqtt/MqttTopics.java`
- **Role**: Static topic builders with env prefix. Topics: `autodelivery/{env}/robots/{robotId}/{type}`. Types: command, telemetry, status, ack, event, availability.
- **Group**: MQTT

### MqttPayloadParser
- **Path**: `app/src/main/java/.../mqtt/MqttPayloadParser.java`
- **Role**: Parses JSON payload strings to model objects (telemetry/status/ack). Serializes `RobotCommand` to JSON.
- **Group**: MQTT

### MqttFirebaseBridge
- **Path**: `app/src/main/java/.../mqtt/MqttFirebaseBridge.java`
- **Role**: Client-side bridge. Mirrors MQTT telemetry/status/ack → Firebase. Throttles telemetry writes (5s). Derives `activeLeg` from status. Calls `NotificationUtils` + `SlotUtils` on status change.
- **Group**: MQTT
- **Key deps**: `FirebaseDatabase`, `NotificationUtils`, `SlotUtils`, `TaskStatus`

### MqttListener
- **Path**: `app/src/main/java/.../mqtt/MqttListener.java`
- **Role**: Interface. Methods: `onMqttConnected`, `onMqttDisconnected`, `onMqttError`, `onRobotTelemetry`, `onRobotStatus`, `onRobotAck`, `onRobotAvailability`.
- **Group**: MQTT

---

## GROUP: BLE

### BleConstants
- **Path**: `app/src/main/java/.../ble/BleConstants.java`
- **Role**: BLE UUID definitions and action/state constants. SERVICE_UUID: `0000FFE0-...`, CHARACTERISTIC_UUID: `0000FFE1-...`. Actions: `confirm_loaded`, `open_slot`. States: `created`, `sender_loaded`, `receiver_unlocked`.
- **Group**: BLE

### BleProtocol
- **Path**: `app/src/main/java/.../ble/BleProtocol.java`
- **Role**: BLE JSON protocol. `buildRequest(action, taskId, slotId, token)` → JSON string. `Response.parse(jsonString)` → `{ok, action, slotId, error}`.
- **Group**: BLE

### BleTokenUtils
- **Path**: `app/src/main/java/.../ble/BleTokenUtils.java`
- **Role**: Generates 128-bit cryptographically secure hex token (32 chars) using `SecureRandom`. Called in `CreateTaskActivity.confirmTask()`.
- **Group**: BLE

### BleManager
- **Path**: `app/src/main/java/.../ble/BleManager.java`
- **Role**: High-level BLE manager. Scans for robot by `robotId`, calls `BleGattClient` to connect and `executeBleAction`. Returns result via `BleActionCallback`.
- **Group**: BLE
- **Key deps**: `BleGattClient`, `BleProtocol`, `BleConstants`, `BlePermissionHelper`

### BleGattClient
- **Path**: `app/src/main/java/.../ble/BleGattClient.java`
- **Role**: Low-level GATT client. Connects to BLE peripheral, discovers services, writes to characteristic, reads response.
- **Group**: BLE

### BlePermissionHelper
- **Path**: `app/src/main/java/.../ble/BlePermissionHelper.java`
- **Role**: Checks and requests `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION` permissions. `REQUEST_CODE_BLE_PERMISSIONS` constant.
- **Group**: BLE

### BleActionCallback
- **Path**: `app/src/main/java/.../ble/BleActionCallback.java`
- **Role**: Interface. Methods: `onSuccess()`, `onFailure(String error)`.
- **Group**: BLE

---

## GROUP: Utils

### TaskStatus
- **Path**: `app/src/main/java/.../utils/TaskStatus.java`
- **Role**: Task status string constants. Values: `pending, going_to_pickup, arrived_pickup, waiting_sender_load, sender_loaded, picked_up, going_to_destination, arrived_dropoff, waiting_receiver_unlock, delivered, cancelled`. `isTerminal(status)` helper.
- **Group**: Utils

### SlotUtils
- **Path**: `app/src/main/java/.../utils/SlotUtils.java`
- **Role**: Firebase slot release. `releaseSlot(robotId, slotId)` writes `{status: available, taskId: null, orderId: null}` to `/robotSlots/{robotId}/{slotId}`. `releaseSlotIfTerminal()` calls `releaseSlot` only if status is terminal.
- **Group**: Utils
- **Key deps**: `FirebaseDatabase`, `TaskStatus`, `Constants`

### NotificationUtils
- **Path**: `app/src/main/java/.../utils/NotificationUtils.java`
- **Role**: Builds notification title/message/icon strings for each task status type. Used by `MqttFirebaseBridge.sendNotification()`.
- **Group**: Utils

### PhoneUtils
- **Path**: `app/src/main/java/.../utils/PhoneUtils.java`
- **Role**: Normalizes phone numbers to E.164 format (`+84`). Used in registration and task creation for receiver lookup.
- **Group**: Utils

### EdgeToEdgeHelper
- **Path**: `app/src/main/java/.../EdgeToEdgeHelper.java`
- **Role**: Applies edge-to-edge status/navigation bar padding. `applyStatusBarPadding(view)`, `applyNavigationBarPadding(view)`, `setLightStatusBar(activity, light)`.
- **Group**: Utils

---

## GROUP: Service

### FCMNotificationService
- **Path**: `app/src/main/java/.../FCMNotificationService.java`
- **Role**: `FirebaseMessagingService` subclass. `onNewToken()` saves FCM token. `onMessageReceived()` handles incoming push messages. Token stored in `SharedPreferences(FCM_PREFS)` under key `pending_token`.
- **Group**: Service
- **Key deps**: `FirebaseMessaging`, `Constants.FCM_PREFS`

---

## GROUP: Data

### HistoryItem
- **Path**: `app/src/main/java/.../data/HistoryItem.java`
- **Role**: Data class for task history list items. Maps Firebase task snapshot fields.
- **Group**: Data

### NotificationItem
- **Path**: `app/src/main/java/.../data/NotificationItem.java`
- **Role**: Data class for notification list items. Maps Firebase `/notifications/{uid}/{notifId}` fields.
- **Group**: Data

---

## GROUP: Constants (root package)

### Constants
- **Path**: `app/src/main/java/.../Constants.java`
- **Role**: Central constants. `DB_URL` (Firebase RTDB URL from BuildConfig), `MAPTILER_API_KEY` (from BuildConfig), `MAP_STYLE_URL` (MapTiler streets-v2), `FCM_PREFS`, `FCM_PREFS_KEY_PENDING_TOKEN`, `DA_NANG_LOCATIONS` (25-entry hardcoded map of Da Nang addresses → coordinates).
- **Group**: Constants
- **Note**: Address resolution is LOCAL LOOKUP ONLY — not real geocoding API.
