package com.fixer.app;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Modern NotificationHelper that uses Firebase Realtime Database
 * for real-time notifications without needing FCM Server Key
 */
public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    private static DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

    /**
     * Notify admins when a new report is created
     */
    public static void notifyNewReport(String reportId, String title, String reportedBy,
                                       String campus, String priority) {
        String notificationTitle = "New Maintenance Report";
        String notificationBody = reportedBy + " reported: " + title + " (" + priority.toUpperCase() + ")";

        sendNotificationToRole("ADMIN", notificationTitle, notificationBody,
                "new_report", reportId, priority);

        Log.d(TAG, "Sending new report notification to admins: " + title);
    }

    /**
     * Notify admins when a critical report is created
     */
    public static void notifyCriticalReport(String reportId, String title, String location) {
        String notificationTitle = "🚨 CRITICAL REPORT";
        String notificationBody = "URGENT: " + title + " at " + location;

        sendNotificationToRole("ADMIN", notificationTitle, notificationBody,
                "critical_report", reportId, "critical");

        Log.d(TAG, "Sending critical report notification: " + title);
    }

    /**
     * Notify technician when a task is assigned to them
     */
    public static void notifyTechnicianAssignment(String technicianEmail, String reportId,
                                                  String title, String location, String scheduledDate) {
        String notificationTitle = "New Task Assigned";
        String notificationBody = "You have been assigned: " + title;

        if (scheduledDate != null && !scheduledDate.isEmpty()) {
            notificationBody += " (Scheduled: " + scheduledDate + ")";
        }

        sendNotificationToUser(technicianEmail, notificationTitle, notificationBody,
                "task_assigned", reportId, "high");

        Log.d(TAG, "Sending assignment notification to technician: " + technicianEmail);
    }

    /**
     * Notify user when their report status changes
     */
    public static void notifyReportStatusChange(String reportId, String title, String status,
                                                String reporterUid, String reporterEmail) {
        String notificationTitle = "Report Status Update";
        String notificationBody = "Your report '" + title + "' is now " + status.toUpperCase();

        String priority = "medium";
        if ("completed".equalsIgnoreCase(status) || "rejected".equalsIgnoreCase(status)) {
            priority = "high";
        }

        // Send by UID (more reliable)
        if (reporterUid != null && !reporterUid.isEmpty()) {
            sendNotificationToUserId(reporterUid, notificationTitle, notificationBody,
                    "status_change", reportId, priority);
        } else if (reporterEmail != null) {
            sendNotificationToUser(reporterEmail, notificationTitle, notificationBody,
                    "status_change", reportId, priority);
        }

        Log.d(TAG, "Sending status change notification to user: " + reporterEmail);
    }

    /**
     * Notify admins when a report is completed
     */
    public static void notifyReportCompleted(String reportId, String title, String technicianName) {
        String notificationTitle = "Task Completed";
        String notificationBody = technicianName + " completed: " + title;

        sendNotificationToRole("ADMIN", notificationTitle, notificationBody,
                "report_completed", reportId, "medium");

        Log.d(TAG, "Sending completion notification to admins: " + title);
    }

    /**
     * Notify user when their report is completed
     */
    public static void notifyUserReportCompleted(String reportId, String title,
                                                 String reporterEmail, String technicianName) {
        String notificationTitle = "✅ Your Report is Complete";
        String notificationBody = "Your maintenance request '" + title + "' has been completed by " + technicianName;

        sendNotificationToUser(reporterEmail, notificationTitle, notificationBody,
                "user_report_completed", reportId, "high");

        Log.d(TAG, "Sending completion notification to user: " + reporterEmail);
    }

    /**
     * Notify when a new chat message is received
     */
    public static void notifyNewChatMessage(String chatId, String reportId, String reportTitle,
                                            String senderName, String senderRole, String message,
                                            String recipientEmail) {
        String notificationTitle = senderName + " (" + senderRole + ")";
        String notificationBody = message;

        // Truncate long messages
        if (notificationBody.length() > 100) {
            notificationBody = notificationBody.substring(0, 97) + "...";
        }

        sendNotificationToUser(recipientEmail, notificationTitle, notificationBody,
                "chat_message", reportId, "low");

        Log.d(TAG, "Sending chat notification to: " + recipientEmail);
    }

    /**
     * Notify technician when task is aborted or reassigned
     */
    public static void notifyTaskAborted(String reportId, String title, String technicianEmail,
                                         String reason) {
        String notificationTitle = "Task Status Change";
        String notificationBody = "Task '" + title + "' has been updated. Reason: " + reason;

        sendNotificationToUser(technicianEmail, notificationTitle, notificationBody,
                "task_aborted", reportId, "medium");

        Log.d(TAG, "Sending abort notification to technician: " + technicianEmail);
    }

    /**
     * Notify admins when a task is aborted by technician
     */
    public static void notifyAdminsTaskAborted(String reportId, String title,
                                               String technicianName, String reason) {
        String notificationTitle = "Task Aborted";
        String notificationBody = technicianName + " aborted: " + title + ". Reason: " + reason;

        sendNotificationToRole("ADMIN", notificationTitle, notificationBody,
                "admin_task_aborted", reportId, "high");

        Log.d(TAG, "Sending abort notification to admins");
    }

    /**
     * Send notification to all users with a specific role
     */
    private static void sendNotificationToRole(String role, String title, String body,
                                               String type, String reportId, String priority) {
        mDatabase.child("users").orderByChild("role").equalTo(role)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                            String userId = userSnapshot.getKey();
                            Boolean roleActive = userSnapshot.child("roleActive").getValue(Boolean.class);

                            // Only send to active users
                            if (Boolean.TRUE.equals(roleActive)) {
                                storeNotificationInDatabase(userId, title, body, type, reportId, priority);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to get users by role", error.toException());
                    }
                });
    }

    /**
     * Send notification to a specific user by email
     */
    private static void sendNotificationToUser(String userEmail, String title, String body,
                                               String type, String reportId, String priority) {
        if (userEmail == null || userEmail.isEmpty()) {
            Log.w(TAG, "Cannot send notification: user email is null or empty");
            return;
        }

        mDatabase.child("users").orderByChild("email").equalTo(userEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                            String userId = userSnapshot.getKey();
                            storeNotificationInDatabase(userId, title, body, type, reportId, priority);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to get user by email", error.toException());
                    }
                });
    }

    /**
     * Send notification to a specific user by ID
     */
    private static void sendNotificationToUserId(String userId, String title, String body,
                                                 String type, String reportId, String priority) {
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "Cannot send notification: user ID is null or empty");
            return;
        }

        storeNotificationInDatabase(userId, title, body, type, reportId, priority);
    }

    /**
     * Store notification in database for real-time delivery
     */
    private static void storeNotificationInDatabase(String userId, String title, String body,
                                                    String type, String reportId, String priority) {
        String notificationId = mDatabase.child("user_notifications").child(userId).push().getKey();

        if (notificationId != null) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("notificationId", notificationId);
            notification.put("title", title);
            notification.put("body", body);
            notification.put("type", type);
            notification.put("reportId", reportId);
            notification.put("priority", priority);
            notification.put("timestamp", System.currentTimeMillis());
            notification.put("read", false);

            mDatabase.child("user_notifications").child(userId).child(notificationId)
                    .setValue(notification)
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "✅ Notification stored for user: " + userId + " - " + title))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "❌ Failed to store notification", e));
        }
    }

    /**
     * Mark notification as read
     */
    public static void markNotificationAsRead(String userId, String notificationId) {
        if (userId == null || notificationId == null) return;

        mDatabase.child("user_notifications").child(userId).child(notificationId)
                .child("read").setValue(true)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "Notification marked as read: " + notificationId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to mark notification as read", e));
    }

    /**
     * Delete notification
     */
    public static void deleteNotification(String userId, String notificationId) {
        if (userId == null || notificationId == null) return;

        mDatabase.child("user_notifications").child(userId).child(notificationId)
                .removeValue()
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "Notification deleted: " + notificationId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to delete notification", e));
    }

    /**
     * Clear all notifications for a user
     */
    public static void clearAllNotifications(String userId) {
        if (userId == null) return;

        mDatabase.child("user_notifications").child(userId)
                .removeValue()
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "All notifications cleared for user: " + userId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to clear notifications", e));
    }

    /**
     * Get unread notification count
     */
    public static void getUnreadCount(String userId, UnreadCountCallback callback) {
        if (userId == null) {
            callback.onCountReceived(0);
            return;
        }

        mDatabase.child("user_notifications").child(userId)
                .orderByChild("read").equalTo(false)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int count = (int) snapshot.getChildrenCount();
                        callback.onCountReceived(count);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to get unread count", error.toException());
                        callback.onCountReceived(0);
                    }
                });
    }

    /**
     * Listen for new notifications in real-time
     */
    public static void listenForNotifications(String userId, NotificationListener listener) {
        if (userId == null) return;

        mDatabase.child("user_notifications").child(userId)
                .orderByChild("timestamp")
                .limitToLast(1)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot notifSnapshot : snapshot.getChildren()) {
                            String title = notifSnapshot.child("title").getValue(String.class);
                            String body = notifSnapshot.child("body").getValue(String.class);
                            String type = notifSnapshot.child("type").getValue(String.class);
                            String reportId = notifSnapshot.child("reportId").getValue(String.class);
                            Long timestamp = notifSnapshot.child("timestamp").getValue(Long.class);

                            if (title != null && body != null) {
                                listener.onNewNotification(title, body, type, reportId, timestamp);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to listen for notifications", error.toException());
                    }
                });
    }

    public interface UnreadCountCallback {
        void onCountReceived(int count);
    }

    public interface NotificationListener {
        void onNewNotification(String title, String body, String type, String reportId, Long timestamp);
    }
}