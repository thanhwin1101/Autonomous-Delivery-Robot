package com.example.autodeliveryapp.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("MissingPermission")
public class BleManager {
    private static final String TAG = "BleManager";
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;
    private BluetoothLeScanner bluetoothLeScanner;
    
    // For demo purposes, we look for a robot name prefix
    private static final String ROBOT_NAME_PREFIX = "AGV_DELIVERY";

    public BleManager(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public void executeBleAction(String targetRobotId, String action, String taskId, String token, BleActionCallback callback) {
        if (!isBluetoothEnabled()) {
            callback.onFailure("BLUETOOTH_DISABLED");
            return;
        }

        if (!BlePermissionHelper.hasBlePermissions(context)) {
            callback.onFailure("MISSING_PERMISSIONS");
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            callback.onFailure("BLE_NOT_SUPPORTED");
            return;
        }

        String payload = BleProtocol.buildRequest(action, taskId, token);
        BleGattClient gattClient = new BleGattClient(payload, callback);

        ScanCallback scanCallback = new ScanCallback() {
            private boolean hasConnected = false;

            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (hasConnected) return;
                BluetoothDevice device = result.getDevice();
                String deviceName = device.getName();
                
                boolean isTarget = false;
                
                // 1. Primary check: Service UUID
                if (result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null) {
                    if (result.getScanRecord().getServiceUuids().contains(new android.os.ParcelUuid(BleConstants.SERVICE_UUID))) {
                        isTarget = true;
                    }
                }
                
                // 2. Fallback check: Device name prefix or exact MAC
                if (!isTarget && deviceName != null && deviceName.startsWith(ROBOT_NAME_PREFIX)) {
                    isTarget = true;
                }
                if (!isTarget && device.getAddress().equals(targetRobotId)) {
                    isTarget = true;
                }

                if (isTarget) {
                    if (hasConnected) return;
                    hasConnected = true;
                    Log.d(TAG, "Found target robot: " + deviceName + " [" + device.getAddress() + "]");
                    stopScan(this);
                    // Connect GATT
                    BluetoothGatt gatt = device.connectGatt(context, false, gattClient);
                    gattClient.setGatt(gatt);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "Scan failed with error: " + errorCode);
                stopScan(this);
                callback.onFailure("SCAN_FAILED");
            }
        };

        startScan(scanCallback, callback);
    }

    private void startScan(ScanCallback scanCallback, BleActionCallback errorCallback) {
        if (isScanning) return;

        List<ScanFilter> filters = new ArrayList<>();
        // No strict OS filter here to allow fallback by device name if Service UUID is missing from advertisement
        
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        handler.postDelayed(() -> {
            if (isScanning) {
                Log.w(TAG, "Scan timeout, robot not found");
                stopScan(scanCallback);
                errorCallback.onFailure("ROBOT_NOT_FOUND");
            }
        }, BleConstants.GATT_TIMEOUT_MS);

        isScanning = true;
        bluetoothLeScanner.startScan(filters, settings, scanCallback);
        Log.d(TAG, "BLE scan started");
    }

    private void stopScan(ScanCallback scanCallback) {
        if (isScanning && bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(scanCallback);
        }
        isScanning = false;
        Log.d(TAG, "BLE scan stopped");
    }
}
