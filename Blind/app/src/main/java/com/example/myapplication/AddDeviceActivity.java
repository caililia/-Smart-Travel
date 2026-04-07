// AddDeviceActivity.java
package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.activity.CallActivity;
import com.example.myapplication.activity.login.LoginActivity;
import com.example.myapplication.adapter.DeviceScanAdapter;
import com.example.myapplication.utils.OkhttpUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddDeviceActivity extends AppCompatActivity {

    private static final String TAG = "AddDeviceActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private RecyclerView recyclerView;
    private DeviceScanAdapter deviceAdapter;
    private ProgressBar progressBar;
    private TextView tvScanStatus;
    private Button btnScan;
    private Button btnConnect;

    private WifiManager wifiManager;
    private List<ESPDevice> discoveredDevices = new ArrayList<>();
    private ExecutorService executorService;
    private boolean isScanning = false;

    private String userId;
    private String phone;
    private ESPDevice selectedDevice = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_device);

        wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        executorService = Executors.newFixedThreadPool(20);

        initViews();
        checkPermissions();
        checkNetworkInfo();
        initData();
        // 自动从服务器获取设备列表
        startScan();
    }

    private void initData() {
        SharedPreferences sharedPreferences = getSharedPreferences("phone", MODE_PRIVATE);
        phone = sharedPreferences.getString("phone", null);

        if (TextUtils.isEmpty(phone)) {
            Intent intent = new Intent(AddDeviceActivity.this, LoginActivity.class);
            String msg = "登录信息已过期，请重新登录";
            Toast.makeText(AddDeviceActivity.this, msg, Toast.LENGTH_SHORT).show();
            startActivity(intent);
            finish();
        }
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        tvScanStatus = findViewById(R.id.tv_scan_status);
        btnScan = findViewById(R.id.btn_scan);
        btnConnect = findViewById(R.id.btn_connect);

        deviceAdapter = new DeviceScanAdapter(discoveredDevices, device -> {
            selectedDevice = device;
            deviceAdapter.setSelectedDevice(device);
            btnConnect.setEnabled(true);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(deviceAdapter);

        btnScan.setOnClickListener(v -> startScan());
        btnConnect.setOnClickListener(v -> connectToDevice());

        btnConnect.setEnabled(false);
    }

    private void checkNetworkInfo() {
        if (wifiManager.isWifiEnabled()) {
            android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            String ip = (ipAddress & 0xFF) + "." +
                    ((ipAddress >> 8) & 0xFF) + "." +
                    ((ipAddress >> 16) & 0xFF) + "." +
                    ((ipAddress >> 24) & 0xFF);

            Log.d(TAG, "手机IP: " + ip);
            Log.d(TAG, "WiFi名称: " + wifiInfo.getSSID());

            int gatewayIp = wifiManager.getDhcpInfo().gateway;
            String gateway = (gatewayIp & 0xFF) + "." +
                    ((gatewayIp >> 8) & 0xFF) + "." +
                    ((gatewayIp >> 16) & 0xFF) + "." +
                    ((gatewayIp >> 24) & 0xFF);
            Log.d(TAG, "网关: " + gateway);
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissionsNeeded = new ArrayList<>();

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }

            if (!permissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsNeeded.toArray(new String[0]),
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "需要位置权限才能扫描WiFi设备", Toast.LENGTH_LONG).show();
            }
        }
    }

    // 从服务器获取设备列表
    private void startScan() {
        discoveredDevices.clear();
        deviceAdapter.notifyDataSetChanged();
        progressBar.setVisibility(View.VISIBLE);
        tvScanStatus.setText("正在从服务器获取设备列表...");
        btnScan.setEnabled(false);
        isScanning = true;

        // 从服务器获取设备列表
        getDevicesFromServer();
    }

    private void getDevicesFromServer() {
        executorService.submit(() -> {
            try {
                String urlStr = OkhttpUtils.URL + OkhttpUtils.getDevice + "?phone=" + phone;
                Log.d(TAG, "请求设备列表: " + urlStr);

                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Content-Type", "application/json");

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    Log.d(TAG, "服务器响应: " + response.toString());
                    parseDeviceList(response.toString());
                } else {
                    runOnUiThread(() -> {
                        showError("获取设备列表失败，响应码: " + responseCode);
                    });
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "获取设备列表错误", e);
                runOnUiThread(() -> {
                    showError("获取设备列表失败：" + e.getMessage());
                });
            }
        });
    }

    private void parseDeviceList(String response) {
        try {
            JSONObject jsonObject = new JSONObject(response);
            int code = jsonObject.optInt("code", -1);

            if (code == 200) {
                JSONArray dataArray = jsonObject.optJSONArray("data");
                if (dataArray != null && dataArray.length() > 0) {
                    List<ESPDevice> newDevices = new ArrayList<>();

                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject deviceJson = dataArray.getJSONObject(i);

                        ESPDevice device = new ESPDevice();
                        device.deviceId = deviceJson.optString("deviceId", "");
                        device.deviceName = deviceJson.optString("deviceName", "ESP8266设备");
                        device.ip = deviceJson.optString("ip", "");
                        device.deviceType = deviceJson.optString("deviceType", "ultrasonic_sensor");
                        device.status = deviceJson.optString("status", "offline");

                        // 检查是否已存在
                        boolean exists = false;
                        for (ESPDevice d : discoveredDevices) {
                            if (d.deviceId != null && d.deviceId.equals(device.deviceId)) {
                                exists = true;
                                break;
                            }
                        }

                        if (!exists) {
                            newDevices.add(device);
                        }
                    }

                    synchronized (discoveredDevices) {
                        discoveredDevices.addAll(newDevices);
                    }

                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        btnScan.setEnabled(true);
                        isScanning = false;

                        if (discoveredDevices.isEmpty()) {
                            tvScanStatus.setText("未发现设备\n请确保设备已连接并上报信息");
                        } else {
                            deviceAdapter.notifyDataSetChanged();
                            tvScanStatus.setText(String.format("发现 %d 个设备", discoveredDevices.size()));
                            Toast.makeText(AddDeviceActivity.this,
                                    "发现 " + discoveredDevices.size() + " 个设备",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        btnScan.setEnabled(true);
                        isScanning = false;
                        tvScanStatus.setText("未发现设备\n请确保设备已连接并上报信息");
                    });
                }
            } else {
                String msg = jsonObject.optString("msg", "获取失败");
                runOnUiThread(() -> {
                    showError(msg);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "解析设备列表失败", e);
            runOnUiThread(() -> {
                showError("解析数据失败：" + e.getMessage());
            });
        }
    }

    private void connectToDevice() {
        if (selectedDevice == null) {
            Toast.makeText(this, "请先选择一个设备", Toast.LENGTH_SHORT).show();
            return;
        }
        showConfigDialog(selectedDevice);
    }

    @SuppressLint("DefaultLocale")
    private void showConfigDialog(ESPDevice device) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_device_config, null);

        EditText etDeviceName = view.findViewById(R.id.et_device_name);
        TextView tvDeviceInfo = view.findViewById(R.id.tv_device_info);

        tvDeviceInfo.setText(String.format(
                "设备ID: %s\n设备类型: %s\nIP地址: %s\n状态: %s",
                device.deviceId,
                device.deviceType,
                device.ip,
                device.status));
        etDeviceName.setText(device.deviceName);

        builder.setTitle("添加设备")
                .setView(view)
                .setPositiveButton("添加", (dialog, which) -> {
                    String deviceName = etDeviceName.getText().toString().trim();
                    if (TextUtils.isEmpty(deviceName)) {
                        deviceName = device.deviceName;
                    }
                    registerDeviceToServer(device, deviceName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void registerDeviceToServer(ESPDevice device, String deviceName) {
        progressBar.setVisibility(View.VISIBLE);
        tvScanStatus.setText("正在注册设备...");

        String serverUrl = OkhttpUtils.URL + OkhttpUtils.AddDevice;

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("deviceUniqueId", device.deviceId);
                json.put("deviceName", deviceName);
                json.put("userId", userId);
                json.put("deviceType", "0");
                json.put("status", "1");

                URL url = new URL(serverUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                DataOutputStream out = new DataOutputStream(connection.getOutputStream());
                out.writeBytes(json.toString());
                out.flush();
                out.close();

                int responseCode = connection.getResponseCode();
                if (responseCode == 200 || responseCode == 201) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(AddDeviceActivity.this,
                                "设备添加成功！", Toast.LENGTH_LONG).show();

                        Intent intent = new Intent();
                        intent.putExtra("device_added", true);
                        setResult(RESULT_OK, intent);
                        finish();
                    });
                } else {
                    runOnUiThread(() -> showError("服务器注册失败"));
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "注册错误", e);
                runOnUiThread(() -> showError("服务器连接失败：" + e.getMessage()));
            }
        }).start();
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            tvScanStatus.setText(message);
            Toast.makeText(AddDeviceActivity.this, message, Toast.LENGTH_LONG).show();
            btnScan.setEnabled(true);
            isScanning = false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        Log.d(TAG, "AddDeviceActivity onDestroy");
    }

    public static class ESPDevice {
        public String ip;
        public String deviceId;
        public String deviceName;
        public String deviceType;
        public String mode;
        public int rssi;
        public String status;

        @Override
        public String toString() {
            return deviceName + " (" + ip + ")";
        }
    }
}