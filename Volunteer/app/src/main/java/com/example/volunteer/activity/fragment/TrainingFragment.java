package com.example.volunteer.activity.fragment;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteer.R;
import com.example.volunteer.activity.activity.CourseDetailActivity;
import com.example.volunteer.activity.activity.MediaVideoActivity;
import com.example.volunteer.adapter.CertificateAdapter;
import com.example.volunteer.adapter.ExamCertAdapter;
import com.example.volunteer.adapter.HotCourseAdapter;
import com.example.volunteer.adapter.LearningPathAdapter;
import com.example.volunteer.adapter.RequiredCourseAdapter;
import com.example.volunteer.data.train.Certificate;
import com.example.volunteer.data.train.Course;
import com.example.volunteer.data.train.ExamCert;
import com.example.volunteer.data.train.LearningPath;
import com.example.volunteer.data.train.SkillPractice;

import java.util.ArrayList;
import java.util.List;

public class TrainingFragment extends Fragment {

    // 视图组件
    private Toolbar toolbar;
    private ImageView ivSearch;
    private TextView tvProgressMore;
    private TextView tvCompletedCount;
    private TextView tvLearningCount;
    private TextView tvTotalCount;
    private ProgressBar progressBar;
    private TextView tvProgressPercent;
    private RecyclerView rvHotCourses;
    private RecyclerView rvRequiredCourses;
    private RecyclerView rvLearningPaths;

    // 数据列表
    private List<Course> hotCourses;
    private List<Course> requiredCourses;
    private List<LearningPath> learningPaths;
    private List<ExamCert> examCerts;

    // 适配器
    private HotCourseAdapter hotCourseAdapter;
    private RequiredCourseAdapter requiredCourseAdapter;
    private LearningPathAdapter learningPathAdapter;
    private ExamCertAdapter examCertAdapter;
    private CertificateAdapter certificateAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_training, container, false);
        initViews(view);
        initData();
        setupToolbar();
        //setupCategories();
        setupHotCourses();
        setupLearningPaths();
        setupExamCerts();
        updateProgressData();
        setupClickListeners();
        return view;
    }

    private void initViews(View view) {
        tvProgressMore = view.findViewById(R.id.tv_progress_more);
        tvCompletedCount = view.findViewById(R.id.tv_completed_count);
        tvLearningCount = view.findViewById(R.id.tv_learning_count);
        tvTotalCount = view.findViewById(R.id.tv_total_count);
        progressBar = view.findViewById(R.id.progress_bar);
        tvProgressPercent = view.findViewById(R.id.tv_progress_percent);
       /* categoryContainer = view.findViewById(R.id.category_container);*/
        rvHotCourses = view.findViewById(R.id.rv_hot_courses);
        rvLearningPaths = view.findViewById(R.id.rv_learning_paths);

        // 设置RecyclerView的布局管理器
        setupRecyclerViews();
    }

    private void setupRecyclerViews() {
        // 热门课程 - 横向滚动
        LinearLayoutManager hotLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        rvHotCourses.setLayoutManager(hotLayoutManager);

        // 学习路径 - 横向滚动
        LinearLayoutManager pathLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        rvLearningPaths.setLayoutManager(pathLayoutManager);
    }

    private void initData() {
        // 初始化热门课程数据
        hotCourses = new ArrayList<>();
        hotCourses.add(new Course(
                "1", "急救知识大全", "45分钟", "初级",
                "#2E7D32", "#E6F7E6", R.drawable.book1, null, null
        ));
        hotCourses.add(new Course(
                "2", "志愿者基础培训", "60分钟", "初级",
                "#2E7D32", "#E6F7E6", R.drawable.book2, null, null
        ));
        hotCourses.add(new Course(
                "3", "语言沟通技巧", "90分钟", "中级",
                "#F97316", "#FFF3E0", R.drawable.book3, null, null
        ));
        hotCourses.add(new Course(
                "4", "心理疏导技巧", "50分钟", "初级",
                "#2E7D32", "#E6F7E6", R.drawable.book4, null, null
        ));

        // 初始化必修课程数据
        requiredCourses = new ArrayList<>();

        // 初始化学习路径数据
        learningPaths = new ArrayList<>();
        learningPaths.add(new LearningPath(
                "1", "家庭急救", "关键时刻能救命的实用技巧",
                30, 2, R.drawable.video1
        ));
        learningPaths.add(new LearningPath(
                "2", "心理疏导", "为志愿者提供心理疏导",
                15, 2, R.drawable.video2
        ));
    }

    private void setupToolbar() {
        if (toolbar != null) {
            toolbar.setTitle("技能培训");
            toolbar.setTitleTextColor(Color.parseColor("#0F172A"));

            // 设置搜索图标点击事件
            ivSearch.setOnClickListener(v -> {
                Toast.makeText(getContext(), "搜索功能开发中", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupHotCourses() {
        hotCourseAdapter = new HotCourseAdapter(getContext(), hotCourses, course -> {
            //Toast.makeText(getActivity(), "点击课程: " + course.getTitle(), Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(getActivity(), CourseDetailActivity.class);
            intent.putExtra("course_id", course.getId());
            intent.putExtra("course_title", course.getTitle());
            startActivity(intent);
        });
        rvHotCourses.setAdapter(hotCourseAdapter);
    }

    private void setupLearningPaths() {
        learningPathAdapter = new LearningPathAdapter(getContext(), learningPaths, path -> {
            Intent intent = new Intent(getContext(), MediaVideoActivity.class);
            if (path.getId().equals("1")){
                intent.putExtra("filename", "303959");
            } else {
                intent.putExtra("filename", "23826");
            }
            startActivity(intent);
        });
        rvLearningPaths.setAdapter(learningPathAdapter);
    }

    private void setupExamCerts() {
        examCertAdapter = new ExamCertAdapter(getContext(), examCerts, exam -> {
            Toast.makeText(getContext(), "点击考试: " + exam.getTitle(), Toast.LENGTH_SHORT).show();
            if (exam.isPassed()) {
                // 查看证书详情
            } else {
                // 开始考试
            }
        });
    }

    private void updateProgressData() {
        // 模拟进度数据
        int completed = 2;
        int learning = 4;
        int total = 8;
        int progressPercent = (int) ((float) completed / total * 100);

        tvCompletedCount.setText(String.valueOf(completed));
        tvLearningCount.setText(String.valueOf(learning));
        tvTotalCount.setText(String.valueOf(total));
        progressBar.setProgress(progressPercent);
        tvProgressPercent.setText(progressPercent + "%");
    }

    private void setupClickListeners() {
        /*// 查看详情
        tvProgressMore.setOnClickListener(v -> {
            Toast.makeText(getContext(), "查看学习进度详情", Toast.LENGTH_SHORT).show();
        });

        // 查看更多课程
        tvAllCourses.setOnClickListener(v -> {
            Toast.makeText(getContext(), "查看全部热门课程", Toast.LENGTH_SHORT).show();
        });

        // 全部必修
        tvRequiredCourses.setOnClickListener(v -> {
            Toast.makeText(getContext(), "查看全部必修课程", Toast.LENGTH_SHORT).show();
        });

        // 更多实训
        tvSkillPractice.setOnClickListener(v -> {
            Toast.makeText(getContext(), "查看全部技能实训", Toast.LENGTH_SHORT).show();
        });

        // 查看全部学习路径
        tvLearningPath.setOnClickListener(v -> {
            Toast.makeText(getContext(), "查看全部学习路径", Toast.LENGTH_SHORT).show();
        });*/
    }
}