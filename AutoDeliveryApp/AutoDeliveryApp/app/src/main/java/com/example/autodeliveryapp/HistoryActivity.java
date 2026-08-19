package com.example.autodeliveryapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.autodeliveryapp.adapters.HistoryAdapter;
import com.example.autodeliveryapp.data.HistoryItem;
import com.example.autodeliveryapp.databinding.ActivityHistoryBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends BottomNavActivity {
    private ActivityHistoryBinding binding;
    private HistoryAdapter adapter;
    private final List<HistoryItem> historyList = new ArrayList<>();


    private DatabaseReference historyRef;
    private ValueEventListener historyListener;
    private DatabaseReference recipientHistoryRef;
    private ValueEventListener recipientHistoryListener;
    
    private final List<HistoryItem> senderHistory = new ArrayList<>();
    private final List<HistoryItem> receiverHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeHelper.setLightStatusBar(this, true);
        EdgeToEdgeHelper.applySystemBarsPadding(binding.getRoot());

        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(this, historyList);
        binding.rvHistory.setAdapter(adapter);

        loadHistoryFromFirebase();
        setupBottomNav(binding.bottomNavigation, R.id.nav_history);
    }

    private void loadHistoryFromFirebase() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null)
            return;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        binding.progressBar.setVisibility(View.VISIBLE);

        historyRef = FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("tasks")
                .child(userId);
                
        recipientHistoryRef = FirebaseDatabase.getInstance(Constants.DB_URL)
                .getReference("recipientTasks")
                .child(userId);

        historyListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (binding == null || isFinishing() || isDestroyed()) return;
                senderHistory.clear();
                processHistorySnapshot(snapshot, senderHistory);
                updateCombinedHistory();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("HistoryDB", "loadHistory cancelled: " + error.getMessage());
                if (binding == null || isFinishing() || isDestroyed()) return;
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(HistoryActivity.this, getString(R.string.toast_error_prefix, error.getMessage()), Toast.LENGTH_SHORT).show();
            }
        };
        
        recipientHistoryListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (binding == null || isFinishing() || isDestroyed()) return;
                receiverHistory.clear();
                processHistorySnapshot(snapshot, receiverHistory);
                updateCombinedHistory();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("HistoryDB", "loadRecipientHistory cancelled: " + error.getMessage());
            }
        };

        historyRef.addValueEventListener(historyListener);
        recipientHistoryRef.addValueEventListener(recipientHistoryListener);
    }
    
    private void processHistorySnapshot(DataSnapshot snapshot, List<HistoryItem> targetList) {
        for (DataSnapshot taskSnap : snapshot.getChildren()) {
            String orderId = taskSnap.child("orderId").getValue(String.class);
            String pickup = taskSnap.child("pickup").getValue(String.class);
            String dropoff = taskSnap.child("dropoff").getValue(String.class);
            String status = taskSnap.child("status").getValue(String.class);
            Long price = taskSnap.child("price").getValue(Long.class);
            Long created = taskSnap.child("createdAt").getValue(Long.class);

            String statusVi = mapStatus(status);
            String date = created != null
                    ? new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(created))
                    : "---";
            String priceStr = price != null
                    ? String.format(Locale.getDefault(), "%,d", price).replace(",", ".") + " VND"
                    : "";

            targetList.add(0, new HistoryItem("#" + orderId, pickup, dropoff, statusVi, date, priceStr));
        }
    }
    
    private void updateCombinedHistory() {
        historyList.clear();
        historyList.addAll(senderHistory);
        historyList.addAll(receiverHistory);
        
        binding.progressBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
    }

    private String mapStatus(String status) {
        if (status == null)
            return "---";
        switch (status) {
            case "pending":
                return getString(R.string.status_pending);
            case "going_to_pickup":
                return "Going to pickup";
            case "picked_up":
                return "Picked up";
            case "going_to_destination":
                return "Delivering";
            case "delivering":
                return getString(R.string.status_delivering);
            case "delivered":
                return "Delivered";
            case "completed":
                return getString(R.string.status_completed);
            case "cancelled":
                return getString(R.string.status_cancelled);
            default:
                return status;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (historyRef != null && historyListener != null) {
            historyRef.removeEventListener(historyListener);
        }
        if (recipientHistoryRef != null && recipientHistoryListener != null) {
            recipientHistoryRef.removeEventListener(recipientHistoryListener);
        }
        binding = null;
    }
}