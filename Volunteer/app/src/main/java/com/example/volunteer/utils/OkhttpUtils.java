package com.example.volunteer.utils;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkhttpUtils {
    public static final String URL = "http://192.168.137.1:80/api/blind";
    public static final String WebSocketUrl = "ws://192.168.137.1:80/websocket/rooms"; // 替换为
    //登录
    /*密码登录*/
    public static final String LOGIN = "/user/login/ByPwd";
    /*注册*/
    public static final String REGISTER = "/user/register";
    /*手机号登录*/
    public static final String SMSLOGIN = "/user/login/BySms";
    /*登录获取验证号码*/
    public static final String CAPTCHA = "/user/login/GetSmsCodee";
    /*注册获取验证码*/
    public static final String CAPTCHANOREG = "/user/register/SmsCode";
    //用户信息
    public static final String GETUSERINFO = "/user/info";
    /*通话获取token*/
    public static final String GeneralToken = "/call/generalToken";
    /*发起语音通话创建房间*/
    public static final String CreateRoom = "/voiceCall/createRoom";
    /*根据房间号删除房间*/
    public static final String DelByRoomId = "/voiceCall/delByRoomId";
    /*查看所有房间*/
    public static final String RoomList = "/voiceCall/room/list";
    /*根据helperId获取所有房间数据*/
    public static final String RoomListByHelperId = "/voiceCall/room/helperId/";
    /*新增评论*/
    public static final String addComment = "/comment/manage/addComment";
    /*获取评论*/
    public static final String listComment = "/comment/manage/list";
    /*获取评论加载文件*/
    public static final String CommentFile = "/file/manage";
    /*获取用户成长信息*/
    public static final String Growth = "/user/growth";

    public static final String FeedBack = "/feedback/manage/submitFeedBack";

    public static final String getFeedBack = "/feedback/manage/getFeedBackByPhone";
    /*获取视频文件*/
    public static final String VideoFile = "/file/manage";


    public static MediaType JSON = MediaType.parse("application/json;charset=utf-8");
    public static final String APP_ID = "87b06c07cb7ce7271dff876cafec542c";

    public static void request(String method, String url, RequestBody body, String token, Callback callback) {
        Request request = new Request.Builder().method(method, body).url(url).addHeader("Authorization", token).build();
        new OkHttpClient().newCall(request).enqueue(callback);
    }

    public static void initRequest(final int id, String method, String url, RequestBody body, String token, final Handler handler) {
        OkhttpUtils.request(method, url, body, token, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("onFailure: ", e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                assert response.body() != null;
                String json = Objects.requireNonNull(response.body().string());
                OkhttpUtils.sendMessage(json, id, handler);
            }
        });
    }

    private static void sendMessage(String json, int id, Handler handler) {
        Message msg = new Message();
        msg.what = id;
        msg.obj = json;
        handler.sendMessage(msg);
    }

    public static RequestBody toBody(HashMap<String, Object> map) {
        JSONObject jsonObject = new JSONObject(map);
        return RequestBody.create(OkhttpUtils.JSON, jsonObject.toString());
    }

    public static <T> T toData(String json, Class<T> tClass) {
        Gson gson = new Gson();
        T t = gson.fromJson(json, tClass);
        return t;
    }
    /**
     * 将gson数组转换为对应的Java对象列表
     * @param json gson格式的字符串数组
     * @param tClass 对象类型
     * @param <T> 泛型
     * @return 对象列表
     */
    public static <T> List<T> jsonToList(String json, Class<T[]> tClass) {
        Gson gson = new Gson();
        T[] array = gson.fromJson(json, tClass);
        Type listType = new TypeToken<List<T>>() {}.getType();
        return gson.fromJson(gson.toJson(array), listType);
    }
}