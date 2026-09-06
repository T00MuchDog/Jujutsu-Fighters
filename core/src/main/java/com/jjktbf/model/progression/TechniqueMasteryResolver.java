package com.jjktbf.model.progression;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.AbilityConditionData;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.StatKey;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleStatMode;
import com.jjktbf.model.move.StatusEffect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Resolves authored per-field stat progressions into concrete battle-time values. */
public final class TechniqueMasteryResolver {

    private TechniqueMasteryResolver() {
    }

    public static int masteryOf(BattleCombatant combatant) {
        return statForProgression(combatant, StatKey.CURSED_TECHNIQUE_MASTERY);
    }

    /**
     * One stat's progression input for a combatant: the effective stat value
     * transformed by the battle's stat mode and clamped to the authored
     * 0..300 stat range.
     */
    public static int statForProgression(BattleCombatant combatant, StatKey stat) {
        if (combatant == null || combatant.getEffectiveStats() == null) return 0;
        return clampProgressionStat(
            combatant.getStatMode(), stat, stat.get(combatant.getEffectiveStats()));
    }

    /** Same policy as {@link #statForProgression} for a detached stat block. */
    public static int statForProgression(
        CharacterStats stats, BattleStatMode mode, StatKey stat
    ) {
        if (stats == null) return 0;
        return clampProgressionStat(mode, stat, stat.get(stats));
    }

    private static int clampProgressionStat(BattleStatMode mode, StatKey stat, int rawValue) {
        return Math.max(0, Math.min(CharacterStats.MAX_STAT,
            mode == null ? rawValue : mode.statForProgression(rawValue)));
    }

    /** Sum of every stat a progression selects, each already mode-scaled and clamped. */
    private static int combinedValue(
        TechniqueMasteryProgressionData progression,
        ToIntFunction<StatKey> statSource
    ) {
        int sum = 0;
        for (StatKey stat : progression.effectiveScalingStats()) {
            sum += statSource.applyAsInt(stat);
        }
        return sum;
    }

    public static AbilityEffectData resolve(AbilityEffectData source, int mastery) {
        return resolve(source, source == null ? null : source.masteryProgression, mastery,
            TechniqueMasteryProgressions.CTM_VARIABLE);
    }

    /** Resolve every field progression against the owning combatant's selected stats. */
    public static AbilityEffectData resolve(AbilityEffectData source, BattleCombatant owner) {
        return resolve(source, statSource(owner));
    }

    /** Resolve every field progression against a detached stat block. */
    public static AbilityEffectData resolve(
        AbilityEffectData source, CharacterStats stats, BattleStatMode mode
    ) {
        return resolve(source, statSource(stats, mode));
    }

    private static AbilityEffectData resolve(
        AbilityEffectData source,
        ToIntFunction<StatKey> statSource
    ) {
        if (source == null || source.masteryProgression == null
            || source.masteryProgression.isEmpty()) {
            return source;
        }
        return resolve(source, source.masteryProgression,
            progression -> combinedValue(progression, statSource),
            TechniqueMasteryProgressions.CTM_VARIABLE);
    }

    private static ToIntFunction<StatKey> statSource(BattleCombatant combatant) {
        return stat -> statForProgression(combatant, stat);
    }

    private static ToIntFunction<StatKey> statSource(CharacterStats stats, BattleStatMode mode) {
        return stat -> statForProgression(stats, mode, stat);
    }

    /** Resolve an effect against a named stat progression without mutating its authored data. */
    public static AbilityEffectData resolve(
        AbilityEffectData source,
        Map<String, TechniqueMasteryProgressionData> progressions,
        int progressionValue,
        String variableName
    ) {
        return resolve(source, progressions,
            progression -> progressionValue, variableName);
    }

    /**
     * Resolve an effect where each field progression draws its combined value
     * from its own selected stats.
     */
    public static AbilityEffectData resolve(
        AbilityEffectData source,
        Map<String, TechniqueMasteryProgressionData> progressions,
        ToIntFunction<TechniqueMasteryProgressionData> progressionValue,
        String fallbackVariable
    ) {
        if (source == null || progressions == null || progressions.isEmpty()) {
            return source;
        }
        AbilityEffectData resolved = source.copy();
        AbilityEffectType type = AbilityEffectType.fromName(source.type);
        Map<String, TechniqueMasteryProgressionData> values = progressions;
        if (source.intValue != null) {
            resolved.intValue = resolveField(values, TechniqueMasteryProgressions.INT_VALUE,
                source.intValue, progressionValue, fallbackVariable);
        }
        if (source.doubleValue != null) {
            TechniqueMasteryProgressionData fieldProgression = values.get(
                TechniqueMasteryProgressions.DOUBLE_VALUE);
            if (fieldProgression != null) {
                int authored = resolveProgression(
                    fieldProgression, progressionValue, fallbackVariable);
                resolved.doubleValue = type.storesDecimalAsPoints(source)
                    ? (double) authored : authored / 100.0;
            }
        }
        if (source.durationRounds != null) {
            resolved.durationRounds = resolveField(values,
                TechniqueMasteryProgressions.DURATION_ROUNDS,
                source.durationRounds, progressionValue, fallbackVariable);
        }
        if (source.durationTicks != null) {
            resolved.durationTicks = resolveField(values,
                TechniqueMasteryProgressions.DURATION_TICKS,
                source.durationTicks, progressionValue, fallbackVariable);
        }
        if (source.magnitude != null) {
            TechniqueMasteryProgressionData fieldProgression = values.get(
                TechniqueMasteryProgressions.MAGNITUDE);
            if (fieldProgression != null) {
                resolved.magnitude = (double) resolveProgression(
                    fieldProgression, progressionValue, fallbackVariable);
            }
        }
        if (source.perTickRemovalChance != null) {
            TechniqueMasteryProgressionData fieldProgression = values.get(
                TechniqueMasteryProgressions.PER_TICK_REMOVAL_CHANCE);
            resolved.perTickRemovalChance = fieldProgression == null
                ? source.perTickRemovalChance
                : resolveProgression(fieldProgression, progressionValue, fallbackVariable)
                    / 100.0;
        }
        if (source.uses != null) {
            resolved.uses = resolveField(values, TechniqueMasteryProgressions.USES,
                source.uses, progressionValue, fallbackVariable);
        }
        if (source.codedStackCount != null) {
            resolved.codedStackCount = resolveField(values,
                TechniqueMasteryProgressions.CODED_STACK_COUNT,
                source.codedStackCount, progressionValue, fallbackVariable);
        }
        if (source.resourceCapacity != null) {
            resolved.resourceCapacity = resolveField(values,
                TechniqueMasteryProgressions.RESOURCE_CAPACITY,
                source.resourceCapacity, progressionValue, fallbackVariable);
        }
        if (source.resourceStartValue != null) {
            resolved.resourceStartValue = resolveField(values,
                TechniqueMasteryProgressions.RESOURCE_START_VALUE,
                source.resourceStartValue, progressionValue, fallbackVariable);
        }
        if (source.sourceResourceAmount != null) {
            resolved.sourceResourceAmount = resolveField(values,
                TechniqueMasteryProgressions.SOURCE_RESOURCE_AMOUNT,
                source.sourceResourceAmount, progressionValue, fallbackVariable);
        }
        if (source.targetResourceAmount != null) {
            resolved.targetResourceAmount = resolveField(values,
                TechniqueMasteryProgressions.TARGET_RESOURCE_AMOUNT,
                source.targetResourceAmount, progressionValue, fallbackVariable);
        }
        resolved.codedParameters = resolveCodedParameters(
            source.codedParameters, values, progressionValue, fallbackVariable);
        return resolved;
    }

    private static int resolveField(
        Map<String, TechniqueMasteryProgressionData> progressions,
        String field,
        int literal,
        ToIntFunction<TechniqueMasteryProgressionData> progressionValue,
        String fallbackVariable
    ) {
        TechniqueMasteryProgressionData progression = TechniqueMasteryProgressions.progression(
            progressions, field);
        return progression == null
            ? literal
            : resolveProgression(progression, progressionValue, fallbackVariable);
    }

    private static int resolveProgression(
        TechniqueMasteryProgressionData progression,
        ToIntFunction<TechniqueMasteryProgressionData> progressionValue,
        String fallbackVariable
    ) {
        return progression.resolve(progressionValue.applyAsInt(progression), fallbackVariable);
    }

    public static AbilityConditionData resolve(AbilityConditionData source, int mastery) {
        return resolveCondition(source, progression -> mastery);
    }

    /** Resolve a condition's field progressions against the owner's selected stats. */
    public static AbilityConditionData resolve(AbilityConditionData source, BattleCombatant owner) {
        return resolve(source, statSource(owner));
    }

    /** Resolve a condition's field progressions against a detached stat block. */
    public static AbilityConditionData resolve(
        AbilityConditionData source, CharacterStats stats, BattleStatMode mode
    ) {
        return resolve(source, statSource(stats, mode));
    }

    private static AbilityConditionData resolve(
        AbilityConditionData source,
        ToIntFunction<StatKey> statSource
    ) {
        if (source == null || source.masteryProgression == null
            || source.masteryProgression.isEmpty()) {
            return source;
        }
        return resolveCondition(source,
            progression -> combinedValue(progression, statSource));
    }

    private static AbilityConditionData resolveCondition(
        AbilityConditionData source,
        ToIntFunction<TechniqueMasteryProgressionData> progressionValue
    ) {
        AbilityConditionData resolved = source.copy();
        resolved.percentage = source.percentage == null ? null : resolvePercentField(
            source.masteryProgression, TechniqueMasteryProgressions.PERCENTAGE,
            source.percentage, progressionValue);
        resolved.amount = source.amount == null ? null : resolveIntField(
            source.masteryProgression, TechniqueMasteryProgressions.AMOUNT,
            source.amount, progressionValue);
        resolved.tick = source.tick == null ? null : resolveIntField(
            source.masteryProgression, TechniqueMasteryProgressions.TICK,
            source.tick, progressionValue);
        resolved.round = source.round == null ? null : resolveIntField(
            source.masteryProgression, TechniqueMasteryProgressions.ROUND,
            source.round, progressionValue);
        return resolved;
    }

    public static StatusEffect resolve(StatusEffect source, int mastery) {
        return resolve(source, progression -> mastery);
    }

    /** Resolve a status's field progressions against the owner's selected stats. */
    public static StatusEffect resolve(StatusEffect source, BattleCombatant owner) {
        if (source == null || source.getMasteryProgression().isEmpty()) return source;
        return resolve(source,
            progression -> combinedValue(progression, statSource(owner)));
    }

    private static StatusEffect resolve(
        StatusEffect source,
        ToIntFunction<TechniqueMasteryProgressionData> progressionValue
    ) {
        Map<String, TechniqueMasteryProgressionData> values = source.getMasteryProgression();
        if (source.isCoded()) {
            Integer stackCount = source.getCodedStackCount();
            TechniqueMasteryProgressionData stackProgression = values.get(
                TechniqueMasteryProgressions.CODED_STACK_COUNT);
            if (stackProgression != null) {
                stackCount = resolveProgression(stackProgression, progressionValue,
                    TechniqueMasteryProgressions.CTM_VARIABLE);
            }
            Map<String, Integer> codedParameters = resolveCodedParameters(
                source.getCodedParameters(), values, progressionValue,
                TechniqueMasteryProgressions.CTM_VARIABLE);
            return StatusEffect.coded(
                source.getCodedAbilityKey(), source.getCodedAction(), source.getCodedTarget(),
                stackCount, codedParameters,
                source.getMasteryProgression());
        }
        int rounds = resolveIntField(values, TechniqueMasteryProgressions.DURATION_ROUNDS,
            source.getDurationRounds(), progressionValue);
        int ticks = resolveIntField(values, TechniqueMasteryProgressions.DURATION_TICKS,
            source.getDurationTicks(), progressionValue);
        TechniqueMasteryProgressionData magnitudeProgression = values.get(
            TechniqueMasteryProgressions.MAGNITUDE);
        double magnitude = magnitudeProgression == null
            ? source.getMagnitude()
            : resolveProgression(magnitudeProgression, progressionValue,
                TechniqueMasteryProgressions.CTM_VARIABLE);
        double perTickRemovalChance = resolvePercentField(
            values, TechniqueMasteryProgressions.PER_TICK_REMOVAL_CHANCE,
            source.getPerTickRemovalChance(), progressionValue);
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

    /** Resolve one integer field against the owner's selected stats. */
    public static int resolveInt(
        Map<String, TechniqueMasteryProgressionData> progressions,
        String field,
        Integer literal,
        BattleCombatant owner
    ) {
        return resolveIntField(progressions, field, literal == null ? 0 : literal,
            progression -> combinedValue(progression, statSource(owner)));
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

    /** Resolve one percentage field against the owner's selected stats. */
    public static double resolvePercent(
        Map<String, TechniqueMasteryProgressionData> progressions,
        String field,
        Double literal,
        BattleCombatant owner
    ) {
        TechniqueMasteryProgressionData progression = TechniqueMasteryProgressions.progression(
            progressions, field);
        return progression == null
            ? (literal == null ? 0.0 : literal)
            : resolveProgression(progression,
                p -> combinedValue(p, statSource(owner)),
                TechniqueMasteryProgressions.CTM_VARIABLE) / 100.0;
    }

    public static int codedParameter(
        Map<String, Integer> parameters,
        String key,
        int fallback
    ) {
        return parameters == null ? fallback : parameters.getOrDefault(key, fallback);
    }

    private static int resolveIntField(
        Map<String, TechniqueMasteryProgressionData> progressions,
        String field,
        int literal,
        ToIntFunction<TechniqueMasteryProgressionData> progressionValue
    ) {
        TechniqueMasteryProgressionData progression = TechniqueMasteryProgressions.progression(
            progressions, field);
        return progression == null ? literal
            : resolveProgression(progression, progressionValue,
                TechniqueMasteryProgressions.CTM_VARIABLE);
    }

    private static double resolvePercentField(
        Map<String, TechniqueMasteryProgressionData> progressions,
        String field,
        double literal,
        ToIntFunction<TechniqueMasteryProgressionData> progressionValue
    ) {
        TechniqueMasteryProgressionData progression = TechniqueMasteryProgressions.progression(
            progressions, field);
        return progression == null ? literal
            : resolveProgression(progression, progressionValue,
                TechniqueMasteryProgressions.CTM_VARIABLE) / 100.0;
    }

    private static Map<String, Integer> resolveCodedParameters(
        Map<String, Integer> parameters,
        Map<String, TechniqueMasteryProgressionData> progressions,
        ToIntFunction<TechniqueMasteryProgressionData> progressionValue,
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
            resolved.put(entry.getKey(), resolveProgression(
                entry.getValue(), progressionValue, variableName));
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
