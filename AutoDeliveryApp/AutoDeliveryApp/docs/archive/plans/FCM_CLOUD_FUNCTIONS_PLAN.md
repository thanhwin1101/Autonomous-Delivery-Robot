# FCM Cloud Functions Plan

## Push Notification Implementation
- The client app receives push notification tokens and handles messages when they arrive.
- However, real FCM push notifications require a backend implementation.
- Cloud Functions for Firebase or an Admin SDK backend server is needed to trigger the actual push payload to FCM when a database event occurs.
- The Android app currently only stores the FCM token in the database.
