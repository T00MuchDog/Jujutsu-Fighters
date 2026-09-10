package com.jjktbf.graphics.ui.menu;

/** Viewport-local geometry; display/HiDPI policy stays in the existing launcher. */
public record MainMenuLayout(float scale, float width, float height, int columns, float cardHeight) {
    public static MainMenuLayout calculate(float worldWidth, float worldHeight, int screenWidth,
                                           boolean editors, int count) {
        float scale = Math.min(worldWidth / 1440f, worldHeight / 900f);
        float width = worldWidth / scale, height = worldHeight / scale;
        int columns = editors ? (screenWidth <= 1450 ? 2 : 3) : (screenWidth < 1100 ? 1 : count);
        int rows = (count + columns - 1) / columns;
        float cardHeight = editors ? (rows == 3 ? 128 : 176) : (rows > 1 ? 100 : 258);
        return new MainMenuLayout(scale, width, height, columns, cardHeight);
    }
}
