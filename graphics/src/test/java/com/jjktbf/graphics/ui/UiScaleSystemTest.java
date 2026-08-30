package com.jjktbf.graphics.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiScaleSystemTest {
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
