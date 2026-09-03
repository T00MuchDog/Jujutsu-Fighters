package com.jjktbf.multiplayer.protocol;

import java.util.List;

/** Wire representation of one ordered damaging component of a move. */
public record HitComponentState(
    int basePower,
    String category,
    List<String> tags,
    int delayTicks,
    boolean requiresPreviousConnection,
    boolean avoidable,
    double baseAccuracy,
    boolean reinforcementEligible,
    int reinforcementBonusPower
) {
    public HitComponentState {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public HitComponentState(
        int basePower, String category, List<String> tags, int delayTicks,
        boolean requiresPreviousConnection, boolean avoidable, double baseAccuracy
    ) {
        this(basePower, category, tags, delayTicks, requiresPreviousConnection,
            avoidable, baseAccuracy, false, 0);
    }
}
