package com.jjktbf.graphics.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowsResolutionTest {
    @Test
    void exposesOnlyTheSupportedModesInAscendingOrder() {
        assertArrayEquals(new WindowsResolution[] {
            WindowsResolution.HD_1366_768,
            WindowsResolution.FULL_HD_1920_1080,
            WindowsResolution.QHD_2560_1440
        }, WindowsResolution.values());
        assertEquals("1366 x 768", WindowsResolution.HD_1366_768.toString());
        assertEquals("1920x1080", WindowsResolution.FULL_HD_1920_1080.id());
    }

    @Test
    void selectsTheLargestModeThatFitsTheDetectedDisplay() {
        assertEquals(WindowsResolution.HD_1366_768,
            WindowsResolution.bestFor(1366, 768));
        assertEquals(WindowsResolution.HD_1366_768,
            WindowsResolution.bestFor(1600, 900));
        assertEquals(WindowsResolution.FULL_HD_1920_1080,
            WindowsResolution.bestFor(2048, 1152));
        assertEquals(WindowsResolution.QHD_2560_1440,
            WindowsResolution.bestFor(3840, 2160));
    }

    @Test
    void persistedIdsAreStableAndStrict() {
        assertEquals(WindowsResolution.QHD_2560_1440,
            WindowsResolution.parseId("2560x1440"));
        assertThrows(IllegalArgumentException.class,
            () -> WindowsResolution.parseId("1600x900"));
    }
}
