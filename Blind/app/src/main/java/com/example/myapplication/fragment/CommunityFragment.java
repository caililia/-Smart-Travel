package com.example.myapplication.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.example.myapplication.activity.login.LoginActivity;
import com.example.myapplication.data.ListComment;
import com.example.myapplication.data.UserData;
import com.example.myapplication.manage.QwenManager;
import com.example.myapplication.manage.SimpleAsrManager;
import com.example.myapplication.manage.SimpleWakeUpManager;
import com.example.myapplication.utils.OkhttpUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CommunityFragment extends Fragment {

    private static final String TAG = "CommunityFragment";
    private static final int FILE_UPLOAD_REQUEST_CODE = 1002;

    private WebView webView;
    private Gson gson = new Gson();
    private OkHttpClient okHttpClient;
    private String phone;
    private String userId;
    private ListComment listComment;

    // 语音助手相关
    private SimpleWakeUpManager wakeUpManager;
    private SimpleAsrManager asrManager;
    private QwenManager qwenManager;
    private boolean isVoiceActive = false;
    private boolean isWaitingForComment = false;
    private Handler voiceHandler = new Handler();
    private Handler enterHandler = new Handler();
    private TextToSpeech tts;
    private String textContent = "";
    private String isOK;

    // 存储上传成功的文件URL
    private List<String> uploadedPictureUrls = new ArrayList<>();
    private List<String> uploadedVideoUrls = new ArrayList<>();
    private List<String> uploadedVoiceUrls = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_community, container, false);

        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        qwenManager = new QwenManager();
        initVoiceAssistant();
        initWebView(view);
        loadCommunityHtml();
        initData();

        return view;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView(View view) {
        webView = view.findViewById(R.id.webview_community);
        android.webkit.WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setDefaultTextEncodingName("UTF-8");
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                fetchComments();
            }
        });

        webView.addJavascriptInterface(new CommunityJsBridge(), "AndroidBridge");
    }

    private void initVoiceAssistant() {
        if (getActivity() == null) return;

        // 初始化ASR
        asrManager = new SimpleAsrManager(getActivity(), new SimpleAsrManager.OnAsrListener() {
            @Override
            public void onResult(String text) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isVoiceActive) {
                            // 处理其他语音指令
                            processCommand(text);
                            if (isVoiceActive && asrManager != null) {
                                asrManager.start();
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "识别错误: " + error);
                if (isWaitingForComment) {
                    isWaitingForComment = false;
                    showToast("未检测到语音，请重试");
                }
            }
        });

        // 初始化唤醒
        wakeUpManager = new SimpleWakeUpManager(getActivity(), new SimpleWakeUpManager.WakeUpListener() {
            @Override
            public void onSuccess(String word) {
                Log.d(TAG, "唤醒成功: " + word);
                if (getActivity() != null) {
                    speak("我在，有什么可以帮您？");
                }
                isVoiceActive = true;
                wakeUpManager.stop();
                if (asrManager != null) {
                    asrManager.start();
                }
                resetAutoSleepTimer();
            }

            @Override
            public void onError(String errorMsg) {
                Log.e(TAG, "唤醒失败: " + errorMsg);
            }
        });

        wakeUpManager.start();

        // 进入页面1.5秒后播报
        enterHandler.postDelayed(() -> {
            if (getActivity() != null) {
                speak("当前进入社区页面");
                // 等待评论加载完成后播报
                waitForCommentsAndSpeak();
            }
        }, 1500);
    }

    private void resetAutoSleepTimer() {
        voiceHandler.removeCallbacks(autoSleepRunnable);
        voiceHandler.postDelayed(autoSleepRunnable, 15000); // 15秒无操作自动休眠
    }

    private Runnable autoSleepRunnable = new Runnable() {
        @Override
        public void run() {
            if (isVoiceActive) {
                isVoiceActive = false;
                if (wakeUpManager != null) wakeUpManager.start();
                if (asrManager != null) asrManager.stop();
                showToast("小黎已休眠");
                Log.d(TAG, "小黎自动休眠");
            }
        }
    };

    private void waitForCommentsAndSpeak() {
        // 延迟等待评论加载
        voiceHandler.postDelayed(() -> {
            if (listComment != null && listComment.getData() != null) {
                List<ListComment.DataBean.Data1Bean> comments = listComment.getData().getData1();
                if (comments != null && !comments.isEmpty()) {
                    speakComments(comments);
                } else {
                    speak("暂无评论内容");
                }
            } else {
                speak("暂无评论内容");
            }
        }, 1000);
    }

    private void speakComments(List<ListComment.DataBean.Data1Bean> comments) {
        if (comments == null || comments.isEmpty()) return;

        // 获取今天的日期
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(new Date());

        // 筛选今天的评论
        List<ListComment.DataBean.Data1Bean> todayComments = new ArrayList<>();
        for (ListComment.DataBean.Data1Bean comment : comments) {
            String createTime = comment.getCreateTime();
            if (createTime != null && createTime.startsWith(today)) {
                todayComments.add(comment);
            }
        }

        // 按照创建时间倒序排序（最新的在前）
        List<ListComment.DataBean.Data1Bean> sortedComments = new ArrayList<>(comments);
        sortedComments.sort((c1, c2) -> {
            String time1 = c1.getCreateTime();
            String time2 = c2.getCreateTime();
            if (time1 == null && time2 == null) return 0;
            if (time1 == null) return 1;
            if (time2 == null) return -1;
            return time2.compareTo(time1);
        });

        // 决定播报哪些评论
        List<ListComment.DataBean.Data1Bean> commentsToSpeak;
        int delay = 0;

        if (!todayComments.isEmpty()) {
            // 对今天的评论也进行排序
            todayComments.sort((c1, c2) -> {
                String time1 = c1.getCreateTime();
                String time2 = c2.getCreateTime();
                if (time1 == null && time2 == null) return 0;
                if (time1 == null) return 1;
                if (time2 == null) return -1;
                return time2.compareTo(time1);
            });
            commentsToSpeak = todayComments.size() > 3 ? todayComments.subList(0, 3) : todayComments;
            // 先播报提示语
            speak("今天有" + commentsToSpeak.size() + "条新评论");
            delay = 2000; // 等待2秒后播报评论
        } else {
            commentsToSpeak = sortedComments.size() > 3 ? sortedComments.subList(0, 3) : sortedComments;
            // 先播报提示语
            speak("以下是最近的评论");
            delay = 2000; // 等待2秒后播报评论
        }

        // 延迟播报每条评论
        for (int i = 0; i < commentsToSpeak.size(); i++) {
            ListComment.DataBean.Data1Bean comment = commentsToSpeak.get(i);
            long id = comment.getUserId();
            String userNickname = getUserIdLastFour(id);
            String content = comment.getContent();

            StringBuilder speakText = new StringBuilder();
            speakText.append(userNickname).append("说：").append(content);

            // 如果有图片，描述图片内容
            if (comment.getPictureUrl() != null && !comment.getPictureUrl().toString().isEmpty()) {
                speakText.append("，并分享了图片");
                String pictureUrlStr = comment.getPictureUrl().toString();
                String[] pictures = pictureUrlStr.split(",");
                if (pictures.length > 1) {
                    speakText.append(pictures.length).append("张");
                }
            }

            // 如果有视频
            if (comment.getVideoUrl() != null && !comment.getVideoUrl().toString().isEmpty()) {
                speakText.append("，并分享了视频");
                String videoUrlStr = comment.getVideoUrl().toString();
                String[] videos = videoUrlStr.split(",");
                if (videos.length > 1) {
                    speakText.append(videos.length).append("个");
                }
            }

            // 如果有语音
            if (comment.getVoiceUrl() != null && !comment.getVoiceUrl().toString().isEmpty()) {
                speakText.append("，并分享了语音");
            }

            final int index = i;
            final String finalSpeakText = speakText.toString();
            // 每条评论间隔3秒，加上之前的延迟
            voiceHandler.postDelayed(() -> speak(finalSpeakText), delay + (index * 3000));
        }
    }

    private String getUserIdLastFour(long userId) {
        int lastFour = (int) (Math.abs(userId) % 10000);
        // 如果后四位不足4位，补0显示
        return "用户" + String.format("%04d", lastFour);
    }

    private void processCommand(String text) {
        if (TextUtils.isEmpty(text)) return;
        Log.d(TAG, "用户说: " + text);
        // 判断是否要发评论
        if (text.contains("发评论") ||
                text.contains("分享我的生活") ||
                text.contains("分享生活") ||
                text.contains("我要评论") ||
                text.contains("写评论") ||
                text.contains("发布") ||
                text.contains("分享评论") ||      // 新增
                text.contains("我想分享") ||       // 新增
                text.contains("想要分享") ||       // 新增
                (text.contains("分享") && text.contains("评论")) || // 新增：同时包含"分享"和"评论"
                (text.contains("发") && text.contains("评论"))) {   // 新增：同时包含"发"和"评论"

            isWaitingForComment = true;
            // 重置休眠计时器
            handleVoiceComment();

            return;
        }

        if (text.contains("休眠") || text.contains("退出") || text.contains("再见")) {
            speak("好的，小黎休眠了");
            isVoiceActive = false;
            if (wakeUpManager != null) wakeUpManager.start();
            if (asrManager != null) asrManager.stop();
            voiceHandler.removeCallbacks(autoSleepRunnable);
            return;
        }

        // 判断是否要刷新
        if (text.contains("刷新") || text.contains("最新评论")) {
            speak("正在为您刷新");
            fetchComments();
            resetAutoSleepTimer();
            return;
        }

        // 其他指令用大模型处理
        qwenManager.sendMessage(text, new QwenManager.QwenCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                handleAiResponse(jsonResponse);
            }

            @Override
            public void onError(String error) {
                speak("网络好像有点问题");
            }
        });
        resetAutoSleepTimer();
    }

    private void handleVoiceComment() {
        resetAutoSleepTimer();
        isWaitingForComment = false;
        showCommentDialog();
        resetAutoSleepTimer();
    }

    private void showCommentDialog() {
        if (getActivity() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("分享你的生活");

        // 创建布局
        View dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_comment, null);
        EditText etContent = dialogView.findViewById(R.id.et_comment_content);
        asrManager = new SimpleAsrManager(getActivity(), new SimpleAsrManager.OnAsrListener() {
            @Override
            public void onResult(String text) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isVoiceActive) {
                            // 处理其他语音指令
                            resetAutoSleepTimer();
                            isWaitingForComment = false;
                            etContent.setText(text);
                            etContent.setSelection(text.length());
                            publishComment(text);
                            //isOK(text);
                            if (isVoiceActive && asrManager != null) {
                                asrManager.start();
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "识别错误: " + error);
                if (isWaitingForComment) {
                    isWaitingForComment = false;
                    showToast("未检测到语音，请重试");
                }
            }
        });

        builder.setView(dialogView);
       /* builder.setPositiveButton("发布", (dialog, which) -> {
            String content = etContent.getText().toString().trim();
            if (!TextUtils.isEmpty(content)) {
                // 调用发布方法
                publishComment(content);
            } else {
                showToast("请输入内容");
            }
        });*/
        builder.setPositiveButton("发布", null);
        builder.setNegativeButton("取消", null);
        builder.show();
    }

   /* private void isOK(String text) {
        speak("您的评论是" + text + "。确认吗？");
        asrManager = new SimpleAsrManager(getActivity(), new SimpleAsrManager.OnAsrListener() {
            @Override
            public void onResult(String text) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isVoiceActive) {
                            resetAutoSleepTimer();
                            isWaitingForComment = false;
                            if (text.contains("确认")){
                                publishComment(textContent);
                            }
                            if (isVoiceActive && asrManager != null) {
                                asrManager.start();
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "识别错误: " + error);
                if (isWaitingForComment) {
                    isWaitingForComment = false;
                    showToast("未检测到语音，请重试");
                }
            }
        });
    }*/

    private void publishComment(String content) {
        if (TextUtils.isEmpty(userId)) {
            showToast("用户信息未加载");
            return;
        }

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("userId", userId)
                .addFormDataPart("content", content);

        Request request = new Request.Builder()
                .url(OkhttpUtils.URL + OkhttpUtils.AddComment)
                .post(builder.build())
                .build();

        showToast("正在发布...");

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showToast("发布失败：" + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            showToast("发布成功！");
                            refreshAllData(); // 刷新评论列表
                            // 播报发布成功
                            voiceHandler.postDelayed(() -> speak("发布成功"), 500);
                        } else {
                            showToast("发布失败");
                        }
                    });
                }
            }
        });
    }

    /**
     * 刷新所有数据
     */
    private void refreshAllData() {
        Log.d(TAG, "========== 刷新所有数据 ==========");

        // 1. 重新获取用户信息（确保 userId 有效）
        fetchUserInfo();

        // 2. 重新获取评论列表
        fetchComments();

        // 3. 通知 WebView 刷新
        if (webView != null) {
            // 方法一：重新加载页面
            // loadCommunityHtml();

            // 方法二：通过 JavaScript 通知 Vue 刷新（推荐）
            String js = "javascript:if(window.refreshComments) window.refreshComments();";
            webView.evaluateJavascript(js, null);
        }
    }

    private void handleAiResponse(String jsonStr) {
        try {
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.replace("```json", "").replace("```", "");
            }
            com.google.gson.JsonObject result = gson.fromJson(jsonStr, com.google.gson.JsonObject.class);
            String reply = result.get("reply").getAsString();
            if (!TextUtils.isEmpty(reply)) {
                speak(reply);
            }
        } catch (Exception e) {
            Log.e(TAG, "解析AI响应失败", e);
        }
    }

    private void speak(String text) {
        if (getActivity() == null) return;

        // 使用 TTS 播报
        tts = new android.speech.tts.TextToSpeech(getActivity(), status -> {
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINESE);
                tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        // 同时显示Toast
        showToast("小黎: " + text);
    }

    private void initData() {
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("phone", MODE_PRIVATE);
        phone = sharedPreferences.getString("phone", null);

        if (TextUtils.isEmpty(phone)) {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            Toast.makeText(getContext(), "登录信息已过期，请重新登录", Toast.LENGTH_SHORT).show();
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().finish();
            }
            return;
        }
        fetchUserInfo();
    }

    private void fetchUserInfo() {
        OkhttpUtils.request("GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取用户信息失败", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() != null) {
                    String json = response.body().string();
                    UserData userData = OkhttpUtils.toData(json, UserData.class);
                    if (userData != null && userData.getData() != null) {
                        userId = String.valueOf(userData.getData().getUserId());
                        Log.d(TAG, "用户ID: " + userId);
                    }
                }
            }
        });
    }

    private void fetchComments() {
        webView.postDelayed(() -> {
            OkhttpUtils.request("GET", OkhttpUtils.URL + OkhttpUtils.listComment, null, "", new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "获取评论失败", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.body() != null) {
                        String json = response.body().string();
                        try {
                            listComment = OkhttpUtils.toData(json, ListComment.class);
                            if (listComment != null && listComment.getData() != null) {
                                List<ListComment.DataBean.Data1Bean> comments = listComment.getData().getData1();
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> sendCommentsToVue(comments));
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "解析评论数据失败", e);
                        }
                    }
                }
            });
        }, 500);
    }

    private void sendCommentsToVue(List<ListComment.DataBean.Data1Bean> comments) {
        if (webView == null || comments == null) {
            return;
        }

        // 按照创建时间倒序排序
        List<ListComment.DataBean.Data1Bean> sortedComments = new ArrayList<>(comments);
        sortedComments.sort((c1, c2) -> {
            String time1 = c1.getCreateTime();
            String time2 = c2.getCreateTime();
            if (time1 == null && time2 == null) return 0;
            if (time1 == null) return 1;
            if (time2 == null) return -1;
            return time2.compareTo(time1);
        });

        List<Map<String, Object>> posts = new ArrayList<>();
        String baseFileUrl = OkhttpUtils.URL + OkhttpUtils.CommentFile + "/";

        for (ListComment.DataBean.Data1Bean comment : sortedComments) {
            try {
                Map<String, Object> post = new HashMap<>();
                post.put("username", "用户" + comment.getUserId());
                post.put("time", formatTime(comment.getCreateTime()));
                post.put("content", comment.getContent());
                post.put("likeCount", comment.getLikeCount());
                post.put("commentCount", comment.getReplyCount());

                // 处理图片
                if (comment.getPictureUrl() != null && !comment.getPictureUrl().toString().isEmpty()) {
                    String pictureUrlStr = comment.getPictureUrl().toString();
                    pictureUrlStr = pictureUrlStr.replace("/picture/", "");
                    String[] fileNames = pictureUrlStr.split(",");
                    List<String> fullUrls = new ArrayList<>();
                    for (String fileName : fileNames) {
                        if (!fileName.trim().isEmpty()) {
                            fullUrls.add(baseFileUrl + "picture/" + fileName.trim());
                        }
                    }
                    post.put("pictureUrl", TextUtils.join(",", fullUrls));
                }

                // 处理视频
                if (comment.getVideoUrl() != null && !comment.getVideoUrl().toString().isEmpty()) {
                    String videoUrlStr = comment.getVideoUrl().toString();
                    videoUrlStr = videoUrlStr.replace("/video/", "");
                    String[] fileNames = videoUrlStr.split(",");
                    List<String> fullUrls = new ArrayList<>();
                    for (String fileName : fileNames) {
                        if (!fileName.trim().isEmpty()) {
                            fullUrls.add(baseFileUrl + "video/" + fileName.trim());
                        }
                    }
                    post.put("videoUrl", TextUtils.join(",", fullUrls));
                }

                // 处理音频
                if (comment.getVoiceUrl() != null && !comment.getVoiceUrl().toString().isEmpty()) {
                    String voiceUrlStr = comment.getVoiceUrl().toString();
                    voiceUrlStr = voiceUrlStr.replace("/voice/", "");
                    String[] fileNames = voiceUrlStr.split(",");
                    List<String> fullUrls = new ArrayList<>();
                    for (String fileName : fileNames) {
                        if (!fileName.trim().isEmpty()) {
                            fullUrls.add(baseFileUrl + "voice/" + fileName.trim());
                        }
                    }
                    post.put("voiceUrl", TextUtils.join(",", fullUrls));
                }

                posts.add(post);
            } catch (Exception e) {
                Log.e(TAG, "处理单条评论失败", e);
            }
        }

        String postsJson = gson.toJson(posts);
        String js = "javascript:if(window.loadComments) window.loadComments(" + postsJson + ");";
        webView.evaluateJavascript(js, null);
    }

    private String formatTime(String createTime) {
        if (TextUtils.isEmpty(createTime)) {
            return "刚刚";
        }
        if (createTime.length() > 16) {
            return createTime.substring(5, 16);
        }
        return createTime;
    }

    private void loadCommunityHtml() {
        String communityHtml = getCommunityHtml();
        webView.loadDataWithBaseURL(null, communityHtml, "text/html", "UTF-8", null);
    }

    private String getCommunityHtml() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/vue@2.7.14/dist/vue.js\"></script>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body { font-family: system-ui, sans-serif; background: #f5f5f5; padding-bottom: 20px; }\n" +
                "        .publish-btn { position: fixed; bottom: 20px; right: 20px; width: 56px; height: 56px; border-radius: 28px; background: #3b82f6; color: white; display: flex; align-items: center; justify-content: center; font-size: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.2); cursor: pointer; z-index: 100; }\n" +
                "        .post-list { padding: 12px; padding-bottom: 80px; }\n" +
                "        .post-card { background: #fff; border-radius: 12px; padding: 16px; margin-bottom: 12px; }\n" +
                "        .post-header { display: flex; justify-content: space-between; margin-bottom: 12px; }\n" +
                "        .username { font-weight: bold; font-size: 16px; }\n" +
                "        .post-time { color: #999; font-size: 12px; }\n" +
                "        .post-content { margin: 12px 0; line-height: 1.5; color: #333; }\n" +
                "        .post-media { margin: 12px 0; display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }\n" +
                "        .media-item { border-radius: 8px; overflow: hidden; aspect-ratio: 1/1; cursor: pointer; background: #f0f0f0; display: flex; align-items: center; justify-content: center; }\n" +
                "        .media-item img, .media-item video { width: 100%; height: 100%; object-fit: cover; }\n" +
                "        .post-actions { display: flex; gap: 24px; padding-top: 12px; border-top: 1px solid #eee; }\n" +
                "        .action-item { color: #666; font-size: 14px; cursor: pointer; }\n" +
                "        .loading-text { color: #999; text-align: center; padding: 20px; }\n" +
                "        .preview-modal { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.95); display: flex; align-items: center; justify-content: center; z-index: 2000; }\n" +
                "        .preview-content { position: relative; max-width: 95%; max-height: 90vh; }\n" +
                "        .preview-content img, .preview-content video { max-width: 100%; max-height: 90vh; object-fit: contain; }\n" +
                "        .preview-close { position: absolute; top: -50px; right: 0; color: white; font-size: 36px; cursor: pointer; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div id=\"app\">\n" +
                "        <div class=\"post-list\">\n" +
                "            <div class=\"post-card\" v-for=\"(post, idx) in posts\" :key=\"idx\">\n" +
                "                <div class=\"post-header\">\n" +
                "                    <span class=\"username\">{{ post.username }}</span>\n" +
                "                    <span class=\"post-time\">{{ post.time }}</span>\n" +
                "                </div>\n" +
                "                <div class=\"post-content\">{{ post.content }}</div>\n" +
                "                <div class=\"post-media\" v-if=\"post.mediaList && post.mediaList.length\">\n" +
                "                    <div class=\"media-item\" v-for=\"(item, mIdx) in post.mediaList\" :key=\"mIdx\" @click=\"openPreview(post.mediaList, mIdx)\">\n" +
                "                        <img v-if=\"item.type === 'image'\" :src=\"item.url\">\n" +
                "                        <video v-else-if=\"item.type === 'video'\" :src=\"item.url\"></video>\n" +
                "                        <div v-else>🎵</div>\n" +
                "                    </div>\n" +
                "                </div>\n" +
                "                <div class=\"post-actions\">\n" +
                "                    <div class=\"action-item\">👍 {{ post.likeCount || 0 }}</div>\n" +
                "                    <div class=\"action-item\">💬 {{ post.commentCount || 0 }}</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            <div class=\"loading-text\" v-if=\"!posts.length\">暂无内容</div>\n" +
                "        </div>\n" +
                "        <div class=\"publish-btn\" @click=\"openPublish\">✏️</div>\n" +
                "        \n" +
                "        <div class=\"preview-modal\" v-if=\"showPreview\" @click=\"closePreview\">\n" +
                "            <div class=\"preview-content\" @click.stop>\n" +
                "                <img v-if=\"currentMedia.type === 'image'\" :src=\"currentMedia.url\">\n" +
                "                <video v-else-if=\"currentMedia.type === 'video'\" :src=\"currentMedia.url\" controls autoplay></video>\n" +
                "                <div class=\"preview-close\" @click=\"closePreview\">×</div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        new Vue({\n" +
                "            el: '#app',\n" +
                "            data: {\n" +
                "                posts: [],\n" +
                "                showPreview: false,\n" +
                "                currentMedia: {},\n" +
                "                mediaList: [],\n" +
                "                currentIndex: 0\n" +
                "            },\n" +
                "            mounted() {\n" +
                "                window.loadComments = (comments) => {\n" +
                "                    const postsWithMedia = comments.map(post => {\n" +
                "                        const mediaList = [];\n" +
                "                        if (post.pictureUrl) {\n" +
                "                            post.pictureUrl.split(',').forEach(url => {\n" +
                "                                if (url && url.trim()) mediaList.push({ type: 'image', url: url.trim() });\n" +
                "                            });\n" +
                "                        }\n" +
                "                        if (post.videoUrl) {\n" +
                "                            post.videoUrl.split(',').forEach(url => {\n" +
                "                                if (url && url.trim()) mediaList.push({ type: 'video', url: url.trim() });\n" +
                "                            });\n" +
                "                        }\n" +
                "                        return { ...post, mediaList };\n" +
                "                    });\n" +
                "                    this.posts = postsWithMedia;\n" +
                "                };\n" +
                "                \n" +
                "                window.openPublish = () => {\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.openVoiceComment();\n" +
                "                    }\n" +
                "                };\n" +
                "            },\n" +
                "            methods: {\n" +
                "                openPreview(list, index) {\n" +
                "                    this.mediaList = list;\n" +
                "                    this.currentIndex = index;\n" +
                "                    this.currentMedia = list[index];\n" +
                "                    this.showPreview = true;\n" +
                "                },\n" +
                "                closePreview() {\n" +
                "                    this.showPreview = false;\n" +
                "                    this.currentMedia = {};\n" +
                "                    this.mediaList = [];\n" +
                "                },\n" +
                "                openPublish() {\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.openVoiceComment();\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        });\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    public class CommunityJsBridge {

        @JavascriptInterface
        public void refreshComments() {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> fetchComments());
            }
        }

        @JavascriptInterface
        public void openVoiceComment() {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    isWaitingForComment = true;
                    speak("请说您要分享的内容");
                    if (asrManager != null) {
                        asrManager.start();
                    }
                });
            }
        }

        @JavascriptInterface
        public void submitWithFiles(String publishDataJson, String filesInfoJson) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    try {
                        PublishData data = gson.fromJson(publishDataJson, PublishData.class);
                        Type listType = new TypeToken<List<Map<String, String>>>() {}.getType();
                        List<Map<String, String>> filesInfo = gson.fromJson(filesInfoJson, listType);

                        List<Uri> fileUris = new ArrayList<>();
                        List<String> fileTypes = new ArrayList<>();

                        for (Map<String, String> fileInfo : filesInfo) {
                            String uriStr = fileInfo.get("uri");
                            if (uriStr != null) {
                                fileUris.add(Uri.parse(uriStr));
                                fileTypes.add(fileInfo.get("type"));
                            }
                        }

                        uploadPost(data, fileUris, fileTypes);
                    } catch (Exception e) {
                        Log.e(TAG, "解析失败", e);
                        showToast("发布失败：" + e.getMessage());
                    }
                });
            }
        }
    }

    private void uploadPost(PublishData data, List<Uri> fileUris, List<String> fileTypes) {
        if (getActivity() == null || TextUtils.isEmpty(userId)) {
            showToast("用户信息未加载");
            return;
        }

        uploadedPictureUrls.clear();
        uploadedVideoUrls.clear();
        uploadedVoiceUrls.clear();

        long timestamp = System.currentTimeMillis();
        String postId = String.valueOf(timestamp);

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("postId", postId)
                .addFormDataPart("userId", userId)
                .addFormDataPart("content", data.content);

        // 分类处理文件
        List<Uri> images = new ArrayList<>();
        List<Uri> videos = new ArrayList<>();
        List<Uri> audios = new ArrayList<>();

        for (int i = 0; i < fileUris.size(); i++) {
            String type = fileTypes.get(i);
            if (type != null && type.startsWith("image/")) {
                images.add(fileUris.get(i));
            } else if (type != null && type.startsWith("video/")) {
                videos.add(fileUris.get(i));
            } else if (type != null && type.startsWith("audio/")) {
                audios.add(fileUris.get(i));
            }
        }

        // 添加文件
        addFileParts(builder, images, "pictureFile", timestamp, "image", uploadedPictureUrls);
        addFileParts(builder, videos, "videoFile", timestamp, "video", uploadedVideoUrls);
        addFileParts(builder, audios, "voiceFile", timestamp, "audio", uploadedVoiceUrls);

        // 添加URL字段
        if (!uploadedPictureUrls.isEmpty()) {
            builder.addFormDataPart("pictureUrl", TextUtils.join(",", uploadedPictureUrls));
        }
        if (!uploadedVideoUrls.isEmpty()) {
            builder.addFormDataPart("videoUrl", TextUtils.join(",", uploadedVideoUrls));
        }
        if (!uploadedVoiceUrls.isEmpty()) {
            builder.addFormDataPart("voiceUrl", TextUtils.join(",", uploadedVoiceUrls));
        }

        Request request = new Request.Builder()
                .url(OkhttpUtils.URL + OkhttpUtils.AddComment)
                .post(builder.build())
                .build();

        showToast("正在发布...");

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showToast("发布失败：" + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            showToast("发布成功！");
                            fetchComments();
                        } else {
                            showToast("发布失败");
                        }
                    });
                }
            }
        });
    }

    private void addFileParts(MultipartBody.Builder builder, List<Uri> fileUris,
                              String fieldName, long timestamp, String fileType,
                              List<String> urlCollector) {
        if (fileUris.isEmpty()) return;

        for (int i = 0; i < fileUris.size(); i++) {
            Uri uri = fileUris.get(i);
            try {
                String fileName = timestamp + "_" + (i + 1) + getFileExtension(fileType);
                String mimeType = getMimeType(uri);
                File file = uriToFile(uri);

                if (file != null && file.exists()) {
                    RequestBody fileBody = RequestBody.create(MediaType.parse(mimeType), file);
                    builder.addFormDataPart(fieldName, fileName, fileBody);
                    urlCollector.add(fileName);
                }
            } catch (Exception e) {
                Log.e(TAG, "添加文件失败", e);
            }
        }
    }

    private String getFileExtension(String fileType) {
        switch (fileType) {
            case "image": return ".jpg";
            case "video": return ".mp4";
            case "audio": return ".m4a";
            default: return ".bin";
        }
    }

    private String getMimeType(Uri uri) {
        if (uri == null) return "application/octet-stream";
        ContentResolver resolver = requireContext().getContentResolver();
        String mimeType = resolver.getType(uri);
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private File uriToFile(Uri uri) {
        if (uri == null) return null;

        try {
            String path = uri.getPath();
            if (path != null) {
                File file = new File(path);
                if (file.exists()) return file;
            }

            if ("content".equals(uri.getScheme())) {
                String fileName = "temp_" + System.currentTimeMillis();
                File cacheFile = new File(requireContext().getCacheDir(), fileName);

                try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                     FileOutputStream outputStream = new FileOutputStream(cacheFile)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                    }
                    return cacheFile;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "uriToFile失败", e);
        }
        return null;
    }

    private void showToast(String message) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() ->
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show());
        }
    }

    public static class PublishData {
        public String content;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_UPLOAD_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            List<Map<String, String>> filesInfo = new ArrayList<>();

            if (data.getData() != null) {
                Uri uri = data.getData();
                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("uri", uri.toString());
                fileInfo.put("type", getMimeType(uri));
                fileInfo.put("name", getFileName(uri));
                filesInfo.add(fileInfo);
            } else if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    Map<String, String> fileInfo = new HashMap<>();
                    fileInfo.put("uri", uri.toString());
                    fileInfo.put("type", getMimeType(uri));
                    fileInfo.put("name", getFileName(uri));
                    filesInfo.add(fileInfo);
                }
            }

            if (webView != null && !filesInfo.isEmpty()) {
                String filesJson = gson.toJson(filesInfo);
                String js = "javascript:window.receiveSelectedFiles('" + filesJson.replace("'", "\\'") + "')";
                webView.evaluateJavascript(js, null);
            }
        }
    }

    private String getFileName(Uri uri) {
        String fileName = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    fileName = cursor.getString(nameIndex);
                }
            } catch (Exception e) {
                Log.e(TAG, "获取文件名失败", e);
            }
        }
        if (fileName == null) {
            String path = uri.getPath();
            int cut = path.lastIndexOf('/');
            fileName = cut != -1 ? path.substring(cut + 1) : path;
        }
        return fileName;
    }

    /**
     * 关闭语音助手，释放所有资源
     */
    private void shutdownVoiceAssistant() {
        Log.d(TAG, "========== 关闭小黎语音助手 ==========");

        // 停止并释放唤醒管理器
        if (wakeUpManager != null) {
            wakeUpManager.stop();
            wakeUpManager.release();
            wakeUpManager = null;
        }

        // 停止并释放语音识别
        if (asrManager != null) {
            asrManager.stop();
            asrManager.release();
            asrManager = null;
        }

        // 停止并释放 TTS
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        // 重置状态标志
        isVoiceActive = false;
        isWaitingForComment = false;

        // 移除所有延迟任务
        if (voiceHandler != null) {
            voiceHandler.removeCallbacks(autoSleepRunnable);
            voiceHandler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "小黎语音助手已关闭");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (wakeUpManager != null) wakeUpManager.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (wakeUpManager != null) wakeUpManager.stop();
        if (asrManager != null) asrManager.stop();
        shutdownVoiceAssistant();
        voiceHandler.removeCallbacksAndMessages(null);
        enterHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        shutdownVoiceAssistant();
        if (wakeUpManager != null) wakeUpManager.release();
        if (asrManager != null) asrManager.release();
        if (voiceHandler != null) voiceHandler.removeCallbacksAndMessages(null);
        if (enterHandler != null) enterHandler.removeCallbacksAndMessages(null);
    }
}