package com.guo.mapper;

import com.guo.entity.KnowledgeFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库文件 Mapper
 * <p>
 * 使用 MyBatis XML 方式，SQL 定义在 resources/mapper/KnowledgeFileMapper.xml。
 *
 * @author gcs
 * @create 2026-09-29
 */
@Mapper
public interface KnowledgeFileMapper {

    int insert(KnowledgeFile entity);

    KnowledgeFile selectById(Long id);

    List<KnowledgeFile> selectPage(@Param("keyword") String keyword,
                                   @Param("category") String category,
                                   @Param("offset") int offset,
                                   @Param("pageSize") int pageSize);

    long countList(@Param("keyword") String keyword,
                   @Param("category") String category);

    int update(KnowledgeFile entity);

    int increaseViewCount(Long id);

    int deleteById(Long id);
}