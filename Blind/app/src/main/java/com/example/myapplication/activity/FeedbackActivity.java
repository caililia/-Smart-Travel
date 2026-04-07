package com.example.myapplication.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.activity.login.LoginActivity;
import com.example.myapplication.data.UserData;
import com.example.myapplication.manage.SimpleAsrManager;
import com.example.myapplication.utils.OkhttpUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class FeedbackActivity extends AppCompatActivity {

    private static final String TAG = "FeedbackActivity";
    private static final String FEEDBACK_URL = OkhttpUtils.URL + "/feedback/manage/submitFeedBack";

    // UI控件
    private EditText etFeedbackContent;
    private TextView tvVoiceStatus;
    private Button btSubmit;

    // 语音相关
    private TextToSpeech tts;
    private SimpleAsrManager asrManager;
    private Vibrator vibrator;

    // 反馈内容拼接
    private StringBuilder feedbackContent = new StringBuilder();
    // 语音交互状态
    private boolean isWaitingForMore = false;
    // 新增：标记TTS是否正在播放，避免识别到自己的声音
    private AtomicBoolean isTtsSpeaking = new AtomicBoolean(false);
    // 新增：延迟启动识别的Handler
    private Handler handler = new Handler();
    private Runnable startAsrRunnable;
    private String phone;
    private int userId;
    private UserData userData;
    private String title = "";
    private HashMap<String, Object> map = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        // 初始化控件
        etFeedbackContent = findViewById(R.id.etFeedbackContent);
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus);
        btSubmit = findViewById(R.id.btSubmit);

        // 初始化震动器
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // 初始化TTS语音播报
        initTTS();

        // 初始化ASR语音识别
        initASR();

        // 启动语音交互流程
        new Handler().postDelayed(this::startVoiceInteraction, 1500);

        // 手动提交按钮点击事件
        btSubmit.setOnClickListener(v -> submitFeedback());
        SharedPreferences sharedPreferences = getSharedPreferences("phone", MODE_PRIVATE);
        phone = sharedPreferences.getString("phone", "");

        if (TextUtils.isEmpty(phone)) {
            Intent intent = new Intent(this, LoginActivity.class);
            Toast.makeText(this, "登录信息已过期，请重新登录", Toast.LENGTH_SHORT).show();
            startActivity(intent);
        }

        initUserInfo();
    }

    /**
     * 初始化TTS语音播报
     */
    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "中文语言包不支持");
                    Toast.makeText(this, "语音播报初始化失败", Toast.LENGTH_SHORT).show();
                }
                // 设置语速
                tts.setSpeechRate(0.9f);
            } else {
                Log.e(TAG, "TTS初始化失败");
                Toast.makeText(this, "语音播报初始化失败", Toast.LENGTH_SHORT).show();
            }
        });

        // 设置TTS播报完成监听
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // TTS开始播放时，标记状态并停止ASR
                isTtsSpeaking.set(true);
                runOnUiThread(() -> {
                    if (asrManager != null) {
                        asrManager.stop();
                    }
                    Log.d(TAG, "TTS开始播放，停止ASR监听");
                });
            }

            @Override
            public void onDone(String utteranceId) {
                // TTS播放完成，标记状态并启动ASR（如果需要）
                isTtsSpeaking.set(false);
                runOnUiThread(() -> {
                    Log.d(TAG, "TTS播放完成，准备启动ASR");
                    // 延迟300ms启动，避免系统回声
                    if (startAsrRunnable != null) {
                        handler.removeCallbacks(startAsrRunnable);
                    }
                    startAsrRunnable = () -> {
                        if (isWaitingForMore && !isTtsSpeaking.get()) {
                            startASRListening();
                        }
                    };
                    handler.postDelayed(startAsrRunnable, 300);
                });
            }

            @Override
            public void onError(String utteranceId) {
                isTtsSpeaking.set(false);
                runOnUiThread(() -> {
                    Log.e(TAG, "TTS播放出错");
                    if (isWaitingForMore && !isTtsSpeaking.get()) {
                        startASRListening();
                    }
                });
            }
        });
    }

    /**
     * 初始化ASR语音识别
     */
    private void initASR() {
        asrManager = new SimpleAsrManager(this, new SimpleAsrManager.OnAsrListener() {
            @Override
            public void onResult(String text) {
                runOnUiThread(() -> {
                    // 停止识别，避免连续输入
                    if (asrManager != null) {
                        asrManager.stop();
                    }
                    // 取消延迟启动任务
                    if (startAsrRunnable != null) {
                        handler.removeCallbacks(startAsrRunnable);
                    }
                    processVoiceInput(text);
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "语音识别错误: " + error);
                runOnUiThread(() -> {
                    tvVoiceStatus.setText("小黎：识别失败，请再说一遍");
                    speak("识别失败，请再说一遍");
                    // 延迟1秒后重新开始识别
                    handler.postDelayed(() -> {
                        if (isWaitingForMore && !isTtsSpeaking.get()) {
                            startASRListening();
                        }
                    }, 1000);
                });
            }
        });
    }

    /**
     * 启动语音交互流程
     */
    private void startVoiceInteraction() {
        isWaitingForMore = true;
        String welcomeMsg = "您想反馈什么内容呢";
        tvVoiceStatus.setText("小黎：" + welcomeMsg);
        speakWithUtteranceId(welcomeMsg, "welcome");
    }

    /**
     * 带ID的语音播报，用于监听播报状态
     */
    private void speakWithUtteranceId(String text, String utteranceId) {
        if (tts != null) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        }
    }

    /**
     * 普通语音播报（用于简短提示）
     */
    private void speak(String text) {
        if (tts != null) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "short");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "short");
        }
    }

    /**
     * 处理语音输入
     *
     * @param text 识别到的语音文本
     */
    private void processVoiceInput(String text) {
        if (TextUtils.isEmpty(text)) {
            speak("我没听清，请再说一遍");
            handler.postDelayed(() -> {
                if (isWaitingForMore && !isTtsSpeaking.get()) {
                    startASRListening();
                }
            }, 1000);
            return;
        }

        // 震动反馈
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(200);
        }

        Log.d(TAG, "用户语音输入: " + text);

        // 过滤掉TTS自己的声音（可选：过滤常见关键词）
        String filteredText = text.replaceAll("您想反馈什么内容呢", "")
                .replaceAll("您还想反馈什么呢", "")
                .replaceAll("如果没有的话将提交反馈", "")
                .trim();

        if (TextUtils.isEmpty(filteredText)) {
            Log.w(TAG, "识别到TTS自己的声音，忽略");
            // 重新开始监听
            handler.postDelayed(() -> {
                if (isWaitingForMore && !isTtsSpeaking.get()) {
                    startASRListening();
                }
            }, 500);
            return;
        }

        // 判断是否是结束指令
        if (isWaitingForMore && (filteredText.contains("没有了") || filteredText.contains("好的") ||
                filteredText.contains("提交") || filteredText.contains("就这样") ||
                filteredText.contains("完成") || filteredText.contains("没有"))) {

            speak("正在为您提交反馈");
            tvVoiceStatus.setText("小黎：正在为您提交反馈");
            submitFeedback();
            return;
        }

        // 拼接反馈内容
        if (feedbackContent.length() > 0) {
            feedbackContent.append("，");
        }
        feedbackContent.append(filteredText);

        // 更新输入框
        etFeedbackContent.setText(feedbackContent.toString());
        etFeedbackContent.setSelection(feedbackContent.length());

        // 询问是否还有更多反馈
        isWaitingForMore = true;
        String continueMsg = "您还想反馈什么呢，如果没有的话将提交反馈";
        tvVoiceStatus.setText("小黎：" + continueMsg);
        // 使用带ID的播报，以便监听播报完成
        speakWithUtteranceId(continueMsg, "continue");
    }

    /**
     * 启动语音识别监听
     */
    private void startASRListening() {
        // 确保TTS不在播放时才启动
        if (isTtsSpeaking.get()) {
            Log.d(TAG, "TTS正在播放，延迟启动ASR");
            handler.postDelayed(this::startASRListening, 500);
            return;
        }

        if (asrManager != null && isWaitingForMore) {
            tvVoiceStatus.setText("小黎：正在等待您的语音指令...");
            asrManager.start();
            Log.d(TAG, "ASR监听已启动");
        }
    }

    /*加载用户信息*/
    private void initUserInfo() {
        OkhttpUtils.request("GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("onFailure", "onFailure: " + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() != null) {
                    String json = response.body().string();
                    userData = OkhttpUtils.toData(json, UserData.class);
                    userId = userData.getData().getUserId();
                }
            }
        });
    }

    /**
     * 提交反馈
     */
    private void submitFeedback() {
        String content = etFeedbackContent.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            speak("反馈内容不能为空，请先说点什么吧");
            tvVoiceStatus.setText("小黎：反馈内容不能为空，请先说点什么吧");
            handler.postDelayed(() -> {
                if (isWaitingForMore && !isTtsSpeaking.get()) {
                    startASRListening();
                }
            }, 1000);
            return;
        }

        // 禁用提交按钮，停止等待更多输入
        isWaitingForMore = false;
        btSubmit.setEnabled(false);

        // 停止ASR监听
        if (asrManager != null) {
            asrManager.stop();
        }


        map.put("userId", userId);
        if (title.equals("")){
            map.put("title", phone.substring(5) + "用户说");
        } else {
            map.put("title", title);
        }

        map.put("content", content);
        map.put("createTime", "");

        // 调用提交接口
        OkhttpUtils.request("POST", FEEDBACK_URL, OkhttpUtils.toBody(map), "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "提交反馈失败: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(FeedbackActivity.this, "提交失败，请检查网络", Toast.LENGTH_SHORT).show();
                    speak("提交失败，请检查网络");
                    btSubmit.setEnabled(true);
                    isWaitingForMore = true;
                    startASRListening();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(FeedbackActivity.this, "反馈提交成功", Toast.LENGTH_SHORT).show();
                        speak("反馈提交成功，感谢您的建议");
                        // 延迟关闭页面
                        handler.postDelayed(() -> finish(), 3000);
                    } else {
                        Toast.makeText(FeedbackActivity.this, "提交失败，错误码：" + response.code(), Toast.LENGTH_SHORT).show();
                        speak("提交失败，请稍后再试");
                        btSubmit.setEnabled(true);
                        isWaitingForMore = true;
                        startASRListening();
                    }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 页面恢复时，如果没有正在提交，重新开始监听
        if (isWaitingForMore && btSubmit.isEnabled() && !isTtsSpeaking.get()) {
            startASRListening();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停识别
        if (asrManager != null) {
            asrManager.stop();
        }
        // 暂停语音播报
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
        }
        // 移除延迟任务
        if (startAsrRunnable != null) {
            handler.removeCallbacks(startAsrRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放资源
        if (asrManager != null) {
            asrManager.release();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        handler.removeCallbacksAndMessages(null);
    }
}