package com.example.myapplication.activity.login;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.data.CaptchaData;
import com.example.myapplication.data.LoginData;
import com.example.myapplication.data.UserData;
import com.example.myapplication.manage.QwenManager;
import com.example.myapplication.manage.SimpleAsrManager;
import com.example.myapplication.manage.SimpleWakeUpManager;
import com.example.myapplication.utils.OkhttpUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;

public class LoginActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "LoginActivity";

    // UI 组件
    private Button btLogin, btRegister, btCode;
    private TextView tvPwd, tvCode, tvForget, tvPwdLogin, tvTitle;
    private EditText etAccNum, etPwd, etCode;
    private LinearLayout llCode;

    // 样式颜色
    private int color = Color.parseColor("#FFFFFF");
    private int color2 = Color.parseColor("#000000");

    // 数据存储
    private HashMap<String, Object> map = new HashMap<>();
    private HashMap<String, Object> map2 = new HashMap<>();

    private String phone;
    private String password;
    private String code;

    private CaptchaData captchaData = new CaptchaData();
    private UserData userData;

    // --- 语音与AI相关 ---
    private SimpleWakeUpManager wakeUpManager;
    private SimpleAsrManager asrManager;
    private TextToSpeech tts;
    private QwenManager qwenManager;

    // 倒计时器
    private CountDownTimer countDownTimer;
    private static final long TOTAL_TIME = 60000;
    private static final long INTERVAL = 1000;

    // 逻辑标记：是否正在等待用户确认手机号
    private boolean isWaitingForConfirmation = false;

    // 【核心控制变量】：语音助手是否处于激活状态
    // 默认为 false，只有唤醒成功后才变为 true
    private boolean isVoiceActive = false;

    // --- 自动休眠机制 ---
    private Handler sleepHandler = new Handler();
    // 60秒无交互自动休眠
    private static final long SLEEP_DELAY = 60000;

    private Runnable sleepRunnable = new Runnable() {
        @Override
        public void run() {
            if (isVoiceActive) {
                isVoiceActive = false;
                Log.d(TAG, "语音助手自动休眠");
                Toast.makeText(LoginActivity.this, "语音助手已休眠", Toast.LENGTH_SHORT).show();
                // 确保唤醒功能开启，等待下一次唤醒
                if (wakeUpManager != null) wakeUpManager.start();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 初始化大模型管理器
        qwenManager = new QwenManager();

        initView();
        initCountDownTimer();
        initTTS();
        initVoiceAssistant();
    }

    /**
     * 刷新休眠倒计时
     * 每次有语音输入或输出时调用，重置60秒倒计时
     */
    private void refreshSleepTimer() {
        if (!isVoiceActive) return;
        sleepHandler.removeCallbacks(sleepRunnable);
        sleepHandler.postDelayed(sleepRunnable, SLEEP_DELAY);
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                if (tts != null) tts.setLanguage(Locale.CHINESE);
            }
        });

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // 播报开始
            }

            @Override
            public void onDone(String utteranceId) {
                // 播报结束，回到主线程
                runOnUiThread(() -> {
                    // 【关键】只有在语音激活状态下，才开启ASR识别
                    if (!isVoiceActive) return;

                    Log.d(TAG, "TTS播报完毕，准备开启识别...");
                    // 延迟开启识别，避免录入TTS的尾音
                    new Handler().postDelayed(() -> {
                        if (!isFinishing() && isVoiceActive) {
                            if (asrManager != null) {
                                Toast.makeText(LoginActivity.this, "请说话...", Toast.LENGTH_SHORT).show();
                                asrManager.start();
                            }
                        }
                    }, 500);
                });
            }

            @Override
            public void onError(String utteranceId) {
                // 出错处理，重置回唤醒状态
                runOnUiThread(() -> {
                    if (wakeUpManager != null) wakeUpManager.start();
                });
            }
        });
    }

    private void initVoiceAssistant() {
        // --- 1. 初始化语音识别 (ASR) ---
        asrManager = new SimpleAsrManager(this, new SimpleAsrManager.OnAsrListener() {
            @Override
            public void onResult(String text) {
                // 收到用户语音，刷新休眠时间
                refreshSleepTimer();
                runOnUiThread(() -> processCommand(text));
            }

            @Override
            public void onError(String error) {
                Log.e("Voice", "识别错误: " + error);
                runOnUiThread(() -> {
                    // 识别失败不一定是交互结束，只要没超时，可以尝试回到唤醒状态等待
                    // 或者在这里不做任何操作，等待下一次点击或唤醒
                    if (wakeUpManager != null) wakeUpManager.start();
                });
            }
        });

        // --- 2. 初始化唤醒 (WakeUp) ---
        wakeUpManager = new SimpleWakeUpManager(this, new SimpleWakeUpManager.WakeUpListener() {
            @Override
            public void onSuccess(String word) {
                Log.d("Voice", "唤醒成功: " + word);
                Toast.makeText(LoginActivity.this, "我在！", Toast.LENGTH_SHORT).show();

                // 【核心逻辑】唤醒成功，激活语音权限
                isVoiceActive = true;

                // 停止唤醒检测，准备开始对话
                wakeUpManager.stop();

                // 重置逻辑状态
                isWaitingForConfirmation = false;

                // 启动自动休眠倒计时
                refreshSleepTimer();

                // 开始第一句对话（因为 isVoiceActive 为 true，这句话可以播报出来）
                speak("您好，我是小黎，这是登录页面，可以告诉我您的账号和密码，或者跳转到注册页面。");
            }

            @Override
            public void onError(String errorMsg) {
                Log.e("Voice", "唤醒失败: " + errorMsg);
            }
        });

        // 启动唤醒检测
        wakeUpManager.start();
    }

    private void processCommand(String text) {
        // 如果语音助手已休眠，忽略指令
        if (!isVoiceActive) return;

        if (text == null || text.trim().isEmpty()) return;

        String cleanText = text.replaceAll("[。，？!.,?!]", "").trim();
        Log.d("Voice", "用户说: " + text);

        // 获取当前界面状态上下文
        boolean isAccountEmpty = etAccNum.getText().toString().trim().isEmpty();
        boolean isPwdEmpty = etPwd.getText().toString().trim().isEmpty();
        boolean isPasswordMode = tvForget.getVisibility() == View.VISIBLE;

        StringBuilder stateBuilder = new StringBuilder();
        stateBuilder.append(String.format(" [当前界面状态: %s, 账号%s, 密码/验证码%s]",
                isPasswordMode ? "密码登录页" : "手机验证码登录页",
                isAccountEmpty ? "为空" : "已填",
                isPwdEmpty ? "为空" : "已填"));

        if (isWaitingForConfirmation) {
            stateBuilder.append("[特别注意: 当前正在等待用户确认账号]");
        }

        String messageToSend = cleanText + stateBuilder.toString();
        Toast.makeText(this, "思考中...", Toast.LENGTH_SHORT).show();

        // 发送给AI
        qwenManager.sendMessage(messageToSend, new QwenManager.QwenCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                runOnUiThread(() -> handleAiResponse(jsonResponse));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> speak("网络好像有点问题，请稍后再试"));
            }
        });
    }

    private void handleAiResponse(String jsonStr) {
        if (!isVoiceActive) return; // 二次检查状态

        refreshSleepTimer(); // 收到回复，刷新休眠时间

        Log.d("Voice", "AI回复: " + jsonStr);
        try {
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.replace("```json", "").replace("```", "");
            }

            JSONObject result = new JSONObject(jsonStr);
            String type = result.optString("type");
            String value = result.optString("value");
            String reply = result.optString("reply");

            // --- 状态与动作处理 ---

            // 填入账号
            if ("FILL_ACCOUNT".equals(type)) {
                etAccNum.setText(value);
                etAccNum.setSelection(value.length());
                if (tvPwdLogin.getVisibility() == View.VISIBLE) {
                    isWaitingForConfirmation = true; // 进入确认模式
                    String numberForTTS = value.replace("", " ").trim();
                    speak("您的账号是 " + numberForTTS + "，确定吗？");
                    return; // 等待用户回答，直接返回
                } else {
                    etPwd.requestFocus();
                }
            }
            // 确认
            else if ("CONFIRM".equals(type)) {
                if (isWaitingForConfirmation) {
                    isWaitingForConfirmation = false;
                    if (btCode.isEnabled()) {
                        btCode.performClick();
                    } else {
                        speak("验证码还在倒计时中");
                        return;
                    }
                }
            }
            // 否认
            else if ("DENY".equals(type)) {
                if (isWaitingForConfirmation) {
                    isWaitingForConfirmation = false;
                    etAccNum.setText("");
                    etAccNum.requestFocus();
                }
            }
            // 填入密码
            else if ("FILL_PASSWORD".equals(type)) {
                etPwd.setText(value);
                etPwd.setSelection(value.length());
                if (tvPwdLogin.getVisibility() == View.VISIBLE) {
                    switchToPasswordMode();
                }
                new Handler().postDelayed(() -> btLogin.performClick(), 1500);
            }
            // 切换/点击操作
            else {
                switch (type) {
                    case "ASK_ACCOUNT":
                        etAccNum.requestFocus();
                        break;
                    case "ASK_PASSWORD":
                        isWaitingForConfirmation = false;
                        etPwd.requestFocus();
                        if (tvPwdLogin.getVisibility() == View.VISIBLE) switchToPasswordMode();
                        break;
                    case "CODE_LOGIN":
                        isWaitingForConfirmation = false;
                        if (tvForget.getVisibility() == View.VISIBLE) tvForget.performClick();
                        break;
                    case "LOGIN":
                        btLogin.performClick();
                        break;
                    case "REGISTER":
                        stopVoiceServices();
                        startActivity(new Intent(this, RegisterActivity.class));
                        break;
                }
            }

            // 播报回复
            if (!reply.isEmpty()) {
                speak(reply);
            } else {
                // 如果没有语音回复，重新进入唤醒待机
                if (wakeUpManager != null) wakeUpManager.start();
            }

        } catch (JSONException e) {
            e.printStackTrace();
            speak("我没太听懂，请您再说一遍好吗？");
        }
    }

    /**
     * 【核心修改方法】语音播报
     * 只有 isVoiceActive 为 true 时才真正发声
     */
    private void speak(String text) {
        if (!isVoiceActive) {
            Log.d(TAG, "speak: 语音助手未激活，拦截播报 -> " + text);
            return;
        }

        if (tts != null) {
            if (tts.isSpeaking()) {
                tts.stop();
            }
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageID");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "messageID");
            } else {
                HashMap<String, String> map = new HashMap<>();
                map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageID");
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, map);
            }
            // 播报也算一次交互，刷新休眠时间
            refreshSleepTimer();
        }
    }

    private void initView() {
        btLogin = findViewById(R.id.btLogin);
        btRegister = findViewById(R.id.btRegister);
        btCode = findViewById(R.id.btCode);
        tvForget = findViewById(R.id.tvForget);
        tvPwd = findViewById(R.id.tvPwd);
        tvCode = findViewById(R.id.tvCode);
        tvPwdLogin = findViewById(R.id.tvPwdLogin);
        tvTitle = findViewById(R.id.tvTitle);
        etAccNum = findViewById(R.id.etAccNum);
        etPwd = findViewById(R.id.etPwd);
        etCode = findViewById(R.id.etCode);
        llCode = findViewById(R.id.llCode);

        btLogin.setOnClickListener(this);
        btRegister.setOnClickListener(this);
        btCode.setOnClickListener(this);
        tvForget.setOnClickListener(this);
        tvPwdLogin.setOnClickListener(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
        }
    }

    private void initCountDownTimer() {
        countDownTimer = new CountDownTimer(TOTAL_TIME, INTERVAL) {
            @Override
            public void onTick(long millisUntilFinished) {
                btCode.setText(millisUntilFinished / 1000 + "秒后重发");
            }

            @Override
            public void onFinish() {
                btCode.setText("获取验证码");
                btCode.setEnabled(true);
                btCode.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            }
        };
    }

    @Override
    public void onClick(View v) {
        // 重置按钮文字颜色
        btLogin.setTextColor(color2);
        btRegister.setTextColor(color2);

        // 手动点击会打断当前的确认流程
        isWaitingForConfirmation = false;

        // 注意：手动点击按钮不会将 isVoiceActive 设为 true
        // 因此手动点击导致的 Toast 会显示，但 speak() 会被拦截（静音），符合需求

        if (v.getId() == R.id.btLogin) {
            btLogin.setBackgroundResource(R.drawable.rounded_but);
            btLogin.setTextColor(color);
            btRegister.setBackgroundResource(R.drawable.rounded_but2);

            phone = etAccNum.getText().toString().trim();
            password = etPwd.getText().toString().trim();
            code = etCode.getText().toString().trim();

            // 密码登录模式
            if (tvPwdLogin.getVisibility() == View.GONE && tvForget.getVisibility() == View.VISIBLE) {
                if (phone.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "账号或密码不能为空!", Toast.LENGTH_SHORT).show();
                    speak("请先输入账号和密码");
                    return;
                }
                map.clear();
                map.put("phone", phone);
                map.put("password", password);
                OkhttpUtils.initRequest(4, "GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", handler);
            }
            // 验证码登录模式
            else if (tvForget.getVisibility() == View.GONE && tvPwdLogin.getVisibility() == View.VISIBLE) {
                if (phone.isEmpty() || code.isEmpty()) {
                    Toast.makeText(this, "手机号或验证码不能为空!", Toast.LENGTH_SHORT).show();
                    speak("请填写手机号和验证码");
                    return;
                }
                map2.clear();
                map2.put("phone", phone);
                map2.put("code", code);
                OkhttpUtils.initRequest(5, "GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", handler);
            }
        }
        else if (v.getId() == R.id.btCode) {
            phone = etAccNum.getText().toString().trim();
            if (phone.isEmpty()) {
                Toast.makeText(this, "请输入手机号!", Toast.LENGTH_SHORT).show();
                speak("请先输入手机号");
            } else if (!isValidPhone(phone)) {
                Toast.makeText(this, "请输入正确的手机号!", Toast.LENGTH_SHORT).show();
                speak("手机号格式好像不对");
            } else {
                map.clear();
                map.put("phone", phone);
                new Handler().postDelayed(() -> {
                    OkhttpUtils.initRequest(2, "POST", OkhttpUtils.URL + OkhttpUtils.CAPTCHA, OkhttpUtils.toBody(map), "", handler);
                    startCountDown();
                }, 1500);
            }
        }
        else if (v.getId() == R.id.btRegister) {
            stopVoiceServices();
            startActivity(new Intent(this, RegisterActivity.class));
        }
        else if (v.getId() == R.id.tvForget) {
            switchToCodeMode();
        }
        else if (v.getId() == R.id.tvPwdLogin) {
            switchToPasswordMode();
        }
    }

    private void switchToCodeMode() {
        btLogin.setBackgroundResource(R.drawable.rounded_but);
        btLogin.setTextColor(color);
        tvPwd.setVisibility(View.GONE);
        etPwd.setVisibility(View.GONE);
        tvForget.setVisibility(View.GONE);
        tvPwdLogin.setVisibility(View.VISIBLE);
        tvCode.setVisibility(View.VISIBLE);
        tvTitle.setText("手机号登录");
        llCode.setVisibility(View.VISIBLE);
        etAccNum.requestFocus();
    }

    private void switchToPasswordMode() {
        btLogin.setBackgroundResource(R.drawable.rounded_but);
        btLogin.setTextColor(color);
        tvPwdLogin.setVisibility(View.GONE);
        tvCode.setVisibility(View.GONE);
        llCode.setVisibility(View.GONE);
        tvPwd.setVisibility(View.VISIBLE);
        tvTitle.setText("账号密码登录");
        etPwd.setVisibility(View.VISIBLE);
        tvForget.setVisibility(View.VISIBLE);
        etPwd.requestFocus();
    }

    private void startCountDown() {
        btCode.setEnabled(false);
        btCode.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        countDownTimer.start();
    }

    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("1[0-9]{10}");
    }

    // 网络请求回调 Handler
    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            String json = msg.obj.toString();
            switch (msg.what) {
                case 1:
                case 3:
                    LoginData loginData = OkhttpUtils.toData(json, LoginData.class);
                    if (loginData.getCode() == 200) {
                        Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                        speak("登录成功，欢迎回来！"); // 如果未唤醒，这句不读
                        SharedPreferences sp = getSharedPreferences("phone", MODE_PRIVATE);
                        sp.edit().putString("phone", phone).apply();
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    } else if (loginData.getCode() == 403) {
                        Toast.makeText(LoginActivity.this, "用户认证信息错误" + "\n" + "请确认是否为视障端", Toast.LENGTH_SHORT).show();
                        speak("用户认证信息错误!请确认是否为视障端");
                    }
                    break;
                case 2:
                    captchaData = OkhttpUtils.toData(json, CaptchaData.class);
                    if (captchaData != null && captchaData.getCode() == 200) {
                        Toast.makeText(LoginActivity.this, "验证码：" + captchaData.getSmsCode(), Toast.LENGTH_SHORT).show();
                        speak("验证码已收到，正在为您自动填入并登录");
                        map.put("code", captchaData.getSmsCode());
                        new Handler().postDelayed(() -> {
                            if (!isFinishing()) {
                                etCode.setText(captchaData.getSmsCode());
                                etCode.setSelection(captchaData.getSmsCode().length());
                                btLogin.performClick();
                            }
                        }, 1500);
                    } else {
                        Toast.makeText(LoginActivity.this, "发送失败", Toast.LENGTH_SHORT).show();
                        speak("验证码发送失败了");
                        countDownTimer.cancel();
                        btCode.setText("获取验证码");
                        btCode.setEnabled(true);
                    }
                    break;
                case 4:
                    try {
                        JSONObject jsonObject = new JSONObject(json);
                        int code = jsonObject.getInt("code");
                        String data = jsonObject.getString("data");
                        JSONObject dataObj = new JSONObject(data);
                        String phone1 = dataObj.getString("phone");
                        if (code == 200 && phone.equals(phone1)) {
                            map.put("userType", "0");
                            OkhttpUtils.initRequest(1, "POST", OkhttpUtils.URL + OkhttpUtils.LOGIN, OkhttpUtils.toBody(map), "", handler);
                            Log.d(TAG, "handleMessage: " + map);
                        } else {
                            Toast.makeText(LoginActivity.this, "账号或密码错误", Toast.LENGTH_SHORT).show();
                            speak("账号或密码不对，请检查一下");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(LoginActivity.this, "登录失败", Toast.LENGTH_SHORT).show();
                        speak("服务器开小差了");
                    }
                    break;
                case 5:
                    userData = OkhttpUtils.toData(json, UserData.class);
                    if ("[]".equals(json)) {
                        Toast.makeText(LoginActivity.this, "账号未注册", Toast.LENGTH_SHORT).show();
                        speak("这个账号还没注册呢");
                    } else if (userData.getCode() == 200) {
                        map.put("userType", "0");
                        OkhttpUtils.initRequest(3, "POST", OkhttpUtils.URL + OkhttpUtils.SMSLOGIN, OkhttpUtils.toBody(map2), "", handler);
                        Log.d(TAG, "handleMessage: " + map);
                    } else {
                        Toast.makeText(LoginActivity.this, "验证码错误", Toast.LENGTH_SHORT).show();
                        speak("验证码不对哦");
                    }
                    break;
            }
            return false;
        }
    });

    /**
     * 停止所有语音相关服务（TTS、ASR、WakeUp）
     * 在页面跳转或退出时调用
     */
    private void stopVoiceServices() {
        // 1. 停止 TTS 播报
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
        }

        // 2. 停止唤醒检测
        if (wakeUpManager != null) {
            wakeUpManager.stop();
        }

        // 3. 停止语音识别
        if (asrManager != null) {
            asrManager.stop(); // 或者是 cancel()，取决于你的管理器实现
        }

        // 4. 重置语音激活状态，防止后台回调触发
        isVoiceActive = false;

        // 5. 移除自动休眠的计时器，防止在别的页面触发 Toast
        if (sleepHandler != null) {
            sleepHandler.removeCallbacksAndMessages(null);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        // 恢复时启动唤醒，但不改变当前的 isVoiceActive 状态
        if (wakeUpManager != null) wakeUpManager.start();
        isVoiceActive = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wakeUpManager != null) wakeUpManager.stop();
        if (asrManager != null) asrManager.stop();
        stopVoiceServices();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeUpManager != null) wakeUpManager.release();
        if (asrManager != null) asrManager.release();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (sleepHandler != null) {
            sleepHandler.removeCallbacksAndMessages(null);
        }
    }
}