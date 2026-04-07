package com.example.volunteer.activity.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.volunteer.R;
import com.example.volunteer.adapter.HelpRecordAdapter;
import com.example.volunteer.data.VoiceCall;
import com.example.volunteer.data.VoiceCallResponse;
import com.example.volunteer.utils.OkhttpUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class HelpingActivity extends AppCompatActivity {

    private static final String TAG = "HelpingActivity";

    private ImageView ivBack;
    private TextView tvTotalHelp;
    private TextView tvPendingCount;
    private TextView tvCompletedCount;
    private TextView tvRecordCount;
    private RecyclerView rvHelpRecords;
    private ProgressBar progressBar;
    private View llEmpty;

    private HelpRecordAdapter adapter;
    private List<VoiceCall> records = new ArrayList<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 当前helperId，可以从Intent或SharedPreferences获取
    private long userId = 10000005;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_helping);

        initViews();
        loadHelpRecords();
    }

    private void initViews() {
        try {
            ivBack = findViewById(R.id.ivBack);
            tvTotalHelp = findViewById(R.id.tv_total_help);
            tvPendingCount = findViewById(R.id.tv_pending_count);
            tvCompletedCount = findViewById(R.id.tv_completed_count);
            tvRecordCount = findViewById(R.id.tv_record_count);
            rvHelpRecords = findViewById(R.id.rv_help_records);
            progressBar = findViewById(R.id.progressBar);
            llEmpty = findViewById(R.id.ll_empty);

            ivBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finish();
                }
            });

            // 检查RecyclerView是否找到
            if (rvHelpRecords == null) {
                Log.e(TAG, "RecyclerView is null! 请检查布局文件");
                return;
            }

            // 设置RecyclerView
            rvHelpRecords.setLayoutManager(new LinearLayoutManager(this));

            adapter = new HelpRecordAdapter(
                    this,                           // Context
                    records,                        // List<VoiceCall>
                    new HelpRecordAdapter.OnItemClickListener() {  // 点击监听器
                        @Override
                        public void onItemClick(VoiceCall record) {
                            // 处理点击事件，比如查看详情
                            Toast.makeText(HelpingActivity.this,
                                    "点击了房间: " + record.getRoomId(), Toast.LENGTH_SHORT).show();
                        }
                    }
            );
            rvHelpRecords.setAdapter(adapter);

            Log.d(TAG, "initViews: 初始化成功");

        } catch (Exception e) {
            Log.e(TAG, "initViews error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadHelpRecords() {
        showLoading(true);

        String url = OkhttpUtils.URL + OkhttpUtils.RoomListByHelperId + userId;

        OkhttpUtils.request("GET", url, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "请求失败: " + e.getMessage());
                mainHandler.post(() -> {
                    showLoading(false);
                    Toast.makeText(HelpingActivity.this,
                            "网络请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "响应数据: " + json);

                    try {
                        VoiceCallResponse voiceCallResponse = OkhttpUtils.toData(json, VoiceCallResponse.class);
                        Log.d(TAG, "解析结果 - code: " + voiceCallResponse.getCode() +
                                ", total: " + voiceCallResponse.getTotal() +
                                ", data size: " + (voiceCallResponse.getData() != null ?
                                voiceCallResponse.getData().size() : 0));

                        mainHandler.post(() -> {
                            showLoading(false);
                            if (voiceCallResponse != null && voiceCallResponse.getCode() == 200) {
                                updateUI(voiceCallResponse);
                            } else {
                                showEmptyState();
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "JSON解析失败: " + e.getMessage());
                        mainHandler.post(() -> {
                            showEmptyState();
                        });
                    }
                } else {
                    Log.e(TAG, "响应失败: " + response.code());
                    mainHandler.post(() -> {
                        showEmptyState();
                    });
                }
            }
        });
    }

    private void updateUI(VoiceCallResponse response) {
        // 检查adapter是否已初始化
        if (adapter == null) {
            Log.e(TAG, "updateUI: adapter is null");
            return;
        }

        records.clear();

        if (response.getData() != null && !response.getData().isEmpty()) {
            records.addAll(response.getData());

            // 计算时长（如果endTime不为null）
            for (VoiceCall record : records) {
                if (record.getEndTime() != null && !record.getEndTime().isEmpty()) {
                    String duration = calculateDuration(record.getCreateTime(), record.getEndTime());
                    record.setDurationTime(duration);
                } else {
                    record.setDurationTime("进行中");
                }
            }

            // 更新统计数字
            if (tvTotalHelp != null) {
                tvTotalHelp.setText(String.valueOf(response.getTotal()));
            }
            if (tvRecordCount != null) {
                tvRecordCount.setText("共" + response.getTotal() + "条记录");
            }

            // 统计进行中和已完成的数量
            int pendingCount = 0;
            int completedCount = 0;
            for (VoiceCall record : records) {
                if ("pending".equals(record.getCallStatus())) {
                    pendingCount++;
                } else if ("completed".equals(record.getCallStatus())) {
                    completedCount++;
                }
            }

            if (tvPendingCount != null) {
                tvPendingCount.setText(String.valueOf(pendingCount));
            }
            if (tvCompletedCount != null) {
                tvCompletedCount.setText(String.valueOf(completedCount));
            }

            // 更新列表
            adapter.notifyDataSetChanged();

            // 显示/隐藏空状态
            if (llEmpty != null && rvHelpRecords != null) {
                if (records.isEmpty()) {
                    llEmpty.setVisibility(View.VISIBLE);
                    rvHelpRecords.setVisibility(View.GONE);
                } else {
                    llEmpty.setVisibility(View.GONE);
                    rvHelpRecords.setVisibility(View.VISIBLE);
                }
            }
        } else {
            // 无数据
            showEmptyState();
        }
    }

    private void showEmptyState() {
        if (tvTotalHelp != null) tvTotalHelp.setText("0");
        if (tvPendingCount != null) tvPendingCount.setText("0");
        if (tvCompletedCount != null) tvCompletedCount.setText("0");
        if (tvRecordCount != null) tvRecordCount.setText("共0条记录");

        if (llEmpty != null && rvHelpRecords != null) {
            llEmpty.setVisibility(View.VISIBLE);
            rvHelpRecords.setVisibility(View.GONE);
        }

        // 清空适配器数据
        if (adapter != null) {
            records.clear();
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * 计算通话时长
     */
    private String calculateDuration(String startTime, String endTime) {
        if (startTime == null || endTime == null) {
            return "--";
        }

        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            long start = sdf.parse(startTime).getTime();
            long end = sdf.parse(endTime).getTime();
            long duration = (end - start) / 1000; // 秒

            if (duration < 0) return "--";

            if (duration < 60) {
                return duration + "秒";
            } else if (duration < 3600) {
                long minutes = duration / 60;
                long seconds = duration % 60;
                if (seconds > 0) {
                    return minutes + "分" + seconds + "秒";
                }
                return minutes + "分钟";
            } else {
                long hours = duration / 3600;
                long minutes = (duration % 3600) / 60;
                return hours + "小时" + minutes + "分钟";
            }
        } catch (Exception e) {
            Log.e(TAG, "计算时长失败: " + e.getMessage());
            return "--";
        }
    }

    private void showLoading(boolean show) {
        runOnUiThread(() -> {
            if (progressBar != null) {
                if (show) {
                    progressBar.setVisibility(View.VISIBLE);
                    if (rvHelpRecords != null) rvHelpRecords.setVisibility(View.GONE);
                    if (llEmpty != null) llEmpty.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
    }
}