# Quy trình hoạt động (Workflow) AGV Robot Giao Hàng

Tài liệu này mô tả chi tiết quy trình (workflow) toàn vẹn của Robot giao hàng tự hành (AGV) dựa trên kiến trúc **ESP32 + App BLE Không Chạm (Contactless) + Cảm biến Laser ToF**. 
Mô hình hoạt động: Giao hàng ngang hàng (Peer-to-Peer) giữa 2 người dùng (A gửi cho B) thông qua hệ thống tủ có các khoang hàng (Slots) riêng biệt trên Robot.

---

## 1. Trình tự các bước (Text)

### Bước 1: Người Gửi (A) Đặt Đơn & Gọi Xe
1. **Khởi tạo đơn:** Người dùng A sử dụng App để tạo đơn hàng mới, chọn khoang hàng (Slot), vị trí của A (Sender Location) và vị trí của B (Receiver Location). App sẽ tự động sinh ra một `Token` 128-bit dạng Hex bảo mật ngẫu nhiên cho đơn hàng này. App đẩy trạng thái đơn lên **Firebase Realtime Database** với `status = "PENDING"` và gửi qua giao thức MQTT để gọi xe.
2. **Xe di chuyển tới A:** Pi 4 đang ở trạng thái IDLE bắt được đơn hàng qua MQTT. Nó lưu `Token` vào bộ nhớ, tính toán đường đi và chạy tới vị trí của A. Trạng thái `NAVIGATING_TO_A`.
3. **Đến nơi (ARRIVED_A):** Xe tới vị trí Người A và dừng lại.

### Bước 2: Mở Tủ Qua App (2-Step BLE Verification) & Bỏ Hàng
1. **Bật BLE & Thông báo:** Khi xe đến nơi (ARRIVED_A), Pi 4 tự động bật sóng BLE trên ESP32 và nạp `Token` vào ESP32. App hiển thị thông báo đẩy (Push Notification) mời Người A ra mở tủ.
2. **Xác thực Bước 1 (Verify):** Người A mở App và chạm nút "Quét và xác thực AGV". App kết nối BLE và truyền gói tin JSON `{"action":"verify_token", "token":"..."}`. ESP32 kiểm tra nếu khớp Token sẽ trả về `{"ok":true}`.
3. **Xác thực Bước 2 (Open):** App nhận phản hồi đúng sẽ hiển thị nút "Mở khoang hàng". Người A bấm nút này, App tiếp tục gửi lệnh `{"action":"open_slot", "token":"..."}`. Lúc này ESP32 mới thực sự bung chốt khoang hàng và báo về Pi 4.
4. **Bỏ hàng:** Người A bỏ hàng vào tủ, sau đó sập cửa. ESP32 nhận diện qua Công tắc hành trình (Limit Switch) và báo cho Pi 4. Pi 4 cập nhật trạng thái `LOADED`.
*(Lưu ý Timeout: Nếu quá 10 phút Người A không thao tác gửi hàng, xe sẽ tự hủy đơn và quay về tọa độ HOME).*

### Bước 3: Di Chuyển Tới Người Nhận (B)
1. **Tắt sóng BLE:** Để bảo mật và tiết kiệm điện, trong suốt quá trình xe chạy, sóng BLE sẽ bị tắt hoàn toàn.
2. **Tìm đường:** Pi 4 sử dụng tọa độ Người B (`recv_lat`, `recv_lon`) để tìm đường trực tiếp đến điểm giao.
3. **Di chuyển:** Pi 4 chạy Pure Pursuit gửi các lệnh `SPEED` xuống ESP32. Cập nhật `status = "NAVIGATING_TO_B"`.
4. **Tránh vật cản (ToF I2C):** ESP32 liên tục quét laser VL53L0X. Nếu cản dưới 30cm, tự động phanh gấp báo Pi 4.

### Bước 4: Mở Khóa Lấy Hàng (2-Step BLE Verification)
1. **Đến nơi:** Pi 4 xác nhận đã đến tọa độ B, gửi lệnh dừng, bật lại sóng BLE, nạp `Token` và cập nhật Firebase `status = "ARRIVED_B"`.
2. **Thông báo người nhận:** App của Người B nhận thông báo đẩy.
3. **Xác thực Bước 1 (Verify):** Người B mở App và bấm "Chạm để quét và xác thực AGV". App kết nối BLE và gửi lệnh JSON `"verify_token"`. ESP32 kiểm tra trùng khớp Token và phản hồi `{"ok":true}`.
4. **Xác thực Bước 2 (Open):** App hiện tên "Đơn hàng từ: [Tên người gửi A]" và nút "Mở khoang hàng". Người B bấm nút, App gửi JSON `"open_slot"`. Tủ hàng bung ra an toàn.

### Bước 5: Hoàn Tất Đơn Hàng / Xử lý Timeout
- **Trường hợp Thành Công:**
  1. **Lấy hàng:** Người nhận B lấy đồ. Công tắc hành trình (Limit Switch) nhả ra. ESP32 có thể dùng thêm ToF/Loadcell để xác nhận khoang thực sự rỗng.
  2. **Hoàn tất:** Pi 4 cập nhật `status = "DONE"`.
  3. **Về Trạm:** Xe tự động vô hiệu hóa BLE, tính toán đường đi và chạy về tọa độ `HOME` (Trạng thái `IDLE`).
- **Trường hợp Timeout (10 Phút tại B):**
  1. Quá 10 phút không có người nhận đồ, xe cập nhật trạng thái `TIMEOUT_RETURNING`.
  2. Hệ thống gửi thông báo cho Người Gửi (A) về việc B không nhận hàng.
  3. Tắt sóng BLE, AGV quay về vị trí của Người Gửi (A).
  4. Về đến nơi (`RETURNED_TO_A`), gửi thông báo cho A ra nhận lại hàng qua App bằng luồng BLE 2 bước.
- **Trường hợp Khóa Xe Tại Trạm (LOCKED_AT_HOME):**
  1. Nếu trả hàng mà 10 phút Người A vẫn không ra nhận, xe chạy thẳng về Trạm (HOME).
  2. Xe giữ sóng BLE sáng vĩnh viễn ở trạng thái `LOCKED_AT_HOME`. Người A có thể ra Trạm quét BLE để mở khóa lấy hàng bất kỳ lúc nào.

---

## 2. Sơ đồ luồng xử lý chi tiết (ASCII Flowchart)

### Giai đoạn 1 & 2: Gọi xe tới Người Gửi (A) & Mở tủ bỏ hàng

```text
[ APP NGƯỜI GỬI A ]
         | (Tạo đơn: Sinh Token 128-bit)
         v
[ FIREBASE DB / MQTT ] (status: PENDING)
         |
         v
 +-------------------+
 | Pi 4 (Master)     | -> [ Tính route tới Người A ]
 +-------------------+ -> [ Xe chạy tới A (NAVIGATING_TO_A) ]
         |
         v
    [ ĐẾN NƠI (ARRIVED_A) ]
         | -> Bật BLE & nạp Token
         v
 [ App Người A quét & chạm Xác thực ]
         |
         v
 [ App gửi JSON "verify_token" ] -> ESP32 kiểm tra -> [ Trả về "ok":true ]
         |
         v
 [ App hiện nút "Mở khoang hàng" ]
         |
         v
 [ App gửi JSON "open_slot" ] -> ESP32 kích Relay Mở Tủ
         |
    (Người A bỏ hàng & Đóng tủ)
         |
         v
 [ ESP32 đọc Limit Switch ] --- UART ---> [ Pi 4 ghi nhận đã có hàng ]
```

### Giai đoạn 3: Di chuyển tới Người Nhận (B)

```text
      +-------------------+
      | Pi 4 (Master)     |
      | Tính route tới B  |
      +---------+---------+
                |
                v
      +-------------------+
      | Pi 4 bám đường    |
      | Gửi lệnh SPEED    |
      +---------+---------+
                | (UART)
                v
      +-------------------+
      | ESP32 (Slave)     |
      | Đọc cảm biến ToF  |
      +---------+---------+
                |
        [ Khoảng cách? ]
       /                \
  ( < 30cm )         ( > 30cm )
      /                  \
+-----------+       +---------------+
| Tự động   |       | Chạy PID Motor|
| STOP &    |       | Xe đi tiếp    |
| Báo cờ cản|       |               |
+-----------+       +---------------+
```

### Giai đoạn 4: Tại điểm giao & Luồng BLE Xác thực 2 Bước (Người B)

```text
[ AGV ĐẾN ĐIỂM GIAO B & DỪNG LẠI ]
               |
               v
  +--------------------------+
  | Pi 4: Cập nhật Firebase  |
  | Bật BLE & Nạp Token      |
  +------------+-------------+
               |
               v
  +--------------------------+
  | Người B nhận Push Notif  |
  | Mở App, bấm nút Xác thực |
  +------------+-------------+
               |
               v
  +--------------------------+
  | App gửi JSON:            |
  | {"action":"verify_token"}|
  +------------+-------------+
               |
               v
  +--------------------------+
  | ESP32 kiểm tra Token     |
  +------------+-------------+
         |            |
   (SAI) |            | (ĐÚNG)
         v            v
 [ Trả về "ok":false] +--------------------------+
                      | ESP32 trả về "ok":true   |
                      | KHÔNG mở cửa             |
                      +------------+-------------+
                                   |
                                   v
                      +--------------------------+
                      | App hiện tên Người gửi A |
                      | Hiện nút "Mở khoang hàng"|
                      | Người B bấm nút Mở       |
                      +------------+-------------+
                                   |
                                   v
                      +--------------------------+
                      | App gửi JSON:            |
                      | {"action":"open_slot"}   |
                      +------------+-------------+
                                   |
                                   v
                      +--------------------------+
                      | ESP32 kích Relay Mở Tủ   |
                      | Tủ hàng bung ra tự động! |
                      +--------------------------+
```
