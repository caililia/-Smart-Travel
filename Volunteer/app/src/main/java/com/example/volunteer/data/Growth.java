package com.example.volunteer.data;

import java.util.List;

public class Growth {

    /**
     * code : 200
     * message : success
     * data : {"currentHours":0,"medals":[],"serviceHours":0,"servicePeopleCount":0,"level":1,"nextLevelHours":100,"experiencePoints":0,"nextLevelName":"白银志愿者","levelName":"青铜志愿者","userId":10000005,"medalCount":0,"points":0}
     */

    private int code;
    private String message;
    private DataBean data;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public DataBean getData() {
        return data;
    }

    public void setData(DataBean data) {
        this.data = data;
    }

    public static class DataBean {
        /**
         * currentHours : 0
         * medals : []
         * serviceHours : 0
         * servicePeopleCount : 0
         * level : 1
         * nextLevelHours : 100
         * experiencePoints : 0
         * nextLevelName : 白银志愿者
         * levelName : 青铜志愿者
         * userId : 10000005
         * medalCount : 0
         * points : 0
         */

        private int currentHours;
        private int serviceHours;
        private int servicePeopleCount;
        private int level;
        private int nextLevelHours;
        private int experiencePoints;
        private String nextLevelName;
        private String levelName;
        private int userId;
        private int medalCount;
        private int points;
        private String medals;

        public int getCurrentHours() {
            return currentHours;
        }

        public void setCurrentHours(int currentHours) {
            this.currentHours = currentHours;
        }

        public int getServiceHours() {
            return serviceHours;
        }

        public void setServiceHours(int serviceHours) {
            this.serviceHours = serviceHours;
        }

        public int getServicePeopleCount() {
            return servicePeopleCount;
        }

        public void setServicePeopleCount(int servicePeopleCount) {
            this.servicePeopleCount = servicePeopleCount;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public int getNextLevelHours() {
            return nextLevelHours;
        }

        public void setNextLevelHours(int nextLevelHours) {
            this.nextLevelHours = nextLevelHours;
        }

        public int getExperiencePoints() {
            return experiencePoints;
        }

        public void setExperiencePoints(int experiencePoints) {
            this.experiencePoints = experiencePoints;
        }

        public String getNextLevelName() {
            return nextLevelName;
        }

        public void setNextLevelName(String nextLevelName) {
            this.nextLevelName = nextLevelName;
        }

        public String getLevelName() {
            return levelName;
        }

        public void setLevelName(String levelName) {
            this.levelName = levelName;
        }

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public int getMedalCount() {
            return medalCount;
        }

        public void setMedalCount(int medalCount) {
            this.medalCount = medalCount;
        }

        public int getPoints() {
            return points;
        }

        public void setPoints(int points) {
            this.points = points;
        }

        public String getMedals() {
            return medals;
        }

        public void setMedals(String medals) {
            this.medals = medals;
        }
    }
}
