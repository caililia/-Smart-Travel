package com.example.myapplication.utils;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;

import java.nio.ByteBuffer;

public class ImageUtils {

    public static Bitmap yuvToBitmap(Image image) {
        if (image == null || image.getFormat() != ImageFormat.YUV_420_888) {
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // 获取 YUV 平面数据
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        // NV21 格式（Android 原生支持的 YUV 格式）
        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);

        // NV21 = Y + VU
        byte[] uBytes = new byte[uSize];
        byte[] vBytes = new byte[vSize];
        uBuffer.get(uBytes);
        vBuffer.get(vBytes);

        // 注意 VU 顺序
        for (int i = 0; i < vSize; i++) {
            nv21[ySize + (i * 2)] = vBytes[i];
            nv21[ySize + (i * 2) + 1] = uBytes[i];
        }

        // 转为 Bitmap
        android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                nv21, ImageFormat.NV21, width, height, null);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 90, out);
        byte[] jpegBytes = out.toByteArray();
        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }
}
