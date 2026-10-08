package com.guo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文件实体
 * <p>
 * 对应表 knowledge_file，用于管理上传的 pdf / word / excel 文件元数据。
 * 文件本体存放在项目根目录 uploadFiles 下，表中只保存路径等元信息。
 *
 * @author gcs
 * @create 2026-09-29
 */
@Data
public class KnowledgeFile {

    /** 主键ID */
    private Long id;

    /** 知识标题/显示名称 */
    private String title;

    /** 分类（Java / 框架 / 数据库 / 中间件 / 前端 / 运维 / 其他） */
    private String category;

    /** 作者/上传人 */
    private String author;

    /** 状态：草稿 / 已发布 */
    private String status;

    /** 浏览量 */
    private Integer viewCount;

    /** 文件类型：pdf / word / excel */
    private String fileType;

    /** 原始文件名 */
    private String fileName;

    /** 存储文件名（UUID） */
    private String storedName;

    /** 文件相对路径（uploadFiles/xxx.ext） */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}