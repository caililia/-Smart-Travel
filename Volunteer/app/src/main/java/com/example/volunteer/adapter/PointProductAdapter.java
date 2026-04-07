package com.example.volunteer.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.volunteer.R;
import com.example.volunteer.data.PointProduct;

import java.util.List;

public class PointProductAdapter extends RecyclerView.Adapter<PointProductAdapter.ViewHolder> {

    private Context context;
    private List<PointProduct> products;
    private OnExchangeClickListener listener;

    public interface OnExchangeClickListener {
        void onExchangeClick(PointProduct product);
    }

    public PointProductAdapter(Context context, List<PointProduct> products, OnExchangeClickListener listener) {
        this.context = context;
        this.products = products;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_point_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PointProduct product = products.get(position);
        holder.bind(product);
    }

    @Override
    public int getItemCount() {
        return products == null ? 0 : products.size();
    }

    public void updateData(List<PointProduct> newProducts) {
        this.products = newProducts;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivProductImage;
        private TextView tvProductName;
        private TextView tvProductDesc;
        private TextView tvPoints;
        private TextView tvStock;
        private TextView tvHotTag;
        private Button btnExchange;
        private CardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProductImage = itemView.findViewById(R.id.iv_product_image);
            tvProductName = itemView.findViewById(R.id.tv_product_name);
            tvProductDesc = itemView.findViewById(R.id.tv_product_desc);
            tvPoints = itemView.findViewById(R.id.tv_points);
            tvStock = itemView.findViewById(R.id.tv_stock);
            //tvHotTag = itemView.findViewById(R.id.tv_hot_tag);
            btnExchange = itemView.findViewById(R.id.btn_exchange);
            cardView = (CardView) itemView;
        }

        public void bind(PointProduct product) {
            // 设置商品名称
            if (product.getName() != null) {
                tvProductName.setText(product.getName());
            } else if (product.getData() != null && !product.getData().isEmpty()) {
                // 兼容旧数据结构
                tvProductName.setText(product.getData().get(0).getName());
            } else {
                tvProductName.setText("商品名称");
            }

            // 设置商品描述
            if (product.getDescription() != null) {
                tvProductDesc.setText(product.getDescription());
            } else if (product.getData() != null && !product.getData().isEmpty()) {
                tvProductDesc.setText(product.getData().get(0).getDiscription());
            } else {
                tvProductDesc.setText("商品描述");
            }

            // 设置积分
            int points = product.getPoints();
            if (points > 0) {
                tvPoints.setText(points + "积分");
            } else if (product.getData() != null && !product.getData().isEmpty()) {
                tvPoints.setText(product.getData().get(0).getPoints() + "积分");
            } else {
                tvPoints.setText("0积分");
            }

            // 设置库存
            int stock = product.getStock();
            if (stock >= 0) {
                tvStock.setText("库存: " + stock);
            } else if (product.getData() != null && !product.getData().isEmpty()) {
                tvStock.setText("库存: " + product.getData().get(0).getStock());
            } else {
                tvStock.setText("库存: --");
            }

            /*// 显示热门标签
            if (product.isHot()) {
                tvHotTag.setVisibility(View.VISIBLE);
            } else {
                tvHotTag.setVisibility(View.GONE);
            }
*/
            // 加载商品图片
            if (!TextUtils.isEmpty(product.getPictureUrl())) {
                Glide.with(context)
                        .load(product.getPictureUrl())
                        .into(ivProductImage);
            }

            // 根据库存状态设置按钮
            int stockCount = product.getStock();
            if (stockCount <= 0) {
                btnExchange.setEnabled(false);
                btnExchange.setText("已兑完");
                btnExchange.setBackgroundResource(R.drawable.button_disabled);
            } else {
                btnExchange.setEnabled(true);
                btnExchange.setText("兑换");
                btnExchange.setBackgroundResource(R.drawable.button_primary_small);
            }

            // 设置兑换按钮点击事件
            btnExchange.setOnClickListener(v -> {
                if (listener != null && stockCount > 0) {
                    listener.onExchangeClick(product);
                }
            });

            // 设置卡片点击事件（可选）
            cardView.setOnClickListener(v -> {
                // 可以跳转到商品详情页
                if (listener != null) {
                    // 或者使用不同的回调
                }
            });
        }
    }
}