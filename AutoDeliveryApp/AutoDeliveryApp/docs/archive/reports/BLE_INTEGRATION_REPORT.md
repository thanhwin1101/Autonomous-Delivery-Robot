# BLE Integration Report

## 1. Files Modified / Created
- **Created**: 
  - `com.example.autodeliveryapp.ble.BleActionCallback`
  - `com.example.autodeliveryapp.ble.BleConstants`
  - `com.example.autodeliveryapp.ble.BleGattClient`
  - `com.example.autodeliveryapp.ble.BleManager`
  - `com.example.autodeliveryapp.ble.BlePermissionHelper`
  - `com.example.autodeliveryapp.ble.BleProtocol`
  - `com.example.autodeliveryapp.ble.BleTokenUtils`
- **Modified**:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/res/layout/activity_tracking.xml`
  - `app/src/main/java/com/example/autodeliveryapp/CreateTaskActivity.java`
  - `app/src/main/java/com/example/autodeliveryapp/TrackingActivity.java`
  - `app/src/main/java/com/example/autodeliveryapp/utils/TaskStatus.java`
  - `app/src/main/java/com/example/autodeliveryapp/utils/NotificationUtils.java`
  - `app/src/main/java/com/example/autodeliveryapp/adapters/NotificationAdapter.java`

## 2. Implemented BLE Flow
- **Token Generation**: Securely generated (128-bit via `SecureRandom`, Base64URL-encoded) when the Sender creates the order in `CreateTaskActivity`.
- **Sender Flow**: When robot is at `arrived_pickup` or `waiting_sender_load`, the UI shows "Đã đặt hàng vào kho chứa". Pressing it uses BLE GATT to send `confirm_loaded` with the token. Robot acknowledges with `{"ok": true}`. State is then updated to `sender_loaded`.
- **Receiver Flow**: When robot is at `arrived_dropoff` or `waiting_receiver_unlock`, the UI shows "Mở khoang hàng". Pressing it uses BLE GATT to send `open_slot` with the token. Robot acknowledges. Receiver's state is updated to `receiver_unlocked` in `recipientTasks`. The main task in `tasks` will be updated securely by the robot via MQTT.
- **Firebase Blocker Restriction Handled**: Due to rules blocking receiver from writing to `tasks`, the receiver only writes the BLE token state to `recipientTasks`. The primary source of truth for task progress remains MQTT.

## 3. New Firebase Fields
- `bleToken`: Stored in both `tasks/{senderUid}/{taskId}` and `recipientTasks/{receiverUid}/{taskId}`.
- `bleState`: Keeps track of UI interactions (`sender_loaded`, `receiver_unlocked`) to disable buttons during processing and show loading states.

## 4. New MQTT & Task Statuses
- `arrived_pickup`
- `waiting_sender_load`
- `sender_loaded`
- `arrived_dropoff`
- `waiting_receiver_unlock`

*Note: Notification mappings were perfectly aligned to ensure accurate cross-communication between the App's notification subsystem and MQTT Bridge.*

## 5. Android Permissions
Requested dynamic permissions using native Android popups (`BlePermissionHelper`).
- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION`
- Handled backwards compatibility for Android versions below 12.

## 6. Build & Lint Results
- `gradlew clean assembleDebug`: **SUCCESSFUL**
- `gradlew lintDebug`: **SUCCESSFUL** (No breaking lint issues in the implemented BLE logic)

## 7. How to Test Without Real Robot
1. Run a Bluetooth LE simulator on a secondary Android device or computer.
2. Broadcast the Service UUID `0000FFE0-0000-1000-8000-00805F9B34FB`.
3. Add a writable Characteristic `0000FFE1-0000-1000-8000-00805F9B34FB`.
4. When the App connects and writes a JSON string (e.g. `{"action":"confirm_loaded","token":"..."}`), the simulator should manually notify/indicate the characteristic back with `{"ok":true}`.
5. Watch the App UI update dynamically and successfully write to Firebase.

## 8. How to Test With Real Robot
1. Ensure the Robot firmware is flashed to advertise `FFE0` service and `FFE1` characteristic.
2. Ensure the Robot's MTU is set to handle the JSON length (app requests MTU 512).
3. Sender places an order, app connects via BLE, writes `confirm_loaded`.
4. Robot verifies the BLE token logic and physical load.
5. Robot completes the delivery trip. Receiver connects via BLE, writes `open_slot`.
6. Robot physically unlocks the storage box.

## 9. Remaining Risks
- **Bluetooth Interference / Range**: If users stand too far away, connection might fail and they'll experience "TIMEOUT" or "DISCONNECTED" toasts.
- **Robot Token Syncing**: The Robot must reliably receive the `bleToken` from the backend/MQTT so it can verify the BLE request payload.
- **Concurrent Connections**: If two users attempt to connect to the robot concurrently, standard BLE GATT usually drops or refuses the second connection. Only one connection at a time is guaranteed.
