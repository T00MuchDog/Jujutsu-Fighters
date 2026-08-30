package com.jjktbf.model.combat;

import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.character.AbilityConditionData;
import com.jjktbf.model.character.AbilityConditionType;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.move.StatusEffectType;
import com.jjktbf.model.progression.TechniqueMasteryResolver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared battle-time policy for whether an actor's move would currently work.
 * Planning is never gated on this: players may place any move on the timeline,
 * and moves that would not work fail at their first tick instead. The policy
 * remains the input for AI move selection and for the resolver's start-tick
 * failure checks.
 */
public final class MoveAvailability {

    private MoveAvailability() { }

    public static boolean isAvailable(BattleState state, BattleCombatant actor, Move move) {
        return restrictionReason(state, actor, move) == null;
    }

    public static String restrictionReason(
        BattleState state,
        BattleCombatant actor,
        Move move
    ) {
        return restrictionReason(state, actor, move, List.of());
    }

    /**
     * As {@link #restrictionReason(BattleState, BattleCombatant, Move)}, with
     * summons already reserved by earlier placements in the actor's round plan.
     */
    public static String restrictionReason(
        BattleState state,
        BattleCombatant actor,
        Move move,
        List<Move> alreadyPlannedMoves
    ) {
        return restrictionReason(state, actor, move, alreadyPlannedMoves, true);
    }

    /** Availability checks that do not depend on the actor's current bounded resources. */
    public static String restrictionReasonWithoutBoundedResources(
        BattleState state,
        BattleCombatant actor,
        Move move
    ) {
        return restrictionReason(state, actor, move, List.of(), false);
    }

    public static String restrictionReasonWithoutBoundedResources(
        BattleState state,
        BattleCombatant actor,
        Move move,
        List<Move> alreadyPlannedMoves
    ) {
        return restrictionReason(state, actor, move, alreadyPlannedMoves, false);
    }

    private static String restrictionReason(
        BattleState state,
        BattleCombatant actor,
        Move move,
        List<Move> alreadyPlannedMoves,
        boolean checkBoundedResources
    ) {
        if (actor == null || move == null) return "A valid actor and move are required.";
        if (actor.hasEffect(StatusEffectType.SLEEP)) {
            return "Cannot act while asleep.";
        }
        if (actor.getAbilityFlags().lockedMoveTags.stream().anyMatch(move::hasTag)) {
            return "Restricted by an active ability.";
        }
        if (actor.isTechniqueLocked(move.getRequiredTechniqueId())) {
            return "The required technique is temporarily unavailable.";
        }
        if (checkBoundedResources) {
            String resourceRestriction = boundedResourceRestrictionReason(
                actor, move, alreadyPlannedMoves);
            if (resourceRestriction != null) return resourceRestriction;
        }
        if (state != null) {
            String activeSummonRestriction = activeOwnedSummonRestrictionReason(
                state, actor, move);
            if (activeSummonRestriction != null) return activeSummonRestriction;
            for (String definitionId : summonedDefinitionIds(move)) {
                String reason = state.summonRestrictionReason(actor, definitionId);
                if (reason != null) return reason;
            }
        }
        int occupiedSlots = state == null ? 0
            : state.directActiveSummonCount(actor) + state.directPendingSummonCount(actor);
        return plannedSummonRestrictionReason(
            move, alreadyPlannedMoves, actor.getAbilityFlags().maxActiveSummons, occupiedSlots);
    }

    /** Restriction contributed by move rows tied to an already-deployed owned summon. */
    public static String activeOwnedSummonRestrictionReason(
        BattleState state,
        BattleCombatant actor,
        Move move
    ) {
        if (state == null || actor == null || move == null || !move.usesUnifiedEffects()) {
            return null;
        }
        for (MoveEffectData effect : move.getEffects()) {
            if (!AbilityEffectType.MOVE_UNAVAILABLE_WHILE_OWNED_SUMMON_ACTIVE.name()
                .equalsIgnoreCase(effect.type)) continue;
            if (state.hasDirectActiveSummonDefinition(actor, effect.characterId)) {
                return "This move cannot be used while its shikigami is active on the field.";
            }
        }
        return null;
    }

    /** Restriction contributed only by summons reserved in the current draft. */
    public static String plannedSummonRestrictionReason(
        Move move,
        List<Move> alreadyPlannedMoves,
        Integer maximumActiveSummons,
        int occupiedSlots
    ) {
        if (move == null || maximumActiveSummons == null) return null;
        Set<String> reservedDefinitions = new LinkedHashSet<>();
        if (alreadyPlannedMoves != null) {
            for (Move planned : alreadyPlannedMoves) {
                reservedDefinitions.addAll(summonedDefinitionIds(planned));
            }
        }
        Set<String> candidateDefinitions = new LinkedHashSet<>();
        for (String definitionId : summonedDefinitionIds(move)) {
            if (reservedDefinitions.contains(definitionId)
                || !candidateDefinitions.add(definitionId)) {
                return "This shikigami is already active or pending in this round's plan.";
            }
            if (occupiedSlots + reservedDefinitions.size() + candidateDefinitions.size()
                > maximumActiveSummons) {
                return "Maximum active summons reached.";
            }
        }
        return null;
    }

    /** Restriction contributed by guaranteed move-start resource transactions. */
    public static String boundedResourceRestrictionReason(
        BattleCombatant actor,
        Move move,
        List<Move> alreadyPlannedMoves
    ) {
        if (actor == null || move == null) return null;
        Map<String, Integer> values = new LinkedHashMap<>();
        Map<String, Integer> maximums = new LinkedHashMap<>();
        if (alreadyPlannedMoves != null) {
            for (Move planned : alreadyPlannedMoves) {
                String reason = applyBoundedResourceTransactions(
                    actor, planned, values, maximums);
                if (reason != null) return reason;
            }
        }
        return applyBoundedResourceTransactions(actor, move, values, maximums);
    }

    /** Validate a complete chronological move sequence against the actor's current resources. */
    public static String boundedResourcePlanRestrictionReason(
        BattleCombatant actor,
        List<Move> plannedMoves
    ) {
        if (actor == null) return null;
        Map<String, Integer> values = new LinkedHashMap<>();
        Map<String, Integer> maximums = new LinkedHashMap<>();
        if (plannedMoves == null) return null;
        for (Move planned : plannedMoves) {
            String reason = applyBoundedResourceTransactions(
                actor, planned, values, maximums);
            if (reason != null) return reason;
        }
        return null;
    }

    /** Client-side equivalent using bounded-resource states supplied by the server. */
    public static String boundedResourceRestrictionReason(
        List<CodedAbilityState> states,
        Move move,
        List<Move> alreadyPlannedMoves
    ) {
        if (move == null) return null;
        Map<String, Integer> values = new LinkedHashMap<>();
        Map<String, Integer> maximums = new LinkedHashMap<>();
        if (states != null) {
            for (CodedAbilityState state : states) {
                if (state == null || state.key() == null || state.key().isBlank()) continue;
                String key = normalizedResourceKey(state.key());
                values.put(key, state.currentValue());
                maximums.put(key, state.maximumValue());
            }
        }
        if (alreadyPlannedMoves != null) {
            for (Move planned : alreadyPlannedMoves) {
                String reason = applyBoundedResourceTransactions(
                    null, planned, values, maximums);
                if (reason != null) return reason;
            }
        }
        return applyBoundedResourceTransactions(null, move, values, maximums);
    }

    /** Validate a complete chronological move sequence from wire-visible resource states. */
    public static String boundedResourcePlanRestrictionReason(
        List<CodedAbilityState> states,
        List<Move> plannedMoves
    ) {
        Map<String, Integer> values = new LinkedHashMap<>();
        Map<String, Integer> maximums = new LinkedHashMap<>();
        if (states != null) {
            for (CodedAbilityState state : states) {
                if (state == null || state.key() == null || state.key().isBlank()) continue;
                String key = normalizedResourceKey(state.key());
                values.put(key, state.currentValue());
                maximums.put(key, state.maximumValue());
            }
        }
        if (plannedMoves == null) return null;
        for (Move planned : plannedMoves) {
            String reason = applyBoundedResourceTransactions(
                null, planned, values, maximums);
            if (reason != null) return reason;
        }
        return null;
    }

    private static String applyBoundedResourceTransactions(
        BattleCombatant actor,
        Move move,
        Map<String, Integer> values,
        Map<String, Integer> maximums
    ) {
        for (AbilityEffectData transaction : boundedResourceTransactions(actor, move)) {
            int sourceAmount = transaction.sourceResourceAmount == null
                ? 0 : transaction.sourceResourceAmount;
            int targetAmount = transaction.targetResourceAmount == null
                ? 0 : transaction.targetResourceAmount;
            String sourceKey = sourceAmount == 0
                ? null : normalizedResourceKey(transaction.sourceResourceKey);
            String targetKey = targetAmount == 0
                ? null : normalizedResourceKey(transaction.targetResourceKey);
            if (sourceAmount > 0 && sourceKey == null) {
                return "Required resource is not available.";
            }
            if (targetAmount > 0 && targetKey == null) {
                return "The resource transaction cannot be completed.";
            }
            Integer sourceValue = sourceKey == null ? null
                : resourceValue(actor, sourceKey, values, maximums);
            Integer targetValue = targetKey == null ? null
                : resourceValue(actor, targetKey, values, maximums);
            if (sourceKey != null && sourceValue == null) {
                return "Required resource is not available: " + sourceKey + ".";
            }
            if (targetKey != null && targetValue == null) {
                return "The resource transaction cannot be completed.";
            }
            if (sourceValue != null && sourceValue < sourceAmount) {
                return "Not enough " + sourceKey + ".";
            }
            if (sourceKey != null && sourceKey.equals(targetKey)) {
                long finalValue = (long) sourceValue - sourceAmount + targetAmount;
                if (finalValue < 0L || finalValue > maximums.get(sourceKey)) {
                    return "The resource transaction cannot be completed.";
                }
                values.put(sourceKey, (int) finalValue);
                continue;
            }
            if (targetValue != null && (long) targetValue + targetAmount > maximums.get(targetKey)) {
                return "The resource transaction cannot be completed.";
            }
            if (sourceKey != null) values.put(sourceKey, sourceValue - sourceAmount);
            if (targetKey != null) values.put(targetKey, targetValue + targetAmount);
        }
        for (AbilityEffectData consumer : boundedResourcePowerConsumers(actor, move)) {
            String sourceKey = normalizedResourceKey(consumer.sourceResourceKey);
            if (sourceKey == null) return "Required resource is not available.";
            Integer sourceValue = resourceValue(actor, sourceKey, values, maximums);
            if (sourceValue == null) {
                return "Required resource is not available: " + sourceKey + ".";
            }
            if (sourceValue <= 0) return "Not enough " + sourceKey + ".";
            values.put(sourceKey, 0);
        }
        return null;
    }

    private static Integer resourceValue(
        BattleCombatant actor,
        String key,
        Map<String, Integer> values,
        Map<String, Integer> maximums
    ) {
        if (values.containsKey(key)) return values.get(key);
        if (actor == null) return null;
        var current = actor.boundedResourceValue(key);
        if (current.isEmpty()) return null;
        var state = actor.abilityState(key).orElseThrow();
        values.put(key, current.getAsInt());
        maximums.put(key, state.maximumValue());
        return current.getAsInt();
    }

    public static List<AbilityEffectData> guaranteedBoundedResourceTransactions(
        BattleCombatant actor,
        Move move
    ) {
        if (actor == null || move == null || !move.usesUnifiedEffects()) return List.of();
        int mastery = TechniqueMasteryResolver.masteryOf(actor);
        return move.getEffects().stream()
            .filter(effect -> AbilityEffectType.TRANSACT_BOUNDED_RESOURCE.name()
                .equalsIgnoreCase(effect.type))
            .filter(effect -> effect.resolvedTrigger() == MoveEffectTrigger.ON_START
                || effect.resolvedTrigger() == MoveEffectTrigger.ON_FIRE)
            .filter(effect -> guaranteed(effect, mastery))
            .map(effect -> TechniqueMasteryResolver.resolve(effect, mastery))
            .toList();
    }

    private static List<AbilityEffectData> boundedResourceTransactions(
        BattleCombatant actor,
        Move move
    ) {
        if (actor != null) return guaranteedBoundedResourceTransactions(actor, move);
        if (move == null || !move.usesUnifiedEffects()) return List.of();
        return move.getEffects().stream()
            .filter(effect -> AbilityEffectType.TRANSACT_BOUNDED_RESOURCE.name()
                .equalsIgnoreCase(effect.type))
            .filter(effect -> effect.resolvedTrigger() == MoveEffectTrigger.ON_START
                || effect.resolvedTrigger() == MoveEffectTrigger.ON_FIRE)
            .filter(effect -> guaranteed(effect, 0))
            .map(effect -> (AbilityEffectData) effect)
            .toList();
    }

    /** Guaranteed consume-all rows that derive one move's base power from a resource. */
    public static List<AbilityEffectData> guaranteedBoundedResourcePowerConsumers(
        BattleCombatant actor,
        Move move
    ) {
        return boundedResourcePowerConsumers(actor, move);
    }

    private static List<AbilityEffectData> boundedResourcePowerConsumers(
        BattleCombatant actor,
        Move move
    ) {
        if (move == null || !move.usesUnifiedEffects()) return List.of();
        int mastery = actor == null ? 0 : TechniqueMasteryResolver.masteryOf(actor);
        return move.effectsFor(MoveEffectTrigger.ON_START, -1).stream()
            .filter(effect -> AbilityEffectType.CONSUME_BOUNDED_RESOURCE_FOR_BASE_POWER.name()
                .equalsIgnoreCase(effect.type))
            .filter(effect -> guaranteed(effect, mastery))
            .map(effect -> TechniqueMasteryResolver.resolve(effect, mastery))
            .toList();
    }

    private static boolean guaranteed(MoveEffectData effect, int mastery) {
        return effect.resolvedActivationChance(mastery) >= 1.0
            && guaranteedCondition(effect.condition);
    }

    private static boolean guaranteedCondition(AbilityConditionData condition) {
        if (condition == null) return true;
        AbilityConditionType type;
        try {
            type = AbilityConditionType.fromName(condition.type);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (type == AbilityConditionType.ALWAYS) return true;
        if (condition.children == null || condition.children.isEmpty()) return false;
        if (type == AbilityConditionType.ALL) {
            return condition.children.stream().allMatch(MoveAvailability::guaranteedCondition);
        }
        return type == AbilityConditionType.ANY
            && condition.children.stream().anyMatch(MoveAvailability::guaranteedCondition);
    }

    private static String normalizedResourceKey(String key) {
        return key == null || key.isBlank()
            ? null : key.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Every shikigami definition this move may summon, in effect order. */
    public static List<String> summonedDefinitionIds(Move move) {
        List<String> ids = new ArrayList<>();
        if (move == null) return List.of();
        if (move.usesUnifiedEffects()) {
            return move.getEffects().stream()
                .filter(effect -> AbilityEffectType.SUMMON_CHARACTER.name()
                    .equalsIgnoreCase(effect.type))
                .map(effect -> effect.characterId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        }
        if (move.summonsCharacter()) ids.add(move.getSummonCharacterId());
        List<StatusEffect> effects = new ArrayList<>(move.getSelfEffects());
        effects.addAll(move.getOnHitEffects());
        effects.addAll(move.getOnBlockEffects());
        effects.addAll(move.getOnParryEffects());
        effects.addAll(move.getOnDodgeEffects());
        for (StatusEffect effect : effects) {
            if (effect != null && effect.isSummon()) ids.add(effect.getSummonCharacterId());
        }
        return List.copyOf(ids);
    }
}
