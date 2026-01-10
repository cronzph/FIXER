package com.fixer.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";
    private static final String PREFS_NAME = "FixerPrefs";

    // UI Components
    private TextView emailText;
    private TextView userIdText;
    private TextView memberSinceText;
    private TextView lastLoginText;
    private TextView roleText;

    private TextInputEditText displayNameInput;
    private TextInputEditText phoneInput;
    private AutoCompleteTextView departmentSpinner;
    private AutoCompleteTextView campusSpinner;

    private TextInputLayout displayNameLayout;
    private TextInputLayout phoneLayout;
    private TextInputLayout departmentLayout;
    private TextInputLayout campusLayout;

    private MaterialButton editNameButton;
    private MaterialButton editPhoneButton;
    private MaterialButton editDepartmentButton;
    private MaterialButton editCampusButton;
    private MaterialButton changePasswordButton;
    private MaterialButton changeEmailButton;
    private MaterialButton deleteAccountButton;
    private MaterialButton logoutButton;

    private ProgressBar progressBar;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;

    // Data
    private List<String> campusList = new ArrayList<>();
    private String[] departments = {
            "Education Department",
            "Industrial Technology Department",
            "Hospitality Management Department",
            "Business Administration Department",
            "Information Technology Department"
    };

    // User Data
    private String currentDisplayName = "";
    private String currentPhone = "";
    private String currentDepartment = "";
    private String currentCampus = "";
    private String currentRole = "USER";
    private long createdAt = 0;
    private long lastLogin = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        // Setup toolbar
        setupToolbar();

        // Initialize views
        initializeViews();

        // Setup click listeners
        setupClickListeners();

        // Load campus data
        loadCampusData();

        // Load user profile
        loadUserProfile();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("My Profile");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void initializeViews() {
        // TextView fields (non-editable)
        emailText = findViewById(R.id.emailText);
        userIdText = findViewById(R.id.userIdText);
        memberSinceText = findViewById(R.id.memberSinceText);
        lastLoginText = findViewById(R.id.lastLoginText);
        roleText = findViewById(R.id.roleText);

        // EditText fields
        displayNameInput = findViewById(R.id.displayNameInput);
        phoneInput = findViewById(R.id.phoneInput);
        departmentSpinner = findViewById(R.id.departmentSpinner);
        campusSpinner = findViewById(R.id.campusSpinner);

        // TextInputLayouts
        displayNameLayout = findViewById(R.id.displayNameLayout);
        phoneLayout = findViewById(R.id.phoneLayout);
        departmentLayout = findViewById(R.id.departmentLayout);
        campusLayout = findViewById(R.id.campusLayout);

        // Buttons
        editNameButton = findViewById(R.id.editNameButton);
        editPhoneButton = findViewById(R.id.editPhoneButton);
        editDepartmentButton = findViewById(R.id.editDepartmentButton);
        editCampusButton = findViewById(R.id.editCampusButton);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        changeEmailButton = findViewById(R.id.changeEmailButton);
        deleteAccountButton = findViewById(R.id.deleteAccountButton);
        logoutButton = findViewById(R.id.logoutButton);

        progressBar = findViewById(R.id.progressBar);

        // Initially disable all input fields
        setFieldsEditable(false);

        // Setup department dropdown
        ArrayAdapter<String> deptAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, departments);
        departmentSpinner.setAdapter(deptAdapter);
    }

    private void setupClickListeners() {
        // Edit Name
        editNameButton.setOnClickListener(v -> {
            if (editNameButton.getText().toString().equals("Edit")) {
                enableNameEdit();
            } else {
                saveDisplayName();
            }
        });

        // Edit Phone
        editPhoneButton.setOnClickListener(v -> {
            if (editPhoneButton.getText().toString().equals("Edit")) {
                enablePhoneEdit();
            } else {
                savePhone();
            }
        });

        // Edit Department
        editDepartmentButton.setOnClickListener(v -> {
            if (editDepartmentButton.getText().toString().equals("Edit")) {
                enableDepartmentEdit();
            } else {
                saveDepartment();
            }
        });

        // Edit Campus
        editCampusButton.setOnClickListener(v -> {
            if (editCampusButton.getText().toString().equals("Edit")) {
                enableCampusEdit();
            } else {
                saveCampus();
            }
        });

        // Change Password
        changePasswordButton.setOnClickListener(v -> showChangePasswordDialog());

        // Change Email
        changeEmailButton.setOnClickListener(v -> showChangeEmailDialog());

        // Delete Account
        deleteAccountButton.setOnClickListener(v -> showDeleteAccountDialog());

        // Logout
        logoutButton.setOnClickListener(v -> showLogoutDialog());
    }

    private void loadCampusData() {
        campusList.clear();
        campusList.add("Meneses Campus");

        // Setup campus dropdown with only Meneses Campus
        ArrayAdapter<String> campusAdapter = new ArrayAdapter<>(ProfileActivity.this,
                android.R.layout.simple_dropdown_item_1line, campusList);
        campusSpinner.setAdapter(campusAdapter);

        // If user doesn't have campus set, default to Meneses
        if (currentCampus == null || currentCampus.isEmpty()) {
            currentCampus = "Meneses Campus";
        }

        campusSpinner.setText(currentCampus, false);
    }

    private void loadUserProfile() {
        if (currentUser == null) return;

        showProgress(true);

        // Display email and UID immediately
        emailText.setText(currentUser.getEmail());
        userIdText.setText("ID: " + currentUser.getUid().substring(0, 8) + "...");

        // Load user data from database
        mDatabase.child("users").child(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Get user data
                            currentDisplayName = snapshot.child("displayName").getValue(String.class);
                            currentPhone = snapshot.child("phone").getValue(String.class);
                            currentDepartment = snapshot.child("department").getValue(String.class);
                            currentCampus = snapshot.child("campus").getValue(String.class);
                            currentRole = snapshot.child("role").getValue(String.class);

                            Long created = snapshot.child("createdAt").getValue(Long.class);
                            createdAt = created != null ? created : 0L;

                            Long login = snapshot.child("lastLogin").getValue(Long.class);
                            lastLogin = login != null ? login : 0L;

                            // Update UI
                            updateUI();
                        }
                        showProgress(false);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load user profile", error.toException());
                        showToast("Failed to load profile data");
                        showProgress(false);
                    }
                });
    }

    private void updateUI() {
        // Set current values
        displayNameInput.setText(currentDisplayName);
        phoneInput.setText(currentPhone);

        // Set role
        if (currentRole != null) {
            roleText.setText("Role: " + currentRole);
            if ("ADMIN".equalsIgnoreCase(currentRole)) {
                roleText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));

                // Hide department and campus fields for ADMIN
                hideDepartmentAndCampusFields();
            } else {
                roleText.setTextColor(getResources().getColor(android.R.color.darker_gray));

                // Show and set department and campus for normal users
                showDepartmentAndCampusFields();
                departmentSpinner.setText(currentDepartment, false);
                campusSpinner.setText(currentCampus, false);
            }
        }

        // Set dates
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        if (createdAt > 0) {
            memberSinceText.setText("Member since: " + dateFormat.format(new Date(createdAt)));
        }
        if (lastLogin > 0) {
            lastLoginText.setText("Last login: " + dateFormat.format(new Date(lastLogin)));
        }
    }

    private void hideDepartmentAndCampusFields() {
        // Find the parent layout containers for department and campus
        View departmentContainer = findViewById(R.id.departmentContainer);
        View campusContainer = findViewById(R.id.campusContainer);

        if (departmentContainer != null) {
            departmentContainer.setVisibility(View.GONE);
        } else {
            // Fallback if container IDs don't exist
            departmentLayout.setVisibility(View.GONE);
            editDepartmentButton.setVisibility(View.GONE);
        }

        if (campusContainer != null) {
            campusContainer.setVisibility(View.GONE);
        } else {
            // Fallback if container IDs don't exist
            campusLayout.setVisibility(View.GONE);
            editCampusButton.setVisibility(View.GONE);
        }
    }

    private void showDepartmentAndCampusFields() {
        // Find the parent layout containers for department and campus
        View departmentContainer = findViewById(R.id.departmentContainer);
        View campusContainer = findViewById(R.id.campusContainer);

        if (departmentContainer != null) {
            departmentContainer.setVisibility(View.VISIBLE);
        } else {
            // Fallback if container IDs don't exist
            departmentLayout.setVisibility(View.VISIBLE);
            editDepartmentButton.setVisibility(View.VISIBLE);
        }

        if (campusContainer != null) {
            campusContainer.setVisibility(View.VISIBLE);
        } else {
            // Fallback if container IDs don't exist
            campusLayout.setVisibility(View.VISIBLE);
            editCampusButton.setVisibility(View.VISIBLE);
        }
    }

    private void setFieldsEditable(boolean editable) {
        displayNameInput.setEnabled(false);
        phoneInput.setEnabled(false);
        departmentSpinner.setEnabled(false);
        campusSpinner.setEnabled(false);
    }

    // Enable individual field editing
    private void enableNameEdit() {
        displayNameInput.setEnabled(true);
        displayNameInput.requestFocus();
        displayNameInput.setSelection(displayNameInput.getText().length());
        editNameButton.setText("Save");
        editNameButton.setIconResource(R.drawable.ic_save);
    }

    private void enablePhoneEdit() {
        phoneInput.setEnabled(true);
        phoneInput.requestFocus();
        phoneInput.setSelection(phoneInput.getText().length());
        editPhoneButton.setText("Save");
        editPhoneButton.setIconResource(R.drawable.ic_save);
    }

    private void enableDepartmentEdit() {
        departmentSpinner.setEnabled(true);
        departmentSpinner.showDropDown();
        editDepartmentButton.setText("Save");
        editDepartmentButton.setIconResource(R.drawable.ic_save);
    }

    private void enableCampusEdit() {
        campusSpinner.setEnabled(true);
        campusSpinner.showDropDown();
        editCampusButton.setText("Save");
        editCampusButton.setIconResource(R.drawable.ic_save);
    }

    // Save individual fields
    private void saveDisplayName() {
        String newName = displayNameInput.getText().toString().trim();

        if (TextUtils.isEmpty(newName)) {
            displayNameLayout.setError("Name cannot be empty");
            return;
        }

        showProgress(true);
        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", newName);

        mDatabase.child("users").child(currentUser.getUid()).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    currentDisplayName = newName;
                    displayNameInput.setEnabled(false);
                    editNameButton.setText("Edit");
                    editNameButton.setIconResource(R.drawable.ic_edit);
                    showToast("Name updated successfully");
                    showProgress(false);
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to update name: " + e.getMessage());
                    showProgress(false);
                });
    }

    private void savePhone() {
        String newPhone = phoneInput.getText().toString().trim();

        if (TextUtils.isEmpty(newPhone)) {
            phoneLayout.setError("Phone cannot be empty");
            return;
        }

        showProgress(true);
        Map<String, Object> updates = new HashMap<>();
        updates.put("phone", newPhone);

        mDatabase.child("users").child(currentUser.getUid()).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    currentPhone = newPhone;
                    phoneInput.setEnabled(false);
                    editPhoneButton.setText("Edit");
                    editPhoneButton.setIconResource(R.drawable.ic_edit);
                    showToast("Phone updated successfully");
                    showProgress(false);
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to update phone: " + e.getMessage());
                    showProgress(false);
                });
    }

    private void saveDepartment() {
        String newDepartment = departmentSpinner.getText().toString().trim();

        if (TextUtils.isEmpty(newDepartment)) {
            departmentLayout.setError("Please select a department");
            return;
        }

        showProgress(true);
        Map<String, Object> updates = new HashMap<>();
        updates.put("department", newDepartment);

        mDatabase.child("users").child(currentUser.getUid()).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    currentDepartment = newDepartment;
                    departmentSpinner.setEnabled(false);
                    editDepartmentButton.setText("Edit");
                    editDepartmentButton.setIconResource(R.drawable.ic_edit);
                    showToast("Department updated successfully");
                    showProgress(false);
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to update department: " + e.getMessage());
                    showProgress(false);
                });
    }

    private void saveCampus() {
        String newCampus = campusSpinner.getText().toString().trim();

        if (TextUtils.isEmpty(newCampus)) {
            campusLayout.setError("Please select a campus");
            return;
        }

        showProgress(true);
        Map<String, Object> updates = new HashMap<>();
        updates.put("campus", newCampus);

        mDatabase.child("users").child(currentUser.getUid()).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    currentCampus = newCampus;
                    campusSpinner.setEnabled(false);
                    editCampusButton.setText("Edit");
                    editCampusButton.setIconResource(R.drawable.ic_edit);
                    showToast("Campus updated successfully");
                    showProgress(false);
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to update campus: " + e.getMessage());
                    showProgress(false);
                });
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        builder.setView(dialogView);

        TextInputEditText currentPasswordInput = dialogView.findViewById(R.id.currentPasswordInput);
        TextInputEditText newPasswordInput = dialogView.findViewById(R.id.newPasswordInput);
        TextInputEditText confirmPasswordInput = dialogView.findViewById(R.id.confirmPasswordInput);

        builder.setTitle("Change Password");
        builder.setPositiveButton("Change", null);
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String currentPassword = currentPasswordInput.getText().toString();
                String newPassword = newPasswordInput.getText().toString();
                String confirmPassword = confirmPasswordInput.getText().toString();

                if (validatePasswordChange(currentPassword, newPassword, confirmPassword)) {
                    changePassword(currentPassword, newPassword, dialog);
                }
            });
        });

        dialog.show();
    }

    private boolean validatePasswordChange(String current, String newPass, String confirm) {
        if (TextUtils.isEmpty(current)) {
            showToast("Current password is required");
            return false;
        }
        if (TextUtils.isEmpty(newPass)) {
            showToast("New password is required");
            return false;
        }
        if (newPass.length() < 6) {
            showToast("Password must be at least 6 characters");
            return false;
        }
        if (!newPass.equals(confirm)) {
            showToast("Passwords do not match");
            return false;
        }
        return true;
    }

    private void changePassword(String currentPassword, String newPassword, AlertDialog dialog) {
        showProgress(true);

        // Re-authenticate user
        AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword);

        currentUser.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    // Now change password
                    currentUser.updatePassword(newPassword)
                            .addOnSuccessListener(aVoid1 -> {
                                showToast("Password changed successfully");
                                dialog.dismiss();
                                showProgress(false);
                            })
                            .addOnFailureListener(e -> {
                                showToast("Failed to change password: " + e.getMessage());
                                showProgress(false);
                            });
                })
                .addOnFailureListener(e -> {
                    showToast("Current password is incorrect");
                    showProgress(false);
                });
    }

    private void showChangeEmailDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_email, null);
        builder.setView(dialogView);

        TextInputEditText newEmailInput = dialogView.findViewById(R.id.newEmailInput);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.passwordInput);

        builder.setTitle("Change Email");
        builder.setPositiveButton("Change", null);
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String newEmail = newEmailInput.getText().toString().trim();
                String password = passwordInput.getText().toString();

                if (validateEmailChange(newEmail, password)) {
                    changeEmail(newEmail, password, dialog);
                }
            });
        });

        dialog.show();
    }

    private boolean validateEmailChange(String email, String password) {
        if (TextUtils.isEmpty(email)) {
            showToast("Email is required");
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast("Invalid email address");
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            showToast("Password is required");
            return false;
        }
        return true;
    }

    private void changeEmail(String newEmail, String password, AlertDialog dialog) {
        // Validate email format first
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            showToast("Please enter a valid email address");
            return;
        }

        // Check if new email is same as current
        if (newEmail.equals(currentUser.getEmail())) {
            showToast("New email is same as current email");
            return;
        }

        showProgress(true);

        // Re-authenticate user
        AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), password);

        currentUser.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    // Verify email is not already in use before updating
                    currentUser.verifyBeforeUpdateEmail(newEmail)
                            .addOnSuccessListener(aVoid1 -> {
                                // Update email in database
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("email", newEmail);

                                mDatabase.child("users").child(currentUser.getUid()).updateChildren(updates)
                                        .addOnSuccessListener(aVoid2 -> {
                                            emailText.setText(newEmail);
                                            showToast("Verification email sent. Please verify your new email.");
                                            dialog.dismiss();
                                            showProgress(false);
                                        })
                                        .addOnFailureListener(e -> {
                                            showToast("Failed to update database: " + e.getMessage());
                                            showProgress(false);
                                        });
                            })
                            .addOnFailureListener(e -> {
                                showProgress(false);
                                String errorMessage = e.getMessage();

                                // Handle specific error cases
                                if (errorMessage != null) {
                                    if (errorMessage.contains("operation-not-allowed")) {
                                        showToast("Email change is not enabled. Please contact support.");
                                    } else if (errorMessage.contains("email-already-in-use")) {
                                        showToast("This email is already in use by another account");
                                    } else if (errorMessage.contains("invalid-email")) {
                                        showToast("Invalid email address format");
                                    } else if (errorMessage.contains("requires-recent-login")) {
                                        showToast("Please sign out and sign in again, then try changing email");
                                    } else {
                                        showToast("Failed to change email: " + errorMessage);
                                    }
                                } else {
                                    showToast("Failed to change email. Please try again.");
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    if (e.getMessage() != null && e.getMessage().contains("wrong-password")) {
                        showToast("Incorrect password");
                    } else {
                        showToast("Authentication failed: " + e.getMessage());
                    }
                });
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Show password confirmation dialog
                    showPasswordConfirmationForDelete();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPasswordConfirmationForDelete() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_password, null);
        builder.setView(dialogView);

        TextInputEditText passwordInput = dialogView.findViewById(R.id.passwordInput);

        builder.setTitle("Confirm Account Deletion");
        builder.setMessage("Enter your password to delete your account");
        builder.setPositiveButton("Delete Account", null);
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String password = passwordInput.getText().toString();

                if (!TextUtils.isEmpty(password)) {
                    deleteAccount(password);
                    dialog.dismiss();
                } else {
                    showToast("Password is required");
                }
            });
        });

        dialog.show();
    }

    private void deleteAccount(String password) {
        showProgress(true);

        // Re-authenticate user
        AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), password);

        currentUser.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    // Delete user data from database
                    mDatabase.child("users").child(currentUser.getUid()).removeValue()
                            .addOnSuccessListener(aVoid1 -> {
                                // Delete authentication account
                                currentUser.delete()
                                        .addOnSuccessListener(aVoid2 -> {
                                            showToast("Account deleted successfully");
                                            clearPreferencesAndLogout();
                                        })
                                        .addOnFailureListener(e -> {
                                            showToast("Failed to delete account: " + e.getMessage());
                                            showProgress(false);
                                        });
                            });
                })
                .addOnFailureListener(e -> {
                    showToast("Password is incorrect");
                    showProgress(false);
                });
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    mAuth.signOut();
                    clearPreferencesAndLogout();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearPreferencesAndLogout() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

        redirectToLogin();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, Login.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}