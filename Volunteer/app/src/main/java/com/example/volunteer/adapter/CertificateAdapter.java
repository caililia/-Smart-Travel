package com.example.volunteer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.volunteer.R;
import com.example.volunteer.data.train.Certificate;

import java.util.List;

public class CertificateAdapter extends RecyclerView.Adapter<CertificateAdapter.ViewHolder> {

    private List<Certificate> certificates;
    private Context context;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Certificate certificate);
    }

    public CertificateAdapter(Context context, List<Certificate> certificates, OnItemClickListener listener) {
        this.context = context;
        this.certificates = certificates;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_certificate, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Certificate certificate = certificates.get(position);
        holder.bind(certificate);
    }

    @Override
    public int getItemCount() {
        return certificates.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivCertImage;
        private TextView tvCertName;
        private TextView tvIssueDate;
        private TextView tvValidStatus;
        private CardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCertImage = itemView.findViewById(R.id.iv_cert_image);
            tvCertName = itemView.findViewById(R.id.tv_cert_name);
            tvIssueDate = itemView.findViewById(R.id.tv_issue_date);
            tvValidStatus = itemView.findViewById(R.id.tv_valid_status);
            cardView = (CardView) itemView;
        }

        public void bind(Certificate certificate) {
            tvCertName.setText(certificate.getName());
            tvIssueDate.setText("颁发日期：" + certificate.getIssueDate());
            tvValidStatus.setText(certificate.isValid() ? "有效" : "已过期");

            if (certificate.isValid()) {
                tvValidStatus.setBackgroundResource(R.drawable.bg_status_completed_small);
                tvValidStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
            } else {
                tvValidStatus.setBackgroundResource(R.drawable.bg_status_expired_small);
                tvValidStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"));
            }

            Glide.with(context)
                    .load(certificate.getImageRes())
                    .placeholder(R.drawable.ic_certificate)
                    .into(ivCertImage);

            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(certificate);
                }
            });
        }
    }
}