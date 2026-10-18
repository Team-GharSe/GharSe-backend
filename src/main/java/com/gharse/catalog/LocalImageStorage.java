package com.gharse.catalog;

import com.gharse.common.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class LocalImageStorage implements ImageStorage {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final Path root = Path.of("uploads", "products");

    @Override
    public String save(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("The photo is empty.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Each photo must be 5 MB or smaller.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(type);
        if (extension == null) {
            throw new BadRequestException("Photos must be JPEG, PNG or WebP.");
        }
        try {
            Files.createDirectories(root);
            String name = UUID.randomUUID() + extension;
            Files.copy(file.getInputStream(), root.resolve(name));
            return "/uploads/products/" + name;
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the photo.", e);
        }
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith("/uploads/products/")) {
            return;
        }
        String name = url.substring("/uploads/products/".length());
        if (name.contains("/") || name.contains("..")) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(name));
        } catch (IOException ignored) {
            // A leftover file on disk does no harm; the database row is the source of truth.
        }
    }
}