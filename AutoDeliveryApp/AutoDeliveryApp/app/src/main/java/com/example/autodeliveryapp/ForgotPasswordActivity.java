package com.example.autodeliveryapp;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.autodeliveryapp.databinding.ActivityForgotPasswordBinding;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {
    

    private ActivityForgotPasswordBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeHelper.setLightStatusBar(this, true);
        EdgeToEdgeHelper.applySystemBarsPadding(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnBackToLogin.setOnClickListener(v -> finish());

        binding.btnSendCode.setOnClickListener(v -> {
            String email = binding.etEmail.getText() != null
                    ? binding.etEmail.getText().toString().trim()
                    : "";
            if (TextUtils.isEmpty(email)) {
                binding.etEmail.setError(getString(R.string.error_empty_email));
                return;
            }

            binding.progressBar.setVisibility(View.VISIBLE);
            mAuth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(unused -> {

                        if (binding == null || isFinishing() || isDestroyed())
                            return;
                        binding.progressBar.setVisibility(View.GONE);
                        binding.layoutStep1.setVisibility(View.GONE);
                        binding.layoutStep2.setVisibility(View.VISIBLE);
                    })
                    .addOnFailureListener(e -> {

                        android.util.Log.e("ForgotPassword",
                                "Send reset email failed: " + e.getMessage());
                        if (binding == null || isFinishing() || isDestroyed())
                            return;
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(this,
                                getString(R.string.toast_error_prefix, e.getMessage()),
                                Toast.LENGTH_LONG).show();
                    });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}



