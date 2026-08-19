# Auto Delivery App

## Purpose
This project is an Android Native Java mobile application developed to manage, assign, and track automated delivery robots. It acts as a bridge between the customer (senders and receivers) and the physical delivery robot hardware.

## Tech Stack
* **Language / Platform**: Android Native Java (JDK 11 compatibility)
* **SDK Versions**: `compileSdk 36`, `targetSdk 36`, `minSdk 24`
* **Build System**: Gradle Kotlin DSL (`build.gradle.kts` and `settings.gradle.kts`)
* **Backend Database**: Firebase Realtime Database
* **Authentication**: Firebase Auth
* **Push Notifications**: Firebase Cloud Messaging (FCM) Client
* **Robot Communication**: HiveMQ MQTT via TCP TLS (Port 8883)
* **Map Display**: MapLibre Android SDK (v11.0.0)
* **Map Style Provider**: MapTiler Cloud Streets v2
* **Routing Services**: OSRM Public Routing API with local coordinates lookup
* **Fallback Algorithm**: Haversine distance calculator

## Main Features
* **Authentication**: User registration, login (using email or phone lookup), forgot password, and profile updates.
* **Slot Booking**: Realtime robot slot availability verification and reservation (using atomic Firebase Realtime Database Transactions) for `slot1`, `slot2`, and `slot3`.
* **Task Management**: Senders can create delivery tasks specifying pickup/dropoff points, recipient, and slot. Receivers are looked up via phone number, and tasks are automatically linked to their dashboard.
* **Map Routing**: MapLibre-rendered maps using MapTiler style, showing optimal route lines calculated dynamically via OSRM API (with straight-line fallback using Haversine formula if OSRM is offline).
* **Realtime Tracking**: Interactive tracking interface showing active robot movement, ETA, remaining distance, battery life, and speed using MQTT telemetry feeds.
* **Notification History**: Local in-app notifications generated upon task creation, pickup, transit, and delivery completion.

## Project Structure
```text
app/src/main/
├── AndroidManifest.xml
├── java/com/example/autodeliveryapp/
│   ├── adapters/          # RecyclerView Adapters (ActiveTask, History, Notification)
│   ├── data/              # Data Models (HistoryItem, NotificationItem)
│   ├── model/             # MQTT Payload Models (RobotAck, RobotCommand, RobotStatusMessage, RobotTelemetry)
│   ├── mqtt/              # MQTT Client Logic (MqttConfig, MqttManager, MqttTopics, MqttPayloadParser, MqttFirebaseBridge)
│   ├── utils/             # Helpers (PhoneUtils, NotificationUtils, SlotUtils, TaskStatus)
│   └── *.java             # UI Activities (Login, Register, MainActivity, TrackingActivity, ProfileActivity, etc.)
└── res/                   # Layouts, themes, color assets, and drawables
```

## Setup Requirements
* **Android Studio**: Android Studio (Jellyfish or newer recommended).
* **JDK**: JDK 11 set as `JAVA_HOME`.
* **MapTiler Account**: A free MapTiler account to acquire an API key.
* **HiveMQ Cloud**: An active HiveMQ Cloud instance.
* **Firebase Project**: A Firebase project configured for Android, with Auth and Realtime Database enabled.

## Firebase Configuration
1. Register the application package `com.example.autodeliveryapp` in your Firebase Console.
2. Download the `google-services.json` file and place it inside the `app/` folder.
3. Configure the database URL `DB_URL` in `local.properties`:
   ```properties
   DB_URL=https://<your-project-id>-default-rtdb.firebaseio.com
   ```
4. Deploy the database rules. Refer to `docs/FIREBASE_RULES_REQUIRED.md` for details.

## MQTT / HiveMQ Configuration
Set up your HiveMQ credentials in `local.properties`:
```properties
MQTT_HOST=<your-hivemq-host>.hivemq.cloud
MQTT_PORT=8883
MQTT_USERNAME=<your-mqtt-username>
MQTT_PASSWORD=<your-mqtt-password>
MQTT_USE_SSL=true
MQTT_ENV=dev
MQTT_DEFAULT_ROBOT_ID=defaultRobot
```
*Note: If these properties are empty or missing, MQTT connections will fail.*

## Map / Routing Configuration
Acquire a MapTiler API Key and set it up in `local.properties`:
```properties
MAPTILER_API_KEY=<your-maptiler-api-key>
```
The app maps static Da Nang destination names (*Inferred from code*) to coordinate pairs inside `Constants.java`.

## Build Instructions
Open your terminal in the root of the project and execute:
```powershell
# Set Java Home to Android Studio's JDK
$env:JAVA_HOME="E:\Application\AndroidStudio\App\jbr"

# Build debug APK
.\gradlew assembleDebug
```
The compiled APK will be available in `app/build/outputs/apk/debug/app-debug.apk`.

## Runtime Testing
To install and run the app on a connected device:
```powershell
.\gradlew installDebug
```
For testing robot communication without hardware, check out `docs/MQTT_ROBOT_SIMULATOR_GUIDE.md`.

## Known Issues
* **FCM Push Backend**: A real push notification backend (e.g., Cloud Functions or Node.js Admin SDK) is *not implemented*. Client token handling exists, but push triggers must be simulated from the Firebase Console.
* **Client-side Mirroring**: Telemetry updates from MQTT are written back to Firebase by the client app (`MqttFirebaseBridge`). If the app is closed, tracking coordinates will stop syncing with Firebase (*Inferred from code*).
* **Firebase Security Rules**: Writing directly from the sender's device to the receiver's node (`recipientTasks` and `notifications`) may fail with `Permission Denied` if database rules are set to standard auth user scopes.

## Next Development Plan
1. Move the MQTT-to-Firebase bridging logic to a dedicated server/bridge container.
2. Implement Cloud Functions to handle receiver notifications (`notifications/{receiverUid}`) and task sharing safely.
3. Hook up physical hardware or deploy the MQTT simulation tool to verify telemetry updates.
