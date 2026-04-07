package com.example.volunteer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.volunteer.R;
import com.example.volunteer.data.train.LearningPath;

import java.util.List;

public class LearningPathAdapter extends RecyclerView.Adapter<LearningPathAdapter.ViewHolder> {

    private List<LearningPath> paths;
    private Context context;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(LearningPath path);
    }

    public LearningPathAdapter(Context context, List<LearningPath> paths, OnItemClickListener listener) {
        this.context = context;
        this.paths = paths;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_learning_path, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LearningPath path = paths.get(position);
        holder.bind(path);
    }

    @Override
    public int getItemCount() {
        return paths.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivPathImage;
        private TextView tvPathTitle;
        private TextView tvPathDesc;
        private TextView tvCourseCount;
        private TextView tvProgress;
        private ProgressBar progressBar;
        private CardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPathImage = itemView.findViewById(R.id.iv_path_image);
            tvPathTitle = itemView.findViewById(R.id.tv_path_title);
            tvPathDesc = itemView.findViewById(R.id.tv_path_desc);
            tvCourseCount = itemView.findViewById(R.id.tv_course_count);
            tvProgress = itemView.findViewById(R.id.tv_progress);
            progressBar = itemView.findViewById(R.id.progress_bar);
            cardView = (CardView) itemView;
        }

        public void bind(LearningPath path) {
            tvPathTitle.setText(path.getTitle());
            tvPathDesc.setText(path.getDescription());
            tvCourseCount.setText(path.getCourseCount() + "门课程");
            tvProgress.setText("进度 " + path.getProgress() + "%");
            progressBar.setProgress(path.getProgress());

            Glide.with(context)
                    .load(path.getImageRes())
                    .placeholder(R.drawable.placeholder_path)
                    .into(ivPathImage);

            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(path);
                }
            });
        }
    }
}