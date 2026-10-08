package com.guo.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识文件更新请求参数
 * <p>
 * multipart/form-data 表单字段：id（必填）、title、category、author、status、file（可选）
 * 传 file 表示换文件覆盖，否则仅更新元信息。
 *
 * @author gcs
 * @create 2026-09-29
 */
@Data
public class KnowledgeUpdateDto {

    /** 记录主键ID（必填） */
    private Long id;

    /** 知识标题 */
    private String title;

    /** 分类 */
    private String category;

    /** 作者/上传人 */
    private String author;

    /** 状态（草稿 / 已发布） */
    private String status;

    /** 可选：新文件（仅支持 pdf / word / excel） */
    private MultipartFile file;
}