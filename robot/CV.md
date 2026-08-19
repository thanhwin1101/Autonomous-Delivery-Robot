PHAN LE THANH NGUYEN
Embedded Software Engineer
Da Nang, Vietnam | (+84) 325 113 108 | nguyenplt.forwork@gmail.com
GitHub: github.com/thanhwin1101 | LinkedIn: [Your-LinkedIn-URL]

----------------------------------------------------------------------------------------------------
PROFESSIONAL SUMMARY
----------------------------------------------------------------------------------------------------
Entry-level Embedded Software Engineer with strong expertise in Embedded C/C++, FreeRTOS, and 
multi-MCU distributed architectures (ESP32, STM32, Linux SBC). Proficient in low-level drivers 
(UART/I2C/SPI/PWM/ADC), deterministic State Machines (FSM), CRC data integrity, and IoT protocols. 
Hands-on experience in on-chip hardware debugging, remote telemetry (OTA/Telnet), and sensor fusion.

----------------------------------------------------------------------------------------------------
TECHNICAL SKILLS
----------------------------------------------------------------------------------------------------
• Languages & OS: Embedded C, C++ (C++11/17), Python, FreeRTOS, Linux SBC (Raspberry Pi OS).
• Hardware & MCUs: STM32 (ARM Cortex-M), ESP32 (Dual-core), Raspberry Pi 4, Arduino, Sensors & Actuators.
• Protocols & Low-Level: UART/USART, I2C, SPI, PWM, ADC, GPIO, Timers, BLE (GATT), MQTT, Non-blocking FSM.
• Hardware & Debug Tools: ST-LINK/J-Link (SWD), Logic Analyzer (Saleae), Digital Oscilloscope, GDB, 
  TelnetSpy (TCP/IP debug), ArduinoOTA, STM32CubeIDE, PlatformIO, VS Code, Proteus.
• Concepts & Algorithms: Moving Average Filter, PID Control, Quadrature Encoders, CRC8, ASPICE, MISRA-C.

----------------------------------------------------------------------------------------------------
PROJECT EXPERIENCE
----------------------------------------------------------------------------------------------------
Carry Robot – Autonomous Medical Delivery AGV System | Jan 2026 – Apr 2026
Embedded Software Engineer (Capstone Lead) | Tech: C/C++, ESP32, STM32, FreeRTOS, MQTT, UART, OTA
• Architected a Multi-MCU distributed system partitioning heavy IoT/telemetry (ESP32 Master) from real-time 
  locomotion (STM32 Slave) via a non-blocking Finite State Machine (Auto, Follow, Recovery) without delay().
• Formulated a custom serial protocol over UART (115200 baud) using frame formatting (<CMD:DATA|CRC>) and 
  CRC-8 validation, completely eliminating frame loss and electrical noise corruption.
• Integrated MQTT (PubSubClient) & ArduinoJson over Wi-Fi for sub-second telemetry (checkpoints, battery, 
  alarms); built a WiFiManager Captive Portal storing persistent credentials in Non-Volatile Flash (Preferences).
• Deployed Over-The-Air (ArduinoOTA) firmware updates and remote headless debugging via TelnetSpy (TCP/IP).
• Implemented I2C OLED (U8g2) UI and an ADC Moving Average Filter for real-time, noise-free battery tracking.

Autonomous Peer-to-Peer Delivery Robot (AGV) | May 2026 – Jul 2026
Embedded & Firmware Specialist (Team Project) | Tech: C++, Python, Raspberry Pi 4, ESP32, BLE, RTK-GPS, ToF
• Engineered a dual-tier SBC-to-MCU control bus (Raspberry Pi 4 Master – ESP32 Slave) over full-duplex 
  CRC8-secured UART to stream high-level navigation setpoints to low-level motor actuation.
• Interfaced Quectel LC29H RTK-GPS (NMEA via UART) and fused with Google Maps API for pinpoint waypoint routing.
• Programmed closed-loop speed control for differential Tank Drive chassis using wheel encoders; integrated 
  VL53L0X Time-of-Flight (ToF) sensors via I2C for instant obstacle detection and emergency braking states.
• Developed a custom BLE GATT Server on ESP32 with context-aware RF power management for 1-tap secure parcel 
  unlocking; designed cloud-synced (Firebase) mission state machines (Pickup, Transit, Docking, Timeout Failsafe).

----------------------------------------------------------------------------------------------------
EDUCATION & CERTIFICATIONS
----------------------------------------------------------------------------------------------------
Bachelor of Computing (Honours) – Upper Second Class (2:1)                 May 2022 – Aug 2026
University of Greenwich (Da Nang Campus) | GPA: 3.62 / 4.00
Certifications: VSTEP English Proficiency Certificate – Level B2 (May 2023)

----------------------------------------------------------------------------------------------------
HONORS & AWARDS
----------------------------------------------------------------------------------------------------
• Third Prize – Central Region Hackathon Competition (2024)
