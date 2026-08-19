package com.example.autodeliveryapp.data;

/**
 * Model class cho notification items.
 *
 *
 */
public class NotificationItem {
    private String icon, title, message, time;

    private String orderId;
    private String taskId;
    private String pickup;
    private String dropoff;
    private double distance;
    private String senderUid;
    private String type;
    private boolean read;
    private long createdAt;
    private String slotId;
    private String robotId;

    // Default constructor required for calls to DataSnapshot.getValue(NotificationItem.class)
    public NotificationItem() {
    }

    public NotificationItem(String icon, String title, String message, String time,
                            String orderId, String pickup, String dropoff, double distance) {
        this.icon = icon;
        this.title = title;
        this.message = message;
        this.time = time;
        this.orderId = orderId;
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.distance = distance;
    }

    public String getIcon() { return icon; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getTime() { return time; }
    public String getOrderId() { return orderId != null ? orderId : taskId; }
    public String getTaskId() { return taskId != null ? taskId : orderId; }
    public String getPickup() { return pickup; }
    public String getDropoff() { return dropoff; }
    public double getDistance() { return distance; }
    public String getSenderUid() { return senderUid; }
    public String getType() { return type; }
    public boolean isRead() { return read; }
    public long getCreatedAt() { return createdAt; }
    public String getSlotId() { return slotId; }
    public String getRobotId() { return robotId; }
}
