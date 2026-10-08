package com.guo.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识文件上传请求参数
 * <p>
 * multipart/form-data 表单字段：file（必填）、title、category、author、status
 *
 * @author gcs
 * @create 2026-09-29
 */
@Data
public class KnowledgeUploadDto {

    /** 上传文件（必填，仅支持 pdf / word / excel） */
    private MultipartFile file;

    /** 知识标题（为空时使用原始文件名） */
    private String title;

    /** 分类（Java / 框架 / 数据库 / 中间件 / 前端 / 运维 / 其他） */
    private String category;

    /** 作者/上传人 */
    private String author;

    /** 状态（草稿 / 已发布） */
    private String status;
}