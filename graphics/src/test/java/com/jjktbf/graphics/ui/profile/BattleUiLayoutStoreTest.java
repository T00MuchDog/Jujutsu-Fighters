package com.jjktbf.graphics.ui.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjktbf.graphics.ui.UiScaleSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleUiLayoutStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void bundledSharedLayoutLoadsAndValidates() throws Exception {
        BattleUiLayoutStore store = new BattleUiLayoutStore(
            null, getClass().getClassLoader());

        assertNotNull(store.load());
    }

    @Test
    void defaultsUseSharedReferenceGeometry() {
        BattleUiLayout layout = BattleUiLayout.defaults();

        assertEquals((int) UiScaleSystem.GAMEPLAY_REFERENCE_WIDTH, layout.referenceWidth);
        assertEquals((int) UiScaleSystem.GAMEPLAY_REFERENCE_HEIGHT, layout.referenceHeight);
        layout.validate();
    }

    @Test
    void sharedTextGeometryInvariantIsValidated() {
        BattleUiLayout layout = BattleUiLayout.defaults();
        layout.planner.textGeometryScale = 1f;

        assertThrows(IllegalArgumentException.class,
            layout::validate);
    }

    @Test
    void omittedSurvivingFieldsUseCanonicalDefaultsViaJson() throws Exception {
        BattleUiLayoutStore store = new BattleUiLayoutStore(
            temporaryDirectory, getClass().getClassLoader());
        Path sourceDirectory = temporaryDirectory.resolve(
            "graphics/src/main/resources/assets/ui/battle-layouts");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("shared.json"), """
            {
              "schemaVersion": 1,
              "referenceWidth": 2560,
              "referenceHeight": 1440,
              "execution": { "hudScale": 1.82 },
              "planner": {}
            }
            """);

        BattleUiLayout layout = store.load();

        assertEquals(1.82f, layout.execution.hudScale, 0.0001f);
        assertEquals(0.035f, layout.execution.outerMarginFraction, 0.0001f);
        assertEquals(UiScaleSystem.GAMEPLAY_TEXT_SCALE,
            layout.planner.textGeometryScale, 0.0001f);
    }

    @Test
    void sourceOverrideLoadsSharedLayout() throws Exception {
        BattleUiLayoutStore store = new BattleUiLayoutStore(
            temporaryDirectory, getClass().getClassLoader());
        BattleUiLayout layout = BattleUiLayout.defaults();
        layout.execution.hudScale = 1.82f;
        Path sourceDirectory = temporaryDirectory.resolve(
            "graphics/src/main/resources/assets/ui/battle-layouts");
        Files.createDirectories(sourceDirectory);
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(sourceDirectory.resolve("shared.json").toFile(), layout);

        assertEquals(1.82f, store.load().execution.hudScale, 0.0001f);
    }

    @Test
    void publicStoreFindsPinnedAuthoringCheckout() throws Exception {
        Path moves = temporaryDirectory.resolve("data/moves/all_moves.json");
        Path characters = temporaryDirectory.resolve("data/characters/all_characters.json");
        Files.createDirectories(moves.getParent());
        Files.createDirectories(characters.getParent());
        Files.writeString(moves, "{}");
        Files.writeString(characters, "{}");

        BattleUiLayout layout = BattleUiLayout.defaults();
        layout.execution.hudScale = 1.63f;
        Path sourceDirectory = temporaryDirectory.resolve(
            "graphics/src/main/resources/assets/ui/battle-layouts");
        Files.createDirectories(sourceDirectory);
        new ObjectMapper().writeValue(sourceDirectory.resolve("shared.json").toFile(), layout);

        String previousAuthoring = System.getProperty("jjktbf.authoring");
        String previousRoot = System.getProperty("jjktbf.authoring.root");
        try {
            System.setProperty("jjktbf.authoring", "true");
            System.setProperty("jjktbf.authoring.root", temporaryDirectory.toString());

            assertEquals(1.63f,
                new BattleUiLayoutStore().load().execution.hudScale,
                0.0001f);
        } finally {
            restoreProperty("jjktbf.authoring", previousAuthoring);
            restoreProperty("jjktbf.authoring.root", previousRoot);
        }
    }

    @Test
    void invalidSourceSharedLayoutIsRejected() throws Exception {
        BattleUiLayoutStore store = new BattleUiLayoutStore(
            temporaryDirectory, getClass().getClassLoader());
        BattleUiLayout layout = BattleUiLayout.defaults();
        layout.execution.textGeometryScale = 1f;
        Path sourceDirectory = temporaryDirectory.resolve(
            "graphics/src/main/resources/assets/ui/battle-layouts");
        Files.createDirectories(sourceDirectory);
        new ObjectMapper().writeValue(sourceDirectory.resolve("shared.json").toFile(), layout);

        assertThrows(IllegalArgumentException.class,
            store::load);
    }

    @Test
    void copyIsDeepEnoughForIndependentLiveDrafts() {
        BattleUiLayout original = BattleUiLayout.defaults();
        BattleUiLayout copy = original.copy();
        float originalHudScale = original.execution.hudScale;
        float originalTextScale = original.planner.textGeometryScale;
        copy.execution.hudScale = 2f;
        copy.planner.textGeometryScale = 2f;

        assertNotSame(original.execution, copy.execution);
        assertNotSame(original.planner, copy.planner);
        assertEquals(originalHudScale, original.execution.hudScale, 0.0001f);
        assertEquals(originalTextScale, original.planner.textGeometryScale, 0.0001f);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }
}
