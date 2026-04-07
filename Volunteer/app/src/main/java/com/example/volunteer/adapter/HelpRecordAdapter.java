package com.example.volunteer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.example.volunteer.R;
import com.example.volunteer.data.VoiceCall;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class HelpRecordAdapter extends RecyclerView.Adapter<HelpRecordAdapter.ViewHolder> {

    private Context context;
    private List<VoiceCall> records;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(VoiceCall record);
    }

    public HelpRecordAdapter(Context context, List<VoiceCall> records, OnItemClickListener listener) {
        this.context = context;
        this.records = records;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_help_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VoiceCall record = records.get(position);
        holder.bind(record);
    }

    @Override
    public int getItemCount() {
        return records == null ? 0 : records.size();
    }

    public void updateData(List<VoiceCall> newRecords) {
        this.records = newRecords;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView tvRoomId;
        private TextView tvStatus;
        private TextView tvCallType;
        private TextView tvDuration;
        private TextView tvCreateTime;
        private TextView tvEndTime;
        private CardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRoomId = itemView.findViewById(R.id.tv_room_id);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvCallType = itemView.findViewById(R.id.tv_call_type);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvCreateTime = itemView.findViewById(R.id.tv_create_time);
            tvEndTime = itemView.findViewById(R.id.tv_end_time);
            cardView = (CardView) itemView;
        }

        public void bind(VoiceCall record) {
            // 房间号
            tvRoomId.setText("房间号: " + record.getRoomId());

            // 状态
            tvStatus.setText(getStatusText(record.getCallStatus()));
            tvStatus.setBackgroundResource(getStatusBg(record.getCallStatus()));

            // 通话类型
            tvCallType.setText(getCallTypeText(record.getCallType()));

            // 时长（统一转换为小时）
            if (record.getEndTime() != null && !record.getEndTime().isEmpty()) {
                double hours = calculateDurationInHours(record.getCreateTime(), record.getEndTime());
                tvDuration.setText(String.format(Locale.getDefault(), "时长: %.2f小时", hours));
            } else {
                tvDuration.setText("进行中...");
            }

            // 时间显示
            String timeInfo = formatTimeInfo(record);
            tvCreateTime.setText("开始时间:" + formatDateTime(record.getCreateTime()));
            tvEndTime.setText("结束时间:" + formatDateTime(record.getCreateTime()));

            // 点击事件
            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(record);
                }
            });
        }

        /**
         * 计算时长（返回小时数）
         */
        private double calculateDurationInHours(String startTime, String endTime) {
            if (startTime == null || endTime == null) return 0;
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                long start = sdf.parse(startTime).getTime();
                long end = sdf.parse(endTime).getTime();
                long durationMillis = end - start;

                if (durationMillis < 0) return 0;

                // 转换为小时（保留一位小数）
                double hours = durationMillis / 3600000.0;
                return Math.round(hours * 10) / 10.0; // 保留一位小数

            } catch (Exception e) {
                return 0;
            }
        }

        /**
         * 格式化时间信息
         */
        private String formatTimeInfo(VoiceCall record) {
            StringBuilder timeInfo = new StringBuilder();

            // 添加开始时间
            if (record.getCreateTime() != null && !record.getCreateTime().isEmpty()) {
                timeInfo.append("开始: ").append(formatDateTime(record.getCreateTime()));
            }

            // 添加结束时间
            if (record.getEndTime() != null && !record.getEndTime().isEmpty()) {
                timeInfo.append("  结束: ").append(formatDateTime(record.getEndTime()));
            } else {
                timeInfo.append("  状态: 进行中");
            }

            return timeInfo.toString();
        }

        private String getStatusText(String status) {
            if (status == null) return "未知";
            switch (status) {
                case "pending": return "进行中";
                case "completed": return "已完成";
                case "cancelled": return "已取消";
                default: return status;
            }
        }

        private int getStatusBg(String status) {
            if (status == null) return R.drawable.bg_status_pending;
            switch (status) {
                case "pending": return R.drawable.bg_status_pending;
                case "completed": return R.drawable.bg_status_completed;
                case "cancelled": return R.drawable.bg_status_cancelled;
                default: return R.drawable.bg_status_pending;
            }
        }

        private String getCallTypeText(String callType) {
            if (callType == null) return "未知";
            switch (callType) {
                case "1": return "语音通话";
                case "2": return "视频通话";
                case "3": return "文字聊天";
                default: return "未知类型";
            }
        }

        private String formatTime(String timeStr) {
            if (timeStr == null || timeStr.isEmpty()) return "--";
            try {
                if (timeStr.length() >= 16) {
                    return timeStr.substring(11, 16);
                }
                return timeStr;
            } catch (Exception e) {
                return timeStr;
            }
        }

        /**
         * 格式化完整日期时间 (MM-dd HH:mm)
         */
        private String formatDateTime(String timeStr) {
            if (timeStr == null || timeStr.isEmpty()) return "--";
            try {
                if (timeStr.length() >= 16) {
                    return timeStr.substring(5, 16);
                }
                return timeStr;
            } catch (Exception e) {
                return timeStr;
            }
        }
    }
}