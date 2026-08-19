package com.example.autodeliveryapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.autodeliveryapp.adapters.ActiveTaskAdapter;
import com.example.autodeliveryapp.data.HistoryItem;
import com.example.autodeliveryapp.databinding.ActivityMainBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends BottomNavActivity {
    private ActivityMainBinding binding;


    private DatabaseReference activeTasksRef;
    private ValueEventListener activeTasksListener;

    private final List<HistoryItem> activeTaskList = new ArrayList<>();
    private ActiveTaskAdapter activeTaskAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeHelper.setLightStatusBar(this, true);
        EdgeToEdgeHelper.applySystemBarsPadding(binding.getRoot());

        binding.tvGreeting.setText(getGreeting());
        loadUserName();
        setupActiveTasksRecyclerView();
        loadActiveTasksFromFirebase();

        binding.btnStart.setOnClickListener(v -> startActivity(new Intent(this, CreateTaskActivity.class)));
        setupBottomNav(binding.bottomNavigation, R.id.nav_home);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }



    /**
     *
     *
     *
     */
    private void setupActiveTasksRecyclerView() {
        activeTaskAdapter = new ActiveTaskAdapter(this, activeTaskList, item -> {

            Intent intent = new Intent(this, TrackingActivity.class);
            intent.putExtra("orderId", item.getOrderId());
            intent.putExtra("taskId", item.getOrderId());
            intent.putExtra("pickup", item.getPickup());
            intent.putExtra("dropoff", item.getDropoff());
            intent.putExtra("senderUid", item.getSenderUid());
            intent.putExtra("robotId", item.getRobotId());
            intent.putExtra("slotId", item.getSlotId());
            // Pass distance from HistoryItem (populated from Firebase deliveryDistanceKm)
            intent.putExtra("distance", item.getDistance());
            startActivity(intent);
        });

        binding.rvActiveTasks.setLayoutManager(new LinearLayoutManager(this));
        binding.rvActiveTasks.setAdapter(activeTaskAdapter);
    }

    /**
     *
     *
     *
     *
     *
     *
     *
     */
    private DatabaseReference recipientTasksRef;
    private ValueEventListener recipientTasksListener;
    private final List<HistoryItem> senderTasks = new ArrayList<>();
    private final List<HistoryItem> receiverTasks = new ArrayList<>();

    private void loadActiveTasksFromFirebase() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            updateEmptyState();
            return;
        }
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        activeTasksRef = FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("tasks")
                .child(userId);
                
        recipientTasksRef = FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("recipientTasks")
                .child(userId);

        activeTasksListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (binding == null || isFinishing() || isDestroyed()) return;
                senderTasks.clear();
                processTaskSnapshot(snapshot, senderTasks);
                updateCombinedList();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("MainDB", "loadActiveTasks cancelled - " + error.getMessage());
            }
        };

        recipientTasksListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (binding == null || isFinishing() || isDestroyed()) return;
                receiverTasks.clear();
                processTaskSnapshot(snapshot, receiverTasks);
                updateCombinedList();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("MainDB", "loadRecipientTasks cancelled - " + error.getMessage());
            }
        };

        activeTasksRef.addValueEventListener(activeTasksListener);
        recipientTasksRef.addValueEventListener(recipientTasksListener);
    }
    
    private void processTaskSnapshot(DataSnapshot snapshot, List<HistoryItem> targetList) {
        for (DataSnapshot taskSnap : snapshot.getChildren()) {
            String rawStatus = taskSnap.child("status").getValue(String.class);

            // Show all non-terminal tasks (everything except delivered/cancelled)
            if (rawStatus == null
                    || "delivered".equals(rawStatus)
                    || "cancelled".equals(rawStatus)) {
                continue;
            }

            String orderId   = taskSnap.child("orderId").getValue(String.class);
            if (orderId == null) orderId = taskSnap.child("taskId").getValue(String.class);
            if (orderId == null) orderId = taskSnap.getKey();

            String pickup    = taskSnap.child("pickup").getValue(String.class);
            if (pickup == null) pickup = taskSnap.child("pickupName").getValue(String.class);

            String dropoff   = taskSnap.child("dropoff").getValue(String.class);
            if (dropoff == null) dropoff = taskSnap.child("dropoffName").getValue(String.class);

            String senderUid = taskSnap.child("senderUid").getValue(String.class);
            String robotId   = taskSnap.child("robotId").getValue(String.class);
            String slotId    = taskSnap.child("slotId").getValue(String.class);

            Double distanceKm = taskSnap.child("deliveryDistanceKm").getValue(Double.class);
            if (distanceKm == null || distanceKm == 0) {
                distanceKm = taskSnap.child("distance").getValue(Double.class);
            }
            if (distanceKm == null) distanceKm = 0.0;

            String statusVi = friendlyStatus(rawStatus);

            targetList.add(new HistoryItem(
                    orderId   != null ? orderId   : "---",
                    pickup    != null ? pickup    : "---",
                    dropoff   != null ? dropoff   : "---",
                    statusVi,
                    "",
                    "",
                    senderUid != null ? senderUid : "",
                    robotId   != null ? robotId   : "",
                    slotId    != null ? slotId    : "",
                    distanceKm));
        }
    }

    private void updateCombinedList() {
        activeTaskList.clear();
        activeTaskList.addAll(senderTasks);
        activeTaskList.addAll(receiverTasks);
        
        // Sort by orderId to keep list stable, or just leave it since orderId has random numbers
        // but sorting by creation time would be better. For now just combine.
        activeTaskAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    /**
     * Control visibility between RecyclerView and text "No active orders".
     *
     */
    private void updateEmptyState() {
        if (binding == null)
            return;
        if (activeTaskList.isEmpty()) {
            binding.tvNoTasks.setVisibility(View.VISIBLE);
            binding.rvActiveTasks.setVisibility(View.GONE);
        } else {
            binding.tvNoTasks.setVisibility(View.GONE);
            binding.rvActiveTasks.setVisibility(View.VISIBLE);
        }
    }



    private void loadUserName() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null)
            return;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();


        FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("users")
                .child(userId)
                .child("username")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        if (binding == null || isFinishing() || isDestroyed())
                            return;
                        String username = snapshot.getValue(String.class);
                        if (username != null) {
                            binding.tvUserName.setText(username);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {

                        android.util.Log.e("MainDB",
                                "loadUserName cancelled - code: " + error.getCode()
                                        + " | " + error.getMessage());
                        if (binding == null || isFinishing() || isDestroyed())
                            return;


                    }
                });
    }


    /** Maps a raw Firebase task status to a short English display label. */
    private String friendlyStatus(String rawStatus) {
        if (rawStatus == null) return "Processing";
        switch (rawStatus) {
            case "pending":                return "Processing";
            case "going_to_pickup":        return "Going to Pickup";
            case "arrived_pickup":         return "At Pickup";
            case "waiting_sender_load":    return "Waiting Load";
            case "sender_loaded":          return "Loaded";
            case "picked_up":              return "Picked Up";
            case "going_to_destination":   return "Delivering";
            case "arrived_dropoff":        return "At Dropoff";
            case "waiting_receiver_unlock":return "Waiting Unlock";
            default:                       return "Delivering";
        }
    }

    private String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12)
            return getString(R.string.greeting_morning);
        if (hour >= 12 && hour < 13)
            return getString(R.string.greeting_noon);
        if (hour >= 13 && hour < 18)
            return getString(R.string.greeting_afternoon);
        return getString(R.string.greeting_evening);
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeTasksRef != null && activeTasksListener != null) {
            activeTasksRef.removeEventListener(activeTasksListener);
        }
        if (recipientTasksRef != null && recipientTasksListener != null) {
            recipientTasksRef.removeEventListener(recipientTasksListener);
        }
        binding = null;
    }
}