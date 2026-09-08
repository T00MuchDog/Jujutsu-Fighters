package com.jjktbf.graphics.ui;

import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiScaleSystemTest {
    @Test
    void smallViewportsIncreaseBodyTextWithoutChangingReferenceTypography() {
        assertEquals(1f, UiScaleSystem.bodyTextScale(1f, 10.25f));
        assertEquals(1.6f, UiScaleSystem.bodyTextScale(768f / 1440f, 10.25f));
        float macScale = 1512f / 2560f;
        assertEquals(9f, macScale * 10.25f * UiScaleSystem.bodyTextScale(macScale, 10.25f), 0.001f);
    }

    @Test
    void gameplayViewportUsesExpandableSharedMinimum() {
        Viewport viewport = UiScaleSystem.newGameplayViewport();
        ExtendViewport extendViewport = assertInstanceOf(ExtendViewport.class, viewport);

        assertEquals(2560f, extendViewport.getMinWorldWidth(), 0.000001f);
        assertEquals(1440f, extendViewport.getMinWorldHeight(), 0.000001f);
    }

    @Test
    void gameplayMetricsUniformlyScaleAndExpandAcrossResolutions() {
        int[][] resolutions = {
            {1512, 982},
            {1366, 768},
            {1920, 1080},
            {2560, 1440},
            {2560, 1600},
            {3440, 1440}
        };

        for (int[] resolution : resolutions) {
            float width = resolution[0];
            float height = resolution[1];
            UiScaleSystem.GameplayMetrics metrics =
                UiScaleSystem.gameplayMetrics(width, height);
            float expectedScale = Math.min(width / 2560f, height / 1440f);

            assertEquals(expectedScale, metrics.scale(), 0.000001f);
            assertEquals(width / metrics.scale(), metrics.worldWidth(), 0.000001f);
            assertEquals(height / metrics.scale(), metrics.worldHeight(), 0.000001f);
            assertTrue(metrics.worldWidth() >= UiScaleSystem.GAMEPLAY_REFERENCE_WIDTH - 0.001f);
            assertTrue(metrics.worldHeight() >= UiScaleSystem.GAMEPLAY_REFERENCE_HEIGHT - 0.001f);
        }
    }

    @Test
    void referenceResolutionIsOneToOne() {
        UiScaleSystem.Fit fit = UiScaleSystem.fitWindows(2560f, 1440f);

        assertEquals(1f, fit.scale(), 0.000001f);
        assertEquals(0f, fit.offsetX(), 0.000001f);
        assertEquals(0f, fit.offsetY(), 0.000001f);
        assertEquals(2560f, fit.width(), 0.000001f);
        assertEquals(1440f, fit.height(), 0.000001f);
    }

    @Test
    void fullHdUsesUniformThreeQuarterScale() {
        UiScaleSystem.Fit fit = UiScaleSystem.fitWindows(1920f, 1080f);

        assertEquals(0.75f, fit.scale(), 0.000001f);
        assertEquals(0f, fit.offsetX(), 0.000001f);
        assertEquals(0f, fit.offsetY(), 0.000001f);
    }

    @Test
    void extraPixelAt1366IsCenteredWithoutHorizontalStretching() {
        UiScaleSystem.Fit fit = UiScaleSystem.fitWindows(1366f, 768f);

        assertEquals(768f / 1440f, fit.scale(), 0.000001f);
        assertEquals((1366f - 2560f * fit.scale()) * 0.5f,
            fit.offsetX(), 0.000001f);
        assertEquals(0f, fit.offsetY(), 0.000001f);
        assertEquals(fit.scale(), fit.width() / 2560f, 0.000001f);
        assertEquals(fit.scale(), fit.height() / 1440f, 0.000001f);
    }
}
