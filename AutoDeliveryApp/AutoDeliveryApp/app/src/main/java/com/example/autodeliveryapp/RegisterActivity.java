package com.example.autodeliveryapp;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.autodeliveryapp.databinding.ActivityRegisterBinding;
import com.example.autodeliveryapp.utils.PhoneUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    

    private ActivityRegisterBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeHelper.setLightStatusBar(this, true);
        EdgeToEdgeHelper.applySystemBarsPadding(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRegister.setOnClickListener(v -> handleRegister());
        binding.tvGoLogin.setOnClickListener(v -> finish());
    }

    private void handleRegister() {
        String username = binding.etUsername.getText() != null
                ? binding.etUsername.getText().toString().trim()
                : "";
        String email = binding.etEmail.getText() != null
                ? binding.etEmail.getText().toString().trim()
                : "";
        String pass = binding.etPassword.getText() != null
                ? binding.etPassword.getText().toString()
                : "";
        String phone = binding.etPhone.getText() != null
                ? binding.etPhone.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(username)) {
            binding.etUsername.setError(getString(R.string.error_empty_name));
            return;
        }
        if (TextUtils.isEmpty(email)) {
            binding.etEmail.setError(getString(R.string.error_empty_email));
            return;
        }
        if (pass.length() < 6) {
            binding.etPassword.setError(getString(R.string.error_short_password));
            return;
        }

        setLoading(true);
        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(authResult -> {

                    if (binding == null || isFinishing() || isDestroyed())
                        return;

                    String userId = authResult.getUser().getUid();
                    String phoneNormalized = PhoneUtils.normalizePhone(phone);

                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("username", username);
                    userMap.put("email", email);
                    userMap.put("phone", phone);
                    userMap.put("phoneNormalized", phoneNormalized);


                    FirebaseDatabase.getInstance(Constants.DB_URL)
                            .getReference("users")
                            .child(userId)
                            .setValue(userMap)
                            .addOnSuccessListener(unused -> {

                                if (binding == null || isFinishing() || isDestroyed())
                                    return;

                                // Create phoneIndex cho fast lookup (login by phone, receiver lookup)
                                if (!phoneNormalized.isEmpty()) {
                                    Map<String, Object> indexMap = new HashMap<>();
                                    indexMap.put("uid", userId);
                                    indexMap.put("email", email);
                                    FirebaseDatabase.getInstance(Constants.DB_URL)
                                            .getReference("phoneIndex")
                                            .child(phoneNormalized)
                                            .setValue(indexMap)
                                            .addOnFailureListener(e ->
                                                    android.util.Log.w("RegisterDB",
                                                            "phoneIndex write failed: " + e.getMessage()));
                                }

                                setLoading(false);
                                Toast.makeText(this, R.string.toast_register_success,
                                        Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(this, MainActivity.class));
                                finishAffinity();
                            })
                            .addOnFailureListener(e -> {
                                // [FIX #4] onCancelled/onFailure: log + guard + Toast
                                android.util.Log.e("RegisterDB",
                                        "Save user data failed: " + e.getMessage());
                                if (binding == null || isFinishing() || isDestroyed())
                                    return;
                                setLoading(false);
                                Toast.makeText(this,
                                        getString(R.string.toast_error_save_data, e.getMessage()),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("RegisterAuth", "Register failed: " + e.getMessage());
                    if (binding == null || isFinishing() || isDestroyed())
                        return;
                    setLoading(false);
                    Toast.makeText(this,
                            getString(R.string.toast_error_register, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean isLoading) {
        if (binding == null)
            return;
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!isLoading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}



