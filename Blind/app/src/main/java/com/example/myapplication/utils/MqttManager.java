package com.example.myapplication.utils;

import android.content.Context;
import android.util.Log;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;

public class MqttManager {

    private static final String TAG = "MqttManager";
    private final MqttAndroidClient mqttClient;
    private boolean isConnected = false;
    private OnMessageListener messageListener;
    private OnConnectionListener connectionListener;

    public MqttManager(Context context, String brokerUrl, String clientId, Ack ack) {
        mqttClient = new MqttAndroidClient(context, brokerUrl, clientId, ack);
    }

    /** 连接 MQTT Broker **/
    public void connect(String username, String password) {
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);  // 自动重连 - 解决连接不稳定的问题
            options.setCleanSession(true);
            options.setKeepAliveInterval(60);     // 心跳间隔60秒
            options.setConnectionTimeout(30);     // 连接超时30秒

            if (username != null) options.setUserName(username);
            if (password != null) options.setPassword(password.toCharArray());

            // 设置遗嘱消息 - 设备异常断开时通知
            options.setWill("device/status", "offline".getBytes(), 1, false);

            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    isConnected = true;
                    Log.i(TAG, "连接成功: " + serverURI + ", 是否重连: " + reconnect);

                    // 连接成功后订阅设备状态主题
                    subscribe("device/+/status");  // + 通配符匹配所有设备
                    subscribe("device/+/command/response");

                    if (connectionListener != null) {
                        connectionListener.onConnected(reconnect);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    isConnected = false;
                    Log.e(TAG, "连接丢失: " + cause);
                    if (connectionListener != null) {
                        connectionListener.onConnectionLost(cause);
                    }
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    Log.i(TAG, "收到消息: " + topic + " -> " + payload);
                    if (messageListener != null) {
                        messageListener.onMessage(topic, payload);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    Log.i(TAG, "消息发送完成");
                }
            });

            Log.i(TAG, "正在连接 MQTT...");
            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "MQTT 连接成功");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "连接失败: " + exception);
                    if (connectionListener != null) {
                        connectionListener.onConnectFailed(exception);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "连接异常: " + e);
        }
    }

    /** 订阅主题 **/
    public void subscribe(String topic) {
        try {
            if (mqttClient.isConnected()) {
                mqttClient.subscribe(topic, 1);
                Log.i(TAG, "已订阅主题: " + topic);
            } else {
                Log.w(TAG, "未连接，无法订阅: " + topic);
            }
        } catch (Exception e) {
            Log.e(TAG, "订阅失败: " + e);
        }
    }

    /** 取消订阅 **/
    public void unsubscribe(String topic) {
        try {
            if (mqttClient.isConnected()) {
                mqttClient.unsubscribe(topic);
                Log.i(TAG, "已取消订阅: " + topic);
            }
        } catch (Exception e) {
            Log.e(TAG, "取消订阅失败: " + e);
        }
    }

    /** 发布消息 **/
    public void publish(String topic, String message) {
        publish(topic, message, 1, false);
    }

    /** 发布消息（带QoS）**/
    public void publish(String topic, String message, int qos, boolean retained) {
        try {
            if (!mqttClient.isConnected()) {
                Log.w(TAG, "未连接，无法发送");
                if (connectionListener != null) {
                    connectionListener.onPublishFailed(topic, message);
                }
                return;
            }
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(qos);
            mqttMessage.setRetained(retained);
            mqttClient.publish(topic, mqttMessage);
            Log.i(TAG, "已发布消息: " + topic + " -> " + message);
        } catch (Exception e) {
            Log.e(TAG, "发布失败: " + e);
        }
    }

    /** 发送设备状态**/
    public void sendDeviceStatus(String deviceId, int status, JSONObject extraData) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("deviceId", deviceId);
            payload.put("status", status);
            payload.put("timestamp", System.currentTimeMillis());
            if (extraData != null) {
                payload.put("data", extraData);
            }
            publish("device/" + deviceId + "/status", payload.toString());
        } catch (Exception e) {
            Log.e(TAG, "发送设备状态失败: " + e);
        }
    }

    /** 发送控制指令到设备 **/
    public void sendCommand(String deviceId, String command, JSONObject params) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("command", command);
            payload.put("params", params);
            payload.put("timestamp", System.currentTimeMillis());
            publish("device/" + deviceId + "/command", payload.toString());
        } catch (Exception e) {
            Log.e(TAG, "发送指令失败: " + e);
        }
    }

    /** 检查连接状态 **/
    public boolean isConnected() {
        return mqttClient.isConnected() && isConnected;
    }

    /** 重连 **/
    public void reconnect() {
        try {
            mqttClient.reconnect();
        } catch (Exception e) {
            Log.e(TAG, "重连失败: " + e);
        }
    }

    /** 断开连接 **/
    public void disconnect() {
        try {
            isConnected = false;
            mqttClient.disconnect();
            Log.i(TAG, "已断开连接");
        } catch (Exception e) {
            Log.e(TAG, "断开失败: " + e);
        }
    }

    /** 释放资源 **/
    public void close() {
        try {
            disconnect();
            mqttClient.close();
        } catch (Exception e) {
            Log.e(TAG, "关闭失败: " + e);
        }
    }

    // ==================== 回调接口 ====================

    public interface OnMessageListener {
        void onMessage(String topic, String message);
    }

    public interface OnConnectionListener {
        void onConnected(boolean isReconnect);
        void onConnectionLost(Throwable cause);
        void onConnectFailed(Throwable exception);
        void onPublishFailed(String topic, String message);
    }

    public void setOnMessageListener(OnMessageListener listener) {
        this.messageListener = listener;
    }

    public void setOnConnectionListener(OnConnectionListener listener) {
        this.connectionListener = listener;
    }
}