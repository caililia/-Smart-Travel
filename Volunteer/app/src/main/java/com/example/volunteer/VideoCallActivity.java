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

import com.example.volunteer.activity.login.LoginActivity;
import com.example.volunteer.data.UserData;
import com.example.volunteer.utils.OkhttpUtils;

import org.ar.rtc.Constants;
import org.ar.rtc.IRtcEngineEventHandler;
import org.ar.rtc.RtcEngine;
import org.ar.rtc.VideoEncoderConfiguration;
import org.ar.rtc.video.VideoCanvas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoCallActivity extends AppCompatActivity {
    // 权限请求码
    private static final int REQUEST_BLUETOOTH_PERMISSION = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH_CONNECT  // Android 12+
    };
    private static final String TAG = "VideoCallActivity";

    // 缓存相关常量
    private static final String USER_CACHE_PREF = "user_cache";
    private static final String TOKEN_CACHE_PREF = "token_cache";
    private static final long TOKEN_CACHE_DURATION = 60 * 60 * 1000; // 1小时
    private static final long USER_CACHE_DURATION = 5 * 60 * 1000;   // 5分钟

    private RtcEngine mRtcEngine;
    private TextView tvCallState, tvCallTime;
    private Button btnMute, btnHangUp, btnSwitchCamera;

    // 视频视图容器
    private FrameLayout mLocalVideoContainer;
    private FrameLayout mRemoteVideoContainer;
    private TextureView mLocalVideoView;
    private TextureView mRemoteVideoView;

    private boolean isMuted = false;
    private boolean isCameraSwitched = false;
    private int callSeconds = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeUpdateRunnable;
    private String phone = "";
    private String code = "";

    // 通话参数
    private String roomId = "";
    private int userId;
    private UserData userData;
    private String token = "";
    private HashMap<String, Object> map = new HashMap<>();

    // 状态标志
    private boolean isRtcEngineInitialized = false;
    private boolean isTokenReady = false;
    private boolean isUserDataReady = false;
    private boolean isJoinedChannel = false;
    private boolean isInitializingRtc = false;
    private boolean hasCheckedTimeout = false;
    private AtomicBoolean isDestroyed = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        roomId = intent.getStringExtra("roomId");
        Log.d(TAG, "接收到的roomId: " + roomId);

        if (TextUtils.isEmpty(roomId)) {
            Toast.makeText(this, "房间号不能为空", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 设置布局
        setContentView(R.layout.activity_video_call);

        // 初始化视图
        initViews();

        // 检查并请求权限
        checkAndRequestPermissions();

        // 初始化数据
        initData();

        // 启动超时检查
        startTimeoutCheck();
    }

    /**
     * 启动超时检查
     */
    private void startTimeoutCheck() {
        handler.postDelayed(() -> {
            if (!isDestroyed.get() && !isJoinedChannel && !hasCheckedTimeout) {
                hasCheckedTimeout = true;
                if (!isRtcEngineInitialized || !isTokenReady) {
                    Log.e(TAG, "初始化超时");
                    runOnUiThread(() -> {
                        if (!isDestroyed.get()) {
                            showToast("初始化超时，请检查网络和权限");
                            finish();
                        }
                    });
                }
            }
        }, 15000); // 15秒超时
    }

    /**
     * 检查并请求权限
     */
    private void checkAndRequestPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[0]),
                    REQUEST_BLUETOOTH_PERMISSION);
        } else {
            // 权限已授予，等待数据准备好后初始化RTC
            tryInitRtcEngine();
        }
    }

    /**
     * 尝试初始化RTC引擎（确保数据和权限都准备好）
     */
    private void tryInitRtcEngine() {
        if (isDestroyed.get()) {
            return;
        }

        // 检查权限
        boolean hasAllPermissions = true;
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                hasAllPermissions = false;
                break;
            }
        }

        if (!hasAllPermissions) {
            Log.d(TAG, "权限未完全授予，等待权限");
            return;
        }

        // 检查数据是否准备好
        if (isUserDataReady && isTokenReady) {
            initRtcEngine();
        } else {
            Log.d(TAG, "等待数据准备 - 用户数据: " + isUserDataReady + ", Token: " + isTokenReady);
        }
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        Log.d(TAG, "开始初始化视图");

        try {
            tvCallState = findViewById(R.id.tv_call_state);
            tvCallTime = findViewById(R.id.tv_call_time);

            // 初始化按钮
            btnMute = findViewById(R.id.btn_mute);
            btnHangUp = findViewById(R.id.btn_hang_up);
            btnSwitchCamera = findViewById(R.id.btn_switch_camera);

            // 初始化视频容器
            mLocalVideoContainer = findViewById(R.id.local_video_view_container);
            mRemoteVideoContainer = findViewById(R.id.remote_video_view_container);

            // 设置按钮点击事件
            btnMute.setOnClickListener(v -> {
                Log.d(TAG, "静音按钮被点击");
                toggleMute();
            });

            btnSwitchCamera.setOnClickListener(v -> {
                Log.d(TAG, "切换摄像头按钮被点击");
                switchCamera();
            });

            btnHangUp.setOnClickListener(v -> {
                Log.d(TAG, "挂断按钮被点击");
                endCall();
            });

            Log.d(TAG, "视图初始化完成");

        } catch (Exception e) {
            Log.e(TAG, "初始化视图时发生错误", e);
            Toast.makeText(this, "界面初始化失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * 初始化数据
     */
    private void initData() {
        SharedPreferences sharedPreferences = getSharedPreferences("phone", MODE_PRIVATE);
        phone = sharedPreferences.getString("phone", null);

        if (TextUtils.isEmpty(phone)) {
            Intent intent = new Intent(VideoCallActivity.this, LoginActivity.class);
            String msg = "登录信息已过期，请重新登录";
            Toast.makeText(VideoCallActivity.this, msg, Toast.LENGTH_SHORT).show();
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
            userId = Integer.parseInt(cachedCode);
            Log.d(TAG, "使用缓存的用户信息: " + userId);
            return true;
        }
        return false;
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
     * 获取用户信息
     */
    private void fetchUserInfo() {
        updateCallState("获取用户信息...");
        OkhttpUtils.initRequest(1, "GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", handlers);
    }

    /**
     * 获取通话参数
     */
    private void getCallParams() {
        Log.d(TAG, "生成房间号: " + roomId + ", userId: " + userId);

        String cachedToken = getCachedToken(roomId, String.valueOf(userId));
        if (cachedToken != null) {
            token = cachedToken;
            isTokenReady = true;
            Log.d(TAG, "使用缓存的Token");
            // Token准备好后尝试初始化RTC引擎
            tryInitRtcEngine();
            return;
        }

        updateCallState("获取视频通话凭证...");
        map.put("roomId", roomId);
        map.put("userId", userId);
        OkhttpUtils.initRequest(2, "POST", OkhttpUtils.URL + OkhttpUtils.GeneralToken,
                OkhttpUtils.toBody(map), "", handlers);
    }

    /**
     * Token缓存管理 - 获取缓存的Token
     */
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
     * 初始化RTC引擎
     */
    private void initRtcEngine() {
        // 防止重复调用
        if (isRtcEngineInitialized || isInitializingRtc || isDestroyed.get()) {
            Log.d(TAG, "RTC引擎已在初始化或已初始化，跳过");
            return;
        }

        Log.d(TAG, "初始化RTC引擎 - 视频模式");
        isInitializingRtc = true;

        try {
            if (mRtcEngine != null) {
                Log.d(TAG, "RTC引擎已存在，跳过初始化");
                isRtcEngineInitialized = true;
                isInitializingRtc = false;
                tryJoinChannel();
                return;
            }

            updateCallState("初始化视频通话组件...");

            // 创建RTC引擎
            mRtcEngine = RtcEngine.create(getApplicationContext(), OkhttpUtils.APP_ID, new RtcEventHandler());

            if (mRtcEngine == null) {
                throw new RuntimeException("创建引擎实例失败");
            }

            Log.d(TAG, "RTC引擎创建成功");

            // 设置通信场景
            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);

            // 启用视频模块
            mRtcEngine.enableVideo();

            // 配置视频编码参数
            VideoEncoderConfiguration configuration = new VideoEncoderConfiguration(
                    VideoEncoderConfiguration.VD_640x480,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
            );
            mRtcEngine.setVideoEncoderConfiguration(configuration);

            // 音频配置
            mRtcEngine.enableAudio();
            mRtcEngine.setAudioProfile(Constants.AUDIO_PROFILE_SPEECH_STANDARD,
                    Constants.AUDIO_SCENARIO_DEFAULT);

            isRtcEngineInitialized = true;
            isInitializingRtc = false;

            // 设置本地视频（延迟执行）
            setupLocalVideo();

            Log.d(TAG, "RTC引擎初始化成功");
            tryJoinChannel();

        } catch (Exception e) {
            isInitializingRtc = false;
            Log.e(TAG, "RTC引擎初始化失败", e);
            runOnUiThread(() -> {
                if (!isDestroyed.get()) {
                    showToast("视频通话初始化失败: " + e.getMessage());
                    finish();
                }
            });
        }
    }

    /**
     * 设置本地视频
     */
    private void setupLocalVideo() {
        Log.d(TAG, "setupLocalVideo开始");

        if (mRtcEngine == null || isDestroyed.get()) {
            Log.e(TAG, "无法设置本地视频: RTC引擎为空或Activity已销毁");
            return;
        }

        // 延迟执行，确保视图完全初始化
        handler.post(() -> {
            if (isDestroyed.get() || mRtcEngine == null) {
                return;
            }

            try {
                // 确保容器存在
                if (mLocalVideoContainer == null) {
                    mLocalVideoContainer = findViewById(R.id.local_video_view_container);
                    if (mLocalVideoContainer == null) {
                        Log.e(TAG, "找不到本地视频容器");
                        return;
                    }
                }

                // 清除容器中的旧视图
                mLocalVideoContainer.removeAllViews();

                // 创建视频渲染视图
                mLocalVideoView = mRtcEngine.CreateRendererView(getApplicationContext());
                if (mLocalVideoView == null) {
                    Log.e(TAG, "创建本地视频视图失败");
                    return;
                }

                Log.d(TAG, "视频视图创建成功，类型: " + mLocalVideoView.getClass().getSimpleName());

                // 设置视图布局参数
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );
                mLocalVideoView.setLayoutParams(layoutParams);

                // 添加到容器
                mLocalVideoContainer.addView(mLocalVideoView);
                mLocalVideoContainer.setVisibility(View.VISIBLE);

                // 延迟一点设置VideoCanvas，确保视图已添加到窗口
                mLocalVideoView.post(() -> {
                    if (!isDestroyed.get() && mRtcEngine != null && mLocalVideoView != null) {
                        try {
                            VideoCanvas localVideoCanvas = new VideoCanvas(mLocalVideoView);
                            localVideoCanvas.renderMode = Constants.RENDER_MODE_HIDDEN;
                            mRtcEngine.setupLocalVideo(localVideoCanvas);
                            mRtcEngine.startPreview();
                            Log.d(TAG, "本地视频设置完成");
                        } catch (Exception e) {
                            Log.e(TAG, "设置本地VideoCanvas失败", e);
                        }
                    }
                });

                Log.d(TAG, "本地视频视图已添加到容器");

            } catch (Exception e) {
                Log.e(TAG, "设置本地视频异常", e);
            }
        });
    }

    /**
     * 设置远程视频
     */
    private void setupRemoteVideo(String uid) {
        if (mRtcEngine == null || mRemoteVideoContainer == null || isDestroyed.get()) {
            return;
        }

        handler.post(() -> {
            if (isDestroyed.get() || mRtcEngine == null) {
                return;
            }

            try {
                mRemoteVideoContainer.removeAllViews();

                // 创建远程视频视图
                mRemoteVideoView = mRtcEngine.CreateRendererView(getApplicationContext());
                if (mRemoteVideoView != null) {
                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                    );
                    mRemoteVideoView.setLayoutParams(layoutParams);
                    mRemoteVideoContainer.addView(mRemoteVideoView);
                    mRemoteVideoContainer.setVisibility(View.VISIBLE);

                    VideoCanvas remoteVideoCanvas = new VideoCanvas(mRemoteVideoView);
                    remoteVideoCanvas.renderMode = Constants.RENDER_MODE_HIDDEN;
                    remoteVideoCanvas.uid = uid;

                    mRtcEngine.setupRemoteVideo(remoteVideoCanvas);
                    Log.d(TAG, "远程视频设置完成 - UID: " + uid);
                }
            } catch (Exception e) {
                Log.e(TAG, "设置远程视频异常", e);
            }
        });
    }

    /**
     * 协调加入房间的时机
     */
    private void tryJoinChannel() {
        if (isDestroyed.get()) {
            return;
        }

        Log.d(TAG, "检查加入房间条件 - RTC初始化: " + isRtcEngineInitialized +
                ", Token准备: " + isTokenReady);

        if (isRtcEngineInitialized && isTokenReady && mRtcEngine != null && !isJoinedChannel) {
            Log.d(TAG, "所有条件满足，准备加入频道");
            joinChannel();
        } else {
            // 显示相应状态
            if (!isRtcEngineInitialized) {
                updateCallState("初始化视频通话组件...");
            } else if (!isTokenReady) {
                updateCallState("获取视频通话凭证...");
            } else if (mRtcEngine == null) {
                updateCallState("初始化失败...");
                Log.e(TAG, "RTC引擎为空");
            }
        }
    }

    /**
     * 加入频道
     */
    private void joinChannel() {
        if (mRtcEngine == null || isDestroyed.get()) {
            Log.e(TAG, "无法加入频道: RTC引擎为空或Activity已销毁");
            return;
        }

        if (isJoinedChannel) {
            Log.d(TAG, "已经加入频道，跳过");
            return;
        }

        runOnUiThread(() -> updateCallState("加入房间中..."));

        Log.d(TAG, "加入视频房间: roomId=" + roomId + ", userId=" + userId);

        try {
            // 加入频道
            int ret = mRtcEngine.joinChannel(token, roomId, "", String.valueOf(userId));

            if (ret == 0) {
                Log.d(TAG, "加入频道成功");
                isJoinedChannel = true;
                hasCheckedTimeout = true; // 成功加入，取消超时标记
                // 创建房间（只在加入频道成功后创建）
                createRoom();
            } else {
                Log.e(TAG, "加入房间失败，错误码：" + ret);
                runOnUiThread(() -> {
                    if (!isDestroyed.get()) {
                        showToast("加入房间失败，错误码：" + ret);
                        finish();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "加入频道异常", e);
            runOnUiThread(() -> {
                if (!isDestroyed.get()) {
                    showToast("加入房间异常: " + e.getMessage());
                    finish();
                }
            });
        }
    }

    /**
     * 创建房间
     */
    private void createRoom() {
        try {
            HashMap<String, Object> roomMap = new HashMap<>();
            roomMap.put("roomId", roomId);
            OkhttpUtils.initRequest(3, "POST", OkhttpUtils.URL + OkhttpUtils.CreateRoom,
                    OkhttpUtils.toBody(roomMap), "", handlers);
            Log.d(TAG, "发送创建房间请求: " + roomId);
        } catch (Exception e) {
            Log.e(TAG, "创建房间请求失败", e);
        }
    }

    /**
     * 切换摄像头
     */
    private void switchCamera() {
        if (mRtcEngine != null && !isDestroyed.get()) {
            try {
                mRtcEngine.switchCamera();
                isCameraSwitched = !isCameraSwitched;
                btnSwitchCamera.setText(isCameraSwitched ? "前置" : "后置");
                Log.d(TAG, "切换摄像头: " + (isCameraSwitched ? "前置" : "后置"));
            } catch (Exception e) {
                Log.e(TAG, "切换摄像头失败", e);
                showToast("切换摄像头失败");
            }
        }
    }

    /**
     * 切换静音状态
     */
    private void toggleMute() {
        if (mRtcEngine == null || isDestroyed.get()) return;

        try {
            isMuted = !isMuted;
            mRtcEngine.muteLocalAudioStream(isMuted);
            btnMute.setText(isMuted ? "取消静音" : "静音");
            btnMute.setBackgroundResource(isMuted ? R.drawable.silent2 : R.drawable.silent1);
            Log.d(TAG, "静音状态: " + isMuted);
        } catch (Exception e) {
            Log.e(TAG, "切换静音状态失败", e);
        }
    }

    /**
     * 结束通话
     */
    private void endCall() {
        Log.d(TAG, "结束通话");
        stopCallTimer();
        leaveChannel();
        finish();
    }

    /**
     * 离开频道
     */
    private void leaveChannel() {
        Log.d(TAG, "离开频道");
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
                Log.d(TAG, "RTC引擎已销毁");
            } catch (Exception e) {
                Log.e(TAG, "销毁引擎异常", e);
            }
        }
    }

    /**
     * 开始通话计时
     */
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
                runOnUiThread(() -> {
                    if (tvCallTime != null) {
                        tvCallTime.setText(timeText);
                    }
                });
                if (!isDestroyed.get()) {
                    handler.postDelayed(this, 1000);
                }
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
        runOnUiThread(() -> {
            if (tvCallState != null && !isDestroyed.get()) {
                tvCallState.setText(state);
                Log.d(TAG, "通话状态更新: " + state);
            }
        });
    }

    /**
     * 显示Toast提示
     */
    private void showToast(String message) {
        runOnUiThread(() -> {
            if (!isDestroyed.get()) {
                Toast.makeText(VideoCallActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * RTC事件回调处理
     */
    private class RtcEventHandler extends IRtcEngineEventHandler {

        @Override
        public void onJoinChannelSuccess(String channel, String uid, int elapsed) {
            Log.d(TAG, "加入频道成功: " + channel + ", uid: " + uid);
            runOnUiThread(() -> {
                if (!isDestroyed.get()) {
                    updateCallState("已加入房间");
                }
            });
        }

        @Override
        public void onUserJoined(String uid, int elapsed) {
            Log.d(TAG, "用户加入: " + uid + ", 耗时: " + elapsed + "ms");
            runOnUiThread(() -> {
                if (!isDestroyed.get()) {
                    updateCallState("对方已加入，正在视频通话中...");
                    startCallTimer();
                    setupRemoteVideo(uid);
                    showToast("对方已加入通话");
                }
            });
        }

        @Override
        public void onFirstLocalVideoFrame(int width, int height, int elapsed) {
            Log.d(TAG, "本地视频首帧渲染: " + width + "x" + height + ", 耗时: " + elapsed + "ms");
            runOnUiThread(() -> {
                if (!isDestroyed.get()) {
                    showToast("本地摄像头已启动");
                    Log.d(TAG, "本地视频画面已显示");
                }
            });
        }

        @Override
        public void onFirstRemoteVideoDecoded(String uid, int width, int height, int elapsed) {
            Log.d(TAG, "远程视频首帧解码: " + uid + ", 分辨率: " + width + "x" + height + ", 耗时: " + elapsed + "ms");
            runOnUiThread(() -> {
                if (!isDestroyed.get()) {
                    setupRemoteVideo(uid);
                    showToast("远程视频已连接");
                    Log.d(TAG, "远程视频画面已显示");
                }
            });
        }

        @Override
        public void onUserOffline(String uid, int reason) {
            Log.d(TAG, "用户离线: " + uid + ", 原因: " + reason);
            runOnUiThread(() -> {
                if (!isDestroyed.get()) {
                    String reasonStr = reason == USER_OFFLINE_QUIT ? "对方已挂断" : "对方网络断开";
                    updateCallState(reasonStr);
                    stopCallTimer();
                    showToast(reasonStr);

                    // 3秒后自动结束通话
                    handler.postDelayed(() -> {
                        if (!isDestroyed.get()) {
                            endCall();
                        }
                    }, 3000);
                }
            });
        }

        @Override
        public void onError(int err) {
            Log.e(TAG, "RTC错误: " + err);
            runOnUiThread(() -> {
                if (!isDestroyed.get()) {
                    //showToast("视频通话错误: " + err);
                    endCall();
                }
            });
        }

        @Override
        public void onLocalVideoStateChanged(int state, int error) {
            String stateStr = "";
            switch (state) {
                case Constants.LOCAL_VIDEO_STREAM_STATE_CAPTURING:
                    stateStr = "采集中";
                    break;
                case Constants.LOCAL_VIDEO_STREAM_STATE_ENCODING:
                    stateStr = "编码中";
                    break;
                case Constants.LOCAL_VIDEO_STREAM_STATE_FAILED:
                    stateStr = "失败";
                    break;
            }
            Log.d(TAG, "本地视频状态: " + stateStr + ", 错误码: " + error);
        }

        @Override
        public void onRemoteVideoStateChanged(String uid, int state, int reason, int elapsed) {
            String stateStr = "";
            switch (state) {
                case Constants.REMOTE_VIDEO_STATE_STARTING:
                    stateStr = "开始";
                    break;
                case Constants.REMOTE_VIDEO_STATE_DECODING:
                    stateStr = "解码中";
                    break;
                case Constants.REMOTE_VIDEO_STATE_FROZEN:
                    stateStr = "卡顿";
                    break;
                case Constants.REMOTE_VIDEO_STATE_FAILED:
                    stateStr = "失败";
                    break;
            }
            Log.d(TAG, "远程视频状态: " + uid + " - " + stateStr + ", 原因: " + reason);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Log.d(TAG, "所有权限已授予");
                // 权限已授予，尝试初始化RTC引擎
                tryInitRtcEngine();
            } else {
                Log.e(TAG, "权限被拒绝");
                Toast.makeText(this, "需要蓝牙权限才能进行视频通话", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Activity销毁");
        isDestroyed.set(true);
        stopCallTimer();
        leaveChannel();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        showToast("请点击挂断按钮结束视频通话");
    }

    /**
     * 网络请求回调处理器
     */
    Handler handlers = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (isDestroyed.get()) {
                return false;
            }

            String json = msg.obj.toString();
            Log.d(TAG, "handleMessage: " + json);
            switch (msg.what) {
                case 1: // 用户信息
                    userData = OkhttpUtils.toData(json, UserData.class);
                    if (userData != null && userData.getData() != null) {
                        userId = userData.getData().getUserId();
                        cacheUserInfo(String.valueOf(userId));
                        isUserDataReady = true;
                        getCallParams();
                    } else {
                        runOnUiThread(() -> {
                            if (!isDestroyed.get()) {
                                showToast("用户数据解析失败");
                                finish();
                            }
                        });
                    }
                    break;

                case 2: // Token
                    try {
                        token = json.trim();
                        if (TextUtils.isEmpty(token)) {
                            throw new IOException("Token为空");
                        }
                        cacheToken(roomId, String.valueOf(userId), token);
                        isTokenReady = true;
                        Log.d(TAG, "Token获取成功");
                        // Token准备好后尝试初始化RTC引擎
                        tryInitRtcEngine();
                    } catch (Exception e) {
                        Log.e(TAG, "处理Token响应失败", e);
                        runOnUiThread(() -> {
                            if (!isDestroyed.get()) {
                                showToast("获取视频通话凭证失败");
                                finish();
                            }
                        });
                    }
                    break;

                case 3: // 创建房间
                    Log.d(TAG, "创建房间响应: " + json);
                    break;

                case 4: // 删除房间
                    Log.d(TAG, "删除房间响应: " + json);
                    break;
            }
            return false;
        }
    });
}