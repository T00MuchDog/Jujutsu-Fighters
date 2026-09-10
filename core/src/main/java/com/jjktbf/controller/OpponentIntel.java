package com.jjktbf.controller;

import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.MoveAvailability;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.Move;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only snapshot of what an opponent brings to a round — the "smart AI"
 * opponent model, shared by every archetype.
 *
 * <p>Computed once per round from the opponent's authored move pool and current
 * resources. It deliberately does not inspect a plan or timeline: these are
 * predictions about plausible options, not knowledge of hidden commitments.
 * <ul>
 *   <li>The opponent's <em>authored</em> move pool
 *       ({@code opponent.getCharacter().getKnownMoves()}) — what they <em>can</em>
 *       do. Only moves currently passing {@link MoveAvailability} and fitting
 *       the AP and CE budgets are considered.</li>
 *   <li>Available defense counts describe distinct options, while anticipated
 *       attack fire-ticks come from repeating the most AP-efficient affordable
 *       attack. Neither is a claim about the opponent's plan.</li>
 * </ul>
 *
 * <p>All fields are package-private; this is an internal collaboration type for
 * the AI strategies and their tests.
 */
final class OpponentIntel {

    static final OpponentIntel EMPTY = new OpponentIntel(
        false, false, false, false, 0,
        0, 0, 0, 0, List.of(), List.of(), 0);

    // --- Currently available attack profile ---
    /** The opponent's currently available attack moves (for block-coverage checks). */
    final List<Move> attacks;
    /** Any available attack carries cursed energy (reinforcement or pure CE). */
    final boolean hasCursedEnergy;
    /** Has available attacks and none carry cursed energy (purely physical arsenal). */
    final boolean physicalOnly;
    final boolean hasGuardBreak;
    final boolean hasIntangible;
    final int maxAttackPotency;

    // --- Currently available options and anticipated pressure ---
    /** Count of distinct affordable melee-scoped dodge options. */
    final int availableMeleeDodge;
    /** Count of distinct affordable ranged-scoped dodge options. */
    final int availableRangedDodge;
    /** Count of distinct affordable block options. */
    final int availableBlock;
    /** Count of distinct affordable parry options. */
    final int availableParry;
    /** Predicted first-hit ticks for plausible attack uses, ascending. */
    final List<Integer> anticipatedAttackFireTicks;

    final int evasion;

    private OpponentIntel(
        boolean hasCursedEnergy, boolean physicalOnly,
        boolean hasGuardBreak, boolean hasIntangible, int maxAttackPotency,
        int availableMeleeDodge, int availableRangedDodge,
        int availableBlock, int availableParry,
        List<Integer> anticipatedAttackFireTicks,
        List<Move> attacks, int evasion
    ) {
        this.hasCursedEnergy = hasCursedEnergy;
        this.physicalOnly = physicalOnly;
        this.hasGuardBreak = hasGuardBreak;
        this.hasIntangible = hasIntangible;
        this.maxAttackPotency = maxAttackPotency;
        this.availableMeleeDodge = availableMeleeDodge;
        this.availableRangedDodge = availableRangedDodge;
        this.availableBlock = availableBlock;
        this.availableParry = availableParry;
        this.anticipatedAttackFireTicks = List.copyOf(anticipatedAttackFireTicks);
        this.attacks = List.copyOf(attacks);
        this.evasion = evasion;
    }

    static OpponentIntel forOpponent(BattleCombatant opponent) {
        if (opponent == null || opponent.getCharacter() == null) return EMPTY;

        int meleeDodge = 0;
        int rangedDodge = 0;
        int block = 0;
        int parry = 0;
        List<Move> availableAttacks = new ArrayList<>();
        boolean hasCe = false;
        boolean hasGuardBreak = false;
        boolean hasIntangible = false;
        int maxPotency = 0;
        for (Move move : opponent.getCharacter().getKnownMoves()) {
            if (!isAvailableAndAffordable(opponent, move)) continue;
            if (move.hasTag("ATTACK")) {
                availableAttacks.add(move);
                if (move.hasTag("CURSED_ENERGY")) hasCe = true;
                if (move.isGuardBreak()) hasGuardBreak = true;
                if (move.isIntangible()) hasIntangible = true;
                maxPotency = Math.max(maxPotency, move.getPotency());
            }
            if (!move.isActiveDefense()) continue;
            DefenseType type = move.getDefenseType();
            if (type == DefenseType.DODGE) {
                String scope = move.getDodgeScope();
                if ("MELEE".equalsIgnoreCase(scope) || "BOTH".equalsIgnoreCase(scope)) meleeDodge++;
                if ("RANGED".equalsIgnoreCase(scope) || "BOTH".equalsIgnoreCase(scope)) rangedDodge++;
            } else if (type == DefenseType.BLOCK) {
                block++;
            } else if (type == DefenseType.PARRY) {
                parry++;
            }
        }
        boolean physicalOnly = !availableAttacks.isEmpty() && !hasCe;

        return new OpponentIntel(
            hasCe, physicalOnly, hasGuardBreak, hasIntangible, maxPotency,
            meleeDodge, rangedDodge, block, parry,
            anticipatedAttackFireTicks(opponent, availableAttacks), availableAttacks,
            opponent.getEvasion());
    }

    /** A move is an intel option only when it can plausibly be used right now. */
    private static boolean isAvailableAndAffordable(BattleCombatant opponent, Move move) {
        if (!MoveAvailability.isAvailable(null, opponent, move)) return false;
        try {
            if (opponent.getEffectiveMoveApCost(move) > opponent.getMaxApBar()) return false;
        } catch (ArithmeticException exception) {
            return false;
        }
        return opponent.computeMoveCeCost(move) <= opponent.getCurrentCe();
    }

    /**
     * Forecast up to three uses of the best pressure-per-AP attack. The forecast
     * starts at tick one and advances by the move's effective AP width; each
     * entry is the first-hit tick, including action delay and hit delay.
     */
    private static List<Integer> anticipatedAttackFireTicks(
        BattleCombatant opponent, List<Move> availableAttacks
    ) {
        Move attack = availableAttacks.stream()
            .max(Comparator
                .comparingDouble((Move move) -> pressurePerAp(opponent, move))
                .thenComparingInt(Move::getTotalBasePower)
                .thenComparingInt(Move::getApCost)
                .thenComparing(Move::getId))
            .orElse(null);
        if (attack == null) return List.of();

        int apCost = opponent.getEffectiveMoveApCost(attack);
        int ceCost = opponent.computeMoveCeCost(attack);
        int uses = Math.min(3, opponent.getMaxApBar() / apCost);
        if (ceCost > 0) uses = Math.min(uses, opponent.getCurrentCe() / ceCost);
        if (attack.getMoveCap() > 0) uses = Math.min(uses, attack.getMoveCap());

        int startTick = 1;
        int firstHitDelay = attack.getHitComponents().stream()
            .mapToInt(component -> component.getDelayTicks()).min().orElse(0);
        List<Integer> fireTicks = new ArrayList<>();
        List<Move> predictedUses = new ArrayList<>();
        for (int i = 0; i < uses; i++) {
            if (MoveAvailability.restrictionReason(null, opponent, attack, predictedUses) != null) break;
            fireTicks.add(startTick + opponent.getEffectiveMoveUnleashPoint(attack) - 1
                + firstHitDelay);
            predictedUses.add(attack);
            startTick += apCost;
        }
        return List.copyOf(fireTicks);
    }

    private static double pressurePerAp(BattleCombatant opponent, Move move) {
        int apCost = opponent.getEffectiveMoveApCost(move);
        return Math.max(1, move.getTotalBasePower()) / (double) apCost;
    }
}
