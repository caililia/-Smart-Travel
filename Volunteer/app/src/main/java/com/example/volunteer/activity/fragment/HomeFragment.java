package com.example.volunteer.activity.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteer.CallActivity;
import com.example.volunteer.R;
import com.example.volunteer.VideoCallActivity;
import com.example.volunteer.activity.login.LoginActivity;
import com.example.volunteer.adapter.TaskAdapter;
import com.example.volunteer.data.Room;
import com.example.volunteer.socket.WebSocketManager;
import com.example.volunteer.utils.OkhttpUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.content.SharedPreferences;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class HomeFragment extends Fragment implements WebSocketManager.WebSocketListener {

    private static final String TAG = "HomeFragment";
    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private List<Room.DataBean> taskList = new ArrayList<>();
    private WebSocketManager webSocketManager;
    private String phone = "";

    // 时间格式化
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        initWebSocket();
        loadTasks();
        initData();
    }



    /**
     * 初始化视图组件
     */
    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view_tasks);
    }

    private void initData() {
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("phone", MODE_PRIVATE);
        phone = sharedPreferences.getString("phone", null);

        if (TextUtils.isEmpty(phone)) {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            String msg = "登录信息已过期，请重新登录";
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            startActivity(intent);
            this.onResume();
            return;
        }
    }

    /**
     * 设置RecyclerView
     */
    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        taskAdapter = new TaskAdapter(getActivity(), taskList, new TaskAdapter.OnTaskActionListener() {
            @Override
            public void onAcceptTask(Room.DataBean task) {
                acceptTask(task);
            }
        });
        recyclerView.setAdapter(taskAdapter);
    }

    private void acceptTask(Room.DataBean task) {
        if (task == null || getActivity() == null) {
            return;
        }
        Intent intent = new Intent();
        intent.putExtra("roomId", task.getRoomId());
        if ("1".equals(task.getCallType())) {
            intent.setClass(getActivity(), VideoCallActivity.class);
        } else {
            intent.setClass(getActivity(), CallActivity.class);
        }
        startActivity(intent);
    }

    /**
     * 初始化WebSocket连接
     */
    private void initWebSocket() {
        webSocketManager = WebSocketManager.getInstance();
        webSocketManager.setListener(this);
        webSocketManager.connect();
    }

    /**
     * 对任务列表进行排序
     * 排序规则：紧急求助(fullDown=1) > 时间(最新在前) > 视频(callType=1) > 语音(callType=0)
     */
    private void sortTaskList(List<Room.DataBean> list) {
        if (list == null || list.isEmpty()) {
            return;
        }

        Collections.sort(list, new Comparator<Room.DataBean>() {
            @Override
            public int compare(Room.DataBean task1, Room.DataBean task2) {
                // 第一优先级：紧急求助（fullDown=1 优先）
                boolean isEmergency1 = "1".equals(task1.getFullDown());
                boolean isEmergency2 = "1".equals(task2.getFullDown());

                if (isEmergency1 != isEmergency2) {
                    // 紧急求助排前面
                    return isEmergency1 ? -1 : 1;
                }

                // 第二优先级：时间（最新的在前）
                try {
                    Date date1 = dateFormat.parse(task1.getCreateTime());
                    Date date2 = dateFormat.parse(task2.getCreateTime());
                    if (date1 != null && date2 != null) {
                        int timeCompare = date2.compareTo(date1);
                        if (timeCompare != 0) {
                            return timeCompare;
                        }
                    }
                } catch (ParseException e) {
                    Log.e(TAG, "时间解析失败: " + e.getMessage());
                }

                // 第三优先级：视频优先于语音（callType=1 视频优先，callType=0 语音）
                boolean isVideo1 = "1".equals(task1.getCallType());
                boolean isVideo2 = "1".equals(task2.getCallType());

                if (isVideo1 != isVideo2) {
                    return isVideo1 ? -1 : 1;
                }

                // 如果所有规则都相同，则按call_id降序（最新的ID更大）
                return Integer.compare(task2.getCall_id(), task1.getCall_id());
            }
        });
    }

    /**
     * 加载任务列表（HTTP请求）
     */
    private void loadTasks() {
        if (getActivity() != null) {
        }

        OkhttpUtils.request("GET", OkhttpUtils.URL + OkhttpUtils.RoomList, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "onFailure: 网络请求失败", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "onResponse: " + json);

                    try {
                        // 解析完整的Room对象
                        Gson gson = new Gson();
                        Type type = new TypeToken<Room>(){}.getType();
                        Room room = gson.fromJson(json, type);

                        if (room != null && room.getCode() == 200) {
                            List<Room.DataBean> dataList = room.getData();
                            Log.e(TAG, "onResponse: 原始数据获取到" + (dataList != null ? dataList.size() : 0) + "个任务");

                            if (dataList != null && !dataList.isEmpty()) {
                                // 对数据进行排序
                                sortTaskList(dataList);

                                // 打印排序后的结果用于调试
                                for (int i = 0; i < dataList.size(); i++) {
                                    Room.DataBean task = dataList.get(i);
                                    Log.d(TAG, String.format("排序后第%d位: call_id=%d, fullDown=%s, callType=%s, createTime=%s",
                                            i + 1, task.getCall_id(), task.getFullDown(),
                                            task.getCallType(), task.getCreateTime()));
                                }

                                // 更新UI
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        taskList.clear();
                                        taskList.addAll(dataList);
                                        taskAdapter.notifyDataSetChanged();
                                    });
                                }
                            } else {
                                // 没有数据
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        taskList.clear();
                                        taskAdapter.notifyDataSetChanged();
                                    });
                                }
                            }
                        } else {
                            // API返回错误
                            String message = room != null ? room.getMessage() : "未知错误";
                            Log.e(TAG, "onResponse: API错误 - " + message);
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                });
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "onResponse: 数据解析失败", e);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                            });
                        }
                    }
                } else {
                    Log.e(TAG, "onResponse: 响应失败，code=" + response.code());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                        });
                    }
                }
            }
        });
    }

    @Override
    public void onRoomDataChanged(String message) {
        Log.d(TAG, "onRoomDataChanged: " + message);
        // 收到房间数据变化，刷新任务列表
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                loadTasks(); // 重新加载任务列表并自动排序
            });
        }
    }

    @Override
    public void onConnectionStatusChanged(boolean connected) {
        Log.d(TAG, "onConnectionStatusChanged: " + connected);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (connected) {
                    Toast.makeText(getActivity(), "实时任务更新已启用", Toast.LENGTH_SHORT).show();
                } else {
                }
            });
        }
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "onError: " + message);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
            });
        }
    }

    @Override
    public void onMessageReceived(String type, String message) {
        Log.d(TAG, "onMessageReceived - type: " + type + ", message: " + message);

        // 根据消息类型处理不同的业务逻辑
        switch (type) {
            case "NEW_TASK":
                handleNewTask(message);
                break;
            case "TASK_UPDATED":
                handleTaskUpdate(message);
                break;
            case "TASK_CANCELLED":
                handleTaskCancelled(message);
                break;
            case "PONG":
                Log.d(TAG, "收到Pong响应");
                break;
            default:
                Log.d(TAG, "未处理的消息类型: " + type);
                break;
        }
    }

    private void handleNewTask(String message) {
        Log.d(TAG, "收到新任务通知: " + message);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                loadTasks(); // 刷新任务列表并重新排序
                Toast.makeText(getActivity(), "有新任务到达！", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void handleTaskUpdate(String message) {
        Log.d(TAG, "收到任务更新通知: " + message);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                loadTasks(); // 刷新任务列表并重新排序
            });
        }
    }

    private void handleTaskCancelled(String message) {
        Log.d(TAG, "收到任务取消通知: " + message);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                loadTasks(); // 刷新任务列表并重新排序
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 页面恢复时检查WebSocket连接状态
        if (webSocketManager != null && !webSocketManager.isConnected()) {
            webSocketManager.connect();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView = null;
        taskAdapter = null;
    }
}