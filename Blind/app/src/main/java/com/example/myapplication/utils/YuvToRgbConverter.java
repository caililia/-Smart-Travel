package com.example.myapplication.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;

import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;

public class YuvToRgbConverter {
    private final RenderScript rs;
    private final ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;

    public YuvToRgbConverter(Context context) {
        rs = RenderScript.create(context);
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
    }

    public void yuvToRgb(ImageProxy imageProxy, Bitmap outputBitmap) {
        @SuppressLint("UnsafeOptInUsageError") Image image = imageProxy.getImage();
        if (image == null) return;

        byte[] yuvBytes = yuv420ToNv21(image);
        Allocation yuvAllocation = Allocation.createSized(rs, Element.U8(rs), yuvBytes.length);
        yuvAllocation.copyFrom(yuvBytes);

        Allocation rgbAllocation = Allocation.createFromBitmap(rs, outputBitmap);

        yuvToRgbIntrinsic.setInput(yuvAllocation);
        yuvToRgbIntrinsic.forEach(rgbAllocation);

        rgbAllocation.copyTo(outputBitmap);
    }

    private byte[] yuv420ToNv21(Image image) {
        Image.Plane[] planes = image.getPlanes();
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = planes[0].getBuffer().remaining();
        int uSize = planes[1].getBuffer().remaining();
        int vSize = planes[2].getBuffer().remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        planes[0].getBuffer().get(nv21, 0, ySize);
        planes[2].getBuffer().get(nv21, ySize, vSize); // V
        planes[1].getBuffer().get(nv21, ySize + vSize, uSize); // U

        return nv21;
    }
}
