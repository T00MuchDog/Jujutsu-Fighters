package com.jjktbf.model.move;

/**
 * Explicit combatant endpoints selected for a move that acts on a pair.
 * The caster is implicit in the self modes; stored target IDs contain only
 * the endpoints named after {@code SELF}, in the enum's declared order.
 */
public enum Targeting {
    DEFAULT("Default"),
    SELF_AND_ENEMY("Self and Enemy"),
    SELF_AND_ALLY("Self and Ally"),
    ALLY_AND_ENEMY("Ally and Enemy");

    private final String displayName;

    Targeting(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Missing, blank, or unknown persisted values use the safe non-pair mode. */
    public static Targeting fromName(String name) {
        if (name == null || name.isBlank()) return DEFAULT;
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return DEFAULT;
        }
    }

    public int explicitTargetCount() {
        return switch (this) {
            case DEFAULT -> 0;
            case SELF_AND_ENEMY, SELF_AND_ALLY -> 1;
            case ALLY_AND_ENEMY -> 2;
        };
    }
}
