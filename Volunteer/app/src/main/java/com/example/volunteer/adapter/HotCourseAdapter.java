package com.example.volunteer.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.example.volunteer.R;
import com.example.volunteer.data.train.Course;

import java.util.List;

public class HotCourseAdapter extends RecyclerView.Adapter<HotCourseAdapter.ViewHolder> {

    private List<Course> courses;
    private Context context;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Course course);
    }

    public HotCourseAdapter(Context context, List<Course> courses, OnItemClickListener listener) {
        this.context = context;
        this.courses = courses;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_hot_course, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Course course = courses.get(position);
        holder.bind(course);
    }

    @Override
    public int getItemCount() {
        return courses.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivCourseImage;
        private TextView tvCourseTitle;
        private TextView tvDuration;
        private TextView tvLevel;
        private CardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCourseImage = itemView.findViewById(R.id.iv_course_image);
            tvCourseTitle = itemView.findViewById(R.id.tv_course_title);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvLevel = itemView.findViewById(R.id.tv_level);
            cardView = (CardView) itemView;
        }

        public void bind(Course course) {
            tvCourseTitle.setText(course.getTitle());
            tvDuration.setText(course.getDuration());
            tvLevel.setText(course.getLevel());

            try {
                tvLevel.setBackgroundColor(Color.parseColor(course.getLevelBgColor()));
                tvLevel.setTextColor(Color.parseColor(course.getLevelColor()));
            } catch (Exception e) {
                tvLevel.setBackgroundColor(Color.parseColor("#E6F7E6"));
                tvLevel.setTextColor(Color.parseColor("#2E7D32"));
            }

            // 不使用Glide，直接设置图片资源
            if (course.getImageRes() != 0) {
                ivCourseImage.setImageResource(course.getImageRes());
            } else {
                // 设置占位背景
                ivCourseImage.setImageDrawable(null);
                GradientDrawable drawable = new GradientDrawable();
                drawable.setColor(Color.parseColor("#E2E8F0"));
                drawable.setCornerRadius(12 * context.getResources().getDisplayMetrics().density);
                ivCourseImage.setBackground(drawable);
            }

            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(course);
                }
            });
        }
    }
}