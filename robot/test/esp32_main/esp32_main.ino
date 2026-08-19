#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_VL53L0X.h>

#define I2C_SDA 21
#define I2C_SCL 22

// Định nghĩa chân điều khiển XSHUT của cảm biến ToF
// (Hãy thay số 4 bằng chân GPIO thực tế bạn đang kết nối trên ESP32/Arduino)
#define XSHUT_FRONT 4 

Adafruit_VL53L0X lox = Adafruit_VL53L0X();
bool isToFReady = false;

void setup() {
  Serial.begin(115200);
  while (!Serial);
  
  Serial.println("\n\n=========================================");
  Serial.println("  CHUẨN ĐOÁN LỖI I2C & RESET TOF QUA XSHUT");
  Serial.println("=========================================\n");

  // ================= HARDWARE RESET TOF =================
  Serial.println("Đang thực hiện Reset cứng cảm biến ToF qua chân XSHUT...");
  pinMode(XSHUT_FRONT, OUTPUT);
  digitalWrite(XSHUT_FRONT, LOW);  // Tắt cảm biến ToF (đưa vào chế độ Standby)
  delay(20);
  digitalWrite(XSHUT_FRONT, HIGH); // Bật lại cảm biến
  delay(50);                       // Chờ cảm biến khởi động lại hoàn toàn
  Serial.println("[OK] Đã kích hoạt lại chân XSHUT!");
  // =====================================================

  Wire.begin(I2C_SDA, I2C_SCL);
  Wire.setTimeOut(20); 

  // 1. Thử đánh thức MPU6050 và Bật chế độ Bypass để tìm La bàn
  Wire.beginTransmission(0x68);
  if (Wire.endTransmission() == 0) {
    Serial.println("[OK] Đã tìm thấy MPU6050 tại 0x68! Đang bật chế độ Bypass...");
    Wire.beginTransmission(0x68);
    Wire.write(0x6B);
    Wire.write(0x00);
    Wire.endTransmission();
    delay(10);
    
    Wire.beginTransmission(0x68);
    Wire.write(0x37);
    Wire.write(0x02);
    Wire.endTransmission();
    delay(10);
  } else {
    Serial.println("[LỖI] KHÔNG TÌM THẤY MPU6050 (0x68) -> Kiểm tra lại dây!");
  }

  // 2. Khởi động cảm biến VL53L0X (ToF) sau khi đã được reset cứng
  Serial.println("\nĐang kết nối với cảm biến ToF VL53L0X (0x29)...");
  if (lox.begin()) {
    Serial.println("[OK] Đã kết nối thành công với VL53L0X!");
    isToFReady = true;
  } else {
    Serial.println("[LỖI] Không thể khởi tạo VL53L0X! Hãy chắc chắn chân XSHUT đã được kéo lên mức HIGH.");
  }

  Serial.println("\nĐang quét toàn bộ địa chỉ I2C...\n");
}

void loop() {
  byte error, address;
  int nDevices = 0;

  Serial.println("--- Bắt đầu quét mạng I2C ---");

  for(address = 1; address < 127; address++) {
    Wire.beginTransmission(address);
    error = Wire.endTransmission();

    if (error == 0) {
      Serial.print("-> Đã tìm thấy thiết bị I2C tại địa chỉ 0x");
      if (address < 16) Serial.print("0");
      Serial.print(address, HEX);

      if (address == 0x68) {
        Serial.println("  (MPU6050 - Gia tốc & Góc quay thuộc GY-87)");
      } 
      else if (address == 0x29) {
        Serial.println("  (VL53L0X - Cảm biến khoảng cách ToF)");
      } 
      else if (address == 0x1E || address == 0x0D || address == 0x2C) {
        Serial.println("  (La bàn từ trường HMC5883L/QMC5883L thuộc GY-87)");
      } 
      else if (address == 0x77) {
        Serial.println("  (BMP180 - Cảm biến áp suất/nhiệt độ thuộc GY-87)");
      } 
      else {
        Serial.println("  (Thiết bị lạ/Không xác định)");
      }

      nDevices++;
    }
    else if (error == 4) {
      Serial.print("-> Lỗi không xác định tại địa chỉ 0x");
      if (address < 16) Serial.print("0");
      Serial.println(address, HEX);
    }
  }

  if (nDevices == 0) {
    Serial.println("\n[CẢNH BÁO ĐỎ] Không tìm thấy BẤT KỲ thiết bị I2C nào!");
  } else {
    Serial.println("\n[THÔNG BÁO] Quét xong. Tổng số thiết bị: " + String(nDevices));
  }
  
  // ==================== PHẦN ĐO KHOẢNG CÁCH ====================
  if (isToFReady) {
    Serial.println("\n--- Đọc dữ liệu từ cảm biến ToF VL53L0X ---");
    
    VL53L0X_RangingMeasurementData_t measure;
    lox.rangingTest(&measure, false); 

    if (measure.RangeStatus != 4) { 
      Serial.print("   Khoảng cách đo được: ");
      Serial.print(measure.RangeMilliMeter);
      Serial.println(" mm");
    } else {
      Serial.println("   [Cảnh báo] Ngoài phạm vi đo (Vượt quá ~2 mét hoặc bị che khuất)");
    }
  } else {
    Serial.println("\n[LỖI] Không thể đo khoảng cách vì ToF chưa khởi động được!");
  }
  // =============================================================

  Serial.println("-------------------------------------------------\n");
  delay(3000); 
}