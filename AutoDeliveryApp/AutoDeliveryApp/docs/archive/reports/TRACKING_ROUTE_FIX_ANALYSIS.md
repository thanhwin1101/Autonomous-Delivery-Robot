# Tracking/Route Fix Analysis (Post-BLE Integration)

## 1. Scope
This analysis documents the root causes of 5 runtime bugs discovered during manual testing after the BLE integration, along with the planned fixes. **BLE core (GATT, token, protocol) is NOT modified.**

---

## 2. Issue Matrix

| # | Symptom | Root Cause File(s) | Root Cause Detail |
|---|---------|-------------------|-------------------|
| 1 | TrackingActivity shows "Moving to B" (dropoff) immediately on task creation | `TrackingActivity.java` L177 | Hardcoded `tvDeliveryStatus.setText("Moving to " + dropoff)` without checking task phase. No distinction between "going_to_pickup" leg and "going_to_destination" leg. |
| 2 | ETA/distance lost when re-opening Tracking from Main/History/Notification | `TrackingActivity.java` L109-173, `MainActivity.java` L59-66 | TrackingActivity relies **solely on Intent extras** for distance/ETA. When opened from MainActivity, distance is hardcoded to `0.0`. No Firebase fallback read. |
| 3 | Timeline step icons render as solid colored squares | `activity_tracking.xml` L197-199, L228-230, L258-260, L288-290 | Step icons are plain `<TextView>` with `android:background="@color/primary_green"` — no icon, no drawable, no text content. Renders as 32×32 green/gray squares. |
| 4 | CreateTask labels say "Pickup Point" and "Enter delivery address" generically | `activity_create_task.xml` L165, L186 | Hints don't clarify A/B semantics for the user. |
| 5 | CreateTask map preview shows only CircleLayer dots, no real markers for A and B | `CreateTaskActivity.java` L350-355 | Uses `CircleLayer` with white fill + blue stroke. No SymbolLayer, no marker icons, no A/B label. |

---

## 3. Root Cause Deep Dive

### 3.1 Wrong Status Display on Task Creation (Issue #1)

**Code evidence** — `TrackingActivity.java` line 177:
```java
binding.tvDeliveryStatus.setText("Moving to " + dropoff);
```
This runs unconditionally in `onCreate`, regardless of task status. The correct flow is:
- **New task**: status = `pending` → robot has NOT started. Should show "Waiting for robot" or "Going to pickup A".
- Only after `sender_loaded` / `going_to_destination` should it show "Delivering to B".

The `setupFirebaseListener` does read `currentTaskStatus`, but the only UI update it triggers is `updateBleButtonVisibility()` — it never updates `tvDeliveryStatus` text based on status changes.

**Fix**: Add a `updateStatusUI(status)` method that maps each `TaskStatus` value to the correct display text and updates `tvDeliveryStatus`, timeline step highlights, and the "active leg" route on the map.

### 3.2 ETA/Distance Lost on Re-open (Issue #2)

**Code evidence** — `TrackingActivity.java` lines 109-173:
```java
double distance = getIntent().getDoubleExtra("distance", 0.0);
// ...
if (distance > 0) { ... } else { binding.tvDistanceRemaining.setText("---"); }
```

And in `MainActivity.java` line 65:
```java
intent.putExtra("distance", 0.0); // Always zero!
```

**Root cause chain**:
1. `CreateTaskActivity` saves `distance` to Firebase as `simulatedDistance`, but does NOT save the pickup/dropoff **lat/lng** or `deliveryDurationMinutes`.
2. `MainActivity` loads tasks from Firebase but doesn't read `distance` — it passes `0.0`.
3. `TrackingActivity` has no fallback logic to read from Firebase if Intent extras are missing.

**Fix**:
1. `CreateTaskActivity`: Save `pickupLat`, `pickupLng`, `dropoffLat`, `dropoffLng`, `deliveryDistanceKm`, `deliveryDurationMinutes` to Firebase.
2. `TrackingActivity`: If Intent distance is 0, read from Firebase. If Firebase has lat/lng but no distance, recalculate via OSRM.
3. `MainActivity`: Read `distance` from Firebase when building the Intent.

### 3.3 Timeline Icons Missing (Issue #3)

**Code evidence** — `activity_tracking.xml` lines 197-199:
```xml
<TextView android:layout_width="32dp" android:layout_height="32dp"
     android:textSize="16sp" android:gravity="center"
    android:background="@color/primary_green"/>
```

These are `TextView` elements with NO `android:text` set (and no `android:drawableStart`). They display as solid green/gray rectangles.

**Fix**: Replace `<TextView>` with `<ImageView>` using vector drawables:
- Step 1 (Order Received): `ic_step_received` (clipboard/inbox icon)
- Step 2 (Processing): `ic_step_preparing` (robot/gear icon)
- Step 3 (Delivering): `ic_step_delivering` (truck/route icon)
- Step 4 (Delivered): `ic_step_completed` (checkmark icon)

Create 4 new vector drawables in `res/drawable/`.

### 3.4 CreateTask Label Hints (Issue #4)

Trivial fix: Change hints in `activity_create_task.xml`:
- `"Pickup Point"` → `"Pickup Point (A)"`
- `"Enter delivery address"` → `"Delivery Point (B)"`

### 3.5 Map Preview Missing Real Markers (Issue #5)

Currently uses `CircleLayer` (9f radius white circles with blue stroke). These are subtle and don't convey A/B semantics.

**Fix**: Replace `CircleLayer` with `SymbolLayer` using marker icons and A/B labels. Since MapLibre SymbolLayer requires bitmap images added to the Style, we'll:
1. Create `ic_marker_a.xml` and `ic_marker_b.xml` vector drawables.
2. Add them as bitmap images to the MapLibre style.
3. Use SymbolLayer with `icon-image` and `text-field` properties.

**Alternative (simpler)**: Keep CircleLayer but increase radius, add distinct colors (green for A, red for B), and add `text-field` overlay via SymbolLayer for "A"/"B" labels.

---

## 4. Firebase Fields Gap Analysis

### Currently Saved (by CreateTaskActivity)
| Field | Saved? |
|-------|--------|
| `orderId` | ✅ |
| `pickup` (name) | ✅ |
| `dropoff` (name) | ✅ |
| `distance` (km) | ✅ |
| `status` | ✅ ("pending") |
| `senderUid` | ✅ |
| `robotId` | ✅ |
| `slotId` | ✅ |
| `bleToken` | ✅ |

### Missing (needed for robust TrackingActivity re-open)
| Field | Purpose |
|-------|---------|
| `pickupLat` | Lat of pickup point A |
| `pickupLng` | Lng of pickup point A |
| `dropoffLat` | Lat of delivery point B |
| `dropoffLng` | Lng of delivery point B |
| `deliveryDistanceKm` | Distance A→B for display |
| `deliveryDurationMinutes` | ETA A→B for display |
| `deliveryRouteGeoJson` | Route geometry for map draw without re-calling OSRM |
| `activeLeg` | "robot_to_pickup" / "pickup_to_delivery" / "completed" |

---

## 5. MQTT/Firebase Bridge Gap

### Current MqttFirebaseBridge.handleStatus()
- Sets `status` and `robotStatus` correctly.
- Does NOT set `activeLeg`.

### Current MqttFirebaseBridge.handleTelemetry()
- Updates `robotLat`/`robotLng` correctly.
- Does NOT update `activeLeg`.

### Fix
- In `handleStatus()`: Map incoming status to `activeLeg`:
  - `going_to_pickup` → `activeLeg = "robot_to_pickup"`
  - `arrived_pickup` / `waiting_sender_load` → `activeLeg = "at_pickup"`
  - `sender_loaded` / `going_to_destination` → `activeLeg = "pickup_to_delivery"`
  - `arrived_dropoff` / `waiting_receiver_unlock` → `activeLeg = "at_delivery"`
  - `delivered` / `cancelled` → `activeLeg = "completed"`

---

## 6. Route Model C→A→B

### Terminology
- **A** = Pickup Point (sender puts item)
- **B** = Delivery Point (receiver gets item)
- **C** = Robot's current real-time position (from MQTT telemetry)

### Route Display Logic by Status

| Task Status | Active Leg | Map shows | ETA source |
|------------|------------|-----------|------------|
| `pending` (no robot location) | waiting | Preview A→B route (dashed) | deliveryDurationMinutes from Firebase |
| `pending` / `going_to_pickup` (C available) | robot_to_pickup | Live route C→A + Preview A→B (dashed) | Calculate C→A via OSRM |
| `arrived_pickup` / `waiting_sender_load` | at_pickup | Robot marker at A, Preview A→B | N/A (waiting) |
| `sender_loaded` / `going_to_destination` (C available) | pickup_to_delivery | Live route C→B | Calculate C→B via OSRM |
| `sender_loaded` / `going_to_destination` (no C) | pickup_to_delivery | Fallback A→B route | deliveryDurationMinutes |
| `arrived_dropoff` / `waiting_receiver_unlock` | at_delivery | Robot marker at B | N/A (waiting) |
| `delivered` | completed | Full route A→B as review | N/A |

---

## 7. Files to Modify

| File | Changes |
|------|---------|
| `CreateTaskActivity.java` | Save pickupLat/Lng, dropoffLat/Lng, deliveryDistanceKm, deliveryDurationMinutes, activeLeg, deliveryRouteGeoJson to Firebase |
| `TrackingActivity.java` | Read Firebase for distance/ETA fallback; status-based UI rendering; route model C→A→B; update timeline |
| `MqttFirebaseBridge.java` | Set `activeLeg` on status changes |
| `MainActivity.java` | Read `distance` from Firebase when opening Tracking; pass `senderUid` |
| `activity_tracking.xml` | Replace timeline step `<TextView>` with `<ImageView>` + vector drawables |
| `activity_create_task.xml` | Update hints for pickup/dropoff fields |
| `res/drawable/` | Create `ic_step_received.xml`, `ic_step_preparing.xml`, `ic_step_delivering.xml`, `ic_step_completed.xml`, `ic_marker_a.xml`, `ic_marker_b.xml` |

---

## 8. Out of Scope
- BLE GATT protocol (`BleGattClient.java`)
- BLE token generation (`BleTokenUtils.java`)
- BLE permissions (`BlePermissionHelper.java`)
- Firebase Auth/Login
- FCM push backend
