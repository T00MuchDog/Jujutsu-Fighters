package com.jjktbf.model.move;

/** Authored defensive improvement applied only while an execution is reinforced. */
public enum ReinforcementDefenseType {
    NONE,
    FLAT_BLOCK,
    PERCENTAGE_BLOCK,
    STAGGER_LENGTH;

    public static ReinforcementDefenseType fromName(String name) {
        if (name == null || name.isBlank()) return NONE;
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
