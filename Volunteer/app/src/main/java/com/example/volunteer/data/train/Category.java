package com.example.volunteer.data.train;

// Category.java
public class Category {
    private String id;
    private String name;
    private String color;
    private boolean isSelected;

    public Category(String id, String name, String color, boolean isSelected) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.isSelected = isSelected;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}