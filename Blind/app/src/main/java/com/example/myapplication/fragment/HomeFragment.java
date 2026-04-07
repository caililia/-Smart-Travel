package com.example.myapplication.fragment;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.activity.CallActivity;
import com.example.myapplication.activity.CameraActivity;
import com.example.myapplication.activity.VideoCallActivity;
import com.example.myapplication.activity.full_down.FallConfirmActivity;
import com.example.myapplication.activity.login.LoginActivity;
import com.example.myapplication.manage.QwenManager;
import com.example.myapplication.manage.SimpleAsrManager;
import com.example.myapplication.manage.SimpleWakeUpManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private CardView cvVoiceCall, cvVideoCall, cvEnvRecog;
    private CardView cvTest;
    private String phone;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 101;
    private static final int BLUETOOTH_PERMISSION_REQUEST_CODE = 100;
    private static final int AUDIO_PERMISSION_REQUEST_CODE = 102; // 新增录音权限请求码

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
                Log.d(TAG, "小黎自动休眠");
                if (getActivity() != null) {
                    Toast.makeText(getActivity(), "小黎已休眠", Toast.LENGTH_SHORT).show();
                }
                // 确保唤醒功能开启，等待下一次唤醒
                if (wakeUpManager != null) wakeUpManager.start();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化控件
        cvVoiceCall = view.findViewById(R.id.cvVoiceCall);
        cvVideoCall = view.findViewById(R.id.cvVideoCall);
        cvEnvRecog = view.findViewById(R.id.cvEnvRecog);
        cvTest = view.findViewById(R.id.cvTest);

        // 初始化AI管理器
        qwenManager = new QwenManager();

        initData();
        checkAndRequestBluetoothPermission();

        // 检查录音权限（用于语音助手）
        checkAndRequestAudioPermission();

        // 初始化语音助手组件
        initTTS();
        initVoiceAssistant();

        // 设置按钮动画和点击事件
        setButtonAnimation(cvVoiceCall, "语音通话功能", CallActivity.class);
        setButtonAnimation(cvVideoCall, "视频通话功能", VideoCallActivity.class);
        setButtonAnimation(cvEnvRecog, "环境识别功能", CameraActivity.class);
        setButtonAnimation(cvTest, "跌倒测试功能", FallConfirmActivity.class);

        if (!checkCameraPermission()) {
            requestCameraPermission();
        }
    }

    /**
     * 检查并请求录音权限
     */
    private void checkAndRequestAudioPermission() {
        if (getActivity() == null) return;
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    AUDIO_PERMISSION_REQUEST_CODE);
        }
    }

    // --- 语音助手核心逻辑 Start ---

    /**
     * 刷新休眠倒计时
     */
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
                    // 只有在激活状态下才继续监听
                    if (!isVoiceActive) return;

                    // 延迟开启识别，避免录入TTS的尾音
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

                // 激活语音状态
                isVoiceActive = true;
                wakeUpManager.stop();
                refreshSleepTimer();

                speak("我在，您想了解什么？");
            }

            @Override
            public void onError(String errorMsg) {
                Log.e(TAG, "唤醒失败: " + errorMsg);
            }
        });

        // 启动唤醒检测
        wakeUpManager.start();
    }

    /**
     * 处理用户指令
     */
    private void processCommand(String text) {
        if (!isVoiceActive) return;
        if (text == null || text.trim().isEmpty()) return;

        Log.d(TAG, "用户说: " + text);


        if (text.contains("社区")) {
            speak("好的，正在为您跳转到社区页面");
            // 延时一点点，保证语音播报能开始
            new Handler().postDelayed(() -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).switchToTab(R.id.menu_community);
                }
            }, 1000);
            return;
        }

        // 跳转到个人页面
        if (text.contains("个人") || text.contains("我的")) {
            speak("好的，正在为您跳转到个人页面");
            new Handler().postDelayed(() -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).switchToTab(R.id.menu_personal);
                }
            }, 1000);
            return;
        }

        /*本地规则匹配 (满足特定问答需求)
        * 询问当前页面是什么/在哪里
        * */
        if (text.contains("什么页面") || text.contains("哪里") || text.contains("介绍一下")) {
            speak("本app包括功能、社区、个人三个板块，现在在功能板块中");
            return;
        }

        //询问有什么功能
        if (text.contains("有什么") || text.contains("功能")) {
            speak("功能页面有语音通话 、 视频通话 、 环境识别三个功能");
            return;
        }

        //语音控制跳转（可选增强功能）
        if (text.contains("视频")) {
            speak("正在为您打开视频通话");
            cvVideoCall.performClick();
            return;
        } else if (text.contains("语音通话")) {
            speak("正在为您打开语音通话");
            cvVoiceCall.performClick();
            return;
        } else if (text.contains("环境") || text.contains("识别")) {
            speak("正在为您打开环境识别");
            cvEnvRecog.performClick();
            return;
        }

        // --- 其他问题交给大模型 ---
        String prompt = "当前在APP的功能首页。用户问：" + text;
        if (getActivity() != null) {
            Toast.makeText(getActivity(), "思考中...", Toast.LENGTH_SHORT).show();
        }

        qwenManager.sendMessage(prompt, new QwenManager.QwenCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> handleAiResponse(jsonResponse));
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> speak("网络好像有点问题"));
                }
            }
        });
    }

    private void handleAiResponse(String jsonStr) {
        if (!isVoiceActive) return;
        refreshSleepTimer();

        try {
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.replace("```json", "").replace("```", "");
            }
            JSONObject result = new JSONObject(jsonStr);
            String reply = result.optString("reply");

            if (!reply.isEmpty()) {
                speak(reply);
            } else {
                if (wakeUpManager != null) wakeUpManager.start();
            }

        } catch (JSONException e) {
            e.printStackTrace();
            speak("我没太听懂");
        }
    }

    private void speak(String text) {
        // 只有激活状态才发声
        if (!isVoiceActive) return;

        if (tts != null) {
            if (tts.isSpeaking()) {
                tts.stop();
            }
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageID");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "messageID");

            refreshSleepTimer();
        }
    }

    // --- 生命周期管理 ---

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

    // --- 语音助手逻辑 End ---

    private void checkAndRequestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(getContext(),
                    Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        getActivity(),
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        BLUETOOTH_PERMISSION_REQUEST_CODE
                );
            }
        } else {
            if (ContextCompat.checkSelfPermission(getContext(),
                    Manifest.permission.BLUETOOTH)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        getActivity(),
                        new String[]{Manifest.permission.BLUETOOTH},
                        BLUETOOTH_PERMISSION_REQUEST_CODE
                );
            }
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(
                getActivity(),
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getContext(), "已授予摄像头权限", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "需要相机权限才能使用视频功能", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initData() {
        if (getActivity() != null) {
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("phone", getActivity().MODE_PRIVATE);
            phone = sharedPreferences.getString("phone", "");

            if (TextUtils.isEmpty(phone)) {
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                Toast.makeText(getActivity(), "登录信息已过期，请重新登录", Toast.LENGTH_SHORT).show();
                startActivity(intent);
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }
        }
    }

    private void setButtonAnimation(CardView cardView, final String tip, final Class<?> targetActivity) {
        cardView.setOnClickListener(v -> {
            ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 0.95f);
            ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 0.95f);
            scaleDownX.setDuration(100);
            scaleDownY.setDuration(100);

            final ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1f);
            final ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1f);
            scaleUpX.setDuration(100);
            scaleUpY.setDuration(100);

            scaleDownX.start();
            scaleDownY.start();

            scaleDownX.addListener(new android.animation.Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(android.animation.Animator animation) {}

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    scaleUpX.start();
                    scaleUpY.start();

                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), tip + "被触发", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(getActivity(), targetActivity);
                        startActivity(intent);
                    }
                }

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {}

                @Override
                public void onAnimationRepeat(android.animation.Animator animation) {}
            });
        });
    }
}