package com.example.volunteer.utils;

import android.text.TextUtils;
import java.io.InputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

public class WordReader {

    /**
     * 从assets读取Word文档并提取文字
     */
    public static String readWordFromAssets(InputStream inputStream) {
        try {
            XWPFDocument document = new XWPFDocument(inputStream);
            StringBuilder text = new StringBuilder();

            // 遍历所有段落
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String paragraphText = paragraph.getText();
                if (!TextUtils.isEmpty(paragraphText)) {
                    text.append(paragraphText).append("\n\n");
                }
            }

            document.close();
            inputStream.close();

            return text.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "读取Word文档失败: " + e.getMessage();
        }
    }
}