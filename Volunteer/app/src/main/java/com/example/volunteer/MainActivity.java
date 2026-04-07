package com.example.volunteer;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.volunteer.activity.fragment.CommunityFragment;
import com.example.volunteer.activity.fragment.GrowthFragment;
import com.example.volunteer.activity.fragment.HomeFragment;
import com.example.volunteer.activity.fragment.PersonalFragment;
import com.example.volunteer.activity.fragment.TrainingFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private FrameLayout fragmentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        fragmentContainer = findViewById(R.id.fragment_container);

        // 默认显示“功能”页面
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .commit();

        // 底部导航选中事件
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            switch (item.getItemId()) {
                case R.id.menu_function:
                    selectedFragment = new HomeFragment();
                    break;
                case R.id.menu_community:
                    selectedFragment = new CommunityFragment();
                    break;

                case R.id.menu_growth:
                    selectedFragment = new GrowthFragment();
                    break;

                case R.id.menu_training:
                    selectedFragment = new TrainingFragment();
                    break;

                case R.id.menu_personal:
                    selectedFragment = new PersonalFragment();
                    break;



            }
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
        });
    }

    public void switchToTab(int menuId) {
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(menuId);
        }
    }
}