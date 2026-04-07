package com.example.myapplication.data;

public class CaptchaData {

    /**
     * code : 200
     * smsCode : 182115
     * message : 验证码发送成功
     */

    private int code;
    private String smsCode;
    private String message;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getSmsCode() {
        return smsCode;
    }

    public void setSmsCode(String smsCode) {
        this.smsCode = smsCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
