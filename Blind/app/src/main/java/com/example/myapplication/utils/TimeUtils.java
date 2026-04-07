package com.example.myapplication.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 时间工具类
 * 提供各种时间获取和格式化的方法
 */
public class TimeUtils {

    private TimeUtils() {
        // 工具类，防止实例化
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // 常用格式化器（线程安全）
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FULL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter CHINESE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private static final DateTimeFormatter CHINESE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分ss秒");

    /**
     * 获取当前日期时间字符串（默认格式：yyyy-MM-dd HH:mm:ss）
     */
    public static String getCurrentDateTime() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    /**
     * 获取当前日期字符串（默认格式：yyyy-MM-dd）
     */
    public static String getCurrentDate() {
        return LocalDate.now().format(DATE_FORMATTER);
    }

    /**
     * 获取当前时间字符串（默认格式：HH:mm:ss）
     */
    public static String getCurrentTime() {
        return LocalTime.now().format(TIME_FORMATTER);
    }

    /**
     * 获取当前时间戳（毫秒）
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 获取当前时间戳（秒）
     * @return
     */
    public static long getCurrentTimestampSeconds() {
        return Instant.now().getEpochSecond();
    }

    /**
     * 获取带毫秒的完整时间字符串
     */
    public static String getCurrentFullTime() {
        return LocalDateTime.now().format(FULL_FORMATTER);
    }

    /**
     * 获取中文格式日期
     */
    public static String getCurrentChineseDate() {
        return LocalDate.now().format(CHINESE_DATE_FORMATTER);
    }

    /**
     * 获取中文格式日期时间
     */
    public static String getCurrentChineseDateTime() {
        return LocalDateTime.now().format(CHINESE_DATE_TIME_FORMATTER);
    }

    /**
     * 获取指定格式的时间字符串
     * @param pattern 格式，如："yyyy/MM/dd"
     */
    public static String getCurrentTime(String pattern) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 获取当前时间的 Date 对象
     */
    public static Date getCurrentDateObject() {
        return new Date();
    }

    /**
     * 获取当前 LocalDateTime 对象
     */
    public static LocalDateTime getCurrentLocalDateTime() {
        return LocalDateTime.now();
    }

    /**
     * 获取当前 LocalDate 对象
     */
    public static LocalDate getCurrentLocalDate() {
        return LocalDate.now();
    }

    /**
     * 获取当前 LocalTime 对象
     */
    public static LocalTime getCurrentLocalTime() {
        return LocalTime.now();
    }

    /**
     * 获取当前时间的 Instant 对象
     */
    public static Instant getCurrentInstant() {
        return Instant.now();
    }

    /**
     * 获取当前时间在一天中的秒数
     */
    public static int getSecondOfDay() {
        return LocalTime.now().toSecondOfDay();
    }

    /**
     * 获取今天是星期几（1-7，1=星期一，7=星期日）
     */
    public static int getDayOfWeek() {
        return LocalDate.now().getDayOfWeek().getValue();
    }

    /**
     * 获取今天是星期几（中文）
     */
    public static String getChineseDayOfWeek() {
        String[] weekDays = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        return weekDays[getDayOfWeek() - 1];
    }

    /**
     * 获取当前月份
     */
    public static int getCurrentMonth() {
        return LocalDate.now().getMonthValue();
    }

    /**
     * 获取当前年份
     */
    public static int getCurrentYear() {
        return LocalDate.now().getYear();
    }
}