package com.example.autodeliveryapp.utils;

/**
 *
 *
 *
 *
 *
 *
 * Terminal states: delivered, cancelled
 */
public final class TaskStatus {

    private TaskStatus() {

    }


    public static final String PENDING = "pending";
    public static final String GOING_TO_PICKUP = "going_to_pickup";
    public static final String ARRIVED_PICKUP = "arrived_pickup";
    public static final String WAITING_SENDER_LOAD = "waiting_sender_load";
    public static final String SENDER_LOADED = "sender_loaded";
    public static final String PICKED_UP = "picked_up";
    public static final String GOING_TO_DESTINATION = "going_to_destination";
    public static final String ARRIVED_DROPOFF = "arrived_dropoff";
    public static final String WAITING_RECEIVER_UNLOCK = "waiting_receiver_unlock";
    public static final String DELIVERED = "delivered";
    public static final String CANCELLED = "cancelled";

    /**
     *
     *
     */
    public static boolean isTerminal(String status) {
        return DELIVERED.equals(status) || CANCELLED.equals(status);
    }
}
