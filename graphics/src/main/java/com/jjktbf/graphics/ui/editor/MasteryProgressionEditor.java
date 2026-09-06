package com.jjktbf.graphics.ui.editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.jjktbf.graphics.ui.DynamicSelectBox;
import com.jjktbf.graphics.ui.profile.UiProfile;
import com.jjktbf.model.character.StatKey;
import com.jjktbf.model.progression.TechniqueMasteryProgressionData;
import com.jjktbf.model.progression.TechniqueMasteryProgressions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Per-field formula/benchmark editor with a live bounded-stat preview. The
 * progression may be driven by any selection of core stats; the selected stats
 * are summed before the formula or benchmark table is applied.
 */
public final class MasteryProgressionEditor extends Table {

    private final String field;
    private final IntSupplier literalValue;
    private final Supplier<Map<String, TechniqueMasteryProgressionData>> getter;
    private final Consumer<Map<String, TechniqueMasteryProgressionData>> setter;
    private final Runnable onDirty;
    private final Skin skin;
    private final UiProfile uiProfile;
    private final boolean windowsLayout;
    /** Fixed-stat variant (Cursed Energy Efficiency): no stat selector shown. */
    private final String statLabel;
    private final String variableName;
    private final boolean fixedStat;
    /** Stats that cannot be selected because the effect modifies them itself. */
    private final Set<StatKey> excludedStats;
    private final Container<Actor> details = new Container<>();

    public MasteryProgressionEditor(
        String field,
        IntSupplier literalValue,
        Supplier<Map<String, TechniqueMasteryProgressionData>> getter,
        Consumer<Map<String, TechniqueMasteryProgressionData>> setter,
        Runnable onDirty,
        UiProfile uiProfile,
        Skin skin
    ) {
        this(field, literalValue, getter, setter, onDirty, uiProfile, skin,
            "CTM", TechniqueMasteryProgressions.CTM_VARIABLE, false, Set.of());
    }

    public MasteryProgressionEditor(
        String field,
        IntSupplier literalValue,
        Supplier<Map<String, TechniqueMasteryProgressionData>> getter,
        Consumer<Map<String, TechniqueMasteryProgressionData>> setter,
        Runnable onDirty,
        UiProfile uiProfile,
        Skin skin,
        String statLabel,
        String variableName
    ) {
        this(field, literalValue, getter, setter, onDirty, uiProfile, skin,
            statLabel, variableName, true, Set.of());
    }

    public MasteryProgressionEditor(
        String field,
        IntSupplier literalValue,
        Supplier<Map<String, TechniqueMasteryProgressionData>> getter,
        Consumer<Map<String, TechniqueMasteryProgressionData>> setter,
        Runnable onDirty,
        UiProfile uiProfile,
        Skin skin,
        Set<StatKey> excludedStats
    ) {
        this(field, literalValue, getter, setter, onDirty, uiProfile, skin,
            "CTM", TechniqueMasteryProgressions.CTM_VARIABLE, false, excludedStats);
    }

    private MasteryProgressionEditor(
        String field,
        IntSupplier literalValue,
        Supplier<Map<String, TechniqueMasteryProgressionData>> getter,
        Consumer<Map<String, TechniqueMasteryProgressionData>> setter,
        Runnable onDirty,
        UiProfile uiProfile,
        Skin skin,
        String statLabel,
        String variableName,
        boolean fixedStat,
        Set<StatKey> excludedStats
    ) {
        super(skin);
        this.field = field;
        this.literalValue = literalValue;
        this.getter = getter;
        this.setter = setter;
        this.onDirty = onDirty == null ? () -> { } : onDirty;
        this.uiProfile = uiProfile;
        this.windowsLayout = uiProfile == UiProfile.WINDOWS;
        this.skin = skin;
        this.statLabel = statLabel;
        this.variableName = variableName;
        this.fixedStat = fixedStat;
        this.excludedStats = excludedStats == null ? Set.of() : excludedStats;
        defaults().left().pad(3).growX();
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        String checkboxLabel = fixedStat
            ? " Scale this value with " + statLabel
            : " Scale this value with stats";
        CheckBox enabled = new CheckBox(checkboxLabel, skin);
        enabled.setChecked(progression() != null);
        enabled.addListener(change(() -> {
            if (enabled.isChecked()) {
                TechniqueMasteryProgressionData data = new TechniqueMasteryProgressionData();
                data.mode = TechniqueMasteryProgressionData.FORMULA;
                data.formula = String.valueOf(literalValue.getAsInt());
                if (!fixedStat) {
                    data.scalingStats = new ArrayList<>(List.of(
                        StatKey.CURSED_TECHNIQUE_MASTERY.fieldName));
                }
                put(data);
            } else {
                removeProgression();
            }
            onDirty.run();
            rebuild();
        }));
        add(enabled).row();
        details.fill(true, false);
        details.setActor(enabled.isChecked() ? buildDetails() : null);
        add(details).growX().row();
    }

    private Actor buildDetails() {
        TechniqueMasteryProgressionData data = progression();
        Table table = new Table(skin);
        table.defaults().left().pad(3).growX();
        if (data == null) return table;

        if (!fixedStat) buildStatSelector(table, data);

        SelectBox<String> mode = new DynamicSelectBox<>(skin, uiProfile);
        mode.setItems("Formula", "Benchmarks");
        mode.setSelected(TechniqueMasteryProgressionData.BENCHMARKS.equals(data.mode)
            ? "Benchmarks" : "Formula");
        mode.addListener(change(() -> {
            if ("Benchmarks".equals(mode.getSelected())) {
                data.mode = TechniqueMasteryProgressionData.BENCHMARKS;
                data.formula = null;
                data.benchmarks = new ArrayList<>();
                data.benchmarks.add(new TechniqueMasteryProgressionData.BenchmarkData(
                    0, literalValue.getAsInt()));
            } else {
                data.mode = TechniqueMasteryProgressionData.FORMULA;
                data.formula = String.valueOf(literalValue.getAsInt());
                data.benchmarks = null;
            }
            onDirty.run();
            rebuild();
        }));
        addRow(table, "Progression mode", mode);

        if (TechniqueMasteryProgressionData.BENCHMARKS.equals(data.mode)) {
            buildBenchmarks(table, data);
        } else {
            data.mode = TechniqueMasteryProgressionData.FORMULA;
            if (data.formula == null) data.formula = String.valueOf(literalValue.getAsInt());
            TextField formula = new HoverTextField(data.formula, skin);
            Label preview = previewLabel(data);
            formula.addListener(change(() -> {
                data.formula = formula.getText();
                preview.setText(previewText(data, activeVariable(data),
                    activeVariableLabel(data)));
                onDirty.run();
            }));
            addRow(table, "Formula", formula);
            Label hint = new Label(formulaHint(data), skin, "small");
            hint.setColor(skin.get("text-dim", Color.class));
            hint.setWrap(true);
            addWideLabel(table, hint);
            addWideLabel(table, preview);
        }
        return table;
    }

    /** Row of core-stat checkboxes; every checked stat contributes to the sum. */
    private void buildStatSelector(Table table, TechniqueMasteryProgressionData data) {
        Table row = new Table(skin);
        Set<StatKey> selected = new LinkedHashSet<>(data.effectiveScalingStats());
        for (StatKey stat : StatKey.values()) {
            boolean excluded = excludedStats.contains(stat);
            CheckBox box = new CheckBox(" " + stat.label, skin);
            box.setChecked(selected.contains(stat));
            box.setDisabled(excluded);
            if (excluded) box.setChecked(false);
            box.addListener(change(() -> {
                if (box.isDisabled()) return;
                Set<StatKey> next = new LinkedHashSet<>(data.effectiveScalingStats());
                if (box.isChecked()) next.add(stat);
                else next.remove(stat);
                data.scalingStats = new ArrayList<>(next.size());
                for (StatKey chosen : next) {
                    data.scalingStats.add(chosen.fieldName);
                }
                onDirty.run();
                rebuild();
            }));
            row.add(box).padRight(8);
        }
        addRow(table, "Scaling stats", row);
        if (data.effectiveScalingStats().size() > 1) {
            Label sumHint = new Label(
                "The selected stats are summed, then the progression is applied to the total.",
                skin, "small");
            sumHint.setColor(skin.get("text-dim", Color.class));
            sumHint.setWrap(true);
            addWideLabel(table, sumHint);
        }
    }

    private void buildBenchmarks(Table table, TechniqueMasteryProgressionData data) {
        if (data.benchmarks == null || data.benchmarks.isEmpty()) {
            data.benchmarks = new ArrayList<>();
            data.benchmarks.add(new TechniqueMasteryProgressionData.BenchmarkData(
                0, literalValue.getAsInt()));
        }
        Label preview = previewLabel(data);
        int maxInput = data.maxScalingInput();
        String inputLabel = fixedStat ? statLabel : activeVariable(data);
        for (int index = 0; index < data.benchmarks.size(); index++) {
            int rowIndex = index;
            TechniqueMasteryProgressionData.BenchmarkData benchmark = data.benchmarks.get(index);
            Table row = new Table(skin);
            TextField mastery = integerField(benchmark.mastery);
            mastery.setDisabled(index == 0);
            TextField value = integerField(benchmark.value);
            mastery.addListener(change(() -> {
                Integer parsed = parseInteger(mastery.getText());
                if (parsed != null) benchmark.mastery = parsed;
                preview.setText(previewText(data, activeVariable(data),
                    activeVariableLabel(data)));
                onDirty.run();
            }));
            value.addListener(change(() -> {
                Integer parsed = parseInteger(value.getText());
                if (parsed != null) benchmark.value = parsed;
                preview.setText(previewText(data, activeVariable(data),
                    activeVariableLabel(data)));
                onDirty.run();
            }));
            row.add(new Label(inputLabel, skin)).padRight(3);
            row.add(mastery).width(windowsLayout ? 105f : 70f).padRight(6);
            row.add(new Label("Value", skin)).padRight(3);
            row.add(value).width(windowsLayout ? 135f : 90f);
            if (index > 0) {
                TextButton remove = new TextButton("X", skin);
                remove.addListener(change(() -> {
                    data.benchmarks.remove(rowIndex);
                    onDirty.run();
                    rebuild();
                }));
                row.add(remove).padLeft(5);
            }
            table.add(row).colspan(2).growX().row();
        }
        TextButton add = new TextButton("+ Add benchmark", skin);
        add.setDisabled(data.benchmarks.get(data.benchmarks.size() - 1).mastery >= maxInput);
        add.addListener(change(() -> {
            if (add.isDisabled()) return;
            TechniqueMasteryProgressionData.BenchmarkData last =
                data.benchmarks.get(data.benchmarks.size() - 1);
            data.benchmarks.add(new TechniqueMasteryProgressionData.BenchmarkData(
                Math.min(maxInput, last.mastery + 20), last.value));
            onDirty.run();
            rebuild();
        }));
        table.add(add).colspan(2).left().row();
        addWideLabel(table, preview);
    }

    private String activeVariable(TechniqueMasteryProgressionData data) {
        return fixedStat ? variableName : data.formulaVariable();
    }

    private String activeVariableLabel(TechniqueMasteryProgressionData data) {
        return fixedStat ? statLabel : data.variableLabel();
    }

    private String formulaHint(TechniqueMasteryProgressionData data) {
        String variable = activeVariable(data);
        String hint = "Use " + variable
            + ", integers, + - * / %, parentheses, min, max, and clamp. "
            + "Result floors to an integer.";
        if (!fixedStat && data.effectiveScalingStats().size() > 1) {
            hint = variable + " is the sum of the selected stats. " + hint;
        }
        return hint;
    }

    private Label previewLabel(TechniqueMasteryProgressionData data) {
        Label label = new Label(
            previewText(data, activeVariable(data), activeVariableLabel(data)),
            skin, "small");
        label.setWrap(true);
        return label;
    }

    static String previewText(TechniqueMasteryProgressionData data) {
        return previewText(data, TechniqueMasteryProgressions.CTM_VARIABLE, "CTM");
    }

    private static String previewText(
        TechniqueMasteryProgressionData data,
        String variableName,
        String variableLabel
    ) {
        String error = data == null ? "Progression is missing."
            : data.validationError(variableName, variableLabel);
        if (error != null) return "Invalid progression: " + error;
        int maxInput = data.maxScalingInput();
        int step = Math.max(1, maxInput / 15);
        StringBuilder text = new StringBuilder("Preview: ");
        for (int value = 0; value <= maxInput; value += step) {
            if (value > 0) text.append(" | ");
            text.append(value).append(": ").append(data.resolve(value, variableName));
        }
        return text.toString();
    }

    private TechniqueMasteryProgressionData progression() {
        Map<String, TechniqueMasteryProgressionData> values = getter.get();
        return values == null ? null : values.get(field);
    }

    private void put(TechniqueMasteryProgressionData progression) {
        Map<String, TechniqueMasteryProgressionData> values = getter.get();
        if (values == null) values = new LinkedHashMap<>();
        else values = new LinkedHashMap<>(values);
        values.put(field, progression);
        setter.accept(values);
    }

    private void removeProgression() {
        Map<String, TechniqueMasteryProgressionData> values = getter.get();
        if (values == null) return;
        values = new LinkedHashMap<>(values);
        values.remove(field);
        setter.accept(values.isEmpty() ? null : values);
    }

    private TextField integerField(int value) {
        TextField field = new HoverTextField(String.valueOf(value), skin);
        field.setTextFieldFilter((textField, character) ->
            Character.isDigit(character) || character == '-');
        return field;
    }

    private void addWideLabel(Table table, Label label) {
        if (windowsLayout) {
            table.add(label).colspan(2).minWidth(0f).prefWidth(750f)
                .maxWidth(750f).growX().row();
        } else {
            table.add(label).colspan(2).width(500f).growX().row();
        }
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) return null;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static void addRow(Table table, String label, Actor actor) {
        table.add(new Label(label, table.getSkin())).padRight(8);
        table.add(actor).growX().row();
    }

    private static ChangeListener change(Runnable action) {
        return new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        };
    }
}
