package com.example.volunteer.activity.activity;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.media.MediaPlayer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;


import com.example.volunteer.R;
import com.example.volunteer.utils.OkhttpUtils;

import java.io.IOException;

public class MediaVideoActivity extends AppCompatActivity implements View.OnClickListener, SurfaceHolder.Callback {

    private SurfaceView svVideo;   // SurfaceView 用于显示视频
    private MediaPlayer mediaPlayer = new MediaPlayer();   // MediaPlayer 播放视频
    private SurfaceHolder surfaceHolder;
    private String videoUrl = new String();  // 替换为实际的视频 URL
    private ImageView iv_back, iv_stop, ivLeft, ivRight;
    private LinearLayout ll_set;
    private boolean isPlay = false;
    private SeekBar seekBar;
    private TextView tvCurrentTime, tvTotalTime;
    private RelativeLayout rlTime;

    private String filename = new String();
    private Handler handler = new Handler();
    private Handler handler1 = new Handler();
    private Runnable hide = new Runnable() {
        @Override
        public void run() {
            iv_back.setVisibility(View.INVISIBLE);
            iv_stop.setVisibility(View.INVISIBLE);
            ll_set.setVisibility(View.INVISIBLE);
            seekBar.setVisibility(View.INVISIBLE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_video);

        Intent intent = getIntent();
        filename = intent.getStringExtra("filename");
        Log.d("onCreate", "onCreate: " + filename);
        videoUrl = OkhttpUtils.URL + OkhttpUtils.VideoFile + "/video/"+ filename + ".mp4";

        playVideo();
        initView();
    }

    private void initView() {
        svVideo = (SurfaceView) findViewById(R.id.sv_video);
        iv_back = (ImageView) findViewById(R.id.iv_back);
        iv_stop = (ImageView) findViewById(R.id.iv_stop);
        ll_set = (LinearLayout) findViewById(R.id.ll_set);
        ivLeft = (ImageView) findViewById(R.id.ivLeft);
        ivRight = (ImageView) findViewById(R.id.ivRight);

        seekBar = (SeekBar) findViewById(R.id.seekBar);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);

        surfaceHolder = svVideo.getHolder();
        surfaceHolder.addCallback(this);

        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
        iv_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPlay){
                    mediaPlayer.start();
                    isPlay =true;
                    iv_stop.setBackgroundResource(R.drawable.video_item_play2);
                }else {
                    mediaPlayer.pause();
                    isPlay = false;
                    iv_stop.setBackgroundResource(R.drawable.video_item_play);
                }
            }
        });
        svVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iv_back.setVisibility(View.VISIBLE);
                iv_stop.setVisibility(View.VISIBLE);
                ll_set.setVisibility(View.VISIBLE);
                seekBar.setVisibility(View.VISIBLE);

                handler.removeCallbacks(hide);
                handler.postDelayed(hide,5000);
                iv_back.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mediaPlayer.pause();
                        finish();
                    }
                });
            }
        });

        // 监听进度条变化，拖动时调整视频位置
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 暂停视频播放，用户拖动时不播放
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    isPlay = false;
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 拖动结束后恢复播放
                if (!isPlay) {
                    isPlay = true;
                    mediaPlayer.start();
                }
            }
        });
    }

    private void playVideo() {
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(videoUrl);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    // 视频准备好后，设置SeekBar最大值和开始播放
                    seekBar.setMax(mediaPlayer.getDuration());
                    tvTotalTime.setText(formatTime(mediaPlayer.getDuration()));
                    tvCurrentTime.setText(formatTime(0));
                    mediaPlayer.start(); // 开始播放
                    mediaPlayer.pause();
                    handler1.postDelayed(updateSeekBarRunnable, 1000); // 启动更新进度条的定时任务
                }
            });

            mediaPlayer.prepareAsync(); // 异步准备视频，避免阻塞UI线程
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Runnable updateSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            // 始终获取当前播放位置
            int currentPosition = mediaPlayer.getCurrentPosition();
            seekBar.setProgress(currentPosition);
            tvCurrentTime.setText(formatTime(currentPosition));

            // 根据播放状态决定是否继续循环
            if (isPlay) {
                // 播放中，1秒后继续更新
                handler1.postDelayed(this, 1000);
            } else {
                // 暂停状态，也继续更新（但频率可以低一些，用于显示seekTo后的位置）
                handler1.postDelayed(this, 500);
            }
        }
    };

    // 格式化时间，转换为 mm:ss 格式
    private String formatTime(int timeInMillis) {
        int seconds = timeInMillis / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.ivLeft){
            // 获取当前播放进度
            int currentPosition = mediaPlayer.getCurrentPosition();
            // 快退 10 秒（10000 毫秒）
            int newPosition = currentPosition - 10000;
            // 确保新位置不小于 0
            if (newPosition < 0) newPosition = 0;
            // 跳转到新位置
            mediaPlayer.seekTo(newPosition);
        }
        else if (v.getId() == R.id.ivRight){
            // 获取当前播放进度
            int currentPosition = mediaPlayer.getCurrentPosition();
            // 快进 10 秒（10000 毫秒）
            int newPosition = currentPosition + 10000;
            // 确保新位置不超过视频时长
            if (newPosition > mediaPlayer.getDuration()) newPosition = mediaPlayer.getDuration();
            // 跳转到新位置
            mediaPlayer.seekTo(newPosition);
        }
    }


    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setDisplay(surfaceHolder);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        // 停止所有定时任务
        if (handler != null) {
            handler.removeCallbacks(hide);
        }
        if (handler1 != null) {
            handler1.removeCallbacks(updateSeekBarRunnable);
        }

        // 释放 MediaPlayer
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }

        super.onBackPressed();
    }
}
