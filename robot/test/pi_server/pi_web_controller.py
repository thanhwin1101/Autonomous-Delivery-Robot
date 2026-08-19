import sys
import os
import time
import threading
from flask import Flask, render_template_string, jsonify, request
from dotenv import load_dotenv

# Đảm bảo import được các thư viện UartComm, GpsNavigator từ pi_master
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'pi_master')))

try:
    from uart_comm import UartComm
    from gps_nav import GpsNavigator
except ImportError as e:
    print(f"Error importing modules: {e}")
    sys.exit(1)

app = Flask(__name__)

# Global variables
uart = None
gps = None
v_base = 15.0  # Tốc độ cơ bản để tune
current_speed_l = 0.0
current_speed_r = 0.0

HTML_PAGE = """
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>AGV Web Dashboard Test</title>
    <style>
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #121212; color: #ffffff; text-align: center; margin: 0; padding: 20px; }
        .dashboard-container { display: flex; flex-wrap: wrap; justify-content: center; gap: 20px; max-width: 1000px; margin: auto; }
        .panel { background-color: #1e1e1e; padding: 20px; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.3); flex: 1; min-width: 300px; }
        h1, h2, h3 { color: #03dac6; }
        .data-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #333; font-size: 18px; }
        .data-val { font-weight: bold; color: #bb86fc; }
        .btn { padding: 20px; font-size: 18px; margin: 5px; cursor: pointer; border: none; border-radius: 8px; color: #fff; font-weight: bold; transition: 0.2s; user-select: none; }
        .btn:active { transform: scale(0.95); }
        .btn-dir { background-color: #3700b3; width: 100%; height: 100%; }
        .btn-stop { background-color: #cf6679; width: 100%; height: 100%; }
        .btn-hw { background-color: #03dac6; color: #000; width: 100%; margin: 10px 0; }
        .control-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; max-width: 300px; margin: 20px auto; aspect-ratio: 1; }
        .slider-container { margin: 20px 0; }
        .slider { width: 100%; height: 25px; }
        .alert { color: #cf6679; font-weight: bold; font-size: 20px; text-transform: uppercase; display: none; background: rgba(207, 102, 121, 0.2); padding: 10px; border-radius: 8px; margin-bottom: 20px; }
    </style>
</head>
<body>
    <h1>AGV TEST DASHBOARD</h1>
    <div id="obstacle-alert" class="alert">⚠️ PHÁT HIỆN VẬT CẢN - XE ĐÃ DỪNG ⚠️</div>
    
    <div class="dashboard-container">
        <!-- Telemetry Panel -->
        <div class="panel">
            <h2>Cảm Biến (Telemetry)</h2>
            <div class="data-row"><span>GPS (Lat, Lon):</span> <span id="gps-val" class="data-val">0.0, 0.0</span></div>
            <div class="data-row"><span>Pin:</span> <span id="batt-val" class="data-val">100%</span></div>
            <div class="data-row"><span>Cửa hộp hàng:</span> <span id="door-val" class="data-val">N/A</span></div>
            <div class="data-row"><span>Cảm biến hàng:</span> <span id="box-val" class="data-val">N/A</span></div>
            <div class="data-row"><span>Khoảng cách ToF:</span> <span id="tof-val" class="data-val">9999 mm</span></div>
            <div class="data-row"><span>Hướng (Yaw):</span> <span id="yaw-val" class="data-val">0.0°</span></div>
            <div class="data-row"><span>Tốc độ (L - R):</span> <span id="speed-val" class="data-val">0.0 - 0.0</span></div>
        </div>

        <!-- Control Panel -->
        <div class="panel">
            <h2>Điều Khiển & Tuning</h2>
            <div class="slider-container">
                <label>Tốc độ chạy Test (v_base): <span id="vbase-label" class="data-val">15.0</span></label><br>
                <input type="range" min="5" max="30" value="15" class="slider" id="vbase-slider" onchange="tuneSpeed(this.value)">
            </div>
            
            <div class="control-grid">
                <div></div>
                <button class="btn btn-dir" onmousedown="sendCmd('F')" onmouseup="sendCmd('S')" ontouchstart="sendCmd('F')" ontouchend="sendCmd('S')">Tiến</button>
                <div></div>
                <button class="btn btn-dir" onmousedown="sendCmd('L')" onmouseup="sendCmd('S')" ontouchstart="sendCmd('L')" ontouchend="sendCmd('S')">Trái</button>
                <button class="btn btn-stop" onmousedown="sendCmd('S')" ontouchstart="sendCmd('S')">DỪNG</button>
                <button class="btn btn-dir" onmousedown="sendCmd('R')" onmouseup="sendCmd('S')" ontouchstart="sendCmd('R')" ontouchend="sendCmd('S')">Phải</button>
                <div></div>
                <button class="btn btn-dir" onmousedown="sendCmd('B')" onmouseup="sendCmd('S')" ontouchstart="sendCmd('B')" ontouchend="sendCmd('S')">Lùi</button>
                <div></div>
            </div>
            <p style="font-size: 14px; color: #888;">* Chạm & Giữ nút để chạy, nhả tay để dừng tự động.</p>
        </div>

        <!-- Hardware Test Panel -->
        <div class="panel">
            <h2>Chức Năng Hộp Hàng</h2>
            <button class="btn btn-hw" onclick="sendCmd('BLE_ON')">MỞ HỘP (Bật BLE)</button>
            <button class="btn btn-hw" style="background-color: #3700b3; color: white;" onclick="sendCmd('BLE_OFF')">ĐÓNG HỘP (Tắt BLE)</button>
            <button class="btn btn-hw" style="background-color: #bb86fc; color: black;" onclick="sendCmd('BOX_CHK')">Kiểm tra cảm biến hành trình</button>
        </div>
    </div>

    <script>
        function sendCmd(cmd) {
            fetch('/api/cmd?c=' + cmd);
        }

        function tuneSpeed(val) {
            document.getElementById('vbase-label').innerText = val;
            fetch('/api/tune?vbase=' + val);
        }

        // Tự động kéo dữ liệu mỗi 500ms
        setInterval(() => {
            fetch('/api/telemetry')
                .then(res => res.json())
                .then(data => {
                    document.getElementById('gps-val').innerText = `${data.gps_lat.toFixed(5)}, ${data.gps_lon.toFixed(5)}`;
                    document.getElementById('batt-val').innerText = `${data.battery}%`;
                    document.getElementById('door-val').innerText = data.door;
                    document.getElementById('box-val').innerText = data.box;
                    document.getElementById('tof-val').innerText = `${data.tof_dist} mm`;
                    document.getElementById('yaw-val').innerText = `${data.yaw.toFixed(2)}°`;
                    document.getElementById('speed-val').innerText = `${data.speed_l} - ${data.speed_r}`;
                    
                    if (data.obstacle) {
                        document.getElementById('obstacle-alert').style.display = 'block';
                    } else {
                        document.getElementById('obstacle-alert').style.display = 'none';
                    }
                })
                .catch(err => {});
        }, 500); 
    </script>
</body>
</html>
"""

@app.route('/')
def index():
    return render_template_string(HTML_PAGE)

@app.route('/api/telemetry')
def get_telemetry():
    global uart, gps, current_speed_l, current_speed_r
    if not uart or not gps:
        return jsonify({"error": "Hardware not connected"}), 500
        
    tel = uart.get_telemetry()
    return jsonify({
        "gps_lat": gps.current_lat,
        "gps_lon": gps.current_lon,
        "battery": tel.get('battery', 0),
        "door": tel.get('door', 'N/A'),
        "box": tel.get('box', 'N/A'),
        "tof_dist": tel.get('tof_dist', 9999),
        "yaw": tel.get('yaw', 0.0),
        "obstacle": tel.get('obstacle', False),
        "speed_l": current_speed_l,
        "speed_r": current_speed_r
    })

@app.route('/api/tune')
def tune_params():
    global v_base
    val = request.args.get('vbase')
    if val:
        try:
            v_base = float(val)
            print(f"[TUNING] Đã cập nhật tốc độ chạy thử: {v_base}")
        except ValueError:
            pass
    return jsonify({"status": "ok", "v_base": v_base})

@app.route('/api/cmd')
def handle_command():
    global uart, v_base, current_speed_l, current_speed_r
    cmd = request.args.get('c')
    if not cmd or not uart:
        return jsonify({"status": "ignored"})
        
    tel = uart.get_telemetry()
    obs = tel.get('obstacle', False)

    # Auto-brake system
    if obs and cmd in ['F', 'L', 'R']:
        print("[ALARM] Vướng vật cản, tự động khóa lệnh đi tới!")
        uart.send_speed(0, 0)
        current_speed_l = 0
        current_speed_r = 0
        return jsonify({"status": "blocked"})

    if cmd == 'F':
        current_speed_l = v_base
        current_speed_r = v_base
        uart.send_speed(current_speed_l, current_speed_r)
    elif cmd == 'B':
        current_speed_l = -v_base
        current_speed_r = -v_base
        uart.send_speed(current_speed_l, current_speed_r)
    elif cmd == 'L':
        current_speed_l = -v_base
        current_speed_r = v_base
        uart.send_speed(current_speed_l, current_speed_r)
    elif cmd == 'R':
        current_speed_l = v_base
        current_speed_r = -v_base
        uart.send_speed(current_speed_l, current_speed_r)
    elif cmd == 'S':
        current_speed_l = 0.0
        current_speed_r = 0.0
        uart.send_speed(0, 0)
    elif cmd == 'BLE_ON':
        uart.send_ble_on("TEST_WEB")
    elif cmd == 'BLE_OFF':
        uart.send_ble_off()
    elif cmd == 'BOX_CHK':
        uart.send_box_check()
        
    print(f"[CMD] Nhận lệnh từ Web: {cmd}")
    return jsonify({"status": "ok"})

def setup_hardware():
    global uart, gps
    load_dotenv(os.path.join(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'pi_master')), '.env'))
    
    esp32_port = os.getenv('PORT_ESP32', '/dev/ttyUSB0')
    gps_port = os.getenv('PORT_GPS', '/dev/ttyUSB1')

    print("==================================")
    print("  KHỞI ĐỘNG AGV WEB DASHBOARD")
    print("==================================")
    
    uart = UartComm(port=esp32_port, baudrate=115200)
    gps = GpsNavigator(port=gps_port, baudrate=115200)
    
    if not uart.connect():
        print(f"ERROR: Không thể kết nối tới ESP32 tại {esp32_port}!")
    else:
        print("Connected to ESP32.")
        
    gps.start_gps()
    print("GPS Tracker started.")

if __name__ == '__main__':
    setup_hardware()
    # Chạy server ở cổng 5000, lắng nghe tất cả IP mạng LAN
    print("\n=> Mở trình duyệt trên điện thoại/PC và truy cập: http://<IP_CỦA_PI>:5000\n")
    app.run(host='0.0.0.0', port=5000, debug=False, use_reloader=False)
