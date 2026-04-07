package com.example.myapplication.activity.full_down;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

public class FallDetectionApplication extends Application {

    private static final String TAG = "FallDetectionApp";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "应用启动，开始跌倒检测服务");
        startFallDetectionService();
    }

    private void startFallDetectionService() {
        Intent intent = new Intent(this, FallDetectionService.class);
        startService(intent);
    }
}