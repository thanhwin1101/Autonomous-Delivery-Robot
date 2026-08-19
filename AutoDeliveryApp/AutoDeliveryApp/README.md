# AutoDeliveryApp

Android Native Java app for autonomous robot delivery management.

> **Note**: This README is minimal by design. Full documentation is in [`docs/current/`](docs/current/).

---

## Quick Start

1. Copy `local.properties.example` (or create `local.properties`) with:
   ```
   sdk.dir=...
   DB_URL=https://your-project.firebaseio.com
   MAPTILER_API_KEY=your_key
   MQTT_HOST=your-broker.hivemq.cloud
   MQTT_PORT=8883
   MQTT_USERNAME=your_user
   MQTT_PASSWORD=your_pass
   MQTT_USE_SSL=true
   MQTT_ENV=dev
   MQTT_DEFAULT_ROBOT_ID=defaultRobot
   Firebase_API_Key=your_firebase_api_key
   ```

2. Build:
   ```
   ./gradlew assembleDebug
   ```

3. Install on device:
   ```
   ./gradlew installDebug
   ```

---

## Documentation

| Document | Description |
|----------|-------------|
| [Project Context](docs/current/PROJECT_CONTEXT_CURRENT.md) | What this project is and how the systems integrate |
| [App Architecture](docs/current/APP_ARCHITECTURE_CURRENT.md) | Package structure, activity navigation, data flow, dependencies |
| [Features & Flows](docs/current/FEATURES_AND_FLOWS_CURRENT.md) | All features with step-by-step flows and Mermaid diagrams |
| [Firebase Schema](docs/current/FIREBASE_SCHEMA_CURRENT.md) | All RTDB nodes with fields, writers, readers, and risks |
| [MQTT Contract](docs/current/MQTT_CONTRACT_CURRENT.md) | Broker config, topic structure, all payload formats, state machine |
| [BLE Contract](docs/current/BLE_CONTRACT_CURRENT.md) | UUIDs, token, sender/receiver flows, robot firmware requirements |
| [Tracking Route Context](docs/current/TRACKING_ROUTE_CONTEXT_CURRENT.md) | C/A/B definitions, route lifecycle, activeLeg mapping |
| [Manual Test Plan](docs/current/MANUAL_TEST_PLAN_CURRENT.md) | 15 test cases for all features |
| [Known Issues](docs/current/KNOWN_ISSUES_CURRENT.md) | 11 risks with severity and next actions |
| [Project Status](docs/current/PROJECT_STATUS_CURRENT.md) | Build config, feature status, next steps |

### Inventory
| Document | Description |
|----------|-------------|
| [Project Tree](docs/generated/PROJECT_TREE_CURRENT.txt) | Current file tree (excluding build/cache) |
| [Project Files CSV](docs/generated/PROJECT_FILES_CURRENT.csv) | All files with type and purpose |
| [Class Index](docs/generated/SOURCE_CLASS_INDEX_CURRENT.md) | All 41 Java classes with role and dependencies |
| [Resource Index](docs/generated/RESOURCE_INDEX_CURRENT.md) | All 37 resource files indexed by category |

---

## Architecture at a Glance

```
App ──[Create Task]──► Firebase RTDB
App ──[MQTT command]──► Robot
Robot ──[MQTT telemetry/status]──► App ──[MqttFirebaseBridge]──► Firebase
Firebase ──[listener]──► Both Sender + Receiver App
App ──[BLE]──► Robot (physical handshake at pickup and dropoff)
```

- **compileSdk**: 36 | **minSdk**: 24
- **Java 11** | ViewBinding | BuildConfig
- **Firebase**: Auth + RTDB + FCM
- **MQTT**: HiveMQ SDK 1.3.15
- **Map**: MapLibre 11.0.0 + OSRM routing
- **BLE**: GATT (SERVICE_UUID: 0000FFE0, CHAR_UUID: 0000FFE1)
