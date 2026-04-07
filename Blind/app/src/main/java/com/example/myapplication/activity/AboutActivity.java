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

public class AboutActivity extends AppCompatActivity {

    private static final String TAG = "AboutActivity";
    // 指令
    private static final String CMD_STOP = "停下|停止|暂停|小黎小黎";
    private static final String CMD_CONTINUE = "继续|接着读|继续读";
    private static final String CMD_EXIT = "退出页面|返回|退出|关闭";

    // UI
    private TextView tvAboutContent;
    private TextView tvVoiceStatus;

    // 语音
    private TextToSpeech tts;
    private SimpleAsrManager asrManager;
    private Vibrator vibrator;

    // 朗读断点
    private ArrayList<String> contentList;
    private int currentReadIndex = 0;
    private boolean isReading = false;
    private boolean isPaused = false;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        tvAboutContent = findViewById(R.id.tvAboutContent);
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        initAboutContent();
        initTTS();
        initASR();

        // 1.5秒后开始朗读
        new Handler().postDelayed(this::startReadContent, 1500);
    }

    // 填充关于我们文案，按段落拆分
    private void initAboutContent() {
        String text = "关于我们\n" +
                "本应用是一款面向视障人群的智能助盲辅助软件。\n" +
                "我们致力于打造简单、安全、免费、好用的无障碍生活助手。\n" +
                "支持语音唤醒、语音导航、语音反馈、双击操作、意见反馈等无障碍能力。\n" +
                "全程语音播报、震动提示，让看不见也能轻松上手。\n" +
                "版本信息：V 1.0\n" +
                "如有问题，欢迎在意见反馈中向我们留言。";

        tvAboutContent.setText(text);

        contentList = new ArrayList<>();
        String[] ps = text.split("\n");
        for (String p : ps) {
            if (!TextUtils.isEmpty(p.trim())) {
                contentList.add(p.trim());
            }
        }
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(Locale.CHINESE);
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "语音播报初始化失败", Toast.LENGTH_SHORT).show();
                }
                tts.setSpeechRate(0.9f);
            } else {
                Toast.makeText(this, "语音播报初始化失败", Toast.LENGTH_SHORT).show();
            }
        });

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                runOnUiThread(() -> {
                    isReading = true;
                    tvVoiceStatus.setText("小黎：正在朗读第" + (currentReadIndex + 1) + "段");
                });
            }

            @Override
            public void onDone(String utteranceId) {
                runOnUiThread(() -> {
                    currentReadIndex++;
                    if (currentReadIndex < contentList.size()) {
                        readNow();
                    } else {
                        isReading = false;
                        tvVoiceStatus.setText("小黎：关于我们朗读完毕");
                        speak("关于我们朗读完毕");
                    }
                });
            }

            @Override
            public void onError(String utteranceId) {
                runOnUiThread(() -> {
                    isReading = false;
                    tvVoiceStatus.setText("小黎：朗读出错");
                });
            }
        });
    }

    private void initASR() {
        asrManager = new SimpleAsrManager(this, new SimpleAsrManager.OnAsrListener() {
            @Override
            public void onResult(String text) {
                runOnUiThread(() -> doCmd(text));
            }

            @Override
            public void onError(String error) {
                startListen();
            }
        });
        startListen();
    }

    private void startReadContent() {
        if (contentList.isEmpty()) return;
        tvVoiceStatus.setText("小黎：开始为您朗读关于我们");
        speak("开始为您朗读关于我们");
        new Handler().postDelayed(this::readNow, 1000);
    }

    private void readNow() {
        if (currentReadIndex >= contentList.size()) return;
        String s = contentList.get(currentReadIndex);
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "about_" + currentReadIndex);
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, params, "about_" + currentReadIndex);
        startListen();
    }

    private void doCmd(String text) {
        if (TextUtils.isEmpty(text)) {
            startListen();
            return;
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(200);
        }

        if (text.matches(".*(" + CMD_STOP + ").*")) {
            stopRead();
            tvVoiceStatus.setText("小黎：已暂停，您可以说继续或退出页面");
            speak("已暂停朗读，您可以说继续继续朗读，或说退出页面离开");
        } else if (text.matches(".*(" + CMD_CONTINUE + ").*")) {
            resumeRead();
        } else if (text.matches(".*(" + CMD_EXIT + ").*")) {
            speak("退出关于我们页面");
            handler.postDelayed(() -> {
                finish();
            }, 2000);
        } else {
            startListen();
        }
    }

    private void stopRead() {
        if (isReading) {
            tts.stop();
            isReading = false;
            isPaused = true;
        }
    }

    private void resumeRead() {
        if (isPaused) {
            tvVoiceStatus.setText("小黎：继续朗读");
            speak("继续为您朗读");
            new Handler().postDelayed(() -> {
                isPaused = false;
                readNow();
            }, 800);
        }
    }

    private void startListen() {
        if (asrManager != null) asrManager.start();
    }

    private void speak(String s) {
        if (tts != null && !tts.isSpeaking()) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "cmd");
            tts.speak(s, TextToSpeech.QUEUE_FLUSH, params, "cmd");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startListen();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isReading) tts.stop();
        if (asrManager != null) asrManager.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (asrManager != null) asrManager.release();
        if (vibrator != null) vibrator.cancel();
    }
}