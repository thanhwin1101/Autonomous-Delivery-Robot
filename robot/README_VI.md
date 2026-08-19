# Robot Giao Hàng Tự Hành (AGV Delivery Robot)

Dự án này là hệ thống phần mềm hoàn chỉnh cho Robot Giao Hàng Tự Hành (AGV), bao gồm hai vi điều khiển chính giao tiếp với nhau qua chuẩn UART:
1. **Master (Raspberry Pi 4 - Python):** Xử lý định vị RTK GPS, bản đồ Google Maps, Firebase, và điều hướng State Machine.
2. **Slave (ESP32 - C++):** Đảm nhiệm điều khiển tốc độ động cơ đếm xung Encoder, tránh vật cản tự động bằng ToF, đóng/mở sóng Bluetooth (BLE) và điều khiển relay mở tủ.

*(Lưu ý: Hệ thống đã được nâng cấp từ mô hình kho tập kết đa ngăn sang mô hình **Peer-to-Peer (Giao hàng trực tiếp từ người sang người)** với 1 ngăn chứa duy nhất, tối ưu hóa quá trình nhận diện bằng ứng dụng điện thoại (1 chạm BLE)).*

---

## 1. Kiến Trúc Phần Cứng (Hardware Architecture)

### 🧠 Raspberry Pi 4 (Master)
- **Hệ điều hành:** Linux (Raspberry Pi OS) chạy Python 3.
- **Kết nối USB:**
  - `ttyUSB0`: Đọc dữ liệu NMEA (`$GNGGA`) từ module Quectel LC29H RTK GPS ở baudrate 9600.
  - `ttyACM0`: Giao tiếp UART với ESP32 ở baudrate 115200.
- **Nhiệm vụ chính:** Cỗ máy trạng thái (State Machine) điều hành toàn bộ vòng đời của đơn hàng.
- **Tính năng nổi bật:**
  - **Điều hướng thông minh:** Sử dụng Google Maps Directions API để tìm đường đi ngắn nhất đến điểm lấy hàng (A) và điểm giao hàng (B).
  - **Quản lý Năng lượng:** Ra lệnh tắt/mở sóng BLE trên ESP32 linh hoạt theo ngữ cảnh để tiết kiệm pin và tăng cường bảo mật.
  - **Xử lý Timeout & Mắc kẹt:** Tự động quay về trả hàng nếu quá 10 phút không có người nhận. Tự động khóa xe (`LOCKED_AT_HOME`) nếu người gửi cũng không nhận lại hàng.

### ⚙️ ESP32 (Slave)
- **Nhiệm vụ chính:** Đếm xung Encoder (Dead reckoning), điều khiển L298N (Tank Drive), tự động phanh khi gặp vật cản (ToF VL53L0X), phát sóng BLE Server chờ khách hàng kết nối, đọc công tắc hành trình và điều khiển relay mở khóa tủ.
- **Tính năng BLE:** Tích hợp sẵn trên SoC ESP32. Khách hàng dùng App quét sóng và bấm duy nhất 1 nút để mở tủ (Unified BLE Unlock).

---

## 2. Giao Thức Giao Tiếp UART (UART Protocol)

Hai bên giao tiếp qua UART với các gói tin (Packet) có kích thước cố định, sử dụng thuật toán **CRC8** để kiểm tra tính toàn vẹn, loại bỏ nhiễu.

### Master (Pi) ➡️ Slave (ESP32) [Kích thước: 5 Bytes]
Cấu trúc: `[0xAA] [Move_Cmd] [Solenoid_Cmd] [CRC8] [0xBB]`
- `0xAA`: Header.
- `Move_Cmd`: Lệnh điều hướng (`0x00` = Dừng, `0x01` = Tiến, `0x02` = Lùi, `0x03` = Rẽ Trái, `0x04` = Rẽ Phải).
- `Solenoid_Cmd`: Lệnh phần cứng (`0x00` = Tắt Relay, `0x01` = Mở tủ 1, `0x10` = Bật sóng BLE, `0x11` = Tắt sóng BLE).
- `CRC8`: Mã kiểm lỗi.
- `0xBB`: Footer.

### Slave (ESP32) ➡️ Master (Pi) [Kích thước: 7 Bytes]
Cấu trúc: `[0xAA] [Key_High] [Key_Low] [Limit_States] [Sensor_Flags] [CRC8] [0xBB]`
- `0xAA`: Header.
- `Key_High` & `Key_Low`: Gộp thành số nguyên 16-bit lưu mã PIN người dùng vừa nhập từ App qua BLE. Nếu chưa có mã mới, gửi `0xFFFF`.
- `Limit_States`: Byte dạng cờ (Chỉ sử dụng Bit 0 cho Tủ 1). `1` = Đóng cửa/Có hàng, `0` = Trống/Mở cửa.
- `Sensor_Flags`: Cờ cảm biến. Bit 0 = `1` nếu đang bị kẹt vật cản phía trước bởi cảm biến ToF.
- `CRC8`: Mã kiểm lỗi.
- `0xBB`: Footer.

---

## 3. Luồng Hoạt Động (Workflow)

Hệ thống hoạt động theo vòng đời khép kín:
1. **Chờ Lệnh (IDLE):** Xe đậu tại Trạm (HOME), tắt BLE. Chờ Firebase có đơn hàng trạng thái `"PENDING"`.
2. **Lấy Hàng Tại A:** Pi tính đường và chạy tới tọa độ Người Gửi. Đến nơi, bật sóng BLE. Người Gửi dùng App bấm "Mở Tủ", bỏ hàng vào và đóng lại.
3. **Giao Hàng Tại B:** Tắt sóng BLE, chạy tới tọa độ Người Nhận. Đến nơi, bật BLE. Người Nhận dùng App bấm "Mở Tủ" để lấy hàng.
4. **Về Trạm (HOME):** Sau khi hoàn tất giao hàng, xe tắt BLE và chạy ngược về HOME, chuyển về `IDLE`.
5. **Cơ Chế Khắc Phục Sự Cố (Timeout 10 Phút):**
   - Nếu B không nhận hàng: Xe tự động ôm hàng chạy về lại chỗ A.
   - Nếu A (hoặc B) gọi xe nhưng không tương tác: Hủy đơn, tự đi về HOME.
   - Nếu xe ôm hàng chạy về HOME: Bị khóa lại ở trạng thái `LOCKED_AT_HOME` và mở BLE vĩnh viễn chờ Người A lên tận Trạm để lấy lại đồ.
