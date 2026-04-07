package com.example.volunteer.data;

import java.util.List;

public class FeedData {

    /**
     * msg : 查询成功
     * code : 200
     * data : [{"userId":10000003,"title":"test","content":"test","createTime":null,"feedbackId":1},{"userId":10000003,"title":"test","content":"test","createTime":"2026-03-23 18:16:18","feedbackId":2},{"userId":10000003,"title":"test","content":"test","createTime":"2026-03-23 18:19:03","feedbackId":3},{"userId":10000003,"title":"test","content":"test","createTime":"2026-03-23 18:20:04","feedbackId":4},{"userId":10000003,"title":"test","content":"test","createTime":"2026-03-23 18:20:56","feedbackId":5},{"userId":10000003,"title":"666666用户说","content":"这个功能还不错。","createTime":"2026-03-23 18:55:10","feedbackId":6}]
     */

    private String msg;
    private int code;
    private List<DataBean> data;

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

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

    public static class DataBean {
        /**
         * userId : 10000003
         * title : test
         * content : test
         * createTime : null
         * feedbackId : 1
         */

        private int userId;
        private String title;
        private String content;
        private String createTime;
        private int feedbackId;

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Object getCreateTime() {
            return createTime;
        }

        public void setCreateTime(String createTime) {
            this.createTime = createTime;
        }

        public int getFeedbackId() {
            return feedbackId;
        }

        public void setFeedbackId(int feedbackId) {
            this.feedbackId = feedbackId;
        }
    }
}
