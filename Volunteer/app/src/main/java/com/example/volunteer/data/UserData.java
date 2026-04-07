package com.example.volunteer.data;

public class UserData {

    /**
     * code : 200
     * data : {"userId":10000003,"username":"用户036666","phone":"15666666666","password":null,"email":null,"userType":"0","createTime":"2025-12-26 12:17:36"}
     * success : true
     * message : 查询成功
     */

    private int code;
    private DataBean data;
    private boolean success;
    private String message;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public DataBean getData() {
        return data;
    }

    public void setData(DataBean data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static class DataBean {
        /**
         * userId : 10000003
         * username : 用户036666
         * phone : 15666666666
         * password : null
         * email : null
         * userType : 0
         * createTime : 2025-12-26 12:17:36
         */

        private int userId;
        private String username;
        private String phone;
        private Object password;
        private Object email;
        private String userType;
        private String createTime;

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public Object getPassword() {
            return password;
        }

        public void setPassword(Object password) {
            this.password = password;
        }

        public Object getEmail() {
            return email;
        }

        public void setEmail(Object email) {
            this.email = email;
        }

        public String getUserType() {
            return userType;
        }

        public void setUserType(String userType) {
            this.userType = userType;
        }

        public String getCreateTime() {
            return createTime;
        }

        public void setCreateTime(String createTime) {
            this.createTime = createTime;
        }
    }
}
