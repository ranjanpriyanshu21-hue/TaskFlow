package com.taskflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path root;

    public FileStorageService(@Value("${app.upload-dir}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /** Saves the upload under a random name and returns that name. */
    public String store(MultipartFile file) {
        String stored = UUID.randomUUID().toString();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(stored));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return stored;
    }

    public Resource load(String storedName) {
        return new FileSystemResource(root.resolve(storedName));
    }

    public void delete(String storedName) {
        try {
            Files.deleteIfExists(root.resolve(storedName));
        } catch (IOException ignored) {
            // best effort; an orphaned file is harmless
        }
    }
}
