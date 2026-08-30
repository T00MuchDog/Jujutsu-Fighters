package com.jjktbf.graphics.display;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jjktbf.AppPaths;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Startup-readable persistence for display settings. */
public final class DisplaySettingsStore {
    public static final String RELATIVE_PATH = "settings/display.json";

    private final Path file;
    private final ObjectMapper mapper;

    public DisplaySettingsStore() {
        this(AppPaths.root().resolve(RELATIVE_PATH));
    }

    public DisplaySettingsStore(Path file) {
        this.file = file.toAbsolutePath();
        this.mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Optional<WindowsResolution> load() throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        try {
            StoredSettings settings = mapper.readValue(
                Files.readAllBytes(file), StoredSettings.class);
            return Optional.of(WindowsResolution.parseId(settings.resolution()));
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new IOException("Stored display settings are malformed", failure);
        }
    }

    public void save(WindowsResolution resolution) throws IOException {
        if (resolution == null) throw new IllegalArgumentException("resolution must not be null");
        Path directory = file.getParent();
        if (directory == null) throw new IOException("Display settings path has no parent");
        Files.createDirectories(directory);

        Path temporary = Files.createTempFile(directory, ".display-", ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, mapper.writeValueAsBytes(new StoredSettings(resolution.id())));
            try {
                Files.move(temporary, file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException failure) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    public record StoredSettings(String resolution) {
    }
}
