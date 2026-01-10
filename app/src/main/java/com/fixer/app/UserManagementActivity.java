package com.fixer.app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserManagementActivity extends AppCompatActivity {

    private static final String TAG = "UserManagement";

    // UI Components
    private SearchView searchView;
    private ChipGroup filterChipGroup;
    private RecyclerView usersRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyStateText;

    // Firebase
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    // Data
    private List<UserData> allUsers;
    private List<UserData> filteredUsers;
    private UserAdapter userAdapter;

    // Filters
    private String selectedFilter = "all"; // all, pending, approved, admin, user
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        // Initialize Firebase
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

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

        // Load users
        loadUsers();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("User Management");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void initializeViews() {
        searchView = findViewById(R.id.searchView);
        filterChipGroup = findViewById(R.id.filterChipGroup);
        usersRecyclerView = findViewById(R.id.usersRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        emptyStateText = findViewById(R.id.emptyStateText);

        // Setup swipe refresh
        swipeRefreshLayout.setOnRefreshListener(this::loadUsers);
        swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_red_light,
                android.R.color.holo_orange_light
        );
    }

    private void setupRecyclerView() {
        allUsers = new ArrayList<>();
        filteredUsers = new ArrayList<>();
        userAdapter = new UserAdapter(filteredUsers);
        usersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        usersRecyclerView.setAdapter(userAdapter);
    }

    private void setupFilters() {
        String[] filters = {"All", "Pending", "Approved", "Admin", "Technician", "User"};
        String[] filterValues = {"all", "pending", "approved", "admin", "technician", "user"};

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

    private void loadUsers() {
        swipeRefreshLayout.setRefreshing(true);

        mDatabase.child("users").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allUsers.clear();

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    UserData user = new UserData();
                    user.uid = userSnapshot.getKey();
                    user.email = userSnapshot.child("email").getValue(String.class);
                    user.displayName = userSnapshot.child("displayName").getValue(String.class);
                    user.campus = userSnapshot.child("campus").getValue(String.class);
                    user.department = userSnapshot.child("department").getValue(String.class);
                    user.phone = userSnapshot.child("phone").getValue(String.class);
                    user.approved = Boolean.TRUE.equals(userSnapshot.child("approved").getValue(Boolean.class));

                    // Handle role - it might be stored as enum or string
                    String roleValue = userSnapshot.child("role").getValue(String.class);
                    if (roleValue == null) {
                        user.role = "USER"; // Default role
                    } else {
                        user.role = roleValue.toUpperCase();
                    }

                    // Handle roleActive - default to true if not present
                    Boolean roleActiveValue = userSnapshot.child("roleActive").getValue(Boolean.class);
                    user.roleActive = roleActiveValue != null ? roleActiveValue : true;

                    // Handle status field from RequestAccessActivity
                    user.status = userSnapshot.child("status").getValue(String.class);
                    if (user.status == null) {
                        user.status = user.approved ? "approved" : "pending";
                    }

                    Long createdAt = userSnapshot.child("createdAt").getValue(Long.class);
                    user.createdAt = createdAt != null ? createdAt : 0L;

                    Long lastLogin = userSnapshot.child("lastLogin").getValue(Long.class);
                    user.lastLogin = lastLogin != null ? lastLogin : 0L;

                    // displayName is actually the fullName from RequestAccessActivity
                    user.fullName = user.displayName;

                    allUsers.add(user);
                }

                // Sort by most recent first
                allUsers.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));

                applyFilters();
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load users", error.toException());
                showToast("Failed to load users. Please try again.");
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void applyFilters() {
        filteredUsers.clear();

        for (UserData user : allUsers) {
            boolean matchesFilter = false;

            switch (selectedFilter) {
                case "all":
                    matchesFilter = true;
                    break;
                case "pending":
                    matchesFilter = !user.approved;
                    break;
                case "approved":
                    matchesFilter = user.approved;
                    break;
                case "admin":
                    matchesFilter = "ADMIN".equalsIgnoreCase(user.role);
                    break;
                case "technician":
                    matchesFilter = "TECHNICIAN".equalsIgnoreCase(user.role);
                    break;
                case "user":
                    matchesFilter = "USER".equalsIgnoreCase(user.role);
                    break;
            }

            boolean matchesSearch = searchQuery.isEmpty() ||
                    (user.fullName != null && user.fullName.toLowerCase().contains(searchQuery.toLowerCase())) ||
                    (user.email != null && user.email.toLowerCase().contains(searchQuery.toLowerCase())) ||
                    (user.campus != null && user.campus.toLowerCase().contains(searchQuery.toLowerCase()));

            if (matchesFilter && matchesSearch) {
                filteredUsers.add(user);
            }
        }

        userAdapter.notifyDataSetChanged();
        updateEmptyState();
    }


    private void updateEmptyState() {
        if (filteredUsers.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            usersRecyclerView.setVisibility(View.GONE);

            if (searchQuery.isEmpty() && selectedFilter.equals("all")) {
                emptyStateText.setText("No users found.\nUsers will appear here once registered.");
            } else {
                emptyStateText.setText("No users match your search or filters.\nTry adjusting your search or filters.");
            }
        } else {
            emptyStateText.setVisibility(View.GONE);
            usersRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showUserActions(UserData user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(user.fullName != null ? user.fullName : user.email);

        List<String> options = new ArrayList<>();

        // Add Edit User option at the top
        options.add("Edit User Details");

        if (!user.approved) {
            options.add("Approve User");
        }

        if (user.approved && user.roleActive) {
            options.add("Deactivate User");
        } else if (user.approved && !user.roleActive) {
            options.add("Activate User");
        }

        // Role management options
        String currentRole = user.role != null ? user.role.toUpperCase() : "USER";

        if (!"ADMIN".equalsIgnoreCase(currentRole)) {
            options.add("Make Admin");
        }

        if (!"TECHNICIAN".equalsIgnoreCase(currentRole)) {
            options.add("Make Technician");
        }

        if (!"USER".equalsIgnoreCase(currentRole)) {
            options.add("Make Regular User");
        }

        options.add("Delete User");
        options.add("Cancel");

        builder.setItems(options.toArray(new String[0]), (dialog, which) -> {
            String selected = options.get(which);

            switch (selected) {
                case "Edit User Details":
                    showEditUserDialog(user);
                    break;
                case "Approve User":
                    approveUser(user);
                    break;
                case "Deactivate User":
                    toggleUserActivation(user, false);
                    break;
                case "Activate User":
                    toggleUserActivation(user, true);
                    break;
                case "Make Admin":
                    changeUserRole(user, "ADMIN");
                    break;
                case "Make Technician":
                    changeUserRole(user, "TECHNICIAN");
                    break;
                case "Make Regular User":
                    changeUserRole(user, "USER");
                    break;
                case "Delete User":
                    confirmDeleteUser(user);
                    break;
            }
        });

        builder.show();
    }

    private void showEditUserDialog(UserData user) {
        // Inflate custom layout for edit dialog
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_user, null);

        // Get references to input fields
        TextInputEditText fullNameInput = dialogView.findViewById(R.id.fullNameInput);
        TextInputEditText emailInput = dialogView.findViewById(R.id.emailInput);
        TextInputEditText campusInput = dialogView.findViewById(R.id.campusInput);
        TextInputEditText departmentInput = dialogView.findViewById(R.id.departmentInput);
        TextInputEditText phoneInput = dialogView.findViewById(R.id.phoneInput);

        // Pre-fill with current data
        fullNameInput.setText(user.fullName != null ? user.fullName : "");
        emailInput.setText(user.email != null ? user.email : "");
        campusInput.setText(user.campus != null ? user.campus : "");
        departmentInput.setText(user.department != null ? user.department : "");
        phoneInput.setText(user.phone != null ? user.phone : "");

        // Email should not be editable (it's the authentication identifier)
        emailInput.setEnabled(false);
        emailInput.setFocusable(false);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit User Details");
        builder.setView(dialogView);

        builder.setPositiveButton("Save", (dialog, which) -> {
            // Get updated values
            String newFullName = fullNameInput.getText().toString().trim();
            String newCampus = campusInput.getText().toString().trim();
            String newDepartment = departmentInput.getText().toString().trim();
            String newPhone = phoneInput.getText().toString().trim();

            // Validate
            if (newFullName.isEmpty()) {
                showToast("Full name cannot be empty");
                return;
            }

            // Update user details
            updateUserDetails(user, newFullName, newCampus, newDepartment, newPhone);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updateUserDetails(UserData user, String fullName, String campus,
                                   String department, String phone) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", fullName);
        updates.put("campus", campus);
        updates.put("department", department);
        updates.put("phone", phone);

        mDatabase.child("users").child(user.uid).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    showToast("User details updated successfully");

                    // Also update access_requests if exists
                    Map<String, Object> requestUpdates = new HashMap<>();
                    requestUpdates.put("fullName", fullName);
                    requestUpdates.put("campus", campus);
                    requestUpdates.put("department", department);
                    requestUpdates.put("phone", phone);

                    mDatabase.child("access_requests").child(user.uid).updateChildren(requestUpdates);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update user details", e);
                    showToast("Failed to update user details: " + e.getMessage());
                });
    }

    private void approveUser(UserData user) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("approved", true);
        updates.put("status", "approved");
        if (user.role == null || user.role.isEmpty()) {
            updates.put("role", "USER"); // Only set if walang role
        } else {
            // Keep existing role (USER or TECHNICIAN)
        }
        updates.put("roleActive", true); // Activate the user

        mDatabase.child("users").child(user.uid).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    showToast("approved successfully");

                    // Also update access_requests if exists
                    Map<String, Object> requestUpdates = new HashMap<>();
                    requestUpdates.put("status", "approved");
                    requestUpdates.put("approvedAt", System.currentTimeMillis());
                    requestUpdates.put("approvedBy", mAuth.getCurrentUser() != null ?
                            mAuth.getCurrentUser().getEmail() : "Admin");

                    mDatabase.child("access_requests").child(user.uid).updateChildren(requestUpdates);
                })
                .addOnFailureListener(e -> showToast("Failed to approve: " + e.getMessage()));
    }

    private void toggleUserActivation(UserData user, boolean activate) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("roleActive", activate);

        mDatabase.child("users").child(user.uid).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    showToast(activate ? "User activated successfully" : "User deactivated successfully");
                })
                .addOnFailureListener(e -> showToast("Failed to update user: " + e.getMessage()));
    }

    private void changeUserRole(UserData user, String newRole) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("role", newRole);

        mDatabase.child("users").child(user.uid).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    showToast("User role changed to " + newRole);
                })
                .addOnFailureListener(e -> showToast("Failed to change role: " + e.getMessage()));
    }

    private void confirmDeleteUser(UserData user) {
        new AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete " +
                        (user.fullName != null ? user.fullName : user.email) + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteUser(user))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteUser(UserData user) {
        // Delete from users node
        mDatabase.child("users").child(user.uid).removeValue()
                .addOnSuccessListener(aVoid -> {
                    // Also delete from access_requests if exists
                    mDatabase.child("access_requests").child(user.uid).removeValue();
                    showToast("User deleted successfully");
                })
                .addOnFailureListener(e -> showToast("Failed to delete user: " + e.getMessage()));
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // User Data Model
    public static class UserData {
        public String uid;
        public String email;
        public String displayName;
        public String fullName;
        public String campus;
        public String department;
        public String phone;
        public boolean approved;
        public String role;
        public boolean roleActive;
        public String status; // pending, approved, rejected
        public long createdAt;
        public long lastLogin;
    }

    // User Adapter
    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {
        private List<UserData> users;
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        public UserAdapter(List<UserData> users) {
            this.users = users;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            UserData user = users.get(position);

            // Set user details
            String displayName = user.fullName != null && !user.fullName.isEmpty() ?
                    user.fullName : user.displayName;
            holder.nameText.setText(displayName != null ? displayName : "No Name");
            holder.emailText.setText(user.email != null ? user.email : "No Email");

            // Build details string
            List<String> detailsList = new ArrayList<>();
            if (user.campus != null && !user.campus.isEmpty()) {
                detailsList.add(user.campus);
            }
            if (user.department != null && !user.department.isEmpty()) {
                detailsList.add(user.department);
            }
            if (user.phone != null && !user.phone.isEmpty()) {
                detailsList.add(user.phone);
            }

            // Join details with bullet separator
            StringBuilder detailsBuilder = new StringBuilder();
            for (int i = 0; i < detailsList.size(); i++) {
                if (i > 0) {
                    detailsBuilder.append(" • ");
                }
                detailsBuilder.append(detailsList.get(i));
            }
            String details = detailsBuilder.toString();

            holder.detailsText.setText(details.isEmpty() ? "No additional details" : details);
            holder.detailsText.setVisibility(details.isEmpty() ? View.GONE : View.VISIBLE);

            // Set status chip
            if (!user.approved) {
                holder.statusChip.setText("PENDING");
                holder.statusChip.setChipBackgroundColorResource(R.color.status_pending);
            } else if (!user.roleActive) {
                holder.statusChip.setText("INACTIVE");
                holder.statusChip.setChipBackgroundColorResource(R.color.status_inactive);
            } else {
                holder.statusChip.setText("ACTIVE");
                holder.statusChip.setChipBackgroundColorResource(R.color.status_active);
            }

            // Set role chip with color coding
            String roleText = user.role != null && !user.role.isEmpty() ? user.role : "USER";
            holder.roleChip.setText(roleText);
            if ("ADMIN".equalsIgnoreCase(roleText)) {
                holder.roleChip.setChipBackgroundColorResource(R.color.role_admin);
            } else if ("TECHNICIAN".equalsIgnoreCase(roleText)) {
                holder.roleChip.setChipBackgroundColorResource(R.color.role_technician);
            } else {
                holder.roleChip.setChipBackgroundColorResource(R.color.role_user);
            }

            // Set last login
            if (user.lastLogin > 0) {
                holder.lastLoginText.setText("Last login: " + dateFormat.format(new Date(user.lastLogin)));
            } else {
                holder.lastLoginText.setText("Never logged in");
            }

            // Set action button
            holder.actionButton.setOnClickListener(v -> showUserActions(user));
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, emailText, detailsText, lastLoginText;
            Chip statusChip, roleChip;
            MaterialButton actionButton;

            ViewHolder(View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.nameText);
                emailText = itemView.findViewById(R.id.emailText);
                detailsText = itemView.findViewById(R.id.detailsText);
                lastLoginText = itemView.findViewById(R.id.lastLoginText);
                statusChip = itemView.findViewById(R.id.statusChip);
                roleChip = itemView.findViewById(R.id.roleChip);
                actionButton = itemView.findViewById(R.id.actionButton);
            }
        }
    }
}