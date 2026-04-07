package com.example.myapplication.activity.login;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.myapplication.R;
import com.example.myapplication.data.CaptchaData;
import com.example.myapplication.data.RegisterData;
import com.example.myapplication.manage.QwenManager;
import com.example.myapplication.manage.SimpleAsrManager;
import com.example.myapplication.manage.SimpleWakeUpManager;
import com.example.myapplication.utils.OkhttpUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;

public class RegisterActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "RegisterActivity";

    // UI 组件
    private EditText etAccNum, etPwd, etCode;
    private Button btLogin, btRegister, btCode;

    // 数据存储
    private HashMap<String, Object> map = new HashMap<>();
    private boolean isClicked = false;
    private String phone;
    private String password;
    private String code;
    private CaptchaData captchaData = new CaptchaData();

    // 通知渠道
    private static final String CHANNEL_ID = "captcha_channel";
    private static final String CHANNEL_NAME = "验证码通知";

    // 倒计时相关
    private CountDownTimer countDownTimer;
    private static final long TOTAL_TIME = 60000;
    private static final long INTERVAL = 1000;

    // --- 语音与AI相关 ---
    private SimpleWakeUpManager wakeUpManager;
    private SimpleAsrManager asrManager;
    private TextToSpeech tts;
    private QwenManager qwenManager;

    // 【核心控制变量】：语音助手是否处于激活状态
    private boolean isVoiceActive = false;

    // 逻辑标记：是否正在等待用户确认
    private boolean isWaitingForConfirmation = false;

    // 当前等待确认的类型：手机号、密码、账号和密码、全部信息
    private String confirmationType = "";

    // 确认流程的下一步动作
    private static final String CONFIRM_PHONE = "手机号";
    private static final String CONFIRM_PASSWORD = "密码";
    private static final String CONFIRM_PHONE_AND_PASSWORD = "账号和密码";
    private static final String CONFIRM_ALL = "全部信息";

    // --- 自动休眠机制 ---
    private Handler sleepHandler = new Handler();
    private static final long SLEEP_DELAY = 60000; // 60秒无交互自动休眠

    private Runnable sleepRunnable = new Runnable() {
        @Override
        public void run() {
            if (isVoiceActive) {
                isVoiceActive = false;
                Log.d(TAG, "语音助手自动休眠");
                Toast.makeText(RegisterActivity.this, "语音助手已休眠", Toast.LENGTH_SHORT).show();
                if (wakeUpManager != null) wakeUpManager.start();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // 初始化大模型管理器
        qwenManager = new QwenManager();

        initView();
        initNotificationChannel();
        initCountDownTimer();
        initTTS();
        initVoiceAssistant();
    }

    /**
     * 刷新休眠倒计时
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
            }

            @Override
            public void onDone(String utteranceId) {
                runOnUiThread(() -> {
                    if (!isVoiceActive) return;

                    Log.d(TAG, "TTS播报完毕，准备开启识别...");
                    new Handler().postDelayed(() -> {
                        if (!isFinishing() && isVoiceActive) {
                            if (asrManager != null) {
                                Toast.makeText(RegisterActivity.this, "请说话...", Toast.LENGTH_SHORT).show();
                                asrManager.start();
                            }
                        }
                    }, 500);
                });
            }

            @Override
            public void onError(String utteranceId) {
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
                refreshSleepTimer();
                runOnUiThread(() -> processCommand(text));
            }

            @Override
            public void onError(String error) {
                Log.e("Voice", "识别错误: " + error);
                runOnUiThread(() -> {
                    if (wakeUpManager != null) wakeUpManager.start();
                });
            }
        });

        // --- 2. 初始化唤醒 (WakeUp) ---
        wakeUpManager = new SimpleWakeUpManager(this, new SimpleWakeUpManager.WakeUpListener() {
            @Override
            public void onSuccess(String word) {
                Log.d("Voice", "唤醒成功: " + word);
                Toast.makeText(RegisterActivity.this, "我在！", Toast.LENGTH_SHORT).show();

                // 唤醒成功，激活语音权限
                isVoiceActive = true;

                wakeUpManager.stop();

                // 重置逻辑状态
                isWaitingForConfirmation = false;
                confirmationType = "";

                // 启动自动休眠倒计时
                refreshSleepTimer();

                // 【核心修改】检查当前输入框状态，决定下一步操作
                checkCurrentStateAndProceed();
            }

            @Override
            public void onError(String errorMsg) {
                Log.e("Voice", "唤醒失败: " + errorMsg);
            }
        });

        // 启动唤醒检测
        wakeUpManager.start();
    }

    /**
     * 【核心新增方法】检查当前输入框状态，决定唤醒后的操作
     */
    private void checkCurrentStateAndProceed() {
        String currentPhone = etAccNum.getText().toString().trim();
        String currentPassword = etPwd.getText().toString().trim();
        String currentCode = etCode.getText().toString().trim();

        boolean hasPhone = !currentPhone.isEmpty();
        boolean hasPassword = !currentPassword.isEmpty();
        boolean hasCode = !currentCode.isEmpty();

        Log.d(TAG, "唤醒后检查状态 - 手机号:" + hasPhone + ", 密码:" + hasPassword + ", 验证码:" + hasCode);

        if (hasPhone && hasPassword && hasCode) {
            // 情况1：全部已填写，确认后直接注册
            isWaitingForConfirmation = true;
            confirmationType = CONFIRM_ALL;
            String phoneForTTS = currentPhone.replace("", " ").trim();
            speak("欢迎回来！检测到您已填写完整信息。手机号是 " + phoneForTTS +
                    "，密码和验证码也已填写。确认无误后我将为您完成注册，请问信息正确吗？");
        }
        else if (hasPhone && hasPassword) {
            // 情况2：有账号和密码，确认后获取验证码
            isWaitingForConfirmation = true;
            confirmationType = CONFIRM_PHONE_AND_PASSWORD;
            String phoneForTTS = currentPhone.replace("", " ").trim();
            speak("欢迎回来！检测到您已填写手机号 " + phoneForTTS +
                    " 和密码。确认无误后我将为您获取验证码，请问信息正确吗？");
        }
        else if (hasPhone) {
            // 情况3：只有账号，确认后输入密码
            isWaitingForConfirmation = true;
            confirmationType = CONFIRM_PHONE;
            String phoneForTTS = currentPhone.replace("", " ").trim();
            speak("欢迎回来！检测到您已填写手机号 " + phoneForTTS +
                    "。请确认手机号是否正确？");
        }
        else {
            // 情况4：什么都没填，从头开始引导
            speak("您好，我是小黎，这是注册页面。请告诉我您的手机号，我可以帮您完成注册。");
        }
    }

    private void processCommand(String text) {
        if (!isVoiceActive) return;

        if (text == null || text.trim().isEmpty()) return;

        String cleanText = text.replaceAll("[。，？!.,?!]", "").trim();
        Log.d("Voice", "用户说: " + text);

        // 获取当前界面状态上下文
        boolean isPhoneEmpty = etAccNum.getText().toString().trim().isEmpty();
        boolean isPwdEmpty = etPwd.getText().toString().trim().isEmpty();
        boolean isCodeEmpty = etCode.getText().toString().trim().isEmpty();

        StringBuilder stateBuilder = new StringBuilder();
        stateBuilder.append(String.format(" [当前界面状态: 注册页面, 手机号%s, 密码%s, 验证码%s, 验证码按钮%s]",
                isPhoneEmpty ? "为空" : "已填(" + etAccNum.getText().toString().trim() + ")",
                isPwdEmpty ? "为空" : "已填",
                isCodeEmpty ? "为空" : "已填",
                btCode.isEnabled() ? "可点击" : "倒计时中"));

        if (isWaitingForConfirmation) {
            stateBuilder.append("[特别注意: 当前正在等待用户确认").append(confirmationType).append("]");
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
        if (!isVoiceActive) return;

        refreshSleepTimer();

        Log.d("Voice", "AI回复: " + jsonStr);
        try {
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.replace("```json", "").replace("```", "");
            }
            jsonStr = jsonStr.trim();

            JSONObject result = new JSONObject(jsonStr);
            String type = result.optString("type");
            String value = result.optString("value");
            String reply = result.optString("reply");

            // --- 状态与动作处理 ---

            switch (type) {
                case "FILL_PHONE":
                case "FILL_ACCOUNT":
                    // 【修复】验证并处理手机号
                    String phoneValue = value.replaceAll("[^0-9]", "");
                    if (phoneValue.length() > 11) {
                        phoneValue = phoneValue.substring(0, 11);
                    }
                    if (phoneValue.isEmpty() || !isValidPhone(phoneValue)) {
                        speak("手机号格式不正确，请重新告诉我11位手机号");
                        return;
                    }
                    etAccNum.setText(phoneValue);
                    // 【修复】使用 EditText 实际文本长度
                    etAccNum.setSelection(etAccNum.getText().length());

                    isWaitingForConfirmation = true;
                    confirmationType = CONFIRM_PHONE;
                    String numberForTTS = phoneValue.replace("", " ").trim();
                    speak("您的手机号是 " + numberForTTS + "，确定吗？");
                    return;

                case "FILL_PASSWORD":
                    // 填入密码
                    if (value == null || value.isEmpty()) {
                        speak("密码不能为空，请重新告诉我");
                        return;
                    }
                    etPwd.setText(value);
                    // 【修复】使用 EditText 实际文本长度
                    etPwd.setSelection(etPwd.getText().length());

                    isWaitingForConfirmation = true;
                    confirmationType = CONFIRM_PASSWORD;
                    speak("密码已输入，确定吗？");
                    return;

                case "FILL_CODE":
                    // 填入验证码
                    if (value == null || value.isEmpty()) {
                        speak("验证码不能为空");
                        return;
                    }
                    etCode.setText(value);
                    // 【修复】使用 EditText 实际文本长度
                    etCode.setSelection(etCode.getText().length());
                    speak("验证码已填入，需要我帮您完成注册吗？");
                    return;

                case "CONFIRM":
                    handleConfirmation();
                    return;

                case "DENY":
                    handleDenial();
                    return;

                case "GET_CODE":
                    if (btCode.isEnabled()) {
                        phone = etAccNum.getText().toString().trim();
                        if (phone.isEmpty()) {
                            speak("请先告诉我您的手机号");
                            return;
                        }
                        speak("好的，正在为您获取验证码");
                        new Handler().postDelayed(() -> btCode.performClick(), 500);
                    } else {
                        speak("验证码还在倒计时中，请稍等");
                    }
                    return;

                case "REGISTER":
                case "DO_REGISTER":
                    phone = etAccNum.getText().toString().trim();
                    password = etPwd.getText().toString().trim();
                    code = etCode.getText().toString().trim();

                    if (phone.isEmpty()) {
                        speak("请先告诉我您的手机号");
                        return;
                    }
                    if (password.isEmpty()) {
                        speak("请告诉我您想设置的密码");
                        return;
                    }
                    if (code.isEmpty()) {
                        speak("请先获取并输入验证码");
                        return;
                    }
                    speak("好的，正在为您注册");
                    new Handler().postDelayed(() -> btRegister.performClick(), 500);
                    return;

                case "GO_LOGIN":
                    speak("好的，正在跳转到登录页面");
                    new Handler().postDelayed(() -> {
                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    }, 1500);
                    return;

                case "ASK_PHONE":
                    etAccNum.requestFocus();
                    break;

                case "ASK_PASSWORD":
                    etPwd.requestFocus();
                    break;

                case "ASK_CODE":
                    etCode.requestFocus();
                    break;

                default:
                    break;
            }

            // 播报回复
            if (reply != null && !reply.isEmpty()) {
                speak(reply);
            } else {
                if (wakeUpManager != null) wakeUpManager.start();
            }

        } catch (JSONException e) {
            e.printStackTrace();
            speak("我没太听懂，请您再说一遍好吗？");
        }
    }

    /**
     * 【新增方法】提取并验证有效的手机号
     * 只保留数字，并验证长度为11位
     */
    private String extractValidPhone(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        // 只保留数字
        String digitsOnly = input.replaceAll("[^0-9]", "");

        // 如果超过11位，只取前11位
        if (digitsOnly.length() > 11) {
            digitsOnly = digitsOnly.substring(0, 11);
        }

        // 验证是否为有效的手机号格式（11位，以1开头）
        if (digitsOnly.length() == 11 && digitsOnly.startsWith("1")) {
            return digitsOnly;
        }

        // 如果不是11位但有数字，尝试返回（可能用户还没说完）
        if (digitsOnly.length() > 0) {
            Log.w(TAG, "手机号长度不正确: " + digitsOnly.length() + " 位");
            // 如果长度接近11位，可能是多识别了，截取
            if (digitsOnly.length() >= 11) {
                return digitsOnly.substring(0, 11);
            }
        }

        return digitsOnly.isEmpty() ? null : digitsOnly;
    }

    /**
     * 【新增方法】安全设置光标位置
     */
    private void safeSetSelection(EditText editText) {
        if (editText != null && editText.getText() != null) {
            int length = editText.getText().length();
            if (length >= 0) {
                editText.setSelection(length);
            }
        }
    }

    /**
     * 【核心新增方法】处理确认逻辑
     */
    private void handleConfirmation() {
        if (!isWaitingForConfirmation) {
            speak("好的");
            return;
        }

        isWaitingForConfirmation = false;

        switch (confirmationType) {
            case CONFIRM_PHONE:
                // 确认手机号后，询问密码
                confirmationType = "";
                speak("好的，手机号已确认。请告诉我您想设置的密码。");
                etPwd.requestFocus();
                break;

            case CONFIRM_PASSWORD:
                // 确认密码后，获取验证码
                confirmationType = "";
                if (btCode.isEnabled()) {
                    speak("好的，密码已确认。正在为您获取验证码。");
                    new Handler().postDelayed(() -> btCode.performClick(), 1000);
                } else {
                    speak("验证码还在倒计时中，请稍等");
                }
                break;

            case CONFIRM_PHONE_AND_PASSWORD:
                // 确认账号和密码后，获取验证码
                confirmationType = "";
                if (btCode.isEnabled()) {
                    speak("好的，信息已确认。正在为您获取验证码。");
                    new Handler().postDelayed(() -> btCode.performClick(), 1000);
                } else {
                    speak("验证码还在倒计时中，请稍等");
                }
                break;

            case CONFIRM_ALL:
                // 确认全部信息后，执行注册
                confirmationType = "";
                speak("好的，正在为您完成注册。");
                new Handler().postDelayed(() -> btRegister.performClick(), 1000);
                break;

            default:
                confirmationType = "";
                speak("好的");
                break;
        }
    }

    /**
     * 【核心新增方法】处理否认逻辑
     */
    private void handleDenial() {
        if (!isWaitingForConfirmation) {
            speak("好的，请告诉我需要修改什么");
            return;
        }

        isWaitingForConfirmation = false;

        switch (confirmationType) {
            case CONFIRM_PHONE:
                // 否认手机号，清空重新输入
                etAccNum.setText("");
                etAccNum.requestFocus();
                confirmationType = "";
                speak("好的，请重新告诉我您的手机号");
                break;

            case CONFIRM_PASSWORD:
                // 否认密码，清空重新输入
                etPwd.setText("");
                etPwd.requestFocus();
                confirmationType = "";
                speak("好的，请重新告诉我您想设置的密码");
                break;

            case CONFIRM_PHONE_AND_PASSWORD:
                // 否认账号和密码，询问要修改哪个
                confirmationType = "";
                speak("好的，请问您要修改手机号还是密码？或者说'都要改'");
                // 设置一个临时状态等待用户回答
                isWaitingForConfirmation = true;
                confirmationType = "选择修改项";
                break;

            case CONFIRM_ALL:
                // 否认全部信息，询问要修改哪个
                confirmationType = "";
                speak("好的，请问您要修改哪项信息？手机号、密码还是验证码？");
                isWaitingForConfirmation = true;
                confirmationType = "选择修改项";
                break;

            case "选择修改项":
                // 用户说了不对但没说改什么，引导重新开始
                confirmationType = "";
                etAccNum.setText("");
                etPwd.setText("");
                etCode.setText("");
                speak("好的，让我们重新开始。请告诉我您的手机号");
                break;

            default:
                confirmationType = "";
                speak("好的，请告诉我需要修改什么");
                break;
        }
    }

    /**
     * 语音播报 - 只有 isVoiceActive 为 true 时才发声
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
            refreshSleepTimer();
        }
    }

    private void initView() {
        etAccNum = findViewById(R.id.etAccNum);
        etPwd = findViewById(R.id.etPwd);
        etCode = findViewById(R.id.etCode);
        btCode = findViewById(R.id.btCode);
        btLogin = findViewById(R.id.btLogin);
        btRegister = findViewById(R.id.btRegister);

        btLogin.setOnClickListener(this);
        btCode.setOnClickListener(this);
        btRegister.setOnClickListener(this);

        // 检查录音权限
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

    private void startCountDown() {
        btCode.setEnabled(false);
        btCode.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        countDownTimer.start();
    }

    @Override
    public void onClick(View v) {
        // 手动点击会打断当前的确认流程
        isWaitingForConfirmation = false;
        confirmationType = "";

        if (v.getId() == R.id.btLogin) {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
        } else if (v.getId() == R.id.btRegister) {
            phone = etAccNum.getText().toString().trim();
            password = etPwd.getText().toString().trim();
            code = etCode.getText().toString().trim();

            if (phone != null && !phone.equals("") && password != null && !password.equals("") && code != null && !code.equals("")) {
                map.put("phone", phone);
                map.put("password", password);
                map.put("smsCode", code);
                if (isClicked) {
                    if (captchaData != null && code.equals(captchaData.getSmsCode())) {
                        OkhttpUtils.initRequest(3, "GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", handler);
                    } else {
                        Toast.makeText(RegisterActivity.this, "验证码错误!", Toast.LENGTH_SHORT).show();
                        speak("验证码不对哦，请重新输入");
                    }
                } else {
                    Toast.makeText(RegisterActivity.this, "请先获取验证码!", Toast.LENGTH_SHORT).show();
                    speak("请先获取验证码");
                }
            } else {
                Toast.makeText(RegisterActivity.this, "手机号、密码、验证码不能为空!", Toast.LENGTH_SHORT).show();
                speak("手机号、密码和验证码都需要填写哦");
            }
        } else if (v.getId() == R.id.btCode) {
            phone = etAccNum.getText().toString().trim();
            if (phone.equals("")) {
                Toast.makeText(RegisterActivity.this, "请输入手机号!", Toast.LENGTH_SHORT).show();
                speak("请先输入手机号");
            } else {
                if (!isValidPhone(phone)) {
                    Toast.makeText(RegisterActivity.this, "请输入正确的手机号!", Toast.LENGTH_SHORT).show();
                    speak("手机号格式好像不对");
                    return;
                }
                isClicked = true;
                map.put("phone", phone);
                new Handler().postDelayed(() -> OkhttpUtils.initRequest(1, "POST", OkhttpUtils.URL + OkhttpUtils.CAPTCHANOREG, OkhttpUtils.toBody(map), "", handler), 1500);
                startCountDown();
            }
        }
    }

    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("1[0-9]{10}");
    }

    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            String json = msg.obj.toString();
            Log.d("handleMessage: json = ", json);
            switch (msg.what) {
                case 1:
                    captchaData = OkhttpUtils.toData(json, CaptchaData.class);
                    if (captchaData != null && captchaData.getCode() == 200) {
                        Log.d("mytag", "验证码获取成功：" + captchaData.getSmsCode());
                        Toast.makeText(RegisterActivity.this, "验证码为：" + captchaData.getSmsCode(), Toast.LENGTH_SHORT).show();
                        // 语音播报验证码
                        String codeForTTS = captchaData.getSmsCode().replace("", " ").trim();
                        speak("验证码已发送，您的验证码是 " + codeForTTS + "，正在为您自动填入并注册");
                        // 自动填入验证码并注册
                        new Handler().postDelayed(() -> {
                            if (!isFinishing()) {
                                etCode.setText(captchaData.getSmsCode());
                                etCode.setSelection(captchaData.getSmsCode().length());
                                // 延迟执行注册
                                new Handler().postDelayed(() -> btRegister.performClick(), 1500);
                            }
                        }, 2000);
                    } else if (captchaData.getCode() == 1003){
                        speak("该账号已注册!");
                        Toast.makeText(RegisterActivity.this, "账号已注册，跳转到登录页面", Toast.LENGTH_SHORT).show();
                        new Handler().postDelayed(() -> {
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                        }, 2000);
                    } else {
                        Toast.makeText(RegisterActivity.this, "验证码发送失败", Toast.LENGTH_SHORT).show();
                        speak("验证码发送失败了");
                        countDownTimer.cancel();
                        btCode.setText("获取验证码");
                        btCode.setEnabled(true);
                        btCode.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
                    }
                    break;
                case 2:
                    RegisterData registerData = OkhttpUtils.toData(json, RegisterData.class);
                    if (registerData.getCode() == 200) {
                        Toast.makeText(RegisterActivity.this, "注册成功!", Toast.LENGTH_SHORT).show();
                        speak("注册成功！正在为您跳转到登录页面");
                        new Handler().postDelayed(() -> {
                            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                            startActivity(intent);
                            finish();
                        }, 2000);
                    }
                    break;
                case 3:
                    try {
                        JSONObject jsonObject = new JSONObject(json);
                        int code = jsonObject.getInt("code");
                        Log.d(TAG, "handleMessage:case3 " + jsonObject);
                        if (code == 404) {
                            map.put("userType", "0");
                            OkhttpUtils.initRequest(2, "POST", OkhttpUtils.URL + OkhttpUtils.REGISTER, OkhttpUtils.toBody(map), "", handler);
                            Log.d(TAG, "handleMessage:case3 " + map);
                        } else {
                            Toast.makeText(RegisterActivity.this, "该账号已注册!", Toast.LENGTH_SHORT).show();
                            speak("这个手机号已经注册过了，您可以直接去登录");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
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
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
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