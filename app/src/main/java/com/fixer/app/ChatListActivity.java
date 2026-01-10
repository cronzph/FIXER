package com.fixer.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatListActivity extends AppCompatActivity {

    private static final String TAG = "ChatListActivity";

    // UI Components
    private RecyclerView chatsRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SearchView searchView;
    private TextView emptyStateText;
    private FloatingActionButton fabNewChat;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;

    // Data
    private List<ChatItem> allChats;
    private List<ChatItem> filteredChats;
    private ChatListAdapter chatAdapter;
    private String searchQuery = "";
    private String currentUserRole;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            finish();
            return;
        }

        // Setup toolbar
        setupToolbar();

        // Initialize views
        initializeViews();

        // Setup RecyclerView
        setupRecyclerView();

        // Setup listeners
        setupListeners();

        // Load user role and chats
        loadCurrentUserData();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Messages");
        }
    }

    private void initializeViews() {
        chatsRecyclerView = findViewById(R.id.chatsRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        emptyStateText = findViewById(R.id.emptyStateText);
        fabNewChat = findViewById(R.id.fabNewChat);

        // Setup swipe refresh
        swipeRefreshLayout.setOnRefreshListener(this::loadChats);
        swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light
        );
    }

    private void setupRecyclerView() {
        allChats = new ArrayList<>();
        filteredChats = new ArrayList<>();
        chatAdapter = new ChatListAdapter(filteredChats);

        chatsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatsRecyclerView.setAdapter(chatAdapter);
    }

    private void setupListeners() {
        fabNewChat.setOnClickListener(v -> showNewChatOptions());
    }

    private void loadCurrentUserData() {
        mDatabase.child("users").child(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            currentUserRole = snapshot.child("role").getValue(String.class);
                            loadChats();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load user data", error.toException());
                        loadChats(); // Load chats anyway
                    }
                });
    }

    private void showNewChatOptions() {
        String[] options;

        // Fixed: Only USER and ADMIN roles (removed MODERATOR)
        if ("ADMIN".equals(currentUserRole)) {
            options = new String[]{"Chat with User", "Browse Reports for Chat"};
        } else {
            options = new String[]{"Chat with Admin", "Chat about My Report", "Create Direct Chat"};
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Start New Chat")
                .setItems(options, (dialog, which) -> {
                    if ("ADMIN".equals(currentUserRole)) {
                        if (which == 0) {
                            showUserSelection();
                        } else {
                            showReportSelection();
                        }
                    } else {
                        switch (which) {
                            case 0: startChatWithAdmin(); break;
                            case 1: showMyReportsForChat(); break;
                            case 2: showCreateDirectChat(); break;
                        }
                    }
                })
                .show();
    }

    private void loadChats() {
        swipeRefreshLayout.setRefreshing(true);

        mDatabase.child("chats").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allChats.clear();

                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    DataSnapshot metadataSnapshot = chatSnapshot.child("metadata");
                    DataSnapshot participantsSnapshot = metadataSnapshot.child("participants");

                    // Check if current user is a participant
                    if (participantsSnapshot.hasChild(currentUser.getUid())) {
                        ChatItem chatItem = new ChatItem();
                        chatItem.chatId = chatId;
                        chatItem.lastMessage = metadataSnapshot.child("lastMessage").getValue(String.class);

                        Long lastMessageTime = metadataSnapshot.child("lastMessageTime").getValue(Long.class);
                        chatItem.lastMessageTime = lastMessageTime != null ? lastMessageTime : 0L;

                        chatItem.lastMessageSender = metadataSnapshot.child("lastMessageSender").getValue(String.class);
                        chatItem.reportId = metadataSnapshot.child("reportId").getValue(String.class);

                        // Get other participants
                        List<String> otherParticipants = new ArrayList<>();
                        for (DataSnapshot participantSnapshot : participantsSnapshot.getChildren()) {
                            String participantId = participantSnapshot.getKey();
                            if (!participantId.equals(currentUser.getUid())) {
                                otherParticipants.add(participantId);
                            }
                        }

                        // Set chat title and other participant info
                        setChatTitle(chatItem, otherParticipants);

                        // Count unread messages with improved logic
                        countUnreadMessages(chatItem, chatSnapshot.child("messages"));

                        allChats.add(chatItem);
                    }
                }

                // Sort by last message time (newest first)
                allChats.sort((a, b) -> Long.compare(b.lastMessageTime, a.lastMessageTime));

                applyFilter();
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load chats", error.toException());
                swipeRefreshLayout.setRefreshing(false);
                showToast("Failed to load chats");
            }
        });
    }

    private void setChatTitle(ChatItem chatItem, List<String> otherParticipants) {
        if (chatItem.reportId != null) {
            // Chat related to a report - get report title
            mDatabase.child("maintenance_reports").child(chatItem.reportId.replace("report_", ""))
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                String reportTitle = snapshot.child("title").getValue(String.class);
                                chatItem.chatTitle = reportTitle != null ? reportTitle : "Report #" + chatItem.reportId.substring(chatItem.reportId.length() - 6);
                            } else {
                                chatItem.chatTitle = "Report #" + chatItem.reportId.substring(chatItem.reportId.length() - 6);
                            }
                            chatItem.chatSubtitle = "Maintenance Request";
                            chatAdapter.notifyDataSetChanged();
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            chatItem.chatTitle = "Report #" + chatItem.reportId.substring(chatItem.reportId.length() - 6);
                            chatItem.chatSubtitle = "Maintenance Request";
                        }
                    });
        } else if (!otherParticipants.isEmpty()) {
            // Direct chat - get participant name
            String participantId = otherParticipants.get(0);
            mDatabase.child("users").child(participantId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                String displayName = snapshot.child("displayName").getValue(String.class);
                                String role = snapshot.child("role").getValue(String.class);

                                chatItem.chatTitle = displayName != null ? displayName : "Unknown User";
                                chatItem.chatSubtitle = role != null ? role : "User";
                                chatItem.otherParticipantId = participantId;

                                chatAdapter.notifyDataSetChanged();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            chatItem.chatTitle = "Unknown User";
                            chatItem.chatSubtitle = "User";
                        }
                    });
        } else {
            chatItem.chatTitle = "General Chat";
            chatItem.chatSubtitle = "Group";
        }
    }

    // Enhanced unread message counting with better logic
    private void countUnreadMessages(ChatItem chatItem, DataSnapshot messagesSnapshot) {
        int unreadCount = 0;

        for (DataSnapshot messageSnapshot : messagesSnapshot.getChildren()) {
            String senderId = messageSnapshot.child("senderId").getValue(String.class);
            DataSnapshot readBySnapshot = messageSnapshot.child("readBy");

            // Skip system messages and own messages
            if ("system".equals(senderId) || currentUser.getUid().equals(senderId)) {
                continue;
            }

            // If message is not read by current user, count as unread
            if (!readBySnapshot.hasChild(currentUser.getUid())) {
                unreadCount++;
            }
        }

        chatItem.unreadCount = unreadCount;
    }

    private void showUserSelection() {
        mDatabase.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> userNames = new ArrayList<>();
                List<String> userIds = new ArrayList<>();

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String userId = userSnapshot.getKey();
                    if (!userId.equals(currentUser.getUid())) {
                        String displayName = userSnapshot.child("displayName").getValue(String.class);
                        String role = userSnapshot.child("role").getValue(String.class);
                        String campus = userSnapshot.child("campus").getValue(String.class);

                        // Only show USER and ADMIN roles
                        if ("USER".equals(role) || "ADMIN".equals(role)) {
                            String displayText = (displayName != null ? displayName : "Unknown") +
                                    (role != null ? " (" + role + ")" : "") +
                                    (campus != null ? " - " + campus : "");

                            userNames.add(displayText);
                            userIds.add(userId);
                        }
                    }
                }

                if (userIds.isEmpty()) {
                    showToast("No other users found");
                    return;
                }

                String[] userArray = userNames.toArray(new String[0]);

                new androidx.appcompat.app.AlertDialog.Builder(ChatListActivity.this)
                        .setTitle("Select User to Chat With")
                        .setItems(userArray, (dialog, which) -> {
                            String selectedUserId = userIds.get(which);
                            String selectedUserName = userNames.get(which);

                            // Extract just the name part
                            String justName = selectedUserName.split(" \\(")[0];

                            startDirectChatSafely(selectedUserId, justName);
                        })
                        .show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showToast("Failed to load users");
            }
        });
    }

    private void showReportSelection() {
        mDatabase.child("maintenance_reports")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> reportTitles = new ArrayList<>();
                        List<String> reportIds = new ArrayList<>();

                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            String title = reportSnapshot.child("title").getValue(String.class);
                            String status = reportSnapshot.child("status").getValue(String.class);
                            String campus = reportSnapshot.child("campus").getValue(String.class);
                            String reportedBy = reportSnapshot.child("reportedBy").getValue(String.class);

                            String displayText = (title != null ? title : "Untitled Report") +
                                    " (" + (status != null ? status.toUpperCase() : "UNKNOWN") + ")" +
                                    (campus != null ? " - " + campus : "") +
                                    (reportedBy != null ? " by " + reportedBy : "");

                            reportTitles.add(displayText);
                            reportIds.add(reportId);
                        }

                        if (reportIds.isEmpty()) {
                            showToast("No reports found");
                            return;
                        }

                        String[] reportArray = reportTitles.toArray(new String[0]);

                        new androidx.appcompat.app.AlertDialog.Builder(ChatListActivity.this)
                                .setTitle("Select Report for Chat")
                                .setItems(reportArray, (dialog, which) -> {
                                    String selectedReportId = reportIds.get(which);
                                    startReportChatSafely(selectedReportId);
                                })
                                .show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showToast("Failed to load reports");
                    }
                });
    }

    private void startChatWithAdmin() {
        mDatabase.child("users").orderByChild("role").equalTo("ADMIN")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.hasChildren()) {
                            List<String> adminNames = new ArrayList<>();
                            List<String> adminIds = new ArrayList<>();

                            for (DataSnapshot adminSnapshot : snapshot.getChildren()) {
                                String adminId = adminSnapshot.getKey();
                                String adminName = adminSnapshot.child("displayName").getValue(String.class);
                                String campus = adminSnapshot.child("campus").getValue(String.class);

                                adminNames.add((adminName != null ? adminName : "Admin") +
                                        (campus != null ? " - " + campus : ""));
                                adminIds.add(adminId);
                            }

                            if (adminIds.size() == 1) {
                                // Only one admin, start chat directly
                                startDirectChatSafely(adminIds.get(0), adminNames.get(0));
                            } else {
                                // Multiple admins, let user choose
                                String[] adminArray = adminNames.toArray(new String[0]);
                                new androidx.appcompat.app.AlertDialog.Builder(ChatListActivity.this)
                                        .setTitle("Select Admin to Chat With")
                                        .setItems(adminArray, (dialog, which) -> {
                                            startDirectChatSafely(adminIds.get(which), adminNames.get(which));
                                        })
                                        .show();
                            }
                        } else {
                            showToast("No admin available for chat");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showToast("Failed to find admin");
                    }
                });
    }

    private void showMyReportsForChat() {
        mDatabase.child("maintenance_reports")
                .orderByChild("reportedByUid").equalTo(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> reportTitles = new ArrayList<>();
                        List<String> reportIds = new ArrayList<>();

                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            String title = reportSnapshot.child("title").getValue(String.class);
                            String status = reportSnapshot.child("status").getValue(String.class);
                            Long createdAt = reportSnapshot.child("createdAt").getValue(Long.class);

                            String dateStr = "";
                            if (createdAt != null) {
                                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                                dateStr = " - " + sdf.format(new Date(createdAt));
                            }

                            String displayText = (title != null ? title : "Untitled Report") +
                                    " (" + (status != null ? status.toUpperCase() : "UNKNOWN") + ")" + dateStr;

                            reportTitles.add(displayText);
                            reportIds.add(reportId);
                        }

                        if (reportIds.isEmpty()) {
                            showToast("You have no reports to chat about");
                            return;
                        }

                        String[] reportArray = reportTitles.toArray(new String[0]);

                        new androidx.appcompat.app.AlertDialog.Builder(ChatListActivity.this)
                                .setTitle("Select Your Report to Chat About")
                                .setItems(reportArray, (dialog, which) -> {
                                    String selectedReportId = reportIds.get(which);
                                    startReportChatSafely(selectedReportId);
                                })
                                .show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showToast("Failed to load your reports");
                    }
                });
    }

    private void showCreateDirectChat() {
        mDatabase.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> userNames = new ArrayList<>();
                List<String> userIds = new ArrayList<>();

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String userId = userSnapshot.getKey();
                    if (!userId.equals(currentUser.getUid())) {
                        String displayName = userSnapshot.child("displayName").getValue(String.class);
                        String role = userSnapshot.child("role").getValue(String.class);
                        String campus = userSnapshot.child("campus").getValue(String.class);

                        // Only show USER and ADMIN roles, exclude SYSTEM
                        if ("USER".equals(role) || "ADMIN".equals(role)) {
                            String displayText = (displayName != null ? displayName : "Unknown User") +
                                    ("ADMIN".equals(role) ? " (ADMIN)" : "") +
                                    (campus != null ? " - " + campus : "");

                            userNames.add(displayText);
                            userIds.add(userId);
                        }
                    }
                }

                if (userIds.isEmpty()) {
                    showToast("No other users found");
                    return;
                }

                String[] userArray = userNames.toArray(new String[0]);

                new androidx.appcompat.app.AlertDialog.Builder(ChatListActivity.this)
                        .setTitle("Select User to Chat With")
                        .setItems(userArray, (dialog, which) -> {
                            String selectedUserId = userIds.get(which);
                            String selectedUserName = userNames.get(which).split(" \\(")[0]; // Extract just the name

                            startDirectChatSafely(selectedUserId, selectedUserName);
                        })
                        .show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showToast("Failed to load users");
            }
        });
    }

    private void startDirectChatSafely(String recipientUid, String recipientName) {
        // Show progress
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Starting chat...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Use ChatHelper's enhanced startDirectChat method
        new android.os.Handler().postDelayed(() -> {
            progressDialog.dismiss();
            ChatHelper.startDirectChat(this, recipientUid, recipientName);
        }, 1000);
    }

    private void startReportChatSafely(String reportId) {
        // Show progress
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Opening report chat...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Use ChatHelper's enhanced startReportChat method
        new android.os.Handler().postDelayed(() -> {
            progressDialog.dismiss();
            ChatHelper.startReportChat(this, reportId);
        }, 1000);
    }

    private void applyFilter() {
        filteredChats.clear();

        if (searchQuery.isEmpty()) {
            filteredChats.addAll(allChats);
        } else {
            for (ChatItem chat : allChats) {
                if (chat.chatTitle != null && chat.chatTitle.toLowerCase().contains(searchQuery.toLowerCase()) ||
                        (chat.lastMessage != null && chat.lastMessage.toLowerCase().contains(searchQuery.toLowerCase()))) {
                    filteredChats.add(chat);
                }
            }
        }

        chatAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredChats.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            chatsRecyclerView.setVisibility(View.GONE);

            if (searchQuery.isEmpty()) {
                emptyStateText.setText("No conversations yet.\nTap + to start a new chat.");
            } else {
                emptyStateText.setText("No chats found for \"" + searchQuery + "\"");
            }
        } else {
            emptyStateText.setVisibility(View.GONE);
            chatsRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_list_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            searchView = (SearchView) searchItem.getActionView();

            if (searchView != null) {
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        searchQuery = newText;
                        applyFilter();
                        return true;
                    }
                });
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_refresh) {
            loadChats();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh chats when returning to this activity to update read status
        loadChats();
    }

    // ChatItem model class
    public static class ChatItem {
        public String chatId;
        public String chatTitle;
        public String chatSubtitle;
        public String lastMessage;
        public long lastMessageTime;
        public String lastMessageSender;
        public String reportId;
        public String otherParticipantId;
        public int unreadCount;
    }

    // ChatList Adapter class with improved unread handling
    private class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatViewHolder> {
        private List<ChatItem> chats;
        private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

        public ChatListAdapter(List<ChatItem> chats) {
            this.chats = chats;
        }

        @NonNull
        @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat, parent, false);
            return new ChatViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            ChatItem chat = chats.get(position);

            holder.chatTitle.setText(chat.chatTitle != null ? chat.chatTitle : "Unknown Chat");
            holder.chatSubtitle.setText(chat.chatSubtitle != null ? chat.chatSubtitle : "");

            // Set last message with better formatting
            if (chat.lastMessage != null && !chat.lastMessage.isEmpty()) {
                String displayMessage = chat.lastMessage;

                // Only show sender name for group chats or if it's not the current user
                if (chat.lastMessageSender != null &&
                        !chat.lastMessageSender.equals("System") &&
                        !chat.lastMessageSender.equals(currentUser.getDisplayName())) {
                    displayMessage = chat.lastMessageSender + ": " + displayMessage;
                }

                if (displayMessage.length() > 50) {
                    displayMessage = displayMessage.substring(0, 47) + "...";
                }

                holder.lastMessage.setText(displayMessage);
                holder.lastMessage.setVisibility(View.VISIBLE);
            } else {
                holder.lastMessage.setText("No messages yet");
                holder.lastMessage.setVisibility(View.VISIBLE);
            }

            // Set time with better formatting
            if (chat.lastMessageTime > 0) {
                Date messageDate = new Date(chat.lastMessageTime);
                Date today = new Date();

                if (dateFormat.format(messageDate).equals(dateFormat.format(today))) {
                    holder.timeText.setText(timeFormat.format(messageDate));
                } else {
                    holder.timeText.setText(dateFormat.format(messageDate));
                }
            } else {
                holder.timeText.setText("");
            }

            // Enhanced unread badge with better styling
            if (chat.unreadCount > 0) {
                holder.unreadBadge.setText(chat.unreadCount > 99 ? "99+" : String.valueOf(chat.unreadCount));
                holder.unreadBadge.setVisibility(View.VISIBLE);

                // Make chat title bold if there are unread messages
                holder.chatTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.lastMessage.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                holder.unreadBadge.setVisibility(View.GONE);

                // Normal text style for read chats
                holder.chatTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.lastMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
            }

            // Click listener with read marking
            holder.itemView.setOnClickListener(v -> {
                // Mark messages as read when entering chat
                if (chat.unreadCount > 0) {
                    ChatHelper.markChatAsRead(chat.chatId, currentUser.getUid());
                }
                ChatHelper.markChatAsRead(chat.chatId, currentUser.getUid());
                Intent intent = new Intent(ChatListActivity.this, ChatActivity.class);

                if (chat.reportId != null) {
                    intent.putExtra(ChatActivity.EXTRA_REPORT_ID, chat.reportId.replace("report_", ""));
                } else if (chat.otherParticipantId != null) {
                    intent.putExtra(ChatActivity.EXTRA_RECIPIENT_UID, chat.otherParticipantId);
                    intent.putExtra(ChatActivity.EXTRA_RECIPIENT_NAME, chat.chatTitle);
                }

                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return chats.size();
        }

        class ChatViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView chatCard;
            TextView chatTitle;
            TextView chatSubtitle;
            TextView lastMessage;
            TextView timeText;
            TextView unreadBadge;

            ChatViewHolder(@NonNull View itemView) {
                super(itemView);
                chatCard = itemView.findViewById(R.id.chatCard);
                chatTitle = itemView.findViewById(R.id.chatTitle);
                chatSubtitle = itemView.findViewById(R.id.chatSubtitle);
                lastMessage = itemView.findViewById(R.id.lastMessage);
                timeText = itemView.findViewById(R.id.timeText);
                unreadBadge = itemView.findViewById(R.id.unreadBadge);
            }
        }
    }
}