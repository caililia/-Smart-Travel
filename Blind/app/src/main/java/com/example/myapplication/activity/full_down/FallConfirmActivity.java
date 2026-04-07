package com.example.myapplication.activity.full_down;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.manage.QwenManager;
import com.example.myapplication.manage.SimpleAsrManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class FallConfirmActivity extends AppCompatActivity {

    private TextView tvCountdown;
    private TextView tvMessage;
    private TextView tvFallInfo;
    private Button btnHelp;
    private Button btnOk;
    private Button btnCancel;

    private CountDownTimer countDownTimer;
    private static final int COUNTDOWN_SECONDS = 30;

    private float impactForce;
    private float angle;
    private long timestamp;
    private boolean isTest = false;

    private FusedLocationProviderClient fusedLocationClient;
    private Vibrator vibrator;

    // 记录用户当前位置（用于判断是否移动）
    private Location currentLocation = null;

    // --- 语音助手相关变量 ---
    private TextToSpeech tts;
    private SimpleAsrManager asrManager;
    private QwenManager qwenManager;
    private Handler handler = new Handler();
    private boolean isVoiceActive = false;
    private boolean hasResponded = false; // 用户是否已回应
    private static final String TAG = "FallConfirmActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fall_confirm);

        // 获取传入数据
        impactForce = getIntent().getFloatExtra("impact_force", 0);
        angle = getIntent().getFloatExtra("angle", 0);
        timestamp = getIntent().getLongExtra("timestamp", System.currentTimeMillis());
        isTest = getIntent().getBooleanExtra("is_test", false);

        initViews();
        initData();
        initVoiceAssistant(); // 初始化语音助手
        startCountdown();
        getCurrentLocation(); // 获取当前位置

        // 如果是测试模式，不触发紧急流程
        if (isTest) {
            tvMessage.setText("测试模式 - 模拟跌倒检测");
        }
    }

    private void initViews() {
        tvCountdown = findViewById(R.id.tv_countdown);
        tvMessage = findViewById(R.id.tv_message);
        tvFallInfo = findViewById(R.id.tv_fall_info);
        btnHelp = findViewById(R.id.btn_help);
        btnOk = findViewById(R.id.btn_ok);
        btnCancel = findViewById(R.id.btn_cancel);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        btnHelp.setOnClickListener(v -> {
            hasResponded = true;
            cancelCountdown();
            stopVoiceAssistant(); // 停止语音识别
            // 用户需要帮助
            getLastLocationAndStartHelp();
        });

        btnOk.setOnClickListener(v -> {
            hasResponded = true;
            cancelCountdown();
            stopVoiceAssistant(); // 停止语音识别
            Toast.makeText(this, "已确认安全", Toast.LENGTH_SHORT).show();
            // 用户安全，立即恢复检测
            sendBroadcast(new Intent("com.android.test.FALL_RESULT")
                    .putExtra("is_safe", true)
                    .putExtra("action", "RESUME_NOW"));
            finish();
        });

        btnCancel.setOnClickListener(v -> {
            hasResponded = true;
            cancelCountdown();
            stopVoiceAssistant(); // 停止语音识别
            Toast.makeText(this, "已取消", Toast.LENGTH_SHORT).show();
            // 用户取消（可能是误报），立即恢复检测
            sendBroadcast(new Intent("com.android.test.FALL_RESULT")
                    .putExtra("is_safe", true)
                    .putExtra("action", "RESUME_NOW"));
            finish();
        });
    }

    private void initData() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String timeStr = sdf.format(new Date(timestamp));

        String info = "检测时间: " + timeStr + "\n";
        info += "撞击力度: " + String.format("%.1f", impactForce) + " m/s²\n";
        info += "身体倾斜: " + String.format("%.1f", angle) + "°";

        tvFallInfo.setText(info);
    }

    /**
     * 获取当前位置
     */
    private void getCurrentLocation() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            currentLocation = task.getResult();
                        }
                    });
        }
    }

    /**
     * 开始倒计时
     */
    private void startCountdown() {
        countDownTimer = new CountDownTimer(COUNTDOWN_SECONDS * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                tvCountdown.setText(String.valueOf(secondsLeft));

                // 最后5秒加快闪烁
                if (secondsLeft <= 5) {
                    tvCountdown.setAlpha((secondsLeft % 2 == 0) ? 1f : 0.5f);
                }
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("0");
                // 倒计时结束，自动触发紧急求助
                if (!isTest && !hasResponded) {
                    getLastLocationAndStartHelp();
                } else {
                    Toast.makeText(FallConfirmActivity.this,
                            "测试模式结束", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }.start();
    }

    /**
     * 取消倒计时
     */
    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    /**
     * 获取位置并启动紧急求助
     */
    private void getLastLocationAndStartHelp() {
        // 发送广播：用户需要帮助（服务暂停10分钟）
        Intent broadcastIntent = new Intent("com.android.test.FALL_RESULT");
        broadcastIntent.putExtra("is_safe", false);
        broadcastIntent.putExtra("action", "PAUSE_10_MINUTES");
        broadcastIntent.putExtra("timestamp", System.currentTimeMillis());

        // 如果有位置信息，一起发送
        if (currentLocation != null) {
            broadcastIntent.putExtra("latitude", currentLocation.getLatitude());
            broadcastIntent.putExtra("longitude", currentLocation.getLongitude());
        }

        sendBroadcast(broadcastIntent);

        // 获取最新位置并启动紧急求助页面
        getLatestLocationAndStartEmergency();
    }

    /**
     * 获取最新位置并启动紧急求助
     */
    private void getLatestLocationAndStartEmergency() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // 没有位置权限，直接进入紧急求助页面
            startEmergencyActivity(null);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NotNull Task<Location> task) {
                        Location location = null;
                        if (task.isSuccessful() && task.getResult() != null) {
                            location = task.getResult();
                        }
                        startEmergencyActivity(location);
                    }
                });
    }

    /**
     * 启动紧急求助页面
     */
    private void startEmergencyActivity(Location location) {
        Intent intent = new Intent(FallConfirmActivity.this, EmergencyHelpActivity.class);
        intent.putExtra("impact_force", impactForce);
        intent.putExtra("angle", angle);
        intent.putExtra("timestamp", timestamp);

        startActivity(intent);
        finish();
    }

    /**
     * 初始化语音助手
     */
    private void initVoiceAssistant() {
        // 初始化TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINESE);
                tts.setSpeechRate(0.9f); // 稍微慢一点，适合老年人

                // 设置语音播报完成监听
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {}

                    @Override
                    public void onDone(String utteranceId) {
                        // 播报完成后开始语音识别
                        if (!hasResponded && isVoiceActive && asrManager != null) {
                            runOnUiThread(() -> {
                                Toast.makeText(FallConfirmActivity.this,
                                        "请回答：您还好吗？", Toast.LENGTH_SHORT).show();
                                asrManager.start();
                            });
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {}
                });
            }
        });

        // 初始化QwenManager（用于语义理解）
        qwenManager = new QwenManager();

        // 初始化ASR（语音识别）
        asrManager = new SimpleAsrManager(this, new SimpleAsrManager.OnAsrListener() {
            @Override
            public void onResult(String text) {
                if (!hasResponded && isVoiceActive) {
                    processVoiceCommand(text);
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "识别错误: " + error);
                if (!hasResponded && isVoiceActive) {
                    // 识别失败，重新提示
                    handler.postDelayed(() -> {
                        if (!hasResponded && isVoiceActive) {
                            speak("我没有听清，您还好吗？");
                        }
                    }, 1000);
                }
            }
        });

        // 激活语音状态
        isVoiceActive = true;

        // 延迟一点播报，确保页面完全加载
        handler.postDelayed(() -> {
            if (!hasResponded && isVoiceActive) {
                speak("检测到您可能摔倒了，您还好吗？");
            }
        }, 500);
    }

    /**
     * 处理语音指令
     */
    /**
     * 处理语音指令
     */
    private void processVoiceCommand(String text) {
        if (hasResponded || !isVoiceActive) return;

        Log.d(TAG, "用户说: " + text);

        // 先直接通过关键词快速判断，提高响应速度
        String lowerText = text.toLowerCase();
        if (lowerText.contains("帮助") || lowerText.contains("救命") ||
                lowerText.contains("不好") || lowerText.contains("有事") ||
                lowerText.contains("help") || lowerText.contains("aid")) {
            // 用户需要帮助
            hasResponded = true;
            speakWithCallback("好的，正在为您联系紧急联系人", new Runnable() {
                @Override
                public void run() {
                    // 语音播报完成后执行
                    if (!isFinishing()) {
                        btnHelp.performClick();
                    }
                }
            });
            return;
        }

        if (lowerText.contains("没事") || lowerText.contains("还好") ||
                lowerText.contains("安全") || lowerText.contains("ok") ||
                lowerText.contains("fine") || lowerText.contains("good")) {
            // 用户表示安全
            hasResponded = true;
            speakWithCallback("好的，已为您退出页面", new Runnable() {
                @Override
                public void run() {
                    // 语音播报完成后执行
                    if (!isFinishing()) {
                        btnOk.performClick();
                    }
                }
            });
            Toast.makeText(FallConfirmActivity.this,
                    "已理解您的意思，请点击'我没事'按钮确认", Toast.LENGTH_LONG).show();
            return;
        }

        // 如果关键词无法判断，再使用大模型
        hasResponded = true; // 先标记已回应，避免重复处理
        processWithQwen(text);
    }

    /**
     * 带完成回调的语音播报
     * @param text 要播报的文本
     * @param callback 播报完成后的回调
     */
    private void speakWithCallback(String text, Runnable callback) {
        if (tts == null) return;

        if (tts.isSpeaking()) {
            tts.stop();
        }

        // 使用HashMap传递参数，实现回调
        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "callback_utterance");

        // 设置播报完成监听
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
                // 播报完成，在主线程执行回调
                if (callback != null) {
                    runOnUiThread(callback);
                }
            }

            @Override
            public void onError(String utteranceId) {
                // 出错时也要执行回调，避免卡死
                if (callback != null) {
                    runOnUiThread(callback);
                }
            }
        });

        // 开始播报
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
    }

    /**
     * 使用大模型处理复杂语义
     */
    private void processWithQwen(String text) {
        // 使用大模型理解用户意图
        String prompt = "用户刚才摔倒了，系统询问他'您还好吗？'，用户回答：" + text +
                "。请判断用户是表示'没事/安全'还是'需要帮助'。只返回JSON格式：{\"reply\":\"SAFE\"}或{\"reply\":\"HELP\"}";

        qwenManager.sendMessage(prompt, new QwenManager.QwenCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                runOnUiThread(() -> {
                    try {
                        Log.d(TAG, "大模型返回: " + jsonResponse);

                        // 清理返回的字符串
                        String cleanResponse = jsonResponse;
                        if (cleanResponse.contains("```json")) {
                            cleanResponse = cleanResponse.replace("```json", "").replace("```", "");
                        }
                        cleanResponse = cleanResponse.trim();

                        // 解析JSON
                        JSONObject result = new JSONObject(cleanResponse);
                        String reply = result.optString("reply", "");

                        if ("HELP".equals(reply)) {
                            // 用户需要帮助 - 等待语音说完再执行点击
                            speakWithCallback("好的，正在为您联系紧急联系人", new Runnable() {
                                @Override
                                public void run() {
                                    // 语音播报完成后执行
                                    if (!isFinishing()) {
                                        btnHelp.performClick();
                                    }
                                }
                            });
                        } else if ("SAFE".equals(reply)) {
                            // 用户表示安全 - 等待语音说完再执行点击
                            speakWithCallback("好的，已为您退出页面", new Runnable() {
                                @Override
                                public void run() {
                                    // 语音播报完成后执行
                                    if (!isFinishing()) {
                                        btnOk.performClick();
                                    }
                                }
                            });
                            Toast.makeText(FallConfirmActivity.this,
                                    "已理解您的意思，为您退出页面", Toast.LENGTH_LONG).show();
                        } else {
                            // 无法理解，重新询问
                            hasResponded = false;
                            speak("抱歉，我没有理解您的意思。如果您需要帮助请说'需要帮助'，如果没事请说'我没事'");
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON解析错误: " + e.getMessage());
                        // 尝试直接匹配返回的文本
                        if (jsonResponse.contains("HELP") || jsonResponse.contains("帮助")) {
                            speakWithCallback("好的，正在为您联系紧急联系人", new Runnable() {
                                @Override
                                public void run() {
                                    // 语音播报完成后执行
                                    if (!isFinishing()) {
                                        btnHelp.performClick();
                                    }
                                }
                            });
                        } else if (jsonResponse.contains("SAFE") || jsonResponse.contains("安全") || jsonResponse.contains("没事")) {
                            speakWithCallback("好的，已为您退出页面", new Runnable() {
                                @Override
                                public void run() {
                                    // 语音播报完成后执行
                                    if (!isFinishing()) {
                                        btnOk.performClick();
                                    }
                                }
                            });
                            Toast.makeText(FallConfirmActivity.this,
                                    "已理解您的意思，请点击'我没事'按钮确认", Toast.LENGTH_LONG).show();
                        } else {
                            hasResponded = false;
                            speak("抱歉，我没有理解您的意思");
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "大模型错误: " + error);
                // 大模型调用失败，但用户已经说了"需要帮助"，不应该说没理解
                runOnUiThread(() -> {
                    // 直接根据用户刚才说的话判断
                    if (text.contains("帮助") || text.contains("救命") || text.contains("不好")) {
                        speakWithCallback("好的，正在为您联系紧急联系人", new Runnable() {
                            @Override
                            public void run() {
                                // 语音播报完成后执行
                                if (!isFinishing()) {
                                    btnHelp.performClick();
                                }
                            }
                        });
                    } else {
                        hasResponded = false;
                        speak("网络连接失败，请手动点击按钮");
                    }
                });
            }
        });
    }

    /**
     * 解析大模型响应
     */
    private String parseQwenResponse(String jsonStr) {
        try {
            Log.d(TAG, "解析大模型返回: " + jsonStr);

            // 清理可能的markdown格式
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("{"), jsonStr.lastIndexOf("}") + 1);
            }

            JSONObject result = new JSONObject(jsonStr);
            // 根据实际的大模型返回格式调整
            if (result.has("reply")) {
                return result.optString("reply", "");
            } else if (result.has("response")) {
                return result.optString("response", "");
            } else if (result.has("text")) {
                return result.optString("text", "");
            } else {
                // 如果都不是，返回整个字符串进行后续处理
                return jsonStr;
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON解析失败: " + e.getMessage());
            // 如果不是JSON格式，直接返回原字符串
            return jsonStr;
        }
    }

    /**
     * 关键词匹配降级方案
     */
    private void fallbackKeywordMatch(String text) {
        if (text == null) return;

        String lowerText = text.toLowerCase();

        // 安全相关的关键词
        String[] safeKeywords = {"没事", "还好", "安全", "没摔", "可以", "ok", "好", "不用", "不需要", "safe", "fine", "good"};
        // 求助相关的关键词
        String[] helpKeywords = {"帮助", "求助", "救命", "不好", "有事", "摔倒", "疼", "痛", "help", "aid", "hurt", "pain"};

        for (String keyword : safeKeywords) {
            if (lowerText.contains(keyword)) {
                // 用户表示安全
                Toast.makeText(this, "已理解您说'没事'，请点击确认按钮", Toast.LENGTH_LONG).show();
                return;
            }
        }

        for (String keyword : helpKeywords) {
            if (lowerText.contains(keyword)) {
                // 用户需要帮助
                speak("好的，正在为您联系紧急联系人");
                handler.postDelayed(() -> {
                    if (!isFinishing()) {
                        btnHelp.performClick();
                    }
                }, 1500);
                return;
            }
        }

        // 无法理解，重新询问
        hasResponded = false;
        speak("抱歉，我没有理解。如果您需要帮助请说'需要帮助'，如果没事请说'我没事'");
    }

    /**
     * TTS播报
     */
    private void speak(String text) {
        if (tts != null) {
            if (tts.isSpeaking()) {
                tts.stop();
            }
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "fall_confirm");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "fall_confirm");
        }
    }

    /**
     * 停止语音助手
     */
    private void stopVoiceAssistant() {
        isVoiceActive = false;
        if (asrManager != null) {
            asrManager.stop();
        }
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
        }
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停语音识别，但不完全关闭
        if (asrManager != null) {
            asrManager.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 如果用户还没回应且语音助手还激活，重新开始识别
        if (!hasResponded && isVoiceActive && asrManager != null) {
            // 不自动开始识别，等下一次播报
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelCountdown();
        stopVoiceAssistant();

        // 释放资源
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (asrManager != null) {
            asrManager.release();
        }
        handler.removeCallbacksAndMessages(null);
    }
}