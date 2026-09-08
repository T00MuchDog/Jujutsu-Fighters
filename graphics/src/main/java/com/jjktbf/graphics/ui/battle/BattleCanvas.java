package com.jjktbf.graphics.ui.battle;

import com.badlogic.gdx.math.Rectangle;
import com.jjktbf.graphics.ui.UiScaleSystem;

/** Uniformly scales the shared battle composition while its surfaces fill the viewport. */
public final class BattleCanvas {

    public static final float WIDTH = UiScaleSystem.GAMEPLAY_REFERENCE_WIDTH;
    public static final float HEIGHT = UiScaleSystem.GAMEPLAY_REFERENCE_HEIGHT;
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

    private BattleCanvas(float viewportWidth, float viewportHeight) {
        this.viewportWidth = Math.max(1f, viewportWidth);
        this.viewportHeight = Math.max(1f, viewportHeight);
        var metrics = UiScaleSystem.gameplayMetrics(
            this.viewportWidth, this.viewportHeight);
        this.scale = metrics.scale();
        this.offsetX = (this.viewportWidth - WIDTH * scale) / 2f;
        this.bottomOffsetY = 0f;
        // The foreground back sprites are cropped at the planner edge. Moving the
        // field independently exposes their flat bottoms on taller viewports.
        this.topOffsetY = this.bottomOffsetY;
    }

    public static BattleCanvas fit(float viewportWidth, float viewportHeight) {
        return new BattleCanvas(viewportWidth, viewportHeight);
    }

    public float viewportWidth() { return viewportWidth; }
    public float viewportHeight() { return viewportHeight; }
    public float scale() { return scale; }
    public float offsetX() { return offsetX; }
    public float offsetY(Anchor anchor) {
        return anchor == Anchor.TOP ? topOffsetY : bottomOffsetY;
    }

    /** Bottom-anchored chrome extends into side margins; controls remain in the safe canvas. */
    public Rectangle planningSurface() {
        return new Rectangle(logicalX(0f), 0f, viewportWidth / scale, BOTTOM_SECTION_HEIGHT);
    }

    /** Upper surfaces use the battlefield transform, but meet the bottom-fixed planner exactly. */
    public Rectangle executionSurface() {
        float bottom = BOTTOM_SECTION_HEIGHT - topOffsetY / scale;
        return new Rectangle(LEFT_COLUMN_WIDTH, bottom,
            logicalX(viewportWidth) - LEFT_COLUMN_WIDTH,
            logicalY(viewportHeight, Anchor.TOP) - bottom);
    }

    public Rectangle logSurface() {
        Rectangle execution = executionSurface();
        float left = logicalX(0f);
        return new Rectangle(left, execution.y, LEFT_COLUMN_WIDTH - left, execution.height);
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
