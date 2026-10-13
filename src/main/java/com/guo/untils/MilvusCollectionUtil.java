package com.guo.untils;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.ShowCollectionsParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

/**
 * Milvus 集合工具类：获取集合列表、创建集合、删除集合。
 * <p>
 * 提供基础的集合管理能力，适用于运维排查或动态建表场景。
 *
 * @author gcs
 */
@Slf4j
@Component
public class MilvusCollectionUtil {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private Integer port;

    private MilvusServiceClient client;

    @PostConstruct
    public void init() {
        client = new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build());
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * 获取当前 Milvus 中所有集合名称
     *
     * @return 集合名列表，查询失败时返回空列表
     */
    public  List<String> listCollections() {
        List<String> collections = new ArrayList<>();
        try {
            R<io.milvus.grpc.ShowCollectionsResponse> response =
                    client.showCollections(ShowCollectionsParam.newBuilder().build());
            if (response.getStatus() == R.Status.Success.getCode()) {
                collections.addAll(response.getData().getCollectionNamesList());
                log.info("Milvus 当前集合列表: {}", collections);
            } else {
                log.error("获取集合列表失败: {}", response.getMessage());
            }
        } catch (Exception e) {
            log.error("获取集合列表异常", e);
        }
        return collections;
    }

    /**
     * 创建集合（若已存在则直接返回，不重复创建）
     * <p>
     * 默认的知识向量集合结构：id(自增主键)/knowledge_id/chunk_index/content/vector
     *
     * @param collectionName 集合名
     * @param dimension      向量维度，须与嵌入模型输出维度一致
     * @return 是否创建成功（已存在也视为成功）
     */
    public boolean createCollection(String collectionName, int dimension) {
        try {
            if (listCollections().contains(collectionName)) {
                log.warn("集合已存在，跳过创建: {}", collectionName);
                return true;
            }
            FieldType idField = FieldType.newBuilder()
                    .withName("id")
                    .withDataType(DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(true)
                    .build();
            FieldType knowledgeIdField = FieldType.newBuilder()
                    .withName("knowledge_id")
                    .withDataType(DataType.Int64)
                    .build();
            FieldType chunkIndexField = FieldType.newBuilder()
                    .withName("chunk_index")
                    .withDataType(DataType.Int64)
                    .build();
            FieldType contentField = FieldType.newBuilder()
                    .withName("content")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(8192)
                    .build();
            FieldType vectorField = FieldType.newBuilder()
                    .withName("vector")
                    .withDataType(DataType.FloatVector)
                    .withDimension(dimension)
                    .build();

            CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                    .addFieldType(idField)
                    .addFieldType(knowledgeIdField)
                    .addFieldType(chunkIndexField)
                    .addFieldType(contentField)
                    .addFieldType(vectorField)
                    .build();

            R<RpcStatus> response = client.createCollection(CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withSchema(schema)
                    .build());
            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("集合创建成功: {}", collectionName);
                return true;
            }
            log.error("创建集合失败: {}", response.getMessage());
            return false;
        } catch (Exception e) {
            log.error("创建集合异常: {}", collectionName, e);
            return false;
        }
    }

    /**
     * 删除集合
     *
     * @param collectionName 集合名
     * @return 是否删除成功
     */
    public boolean dropCollection(String collectionName) {
        try {
            if (!listCollections().contains(collectionName)) {
                log.warn("集合不存在，无需删除: {}", collectionName);
                return false;
            }
            R<RpcStatus> response = client.dropCollection(DropCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());
            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("集合删除成功: {}", collectionName);
                return true;
            }
            log.error("删除集合失败: {}", response.getMessage());
            return false;
        } catch (Exception e) {
            log.error("删除集合异常: {}", collectionName, e);
            return false;
        }
    }
}