package com.fixer.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

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
import android.text.Editable;
import android.text.TextWatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestAccessActivity extends AppCompatActivity {

    private static final String TAG = "RequestAccessActivity";
    private static final String REQUIRED_EMAIL_DOMAIN = "@bulsu.edu.ph";

    // UI Components
    private TextInputEditText fullNameEditText;
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private TextInputEditText confirmPasswordEditText;
    private TextInputEditText phoneEditText;
    private AutoCompleteTextView roleDropdown;
    private AutoCompleteTextView departmentDropdown;
    private AutoCompleteTextView campusDropdown;

    private TextInputLayout fullNameInputLayout;
    private TextInputLayout emailInputLayout;
    private TextInputLayout passwordInputLayout;
    private TextInputLayout confirmPasswordInputLayout;
    private TextInputLayout phoneInputLayout;
    private TextInputLayout roleInputLayout;
    private TextInputLayout departmentInputLayout;
    private TextInputLayout campusInputLayout;

    private MaterialButton submitButton;
    private MaterialButton cancelButton;
    private ProgressBar progressBar;
    private ProgressBar campusProgressBar;

    // Terms and Conditions
    private MaterialCheckBox termsCheckBox;
    private TextView viewTermsTextView;
    private TextView termsErrorTextView;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    // Validation flags
    private boolean isFullNameValid = false;
    private boolean isEmailValid = false;
    private boolean isPasswordValid = false;
    private boolean isConfirmPasswordValid = false;
    private boolean isPhoneValid = false;
    private boolean isRoleValid = false;
    private boolean isDepartmentValid = false;
    private boolean isCampusValid = false;

    private String[] roles = {
            "Guard",
            "Facility Management/Janitor",
            "Faculty Member",
            "Technician"
    };

    private String[] departments = {
            "Education Department",
            "Industrial Technology Department",
            "Hospitality Management Department",
            "Business Administration Department",
            "Information Technology Department"
    };

    private List<String> campusList = new ArrayList<>();
    private ArrayAdapter<String> campusAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request_access);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize UI components
        initializeViews();

        // Disable all fields except the first one initially
        initializeFieldStates();

        // Set up click listeners
        setupClickListeners();

        // Setup dropdowns
        setupRoleDropdown();
        setupDepartmentDropdown();
        setupCampusDropdown();

        // Setup validations
        setupFullNameValidation();
        setupEmailValidation();
        setupPasswordValidation();
        setupConfirmPasswordValidation();
        setupPhoneValidation();
    }

    private void initializeFieldStates() {
        // Only enable the first field
        fullNameEditText.setEnabled(true);

        // Disable all other fields
        emailEditText.setEnabled(false);
        passwordEditText.setEnabled(false);
        confirmPasswordEditText.setEnabled(false);
        phoneEditText.setEnabled(false);
        roleDropdown.setEnabled(false);
        departmentDropdown.setEnabled(false);
        campusDropdown.setEnabled(false);

        // Set helper text for disabled fields
        emailInputLayout.setHelperText("Complete Full Name first");
        passwordInputLayout.setHelperText("Complete Email first");
        confirmPasswordInputLayout.setHelperText("Complete Password first");
        phoneInputLayout.setHelperText("Complete Confirm Password first");
        roleInputLayout.setHelperText("Complete Phone Number first");
        departmentInputLayout.setHelperText("Complete Role first");
        campusInputLayout.setHelperText("Complete Department first");
    }

    private void setupFullNameValidation() {
        fullNameEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String fullName = s.toString().trim();
                fullNameInputLayout.setError(null);

                if (fullName.isEmpty()) {
                    fullNameInputLayout.setHelperText("⚠ Full name is required");
                    fullNameInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(android.R.color.holo_red_dark)));
                    isFullNameValid = false;
                } else if (fullName.length() < 2) {
                    fullNameInputLayout.setHelperText("⚠ Please enter a valid full name");
                    fullNameInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(android.R.color.holo_red_dark)));
                    isFullNameValid = false;
                } else {
                    fullNameInputLayout.setHelperText("✓ Valid full name");
                    fullNameInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(android.R.color.holo_green_dark)));
                    isFullNameValid = true;
                }

                updateFieldStates();
            }
        });
    }

    private void setupEmailValidation() {
        emailEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String email = s.toString().trim();
                emailInputLayout.setError(null);

                if (email.isEmpty()) {
                    emailInputLayout.setHelperText("⚠ Email is required");
                    emailInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(android.R.color.holo_red_dark)));
                    isEmailValid = false;
                } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailInputLayout.setHelperText("⚠ Invalid email format");
                    emailInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(android.R.color.holo_red_dark)));
                    isEmailValid = false;
                } else if (!email.endsWith(REQUIRED_EMAIL_DOMAIN)) {
                    emailInputLayout.setHelperText("⚠ Must use @bulsu.edu.ph email");
                    emailInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(android.R.color.holo_red_dark)));
                    isEmailValid = false;
                } else {
                    emailInputLayout.setHelperText("✓ Valid BulSU email");
                    emailInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(android.R.color.holo_green_dark)));
                    isEmailValid = true;
                }

                updateFieldStates();
            }
        });
    }

    private void setupPasswordValidation() {
        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String password = s.toString();
                passwordInputLayout.setError(null);

                if (password.isEmpty()) {
                    passwordInputLayout.setHelperText("⚠ Password is required");
                    passwordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(android.R.color.holo_red_dark)));
                    isPasswordValid = false;
                } else if (password.length() < 6) {
                    passwordInputLayout.setHelperText("⚠ At least 6 characters required");
                    passwordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(android.R.color.holo_red_dark)));
                    isPasswordValid = false;
                } else {
                    boolean hasLetter = password.matches(".*[A-Za-z].*");
                    boolean hasDigit = password.matches(".*\\d.*");
                    boolean hasSpecial = password.matches(".*[@$!%*_,.#?&].*");

                    if (!hasLetter) {
                        passwordInputLayout.setHelperText("⚠ Must contain at least one letter");
                        passwordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                                getResources().getColor(android.R.color.holo_red_dark)));
                        isPasswordValid = false;
                    } else if (!hasDigit) {
                        passwordInputLayout.setHelperText("⚠ Must contain at least one number");
                        passwordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                                getResources().getColor(android.R.color.holo_red_dark)));
                        isPasswordValid = false;
                    } else if (!hasSpecial) {
                        passwordInputLayout.setHelperText("⚠ Must contain a special character (@$!%*_,.#?&)");
                        passwordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                                getResources().getColor(android.R.color.holo_red_dark)));
                        isPasswordValid = false;
                    } else {
                        passwordInputLayout.setHelperText("✓ Strong password");
                        passwordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                                getResources().getColor(android.R.color.holo_green_dark)));
                        isPasswordValid = true;
                    }
                }

                // Check confirm password match
                String confirmPassword = confirmPasswordEditText.getText().toString();
                if (!confirmPassword.isEmpty()) {
                    validateConfirmPassword(confirmPassword);
                }

                updateFieldStates();
            }
        });
    }

    private void setupConfirmPasswordValidation() {
        confirmPasswordEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String confirmPassword = s.toString();
                validateConfirmPassword(confirmPassword);
                updateFieldStates();
            }
        });
    }

    private void validateConfirmPassword(String confirmPassword) {
        String password = passwordEditText.getText().toString();
        confirmPasswordInputLayout.setError(null);

        if (confirmPassword.isEmpty()) {
            confirmPasswordInputLayout.setHelperText("⚠ Please confirm your password");
            confirmPasswordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(android.R.color.holo_red_dark)));
            isConfirmPasswordValid = false;
        } else if (!password.equals(confirmPassword)) {
            confirmPasswordInputLayout.setHelperText("⚠ Passwords do not match");
            confirmPasswordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(android.R.color.holo_red_dark)));
            isConfirmPasswordValid = false;
        } else {
            confirmPasswordInputLayout.setHelperText("✓ Passwords match");
            confirmPasswordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(android.R.color.holo_green_dark)));
            isConfirmPasswordValid = true;
        }
    }

    private void setupPhoneValidation() {
        phoneEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String phone = s.toString().trim();
                phoneInputLayout.setError(null);

                if (phone.isEmpty()) {
                    phoneInputLayout.setHelperText("⚠ Phone number is required");
                    phoneInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(android.R.color.holo_red_dark)));
                    isPhoneValid = false;
                } else {
                    String digitsOnly = phone.replaceAll("\\D", "");

                    if (digitsOnly.length() != 11) {
                        phoneInputLayout.setHelperText("⚠ Must be exactly 11 digits (e.g., 09XXXXXXXXX)");
                        phoneInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                                getResources().getColor(android.R.color.holo_red_dark)));
                        isPhoneValid = false;
                    } else if (!digitsOnly.startsWith("09")) {
                        phoneInputLayout.setHelperText("⚠ Must start with 09 (Philippine format)");
                        phoneInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                                getResources().getColor(android.R.color.holo_red_dark)));
                        isPhoneValid = false;
                    } else {
                        phoneInputLayout.setHelperText("✓ Valid Philippine phone number");
                        phoneInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                                getResources().getColor(android.R.color.holo_green_dark)));
                        isPhoneValid = true;
                    }
                }

                updateFieldStates();
            }
        });
    }

    private void updateFieldStates() {
        // Enable/disable fields based on previous field validation

        // Email field
        if (isFullNameValid) {
            emailEditText.setEnabled(true);
            // Don't override the real-time validation messages
            if (!emailEditText.isFocused() && emailEditText.getText().toString().isEmpty()) {
                emailInputLayout.setHelperText(null);
            }
        } else {
            emailEditText.setEnabled(false);
            if (!emailEditText.isFocused()) {
                emailInputLayout.setHelperText("Complete Full Name first");
                emailInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                        getResources().getColor(android.R.color.darker_gray)));
            }
        }

        // Password field
        if (isFullNameValid && isEmailValid) {
            passwordEditText.setEnabled(true);
            if (!passwordEditText.isFocused() && passwordEditText.getText().toString().isEmpty()) {
                passwordInputLayout.setHelperText(null);
            }
        } else {
            passwordEditText.setEnabled(false);
            if (!passwordEditText.isFocused()) {
                passwordInputLayout.setHelperText("Complete Email first");
                passwordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                        getResources().getColor(android.R.color.darker_gray)));
            }
        }

        // Confirm Password field
        if (isFullNameValid && isEmailValid && isPasswordValid) {
            confirmPasswordEditText.setEnabled(true);
            if (!confirmPasswordEditText.isFocused() && confirmPasswordEditText.getText().toString().isEmpty()) {
                confirmPasswordInputLayout.setHelperText(null);
            }
        } else {
            confirmPasswordEditText.setEnabled(false);
            if (!confirmPasswordEditText.isFocused()) {
                confirmPasswordInputLayout.setHelperText("Complete Password first");
                confirmPasswordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                        getResources().getColor(android.R.color.darker_gray)));
            }
        }

        // Phone field
        if (isFullNameValid && isEmailValid && isPasswordValid && isConfirmPasswordValid) {
            phoneEditText.setEnabled(true);
            if (!phoneEditText.isFocused() && phoneEditText.getText().toString().isEmpty()) {
                phoneInputLayout.setHelperText(null);
            }
        } else {
            phoneEditText.setEnabled(false);
            if (!phoneEditText.isFocused()) {
                phoneInputLayout.setHelperText("Complete Confirm Password first");
                phoneInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                        getResources().getColor(android.R.color.darker_gray)));
            }
        }

        // Role field
        if (isFullNameValid && isEmailValid && isPasswordValid && isConfirmPasswordValid && isPhoneValid) {
            roleDropdown.setEnabled(true);
            if (isRoleValid) {
                roleInputLayout.setHelperText("✓ Role selected");
                roleInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                        getResources().getColor(android.R.color.holo_green_dark)));
            } else if (roleDropdown.getText().toString().isEmpty()) {
                roleInputLayout.setHelperText(null);
            }
        } else {
            roleDropdown.setEnabled(false);
            roleInputLayout.setHelperText("Complete Phone Number first");
            roleInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(android.R.color.darker_gray)));
        }

        // Department field (conditional based on role)
        String selectedRole = roleDropdown.getText().toString().trim();
        if ("Technician".equals(selectedRole)) {
            departmentInputLayout.setVisibility(View.GONE);
            isDepartmentValid = true; // Auto-valid for technicians
        } else {
            departmentInputLayout.setVisibility(View.VISIBLE);
            if (isFullNameValid && isEmailValid && isPasswordValid && isConfirmPasswordValid && isPhoneValid && isRoleValid) {
                departmentDropdown.setEnabled(true);
                if (isDepartmentValid) {
                    departmentInputLayout.setHelperText("✓ Department selected");
                    departmentInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(android.R.color.holo_green_dark)));
                } else if (departmentDropdown.getText().toString().isEmpty()) {
                    departmentInputLayout.setHelperText(null);
                }
            } else {
                departmentDropdown.setEnabled(false);
                departmentInputLayout.setHelperText("Complete Role first");
                departmentInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                        getResources().getColor(android.R.color.darker_gray)));
            }
        }

        // Campus field
        if (isFullNameValid && isEmailValid && isPasswordValid && isConfirmPasswordValid && isPhoneValid && isRoleValid && isDepartmentValid) {
            campusDropdown.setEnabled(true);
            campusInputLayout.setHelperText("Default campus");
            campusInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(android.R.color.darker_gray)));
        } else {
            campusDropdown.setEnabled(false);
            if ("Technician".equals(selectedRole)) {
                campusInputLayout.setHelperText("Complete Role first");
            } else {
                campusInputLayout.setHelperText("Complete Department first");
            }
            campusInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(android.R.color.darker_gray)));
        }

        updateSubmitButtonState();
    }

    private void initializeViews() {
        fullNameEditText = findViewById(R.id.fullNameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        roleDropdown = findViewById(R.id.roleDropdown);
        departmentDropdown = findViewById(R.id.departmentDropdown);
        campusDropdown = findViewById(R.id.campusDropdown);

        fullNameInputLayout = findViewById(R.id.fullNameInputLayout);
        emailInputLayout = findViewById(R.id.emailInputLayout);
        passwordInputLayout = findViewById(R.id.passwordInputLayout);
        confirmPasswordInputLayout = findViewById(R.id.confirmPasswordInputLayout);
        phoneInputLayout = findViewById(R.id.phoneInputLayout);
        roleInputLayout = findViewById(R.id.roleInputLayout);
        departmentInputLayout = findViewById(R.id.departmentInputLayout);
        campusInputLayout = findViewById(R.id.campusInputLayout);

        submitButton = findViewById(R.id.submitButton);
        cancelButton = findViewById(R.id.cancelButton);
        progressBar = findViewById(R.id.progressBar);
        campusProgressBar = findViewById(R.id.campusProgressBar);

        termsCheckBox = findViewById(R.id.termsCheckBox);
        viewTermsTextView = findViewById(R.id.viewTermsTextView);
        termsErrorTextView = findViewById(R.id.termsErrorTextView);
    }

    private void setupClickListeners() {
        submitButton.setOnClickListener(v -> submitAccessRequest());
        cancelButton.setOnClickListener(v -> finish());

        viewTermsTextView.setOnClickListener(v -> showTermsAndConditions());

        termsCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                viewTermsTextView.setVisibility(View.GONE);
                termsErrorTextView.setVisibility(View.GONE);
            } else {
                termsErrorTextView.setVisibility(View.VISIBLE);
                viewTermsTextView.setVisibility(View.VISIBLE);
            }
            updateSubmitButtonState();
        });
    }

    private void setupRoleDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, roles);
        roleDropdown.setAdapter(adapter);

        roleDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedRole = roles[position];
            isRoleValid = true;

            if ("Technician".equals(selectedRole)) {
                departmentInputLayout.setVisibility(View.GONE);
                departmentDropdown.setText("", false);
                isDepartmentValid = true;
            } else {
                departmentInputLayout.setVisibility(View.VISIBLE);
                isDepartmentValid = false;
            }

            updateFieldStates();
        });
    }

    private void setupDepartmentDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, departments);
        departmentDropdown.setAdapter(adapter);

        departmentDropdown.setOnItemClickListener((parent, view, position, id) -> {
            isDepartmentValid = true;
            updateFieldStates();
        });
    }

    private void setupCampusDropdown() {
        campusList.clear();
        campusList.add("Meneses Campus");

        campusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, campusList);
        campusDropdown.setAdapter(campusAdapter);

        campusDropdown.setText("Meneses Campus", false);
        isCampusValid = true;

        campusProgressBar.setVisibility(View.GONE);
    }

    private void updateSubmitButtonState() {
        boolean allFieldsValid = isFullNameValid && isEmailValid && isPasswordValid &&
                isConfirmPasswordValid && isPhoneValid && isRoleValid &&
                isDepartmentValid && isCampusValid && termsCheckBox.isChecked();
        submitButton.setEnabled(allFieldsValid);
    }

    private void showTermsAndConditions() {
        Intent intent = new Intent(this, TermsAndConditionsActivity.class);
        startActivity(intent);
    }

    private void submitAccessRequest() {
        clearErrors();

        String fullName = fullNameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();
        String phone = phoneEditText.getText().toString().trim();
        String role = roleDropdown.getText().toString().trim();
        String department = departmentDropdown.getText().toString().trim();
        String campus = campusDropdown.getText().toString().trim();

        if (!validateInputs(fullName, email, password, confirmPassword, phone, role, department, campus)) {
            return;
        }

        showProgress(true);

        mDatabase.child("users").orderByChild("email").equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            showProgress(false);
                            showToast("This email is already registered");
                        } else {
                            createUserAccount(fullName, email, password, phone, role, department, campus);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showProgress(false);
                        Log.e(TAG, "Failed to check email", error.toException());
                        showToast("Failed to verify email: " + error.getMessage());
                    }
                });
    }

    private void createUserAccount(String fullName, String email, String password,
                                   String phone, String role, String department, String campus) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "createUserWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();

                            if (user != null) {
                                user.sendEmailVerification()
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "Verification email sent");
                                            createAccessRequest(user, fullName, email, phone, role, department, campus);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to send verification email", e);
                                            createAccessRequest(user, fullName, email, phone, role, department, campus);
                                        });
                            }
                        } else {
                            showProgress(false);
                            Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            handleRegistrationFailure(task.getException());
                        }
                    }
                });
    }

    private boolean validateInputs(String fullName, String email, String password,
                                   String confirmPassword, String phone, String role,
                                   String department, String campus) {
        boolean valid = true;

        if (TextUtils.isEmpty(fullName)) {
            fullNameInputLayout.setError("Full name is required");
            valid = false;
        } else if (fullName.length() < 2) {
            fullNameInputLayout.setError("Please enter a valid full name");
            valid = false;
        }

        if (TextUtils.isEmpty(email)) {
            emailInputLayout.setError("Email is required");
            valid = false;
        } else if (!isValidEmail(email)) {
            emailInputLayout.setError("Please enter a valid email address");
            valid = false;
        } else if (!isValidBulsuEmail(email)) {
            emailInputLayout.setError("Only @bulsu.edu.ph emails are allowed");
            valid = false;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInputLayout.setErrorEnabled(false);
            passwordInputLayout.setHelperText("⚠ Password is required");
            passwordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(android.R.color.holo_red_dark)));
            valid = false;
        } else if (password.length() < 6) {
            passwordInputLayout.setErrorEnabled(false);
            passwordInputLayout.setHelperText("⚠ Password must be at least 6 characters");
            passwordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(android.R.color.holo_red_dark)));
            valid = false;
        } else if (!isStrongPassword(password)) {
            passwordInputLayout.setErrorEnabled(false);
            passwordInputLayout.setHelperText("⚠ Password must contain letters, numbers, and special characters");
            passwordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(android.R.color.holo_red_dark)));
            valid = false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordInputLayout.setErrorEnabled(false);
            confirmPasswordInputLayout.setHelperText("⚠ Please confirm your password");
            confirmPasswordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(android.R.color.holo_red_dark)));
            valid = false;
        } else if (!password.equals(confirmPassword)) {
            confirmPasswordInputLayout.setErrorEnabled(false);
            confirmPasswordInputLayout.setHelperText("⚠ Passwords do not match");
            confirmPasswordInputLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(android.R.color.holo_red_dark)));
            valid = false;
        }

        if (TextUtils.isEmpty(phone)) {
            phoneInputLayout.setError("Phone number is required");
            valid = false;
        } else if (!isValidPhilippinePhone(phone)) {
            phoneInputLayout.setError("Please enter a valid 11-digit PH number (09XXXXXXXXX)");
            valid = false;
        }

        if (TextUtils.isEmpty(role)) {
            roleInputLayout.setError("Role is required");
            valid = false;
        }

        if (!"Technician".equals(role)) {
            if (TextUtils.isEmpty(department)) {
                departmentInputLayout.setError("Department is required");
                valid = false;
            }
        }

        if (TextUtils.isEmpty(campus)) {
            campusInputLayout.setError("Campus is required");
            valid = false;
        } else if (!campusList.contains(campus)) {
            campusInputLayout.setError("Please select a valid campus from the list");
            valid = false;
        }

        return valid;
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isValidBulsuEmail(String email) {
        return email.endsWith(REQUIRED_EMAIL_DOMAIN);
    }

    private boolean isStrongPassword(String password) {
        return password.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*_,.#?&])[A-Za-z\\d@$!%*#?&]{6,}$");
    }

    private boolean isValidPhilippinePhone(String phone) {
        String digitsOnly = phone.replaceAll("\\D", "");
        return digitsOnly.length() == 11 && digitsOnly.startsWith("09");
    }

    private String getParentRole(String roleVariant) {
        if ("Technician".equals(roleVariant)) {
            return "TECHNICIAN";
        } else {
            return "USER";
        }
    }

    private void createAccessRequest(FirebaseUser user, String fullName, String email,
                                     String phone, String roleVariant, String department, String campus) {
        if (user == null) {
            showProgress(false);
            showToast("Failed to create user account. Please try again.");
            return;
        }

        String parentRole = getParentRole(roleVariant);

        AccessRequest accessRequest = new AccessRequest(
                user.getUid(),
                fullName,
                email,
                phone,
                parentRole,
                roleVariant,
                "TECHNICIAN".equals(parentRole) ? null : department,
                campus,
                "pending",
                System.currentTimeMillis(),
                null,
                null,
                null
        );

        mDatabase.child("access_requests").child(user.getUid()).setValue(accessRequest)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        createUserProfile(user, fullName, email, phone, parentRole, roleVariant, department, campus);
                    } else {
                        showProgress(false);
                        Log.w(TAG, "createAccessRequest:failure", task.getException());
                        showToast("Failed to submit access request. Please try again.");
                    }
                });
    }

    private void createUserProfile(FirebaseUser user, String fullName, String email,
                                   String phone, String parentRole, String roleVariant,
                                   String department, String campus) {

        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("uid", user.getUid());
        userProfile.put("email", email);
        userProfile.put("displayName", fullName);
        userProfile.put("phone", phone);
        userProfile.put("role", parentRole);
        userProfile.put("roleVariant", roleVariant);

        if ("TECHNICIAN".equals(parentRole)) {
            userProfile.put("department", null);
        } else {
            userProfile.put("department", department);
        }

        userProfile.put("campus", campus);
        userProfile.put("approved", false);
        userProfile.put("roleActive", false);
        userProfile.put("createdAt", System.currentTimeMillis());
        userProfile.put("lastLogin", null);
        userProfile.put("status", "pending");

        mDatabase.child("users").child(user.getUid()).setValue(userProfile)
                .addOnCompleteListener(task -> {
                    showProgress(false);

                    if (task.isSuccessful()) {
                        Log.d(TAG, "User profile created successfully");
                        sendNotificationToAdmins(user.getUid(), fullName, email, roleVariant, campus);
                        mAuth.signOut();
                        showSuccessDialog();
                    } else {
                        Log.w(TAG, "createUserProfile:failure", task.getException());
                        showToast("Failed to create user profile. Please contact support.");
                    }
                });
    }

    private void sendNotificationToAdmins(String userId, String fullName, String email,
                                          String roleVariant, String campus) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "access_request");
        notification.put("userId", userId);
        notification.put("userName", fullName);
        notification.put("userEmail", email);
        notification.put("userRole", roleVariant);
        notification.put("userCampus", campus);
        notification.put("message", fullName + " (" + roleVariant + ") from " + campus +
                " campus has requested access to F.I.X.E.R");
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("read", false);

        mDatabase.child("admin_notifications").push().setValue(notification)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Admin notification sent successfully");
                    } else {
                        Log.w(TAG, "Failed to send admin notification", task.getException());
                    }
                });
    }

    private void showSuccessDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Access Request Submitted")
                .setMessage("Your access request has been submitted successfully. " +
                        "A verification email has been sent to your email address. " +
                        "Please verify your email and wait for admin approval.")
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    Intent intent = new Intent(this, Login.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void handleRegistrationFailure(Exception exception) {
        String errorMessage = "Failed to create account. Please try again.";

        if (exception != null) {
            String exceptionMessage = exception.getMessage();
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("email")) {
                    errorMessage = "This email address is already registered.";
                } else if (exceptionMessage.contains("password")) {
                    errorMessage = "Password is too weak. Please use a stronger password.";
                } else if (exceptionMessage.contains("network")) {
                    errorMessage = "Network error. Please check your connection.";
                }
            }
        }

        showToast(errorMessage);
    }

    private void clearErrors() {
        fullNameInputLayout.setError(null);
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);
        confirmPasswordInputLayout.setError(null);
        phoneInputLayout.setError(null);
        roleInputLayout.setError(null);
        departmentInputLayout.setError(null);
        campusInputLayout.setError(null);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!show);
        cancelButton.setEnabled(!show);
        setInputFieldsEnabled(!show);
    }

    private void setInputFieldsEnabled(boolean enabled) {
        if (!enabled) {
            fullNameEditText.setEnabled(false);
            emailEditText.setEnabled(false);
            passwordEditText.setEnabled(false);
            confirmPasswordEditText.setEnabled(false);
            phoneEditText.setEnabled(false);
            roleDropdown.setEnabled(false);
            departmentDropdown.setEnabled(false);
            campusDropdown.setEnabled(false);
        } else {
            // Re-enable based on validation state
            updateFieldStates();
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public static class AccessRequest {
        public String userId;
        public String fullName;
        public String email;
        public String phone;
        public String role;
        public String roleVariant;
        public String department;
        public String campus;
        public String status;
        public long requestedAt;
        public Long approvedAt;
        public String approvedBy;
        public String notes;

        public AccessRequest() {
        }

        public AccessRequest(String userId, String fullName, String email, String phone,
                             String role, String roleVariant, String department, String campus,
                             String status, long requestedAt, Long approvedAt, String approvedBy,
                             String notes) {
            this.userId = userId;
            this.fullName = fullName;
            this.email = email;
            this.phone = phone;
            this.role = role;
            this.roleVariant = roleVariant;
            this.department = department;
            this.campus = campus;
            this.status = status;
            this.requestedAt = requestedAt;
            this.approvedAt = approvedAt;
            this.approvedBy = approvedBy;
            this.notes = notes;
        }
    }
}