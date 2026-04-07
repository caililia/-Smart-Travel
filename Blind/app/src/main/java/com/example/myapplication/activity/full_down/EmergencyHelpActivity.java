package com.example.myapplication.activity.full_down;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.activity.EnvironmentActivity;
import com.example.myapplication.activity.VideoCallActivity;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EmergencyHelpActivity extends AppCompatActivity {
    private TextView tvFallInfo;
    private Button btnCallEmergency;
    private Button btnSendSms;
    private Button btnBack;

    private float impactForce;
    private float angle;
    private long timestamp;

    private static final int PERMISSION_REQUEST_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_help);

        // 获取传入数据
        impactForce = getIntent().getFloatExtra("impact_force", 0);
        angle = getIntent().getFloatExtra("angle", 0);
        timestamp = getIntent().getLongExtra("timestamp", System.currentTimeMillis());

        initViews();
        initData();
        setupListeners();
    }

    private void initViews() {
        tvFallInfo = findViewById(R.id.tv_fall_info);
        btnCallEmergency = findViewById(R.id.btn_call_emergency);
        btnSendSms = findViewById(R.id.btn_send_sms);
        btnBack = findViewById(R.id.btn_back);


        btnSendSms.setOnClickListener(v -> sendEmergencySms());
        btnCallEmergency.setOnClickListener(v -> goVideoCall());
        btnBack.setOnClickListener(v -> finish());
        autoExecuteEmergencyActions();
    }

    private void autoExecuteEmergencyActions() {
        Handler handler = new Handler();
        // 先发送短信
        handler.postDelayed(this::sendEmergencySms, 1500);

        // 视频通话（延迟2.5秒）
        handler.postDelayed(this::goVideoCall, 2500);
    }

    private void initData() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timeStr = sdf.format(new Date(timestamp));

        String info = "跌倒时间: " + timeStr + "\n";
        info += "撞击力度: " + String.format("%.1f", impactForce) + " m/s²\n";
        info += "身体倾斜: " + String.format("%.1f", angle) + "°";
        tvFallInfo.setText(info);
    }

    private void goVideoCall(){
        Intent intent = new Intent(this, VideoCallActivity.class);
        intent.putExtra("fullDown", "1");
        startActivity(intent);
    }

    private void setupListeners() {
        btnCallEmergency.setOnClickListener(v -> {
            goVideoCall();
        });

        btnSendSms.setOnClickListener(v -> {
            checkSmsPermission();
        });

        btnBack.setOnClickListener(v -> {
            finish();
        });
    }

    /**
     * 拨打电话
     */
    private void makePhoneCall() {
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:")); // 紧急号码
        try {
            startActivity(intent);
        } catch (SecurityException e) {
            //Toast.makeText(this, "拨打电话失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检查短信权限并发送短信
     */
    private void checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    PERMISSION_REQUEST_CODE + 1);
        } else {
            sendEmergencySms();
        }
    }

    /**
     * 发送紧急短信
     */
    private void sendEmergencySms() {
        String message = buildEmergencyMessage();
        try {
            SmsManager smsManager = SmsManager.getDefault();
            // 这里可以设置多个紧急联系人
            smsManager.sendTextMessage("", null, message, null, null);
            //Toast.makeText(this, "紧急短信已发送", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            /*Toast.makeText(this, "短信发送失败: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();*/
        }
    }

    /**
     * 构建紧急消息
     */
    private String buildEmergencyMessage() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String timeStr = sdf.format(new Date(timestamp));

        StringBuilder sb = new StringBuilder();
        sb.append("【慧行助盲紧急求助】\n");
        sb.append("检测到用户可能跌倒！\n");
        sb.append("时间: ").append(timeStr).append("\n");
        return sb.toString();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions,
                                           @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //makePhoneCall();
            } else {
                //Toast.makeText(this, "需要通话权限", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == PERMISSION_REQUEST_CODE + 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendEmergencySms();
            } else {
                //Toast.makeText(this, "需要短信权限", Toast.LENGTH_SHORT).show();
            }
        }
    }
}