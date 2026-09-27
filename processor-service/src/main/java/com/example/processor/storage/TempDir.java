package com.example.processor.storage;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;

@Slf4j
public class TempDir implements AutoCloseable {

    @Getter
    private final Path path;

    public TempDir() throws IOException {
        this.path = Paths.get(System.getProperty("java.io.tmpdir"), "video_" + UUID.randomUUID());
        Files.createDirectories(this.path);
    }

    public Path resolve(String name) {
        return path.resolve(name);
    }

    public Path createSubdir(String name) throws IOException {
        Path sub = path.resolve(name);
        Files.createDirectories(sub);
        return sub;
    }

    @Override
    public void close() {
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Could not delete {}", p);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean temp dir {}: {}", path, e.getMessage());
        }
    }
}