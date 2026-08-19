package com.example.autodeliveryapp.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class BleGattClient extends BluetoothGattCallback {
    private static final String TAG = "BleGattClient";
    private final String payload;
    private final BleActionCallback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private boolean completed = false;
    private final Runnable timeoutRunnable;

    public BleGattClient(String payload, BleActionCallback callback) {
        this.payload = payload;
        this.callback = callback;
        this.timeoutRunnable = () -> {
            if (!completed) {
                completed = true;
                if (gatt != null) {
                    gatt.disconnect();
                    gatt.close();
                }
                callback.onFailure("TIMEOUT");
            }
        };
    }

    public void setGatt(BluetoothGatt gatt) {
        this.gatt = gatt;
        handler.postDelayed(timeoutRunnable, BleConstants.GATT_TIMEOUT_MS);
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (this.gatt == null) this.gatt = gatt;
        if (completed) return;
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(TAG, "Connected to GATT server.");
            // Request larger MTU to ensure JSON payload fits
            gatt.requestMtu(512);
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.d(TAG, "Disconnected from GATT server.");
            if (!completed) {
                completed = true;
                handler.removeCallbacks(timeoutRunnable);
                callback.onFailure("DISCONNECTED");
                gatt.close();
            }
        }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        if (completed) return;
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "MTU changed to " + mtu);
            gatt.discoverServices();
        } else {
            Log.w(TAG, "MTU request failed, discovering services anyway.");
            gatt.discoverServices();
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (completed) return;
        if (status == BluetoothGatt.GATT_SUCCESS) {
            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(BleConstants.CHARACTERISTIC_UUID);
                if (characteristic != null) {
                    // Enable notifications/indications if required by the robot firmware for response
                    gatt.setCharacteristicNotification(characteristic, true);
                    
                    // MUST write to CCCD descriptor to actually enable notifications on the ESP32 side
                    java.util.UUID CCCD_UUID = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
                    android.bluetooth.BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
                    if (descriptor != null) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(descriptor, android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        } else {
                            descriptor.setValue(android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                    }
                    
                    // Add a small delay to allow GATT cache to settle on some Android devices and descriptor to write
                    handler.postDelayed(() -> {
                        if (completed) return;
                        byte[] value = payload.getBytes(StandardCharsets.UTF_8);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            int statusCode = gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            if (statusCode != 0) { // 0 is BluetoothStatusCodes.SUCCESS
                                fail("WRITE_FAILED_CODE_" + statusCode);
                            }
                        } else {
                            characteristic.setValue(value);
                            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            boolean success = gatt.writeCharacteristic(characteristic);
                            if (!success) {
                                fail("WRITE_FAILED");
                            }
                        }
                    }, 500);
                } else {
                    fail("CHARACTERISTIC_NOT_FOUND");
                }
            } else {
                fail("SERVICE_NOT_FOUND");
            }
        } else {
            fail("DISCOVERY_FAILED");
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (completed) return;
        if (status != BluetoothGatt.GATT_SUCCESS) {
            fail("WRITE_FAILED_STATUS_" + status);
        } else {
            Log.d(TAG, "Characteristic written successfully. Waiting for response...");
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (completed) return;
        if (BleConstants.CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
            byte[] value = characteristic.getValue();
            if (value != null) {
                String responseStr = new String(value, StandardCharsets.UTF_8);
                Log.d(TAG, "Received response: " + responseStr);
                
                BleProtocol.Response response = BleProtocol.Response.parse(responseStr);
                if (response.ok) {
                    succeed();
                } else {
                    fail(response.error.isEmpty() ? "UNKNOWN_ERROR" : response.error);
                }
            }
        }
    }

    private void succeed() {
        if (!completed) {
            completed = true;
            handler.removeCallbacks(timeoutRunnable);
            handler.post(callback::onSuccess);
            if (gatt != null) {
                gatt.disconnect();
                gatt.close();
            }
        }
    }

    private void fail(String errorMsg) {
        if (!completed) {
            completed = true;
            handler.removeCallbacks(timeoutRunnable);
            handler.post(() -> callback.onFailure(errorMsg));
            if (gatt != null) {
                gatt.disconnect();
                gatt.close();
            }
        }
    }
}
