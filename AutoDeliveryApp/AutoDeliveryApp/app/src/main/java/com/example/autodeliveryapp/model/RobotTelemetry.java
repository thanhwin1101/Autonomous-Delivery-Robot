package com.example.autodeliveryapp.model;

public class RobotTelemetry {
    public String robotId;
    public String taskId;
    public double lat;
    public double lng;
    public int battery;
    public double speed;
    public double heading;
    public boolean obstacle;
    public long timestamp;
    public long seq;

    public RobotTelemetry() {
    }
}
