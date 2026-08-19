# Kiến trúc & Lưu đồ Vận hành Hệ thống AGV Giao hàng

Tài liệu này là bản thiết kế tổng thể (Blueprint) cho dự án AGV giao hàng tự hành, bao gồm sơ đồ phần cứng tối ưu, luồng thuật toán định vị và chi tiết 5 giai đoạn vận hành từ lúc nhận đơn đến khi về trạm sạc.

---

## 1. Kiến trúc Hệ thống & Xử lý Dữ liệu

### 1.1. Raspberry Pi 4 (High-Level Controller - Bộ脑 trung tâm)
- **Nhiệm vụ:** Quản lý kết nối Internet (MQTT), tính toán thuật toán, lập kế hoạch quỹ đạo.
- **Sensor Fusion (Dung hợp dữ liệu):** Nhận 3 luồng dữ liệu liên tục:
  1.  **Góc quay (Heading):** Từ cảm biến La bàn/IMU (MPU6050/9250) do ESP32 truyền lên.
  2.  **Quãng đường đi (Odometry):** Từ số xung (ticks) của 2 Encoder truyền lên.
  3.  **Tọa độ tuyệt đối (GPS):** Từ mạch định vị vệ tinh.
  $\rightarrow$ Pi 4 chạy bộ lọc (Kalman Filter / Complementary) để triệt tiêu nhiễu GPS và tính ra vị trí thực tế $(x, y, \theta)$ chính xác nhất của xe.
- **Path Planning & Following:** Dùng nội suy **Spline** làm mịn lộ trình bản đồ thành các Waypoints li ti. Sau đó chạy thuật toán **Pure Pursuit** để tính toán ra tốc độ riêng biệt cho bánh trái/phải giúp xe bám cua mượt mà.

### 1.2. ESP32 (Low-Level Controller - Xử lý Thời gian thực)
- **Nhiệm vụ:** Thực thi lệnh từ Pi 4, điều khiển Motor qua vòng lặp PID vận tốc, đọc cảm biến khẩn cấp (ToF) và quản lý hệ thống bảo mật mở cửa Không Chạm (BLE).
- **Phản xạ nhanh (Real-time):** Quyết định dừng phanh khẩn cấp độc lập với Pi 4 khi phát hiện vật cản ở cự ly nguy hiểm.
- **Xử lý BLE JSON:** Nhận luồng lệnh từ App Android dưới định dạng JSON (VD: `{"action":"verify_token", "token":"..."}`). Parse JSON bằng thư viện `ArduinoJson` để phân luồng xử lý: Xác thực chạm (Proximity) hoặc Mở khóa vật lý (Open Slot).

---

## 2. Hướng dẫn Nối dây Phần cứng

> [!IMPORTANT]
> Toàn bộ hệ thống (Pi 4, ESP32, Mạch công suất, Các Module cảm biến) phải được nối chung **GND (Ground)** để đồng nhất điện áp logic.

### 2.1. Kết nối Mạch công suất & Động cơ (ESP32 $\rightarrow$ L298N / BTS7960)
- Nguồn 12V/24V cấp vào mạch cầu H.
- Các chân băm xung (PWM) và chân đảo chiều từ mạch cầu H nối với các GPIO xuất PWM của ESP32.
- **Encoder 2 bánh:** Nối vào 4 chân GPIO có hỗ trợ Ngắt ngoài (External Interrupt) của ESP32 (ví dụ `34, 35` và `36, 39`) để đếm xung vận tốc cao.

### 2.2. Kết nối Cảm biến Giao tiếp I2C (SDA/SCL)
ESP32 sử dụng chuẩn I2C (SDA: `GPIO 21`, SCL: `GPIO 22`) để đọc đồng thời nhiều thiết bị trên cùng một bus:
- **Cảm biến góc (IMU - MPU6050 hoặc MPU9250 9 trục):** Đọc dữ liệu góc yaw. *Lưu ý: Bắt buộc phải viết hàm Calibration kỹ càng lúc xe khởi động để khử trôi (drift).*
- **Cảm biến ToF phía trước:** Quét khoảng cách đầu xe (VD: VL53L0X) để tránh vật cản.
- **Cảm biến ToF trong khoang chứa:** Đặt dưới đáy hoặc trên nắp khoang hàng để quét xem khoang đã thực sự trống hàng hay chưa.

### 2.3. Hệ thống Cửa thông minh (Khóa điện & Cảm biến từ)
- **Chốt cửa điện từ (Relay):** Chân Tín hiệu (Signal) nối `GPIO` (ESP32) $\rightarrow$ Module Relay $\rightarrow$ Chốt khóa điện từ 12V.
- **Cảm biến hành trình / Cảm biến từ cửa:** Chân `NO/NC` nối `GPIO` của ESP32 (khai báo `INPUT_PULLUP`), chân còn lại nối `GND`. Dùng để báo trạng thái cửa đang khép hay mở.

### 2.4. Kết nối Serial (UART)
- **USB UART 1:** Pi 4 $\leftrightarrow$ ESP32. Dùng để truyền/nhận các lệnh cấu trúc như tốc độ, dữ liệu MPU, xung Encoder, trạng thái ToF và cửa.
- **USB UART 2:** Pi 4 $\leftrightarrow$ Module GPS. Đọc các câu lệnh chuẩn NMEA (GPGGA, GPRMC).

---

## 3. Chi tiết Vận hành qua 5 Giai đoạn

### GIAI ĐOẠN 1: Nhận đơn & Di chuyển tới Người gửi
**Trạng thái hệ thống:** `GOING_TO_SENDER`

1.  **Nhận lệnh:** App Android tạo Token 128-bit độc lập cho đơn hàng, sau đó gửi lệnh MQTT `START_TRIP` chứa tọa độ Người gửi, `Slot_ID` và mã `Token` xuống Server/Pi 4.
2.  **Lưu trữ Offline:** Pi 4 lưu trữ `Token` vào RAM cục bộ (sẵn sàng cho ESP32 xác thực mở cửa cả khi xuống hầm/mất mạng).
3.  **Làm mịn lộ trình:** Pi 4 nội suy Spline bản đồ thành chuỗi tọa độ Waypoints cong và mịn.
4.  **Bám đường (Pure Pursuit):**
    *   Pi 4 dung hợp vị trí (GPS + MPU + Encoder) $\rightarrow$ Tìm "Điểm nhìn trước" (Look-ahead point) $\rightarrow$ Tính toán tốc độ cần thiết của bánh trái ($V_L$) và bánh phải ($V_R$).
    *   Gửi lệnh UART xuống ESP32 liên tục mỗi 100ms: `SPEED:v_left:v_right`.
5.  **ESP32 thực thi:**
    *   Chuyển đổi `v_left`, `v_right` thành PWM chạy qua vòng lặp PID.
    *   Định kỳ mỗi 50ms, gửi UART lên Pi 4: `ODO:ticks_L:ticks_R` kèm dữ liệu góc `MPU`.
    *   Liên tục quét ToF phía trước mỗi 30ms.

### GIAI ĐOẠN 2: Đến vị trí Người gửi & Bỏ hàng vào xe
**Trạng thái hệ thống:** `WAITING_SENDER`

1.  **Dừng xe:** Pi 4 gửi lệnh dừng `SPEED:0:0`. ESP32 hãm phanh lập tức.
2.  **Khởi động bảo mật BLE:** Pi 4 gửi UART `BLE:ON:Token`. ESP32 tạo GATT Server, phát sóng Bluetooth tên `AGV_DELIVERY_01` và gán biến `Local_Token = Token`.
3.  **Báo Server:** Pi 4 gửi MQTT báo trạng thái `ARRIVED_PICKUP` để App hiện nút thao tác.
4.  **Xác thực 2 Bước (2-Step BLE Verification):**
    *   **Bước 1 (Verify):** Người gửi chạm nút trên App $\rightarrow$ App kết nối BLE và gửi JSON `{"action":"verify_token", "token":"..."}`. ESP32 kiểm tra trùng khớp với `Local_Token` $\rightarrow$ Trả về `{"ok":true}` mà không bung chốt.
    *   **Bước 2 (Open):** App hiện giao diện Tên người nhận và nút "Mở khoang hàng". Người gửi bấm nút $\rightarrow$ App gửi JSON `{"action":"open_slot", "token":"..."}`. ESP32 kích Relay bung chốt cửa $\rightarrow$ Báo lên Pi 4: `DOOR:OPENED`.
5.  **Đóng cửa:** Người gửi bỏ hàng, sập cửa. ESP32 ngắt Relay khóa chốt an toàn $\rightarrow$ Báo lên Pi 4: `DOOR:LOCKED`.
6.  **Xuất phát:** Pi 4 nhận `DOOR:LOCKED`, cập nhật Firebase và bắt đầu hành trình.

### GIAI ĐOẠN 3: Di chuyển tới Người nhận
**Trạng thái hệ thống:** `GOING_TO_RECEIVER`

-   Cơ chế bám đường tương tự Giai đoạn 1. Tắt sóng BLE để tiết kiệm điện.

> [!WARNING]
> **KỊCH BẢN ĐẶC BIỆT: Xử lý vật cản ToF thời gian thực (Áp dụng cho GĐ 1, 3, 5)**
> 1. **Phát hiện:** ToF ESP32 đọc khoảng cách `< 30cm` $\rightarrow$ cắt PWM về 0 ngay lập tức, gửi UART khẩn: `WARN:OBSTACLE`.
> 2. **Pi 4 Đóng băng:** Nhận cảnh báo, Pi 4 *Đóng băng (Pause)* luồng tính toán Pure Pursuit, báo MQTT `{"status":"BLOCKED"}`.
> 3. **Hết cản trở:** Khi vật cản rời đi (`> 50cm`), ESP32 gửi UART: `WARN:CLEAR`. Pi 4 chạy tiếp thuật toán bám đường.

### GIAI ĐOẠN 4: Đến vị trí Người nhận & Giao hàng
**Trạng thái hệ thống:** `WAITING_RECEIVER`

1.  Quy trình dừng xe và nạp `Token` tương tự GĐ 2.
2.  Người nhận nhận được Push Notification, mở App $\rightarrow$ App yêu cầu chạm để quét xác thực (Proximity Check).
3.  **Xác thực 2 bước** diễn ra tương tự: App gửi `verify_token` $\rightarrow$ Nhận phản hồi đúng $\rightarrow$ Hiện tên người gửi và nút Mở khóa $\rightarrow$ Người dùng bấm nút $\rightarrow$ App gửi `open_slot` để lấy hàng.
4.  **Kiểm tra an toàn khoang hàng:**
    *   Sau khi đóng cửa, ESP32 khóa chốt.
    *   Pi 4 gửi lệnh kiểm tra: `BOX:CHECK`.
    *   ESP32 đọc cảm biến trong khoang $\rightarrow$ Gửi UART: `BOX:EMPTY` (nếu đã lấy hàng).
5.  Pi 4 gửi MQTT báo hoàn thành đơn hàng.

### GIAI ĐOẠN 5: Quay trở về Trạm sạc
**Trạng thái hệ thống:** `RETURNING` $\rightarrow$ `IDLE`

1.  Pi 4 chạy Pure Pursuit để lái xe quay về tọa độ Home.
2.  Khi về tới nơi, Pi 4 gửi UART `BLE:OFF` để tiết kiệm pin.
3.  Cập nhật trạng thái xe thành `IDLE` trên Firebase và kích hoạt chế độ tự động sạc.
