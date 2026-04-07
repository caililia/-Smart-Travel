package com.example.volunteer;

import static org.ar.rtc.Constants.USER_OFFLINE_QUIT;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.volunteer.activity.login.LoginActivity;
import com.example.volunteer.data.UserData;
import com.example.volunteer.utils.OkhttpUtils;

import org.ar.rtc.IRtcEngineEventHandler;
import org.ar.rtc.RtcEngine;

import java.io.IOException;
import java.util.HashMap;

public class CallActivity extends AppCompatActivity {
    // 权限请求码
    private static final int PERMISSION_REQUEST_CODE = 100;
    // 所需权限
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
    };
    private static final String TAG = "CallActivity";

    // 缓存相关常量
    private static final String USER_CACHE_PREF = "user_cache";
    private static final String TOKEN_CACHE_PREF = "token_cache";
    private static final long TOKEN_CACHE_DURATION = 60 * 60 * 1000; // 1小时
    private static final long USER_CACHE_DURATION = 5 * 60 * 1000;   // 5分钟

    private RtcEngine mRtcEngine;
    private TextView tvCallState, tvCallTime;
    private Button btnMute, btnHangUp;
    private boolean isMuted = false;
    private int callSeconds = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeUpdateRunnable;
    private String phone = "";
    private String code = "";

    // 通话参数
    private String roomId = "";
    private String userId = "";
    private UserData userData;
    private String token = "";
    private HashMap<String, Object> map = new HashMap<>();

    // 状态标志
    private boolean isRtcEngineInitialized = false;
    private boolean isTokenReady = false;
    private boolean isUserDataReady = false;
    private boolean hasJoined = false; // 防止重复加入的标志位

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);
        Intent intent = getIntent();
        roomId = intent.getStringExtra("roomId");

        // 初始化视图
        initViews();

        // 立即显示初始状态
        updateCallState("准备通话...");

        // 并行执行：权限检查 + 数据预加载
        if (checkPermissions()) {
            initRtcEngine(); // 先初始化RTC引擎
            initData();      // 同时加载用户数据
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * 获取通话参数
     */
    private void getCallParams() {
        // 尝试使用缓存的token
        String cachedToken = getCachedToken(roomId, userId);
        if (cachedToken != null) {
            token = cachedToken;
            isTokenReady = true;
            Log.d(TAG, "使用缓存的Token");
            tryJoinChannel();
            return;
        }
        // 请求新token
        updateCallState("获取通话凭证...");
        map.put("roomId", roomId);
        map.put("userId", userId);
        OkhttpUtils.initRequest(2, "POST", OkhttpUtils.URL + OkhttpUtils.GeneralToken, OkhttpUtils.toBody(map), "", handlers);
        Log.d(TAG, "getCallParams: 通话凭证:" + map);
    }

    /**
     * Token缓存管理 - 获取缓存的Token
     */
    private String getCachedToken(String roomId, String userId) {
        SharedPreferences cache = getSharedPreferences(TOKEN_CACHE_PREF, MODE_PRIVATE);
        String key = roomId + "_" + userId;
        String token = cache.getString(key, null);
        long cacheTime = cache.getLong(key + "_time", 0);

        // 检查Token缓存是否在有效期内
        if (token != null && (System.currentTimeMillis() - cacheTime) < TOKEN_CACHE_DURATION) {
            return token;
        }
        return null;
    }

    /**
     * Token缓存管理 - 缓存Token
     */
    private void cacheToken(String roomId, String userId, String token) {
        SharedPreferences.Editor editor = getSharedPreferences(TOKEN_CACHE_PREF, MODE_PRIVATE).edit();
        String key = roomId + "_" + userId;
        editor.putString(key, token);
        editor.putLong(key + "_time", System.currentTimeMillis());
        editor.apply();
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        tvCallState = findViewById(R.id.tv_call_state);
        tvCallTime = findViewById(R.id.tv_call_time);
        btnMute = findViewById(R.id.btn_mute);
        btnHangUp = findViewById(R.id.btn_hang_up);

        // 静音按钮点击事件
        btnMute.setOnClickListener(v -> toggleMute());

        // 挂断按钮点击事件
        btnHangUp.setOnClickListener(v -> endCall());
    }

    /**
     * 初始化数据
     */
    private void initData() {
        SharedPreferences sharedPreferences = getSharedPreferences("phone", MODE_PRIVATE);
        phone = sharedPreferences.getString("phone", null);

        if (TextUtils.isEmpty(phone)) {
            Intent intent = new Intent(CallActivity.this, LoginActivity.class);
            String msg = "登录信息已过期，请重新登录";
            Toast.makeText(CallActivity.this, msg, Toast.LENGTH_SHORT).show();
            startActivity(intent);
            finish();
            return;
        }

        // 尝试使用缓存的用户信息
        if (tryUseCachedUserInfo()) {
            isUserDataReady = true;
            getCallParams();
        } else {
            fetchUserInfo();
        }
    }

    /**
     * 尝试使用缓存的用户信息
     */
    private boolean tryUseCachedUserInfo() {
        SharedPreferences cache = getSharedPreferences(USER_CACHE_PREF, MODE_PRIVATE);
        String cachedCode = cache.getString("user_code_" + phone, null);
        long cacheTime = cache.getLong("cache_time_" + phone, 0);

        if (cachedCode != null && (System.currentTimeMillis() - cacheTime) < USER_CACHE_DURATION) {
            code = cachedCode;
            userId = code;
            Log.d(TAG, "使用缓存的用户信息: " + code);
            return true;
        }
        return false;
    }

    /**
     * 获取用户信息
     */
    private void fetchUserInfo() {
        updateCallState("获取用户信息...");
        OkhttpUtils.initRequest(1, "GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", handlers);
    }

    /**
     * 缓存用户信息
     */
    private void cacheUserInfo(String code) {
        SharedPreferences.Editor editor = getSharedPreferences(USER_CACHE_PREF, MODE_PRIVATE).edit();
        editor.putString("user_code_" + phone, code);
        editor.putLong("cache_time_" + phone, System.currentTimeMillis());
        editor.apply();
    }

    /**
     * 检查权限
     */
    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 初始化RTC引擎
     */
    private void initRtcEngine() {
        Log.d(TAG, "初始化RTC引擎");

        try {
            if (mRtcEngine != null) {
                // 避免重复初始化
                isRtcEngineInitialized = true;
                tryJoinChannel();
                return;
            }

            updateCallState("初始化通话组件...");

            mRtcEngine = RtcEngine.create(getApplicationContext(), OkhttpUtils.APP_ID, new RtcEventHandler());
            if (mRtcEngine == null) {
                throw new RuntimeException("创建引擎实例失败");
            }

            // 提前配置音频参数
            mRtcEngine.enableAudio();
            mRtcEngine.setAudioProfile(1, 0);

            isRtcEngineInitialized = true;
            tryJoinChannel();

        } catch (Exception e) {
            Log.e(TAG, "RTC引擎初始化失败", e);
            runOnUiThread(() -> {
                showToast("通话初始化失败");
                finish();
            });
        }
    }

    /**
     * 协调加入房间的时机
     */
    private void tryJoinChannel() {
        Log.d(TAG, "检查加入房间条件 - RTC初始化: " + isRtcEngineInitialized + ", Token准备: " + isTokenReady);
        if (isRtcEngineInitialized && isTokenReady) {
            joinChannel();
        } else {
            // 显示相应状态
            if (!isRtcEngineInitialized) {
                updateCallState("初始化通话组件...");
            } else if (!isTokenReady) {
                updateCallState("获取通话凭证...");
            }
        }
    }

    /**
     * 加入频道
     */
    private void joinChannel() {
        if (hasJoined) {
            Log.w(TAG, "检测到重复加入请求，已拦截");
            return;
        }
        runOnUiThread(() -> {
            updateCallState("加入房间中...");
        });
        Log.d(TAG, "加入房间: roomId=" + roomId + ", userId=" + userId);
        int ret = mRtcEngine.joinChannel(token, roomId, "", userId);
        if (ret != 0) {
            runOnUiThread(() -> {
                showToast("加入房间失败，错误码：" + ret);
                finish();
            });
        }
        map.put("roomId", roomId);
    }

    /**
     * 切换静音状态
     */
    private void toggleMute() {
        if (mRtcEngine == null) return;

        isMuted = !isMuted;
        mRtcEngine.muteLocalAudioStream(isMuted);
        btnMute.setText(isMuted ? "取消静音" : "静音");
        btnMute.setBackgroundResource(isMuted ? R.drawable.silent2 : R.drawable.silent1);
    }

    /**
     * 结束通话
     */
    private void endCall() {
        Log.d(TAG, "endCall: 开始执行挂断操作");

        stopCallTimer();
        leaveChannel();

        Log.d(TAG, "endCall: 挂断操作完成，准备结束Activity");
        finish();
    }

    /**
     * 离开频道
     */
    private void leaveChannel() {
        if (mRtcEngine != null) {
            try {
                // 先离开频道
                mRtcEngine.leaveChannel();

                // 使用正确的方式销毁引擎
                RtcEngine.destroy();
                mRtcEngine = null;

                Log.d(TAG, "成功离开频道并销毁引擎");
            } catch (Exception e) {
                Log.e(TAG, "离开频道异常", e);
                // 即使出现异常也要确保引擎为null
                mRtcEngine = null;
            }
        }
    }

    /**
     * 开始通话计时
     */
    private void startCallTimer() {
        stopCallTimer();
        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                callSeconds++;
                int minutes = callSeconds / 60;
                int seconds = callSeconds % 60;
                tvCallTime.setText(String.format("%02d:%02d", minutes, seconds));
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timeUpdateRunnable);
    }

    /**
     * 停止通话计时
     */
    private void stopCallTimer() {
        if (timeUpdateRunnable != null) {
            handler.removeCallbacks(timeUpdateRunnable);
            timeUpdateRunnable = null;
        }
    }

    /**
     * 更新通话状态
     */
    private void updateCallState(String state) {
        runOnUiThread(() -> tvCallState.setText(state));
    }

    /**
     * RTC事件回调处理
     */
    private class RtcEventHandler extends IRtcEngineEventHandler {
        @Override
        public void onJoinChannelSuccess(String channel, String uid, int elapsed) {
            runOnUiThread(() -> updateCallState("已加入房间，等待对方..."));
        }

        public void onUserJoined(String uid) {
            runOnUiThread(() -> {
                updateCallState("对方已加入，正在通话中...");
                startCallTimer();
            });
        }

        @Override
        public void onUserOffline(String uid, int reason) {
            runOnUiThread(() -> {
                String reasonStr = reason == USER_OFFLINE_QUIT ? "对方已挂断" : "对方网络断开";
                updateCallState(reasonStr);
                stopCallTimer();

                // 3秒后自动结束通话
                handler.postDelayed(() -> endCall(), 3000);
            });
        }

        @Override
        public void onError(int err) {
            runOnUiThread(() -> {
                showToast("通话错误: " + err);
                endCall();
            });
        }
    }

    /**
     * 显示Toast提示
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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

            if (allGranted) {
                initRtcEngine();
                initData();
            } else {
                showToast("需要麦克风权限才能进行语音通话");
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCallTimer();
        leaveChannel();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        // 防止误触返回键挂断通话
        showToast("请点击挂断按钮结束通话");
    }

    Handler handlers = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            String json = msg.obj.toString();
            Log.d("handleMessage: json = ", json);
            switch (msg.what) {
                case 1:
                    Log.d(TAG, "handleMessage: " + json);
                    userData = OkhttpUtils.toData(json, UserData.class);
                    if (userData != null && userData.getData() != null) {
                        code = String.valueOf(userData.getData().getUserId());
                        userId = code;
                        cacheUserInfo(code);
                        isUserDataReady = true;
                        getCallParams();
                    } else {
                        try {
                            throw new IOException("用户数据解析失败");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;

                case 2:
                    try {
                        token = json.trim(); // 注意去除空白字符
                        if (TextUtils.isEmpty(token)) {
                            throw new IOException("Token为空");
                        }
                        // 缓存token
                        cacheToken(roomId, userId, token);
                        isTokenReady = true;
                        tryJoinChannel();
                    } catch (Exception e) {
                        Log.e(TAG, "处理Token响应失败", e);
                        runOnUiThread(() -> {
                            showToast("获取通话凭证失败");
                            finish();
                        });
                    }
                    break;

                case 3:

                case 4:
                    Log.d(TAG, "handleMessage: " + json);
                    break;
            }
            return false;
        }
    });
}