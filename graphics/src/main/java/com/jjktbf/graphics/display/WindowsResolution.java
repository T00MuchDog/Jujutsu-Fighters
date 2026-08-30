package com.jjktbf.graphics.display;

import java.util.Arrays;
import java.util.Optional;

/** Supported Windows rendering resolutions, ordered from smallest to largest. */
public enum WindowsResolution {
    HD_1366_768(1366, 768),
    FULL_HD_1920_1080(1920, 1080),
    QHD_2560_1440(2560, 1440);

    private final int width;
    private final int height;
    private final String id;
    private final String label;

    WindowsResolution(int width, int height) {
        this.width = width;
        this.height = height;
        this.id = width + "x" + height;
        this.label = width + " x " + height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String id() {
        return id;
    }

    public boolean fits(int availableWidth, int availableHeight) {
        return width <= availableWidth && height <= availableHeight;
    }

    public static Optional<WindowsResolution> exact(int width, int height) {
        return Arrays.stream(values())
            .filter(resolution -> resolution.width == width && resolution.height == height)
            .findFirst();
    }

    /** Chooses the largest fitting mode, or the minimum supported mode if none fit. */
    public static WindowsResolution bestFor(int availableWidth, int availableHeight) {
        WindowsResolution best = HD_1366_768;
        for (WindowsResolution resolution : values()) {
            if (resolution.fits(availableWidth, availableHeight)) best = resolution;
        }
        return best;
    }

    public static WindowsResolution parseId(String id) {
        if (id != null) {
            for (WindowsResolution resolution : values()) {
                if (resolution.id.equals(id.trim())) return resolution;
            }
        }
        throw new IllegalArgumentException("Unsupported Windows resolution: " + id);
    }

    @Override
    public String toString() {
        return label;
    }
}
