package com.jjktbf.model.combat;

/**
 * Authoritative roster-size format for a battle.
 *
 * <p>The multi-fighter engine ({@link BattleState}, {@link BattleTeam},
 * {@code BattleController.runTeamBattle}) supports any number of fighters per
 * team; this enum captures the <em>configured</em> format for a specific match
 * so setup flows (local character select, and later the multiplayer
 * challenge/match chain) agree on how many fighters each side fields.
 *
 * <ul>
 *   <li>{@link #ONE_V_ONE} — one fighter per side (the legacy default).</li>
 *   <li>{@link #TWO_V_TWO} — two fighters per side.</li>
 *   <li>{@link #SIX_V_SIX} — six fighters per side, three on the field.</li>
 * </ul>
 */
public enum BattleFormat {
    /** One fighter per side. */
    ONE_V_ONE(1, 1),
    /** Two fighters per side. */
    TWO_V_TWO(2, 2),
    /** Six fighters per side: three active and three held in reserve. */
    SIX_V_SIX(6, 3);

    private final int fightersPerSide;
    private final int activeFightersPerSide;

    BattleFormat(int fightersPerSide, int activeFightersPerSide) {
        this.fightersPerSide = fightersPerSide;
        this.activeFightersPerSide = activeFightersPerSide;
    }

    /** Total number of fighters on each side's roster. */
    public int fightersPerSide() {
        return fightersPerSide;
    }

    /** Maximum number of roster fighters occupying field slots at once. */
    public int activeFightersPerSide() {
        return activeFightersPerSide;
    }

    public int reserveFightersPerSide() {
        return fightersPerSide - activeFightersPerSide;
    }

    public boolean hasReserves() {
        return reserveFightersPerSide() > 0;
    }
}
