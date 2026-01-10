package com.fixer.app;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Login extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "FixerPrefs";
    private static final String PREF_EMAIL = "email";
    private static final String PREF_REMEMBER = "remember";
    private static final String PREF_USER_ROLE = "user_role";

    // Login attempt security constants
    private static final String PREF_LOGIN_ATTEMPTS = "login_attempts";
    private static final String PREF_LOCKOUT_TIME = "lockout_time";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION = TimeUnit.MINUTES.toMillis(15); // 15 minutes lockout

    // UI Components
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private TextInputLayout emailInputLayout;
    private TextInputLayout passwordInputLayout;
    private MaterialButton loginButton;
    private MaterialButton registerButton;
    private MaterialCheckBox rememberMeCheckbox;
    private TextView forgotPasswordText;
    private ProgressBar progressBar;
    private TextView lockoutMessageText;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Initialize UI components
        initializeViews();

        // Set up click listeners
        setupClickListeners();

        // Load saved credentials if remember me was checked
        loadSavedCredentials();

        // Add logo animation
        animateLogo();

        // Check if account is locked
        checkAccountLockout();
    }

    private void initializeViews() {
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        emailInputLayout = findViewById(R.id.emailInputLayout);
        passwordInputLayout = findViewById(R.id.passwordInputLayout);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registerButton);
        rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        progressBar = findViewById(R.id.progressBar);
        lockoutMessageText = findViewById(R.id.lockoutMessageText);
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> attemptLogin());
        registerButton.setOnClickListener(v -> openRequestAccessActivity());
        forgotPasswordText.setOnClickListener(v -> handleForgotPassword());
    }

    private void animateLogo() {
        View logoView = findViewById(R.id.logoImageView);
        if (logoView != null) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(logoView, "scaleX", 0.8f, 1.0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(logoView, "scaleY", 0.8f, 1.0f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(logoView, "alpha", 0.0f, 1.0f);

            scaleX.setDuration(1000);
            scaleY.setDuration(1000);
            alpha.setDuration(1000);

            scaleX.setInterpolator(new DecelerateInterpolator());
            scaleY.setInterpolator(new DecelerateInterpolator());
            alpha.setInterpolator(new DecelerateInterpolator());

            scaleX.start();
            scaleY.start();
            alpha.start();
        }
    }

    private void checkAccountLockout() {
        long lockoutTime = sharedPreferences.getLong(PREF_LOCKOUT_TIME, 0);
        long currentTime = System.currentTimeMillis();

        if (lockoutTime > currentTime) {
            // Account is still locked
            long remainingTime = lockoutTime - currentTime;
            disableLoginWithLockout(remainingTime);
        } else if (lockoutTime > 0 && lockoutTime <= currentTime) {
            // Lockout period has expired, reset attempts
            resetLoginAttempts();
        }
    }

    private void disableLoginWithLockout(long remainingTime) {
        loginButton.setEnabled(false);
        emailEditText.setEnabled(false);
        passwordEditText.setEnabled(false);

        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime) % 60;

        String lockoutMessage = String.format("Account locked due to too many failed login attempts.\nPlease try again in %d minutes and %d seconds.", minutes, seconds);

        if (lockoutMessageText != null) {
            lockoutMessageText.setText(lockoutMessage);
            lockoutMessageText.setVisibility(View.VISIBLE);
        } else {
            showToast(lockoutMessage);
        }

        // Schedule re-check after 1 second
        loginButton.postDelayed(this::checkAccountLockout, 1000);
    }

    private void resetLoginAttempts() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(PREF_LOGIN_ATTEMPTS);
        editor.remove(PREF_LOCKOUT_TIME);
        editor.apply();

        loginButton.setEnabled(true);
        emailEditText.setEnabled(true);
        passwordEditText.setEnabled(true);

        if (lockoutMessageText != null) {
            lockoutMessageText.setVisibility(View.GONE);
        }
    }

    private void incrementLoginAttempts() {
        int attempts = sharedPreferences.getInt(PREF_LOGIN_ATTEMPTS, 0) + 1;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(PREF_LOGIN_ATTEMPTS, attempts);
        editor.apply();

        int remainingAttempts = MAX_LOGIN_ATTEMPTS - attempts;

        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            // Lock the account
            long lockoutTime = System.currentTimeMillis() + LOCKOUT_DURATION;
            editor.putLong(PREF_LOCKOUT_TIME, lockoutTime);
            editor.apply();

            disableLoginWithLockout(LOCKOUT_DURATION);

            // Also log this security event to Firebase
            logSecurityEvent("account_locked", "Too many failed login attempts");
        } else if (remainingAttempts <= 2) {
            // Warn user about remaining attempts
            showToast(String.format("Invalid credentials. %d attempt(s) remaining before account lockout.", remainingAttempts));
        } else {
            showToast("Invalid credentials. Please try again.");
        }
    }

    private void logSecurityEvent(String eventType, String details) {
        String email = emailEditText.getText().toString().trim();
        if (TextUtils.isEmpty(email)) return;

        Map<String, Object> securityEvent = new HashMap<>();
        securityEvent.put("email", email);
        securityEvent.put("eventType", eventType);
        securityEvent.put("details", details);
        securityEvent.put("timestamp", System.currentTimeMillis());
        securityEvent.put("deviceInfo", android.os.Build.MODEL);

        mDatabase.child("security_events").push().setValue(securityEvent);
    }

    private void loadSavedCredentials() {
        boolean rememberMe = sharedPreferences.getBoolean(PREF_REMEMBER, false);
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (rememberMe && currentUser != null) {
            // User is signed in and wants to be remembered
            String savedEmail = sharedPreferences.getString(PREF_EMAIL, "");
            emailEditText.setText(savedEmail);
            rememberMeCheckbox.setChecked(true);

            // Check if we have a saved role in SharedPreferences for quick redirect
            String savedRole = sharedPreferences.getString(PREF_USER_ROLE, null);

            if (savedRole != null) {
                // We have a saved role, redirect immediately based on it
                UserRole role = UserRole.fromString(savedRole);
                redirectBasedOnRole(role);

                // Still verify with Firebase in background to ensure user is still approved
                verifyUserStatusInBackground(currentUser);
            } else {
                // No saved role, need to check Firebase
                checkEmailVerificationAndStatus(currentUser);
            }
        } else if (!rememberMe && currentUser != null) {
            // User didn't want to be remembered, so log them out
            mAuth.signOut();
            clearSavedCredentials();
        }
    }

    private void verifyUserStatusInBackground(FirebaseUser user) {
        if (user == null) return;

        DatabaseReference userRef = mDatabase.child("users").child(user.getUid());
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    User userData = snapshot.getValue(User.class);
                    if (userData != null) {
                        if (!userData.isApproved() || !userData.isRoleActive()) {
                            // User is no longer approved or active, sign them out
                            mAuth.signOut();
                            clearSavedCredentials();

                            // Redirect back to login
                            Intent intent = new Intent(Login.this, Login.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);

                            if (!userData.isApproved()) {
                                showToast("Your account is no longer approved. Please contact administrator.");
                            } else {
                                showToast("Your account has been deactivated. Please contact administrator.");
                            }
                        } else {
                            // Update the saved role if it changed
                            saveUserRole(userData.getRole());
                            updateLastLogin(user.getUid());
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "verifyUserStatusInBackground:cancelled", error.toException());
            }
        });
    }

    private void attemptLogin() {
        // Check if account is locked
        long lockoutTime = sharedPreferences.getLong(PREF_LOCKOUT_TIME, 0);
        if (lockoutTime > System.currentTimeMillis()) {
            long remainingTime = lockoutTime - System.currentTimeMillis();
            long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime);
            showToast(String.format("Account is locked. Please try again in %d minutes.", minutes));
            return;
        }

        // Reset error states
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);

        // Get input values
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Validate inputs
        if (!validateInputs(email, password)) {
            return;
        }

        // Show progress
        showProgress(true);

        // Attempt Firebase authentication
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        showProgress(false);

                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();

                            // Reset login attempts on successful login
                            resetLoginAttempts();

                            // Log successful login
                            logSecurityEvent("login_success", "User logged in successfully");

                            // Save credentials if remember me is checked
                            saveCredentialsIfNeeded(email);

                            // Check email verification first, then user approval and role
                            checkEmailVerificationAndStatus(user);

                        } else {
                            Log.w(TAG, "signInWithEmail:failure", task.getException());

                            // Increment failed login attempts
                            incrementLoginAttempts();

                            // Log failed login attempt
                            logSecurityEvent("login_failed", "Invalid credentials");

                            handleLoginFailure(task.getException());
                        }
                    }
                });
    }

    private void checkEmailVerificationAndStatus(FirebaseUser user) {
        if (user == null) return;

        // Reload user to get latest email verification status
        user.reload().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (user.isEmailVerified()) {
                    // Email is verified, proceed to check approval and role
                    checkUserApprovalAndRole(user);
                } else {
                    // Email not verified
                    showEmailNotVerifiedDialog(user);
                }
            } else {
                Log.e(TAG, "Failed to reload user", task.getException());
                showToast("Failed to verify email status. Please try again.");
                mAuth.signOut();
                clearSavedCredentials();
            }
        });
    }

    private void showEmailNotVerifiedDialog(FirebaseUser user) {
        new AlertDialog.Builder(this)
                .setTitle("Email Not Verified")
                .setMessage("Your email address has not been verified yet. Please check your inbox for the verification email.\n\nWould you like us to resend the verification email?")
                .setPositiveButton("Resend Email", (dialog, which) -> {
                    resendVerificationEmail(user);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    mAuth.signOut();
                    clearSavedCredentials();
                })
                .setCancelable(false)
                .show();
    }

    private void resendVerificationEmail(FirebaseUser user) {
        if (user == null) return;

        showProgress(true);
        user.sendEmailVerification()
                .addOnSuccessListener(aVoid -> {
                    showProgress(false);
                    showToast("Verification email sent successfully. Please check your inbox.");
                    mAuth.signOut();
                    clearSavedCredentials();
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Log.e(TAG, "Failed to send verification email", e);
                    showToast("Failed to send verification email: " + e.getMessage());
                    mAuth.signOut();
                    clearSavedCredentials();
                });
    }

    private boolean validateInputs(String email, String password) {
        boolean valid = true;

        // Validate email
        if (TextUtils.isEmpty(email)) {
            emailInputLayout.setError("Email is required");
            valid = false;
        } else if (!isValidEmail(email)) {
            emailInputLayout.setError("Please enter a valid email address");
            valid = false;
        }

        // Validate password
        if (TextUtils.isEmpty(password)) {
            passwordInputLayout.setError("Password is required");
            valid = false;
        } else if (password.length() < 6) {
            passwordInputLayout.setError("Password must be at least 6 characters");
            valid = false;
        }

        return valid;
    }

    private boolean isValidEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void checkUserApprovalAndRole(FirebaseUser user) {
        if (user == null) return;

        DatabaseReference userRef = mDatabase.child("users").child(user.getUid());
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    User userData = snapshot.getValue(User.class);
                    if (userData != null) {
                        if (userData.isApproved()) {
                            // Check if user role is active
                            if (userData.isRoleActive()) {
                                // Save user role to SharedPreferences
                                saveUserRole(userData.getRole());

                                // Update last login
                                updateLastLogin(user.getUid());

                                // Redirect to appropriate dashboard based on role
                                redirectBasedOnRole(userData.getRole());
                            } else {
                                showToast("Your account has been deactivated. Please contact administrator.");
                                mAuth.signOut();
                                clearSavedCredentials();
                            }
                        } else {
                            // User not approved yet
                            showToast("Your account is pending approval. Please wait for administrator approval.");
                            mAuth.signOut();
                            clearSavedCredentials();
                        }
                    }
                } else {
                    // User data doesn't exist, create it with default role
                    createUserProfile(user);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "checkUserApprovalAndRole:cancelled", error.toException());
                showToast("Failed to verify user status. Please try again.");
            }
        });
    }

    private void createUserProfile(FirebaseUser user) {
        User newUser = new User(
                user.getUid(),
                user.getEmail(),
                user.getDisplayName() != null ? user.getDisplayName() : "",
                false, // Not approved by default
                UserRole.USER, // Default role
                true, // Role active by default
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        mDatabase.child("users").child(user.getUid()).setValue(newUser)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        showToast("Account created successfully. Please wait for administrator approval.");
                        mAuth.signOut();
                        clearSavedCredentials();
                    } else {
                        Log.w(TAG, "createUserProfile:failure", task.getException());
                        showToast("Failed to create user profile. Please try again.");
                    }
                });
    }

    private void updateLastLogin(String userId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastLogin", System.currentTimeMillis());

        mDatabase.child("users").child(userId).updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Last login updated successfully");
                    }
                });
    }

    private void saveCredentialsIfNeeded(String email) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if (rememberMeCheckbox.isChecked()) {
            editor.putString(PREF_EMAIL, email);
            editor.putBoolean(PREF_REMEMBER, true);
        } else {
            editor.remove(PREF_EMAIL);
            editor.remove(PREF_USER_ROLE);
            editor.putBoolean(PREF_REMEMBER, false);
        }

        editor.apply();
    }

    private void saveUserRole(UserRole role) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PREF_USER_ROLE, role.name());
        editor.apply();
    }

    private void clearSavedCredentials() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(PREF_EMAIL);
        editor.remove(PREF_USER_ROLE);
        editor.putBoolean(PREF_REMEMBER, false);
        editor.apply();
    }

    private void redirectBasedOnRole(UserRole role) {
        Intent intent;

        switch (role) {
            case ADMIN:
                intent = new Intent(this, Admin.class);
                break;
            case TECHNICIAN:
                intent = new Intent(this, TechnicianDashboardActivity.class);
                break;
            case USER:
            default:
                intent = new Intent(this, MainActivity.class);
                break;
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void redirectToDashboard() {
        // This method is deprecated - use redirectBasedOnRole instead
        // Check if we have a saved role
        String savedRole = sharedPreferences.getString(PREF_USER_ROLE, null);
        if (savedRole != null) {
            UserRole role = UserRole.fromString(savedRole);
            redirectBasedOnRole(role);
        } else {
            // Default to MainActivity if no role is saved
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void handleLoginFailure(Exception exception) {
        String errorMessage = "Login failed. Please check your credentials.";

        if (exception != null) {
            String exceptionMessage = exception.getMessage();
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("password")) {
                    errorMessage = "Invalid password. Please try again.";
                } else if (exceptionMessage.contains("email")) {
                    errorMessage = "Invalid email address.";
                } else if (exceptionMessage.contains("network")) {
                    errorMessage = "Network error. Please check your connection.";
                } else if (exceptionMessage.contains("user-not-found")) {
                    errorMessage = "No account found with this email.";
                }
            }
        }

        showToast(errorMessage);
    }

    // Updated handleForgotPassword method - now opens ForgotPasswordActivity
    private void handleForgotPassword() {
        String email = emailEditText.getText().toString().trim();

        Intent intent = new Intent(this, ForgotPasswordActivity.class);
        if (!TextUtils.isEmpty(email) && isValidEmail(email)) {
            intent.putExtra("email", email);
        }
        startActivity(intent);
    }

    private void openRequestAccessActivity() {
        Intent intent = new Intent(this, RequestAccessActivity.class);
        startActivity(intent);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!show);
        registerButton.setEnabled(!show);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Check if remember me is enabled
            boolean rememberMe = sharedPreferences.getBoolean(PREF_REMEMBER, false);
            if (rememberMe) {
                // User is signed in and wants to be remembered
                // Check if we have a saved role for quick redirect
                String savedRole = sharedPreferences.getString(PREF_USER_ROLE, null);
                if (savedRole != null) {
                    UserRole role = UserRole.fromString(savedRole);
                    redirectBasedOnRole(role);
                } else {
                    // No saved role, check email verification and user approval
                    checkEmailVerificationAndStatus(currentUser);
                }
            } else {
                // User didn't want to be remembered, sign them out
                mAuth.signOut();
                clearSavedCredentials();
            }
        }
    }

    // User Role Enum
    public enum UserRole {
        ADMIN("Admin"),
        TECHNICIAN("Technician"),
        USER("User");

        private final String displayName;

        UserRole(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static UserRole fromString(String role) {
            for (UserRole userRole : UserRole.values()) {
                if (userRole.name().equalsIgnoreCase(role)) {
                    return userRole;
                }
            }
            return USER; // Default to USER if not found
        }
    }

    // Enhanced User model class
    public static class User {
        public String uid;
        public String email;
        public String displayName;
        public boolean approved;
        public UserRole role;
        public boolean roleActive;
        public long createdAt;
        public long lastLogin;

        public User() {
            // Default constructor required for calls to DataSnapshot.getValue(User.class)
            this.role = UserRole.USER;
            this.roleActive = true;
        }

        public User(String uid, String email, String displayName, boolean approved,
                    UserRole role, boolean roleActive, long createdAt, long lastLogin) {
            this.uid = uid;
            this.email = email;
            this.displayName = displayName;
            this.approved = approved;
            this.role = role != null ? role : UserRole.USER;
            this.roleActive = roleActive;
            this.createdAt = createdAt;
            this.lastLogin = lastLogin;
        }

        // Getters
        public boolean isApproved() {
            return approved;
        }

        public UserRole getRole() {
            return role != null ? role : UserRole.USER;
        }

        public boolean isRoleActive() {
            return roleActive;
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