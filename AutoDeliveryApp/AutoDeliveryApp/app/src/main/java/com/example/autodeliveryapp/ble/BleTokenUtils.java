package com.example.autodeliveryapp.ble;

import java.security.SecureRandom;

public final class BleTokenUtils {
    private BleTokenUtils() {}

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a 128-bit cryptographically secure token, encoded as a 32-character Hex string.
     */
    public static String generateToken() {
        byte[] bytes = new byte[16]; // 16 bytes * 8 = 128 bits
        secureRandom.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
