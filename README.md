# Autonomous Delivery Robot (AGV)

An advanced Autonomous Guided Vehicle (AGV) designed for outdoor delivery tasks. The system features a robust architecture combining a high-level Linux navigator (Raspberry Pi) and a real-time hardware controller (ESP32 with FreeRTOS). It also includes a comprehensive Web Dashboard and a Mobile Application for real-time tracking and dispatching.

## System Architecture

This repository is organized into three main components:

### 1. Robot Core (/robot)
- **Pi Master (pi_master/)**: A Raspberry Pi running a Python state machine. It handles GPS parsing, dynamic routing, order lifecycle management, and synchronization with the MQTT backend.
- **ESP32 RTOS Slave (arduino_slave/)**: The real-time hardware controller built on FreeRTOS. It manages concurrent tasks including PID motor tuning, ToF sensors for collision avoidance, and IMU data gathering using Mutexes for data safety. 

### 2. Web Platform (/web)
- **Frontend**: Built with React.js (Vite, TailwindCSS). Features an interactive Leaflet map for live GPS tracking of the AGV, order dispatching, and telemetry monitoring.
- **Backend**: A Node.js (Express) server bridging MQTT and Firebase. It ensures real-time state synchronization between the web clients, the mobile app, and the physical robot.

### 3. Mobile App (/AutoDeliveryApp)
- A native Android application (Java/XML) allowing users to place delivery orders, track their packages on a map in real-time, and receive notifications when the robot arrives.

## Code Structure

- /robot/pi_master: Contains Python scripts for high-level decision making (main.py), GPS handling (gps_nav.py), MQTT communication (mqtt_mgr.py), and UART serial interface (uart_comm.py).
- /robot/arduino_slave: PlatformIO project for ESP32. Contains FreeRTOS tasks (src/main.cpp) for hardware polling, sensor fusion (IMU, ToF), and PID motor control.
- /web/frontend: Vite-based React application for the control dashboard.
- /web/backend: Node.js server handling MQTT broker subscriptions and Firebase state persistence (server.js).
- /AutoDeliveryApp/app/src/main: Standard Android project structure containing Activities, Layouts, and Data models for the end-user delivery app.

## Operation Flow

1. Order Placement: A user submits a delivery request via the Web Dashboard or the Mobile App. The order is stored in Firebase.
2. State Synchronization: The Node.js backend detects the new order in Firebase and publishes an MQTT payload.
3. Route Planning: The Raspberry Pi (Pi Master) receives the MQTT payload, extracts the destination coordinates, and calculates the route using the GPS module.
4. Execution: The Pi Master transitions the state machine to routing mode and continuously sends velocity/steering commands to the ESP32 (RTOS Slave) via UART.
5. Real-time Control: The ESP32 processes the commands using a PID loop to drive the motors. It simultaneously reads the VL53L0X ToF sensors for obstacle detection and the MPU9250 for orientation (IMU).
6. Telemetry Feedback: The ESP32 sends real-time hardware status back to the Pi, which then relays the telemetry data and current GPS coordinates to the Web and Mobile apps via MQTT.

## Tech Stack

- Hardware/Embedded: Raspberry Pi, ESP32, FreeRTOS, C/C++, Python.
- Web: React.js, Node.js, Express, Firebase Admin, MQTT, WebSockets, Leaflet.
- Mobile: Android (Java).
- Sensors: VL53L0X (ToF), MPU9250/6050 (IMU), Neo-6M/8M GPS.
