package com.example.volunteer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.volunteer.R;
import com.example.volunteer.data.train.Course;

import java.util.List;

public class RequiredCourseAdapter extends RecyclerView.Adapter<RequiredCourseAdapter.ViewHolder> {

    private List<Course> courses;
    private Context context;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Course course);
    }

    public RequiredCourseAdapter(Context context, List<Course> courses, OnItemClickListener listener) {
        this.context = context;
        this.courses = courses;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_required_course, parent, false);
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
        private TextView tvCourseDesc;
        private TextView tvDuration;
        private TextView tvStudentCount;
        private TextView tvStatus;
        private CardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCourseImage = itemView.findViewById(R.id.iv_course_image);
            tvCourseTitle = itemView.findViewById(R.id.tv_course_title);
            tvCourseDesc = itemView.findViewById(R.id.tv_course_desc);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvStudentCount = itemView.findViewById(R.id.tv_student_count);
            tvStatus = itemView.findViewById(R.id.tv_status);
            cardView = (CardView) itemView;
        }

        public void bind(Course course) {
            tvCourseTitle.setText(course.getTitle());
            tvCourseDesc.setText(course.getDescription() != null ? course.getDescription() : "");
            tvDuration.setText(course.getDuration());
            tvStudentCount.setText(course.getStudentCount() != null ? course.getStudentCount() : "0人学习");
            tvStatus.setText(course.getStatus() != null ? course.getStatus() : "未开始");

            // 根据状态设置不同样式
            String status = course.getStatus();
            if ("已完成".equals(status)) {
                tvStatus.setBackgroundResource(R.drawable.bg_status_completed);
                tvStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
            } else if ("学习中".equals(status)) {
                tvStatus.setBackgroundResource(R.drawable.bg_status_learning);
                tvStatus.setTextColor(android.graphics.Color.parseColor("#F97316"));
            } else {
                tvStatus.setBackgroundResource(R.drawable.bg_status_not_started);
                tvStatus.setTextColor(android.graphics.Color.parseColor("#64748B"));
            }

            Glide.with(context)
                    .load(course.getImageRes())
                    .placeholder(R.drawable.placeholder_course)
                    .into(ivCourseImage);

            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(course);
                }
            });
        }
    }
}