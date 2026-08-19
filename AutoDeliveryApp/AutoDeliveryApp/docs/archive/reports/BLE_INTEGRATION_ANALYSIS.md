# BLE Integration Analysis

## 1. Tài liệu và Nguồn đã đọc
Trước khi tiến hành sửa mã nguồn, các tài liệu và tệp mã nguồn sau đây đã được đọc và phân tích chi tiết:
- `README.md`: Nắm bắt tổng quan dự án, cấu trúc và cách cài đặt.
- `docs/FULL_PROJECT_AUDIT_AND_FLOW_REPORT.md`: Báo cáo audit trước đó, hiểu sơ đồ phân tích luồng Auth, Map, MQTT, Firebase.
- `docs/PROJECT_STATUS_REPORT.md`: Báo cáo trạng thái hiện tại của app.
- `docs/MANUAL_TEST_PLAN.md`: Bộ ca kiểm thử thủ công đang áp dụng.
- `docs/MQTT_INTEGRATION_REPORT.md` & `MQTT_ROBOT_SIMULATOR_GUIDE.md`: Cấu trúc tin nhắn, topic giao tiếp với robot.
- `PROJECT_TREE_SOURCE_ONLY.txt`: Cây thư mục nguồn của ứng dụng.
- `app/src/main/AndroidManifest.xml`: Khai báo components và permissions.
- Mã nguồn Java của các Activity & Service chính:
  - `CreateTaskActivity.java`: Logic tạo đơn hàng, tìm kiếm receiver, lưu Firebase, gửi MQTT command.
  - `TrackingActivity.java`: Logic theo dõi robot thời gian thực, kết nối MQTT broker, cầu nối ghi Firebase.
  - `NotificationActivity.java`: Danh sách thông báo in-app.
  - `FCMNotificationService.java`: Nhận token và notification FCM.
- Mã nguồn MQTT & Utils:
  - `mqtt/MqttFirebaseBridge.java` & `mqtt/MqttPayloadParser.java` & `model/RobotStatusMessage.java`.
  - `utils/NotificationUtils.java` & `utils/TaskStatus.java`.

---

## 2. Luồng hoạt động hiện tại (Task / MQTT / Notification)
- **Tạo đơn hàng (CreateTaskActivity)**: Người dùng nhập thông tin và xác nhận. Một task được ghi vào `/tasks/{senderUid}/{orderId}` và một bản copy được ghi vào `/recipientTasks/{receiverUid}/{orderId}`. App gửi lệnh `START_DELIVERY` qua MQTT.
- **Theo dõi (TrackingActivity & MqttFirebaseBridge)**: App đăng ký nhận `telemetry` (tọa độ robot), `status` (trạng thái robot), `ack` (xác nhận robot) qua MQTT broker. `MqttFirebaseBridge` nhận các tin nhắn này và ghi ngược lên Firebase để cập nhật trạng thái đơn hàng.
- **Thông báo (Notification & FCM)**: Khi có cập nhật trạng thái, bridge ghi log thông báo vào `/notifications/{uid}`. Phía client sử dụng Firebase listener để cập nhật giao diện `NotificationActivity`. App tắt thì FCM push chưa chạy thực tế do chưa có backend (scope demo chỉ có in-app/Firebase RTDB notification).

---

## 3. Các điểm hook BLE vào từng file
Để tích hợp BLE xác thực đóng/mở khoang robot, các file sẽ được can thiệp tại các điểm sau:
- **`AndroidManifest.xml`**: Hook các permission Bluetooth mới và uses-feature.
- **`utils/TaskStatus.java`**: Hook thêm các hằng số trạng thái BLE/Robot mới (`arrived_pickup`, `waiting_sender_load`, `sender_loaded`, `arrived_dropoff`, `waiting_receiver_unlock`).
- **`CreateTaskActivity.java`**: Hook logic tạo token BLE bằng `SecureRandom` khi confirm task, lưu token cùng các trường trạng thái vào cả node gốc `tasks` và copy `recipientTasks`.
- **`TrackingActivity.java`**:
  - Hook layout: Thêm nút xác thực với đúng label: Sender là **“Đã đặt hàng vào kho chứa”**, Receiver là **“Mở khoang hàng”**.
  - Hook database: Đọc task động từ `/tasks/{currentUid}/{taskId}` (nếu là Sender) hoặc `/recipientTasks/{currentUid}/{taskId}` (nếu là Receiver) để lấy `bleToken`, `slotId`, `robotId` và cập nhật UI.
  - Hook BLE flow: Xử lý click nút xác thực -> check/request Bluetooth permission dialog -> quét robot -> kết nối GATT -> write command JSON -> nhận response thành công -> cập nhật trạng thái Firebase.
    - Với **Sender**: Cập nhật cả `/tasks` và `/recipientTasks`.
    - Với **Receiver**: Chỉ cập nhật `/recipientTasks` (do Firebase rule chặn ghi vào node `/tasks` của Sender). Trạng thái của task gốc tại `/tasks` sẽ được cập nhật gián tiếp khi Robot nhận lệnh qua BLE thành công và phát tín hiệu MQTT status (`receiver_unlocked` hoặc `delivered`) về MQTT bridge.
- **`mqtt/MqttFirebaseBridge.java`**: Hook trạng thái mới `arrived_pickup` và `arrived_dropoff` nhận được từ MQTT để cập nhật Firebase và tạo notification.
- **`utils/NotificationUtils.java`**: Hook mapping thông báo tiếng Anh phù hợp cho các trạng thái robot đã đến điểm lấy/giao hàng.
- **`NotificationActivity.java`**: Hook khi nhấn vào notification sẽ mở `TrackingActivity` kèm đầy đủ thông tin: `orderId`, `senderUid`, `receiverUid`, `robotId`, `slotId`. Nút xác thực BLE sẽ nằm trực tiếp trong `TrackingActivity`.

---

## 4. Firebase Schema Mới (Dành cho Tasks và RecipientTasks)
Khi tạo task, cả `tasks/{senderUid}/{taskId}` và bản copy `recipientTasks/{receiverUid}/{taskId}` sẽ có thêm các trường sau:
```json
{
  "bleToken": "M4K2P9S8A1B2C3D4E5F6G7", // Token xác thực 128-bit bằng SecureRandom encoded Base64URL/Hex (dài từ 22-32 ký tự). Không log token này ra console hay report.
  "bleTokenCreatedAt": 1719878400000,   // Thời gian sinh token (milisecond)
  "bleState": "created",               // Trạng thái BLE: "created" | "sender_loaded" | "receiver_unlocked"
  "robotId": "defaultRobot",            // ID của Robot phục vụ
  "slotId": "slot1",                   // Khay chứa hàng được chọn (slot1/slot2/slot3)
  "senderLoadedConfirmed": false,      // Sender đã xác nhận xếp hàng và khóa khoang qua BLE
  "receiverUnlocked": false            // Receiver đã mở khoang hàng thành công qua BLE
}
```

---

## 5. BLE Protocol
Giao tiếp giữa App (GATT Client) và Robot (GATT Server) sử dụng định dạng JSON UTF-8.

### 5.1. Khởi tạo kết nối & Tìm kiếm Robot
- `BleManager` quét các thiết bị BLE xung quanh.
- Lọc thiết bị có device name có tiền tố (name prefix) `AutoDeliveryRobot` hoặc lọc theo UUID dịch vụ.
- Thiết lập ánh xạ: `robotId` (ví dụ: `defaultRobot`) tương ứng với tên thiết bị hoặc địa chỉ MAC khi quét được.

### 5.2. GATT UUIDs
Đây là demo UUID contract, Robot firmware bắt buộc phải expose đúng các UUID này để app có thể giao tiếp:
- **Service UUID**: `0000FFE0-0000-1000-8000-00805F9B34FB`
- **Characteristic UUID**: `0000FFE1-0000-1000-8000-00805F9B34FB`

### 5.3. Command Payloads
Khi Sender xác nhận đã xếp hàng (CONFIRM_LOADED):
```json
{
  "action": "CONFIRM_LOADED",
  "taskId": "RBT-1234",
  "slotId": "slot1",
  "token": "...",
  "timestamp": 1719878400000
}
```

Khi Receiver mở khoang hàng (OPEN_SLOT):
```json
{
  "action": "OPEN_SLOT",
  "taskId": "RBT-1234",
  "slotId": "slot1",
  "token": "...",
  "timestamp": 1719878500000
}
```

### 5.4. Response Payload từ Robot
```json
{
  "ok": true,
  "action": "CONFIRM_LOADED",
  "slotId": "slot1",
  "error": "" // Trống nếu thành công, hoặc chứa mã lỗi: "TOKEN_MISMATCH", "ROBOT_NOT_FOUND", "TIMEOUT", "SLOT_ERROR"
}
```

### 5.5. Phản ứng của Robot sau khi nhận lệnh
- **Sau CONFIRM_LOADED thành công**: Robot đóng khoang chứa hàng của slot tương ứng, publish trạng thái MQTT `sender_loaded` hoặc `going_to_destination`, và bắt đầu di chuyển tới điểm giao.
- **Sau OPEN_SLOT thành công**: Robot mở khoang chứa hàng của slot tương ứng, publish trạng thái MQTT `receiver_unlocked` hoặc `delivered`.

---

## 6. Rủi ro Runtime và Giải pháp
1. **Rào cản Ghi chéo Firebase (Permission Blocker)**:
   - *Rủi ro*: Receiver không có quyền write trực tiếp vào node `/tasks/{senderUid}/{taskId}` của Sender.
   - *Giải pháp*: Receiver chỉ cập nhật trạng thái xác nhận thành công lên bản copy `/recipientTasks/{receiverUid}/{taskId}` của mình. Task gốc trên `/tasks` sẽ do Robot gửi MQTT status về cho `MqttFirebaseBridge` xử lý ghi hoặc do Sender chạy app cập nhật.
2. **Quyền hạn Bluetooth trên Android 12+ (API 31+)**:
   - *Rủi ro*: Trên các phiên bản Android mới, việc scan/connect BLE yêu cầu quyền runtime `BLUETOOTH_SCAN` và `BLUETOOTH_CONNECT`. Nếu không xin quyền, ứng dụng sẽ crash.
   - *Giải pháp*: Sử dụng helper kiểm tra và hiển thị dialog xin quyền.
3. **Người dùng tắt Bluetooth**:
   - *Rủi ro*: Người dùng tắt Bluetooth trong cài đặt hệ thống.
   - *Giải pháp*: Hiển thị dialog yêu cầu bật Bluetooth. Nếu bấm "No", chặn hành động xác thực và hiển thị đúng thông báo cảnh báo: *"You will not be able to confirm the order with the robot to send or receive the goods. Please accept the request."*
4. **Mất kết nối BLE/Timeout giữa chừng**:
   - *Rủi ro*: Robot di chuyển hoặc nhiễu sóng làm kết nối GATT thất bại.
   - *Giải pháp*: Thiết lập bộ đếm thời gian (Timeout) 10 giây cho quá trình quét và kết nối. Nếu quá thời gian, giải phóng GATT client và báo lỗi rõ ràng cho người dùng.
