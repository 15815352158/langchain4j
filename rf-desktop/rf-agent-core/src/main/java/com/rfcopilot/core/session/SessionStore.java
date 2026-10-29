package com.rfcopilot.core.session;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rfcopilot.core.RfPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话持久化：每个会话一个 JSON 文件，存于 ~/.rfcopilot/sessions/
 */
public class SessionStore {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<ChatSession> listSessions() {
        Path dir = RfPaths.sessionsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.toString().endsWith(".json"))
                    .map(this::read)
                    .sorted(Comparator.comparing(ChatSession::getUpdatedAt).reversed())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ChatSession load(String sessionId) {
        Path file = file(sessionId);
        if (!Files.exists(file)) {
            return null;
        }
        return read(file);
    }

    public void save(ChatSession session) {
        try {
            RfPaths.ensureDirs();
            session.touch();
            Files.write(file(session.getId()),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(session));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ChatSession read(Path file) {
        try {
            return mapper.readValue(Files.readAllBytes(file), ChatSession.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file(String sessionId) {
        return RfPaths.sessionsDir().resolve(sessionId + ".json");
    }
}
