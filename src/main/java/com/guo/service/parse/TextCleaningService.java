package com.guo.service.parse;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 文本清洗服务
 * <p>
 * 在 Tika 解析之后做第二层兜底清理，处理解析阶段残留的噪音：
 * 控制字符、图片文件名、图片链接、临时文件路径、分隔线、连续空行。
 * 正则均预编译，且采用"整行精确匹配"原则，最大限度避免误删正文。
 *
 * @author gcs
 */
@Service
public class TextCleaningService {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Pattern IMAGE_FILENAME_LINE = Pattern.compile(
            "(?m)^\\s*(?:image|img)\\d*\\.(?:png|jpe?g|gif|bmp|webp)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE_URL = Pattern.compile(
            "https?://\\S+\\.(?:png|jpe?g|gif|bmp|webp)");
    private static final Pattern FILE_URL = Pattern.compile("file:///\\S+");
    private static final Pattern SEPARATOR_LINE = Pattern.compile("(?m)^[=*\\-]{3,}\\s*$");
    private static final Pattern LINE_TRAILING_SPACES = Pattern.compile("(?m)[ \\t]+$");

    /**
     * 清洗解析后的原始文本
     *
     * @param text Tika 解析出的文本
     * @return 清洗后的干净正文，空输入返回空串
     */
    public String clean(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        String t = text;

        // 第一层：语义去噪
        t = CONTROL_CHARS.matcher(t).replaceAll("");
        t = IMAGE_FILENAME_LINE.matcher(t).replaceAll("");
        t = IMAGE_URL.matcher(t).replaceAll("");
        t = FILE_URL.matcher(t).replaceAll("");
        t = SEPARATOR_LINE.matcher(t).replaceAll("");

        // 第二层：格式规范化
        t = t.replace("\r\n", "\n").replace("\r", "\n");
        t = LINE_TRAILING_SPACES.matcher(t).replaceAll("");
        t = t.replaceAll("\\n{3,}", "\n\n");

        return t.trim();
    }
}