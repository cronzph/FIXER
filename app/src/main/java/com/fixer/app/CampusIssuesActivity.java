package com.fixer.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CampusIssuesActivity extends AppCompatActivity {

    private static final String TAG = "CampusIssuesActivity";

    // UI Components
    private RecyclerView campusRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SearchView searchView;
    private ChipGroup filterChipGroup;
    private TextView emptyStateText;
    private TextView summaryText;


    // Firebase
    private DatabaseReference mDatabase;

    // Data
    private List<CampusIssueItem> allCampuses;
    private List<CampusIssueItem> filteredCampuses;
    private CampusIssuesAdapter campusAdapter;
    private String searchQuery = "";
    private String selectedFilter = "all"; // all, critical, warning, normal, no_issues

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_campus_issues);

        // Initialize Firebase
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Setup toolbar
        setupToolbar();

        // Initialize UI components
        initializeViews();

        // Setup RecyclerView
        setupRecyclerView();

        // Setup filters
        setupFilters();

        // Setup search
        setupSearch();

        // Load data
        loadCampusData();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Campus Issues Overview");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void initializeViews() {
        campusRecyclerView = findViewById(R.id.campusRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        searchView = findViewById(R.id.searchView);
        filterChipGroup = findViewById(R.id.filterChipGroup);
        emptyStateText = findViewById(R.id.emptyStateText);
        summaryText = findViewById(R.id.summaryText);

        // Setup swipe refresh
        swipeRefreshLayout.setOnRefreshListener(this::loadCampusData);
        swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_red_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_green_light
        );

    }

    private void setupRecyclerView() {
        allCampuses = new ArrayList<>();
        filteredCampuses = new ArrayList<>();
        campusAdapter = new CampusIssuesAdapter(filteredCampuses);

        // Default to list view
        campusRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        campusRecyclerView.setAdapter(campusAdapter);
    }

    private void setupFilters() {
        String[] filters = {"All", "Critical", "Warning", "Normal", "No Issues"};
        String[] filterValues = {"all", "critical", "warning", "normal", "no_issues"};

        for (int i = 0; i < filters.length; i++) {
            Chip chip = new Chip(this);
            chip.setText(filters[i]);
            chip.setCheckable(true);

            final String filterValue = filterValues[i];
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedFilter = filterValue;
                    uncheckOtherChips(chip);
                    applyFilters();
                }
            });

            filterChipGroup.addView(chip);

            // Set "All" as default selected
            if (i == 0) {
                chip.setChecked(true);
            }
        }
    }

    private void uncheckOtherChips(Chip selectedChip) {
        for (int i = 0; i < filterChipGroup.getChildCount(); i++) {
            Chip chip = (Chip) filterChipGroup.getChildAt(i);
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

    private void loadCampusData() {
        swipeRefreshLayout.setRefreshing(true);

        // First, load all campuses
        mDatabase.child("campuses").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot campusSnapshot) {
                allCampuses.clear();

                // Create map to store campus names
                Map<String, String> campusMap = new HashMap<>();

                for (DataSnapshot campus : campusSnapshot.getChildren()) {
                    String campusId = campus.getKey();
                    String campusName = campus.getValue(String.class);
                    if (campusName != null) {
                        campusMap.put(campusName, campusId);
                    }
                }

                // Now load reports to count issues per campus
                loadReportsAndCountIssues(campusMap);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load campuses", error.toException());
                showToast("Failed to load campuses");
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void loadReportsAndCountIssues(Map<String, String> campusMap) {
        mDatabase.child("maintenance_reports").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Count issues per campus
                Map<String, CampusIssueItem> campusIssueMap = new HashMap<>();

                // Initialize all campuses with 0 issues
                for (Map.Entry<String, String> entry : campusMap.entrySet()) {
                    CampusIssueItem item = new CampusIssueItem(
                            entry.getValue(), // ID
                            entry.getKey(),   // Name
                            0, 0, 0, 0        // Issue counts
                    );
                    campusIssueMap.put(entry.getKey(), item);
                }

                // Count issues by status
                for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                    String campus = reportSnapshot.child("campus").getValue(String.class);
                    String status = reportSnapshot.child("status").getValue(String.class);
                    String priority = reportSnapshot.child("priority").getValue(String.class);

                    if (campus != null && status != null && campusIssueMap.containsKey(campus)) {
                        CampusIssueItem item = campusIssueMap.get(campus);

                        // Count by status
                        switch (status.toLowerCase()) {
                            case "pending":
                                item.pendingCount++;
                                break;
                            case "in progress":
                            case "in_progress":
                                item.inProgressCount++;
                                break;
                            case "scheduled":
                                item.scheduledCount++;
                                break;
                            case "completed":
                                item.completedCount++;
                                break;
                        }

                        // Count critical issues (high priority or critical priority)
                        if ("critical".equalsIgnoreCase(priority) || "high".equalsIgnoreCase(priority)) {
                            item.criticalCount++;
                        }
                    }
                }

                // Convert map to list
                allCampuses.clear();
                allCampuses.addAll(campusIssueMap.values());

                // Sort by total active issues (descending)
                allCampuses.sort((a, b) -> Integer.compare(b.getTotalActiveIssues(), a.getTotalActiveIssues()));

                // Update UI
                applyFilters();
                updateSummary();
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
               // Log.e(TAG, "Failed to load reports", error.toException());
               // showToast("Failed to load issue counts");
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void applyFilters() {
        filteredCampuses.clear();

        for (CampusIssueItem campus : allCampuses) {
            // Apply status filter
            boolean matchesFilter = false;
            int totalActive = campus.getTotalActiveIssues();

            switch (selectedFilter) {
                case "all":
                    matchesFilter = true;
                    break;
                case "critical":
                    matchesFilter = totalActive >= 5;
                    break;
                case "warning":
                    matchesFilter = totalActive >= 3 && totalActive < 5;
                    break;
                case "normal":
                    matchesFilter = totalActive >= 1 && totalActive < 3;
                    break;
                case "no_issues":
                    matchesFilter = totalActive == 0;
                    break;
            }

            // Apply search filter
            boolean matchesSearch = searchQuery.isEmpty() ||
                    campus.campusName.toLowerCase().contains(searchQuery.toLowerCase());

            if (matchesFilter && matchesSearch) {
                filteredCampuses.add(campus);
            }
        }

        campusAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateSummary() {
        if (summaryText == null) return;

        int totalCampuses = allCampuses.size();
        int totalIssues = 0;
        int criticalCampuses = 0;
        int warningCampuses = 0;
        int normalCampuses = 0;
        int noIssueCampuses = 0;

        for (CampusIssueItem campus : allCampuses) {
            int activeIssues = campus.getTotalActiveIssues();
            totalIssues += activeIssues;

            if (activeIssues >= 5) {
                criticalCampuses++;
            } else if (activeIssues >= 3) {
                warningCampuses++;
            } else if (activeIssues >= 1) {
                normalCampuses++;
            } else {
                noIssueCampuses++;
            }
        }

        String summary = String.format(
                "Total: %d campuses, %d active issues\n" +
                        "🔴 Critical: %d  🟡 Warning: %d  🟢 Normal: %d  ✅ Clear: %d",
                totalCampuses, totalIssues,
                criticalCampuses, warningCampuses, normalCampuses, noIssueCampuses
        );

        summaryText.setText(summary);
    }

    private void updateEmptyState() {
        if (filteredCampuses.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            campusRecyclerView.setVisibility(View.GONE);

            if (searchQuery.isEmpty() && selectedFilter.equals("all")) {
                emptyStateText.setText("No campuses found");
            } else {
                emptyStateText.setText("No campuses match your filters");
            }
        } else {
            emptyStateText.setVisibility(View.GONE);
            campusRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void openCampusReports(CampusIssueItem campus) {
        Intent intent = new Intent(this, ReportManagementActivity.class);
        intent.putExtra("filter_campus", campus.campusName);
        intent.putExtra("campus_id", campus.campusId);
        startActivity(intent);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Data model for campus with issue counts
    public static class CampusIssueItem {
        public String campusId;
        public String campusName;
        public int pendingCount;
        public int inProgressCount;
        public int scheduledCount;
        public int completedCount;
        public int criticalCount;

        public CampusIssueItem(String campusId, String campusName,
                               int pendingCount, int inProgressCount,
                               int scheduledCount, int completedCount) {
            this.campusId = campusId;
            this.campusName = campusName;
            this.pendingCount = pendingCount;
            this.inProgressCount = inProgressCount;
            this.scheduledCount = scheduledCount;
            this.completedCount = completedCount;
            this.criticalCount = 0;
        }

        public int getTotalActiveIssues() {
            return pendingCount + inProgressCount + scheduledCount;
        }

        public int getTotalIssues() {
            return pendingCount + inProgressCount + scheduledCount + completedCount;
        }

        public String getStatusIndicator() {
            int active = getTotalActiveIssues();
            if (active >= 5) return "🔴"; // Critical
            if (active >= 3) return "🟡"; // Warning
            if (active >= 1) return "🟢"; // Normal
            return "✅"; // No issues
        }

        public int getStatusColor() {
            int active = getTotalActiveIssues();
            if (active >= 5) return R.color.primary_light;
            if (active >= 3) return R.color.primary_light;
            if (active >= 1) return R.color.primary_light;
            return R.color.primary_light;
        }
    }

    // Adapter for campus issues
    private class CampusIssuesAdapter extends RecyclerView.Adapter<CampusIssuesAdapter.ViewHolder> {
        private List<CampusIssueItem> campuses;
        private boolean isGridView = false;

        public CampusIssuesAdapter(List<CampusIssueItem> campuses) {
            this.campuses = campuses;
        }

        public void setGridView(boolean gridView) {
            this.isGridView = gridView;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId = isGridView ? R.layout.item_campus_grid : R.layout.item_campus_issue;
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(layoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CampusIssueItem campus = campuses.get(position);

            holder.campusNameText.setText(campus.campusName);
            holder.statusIndicator.setText(campus.getStatusIndicator());

            // Set total active issues
            int activeIssues = campus.getTotalActiveIssues();
            holder.totalIssuesText.setText(activeIssues + " active issue" +
                    (activeIssues != 1 ? "s" : ""));

            // Set status breakdown
            if (holder.statusBreakdownText != null) {
                String breakdown = String.format(
                        "Pending: %d | In Progress: %d | Scheduled: %d",
                        campus.pendingCount, campus.inProgressCount, campus.scheduledCount
                );
                holder.statusBreakdownText.setText(breakdown);
            }

            // Set completed count if available
            if (holder.completedText != null) {
                holder.completedText.setText("Completed: " + campus.completedCount);
            }

            // Set critical indicator
            if (holder.criticalIndicator != null) {
                if (campus.criticalCount > 0) {
                    holder.criticalIndicator.setVisibility(View.VISIBLE);
                    holder.criticalIndicator.setText("⚠️ " + campus.criticalCount + " critical");
                } else {
                    holder.criticalIndicator.setVisibility(View.GONE);
                }
            }

            // Set card background color based on status
            if (holder.cardView != null) {
                int colorRes = campus.getStatusColor();
                holder.cardView.setCardBackgroundColor(
                        getResources().getColor(colorRes, null)
                );
            }

            // Set click listener
            holder.itemView.setOnClickListener(v -> openCampusReports(campus));
        }

        @Override
        public int getItemCount() {
            return campuses.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView campusNameText;
            TextView statusIndicator;
            TextView totalIssuesText;
            TextView statusBreakdownText;
            TextView completedText;
            TextView criticalIndicator;
            CardView cardView;

            ViewHolder(View itemView) {
                super(itemView);
                campusNameText = itemView.findViewById(R.id.campusNameText);
                statusIndicator = itemView.findViewById(R.id.statusIndicator);
                totalIssuesText = itemView.findViewById(R.id.totalIssuesText);
                statusBreakdownText = itemView.findViewById(R.id.statusBreakdownText);
                completedText = itemView.findViewById(R.id.completedText);
                criticalIndicator = itemView.findViewById(R.id.criticalIndicator);
                cardView = itemView.findViewById(R.id.campusCard);
            }
        }
    }
}