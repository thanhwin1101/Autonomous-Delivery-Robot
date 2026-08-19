# MQTT Robot Simulator Guide

## Overview
Because physical robot hardware may not be available during app development, you can use a simulator to test the location updates over MQTT.

## Requirements
- An MQTT client such as MQTT Explorer or Mosquitto CLI.
- Connection details matching the app (HiveMQ Cloud, TLS Port 8883, matching username and password).

## Testing Flow
1. Connect the simulator to the MQTT Broker.
2. Publish location updates (Latitude and Longitude) to the designated robot topic.
3. Observe the `TrackingActivity` in the app to see the robot marker move across the map.