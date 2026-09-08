package com.jjktbf.graphics.ui.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjktbf.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the single shared battle layout from source authoring files or bundled resources. */
public final class BattleUiLayoutStore {

    private static final String RESOURCE_DIRECTORY = "assets/ui/battle-layouts";
    private static final String SOURCE_DIRECTORY =
        "graphics/src/main/resources/" + RESOURCE_DIRECTORY;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ClassLoader classLoader;
    private final Path sourceRoot;

    public BattleUiLayoutStore() {
        this(resolveAuthoringSourceRoot(), contextClassLoader());
    }

    BattleUiLayoutStore(Path sourceRoot, ClassLoader classLoader) {
        this.sourceRoot = sourceRoot;
        this.classLoader = classLoader;
    }

    public BattleUiLayout load() throws IOException {
        Path source = sourceRoot == null ? null
            : sourceRoot.resolve(SOURCE_DIRECTORY).resolve("shared.json");
        BattleUiLayout layout;
        if (source != null && Files.isRegularFile(source)) {
            layout = mapper.readValue(source.toFile(), BattleUiLayout.class);
        } else {
            String resource = RESOURCE_DIRECTORY + "/shared.json";
            try (InputStream input = classLoader.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IOException("Missing bundled battle UI layout " + resource);
                }
                layout = mapper.readValue(input, BattleUiLayout.class);
            }
        }
        layout.validate();
        return layout;
    }

    private static Path resolveAuthoringSourceRoot() {
        if (!AppPaths.isAuthoringMode()) return null;
        Path data = AppPaths.authoringDataDir();
        if (data == null) return null;
        Path root = data.getParent();
        return Files.isDirectory(root.resolve(SOURCE_DIRECTORY)) ? root : null;
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? BattleUiLayoutStore.class.getClassLoader() : loader;
    }
}
