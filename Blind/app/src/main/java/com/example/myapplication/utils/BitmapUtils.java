package com.example.myapplication.utils;

import android.graphics.Bitmap;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

public class BitmapUtils {

    /**
     * 将Bitmap转换为Base64字符串
     * @param bitmap 图片
     * @param quality 压缩质量 (0-100)
     * @return Base64字符串
     */
    public static String bitmapToBase64(Bitmap bitmap, int quality) {
        if (bitmap == null) return "";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] bytes = baos.toByteArray();

        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}