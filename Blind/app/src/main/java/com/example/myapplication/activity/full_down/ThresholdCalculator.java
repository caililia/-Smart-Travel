package com.example.myapplication.activity.full_down;

/**
 * 动态阈值计算器
 * 根据用户身高体重个性化调整检测阈值
 */
public class ThresholdCalculator {

    /**
     * 计算个性化撞击阈值
     * @param height 身高(cm)
     * @param weight 体重(kg)
     * @return 撞击阈值 (m/s²)
     */
    public static float calculateImpactThreshold(float height, float weight) {
        float heightInM = height / 100f;
        float bmi = weight / (heightInM * heightInM);

        // 基础阈值
        float baseThreshold = 15.0f;

        // BMI系数
        float bmiFactor;
        if (bmi < 18.5) {
            // 偏瘦：阈值降低
            bmiFactor = 0.9f;
        } else if (bmi < 24) {
            // 正常
            bmiFactor = 1.0f;
        } else if (bmi < 28) {
            // 超重
            bmiFactor = 1.1f;
        } else {
            // 肥胖
            bmiFactor = 1.2f;
        }

        return baseThreshold * bmiFactor;
    }

    /**
     * 计算个性化角度阈值
     * @param height 身高(cm)
     * @return 角度阈值 (度)
     */
    public static float calculateAngleThreshold(float height) {
        float baseAngle = 45.0f;

        // 身高越高，角度阈值越小（更容易触发）
        if (height > 175) {
            return baseAngle * 0.9f;
        } else if (height < 160) {
            return baseAngle * 1.1f;
        } else {
            return baseAngle;
        }
    }

    /**
     * 计算自由落体阈值
     * @return 自由落体阈值 (m/s²)
     */
    public static float calculateFreeFallThreshold() {
        return 7.5f; // 固定值，小于0.75g
    }

    /**
     * 计算静止检测阈值
     * @return 静止阈值 (m/s²)
     */
    public static float calculateStaticThreshold() {
        return 11.0f; // 接近1g的阈值
    }

    /**
     * 根据年龄调整阈值
     * @param age 年龄
     * @param baseThreshold 基础阈值
     * @return 调整后的阈值
     */
    public static float adjustByAge(int age, float baseThreshold) {
        if (age > 70) {
            // 老年人：阈值降低（更容易触发）
            return baseThreshold * 0.8f;
        } else if (age > 60) {
            return baseThreshold * 0.9f;
        } else if (age < 18) {
            // 青少年：阈值提高
            return baseThreshold * 1.1f;
        }
        return baseThreshold;
    }
}