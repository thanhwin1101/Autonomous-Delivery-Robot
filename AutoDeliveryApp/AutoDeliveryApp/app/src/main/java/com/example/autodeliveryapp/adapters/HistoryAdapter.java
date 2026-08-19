package com.example.autodeliveryapp.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.autodeliveryapp.R;
import com.example.autodeliveryapp.data.HistoryItem;
import com.example.autodeliveryapp.databinding.ItemHistoryBinding;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private final Context context;
    private final List<HistoryItem> list;

    public HistoryAdapter(Context context, List<HistoryItem> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHistoryBinding binding = ItemHistoryBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem item = list.get(position);
        holder.binding.tvOrderId.setText(item.getOrderId());
        holder.binding.tvPickup.setText(item.getPickup());
        holder.binding.tvDropoff.setText(item.getDropoff());
        holder.binding.tvDate.setText(item.getDate());
        holder.binding.tvStatus.setText(item.getStatus());

        switch (item.getStatus()) {
            case "Completed":
                holder.binding.tvStatus.setTextColor(context.getColor(R.color.status_completed));
                holder.binding.tvStatus.setBackgroundResource(R.drawable.bg_tag_green);
                break;
            case "Delivering":
                holder.binding.tvStatus.setTextColor(context.getColor(R.color.status_processing));
                holder.binding.tvStatus.setBackgroundResource(R.drawable.bg_tag_orange);
                break;
            case "Cancelled":
                holder.binding.tvStatus.setTextColor(context.getColor(R.color.status_cancelled));
                holder.binding.tvStatus.setBackgroundResource(R.drawable.bg_tag_red);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemHistoryBinding binding;

        ViewHolder(@NonNull ItemHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
