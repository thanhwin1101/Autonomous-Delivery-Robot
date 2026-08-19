# Autonomous Delivery Robot (AGV) 🚚🤖

An advanced Autonomous Guided Vehicle (AGV) designed for outdoor delivery tasks. The system features a robust architecture combining a high-level Linux navigator (Raspberry Pi) and a real-time hardware controller (ESP32 with FreeRTOS). It also includes a comprehensive Web Dashboard and a Mobile Application for real-time tracking and dispatching.

## 🌟 System Architecture

This repository is organized into three main components:

### 1. 🤖 Robot Core (`/robot`)
- **Pi Master (`pi_master/`)**: A Raspberry Pi running a Python state machine. It handles GPS parsing, dynamic routing, order lifecycle management, and synchronization with the MQTT backend.
- **ESP32 RTOS Slave (`arduino_slave/`)**: The real-time hardware controller built on FreeRTOS. It manages concurrent tasks including PID motor tuning, ToF sensors for collision avoidance, and IMU data gathering using Mutexes for data safety. 

### 2. 🌐 Web Platform (`/web`)
- **Frontend**: Built with React.js (Vite, TailwindCSS). Features an interactive Leaflet map for live GPS tracking of the AGV, order dispatching, and telemetry monitoring.
- **Backend**: A Node.js (Express) server bridging MQTT and Firebase. It ensures real-time state synchronization between the web clients, the mobile app, and the physical robot.

### 3. 📱 Mobile App (`/AutoDeliveryApp`)
- A mobile application allowing users to place delivery orders, track their packages on a map in real-time, and receive notifications when the robot arrives.

## 🛠️ Tech Stack
- **Hardware/Embedded**: Raspberry Pi, ESP32, FreeRTOS, C/C++, Python.
- **Web**: React.js, Node.js, Express, Firebase Admin, MQTT, WebSockets, Leaflet.
- **Mobile**: Android (Java).
- **Sensors**: VL53L0X (ToF), MPU9250/6050 (IMU), Neo-6M/8M GPS.

## 🚀 Getting Started

### Running the Web Dashboard
```bash
# 1. Start Backend
cd web/backend
npm install
npm start

# 2. Start Frontend
cd ../frontend
npm install
npm run dev
```

### Running the Robot Controller
```bash
# On Raspberry Pi
cd robot/pi_master
pip install -r requirements.txt
python main.py
```
*(Ensure the ESP32 code from `robot/arduino_slave` is flashed to the microcontroller first).*

## 📄 Notes
This project was developed as a Final Year Project showcasing embedded systems, IoT integration, and full-stack web/mobile development.
