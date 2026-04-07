package com.example.volunteer.entity;

public class Feedback {
    private String title;
    private String content;
    private String Time;

    // 构造函数
    public Feedback(String title, String content, String Time) {
        this.title = title;
        this.content = content;
        this.Time = Time;
    }

    // Getter 和 Setter 方法
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

    public String getTime() {
        return Time;
    }

    public void setTime(String time) {
        Time = time;
    }
}
