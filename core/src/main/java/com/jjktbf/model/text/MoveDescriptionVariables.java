package com.jjktbf.model.text;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.coded.CodedAbilityRegistry;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.progression.TechniqueMasteryProgressions;
import com.jjktbf.model.progression.TechniqueMasteryResolver;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Catalogs and resolves numeric move-effect values embedded in move descriptions. */
public final class MoveDescriptionVariables {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        ":([A-Za-z0-9_-]+)\\.([A-Za-z][A-Za-z0-9_-]*):");
    private static final Pattern RESERVED_TOKEN_START = Pattern.compile(
        ":effect-", Pattern.CASE_INSENSITIVE);

    private MoveDescriptionVariables() {
    }

    /** One stable, editor-selectable value from a move's effect composition. */
    public record Variable(
        String effectId,
        String field,
        String label,
        String description
    ) {
        public String key() {
            return effectId + "." + field;
        }

        public String token() {
            return ":" + key() + ":";
        }
    }

    public static List<Variable> variables(Move move) {
        return move == null ? List.of() : variables(move.getEffects());
    }

    /** Returns numeric fields in effect-row order and field-authoring order. */
    public static List<Variable> variables(
        List<? extends AbilityEffectData> effects
    ) {
        if (effects == null || effects.isEmpty()) return List.of();
        List<Variable> variables = new ArrayList<>();
        for (int effectIndex = 0; effectIndex < effects.size(); effectIndex++) {
            AbilityEffectData effect = effects.get(effectIndex);
            if (effect == null || effect.effectId == null || effect.effectId.isBlank()) continue;

            AbilityEffectType type;
            try {
                type = AbilityEffectType.fromName(effect.type);
            } catch (IllegalArgumentException exception) {
                continue;
            }

            Set<String> fields = new LinkedHashSet<>(type.masteryProgressionFields(effect));
            for (String field : fields) {
                if (!hasValue(effect, field)) continue;
                variables.add(variable(effect, type, effectIndex, field,
                    hasEffectProgression(effect, field)));
            }
            if (effect instanceof MoveEffectData moveEffect
                && (Boolean.TRUE.equals(moveEffect.activationChanceEnabled)
                    || moveEffect.activationMasteryProgression != null
                        && moveEffect.activationMasteryProgression.containsKey(
                            TechniqueMasteryProgressions.ACTIVATION_CHANCE))) {
                variables.add(variable(effect, type, effectIndex,
                    TechniqueMasteryProgressions.ACTIVATION_CHANCE,
                    moveEffect.activationMasteryProgression != null
                        && moveEffect.activationMasteryProgression.containsKey(
                            TechniqueMasteryProgressions.ACTIVATION_CHANCE)));
            }
        }
        return List.copyOf(variables);
    }

    /** Resolves every known token against this move user's current CTM value. */
    public static String resolve(Move move, int mastery) {
        if (move == null) return "";
        return resolve(move.getDescription(), move.getEffects(), mastery);
    }

    public static String resolve(
        String description,
        List<? extends AbilityEffectData> effects,
        int mastery
    ) {
        if (description == null || description.isEmpty() || effects == null
            || effects.isEmpty()) {
            return description == null ? "" : description;
        }

        Map<String, AbilityEffectData> effectsById = new LinkedHashMap<>();
        for (AbilityEffectData effect : effects) {
            if (effect != null && effect.effectId != null && !effect.effectId.isBlank()) {
                effectsById.putIfAbsent(normalize(effect.effectId), effect);
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Variable variable : variables(effects)) {
            AbilityEffectData effect = effectsById.get(normalize(variable.effectId()));
            String value = resolvedValue(effect, variable.field(), mastery);
            if (value != null) values.put(normalize(variable.key()), value);
        }
        if (values.isEmpty()) return description;

        Matcher matcher = TOKEN_PATTERN.matcher(description);
        StringBuffer resolved = new StringBuffer(description.length());
        while (matcher.find()) {
            String value = values.get(normalize(matcher.group(1) + "." + matcher.group(2)));
            matcher.appendReplacement(resolved,
                Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    /** Returns an editor-facing error for a missing or malformed stable effect token. */
    public static String validationError(
        String description,
        List<? extends AbilityEffectData> effects
    ) {
        if (description == null || description.isEmpty()) return null;
        Set<String> validTokens = variables(effects).stream()
            .map(Variable::token)
            .map(MoveDescriptionVariables::normalize)
            .collect(java.util.stream.Collectors.toSet());

        Matcher startMatcher = RESERVED_TOKEN_START.matcher(description);
        int searchFrom = 0;
        while (startMatcher.find(searchFrom)) {
            int start = startMatcher.start();
            int end = description.indexOf(':', start + 1);
            if (end < 0) {
                return "Close the move description variable that starts at character "
                    + (start + 1) + ".";
            }
            String token = description.substring(start, end + 1);
            if (!TOKEN_PATTERN.matcher(token).matches()
                || !validTokens.contains(normalize(token))) {
                return "Unknown move description variable " + token + ".";
            }
            searchFrom = end + 1;
        }
        return null;
    }

    private static Variable variable(
        AbilityEffectData effect,
        AbilityEffectType type,
        int effectIndex,
        String field,
        boolean progressed
    ) {
        String label = fieldLabel(effect, type, field);
        String source = "Effect " + (effectIndex + 1) + " (" + type.displayName() + "): "
            + label + ". ";
        String behavior = progressed
            ? "Uses this field's CTM progression and displays the move user's current value."
            : "Displays this effect field's current value.";
        return new Variable(effect.effectId, field, label, source + behavior);
    }

    private static boolean hasValue(AbilityEffectData effect, String field) {
        return switch (field) {
            case TechniqueMasteryProgressions.INT_VALUE -> effect.intValue != null;
            case TechniqueMasteryProgressions.DOUBLE_VALUE -> effect.doubleValue != null;
            case TechniqueMasteryProgressions.DURATION_ROUNDS -> effect.durationRounds != null;
            case TechniqueMasteryProgressions.DURATION_TICKS -> effect.durationTicks != null;
            case TechniqueMasteryProgressions.MAGNITUDE -> effect.magnitude != null;
            case TechniqueMasteryProgressions.PER_TICK_REMOVAL_CHANCE ->
                effect.perTickRemovalChance != null;
            case TechniqueMasteryProgressions.USES -> effect.uses != null;
            case TechniqueMasteryProgressions.CODED_STACK_COUNT ->
                effect.codedStackCount != null;
            default -> effect.codedParameters != null
                && effect.codedParameters.containsKey(field);
        };
    }

    private static boolean hasEffectProgression(AbilityEffectData effect, String field) {
        return effect.masteryProgression != null
            && effect.masteryProgression.containsKey(field);
    }

    private static String resolvedValue(
        AbilityEffectData source,
        String field,
        int mastery
    ) {
        if (source == null) return null;
        if (TechniqueMasteryProgressions.ACTIVATION_CHANCE.equals(field)
            && source instanceof MoveEffectData moveEffect) {
            return formatPercent(moveEffect.resolvedActivationChance(mastery));
        }

        AbilityEffectData resolved = TechniqueMasteryResolver.resolve(source, mastery);
        AbilityEffectType type;
        try {
            type = AbilityEffectType.fromName(source.type);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return switch (field) {
            case TechniqueMasteryProgressions.INT_VALUE -> format(resolved.intValue);
            case TechniqueMasteryProgressions.DOUBLE_VALUE ->
                displaysDoubleAsPercent(type)
                    ? formatPercent(resolved.doubleValue) : format(resolved.doubleValue);
            case TechniqueMasteryProgressions.DURATION_ROUNDS ->
                format(resolved.durationRounds);
            case TechniqueMasteryProgressions.DURATION_TICKS ->
                format(resolved.durationTicks);
            case TechniqueMasteryProgressions.MAGNITUDE -> format(resolved.magnitude);
            case TechniqueMasteryProgressions.PER_TICK_REMOVAL_CHANCE ->
                formatPercent(resolved.perTickRemovalChance);
            case TechniqueMasteryProgressions.USES -> format(resolved.uses);
            case TechniqueMasteryProgressions.CODED_STACK_COUNT ->
                format(resolved.codedStackCount);
            default -> format(resolved.codedParameters == null
                ? null : resolved.codedParameters.get(field));
        };
    }

    private static String fieldLabel(
        AbilityEffectData effect,
        AbilityEffectType type,
        String field
    ) {
        if (TechniqueMasteryProgressions.ACTIVATION_CHANCE.equals(field)) {
            return "Activation chance %";
        }
        if (TechniqueMasteryProgressions.CODED_STACK_COUNT.equals(field)) {
            return "Stacks to create";
        }
        if (effect.codedParameters != null && effect.codedParameters.containsKey(field)) {
            return CodedAbilityRegistry.effectParameters(
                    effect.codedAbilityKey, effect.codedAction, effect.codedTarget).stream()
                .filter(parameter -> field.equals(parameter.key()))
                .map(CodedAbilityRegistry.CodedParameter::label)
                .findFirst()
                .orElseGet(() -> humanize(field));
        }
        return switch (field) {
            case TechniqueMasteryProgressions.INT_VALUE -> switch (type) {
                case NEVER_MISS, NEVER_HIT -> "Accuracy priority tier";
                default -> "Value";
            };
            case TechniqueMasteryProgressions.DOUBLE_VALUE ->
                displaysDoubleAsPercent(type) ? "Percentage" : "Multiplier or decimal value";
            case TechniqueMasteryProgressions.DURATION_ROUNDS -> "Duration rounds";
            case TechniqueMasteryProgressions.DURATION_TICKS -> "Duration AP ticks";
            case TechniqueMasteryProgressions.MAGNITUDE -> "Magnitude";
            case TechniqueMasteryProgressions.PER_TICK_REMOVAL_CHANCE ->
                "Removal chance per tick %";
            case TechniqueMasteryProgressions.USES -> "Uses";
            default -> humanize(field);
        };
    }

    private static boolean displaysDoubleAsPercent(AbilityEffectType type) {
        return switch (type) {
            case BF_CHANCE_ADD, HEAL_HP_PERCENT, RESTORE_CE_PERCENT, DRAIN_CE_PERCENT,
                 DEAL_MAX_HP_DAMAGE, TEMP_STAT_PERCENT, BATTLE_STAT_PERCENT -> true;
            default -> false;
        };
    }

    private static String humanize(String value) {
        if (value == null || value.isBlank()) return "Value";
        String spaced = value.replace('_', ' ')
            .replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String format(Number value) {
        if (value == null) return null;
        if (value instanceof Byte || value instanceof Short
            || value instanceof Integer || value instanceof Long) {
            return String.valueOf(value.longValue());
        }
        double decimal = value.doubleValue();
        if (!Double.isFinite(decimal)) return null;
        if (decimal == 0.0) return "0";
        return BigDecimal.valueOf(decimal).stripTrailingZeros().toPlainString();
    }

    private static String formatPercent(Double fraction) {
        if (fraction == null || !Double.isFinite(fraction)) return null;
        return BigDecimal.valueOf(fraction).movePointRight(2)
            .stripTrailingZeros().toPlainString();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
