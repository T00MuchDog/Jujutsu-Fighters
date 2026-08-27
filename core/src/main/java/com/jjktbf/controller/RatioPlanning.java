package com.jjktbf.controller;

import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.coded.RatioAbility;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveEffectData;

/**
 * Planning-time model of the Ratio technique, used by {@link RatioAIStrategy}.
 *
 * <p>Moves are classified by their authored coded effect rows — never by id or
 * name — so any present or future Ratio content composes with the same AI:
 * <ul>
 *   <li><b>Ratio stack move</b> — carries a {@code RATIO}/{@code RATIO_EFFECT}
 *       row targeted {@code CREATE_STACKS} (Ratio Mark): on fire it adds its
 *       {@code codedStackCount} to the target's stack count, bounded by the
 *       owner's stack capacity.</li>
 *   <li><b>Ratio strike</b> — carries a row targeted {@code APPLY_TO_MOVE}
 *       (Ratio Strike, Overhead Ratio Cleave): the attack itself drives Ratio
 *       into a connected hit, independently of any held stack.</li>
 * </ul>
 *
 * <p>Runtime consumption (see {@link RatioAbility}): every connected attack
 * consumes one stack held on its defender, giving that hit a chance to resolve
 * as a Ratio hit. A ratio strike already applies Ratio by itself, so spending a
 * stack on one is waste — the "use the stack up with a normal attack first"
 * rule encoded by {@link #pendingStacksAfter}.
 */
final class RatioPlanning {

    private RatioPlanning() { }

    /** True if this move creates Ratio stacks when it fires. */
    static boolean isRatioStackMove(Move move) {
        return ratioEffectRow(move, RatioAbility.CREATE_STACKS) != null;
    }

    /** True if this attack applies Ratio by itself on a connected hit. */
    static boolean isRatioStrike(Move move) {
        return ratioEffectRow(move, RatioAbility.APPLY_TO_MOVE) != null;
    }

    /** Stacks a stack move adds per use (1 when unauthored); 0 for other moves. */
    static int stacksCreatedBy(Move move) {
        MoveEffectData row = ratioEffectRow(move, RatioAbility.CREATE_STACKS);
        return row == null ? 0 : (row.codedStackCount == null ? 1 : row.codedStackCount);
    }

    /**
     * Pending-stack bookkeeping across one round's placements: a stack move
     * adds its stacks up to capacity, a normal attack uses one stack up, and
     * anything else leaves the count untouched.
     */
    static int pendingStacksAfter(Move placed, int pendingBefore, int capacity) {
        if (placed == null) return pendingBefore;
        if (isRatioStackMove(placed)) {
            return Math.min(capacity, pendingBefore + stacksCreatedBy(placed));
        }
        if (placed.hasTag("ATTACK") && !isRatioStrike(placed)) {
            return Math.max(0, pendingBefore - 1);
        }
        return pendingBefore;
    }

    private static MoveEffectData ratioEffectRow(Move move, String codedTarget) {
        if (move == null) return null;
        for (MoveEffectData effect : move.getEffects()) {
            if (effect == null
                || !AbilityEffectType.CODED_MOVE_ACTION.name().equalsIgnoreCase(effect.type)) {
                continue;
            }
            if (RatioAbility.KEY.equalsIgnoreCase(effect.codedAbilityKey)
                && RatioAbility.RATIO_EFFECT.equalsIgnoreCase(effect.codedAction)
                && codedTarget.equalsIgnoreCase(effect.codedTarget)) {
                return effect;
            }
        }
        return null;
    }
}
