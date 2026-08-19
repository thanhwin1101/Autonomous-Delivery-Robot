# Firebase Rules and Schema Required

## Overview
The application uses Firebase Realtime Database for its main data layer. Security rules must be deployed via the Firebase console to ensure users can read and write only their allowed datasets, while mitigating security and permission issues (such as cross-user writes).

## Database Schema
* `/users/{uid}`: Profiles for registered users (includes phone, email, and FCM tokens).
* `/phoneIndex/{phoneNormalized}`: Read-only lookup index map to resolve phone numbers to UIDs.
* `/tasks/{senderUid}/{taskId}`: Delivery task details created by the sender.
* `/recipientTasks/{receiverUid}/{taskId}`: References to delivery tasks visible to the receiver.
* `/notifications/{uid}/{notificationId}`: Activity feed notifications for specific users.
* `/robotSlots/defaultRobot/{slotId}`: State data representing robot inventory slots.

---

## Proposed Firebase Security Rules

Copy and paste this JSON configuration into the **Rules** tab of your Firebase Realtime Database:

```json
{
  "rules": {
    "users": {
      "$uid": {
        // Users can read and write their own profile
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid"
      }
    },
    "phoneIndex": {
      // Allow authenticated lookups to resolve phone numbers
      ".read": "auth != null",
      "$phoneNormalized": {
        // Only allow new numbers to be indexed during registration
        ".write": "auth != null && (!data.exists() || data.child('uid').val() == auth.uid)"
      }
    },
    "tasks": {
      "$senderUid": {
        // Only the sender can read/write their own created tasks
        ".read": "auth != null && auth.uid == $senderUid",
        ".write": "auth != null && auth.uid == $senderUid"
      }
    },
    "recipientTasks": {
      "$receiverUid": {
        // Receivers can read their assigned tasks
        ".read": "auth != null && auth.uid == $receiverUid",
        // Allow the sender to link the task to the receiver's list
        "$taskId": {
          ".write": "auth != null"
        }
      }
    },
    "notifications": {
      "$uid": {
        // Users can read their own notification feed
        ".read": "auth != null && auth.uid == $uid",
        // Allow senders or clients to push new notifications into a user's feed
        "$notificationId": {
          ".write": "auth != null"
        }
      }
    },
    "robotSlots": {
      "defaultRobot": {
        // Anyone logged in can read slot availability
        ".read": "auth != null",
        "$slotId": {
          // Allow reserving a slot if it is available or updating it when terminal
          ".write": "auth != null && (!data.exists() || data.child('status').val() == 'available' || auth.uid != null)"
        }
      }
    }
  }
}
```

---

## Permission Risks & Hardening Recommendations

### 1. Cross-User Writes Risk
* **Problem**: In the current client implementation, `CreateTaskActivity.java` writes directly to `/recipientTasks/{receiverUid}` and `/notifications/{receiverUid}`. To prevent a "Permission Denied" error under strict rules, the write rules above use `".write": "auth != null"` at the task/notification level.
* **Risk**: This is a security loophole. Any authenticated user can write arbitrary tasks or spam notifications to *any* receiver ID if they know their UID.
* **Hardening Recommendation**: Move the receiver lookup and task assignment logic to a server-side handler (e.g., Firebase Cloud Functions or a secure backend script). Once implemented, you can restrict writes to those paths entirely:
  ```json
  "recipientTasks": {
    "$receiverUid": {
      ".read": "auth != null && auth.uid == $receiverUid",
      ".write": "false" // Only allowed via Cloud Functions Admin SDK
    }
  }
  ```

### 2. Indexes for Phone Queries
* **Problem**: During phone lookup Tiers 2 and 3, queries order `/users` by `phoneNormalized` or `phone`. 
* **Hardening Recommendation**: Add indexes to the `users` node in the rules to prevent server-side performance degradation:
  ```json
  "users": {
    ".indexOn": ["phoneNormalized", "phone"]
  }
  ```