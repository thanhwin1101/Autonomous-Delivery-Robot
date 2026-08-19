# MANUAL TEST PLAN — CURRENT
> Generated: 2026-07-02 | Source: source code analysis  
> Environment required: Real Android device (API 24+), Firebase project configured, MQTT broker accessible

---

## Pre-Test Checklist

| Check | How |
|-------|-----|
| `local.properties` has all secrets | Verify file exists; `DB_URL`, `MAPTILER_API_KEY`, `MQTT_HOST`, `MQTT_PORT`, `MQTT_USERNAME`, `MQTT_PASSWORD`, `MQTT_USE_SSL`, `MQTT_ENV`, `MQTT_DEFAULT_ROBOT_ID` are set |
| `google-services.json` is generated | Build the app — `google-services.json` should be generated from template + BuildConfig |
| App installs on device | Run `./gradlew installDebug` or from Android Studio |
| Device has Bluetooth enabled | Required for BLE tests |
| Device has internet access | Required for Firebase + MQTT + OSRM |
| MQTT broker accessible | Try connecting with MQTT Explorer to same broker |

---

## TC-AUTH-01: Register New User

| Field | Value |
|-------|-------|
| **Test Case** | TC-AUTH-01 |
| **Feature** | Registration |
| **Steps** | 1. Open app → LoginActivity → tap Register<br>2. Enter: display name, email (test+N@example.com), password, phone (+84xxx)<br>3. Tap Register button |
| **Expected** | Account created; navigates to LoginActivity; `/users/{uid}` written; `/phoneIndex/{+84xxx}` = uid |
| **Pass condition** | No error dialog; Firebase Console shows user in Auth + RTDB |
| **Known risk** | Duplicate phone: no uniqueness check |

---

## TC-AUTH-02: Login (Email)

| Field | Value |
|-------|-------|
| **Test Case** | TC-AUTH-02 |
| **Feature** | Email login |
| **Steps** | 1. Open app → LoginActivity<br>2. Enter registered email + password<br>3. Tap Login |
| **Expected** | Navigates to MainActivity |
| **Pass condition** | MainActivity visible; user UID resolvable |

---

## TC-AUTH-03: Forgot Password

| Field | Value |
|-------|-------|
| **Test Case** | TC-AUTH-03 |
| **Feature** | Forgot Password |
| **Steps** | 1. LoginActivity → tap Forgot Password<br>2. Enter registered email<br>3. Tap Send Reset Email |
| **Expected** | Toast: email sent; reset email arrives in inbox |
| **Pass condition** | Email received with reset link |

---

## TC-CREATE-01: Create Task — Happy Path

| Field | Value |
|-------|-------|
| **Test Case** | TC-CREATE-01 |
| **Feature** | Create Task |
| **Precondition** | Logged in as Sender; receiver registered with known phone |
| **Steps** | 1. MainActivity → tap Create Task button<br>2. Map opens<br>3. Enter pickup address (known Da Nang location, e.g. "Da Nang Airport")<br>4. Enter dropoff address (e.g. "Dragon Bridge")<br>5. Enter receiver name + phone<br>6. Observe: map shows A→B route + distance + ETA<br>7. Select slot 1<br>8. Tap Confirm |
| **Expected** | Route drawn; task created; TrackingActivity opens; slot1 shows "occupied" in Firebase |
| **Pass condition** | `/tasks/{senderUid}/{orderId}` written; `/recipientTasks/{receiverUid}/{orderId}` written; `/robotSlots/defaultRobot/slot1/status = occupied`; MQTT command published |
| **Known risk** | Address not in 25-entry map → confirm button blocked |

---

## TC-CREATE-02: Create Task — OSRM Fallback

| Field | Value |
|-------|-------|
| **Test Case** | TC-CREATE-02 |
| **Feature** | Create Task — route fallback |
| **Precondition** | Network accessible but OSRM API unreachable (can simulate by using wrong address pair outside Da Nang scope or mock) |
| **Steps** | 1. Same as TC-CREATE-01<br>2. Observe distance field and route on map |
| **Expected** | Distance shows Haversine value; straight line drawn on map; `deliveryRouteSource = "haversine"` in Firebase |
| **Pass condition** | Confirm still works; task created with Haversine data |

---

## TC-CREATE-03: Slot Booking Race

| Field | Value |
|-------|-------|
| **Test Case** | TC-CREATE-03 |
| **Feature** | Slot booking Firebase Transaction |
| **Precondition** | Two devices with same account; slot1 is the only available slot |
| **Steps** | 1. Both devices open CreateTaskActivity<br>2. Both select slot1<br>3. Both tap Confirm simultaneously |
| **Expected** | One succeeds; the other shows "slot taken" toast |
| **Pass condition** | Firebase RTDB slot1 is occupied by exactly one task |

---

## TC-TRACK-01: Tracking — Firebase Status Update

| Field | Value |
|-------|-------|
| **Test Case** | TC-TRACK-01 |
| **Feature** | Tracking — Firebase listener |
| **Precondition** | Task created (TC-CREATE-01 passed); TrackingActivity open |
| **Steps** | 1. Open TrackingActivity<br>2. Manually change `/tasks/{senderUid}/{orderId}/status` in Firebase Console to `going_to_pickup` |
| **Expected** | TrackingActivity status text updates; Step 2 (Preparing) highlights |
| **Pass condition** | UI updates in < 2 seconds without app restart |

---

## TC-TRACK-02: Tracking — MQTT Telemetry

| Field | Value |
|-------|-------|
| **Test Case** | TC-TRACK-02 |
| **Feature** | Tracking — MQTT telemetry + map update |
| **Precondition** | Task created; TrackingActivity open; MQTT broker accessible; MQTT client (MQTT Explorer or Mosquitto) ready |
| **Steps** | 1. Publish to `autodelivery/{env}/robots/defaultRobot/telemetry`:<br>```json<br>{"robotId":"defaultRobot","taskId":"{orderId}","lat":16.0611,"lng":108.2275,"battery":80,"speed":1.0,"heading":90,"obstacle":false,"timestamp":1719888000000,"seq":1}<br>```<br>2. Observe TrackingActivity map |
| **Expected** | Robot position C visible on map; route drawn C→A; MQTT status bar shows timestamp |
| **Pass condition** | Map updates; `robotLat/Lng` written to Firebase |

---

## TC-TRACK-03: Tracking — MQTT Status Change

| Field | Value |
|-------|-------|
| **Test Case** | TC-TRACK-03 |
| **Feature** | Tracking — MQTT status → UI + notifications |
| **Precondition** | TC-TRACK-02 passed |
| **Steps** | 1. Publish to `autodelivery/{env}/robots/defaultRobot/status`:<br>```json<br>{"robotId":"defaultRobot","taskId":"{orderId}","status":"arrived_pickup","slotId":"slot1","message":"At pickup","timestamp":1719888000000}<br>```<br>2. Observe TrackingActivity + Firebase Console + NotificationActivity |
| **Expected** | Status text changes to "Arrived at Pickup"; BLE button appears (if isSender=true); `/notifications/{senderUid}` has new notification; `/notifications/{receiverUid}` has new notification |
| **Pass condition** | All three update correctly |

---

## TC-BLE-01: Sender Confirm Loaded

| Field | Value |
|-------|-------|
| **Test Case** | TC-BLE-01 |
| **Feature** | BLE — sender confirm_loaded |
| **Precondition** | Robot BLE device nearby advertising `SERVICE_UUID=0000FFE0-...`, device named "defaultRobot"; status = ARRIVED_PICKUP or WAITING_SENDER_LOAD; isSender = true |
| **Steps** | 1. TrackingActivity shows BLE button "Đã đặt hàng vào kho chứa"<br>2. Tap button<br>3. Grant BLE permissions if prompted<br>4. Observe: Toast "Đang kết nối Bluetooth với robot..."<br>5. Wait for BLE response |
| **Expected** | Toast "Gửi lệnh thành công!"; `/tasks/{uid}/{orderId}/bleState` = "sender_loaded" |
| **Pass condition** | BLE write successful; Firebase updated |
| **Known risk** | Robot firmware not yet verified |

---

## TC-BLE-02: Receiver Open Slot

| Field | Value |
|-------|-------|
| **Test Case** | TC-BLE-02 |
| **Feature** | BLE — receiver open_slot |
| **Precondition** | Same robot BLE device; status = ARRIVED_DROPOFF or WAITING_RECEIVER_UNLOCK; isSender = false (logged in as receiver) |
| **Steps** | 1. TrackingActivity shows BLE button "Mở khoang hàng"<br>2. Tap button<br>3. Wait for BLE response |
| **Expected** | Toast "Gửi lệnh thành công!"; `/tasks/{uid}/{orderId}/bleState` = "receiver_unlocked" |
| **Pass condition** | BLE write successful; Firebase updated |

---

## TC-SLOT-01: Slot Release on Delivery

| Field | Value |
|-------|-------|
| **Test Case** | TC-SLOT-01 |
| **Feature** | Slot lifecycle |
| **Precondition** | Task in progress with slot1 occupied |
| **Steps** | 1. Publish MQTT status `delivered`<br>2. Observe Firebase Console `/robotSlots/defaultRobot/slot1` |
| **Expected** | `status = "available"`, `taskId = null`, `orderId = null` |
| **Pass condition** | Slot released within 5 seconds |

---

## TC-NOTIF-01: In-App Notification

| Field | Value |
|-------|-------|
| **Test Case** | TC-NOTIF-01 |
| **Feature** | Notification inbox |
| **Precondition** | Task created (TC-CREATE-01) |
| **Steps** | 1. Open NotificationActivity (tap bell tab)<br>2. Observe list |
| **Expected** | "Task created" notification visible for sender; "Incoming delivery" for receiver |
| **Pass condition** | Notification appears with correct title, message, icon, time |

---

## TC-HIST-01: History List

| Field | Value |
|-------|-------|
| **Test Case** | TC-HIST-01 |
| **Feature** | History |
| **Precondition** | At least one task in Firebase `/tasks/{uid}` |
| **Steps** | 1. Open HistoryActivity (tap History tab)<br>2. Observe list |
| **Expected** | Task(s) appear in list with orderId, status, pickup, dropoff |
| **Pass condition** | At least one item visible |

---

## TC-PROFILE-01: Profile and Logout

| Field | Value |
|-------|-------|
| **Test Case** | TC-PROFILE-01 |
| **Feature** | Profile + Logout |
| **Steps** | 1. Open ProfileActivity (Profile tab)<br>2. Verify name/email/phone shown<br>3. Tap Logout |
| **Expected** | User info displayed correctly; logout navigates to LoginActivity |
| **Pass condition** | After logout, cannot access MainActivity (redirected to Login) |

---

## TC-MAP-01: Address Boundary Test

| Field | Value |
|-------|-------|
| **Test Case** | TC-MAP-01 |
| **Feature** | Address resolver |
| **Steps** | 1. CreateTaskActivity<br>2. Type an unknown address (e.g. "123 Unknown Street")<br>3. Type a known address (e.g. "Da Nang Airport") |
| **Expected** | Unknown: Confirm button blocked (null coordinates); Known: route drawn |
| **Pass condition** | Only known addresses enable Confirm |

---

## Regression Checklist After Any Code Change

- [ ] App builds without error (`./gradlew assembleDebug`)
- [ ] Lint passes or is reviewed (`./gradlew lintDebug`)
- [ ] Login (email) works
- [ ] Create Task completes with MQTT command published
- [ ] TrackingActivity opens and shows status
- [ ] MQTT telemetry updates map
- [ ] MQTT status updates UI and Firebase
- [ ] Slot released after terminal status
- [ ] Notifications appear in NotificationActivity
