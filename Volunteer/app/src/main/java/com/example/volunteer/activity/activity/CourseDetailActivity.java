package com.example.volunteer.activity.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.volunteer.MainActivity;
import com.example.volunteer.R;
import com.example.volunteer.utils.WordReader;

import java.io.InputStream;

public class CourseDetailActivity extends AppCompatActivity {

    private static final String TAG = "CourseDetailActivity";
    private ImageView ivBack;
    private ProgressBar progressBar;
    private ScrollView scrollView;
    private TextView tvContent;
    private String courseId;
    private String  courseTitle;
    private TextView tvTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course_detail);

        initViews();

        // 获取传递的课程信息
        courseId = getIntent().getStringExtra("course_id");
        courseTitle = getIntent().getStringExtra("course_title");
        tvTitle.setText(courseTitle);

        // 加载Word文档
        loadWordDocument(courseId);
    }

    private void initViews() {
        progressBar = findViewById(R.id.progressBar);
        scrollView = findViewById(R.id.scrollView);
        tvContent = findViewById(R.id.tvContent);
        ivBack = findViewById(R.id.ivBack);
        tvTitle = findViewById(R.id.tvTitle);
        ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //清空所有Activity，回到MainActivity并切换到TrainingFragment
                Intent intent = new Intent(CourseDetailActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("target_fragment", "training"); // 传递要显示的Fragment
                startActivity(intent);
                finish();
            }
        });

        // 设置TextView可滚动
        tvContent.setMovementMethod(new ScrollingMovementMethod());
    }

    private void loadWordDocument(String courseId) {
        showLoading(true);

        new Thread(() -> {
            try {
                // 根据课程ID获取对应的Word文件名
                String fileName = getFileName(courseId);
                // 从assets读取Word文件
                InputStream inputStream = getAssets().open(fileName);

                // 提取文字
                String content = WordReader.readWordFromAssets(inputStream);

                // 显示文字
                runOnUiThread(() -> {
                    showLoading(false);
                    tvContent.setText(content);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    showLoading(false);
                    tvContent.setText("加载失败: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * 根据课程ID返回对应的Word文件名
     */
    private String getFileName(String courseId) {
        if (courseId == null) return "firstaid.docx";

        switch (courseId) {
            case "1":
            case "course_001":
                return "firstaid.docx";      // 急救知识大全
            case "2":
            case "course_002":
                return "sign_language.docx"; // 基础手语入门
            case "3":
            case "course_003":
                return "communication.docx"; // 视障人士沟通技巧
            case "4":
            case "course_004":
                return "shudao.docx";
            default:
                return null;
        }
    }

    private void showLoading(boolean show) {
        runOnUiThread(() -> {
            if (show) {
                progressBar.setVisibility(View.VISIBLE);
                scrollView.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.GONE);
                scrollView.setVisibility(View.VISIBLE);
            }
        });
    }
}