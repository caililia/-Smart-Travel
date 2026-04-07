package com.example.volunteer.activity.fragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.volunteer.MainActivity;
import com.example.volunteer.R;
import com.example.volunteer.activity.activity.HelpingActivity;
import com.example.volunteer.activity.activity.MedalActivity;
import com.example.volunteer.activity.activity.PointActivity;
import com.example.volunteer.activity.activity.feedback.FeedBackActivity;
import com.example.volunteer.activity.login.LoginActivity;
import com.example.volunteer.data.Growth;
import com.example.volunteer.utils.OkhttpUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import static android.content.Context.MODE_PRIVATE;

public class PersonalFragment extends Fragment {

    private static final String TAG = "PersonalFragment";
    private WebView webView;
    private SharedPreferences sharedPreferences;
    private Handler handler;
    private int userId;
    private Growth growthData;
    private Growth.DataBean data1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_personal, container, false);
        sharedPreferences = getActivity().getSharedPreferences("phone", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());

        // 获取用户ID
        String phone = sharedPreferences.getString("phone", null);
        if (TextUtils.isEmpty(phone)) {
            // 没有登录信息，跳转到登录页
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            getActivity().finish();
            return view;
        }

        // 从缓存或本地获取userId
        loadUserIdFromCache();

        initWebView(view);

        // 获取用户成长信息
        fetchGrowthInfo();

        return view;
    }

    /**
     * 从缓存加载用户ID
     */
    private void loadUserIdFromCache() {
        // 尝试从SharedPreferences获取用户ID
        SharedPreferences userCache = getActivity().getSharedPreferences("user_cache", MODE_PRIVATE);
        String phone = sharedPreferences.getString("phone", null);
        if (phone != null) {
            String cachedCode = userCache.getString("user_code_" + phone, null);
            if (cachedCode != null) {
                try {
                    userId = Integer.parseInt(cachedCode);
                    Log.d(TAG, "从缓存获取用户ID: " + userId);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "解析用户ID失败", e);
                }
            }
        }
    }

    /**
     * 获取用户成长信息
     */
    private void fetchGrowthInfo() {
        if (userId <= 0) {
            Log.e(TAG, "用户ID无效，尝试从接口获取");
            // 如果userId无效，先获取用户信息
            fetchUserInfo();
            return;
        }

        String url = OkhttpUtils.URL + OkhttpUtils.Growth + "/" + userId;
        Log.d(TAG, "请求用户成长信息: " + url);

        OkhttpUtils.request("GET", url, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取用户成长信息失败", e);
                handler.post(() -> {
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), "获取用户信息失败", Toast.LENGTH_SHORT).show();
                        // 使用默认数据
                        updateWebViewWithDefaultData();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "获取用户成长信息响应: " + json);
                    growthData = OkhttpUtils.toData(json, Growth.class);
                    if (growthData.getCode() == 200) {
                            data1 = growthData.getData();
                            handler.post(() -> {
                                if (getActivity() != null && webView != null) {
                                    updateWebViewWithGrowthData();
                                }
                            });
                        } else {
                            Log.e(TAG, "获取用户成长信息失败: ");
                            handler.post(() -> {
                                if (getActivity() != null) {
                                    updateWebViewWithDefaultData();
                                }
                            });
                        }
                } else {
                    Log.e(TAG, "请求失败: " + response.code());
                    handler.post(() -> {
                        if (getActivity() != null) {
                            Toast.makeText(getActivity(), "请求失败: " + response.code(), Toast.LENGTH_SHORT).show();
                            updateWebViewWithDefaultData();
                        }
                    });
                }
            }
        });
    }

    /**
     * 获取用户信息（用于获取userId）
     */
    private void fetchUserInfo() {
        String phone = sharedPreferences.getString("phone", null);
        if (TextUtils.isEmpty(phone)) {
            return;
        }

        String url = OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone;
        Log.d(TAG, "获取用户信息: " + url);

        OkhttpUtils.request("GET", url, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取用户信息失败", e);
                handler.post(() -> {
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), "获取用户信息失败", Toast.LENGTH_SHORT).show();
                        updateWebViewWithDefaultData();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "获取用户信息响应: " + json);

                    try {
                        JSONObject jsonObject = new JSONObject(json);
                        int code = jsonObject.getInt("code");
                        if (code == 200) {
                            JSONObject data = jsonObject.getJSONObject("data");
                            userId = data.optInt("userId", 0);
                            if (userId > 0) {
                                // 缓存用户ID
                                cacheUserId(userId);
                                // 重新获取成长信息
                                fetchGrowthInfo();
                            } else {
                                handler.post(() -> updateWebViewWithDefaultData());
                            }
                        } else {
                            handler.post(() -> updateWebViewWithDefaultData());
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "解析用户信息失败", e);
                        handler.post(() -> updateWebViewWithDefaultData());
                    }
                } else {
                    handler.post(() -> updateWebViewWithDefaultData());
                }
            }
        });
    }

    /**
     * 缓存用户ID
     */
    private void cacheUserId(int userId) {
        SharedPreferences userCache = getActivity().getSharedPreferences("user_cache", MODE_PRIVATE);
        String phone = sharedPreferences.getString("phone", null);
        if (phone != null) {
            SharedPreferences.Editor editor = userCache.edit();
            editor.putString("user_code_" + phone, String.valueOf(userId));
            editor.putLong("cache_time_" + phone, System.currentTimeMillis());
            editor.apply();
        }
    }

    /**
     * 使用成长数据更新WebView
     */
    private void updateWebViewWithGrowthData() {
        if (growthData == null) {
            updateWebViewWithDefaultData();
            return;
        }

        String html = getPersonHtmlWithData();
        if (webView != null) {
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        }
    }

    /**
     * 使用默认数据更新WebView
     */
    private void updateWebViewWithDefaultData() {
        String html = getPersonHtmlWithDefaultData();
        if (webView != null) {
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView(View view) {
        webView = view.findViewById(R.id.webview_person);
        WebSettings webSettings = webView.getSettings();

        // WebView 核心配置
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setDefaultTextEncodingName("UTF-8");

        webView.setWebViewClient(new WebViewClient());

        // 添加 JavaScript 接口
        webView.addJavascriptInterface(new PersonalJsBridge(), "AndroidBridge");
    }

    /**
     * JavaScript 桥接类，处理前端点击事件
     */
    public class PersonalJsBridge {

        @JavascriptInterface
        public void goToGrowth() {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // 跳转到 GrowthFragment
                    ((MainActivity) getActivity()).switchToTab(R.id.menu_growth);
                });
            }
        }

        @JavascriptInterface
        public void goToHelping() {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Intent intent = new Intent(getActivity(), HelpingActivity.class);
                    startActivity(intent);
                });
            }
        }

        @JavascriptInterface
        public void goToPoint() {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Intent intent = new Intent(getActivity(), PointActivity.class);
                    intent.putExtra("points", data1.getPoints());
                    Log.d(TAG, "goToPoint: " + data1.getPoints());
                    startActivity(intent);
                });
            }
        }

        @JavascriptInterface
        public void goToMedal() {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Intent intent = new Intent(getActivity(), MedalActivity.class);
                    startActivity(intent);
                });
            }
        }

        @JavascriptInterface
        public void goToFeedback() {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Intent intent = new Intent(getActivity(), FeedBackActivity.class);
                    startActivity(intent);
                });
            }
        }

        @JavascriptInterface
        public void logout() {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // 清空缓存
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.clear();
                    editor.apply();

                    // 清空用户缓存
                    SharedPreferences userCache = getActivity().getSharedPreferences("user_cache", MODE_PRIVATE);
                    userCache.edit().clear().apply();

                    // 清空 WebView 缓存
                    if (webView != null) {
                        webView.clearCache(true);
                        webView.clearHistory();
                    }

                    // 跳转到登录页
                    Intent intent = new Intent(getActivity(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    getActivity().finish();

                    Toast.makeText(getActivity(), "已退出登录", Toast.LENGTH_SHORT).show();
                });
            }
        }

        @JavascriptInterface
        public void showToast(String message) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void refreshData() {
            // 刷新用户数据
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    fetchGrowthInfo();
                });
            }
        }
    }

    /**
     * 获取带真实数据的HTML
     */
    private String getPersonHtmlWithData() {
        if (growthData == null) {
            return getPersonHtmlWithDefaultData();
        }

        String name = sharedPreferences.getString("phone", "用户");
        // 隐藏手机号中间四位
        if (name != null && name.length() == 11) {
            name = name.substring(0, 3) + "****" + name.substring(7);
        }

        return "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=no\">\n" +
                "    <title>个人中心</title>\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/vue@2.7.14/dist/vue.js\"></script>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }\n" +
                "        body { background: #f5f7fa; }\n" +
                "        /* 顶部渐变栏 */\n" +
                "        .top-bar {\n" +
                "            height: 40px;\n" +
                "            background: linear-gradient(135deg, #a0cfff, #69b1ff);\n" +
                "            position: relative;\n" +
                "            border-bottom-left-radius: 20px;\n" +
                "            border-bottom-right-radius: 20px;\n" +
                "        }\n" +
                "        .top-bar::after {\n" +
                "            content: '👫';\n" +
                "            position: absolute;\n" +
                "            top: 50%;\n" +
                "            left: 50%;\n" +
                "            transform: translate(-50%, -50%);\n" +
                "            font-size: 30px;\n" +
                "            opacity: 0.3;\n" +
                "        }\n" +
                "        /* 卡片容器 */\n" +
                "        .card {\n" +
                "            background: white;\n" +
                "            border-radius: 16px;\n" +
                "            margin: -5px 16px 16px;\n" +
                "            padding: 20px;\n" +
                "            box-shadow: 0 2px 12px rgba(0,0,0,0.08);\n" +
                "        }\n" +
                "        /* 用户信息头部 */\n" +
                "        .user-header {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            margin-bottom: 24px;\n" +
                "        }\n" +
                "        .avatar {\n" +
                "            width: 40px;\n" +
                "            height: 40px;\n" +
                "            border-radius: 50%;\n" +
                "            background: #e6f7ff;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            margin-right: 16px;\n" +
                "        }\n" +
                "        .avatar::after {\n" +
                "            content: '👤';\n" +
                "            font-size: 18px;\n" +
                "        }\n" +
                "        .user-info h3 {\n" +
                "            font-size: 20px;\n" +
                "            font-weight: 600;\n" +
                "            margin-bottom: 4px;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "        }\n" +
                "        .level-tag {\n" +
                "            background: #cd7f32;\n" +
                "            color: white;\n" +
                "            font-size: 10px;\n" +
                "            padding: 4px 12px;\n" +
                "            border-radius: 12px;\n" +
                "            margin-left: 12px;\n" +
                "        }\n" +
                "        .user-info p {\n" +
                "            font-size: 16px;\n" +
                "            color: #666;\n" +
                "        }\n" +
                "        /* 数据统计行 */\n" +
                "        .stats-row {\n" +
                "            display: flex;\n" +
                "            justify-content: space-around;\n" +
                "            margin-bottom: 24px;\n" +
                "        }\n" +
                "        .stat-item {\n" +
                "            text-align: center;\n" +
                "            cursor: pointer;\n" +
                "            transition: opacity 0.2s;\n" +
                "        }\n" +
                "        .stat-item:active {\n" +
                "            opacity: 0.6;\n" +
                "        }\n" +
                "        .stat-number {\n" +
                "            font-size: 32px;\n" +
                "            font-weight: 600;\n" +
                "            color: #3b82f6;\n" +
                "            margin-bottom: 4px;\n" +
                "        }\n" +
                "        .stat-label {\n" +
                "            font-size: 14px;\n" +
                "            color: #666;\n" +
                "        }\n" +
                "        /* 升级进度条 */\n" +
                "        .progress-section {\n" +
                "            margin-bottom: 24px;\n" +
                "            cursor: pointer;\n" +
                "        }\n" +
                "        .progress-section:active {\n" +
                "            opacity: 0.6;\n" +
                "        }\n" +
                "        .progress-info {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            font-size: 14px;\n" +
                "            color: #666;\n" +
                "            margin-bottom: 12px;\n" +
                "        }\n" +
                "        .progress-bar {\n" +
                "            height: 6px;\n" +
                "            background: #e5e7eb;\n" +
                "            border-radius: 3px;\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "        .progress-fill {\n" +
                "            height: 100%;\n" +
                "            width: 0%;\n" +
                "            background: #3b82f6;\n" +
                "        }\n" +
                "        /* 菜单列表 */\n" +
                "        .menu-section {\n" +
                "            background: white;\n" +
                "            border-radius: 16px;\n" +
                "            margin: 0 16px 16px;\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "        .menu-item {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            padding: 18px 20px;\n" +
                "            border-bottom: 1px solid #f0f2f5;\n" +
                "            cursor: pointer;\n" +
                "            transition: background 0.2s;\n" +
                "        }\n" +
                "        .menu-item:active {\n" +
                "            background: #f5f5f5;\n" +
                "        }\n" +
                "        .menu-item:last-child {\n" +
                "            border-bottom: none;\n" +
                "        }\n" +
                "        .menu-icon {\n" +
                "            width: 36px;\n" +
                "            height: 36px;\n" +
                "            border-radius: 50%;\n" +
                "            margin-right: 16px;\n" +
                "        }\n" +
                "        .menu-text {\n" +
                "            flex: 1;\n" +
                "            font-size: 18px;\n" +
                "            font-weight: 500;\n" +
                "        }\n" +
                "        .menu-right {\n" +
                "            font-size: 16px;\n" +
                "            color: #999;\n" +
                "        }\n" +
                "        /* 退出登录按钮 */\n" +
                "        .logout-btn {\n" +
                "            background: white;\n" +
                "            border-radius: 16px;\n" +
                "            margin: 16px;\n" +
                "            padding: 16px;\n" +
                "            text-align: center;\n" +
                "            color: #ff4757;\n" +
                "            font-size: 18px;\n" +
                "            font-weight: 500;\n" +
                "            cursor: pointer;\n" +
                "            transition: background 0.2s;\n" +
                "        }\n" +
                "        .logout-btn:active {\n" +
                "            background: #f5f5f5;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div id=\"app\">\n" +
                "        <!-- 顶部渐变栏 -->\n" +
                "        <div class=\"top-bar\"></div>\n" +
                "\n" +
                "        <!-- 用户信息卡片 -->\n" +
                "        <div class=\"card\">\n" +
                "            <div class=\"user-header\">\n" +
                "                <div class=\"avatar\"></div>\n" +
                "                <div class=\"user-info\">\n" +
                "                    <h3>" + name + " <span class=\"level-tag\">" + data1.getLevelName() + "</span></h3>\n" +
                "                    <p>" + data1.getExperiencePoints() + " 经验值 | ID: " + data1.getUserId() + "</p>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "\n" +
                "            <div class=\"stats-row\">\n" +
                "                <div class=\"stat-item\" @click=\"goToGrowth\">\n" +
                "                    <div class=\"stat-number\">" + data1.getServiceHours() + "</div>\n" +
                "                    <div class=\"stat-label\">服务小时</div>\n" +
                "                </div>\n" +
                "                <div class=\"stat-item\" @click=\"goToGrowth\">\n" +
                "                    <div class=\"stat-number\">" + data1.getServicePeopleCount() + "</div>\n" +
                "                    <div class=\"stat-label\">帮助人数</div>\n" +
                "                </div>\n" +
                "                <div class=\"stat-item\" @click=\"goToMedal\">\n" +
                "                    <div class=\"stat-number\">" + data1.getMedalCount() + "</div>\n" +
                "                    <div class=\"stat-label\">获得勋章</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "\n" +
                "            <div class=\"progress-section\" @click=\"goToGrowth\">\n" +
                "                <div class=\"progress-info\">\n" +
                "                    <span>距离 " + data1.getNextLevelName() + " 还差 " + (data1.getNextLevelHours() - data1.getCurrentHours()) + " 小时</span>\n" +
                "                    <span>" + data1.getCurrentHours() + "/" + data1.getNextLevelHours() + "</span>\n" +
                "                </div>\n" +
                "                <div class=\"progress-bar\">\n" +
                "                    <div class=\"progress-fill\" :style=\"{width: progressPercent}\"></div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- 菜单列表 -->\n" +
                "        <div class=\"menu-section\">\n" +
                "            <div class=\"menu-item\" @click=\"goToHelping\">\n" +
                "                <div class=\"menu-icon\" style=\"background: #e6f7ff;\"></div>\n" +
                "                <div class=\"menu-text\">服务记录</div>\n" +
                "                <div class=\"menu-right\">查看全部</div>\n" +
                "            </div>\n" +
                "            <div class=\"menu-item\" @click=\"goToPoint\">\n" +
                "                <div class=\"menu-icon\" style=\"background: #fff2e6;\"></div>\n" +
                "                <div class=\"menu-text\">我的积分</div>\n" +
                "                <div class=\"menu-right\">" + data1.getPoints() + " 分</div>\n" +
                "            </div>\n" +
                "            <div class=\"menu-item\" @click=\"goToMedal\">\n" +
                "                <div class=\"menu-icon\" style=\"background: #f3e8ff;\"></div>\n" +
                "                <div class=\"menu-text\">勋章墙</div>\n" +
                "                <div class=\"menu-right\"></div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"menu-section\">\n" +
                "            <div class=\"menu-item\" @click=\"goToFeedback\">\n" +
                "                <div class=\"menu-icon\" style=\"background: #f0f2f5;\"></div>\n" +
                "                <div class=\"menu-text\">帮助与反馈</div>\n" +
                "                <div class=\"menu-right\"></div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- 退出登录按钮 -->\n" +
                "        <div class=\"logout-btn\" @click=\"logout\">\n" +
                "            退出登录\n" +
                "        </div>\n" +
                "        \n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        new Vue({\n" +
                "            el: '#app',\n" +
                "            data: {\n" +
                "                user: {\n" +
                "                    name: '" + name + "',\n" +
                "                    level: '" + data1.getLevelName() + "',\n" +
                "                    score: " + data1.getExperiencePoints() + ",\n" +
                "                    id: " + data1.getUserId() + ",\n" +
                "                    serviceHours: " + data1.getServiceHours() + ",\n" +
                "                    helpCount: " + data1.getServicePeopleCount() + ",\n" +
                "                    medalCount: " + data1.getMedalCount() + ",\n" +
                "                    points: " + data1.getPoints() + ",\n" +
                "                    nextLevelName: '" + data1.getNextLevelName() + "',\n" +
                "                    nextLevelHours: " + data1.getNextLevelHours() + ",\n" +
                "                    currentHours: " + data1.getCurrentHours() + "\n" +
                "                }\n" +
                "            },\n" +
                "            computed: {\n" +
                "                progressPercent() {\n" +
                "                    return (this.user.currentHours / this.user.nextLevelHours) * 100 + '%';\n" +
                "                }\n" +
                "            },\n" +
                "            methods: {\n" +
                "                goToGrowth() {\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.goToGrowth();\n" +
                "                    }\n" +
                "                },\n" +
                "                goToHelping() {\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.goToHelping();\n" +
                "                    }\n" +
                "                },\n" +
                "                goToPoint() {\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.goToPoint();\n" +
                "                    }\n" +
                "                },\n" +
                "                goToMedal() {\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.goToMedal();\n" +
                "                    }\n" +
                "                },\n" +
                "                goToFeedback() {\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.goToFeedback();\n" +
                "                    }\n" +
                "                },\n" +
                "                logout() {\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.logout();\n" +
                "                    }\n" +
                "                },\n" +
                "                showToast(msg) {\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.showToast(msg);\n" +
                "                    }\n" +
                "                },\n" +
                "                refreshData() {\n" +
                "                    if (window.AndroidBridge) {\n" +
                "                        window.AndroidBridge.refreshData();\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        });\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    /**
     * 获取默认数据的HTML
     */
    private String getPersonHtmlWithDefaultData() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=no\">\n" +
                "    <title>个人中心</title>\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/vue@2.7.14/dist/vue.js\"></script>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }\n" +
                "        body { background: #f5f7fa; }\n" +
                "        .top-bar {\n" +
                "            height: 40px;\n" +
                "            background: linear-gradient(135deg, #a0cfff, #69b1ff);\n" +
                "            position: relative;\n" +
                "            border-bottom-left-radius: 20px;\n" +
                "            border-bottom-right-radius: 20px;\n" +
                "        }\n" +
                "        .top-bar::after {\n" +
                "            content: '👫';\n" +
                "            position: absolute;\n" +
                "            top: 50%;\n" +
                "            left: 50%;\n" +
                "            transform: translate(-50%, -50%);\n" +
                "            font-size: 30px;\n" +
                "            opacity: 0.3;\n" +
                "        }\n" +
                "        .card {\n" +
                "            background: white;\n" +
                "            border-radius: 16px;\n" +
                "            margin: -5px 16px 16px;\n" +
                "            padding: 20px;\n" +
                "            box-shadow: 0 2px 12px rgba(0,0,0,0.08);\n" +
                "        }\n" +
                "        .user-header {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            margin-bottom: 24px;\n" +
                "        }\n" +
                "        .avatar {\n" +
                "            width: 60px;\n" +
                "            height: 60px;\n" +
                "            border-radius: 50%;\n" +
                "            background: #e6f7ff;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            margin-right: 16px;\n" +
                "        }\n" +
                "        .avatar::after {\n" +
                "            content: '👤';\n" +
                "            font-size: 30px;\n" +
                "        }\n" +
                "        .user-info h1 {\n" +
                "            font-size: 24px;\n" +
                "            font-weight: 600;\n" +
                "            margin-bottom: 4px;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "        }\n" +
                "        .level-tag {\n" +
                "            background: #cd7f32;\n" +
                "            color: white;\n" +
                "            font-size: 14px;\n" +
                "            padding: 4px 12px;\n" +
                "            border-radius: 12px;\n" +
                "            margin-left: 12px;\n" +
                "        }\n" +
                "        .user-info p {\n" +
                "            font-size: 16px;\n" +
                "            color: #666;\n" +
                "        }\n" +
                "        .stats-row {\n" +
                "            display: flex;\n" +
                "            justify-content: space-around;\n" +
                "            margin-bottom: 24px;\n" +
                "        }\n" +
                "        .stat-item {\n" +
                "            text-align: center;\n" +
                "            cursor: pointer;\n" +
                "            transition: opacity 0.2s;\n" +
                "        }\n" +
                "        .stat-item:active {\n" +
                "            opacity: 0.6;\n" +
                "        }\n" +
                "        .stat-number {\n" +
                "            font-size: 32px;\n" +
                "            font-weight: 600;\n" +
                "            color: #3b82f6;\n" +
                "            margin-bottom: 4px;\n" +
                "        }\n" +
                "        .stat-label {\n" +
                "            font-size: 14px;\n" +
                "            color: #666;\n" +
                "        }\n" +
                "        .progress-section {\n" +
                "            margin-bottom: 24px;\n" +
                "            cursor: pointer;\n" +
                "        }\n" +
                "        .progress-section:active {\n" +
                "            opacity: 0.6;\n" +
                "        }\n" +
                "        .progress-info {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            font-size: 16px;\n" +
                "            color: #666;\n" +
                "            margin-bottom: 12px;\n" +
                "        }\n" +
                "        .progress-bar {\n" +
                "            height: 6px;\n" +
                "            background: #e5e7eb;\n" +
                "            border-radius: 3px;\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "        .progress-fill {\n" +
                "            height: 100%;\n" +
                "            width: 0%;\n" +
                "            background: #3b82f6;\n" +
                "        }\n" +
                "        .menu-section {\n" +
                "            background: white;\n" +
                "            border-radius: 16px;\n" +
                "            margin: 0 16px 16px;\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "        .menu-item {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            padding: 18px 20px;\n" +
                "            border-bottom: 1px solid #f0f2f5;\n" +
                "            cursor: pointer;\n" +
                "            transition: background 0.2s;\n" +
                "        }\n" +
                "        .menu-item:active {\n" +
                "            background: #f5f5f5;\n" +
                "        }\n" +
                "        .menu-item:last-child {\n" +
                "            border-bottom: none;\n" +
                "        }\n" +
                "        .menu-icon {\n" +
                "            width: 36px;\n" +
                "            height: 36px;\n" +
                "            border-radius: 50%;\n" +
                "            margin-right: 16px;\n" +
                "        }\n" +
                "        .menu-text {\n" +
                "            flex: 1;\n" +
                "            font-size: 18px;\n" +
                "            font-weight: 500;\n" +
                "        }\n" +
                "        .menu-right {\n" +
                "            font-size: 16px;\n" +
                "            color: #999;\n" +
                "        }\n" +
                "        .logout-btn {\n" +
                "            background: white;\n" +
                "            border-radius: 16px;\n" +
                "            margin: 16px;\n" +
                "            padding: 16px;\n" +
                "            text-align: center;\n" +
                "            color: #ff4757;\n" +
                "            font-size: 18px;\n" +
                "            font-weight: 500;\n" +
                "            cursor: pointer;\n" +
                "            transition: background 0.2s;\n" +
                "        }\n" +
                "        .logout-btn:active {\n" +
                "            background: #f5f5f5;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div id=\"app\">\n" +
                "        <div class=\"top-bar\"></div>\n" +
                "        <div class=\"card\">\n" +
                "            <div class=\"user-header\">\n" +
                "                <div class=\"avatar\"></div>\n" +
                "                <div class=\"user-info\">\n" +
                "                    <h1>加载中... <span class=\"level-tag\">请稍候</span></h1>\n" +
                "                    <p>0 经验值 | ID: --</p>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            <div class=\"stats-row\">\n" +
                "                <div class=\"stat-item\">\n" +
                "                    <div class=\"stat-number\">--</div>\n" +
                "                    <div class=\"stat-label\">服务小时</div>\n" +
                "                </div>\n" +
                "                <div class=\"stat-item\">\n" +
                "                    <div class=\"stat-number\">--</div>\n" +
                "                    <div class=\"stat-label\">帮助人数</div>\n" +
                "                </div>\n" +
                "                <div class=\"stat-item\">\n" +
                "                    <div class=\"stat-number\">--</div>\n" +
                "                    <div class=\"stat-label\">获得勋章</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            <div class=\"progress-section\">\n" +
                "                <div class=\"progress-info\">\n" +
                "                    <span>加载中...</span>\n" +
                "                    <span>0/0</span>\n" +
                "                </div>\n" +
                "                <div class=\"progress-bar\">\n" +
                "                    <div class=\"progress-fill\" style=\"width:0%\"></div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class=\"menu-section\">\n" +
                "            <div class=\"menu-item\" @click=\"goToHelping\">\n" +
                "                <div class=\"menu-icon\" style=\"background: #e6f7ff;\"></div>\n" +
                "                <div class=\"menu-text\">服务记录</div>\n" +
                "                <div class=\"menu-right\">查看全部</div>\n" +
                "            </div>\n" +
                "            <div class=\"menu-item\" @click=\"goToPoint\">\n" +
                "                <div class=\"menu-icon\" style=\"background: #fff2e6;\"></div>\n" +
                "                <div class=\"menu-text\">我的积分</div>\n" +
                "                <div class=\"menu-right\">-- 分</div>\n" +
                "            </div>\n" +
                "            <div class=\"menu-item\" @click=\"goToMedal\">\n" +
                "                <div class=\"menu-icon\" style=\"background: #f3e8ff;\"></div>\n" +
                "                <div class=\"menu-text\">勋章墙</div>\n" +
                "                <div class=\"menu-right\"></div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class=\"menu-section\">\n" +
                "            <div class=\"menu-item\" @click=\"showToast('在线时段设置开发中')\">\n" +
                "                <div class=\"menu-icon\" style=\"background: #e6f9e6;\"></div>\n" +
                "                <div class=\"menu-text\">在线时段设置</div>\n" +
                "                <div class=\"menu-right\">每天 19:00-22:00</div>\n" +
                "            </div>\n" +
                "            <div class=\"menu-item\" @click=\"showToast('隐私设置开发中')\">\n" +
                "                <div class=\"menu-icon\" style=\"background: #e6f7ff;\"></div>\n" +
                "                <div class=\"menu-text\">隐私设置</div>\n" +
                "                <div class=\"menu-right\"></div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class=\"menu-section\">\n" +
                "            <div class=\"menu-item\" @click=\"goToFeedback\">\n" +
                "                <div class=\"menu-icon\" style=\"background: #f0f2f5;\"></div>\n" +
                "                <div class=\"menu-text\">帮助与反馈</div>\n" +
                "                <div class=\"menu-right\"></div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class=\"logout-btn\" @click=\"logout\">\n" +
                "            退出登录\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    <script>\n" +
                "        new Vue({\n" +
                "            el: '#app',\n" +
                "            data: {},\n" +
                "            methods: {\n" +
                "                goToGrowth() {\n" +
                "                    if(window.AndroidBridge) window.AndroidBridge.goToGrowth();\n" +
                "                },\n" +
                "                goToHelping() {\n" +
                "                    if(window.AndroidBridge) window.AndroidBridge.goToHelping();\n" +
                "                },\n" +
                "                goToPoint() {\n" +
                "                    if(window.AndroidBridge) window.AndroidBridge.goToPoint();\n" +
                "                },\n" +
                "                goToMedal() {\n" +
                "                    if(window.AndroidBridge) window.AndroidBridge.goToMedal();\n" +
                "                },\n" +
                "                goToFeedback() {\n" +
                "                    if(window.AndroidBridge) window.AndroidBridge.goToFeedback();\n" +
                "                },\n" +
                "                logout() {\n" +
                "                    if(window.AndroidBridge) window.AndroidBridge.logout();\n" +
                "                },\n" +
                "                showToast(msg) {\n" +
                "                    if(window.AndroidBridge) window.AndroidBridge.showToast(msg);\n" +
                "                },\n" +
                "                refreshData() {\n" +
                "                    if(window.AndroidBridge) window.AndroidBridge.refreshData();\n" +
                "                }\n" +
                "            }\n" +
                "        });\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            // 每次返回时刷新数据
            fetchGrowthInfo();
        }
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
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}