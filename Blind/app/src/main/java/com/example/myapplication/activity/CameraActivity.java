package com.example.myapplication.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.manage.QwenManager;
import com.example.myapplication.manage.SimpleAsrManager;
import com.example.myapplication.manage.SimpleWakeUpManager;
import com.example.myapplication.utils.BitmapUtils;
import com.example.myapplication.utils.MqttManager;
import com.example.myapplication.utils.OkhttpUtils;
import com.example.myapplication.utils.TimeUtils;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class CameraActivity extends AppCompatActivity
        implements SurfaceHolder.Callback,
        Camera.PreviewCallback,
        TextToSpeech.OnInitListener,
        SensorEventListener {

    private static final String TAG = "CameraActivity";

    // MQTT配置
    private static final String MQTT_BROKER = "tcp://192.168.137.1:1883";  // 替换为你的MQTT服务器地址
    private static final String MQTT_CLIENT_ID = "android_app_" + System.currentTimeMillis();

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_AUDIO_PERMISSION = 101;

    // UI组件
    private SurfaceView cameraView;
    private SurfaceHolder surfaceHolder;
    private TextView statusText;
    private TextView analysisResult;
    private LinearLayout voiceFeedbackPanel;
    private TextView voiceCommandContent;
    private TextView analysisStatus;
    private ProgressBar analysisProgress;

    // 摄像头相关
    private Camera camera;
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private boolean isCameraReady = false;

    // TTS相关
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private Queue<String> ttsQueue = new LinkedList<>();
    private boolean isSpeaking = false;
    private String lastAlertMessage = "";
    private long lastAlertTime = 0;
    private static final long ALERT_COOLDOWN = 5000;

    // 语音助手
    private SimpleWakeUpManager wakeUpManager;
    private SimpleAsrManager asrManager;
    private QwenManager qwenManager;

    // MQTT通信相关
    private MqttManager mqttManager;
    private String deviceId = "";
    private String deviceName;
    private String deviceIp;
    private String deviceType;
    private TextView tvDistance;

    // MQTT超时检测
    private Handler timeoutHandler = new Handler();
    private Runnable timeoutRunnable;
    private static final long MQTT_TIMEOUT = 15000; // 15秒超时

    // 图像分析相关
    private AtomicBoolean isAnalyzing = new AtomicBoolean(false);
    private AtomicBoolean isCapturingForAnalysis = new AtomicBoolean(false);
    private String pendingCommand = "";
    private String lastAnalyzedObject = "";  // 上次分析的物体名称
    private double lastAnalyzedDistance = 0;  // 上次分析时的距离
    private boolean hasAnalyzedAtRange = false;  // 是否已在50-100cm范围内分析过
    private long lastAnalysisTime = 0;  // 上次分析时间
    private static final long ANALYSIS_COOLDOWN = 30000;  // 30秒内不重复分析
    private double lastRecordedDistance = 0;  // 上次记录的距离（用于检测跨度）
    private static final double DISTANCE_SPAN_THRESHOLD = 20.0;  // 距离跨度阈值20cm

    private Handler mqttTimeoutHandler = new Handler();
    private Runnable mqttTimeoutRunnable;
    private static final long MQTT_HEARTBEAT_TIMEOUT = 15000; // 15秒超时


    // 方向/旋转传感器相关
    private SensorManager sensorManager;
    private Sensor orientationSensor;
    private float lastAzimuth = 0;  // 上次的方向角（度）
    private float lastPitch = 0;    // 上次的俯仰角（度）
    private float lastRoll = 0;     // 上次的翻滚角（度）
    private boolean hasLastOrientation = false;
    private static final float ROTATION_THRESHOLD = 20.0f;  // 旋转角度阈值30度
    private static final float PITCH_THRESHOLD = 20.0f;     // 俯仰角阈值20度

    // 线程池
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 最新帧缓存
    private byte[] latestFrame;
    private int previewWidth;
    private int previewHeight;
    private final Object frameLock = new Object();

    // 自动休眠
    private Handler sleepHandler = new Handler();
    private static final long SLEEP_DELAY = 60000;
    private boolean isVoiceActive = false;

    private Runnable sleepRunnable = new Runnable() {
        @Override
        public void run() {
            if (isVoiceActive) {
                isVoiceActive = false;
                Log.d(TAG, "小黎自动休眠");
                runOnUiThread(() -> {
                    Toast.makeText(CameraActivity.this, "小黎已休眠", Toast.LENGTH_SHORT).show();
                    if (voiceFeedbackPanel != null) {
                        voiceFeedbackPanel.setVisibility(View.GONE);
                    }
                    if (statusText != null) {
                        statusText.setText("● 待命中");
                    }
                });
                if (wakeUpManager != null) wakeUpManager.start();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        getDeviceInfoFromIntent();
        initViews();
        initMqttCommunication();
        initOrientationSensors();
        qwenManager = new QwenManager();
        initTTS();
        checkPermissions();
    }

    private void getDeviceInfoFromIntent() {
        Intent intent = getIntent();
        deviceId = intent.getStringExtra("deviceId");
        deviceName = intent.getStringExtra("deviceName");
        deviceIp = intent.getStringExtra("deviceIp");
        deviceType = intent.getStringExtra("deviceType");
        Log.d(TAG, "设备信息 - ID: " + deviceId + ", 名称: " + deviceName + ", IP: " + deviceIp);
    }

    private void initViews() {
        cameraView = findViewById(R.id.camera_view);
        statusText = findViewById(R.id.status_text);
        analysisResult = findViewById(R.id.analysis_result);
        voiceFeedbackPanel = findViewById(R.id.voice_feedback_panel);
        voiceCommandContent = findViewById(R.id.voice_command_content);
        analysisStatus = findViewById(R.id.analysis_status);
        analysisProgress = findViewById(R.id.analysis_progress);

        tvDistance = findViewById(R.id.tv_distance);
        if (tvDistance == null) {
            tvDistance = new TextView(this);
            tvDistance.setTextSize(16);
            tvDistance.setTextColor(Color.WHITE);
            tvDistance.setBackgroundColor(Color.parseColor("#88000000"));
            tvDistance.setPadding(20, 10, 20, 10);
            FrameLayout root = findViewById(android.R.id.content);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.TOP | Gravity.END;
            params.topMargin = 100;
            params.rightMargin = 20;
            root.addView(tvDistance, params);
        }

        surfaceHolder = cameraView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        if (voiceFeedbackPanel != null) {
            voiceFeedbackPanel.setVisibility(View.GONE);
        }
        if (statusText != null) {
            statusText.setText("● 检查摄像头...");
        }
    }

    // ================= MQTT通信=================

    private void initMqttCommunication() {
        if (deviceIp == null || deviceIp.isEmpty()) {
            Log.e(TAG, "设备IP为空，无法建立MQTT连接");
            Toast.makeText(this, "设备IP地址无效", Toast.LENGTH_SHORT).show();
            return;
        }

        // 初始化MQTT管理器
        mqttManager = new MqttManager(
                getApplicationContext(),
                MQTT_BROKER,
                MQTT_CLIENT_ID,
                info.mqtt.android.service.Ack.AUTO_ACK
        );

        // 设置消息监听
        mqttManager.setOnMessageListener((topic, message) -> {
            Log.d(TAG, "收到MQTT消息 - Topic: " + topic + ", 消息: " + message);
            runOnUiThread(() -> {
                resetMqttTimeout();  // 收到任何消息都重置超时计时
                parseSensorData(message);
            });
        });

        // 设置连接状态监听
        mqttManager.setOnConnectionListener(new MqttManager.OnConnectionListener() {
            @Override
            public void onConnected(boolean isReconnect) {
                runOnUiThread(() -> {
                    Log.i(TAG, "MQTT连接成功");
                    statusText.setText("● 已连接");

                    // 订阅设备状态主题
                    mqttManager.subscribe("device/" + deviceId + "/sensor");
                    mqttManager.subscribe("device/" + deviceId + "/status");

                    // 请求传感器数据
                    requestSensorData();

                    // 启动心跳超时检测
                    startMqttHeartbeatTimeout();
                });
            }

            @Override
            public void onConnectionLost(Throwable cause) {
                runOnUiThread(() -> {
                    Log.e(TAG, "MQTT连接丢失");
                    statusText.setText("● 设备离线");
                    statusText.setTextColor(Color.parseColor("#F44336"));
                    Toast.makeText(CameraActivity.this, "设备离线", Toast.LENGTH_SHORT).show();

                    // 停止超时检测
                    stopMqttHeartbeatTimeout();

                    // 连接丢失，退出页面
                    exitAppDueToTimeout();
                });
            }

            @Override
            public void onConnectFailed(Throwable exception) {
                runOnUiThread(() -> {
                    Log.e(TAG, "MQTT连接失败");
                    statusText.setText("● 连接失败");
                    Toast.makeText(CameraActivity.this, "连接失败", Toast.LENGTH_LONG).show();

                    // 连接失败，退出页面
                    exitAppDueToTimeout();
                });
            }

            @Override
            public void onPublishFailed(String topic, String message) {
                Log.e(TAG, "发送失败: " + topic);
            }
        });

        // 开始连接
        mqttManager.connect(null, null);
        Log.d(TAG, "MQTT通信已初始化，Broker: " + MQTT_BROKER);
    }

    // 启动心跳超时检测
    private void startMqttHeartbeatTimeout() {
        stopMqttHeartbeatTimeout(); // 先停止之前的

        mqttTimeoutRunnable = () -> {
            Log.e(TAG, "超过15秒未收到MQTT心跳数据，断开连接并退出页面");

            // 断开MQTT连接
            if (mqttManager != null) {
                mqttManager.disconnect();
            }

            // 退出页面
            exitAppDueToTimeout();
        };

        mqttTimeoutHandler.postDelayed(mqttTimeoutRunnable, MQTT_HEARTBEAT_TIMEOUT);
        Log.d(TAG, "心跳超时检测已启动，超时时间: " + MQTT_HEARTBEAT_TIMEOUT + "ms");
    }

    // 重置心跳超时计时
    private void resetMqttTimeout() {
        if (mqttTimeoutRunnable != null) {
            mqttTimeoutHandler.removeCallbacks(mqttTimeoutRunnable);
            mqttTimeoutHandler.postDelayed(mqttTimeoutRunnable, MQTT_HEARTBEAT_TIMEOUT);
            Log.d(TAG, "心跳超时计时已重置");
        }
    }

    // 停止心跳超时检测
    private void stopMqttHeartbeatTimeout() {
        if (mqttTimeoutHandler != null && mqttTimeoutRunnable != null) {
            mqttTimeoutHandler.removeCallbacks(mqttTimeoutRunnable);
            Log.d(TAG, "心跳超时检测已停止");
        }
    }

    // 超时退出方法
    private void exitAppDueToTimeout() {
        Log.e(TAG, "因超时退出页面，清理所有页面");

        // 在主线程执行退出操作
        runOnUiThread(() -> {
            // 更新设备状态为离线
            updateDeviceOfflineStatus();

            // 停止所有正在进行的操作
            stopMqttHeartbeatTimeout();
            if (asrManager != null) asrManager.stop();
            if (wakeUpManager != null) wakeUpManager.stop();
            stopSpeaking();

            // 清理所有页面并退出
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

            // 关闭当前Activity
            finishAffinity();
        });
    }

    // 更新设备离线状态到服务器
    private void updateDeviceOfflineStatus() {
        new Thread(() -> {
            try {
                HashMap<String, Object> map = new HashMap<>();
                map.put("deviceUniqueId", deviceId);
                map.put("status", "0");

                OkhttpUtils.request("POST", OkhttpUtils.URL + OkhttpUtils.updateDevice,
                        OkhttpUtils.toBody(map), "", new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {
                                Log.e(TAG, "更新设备状态失败: ", e);
                            }

                            @Override
                            public void onResponse(Call call, Response response) throws IOException {
                                Log.i(TAG, "设备状态已更新为离线");
                                response.close();
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "更新设备状态异常: ", e);
            }
        }).start();
    }


    private void startMqttTimeoutCheck() {
        timeoutRunnable = () -> {
            Log.e(TAG, "超过" + MQTT_TIMEOUT / 1000 + "秒未收到设备数据，退出页面");
            runOnUiThread(() -> {
                // 更新设备状态为离线
                HashMap<String, Object> map = new HashMap<>();
                map.put("deviceUniqueId", deviceId);
                map.put("status", "0");
                OkhttpUtils.request("POST", OkhttpUtils.URL + OkhttpUtils.updateDevice,
                        OkhttpUtils.toBody(map), "", new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {
                                Log.e(TAG, "更新设备状态失败: ", e);
                            }

                            @Override
                            public void onResponse(Call call, Response response) throws IOException {
                                Log.i(TAG, "设备状态已更新为离线");
                            }
                        });

                // 清理所有页面并退出
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finishAffinity();
            });
        };
        resetMqttTimeout();
    }

    private void requestSensorData() {
        if (mqttManager != null && mqttManager.isConnected()) {
            try {
                JSONObject request = new JSONObject();
                request.put("type", "request_data");
                request.put("deviceId", deviceId);
                mqttManager.publish("device/" + deviceId + "/command", request.toString());
                Log.d(TAG, "发送传感器数据请求");
            } catch (Exception e) {
                Log.e(TAG, "发送请求失败", e);
            }
        }
    }

    private void parseSensorData(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type");

            if ("sensor_data".equals(type)) {
                double distance = json.optDouble("distance", 0);
                String alert = json.optString("alert", "");
                Log.d(TAG, "传感器数据 - 距离: " + distance + "cm, 警报: " + alert);
                runOnUiThread(() -> updateDistanceDisplay(distance, alert));

                // 处理距离触发逻辑
                handleDistanceTrigger(distance, alert);

                // 更新记录的距离
                lastRecordedDistance = distance;

                // 注意：这里不需要重置超时，因为已经在消息监听中重置了
            } else if ("heartbeat_response".equals(type)) {
                Log.d(TAG, "心跳响应收到，设备在线");
                // 心跳响应不需要额外处理，因为收到任何消息都会重置超时
                // 但可以在这里做一些额外的状态更新
                runOnUiThread(() -> {
                    if (statusText != null) {
                        statusText.setText("● 在线");
                    }
                });
            } else if ("status".equals(type)) {
                String status = json.optString("status", "");
                Log.d(TAG, "设备状态: " + status);
                if ("offline".equals(status)) {
                    Log.w(TAG, "设备离线，退出页面");
                    exitAppDueToTimeout();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析传感器数据失败", e);
        }
    }

    // 处理距离触发的逻辑
    private void handleDistanceTrigger(double distance, String alert) {
        long now = System.currentTimeMillis();

        // 检查距离跨度是否超过20cm
        if (lastRecordedDistance > 0 && Math.abs(distance - lastRecordedDistance) >= DISTANCE_SPAN_THRESHOLD) {
            Log.d(TAG, "距离跨度超过" + DISTANCE_SPAN_THRESHOLD + "cm，重置分析标志");
            hasAnalyzedAtRange = false;
            lastAnalyzedObject = null;
        }

        // 当距离在70-100cm之间且尚未分析过时触发图像分析
        if (distance >= 70 && distance <= 100 && !hasAnalyzedAtRange &&
                !isCapturingForAnalysis.get() && (now - lastAnalysisTime) > ANALYSIS_COOLDOWN) {
            Log.d(TAG, "距离在70-100cm之间，触发图像分析");
            hasAnalyzedAtRange = true;
            isCapturingForAnalysis.set(true);
            lastAnalyzedDistance = distance;
            lastAnalysisTime = now;
            captureAndAnalyzeCenterObject();
        }

        // 重置分析标志
        if (distance < 70 || distance > 100) {
            hasAnalyzedAtRange = false;
        }

        // 当距离小于80cm时触发距离警报
        if (distance < 80) {
            triggerDistanceAlert(distance, alert);
        }
    }

    private void updateDistanceDisplay(double distance, String alert) {
        if (tvDistance == null) return;
        String distanceText = String.format("📡 距离: %.1f cm", distance);
        tvDistance.setText(distanceText);

        if ("danger".equals(alert) || distance < 30) {
            tvDistance.setTextColor(Color.RED);
            tvDistance.setBackgroundColor(Color.parseColor("#88FF0000"));
        } else if ("warning".equals(alert) || distance < 50) {
            tvDistance.setTextColor(Color.YELLOW);
            tvDistance.setBackgroundColor(Color.parseColor("#88FF6600"));
        } else if (distance < 80) {
            tvDistance.setTextColor(Color.GREEN);
            tvDistance.setBackgroundColor(Color.parseColor("#8800AA00"));
        } else {
            tvDistance.setTextColor(Color.WHITE);
            tvDistance.setBackgroundColor(Color.parseColor("#88000000"));
        }
        tvDistance.setVisibility(View.VISIBLE);
    }

    private void triggerDistanceAlert(double distance, String alert) {
        if (distance <= 0) return;

        boolean hasValidObject = lastAnalyzedObject != null &&
                !lastAnalyzedObject.isEmpty() &&
                !lastAnalyzedObject.equals("物体") &&
                !lastAnalyzedObject.equals("一个人") &&
                !lastAnalyzedObject.equals("人");

        String alertMessage;
        if (hasValidObject) {
            if (distance < 30) {
                alertMessage = "注意！前方" + lastAnalyzedObject + "，距离您" + String.format("%.0f", distance) + "厘米，请小心！";
            } else {
                alertMessage = "注意！前方" + lastAnalyzedObject + "，距离" + String.format("%.0f", distance) + "厘米，请小心";
            }
        } else {
            if (distance < 30) {
                alertMessage = "危险！前方" + String.format("%.0f", distance) + "厘米处有障碍物，请小心！";
            } else {
                alertMessage = "注意！前方" + String.format("%.0f", distance) + "厘米处有障碍物，请小心";
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastAlertTime < ALERT_COOLDOWN) {
            return;
        }
        if (alertMessage.equals(lastAlertMessage) && (now - lastAlertTime) < ALERT_COOLDOWN * 2) {
            return;
        }

        lastAlertMessage = alertMessage;
        lastAlertTime = now;

        if (distance < 20) {
            stopSpeaking();
        }
        speak(alertMessage);
    }

    // ================= 图像分析相关方法 =================

    private void captureAndAnalyzeCenterObject() {
        if (!isCameraReady || camera == null) {
            Log.e(TAG, "摄像头未就绪");
            isCapturingForAnalysis.set(false);
            return;
        }

        runOnUiThread(() -> {
            if (analysisStatus != null) {
                analysisStatus.setText("正在识别前方物体...");
            }
            if (analysisProgress != null) {
                analysisProgress.setVisibility(View.VISIBLE);
            }
        });

        mainHandler.postDelayed(() -> {
            byte[] frame;
            synchronized (frameLock) {
                if (latestFrame == null) {
                    Log.e(TAG, "没有图像帧");
                    isCapturingForAnalysis.set(false);
                    runOnUiThread(() -> {
                        if (analysisProgress != null) analysisProgress.setVisibility(View.GONE);
                    });
                    return;
                }
                frame = latestFrame.clone();
            }

            executor.execute(() -> {
                Bitmap bitmap = yuvToBitmap(frame, previewWidth, previewHeight);
                if (bitmap == null) {
                    Log.e(TAG, "图像转换失败");
                    isCapturingForAnalysis.set(false);
                    runOnUiThread(() -> {
                        if (analysisProgress != null) analysisProgress.setVisibility(View.GONE);
                    });
                    return;
                }
                analyzeCenterObject(bitmap);
            });
        }, 100);
    }

    private void analyzeCenterObject(Bitmap bitmap) {
        try {
            String base64 = BitmapUtils.bitmapToBase64(bitmap, 80);
            String time = TimeUtils.getCurrentTime();
            String hourStr = time.substring(0, 2);
            String Mode = (hourStr.compareTo("06") >= 0 && hourStr.compareTo("18") <= 0) ? "白天" : "晚上";

            String prompt = "你是一个辅助盲人的视觉助手。请仔细观察这张图片，重点分析图片正中间最靠近摄像头（最近）的物体。\n" +
                    "现在是" + Mode + "。\n" +
                    "请回答以下问题：\n" +
                    "1. 图片正中间最靠近的物体是什么？（请用具体的物体名称）\n" +
                    "2. 这个物体大概有多大？（小、中、大）\n" +
                    "3. 这个物体的颜色是什么？\n" +
                    "请用格式回答：【物体】+【大小】+【颜色】";

            qwenManager.sendMessage2(prompt, new QwenManager.QwenCallback() {
                @Override
                public void onSuccess(String response) {
                    Log.d(TAG, "物体识别响应: " + response);
                    String objectDesc = parseObjectDescription(response);

                    if (objectDesc == null || objectDesc.isEmpty() ||
                            objectDesc.equals("一个人") || objectDesc.equals("人") ||
                            objectDesc.contains("无法识别")) {
                        lastAnalyzedObject = null;
                        runOnUiThread(() -> {
                            if (analysisProgress != null) analysisProgress.setVisibility(View.GONE);
                            if (analysisStatus != null) analysisStatus.setText("未识别到物体");
                        });
                    } else {
                        lastAnalyzedObject = objectDesc;
                        runOnUiThread(() -> {
                            if (analysisResult != null) {
                                analysisResult.setText("识别结果: " + objectDesc);
                            }
                            if (analysisProgress != null) {
                                analysisProgress.setVisibility(View.GONE);
                            }
                            if (analysisStatus != null) {
                                analysisStatus.setText("识别完成");
                            }
                            mainHandler.postDelayed(() -> {
                                if (voiceFeedbackPanel != null && !isVoiceActive) {
                                    voiceFeedbackPanel.setVisibility(View.GONE);
                                }
                            }, 3000);
                        });
                    }
                    isCapturingForAnalysis.set(false);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "物体识别失败: " + error);
                    lastAnalyzedObject = null;
                    runOnUiThread(() -> {
                        if (analysisProgress != null) analysisProgress.setVisibility(View.GONE);
                        if (analysisStatus != null) analysisStatus.setText("识别失败");
                    });
                    isCapturingForAnalysis.set(false);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "分析物体错误", e);
            isCapturingForAnalysis.set(false);
            runOnUiThread(() -> {
                if (analysisProgress != null) analysisProgress.setVisibility(View.GONE);
            });
        }
    }

    private String parseObjectDescription(String response) {
        if (response == null || response.isEmpty()) return null;

        String cleanText = response.replace("```", "").replace("json", "").trim();

        if (cleanText.contains("无法识别") || cleanText.contains("看不清")) {
            return null;
        }

        if (cleanText.equals("一个人") || cleanText.equals("人") || cleanText.contains("一个人")) {
            return null;
        }

        if (cleanText.contains("，")) {
            String[] parts = cleanText.split("，");
            if (parts.length > 0) {
                String object = parts[0].trim();
                if (object.length() <= 2 && (object.equals("人") || object.equals("物"))) {
                    return null;
                }
                return object;
            }
        }

        if (cleanText.length() > 0 && cleanText.length() < 50) {
            if (cleanText.equals("一个人") || cleanText.equals("人")) {
                return null;
            }
            return cleanText;
        }

        String result = cleanText.substring(0, Math.min(20, cleanText.length()));
        if (result.equals("一个人") || result.equals("人")) {
            return null;
        }
        return result;
    }

    // ================= 方向传感器 =================

    private void initOrientationSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (orientationSensor == null) {
                orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
            }
        }
    }

    private void registerOrientationListener() {
        if (sensorManager != null && orientationSensor != null) {
            sensorManager.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void unregisterOrientationListener() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);
            checkOrientationChange(
                    (float) Math.toDegrees(orientation[0]),
                    (float) Math.toDegrees(orientation[1]),
                    (float) Math.toDegrees(orientation[2])
            );
        } else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            checkOrientationChange(event.values[0], event.values[1], event.values[2]);
        }
    }

    private void checkOrientationChange(float azimuth, float pitch, float roll) {
        if (!hasLastOrientation) {
            lastAzimuth = azimuth;
            lastPitch = pitch;
            lastRoll = roll;
            hasLastOrientation = true;
            return;
        }

        float azimuthDelta = Math.abs(azimuth - lastAzimuth);
        float pitchDelta = Math.abs(pitch - lastPitch);
        if (azimuthDelta > 180) azimuthDelta = 360 - azimuthDelta;

        if (azimuthDelta >= ROTATION_THRESHOLD || pitchDelta >= PITCH_THRESHOLD) {
            Log.d(TAG, "检测到角度变化，重置分析状态");
            hasAnalyzedAtRange = false;
            lastAnalyzedObject = null;

            if (lastRecordedDistance >= 70 && lastRecordedDistance <= 100) {
                long now = System.currentTimeMillis();
                if (!isCapturingForAnalysis.get() && (now - lastAnalysisTime) > ANALYSIS_COOLDOWN) {
                    hasAnalyzedAtRange = true;
                    isCapturingForAnalysis.set(true);
                    lastAnalysisTime = now;
                    captureAndAnalyzeCenterObject();
                }
            }

            lastAzimuth = azimuth;
            lastPitch = pitch;
            lastRoll = roll;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ================= TTS 语音播报 =================

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS语言不支持");
                } else {
                    ttsReady = true;
                    setUtteranceProgressListener();
                    Log.d(TAG, "TTS初始化成功");
                }
            }
        });
    }

    private void setUtteranceProgressListener() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
                isSpeaking = false;
                processTTSQueue();
            }

            @Override
            public void onError(String utteranceId) {
                isSpeaking = false;
                processTTSQueue();
            }

            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                isSpeaking = false;
                processTTSQueue();
            }
        });
    }

    private void speak(String text) {
        if (!ttsReady || tts == null || text == null || text.isEmpty()) return;
        synchronized (ttsQueue) {
            ttsQueue.add(text);
        }
        processTTSQueue();
    }

    private void processTTSQueue() {
        if (isSpeaking) return;
        String text;
        synchronized (ttsQueue) {
            if (ttsQueue.isEmpty()) return;
            text = ttsQueue.poll();
        }
        if (text != null && !text.isEmpty()) {
            isSpeaking = true;
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, text);
        }
    }

    private void stopSpeaking() {
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
        }
        isSpeaking = false;
        synchronized (ttsQueue) {
            ttsQueue.clear();
        }
    }

    // ================= 权限和初始化 =================

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
            return;
        }
        initCamera();
        initVoiceAssistant();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkPermissions();
            } else {
                Toast.makeText(this, "需要摄像头权限才能使用", Toast.LENGTH_LONG).show();
                finish();
            }
        } else if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkPermissions();
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音功能", Toast.LENGTH_LONG).show();
                if (statusText != null) {
                    statusText.setText("● 语音不可用");
                }
            }
        }
    }

    private void refreshSleepTimer() {
        if (!isVoiceActive) return;
        sleepHandler.removeCallbacks(sleepRunnable);
        sleepHandler.postDelayed(sleepRunnable, SLEEP_DELAY);
    }

    private void initVoiceAssistant() {
        asrManager = new SimpleAsrManager(this, new SimpleAsrManager.OnAsrListener() {
            @Override
            public void onResult(String text) {
                refreshSleepTimer();
                runOnUiThread(() -> processCommand(text));
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "ASR错误: " + error);
                runOnUiThread(() -> {
                    if (isVoiceActive && asrManager != null) {
                        asrManager.start();
                    }
                });
            }
        });

        wakeUpManager = new SimpleWakeUpManager(this, new SimpleWakeUpManager.WakeUpListener() {
            @Override
            public void onSuccess(String word) {
                runOnUiThread(() -> {
                    isVoiceActive = true;
                    wakeUpManager.stop();
                    Toast.makeText(CameraActivity.this, "我在", Toast.LENGTH_SHORT).show();
                    statusText.setText("● 已唤醒");
                    speak("我在，您想识别什么？");
                    if (asrManager != null) {
                        asrManager.start();
                    }
                    refreshSleepTimer();
                });
            }

            @Override
            public void onError(String errorMsg) {
                Log.e(TAG, "唤醒失败: " + errorMsg);
            }
        });

        if (isCameraReady) {
            wakeUpManager.start();
        }
    }

    private void processCommand(String text) {
        if (text == null || text.isEmpty()) return;
        Log.d(TAG, "处理指令: " + text);

        if (text.contains("退出") || text.contains("关闭") || text.contains("再见") || text.contains("退下")) {
            isVoiceActive = false;
            speak("好的，已退出");
            if (voiceFeedbackPanel != null) {
                voiceFeedbackPanel.setVisibility(View.GONE);
            }
            statusText.setText("● 待命中");
            if (wakeUpManager != null) wakeUpManager.start();
            return;
        }

        if (text.contains("距离") || text.contains("多远")) {
            requestSensorData();
            speak("正在获取距离信息");
            return;
        }

        if (text.contains("前面") || text.contains("是什么") || text.contains("识别") || text.contains("看到") ||
                text.contains("手里") || text.contains("什么东西")) {
            if (!isCameraReady || camera == null) {
                speak("摄像头未就绪，请稍后再试");
                return;
            }
            if (isAnalyzing.get()) {
                speak("正在分析中，请稍候");
                return;
            }
            pendingCommand = text;
            showAnalysisPanel(text);
            captureFrame();
        } else {
            speak("请说“前面有什么”或“识别”来进行环境识别");
        }
    }

    private void showAnalysisPanel(String command) {
        if (voiceFeedbackPanel != null) voiceFeedbackPanel.setVisibility(View.VISIBLE);
        if (voiceCommandContent != null) voiceCommandContent.setText("“" + command + "”");
        if (analysisStatus != null) analysisStatus.setText("正在获取图像...");
        if (analysisProgress != null) analysisProgress.setVisibility(View.VISIBLE);
        if (analysisResult != null) analysisResult.setText("正在处理...");
    }

    private void captureFrame() {
        if (isAnalyzing.get()) return;

        byte[] frame;
        synchronized (frameLock) {
            if (latestFrame == null) {
                runOnUiThread(() -> {
                    speak("正在获取图像，请稍候");
                    if (analysisStatus != null) analysisStatus.setText("等待图像数据...");
                });
                mainHandler.postDelayed(() -> {
                    if (!isAnalyzing.get() && latestFrame != null) {
                        captureFrame();
                    }
                }, 1000);
                return;
            }
            frame = latestFrame.clone();
        }

        isAnalyzing.set(true);
        runOnUiThread(() -> {
            if (analysisStatus != null) analysisStatus.setText("正在处理图像...");
        });

        executor.execute(() -> {
            Bitmap bitmap = yuvToBitmap(frame, previewWidth, previewHeight);
            if (bitmap == null) {
                runOnUiThread(() -> {
                    isAnalyzing.set(false);
                    if (analysisStatus != null) analysisStatus.setText("图像处理失败");
                    speak("图像处理失败，请重试");
                });
                return;
            }
            runOnUiThread(() -> {
                if (analysisStatus != null) analysisStatus.setText("正在分析环境...");
            });
            analyzeImage(bitmap);
        });
    }

    private Bitmap yuvToBitmap(byte[] data, int width, int height) {
        try {
            YuvImage yuv = new YuvImage(data, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, width, height), 80, out);
            byte[] bytes = out.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                return null;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "yuvToBitmap error", e);
            return null;
        }
    }

    private void analyzeImage(Bitmap bitmap) {
        try {
            String base64 = BitmapUtils.bitmapToBase64(bitmap, 80);
            String time = TimeUtils.getCurrentTime();
            String hourStr = time.substring(0, 2);
            String Mode = (hourStr.compareTo("06") >= 0 && hourStr.compareTo("18") <= 0) ? "白天" : "晚上";

            String prompt = "你是一个辅助盲人的视觉助手。请详细描述这张图片中的内容，包括：\n" +
                    "现在是" + Mode + "室内还是室外？有什么物体：看到了什么物品？它们的位置和状态？什么样？什么颜色？大小？" +
                    "是否有人？他们在做什么？\n" +
                    "如果有文字，请读出来\n" +
                    "是否有需要注意的危险？\n" +
                    "用户的问题是：" + pendingCommand + "\n" +
                    "请用自然、友好的语气直接回答，不要说'根据图片'之类的话。";

            qwenManager.sendMessage2(prompt, new QwenManager.QwenCallback() {
                @Override
                public void onSuccess(String response) {
                    runOnUiThread(() -> {
                        try {
                            String text = response;
                            if (response != null && response.trim().startsWith("{")) {
                                try {
                                    JSONObject obj = new JSONObject(response);
                                    if (obj.has("reply")) text = obj.getString("reply");
                                    else if (obj.has("content")) text = obj.getString("content");
                                    else if (obj.has("text")) text = obj.getString("text");
                                } catch (Exception e) {
                                    Log.e(TAG, "JSON解析失败", e);
                                }
                            }
                            if (text != null) {
                                text = text.replace("```", "").replace("json", "").trim();
                            }
                            final String finalText = text;
                            if (analysisResult != null && finalText != null) {
                                analysisResult.setText(finalText);
                            }
                            if (analysisStatus != null) {
                                analysisStatus.setText("分析完成");
                                analysisStatus.setTextColor(getColor(android.R.color.holo_green_light));
                            }
                            if (analysisProgress != null) analysisProgress.setVisibility(View.GONE);
                            if (finalText != null && !finalText.isEmpty()) {
                                speak(finalText);
                                if (asrManager != null) asrManager.stop();
                            } else {
                                speak("我没有看清，请再试一次");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "处理响应失败", e);
                            if (response != null) {
                                analysisResult.setText(response);
                                speak(response);
                            }
                        }
                        isAnalyzing.set(false);
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        if (analysisStatus != null) {
                            analysisStatus.setText("分析失败");
                            analysisStatus.setTextColor(getColor(android.R.color.holo_red_light));
                        }
                        if (analysisResult != null) analysisResult.setText("错误: " + error);
                        if (analysisProgress != null) analysisProgress.setVisibility(View.GONE);
                        speak("分析失败，请重试");
                        isAnalyzing.set(false);
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "AI分析错误", e);
            runOnUiThread(() -> {
                isAnalyzing.set(false);
                speak("分析过程出错");
            });
        }
    }

    // ===== 摄像头回调 =====

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if (!isCameraReady) {
            initCamera();
        }
    }

    private void initCamera() {
        try {
            camera = Camera.open(cameraId);
            Camera.Parameters params = camera.getParameters();
            Camera.Size size = params.getPreviewSize();
            previewWidth = size.width;
            previewHeight = size.height;
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(params);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(surfaceHolder);
            camera.setPreviewCallback(this);
            camera.startPreview();
            isCameraReady = true;
            runOnUiThread(() -> {
                statusText.setText("● 待命中");
                if (wakeUpManager != null) wakeUpManager.start();
            });
            Log.d(TAG, "摄像头初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "camera init error", e);
            runOnUiThread(() -> {
                statusText.setText("● 摄像头错误");
                Toast.makeText(this, "摄像头初始化失败", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        synchronized (frameLock) {
            if (latestFrame == null || latestFrame.length != data.length) {
                latestFrame = new byte[data.length];
            }
            System.arraycopy(data, 0, latestFrame, 0, data.length);
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.setPreviewDisplay(holder);
                camera.startPreview();
            } catch (Exception e) {
                Log.e(TAG, "surfaceChanged error", e);
            }
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        releaseCamera();
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
            isCameraReady = false;
        }
    }

    // ===== 生命周期 =====

    @Override
    protected void onResume() {
        super.onResume();
        registerOrientationListener();
        if (wakeUpManager != null && isCameraReady) wakeUpManager.start();
        if (!isCameraReady && camera == null) initCamera();
        if (mqttManager != null && mqttManager.isConnected()) {
            requestSensorData();
            startMqttHeartbeatTimeout();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterOrientationListener();
        if (wakeUpManager != null) wakeUpManager.stop();
        if (asrManager != null) asrManager.stop();
        sleepHandler.removeCallbacks(sleepRunnable);
        stopSpeaking();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMqttHeartbeatTimeout();
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
        if (mqttManager != null) {
            // 发送离线状态
            try {
                JSONObject offlineMsg = new JSONObject();
                offlineMsg.put("type", "offline");
                offlineMsg.put("deviceId", deviceId);
                mqttManager.publish("device/" + deviceId + "/status", offlineMsg.toString());
            } catch (Exception e) {
                Log.e(TAG, "发送离线消息失败", e);
            }
            mqttManager.close();
        }
        releaseCamera();
        if (wakeUpManager != null) wakeUpManager.release();
        stopSpeaking();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (sleepHandler != null) sleepHandler.removeCallbacksAndMessages(null);
        executor.shutdown();
        unregisterOrientationListener();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
        }
    }
}