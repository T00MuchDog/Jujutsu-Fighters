package com.jjktbf.graphics.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainMenuScreenSettingsTest {
    @Test
    void volumeTextIsClampedAndFallsBackWhenIncomplete() {
        assertEquals(0, MainMenuScreen.parseVolumePercent("-5", 50));
        assertEquals(64, MainMenuScreen.parseVolumePercent("64", 50));
        assertEquals(100, MainMenuScreen.parseVolumePercent("250", 50));
        assertEquals(50, MainMenuScreen.parseVolumePercent("", 50));
        assertEquals(50, MainMenuScreen.parseVolumePercent("not a number", 50));
    }

    @Test
    void windowsMenuShrinksInsteadOfClippingAtConstrainedHeights() {
        assertEquals(521.2f / 970.1f, MainMenuScreen.windowsMenuReferenceScale(
            720f, 1.2f / 1.75f, 970.1f), 0.001f);
        assertEquals(881.2f / 1083.9f, MainMenuScreen.windowsMenuReferenceScale(
            1080f, 1f, 1083.9f), 0.001f);
        assertEquals(1f, MainMenuScreen.windowsMenuReferenceScale(
            0f, 1f, 970.1f), 0.001f);
    }

    @Test
    void windowsMenuCentersBetweenTitleBarAndScreenBottom() {
        assertEquals(0f, MainMenuScreen.windowsMenuY(1080f, 951.2f), 0.001f);
        assertEquals(135.55f, MainMenuScreen.windowsMenuY(1440f, 1040.1f), 0.001f);
        assertEquals(0f, MainMenuScreen.windowsMenuY(720f, 591.2f), 0.001f);
    }

    @Test
    void windowsCommandViewportClearsSideControlsAndStaysCentered() {
        assertEquals(345.2f,
            MainMenuScreen.windowsCommandViewportHalfWidth(1280f, 1.2f / 1.75f),
            0.001f);
        assertEquals(567f,
            MainMenuScreen.windowsCommandViewportHalfWidth(2560f, 1f));
    }
}
