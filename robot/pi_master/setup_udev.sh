#!/bin/bash

echo "Thiết lập cố định cổng COM (udev rules) cho xe AGV..."

# Xóa rule cũ nếu có
sudo rm -f /etc/udev/rules.d/99-agv-usb.rules

# Mạch RTK GPS (Sử dụng chip CH340 - ID 1a86:7523)
echo 'SUBSYSTEM=="tty", ATTRS{idVendor}=="1a86", ATTRS{idProduct}=="7523", SYMLINK+="agv_gps"' | sudo tee -a /etc/udev/rules.d/99-agv-usb.rules > /dev/null

# Mạch ESP32 (Sử dụng chip CP210x - ID 10c4:ea60)
echo 'SUBSYSTEM=="tty", ATTRS{idVendor}=="10c4", ATTRS{idProduct}=="ea60", SYMLINK+="agv_esp32"' | sudo tee -a /etc/udev/rules.d/99-agv-usb.rules > /dev/null

# Khởi động lại service quản lý thiết bị
sudo udevadm control --reload-rules
sudo udevadm trigger

echo "Hoàn tất! Từ nay hệ thống sẽ luôn tự động nhận diện:"
echo "-> /dev/agv_gps   (Mạch RTK GPS)"
echo "-> /dev/agv_esp32 (Mạch ESP32)"
echo "Bạn không cần quan tâm nó là ttyUSB0 hay ttyUSB1 nữa!"
