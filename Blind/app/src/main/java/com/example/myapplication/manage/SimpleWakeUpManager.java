package com.example.myapplication.manage;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;
import com.baidu.speech.asr.SpeechConstant;
import com.example.myapplication.utils.OkhttpUtils; // 假设你的ID都在这

import java.util.HashMap;
import java.util.Map;

public class SimpleWakeUpManager implements EventListener {

    private static final String TAG = "SimpleWakeUpManager";
    private EventManager wp;
    private Context context;
    private WakeUpListener listener;

    public interface WakeUpListener {
        void onSuccess(String word);
        void onError(String errorMsg);
    }

    public SimpleWakeUpManager(Context context, WakeUpListener listener) {
        this.context = context;
        this.listener = listener;
        wp = EventManagerFactory.create(context, "wp");
        wp.registerListener(this);
    }

    public void start() {
        Map<String, Object> params = new HashMap<>();

        // 唤醒词文件
        params.put(SpeechConstant.WP_WORDS_FILE, "assets:///WakeUp.bin");
        params.put(SpeechConstant.ACCEPT_AUDIO_VOLUME, false); // 不需要回调音量
        // 鉴权
        params.put(SpeechConstant.VAD, SpeechConstant.VAD_DNN); // 开启VAD

        String json = new JSONObject(params).toString();

        Log.d(TAG, "正在启动唤醒，参数: " + json);
        wp.send(SpeechConstant.WAKEUP_START, json, null, 0, 0);
    }

    public void stop() {
        if (wp != null) {
            wp.send(SpeechConstant.WAKEUP_STOP, null, null, 0, 0);
        }
    }

    public void release() {
        stop();
        if (wp != null) {
            wp.unregisterListener(this);
            wp = null;
        }
    }

    @Override
    public void onEvent(String name, String params, byte[] data, int offset, int length) {
        if (SpeechConstant.CALLBACK_EVENT_WAKEUP_SUCCESS.equals(name)) {
            try {
                JSONObject json = new JSONObject(params);
                int errorCode = json.optInt("errorCode");
                if (errorCode == 0) {
                    String word = json.optString("word");
                    if (listener != null) {
                        listener.onSuccess(word);
                    }
                } else {
                    if (listener != null) listener.onError("唤醒异常 Code: " + errorCode);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (SpeechConstant.CALLBACK_EVENT_WAKEUP_ERROR.equals(name)) {
            if (listener != null) listener.onError("唤醒引擎报错: " + params);
        }
    }
}