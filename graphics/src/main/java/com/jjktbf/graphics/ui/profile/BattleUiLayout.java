package com.jjktbf.graphics.ui.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjktbf.graphics.ui.UiScaleSystem;

/**
 * Serializable, presentation-only metrics consumed by the production battle UI.
 * Values deliberately describe reusable geometry rather than a character or move.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BattleUiLayout {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public int schemaVersion = CURRENT_SCHEMA_VERSION;
    public int referenceWidth = (int) UiScaleSystem.GAMEPLAY_REFERENCE_WIDTH;
    public int referenceHeight = (int) UiScaleSystem.GAMEPLAY_REFERENCE_HEIGHT;
    public Execution execution = new Execution();
    public Planner planner = new Planner();

    public BattleUiLayout() {
    }

    private BattleUiLayout(BattleUiLayout source) {
        schemaVersion = source.schemaVersion;
        referenceWidth = source.referenceWidth;
        referenceHeight = source.referenceHeight;
        execution = new Execution(source.execution);
        planner = new Planner(source.planner);
    }

    public BattleUiLayout copy() {
        return new BattleUiLayout(this);
    }

    public static BattleUiLayout defaults() {
        return new BattleUiLayout();
    }

    public void validate() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                "Unsupported battle UI layout schema " + schemaVersion);
        }
        requireRange("referenceWidth", referenceWidth, 640f, 7680f);
        requireRange("referenceHeight", referenceHeight, 480f, 4320f);
        if (referenceWidth != UiScaleSystem.GAMEPLAY_REFERENCE_WIDTH
            || referenceHeight != UiScaleSystem.GAMEPLAY_REFERENCE_HEIGHT) {
            throw new IllegalArgumentException("Shared battle reference resolution must be "
                + UiScaleSystem.GAMEPLAY_REFERENCE_WIDTH + " x "
                + UiScaleSystem.GAMEPLAY_REFERENCE_HEIGHT);
        }
        if (execution == null) throw new IllegalArgumentException("execution layout is required");
        if (planner == null) throw new IllegalArgumentException("planner layout is required");

        execution.validate();
        planner.validate();
        if (execution.textGeometryScale != UiScaleSystem.GAMEPLAY_TEXT_SCALE
            || planner.textGeometryScale != UiScaleSystem.GAMEPLAY_TEXT_SCALE) {
            throw new IllegalArgumentException(
                "Battle text geometry scale must be " + UiScaleSystem.GAMEPLAY_TEXT_SCALE);
        }
    }

    private static void requireRange(String name, float value, float minimum, float maximum) {
        if (!Float.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                name + " must be between " + minimum + " and " + maximum);
        }
    }

    private static void requireOrdered(
        String minimumName,
        float minimum,
        String maximumName,
        float maximum
    ) {
        if (minimum > maximum) {
            throw new IllegalArgumentException(
                minimumName + " cannot exceed " + maximumName);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Execution {
        public float textGeometryScale = UiScaleSystem.GAMEPLAY_TEXT_SCALE;
        public float outerMarginFraction = 0.035f;
        public float outerMarginMin = 16f;
        public float outerMarginMax = 32f;
        public float logLineSpacing = 1.7f;
        public float hudScale = 1.25f;
        public float meterHudGap = 14f;
        public float miraclesWidthFraction = 0.11f;
        public float ratioWidthFraction = 0.12f;

        public Execution() {
        }

        private Execution(Execution source) {
            textGeometryScale = source.textGeometryScale;
            outerMarginFraction = source.outerMarginFraction;
            outerMarginMin = source.outerMarginMin;
            outerMarginMax = source.outerMarginMax;
            logLineSpacing = source.logLineSpacing;
            hudScale = source.hudScale;
            meterHudGap = source.meterHudGap;
            miraclesWidthFraction = source.miraclesWidthFraction;
            ratioWidthFraction = source.ratioWidthFraction;
        }

        private void validate() {
            requireRange("execution.textGeometryScale", textGeometryScale, 1f, 3f);
            requireRange("execution.outerMarginFraction", outerMarginFraction, 0f, 0.25f);
            requireRange("execution.outerMarginMin", outerMarginMin, 0f, 300f);
            requireRange("execution.outerMarginMax", outerMarginMax, 0f, 500f);
            requireOrdered("execution.outerMarginMin", outerMarginMin,
                "execution.outerMarginMax", outerMarginMax);
            requireRange("execution.logLineSpacing", logLineSpacing, 0.5f, 4f);
            requireRange("execution.hudScale", hudScale, 0.25f, 3f);
            requireRange("execution.meterHudGap", meterHudGap, 0f, 500f);
            requireRange("execution.miraclesWidthFraction", miraclesWidthFraction, 0.01f, 0.50f);
            requireRange("execution.ratioWidthFraction", ratioWidthFraction, 0.01f, 0.50f);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Planner {
        public float textGeometryScale = UiScaleSystem.GAMEPLAY_TEXT_SCALE;

        public Planner() {
        }

        private Planner(Planner source) {
            textGeometryScale = source.textGeometryScale;
        }

        private void validate() {
            requireRange("planner.textGeometryScale", textGeometryScale, 1f, 3f);
        }
    }
}
