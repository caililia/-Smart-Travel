package com.example.volunteer.activity.login;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.volunteer.R;


public class UserInfoActivity extends AppCompatActivity implements View.OnClickListener {
    private ImageView ivBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_info);
        initView();
    }

    private void initView() {
        ivBack = (ImageView) findViewById(R.id.ivBack);

        ivBack.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.ivBack){
            this.finish();
        }
    }
}