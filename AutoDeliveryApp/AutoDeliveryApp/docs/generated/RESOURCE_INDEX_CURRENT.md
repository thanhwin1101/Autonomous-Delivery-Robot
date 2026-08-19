# RESOURCE INDEX — CURRENT
> Generated: 2026-07-02 | Source: live res/ scan

---

## LAYOUT FILES (`res/layout/`)

| File | Used In | Purpose |
|------|---------|---------|
| `activity_login.xml` | `LoginActivity` | Email/phone tabs, password field, login button, forgot password link |
| `activity_register.xml` | `RegisterActivity` | Name, email, phone, password fields, register button |
| `activity_forgot_password.xml` | `ForgotPasswordActivity` | Email field, send reset button |
| `activity_main.xml` | `MainActivity` | Active task RecyclerView, create task FAB/button, bottom nav placeholder |
| `activity_history.xml` | `HistoryActivity` | History RecyclerView, empty state |
| `activity_notification.xml` | `NotificationActivity` | Notification RecyclerView, empty state |
| `activity_profile.xml` | `ProfileActivity` | User info (name/phone/email), logout button |
| `activity_create_task.xml` | `CreateTaskActivity` | MapView (MapLibre), bottom sheet, sender/receiver form, address fields, slot RadioGroup (slot1/slot2/slot3), distance text, confirm button, back button, progress bar |
| `activity_tracking.xml` | `TrackingActivity` | MapView, bottom sheet, timeline steps (received/preparing/delivering/completed), distance remaining, ETA, mission ID, order ID, MQTT status, BLE action button, back button |
| `item_active_task.xml` | `ActiveTaskAdapter` | Card for active task: orderId, status tag, pickup, dropoff, tracking button |
| `item_history.xml` | `HistoryAdapter` | Card for history item: orderId, date, status, pickup/dropoff |
| `item_notification.xml` | `NotificationAdapter` | Notification item: icon, title, message, time, read/unread state |

---

## DRAWABLE FILES (`res/drawable/`)

### Background Shapes
| File | Purpose | Used In |
|------|---------|---------|
| `bg_bottom_sheet_rounded.xml` | Rounded top corners background for bottom sheets | CreateTaskActivity, TrackingActivity bottom sheets |
| `bg_button_green.xml` | Green rounded button background | Confirm/action buttons |
| `bg_card_outlined.xml` | Outlined card background (border, rounded corners) | Task cards, form cards |
| `bg_tag_blue.xml` | Blue chip/tag background | Status tags (e.g. in-transit) |
| `bg_tag_green.xml` | Green chip/tag background | Status tags (e.g. delivered) |
| `bg_tag_orange.xml` | Orange chip/tag background | Status tags (e.g. pending) |
| `bg_tag_red.xml` | Red chip/tag background | Status tags (e.g. cancelled) |

### Navigation Icons (Bottom Nav)
| File | Purpose |
|------|---------|
| `ic_nav_home.xml` | Home tab icon |
| `ic_nav_history.xml` | History tab icon |
| `ic_nav_notification.xml` | Notification tab icon |
| `ic_nav_profile.xml` | Profile tab icon |

### Tracking Timeline Icons
| File | Purpose | Related Status |
|------|---------|----------------|
| `ic_step_received.xml` | Timeline step 1: Order received | Always active |
| `ic_step_preparing.xml` | Timeline step 2: Preparing/Going to pickup | `going_to_pickup`, `arrived_pickup`, `waiting_sender_load` |
| `ic_step_delivering.xml` | Timeline step 3: Delivering | `sender_loaded`, `going_to_destination`, `arrived_dropoff` |
| `ic_step_completed.xml` | Timeline step 4: Completed | `delivered` |

### Other Icons
| File | Purpose |
|------|---------|
| `ic_back.xml` | Back navigation arrow icon |

---

## MENU FILES (`res/menu/`)

| File | Purpose | Used In |
|------|---------|---------|
| `bottom_nav_menu.xml` | Bottom navigation menu items: home, history, notification, profile | All bottom nav activities |

---

## VALUES FILES (`res/values/`)

| File | Purpose | Key Contents |
|------|---------|--------------|
| `colors.xml` | App color palette | `primary_green`, `text_hint`, and other brand colors |
| `strings.xml` | App string resources | UI labels, format strings (`format_distance_km`, `format_eta_minutes`, `format_mission_id`, `format_order_id`), error messages, toast messages |
| `themes.xml` | Light theme | `Theme.AutoDeliveryApp` based on Material3 |
| `values-night/themes.xml` | Dark theme override | Dark mode colors |

---

## XML CONFIG FILES (`res/xml/`)

| File | Purpose |
|------|---------|
| `backup_rules.xml` | Android backup configuration |
| `data_extraction_rules.xml` | Android data extraction rules |

---

## MIPMAP (App Icons)
| File | Purpose |
|------|---------|
| `mipmap-hdpi/ic_launcher.webp` | App icon |
| `mipmap-hdpi/ic_launcher_round.webp` | Round app icon |

---

## SUMMARY

| Category | Count |
|----------|-------|
| Layout files | 12 |
| Drawable XML | 16 |
| Menu files | 1 |
| Values files | 4 (3 + 1 night) |
| XML config files | 2 |
| **Total XML** | **35** |
| Mipmap (webp) | 2 |
| **Total res files** | **37** |

---

## Notable Resource → Feature Mapping

| Feature | Layout | Drawables | Related Java |
|---------|--------|-----------|-------------|
| Tracking Timeline | `activity_tracking.xml` | `ic_step_received/preparing/delivering/completed.xml` | `TrackingActivity.updateStatusUI()` |
| Route Map (A→B, C→A) | `activity_tracking.xml`, `activity_create_task.xml` | — | `TrackingActivity.updateMapRoute()`, `CreateTaskActivity.drawRouteIfReady()` |
| BLE Action Button | `activity_tracking.xml` | `bg_button_green.xml` | `TrackingActivity.handleBleAction()` |
| Slot Selection | `activity_create_task.xml` | — | `CreateTaskActivity.loadRobotSlots()` |
| Status Tags | `item_active_task.xml`, `item_history.xml` | `bg_tag_*.xml` | `ActiveTaskAdapter`, `HistoryAdapter` |
| Bottom Navigation | All main activities | `ic_nav_*.xml` | `BottomNavActivity`, `bottom_nav_menu.xml` |
