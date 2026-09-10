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
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.AoeType;
import com.jjktbf.model.move.StatusEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Disaster Plants starts reserved, building pressure and Flower Offering, then
 * grows more passionate each round. Major attacks are held for late battle or
 * an injured, cornered state. All opponent reads use visible state and kit intel.
 */
public class HanamiAIStrategy implements AIStrategy {

    private static final String FLOWER_OFFERING = "FLOWER_OFFERING";
    private static final int BIG_MOVE_ROUND = 4;
    private static final int FULL_PASSION_ROUND = 5;
    private static final double CORNERED_HP = 0.35;

    @Override
    public BattlePlan buildPlan(BattleState state, BattleCombatant ai, RandomSource rng) {
        return placeMoves(state, ai, state.firstActiveEnemyOf(ai), rng);
    }

    @Override
    public boolean allowsOpening(BattleState state, BattleCombatant ai, Move move) {
        return !holdsInReserve(state, ai, move);
    }

    @Override
    public BattlePlan selectPlan(
        BattleCombatant ai,
        BattleCombatant opponent,
        RandomSource rng
    ) {
        return placeMoves(null, ai, opponent, rng);
    }

    private BattlePlan placeMoves(
        BattleState state,
        BattleCombatant ai,
        BattleCombatant opponent,
        RandomSource rng
    ) {
        int gridLength = Timeline.gridLengthForStrongestAp(
            Math.max(ai.getMaxApBar(), opponent == null ? 0 : opponent.getMaxApBar()));
        BattlePlan plan = BattlePlan.forCombatant(ai, gridLength);
        OpponentIntel intel = OpponentIntel.forOpponent(opponent);
        int passion = passion(state, ai);
        int attackCap = passion == 4 ? Integer.MAX_VALUE : 2 + passion;
        int attackApLimit = (int) Math.round(plan.apBudget() * (0.45 + 0.1375 * passion));

        List<Move> beams = new ArrayList<>();
        List<Move> offeringGenerators = new ArrayList<>();
        List<Move> attacks = new ArrayList<>();
        List<Move> controls = new ArrayList<>();
        List<Move> defenses = new ArrayList<>();
        for (Move move : ai.getCharacter().getKnownMoves()) {
            if (AIStrategy.isAiUnsupported(move)
                || MoveAvailability.restrictionReasonWithoutBoundedResources(state, ai, move) != null) continue;
            if (consumesOfferingForPower(ai, move)) {
                beams.add(move);
            } else if (gainsOffering(ai, move)) {
                offeringGenerators.add(move);
            } else if (move.isActiveDefense()) {
                defenses.add(move);
            } else if (move.hasTag("ATTACK")) {
                attacks.add(move);
            } else if (locksEnemyAttacks(move)) {
                controls.add(move);
            }
        }

        int attackCursor = 1;
        int attacksPlaced = 0;
        int attackApUsed = 0;
        boolean unleashed = !holdsBigMoves(state, ai);
        int desiredOffering = Math.min(2, resourceCapacity(ai, FLOWER_OFFERING));
        boolean spendLoaded = unleashed && resourceValue(ai, FLOWER_OFFERING) > 0
            && (resourceValue(ai, FLOWER_OFFERING) >= desiredOffering || passion == 4);

        // Bank at most one charge per round while reserved. Do not buy setup at
        // the cost of all pressure, or if its eventual beam cannot be afforded.
        Move beam = beams.stream()
            .filter(move -> ai.computeMoveCeCost(move) <= plan.remainingCe())
            .filter(move -> plan.effectiveApCost(move) <= plan.apBudget())
            .max(Comparator.comparingDouble(move -> attackWeight(move, ai, opponent, intel, passion, plan)))
            .orElse(null);
        ActionSegment generatorSegment = null;
        if (beam != null && !spendLoaded) {
            Move generator = cheapestAvailable(state, ai, offeringGenerators, plan);
            if (generator != null) {
                int pressureRoom = attacks.stream()
                    .filter(move -> !holdsInReserve(state, ai, move))
                    .filter(move -> available(state, ai, move, plan))
                    .mapToInt(plan::effectiveApCost).min().orElse(0);
                if (plan.remainingApBudget() - plan.effectiveApCost(generator) >= pressureRoom
                    && (!unleashed || plan.remainingCe() - ai.computeMoveCeCost(generator)
                        >= ai.computeMoveCeCost(beam))) {
                    generatorSegment = placeAvailable(state, ai, plan, generator, 1);
                    if (generatorSegment != null) {
                        boolean canStillPressure = attacks.stream()
                            .filter(move -> !holdsInReserve(state, ai, move))
                            .anyMatch(move -> available(state, ai, move, plan)
                                && plan.canPlace(move, ai.computeMoveCeCost(move)));
                        if (!unleashed && pressureRoom > 0 && !canStillPressure) {
                            plan.remove(generatorSegment);
                            generatorSegment = null;
                        } else {
                            attackCursor = generatorSegment.getFireTick() + 1;
                        }
                    }
                }
            }
        }

        int offering = projectedOffering(ai, plan);
        if (beam != null && unleashed && offering > 0
            && (offering >= desiredOffering || passion == 4)) {
            ActionSegment segment = placeAvailable(state, ai, plan, beam, attackCursor);
            if (segment != null) {
                attacksPlaced++;
                attackApUsed += segment.getApCost();
                attackCursor = segment.getEndTick() + 1;
            } else if (generatorSegment != null && isCornered(ai)) {
                // In an emergency, a failed charge-and-fire must not consume the
                // AP needed to fight back with an ordinary attack.
                plan.remove(generatorSegment);
                attackCursor = 1;
            }
        }

        if (unleashed && !intel.attacks.isEmpty()
            && rng.nextDouble() < controlChance(state, ai, passion)) {
            Move control = cheapestAvailable(state, ai, controls, plan);
            if (control != null && plan.remainingApBudget() - plan.effectiveApCost(control)
                >= cheapestAttackAp(attacks, state, ai, plan)) {
                ActionSegment segment = placeAvailable(state, ai, plan, control, 1);
                if (segment != null) attackCursor = Math.max(attackCursor, segment.getFireTick() + 1);
            }
        }

        Set<Move> stuck = new HashSet<>();
        while (attacksPlaced < attackCap) {
            List<Move> pool = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (Move attack : attacks) {
                if (stuck.contains(attack) || holdsInReserve(state, ai, attack)
                    || !available(state, ai, attack, plan)
                    || !plan.canPlace(attack, ai.computeMoveCeCost(attack))) {
                    continue;
                }
                // Always allow a first ordinary attack when it is the only way
                // to pressure; a reserved personality must not mean doing nothing.
                if (attacksPlaced > 0 && attackApUsed + plan.effectiveApCost(attack) > attackApLimit) continue;
                pool.add(attack);
                weights.add(attackWeight(attack, ai, opponent, intel, passion, plan));
            }
            Move attack = SmartAIScoring.weightedRandomPick(pool, weights, rng);
            if (attack == null) break;
            ActionSegment segment = placeAvailable(state, ai, plan, attack, attackCursor);
            if (segment == null) {
                stuck.add(attack);
            } else {
                attacksPlaced++;
                attackApUsed += segment.getApCost();
                attackCursor = segment.getEndTick() + 1;
            }
        }

        placeDefense(ai, opponent, plan, defenses, intel, state, passion);
        return plan;
    }

    private static void placeDefense(
        BattleCombatant ai,
        BattleCombatant opponent,
        BattlePlan plan,
        List<Move> defenses,
        OpponentIntel intel,
        BattleState state,
        int passion
    ) {
        Move defense = defenses.stream()
            .filter(move -> available(state, ai, move, plan))
            .filter(move -> plan.canPlace(move, ai.computeMoveCeCost(move)))
            .filter(move -> plan.effectiveApCost(move) <= plan.apBudget() * (0.30 - passion * 0.025))
            .max(Comparator.comparingDouble(move ->
                SmartAIScoring.defenseValue(move, intel) / plan.effectiveApCost(move)))
            .orElse(null);
        if (defense == null || SmartAIScoring.defenseValue(defense, intel) <= 0.0) return;
        int lead = opponent != null
            && ai.getRuntimeStat(StatKey.SPEED) <= opponent.getRuntimeStat(StatKey.SPEED) ? 1 : 0;
        for (int threat : intel.anticipatedAttackFireTicks) {
            int start = threat - lead - plan.effectiveUnleashPoint(defense) + 1;
            if (start < 1) continue;
            ActionSegment placed = placeAvailable(state, ai, plan, defense, start);
            if (placed == null) continue;
            if (placed.getFireTick() <= threat - lead) return;
            plan.remove(placed);
        }
    }

    private static double attackWeight(
        Move move,
        BattleCombatant ai,
        BattleCombatant opponent,
        OpponentIntel intel,
        int passion,
        BattlePlan plan
    ) {
        double weight = Math.max(1, opponent == null ? move.getTotalBasePower()
            : SmartAIScoring.estimatedDamage(move, ai, opponent));
        weight /= Math.pow(plan.effectiveApCost(move), 1.0 - passion * 0.15);
        weight /= 1.0 + (0.8 - passion * 0.15)
            * ai.computeMoveCeCost(move) / Math.max(1.0, plan.remainingCe());
        if (isMajorAttack(ai, move)) weight *= 1.0 + passion * 0.4;
        weight *= SmartAIScoring.effectMultiplier(move);
        weight *= SmartAIScoring.dodgeExposureMultiplier(move, intel);
        weight *= SmartAIScoring.reinforcementAttackMultiplier(move, intel);
        weight *= SmartAIScoring.defenseCrackMultiplier(move, intel);
        if (appliesStatus(move, StatusEffectType.CURSED_ENERGY_PARASITE)) {
            if (opponent == null || !opponent.hasAnyCe()
                || opponent.hasEffect(StatusEffectType.CURSED_ENERGY_PARASITE)
                || plannedStatus(plan, StatusEffectType.CURSED_ENERGY_PARASITE)) {
                weight *= 0.25;
            } else {
                weight *= 3.0 - passion * 0.35;
            }
        }
        if (appliesStatus(move, StatusEffectType.RESTRAINED) && opponent != null) {
            if (opponent.hasEffect(StatusEffectType.RESTRAINED)
                || plannedStatus(plan, StatusEffectType.RESTRAINED)) weight *= 0.65;
            else if (opponent.getRuntimeStat(StatKey.SPEED) > ai.getRuntimeStat(StatKey.SPEED)) weight *= 1.5;
        }
        if (opponent != null
            && SmartAIScoring.estimatedDamage(move, ai, opponent) >= opponent.getCurrentHp()) {
            weight *= 3.0;
        }
        return weight;
    }

    private static boolean consumesOfferingForPower(BattleCombatant ai, Move move) {
        return MoveAvailability.guaranteedBoundedResourcePowerConsumers(ai, move).stream()
            .anyMatch(effect -> FLOWER_OFFERING.equalsIgnoreCase(effect.sourceResourceKey));
    }

    private static boolean gainsOffering(BattleCombatant ai, Move move) {
        return MoveAvailability.guaranteedBoundedResourceTransactions(ai, move).stream()
            .anyMatch(effect -> FLOWER_OFFERING.equalsIgnoreCase(effect.targetResourceKey)
                && value(effect.targetResourceAmount) > 0);
    }

    private static boolean locksEnemyAttacks(Move move) {
        return move.getEffects().stream().anyMatch(effect ->
            AbilityEffectType.TEMP_LOCK_MOVE_TAG.name().equalsIgnoreCase(effect.type)
                && "ATTACK".equalsIgnoreCase(effect.moveTag)
                && "ENEMY".equalsIgnoreCase(effect.target));
    }

    private static boolean appliesStatus(Move move, StatusEffectType status) {
        return move.getEffects().stream().anyMatch(effect ->
            AbilityEffectType.APPLY_STATUS.name().equalsIgnoreCase(effect.type)
                && status.name().equalsIgnoreCase(effect.stringValue));
    }

    /** Stateless progression: repeated planning cannot accelerate her personality. */
    private static int passion(BattleState state, BattleCombatant ai) {
        if (isCornered(ai)) return FULL_PASSION_ROUND - 1;
        return Math.max(0, Math.min(FULL_PASSION_ROUND - 1,
            (state == null ? 1 : state.getRoundNumber()) - 1));
    }

    private static boolean isCornered(BattleCombatant ai) {
        return (double) ai.getCurrentHp() / Math.max(1, ai.getMaxHp()) <= CORNERED_HP;
    }

    private static boolean holdsBigMoves(BattleState state, BattleCombatant ai) {
        return !isCornered(ai) && (state == null || state.getRoundNumber() < BIG_MOVE_ROUND);
    }

    /** Also used by the dispatcher's finishing-shot promotion to respect her restraint. */
    static boolean holdsInReserve(BattleState state, BattleCombatant ai, Move move) {
        return holdsBigMoves(state, ai) && (isMajorAttack(ai, move) || locksEnemyAttacks(move));
    }

    private static boolean isMajorAttack(BattleCombatant ai, Move move) {
        return consumesOfferingForPower(ai, move)
            || (move.hasTag("ATTACK") && (move.getPotency() >= 3
                || (move.isAoe() && move.getAoeType() != AoeType.MULTIPLE)
                || move.getHitComponents().stream().anyMatch(hit ->
                    hit.getBasePower() > 0 && !hit.isAvoidable())));
    }

    private static boolean plannedStatus(BattlePlan plan, StatusEffectType status) {
        return plan.allSegments().stream().anyMatch(s -> appliesStatus(s.getMove(), status));
    }

    private static List<Move> movesIn(BattlePlan plan) {
        return plan.allSegments().stream().map(ActionSegment::getMove).toList();
    }

    private static int cheapestAttackAp(List<Move> moves, BattleState state,
                                        BattleCombatant ai, BattlePlan plan) {
        return moves.stream().filter(move -> available(state, ai, move, plan))
            .mapToInt(plan::effectiveApCost).min().orElse(0);
    }

    private static ActionSegment placeAvailable(BattleState state, BattleCombatant ai,
                                                BattlePlan plan, Move move, int fromTick) {
        if (usesResources(ai, move)) {
            for (ActionSegment segment : plan.allSegments()) {
                if (usesResources(ai, segment.getMove())) {
                    fromTick = Math.max(fromTick, segment.getFireTick() + 1);
                }
            }
        }
        ActionSegment placed = SmartAIScoring.placeAtOrAfter(
            plan, move, ai.computeMoveCeCost(move), fromTick);
        if (placed == null) return null;
        List<Move> earlier = new ArrayList<>();
        for (ActionSegment segment : plan.allSegments()) {
            if (MoveAvailability.restrictionReason(state, ai, segment.getMove(), earlier) != null) {
                plan.remove(placed);
                return null;
            }
            earlier.add(segment.getMove());
        }
        return placed;
    }

    private static boolean usesResources(BattleCombatant ai, Move move) {
        return !MoveAvailability.guaranteedBoundedResourceTransactions(ai, move).isEmpty()
            || !MoveAvailability.guaranteedBoundedResourcePowerConsumers(ai, move).isEmpty();
    }

    private static int projectedOffering(BattleCombatant ai, BattlePlan plan) {
        int offering = resourceValue(ai, FLOWER_OFFERING);
        for (Move move : movesIn(plan)) {
            for (AbilityEffectData effect : MoveAvailability.guaranteedBoundedResourceTransactions(ai, move)) {
                if (FLOWER_OFFERING.equalsIgnoreCase(effect.sourceResourceKey)) offering -= value(effect.sourceResourceAmount);
                if (FLOWER_OFFERING.equalsIgnoreCase(effect.targetResourceKey)) offering += value(effect.targetResourceAmount);
            }
            if (consumesOfferingForPower(ai, move)) offering = 0;
        }
        return offering;
    }

    private static Move cheapestAvailable(
        BattleState state,
        BattleCombatant ai,
        List<Move> moves,
        BattlePlan plan
    ) {
        return moves.stream()
            .filter(move -> available(state, ai, move, plan))
            .filter(move -> plan.canPlace(move, ai.computeMoveCeCost(move)))
            .min(Comparator.comparingInt(ai::computeMoveCeCost)
                .thenComparingInt(Move::getApCost))
            .orElse(null);
    }

    private static boolean available(BattleState state, BattleCombatant ai, Move move, BattlePlan plan) {
        return MoveAvailability.restrictionReason(state, ai, move, movesIn(plan)) == null;
    }

    private static double controlChance(BattleState state, BattleCombatant ai, int passion) {
        if (isCornered(ai)) return 0.75;
        if (state != null && state.activeEnemiesOf(ai).size() > 1) return 0.65;
        return 0.65 - passion * 0.10;
    }

    private static int resourceValue(BattleCombatant ai, String key) {
        return ai.boundedResourceValue(key).orElse(0);
    }

    private static int resourceCapacity(BattleCombatant ai, String key) {
        return ai.abilityState(key).map(state -> state.maximumValue()).orElse(0);
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
