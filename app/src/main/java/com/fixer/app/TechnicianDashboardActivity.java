package com.fixer.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
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

public class TechnicianDashboardActivity extends AppCompatActivity {

    private static final String TAG = "TechnicianDashboard";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private static final String PREFS_NAME = "FixerPrefs";
    private static final String PREFS_LAST_NOTIFICATION_CHECK = "last_notification_check";
    private static final String PREFS_SEEN_REPORT_IDS = "seen_report_ids";
    private static final long NOTIFICATION_CHECK_INTERVAL = 1000;

    // UI Components
    private SearchView searchView;
    private ChipGroup statusChipGroup;
    private RecyclerView reportsRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyStateText;
    private TextView statsText;

    private int notesInputId = View.generateViewId();
    private int photoImageViewId = View.generateViewId();
    private int removePhotoButtonId = View.generateViewId();

    // Firebase
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    // Data
    private List<MaintenanceReport> allReports;
    private List<MaintenanceReport> filteredReports;
    private TechnicianReportAdapter reportAdapter;
    private String currentFilter = "available"; // available, my_tasks, all
    private String searchQuery = "";

    // For photo handling
    private String tempPhotoBase64 = null;
    private MaintenanceReport currentWorkingReport = null;
    private AlertDialog currentDialog = null;
    private View currentDialogView = null;

    private Handler notificationHandler;
    private Runnable notificationCheckRunnable;
    private Set<String> seenReportIds = new HashSet<>();
    private long lastNotificationCheck = 0;
    private int unreadNotificationCount = 0;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.technician_menu, menu);

        // Update notification icon based on unread count
        MenuItem notificationItem = menu.findItem(R.id.action_notifications);
        if (notificationItem != null) {
            if (unreadNotificationCount > 0) {
                notificationItem.setIcon(R.drawable.ic_notifications_active);
            } else {
                notificationItem.setIcon(R.drawable.ic_notifications);
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

         if (id == R.id.action_notifications) {
            showNotificationsDialog();
            return true;

        } else if (id == R.id.action_logout) {
            // ✅ Show confirmation dialog before logging out
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Logout")
                    .setMessage("Are you sure you want to log out?")
                    .setPositiveButton("Logout", (dialog, which) -> {
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(this, Login.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
            return true;

        } else if (id == R.id.action_profile) {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showNotificationsDialog() {
        if (mAuth.getCurrentUser() == null) return;
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setMessage("Loading notifications...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        mDatabase.child("user_notifications").child(mAuth.getCurrentUser().getUid())
                .orderByChild("timestamp")
                .limitToLast(20)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressDialog.dismiss();

                        List<Map<String, Object>> notifications = new ArrayList<>();
                        for (DataSnapshot notifSnapshot : snapshot.getChildren()) {
                            Map<String, Object> notif = new HashMap<>();
                            notif.put("id", notifSnapshot.getKey());
                            notif.put("title", notifSnapshot.child("title").getValue(String.class));
                            notif.put("body", notifSnapshot.child("body").getValue(String.class));
                            notif.put("timestamp", notifSnapshot.child("timestamp").getValue(Long.class));
                            notif.put("read", notifSnapshot.child("read").getValue(Boolean.class));
                            notif.put("reportId", notifSnapshot.child("reportId").getValue(String.class));
                            notifications.add(notif);
                        }

                        if (notifications.isEmpty()) {
                            new AlertDialog.Builder(TechnicianDashboardActivity.this)
                                    .setTitle("Notifications")
                                    .setMessage("No notifications yet")
                                    .setPositiveButton("OK", null)
                                    .show();
                            return;
                        }

                        // Reverse to show newest first
                        java.util.Collections.reverse(notifications);

                        showNotificationsList(notifications);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        showToast("Failed to load notifications");
                    }
                });
    }

    private void showNotificationsList(List<Map<String, Object>> notifications) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Notifications (" + notifications.size() + ")");
        String[] notificationTitles = new String[notifications.size()];
        SimpleDateFormat timeFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

        for (int i = 0; i < notifications.size(); i++) {
            Map<String, Object> notif = notifications.get(i);
            String title = (String) notif.get("title");
            Long timestamp = (Long) notif.get("timestamp");
            Boolean read = (Boolean) notif.get("read");

            String prefix = Boolean.TRUE.equals(read) ? "" : "🔵 ";
            String timeStr = timestamp != null ? timeFormat.format(new Date(timestamp)) : "";

            notificationTitles[i] = prefix + title + "\n" + timeStr;
        }

        builder.setItems(notificationTitles, (dialog, which) -> {
            Map<String, Object> selectedNotif = notifications.get(which);
            showNotificationDetail(selectedNotif);
        });

        builder.setNegativeButton("Clear All", (dialog, which) -> {
            NotificationHelper.clearAllNotifications(mAuth.getCurrentUser().getUid());
            showToast("All notifications cleared");
        });

        builder.setNeutralButton("Close", null);
        builder.show();
    }
    private void showNotificationDetail(Map<String, Object> notification) {
        String notifId = (String) notification.get("id");
        String title = (String) notification.get("title");
        String body = (String) notification.get("body");
        String reportId = (String) notification.get("reportId");
// Mark as read
        if (mAuth.getCurrentUser() != null && notifId != null) {
            NotificationHelper.markNotificationAsRead(mAuth.getCurrentUser().getUid(), notifId);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(body);

        if (reportId != null && !reportId.isEmpty()) {
            builder.setPositiveButton("View Report", (dialog, which) -> {
                // Find and show the report
                for (MaintenanceReport report : allReports) {
                    if (report.reportId.equals(reportId)) {
                        showReportActions(report);
                        break;
                    }
                }
            });

            builder.setNeutralButton("Open Chat", (dialog, which) -> {
                ChatHelper.startReportChat(this, reportId);
            });
        }

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_technician_dashboard);

        // Initialize Firebase
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        // Setup toolbar
        setupToolbar();

        // Initialize views
        initializeViews();

        // Setup RecyclerView
        setupRecyclerView();

        // Setup filters
        setupFilters();

        // Setup search
        setupSearch();

        // Load reports
        loadReports();

        // Request notification permission
        requestNotificationPermission();

        // Get and save FCM token
        getFCMToken();

        // Setup notification listener
        setupNotificationListener();
    }

    /**
     * Load previously seen report IDs from SharedPreferences
     */
    private void loadSeenReportIds() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> savedIds = prefs.getStringSet(PREFS_SEEN_REPORT_IDS, new HashSet<>());
        seenReportIds = new HashSet<>(savedIds);
        lastNotificationCheck = prefs.getLong(PREFS_LAST_NOTIFICATION_CHECK, System.currentTimeMillis());

        Log.d(TAG, "Loaded " + seenReportIds.size() + " seen report IDs");
    }

    /**
     * Save seen report IDs to SharedPreferences
     */
    private void saveSeenReportIds() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(PREFS_SEEN_REPORT_IDS, seenReportIds);
        editor.putLong(PREFS_LAST_NOTIFICATION_CHECK, lastNotificationCheck);
        editor.apply();
    }

    /**
     * Setup client-side notification system that works without Cloud Functions
     */
    private void setupClientSideNotifications() {
        if (mAuth.getCurrentUser() == null) return;

        notificationHandler = new Handler(Looper.getMainLooper());

        notificationCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkForNewAssignments();
                checkForAbortedTasks();
                checkForCriticalReports();
                checkForScheduledTasks();

                // Schedule next check
                notificationHandler.postDelayed(this, NOTIFICATION_CHECK_INTERVAL);
            }
        };

        // Start periodic checks
        notificationHandler.post(notificationCheckRunnable);

        Log.d(TAG, "Client-side notification monitoring started");
    }

    /**
     * Check for new assignments to technician
     */
    private void checkForNewAssignments() {
        String currentUserEmail = mAuth.getCurrentUser() != null ?
                mAuth.getCurrentUser().getEmail() : "";

        if (currentUserEmail.isEmpty()) return;

        mDatabase.child("maintenance_reports")
                .orderByChild("assignedTo")
                .equalTo(currentUserEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            Long assignedAt = reportSnapshot.child("assignedAt").getValue(Long.class);

                            if (reportId == null || assignedAt == null) continue;

                            // Check if assignment is new
                            if (assignedAt > lastNotificationCheck) {
                                String notifKey = reportId + "_assigned_to_me";

                                if (!seenReportIds.contains(notifKey)) {
                                    String title = reportSnapshot.child("title").getValue(String.class);
                                    String location = reportSnapshot.child("location").getValue(String.class);
                                    String priority = reportSnapshot.child("priority").getValue(String.class);

                                    String priorityEmoji = getPriorityEmoji(priority);

                                    createLocalNotification(
                                            "New Task Assigned",
                                            priorityEmoji + " You've been assigned: " + title + " at " + location,
                                            reportId,
                                            "assignment",
                                            priority
                                    );

                                    seenReportIds.add(notifKey);
                                }
                            }
                        }

                        lastNotificationCheck = System.currentTimeMillis();
                        saveSeenReportIds();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check new assignments", error.toException());
                    }
                });
    }

    /**
     * Check for critical/high priority reports that need attention
     */
    private void checkForCriticalReports() {
        mDatabase.child("maintenance_reports")
                .orderByChild("createdAt")
                .startAt(lastNotificationCheck)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            String priority = reportSnapshot.child("priority").getValue(String.class);
                            String status = reportSnapshot.child("status").getValue(String.class);
                            Long createdAt = reportSnapshot.child("createdAt").getValue(Long.class);

                            if (reportId == null || priority == null || createdAt == null) continue;

                            // Only notify about critical/high priority unassigned reports
                            if (("critical".equalsIgnoreCase(priority) || "high".equalsIgnoreCase(priority)) &&
                                    "pending".equalsIgnoreCase(status) &&
                                    createdAt > lastNotificationCheck) {

                                String notifKey = reportId + "_critical";

                                if (!seenReportIds.contains(notifKey)) {
                                    String title = reportSnapshot.child("title").getValue(String.class);
                                    String location = reportSnapshot.child("location").getValue(String.class);

                                    String emoji = "critical".equalsIgnoreCase(priority) ? "🚨" : "⚠️";

                                    createLocalNotification(
                                            priority.toUpperCase() + " Priority Alert",
                                            emoji + " New " + priority + " priority report: " + title + " at " + location,
                                            reportId,
                                            "critical",
                                            priority
                                    );

                                    seenReportIds.add(notifKey);
                                }
                            }
                        }

                        saveSeenReportIds();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check critical reports", error.toException());
                    }
                });
    }

    /**
     * Check for aborted tasks that are back to available
     */
    private void checkForAbortedTasks() {
        mDatabase.child("maintenance_reports")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            Long abortedAt = reportSnapshot.child("abortedAt").getValue(Long.class);
                            String status = reportSnapshot.child("status").getValue(String.class);

                            if (reportId == null || abortedAt == null) continue;

                            // Check if abort happened recently and task is now pending
                            if (abortedAt > lastNotificationCheck && "pending".equalsIgnoreCase(status)) {
                                String notifKey = reportId + "_aborted";

                                if (!seenReportIds.contains(notifKey)) {
                                    String title = reportSnapshot.child("title").getValue(String.class);
                                    String abortReason = reportSnapshot.child("abortReason").getValue(String.class);

                                    createLocalNotification(
                                            "Task Available",
                                            "🔄 Task returned to available: " + title +
                                                    (abortReason != null ? "\nReason: " + abortReason : ""),
                                            reportId,
                                            "abort",
                                            null
                                    );

                                    seenReportIds.add(notifKey);
                                }
                            }
                        }

                        saveSeenReportIds();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check aborted tasks", error.toException());
                    }
                });
    }

    /**
     * Check for scheduled tasks approaching their scheduled date
     */
    private void checkForScheduledTasks() {
        String currentUserEmail = mAuth.getCurrentUser() != null ?
                mAuth.getCurrentUser().getEmail() : "";

        if (currentUserEmail.isEmpty()) return;

        mDatabase.child("maintenance_reports")
                .orderByChild("assignedTo")
                .equalTo(currentUserEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            String status = reportSnapshot.child("status").getValue(String.class);
                            String scheduledDate = reportSnapshot.child("scheduledDate").getValue(String.class);
                            Long scheduledAt = reportSnapshot.child("scheduledAt").getValue(Long.class);

                            if (reportId == null || scheduledAt == null) continue;

                            // Notify about newly scheduled tasks
                            if (scheduledAt > lastNotificationCheck &&
                                    "scheduled".equalsIgnoreCase(status) &&
                                    scheduledDate != null) {

                                String notifKey = reportId + "_scheduled";

                                if (!seenReportIds.contains(notifKey)) {
                                    String title = reportSnapshot.child("title").getValue(String.class);
                                    String scheduledTime = reportSnapshot.child("scheduledTime").getValue(String.class);

                                    String scheduleInfo = scheduledDate;
                                    if (scheduledTime != null && !scheduledTime.isEmpty()) {
                                        scheduleInfo += " at " + scheduledTime;
                                    }

                                    createLocalNotification(
                                            "Task Scheduled",
                                            "📅 Scheduled for " + scheduleInfo + ": " + title,
                                            reportId,
                                            "schedule",
                                            null
                                    );

                                    seenReportIds.add(notifKey);
                                }
                            }
                        }

                        saveSeenReportIds();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check scheduled tasks", error.toException());
                    }
                });
    }

    /**
     * Get emoji for priority level
     */
    private String getPriorityEmoji(String priority) {
        if (priority == null) return "📋";

        switch (priority.toLowerCase()) {
            case "critical":
                return "🚨";
            case "high":
                return "⚠️";
            case "medium":
                return "📋";
            case "low":
            default:
                return "ℹ️";
        }
    }

    /**
     * Create a local notification and store it in Firebase
     */
    private void createLocalNotification(String title, String body, String reportId, String type, String priority) {
        if (mAuth.getCurrentUser() == null) return;

        // Create notification data
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("title", title);
        notificationData.put("body", body);
        notificationData.put("reportId", reportId);
        notificationData.put("type", type);
        notificationData.put("priority", priority);
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("read", false);

        // Save to Firebase under user's notifications
        String notificationId = mDatabase.child("user_notifications")
                .child(mAuth.getCurrentUser().getUid())
                .push()
                .getKey();

        if (notificationId != null) {
            mDatabase.child("user_notifications")
                    .child(mAuth.getCurrentUser().getUid())
                    .child(notificationId)
                    .setValue(notificationData)
                    .addOnSuccessListener(aVoid -> {
                        // Increment unread count
                        unreadNotificationCount++;
                        invalidateOptionsMenu();

                        // Show in-app notification
                        showInAppNotification(title, body);

                        Log.d(TAG, "Local notification created: " + title);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to create notification", e);
                    });
        }
    }

    /**
     * Show an in-app notification toast
     */
    private void showInAppNotification(String title, String body) {
        runOnUiThread(() -> {
            Toast.makeText(this, title + "\n" + body, Toast.LENGTH_LONG).show();
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        999);
            }
        }
    }

    private void getFCMToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM token failed", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    Log.d(TAG, "FCM Token: " + token);
                    saveTokenToDatabase(token);
                });
    }

    private void saveTokenToDatabase(String token) {
        if (mAuth.getCurrentUser() != null) {
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("token", token);
            tokenData.put("updatedAt", System.currentTimeMillis());

            mDatabase.child("user_tokens").child(mAuth.getCurrentUser().getUid())
                    .setValue(tokenData);
        }
    }

    private void setupNotificationListener() {
        if (mAuth.getCurrentUser() == null) return;

        mDatabase.child("user_notifications").child(mAuth.getCurrentUser().getUid())
                .orderByChild("timestamp")
                .limitToLast(50)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int unreadCount = 0;
                        for (DataSnapshot notifSnapshot : snapshot.getChildren()) {
                            Boolean read = notifSnapshot.child("read").getValue(Boolean.class);
                            if (!Boolean.TRUE.equals(read)) {
                                unreadCount++;
                            }
                        }
                        unreadNotificationCount = unreadCount; // Store for menu update
                        updateNotificationBadge(unreadCount);
                        invalidateOptionsMenu(); // Refresh menu to update icon
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load notifications", error.toException());
                    }
                });
    }

    private void updateNotificationBadge(int count) {
        Log.d(TAG, "Unread notifications: " + count);
        // Update UI badge if needed
        if (statsText != null && count > 0) {
            runOnUiThread(() -> {
                String currentStats = statsText.getText().toString();
                if (!currentStats.contains("🔔")) {
                    statsText.setText(currentStats + " | 🔔 " + count);
                }
            });
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Technician Dashboard");
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void initializeViews() {
        searchView = findViewById(R.id.searchView);
        statusChipGroup = findViewById(R.id.statusChipGroup);
        reportsRecyclerView = findViewById(R.id.reportsRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        emptyStateText = findViewById(R.id.emptyStateText);
        statsText = findViewById(R.id.statsText);

        swipeRefreshLayout.setOnRefreshListener(this::loadReports);
        swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_red_light,
                android.R.color.holo_orange_light
        );
    }

    private void setupRecyclerView() {
        allReports = new ArrayList<>();
        filteredReports = new ArrayList<>();
        reportAdapter = new TechnicianReportAdapter(filteredReports);
        reportsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        reportsRecyclerView.setAdapter(reportAdapter);
    }

    private void setupFilters() {
        String[] filters = {"Available Tasks", "My Tasks", "All Reports"};
        String[] filterValues = {"available", "my_tasks", "all"};

        for (int i = 0; i < filters.length; i++) {
            Chip chip = new Chip(this);
            chip.setText(filters[i]);
            chip.setCheckable(true);

            final String filterValue = filterValues[i];
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    currentFilter = filterValue;
                    uncheckOtherChips(chip);
                    applyFilters();
                }
            });

            statusChipGroup.addView(chip);

            // Set "Available Tasks" as default
            if (i == 0) {
                chip.setChecked(true);
            }
        }
    }

    private void uncheckOtherChips(Chip selectedChip) {
        for (int i = 0; i < statusChipGroup.getChildCount(); i++) {
            Chip chip = (Chip) statusChipGroup.getChildAt(i);
            if (chip != selectedChip) {
                chip.setChecked(false);
            }
        }
    }

    private void setupSearch() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query;
                applyFilters();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText;
                applyFilters();
                return true;
            }
        });
    }

    private void loadReports() {
        swipeRefreshLayout.setRefreshing(true);

        mDatabase.child("maintenance_reports")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        allReports.clear();

                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            try {
                                MaintenanceReport report = new MaintenanceReport();

                                report.reportId = reportSnapshot.getKey();
                                report.title = reportSnapshot.child("title").getValue(String.class);
                                report.description = reportSnapshot.child("description").getValue(String.class);
                                report.location = reportSnapshot.child("location").getValue(String.class);
                                report.campus = reportSnapshot.child("campus").getValue(String.class);
                                report.priority = reportSnapshot.child("priority").getValue(String.class);
                                report.status = reportSnapshot.child("status").getValue(String.class);
                                report.assignedTo = reportSnapshot.child("assignedTo").getValue(String.class);
                                report.photoUrl = reportSnapshot.child("photoBase64").getValue(String.class);
                                report.completionPhotoBase64 = reportSnapshot.child("completionPhotoBase64").getValue(String.class);
                                report.technicianNotes = reportSnapshot.child("technicianNotes").getValue(String.class);
                                report.scheduledDate = reportSnapshot.child("scheduledDate").getValue(String.class);

                                Long createdAt = reportSnapshot.child("createdAt").getValue(Long.class);
                                report.createdAt = createdAt != null ? createdAt : 0L;

                                // Set defaults
                                if (report.status == null) report.status = "pending";
                                if (report.priority == null) report.priority = "medium";

                                allReports.add(report);
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing report", e);
                            }
                        }

                        allReports.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                        applyFilters();
                        updateStats();
                        swipeRefreshLayout.setRefreshing(false);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load reports", error.toException());
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
    }

    private void applyFilters() {
        filteredReports.clear();
        String currentUserEmail = mAuth.getCurrentUser() != null ?
                mAuth.getCurrentUser().getEmail() : "";

        for (MaintenanceReport report : allReports) {
            boolean matchesFilter = false;

            switch (currentFilter) {
                case "available":
                    // Show pending and scheduled reports not assigned to anyone
                    matchesFilter = ("pending".equalsIgnoreCase(report.status) ||
                            "scheduled".equalsIgnoreCase(report.status)) &&
                            (report.assignedTo == null || report.assignedTo.isEmpty());
                    break;

                case "my_tasks":
                    // Show reports assigned to current technician (excluding fully completed)
                    matchesFilter = currentUserEmail.equals(report.assignedTo) &&
                            !"completed".equalsIgnoreCase(report.status);
                    break;

                case "all":
                    matchesFilter = true;
                    break;
            }

            // Apply search filter
            if (matchesFilter && !searchQuery.isEmpty()) {
                String query = searchQuery.toLowerCase();
                matchesFilter = (report.title != null && report.title.toLowerCase().contains(query)) ||
                        (report.description != null && report.description.toLowerCase().contains(query)) ||
                        (report.location != null && report.location.toLowerCase().contains(query));
            }

            if (matchesFilter) {
                filteredReports.add(report);
            }
        }

        reportAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateStats() {
        int availableTasks = 0;
        int myTasks = 0;
        String currentUserEmail = mAuth.getCurrentUser() != null ?
                mAuth.getCurrentUser().getEmail() : "";

        for (MaintenanceReport report : allReports) {
            if (("pending".equalsIgnoreCase(report.status) ||
                    "scheduled".equalsIgnoreCase(report.status)) &&
                    (report.assignedTo == null || report.assignedTo.isEmpty())) {
                availableTasks++;
            }

            if (currentUserEmail.equals(report.assignedTo) &&
                    !"completed".equalsIgnoreCase(report.status)) {
                myTasks++;
            }
        }

        statsText.setText("Available: " + availableTasks + " | My Tasks: " + myTasks);
    }

    private void updateEmptyState() {
        if (filteredReports.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            reportsRecyclerView.setVisibility(View.GONE);

            switch (currentFilter) {
                case "available":
                    emptyStateText.setText("No available tasks at the moment.");
                    break;
                case "my_tasks":
                    emptyStateText.setText("You have no assigned tasks.");
                    break;
                default:
                    emptyStateText.setText("No reports found.");
                    break;
            }
        } else {
            emptyStateText.setVisibility(View.GONE);
            reportsRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showReportActions(MaintenanceReport report) {
        String currentUserEmail = mAuth.getCurrentUser() != null ?
                mAuth.getCurrentUser().getEmail() : "";
        boolean isAssignedToMe = currentUserEmail.equals(report.assignedTo);

        List<String> options = new ArrayList<>();
        options.add("View Details");
        options.add("Chat about Report");

        if (report.photoUrl != null && !report.photoUrl.isEmpty()) {
            options.add("View Report Photo");
        }

        if (!isAssignedToMe && (report.assignedTo == null || report.assignedTo.isEmpty())) {
            options.add("Accept Task");
        }

        if (isAssignedToMe && !"completed".equalsIgnoreCase(report.status)) {
            // ✅ Allow "Start Working" only if status is "waiting" or "scheduled"
            if ("waiting".equalsIgnoreCase(report.status) ||
                    "scheduled".equalsIgnoreCase(report.status)) {
                options.add("Start Working");
            }

            // ✅ Allow "Mark as Complete" and "Mark as Partially Complete" only if in progress
            if ("in progress".equalsIgnoreCase(report.status)) {
                options.add("Mark as Complete");
                options.add("Mark as Partially Complete");
            }

            // ✅ Allow "Abort Task" for ALL assigned tasks that are not completed
            options.add("Abort Task");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Report Actions");
        builder.setItems(options.toArray(new String[0]), (dialog, which) -> {
            String selected = options.get(which);

            switch (selected) {
                case "View Details":
                    showReportDetails(report);
                    break;
                case "Chat about Report":
                    ChatHelper.startReportChat(this, report.reportId);
                    break;
                case "View Report Photo":
                    viewFullPhoto(report.photoUrl);
                    break;
                case "Accept Task":
                    acceptTask(report);
                    break;
                case "Start Working":
                    startWorking(report);
                    break;
                case "Mark as Complete":
                    showCompleteDialog(report);
                    break;
                case "Mark as Partially Complete":
                    showPartiallyCompleteDialog(report);
                    break;
                case "Abort Task":
                    showAbortDialog(report);
                    break;
            }
        });

        builder.show();
    }

    private void showPartiallyCompleteDialog(MaintenanceReport report) {
        currentWorkingReport = report;
        tempPhotoBase64 = null;

        View dialogView = createPartiallyCompleteDialogView();
        currentDialogView = dialogView;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Mark as Partially Complete");
        builder.setView(dialogView);

        builder.setPositiveButton("Submit", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            currentDialogView = null;
            currentDialog = null;
            currentWorkingReport = null;
            tempPhotoBase64 = null;
        });

        AlertDialog dialog = builder.create();
        currentDialog = dialog;

        dialog.setOnShowListener(dialogInterface -> {
            MaterialButton submitButton = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            submitButton.setOnClickListener(view -> {
                TextInputEditText notesInput = dialogView.findViewById(notesInputId);

                String notes = notesInput != null ? notesInput.getText().toString().trim() : "";

                if (notes.isEmpty()) {
                    showToast("Please provide notes about the partial completion");
                    return;
                }

                if (tempPhotoBase64 == null || tempPhotoBase64.isEmpty()) {
                    showToast("Please add a completion photo");
                    return;
                }

                markAsPartiallyComplete(report, notes, tempPhotoBase64);
                currentDialogView = null;
                currentDialog = null;
                currentWorkingReport = null;
                tempPhotoBase64 = null;
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private View createPartiallyCompleteDialogView() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView instructionText = new TextView(this);
        instructionText.setText("Add completion photo and notes about what was completed:");
        instructionText.setPadding(0, 0, 0, 16);
        layout.addView(instructionText);

        TextView notesLabel = new TextView(this);
        notesLabel.setText("Notes (Required):");
        notesLabel.setPadding(0, 0, 0, 8);
        layout.addView(notesLabel);

        TextInputEditText notesInput = new TextInputEditText(this);
        notesInput.setId(notesInputId);
        notesInput.setHint("Describe what was completed and what remains...");
        notesInput.setMinLines(4);
        notesInput.setMaxLines(6);
        layout.addView(notesInput);

        TextView spacer = new TextView(this);
        spacer.setPadding(0, 20, 0, 0);
        layout.addView(spacer);

        MaterialButton addPhotoButton = new MaterialButton(this);
        addPhotoButton.setText("Add Completion Photo");
        addPhotoButton.setOnClickListener(v -> showPhotoOptions());
        layout.addView(addPhotoButton);

        ImageView photoImageView = new ImageView(this);
        photoImageView.setId(photoImageViewId);
        photoImageView.setAdjustViewBounds(true);
        photoImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photoImageView.setVisibility(View.GONE);
        layout.addView(photoImageView);

        MaterialButton removeButton = new MaterialButton(this);
        removeButton.setId(removePhotoButtonId);
        removeButton.setText("Remove Photo");
        removeButton.setVisibility(View.GONE);
        removeButton.setOnClickListener(v -> {
            tempPhotoBase64 = null;
            photoImageView.setVisibility(View.GONE);
            photoImageView.setImageBitmap(null);
            removeButton.setVisibility(View.GONE);
        });
        layout.addView(removeButton);

        return layout;
    }

// ADD method to mark as partially complete:

    private void markAsPartiallyComplete(MaintenanceReport report, String notes, String photoBase64) {
        long completionTime = System.currentTimeMillis();

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "partially completed");
        updates.put("completedAt", completionTime);
        updates.put("completionPhotoBase64", photoBase64);
        updates.put("partialCompletionNotes", notes);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        updates.put("completedAtFormatted", dateFormat.format(new Date(completionTime)));

        if (mAuth.getCurrentUser() != null) {
            updates.put("completedBy", mAuth.getCurrentUser().getEmail());
        }

        mDatabase.child("maintenance_reports").child(report.reportId)
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    showToast("Task marked as partially complete");

                    // ✅ NOTIFY USER
                    mDatabase.child("maintenance_reports").child(report.reportId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    String reporterEmail = snapshot.child("reportedByEmail")
                                            .getValue(String.class);
                                    String reporterUid = snapshot.child("reportedByUid")
                                            .getValue(String.class);

                                    if (reporterEmail != null) {
                                        NotificationHelper.notifyReportStatusChange(
                                                report.reportId, report.title, "partially completed",
                                                reporterUid, reporterEmail);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                }
                            });

                    String chatId = ChatHelper.generateReportChatId(report.reportId);
                    String message = "Task marked as PARTIALLY COMPLETED by technician\n\nNotes: " + notes;
                    ChatHelper.sendSystemMessage(chatId, message, report.reportId);

                    loadReports();
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to update task: " + e.getMessage());
                    Log.e(TAG, "Failed to update task", e);
                });
    }

    private void showAbortDialog(MaintenanceReport report) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Abort Task");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView reasonLabel = new TextView(this);
        reasonLabel.setText("Please provide a reason for aborting:");
        layout.addView(reasonLabel);

        TextInputEditText reasonInput = new TextInputEditText(this);
        reasonInput.setHint("Enter reason here...");
        layout.addView(reasonInput);

        builder.setView(layout);

        builder.setPositiveButton("Abort", (dialog, which) -> {
            String reason = reasonInput.getText().toString().trim();
            if (reason.isEmpty()) {
                showToast("Abort reason is required.");
                return;
            }

            String technicianEmail = mAuth.getCurrentUser() != null ?
                    mAuth.getCurrentUser().getEmail() : "Technician";

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "pending");
            updates.put("assignedTo", null);
            updates.put("assignedAt", null);
            updates.put("abortedAt", System.currentTimeMillis());
            updates.put("abortReason", reason);

            mDatabase.child("maintenance_reports").child(report.reportId)
                    .updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        showToast("Task aborted and returned to pending.");

                        // ✅ NOTIFY ADMINS about abort
                        NotificationHelper.notifyAdminsTaskAborted(
                                report.reportId, report.title, technicianEmail, reason);

                        // ✅ NOTIFY USER
                        mDatabase.child("maintenance_reports").child(report.reportId)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        String reporterEmail = snapshot.child("reportedByEmail")
                                                .getValue(String.class);
                                        String reporterUid = snapshot.child("reportedByUid")
                                                .getValue(String.class);

                                        if (reporterEmail != null) {
                                            NotificationHelper.notifyReportStatusChange(
                                                    report.reportId, report.title, "pending",
                                                    reporterUid, reporterEmail);
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                    }
                                });

                        String chatId = ChatHelper.generateReportChatId(report.reportId);
                        ChatHelper.sendSystemMessage(chatId,
                                "Technician aborted the task. Reason: " + reason +
                                        "\nTask is now available for reassignment.",
                                report.reportId);

                        loadReports();
                    })
                    .addOnFailureListener(e -> showToast("Failed to abort task: " + e.getMessage()));
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }



    private void showReportDetails(MaintenanceReport report) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(report.title);

        StringBuilder details = new StringBuilder();
        details.append("Description: ").append(report.description).append("\n\n");
        details.append("Location: ").append(report.location).append("\n");
        details.append("Campus: ").append(report.campus).append("\n");
        details.append("Priority: ").append(report.priority.toUpperCase()).append("\n");
        details.append("Status: ").append(report.status.toUpperCase()).append("\n");

        if (report.scheduledDate != null && !report.scheduledDate.isEmpty()) {
            details.append("Scheduled Date: ").append(report.scheduledDate).append("\n");
        }

        if (report.assignedTo != null && !report.assignedTo.isEmpty()) {
            details.append("Assigned to: ").append(report.assignedTo).append("\n");
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        details.append("\nReported: ").append(dateFormat.format(new Date(report.createdAt)));

        if (report.technicianNotes != null && !report.technicianNotes.isEmpty()) {
            details.append("\n\nTechnician Notes:\n").append(report.technicianNotes);
        }

        // Show partial completion notes if exists
        if ("partially completed".equalsIgnoreCase(report.status)) {
            // Try to get from technicianNotes or partialCompletionNotes
            // Since we don't have partialCompletionNotes field in this class yet, we'll show it later
            details.append("\n\n⚠️ This task is partially completed");
        }

        builder.setMessage(details.toString());
        builder.setPositiveButton("Close", null);
        builder.show();
    }


    private void viewFullPhoto(String photoBase64) {
        if (photoBase64 == null || photoBase64.isEmpty()) {
            showToast("No photo available");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        ImageView imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        try {
            byte[] decodedBytes = Base64.decode(photoBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                int padding = 16;
                imageView.setPadding(padding, padding, padding, padding);

                builder.setView(imageView);
                builder.setTitle("Photo");
                builder.setPositiveButton("Close", null);
                builder.show();
            } else {
                showToast("Failed to load photo");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error displaying photo", e);
            showToast("Error displaying photo");
        }
    }

    private void acceptTask(MaintenanceReport report) {
        new AlertDialog.Builder(this)
                .setTitle("Accept Task")
                .setMessage("Do you want to accept this maintenance task?")
                .setPositiveButton("Accept", (dialog, which) -> {
                    String userEmail = mAuth.getCurrentUser() != null ?
                            mAuth.getCurrentUser().getEmail() : "";

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("assignedTo", userEmail);
                    updates.put("assignedAt", System.currentTimeMillis());

                    if (report.scheduledDate != null && !report.scheduledDate.isEmpty()) {
                        updates.put("status", "waiting");
                    } else {
                        updates.put("status", "in progress");
                    }

                    mDatabase.child("maintenance_reports").child(report.reportId)
                            .updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                showToast("Task accepted successfully");

                                // ✅ NOTIFY USER about status change
                                mDatabase.child("maintenance_reports").child(report.reportId)
                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override
                                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                String reporterEmail = snapshot.child("reportedByEmail")
                                                        .getValue(String.class);
                                                String reporterUid = snapshot.child("reportedByUid")
                                                        .getValue(String.class);
                                                String status = snapshot.child("status").getValue(String.class);

                                                if (reporterEmail != null) {
                                                    NotificationHelper.notifyReportStatusChange(
                                                            report.reportId, report.title, status,
                                                            reporterUid, reporterEmail);
                                                }
                                            }

                                            @Override
                                            public void onCancelled(@NonNull DatabaseError error) {
                                                Log.e(TAG, "Failed to get reporter info", error.toException());
                                            }
                                        });

                                String chatId = ChatHelper.generateReportChatId(report.reportId);
                                String message = "waiting".equals(updates.get("status")) ?
                                        "Task accepted. Waiting for scheduled date: " + report.scheduledDate :
                                        "Task accepted and started immediately.";

                                ChatHelper.sendSystemMessage(chatId, message, report.reportId);
                                loadReports();
                            })
                            .addOnFailureListener(e -> showToast("Failed to accept task: " + e.getMessage()));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void startWorking(MaintenanceReport report) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "in progress");
        updates.put("startedAt", System.currentTimeMillis());

        mDatabase.child("maintenance_reports").child(report.reportId)
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    showToast("Status updated to In Progress");

                    // ✅ NOTIFY USER
                    mDatabase.child("maintenance_reports").child(report.reportId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    String reporterEmail = snapshot.child("reportedByEmail")
                                            .getValue(String.class);
                                    String reporterUid = snapshot.child("reportedByUid")
                                            .getValue(String.class);

                                    if (reporterEmail != null) {
                                        NotificationHelper.notifyReportStatusChange(
                                                report.reportId, report.title, "in progress",
                                                reporterUid, reporterEmail);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                }
                            });

                    String chatId = ChatHelper.generateReportChatId(report.reportId);
                    ChatHelper.sendSystemMessage(chatId, "Technician started working on this task", report.reportId);
                    loadReports();
                })
                .addOnFailureListener(e -> showToast("Failed to update status: " + e.getMessage()));
    }

    private void showCompleteDialog(MaintenanceReport report) {
        currentWorkingReport = report;
        tempPhotoBase64 = null;

        View dialogView = createCompleteDialogView();
        currentDialogView = dialogView;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Complete Task");
        builder.setView(dialogView);

        builder.setPositiveButton("Complete", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            currentDialogView = null;
            currentDialog = null;
            currentWorkingReport = null;
            tempPhotoBase64 = null;
        });

        AlertDialog dialog = builder.create();
        currentDialog = dialog;

        dialog.setOnShowListener(dialogInterface -> {
            MaterialButton completeButton = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            completeButton.setOnClickListener(view -> {
                TextInputEditText notesInput = dialogView.findViewById(notesInputId);

                String notes = notesInput != null ? notesInput.getText().toString().trim() : "";

                if (tempPhotoBase64 == null || tempPhotoBase64.isEmpty()) {
                    showToast("Please add a completion photo");
                    return;
                }

                completeTask(report, notes, tempPhotoBase64);
                currentDialogView = null;
                currentDialog = null;
                currentWorkingReport = null;
                tempPhotoBase64 = null;
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private View createCompleteDialogView() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView instructionText = new TextView(this);
        instructionText.setText("Add completion photo and optional notes:");
        instructionText.setPadding(0, 0, 0, 16);
        layout.addView(instructionText);

        MaterialButton addPhotoButton = new MaterialButton(this);
        addPhotoButton.setText("Add Completion Photo");
        addPhotoButton.setOnClickListener(v -> showPhotoOptions());
        layout.addView(addPhotoButton);

        ImageView photoImageView = new ImageView(this);
        photoImageView.setId(photoImageViewId);
        photoImageView.setAdjustViewBounds(true);
        photoImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photoImageView.setVisibility(View.GONE);
        layout.addView(photoImageView);

        MaterialButton removeButton = new MaterialButton(this);
        removeButton.setId(removePhotoButtonId);
        removeButton.setText("Remove Photo");
        removeButton.setVisibility(View.GONE);
        removeButton.setOnClickListener(v -> {
            tempPhotoBase64 = null;
            photoImageView.setVisibility(View.GONE);
            photoImageView.setImageBitmap(null);
            removeButton.setVisibility(View.GONE);
        });
        layout.addView(removeButton);

        TextView notesLabel = new TextView(this);
        notesLabel.setText("Notes (Optional):");
        notesLabel.setPadding(0, 20, 0, 8);
        layout.addView(notesLabel);

        TextInputEditText notesInput = new TextInputEditText(this);
        notesInput.setId(notesInputId);
        notesInput.setHint("Add any notes about the repair...");
        notesInput.setMinLines(3);
        notesInput.setMaxLines(5);
        layout.addView(notesInput);

        return layout;
    }

    private void showPhotoOptions() {
        String[] options = {"Take Photo", "Choose from Gallery"};

        new AlertDialog.Builder(this)
                .setTitle("Add Completion Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (checkCameraPermission()) {
                            openCamera();
                        }
                    } else {
                        if (checkStoragePermission()) {
                            openGallery();
                        }
                    }
                })
                .show();
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
            return false;
        }
        return true;
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        REQUEST_STORAGE_PERMISSION);
                return false;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
                return false;
            }
        }
        return true;
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void openGallery() {
        Intent pickPhotoIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickPhotoIntent, REQUEST_IMAGE_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null && currentDialogView != null) {
            Bitmap processedBitmap = null;

            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                Bundle extras = data.getExtras();
                if (extras != null) {
                    Bitmap imageBitmap = (Bitmap) extras.get("data");
                    if (imageBitmap != null) {
                        processedBitmap = compressBitmap(imageBitmap);
                    }
                }
            } else if (requestCode == REQUEST_IMAGE_PICK) {
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                        processedBitmap = compressBitmap(bitmap);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to load image", e);
                        showToast("Failed to load image");
                    }
                }
            }

            if (processedBitmap != null) {
                tempPhotoBase64 = bitmapToBase64(processedBitmap);

                ImageView photoImageView = currentDialogView.findViewById(photoImageViewId);
                MaterialButton removeButton = currentDialogView.findViewById(R.id.removePhotoButton);

                if (photoImageView != null) {
                    photoImageView.setImageBitmap(processedBitmap);
                    photoImageView.setVisibility(View.VISIBLE);
                }

                if (removeButton != null) {
                    removeButton.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private Bitmap compressBitmap(Bitmap bitmap) {
        int MAX_IMAGE_SIZE = 800;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float aspectRatio = (float) width / height;
        int newWidth, newHeight;

        if (width > height) {
            newWidth = Math.min(width, MAX_IMAGE_SIZE);
            newHeight = (int) (newWidth / aspectRatio);
        } else {
            newHeight = Math.min(height, MAX_IMAGE_SIZE);
            newWidth = (int) (newHeight * aspectRatio);
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    private void completeTask(MaintenanceReport report, String notes, String photoBase64) {
        long completionTime = System.currentTimeMillis();

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "completed");
        updates.put("completedAt", completionTime);
        updates.put("completionPhotoBase64", photoBase64);
        updates.put("technicianNotes", notes);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        updates.put("completedAtFormatted", dateFormat.format(new Date(completionTime)));

        String technicianEmail = mAuth.getCurrentUser() != null ?
                mAuth.getCurrentUser().getEmail() : "Technician";
        updates.put("completedBy", technicianEmail);

        mDatabase.child("maintenance_reports").child(report.reportId)
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    showToast("Task completed successfully");

                    // ✅ NOTIFY ADMINS
                    NotificationHelper.notifyReportCompleted(report.reportId, report.title, technicianEmail);

                    // ✅ NOTIFY USER
                    mDatabase.child("maintenance_reports").child(report.reportId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    String reporterEmail = snapshot.child("reportedByEmail")
                                            .getValue(String.class);

                                    if (reporterEmail != null) {
                                        NotificationHelper.notifyUserReportCompleted(
                                                report.reportId, report.title, reporterEmail, technicianEmail);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                }
                            });

                    String chatId = ChatHelper.generateReportChatId(report.reportId);
                    String message = "Task marked as COMPLETED by technician\n\n";
                    if (!notes.isEmpty()) {
                        message += "Notes: " + notes;
                    }
                    ChatHelper.sendSystemMessage(chatId, message, report.reportId);

                    loadReports();
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to complete task: " + e.getMessage());
                    Log.e(TAG, "Failed to complete task", e);
                });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Maintenance Report Model
    public static class MaintenanceReport {
        public String reportId;
        public String title;
        public String description;
        public String location;
        public String campus;
        public String priority;
        public String status;
        public String scheduledDate;
        public String assignedTo;
        public String photoUrl;
        public String completionPhotoBase64;
        public String technicianNotes;
        public long createdAt;
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop notification checks when app is paused
        if (notificationHandler != null && notificationCheckRunnable != null) {
            notificationHandler.removeCallbacks(notificationCheckRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Resume notification checks when app is resumed
        if (notificationHandler != null && notificationCheckRunnable != null) {
            notificationHandler.post(notificationCheckRunnable);
        }

        // Reload reports
        loadReports();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop notification checks
        if (notificationHandler != null && notificationCheckRunnable != null) {
            notificationHandler.removeCallbacks(notificationCheckRunnable);
        }
    }

    // Technician Report Adapter
    private class TechnicianReportAdapter extends RecyclerView.Adapter<TechnicianReportAdapter.ViewHolder> {
        private List<MaintenanceReport> reports;
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        public TechnicianReportAdapter(List<MaintenanceReport> reports) {
            this.reports = reports;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_technician_report, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MaintenanceReport report = reports.get(position);

            holder.titleText.setText(report.title != null ? report.title : "No Title");
            holder.descriptionText.setText(report.description != null ? report.description : "No Description");
            holder.locationText.setText((report.location != null ? report.location : "Unknown") +
                    " • " + (report.campus != null ? report.campus : "Unknown Campus"));
            holder.dateText.setText(dateFormat.format(new Date(report.createdAt)));

            // Set priority chip
            if (report.priority != null) {
                holder.priorityChip.setText(report.priority.toUpperCase());
                holder.priorityChip.setVisibility(View.VISIBLE);
                setPriorityColor(holder.priorityChip, report.priority);
            } else {
                holder.priorityChip.setVisibility(View.GONE);
            }

            // Set status chip
            if (report.status != null) {
                holder.statusChip.setText(report.status.toUpperCase());
                holder.statusChip.setVisibility(View.VISIBLE);
                setStatusColor(holder.statusChip, report.status);
            } else {
                holder.statusChip.setVisibility(View.GONE);
            }

            // Show assignment info
            String currentUserEmail = mAuth.getCurrentUser() != null ?
                    mAuth.getCurrentUser().getEmail() : "";

            if (currentUserEmail.equals(report.assignedTo)) {
                holder.assignmentText.setText("Assigned to: YOU");
                holder.assignmentText.setVisibility(View.VISIBLE);
                holder.assignmentText.setTextColor(getResources().getColor(R.color.primary));
            } else if (report.assignedTo != null && !report.assignedTo.isEmpty()) {
                holder.assignmentText.setText("Assigned to: " + report.assignedTo);
                holder.assignmentText.setVisibility(View.VISIBLE);
            } else {
                holder.assignmentText.setText("Unassigned");
                holder.assignmentText.setVisibility(View.VISIBLE);
            }

            // Show photo indicator
            if (report.photoUrl != null && !report.photoUrl.isEmpty()) {
                holder.photoIndicator.setVisibility(View.VISIBLE);
            } else {
                holder.photoIndicator.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> showReportActions(report));
        }

        @Override
        public int getItemCount() {
            return reports.size();
        }

        private void setPriorityColor(Chip chip, String priority) {
            switch (priority.toLowerCase()) {
                case "critical":
                    chip.setChipBackgroundColorResource(R.color.priority_critical);
                    chip.setTextColor(getResources().getColor(android.R.color.white));
                    break;
                case "high":
                    chip.setChipBackgroundColorResource(R.color.priority_high);
                    chip.setTextColor(getResources().getColor(android.R.color.white));
                    break;
                case "medium":
                    chip.setChipBackgroundColorResource(R.color.priority_medium);
                    chip.setTextColor(getResources().getColor(android.R.color.black));
                    break;
                case "low":
                default:
                    chip.setChipBackgroundColorResource(R.color.priority_low);
                    chip.setTextColor(getResources().getColor(android.R.color.white));
                    break;
            }
        }

        private void setStatusColor(Chip chip, String status) {
            switch (status.toLowerCase().replace(" ", "_")) {
                case "completed":
                    chip.setChipBackgroundColorResource(R.color.status_completed);
                    chip.setTextColor(getResources().getColor(android.R.color.white));
                    break;
                case "partially_completed":
                case "partially completed":
                    chip.setChipBackgroundColorResource(R.color.status_partially_completed);
                    chip.setTextColor(getResources().getColor(android.R.color.white));
                    break;
                case "in_progress":
                case "in progress":
                    chip.setChipBackgroundColorResource(R.color.status_in_progress);
                    chip.setTextColor(getResources().getColor(android.R.color.white));
                    break;
                case "scheduled":
                    chip.setChipBackgroundColorResource(R.color.status_scheduled);
                    chip.setTextColor(getResources().getColor(android.R.color.white));
                    break;
                case "waiting":
                    chip.setChipBackgroundColorResource(R.color.status_waiting);
                    chip.setTextColor(getResources().getColor(android.R.color.white));
                    break;
                case "rejected":
                    chip.setChipBackgroundColorResource(R.color.status_rejected);
                    chip.setTextColor(getResources().getColor(android.R.color.white));
                    break;
                case "pending":
                default:
                    chip.setChipBackgroundColorResource(R.color.status_pending);
                    chip.setTextColor(getResources().getColor(android.R.color.white));
                    break;
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView titleText, descriptionText, locationText, dateText, assignmentText;
            TextView photoIndicator;
            Chip priorityChip, statusChip;

            ViewHolder(View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.titleText);
                descriptionText = itemView.findViewById(R.id.descriptionText);
                locationText = itemView.findViewById(R.id.locationText);
                dateText = itemView.findViewById(R.id.dateText);
                assignmentText = itemView.findViewById(R.id.assignmentText);
                priorityChip = itemView.findViewById(R.id.priorityChip);
                statusChip = itemView.findViewById(R.id.statusChip);
                photoIndicator = itemView.findViewById(R.id.photoIndicator);
            }
        }
    }
}