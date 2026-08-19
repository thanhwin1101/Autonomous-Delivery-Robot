# PROJECT CONTEXT — CURRENT
> Generated: 2026-07-02 | Source: source code scan only  
> Old docs in docs/archive are NOT used as source of truth.

---

## 1. What Is This Project?

**Auto Delivery App** is an Android Native Java application for managing autonomous robot deliveries.

A sender creates a delivery task (specifying pickup and dropoff locations). A delivery robot receives the task via MQTT, navigates to the pickup point, waits for the sender to confirm loading via BLE, then delivers to the dropoff point where the receiver unlocks the slot via BLE.

The app tracks the robot's real-time position via MQTT telemetry, shows live route on a MapLibre map, and notifies both sender and receiver of status changes.

---

## 2. Current Objective

- Final-year project demonstrating autonomous robot delivery with Android app integration
- Demonstrate: Auth → Task Creation → MQTT command → Robot navigation → BLE handshake → Delivery complete
- Test on real Android device with physical (or simulated) robot

---

## 3. Primary Users

| User | Role |
|------|------|
| **Sender** | Creates delivery task, confirms goods loaded via BLE at pickup |
| **Receiver** | Receives delivery notification, unlocks slot via BLE at dropoff |
| (Both use same app, role determined at runtime by `isSender` flag)

---

## 4. How App, Robot, Firebase, MQTT, BLE Work Together

```
Sender App ──[Create Task]──► Firebase RTDB (task data + bleToken)
Sender App ──[MQTT command]──► Robot Firmware
Robot Firmware ──[MQTT telemetry/status]──► Sender App (TrackingActivity)
Sender App ──[MqttFirebaseBridge]──► Firebase RTDB (robotLat/Lng, status, activeLeg)
Firebase RTDB ──[listener]──► Both Sender + Receiver App (live status updates)
Sender App ──[BLE confirm_loaded]──► Robot (physical handshake at pickup)
Receiver App ──[BLE open_slot]──► Robot (physical handshake at dropoff)
Firebase RTDB ──[/notifications]──► Both Apps (in-app notification inbox)
```

**Key constraint**: MQTT bridge runs client-side in TrackingActivity. App must stay open for telemetry/status to reach Firebase.

---

## 5. Technology Stack

| Tech | Version / Config | Purpose |
|------|-----------------|---------|
| Android Native Java | compileSdk 36, minSdk 24 | App platform |
| Firebase Auth | BOM 32.7.0 | Email + Phone OTP login |
| Firebase RTDB | BOM 32.7.0 | All persistent state |
| Firebase FCM | BOM 32.7.0 | Push token (server push not yet implemented) |
| HiveMQ MQTT Client | 1.3.15 | MQTT broker connection |
| MapLibre Android SDK | 11.0.0 | Map rendering (OSM tiles from MapTiler) |
| OSRM | Public API | Road routing A→B (Haversine fallback) |
| BLE GATT | Standard Android API | Robot physical handshake |
| ViewBinding | Enabled | Layout binding |
| BuildConfig | Enabled | Secrets from local.properties |

---

## 6. Implemented Modules

| Module | Status | Notes |
|--------|--------|-------|
| Email login | ✅ | Firebase Auth |
| Phone OTP login | ✅ | Firebase Auth PhoneAuthProvider |
| Forgot password | ✅ | Firebase Auth sendPasswordResetEmail |
| Register | ✅ | Auth + RTDB users + phoneIndex |
| Create task | ✅ | Local address lookup + OSRM + slot booking + Firebase write |
| A→B route preview | ✅ | MapLibre + OSRM (Haversine fallback) |
| Tracking C→A→B | ✅ | Firebase listener + MQTT + MapLibre |
| MQTT command | ✅ | START_DELIVERY published on task create |
| MQTT telemetry bridge | ✅ | robotLat/Lng written to Firebase |
| MQTT status bridge | ✅ | Task status + activeLeg updated |
| MQTT ack bridge | ✅ | mqttAck field updated |
| BLE sender confirm_loaded | ✅ | BLE scan + GATT write + Firebase update |
| BLE receiver open_slot | ✅ | BLE scan + GATT write + Firebase update |
| Slot booking (Firebase Tx) | ✅ | Atomic Transaction prevents double-booking |
| Slot release on terminal | ✅ | SlotUtils.releaseSlotIfTerminal() |
| In-app notifications | ✅ | /notifications/{uid} written by MqttFirebaseBridge |
| Notification inbox | ✅ | NotificationActivity reads Firebase |
| History | ✅ | HistoryActivity reads Firebase tasks |
| Profile / logout | ✅ | Show user info, FirebaseAuth.signOut() |
| FCM client token | ✅ | FCMNotificationService stores token |
| Bottom navigation | ✅ | 4-tab bottom nav with singleTask activities |
| Edge-to-edge UI | ✅ | EdgeToEdgeHelper |

---

## 7. Modules Not Fully Verified at Runtime

| Module | Risk / Unknown |
|--------|---------------|
| **Real FCM push** | Server-side Cloud Functions not implemented — push delivery unverified |
| **Robot BLE firmware** | Robot must implement the exact BLE UUID + JSON protocol — not tested with real robot |
| **MQTT bridge when app closed** | Bridge only runs while TrackingActivity is open |
| **Firebase Security Rules** | Server rules state unknown — may be too permissive (demo rules) |
| **Address resolver edge cases** | Only 25 Da Nang locations; any other address → null/fallback |
| **Task reopen for old tasks** | Old tasks without `deliveryDistanceKm` field trigger OSRM recalculate |

---

## 8. What NOT to Trust in docs/archive

The following archived files may contain stale or incorrect information:
- `docs/archive/raw/README.md` — Old README, may not reflect current code
- `docs/archive/reports/PROJECT_STATUS_REPORT.md` — Snapshot from unknown date
- `docs/archive/audits/FULL_PROJECT_AUDIT_AND_FLOW_REPORT.md` — Audit from before BLE integration
- `docs/archive/reports/FEATURE_COMPLETION_MATRIX.csv` — Status claims unverified
- Any `audit_*.txt` in `docs/archive/audits/` — Raw grep output, not human-verified

**Rule**: When in doubt, read the source code. Source always wins.
