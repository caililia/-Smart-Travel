package com.example.myapplication.activity;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.manage.SimpleAsrManager;

import java.util.ArrayList;
import java.util.Locale;

public class HelpActivity extends AppCompatActivity {

    private static final String TAG = "HelpActivity";
    // 语音指令关键词
    private static final String CMD_STOP = "停下|停止|暂停|小黎";
    private static final String CMD_CONTINUE = "继续|接着读|继续读";
    private static final String CMD_EXIT = "退出页面|返回|退出|关闭";

    // UI控件
    private TextView tvHelpContent;
    private TextView tvVoiceStatus;

    // 语音相关
    private TextToSpeech tts;
    private SimpleAsrManager asrManager;
    private Vibrator vibrator;

    // 朗读相关
    private ArrayList<String> helpContentList; // 按段落拆分的帮助内容
    private int currentReadIndex = 0; // 当前朗读到的段落索引（断点）
    private boolean isReading = false; // 是否正在朗读
    private boolean isPaused = false; // 是否暂停
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        // 初始化控件
        tvHelpContent = findViewById(R.id.tvHelpContent);
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus);

        // 初始化震动器
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // 初始化帮助内容
        initHelpContent();

        // 初始化TTS语音播报
        initTTS();

        // 初始化ASR语音识别（监听用户指令）
        initASR();

        // 1.5秒后开始朗读
        new Handler().postDelayed(this::startReadHelpContent, 1500);
    }

    /**
     * 初始化帮助内容，按段落拆分便于断点续读
     */
    private void initHelpContent() {
        // 帮助手册内容
        String helpText = "### 助盲APP 用户简易帮助手册\n" +
                "## 一、APP简介\n" +
                "本APP专为视障用户设计，支持语音操控、双击点击、语音反馈等功能，无需手动操作，简单易用，助力无障碍使用。\n" +
                "## 二、基础操作说明\n" +
                "1. 唤醒语音助手（小黎）：直接说出唤醒词，即可唤醒小黎，唤醒后有震动+语音提示“我在”。\n" +
                "2. 页面操作方式：所有功能选项双击进入，单击语音提示“双击进入”，同时触发震动反馈，方便感知操作。\n" +
                "3. 自动休眠：语音助手60秒无操作，将自动休眠，说出唤醒词可重新激活。\n" +
                "## 三、主要功能使用方法\n" +
                "### 1. 个人中心\n" +
                "- 查看账号：显示脱敏手机号，标注“已认证·助盲用户”\n" +
                "- 语音导航：说出“首页”“社区”，可自动切换页面\n" +
                "- 语音查询：说出“有什么功能”“这是什么页面”，自动语音介绍\n" +
                "### 2. 语音设置\n" +
                "双击“语音设置”，进入页面可调节语音语速、开关语音播报等。\n" +
                "### 3. 意见反馈（语音提交）\n" +
                "1）双击进入意见反馈页面，1.5秒后小黎播报：您想反馈什么内容呢\n" +
                "2）直接说出反馈内容（如：功能很好用），自动填入输入框\n" +
                "3）小黎播报：您还想反馈什么呢，如果没有的话将提交反馈\n" +
                "4）说出“没有了/好的/提交”，自动提交反馈，提示提交成功\n" +
                "### 4. 使用帮助&关于我们\n" +
                "双击即可进入，语音自动讲解相关信息。\n" +
                "### 5. 退出登录\n" +
                "双击“退出登录”，语音确认后，自动退出并跳转登录页。\n" +
                "## 四、常见问题\n" +
                "1. 听不到语音播报？检查手机音量，确保媒体音量开启。\n" +
                "2. 语音识别失败？靠近手机，语速放缓，重新说出指令即可。\n" +
                "3. 助手不响应？语音助手已休眠，重新说出唤醒词激活。\n" +
                "## 五、温馨提示\n" +
                "所有操作均有语音播报+震动反馈，放心使用；如有问题，可通过意见反馈提交建议。";

        // 显示帮助内容到页面
        tvHelpContent.setText(helpText);

        // 按段落拆分内容（去掉markdown标记，按换行拆分）
        helpContentList = new ArrayList<>();
        String[] paragraphs = helpText.replaceAll("#+ ", "").split("\n");
        for (String p : paragraphs) {
            if (!TextUtils.isEmpty(p.trim())) {
                helpContentList.add(p.trim());
            }
        }
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

        // 监听朗读进度，自动读下一段
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                runOnUiThread(() -> {
                    isReading = true;
                    tvVoiceStatus.setText("小黎：正在朗读第" + (currentReadIndex + 1) + "段...");
                });
            }

            @Override
            public void onDone(String utteranceId) {
                runOnUiThread(() -> {
                    // 读完当前段，读下一段
                    currentReadIndex++;
                    if (currentReadIndex < helpContentList.size()) {
                        readCurrentParagraph();
                    } else {
                        // 全部读完
                        isReading = false;
                        tvVoiceStatus.setText("小黎：帮助内容已朗读完毕");
                        speak("帮助内容已朗读完毕");
                    }
                });
            }

            @Override
            public void onError(String utteranceId) {
                runOnUiThread(() -> {
                    isReading = false;
                    tvVoiceStatus.setText("小黎：朗读出错，请重试");
                    speak("朗读出错，请重试");
                });
            }
        });
    }

    /**
     * 初始化ASR语音识别（监听用户指令）
     */
    private void initASR() {
        asrManager = new SimpleAsrManager(this, new SimpleAsrManager.OnAsrListener() {
            @Override
            public void onResult(String text) {
                runOnUiThread(() -> processUserCommand(text));
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "语音识别错误: " + error);
                // 识别失败不影响，继续监听
                startASRListening();
            }
        });

        // 启动语音指令监听
        startASRListening();
    }

    /**
     * 开始朗读帮助内容
     */
    private void startReadHelpContent() {
        if (helpContentList.isEmpty()) {
            tvVoiceStatus.setText("小黎：暂无帮助内容");
            return;
        }

        tvVoiceStatus.setText("小黎：开始为您朗读帮助内容");
        speak("开始为您朗读帮助内容");

        // 延迟启动朗读（等提示音说完）
        new Handler().postDelayed(this::readCurrentParagraph, 1000);
    }

    /**
     * 朗读当前段落（断点）
     */
    private void readCurrentParagraph() {
        if (currentReadIndex >= helpContentList.size()) {
            return;
        }

        String content = helpContentList.get(currentReadIndex);
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "help_" + currentReadIndex);
        tts.speak(content, TextToSpeech.QUEUE_FLUSH, params, "help_" + currentReadIndex);

        // 继续监听用户指令
        startASRListening();
    }

    /**
     * 处理用户语音指令
     */
    private void processUserCommand(String text) {
        if (TextUtils.isEmpty(text)) {
            startASRListening();
            return;
        }

        Log.d(TAG, "用户指令: " + text);
        // 震动反馈
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(200);
        }

        // 匹配停止指令
        if (text.matches(".*(" + CMD_STOP + ").*")) {
            stopReading();
            tvVoiceStatus.setText("小黎：已暂停朗读，您可以说继续继续朗读，或说退出页面离开");
            speak("已暂停朗读，您可以说继续继续朗读，或说退出页面离开");
        }
        // 匹配继续指令
        else if (text.matches(".*(" + CMD_CONTINUE + ").*")) {
            continueReading();
        }
        // 匹配退出指令
        else if (text.matches(".*(" + CMD_EXIT + ").*")) {
            speak("正在退出使用帮助页面");
            handler.postDelayed(() -> {
                finish();
            }, 2000);
        }
        // 未知指令，继续监听
        else {
            startASRListening();
        }
    }

    /**
     * 停止朗读（记录断点）
     */
    private void stopReading() {
        if (isReading) {
            tts.stop();
            isReading = false;
            isPaused = true;
        }
    }

    /**
     * 继续朗读（从断点开始）
     */
    private void continueReading() {
        if (isPaused) {
            tvVoiceStatus.setText("小黎：继续为您朗读第" + (currentReadIndex + 1) + "段");
            speak("继续为您朗读第" + (currentReadIndex + 1) + "段");
            new Handler().postDelayed(() -> {
                isPaused = false;
                readCurrentParagraph();
            }, 1000);
        } else if (!isReading) {
            tvVoiceStatus.setText("小黎：还未开始朗读，现在为您开始");
            speak("还未开始朗读，现在为您开始");
            startReadHelpContent();
        } else {
            tvVoiceStatus.setText("小黎：正在朗读中");
            speak("正在朗读中");
            startASRListening();
        }
    }

    /**
     * 启动语音指令监听
     */
    private void startASRListening() {
        if (asrManager != null) {
            asrManager.start();
        }
    }

    /**
     * 语音播报
     */
    private void speak(String text) {
        if (tts != null && !tts.isSpeaking()) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "cmd");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "cmd");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 页面恢复后继续监听指令
        startASRListening();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停朗读和识别
        if (isReading) {
            tts.stop();
        }
        if (asrManager != null) {
            asrManager.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放资源
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (asrManager != null) {
            asrManager.release();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }
}