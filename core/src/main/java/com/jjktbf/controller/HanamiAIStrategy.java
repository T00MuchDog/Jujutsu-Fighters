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
import com.jjktbf.model.move.StatusEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resource-aware planner for the Disaster Plants technique. */
public class HanamiAIStrategy implements AIStrategy {

    private static final String FLOWER_OFFERING = "FLOWER_OFFERING";
    private static final int ATTACK_CAP = 2;

    public BattlePlan buildPlan(BattleState state, BattleCombatant ai, RandomSource rng) {
        return placeMoves(state, ai, state.firstActiveEnemyOf(ai), rng);
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

        List<Move> beams = new ArrayList<>();
        List<Move> offeringGenerators = new ArrayList<>();
        List<Move> attacks = new ArrayList<>();
        List<Move> controls = new ArrayList<>();
        List<Move> defenses = new ArrayList<>();
        for (Move move : ai.getCharacter().getKnownMoves()) {
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

        int offering = resourceValue(ai, FLOWER_OFFERING);
        int capacity = resourceCapacity(ai, FLOWER_OFFERING);
        int attackCursor = 1;
        int attacksPlaced = 0;

        Move beam = strongest(beams);
        if (beam != null && offering > 0 && available(state, ai, beam)) {
            ActionSegment segment = SmartAIScoring.placeAtOrAfter(
                plan, beam, ai.computeMoveCeCost(beam), attackCursor);
            if (segment != null) {
                attacksPlaced++;
                attackCursor = segment.getEndTick() + 1;
                offering = 0;
            }
        } else if (beam != null && offering == 0) {
            Move generator = cheapestAvailable(state, ai, offeringGenerators);
            if (generator != null) {
                ActionSegment setup = SmartAIScoring.placeAtOrAfter(
                    plan, generator, ai.computeMoveCeCost(generator), attackCursor);
                if (setup != null) {
                    offering = Math.min(capacity, offering + 1);
                    attackCursor = setup.getEndTick() + 1;
                    if (plan.canPlace(beam, ai.computeMoveCeCost(beam))) {
                        ActionSegment finisher = SmartAIScoring.placeAtOrAfter(
                            plan, beam, ai.computeMoveCeCost(beam), attackCursor);
                        if (finisher != null) {
                            attacksPlaced++;
                            attackCursor = finisher.getEndTick() + 1;
                            offering = 0;
                        }
                    }
                }
            }
        }

        if (offering < capacity && attacksPlaced == 0 && beam != null) {
            Move generator = cheapestAvailable(state, ai, offeringGenerators);
            if (generator != null && plan.canPlace(generator, ai.computeMoveCeCost(generator))) {
                ActionSegment setup = SmartAIScoring.placeAtOrAfter(
                    plan, generator, ai.computeMoveCeCost(generator), attackCursor);
                if (setup != null) attackCursor = setup.getEndTick() + 1;
            }
        }

        if (!controls.isEmpty() && rng.nextDouble() < controlChance(state, ai)
            && attacksPlaced < ATTACK_CAP) {
            Move control = cheapestAvailable(state, ai, controls);
            if (control != null) {
                ActionSegment segment = SmartAIScoring.placeAtOrAfter(
                    plan, control, ai.computeMoveCeCost(control), attackCursor);
                if (segment != null) attackCursor = segment.getEndTick() + 1;
            }
        }

        Set<Move> stuck = new HashSet<>();
        while (attacksPlaced < ATTACK_CAP) {
            List<Move> pool = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (Move attack : attacks) {
                if (stuck.contains(attack) || !available(state, ai, attack)
                    || !plan.canPlace(attack, ai.computeMoveCeCost(attack))) {
                    continue;
                }
                pool.add(attack);
                weights.add(attackWeight(attack, ai, opponent, intel));
            }
            Move attack = SmartAIScoring.weightedRandomPick(pool, weights, rng);
            if (attack == null) break;
            ActionSegment segment = SmartAIScoring.placeAtOrAfter(
                plan, attack, ai.computeMoveCeCost(attack), attackCursor);
            if (segment == null) {
                stuck.add(attack);
            } else {
                attacksPlaced++;
                attackCursor = segment.getEndTick() + 1;
            }
        }

        placeDefense(ai, opponent, plan, defenses, intel, state);
        return plan;
    }

    private static void placeDefense(
        BattleCombatant ai,
        BattleCombatant opponent,
        BattlePlan plan,
        List<Move> defenses,
        OpponentIntel intel,
        BattleState state
    ) {
        Move defense = defenses.stream()
            .filter(move -> available(state, ai, move))
            .filter(move -> plan.canPlace(move, ai.computeMoveCeCost(move)))
            .max(Comparator.comparingDouble(move -> SmartAIScoring.defenseValue(move, intel)))
            .orElse(null);
        if (defense == null || SmartAIScoring.defenseValue(defense, intel) <= 0.0) return;
        int ceCost = ai.computeMoveCeCost(defense);
        Integer threat = intel.committedAttackFireTicks.isEmpty()
            ? null : intel.committedAttackFireTicks.get(0);
        ActionSegment placed = threat == null ? null
            : SmartAIScoring.placeAlignedToThreat(
                plan, defense, ceCost, threat, ai, opponent);
        if (placed == null) SmartAIScoring.placeAtOrAfter(plan, defense, ceCost, 1);
    }

    private static double attackWeight(
        Move move,
        BattleCombatant ai,
        BattleCombatant opponent,
        OpponentIntel intel
    ) {
        double weight = Math.max(1, move.getTotalBasePower());
        weight *= SmartAIScoring.effectMultiplier(move);
        weight *= SmartAIScoring.dodgeExposureMultiplier(move, intel);
        weight *= SmartAIScoring.reinforcementAttackMultiplier(move, intel);
        weight *= SmartAIScoring.defenseCrackMultiplier(move, intel);
        if (appliesStatus(move, StatusEffectType.CURSED_ENERGY_PARASITE)) {
            if (opponent == null || !opponent.hasAnyCe()
                || opponent.hasEffect(StatusEffectType.CURSED_ENERGY_PARASITE)) {
                weight *= 0.25;
            } else {
                weight *= 1.8;
            }
        }
        if (appliesStatus(move, StatusEffectType.RESTRAINED)
            && opponent != null
            && opponent.getRuntimeStat(StatKey.SPEED) > ai.getRuntimeStat(StatKey.SPEED)) {
            weight *= 1.5;
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

    private static Move strongest(List<Move> moves) {
        return moves.stream().max(Comparator.comparingInt(Move::getTotalBasePower)).orElse(null);
    }

    private static Move cheapestAvailable(
        BattleState state,
        BattleCombatant ai,
        List<Move> moves
    ) {
        return moves.stream()
            .filter(move -> available(state, ai, move))
            .min(Comparator.comparingInt(ai::computeMoveCeCost)
                .thenComparingInt(Move::getApCost))
            .orElse(null);
    }

    private static boolean available(BattleState state, BattleCombatant ai, Move move) {
        return MoveAvailability.isAvailable(null, ai, move)
            && (state == null || MoveAvailability.isAvailable(state, ai, move));
    }

    private static double controlChance(BattleState state, BattleCombatant ai) {
        if (state != null && state.activeEnemiesOf(ai).size() > 1) return 0.8;
        return 0.35;
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
