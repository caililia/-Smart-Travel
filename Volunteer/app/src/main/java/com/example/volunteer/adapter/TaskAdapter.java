package com.example.volunteer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteer.R;
import com.example.volunteer.data.Room;

import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private Context context;
    private List<Room.DataBean> taskList;
    private OnTaskActionListener listener;

    public interface OnTaskActionListener {
        void onAcceptTask(Room.DataBean task);
    }

    public TaskAdapter(Context context, List<Room.DataBean> taskList, OnTaskActionListener listener) {
        this.context = context;
        this.taskList = taskList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Room.DataBean task = taskList.get(position);
        holder.bind(task);

        holder.btnAccept.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAcceptTask(task);
            }
        });
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvTaskTitle;
        TextView tvDistance;
        TextView tvRequesterId;
        TextView tvTaskType;
        TextView tvTaskDesc;
        TextView tvCreateTime;
        Button btnAccept;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTaskTitle = itemView.findViewById(R.id.tv_task_title);
            tvDistance = itemView.findViewById(R.id.tv_distance);
            tvRequesterId = itemView.findViewById(R.id.tv_requester_id);
            tvTaskType = itemView.findViewById(R.id.tv_task_type);
            tvTaskDesc = itemView.findViewById(R.id.tv_task_desc);
            tvCreateTime = itemView.findViewById(R.id.tv_create_time);
            btnAccept = itemView.findViewById(R.id.btn_accept);
        }

        public void bind(Room.DataBean task) {
            // 设置标题
            if ("1".equals(task.getFullDown())) {
                tvTaskTitle.setText("紧急求助-摔倒");
                tvTaskTitle.setTextColor(context.getResources().getColor(R.color.red));
            } else {
                tvTaskTitle.setText(task.getTaskType());
                tvTaskTitle.setTextColor(context.getResources().getColor(R.color.black));
            }

            // 设置距离（可以根据经纬度计算实际距离）
            tvDistance.setText("7087.5m");

            // 设置求助者ID（脱敏显示）
            String requesterId = String.valueOf(task.getRequesterId());
            String maskedId;
            if (requesterId.length() <= 4) {
                maskedId = requesterId;
            } else {
                maskedId = requesterId.substring(0, 4) + "**" +
                        requesterId.substring(requesterId.length() - 2);
            }
            tvRequesterId.setText("求助者" + maskedId);

            // 设置求助类型标签
            if ("1".equals(task.getCallType())) {
                tvTaskType.setText("视频求助");
                tvTaskType.setBackgroundColor(context.getResources().getColor(R.color.red));
            } else {
                tvTaskType.setText("语音求助");
                tvTaskType.setBackgroundColor(context.getResources().getColor(R.color.colorPrimary));
            }

            // 设置任务描述
            if ("1".equals(task.getFullDown())) {
                tvTaskDesc.setText("用户摔倒，需要紧急帮助！");
            } else {
                tvTaskDesc.setText("正在" + task.getTaskType());
            }

            // 设置创建时间
            tvCreateTime.setText(task.getCreateTime());
        }
    }
}