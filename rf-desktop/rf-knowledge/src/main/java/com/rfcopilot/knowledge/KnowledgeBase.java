package com.rfcopilot.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rfcopilot.core.RfPaths;
import com.rfcopilot.core.config.ModelProfile;
import com.rfcopilot.core.model.EmbeddingModelFactory;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.parser.markdown.MarkdownDocumentParser;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 知识库服务：文档解析 → 递归分块 → 嵌入 → InMemoryEmbeddingStore 持久化。
 * 向量文件：~/.rfcopilot/knowledge/vectors.json；收录清单：manifest.json。
 */
public class KnowledgeBase {

    /** 分块大小（字符）与重叠 */
    private static final int CHUNK_CHARS = 600;
    private static final int CHUNK_OVERLAP = 100;
    /** 检索返回的最大片段数与相似度阈值 */
    private static final int MAX_RESULTS = 4;
    private static final double MIN_SCORE = 0.55;
    /** 片段元数据键：来源文件绝对路径 */
    public static final String METADATA_SOURCE = "source";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 支持的文件扩展名 */
    public static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "md", "markdown", "txt");

    private final Path vectorsFile = RfPaths.knowledgeDir().resolve("vectors.json");
    private final Path manifestFile = RfPaths.knowledgeDir().resolve("manifest.json");
    private final ObjectMapper mapper = new ObjectMapper();

    private InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
    private final List<KnowledgeEntry> entries = new ArrayList<>();

    /** 启动时加载已持久化的向量与清单；文件损坏时回退为空知识库 */
    public synchronized void load() {
        try {
            RfPaths.ensureDirs();
            if (Files.exists(vectorsFile)) {
                store = InMemoryEmbeddingStore.fromFile(vectorsFile);
            }
            if (Files.exists(manifestFile)) {
                entries.clear();
                entries.addAll(mapper.readValue(manifestFile.toFile(),
                        new TypeReference<List<KnowledgeEntry>>() {
                        }));
            }
        } catch (Exception e) {
            store = new InMemoryEmbeddingStore<>();
            entries.clear();
        }
    }

    public synchronized List<KnowledgeEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized boolean isEmpty() {
        return store.isEmpty();
    }

    public synchronized boolean contains(Path file) {
        String key = absoluteKey(file);
        return entries.stream().anyMatch(e -> e.getPath().equals(key));
    }

    /** 判断文件扩展名是否受支持 */
    public static boolean isSupported(Path file) {
        return SUPPORTED_EXTENSIONS.contains(extensionOf(file));
    }

    /**
     * 收录单个文件：解析 → 分块 → 嵌入 → 持久化。
     *
     * @return 新增片段数
     * @throws IllegalArgumentException 重复收录
     * @throws IOException              解析或写入失败
     */
    public synchronized int importFile(Path file, ModelProfile profile, Consumer<String> progress)
            throws IOException {
        Path abs = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(abs)) {
            throw new IOException("文件不存在：" + abs);
        }
        if (contains(abs)) {
            throw new IllegalArgumentException("文件已收录：" + abs.getFileName());
        }
        progress.accept("正在解析：" + abs.getFileName());
        List<TextSegment> segments = parseAndSplit(abs);
        if (segments.isEmpty()) {
            throw new IOException("未能从文档中提取到文本：" + abs.getFileName());
        }
        progress.accept("解析完成（" + segments.size() + " 个片段），正在嵌入：" + abs.getFileName());
        EmbeddingModel model = EmbeddingModelFactory.create(profile);
        List<Embedding> embeddings = embedInBatches(model, segments, progress);
        store.addAll(embeddings, segments);
        persistVectors();
        entries.add(new KnowledgeEntry(abs.toString(), abs.getFileName().toString(),
                segments.size(), LocalDateTime.now().format(FMT)));
        persistManifest();
        return segments.size();
    }

    /** 删除某文件对应的全部向量与清单项 */
    public synchronized void delete(KnowledgeEntry entry, Consumer<String> progress) throws IOException {
        progress.accept("正在删除：" + entry.getName());
        store.removeAll(MetadataFilterBuilder.metadataKey(METADATA_SOURCE).isEqualTo(entry.getPath()));
        entries.removeIf(e -> e.getPath().equals(entry.getPath()));
        persistVectors();
        persistManifest();
    }

    /** 清空知识库 */
    public synchronized void clear() throws IOException {
        store = new InMemoryEmbeddingStore<>();
        entries.clear();
        Files.deleteIfExists(vectorsFile);
        persistManifest();
    }

    /**
     * 重建索引：按清单重新解析并嵌入全部文件（切换嵌入模型后调用）。
     *
     * @return 总片段数
     */
    public synchronized int rebuild(ModelProfile profile, Consumer<String> progress) throws IOException {
        List<KnowledgeEntry> old = new ArrayList<>(entries);
        store = new InMemoryEmbeddingStore<>();
        entries.clear();
        EmbeddingModel model = EmbeddingModelFactory.create(profile);
        int total = 0;
        for (KnowledgeEntry e : old) {
            Path file = Path.of(e.getPath());
            if (!Files.isRegularFile(file)) {
                progress.accept("跳过（原文件不存在）：" + e.getName());
                entries.add(e);
                continue;
            }
            progress.accept("重建解析中：" + e.getName());
            List<TextSegment> segments = parseAndSplit(file);
            store.addAll(embedInBatches(model, segments, progress), segments);
            e.setChunks(segments.size());
            entries.add(e);
            total += segments.size();
            persistVectors();
            persistManifest();
        }
        return total;
    }

    /** 构建 RAG 检索器；知识库为空时返回 null */
    public synchronized ContentRetriever contentRetriever(ModelProfile profile) {
        if (store.isEmpty()) {
            return null;
        }
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(EmbeddingModelFactory.create(profile))
                .maxResults(MAX_RESULTS)
                .minScore(MIN_SCORE)
                .build();
    }

    // ---------- 内部 ----------

    /** 分批嵌入并回显进度，避免单次超大请求且让用户看到实时进展 */
    private static List<Embedding> embedInBatches(EmbeddingModel model, List<TextSegment> segments,
                                                  Consumer<String> progress) {
        final int batchSize = 32;
        List<Embedding> all = new ArrayList<>();
        for (int i = 0; i < segments.size(); i += batchSize) {
            List<TextSegment> batch = segments.subList(i, Math.min(i + batchSize, segments.size()));
            all.addAll(model.embedAll(batch).content());
            progress.accept("已嵌入 " + all.size() + "/" + segments.size() + " 个片段");
        }
        return all;
    }

    private List<TextSegment> parseAndSplit(Path file) throws IOException {
        DocumentParser parser = parserFor(file);
        Document doc;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            doc = parser.parse(in);
        }
        doc.metadata().put(METADATA_SOURCE, absoluteKey(file));
        return DocumentSplitters.recursive(CHUNK_CHARS, CHUNK_OVERLAP).split(doc);
    }

    private static DocumentParser parserFor(Path file) {
        return switch (extensionOf(file)) {
            case "pdf" -> new ApachePdfBoxDocumentParser();
            case "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> new ApachePoiDocumentParser();
            case "md", "markdown" -> new MarkdownDocumentParser();
            case "txt" -> new TextDocumentParser();
            default -> throw new IllegalArgumentException("不支持的文件类型：" + file);
        };
    }

    private static String extensionOf(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private static String absoluteKey(Path file) {
        return file.toAbsolutePath().normalize().toString();
    }

    private void persistVectors() throws IOException {
        RfPaths.ensureDirs();
        store.serializeToFile(vectorsFile);
    }

    private void persistManifest() throws IOException {
        RfPaths.ensureDirs();
        mapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile.toFile(), entries);
    }
}
