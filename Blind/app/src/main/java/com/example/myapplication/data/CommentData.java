package com.example.myapplication.data;

import java.util.List;

public class CommentData {

    /**
     * code  : 200
     * data : {"total":2,"data2":[],"data1":[{"commentId":2,"userId":10000003,"userName":null,"userAvatar":null,"content":"测试","voiceUrl":"","voiceDuration":null,"parentId":0,"rootId":0,"level":1,"replyToUserId":null,"replyToUserName":null,"replyToCommentId":null,"likeCount":0,"replyCount":0,"isLiked":null,"createTime":"2026-03-10 15:38:34","childComments":null},{"commentId":1,"userId":10000003,"userName":null,"userAvatar":null,"content":"测试","voiceUrl":"","voiceDuration":null,"parentId":0,"rootId":0,"level":1,"replyToUserId":null,"replyToUserName":null,"replyToCommentId":null,"likeCount":0,"replyCount":0,"isLiked":null,"createTime":"2026-03-10 14:40:50","childComments":null}],"pageSize":10,"page":1}
     * message : 获取成功
     */

    private int code;
    private DataBean data;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static class DataBean {
        /**
         * total : 2
         * data2 : []
         * data1 : [{"commentId":2,"userId":10000003,"userName":null,"userAvatar":null,"content":"测试","voiceUrl":"","voiceDuration":null,"parentId":0,"rootId":0,"level":1,"replyToUserId":null,"replyToUserName":null,"replyToCommentId":null,"likeCount":0,"replyCount":0,"isLiked":null,"createTime":"2026-03-10 15:38:34","childComments":null},{"commentId":1,"userId":10000003,"userName":null,"userAvatar":null,"content":"测试","voiceUrl":"","voiceDuration":null,"parentId":0,"rootId":0,"level":1,"replyToUserId":null,"replyToUserName":null,"replyToCommentId":null,"likeCount":0,"replyCount":0,"isLiked":null,"createTime":"2026-03-10 14:40:50","childComments":null}]
         * pageSize : 10
         * page : 1
         */

        private int total;
        private int pageSize;
        private int page;
        private List<?> data2;
        private List<Data1Bean> data1;

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public List<?> getData2() {
            return data2;
        }

        public void setData2(List<?> data2) {
            this.data2 = data2;
        }

        public List<Data1Bean> getData1() {
            return data1;
        }

        public void setData1(List<Data1Bean> data1) {
            this.data1 = data1;
        }

        public static class Data1Bean {
            /**
             * commentId : 2
             * userId : 10000003
             * userName : null
             * userAvatar : null
             * content : 测试
             * voiceUrl :
             * voiceDuration : null
             * parentId : 0
             * rootId : 0
             * level : 1
             * replyToUserId : null
             * replyToUserName : null
             * replyToCommentId : null
             * likeCount : 0
             * replyCount : 0
             * isLiked : null
             * createTime : 2026-03-10 15:38:34
             * childComments : null
             */

            private int commentId;
            private int userId;
            private Object userName;
            private Object userAvatar;
            private String content;
            private String voiceUrl;
            private Object voiceDuration;
            private int parentId;
            private int rootId;
            private int level;
            private Object replyToUserId;
            private Object replyToUserName;
            private Object replyToCommentId;
            private int likeCount;
            private int replyCount;
            private Object isLiked;
            private String createTime;
            private Object childComments;

            public int getCommentId() {
                return commentId;
            }

            public void setCommentId(int commentId) {
                this.commentId = commentId;
            }

            public int getUserId() {
                return userId;
            }

            public void setUserId(int userId) {
                this.userId = userId;
            }

            public Object getUserName() {
                return userName;
            }

            public void setUserName(Object userName) {
                this.userName = userName;
            }

            public Object getUserAvatar() {
                return userAvatar;
            }

            public void setUserAvatar(Object userAvatar) {
                this.userAvatar = userAvatar;
            }

            public String getContent() {
                return content;
            }

            public void setContent(String content) {
                this.content = content;
            }

            public String getVoiceUrl() {
                return voiceUrl;
            }

            public void setVoiceUrl(String voiceUrl) {
                this.voiceUrl = voiceUrl;
            }

            public Object getVoiceDuration() {
                return voiceDuration;
            }

            public void setVoiceDuration(Object voiceDuration) {
                this.voiceDuration = voiceDuration;
            }

            public int getParentId() {
                return parentId;
            }

            public void setParentId(int parentId) {
                this.parentId = parentId;
            }

            public int getRootId() {
                return rootId;
            }

            public void setRootId(int rootId) {
                this.rootId = rootId;
            }

            public int getLevel() {
                return level;
            }

            public void setLevel(int level) {
                this.level = level;
            }

            public Object getReplyToUserId() {
                return replyToUserId;
            }

            public void setReplyToUserId(Object replyToUserId) {
                this.replyToUserId = replyToUserId;
            }

            public Object getReplyToUserName() {
                return replyToUserName;
            }

            public void setReplyToUserName(Object replyToUserName) {
                this.replyToUserName = replyToUserName;
            }

            public Object getReplyToCommentId() {
                return replyToCommentId;
            }

            public void setReplyToCommentId(Object replyToCommentId) {
                this.replyToCommentId = replyToCommentId;
            }

            public int getLikeCount() {
                return likeCount;
            }

            public void setLikeCount(int likeCount) {
                this.likeCount = likeCount;
            }

            public int getReplyCount() {
                return replyCount;
            }

            public void setReplyCount(int replyCount) {
                this.replyCount = replyCount;
            }

            public Object getIsLiked() {
                return isLiked;
            }

            public void setIsLiked(Object isLiked) {
                this.isLiked = isLiked;
            }

            public String getCreateTime() {
                return createTime;
            }

            public void setCreateTime(String createTime) {
                this.createTime = createTime;
            }

            public Object getChildComments() {
                return childComments;
            }

            public void setChildComments(Object childComments) {
                this.childComments = childComments;
            }
        }
    }
}
