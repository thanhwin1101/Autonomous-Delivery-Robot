import serial
import threading
import time
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def calculate_crc8(data: str) -> int:
    crc = 0x00
    for char in data:
        crc ^= ord(char)
        for _ in range(8):
            if crc & 0x80:
                crc = ((crc << 1) ^ 0x07) & 0xFF
            else:
                crc = (crc << 1) & 0xFF
    return crc

class UartComm:
    def __init__(self, port='/dev/ttyACM0', baudrate=115200):
        self.port = port
        self.baudrate = baudrate
        self.ser = None
        self.running = False
        
        # Telemetry State
        self.ticks_l = 0
        self.ticks_r = 0
        self.yaw = 0.0
        self.obstacle_flag = False
        self.door_state = None  # None, "OPENED", or "LOCKED"
        self.box_state = None   # None, "EMPTY", or "FULL"
        self.battery_pct = 100.0
        self.tof_dist = 9999
        self.imu_data = {"ax": 0, "ay": 0, "az": 0, "gx": 0, "gy": 0, "gz": 0.0}
        self.on_log_callback = None
        self.sys_state = 'WAITING'
        
        self.lock = threading.Lock()

    def connect(self):
        try:
            self.ser = serial.Serial()
            self.ser.port = self.port
            self.ser.baudrate = self.baudrate
            self.ser.timeout = 1
            self.ser.open()
            
            # Ép buộc Reset ESP32 bằng tay (Rất quan trọng khi chạy ngầm bằng systemd)
            # ESP32 Normal Reset Sequence: DTR=False, RTS=True (EN=LOW) -> DTR=False, RTS=False (EN=HIGH, IO0=HIGH)
            self.ser.dtr = False
            self.ser.rts = True
            time.sleep(0.1)
            self.ser.dtr = False
            self.ser.rts = False
            
            # Đợi 1.5 giây để ESP32 khởi động xong (nếu bị reset khi cắm/mở port)
            # Tránh việc gửi lệnh rác làm ESP32 kẹt ở chế độ Bootloader
            time.sleep(1.5)
            
            self.running = True
            threading.Thread(target=self._receive_loop, daemon=True).start()
            logger.info(f"Connected to ESP32 on {self.port}")
            return True
        except Exception as e:
            logger.error(f"Failed to connect to ESP32: {e}")
            return False

    def disconnect(self):
        self.running = False
        if self.ser and self.ser.is_open:
            self.ser.close()

    def send_speed(self, v_left: float, v_right: float):
        """Sends target motor speed command to ESP32 (ticks per 50ms)"""
        self._write_with_crc(f"SPEED:{v_left:.2f}:{v_right:.2f}")

    def send_ble_on(self, token: str):
        """Sends command to enable BLE with the specified token"""
        self._write_with_crc(f"BLE:ON:{token}")

    def send_ble_off(self):
        """Sends command to disable BLE"""
        self._write_with_crc("BLE:OFF")

    def send_box_check(self):
        """Sends command to trigger box load check"""
        self._write_with_crc("BOX:CHECK")

    def send_buzzer(self, beeps: int):
        """Sends command to trigger buzzer"""
        self._write_with_crc(f"BUZZER:{beeps}")

    def send_sys_check(self):
        """Sends command to check hardware readiness"""
        self._write_with_crc("SYS:CHECK")

    def _write_with_crc(self, msg: str):
        crc = calculate_crc8(msg)
        full_msg = f"{msg}*{crc:02X}\n"
        if self.ser and self.ser.is_open:
            try:
                self.ser.write(full_msg.encode('utf-8'))
            except Exception as e:
                logger.error(f"Error sending UART data: {e}")

    def _receive_loop(self):
        while self.running:
            try:
                if self.ser and self.ser.is_open and self.ser.in_waiting > 0:
                    line = self.ser.readline().decode('utf-8', errors='replace').strip()
                    if not line:
                        continue
                    
                    self._parse_line(line)
            except Exception as e:
                logger.error(f"UART Receive Error: {e}")
                time.sleep(1)

    def _parse_line(self, line: str):
        if '*' in line:
            payload, crc_hex = line.rsplit('*', 1)
            try:
                received_crc = int(crc_hex, 16)
                calc_crc = calculate_crc8(payload)
                if received_crc != calc_crc:
                    logger.warning(f"CRC Error! Dropped: {line}")
                    return
                line = payload
            except ValueError:
                logger.warning(f"Invalid CRC format! Dropped: {line}")
                return

        parts = line.split(":")
        if not parts:
            return
        
        prefix = parts[0]
        with self.lock:
            if prefix == "ODO":
                try:
                    self.ticks_l = int(parts[1])
                    self.ticks_r = int(parts[2])
                    self.yaw = float(parts[3])
                    self.imu_data["ax"] = int(parts[4])
                    self.imu_data["ay"] = int(parts[5])
                    self.imu_data["az"] = int(parts[6])
                    self.imu_data["gx"] = int(parts[7])
                    self.imu_data["gy"] = int(parts[8])
                    self.imu_data["gz"] = float(parts[9]) / 131.0
                    
                    if len(parts) >= 13:
                        # 13 parts: 0-12
                        self.imu_data["gz_deg"] = float(parts[10])
                        self.battery_pct = float(parts[11])
                        self.tof_dist = int(parts[12])
                    elif len(parts) == 12:
                        # 12 parts: 0-11
                        self.imu_data["gz_deg"] = float(parts[10])
                        self.battery_pct = float(parts[11])
                    elif len(parts) == 11:
                        # 11 parts: 0-10
                        self.battery_pct = float(parts[10])
                except ValueError as e:
                    logger.debug(f"Failed to parse ODO line: {line} - {e}")
            elif prefix == "DOOR" and len(parts) >= 2:
                self.door_state = parts[1]  # "OPENED" or "LOCKED"
                logger.info(f"Door state updated: {self.door_state}")
            elif prefix == "WARN" and len(parts) >= 2:
                if parts[1] == "OBSTACLE":
                    self.obstacle_flag = True
                elif parts[1] == "CLEAR":
                    self.obstacle_flag = False
                logger.warning(f"Obstacle warning updated: {self.obstacle_flag}")
            elif prefix == "BOX" and len(parts) >= 2:
                self.box_state = parts[1]  # "EMPTY" or "FULL"
                logger.info(f"Box state updated: {self.box_state}")
            elif prefix == "LOG" and len(parts) >= 2:
                log_msg = ":".join(parts[1:])
                logger.info(f"[ESP32] {log_msg}")
                if self.on_log_callback:
                    try:
                        self.on_log_callback(log_msg)
                    except Exception as e:
                        pass
            elif prefix == "SYS" and len(parts) >= 2:
                if parts[1] == "READY":
                    self.sys_state = "READY"
                elif parts[1] == "ERROR" and len(parts) >= 3:
                    self.sys_state = "ERROR:" + parts[2]
                logger.info(f"System State updated: {self.sys_state}")
            else:
                logger.warning(f"[UNHANDLED ESP32 MSG]: {line}")

    def get_telemetry(self):
        with self.lock:
            # Copy and reset temporary trigger states
            door = self.door_state
            box = self.box_state
            self.door_state = None
            self.box_state = None
            
            return {
                "ticks_l": self.ticks_l,
                "ticks_r": self.ticks_r,
                "yaw": self.yaw,
                "obstacle": self.obstacle_flag,
                "door": door,
                "box": box,
                "ax": self.imu_data["ax"],
                "ay": self.imu_data["ay"],
                "az": self.imu_data["az"],
                "gx": self.imu_data["gx"],
                "gy": self.imu_data["gy"],
                "gz": self.imu_data["gz"],
                "battery": self.battery_pct,
                "tof_dist": self.tof_dist
            }
