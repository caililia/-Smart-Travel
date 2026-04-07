package com.example.volunteer.data.train;

// ExamCert.java
public class ExamCert {
    private String id;
    private String title;
    private String description;
    private String duration;
    private String passRate;
    private boolean isPassed;

    public ExamCert(String id, String title, String description,
                    String duration, String passRate, boolean isPassed) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.duration = duration;
        this.passRate = passRate;
        this.isPassed = isPassed;
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
    public String getPassRate() { return passRate; }
    public void setPassRate(String passRate) { this.passRate = passRate; }
    public boolean isPassed() { return isPassed; }
    public void setPassed(boolean passed) { isPassed = passed; }
}
