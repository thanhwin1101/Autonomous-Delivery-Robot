# Báo cáo Audit Toàn Dự Án Trước Manual Test

## 1. Phạm vi kiểm tra
Báo cáo này được lập nhằm mục đích kiểm tra và đánh giá toàn diện mã nguồn tĩnh của dự án **Auto Delivery App** trước khi tiến hành quá trình kiểm thử thủ công (manual runtime testing) trên thiết bị thật hoặc giả lập. Phạm vi bao gồm:
* Cấu trúc và phân bổ tệp tin mã nguồn.
* Các tệp tin cấu hình Gradle (`build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`).
* Tệp Manifest của ứng dụng (`AndroidManifest.xml`).
* Tất cả các lớp mã nguồn Java trong package `com.example.autodeliveryapp`.
* Kiểm tra tính nhất quán giữa tài liệu mô tả hiện có (`docs/*.md`, `README.md`) và mã nguồn thực tế.
* Nhận diện và đánh giá các rủi ro kỹ thuật liên quan đến Firebase, MQTT, Map/Routing và bảo mật cấu hình.

*Lưu ý: Báo cáo này hoàn toàn dựa trên phương pháp static audit (phân tích mã nguồn tĩnh) và build-verification. Kết quả runtime chỉ mang tính chất dự đoán từ logic code trừ khi có bằng chứng chạy thực tế.*

---

## 2. Cây thư mục và inventory
Sau khi thực hiện quét hệ thống bằng PowerShell, cấu trúc dự án và danh sách tệp tin đã được ghi nhận:
* **PROJECT_TREE_FULL.txt**: Chứa toàn bộ cây thư mục của dự án bao gồm cả thư mục build, cấu hình `.idea`, `.gradle`.
* **PROJECT_TREE_SOURCE_ONLY.txt**: Chỉ chứa cây thư mục của phân hệ mã nguồn chính nằm trong `app/src`.
* **PROJECT_FILES_FULL.csv**: Danh sách đầy đủ mọi tệp tin trong thư mục gốc dự án.
* **PROJECT_FILES_SOURCE_ONLY.csv**: Danh sách tệp tin mã nguồn thực tế (loại trừ các thư mục sinh tự động hoặc cache build).

### Phân tích phân bổ loại tệp tin (File Extension Summary)
Loại tệp tin có số lượng lớn nhất trong dự án (loại trừ build/cache):
1. **.java**: 34 tệp (Định nghĩa logic xử lý của ứng dụng Android Native Java).
2. **.xml**: 33 tệp (Cấu hình giao diện layout, menu, drawable, colors và Manifest).
3. **.properties**: 4 tệp (Cấu hình SDK, API key và môi trường cục bộ).
4. **.kts**: 3 tệp (Kotlin DSL Gradle scripts cấu hình build).
5. **.toml**: 1 tệp (`libs.versions.toml` quản lý tập trung các phiên bản dependency).

**Kết luận về kiểu dự án**: Đây là một dự án **Android Native viết bằng Java** thuần túy, sử dụng Gradle Kotlin DSL làm hệ thống build chính, có tích hợp Firebase SDK, HiveMQ MQTT Client và MapLibre Android SDK. Không có sự pha trộn giữa Kotlin (phần logic app), Flutter hay React Native.

---

## 3. Build/lint hiện tại
Quá trình build thử nghiệm trên môi trường cục bộ sử dụng JDK 17 (JBR đi kèm Android Studio) và Gradle 9.4.1 thu được kết quả:

| Check | Log | Kết quả | Ghi chú |
|---|---|---|---|
| `gradlew tasks` | [gradle_tasks_audit.log](file:///E:/final-year-project/AutoDeliveryApp-local/logs/gradle_tasks_audit.log) | **BUILD SUCCESSFUL** | Hoàn thành trong 7 giây. Các tác vụ build, cài đặt và xác minh của Gradle được nhận diện đầy đủ. |
| `assembleDebug` | [build_pre_manual_test_audit.log](file:///E:/final-year-project/AutoDeliveryApp-local/logs/build_pre_manual_test_audit.log) | **BUILD SUCCESSFUL** | Hoàn thành trong 8 giây. Ứng dụng biên dịch thành công mà không có lỗi cú pháp hay thiếu import nào. |
| `lintDebug` | [lint_debug_audit.log](file:///E:/final-year-project/AutoDeliveryApp-local/logs/lint_debug_audit.log) | **BUILD SUCCESSFUL** | Hoàn thành trong 38 giây. Báo cáo HTML được ghi ra tại `app/build/reports/lint-results-debug.html`. Không có lỗi nghiêm trọng làm gián đoạn quá trình biên dịch. |

*Lưu ý: Biên dịch thành công chỉ chứng minh code không lỗi cú pháp và liên kết thư viện thành công. Trạng thái hoạt động trên thiết bị thực tế (runtime) vẫn cần được xác nhận.*

---

## 4. Công nghệ xác nhận từ code
Dựa trên phân tích trực tiếp tệp build và import trong code, cấu trúc công nghệ thực tế bao gồm:

| Layer | Technology | Evidence | Status |
|---|---|---|---|
| **Android app** | Android Native Java (compileSdk 36, targetSdk 36, minSdk 24) | `app/build.gradle.kts` và các tệp `.java` | **Matches code** |
| **Backend/server data** | Firebase Realtime Database | Thư viện `firebase-database` trong dependencies, gọi qua `FirebaseDatabase.getInstance(Constants.DB_URL)` | **Matches code** |
| **Authentication** | Firebase Auth | Thư viện `firebase-auth` trong dependencies, gọi qua `FirebaseAuth.getInstance()` | **Matches code** |
| **Robot communication** | MQTT qua HiveMQ Cloud | Thư viện `com.hivemq:hivemq-mqtt-client:1.3.15` trong dependencies | **Matches code** |
| **MQTT broker** | HiveMQ Cloud (TCP TLS 8883) | Logic trong `MqttManager.java` sử dụng cổng SSL 8883 mặc định | **Matches code** |
| **Map rendering** | MapLibre Android SDK | Thư viện `org.maplibre.gl:android-sdk:11.0.0` trong dependencies | **Matches code** |
| **Map style/tile provider**| MapTiler Cloud | Sử dụng `Constants.MAP_STYLE_URL` truyền key từ `BuildConfig.MAPTILER_API_KEY` | **Matches code** |
| **Routing** | OSRM Public Routing API | Gửi truy vấn HTTP GET tới `https://router.project-osrm.org/route/v1/...` trong `CreateTaskActivity.java` và `TrackingActivity.java` | **Matches code** |
| **Distance fallback** | Haversine Formula | Phương thức `haversineKm()` trong `CreateTaskActivity.java` và `TrackingActivity.java` | **Matches code** |
| **Push notification** | Firebase Cloud Messaging (FCM) Client-side | Thư viện `firebase-messaging` và lớp `FCMNotificationService` kế thừa `FirebaseMessagingService` | **Matches code** |

---

## 5. Entry point và app components
Khai báo thành phần ứng dụng trong `AndroidManifest.xml` được đối chiếu với mã nguồn thực tế:

| Component | Type | File/Class | Purpose inferred from code | Manifest registered? |
|---|---|---|---|---|
| **LoginActivity** | Activity | [LoginActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/LoginActivity.java) | Entry point chính của ứng dụng (Launcher), chịu trách nhiệm đăng nhập bằng email hoặc số điện thoại. | **Có** (Có chứa `android.intent.action.MAIN` và `android.intent.category.LAUNCHER`) |
| **RegisterActivity** | Activity | [RegisterActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/RegisterActivity.java) | Đăng ký tài khoản người dùng mới (Username, Email, Phone, Password). Ghi nhận thông tin vào Firebase DB. | **Có** |
| **ForgotPasswordActivity**| Activity | [ForgotPasswordActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/ForgotPasswordActivity.java) | Gửi email yêu cầu đặt lại mật khẩu qua Firebase Auth. | **Có** |
| **MainActivity** | Activity | [MainActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/MainActivity.java) | Trang chủ hiển thị danh sách các task đang hoạt động (pending/delivering) của người dùng hiện tại. | **Có** (launchMode = singleTask) |
| **HistoryActivity** | Activity | [HistoryActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/HistoryActivity.java) | Hiển thị lịch sử tất cả các task đã tạo/nhận (cả terminal states như delivered/cancelled). | **Có** (launchMode = singleTask) |
| **NotificationActivity** | Activity | [NotificationActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/NotificationActivity.java) | Hiển thị danh sách thông báo hoạt động của người dùng nhận từ Firebase Database. | **Có** (launchMode = singleTask) |
| **ProfileActivity** | Activity | [ProfileActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/ProfileActivity.java) | Hiển thị thông tin cá nhân của user hiện tại và xử lý Đăng xuất (Sign out). | **Có** (launchMode = singleTask) |
| **CreateTaskActivity** | Activity | [CreateTaskActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/CreateTaskActivity.java) | Giao diện tạo nhiệm vụ giao hàng mới: nhập địa điểm đi/đến, chọn slot robot, tính quãng đường và confirm. | **Có** |
| **TrackingActivity** | Activity | [TrackingActivity.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/TrackingActivity.java) | Theo dõi hành trình của Robot thời gian thực thông qua bản đồ và kết nối MQTT lắng nghe telemetry. | **Có** |
| **FCMNotificationService**| Service | [FCMNotificationService.java](file:///E:/final-year-project/AutoDeliveryApp-local/app/src/main/java/com/example/autodeliveryapp/FCMNotificationService.java) | Lắng nghe và xử lý token FCM mới hoặc các tin nhắn đẩy nhận được từ xa. | **Có** (Đăng ký hành động `com.google.firebase.MESSAGING_EVENT`) |

---

## 6. Code vs Docs/Logs Consistency Matrix
Đối chiếu các tuyên bố trong tài liệu hiện tại với mã nguồn thực tế:

| Claim | Source file containing claim | Code evidence | Status | Notes |
|---|---|---|---|---|
| **Dự án là Android Native Java** | `PROJECT_STATUS_REPORT.md` | `app/build.gradle.kts` và các lớp `.java` sử dụng ngôn ngữ Java. | **Matches code** | Cấu trúc source-only hoàn toàn là file Java. |
| **Package là com.example.autodeliveryapp** | `app/build.gradle.kts` | `namespace = "com.example.autodeliveryapp"` | **Matches code** | Đồng bộ trên Manifest và tất cả tệp nguồn Java. |
| **Ứng dụng chỉ có tiếng Anh** | `PROJECT_STATUS_REPORT.md` | Không tìm thấy ký tự tiếng Việt hoặc file tài nguyên tiếng Việt trong `/app/src/main/res/values/strings.xml`. | **Matches code** | Tất cả chuỗi hiển thị đã được làm sạch sang tiếng Anh. |
| **Tính năng chuyển đổi ngôn ngữ đã bị xóa** | `FULL_PROJECT_AUDIT_AND_FLOW_REPORT.md`| Màn hình `ProfileActivity` chỉ có nút Logout, không có cơ chế chuyển locale. | **Matches code** | Logic chuyển ngôn ngữ đã bị gỡ hoàn toàn. |
| **Thư mục values-vi đã bị xóa** | `PROJECT_STATUS_REPORT.md` | `PROJECT_TREE_SOURCE_ONLY.txt` chỉ hiển thị `app\src\main\res\values` và `values-night`. | **Matches code** | Thư mục `values-vi` thực tế đã bị xóa trong source code. Tuy nhiên, tệp cache của build cũ trong `app/build/...` có thể vẫn giữ vết tạm (sẽ biến mất khi clean build). |
| **LocaleHelper đã bị xóa** | `PROJECT_STATUS_REPORT.md` | Không tìm thấy tệp `LocaleHelper.java` trong thư mục utils hoặc bất cứ đâu. | **Matches code** | Lớp này đã được gỡ bỏ khỏi dự án. |
| **Firebase được dùng làm backend chính** | `PROJECT_STATUS_REPORT.md` | Các lớp Activity gọi Firebase Auth và Realtime Database. | **Matches code** | Firebase RTDB lưu trữ trạng thái tasks, users, notifications, slots. |
| **Firebase Auth được tích hợp** | `PROJECT_STATUS_REPORT.md` | Sử dụng `FirebaseAuth.getInstance()` trong Login, Register, Forgot Password. | **Matches code** | Đăng ký và đăng nhập dựa hoàn toàn vào Firebase Auth. |
| **Firebase Realtime Database được tích hợp** | `PROJECT_STATUS_REPORT.md` | Lấy dữ liệu qua `FirebaseDatabase.getInstance(Constants.DB_URL)`. | **Matches code** | Các node dữ liệu được đồng bộ hóa trực tiếp. |
| **FCM push backend** | `FCM_CLOUD_FUNCTIONS_PLAN.md` | Dự án không có mã nguồn cho Cloud Functions hay Admin SDK. | **Matches code** | Phía client có xử lý token nhưng push backend thực tế chưa triển khai. |
| **Dùng HiveMQ MQTT cho robot** | `PROJECT_STATUS_REPORT.md` | Sử dụng `com.hivemq:hivemq-mqtt-client` trong `MqttManager.java`. | **Matches code** | Thư viện client hoạt động trên nền tảng HiveMQ. |
| **MQTT runtime được xác nhận** | `MQTT_INTEGRATION_REPORT.md` | Không tìm thấy log chạy thực tế hoặc bằng chứng kết nối thành công với broker. | **Requires runtime test** | Cần thiết bị/giả lập kết nối thực tế để kiểm chứng. |
| **Sử dụng MapLibre để vẽ bản đồ** | `PROJECT_STATUS_REPORT.md` | `MapLibre.getInstance(this)` trong `CreateTaskActivity` và `TrackingActivity`. | **Matches code** | MapLibre SDK được gọi trực tiếp để hiển thị bản đồ nền. |
| **Dùng MapTiler làm nhà cung cấp style** | `PROJECT_STATUS_REPORT.md` | `Constants.MAP_STYLE_URL` chỉ đến style Streets-v2 của MapTiler. | **Matches code** | Style được tải thông qua API key cấu hình trong `local.properties`. |
| **OSRM dùng để tính toán tuyến đường** | `PROJECT_STATUS_REPORT.md` | Gửi HTTP GET tới `router.project-osrm.org`. | **Matches code** | Tính khoảng cách thực và hình học tuyến đường lái xe. |
| **Cơ chế Haversine fallback** | `PROJECT_STATUS_REPORT.md` | Phương thức `haversineKm` tính khoảng cách đường chim bay khi OSRM lỗi. | **Matches code** | Fallback hoạt động đúng logic khi OSRM API lỗi hoặc mất mạng. |
| **Robot hardware runtime integration** | `MQTT_INTEGRATION_REPORT.md` | Dự án không có phần cứng robot kết nối sẵn. | **Requires runtime test** | Cần kết nối với simulator/robot thật để xác nhận. |
| **PROJECT_TREE chính xác** | - | `PROJECT_TREE_SOURCE_ONLY.txt` phản ánh chính xác cấu trúc thư mục hiện tại. | **Matches code** | Tệp cây thư mục nguồn hoàn toàn khớp với thực tế. |
| **Logs hiện tại** | - | Thư mục `logs/` chứa log cũ từ các phiên làm việc trước đó. | **Stale / outdated** | Các tệp log trong thư mục gốc là log tĩnh của quá khứ. Cần chạy lại để sinh log mới. |
| **Dọn dẹp tệp tạm thành công** | `CLEANUP_CANDIDATES_REPORT.md` | Không tìm thấy các file `search*.txt`, `non_ascii.txt`, `files_to_translate.txt` trong thư mục gốc. | **Matches code** | Quá trình dọn dẹp các tệp tạm của các pass trước đã hoàn thành. |
| **Trạng thái Build khớp** | `PROJECT_STATUS_REPORT.md` | Báo cáo tuyên bố build thành công trên nền Gradle. | **Matches code** | Khớp với kết quả chạy `assembleDebug` thành công. |
| **Trạng thái Runtime khớp** | `PROJECT_STATUS_REPORT.md` | Báo cáo ghi nhận runtime chưa được xác thực (Build-verified only). | **Matches code** | Tuyệt đối không overclaim về tính ổn định runtime khi chưa test thiết bị. |

---

## 7. Feature Status Matrix

Dưới đây là bảng trạng thái các tính năng chính của ứng dụng được đánh giá thông qua đọc mã nguồn tĩnh:

| Feature | Status | Evidence from code | Runtime required? | Risk |
|---|---|---|---|---|
| **Register** | Implemented in code | `RegisterActivity.java` gọi `createUserWithEmailAndPassword` và lưu thông tin vào Firebase. | Có | Thấp |
| **Login** | Implemented in code | `LoginActivity.java` hỗ trợ đăng nhập bằng Email trực tiếp hoặc Số điện thoại (qua phoneIndex lookup). | Có | Trung bình (Rủi ro Lookup phoneIndex bị từ chối nếu cấu hình rules bảo mật chặt chẽ). |
| **Forgot password** | Implemented in code | `ForgotPasswordActivity.java` gọi `sendPasswordResetEmail`. | Có | Thấp |
| **Profile** | Implemented in code | `ProfileActivity.java` đọc dữ liệu `/users/{uid}` hiển thị giao diện. | Có | Thấp |
| **Logout** | Implemented in code | `ProfileActivity.java` gọi `signOut()` và xóa stack hoạt động chuyển về Login. | Có | Thấp |
| **Home/Main** | Implemented in code | `MainActivity.java` lắng nghe `/tasks/{userId}` để hiển thị danh sách đơn hàng xử lý. | Có | Thấp |
| **Bottom Navigation** | Implemented in code | Kế thừa lớp cơ sở `BottomNavActivity.java` liên kết menu XML. | Có | Thấp |
| **Create Task** | Implemented in code | `CreateTaskActivity.java` xử lý toàn bộ logic nhập liệu, tính toán tuyến đường và chọn slot. | Có | Trung bình (Rủi ro nghẽn luồng OSRM API công cộng). |
| **Phone/receiver lookup**| Implemented in code | Logic truy vấn 3 cấp độ (Tiers) trong `CreateTaskActivity.java` để tìm `receiverUid` qua số điện thoại. | Có | Trung bình (Permission denied khi truy vấn node user của người khác). |
| **Firebase task write** | Implemented in code | `CreateTaskActivity.java` ghi thông tin đơn hàng tại `/tasks/{senderUid}/{orderId}`. | Có | Thấp |
| **recipientTasks write** | Implemented in code | `CreateTaskActivity.java` ghi thông tin liên kết tại `/recipientTasks/{receiverUid}/{orderId}`. | Có | **Cao** (Sender ghi trực tiếp vào node của Receiver - rủi ro bảo mật Firebase Rules). |
| **notifications write** | Implemented in code | Ghi trực tiếp thông báo vào `/notifications/{uid}/{notifId}` cho cả sender và receiver. | Có | **Cao** (Sender ghi trực tiếp vào node thông báo của Receiver). |
| **Robot slot reservation**| Implemented in code | `CreateTaskActivity.java` gọi transaction để đổi trạng thái slot sang `occupied` tại `/robotSlots/defaultRobot/{slotId}`. | Có | Trung bình (Tránh tranh chấp slot - cần kiểm tra tính nguyên tử của Transaction). |
| **Robot slot release** | Implemented in code | `SlotUtils.java` chuyển trạng thái slot về `available` khi task chuyển sang trạng thái terminal. | Có | Trung bình (Phụ thuộc vào việc Client bắt được sự kiện terminal status). |
| **Map display** | Implemented in code | `MapLibre` được cấu hình trên `mapView` của màn hình Create và Tracking. | Có | Trung bình (Lỗi nếu API key MapTiler hết hạn hoặc cấu hình sai). |
| **Route calculation** | Implemented in code | Lớp `CreateTaskActivity` gọi OSRM API để lấy danh sách tọa độ và vẽ GeoJson. | Có | Trung bình |
| **Haversine fallback** | Implemented in code | Logic tính toán khoảng cách đường chim bay hoạt động khi OSRM trả về mã lỗi HTTP hoặc ngoại lệ. | Có | Thấp |
| **Tracking screen** | Implemented in code | `TrackingActivity.java` kết nối MQTT và hiển thị tuyến đường trên bản đồ. | Có | Trung bình |
| **History screen** | Implemented in code | `HistoryActivity.java` đọc toàn bộ đơn hàng từ `/tasks/{userId}`. | Có | Thấp |
| **Notification screen** | Implemented in code | `NotificationActivity.java` đọc từ `/notifications/{userId}`. | Có | Thấp |
| **Firebase in-app notification**| Implemented in code| Tạo bản ghi thông báo trong cơ sở dữ liệu khi trạng thái robot thay đổi (`MqttFirebaseBridge`). | Có | Trung bình |
| **FCM client token** | Implemented in code | Lấy token trong `FCMNotificationService.java` và lưu vào DB hoặc SharedPreferences. | Có | Thấp |
| **Real FCM push backend** | **Not implemented** | Không có máy chủ gửi tin nhắn đẩy. FCM chỉ hoạt động khi nhận được tác vụ đẩy ngoài (phải dùng Firebase Console để test thủ công). | Không | Trung bình (Giới hạn tính năng đẩy thực tế). |
| **MQTT command publish** | Implemented in code | Gửi lệnh `START_DELIVERY` qua topic `autodelivery/{env}/robots/{robotId}/command`. | Có | Trung bình (Cần broker hoạt động). |
| **MQTT telemetry sub** | Implemented in code | Đăng ký topic `autodelivery/{env}/robots/{robotId}/telemetry`. | Có | Trung bình |
| **MQTT status subscribe** | Implemented in code | Đăng ký topic `autodelivery/{env}/robots/{robotId}/status`. | Có | Trung bình |
| **MQTT ack handling** | Implemented in code | Đăng ký topic `autodelivery/{env}/robots/{robotId}/ack` và cập nhật trường `mqttAck` trong Firebase. | Có | Trung bình |
| **MQTT-to-Firebase bridge**| Implemented in code | `MqttFirebaseBridge.java` nhận tin nhắn MQTT và trực tiếp ghi ngược dữ liệu lên Firebase Database. | Có | **Cao** (Tất cả cập nhật của robot đều do client-side ghi vào DB. Nếu client tắt app, DB sẽ không được cập nhật). |
| **Robot hardware integration**| **Requires hardware** | Cần phần cứng thật hoặc phần mềm giả lập để bắn dữ liệu MQTT. | Có | **Cao** (Khó kiểm thử nếu không có simulator chuẩn). |

---

## 8. Screen Flow (Luồng Màn Hình)
Quy trình điều hướng của người dùng qua các màn hình được thiết kế như sau:

```mermaid
graph TD
    A[LoginActivity] -- Đăng nhập thành công --> D[MainActivity]
    A -- Đăng ký tài khoản --> B[RegisterActivity]
    B -- Đăng ký thành công --> D
    B -- Quay lại --> A
    A -- Quên mật khẩu --> C[ForgotPasswordActivity]
    C -- Nhận email/Quay lại --> A
    
    D -- Chọn tab Lịch sử --> E[HistoryActivity]
    D -- Chọn tab Thông báo --> F[NotificationActivity]
    D -- Chọn tab Cá nhân --> G[ProfileActivity]
    
    D -- Nhấn nút Start New Delivery --> H[CreateTaskActivity]
    H -- Xác nhận tạo task thành công --> I[TrackingActivity]
    I -- Nhấn nút Back --> D
    
    F -- Nhấn vào một thông báo đơn hàng --> I
    E -- Chọn tab Trang chủ --> D
    G -- Nhấn Logout --> A
```

### Giải thích quy trình hoạt động cho người ngoài:
1. **Khởi động và Xác thực (Auth)**: Người dùng mở ứng dụng và thấy màn hình đăng nhập (`LoginActivity`). Nếu chưa có tài khoản, họ có thể chuyển sang màn hình đăng ký (`RegisterActivity`) để tạo tài khoản mới bằng cách nhập tên, email, mật khẩu và số điện thoại. Sau khi đăng ký hoặc đăng nhập thành công (xác thực qua Firebase Auth), token thông báo FCM của thiết bị sẽ được lưu lại để phục vụ nhận tin nhắn đẩy và người dùng được dẫn tới màn hình chính (`MainActivity`).
2. **Trang chủ (Home/Main)**: Màn hình chính hiển thị danh sách các đơn hàng hiện tại đang hoạt động (đơn hàng ở trạng thái `pending` hoặc `delivering`). Nếu không có đơn hàng nào, một dòng chữ thông báo trống sẽ xuất hiện. Tại đây, người dùng có thể nhấp vào một đơn hàng bất kỳ để mở giao diện theo dõi (`TrackingActivity`) hoặc nhấn nút có biểu tượng "Play" lớn ở góc dưới để bắt đầu tạo một tác vụ giao hàng mới.
3. **Tạo đơn hàng (Create Task)**: Trên giao diện `CreateTaskActivity`, người dùng điền thông tin người gửi (mặc định điền từ thông tin cá nhân), thông tin người nhận bao gồm số điện thoại, địa điểm lấy hàng (pickup) và địa điểm giao hàng (dropoff). Khi nhập địa chỉ, hệ thống sẽ tự động đối chiếu với danh sách các địa điểm tĩnh tại Đà Nẵng để lấy tọa độ, sau đó gọi dịch vụ OSRM để tính toán quãng đường thực tế và vẽ tuyến đường màu xanh lam trên bản đồ MapLibre. Người dùng bắt buộc phải chọn một khay (slot) còn trống của robot (thông tin slot được cập nhật thời gian thực từ Firebase). Khi nhấn "Confirm", hệ thống sẽ thực hiện một Transaction khóa slot trên Firebase, lưu đơn hàng vào danh sách của người gửi, tự động tìm tài khoản người nhận dựa trên số điện thoại (nếu tìm thấy sẽ ghi đơn hàng vào danh sách đơn của người nhận và gửi thông báo cho người nhận), gửi lệnh khởi chạy robot `START_DELIVERY` qua giao thức MQTT, rồi chuyển hướng người dùng sang màn hình theo dõi đơn hàng.
4. **Theo dõi đơn hàng thời gian thực (Tracking)**: Tại màn hình `TrackingActivity`, ứng dụng kết nối trực tiếp đến HiveMQ Cloud MQTT Broker và đăng ký lắng nghe các topic của robot tương ứng. Khi robot di chuyển và gửi tọa độ (`telemetry`) hoặc trạng thái (`status`), ứng dụng của người dùng (sender/receiver) sẽ nhận gói tin này qua MQTT, cập nhật vị trí robot trên bản đồ MapLibre, đồng thời hoạt động như một cầu nối (bridge) cập nhật trực tiếp các thông số này lên Firebase Realtime Database. Nhờ đó, ngay cả người dùng không mở kết nối MQTT trực tiếp (ví dụ đang ở màn hình khác) vẫn có thể xem được dữ liệu cập nhật gián tiếp qua Firebase.
5. **Lịch sử, Thông báo và Hồ sơ cá nhân**: Thông qua thanh điều hướng dưới (Bottom Navigation), người dùng dễ dàng chuyển đổi qua lại giữa:
   - **History**: Xem lại tất cả các đơn hàng trong quá khứ kèm trạng thái tương ứng (Delivered, Cancelled,...).
   - **Notification**: Xem danh sách các thông báo hoạt động đơn hàng (đơn hàng mới, robot đã đến điểm lấy, robot đã giao xong,...). Nhấp vào thông báo sẽ mở trực tiếp màn hình theo dõi đơn hàng tương ứng.
   - **Profile**: Xem thông tin tài khoản hiện tại và thực hiện Đăng xuất.

---

## 9. Firebase Contract (Hợp đồng Firebase RTDB)

Cấu trúc các node dữ liệu được đọc trực tiếp từ logic code của ứng dụng:

| Firebase path | Read/Write | Class/Method | User role | Data stored | Rules required | Risk |
|---|---|---|---|---|---|---|
| `users/{uid}` | Read/Write | `RegisterActivity.java`, `ProfileActivity.java`, `LoginActivity.java` | Bản thân User | Lưu thông tin hồ sơ người dùng bao gồm: `username`, `email`, `phone`, `phoneNormalized`, `fcmToken`. | `auth.uid == uid` | Thấp. |
| `phoneIndex/{phoneNormalized}` | Read/Write | `RegisterActivity.java` (Write), `LoginActivity.java` & `CreateTaskActivity.java` (Read) | Mọi User (khi tạo tài khoản hoặc tìm người nhận) | Lưu ánh xạ từ số điện thoại chuẩn hóa sang `uid` và `email` để thực hiện tra cứu nhanh O(1). | Cho phép đọc toàn bộ, ghi chỉ khi đăng ký tài khoản mới. | **Trung bình**. Cần bảo vệ chống đọc trộm danh sách số điện thoại (không cho phép list toàn bộ node). |
| `tasks/{senderUid}/{taskId}` | Read/Write | `CreateTaskActivity.java` (Write), `MainActivity.java` & `HistoryActivity.java` (Read), `MqttFirebaseBridge.java` (Write) | Sender (chủ sở hữu đơn) và Receiver (đọc gián tiếp qua UI/cầu nối) | Chứa thông tin chi tiết đơn hàng: trạng thái, địa điểm, khoảng cách, slot robot, các thông số MQTT ACK và tọa độ robot hiện thời. | Cho phép Sender ghi/đọc, cho phép Receiver có số điện thoại khớp đọc. | **Cao**. Nếu cấu hình rules mặc định, người khác có thể đọc trộm thông tin đơn hàng nếu biết `senderUid`. Cần viết Rule ràng buộc quyền đọc cho Sender và Receiver có số điện thoại khớp. |
| `recipientTasks/{receiverUid}/{taskId}`| Read/Write | `CreateTaskActivity.java` (Write), `MqttFirebaseBridge.java` (Write) | Sender (Write), Receiver (Read) | Lưu bản sao hoặc tham chiếu của task dành cho người nhận để hiển thị trong lịch sử của họ. | Cho phép Receiver đọc, cho phép Sender (người tạo task) ghi khi tìm thấy số điện thoại. | **Cao**. Sender ghi trực tiếp sang node của Receiver khác sẽ bị Firebase chặn (Permission Denied) nếu thiết lập Rule chặt chẽ `auth.uid == receiverUid`. |
| `notifications/{uid}/{notificationId}`| Read/Write | `CreateTaskActivity.java` & `MqttFirebaseBridge.java` (Write), `NotificationActivity.java` (Read) | Sender/Receiver | Lưu danh sách thông báo dạng danh sách đẩy cho từng User. | Cho phép chủ sở hữu đọc, cho phép người tạo task ghi thông báo mới. | **Cao**. Rủi ro Permission Denied tương tự như `recipientTasks` khi Sender ghi trực tiếp vào node thông báo của Receiver. |
| `robotSlots/defaultRobot/{slotId}`| Read/Write (Transaction) | `CreateTaskActivity.java` (Reserve - transaction), `SlotUtils.java` (Release) | Sender (Reserve), Bất kỳ ai/Robot (Release) | Lưu trạng thái đặt khay của robot: `status` ("available" hoặc "occupied") và `orderId` đang liên kết. | Cho phép mọi user đã đăng nhập đọc/ghi giá trị thông qua luật kiểm soát trạng thái hợp lệ. | Trung bình. Cần đảm bảo Transaction thực hiện nguyên tử để tránh tranh chấp slot. |

---

## 10. MQTT/HiveMQ Contract (Hợp đồng MQTT)

Cấu trúc giao tiếp MQTT giữa ứng dụng Android và Robot:

| Topic | Direction | QoS | Payload/model | Producer | Consumer | Firebase mirror | Notes |
|---|---|---|---|---|---|---|---|
| `autodelivery/{env}/robots/{robotId}/command` | App -> Robot | 1 (At least once) | `RobotCommand` (JSON: `commandId`, `command` ("START_DELIVERY" hoặc "CANCEL_TASK"), `taskId`, `robotId`, `slotId`, tọa độ pickup/dropoff, địa chỉ, `timestamp`) | Android App (`CreateTaskActivity`) | Robot Hardware | Không ghi trực tiếp, nhưng app lưu mốc thời gian `mqttCommandPublishedAt` lên Firebase. | Lệnh kích hoạt robot di chuyển. |
| `autodelivery/{env}/robots/{robotId}/telemetry` | Robot -> App | 0 (At most once) | `RobotTelemetry` (JSON: `seq`, `taskId`, `robotId`, `lat`, `lng`, `battery`, `speed`, `heading`, `timestamp`) | Robot Hardware | Android App (`TrackingActivity`) | Có (`robotLat`, `robotLng`, `robotBattery`, `robotSpeed`, `robotHeading`, `lastTelemetrySeq`) ghi vào `tasks/{senderUid}/{taskId}` | Cập nhật vị trí robot định kỳ (tần suất ghi Firebase tối đa 5s/lần). |
| `autodelivery/{env}/robots/{robotId}/status` | Robot -> App | 1 (At least once) | `RobotStatusMessage` (JSON: `taskId`, `robotId`, `status` ("going_to_pickup", "picked_up", "going_to_destination", "delivered", "cancelled"), `timestamp`) | Robot Hardware | Android App (`TrackingActivity`) | Có (`status`, `robotStatus`) ghi vào cả `tasks` và `recipientTasks` | Cập nhật trạng thái nhiệm vụ. Kích hoạt giải phóng slot nếu trạng thái là terminal. |
| `autodelivery/{env}/robots/{robotId}/ack` | Robot -> App | 1 (At least once) | `RobotAck` (JSON: `commandId`, `taskId`, `robotId`, `accepted` (boolean), `message`, `timestamp`) | Robot Hardware | Android App (`TrackingActivity`) | Có (`mqttAck`, `mqttAckMessage`) ghi vào `tasks/{senderUid}/{taskId}` | Xác nhận robot đã nhận được lệnh và chấp nhận chạy đơn hàng hay không. |
| `autodelivery/{env}/robots/{robotId}/availability` | Robot -> App | 1 (At least once) | Chuỗi văn bản ("online" hoặc "offline") | Robot Hardware | Android App (`TrackingActivity`) | Không | Thể hiện trạng thái kết nối mạng của Robot. |

---

## 11. Map/Routing Contract

Quy trình xử lý bản đồ và tuyến đường trong ứng dụng:
1. **MapLibre SDK**: Chịu trách nhiệm khởi tạo bản đồ nền (`mapView.getMapAsync`), hiển thị các Layer và Source hình học, vẽ tuyến đường (LineLayer màu xanh lam `#2979FF`, độ rộng 5f) và các điểm mút (CircleLayer).
2. **MapTiler Cloud**: Cung cấp kiểu bản đồ nền (Style URL: `streets-v2`). Bản đồ không thể hiển thị nếu không cấu hình đúng API key MapTiler trong `local.properties`.
3. **OSRM (Open Source Routing Machine)**: Khi người dùng nhập địa chỉ lấy và giao hàng hợp lệ (trong phạm vi Đà Nẵng), ứng dụng gọi API công cộng OSRM để lấy thông số khoảng cách chính xác theo tuyến đường bộ và chuỗi tọa độ (Geometry GeoJSON) để vẽ tuyến đường chính xác uốn lượn theo các ngã rẽ trên bản đồ.
4. **Haversine Formula Fallback**: Nếu OSRM bị lỗi mạng, bị chặn hoặc trả về mã trạng thái không phải 200, hệ thống sẽ tự động tính toán khoảng cách theo đường chim bay (Haversine formula) và vẽ một tuyến đường thẳng nối trực tiếp hai điểm trên bản đồ nhằm đảm bảo trải nghiệm người dùng không bị gián đoạn.

---

## 12. Secrets/Config Audit (Kiểm tra bí mật cấu hình)

Các cấu hình nhạy cảm được quét và phát hiện trong dự án:

| Config item | Location | Secret exposed in source/docs/logs? | Safe handling recommendation |
|---|---|---|---|
| **MapTiler API Key** | `local.properties` | **Không**. Khóa `MAPTILER_API_KEY` nằm trong file cấu hình cục bộ không bị đẩy lên Git. | Cần được giữ kín. Không bao giờ đưa file `local.properties` vào mã nguồn chung. |
| **Firebase RTDB URL** | `local.properties` & `app/google-services.json` | **Có** (Dưới dạng URL công khai: `https://auto-delivery-e0327-default-rtdb.asia-southeast1.firebasedatabase.app`). | Đây là URL định danh database, không phải khóa bí mật nhưng nên đi kèm với rules bảo mật nghiêm ngặt. |
| **Firebase API Key** | `app/google-services.json` | **Có** (Dưới dạng API key client của Google: `AIzaSyC2hiRXtI8qzccctnNZIHLD8afGeaFK9JA`). | API key của Firebase client được thiết kế để nhúng vào app, tuy nhiên cần giới hạn quyền của key này trên Google Cloud Console (chỉ cho phép gọi API của Auth và RTDB của project). |
| **MQTT Credentials** | `app/build.gradle.kts` (Đọc từ `local.properties`) | **Không**. Các thuộc tính `MQTT_USERNAME`, `MQTT_PASSWORD` hiện thời không được khai báo trong `local.properties` trên máy cục bộ này (các giá trị đánh giá là trống). | Cần khai báo đầy đủ các thuộc tính MQTT trong `local.properties` trước khi chạy trên thiết bị để MqttManager có thông tin kết nối Broker. |

---

## 13. Docs Freshness Audit (Đánh giá độ tươi mới tài liệu)

| Doc file | Purpose | Freshness | Problem | Recommended action |
|---|---|---|---|---|
| `README.md` | Hướng dẫn chung về dự án. | **Stale / outdated** | Quá ngắn, thiếu các hướng dẫn cấu hình chi tiết, cấu trúc thư mục và cách build. | Cần cập nhật toàn diện theo sát mã nguồn thực tế (Xem tệp README.md mới). |
| `docs/PROJECT_STATUS_REPORT.md` | Báo cáo trạng thái dự án hiện tại. | **Matches code** | Thông tin cơ bản khớp, tuy nhiên cần đồng bộ kết quả build và phân tích chất lượng mới nhất. | Cập nhật cấu trúc báo cáo chi tiết. |
| `docs/FULL_PROJECT_AUDIT_AND_FLOW_REPORT.md`| Báo cáo luồng hoạt động ứng dụng. | **Matches code** | Báo cáo cũ còn sơ sài. | Cập nhật đầy đủ các bảng dữ liệu contract và ma trận như báo cáo hiện tại này. |
| `docs/FIREBASE_RULES_REQUIRED.md` | Định nghĩa rules bảo mật Firebase cần thiết. | **Partially matches code**| Chỉ ra các node dữ liệu nhưng chưa đề xuất bộ Rules JSON thực tế để giải quyết rủi ro Permission Denied. | Cần bổ sung mẫu thiết lập Firebase Security Rules cụ thể để người dùng chỉ việc copy-paste. |
| `docs/MQTT_INTEGRATION_REPORT.md` | Mô tả giao tiếp MQTT. | **Matches code** | Đúng về mặt lý thuyết, khớp với logic trong code. | Giữ nguyên làm tài liệu tham khảo. |
| `docs/MQTT_ROBOT_SIMULATOR_GUIDE.md` | Hướng dẫn giả lập robot gửi MQTT. | **Matches code** | Tài liệu hướng dẫn tốt cách giả lập tin nhắn. | Giữ nguyên. |
| `docs/CLEANUP_CANDIDATES_REPORT.md`| Đề xuất dọn dẹp file rác. | **Matches code** | Các file rác liệt kê thực tế đã được dọn sạch khỏi thư mục gốc. | Giữ nguyên để theo dõi lịch sử dọn dẹp. |

---

## 14. Cleanup/Temp File Audit
Kết quả quét các tệp tin tạm, tệp tin nháp sinh ra trong quá trình phát triển:
* Không phát hiện bất kỳ file rác nào dạng `search*.txt`, `non_ascii.txt` hay script Python dịch thuật `translate.py` trong thư mục gốc. Hệ thống sạch sẽ.
* **Khuyến nghị**: Không cần thực hiện thêm hành động dọn dẹp nào đối với mã nguồn.

---

## 15. Known Risks Before Manual Testing (Rủi ro kỹ thuật đã nhận diện)
1. **Rủi ro Ghi chéo Node trên Firebase RTDB (Cross-user Write Permission Denied)**:
   * *Chi tiết*: Lớp `CreateTaskActivity.java` và `MqttFirebaseBridge.java` trực tiếp thực hiện ghi dữ liệu đơn hàng và thông báo sang node của người nhận (`recipientTasks/{receiverUid}` và `notifications/{receiverUid}`).
   * *Rủi ro*: Nếu Firebase Realtime Database được thiết lập bảo mật chuẩn (chỉ cho phép ghi khi `auth.uid == $uid`), các thao tác ghi xuyên tài khoản này của Sender sẽ bị lỗi **Permission Denied**. Đơn hàng vẫn được tạo thành công cho Sender nhưng phía Receiver sẽ không thấy bất kỳ thông tin hay thông báo nào.
   * *Giải pháp*: Cần cấu hình Firebase Security Rules cho phép viết đối với các node này nếu người viết là Sender được liên kết, hoặc chuyển logic ghi này sang phía Server (ví dụ Cloud Functions) để đảm bảo an toàn.
2. **Rủi ro tắt ứng dụng ngắt kết nối MQTT (Client-side Bridge Dependency)**:
   * *Chi tiết*: Việc đồng bộ tọa độ robot và trạng thái robot từ MQTT lên Firebase RTDB do chính client Android đảm nhiệm thông qua `MqttFirebaseBridge.java`.
   * *Rủi ro*: Nếu cả Sender và Receiver đều tắt ứng dụng (hoặc app bị hệ điều hành Android kill khi chạy ngầm), kết nối MQTT sẽ bị ngắt. Lúc này, dù robot vẫn chạy và bắn telemetry lên Broker, Firebase RTDB vẫn giữ nguyên trạng thái cũ. Khi mở lại app, dữ liệu hành trình sẽ bị đứt quãng.
   * *Giải pháp*: Cần xây dựng một MQTT-to-Firebase Bridge độc lập chạy trên máy chủ (backend bridge) thay vì phụ thuộc hoàn toàn vào ứng dụng client.
3. **Rủi ro nghẽn OSRM API công cộng**:
   * *Chi tiết*: API `router.project-osrm.org` là dịch vụ công cộng miễn phí không đảm bảo băng thông và độ trễ.
   * *Rủi ro*: Khi gọi liên tục hoặc vào giờ cao điểm, API có thể bị timeout dẫn đến việc vẽ đường thẳng chim bay (Haversine Fallback) liên tục.
4. **Thiếu thông số cấu hình MQTT Broker cục bộ**:
   * *Chi tiết*: Tệp `local.properties` hiện chưa điền các trường `MQTT_HOST`, `MQTT_USERNAME`, `MQTT_PASSWORD`.
   * *Rủi ro*: App build ra sẽ không thể thiết lập kết nối MQTT tới HiveMQ Cloud và sẽ đưa ra thông báo cảnh báo trên màn hình Tracking.

---

## 16. Manual Test Readiness (Độ sẵn sàng kiểm thử thủ công)
* **Trạng thái**: **Sẵn sàng một phần (Partially Ready)**.
* **Yêu cầu bắt buộc trước khi test**:
  1. Người dùng cần bổ sung đầy đủ thông tin tài khoản kết nối HiveMQ Cloud (`MQTT_HOST`, `MQTT_USERNAME`, `MQTT_PASSWORD`) vào tệp [local.properties](file:///E:/final-year-project/AutoDeliveryApp-local/local.properties).
  2. Triển khai bộ quy tắc bảo mật Firebase (Security Rules) lên Firebase Console theo tài liệu hướng dẫn [FIREBASE_RULES_REQUIRED.md](file:///E:/final-year-project/AutoDeliveryApp-local/docs/FIREBASE_RULES_REQUIRED.md) để tránh lỗi ghi dữ liệu chéo.
* **Bằng chứng cần thu thập khi test**: Thực hiện theo đúng chỉ dẫn trong [MANUAL_TEST_PLAN.md](file:///E:/final-year-project/AutoDeliveryApp-local/docs/MANUAL_TEST_PLAN.md).

---

## 17. Next Steps (Các bước tiếp theo)
1. Cập nhật các thông số MQTT Broker vào tệp `local.properties`.
2. Truy cập Firebase Console và triển khai cấu hình Rules tương thích.
3. Cài đặt app lên thiết bị kiểm thử Android bằng lệnh `.\gradlew installDebug`.
4. Mở ADB logcat để bắt đầu ghi log kiểm thử.
5. Chạy qua 29 ca kiểm thử trong [MANUAL_TEST_PLAN.md](file:///E:/final-year-project/AutoDeliveryApp-local/docs/MANUAL_TEST_PLAN.md) và ghi nhận kết quả thực tế.
