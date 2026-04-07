package com.example.volunteer.activity.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteer.R;
import com.example.volunteer.adapter.PointProductAdapter;
import com.example.volunteer.data.PointProduct;
import com.example.volunteer.data.UserPoints;
import com.example.volunteer.utils.OkhttpUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class PointActivity extends AppCompatActivity {

    private static final String TAG = "PointActivity";

    private Toolbar toolbar;
    private TextView tvPoints;
    private RecyclerView rvProducts;
    private ProgressBar progressBar;
    private LinearLayout categoryContainer;
    //private Button btnExchangeHistory;

    private PointProductAdapter adapter;
    private List<PointProduct> products = new ArrayList<>();
    private List<PointProduct> allProducts = new ArrayList<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private String currentCategory = "all";
    private int points;
    private String userId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_point);
        Intent intent = getIntent();
        points = intent.getIntExtra("points", 0);
        Log.d(TAG, "onCreate: " + points);
        initViews();
        setupToolbar();
        setupCategories();
        loadUserPoints();
        loadProducts();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvPoints = findViewById(R.id.tv_points);
        rvProducts = findViewById(R.id.rv_products);
        progressBar = findViewById(R.id.progressBar);
        categoryContainer = findViewById(R.id.category_container);
        //btnExchangeHistory = findViewById(R.id.btn_exchange_history);

        rvProducts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PointProductAdapter(this, products, product -> {
            showExchangeConfirmDialog(product);
        });
        rvProducts.setAdapter(adapter);

        /*btnExchangeHistory.setOnClickListener(v -> {
            Toast.makeText(this, "兑换记录功能开发中", Toast.LENGTH_SHORT).show();
        });*/
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("积分商城");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupCategories() {
        String[] categories = {"全部", "学习用品", "生活用品", "电子产品", "公益礼品"};
        String[] categoryKeys = {"all", "study", "life", "electronic", "gift"};

        for (int i = 0; i < categories.length; i++) {
            TextView categoryView = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.item_category_text, categoryContainer, false);
            categoryView.setText(categories[i]);
            final String key = categoryKeys[i];

            if (i == 0) {
                categoryView.setSelected(true);
            }

            categoryView.setOnClickListener(v -> {
                resetCategorySelection();
                categoryView.setSelected(true);
                currentCategory = key;
                filterProductsByCategory();
            });

            categoryContainer.addView(categoryView);
        }
    }

    private void resetCategorySelection() {
        for (int i = 0; i < categoryContainer.getChildCount(); i++) {
            View child = categoryContainer.getChildAt(i);
            child.setSelected(false);
        }
    }

    private void filterProductsByCategory() {
        products.clear();
        if ("all".equals(currentCategory)) {
            products.addAll(allProducts);
        } else {
            for (PointProduct product : allProducts) {
                if (currentCategory.equals(product.getCategory())) {
                    products.add(product);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void loadUserPoints() {
        updateUserPointsUI();
    }

    private void updateUserPointsUI() {
        tvPoints.setText(String.valueOf(points));
    }

    private void loadProducts() {
        showLoading(true);

        String url = OkhttpUtils.URL + "/pointProduct/manage/list";
        Log.d(TAG, "请求URL: " + url);

        OkhttpUtils.request("GET", url, null, "", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取商品列表失败: " + e.getMessage());
                mainHandler.post(() -> {
                    showLoading(false);
                    Toast.makeText(PointActivity.this, "网络请求失败", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "商品列表响应: " + json);

                    try {
                        JSONObject jsonObject = new JSONObject(json);
                        int code = jsonObject.getInt("code");

                        if (code == 200) {
                            // 解析商品数据
                            PointProduct responseData = OkhttpUtils.toData(json, PointProduct.class);

                            if (responseData != null && responseData.getData() != null) {
                                List<PointProduct.DataBean> dataList = responseData.getData();

                                mainHandler.post(() -> {
                                    allProducts.clear();

                                    for (PointProduct.DataBean item : dataList) {
                                        // 为每个商品创建新的 PointProduct 对象
                                        PointProduct product = new PointProduct();
                                        product.setId(item.getId());
                                        product.setName(item.getName());
                                        product.setDescription(item.getDiscription());
                                        product.setPoints(Integer.parseInt(item.getPoints()));
                                        product.setStock(Integer.parseInt(item.getStock()));
                                        product.setCategory(item.getCategory());
                                        product.setPicture(item.getPicture());
                                        product.setPictureUrl(item.getPictureUrl());

                                        // 设置热门标记（积分大于200的商品标记为热门）
                                        if (product.getPoints() > 200) {
                                            product.setHot(true);
                                        }

                                        allProducts.add(product);
                                    }

                                    // 更新显示
                                    products.clear();
                                    products.addAll(allProducts);
                                    adapter.notifyDataSetChanged();

                                    showLoading(false);
                                    Log.d(TAG, "加载商品成功，共" + allProducts.size() + "件商品");
                                });
                            } else {
                                mainHandler.post(() -> {
                                    showLoading(false);
                                    Toast.makeText(PointActivity.this, "商品数据为空", Toast.LENGTH_SHORT).show();
                                });
                            }
                        } else {
                            String msg = jsonObject.optString("msg", "获取失败");
                            mainHandler.post(() -> {
                                showLoading(false);
                                Toast.makeText(PointActivity.this, msg, Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析商品数据失败", e);
                        mainHandler.post(() -> {
                            showLoading(false);
                            Toast.makeText(PointActivity.this, "数据解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    mainHandler.post(() -> {
                        showLoading(false);
                        Toast.makeText(PointActivity.this, "请求失败: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void showExchangeConfirmDialog(PointProduct product) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_exchange_confirm, null);

        TextView tvProductName = dialogView.findViewById(R.id.tv_product_name);
        TextView tvPoints = dialogView.findViewById(R.id.tv_points);
        TextView tvCurrentPoints = dialogView.findViewById(R.id.tv_current_points);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);

        tvProductName.setText(product.getName());
        tvPoints.setText("需要" + product.getPoints() + "积分");
        tvCurrentPoints.setText("当前积分: " + points);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            performExchange(product);
        });

        dialog.show();
    }

    private void performExchange(PointProduct product) {
        showLoading(true);

        // 模拟网络请求延迟
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                // 1. 检查积分是否足够（再次确认）
                if (String.valueOf(points) == null) {
                    showToast("用户信息异常");
                    showLoading(false);
                    return;
                }

                int productPoints = product.getPoints();
                int currentPoints = points;

                if (currentPoints < productPoints) {
                    showToast("积分不足，无法兑换");
                    showLoading(false);
                    return;
                }

                // 2. 检查库存是否足够
                if (product.getStock() <= 0) {
                    showToast("商品库存不足");
                    showLoading(false);
                    return;
                }

                // 3. 执行兑换操作（假的）
                // 更新用户积分
                points = points - productPoints;

                // 更新商品库存
                product.setStock(product.getStock() - 1);

                // 4. 刷新页面UI
                updateUserPointsUI();  // 刷新积分显示
                adapter.notifyDataSetChanged();  // 刷新商品列表

                // 5. 显示成功提示
                showToast("兑换成功！\n已扣除" + productPoints + "积分");

                // 6. 可选：播放成功动画或震动
                 Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                 if (vibrator != null) {
                     vibrator.vibrate(100);
                 }

                Log.d(TAG, "兑换成功 - 商品: " + product.getName() +
                        ", 扣除积分: " + productPoints +
                        ", 剩余库存: " + product.getStock());

            } catch (Exception e) {
                Log.e(TAG, "兑换失败: " + e.getMessage());
                showToast("兑换失败: " + e.getMessage());
            } finally {
                showLoading(false);
            }
        }, 1000); // 模拟500ms的网络延迟
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(PointActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private void showLoading(boolean show) {
        runOnUiThread(() -> {
            if (progressBar != null && rvProducts != null) {
                if (show) {
                    progressBar.setVisibility(View.VISIBLE);
                    rvProducts.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.GONE);
                    rvProducts.setVisibility(View.VISIBLE);
                }
            }
        });
    }
}