package com.jjktbf.multiplayer.protocol;

/** Authored reinforcement data and actor-specific availability for a move card. */
public record ReinforcementMoveState(
    boolean canBeReinforced,
    boolean available,
    int baseCeCost,
    int effectiveCeCost,
    int minCeCost,
    int maxCeCost,
    String defenseType,
    int defenseValue
) { }
