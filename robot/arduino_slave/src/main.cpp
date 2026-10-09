// ====================================================================
//  Delivery Robot – ESP32 HARDWARE SLAVE (Embedded C++ OOP & FreeRTOS)
//  ------------------------------------------------------------------
//  - Hardware PCNT: Single-channel rising-edge counting; software reads counters.
//  - Dual-Core FreeRTOS: Core 0 (Comms + BLE) | Core 1 (PID Motor + ToF Safety)
//  - Static subsystem objects; BLE, JSON, tasks and mutex still allocate heap.
// ====================================================================
#include <Arduino.h>
#include <Wire.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <ESP32Servo.h>
#include <MadgwickAHRS.h>
#include <ArduinoJson.h>
#include <ctype.h>
#include <math.h>
#include "Adafruit_VL53L0X.h"

#include "pcnt_encoder.h"
#include "motor_controller.h"
#include "pid_controller.h"

// ====================================================================
// 1. PIN DEFINITIONS
// ====================================================================
#define I2C_SDA             21
#define I2C_SCL             22
#define MPU6050_ADDR        0x68
#define COMPASS_ADDR        0x2C

#define ENC_L_PIN           16
#define ENC_R_PIN           17

#define ENA                 14
#define IN1                 32
#define IN2                 33
#define IN3                 25
#define IN4                 26
#define ENB                 27

#define SERVO_PIN           23
#define LIMIT_SWITCH_PIN    5
#define BATTERY_ADC_PIN     36
#define BUZZER_PIN          19

// ====================================================================
// 2. HARDWARE OBJECTS (OOP Subsystems)
// ====================================================================
static PcntEncoder     g_encL(PCNT_UNIT_0, ENC_L_PIN);
static PcntEncoder     g_encR(PCNT_UNIT_1, ENC_R_PIN);
static L298nDualMotor  g_motors(ENA, IN1, IN2, 2, ENB, IN3, IN4, 3);
static ClosedLoopPid   g_pidL(3.5f, 0.8f, 0.2f);
static ClosedLoopPid   g_pidR(3.5f, 0.8f, 0.2f);

static Adafruit_VL53L0X g_tof;
static Madgwick         g_imuFilter;
static Servo            g_doorServo;

// ====================================================================
// 3. THREAD-SAFE TELEMETRY & CONCURRENCY
// ====================================================================
static SemaphoreHandle_t g_dataMutex = nullptr;
static SemaphoreHandle_t g_uartMutex = nullptr;

static void sendUartLine(const char* line) {
    if (xSemaphoreTake(g_uartMutex, portMAX_DELAY)) {
        Serial.println(line);
        xSemaphoreGive(g_uartMutex);
    }
}

struct TelemetryData {
    int32_t  encLeftCount;
    int32_t  encRightCount;
    float    yaw;
    int16_t  ax, ay, az;
    int16_t  gx, gy, gz;
    float    gz_deg;
    uint16_t tofDistanceMm;
    float    batteryPercentage;
    bool     obstacleDetected;
};

static TelemetryData g_telemetry = {0, 0, 0.0f, 0, 0, 0, 0, 0, 0, 0.0f, 9999, 100.0f, false};

// Điều khiển từ Pi
static float g_targetSpeedL = 0.0f;
static float g_targetSpeedR = 0.0f;
// Pi navigation sends at ~100 ms; 1000 ms allows ten missed active updates.
static constexpr uint32_t SPEED_TIMEOUT_MS = 1000;
static constexpr float MAX_TARGET_SPEED = 25.0f; // ticks / 50 ms, gps_nav.py limit
static uint32_t g_lastSpeedMs = 0;
static bool g_haveSpeed = false;
static bool  g_sysReady = false;

// Trạng thái cửa
enum class DoorState : uint8_t { CLOSED, OPEN, WAITING_TO_CLOSE };
static DoorState g_doorState = DoorState::CLOSED;
static int       g_currentServoAngle = 100;
static int       g_targetServoAngle = 100;
static uint32_t  g_boxFullTime = 0;
static bool      g_initialBoxState = false;

// Buzzer
static int  g_buzzerBeeps = 0;

static void triggerBuzzer(int beeps) {
    if (xSemaphoreTake(g_dataMutex, portMAX_DELAY)) {
        g_buzzerBeeps = beeps * 2;
        xSemaphoreGive(g_dataMutex);
    }
}

// ====================================================================
// 4. BLE GATT SERVER
// ====================================================================
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID_RX "beb5483e-36e1-4688-b7f5-ea07361b26a8"

static BLEServer*         g_pServer = nullptr;
static BLECharacteristic* g_pRxChar = nullptr;
static char               g_localToken[64] = {0};

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        triggerBuzzer(1);
    }
    void onDisconnect(BLEServer* pServer) override {
        BLEDevice::startAdvertising();
        triggerBuzzer(2);
    }
};

class RxCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        String jsonStr = pCharacteristic->getValue().c_str();
        jsonStr.trim();
        if (jsonStr.length() == 0) return;

        JsonDocument doc;
        if (deserializeJson(doc, jsonStr)) return;

        const char* action = doc["action"] | "";
        const char* token  = doc["token"] | "";

        bool tokenValid = false;
        bool opened = false;
        if (xSemaphoreTake(g_dataMutex, portMAX_DELAY)) {
            tokenValid = g_localToken[0] && strcmp(token, g_localToken) == 0;
            if (tokenValid && (strcmp(action, "open_slot") == 0 || strcmp(action, "confirm_loaded") == 0)) {
                g_targetServoAngle = 30;
                g_doorState = DoorState::OPEN;
                g_initialBoxState = (digitalRead(LIMIT_SWITCH_PIN) == LOW);
                opened = true;
            }
            xSemaphoreGive(g_dataMutex);
        }
        if (tokenValid) {
            if (strcmp(action, "verify_token") == 0) {
                pCharacteristic->setValue("{\"ok\":true,\"action\":\"verify_token\"}");
                pCharacteristic->notify();
            } else if (opened) {
                sendUartLine("DOOR:OPENED");
                triggerBuzzer(3);
                pCharacteristic->setValue("{\"ok\":true,\"action\":\"opened\"}");
                pCharacteristic->notify();
            }
        } else {
            pCharacteristic->setValue("{\"ok\":false,\"error\":\"INVALID_TOKEN\"}");
            pCharacteristic->notify();
        }
    }
};

// ====================================================================
// 5. CRC8 & UART PROTOCOL
// ====================================================================
static uint8_t crc8_calc(const char* data, size_t len) {
    uint8_t crc = 0x00;
    for (size_t i = 0; i < len; i++) {
        crc ^= (uint8_t)data[i];
        for (uint8_t j = 0; j < 8; j++) {
            if (crc & 0x80) crc = ((crc << 1) ^ 0x07);
            else            crc <<= 1;
        }
    }
    return crc;
}

static void handlePiCommand(const char* cmd) {
    if (!cmd || !*cmd) return;

    if (strncmp(cmd, "SPEED:", 6) == 0) {
        float l = 0, r = 0;
        int consumed = 0;
        if (sscanf(cmd + 6, "%f:%f%n", &l, &r, &consumed) == 2 &&
            cmd[6 + consumed] == '\0' && isfinite(l) && isfinite(r) &&
            fabsf(l) <= MAX_TARGET_SPEED && fabsf(r) <= MAX_TARGET_SPEED) {
            if (xSemaphoreTake(g_dataMutex, portMAX_DELAY)) {
                g_targetSpeedL = l;
                g_targetSpeedR = r;
                g_lastSpeedMs = millis();
                g_haveSpeed = true;
                xSemaphoreGive(g_dataMutex);
            }
        }
    } else if (strncmp(cmd, "BLE:ON:", 7) == 0) {
        size_t len = strlen(cmd + 7);
        if (!len || len >= sizeof(g_localToken)) return;
        if (xSemaphoreTake(g_dataMutex, portMAX_DELAY)) {
            memcpy(g_localToken, cmd + 7, len + 1);
            xSemaphoreGive(g_dataMutex);
        }
        BLEDevice::startAdvertising();
    } else if (strcmp(cmd, "BLE:OFF") == 0) {
        if (xSemaphoreTake(g_dataMutex, portMAX_DELAY)) {
            g_localToken[0] = '\0';
            xSemaphoreGive(g_dataMutex);
        }
        BLEDevice::getAdvertising()->stop();
    } else if (strncmp(cmd, "BUZZER:", 7) == 0) {
        char* end = nullptr;
        long beeps = strtol(cmd + 7, &end, 10);
        if (end != cmd + 7 && *end == '\0' && beeps >= 0 && beeps <= 20) triggerBuzzer((int)beeps);
    } else if (strcmp(cmd, "BOX:CHECK") == 0) {
        bool empty = (digitalRead(LIMIT_SWITCH_PIN) == HIGH);
        sendUartLine(empty ? "BOX:EMPTY" : "BOX:FULL");
    } else if (strcmp(cmd, "SYS:CHECK") == 0) {
        sendUartLine(g_sysReady ? "SYS:READY" : "SYS:ERROR:TOF");
    }
}

// ====================================================================
// 6. FREERTOS TASKS
// ====================================================================

// TASK COMMS (Core 0): Truyền thông UART với Pi + BLE + Telemetry (20Hz)
static void TaskComms(void* pvParameters) {
    char rxBuf[128];
    size_t rxLen = 0;
    bool discardLine = false;
    uint32_t lastTelemetryMs = 0;

    for (;;) {
        // 1. Nhận lệnh UART
        while (Serial.available() > 0) {
            char c = (char)Serial.read();
            if (c == '\n') {
                if (discardLine) { discardLine = false; rxLen = 0; continue; }
                rxBuf[rxLen] = '\0';
                // Kiểm tra CRC nếu có ký tự '*'
                char* star = strrchr(rxBuf, '*');
                if (star && strlen(star + 1) == 2 &&
                    isxdigit((unsigned char)star[1]) && isxdigit((unsigned char)star[2])) {
                    *star = '\0';
                    uint8_t rxCrc = (uint8_t)strtol(star + 1, nullptr, 16);
                    uint8_t calcCrc = crc8_calc(rxBuf, strlen(rxBuf));
                    if (rxCrc == calcCrc) {
                        handlePiCommand(rxBuf);
                    }
                }
                rxLen = 0;
            } else if (c != '\r' && !discardLine) {
                if (rxLen < sizeof(rxBuf) - 1 && c != '\0') rxBuf[rxLen++] = c;
                else { discardLine = true; rxLen = 0; }
            }
        }

        // 2. Gửi Telemetry (50ms - 20Hz)
        uint32_t now = millis();
        if (g_sysReady && (now - lastTelemetryMs >= 50)) {
            lastTelemetryMs = now;

            TelemetryData localTel;
            if (xSemaphoreTake(g_dataMutex, portMAX_DELAY)) {
                localTel = g_telemetry;
                xSemaphoreGive(g_dataMutex);
            }

            char txBuf[160];
            snprintf(txBuf, sizeof(txBuf),
                     "ODO:%ld:%ld:%.2f:%d:%d:%d:%d:%d:%d:%.2f:%.1f:%d",
                     (long)localTel.encLeftCount, (long)localTel.encRightCount, localTel.yaw,
                     localTel.ax, localTel.ay, localTel.az,
                     localTel.gx, localTel.gy, localTel.gz,
                     localTel.gz_deg, localTel.batteryPercentage, localTel.tofDistanceMm);

            uint8_t crc = crc8_calc(txBuf, strlen(txBuf));
            char frame[sizeof(txBuf) + 4];
            snprintf(frame, sizeof(frame), "%s*%02X", txBuf, crc);
            sendUartLine(frame);
        }

        vTaskDelay(pdMS_TO_TICKS(10));
    }
}

// TASK CONTROL (Core 1): Điều khiển Động cơ PID, Cảm biến ToF & Madgwick AHRS (200Hz)
static void TaskControl(void* pvParameters) {
    TickType_t xLastWakeTime = xTaskGetTickCount();
    const TickType_t xFrequency = pdMS_TO_TICKS(5); // Chu kỳ 5ms (200Hz)

    uint32_t lastBatteryMs = 0;
    uint32_t lastServoStepMs = 0;
    uint32_t lastEncoderMs = millis();
    bool forwardL = true, forwardR = true;
    bool lastObs = false;

    for (;;) {
        vTaskDelayUntil(&xLastWakeTime, xFrequency);

        uint32_t now = millis();

        // 1. Đọc IMU MPU6050 & Cập nhật Madgwick AHRS (200Hz)
        int16_t rawAx = 0, rawAy = 0, rawAz = 0;
        int16_t rawGx = 0, rawGy = 0, rawGz = 0;
        float yaw = 0.0f, gz_deg = 0.0f;

#if !defined(TEST_HARNESS)
        Wire.beginTransmission(MPU6050_ADDR);
        Wire.write(0x3B);
        if (Wire.endTransmission(false) == 0 && Wire.requestFrom((uint8_t)MPU6050_ADDR, (size_t)14, true) == 14) {
            rawAx = (int16_t)((Wire.read() << 8) | Wire.read());
            rawAy = (int16_t)((Wire.read() << 8) | Wire.read());
            rawAz = (int16_t)((Wire.read() << 8) | Wire.read());
            Wire.read(); Wire.read(); // Bỏ qua temp
            rawGx = (int16_t)((Wire.read() << 8) | Wire.read());
            rawGy = (int16_t)((Wire.read() << 8) | Wire.read());
            rawGz = (int16_t)((Wire.read() << 8) | Wire.read());

            float ax_g = (float)rawAx / 16384.0f;
            float ay_g = (float)rawAy / 16384.0f;
            float az_g = (float)rawAz / 16384.0f;
            float gx_dps = (float)rawGx / 131.0f;
            float gy_dps = (float)rawGy / 131.0f;
            gz_deg = (float)rawGz / 131.0f;

            g_imuFilter.updateIMU(gx_dps, gy_dps, gz_deg, ax_g, ay_g, az_g);
            yaw = g_imuFilter.getYaw();
        }
#endif

        // 2. Đọc Cảm Biến ToF VL53L0X
        VL53L0X_RangingMeasurementData_t measure = {};
        measure.RangeStatus = 4;
        bool rangeOk = g_sysReady && g_tof.rangingTest(&measure, false) == VL53L0X_ERROR_NONE;
        uint16_t dist = 9999;
        bool obs = true; // Missing/invalid range is not evidence of a clear path.

        if (rangeOk && measure.RangeStatus == 0 && measure.RangeMilliMeter > 0 && measure.RangeMilliMeter < 8000) {
            dist = measure.RangeMilliMeter;
            obs = dist < 300;
        }
        now = millis(); // Includes time spent in the blocking ranging call.
        uint32_t elapsedMs = now - lastEncoderMs;
        lastEncoderMs = now;

        // 3. Đọc Encoder Phần Cứng (PCNT)
        int32_t deltaL = g_encL.getDeltaAndReset();
        int32_t deltaR = g_encR.getDeltaAndReset();
        // Single-channel PCNT cannot measure direction; infer the preceding motor output.
        int32_t totalL = g_encL.getPulseCount(forwardL);
        int32_t totalR = g_encR.getPulseCount(forwardR);

        // 4. Tính Toán PID Vận Tốc (200Hz)
        float targetL = 0.0f, targetR = 0.0f;
        if (xSemaphoreTake(g_dataMutex, portMAX_DELAY)) {
            if (!g_haveSpeed || millis() - g_lastSpeedMs >= SPEED_TIMEOUT_MS) {
                g_targetSpeedL = 0.0f;
                g_targetSpeedR = 0.0f;
                g_haveSpeed = false;
            }
            targetL = g_targetSpeedL;
            targetR = g_targetSpeedR;
            g_telemetry.encLeftCount = totalL;
            g_telemetry.encRightCount = totalR;
            g_telemetry.yaw = yaw;
            g_telemetry.ax = rawAx;
            g_telemetry.ay = rawAy;
            g_telemetry.az = rawAz;
            g_telemetry.gx = rawGx;
            g_telemetry.gy = rawGy;
            g_telemetry.gz = rawGz;
            g_telemetry.gz_deg = gz_deg;
            g_telemetry.tofDistanceMm = dist;
            g_telemetry.obstacleDetected = obs;
            xSemaphoreGive(g_dataMutex);
        }

        if (obs) {
            // Có vật cản -> Phanh khẩn cấp
            g_motors.stop();
            g_pidL.reset();
            g_pidR.reset();
        } else if (targetL == 0 && targetR == 0) {
            g_motors.stop();
            g_pidL.reset();
            g_pidR.reset();
        } else {
            // Normalize measured ticks to the unchanged wire unit ticks/50 ms.
            float scale = elapsedMs ? 50.0f / elapsedMs : 0.0f;
            float pwmL = constrain(g_pidL.compute(fabsf(targetL), deltaL * scale), 0.0f, 255.0f);
            float pwmR = constrain(g_pidR.compute(fabsf(targetR), deltaR * scale), 0.0f, 255.0f);
            if (targetL == 0) { pwmL = 0; g_pidL.reset(); }
            if (targetR == 0) { pwmR = 0; g_pidR.reset(); }
            if (targetL < 0) pwmL = -pwmL;
            if (targetR < 0) pwmR = -pwmR;
            if (pwmL != 0) forwardL = pwmL > 0;
            if (pwmR != 0) forwardR = pwmR > 0;
            g_motors.setSpeed((int16_t)pwmL, (int16_t)pwmR);
        }

        // 4. Quản Lý Nắp Tủ Giao Hàng & Servo
        bool closed = false, closingBeep = false;
        if (xSemaphoreTake(g_dataMutex, portMAX_DELAY)) {
            if (g_currentServoAngle != g_targetServoAngle && (now - lastServoStepMs >= 10)) {
                lastServoStepMs = now;
                if (g_currentServoAngle < g_targetServoAngle) g_currentServoAngle += 2;
                else                                         g_currentServoAngle -= 2;
                g_doorServo.write(g_currentServoAngle);
                closed = (g_currentServoAngle == 100 && g_doorState == DoorState::CLOSED);
            }

            if (g_doorState == DoorState::OPEN) {
                bool boxFilled = (digitalRead(LIMIT_SWITCH_PIN) == LOW);
                if (boxFilled != g_initialBoxState) {
                    g_doorState = DoorState::WAITING_TO_CLOSE;
                    g_boxFullTime = now;
                }
            } else if (g_doorState == DoorState::WAITING_TO_CLOSE) {
                if (now - g_boxFullTime >= 8000) {
                    g_targetServoAngle = 100; // Đóng nắp sau 8s
                    g_doorState = DoorState::CLOSED;
                    closingBeep = true;
                }
            }
            xSemaphoreGive(g_dataMutex);
        }
        if (closed) sendUartLine("DOOR:CLOSED");
        if (closingBeep) triggerBuzzer(1);

        // 5. Cập nhật Điện Áp Pin (1Hz)
        if (now - lastBatteryMs >= 1000) {
            lastBatteryMs = now;
            float voltage = (analogRead(BATTERY_ADC_PIN) / 4095.0f) * 3.3f;
            float battPct = constrain(((voltage - 2.8f) / (3.3f - 2.8f)) * 100.0f, 0.0f, 100.0f);
            if (xSemaphoreTake(g_dataMutex, portMAX_DELAY)) {
                g_telemetry.batteryPercentage = battPct;
                xSemaphoreGive(g_dataMutex);
            }
        }

        // 6. Cảnh báo Buzzer
        int beeps = 0;
        if (xSemaphoreTake(g_dataMutex, portMAX_DELAY)) {
            beeps = g_buzzerBeeps;
            if (!obs && beeps > 0) --g_buzzerBeeps;
            xSemaphoreGive(g_dataMutex);
        }
        if (obs != lastObs) {
            sendUartLine(obs ? "WARN:OBSTACLE" : "WARN:CLEAR");
            lastObs = obs;
        }
        if (obs) {
            digitalWrite(BUZZER_PIN, HIGH);
        } else if (beeps > 0) {
            digitalWrite(BUZZER_PIN, (beeps % 2 == 0) ? HIGH : LOW);
        } else {
            digitalWrite(BUZZER_PIN, LOW);
        }
    }
}

// ====================================================================
// 7. SETUP
// ====================================================================
void setup() {
    Serial.begin(115200);
    g_dataMutex = xSemaphoreCreateMutex();
    g_uartMutex = xSemaphoreCreateMutex();
    if (!g_dataMutex || !g_uartMutex) {
        g_motors.init();
        for (;;) delay(1000);
    }

    Wire.begin(I2C_SDA, I2C_SCL);
    Wire.setClock(400000);
    Wire.setTimeOut(50);

    // Khởi tạo MPU6050 & Madgwick AHRS (200Hz)
    Wire.beginTransmission(MPU6050_ADDR);
    Wire.write(0x6B); // PWR_MGMT_1
    Wire.write(0x00); // Wake up
    Wire.endTransmission(true);

    Wire.beginTransmission(MPU6050_ADDR);
    Wire.write(0x1B); // GYRO_CONFIG
    Wire.write(0x00); // ±250 deg/s
    Wire.endTransmission(true);

    Wire.beginTransmission(MPU6050_ADDR);
    Wire.write(0x1C); // ACCEL_CONFIG
    Wire.write(0x00); // ±2g
    Wire.endTransmission(true);

    g_imuFilter.begin(200.0f);

    // Khởi tạo phần cứng đếm xung PCNT
    g_encL.init();
    g_encR.init();

    // Khởi tạo cầu H L298N
    g_motors.init();

    // Khởi tạo ToF VL53L0X
    if (g_tof.begin()) {
        g_sysReady = true;
    }

    // Khởi tạo Servo & GPIO ngoại vi
    pinMode(LIMIT_SWITCH_PIN, INPUT_PULLUP);
    pinMode(BUZZER_PIN, OUTPUT);
    digitalWrite(BUZZER_PIN, LOW);

    g_doorServo.setPeriodHertz(50);
    g_doorServo.attach(SERVO_PIN, 500, 2400);
    g_doorServo.write(g_currentServoAngle);

    analogSetPinAttenuation(BATTERY_ADC_PIN, ADC_11db);

    // Khởi tạo BLE Server
    BLEDevice::init("AGV_DELIVERY_01");
    BLEDevice::setMTU(512);
    g_pServer = BLEDevice::createServer();
    g_pServer->setCallbacks(new ServerCallbacks());

    BLEService* pService = g_pServer->createService(SERVICE_UUID);
    g_pRxChar = pService->createCharacteristic(
        CHARACTERISTIC_UUID_RX,
        BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY
    );
    g_pRxChar->addDescriptor(new BLE2902());
    g_pRxChar->setCallbacks(new RxCallbacks());
    pService->start();

    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);

    // Tạo các Task FreeRTOS ghim cố định vào 2 Core (SMP)
    xTaskCreatePinnedToCore(TaskComms,   "TaskComms",   8192, nullptr, 1, nullptr, 0); // Core 0
    xTaskCreatePinnedToCore(TaskControl, "TaskControl", 8192, nullptr, 2, nullptr, 1); // Core 1
}

void loop() {
    vTaskDelete(nullptr);
}
