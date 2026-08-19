# Báo cáo Phân tích và Kế hoạch Sửa lỗi UI & Routing

Báo cáo này tổng hợp các lỗi phát hiện khi kiểm thử thủ công ứng dụng **Auto Delivery App** trên thiết bị thực và đề xuất giải pháp kỹ thuật chi tiết để khắc phục.

---

## 1. Danh sách tài liệu và log đã đọc
* [README.md](file:///E:/final-year-project/AutoDeliveryApp-local/README.md)
* [PROJECT_TREE_SOURCE_ONLY.txt](file:///E:/final-year-project/AutoDeliveryApp-local/PROJECT_TREE_SOURCE_ONLY.txt)
* [docs/PROJECT_STATUS_REPORT.md](file:///E:/final-year-project/AutoDeliveryApp-local/docs/PROJECT_STATUS_REPORT.md)
* [docs/FULL_PROJECT_AUDIT_AND_FLOW_REPORT.md](file:///E:/final-year-project/AutoDeliveryApp-local/docs/FULL_PROJECT_AUDIT_AND_FLOW_REPORT.md)
* [docs/FIREBASE_RULES_REQUIRED.md](file:///E:/final-year-project/AutoDeliveryApp-local/docs/FIREBASE_RULES_REQUIRED.md)
* [docs/MQTT_INTEGRATION_REPORT.md](file:///E:/final-year-project/AutoDeliveryApp-local/docs/MQTT_INTEGRATION_REPORT.md)
* [logs/gradle_tasks_audit.log](file:///E:/final-year-project/AutoDeliveryApp-local/logs/gradle_tasks_audit.log)
* [logs/build_pre_manual_test_audit.log](file:///E:/final-year-project/AutoDeliveryApp-local/logs/build_pre_manual_test_audit.log)
* [logs/lint_debug_audit.log](file:///E:/final-year-project/AutoDeliveryApp-local/logs/lint_debug_audit.log)

---

## 2. Phân nhóm lỗi thực tế và nguyên nhân chi tiết

### Nhóm A: Lỗi Giao diện (UI/Theme/Edge-to-Edge)
1. **Màu sắc khó nhìn (Text bị chìm, hint quá nhạt, button không nổi bật)**:
   * *Nguyên nhân*: Dự án đang có tệp `values-night/themes.xml` hoặc cấu hình màu sắc trong `colors.xml`/`themes.xml` bị kế thừa sai tông màu giữa Light Mode và Dark Mode. Nền card hoặc input dùng màu tối nhưng chữ vẫn giữ màu mặc định (đen/xám) hoặc ngược lại.
   * *Giải pháp*: Chuẩn hóa bảng màu, ép ứng dụng chạy Light Mode duy nhất (`AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)`) để phục vụ demo nhất quán, hoặc cập nhật toàn bộ thuộc tính màu chữ (`android:textColor` và `android:textColorHint`) trên các layout.
2. **Kích thước chữ nhỏ và thiếu độ tương phản**:
   * *Nguyên nhân*: Các text view trong card hoặc status badge đang sử dụng cỡ chữ nhỏ (10sp-12sp) và màu xám nhạt.
   * *Giải pháp*: Tăng kích thước chữ tối thiểu lên 14sp-16sp đối với body/status và sử dụng màu có độ tương phản cao (chữ trắng trên nền tối, chữ đen trên nền sáng).
3. **App che mất thanh trạng thái hệ thống (Status Bar)**:
   * *Nguyên nhân*: Gọi tính năng Edge-to-Edge thông qua `WindowCompat.setDecorFitsSystemWindows(false)` mà không cấu hình cộng thêm padding top cho root view hoặc toolbar, hoặc không dùng `android:fitsSystemWindows="true"`. Ngoài ra, biểu tượng thanh trạng thái bị ẩn hoặc hiển thị màu trắng trên nền sáng.
   * *Giải pháp*: Thiết lập `statusBarColor` và bật cờ `lightStatusBar = true` trong theme để hiển thị icon màu tối trên nền trắng. Đồng thời tinh chỉnh `EdgeToEdgeHelper.java` để cộng padding top/bottom an toàn.
4. **Nút Back hiển thị một ô xám, mất biểu tượng**:
   * *Nguyên nhân*: Thuộc tính `android:src` hoặc `app:srcCompat` của ImageButton bị thiếu, hoặc sử dụng drawable không tồn tại hoặc bị tint màu trùng màu nền.
   * *Giải pháp*: Khai báo biểu tượng back rõ ràng (`@drawable/ic_back`), đặt tint thích hợp và kích thước touch target tối thiểu 48dp.
5. **Icon Bottom Navigation và Password eye mờ nhạt**:
   * *Nguyên nhân*: Selector trong `@color/bottom_nav_color.xml` cấu hình màu sắc không có độ tương phản đủ tốt cho trạng thái unselected, và input password không có `passwordToggleTint`.
   * *Giải pháp*: Cập nhật selector với màu xanh lá đậm (selected) và màu xám đậm (unselected).

### Nhóm B: Lỗi Chức năng định vị & Tuyến đường (OSRM & Geocoding)
1. **Không định vị được khi nhập địa chỉ tự do**:
   * *Nguyên nhân*: Hàm `getCoordinates` trong `CreateTaskActivity.java` thực hiện so khớp chuỗi tĩnh trong bản đồ địa danh cố định. Khi nhập hẻm/số nhà như "k112/92 tran cao van", nó không tìm thấy vì trong danh sách tĩnh chỉ có "tran cao van".
   * *Giải pháp*: Normalize chuỗi tìm kiếm (chuyển thường, bỏ dấu tiếng Việt, loại bỏ tiền tố hẻm/số nhà như "k112/92", "k112" để lấy tên đường chính "tran cao van"). Tạo lớp `AddressResolver` để hỗ trợ lọc địa chỉ thông minh.
2. **Không vẽ được đường phố thực tế mà vẽ đường thẳng (chỉ chạy Haversine fallback)**:
   * *Nguyên nhân*: OSRM API thất bại hoặc bị crash/lỗi mạng. Cần kiểm tra thứ tự tọa độ truyền vào OSRM. OSRM yêu cầu thứ tự là `{longitude},{latitude}`, nếu truyền ngược lại thành `{latitude},{longitude}` (lỗi phổ biến khi lấy từ LatLng), OSRM sẽ trả về lỗi không tìm thấy đường và kích hoạt Haversine Fallback.
   * *Giải pháp*: Đảo thứ tự tọa độ trong chuỗi định dạng URL OSRM thành `longitude,latitude` và ghi nhận log rõ ràng để chẩn đoán.
3. **Bản đồ ban đầu zoom ra toàn thế giới**:
   * *Nguyên nhân*: Khi bản đồ tải xong, chưa chỉ định camera di chuyển về vị trí mặc định tại Đà Nẵng.
   * *Giải pháp*: Đặt vị trí mặc định của bản đồ tại Đà Nẵng `(16.047079, 108.206230)` với mức zoom 12-13 ngay sau khi map load style thành công.

---

## 3. Danh sách file dự kiến cần sửa đổi
* **Màu sắc & Giao diện (Resources)**:
  * `app/src/main/res/values/colors.xml` (Chuẩn hóa primary/secondary/hint colors)
  * `app/src/main/res/values/themes.xml` (Thêm các thuộc tính hiển thị status bar sáng)
  * `app/src/main/res/color/bottom_nav_color.xml` (Cập nhật màu sắc các tab)
* **Giao diện Layouts (XML)**:
  * `activity_login.xml` (Tăng tương phản input, nút login chữ trắng)
  * `activity_register.xml` (Tăng tương phản nút bấm và ô nhập liệu)
  * `activity_forgot_password.xml` (Chỉnh sửa hiển thị text)
  * `activity_main.xml` (Định dạng danh sách task)
  * `activity_create_task.xml` (Sửa nút back trên map, layout chọn slot)
  * `activity_tracking.xml` (Sửa hiển thị thông số hành trình và MQTT status)
  * `item_notification.xml`, `item_history.xml`, `item_active_task.xml` (Sửa độ tương phản chữ trên nền card tối)
* **Logic Java**:
  * `app/src/main/java/com/example/autodeliveryapp/EdgeToEdgeHelper.java` (Sửa padding hệ thống)
  * `app/src/main/java/com/example/autodeliveryapp/LoginActivity.java` (Ép Light Mode ứng dụng)
  * `app/src/main/java/com/example/autodeliveryapp/CreateTaskActivity.java` (Sửa logic Geocoding Đà Nẵng, OSRM URL, default camera)
  * `app/src/main/java/com/example/autodeliveryapp/TrackingActivity.java` (Sửa OSRM URL, vẽ tuyến đường thực tế)

---

## 4. Kế hoạch thực hiện theo thứ tự ưu tiên
1. **Bước 1**: Ép ứng dụng chạy ở Light Mode để loại bỏ hoàn toàn các xung đột màu sắc khi điện thoại tự chuyển Dark Mode.
2. **Bước 2**: Cấu hình bảng màu trong `colors.xml` và `themes.xml` để đảm bảo chữ trắng trên nút xanh lá, hint rõ ràng và thanh trạng thái (status bar) hiển thị rõ icon màu tối.
3. **Bước 3**: Sửa `EdgeToEdgeHelper.java` và chỉnh insets padding để tránh app đè status bar.
4. **Bước 4**: Kiểm tra và cập nhật các layout để nút Back hiển thị đúng biểu tượng mũi tên, text trên card tối được chuyển sang màu sáng hoặc đổi nền card sang màu sáng tương phản chữ tối.
5. **Bước 5**: Sửa default camera trong `CreateTaskActivity` và `TrackingActivity` trỏ về Đà Nẵng.
6. **Bước 6**: Xây dựng thuật toán normalize địa chỉ tự do trong `CreateTaskActivity` để lọc được tên đường chính từ chuỗi nhập vào (ví dụ: loại bỏ "k112/92" lấy "tran cao van").
7. **Bước 7**: Xác minh và sửa đổi URL request OSRM đảm bảo định dạng đúng `{longitude},{latitude}`, kiểm tra kết quả trả về để vẽ đường bộ thực tế và cập nhật Firebase schema.

---

## 5. Rủi ro ảnh hưởng hệ thống
* **Firebase**: Cần cẩn trọng khi cập nhật hoặc ghi thêm trường dữ liệu vị trí khi tạo task (`pickupLat`, `pickupLng`...) để không làm vỡ cấu trúc JSON cũ mà các màn hình khác đang đọc.
* **MQTT**: Việc thay đổi logic trong `TrackingActivity` không được làm ảnh hưởng đến luồng kết nối và đăng ký nhận telemetry của MqttManager.
* **MapLibre**: Việc gỡ bỏ hoặc vẽ lại lớp LineLayer cần thực hiện an toàn để tránh crash khi Map style chưa tải xong.
