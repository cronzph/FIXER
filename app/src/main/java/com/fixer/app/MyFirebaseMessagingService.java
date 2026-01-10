package com.fixer.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * FCM Service to handle push notifications
 * Works even when app is closed!
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "fixer_notifications";
    private static final String CHANNEL_NAME = "F.I.X.E.R Notifications";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "✅ New FCM token: " + token);

        // Save token to database
        saveTokenToDatabase(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "📩 Message received from: " + remoteMessage.getFrom());

        // Handle notification payload
        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();

            Log.d(TAG, "Notification Title: " + title);
            Log.d(TAG, "Notification Body: " + body);

            // Get data payload
            Map<String, String> data = remoteMessage.getData();
            String type = data.get("type");
            String reportId = data.get("reportId");
            String priority = data.get("priority");

            // Show notification
            sendNotification(title, body, type, reportId, priority);
        }

        // Handle data-only messages
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());

            Map<String, String> data = remoteMessage.getData();
            String type = data.get("type");
            String title = data.get("title");
            String body = data.get("body");
            String reportId = data.get("reportId");
            String priority = data.get("priority");

            if (title != null && body != null) {
                sendNotification(title, body, type, reportId, priority);
            }
        }
    }

    private void sendNotification(String title, String body, String type,
                                  String reportId, String priority) {
        Intent intent;

        // Determine which activity to open based on user role and type
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            // Get user role from SharedPreferences or determine from type
            switch (type != null ? type : "") {
                case "new_report":
                case "critical_report":
                case "report_completed":
                case "admin_task_aborted":
                    intent = new Intent(this, Admin.class);
                    break;

                case "task_assigned":
                case "task_aborted":
                    intent = new Intent(this, TechnicianDashboardActivity.class);
                    break;

                case "status_change":
                case "user_report_completed":
                    intent = new Intent(this, UserDashboardActivity.class);
                    break;

                case "chat_message":
                    if (reportId != null && !reportId.isEmpty()) {
                        intent = new Intent(this, ChatActivity.class);
                        intent.putExtra("reportId", reportId);
                    } else {
                        intent = new Intent(this, UserDashboardActivity.class);
                    }
                    break;

                default:
                    intent = new Intent(this, Login.class);
                    break;
            }
        } else {
            intent = new Intent(this, Login.class);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (reportId != null && !reportId.isEmpty()) {
            intent.putExtra("reportId", reportId);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(), // Unique request code
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        // Notification sound
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // Create notification channel (Android 8.0+)
        createNotificationChannel();

        // Determine notification priority
        int notificationPriority = NotificationCompat.PRIORITY_DEFAULT;
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        if ("critical".equals(priority) || "high".equals(priority)) {
            notificationPriority = NotificationCompat.PRIORITY_HIGH;
            importance = NotificationManager.IMPORTANCE_HIGH;
        }

        // Build notification
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notifications)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent)
                        .setPriority(notificationPriority)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setVibrate(new long[]{0, 500, 200, 500});

        // Add color for high priority
        if ("critical".equals(priority) || "high".equals(priority)) {
            notificationBuilder.setColor(getResources().getColor(android.R.color.holo_red_dark));
        }

        // Add action buttons based on type
        if ("new_report".equals(type) || "critical_report".equals(type)) {
            Intent viewIntent = new Intent(this, Admin.class);
            viewIntent.putExtra("reportId", reportId);
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent viewPendingIntent = PendingIntent.getActivity(
                    this,
                    (int) (System.currentTimeMillis() + 1),
                    viewIntent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );

            notificationBuilder.addAction(
                    R.drawable.ic_view,
                    "View Report",
                    viewPendingIntent
            );
        }

        if ("task_assigned".equals(type)) {
            Intent acceptIntent = new Intent(this, TechnicianDashboardActivity.class);
            acceptIntent.putExtra("reportId", reportId);
            acceptIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent acceptPendingIntent = PendingIntent.getActivity(
                    this,
                    (int) (System.currentTimeMillis() + 1),
                    acceptIntent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );

            notificationBuilder.addAction(
                    R.drawable.ic_check,
                    "View Task",
                    acceptPendingIntent
            );
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Generate unique notification ID
        int notificationId = (int) System.currentTimeMillis();

        if (notificationManager != null) {
            notificationManager.notify(notificationId, notificationBuilder.build());
            Log.d(TAG, "✅ Notification displayed: " + title);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("Maintenance report notifications");
            channel.enableLights(true);
            channel.setLightColor(getResources().getColor(R.color.primary));
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            channel.setShowBadge(true);

            NotificationManager notificationManager =
                    getSystemService(NotificationManager.class);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void saveTokenToDatabase(String token) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            String userId = auth.getCurrentUser().getUid();

            DatabaseReference dbRef = FirebaseDatabase.getInstance()
                    .getReference("user_tokens")
                    .child(userId);

            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("token", token);
            tokenData.put("updatedAt", System.currentTimeMillis());

            dbRef.setValue(tokenData)
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "✅ Token saved to database"))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "❌ Failed to save token", e));
        }
    }

    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();
        Log.d(TAG, "Messages deleted on server");
    }

    @Override
    public void onMessageSent(@NonNull String msgId) {
        super.onMessageSent(msgId);
        Log.d(TAG, "Message sent: " + msgId);
    }

    @Override
    public void onSendError(@NonNull String msgId, @NonNull Exception exception) {
        super.onSendError(msgId, exception);
        Log.e(TAG, "Send error for message: " + msgId, exception);
    }
}