package com.example.volunteer.activity.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.volunteer.R;
import com.example.volunteer.activity.login.LoginActivity;
import com.example.volunteer.data.ListComment;
import com.example.volunteer.data.UserData;
import com.example.volunteer.utils.OkhttpUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private WebView webView;
    private Gson gson = new Gson();
    private OkHttpClient okHttpClient;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;
    private static final int FILE_UPLOAD_REQUEST_CODE = 1002;
    private String phone;
    private UserData userData;
    private String userId;

    // 存储上传成功的文件URL
    private List<String> uploadedPictureUrls = new ArrayList<>();
    private List<String> uploadedVideoUrls = new ArrayList<>();
    private List<String> uploadedVoiceUrls = new ArrayList<>();
    private ListComment listComment;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_community, container, false);

        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        initWebView(view);
        loadCommunityHtml();
        initData();
        return view;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView(View view) {
        webView = view.findViewById(R.id.webview_community);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setDefaultTextEncodingName("UTF-8");
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setAllowContentAccess(true);

        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setBlockNetworkImage(false);
        webSettings.setBlockNetworkLoads(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 页面加载完成后获取评论列表
                fetchComments();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.e(TAG, "WebView加载错误: " + errorCode + " - " + description + " - " + failingUrl);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                CommunityFragment.this.filePathCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*", "audio/*"});
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(intent, "选择文件"), FILE_CHOOSER_REQUEST_CODE);
                return true;
            }
        });

        webView.addJavascriptInterface(new CommunityJsBridge(), "AndroidBridge");
    }

    private void initData() {
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("phone", MODE_PRIVATE);
        phone = sharedPreferences.getString("phone", null);

        if (TextUtils.isEmpty(phone)) {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            Toast.makeText(getContext(), "登录信息已过期，请重新登录", Toast.LENGTH_SHORT).show();
            startActivity(intent);
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
                    userData = OkhttpUtils.toData(json, UserData.class);
                    if (userData != null && userData.getData() != null) {
                        userId = String.valueOf(userData.getData().getUserId());
                        Log.d(TAG, "用户ID: " + userId);
                    }
                }
            }
        });
    }

    /**
     * 获取所有评论列表
     */
    private void fetchComments() {
        webView.postDelayed(() -> {
            Log.d(TAG, "========== 开始获取评论列表 ==========");

            OkhttpUtils.request("GET", OkhttpUtils.URL + OkhttpUtils.listComment, null, "", new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "onFailure: ", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.body() != null) {
                        String json = response.body().string();
                        try {
                            listComment = OkhttpUtils.toData(json, ListComment.class);
                            if (listComment != null && listComment.getData() != null) {
                                ListComment.DataBean dataBean = listComment.getData();
                                Log.d(TAG, "onResponse: json" + json);
                                List<ListComment.DataBean.Data1Bean> comments = dataBean.getData1();


                                //确保在主线程中更新UI
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

    /**
     * 将评论数据发送给前端Vue
     *
     * @param comments 评论数据列表
     */
    private void sendCommentsToVue(List<ListComment.DataBean.Data1Bean> comments) {
        if (webView == null || comments == null || comments.isEmpty()) {
            Log.w(TAG, "sendCommentsToVue: webView is null or comments is empty");
            return;
        }

        Log.d(TAG, "sendCommentsToVue: 开始处理 " + comments.size() + " 条评论");

        // 🔥 按照创建时间倒序排序（最新的在前面）
        List<ListComment.DataBean.Data1Bean> sortedComments = new ArrayList<>(comments);
        sortedComments.sort((c1, c2) -> {
            String time1 = c1.getCreateTime();
            String time2 = c2.getCreateTime();
            if (time1 == null && time2 == null) return 0;
            if (time1 == null) return 1;
            if (time2 == null) return -1;
            // 倒序：时间晚的在前
            return time2.compareTo(time1);
        });

        // 构建前端需要的帖子数据格式
        List<Map<String, Object>> posts = new ArrayList<>();

        for (ListComment.DataBean.Data1Bean comment : sortedComments) {
            try {
                Map<String, Object> post = new HashMap<>();
                post.put("username", "用户" + comment.getUserId());
                post.put("level", getLevelByUserId(comment.getUserId()));
                post.put("time", formatTime(comment.getCreateTime().substring(5)));
                post.put("content", comment.getContent());

                // 构建标签字符串
                StringBuilder tags = new StringBuilder();
                if (comment.getVoiceUrl() != null && !comment.getVoiceUrl().toString().isEmpty()) {
                    tags.append("#音频 ");
                }

                post.put("tags", tags.toString());
                post.put("likeCount", comment.getLikeCount());
                post.put("commentCount", comment.getReplyCount());

                String baseFileUrl = OkhttpUtils.URL + OkhttpUtils.CommentFile + "/";

                // 处理图片 URL
                if (comment.getPictureUrl() != null && !comment.getPictureUrl().toString().isEmpty()) {
                    String pictureUrlStr = comment.getPictureUrl().toString();
                    pictureUrlStr = pictureUrlStr.replace("/picture/", "");
                    String[] fileNames = pictureUrlStr.split(",");
                    List<String> fullUrls = new ArrayList<>();
                    for (String fileName : fileNames) {
                        if (!fileName.trim().isEmpty()) {
                            // 根据文件扩展名判断文件类型
                            String fileType = getFileTypeByExtension(fileName.trim());
                            fullUrls.add(baseFileUrl + fileType + "/" + fileName.trim());
                        }
                    }
                    post.put("pictureUrl", TextUtils.join(",", fullUrls));
                } else {
                    post.put("pictureUrl", null);
                }

                // 处理视频 URL
                if (comment.getVideoUrl() != null && !comment.getVideoUrl().toString().isEmpty()) {
                    String videoUrlStr = comment.getVideoUrl().toString();
                    videoUrlStr = videoUrlStr.replace("/video/", "");
                    String[] fileNames = videoUrlStr.split(",");
                    List<String> fullUrls = new ArrayList<>();
                    for (String fileName : fileNames) {
                        if (!fileName.trim().isEmpty()) {
                            String fileType = getFileTypeByExtension(fileName.trim());
                            fullUrls.add(baseFileUrl + fileType + "/" + fileName.trim());
                        }
                    }
                    post.put("videoUrl", TextUtils.join(",", fullUrls));
                } else {
                    post.put("videoUrl", null);
                }

                // 处理音频 URL
                if (comment.getVoiceUrl() != null && !comment.getVoiceUrl().toString().isEmpty()) {
                    String voiceUrlStr = comment.getVoiceUrl().toString();
                    voiceUrlStr = voiceUrlStr.replace("/voice/", "");
                    String[] fileNames = voiceUrlStr.split(",");
                    List<String> fullUrls = new ArrayList<>();
                    for (String fileName : fileNames) {
                        if (!fileName.trim().isEmpty()) {
                            String fileType = getFileTypeByExtension(fileName.trim());
                            fullUrls.add(baseFileUrl + fileType + "/" + fileName.trim());
                        }
                    }
                    post.put("voiceUrl", TextUtils.join(",", fullUrls));
                } else {
                    post.put("voiceUrl", null);
                }

                posts.add(post);
            } catch (Exception e) {
                Log.e(TAG, "处理单条评论失败", e);
            }
        }

        // 构建JSON并传递给前端
        String postsJson = gson.toJson(posts);
        Log.d(TAG, "传递帖子数据到前端，共 " + posts.size() + " 条");

        // 使用 evaluateJavascript 传递数据
        String js = "javascript:(function() {" +
                "try {" +
                "    if(window.loadComments) {" +
                "        window.loadComments(" + postsJson + ");" +
                "        console.log('loadComments 调用成功');" +
                "    } else {" +
                "        console.log('loadComments 方法未定义');" +
                "    }" +
                "} catch(e) {" +
                "    console.log('执行JS出错: ' + e.message);" +
                "}" +
                "})()";

        webView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.d(TAG, "JavaScript 执行结果: " + value);
            }
        });

    }

    /**
     * 根据文件扩展名获取文件类型
     */
    private String getFileTypeByExtension(String fileName) {
        if (fileName == null) return "picture";

        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") ||
                lowerFileName.endsWith(".png") || lowerFileName.endsWith(".gif")) {
            return "picture";
        } else if (lowerFileName.endsWith(".mp4") || lowerFileName.endsWith(".avi") ||
                lowerFileName.endsWith(".mov")) {
            return "video";
        } else if (lowerFileName.endsWith(".mp3") || lowerFileName.endsWith(".m4a") ||
                lowerFileName.endsWith(".wav")) {
            return "voice";
        } else {
            return "picture"; // 默认作为图片处理
        }
    }

    /**
     * 根据用户ID获取等级
     */
    private String getLevelByUserId(long userId) {
        // 这里可以根据实际业务逻辑返回等级
        // 暂时返回普通
        return "普通";
    }

    /**
     * 格式化时间
     */
    private String formatTime(String createTime) {
        if (TextUtils.isEmpty(createTime)) {
            return "刚刚";
        }
        // 这里可以根据需要格式化时间
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
                "    <title>志愿者社区</title>\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/vue@2.7.14/dist/vue.js\"></script>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body { font-family: system-ui, -apple-system, sans-serif; background: #f5f5f5; padding-bottom: 20px; }\n" +
                "        .title-bar { background: #fff; padding: 16px; text-align: center; font-size: 18px; font-weight: bold; border-bottom: 1px solid #eee; }\n" +
                "        .tab-container { display: flex; background: #fff; overflow-x: auto; padding: 0 12px; border-bottom: 1px solid #eee; }\n" +
                "        .tab-item { padding: 12px 16px; font-size: 14px; color: #666; white-space: nowrap; border-bottom: 2px solid transparent; cursor: pointer; }\n" +
                "        .tab-item.active { color: #3b82f6; border-bottom-color: #3b82f6; }\n" +
                "        .publish-box { background: #fff; padding: 12px; margin: 8px 12px; border-radius: 12px; }\n" +
                "        .publish-input { width: 100%; padding: 12px; background: #f5f5f5; border: none; border-radius: 24px; font-size: 14px; }\n" +
                "        .post-list { padding: 12px; }\n" +
                "        .post-card { background: #fff; border-radius: 12px; padding: 16px; margin-bottom: 12px; }\n" +
                "        .post-header { display: flex; justify-content: space-between; margin-bottom: 12px; }\n" +
                "        .username { font-weight: bold; font-size: 16px; }\n" +
                "        .level-tag { padding: 2px 8px; border-radius: 12px; font-size: 10px; color: #fff; margin-left: 8px; }\n" +
                "        .level-gold { background: #ffb300; }\n" +
                "        .level-silver { background: #9e9e9e; }\n" +
                "        .level-bronze { background: #cd7f32; }\n" +
                "        .post-time { color: #999; font-size: 12px; }\n" +
                "        .post-content { margin: 12px 0; line-height: 1.5; color: #333; }\n" +
                "        .post-tags { color: #3b82f6; font-size: 12px; margin: 8px 0; }\n" +
                "        .post-media { margin: 12px 0; display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }\n" +
                "        .media-item { border-radius: 8px; overflow: hidden; aspect-ratio: 1 / 1; cursor: pointer; background: #f0f0f0; display: flex; align-items: center; justify-content: center; }\n" +
                "        .media-item img, .media-item video { width: 100%; height: 100%; object-fit: cover; }\n" +
                "        .audio-placeholder { font-size: 24px; }\n" +
                "        .post-actions { display: flex; gap: 24px; padding-top: 12px; border-top: 1px solid #eee; }\n" +
                "        .action-item { color: #666; font-size: 14px; cursor: pointer; }\n" +
                "        .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }\n" +
                "        .modal-content { background: #fff; width: 90%; max-width: 500px; border-radius: 16px; max-height: 80vh; overflow-y: auto; }\n" +
                "        .modal-header { padding: 16px; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; align-items: center; }\n" +
                "        .close-btn { background: none; border: none; font-size: 24px; cursor: pointer; }\n" +
                "        .modal-body { padding: 16px; }\n" +
                "        .content-input { width: 100%; border: 1px solid #ddd; border-radius: 8px; padding: 12px; font-size: 14px; resize: vertical; }\n" +
                "        .topic-tags { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; }\n" +
                "        .topic-tag { padding: 4px 12px; background: #f5f5f5; border-radius: 16px; font-size: 12px; cursor: pointer; }\n" +
                "        .topic-tag.active { background: #3b82f6; color: #fff; }\n" +
                "        .media-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 12px; }\n" +
                "        .upload-media-item { position: relative; aspect-ratio: 1 / 1; border-radius: 8px; overflow: hidden; background: #f5f5f5; display: flex; align-items: center; justify-content: center; }\n" +
                "        .upload-media-item img, .upload-media-item video { width: 100%; height: 100%; object-fit: cover; }\n" +
                "        .add-btn { font-size: 24px; color: #999; cursor: pointer; }\n" +
                "        .remove-btn { position: absolute; top: 4px; right: 4px; background: rgba(0,0,0,0.6); color: #fff; width: 20px; height: 20px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 12px; cursor: pointer; z-index: 10; }\n" +
                "        .modal-footer { padding: 16px; border-top: 1px solid #eee; display: flex; justify-content: flex-end; gap: 12px; }\n" +
                "        .btn-cancel { padding: 8px 20px; background: #f5f5f5; border: none; border-radius: 8px; cursor: pointer; }\n" +
                "        .btn-submit { padding: 8px 20px; background: #3b82f6; color: #fff; border: none; border-radius: 8px; cursor: pointer; }\n" +
                "        .btn-submit:disabled { background: #ccc; cursor: not-allowed; }\n" +
                "        .loading-text { color: #999; text-align: center; padding: 20px; }\n" +
                "        /* 预览弹窗样式 */\n" +
                "        .preview-modal { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.95); display: flex; align-items: center; justify-content: center; z-index: 2000; backdrop-filter: blur(5px); }\n" +
                "        .preview-content { position: relative; max-width: 95%; max-height: 90vh; display: flex; align-items: center; justify-content: center; }\n" +
                "        .preview-content img, .preview-content video { max-width: 100%; max-height: 90vh; object-fit: contain; border-radius: 8px; }\n" +
                "        .preview-close { position: absolute; top: -50px; right: 0; color: white; font-size: 36px; cursor: pointer; width: 44px; height: 44px; text-align: center; line-height: 44px; background: rgba(0,0,0,0.5); border-radius: 50%; transition: all 0.3s; }\n" +
                "        .preview-close:hover { background: rgba(255,255,255,0.3); transform: rotate(90deg); }\n" +
                "        .preview-indicator { position: absolute; bottom: -60px; left: 50%; transform: translateX(-50%); color: white; font-size: 16px; background: rgba(0,0,0,0.6); padding: 8px 16px; border-radius: 20px; display: flex; gap: 20px; align-items: center; }\n" +
                "        .preview-nav { background: rgba(255,255,255,0.2); border: none; color: white; font-size: 24px; width: 36px; height: 36px; border-radius: 50%; cursor: pointer; transition: all 0.3s; }\n" +
                "        .preview-nav:hover { background: rgba(255,255,255,0.4); transform: scale(1.1); }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div id=\"app\">\n" +
                "        <div class=\"title-bar\">志愿者社区</div>\n" +
                "        <div class=\"tab-container\" v-if=\"tabs.length\">\n" +
                "            <div class=\"tab-item\" :class=\"{active: activeTab === tab}\" v-for=\"tab in tabs\" :key=\"tab\" @click=\"switchTab(tab)\">{{ tab }}</div>\n" +
                "        </div>\n" +
                "        <div class=\"publish-box\">\n" +
                "            <input class=\"publish-input\" placeholder=\"分享你的故事...\" @click=\"showModal = true\">\n" +
                "        </div>\n" +
                "        <div class=\"post-list\">\n" +
                "            <div class=\"post-card\" v-for=\"post in filteredPosts\" :key=\"post.time\">\n" +
                "                <div class=\"post-header\">\n" +
                "                    <div class=\"user-info\">\n" +
                "                        <span class=\"username\">{{ post.username }}</span>\n" +
                "                        <span class=\"level-tag\" :class=\"getLevelClass(post.level)\">{{ post.level }}</span>\n" +
                "                    </div>\n" +
                "                    <span class=\"post-time\">{{ post.time }}</span>\n" +
                "                </div>\n" +
                "                <div class=\"post-content\">{{ post.content }}</div>\n" +
                "                <div class=\"post-tags\">{{ post.tags }}</div>\n" +
                "                <div class=\"post-media\" v-if=\"post.mediaList && post.mediaList.length > 0\">\n" +
                "                    <div class=\"media-item\" v-for=\"(item, idx) in post.mediaList\" :key=\"idx\" @click=\"openMediaPreview(post.mediaList, idx)\">\n" +
                "                        <img v-if=\"item.type === 'image'\" :src=\"item.url\" alt=\"图片\">\n" +
                "                        <video v-else-if=\"item.type === 'video'\" :src=\"item.url\" muted playsinline></video>\n" +
                "                        <div v-else class=\"audio-placeholder\">🎵 音频</div>\n" +
                "                    </div>\n" +
                "                </div>\n" +
                "                <div class=\"post-actions\">\n" +
                "                    <div class=\"action-item\" @click=\"likePost(post)\">👍 {{ post.likeCount }}</div>\n" +
                "                    <div class=\"action-item\" @click=\"commentPost(post)\">💬 {{ post.commentCount }}</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            <div class=\"loading-text\" v-if=\"!posts.length\">暂无内容，快来发布第一条动态吧~</div>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- 发布弹窗 -->\n" +
                "        <div class=\"modal-overlay\" v-if=\"showModal\" @click.self=\"closeModal\">\n" +
                "            <div class=\"modal-content\">\n" +
                "                <div class=\"modal-header\">\n" +
                "                    <span>发布动态</span>\n" +
                "                    <button class=\"close-btn\" @click=\"closeModal\">×</button>\n" +
                "                </div>\n" +
                "                <div class=\"modal-body\">\n" +
                "                    <textarea class=\"content-input\" v-model=\"newContent\" placeholder=\"分享你的故事...\" rows=\"4\"></textarea>\n" +
                "                    <div class=\"topic-tags\">\n" +
                "                        <span class=\"topic-tag\" :class=\"{active: selectedTopics.includes(t)}\" v-for=\"t in recommendTopics\" :key=\"t\" @click=\"toggleTopic(t)\">{{ t }}</span>\n" +
                "                    </div>\n" +
                "                    <div>\n" +
                "                        <span style=\"font-size: 12px; color: #666;\">添加图片/视频/音频 (最多9个)</span>\n" +
                "                        <div class=\"media-grid\">\n" +
                "                            <div class=\"upload-media-item add-btn\" @click=\"selectFiles\">+</div>\n" +
                "                            <div class=\"upload-media-item\" v-for=\"(file, idx) in selectedFiles\" :key=\"idx\">\n" +
                "                                <img v-if=\"file.type && file.type.startsWith('image')\" :src=\"file.url\" style=\"width:100%;height:100%;object-fit:cover\">\n" +
                "                                <video v-else-if=\"file.type && file.type.startsWith('video')\" :src=\"file.url\" controls style=\"width:100%;height:100%;object-fit:cover\"></video>\n" +
                "                                <div v-else class=\"audio-placeholder\">🎵 {{ file.name || '音频' }}</div>\n" +
                "                                <span class=\"remove-btn\" @click=\"removeFile(idx)\">×</span>\n" +
                "                            </div>\n" +
                "                        </div>\n" +
                "                    </div>\n" +
                "                </div>\n" +
                "                <div class=\"modal-footer\">\n" +
                "                    <button class=\"btn-cancel\" @click=\"closeModal\">取消</button>\n" +
                "                    <button class=\"btn-submit\" @click=\"submitPost\" :disabled=\"!newContent.trim() || submitting\">{{ submitting ? '发布中...' : '发布' }}</button>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- 媒体预览弹窗 -->\n" +
                "        <div class=\"preview-modal\" v-if=\"showPreview\" @click.self=\"closePreview\">\n" +
                "            <div class=\"preview-content\">\n" +
                "                <span class=\"preview-close\" @click=\"closePreview\">×</span>\n" +
                "                <img v-if=\"previewMedia.type === 'image'\" :src=\"previewMedia.url\" alt=\"预览图片\" @click.stop>\n" +
                "                <video v-else-if=\"previewMedia.type === 'video'\" :src=\"previewMedia.url\" controls autoplay @click.stop></video>\n" +
                "                <div v-else class=\"audio-preview\">🎵 音频文件</div>\n" +
                "                <div class=\"preview-indicator\" v-if=\"previewList.length > 1\">\n" +
                "                    <button class=\"preview-nav prev\" @click.stop=\"prevPreview\">‹</button>\n" +
                "                    <span>{{ previewIndex + 1 }} / {{ previewList.length }}</span>\n" +
                "                    <button class=\"preview-nav next\" @click.stop=\"nextPreview\">›</button>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        new Vue({\n" +
                "            el: '#app',\n" +
                "            data: {\n" +
                "                tabs: ['推荐', '求助', '经验', '暖心', '活动'],\n" +
                "                posts: [],\n" +
                "                activeTab: '推荐',\n" +
                "                showModal: false,\n" +
                "                newContent: '',\n" +
                "                selectedTopics: [],\n" +
                "                recommendTopics: ['#暖心瞬间', '#志愿者故事', '#助盲技巧', '#今日求助'],\n" +
                "                selectedFiles: [],\n" +
                "                submitting: false,\n" +
                "                // 预览相关\n" +
                "                showPreview: false,\n" +
                "                previewMedia: {},\n" +
                "                previewList: [],\n" +
                "                previewIndex: 0\n" +
                "            },\n" +
                "            computed: {\n" +
                "                filteredPosts() {\n" +
                "                    if (this.activeTab === '求助') {\n" +
                "                        return this.posts.filter(p => p.tags && p.tags.includes('#今日求助'));\n" +
                "                    } else if (this.activeTab === '暖心') {\n" +
                "                        return this.posts.filter(p => p.tags && p.tags.includes('#暖心瞬间'));\n" +
                "                    } else if (this.activeTab === '经验') {\n" +
                "                        return this.posts.filter(p => p.tags && p.tags.includes('#经验分享'));\n" +
                "                    }\n" +
                "                    return this.posts;\n" +
                "                }\n" +
                "            },\n" +
                "            mounted() {\n" +
                "                // 接收从服务器加载的评论列表\n" +
                "                window.loadComments = (comments) => {\n" +
                "                    console.log('收到评论数据:', comments);\n" +
                "                    const postsWithMedia = comments.map(post => {\n" +
                "                        const mediaList = [];\n" +
                "                        // 解析图片\n" +
                "                        if (post.pictureUrl) {\n" +
                "                            const urls = post.pictureUrl.split(',');\n" +
                "                            urls.forEach(url => {\n" +
                "                                if (url && url.trim()) {\n" +
                "                                    mediaList.push({ type: 'image', url: url.trim() });\n" +
                "                                }\n" +
                "                            });\n" +
                "                        }\n" +
                "                        // 解析视频\n" +
                "                        if (post.videoUrl) {\n" +
                "                            const urls = post.videoUrl.split(',');\n" +
                "                            urls.forEach(url => {\n" +
                "                                if (url && url.trim()) {\n" +
                "                                    mediaList.push({ type: 'video', url: url.trim() });\n" +
                "                                }\n" +
                "                            });\n" +
                "                        }\n" +
                "                        // 解析音频\n" +
                "                        if (post.voiceUrl) {\n" +
                "                            const urls = post.voiceUrl.split(',');\n" +
                "                            urls.forEach(url => {\n" +
                "                                if (url && url.trim()) {\n" +
                "                                    mediaList.push({ type: 'audio', url: url.trim(), name: '音频文件' });\n" +
                "                                }\n" +
                "                            });\n" +
                "                        }\n" +
                "                        return { ...post, mediaList: mediaList };\n" +
                "                    });\n" +
                "                    this.posts = postsWithMedia;\n" +
                "                };\n" +
                "                \n" +
                "                // 接收新发布的帖子（保留用于兼容）\n" +
                "                window.addNewPost = (post) => {\n" +
                "                    // 刷新整个列表而不是只添加一条\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.refreshComments();\n" +
                "                    }\n" +
                "                };\n" +
                "                \n" +
                "                // 接收原生传回的文件\n" +
                "                window.receiveSelectedFiles = (filesJson) => {\n" +
                "                    console.log('收到文件: ' + filesJson);\n" +
                "                    const files = JSON.parse(filesJson);\n" +
                "                    files.forEach(file => {\n" +
                "                        this.selectedFiles.push({\n" +
                "                            type: file.type,\n" +
                "                            url: file.uri,\n" +
                "                            name: file.name,\n" +
                "                            uri: file.uri\n" +
                "                        });\n" +
                "                    });\n" +
                "                };\n" +
                "            },\n" +
                "            methods: {\n" +
                "                switchTab(tab) {\n" +
                "                    this.activeTab = tab;\n" +
                "                },\n" +
                "                toggleTopic(topic) {\n" +
                "                    const idx = this.selectedTopics.indexOf(topic);\n" +
                "                    idx > -1 ? this.selectedTopics.splice(idx, 1) : this.selectedTopics.push(topic);\n" +
                "                },\n" +
                "                selectFiles() {\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.openFileChooser();\n" +
                "                    }\n" +
                "                },\n" +
                "                removeFile(index) {\n" +
                "                    this.selectedFiles.splice(index, 1);\n" +
                "                },\n" +
                "                closeModal() {\n" +
                "                    this.showModal = false;\n" +
                "                    this.newContent = '';\n" +
                "                    this.selectedTopics = [];\n" +
                "                    this.selectedFiles = [];\n" +
                "                    this.submitting = false;\n" +
                "                },\n" +
                "                submitPost() {\n" +
                "                    if (!this.newContent.trim() || this.submitting) return;\n" +
                "                    this.submitting = true;\n" +
                "                    \n" +
                "                    const publishData = {\n" +
                "                        content: this.newContent,\n" +
                "                        topics: this.selectedTopics\n" +
                "                    };\n" +
                "                    \n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        if (this.selectedFiles.length > 0) {\n" +
                "                            const filesInfo = this.selectedFiles.map(f => ({\n" +
                "                                type: f.type,\n" +
                "                                name: f.name,\n" +
                "                                uri: f.uri\n" +
                "                            }));\n" +
                "                            window.AndroidBridge.submitWithFiles(JSON.stringify(publishData), JSON.stringify(filesInfo));\n" +
                "                        } else {\n" +
                "                            window.AndroidBridge.submitPost(JSON.stringify(publishData));\n" +
                "                        }\n" +
                "                    }\n" +
                "                    this.closeModal();\n" +
                "                },\n" +
                "                likePost(post) {\n" +
                "                    post.likeCount++;\n" +
                "                },\n" +
                "                commentPost(post) {\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.showToast('评论功能开发中');\n" +
                "                    }\n" +
                "                },\n" +
                "                getLevelClass(level) {\n" +
                "                    if (level === '金牌') return 'level-gold';\n" +
                "                    if (level === '白银') return 'level-silver';\n" +
                "                    return 'level-bronze';\n" +
                "                },\n" +
                "                // 打开媒体预览\n" +
                "                openMediaPreview(mediaList, index) {\n" +
                "                    this.previewList = mediaList;\n" +
                "                    this.previewIndex = index;\n" +
                "                    this.previewMedia = mediaList[index];\n" +
                "                    this.showPreview = true;\n" +
                "                    document.body.style.overflow = 'hidden';\n" +
                "                },\n" +
                "                // 关闭预览\n" +
                "                closePreview() {\n" +
                "                    this.showPreview = false;\n" +
                "                    this.previewMedia = {};\n" +
                "                    this.previewList = [];\n" +
                "                    this.previewIndex = 0;\n" +
                "                    document.body.style.overflow = 'auto';\n" +
                "                },\n" +
                "                // 下一张\n" +
                "                nextPreview() {\n" +
                "                    if (this.previewIndex < this.previewList.length - 1) {\n" +
                "                        this.previewIndex++;\n" +
                "                        this.previewMedia = this.previewList[this.previewIndex];\n" +
                "                    }\n" +
                "                },\n" +
                "                // 上一张\n" +
                "                prevPreview() {\n" +
                "                    if (this.previewIndex > 0) {\n" +
                "                        this.previewIndex--;\n" +
                "                        this.previewMedia = this.previewList[this.previewIndex];\n" +
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
        public void showToast(String message) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void openFileChooser() {
            Log.d(TAG, "openFileChooser called");
            if (getActivity() != null) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*", "audio/*"});
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(intent, "选择文件"), FILE_UPLOAD_REQUEST_CODE);
            }
        }

        @JavascriptInterface
        public void refreshComments() {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Log.d(TAG, "刷新评论列表");
                    fetchComments();
                });
            }
        }

        @JavascriptInterface
        public void submitPost(String publishDataJson) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    try {
                        PublishData data = gson.fromJson(publishDataJson, PublishData.class);
                        uploadPost(data, new ArrayList<>(), new ArrayList<>());
                    } catch (Exception e) {
                        Log.e(TAG, "解析失败", e);
                        showToast("发布失败：" + e.getMessage());
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
                        Type listType = new TypeToken<List<Map<String, String>>>() {
                        }.getType();
                        List<Map<String, String>> filesInfo = gson.fromJson(filesInfoJson, listType);

                        Log.d(TAG, "submitWithFiles: content=" + data.content);
                        Log.d(TAG, "submitWithFiles: 文件数量=" + filesInfo.size());

                        // 提取文件 URI 和类型
                        List<Uri> fileUris = new ArrayList<>();
                        List<String> fileTypes = new ArrayList<>();

                        for (Map<String, String> fileInfo : filesInfo) {
                            String uriStr = fileInfo.get("uri");
                            if (uriStr != null) {
                                Uri uri = Uri.parse(uriStr);
                                fileUris.add(uri);
                                fileTypes.add(fileInfo.get("type"));
                                Log.d(TAG, "添加文件: " + uriStr + ", type=" + fileInfo.get("type"));
                            }
                        }

                        // 直接上传
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
        if (getActivity() == null) {
            Log.e(TAG, "Activity is null");
            return;
        }

        if (TextUtils.isEmpty(userId)) {
            Log.e(TAG, "userId is empty");
            showToast("用户信息未加载，请稍后重试");
            return;
        }

        Log.d(TAG, "========== 开始上传 ==========");
        Log.d(TAG, "userId: " + userId);
        Log.d(TAG, "content: " + data.content);
        Log.d(TAG, "文件数量: " + fileUris.size());

        // 清空之前的URL列表
        uploadedPictureUrls.clear();
        uploadedVideoUrls.clear();
        uploadedVoiceUrls.clear();

        long timestamp = System.currentTimeMillis();
        String postId = String.valueOf(timestamp);
        Log.d(TAG, "postId: " + postId);

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("postId", postId)
                .addFormDataPart("userId", userId)
                .addFormDataPart("content", data.content);

        if (data.topics != null && data.topics.length > 0) {
            String topicsStr = String.join(" ", data.topics);
            builder.addFormDataPart("topics", topicsStr);
            Log.d(TAG, "topics: " + topicsStr);
        }

        // 分类处理文件
        List<Uri> images = new ArrayList<>();
        List<Uri> videos = new ArrayList<>();
        List<Uri> audios = new ArrayList<>();

        for (int i = 0; i < fileUris.size(); i++) {
            Uri uri = fileUris.get(i);
            String type = fileTypes.get(i);
            Log.d(TAG, "文件 " + i + ": type=" + type + ", uri=" + uri);

            if (type != null && type.startsWith("image/")) {
                images.add(uri);
                Log.d(TAG, " -> 归类为图片");
            } else if (type != null && type.startsWith("video/")) {
                videos.add(uri);
                Log.d(TAG, " -> 归类为视频");
            } else if (type != null && type.startsWith("audio/")) {
                audios.add(uri);
                Log.d(TAG, " -> 归类为音频");
            }
        }

        // 添加图片文件并收集URL
        addFilePartsWithUrlCollect(builder, images, "pictureFile", timestamp, "image", uploadedPictureUrls);

        // 添加视频文件并收集URL
        addFilePartsWithUrlCollect(builder, videos, "videoFile", timestamp, "video", uploadedVideoUrls);

        // 添加音频文件并收集URL
        addFilePartsWithUrlCollect(builder, audios, "voiceFile", timestamp, "audio", uploadedVoiceUrls);

        // 🔥 这里直接使用收集到的文件名列表，不需要添加前缀
        if (!uploadedPictureUrls.isEmpty()) {
            String pictureUrlsStr = TextUtils.join(",", uploadedPictureUrls);
            builder.addFormDataPart("pictureUrl", pictureUrlsStr);  // 字段名
            Log.d(TAG, "图片文件名: " + pictureUrlsStr);
        }

        if (!uploadedVideoUrls.isEmpty()) {
            String videoUrlsStr = TextUtils.join(",", uploadedVideoUrls);
            builder.addFormDataPart("videoUrl", videoUrlsStr);
            Log.d(TAG, "视频文件名: " + videoUrlsStr);
        }

        if (!uploadedVoiceUrls.isEmpty()) {
            String voiceUrlsStr = TextUtils.join(",", uploadedVoiceUrls);
            builder.addFormDataPart("voiceUrl", voiceUrlsStr);
            Log.d(TAG, "音频文件名: " + voiceUrlsStr);
        }

        RequestBody requestBody = builder.build();

        Request request = new Request.Builder()
                .url(OkhttpUtils.URL + OkhttpUtils.addComment)
                .post(requestBody)
                .build();

        showToast("正在发布...");

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "网络请求失败", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            showToast("发布失败：" + e.getMessage()));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "响应码: " + response.code());
                Log.d(TAG, "响应内容: " + responseBody);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            showToast("发布成功！");
                            // 发布成功后刷新评论列表
                            fetchComments();
                        } else {
                            showToast("发布失败：" + responseBody);
                        }
                    });
                }
                if (response.body() != null) {
                    response.body().close();
                }
            }
        });
    }

    private void addFilePartsWithUrlCollect(MultipartBody.Builder
                                                    builder, List<Uri> fileUris,
                                            String fieldName, long timestamp, String fileType,
                                            List<String> urlCollector) {
        if (fileUris.isEmpty()) {
            Log.d(TAG, "没有 " + fileType + " 文件需要添加");
            return;
        }

        Log.d(TAG, "========== 添加 " + fileType + " 文件 ==========");
        Log.d(TAG, "文件数量: " + fileUris.size());

        for (int i = 0; i < fileUris.size(); i++) {
            Uri uri = fileUris.get(i);
            try {
                // 生成文件名: 时间戳 或 时间戳_序号
                /*if (fileUris.size() == 1) {
                    fileName = timestamp + getFileExtension(fileType);
                } else {
                    fileName = timestamp + "_" + (i + 1) + getFileExtension(fileType);
                }*/
                String fileName = timestamp + "_" + (i + 1) + getFileExtension(fileType);
                String mimeType = getMimeType(uri);
                Log.d(TAG, "处理文件 " + (i + 1) + ":");
                Log.d(TAG, "  URI: " + uri);
                Log.d(TAG, "  文件名: " + fileName);
                Log.d(TAG, "  MIME类型: " + mimeType);

                File file = uriToFile(uri);

                if (file != null && file.exists()) {
                    Log.d(TAG, "  文件路径: " + file.getAbsolutePath());
                    Log.d(TAG, "  文件大小: " + file.length() + " bytes");
                    RequestBody fileBody = RequestBody.create(MediaType.parse(mimeType), file);
                    builder.addFormDataPart(fieldName, fileName, fileBody);
                    Log.d(TAG, "  ✓ 成功添加 " + fieldName + ": " + fileName);

                    // 收集URL（用于前端显示）
                    String url =fileName;
                    urlCollector.add(url);
                } else {
                    Log.e(TAG, "  ✗ 文件不存在: " + uri);
                }
            } catch (Exception e) {
                Log.e(TAG, "添加 " + fileType + " 文件失败", e);
            }
        }
    }

    private String getFileExtension(String fileType) {
        switch (fileType) {
            case "image":
                return ".jpg";
            case "video":
                return ".mp4";
            case "audio":
                return ".m4a";
            default:
                return ".bin";
        }
    }

    private String getMimeType(Uri uri) {
        if (uri == null) return "application/octet-stream";
        ContentResolver resolver = requireContext().getContentResolver();
        String mimeType = resolver.getType(uri);
        Log.d(TAG, "getMimeType: " + uri + " -> " + mimeType);
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private File uriToFile(Uri uri) {
        if (uri == null) return null;

        try {
            // 尝试直接获取文件路径
            String path = uri.getPath();
            if (path != null) {
                File file = new File(path);
                if (file.exists()) {
                    Log.d(TAG, "uriToFile: 直接路径成功: " + path);
                    return file;
                }
            }

            // 如果是 content:// 协议，复制到缓存目录
            if ("content".equals(uri.getScheme())) {
                String fileName = "temp_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
                File cacheFile = new File(requireContext().getCacheDir(), fileName);
                Log.d(TAG, "uriToFile: 复制到缓存文件: " + cacheFile.getAbsolutePath());

                try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                     FileOutputStream outputStream = new FileOutputStream(cacheFile)) {

                    byte[] buffer = new byte[8192];
                    int length;
                    long totalSize = 0;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                        totalSize += length;
                    }
                    Log.d(TAG, "uriToFile: 复制成功, 大小: " + totalSize + " bytes");
                    return cacheFile;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "uriToFile 失败: " + uri, e);
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
        public String[] topics;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 处理发布时的文件选择
        if (requestCode == FILE_UPLOAD_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Log.d(TAG, "========== onActivityResult: 收到文件选择结果 ==========");

            List<Map<String, String>> filesInfo = new ArrayList<>();

            if (data.getData() != null) {
                Uri uri = data.getData();
                String mimeType = getMimeType(uri);
                String fileName = getFileName(uri);
                Log.d(TAG, "单文件选择:");
                Log.d(TAG, "  URI: " + uri);
                Log.d(TAG, "  类型: " + mimeType);
                Log.d(TAG, "  名称: " + fileName);

                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("uri", uri.toString());
                fileInfo.put("type", mimeType);
                fileInfo.put("name", fileName);
                filesInfo.add(fileInfo);

            } else if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                Log.d(TAG, "多文件选择, 数量: " + count);

                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    String mimeType = getMimeType(uri);
                    String fileName = getFileName(uri);
                    Log.d(TAG, "文件 " + (i + 1) + ":");
                    Log.d(TAG, "  URI: " + uri);
                    Log.d(TAG, "  类型: " + mimeType);
                    Log.d(TAG, "  名称: " + fileName);

                    Map<String, String> fileInfo = new HashMap<>();
                    fileInfo.put("uri", uri.toString());
                    fileInfo.put("type", mimeType);
                    fileInfo.put("name", fileName);
                    filesInfo.add(fileInfo);
                }
            }

            // 将文件信息传递给前端
            if (webView != null && !filesInfo.isEmpty()) {
                String filesJson = gson.toJson(filesInfo);
                Log.d(TAG, "传递文件到前端: " + filesJson);
                String js = "javascript:window.receiveSelectedFiles('" + filesJson.replace("'", "\\'") + "')";
                webView.evaluateJavascript(js, null);
            } else {
                Log.e(TAG, "没有文件被选中");
            }
        }

        // 处理 WebView 文件选择器
        if (requestCode == FILE_CHOOSER_REQUEST_CODE && filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                } else if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
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
            fileName = uri.getPath();
            int cut = fileName.lastIndexOf('/');
            if (cut != -1) {
                fileName = fileName.substring(cut + 1);
            }
        }
        return fileName;
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
    }
}