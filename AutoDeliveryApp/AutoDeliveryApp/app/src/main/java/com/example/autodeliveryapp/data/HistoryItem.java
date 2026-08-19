package com.example.autodeliveryapp.data;

/**
 * Represents a task item shown in the active tasks list (MainActivity)
 * and history list (HistoryActivity).
 *
 * Extended to carry senderUid, robotId, slotId, and distance so that
 * MainActivity can open TrackingActivity with complete context without
 * a secondary Firebase read.
 */
public class HistoryItem {
    private String orderId, pickup, dropoff, status, date, price;
    // Extended fields for Tracking intent
    private String senderUid, robotId, slotId;
    private double distance;

    /** Legacy constructor (HistoryActivity / history list) */
    public HistoryItem(String orderId, String pickup, String dropoff,
                       String status, String date, String price) {
        this.orderId   = orderId;
        this.pickup    = pickup;
        this.dropoff   = dropoff;
        this.status    = status;
        this.date      = date;
        this.price     = price;
        this.senderUid = "";
        this.robotId   = "";
        this.slotId    = "";
        this.distance  = 0.0;
    }

    /** Full constructor used by MainActivity active-task list */
    public HistoryItem(String orderId, String pickup, String dropoff,
                       String status, String date, String price,
                       String senderUid, String robotId, String slotId,
                       double distance) {
        this.orderId   = orderId;
        this.pickup    = pickup;
        this.dropoff   = dropoff;
        this.status    = status;
        this.date      = date;
        this.price     = price;
        this.senderUid = senderUid != null ? senderUid : "";
        this.robotId   = robotId   != null ? robotId   : "";
        this.slotId    = slotId    != null ? slotId    : "";
        this.distance  = distance;
    }

    public String getOrderId()   { return orderId; }
    public String getPickup()    { return pickup; }
    public String getDropoff()   { return dropoff; }
    public String getStatus()    { return status; }
    public String getDate()      { return date; }
    public String getPrice()     { return price; }
    public String getSenderUid() { return senderUid; }
    public String getRobotId()   { return robotId; }
    public String getSlotId()    { return slotId; }
    public double getDistance()  { return distance; }
}