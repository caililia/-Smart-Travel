package com.example.volunteer.data.train;

// SkillPractice.java
public class SkillPractice {
    private String id;
    private String title;
    private String description;
    private String duration;
    private String difficulty;
    private int imageRes;

    public SkillPractice(String id, String title, String description,
                         String duration, String difficulty, int imageRes) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.duration = duration;
        this.difficulty = difficulty;
        this.imageRes = imageRes;
    }

    // Getter和Setter方法
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public int getImageRes() { return imageRes; }
    public void setImageRes(int imageRes) { this.imageRes = imageRes; }
}

