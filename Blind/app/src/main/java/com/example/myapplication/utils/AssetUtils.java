// AssetUtils.java
package com.example.myapplication.utils; // 注意包名，需与项目一致

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AssetUtils {
    private static final String TAG = "AssetUtils";

    /**
     * 从 assets 复制单个文件到 app 的 files 目录
     * @param context 上下文
     * @param filename assets 中的文件名
     * @return 复制成功返回 true，失败返回 false
     */
    public static boolean copyAssetToFilesDir(Context context, String filename) {
        AssetManager assetManager = context.getAssets();
        InputStream in = null;
        OutputStream out = null;

        try {
            // 打开 assets 中的源文件
            in = assetManager.open(filename);
            // 目标文件路径：/data/data/包名/files/filename
            File destFile = new File(context.getFilesDir(), filename);

            // 若文件已存在，直接返回成功
            if (destFile.exists()) {
                Log.d(TAG, "File already exists: " + filename);
                return true;
            }

            // 写入目标文件
            out = new FileOutputStream(destFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            Log.d(TAG, "Copy success: " + filename);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Copy failed for " + filename, e);
            return false;
        } finally {
            // 关闭流
            try {
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (IOException e) {
                Log.e(TAG, "Stream close error", e);
            }
        }
    }
}