package com.example.myapplication.activity.location;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
// 腾讯定位相关
import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;
// 腾讯地图相关
import com.tencent.tencentmap.mapsdk.maps.LocationSource;
import com.tencent.tencentmap.mapsdk.maps.MapView;
import com.tencent.tencentmap.mapsdk.maps.TencentMap;
import com.tencent.tencentmap.mapsdk.maps.TencentMapInitializer;
import com.tencent.tencentmap.mapsdk.maps.UiSettings;
import com.tencent.tencentmap.mapsdk.maps.model.MyLocationStyle;

public class LocationActivity extends AppCompatActivity implements LocationSource, TencentLocationListener {

    private static final String TAG = "LocationActivity";

    private MapView mMapView;
    private TencentMap mTencentMap;

    private TencentLocationManager mLocationManager;
    private TencentLocationRequest mLocationRequest;
    private OnLocationChangedListener mListener;

    // --- 【新增】判断位置稳定的变量 ---
    private double lastLat = 0; // 上一次纬度
    private double lastLng = 0; // 上一次经度
    private int stableCount = 0; // 稳定计数器
    private boolean isFirstLoc = true; // 是否是第一次定位

    // 设置判定为“变化不大”的阈值（单位：米）
    // GPS通常有漂移，建议设置 5米 - 10米 左右
    private static final float DISTANCE_THRESHOLD = 5.0f;

    private final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TencentMapInitializer.setAgreePrivacy(true);
        setContentView(R.layout.activity_location);
        mMapView = findViewById(R.id.map);

        if (checkPermission()) {
            initMap();
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, 100);
        }
    }

    private void initMap() {
        if (mTencentMap == null) {
            mTencentMap = mMapView.getMap();
        }
        UiSettings uiSettings = mTencentMap.getUiSettings();
        uiSettings.setMyLocationButtonEnabled(true);

        MyLocationStyle locationStyle = new MyLocationStyle();
        locationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE);
        mTencentMap.setMyLocationStyle(locationStyle);

        mTencentMap.setLocationSource(this);
        mTencentMap.setMyLocationEnabled(true);
    }

    @Override
    public void activate(OnLocationChangedListener listener) {
        mListener = listener;
        startLocation();
    }

    @Override
    public void deactivate() {
        mListener = null;
        stopLocation();
    }

    private void startLocation() {
        if (mLocationManager == null) {
            mLocationManager = TencentLocationManager.getInstance(this);
        }
        mLocationManager.removeUpdates(this);

        if (mLocationRequest == null) {
            mLocationRequest = TencentLocationRequest.create();
        }

        mLocationRequest.setAllowGPS(true);
        mLocationRequest.setAllowDirection(true);
        // 定位间隔 2000ms (2秒)
        mLocationRequest.setInterval(2000);
        mLocationRequest.setRequestLevel(TencentLocationRequest.REQUEST_LEVEL_GEO);

        // 重置计数器
        stableCount = 0;
        isFirstLoc = true;

        int error = mLocationManager.requestLocationUpdates(mLocationRequest, this, Looper.getMainLooper());
        if (error != 0) {
            Log.e(TAG, "定位启动失败: " + error);
        }
    }

    private void stopLocation() {
        if (mLocationManager != null) {
            mLocationManager.removeUpdates(this);
        }
    }

    // ==========================================
    // 【核心逻辑】定位回调
    // ==========================================
    @Override
    public void onLocationChanged(TencentLocation tencentLocation, int error, String reason) {
        if (error == TencentLocation.ERROR_OK && mListener != null) {
            // 1. 获取当前坐标
            double curLat = tencentLocation.getLatitude();
            double curLng = tencentLocation.getLongitude();

            // 绘制蓝点
            android.location.Location location = new android.location.Location(tencentLocation.getProvider());
            location.setLatitude(curLat);
            location.setLongitude(curLng);
            location.setAccuracy(tencentLocation.getAccuracy());
            location.setBearing(tencentLocation.getBearing());
            location.setTime(System.currentTimeMillis());
            mListener.onLocationChanged(location);

            // -------------------------------------------------
            // 2. 【新增】判断位置是否稳定
            // -------------------------------------------------
            if (isFirstLoc) {
                // 第一次定位，只记录，不比较
                lastLat = curLat;
                lastLng = curLng;
                isFirstLoc = false;
                Log.d(TAG, "首次定位成功，初始化坐标");
            } else {
                // 计算当前点和上一次点的距离（米）
                float distance = calculateDistance(lastLat, lastLng, curLat, curLng);

                Log.d(TAG, "距离上次定位移动了: " + distance + "米");

                if (distance < DISTANCE_THRESHOLD) {
                    // 距离小于阈值（例如5米），认为没动，计数器+1
                    stableCount++;
                    Log.d(TAG, "位置稳定计数: " + stableCount);
                } else {
                    // 距离超过阈值，认为用户移动了，计数器归零
                    stableCount = 0;
                    Log.d(TAG, "位置发生移动，计数器重置");
                }

                // 更新“上一次坐标”为“当前坐标”
                lastLat = curLat;
                lastLng = curLng;

                // 3. 【新增】触发条件：连续3次稳定
                if (stableCount >= 3) {
                    postLocation(curLat, curLng);

                    // 触发后清零，等待下一次连续3次稳定
                    // 如果你想一直传，可以把这行注释掉，或者改为 stableCount = 2;
                    stableCount = 0;
                }
            }

        } else {
            Log.e(TAG, "定位失败: " + reason);
        }
    }

    // ==========================================
    // 【新增】自定义的业务方法
    // ==========================================

    /**
     * 当坐标稳定时调用此方法
     */
    private void postLocation(double lat, double lng) {
        Log.i(TAG, ">>> 满足条件！执行 postLocation: " + lat + "," + lng);

        // 在这里写你的上传服务器代码
        // ...

        // 弹窗提示
        showToastSafe("位置已稳定，正在上传坐标...\n" + lat + ", " + lng);
    }

    /**
     * 计算两点之间的距离（单位：米）
     */
    private float calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        float[] results = new float[1];
        // Android原生提供的计算两点间距离的方法（基于WGS84椭球体）
        android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, results);
        return results[0];
    }

    @Override
    public void onStatusUpdate(String s, int i, String s1) {}

    private void showToastSafe(String msg) {
        runOnUiThread(() ->
                Toast.makeText(LocationActivity.this, msg, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    protected void onResume() { super.onResume(); if (mMapView != null) mMapView.onResume(); }
    @Override
    protected void onPause() { super.onPause(); if (mMapView != null) mMapView.onPause(); }
    @Override
    protected void onStart() { super.onStart(); if (mMapView != null) mMapView.onStart(); }
    @Override
    protected void onStop() { super.onStop(); if (mMapView != null) mMapView.onStop(); }
    @Override
    protected void onDestroy() { super.onDestroy(); stopLocation(); if (mMapView != null) mMapView.onDestroy(); }
    @Override
    protected void onRestart() { super.onRestart(); if (mMapView != null) mMapView.onRestart(); }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            for (String permission : PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (checkPermission()) initMap();
        }
    }
}