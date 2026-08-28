package com.jjktbf.graphics.screens;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CharacterSelectScreenLayoutTest {

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
    void windowsRosterUsesTwentyPercentOfScreenWidth() {
        assertEquals(512f, CharacterSelectScreen.windowsRosterWidth(2560f), 0.001f);
        assertEquals(204.8f, CharacterSelectScreen.windowsRosterWidth(1024f), 0.001f);
    }

    @Test
    void windowsProfileReservesTechniqueRowsAtTargetHeights() {
        assertEquals(400f, CharacterSelectScreen.windowsTechniqueSectionHeight(1267f));
        assertEquals(15, CharacterSelectScreen.windowsTechniqueVisibleRows(1267f));
        assertEquals(297f, CharacterSelectScreen.windowsTechniqueSectionHeight(727f));
        assertEquals(11, CharacterSelectScreen.windowsTechniqueVisibleRows(727f));
    }

    @Test
    void macProfileReservesTechniqueDetailsWithoutCrushingSummary() {
        assertEquals(190f, CharacterSelectScreen.macTechniqueSectionHeight(644f));
        assertEquals(106f, CharacterSelectScreen.macTechniqueSectionHeight(378f));
        assertEquals(0f, CharacterSelectScreen.macTechniqueSectionHeight(350f));
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
}
