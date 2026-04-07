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

import com.example.volunteer.R;
import com.example.volunteer.data.train.ExamCert;

import java.util.List;

public class ExamCertAdapter extends RecyclerView.Adapter<ExamCertAdapter.ViewHolder> {

    private List<ExamCert> exams;
    private Context context;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ExamCert exam);
    }

    public ExamCertAdapter(Context context, List<ExamCert> exams, OnItemClickListener listener) {
        this.context = context;
        this.exams = exams;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_exam_cert, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExamCert exam = exams.get(position);
        holder.bind(exam);
    }

    @Override
    public int getItemCount() {
        return exams.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivExamIcon;
        private TextView tvExamTitle;
        private TextView tvExamDesc;
        private TextView tvDuration;
        private TextView tvPassRate;
        private TextView tvStatus;
        private CardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivExamIcon = itemView.findViewById(R.id.iv_exam_icon);
            tvExamTitle = itemView.findViewById(R.id.tv_exam_title);
            tvExamDesc = itemView.findViewById(R.id.tv_exam_desc);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvPassRate = itemView.findViewById(R.id.tv_pass_rate);
            tvStatus = itemView.findViewById(R.id.tv_status);
            cardView = (CardView) itemView;
        }

        public void bind(ExamCert exam) {
            tvExamTitle.setText(exam.getTitle());
            tvExamDesc.setText(exam.getDescription());
            tvDuration.setText(exam.getDuration());
            tvPassRate.setText("通过率 " + exam.getPassRate());
            tvStatus.setText(exam.isPassed() ? "已通过" : "未开始");

            if (exam.isPassed()) {
                tvStatus.setBackgroundResource(R.drawable.bg_status_completed);
                tvStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
            } else {
                tvStatus.setBackgroundResource(R.drawable.bg_status_not_started);
                tvStatus.setTextColor(android.graphics.Color.parseColor("#64748B"));
            }

            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(exam);
                }
            });
        }
    }
}