package com.example.autodeliveryapp.ble;

public interface BleActionCallback {
    void onSuccess();
    void onFailure(String error);
}
