package com.guo.service.parse;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文档解析服务
 * <p>
 * 基于 Apache Tika 自动识别文件真实类型并提取正文，一次 API 覆盖 PDF/Word/Excel/TXT 等格式。
 * 关键工程配置：
 * 1. 魔数检测（Tika.detect）替代后缀名，防止伪造类型；
 * 2. BodyContentHandler 限定最大文本长度，防止恶意文件 OOM；
 * 3. NoOpEmbeddedDocumentExtractor 屏蔽嵌入资源（图片/附件）噪音；
 * 4. PDF 关闭内嵌图片提取、按坐标排序保证多栏阅读顺序。
 *
 * @author gcs
 */
@Service
public class DocumentParseService {

    /** 最大解析文本长度：5MB，超过抛 SAXException，防 OOM */
    private static final int MAX_TEXT_LENGTH = 5 * 1024 * 1024;

    private static final String[] ALLOWED_TYPES = {
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/plain;charset=UTF-8"
    };

    private final Tika tika = new Tika();

    /**
     * 校验文件真实类型是否在白名单内（基于魔数，不信任 Content-Type/后缀名）
     *
     * @param inputStream 文件输入流
     * @param fileName    原始文件名（辅助 Tika 识别）
     * @throws IllegalArgumentException 类型不在白名单内
     */
    public void assertAllowedType(TikaInputStream inputStream, String fileName) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String contentType = tika.detect(inputStream, fileName);
        for (String allowed : ALLOWED_TYPES) {
            if (contentType != null && contentType.toLowerCase().startsWith(allowed.toLowerCase())) {
                return;
            }
        }
        throw new IllegalArgumentException("不支持的文件类型：" + contentType + "，仅支持 PDF/Word/文本");
    }

    /**
     * 解析文档提取干净正文（已内置嵌入资源屏蔽与长度限制）
     *
     * @param inputStream 文件输入流
     * @return 解析出的原始文本（尚未清洗）
     */
    public String parse(InputStream inputStream, String fileName) throws IOException, SAXException, TikaException {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);

        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        context.set(EmbeddedDocumentExtractor.class, new NoOpEmbeddedDocumentExtractor());

        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(false);
        pdfConfig.setSortByPosition(true);
        context.set(PDFParserConfig.class, pdfConfig);

        parser.parse(inputStream, handler, metadata, context);
        return handler.toString();
    }
}