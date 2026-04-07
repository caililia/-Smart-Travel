package com.example.myapplication.activity.full_down;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单的机器学习分类器
 * 基于统计特征判断是否为跌倒
 */
public class FallClassifier {

    // 特征向量
    public static class FeatureVector {
        public float mean;          // 均值
        public float variance;       // 方差
        public float peak;           // 峰值
        public float min;             // 最小值
        public float range;           // 范围
        public float zeroCrossRate;   // 过零率
        public float energy;          // 能量
    }

    /**
     * 提取特征
     * @param data 加速度数据窗口
     * @return 特征向量
     */
    public static FeatureVector extractFeatures(float[] data) {
        FeatureVector features = new FeatureVector();

        // 计算均值
        float sum = 0;
        for (float v : data) {
            sum += v;
        }
        features.mean = sum / data.length;

        // 计算方差
        float varianceSum = 0;
        for (float v : data) {
            varianceSum += (v - features.mean) * (v - features.mean);
        }
        features.variance = varianceSum / data.length;

        // 计算峰值
        float max = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;
        for (float v : data) {
            if (v > max) max = v;
            if (v < min) min = v;
        }
        features.peak = max;
        features.min = min;
        features.range = max - min;

        // 计算过零率（相对于均值）
        int zeroCross = 0;
        for (int i = 1; i < data.length; i++) {
            if ((data[i-1] - features.mean) * (data[i] - features.mean) < 0) {
                zeroCross++;
            }
        }
        features.zeroCrossRate = (float) zeroCross / data.length;

        // 计算能量
        float energy = 0;
        for (float v : data) {
            energy += v * v;
        }
        features.energy = energy / data.length;

        return features;
    }

    /**
     * 判断是否为跌倒
     * @param features 特征向量
     * @return 是否为跌倒
     */
    public static boolean isFall(FeatureVector features) {
        // 基于规则的分类
        // 跌倒的特征组合：
        // 1. 有较大的峰值 (>15)
        // 2. 方差较大 (>5)
        // 3. 均值适中 (5-12)
        // 4. 范围较大 (>10)

        return features.peak > 15.0f
                && features.variance > 5.0f
                && features.mean > 5.0f
                && features.mean < 12.0f
                && features.range > 10.0f
                && features.zeroCrossRate < 0.3f; // 跌倒波形较平滑
    }

    /**
     * 计算跌倒概率
     * @param features 特征向量
     * @return 概率 (0-1)
     */
    public static float calculateProbability(FeatureVector features) {
        float probability = 0;

        // 峰值贡献
        if (features.peak > 15) {
            probability += 0.3f;
        } else if (features.peak > 12) {
            probability += 0.2f;
        }

        // 方差贡献
        if (features.variance > 10) {
            probability += 0.3f;
        } else if (features.variance > 5) {
            probability += 0.2f;
        }

        // 均值贡献
        if (features.mean > 7 && features.mean < 10) {
            probability += 0.2f;
        }

        // 范围贡献
        if (features.range > 15) {
            probability += 0.2f;
        } else if (features.range > 10) {
            probability += 0.1f;
        }

        return Math.min(probability, 1.0f);
    }

    /**
     * 使用滑动窗口检测
     * @param dataStream 数据流
     * @param windowSize 窗口大小
     * @return 检测结果列表
     */
    public static List<Boolean> detectWithWindow(float[] dataStream, int windowSize) {
        List<Boolean> results = new ArrayList<>();

        for (int i = 0; i <= dataStream.length - windowSize; i++) {
            float[] window = new float[windowSize];
            System.arraycopy(dataStream, i, window, 0, windowSize);

            FeatureVector features = extractFeatures(window);
            results.add(isFall(features));
        }

        return results;
    }
}