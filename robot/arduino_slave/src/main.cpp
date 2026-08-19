#include "Adafruit_VL53L0X.h"
#include <Arduino.h>
#include <ArduinoJson.h>
#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <ESP32Servo.h>
#include <MadgwickAHRS.h>
#include <Wire.h>

// ==========================================
// 1. PIN DEFINITIONS (ESP32)
// ==========================================
#define I2C_SDA 21
#define I2C_SCL 22
#define MPU6050_ADDR 0x68
#define COMPASS_ADDR 0x2C

#define XSHUT_FRONT 18

#define ENC_L_PIN 16
#define ENC_R_PIN 17

#define ENA 14
#define IN1 32
#define IN2 33
#define IN3 25
#define IN4 26
#define ENB 27

#define SERVO_PIN 23
#define LIMIT_SWITCH_PIN 5
#define BATTERY_ADC_PIN 36
#define BUZZER_PIN 19

// ==========================================
// 2. FREERTOS OBJECTS & SHARED VARIABLES
// ==========================================
SemaphoreHandle_t dataMutex;

// --- Dữ liệu điều khiển (Từ Pi -> ESP32) ---
float shared_targetSpeedL = 0;
float shared_targetSpeedR = 0;

// --- Dữ liệu Telemetry (Từ ESP32 -> Pi) ---
long shared_encLeftCount = 0;
long shared_encRightCount = 0;
float shared_yaw = 0.0;
int16_t shared_ax = 0, shared_ay = 0, shared_az = 0, shared_gx = 0,
        shared_gy = 0, shared_gz = 0;
float shared_gz_deg = 0.0;
bool shared_isObstacleDetected = false;
float shared_batteryPercentage = 100.0;
uint16_t shared_tofDist = 9999;

// --- Dữ liệu Trạng thái Hệ thống ---
bool sysReady = false;
String sysErrorMsg = "";

// --- Buzzer Control ---
int buzzerBeepsRemaining = 0;
unsigned long buzzerLastToggle = 0;
bool buzzerActiveState = false;

void triggerBuzzer(int beeps) {
  buzzerBeepsRemaining = beeps * 2;
  buzzerActiveState = true;
}

// ==========================================
// 3. HARDWARE VARIABLES (Local to Core 1 mostly)
// ==========================================
const int freq = 5000;
const int pwmChannelA = 2;
const int pwmChannelB = 3;
const int resolution = 8;

volatile long encLeftCount = 0;
volatile long encRightCount = 0;
volatile bool dirLeftForward = true;
volatile bool dirRightForward = true;

long prevEncL = 0;
long prevEncR = 0;

const float Kp = 3.5;
const float Ki = 0.8;
const float Kd = 0.2;
float errorL_integral = 0;
float errorR_integral = 0;
float last_errorL = 0;
float last_errorR = 0;

Adafruit_VL53L0X loxFront = Adafruit_VL53L0X();
bool tofFrontReady = false;

// MPU & Compass
bool mpuReady = false;
bool compassReady = false;
Madgwick filter;

Servo doorServo;
int currentServoAngle = 100;
int targetServoAngle = 100;

enum DoorState { DOOR_CLOSED, DOOR_OPEN, DOOR_WAITING_TO_CLOSE };
DoorState doorState = DOOR_CLOSED;
unsigned long boxFullTime = 0;
bool initialBoxState = false;

// ==========================================
// 4. BLE VARIABLES & CALLBACKS
// ==========================================
#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID_RX "beb5483e-36e1-4688-b7f5-ea07361b26a8"
BLEServer *pServer = NULL;
bool deviceConnected = false;
String localToken = "";

void logToPi(String msg) { Serial.println("LOG:" + msg); }

class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) {
    deviceConnected = true;
    triggerBuzzer(1); // 1 beep for connect
  }
  void onDisconnect(BLEServer *pServer) {
    deviceConnected = false;
    BLEDevice::startAdvertising();
    triggerBuzzer(2); // 2 beeps for disconnect
  }
};

class MyCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    String jsonStr = pCharacteristic->getValue().c_str();
    jsonStr.trim();
    if (jsonStr.length() > 0) {
      logToPi("BLE Recv: " + jsonStr);

      JsonDocument doc;
      DeserializationError error = deserializeJson(doc, jsonStr);
      if (error) {
        logToPi("BLE JSON parse failed");
        return;
      }

      String action = doc["action"] | "";
      String token = doc["token"] | "";

      logToPi("Action: " + action + " | Token: " + token +
              " | Expected: " + localToken);

      if (localToken.length() > 0 && token == localToken) {
        if (action == "verify_token") {
          String resp = "{\"ok\":true, \"action\":\"verify_token\"}";
          pCharacteristic->setValue(resp.c_str());
          pCharacteristic->notify();
          logToPi("Token MATCHED: verify_token OK");
        } else if (action == "open_slot" || action == "confirm_loaded") {
          if (xSemaphoreTake(dataMutex, portMAX_DELAY)) {
            targetServoAngle = 30; // Mở nắp
            doorState = DOOR_OPEN;
            initialBoxState = (digitalRead(LIMIT_SWITCH_PIN) == LOW);
            xSemaphoreGive(dataMutex);
          }
          Serial.println("DOOR:OPENED");
          triggerBuzzer(3); // 3 beeps for door opened
          logToPi("Token MATCHED: Door Opened via Servo! (" + action + ")");

          String resp = "{\"ok\":true, \"action\":\"" + action + "\"}";
          pCharacteristic->setValue(resp.c_str());
          pCharacteristic->notify();
        }
      } else {
        logToPi("Token MISMATCHED.");
        String resp = "{\"ok\":false, \"error\":\"INVALID_TOKEN\"}";
        pCharacteristic->setValue(resp.c_str());
        pCharacteristic->notify();
      }
    }
  }
};

// ==========================================
// 5. INTERRUPTS & HARDWARE FUNCTIONS
// ==========================================
void writeI2C(uint8_t addr, uint8_t reg, uint8_t data) {
  Wire.beginTransmission(addr);
  Wire.write(reg);
  Wire.write(data);
  Wire.endTransmission();
}

uint8_t readI2C(uint8_t addr, uint8_t reg) {
  Wire.beginTransmission(addr);
  Wire.write(reg);
  Wire.endTransmission(false);
  Wire.requestFrom((uint8_t)addr, (uint8_t)1);
  if (Wire.available())
    return Wire.read();
  return 0;
}
void IRAM_ATTR isrLeftEncoder() {
  if (dirLeftForward)
    encLeftCount++;
  else
    encLeftCount--;
}

void IRAM_ATTR isrRightEncoder() {
  if (dirRightForward)
    encRightCount++;
  else
    encRightCount--;
}

// readMPU6050 thô đã bị xóa để dùng thư viện Adafruit

void runPID(long deltaL, long deltaR, float targetL, float targetR,
            bool obstacleDetected) {
  if (targetL == 0 && targetR == 0) {
    digitalWrite(IN1, LOW);
    digitalWrite(IN2, LOW);
    digitalWrite(IN3, LOW);
    digitalWrite(IN4, LOW);
    ledcWrite(pwmChannelA, 0);
    ledcWrite(pwmChannelB, 0);
    errorL_integral = 0;
    errorR_integral = 0;
    last_errorL = 0;
    last_errorR = 0;
    return;
  }

  if (obstacleDetected && (targetL > 0 || targetR > 0)) {
    digitalWrite(IN1, LOW);
    digitalWrite(IN2, LOW);
    digitalWrite(IN3, LOW);
    digitalWrite(IN4, LOW);
    ledcWrite(pwmChannelA, 0);
    ledcWrite(pwmChannelB, 0);
    errorL_integral = 0;
    errorR_integral = 0;
    return;
  }

  dirLeftForward = (targetL >= 0);
  float abs_targetL = abs(targetL);
  float abs_currentL = abs(deltaL);
  float errorL = abs_targetL - abs_currentL;
  errorL_integral = constrain(errorL_integral + errorL, -200, 200);
  float derivL = errorL - last_errorL;
  last_errorL = errorL;
  float outputL = Kp * errorL + Ki * errorL_integral + Kd * derivL;
  int pwmL = constrain((int)outputL, 0, 255);

  if (dirLeftForward) {
    digitalWrite(IN1, HIGH);
    digitalWrite(IN2, LOW);
  } else {
    digitalWrite(IN1, LOW);
    digitalWrite(IN2, HIGH);
  }
  ledcWrite(pwmChannelA, pwmL);

  dirRightForward = (targetR >= 0);
  float abs_targetR = abs(targetR);
  float abs_currentR = abs(deltaR);
  float errorR = abs_targetR - abs_currentR;
  errorR_integral = constrain(errorR_integral + errorR, -200, 200);
  float derivR = errorR - last_errorR;
  last_errorR = errorR;
  float outputR = Kp * errorR + Ki * errorR_integral + Kd * derivR;
  int pwmR = constrain((int)outputR, 0, 255);

  if (dirRightForward) {
    digitalWrite(IN3, HIGH);
    digitalWrite(IN4, LOW);
  } else {
    digitalWrite(IN3, LOW);
    digitalWrite(IN4, HIGH);
  }
  ledcWrite(pwmChannelB, pwmR);
}

uint8_t calculateCRC8(const String &data) {
  uint8_t crc = 0x00;
  for (size_t i = 0; i < data.length(); i++) {
    crc ^= data[i];
    for (uint8_t j = 0; j < 8; j++) {
      if (crc & 0x80)
        crc = (crc << 1) ^ 0x07;
      else
        crc <<= 1;
    }
  }
  return crc;
}

uint8_t calculateCRC8(const char *data) {
  uint8_t crc = 0x00;
  while (*data) {
    crc ^= *data++;
    for (uint8_t j = 0; j < 8; j++) {
      if (crc & 0x80)
        crc = (crc << 1) ^ 0x07;
      else
        crc <<= 1;
    }
  }
  return crc;
}

void handlePiCommand(String cmd) {
  cmd.trim();
  int starIndex = cmd.lastIndexOf('*');
  if (starIndex != -1) {
    String payload = cmd.substring(0, starIndex);
    String crcHexStr = cmd.substring(starIndex + 1);
    uint8_t receivedCrc = (uint8_t)strtol(crcHexStr.c_str(), NULL, 16);
    if (receivedCrc != calculateCRC8(payload)) {
      logToPi("CRC Error! Dropped: " + cmd);
      return;
    }
    cmd = payload;
  }

  if (cmd.startsWith("SPEED:")) {
    int firstColon = cmd.indexOf(':');
    int secondColon = cmd.indexOf(':', firstColon + 1);
    if (firstColon != -1 && secondColon != -1) {
      if (xSemaphoreTake(dataMutex, portMAX_DELAY)) {
        shared_targetSpeedL =
            cmd.substring(firstColon + 1, secondColon).toFloat();
        shared_targetSpeedR = cmd.substring(secondColon + 1).toFloat();
        xSemaphoreGive(dataMutex);
      }
    }
  } else if (cmd.startsWith("BLE:ON:")) {
    int lastColon = cmd.lastIndexOf(':');
    if (lastColon != -1) {
      localToken = cmd.substring(lastColon + 1);
      localToken.trim();
    }
    BLEDevice::startAdvertising();
  } else if (cmd == "BLE:OFF") {
    BLEDevice::getAdvertising()->stop();
  } else if (cmd.startsWith("BUZZER:")) {
    int lastColon = cmd.lastIndexOf(':');
    if (lastColon != -1) {
      int beeps = cmd.substring(lastColon + 1).toInt();
      triggerBuzzer(beeps);
    }
  } else if (cmd == "BOX:CHECK") {
    bool isBoxEmpty = (digitalRead(LIMIT_SWITCH_PIN) == HIGH);
    if (isBoxEmpty)
      Serial.println("BOX:EMPTY");
    else
      Serial.println("BOX:FULL");
  } else if (cmd == "SYS:CHECK") {
    if (sysReady)
      Serial.println("SYS:READY");
    else
      Serial.println("SYS:ERROR:" + sysErrorMsg);
  }
}

// ==========================================
// 6. FREERTOS TASKS
// ==========================================

// TASK 1: Comms & Telemetry (Core 0)
void TaskComms(void *pvParameters) {
  unsigned long lastTelemetryTime = 0;
  String rxStr = "";

  for (;;) {
    // 1. Nhận UART từ Pi
    while (Serial.available() > 0) {
      char c = Serial.read();
      if (c == '\n') {
        handlePiCommand(rxStr);
        rxStr = "";
      } else if (c != '\r') {
        rxStr += c;
      }
    }

    // 2. Gửi Telemetry (mỗi 50ms)
    unsigned long currentMillis = millis();
    if (sysReady && (currentMillis - lastTelemetryTime >= 50)) {
      lastTelemetryTime = currentMillis;

      long encL, encR;
      float y, gz_d, batt;
      int16_t a_x, a_y, a_z, g_x, g_y, g_z;
      uint16_t t_dist;

      if (xSemaphoreTake(dataMutex, portMAX_DELAY)) {
        encL = shared_encLeftCount;
        encR = shared_encRightCount;
        y = shared_yaw;
        a_x = shared_ax;
        a_y = shared_ay;
        a_z = shared_az;
        g_x = shared_gx;
        g_y = shared_gy;
        g_z = shared_gz;
        gz_d = shared_gz_deg;
        batt = shared_batteryPercentage;
        t_dist = shared_tofDist;
        xSemaphoreGive(dataMutex);
      }

      char txBuf[128];
      snprintf(txBuf, sizeof(txBuf),
               "ODO:%ld:%ld:%.2f:%d:%d:%d:%d:%d:%d:%.2f:%.1f:%d", encL, encR, y,
               a_x, a_y, a_z, g_x, g_y, g_z, gz_d, batt, t_dist);

      uint8_t crc = calculateCRC8(txBuf);
      Serial.print(txBuf);
      Serial.print("*");
      if (crc < 0x10)
        Serial.print("0");
      Serial.println(crc, HEX);
    }

    vTaskDelay(10 / portTICK_PERIOD_MS); // Nhường CPU Core 0 cho Wi-Fi/BLE
  }
}

// TASK 2: Real-time Control & Sensors (Core 1)
void TaskControl(void *pvParameters) {
  unsigned long lastImuTime = millis();
  unsigned long lastPidTime = millis();
  unsigned long lastBatteryTime = millis();
  unsigned long lastServoStepTime = millis();
  bool localObstacleDetected = false;

  for (;;) {
    unsigned long currentMillis = millis();

    // 0. Nếu phần cứng lỗi, dừng thuật toán
    if (!sysReady) {
      vTaskDelay(500 / portTICK_PERIOD_MS);
      continue;
    }

    // 1. ToF Front
    if (tofFrontReady) {
      VL53L0X_RangingMeasurementData_t measure;
      loxFront.rangingTest(&measure, false);
      // Khi cất vật ra đột ngột, cảm biến có thể bị nhiễu pha (Phase Fail) hoặc
      // Signal Fail dẫn đến RangeStatus != 4 nhưng RangeMilliMeter lại bằng 0
      // hoặc rác. Do đó, ta chỉ tin tưởng nếu dist > 20mm (giới hạn vật lý tối
      // thiểu) và < 8000mm
      if (measure.RangeStatus != 4 && measure.RangeMilliMeter > 20 &&
          measure.RangeMilliMeter < 8000) {
        uint16_t dist = measure.RangeMilliMeter;

        if (xSemaphoreTake(dataMutex, portMAX_DELAY)) {
          shared_tofDist = dist;
          xSemaphoreGive(dataMutex);
        }

        if (dist < 300 && !localObstacleDetected) {
          localObstacleDetected = true;
          Serial.println("WARN:OBSTACLE");
          logToPi("CRITICAL: Obstacle detected!");
        } else if (dist > 500 && localObstacleDetected) {
          localObstacleDetected = false;
          Serial.println("WARN:CLEAR");
        }
      } else {
        // Out of range hoặc lỗi (dist = 0 do cất vật đi đột ngột) -> Xóa trạng
        // thái cản
        if (xSemaphoreTake(dataMutex, portMAX_DELAY)) {
          shared_tofDist = 9999;
          xSemaphoreGive(dataMutex);
        }
        if (localObstacleDetected) {
          localObstacleDetected = false;
          Serial.println("WARN:CLEAR");
        }
      }
    }

    // 2. MPU6050 & Compass 0x2C
    if (mpuReady) {
      Wire.beginTransmission(MPU6050_ADDR);
      Wire.write(0x3B);
      Wire.endTransmission(false);
      Wire.requestFrom((uint8_t)MPU6050_ADDR, (uint8_t)14);

      if (Wire.available() == 14) {
        int16_t ax = (Wire.read() << 8 | Wire.read());
        int16_t ay = (Wire.read() << 8 | Wire.read());
        int16_t az = (Wire.read() << 8 | Wire.read());
        Wire.read();
        Wire.read(); // Temp
        int16_t gx = (Wire.read() << 8 | Wire.read());
        int16_t gy = (Wire.read() << 8 | Wire.read());
        int16_t gz = (Wire.read() << 8 | Wire.read());

        float ax_g = ax / 16384.0f;
        float ay_g = ay / 16384.0f;
        float az_g = az / 16384.0f;
        float gx_dps = gx / 131.0f;
        float gy_dps = gy / 131.0f;
        float gz_dps = gz / 131.0f;

        float mx = 0, my = 0, mz = 0;

        if (compassReady) {
          Wire.beginTransmission(COMPASS_ADDR);
          Wire.write(0x00);
          Wire.endTransmission(false);
          Wire.requestFrom((uint8_t)COMPASS_ADDR, (uint8_t)6);
          if (Wire.available() == 6) {
            // LMC5883 / QMC5883L assumption: LSB first
            int16_t m_x = (Wire.read() | (Wire.read() << 8));
            int16_t m_y = (Wire.read() | (Wire.read() << 8));
            int16_t m_z = (Wire.read() | (Wire.read() << 8));
            mx = m_x;
            my = m_y;
            mz = m_z;
          }
        }

        filter.update(gx_dps, gy_dps, gz_dps, ax_g, ay_g, az_g, mx, my, mz);

        if (xSemaphoreTake(dataMutex, portMAX_DELAY)) {
          shared_yaw = filter.getYaw();
          shared_ax = ax;
          shared_ay = ay;
          shared_az = az;
          shared_gx = gx;
          shared_gy = gy;
          shared_gz = gz;
          xSemaphoreGive(dataMutex);
        }
      }
    }

    // 3. Servo Sweep & Box Logic
    if (xSemaphoreTake(dataMutex, portMAX_DELAY)) {
      if (currentServoAngle != targetServoAngle &&
          (currentMillis - lastServoStepTime >= 5)) {
        lastServoStepTime = currentMillis;
        if (currentServoAngle < targetServoAngle)
          currentServoAngle++;
        else
          currentServoAngle--;
        doorServo.write(currentServoAngle);
      }

      // Box Logic: Đóng nắp sau 8s nếu trạng thái hàng hóa thay đổi (người gửi
      // bỏ hàng vào, hoặc người nhận lấy hàng ra)
      if (doorState == DOOR_OPEN) {
        bool currentBoxState = (digitalRead(LIMIT_SWITCH_PIN) == LOW);
        if (currentBoxState != initialBoxState) {
          doorState = DOOR_WAITING_TO_CLOSE;
          boxFullTime = currentMillis;
        }
      } else if (doorState == DOOR_WAITING_TO_CLOSE) {
        bool currentBoxState = (digitalRead(LIMIT_SWITCH_PIN) == LOW);
        if (currentBoxState == initialBoxState) {
          // Nếu trạng thái quay lại như cũ (vd bỏ vào xong lại nhấc ra), hủy
          // đếm giờ
          doorState = DOOR_OPEN;
        } else if (currentMillis - boxFullTime >= 8000) {
          // Đã giữ trạng thái mới đủ 8 giây
          targetServoAngle = 100; // Đóng nắp
          doorState = DOOR_CLOSED;
          Serial.println("DOOR:CLOSED");
          triggerBuzzer(1); // 1 beep for door closed
        }
      }

      xSemaphoreGive(dataMutex);
    }

    // 3.5. Buzzer Logic
    if (localObstacleDetected) {
      digitalWrite(BUZZER_PIN, HIGH);
    } else if (buzzerBeepsRemaining > 0) {
      if (currentMillis - buzzerLastToggle >= 100) {
        buzzerLastToggle = currentMillis;
        buzzerActiveState = !buzzerActiveState;
        digitalWrite(BUZZER_PIN, buzzerActiveState ? HIGH : LOW);
        buzzerBeepsRemaining--;
      }
    } else {
      digitalWrite(BUZZER_PIN, LOW);
    }

    // 4. Battery
    if (currentMillis - lastBatteryTime >= 1000) {
      lastBatteryTime = currentMillis;
      float voltage = (analogRead(BATTERY_ADC_PIN) / 4095.0) * 3.3;
      float batt = ((voltage - 2.8) / (3.3 - 2.8)) * 100.0;
      if (xSemaphoreTake(dataMutex, portMAX_DELAY)) {
        shared_batteryPercentage = constrain(batt, 0.0, 100.0);
        xSemaphoreGive(dataMutex);
      }
    }

    // 5. PID Control
    if (currentMillis - lastPidTime >= 50) {
      lastPidTime = currentMillis;
      noInterrupts();
      long currentEncL = encLeftCount;
      long currentEncR = encRightCount;
      interrupts();

      long deltaL = currentEncL - prevEncL;
      long deltaR = currentEncR - prevEncR;
      prevEncL = currentEncL;
      prevEncR = currentEncR;

      float tL = 0, tR = 0;
      if (xSemaphoreTake(dataMutex, portMAX_DELAY)) {
        tL = shared_targetSpeedL;
        tR = shared_targetSpeedR;

        shared_encLeftCount = currentEncL;
        shared_encRightCount = currentEncR;
        // Các biến ax,ay,az,gx,gy,gz, và yaw đã được update liên tục ở phần (2.
        // MPU9250) phía trên nên không cần gán đè ở đây nữa.
        shared_gz_deg = (mpuReady) ? shared_gz : 0.0;
        shared_isObstacleDetected = localObstacleDetected;
        xSemaphoreGive(dataMutex);
      }

      runPID(deltaL, deltaR, tL, tR, localObstacleDetected);
    }

    // Core 1 Delay (Run at max 200Hz = 5ms loop to ensure ToF/IMU are read
    // fast)
    vTaskDelay(5 / portTICK_PERIOD_MS);
  }
}

// ==========================================
// 7. SETUP
// ==========================================
void setup() {
  Serial.begin(115200);
  dataMutex = xSemaphoreCreateMutex();
  Wire.begin(I2C_SDA, I2C_SCL);
  Wire.setTimeOut(
      150); // Tăng lên 150ms để ToF VL53L0X kịp phản hồi (20ms là quá nhanh)

  // 1. Khởi tạo MPU6050 và La bàn (0x2C)
  Wire.beginTransmission(MPU6050_ADDR);
  if (Wire.endTransmission() == 0) {
    mpuReady = true;
    writeI2C(MPU6050_ADDR, 0x6B, 0x00); // Wake up
    delay(10);
    writeI2C(MPU6050_ADDR, 0x1C, 0x00); // Accel config: +/- 2g
    writeI2C(MPU6050_ADDR, 0x1B, 0x00); // Gyro config: +/- 250 deg/s
    writeI2C(MPU6050_ADDR, 0x37, 0x02); // Enable I2C Bypass
    delay(10);
    Serial.println("MPU6050 READY and I2C Bypass Enabled.");

    Wire.beginTransmission(COMPASS_ADDR);
    if (Wire.endTransmission() == 0) {
      compassReady = true;
      writeI2C(COMPASS_ADDR, 0x0B, 0x01); // Set/Reset Period
      writeI2C(COMPASS_ADDR, 0x09, 0x1D); // Control: Continuous Mode
      Serial.println("Compass 0x2C READY.");
    } else {
      Serial.println("WARN: Compass 0x2C NOT FOUND!");
    }
  } else {
    Serial.println("WARN: MPU6050 NOT FOUND at 0x68!");
  }

  filter.begin(200.0); // Tần số vòng lặp TaskControl ~200Hz

  // 2. Khởi tạo VL53L0X sau
  if (loxFront.begin()) {
    tofFrontReady = true;
  } else {
    tofFrontReady = false;
    Serial.println("WARN: ToF VL53L0X not found! Disabling ToF features.");
  }

  // Tạm thời KHÔNG bắt lỗi ToF để cho phép xe chạy dù không có ToF
  // if (!tofFrontReady) sysErrorMsg += "ToF_Front ";

  // if (!mpuReady) sysErrorMsg += "MPU6050 ";
  if (sysErrorMsg == "")
    sysReady = true;

  /*
  if (mpuReady) {
    Serial.println("Calibrating MPU9250 (Giữ xe nằm im trong 3 giây)...");
    mpu.calibrateAccelGyro();
    Serial.println("Calibration Done!");
  }
  */

  pinMode(ENC_L_PIN, INPUT_PULLUP);
  pinMode(ENC_R_PIN, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(ENC_L_PIN), isrLeftEncoder, RISING);
  attachInterrupt(digitalPinToInterrupt(ENC_R_PIN), isrRightEncoder, RISING);

  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);
  ledcSetup(pwmChannelA, freq, resolution);
  ledcSetup(pwmChannelB, freq, resolution);
  ledcAttachPin(ENA, pwmChannelA);
  ledcAttachPin(ENB, pwmChannelB);

  pinMode(LIMIT_SWITCH_PIN, INPUT_PULLUP);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  doorServo.setPeriodHertz(50);
  doorServo.attach(SERVO_PIN, 500, 2400);
  doorServo.write(currentServoAngle);

  analogSetPinAttenuation(BATTERY_ADC_PIN, ADC_11db);

  BLEDevice::init("AGV_DELIVERY_01");
  BLEDevice::setMTU(512); // explicitly allow large payload
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  BLEService *pService = pServer->createService(SERVICE_UUID);
  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
      CHARACTERISTIC_UUID_RX,
      BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
  pRxCharacteristic->addDescriptor(new BLE2902());
  pRxCharacteristic->setCallbacks(new MyCallbacks());
  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);

  xTaskCreatePinnedToCore(TaskComms, "TaskComms", 8192, NULL, 1, NULL, 0);
  xTaskCreatePinnedToCore(TaskControl, "TaskControl", 8192, NULL, 2, NULL, 1);
}

void loop() {
  // Bỏ trống vì FreeRTOS đã chiếm quyền điều khiển
  vTaskDelete(NULL);
}
