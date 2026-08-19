# Báo cáo: Sửa lỗi Tracking và Route sau tích hợp BLE

Báo cáo này tổng hợp các sửa đổi đã thực hiện để khắc phục 5 lỗi runtime liên quan đến Tracking và Map Route (C→A→B flow) sau khi tích hợp BLE core.

---

## 1. Nguyên nhân 5 lỗi runtime ban đầu

1. **Hiển thị "Delivering" sai thời điểm**: Mặc định `TrackingActivity` sử dụng cứng logic `"Moving to " + dropoff` và status lấy từ Intent khiến robot luôn bị hiển thị là đang giao hàng tới B (Delivering) dù vừa mới tạo task và robot vẫn đang di chuyển tới điểm lấy hàng (C→A).
2. **Mất route ETA/distance khi mở lại app**: `TrackingActivity` phụ thuộc hoàn toàn vào dữ liệu Intent được truyền từ `CreateTaskActivity` thay vì đọc từ Firebase. Khi ứng dụng thoát ra vào lại hoặc mở qua Notification, Intent bị thiếu dữ liệu dẫn đến hiển thị `---`.
3. **Mất timeline icons**: File `activity_tracking.xml` sử dụng các thẻ `<TextView>` trống thay cho icon trong các bước của timeline, dẫn đến hiển thị lỗi ô vuông trống trơn trên giao diện.
4. **Không phân biệt được điểm A và điểm B trên Map**: Cả 2 điểm đều được vẽ bằng viền màu giống nhau (xanh dương) và không có text phân biệt khiến người dùng không biết đâu là điểm lấy hàng (A) đâu là điểm giao hàng (B). Gợi ý nhập liệu ở `CreateTaskActivity` cũng không rõ ràng.
5. **Route tĩnh, không cập nhật luồng C→A→B**: `TrackingActivity` chỉ vẽ lại route A→B mà bỏ qua vị trí thực tế của robot (C) trên MQTT, dẫn đến hiển thị sai vị trí và hướng đi.

---

## 2. Danh sách file đã sửa đổi

| File | Mô tả thay đổi |
| --- | --- |
| `ic_step_received.xml` (NEW) | Vector drawable: Icon clipboard cho bước Order Received. |
| `ic_step_preparing.xml` (NEW) | Vector drawable: Icon gear/settings cho bước Going to Pickup / At Pickup. |
| `ic_step_delivering.xml` (NEW) | Vector drawable: Icon delivery truck cho bước Delivering. |
| `ic_step_completed.xml` (NEW) | Vector drawable: Icon checkmark cho bước Delivered. |
| `activity_tracking.xml` | Thay đổi các `<TextView>` trống thành `<FrameLayout>` + `<ImageView>` tích hợp vector icons. Cập nhật `app:tint` thay vì `android:tint`. Gán IDs cho các text labels để điều khiển động bằng code Java. |
| `activity_create_task.xml` | Đổi hint nhập liệu thành "Pickup Point (A)" và "Delivery Point (B)". |
| `CreateTaskActivity.java` | Bổ sung ghi đè 12 fields metadata cho route vào Firebase, cập nhật layer vẽ Map với các marker vòng tròn mã màu: A (Xanh lá), B (Đỏ). |
| `TrackingActivity.java` | Viết lại toàn bộ theo kiến trúc **Firebase-first**, ưu tiên đọc Snapshot thay vì Intent. Triển khai phương thức `updateStatusUI()` cập nhật logic UI tương ứng với timeline C→A→B. Tự động dự phòng OSRM recalculation. |
| `MqttFirebaseBridge.java` | Bổ sung hàm `deriveActiveLeg()` chuyển đổi status MQTT sang giá trị `activeLeg` (`robot_to_pickup`, `at_pickup`, `pickup_to_delivery`, v.v) và ghi đè vào Firebase. |
| `MainActivity.java` | Truyền đầy đủ `senderUid`, `robotId`, `slotId`, và `distance` vào Intent cho TrackingActivity. Mở rộng danh sách task lấy tất cả các trạng thái trung gian (chỉ loại bỏ `delivered` / `cancelled`). |
| `HistoryItem.java` | Thêm các trường dữ liệu mới để chứa `senderUid`, `robotId`, `slotId`, `distance`. |

---

## 3. Các Firebase fields mới được thêm

Khi tạo Task (`CreateTaskActivity`), app sẽ push thêm các trường dữ liệu sau vào `taskMap`:

- `pickupName` & `dropoffName`: Tên địa điểm gốc lưu dưới dạng chuỗi (String).
- `pickupLat` & `pickupLng`: Tọa độ vĩ độ/kinh độ của điểm A.
- `dropoffLat` & `dropoffLng`: Tọa độ vĩ độ/kinh độ của điểm B.
- `deliveryDistanceKm`: Khoảng cách tuyến đường (thực tế hoặc Haversine fallback).
- `deliveryDurationMinutes`: Thời gian ước tính di chuyển tĩnh (ETA tĩnh ban đầu).
- `deliveryRouteSource`: Ghi chú nguồn dữ liệu route (`osrm`, `haversine`, `pending`).
- `deliveryRouteStatus`: Ghi chú trạng thái route.
- `deliveryRouteGeoJson`: GeoJSON string của polyline route A→B để phục hồi mà không cần gọi lại OSRM.
- `activeLeg`: Phân đoạn route hiện tại (Mặc định khi tạo là `robot_to_pickup`).

---

## 4. Flow hiển thị Map Route C → A → B

Được triển khai trong `TrackingActivity.updateMapRoute(status)`:

1. **Trước khi nhận hàng (Pending, Going to Pickup, Arrived Pickup, Waiting Sender Load)**:
   - Nếu có vị trí C của Robot (từ MQTT Telemetry): Vẽ route động từ **C → A**.
   - Nếu chưa có vị trí Robot C: Chỉ vẽ route preview mờ từ **A → B**.

2. **Sau khi đã lấy hàng (Sender Loaded, Picked Up, Going to Destination, Arrived Dropoff, v.v)**:
   - Nếu có vị trí C của Robot: Vẽ route động từ **C → B**.
   - Nếu không có vị trí C: Khôi phục route tĩnh từ **A → B**.

3. Điểm **A (Pickup)** luôn vẽ marker **Vòng tròn Xanh lá**, Điểm **B (Dropoff)** vẽ marker **Vòng tròn Đỏ**.

---

## 5. Logic xử lý khi khởi động lại / chưa có MQTT Telemetry

- **Phục hồi từ Firebase**: `TrackingActivity` giờ đây thiết lập một `ValueEventListener` lên nút Task. Nó lấy trực tiếp thông tin khoảng cách (`deliveryDistanceKm`), thời gian (`deliveryDurationMinutes`) và đặc biệt là Route Map Cache (`deliveryRouteGeoJson`). Nhờ vậy khi user vào lại app, tuyến đường hiện ra lập tức mà không bị `---`.
- **Dự phòng (Fallback)**: Nếu Firebase không lưu dữ liệu khoảng cách vì lý do nào đó (backward compatibility), App sẽ tự động thực hiện một truy vấn `OSRM` hoặc `Haversine` dự phòng ngầm ở dạng background.
- **Không có Telemetry (C)**: Robot chưa kết nối hoặc chưa gửi tọa độ thì map vẫn render điểm A, điểm B và text sẽ hiển thị là "Waiting for robot location...".

---

## 6. Kết quả Build và Lint

- **Build**: `BUILD SUCCESSFUL in 28s` (Không có lỗi compile-time, thoát mã 0).
- **Lint**: Hoàn thành chạy lintDebug. Lỗi `MissingPermission` trên Android 12+ (cho thao tác `ACTION_REQUEST_ENABLE` của Bluetooth) đã được khắc phục bằng cách sử dụng `@SuppressLint("MissingPermission")` ngay trên hàm `handleBleAction()`, vì quyền BLUETOOTH_CONNECT đã được xác nhận bằng helper trước đó. Tất cả Warning còn lại không gây Crash/Runtime exception.

---

## 7. Cách Test luồng Tracking bằng MQTT Simulator

Bạn có thể chạy thử nghiệm flow hiển thị ứng dụng mà không cần robot thực tế bằng cách gửi tin nhắn thủ công qua MQTT Broker.

1. **Tạo Task**: Mở app và tạo một Task với điểm A = "tran cao van" và B = "cau rong". Giao diện ban đầu sẽ báo "Waiting for robot location" thay vì "Delivering".
2. **Simulator Telemetry (Cập nhật vị trí C)**: Dùng MQTT client (như MQTT Explorer hoặc Node.js MQTT) gửi gói tin vào topic `v1/robot/defaultRobot/telemetry`:
   ```json
   {
       "robotId": "defaultRobot",
       "taskId": "<NHẬP_ORDER_ID_VỪA_TẠO>",
       "lat": 16.0500,
       "lng": 108.2000,
       "battery": 80,
       "timestamp": 1700000000000
   }
   ```
   *TrackingActivity sẽ lập tức vẽ route từ C(16.05, 108.2) đến A(Trần Cao Vân).*
3. **Simulator Status (Cập nhật quy trình)**: Gửi gói tin vào topic `v1/robot/defaultRobot/status`:
   ```json
   {
       "robotId": "defaultRobot",
       "taskId": "<NHẬP_ORDER_ID_VỪA_TẠO>",
       "status": "arrived_pickup",
       "timestamp": 1700000000000
   }
   ```
   *UI sẽ đổi thành "At Pickup Point" và hiện nút BLE Action (nếu là người gửi).*
4. **Hoàn tất lấy hàng**: Đổi `"status": "sender_loaded"` và gửi qua topic status. Route sẽ lập tức vẽ từ C → B (Cầu Rồng). Label đổi thành "Delivering" màu xanh.
