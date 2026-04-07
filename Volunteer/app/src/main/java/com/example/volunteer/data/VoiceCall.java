package com.example.volunteer.data;

public class VoiceCall {
    private long call_id;
    private String roomId;
    private String callType;
    private long requesterId;
    private long helperId;
    private String userLocation;
    private String callStatus;
    private String createTime;
    private String endTime;
    private String isArchived;
    private String fullDown;

    // 计算字段
    private String durationTime;

    // getter 和 setter
    public long getCall_id() { return call_id; }
    public void setCall_id(long call_id) { this.call_id = call_id; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getCallType() { return callType; }
    public void setCallType(String callType) { this.callType = callType; }

    public long getRequesterId() { return requesterId; }
    public void setRequesterId(long requesterId) { this.requesterId = requesterId; }

    public long getHelperId() { return helperId; }
    public void setHelperId(long helperId) { this.helperId = helperId; }

    public String getUserLocation() { return userLocation; }
    public void setUserLocation(String userLocation) { this.userLocation = userLocation; }

    public String getCallStatus() { return callStatus; }
    public void setCallStatus(String callStatus) { this.callStatus = callStatus; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getIsArchived() { return isArchived; }
    public void setIsArchived(String isArchived) { this.isArchived = isArchived; }

    public String getFullDown() { return fullDown; }
    public void setFullDown(String fullDown) { this.fullDown = fullDown; }

    public String getDurationTime() { return durationTime; }
    public void setDurationTime(String durationTime) { this.durationTime = durationTime; }

    /**
     * 获取状态显示文字
     */
    public String getStatusText() {
        if (callStatus == null) return "未知";
        switch (callStatus) {
            case "pending": return "进行中";
            case "completed": return "已完成";
            case "cancelled": return "已取消";
            default: return callStatus;
        }
    }

    /**
     * 获取状态颜色
     */
    public int getStatusColor() {
        if (callStatus == null) return 0xFF94A3B8;
        switch (callStatus) {
            case "pending": return 0xFFF97316;  // 橙色
            case "completed": return 0xFF22C55E; // 绿色
            case "cancelled": return 0xFFEF4444; // 红色
            default: return 0xFF94A3B8;
        }
    }

    /**
     * 获取调用类型文字
     */
    public String getCallTypeText() {
        if (callType == null) return "未知";
        switch (callType) {
            case "1": return "语音通话";
            case "2": return "视频通话";
            case "3": return "文字聊天";
            default: return "未知类型";
        }
    }
}