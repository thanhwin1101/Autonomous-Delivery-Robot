package com.example.autodeliveryapp.utils;

/**
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
public final class PhoneUtils {

    private PhoneUtils() {

    }

    /**
     *
     *
     *
     *
     */
    public static String normalizePhone(String rawPhone) {
        if (rawPhone == null) return "";


        String cleaned = rawPhone.trim()
                .replaceAll("[\\s\\-.]", "");


        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
            } else if (c == '+' && i == 0) {
                sb.append(c);
            }

        }
        String digits = sb.toString();


        if (digits.startsWith("+84") && digits.length() > 3) {
            digits = "0" + digits.substring(3);
        }

        else if (digits.startsWith("84") && digits.length() >= 11 && digits.length() <= 12) {
            digits = "0" + digits.substring(2);
        }

        return digits;
    }

    /**
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     */
    public static boolean isLikelyPhone(String input) {
        if (input == null || input.trim().isEmpty()) return false;

        String trimmed = input.trim();


        if (trimmed.contains("@")) return false;


        if (!trimmed.matches("^[0-9+\\s\\-.]+$")) return false;


        int digitCount = 0;
        for (char c : trimmed.toCharArray()) {
            if (Character.isDigit(c)) digitCount++;
        }

        return digitCount >= 8;
    }
}
