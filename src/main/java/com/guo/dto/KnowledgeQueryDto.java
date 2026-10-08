package com.guo.dto;

import lombok.Data;

/**
 * 知识库分页查询请求参数
 *
 * @author gcs
 * @create 2026-09-29
 */
@Data
public class KnowledgeQueryDto {

    /** 页码（从 1 开始） */
    private int page = 1;

    /** 每页条数 */
    private int pageSize = 10;

    /** 关键字（匹配标题 / 作者 / 原始文件名） */
    private String keyword;

    /** 分类过滤 */
    private String category;
}