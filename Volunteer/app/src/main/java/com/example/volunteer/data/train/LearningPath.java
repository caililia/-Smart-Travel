package com.example.volunteer.data.train;

// LearningPath.java
public class LearningPath {
    private String id;
    private String title;
    private String description;
    private int progress;
    private int courseCount;
    private int imageRes;

    public LearningPath(String id, String title, String description,
                        int progress, int courseCount, int imageRes) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.progress = progress;
        this.courseCount = courseCount;
        this.imageRes = imageRes;
    }

    // Getter和Setter方法
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public int getCourseCount() { return courseCount; }
    public void setCourseCount(int courseCount) { this.courseCount = courseCount; }
    public int getImageRes() { return imageRes; }
    public void setImageRes(int imageRes) { this.imageRes = imageRes; }
}
