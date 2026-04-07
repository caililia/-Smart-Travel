package com.example.volunteer.activity.login;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.example.volunteer.R;
import com.example.volunteer.data.CaptchaData;
import com.example.volunteer.data.RegisterData;
import com.example.volunteer.utils.OkhttpUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class RegisterActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "Register";
    private EditText etAccNum, etPwd, etCode;
    private Button btLogin, btRegister, btCode;
    private HashMap<String, Object> map = new HashMap<>();
    private boolean isClicked = false;
    private String phone;
    private String password;
    private String code;
    private CaptchaData captchaData = new CaptchaData();
    private static final String CHANNEL_ID = "captcha_channel";
    private static final String CHANNEL_NAME = "验证码通知";

    // 倒计时相关变量
    private CountDownTimer countDownTimer;
    private static final long TOTAL_TIME = 60000; // 总计时60秒
    private static final long INTERVAL = 1000; // 间隔1秒

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        initView();
        initNotificationChannel(); // 初始化通知渠道
        initCountDownTimer(); // 初始化倒计时器
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 避免内存泄漏，在Activity销毁时取消倒计时
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    private void initView() {
        etAccNum = (EditText) findViewById(R.id.etAccNum);
        etPwd = (EditText) findViewById(R.id.etPwd);
        etCode = (EditText) findViewById(R.id.etCode);
        btCode = (Button) findViewById(R.id.btCode);
        btLogin = (Button) findViewById(R.id.btLogin);
        btRegister = (Button) findViewById(R.id.btRegister);

        btLogin.setOnClickListener(this);
        btCode.setOnClickListener(this);
        btRegister.setOnClickListener(this);
    }

    // 初始化倒计时器
    private void initCountDownTimer() {
        countDownTimer = new CountDownTimer(TOTAL_TIME, INTERVAL) {
            @Override
            public void onTick(long millisUntilFinished) {
                // 更新按钮文本，显示剩余秒数
                btCode.setText(millisUntilFinished / 1000 + "秒后重发");
            }

            @Override
            public void onFinish() {
                // 倒计时结束，恢复按钮状态
                btCode.setText("获取验证码");
                btCode.setEnabled(true);
                btCode.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light)); // 恢复背景色
            }
        };
    }

    // 开始倒计时
    private void startCountDown() {
        btCode.setEnabled(false);
        btCode.setBackgroundColor(getResources().getColor(android.R.color.darker_gray)); // 灰色表示不可用
        countDownTimer.start();
    }

    @Override
    public void onClick(View v) {
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
                    }
                } else {
                    Toast.makeText(RegisterActivity.this, "请先获取验证码!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(RegisterActivity.this, "手机号、密码、验证码不能为空!", Toast.LENGTH_SHORT).show();
            }
        } else if (v.getId() == R.id.btCode) {
            phone = etAccNum.getText().toString().trim();
            if (phone.equals("")) {
                Toast.makeText(RegisterActivity.this, "请输入手机号!", Toast.LENGTH_SHORT).show();
            } else {
                // 检查手机号格式（简单验证）
                if (!isValidPhone(phone)) {
                    Toast.makeText(RegisterActivity.this, "请输入正确的手机号!", Toast.LENGTH_SHORT).show();
                    return;
                }
                isClicked = true;
                map.put("phone", phone);
                new Handler().postDelayed(() -> {
                    OkhttpUtils.initRequest(1, "POST", OkhttpUtils.URL + OkhttpUtils.CAPTCHANOREG, OkhttpUtils.toBody(map), "", handler);
                }, 2000);
                startCountDown();
            }
        }
    }

    // 简单的手机号格式验证
    private boolean isValidPhone(String phone) {
        // 简单的手机号验证：1开头，11位数字
        return phone != null && phone.matches("1[0-9]{10}");
    }

    // 初始化通知渠道（Android O+）
    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "验证码通知", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
    }

    private void showNotification(String smsCode) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.sigh)
                .setContentTitle("注册验证码")
                .setContentText("验证码：" + smsCode + "（5分钟内有效）")
                .setAutoCancel(true)
                .build();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(0, notification);
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
                        Toast.makeText(RegisterActivity.this, "验证码为：" + captchaData.getSmsCode(), Toast.LENGTH_SHORT).show();
                        Log.d("mytag", "验证码获取成功：" + captchaData.getSmsCode());
                        showNotification(captchaData.getSmsCode());
                    } else if (captchaData.getCode() == 1003){
                        Toast.makeText(RegisterActivity.this, "账号已注册，跳转到登录页面", Toast.LENGTH_SHORT).show();
                        new Handler().postDelayed(() -> {
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                        }, 2000);
                    }  else {
                        Toast.makeText(RegisterActivity.this, "验证码发送失败", Toast.LENGTH_SHORT).show();
                        // 如果发送失败，恢复按钮状态
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
                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish(); // 注册成功后关闭当前页面
                    }
                    break;

                case 3:
                    try {
                        JSONObject jsonObject = new JSONObject(json);
                        int code = jsonObject.getInt("code");
                        Log.d(TAG, "handleMessage: " + jsonObject);
                        if (code == 404) {
                            map.put("userType", "1");
                            OkhttpUtils.initRequest(2, "POST", OkhttpUtils.URL + OkhttpUtils.REGISTER, OkhttpUtils.toBody(map), "", handler);
                            Log.d(TAG, "handleMessage:case3 " + map);
                        } else {
                            Toast.makeText(RegisterActivity.this, "该账号已注册!", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    break;
            }
            return false;
        }
    });
}