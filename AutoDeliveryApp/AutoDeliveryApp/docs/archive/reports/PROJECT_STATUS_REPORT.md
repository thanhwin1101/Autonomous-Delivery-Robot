# Project Status Report

## Current Summary
This project is an Android Native Java application for automated delivery management and robot tracking. A comprehensive static audit and compilation pass has been performed. The app has been converted to English-only, and the Vietnamese resources and locale-switching logic have been cleaned up. 

## Build Status
- **Compilation**: **SUCCESSFUL** (verified via local Gradle build task `assembleDebug` on 2026-07-01).
- **Gradle Tasks list**: **SUCCESSFUL** (Gradle version 9.4.1, Android Gradle Plugin 9.2.1, JDK 11 compatibility).
- **Static Linting**: **SUCCESSFUL** (completed via `lintDebug` without critical compiler errors, HTML report exported to `app/build/reports/lint-results-debug.html`).

## Runtime Status
- **Status**: **Not Confirmed** (Build-verified only; runtime confirmation on emulator or real hardware still required).
- No actual emulator, physical device, or logcat run evidence was found or generated during this pass. 

## Technology Stack
- **Platform**: Android Native Java (compileSdk 36, targetSdk 36, minSdk 24).
- **Backend / Data Layer**: Firebase (Auth for credentials, Realtime Database for active state).
- **Robot Realtime Communication**: MQTT through HiveMQ Cloud (TCP TLS Port 8883, utilizing `hivemq-mqtt-client` library).
- **Map Display**: MapLibre Android SDK (v11.0.0).
- **Map Style / Tiles**: MapTiler Cloud style URL (Streets v2, utilizing client API key from `local.properties`).
- **Routing Services**: OSRM public API (with local Da Nang coordinate resolution lookup).
- **Distance Calculation Fallback**: Haversine formula (computes straight-line distance if OSRM endpoint fails).

## Feature Status
- **Register / Login / Password Reset**: Implemented in code (Firebase Auth).
- **Profile / Logout**: Implemented in code.
- **Task Creation & Slot Reservation**: Implemented in code (atomic Firebase transaction locks slots `slot1`, `slot2`, `slot3` under `robotSlots`).
- **Receiver User Lookup**: Implemented in code (3-tier lookup: `phoneIndex` -> normalized phone query -> raw phone query).
- **Cross-User Writes (recipientTasks & notifications)**: Implemented in code (runs client-side, see Risks).
- **In-App Notifications**: Implemented in code (reads from `/notifications/{uid}`).
- **FCM Push Notification**: Client-side messaging service (`FCMNotificationService`) is implemented; server-side/admin push backend is **Not Implemented**.
- **MQTT Telemetry/Status/Ack Mirroring**: Implemented in code (handled via client-side `MqttFirebaseBridge`).
- **Robot Hardware runtime integration**: **Requires hardware or simulator** (static implementation verified).

## Known Risks
1. **Receiver Node Write Permissions (Permission Denied Risk)**: 
   The app attempts to write directly from the sender's client device to the receiver's node at `recipientTasks/{receiverUid}` and `notifications/{receiverUid}`. Under standard Firebase security configurations (`auth.uid == $uid`), these writes will fail with a `Permission Denied` error unless security rules are set up with custom write exceptions.
2. **Client-side MQTT-to-Firebase Bridge Dependency**: 
   The `MqttFirebaseBridge` runs within the client app (`TrackingActivity`). If the app is closed or killed by the Android OS, no robot telemetry or status updates from MQTT will be written to Firebase. A standalone server-side bridge is highly recommended.
3. **Missing MQTT Configuration**: 
   The local environment configuration (`local.properties`) does not define the MQTT credentials (`MQTT_HOST`, `MQTT_USERNAME`, `MQTT_PASSWORD`), which will cause MQTT connection failures during initial runtime testing.

## Manual Test Readiness
- **Readiness**: **Partially Ready**.
- **Blockers / Prerequisites**: 
  - Fill in HiveMQ credentials in `local.properties`.
  - Deploy compatible Firebase Database Security Rules (see `FIREBASE_RULES_REQUIRED.md`).
  - Prepare an MQTT simulator for testing telemetry and status callbacks (see `MQTT_ROBOT_SIMULATOR_GUIDE.md`).

## Next Step
1. Configure MQTT broker variables in `local.properties`.
2. Configure Firebase Realtime Database rules on the Firebase Console.
3. Compile and install the debug package on the target device:
   ```powershell
   .\gradlew installDebug
   ```
4. Run the 29 test cases described in [MANUAL_TEST_PLAN.md](file:///E:/final-year-project/AutoDeliveryApp-local/docs/MANUAL_TEST_PLAN.md) and capture evidence (screenshots, logcats, and database states).
