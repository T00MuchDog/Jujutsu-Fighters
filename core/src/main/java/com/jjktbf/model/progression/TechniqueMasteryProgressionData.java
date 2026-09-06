package com.jjktbf.model.progression;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.StatKey;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Persisted formula or step-based progression driven by a bounded combat stat. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TechniqueMasteryProgressionData {

    public static final String FORMULA = "FORMULA";
    public static final String BENCHMARKS = "BENCHMARKS";

    /**
     * Formula variable bound to the sum of the selected stats when more than
     * one stat drives this progression.
     */
    public static final String COMBINED_VARIABLE = "value";

    public String mode;
    public String formula;
    public List<BenchmarkData> benchmarks;

    /**
     * Core stats whose values are summed to drive this progression. Entries may
     * use any {@link StatKey} alias. Null means legacy behaviour: scale with
     * Cursed Technique Mastery only.
     */
    public List<String> scalingStats;
    private transient String cachedFormula;
    private transient String cachedVariableName;
    private transient TechniqueMasteryFormula.Expression cachedExpression;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BenchmarkData {
        public int mastery;
        public int value;

        public BenchmarkData() {
        }

        public BenchmarkData(int mastery, int value) {
            this.mastery = mastery;
            this.value = value;
        }

        public BenchmarkData copy() {
            return new BenchmarkData(mastery, value);
        }
    }

    public TechniqueMasteryProgressionData copy() {
        TechniqueMasteryProgressionData copy = new TechniqueMasteryProgressionData();
        copy.mode = mode;
        copy.formula = formula;
        if (benchmarks != null) {
            copy.benchmarks = new ArrayList<>(benchmarks.size());
            for (BenchmarkData benchmark : benchmarks) {
                copy.benchmarks.add(benchmark == null ? null : benchmark.copy());
            }
        }
        if (scalingStats != null) copy.scalingStats = new ArrayList<>(scalingStats);
        return copy;
    }

    /**
     * Canonical stat list driving this progression: parsing each authored entry
     * through {@link StatKey}, dropping duplicates while preserving order.
     * Unparseable entries are skipped here and reported by validation. Null or
     * blank authored lists fall back to Cursed Technique Mastery so legacy data
     * keeps its original meaning.
     */
    public List<StatKey> effectiveScalingStats() {
        if (scalingStats == null || scalingStats.isEmpty()) {
            return List.of(StatKey.CURSED_TECHNIQUE_MASTERY);
        }
        Set<StatKey> stats = new LinkedHashSet<>();
        for (String authored : scalingStats) {
            if (authored == null || authored.isBlank()) continue;
            try {
                stats.add(StatKey.fromString(authored));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return stats.isEmpty()
            ? List.of(StatKey.CURSED_TECHNIQUE_MASTERY)
            : List.copyOf(stats);
    }

    /**
     * Upper bound of the combined scaling value: each selected stat contributes
     * at most {@link CharacterStats#MAX_STAT}, and stats are summed.
     */
    public int maxScalingInput() {
        return CharacterStats.MAX_STAT * effectiveScalingStats().size();
    }

    /**
     * Formula variable for this progression: the selected stat's code when one
     * stat drives it, the combined {@link #COMBINED_VARIABLE} when several do,
     * and {@code ctm} for legacy CTM-only progressions.
     */
    public String formulaVariable() {
        List<StatKey> stats = effectiveScalingStats();
        if (stats.size() == 1) return stats.get(0).formulaVariable;
        return COMBINED_VARIABLE;
    }

    /** User-facing name of the quantity the progression input represents. */
    public String variableLabel() {
        List<StatKey> stats = effectiveScalingStats();
        if (stats.size() == 1) {
            return stats.get(0) == StatKey.CURSED_TECHNIQUE_MASTERY
                && (scalingStats == null || scalingStats.isEmpty())
                ? "CTM"
                : stats.get(0).label;
        }
        StringBuilder label = new StringBuilder();
        for (StatKey stat : stats) {
            if (label.length() > 0) label.append(" + ");
            label.append(stat.label);
        }
        return label.append(" (sum)").toString();
    }

    /** Returns {@code null} when valid, otherwise a deterministic description. */
    public String validationError() {
        return validationError(TechniqueMasteryProgressions.CTM_VARIABLE, "CTM");
    }

    /** Validate this progression against its authored formula variable. */
    public String validationError(String variableName, String variableLabel) {
        String statError = scalingStatValidationError();
        if (statError != null) return statError;
        if (hasAuthoredScalingStats()) {
            variableName = formulaVariable();
            variableLabel = variableLabel();
        }
        if (mode == null) return "Progression mode is required";
        if (FORMULA.equals(mode)) return formulaValidationError(variableName, variableLabel);
        if (BENCHMARKS.equals(mode)) return benchmarkValidationError();
        return "Progression mode must be FORMULA or BENCHMARKS";
    }

    private boolean hasAuthoredScalingStats() {
        return scalingStats != null && !scalingStats.isEmpty();
    }

    private String scalingStatValidationError() {
        if (scalingStats == null) return null;
        if (scalingStats.isEmpty()) return "Select at least one scaling stat.";
        for (String authored : scalingStats) {
            if (authored == null || authored.isBlank()) {
                return "Scaling stat entries cannot be blank.";
            }
            try {
                StatKey.fromString(authored);
            } catch (IllegalArgumentException exception) {
                return "Unknown scaling stat: " + authored;
            }
        }
        return null;
    }

    /** Resolves the progression after clamping its input to the authored range. */
    public int resolve(int ctm) {
        return resolve(ctm, TechniqueMasteryProgressions.CTM_VARIABLE);
    }

    /** Resolve this progression using the configured formula variable. */
    public int resolve(int value, String variableName) {
        int clampedValue = Math.max(0, Math.min(maxScalingInput(), value));
        if (hasAuthoredScalingStats()) variableName = formulaVariable();
        if (FORMULA.equals(mode)) {
            if (formula == null || formula.isBlank()) {
                throw new IllegalStateException("Invalid stat progression: Formula is required");
            }
            return expression(variableName)
                .evaluate(TechniqueMasteryFormula.Rational.of(clampedValue))
                .floorToInt();
        }
        if (!BENCHMARKS.equals(mode)) {
            throw new IllegalStateException(
                "Invalid stat progression: unknown mode " + mode);
        }
        String benchmarkError = benchmarkValidationError();
        if (benchmarkError != null) {
            throw new IllegalStateException(
                "Invalid stat progression: " + benchmarkError);
        }

        int resolved = benchmarks.get(0).value;
        for (BenchmarkData benchmark : benchmarks) {
            if (benchmark.mastery > clampedValue) break;
            resolved = benchmark.value;
        }
        return resolved;
    }

    private String formulaValidationError(String variableName, String variableLabel) {
        if (formula == null || formula.isBlank()) return "Formula is required";

        TechniqueMasteryFormula.Expression expression;
        try {
            expression = expression(variableName);
        } catch (TechniqueMasteryFormula.FormulaException exception) {
            return "Invalid formula: " + exception.getMessage();
        }

        for (int value = 0; value <= maxScalingInput(); value++) {
            try {
                expression.evaluate(TechniqueMasteryFormula.Rational.of(value)).floorToInt();
            } catch (TechniqueMasteryFormula.FormulaException exception) {
                return "Invalid formula at " + variableLabel + " " + value + ": "
                    + exception.getMessage();
            }
        }
        return null;
    }

    private TechniqueMasteryFormula.Expression expression(String variableName) {
        if (cachedExpression == null || !java.util.Objects.equals(cachedFormula, formula)
            || !java.util.Objects.equals(cachedVariableName, variableName)) {
            cachedExpression = TechniqueMasteryFormula.parse(formula, variableName);
            cachedFormula = formula;
            cachedVariableName = variableName;
        }
        return cachedExpression;
    }

    private String benchmarkValidationError() {
        if (benchmarks == null || benchmarks.isEmpty()) {
            return "At least one benchmark is required";
        }
        if (benchmarks.get(0) == null) return "Benchmark 1 is required";
        if (benchmarks.get(0).mastery != 0) return "First benchmark mastery must be 0";
        int maxInput = maxScalingInput();
        int previousMastery = -1;
        for (int index = 0; index < benchmarks.size(); index++) {
            BenchmarkData benchmark = benchmarks.get(index);
            if (benchmark == null) return "Benchmark " + (index + 1) + " is required";
            if (benchmark.mastery < 0 || benchmark.mastery > maxInput) {
                return "Benchmark " + (index + 1) + " mastery must be between 0 and " + maxInput;
            }
            if (benchmark.mastery <= previousMastery) {
                return "Benchmark masteries must be strictly increasing";
            }
            previousMastery = benchmark.mastery;
        }
        return null;
    }
}
