package com.example.myapplication.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.myapplication.AddDeviceActivity;
import com.example.myapplication.R;
import com.example.myapplication.activity.CameraActivity;
import com.example.myapplication.activity.login.LoginActivity;
import com.example.myapplication.adapter.DeviceAdapter;
import com.example.myapplication.data.DeviceData;
import com.example.myapplication.utils.OkhttpUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class DeviceFragment extends Fragment {

    private static final String TAG = "DeviceFragment";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView tvEmptyView;
    private Button btAdd;

    private List<DeviceData.DataBean> deviceList = new ArrayList<>();
    private DeviceAdapter deviceAdapter;
    private Gson gson = new Gson();
    private String phone;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean hasAutoNavigated = false;  // 防止重复自动跳转
    private String finalDeviceIp;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device, container, false);

        initViews(view);
        initData();
        setupListeners();
        loadDevices();

        return view;
    }

    private void initViews(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        recyclerView = view.findViewById(R.id.recycler_view);
        tvEmptyView = view.findViewById(R.id.tv_empty_view);
        btAdd = view.findViewById(R.id.btAdd);

        btAdd.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), AddDeviceActivity.class);
            startActivity(intent);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // 初始化Adapter并设置监听器
        deviceAdapter = new DeviceAdapter(deviceList);

        // 设置设备点击监听
        deviceAdapter.setOnDeviceClickListener(device -> {
            Log.d(TAG, "点击设备: " + device.getDeviceName() + ", 状态: " + device.getStatus());
            if (device.getStatus() == 1) {
                // 设备在线，获取IP并跳转
                getDeviceIpAndNavigate(device);
            } else {
                showToast("设备离线，无法连接");
            }
        });

        // 设置开关状态监听
        deviceAdapter.setOnStatusChangeListener((device, isChecked) -> {
            Log.d(TAG, "设备状态变更: " + device.getDeviceName() + ", isChecked: " + isChecked);
            device.setStatus(isChecked ? 1 : 0);
            updateDeviceStatus(device);
        });

        recyclerView.setAdapter(deviceAdapter);
    }

    private void setupListeners() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            hasAutoNavigated = false;  // 重置自动跳转标志
            loadDevices();
        });
    }

    private void initData() {
        if (getActivity() != null) {
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("phone", getActivity().MODE_PRIVATE);
            phone = sharedPreferences.getString("phone", "");

            if (TextUtils.isEmpty(phone)) {
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                Toast.makeText(getContext(), "登录信息已过期，请重新登录", Toast.LENGTH_SHORT).show();
                startActivity(intent);
                if (getActivity() != null) {
                    getActivity().finish();
                }
                return;
            }
        }
    }

    private void loadDevices() {
        if (TextUtils.isEmpty(phone)) {
            Log.e(TAG, "手机号为空");
            showToast("手机号为空");
            return;
        }

        OkhttpUtils.request("GET", OkhttpUtils.URL + OkhttpUtils.GetDeviceByPhone + "?phone=" + phone, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "加载设备列表失败", e);
                mainHandler.post(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    showToast("加载失败：" + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "设备列表响应: " + json);

                    mainHandler.post(() -> {
                        swipeRefreshLayout.setRefreshing(false);

                        try {
                            DeviceData deviceData = OkhttpUtils.toData(json, DeviceData.class);

                            if (deviceData != null && "200".equals(deviceData.getCode())) {
                                List<DeviceData.DataBean> devices = deviceData.getData();
                                deviceList.clear();

                                if (devices != null && !devices.isEmpty()) {
                                    deviceList.addAll(devices);
                                    Log.d(TAG, "获取到 " + deviceList.size() + " 个设备");

                                    // 自动跳转到第一个在线的设备
                                    autoNavigateToOnlineDevice();
                                }

                                deviceAdapter.notifyDataSetChanged();

                                if (deviceList.isEmpty()) {
                                    tvEmptyView.setVisibility(View.VISIBLE);
                                    recyclerView.setVisibility(View.GONE);
                                } else {
                                    tvEmptyView.setVisibility(View.GONE);
                                    recyclerView.setVisibility(View.VISIBLE);
                                }
                            } else {
                                Log.e(TAG, "返回数据异常");
                                deviceList.clear();
                                deviceAdapter.notifyDataSetChanged();
                                tvEmptyView.setVisibility(View.VISIBLE);
                                recyclerView.setVisibility(View.GONE);
                                showToast(deviceData != null ? deviceData.getMsg() : "暂无设备数据");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "解析设备列表失败", e);
                            deviceList.clear();
                            deviceAdapter.notifyDataSetChanged();
                            tvEmptyView.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                            showToast("数据解析失败");
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        showToast("响应数据为空");
                    });
                }
            }
        });
    }

    // 自动跳转到第一个在线的设备
    private void autoNavigateToOnlineDevice() {
        if (hasAutoNavigated) {
            return;  // 已经跳转过，不再重复跳转
        }

        for (DeviceData.DataBean device : deviceList) {
            if (device.getStatus() == 1) {
                hasAutoNavigated = true;
                Log.d(TAG, "自动跳转到在线设备: " + device.getDeviceName());
                getDeviceIpAndNavigate(device);
                break;
            }
        }
    }

    // 获取设备IP并跳转到CameraActivity
    private void getDeviceIpAndNavigate(DeviceData.DataBean device) {
        String url = OkhttpUtils.URL + OkhttpUtils.getDevice +"?phone=" + phone;

        OkhttpUtils.request("GET", url, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取设备IP失败", e);
                mainHandler.post(() -> showToast("获取设备信息失败：" + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "在线设备列表响应: " + json);

                    try {
                        JSONObject jsonObject = new JSONObject(json);
                        if (jsonObject.getInt("code") == 200) {
                            JSONArray dataArray = jsonObject.getJSONArray("data");
                            String deviceIp = "";

                            for (int i = 0; i < dataArray.length(); i++) {
                                JSONObject item = dataArray.getJSONObject(i);
                                    deviceIp = item.getString("ip");
                                    break;
                            }

                            finalDeviceIp = deviceIp;
                            mainHandler.post(() -> {
                                if (finalDeviceIp != null && !finalDeviceIp.isEmpty()) {
                                    navigateToCameraActivity(device, finalDeviceIp);
                                } else {
                                    showToast("无法获取设备IP");
                                }
                            });
                        } else {
                            mainHandler.post(() -> showToast("获取设备信息失败"));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析在线设备列表失败", e);
                        mainHandler.post(() -> showToast("解析设备信息失败"));
                    }
                }
            }
        });
    }

    // 跳转到CameraActivity
    private void navigateToCameraActivity(DeviceData.DataBean device, String deviceIp) {
        Intent intent = new Intent(getActivity(), CameraActivity.class);
        intent.putExtra("deviceId", device.getDeviceUniqueId());
        intent.putExtra("deviceName", device.getDeviceName());
        intent.putExtra("deviceIp", deviceIp);
        intent.putExtra("deviceType", device.getDeviceType());
        startActivity(intent);
    }

    private void updateDeviceStatus(DeviceData.DataBean device) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("deviceId", device.getDeviceId());
        params.put("status", device.getStatus());

        String url = OkhttpUtils.URL + "/api/blind/device/updateStatus";

        OkhttpUtils.request("POST", url, OkhttpUtils.toBody(params), "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "更新设备状态失败", e);
                mainHandler.post(() -> {
                    showToast("更新失败：" + e.getMessage());
                    loadDevices();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    if (response.isSuccessful()) {
                        showToast("状态更新成功");
                    } else {
                        showToast("状态更新失败");
                        loadDevices();
                    }
                });
                response.close();
            }
        });
    }

    private void updateDevice(DeviceData.DataBean device) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("deviceId", device.getDeviceId());
        params.put("deviceName", device.getDeviceName());
        params.put("deviceType", device.getDeviceType());
        params.put("deviceUniqueId", device.getDeviceUniqueId());

        String url = OkhttpUtils.URL + "/api/blind/device/update";

        OkhttpUtils.request("POST", url, OkhttpUtils.toBody(params), "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "更新设备失败", e);
                mainHandler.post(() -> showToast("更新失败：" + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    if (response.isSuccessful()) {
                        showToast("更新成功");
                        loadDevices();
                    } else {
                        showToast("更新失败");
                    }
                });
                response.close();
            }
        });
    }

    private void deleteDevice(DeviceData.DataBean device) {
        String url = OkhttpUtils.URL + "/api/blind/device/delete/" + device.getDeviceId() + "?phone=" + phone;

        OkhttpUtils.request("DELETE", url, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "删除设备失败", e);
                mainHandler.post(() -> showToast("删除失败：" + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    if (response.isSuccessful()) {
                        showToast("删除成功");
                        loadDevices();
                    } else {
                        showToast("删除失败");
                    }
                });
                response.close();
            }
        });
    }

    private void showDeleteConfirmDialog(DeviceData.DataBean device) {
        if (getActivity() == null) return;

        new MaterialAlertDialogBuilder(getActivity())
                .setTitle("删除设备")
                .setMessage("确定要删除设备 \"" + device.getDeviceName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteDevice(device))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showToast(String message) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() ->
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        hasAutoNavigated = false;  // 重置自动跳转标志
        loadDevices();
    }
}