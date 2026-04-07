package com.example.volunteer.entity;

public class Card {
    private String name;
    private int imgSrc;
    private String name2;


    public Card(int imgSrc, String name, String name2) {
        this.name = name;
        this.imgSrc = imgSrc;
        this.name2 = name2;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getImgSrc() {
        return imgSrc;
    }

    public void setImgSrc(int imgSrc) {
        this.imgSrc = imgSrc;
    }

    public String getName2() {
        return name2;
    }

    public void setName2(String name2) {
        this.name2 = name2;
    }

    @Override
    public String toString() {
        return "Card{" +
                "name='" + name + '\'' +
                ", imgSrc=" + imgSrc +
                ", name2='" + name2 + '\'' +
                '}';
    }
}
