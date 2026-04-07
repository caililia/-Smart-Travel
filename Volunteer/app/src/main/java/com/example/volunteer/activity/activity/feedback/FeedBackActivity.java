package com.example.volunteer.activity.activity.feedback;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


import com.example.volunteer.R;
import com.example.volunteer.activity.login.LoginActivity;
import com.example.volunteer.data.MsgData;
import com.example.volunteer.data.UserData;
import com.example.volunteer.utils.OkhttpUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class FeedBackActivity extends AppCompatActivity implements View.OnClickListener{

    private static final String TAG = "";
    private EditText etTitle;
    private EditText etTextArea;
    private Button btSubFeed;
    private ImageView ivFeedBack;
    private HashMap<String, Object> map = new HashMap<>();
    private String phone = null;
    private Handler handler = new Handler();
    private TextView tvMyFeed;
    private SharedPreferences sharedPreferences;
    private String userId;
    private UserData userData;
    private MsgData msgData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed_back);
        initData();

        // 初始化视图
        etTitle = findViewById(R.id.etTitle);
        etTextArea = findViewById(R.id.etTextArea);
        btSubFeed = findViewById(R.id.btSubFeed);
        ivFeedBack = findViewById(R.id.ivFeedBack);
        tvMyFeed = findViewById(R.id.tvMyFeed);
        SharedPreferences sharedPreferences = this.getSharedPreferences("phone", MODE_PRIVATE);
        phone = sharedPreferences.getString("phone", null);
        ivFeedBack.setOnClickListener(this);
        tvMyFeed.setOnClickListener(this);

        btSubFeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String title = etTitle.getText().toString().trim();
                String TextArea = etTextArea.getText().toString().trim();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                // 获取当前日期
                Date currentDate = new Date();

                map.put("title", title);
                map.put("content", TextArea);
                map.put("userId", userId);
                map.put("time", "");
                OkhttpUtils.request("POST", OkhttpUtils.URL + OkhttpUtils.FeedBack, OkhttpUtils.toBody(map), "", new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "onFailure: " + e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String json = response.body().string();
                        Log.d(TAG, "onResponse: " + json);
                        msgData = OkhttpUtils.toData(json, MsgData.class);
                        if (msgData.getCode() == 200) {
                            // 反馈成功
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(FeedBackActivity.this, "反馈成功", Toast.LENGTH_SHORT).show();
                                }
                            });
                            handler.postDelayed(() ->{
                                finish();
                            }, 3000);

                        } else {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(FeedBackActivity.this, "请输入完整的反馈内容", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    private void initData() {
        sharedPreferences = getSharedPreferences("phone", MODE_PRIVATE);
        phone =  sharedPreferences.getString("phone", null);
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "登录信息已过期，请重新登录", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(FeedBackActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        fetchUserInfo();
    }

    private void fetchUserInfo() {
        OkhttpUtils.request("GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取用户信息失败", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "用户信息响应: " + json);
                    userData = OkhttpUtils.toData(json, UserData.class);
                    if (userData != null && userData.getData() != null) {
                        userId = String.valueOf(userData.getData().getUserId());
                    }
                }
            }
        });
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.ivFeedBack){
            this.finish();
        }
        else if (v.getId() == R.id.tvMyFeed) {
            Intent intent = new Intent(FeedBackActivity.this, FeedBackDetailActivity.class);
            startActivity(intent);
        }
    }
}
