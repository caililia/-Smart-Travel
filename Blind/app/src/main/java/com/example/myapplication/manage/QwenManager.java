package   包 com.example.myapplication.manage;

import   进口   进口android.util.Log; android.util.Log;
import   进口进口androidx.annotation.NonNull; androidx.annotation.NonNull;
import   进口 okhttp3.*;
import   进口 org.json.JSONArray;
import   进口进口org.json.JSONObject; org.json.JSONObject;
import   进口进口java.io.IOException; java.io.IOException;
import   进口 java.util.concurrent.TimeUnit;

public class   类 QwenManager {
    // 替换为你自己的 API KEY
    private static   静态 final String   字符串 API_KEY = "";
    private static   静态 final String   字符串 URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static   静态 final String   字符串 TAG = "QwenManager";

    private OkHttpClient client;

    // 核心提示词：教大模型如何控制你的APP
    private static   静态 final String   字符串 SYSTEM_PROMPT =
            "你叫小黎，是一个安卓APP的语音助手。请根据用户的输入和[当前界面状态]，严格提取意图并返回JSON。\n" +
                    "不要返回markdown格式，只返回纯JSON字符串。\n\n" +
                    "===== 可用字段 =====\n" +
                    "type: 操作类型\n" +
                    "value: 提取的内容（如手机号、密码、验证码）\n" +
                    "reply: 你对用户的语音回复\n\n" +
                    "===== 登录页面可用type =====\n" +
                    "[CODE_LOGIN, ASK_PASSWORD, ASK_ACCOUNT, FILL_ACCOUNT, FILL_PASSWORD, LOGIN, REGISTER, CONFIRM, DENY, UNKNOWN]\n\n" +
                    "===== 注册页面可用type =====\n" +
                    "[FILL_PHONE, FILL_PASSWORD, FILL_CODE, GET_CODE, DO_REGISTER, GO_LOGIN, ASK_PHONE, ASK_PASSWORD, ASK_CODE, CONFIRM, DENY, UNKNOWN]\n\n" +
                    "===== 【最高优先级规则 - 确认/否认】=====\n" +
                    "1. [关键] 如果当前状态包含 [等待用户确认]：\n" +
                    "   - 用户说 '确定'、'对的'、'没问题'、'是的'、'对'、'好的'、'可以'、'没错' -> 返回 type: CONFIRM\n" +
                    "   - 用户说 '不对'、'错了'、'重输'、'不是'、'重新'、'取消' -> 返回 type: DENY\n\n" +
                    "===== 【登录页面规则】=====\n" +
                    "当状态包含 [密码登录页] 或 [手机验证码登录页] 时使用以下规则：\n\n" +
                    "2. 用户提到 '忘记密码'、'忘了'、'验证码登录'、'手机号登录'、'切换验证码'：\n" +
                    "   -> type: CODE_LOGIN, reply: '好的，已切换到验证码登录'\n\n" +
                    "3. 用户提到 '密码登录'、'账号登录'、'使用密码'、'切换密码'：\n" +
                    "   -> type: ASK_PASSWORD, reply: '好的，已切换到密码登录'\n\n" +
                    "4. 用户说 '注册' 或 '去注册' 或 '我要注册'：\n" +
                    "   -> type: REGISTER\n\n" +
                    "5. 用户想登录且[账号为空] -> ASK_ACCOUNT\n\n" +
                    "6. 用户提供11位纯数字(手机号) -> FILL_ACCOUNT, value填手机号\n\n" +
                    "7. 用户提供密码或验证码 -> FILL_PASSWORD, value填密码/验证码\n\n" +
                    "8. 用户说 '登录' 且账号密码已填 -> LOGIN\n\n" +
                    "===== 【注册页面规则】=====\n" +
                    "当状态包含 [注册页面] 时使用以下规则：\n\n" +
                    "9. 用户提供11位手机号/账号：\n" +
                    "   -> type: FILL_PHONE, value: 手机号, reply: '好的'\n\n" +
                    "10. 用户说 '密码是...' 或 '密码设置为...' 或提供密码：\n" +
                    "    -> type: FILL_PASSWORD, value: 密码内容\n\n" +
                    "11. 用户说4-6位数字且状态显示在等验证码：\n" +
                    "    -> type: FILL_CODE, value: 验证码\n\n" +
                    "12. 用户说 '获取验证码'、'发送验证码'、'发验证码'：\n" +
                    "    -> type: GET_CODE\n\n" +
                    "13. 用户说 '注册'、'帮我注册'、'完成注册'：\n" +
                    "    -> type: REGISTER\n\n" +
                    "14. 用户说 '登录'、'去登录'、'返回登录'、'已有账号'：\n" +
                    "    -> type: LOGIN, reply: '好的，正在跳转到登录页面'\n\n" +
                    "15. 用户问 '怎么注册' 或需要引导：\n" +
                    "    - 如果手机号为空 -> ASK_PHONE, reply: '请先告诉我您的手机号'\n" +
                    "    - 如果密码为空 -> ASK_PASSWORD, reply: '请告诉我您想设置的密码'\n" +
                    "    - 如果验证码为空 -> ASK_CODE, reply: '请告诉我验证码'\n\n" +
                    "===== 【数字转换规则】=====\n" +
                    "- '幺' -> 1, '两' -> 2, '零' -> 0\n" +
                    "- '一二三四五六七八九' -> '123456789'\n" +
                    "- 去除所有空格、逗号、顿号\n\n" +

                    "===== 【示例】=====\n" +
                    "输入：'我忘记密码了' [密码登录页]\n" +
                    "输出：{\"type\":\"CODE_LOGIN\", \"value\":\"\", \"reply\":\"已切换到验证码登录\"}\n\n" +

                    "输入：'13800138000' [登录页, 账号空]\n" +
                    "输出：{\"type\":\"FILL_ACCOUNT\", \"value\":\"13800138000\", \"reply\":\"好的\"}\n\n" +

                    "输入：'对的' [等待用户确认]\n" +
                    "输出：{\"type\":\"CONFIRM\", \"value\":\"\", \"reply\":\"好的\"}\n\n" +

                    "输入：'手机号/账号是13912345678' [注册页面, 手机号为空]\n" +
                    "输出：{\"type\":\"FILL_PHONE\", \"value\":\"13912345678\", \"reply\":\"好的\"}\n\n" +

                    "输入：'密码设置成abc123' [注册页面]\n" +
                    "输出：{\"type\":\"FILL_PASSWORD\", \"value\":\"abc123\", \"reply\":\"好的\"}\n\n" +

                    "输入：'发送验证码' [注册页面]\n" +
                    "输出：{\"type\":\"GET_CODE\", \"value\":\"\", \"reply\":\"好的，正在发送验证码\"}\n\n" +

                    "输入：'帮我注册' [注册页面, 信息已填完]\n" +
                    "输出：{\"type\":\"DO_REGISTER\", \"value\":\"\", \"reply\":\"好的，正在为您注册\"}\n\n" +

                    "输入：'我要去登录' [注册页面]\n" +
                    "输出：{\"type\":\"GO_LOGIN\", \"value\":\"\", \"reply\":\"好的，正在跳转\"}\n\n" +

                    "只有当完全无法理解用户意图时，才返回 type: UNKNOWN";

    public interface QwenCallback {
        void onSuccess(String jsonResponse);
        void onError(String error);
    }

    public QwenManager() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void sendMessage(String userText, QwenCallback callback) {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        // 构建请求体
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("model", "qwen-omni-turbo"); // 使用通义千问模型

            JSONArray messages = new JSONArray();

            // 添加系统预设
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM_PROMPT);
            messages.put(systemMsg);

            // 添加用户消息
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userText);
            messages.put(userMsg);

            requestBody.put("messages", messages);

        } catch (Exception e) {
            e.printStackTrace();
        }

        Request request = new Request.Builder()
                .url(URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(JSON, requestBody.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String respStr = response.body().string();
                        JSONObject jsonObject = new JSONObject(respStr);
                        // 提取大模型回复的内容
                        String content = jsonObject.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                        callback.onSuccess(content);
                    } catch (Exception e) {
                        callback.onError("解析失败: " + e.getMessage());
                    }
                } else {
                    callback.onError("请求失败 code: " + response.code());
                }
            }
        });
    }

    public void sendMessage2(String prompt, QwenCallback callback) {
        try {
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "qwen-vl-plus"); // 使用支持图片的模型

            JSONArray messages = new JSONArray();

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");

            // 检查是否包含图片
            if (prompt.contains("base64")) {
                // 解析图片和文本
                String[] parts = prompt.split("图片数据\\(base64\\): ");
                if (parts.length > 1) {
                    String[] imageAndText = parts[1].split("\n\n用户的问题：");
                    if (imageAndText.length > 1) {
                        String base64Image = imageAndText[0].trim();
                        String userQuestion = imageAndText[1].split("\n\n")[0];

                        // 构建多模态消息
                        JSONArray content = new JSONArray();

                        // 添加文本
                        JSONObject textContent = new JSONObject();
                        textContent.put("type", "text");
                        textContent.put("text", "请描述这张图片。用户问题：" + userQuestion);
                        content.put(textContent);

                        // 添加图片
                        JSONObject imageContent = new JSONObject();
                        imageContent.put("type", "image_url");
                        JSONObject imageUrl = new JSONObject();
                        imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
                        imageContent.put("image_url", imageUrl);
                        content.put(imageContent);

                        userMsg.put("content", content);
                    }
                }
            } else {
                userMsg.put("content", prompt);
            }

            messages.put(userMsg);
            requestBody.put("messages", messages);

            String jsonBody = requestBody.toString();
            Log.d(TAG, "请求体: " + jsonBody);

            Request request = new Request.Builder()
                    .url(URL)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(JSON, jsonBody))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "请求失败", e);
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String respStr = response.body() != null ? response.body().string() : "";
                        Log.d(TAG, "响应: " + respStr);

                        if (response.isSuccessful()) {
                            JSONObject jsonObject = new JSONObject(respStr);
                            String content = jsonObject.getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content");
                            callback.onSuccess(content);
                        } else {
                            callback.onError("HTTP错误: " + response.code());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析失败", e);
                        callback.onError("解析失败: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "构建请求失败", e);
            callback.onError("构建请求失败: " + e.getMessage());
        }
    }
}
