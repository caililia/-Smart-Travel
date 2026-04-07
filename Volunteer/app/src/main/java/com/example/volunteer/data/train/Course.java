package com.example.volunteer.data.train;

// Course.java
public class Course {
    private String id;
    private String title;
    private String duration;
    private String level;
    private String levelColor;
    private String levelBgColor;
    private int imageRes;
    private String studentCount;
    private String status;
    private String description;

    public Course(String id, String title, String duration, String level,
                  String levelColor, String levelBgColor, int imageRes,
                  String studentCount, String status) {
        this.id = id;
        this.title = title;
        this.duration = duration;
        this.level = level;
        this.levelColor = levelColor;
        this.levelBgColor = levelBgColor;
        this.imageRes = imageRes;
        this.studentCount = studentCount;
        this.status = status;
    }

    // Getter和Setter方法
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getLevelColor() { return levelColor; }
    public void setLevelColor(String levelColor) { this.levelColor = levelColor; }
    public String getLevelBgColor() { return levelBgColor; }
    public void setLevelBgColor(String levelBgColor) { this.levelBgColor = levelBgColor; }
    public int getImageRes() { return imageRes; }
    public void setImageRes(int imageRes) { this.imageRes = imageRes; }
    public String getStudentCount() { return studentCount; }
    public void setStudentCount(String studentCount) { this.studentCount = studentCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}





