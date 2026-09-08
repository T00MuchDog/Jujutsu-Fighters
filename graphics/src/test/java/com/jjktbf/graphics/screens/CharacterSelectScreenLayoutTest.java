package com.jjktbf.graphics.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jjktbf.graphics.ui.UiScaleSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterSelectScreenLayoutTest {
    private final Graphics previousGraphics = Gdx.graphics;
    private final GL20 previousGl = Gdx.gl;

    @AfterEach
    void restoreGraphics() {
        Gdx.graphics = previousGraphics;
        Gdx.gl = previousGl;
    }

    @Test
    void rosterScrollRevealsSelectionUsingExplicitRows() {
        float rowHeight = 66f;
        float viewportHeight = 470f;

        assertEquals(0f, CharacterSelectScreen.rosterScrollOffsetForSelection(
            0f, 0, 12, rowHeight, viewportHeight));
        assertEquals(58f, CharacterSelectScreen.rosterScrollOffsetForSelection(
            0f, 7, 12, rowHeight, viewportHeight));
        assertEquals(322f, CharacterSelectScreen.rosterScrollOffsetForSelection(
            58f, 11, 12, rowHeight, viewportHeight));
        assertEquals(0f, CharacterSelectScreen.rosterScrollOffsetForSelection(
            322f, 0, 12, rowHeight, viewportHeight));
    }

    @Test
    void referenceSizePreservesCanonicalPanelAndProfileGeometry() {
        Bounds bounds = new Bounds(2560f, 1440f);

        assertEquals(new Rectangle(36f, 1317f, 2488f, 87f), bounds.header);
        assertEquals(new Rectangle(36f, 36f, 512f, 1267f), bounds.roster);
        assertEquals(new Rectangle(562f, 36f, 1962f, 1267f), bounds.detail);
        assertEquals(new Rectangle(586f, 639f, 1914f, 580f), bounds.summary);
        assertEquals(new Rectangle(586f, 274f, 1914f, 349f), bounds.technique);
        assertEquals(new Rectangle(586f, 60f, 1914f, 198f), bounds.moves);
        assertEquals(new Rectangle(601f, 75f, 1884f, 81f), bounds.moveViewport);
    }

    @Test
    void teamTrayReservesSpaceForOpenAndClosedLearnedMoveToggle() {
        Bounds bounds = new Bounds(2560f, 1440f);
        for (float toggleX : new float[] {2282f, 1906f}) {
            Rectangle toggle = new Rectangle(toggleX, bounds.header.y + 39f, 278f, 48f);
            Rectangle tray = CharacterSelectScreen.teamTrayBounds(bounds.header, toggle);
            assertInside(bounds.header, tray);
            assertFalse(tray.overlaps(toggle));
            assertTrue((tray.width - 14f) / 3f >= 220f);
        }
    }

    @ParameterizedTest
    @CsvSource({"1366,768", "1920,1080", "2560,1440", "1512,982", "2560,1600", "3440,1440"})
    void supportedDimensionsFillViewportAndKeepAllSectionsUsable(int width, int height) {
        Viewport viewport = viewport(width, height);
        Bounds bounds = new Bounds(viewport.getWorldWidth(), viewport.getWorldHeight());
        Rectangle world = new Rectangle(0f, 0f, viewport.getWorldWidth(), viewport.getWorldHeight());

        assertEquals(0, viewport.getScreenX());
        assertEquals(0, viewport.getScreenY());
        assertEquals(width, viewport.getScreenWidth());
        assertEquals(height, viewport.getScreenHeight());
        assertEquals(width / viewport.getWorldWidth(), height / viewport.getWorldHeight(), 0.001f);
        assertInside(world, bounds.header);
        assertInside(world, bounds.roster);
        assertInside(world, bounds.detail);
        assertInside(bounds.roster, bounds.rosterViewport);
        assertFalse(bounds.header.overlaps(bounds.roster));
        assertFalse(bounds.header.overlaps(bounds.detail));
        assertFalse(bounds.roster.overlaps(bounds.detail));
        assertEquals(viewport.getWorldWidth() * 0.2f, bounds.roster.width, 0.001f);
        assertInside(bounds.detail, bounds.summary);
        assertInside(bounds.detail, bounds.technique);
        assertInside(bounds.detail, bounds.moves);
        assertFalse(bounds.summary.overlaps(bounds.technique));
        assertFalse(bounds.technique.overlaps(bounds.moves));
        assertEquals(580f, bounds.summary.height, 0.001f);
        assertTrue(bounds.technique.height >= 349f - 0.001f);
        assertEquals(198f, bounds.moves.height, 0.001f);
        assertInside(bounds.moves, bounds.moveViewport);
        assertEquals(81f, bounds.moveViewport.height, 0.001f);
        for (Rectangle action : new Rectangle[] {bounds.recommended, bounds.customize, bounds.randomize}) {
            assertInside(bounds.moves, action);
            assertFalse(action.overlaps(bounds.moveViewport));
            assertEquals(39f, action.height);
            assertTrue(action.width >= 559f);
        }
        assertFalse(bounds.recommended.overlaps(bounds.customize));
        assertFalse(bounds.customize.overlaps(bounds.randomize));
    }

    @ParameterizedTest
    @CsvSource({"1366,768", "1920,1080", "2560,1440", "1512,982", "2560,1600"})
    void projectedPointerSelectsTheSameRosterRowsBeforeAndAfterScrolling(int width, int height) {
        Viewport viewport = viewport(width, height);
        Bounds bounds = new Bounds(viewport.getWorldWidth(), viewport.getWorldHeight());
        Rectangle roster = bounds.rosterViewport;
        int rowCount = 40;

        for (int index : new int[] {0, 1, rowCount - 1}) {
            float scroll = CharacterSelectScreen.rosterScrollOffsetForSelection(
                0f, index, rowCount, 66f, roster.height);
            Vector2 pointer = new Vector2(roster.x + roster.width / 2f,
                roster.y + roster.height - (index + 0.5f) * 66f + scroll);
            assertTrue(roster.contains(pointer));
            viewport.project(pointer);
            pointer.y = height - pointer.y;
            viewport.unproject(pointer);

            assertEquals(index, CharacterSelectScreen.rosterRowAt(
                roster, scroll, rowCount, pointer.x, pointer.y));
        }
        assertEquals(-1, CharacterSelectScreen.rosterRowAt(
            roster, 0f, rowCount, roster.x, roster.y + roster.height + 1f));
        assertEquals(-1, CharacterSelectScreen.rosterRowAt(
            roster, 0f, rowCount, roster.x - 1f, roster.y));
    }

    @ParameterizedTest
    @CsvSource({"1512,982", "2560,1600", "3440,1440"})
    void extraLogicalSpaceExpandsContentInsteadOfStretchingControls(int width, int height) {
        Viewport viewport = viewport(width, height);
        Bounds reference = new Bounds(2560f, 1440f);
        Bounds expanded = new Bounds(viewport.getWorldWidth(), viewport.getWorldHeight());
        float extraWidth = viewport.getWorldWidth() - 2560f;
        float extraHeight = viewport.getWorldHeight() - 1440f;

        assertEquals(reference.header.height, expanded.header.height);
        assertEquals(reference.summary.height, expanded.summary.height);
        assertEquals(reference.moves.height, expanded.moves.height);
        assertEquals(reference.technique.height + extraHeight, expanded.technique.height, 0.001f);
        assertEquals(reference.rosterViewport.height + extraHeight, expanded.rosterViewport.height, 0.001f);
        assertEquals(reference.detail.width + extraWidth * 0.8f, expanded.detail.width, 0.001f);
        assertEquals(reference.moveViewport.width + extraWidth * 0.8f, expanded.moveViewport.width, 0.001f);
    }

    @Test
    void statBarsUseTenEightyAndThreeHundredColorStops() {
        assertEquals(new Color(0.920f, 0.220f, 0.180f, 1f),
            CharacterSelectScreen.statBarColor(10, new Color()));
        assertEquals(Color.YELLOW,
            CharacterSelectScreen.statBarColor(80, new Color()));
        assertEquals(new Color(0.260f, 0.820f, 0.360f, 1f),
            CharacterSelectScreen.statBarColor(300, new Color()));
        assertEquals(0f, CharacterSelectScreen.statBarFillRatio(10));
        assertEquals(1f, CharacterSelectScreen.statBarFillRatio(300));
    }

    private Viewport viewport(int width, int height) {
        GdxNativesLoader.load();
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
            Graphics.class.getClassLoader(), new Class<?>[] {Graphics.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getWidth", "getBackBufferWidth" -> width;
                case "getHeight", "getBackBufferHeight" -> height;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        Gdx.gl = (GL20) Proxy.newProxyInstance(
            GL20.class.getClassLoader(), new Class<?>[] {GL20.class},
            (proxy, method, arguments) -> {
                if (method.getName().equals("glViewport")) return null;
                throw new UnsupportedOperationException(method.getName());
            });
        Viewport viewport = UiScaleSystem.newGameplayViewport();
        viewport.update(width, height, true);
        return viewport;
    }

    private static void assertInside(Rectangle parent, Rectangle child) {
        assertTrue(child.width > 0f && child.height > 0f, () -> "Empty bounds: " + child);
        assertTrue(child.x >= parent.x - 0.001f && child.y >= parent.y - 0.001f
            && child.x + child.width <= parent.x + parent.width + 0.001f
            && child.y + child.height <= parent.y + parent.height + 0.001f,
            () -> child + " outside " + parent);
    }

    private static final class Bounds {
        final Rectangle header = new Rectangle();
        final Rectangle roster = new Rectangle();
        final Rectangle rosterViewport = new Rectangle();
        final Rectangle detail = new Rectangle();
        final Rectangle summary = new Rectangle();
        final Rectangle technique = new Rectangle();
        final Rectangle moves = new Rectangle();
        final Rectangle moveViewport = new Rectangle();
        final Rectangle recommended = new Rectangle();
        final Rectangle customize = new Rectangle();
        final Rectangle randomize = new Rectangle();

        Bounds(float width, float height) {
            CharacterSelectScreen.layoutPanelBounds(width, height, header, roster, rosterViewport, detail);
            CharacterSelectScreen.layoutProfileBounds(detail, summary, technique, moves);
            CharacterSelectScreen.layoutMoveSetBounds(moves, moveViewport, recommended, customize, randomize);
        }
    }
}
