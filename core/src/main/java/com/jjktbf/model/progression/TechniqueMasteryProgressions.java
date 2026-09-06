package com.jjktbf.model.progression;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Shared field keys and helpers for stat-driven effect progressions. */
public final class TechniqueMasteryProgressions {

    public static final String CTM_VARIABLE = "ctm";
    public static final String CE_EFFICIENCY_VARIABLE = "cee";
    public static final String INT_VALUE = "intValue";
    public static final String DOUBLE_VALUE = "doubleValue";
    public static final String DURATION_ROUNDS = "durationRounds";
    public static final String DURATION_TICKS = "durationTicks";
    public static final String MAGNITUDE = "magnitude";
    public static final String PER_TICK_REMOVAL_CHANCE = "perTickRemovalChance";
    public static final String USES = "uses";
    public static final String CODED_STACK_COUNT = "codedStackCount";
    public static final String ACTIVATION_CHANCE = "activationChance";
    public static final String PERCENTAGE = "percentage";
    public static final String AMOUNT = "amount";
    public static final String TICK = "tick";
    public static final String ROUND = "round";
    public static final String RESOURCE_CAPACITY = "resourceCapacity";
    public static final String RESOURCE_START_VALUE = "resourceStartValue";
    public static final String SOURCE_RESOURCE_AMOUNT = "sourceResourceAmount";
    public static final String TARGET_RESOURCE_AMOUNT = "targetResourceAmount";

    private TechniqueMasteryProgressions() {
    }

    public static Map<String, TechniqueMasteryProgressionData> copy(
        Map<String, TechniqueMasteryProgressionData> source
    ) {
        if (source == null) return null;
        Map<String, TechniqueMasteryProgressionData> copy = new LinkedHashMap<>();
        for (Map.Entry<String, TechniqueMasteryProgressionData> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().copy());
        }
        return copy;
    }

    public static Map<String, Integer> copyIntegers(Map<String, Integer> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }

    public static int resolve(
        Map<String, TechniqueMasteryProgressionData> progressions,
        String field,
        int literal,
        int mastery
    ) {
        return resolve(progressions, field, literal, mastery, CTM_VARIABLE);
    }

    public static int resolve(
        Map<String, TechniqueMasteryProgressionData> progressions,
        String field,
        int literal,
        int value,
        String variableName
    ) {
        TechniqueMasteryProgressionData progression = progression(progressions, field);
        return progression == null ? literal : progression.resolve(value, variableName);
    }

    /** Resolve an integer-authored percentage and convert it to the runtime fraction. */
    public static double resolvePercent(
        Map<String, TechniqueMasteryProgressionData> progressions,
        String field,
        double literalFraction,
        int mastery
    ) {
        return resolvePercent(progressions, field, literalFraction, mastery, CTM_VARIABLE);
    }

    /** Resolve an integer-authored percentage using the named progression variable. */
    public static double resolvePercent(
        Map<String, TechniqueMasteryProgressionData> progressions,
        String field,
        double literalFraction,
        int value,
        String variableName
    ) {
        TechniqueMasteryProgressionData progression = progression(progressions, field);
        return progression == null ? literalFraction : progression.resolve(value, variableName) / 100.0;
    }

    public static TechniqueMasteryProgressionData progression(
        Map<String, TechniqueMasteryProgressionData> progressions,
        String field
    ) {
        return progressions == null || field == null ? null : progressions.get(field);
    }

    /** Largest combined input any progression in the map can receive. */
    public static int maxScalingInput(
        Map<String, TechniqueMasteryProgressionData> progressions
    ) {
        int max = 0;
        if (progressions != null) {
            for (TechniqueMasteryProgressionData progression : progressions.values()) {
                if (progression != null) {
                    max = Math.max(max, progression.maxScalingInput());
                }
            }
        }
        return max == 0 ? com.jjktbf.model.character.CharacterStats.MAX_STAT : max;
    }

    public static String validationError(
        Map<String, TechniqueMasteryProgressionData> progressions,
        Set<String> allowedFields
    ) {
        return validationError(progressions, allowedFields, CTM_VARIABLE, "CTM");
    }

    public static String validationError(
        Map<String, TechniqueMasteryProgressionData> progressions,
        Set<String> allowedFields,
        String variableName,
        String variableLabel
    ) {
        if (progressions == null || progressions.isEmpty()) return null;
        for (Map.Entry<String, TechniqueMasteryProgressionData> entry : progressions.entrySet()) {
            if (entry.getKey() == null || !allowedFields.contains(entry.getKey())) {
                return "Unsupported progression field: " + entry.getKey();
            }
            if (entry.getValue() == null) {
                return "Progression for " + entry.getKey() + " is missing.";
            }
            String error = entry.getValue().validationError(variableName, variableLabel);
            if (error != null) return entry.getKey() + ": " + error;
        }
        return null;
    }
}
