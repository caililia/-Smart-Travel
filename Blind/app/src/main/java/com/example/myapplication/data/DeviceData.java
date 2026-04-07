package com.example.myapplication.data;

import java.util.List;

public class DeviceData {

    /**
     * msg : 查询成功
     * code : 200
     * data : [{"deviceId":4,"deviceUniqueId":"20047E33E864","userId":10000003,"status":0,"deviceType":"1","deviceName":"ESP32-CAM-00FF0000","activationDate":"2025-12-21 14:26:54","createTime":"2025-12-22 20:57:54","lastUpdateTime":"2026-03-25 13:58:20"},{"deviceId":5,"deviceUniqueId":"C4D8D53CB1F3","userId":10000003,"status":1,"deviceType":"0","deviceName":"ESP8266-3CB1F3","activationDate":null,"createTime":null,"lastUpdateTime":null}]
     */

    private String msg;
    private String code;
    private List<DataBean> data;

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<DataBean> getData() {
        return data;
    }

    public void setData(List<DataBean> data) {
        this.data = data;
    }

    public static class DataBean {
        /**
         * deviceId : 4
         * deviceUniqueId : 20047E33E864
         * userId : 10000003
         * status : 0
         * deviceType : 1
         * deviceName : ESP32-CAM-00FF0000
         * activationDate : 2025-12-21 14:26:54
         * createTime : 2025-12-22 20:57:54
         * lastUpdateTime : 2026-03-25 13:58:20
         */

        private int deviceId;
        private String deviceUniqueId;
        private int userId;
        private int status;
        private String deviceType;
        private String deviceName;
        private String activationDate;
        private String createTime;
        private String lastUpdateTime;

        public int getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(int deviceId) {
            this.deviceId = deviceId;
        }

        public String getDeviceUniqueId() {
            return deviceUniqueId;
        }

        public void setDeviceUniqueId(String deviceUniqueId) {
            this.deviceUniqueId = deviceUniqueId;
        }

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getDeviceType() {
            return deviceType;
        }

        public void setDeviceType(String deviceType) {
            this.deviceType = deviceType;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }

        public String getActivationDate() {
            return activationDate;
        }

        public void setActivationDate(String activationDate) {
            this.activationDate = activationDate;
        }

        public String getCreateTime() {
            return createTime;
        }

        public void setCreateTime(String createTime) {
            this.createTime = createTime;
        }

        public String getLastUpdateTime() {
            return lastUpdateTime;
        }

        public void setLastUpdateTime(String lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }
    }
}