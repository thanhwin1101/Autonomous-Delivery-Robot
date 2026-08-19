# KNOWN ISSUES — CURRENT
> Generated: 2026-07-02 | Source: source code analysis  
> Severity: CRITICAL / HIGH / MEDIUM / LOW

---

## 1. Real FCM Push Requires Backend / Cloud Functions

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Impact** | Push notifications do not reach users when app is in background/killed |
| **Evidence from source** | `FCMNotificationService.java` only handles incoming messages and saves token to SharedPreferences. No server-side push sender exists. `MqttFirebaseBridge.sendNotification()` writes to `/notifications/{uid}` in RTDB only (in-app bell icon), not a real push. |
| **Next action** | Implement Firebase Cloud Functions triggered by RTDB write to `/notifications/{uid}` to send real FCM push using Admin SDK |

---

## 2. Robot Firmware BLE Not Tested

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Impact** | BLE confirm_loaded and open_slot flows cannot be verified without matching firmware |
| **Evidence from source** | `BleConstants.java` defines `SERVICE_UUID=0000FFE0-...`, `CHARACTERISTIC_UUID=0000FFE1-...`. `BleProtocol.buildRequest()` and `BleProtocol.Response.parse()` define the JSON contract. Robot firmware must advertise this UUID + accept/validate the JSON — not confirmed implemented. |
| **Next action** | Implement robot BLE peripheral firmware matching UUID + JSON protocol. Test with real device. |

---

## 3. MQTT Bridge Is Client-Side Only

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Impact** | If app is closed or backgrounded while robot is delivering, telemetry/status is not written to Firebase. Task status does not update. Notifications not sent. Slot not released on delivery. |
| **Evidence from source** | `MqttFirebaseBridge` is instantiated inside `TrackingActivity.onCreate()`. It has no foreground service or background component. `MqttManager.getInstance().disconnect()` is called in `TrackingActivity.onDestroy()`. |
| **Next action** | Consider moving MQTT bridge to a persistent Foreground Service, or implement server-side bridge (Cloud Functions + MQTT webhook) |

---

## 4. Firebase Security Rules Status Unknown

| Field | Value |
|-------|-------|
| **Severity** | CRITICAL |
| **Impact** | If rules are too permissive, any authenticated user can read/write any task, token, or slot. `bleToken` could be exposed. |
| **Evidence from source** | `docs/archive/raw/FIREBASE_RULES_LOGIN_DEMO.json` exists but is archived sample only — not confirmed applied to Firebase project. No source code reads or validates rules. |
| **Next action** | Login to Firebase Console → Database → Rules. Review and apply proper user-scoped rules. `/tasks/{senderUid}` readable only by `auth.uid == senderUid`. `/recipientTasks/{receiverUid}` readable only by `auth.uid == receiverUid`. |

---

## 5. Firebase Demo Rules Risk if Used in Production

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Impact** | If development-mode rules (`.read: true, .write: true`) are applied, entire database is public |
| **Evidence from source** | N/A — no evidence rules have been applied. Risk is precautionary. |
| **Next action** | Never use open rules in production. Apply scoped rules before any real-user testing. |

---

## 6. OSRM Public API Dependency

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Impact** | Route calculation fails if OSRM public API is down or rate-limited. Haversine fallback gives straight-line distance (not road distance). |
| **Evidence from source** | `CreateTaskActivity.fetchOsrmRoute()` and `TrackingActivity.fetchRouteAndDraw()` call `https://router.project-osrm.org/route/v1/driving/...`. No API key. Public service with no SLA. |
| **Next action** | Consider self-hosting OSRM or using a paid routing API for production |

---

## 7. Address Resolver Is Local Lookup Only (NOT Geocoding)

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Impact** | Only 25 Da Nang addresses are recognizable. Any other address returns null (CreateTask blocks confirm) or Dragon Bridge fallback (TrackingActivity). |
| **Evidence from source** | `Constants.DA_NANG_LOCATIONS` hardcoded 25-entry map. `getCoordinates()` in both `CreateTaskActivity` and `TrackingActivity` uses substring matching against this local map. |
| **Next action** | Integrate a real geocoding API (Google Maps Geocoding, Nominatim, MapTiler Geocoding) for arbitrary address resolution |

---

## 8. BLE Token Stored Client-Side and Not Rotated

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Impact** | Token generated once in CreateTask and used for both sender confirm and receiver unlock. If leaked (via Firebase rules, network sniff), unauthorized BLE access possible. Token not rotated or invalidated after first use. |
| **Evidence from source** | `BleTokenUtils.generateToken()` called once in `CreateTaskActivity.confirmTask()`. Same token stored in Firebase and sent in MQTT command. Both sender BLE action and receiver BLE action use same `currentBleToken`. |
| **Next action** | Consider generating separate tokens for sender vs receiver, or invalidating token server-side after first use via Cloud Functions |

---

## 9. App Must Be Tested on Real Android Device

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Impact** | BLE, MapLibre, MQTT, Firebase real-time listener all require real device or thorough emulator config |
| **Evidence from source** | BLE scanning requires Bluetooth hardware. MapLibre GPU rendering may differ on emulator. MQTT SSL connection may fail on emulator network config. |
| **Next action** | Install APK on physical Android device (API 24+ = Android 7.0+). Test all flows. |

---

## 10. Route/ETA for Old Tasks Without New Fields

| Field | Value |
|-------|-------|
| **Severity** | LOW |
| **Impact** | Tasks created before `deliveryDistanceKm`, `deliveryRouteGeoJson`, `pickupLat/Lng`, `dropoffLat/Lng` were added will not restore route from Firebase. Will trigger `recalculateDistanceFromCoords()` which calls OSRM again. |
| **Evidence from source** | `TrackingActivity.setupFirebaseListener()`: `if (fbDistanceKm == null || fbDistanceKm == 0)` → `recalculateDistanceFromCoords()`. `if (!routeRestoredFromFirebase && deliveryRouteGeoJson == null && ...)` → `fetchRouteAndDraw()`. |
| **Next action** | Accept as known limitation. Old tasks may show incorrect or re-fetched routes. |

---

## 11. Bluetooth Permission / Device Compatibility Risk

| Field | Value |
|-------|-------|
| **Severity** | LOW |
| **Impact** | On some Android 12+ devices, BLUETOOTH_SCAN requires `neverForLocation=true` attribute or fine location permission. BLE scan may silently fail on some device/ROM combinations. |
| **Evidence from source** | `BlePermissionHelper.java` requests standard BLE permissions. Manifest uses `ACCESS_FINE_LOCATION android:maxSdkVersion="30"` (location only for ≤30). `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` declared without `neverForLocation`. |
| **Next action** | Test BLE permissions on target physical device. Add `android:usesPermissionFlags="neverForLocation"` to BLUETOOTH_SCAN if location not needed for scan. |

---

## Summary Table

| # | Issue | Severity | Impact |
|---|-------|----------|--------|
| 1 | Real FCM push not implemented | HIGH | Background push not delivered |
| 2 | Robot BLE firmware not tested | HIGH | BLE flows unverified |
| 3 | MQTT bridge client-side only | HIGH | State lost if app closed |
| 4 | Firebase Rules status unknown | CRITICAL | Potential data exposure |
| 5 | Demo Firebase Rules risk | HIGH | Open DB if applied |
| 6 | OSRM public API dependency | MEDIUM | Route fails if OSRM down |
| 7 | Address resolver local only | MEDIUM | Only 25 Da Nang addresses |
| 8 | BLE token not rotated | MEDIUM | Demo-grade security |
| 9 | Needs real device test | MEDIUM | Unverified on emulator |
| 10 | Old task fields missing | LOW | Route recalculated |
| 11 | BLE permissions edge cases | LOW | Some devices may fail |
