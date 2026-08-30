package com.jjktbf.model.progression;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.AbilityConditionData;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.move.StatusEffect;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves authored per-field stat progressions into concrete battle-time values. */
public final class TechniqueMasteryResolver {

    private TechniqueMasteryResolver() {
    }

    public static int masteryOf(BattleCombatant combatant) {
        if (combatant == null || combatant.getEffectiveStats() == null) return 0;
        return Math.max(0, Math.min(300,
            combatant.getStatMode().masteryForProgression(
                combatant.getEffectiveStats().getCursedTechniqueMastery())));
    }

    public static AbilityEffectData resolve(AbilityEffectData source, int mastery) {
        return resolve(source, source == null ? null : source.masteryProgression, mastery,
            TechniqueMasteryProgressions.CTM_VARIABLE);
    }

    /** Resolve an effect against a named stat progression without mutating its authored data. */
    public static AbilityEffectData resolve(
        AbilityEffectData source,
        Map<String, TechniqueMasteryProgressionData> progressions,
        int progressionValue,
        String variableName
    ) {
        if (source == null || progressions == null || progressions.isEmpty()) {
            return source;
        }
        AbilityEffectData resolved = source.copy();
        AbilityEffectType type = AbilityEffectType.fromName(source.type);
        Map<String, TechniqueMasteryProgressionData> values = progressions;
        if (source.intValue != null) {
            resolved.intValue = TechniqueMasteryProgressions.resolve(
                values, TechniqueMasteryProgressions.INT_VALUE, source.intValue,
                progressionValue, variableName);
        }
        if (source.doubleValue != null) {
            TechniqueMasteryProgressionData fieldProgression = values.get(
                TechniqueMasteryProgressions.DOUBLE_VALUE);
            if (fieldProgression != null) {
                int authored = fieldProgression.resolve(progressionValue, variableName);
                resolved.doubleValue = type.storesDecimalAsPoints(source)
                    ? (double) authored : authored / 100.0;
            }
        }
        if (source.durationRounds != null) {
            resolved.durationRounds = TechniqueMasteryProgressions.resolve(
                values, TechniqueMasteryProgressions.DURATION_ROUNDS, source.durationRounds,
                progressionValue, variableName);
        }
        if (source.durationTicks != null) {
            resolved.durationTicks = TechniqueMasteryProgressions.resolve(
                values, TechniqueMasteryProgressions.DURATION_TICKS, source.durationTicks,
                progressionValue, variableName);
        }
        if (source.magnitude != null) {
            TechniqueMasteryProgressionData fieldProgression = values.get(
                TechniqueMasteryProgressions.MAGNITUDE);
            if (fieldProgression != null) {
                resolved.magnitude = (double) fieldProgression.resolve(
                    progressionValue, variableName);
            }
        }
        if (source.perTickRemovalChance != null) {
            resolved.perTickRemovalChance = TechniqueMasteryProgressions.resolvePercent(
                values, TechniqueMasteryProgressions.PER_TICK_REMOVAL_CHANCE,
                source.perTickRemovalChance, progressionValue, variableName);
        }
        if (source.uses != null) {
            resolved.uses = TechniqueMasteryProgressions.resolve(
                values, TechniqueMasteryProgressions.USES, source.uses,
                progressionValue, variableName);
        }
        if (source.codedStackCount != null) {
            resolved.codedStackCount = TechniqueMasteryProgressions.resolve(
                values, TechniqueMasteryProgressions.CODED_STACK_COUNT,
                source.codedStackCount, progressionValue, variableName);
        }
        if (source.resourceCapacity != null) {
            resolved.resourceCapacity = TechniqueMasteryProgressions.resolve(
                values, TechniqueMasteryProgressions.RESOURCE_CAPACITY,
                source.resourceCapacity, progressionValue, variableName);
        }
        if (source.resourceStartValue != null) {
            resolved.resourceStartValue = TechniqueMasteryProgressions.resolve(
                values, TechniqueMasteryProgressions.RESOURCE_START_VALUE,
                source.resourceStartValue, progressionValue, variableName);
        }
        if (source.sourceResourceAmount != null) {
            resolved.sourceResourceAmount = TechniqueMasteryProgressions.resolve(
                values, TechniqueMasteryProgressions.SOURCE_RESOURCE_AMOUNT,
                source.sourceResourceAmount, progressionValue, variableName);
        }
        if (source.targetResourceAmount != null) {
            resolved.targetResourceAmount = TechniqueMasteryProgressions.resolve(
                values, TechniqueMasteryProgressions.TARGET_RESOURCE_AMOUNT,
                source.targetResourceAmount, progressionValue, variableName);
        }
        resolved.codedParameters = resolveCodedParameters(
            source.codedParameters, values, progressionValue, variableName);
        return resolved;
    }

    public static AbilityConditionData resolve(AbilityConditionData source, int mastery) {
        if (source == null || source.masteryProgression == null
            || source.masteryProgression.isEmpty()) {
            return source;
        }
        AbilityConditionData resolved = source.copy();
        resolved.percentage = source.percentage == null ? null : resolvePercent(
            source.masteryProgression, TechniqueMasteryProgressions.PERCENTAGE,
            source.percentage, mastery);
        resolved.amount = source.amount == null ? null : resolveInt(
            source.masteryProgression, TechniqueMasteryProgressions.AMOUNT,
            source.amount, mastery);
        resolved.tick = source.tick == null ? null : resolveInt(
            source.masteryProgression, TechniqueMasteryProgressions.TICK,
            source.tick, mastery);
        resolved.round = source.round == null ? null : resolveInt(
            source.masteryProgression, TechniqueMasteryProgressions.ROUND,
            source.round, mastery);
        return resolved;
    }

    public static StatusEffect resolve(StatusEffect source, int mastery) {
        if (source == null || source.getMasteryProgression().isEmpty()) return source;
        Map<String, TechniqueMasteryProgressionData> values = source.getMasteryProgression();
        if (source.isCoded()) {
            Integer stackCount = source.getCodedStackCount();
            TechniqueMasteryProgressionData stackProgression = values.get(
                TechniqueMasteryProgressions.CODED_STACK_COUNT);
            if (stackProgression != null) stackCount = stackProgression.resolve(mastery);
            return StatusEffect.coded(
                source.getCodedAbilityKey(), source.getCodedAction(), source.getCodedTarget(),
                stackCount,
                resolveCodedParameters(source.getCodedParameters(), values, mastery,
                    TechniqueMasteryProgressions.CTM_VARIABLE),
                source.getMasteryProgression());
        }
        int rounds = TechniqueMasteryProgressions.resolve(
            values, TechniqueMasteryProgressions.DURATION_ROUNDS,
            source.getDurationRounds(), mastery);
        int ticks = TechniqueMasteryProgressions.resolve(
            values, TechniqueMasteryProgressions.DURATION_TICKS,
            source.getDurationTicks(), mastery);
        TechniqueMasteryProgressionData magnitudeProgression = values.get(
            TechniqueMasteryProgressions.MAGNITUDE);
        double magnitude = magnitudeProgression == null
            ? source.getMagnitude() : magnitudeProgression.resolve(mastery);
        double perTickRemovalChance = TechniqueMasteryProgressions.resolvePercent(
            values, TechniqueMasteryProgressions.PER_TICK_REMOVAL_CHANCE,
            source.getPerTickRemovalChance(), mastery);
        return new StatusEffect(source.getType(), rounds, ticks, magnitude,
            perTickRemovalChance,
            source.getMasteryProgression());
    }

    public static int resolveInt(
        Map<String, TechniqueMasteryProgressionData> progressions,
        String field,
        Integer literal,
        int mastery
    ) {
        return TechniqueMasteryProgressions.resolve(
            progressions, field, literal == null ? 0 : literal, mastery);
    }

    public static double resolvePercent(
        Map<String, TechniqueMasteryProgressionData> progressions,
        String field,
        Double literal,
        int mastery
    ) {
        return TechniqueMasteryProgressions.resolvePercent(
            progressions, field, literal == null ? 0.0 : literal, mastery);
    }

    public static int codedParameter(
        Map<String, Integer> parameters,
        String key,
        int fallback
    ) {
        return parameters == null ? fallback : parameters.getOrDefault(key, fallback);
    }

    private static Map<String, Integer> resolveCodedParameters(
        Map<String, Integer> parameters,
        Map<String, TechniqueMasteryProgressionData> progressions,
        int progressionValue,
        String variableName
    ) {
        if ((parameters == null || parameters.isEmpty())
            && (progressions == null || progressions.isEmpty())) {
            return parameters;
        }
        Map<String, Integer> resolved = parameters == null
            ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
        if (progressions == null) return resolved;
        for (Map.Entry<String, TechniqueMasteryProgressionData> entry : progressions.entrySet()) {
            if (isGenericField(entry.getKey()) || entry.getValue() == null) continue;
            resolved.put(entry.getKey(), entry.getValue().resolve(progressionValue, variableName));
        }
        return resolved;
    }

    private static boolean isGenericField(String field) {
        return TechniqueMasteryProgressions.INT_VALUE.equals(field)
            || TechniqueMasteryProgressions.DOUBLE_VALUE.equals(field)
            || TechniqueMasteryProgressions.DURATION_ROUNDS.equals(field)
            || TechniqueMasteryProgressions.DURATION_TICKS.equals(field)
            || TechniqueMasteryProgressions.MAGNITUDE.equals(field)
            || TechniqueMasteryProgressions.PER_TICK_REMOVAL_CHANCE.equals(field)
            || TechniqueMasteryProgressions.USES.equals(field)
            || TechniqueMasteryProgressions.CODED_STACK_COUNT.equals(field)
            || TechniqueMasteryProgressions.RESOURCE_CAPACITY.equals(field)
            || TechniqueMasteryProgressions.RESOURCE_START_VALUE.equals(field)
            || TechniqueMasteryProgressions.SOURCE_RESOURCE_AMOUNT.equals(field)
            || TechniqueMasteryProgressions.TARGET_RESOURCE_AMOUNT.equals(field);
    }
}
