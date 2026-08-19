package com.example.autodeliveryapp.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.autodeliveryapp.data.NotificationItem;
import com.example.autodeliveryapp.databinding.ItemNotificationBinding;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
    private final Context context;
    private final List<NotificationItem> list;

    public NotificationAdapter(Context context, List<NotificationItem> list) {
        this.context = context;
        this.list = list;
    }

    public void updateData(List<NotificationItem> newList) {
        this.list.clear();
        this.list.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNotificationBinding binding = ItemNotificationBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationItem item = list.get(position);
        holder.binding.tvIcon.setText(item.getIcon());
        holder.binding.tvTitle.setText(item.getTitle());
        holder.binding.tvMessage.setText(item.getMessage());
        holder.binding.tvTime.setText(item.getTime());

        holder.itemView.setOnClickListener(v -> {
            String taskId = item.getOrderId(); // getOrderId() now returns taskId if orderId is null
            String senderUid = item.getSenderUid();
            
            if (taskId != null && !taskId.isEmpty()) {
                if (senderUid != null && !senderUid.isEmpty()) {
                    // Read task from Firebase
                    com.google.firebase.database.FirebaseDatabase.getInstance(com.example.autodeliveryapp.Constants.DB_URL)
                            .getReference("tasks")
                            .child(senderUid)
                            .child(taskId)
                            .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                                    if (snapshot.exists()) {
                                        Intent intent = new Intent(context, com.example.autodeliveryapp.TrackingActivity.class);
                                        intent.putExtra("orderId", taskId);
                                        intent.putExtra("pickup", snapshot.child("pickup").getValue(String.class));
                                        intent.putExtra("dropoff", snapshot.child("dropoff").getValue(String.class));
                                        
                                        Double distance = snapshot.child("distance").getValue(Double.class);
                                        intent.putExtra("distance", distance != null ? distance : 0.0);
                                        
                                        intent.putExtra("senderUid", senderUid);
                                        intent.putExtra("robotId", snapshot.child("robotId").getValue(String.class));
                                        intent.putExtra("slotId", snapshot.child("slotId").getValue(String.class));
                                        
                                        context.startActivity(intent);
                                    } else {
                                        android.widget.Toast.makeText(context, "Order information not found", android.widget.Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                                    android.widget.Toast.makeText(context, "Error reading order data", android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    // Fallback to data stored in notification
                    Intent intent = new Intent(context, com.example.autodeliveryapp.TrackingActivity.class);
                    intent.putExtra("orderId", taskId);
                    intent.putExtra("pickup", item.getPickup());
                    intent.putExtra("dropoff", item.getDropoff());
                    intent.putExtra("distance", item.getDistance());
                    context.startActivity(intent);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemNotificationBinding binding;

        ViewHolder(@NonNull ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
