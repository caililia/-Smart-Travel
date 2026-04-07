package   包 com.example.myapplication.utils;

import android.os.Handler;进口handler;
import android.os.Message;进口android.os.Message;
import android.util.Log;   进口android.util.Log;

import androidx.annotation.NonNull;进口androidx.annotation.NonNull;

import com.google.gson.Gson;进口com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;进口com.google.gson.reflect.TypeToken;

import org.json.JSONObject;进口org.json.JSONObject;

import java.io.IOException;进口java.io.IOException;
import java.lang.reflect.Type;进口java.lang.reflect.Type;
import java.util.HashMap;   进口java.util.HashMap;
import java.util.List;   进口并不知道;
import java.util.Objects;   进口java.util.Objects;

import okhttp3.Call;   进口okhttp3.Call;
import okhttp3.Callback;   进口okhttp3.Callback;
import okhttp3.MediaType;   进口okhttp3.MediaType;
import okhttp3.OkHttpClient;进口okhttp3.OkHttpClient;
import okhttp3.Request;   进口okhttp3.Request;
import okhttp3.RequestBody;进口okhttp3.RequestBody;
import okhttp3.Response;   进口okhttp3.Response;

public class OkhttpUtils {   公共类OkhttpUtils {
    public static final String URL = "http://192.168.137.1:80/api/blind";URL = "http://192.168.137.1:80/api/blind""http://192.168.137.1:80/api/blind"；
    //登录
    /*密码登录*/
    public static final String LOGIN = "/user/login/ByPwd";public   公共 static   静态 final   最后 String   字符串 LOGIN   登录 = "/user/ LOGIN /ByPwd"   /user/ LOGIN /ByPwd"；；
    /*注册*/
    public static final String REGISTER = "/user/register";public   公共 static   静态 final   最后 String   字符串 REGISTER   注册 = "/user/ REGISTER "   /user/ REGISTER "；；
    /*手机号登录*/
    public static final String SMSLOGIN = "/user/login/BySms";public   公共 static   静态 final   最后 String   字符串 SMSLOGIN = "/user/login/BySms"   “/ user /登录/ BySms"；
    /*登录获取验证号码*/
    public static final String CAPTCHA = "/user/login/GetSmsCode";CAPTCHA = /user/login/GetSmsCode"；
    /*注册获取验证码*/
    public static final String CAPTCHANOREG = "/user/register/SmsCode";CAPTCHANOREG = /user/register/SmsCode"；
    //用户信息
    public static final String GETUSERINFO = "/user/info";GETUSERINFO = "/user/info"；
    /*通话获取token*/
    public static final String GeneralToken = "/call/generalToken";public   公共 static   静态 final   最后 String   字符串 GeneralToken = /call/ GeneralToken "；
    /*发起语音通话创建房间*/
    public static final String CreateRoom = "/voiceCall/createRoom";/voiceCall/ CreateRoom "；
    /*根据房间号修改房间信息*/
    public static final String UpdateRoom = "/voiceCall/updateRoom";/voiceCall/ UpdateRoom "；
    /*根据房间号删除房间*/
    public static final String DelByRoomId = "/voiceCall/delByRoomId";DelByRoomId = "/voiceCall/ DelByRoomId ""/voiceCall/ DelByRoomId ""/voiceCall/ DelByRoomId ""/voiceCall/ DelByRoomId ""/voiceCall/ DelByRoomId ""/voiceCall/ DelByRoomId ""/voiceCall/ DelByRoomId ""/voiceCall/ DelByRoomId ""/voiceCall/ DelByRoomId ""/voiceCall/ DelByRoomId ""/voiceCall/ DelByRoomId "；
    /*增加评论*/
    public static final String AddComment = "/comment/manage/addComment";AddComment = "/comment/manage/ AddComment "/comment/manage/ AddComment "；；
    /*获取音频*/
    public static final String GetAudio = "/audio/manage";GetAudio = "/audio/ management "   /音频/管理"；；
    /*获取评论*/
    public static final String listComment = "/comment/manage/list";public   公共 static   静态 final   最后 String   字符串 listComment = /comment/manage/list   列表"；
    /*获取评论加载文件*/
    public static final String CommentFile = "/file/manage";public   公共 static   静态 final   最后 String   字符串 CommentFile = /file/ management    管理"；
    /*根据手机号获取设备信息*/
    public static final String GetDeviceByPhone = "/device/getByPhone";GetDeviceByPhone = /device/getByPhone"；
    /*新增设备*/
    public static final String AddDevice = "/device/insertDevice";AddDevice = "/device/insertDevice"   “/设备/ insertDevice"；
    /*获取设备ip*/
    public static final String getDevice = "/device/report/list";getDevice = /device/report/list   列表"；
    /*修改设备状态*/
    public static final String updateDevice = "/device/updateDevice";



    public static MediaType JSON = MediaType.parse("application/json;charset=utf-8");public   公共 static   静态 MediaType JSON = MediaType.parse   解析("application/ JSON;charset=utf-8"" application / JSON charset = utf-8")；
    public static final String APP_ID = "";
    public static void request(String method, String url, RequestBody body, String token, Callback callback) {public   公共 static   静态 void   无效 request   请求(String   字符串 method   方法, String   字符串 url, RequestBody body   身体, String   字符串 token   令牌, Callback) {
        Request request = new Request.Builder().method(method, body).url(url).addHeader("Authorization", token).build();Request   请求 Request   请求 = new   新 Request   请求. builder   建设者（）。method(method, body).url(url).addHeader("Authorization" token).build()；
        new OkHttpClient().newCall(request).enqueue(callback);新OkHttpClient () .newCall(请求).enqueue   排队(回调);
    }

    public static void initRequest(final int id, String method, String url, RequestBody body, String token, final Handler handler) {initRequest（最终int id，字符串方法，字符串url， RequestBody主体，字符串令牌，最终Handler   饲养员 Handler   饲养员） {
        OkhttpUtils.request   请求   请求(method, url, body, token,    方法new Callback() {OkhttpUtils。request   请求(method   方法, url, body   身体   身体, token   令牌   令牌, new   新) Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {公共   令牌void   无效   身体 onFailure(@NonNull调用调用，@NonNull IOException) {
                Log   日志.e("onFailure: ", e.getMessage());Log.e("onFailure: ", e.getMessage())；
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response   响应 response) throws IOException {public   公共 void   无效 onResponse（@NonNull Call   呼叫 Call   呼叫, @NonNull Response   响应 Response   响应   响应   响应）抛出IOException {
                assert response.body   身体() != null;断言response.body   身体   身体() ！=零;
                String json = Objects.requireNonNull(response.body().string());String   字符串 json = Objects.requireNonNull(response.body   身体().string())；
                OkhttpUtils.sendMessage(json, id, handler);OkhttpUtils。sendMessage(json, id, handler   饲养员)；
            }
        });
    }

    private static void sendMessage(String json, int id, Handler handler) {sendMessage(String   字符串 json, int id, Handler   饲养员 Handler) {
        Message msg = new Message();
        msg.what = id;   味精。What = id；
        msg.obj = json;   味精。Obj = json；
        handler.sendMessage(msg);handler.sendMessage(味精);
    }

    public static RequestBody toBody(HashMap<String, Object> map) {public   公共 static   静态 RequestBody toBody(HashMap<String   字符串, Object> map) {
        JSONObject jsonObject = new JSONObject(map);
        return RequestBody.create(OkhttpUtils.JSON, jsonObject.toString());返回RequestBody.create   创建 (OkhttpUtils。JSON, jsonObject.toString ());
    }

    public static <T> T toData   数据,   数据,(String   字符串 json, Class<T> tClass) {public   公共 static   静态 <T> ttodata (String   字符串 json, Class<T> tClass) {
        Gson gson = new Gson();   Gson = new   新 Gson()；
        T t = gson.fromJson(json, tClass);T = gson.fromJson(json, tClass)；
        return t;   返回t;
    }
    /**
     * 将gson数组转换为对应的Java对象列表
     * @param json gson格式的字符串数组
     * @param tClass 对象类型
     * @param <T> 泛型
     * @return 对象列表
     */
    public static <T> List   列表<   列表   列表T> jsonToList(String   字符串 json, Class<T[]> tClass) {
        Gson gson = new Gson();   Gson = new   新 Gson()；
        T[] array = gson.fromJson(json, tClass);
        Type listType = new TypeToken<List<T>>() {}.getType();
        return gson.fromJson(gson.toJson(array), listType);
    }
                                                                                    }
