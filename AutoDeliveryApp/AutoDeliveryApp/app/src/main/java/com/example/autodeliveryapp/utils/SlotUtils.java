package com.example.autodeliveryapp.utils;

import com.example.autodeliveryapp.Constants;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 *
 *
 * Slot States:
 *
 *
 *
 * Release Logic:
 *
 *
 *
 *
 *
 */
public final class SlotUtils {

    private SlotUtils() {

    }

    /**
     *
     *
     *
     *
     *
     */
    public static void releaseSlot(String robotId, String slotId) {
        if (robotId == null || robotId.isEmpty() || slotId == null || slotId.isEmpty()) {
            android.util.Log.w("SlotUtils", "releaseSlot called with null/empty robotId or slotId");
            return;
        }

        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("status", "available");
        updateMap.put("taskId", null);
        updateMap.put("orderId", null);
        updateMap.put("updatedAt", com.google.firebase.database.ServerValue.TIMESTAMP);

        FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("robotSlots")
                .child(robotId)
                .child(slotId)
                .updateChildren(updateMap)
                .addOnSuccessListener(unused ->
                        android.util.Log.d("SlotUtils", "Slot " + slotId + " released on " + robotId))
                .addOnFailureListener(e ->
                        android.util.Log.e("SlotUtils", "Failed to release slot " + slotId + ": " + e.getMessage()));
    }

    /**
     *
     *
     *
     * @param status  current task status
     * @param robotId robot ID
     * @param slotId  slot ID
     *
     */
    public static boolean releaseSlotIfTerminal(String status, String robotId, String slotId) {
        if (TaskStatus.isTerminal(status)) {
            releaseSlot(robotId, slotId);
            return true;
        }
        return false;
    }
}
