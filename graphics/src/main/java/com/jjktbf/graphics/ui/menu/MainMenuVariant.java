package com.jjktbf.graphics.ui.menu;

import java.util.Locale;

/** Presentation choice only; both menus use the same game navigation. */
public enum MainMenuVariant {
    LEGACY, REDESIGNED;

    public MainMenuVariant other() {
        return this == LEGACY ? REDESIGNED : LEGACY;
    }

    public static MainMenuVariant parse(String value) {
        if (value != null) {
            try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { }
        }
        return REDESIGNED;
    }
}
