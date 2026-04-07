package com.example.volunteer.socket;

import android.util.Log;

import com.example.volunteer.utils.OkhttpUtils;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;

public class WebSocketManager {
    private static final String TAG = "WebSocketManager";
    private static WebSocketManager instance;
    private WebSocketClient webSocketClient;
    private WebSocketListener listener;
    private boolean isConnected = false;

    public interface WebSocketListener {
        void onRoomDataChanged(String message);
        void onConnectionStatusChanged(boolean connected);
        void onError(String message);
        void onMessageReceived(String type, String message);
    }

    private WebSocketManager() {}

    public static WebSocketManager getInstance() {
        if (instance == null) {
            instance = new WebSocketManager();
        }
        return instance;
    }

    public void setListener(WebSocketListener listener) {
        this.listener = listener;
    }

    public void connect() {
        try {
            if (webSocketClient != null) {
                webSocketClient.close();
            }

            URI uri = URI.create(OkhttpUtils.WebSocketUrl);
            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.d(TAG, "WebSocket连接已建立");
                    isConnected = true;
                    if (listener != null) {
                        listener.onConnectionStatusChanged(true);
                    }

                    // 连接成功后自动订阅房间更新
                    subscribeRooms();
                }

                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "收到WebSocket消息: " + message);
                    if (listener != null) {
                        listener.onRoomDataChanged(message);

                        // 解析消息类型
                        try {
                            JSONObject jsonMessage = new JSONObject(message);
                            String type = jsonMessage.optString("type", "");
                            String msg = jsonMessage.optString("message", "");
                            listener.onMessageReceived(type, msg);
                        } catch (JSONException e) {
                            Log.e(TAG, "解析消息失败", e);
                        }
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "WebSocket连接关闭: " + reason + ", code: " + code);
                    isConnected = false;
                    if (listener != null) {
                        listener.onConnectionStatusChanged(false);
                    }
                    // 自动重连
                    attemptReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket错误: " + ex.getMessage());
                    isConnected = false;
                    if (listener != null) {
                        listener.onError(ex.getMessage());
                    }
                }
            };

            Log.d(TAG, "尝试连接WebSocket: " + OkhttpUtils.WebSocketUrl);
            webSocketClient.connect();

        } catch (Exception e) {
            Log.e(TAG, "WebSocket连接异常: " + e.getMessage());
            if (listener != null) {
                listener.onError(e.getMessage());
            }
        }
    }

    private void attemptReconnect() {
        Log.d(TAG, "5秒后尝试重新连接...");
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isConnected) {
                Log.d(TAG, "执行重连...");
                connect();
            }
        }, 5000);
    }

    public void disconnect() {
        Log.d(TAG, "主动断开WebSocket连接");
        if (webSocketClient != null) {
            webSocketClient.close();
            webSocketClient = null;
        }
        isConnected = false;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void sendMessage(String message) {
        if (webSocketClient != null && isConnected) {
            webSocketClient.send(message);
            Log.d(TAG, "发送消息: " + message);
        } else {
            Log.w(TAG, "WebSocket未连接，无法发送消息");
        }
    }

    /**
     * 订阅房间更新
     */
    public void subscribeRooms() {
        try {
            JSONObject subscribeMessage = new JSONObject();
            subscribeMessage.put("type", "SUBSCRIBE_ROOMS");
            subscribeMessage.put("timestamp", System.currentTimeMillis());

            sendMessage(subscribeMessage.toString());
            Log.d(TAG, "发送房间订阅请求");
        } catch (JSONException e) {
            Log.e(TAG, "创建订阅消息失败", e);
        }
    }

    /**
     * 发送 Ping 消息测试连接
     */
    public void sendPing() {
        try {
            JSONObject pingMessage = new JSONObject();
            pingMessage.put("type", "PING");
            pingMessage.put("timestamp", System.currentTimeMillis());

            sendMessage(pingMessage.toString());
            Log.d(TAG, "发送 Ping 消息");
        } catch (JSONException e) {
            Log.e(TAG, "创建 Ping 消息失败", e);
        }
    }

    /**
     * 获取连接状态详情
     */
    public String getConnectionStatus() {
        if (webSocketClient == null) {
            return "未初始化";
        }

        switch (webSocketClient.getReadyState()) {
            case OPEN:
                return "已连接";
            case CLOSING:
                return "关闭中";
            case CLOSED:
                return "已关闭";
            default:
                return "未知状态";
        }
    }
}