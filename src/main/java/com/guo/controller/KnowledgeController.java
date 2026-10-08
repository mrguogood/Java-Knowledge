package com.guo.controller;

import com.guo.common.PageResult;
import com.guo.common.ResponseResult;
import com.guo.dto.KnowledgeIdDto;
import com.guo.dto.KnowledgeQueryDto;
import com.guo.dto.KnowledgeUpdateDto;
import com.guo.dto.KnowledgeUploadDto;
import com.guo.entity.KnowledgeFile;
import com.guo.service.knowledgeService.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 知识库管理控制器
 * <p>
 * 提供 pdf / word / excel 文件的上传、列表、详情、下载、更新、删除接口。
 * 文件本体存储于 uploadFiles 目录，元数据存 MySQL。所有接口统一为 POST。
 *
 * @author gcs
 * @create 2026-09-29
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    // ==================== 增：上传文件 ====================

    /**
     * 上传 pdf / word / excel 文件
     * <p>
     * Content-Type: multipart/form-data
     * 表单字段：file（必填）、title、category、author、status
     */
    @PostMapping("/upload")
    public ResponseResult<KnowledgeFile> upload(@ModelAttribute KnowledgeUploadDto dto) {
        log.info("知识文件上传，原始文件名：{}", dto.getFile() == null ? null : dto.getFile().getOriginalFilename());
        try {
            KnowledgeFile entity = knowledgeService.upload(dto.getFile(), dto.getTitle(), dto.getCategory(), dto.getAuthor(), dto.getStatus());
            return ResponseResult.success("上传成功", entity);
        } catch (Exception e) {
            log.error("知识文件上传失败：{}", e.getMessage(), e);
            return ResponseResult.fail(e.getMessage());
        }
    }

    // ==================== 查：分页列表 ====================

    /**
     * 分页条件查询
     * <p>
     * JSON body：page、pageSize、keyword（标题/作者/文件名）、category（分类）
     */
    @PostMapping("/list")
    public ResponseResult<PageResult<KnowledgeFile>> list(@RequestBody KnowledgeQueryDto dto) {
        try {
            PageResult<KnowledgeFile> result = knowledgeService.list(dto.getKeyword(), dto.getCategory(), dto.getPage(), dto.getPageSize());
            return ResponseResult.success(result);
        } catch (Exception e) {
            log.error("知识列表查询失败：{}", e.getMessage(), e);
            return ResponseResult.fail(e.getMessage());
        }
    }

    // ==================== 查：详情 ====================

    /**
     * 详情查询（浏览量 +1）
     * <p>
     * JSON body：id
     */
    @PostMapping("/detail")
    public ResponseResult<KnowledgeFile> detail(@RequestBody KnowledgeIdDto dto) {
        Long id = dto.getId();
        try {
            KnowledgeFile entity = knowledgeService.detail(id);
            if (entity == null) {
                return ResponseResult.fail("资源不存在");
            }
            return ResponseResult.success(entity);
        } catch (Exception e) {
            log.error("知识详情查询失败，id: {}，{}", id, e.getMessage(), e);
            return ResponseResult.fail(e.getMessage());
        }
    }

    // ==================== 查：下载/查看文件 ====================

    /**
     * 下载/预览文件
     * <p>
     * JSON body：id
     */
    @PostMapping("/download")
    public ResponseEntity<Resource> download(@RequestBody KnowledgeIdDto dto) {
        Long id = dto.getId();
        KnowledgeFile entity = knowledgeService.detail(id);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }
        File file = knowledgeService.resolveFile(entity.getStoredName());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = mediaTypeFor(entity.getFileType());
        String filename = encodeFileName(entity.getFileName());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .body(new FileSystemResource(file));
    }

    // ==================== 改：更新元信息 / 换文件 ====================

    /**
     * 更新记录（可改标题、分类、作者、状态；可选传新文件覆盖）
     * <p>
     * Content-Type: multipart/form-data
     * 表单字段：id（必填）、title、category、author、status、file（可选）
     */
    @PostMapping("/update")
    public ResponseResult<KnowledgeFile> update(@ModelAttribute KnowledgeUpdateDto dto) {
        log.info("知识文件更新，id：{}", dto.getId());
        try {
            KnowledgeFile entity = new KnowledgeFile();
            entity.setId(dto.getId());
            entity.setTitle(dto.getTitle());
            entity.setCategory(dto.getCategory());
            entity.setAuthor(dto.getAuthor());
            entity.setStatus(dto.getStatus());
            KnowledgeFile updated = knowledgeService.update(entity, dto.getFile());
            return ResponseResult.success("更新成功", updated);
        } catch (Exception e) {
            log.error("知识文件更新失败，id：{}，{}", dto.getId(), e.getMessage(), e);
            return ResponseResult.fail(e.getMessage());
        }
    }

    // ==================== 删：删除记录 + 物理文件 ====================

    /**
     * 删除记录并清理磁盘文件
     * <p>
     * JSON body：id
     */
    @PostMapping("/delete")
    public ResponseResult<Void> delete(@RequestBody KnowledgeIdDto dto) {
        Long id = dto.getId();
        log.info("知识文件删除，id：{}", id);
        try {
            knowledgeService.delete(id);
            return ResponseResult.success();
        } catch (Exception e) {
            log.error("知识文件删除失败，id：{}，{}", id, e.getMessage(), e);
            return ResponseResult.fail(e.getMessage());
        }
    }

    // ==================== 私有工具 ====================

    /**
     * 文件名 URL 编码（JDK 8 兼容：String 版 encode 会抛受检异常，这里做兼容处理）
     */
    private String encodeFileName(String name) {
        try {
            return URLEncoder.encode(name, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return name;
        }
    }

    private String mediaTypeFor(String fileType) {
        if ("excel".equals(fileType)) {
            return "application/vnd.ms-excel";
        }
        if ("word".equals(fileType)) {
            return "application/msword";
        }
        // 默认 pdf / 其他
        return "application/pdf";
    }
}