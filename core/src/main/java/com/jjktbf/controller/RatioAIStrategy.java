package com.jjktbf.controller;

import com.jjktbf.model.character.StatKey;
import com.jjktbf.model.character.coded.RatioAbility;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.MoveAvailability;
import com.jjktbf.model.combat.PowerCalculator;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.Move;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI archetype for Nanami Kento (Ratio) — the measured professional, built on
 * the default greedy planner's selection/placement shape.
 *
 * <p>A deep analyser who spends exactly the effort a fight requires:
 * <ul>
 *   <li><b>Effort assessment.</b> He weighs the strongest enemy's base-stat
 *       total against his own. Against opponents clearly beneath him he keeps
 *       to <b>moderate effort</b>: a short offensive, cheap moves favoured,
 *       technique held back, cursed energy conserved. He goes <b>all out</b>
 *       only when the situation calls for it — the enemy reaches
 *       {@link #ALL_OUT_ENEMY_BST_FRACTION} of his own strength, or his own HP
 *       falls below {@link #SERIOUS_HP_FRACTION} (a professional sees it
 *       through).</li>
 *   <li><b>Ratio sequencing.</b> A ratio strike applies Ratio by itself, so
 *       feeding it a stack is waste: he never places one while a Ratio stack
 *       is pending — held from earlier rounds or just created by a placed
 *       stack move — until a <em>normal</em> attack has been placed to use the
 *       stack up. Offence is placed grouped-early, so placement order is fire
 *       order and the sequence resolves Mark → normal attack → strike.</li>
 *   <li><b>Efficiency.</b> Cheaper moves are always favoured (strongly under
 *       moderate effort), and stack moves are only planned while stack
 *       capacity remains — marking at full capacity creates nothing.</li>
 *   <li><b>Analysis.</b> Attack weights fold in the shared scoring factors
 *       (authored effects, dodge exposure, reinforcement bypass), a boost for a
 *       stack-consuming normal attack, and a large boost when the move's
 *       estimated damage is lethal. Defenses align to the opponent's earliest
 *       committed attack when he is fast enough to contest it.</li>
 * </ul>
 *
 * <p>Stack capacity/current counts come from the compiled {@link RatioAbility}
 * runtime via {@link BattleCombatant#abilityState}; without the Ratio
 * Reinforcement feature the capacity is 0 and stack moves are never planned.
 *
 * <p>State-aware ({@link #buildPlan}) because the effort assessment needs the
 * full enemy roster; {@link #selectPlan} is a single-opponent fallback.
 */
public class RatioAIStrategy implements AIStrategy {

    // --- Effort thresholds (the "situation calls for it" rules) ---
    /** Strongest enemy at/above this fraction of his own base-stat total → all out. */
    static final double ALL_OUT_ENEMY_BST_FRACTION = 0.9;
    /** His own HP fraction at/below which he gets serious regardless of the enemy. */
    static final double SERIOUS_HP_FRACTION = 0.35;

    // --- Shared offensive tunables (code-only) ---
    /** Nominal power a stack move competes with (they carry no base power). */
    private static final double MARK_NOMINAL_POWER = 40.0;
    /** Boost for a normal attack that will consume a pending stack (7:3 proc). */
    private static final double STACK_PAYOFF = 1.6;
    /** Boost when the move's estimated connected damage can finish the target. */
    private static final double LETHAL_BOOST = 3.0;

    /** Effort tier: how hard Nanami judges this fight to be. */
    enum EffortTier { MODERATE, ALL_OUT }

    /** Per-tier tunables (offence shape, technique appetite, CE thrift). */
    private record EffortProfile(
        int attackCap, int defenseCap, double utilityChance,
        double techniqueFactor, double ceCheapnessRef, double markWeight
    ) {
        static final EffortProfile MODERATE = new EffortProfile(2, 2, 0.45, 0.5, 25.0, 0.5);
        static final EffortProfile ALL_OUT  = new EffortProfile(4, 1, 0.25, 1.5, 90.0, 1.6);
    }

    /** Ratio stack counts the compiled runtime exposes for planning. */
    private record RatioStacks(int current, int capacity) {
        static RatioStacks of(BattleCombatant ai) {
            return ai.abilityState(RatioAbility.KEY)
                .map(state -> new RatioStacks(
                    Math.max(0, state.currentValue()), Math.max(0, state.maximumValue())))
                .orElse(new RatioStacks(0, 0));
        }
    }

    // -------------------------------------------------------------------------
    // Entries
    // -------------------------------------------------------------------------

    /** State-aware entry used by the dispatcher's team-plan build. */
    public BattlePlan buildPlan(BattleState state, BattleCombatant ai, RandomSource rng) {
        return placeMoves(state, ai, state.firstActiveEnemyOf(ai), rng);
    }

    /** Single-opponent fallback (interface contract). */
    @Override
    public BattlePlan selectPlan(BattleCombatant ai, BattleCombatant opponent, RandomSource rng) {
        return placeMoves(null, ai, opponent, rng);
    }

    // -------------------------------------------------------------------------
    // Placement
    // -------------------------------------------------------------------------

    private BattlePlan placeMoves(BattleState state, BattleCombatant ai,
                                  BattleCombatant opponent, RandomSource rng) {
        int gridLength = Timeline.gridLengthForStrongestAp(
            Math.max(ai.getMaxApBar(), opponent == null ? 0 : opponent.getMaxApBar()));
        BattlePlan plan = BattlePlan.forCombatant(ai, gridLength);
        OpponentIntel intel = OpponentIntel.forOpponent(opponent);
        RatioStacks stacks = RatioStacks.of(ai);

        List<Move> marks = new ArrayList<>();
        List<Move> strikes = new ArrayList<>();
        List<Move> normals = new ArrayList<>();
        List<Move> defenses = new ArrayList<>();
        List<Move> utilities = new ArrayList<>();
        for (Move move : ai.getCharacter().getKnownMoves()) {
            if (state != null && !MoveAvailability.isAvailable(state, ai, move)) continue;
            if (!MoveAvailability.isAvailable(null, ai, move)) continue;
            if (RatioPlanning.isRatioStackMove(move)) {
                marks.add(move);
            } else if (move.hasTag("ATTACK")) {
                (RatioPlanning.isRatioStrike(move) ? strikes : normals).add(move);
            } else if (move.isActiveDefense()) {
                defenses.add(move);
            } else {
                utilities.add(move);
            }
        }

        EffortTier tier = effortTier(
            baseStatTotal(ai), strongestEnemyBst(state, ai, opponent), hpFraction(ai));
        EffortProfile profile = tier == EffortTier.ALL_OUT
            ? EffortProfile.ALL_OUT : EffortProfile.MODERATE;

        // --- A measured opening read (accuracy/evasion utility), at most one,
        //     placed first so its round-long buff covers the offence. ---
        placeUtility(ai, plan, utilities, profile, rng);

        // --- Offence with Ratio sequencing. ---
        placeOffence(ai, opponent, plan, intel, marks, strikes, normals, profile, stacks, rng);

        // --- Defenses aligned to the opponent's earliest committed threat. ---
        placeDefenses(ai, opponent, plan, defenses, intel, profile);
        return plan;
    }

    // -------------------------------------------------------------------------
    // Offence
    // -------------------------------------------------------------------------

    private void placeOffence(
        BattleCombatant ai, BattleCombatant opponent, BattlePlan plan, OpponentIntel intel,
        List<Move> marks, List<Move> strikes, List<Move> normals,
        EffortProfile profile, RatioStacks stacks, RandomSource rng
    ) {
        Set<Move> stuck = new HashSet<>();
        int placed = 0;
        // Stacks already held on enemies must be used up before a ratio strike too.
        int pendingStacks = stacks.current();
        while (placed < profile.attackCap()) {
            List<Move> pool = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (Move m : normals) {
                if (stuck.contains(m) || !plan.canPlace(m, ai.computeMoveCeCost(m))) continue;
                pool.add(m);
                weights.add(attackWeight(m, ai, opponent, intel, profile, false, pendingStacks > 0));
            }
            for (Move m : marks) {
                if (stacks.capacity() <= 0 || pendingStacks >= stacks.capacity()) continue;
                if (stuck.contains(m) || !plan.canPlace(m, ai.computeMoveCeCost(m))) continue;
                pool.add(m);
                weights.add(markWeight(m, ai, profile));
            }
            for (Move m : strikes) {
                // The sequencing rule: never place a ratio strike while a stack
                // is pending — a normal attack must use the stack up first.
                if (pendingStacks > 0) continue;
                if (stuck.contains(m) || !plan.canPlace(m, ai.computeMoveCeCost(m))) continue;
                pool.add(m);
                weights.add(attackWeight(m, ai, opponent, intel, profile, true, false));
            }
            Move pick = SmartAIScoring.weightedRandomPick(pool, weights, rng);
            if (pick == null) break;
            int intrinsicCost = ai.computeMoveCeCost(pick);
            int surcharge = ai.canReinforce(pick)
                ? ai.computeReinforcementCeCost(pick) : 0;
            boolean reinforced = ai.canReinforce(pick)
                && plan.canPlace(pick, Math.addExact(intrinsicCost, surcharge));
            ActionSegment segment = SmartAIScoring.placeAtOrAfter(
                plan, pick, Math.addExact(intrinsicCost, reinforced ? surcharge : 0), 1,
                reinforced, reinforced ? surcharge : 0);
            if (segment == null) {
                stuck.add(pick);
            } else {
                placed++;
                pendingStacks = RatioPlanning.pendingStacksAfter(
                    pick, pendingStacks, stacks.capacity());
            }
        }
    }

    private static double attackWeight(
        Move move, BattleCombatant ai, BattleCombatant opponent, OpponentIntel intel,
        EffortProfile profile, boolean isTechnique, boolean stackPayoff
    ) {
        double basePower = Math.max(1, move.getTotalBasePower());
        double power = Math.max(1, PowerCalculator.compute(
            move.getCategory(), ai.getEffectiveStats(), ai.getStatMode()));
        double weight = basePower * power;
        weight *= SmartAIScoring.effectMultiplier(move);
        weight *= SmartAIScoring.dodgeExposureMultiplier(move, intel);
        weight *= SmartAIScoring.reinforcementAttackMultiplier(move, intel);
        weight *= ceCheapness(move, ai, profile);
        if (isTechnique) weight *= profile.techniqueFactor();
        if (stackPayoff) weight *= STACK_PAYOFF;
        if (opponent != null && SmartAIScoring.estimatedDamage(move, ai, opponent)
                >= opponent.getCurrentHp()) {
            weight *= LETHAL_BOOST;
        }
        return weight;
    }

    private static double markWeight(Move move, BattleCombatant ai, EffortProfile profile) {
        return MARK_NOMINAL_POWER
            * profile.markWeight()
            * ceCheapness(move, ai, profile);
    }

    /** Cheaper is better — the thrift (CE efficiency) scales with held-back effort. */
    private static double ceCheapness(Move move, BattleCombatant ai, EffortProfile profile) {
        int cost = Math.max(0, ai.computeMoveCeCost(move));
        return profile.ceCheapnessRef() / (profile.ceCheapnessRef() + cost);
    }

    // -------------------------------------------------------------------------
    // Utility + defense
    // -------------------------------------------------------------------------

    private void placeUtility(
        BattleCombatant ai, BattlePlan plan, List<Move> utilities,
        EffortProfile profile, RandomSource rng
    ) {
        if (utilities.isEmpty() || rng.nextDouble() >= profile.utilityChance()) return;
        utilities.stream()
            .min(Comparator.comparingInt(ai::computeMoveCeCost))
            .ifPresent(utility -> {
                int ceCost = ai.computeMoveCeCost(utility);
                if (plan.canPlace(utility, ceCost)) {
                    SmartAIScoring.placeAtOrAfter(plan, utility, ceCost, 1);
                }
            });
    }

    private void placeDefenses(
        BattleCombatant ai, BattleCombatant opponent, BattlePlan plan,
        List<Move> defenses, OpponentIntel intel, EffortProfile profile
    ) {
        List<Move> useful = new ArrayList<>();
        for (Move d : defenses) {
            if (SmartAIScoring.defenseValue(d, intel) > 0
                && plan.canPlace(d, ai.computeMoveCeCost(d))) {
                useful.add(d);
            }
        }
        useful.sort(Comparator.comparingDouble(
            (Move d) -> SmartAIScoring.defenseValue(d, intel)).reversed());
        int placed = 0;
        for (Move d : useful) {
            if (placed >= profile.defenseCap()) break;
            int ceCost = ai.computeMoveCeCost(d);
            Integer threatFireTick = intel.committedAttackFireTicks.isEmpty()
                ? null : intel.committedAttackFireTicks.get(0);
            ActionSegment segment = threatFireTick == null ? null
                : SmartAIScoring.placeAlignedToThreat(
                    plan, d, ceCost, threatFireTick, ai, opponent);
            if (segment == null) {
                segment = SmartAIScoring.placeAtOrAfter(plan, d, ceCost, 1);
            }
            if (segment != null) placed++;
        }
    }

    // -------------------------------------------------------------------------
    // Effort assessment + state helpers
    // -------------------------------------------------------------------------

    /**
     * Effort tier from the situation: all out when the strongest enemy is at or
     * near his own level, or when he himself is badly hurt. Package-private
     * for testing.
     */
    static EffortTier effortTier(int ownBst, int strongestEnemyBst, double ownHpFraction) {
        if (strongestEnemyBst >= ALL_OUT_ENEMY_BST_FRACTION * Math.max(1, ownBst)) {
            return EffortTier.ALL_OUT;
        }
        if (ownHpFraction <= SERIOUS_HP_FRACTION) return EffortTier.ALL_OUT;
        return EffortTier.MODERATE;
    }

    /** Sum of a combatant's effective base stats. */
    private static int baseStatTotal(BattleCombatant c) {
        if (c == null) return 0;
        int total = 0;
        for (StatKey stat : StatKey.values()) total += c.getRuntimeStat(stat);
        return total;
    }

    /** Highest base-stat total among active enemies (fallback opponent when stateless). */
    private static int strongestEnemyBst(BattleState state, BattleCombatant ai,
                                         BattleCombatant fallback) {
        int best = baseStatTotal(fallback);
        if (state == null) return best;
        for (BattleCombatant e : state.activeEnemiesOf(ai)) {
            best = Math.max(best, baseStatTotal(e));
        }
        return best;
    }

    private static double hpFraction(BattleCombatant ai) {
        return ai.getCurrentHp() / (double) Math.max(1, ai.getMaxHp());
    }
}
