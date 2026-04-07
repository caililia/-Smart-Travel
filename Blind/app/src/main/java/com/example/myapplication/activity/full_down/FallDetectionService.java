package com.example.myapplication.activity.full_down;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FallDetectionService extends Service implements SensorEventListener {

    private static final String TAG = "FallDetection";
    private static final String CHANNEL_ID = "fall_detection_channel";
    private static final int NOTIFICATION_ID = 1001;

    // 前后台状态
    private boolean isAppInForeground = true;
    private boolean isFallConfirmed = false;
    private long backgroundStartTime = 0;
    private static final long BACKGROUND_TIMEOUT = 10 * 60 * 1000; // 10分钟

    // 传感器管理器
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    // 电源管理
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private Location lastKnownLocation;
    private static final float MOVEMENT_THRESHOLD = 50; // 50米移动阈值

    // 日志计数器
    private int logCounter = 0;

    // 振动器
    private Vibrator vibrator;

    // 线程池
    private ExecutorService executorService;
    private Handler mainHandler;

    // ========== 检测状态 ==========
    private enum DetectionState {
        NORMAL,      // 正常监控
        FREE_FALL,   // 自由落体
        IMPACT,      // 撞击
        POST_FALL    // 跌倒后
    }

    private DetectionState currentState = DetectionState.NORMAL;
    private long freeFallStartTime = 0;
    private long impactTime = 0;

    // ========== 数据缓存 ==========
    private float[] lastAccel = new float[3];
    private float[] lastGyro = new float[3];
    private float[] gravity = new float[3];
    private float[] linearAccel = new float[3];

    // 加速度历史缓存（用于平滑）
    private static final int HISTORY_SIZE = 20;
    private float[] accelHistory = new float[HISTORY_SIZE];
    private int historyIndex = 0;

    // ========== 机器学习窗口数据 ==========
    private static final int WINDOW_SIZE = 30;  // 窗口大小
    private float[] dataWindow = new float[WINDOW_SIZE];
    private int windowIndex = 0;
    private boolean isWindowFull = false;

    // ========== 基础阈值 ==========
    private static final float GRAVITY_EARTH = 9.81f;
    private static final float BASE_FREE_FALL = 8.0f;      // 基础失重阈值
    private static final float BASE_IMPACT = 14.0f;        // 基础撞击阈值
    private static final float BASE_ANGLE = 40.0f;         // 基础角度阈值
    private static final float LIGHT_IMPACT_THRESHOLD = 12.0f;  // 轻撞击预警
    private static final float WARNING_IMPACT = 11.0f;      // 预警阈值
    private static final float WARNING_ANGLE = 35.0f;       // 预警角度

    // 时间阈值
    private static final long FREE_FALL_TIMEOUT = 1000;     // 自由落体最长等待
    private static final long POST_FALL_CHECK_TIME = 800;   // 跌倒后快速确认
    private static final long POST_FALL_STATIC_TIME = 500;  // 静止确认时间
    private static final long MIN_FALL_INTERVAL = 5000;     // 最小触发间隔

    // 用户个性化参数
    private float userHeight = 170f;      // 用户身高(cm)
    private float userWeight = 70f;        // 用户体重(kg)
    private int userAge = 50;               // 用户年龄
    private boolean useCane = false;        // 是否使用导盲杖

    // ========== 动态阈值（由ThresholdCalculator计算）==========
    private float dynamicFreeFallThreshold;
    private float dynamicImpactThreshold;
    private float dynamicAngleThreshold;
    private float dynamicStaticThreshold;

    // 检测计数器
    private int freeFallCount = 0;
    private int impactCount = 0;
    private long lastFallTime = 0;

    // 机器学习置信度阈值
    private static final float ML_CONFIDENCE_THRESHOLD = 0.6f;  // 60%以上才确认

    // 广播接收器 - 监听应用前后台切换
    private BroadcastReceiver appStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                checkAndUpdateForegroundState(true);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                checkAndUpdateForegroundState(false);
            }
        }
    };

    // 跌倒确认结果的广播接收器
    private BroadcastReceiver fallResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.example.myapplication.activity.full_down.FALL_RESULT".equals(action)) {
                boolean isSafe = intent.getBooleanExtra("is_safe", false);
                String resultAction = intent.getStringExtra("action");

                if (isSafe) {
                    // 用户确认安全，立即恢复检测
                    resumeDetection();
                    Log.i(TAG, "用户确认安全，立即恢复检测");
                } else {
                    // 用户需要帮助，暂停10分钟
                    if ("PAUSE_10_MINUTES".equals(resultAction)) {
                        long pauseStartTime = intent.getLongExtra("timestamp", System.currentTimeMillis());

                        // 记录用户位置
                        float latitude = intent.getFloatExtra("latitude", 0);
                        float longitude = intent.getFloatExtra("longitude", 0);
                        if (latitude != 0 && longitude != 0) {
                            saveUserLocation(latitude, longitude);
                        }

                        pauseDetection(10 * 60 * 1000); // 暂停10分钟
                        Log.i(TAG, "用户需要帮助，暂停检测10分钟");
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "跌倒检测服务启动 - 集成机器学习");

        initManagers();
        initSensors();
        registerReceivers();
        calculateDynamicThresholds();
        startForegroundService();
        acquireWakeLock();
        logThresholdStatus();

        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 初始化管理器
     */
    private void initManagers() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * 初始化传感器
     */
    private void initSensors() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) {
            Log.e(TAG, "设备不支持加速度传感器");
            stopSelf();
            return;
        }

        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroscope == null) {
            Log.w(TAG, "设备不支持陀螺仪，将使用加速度计进行检测");
        }

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
        }
    }

    /**
     * 注册广播接收器
     */
    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(appStateReceiver, filter);

        IntentFilter fallFilter = new IntentFilter("com.example.myapplication.activity.full_down.FALL_RESULT");
        registerReceiver(fallResultReceiver, fallFilter);
    }

    /**
     * 使用ThresholdCalculator计算动态阈值
     */
    private void calculateDynamicThresholds() {
        // 计算自由落体阈值（固定值）
        dynamicFreeFallThreshold = com.example.myapplication.activity.full_down.ThresholdCalculator.calculateFreeFallThreshold();

        // 计算撞击阈值（根据身高体重）
        dynamicImpactThreshold = com.example.myapplication.activity.full_down.ThresholdCalculator.calculateImpactThreshold(userHeight, userWeight);
        dynamicImpactThreshold = com.example.myapplication.activity.full_down.ThresholdCalculator.adjustByAge(userAge, dynamicImpactThreshold);

        // 计算角度阈值（根据身高）
        dynamicAngleThreshold = com.example.myapplication.activity.full_down.ThresholdCalculator.calculateAngleThreshold(userHeight);

        // 计算静止阈值
        dynamicStaticThreshold = com.example.myapplication.activity.full_down.ThresholdCalculator.calculateStaticThreshold();

        Log.d(TAG, "ThresholdCalculator计算结果:");
        Log.d(TAG, "用户BMI: " + (userWeight / ((userHeight/100f) * (userHeight/100f))));
        Log.d(TAG, "动态阈值 - 失重: " + dynamicFreeFallThreshold +
                ", 撞击: " + dynamicImpactThreshold +
                ", 角度: " + dynamicAngleThreshold);
    }

    /**
     * 输出阈值状态
     */
    private void logThresholdStatus() {
        Log.i(TAG, "========== 跌倒检测配置 ==========");
        Log.i(TAG, "失重阈值: <" + dynamicFreeFallThreshold + " m/s²");
        Log.i(TAG, "撞击阈值: >" + dynamicImpactThreshold + " m/s²");
        Log.i(TAG, "角度阈值: >" + dynamicAngleThreshold + "°");
        Log.i(TAG, "预警阈值: >" + WARNING_IMPACT + " m/s², >" + WARNING_ANGLE + "°");
        Log.i(TAG, "机器学习置信度阈值: " + (ML_CONFIDENCE_THRESHOLD * 100) + "%");
        Log.i(TAG, "后台超时: 10分钟");
        Log.i(TAG, "移动检测阈值: " + MOVEMENT_THRESHOLD + "米");
        Log.i(TAG, "==================================");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isFallConfirmed) return;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            handleAccelerometer(event);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            handleGyroscope(event);
        }
    }

    /**
     * 处理加速度数据
     */
    private void handleAccelerometer(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        lastAccel[0] = x;
        lastAccel[1] = y;
        lastAccel[2] = z;

        final float alpha = 0.8f;
        gravity[0] = alpha * gravity[0] + (1 - alpha) * x;
        gravity[1] = alpha * gravity[1] + (1 - alpha) * y;
        gravity[2] = alpha * gravity[2] + (1 - alpha) * z;

        float totalAccel = (float) Math.sqrt(x*x + y*y + z*z);

        // 更新平滑缓存
        accelHistory[historyIndex] = totalAccel;
        historyIndex = (historyIndex + 1) % HISTORY_SIZE;
        float smoothedAccel = getSmoothedAccel();

        // 更新机器学习窗口数据
        dataWindow[windowIndex] = totalAccel;
        windowIndex = (windowIndex + 1) % WINDOW_SIZE;
        if (windowIndex == 0) {
            isWindowFull = true;
        }

        // 计算角度
        float[] angles = calculateAngles();
        float maxTilt = Math.max(Math.abs(angles[0]), Math.abs(angles[1]));

        // 日志
        logCounter++;
        if (logCounter % 20 == 0) {
            String stateStr;
            switch (currentState) {
                case FREE_FALL: stateStr = "自由落体"; break;
                case IMPACT: stateStr = "撞击"; break;
                case POST_FALL: stateStr = "跌倒后"; break;
                default: stateStr = "正常";
            }
            Log.d(TAG, String.format(Locale.CHINA,
                    "加速度: %.2f m/s² | 倾斜: %.1f° | 状态: %s | 阈值: 失重<%.1f 撞击>%.1f 角度>%.1f",
                    totalAccel, maxTilt, stateStr, dynamicFreeFallThreshold,
                    dynamicImpactThreshold, dynamicAngleThreshold));
        }

        // 预警检测
        if (totalAccel > WARNING_IMPACT && maxTilt > WARNING_ANGLE) {
            Log.w(TAG, "⚠️ 跌倒预警：检测到异常动作");
        }

        // 状态机检测
        detectFallState(smoothedAccel, maxTilt);
    }

    /**
     * 跌倒检测状态机
     */
    private void detectFallState(float accel, float tilt) {
        long currentTime = System.currentTimeMillis();

        switch (currentState) {
            case NORMAL:
                if (accel < dynamicFreeFallThreshold) {
                    currentState = DetectionState.FREE_FALL;
                    freeFallStartTime = currentTime;
                    freeFallCount++;
                    Log.i(TAG, "检测到自由落体 #" + freeFallCount);
                }
                break;

            case FREE_FALL:
                if (currentTime - freeFallStartTime > FREE_FALL_TIMEOUT) {
                    Log.d(TAG, "自由落体超时，重置检测");
                    resetState();
                    break;
                }

                if (accel > dynamicImpactThreshold) {
                    currentState = DetectionState.IMPACT;
                    impactTime = currentTime;
                    impactCount++;
                    Log.i(TAG, "检测到撞击 #" + impactCount);

                    if (tilt > dynamicAngleThreshold) {
                        Log.d(TAG, "角度条件满足，开始机器学习验证");
                        validateWithMachineLearning();
                    } else {
                        Log.d(TAG, "角度不足，继续观察");
                    }
                }
                break;

            case IMPACT:
                if (Math.abs(accel - GRAVITY_EARTH) < 3.0f) {
                    long staticDuration = currentTime - impactTime;
                    if (staticDuration > POST_FALL_STATIC_TIME) {
                        if (tilt > dynamicAngleThreshold) {
                            validateWithMachineLearning();
                        } else {
                            Log.d(TAG, "角度不足，可能是坐下/躺下");
                            resetState();
                        }
                    }
                } else if (currentTime - impactTime > POST_FALL_CHECK_TIME) {
                    Log.d(TAG, "撞击后移动时间过长，重置检测");
                    resetState();
                }
                break;
        }
    }

    /**
     * 使用机器学习验证
     */
    private void validateWithMachineLearning() {
        if (!isWindowFull) {
            Log.d(TAG, "窗口数据未满，直接触发跌倒");
            startPostFallCheck();
            return;
        }

        executorService.submit(() -> {
            try {
                // 提取特征
                com.example.myapplication.activity.full_down.FallClassifier.FeatureVector features = com.example.myapplication.activity.full_down.FallClassifier.extractFeatures(dataWindow);

                // 计算概率
                float probability = com.example.myapplication.activity.full_down.FallClassifier.calculateProbability(features);

                // 判断是否跌倒
                boolean isMLFall = com.example.myapplication.activity.full_down.FallClassifier.isFall(features);

                Log.i(TAG, "机器学习分析结果:");
                Log.i(TAG, "  峰值: " + features.peak);
                Log.i(TAG, "  方差: " + features.variance);
                Log.i(TAG, "  均值: " + features.mean);
                Log.i(TAG, "  范围: " + features.range);
                Log.i(TAG, "  过零率: " + features.zeroCrossRate);
                Log.i(TAG, "  能量: " + features.energy);
                Log.i(TAG, "  跌倒概率: " + (probability * 100) + "%");
                Log.i(TAG, "  规则判断: " + (isMLFall ? "是跌倒" : "不是跌倒"));

                mainHandler.post(() -> {
                    if (probability >= ML_CONFIDENCE_THRESHOLD || isMLFall) {
                        Log.d(TAG, "机器学习验证通过，开始跌倒后检查");
                        startPostFallCheck();
                    } else {
                        Log.d(TAG, "机器学习验证未通过，可能是误报");
                        resetState();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "机器学习分析失败", e);
                mainHandler.post(this::startPostFallCheck);
            }
        });
    }

    /**
     * 开始跌倒后检查
     */
    private void startPostFallCheck() {
        executorService.submit(() -> {
            try {
                Thread.sleep(POST_FALL_CHECK_TIME);
                mainHandler.post(() -> {
                    if (currentState == DetectionState.IMPACT) {
                        float[] angles = calculateAngles();
                        float currentTilt = Math.max(Math.abs(angles[0]), Math.abs(angles[1]));

                        if (currentTilt > dynamicAngleThreshold) {
                            Log.d(TAG, "跌倒后检查确认，触发跌倒");
                            confirmFall(lastAccel[0], currentTilt);
                        } else {
                            Log.d(TAG, "跌倒后检查：角度已恢复，可能是误报");
                            resetState();
                        }
                    }
                });
            } catch (InterruptedException e) {
                Log.e(TAG, "延迟任务中断", e);
            }
        });
    }

    /**
     * 确认跌倒
     */
    private void confirmFall(float impactForce, float angle) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastFallTime < MIN_FALL_INTERVAL) {
            Log.d(TAG, "检测到重复跌倒，已忽略");
            return;
        }

        lastFallTime = currentTime;
        isFallConfirmed = true;

        Log.i(TAG, "⚠️⚠️⚠️ 跌倒检测确认！ ⚠️⚠️⚠️");
        Log.i(TAG, "撞击力: " + String.format("%.2f", impactForce) + " m/s²");
        Log.i(TAG, "倾斜角度: " + String.format("%.1f", angle) + "°");

        triggerVibration();
        sendFallNotification();
        startFallConfirmActivity(impactForce, angle);

        currentState = DetectionState.NORMAL;
        freeFallStartTime = 0;
        impactTime = 0;
    }

    /**
     * 暂停检测
     */
    private void pauseDetection(long pauseTime) {
        isFallConfirmed = true;
        Log.i(TAG, "检测暂停 " + (pauseTime/1000) + " 秒");

        mainHandler.postDelayed(() -> {
            checkUserMovement();
        }, pauseTime);
    }

    /**
     * 保存用户位置
     */
    private void saveUserLocation(float latitude, float longitude) {
        if (lastKnownLocation == null) {
            lastKnownLocation = new Location("");
            lastKnownLocation.setLatitude(latitude);
            lastKnownLocation.setLongitude(longitude);
        }
    }

    /**
     * 检查用户是否移动
     */
    private void checkUserMovement() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // 没有权限，直接恢复检测
            resumeDetection();
            return;
        }
    }

    /**
     * 恢复检测
     */
    private void resumeDetection() {
        isFallConfirmed = false;
        resetState();
        Log.i(TAG, "检测已恢复");
    }

    /**
     * 触发振动
     */
    private void triggerVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 500, 200, 500}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 500, 200, 500}, -1);
            }
        }
    }

    /**
     * 发送跌倒通知
     */
    private void sendFallNotification() {
        Intent intent = new Intent(this, FallConfirmActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⚠️ 跌倒检测")
                .setContentText("检测到您可能摔倒了，请确认是否需要帮助")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    /**
     * 启动跌倒确认界面
     */
    private void startFallConfirmActivity(float impactForce, float angle) {
        Intent intent = new Intent(this, FallConfirmActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("impact_force", impactForce);
        intent.putExtra("angle", angle);
        intent.putExtra("timestamp", System.currentTimeMillis());
        startActivity(intent);
    }

    /**
     * 处理陀螺仪数据
     */
    private void handleGyroscope(SensorEvent event) {
        lastGyro[0] = event.values[0];
        lastGyro[1] = event.values[1];
        lastGyro[2] = event.values[2];
    }

    /**
     * 从加速度计算角度
     */
    private float[] calculateAngles() {
        float[] angles = new float[2];
        angles[0] = (float) Math.toDegrees(Math.atan2(-gravity[0],
                Math.sqrt(gravity[1]*gravity[1] + gravity[2]*gravity[2])));
        angles[1] = (float) Math.toDegrees(Math.atan2(gravity[1], gravity[2]));
        return angles;
    }

    /**
     * 获取平滑后的加速度
     */
    private float getSmoothedAccel() {
        float sum = 0;
        int count = 0;
        for (int i = 0; i < HISTORY_SIZE; i++) {
            if (accelHistory[i] > 0) {
                sum += accelHistory[i];
                count++;
            }
        }
        return count > 0 ? sum / count : GRAVITY_EARTH;
    }

    /**
     * 重置检测状态
     */
    private void resetState() {
        currentState = DetectionState.NORMAL;
        freeFallStartTime = 0;
        impactTime = 0;
    }

    /**
     * 检查并更新前后台状态
     */
    private void checkAndUpdateForegroundState(boolean isForeground) {
        long currentTime = System.currentTimeMillis();

        if (isForeground) {
            isAppInForeground = true;
            backgroundStartTime = 0;
            Log.d(TAG, "应用回到前台，继续检测");
        } else {
            if (isAppInForeground) {
                isAppInForeground = false;
                backgroundStartTime = currentTime;
                Log.d(TAG, "应用进入后台，10分钟后将停止检测");
                startBackgroundTimeoutCheck();
            }
        }
    }

    /**
     * 启动后台超时检查
     */
    private void startBackgroundTimeoutCheck() {
        executorService.submit(() -> {
            try {
                Thread.sleep(BACKGROUND_TIMEOUT);
                mainHandler.post(() -> {
                    if (!isAppInForeground && !isFallConfirmed && backgroundStartTime > 0) {
                        long backgroundDuration = System.currentTimeMillis() - backgroundStartTime;
                        if (backgroundDuration >= BACKGROUND_TIMEOUT) {
                            Log.d(TAG, "应用在后台超过10分钟，停止检测以省电");
                            stopSelf();
                        }
                    }
                });
            } catch (InterruptedException e) {
                Log.e(TAG, "后台超时检查中断", e);
            }
        });
    }

    /**
     * 启动前台服务
     */
    private void startForegroundService() {
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, FallDetectionService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("慧行助盲")
                .setContentText("跌倒检测运行中...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "跌倒检测服务",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("用于显示跌倒检测服务状态");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 获取唤醒锁
     */
    private void acquireWakeLock() {
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "FallDetection::WakeLock");
            wakeLock.acquire(10 * 60 * 1000L);
        }
    }

    /**
     * 设置用户参数
     */
    public void setUserParams(float height, float weight, int age, boolean useCane) {
        this.userHeight = height;
        this.userWeight = weight;
        this.userAge = age;
        this.useCane = useCane;
        calculateDynamicThresholds();
        logThresholdStatus();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // 移除所有回调
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }

        try {
            unregisterReceiver(appStateReceiver);
            unregisterReceiver(fallResultReceiver);
        } catch (Exception e) {
            Log.e(TAG, "注销接收器失败", e);
        }

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }

        Log.d(TAG, "跌倒检测服务已停止");
    }
}