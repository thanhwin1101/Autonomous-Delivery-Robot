# PROJECT STATUS — CURRENT
> Generated: 2026-07-02 | Source: source code scan only  
> Last actual build result: unknown (see docs/cleanup/ for cleanup context)

---

## Build Configuration

| Property | Value |
|----------|-------|
| compileSdk | 36 |
| minSdk | 24 |
| targetSdk | 36 |
| versionCode | 1 |
| versionName | 1.0 |
| Java source compatibility | 11 |
| Java target compatibility | 11 |
| ViewBinding | Enabled |
| BuildConfig | Enabled |
| ABI filters | armeabi-v7a, arm64-v8a, x86, x86_64 |

---

## Source File Count (Verified by Live Scan)

| Category | Count |
|----------|-------|
| Java source files | 41 |
| XML layout files | 12 |
| Drawable XML files | 16 |
| Values files | 4 (3 + 1 night) |
| Menu files | 1 |
| XML config files | 2 |
| Total res XML | 35 |
| App icon (webp) | 2 |
| **Total res files** | **37** |

---

## Dependency Status

| Library | Version | Status |
|---------|---------|--------|
| Firebase BOM | 32.7.0 | Confirmed in build.gradle.kts |
| Firebase Auth (KTX) | via BOM | Confirmed |
| Firebase RTDB (KTX) | via BOM | Confirmed |
| Firebase Messaging (KTX) | via BOM | Confirmed |
| Google Services plugin | 4.4.2 | Confirmed |
| MapLibre Android SDK | 11.0.0 | Confirmed |
| HiveMQ MQTT Client | 1.3.15 | Confirmed |
| Material Components | via libs.versions.toml | Confirmed |
| AppCompat | via libs.versions.toml | Confirmed |
| ConstraintLayout | via libs.versions.toml | Confirmed |
| CircleImageView | — | Confirmed in build.gradle.kts |

---

## Feature Implementation Status

| Feature | Status | Notes |
|---------|--------|-------|
| Email Login | ✅ Complete | Firebase Auth |
| Phone OTP Login | ✅ Complete | Firebase PhoneAuthProvider |
| Forgot Password | ✅ Complete | Firebase Auth reset email |
| Register | ✅ Complete | Auth + RTDB users + phoneIndex |
| Create Task | ✅ Complete | Local address + OSRM + slot + Firebase + MQTT |
| Route Preview A→B | ✅ Complete | OSRM + Haversine fallback |
| Live Tracking C→A→B | ✅ Complete | Firebase + MQTT + MapLibre |
| MQTT START_DELIVERY | ✅ Complete | Published on task confirm |
| MQTT Telemetry Mirror | ✅ Complete | Firebase bridge (5s throttle) |
| MQTT Status Mirror | ✅ Complete | Firebase bridge + activeLeg |
| MQTT Ack Mirror | ✅ Complete | Firebase bridge |
| BLE Sender (confirm_loaded) | ✅ Complete (code) | Robot firmware unverified |
| BLE Receiver (open_slot) | ✅ Complete (code) | Robot firmware unverified |
| Slot Booking (Transaction) | ✅ Complete | Firebase atomic transaction |
| Slot Release on Terminal | ✅ Complete | SlotUtils.releaseSlotIfTerminal |
| In-App Notifications | ✅ Complete | MqttFirebaseBridge.sendNotification |
| Notification Inbox | ✅ Complete | NotificationActivity reads Firebase |
| Task History | ✅ Complete | HistoryActivity reads Firebase |
| Profile + Logout | ✅ Complete | Show user info + signOut |
| Bottom Navigation | ✅ Complete | 4-tab singleTask |
| Edge-to-edge UI | ✅ Complete | EdgeToEdgeHelper |
| FCM Token Save | ✅ Complete | FCMNotificationService |
| Real FCM Push | ⚠️ Pending | Requires Cloud Functions (not implemented) |
| Real Robot BLE Test | ⚠️ Pending | Robot firmware not yet verified |
| Server-side MQTT Bridge | ⚠️ Pending | Bridge is client-side only |
| Firebase Security Rules | ⚠️ Unknown | Server-side rules not verified |
| Real Geocoding API | ⚠️ Limited | 25 hardcoded Da Nang addresses only |

---

## Runtime Risks

| Risk | Severity |
|------|----------|
| Firebase Rules may be too permissive | CRITICAL |
| FCM push requires Cloud Functions | HIGH |
| BLE firmware not verified | HIGH |
| MQTT bridge app-dependent | HIGH |
| OSRM public API reliability | MEDIUM |
| Address resolver limited scope | MEDIUM |
| BLE token not rotated | MEDIUM |

---

## Next Steps Recommended

1. **Verify Firebase Security Rules** in Firebase Console → Database → Rules (do not skip)
2. **Test full flow on real Android device** (minSdk 24, Bluetooth capable)
3. **Implement robot BLE firmware** matching UUID + JSON protocol
4. **Simulate MQTT messages** with MQTT Explorer for end-to-end testing
5. **Implement Cloud Functions** for real FCM push delivery (optional for demo, required for production)
6. **Optionally**: Move MQTT bridge to a Foreground Service for background operation

---

## Docs State After Context Rebuild (2026-07-02)

| Location | Content |
|----------|---------|
| `docs/archive/` | All old logs/reports/audits (DO NOT USE as source of truth) |
| `docs/cleanup/` | Cleanup process records |
| `docs/generated/` | Live-generated inventory (tree, CSV, class index, resource index) |
| `docs/current/` | Fresh context documents (this file + architecture/flows/schema/contracts/issues/tests) |
| Root `README.md` | Minimal, points to docs/current/ |
