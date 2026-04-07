package com.example.volunteer.data;

import java.util.List;

public class VoiceCallResponse {
    private int code;
    private String message;
    private int total;
    private List<VoiceCall> data;

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public List<VoiceCall> getData() { return data; }
    public void setData(List<VoiceCall> data) { this.data = data; }

    public boolean isSuccess() {
        return code == 200;
    }
}