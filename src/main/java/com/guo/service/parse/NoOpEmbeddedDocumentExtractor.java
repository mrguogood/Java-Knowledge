package com.guo.service.parse;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;

import java.io.InputStream;

/**
 * 跳过嵌入资源提取器
 * <p>
 * Tika 解析文档遇到嵌入资源（Word 图片、PDF 附件、Excel 图表等）时，
 * 默认实现会把 image1.jpeg、file:///tmp/xxx 之类噪音混进正文。
 * 本实现让 shouldParseEmbedded 恒返回 false，从源头屏蔽嵌入资源，
 * 保证解析结果只有干净正文。
 *
 * @author gcs
 */
public class NoOpEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {

    @Override
    public boolean shouldParseEmbedded(Metadata metadata) {
        return false;
    }

    @Override
    public void parseEmbedded(InputStream stream, ContentHandler handler,
                              Metadata metadata, boolean outputHtml) {
        // shouldParseEmbedded 返回 false 后不会被调用，空实现兜底
    }
}