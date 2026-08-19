package com.example.autodeliveryapp.utils;

/**
 *
 *
 *
 *
 *
 *
 *
 */
public final class NotificationUtils {

    private NotificationUtils() {

    }



    /** Order was created successfully (for sender) */
    public static final String TYPE_TASK_CREATED = "task_created";

    /** New order is arriving (for receiver) */
    public static final String TYPE_INCOMING_DELIVERY = "incoming_delivery";

    /** Robot is on the way to receive point */
    public static final String TYPE_ROBOT_GOING_TO_PICKUP = "going_to_pickup";
    
    public static final String TYPE_ROBOT_ARRIVED_PICKUP = "arrived_pickup";
    public static final String TYPE_WAITING_SENDER_LOAD = "waiting_sender_load";
    public static final String TYPE_SENDER_LOADED = "sender_loaded";

    /** Robot has picked up the item successfully */
    public static final String TYPE_ROBOT_PICKED_UP = "picked_up";

    /** Robot is on the way to destination */
    public static final String TYPE_ROBOT_GOING_TO_DESTINATION = "going_to_destination";
    
    public static final String TYPE_ROBOT_ARRIVED_DROPOFF = "arrived_dropoff";
    public static final String TYPE_WAITING_RECEIVER_UNLOCK = "waiting_receiver_unlock";

    /** Order was created successfully (for sender) */
    public static final String TYPE_DELIVERED = "delivered";

    /** Order was created successfully (for sender) */
    public static final String TYPE_CANCELLED = "cancelled";



    /**
     *
     *
     * @param type notification type constant
     * @return title string
     */
    public static String buildTitle(String type) {
        if (type == null) return "Notifications";
        switch (type) {
            case TYPE_TASK_CREATED:
                return "Order created successfully";
            case TYPE_INCOMING_DELIVERY:
                return "You have a new order";
            case TYPE_ROBOT_GOING_TO_PICKUP:
                return "Robot is going to pickup point";
            case TYPE_ROBOT_ARRIVED_PICKUP:
                return "Robot arrived at pickup";
            case TYPE_WAITING_SENDER_LOAD:
                return "Waiting for sender to load";
            case TYPE_SENDER_LOADED:
                return "Sender loaded item";
            case TYPE_ROBOT_PICKED_UP:
                return "Robot has picked up the item";
            case TYPE_ROBOT_GOING_TO_DESTINATION:
                return "Robot is delivering";
            case TYPE_ROBOT_ARRIVED_DROPOFF:
                return "Robot arrived at dropoff";
            case TYPE_WAITING_RECEIVER_UNLOCK:
                return "Waiting for receiver to unlock";
            case TYPE_DELIVERED:
                return "Delivered successfully";
            case TYPE_CANCELLED:
                return "Order cancelled";
            default:
                return "Notifications";
        }
    }

    /**
     *
     *
     * @param type    notification type constant
     *
     *
     *
     *
     * @return message string
     */
    public static String buildMessage(String type, String orderId, String slotId,
                                       String pickup, String dropoff) {
        if (type == null) return "You have a new notification.";
        String safeOrderId = orderId != null ? orderId : "---";

        switch (type) {
            case TYPE_TASK_CREATED:
                return "Order " + safeOrderId + " created successfully.";

            case TYPE_INCOMING_DELIVERY:
                if (pickup != null && dropoff != null) {
                    return "You have an incoming order from " + pickup + " to " + dropoff + ".";
                }
                return "Order " + safeOrderId + " is being delivered to you.";

            case TYPE_ROBOT_GOING_TO_PICKUP:
                return "Robot is on the way to the pickup point.";
            case TYPE_ROBOT_ARRIVED_PICKUP:
                return "Robot has arrived at the pickup location.";
            case TYPE_WAITING_SENDER_LOAD:
                return "Robot is waiting for you to load the item.";
            case TYPE_SENDER_LOADED:
                return "Item loaded successfully into the robot.";

            case TYPE_ROBOT_PICKED_UP:
                String slotInfo = (slotId != null && !slotId.isEmpty())
                        ? " at slot " + slotId
                        : "";
                return "Robot has picked up the item successfully" + slotInfo + ".";

            case TYPE_ROBOT_GOING_TO_DESTINATION:
                return "Robot is on the way to the destination.";
            case TYPE_ROBOT_ARRIVED_DROPOFF:
                return "Robot has arrived at the destination.";
            case TYPE_WAITING_RECEIVER_UNLOCK:
                return "Robot is waiting for you to unlock the slot.";

            case TYPE_DELIVERED:
                return "Robot delivered successfully. Order " + safeOrderId + " completed.";

            case TYPE_CANCELLED:
                return "Order " + safeOrderId + " has been cancelled.";

            default:
                return "You have a new notification from order " + safeOrderId + ".";
        }
    }

    /**
     *
     *
     * @param type notification type constant
     * @return emoji string
     */
    public static String getIcon(String type) {
        if (type == null) return "🔔";
        switch (type) {
            case TYPE_TASK_CREATED:
                return "✅";
            case TYPE_INCOMING_DELIVERY:
                return "📦";
            case TYPE_ROBOT_GOING_TO_PICKUP:
                return "🚗";
            case TYPE_ROBOT_ARRIVED_PICKUP:
                return "📍";
            case TYPE_WAITING_SENDER_LOAD:
                return "⏳";
            case TYPE_SENDER_LOADED:
                return "✅";
            case TYPE_ROBOT_PICKED_UP:
                return "📥";
            case TYPE_ROBOT_GOING_TO_DESTINATION:
                return "🚚";
            case TYPE_ROBOT_ARRIVED_DROPOFF:
                return "🏁";
            case TYPE_WAITING_RECEIVER_UNLOCK:
                return "🔓";
            case TYPE_DELIVERED:
                return "🎉";
            case TYPE_CANCELLED:
                return "❌";
            default:
                return "🔔";
        }
    }
}
