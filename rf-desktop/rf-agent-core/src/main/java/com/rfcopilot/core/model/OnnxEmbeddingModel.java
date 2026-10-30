package com.rfcopilot.core.model;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内置离线嵌入模型：在应用进程内用 ONNX Runtime 运行 bge-small-zh-v1.5，
 * 不依赖 Ollama、不联网。模型文件随应用打包，首次使用时解压到 ~/.rfcopilot/models/。
 * 单例复用（会话初始化约需 1~2 秒）。
 */
public class OnnxEmbeddingModel implements EmbeddingModel {

    private static final String MODEL_NAME = "bge-small-zh-v1.5";
    private static final String RESOURCE_DIR = "/models/bge-small-zh-v1.5/";
    private static final int MAX_SEQ_LEN = 512;
    private static final int BATCH_SIZE = 16;

    private static volatile OnnxEmbeddingModel instance;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final BertWordPieceTokenizer tokenizer;
    private final Set<String> inputNames;
    private final String outputName;
    private final boolean pooledOutput;

    private OnnxEmbeddingModel(OrtEnvironment env, OrtSession session, BertWordPieceTokenizer tokenizer) {
        this.env = env;
        this.session = session;
        this.tokenizer = tokenizer;
        this.inputNames = session.getInputNames();
        // 优先用图内已池化输出（Xenova 导出常见），否则对 last_hidden_state 做均值池化
        if (session.getOutputNames().contains("sentence_embedding")) {
            this.outputName = "sentence_embedding";
            this.pooledOutput = true;
        } else {
            this.outputName = "last_hidden_state";
            this.pooledOutput = false;
        }
    }

    public static OnnxEmbeddingModel getInstance() {
        OnnxEmbeddingModel local = instance;
        if (local == null) {
            synchronized (OnnxEmbeddingModel.class) {
                local = instance;
                if (local == null) {
                    local = init();
                    instance = local;
                }
            }
        }
        return local;
    }

    private static OnnxEmbeddingModel init() {
        try {
            Path dir = ensureModelFiles();
            BertWordPieceTokenizer tok = BertWordPieceTokenizer.load(
                    Files.newInputStream(dir.resolve("vocab.txt")));
            OrtEnvironment environment = OrtEnvironment.getEnvironment();
            OrtSession s = environment.createSession(dir.resolve("model.onnx").toString());
            return new OnnxEmbeddingModel(environment, s, tok);
        } catch (Exception e) {
            throw new IllegalStateException("内置离线嵌入模型初始化失败：" + e.getMessage(), e);
        }
    }

    /** 模型文件优先用 ~/.rfcopilot/models/ 下已解压副本；没有则从应用资源解出 */
    private static Path ensureModelFiles() throws IOException {
        Path dir = Path.of(System.getProperty("user.home"), ".rfcopilot", "models", "bge-small-zh-v1.5");
        Path model = dir.resolve("model.onnx");
        Path vocab = dir.resolve("vocab.txt");
        if (Files.exists(model) && Files.exists(vocab) && Files.size(model) > 1_000_000) {
            return dir;
        }
        Files.createDirectories(dir);
        extract("model.onnx", model);
        extract("vocab.txt", vocab);
        return dir;
    }

    private static void extract(String name, Path target) throws IOException {
        try (InputStream in = OnnxEmbeddingModel.class.getResourceAsStream(RESOURCE_DIR + name)) {
            if (in == null) {
                throw new IOException("应用包内缺少模型资源：" + name);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> result = new ArrayList<>();
        for (int from = 0; from < textSegments.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, textSegments.size());
            result.addAll(embedBatch(textSegments.subList(from, to)));
        }
        return Response.from(result);
    }

    private List<Embedding> embedBatch(List<TextSegment> batch) {
        try {
            List<List<Integer>> encoded = new ArrayList<>();
            int maxLen = 0;
            for (TextSegment seg : batch) {
                List<Integer> ids = tokenizer.encode(seg.text(), MAX_SEQ_LEN);
                encoded.add(ids);
                maxLen = Math.max(maxLen, ids.size());
            }
            long[][] inputIds = new long[batch.size()][maxLen];
            long[][] attention = new long[batch.size()][maxLen];
            long[][] typeIds = new long[batch.size()][maxLen];
            for (int i = 0; i < batch.size(); i++) {
                List<Integer> ids = encoded.get(i);
                for (int j = 0; j < ids.size(); j++) {
                    inputIds[i][j] = ids.get(j);
                    attention[i][j] = 1;
                }
            }
            Map<String, OnnxTensor> feeds = new HashMap<>();
            feeds.put("input_ids", OnnxTensor.createTensor(env, inputIds));
            feeds.put("attention_mask", OnnxTensor.createTensor(env, attention));
            if (inputNames.contains("token_type_ids")) {
                feeds.put("token_type_ids", OnnxTensor.createTensor(env, typeIds));
            }
            try (OrtSession.Result out = session.run(feeds)) {
                List<float[]> vectors = new ArrayList<>();
                if (pooledOutput) {
                    float[][] pooled = (float[][]) ((OnnxTensor) out.get(outputName).get()).getValue();
                    for (float[] v : pooled) {
                        vectors.add(v);
                    }
                } else {
                    float[][][] hidden = (float[][][]) ((OnnxTensor) out.get(outputName).get()).getValue();
                    for (int i = 0; i < hidden.length; i++) {
                        vectors.add(meanPool(hidden[i], attention[i]));
                    }
                }
                List<Embedding> embeddings = new ArrayList<>();
                for (float[] v : vectors) {
                    embeddings.add(Embedding.from(normalize(v)));
                }
                return embeddings;
            } finally {
                feeds.values().forEach(OnnxTensor::close);
            }
        } catch (Exception e) {
            throw new RuntimeException("内置离线嵌入模型推理失败：" + e.getMessage(), e);
        }
    }

    /** 注意力掩码均值池化 */
    private static float[] meanPool(float[][] hidden, long[] mask) {
        int dim = hidden[0].length;
        float[] sum = new float[dim];
        float count = 0;
        for (int j = 0; j < hidden.length; j++) {
            if (mask[j] == 0) {
                continue;
            }
            count++;
            for (int d = 0; d < dim; d++) {
                sum[d] += hidden[j][d];
            }
        }
        for (int d = 0; d < dim; d++) {
            sum[d] /= count;
        }
        return sum;
    }

    /** L2 归一化（bge 检索用余弦相似度） */
    private static float[] normalize(float[] v) {
        double norm = 0;
        for (float f : v) {
            norm += f * f;
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-12) {
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
        return v;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    @Override
    public int dimension() {
        return 512;
    }
}
