package com.fixer.app;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for chat-related operations in the FIXER app
 * FIXED VERSION - Enables users to create chats and fixes permission errors
 */
public class ChatHelper {

    private static final String TAG = "ChatHelper";

    /**
     * Creates a direct chat between two users - ENHANCED VERSION
     */
    public static void startDirectChat(Context context, String recipientUid, String recipientName) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user for direct chat");
            return;
        }

        String chatId = generateDirectChatId(currentUser.getUid(), recipientUid);

        // Check if chat exists, create if not
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();
        mDatabase.child("chats").child(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Create the direct chat
                    createDirectChat(currentUser.getUid(), recipientUid, recipientName, () -> {
                        markChatAsRead(chatId, currentUser.getUid());
                        // After creation, start the chat
                        Intent intent = new Intent(context, ChatActivity.class);
                        intent.putExtra(ChatActivity.EXTRA_RECIPIENT_UID, recipientUid);
                        intent.putExtra(ChatActivity.EXTRA_RECIPIENT_NAME, recipientName);
                        context.startActivity(intent);
                    });
                } else {
                    markChatAsRead(chatId, currentUser.getUid());
                    // Chat exists, open directly
                    Intent intent = new Intent(context, ChatActivity.class);
                    intent.putExtra(ChatActivity.EXTRA_RECIPIENT_UID, recipientUid);
                    intent.putExtra(ChatActivity.EXTRA_RECIPIENT_NAME, recipientName);
                    context.startActivity(intent);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check direct chat existence", error.toException());
            }
        });
    }

    /**
     * Creates a chat related to a specific report - ENHANCED VERSION
     */
    public static void startReportChat(Context context, String reportId) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user for report chat");
            return;
        }

        String chatId = generateReportChatId(reportId);

        // Check if chat exists, create if not
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();
        mDatabase.child("chats").child(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Get report details first, then create chat
                    mDatabase.child("maintenance_reports").child(reportId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot reportSnapshot) {
                                    if (reportSnapshot.exists()) {
                                        String title = reportSnapshot.child("title").getValue(String.class);
                                        String reporterUid = reportSnapshot.child("reportedByUid").getValue(String.class);
                                        String reporterName = reportSnapshot.child("reportedBy").getValue(String.class);

                                        if (title != null) {
                                            // Create the chat with proper structure
                                            createReportChat(reportId, title,
                                                    reporterUid != null ? reporterUid : currentUser.getUid(),
                                                    reporterName != null ? reporterName : "User");
                                        }
                                    }
                                    markChatAsRead(chatId, currentUser.getUid());
                                    // Open chat regardless (will be created by now or user can create it)
                                    Intent intent = new Intent(context, ChatActivity.class);
                                    intent.putExtra(ChatActivity.EXTRA_REPORT_ID, reportId);
                                    context.startActivity(intent);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    markChatAsRead(chatId, currentUser.getUid());
                                    // Still try to open the chat
                                    Intent intent = new Intent(context, ChatActivity.class);
                                    intent.putExtra(ChatActivity.EXTRA_REPORT_ID, reportId);
                                    context.startActivity(intent);
                                }
                            });
                } else {
                    // Chat exists, open directly
                    markChatAsRead(chatId, currentUser.getUid());
                    Intent intent = new Intent(context, ChatActivity.class);
                    intent.putExtra(ChatActivity.EXTRA_REPORT_ID, reportId);
                    context.startActivity(intent);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check report chat existence", error.toException());
                markChatAsRead(chatId, currentUser.getUid());
                // Still try to open the chat
                Intent intent = new Intent(context, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_REPORT_ID, reportId);
                context.startActivity(intent);
            }
        });
    }

    /**
     * Opens the chat list activity
     */
    public static void openChatList(Context context) {
        Intent intent = new Intent(context, ChatListActivity.class);
        context.startActivity(intent);
    }

    /**
     * Creates a direct chat between two users - NEW METHOD
     */
    public static void createDirectChat(String currentUserId, String recipientUid, String recipientName, Runnable onComplete) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        // Get current user info first
        mDatabase.child("users").child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String currentUserName = snapshot.child("displayName").getValue(String.class);
                        if (currentUserName == null) {
                            currentUserName = "User";
                        }

                        String chatId = generateDirectChatId(currentUserId, recipientUid);

                        // Create the complete chat structure
                        Map<String, Object> completeChat = new HashMap<>();

                        // Create metadata object
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("createdAt", System.currentTimeMillis());
                        metadata.put("createdBy", currentUserId);
                        metadata.put("chatType", "direct");

                        // Add participants
                        Map<String, Boolean> participants = new HashMap<>();
                        participants.put(currentUserId, true);
                        participants.put(recipientUid, true);
                        metadata.put("participants", participants);

                        // Add metadata to complete chat structure
                        completeChat.put("metadata", metadata);

                        // Create the chat
                        String finalCurrentUserName = currentUserName;
                        mDatabase.child("chats").child(chatId).setValue(completeChat)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Direct chat created successfully");

                                    // Send initial system message
                                    sendDirectChatWelcomeMessage(chatId, finalCurrentUserName, recipientName);

                                    if (onComplete != null) {
                                        onComplete.run();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create direct chat", e);
                                    if (onComplete != null) {
                                        onComplete.run(); // Still try to proceed
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to get current user info", error.toException());
                        if (onComplete != null) {
                            onComplete.run(); // Still try to proceed
                        }
                    }
                });
    }

    /**
     * Sends welcome message for direct chats
     */
    private static void sendDirectChatWelcomeMessage(String chatId, String currentUserName, String recipientName) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        String messageId = mDatabase.child("chats").child(chatId).child("messages").push().getKey();
        if (messageId != null) {
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("senderId", "system");
            messageData.put("senderName", "System");
            messageData.put("senderRole", "SYSTEM");
            messageData.put("message", "Chat started between " + currentUserName + " and " + recipientName + ".");
            messageData.put("timestamp", System.currentTimeMillis());
            messageData.put("messageType", "system");
            messageData.put("readBy", new HashMap<String, Long>());

            mDatabase.child("chats").child(chatId).child("messages").child(messageId)
                    .setValue(messageData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Welcome message sent for direct chat: " + chatId);
                        updateChatMetadata(chatId, "Chat started", System.currentTimeMillis(), "System");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send welcome message", e);
                    });
        }
    }

    /**
     * Sends a system message to a chat (e.g., "Report status updated")
     */
    public static void sendSystemMessage(String chatId, String message, String reportId) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        ChatActivity.ChatMessage systemMessage = new ChatActivity.ChatMessage();
        systemMessage.senderId = "system";
        systemMessage.senderName = "System";
        systemMessage.senderRole = "SYSTEM";
        systemMessage.message = message;
        systemMessage.timestamp = System.currentTimeMillis();
        systemMessage.messageType = "system";
        systemMessage.reportId = reportId;

        String messageId = mDatabase.child("chats").child(chatId).child("messages").push().getKey();

        if (messageId != null) {
            mDatabase.child("chats").child(chatId).child("messages").child(messageId)
                    .setValue(systemMessage)
                    .addOnSuccessListener(aVoid -> {
                        // Update chat metadata
                        updateChatMetadata(chatId, systemMessage);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send system message", e);
                    });
        }
    }

    /**
     * Creates a chat when a report is created (between reporter and admin) - FIXED VERSION
     */
    public static void createReportChat(String reportId, String reportTitle, String reporterUid, String reporterName) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        // Find ALL admins to include in chat
        mDatabase.child("users").orderByChild("role").equalTo("ADMIN")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String chatId = generateReportChatId(reportId);

                        // Create the complete chat structure that matches Firebase rules
                        Map<String, Object> completeChat = new HashMap<>();

                        // Create metadata object (this is what the rules expect!)
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("reportId", reportId);
                        metadata.put("reportTitle", reportTitle);
                        metadata.put("createdAt", System.currentTimeMillis());
                        metadata.put("createdBy", reporterUid);
                        metadata.put("chatType", "report");

                        // Add participants - reporter + ALL admins
                        Map<String, Boolean> participants = new HashMap<>();
                        participants.put(reporterUid, true); // Add reporter

                        // Add all admins
                        for (DataSnapshot adminSnapshot : snapshot.getChildren()) {
                            String adminId = adminSnapshot.getKey();
                            if (adminId != null) {
                                participants.put(adminId, true);
                            }
                        }

                        metadata.put("participants", participants);

                        // Add metadata to complete chat structure
                        completeChat.put("metadata", metadata);

                        // Create the chat with the complete structure
                        mDatabase.child("chats").child(chatId).setValue(completeChat)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Chat created successfully for report: " + reportId);

                                    // Send initial system message
                                    sendInitialSystemMessage(chatId, reportTitle, reportId);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create chat", e);
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to find admins", error.toException());
                    }
                });
    }

    /**
     * Sends the initial system message when a report chat is created
     */
    private static void sendInitialSystemMessage(String chatId, String reportTitle, String reportId) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        String messageId = mDatabase.child("chats").child(chatId).child("messages").push().getKey();
        if (messageId != null) {
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("senderId", "system");
            messageData.put("senderName", "System");
            messageData.put("senderRole", "SYSTEM");
            messageData.put("message", "Chat started for report: " + reportTitle + "\n\nUsers and administrators can now communicate about this maintenance request.");
            messageData.put("timestamp", System.currentTimeMillis());
            messageData.put("messageType", "system");
            messageData.put("reportId", reportId);
            messageData.put("readBy", new HashMap<String, Long>());

            mDatabase.child("chats").child(chatId).child("messages").child(messageId)
                    .setValue(messageData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Initial message sent for chat: " + chatId);

                        // Update metadata with last message info
                        updateChatMetadata(chatId, "Chat started for report: " + reportTitle, System.currentTimeMillis(), "System");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send initial message", e);
                    });
        }
    }

    /**
     * Generates a chat ID for a report
     */
    public static String generateReportChatId(String reportId) {
        return "report_" + reportId;
    }

    /**
     * Generates a chat ID for direct messaging between two users
     */
    public static String generateDirectChatId(String userId1, String userId2) {
        String[] uids = {userId1, userId2};
        java.util.Arrays.sort(uids);
        return "direct_" + uids[0] + "_" + uids[1];
    }

    /**
     * Counts unread messages for a user across all chats - WORKING VERSION
     */
    public static void getUnreadMessageCount(String userId, UnreadCountCallback callback) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        mDatabase.child("chats").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int totalUnread = 0;

                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    DataSnapshot participantsSnapshot = chatSnapshot.child("metadata").child("participants");

                    // Check if user is a participant
                    if (participantsSnapshot.hasChild(userId)) {
                        DataSnapshot messagesSnapshot = chatSnapshot.child("messages");

                        for (DataSnapshot messageSnapshot : messagesSnapshot.getChildren()) {
                            String senderId = messageSnapshot.child("senderId").getValue(String.class);
                            DataSnapshot readBySnapshot = messageSnapshot.child("readBy");

                            // If message is not from current user and not read by current user
                            if (!userId.equals(senderId) && !readBySnapshot.hasChild(userId)) {
                                totalUnread++;
                            }
                        }
                    }
                }

                callback.onUnreadCountReceived(totalUnread);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to get unread count", error.toException());
                callback.onUnreadCountReceived(0);
            }
        });
    }

    /**
     * Marks all messages in a chat as read for the current user - WORKING VERSION
     */
    public static void markMessageAsRead(String chatId, String messageId, String userId) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        // Only mark if it's not the user's own message
        mDatabase.child("chats").child(chatId).child("messages").child(messageId).child("senderId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String senderId = snapshot.getValue(String.class);
                        if (senderId != null && !senderId.equals(userId) && !senderId.equals("system")) {
                            // Mark as read
                            mDatabase.child("chats").child(chatId).child("messages").child(messageId)
                                    .child("readBy").child(userId)
                                    .setValue(System.currentTimeMillis());
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check message sender", error.toException());
                    }
                });
    }

    public static void markChatAsRead(String chatId, String userId) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        mDatabase.child("chats").child(chatId).child("messages")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                            String messageId = messageSnapshot.getKey();
                            String senderId = messageSnapshot.child("senderId").getValue(String.class);

                            if (senderId != null && !senderId.equals(userId) && !senderId.equals("system")) {
                                // Mark as read
                                mDatabase.child("chats").child(chatId).child("messages").child(messageId)
                                        .child("readBy").child(userId)
                                        .setValue(System.currentTimeMillis());
                            }

                            // Don't mark own messages as read
                            if (!userId.equals(senderId)) {
                                mDatabase.child("chats").child(chatId).child("messages")
                                        .child(messageId).child("readBy").child(userId)
                                        .setValue(System.currentTimeMillis());
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to mark chat as read", error.toException());
                    }
                });
    }

    /**
     * Adds a participant to an existing chat - WORKING VERSION
     */
    public static void addParticipantToChat(String chatId, String userId) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        mDatabase.child("chats").child(chatId).child("metadata").child("participants")
                .child(userId).setValue(true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Participant added to chat successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add participant to chat", e);
                });
    }

    /**
     * Updates chat metadata with the latest message info - OVERLOADED METHODS
     */
    private static void updateChatMetadata(String chatId, String lastMessage, long timestamp, String senderName) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        Map<String, Object> metadataUpdates = new HashMap<>();
        metadataUpdates.put("lastMessage", lastMessage);
        metadataUpdates.put("lastMessageTime", timestamp);
        metadataUpdates.put("lastMessageSender", senderName);

        mDatabase.child("chats").child(chatId).child("metadata").updateChildren(metadataUpdates);
    }

    private static void updateChatMetadata(String chatId, ChatActivity.ChatMessage lastMessage) {
        updateChatMetadata(chatId, lastMessage.message, lastMessage.timestamp, lastMessage.senderName);
    }

    /**
     * Gets the display name for a chat based on its participants and type - WORKING VERSION
     */
    public static void getChatDisplayName(String chatId, String currentUserId, ChatDisplayNameCallback callback) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        mDatabase.child("chats").child(chatId).child("metadata")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String reportId = snapshot.child("reportId").getValue(String.class);
                        String reportTitle = snapshot.child("reportTitle").getValue(String.class);

                        if (reportId != null) {
                            // Report-based chat
                            String displayName = reportTitle != null ? reportTitle : "Report #" + reportId.substring(reportId.length() - 6);
                            callback.onDisplayNameReceived(displayName, "Maintenance Request");
                        } else {
                            // Direct chat - find other participant
                            DataSnapshot participantsSnapshot = snapshot.child("participants");
                            for (DataSnapshot participantSnapshot : participantsSnapshot.getChildren()) {
                                String participantId = participantSnapshot.getKey();
                                if (!participantId.equals(currentUserId)) {
                                    // Get participant details
                                    mDatabase.child("users").child(participantId)
                                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                                @Override
                                                public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                                    String displayName = userSnapshot.child("displayName").getValue(String.class);
                                                    String role = userSnapshot.child("role").getValue(String.class);

                                                    callback.onDisplayNameReceived(
                                                            displayName != null ? displayName : "Unknown User",
                                                            role != null ? role : "User"
                                                    );
                                                }

                                                @Override
                                                public void onCancelled(@NonNull DatabaseError error) {
                                                    callback.onDisplayNameReceived("Unknown User", "User");
                                                }
                                            });
                                    break;
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onDisplayNameReceived("Unknown Chat", "");
                    }
                });
    }

    /**
     * Deletes a chat and all its messages - WORKING VERSION
     */
    public static void deleteChat(String chatId, DeleteChatCallback callback) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        mDatabase.child("chats").child(chatId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Chat deleted successfully");
                    callback.onChatDeleted(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete chat", e);
                    callback.onChatDeleted(false);
                });
    }

    /**
     * Checks if a user has permission to access a specific chat - WORKING VERSION
     */
    public static void checkChatPermission(String chatId, String userId, PermissionCallback callback) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        mDatabase.child("chats").child(chatId).child("metadata").child("participants")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean hasPermission = snapshot.hasChild(userId);
                        callback.onPermissionChecked(hasPermission);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onPermissionChecked(false);
                    }
                });
    }

    /**
     * Debug method to check chat participants and permissions
     */
    public static void debugChatPermissions(String chatId, String currentUserId) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        Log.d(TAG, "Debugging chat permissions for chat: " + chatId + ", user: " + currentUserId);

        mDatabase.child("chats").child(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Chat exists: " + snapshot.exists());

                if (snapshot.exists()) {
                    DataSnapshot metadata = snapshot.child("metadata");
                    DataSnapshot participants = metadata.child("participants");

                    Log.d(TAG, "Metadata exists: " + metadata.exists());
                    Log.d(TAG, "Participants exists: " + participants.exists());
                    Log.d(TAG, "Current user is participant: " + participants.hasChild(currentUserId));

                    // Log all participants
                    for (DataSnapshot participantSnapshot : participants.getChildren()) {
                        String participantId = participantSnapshot.getKey();
                        Boolean isParticipant = participantSnapshot.getValue(Boolean.class);
                        Log.d(TAG, "Participant: " + participantId + " = " + isParticipant);
                    }

                    // Log report info
                    String reportId = metadata.child("reportId").getValue(String.class);
                    String reportTitle = metadata.child("reportTitle").getValue(String.class);
                    Log.d(TAG, "Report ID: " + reportId);
                    Log.d(TAG, "Report Title: " + reportTitle);

                    // Check messages
                    DataSnapshot messages = snapshot.child("messages");
                    Log.d(TAG, "Messages count: " + messages.getChildrenCount());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Debug query cancelled", error.toException());
            }
        });
    }

    // Callback interfaces
    public interface UnreadCountCallback {
        void onUnreadCountReceived(int count);
    }

    public interface ChatDisplayNameCallback {
        void onDisplayNameReceived(String displayName, String subtitle);
    }

    public interface DeleteChatCallback {
        void onChatDeleted(boolean success);
    }

    public interface PermissionCallback {
        void onPermissionChecked(boolean hasPermission);
    }
}