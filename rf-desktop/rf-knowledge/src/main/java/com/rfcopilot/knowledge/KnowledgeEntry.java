package com.rfcopilot.knowledge;

/**
 * 知识库已收录文件条目（manifest.json 持久化，Jackson 序列化）。
 */
public class KnowledgeEntry {

    private String path;
    private String name;
    private int chunks;
    private String importedAt;

    public KnowledgeEntry() {
    }

    public KnowledgeEntry(String path, String name, int chunks, String importedAt) {
        this.path = path;
        this.name = name;
        this.chunks = chunks;
        this.importedAt = importedAt;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getChunks() {
        return chunks;
    }

    public void setChunks(int chunks) {
        this.chunks = chunks;
    }

    public String getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(String importedAt) {
        this.importedAt = importedAt;
    }
}
