package com.example.myapplication.manage;

import android.content.Context;
import android.util.Log;
import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;
import com.baidu.speech.asr.SpeechConstant;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 识别管理器：负责在唤醒后，听懂具体的指令
 */
public class SimpleAsrManager implements EventListener {

    private EventManager asr;
    private OnAsrListener listener;

    public interface OnAsrListener {
        void onResult(String text); // 识别结果
        void onError(String error);
    }

    public SimpleAsrManager(Context context, OnAsrListener listener) {
        this.listener = listener;
        asr = EventManagerFactory.create(context, "asr");
        asr.registerListener(this);
    }

    public void setListener(OnAsrListener listener) {
        this.listener = listener;
    }

    public OnAsrListener getListener() {
        return this.listener;
    }

    public void start() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(SpeechConstant.ACCEPT_AUDIO_VOLUME, false);
        // 1537: 普通话输入法模型，适合短指令
        params.put(SpeechConstant.PID, 1537);
        // VAD: 开启自动断句，用户说完话自动停止录音
        params.put(SpeechConstant.VAD, SpeechConstant.VAD_DNN);
        params.put(SpeechConstant.VAD_ENDPOINT_TIMEOUT, 2000);

        String json = new JSONObject(params).toString();
        asr.send(SpeechConstant.ASR_START, json, null, 0, 0);
    }

    public void stop() {
        if (asr != null) asr.send(SpeechConstant.ASR_STOP, null, null, 0, 0);
    }

    public void release() {
        stop();
        if (asr != null) {
            asr.unregisterListener(this);
            asr = null;
        }
    }

    @Override
    public void onEvent(String name, String params, byte[] data, int offset, int length) {
        // 打印日志方便调试
        if (params != null && !params.isEmpty()) {
            Log.d("ASR_Event", "Name: " + name + " | Params: " + params);
        }

        try {
            if (SpeechConstant.CALLBACK_EVENT_ASR_PARTIAL.equals(name)) {
                JSONObject json = new JSONObject(params);
                String resultType = json.optString("result_type");

                if ("final_result".equals(resultType)) {
                    String bestResult = json.optString("best_result");

                    if (bestResult == null || bestResult.isEmpty()) {
                        JSONArray results = json.optJSONArray("results_recognition");
                        if (results != null && results.length() > 0) {
                            bestResult = results.getString(0);
                        }
                    }

                    // 回调结果
                    if (bestResult != null && !bestResult.isEmpty()) {
                        listener.onResult(bestResult);
                    }
                }
            }

            else if (SpeechConstant.CALLBACK_EVENT_ASR_FINISH.equals(name)) {
                JSONObject json = new JSONObject(params);
                int error = json.optInt("error");
                if (error != 0) {
                    if (listener != null) listener.onError("错误码: " + error);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}