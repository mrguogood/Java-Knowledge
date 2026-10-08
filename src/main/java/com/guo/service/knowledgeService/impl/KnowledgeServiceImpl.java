package com.guo.service.knowledgeService.impl;

import com.guo.common.PageResult;
import com.guo.entity.KnowledgeFile;
import com.guo.mapper.KnowledgeFileMapper;
import com.guo.service.knowledgeService.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库文件管理服务实现
 * <p>
 * 文件本体存储于 uploadFiles 目录，数据库仅存元信息。
 * 支持 pdf / word / excel 上传、下载、增删改查。
 *
 * @author gcs
 * @create 2026-09-29
 */
@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    /** 上传根目录（默认项目根目录/uploadFiles） */
    @Value("${knowledge.upload-dir:${user.dir}/uploadFiles}")
    private String uploadDir;

    /** 允许的文件类型扩展名 → 类型标识 */
    private static final Map<String, String> EXT_TO_TYPE = new ConcurrentHashMap<>();

    static {
        // pdf
        EXT_TO_TYPE.put("pdf", "pdf");
        // word
        EXT_TO_TYPE.put("doc", "word");
        EXT_TO_TYPE.put("docx", "word");
        // excel
        EXT_TO_TYPE.put("xls", "excel");
        EXT_TO_TYPE.put("xlsx", "excel");
    }

    @Autowired
    private KnowledgeFileMapper knowledgeFileMapper;

    @Override
    public KnowledgeFile upload(MultipartFile file, String title, String category, String author, String status) {
        // 1. 空文件校验
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        // 2. 扩展名白名单校验
        String originalName = file.getOriginalFilename();
        String ext = StringUtils.getFilenameExtension(originalName);
        String fileType = ext == null ? null : EXT_TO_TYPE.get(ext.toLowerCase());
        if (fileType == null) {
            throw new IllegalArgumentException("仅支持 pdf / word / excel 格式文件");
        }

        // 3. 存储文件本体
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();
        String relativePath = "uploadFiles/" + storedName;
        saveToDisk(storedName, file);

        // 4. 组装元数据入库
        KnowledgeFile entity = new KnowledgeFile();
        entity.setTitle(StringUtils.hasText(title) ? title : originalName);
        entity.setCategory(StringUtils.hasText(category) ? category : "其他");
        entity.setAuthor(StringUtils.hasText(author) ? author : "");
        entity.setStatus(StringUtils.hasText(status) ? status : "草稿");
        entity.setViewCount(0);
        entity.setFileType(fileType);
        entity.setFileName(originalName);
        entity.setStoredName(storedName);
        entity.setFilePath(relativePath);
        entity.setFileSize(file.getSize());

        knowledgeFileMapper.insert(entity);
        log.info("知识文件上传成功，id: {}, 类型: {}, 文件名: {}", entity.getId(), fileType, originalName);
        return entity;
    }

    @Override
    public PageResult<KnowledgeFile> list(String keyword, String category, int page, int pageSize) {
        if (page <= 0) {
            page = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }
        int offset = (page - 1) * pageSize;
        List<KnowledgeFile> records = knowledgeFileMapper.selectPage(keyword, category, offset, pageSize);
        long total = knowledgeFileMapper.countList(keyword, category);
        return PageResult.of(records, total);
    }

    @Override
    public KnowledgeFile detail(Long id) {
        knowledgeFileMapper.increaseViewCount(id);
        return knowledgeFileMapper.selectById(id);
    }

    @Override
    public KnowledgeFile update(KnowledgeFile entity, MultipartFile file) {
        KnowledgeFile exists = knowledgeFileMapper.selectById(entity.getId());
        if (exists == null) {
            throw new IllegalArgumentException("记录不存在，id: " + entity.getId());
        }

        // 如果带了新文件，先存盘再更新文件相关字段
        if (file != null && !file.isEmpty()) {
            String originalName = file.getOriginalFilename();
            String ext = StringUtils.getFilenameExtension(originalName);
            String fileType = ext == null ? null : EXT_TO_TYPE.get(ext.toLowerCase());
            if (fileType == null) {
                throw new IllegalArgumentException("仅支持 pdf / word / excel 格式文件");
            }

            String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();
            saveToDisk(storedName, file);

            entity.setFileType(fileType);
            entity.setFileName(originalName);
            entity.setStoredName(storedName);
            entity.setFilePath("uploadFiles/" + storedName);
            entity.setFileSize(file.getSize());

            // 新文件已写入磁盘，删除旧物理文件
            deleteDiskFile(exists.getStoredName());
        }

        knowledgeFileMapper.update(entity);
        return knowledgeFileMapper.selectById(entity.getId());
    }

    @Override
    public void delete(Long id) {
        KnowledgeFile exists = knowledgeFileMapper.selectById(id);
        if (exists == null) {
            throw new IllegalArgumentException("记录不存在，id: " + id);
        }
        knowledgeFileMapper.deleteById(id);
        deleteDiskFile(exists.getStoredName());
        log.info("知识文件已删除，id: {}, 文件名: {}", id, exists.getFileName());
    }

    @Override
    public File resolveFile(String storedName) {
        Path path = Paths.get(uploadDir, storedName);
        return path.toFile();
    }

    // ==================== 私有工具 ====================

    /**
     * 将上传文件写入 uploadFiles 目录
     */
    private void saveToDisk(String storedName, MultipartFile file) {
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path target = uploadPath.resolve(storedName);
            file.transferTo(target);
            log.debug("文件已保存: {}", target.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("文件存储失败", e);
        }
    }

    /**
     * 删除磁盘上的物理文件（找不到不报错）
     */
    private void deleteDiskFile(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            return;
        }
        try {
            Path path = Paths.get(uploadDir, storedName);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("物理文件删除失败，storedName: {}", storedName, e);
        }
    }
}