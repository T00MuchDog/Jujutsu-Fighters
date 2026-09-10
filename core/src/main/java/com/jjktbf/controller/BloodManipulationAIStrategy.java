package com.jjktbf.controller;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.StatKey;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.MoveAvailability;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.StatusEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Offensive blood-economy planner. Compare direct pressure with one purposeful
 * setup, then spend the round following through. Moves are recognized by their
 * effect compositions, never by their ids. Opponent intel contains only visible
 * capabilities and resources, not the opponent's submitted actions.
 */
public class BloodManipulationAIStrategy implements AIStrategy {
    private static final String BLOOD = "BLOOD_SUPPLY";
    private static final String COMPRESSION = "COMPRESSION";
    private static final double DEFENSE_AP_SHARE = 0.20;
    private static final double COMPETITIVE_ATTACK_FRACTION = 0.70;

    public BattlePlan buildPlan(BattleState state, BattleCombatant ai, RandomSource rng) {
        return placeMoves(state, ai, state.firstActiveEnemyOf(ai), rng);
    }

    @Override
    public BattlePlan selectPlan(BattleCombatant ai, BattleCombatant opponent, RandomSource rng) {
        return placeMoves(null, ai, opponent, rng);
    }

    private BattlePlan placeMoves(BattleState state, BattleCombatant ai,
                                  BattleCombatant opponent, RandomSource rng) {
        int grid = Timeline.gridLengthForStrongestAp(
            Math.max(ai.getMaxApBar(), opponent == null ? 0 : opponent.getMaxApBar()));
        OpponentIntel intel = OpponentIntel.forOpponent(opponent);
        List<Move> attacks = new ArrayList<>();
        List<Move> defenses = new ArrayList<>();
        List<Move> setups = new ArrayList<>();
        for (Move move : ai.getCharacter().getKnownMoves()) {
            // A setup may unlock an attack that is not affordable in blood yet.
            if (AIStrategy.isAiUnsupported(move)
                || MoveAvailability.restrictionReasonWithoutBoundedResources(state, ai, move) != null) {
                continue;
            }
            if (move.hasTag("ATTACK")) attacks.add(move);
            else if (move.isActiveDefense()) defenses.add(move);
            else if (gainsCompression(ai, move)
                || (isSurge(move) && !ai.hasActiveRuntimeEffect(BloodManipulationAIStrategy::isCoreBoost))
                || (isAccuracyFocus(move)
                    && !ai.hasActiveRuntimeEffect(BloodManipulationAIStrategy::isAccuracyBoost)
                    && !ai.hasActiveRuntimeEffect(BloodManipulationAIStrategy::isSelfNeverMiss))) {
                setups.add(move);
            }
        }

        // Compare alternatives with equivalent random streams, rather than
        // giving later setup candidates unrelated lucky/unlucky attack rolls.
        int planningSeed = rng.nextInt(Integer.MAX_VALUE);
        BattlePlan best = buildCandidate(state, ai, opponent, intel, grid,
            attacks, defenses, null, new SeededRandomSource(planningSeed));
        double bestValue = planValue(best, ai, opponent, intel);
        // Do not delay an affordable finishing shot for a buff or a conversion.
        boolean immediateFinish = best.allSegments().stream().anyMatch(segment ->
            isLethal(segment.getMove(), ai, opponent));
        if (!immediateFinish) {
            for (Move setup : setups) {
                BattlePlan candidate = buildCandidate(state, ai, opponent, intel, grid,
                    attacks, defenses, setup, new SeededRandomSource(planningSeed));
                if (candidate == null) continue;
                double value = planValue(candidate, ai, opponent, intel);
                if (value > bestValue) {
                    best = candidate;
                    bestValue = value;
                }
            }
        }
        return best;
    }

    private BattlePlan buildCandidate(
        BattleState state, BattleCombatant ai, BattleCombatant opponent, OpponentIntel intel,
        int grid, List<Move> attacks, List<Move> defenses, Move setup, RandomSource rng
    ) {
        BattlePlan plan = BattlePlan.forCombatant(ai, grid);
        int cursor = 1;
        if (setup != null) {
            ActionSegment placed = placeAvailable(state, ai, plan, setup, 1);
            if (placed == null) return null;
            // Strictly later avoids cross-board ON_START transaction ordering,
            // and ensures ON_FIRE buffs exist before follow-up attacks launch.
            cursor = placed.getFireTick() + 1;
        }
        boolean aimWindow = ai.hasActiveRuntimeEffect(BloodManipulationAIStrategy::isSelfNeverMiss);
        boolean restrained = opponent != null && opponent.hasEffect(StatusEffectType.RESTRAINED);
        Set<Move> stuck = new HashSet<>();
        int attacksPlaced = 0;
        while (true) {
            List<Move> pool = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            double bestWeight = 0;
            for (Move move : attacks) {
                if (stuck.contains(move) || !plan.canPlace(move, ai.computeMoveCeCost(move))) continue;
                if (MoveAvailability.restrictionReason(state, ai, move, movesIn(plan)) != null) continue;
                double weight = attackValue(move, ai, opponent, aimWindow, restrained)
                    / (plan.effectiveApCost(move) + 0.5 * plan.remainingApBudget()
                        * ai.computeMoveCeCost(move) / Math.max(1.0, plan.remainingCe()))
                    * SmartAIScoring.dodgeExposureMultiplier(move, intel)
                    * SmartAIScoring.defenseCrackMultiplier(move, intel);
                if (isLethal(move, ai, opponent)) weight *= 4.0;
                pool.add(move);
                weights.add(weight);
                bestWeight = Math.max(bestWeight, weight);
            }
            // Keep variety among competitive attacks rather than wasting scarce
            // blood on a much weaker move just because it won a broad lottery.
            for (int i = pool.size() - 1; i >= 0; i--) {
                if (weights.get(i) < bestWeight * COMPETITIVE_ATTACK_FRACTION) {
                    pool.remove(i);
                    weights.remove(i);
                }
            }
            Move pick = SmartAIScoring.weightedRandomPick(pool, weights, rng);
            if (pick == null) break;
            ActionSegment segment = placeAvailable(state, ai, plan, pick, cursor);
            if (segment == null) {
                stuck.add(pick);
                continue;
            }
            attacksPlaced++;
            if (grantsNeverMiss(pick)) {
                aimWindow = true;
                cursor = Math.max(cursor, segment.getFireTick() + 1);
            }
            if (restrainsTarget(pick)) restrained = true;
            // Secure pressure before buying one modest defensive layer, then
            // keep attacking until AP, CE, blood, use caps or space run out.
            if (attacksPlaced == 2) placeDefense(state, ai, opponent, plan, defenses, intel);
        }
        if (attacksPlaced < 2) placeDefense(state, ai, opponent, plan, defenses, intel);
        if (setup != null) {
            if (gainsCompression(ai, setup)) {
                if (plan.allSegments().stream().noneMatch(s ->
                    spendsResource(ai, s.getMove(), COMPRESSION))) return null;
            } else if (attacksPlaced < 2) {
                return null; // no empty buff rounds or setup for a lone poke
            }
        }
        return plan;
    }

    private void placeDefense(BattleState state, BattleCombatant ai, BattleCombatant opponent,
                              BattlePlan plan, List<Move> defenses, OpponentIntel intel) {
        List<Move> ranked = new ArrayList<>(defenses);
        ranked.sort(Comparator.comparingDouble((Move move) ->
            SmartAIScoring.defenseValue(move, intel) / plan.effectiveApCost(move)).reversed());
        for (Move defense : ranked) {
            if (plan.effectiveApCost(defense) > plan.apBudget() * DEFENSE_AP_SHARE
                || SmartAIScoring.defenseValue(defense, intel) <= 0) continue;
            for (int threat : intel.anticipatedAttackFireTicks) {
                int lead = opponent != null
                    && ai.getRuntimeStat(StatKey.SPEED) <= opponent.getRuntimeStat(StatKey.SPEED) ? 1 : 0;
                int near = threat - lead - plan.effectiveUnleashPoint(defense) + 1;
                if (near < 1) continue;
                ActionSegment placed = placeAvailable(state, ai, plan, defense, near);
                if (placed == null) continue;
                if (placed.getFireTick() <= threat - lead) return;
                plan.remove(placed); // occupied setup space pushed it past the threat
            }
        }
    }

    /** Validate the actual chronological sequence, including all setup costs. */
    private static ActionSegment placeAvailable(BattleState state, BattleCombatant ai,
                                                BattlePlan plan, Move move, int fromTick) {
        // Availability projects whole moves. Serialize resource-bearing actions
        // past each other's fire ticks so ON_FIRE and ON_START transactions
        // cannot interleave across the two boards and invalidate that projection.
        if (usesBoundedResources(ai, move)) {
            for (ActionSegment existing : plan.allSegments()) {
                if (usesBoundedResources(ai, existing.getMove())) {
                    fromTick = Math.max(fromTick, existing.getFireTick() + 1);
                }
            }
        }
        ActionSegment segment = SmartAIScoring.placeAtOrAfter(
            plan, move, ai.computeMoveCeCost(move), fromTick);
        if (segment == null) return null;
        List<Move> earlier = new ArrayList<>();
        for (ActionSegment action : plan.allSegments()) {
            if (MoveAvailability.restrictionReason(state, ai, action.getMove(), earlier) != null) {
                plan.remove(segment);
                return null;
            }
            earlier.add(action.getMove());
        }
        return segment;
    }

    private static boolean usesBoundedResources(BattleCombatant ai, Move move) {
        return !MoveAvailability.guaranteedBoundedResourceTransactions(ai, move).isEmpty()
            || !MoveAvailability.guaranteedBoundedResourcePowerConsumers(ai, move).isEmpty();
    }

    private static List<Move> movesIn(BattlePlan plan) {
        return plan.allSegments().stream().map(ActionSegment::getMove).toList();
    }

    private static double attackValue(Move move, BattleCombatant ai, BattleCombatant opponent,
                                      boolean aimWindow, boolean restrained) {
        double damage = opponent == null ? move.getTotalBasePower()
            : SmartAIScoring.estimatedDamage(move, ai, opponent);
        double value = Math.max(1, damage) * SmartAIScoring.effectMultiplier(move);
        // Prefer free pressure when competitive, but never hoard a killing shot.
        if (spendsResource(ai, move, BLOOD) && !isLethal(move, ai, opponent)) value *= 0.85;
        if (grantsNeverMiss(move)) value *= aimWindow ? 0.4 : 1.35;
        if (move.hasTag("BOW") && aimWindow) value *= 1.15;
        if (restrainsTarget(move)) {
            if (restrained) value *= 0.5;
            else if (opponent != null
                && opponent.getRuntimeStat(StatKey.SPEED) > ai.getRuntimeStat(StatKey.SPEED)) value *= 2.0;
        }
        return value;
    }

    /** Compare whole rounds so setup has to earn back the attacks it displaces. */
    private static double planValue(BattlePlan plan, BattleCombatant ai,
                                    BattleCombatant opponent, OpponentIntel intel) {
        double value = 0;
        double surgeMultiplier = 1.0;
        boolean accuracyFocus = false;
        boolean aimWindow = ai.hasActiveRuntimeEffect(BloodManipulationAIStrategy::isSelfNeverMiss);
        boolean restrained = opponent != null && opponent.hasEffect(StatusEffectType.RESTRAINED);
        List<ActionSegment> actions = new ArrayList<>(plan.allSegments());
        actions.sort(Comparator.comparingInt(ActionSegment::getFireTick));
        for (ActionSegment action : actions) {
            Move move = action.getMove();
            if (isSurge(move)) {
                surgeMultiplier = 1.0 + move.getEffects().stream()
                    .filter(BloodManipulationAIStrategy::isCoreBoost)
                    .mapToDouble(e -> e.doubleValue == null ? 0 : Math.min(0.5, e.doubleValue))
                    .max().orElse(0.0);
            }
            if (isAccuracyFocus(move)) accuracyFocus = true;
            if (!move.hasTag("ATTACK")) continue;
            double attack = attackValue(move, ai, opponent, aimWindow, restrained);
            attack *= SmartAIScoring.dodgeExposureMultiplier(move, intel)
                * SmartAIScoring.defenseCrackMultiplier(move, intel);
            if (move.hasTag("PHYSICAL")) attack *= surgeMultiplier;
            if (accuracyFocus && !aimWindow) attack *= 1.05;
            // Earlier pressure has a modest advantage over an equal late combo.
            value += attack / (1.0 + 0.15 * action.getFireTick() / plan.gridLength());
            if (grantsNeverMiss(move)) aimWindow = true;
            if (restrainsTarget(move)) restrained = true;
        }
        return value * (1.0 - 0.15 * plan.totalCeUsed() / Math.max(1.0, plan.ceBudget()));
    }

    private static boolean isLethal(Move move, BattleCombatant ai, BattleCombatant opponent) {
        return opponent != null && move.hasTag("ATTACK")
            && SmartAIScoring.estimatedDamage(move, ai, opponent) >= opponent.getCurrentHp();
    }

    private static boolean gainsCompression(BattleCombatant ai, Move move) {
        return MoveAvailability.guaranteedBoundedResourceTransactions(ai, move).stream().anyMatch(e ->
            COMPRESSION.equalsIgnoreCase(e.targetResourceKey)
                && e.targetResourceAmount != null && e.targetResourceAmount > 0);
    }

    private static boolean spendsResource(BattleCombatant ai, Move move, String key) {
        return MoveAvailability.guaranteedBoundedResourceTransactions(ai, move).stream().anyMatch(e ->
            key.equalsIgnoreCase(e.sourceResourceKey)
                && e.sourceResourceAmount != null && e.sourceResourceAmount > 0);
    }

    private static boolean isSurge(Move move) {
        return !move.hasTag("ATTACK") && move.getEffects().stream().anyMatch(
            BloodManipulationAIStrategy::isCoreBoost);
    }

    private static boolean isCoreBoost(AbilityEffectData effect) {
        return isPositiveSelfModifier(effect)
            && AbilityEffectType.statType(effect) == AbilityEffectType.StatType.CORE;
    }

    private static boolean isAccuracyFocus(Move move) {
        return !move.hasTag("ATTACK") && move.getEffects().stream().anyMatch(
            BloodManipulationAIStrategy::isAccuracyBoost);
    }

    private static boolean isAccuracyBoost(AbilityEffectData effect) {
        return isPositiveSelfModifier(effect)
            && AbilityEffectType.statType(effect) == AbilityEffectType.StatType.BATTLE
            && "ACCURACY".equalsIgnoreCase(effect.stringValue);
    }

    private static boolean isPositiveSelfModifier(AbilityEffectData effect) {
        return AbilityEffectType.TIMED_STAT_MODIFIER.name().equalsIgnoreCase(effect.type)
            && "SELF".equalsIgnoreCase(effect.target)
            && effect.doubleValue != null && effect.doubleValue > 0;
    }

    private static boolean isSelfNeverMiss(AbilityEffectData effect) {
        return AbilityEffectType.APPLY_NEVER_MISS.name().equalsIgnoreCase(effect.type)
            && "SELF".equalsIgnoreCase(effect.target);
    }

    private static boolean grantsNeverMiss(Move move) {
        return move.getEffects().stream().anyMatch(BloodManipulationAIStrategy::isSelfNeverMiss);
    }

    private static boolean restrainsTarget(Move move) {
        return move.getEffects().stream().anyMatch(e ->
            AbilityEffectType.APPLY_STATUS.name().equalsIgnoreCase(e.type)
                && StatusEffectType.RESTRAINED.name().equalsIgnoreCase(e.stringValue));
    }
}
