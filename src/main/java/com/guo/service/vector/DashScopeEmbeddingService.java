package com.guo.service.vector;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DashScope 通义文本嵌入服务
 * <p>
 * 把文档切片转成向量。基于 dashscope-sdk-java 的 TextEmbedding，
 * 输出维度见 ai.embedding.dimension，必须与 Milvus 集合维度严格一致。
 *
 * @author gcs
 */
@Service
public class DashScopeEmbeddingService {

    @Value("${ai.qwen.api-key}")
    private String apiKey;

    @Value("${ai.embedding.model}")
    private String model;

    /**
     * 向量化单个文本
     *
     * @param text 文本内容
     * @return 该文本的向量
     */
    public List<Float> embed(String text) {
        return embedBatch(Collections.singletonList(text)).get(0);
    }

    /**
     * 批量向量化文本
     * <p>
     * 通义千问 embedding 接口单次批次上限 20，超过需分批调用后合并。
     *
     * @param texts 文本列表
     * @return 对应向量列表，顺序与入参一致
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        // 单次请求批次上限（DashScope 限制不超过 20）
        final int batchSize = 20;
        List<List<Float>> allVectors = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> subTexts = texts.subList(i, Math.min(i + batchSize, texts.size()));
            allVectors.addAll(embedBatchInternal(subTexts));
        }
        return allVectors;
    }

    private List<List<Float>> embedBatchInternal(List<String> texts) {
        try {
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .texts(texts)
                    .build();
            TextEmbeddingResult result = new TextEmbedding().call(param);
            List<List<Float>> vectors = new ArrayList<>(result.getOutput().getEmbeddings().size());
            result.getOutput().getEmbeddings().forEach(embedding -> {
                List<Double> doubles = embedding.getEmbedding();
                List<Float> floats = new ArrayList<>(doubles.size());
                for (Double d : doubles) {
                    floats.add(d.floatValue());
                }
                vectors.add(floats);
            });
            return vectors;
        } catch (Exception e) {
            throw new IllegalStateException("文本向量化失败", e);
        }
    }
}