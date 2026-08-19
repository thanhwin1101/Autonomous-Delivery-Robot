import serial
import pynmea2
import threading
import time
import math
import requests
import os
import numpy as np
from dotenv import load_dotenv
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def latlon_to_xy(lat, lon, ref_lat, ref_lon):
    R = 6371000.0  # Earth's radius in meters
    lat_rad = math.radians(lat)
    lon_rad = math.radians(lon)
    ref_lat_rad = math.radians(ref_lat)
    ref_lon_rad = math.radians(ref_lon)
    
    x = R * (lon_rad - ref_lon_rad) * math.cos(ref_lat_rad)
    y = R * (lat_rad - ref_lat_rad)
    return x, y

def xy_to_latlon(x, y, ref_lat, ref_lon):
    R = 6371000.0
    ref_lat_rad = math.radians(ref_lat)
    lat_rad = (y / R) + ref_lat_rad
    lon_rad = (x / (R * math.cos(ref_lat_rad))) + math.radians(ref_lon)
    return math.degrees(lat_rad), math.degrees(lon_rad)

class GpsNavigator:
    def __init__(self, port='/dev/ttyUSB0', baudrate=115200):
        load_dotenv()
        self.port = port
        self.baudrate = baudrate
        self.ser = None
        self.running = False
        
        # Raw GPS coords
        self.current_lat = 0.0
        self.current_lon = 0.0
        self.gps_updated = False
        self.override_active = False
        
        self.maptiler_api_key = os.getenv('MAPTILER_API_KEY')
        
        # Reference coordinates for Local Cartesian Space (set on first GPS fix)
        self.ref_lat = None
        self.ref_lon = None
        
        # Path waypoints in Local Cartesian Coordinates (x, y)
        self.smoothed_waypoints = []
        self.current_waypoint_index = 0
        
        # EKF State: [x, y, theta, bias_gz]
        self.X = np.zeros((4, 1))
        # EKF Covariance
        self.P = np.eye(4) * 0.1
        self.P[3, 3] = 0.001 # Initial bias confidence
        
        # Localization state (Fused outputs for Pure Pursuit)
        self.x_est = 0.0
        self.y_est = 0.0
        self.theta_est = 0.0  # Radians, counter-clockwise from East
        
        # Encoder integration
        self.last_ticks_l = None
        self.last_ticks_r = None
        
        # EKF Timings & Cache
        self.last_ekf_time = time.time()
        self.prev_gps_xy = None
        
        # Calibration constants
        self.meters_per_tick = 0.002  # Tune based on wheel diameter & ticks per rev
        self.wheelbase = 0.20  # Distance between wheels in meters

    def start_gps(self):
        try:
            self.ser = serial.Serial(self.port, self.baudrate, timeout=1)
            self.running = True
            threading.Thread(target=self._gps_loop, daemon=True).start()
            logger.info(f"Started GPS reading on {self.port}")
            return True
        except Exception as e:
            logger.error(f"Failed to open GPS port: {e}")
            return False

    def _gps_loop(self):
        while self.running:
            try:
                line = self.ser.readline().decode('ascii', errors='replace').strip()
                if line.startswith('$GNGGA') or line.startswith('$GPGGA'):
                    msg = pynmea2.parse(line)
                    if msg.latitude and msg.longitude:
                        if not self.override_active:
                            self.current_lat = msg.latitude
                            self.current_lon = msg.longitude
                            self.gps_updated = True
            except Exception as e:
                pass

    def force_location(self, lat, lon):
        self.override_active = True
        self.current_lat = lat
        self.current_lon = lon
        if self.ref_lat is not None:
            x, y = latlon_to_xy(lat, lon, self.ref_lat, self.ref_lon)
            self.X[0, 0] = x
            self.X[1, 0] = y
            self.x_est = x
            self.y_est = y
            self.prev_gps_xy = (x, y)
        self.gps_updated = True

    def fetch_route(self, dest_lat, dest_lon):
        # No API key needed for public OSRM

        if self.current_lat == 0.0:
            logger.warning("No GPS fix yet, cannot fetch route.")
            return False

        # Set reference coordinates if not already set
        if self.ref_lat is None:
            self.ref_lat = self.current_lat
            self.ref_lon = self.current_lon
            # Initialize estimated position to origin
            self.X = np.zeros((4, 1))
            self.x_est = 0.0
            self.y_est = 0.0
            self.theta_est = 0.0

        # Use publipubliddo
        url = f"https://router.project-osrm.org/route/v1/driving/{self.current_lon},{self.current_lat};{dest_lon},{dest_lat}"
        params = {
            'steps': 'true'
        }

        try:
            response = requests.get(url, params=params)
            if response.status_code != 200:
                logger.error(f"OSRM HTTP {response.status_code}: {response.text}")
                return False

            data = response.json()
            if data.get('code') == 'Ok':
                raw_coords = []
                for leg in data['routes'][0]['legs']:
                    for step in leg['steps']:
                        loc = step['maneuver']['location']
                        raw_coords.append((loc[1], loc[0]))
                
                # Add final destination
                raw_coords.append((dest_lat, dest_lon))
                
                # Convert raw GPS waypoints to local Cartesian (x, y) coordinates
                raw_xy = [latlon_to_xy(lat, lon, self.ref_lat, self.ref_lon) for lat, lon in raw_coords]
                
                # Smooth the path (Spline-like interpolation)
                self.smoothed_waypoints = self._smooth_path(raw_xy, target_spacing=0.2, window_size=5)
                self.current_waypoint_index = 0
                
                # Initialize heading towards the first waypoint
                if len(self.smoothed_waypoints) > 1:
                    p1 = self.smoothed_waypoints[0]
                    p2 = self.smoothed_waypoints[1]
                    self.theta_est = math.atan2(p2[1] - p1[1], p2[0] - p1[0])
                
                logger.info(f"Fetched OSRM route. Smoothed into {len(self.smoothed_waypoints)} waypoints.")
                return True
            else:
                logger.error(f"OSRM API failed: {data.get('code')} - {data.get('message', '')}")
                return False
        except Exception as e:
            logger.error(f"Error fetching route from OSRM: {e}")
            return False

    def _smooth_path(self, raw_points, target_spacing=0.2, window_size=5):
        if len(raw_points) < 2:
            return raw_points
        
        # 1. Linear interpolation to increase waypoint density
        dense_points = []
        for i in range(len(raw_points) - 1):
            p1 = raw_points[i]
            p2 = raw_points[i+1]
            dx = p2[0] - p1[0]
            dy = p2[1] - p1[1]
            dist = math.hypot(dx, dy)
            if dist == 0:
                continue
            
            steps = max(1, int(dist / target_spacing))
            for s in range(steps):
                t = s / steps
                dense_points.append((p1[0] + t * dx, p1[1] + t * dy))
        dense_points.append(raw_points[-1])
        
        # 2. Moving average smoothing filter (simulates spline curve)
        if len(dense_points) <= window_size:
            return dense_points
        
        smoothed = []
        half_w = window_size // 2
        for i in range(len(dense_points)):
            start = max(0, i - half_w)
            end = min(len(dense_points), i + half_w + 1)
            sum_x = sum(p[0] for p in dense_points[start:end])
            sum_y = sum(p[1] for p in dense_points[start:end])
            count = end - start
            smoothed.append((sum_x / count, sum_y / count))
        return smoothed

    def update_localization(self, telemetry):
        """Fuses Encoder ticks, IMU Gyro Z, and GPS coordinates using Extended Kalman Filter"""
        current_time = time.time()
        dt = current_time - self.last_ekf_time
        if dt <= 0 or dt > 0.5: dt = 0.05
        self.last_ekf_time = current_time

        # --- 1. EKF PREDICTION STEP (Odometry & Gyro) ---
        # Extract Gyro Z (Degrees per second -> Radians per second)
        gz_deg = telemetry.get("gz", 0.0)
        gz_rad = math.radians(gz_deg)
        
        # Calculate velocity from Encoders
        ticks_l = telemetry.get("ticks_l", 0)
        ticks_r = telemetry.get("ticks_r", 0)
        v = 0.0
        if self.last_ticks_l is not None and self.last_ticks_r is not None:
            dtL = ticks_l - self.last_ticks_l
            dtR = ticks_r - self.last_ticks_r
            v = ((dtL + dtR) / 2.0 * self.meters_per_tick) / dt
        self.last_ticks_l = ticks_l
        self.last_ticks_r = ticks_r

        x, y, theta, bz = self.X[0,0], self.X[1,0], self.X[2,0], self.X[3,0]
        
        # Predict State
        theta_pred = theta + (gz_rad - bz) * dt
        theta_pred = (theta_pred + math.pi) % (2 * math.pi) - math.pi # Normalize -pi to pi
        x_pred = x + v * math.cos(theta_pred) * dt
        y_pred = y + v * math.sin(theta_pred) * dt
        
        self.X[0,0] = x_pred
        self.X[1,0] = y_pred
        self.X[2,0] = theta_pred
        # bz remains the same
        
        # Predict Covariance
        # Jacobian F
        F = np.eye(4)
        F[0, 2] = -v * math.sin(theta_pred) * dt
        F[1, 2] = v * math.cos(theta_pred) * dt
        F[2, 3] = -dt
        
        # Process Noise Q (High noise for Mecanum wheels slippage)
        Q = np.diag([0.05, 0.05, 0.05, 1e-6]) 
        self.P = F @ self.P @ F.T + Q

        # --- 2. EKF UPDATE STEP (Standalone GPS) ---
        if self.gps_updated and self.ref_lat is not None:
            self.gps_updated = False
            x_gps, y_gps = latlon_to_xy(self.current_lat, self.current_lon, self.ref_lat, self.ref_lon)
            
            # Compute Course Over Ground (COG) if moved > 0.5m since last GPS update
            cog = None
            if self.prev_gps_xy is not None:
                dx = x_gps - self.prev_gps_xy[0]
                dy = y_gps - self.prev_gps_xy[1]
                dist = math.hypot(dx, dy)
                if dist > 0.5:  # Moved enough to trust GPS heading
                    cog = math.atan2(dy, dx)
                    self.prev_gps_xy = (x_gps, y_gps)
            else:
                self.prev_gps_xy = (x_gps, y_gps)
            
            # Measurement Update
            if cog is not None:
                # We have X, Y, and Theta measurement
                Z = np.array([[x_gps], [y_gps], [cog]])
                H = np.zeros((3, 4))
                H[0, 0] = 1.0
                H[1, 1] = 1.0
                H[2, 2] = 1.0
                # Measurement Noise R (Standalone GPS is noisy: ~9.0 variance for position)
                R = np.diag([9.0, 9.0, 0.5]) 
            else:
                # We only have X, Y measurement
                Z = np.array([[x_gps], [y_gps]])
                H = np.zeros((2, 4))
                H[0, 0] = 1.0
                H[1, 1] = 1.0
                R = np.diag([9.0, 9.0])
            
            # Innovation
            Y = Z - H @ self.X
            if cog is not None:
                Y[2,0] = (Y[2,0] + math.pi) % (2 * math.pi) - math.pi # Normalize angle error
            
            S = H @ self.P @ H.T + R
            K = self.P @ H.T @ np.linalg.inv(S)
            
            # State Update
            self.X = self.X + K @ Y
            self.X[2,0] = (self.X[2,0] + math.pi) % (2 * math.pi) - math.pi # Normalize
            
            # Covariance Update
            I = np.eye(4)
            self.P = (I - K @ H) @ self.P

        # Update output variables for Pure Pursuit controller
        self.x_est = self.X[0,0]
        self.y_est = self.X[1,0]
        self.theta_est = self.X[2,0]

    def get_navigation_commands(self, telemetry):
        """
        Pure Pursuit Controller.
        Calculates left and right target speeds based on look-ahead distance.
        Returns: target_speed_l, target_speed_r, arrived
        """
        # Update localization state first
        self.update_localization(telemetry)
        
        # Check if final destination has been reached (using raw GPS to prevent EKF drift issues)
        if not self.smoothed_waypoints:
            return 0.0, 0.0, True
        final_target = self.smoothed_waypoints[-1]
        x_gps, y_gps = latlon_to_xy(self.current_lat, self.current_lon, self.ref_lat, self.ref_lon)
        dist_to_final = math.hypot(final_target[0] - x_gps, final_target[1] - y_gps)
        if dist_to_final < 15.0: # Arrived within 15.0m based on true GPS position
            return 0.0, 0.0, True

        # 1. Pure Pursuit Look-ahead Target Search
        # Giảm khoảng cách lookahead xuống 0.7m (từ 1.0m) để xe bám đường sát hơn, tránh cua rộng
        lookahead_dist = 0.7  
        
        # --- FIX: Find closest waypoint first to prevent going backwards ---
        min_dist = float('inf')
        closest_idx = self.current_waypoint_index
        search_window = min(len(self.smoothed_waypoints), self.current_waypoint_index + 50)
        
        for i in range(self.current_waypoint_index, search_window):
            pt = self.smoothed_waypoints[i]
            d = math.hypot(pt[0] - self.x_est, pt[1] - self.y_est)
            if d < min_dist:
                min_dist = d
                closest_idx = i
                
        # Update current waypoint to the closest one
        self.current_waypoint_index = closest_idx
        
        # --- Search forward for the look-ahead point ---
        target_pt = None
        for i in range(self.current_waypoint_index, len(self.smoothed_waypoints)):
            pt = self.smoothed_waypoints[i]
            d = math.hypot(pt[0] - self.x_est, pt[1] - self.y_est)
            if d >= lookahead_dist:
                target_pt = pt
                break
                
        # If no point is found ahead, target the final destination
        if target_pt is None:
            target_pt = final_target

        # 2. Transform target to robot local frame
        dx = target_pt[0] - self.x_est
        dy = target_pt[1] - self.y_est
        
        # Calculate heading error to target look-ahead point
        target_heading = math.atan2(dy, dx)
        heading_error = (target_heading - self.theta_est + math.pi) % (2 * math.pi) - math.pi

        # Nếu góc lệch hướng quá lớn (> 45 độ / ~0.8 rad), ưu tiên xoay tại chỗ (Spin in Place) để định hướng lại
        if abs(heading_error) > 0.8:
            turn_speed = 15.0  # Tốc độ quay tại chỗ (ticks/50ms)
            if heading_error > 0:
                # Quay trái: bánh trái quay lùi, bánh phải quay tiến
                return -turn_speed, turn_speed, False
            else:
                # Quay phải: bánh trái quay tiến, bánh phải quay lùi
                return turn_speed, -turn_speed, False

        # Local coordinates transformation
        # x_local is forward, y_local is left (lateral error)
        x_local = dx * math.cos(self.theta_est) + dy * math.sin(self.theta_est)
        y_local = -dx * math.sin(self.theta_est) + dy * math.cos(self.theta_est)

        # 3. Calculate steering curvature (kappa)
        # curvature = 2 * y_local / (L_ad ^ 2)
        L_ad = math.hypot(x_local, y_local)
        if L_ad > 0:
            kappa = (2.0 * y_local) / (L_ad ** 2)
        else:
            kappa = 0.0

        # 4. Command Generation (Differential Drive Kinematics)
        v_base = 15.0  # Base speed in ticks per 50ms (approx 15 * 0.002m / 0.05s = 0.6 m/s)
        
        # Reduce speed on sharp turns
        if abs(kappa) > 1.0:
            v_base = max(5.0, v_base / (abs(kappa) * 0.8))
            
        # Tăng hệ số bẻ lái (Steering Gain) vì bánh xe Mecanum/cao su có độ trượt lớn.
        # Nếu không nhân hệ số, chênh lệch vận tốc 2 bánh quá nhỏ sẽ không đủ thắng lực ma sát để xe rẽ.
        steering_gain = 2.5 
        w = v_base * kappa * steering_gain  # Angular velocity target
        
        # Left and Right motor target speeds
        v_left = v_base - w * (self.wheelbase / 2.0)
        v_right = v_base + w * (self.wheelbase / 2.0)

        # Ensure we stay within realistic boundaries
        max_speed = 25.0
        v_left = max(-max_speed, min(max_speed, v_left))
        v_right = max(-max_speed, min(max_speed, v_right))

        return v_left, v_right, False
