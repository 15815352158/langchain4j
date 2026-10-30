package com.rfcopilot.core.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简化版 BERT 中文 WordPiece 分词器（配合 bge-small-zh-v1.5 使用）。
 * 覆盖中文场景常用规则：全角转半角、小写化、中日韩字符逐字切分、标点独立、贪心 WordPiece。
 */
public class BertWordPieceTokenizer {

    private static final int MAX_PIECE_LEN = 100;

    private final Map<String, Integer> vocab = new HashMap<>();
    private final int clsId;
    private final int sepId;
    private final int padId;
    private final int unkId;

    private BertWordPieceTokenizer(Map<String, Integer> vocab) {
        this.vocab.putAll(vocab);
        this.clsId = vocab.getOrDefault("[CLS]", 101);
        this.sepId = vocab.getOrDefault("[SEP]", 102);
        this.padId = vocab.getOrDefault("[PAD]", 0);
        this.unkId = vocab.getOrDefault("[UNK]", 100);
    }

    /** 从 vocab.txt 加载词表 */
    public static BertWordPieceTokenizer load(InputStream in) throws IOException {
        Map<String, Integer> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int idx = 0;
            while ((line = reader.readLine()) != null) {
                map.put(line.strip(), idx++);
            }
        }
        return new BertWordPieceTokenizer(map);
    }

    /**
     * 编码单句：[CLS] pieces [SEP]，超长截断。
     *
     * @return input_ids 数组
     */
    public List<Integer> encode(String text, int maxSeqLen) {
        List<Integer> ids = new ArrayList<>();
        ids.add(clsId);
        for (String word : basicTokenize(text)) {
            for (String piece : wordPiece(word)) {
                if (ids.size() >= maxSeqLen - 1) {
                    ids.add(sepId);
                    return ids;
                }
                ids.add(vocab.getOrDefault(piece, unkId));
            }
        }
        ids.add(sepId);
        return ids;
    }

    /** 基础切分：规范化 + 按 CJK/标点/空白切词 */
    private List<String> basicTokenize(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == 0 || c == '\ufffd') {
                continue;
            }
            if (Character.isWhitespace(c)) {
                sb.append(' ');
            } else if (c >= 0xFF01 && c <= 0xFF5E) {
                // 全角转半角
                sb.append((char) (c - 0xFEE0));
            } else if (c == 0x3000) {
                sb.append(' ');
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        String normalized = sb.toString();

        List<String> words = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (isCJK(c) || isPunctuation(c)) {
                flush(cur, words);
                words.add(String.valueOf(c));
            } else if (Character.isWhitespace(c)) {
                flush(cur, words);
            } else {
                cur.append(c);
            }
        }
        flush(cur, words);
        return words;
    }

    private static void flush(StringBuilder cur, List<String> words) {
        if (cur.length() > 0) {
            words.add(cur.toString());
            cur.setLength(0);
        }
    }

    /** 贪心最长匹配 WordPiece */
    private List<String> wordPiece(String word) {
        List<String> pieces = new ArrayList<>();
        if (word.length() > 100) {
            for (int i = 0; i < word.length(); i += 100) {
                pieces.add("[UNK]");
            }
            return pieces;
        }
        int start = 0;
        while (start < word.length()) {
            int end = Math.min(word.length(), start + MAX_PIECE_LEN);
            String matched = null;
            while (start < end) {
                String sub = (start > 0 ? "##" : "") + word.substring(start, end);
                if (vocab.containsKey(sub)) {
                    matched = sub;
                    break;
                }
                end--;
            }
            if (matched == null) {
                pieces.add("[UNK]");
                start++;
            } else {
                pieces.add(matched);
                start = end;
            }
        }
        return pieces;
    }

    private static boolean isCJK(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)
                || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0x3040 && c <= 0x30FF);
    }

    private static boolean isPunctuation(char c) {
        int t = Character.getType(c);
        if (t == Character.CONNECTOR_PUNCTUATION || t == Character.DASH_PUNCTUATION
                || t == Character.END_PUNCTUATION || t == Character.FINAL_QUOTE_PUNCTUATION
                || t == Character.INITIAL_QUOTE_PUNCTUATION || t == Character.OTHER_PUNCTUATION
                || t == Character.START_PUNCTUATION) {
            return true;
        }
        return (c >= 0x3000 && c <= 0x303F) || (c >= 0xFF00 && c <= 0xFFEF);
    }
}
