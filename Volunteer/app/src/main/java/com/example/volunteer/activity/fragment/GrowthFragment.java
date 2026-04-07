package com.example.volunteer.activity.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteer.R;
import com.example.volunteer.activity.activity.MedalActivity;
import com.example.volunteer.activity.activity.PointActivity;
import com.example.volunteer.activity.login.LoginActivity;
import com.example.volunteer.data.Growth;
import com.example.volunteer.data.UserData;
import com.example.volunteer.utils.OkhttpUtils;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import static android.content.Context.MODE_PRIVATE;

public class GrowthFragment extends Fragment {

    private static final String TAG = "GrowthFragment";
    private Gson gson = new Gson();
    private SharedPreferences sharedPreferences;
    private String userId;
    private String phone;
    private UserData userData;
    private Growth growth;

    // UI 组件
    private ScrollView scrollView;
    private LinearLayout loadingLayout;
    private LinearLayout contentLayout;

    // 用户信息
    private TextView tvLevelName;
    private TextView tvUserId;
    private TextView tvServiceHours;
    private TextView tvServicePeopleCount;
    private TextView tvExperiencePoints;
    private TextView tvPoints;

    // 进度条相关
    private TextView tvProgressInfo;
    private TextView tvProgressValue;
    private ProgressBar progressBar;

    // 勋章列表
    private RecyclerView rvMedals;
    private MedalAdapter medalAdapter;
    private TextView tvEmptyMedal;

    // 积分商城
    private CardView cvPointsMall;
    private int points;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_growth, container, false);
        sharedPreferences = getActivity().getSharedPreferences("phone", MODE_PRIVATE);

        initViews(view);
        initData();

        return view;
    }

    private void initViews(View view) {
        scrollView = view.findViewById(R.id.scroll_view);
        contentLayout = view.findViewById(R.id.content_layout);

        // 用户信息
        tvLevelName = view.findViewById(R.id.tv_level_name);
        tvUserId = view.findViewById(R.id.tv_user_id);
        tvServiceHours = view.findViewById(R.id.tv_service_hours);
        tvServicePeopleCount = view.findViewById(R.id.tv_service_people_count);
        tvExperiencePoints = view.findViewById(R.id.tv_experience_points);
        tvPoints = view.findViewById(R.id.tv_points);

        // 进度条
        tvProgressInfo = view.findViewById(R.id.tv_progress_info);
        tvProgressValue = view.findViewById(R.id.tv_progress_value);
        progressBar = view.findViewById(R.id.progress_bar);

        // 勋章列表
        rvMedals = view.findViewById(R.id.rv_medals);
        tvEmptyMedal = view.findViewById(R.id.tv_empty_medal);

        // 设置勋章列表为网格布局，每行4个
        rvMedals.setLayoutManager(new GridLayoutManager(getContext(), 4));
        medalAdapter = new MedalAdapter();
        rvMedals.setAdapter(medalAdapter);

        // 积分商城点击事件
        cvPointsMall = view.findViewById(R.id.cv_points_mall);
        cvPointsMall.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), PointActivity.class);
            intent.putExtra("points", points);
            startActivity(intent);
        });

        // 查看全部勋章点击事件
        TextView tvViewAllMedals = view.findViewById(R.id.tv_view_all_medals);
        /*tvViewAllMedals.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MedalActivity.class);
            startActivity(intent);
        });*/
    }

    private void initData() {
        phone = sharedPreferences.getString("phone", null);

        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(getContext(), "登录信息已过期，请重新登录", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            startActivity(intent);
            getActivity().finish();
            return;
        }
        fetchUserInfo();
    }

    private void fetchUserInfo() {
        OkhttpUtils.request("GET", OkhttpUtils.URL + OkhttpUtils.GETUSERINFO + "/" + phone, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取用户信息失败", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "获取用户信息失败", Toast.LENGTH_SHORT).show();
                         
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "用户信息响应: " + json);
                    userData = OkhttpUtils.toData(json, UserData.class);
                    if (userData != null && userData.getData() != null) {
                        userId = String.valueOf(userData.getData().getUserId());
                        Log.d(TAG, "用户ID: " + userId);
                        fetchGrowthData();
                    } else {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "获取用户信息失败", Toast.LENGTH_SHORT).show();
                                 
                            });
                        }
                    }
                }
            }
        });
    }

    private void fetchGrowthData() {
        if (TextUtils.isEmpty(userId)) {
            Log.e(TAG, "userId为空，无法获取成长数据");
             
            return;
        }

        OkhttpUtils.request("GET", OkhttpUtils.URL + OkhttpUtils.Growth + "/" + userId, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取成长数据失败", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "获取成长数据失败", Toast.LENGTH_SHORT).show();
                         
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "成长数据响应: " + json);
                    try {
                        growth = OkhttpUtils.toData(json, Growth.class);
                        Log.d(TAG, "解析后的Growth对象: " + (growth != null ? growth.toString() : "null"));
                        if (growth != null && growth.getCode() == 200 && growth.getData() != null) {
                            Growth.DataBean data = growth.getData();
                            Log.d(TAG, "成长数据: userId=" + data.getUserId() +
                                    ", serviceHours=" + data.getServiceHours() +
                                    ", levelName=" + data.getLevelName() +
                                    ", medals=" + data.getMedals());
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> updateUI(data));
                            }
                        } else {
                            Log.e(TAG, "接口返回错误: code=" + (growth != null ? growth.getCode() : "null"));
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "数据加载失败", Toast.LENGTH_SHORT).show();
                                     
                                });
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析成长数据失败", e);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "数据解析失败", Toast.LENGTH_SHORT).show();
                                 
                            });
                        }
                    }
                }
            }
        });
    }

    private void updateUI(Growth.DataBean data) {
        // 更新用户信息
        tvLevelName.setText(data.getLevelName() != null ? data.getLevelName() : "青铜志愿者");
        tvUserId.setText(String.valueOf(data.getUserId()));
        tvServiceHours.setText(String.valueOf(data.getServiceHours()));
        tvServicePeopleCount.setText(String.valueOf(data.getServicePeopleCount()));
        tvExperiencePoints.setText(String.valueOf(data.getExperiencePoints()));
        tvPoints.setText(String.valueOf(data.getPoints()));
        points = data.getPoints();

        // 更新进度条
        int currentHours = data.getCurrentHours();
        int nextLevelHours = data.getNextLevelHours();
        int needHours = nextLevelHours - currentHours;

        String nextLevelName = data.getNextLevelName() != null ? data.getNextLevelName() : "下一级";
        tvProgressInfo.setText(String.format("距离 %s 还差 %d 小时", nextLevelName, needHours));
        tvProgressValue.setText(String.format("%d/%d", currentHours, nextLevelHours));

        int progressPercent = (int) ((float) currentHours / nextLevelHours * 100);
        progressBar.setProgress(progressPercent);

        // 更新勋章列表
        String medalCodes = data.getMedals();
        Log.d(TAG, "勋章编码列表: " + medalCodes);

        String medalsStr = data.getMedals();
        Log.d(TAG, "勋章字符串: " + medalsStr);

        if (medalsStr != null && !medalsStr.isEmpty() && !"null".equals(medalsStr)) {
            // 按逗号分割字符串
            String[] medalArray = medalsStr.split(",");
            List<Medal> medalList = new ArrayList<>();

            for (String medalCode : medalArray) {
                String trimmedCode = medalCode.trim();
                if (!trimmedCode.isEmpty()) {
                    Medal medal = getMedalByCode(trimmedCode);
                    if (medal != null) {
                        medalList.add(medal);
                        Log.d(TAG, "添加勋章: " + medal.medalName + " (编码: " + trimmedCode + ")");
                    }
                }
            }

            if (!medalList.isEmpty()) {
                medalAdapter.setMedalList(medalList);
                rvMedals.setVisibility(View.VISIBLE);
                tvEmptyMedal.setVisibility(View.GONE);
            } else {
                rvMedals.setVisibility(View.GONE);
                tvEmptyMedal.setVisibility(View.VISIBLE);
            }
        } else {
            rvMedals.setVisibility(View.GONE);
            tvEmptyMedal.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 根据勋章编码获取勋章信息
     */
    private Medal getMedalByCode(String medalCode) {
        Medal medal = new Medal();
        medal.medalCode = medalCode;

        if (medalCode == null) return null;

        switch (medalCode) {
            // 基础勋章
            case "register":
                medal.medalName = "注册勋章";
                medal.medalIcon = "🎖️";
                medal.medalDesc = "欢迎加入志愿者大家庭";
                break;
            case "new_per":
                medal.medalName = "新手勋章";
                medal.medalIcon = "🌟";
                medal.medalDesc = "完成首次志愿服务";
                break;
            case "first_help":
                medal.medalName = "首次帮助";
                medal.medalIcon = "🤝";
                medal.medalDesc = "完成第一次帮助";
                break;

            // 服务时长勋章
            case "sun":
            case "service_10":
                medal.medalName = "阳光勋章";
                medal.medalIcon = "☀️";
                medal.medalDesc = "累计服务10小时";
                break;
            case "service_50":
                medal.medalName = "服务之星";
                medal.medalIcon = "✨";
                medal.medalDesc = "累计服务50小时";
                break;
            case "service_100":
                medal.medalName = "服务大师";
                medal.medalIcon = "🏅";
                medal.medalDesc = "累计服务100小时";
                break;
            case "baici":
                medal.medalName = "百次勋章";
                medal.medalIcon = "💯";
                medal.medalDesc = "累计服务100次";
                break;

            // 帮助人数勋章
            case "help_10":
                medal.medalName = "助人为乐";
                medal.medalIcon = "🤗";
                medal.medalDesc = "累计帮助10人";
                break;
            case "help_50":
                medal.medalName = "爱心使者";
                medal.medalIcon = "💝";
                medal.medalDesc = "累计帮助50人";
                break;
            case "help_100":
                medal.medalName = "爱心大使";
                medal.medalIcon = "🏆";
                medal.medalDesc = "累计帮助100人";
                break;

            // 积分勋章
            case "point":
                medal.medalName = "积分勋章";
                medal.medalIcon = "⭐";
                medal.medalDesc = "累计获得500积分";
                break;
            case "point2":
                medal.medalName = "积分达人勋章";
                medal.medalIcon = "🏅";
                medal.medalDesc = "累计获得1000积分";
                break;
            case "point_500":
                medal.medalName = "积分新秀";
                medal.medalIcon = "⭐";
                medal.medalDesc = "累计获得500积分";
                break;
            case "point_1000":
                medal.medalName = "积分达人";
                medal.medalIcon = "🏅";
                medal.medalDesc = "累计获得1000积分";
                break;
            case "point_5000":
                medal.medalName = "积分大师";
                medal.medalIcon = "👑";
                medal.medalDesc = "累计获得5000积分";
                break;

            // 经验值勋章
            case "experience_100":
                medal.medalName = "经验新秀";
                medal.medalIcon = "📚";
                medal.medalDesc = "累计获得100经验值";
                break;
            case "experience_500":
                medal.medalName = "经验达人";
                medal.medalIcon = "📖";
                medal.medalDesc = "累计获得500经验值";
                break;
            case "experience_1000":
                medal.medalName = "经验大师";
                medal.medalIcon = "🎓";
                medal.medalDesc = "累计获得1000经验值";
                break;

            // 特殊勋章
            case "jishiyu":
                medal.medalName = "及时雨勋章";
                medal.medalIcon = "🌧️";
                medal.medalDesc = "快速响应求助";
                break;
            case "night_owl":
                medal.medalName = "夜猫子勋章";
                medal.medalIcon = "🦉";
                medal.medalDesc = "夜间服务3次以上";
                break;
            case "recognition_master":
                medal.medalName = "识别达人";
                medal.medalIcon = "👁️";
                medal.medalDesc = "成功识别100个物品";
                break;
            case "team_star":
                medal.medalName = "团队之星";
                medal.medalIcon = "⭐";
                medal.medalDesc = "团队协作表现突出";
                break;
            case "service_daily":
                medal.medalName = "每日一善";
                medal.medalIcon = "🌸";
                medal.medalDesc = "连续7天参与服务";
                break;
            case "service_month":
                medal.medalName = "月度之星";
                medal.medalIcon = "🌙";
                medal.medalDesc = "当月服务时长排名前10";
                break;
            case "service_year":
                medal.medalName = "年度典范";
                medal.medalIcon = "🏆";
                medal.medalDesc = "全年服务时长排名前10";
                break;

            default:
                medal.medalName = medalCode;
                medal.medalIcon = "🏅";
                medal.medalDesc = "特殊勋章";
                break;
        }

        return medal;
    }

    /**
     * 勋章适配器
     */
    class MedalAdapter extends RecyclerView.Adapter<MedalAdapter.MedalViewHolder> {

        private List<Medal> medalList = new ArrayList<>();

        public void setMedalList(List<Medal> list) {
            this.medalList = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public MedalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_medal, parent, false);
            return new MedalViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MedalViewHolder holder, int position) {
            Medal medal = medalList.get(position);
            holder.tvMedalIcon.setText(medal.medalIcon);
            holder.tvMedalName.setText(medal.medalName);
            holder.itemView.setOnClickListener(v -> {
                Toast.makeText(getContext(), medal.medalDesc, Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() {
            return medalList.size();
        }

        class MedalViewHolder extends RecyclerView.ViewHolder {
            TextView tvMedalIcon;
            TextView tvMedalName;

            public MedalViewHolder(@NonNull View itemView) {
                super(itemView);
                tvMedalIcon = itemView.findViewById(R.id.tv_medal_icon);
                tvMedalName = itemView.findViewById(R.id.tv_medal_name);
            }
        }
    }

    /**
     * 勋章数据类
     */
    static class Medal {
        String medalCode;
        String medalName;
        String medalIcon;
        String medalDesc;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!TextUtils.isEmpty(userId)) {
            fetchGrowthData();
        }
    }
}