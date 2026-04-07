package com.example.volunteer.data.train;

// Certificate.java
public class Certificate {
    private String id;
    private String name;
    private String issueDate;
    private int imageRes;
    private boolean isValid;

    public Certificate(String id, String name, String issueDate, int imageRes, boolean isValid) {
        this.id = id;
        this.name = name;
        this.issueDate = issueDate;
        this.imageRes = imageRes;
        this.isValid = isValid;
    }

    // Getter和Setter方法
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIssueDate() { return issueDate; }
    public void setIssueDate(String issueDate) { this.issueDate = issueDate; }
    public int getImageRes() { return imageRes; }
    public void setImageRes(int imageRes) { this.imageRes = imageRes; }
    public boolean isValid() { return isValid; }
    public void setValid(boolean valid) { isValid = valid; }
}