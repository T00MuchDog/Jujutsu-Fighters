package com.jjktbf.graphics.ui;

import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jjktbf.graphics.ui.profile.UiProfile;

import java.util.Objects;

/** Shared gameplay metrics; legacy menu/editor viewport policies remain separate. */
public final class UiScaleSystem {
    public static final float GAMEPLAY_REFERENCE_WIDTH = 2560f;
    public static final float GAMEPLAY_REFERENCE_HEIGHT = 1440f;
    public static final float GAMEPLAY_TEXT_SCALE = 1.5f;
    public static final float WINDOWS_REFERENCE_WIDTH =
        UiProfile.WINDOWS.defaultReferenceWidth();
    public static final float WINDOWS_REFERENCE_HEIGHT =
        UiProfile.WINDOWS.defaultReferenceHeight();

    private UiScaleSystem() {
    }

    /** Expand the logical layout instead of letterboxing or stretching its elements. */
    public static Viewport newGameplayViewport() {
        return new ExtendViewport(GAMEPLAY_REFERENCE_WIDTH, GAMEPLAY_REFERENCE_HEIGHT);
    }

    public static GameplayMetrics gameplayMetrics(float width, float height) {
        float safeWidth = Math.max(1f, width);
        float safeHeight = Math.max(1f, height);
        float scale = Math.min(safeWidth / GAMEPLAY_REFERENCE_WIDTH,
            safeHeight / GAMEPLAY_REFERENCE_HEIGHT);
        return new GameplayMetrics(scale, safeWidth / scale, safeHeight / scale);
    }

    public record GameplayMetrics(float scale, float worldWidth, float worldHeight) { }

    /** Keep body glyphs readable on small viewports without enlarging the whole composition. */
    public static float bodyTextScale(float viewportScale, float capHeight) {
        return Math.max(1f, Math.min(1.6f, 9f / Math.max(1f, viewportScale * capHeight)));
    }

    /** Creates an independent viewport; viewports are mutable and must not be shared by screens. */
    public static Viewport newViewport(UiProfile profile) {
        return Objects.requireNonNull(profile, "profile") == UiProfile.WINDOWS
            ? new FitViewport(WINDOWS_REFERENCE_WIDTH, WINDOWS_REFERENCE_HEIGHT)
            : new ScreenViewport();
    }

    public static Fit fitWindows(float targetWidth, float targetHeight) {
        float safeWidth = Math.max(1f, targetWidth);
        float safeHeight = Math.max(1f, targetHeight);
        float scale = Math.min(
            safeWidth / WINDOWS_REFERENCE_WIDTH,
            safeHeight / WINDOWS_REFERENCE_HEIGHT);
        float width = WINDOWS_REFERENCE_WIDTH * scale;
        float height = WINDOWS_REFERENCE_HEIGHT * scale;
        return new Fit(
            scale,
            Math.max(0f, (safeWidth - width) * 0.5f),
            Math.max(0f, (safeHeight - height) * 0.5f),
            width,
            height);
    }

    public record Fit(
        float scale,
        float offsetX,
        float offsetY,
        float width,
        float height
    ) {
    }
}
