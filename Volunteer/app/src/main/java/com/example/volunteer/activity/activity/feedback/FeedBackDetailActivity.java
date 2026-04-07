package com.example.volunteer.activity.activity.feedback;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.volunteer.R;
import com.example.volunteer.data.FeedData;
import com.example.volunteer.entity.Feedback;
import com.example.volunteer.utils.OkhttpUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class FeedBackDetailActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "FeedBackDetailActivity";
    private ListView lvFeed;
    private ImageView ivFeedBack;
    private List<Feedback> feedbackList = new ArrayList<>();
    private String phone = null;
    private FeedData feedData;
    private long lastClickTime = 0;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView tvFeedbackCount;
    private TextView tvEmptyHint;
    private LinearLayout llEmptyView;
    private TextView tvRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed_back_detail);

        // 初始化Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // 初始化视图
        initViews();

        // 获取用户信息
        SharedPreferences sharedPreferences = this.getSharedPreferences("phone", MODE_PRIVATE);
        phone = sharedPreferences.getString("phone", null);

        if (phone == null || phone.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 加载数据
        loadFeedbackData();

        // 设置刷新点击事件
        tvRefresh.setOnClickListener(v -> loadFeedbackData());

        // 设置列表项双击事件
        lvFeed.setOnItemClickListener((parent, view, position, id) -> {
            long currentClickTime = System.currentTimeMillis();
            if (currentClickTime - lastClickTime < 300) {
                if (position < feedbackList.size()) {
                    showFeedbackDetailDialog(feedbackList.get(position));
                }
            }
            lastClickTime = currentClickTime;
        });
    }

    private void initViews() {
        lvFeed = findViewById(R.id.lvFeed);
        tvFeedbackCount = findViewById(R.id.tv_feedback_count);
        tvEmptyHint = findViewById(R.id.tv_empty_hint);
        llEmptyView = findViewById(R.id.ll_empty_view);
        tvRefresh = findViewById(R.id.tv_refresh);
    }

    private void loadFeedbackData() {
        // 显示加载状态
        showLoading();

        // 网络请求获取反馈数据
        OkhttpUtils.request("GET", OkhttpUtils.URL + OkhttpUtils.getFeedBack + "?phone=" + phone, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "onFailure: " + e);
                mainHandler.post(() -> {
                    hideLoading();
                    Toast.makeText(FeedBackDetailActivity.this,
                            "网络请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "onResponse: " + json);

                    try {
                        feedData = OkhttpUtils.toData(json, FeedData.class);

                        mainHandler.post(() -> {
                            hideLoading();

                            if (feedData != null && feedData.getData() != null && !feedData.getData().isEmpty()) {
                                // 清空原有数据
                                feedbackList.clear();

                                // 添加所有反馈数据
                                for (int i = 0; i < feedData.getData().size(); i++) {
                                    FeedData.DataBean item = feedData.getData().get(i);
                                    feedbackList.add(new Feedback(
                                            item.getTitle(),
                                            item.getContent(),
                                            (String) item.getCreateTime()
                                    ));
                                }

                                // 更新统计数量
                                tvFeedbackCount.setText(String.valueOf(feedbackList.size()));
                                tvEmptyHint.setVisibility(View.GONE);
                                llEmptyView.setVisibility(View.GONE);
                                lvFeed.setVisibility(View.VISIBLE);

                                // 设置适配器
                                FeedbackAdapter adapter = new FeedbackAdapter(feedbackList);
                                lvFeed.setAdapter(adapter);
                            } else {
                                // 显示空状态
                                tvFeedbackCount.setText("0");
                                tvEmptyHint.setVisibility(View.VISIBLE);
                                llEmptyView.setVisibility(View.VISIBLE);
                                lvFeed.setVisibility(View.GONE);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "解析数据失败", e);
                        mainHandler.post(() -> {
                            hideLoading();
                            Toast.makeText(FeedBackDetailActivity.this,
                                    "数据解析失败", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    Log.e(TAG, "请求失败: " + response.code());
                    mainHandler.post(() -> {
                        hideLoading();
                        Toast.makeText(FeedBackDetailActivity.this,
                                "请求失败: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void showLoading() {
        // 可以显示进度条，这里简单处理
        tvRefresh.setText("加载中...");
        tvRefresh.setEnabled(false);
    }

    private void hideLoading() {
        tvRefresh.setText("刷新");
        tvRefresh.setEnabled(true);
    }

    private void showFeedbackDetailDialog(Feedback feedback) {
        if (feedback == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(feedback.getTitle())
                .setMessage(feedback.getContent() + "\n\n" + feedback.getTime())
                .setPositiveButton("关闭", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    @Override
    public void onClick(View view) {

    }

    class FeedbackAdapter extends ArrayAdapter<Feedback> {

        public FeedbackAdapter(List<Feedback> feedbackList) {
            super(FeedBackDetailActivity.this, R.layout.feedback_item, feedbackList);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.feedback_item, parent, false);
            }

            Feedback feedback = getItem(position);

            TextView feedbackTitle = convertView.findViewById(R.id.feedbackTitle);
            TextView feedbackContent = convertView.findViewById(R.id.feedbackContent);
            TextView feedbackTime = convertView.findViewById(R.id.feedbackTime);

            if (feedback != null) {
                if (feedback.getTitle() != null){
                    feedbackTitle.setText(feedback.getTitle());
                } else {
                    feedbackTitle.setText("");
                }
                if (feedback.getContent() != null){
                    feedbackContent.setText(feedback.getContent());
                } else {
                    feedbackContent.setText("");
                }
                if (feedback.getTime() != null){
                    feedbackTime.setText(feedback.getTime());
                } else {
                    feedbackTime.setText("");
                }
            }

            return convertView;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
}