package com.example.autodeliveryapp.model;

public class RobotCommand {
    public String commandId;
    public String command;
    public String taskId;
    public String orderId;
    public String robotId;
    public String slotId;
    public double pickupLat;
    public double pickupLng;
    public String pickupAddress;
    public double dropoffLat;
    public double dropoffLng;
    public String dropoffAddress;
    public String bleToken;
    public long timestamp;

    public RobotCommand() {
    }
}
