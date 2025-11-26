package com.example.circularslider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * RecyclerView 适配器：用于显示已配对或已发现的蓝牙设备列表。
 */
public class BTAdapter extends RecyclerView.Adapter<BTAdapter.DeviceViewHolder> {

    private final List<DeviceItem> deviceList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(DeviceItem item);
    }

    public BTAdapter(List<DeviceItem> deviceList, OnItemClickListener listener) {
        this.deviceList = deviceList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        DeviceItem item = deviceList.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    /** 清空列表 */
    public void clear() {
        int size = deviceList.size();
        deviceList.clear();
        // 使用更具体的通知以获得动画效果（可选，notifyDataSetChanged 也行）
        notifyDataSetChanged();
    }

    /** 添加设备 */
    public void addDevice(DeviceItem device) {
        deviceList.add(device);
        // 通知插入了最后一行
        notifyItemInserted(deviceList.size() - 1);
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final TextView addressTextView;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.device_name);
            addressTextView = itemView.findViewById(R.id.device_address);
        }

        public void bind(final DeviceItem item, final OnItemClickListener listener) {
            nameTextView.setText(item.getName());
            addressTextView.setText(item.getAddress());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }
}