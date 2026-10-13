package com.guo.service.knowledgeService.impl;

import com.guo.common.PageResult;
import com.guo.entity.KnowledgeFile;
import com.guo.mapper.KnowledgeFileMapper;
import com.guo.service.knowledgeService.KnowledgeService;
import com.guo.service.parse.DocumentParseService;
import com.guo.service.parse.TextCleaningService;
import com.guo.service.vector.MilvusVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    @Autowired
    private DocumentParseService documentParseService;

    @Autowired
    private TextCleaningService textCleaningService;

    @Autowired
    private MilvusVectorStore milvusVectorStore;

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
        String baseName = StringUtils.stripFilenameExtension(originalName);
        String random3 = String.valueOf(100 + new Random().nextInt(900));
        String storedName = baseName + random3 + System.currentTimeMillis() + "." + ext.toLowerCase();
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

        // 5. 解析并写入向量索引（失败不阻断文件入库，仅告警日志）
        indexVector(entity);
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

            // 3. 存储文件本体
            String baseName = StringUtils.stripFilenameExtension(originalName);
            String random3 = String.valueOf(100 + new Random().nextInt(900));
            String storedName = baseName + random3 + System.currentTimeMillis() + "." + ext.toLowerCase();
            saveToDisk(storedName, file);

            entity.setFileType(fileType);
            entity.setFileName(originalName);
            entity.setStoredName(storedName);
            entity.setFilePath("uploadFiles/" + storedName);
            entity.setFileSize(file.getSize());

            // 新文件已写入磁盘，删除旧物理文件，并清理旧向量后重建索引
            deleteDiskFile(exists.getStoredName());
            milvusVectorStore.deleteByKnowledgeId(exists.getId());
        }

        knowledgeFileMapper.update(entity);
        KnowledgeFile updated = knowledgeFileMapper.selectById(entity.getId());
        indexVector(updated);
        return updated;
    }

    @Override
    public void delete(Long id) {
        KnowledgeFile exists = knowledgeFileMapper.selectById(id);
        if (exists == null) {
            throw new IllegalArgumentException("记录不存在，id: " + id);
        }
        knowledgeFileMapper.deleteById(id);
        deleteDiskFile(exists.getStoredName());
        milvusVectorStore.deleteByKnowledgeId(id);
        log.info("知识文件已删除，id: {}, 文件名: {}", id, exists.getFileName());
    }

    @Override
    public File resolveFile(String storedName) {
        Path path = Paths.get(uploadDir, storedName);
        return path.toFile();
    }

    // ==================== 私有工具 ====================

    /**
     * 解析已落盘文档并写入 Milvus 向量索引
     * <p>
     * 全流程：Tika 解析 → 双层清洗 → 切片 → 嵌入 → 入库。
     * 向量化失败不影响文件元数据入库，仅记录告警日志，便于后续补偿重试。
     */
    private void indexVector(KnowledgeFile entity) {
        File source = resolveFile(entity.getStoredName());
        if (source == null || !source.exists()) {
            log.warn("向量化异常：文件不存在，id: {}, storedName: {}", entity.getId(), entity.getStoredName());
            return;
        }
        try (InputStream in = Files.newInputStream(source.toPath())) {
            String rawText = documentParseService.parse(in, entity.getFileName());
            String cleanText = textCleaningService.clean(rawText);
            if (StringUtils.hasText(cleanText)) {
                milvusVectorStore.indexFile(entity, cleanText);
            } else {
                log.warn("文档解析后无有效文本，跳过向量化，id: {}", entity.getId());
            }
        } catch (Exception e) {
            log.error("文档向量化失败，id: {}, 文件名: {}", entity.getId(), entity.getFileName(), e);
        }
    }

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