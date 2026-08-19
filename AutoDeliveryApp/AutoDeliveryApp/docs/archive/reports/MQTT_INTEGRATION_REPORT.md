# MQTT Integration Report

## Communication Strategy
- Robot realtime communication is handled via MQTT through HiveMQ Cloud.
- Android app uses the HiveMQ MQTT client.
- Connection is made via TCP TLS port 8883.
- WebSocket 8884 is not used in Android Phase 1.
- Configuration for MQTT_HOST, MQTT_USERNAME, MQTT_PASSWORD, etc. are read securely (e.g. from local.properties).

## Topics
- App subscribes to robot location topics.
- Tasks are sent to Firebase, not via MQTT directly, but the app may listen to MQTT for specific hardware-related events if implemented.

*Note: Runtime status remains unconfirmed until hardware testing is conducted.*