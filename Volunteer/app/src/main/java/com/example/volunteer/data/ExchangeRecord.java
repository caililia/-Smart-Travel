package com.example.volunteer.data;

public class ExchangeRecord {
    private int id;
    private int productId;
    private String productName;
    private int points;
    private String exchangeTime;
    private String status; // pending, completed, cancelled

    public ExchangeRecord() {
    }

    public ExchangeRecord(int id, String productName, int points, String exchangeTime) {
        this.id = id;
        this.productName = productName;
        this.points = points;
        this.exchangeTime = exchangeTime;
    }

    // Getter 和 Setter
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public String getExchangeTime() { return exchangeTime; }
    public void setExchangeTime(String exchangeTime) { this.exchangeTime = exchangeTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusText() {
        if (status == null) return "处理中";
        switch (status) {
            case "pending": return "处理中";
            case "completed": return "已完成";
            case "cancelled": return "已取消";
            default: return status;
        }
    }
}