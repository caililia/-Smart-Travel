package com.example.myapplication.utils;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.InputStream;

public class NcnnYolo {
    private static final String TAG = "NCNN";
    private android.content.Context context;

    // YOLOv11参数
    private static final int INPUT_SIZE = 640;
    private static final int NUM_CLASSES = 80;
    private static final float CONFIDENCE_THRESHOLD = 0.25f;
    private static final float NMS_THRESHOLD = 0.45f;

    private boolean isModelLoaded = false;
    private long nativeObj = 0; // C++ Yolo对象的指针

    // COCO类别
    private static final String[] COCO_CLASSES = {
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
            "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
            "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
            "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
            "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
            "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
            "toothbrush"
    };

    public static class Obj {
        public float x, y, w, h;
        public int label;
        public float prob;

        public Obj(float x, float y, float w, float h, int label, float prob) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.prob = prob;
        }

        public String getLabelName() {
            if (label >= 0 && label < COCO_CLASSES.length) {
                return COCO_CLASSES[label];
            }
            return "unknown";
        }
    }

    // 加载本地库
    static {
        try {
            System.loadLibrary("yolo_jni");
            Log.d(TAG, "yolo_jni library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load yolo_jni library: " + e.getMessage());
        }
    }

    // Native方法声明
    private native long nativeCreateObject();
    private native void nativeDestroyObject(long thiz);
    private native boolean nativeLoadModel(long thiz, android.content.Context context, AssetManager assetManager, String paramPath, String binPath);private native Obj[] nativeDetect(long thiz, long matAddr, float confidenceThreshold, float nmsThreshold);
    private native void nativeUnloadModel(long thiz);
    private native boolean nativeIsModelLoaded(long thiz);
    public NcnnYolo(android.content.Context context) {
        this.context = context;
        nativeObj = nativeCreateObject();
        Log.d(TAG, "NcnnYolo constructor called, nativeObj: " + nativeObj);
    }

    public NcnnYolo() {
        nativeObj = nativeCreateObject();
        Log.d(TAG, "NcnnYolo constructor called, nativeObj: " + nativeObj);
    }

    @Override
    protected void finalize() throws Throwable {
        if (nativeObj != 0) {
            nativeDestroyObject(nativeObj);
            nativeObj = 0;
        }
        super.finalize();
    }

    public boolean loadModel(android.content.Context context, AssetManager assetManager, String paramPath, String binPath) {
        Log.d(TAG, "loadModel called with nativeObj: " + nativeObj);

        if (nativeObj == 0) {
            Log.e(TAG, "Native object not created");
            return false;
        }

        try {
            // 检查文件是否存在且可读
            InputStream paramStream = assetManager.open(paramPath);
            InputStream binStream = assetManager.open(binPath);
            long paramSize = paramStream.available();
            long binSize = binStream.available();
            paramStream.close();
            binStream.close();

            Log.d(TAG, "Model file sizes - param: " + paramSize + ", bin: " + binSize);

            isModelLoaded = nativeLoadModel(nativeObj, context, assetManager, paramPath, binPath);
            Log.d(TAG, "Model load result: " + isModelLoaded);

            // 检查模型加载状态
            boolean nativeLoaded = nativeIsModelLoaded(nativeObj);
            Log.d(TAG, "Native model loaded status: " + nativeLoaded);

            return isModelLoaded && nativeLoaded;
        } catch (Exception e) {
            Log.e(TAG, "Exception in loadModel: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public Obj[] detect(Bitmap bitmap, float confidenceThreshold, float nmsThreshold) {
        if (!isModelLoaded || nativeObj == 0) {
            Log.e(TAG, "Model not loaded for Bitmap detection");
            return new Obj[0];
        }

        try {
            // 将Bitmap转换为OpenCV Mat
            Mat mat = new Mat();
            Utils.bitmapToMat(bitmap, mat);
            Obj[] results = nativeDetect(nativeObj, mat.getNativeObjAddr(), confidenceThreshold, nmsThreshold);
            mat.release();
            return results;
        } catch (Exception e) {
            Log.e(TAG, "Detection from Bitmap failed: " + e.getMessage());
            e.printStackTrace();
            return new Obj[0];
        }
    }

    public Obj[] detect(Mat mat, float confidenceThreshold, float nmsThreshold) {
        Log.d(TAG, "detect called - isModelLoaded: " + isModelLoaded + ", nativeObj: " + nativeObj);

        if (!isModelLoaded || nativeObj == 0) {
            Log.e(TAG, "Model not loaded for Mat detection");
            return new Obj[0];
        }

        try {
            if (mat.empty()) {
                Log.e(TAG, "Input Mat is empty");
                return new Obj[0];
            }

            Log.d(TAG, "Detecting from Mat - size: " + mat.cols() + "x" + mat.rows());
            Obj[] results = nativeDetect(nativeObj, mat.getNativeObjAddr(), confidenceThreshold, nmsThreshold);
            Log.d(TAG, "Detection completed, found " + (results != null ? results.length : 0) + " objects");
            return results != null ? results : new Obj[0];
        } catch (Exception e) {
            Log.e(TAG, "Detection from Mat failed: " + e.getMessage());
            e.printStackTrace();
            return new Obj[0];
        }
    }

    public void unloadModel() {
        Log.d(TAG, "unloadModel called");
        if (nativeObj != 0) {
            nativeUnloadModel(nativeObj);
        }
        isModelLoaded = false;
        Log.d(TAG, "Model unloaded");
    }

    public boolean isModelLoaded() {
        boolean result = isModelLoaded && nativeObj != 0;
        Log.d(TAG, "isModelLoaded: " + result);
        return result;
    }

    public boolean isNativeObjectCreated() {
        boolean result = nativeObj != 0;
        Log.d(TAG, "isNativeObjectCreated: " + result);
        return result;
    }

    // 添加一个简单的测试方法
    public String getModelStatus() {
        return "NativeObj: " + nativeObj + ", ModelLoaded: " + isModelLoaded;
    }
}