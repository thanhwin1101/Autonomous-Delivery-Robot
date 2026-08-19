package com.example.autodeliveryapp.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.autodeliveryapp.data.HistoryItem;
import com.example.autodeliveryapp.databinding.ItemActiveTaskBinding;
import java.util.List;

/**
 * Adapter displaying active orders list (pending / delivering)
 * on Home (MainActivity).
 *
 * Uses View Binding for view mapping.
 * Provides OnTaskClickListener for MainActivity to handle click events.
 */
public class ActiveTaskAdapter extends RecyclerView.Adapter<ActiveTaskAdapter.ViewHolder> {

    /**
     *
     *
     *
     */
    public interface OnTaskClickListener {
        void onTaskClick(HistoryItem item);
    }

    private final Context context;
    private final List<HistoryItem> list;
    private final OnTaskClickListener listener;

    public ActiveTaskAdapter(Context context, List<HistoryItem> list, OnTaskClickListener listener) {
        this.context = context;
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemActiveTaskBinding binding = ItemActiveTaskBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem item = list.get(position);

        holder.binding.tvActiveOrderId.setText(item.getOrderId());
        holder.binding.tvActiveDropoff.setText(item.getDropoff());
        holder.binding.tvActiveStatus.setText(item.getStatus());



        // Here we just apply corresponding colors.
        if ("Delivering".equals(item.getStatus())) {
            holder.binding.tvActiveStatus.setBackgroundResource(
                    com.example.autodeliveryapp.R.drawable.bg_tag_orange);
            holder.binding.tvActiveStatus.setTextColor(
                    context.getColor(com.example.autodeliveryapp.R.color.status_processing));
        } else {

            holder.binding.tvActiveStatus.setBackgroundResource(
                    com.example.autodeliveryapp.R.drawable.bg_tag_blue);
            holder.binding.tvActiveStatus.setTextColor(
                    context.getColor(com.example.autodeliveryapp.R.color.accent_blue));
        }

        // Handle click event on entire item card
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTaskClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemActiveTaskBinding binding;

        ViewHolder(@NonNull ItemActiveTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
