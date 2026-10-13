package com.guo.service.parse;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切片工具
 * <p>
 * 将长文档按固定长度切块并带重叠，保证相邻切片语义连贯。
 * 中文场景建议 400-600 字符、重叠率 10% 左右检索效果最佳。
 *
 * @author gcs
 */
@Component
public class ChineseTextChunker {

    /**
     * 递归式固定窗口切片
     *
     * @param text   待切片文本（已清洗）
     * @param size   单块最大字符数
     * @param overlap 相邻块重叠字符数，应小于 size
     * @return 切片列表
     */
    public List<String> split(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }
        String source = text.trim();
        int length = source.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + size, length);
            chunks.add(source.substring(start, end).trim());
            if (end == length) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }
}