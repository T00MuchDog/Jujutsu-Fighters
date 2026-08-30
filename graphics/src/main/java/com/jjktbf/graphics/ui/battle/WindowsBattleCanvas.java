package com.jjktbf.graphics.ui.battle;

import com.badlogic.gdx.math.Rectangle;
import com.jjktbf.graphics.ui.UiScaleSystem;

/** Maps the fixed Windows battle composition to the live desktop viewport. */
public final class WindowsBattleCanvas {

    public static final float WIDTH = UiScaleSystem.WINDOWS_REFERENCE_WIDTH;
    public static final float HEIGHT = UiScaleSystem.WINDOWS_REFERENCE_HEIGHT;
    public static final float PLANNING_HEIGHT_REDUCTION = 47.04f;
    public static final float BOTTOM_SECTION_HEIGHT = 537.96f;
    public static final float LEFT_COLUMN_WIDTH = 581f;
    public static final float ACTION_X = 72f;
    public static final float PLAYBACK_ACTION_X = 162f;
    public static final float ACTION_Y = 326.96f;
    public static final float ACTION_WIDTH = 186f;
    public static final float ACTION_HEIGHT = 172f;
    public static final float SPEED_CONTROL_X = 18f;
    public static final float SPEED_CONTROL_SIZE = 82f;
    public static final float SPEED_CONTROL_GAP = 8f;

    public enum Anchor { BOTTOM, TOP }

    private final float viewportWidth;
    private final float viewportHeight;
    private final float scale;
    private final float offsetX;
    private final float bottomOffsetY;
    private final float topOffsetY;

    private WindowsBattleCanvas(float viewportWidth, float viewportHeight) {
        this.viewportWidth = Math.max(1f, viewportWidth);
        this.viewportHeight = Math.max(1f, viewportHeight);
        UiScaleSystem.Fit fit = UiScaleSystem.fitWindows(
            this.viewportWidth, this.viewportHeight);
        this.scale = fit.scale();
        this.offsetX = fit.offsetX();
        this.bottomOffsetY = fit.offsetY();
        this.topOffsetY = fit.offsetY();
    }

    public static WindowsBattleCanvas fit(float viewportWidth, float viewportHeight) {
        return new WindowsBattleCanvas(viewportWidth, viewportHeight);
    }

    public float viewportWidth() { return viewportWidth; }
    public float viewportHeight() { return viewportHeight; }
    public float scale() { return scale; }
    public float offsetX() { return offsetX; }
    public float offsetY(Anchor anchor) {
        return anchor == Anchor.TOP ? topOffsetY : bottomOffsetY;
    }

    public float logicalX(float physicalX) {
        return (physicalX - offsetX) / scale;
    }

    public float logicalY(float physicalBottomY, Anchor anchor) {
        return (physicalBottomY - offsetY(anchor)) / scale;
    }

    public Rectangle physicalBounds(Rectangle logicalBounds, Anchor anchor) {
        return new Rectangle(
            offsetX + logicalBounds.x * scale,
            offsetY(anchor) + logicalBounds.y * scale,
            logicalBounds.width * scale,
            logicalBounds.height * scale);
    }
}
