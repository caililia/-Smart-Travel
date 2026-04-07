package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.DeviceData;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private List<DeviceData.DataBean> deviceList;
    private OnDeviceClickListener onDeviceClickListener;
    private OnStatusChangeListener onStatusChangeListener;

    public interface OnDeviceClickListener {
        void onDeviceClick(DeviceData.DataBean device);
    }

    public interface OnStatusChangeListener {
        void onStatusChange(DeviceData.DataBean device, boolean isChecked);
    }

    public DeviceAdapter(List<DeviceData.DataBean> deviceList) {
        this.deviceList = deviceList;
    }

    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        this.onDeviceClickListener = listener;
    }

    public void setOnStatusChangeListener(OnStatusChangeListener listener) {
        this.onStatusChangeListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeviceData.DataBean device = deviceList.get(position);

        // 使用 String.valueOf() 将任何类型转换为字符串，避免资源ID错误
        holder.tvDeviceName.setText(String.valueOf(device.getDeviceName()));
        holder.tvDeviceId.setText(String.valueOf(device.getDeviceId()));

        // 根据状态显示UI
        if (device.getStatus() == 1) {
            holder.tvStatus.setText("在线");  // 直接设置字符串，不是资源ID
            holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.green));
            holder.switchStatus.setChecked(true);
        } else {
            holder.tvStatus.setText("离线");  // 直接设置字符串，不是资源ID
            holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.red));
            holder.switchStatus.setChecked(false);
        }

        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (onDeviceClickListener != null) {
                onDeviceClickListener.onDeviceClick(device);
            }
        });

        // 设置开关事件（避免重复触发）
        holder.switchStatus.setOnCheckedChangeListener(null);
        holder.switchStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (onStatusChangeListener != null) {
                onStatusChangeListener.onStatusChange(device, isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeviceName;
        TextView tvDeviceId;
        TextView tvStatus;
        Switch switchStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            tvDeviceId = itemView.findViewById(R.id.tv_device_id);
            tvStatus = itemView.findViewById(R.id.tv_status);
            switchStatus = itemView.findViewById(R.id.switch_status);
        }
    }
}