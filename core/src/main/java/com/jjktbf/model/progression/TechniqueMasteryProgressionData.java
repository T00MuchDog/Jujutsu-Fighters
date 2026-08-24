package com.jjktbf.model.progression;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/** Persisted formula or step-based progression driven by a bounded combat stat. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TechniqueMasteryProgressionData {

    public static final String FORMULA = "FORMULA";
    public static final String BENCHMARKS = "BENCHMARKS";

    public String mode;
    public String formula;
    public List<BenchmarkData> benchmarks;
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
        return copy;
    }

    /** Returns {@code null} when valid, otherwise a deterministic description. */
    public String validationError() {
        return validationError(TechniqueMasteryProgressions.CTM_VARIABLE, "CTM");
    }

    /** Validate this progression against its authored formula variable. */
    public String validationError(String variableName, String variableLabel) {
        if (mode == null) return "Progression mode is required";
        if (FORMULA.equals(mode)) return formulaValidationError(variableName, variableLabel);
        if (BENCHMARKS.equals(mode)) return benchmarkValidationError();
        return "Progression mode must be FORMULA or BENCHMARKS";
    }

    /** Resolves the progression after clamping its input to the authored 0..300 range. */
    public int resolve(int ctm) {
        return resolve(ctm, TechniqueMasteryProgressions.CTM_VARIABLE);
    }

    /** Resolve this progression using the configured formula variable. */
    public int resolve(int value, String variableName) {
        int clampedValue = Math.max(0, Math.min(300, value));
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

        for (int value = 0; value <= 300; value++) {
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

        int previousMastery = -1;
        for (int index = 0; index < benchmarks.size(); index++) {
            BenchmarkData benchmark = benchmarks.get(index);
            if (benchmark == null) return "Benchmark " + (index + 1) + " is required";
            if (benchmark.mastery < 0 || benchmark.mastery > 300) {
                return "Benchmark " + (index + 1) + " mastery must be between 0 and 300";
            }
            if (benchmark.mastery <= previousMastery) {
                return "Benchmark masteries must be strictly increasing";
            }
            previousMastery = benchmark.mastery;
        }
        return null;
    }
}
