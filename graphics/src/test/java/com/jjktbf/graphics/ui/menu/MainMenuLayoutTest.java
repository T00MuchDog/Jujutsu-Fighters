package com.jjktbf.graphics.ui.menu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainMenuLayoutTest {
    @ParameterizedTest
    @CsvSource({
        "2560, 1440, false, 2, 2, 258",
        "2560, 1440, false, 3, 3, 258",
        "1366, 768, false, 2, 2, 258",
        "1366, 768, false, 3, 3, 258",
        "1024, 600, false, 3, 1, 100",
        "1512, 982, true, 6, 3, 176",
        "1366, 768, true, 6, 2, 128",
        "1920, 1080, true, 6, 3, 176"
    })
    void optionalCatalogChangesColumnsAndCardDensity(
        int width, int height, boolean editors, int count,
        int columns, float cardHeight
    ) {
        MainMenuLayout layout = MainMenuLayout.calculate(
            width, height, width, editors, count);

        assertEquals(columns, layout.columns());
        assertEquals(cardHeight, layout.cardHeight());
        assertTrue(layout.scale() > 0f);
        assertEquals(width / layout.scale(), layout.width(), 0.001f);
        assertEquals(height / layout.scale(), layout.height(), 0.001f);
    }

    @Test
    void editorGridReflowsFromThreeColumnsToTwoWithAnExtraRow() {
        MainMenuLayout wide = MainMenuLayout.calculate(2560, 1440, 1920, true, 6);
        MainMenuLayout narrow = MainMenuLayout.calculate(2560, 1440, 1366, true, 6);

        assertEquals(3, wide.columns());
        assertEquals(2, narrow.columns());
        assertEquals(2, (6 + wide.columns() - 1) / wide.columns());
        assertEquals(3, (6 + narrow.columns() - 1) / narrow.columns());
        assertEquals(176f, wide.cardHeight());
        assertEquals(128f, narrow.cardHeight());
    }
}
