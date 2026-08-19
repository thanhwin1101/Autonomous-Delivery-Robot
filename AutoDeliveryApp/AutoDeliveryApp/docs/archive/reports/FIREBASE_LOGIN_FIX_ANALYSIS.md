# Phân tích lỗi Firebase Login (API Key & Rules Blocked)

Tài liệu này phân tích nguyên nhân kỹ thuật gây ra lỗi đăng nhập bằng Email và số điện thoại trên thiết bị thực tế, đồng thời đề xuất phương án xử lý chi tiết.

---

## 1. Nguyên nhân lỗi "API key not valid" khi đăng nhập bằng Email
* **Hiện tượng**: Khi gọi `signInWithEmailAndPassword`, Firebase Auth SDK báo lỗi:  
  `Error: An internal error has occurred. [ API key not valid. Please pass a valid API key. ]`
* **Phân tích kỹ thuật**:
  * Tệp [google-services.json](file:///E:/final-year-project/AutoDeliveryApp-local/app/google-services.json) hiện tại đang cấu hình trường `api_key[0].current_key` là chuỗi placeholder: `"Firebase_API_Key"`.
  * Google-services gradle plugin biên dịch tệp JSON này thành các tài nguyên Android tĩnh (như `@string/google_api_key`), khiến Firebase SDK khởi tạo ứng dụng mặc định bằng key placeholder `"Firebase_API_Key"`. Khi ứng dụng gửi request đăng nhập lên máy chủ Firebase Auth, request bị từ chối do API key không hợp lệ.
  * Mặt khác, API key thật đang nằm ở tệp [local.properties](file:///E:/final-year-project/AutoDeliveryApp-local/local.properties) (`Firebase_API_Key=AIzaSy...`) nhưng lại chưa được hệ thống build của Gradle nạp vào cấu hình hoặc mã nguồn.
* **Giải pháp**:
  * Chuyển đổi tệp `google-services.json` thành `google-services.json.template` đóng vai trò làm mẫu (template).
  * Viết một Gradle task trong `app/build.gradle.kts` để đọc `Firebase_API_Key` từ `local.properties` tại root và tự động thay thế chuỗi `"Firebase_API_Key"` trong template để tạo ra tệp `app/google-services.json` thật trước khi tác vụ `processDebugGoogleServices` chạy.
  * Phương thức khởi tạo `FirebaseApp` mặc định của SDK Android sẽ tự động lấy key từ tệp XML tài nguyên được sinh ra mà không cần chỉnh sửa/re-initialize thủ công trong mã nguồn Java.

---

## 2. Nguyên nhân lỗi "Firebase Rules blocked" khi đăng nhập bằng Số điện thoại
* **Hiện tượng**: Đăng nhập bằng số điện thoại báo lỗi:  
  `Cannot look up phone: Firebase Rules blocked. Please use email...`
* **Phân tích kỹ thuật**:
  * Khi người dùng nhập số điện thoại để đăng nhập, `LoginActivity` thực hiện truy vấn 3 cấp độ (3 Tiers) để tìm kiếm Email tương ứng:
    * **Tier 1**: Truy vấn khóa `/phoneIndex/{phoneNormalized}`.
    * **Tier 2**: Truy vấn node `/users` lọc theo thuộc tính `phoneNormalized`.
    * **Tier 3**: Truy vấn node `/users` lọc theo thuộc tính `phone`.
  * Bộ quy tắc bảo mật được đề xuất trong [FIREBASE_RULES_REQUIRED.md](file:///E:/final-year-project/AutoDeliveryApp-local/docs/FIREBASE_RULES_REQUIRED.md) cấu hình bảo vệ thông tin người dùng rất chặt chẽ:
    * Node `/users/{uid}` chỉ cho phép người dùng đã xác thực (logged in) đọc/ghi chính họ: `".read": "auth != null && auth.uid == $uid"`.
    * Do đó, việc gọi truy vấn lọc (query) trên toàn bộ node `/users` ở Tier 2 và Tier 3 từ một client chưa được xác thực (chưa đăng nhập) sẽ luôn luôn bị máy chủ Firebase RTDB trả về lỗi **Permission Denied** (Rules blocked).
    * Thêm vào đó, Tier 1 truy cập `/phoneIndex` cũng bị chặn nếu rules yêu cầu `auth != null` khi người dùng chưa đăng nhập.
* **Giải pháp**:
  * Cần cấu hình rule cho phép đọc công khai (không cần login) đối với các node con cụ thể của `/phoneIndex` (ví dụ: `/phoneIndex/{phoneNormalized}`) nhưng **nghiêm cấm** việc đọc danh sách toàn bộ node `/phoneIndex` để tránh rò rỉ dữ liệu.
  * Tinh chỉnh logic `LoginActivity.java`: Loại bỏ hoàn toàn Tier 2 và Tier 3 (không query node `/users` khi chưa đăng nhập). Chỉ sử dụng Tier 1 (đọc trực tiếp node con `/phoneIndex/{phoneNormalized}`).
  * Nếu việc đọc `/phoneIndex/{phoneNormalized}` thất bại với mã lỗi Permission Denied, hiển thị thông báo rõ ràng cho người dùng theo yêu cầu.

---

## 3. Các tệp cần sửa đổi
* [app/build.gradle.kts](file:///E:/final-year-project/AutoDeliveryApp-local/app/build.gradle.kts): Thêm Gradle task nạp API key động và tạo `google-services.json`.
* [app/src/main/java/com/example/autodeliveryapp/LoginActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/LoginActivity.java):
  * Loại bỏ các hàm truy vấn `lookupByPhoneNormalized()` và `lookupByRawPhone()`.
  * Sửa lỗi hiển thị lỗi FirebaseAuth chi tiết hơn (wrong password, user not found, invalid API key, network error).
* [docs/FIREBASE_RULES_LOGIN_DEMO.json](file:///E:/final-year-project/AutoDeliveryApp-local/docs/FIREBASE_RULES_LOGIN_DEMO.json): Tạo bộ rules Realtime Database demo cho phép đọc `/phoneIndex/{phoneNormalized}` công khai trước khi đăng nhập.

---

## 4. Những điều người dùng cần tự kiểm tra trong Firebase Console
1. **Bật Email/Password Auth**: Truy cập **Firebase Console -> Authentication -> Sign-in method** và kích hoạt nhà cung cấp **Email/Password**.
2. **Kiểm tra giới hạn khóa (API Key Restrictions)**: Nếu API key client (`AIzaSy...`) bị giới hạn trên Google Cloud Console, hãy đảm bảo khóa này được quyền gọi dịch vụ **Identity Toolkit API** (Firebase Auth) và **Google Firebase Database**.
3. **Cài đặt Firebase Database Security Rules**:
   * Sao chép nội dung quy tắc bảo mật từ tệp [docs/FIREBASE_RULES_LOGIN_DEMO.json](file:///E:/final-year-project/AutoDeliveryApp-local/docs/FIREBASE_RULES_LOGIN_DEMO.json) được tạo mới.
   * Dán vào tab **Rules** trong phân hệ **Realtime Database** trên Firebase Console và nhấn **Publish**.
