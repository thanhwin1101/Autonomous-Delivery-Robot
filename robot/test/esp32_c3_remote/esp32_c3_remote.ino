#include <Arduino.h>
#include <WiFi.h> 
#include <BLEDevice.h>

#define BUTTON_PIN 9 // Chân nối nút bấm trên ESP32 C3
#define WIFI_SSID "Ten_WiFi_Cua_Ban"
#define WIFI_PASS "Mat_Khau_WiFi"

// Trùng với UUID của mạch chính
static BLEUUID serviceUUID("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
static BLEUUID charUUID("beb5483e-36e1-4688-b7f5-ea07361b26a8");

static boolean doConnect = false;
static boolean connected = false;
static BLERemoteCharacteristic* pRemoteCharacteristic;
static BLEAdvertisedDevice* myDevice;

// Hàm bắt sóng BLE
class MyAdvertisedDeviceCallbacks: public BLEAdvertisedDeviceCallbacks {
  void onResult(BLEAdvertisedDevice advertisedDevice) {
    if (advertisedDevice.haveServiceUUID() && advertisedDevice.isAdvertisingService(serviceUUID)) {
      BLEDevice::getScan()->stop();
      myDevice = new BLEAdvertisedDevice(advertisedDevice);
      doConnect = true;
      Serial.println("Đã tìm thấy xe Tank! Đang tiến hành kết nối...");
    }
  }
};

bool connectToServer() {
  BLEClient*  pClient  = BLEDevice::createClient();
  pClient->connect(myDevice);
  BLERemoteService* pRemoteService = pClient->getService(serviceUUID);
  if (pRemoteService == nullptr) return false;
  
  pRemoteCharacteristic = pRemoteService->getCharacteristic(charUUID);
  if (pRemoteCharacteristic == nullptr) return false;
  
  connected = true;
  return true;
}

void setup() {
  Serial.begin(115200);
  // Cấu hình nút nhấn kéo lên (Pull-up)
  pinMode(BUTTON_PIN, INPUT_PULLUP);

  // Kết nối WiFi (Như yêu cầu "bắt được wifi")
  Serial.print("Đang kết nối WiFi");
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500); Serial.print(".");
  }
  Serial.println("\nWiFi đã kết nối thành công!");

  // Bắt đầu quét BLE
  BLEDevice::init("");
  BLEScan* pBLEScan = BLEDevice::getScan();
  pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks());
  pBLEScan->setInterval(1349);
  pBLEScan->setWindow(449);
  pBLEScan->setActiveScan(true);
  pBLEScan->start(0, false); // Quét liên tục
}

void loop() {
  if (doConnect) {
    if (connectToServer()) {
      Serial.println("Đã kết nối BLE thành công với xe Tank!");
    } else {
      Serial.println("Lỗi kết nối BLE.");
    }
    doConnect = false;
  }

  // Nếu đã kết nối, chờ nút bấm
  if (connected) {
    if (digitalRead(BUTTON_PIN) == LOW) { // Bấm nút
      String msg = "1";
      pRemoteCharacteristic->writeValue(msg.c_str(), msg.length());
      Serial.println("Đã bấm nút! Truyền tín hiệu bật đèn sang mạch chính...");
      delay(1000); // Chống dội nút (Debounce)
    }
  }
  delay(50);
}
