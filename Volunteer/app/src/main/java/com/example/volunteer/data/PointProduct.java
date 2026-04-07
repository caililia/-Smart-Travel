package com.example.volunteer.data;

import java.util.List;

public class PointProduct {
    private int id;
    private String name;
    private String description;
    private int points;
    private int stock;
    private String category;
    private String picture;
    private String pictureUrl;
    private boolean isHot;

    // 用于API响应的字段
    private String msg;
    private int code;
    private List<DataBean> data;

    // 构造函数
    public PointProduct() {
    }

    public PointProduct(int id, String name, String description, int points, int stock, String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.points = points;
        this.stock = stock;
        this.category = category;
        this.isHot = false;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        if (name != null) {
            return name;
        } else if (data != null && !data.isEmpty()) {
            return data.get(0).getName();
        }
        return null;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        if (description != null) {
            return description;
        } else if (data != null && !data.isEmpty()) {
            return data.get(0).getDiscription();
        }
        return null;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPoints() {
        if (points > 0) {
            return points;
        } else if (data != null && !data.isEmpty()) {
            try {
                return Integer.parseInt(data.get(0).getPoints());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getStock() {
        if (stock > 0) {
            return stock;
        } else if (data != null && !data.isEmpty()) {
            try {
                return Integer.parseInt(data.get(0).getStock());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPicture() {
        return picture;
    }

    public void setPicture(String picture) {
        this.picture = picture;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }

    public void setPictureUrl(String pictureUrl) {
        this.pictureUrl = pictureUrl;
    }

    public boolean isHot() {
        return isHot;
    }

    public void setHot(boolean hot) {
        isHot = hot;
    }

    public boolean isInStock() {
        return getStock() > 0;
    }

    // 用于API响应的数据
    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public List<DataBean> getData() {
        return data;
    }

    public void setData(List<DataBean> data) {
        this.data = data;
    }

    // 内部类用于API响应
    public static class DataBean {
        private int id;
        private String name;
        private String discription;
        private String points;
        private String stock;
        private String category;
        private String picture;
        private String pictureUrl;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDiscription() {
            return discription;
        }

        public void setDiscription(String discription) {
            this.discription = discription;
        }

        public String getPoints() {
            return points;
        }

        public void setPoints(String points) {
            this.points = points;
        }

        public String getStock() {
            return stock;
        }

        public void setStock(String stock) {
            this.stock = stock;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getPicture() {
            return picture;
        }

        public void setPicture(String picture) {
            this.picture = picture;
        }

        public String getPictureUrl() {
            return pictureUrl;
        }

        public void setPictureUrl(String pictureUrl) {
            this.pictureUrl = pictureUrl;
        }
    }
}