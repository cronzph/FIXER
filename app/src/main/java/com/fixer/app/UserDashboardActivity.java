package com.fixer.app;

import android.Manifest;
import android.app.MediaRouteButton;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import android.widget.ScrollView;
import androidx.cardview.widget.CardView;
import android.os.Handler;
import android.os.Looper;
import java.util.HashSet;
import java.util.Set;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserDashboardActivity extends AppCompatActivity {

    private static final String TAG = "UserDashboard";
    private static final String PREFS_NAME = "FixerPrefs";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private static final int REQUEST_COMPLETION_IMAGE_CAPTURE = 3;
    private static final int REQUEST_COMPLETION_IMAGE_PICK = 4;
    private static final int MAX_IMAGE_SIZE = 50000; // Maximum width/height in pixels
    private boolean isSubmittingReport = false;
    private static final String PREFS_LAST_NOTIFICATION_CHECK = "last_notification_check";
    private static final String PREFS_SEEN_REPORT_IDS = "seen_report_ids";
    private static final long NOTIFICATION_CHECK_INTERVAL = 1000; // 30 seconds
    private Handler notificationHandler;
    private Runnable notificationCheckRunnable;
    private Set<String> seenReportIds = new HashSet<>();
    private long lastNotificationCheck = 0;
    private int unreadNotificationCount = 0;

    // UI Components
    private TextView welcomeText;
    private TextView campusText;
    private TextView totalReportsText;
    private TextView pendingReportsText;
    private TextView completedReportsText;
    private CardView totalCard, pendingCard, completedCard;
    private RecyclerView reportsRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton fabAddReport;
    private TextView emptyStateText;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;

    // User Data
    private String userCampus = "";
    private String userName = "";
    private String userDepartment = "";

    // Reports Data
    private List<Report> userReports;
    private ReportAdapter reportAdapter;
    private int totalReports = 0;
    private int pendingReports = 0;
    private int completedReports = 0;
    private TextView rejectedReportsText;
    private CardView rejectedCard;
    private int rejectedReports = 0;


    // Photo Upload
    private String selectedImageBase64 = null;
    private String completionImageBase64 = null;
    private ImageView selectedImageView;
    private MaterialButton removePhotoButton;
    private AlertDialog currentReportDialog = null;
    private AlertDialog currentCompletionDialog = null;
    private View currentDialogView = null;
    private View currentCompletionDialogView = null;
    private PhotoSelectionCallback photoCallback;
    private String currentReportIdForCompletion = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_user_dashboard);

        // Initialize Firebase

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentUser = mAuth.getCurrentUser();

        // Check if user is logged in
        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        // Setup toolbar
        setupToolbar();

        // Initialize UI components
        initializeViews();

        // Setup click listeners
        setupClickListeners();

        // Setup RecyclerView
        setupRecyclerView();

        // Load user data and reports
        loadUserData();

        // Request notification permission for Android 13+
        requestNotificationPermission();

        // Get and save FCM token
        getFCMToken();

        // Setup notification listener
        setupNotificationListener();

        // Load seen report IDs from preferences
        loadSeenReportIds();

// Setup client-side notification monitoring
        setupClientSideNotifications();
    }

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
                checkForReportUpdates();
                checkForAssignments();
                checkForScheduleChanges();
                checkForCompletions();

                // Schedule next check
                notificationHandler.postDelayed(this, NOTIFICATION_CHECK_INTERVAL);
            }
        };

        // Start periodic checks
        notificationHandler.post(notificationCheckRunnable);

        Log.d(TAG, "Client-side notification monitoring started");
    }

    /**
     * Check for updates to user's reports
     */
    private void checkForReportUpdates() {
        mDatabase.child("maintenance_reports")
                .orderByChild("reportedByUid")
                .equalTo(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            String status = reportSnapshot.child("status").getValue(String.class);
                            Long updatedAt = reportSnapshot.child("updatedAt").getValue(Long.class);

                            if (reportId == null || status == null) continue;

                            // Check if status was recently updated
                            if (updatedAt != null && updatedAt > lastNotificationCheck) {
                                String notifKey = reportId + "_status_" + status;

                                if (!seenReportIds.contains(notifKey)) {
                                    String title = reportSnapshot.child("title").getValue(String.class);

                                    createLocalNotification(
                                            "Report Status Updated",
                                            "Your report \"" + title + "\" status changed to: " + status.toUpperCase(),
                                            reportId,
                                            "status_update",
                                            null
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
                        Log.e(TAG, "Failed to check report updates", error.toException());
                    }
                });
    }

    /**
     * Check for assignments to user's reports
     */
    private void checkForAssignments() {
        mDatabase.child("maintenance_reports")
                .orderByChild("reportedByUid")
                .equalTo(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            Long assignedAt = reportSnapshot.child("assignedAt").getValue(Long.class);
                            String assignedTo = reportSnapshot.child("assignedTo").getValue(String.class);

                            if (reportId == null || assignedAt == null || assignedTo == null) continue;

                            // Check if assignment is new
                            if (assignedAt > lastNotificationCheck) {
                                String notifKey = reportId + "_assigned";

                                if (!seenReportIds.contains(notifKey)) {
                                    String title = reportSnapshot.child("title").getValue(String.class);
                                    String assignedToName = reportSnapshot.child("assignedToName").getValue(String.class);

                                    createLocalNotification(
                                            "Technician Assigned",
                                            "👷 " + assignedToName + " has been assigned to your report: " + title,
                                            reportId,
                                            "assignment",
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
                        Log.e(TAG, "Failed to check assignments", error.toException());
                    }
                });
    }

    /**
     * Check for schedule changes
     */
    private void checkForScheduleChanges() {
        mDatabase.child("maintenance_reports")
                .orderByChild("reportedByUid")
                .equalTo(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            Long scheduledAt = reportSnapshot.child("scheduledAt").getValue(Long.class);
                            String scheduledDate = reportSnapshot.child("scheduledDate").getValue(String.class);

                            if (reportId == null || scheduledAt == null || scheduledDate == null) continue;

                            // Check if schedule is new
                            if (scheduledAt > lastNotificationCheck) {
                                String notifKey = reportId + "_scheduled";

                                if (!seenReportIds.contains(notifKey)) {
                                    String title = reportSnapshot.child("title").getValue(String.class);
                                    String scheduledTime = reportSnapshot.child("scheduledTime").getValue(String.class);

                                    String scheduleInfo = scheduledDate;
                                    if (scheduledTime != null && !scheduledTime.isEmpty()) {
                                        scheduleInfo += " at " + scheduledTime;
                                    }

                                    createLocalNotification(
                                            "Maintenance Scheduled",
                                            "📅 Your report \"" + title + "\" has been scheduled for: " + scheduleInfo,
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
                        Log.e(TAG, "Failed to check schedule changes", error.toException());
                    }
                });
    }

    /**
     * Check for report completions
     */
    private void checkForCompletions() {
        mDatabase.child("maintenance_reports")
                .orderByChild("reportedByUid")
                .equalTo(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String reportId = reportSnapshot.getKey();
                            String status = reportSnapshot.child("status").getValue(String.class);
                            Long completedAt = reportSnapshot.child("completedAt").getValue(Long.class);

                            if (reportId == null || status == null || completedAt == null) continue;

                            // Check if completion is new
                            if (completedAt > lastNotificationCheck &&
                                    ("completed".equalsIgnoreCase(status) || "partially completed".equalsIgnoreCase(status))) {

                                String notifKey = reportId + "_completed";

                                if (!seenReportIds.contains(notifKey)) {
                                    String title = reportSnapshot.child("title").getValue(String.class);
                                    String completedByName = reportSnapshot.child("completedByName").getValue(String.class);

                                    String emoji = "completed".equalsIgnoreCase(status) ? "✅" : "⚠️";
                                    String statusText = "completed".equalsIgnoreCase(status) ? "completed" : "partially completed";

                                    createLocalNotification(
                                            "Report " + statusText.substring(0, 1).toUpperCase() + statusText.substring(1),
                                            emoji + " Your report \"" + title + "\" has been " + statusText + " by " + completedByName,
                                            reportId,
                                            "completion",
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
                        Log.e(TAG, "Failed to check completions", error.toException());
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
        if (currentUser != null) {
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("token", token);
            tokenData.put("updatedAt", System.currentTimeMillis());

            mDatabase.child("user_tokens").child(currentUser.getUid())
                    .setValue(tokenData);
        }
    }

    private void setupNotificationListener() {
        if (currentUser == null) return;

        mDatabase.child("user_notifications").child(currentUser.getUid())
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
                        invalidateOptionsMenu(); // Refresh menu to update badge
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load notifications", error.toException());
                    }
                });
    }


    private void updateNotificationBadge(int count) {
        // Update toolbar badge if you have one
        Log.d(TAG, "Unread notifications: " + count);
        // You can add visual updates here
    }




    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("F.I.X.E.R Dashboard");
        }
    }

    private void initializeViews() {
        welcomeText = findViewById(R.id.welcomeText);
        campusText = findViewById(R.id.campusText);
        totalReportsText = findViewById(R.id.totalReportsText);
        pendingReportsText = findViewById(R.id.pendingReportsText);
        completedReportsText = findViewById(R.id.completedReportsText);

        totalCard = findViewById(R.id.totalCard);
        pendingCard = findViewById(R.id.pendingCard);
        completedCard = findViewById(R.id.completedCard);

        rejectedReportsText = findViewById(R.id.rejectedReportsText);
        rejectedCard = findViewById(R.id.rejectedCard);


        reportsRecyclerView = findViewById(R.id.reportsRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        fabAddReport = findViewById(R.id.fabAddReport);
        emptyStateText = findViewById(R.id.emptyStateText);

        // Setup swipe refresh
        swipeRefreshLayout.setOnRefreshListener(this::loadUserReports);
        swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light
        );
    }

    private void setupClickListeners() {
        fabAddReport.setOnClickListener(v -> showAddReportDialog());

        totalCard.setOnClickListener(v -> filterReports("all"));
        pendingCard.setOnClickListener(v -> filterReports("pending"));
        completedCard.setOnClickListener(v -> filterReports("completed"));
        rejectedCard.setOnClickListener(v -> filterReports("rejected"));

    }

    private void setupRecyclerView() {
        userReports = new ArrayList<>();
        reportAdapter = new ReportAdapter(userReports);
        reportsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        reportsRecyclerView.setAdapter(reportAdapter);
    }

    private void loadUserData() {
        if (currentUser == null) return;

        mDatabase.child("users").child(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            userName = snapshot.child("displayName").getValue(String.class);
                            userCampus = "Meneses Campus";
                            userDepartment = snapshot.child("department").getValue(String.class);

                            // Update UI
                            welcomeText.setText("Welcome, " + (userName != null ? userName : "User"));
                            campusText.setText(userCampus != null ? userCampus : "Campus Not Set");

                            // Load reports for this campus
                            loadUserReports();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load user data", error.toException());
                        showToast("Failed to load user data");
                    }
                });
    }

    private void loadUserReports() {
        if (userCampus == null || userCampus.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            updateEmptyState();

            return;
        }

        swipeRefreshLayout.setRefreshing(true);

        // Load only reports created by current user
        Query reportsQuery = mDatabase.child("maintenance_reports")
                .orderByChild("reportedByUid").equalTo(currentUser.getUid());

        reportsQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                userReports.clear();
                totalReports = 0;
                pendingReports = 0;
                completedReports = 0;
                rejectedReports = 0;


                for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                    Report report = new Report();
                    report.reportId = reportSnapshot.getKey();
                    report.title = reportSnapshot.child("title").getValue(String.class);
                    report.description = reportSnapshot.child("description").getValue(String.class);
                    report.location = reportSnapshot.child("location").getValue(String.class);
                    report.campus = reportSnapshot.child("campus").getValue(String.class);
                    report.priority = reportSnapshot.child("priority").getValue(String.class);
                    report.status = reportSnapshot.child("status").getValue(String.class);
                    report.reportedBy = reportSnapshot.child("reportedBy").getValue(String.class);
                    report.reportedByEmail = reportSnapshot.child("reportedByEmail").getValue(String.class);
                    report.reportedByUid = reportSnapshot.child("reportedByUid").getValue(String.class);
                    report.department = reportSnapshot.child("department").getValue(String.class);
                    report.photoBase64 = reportSnapshot.child("photoBase64").getValue(String.class);
                    report.completionPhotoBase64 = reportSnapshot.child("completionPhotoBase64").getValue(String.class);
                    report.scheduledDate = reportSnapshot.child("scheduledDate").getValue(String.class);
                    report.scheduledTime = reportSnapshot.child("scheduledTime").getValue(String.class);
                    report.assignedTo = reportSnapshot.child("assignedTo").getValue(String.class);
                    report.technicianEmail = reportSnapshot.child("technicianEmail").getValue(String.class);
                    report.technicianPhone = reportSnapshot.child("technicianPhone").getValue(String.class);

                    Long createdAt = reportSnapshot.child("createdAt").getValue(Long.class);
                    report.createdAt = createdAt != null ? createdAt : 0L;

                    Long completedAt = reportSnapshot.child("completedAt").getValue(Long.class);
                    report.completedAt = completedAt != null ? completedAt : 0L;

                    userReports.add(report);

                    // Count reports by status
                    totalReports++;
                    if ("pending".equalsIgnoreCase(report.status)) {
                        pendingReports++;
                    } else if ("completed".equalsIgnoreCase(report.status)) {
                        completedReports++;
                    } else if ("rejected".equalsIgnoreCase(report.status)) {
                        rejectedReports++;
                    }

                }

                // Sort by most recent first
                userReports.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));

                // Update UI
                updateStats();
                reportAdapter.updateData(userReports);
                updateEmptyState();
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load reports", error.toException());
               // showToast("Failed to load reports");
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void updateStats() {
        totalReportsText.setText(String.valueOf(totalReports));
        pendingReportsText.setText(String.valueOf(pendingReports));
        completedReportsText.setText(String.valueOf(completedReports));
        rejectedReportsText.setText(String.valueOf(rejectedReports));

    }

    private void updateEmptyState() {
        if (userReports.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            reportsRecyclerView.setVisibility(View.GONE);
            emptyStateText.setText("No reports found.\nTap + to create your first report.");
        } else {
            emptyStateText.setVisibility(View.GONE);
            reportsRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void filterReports(String filter) {
        if ("all".equals(filter)) {
            reportAdapter.setFilter(null);
        } else {
            reportAdapter.setFilter(filter);
        }
    }

    private void showAddReportDialog() {
        // Reset submission flag when opening dialog
        isSubmittingReport = false;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_report, null);
        builder.setView(dialogView);

        currentDialogView = dialogView;

        TextInputEditText titleInput = dialogView.findViewById(R.id.titleInput);
        TextInputEditText descriptionInput = dialogView.findViewById(R.id.descriptionInput);
        Spinner locationSpinner = dialogView.findViewById(R.id.locationSpinner);
        Spinner prioritySpinner = dialogView.findViewById(R.id.prioritySpinner);
        MaterialButton addPhotoButton = dialogView.findViewById(R.id.addPhotoButton);
        ImageView photoImageView = dialogView.findViewById(R.id.selectedImageView);
        MaterialButton removeButton = dialogView.findViewById(R.id.removePhotoButton);

        // Reset selected image
        selectedImageBase64 = null;
        if (photoImageView != null) {
            photoImageView.setVisibility(View.GONE);
            photoImageView.setImageBitmap(null);
        }
        if (removeButton != null) {
            removeButton.setVisibility(View.GONE);
        }

        String[] locations = {
                "Select Location",
                "Audio Visual Room", "Canteen", "Clinic",
                "Computer Laboratory 1", "Computer Laboratory 2",
                "Dambana", "Dean's Office", "Faculty", "Guard House",
                "Guidance", "Hostel", "Infirmary", "Library",
                "LSC Office", "NB1A", "NB1B", "NB2A", "Science Laboratory"
        };

        ArrayAdapter<String> locationAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, locations);
        locationSpinner.setAdapter(locationAdapter);
        locationSpinner.setSelection(0);

        String[] priorities = {
                "Select Priority Level",
                "Low - Minor issues, no urgency",
                "Medium - Normal maintenance",
                "High - Affects operations",
                "Critical - Safety hazard/Emergency"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, priorities);
        prioritySpinner.setAdapter(adapter);
        prioritySpinner.setSelection(0);

        prioritySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 4) { // Critical selected (index 4)
                    showCriticalPriorityWarning();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        if (addPhotoButton != null) {
            addPhotoButton.setText("Add Photo (Required)");
            addPhotoButton.setOnClickListener(v -> showPhotoOptions());
        }

        if (removeButton != null) {
            removeButton.setOnClickListener(v -> {
                selectedImageBase64 = null;
                if (photoImageView != null) {
                    photoImageView.setVisibility(View.GONE);
                    photoImageView.setImageBitmap(null);
                }
                removeButton.setVisibility(View.GONE);
            });
        }

        builder.setTitle("Report Issue - " + userCampus + " (Photo Required)");
        builder.setPositiveButton("Submit", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Cancel")
                    .setMessage("Are you sure you want to cancel?")
                    .setPositiveButton("Yes", (confirmDialog, confirmWhich) -> {
                        currentDialogView = null;
                        currentReportDialog = null;
                        isSubmittingReport = false; // Reset flag
                        confirmDialog.dismiss();
                    })
                    .setNegativeButton("No", (confirmDialog, confirmWhich) -> {
                        confirmDialog.dismiss();
                        if (currentReportDialog != null) {
                            currentReportDialog.show();
                        } else {
                            showAddReportDialog();
                        }
                    })
                    .show();
        });

        AlertDialog dialog = builder.create();
        currentReportDialog = dialog;

        dialog.setOnShowListener(dialogInterface -> {
            MaterialButton submitButton = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            submitButton.setOnClickListener(view -> {
                // PREVENT DOUBLE SUBMISSION
                if (isSubmittingReport) {
                    showToast("Please wait, submitting report...");
                    return;
                }

                String title = titleInput.getText().toString().trim();
                String description = descriptionInput.getText().toString().trim();
                String location = locationSpinner.getSelectedItem().toString();

                if ("Select Location".equals(location)) {
                    showToast("Please select a valid location");
                    return;
                }

                // Extract priority
                String selectedPriority = prioritySpinner.getSelectedItem().toString();
                if (selectedPriority.equals("Select Priority Level")) {
                    showToast("Please select a priority level");
                    return;
                }
                String priority = selectedPriority.split(" - ")[0].toLowerCase();

                // Validate inputs
                if (validateReportInputs(title, description, location)) {
                    // SET FLAG TO PREVENT DOUBLE CLICK
                    isSubmittingReport = true;
                    submitButton.setEnabled(false);
                    submitButton.setText("Submitting...");

                    // Check location limit
                    checkLocationReportLimit(location, (canSubmit, currentCount) -> {
                        if (!canSubmit) {
                            // Reset flag on error
                            isSubmittingReport = false;
                            submitButton.setEnabled(true);
                            submitButton.setText("Submit");
                            showLocationLimitDialog(location, currentCount);
                        } else {
                            // Check for duplicates
                            checkForDuplicateReports(location, title, description, (hasDuplicate, existingReports) -> {
                                if (hasDuplicate && existingReports != null && !existingReports.isEmpty()) {
                                    // Reset flag when showing duplicate warning
                                    isSubmittingReport = false;
                                    submitButton.setEnabled(true);
                                    submitButton.setText("Submit");

                                    showDuplicateWarningDialog(existingReports, location, title, description, priority, dialog);
                                } else {
                                    // No duplicates, proceed with submission
                                    if ("critical".equals(priority) || "high".equals(priority)) {
                                        // Reset flag before showing confirmation dialog
                                        isSubmittingReport = false;
                                        submitButton.setEnabled(true);
                                        submitButton.setText("Submit");

                                        confirmPriorityAndSubmit(title, description, location, priority, dialog);
                                    } else {
                                        // ⚠️ FIX: For normal priority, submit and dismiss
                                        submitReportAndDismiss(title, description, location, priority, false, dialog);
                                    }
                                }
                            });
                        }
                    });
                }
            });  // ← Closes setOnClickListener
        });      // ← Closes setOnShowListener

        dialog.show();  // ← Now this is at the right indentation level
    }  // ← This MUST be here to close the showAddReportDialog() method

    interface DuplicateCheckCallback {
        void onCheckComplete(boolean hasDuplicate, List<Report> existingReports);
    }

    private void showLocationLimitDialog(String location, int currentCount) {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 32, 40, 32);

        // Header
        TextView headerText = new TextView(this);
        headerText.setText("⚠️ Daily Report Limit Reached");
        headerText.setTextSize(18);
        headerText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        headerText.setTypeface(null, android.graphics.Typeface.BOLD);
        headerText.setPadding(0, 0, 0, 16);
        layout.addView(headerText);

        // Message
        TextView messageText = new TextView(this);
        messageText.setText("Location: " + location + "\n\n" +
                "This location has reached the maximum of 5 reports for today.\n\n" +
                "Current reports today: " + currentCount + " / 5\n\n" +
                "This limit helps prevent duplicate reports and ensures efficient processing.\n\n" +
                "What you can do:\n" +
                "• Check existing reports for this location\n" +
                "• Add comments to existing reports via chat\n" +
                "• Try reporting again tomorrow\n" +
                "• Contact admin for urgent issues");
        messageText.setTextSize(14);
        messageText.setPadding(0, 0, 0, 16);
        layout.addView(messageText);

        scrollView.addView(layout);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(scrollView);
        builder.setTitle("Report Limit");
        builder.setPositiveButton("View Existing Reports", (dialog, which) -> {
            showLocationReportsDialog(location);
        });
        builder.setNegativeButton("OK", null);
        builder.setIcon(android.R.drawable.ic_dialog_alert);

        builder.create().show();
    }

    // 5. SHOW EXISTING REPORTS FOR LOCATION
    private void showLocationReportsDialog(String location) {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setMessage("Loading reports for " + location + "...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        mDatabase.child("maintenance_reports")
                .orderByChild("location")
                .equalTo(location)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressDialog.dismiss();

                        List<Report> locationReports = new ArrayList<>();
                        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String status = reportSnapshot.child("status").getValue(String.class);

                            // Only show active reports
                            if (status != null && !status.equalsIgnoreCase("completed") &&
                                    !status.equalsIgnoreCase("rejected")) {

                                Report report = new Report();
                                report.reportId = reportSnapshot.getKey();
                                report.title = reportSnapshot.child("title").getValue(String.class);
                                report.description = reportSnapshot.child("description").getValue(String.class);
                                report.location = reportSnapshot.child("location").getValue(String.class);
                                report.status = status;
                                report.priority = reportSnapshot.child("priority").getValue(String.class);
                                report.reportedBy = reportSnapshot.child("reportedBy").getValue(String.class);

                                Long createdAt = reportSnapshot.child("createdAt").getValue(Long.class);
                                report.createdAt = createdAt != null ? createdAt : 0L;

                                locationReports.add(report);
                            }
                        }

                        if (locationReports.isEmpty()) {
                            showToast("No active reports found for this location");
                            return;
                        }

                        // Sort by most recent
                        locationReports.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));

                        // Show in dialog
                        String[] reportTitles = new String[locationReports.size()];
                        for (int i = 0; i < locationReports.size(); i++) {
                            Report r = locationReports.get(i);
                            reportTitles[i] = r.title + "\n" +
                                    dateFormat.format(new Date(r.createdAt)) +
                                    " - " + r.status.toUpperCase();
                        }

                        new AlertDialog.Builder(UserDashboardActivity.this)
                                .setTitle("Active Reports: " + location)
                                .setItems(reportTitles, (dialog, which) -> {
                                    Report selectedReport = locationReports.get(which);
                                    showReportDetails(selectedReport);
                                })
                                .setNegativeButton("Close", null)
                                .show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        showToast("Failed to load reports: " + error.getMessage());
                    }
                });
    }

    private void checkForDuplicateReports(String location, String title, String description,
                                          DuplicateCheckCallback callback) {
        AlertDialog checkingDialog = new AlertDialog.Builder(this)
                .setTitle("Checking for Similar Reports")
                .setMessage("Please wait...")
                .setCancelable(false)
                .create();

        checkingDialog.show();

        // Query reports at the same location that are not completed or rejected
        mDatabase.child("maintenance_reports")
                .orderByChild("location")
                .equalTo(location)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        checkingDialog.dismiss();

                        List<Report> similarReports = new ArrayList<>();

                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            String status = reportSnapshot.child("status").getValue(String.class);
                            String reportTitle = reportSnapshot.child("title").getValue(String.class);
                            String reportDesc = reportSnapshot.child("description").getValue(String.class);

                            // Only check active reports
                            if (status != null && !status.equalsIgnoreCase("completed")
                                    && !status.equalsIgnoreCase("rejected")) {

                                // IMPROVED: Check for exact or very similar reports
                                if (isExactOrVerySimilarReport(title, description, reportTitle, reportDesc)) {
                                    Report report = new Report();
                                    report.reportId = reportSnapshot.getKey();
                                    report.title = reportTitle;
                                    report.description = reportDesc;
                                    report.location = reportSnapshot.child("location").getValue(String.class);
                                    report.status = status;
                                    report.priority = reportSnapshot.child("priority").getValue(String.class);
                                    report.reportedBy = reportSnapshot.child("reportedBy").getValue(String.class);

                                    Long createdAt = reportSnapshot.child("createdAt").getValue(Long.class);
                                    report.createdAt = createdAt != null ? createdAt : 0L;

                                    similarReports.add(report);
                                }
                            }
                        }

                        callback.onCheckComplete(!similarReports.isEmpty(), similarReports);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        checkingDialog.dismiss();
                        Log.e(TAG, "Error checking duplicates", error.toException());
                        // Continue with submission if check fails
                        callback.onCheckComplete(false, null);
                    }
                });
    }

    private boolean isExactOrVerySimilarReport(String newTitle, String newDesc,
                                               String existingTitle, String existingDesc) {
        if (existingTitle == null || existingDesc == null) return false;

        // Normalize strings
        String newTitleLower = normalizeText(newTitle);
        String newDescLower = normalizeText(newDesc);
        String existingTitleLower = normalizeText(existingTitle);
        String existingDescLower = normalizeText(existingDesc);

        // 1. EXACT MATCH: If title AND description are exactly the same
        if (newTitleLower.equals(existingTitleLower) && newDescLower.equals(existingDescLower)) {
            return true;
        }

        // 2. TITLE EXACT MATCH: If titles are exactly the same (high confidence)
        if (newTitleLower.equals(existingTitleLower)) {
            return true;
        }

        // 3. VERY HIGH SIMILARITY: Title is 90%+ similar
        if (calculateSimilarity(newTitleLower, existingTitleLower) >= 0.90) {
            return true;
        }

        // 4. KEYWORD MATCHING: Check if multiple important keywords match
        String[] keywords = {
                "leak", "leaking", "broken", "crack", "cracked", "damage", "damaged",
                "not working", "malfunction", "malfunctioning", "defective",
                "door", "window", "light", "lights", "bulb", "electricity", "electrical",
                "water", "aircon", "ac", "air conditioning", "hvac",
                "toilet", "cr", "comfort room", "restroom", "bathroom",
                "sink", "faucet", "tap", "pipe", "plumbing",
                "ceiling", "floor", "wall", "paint", "painting",
                "chair", "table", "desk", "furniture",
                "projector", "computer", "pc", "laptop", "printer",
                "wifi", "internet", "network", "connection"
        };

        int matchingKeywords = 0;
        int titleKeywordMatches = 0;

        for (String keyword : keywords) {
            boolean newTitleHas = newTitleLower.contains(keyword);
            boolean existingTitleHas = existingTitleLower.contains(keyword);
            boolean newDescHas = newDescLower.contains(keyword);
            boolean existingDescHas = existingDescLower.contains(keyword);

            // Title keyword match (more important)
            if (newTitleHas && existingTitleHas) {
                titleKeywordMatches++;
                matchingKeywords++;
            }

            // Description keyword match
            if (newDescHas && existingDescHas) {
                matchingKeywords++;
            }
        }

        // If 2+ keywords match in titles, it's very likely a duplicate
        if (titleKeywordMatches >= 2) {
            return true;
        }

        // If 3+ keywords match overall, likely a duplicate
        if (matchingKeywords >= 3) {
            return true;
        }

        return false;
    }

    private String normalizeText(String text) {
        if (text == null) return "";

        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "") // Remove special characters
                .replaceAll("\\s+", " ") // Replace multiple spaces with single space
                .trim();
    }

    // Helper method to calculate string similarity (Levenshtein distance based)
    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;

        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLength);
    }

    // Levenshtein distance algorithm
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                                dp[i - 1][j] + 1,      // deletion
                                dp[i][j - 1] + 1),     // insertion
                        dp[i - 1][j - 1] + cost); // substitution
            }
        }

        return dp[s1.length()][s2.length()];
    }

    // 5. ADD method to show duplicate warning dialog
    private void showDuplicateWarningDialog(List<Report> existingReports, String location,
                                            String title, String description, String priority,
                                            AlertDialog parentDialog) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Create scrollable view
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 32, 40, 32);

        // Warning header
        TextView headerText = new TextView(this);
        headerText.setText("⚠️ Similar Reports Found");
        headerText.setTextSize(18);
        headerText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        headerText.setTypeface(null, android.graphics.Typeface.BOLD);
        headerText.setPadding(0, 0, 0, 16);
        layout.addView(headerText);

        // Warning message
        TextView warningText = new TextView(this);
        warningText.setText("There are already " + existingReports.size() +
                " active report(s) for issues at:\n\n📍 " + location +
                "\n\nSubmitting duplicate reports may delay processing. " +
                "Please review existing reports below:");
        warningText.setTextSize(14);
        warningText.setPadding(0, 0, 0, 20);
        layout.addView(warningText);

        // Divider
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        layout.addView(divider);

        // List existing reports
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        for (int i = 0; i < existingReports.size(); i++) {
            Report existingReport = existingReports.get(i);

            // Report card
            CardView cardView = new CardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 16, 0, 0);
            cardView.setLayoutParams(cardParams);
            cardView.setCardElevation(4);
            cardView.setRadius(8);

            LinearLayout cardContent = new LinearLayout(this);
            cardContent.setOrientation(LinearLayout.VERTICAL);
            cardContent.setPadding(16, 16, 16, 16);

            // Report number
            TextView reportNumText = new TextView(this);
            reportNumText.setText("Existing Report #" + (i + 1));
            reportNumText.setTextSize(12);
            reportNumText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            cardContent.addView(reportNumText);

            // Title
            TextView titleText = new TextView(this);
            titleText.setText(existingReport.title);
            titleText.setTextSize(16);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setPadding(0, 4, 0, 4);
            cardContent.addView(titleText);

            // Description
            TextView descText = new TextView(this);
            String shortDesc = existingReport.description;
            if (shortDesc != null && shortDesc.length() > 100) {
                shortDesc = shortDesc.substring(0, 97) + "...";
            }
            descText.setText(shortDesc);
            descText.setTextSize(14);
            descText.setPadding(0, 0, 0, 8);
            cardContent.addView(descText);

            // Status and priority
            LinearLayout infoLayout = new LinearLayout(this);
            infoLayout.setOrientation(LinearLayout.HORIZONTAL);

            Chip statusChip = new Chip(this);
            statusChip.setText(existingReport.status.toUpperCase());
            statusChip.setTextSize(10);
            statusChip.setChipBackgroundColorResource(getStatusColorResource(existingReport.status));
            infoLayout.addView(statusChip);

            TextView spacer = new TextView(this);
            spacer.setText("  ");
            infoLayout.addView(spacer);

            Chip priorityChip = new Chip(this);
            priorityChip.setText(existingReport.priority.toUpperCase());
            priorityChip.setTextSize(10);
            priorityChip.setChipBackgroundColorResource(getPriorityColorResource(existingReport.priority));
            infoLayout.addView(priorityChip);

            cardContent.addView(infoLayout);

            // Date and reporter
            TextView infoText = new TextView(this);
            infoText.setText("Reported: " + dateFormat.format(new Date(existingReport.createdAt)) +
                    " by " + existingReport.reportedBy);
            infoText.setTextSize(11);
            infoText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            infoText.setPadding(0, 8, 0, 0);
            cardContent.addView(infoText);

            // View button
            MaterialButton viewButton = new MaterialButton(this);
            viewButton.setText("View This Report");
            viewButton.setTextSize(12);
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            buttonParams.setMargins(0, 8, 0, 0);
            viewButton.setLayoutParams(buttonParams);

            final Report reportToView = existingReport;
            viewButton.setOnClickListener(v -> {
                // Close all dialogs and show the existing report
                if (parentDialog != null) parentDialog.dismiss();
                showReportDetails(reportToView);
            });

            cardContent.addView(viewButton);
            cardView.addView(cardContent);
            layout.addView(cardView);
        }

        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setTitle("Duplicate Report Warning");

        // Options
        builder.setPositiveButton("Submit Anyway", (dialog, which) -> {
            // User confirms they want to submit despite duplicates
            isSubmittingReport = true; // Set flag here

            if ("critical".equals(priority) || "high".equals(priority)) {
                confirmPriorityAndSubmit(title, description, location, priority, parentDialog);
            } else {
                submitReportAndDismiss(title, description, location, priority, true, parentDialog);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            // User decides not to submit
            dialog.dismiss();
            // Keep the original dialog open
        });

        builder.setNeutralButton("View & Join Existing", (dialog, which) -> {
            // Show list of existing reports to view
            if (!existingReports.isEmpty()) {
                showExistingReportsList(existingReports, parentDialog);
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        // Make Submit Anyway button red to emphasize it's not recommended
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                getResources().getColor(android.R.color.holo_red_dark));
    }

    // 6. ADD method to show list of existing reports to join
    private void showExistingReportsList(List<Report> existingReports, AlertDialog parentDialog) {
        String[] reportTitles = new String[existingReports.size()];
        for (int i = 0; i < existingReports.size(); i++) {
            Report report = existingReports.get(i);
            reportTitles[i] = report.title + " (" + report.status.toUpperCase() + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Report to View")
                .setItems(reportTitles, (dialog, which) -> {
                    Report selectedReport = existingReports.get(which);
                    parentDialog.dismiss();
                    showReportDetails(selectedReport);

                    // Suggest joining the chat
                    new AlertDialog.Builder(this)
                            .setTitle("Join This Report's Chat?")
                            .setMessage("Would you like to join the discussion about this existing report? " +
                                    "You can add your observations or follow the progress.")
                            .setPositiveButton("Join Chat", (d, w) -> {
                                startReportChatWithProgress(selectedReport.reportId);
                            })
                            .setNegativeButton("Just View", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // 7. ADD helper method to get status color resource
    private int getStatusColorResource(String status) {
        if (status == null) return R.color.status_pending;

        switch (status.toLowerCase()) {
            case "completed":
                return R.color.status_completed;
            case "in progress":
            case "scheduled":
                return R.color.status_in_progress;
            case "rejected":
                return R.color.status_rejected;
            case "pending":
            default:
                return R.color.status_pending;
        }
    }

    // 8. ADD helper method to get priority color resource
    private int getPriorityColorResource(String priority) {
        if (priority == null) return R.color.priority_medium;

        switch (priority.toLowerCase()) {
            case "critical":
                return R.color.priority_critical;
            case "high":
                return R.color.priority_high;
            case "medium":
                return R.color.priority_medium;
            case "low":
            default:
                return R.color.priority_low;
        }
    }



    // Add this new method to show critical priority warning
    private void showCriticalPriorityWarning() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Critical Priority Guidelines")
                .setMessage("CRITICAL priority should ONLY be used for:\n\n" +
                        "• Immediate safety hazards\n" +
                        "• Fire/electrical emergencies\n" +
                        "• Severe water leaks/flooding\n" +
                        "• Complete system failures\n" +
                        "• Situations requiring immediate attention\n\n" +
                        "Misuse of Critical priority may result in delayed processing of your report.\n\n" +
                        "Are you sure this issue is Critical?")
                .setPositiveButton("Yes, It's Critical", null)
                .setNegativeButton("No, Change Priority", (dialog, which) -> {
                    // User will manually change it
                    dialog.dismiss();
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    // 1. CHECK LOCATION REPORT LIMIT (Max 5 reports per location per day)
    private void checkLocationReportLimit(String location, LocationLimitCallback callback) {
        // Get today's date at midnight (start of day)
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        long startOfDay = calendar.getTimeInMillis();

        // Query reports at this location created today
        mDatabase.child("maintenance_reports")
                .orderByChild("location")
                .equalTo(location)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int todayCount = 0;

                        for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                            Long createdAt = reportSnapshot.child("createdAt").getValue(Long.class);
                            String status = reportSnapshot.child("status").getValue(String.class);

                            // Count only reports created today that are not rejected
                            if (createdAt != null && createdAt >= startOfDay &&
                                    (status == null || !status.equalsIgnoreCase("rejected"))) {
                                todayCount++;
                            }
                        }

                        callback.onCheckComplete(todayCount < 5, todayCount);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check location limit", error.toException());
                        // Allow submission if check fails
                        callback.onCheckComplete(true, 0);
                    }
                });
    }

    // 2. CALLBACK INTERFACE
    interface LocationLimitCallback {
        void onCheckComplete(boolean canSubmit, int currentCount);
    }


    // Add this new method to confirm priority before submission
    private void confirmPriorityAndSubmit(String title, String description, String location,
                                          String priority, AlertDialog parentDialog) {
        String priorityMessage;
        String priorityTitle;

        if ("critical".equals(priority)) {
            priorityTitle = "⚠️ Confirm CRITICAL Priority";
            priorityMessage = "You are submitting this as CRITICAL priority.\n\n" +
                    "This should ONLY be used for:\n" +
                    "• Safety hazards\n" +
                    "• Emergencies requiring immediate response\n" +
                    "• Complete system failures\n\n" +
                    "Misuse may result in:\n" +
                    "• Delayed processing\n" +
                    "• Report rejection\n" +
                    "• Future restrictions\n\n" +
                    "Is this truly a CRITICAL issue?";
        } else {
            priorityTitle = "Confirm HIGH Priority";
            priorityMessage = "You are submitting this as HIGH priority.\n\n" +
                    "This should be used for:\n" +
                    "• Issues affecting operations\n" +
                    "• Problems impacting multiple users\n" +
                    "• Urgent but not emergency situations\n\n" +
                    "Is this the correct priority level?";
        }

        new AlertDialog.Builder(this)
                .setTitle(priorityTitle)
                .setMessage(priorityMessage)
                .setPositiveButton("Yes, Submit", (dialog, which) -> {
                    // NOW set the flag when actually submitting
                    isSubmittingReport = true;

                    // Call submitReport and pass the parent dialog to dismiss it
                    submitReportAndDismiss(title, description, location, priority, false, parentDialog);
                })
                .setNegativeButton("No, Change Priority", (dialog, which) -> {
                    // Flag remains false, user can change priority and resubmit
                    showToast("Please select the appropriate priority level");
                    // Don't dismiss parent dialog - user needs to change priority
                })
                .setNeutralButton("Cancel Report", (dialog, which) -> {
                    // Reset everything and dismiss parent dialog
                    isSubmittingReport = false;
                    currentDialogView = null;
                    currentReportDialog = null;
                    parentDialog.dismiss();
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setCancelable(false)
                .show();
    }

    private void submitReportAndDismiss(String title, String description, String location,
                                        String priority, boolean isDuplicateOverride,
                                        AlertDialog parentDialog) {
        String reportId = mDatabase.child("maintenance_reports").push().getKey();

        if (reportId == null) {
            showToast("Failed to create report ID");
            isSubmittingReport = false;
            return;
        }

        if (selectedImageBase64 == null || selectedImageBase64.isEmpty()) {
            showToast("Photo is required - Please add a photo of the issue");
            isSubmittingReport = false;
            return;
        }

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("reportId", reportId);
        reportData.put("title", title);
        reportData.put("description", description);
        reportData.put("location", location);
        reportData.put("campus", "Meneses Campus");
        reportData.put("department", userDepartment);
        reportData.put("priority", priority);
        reportData.put("status", "pending");
        reportData.put("reportedBy", userName);
        reportData.put("reportedByEmail", currentUser.getEmail());
        reportData.put("reportedByUid", currentUser.getUid());
        reportData.put("createdAt", System.currentTimeMillis());
        reportData.put("photoBase64", selectedImageBase64);
        reportData.put("hasPhoto", true);

        if (isDuplicateOverride) {
            reportData.put("isDuplicateOverride", true);
            reportData.put("duplicateConfirmedAt", System.currentTimeMillis());
        }

        if ("critical".equals(priority) || "high".equals(priority)) {
            reportData.put("priorityConfirmed", true);
            reportData.put("priorityConfirmedAt", System.currentTimeMillis());
        }

        showToast("Submitting report...");

        mDatabase.child("maintenance_reports").child(reportId).setValue(reportData)
                .addOnSuccessListener(aVoid -> {
                    // RESET FLAG ON SUCCESS
                    isSubmittingReport = false;

                    String successMessage = "Report submitted successfully";

                    if (isDuplicateOverride) {
                        successMessage += "\n\nNote: This is a duplicate report. Admins will review and may merge it.";
                    }

                    if ("critical".equals(priority)) {
                        successMessage += "\n\nCRITICAL priority - Admins will be notified immediately";
                    } else if ("high".equals(priority)) {
                        successMessage += "\n\nHIGH priority - Will be processed with urgency";
                    }

                    showToast(successMessage);

                    // Create chat
                    ChatHelper.createReportChat(reportId, title, currentUser.getUid(), userName);

                    // Send notifications
                    NotificationHelper.notifyNewReport(reportId, title, userName, "Meneses Campus", priority);

                    if ("critical".equalsIgnoreCase(priority)) {
                        NotificationHelper.notifyCriticalReport(reportId, title, location);
                    }

                    // Clean up and dismiss dialogs
                    currentDialogView = null;
                    currentReportDialog = null;
                    parentDialog.dismiss();

                    loadUserReports();
                })
                .addOnFailureListener(e -> {
                    // RESET FLAG ON FAILURE
                    isSubmittingReport = false;

                    showToast("Failed to submit report: " + e.getMessage());
                    Log.e(TAG, "Failed to submit report", e);
                });
    }


    private void showMarkCompleteDialog(Report report) {
        currentReportIdForCompletion = report.reportId;
        completionImageBase64 = null;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mark_complete, null);
        builder.setView(dialogView);

        currentCompletionDialogView = dialogView;

        TextView titleText = dialogView.findViewById(R.id.completionTitleText);
        MaterialButton addPhotoButton = dialogView.findViewById(R.id.addCompletionPhotoButton);
        ImageView photoImageView = dialogView.findViewById(R.id.completionImageView);
        MaterialButton removeButton = dialogView.findViewById(R.id.removeCompletionPhotoButton);
        TextView instructionText = dialogView.findViewById(R.id.completionInstructionText);

        titleText.setText("Mark as Complete: " + report.title);
        instructionText.setText("Please take a photo showing the completed repair/fix before marking this report as complete.");

        // Reset photo state
        if (photoImageView != null) {
            photoImageView.setVisibility(View.GONE);
            photoImageView.setImageBitmap(null);
        }
        if (removeButton != null) {
            removeButton.setVisibility(View.GONE);
        }

        // Photo button click listener
        if (addPhotoButton != null) {
            addPhotoButton.setOnClickListener(v -> showCompletionPhotoOptions());
        }

        // Remove photo button listener
        if (removeButton != null) {
            removeButton.setOnClickListener(v -> {
                completionImageBase64 = null;
                if (photoImageView != null) {
                    photoImageView.setVisibility(View.GONE);
                    photoImageView.setImageBitmap(null);
                }
                removeButton.setVisibility(View.GONE);
            });
        }

        builder.setTitle("Complete Report");
        builder.setPositiveButton("Mark Complete", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            currentCompletionDialogView = null;
            currentCompletionDialog = null;
            currentReportIdForCompletion = null;
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        currentCompletionDialog = dialog;

        dialog.setOnShowListener(dialogInterface -> {
            MaterialButton completeButton = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            completeButton.setOnClickListener(view -> {
                if (completionImageBase64 == null || completionImageBase64.isEmpty()) {
                    showToast("Please add a completion photo before marking as complete");
                    return;
                }
                markReportAsComplete(report);
                currentCompletionDialogView = null;
                currentCompletionDialog = null;
                currentReportIdForCompletion = null;
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void showCompletionPhotoOptions() {
        String[] options = {"Take Photo", "Choose from Gallery"};

        new AlertDialog.Builder(this)
                .setTitle("Add Completion Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Take photo
                        if (checkCameraPermission()) {
                            openCompletionCamera();
                        }
                    } else {
                        // Choose from gallery
                        if (checkStoragePermission()) {
                            openCompletionGallery();
                        }
                    }
                })
                .show();
    }

    private void openCompletionCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_COMPLETION_IMAGE_CAPTURE);
        }
    }

    private void openCompletionGallery() {
        Intent pickPhotoIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickPhotoIntent, REQUEST_COMPLETION_IMAGE_PICK);
    }

    private void markReportAsComplete(Report report) {
        if (completionImageBase64 == null || completionImageBase64.isEmpty()) {
            showToast("Completion photo is required");
            return;
        }

        long completionTime = System.currentTimeMillis();

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "completed");
        updates.put("completedAt", completionTime);
        updates.put("completionPhotoBase64", completionImageBase64);

        // Add completion date in readable format for easier querying/display
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        updates.put("completedAtFormatted", dateFormat.format(new Date(completionTime)));

        showToast("Marking report as complete...");

        mDatabase.child("maintenance_reports").child(report.reportId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    showToast("Report marked as complete successfully at " +
                            new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(completionTime)));
                    loadUserReports();
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to update report: " + e.getMessage());
                    Log.e(TAG, "Failed to update report", e);
                });
    }

    private void showStatusUpdateDialog(Report report) {
        String[] statusOptions;
        String currentStatus = report.status != null ? report.status.toLowerCase() : "pending";

        // Different options based on current status
        switch (currentStatus) {
            case "pending":
                statusOptions = new String[]{"In Progress", "Completed"};
                break;
            case "scheduled":
                statusOptions = new String[]{"In Progress", "Completed"};
                break;
            case "in progress":
                statusOptions = new String[]{"Completed"};
                break;
            case "completed":
                statusOptions = new String[]{"Report Again"};
                break;
            default:
                statusOptions = new String[]{"In Progress", "Completed"};
                break;
        }

        new AlertDialog.Builder(this)
                .setTitle("Update Status")
                .setItems(statusOptions, (dialog, which) -> {
                    String selectedOption = statusOptions[which];

                    switch (selectedOption) {
                        case "In Progress":
                            updateReportStatus(report, "in progress");
                            break;
                        case "Completed":
                            if (!"completed".equalsIgnoreCase(report.status)) {
                                // Show completion dialog that requires photo
                                showMarkCompleteDialog(report);
                            }
                            break;
                        case "Report Again":
                            reportAgain(report);
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reportAgain(Report report) {
        new AlertDialog.Builder(this)
                .setTitle("Report Again")
                .setMessage("This will create a new report based on this completed one. The original report will remain as completed.")
                .setPositiveButton("Create New Report", (dialog, which) -> {
                    // Create new report based on the completed one
                    createNewReportFromExisting(report);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNewReportFromExisting(Report existingReport) {
        String reportId = mDatabase.child("maintenance_reports").push().getKey();

        if (reportId == null) {
            showToast("Failed to create new report");
            return;
        }

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("reportId", reportId);
        reportData.put("title", existingReport.title);
        reportData.put("description", existingReport.description);
        reportData.put("location", existingReport.location);
        reportData.put("campus", "Meneses Campus");
        reportData.put("department", existingReport.department);
        reportData.put("priority", existingReport.priority);
        reportData.put("status", "pending");
        reportData.put("reportedBy", userName);
        reportData.put("reportedByEmail", currentUser.getEmail());
        reportData.put("reportedByUid", currentUser.getUid());
        reportData.put("createdAt", System.currentTimeMillis());
        reportData.put("hasPhoto", false);
        reportData.put("basedOnReport", existingReport.reportId); // Reference to original report

        showToast("Creating new report...");

        mDatabase.child("maintenance_reports").child(reportId).setValue(reportData)
                .addOnSuccessListener(aVoid -> {
                    showToast("New report created successfully");
                    loadUserReports();
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to create new report: " + e.getMessage());
                    Log.e(TAG, "Failed to create new report", e);
                });
    }

    private void updateReportStatus(Report report, String newStatus) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);

        if ("pending".equals(newStatus)) {
            updates.put("completedAt", null);
            updates.put("completionPhotoBase64", null);
        }

        mDatabase.child("maintenance_reports").child(report.reportId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    showToast("Report status updated successfully");

                    // Send notification to chat
                    String chatId = ChatHelper.generateReportChatId(report.reportId);
                    String message = "Status updated to: " + newStatus.toUpperCase();
                    ChatHelper.sendSystemMessage(chatId, message, report.reportId);

                    loadUserReports();
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to update status: " + e.getMessage());
                    Log.e(TAG, "Failed to update status", e);
                });
    }

    private void showDeleteDialog(Report report) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Report")
                .setMessage("Are you sure you want to delete this report?\n\nTitle: " + report.title + "\n\nThis action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteReport(report))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteReport(Report report) {
        mDatabase.child("maintenance_reports").child(report.reportId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    showToast("Report deleted successfully");
                    loadUserReports();
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to delete report: " + e.getMessage());
                    Log.e(TAG, "Failed to delete report", e);
                });
    }

    // Interface for photo selection callback
    interface PhotoSelectionCallback {
        void onPhotoSelected(String base64Image, Bitmap bitmap);
    }

    // Modified showPhotoOptions to support callback
    private void showPhotoOptionsWithCallback(PhotoSelectionCallback callback) {
        // Store callback for use in onActivityResult
        this.photoCallback = callback;

        String[] options = {"Take Photo", "Choose from Gallery"};

        new AlertDialog.Builder(this)
                .setTitle("Select Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Take photo
                        if (checkCameraPermission()) {
                            openCamera();
                        }
                    } else {
                        // Choose from gallery
                        if (checkStoragePermission()) {
                            openGallery();
                        }
                    }
                })
                .show();
    }

    private void showPhotoOptions() {
        String[] options = {"Take Photo", "Choose from Gallery"};

        new AlertDialog.Builder(this)
                .setTitle("Select Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Take photo
                        if (checkCameraPermission()) {
                            openCamera();
                        }
                    } else {
                        // Choose from gallery
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
            // Android 13+ uses READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        REQUEST_STORAGE_PERMISSION);
                return false;
            }
        } else {
            // Android 12 and below
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

        if (resultCode == RESULT_OK && data != null) {
            Bitmap processedBitmap = null;

            if (requestCode == REQUEST_IMAGE_CAPTURE || requestCode == REQUEST_COMPLETION_IMAGE_CAPTURE) {
                Bundle extras = data.getExtras();
                if (extras != null) {
                    Bitmap imageBitmap = (Bitmap) extras.get("data");
                    if (imageBitmap != null) {
                        processedBitmap = compressBitmap(imageBitmap);
                    }
                }
            } else if (requestCode == REQUEST_IMAGE_PICK || requestCode == REQUEST_COMPLETION_IMAGE_PICK) {
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
                if (requestCode == REQUEST_COMPLETION_IMAGE_CAPTURE || requestCode == REQUEST_COMPLETION_IMAGE_PICK) {
                    // Handle completion photo
                    completionImageBase64 = bitmapToBase64(processedBitmap);

                    if (currentCompletionDialogView != null) {
                        ImageView photoImageView = currentCompletionDialogView.findViewById(R.id.completionImageView);
                        MaterialButton removeButton = currentCompletionDialogView.findViewById(R.id.removeCompletionPhotoButton);

                        if (photoImageView != null) {
                            photoImageView.setImageBitmap(processedBitmap);
                            photoImageView.setVisibility(View.VISIBLE);
                        }

                        if (removeButton != null) {
                            removeButton.setVisibility(View.VISIBLE);
                        }
                    }
                } else {
                    // Handle regular report photo
                    selectedImageBase64 = bitmapToBase64(processedBitmap);

                    if (currentDialogView != null) {
                        ImageView photoImageView = currentDialogView.findViewById(R.id.selectedImageView);
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
        }
    }

    private Bitmap compressBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Calculate new dimensions
        float aspectRatio = (float) width / height;
        int newWidth, newHeight;

        if (width > height) {
            newWidth = Math.min(width, MAX_IMAGE_SIZE);
            newHeight = (int) (newWidth / aspectRatio);
        } else {
            newHeight = Math.min(height, MAX_IMAGE_SIZE);
            newWidth = (int) (newHeight * aspectRatio);
        }

        // Create scaled bitmap
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos); // Compress to 70% quality
        byte[] imageBytes = baos.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    private Bitmap base64ToBitmap(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return null;
        }
        byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                showToast("Camera permission is required to take photos");
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                showToast("Storage permission is required to select photos");
            }
        }
    }

    private boolean validateReportInputs(String title, String description, String location) {
        if (title.isEmpty()) {
            showToast("Title is required");
            return false;
        }
        if (description.isEmpty()) {
            showToast("Description is required");
            return false;
        }
        if (location.isEmpty()) {
            showToast("Location is required");
            return false;
        }
        // Add photo validation
        if (selectedImageBase64 == null || selectedImageBase64.isEmpty()) {
            showToast("Photo is required - Please add a photo of the issue");
            return false;
        }
        return true;
    }

    private void submitReport(String title, String description, String location, String priority) {
        submitReport(title, description, location, priority, false);
    }

    private void submitReport(String title, String description, String location, String priority,
                              boolean isDuplicateOverride) {

        // ⚠️ REMOVE THIS CHECK - flag should already be set
        // if (isSubmittingReport && !isDuplicateOverride) {
        //     showToast("Already submitting, please wait...");
        //     return;
        // }

        String reportId = mDatabase.child("maintenance_reports").push().getKey();

        if (reportId == null) {
            showToast("Failed to create report ID");
            isSubmittingReport = false;
            return;
        }

        if (selectedImageBase64 == null || selectedImageBase64.isEmpty()) {
            showToast("Photo is required - Please add a photo of the issue");
            isSubmittingReport = false;
            return;
        }

        // ⚠️ REMOVE THIS - flag should already be set by caller
        // Set flag if not already set
        // if (!isSubmittingReport) {
        //     isSubmittingReport = true;
        // }

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("reportId", reportId);
        reportData.put("title", title);
        reportData.put("description", description);
        reportData.put("location", location);
        reportData.put("campus", "Meneses Campus");
        reportData.put("department", userDepartment);
        reportData.put("priority", priority);
        reportData.put("status", "pending");
        reportData.put("reportedBy", userName);
        reportData.put("reportedByEmail", currentUser.getEmail());
        reportData.put("reportedByUid", currentUser.getUid());
        reportData.put("createdAt", System.currentTimeMillis());
        reportData.put("photoBase64", selectedImageBase64);
        reportData.put("hasPhoto", true);

        if (isDuplicateOverride) {
            reportData.put("isDuplicateOverride", true);
            reportData.put("duplicateConfirmedAt", System.currentTimeMillis());
        }

        if ("critical".equals(priority) || "high".equals(priority)) {
            reportData.put("priorityConfirmed", true);
            reportData.put("priorityConfirmedAt", System.currentTimeMillis());
        }

        showToast("Submitting report...");

        mDatabase.child("maintenance_reports").child(reportId).setValue(reportData)
                .addOnSuccessListener(aVoid -> {
                    // RESET FLAG ON SUCCESS
                    isSubmittingReport = false;

                    String successMessage = "Report submitted successfully";

                    if (isDuplicateOverride) {
                        successMessage += "\n\nNote: This is a duplicate report. Admins will review and may merge it.";
                    }

                    if ("critical".equals(priority)) {
                        successMessage += "\n\nCRITICAL priority - Admins will be notified immediately";
                    } else if ("high".equals(priority)) {
                        successMessage += "\n\nHIGH priority - Will be processed with urgency";
                    }

                    showToast(successMessage);

                    // Create chat
                    ChatHelper.createReportChat(reportId, title, currentUser.getUid(), userName);

                    // Send notifications
                    NotificationHelper.notifyNewReport(reportId, title, userName, "Meneses Campus", priority);

                    if ("critical".equalsIgnoreCase(priority)) {
                        NotificationHelper.notifyCriticalReport(reportId, title, location);
                    }

                    loadUserReports();
                })
                .addOnFailureListener(e -> {
                    // RESET FLAG ON FAILURE
                    isSubmittingReport = false;

                    showToast("Failed to submit report: " + e.getMessage());
                    Log.e(TAG, "Failed to submit report", e);
                });
    }

    private void showReportDetails(Report report) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Create scrollable view for details
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 32, 40, 32);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

        // Title
        TextView titleHeader = new TextView(this);
        titleHeader.setText(report.title != null ? report.title : "No Title");
        titleHeader.setTextSize(20);
        titleHeader.setTextColor(getResources().getColor(android.R.color.black));
        titleHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        titleHeader.setPadding(0, 0, 0, 16);
        layout.addView(titleHeader);

        // Complete Timeline Section
        TextView timelineHeader = new TextView(this);
        timelineHeader.setText("Complete Timeline");
        timelineHeader.setTextSize(16);
        timelineHeader.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        timelineHeader.setPadding(0, 8, 0, 12);
        timelineHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(timelineHeader);

        // Created
        layout.addView(createHistoryItem("📝 Report Created",
                dateFormat.format(new Date(report.createdAt))));
        layout.addView(createHistoryDivider());

        // Reported By
        layout.addView(createHistoryItem("👤 Reported By",
                report.reportedBy != null ? report.reportedBy : "Unknown"));
        layout.addView(createHistoryDivider());

        // Location
        layout.addView(createHistoryItem("📍 Location",
                report.location != null ? report.location : "Unknown Location"));
        layout.addView(createHistoryDivider());

        // Department
        if (report.department != null && !report.department.isEmpty()) {
            layout.addView(createHistoryItem("🏢 Department", report.department));
            layout.addView(createHistoryDivider());
        }

        // Description
        layout.addView(createHistoryItem("📋 Description",
                report.description != null ? report.description : "No Description"));
        layout.addView(createHistoryDivider());

        // Priority
        layout.addView(createHistoryItem("⚠️ Priority",
                report.priority != null ? report.priority.toUpperCase() : "MEDIUM"));
        layout.addView(createHistoryDivider());

        // Scheduled
        if (report.scheduledDate != null && !report.scheduledDate.isEmpty()) {
            String scheduleInfo = report.scheduledDate;
            if (report.scheduledTime != null && !report.scheduledTime.isEmpty()) {
                scheduleInfo += " at " + report.scheduledTime;
            }
            layout.addView(createHistoryItem("📅 Scheduled", scheduleInfo));

            if (report.assignedTo != null && !report.assignedTo.isEmpty()) {
                layout.addView(createHistorySubItem("👷 Assigned to", report.assignedTo));
            }
            if (report.technicianPhone != null && !report.technicianPhone.isEmpty()) {
                layout.addView(createHistorySubItem("📞 Phone", report.technicianPhone));
            }
            if (report.technicianEmail != null && !report.technicianEmail.isEmpty()) {
                layout.addView(createHistorySubItem("📧 Email", report.technicianEmail));
            }
            layout.addView(createHistoryDivider());
        }

        // Current Status
        String currentStatus = report.status != null ? report.status.toUpperCase() : "PENDING";
        String statusEmoji = getStatusEmoji(currentStatus);
        layout.addView(createHistoryItem(statusEmoji + " Current Status", currentStatus));
        layout.addView(createHistoryDivider());

        // Completed
        if ("completed".equalsIgnoreCase(report.status) && report.completedAt > 0) {
            layout.addView(createHistoryItem("✅ Completed",
                    dateFormat.format(new Date(report.completedAt))));

            long duration = report.completedAt - report.createdAt;
            String durationStr = formatDuration(duration);
            layout.addView(createHistorySubItem("⏱️ Duration", durationStr));
            layout.addView(createHistoryDivider());
        }

        // Photos Section
        TextView photosHeader = new TextView(this);
        photosHeader.setText("Attachments");
        photosHeader.setTextSize(16);
        photosHeader.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        photosHeader.setPadding(0, 16, 0, 12);
        photosHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(photosHeader);

        if (report.photoBase64 != null && !report.photoBase64.isEmpty()) {
            layout.addView(createHistorySubItem("📷", "Report Photo Available"));
        }
        if (report.completionPhotoBase64 != null && !report.completionPhotoBase64.isEmpty()) {
            layout.addView(createHistorySubItem("✅📷", "Completion Photo Available"));
        }
        if ((report.photoBase64 == null || report.photoBase64.isEmpty()) &&
                (report.completionPhotoBase64 == null || report.completionPhotoBase64.isEmpty())) {
            layout.addView(createHistorySubItem("❌", "No photos attached"));
        }

        scrollView.addView(layout);
        builder.setView(scrollView);
        builder.setTitle("Report Details");

        // Build dynamic options list
        List<String> options = new ArrayList<>();
        options.add("💬 Open Chat");

        if (report.photoBase64 != null && !report.photoBase64.isEmpty()) {
            options.add("📷 View Report Photo");
        }
        if (report.completionPhotoBase64 != null && !report.completionPhotoBase64.isEmpty()) {
            options.add("✅ View Completion Photo");
        }

        options.add("📋 View Action History");
        options.add("❌ Close");

        builder.setItems(options.toArray(new String[0]), (dialog, which) -> {
            String selected = options.get(which);

            if (selected.contains("Open Chat")) {
                startReportChatWithProgress(report.reportId);
            } else if (selected.contains("View Report Photo")) {
                showFullPhotoDialog(report.photoBase64);
            } else if (selected.contains("View Completion Photo")) {
                showFullPhotoDialog(report.completionPhotoBase64);
            } else if (selected.contains("View Action History")) {
                showActionHistoryDialog(report);
            } else if (selected.contains("Close")) {
                dialog.dismiss();
            }
        });

        builder.show();
    }

    private void showActionHistoryDialog(Report report) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Action History - " + report.title);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 32, 40, 32);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

        // Header
        TextView headerText = new TextView(this);
        headerText.setText("System Actions & Updates");
        headerText.setTextSize(16);
        headerText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        headerText.setPadding(0, 0, 0, 16);
        headerText.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(headerText);

        // Load chat history (system messages only)
        loadReportChatHistory(report.reportId, layout, dateFormat);

        scrollView.addView(layout);
        builder.setView(scrollView);
        builder.setPositiveButton("Close", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showReportHistoryDialog(Report report) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Report History - " + report.title);

        // Create scrollable layout
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 32, 40, 32);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

        // Header
        TextView headerText = new TextView(this);
        headerText.setText("Complete Timeline");
        headerText.setTextSize(16);
        headerText.setTextColor(getResources().getColor(android.R.color.black));
        headerText.setPadding(0, 0, 0, 16);
        headerText.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(headerText);

        // Load and display chat history for technician actions FIRST
        loadReportChatHistory(report.reportId, layout, dateFormat);

        // Original report info
        layout.addView(createHistoryItem("📝 Report Created",
                dateFormat.format(new Date(report.createdAt))));
        layout.addView(createHistoryDivider());

        layout.addView(createHistoryItem("👤 Reported By",
                report.reportedBy != null ? report.reportedBy : "Unknown"));
        layout.addView(createHistoryDivider());

        if (report.department != null && !report.department.isEmpty()) {
            layout.addView(createHistoryItem("🏢 Department", report.department));
            layout.addView(createHistoryDivider());
        }

        layout.addView(createHistoryItem("⚠️ Priority",
                report.priority != null ? report.priority.toUpperCase() : "MEDIUM"));
        layout.addView(createHistoryDivider());

        // Scheduled
        if (report.scheduledDate != null && !report.scheduledDate.isEmpty()) {
            String scheduleInfo = report.scheduledDate;
            if (report.scheduledTime != null && !report.scheduledTime.isEmpty()) {
                scheduleInfo += " at " + report.scheduledTime;
            }
            layout.addView(createHistoryItem("📅 Scheduled", scheduleInfo));
            layout.addView(createHistoryDivider());
        }

        // Assigned To
        if (report.assignedTo != null && !report.assignedTo.isEmpty()) {
            layout.addView(createHistoryItem("👷 Assigned To", report.assignedTo));

            if (report.technicianPhone != null && !report.technicianPhone.isEmpty()) {
                layout.addView(createHistorySubItem("📞 Phone", report.technicianPhone));
            }
            if (report.technicianEmail != null && !report.technicianEmail.isEmpty()) {
                layout.addView(createHistorySubItem("📧 Email", report.technicianEmail));
            }
            layout.addView(createHistoryDivider());
        }

        // Current Status
        String currentStatus = report.status != null ? report.status.toUpperCase() : "PENDING";
        String statusEmoji = getStatusEmoji(currentStatus);
        layout.addView(createHistoryItem(statusEmoji + " Current Status", currentStatus));
        layout.addView(createHistoryDivider());

        // Completed
        if ("completed".equalsIgnoreCase(report.status) && report.completedAt > 0) {
            layout.addView(createHistoryItem("✅ Completed",
                    dateFormat.format(new Date(report.completedAt))));

            long duration = report.completedAt - report.createdAt;
            String durationStr = formatDuration(duration);
            layout.addView(createHistorySubItem("⏱️ Duration", durationStr));
            layout.addView(createHistoryDivider());
        }

        // Photos
        TextView photosHeader = new TextView(this);
        photosHeader.setText("Attachments");
        photosHeader.setTextSize(16);
        photosHeader.setTextColor(getResources().getColor(android.R.color.black));
        photosHeader.setPadding(0, 16, 0, 12);
        photosHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(photosHeader);

        if (report.photoBase64 != null && !report.photoBase64.isEmpty()) {
            layout.addView(createHistorySubItem("📷", "Report Photo Available"));
        }
        if (report.completionPhotoBase64 != null && !report.completionPhotoBase64.isEmpty()) {
            layout.addView(createHistorySubItem("✅📷", "Completion Photo Available"));
        }
        if ((report.photoBase64 == null || report.photoBase64.isEmpty()) &&
                (report.completionPhotoBase64 == null || report.completionPhotoBase64.isEmpty())) {
            layout.addView(createHistorySubItem("❌", "No photos attached"));
        }

        scrollView.addView(layout);
        builder.setView(scrollView);
        builder.setPositiveButton("Close", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void loadReportChatHistory(String reportId, LinearLayout layout, SimpleDateFormat dateFormat) {
        String chatId = ChatHelper.generateReportChatId(reportId);

        // Add a "Loading history..." placeholder
        TextView loadingText = new TextView(this);
        loadingText.setText("Loading action history...");
        loadingText.setTextSize(12);
        loadingText.setTextColor(getResources().getColor(android.R.color.darker_gray));
        loadingText.setPadding(0, 8, 0, 8);
        layout.addView(loadingText);

        mDatabase.child("chats").child(chatId).child("messages")
                .orderByChild("timestamp")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        // Remove loading text
                        layout.removeView(loadingText);

                        // Add action history header
                        TextView actionsHeader = new TextView(UserDashboardActivity.this);
                        actionsHeader.setText("Action History");
                        actionsHeader.setTextSize(16);
                        actionsHeader.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                        actionsHeader.setPadding(0, 16, 0, 12);
                        actionsHeader.setTypeface(null, android.graphics.Typeface.BOLD);
                        layout.addView(actionsHeader);

                        boolean hasActions = false;

                        for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                            String messageType = messageSnapshot.child("messageType").getValue(String.class);
                            String message = messageSnapshot.child("message").getValue(String.class);
                            String senderName = messageSnapshot.child("senderName").getValue(String.class);
                            Long timestamp = messageSnapshot.child("timestamp").getValue(Long.class);

                            // Only show system messages (technician actions, status changes)
                            if ("system".equals(messageType) && message != null && timestamp != null) {
                                hasActions = true;

                                // Determine action emoji
                                String emoji = "📢";
                                if (message.contains("assigned") || message.contains("Assigned")) {
                                    emoji = "👷";
                                } else if (message.contains("accepted") || message.contains("Accepted")) {
                                    emoji = "✅";
                                } else if (message.contains("aborted") || message.contains("Aborted")) {
                                    emoji = "❌";
                                } else if (message.contains("completed") || message.contains("Completed")) {
                                    emoji = "🎉";
                                } else if (message.contains("Status updated")) {
                                    emoji = "🔄";
                                }

                                // Create action item with background
                                LinearLayout actionLayout = new LinearLayout(UserDashboardActivity.this);
                                actionLayout.setOrientation(LinearLayout.VERTICAL);
                                actionLayout.setPadding(12, 12, 12, 12);
                                actionLayout.setBackgroundColor(getResources().getColor(android.R.color.background_light));

                                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT);
                                params.setMargins(0, 4, 0, 4);
                                actionLayout.setLayoutParams(params);

                                TextView actionText = new TextView(UserDashboardActivity.this);
                                actionText.setText(emoji + " " + message);
                                actionText.setTextSize(13);
                                actionText.setTextColor(getResources().getColor(android.R.color.black));
                                actionLayout.addView(actionText);

                                TextView timeText = new TextView(UserDashboardActivity.this);
                                timeText.setText(dateFormat.format(new Date(timestamp)));
                                timeText.setTextSize(11);
                                timeText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                                timeText.setPadding(0, 4, 0, 0);
                                actionLayout.addView(timeText);

                                layout.addView(actionLayout);
                            }
                        }

                        if (!hasActions) {
                            TextView noActionsText = new TextView(UserDashboardActivity.this);
                            noActionsText.setText("No additional actions recorded yet");
                            noActionsText.setTextSize(12);
                            noActionsText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                            noActionsText.setPadding(8, 8, 8, 16);
                            layout.addView(noActionsText);
                        }

                        layout.addView(createHistoryDivider());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        layout.removeView(loadingText);
                        TextView errorText = new TextView(UserDashboardActivity.this);
                        errorText.setText("⚠️ Could not load action history");
                        errorText.setTextSize(12);
                        errorText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        errorText.setPadding(8, 8, 8, 16);
                        layout.addView(errorText);
                        layout.addView(createHistoryDivider());
                    }
                });
    }

    // Helper method to create history item with better styling
    private View createHistoryItem(String label, String value) {
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

        return itemLayout;
    }

    // Helper method to create sub-item (indented)
    private View createHistorySubItem(String label, String value) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(20, 4, 0, 4);

        TextView labelText = new TextView(this);
        labelText.setText(label + " ");
        labelText.setTextSize(12);
        labelText.setTextColor(getResources().getColor(android.R.color.darker_gray));
        itemLayout.addView(labelText);

        TextView valueText = new TextView(this);
        valueText.setText(value);
        valueText.setTextSize(12);
        valueText.setTextColor(getResources().getColor(android.R.color.black));
        itemLayout.addView(valueText);

        return itemLayout;
    }

    // Helper method to create divider
    private View createHistoryDivider() {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) divider.getLayoutParams();
        params.setMargins(0, 8, 0, 8);
        divider.setLayoutParams(params);
        return divider;
    }

    // Helper method to get emoji for status
    private String getStatusEmoji(String status) {
        switch (status.toLowerCase()) {
            case "completed":
                return "✅";
            case "in progress":
                return "🔧";
            case "scheduled":
                return "📅";
            case "rejected":
                return "❌";
            case "pending":
            default:
                return "⏳";
        }
    }

    // Helper method to format duration
    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "");
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "");
        }
    }

  /*  private void showReportDetails(Report report) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_report_detail_with_photo, null);

        if (dialogView != null) {
            // Title
            TextView titleText = dialogView.findViewById(R.id.detailTitle);
            if (titleText != null) {
                titleText.setText(report.title != null ? report.title : "No Title");
            }

            // Description
            TextView descriptionText = dialogView.findViewById(R.id.detailDescription);
            if (descriptionText != null) {
                descriptionText.setText(report.description != null ? report.description : "No Description");
            }

            // Location
            TextView locationText = dialogView.findViewById(R.id.detailLocation);
            if (locationText != null) {
                locationText.setText(report.location != null ? report.location : "Unknown Location");
            }

            // Date
            TextView dateText = dialogView.findViewById(R.id.detailDate);
            if (dateText != null) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
                String dateInfo = "Created: " + dateFormat.format(new Date(report.createdAt));

                // Add completion time if completed
                if ("completed".equalsIgnoreCase(report.status) && report.completedAt > 0) {
                    dateInfo += "\nCompleted: " + dateFormat.format(new Date(report.completedAt));
                }

                dateText.setText(dateInfo);
            }

            // Reported By
            TextView reportedByText = dialogView.findViewById(R.id.detailReportedBy);
            if (reportedByText != null) {
                reportedByText.setText("Reported by: " + (report.reportedBy != null ? report.reportedBy : "Unknown"));
            }

            // Status Chip
            Chip statusChip = dialogView.findViewById(R.id.detailStatusChip);
            if (statusChip != null) {
                statusChip.setText(report.status != null ? report.status.toUpperCase() : "PENDING");
                setStatusChipColor(statusChip, report.status);
            }

            // Photo
            ImageView photoImageView = dialogView.findViewById(R.id.detailPhoto);
            if (photoImageView != null) {
                if (report.photoBase64 != null && !report.photoBase64.isEmpty()) {
                    try {
                        Bitmap bitmap = base64ToBitmap(report.photoBase64);
                        if (bitmap != null) {
                            photoImageView.setImageBitmap(bitmap);
                            photoImageView.setVisibility(View.VISIBLE);
                        } else {
                            photoImageView.setVisibility(View.GONE);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error displaying photo", e);
                        photoImageView.setVisibility(View.GONE);
                    }
                } else {
                    photoImageView.setVisibility(View.GONE);
                }
            }

            builder.setView(dialogView);
            builder.setTitle("Report Details");

            // Check what photos are available
            boolean hasReportPhoto = (report.photoBase64 != null && !report.photoBase64.isEmpty());
            boolean hasCompletionPhoto = (report.completionPhotoBase64 != null && !report.completionPhotoBase64.isEmpty());

            // Set buttons based on available content
            if (hasReportPhoto && hasCompletionPhoto) {
                builder.setNeutralButton("Report Photo", (dialog, which) -> {
                    showFullPhotoDialog(report.photoBase64);
                });
                builder.setNegativeButton("Completion Photo", (dialog, which) -> {
                    showFullPhotoDialog(report.completionPhotoBase64);
                });
                builder.setPositiveButton("💬 Chat", (dialog, which) -> {
                    startReportChatWithProgress(report.reportId);
                });
            } else if (hasReportPhoto) {
                builder.setNeutralButton("View Photo", (dialog, which) -> {
                    showFullPhotoDialog(report.photoBase64);
                });
                builder.setNegativeButton("💬 Chat", (dialog, which) -> {
                    startReportChatWithProgress(report.reportId);
                });
                builder.setPositiveButton("Close", null);
            } else if (hasCompletionPhoto) {
                builder.setNeutralButton("Completion Photo", (dialog, which) -> {
                    showFullPhotoDialog(report.completionPhotoBase64);
                });
                builder.setNegativeButton("💬 Chat", (dialog, which) -> {
                    startReportChatWithProgress(report.reportId);
                });
                builder.setPositiveButton("Close", null);
            } else {
                builder.setNeutralButton("💬 Chat", (dialog, which) -> {
                    startReportChatWithProgress(report.reportId);
                });
                builder.setPositiveButton("Close", null);
            }


            // Add schedule info to the message if scheduled
            if (report.scheduledDate != null && !report.scheduledDate.isEmpty()) {
                StringBuilder scheduleInfo = new StringBuilder("\n\nSchedule Information:\n");
                scheduleInfo.append("Date: ").append(report.scheduledDate);
                if (report.scheduledTime != null && !report.scheduledTime.isEmpty()) {
                    scheduleInfo.append(" at ").append(report.scheduledTime);
                }
                if (report.assignedTo != null && !report.assignedTo.isEmpty()) {
                    scheduleInfo.append("\nAssigned to: ").append(report.assignedTo);

                    // Add contact information
                    if (report.technicianPhone != null && !report.technicianPhone.isEmpty()) {
                        scheduleInfo.append("\nPhone: ").append(report.technicianPhone);
                    }
                    if (report.technicianEmail != null && !report.technicianEmail.isEmpty()) {
                        scheduleInfo.append("\nEmail: ").append(report.technicianEmail);
                    }
                }

                TextView scheduleText = new TextView(this);
                scheduleText.setText(scheduleInfo.toString());
                scheduleText.setPadding(20, 10, 20, 10);
                scheduleText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));

                // Add schedule info below the main content
                if (dialogView instanceof ViewGroup) {
                    ((ViewGroup) ((ViewGroup) dialogView).getChildAt(0)).addView(scheduleText);
                }
            }

        } else {
            // Fallback to simple dialog if layout is not found
            String details = buildReportDetailsString(report);
            builder.setTitle(report.title);
            builder.setMessage(details);

            // Always show chat button in fallback
            builder.setNeutralButton("Chat", (dialog, which) -> {
                ChatHelper.startReportChat(this, report.reportId);
            });

            if (report.photoBase64 != null && !report.photoBase64.isEmpty()) {
                builder.setNegativeButton("View Photo", (dialog, which) -> showFullPhotoDialog(report.photoBase64));
            }

            builder.setPositiveButton("Close", null);
        }

        builder.show();
    } */

    // Method to start chat with progress indicator
    private void startReportChatWithProgress(String reportId) {
        // Show progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Starting Chat")
                .setMessage("Please wait...")
                .setCancelable(false)
                .create();

        progressDialog.show();

        String chatId = ChatHelper.generateReportChatId(reportId);

        // Check if chat exists first
        mDatabase.child("chats").child(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressDialog.dismiss();

                if (snapshot.exists()) {
                    // Chat exists, open it
                    ChatHelper.startReportChat(UserDashboardActivity.this, reportId);
                } else {
                    // Chat doesn't exist, create it first
                    createChatAndOpen(reportId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressDialog.dismiss();
                Log.e(TAG, "Failed to check chat existence", error.toException());
                showToast("Failed to access chat: " + error.getMessage());
            }
        });
    }

    private void createChatAndOpen(String reportId) {
        // Find the report to get details
        Report reportToChat = null;
        for (Report report : userReports) {
            if (report.reportId.equals(reportId)) {
                reportToChat = report;
                break;
            }
        }

        if (reportToChat != null) {
            final Report finalReport = reportToChat;

            // Show creating chat dialog
            AlertDialog creatingDialog = new AlertDialog.Builder(this)
                    .setTitle("Creating Chat")
                    .setMessage("Setting up conversation about: " + finalReport.title)
                    .setCancelable(false)
                    .create();

            creatingDialog.show();

            createReportChatWithCallback(reportId, finalReport.title, new ChatCreationCallback() {
                @Override
                public void onChatCreated(boolean success) {
                    creatingDialog.dismiss();

                    if (success) {
                        // Wait a moment then open chat
                        new android.os.Handler().postDelayed(() -> {
                            ChatHelper.startReportChat(UserDashboardActivity.this, reportId);
                        }, 1000);
                        showToast("Chat created! Opening conversation...");
                    } else {
                        new AlertDialog.Builder(UserDashboardActivity.this)
                                .setTitle("Chat Creation Failed")
                                .setMessage("Unable to create chat for this report. This may be due to:\n\n• Network connectivity issues\n• Server problems\n• Missing admin accounts\n\nPlease try again later or contact support if the problem persists.")
                                .setPositiveButton("OK", null)
                                .setNeutralButton("Retry", (dialog, which) -> {
                                    createChatAndOpen(reportId);
                                })
                                .show();
                    }
                }
            });
        } else {
            Log.e(TAG, "Report not found in user reports: " + reportId);
            showToast("Unable to find report details");
        }
    }

    private void showFullPhotoDialog(String photoBase64) {
        if (photoBase64 == null || photoBase64.isEmpty()) {
            showToast("No photo available");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Create ScrollView to handle large images
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        ImageView imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        try {
            Bitmap bitmap = base64ToBitmap(photoBase64);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                scrollView.addView(imageView);

                builder.setView(scrollView);
                builder.setTitle("Report Photo");
                builder.setPositiveButton("Close", null);

                AlertDialog dialog = builder.create();
                dialog.show();

                // Set dialog size to be larger for better photo viewing
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                }
            } else {
                showToast("Failed to load photo");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing full photo", e);
            showToast("Error displaying photo");
        }
    }

    // Add this helper method to set status chip color
    private void setStatusChipColor(Chip chip, String status) {
        if (status == null) status = "pending";

        switch (status.toLowerCase()) {
            case "completed":
                chip.setChipBackgroundColorResource(R.color.status_completed);
                break;
            case "in progress":
            case "scheduled":
                chip.setChipBackgroundColorResource(R.color.status_in_progress);
                break;
            case "pending":
            default:
                chip.setChipBackgroundColorResource(R.color.status_pending);
                break;
        }
    }

    private String buildReportDetailsString(Report report) {
        StringBuilder details = new StringBuilder();
        details.append("Description: ").append(report.description).append("\n\n");
        details.append("Location: ").append(report.location).append("\n");
        details.append("Priority: ").append(report.priority).append("\n");
        details.append("Status: ").append(report.status).append("\n");
        details.append("Created: ").append(new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                .format(new Date(report.createdAt)));

        // Add completion time if completed
        if ("completed".equalsIgnoreCase(report.status) && report.completedAt > 0) {
            details.append("\nCompleted: ").append(new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                    .format(new Date(report.completedAt)));
        }

        if (report.scheduledDate != null && !report.scheduledDate.isEmpty()) {
            details.append("\n\nScheduled: ").append(report.scheduledDate);
            if (report.scheduledTime != null && !report.scheduledTime.isEmpty()) {
                details.append(" at ").append(report.scheduledTime);
            }
        }

        if (report.assignedTo != null && !report.assignedTo.isEmpty()) {
            details.append("\nAssigned to: ").append(report.assignedTo);
            if (report.technicianPhone != null && !report.technicianPhone.isEmpty()) {
                details.append("\nPhone: ").append(report.technicianPhone);
            }
            if (report.technicianEmail != null && !report.technicianEmail.isEmpty()) {
                details.append("\nEmail: ").append(report.technicianEmail);
            }
        }

        return details.toString();
    }

    private void setupReportDetailsView(View dialogView, Report report) {
        // Setup custom view components if they exist
        TextView titleText = dialogView.findViewById(R.id.detailTitleText);
        if (titleText != null) titleText.setText(report.title);

        TextView descriptionText = dialogView.findViewById(R.id.detailDescriptionText);
        if (descriptionText != null) descriptionText.setText(report.description);

        // Add more view setup as needed
    }

    private void showPhotoDialog(String photoBase64) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        ImageView imageView = new ImageView(this);

        Bitmap bitmap = base64ToBitmap(photoBase64);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }

        builder.setView(imageView);
        builder.setTitle("Report Photo");
        builder.setPositiveButton("Close", null);
        builder.show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.user_menu, menu);

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
            loadUserReports();
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
        loadUserReports();

        // Resume notification checks when app is resumed
        if (notificationHandler != null && notificationCheckRunnable != null) {
            notificationHandler.post(notificationCheckRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop notification checks
        if (notificationHandler != null && notificationCheckRunnable != null) {
            notificationHandler.removeCallbacks(notificationCheckRunnable);
        }

        // Cleanup adapter listeners
        if (reportAdapter != null) {
            reportAdapter.cleanup();
        }
    }

    // 6. ADD method to show notifications dialog:
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
                            new AlertDialog.Builder(UserDashboardActivity.this)
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
            NotificationHelper.clearAllNotifications(currentUser.getUid());
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
        if (currentUser != null && notifId != null) {
            NotificationHelper.markNotificationAsRead(currentUser.getUid(), notifId);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(body);

        if (reportId != null && !reportId.isEmpty()) {
            builder.setPositiveButton("View Report", (dialog, which) -> {
                // Find and show the report
                for (Report report : userReports) {
                    if (report.reportId.equals(reportId)) {
                        showReportDetails(report);
                        break;
                    } else {
                        showToast("Report not found or has been deleted");
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

    // Interface for chat creation callback
    interface ChatCreationCallback {
        void onChatCreated(boolean success);
    }

    // Method to create report chat with callback
    // Method to create report chat with callback - FIXED VERSION
    private void createReportChatWithCallback(String reportId, String reportTitle, ChatCreationCallback callback) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        // Find ALL admins to include in chat
        mDatabase.child("users").orderByChild("role").equalTo("ADMIN")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String chatId = ChatHelper.generateReportChatId(reportId);

                        // Create the complete chat structure that matches Firebase rules
                        Map<String, Object> completeChat = new HashMap<>();

                        // Create metadata object (this is what the rules expect!)
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("reportId", reportId);
                        metadata.put("reportTitle", reportTitle);
                        metadata.put("createdAt", System.currentTimeMillis());
                        metadata.put("createdBy", currentUser.getUid());
                        metadata.put("chatType", "report");

                        // Add participants - reporter + ALL admins
                        Map<String, Boolean> participants = new HashMap<>();
                        participants.put(currentUser.getUid(), true); // Add current user (reporter)

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
                                    //sendInitialChatMessage(chatId, reportTitle, reportId);

                                    callback.onChatCreated(true);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create chat", e);
                                    callback.onChatCreated(false);
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to find admins", error.toException());
                        callback.onChatCreated(false);
                    }
                });
    }

    // Method to send initial chat message - FIXED VERSION
    private void sendInitialChatMessage(String chatId, String reportTitle, String reportId) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        String messageId = mDatabase.child("chats").child(chatId).child("messages").push().getKey();
        if (messageId != null) {
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("senderId", "system");
            messageData.put("senderName", "System");
            messageData.put("senderRole", "SYSTEM");
            messageData.put("message", "Chat started for report: " + reportTitle + "\n\nYou can now communicate with administrators about this maintenance request.");
            messageData.put("timestamp", System.currentTimeMillis());
            messageData.put("messageType", "system");
            messageData.put("reportId", reportId);
            messageData.put("readBy", new HashMap<String, Long>());

            mDatabase.child("chats").child(chatId).child("messages").child(messageId)
                    .setValue(messageData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Initial message sent for chat: " + chatId);

                        // Update metadata with last message info
                        updateChatMetadata(chatId, "Chat started for report: " + reportTitle);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send initial message", e);
                    });
        }
    }

    // Helper method to update chat metadata
    private void updateChatMetadata(String chatId, String lastMessage) {
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();

        Map<String, Object> metadataUpdates = new HashMap<>();
        metadataUpdates.put("lastMessage", lastMessage);
        metadataUpdates.put("lastMessageTime", System.currentTimeMillis());
        metadataUpdates.put("lastMessageSender", "System");

        mDatabase.child("chats").child(chatId).child("metadata").updateChildren(metadataUpdates);
    }

    // IMPROVED: Better error handling for startReportChatSafely
    private void startReportChatSafely(String reportId) {
        String chatId = ChatHelper.generateReportChatId(reportId);

        // Show progress
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Loading Chat")
                .setMessage("Please wait...")
                .setCancelable(false)
                .create();

        progressDialog.show();

        // Check if chat exists first with better error handling
        mDatabase.child("chats").child(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressDialog.dismiss();

                if (snapshot.exists()) {
                    // Verify the chat has proper structure
                    DataSnapshot metadataSnapshot = snapshot.child("metadata");
                    DataSnapshot participantsSnapshot = metadataSnapshot.child("participants");

                    if (participantsSnapshot.hasChild(currentUser.getUid())) {
                        // User is a participant, open chat
                        ChatHelper.startReportChat(UserDashboardActivity.this, reportId);
                    } else {
                        // User is not a participant, add them first
                        Log.d(TAG, "Adding user to existing chat");
                        addUserToExistingChat(chatId, reportId);
                    }
                } else {
                    // Chat doesn't exist, create it
                    Log.d(TAG, "Chat doesn't exist, creating for report: " + reportId);
                    createChatAndOpen(reportId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressDialog.dismiss();
                Log.e(TAG, "Failed to check chat existence", error.toException());

                // Show detailed error message
                String errorMessage = "Failed to access chat";
                if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                    errorMessage = "Permission denied. Unable to access chat for this report.";
                }

                new AlertDialog.Builder(UserDashboardActivity.this)
                        .setTitle("Chat Access Error")
                        .setMessage(errorMessage + "\n\nError details: " + error.getMessage())
                        .setPositiveButton("OK", null)
                        .setNeutralButton("Retry", (dialog, which) -> {
                            startReportChatSafely(reportId);
                        })
                        .show();
            }
        });
    }

    // Helper method to add user to existing chat
    private void addUserToExistingChat(String chatId, String reportId) {
        mDatabase.child("chats").child(chatId).child("metadata").child("participants")
                .child(currentUser.getUid()).setValue(true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User added to existing chat successfully");
                    ChatHelper.startReportChat(UserDashboardActivity.this, reportId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add user to existing chat", e);
                    showToast("Failed to join chat: " + e.getMessage());
                });
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

                    redirectToLogin();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, Login.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Report Model
    public static class Report {
        public String reportId;
        public String title;
        public String description;
        public String location;
        public String campus;
        public String department;
        public String priority;
        public String status;
        public String reportedBy;
        public String reportedByEmail;
        public String reportedByUid;
        public String photoUrl;
        public String scheduledDate;
        public String scheduledTime;
        public String assignedTo;
        public String technicianEmail;
        public String technicianPhone;
        public long createdAt;
        public long completedAt;
        public String photoBase64;
        public String completionPhotoBase64;
    }

    // Report Adapter
    // Report Adapter - Updated version with Chat Button
    private class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {
        private List<Report> allReports;
        private List<Report> filteredReports;
        private String currentFilter = null;
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        private Map<String, Integer> unreadCounts = new HashMap<>(); // Store unread counts per report
        private Map<String, ValueEventListener> listeners = new HashMap<>(); // Track listeners to avoid memory leaks

        public ReportAdapter(List<Report> reports) {
            this.allReports = reports;
            this.filteredReports = new ArrayList<>(reports);
        }

        public void setFilter(String filter) {
            currentFilter = filter;
            applyFilter();
        }

        public void updateData(List<Report> newReports) {
            this.allReports = newReports;
            applyFilter();

            // Load unread counts for all reports
            loadUnreadCounts();
        }

        private void applyFilter() {
            filteredReports.clear();

            if (currentFilter == null) {
                filteredReports.addAll(allReports);
            } else {
                for (Report report : allReports) {
                    if (currentFilter.equalsIgnoreCase(report.status)) {
                        filteredReports.add(report);
                    }
                }
            }
            notifyDataSetChanged();
        }

        private void loadUnreadCounts() {
            // Clear existing listeners to prevent memory leaks
            clearListeners();

            // Load unread count for each report
            for (Report report : allReports) {
                loadUnreadCountForReport(report.reportId);
            }
        }

        private void clearListeners() {
            for (Map.Entry<String, ValueEventListener> entry : listeners.entrySet()) {
                String reportId = entry.getKey();
                ValueEventListener listener = entry.getValue();
                String chatId = ChatHelper.generateReportChatId(reportId);

                mDatabase.child("chats").child(chatId).child("messages")
                        .removeEventListener(listener);
            }
            listeners.clear();
        }

        private void loadUnreadCountForReport(String reportId) {
            String chatId = ChatHelper.generateReportChatId(reportId);

            ValueEventListener listener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    int unreadCount = 0;

                    for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                        String senderId = messageSnapshot.child("senderId").getValue(String.class);
                        DataSnapshot readBySnapshot = messageSnapshot.child("readBy");

                        // If message is not from current user and not read by current user
                        if (!currentUser.getUid().equals(senderId) &&
                                !readBySnapshot.hasChild(currentUser.getUid())) {
                            unreadCount++;
                        }
                    }

                    // Update unread count and refresh UI if changed
                    Integer previousCount = unreadCounts.get(reportId);
                    if (previousCount == null || previousCount != unreadCount) {
                        unreadCounts.put(reportId, unreadCount);

                        // Only notify for specific items instead of entire dataset
                        notifyItemRangeChanged(0, getItemCount());
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to load unread count for report: " + reportId, error.toException());
                }
            };

            // Store the listener for cleanup
            listeners.put(reportId, listener);

            mDatabase.child("chats").child(chatId).child("messages")
                    .addValueEventListener(listener);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user_report, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Report report = filteredReports.get(position);

            holder.titleText.setText(report.title);
            holder.descriptionText.setText(report.description);
            holder.locationText.setText("Location: " + report.location);
            holder.dateText.setText(dateFormat.format(new Date(report.createdAt)));


            String date = report.scheduledDate != null ? report.scheduledDate : "";
            String time = report.scheduledTime != null ? report.scheduledTime : "";

            if (!date.isEmpty() && !time.isEmpty()) {
                holder.scheduleText.setVisibility(View.VISIBLE);
                holder.scheduleText.setText("Scheduled: " + date + " at " + time);
            } else {
                holder.scheduleText.setVisibility(View.GONE);
            }

            // Set priority chip
            holder.priorityChip.setText(report.priority.toUpperCase());
            setPriorityColor(holder.priorityChip, report.priority);

            // Set status chip
            holder.statusChip.setText(report.status.toUpperCase());
            setStatusColor(holder.statusChip, report.status);

            // Setup unread badge BEFORE setting up action buttons
            setupUnreadBadge(holder, report);

            // Setup action buttons
            setupActionButtons(holder, report);

            // Click listener for details
            holder.itemView.setOnClickListener(v -> showReportDetails(report));
        }

        private void setupUnreadBadge(ViewHolder holder, Report report) {
            Integer unreadCount = unreadCounts.get(report.reportId);

            if (unreadCount != null && unreadCount > 0) {
                holder.chatUnreadBadge.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
                holder.chatUnreadBadge.setVisibility(View.VISIBLE);
            } else {
                holder.chatUnreadBadge.setVisibility(View.GONE);
            }
        }

        private void setupActionButtons(ViewHolder holder, Report report) {
            // Chat button - always visible and enabled
            holder.chatButton.setOnClickListener(v -> {
                // Clear unread badge immediately for better UX
                unreadCounts.put(report.reportId, 0);
                holder.chatUnreadBadge.setVisibility(View.GONE);

                // Mark chat as read when clicking
                String chatId = ChatHelper.generateReportChatId(report.reportId);
                ChatHelper.markChatAsRead(chatId, currentUser.getUid());

                startReportChatWithProgress(report.reportId);
            });

            // Add visual feedback for chat button
            holder.chatButton.setOnTouchListener((view, motionEvent) -> {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setAlpha(0.7f);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.setAlpha(1.0f);
                        break;
                }
                return false;
            });
/*
            // Update Status button
            holder.updateStatusButton.setOnClickListener(v -> showStatusUpdateDialog(report));

            // Delete button - show for ALL reports
            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setOnClickListener(v -> showDeleteDialog(report));

            // Update button text based on status for better UX
            switch (report.status.toLowerCase()) {
                case "pending":
                    holder.updateStatusButton.setText("Update");
                    break;
                case "in progress":
                    holder.updateStatusButton.setText("Complete");
                    break;
                case "completed":
                    holder.updateStatusButton.setText("Actions");
                    break;
                default:
                    holder.updateStatusButton.setText("Update");
                    break;
            }
            */

        }

        @Override
        public int getItemCount() {
            return filteredReports.size();
        }

        // Method to cleanup listeners when adapter is destroyed
        public void cleanup() {
            clearListeners();
        }

        private void setPriorityColor(Chip chip, String priority) {
            switch (priority.toLowerCase()) {
                case "critical":
                    chip.setChipBackgroundColorResource(R.color.priority_critical);
                    break;
                case "high":
                    chip.setChipBackgroundColorResource(R.color.priority_high);
                    break;
                case "medium":
                    chip.setChipBackgroundColorResource(R.color.priority_medium);
                    break;
                case "low":
                default:
                    chip.setChipBackgroundColorResource(R.color.priority_low);
                    break;
            }
        }

        private void setStatusColor(Chip chip, String status) {
            switch (status.toLowerCase()) {
                case "completed":
                    chip.setChipBackgroundColorResource(R.color.status_completed);
                    break;
                case "in progress":
                case "scheduled":
                    chip.setChipBackgroundColorResource(R.color.status_in_progress);
                    break;
                case "pending":
                default:
                    chip.setChipBackgroundColorResource(R.color.status_pending);
                    break;
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView scheduleText;
            TextView titleText, descriptionText, locationText, dateText;
            Chip priorityChip, statusChip;
            MaterialButton chatButton;
            TextView chatUnreadBadge; // Add this new field

            ViewHolder(View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.titleText);
                descriptionText = itemView.findViewById(R.id.descriptionText);
                locationText = itemView.findViewById(R.id.locationText);
                dateText = itemView.findViewById(R.id.dateText);
                priorityChip = itemView.findViewById(R.id.priorityChip);
                statusChip = itemView.findViewById(R.id.statusChip);
                chatButton = itemView.findViewById(R.id.chatButton);
                scheduleText = itemView.findViewById(R.id.scheduleText);

                //updateStatusButton = itemView.findViewById(R.id.updateStatusButton);
               // deleteButton = itemView.findViewById(R.id.deleteButton);
                chatUnreadBadge = itemView.findViewById(R.id.chatUnreadBadge); // Initialize the badge
            }
        }
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