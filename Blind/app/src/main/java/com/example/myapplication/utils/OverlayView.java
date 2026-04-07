package com.example.myapplication.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {
    private static final String TAG = "OverlayView";

    private List<DetectionResult> results = new ArrayList<>();
    private Paint boxPaint, textPaint, backgroundPaint;

    // 颜色数组
    private final int[] colors = {
            Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.CYAN,
            Color.MAGENTA, Color.rgb(255, 165, 0), Color.rgb(128, 0, 128)
    };

    public OverlayView(Context context) {
        super(context);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 边界框画笔
        boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);
        boxPaint.setAntiAlias(true);

        // 文本背景画笔
        backgroundPaint = new Paint();
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setAntiAlias(true);

        // 文本画笔
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setAntiAlias(true);
    }

    public void setResults(List<DetectionResult> results) {
        this.results = results != null ? new ArrayList<>(results) : new ArrayList<>();
        Log.d(TAG, "设置检测结果: " + this.results.size() + " 个对象");

        // 打印调试信息
        for (int i = 0; i < this.results.size(); i++) {
            DetectionResult result = this.results.get(i);
            Log.d(TAG, String.format("结果 %d: %s (%.2f) [x=%.1f, y=%.1f, w=%.1f, h=%.1f]",
                    i, result.className, result.confidence,
                    result.x, result.y, result.width, result.height));
        }

        // 请求重绘
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (results == null || results.isEmpty()) {
            Log.d(TAG, "没有检测结果可绘制");
            return;
        }

        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();

        Log.d(TAG, "画布尺寸: " + canvasWidth + "x" + canvasHeight);

        // 绘制所有检测结果
        for (int i = 0; i < results.size(); i++) {
            drawDetectionResult(canvas, results.get(i), i, canvasWidth, canvasHeight);
        }
    }

    private void drawDetectionResult(Canvas canvas, DetectionResult result, int index,
                                     int canvasWidth, int canvasHeight) {
        // 设置颜色
        int color = colors[index % colors.length];
        boxPaint.setColor(color);
        backgroundPaint.setColor(color);

        // 计算边界框坐标 - 尝试不同的转换方式
        float left, top, right, bottom;

        // 调试：打印原始坐标
        Log.d(TAG, String.format("原始坐标: [x=%.1f, y=%.1f, w=%.1f, h=%.1f]",
                result.x, result.y, result.width, result.height));

        // 方式1：直接使用绝对坐标（如果坐标已经是像素值）
        left = result.x;
        top = result.y;
        right = result.x + result.width;
        bottom = result.y + result.height;
        // 确保坐标在画布范围内
        left = Math.max(0, Math.min(left, canvasWidth));
        top = Math.max(0, Math.min(top, canvasHeight));
        right = Math.max(0, Math.min(right, canvasWidth));
        bottom = Math.max(0, Math.min(bottom, canvasHeight));

        // 检查边界框是否有效
        if (right <= left || bottom <= top) {
            Log.w(TAG, "无效的边界框: [" + left + ", " + top + ", " + right + ", " + bottom + "]");
            return;
        }

        Log.d(TAG, String.format("绘制边界框: [%.1f, %.1f, %.1f, %.1f]", left, top, right, bottom));

        // 创建边界框矩形
        RectF rect = new RectF(left, top, right, bottom);

        // 绘制边界框
        canvas.drawRect(rect, boxPaint);

        // 准备标签文本
        String label = String.format("%s %.1f%%",
                result.className, result.confidence * 100);

        // 计算文本尺寸
        float textWidth = textPaint.measureText(label);
        float textHeight = textPaint.getTextSize();

        // 计算文本背景位置
        float bgLeft = left;
        float bgTop = top - textHeight - 8;
        float bgRight = left + textWidth + 16;
        float bgBottom = top;

        // 如果文本背景超出顶部，放在边界框下方
        if (bgTop < 0) {
            bgTop = bottom;
            bgBottom = bottom + textHeight + 8;
        }

        // 如果文本背景超出右侧，向左调整
        if (bgRight > canvasWidth) {
            bgRight = canvasWidth;
            bgLeft = canvasWidth - textWidth - 16;
        }

        // 绘制文本背景
        RectF bgRect = new RectF(bgLeft, bgTop, bgRight, bgBottom);
        canvas.drawRect(bgRect, backgroundPaint);

        // 绘制文本
        float textX = bgLeft + 8;
        float textY = bgTop + textHeight - 4;
        canvas.drawText(label, textX, textY, textPaint);

        Log.d(TAG, "成功绘制: " + label);
    }

    /**
     * 清空检测结果
     */
    public void clearResults() {
        this.results.clear();
        postInvalidate();
    }
}