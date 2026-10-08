package com.guo.common;

import lombok.Data;

import java.util.List;

/**
 * 通用分页返回结果
 *
 * @param <T> 列表元素类型
 * @author gcs
 * @create 2026-09-29
 */
@Data
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    public PageResult() {
    }

    public PageResult(List<T> records, long total) {
        this.records = records;
        this.total = total;
    }

    public static <T> PageResult<T> of(List<T> records, long total) {
        return new PageResult<>(records, total);
    }
}