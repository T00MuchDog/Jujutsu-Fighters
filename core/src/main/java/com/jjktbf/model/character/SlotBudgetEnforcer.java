package com.jjktbf.model.character;

import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MovePool;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for move-slot budget calculations.
 *
 * <p>There are two move pools, each granted slots by a single stat:
 * <ul>
 *   <li>{@link MovePool#COMBAT_ARTS} — moves containing the PHYSICAL tag;
 *       slots granted by the Combat Ability stat.</li>
 *   <li>{@link MovePool#JUJUTSU_ARTS} — moves without the PHYSICAL tag;
 *       slots granted by the Jujutsu Skill stat.</li>
 * </ul>
 *
 * <p>Every non-free move consumes a slot in its pool unless an ability or item
 * explicitly grants it as slot-exempt. Offensive, defensive and utility moves
 * alike take up slots.
 *
 * <p>This is shared by character construction, battle loadout validation, and
 * presentation-layer counters so all three use the same budgets.
 */
public final class SlotBudgetEnforcer {

    private SlotBudgetEnforcer() {}

    /**
     * Returns the number of move slots available for the given {@link MovePool},
     * given the character's derived combat stats.
     */
    public static int slotBudgetFor(CombatStats cs, MovePool pool) {
        return switch (pool) {
            case COMBAT_ARTS  -> cs.getCombatArtsSlots();
            case JUJUTSU_ARTS -> cs.getJujutsuArtsSlots();
        };
    }

    /**
     * Counts slot usage from a set of already-assigned move pools.
     *
     * @param assignedPools  pools of moves already assigned (may include duplicates)
     * @return mutable EnumMap of pool → slots used
     */
    public static Map<MovePool, Integer> countUsage(Iterable<MovePool> assignedPools) {
        Map<MovePool, Integer> used = new EnumMap<>(MovePool.class);
        for (MovePool pool : assignedPools) {
            used.merge(pool, 1, Integer::sum);
        }
        return used;
    }

    /** Whether a learned move consumes one slot when included in a move set. */
    public static boolean consumesSlot(Move move, Set<String> exemptMoveIds) {
        return move != null
            && !move.isFreeMove()
            && (exemptMoveIds == null || !exemptMoveIds.contains(move.getId()));
    }

    /** Counts move-set usage by pool, excluding free and automatically granted moves. */
    public static Map<MovePool, Integer> countMoveSetUsage(
        Iterable<Move> moves,
        Set<String> exemptMoveIds
    ) {
        Map<MovePool, Integer> used = new EnumMap<>(MovePool.class);
        if (moves == null) return used;
        for (Move move : moves) {
            if (consumesSlot(move, exemptMoveIds)) {
                used.merge(move.getPool(), 1, Integer::sum);
            }
        }
        return used;
    }

    /**
     * Selects the first learned moves, in authored order, that fit each pool.
     * Free and automatically granted moves are always retained.
     */
    public static List<Move> defaultMoveSet(
        List<Move> learnedMoves,
        CombatStats combatStats,
        Set<String> exemptMoveIds
    ) {
        if (learnedMoves == null || learnedMoves.isEmpty()) return List.of();
        Map<MovePool, Integer> used = new EnumMap<>(MovePool.class);
        List<Move> selected = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();
        for (Move move : learnedMoves) {
            if (move == null || !selectedIds.add(move.getId())) continue;
            if (!consumesSlot(move, exemptMoveIds)) {
                selected.add(move);
                continue;
            }
            MovePool pool = move.getPool();
            int current = used.getOrDefault(pool, 0);
            if (current >= slotBudgetFor(combatStats, pool)) continue;
            selected.add(move);
            used.put(pool, current + 1);
        }
        return List.copyOf(selected);
    }

    /** Rejects a move set that exceeds either stat-derived pool budget. */
    public static void validateMoveSet(
        Iterable<Move> moves,
        CombatStats combatStats,
        Set<String> exemptMoveIds
    ) {
        Map<MovePool, Integer> used = countMoveSetUsage(moves, exemptMoveIds);
        for (MovePool pool : MovePool.values()) {
            int count = used.getOrDefault(pool, 0);
            int budget = slotBudgetFor(combatStats, pool);
            if (count > budget) {
                throw new IllegalArgumentException(
                    "Move set exceeds " + pool + " slots (used=" + count
                        + ", budget=" + budget + ")");
            }
        }
    }
}
