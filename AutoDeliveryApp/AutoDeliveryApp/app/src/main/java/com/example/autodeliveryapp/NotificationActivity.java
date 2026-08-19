package com.example.autodeliveryapp;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.autodeliveryapp.adapters.NotificationAdapter;
import com.example.autodeliveryapp.data.NotificationItem;
import com.example.autodeliveryapp.databinding.ActivityNotificationBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationActivity extends BottomNavActivity {
    private ActivityNotificationBinding binding;
    private NotificationAdapter adapter;
    private DatabaseReference notificationRef;
    private ValueEventListener notificationListener;
    private final List<NotificationItem> notificationList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeHelper.setLightStatusBar(this, true);
        EdgeToEdgeHelper.applySystemBarsPadding(binding.getRoot());

        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(this, notificationList);
        binding.rvNotifications.setAdapter(adapter);

        loadNotificationsFromFirebase();
        setupBottomNav(binding.bottomNavigation, R.id.nav_notification);
    }

    private void loadNotificationsFromFirebase() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvEmpty.setVisibility(View.GONE);
        binding.rvNotifications.setVisibility(View.GONE);

        notificationRef = FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("notifications")
                .child(userId);

        notificationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                
                List<NotificationItem> fetchedList = new ArrayList<>();
                for (DataSnapshot itemSnap : snapshot.getChildren()) {
                    NotificationItem item = itemSnap.getValue(NotificationItem.class);
                    if (item != null) {
                        fetchedList.add(item);
                    }
                }
                
                // Assuming newer notifications are added at the end (or we can just reverse)
                Collections.reverse(fetchedList);
                adapter.updateData(fetchedList);

                if (fetchedList.isEmpty()) {
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                    binding.rvNotifications.setVisibility(View.GONE);
                } else {
                    binding.tvEmpty.setVisibility(View.GONE);
                    binding.rvNotifications.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("NotificationActivity", "Failed to load notifications: " + error.getMessage());
                if (binding != null) {
                    binding.progressBar.setVisibility(View.GONE);
                }
            }
        };
        
        notificationRef.addValueEventListener(notificationListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationRef != null && notificationListener != null) {
            notificationRef.removeEventListener(notificationListener);
        }
        binding = null; // Release binding to avoid memory leak
    }
}