package com.example.myapplication.activity;

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
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.activity.login.LoginActivity;
import com.example.myapplication.data.UserData;
import com.example.myapplication.fragment.HomeFragment;
import com.example.myapplication.utils.OkhttpUtils;

// RTC 相关引用
import org.ar.rtc.Constants;
import org.ar.rtc.IRtcEngineEventHandler;
import org.ar.rtc.RtcEngine;
import org.ar.rtc.VideoEncoderConfiguration;
import org.ar.rtc.video.VideoCanvas;

// 腾讯定位 相关引用
import com.example.myapplication.utils.TimeUtils;
import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VideoCallActivity extends AppCompatActivity implements TencentLocationListener {

    private static final String TAG = "VideoCallActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    // --- 权限合并：视频通话 + 定位所需的所有权限 ---
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            // 新增定位权限
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE
    };

    // 缓存相关常量
    private static final String USER_CACHE_PREF = "user_cache";
    private static final String TOKEN_CACHE_PREF = "token_cache";
    private static final long TOKEN_CACHE_DURATION = 60 * 60 * 1000;
    private static final long USER_CACHE_DURATION = 5 * 60 * 1000;

    // RTC 变量
    private RtcEngine mRtcEngine;
    private TextView tvCallState, tvCallTime;
    private Button btnMute, btnHangUp;
    private FrameLayout mLocalVideoContainer;
    private TextureView mLocalVideoView;

    // 通话状态变量
    private boolean isMuted = false;
    private int callSeconds = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeUpdateRunnable;
    private String phone = "";
    private String code = "";
    private String roomId = "";
    private String userId;
    private UserData userData;
    private String token = "";
    private HashMap<String, Object> map = new HashMap<>();
    private boolean isRtcEngineInitialized = false;
    private boolean isTokenReady = false;
    private boolean isUserDataReady = false;
    private boolean isJoinedChannel = false;
    private String fullDown;

    // ==========================================
    // 【新增】定位相关变量
    // ==========================================
    private TencentLocationManager mLocationManager;
    private TencentLocationRequest mLocationRequest;

    private String userLocation = "";

    private double lastLat = 0;          // 上一次纬度
    private double lastLng = 0;          // 上一次经度
    private int stableCount = 0;         // 稳定计数器
    private boolean isFirstLoc = true;   // 是否首次定位
    // 判定静止的距离阈值（米）
    private static final float DISTANCE_THRESHOLD = 5.0f;
    // 判定稳定的次数阈值（3次）
    private static final int STABLE_COUNT_TARGET = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);
        Intent intent = getIntent();
        fullDown = intent.getStringExtra("fullDown");
        if (fullDown == null) {
            fullDown = "0";
        }
        initViews();
        updateCallState("准备视频通话...");

        if (checkPermissions()) {
            initData(); // 初始化数据并启动定位
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    private void initViews() {
        Log.d(TAG, "开始初始化视图");
        try {
            tvCallState = findViewById(R.id.tv_call_state);
            tvCallTime = findViewById(R.id.tv_call_time);
            btnMute = findViewById(R.id.btn_mute);
            btnHangUp = findViewById(R.id.btn_hang_up);
            mLocalVideoContainer = findViewById(R.id.local_video_view_container);

            btnMute.setOnClickListener(v -> toggleMute());
            btnHangUp.setOnClickListener(v -> endCall());
            Log.d(TAG, "视图初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "初始化视图时发生错误", e);
        }
    }

    private void initData() {
        SharedPreferences sharedPreferences = getSharedPreferences("phone", MODE_PRIVATE);
        phone = sharedPreferences.getString("phone", null);

        if (TextUtils.isEmpty(phone)) {
            Intent intent = new Intent(VideoCallActivity.this, LoginActivity.class);
            Toast.makeText(VideoCallActivity.this, "登录信息已过期", Toast.LENGTH_SHORT).show();
            startActivity(intent);
            finish();
            return;
        }

        // 【新增】启动定位服务
        startLocation();

        if (tryUseCachedUserInfo()) {
            isUserDataReady = true;
            getCallParams();
        } else {
            fetchUserInfo();
        }
    }

    // ==========================================
    // 【新增】定位核心逻辑开始
    // ==========================================

    /**
     * 启动腾讯定位
     */
    private void startLocation() {
        Log.i(TAG, "正在启动后台定位...");
        if (mLocationManager == null) {
            mLocationManager = TencentLocationManager.getInstance(this);
        }
        // 移除旧更新
        mLocationManager.removeUpdates(this);

        if (mLocationRequest == null) {
            mLocationRequest = TencentLocationRequest.create();
        }

        mLocationRequest.setAllowGPS(true);
        mLocationRequest.setAllowDirection(true);
        mLocationRequest.setInterval(2000); // 2秒定位一次
        mLocationRequest.setRequestLevel(TencentLocationRequest.REQUEST_LEVEL_GEO); // 仅需经纬度

        stableCount = 0;
        isFirstLoc = true;

        int error = mLocationManager.requestLocationUpdates(mLocationRequest, this, Looper.getMainLooper());
        if (error != 0) {
            Log.e(TAG, "定位启动失败, 错误码: " + error);
        } else {
            Log.d(TAG, "定位服务启动成功");
        }
    }

    /**
     * 停止定位
     */
    private void stopLocation() {
        if (mLocationManager != null) {
            mLocationManager.removeUpdates(this);
            Log.d(TAG, "定位服务已停止");
        }
    }

    /**
     * 腾讯定位回调
     */
    @Override
    public void onLocationChanged(TencentLocation tencentLocation, int error, String reason) {
        if (error == TencentLocation.ERROR_OK) {
            double curLat = tencentLocation.getLatitude();
            double curLng = tencentLocation.getLongitude();

            // 判断稳定性逻辑
            if (isFirstLoc) {
                lastLat = curLat;
                lastLng = curLng;
                isFirstLoc = false;
                Log.d(TAG, "首次定位: " + curLat + "," + curLng);
            } else {
                float distance = calculateDistance(lastLat, lastLng, curLat, curLng);
                Log.d(TAG, String.format("距离上次移动: %.2f米 | 稳定计数: %d", distance, stableCount));

                if (distance < DISTANCE_THRESHOLD) {
                    stableCount++; // 没动
                } else {
                    stableCount = 0; // 动了，重置
                    Log.d(TAG, "位置移动，计数重置");
                }

                // 更新基准点
                lastLat = curLat;
                lastLng = curLng;

                // 达到3次稳定，触发上传
                if (stableCount >= STABLE_COUNT_TARGET) {
                    postLocation(curLat, curLng);
                    stopLocation();
                }
            }
        } else {
            Log.e(TAG, "定位失败: " + reason);
        }
    }

    @Override
    public void onStatusUpdate(String name, int status, String desc) {
        // 状态改变，一般不用处理
    }

    /**
     * 【业务方法】上传坐标
     */
    private void postLocation(double lat, double lng) {
        Log.w(TAG, ">>> [触发业务] 坐标稳定，执行 postLocation: " + lat + ", " + lng);
        map.clear();
        map.put("roomId", roomId);
        map.put("userLocation", lat + "," + lng);
        OkhttpUtils.initRequest(3,"PUT",OkhttpUtils.URL + OkhttpUtils.UpdateRoom, OkhttpUtils.toBody(map),"",handlers);
    }

    /**
     * 计算两点间距离（米）
     */
    private float calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, results);
        return results[0];
    }

    // ==========================================
    // 定位逻辑结束
    // ==========================================

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

    private void cacheUserInfo(String code) {
        SharedPreferences.Editor editor = getSharedPreferences(USER_CACHE_PREF, MODE_PRIVATE).edit();
        editor.putString("user_code_" + phone, code);
        editor.putLong("cache_time_" + phone, System.currentTimeMillis());
        editor.apply();
    }

    private void fetchUserInfo() {
        updateCallState("获取用户信息...");
        OkhttpUtils.initRequest(1, "GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", handlers);
    }

    private void getCallParams() {
        roomId = generateRoomId();
        Log.d(TAG, "生成房间号: " + roomId + ", userId: " + userId);

        String cachedToken = getCachedToken(roomId, userId);
        if (cachedToken != null) {
            token = cachedToken;
            isTokenReady = true;
            Log.d(TAG, "使用缓存的Token");
            initRtcEngine();
            return;
        }

        updateCallState("获取视频通话凭证...");
        map.put("roomId", roomId);
        map.put("userId", userId);
        OkhttpUtils.initRequest(2, "POST", OkhttpUtils.URL + OkhttpUtils.GeneralToken, OkhttpUtils.toBody(map), "", handlers);
    }

    private String getCachedToken(String roomId, String userId) {
        SharedPreferences cache = getSharedPreferences(TOKEN_CACHE_PREF, MODE_PRIVATE);
        String key = roomId + "_" + userId;
        String token = cache.getString(key, null);
        long cacheTime = cache.getLong(key + "_time", 0);
        if (token != null && (System.currentTimeMillis() - cacheTime) < TOKEN_CACHE_DURATION) {
            return token;
        }
        return null;
    }

    private void cacheToken(String roomId, String userId, String token) {
        SharedPreferences.Editor editor = getSharedPreferences(TOKEN_CACHE_PREF, MODE_PRIVATE).edit();
        String key = roomId + "_" + userId;
        editor.putString(key, token);
        editor.putLong(key + "_time", System.currentTimeMillis());
        editor.apply();
    }

    private void initRtcEngine() {
        Log.d(TAG, "初始化RTC引擎 - 视频模式 (1080P)");
        try {
            if (mRtcEngine != null) {
                isRtcEngineInitialized = true;
                tryJoinChannel();
                return;
            }
            updateCallState("初始化视频通话组件...");
            mRtcEngine = RtcEngine.create(getApplicationContext(), OkhttpUtils.APP_ID, new RtcEventHandler());

            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);
            mRtcEngine.enableVideo();

            // 1080P + 24FPS + 3000Kbps 配置
            VideoEncoderConfiguration configuration = new VideoEncoderConfiguration(
                    VideoEncoderConfiguration.VD_1280x720,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
            );
            configuration.degradationPrefer = VideoEncoderConfiguration.DEGRADATION_PREFERENCE.MAINTAIN_QUALITY;
            mRtcEngine.setVideoEncoderConfiguration(configuration);

            mRtcEngine.enableAudio();
            mRtcEngine.setAudioProfile(Constants.AUDIO_PROFILE_SPEECH_STANDARD, Constants.AUDIO_SCENARIO_DEFAULT);

            setupLocalVideo(); // 本地全屏
            isRtcEngineInitialized = true;
            tryJoinChannel();
        } catch (Exception e) {
            Log.e(TAG, "RTC引擎初始化失败", e);
            runOnUiThread(() -> {
                showToast("视频通话初始化失败: " + e.getMessage());
                finish();
            });
        }
    }

    private void setupLocalVideo() {
        if (mRtcEngine == null || mLocalVideoContainer == null) return;
        runOnUiThread(() -> {
            try {
                mLocalVideoContainer.removeAllViews();
                mLocalVideoView = mRtcEngine.CreateRendererView(getApplicationContext());
                if (mLocalVideoView != null) {
                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                    );
                    mLocalVideoView.setLayoutParams(layoutParams);
                    mLocalVideoContainer.addView(mLocalVideoView);

                    VideoCanvas localVideoCanvas = new VideoCanvas(mLocalVideoView);
                    localVideoCanvas.renderMode = Constants.RENDER_MODE_HIDDEN;
                    mRtcEngine.setupLocalVideo(localVideoCanvas);
                    mRtcEngine.startPreview();

                    // 强制后置
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (mRtcEngine != null) {
                            try {
                                mRtcEngine.switchCamera();
                                Log.d(TAG, "已切换至后置摄像头");
                                // 尝试开启人脸对焦增强清晰度
                                // mRtcEngine.setCameraAutoFocusFaceModeEnabled(true);
                            } catch (Exception e) {
                                Log.e(TAG, "切换摄像头失败", e);
                            }
                        }
                    }, 500);
                }
            } catch (Exception e) {
                Log.e(TAG, "设置本地视频异常", e);
            }
        });
    }

    private void tryJoinChannel() {
        if (isRtcEngineInitialized && isTokenReady && mRtcEngine != null) {
            joinChannel();
        } else {
            if (!isRtcEngineInitialized) updateCallState("初始化视频通话组件...");
            else if (!isTokenReady) updateCallState("获取视频通话凭证...");

            if (callSeconds < 10) handler.postDelayed(this::tryJoinChannel, 1000);
            else {
                runOnUiThread(() -> {
                    showToast("初始化超时，请重试");
                    finish();
                });
            }
        }
    }

    private void joinChannel() {
        if (mRtcEngine == null) return;
        runOnUiThread(() -> updateCallState("加入房间中..."));
        try {
            int ret = mRtcEngine.joinChannel(token, roomId, "", userId);
            if (ret == 0) {
                isJoinedChannel = true;
                createRoom();
            } else {
                runOnUiThread(() -> {
                    showToast("加入房间失败，错误码：" + ret);
                    finish();
                });
            }
        } catch (Exception e) {
            runOnUiThread(() -> {
                showToast("加入房间异常: " + e.getMessage());
                finish();
            });
        }
    }

    private void createRoom() {
        try {
            map.put("roomId", roomId);
            map.put("callType", 1);
            map.put("requesterId", code);
            map.put("userLocation", "");
            map.put("fullDown",fullDown);
            OkhttpUtils.initRequest(3, "POST", OkhttpUtils.URL + OkhttpUtils.CreateRoom,
                    OkhttpUtils.toBody(map), "", handlers);
            Log.d(TAG, "createRoom: " + map);
        } catch (Exception e) {
            Log.e(TAG, "创建房间请求失败", e);
        }
    }

    private void toggleMute() {
        if (mRtcEngine == null) return;
        try {
            isMuted = !isMuted;
            mRtcEngine.muteLocalAudioStream(isMuted);
            btnMute.setText(isMuted ? "取消静音" : "静音");
            btnMute.setBackgroundResource(isMuted ? R.drawable.silent2 : R.drawable.silent1);
        } catch (Exception e) {
            Log.e(TAG, "切换静音状态失败", e);
        }
    }

    private void endCall() {
        stopCallTimer();
        stopLocation();
        leaveChannel();
        map.clear();
        map.put("roomId", roomId);
        map.put("endTime", TimeUtils.getCurrentDateTime());
        OkhttpUtils.initRequest(4, "PUT", OkhttpUtils.URL + OkhttpUtils.UpdateRoom, OkhttpUtils.toBody(map), "", handlers);

        // 完全重置任务栈，创建新的主界面
        Intent intent = new Intent(VideoCallActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        Toast.makeText(VideoCallActivity.this, "结束通话!", Toast.LENGTH_SHORT).show();
    }

    private void leaveChannel() {
        if (mRtcEngine != null) {
            try {
                if (isJoinedChannel) {
                    mRtcEngine.leaveChannel();
                    isJoinedChannel = false;
                }
            } catch (Exception e) {
                Log.e(TAG, "离开频道异常", e);
            }
            try {
                RtcEngine.destroy();
                mRtcEngine = null;
            } catch (Exception e) {
                Log.e(TAG, "销毁引擎异常", e);
            }
        }
    }

    private void startCallTimer() {
        stopCallTimer();
        runOnUiThread(() -> {
            tvCallTime.setVisibility(View.VISIBLE);
            tvCallTime.setText("00:00");
        });
        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                callSeconds++;
                int minutes = callSeconds / 60;
                int seconds = callSeconds % 60;
                final String timeText = String.format("%02d:%02d", minutes, seconds);
                runOnUiThread(() -> tvCallTime.setText(timeText));
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timeUpdateRunnable);
    }

    private void stopCallTimer() {
        if (timeUpdateRunnable != null) {
            handler.removeCallbacks(timeUpdateRunnable);
            timeUpdateRunnable = null;
        }
    }

    private void updateCallState(String state) {
        runOnUiThread(() -> {
            if (tvCallState != null) tvCallState.setText(state);
        });
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(VideoCallActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String generateRoomId() {
        int number = ThreadLocalRandom.current().nextInt(10000);
        return String.format("%04d", number);
    }

    private class RtcEventHandler extends IRtcEngineEventHandler {
        @Override
        public void onJoinChannelSuccess(String channel, String uid, int elapsed) {
            runOnUiThread(() -> updateCallState("已加入房间"));
        }

        @Override
        public void onUserJoined(String uid, int elapsed) {
            runOnUiThread(() -> {
                updateCallState("对方已加入");
                startCallTimer();
                showToast("对方已连接");
            });
        }

        @Override
        public void onUserOffline(String uid, int reason) {
            runOnUiThread(() -> {
                String reasonStr = reason == USER_OFFLINE_QUIT ? "对方已挂断" : "对方网络断开";
                updateCallState(reasonStr);
                stopCallTimer();
                showToast(reasonStr);
                handler.postDelayed(() -> endCall(), 3000);
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
                initData();
            } else {
                showToast("需要完整权限(相机/麦克风/定位)才能使用功能");
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCallTimer();
        stopLocation(); // 页面销毁时停止定位
        leaveChannel();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        showToast("请点击挂断按钮结束视频通话");
    }

    Handler handlers = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            String json = msg.obj.toString();
            switch (msg.what) {
                case 1: // UserInfo
                    userData = OkhttpUtils.toData(json, UserData.class);
                    if (userData != null && userData.getData() != null) {
                        code = String.valueOf(userData.getData().getUserId());
                        userId = code;
                        cacheUserInfo(code);
                        isUserDataReady = true;
                        getCallParams();
                    } else {
                        runOnUiThread(() -> {
                            showToast("用户数据解析失败");
                            finish();
                        });
                    }
                    break;
                case 2: // Token
                    try {
                        token = json.trim();
                        if (TextUtils.isEmpty(token)) throw new IOException("Token为空");
                        cacheToken(roomId, userId, token);
                        isTokenReady = true;
                        initRtcEngine();
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            showToast("获取凭证失败");
                            finish();
                        });
                    }
                    break;
                case 3:
                    Log.d(TAG, "handleMessage:case3 " + json);
                    break; // CreateRoom
                case 4:
                    userData = OkhttpUtils.toData(json, UserData.class);
                    if (userData.getCode() == 200) {
                        OkhttpUtils.initRequest(5, "DELETE", OkhttpUtils.URL + OkhttpUtils.DelByRoomId + "/" + roomId, null, "", handlers);
                    } else {
                        runOnUiThread(() -> {
                            showToast("数据解析失败");
                            finish();
                        });
                    }
                    break;
                case 5:
                    // DeleteRoom
                    break;
            }
            return false;
        }
    });
}