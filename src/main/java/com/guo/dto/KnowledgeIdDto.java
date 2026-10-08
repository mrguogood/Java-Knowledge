package com.guo.dto;

import lombok.Data;

/**
 * 按 ID 操作的通用请求参数
 * <p>
 * 用于详情、下载、删除等仅需主键的接口。
 *
 * @author gcs
 * @create 2026-09-29
 */
@Data
public class KnowledgeIdDto {

    /** 记录主键ID */
    private Long id;
}