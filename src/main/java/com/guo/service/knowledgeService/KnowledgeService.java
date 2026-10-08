package com.guo.service.knowledgeService;

import com.guo.common.PageResult;
import com.guo.entity.KnowledgeFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文件管理服务
 * <p>
 * 支持 pdf / word / excel 的增删改查，文件本体存本地 uploadFiles 目录，
 * 元数据存 MySQL。
 *
 * @author gcs
 * @create 2026-09-29
 */
public interface KnowledgeService {

    /**
     * 上传文件并新增记录
     *
     * @param file     上传文件
     * @param title    标题（为空时用原始文件名）
     * @param category 分类
     * @param author   作者
     * @param status   状态
     * @return 入库后的记录（含自增ID）
     */
    KnowledgeFile upload(MultipartFile file, String title, String category, String author, String status);

    /**
     * 分页条件查询
     */
    PageResult<KnowledgeFile> list(String keyword, String category, int page, int pageSize);

    /**
     * 根据 ID 查询详情（浏览量 +1）
     */
    KnowledgeFile detail(Long id);

    /**
     * 更新元信息（支持换文件重传）
     *
     * @param entity 只更新非空字段
     */
    KnowledgeFile update(KnowledgeFile entity, MultipartFile file);

    /**
     * 删除记录并清理物理文件
     */
    void delete(Long id);

    /**
     * 计算文件的绝对磁盘路径
     */
    java.io.File resolveFile(String storedName);
}