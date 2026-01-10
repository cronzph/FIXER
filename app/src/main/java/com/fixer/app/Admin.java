package com.fixer.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import android.widget.ScrollView;
import java.util.Collections;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Admin extends AppCompatActivity {

    private static final String TAG = "AdminDashboard";
    private static final String PREFS_NAME = "FixerPrefs";
    private static final String PREFS_LAST_NOTIFICATION_CHECK = "last_notification_check";
    private static final String PREFS_SEEN_REPORT_IDS = "seen_report_ids";
    private static final long NOTIFICATION_CHECK_INTERVAL = 1000; // 30 seconds

    private int unreadNotificationCount = 0;
    private ValueEventListener notificationListener;
    private Handler notificationHandler;
    private Runnable notificationCheckRunnable;
    private Set<String> seenReportIds = new HashSet<>();
    private long lastNotificationCheck = 0;

    // UI Components
    private TextView welcomeText;
    private TextView totalUsersText;
    private TextView pendingRequestsText;

    private CardView usersCard, requestsCard, campusCard, campusIssuesCard;
    private MaterialButton manageUsersButton, viewReportsButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recentActivityRecyclerView;


    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;

    // Data
    private List<ActivityItem> recentActivities;
    private ActivityAdapter activityAdapter;
    private int totalReports = 0;
    private int pendingReports = 0;
    private int campusesWithIssues = 0;
    private HashMap<String, Integer> campusIssueDetails = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_admin);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentUser = mAuth.getCurrentUser();

        // Setup toolbar
        setupToolbar();

        // Initialize UI components
        initializeViews();

        // Setup click listeners
        setupClickListeners();

        // Setup RecyclerView
        setupRecyclerView();

        // Load dashboard data
        loadDashboardData();

        // Check if user is actually an admin
        verifyAdminAccess();

        // Request notification permission for Android 13+
        requestNotificationPermission();

        // Get FCM token
        getFCMToken();

        // Load seen report IDs from preferences
        loadSeenReportIds();

        // Setup client-side notification monitoring
        setupClientSideNotifications();
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
        if (currentUser == null) return;

        notificationHandler = new Handler(Looper.getMainLooper());

        notificationCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkForNewReports();
                checkForStatusChanges();
                checkForAssignments();

                // Schedule next check
                notificationHandler.postDelayed(this, NOTIFICATION_CHECK_INTERVAL);
            }
        };

        // Start periodic checks
        notificationHandler.post(notificationCheckRunnable);

        Log.d(TAG, "Client-side notification monitoring started");
    }

    /**
     * Check for new maintenance reports
     */
    private void checkForNewReports() {
        mDatabase.child("maintenance_reports")
                .orderByChild("createdAt")
                .startAt(lastNotificationCheck)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            Long createdAt = reportSnapshot.child("createdAt").getValue(Long.class);

                            if (reportId == null || createdAt == null) continue;

                            // Skip if we've already seen this report
                            if (seenReportIds.contains(reportId)) continue;

                            // Skip reports created before we started monitoring
                            if (createdAt <= lastNotificationCheck) continue;

                            // New report found!
                            String title = reportSnapshot.child("title").getValue(String.class);
                            String reportedByName = reportSnapshot.child("reportedByName").getValue(String.class);
                            String campus = reportSnapshot.child("campus").getValue(String.class);
                            String priority = reportSnapshot.child("priority").getValue(String.class);

                            // Create notification
                            createLocalNotification(
                                    "New Maintenance Report",
                                    reportedByName + " reported: " + title + " at " + campus,
                                    reportId,
                                    "new_report",
                                    priority
                            );

                            // Mark as seen
                            seenReportIds.add(reportId);
                        }

                        lastNotificationCheck = System.currentTimeMillis();
                        saveSeenReportIds();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check for new reports", error.toException());
                    }
                });
    }

    /**
     * Check for status changes in reports
     */
    private void checkForStatusChanges() {
        mDatabase.child("maintenance_reports")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            String status = reportSnapshot.child("status").getValue(String.class);
                            Long completedAt = reportSnapshot.child("completedAt").getValue(Long.class);
                            Long updatedAt = reportSnapshot.child("updatedAt").getValue(Long.class);

                            if (reportId == null || status == null) continue;

                            // Check if status was recently updated
                            Long statusChangeTime = completedAt != null && completedAt > 0 ? completedAt : updatedAt;
                            if (statusChangeTime == null || statusChangeTime <= lastNotificationCheck) continue;

                            // Check for completion
                            if ("completed".equalsIgnoreCase(status) || "partially completed".equalsIgnoreCase(status)) {
                                String notifKey = reportId + "_" + status;
                                if (!seenReportIds.contains(notifKey)) {
                                    String title = reportSnapshot.child("title").getValue(String.class);
                                    String completedByName = reportSnapshot.child("completedByName").getValue(String.class);

                                    String emoji = "completed".equalsIgnoreCase(status) ? "✅" : "⚠️";
                                    String statusText = "completed".equalsIgnoreCase(status) ? "completed" : "partially completed";

                                    createLocalNotification(
                                            "Report " + statusText.substring(0, 1).toUpperCase() + statusText.substring(1),
                                            emoji + " " + completedByName + " " + statusText + ": " + title,
                                            reportId,
                                            "status_change",
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
                        Log.e(TAG, "Failed to check status changes", error.toException());
                    }
                });
    }

    /**
     * Check for new assignments
     */
    private void checkForAssignments() {
        mDatabase.child("maintenance_reports")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            Long assignedAt = reportSnapshot.child("assignedAt").getValue(Long.class);
                            String assignedTo = reportSnapshot.child("assignedTo").getValue(String.class);

                            if (reportId == null || assignedAt == null || assignedTo == null) continue;

                            // Check if assignment is new
                            if (assignedAt <= lastNotificationCheck) continue;

                            String notifKey = reportId + "_assigned";
                            if (!seenReportIds.contains(notifKey)) {
                                String title = reportSnapshot.child("title").getValue(String.class);
                                String assignedToName = reportSnapshot.child("assignedToName").getValue(String.class);

                                createLocalNotification(
                                        "Report Assigned",
                                        "👷 " + title + " assigned to " + assignedToName,
                                        reportId,
                                        "assignment",
                                        null
                                );

                                seenReportIds.add(notifKey);
                            }
                        }

                        saveSeenReportIds();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check assignments", error.toException());
                    }
                });
    }

    /**
     * Create a local notification and store it in Firebase
     */
    private void createLocalNotification(String title, String body, String reportId, String type, String priority) {
        if (currentUser == null) return;

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
                .child(currentUser.getUid())
                .push()
                .getKey();

        if (notificationId != null) {
            mDatabase.child("user_notifications")
                    .child(currentUser.getUid())
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

    private void setupNotificationListener() {
        if (currentUser == null) return;

        notificationListener = mDatabase.child("user_notifications").child(currentUser.getUid())
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
                        unreadNotificationCount = unreadCount;
                        invalidateOptionsMenu(); // Refresh menu to update badge
                        Log.d(TAG, "Unread notifications: " + unreadCount);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load notifications", error.toException());
                    }
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

                    // Save token to database
                    saveTokenToDatabase(token);
                });
    }

    private void saveTokenToDatabase(String token) {
        if (currentUser != null) {
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("token", token);
            tokenData.put("updatedAt", System.currentTimeMillis());

            mDatabase.child("user_tokens").child(currentUser.getUid())
                    .setValue(tokenData);
        }
    }
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Admin Dashboard");
        }
    }

    private void initializeViews() {
        welcomeText = findViewById(R.id.welcomeText);
        totalUsersText = findViewById(R.id.totalReportsText);
        pendingRequestsText = findViewById(R.id.pendingRequestsText);


        usersCard = findViewById(R.id.usersCard);
        requestsCard = findViewById(R.id.requestsCard);
        campusCard = findViewById(R.id.campusCard);

        // Try to find the campus issues card (it might be using a different ID)

        if (campusIssuesCard == null) {
            // If not found, it might be the activeUsersCard or similar

        }

        // Updated button references
        manageUsersButton = findViewById(R.id.manageReportsButton);
        viewReportsButton = findViewById(R.id.reportsCard);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recentActivityRecyclerView = findViewById(R.id.recentActivityRecyclerView);


        // Setup swipe refresh
        swipeRefreshLayout.setOnRefreshListener(this::loadDashboardData);
        swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
        );
    }

    private void setupClickListeners() {
        // Card click listeners
        usersCard.setOnClickListener(v -> openReportsManagement());
        requestsCard.setOnClickListener(v -> openPendingReports());

        // Campus with issues card click listener
        if (campusIssuesCard != null) {
            campusIssuesCard.setOnClickListener(v -> openCampusesWithIssues());
        }

        // Quick action buttons
        if (manageUsersButton != null) {
            manageUsersButton.setOnClickListener(v -> openUserManagement());
        }
        if (viewReportsButton != null) {
            viewReportsButton.setOnClickListener(v -> openReportsManagement());
        }


    }

    private void setupRecyclerView() {
        recentActivities = new ArrayList<>();
        activityAdapter = new ActivityAdapter(recentActivities);
        recentActivityRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        recentActivityRecyclerView.setAdapter(activityAdapter);
    }

    private void verifyAdminAccess() {
        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        mDatabase.child("users").child(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String role = snapshot.child("role").getValue(String.class);
                            Boolean approved = snapshot.child("approved").getValue(Boolean.class);
                            Boolean roleActive = snapshot.child("roleActive").getValue(Boolean.class);

                            if (!"ADMIN".equals(role) || !Boolean.TRUE.equals(approved) ||
                                    !Boolean.TRUE.equals(roleActive)) {
                                showAccessDeniedDialog();
                            } else {
                                // User is verified admin, set welcome message
                                setWelcomeMessage(snapshot);
                                // Setup notification listener after verification
                                setupNotificationListener();
                            }
                        } else {
                            showAccessDeniedDialog();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to verify admin access", error.toException());
                        showToast("Failed to verify access. Please try again.");
                    }
                });
    }

    private void setWelcomeMessage(DataSnapshot userSnapshot) {
        String displayName = userSnapshot.child("displayName").getValue(String.class);
        if (displayName != null && !displayName.trim().isEmpty()) {
            welcomeText.setText("Welcome back, " + displayName);
        } else {
            welcomeText.setText("Welcome back, Admin");
        }
    }

    private void loadDashboardData() {
        swipeRefreshLayout.setRefreshing(true);

        // Load all dashboard metrics
        loadReportStats();
        loadPendingReports();

        loadRecentActivity();
    }

    private void loadReportStats() {
        // Load total reports from maintenance_reports collection
        mDatabase.child("maintenance_reports").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                totalReports = (int) snapshot.getChildrenCount();
                updateReportStats();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load report stats", error.toException());
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        // Load campuses with issues (count unique campuses with at least one pending/in_progress/scheduled report)
        mDatabase.child("maintenance_reports").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Clear previous data
                campusIssueDetails.clear();

                // Track unique campuses with issues and count per campus
                HashMap<String, Integer> campusIssueCounts = new HashMap<>();

                for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                    String status = reportSnapshot.child("status").getValue(String.class);
                    String campus = reportSnapshot.child("campus").getValue(String.class);

                    if (campus != null && status != null &&
                            (status.equalsIgnoreCase("pending") ||
                                    status.equalsIgnoreCase("in progress") ||
                                    status.equalsIgnoreCase("scheduled"))) {

                        // Increment count for this campus
                        campusIssueCounts.put(campus,
                                campusIssueCounts.getOrDefault(campus, 0) + 1);
                    }
                }

                // Store the details for later use
                campusIssueDetails = campusIssueCounts;
                campusesWithIssues = campusIssueCounts.size();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load campus issues", error.toException());
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void loadPendingReports() {
        // Load pending reports instead of access requests
        Query pendingQuery = mDatabase.child("maintenance_reports")
                .orderByChild("status").equalTo("pending");

        pendingQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                pendingReports = (int) snapshot.getChildrenCount();
                updatePendingReportsStats();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load pending reports", error.toException());
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }


    private void loadRecentActivity() {
        recentActivities.clear();

        // Load all types of activities and combine them
        loadReportActivities();
        loadAccessRequestActivities();
        loadChatSystemMessages();
    }

    private void loadReportActivities() {
        // Load recent maintenance reports
        Query recentReportsQuery = mDatabase.child("maintenance_reports")
                .orderByChild("createdAt")
                .limitToLast(20);

        recentReportsQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                    String reportId = reportSnapshot.getKey();
                    String title = reportSnapshot.child("title").getValue(String.class);
                    String campus = reportSnapshot.child("campus").getValue(String.class);
                    String status = reportSnapshot.child("status").getValue(String.class);
                    String reportedBy = reportSnapshot.child("reportedBy").getValue(String.class);
                    String reportedByName = reportSnapshot.child("reportedByName").getValue(String.class);
                    Long createdAt = reportSnapshot.child("createdAt").getValue(Long.class);

                    if (title != null && campus != null && status != null && createdAt != null) {
                        String displayName = reportedByName != null ? reportedByName :
                                (reportedBy != null ? reportedBy : "Unknown");

                        String activity = "📝 " + displayName + " reported: " + title +
                                " at " + campus;

                        recentActivities.add(new ActivityItem(
                                activity,
                                new Date(createdAt),
                                "report",
                                reportId
                        ));
                    }

                    // Load completion activities
                    Long completedAt = reportSnapshot.child("completedAt").getValue(Long.class);
                    String completedBy = reportSnapshot.child("completedBy").getValue(String.class);
                    String completedByName = reportSnapshot.child("completedByName").getValue(String.class);

                    if (completedAt != null && completedAt > 0) {
                        String completerName = completedByName != null ? completedByName :
                                (completedBy != null ? completedBy : "Someone");

                        String completionActivity = "✅ " + completerName + " completed: " + title;

                        if ("partially completed".equalsIgnoreCase(status)) {
                            completionActivity = "⚠️ " + completerName + " partially completed: " + title;
                        }

                        recentActivities.add(new ActivityItem(
                                completionActivity,
                                new Date(completedAt),
                                "report",
                                reportId
                        ));
                    }

                    // Load assignment activities
                    Long assignedAt = reportSnapshot.child("assignedAt").getValue(Long.class);
                    String assignedTo = reportSnapshot.child("assignedTo").getValue(String.class);
                    String assignedToName = reportSnapshot.child("assignedToName").getValue(String.class);

                    if (assignedAt != null && assignedAt > 0 && assignedTo != null) {
                        String technicianName = assignedToName != null ? assignedToName : assignedTo;
                        String assignActivity = "👷 Assigned " + title + " to " + technicianName;

                        recentActivities.add(new ActivityItem(
                                assignActivity,
                                new Date(assignedAt),
                                "report",
                                reportId
                        ));
                    }

                    // Load scheduled activities
                    Long scheduledAt = reportSnapshot.child("scheduledAt").getValue(Long.class);
                    String scheduledDate = reportSnapshot.child("scheduledDate").getValue(String.class);

                    if (scheduledAt != null && scheduledAt > 0 && scheduledDate != null) {
                        String scheduleActivity = "📅 Scheduled " + title + " for " + scheduledDate;

                        recentActivities.add(new ActivityItem(
                                scheduleActivity,
                                new Date(scheduledAt),
                                "report",
                                reportId
                        ));
                    }
                }

                sortAndUpdateActivities();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load report activities", error.toException());
            }
        });
    }

    private void loadAccessRequestActivities() {
        // Load recent access requests
        Query accessRequestsQuery = mDatabase.child("access_requests")
                .orderByChild("requestedAt")
                .limitToLast(15);

        accessRequestsQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot requestSnapshot : snapshot.getChildren()) {
                    String userId = requestSnapshot.getKey();
                    String fullName = requestSnapshot.child("fullName").getValue(String.class);
                    String role = requestSnapshot.child("role").getValue(String.class);
                    String status = requestSnapshot.child("status").getValue(String.class);
                    Long requestedAt = requestSnapshot.child("requestedAt").getValue(Long.class);
                    Long approvedAt = requestSnapshot.child("approvedAt").getValue(Long.class);

                    if (fullName != null && role != null && requestedAt != null) {
                        // Request created activity
                        String requestActivity = "🔐 " + fullName + " requested " + role + " access";

                        recentActivities.add(new ActivityItem(
                                requestActivity,
                                new Date(requestedAt),
                                "access_request",
                                userId
                        ));

                        // Approval/rejection activity
                        if (approvedAt != null && approvedAt > 0) {
                            String approvalActivity;
                            if ("approved".equalsIgnoreCase(status)) {
                                approvalActivity = "✅ Approved " + fullName + "'s " + role + " access";
                            } else if ("rejected".equalsIgnoreCase(status)) {
                                approvalActivity = "❌ Rejected " + fullName + "'s " + role + " access";
                            } else {
                                continue;
                            }

                            recentActivities.add(new ActivityItem(
                                    approvalActivity,
                                    new Date(approvedAt),
                                    "access_request",
                                    userId
                            ));
                        }
                    }
                }

                sortAndUpdateActivities();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load access request activities", error.toException());
            }
        });
    }

    private void loadChatSystemMessages() {
        // Load system messages from chats for technician actions
        mDatabase.child("chats").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    DataSnapshot messagesSnapshot = chatSnapshot.child("messages");
                    DataSnapshot metadataSnapshot = chatSnapshot.child("metadata");

                    String reportId = metadataSnapshot.child("reportId").getValue(String.class);
                    String reportTitle = metadataSnapshot.child("reportTitle").getValue(String.class);

                    if (reportId == null || reportTitle == null) continue;

                    for (DataSnapshot messageSnapshot : messagesSnapshot.getChildren()) {
                        String messageType = messageSnapshot.child("messageType").getValue(String.class);
                        String message = messageSnapshot.child("message").getValue(String.class);
                        Long timestamp = messageSnapshot.child("timestamp").getValue(Long.class);

                        if (!"system".equals(messageType) || message == null || timestamp == null) {
                            continue;
                        }

                        // Only show important system messages
                        String emoji = "";
                        String activityText = "";

                        if (message.contains("accepted")) {
                            emoji = "✅";
                            activityText = "Task accepted: " + reportTitle;
                        } else if (message.contains("started working") || message.contains("In Progress")) {
                            emoji = "🔧";
                            activityText = "Work started: " + reportTitle;
                        } else if (message.contains("COMPLETED")) {
                            emoji = "🎉";
                            activityText = "Task completed: " + reportTitle;
                        } else if (message.contains("PARTIALLY COMPLETED")) {
                            emoji = "⚠️";
                            activityText = "Task partially completed: " + reportTitle;
                        } else if (message.contains("aborted")) {
                            emoji = "🚫";
                            activityText = "Task aborted: " + reportTitle;
                        } else if (message.contains("Assigned")) {
                            emoji = "👷";
                            // Extract technician name from message if possible
                            activityText = "Assignment: " + reportTitle;
                        } else {
                            continue; // Skip other system messages
                        }

                        if (!activityText.isEmpty()) {
                            recentActivities.add(new ActivityItem(
                                    emoji + " " + activityText,
                                    new Date(timestamp),
                                    "report",
                                    reportId
                            ));
                        }
                    }
                }

                sortAndUpdateActivities();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load chat activities", error.toException());
            }
        });
    }

    private void sortAndUpdateActivities() {
        // Remove duplicates based on timestamp and description
        Map<String, ActivityItem> uniqueActivities = new HashMap<>();
        for (ActivityItem activity : recentActivities) {
            String key = activity.timestamp.getTime() + "_" + activity.description;
            if (!uniqueActivities.containsKey(key)) {
                uniqueActivities.put(key, activity);
            }
        }

        recentActivities.clear();
        recentActivities.addAll(uniqueActivities.values());

        // Sort by most recent first
        recentActivities.sort((a, b) -> b.timestamp.compareTo(a.timestamp));

        // Limit to 20 most recent
        if (recentActivities.size() > 20) {
            recentActivities = new ArrayList<>(recentActivities.subList(0, 20));
        }

        activityAdapter.updateData(recentActivities);
        swipeRefreshLayout.setRefreshing(false);
    }


    private void updateReportStats() {
        totalUsersText.setText(String.valueOf(totalReports));
    }

    private void updatePendingReportsStats() {
        pendingRequestsText.setText(String.valueOf(pendingReports));
    }

    private void openReportsManagement() {
        Intent intent = new Intent(this, ReportManagementActivity.class);
        startActivity(intent);
    }

    private void openUserManagement() {
        Intent intent = new Intent(this, UserManagementActivity.class);
        startActivity(intent);
    }

    private void openPendingReports() {
        // Open report management filtered by pending status
        Intent intent = new Intent(this, ReportManagementActivity.class);
        intent.putExtra("filter_status", "pending");
        startActivity(intent);
    }



    private void openCampusesWithIssues() {
        // Check if there are any campuses with issues
        if (campusesWithIssues == 0) {
            showToast("No campuses with active issues");
            return;
        }

        // Open the new CampusIssuesActivity
        Intent intent = new Intent(this, CampusIssuesActivity.class);
        startActivity(intent);
    }

    private void showCampusIssuesDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Campuses with Active Issues");

        // Build the message with campus details
        StringBuilder message = new StringBuilder();
        message.append("Select a campus to view its reports:\n\n");

        // Sort campuses by issue count (highest first)
        List<Map.Entry<String, Integer>> sortedCampuses = new ArrayList<>(campusIssueDetails.entrySet());
        sortedCampuses.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // Create array of campus names for dialog
        String[] campusNames = new String[sortedCampuses.size() + 1];
        campusNames[0] = "View All Problem Campuses";

        int index = 1;
        for (Map.Entry<String, Integer> entry : sortedCampuses) {
            String campus = entry.getKey();
            int issueCount = entry.getValue();

            // Add warning emoji for campuses with many issues
            String indicator = "";
            if (issueCount >= 5) {
                indicator = " 🔴"; // Critical
            } else if (issueCount >= 3) {
                indicator = " 🟡"; // Warning
            } else {
                indicator = " 🟢"; // Normal
            }

            campusNames[index] = campus + " (" + issueCount + " issue" +
                    (issueCount > 1 ? "s" : "") + ")" + indicator;
            index++;
        }

        // Create dialog with list of campuses
        builder.setItems(campusNames, (dialog, which) -> {
            if (which == 0) {
                // View all campuses with problems
                Intent intent = new Intent(this, ReportManagementActivity.class);
                intent.putExtra("filter_problem_campuses", true);
                startActivity(intent);
            } else {
                // View specific campus reports
                String selectedCampus = sortedCampuses.get(which - 1).getKey();
                Intent intent = new Intent(this, ReportManagementActivity.class);
                intent.putExtra("filter_campus", selectedCampus);
                intent.putExtra("filter_active_only", true); // Show only active issues
                startActivity(intent);
            }
        });

        builder.setNegativeButton("Cancel", null);

        // Add a neutral button for statistics
        builder.setNeutralButton("Statistics", (dialog, which) -> {
            showCampusStatistics();
        });

        builder.show();
    }

    private void showCampusStatistics() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Campus Issue Statistics");

        StringBuilder stats = new StringBuilder();

        // Calculate totals
        int totalActiveIssues = 0;
        int criticalCampuses = 0;
        int warningCampuses = 0;

        for (Map.Entry<String, Integer> entry : campusIssueDetails.entrySet()) {
            int count = entry.getValue();
            totalActiveIssues += count;

            if (count >= 5) {
                criticalCampuses++;
            } else if (count >= 3) {
                warningCampuses++;
            }
        }

        // Build statistics message
        stats.append("📊 Overall Statistics\n\n");
        stats.append("Total Active Issues: ").append(totalActiveIssues).append("\n");
        stats.append("Affected Campuses: ").append(campusesWithIssues).append("\n");
        stats.append("Average Issues per Campus: ")
                .append(String.format("%.1f", (float)totalActiveIssues / campusesWithIssues)).append("\n\n");

        stats.append("🔴 Critical (5+ issues): ").append(criticalCampuses).append(" campus(es)\n");
        stats.append("🟡 Warning (3-4 issues): ").append(warningCampuses).append(" campus(es)\n");
        stats.append("🟢 Normal (1-2 issues): ")
                .append(campusesWithIssues - criticalCampuses - warningCampuses).append(" campus(es)\n\n");

        // Add top 3 campuses with most issues
        if (!campusIssueDetails.isEmpty()) {
            stats.append("🏆 Top Affected Campuses:\n");

            List<Map.Entry<String, Integer>> sortedCampuses = new ArrayList<>(campusIssueDetails.entrySet());
            sortedCampuses.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            int limit = Math.min(3, sortedCampuses.size());
            for (int i = 0; i < limit; i++) {
                Map.Entry<String, Integer> entry = sortedCampuses.get(i);
                stats.append(i + 1).append(". ").append(entry.getKey())
                        .append(" - ").append(entry.getValue()).append(" issues\n");
            }
        }

        builder.setMessage(stats.toString());
        builder.setPositiveButton("View Reports", (dialog, which) -> {
            Intent intent = new Intent(this, ReportManagementActivity.class);
            intent.putExtra("filter_problem_campuses", true);
            startActivity(intent);
        });
        builder.setNegativeButton("Close", null);

        builder.show();
    }

    private void showAddCampusDialog() {
        // Simple dialog to add a new campus
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Campus");

        // Create an EditText for campus name input
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter campus name");
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String campusName = input.getText().toString().trim();
            if (!campusName.isEmpty()) {
                addNewCampus(campusName);
            } else {
                showToast("Campus name cannot be empty");
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void addNewCampus(String campusName) {
        // Add campus to Firebase
        String campusId = mDatabase.child("campuses").push().getKey();
        if (campusId != null) {
            mDatabase.child("campuses").child(campusId).setValue(campusName)
                    .addOnSuccessListener(aVoid -> {
                        showToast("Campus added successfully");
                        loadDashboardData();  // Refresh data
                    })
                    .addOnFailureListener(e -> {
                        showToast("Failed to add campus: " + e.getMessage());
                    });
        }
    }

    private void showAccessDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Access Denied")
                .setMessage("You don't have admin privileges to access this section.")
                .setPositiveButton("OK", (dialog, which) -> redirectToLogin())
                .setCancelable(false)
                .show();
    }

    private void redirectToLogin() {
        mAuth.signOut();

        // Clear saved preferences
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

        Intent intent = new Intent(this, Login.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.admin_menu, menu);

        // Update notification icon based on unread count
        MenuItem notificationItem = menu.findItem(R.id.action_notifications);
        if (notificationItem != null) {
            if (unreadNotificationCount > 0) {
                notificationItem.setIcon(R.drawable.ic_notifications_active);
                // You can set a badge here if you have a badge drawable
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
        } else if (id == R.id.action_refresh) {
            loadDashboardData();
            return true;
        } else if (id == R.id.action_profile) {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_logout) {
            showLogoutDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Add method to show notifications dialog
    private void showNotificationsDialog() {
        if (currentUser == null) return;

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setMessage("Loading notifications...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        mDatabase.child("user_notifications").child(currentUser.getUid())
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
                            new AlertDialog.Builder(Admin.this)
                                    .setTitle("Notifications")
                                    .setMessage("No notifications yet")
                                    .setPositiveButton("OK", null)
                                    .show();
                            return;
                        }

                        // Reverse to show newest first
                        Collections.reverse(notifications);

                        showNotificationsList(notifications);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        showToast("Failed to load notifications");
                    }
                });
    }

    // Add method to show notifications list
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
            clearAllNotifications();
            showToast("All notifications cleared");
        });

        builder.setNeutralButton("Close", null);
        builder.show();
    }

    // Add method to show notification detail
    private void showNotificationDetail(Map<String, Object> notification) {
        String notifId = (String) notification.get("id");
        String title = (String) notification.get("title");
        String body = (String) notification.get("body");
        String reportId = (String) notification.get("reportId");

        // Mark as read
        if (currentUser != null && notifId != null) {
            markNotificationAsRead(notifId);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(body);

        if (reportId != null && !reportId.isEmpty()) {
            builder.setPositiveButton("View Report", (dialog, which) -> {
                loadAndShowReportDetails(reportId);
            });
            builder.setNeutralButton("Open Chat", (dialog, which) -> {
                openReportChat(reportId);
            });
        }

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    // Add helper method to load and show report details
    private void loadAndShowReportDetails(String reportId) {
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Loading report details...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        mDatabase.child("maintenance_reports").child(reportId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressDialog.dismiss();

                        if (!snapshot.exists()) {
                            showToast("Report not found");
                            return;
                        }

                        showReportDetailsDialog(snapshot, reportId);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        showToast("Failed to load report: " + error.getMessage());
                    }
                });
    }

    // Add method to show report details dialog
    private void showReportDetailsDialog(DataSnapshot reportSnapshot, String reportId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 32, 40, 32);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

        // Get report data
        String title = reportSnapshot.child("title").getValue(String.class);
        String description = reportSnapshot.child("description").getValue(String.class);
        String location = reportSnapshot.child("location").getValue(String.class);
        String campus = reportSnapshot.child("campus").getValue(String.class);
        String status = reportSnapshot.child("status").getValue(String.class);
        String priority = reportSnapshot.child("priority").getValue(String.class);
        String reportedBy = reportSnapshot.child("reportedByName").getValue(String.class);
        if (reportedBy == null) {
            reportedBy = reportSnapshot.child("reportedBy").getValue(String.class);
        }
        Long createdAt = reportSnapshot.child("createdAt").getValue(Long.class);
        Long completedAt = reportSnapshot.child("completedAt").getValue(Long.class);
        String scheduledDate = reportSnapshot.child("scheduledDate").getValue(String.class);
        String scheduledTime = reportSnapshot.child("scheduledTime").getValue(String.class);
        String assignedTo = reportSnapshot.child("assignedToName").getValue(String.class);

        // Build details
        TextView titleHeader = new TextView(this);
        titleHeader.setText(title != null ? title : "No Title");
        titleHeader.setTextSize(20);
        titleHeader.setTextColor(getResources().getColor(android.R.color.black));
        titleHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        titleHeader.setPadding(0, 0, 0, 16);
        layout.addView(titleHeader);

        // Description
        addDetailItem(layout, "📋 Description", description != null ? description : "N/A");

        // Location
        addDetailItem(layout, "📍 Location", location != null ? location : "N/A");

        // Campus
        addDetailItem(layout, "🏫 Campus", campus != null ? campus : "N/A");

        // Status
        addDetailItem(layout, "📊 Status", status != null ? status.toUpperCase() : "N/A");

        // Priority
        addDetailItem(layout, "⚠️ Priority", priority != null ? priority.toUpperCase() : "N/A");

        // Reported By
        addDetailItem(layout, "👤 Reported By", reportedBy != null ? reportedBy : "Unknown");

        // Created At
        if (createdAt != null) {
            addDetailItem(layout, "📅 Created", dateFormat.format(new Date(createdAt)));
        }

        // Scheduled Info
        if (scheduledDate != null && !scheduledDate.isEmpty()) {
            String scheduleInfo = scheduledDate;
            if (scheduledTime != null && !scheduledTime.isEmpty()) {
                scheduleInfo += " at " + scheduledTime;
            }
            addDetailItem(layout, "🗓️ Scheduled", scheduleInfo);

            if (assignedTo != null && !assignedTo.isEmpty()) {
                addDetailItem(layout, "👷 Assigned To", assignedTo);
            }
        }

        // Completed At
        if (completedAt != null && completedAt > 0) {
            addDetailItem(layout, "✅ Completed", dateFormat.format(new Date(completedAt)));
        }

        scrollView.addView(layout);
        builder.setView(scrollView);
        builder.setTitle("Report Details");

        builder.setPositiveButton("View in Management", (dialog, which) -> {
            Intent intent = new Intent(Admin.this, ReportManagementActivity.class);
            intent.putExtra("reportId", reportId);
            startActivity(intent);
        });

        builder.setNeutralButton("Open Chat", (dialog, which) -> {
            openReportChat(reportId);
        });

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    // Helper method to add detail items
    private void addDetailItem(LinearLayout layout, String label, String value) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setPadding(0, 8, 0, 8);

        TextView labelText = new TextView(this);
        labelText.setText(label);
        labelText.setTextSize(14);
        labelText.setTextColor(getResources().getColor(android.R.color.darker_gray));
        labelText.setTypeface(null, android.graphics.Typeface.BOLD);
        itemLayout.addView(labelText);

        TextView valueText = new TextView(this);
        valueText.setText(value);
        valueText.setTextSize(14);
        valueText.setTextColor(getResources().getColor(android.R.color.black));
        valueText.setPadding(0, 4, 0, 0);
        itemLayout.addView(valueText);

        layout.addView(itemLayout);

        // Add divider
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) divider.getLayoutParams();
        params.setMargins(0, 8, 0, 8);
        divider.setLayoutParams(params);
        layout.addView(divider);
    }

    // Add method to open report chat
    private void openReportChat(String reportId) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("reportId", reportId);
        startActivity(intent);
    }

    // Add method to mark notification as read
    private void markNotificationAsRead(String notificationId) {
        if (currentUser != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("read", true);
            updates.put("readAt", System.currentTimeMillis());

            mDatabase.child("user_notifications")
                    .child(currentUser.getUid())
                    .child(notificationId)
                    .updateChildren(updates);
        }
    }

    // Add method to clear all notifications
    private void clearAllNotifications() {
        if (currentUser != null) {
            mDatabase.child("user_notifications")
                    .child(currentUser.getUid())
                    .removeValue()
                    .addOnSuccessListener(aVoid -> {
                        unreadNotificationCount = 0;
                        invalidateOptionsMenu();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to clear notifications", e);
                        showToast("Failed to clear notifications");
                    });
        }
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
    protected void onDestroy() {
        super.onDestroy();

        // Remove notification listener to prevent memory leaks
        if (notificationListener != null && currentUser != null) {
            mDatabase.child("user_notifications")
                    .child(currentUser.getUid())
                    .removeEventListener(notificationListener);
        }

        // Stop notification checks
        if (notificationHandler != null && notificationCheckRunnable != null) {
            notificationHandler.removeCallbacks(notificationCheckRunnable);
        }
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    mAuth.signOut();

                    // Clear saved preferences
                    SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.clear();
                    editor.apply();

                    Intent intent = new Intent(Admin.this, Login.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardData();

        // Resume notification checks when app is resumed
        if (notificationHandler != null && notificationCheckRunnable != null) {
            notificationHandler.post(notificationCheckRunnable);
        }
    }

    // Activity Item class for recent activities
    public static class ActivityItem {
        public String description;
        public Date timestamp;
        public String type; // "report", "access_request"
        public String id;   // reportId or userId

        public ActivityItem(String description, Date timestamp) {
            this.description = description;
            this.timestamp = timestamp;
            this.type = "other";
            this.id = null;
        }

        public ActivityItem(String description, Date timestamp, String type, String id) {
            this.description = description;
            this.timestamp = timestamp;
            this.type = type;
            this.id = id;
        }
    }

    // Activity Adapter for RecyclerView
    private class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ViewHolder> {
        private List<ActivityItem> activities;
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

        public ActivityAdapter(List<ActivityItem> activities) {
            this.activities = activities;
        }

        public void updateData(List<ActivityItem> newActivities) {
            this.activities = newActivities;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_activity, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ActivityItem activity = activities.get(position);
            holder.descriptionText.setText(activity.description);
            holder.timestampText.setText(dateFormat.format(activity.timestamp));

            // Make item clickable if it has an ID
            if (activity.id != null && !activity.id.isEmpty()) {
                holder.itemView.setClickable(true);
                holder.itemView.setFocusable(true);

                // Add ripple effect
                holder.itemView.setBackgroundResource(R.drawable.ripple_effect);

                holder.itemView.setOnClickListener(v -> {
                    if ("report".equals(activity.type)) {
                        openReportDetails(activity.id);
                    } else if ("access_request".equals(activity.type)) {
                        openAccessRequestDetails(activity.id);
                    }
                });
            } else {
                holder.itemView.setClickable(false);
                holder.itemView.setFocusable(false);
                holder.itemView.setBackground(null);
            }
        }

        @Override
        public int getItemCount() {
            return activities.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView descriptionText;
            TextView timestampText;

            ViewHolder(View itemView) {
                super(itemView);
                descriptionText = itemView.findViewById(R.id.activityDescription);
                timestampText = itemView.findViewById(R.id.activityTimestamp);
            }
        }
    }

    // ADD methods to open details:
    private void openReportDetails(String reportId) {
        // Show loading
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Loading report details...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        mDatabase.child("maintenance_reports").child(reportId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressDialog.dismiss();

                        if (!snapshot.exists()) {
                            showToast("Report not found");
                            return;
                        }

                        // Build report details
                        String title = snapshot.child("title").getValue(String.class);
                        String description = snapshot.child("description").getValue(String.class);
                        String location = snapshot.child("location").getValue(String.class);
                        String campus = snapshot.child("campus").getValue(String.class);
                        String status = snapshot.child("status").getValue(String.class);
                        String priority = snapshot.child("priority").getValue(String.class);
                        String reportedBy = snapshot.child("reportedBy").getValue(String.class);
                        Long createdAt = snapshot.child("createdAt").getValue(Long.class);

                        StringBuilder details = new StringBuilder();
                        details.append("Title: ").append(title != null ? title : "N/A").append("\n\n");
                        details.append("Description: ").append(description != null ? description : "N/A").append("\n\n");
                        details.append("Location: ").append(location != null ? location : "N/A").append("\n");
                        details.append("Campus: ").append(campus != null ? campus : "N/A").append("\n");
                        details.append("Status: ").append(status != null ? status.toUpperCase() : "N/A").append("\n");
                        details.append("Priority: ").append(priority != null ? priority.toUpperCase() : "N/A").append("\n");
                        details.append("Reported by: ").append(reportedBy != null ? reportedBy : "Unknown").append("\n");

                        if (createdAt != null) {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
                            details.append("Created: ").append(dateFormat.format(new Date(createdAt)));
                        }

                        new AlertDialog.Builder(Admin.this)
                                .setTitle("Report Details")
                                .setMessage(details.toString())
                                .setPositiveButton("View in Report Management", (dialog, which) -> {
                                    Intent intent = new Intent(Admin.this, ReportManagementActivity.class);
                                    startActivity(intent);
                                })
                                .setNegativeButton("Close", null)
                                .show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        showToast("Failed to load report: " + error.getMessage());
                    }
                });
    }

    private void openAccessRequestDetails(String userId) {
        // Show loading
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Loading access request...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        mDatabase.child("access_requests").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressDialog.dismiss();

                        if (!snapshot.exists()) {
                            showToast("Access request not found");
                            return;
                        }

                        String fullName = snapshot.child("fullName").getValue(String.class);
                        String email = snapshot.child("email").getValue(String.class);
                        String phone = snapshot.child("phone").getValue(String.class);
                        String role = snapshot.child("role").getValue(String.class);
                        String department = snapshot.child("department").getValue(String.class);
                        String campus = snapshot.child("campus").getValue(String.class);
                        String status = snapshot.child("status").getValue(String.class);
                        Long requestedAt = snapshot.child("requestedAt").getValue(Long.class);

                        StringBuilder details = new StringBuilder();
                        details.append("Name: ").append(fullName != null ? fullName : "N/A").append("\n");
                        details.append("Email: ").append(email != null ? email : "N/A").append("\n");
                        details.append("Phone: ").append(phone != null ? phone : "N/A").append("\n");
                        details.append("Role: ").append(role != null ? role : "N/A").append("\n");

                        if (department != null && !department.isEmpty()) {
                            details.append("Department: ").append(department).append("\n");
                        }

                        details.append("Campus: ").append(campus != null ? campus : "N/A").append("\n");
                        details.append("Status: ").append(status != null ? status.toUpperCase() : "PENDING").append("\n");

                        if (requestedAt != null) {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
                            details.append("Requested: ").append(dateFormat.format(new Date(requestedAt)));
                        }

                        new AlertDialog.Builder(Admin.this)
                                .setTitle("Access Request Details")
                                .setMessage(details.toString())
                                .setPositiveButton("View in User Management", (dialog, which) -> {
                                    Intent intent = new Intent(Admin.this, UserManagementActivity.class);
                                    startActivity(intent);
                                })
                                .setNegativeButton("Close", null)
                                .show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        showToast("Failed to load request: " + error.getMessage());
                    }
                });
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Exit the app
                    super.onBackPressed();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    // Dismiss the dialog
                    dialog.dismiss();
                })
                .setCancelable(true)
                .show();
    }

}