package com.example.myapplication.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.activity.AboutActivity;
import com.example.myapplication.activity.FeedbackActivity;
import com.example.myapplication.activity.HelpActivity;
import com.example.myapplication.activity.login.LoginActivity;
import com.example.myapplication.data.UserData;
import com.example.myapplication.manage.QwenManager;
import com.example.myapplication.manage.SimpleAsrManager;
import com.example.myapplication.manage.SimpleWakeUpManager;
import com.example.myapplication.utils.OkhttpUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class PersonalFragment extends Fragment {

    private static final String TAG = "PersonalFragment";

    // UI 控件
    private TextView tvUserName;
    private TextView tvUserStatus;
    private LinearLayout llVoiceSetting;
    private LinearLayout llVibrateFeedback;
    private LinearLayout llNetwork;
    private LinearLayout llFeedback;
    private LinearLayout llHelp;
    private LinearLayout llAbout;
    private Button btLogout;

    private String phone = new String();
    private UserData userData;

    // --- 语音与AI相关变量 ---
    private SimpleWakeUpManager wakeUpManager;
    private SimpleAsrManager asrManager;
    private TextToSpeech tts;
    private QwenManager qwenManager;

    // 核心控制变量：语音助手是否处于激活状态
    private boolean isVoiceActive = false;

    // --- 自动休眠机制 ---
    private Handler sleepHandler = new Handler();
    // 60秒无交互自动休眠
    private static final long SLEEP_DELAY = 60000;

    private Runnable sleepRunnable = new Runnable() {
        @Override
        public void run() {
            if (isVoiceActive) {
                isVoiceActive = false;
                Log.d(TAG, "语音助手自动休眠");
                if (getActivity() != null) {
                    Toast.makeText(getActivity(), "语音助手已休眠", Toast.LENGTH_SHORT).show();
                }
                // 确保唤醒功能开启，等待下一次唤醒
                if (wakeUpManager != null) wakeUpManager.start();
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_personal, container, false);

        // 初始化控件
        tvUserName = view.findViewById(R.id.tvUserName);
        tvUserStatus = view.findViewById(R.id.tvUserStatus);
        llFeedback = view.findViewById(R.id.llFeedback);
        llHelp = view.findViewById(R.id.llHelp);
        llAbout = view.findViewById(R.id.llAbout);
        btLogout = view.findViewById(R.id.btLogout);

        // 初始化AI管理器
        qwenManager = new QwenManager();

        // 为每个功能项设置双击监听
        setupDoubleClick(llFeedback, "意见反馈");
        setupDoubleClick(llHelp, "使用帮助");
        setupDoubleClick(llAbout, "关于我们");
        setupDoubleClick(btLogout, "退出登录");

        initData();

        // 初始化语音助手组件
        initTTS();
        initVoiceAssistant();

        return view;
    }

    // --- 语音助手核心逻辑 Start ---

    private void refreshSleepTimer() {
        if (!isVoiceActive) return;
        sleepHandler.removeCallbacks(sleepRunnable);
        sleepHandler.postDelayed(sleepRunnable, SLEEP_DELAY);
    }

    private void initTTS() {
        if (getActivity() == null) return;
        tts = new TextToSpeech(getActivity(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                if (tts != null) tts.setLanguage(Locale.CHINESE);
            }
        });

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (!isVoiceActive) return;
                    new Handler().postDelayed(() -> {
                        if (isAdded() && isVoiceActive && asrManager != null) {
                            Toast.makeText(getActivity(), "请说话...", Toast.LENGTH_SHORT).show();
                            asrManager.start();
                        }
                    }, 500);
                });
            }

            @Override
            public void onError(String utteranceId) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (wakeUpManager != null) wakeUpManager.start();
                });
            }
        });
    }

    private void initVoiceAssistant() {
        if (getActivity() == null) return;

        // 1. 初始化ASR
        asrManager = new SimpleAsrManager(getActivity(), new SimpleAsrManager.OnAsrListener() {
            @Override
            public void onResult(String text) {
                refreshSleepTimer();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> processCommand(text));
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "识别错误: " + error);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (wakeUpManager != null) wakeUpManager.start();
                    });
                }
            }
        });

        // 2. 初始化唤醒
        wakeUpManager = new SimpleWakeUpManager(getActivity(), new SimpleWakeUpManager.WakeUpListener() {
            @Override
            public void onSuccess(String word) {
                Log.d(TAG, "唤醒成功: " + word);
                if (getActivity() != null) {
                    Toast.makeText(getActivity(), "我在！", Toast.LENGTH_SHORT).show();
                }
                isVoiceActive = true;
                wakeUpManager.stop();
                refreshSleepTimer();
                speak("我在，这是个人中心，您想做什么？");
            }

            @Override
            public void onError(String errorMsg) {
                Log.e(TAG, "唤醒失败: " + errorMsg);
            }
        });

        wakeUpManager.start();
    }

    private void processCommand(String text) {
        if (!isVoiceActive) return;
        if (text == null || text.trim().isEmpty()) return;

        Log.d(TAG, "用户说: " + text);

        // --- 1. 页面跳转逻辑 ---
        if (text.contains("首页") || text.contains("功能") || text.contains("回去")) {
            speak("好的，返回功能首页");
            new Handler().postDelayed(() -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).switchToTab(R.id.menu_function);
                }
            }, 2000);
            return;
        }

        if (text.contains("社区")) {
            speak("好的，前往社区页面");
            new Handler().postDelayed(() -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).switchToTab(R.id.menu_community);
                }
            }, 2000);
            return;
        }

        // --- 2. 页面内功能控制 ---
        if (text.contains("退出") || text.contains("注销")) {
            speak("正在为您退出登录");
            new Handler().postDelayed(() -> {
                handleAction("退出登录");
            }, 2000);
            return;
        }

        if (text.contains("反馈") || text.contains("建议")) {
            speak("正在打开意见反馈页面");
            handleAction("意见反馈");
            return;
        }

        if (text.contains("帮助")) {
            speak("正在打开使用帮助");
            handleAction("使用帮助");
            return;
        }

        if (text.contains("关于我们")) {
            speak("正在打开关于我们页面");
            handleAction("关于我们");
            return;
        }

        // --- 3. 页面介绍 ---
        if (text.contains("什么页面") || text.contains("哪里") || text.contains("介绍")) {
            speak("这是个人中心页面，您可以设置语音，查看帮助或退出登录。");
            return;
        }

        if (text.contains("有什么") || text.contains("功能")) {
            speak("个人中心有语音设置、意见反馈、使用帮助、关于我们和退出登录功能。");
            return;
        }
    }

    private void speak(String text) {
        if (!isVoiceActive) return;
        if (tts != null) {
            if (tts.isSpeaking()) tts.stop();
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageID");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "messageID");
            refreshSleepTimer();
        }
    }

    // --- 生命周期处理 ---

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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeUpManager != null) wakeUpManager.release();
        if (asrManager != null) asrManager.release();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (sleepHandler != null) {
            sleepHandler.removeCallbacksAndMessages(null);
        }
    }

    // --- 原有逻辑代码 ---

    private void initData() {
        if (getActivity() == null) return;
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("phone", MODE_PRIVATE);
        phone = sharedPreferences.getString("phone", null);
        if (TextUtils.isEmpty(phone)) {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            String msg = "登录信息已过期，请重新登录";
            Toast.makeText(getActivity(), "" + msg, Toast.LENGTH_SHORT).show();
            startActivity(intent);
            getActivity().finish();
        } else {
            initUserInfo();
            if (phone.length() >= 11) {
                String maskedPhone = phone.substring(0, 3) + "****" + phone.substring(7, 11);
                tvUserName.setText("用户" + maskedPhone);
                tvUserName.setContentDescription("用户" + maskedPhone);
            }
            tvUserStatus.setText("已认证·助盲用户");
        }
    }

    private void initUserInfo() {
        OkhttpUtils.request("GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("onFailure", "onFailure: " + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() != null) {
                    String json = response.body().string();
                    userData = OkhttpUtils.toData(json, UserData.class);
                }
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDoubleClick(View view, final String action) {
        if (getActivity() == null) return;

        final GestureDetector detector = new GestureDetector(getActivity(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        new Handler().postDelayed(() ->  handleAction(action), 1500);
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        Toast.makeText(getActivity(), "单击了" + action + "，双击进入",
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });

        view.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    private void handleAction(String action) {
        if (getActivity() == null) return;

        switch (action) {
            case "意见反馈":
                Toast.makeText(getActivity(), "进入意见反馈页面", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(getActivity(), FeedbackActivity.class));
                break;
            case "使用帮助":
                Toast.makeText(getActivity(), "进入使用帮助页面", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(getActivity(), HelpActivity.class));
                break;
            case "关于我们":
                Toast.makeText(getActivity(), "进入关于我们页面", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(getActivity(), AboutActivity.class));
                break;
            case "退出登录":
                SharedPreferences sharedPreferences = getActivity().getSharedPreferences("phone", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.clear();
                editor.apply();
                Toast.makeText(getActivity(), "已退出登录", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(this.getActivity(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                getActivity().finish();
                break;
        }
    }
}