package com.example.volunteer.data;

import java.util.List;

public class Room {
    private int code;
    private List<DataBean> data;
    private String message;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public List<DataBean> getData() {
        return data;
    }

    public void setData(List<DataBean> data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static class DataBean {
        private int call_id;
        private String roomId;
        private String callType;
        private int requesterId;
        private int helperId;
        private String userLocation;
        private String callStatus;
        private String createTime;
        private String endTime;
        private String isArchived;
        private String fullDown;

        // Getters and Setters
        public int getCall_id() {
            return call_id;
        }

        public void setCall_id(int call_id) {
            this.call_id = call_id;
        }

        public String getRoomId() {
            return roomId;
        }

        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        public String getCallType() {
            return callType;
        }

        public void setCallType(String callType) {
            this.callType = callType;
        }

        public int getRequesterId() {
            return requesterId;
        }

        public void setRequesterId(int requesterId) {
            this.requesterId = requesterId;
        }

        public int getHelperId() {
            return helperId;
        }

        public void setHelperId(int helperId) {
            this.helperId = helperId;
        }

        public String getUserLocation() {
            return userLocation;
        }

        public void setUserLocation(String userLocation) {
            this.userLocation = userLocation;
        }

        public String getCallStatus() {
            return callStatus;
        }

        public void setCallStatus(String callStatus) {
            this.callStatus = callStatus;
        }

        public String getCreateTime() {
            return createTime;
        }

        public void setCreateTime(String createTime) {
            this.createTime = createTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        public String getIsArchived() {
            return isArchived;
        }

        public void setIsArchived(String isArchived) {
            this.isArchived = isArchived;
        }

        public String getFullDown() {
            return fullDown;
        }

        public void setFullDown(String fullDown) {
            this.fullDown = fullDown;
        }

        // 辅助方法
        public String getTaskType() {
            return "1".equals(callType) ? "视频求助" : "语音求助";
        }

        @Override
        public String toString() {
            return "DataBean{" +
                    "call_id=" + call_id +
                    ", callType='" + callType + '\'' +
                    ", requesterId=" + requesterId +
                    ", fullDown='" + fullDown + '\'' +
                    '}';
        }
    }
}