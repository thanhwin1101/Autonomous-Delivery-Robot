package com.example.autodeliveryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import com.example.autodeliveryapp.databinding.ActivityProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfileActivity extends BottomNavActivity {
    private ActivityProfileBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeHelper.setLightStatusBar(this, true);
        EdgeToEdgeHelper.applySystemBarsPadding(binding.getRoot());

        loadUserProfile();

        binding.btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        setupBottomNav(binding.bottomNavigation, R.id.nav_profile);
    }
    private void loadUserProfile() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null)
            return;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();


        FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("users")
                .child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        if (binding == null || isFinishing() || isDestroyed())
                            return;

                        String name = snapshot.child("username").getValue(String.class);
                        String email = snapshot.child("email").getValue(String.class);
                        String phone = snapshot.child("phone").getValue(String.class);

                        if (name != null)
                            binding.tvUserName.setText(name);
                        if (email != null)
                            binding.tvEmail.setText(email);
                        binding.tvPhone.setText(
                                (phone != null && !phone.isEmpty())
                                        ? phone
                                        : getString(R.string.profile_phone_not_set));
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {

                        android.util.Log.e("ProfileDB",
                                "loadUserProfile cancelled - code: " + error.getCode()
                                        + " | " + error.getMessage());


                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}