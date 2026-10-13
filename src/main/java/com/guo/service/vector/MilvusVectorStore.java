package com.guo.service.vector;

import com.alibaba.fastjson.JSONObject;
import com.guo.entity.KnowledgeFile;
import com.guo.service.parse.ChineseTextChunker;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.FieldData;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.MetricType;
import io.milvus.param.IndexType;
import io.milvus.param.R;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Milvus 向量存储服务
 * <p>
 * 基于 milvus-sdk-java 直连，负责：集合建置、文档切片向量化入库、
 * 按知识 ID 删除、相似度检索。集合字段：id(自增主键)/knowledge_id/chunk_index/content/vector。
 *
 * @author gcs
 */
@Slf4j
@Service
public class MilvusVectorStore {

    private static final String FIELD_KNOWLEDGE_ID = "knowledge_id";
    private static final String FIELD_CHUNK_INDEX = "chunk_index";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_VECTOR = "vector";

    /** 中文切片参数：单块 500 字符、重叠 50 */
    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private Integer port;

    @Value("${milvus.collection-name:knowledge_vectors}")
    private String collectionName;

    @Value("${milvus.dimension:1024}")
    private Integer dimension;

    @Autowired
    private DashScopeEmbeddingService embeddingService;

    @Autowired
    private ChineseTextChunker chunker;

    private MilvusServiceClient client;

    @PostConstruct
    public void init() {
        client = new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build());
        log.info("Milvus 客户端初始化参数: host: {}, port: {}", host, port);
        ensureCollection();
        log.info("Milvus 客户端已初始化，collection: {}", collectionName);
    }

    /**
     * 文档内容向量化并写入集合（切片 → 嵌入 → 入库）
     *
     * @param file    知识文件元数据（取其自增 id 作为关联键）
     * @param content 已清洗的文档正文
     */
    public void indexFile(KnowledgeFile file, String content) {
        List<String> chunks = chunker.split(content, CHUNK_SIZE, CHUNK_OVERLAP);
        if (chunks.isEmpty()) {
            log.warn("文档无可入库内容，跳过向量化，id: {}", file.getId());
            return;
        }
        List<List<Float>> vectors = embeddingService.embedBatch(chunks);

        List<JSONObject> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            JSONObject row = new JSONObject();
            row.put(FIELD_KNOWLEDGE_ID, file.getId());
            row.put(FIELD_CHUNK_INDEX, (long) i);
            row.put(FIELD_CONTENT, chunks.get(i));
            row.put(FIELD_VECTOR, vectors.get(i));
            rows.add(row);
        }

        InsertParam param = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withRows(rows)
                .build();
        R<?> r = client.insert(param);
        if (r.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("向量入库失败: " + r.getMessage());
        }
        log.info("文档向量化入库完成，id: {}, 切片数: {}", file.getId(), chunks.size());
    }

    /**
     * 根据知识 ID 删除该文档的全部向量（更新/删除时同步清理）
     *
     * @param knowledgeId 知识文件主键 id
     */
    public void deleteByKnowledgeId(Long knowledgeId) {
        ensureLoaded();
        String expr = FIELD_KNOWLEDGE_ID + " == " + knowledgeId;
        DeleteParam param = DeleteParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(expr)
                .build();
        R<?> r = client.delete(param);
        if (r.getStatus() != R.Status.Success.getCode()) {
            log.warn("向量删除失败，knowledgeId: {}, msg: {}", knowledgeId, r.getMessage());
        } else {
            log.info("向量已删除，knowledgeId: {}", knowledgeId);
        }
    }

    /**
     * 相似度检索：返回命中的文本片段
     *
     * @param questionVector 用户问题的向量
     * @param topK           返回条数
     * @return 命中片段（含原文与相似度分数）
     */
    public List<VectorHit> search(List<Float> questionVector, int topK) {
        SearchParam param = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withVectorFieldName(FIELD_VECTOR)
                .withVectors(Collections.singletonList(questionVector))
                .withTopK(topK)
                .withOutFields(Collections.singletonList(FIELD_CONTENT))
                .withParams("{\"nprobe\":16}")
                .build();

        R<SearchResults> r = client.search(param);
        List<VectorHit> hits = new ArrayList<>();
        if (r.getStatus() != R.Status.Success.getCode()) {
            log.warn("向量检索失败: {}", r.getMessage());
            return hits;
        }

        SearchResultData data = r.getData().getResults();
        if (data == null || data.getFieldsDataCount() == 0) {
            return hits;
        }

        // 找出 content 字段对应的列数据（Milvus 列式返回）
        FieldData contentField = null;
        for (FieldData fd : data.getFieldsDataList()) {
            if (FIELD_CONTENT.equals(fd.getFieldName())) {
                contentField = fd;
                break;
            }
        }

        List<Float> scores = data.getScoresList();
        for (int i = 0; i < scores.size(); i++) {
            String content = "";
            if (contentField != null && contentField.getScalars().getStringData().getDataCount() > i) {
                content = contentField.getScalars().getStringData().getData(i);
            }
            hits.add(new VectorHit(content, scores.get(i)));
        }
        return hits;
    }

    /**
     * 建集合 + 建索引 + 加载（重复调用幂等）
     */
    private void ensureCollection() {
        R<Boolean> has = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName).build());
        log.info("检查 Milvus 集合是否存在: {}, 存在: {}", collectionName, has.getData());
        if (has.getData() != null && has.getData()) {
            ensureLoaded();
            return;
        }

        CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                .addFieldType(FieldType.newBuilder()
                        .withName("id").withDataType(DataType.Int64)
                        .withPrimaryKey(true).withAutoID(true).build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_KNOWLEDGE_ID).withDataType(DataType.Int64).build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_CHUNK_INDEX).withDataType(DataType.Int64).build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_CONTENT).withDataType(DataType.VarChar)
                        .withMaxLength(8192).build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_VECTOR).withDataType(DataType.FloatVector)
                        .withDimension(dimension).build())
                .build();

        R<?> createR = client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withSchema(schema)
                .build());
        if (createR.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("创建 Milvus 集合失败: " + createR.getMessage());
        }

        R<?> indexR = client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(FIELD_VECTOR)
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":128}")
                .build());
        if (indexR.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("创建向量索引失败: " + indexR.getMessage());
        }

        client.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName).build());
        log.info("Milvus 集合创建完成: {}", collectionName);
    }

    /**
     * 确保集合已加载到内存（删除/检索前兜底，幂等）
     */
    private void ensureLoaded() {
        client.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName).build());
    }

    /**
     * 检索命中结果
     */
    public static class VectorHit {
        private final String content;
        private final float score;

        public VectorHit(String content, float score) {
            this.content = content;
            this.score = score;
        }

        public String getContent() {
            return content;
        }

        public float getScore() {
            return score;
        }
    }
}