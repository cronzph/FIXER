package com.fixer.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.SignInMethodQueryResult;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPassword";

    // UI Components
    private TextInputEditText emailEditText;
    private TextInputLayout emailInputLayout;
    private MaterialButton sendResetButton;
    private MaterialButton backToLoginButton;
    private ProgressBar progressBar;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Setup toolbar
        setupToolbar();

        // Initialize UI components
        initializeViews();

        // Setup click listeners
        setupClickListeners();

        // Get email from intent if provided
        loadEmailFromIntent();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Reset Password");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void initializeViews() {
        emailEditText = findViewById(R.id.emailEditText);
        emailInputLayout = findViewById(R.id.emailInputLayout);
        sendResetButton = findViewById(R.id.sendResetButton);
        backToLoginButton = findViewById(R.id.backToLoginButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupClickListeners() {
        sendResetButton.setOnClickListener(v -> sendPasswordReset());
        backToLoginButton.setOnClickListener(v -> goBackToLogin());
    }

    private void loadEmailFromIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("email")) {
            String email = intent.getStringExtra("email");
            emailEditText.setText(email);
        }
    }

    private void sendPasswordReset() {
        // Clear previous errors
        emailInputLayout.setError(null);

        // Get email
        String email = emailEditText.getText().toString().trim();

        // Validate email
        if (!validateEmail(email)) {
            return;
        }

        showProgress(true);

        // Check if email exists in Firebase Authentication
        checkEmailExists(email, new UserExistsCallback() {
            @Override
            public void onResult(boolean exists) {
                showProgress(false);

                if (exists) {
                    // Email exists, proceed with password reset
                    sendResetEmail(email);
                } else {
                    // Email doesn't exist, show dialog
                    showEmailNotFoundDialog();
                }
            }
        });
    }

    private void checkEmailExists(String email, UserExistsCallback callback) {
        mAuth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(new OnCompleteListener<SignInMethodQueryResult>() {
                    @Override
                    public void onComplete(@NonNull Task<SignInMethodQueryResult> task) {
                        if (task.isSuccessful()) {
                            SignInMethodQueryResult result = task.getResult();
                            boolean exists = result != null &&
                                    result.getSignInMethods() != null &&
                                    !result.getSignInMethods().isEmpty();
                            callback.onResult(exists);
                        } else {
                            // If check fails, show error
                            showProgress(false);
                            emailInputLayout.setError("Unable to verify email. Please check your connection and try again.");
                            Log.e(TAG, "Error checking email existence", task.getException());
                        }
                    }
                });
    }

    private void sendResetEmail(String email) {
        showProgress(true);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        showProgress(false);

                        if (task.isSuccessful()) {
                            showSuccessMessage(email);
                        } else {
                            handleResetFailure(task.getException());
                        }
                    }
                });
    }

    private void showEmailNotFoundDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Email Not Found")
                .setMessage("No account exists with this email address. Please check your email or request access to create an account.")
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    emailEditText.requestFocus();
                })
                .setNegativeButton("Request Access", (dialog, which) -> {
                    dialog.dismiss();
                    // Navigate to Request Access activity
                    Intent intent = new Intent(ForgotPasswordActivity.this, RequestAccessActivity.class);
                    startActivity(intent);
                })
                .setCancelable(true)
                .show();
    }

    private boolean validateEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            emailInputLayout.setError("Email is required");
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.setError("Please enter a valid email address");
            return false;
        }

        return true;
    }

    private void showSuccessMessage(String email) {
        // Show success message
        showToast("Password reset email sent to " + email + ". Please check your inbox and follow the instructions.");

        // Optionally, go back to login after a delay or immediately
        goBackToLogin();
    }

    private void handleResetFailure(Exception exception) {
        String errorMessage = "Failed to send password reset email. Please try again.";

        if (exception != null) {
            String exceptionMessage = exception.getMessage();
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("user-not-found")) {
                    errorMessage = "No account found with this email address.";
                } else if (exceptionMessage.contains("invalid-email")) {
                    errorMessage = "Invalid email address.";
                } else if (exceptionMessage.contains("network")) {
                    errorMessage = "Network error. Please check your connection.";
                }
            }
        }

        emailInputLayout.setError(errorMessage);
    }

    private void goBackToLogin() {
        Intent intent = new Intent(this, Login.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        sendResetButton.setEnabled(!show);
        backToLoginButton.setEnabled(!show);
        emailEditText.setEnabled(!show);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // Callback interface for user existence check
    interface UserExistsCallback {
        void onResult(boolean exists);
    }
}