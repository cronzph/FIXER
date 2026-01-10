package com.fixer.app;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.graphics.pdf.PdfDocument;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

            public class ReportManagementActivity extends AppCompatActivity {

                private static final String TAG = "ReportManagement";

                // UI Components
                private SearchView searchView;
                private ChipGroup statusChipGroup;
                private MaterialButton filterButton;
                private RecyclerView reportsRecyclerView;
                private SwipeRefreshLayout swipeRefreshLayout;
                private TextView emptyStateText;
                private TextView filterIndicator;
                private ChipGroup activeFiltersChipGroup;
                private static final int REQUEST_ADMIN_COMPLETION_IMAGE_CAPTURE = 5;
                private static final int REQUEST_ADMIN_COMPLETION_IMAGE_PICK = 6;
                private String adminCompletionImageBase64 = null;
                private String currentReportIdForAdminCompletion = null;
                private AlertDialog currentAdminCompletionDialog = null;
                private View currentAdminCompletionDialogView = null;
                private static final int REQUEST_WRITE_STORAGE = 112;


                // Firebase
                private DatabaseReference mDatabase;
                private FirebaseAuth mAuth;

                // Data
                private List<MaintenanceReport> allReports;
                private List<MaintenanceReport> filteredReports;
                private ReportAdapter reportAdapter;
                private List<String> campusList;
                private Map<String, Integer> campusIssueCount;

                // Filter Settings
                private FilterSettings currentFilters;
                private String searchQuery = "";
                private String initialFilterStatus = null;

                private View createAdminCompletionView() {
                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(32, 32, 32, 32);

                    // Title
                    TextView titleText = new TextView(this);
                    titleText.setId(R.id.completionTitleText);
                    titleText.setTextSize(18);
                    titleText.setPadding(0, 0, 0, 16);
                    layout.addView(titleText);

                    // Instruction
                    TextView instructionText = new TextView(this);
                    instructionText.setId(R.id.completionInstructionText);
                    instructionText.setPadding(0, 0, 0, 16);
                    layout.addView(instructionText);

                    // Add Photo Button
                    MaterialButton addPhotoButton = new MaterialButton(this);
                    addPhotoButton.setId(R.id.addCompletionPhotoButton);
                    addPhotoButton.setText("Add Completion Photo");
                    layout.addView(addPhotoButton);

                    // Image View
                    ImageView photoImageView = new ImageView(this);
                    photoImageView.setId(R.id.completionImageView);
                    photoImageView.setAdjustViewBounds(true);
                    photoImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    photoImageView.setVisibility(View.GONE);
                    layout.addView(photoImageView);

                    // Remove Photo Button
                    MaterialButton removeButton = new MaterialButton(this);
                    removeButton.setId(R.id.removeCompletionPhotoButton);
                    removeButton.setText("Remove Photo");
                    removeButton.setVisibility(View.GONE);
                    layout.addView(removeButton);

                    return layout;
                }

                // Filter class to hold all filter settings
                private class FilterSettings {
                    String status = "all";
                    String priority = "all";
                    // String campus = "all";  // REMOVE THIS LINE
                    String department = "all";
                    String location = "all";
                    String reportedBy = "all";
                    boolean showOnlyWithPhotos = false;
                    boolean showOnlyScheduled = false;
                    boolean showCampusesWithProblems = false;
                    String dateFrom = "";
                    String dateTo = "";
                    String assignedTo = "";

                    public void reset() {
                        status = "all";
                        priority = "all";
                        // campus = "all";  // REMOVE THIS LINE
                        department = "all";
                        location = "all";
                        reportedBy = "all";
                        showOnlyWithPhotos = false;
                        showOnlyScheduled = false;
                        showCampusesWithProblems = false;
                        dateFrom = "";
                        dateTo = "";
                        assignedTo = "";
                    }

                    public boolean hasActiveFilters() {
                        return !status.equals("all") ||
                                !priority.equals("all") ||
                                // !campus.equals("all") ||  // REMOVE THIS LINE
                                !department.equals("all") ||
                                !location.equals("all") ||
                                !reportedBy.equals("all") ||
                                showOnlyWithPhotos ||
                                showOnlyScheduled ||
                                showCampusesWithProblems ||
                                !dateFrom.isEmpty() ||
                                !dateTo.isEmpty() ||
                                !assignedTo.isEmpty();
                    }

                    public int getActiveFilterCount() {
                        int count = 0;
                        if (!status.equals("all")) count++;
                        if (!priority.equals("all")) count++;
                        // if (!campus.equals("all")) count++;  // REMOVE THIS LINE
                        if (!department.equals("all")) count++;
                        if (!location.equals("all")) count++;
                        if (!reportedBy.equals("all")) count++;
                        if (showOnlyWithPhotos) count++;
                        if (showOnlyScheduled) count++;
                        if (showCampusesWithProblems) count++;
                        if (!dateFrom.isEmpty()) count++;
                        if (!dateTo.isEmpty()) count++;
                        if (!assignedTo.isEmpty()) count++;
                        return count;
                    }
                }


                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setContentView(R.layout.activity_report_management);

                    Intent intent = getIntent();
                    if (intent != null) {
                        // Check for status filter
                        if (intent.hasExtra("filter_status")) {
                            initialFilterStatus = intent.getStringExtra("filter_status");
                        }

                        // Check for problem campuses filter
                        if (intent.getBooleanExtra("filter_problem_campuses", false)) {
                            // Will be set after filters are initialized
                        }

                        // REMOVE THIS ENTIRE BLOCK:
        /*
        if (intent.hasExtra("filter_campus")) {
            // Will be set after filters are initialized
        }
        */
                    }

                    // Initialize Firebase
                    mDatabase = FirebaseDatabase.getInstance().getReference();
                    mAuth = FirebaseAuth.getInstance();

                    // Initialize filter settings
                    currentFilters = new FilterSettings();

                    // Apply intent filters
                    if (intent != null) {
                        if (initialFilterStatus != null) {
                            currentFilters.status = initialFilterStatus;
                        }

                        // Check for problem campuses filter
                        if (intent.getBooleanExtra("filter_problem_campuses", false)) {
                            currentFilters.showCampusesWithProblems = true;
                        }

                        // REMOVE THIS ENTIRE BLOCK:
        /*
        if (intent.hasExtra("filter_campus")) {
            currentFilters.campus = intent.getStringExtra("filter_campus");
            // Update toolbar title to show filtered campus
            if (getSupportActionBar() != null && currentFilters.campus != null) {
                getSupportActionBar().setTitle("Reports - " + currentFilters.campus);
            }
        }
        */

                        // Check for active only filter
                        if (intent.getBooleanExtra("filter_active_only", false)) {
                            currentFilters.status = "active";
                        }
                    }

                    // Setup toolbar
                    setupToolbar();

                    // Initialize UI components
                    initializeViews();

                    // Setup RecyclerView
                    setupRecyclerView();

                    // Setup quick status filters
                    setupQuickStatusFilters();

                    // Setup search
                    setupSearch();

                    // Load data
                    loadCampusData();
                    loadReports();
                }

                private void showExportOptions() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Export Reports to PDF");

                    View dialogView = createExportOptionsView();
                    builder.setView(dialogView);

                    final Spinner exportTypeSpinner = dialogView.findViewById(R.id.exportTypeSpinner);
                    final Spinner exportValueSpinner = dialogView.findViewById(R.id.exportValueSpinner);

                    // Setup export type spinner
                    String[] exportTypes = {"All Reports", "By Status", "By Priority", "By Campus", "By Department", "Current Filters"};
                    ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, exportTypes);
                    exportTypeSpinner.setAdapter(typeAdapter);

                    // Listen for export type changes to update value spinner
                    exportTypeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                            updateExportValueSpinner(exportValueSpinner, position);
                        }

                        @Override
                        public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    });

                    builder.setPositiveButton("Export", (dialog, which) -> {
                        String exportType = exportTypeSpinner.getSelectedItem().toString();
                        String exportValue = exportValueSpinner.getSelectedItem() != null ?
                                exportValueSpinner.getSelectedItem().toString() : "";

                        if (checkWriteStoragePermission()) {
                            exportToPDF(exportType, exportValue);
                        }
                    });

                    builder.setNegativeButton("Cancel", null);
                    builder.show();
                }

                private View createExportOptionsView() {
                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(50, 40, 50, 10);

                    TextView instructionText = new TextView(this);
                    instructionText.setText("Select what to export:");
                    instructionText.setPadding(0, 0, 0, 20);
                    instructionText.setTextSize(16);
                    layout.addView(instructionText);

                    TextView typeLabel = new TextView(this);
                    typeLabel.setText("Export Type:");
                    typeLabel.setPadding(0, 0, 0, 8);
                    layout.addView(typeLabel);

                    Spinner exportTypeSpinner = new Spinner(this);
                    exportTypeSpinner.setId(R.id.exportTypeSpinner);
                    layout.addView(exportTypeSpinner);

                    TextView valueLabel = new TextView(this);
                    valueLabel.setText("Select Value:");
                    valueLabel.setPadding(0, 20, 0, 8);
                    valueLabel.setId(R.id.exportValueLabel);
                    layout.addView(valueLabel);

                    Spinner exportValueSpinner = new Spinner(this);
                    exportValueSpinner.setId(R.id.exportValueSpinner);
                    layout.addView(exportValueSpinner);

                    return layout;
                }

                private void updateExportValueSpinner(Spinner valueSpinner, int exportTypePosition) {
                    List<String> values = new ArrayList<>();

                    switch (exportTypePosition) {
                        case 0: // All Reports
                            values.add("All");
                            valueSpinner.setEnabled(false);
                            break;
                        case 1: // By Status
                            values.addAll(Arrays.asList("Pending", "Scheduled", "In Progress", "Partially Completed", "Completed", "Rejected"));
                            valueSpinner.setEnabled(true);
                            break;
                        case 2: // By Priority
                            values.addAll(Arrays.asList("Critical", "High", "Medium", "Low"));
                            valueSpinner.setEnabled(true);
                            break;
                        case 3: // By Campus
                            values.addAll(campusList);
                            if (values.isEmpty()) values.add("No campuses available");
                            valueSpinner.setEnabled(true);
                            break;
                        case 4: // By Department
                            Set<String> departments = new HashSet<>();
                            for (MaintenanceReport report : allReports) {
                                if (report.department != null && !report.department.isEmpty()) {
                                    departments.add(report.department);
                                }
                            }
                            values.addAll(departments);
                            if (values.isEmpty()) values.add("No departments available");
                            valueSpinner.setEnabled(true);
                            break;
                        case 5: // Current Filters
                            values.add("Current View");
                            valueSpinner.setEnabled(false);
                            break;
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, values);
                    valueSpinner.setAdapter(adapter);
                }

                private boolean checkWriteStoragePermission() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ doesn't need WRITE_EXTERNAL_STORAGE for app-specific directory
                        return true;
                    } else {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    REQUEST_WRITE_STORAGE);
                            return false;
                        }
                        return true;
                    }
                }

                private void exportToPDF(String exportType, String exportValue) {
                    // Filter reports based on export criteria
                    List<MaintenanceReport> reportsToExport = new ArrayList<>();

                    switch (exportType) {
                        case "All Reports":
                            reportsToExport.addAll(allReports);
                            break;
                        case "By Status":
                            for (MaintenanceReport report : allReports) {
                                if (report.status != null &&
                                        report.status.equalsIgnoreCase(exportValue)) {
                                    reportsToExport.add(report);
                                }
                            }
                            break;
                        case "By Priority":
                            for (MaintenanceReport report : allReports) {
                                if (report.priority != null &&
                                        report.priority.equalsIgnoreCase(exportValue)) {
                                    reportsToExport.add(report);
                                }
                            }
                            break;
                        case "By Campus":
                            for (MaintenanceReport report : allReports) {
                                if (report.campus != null &&
                                        report.campus.equalsIgnoreCase(exportValue)) {
                                    reportsToExport.add(report);
                                }
                            }
                            break;
                        case "By Department":
                            for (MaintenanceReport report : allReports) {
                                if (report.department != null &&
                                        report.department.equalsIgnoreCase(exportValue)) {
                                    reportsToExport.add(report);
                                }
                            }
                            break;
                        case "Current Filters":
                            reportsToExport.addAll(filteredReports);
                            break;
                    }

                    if (reportsToExport.isEmpty()) {
                        showToast("No reports to export");
                        return;
                    }

                    generatePDF(reportsToExport, exportType, exportValue);
                }

                private void generatePDF(List<MaintenanceReport> reports, String exportType, String exportValue) {
                    PdfDocument document = new PdfDocument();

                    int pageWidth = 595;
                    int pageHeight = 842;
                    int margin = 40;
                    int contentWidth = pageWidth - (2 * margin);

                    // ALL PAINT OBJECTS USE BLACK COLOR ONLY
                    Paint titlePaint = new Paint();
                    titlePaint.setTextSize(20);
                    titlePaint.setColor(Color.BLACK);
                    titlePaint.setFakeBoldText(true);

                    Paint headerPaint = new Paint();
                    headerPaint.setTextSize(14);
                    headerPaint.setColor(Color.BLACK);
                    headerPaint.setFakeBoldText(true);

                    Paint normalPaint = new Paint();
                    normalPaint.setTextSize(11);
                    normalPaint.setColor(Color.BLACK);

                    Paint smallPaint = new Paint();
                    smallPaint.setTextSize(9);
                    smallPaint.setColor(Color.BLACK);  // Changed from GRAY to BLACK

                    int pageNumber = 1;
                    int yPos = margin + 30;

                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    PdfDocument.Page page = document.startPage(pageInfo);
                    Canvas canvas = page.getCanvas();

                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    canvas.drawText("Maintenance Reports", margin, yPos, titlePaint);
                    yPos += 25;

                    String subtitle = exportType.equals("Current Filters") ?
                            "Filtered View" : exportType + ": " + exportValue;
                    canvas.drawText(subtitle, margin, yPos, headerPaint);
                    yPos += 20;

                    canvas.drawText("Generated: " + dateFormat.format(new Date()), margin, yPos, smallPaint);
                    canvas.drawText("Total Reports: " + reports.size(), pageWidth - margin - 150, yPos, smallPaint);
                    yPos += 30;

                    for (int i = 0; i < reports.size(); i++) {
                        MaintenanceReport report = reports.get(i);

                        int baseReportHeight = 200;  // Increased for additional info
                        int photoHeight = 0;
                        boolean hasReportPhoto = report.photoUrl != null && !report.photoUrl.isEmpty();
                        boolean hasCompletionPhoto = report.completionPhotoBase64 != null && !report.completionPhotoBase64.isEmpty();

                        if (hasReportPhoto) photoHeight += 180;
                        if (hasCompletionPhoto) photoHeight += 180;

                        int totalReportHeight = baseReportHeight + photoHeight;

                        if (yPos + totalReportHeight > pageHeight - 50) {
                            document.finishPage(page);
                            pageNumber++;
                            pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                            page = document.startPage(pageInfo);
                            canvas = page.getCanvas();
                            yPos = margin + 30;
                        }

                        Paint linePaint = new Paint();
                        linePaint.setColor(Color.BLACK);  // Changed from LTGRAY to BLACK
                        linePaint.setStrokeWidth(1);
                        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, linePaint);
                        yPos += 15;

                        String title = (i + 1) + ". " + (report.title != null ? report.title : "Untitled");
                        canvas.drawText(title, margin, yPos, headerPaint);
                        yPos += 20;

                        canvas.drawText("Status: " + report.status.toUpperCase(), margin + 10, yPos, normalPaint);
                        canvas.drawText("Priority: " + report.priority.toUpperCase(),
                                margin + 200, yPos, normalPaint);
                        yPos += 18;

                        canvas.drawText("Location: " + report.location, margin + 10, yPos, normalPaint);
                        yPos += 18;

                        canvas.drawText("Campus: " + report.campus, margin + 10, yPos, normalPaint);
                        yPos += 18;

                        if (report.department != null && !report.department.isEmpty()) {
                            canvas.drawText("Department: " + report.department, margin + 10, yPos, normalPaint);
                            yPos += 18;
                        }

                        String description = report.description;
                        if (description != null && description.length() > 100) {
                            description = description.substring(0, 97) + "...";
                        }
                        canvas.drawText("Description: " + (description != null ? description : "N/A"),
                                margin + 10, yPos, normalPaint);
                        yPos += 18;

                        canvas.drawText("Reported: " + dateFormat.format(new Date(report.createdAt)),
                                margin + 10, yPos, smallPaint);
                        yPos += 15;

                        canvas.drawText("Reported by: " + report.reportedBy, margin + 10, yPos, smallPaint);
                        yPos += 18;

                        // Show scheduled date and time
                        if (report.scheduledDate != null && !report.scheduledDate.isEmpty()) {
                            String scheduleText = "Scheduled: " + report.scheduledDate;
                            if (report.scheduledTime != null && !report.scheduledTime.isEmpty()) {
                                scheduleText += " at " + report.scheduledTime;
                            }
                            canvas.drawText(scheduleText, margin + 10, yPos, smallPaint);
                            yPos += 15;
                        }

                        if (report.assignedTo != null && !report.assignedTo.isEmpty()) {
                            canvas.drawText("Assigned to: " + report.assignedTo, margin + 10, yPos, smallPaint);
                            yPos += 15;
                        }

                        // Show completion info
                        if (("completed".equalsIgnoreCase(report.status) || "partially completed".equalsIgnoreCase(report.status))
                                && report.completedAt > 0) {
                            canvas.drawText("Completed: " + dateFormat.format(new Date(report.completedAt)),
                                    margin + 10, yPos, smallPaint);
                            yPos += 15;

                            if (report.completedBy != null && !report.completedBy.isEmpty()) {
                                canvas.drawText("Completed by: " + report.completedBy, margin + 10, yPos, smallPaint);
                                yPos += 15;
                            }
                        }

                        // Show partial completion notes
                        if ("partially completed".equalsIgnoreCase(report.status) &&
                                report.partialCompletionNotes != null && !report.partialCompletionNotes.isEmpty()) {
                            canvas.drawText("Notes: " + report.partialCompletionNotes, margin + 10, yPos, smallPaint);
                            yPos += 15;
                        }

                        // Draw photos
                        if (hasReportPhoto || hasCompletionPhoto) {
                            yPos += 5;
                            canvas.drawText("Photos:", margin + 10, yPos, normalPaint);
                            yPos += 15;

                            int photoTop = yPos;
                            int photoSize = 150;
                            int spacing = 20;

                            int reportPhotoX = margin + 10;
                            int completionPhotoX = reportPhotoX + photoSize + spacing;

                            if (hasReportPhoto) {
                                Bitmap reportBitmap = base64ToBitmap(report.photoUrl);
                                if (reportBitmap != null) {
                                    Bitmap scaledBitmap = scaleBitmapForPDF(reportBitmap, photoSize, photoSize);
                                    canvas.drawBitmap(scaledBitmap, reportPhotoX, photoTop, null);
                                    scaledBitmap.recycle();
                                    canvas.drawText("Report Photo", reportPhotoX, photoTop + photoSize + 12, smallPaint);
                                }
                            }

                            if (hasCompletionPhoto) {
                                Bitmap completionBitmap = base64ToBitmap(report.completionPhotoBase64);
                                if (completionBitmap != null) {
                                    Bitmap scaledBitmap = scaleBitmapForPDF(completionBitmap, photoSize, photoSize);
                                    canvas.drawBitmap(scaledBitmap, completionPhotoX, photoTop, null);
                                    scaledBitmap.recycle();
                                    canvas.drawText("Completion Photo", completionPhotoX, photoTop + photoSize + 12, smallPaint);
                                }
                            }

                            yPos += photoSize + 25;
                        }

                        yPos += 15;
                    }

                    document.finishPage(page);

                    // Save document (rest of the code remains the same)
                    String fileName = "Reports_" + exportType.replaceAll(" ", "_") + "_" +
                            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".pdf";

                    File file;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MaintenanceReports");
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }
                        file = new File(dir, fileName);
                    } else {
                        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                                "MaintenanceReports");
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }
                        file = new File(dir, fileName);
                    }

                    try {
                        FileOutputStream fos = new FileOutputStream(file);
                        document.writeTo(fos);
                        document.close();
                        fos.close();

                        showToast("PDF exported successfully to: " + file.getAbsolutePath());
                        showOpenPDFDialog(file);

                    } catch (Exception e) {
                        Log.e(TAG, "Error creating PDF", e);
                        showToast("Failed to export PDF: " + e.getMessage());
                    }
                }

                // Helper method to convert Base64 string to Bitmap
                private Bitmap base64ToBitmap(String base64String) {
                    try {
                        byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
                        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                    } catch (Exception e) {
                        Log.e(TAG, "Error decoding Base64 to Bitmap", e);
                        return null;
                    }
                }

                // Helper method to scale bitmap to fit PDF
                private Bitmap scaleBitmapForPDF(Bitmap bitmap, int maxWidth, int maxHeight) {
                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();

                    float aspectRatio = (float) width / height;
                    int newWidth, newHeight;

                    if (width > height) {
                        newWidth = Math.min(width, maxWidth);
                        newHeight = (int) (newWidth / aspectRatio);
                    } else {
                        newHeight = Math.min(height, maxHeight);
                        newWidth = (int) (newHeight * aspectRatio);
                    }

                    // Ensure it fits within max dimensions
                    if (newWidth > maxWidth) {
                        newWidth = maxWidth;
                        newHeight = (int) (maxWidth / aspectRatio);
                    }
                    if (newHeight > maxHeight) {
                        newHeight = maxHeight;
                        newWidth = (int) (maxHeight * aspectRatio);
                    }

                    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                }

                private void showOpenPDFDialog(File pdfFile) {
                    new AlertDialog.Builder(this)
                            .setTitle("PDF Exported")
                            .setMessage("PDF has been saved to:\n" + pdfFile.getAbsolutePath() +
                                    "\n\nWould you like to open it?")
                            .setPositiveButton("Open", (dialog, which) -> openPDF(pdfFile))
                            .setNegativeButton("Close", null)
                            .show();
                }

                private void openPDF(File pdfFile) {
                    Uri uri;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        uri = androidx.core.content.FileProvider.getUriForFile(this,
                                getApplicationContext().getPackageName() + ".provider", pdfFile);
                    } else {
                        uri = Uri.fromFile(pdfFile);
                    }

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "application/pdf");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        showToast("No PDF viewer app found. File saved to: " + pdfFile.getAbsolutePath());
                    }
                }


                private void setupToolbar() {
                    Toolbar toolbar = findViewById(R.id.toolbar);
                    setSupportActionBar(toolbar);
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle("Report Management");
                        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    }
                    toolbar.setNavigationOnClickListener(v -> onBackPressed());
                }


                private void initializeViews() {
                    searchView = findViewById(R.id.searchView);
                    statusChipGroup = findViewById(R.id.statusChipGroup);
                    filterButton = findViewById(R.id.filterButton);
                    reportsRecyclerView = findViewById(R.id.reportsRecyclerView);
                    swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
                    emptyStateText = findViewById(R.id.emptyStateText);
                    filterIndicator = findViewById(R.id.filterIndicator);
                    activeFiltersChipGroup = findViewById(R.id.activeFiltersChipGroup);

                    // Setup filter button
                    filterButton.setOnClickListener(v -> showAdvancedFilterDialog());

                    // Setup export button
                    addExportButton();

                    // Setup swipe refresh
                    swipeRefreshLayout.setOnRefreshListener(this::loadReports);
                    swipeRefreshLayout.setColorSchemeResources(
                            android.R.color.holo_red_light,
                            android.R.color.holo_orange_light
                    );

                    // Update filter indicator
                    updateFilterIndicator();
                }

                private void viewFullPhoto(String photoBase64) {
                    if (photoBase64 == null || photoBase64.isEmpty()) {
                        showToast("No photo attached to this report");
                        return;
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);

                    // Create ImageView programmatically since we're dealing with Base64
                    ImageView imageView = new ImageView(this);
                    imageView.setAdjustViewBounds(true);
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

                    try {
                        // Convert Base64 string to Bitmap
                        byte[] decodedBytes = Base64.decode(photoBase64, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap);

                            // Add padding to the ImageView
                            int padding = 16;
                            imageView.setPadding(padding, padding, padding, padding);

                            builder.setView(imageView);
                            builder.setTitle("Report Photo");
                            builder.setPositiveButton("Close", null);

                            AlertDialog dialog = builder.create();
                            dialog.show();
                        } else {
                            showToast("Failed to decode photo");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error decoding Base64 image", e);
                        showToast("Error displaying photo");
                    }
                }



                private void setupRecyclerView() {
                    allReports = new ArrayList<>();
                    filteredReports = new ArrayList<>();
                    campusList = new ArrayList<>();
                    campusIssueCount = new HashMap<>();
                    reportAdapter = new ReportAdapter(filteredReports);
                    reportsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                    reportsRecyclerView.setAdapter(reportAdapter);
                }

                private void setupQuickStatusFilters() {
                    String[] statuses = {"All", "Pending", "Scheduled", "In Progress", "Partially Completed", "Completed", "Rejected"};
                    String[] statusValues = {"all", "pending", "scheduled", "in progress", "partially completed", "completed", "rejected"};

                    for (int i = 0; i < statuses.length; i++) {
                        Chip chip = new Chip(this);
                        chip.setText(statuses[i]);
                        chip.setCheckable(true);

                        final String statusValue = statusValues[i];
                        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            if (isChecked) {
                                currentFilters.status = statusValue;
                                uncheckOtherStatusChips(chip);
                                applyFilters();
                                updateFilterIndicator();
                            }
                        });

                        statusChipGroup.addView(chip);

                        // Set initial selection
                        if (statusValue.equals(currentFilters.status)) {
                            chip.setChecked(true);
                        }
                    }
                }

                private void uncheckOtherStatusChips(Chip selectedChip) {
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

                private void showAdvancedFilterDialog() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_advanced_filters, null);

                    if (dialogView == null) {
                        dialogView = createAdvancedFilterView();
                    }

                    builder.setView(dialogView);
                    builder.setTitle("Advanced Filters");

                    Spinner statusSpinner = dialogView.findViewById(R.id.statusSpinner);
                    Spinner prioritySpinner = dialogView.findViewById(R.id.prioritySpinner);
                    Spinner departmentSpinner = dialogView.findViewById(R.id.departmentSpinner);
                    Spinner locationSpinner = dialogView.findViewById(R.id.locationSpinner);
                    Spinner userSpinner = dialogView.findViewById(R.id.userSpinner);
                    // REMOVE: Spinner campusSpinner = dialogView.findViewById(R.id.campusSpinner);

                    CheckBox photoCheckBox = dialogView.findViewById(R.id.photoCheckBox);
                    CheckBox scheduledCheckBox = dialogView.findViewById(R.id.scheduledCheckBox);
                    TextView dateFromText = dialogView.findViewById(R.id.dateFromText);
                    TextView dateToText = dialogView.findViewById(R.id.dateToText);
                    MaterialButton dateFromButton = dialogView.findViewById(R.id.dateFromButton);
                    MaterialButton dateToButton = dialogView.findViewById(R.id.dateToButton);
                    MaterialButton clearDateFromButton = dialogView.findViewById(R.id.clearDateFromButton);
                    MaterialButton clearDateToButton = dialogView.findViewById(R.id.clearDateToButton);
                    MaterialButton resetButton = dialogView.findViewById(R.id.resetFiltersButton);

                    // Setup spinners
                    setupFilterSpinner(statusSpinner, new String[]{"All", "Pending", "Scheduled", "In Progress", "Partially Completed", "Completed", "Rejected"},
                            currentFilters.status);
                    setupFilterSpinner(prioritySpinner, new String[]{"All", "Low", "Medium", "High", "Critical"},
                            currentFilters.priority);
                    setupDepartmentFilterSpinner(departmentSpinner);
                    setupLocationFilterSpinner(locationSpinner);
                    setupUserFilterSpinner(userSpinner);
                    // REMOVE: setupCampusFilterSpinner(campusSpinner);

                    // Rest of the method stays the same...
                    if (photoCheckBox != null) photoCheckBox.setChecked(currentFilters.showOnlyWithPhotos);
                    if (scheduledCheckBox != null) scheduledCheckBox.setChecked(currentFilters.showOnlyScheduled);

                    if (dateFromText != null && !currentFilters.dateFrom.isEmpty())
                        dateFromText.setText(currentFilters.dateFrom);
                    if (dateToText != null && !currentFilters.dateTo.isEmpty())
                        dateToText.setText(currentFilters.dateTo);

                    // Date picker handlers...
                    if (dateFromButton != null) {
                        dateFromButton.setOnClickListener(v -> {
                            showDatePickerSafe(date -> {
                                currentFilters.dateFrom = date;
                                if (dateFromText != null) dateFromText.setText(date);
                            });
                        });
                    }

                    if (dateToButton != null) {
                        dateToButton.setOnClickListener(v -> {
                            showDatePickerSafe(date -> {
                                currentFilters.dateTo = date;
                                if (dateToText != null) dateToText.setText(date);
                            });
                        });
                    }

                    if (clearDateFromButton != null) {
                        clearDateFromButton.setOnClickListener(v -> {
                            currentFilters.dateFrom = "";
                            if (dateFromText != null) dateFromText.setText("");
                        });
                    }

                    if (clearDateToButton != null) {
                        clearDateToButton.setOnClickListener(v -> {
                            currentFilters.dateTo = "";
                            if (dateToText != null) dateToText.setText("");
                        });
                    }

                    if (resetButton != null) {
                        resetButton.setOnClickListener(v -> {
                            currentFilters.reset();
                            if (statusSpinner != null) statusSpinner.setSelection(0);
                            if (prioritySpinner != null) prioritySpinner.setSelection(0);
                            if (departmentSpinner != null) departmentSpinner.setSelection(0);
                            if (locationSpinner != null) locationSpinner.setSelection(0);
                            if (userSpinner != null) userSpinner.setSelection(0);
                            // REMOVE: if (campusSpinner != null) campusSpinner.setSelection(0);
                            if (photoCheckBox != null) photoCheckBox.setChecked(false);
                            if (scheduledCheckBox != null) scheduledCheckBox.setChecked(false);
                            if (dateFromText != null) dateFromText.setText("");
                            if (dateToText != null) dateToText.setText("");
                        });
                    }

                    builder.setPositiveButton("Apply", (dialog, which) -> {
                        if (statusSpinner != null) {
                            String selected = statusSpinner.getSelectedItem().toString().toLowerCase();
                            currentFilters.status = selected.equals("in progress") ? "in progress" :
                                    selected.equals("partially completed") ? "partially completed" : selected;
                        }
                        if (prioritySpinner != null) {
                            currentFilters.priority = prioritySpinner.getSelectedItem().toString().toLowerCase();
                        }
                        if (departmentSpinner != null && departmentSpinner.getSelectedItemPosition() > 0) {
                            currentFilters.department = departmentSpinner.getSelectedItem().toString();
                        } else {
                            currentFilters.department = "all";
                        }
                        if (locationSpinner != null && locationSpinner.getSelectedItemPosition() > 0) {
                            currentFilters.location = locationSpinner.getSelectedItem().toString();
                        } else {
                            currentFilters.location = "all";
                        }
                        if (userSpinner != null && userSpinner.getSelectedItemPosition() > 0) {
                            currentFilters.reportedBy = userSpinner.getSelectedItem().toString();
                        } else {
                            currentFilters.reportedBy = "all";
                        }
                        // REMOVE THIS BLOCK:
        /*
        if (campusSpinner != null && campusSpinner.getSelectedItemPosition() > 0) {
            currentFilters.campus = campusSpinner.getSelectedItem().toString();
        } else {
            currentFilters.campus = "all";
        }
        */
                        if (photoCheckBox != null) {
                            currentFilters.showOnlyWithPhotos = photoCheckBox.isChecked();
                        }
                        if (scheduledCheckBox != null) {
                            currentFilters.showOnlyScheduled = scheduledCheckBox.isChecked();
                        }

                        updateStatusChipsFromFilter();
                        applyFilters();
                        updateFilterIndicator();
                        showActiveFilters();
                    });

                    builder.setNegativeButton("Cancel", null);
                    builder.show();
                }

                private void setupUserFilterSpinner(Spinner spinner) {
                    if (spinner == null) return;

                    // Collect unique users (reported by names) from reports
                    Set<String> users = new HashSet<>();
                    for (MaintenanceReport report : allReports) {
                        String userName = report.reportedByName != null ? report.reportedByName : report.reportedBy;
                        if (userName != null && !userName.isEmpty()) {
                            users.add(userName);
                        }
                    }

                    List<String> userOptions = new ArrayList<>();
                    userOptions.add("All Users");

                    // Sort users alphabetically
                    List<String> sortedUsers = new ArrayList<>(users);
                    Collections.sort(sortedUsers);
                    userOptions.addAll(sortedUsers);

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, userOptions);
                    spinner.setAdapter(adapter);

                    // Set current selection
                    if (!currentFilters.reportedBy.equals("all")) {
                        for (int i = 0; i < userOptions.size(); i++) {
                            if (userOptions.get(i).equals(currentFilters.reportedBy)) {
                                spinner.setSelection(i);
                                break;
                            }
                        }
                    }
                }

                private void setupLocationFilterSpinner(Spinner spinner) {
                    if (spinner == null) return;

                    // All locations from UserDashboard
                    String[] locations = {
                            "All Locations",
                            "Audio Visual Room",
                            "Canteen",
                            "Clinic",
                            "Computer Laboratory 1",
                            "Computer Laboratory 2",
                            "Dambana",
                            "Dean's Office",
                            "Faculty",
                            "Guard House",
                            "Guidance",
                            "Hostel",
                            "Infirmary",
                            "Library",
                            "LSC Office",
                            "NB1A",
                            "NB1B",
                            "NB2A",
                            "Science Laboratory"
                    };

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, locations);
                    spinner.setAdapter(adapter);

                    // Set current selection
                    if (!currentFilters.location.equals("all")) {
                        for (int i = 0; i < locations.length; i++) {
                            if (locations[i].equalsIgnoreCase(currentFilters.location)) {
                                spinner.setSelection(i);
                                break;
                            }
                        }
                    }
                }

                // NEW: Safe date picker with error handling
                private void showDatePickerSafe(DatePickerCallback callback) {
                    try {
                        Calendar calendar = Calendar.getInstance();
                        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                                (view, year, month, dayOfMonth) -> {
                                    try {
                                        String date = String.format(Locale.getDefault(), "%d-%02d-%02d",
                                                year, month + 1, dayOfMonth);
                                        callback.onDateSelected(date);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error formatting date", e);
                                        showToast("Error selecting date");
                                    }
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH));

                        datePickerDialog.setOnCancelListener(dialog -> {
                            // Handle cancel gracefully
                            Log.d(TAG, "Date picker cancelled");
                        });

                        datePickerDialog.show();
                    } catch (Exception e) {
                        Log.e(TAG, "Error showing date picker", e);
                        showToast("Error opening date picker");
                    }
                }



                private View createAdvancedFilterView() {
                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(16, 16, 16, 16);

                    // Status spinner
                    TextView statusLabel = new TextView(this);
                    statusLabel.setText("Status:");
                    layout.addView(statusLabel);

                    Spinner statusSpinner = new Spinner(this);
                    statusSpinner.setId(R.id.statusSpinner);
                    layout.addView(statusSpinner);

                    // Priority spinner
                    TextView priorityLabel = new TextView(this);
                    priorityLabel.setText("Priority:");
                    layout.addView(priorityLabel);

                    Spinner prioritySpinner = new Spinner(this);
                    prioritySpinner.setId(R.id.prioritySpinner);
                    layout.addView(prioritySpinner);

                    // REMOVE THIS ENTIRE CAMPUS SPINNER BLOCK:
    /*
    TextView campusLabel = new TextView(this);
    campusLabel.setText("Campus:");
    layout.addView(campusLabel);

    Spinner campusSpinner = new Spinner(this);
    campusSpinner.setId(R.id.campusSpinner);
    layout.addView(campusSpinner);
    */

                    // Checkboxes
                    CheckBox photoCheckBox = new CheckBox(this);
                    photoCheckBox.setId(R.id.photoCheckBox);
                    photoCheckBox.setText("Only reports with photos");
                    layout.addView(photoCheckBox);

                    CheckBox scheduledCheckBox = new CheckBox(this);
                    scheduledCheckBox.setId(R.id.scheduledCheckBox);
                    scheduledCheckBox.setText("Only scheduled reports");
                    layout.addView(scheduledCheckBox);

                    CheckBox problemCampusCheckBox = new CheckBox(this);
                    problemCampusCheckBox.setText("Only campuses with active issues");
                    layout.addView(problemCampusCheckBox);

                    // Reset button
                    MaterialButton resetButton = new MaterialButton(this);
                    resetButton.setId(R.id.resetFiltersButton);
                    resetButton.setText("Reset Filters");
                    layout.addView(resetButton);

                    return layout;
                }


                private void setupFilterSpinner(Spinner spinner, String[] options, String currentValue) {
                    if (spinner == null) return;

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, options);
                    spinner.setAdapter(adapter);

                    // Set current selection
                    for (int i = 0; i < options.length; i++) {
                        if (options[i].toLowerCase().equals(currentValue)) {
                            spinner.setSelection(i);
                            break;
                        }
                    }
                }

                private void setupDepartmentFilterSpinner(Spinner spinner) {
                    if (spinner == null) return;

                    // Collect unique departments from reports
                    Set<String> departments = new HashSet<>();
                    for (MaintenanceReport report : allReports) {
                        if (report.department != null && !report.department.isEmpty()) {
                            departments.add(report.department);
                        }
                    }

                    List<String> departmentOptions = new ArrayList<>();
                    departmentOptions.add("All Departments");
                    departmentOptions.addAll(departments);

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, departmentOptions);
                    spinner.setAdapter(adapter);
                }

                private void showDatePicker(DatePickerCallback callback) {
                    Calendar calendar = Calendar.getInstance();
                    DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                            (view, year, month, dayOfMonth) -> {
                                String date = String.format(Locale.getDefault(), "%d-%02d-%02d",
                                        year, month + 1, dayOfMonth);
                                callback.onDateSelected(date);
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH));
                    datePickerDialog.show();
                }

                private interface DatePickerCallback {
                    void onDateSelected(String date);
                }

                private void updateFilterIndicator() {
                    if (filterIndicator == null) return;

                    int activeFilters = currentFilters.getActiveFilterCount();
                    if (!searchQuery.isEmpty()) activeFilters++;

                    if (activeFilters > 0) {
                        filterIndicator.setVisibility(View.VISIBLE);
                        filterIndicator.setText(activeFilters + " filter" + (activeFilters > 1 ? "s" : "") + " active");
                        filterButton.setText("Filters (" + activeFilters + ")");
                    } else {
                        filterIndicator.setVisibility(View.GONE);
                        filterButton.setText("Filters");
                    }
                }

                private void showActiveFilters() {
                    if (activeFiltersChipGroup == null) return;

                    activeFiltersChipGroup.removeAllViews();

                    // Add chips for each active filter
                    if (!currentFilters.status.equals("all")) {
                        addFilterChip("Status: " + currentFilters.status, () -> {
                            currentFilters.status = "all";
                            applyFilters();
                            updateFilterIndicator();
                            showActiveFilters();
                        });
                    }

                    if (!currentFilters.priority.equals("all")) {
                        addFilterChip("Priority: " + currentFilters.priority, () -> {
                            currentFilters.priority = "all";
                            applyFilters();
                            updateFilterIndicator();
                            showActiveFilters();
                        });
                    }

                    // REMOVE THIS ENTIRE BLOCK:
    /*
    if (!currentFilters.campus.equals("all")) {
        addFilterChip("Campus: " + currentFilters.campus, () -> {
            currentFilters.campus = "all";
            applyFilters();
            updateFilterIndicator();
            showActiveFilters();
        });
    }
    */

                    if (!currentFilters.location.equals("all")) {
                        addFilterChip("Location: " + currentFilters.location, () -> {
                            currentFilters.location = "all";
                            applyFilters();
                            updateFilterIndicator();
                            showActiveFilters();
                        });
                    }

                    if (!currentFilters.reportedBy.equals("all")) {
                        addFilterChip("User: " + currentFilters.reportedBy, () -> {
                            currentFilters.reportedBy = "all";
                            applyFilters();
                            updateFilterIndicator();
                            showActiveFilters();
                        });
                    }

                    // Rest of the method stays the same...
                    if (currentFilters.showOnlyWithPhotos) {
                        addFilterChip("Has Photo", () -> {
                            currentFilters.showOnlyWithPhotos = false;
                            applyFilters();
                            updateFilterIndicator();
                            showActiveFilters();
                        });
                    }

                    if (currentFilters.showOnlyScheduled) {
                        addFilterChip("Scheduled", () -> {
                            currentFilters.showOnlyScheduled = false;
                            applyFilters();
                            updateFilterIndicator();
                            showActiveFilters();
                        });
                    }

                    if (currentFilters.showCampusesWithProblems) {
                        addFilterChip("Problem Campuses", () -> {
                            currentFilters.showCampusesWithProblems = false;
                            applyFilters();
                            updateFilterIndicator();
                            showActiveFilters();
                        });
                    }

                    if (!currentFilters.dateFrom.isEmpty()) {
                        addFilterChip("From: " + currentFilters.dateFrom, () -> {
                            currentFilters.dateFrom = "";
                            applyFilters();
                            updateFilterIndicator();
                            showActiveFilters();
                        });
                    }

                    if (!currentFilters.dateTo.isEmpty()) {
                        addFilterChip("To: " + currentFilters.dateTo, () -> {
                            currentFilters.dateTo = "";
                            applyFilters();
                            updateFilterIndicator();
                            showActiveFilters();
                        });
                    }
                }

                private void addFilterChip(String text, Runnable onClose) {
                    if (activeFiltersChipGroup == null) return;

                    Chip chip = new Chip(this);
                    chip.setText(text);
                    chip.setCloseIconVisible(true);
                    chip.setOnCloseIconClickListener(v -> {
                        onClose.run();
                        activeFiltersChipGroup.removeView(chip);
                        showActiveFilters();
                    });
                    activeFiltersChipGroup.addView(chip);
                }

                private void updateStatusChipsFromFilter() {
                    for (int i = 0; i < statusChipGroup.getChildCount(); i++) {
                        Chip chip = (Chip) statusChipGroup.getChildAt(i);
                        String chipValue = chip.getText().toString().toLowerCase();
                        if (chipValue.equals("in progress")) chipValue = "in progress";
                        chip.setChecked(chipValue.equals(currentFilters.status));
                    }
                }

                private void loadCampusData() {
                    mDatabase.child("campuses").addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            campusList.clear();

                            for (DataSnapshot campusSnapshot : snapshot.getChildren()) {
                                String campusName = campusSnapshot.getValue(String.class);
                                if (campusName != null && !campusName.trim().isEmpty()) {
                                    campusList.add(campusName);
                                }
                            }

                            campusList.sort(String::compareToIgnoreCase);
                            calculateCampusIssues();
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.w(TAG, "Failed to load campuses", error.toException());
                        }
                    });
                }

                private void calculateCampusIssues() {
                    campusIssueCount.clear();

                    for (MaintenanceReport report : allReports) {
                        if (report.campus != null &&
                                (report.status.equals("pending") ||
                                        report.status.equals("in progress") ||
                                        report.status.equals("scheduled") ||
                                        report.status.equals("partially completed"))) {
                            campusIssueCount.put(report.campus,
                                    campusIssueCount.getOrDefault(report.campus, 0) + 1);
                        }
                    }
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

                                            // Existing fields...
                                            report.reportId = reportSnapshot.getKey();
                                            report.title = reportSnapshot.child("title").getValue(String.class);
                                            report.description = reportSnapshot.child("description").getValue(String.class);
                                            report.location = reportSnapshot.child("location").getValue(String.class);
                                            report.campus = reportSnapshot.child("campus").getValue(String.class);
                                            report.department = reportSnapshot.child("department").getValue(String.class);
                                            report.priority = reportSnapshot.child("priority").getValue(String.class);
                                            report.status = reportSnapshot.child("status").getValue(String.class);
                                            report.reportedBy = reportSnapshot.child("reportedBy").getValue(String.class);
                                            report.photoUrl = reportSnapshot.child("photoBase64").getValue(String.class);
                                            report.rejectionReason = reportSnapshot.child("rejectionReason").getValue(String.class);
                                            report.rejectedBy = reportSnapshot.child("rejectedBy").getValue(String.class);
                                            report.completionPhotoBase64 = reportSnapshot.child("completionPhotoBase64").getValue(String.class);
                                            report.scheduledDate = reportSnapshot.child("scheduledDate").getValue(String.class);
                                            report.scheduledTime = reportSnapshot.child("scheduledTime").getValue(String.class);
                                            report.assignedTo = reportSnapshot.child("assignedTo").getValue(String.class);
                                            report.completedAtFormatted = reportSnapshot.child("completedAtFormatted").getValue(String.class);
                                            report.abortReason = reportSnapshot.child("abortReason").getValue(String.class);
                                            report.completedBy = reportSnapshot.child("completedBy").getValue(String.class);
                                            report.partialCompletionNotes = reportSnapshot.child("partialCompletionNotes").getValue(String.class);

                                            // NEW: Load full names instead of emails
                                            report.reportedByName = reportSnapshot.child("reportedByName").getValue(String.class);
                                            report.assignedToName = reportSnapshot.child("assignedToName").getValue(String.class);
                                            report.scheduledByName = reportSnapshot.child("scheduledByName").getValue(String.class);
                                            report.completedByName = reportSnapshot.child("completedByName").getValue(String.class);
                                            report.rejectedByName = reportSnapshot.child("rejectedByName").getValue(String.class);

                                            // Long values...
                                            Long rejectedAt = reportSnapshot.child("rejectedAt").getValue(Long.class);
                                            report.rejectedAt = rejectedAt != null ? rejectedAt : 0L;

                                            report.assignedAt = reportSnapshot.child("assignedAt").getValue(Long.class) != null ?
                                                    reportSnapshot.child("assignedAt").getValue(Long.class) : 0L;

                                            report.startedAt = reportSnapshot.child("startedAt").getValue(Long.class) != null ?
                                                    reportSnapshot.child("startedAt").getValue(Long.class) : 0L;

                                            report.completedAt = reportSnapshot.child("completedAt").getValue(Long.class) != null ?
                                                    reportSnapshot.child("completedAt").getValue(Long.class) : 0L;

                                            report.abortedAt = reportSnapshot.child("abortedAt").getValue(Long.class) != null ?
                                                    reportSnapshot.child("abortedAt").getValue(Long.class) : 0L;

                                            Long createdAt = reportSnapshot.child("createdAt").getValue(Long.class);
                                            report.createdAt = createdAt != null ? createdAt : 0L;

                                            // Set defaults
                                            if (report.title == null) report.title = "Untitled Report";
                                            if (report.status == null) report.status = "pending";
                                            if (report.priority == null) report.priority = "medium";

                                            allReports.add(report);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error parsing report", e);
                                        }
                                    }

                                    allReports.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                                    calculateCampusIssues();
                                    applyFilters();
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

                    for (MaintenanceReport report : allReports) {
                        // Check status filter
                        if (!currentFilters.status.equals("all")) {
                            if (currentFilters.status.equals("active")) {
                                if (!report.status.equalsIgnoreCase("pending") &&
                                        !report.status.equalsIgnoreCase("scheduled") &&
                                        !report.status.equalsIgnoreCase("in progress") &&
                                        !report.status.equalsIgnoreCase("partially completed")) {
                                    continue;
                                }
                            } else if (!report.status.equalsIgnoreCase(currentFilters.status)) {
                                continue;
                            }
                        }

                        // Check priority filter
                        if (!currentFilters.priority.equals("all") &&
                                !report.priority.equalsIgnoreCase(currentFilters.priority)) {
                            continue;
                        }

                        // REMOVE THIS ENTIRE CAMPUS FILTER BLOCK:
        /*
        if (!currentFilters.campus.equals("all")) {
            String cleanCampus = currentFilters.campus.split(" \\(")[0];
            if (!report.campus.equalsIgnoreCase(cleanCampus)) {
                continue;
            }
        }
        */

                        // Check department filter
                        if (!currentFilters.department.equals("all") &&
                                (report.department == null || !report.department.equalsIgnoreCase(currentFilters.department))) {
                            continue;
                        }

                        // Check location filter
                        if (!currentFilters.location.equals("all") &&
                                (report.location == null || !report.location.equalsIgnoreCase(currentFilters.location))) {
                            continue;
                        }

                        // Check user filter
                        if (!currentFilters.reportedBy.equals("all")) {
                            String userName = report.reportedByName != null ? report.reportedByName : report.reportedBy;
                            if (userName == null || !userName.equals(currentFilters.reportedBy)) {
                                continue;
                            }
                        }

                        // Check photo filter
                        if (currentFilters.showOnlyWithPhotos &&
                                (report.photoUrl == null || report.photoUrl.isEmpty())) {
                            continue;
                        }

                        // Check scheduled filter
                        if (currentFilters.showOnlyScheduled &&
                                (report.scheduledDate == null || report.scheduledDate.isEmpty())) {
                            continue;
                        }

                        // Check problem campus filter
                        if (currentFilters.showCampusesWithProblems) {
                            Integer issueCount = campusIssueCount.get(report.campus);
                            if (issueCount == null || issueCount == 0) {
                                continue;
                            }
                        }

                        // Check date range
                        if (!currentFilters.dateFrom.isEmpty() || !currentFilters.dateTo.isEmpty()) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            try {
                                Date reportDate = new Date(report.createdAt);

                                if (!currentFilters.dateFrom.isEmpty()) {
                                    Date fromDate = sdf.parse(currentFilters.dateFrom);
                                    if (fromDate != null && reportDate.before(fromDate)) continue;
                                }

                                if (!currentFilters.dateTo.isEmpty()) {
                                    Date toDate = sdf.parse(currentFilters.dateTo);
                                    if (toDate != null) {
                                        Calendar cal = Calendar.getInstance();
                                        cal.setTime(toDate);
                                        cal.set(Calendar.HOUR_OF_DAY, 23);
                                        cal.set(Calendar.MINUTE, 59);
                                        cal.set(Calendar.SECOND, 59);
                                        toDate = cal.getTime();

                                        if (reportDate.after(toDate)) continue;
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Date parsing error", e);
                            }
                        }

                        // Check search query
                        if (!searchQuery.isEmpty()) {
                            String query = searchQuery.toLowerCase();
                            boolean matches = (report.title != null && report.title.toLowerCase().contains(query)) ||
                                    (report.description != null && report.description.toLowerCase().contains(query)) ||
                                    (report.location != null && report.location.toLowerCase().contains(query)) ||
                                    (report.reportedBy != null && report.reportedBy.toLowerCase().contains(query));
                            if (!matches) continue;
                        }

                        filteredReports.add(report);
                    }

                    reportAdapter.notifyDataSetChanged();
                    updateEmptyState();
                }

                private void updateEmptyState() {
                    if (filteredReports.isEmpty()) {
                        emptyStateText.setVisibility(View.VISIBLE);
                        reportsRecyclerView.setVisibility(View.GONE);

                        if (currentFilters.hasActiveFilters() || !searchQuery.isEmpty()) {
                            emptyStateText.setText("No reports match your filters.\nTry adjusting your search or filters.");
                        } else {
                            emptyStateText.setText("No reports found.\nReports will appear here once created.");
                        }
                    } else {
                        emptyStateText.setVisibility(View.GONE);
                        reportsRecyclerView.setVisibility(View.VISIBLE);
                    }
                }

                private void showToast(String message) {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                }

                public static class MaintenanceReport {
                    public String reportId;
                    public String title;
                    public String description;
                    public String location;
                    public String campus;
                    public String department;
                    public String priority;
                    public String status;
                    public String reportedBy;
                    public long assignedAt;
                    public long startedAt;
                    public long completedAt;
                    public long abortedAt;
                    public String abortReason;
                    public String reportedByEmail;
                    public String reportedByUid;
                    public String photoUrl;
                    public String completionPhotoBase64;
                    public String scheduledDate;
                    public String scheduledTime;
                    public String assignedTo;
                    public long createdAt;
                    public String completedAtFormatted;
                    public String rejectionReason;
                    public String rejectedBy;
                    public long rejectedAt;
                    public String completedBy;  // NEW - Who completed the report
                    public String partialCompletionNotes;  // NEW - Notes for partial completion
                    public String reportedByName;      // Full name of reporter
                    public String assignedToName;      // Full name of assigned technician
                    public String scheduledByName;     // Full name of scheduler
                    public String completedByName;     // Full name of completer
                    public String rejectedByName;      // Full name of rejector



                    public MaintenanceReport() {
                        // Default constructor required for Firebase
                    }
                }

                // Report Adapter
                private class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {
                    private List<MaintenanceReport> reports;
                    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

                    public ReportAdapter(List<MaintenanceReport> reports) {
                        this.reports = reports;
                    }

                    @NonNull
                    @Override
                    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                        View view = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_maintenance_report, parent, false);
                        return new ViewHolder(view);
                    }

                    @Override
                    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                        MaintenanceReport report = reports.get(position);

                        holder.titleText.setText(report.title != null ? report.title : "No Title");
                        holder.descriptionText.setText(report.description != null ? report.description : "No Description");

                        // Show campus with issue indicator if it has problems
                        String locationInfo = (report.location != null ? report.location : "Unknown");
                        String campusInfo = report.campus != null ? report.campus : "Unknown Campus";
                        Integer issueCount = campusIssueCount.get(report.campus);
                        if (issueCount != null && issueCount > 0) {
                            campusInfo += " ⚠️ (" + issueCount + " active)";
                        }
                        holder.locationText.setText(locationInfo + " • " + campusInfo);

                        holder.dateText.setText(dateFormat.format(new Date(report.createdAt)));
                        holder.reportedByText.setText("Reported by: " +
                                (report.reportedBy != null ? report.reportedBy : "Unknown"));

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

                        // Show indicators
                        if (report.scheduledDate != null && !report.scheduledDate.isEmpty()) {
                            holder.scheduleIndicator.setVisibility(View.VISIBLE);
                            String scheduleText = "📅 " + report.scheduledDate;
                            holder.scheduleIndicator.setText(scheduleText);
                        } else {
                            holder.scheduleIndicator.setVisibility(View.GONE);
                        }

                        // In the ReportAdapter's onBindViewHolder method:
                        if (report.photoUrl != null && !report.photoUrl.isEmpty()) {
                            holder.photoIndicator.setVisibility(View.VISIBLE);

                            // Show different indicator if completion photo is also available
                            if (report.completionPhotoBase64 != null && !report.completionPhotoBase64.isEmpty()) {
                                holder.photoIndicator.setText("📷📷"); // Two camera icons for both photos
                            } else {
                                holder.photoIndicator.setText("📷"); // One camera icon for report photo only
                            }
                        } else if (report.completionPhotoBase64 != null && !report.completionPhotoBase64.isEmpty()) {
                            holder.photoIndicator.setVisibility(View.VISIBLE);
                            holder.photoIndicator.setText("✅📷"); // Checkmark + camera for completion photo only
                        } else {
                            holder.photoIndicator.setVisibility(View.GONE);
                        }

                        // Highlight if campus has multiple issues
                        if (issueCount != null && issueCount > 3) {
                            holder.itemView.setBackgroundColor(getResources().getColor(R.color.warning_background));
                        } else {
                            holder.itemView.setBackgroundColor(getResources().getColor(android.R.color.white));
                        }

                        // Click listener
                        holder.itemView.setOnClickListener(v -> showReportDetails(report));
                    }

                    @Override
                    public int getItemCount() {
                        return reports.size();
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
                        switch (status.toLowerCase().replace(" ", "_")) {
                            case "completed":
                                chip.setChipBackgroundColorResource(R.color.status_completed);
                                break;
                            case "partially_completed":
                            case "partially completed":
                                chip.setChipBackgroundColorResource(R.color.status_partially_completed);
                                break;
                            case "in_progress":
                            case "in progress":
                                chip.setChipBackgroundColorResource(R.color.status_in_progress);
                                break;
                            case "scheduled":
                                chip.setChipBackgroundColorResource(R.color.status_scheduled);
                                break;
                            case "rejected":
                                chip.setChipBackgroundColorResource(R.color.status_rejected);
                                break;
                            case "pending":
                            default:
                                chip.setChipBackgroundColorResource(R.color.status_pending);
                                break;
                        }
                    }
                    class ViewHolder extends RecyclerView.ViewHolder {
                        TextView titleText, descriptionText, locationText, dateText, reportedByText;
                        TextView scheduleIndicator, photoIndicator;
                        Chip priorityChip, statusChip;

                        ViewHolder(View itemView) {
                            super(itemView);
                            titleText = itemView.findViewById(R.id.titleText);
                            descriptionText = itemView.findViewById(R.id.descriptionText);
                            locationText = itemView.findViewById(R.id.locationText);
                            dateText = itemView.findViewById(R.id.dateText);
                            reportedByText = itemView.findViewById(R.id.reportedByText);
                            priorityChip = itemView.findViewById(R.id.priorityChip);
                            statusChip = itemView.findViewById(R.id.statusChip);
                            scheduleIndicator = itemView.findViewById(R.id.scheduleIndicator);
                            photoIndicator = itemView.findViewById(R.id.photoIndicator);
                        }
                    }
                }

                private void showReportDetails(MaintenanceReport report) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);

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
                    String reporterName = report.reportedByName != null ? report.reportedByName :
                            (report.reportedBy != null ? report.reportedBy : "Unknown");
                    layout.addView(createHistoryItem("👤 Reported By", reporterName));
                    layout.addView(createHistoryDivider());

                    // Location
                    layout.addView(createHistoryItem("📍 Location",
                            report.location != null ? report.location : "Unknown Location"));
                    layout.addView(createHistoryDivider());

                    // Campus
                    String campusInfo = report.campus != null ? report.campus : "Unknown Campus";
                    Integer issueCount = campusIssueCount.get(report.campus);
                    if (issueCount != null && issueCount > 0) {
                        campusInfo += " ⚠️ (" + issueCount + " active issues)";
                    }
                    layout.addView(createHistoryItem("🏫 Campus", campusInfo));
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

                        if (report.scheduledByName != null && !report.scheduledByName.isEmpty()) {
                            layout.addView(createHistorySubItem("👤 By", report.scheduledByName));
                        }
                        layout.addView(createHistoryDivider());
                    }

                    // Assigned To
                    if (report.assignedTo != null && !report.assignedTo.isEmpty()) {
                        String assignedToName = report.assignedToName != null ? report.assignedToName : report.assignedTo;
                        layout.addView(createHistoryItem("👷 Assigned To", assignedToName));
                        layout.addView(createHistoryDivider());
                    }

                    // Current Status
                    String currentStatus = report.status != null ? report.status.toUpperCase() : "PENDING";
                    String statusEmoji = getStatusEmoji(currentStatus);
                    layout.addView(createHistoryItem(statusEmoji + " Current Status", currentStatus));
                    layout.addView(createHistoryDivider());

                    // Partially Completed
                    if ("partially completed".equalsIgnoreCase(report.status) && report.completedAt > 0) {
                        layout.addView(createHistoryItem("⚠️ Partially Completed",
                                dateFormat.format(new Date(report.completedAt))));

                        if (report.completedByName != null && !report.completedByName.isEmpty()) {
                            layout.addView(createHistorySubItem("👤 By", report.completedByName));
                        }

                        if (report.partialCompletionNotes != null && !report.partialCompletionNotes.isEmpty()) {
                            layout.addView(createHistorySubItem("📝 Notes", report.partialCompletionNotes));
                        }

                        long duration = report.completedAt - report.createdAt;
                        String durationStr = formatDuration(duration);
                        layout.addView(createHistorySubItem("⏱️ Duration", durationStr));
                        layout.addView(createHistoryDivider());
                    }

                    // Completed
                    if ("completed".equalsIgnoreCase(report.status) && report.completedAt > 0) {
                        layout.addView(createHistoryItem("✅ Completed",
                                dateFormat.format(new Date(report.completedAt))));

                        if (report.completedByName != null && !report.completedByName.isEmpty()) {
                            layout.addView(createHistorySubItem("👤 By", report.completedByName));
                        }

                        long duration = report.completedAt - report.createdAt;
                        String durationStr = formatDuration(duration);
                        layout.addView(createHistorySubItem("⏱️ Duration", durationStr));
                        layout.addView(createHistoryDivider());
                    }

                    // Rejected
                    if ("rejected".equalsIgnoreCase(report.status) && report.rejectedAt > 0) {
                        layout.addView(createHistoryItem("❌ Rejected",
                                dateFormat.format(new Date(report.rejectedAt))));

                        if (report.rejectedByName != null && !report.rejectedByName.isEmpty()) {
                            layout.addView(createHistorySubItem("👤 By", report.rejectedByName));
                        }

                        if (report.rejectionReason != null && !report.rejectionReason.isEmpty()) {
                            layout.addView(createHistorySubItem("📝 Reason", report.rejectionReason));
                        }
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

                    if (report.photoUrl != null && !report.photoUrl.isEmpty()) {
                        layout.addView(createHistorySubItem("📷", "Report Photo Available"));
                    }
                    if (report.completionPhotoBase64 != null && !report.completionPhotoBase64.isEmpty()) {
                        layout.addView(createHistorySubItem("✅📷", "Completion Photo Available"));
                    }
                    if ((report.photoUrl == null || report.photoUrl.isEmpty()) &&
                            (report.completionPhotoBase64 == null || report.completionPhotoBase64.isEmpty())) {
                        layout.addView(createHistorySubItem("❌", "No photos attached"));
                    }

                    scrollView.addView(layout);
                    builder.setView(scrollView);
                    builder.setTitle("Report Details");

                    // Build options
                    List<String> options = new ArrayList<>();
                    options.add("💬 Open Chat");
                    options.add("🔄 Update Status");
                    options.add("⚠️ Update Priority");  // NEW: Priority update option

                    if (report.photoUrl != null && !report.photoUrl.isEmpty()) {
                        options.add("📷 View Report Photo");
                    }
                    if (report.completionPhotoBase64 != null && !report.completionPhotoBase64.isEmpty()) {
                        options.add("✅ View Completion Photo");
                    }

                    options.add("📋 View Action History");
                    options.add("📅 Set Schedule");
                    options.add("🗑️ Delete Report");
                    options.add("❌ Close");

                    builder.setItems(options.toArray(new String[0]), (dialog, which) -> {
                        String selected = options.get(which);

                        if (selected.contains("Open Chat")) {
                            ChatHelper.startReportChat(this, report.reportId);
                        } else if (selected.contains("Update Status")) {
                            showUpdateStatusDialog(report);
                        } else if (selected.contains("Update Priority")) {  // NEW: Priority update handler
                            showUpdatePriorityDialog(report);
                        } else if (selected.contains("View Report Photo")) {
                            viewFullPhoto(report.photoUrl);
                        } else if (selected.contains("View Completion Photo")) {
                            viewFullPhoto(report.completionPhotoBase64);
                        } else if (selected.contains("View Action History")) {
                            showActionHistoryDialog(report);
                        } else if (selected.contains("Set Schedule")) {
                            showScheduleDialog(report);
                        } else if (selected.contains("Delete Report")) {
                            confirmDeleteReport(report);
                        } else if (selected.contains("Close")) {
                            dialog.dismiss();
                        }
                    });

                    builder.show();
                }

                // Replace your showUpdatePriorityDialog method with this fixed version WITH SPINNER:

                private void showUpdatePriorityDialog(MaintenanceReport report) {
                    String currentPriority = report.priority != null ? report.priority.toLowerCase() : "medium";

                    String[] allPriorities = {"Low", "Medium", "High", "Critical"};
                    List<String> availablePriorities = new ArrayList<>();
                    availablePriorities.add("-- Select Priority --"); // Default prompt

                    // Add all priorities except current one
                    for (String priority : allPriorities) {
                        if (!priority.toLowerCase().equals(currentPriority)) {
                            availablePriorities.add(priority);
                        }
                    }

                    if (availablePriorities.size() <= 1) { // Only has the prompt
                        showToast("No priority changes available");
                        return;
                    }

                    // Create custom dialog view with Spinner
                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(50, 40, 50, 10);

                    // Current priority text
                    TextView currentPriorityText = new TextView(this);
                    currentPriorityText.setText("Current Priority: " + currentPriority.toUpperCase());
                    currentPriorityText.setTextSize(16);
                    currentPriorityText.setTypeface(null, android.graphics.Typeface.BOLD);
                    currentPriorityText.setPadding(0, 0, 0, 20);
                    layout.addView(currentPriorityText);

                    // Instruction text
                    TextView instructionText = new TextView(this);
                    instructionText.setText("Select new priority level:");
                    instructionText.setPadding(0, 0, 0, 10);
                    layout.addView(instructionText);

                    // Create Spinner
                    Spinner prioritySpinner = new Spinner(this);
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, availablePriorities);
                    prioritySpinner.setAdapter(adapter);
                    layout.addView(prioritySpinner);

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Update Priority Level");
                    builder.setView(layout);

                    builder.setPositiveButton("Update", (dialog, which) -> {
                        int selectedPosition = prioritySpinner.getSelectedItemPosition();

                        if (selectedPosition == 0) { // Selected the prompt
                            showToast("Please select a priority level");
                            return;
                        }

                        String newPriority = prioritySpinner.getSelectedItem().toString().toLowerCase();

                        // Show warning for critical priority
                        if ("critical".equals(newPriority)) {
                            showCriticalPriorityConfirmation(report, newPriority);
                        } else if ("high".equals(newPriority)) {
                            showHighPriorityConfirmation(report, newPriority);
                        } else {
                            updateReportPriority(report, newPriority);
                        }
                    });

                    builder.setNegativeButton("Cancel", null);
                    builder.show();
                }

// ============================================
// 8. ADD PRIORITY CONFIRMATION DIALOGS
// ============================================

                private void showCriticalPriorityConfirmation(MaintenanceReport report, String newPriority) {
                    new AlertDialog.Builder(this)
                            .setTitle("⚠️ Confirm CRITICAL Priority")
                            .setMessage("You are updating this report to CRITICAL priority.\n\n" +
                                    "CRITICAL priority should ONLY be used for:\n" +
                                    "• Immediate safety hazards\n" +
                                    "• Fire/electrical emergencies\n" +
                                    "• Severe water leaks/flooding\n" +
                                    "• Complete system failures\n\n" +
                                    "Is this issue truly CRITICAL?")
                            .setPositiveButton("Yes, Set to CRITICAL", (dialog, which) -> {
                                updateReportPriority(report, newPriority);
                            })
                            .setNegativeButton("Cancel", null)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }

                private void showHighPriorityConfirmation(MaintenanceReport report, String newPriority) {
                    new AlertDialog.Builder(this)
                            .setTitle("Confirm HIGH Priority")
                            .setMessage("You are updating this report to HIGH priority.\n\n" +
                                    "HIGH priority should be used for:\n" +
                                    "• Issues affecting operations\n" +
                                    "• Problems impacting multiple users\n" +
                                    "• Urgent but not emergency situations\n\n" +
                                    "Is this the correct priority level?")
                            .setPositiveButton("Yes, Set to HIGH", (dialog, which) -> {
                                updateReportPriority(report, newPriority);
                            })
                            .setNegativeButton("Cancel", null)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }

// ============================================
// 9. ADD updateReportPriority() METHOD
// ============================================

                private void updateReportPriority(MaintenanceReport report, String newPriority) {
                    String oldPriority = report.priority != null ? report.priority : "medium";

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("priority", newPriority);
                    updates.put("priorityUpdatedAt", System.currentTimeMillis());

                    if (mAuth.getCurrentUser() != null) {
                        updates.put("priorityUpdatedBy", mAuth.getCurrentUser().getEmail());

                        // Get and save updater's full name
                        String currentUid = mAuth.getCurrentUser().getUid();
                        mDatabase.child("users").child(currentUid).child("displayName")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        String updaterName = snapshot.getValue(String.class);
                                        if (updaterName != null) {
                                            mDatabase.child("maintenance_reports").child(report.reportId)
                                                    .child("priorityUpdatedByName").setValue(updaterName);
                                        }
                                    }
                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {}
                                });
                    }

                    showToast("Updating priority level...");

                    mDatabase.child("maintenance_reports").child(report.reportId).updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                showToast("Priority updated successfully: " + oldPriority.toUpperCase() +
                                        " → " + newPriority.toUpperCase());

                                // Send system message to chat
                                String chatId = ChatHelper.generateReportChatId(report.reportId);
                                String message = "Priority level updated: " + oldPriority.toUpperCase() +
                                        " → " + newPriority.toUpperCase();

                                // Add context based on new priority
                                switch (newPriority.toLowerCase()) {
                                    case "critical":
                                        message += "\n\n⚠️ This report is now marked as CRITICAL and requires immediate attention!";
                                        // Send notification to admins about critical priority change
                                        NotificationHelper.notifyCriticalReport(report.reportId, report.title, report.location);
                                        break;
                                    case "high":
                                        message += "\n\n⚠️ This report has been escalated to HIGH priority.";
                                        break;
                                    case "low":
                                        message += "\n\nℹ️ This report has been lowered to LOW priority.";
                                        break;
                                    case "medium":
                                        message += "\n\nℹ️ Priority set to MEDIUM (normal processing).";
                                        break;
                                }

                                ChatHelper.sendSystemMessage(chatId, message, report.reportId);

                                // Reload reports to reflect changes
                                loadReports();
                            })
                            .addOnFailureListener(e -> {
                                showToast("Failed to update priority: " + e.getMessage());
                                Log.e(TAG, "Failed to update priority", e);
                            });
                }


                private void showActionHistoryDialog(MaintenanceReport report) {
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


                private String buildReportDetailsString(MaintenanceReport report) {
                    StringBuilder details = new StringBuilder();
                    details.append("Description: ").append(report.description).append("\n\n");
                    details.append("Location: ").append(report.location).append("\n");
                    details.append("Campus: ").append(report.campus);

                    Integer issueCount = campusIssueCount.get(report.campus);
                    if (issueCount != null && issueCount > 0) {
                        details.append(" (").append(issueCount).append(" active issues in campus)");
                    }

                    details.append("\nPriority: ").append(report.priority);
                    details.append("\nStatus: ").append(report.status);

                    if ("rejected".equalsIgnoreCase(report.status) && report.rejectionReason != null) {
                        details.append("\n\n⚠️ REJECTION REASON:\n").append(report.rejectionReason);
                        if (report.rejectedBy != null) {
                            details.append("\nRejected by: ").append(report.rejectedBy);
                        }
                    }

                    details.append("\nReported by: ").append(report.reportedBy);
                    details.append("\nCreated: ").append(new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                            .format(new Date(report.createdAt)));

                    // Show scheduled date and time
                    if (report.scheduledDate != null && !report.scheduledDate.isEmpty()) {
                        details.append("\n\nScheduled: ").append(report.scheduledDate);
                        if (report.scheduledTime != null && !report.scheduledTime.isEmpty()) {
                            details.append(" at ").append(report.scheduledTime);
                        }
                    }

                    if (report.assignedTo != null && !report.assignedTo.isEmpty()) {
                        details.append("\nAssigned to: ").append(report.assignedTo);
                    }

                    // Add completion info
                    if (("completed".equalsIgnoreCase(report.status) || "partially completed".equalsIgnoreCase(report.status))
                            && report.completedAt > 0) {
                        details.append("\n\nCompleted: ").append(new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                                .format(new Date(report.completedAt)));

                        if (report.completedBy != null && !report.completedBy.isEmpty()) {
                            details.append("\nCompleted by: ").append(report.completedBy);
                        }
                    }

                    // Show partial completion notes
                    if ("partially completed".equalsIgnoreCase(report.status) &&
                            report.partialCompletionNotes != null && !report.partialCompletionNotes.isEmpty()) {
                        details.append("\n\nPartial Completion Notes:\n").append(report.partialCompletionNotes);
                    }

                    // Add photo availability info
                    details.append("\n\nAttachments:");
                    if (report.photoUrl != null && !report.photoUrl.isEmpty()) {
                        details.append("\n- Report Photo Available");
                    }
                    if (report.completionPhotoBase64 != null && !report.completionPhotoBase64.isEmpty()) {
                        details.append("\n- Completion Photo Available");
                    }
                    if ((report.photoUrl == null || report.photoUrl.isEmpty()) &&
                            (report.completionPhotoBase64 == null || report.completionPhotoBase64.isEmpty())) {
                        details.append("\n- No photos attached");
                    }

                    return details.toString();
                }

                private void showAssignTechnicianDialog(MaintenanceReport report) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_assign_technician, null);

                    // If custom layout doesn't exist, create simple inputs
                    if (dialogView == null) {
                        dialogView = createSimpleTechnicianView();
                    }

                    builder.setView(dialogView);

                    final EditText technicianNameInput = dialogView.findViewById(R.id.technicianNameInput);
                    final EditText technicianEmailInput = dialogView.findViewById(R.id.technicianEmailInput);
                    final EditText technicianPhoneInput = dialogView.findViewById(R.id.technicianPhoneInput);

                    if (report.assignedTo != null && technicianNameInput != null) {
                        technicianNameInput.setText(report.assignedTo);
                    }

                    builder.setTitle("Assign Technician");
                    builder.setMessage("Name and phone number are required fields.");
                    builder.setPositiveButton("Assign", (dialog, which) -> {
                        String name = technicianNameInput != null ? technicianNameInput.getText().toString().trim() : "";
                        String email = technicianEmailInput != null ? technicianEmailInput.getText().toString().trim() : "";
                        String phone = technicianPhoneInput != null ? technicianPhoneInput.getText().toString().trim() : "";

                        assignTechnician(report, name, email, phone);
                    });
                    builder.setNegativeButton("Cancel", null);

                    builder.show();
                }

                private boolean isValidPhoneNumber(String phone) {
                    // Remove any spaces, dashes, or parentheses
                    String cleanPhone = phone.replaceAll("[\\s\\-\\(\\)]", "");

                    // Check if it contains only digits and has reasonable length
                    return cleanPhone.matches("\\d{10,15}"); // 10-15 digits
                }

                private void assignTechnician(MaintenanceReport report, String name, String email, String phone) {
                    // Validate required fields
                    if (name.isEmpty()) {
                        showAssignTechnicianDialog(report);
                        showToast("Technician name is required");
                        return;
                    }

                    if (phone.isEmpty()) {
                        showAssignTechnicianDialog(report);
                        showToast("Phone number is required");
                        return;
                    }

                    // Optional: Add phone number format validation
                    if (!isValidPhoneNumber(phone)) {
                        showAssignTechnicianDialog(report);
                        showToast("Please enter a valid phone number");
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("assignedTo", name);
                    updates.put("technicianEmail", email);
                    updates.put("technicianPhone", phone);
                    updates.put("assignedAt", System.currentTimeMillis());

                    if (mAuth.getCurrentUser() != null) {
                        updates.put("assignedBy", mAuth.getCurrentUser().getEmail());
                    }

                    mDatabase.child("maintenance_reports").child(report.reportId).updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                showToast("Technician assigned successfully");
                                loadReports();
                            })
                            .addOnFailureListener(e -> showToast("Failed to assign technician: " + e.getMessage()));
                }

                private View createSimpleTechnicianView() {
                    // Create a simple linear layout with EditTexts as fallback
                    View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
                    // This is a fallback - the actual layout should be created
                    return view;
                }

                private void showReportActions(MaintenanceReport report) {
                    List<String> optionsList = new ArrayList<>();
                    optionsList.add("Chat about Report");
                    optionsList.add("Update Status");
                    optionsList.add("View Details");
                    optionsList.add("Export to PDF"); // NEW OPTION
                    optionsList.add("View History");



                    if (report.photoUrl != null && !report.photoUrl.isEmpty()) {
                        optionsList.add("View Report Photo");
                    }

                    if (report.completionPhotoBase64 != null && !report.completionPhotoBase64.isEmpty()) {
                        optionsList.add("View Completion Photo");
                    }

                    optionsList.add("Set Schedule");
                    optionsList.add("Delete Report");

                    String[] options = optionsList.toArray(new String[0]);

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Select Action");
                    builder.setItems(options, (dialog, which) -> {
                        String selectedOption = options[which];

                        switch (selectedOption) {
                            case "Chat about Report":
                                ChatHelper.startReportChat(this, report.reportId);
                                break;
                            case "Update Status":
                                showUpdateStatusDialog(report);
                                break;
                            case "View Details":
                                showReportDetails(report);
                                break;
                            case "Export to PDF": // NEW CASE
                                exportSingleReportToPDF(report);
                                break;
                            case "View History":
                                showReportHistoryDialog(report);
                                break;

                            case "View Report Photo":
                                viewFullPhoto(report.photoUrl);
                                break;
                            case "View Completion Photo":
                                viewFullPhoto(report.completionPhotoBase64);
                                break;
                            case "Set Schedule":
                                showScheduleDialog(report);
                                break;
                            case "Delete Report":
                                confirmDeleteReport(report);
                                break;
                        }
                    });
                    builder.show();
                }

                private void showReportHistoryDialog(MaintenanceReport report) {
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

                    // Load and display chat history for actions FIRST
                    loadReportChatHistory(report.reportId, layout, dateFormat);

                    // Original report info
                    layout.addView(createHistoryItem("📝 Report Created",
                            dateFormat.format(new Date(report.createdAt))));
                    layout.addView(createHistoryDivider());

                    String reporterName = report.reportedByName != null ? report.reportedByName :
                            (report.reportedBy != null ? report.reportedBy : "Unknown");
                    layout.addView(createHistoryItem("👤 Reported By", reporterName));
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

                        if (report.scheduledByName != null && !report.scheduledByName.isEmpty()) {
                            layout.addView(createHistorySubItem("👤 By", report.scheduledByName));
                        }
                        layout.addView(createHistoryDivider());
                    }

                    // Assigned To
                    if (report.assignedTo != null && !report.assignedTo.isEmpty()) {
                        String assignedToName = report.assignedToName != null ? report.assignedToName : report.assignedTo;
                        layout.addView(createHistoryItem("👷 Assigned To", assignedToName));
                        layout.addView(createHistoryDivider());
                    }

                    // Current Status
                    String currentStatus = report.status != null ? report.status.toUpperCase() : "PENDING";
                    String statusEmoji = getStatusEmoji(currentStatus);
                    layout.addView(createHistoryItem(statusEmoji + " Current Status", currentStatus));
                    layout.addView(createHistoryDivider());

                    // Partially Completed
                    if ("partially completed".equalsIgnoreCase(report.status) && report.completedAt > 0) {
                        layout.addView(createHistoryItem("⚠️ Partially Completed",
                                dateFormat.format(new Date(report.completedAt))));

                        if (report.completedByName != null && !report.completedByName.isEmpty()) {
                            layout.addView(createHistorySubItem("👤 By", report.completedByName));
                        }

                        if (report.partialCompletionNotes != null && !report.partialCompletionNotes.isEmpty()) {
                            layout.addView(createHistorySubItem("📝 Notes", report.partialCompletionNotes));
                        }

                        long duration = report.completedAt - report.createdAt;
                        String durationStr = formatDuration(duration);
                        layout.addView(createHistorySubItem("⏱️ Duration", durationStr));
                        layout.addView(createHistoryDivider());
                    }

                    // Completed
                    if ("completed".equalsIgnoreCase(report.status) && report.completedAt > 0) {
                        layout.addView(createHistoryItem("✅ Completed",
                                dateFormat.format(new Date(report.completedAt))));

                        if (report.completedByName != null && !report.completedByName.isEmpty()) {
                            layout.addView(createHistorySubItem("👤 By", report.completedByName));
                        }

                        long duration = report.completedAt - report.createdAt;
                        String durationStr = formatDuration(duration);
                        layout.addView(createHistorySubItem("⏱️ Duration", durationStr));
                        layout.addView(createHistoryDivider());
                    }

                    // Rejected
                    if ("rejected".equalsIgnoreCase(report.status) && report.rejectedAt > 0) {
                        layout.addView(createHistoryItem("❌ Rejected",
                                dateFormat.format(new Date(report.rejectedAt))));

                        if (report.rejectedByName != null && !report.rejectedByName.isEmpty()) {
                            layout.addView(createHistorySubItem("👤 By", report.rejectedByName));
                        }

                        if (report.rejectionReason != null && !report.rejectionReason.isEmpty()) {
                            layout.addView(createHistorySubItem("📝 Reason", report.rejectionReason));
                        }
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

                    if (report.photoUrl != null && !report.photoUrl.isEmpty()) {
                        layout.addView(createHistorySubItem("📷", "Report Photo Available"));
                    }
                    if (report.completionPhotoBase64 != null && !report.completionPhotoBase64.isEmpty()) {
                        layout.addView(createHistorySubItem("✅📷", "Completion Photo Available"));
                    }
                    if ((report.photoUrl == null || report.photoUrl.isEmpty()) &&
                            (report.completionPhotoBase64 == null || report.completionPhotoBase64.isEmpty())) {
                        layout.addView(createHistorySubItem("❌", "No photos attached"));
                    }

                    scrollView.addView(layout);
                    builder.setView(scrollView);
                    builder.setPositiveButton("Close", null);

                    AlertDialog dialog = builder.create();
                    dialog.show();
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
                    if (status == null) return "⏳";

                    switch (status.toLowerCase()) {
                        case "completed":
                            return "✅";
                        case "partially completed":
                            return "⚠️";
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



                // Add this method to load chat history with system messages
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
                                    TextView actionsHeader = new TextView(ReportManagementActivity.this);
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
                                            LinearLayout actionLayout = new LinearLayout(ReportManagementActivity.this);
                                            actionLayout.setOrientation(LinearLayout.VERTICAL);
                                            actionLayout.setPadding(12, 12, 12, 12);
                                            actionLayout.setBackgroundColor(getResources().getColor(android.R.color.background_light));

                                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.WRAP_CONTENT);
                                            params.setMargins(0, 4, 0, 4);
                                            actionLayout.setLayoutParams(params);

                                            TextView actionText = new TextView(ReportManagementActivity.this);
                                            actionText.setText(emoji + " " + message);
                                            actionText.setTextSize(13);
                                            actionText.setTextColor(getResources().getColor(android.R.color.black));
                                            actionLayout.addView(actionText);

                                            TextView timeText = new TextView(ReportManagementActivity.this);
                                            timeText.setText(dateFormat.format(new Date(timestamp)));
                                            timeText.setTextSize(11);
                                            timeText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                                            timeText.setPadding(0, 4, 0, 0);
                                            actionLayout.addView(timeText);

                                            layout.addView(actionLayout);
                                        }
                                    }

                                    if (!hasActions) {
                                        TextView noActionsText = new TextView(ReportManagementActivity.this);
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
                                    TextView errorText = new TextView(ReportManagementActivity.this);
                                    errorText.setText("⚠️ Could not load action history");
                                    errorText.setTextSize(12);
                                    errorText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                                    errorText.setPadding(8, 8, 8, 16);
                                    layout.addView(errorText);
                                    layout.addView(createHistoryDivider());
                                }
                            });
                }




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




                // Export single report
                private void exportSingleReportToPDF(MaintenanceReport report) {
                    List<MaintenanceReport> singleReport = new ArrayList<>();
                    singleReport.add(report);

                    if (checkWriteStoragePermission()) {
                        generatePDF(singleReport, "Single Report", report.title);
                    }
                }

                // Add Export button to toolbar or filter section
// Add this to your initializeViews() method:
                private void addExportButton() {
                    MaterialButton exportButton = findViewById(R.id.exportButton);
                    if (exportButton != null) {
                        exportButton.setOnClickListener(v -> showExportOptions());
                    }
                }

                private void showScheduleDialog(MaintenanceReport report) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_schedule_maintenance, null);

                    if (dialogView == null) {
                        dialogView = createSimpleScheduleView();
                    }

                    builder.setView(dialogView);

                    final TextView selectedDateText = dialogView.findViewById(R.id.selectedDateText);
                    final TextView selectedTimeText = dialogView.findViewById(R.id.selectedTimeText);
                    MaterialButton selectDateButton = dialogView.findViewById(R.id.selectDateButton);
                    MaterialButton selectTimeButton = dialogView.findViewById(R.id.selectTimeButton);
                    final EditText assignToInput = dialogView.findViewById(R.id.assignToInput);

                    final String[] selectedDate = {report.scheduledDate};
                    final String[] selectedTime = {report.scheduledTime};

                    // Pre-fill if schedule exists
                    if (selectedDate[0] != null && !selectedDate[0].isEmpty() && selectedDateText != null) {
                        selectedDateText.setText(selectedDate[0]);
                    }
                    if (selectedTime[0] != null && !selectedTime[0].isEmpty() && selectedTimeText != null) {
                        selectedTimeText.setText(selectedTime[0]);
                    }
                    if (report.assignedTo != null && assignToInput != null) {
                        assignToInput.setText(report.assignedTo);
                    }

                    if (selectDateButton != null) {
                        selectDateButton.setOnClickListener(v -> {
                            Calendar calendar = Calendar.getInstance();
                            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                                    (view, year, month, dayOfMonth) -> {
                                        selectedDate[0] = String.format(Locale.getDefault(), "%d-%02d-%02d",
                                                year, month + 1, dayOfMonth);
                                        if (selectedDateText != null) {
                                            selectedDateText.setText(selectedDate[0]);
                                        }
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH));

                            // REMOVED: datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
                            // Now allows past dates
                            datePickerDialog.show();
                        });
                    }

                    if (selectTimeButton != null) {
                        selectTimeButton.setOnClickListener(v -> {
                            Calendar calendar = Calendar.getInstance();
                            TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                                    (view, hourOfDay, minute) -> {
                                        // Convert to 12-hour format with AM/PM
                                        String amPm = hourOfDay >= 12 ? "PM" : "AM";
                                        int hour12 = hourOfDay % 12;
                                        if (hour12 == 0) hour12 = 12;

                                        selectedTime[0] = String.format(Locale.getDefault(), "%02d:%02d %s",
                                                hour12, minute, amPm);
                                        if (selectedTimeText != null) {
                                            selectedTimeText.setText(selectedTime[0]);
                                        }
                                    },
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                    false); // Changed to false for 12-hour format
                            timePickerDialog.show();
                        });
                    }

                    builder.setTitle("Schedule Maintenance");
                    builder.setPositiveButton("Schedule", (dialog, which) -> {
                        String assignedTo = assignToInput != null ? assignToInput.getText().toString().trim() : "";

                        if (selectedDate[0] == null || selectedDate[0].isEmpty()) {
                            showToast("Please select a date");
                            return;
                        }

                        if (selectedTime[0] == null || selectedTime[0].isEmpty()) {
                            showToast("Please select a time");
                            return;
                        }

                        updateReportSchedule(report, selectedDate[0], selectedTime[0], assignedTo);
                    });
                    builder.setNegativeButton("Cancel", null);

                    builder.show();
                }

                private View createSimpleScheduleView() {
                    // Create a simple linear layout with EditTexts as fallback
                    View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
                    // This is a fallback - the actual layout should be created
                    return view;
                }

                private void updateReportSchedule(MaintenanceReport report, String date, String time, String assignedTo) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("scheduledDate", date);
                    updates.put("scheduledTime", time);
                    updates.put("scheduledAt", System.currentTimeMillis());
                    updates.put("status", "scheduled");

                    if (!assignedTo.isEmpty()) {
                        updates.put("assignedTo", assignedTo);
                    }

                    if (mAuth.getCurrentUser() != null) {
                        updates.put("scheduledBy", mAuth.getCurrentUser().getEmail());

                        // NEW: Get and save scheduler's full name
                        String currentUid = mAuth.getCurrentUser().getUid();
                        mDatabase.child("users").child(currentUid).child("displayName")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        String schedulerName = snapshot.getValue(String.class);
                                        if (schedulerName != null) {
                                            mDatabase.child("maintenance_reports").child(report.reportId)
                                                    .child("scheduledByName").setValue(schedulerName);
                                        }
                                    }
                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {}
                                });
                    }

                    mDatabase.child("maintenance_reports").child(report.reportId).updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                showToast("Maintenance scheduled successfully");

                                if (!assignedTo.isEmpty()) {
                                    sendScheduleNotification(report, assignedTo, date, time);
                                }

                                loadReports();
                            })
                            .addOnFailureListener(e -> showToast("Failed to schedule: " + e.getMessage()));
                }

                private void sendScheduleNotification(MaintenanceReport report, String assignedTo,
                                                      String date, String time) {
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("type", "maintenance_scheduled");
                    notification.put("reportId", report.reportId);
                    notification.put("title", "New Maintenance Assignment");
                    notification.put("message", "You have been assigned to: " + report.title +
                            " on " + date + " at " + time);
                    notification.put("assignedTo", assignedTo);
                    notification.put("campus", report.campus);
                    notification.put("location", report.location);
                    notification.put("timestamp", System.currentTimeMillis());
                    notification.put("read", false);

                    mDatabase.child("notifications").push().setValue(notification);
                }

                private void showUpdateStatusDialog(MaintenanceReport report) {
                    String currentStatus = report.status != null ? report.status.toLowerCase() : "pending";
                    List<String> allStatusOptions = Arrays.asList("Pending", "Scheduled", "In Progress", "Completed", "Partially Completed", "Rejected");
                    List<String> filteredOptions = new ArrayList<>();

                    for (String status : allStatusOptions) {
                        String lowerStatus = status.toLowerCase();

                        // Skip the current status
                        if (lowerStatus.equals(currentStatus)) {
                            continue;
                        }

                        // Only allow "Rejected" status change from "Pending"
                        if (lowerStatus.equals("rejected") && !currentStatus.equals("pending")) {
                            continue;
                        }

                        // Allow changing from "completed" or "partially completed" back to "pending"
                        if (lowerStatus.equals("pending") &&
                                !currentStatus.equals("completed") &&
                                !currentStatus.equals("partially completed") &&
                                !currentStatus.equals("pending")) {
                            continue;
                        }

                        // Prevent changing from "rejected" to anything except back to "pending"
                        if (currentStatus.equals("rejected") && !lowerStatus.equals("pending")) {
                            continue;
                        }

                        // Allow going back from completed/partially completed to pending, scheduled, or in progress
                        if ((currentStatus.equals("completed") || currentStatus.equals("partially completed")) &&
                                !lowerStatus.equals("pending") &&
                                !lowerStatus.equals("scheduled") &&
                                !lowerStatus.equals("in progress")) {
                            continue;
                        }

                        // Prevent going back to "scheduled" from "in progress" (but allow from completed)
                        if (lowerStatus.equals("scheduled") && currentStatus.equals("in progress")) {
                            continue;
                        }

                        filteredOptions.add(status);
                    }

                    if (filteredOptions.isEmpty()) {
                        showToast("No status changes available for this report");
                        return;
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Update Status");
                    builder.setItems(filteredOptions.toArray(new String[0]), (dialog, which) -> {
                        String newStatus = filteredOptions.get(which).toLowerCase();

                        if ("rejected".equals(newStatus)) {
                            showRejectReasonDialog(report);
                        } else if ("scheduled".equals(newStatus)) {
                            showScheduleDialog(report);
                        } else if ("completed".equals(newStatus)) {
                            showAdminMarkCompleteDialog(report);
                        } else if ("partially completed".equals(newStatus)) {
                            showPartiallyCompleteDialog(report);
                        } else {
                            updateReportStatus(report, newStatus);
                        }
                    });
                    builder.setNegativeButton("Cancel", null);
                    builder.show();
                }

                private void showPartiallyCompleteDialog(MaintenanceReport report) {
                    currentReportIdForAdminCompletion = report.reportId;
                    adminCompletionImageBase64 = null;

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(32, 32, 32, 32);

                    // Title
                    TextView titleText = new TextView(this);
                    titleText.setText("Mark as Partially Complete: " + report.title);
                    titleText.setTextSize(18);
                    titleText.setPadding(0, 0, 0, 16);
                    layout.addView(titleText);

                    // Instruction
                    TextView instructionText = new TextView(this);
                    instructionText.setText("Upload a photo and provide notes about the partial completion:");
                    instructionText.setPadding(0, 0, 0, 16);
                    layout.addView(instructionText);

                    // Notes input
                    EditText notesInput = new EditText(this);
                    notesInput.setHint("Enter notes (required)");
                    notesInput.setMinLines(3);
                    notesInput.setMaxLines(5);
                    layout.addView(notesInput);

                    // Add Photo Button
                    MaterialButton addPhotoButton = new MaterialButton(this);
                    addPhotoButton.setText("Add Completion Photo");
                    layout.addView(addPhotoButton);

                    // Image View
                    ImageView photoImageView = new ImageView(this);
                    photoImageView.setAdjustViewBounds(true);
                    photoImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    photoImageView.setVisibility(View.GONE);
                    layout.addView(photoImageView);

                    // Remove Photo Button
                    MaterialButton removeButton = new MaterialButton(this);
                    removeButton.setText("Remove Photo");
                    removeButton.setVisibility(View.GONE);
                    layout.addView(removeButton);

                    builder.setView(layout);
                    currentAdminCompletionDialogView = layout;

                    addPhotoButton.setOnClickListener(v -> showAdminCompletionPhotoOptions());

                    removeButton.setOnClickListener(v -> {
                        adminCompletionImageBase64 = null;
                        photoImageView.setVisibility(View.GONE);
                        photoImageView.setImageBitmap(null);
                        removeButton.setVisibility(View.GONE);
                    });

                    builder.setTitle("Partially Complete Report");
                    builder.setPositiveButton("Mark Partially Complete", null);
                    builder.setNegativeButton("Cancel", (dialog, which) -> {
                        currentAdminCompletionDialogView = null;
                        currentAdminCompletionDialog = null;
                        currentReportIdForAdminCompletion = null;
                        adminCompletionImageBase64 = null;
                    });

                    AlertDialog dialog = builder.create();
                    currentAdminCompletionDialog = dialog;

                    dialog.setOnShowListener(dialogInterface -> {
                        MaterialButton completeButton = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        completeButton.setOnClickListener(view -> {
                            String notes = notesInput.getText().toString().trim();

                            if (notes.isEmpty()) {
                                showToast("Please provide notes about the partial completion");
                                return;
                            }

                            if (adminCompletionImageBase64 == null || adminCompletionImageBase64.isEmpty()) {
                                showToast("Please add a completion photo");
                                return;
                            }

                            markReportAsPartiallyComplete(report, notes);
                            currentAdminCompletionDialogView = null;
                            currentAdminCompletionDialog = null;
                            currentReportIdForAdminCompletion = null;
                            adminCompletionImageBase64 = null;
                            dialog.dismiss();
                        });
                    });

                    dialog.show();
                }

                private void markReportAsPartiallyComplete(MaintenanceReport report, String notes) {
                    if (adminCompletionImageBase64 == null || adminCompletionImageBase64.isEmpty()) {
                        showToast("Completion photo is required");
                        return;
                    }

                    long completionTime = System.currentTimeMillis();

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", "partially completed");
                    updates.put("completedAt", completionTime);
                    updates.put("completionPhotoBase64", adminCompletionImageBase64);
                    updates.put("partialCompletionNotes", notes);

                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    updates.put("completedAtFormatted", dateFormat.format(new Date(completionTime)));

                    if (mAuth.getCurrentUser() != null) {
                        updates.put("completedBy", mAuth.getCurrentUser().getEmail());

                        // NEW: Get and save completer's full name
                        String currentUid = mAuth.getCurrentUser().getUid();
                        mDatabase.child("users").child(currentUid).child("displayName")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        String completerName = snapshot.getValue(String.class);
                                        if (completerName != null) {
                                            mDatabase.child("maintenance_reports").child(report.reportId)
                                                    .child("completedByName").setValue(completerName);
                                        }
                                    }
                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {}
                                });
                    }

                    showToast("Marking report as partially complete...");

                    mDatabase.child("maintenance_reports").child(report.reportId).updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                showToast("Report marked as partially complete successfully");

                                String chatId = ChatHelper.generateReportChatId(report.reportId);
                                String message = "Report status updated to: PARTIALLY COMPLETED\n\nNotes: " + notes;
                                ChatHelper.sendSystemMessage(chatId, message, report.reportId);

                                loadReports();
                            })
                            .addOnFailureListener(e -> {
                                showToast("Failed to update report: " + e.getMessage());
                                Log.e(TAG, "Failed to update report", e);
                            });
                }

                private void showRejectReasonDialog(MaintenanceReport report) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);

                    LinearLayout layout = new LinearLayout(this);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setPadding(50, 40, 50, 10);

                    TextView messageText = new TextView(this);
                    messageText.setText("Please provide a reason for rejecting this report:");
                    messageText.setPadding(0, 0, 0, 20);
                    layout.addView(messageText);

                    EditText reasonInput = new EditText(this);
                    reasonInput.setHint("Rejection reason (required)");
                    reasonInput.setMinLines(3);
                    reasonInput.setMaxLines(5);
                    layout.addView(reasonInput);

                    builder.setView(layout);
                    builder.setTitle("Reject Report");
                    builder.setPositiveButton("Reject", (dialog, which) -> {
                        String reason = reasonInput.getText().toString().trim();
                        if (reason.isEmpty()) {
                            showToast("Please provide a reason for rejection");
                            showRejectReasonDialog(report); // Show dialog again
                            return;
                        }
                        updateReportStatusWithReason(report, "rejected", reason);
                    });
                    builder.setNegativeButton("Cancel", null);
                    builder.show();
                }

                private void updateReportStatusWithReason(MaintenanceReport report, String newStatus, String reason) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", newStatus);
                    updates.put("rejectionReason", reason);
                    updates.put("rejectedAt", System.currentTimeMillis());

                    if (mAuth.getCurrentUser() != null) {
                        updates.put("rejectedBy", mAuth.getCurrentUser().getEmail());

                        // NEW: Get and save rejector's full name
                        String currentUid = mAuth.getCurrentUser().getUid();
                        mDatabase.child("users").child(currentUid).child("displayName")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        String rejectorName = snapshot.getValue(String.class);
                                        if (rejectorName != null) {
                                            mDatabase.child("maintenance_reports").child(report.reportId)
                                                    .child("rejectedByName").setValue(rejectorName);
                                        }
                                    }
                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {}
                                });
                    }

                    mDatabase.child("maintenance_reports").child(report.reportId).updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                showToast("Report rejected successfully");

                                String chatId = ChatHelper.generateReportChatId(report.reportId);
                                String message = "Report status updated to: REJECTED\n\nReason: " + reason;
                                ChatHelper.sendSystemMessage(chatId, message, report.reportId);

                                loadReports();
                            })
                            .addOnFailureListener(e -> showToast("Failed to update status: " + e.getMessage()));
                }

                private void updateReportStatus(MaintenanceReport report, String newStatus) {
                    // If marking as completed, require completion photo
                    if ("completed".equalsIgnoreCase(newStatus)) {
                        showAdminMarkCompleteDialog(report);
                        return;
                    }

                    // For other status updates, proceed normally
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", newStatus);

                    mDatabase.child("maintenance_reports").child(report.reportId).updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                showToast("Status updated successfully");

                                // Send system message to chat
                                String chatId = ChatHelper.generateReportChatId(report.reportId);
                                String message = "Report status updated to: " + newStatus.toUpperCase();

                                // Add context based on status
                                switch (newStatus.toLowerCase()) {
                                    case "scheduled":
                                        message += "\n\nMaintenance has been scheduled for this report.";
                                        break;
                                    case "in progress":
                                        message += "\n\nTechnician is now working on this issue.";
                                        break;
                                }

                                ChatHelper.sendSystemMessage(chatId, message, report.reportId);

                                loadReports();
                            })
                            .addOnFailureListener(e -> showToast("Failed to update status: " + e.getMessage()));
                }

                private void showAdminMarkCompleteDialog(MaintenanceReport report) {
                    currentReportIdForAdminCompletion = report.reportId;
                    adminCompletionImageBase64 = null;

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_mark_complete, null);

                    // If layout doesn't exist, create programmatically
                    if (dialogView == null) {
                        dialogView = createAdminCompletionView();
                    }

                    builder.setView(dialogView);
                    currentAdminCompletionDialogView = dialogView;

                    TextView titleText = dialogView.findViewById(R.id.completionTitleText);
                    MaterialButton addPhotoButton = dialogView.findViewById(R.id.addCompletionPhotoButton);
                    ImageView photoImageView = dialogView.findViewById(R.id.completionImageView);
                    MaterialButton removeButton = dialogView.findViewById(R.id.removeCompletionPhotoButton);
                    TextView instructionText = dialogView.findViewById(R.id.completionInstructionText);

                    if (titleText != null) {
                        titleText.setText("Mark as Complete: " + report.title);
                    }

                    if (instructionText != null) {
                        instructionText.setText("Please upload a photo showing the completed repair/fix before marking this report as complete.");
                    }

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
                        addPhotoButton.setOnClickListener(v -> showAdminCompletionPhotoOptions());
                    }

                    // Remove photo button listener
                    if (removeButton != null) {
                        removeButton.setOnClickListener(v -> {
                            adminCompletionImageBase64 = null;
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
                        currentAdminCompletionDialogView = null;
                        currentAdminCompletionDialog = null;
                        currentReportIdForAdminCompletion = null;
                        adminCompletionImageBase64 = null;
                        dialog.dismiss();
                    });

                    AlertDialog dialog = builder.create();
                    currentAdminCompletionDialog = dialog;

                    dialog.setOnShowListener(dialogInterface -> {
                        MaterialButton completeButton = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        completeButton.setOnClickListener(view -> {
                            if (adminCompletionImageBase64 == null || adminCompletionImageBase64.isEmpty()) {
                                showToast("Please add a completion photo before marking as complete");
                                return;
                            }
                            markReportAsCompleteWithPhoto(report);
                            currentAdminCompletionDialogView = null;
                            currentAdminCompletionDialog = null;
                            currentReportIdForAdminCompletion = null;
                            adminCompletionImageBase64 = null;
                            dialog.dismiss();
                        });
                    });

                    dialog.show();
                }

                private void showAdminCompletionPhotoOptions() {
                    String[] options = {"Take Photo", "Choose from Gallery"};

                    new AlertDialog.Builder(this)
                            .setTitle("Add Completion Photo")
                            .setItems(options, (dialog, which) -> {
                                if (which == 0) {
                                    // Take photo
                                    if (checkCameraPermission()) {
                                        openAdminCompletionCamera();
                                    }
                                } else {
                                    // Choose from gallery
                                    if (checkStoragePermission()) {
                                        openAdminCompletionGallery();
                                    }
                                }
                            })
                            .show();
                }

                // Add permission check methods
                private boolean checkCameraPermission() {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.CAMERA},
                                100);
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
                                    101);
                            return false;
                        }
                    } else {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                                != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                    101);
                            return false;
                        }
                    }
                    return true;
                }

                // Add camera and gallery methods
                private void openAdminCompletionCamera() {
                    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                        startActivityForResult(takePictureIntent, REQUEST_ADMIN_COMPLETION_IMAGE_CAPTURE);
                    }
                }

                private void openAdminCompletionGallery() {
                    Intent pickPhotoIntent = new Intent(Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(pickPhotoIntent, REQUEST_ADMIN_COMPLETION_IMAGE_PICK);
                }

                @Override
                protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
                    super.onActivityResult(requestCode, resultCode, data);

                    if (resultCode == RESULT_OK && data != null) {
                        Bitmap processedBitmap = null;

                        if (requestCode == REQUEST_ADMIN_COMPLETION_IMAGE_CAPTURE) {
                            Bundle extras = data.getExtras();
                            if (extras != null) {
                                Bitmap imageBitmap = (Bitmap) extras.get("data");
                                if (imageBitmap != null) {
                                    processedBitmap = compressBitmap(imageBitmap);
                                }
                            }
                        } else if (requestCode == REQUEST_ADMIN_COMPLETION_IMAGE_PICK) {
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
                            adminCompletionImageBase64 = bitmapToBase64(processedBitmap);

                            if (currentAdminCompletionDialogView != null) {
                                ImageView photoImageView = currentAdminCompletionDialogView.findViewById(R.id.completionImageView);
                                MaterialButton removeButton = currentAdminCompletionDialogView.findViewById(R.id.removeCompletionPhotoButton);

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

                // Add image processing methods
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

                // Add method to mark report as complete with photo
                private void markReportAsCompleteWithPhoto(MaintenanceReport report) {
                    if (adminCompletionImageBase64 == null || adminCompletionImageBase64.isEmpty()) {
                        showToast("Completion photo is required");
                        return;
                    }

                    long completionTime = System.currentTimeMillis();

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", "completed");
                    updates.put("completedAt", completionTime);
                    updates.put("completionPhotoBase64", adminCompletionImageBase64);

                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    updates.put("completedAtFormatted", dateFormat.format(new Date(completionTime)));

                    if (mAuth.getCurrentUser() != null) {
                        updates.put("completedBy", mAuth.getCurrentUser().getEmail());

                        // NEW: Get and save completer's full name
                        String currentUid = mAuth.getCurrentUser().getUid();
                        mDatabase.child("users").child(currentUid).child("displayName")
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        String completerName = snapshot.getValue(String.class);
                                        if (completerName != null) {
                                            mDatabase.child("maintenance_reports").child(report.reportId)
                                                    .child("completedByName").setValue(completerName);
                                        }
                                    }
                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {}
                                });
                    }

                    showToast("Marking report as complete...");

                    mDatabase.child("maintenance_reports").child(report.reportId).updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                showToast("Report marked as complete successfully");

                                String chatId = ChatHelper.generateReportChatId(report.reportId);
                                String message = "Report status updated to: COMPLETED\n\nThis maintenance request has been completed with verification photo.";
                                ChatHelper.sendSystemMessage(chatId, message, report.reportId);

                                loadReports();
                            })
                            .addOnFailureListener(e -> {
                                showToast("Failed to update report: " + e.getMessage());
                                Log.e(TAG, "Failed to update report", e);
                            });
                }

                private void confirmDeleteReport(MaintenanceReport report) {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete Report")
                            .setMessage("Are you sure you want to delete this report?\n\n" + report.title)
                            .setPositiveButton("Delete", (dialog, which) -> deleteReport(report))
                            .setNegativeButton("Cancel", null)
                            .show();
                }

                private void deleteReport(MaintenanceReport report) {
                    mDatabase.child("maintenance_reports").child(report.reportId).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                showToast("Report deleted successfully");
                                loadReports();
                            })
                            .addOnFailureListener(e -> showToast("Failed to delete report: " + e.getMessage()));
                }

                // Helper method to set priority color (add if not exists)
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

                // Helper method to set status color (add if not exists)
                private void setStatusColor(Chip chip, String status) {
                    switch (status.toLowerCase().replace(" ", "_")) {
                        case "completed":
                            chip.setChipBackgroundColorResource(R.color.status_completed);
                            break;
                        case "in_progress":
                        case "in progress":
                            chip.setChipBackgroundColorResource(R.color.status_in_progress);
                            break;
                        case "scheduled":
                            chip.setChipBackgroundColorResource(R.color.status_scheduled);
                            break;
                        case "pending":
                        default:
                            chip.setChipBackgroundColorResource(R.color.status_pending);
                            break;
                    }
                }


            }