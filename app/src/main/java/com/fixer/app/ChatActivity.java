package com.fixer.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final int REQUEST_IMAGE_PICK = 101;
    private static final int REQUEST_CAMERA = 103;
    private static final int PERMISSION_REQUEST_CODE = 104;
    private static final int MAX_IMAGE_SIZE = 800; // Max width/height in pixels
    private static final int IMAGE_QUALITY = 70; // JPEG quality 0-100

    // Intent extras
    public static final String EXTRA_REPORT_ID = "report_id";
    public static final String EXTRA_RECIPIENT_UID = "recipient_uid";
    public static final String EXTRA_RECIPIENT_NAME = "recipient_name";

    // UI Components
    private RecyclerView messagesRecyclerView;
    private EditText messageEditText;
    private MaterialButton sendButton;
    private MaterialButton attachButton;
    private TextView headerInfoText;
    private LinearLayout typingIndicator;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;

    // Data
    private List<ChatMessage> messagesList;
    private ChatMessageAdapter messagesAdapter;
    private String reportId;
    private String recipientUid;
    private String recipientName;
    private String chatId;
    private String currentUserName;
    private String currentUserRole;

    // Listeners
    private ChildEventListener messagesListener;
    private ValueEventListener typingListener;

    // Upload progress dialog
    private android.app.ProgressDialog uploadProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_chat);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            finish();
            return;
        }

        // Get intent extras
        getIntentExtras();

        // Setup toolbar
        setupToolbar();

        // Initialize views
        initializeViews();

        // Setup RecyclerView
        setupRecyclerView();

        // Load user data
        loadCurrentUserData();

        // Setup listeners
        setupClickListeners();

        // Generate chat ID
        generateChatId();
    }

    private void getIntentExtras() {
        reportId = getIntent().getStringExtra(EXTRA_REPORT_ID);
        recipientUid = getIntent().getStringExtra(EXTRA_RECIPIENT_UID);
        recipientName = getIntent().getStringExtra(EXTRA_RECIPIENT_NAME);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chat");
        }
    }

    private void initializeViews() {
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        attachButton = findViewById(R.id.attachButton);
        headerInfoText = findViewById(R.id.headerInfoText);
        typingIndicator = findViewById(R.id.typingIndicator);

        // Set header info
        if (reportId != null) {
            headerInfoText.setText("Chat about Report #" + reportId.substring(reportId.length() - 6));
        } else if (recipientName != null) {
            headerInfoText.setText("Chat with " + recipientName);
        }
    }

    private void setupRecyclerView() {
        messagesList = new ArrayList<>();
        messagesAdapter = new ChatMessageAdapter(messagesList, currentUser.getUid());

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);

        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setAdapter(messagesAdapter);
    }

    private void loadCurrentUserData() {
        mDatabase.child("users").child(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            currentUserName = snapshot.child("displayName").getValue(String.class);
                            currentUserRole = snapshot.child("role").getValue(String.class);

                            if (currentUserName == null) {
                                currentUserName = currentUser.getEmail();
                            }

                            loadMessages();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load user data", error.toException());
                    }
                });
    }

    private void setupClickListeners() {
        sendButton.setOnClickListener(v -> sendMessage());

        attachButton.setOnClickListener(v -> showMediaOptions());

        messageEditText.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });

        messageEditText.addTextChangedListener(new TextWatcher() {
            private Timer typingTimer;

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateTypingStatus(true);
                setupTypingIndicator();

                if (typingTimer != null) {
                    typingTimer.cancel();
                }

                typingTimer = new Timer();
                typingTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        updateTypingStatus(false);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            typingIndicator.setVisibility(View.GONE);
                        });
                    }
                }, 2000);
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        messageEditText.setOnFocusChangeListener((v, hasFocus) -> {
            updateTypingStatus(hasFocus);
            setupTypingIndicator();
        });
    }

    private void showMediaOptions() {
        String[] options = {"Take Photo", "Choose Image from Gallery"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Send Image")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openCamera();
                            break;
                        case 1:
                            pickImage();
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openCamera() {
        if (checkPermissions()) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, REQUEST_CAMERA);
            } else {
                showToast("No camera app found");
            }
        }
    }

    private void pickImage() {
        if (checkPermissions()) {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_IMAGE_PICK);
        }
    }

    private boolean checkPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                showToast("Permissions granted");
            } else {
                showToast("Some permissions were denied. Image upload may not work.");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            try {
                Bitmap bitmap = null;

                if (requestCode == REQUEST_CAMERA) {
                    // Get bitmap from camera
                    bitmap = (Bitmap) data.getExtras().get("data");
                } else if (requestCode == REQUEST_IMAGE_PICK) {
                    // Get bitmap from gallery
                    Uri imageUri = data.getData();
                    if (imageUri != null) {
                        bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    }
                }

                if (bitmap != null) {
                    processAndSendImage(bitmap);
                } else {
                    showToast("Failed to get image");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error processing image", e);
                showToast("Error processing image: " + e.getMessage());
            }
        }
    }

    private void processAndSendImage(Bitmap originalBitmap) {
        // Show progress dialog
        uploadProgressDialog = new android.app.ProgressDialog(this);
        uploadProgressDialog.setMessage("Processing image...");
        uploadProgressDialog.setCancelable(false);
        uploadProgressDialog.show();

        // Process image in background thread
        new Thread(() -> {
            try {
                // Resize image if needed
                Bitmap resizedBitmap = resizeImage(originalBitmap, MAX_IMAGE_SIZE);

                // Convert to Base64
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, baos);
                byte[] imageBytes = baos.toByteArray();
                String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);

                // Check size (Firebase has 10MB limit per value)
                int sizeInKB = base64Image.length() / 1024;
                Log.d(TAG, "Image size: " + sizeInKB + " KB");

                if (sizeInKB > 1024) { // If larger than 1MB, warn user
                    runOnUiThread(() -> {
                        uploadProgressDialog.dismiss();
                        showToast("Image too large. Try a smaller image or lower quality photo.");
                    });
                    return;
                }

                // Send on UI thread
                runOnUiThread(() -> {
                    uploadProgressDialog.setMessage("Sending image...");
                    sendImageMessage(base64Image);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error processing image", e);
                runOnUiThread(() -> {
                    uploadProgressDialog.dismiss();
                    showToast("Failed to process image: " + e.getMessage());
                });
            }
        }).start();
    }

    private Bitmap resizeImage(Bitmap original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();

        // Check if resize is needed
        if (width <= maxSize && height <= maxSize) {
            return original;
        }

        float ratio = Math.min(
                (float) maxSize / width,
                (float) maxSize / height
        );

        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    private void sendImageMessage(String base64Image) {
        if (chatId == null) {
            uploadProgressDialog.dismiss();
            showToast("Error: Chat not available");
            return;
        }

        updateTypingStatus(false);

        ChatMessage message = new ChatMessage();
        message.senderId = currentUser.getUid();
        message.senderName = currentUserName != null ? currentUserName : currentUser.getEmail();
        message.senderRole = currentUserRole != null ? currentUserRole : "USER";
        message.message = base64Image; // Store Base64 image data
        message.timestamp = System.currentTimeMillis();
        message.messageType = "image";
        message.reportId = reportId;

        String messageId = mDatabase.child("chats").child(chatId).child("messages").push().getKey();

        if (messageId != null) {
            mDatabase.child("chats").child(chatId).child("messages").child(messageId).setValue(message)
                    .addOnSuccessListener(aVoid -> {
                        uploadProgressDialog.dismiss();
                        Log.d(TAG, "Image message sent successfully");

                        // Update metadata with text instead of image data
                        ChatMessage metadataMessage = new ChatMessage();
                        metadataMessage.senderId = message.senderId;
                        metadataMessage.senderName = message.senderName;
                        metadataMessage.senderRole = message.senderRole;
                        metadataMessage.message = "📷 Image";
                        metadataMessage.timestamp = message.timestamp;
                        metadataMessage.messageType = "image";
                        updateChatMetadata(metadataMessage);
                    })
                    .addOnFailureListener(e -> {
                        uploadProgressDialog.dismiss();
                        Log.e(TAG, "Failed to send image message", e);
                        showToast("Failed to send image: " + e.getMessage());
                    });
        } else {
            uploadProgressDialog.dismiss();
            showToast("Error generating message ID");
        }
    }

    private void generateChatId() {
        if (reportId != null) {
            chatId = "report_" + reportId;
        } else if (recipientUid != null) {
            String[] uids = {currentUser.getUid(), recipientUid};
            java.util.Arrays.sort(uids);
            chatId = "direct_" + uids[0] + "_" + uids[1];
        } else {
            chatId = "general_" + System.currentTimeMillis();
        }
    }

    private void loadMessages() {
        if (chatId == null) {
            Log.e(TAG, "Chat ID is null, cannot load messages");
            showToast("Error: Invalid chat ID");
            return;
        }

        Log.d(TAG, "Loading messages for chat: " + chatId);

        mDatabase.child("chats").child(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.d(TAG, "Chat doesn't exist, creating it...");
                    createMissingChat();
                } else {
                    Log.d(TAG, "Chat exists, loading messages...");
                    setupMessageListener();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check chat existence", error.toException());
                showToast("Failed to load chat: " + error.getMessage());
            }
        });
    }

    private void createMissingChat() {
        if (reportId != null) {
            Log.d(TAG, "Creating missing report chat for: " + reportId);

            mDatabase.child("maintenance_reports").child(reportId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                String title = snapshot.child("title").getValue(String.class);
                                String reporterUid = snapshot.child("reportedByUid").getValue(String.class);
                                String reporterName = snapshot.child("reportedBy").getValue(String.class);

                                if (title != null && reporterUid != null && reporterName != null) {
                                    ChatHelper.createReportChat(reportId, title, reporterUid, reporterName);

                                    new android.os.Handler().postDelayed(() -> {
                                        setupMessageListener();
                                    }, 2000);

                                    showToast("Setting up chat...");
                                } else {
                                    Log.e(TAG, "Missing report data for chat creation");
                                    showToast("Error: Cannot create chat for this report");
                                }
                            } else {
                                Log.e(TAG, "Report not found: " + reportId);
                                showToast("Error: Report not found");
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Failed to load report data", error.toException());
                            showToast("Failed to load report data: " + error.getMessage());
                        }
                    });
        } else {
            Log.e(TAG, "Cannot create chat: no report ID provided");
            showToast("Error: Cannot create chat - missing report information");
        }
    }

    private void setupMessageListener() {
        DatabaseReference chatRef = mDatabase.child("chats").child(chatId).child("messages");

        messagesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                ChatMessage message = snapshot.getValue(ChatMessage.class);
                if (message != null) {
                    message.messageId = snapshot.getKey();
                    messagesList.add(message);
                    messagesAdapter.notifyItemInserted(messagesList.size() - 1);
                    messagesRecyclerView.scrollToPosition(messagesList.size() - 1);

                    if (!message.senderId.equals(currentUser.getUid())) {
                        markMessageAsRead(message.messageId);
                    }

                    Log.d(TAG, "Message loaded: " + message.messageType);
                } else {
                    Log.w(TAG, "Received null message from snapshot");
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                ChatMessage message = snapshot.getValue(ChatMessage.class);
                if (message != null) {
                    message.messageId = snapshot.getKey();

                    for (int i = 0; i < messagesList.size(); i++) {
                        if (messagesList.get(i).messageId.equals(message.messageId)) {
                            messagesList.set(i, message);
                            messagesAdapter.notifyItemChanged(i);
                            Log.d(TAG, "Message updated: " + message.messageId);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String messageId = snapshot.getKey();
                if (messageId != null) {
                    for (int i = 0; i < messagesList.size(); i++) {
                        if (messagesList.get(i).messageId.equals(messageId)) {
                            messagesList.remove(i);
                            messagesAdapter.notifyItemRemoved(i);
                            Log.d(TAG, "Message removed: " + messageId);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Message listener cancelled: " + error.getMessage(), error.toException());
                showToast("Failed to load chat messages: " + error.getMessage());

                new android.os.Handler().postDelayed(() -> {
                    Log.d(TAG, "Attempting to reconnect message listener...");
                    setupMessageListener();
                }, 3000);
            }
        };

        chatRef.addChildEventListener(messagesListener);
        Log.d(TAG, "Message listener attached successfully");

        setupTypingIndicator();
    }

    private void setupTypingIndicator() {
        DatabaseReference typingRef = mDatabase.child("chats").child(chatId).child("typing");

        typingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean showTyping = false;
                String typingUserName = "";

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String userId = userSnapshot.getKey();
                    Boolean isTyping = userSnapshot.getValue(Boolean.class);

                    if (!userId.equals(currentUser.getUid()) && Boolean.TRUE.equals(isTyping)) {
                        showTyping = true;
                        if (userId.equals(recipientUid) && recipientName != null) {
                            typingUserName = recipientName;
                        } else {
                            typingUserName = "Someone";
                        }
                        break;
                    }
                }

                if (showTyping) {
                    TextView typingText = typingIndicator.findViewById(R.id.typingText);
                    typingText.setText(typingUserName + " is typing...");
                    typingIndicator.setVisibility(View.VISIBLE);
                } else {
                    typingIndicator.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Typing indicator error", error.toException());
            }
        };

        mDatabase.child("chats").child(chatId).child("typing").addValueEventListener(typingListener);
    }

    private void sendMessage() {
        String messageText = messageEditText.getText().toString().trim();

        if (TextUtils.isEmpty(messageText)) {
            return;
        }

        if (chatId == null) {
            Log.e(TAG, "Cannot send message: chat ID is null");
            showToast("Error: Chat not available");
            return;
        }

        messageEditText.setText("");
        updateTypingStatus(false);

        ChatMessage message = new ChatMessage();
        message.senderId = currentUser.getUid();
        message.senderName = currentUserName != null ? currentUserName : currentUser.getEmail();
        message.senderRole = currentUserRole != null ? currentUserRole : "USER";
        message.message = messageText;
        message.timestamp = System.currentTimeMillis();
        message.messageType = "text";
        message.reportId = reportId;

        String messageId = mDatabase.child("chats").child(chatId).child("messages").push().getKey();

        if (messageId != null) {
            Log.d(TAG, "Sending message to chat: " + chatId);

            mDatabase.child("chats").child(chatId).child("messages").child(messageId).setValue(message)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Message sent successfully");
                        updateChatMetadata(message);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send message: " + e.getMessage(), e);
                        showToast("Failed to send message: " + e.getMessage());
                        messageEditText.setText(messageText);
                    });
        } else {
            Log.e(TAG, "Failed to generate message ID");
            showToast("Error: Cannot generate message ID");
            messageEditText.setText(messageText);
        }
    }

    private void updateChatMetadata(ChatMessage lastMessage) {
        Map<String, Object> chatMetadata = new HashMap<>();
        chatMetadata.put("lastMessage", lastMessage.message);
        chatMetadata.put("lastMessageTime", lastMessage.timestamp);
        chatMetadata.put("lastMessageSender", lastMessage.senderName);

        Map<String, Boolean> participants = new HashMap<>();
        participants.put(currentUser.getUid(), true);
        if (recipientUid != null) {
            participants.put(recipientUid, true);
        }
        chatMetadata.put("participants", participants);

        if (reportId != null) {
            chatMetadata.put("reportId", reportId);
        }

        mDatabase.child("chats").child(chatId).child("metadata").updateChildren(chatMetadata);
    }

    private void updateTypingStatus(boolean isTyping) {
        if (chatId != null) {
            mDatabase.child("chats").child(chatId).child("typing").child(currentUser.getUid())
                    .setValue(isTyping);

            if (isTyping) {
                messageEditText.postDelayed(() -> {
                    mDatabase.child("chats").child(chatId).child("typing").child(currentUser.getUid())
                            .setValue(false);
                }, 3000);
            }
        }
    }

    private void markMessageAsRead(String messageId) {
        if (messageId != null) {
            mDatabase.child("chats").child(chatId).child("messages").child(messageId)
                    .child("readBy").child(currentUser.getUid()).setValue(System.currentTimeMillis());
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_chat_info) {
            showChatInfo();
            return true;
        } else if (id == R.id.action_clear_chat) {
            showClearChatDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showChatInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Chat ID: ").append(chatId).append("\n\n");

        if (reportId != null) {
            info.append("Related Report: #").append(reportId.substring(reportId.length() - 6)).append("\n");
        }

        if (recipientName != null) {
            info.append("Participant: ").append(recipientName).append("\n");
        }

        info.append("Total Messages: ").append(messagesList.size());

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Chat Information")
                .setMessage(info.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void showClearChatDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear Chat")
                .setMessage("Are you sure you want to clear this chat? This action cannot be undone.\n\nAll messages will be permanently deleted.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
                    progressDialog.setMessage("Clearing chat...");
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    clearChatWithProgress(progressDialog);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearChatWithProgress(android.app.ProgressDialog progressDialog) {
        mDatabase.child("chats").child(chatId).child("messages").removeValue()
                .addOnSuccessListener(aVoid -> {
                    messagesList.clear();
                    messagesAdapter.notifyDataSetChanged();

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("lastMessage", "Chat cleared");
                    updates.put("lastMessageTime", System.currentTimeMillis());
                    updates.put("lastMessageSender", "System");

                    mDatabase.child("chats").child(chatId).child("metadata").updateChildren(updates)
                            .addOnCompleteListener(task -> {
                                progressDialog.dismiss();
                                if (task.isSuccessful()) {
                                    showToast("Chat cleared successfully");
                                } else {
                                    showToast("Chat cleared");
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Log.e(TAG, "Clear chat failed: " + e.getMessage(), e);

                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Clear Chat Failed")
                            .setMessage("Failed to clear chat:\n" + e.getMessage() +
                                    "\n\nThis might be due to permission restrictions. Contact admin if the problem persists.")
                            .setPositiveButton("OK", null)
                            .show();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (messagesListener != null && chatId != null) {
            mDatabase.child("chats").child(chatId).child("messages").removeEventListener(messagesListener);
        }

        if (typingListener != null && chatId != null) {
            mDatabase.child("chats").child(chatId).child("typing").removeEventListener(typingListener);
        }

        updateTypingStatus(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateTypingStatus(false);
    }

    // ChatMessage model class
    public static class ChatMessage {
        public String messageId;
        public String senderId;
        public String senderName;
        public String senderRole;
        public String message;
        public long timestamp;
        public String messageType; // text, image, system
        public String reportId;
        public Map<String, Long> readBy;

        public ChatMessage() {
            this.readBy = new HashMap<>();
        }
    }

    // ChatMessage Adapter class
    private class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {
        private List<ChatMessage> messages;
        private String currentUserId;
        private SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

        public ChatMessageAdapter(List<ChatMessage> messages, String currentUserId) {
            this.messages = messages;
            this.currentUserId = currentUserId;
        }

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == 1) {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_sent, parent, false);
            } else {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_received, parent, false);
            }
            return new MessageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            ChatMessage message = messages.get(position);

            // Handle different message types
            if ("image".equals(message.messageType)) {
                // Show image message
                if (holder.messageText != null) {
                    holder.messageText.setVisibility(View.GONE);
                }
                if (holder.messageImage != null) {
                    holder.messageImage.setVisibility(View.VISIBLE);

                    // Decode Base64 and display
                    try {
                        byte[] decodedBytes = Base64.decode(message.message, Base64.DEFAULT);
                        Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                        holder.messageImage.setImageBitmap(bitmap);

                        // Click to view full screen
                        holder.messageImage.setOnClickListener(v -> showFullScreenImage(bitmap));
                    } catch (Exception e) {
                        Log.e(TAG, "Error decoding image", e);
                        holder.messageImage.setVisibility(View.GONE);
                        if (holder.messageText != null) {
                            holder.messageText.setVisibility(View.VISIBLE);
                            holder.messageText.setText("❌ Failed to load image");
                        }
                    }
                }
            } else {
                // Show text message
                if (holder.messageImage != null) {
                    holder.messageImage.setVisibility(View.GONE);
                }
                if (holder.messageText != null) {
                    holder.messageText.setVisibility(View.VISIBLE);
                    holder.messageText.setText(message.message);
                }
            }

            holder.timeText.setText(timeFormat.format(new Date(message.timestamp)));

            // Show sender name for received messages
            if (!message.senderId.equals(currentUserId)) {
                if (holder.senderNameText != null) {
                    String displayName = message.senderName;
                    if (message.senderRole != null) {
                        displayName += " (" + message.senderRole + ")";
                    }
                    holder.senderNameText.setText(displayName);
                    holder.senderNameText.setVisibility(View.VISIBLE);
                }
            }

            if (holder.readStatusText != null) {
                String status = getSimpleReadStatus(message);
                holder.readStatusText.setText(status);
                holder.readStatusText.setVisibility(View.VISIBLE);
            }

            // Show date separator if needed
            boolean showDate = false;
            if (position == 0) {
                showDate = true;
            } else {
                ChatMessage previousMessage = messages.get(position - 1);
                Date currentDate = new Date(message.timestamp);
                Date previousDate = new Date(previousMessage.timestamp);

                if (!dateFormat.format(currentDate).equals(dateFormat.format(previousDate))) {
                    showDate = true;
                }
            }

            if (holder.dateText != null) {
                if (showDate) {
                    holder.dateText.setText(dateFormat.format(new Date(message.timestamp)));
                    holder.dateText.setVisibility(View.VISIBLE);
                } else {
                    holder.dateText.setVisibility(View.GONE);
                }
            }
        }

        private void showFullScreenImage(Bitmap bitmap) {
            // Create dialog to show full screen image
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(ChatActivity.this);

            ImageView imageView = new ImageView(ChatActivity.this);
            imageView.setImageBitmap(bitmap);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setPadding(16, 16, 16, 16);

            builder.setView(imageView);
            builder.setPositiveButton("Close", null);
            builder.create().show();
        }

        private String getSimpleReadStatus(ChatMessage message) {
            if (message.readBy == null || message.readBy.isEmpty()) {
                return "Sent";
            }
            return "Seen";
        }

        @Override
        public int getItemViewType(int position) {
            ChatMessage message = messages.get(position);
            return message.senderId.equals(currentUserId) ? 1 : 0;
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        class MessageViewHolder extends RecyclerView.ViewHolder {
            TextView messageText;
            ImageView messageImage;
            TextView timeText;
            TextView senderNameText;
            TextView readStatusText;
            TextView dateText;
            MaterialCardView messageCard;

            MessageViewHolder(@NonNull View itemView) {
                super(itemView);
                messageText = itemView.findViewById(R.id.messageText);
                messageImage = itemView.findViewById(R.id.messageImage);
                timeText = itemView.findViewById(R.id.timeText);
                senderNameText = itemView.findViewById(R.id.senderNameText);
                readStatusText = itemView.findViewById(R.id.readStatusText);
                dateText = itemView.findViewById(R.id.dateText);
                messageCard = itemView.findViewById(R.id.messageCard);
            }
        }
    }
}