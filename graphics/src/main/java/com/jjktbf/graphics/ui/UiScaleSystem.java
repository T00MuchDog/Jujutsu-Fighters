package com.jjktbf.graphics.ui;

import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jjktbf.graphics.ui.profile.UiProfile;

import java.util.Objects;

/** Fixed-canvas scaling policy for the canonical Windows UI. */
public final class UiScaleSystem {
    public static final float WINDOWS_REFERENCE_WIDTH =
        UiProfile.WINDOWS.defaultReferenceWidth();
    public static final float WINDOWS_REFERENCE_HEIGHT =
        UiProfile.WINDOWS.defaultReferenceHeight();

    private UiScaleSystem() {
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
