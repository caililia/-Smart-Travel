package com.example.volunteer.data;

public class UserPoints {
    private long userId;
    private int totalPoints;
    private int availablePoints;
    private int usedPoints;
    private int level;
    private String levelName;

    public UserPoints() {
    }

    public UserPoints(long userId, int totalPoints, int availablePoints, int usedPoints) {
        this.userId = userId;
        this.totalPoints = totalPoints;
        this.availablePoints = availablePoints;
        this.usedPoints = usedPoints;
        updateLevel();
    }

    private void updateLevel() {
        if (totalPoints < 100) {
            level = 1;
            levelName = "青铜志愿者";
        } else if (totalPoints < 500) {
            level = 2;
            levelName = "白银志愿者";
        } else if (totalPoints < 1000) {
            level = 3;
            levelName = "黄金志愿者";
        } else if (totalPoints < 5000) {
            level = 4;
            levelName = "铂金志愿者";
        } else {
            level = 5;
            levelName = "钻石志愿者";
        }
    }

    // Getter 和 Setter
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
        updateLevel();
    }

    public int getAvailablePoints() { return availablePoints; }
    public void setAvailablePoints(int availablePoints) { this.availablePoints = availablePoints; }

    public int getUsedPoints() { return usedPoints; }
    public void setUsedPoints(int usedPoints) { this.usedPoints = usedPoints; }

    public int getLevel() { return level; }
    public String getLevelName() { return levelName; }

    public int getNextLevelPoints() {
        if (level == 1) return 100;
        if (level == 2) return 500;
        if (level == 3) return 1000;
        if (level == 4) return 5000;
        return totalPoints;
    }

    public int getProgressToNextLevel() {
        int nextPoints = getNextLevelPoints();
        if (nextPoints <= totalPoints) return 100;
        return (int) ((float) totalPoints / nextPoints * 100);
    }
}