package com.example.volunteer.activity.login;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.example.volunteer.MainActivity;
import com.example.volunteer.R;
import com.example.volunteer.data.CaptchaData;
import com.example.volunteer.data.LoginData;
import com.example.volunteer.data.UserData;
import com.example.volunteer.utils.OkhttpUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class LoginActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "";
    private Button btLogin, btRegister, btCode;
    private TextView tvPwd, tvCode, tvForget, tvPwdLogin, tvTitle;
    private EditText etAccNum, etPwd, etCode;
    private LinearLayout llCode;
    private int color = Color.parseColor("#FFFFFF");
    private int color2 = Color.parseColor("#000000");
    private HashMap<String, Object> map = new HashMap<>();
    private HashMap<String, Object> map2 = new HashMap<>();
    private String phone;
    private String password;
    private String code;
    private boolean isClicked = false;
    private CaptchaData captchaData = new CaptchaData();
    private UserData userData;
    // 倒计时相关变量
    private CountDownTimer countDownTimer;
    private static final long TOTAL_TIME = 60000; // 总计时60秒
    private static final long INTERVAL = 1000; // 间隔1秒
    private static final String CHANNEL_ID = "captcha_channel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        initView();
        initNotificationChannel(); // 初始化通知渠道
        initCountDownTimer(); // 初始化倒计时器
    }

    private void initView() {
        btLogin = (Button) findViewById(R.id.btLogin);
        btRegister = (Button) findViewById(R.id.btRegister);
        btCode = (Button) findViewById(R.id.btCode);
        tvForget = (TextView) findViewById(R.id.tvForget);
        tvPwd = (TextView) findViewById(R.id.tvPwd);
        tvCode = (TextView) findViewById(R.id.tvCode);
        tvPwdLogin = (TextView) findViewById(R.id.tvPwdLogin);
        tvTitle = (TextView) findViewById(R.id.tvTitle);
        etAccNum = (EditText) findViewById(R.id.etAccNum);
        etPwd = (EditText) findViewById(R.id.etPwd);
        etCode = (EditText) findViewById(R.id.etCode);
        llCode = (LinearLayout) findViewById(R.id.llCode);

        btLogin.setOnClickListener(this);
        btRegister.setOnClickListener(this);
        btCode.setOnClickListener(this);
        tvForget.setOnClickListener(this);
        tvPwdLogin.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        btLogin.setTextColor(color2);
        btRegister.setTextColor(color2);

        if (v.getId() == R.id.btLogin) {
            btLogin.setBackgroundResource(R.drawable.rounded_but);
            btLogin.setTextColor(color);
            btRegister.setBackgroundResource(R.drawable.rounded_but2);
            /*tvForget.setVisibility(View.VISIBLE);
            tvPwd.setVisibility(View.VISIBLE);
            etPwd.setVisibility(View.VISIBLE);*/
            /*获取手机号，密码等信息*/
            phone = etAccNum.getText().toString().trim();
            password = etPwd.getText().toString().trim();
            code = etCode.getText().toString().trim();

            if (tvPwdLogin.getVisibility() == View.GONE && tvForget.getVisibility() == View.VISIBLE) {
                if (phone != null && !phone.isEmpty() && password != null && !password.isEmpty()) {
                    map.put("phone", phone);
                    map.put("password", password);
                    OkhttpUtils.initRequest(4, "GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", handler);
                } else {
                    Toast.makeText(LoginActivity.this, "账号密码不能为空!", Toast.LENGTH_SHORT).show();
                }
            } else if ((tvForget.getVisibility() == View.GONE && tvPwdLogin.getVisibility() == View.VISIBLE)) {
                if (phone != null && !phone.equals("") && code != null && !code.equals("")) {
                    //判断验证码的按钮是否被点击，如果点击过，进行下列操作
                    map2.put("phone", phone);
                    map2.put("code", code);
                    OkhttpUtils.initRequest(5, "GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", handler);
                } else {
                    Toast.makeText(LoginActivity.this, "账号验证码不能为空!", Toast.LENGTH_SHORT).show();
                }
            }

        } else if (v.getId() == R.id.btRegister) {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        } else if (v.getId() == R.id.tvForget) {
            btLogin.setBackgroundResource(R.drawable.rounded_but);
            btLogin.setTextColor(color);
            tvPwd.setVisibility(View.GONE);
            etPwd.setVisibility(View.GONE);
            tvForget.setVisibility(View.GONE);
            tvPwdLogin.setVisibility(View.VISIBLE);
            tvCode.setVisibility(View.VISIBLE);
            tvTitle.setText("手机号登录");
            llCode.setVisibility(View.VISIBLE);
        } else if (v.getId() == R.id.tvPwdLogin) {
            btLogin.setBackgroundResource(R.drawable.rounded_but); // Assuming you have a default button background
            btLogin.setTextColor(color); // Assuming you have a default text color
            tvPwdLogin.setVisibility(View.GONE);
            tvCode.setVisibility(View.GONE);
            llCode.setVisibility(View.GONE);
            tvPwd.setVisibility(View.VISIBLE);
            tvTitle.setText("账号密码登录");
            etPwd.setVisibility(View.VISIBLE);
            tvForget.setVisibility(View.VISIBLE);
        } else if (v.getId() == R.id.btCode) {
            isClicked = true;
            /*获取验证码*/
            btLogin.setBackgroundResource(R.drawable.rounded_but);
            btLogin.setTextColor(color);
            tvPwdLogin.setVisibility(View.VISIBLE);
            tvCode.setVisibility(View.VISIBLE);
            tvTitle.setText("手机号登录");
            llCode.setVisibility(View.VISIBLE);
            etCode.setVisibility(View.VISIBLE);

            phone = etAccNum.getText().toString().trim();
            if (phone.equals("")) {
                Toast.makeText(LoginActivity.this, "请输入手机号!", Toast.LENGTH_SHORT).show();
            } else {
                if (!isValidPhone(phone)) {
                    Toast.makeText(LoginActivity.this, "请输入正确的手机号!", Toast.LENGTH_SHORT).show();
                    return;
                }
                isClicked = true;
                map.put("phone", phone);
                OkhttpUtils.initRequest(2, "POST", OkhttpUtils.URL + OkhttpUtils.CAPTCHA, OkhttpUtils.toBody(map), "", handler);
                startCountDown();

            }
        }
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

    // 初始化通知渠道（Android O+）
    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "验证码通知", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
    }

    // 显示验证码通知
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

    // 简单的手机号格式验证
    private boolean isValidPhone(String phone) {
        // 简单的手机号验证：1开头，11位数字
        return phone != null && phone.matches("1[0-9]{10}");
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            String json = msg.obj.toString();
            Log.d("handleMessage: json = ", json);
            switch (msg.what) {
                /*账号密码登录*/
                case 1:

                case 3:
                    LoginData loginData = OkhttpUtils.toData(json, LoginData.class);
                    if (loginData.getCode() == 200) {
                        Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                        SharedPreferences sharedPreferences = getSharedPreferences("phone", MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.clear();
                        editor.putString("phone", phone);
                        editor.apply();
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                    } else if (loginData.getCode() == 403) {
                        Toast.makeText(LoginActivity.this, "用户认证信息错误" + "\n" + "请确认是否为志愿者端", Toast.LENGTH_SHORT).show();
                    }
                    break;
                /*通过手机号获取验证码*/
                case 2:
                    captchaData = OkhttpUtils.toData(json, CaptchaData.class);
                    if (captchaData != null && captchaData.getCode() == 200) {
                        Log.d("mytag", "验证码获取成功：" + captchaData.getSmsCode());
                        // 使用新的验证码处理流程
                        showNotification(captchaData.getSmsCode());
                    } else {
                        Toast.makeText(LoginActivity.this, "验证码发送失败", Toast.LENGTH_SHORT).show();
                        // 如果发送失败，恢复按钮状态
                        countDownTimer.cancel();
                        btCode.setText("获取验证码");
                        btCode.setEnabled(true);
                        btCode.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
                    }
                    break;
                case 4:
                    try {
                        JSONObject jsonObject = new JSONObject(json);
                        Log.d(TAG, "jsonObject: " + jsonObject);
                        int code = jsonObject.getInt("code");
                        String data = jsonObject.getString("data");
                        JSONObject dataJsonObject = new JSONObject(data);
                        String phone1 = dataJsonObject.getString("phone");
                        if (code == 200) {
                            if (phone.equals(phone1)) {
                                map.put("userType", "1");
                                OkhttpUtils.initRequest(1, "POST", OkhttpUtils.URL + OkhttpUtils.LOGIN, OkhttpUtils.toBody(map), "", handler);
                            }
                        } else {
                            Toast.makeText(LoginActivity.this, "账号或密码错误!", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    break;

                case 5:
                    userData = OkhttpUtils.toData(json, UserData.class);
                    if (json.equals("[]")) {
                        Toast.makeText(LoginActivity.this, "该账号尚未注册!", Toast.LENGTH_SHORT).show();
                    } else if (isClicked) {
                        if (code.equals(captchaData.getSmsCode())) {
                            map2.put("data", code);
                            map.put("userType", "1");
                            OkhttpUtils.initRequest(3, "POST", OkhttpUtils.URL + OkhttpUtils.SMSLOGIN, OkhttpUtils.toBody(map2), "", handler);
                        } else {
                            Toast.makeText(LoginActivity.this, "验证码错误!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "请先获取验证码!", Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
            return false;
        }
    });

}