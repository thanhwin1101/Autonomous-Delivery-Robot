package com.example.autodeliveryapp;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.autodeliveryapp.databinding.ActivityLoginBinding;
import com.example.autodeliveryapp.utils.PhoneUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

public class LoginActivity extends AppCompatActivity {
    

    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();


        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeHelper.setLightStatusBar(this, true);
        EdgeToEdgeHelper.applySystemBarsPadding(binding.getRoot());

        binding.btnLogin.setOnClickListener(v -> handleLogin());
        binding.tvGoRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        binding.tvForgotPassword.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
    }

    private void handleLogin() {
        String loginInput = binding.etEmail.getText() != null
                ? binding.etEmail.getText().toString().trim()
                : "";
        String pass = binding.etPassword.getText() != null
                ? binding.etPassword.getText().toString()
                : "";

        if (TextUtils.isEmpty(loginInput)) {
            binding.etEmail.setError("Cannot be empty");
            return;
        }
        if (TextUtils.isEmpty(pass)) {
            binding.etPassword.setError(getString(R.string.error_empty_password));
            return;
        }

        setLoading(true);


        if (PhoneUtils.isLikelyPhone(loginInput)) {
            String phoneNormalized = PhoneUtils.normalizePhone(loginInput);
            lookupPhoneAndLogin(phoneNormalized, loginInput, pass);
        } else {
            // Email login
            performFirebaseAuthSignIn(loginInput, pass);
        }
    }

    /**
     *
     * 1. phoneIndex/{phoneNormalized} (fast, O(1) lookup)
     * 2. users.orderByChild("phoneNormalized").equalTo(...) (need indexOn)
     * 3. users.orderByChild("phone").equalTo(rawPhone) (backward compatibility)
     */
    private void lookupPhoneAndLogin(String phoneNormalized, String rawPhone, String pass) {
        FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("phoneIndex")
                .child(phoneNormalized)
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        if (binding == null || isFinishing() || isDestroyed()) return;

                        if (snapshot.exists()) {
                            String foundEmail = snapshot.child("email").getValue(String.class);
                            if (foundEmail != null && !foundEmail.isEmpty()) {
                                performFirebaseAuthSignIn(foundEmail, pass);
                                return;
                            }
                        }

                        setLoading(false);
                        Toast.makeText(LoginActivity.this, "Phone Number is not registered.", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {
                        if (binding == null || isFinishing() || isDestroyed()) return;
                        setLoading(false);

                        String errorMsg = error.getMessage();
                        if (errorMsg != null && errorMsg.contains("Permission denied")) {
                            Toast.makeText(LoginActivity.this,
                                    "Phone login requires public phoneIndex lookup rule or backend lookup. Please use email login.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(LoginActivity.this, "DB connection error: " + errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void performFirebaseAuthSignIn(String email, String pass) {
        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {

                    if (binding == null || isFinishing() || isDestroyed()) return;

                    setLoading(false);
                    if (task.isSuccessful()) {

                        syncPendingFcmToken();

                        Toast.makeText(this, R.string.toast_login_success, Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } else {
                        Exception e = task.getException();
                        String errMsg = e != null ? e.getMessage() : "";
                        if (errMsg.contains("API key not valid") || errMsg.contains("API key is invalid") || errMsg.contains("key not valid")) {
                            Toast.makeText(this,
                                    "Firebase API key is invalid. Check google-services.json and Google Cloud API key restrictions.",
                                    Toast.LENGTH_LONG).show();
                        } else if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                            Toast.makeText(this, "Incorrect password. Please try again.", Toast.LENGTH_LONG).show();
                        } else if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                            Toast.makeText(this, "User account not found. Please register first.", Toast.LENGTH_LONG).show();
                        } else if (e instanceof com.google.firebase.FirebaseNetworkException) {
                            Toast.makeText(this, "Network connection error. Please check your internet connection.", Toast.LENGTH_LONG).show();
                        } else {
                            String displayMsg = errMsg.isEmpty() ? "Unknown error" : errMsg;
                            Toast.makeText(this,
                                    getString(R.string.toast_error_prefix, displayMsg),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    /**
     *
     *
     *
     */
    private void syncPendingFcmToken() {
        if (mAuth.getCurrentUser() == null)
            return;

        String pendingToken = getSharedPreferences(Constants.FCM_PREFS, MODE_PRIVATE)
                .getString(Constants.FCM_PREFS_KEY_PENDING_TOKEN, null);

        if (pendingToken == null)
            return;

        String userId = mAuth.getCurrentUser().getUid();
        FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("users")
                .child(userId)
                .child("fcmToken")
                .setValue(pendingToken)
                .addOnCompleteListener(task -> {

                    if (binding == null || isFinishing() || isDestroyed()) return;

                    if (task.isSuccessful()) {

                        getSharedPreferences(Constants.FCM_PREFS, MODE_PRIVATE)
                                .edit()
                                .remove(Constants.FCM_PREFS_KEY_PENDING_TOKEN)
                                .apply();
                        android.util.Log.d("FCMSync", "Pending token synced successfully.");
                    } else {
                        Exception e = task.getException();
                        android.util.Log.e("FCMSync", "Failed to sync token: " + (e != null ? e.getMessage() : "Unknown error"));
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        if (binding == null)
            return;
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!isLoading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null; // [FIX #3] Release binding to avoid memory leak
    }
}



