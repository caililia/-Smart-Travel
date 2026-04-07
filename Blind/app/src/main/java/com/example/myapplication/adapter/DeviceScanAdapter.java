package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.AddDeviceActivity;
import com.example.myapplication.R;

import java.util.List;

public class DeviceScanAdapter extends RecyclerView.Adapter<DeviceScanAdapter.ViewHolder> {

    private List<AddDeviceActivity.ESPDevice> devices;
    private OnDeviceClickListener listener;
    private AddDeviceActivity.ESPDevice selectedDevice;

    public interface OnDeviceClickListener {
        void onDeviceClick(AddDeviceActivity.ESPDevice device);
    }

    public DeviceScanAdapter(List<AddDeviceActivity.ESPDevice> devices,
                             OnDeviceClickListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    public void setSelectedDevice(AddDeviceActivity.ESPDevice device) {
        this.selectedDevice = device;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device_scan, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AddDeviceActivity.ESPDevice device = devices.get(position);

        holder.tvDeviceName.setText(device.deviceName);
        holder.tvDeviceInfo.setText(String.format("IP: %s\n信号: %d dBm | 模式: %s",
                device.ip, device.rssi, device.mode));

        if (selectedDevice != null && selectedDevice.ip != null &&
                selectedDevice.ip.equals(device.ip)) {
            holder.itemView.setBackgroundResource(R.drawable.bg_device_selected);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_device_normal);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeviceClick(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeviceName;
        TextView tvDeviceInfo;

        ViewHolder(View itemView) {
            super(itemView);
            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            tvDeviceInfo = itemView.findViewById(R.id.tv_device_info);
        }
    }
}