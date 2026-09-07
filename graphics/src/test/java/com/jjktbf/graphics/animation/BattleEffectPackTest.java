package com.jjktbf.graphics.animation;

import com.badlogic.gdx.files.FileHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleEffectPackTest {
    @TempDir
    Path root;

    @Test
    void readsTimingBindingsFallbackRolesAndImpactSlices() throws IOException {
        Files.writeString(root.resolve("strike.png"), "not decoded until prepare");
        Files.writeString(root.resolve("event.png"), "not decoded until prepare");
        writeManifest("""
                {
                  "schemaVersion": 1,
                  "frameWidth": 10,
                  "frameHeight": 20,
                  "columns": 2,
                  "sheetOrder": "row-major-top-left",
                  "authoringResolution": 96,
                  "effects": [
                    {"id":"strike", "sheet":"strike.png", "frameCount":10,
                     "frameDurationMs":50, "loop":false, "anchor":[0.25,0.5],
                     "placement":"target", "moveIds":["move-a"],
                     "impactFrames":[2,7]},
                    {"id":"event", "sheet":"event.png", "frameCount":4,
                     "frameDurationMs":125, "loop":true, "anchor":[0.5,1],
                     "placement":"source", "eventTypes":["EVENT_A"]}
                  ]
                }
                """);

        BattleEffectPack pack = new BattleEffectPack(new FileHandle(root.toFile()));
        BattleEffectPack.Effect strike = pack.effectForMove("move-a");
        assertEquals(0.5f, strike.durationSeconds(), 0.0001f);
        assertEquals("attack", strike.role());
        assertEquals("utility", pack.effectForEvent("EVENT_A").role());

        BattleEffectPack.Clip first = pack.clip(strike, null);
        assertEquals(0, first.firstFrame());
        assertEquals(4, first.endFrame());
        assertEquals(2, first.impactFrame());
        assertEquals(0.1f, first.impactSeconds(), 0.0001f);

        BattleEffectPack.Clip second = pack.clip(strike, 1);
        assertEquals(4, second.firstFrame());
        assertEquals(10, second.endFrame());
        assertEquals(7, second.impactFrame());
        assertEquals(0.15f, second.impactSeconds(), 0.0001f);
        assertEquals(second, pack.clip(strike, 99));
        assertEquals(0.5f, pack.fullClip(pack.effect("event")).durationSeconds(), 0.0001f);
    }

    @Test
    void rejectsBadMetadataAndUnsafeExternalPaths() throws IOException {
        Files.writeString(root.resolve("sheet.png"), "placeholder");
        writeManifest(baseManifest("../sheet.png"));
        assertThrows(IllegalArgumentException.class,
                () -> new BattleEffectPack(new FileHandle(root.toFile())));
    }

    @Test
    void rejectsDuplicateBindings() throws IOException {
        Files.writeString(root.resolve("a.png"), "placeholder");
        Files.writeString(root.resolve("b.png"), "placeholder");
        writeManifest("""
                {
                  "schemaVersion": 1, "frameWidth": 10, "frameHeight": 10, "columns": 1,
                  "sheetOrder": "row-major-top-left", "effects": [
                    {"id":"a", "sheet":"a.png", "frameCount":3, "frameDurationMs":50,
                     "loop":false, "anchor":[0,0], "placement":"target",
                     "moveIds":["same"], "impactFrames":[0,2]},
                    {"id":"b", "sheet":"b.png", "frameCount":3, "frameDurationMs":50,
                     "loop":false, "anchor":[0,0], "placement":"source",
                     "moveIds":["same"]}
                  ]
                }
                """);
        assertThrows(IllegalArgumentException.class,
                () -> new BattleEffectPack(new FileHandle(root.toFile())));
    }

    @Test
    void acceptsSourceToTargetPrimaryPlacements() throws IOException {
        Files.writeString(root.resolve("beam.png"), "placeholder");
        Files.writeString(root.resolve("orb.png"), "placeholder");
        writeManifest("""
                {"schemaVersion":1,"frameWidth":10,"frameHeight":10,"columns":1,
                 "sheetOrder":"row-major-top-left","effects":[
                   {"id":"beam","sheet":"beam.png","frameCount":1,"frameDurationMs":50,
                    "loop":false,"anchor":[0.5,0.5],"placement":"beam"},
                   {"id":"orb","sheet":"orb.png","frameCount":1,"frameDurationMs":50,
                    "loop":false,"anchor":[0.5,0.5],"placement":"projectile"}]}
                """);
        BattleEffectPack pack = new BattleEffectPack(new FileHandle(root.toFile()));
        assertEquals("beam", pack.effect("beam").placement());
        assertEquals("projectile", pack.effect("orb").placement());
    }

    @Test
    void rejectsOutOfRangeContacts() throws IOException {
        Files.writeString(root.resolve("sheet.png"), "placeholder");
        writeManifest("""
                {
                  "schemaVersion": 1, "frameWidth": 10, "frameHeight": 10, "columns": 1,
                  "sheetOrder": "row-major-top-left", "effects": [{
                    "id":"effect", "sheet":"sheet.png", "frameCount":3, "frameDurationMs":50,
                    "loop":false, "anchor":[0.5,0.5], "placement":"target",
                    "impactFrames":[0,3]
                  }]
                }
                """);
        assertThrows(IllegalArgumentException.class,
                () -> new BattleEffectPack(new FileHandle(root.toFile())));
    }

    @Test
    void rejectsSymlinkedSheetEscapingExternalRoot() throws IOException {
        Path outside = Files.createTempFile("effect-pack-outside", ".png");
        Path link = root.resolve("link.png");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unsupported) {
            return;
        }
        writeManifest(baseManifest("link.png"));
        assertThrows(IllegalArgumentException.class,
                () -> new BattleEffectPack(new FileHandle(root.toFile())));
        Files.deleteIfExists(outside);
    }

    private void writeManifest(String manifest) throws IOException {
        Files.writeString(root.resolve("manifest.json"), manifest);
    }

    private static String baseManifest(String sheet) {
        return """
                {
                  "schemaVersion": 1, "frameWidth": 10, "frameHeight": 10, "columns": 1,
                  "sheetOrder": "row-major-top-left", "effects": [{
                    "id":"effect", "sheet":"%s", "frameCount":2, "frameDurationMs":50,
                    "loop":false, "anchor":[0.5,0.5], "placement":"target"
                  }]
                }
                """.formatted(sheet);
    }
}
