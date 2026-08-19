# Báo cáo Sửa lỗi Firebase Login (API Key & Rules Blocked)

Tài liệu này tổng hợp kết quả kiểm tra, sửa đổi và biên dịch dự án sau khi hoàn thành sửa lỗi phân hệ đăng nhập bằng Email và số điện thoại trên Firebase.

---

## 1. Danh sách các tệp tin đã kiểm tra
* [app/build.gradle.kts](file:///E:/final-year-project/AutoDeliveryApp-local/app/build.gradle.kts)
* [app/google-services.json.template](file:///E:/final-year-project/AutoDeliveryApp-local/app/google-services.json.template)
* [app/google-services.json](file:///E:/final-year-project/AutoDeliveryApp-local/app/google-services.json) (đã xóa khỏi Git và sinh tự động)
* [app/src/main/java/com/example/autodeliveryapp/LoginActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/LoginActivity.java)
* [app/src/main/java/com/example/autodeliveryapp/RegisterActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/RegisterActivity.java)
* [app/src/main/java/com/example/autodeliveryapp/Constants.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/Constants.java)
* [docs/FIREBASE_RULES_REQUIRED.md](file:///E:/final-year-project/AutoDeliveryApp-local/docs/FIREBASE_RULES_REQUIRED.md)
* [local.properties](file:///E:/final-year-project/AutoDeliveryApp-local/local.properties)

---

## 2. Danh sách các tệp tin đã sửa đổi
* **[app/build.gradle.kts](file:///E:/final-year-project/AutoDeliveryApp-local/app/build.gradle.kts)**:
  * Tạo Gradle task `generateGoogleServicesJson` tự động thay thế placeholder `"Firebase_API_Key"` bằng API key thật lấy từ `local.properties`.
  * Liên kết để task này chạy trước mọi tác vụ `process*GoogleServices`.
  * Ràng buộc để tiến trình build sẽ **FAIL** ngay lập tức nếu API key trong `local.properties` bị thiếu, trống hoặc vẫn là placeholder.
* **[app/.gitignore](file:///E:/final-year-project/AutoDeliveryApp-local/app/.gitignore)**:
  * Thêm `/google-services.json` để loại bỏ tệp sinh ra tự động khỏi việc theo dõi mã nguồn Git, tránh rò rỉ khóa bảo mật.
* **[app/src/main/java/com/example/autodeliveryapp/LoginActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/LoginActivity.java)**:
  * Loại bỏ hoàn toàn cơ chế tự khởi tạo/re-initialize `FirebaseApp` thủ công trong Java.
  * Xóa bỏ Tier 2 và Tier 3 phone lookup (không còn thực hiện truy vấn query vào node `/users`).
  * Giới hạn tra cứu số điện thoại chỉ đọc trực tiếp tại node con `/phoneIndex/{phoneNormalized}`.
  * Tinh chỉnh các khối ngoại lệ bắt lỗi Firebase Auth để hiển thị thông báo rõ ràng cho từng trường hợp: Sai mật khẩu, Tài khoản không tồn tại, API key không hợp lệ, và lỗi kết nối mạng.
* **[docs/FIREBASE_RULES_LOGIN_DEMO.json](file:///E:/final-year-project/AutoDeliveryApp-local/docs/FIREBASE_RULES_LOGIN_DEMO.json)**:
  * Tạo tệp JSON quy tắc bảo mật mẫu (demo rules), cấu hình chỉ cho phép đọc công khai (read) đối với node con `/phoneIndex/{phoneNormalized}` mà không cho phép tải toàn bộ danh sách `phoneIndex`.

---

## 3. Kết quả đối chiếu & Phát hiện lỗi cấu hình
* **google-services.json sai/placeholder**:
  * **CÓ**. Tệp `google-services.json` gốc có API key được thiết lập là `"Firebase_API_Key"` (chuỗi placeholder) thay vì API key thật, gây lỗi `API key not valid`.
  * Khắc phục: Đã chuyển đổi tệp gốc này thành `google-services.json.template` và đưa vào Gradle task để sinh tự động.
* **Package / applicationId mismatch**:
  * **KHÔNG**. Cả `applicationId` trong `build.gradle.kts` và `package_name` trong `google-services.json` đều khớp chính xác là `com.example.autodeliveryapp`.

---

## 4. Cấu hình Firebase Security Rules cho Phone Login
Để tính năng đăng nhập bằng số điện thoại hoạt động mà không vi phạm bảo mật (không mở public read cho toàn bộ `/users`), bộ quy tắc bảo mật Realtime Database bắt buộc phải chứa khai báo:
```json
"phoneIndex": {
  "$phoneNormalized": {
    ".read": "true",
    ".write": "auth != null && (!data.exists() || data.child('uid').val() == auth.uid)"
  }
}
```
* **Tại sao cần**: Cho phép người dùng chưa đăng nhập có thể thực hiện kiểm tra O(1) số điện thoại của mình để tìm ra Email liên kết trước khi thực hiện đăng nhập qua Firebase Auth SDK. Đồng thời không khai báo `.read` ở node cha `/phoneIndex` để ngăn chặn việc tải toàn bộ danh sách số điện thoại.

---

## 5. Kết quả Build & Lint
Các tác vụ build và kiểm tra tĩnh đã chạy thành công và ghi nhận tại thư mục `logs/`:
* **Compile & Assemble Debug**: Thành công (**`BUILD SUCCESSFUL`**), log ghi tại [logs/firebase_login_fix_build_utf8.log](file:///E:/final-year-project/AutoDeliveryApp-local/logs/firebase_login_fix_build_utf8.log).
* **Lint Debug**: Thành công (**`BUILD SUCCESSFUL`**), log ghi tại [logs/firebase_login_fix_lint_utf8.log](file:///E:/final-year-project/AutoDeliveryApp-local/logs/firebase_login_fix_lint_utf8.log). HTML report tại [app/build/reports/lint-results-debug.html](file:///E:/final-year-project/AutoDeliveryApp-local/app/build/reports/lint-results-debug.html).
* **Xác thực sinh file thành công**: Tệp XML sinh ra tại `app/build/generated/res/processDebugGoogleServices/values/values.xml` đã được nạp chính xác API key thật lấy từ `local.properties` thay vì chuỗi placeholder `"Firebase_API_Key"`.

---

## 6. Các bước người dùng cần thực hiện trong Firebase Console
1. **Dán bộ Rules bảo mật**:
   * Truy cập vào **Firebase Console** của dự án -> Chọn **Realtime Database** -> Vào tab **Rules**.
   * Sao chép nội dung từ tệp [docs/FIREBASE_RULES_LOGIN_DEMO.json](file:///E:/final-year-project/AutoDeliveryApp-local/docs/FIREBASE_RULES_LOGIN_DEMO.json) và dán đè vào, sau đó nhấn **Publish**.
2. **Kích hoạt Email/Password Auth**:
   * Vào **Authentication** -> Tab **Sign-in method** -> Đảm bảo nhà cung cấp **Email/Password** đã được bật (Enabled).
3. **Kiểm tra giới hạn Khóa trên GCP**:
   * Truy cập **Google Cloud Console -> APIs & Services -> Credentials**.
   * Kiểm tra API key có tên trùng khớp với key trong `local.properties`.
   * Đảm bảo cấu hình **API Restrictions** cho phép gọi dịch vụ **Identity Toolkit API** (Firebase Auth) và **Firebase Database API**.
