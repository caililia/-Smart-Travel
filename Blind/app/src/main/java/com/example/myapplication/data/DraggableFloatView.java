package com.example.myapplication.data;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;

import androidx.appcompat.widget.AppCompatImageView;

import com.example.myapplication.R;

public class DraggableFloatView extends AppCompatImageView implements View.OnTouchListener {

    private float dX, dY;
    private float downX, downY;
    private boolean isDrag = false;
    private int screenWidth, screenHeight;

    public DraggableFloatView(Context context) {
        super(context);
        init();
    }

    public DraggableFloatView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DraggableFloatView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 设置默认图片（最好找一个麦克风或者机器人的圆形图标）
        this.setImageResource(R.mipmap.ic_launcher_round); // 暂时用默认图标，你可以换成好看的icon
        this.setOnTouchListener(this);
        this.setScaleType(ScaleType.CENTER_CROP);

        // 获取屏幕尺寸
        screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDrag = false;
                downX = event.getRawX();
                downY = event.getRawY();
                dX = view.getX() - event.getRawX();
                dY = view.getY() - event.getRawY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getRawX() - downX) > 10 || Math.abs(event.getRawY() - downY) > 10) {
                    isDrag = true; // 移动距离超过10px算作拖拽
                }
                // 更新位置
                view.animate()
                        .x(event.getRawX() + dX)
                        .y(event.getRawY() + dY)
                        .setDuration(0)
                        .start();
                break;

            case MotionEvent.ACTION_UP:
                if (!isDrag) {
                    performClick(); // 如果不是拖拽，则触发点击事件
                } else {
                    // 拖拽结束，可以在这里做吸边效果（可选）
                    adsorbEdge(view);
                }
                break;
        }
        return true; // 消费事件
    }

    // 吸边效果：松手后自动贴到屏幕左侧或右侧
    private void adsorbEdge(View view) {
        float x = view.getX();
        float targetX = (x + view.getWidth() / 2) > screenWidth / 2 ? screenWidth - view.getWidth() : 0;
        view.animate().setDuration(300).x(targetX).start();
    }

    // ==================== 动画效果 ====================

    // 开始“呼吸”动画（表示正在听）
    public void startListeningAnim() {
        ScaleAnimation anim = new ScaleAnimation(1.0f, 1.2f, 1.0f, 1.2f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(500);
        anim.setRepeatCount(Animation.INFINITE);
        anim.setRepeatMode(Animation.REVERSE);
        this.startAnimation(anim);
        this.setColorFilter(null); // 清除颜色滤镜
    }

    // 停止动画（待机状态）
    public void stopAnim() {
        this.clearAnimation();
        this.setColorFilter(null);
    }

    // 思考状态（变色）
    public void showThinking() {
        this.clearAnimation();
        // 变成灰色或者其他颜色表示思考
        this.setColorFilter(0x88000000);
    }
}