# Báo cáo Kiểm tra và Xác minh Tích hợp BLE (BLE Verify Report)

Sau quá trình kiểm tra (verify) chi tiết các yêu cầu, các lỗi và thiết sót đã được khắc phục hoàn toàn để đảm bảo giao thức BLE hoạt động đồng nhất và an toàn.

## 1. Định dạng Token thực tế (Token Format)
- Token thực tế được tạo ra thông qua `BleTokenUtils.generateToken()` hiện tại trả về chuỗi **Hex dài 32 ký tự** (được khởi tạo từ 16 byte / 128-bit qua `SecureRandom`). 

## 2. JSON Payload chuẩn cho Robot
Payload gửi qua BLE Characteristic `FFE1` đã được chuẩn hoá với các keys như sau (đặc biệt `action` đã được đổi sang chữ thường):
```json
{
  "action": "confirm_loaded", 
  "taskId": "RBT-1234",
  "slotId": "slot1",
  "token": "a1b2c3d4e5f607a8b9c0d1e2f3a4b5c6",
  "timestamp": 1691234567890
}
```
*(Ghi chú: `action` sẽ là `"confirm_loaded"` dành cho Sender và `"open_slot"` dành cho Receiver).*

## 3. UUID chính thức
- **Service UUID**: `0000FFE0-0000-1000-8000-00805F9B34FB` (Đã được hardcode làm bộ lọc duy nhất trong scan của `BleManager` để tìm đúng robot theo chuẩn).
- **Characteristic UUID**: `0000FFE1-0000-1000-8000-00805F9B34FB` (Ghi và đọc dữ liệu JSON).

## 4. Cấu trúc Firebase thực tế
Lớp `CreateTaskActivity.java` đã được fix lại để lưu đầy đủ các trường thiết yếu vào **cả 2 nhánh** (`tasks/{senderUid}/{taskId}` và `recipientTasks/{receiverUid}/{taskId}`):
- `bleToken`
- `bleTokenCreatedAt`
- `bleState`
- `senderLoadedConfirmed`
- `receiverUnlocked`
- `senderUid`
- `receiverUid`
- `robotId`
- `slotId`

## 5. Cập nhật MQTT Command/Status
- Model `RobotCommand.java` và `MqttPayloadParser.java` đã được bổ sung field `bleToken`.
- Lệnh MQTT `START_DELIVERY` khi được đẩy xuống robot tại thời điểm tạo đơn hàng **đã chứa `bleToken`** để robot lấy làm tham chiếu so khớp với token gửi qua BLE sau này.

## 6. Các tập tin thực sự đã được sửa chữa trong lần Verify này
1. `app/src/main/java/com/example/autodeliveryapp/ble/BleConstants.java`: Chuẩn hoá `ACTION_CONFIRM_LOADED` và `ACTION_OPEN_SLOT` thành chữ thường.
2. `app/src/main/java/com/example/autodeliveryapp/CreateTaskActivity.java`: Cập nhật logic lưu Firebase để có đủ `senderUid` và `receiverUid` trong `tasks`, đồng thời đẩy `bleToken` xuống MQTT method.
3. `app/src/main/java/com/example/autodeliveryapp/model/RobotCommand.java`: Thêm thuộc tính `bleToken`.
4. `app/src/main/java/com/example/autodeliveryapp/mqtt/MqttPayloadParser.java`: Đẩy field `bleToken` vào payload JSON `START_DELIVERY`.
5. `app/src/main/java/com/example/autodeliveryapp/ble/BleManager.java`: Bổ sung filter cực kỳ nghiêm ngặt bằng `Service UUID` để quét Bluetooth thay vì chỉ dựa vào tên.
6. `app/src/main/java/com/example/autodeliveryapp/TrackingActivity.java`: Bổ sung Popup System Dialog (`AlertDialog`) yêu cầu bật Bluetooth với lựa chọn "Yes/No" khi người dùng nhấn nhưng điện thoại đang tắt Bluetooth.

*Lưu ý: `AndroidManifest.xml` và `activity_tracking.xml` đã đáp ứng đầy đủ yêu cầu (có BottomSheet không che UI, có maxSdkVersion cho Bluetooth) nên không cần chỉnh sửa.*

## 7. Kết quả Build và Lint
- Lệnh biên dịch (`.\gradlew clean assembleDebug`) **thành công 100% (BUILD SUCCESSFUL in 10s)**. 
- Lệnh kiểm tra code (`.\gradlew lintDebug`) **hoàn thành** với một số warnings về việc Hardcode text trong XML không liên quan đến BLE, hoàn toàn ổn định và sẵn sàng chạy thử nghiệm thực tế.
