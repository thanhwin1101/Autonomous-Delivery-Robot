package com.example.autodeliveryapp.ble;

import java.util.UUID;

public final class BleConstants {
    private BleConstants() {}

    // Demo UUID contract for AutoDeliveryRobot (Matches ESP32)
    public static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    public static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    public static final int GATT_TIMEOUT_MS = 10000;
    
    // Actions
    public static final String ACTION_CONFIRM_LOADED = "confirm_loaded";
    public static final String ACTION_OPEN_SLOT = "open_slot";
    public static final String ACTION_VERIFY_TOKEN = "verify_token";
    
    // Statuses
    public static final String BLE_STATE_CREATED = "created";
    public static final String BLE_STATE_SENDER_LOADED = "sender_loaded";
    public static final String BLE_STATE_RECEIVER_UNLOCKED = "receiver_unlocked";
}
