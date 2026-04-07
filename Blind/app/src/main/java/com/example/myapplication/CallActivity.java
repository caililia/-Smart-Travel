package com.example.myapplication;

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

import com.example.myapplication.R;
import com.example.myapplication.activity.login.LoginActivity;
import com.example.myapplication.data.UserData;
import com.example.myapplication.utils.OkhttpUtils;
import com.example.myapplication.utils.TimeUtils;

import org.ar.rtc.IRtcEngineEventHandler;
import org.ar.rtc.RtcEngine;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;

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

    // ========== 志愿者接单并发控制 ==========
    private static final Object ORDER_LOCK = new Object();  // 互斥锁
    private volatile int helpStatus = 0;  // 求助状态：0-未接单 1-已接单 2-通话中
    private String acceptedVolunteerId = null;  // 接单成功的志愿者ID
    private PriorityQueue<VolunteerInfo> pendingQueue;  // 优先级队列

    private RtcEngine mRtcEngine;
    private TextView tvCallState, tvCallTime;
    private Button btnMute, btnHangUp;
    private boolean isMuted = false;
    private int callSeconds = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeUpdateRunnable;
    private String phone = "";

    // 通话参数
    private String roomId = "";
    private String userId;
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

        // 检查缓存是否在有效期内
        if (cachedCode != null && (System.currentTimeMillis() - cacheTime) < USER_CACHE_DURATION) {
            userId = cachedCode;
            Log.d(TAG, "使用缓存的用户信息: " + userId);
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
     * 获取通话参数
     */
    private void getCallParams() {
        roomId = generateRoomId();
        Log.d(TAG, "生成房间号: " + roomId + ", userId: " + userId);

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
     * 生成房间号
     */
    private String generateRoomId() {
        int number = java.util.concurrent.ThreadLocalRandom.current().nextInt(10000);
        return String.format("%04d", number);
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
     * 志愿者尝试接单（多志愿者同时调用，只有一个能成功）
     * @param volunteerId 志愿者ID
     * @param level 志愿者等级
     * @return true-接单成功，false-接单失败
     */
    private boolean tryAcceptOrder(String volunteerId, int level) {
        synchronized (ORDER_LOCK) {
            // 检查状态：只有未接单(0)才能接单
            if (helpStatus != 0) {
                Log.d(TAG, "接单失败：订单已被接，当前状态=" + helpStatus);
                return false;
            }

            // 检查是否已有接单者
            if (acceptedVolunteerId != null) {
                Log.d(TAG, "接单失败：已有志愿者接单，id=" + acceptedVolunteerId);
                return false;
            }

            // 初始化优先级队列
            if (pendingQueue == null) {
                pendingQueue = new PriorityQueue<>();
            }

            // 加入队列
            pendingQueue.offer(new VolunteerInfo(volunteerId, level));

            // 检查是否是优先级最高的志愿者
            VolunteerInfo best = pendingQueue.peek();
            if (best != null && best.volunteerId.equals(volunteerId)) {
                // 接单成功
                acceptedVolunteerId = volunteerId;
                helpStatus = 1;  // 已接单

                // 清空队列，其他志愿者不再参与
                pendingQueue.clear();

                Log.d(TAG, "接单成功！志愿者=" + volunteerId + ", 等级=" + level);
                return true;
            }

            Log.d(TAG, "接单失败：优先级不足，当前等级=" + level +
                    ", 最高等级=" + (best != null ? best.level : 0));
            return false;
        }
    }

    /**
     * 更新求助状态
     */
    private void updateHelpStatus(int status) {
        synchronized (ORDER_LOCK) {
            this.helpStatus = status;
            if (status == 2) {
                Log.d(TAG, "状态更新：通话中，接单志愿者=" + acceptedVolunteerId);
            }
        }
    }

    /**
     * 重置订单（通话结束后调用）
     */
    private void resetOrder() {
        synchronized (ORDER_LOCK) {
            helpStatus = 0;
            acceptedVolunteerId = null;
            if (pendingQueue != null) {
                pendingQueue.clear();
            }
            Log.d(TAG, "订单已重置");
        }
    }

    /**
     * 获取当前接单状态
     */
    private int getHelpStatus() {
        return helpStatus;
    }

    /**
     * 获取志愿者等级（需要从UserData中获取，暂时默认返回1）
     */
    private int getUserLevel() {
        // TODO: 根据实际业务从UserData中获取志愿者等级
        return 1;
    }

    // ========== 志愿者信息内部类 ==========

    /**
     * 志愿者信息（按等级排序）
     */
    private static class VolunteerInfo implements Comparable<VolunteerInfo> {
        String volunteerId;
        int level;  // 等级越高优先级越高

        VolunteerInfo(String volunteerId, int level) {
            this.volunteerId = volunteerId;
            this.level = level;
        }

        @Override
        public int compareTo(VolunteerInfo other) {
            return Integer.compare(other.level, this.level);  // 降序
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

        // 尝试接单（userId 就是当前用户ID）
        int volunteerLevel = getUserLevel();
        if (!tryAcceptOrder(userId, volunteerLevel)) {
            // 接单失败，说明已有其他志愿者接单
            runOnUiThread(() -> {
                showToast("已有其他志愿者接单");
                finish();
            });
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
        map.put("callType", 0);
        map.put("requesterId", userId);
        map.put("userLocation", null);
        map.put("helperId", userId);
        OkhttpUtils.initRequest(3, "POST", OkhttpUtils.URL + OkhttpUtils.CreateRoom, OkhttpUtils.toBody(map), "", handlers);
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
        stopCallTimer();
        leaveChannel();
        resetOrder();  // 重置订单状态
        map.clear();
        map.put("roomId", roomId);
        map.put("endTime", TimeUtils.getCurrentDateTime());
        OkhttpUtils.initRequest(4, "PUT", OkhttpUtils.URL + OkhttpUtils.UpdateRoom, OkhttpUtils.toBody(map), "", handlers);
        finish();
        Toast.makeText(CallActivity.this, "结束通话!", Toast.LENGTH_SHORT).show();
    }

    /**
     * 离开频道
     */
    private void leaveChannel() {
        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
            RtcEngine.destroy();
            mRtcEngine = null;
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
            // 加入成功，标记为通话中
            updateHelpStatus(2);
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
                // 重置订单状态
                resetOrder();
                // 3秒后自动结束通话
                handler.postDelayed(() -> endCall(), 3000);
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
                /*获取用户信息*/
                case 1:
                    Log.d(TAG, "TAG: " + json);
                    userData = OkhttpUtils.toData(json, UserData.class);
                    Log.d(TAG, "TAG2: " + userData.getMessage());
                    Log.d(TAG, "TAG3: " + userData.getData());
                    if (userData != null && userData.getData() != null) {
                        userId = String.valueOf(userData.getData().getUserId());
                        // 缓存用户信息
                        cacheUserInfo(userId);
                        isUserDataReady = true;
                        getCallParams();
                    } else {
                        // 添加详细的错误诊断
                        Log.e(TAG, "========== 用户数据解析失败诊断开始 ==========");
                        Log.e(TAG, "userData是否为null: " + (userData == null));

                        if (userData != null) {
                            Log.e(TAG, "user字段是否为null: " + (userData.getData() == null));
                            Log.e(TAG, "响应码: " + userData.getCode());
                            Log.e(TAG, "响应消息: " + userData.getMessage());
                            Log.e(TAG, "Token: " + userData.getToken());
                        }

                        // 尝试手动解析JSON，找出具体哪个字段导致问题
                        try {
                            JSONObject jsonObject = new JSONObject(json);
                            Log.e(TAG, "完整JSON解析成功");

                            // 检查是否有user字段
                            if (jsonObject.has("user")) {
                                if (jsonObject.isNull("user")) {
                                    Log.e(TAG, "user字段存在但值为null");
                                } else {
                                    JSONObject userJson = jsonObject.getJSONObject("user");
                                    Log.e(TAG, "user字段内容: " + userJson.toString());

                                    // 逐个检查user对象中的字段
                                    Iterator<String> keys = userJson.keys();
                                    while (keys.hasNext()) {
                                        String key = keys.next();
                                        Object value = userJson.get(key);
                                        Log.e(TAG, "user字段 - " + key + ": " + value +
                                                " (类型: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
                                    }
                                }
                            } else {
                                Log.e(TAG, "JSON中没有user字段");
                            }

                            // 检查其他字段
                            if (jsonObject.has("code")) {
                                Log.e(TAG, "code字段: " + jsonObject.get("code") +
                                        " (类型: " + jsonObject.get("code").getClass().getSimpleName() + ")");
                            }
                            if (jsonObject.has("message")) {
                                Log.e(TAG, "message字段: " + jsonObject.get("message") +
                                        " (类型: " + jsonObject.get("message").getClass().getSimpleName() + ")");
                            }
                            if (jsonObject.has("token")) {
                                Log.e(TAG, "token字段: " + jsonObject.get("token") +
                                        " (类型: " + jsonObject.get("token").getClass().getSimpleName() + ")");
                            }

                        } catch (JSONException e) {
                            Log.e(TAG, "手动解析JSON失败", e);
                        }

                        Log.e(TAG, "========== 用户数据解析失败诊断结束 ==========");

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
                    Log.d(TAG, "handleMessage:case3 " + json);
                    break;

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