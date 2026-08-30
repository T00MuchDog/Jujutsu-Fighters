package com.jjktbf.graphics.display;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplaySettingsStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyRoundTripsAResolution() throws Exception {
        Path file = temporaryDirectory.resolve("settings/display.json");
        DisplaySettingsStore store = new DisplaySettingsStore(file);

        assertTrue(store.load().isEmpty());
        store.save(WindowsResolution.FULL_HD_1920_1080);

        assertEquals(WindowsResolution.FULL_HD_1920_1080,
            store.load().orElseThrow());
        assertTrue(Files.readString(file).contains("1920x1080"));
    }

    @Test
    void rejectsMalformedAndUnsupportedSettings() throws Exception {
        Path file = temporaryDirectory.resolve("display.json");
        DisplaySettingsStore store = new DisplaySettingsStore(file);
        Files.writeString(file, "{\"resolution\":\"1600x900\"}");

        assertThrows(IOException.class, store::load);

        Files.writeString(file, "not json");
        assertThrows(IOException.class, store::load);
    }
}
