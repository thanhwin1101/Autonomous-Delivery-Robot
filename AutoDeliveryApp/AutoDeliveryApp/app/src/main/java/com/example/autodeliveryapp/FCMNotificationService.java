package com.example.autodeliveryapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FCMNotificationService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "delivery_channel";
    private static final String CHANNEL_NAME = "Delivery Notifications";

    /**
     *
     *
     *
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);


        String title = getString(R.string.app_name);
        String message = "You have a new notification from order yours";

        if (remoteMessage.getNotification() != null) {


            String notifTitle = remoteMessage.getNotification().getTitle();
            String notifBody = remoteMessage.getNotification().getBody();

            if (notifTitle != null && !notifTitle.isEmpty())
                title = notifTitle;
            if (notifBody != null && !notifBody.isEmpty())
                message = notifBody;

        } else if (!remoteMessage.getData().isEmpty()) {

            // foreground
            String dataTitle = remoteMessage.getData().get("title");
            String dataMessage = remoteMessage.getData().get("message");

            if (dataTitle != null && !dataTitle.isEmpty())
                title = dataTitle;
            if (dataMessage != null && !dataMessage.isEmpty())
                message = dataMessage;
        }

        showNotification(title, message);
    }

    /**
     *
     *
     *
     *
     *
     *
     *
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM Token issued: " + token);
        saveFcmTokenToDatabase(token);
    }

    /**
     *
     * users/{userId}/fcmToken.
     *
     */
    private void saveFcmTokenToDatabase(String token) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {


            Log.d(TAG, "User not logged in - saving temp token to SharedPreferences");
            getSharedPreferences(Constants.FCM_PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(Constants.FCM_PREFS_KEY_PENDING_TOKEN, token)
                    .apply();
            return;
        }


        String userId = currentUser.getUid();
        FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("users")
                .child(userId)
                .child("fcmToken")
                .setValue(token)
                .addOnSuccessListener(unused -> Log.d(TAG, "FCM token saved to DB successfully for user: " + userId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save FCM token: " + e.getMessage()));
    }

    private void showNotification(String title, String message) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            Log.e(TAG, "NotificationManager is null - cannot show notification");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, NotificationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        int notificationId = (message != null) ? message.hashCode() : (int) System.currentTimeMillis();
        manager.notify(notificationId, builder.build());
    }
}