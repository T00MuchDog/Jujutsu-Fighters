package com.jjktbf.model.text;

import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.coded.RatioAbility;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.progression.TechniqueMasteryProgressionData;
import com.jjktbf.model.progression.TechniqueMasteryProgressions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveDescriptionVariablesTest {

    @Test
    void codedMoveFieldsUseTheirCurrentCtmValues() {
        MoveEffectData effect = ratioStacksEffect();
        String description = "Add :effect-000000.codedStackCount: stacks for "
            + ":effect-000000.stackDurationTicks: AP ticks.";

        assertEquals("Add 1 stacks for 55 AP ticks.",
            MoveDescriptionVariables.resolve(description, List.of(effect), 100));
        assertEquals("Add 2 stacks for 65 AP ticks.",
            MoveDescriptionVariables.resolve(description, List.of(effect), 200));
        assertNull(MoveDescriptionVariables.validationError(description, List.of(effect)));

        List<String> tokens = MoveDescriptionVariables.variables(List.of(effect)).stream()
            .map(MoveDescriptionVariables.Variable::token)
            .toList();
        assertTrue(tokens.contains(":effect-000000.codedStackCount:"));
        assertTrue(tokens.contains(":effect-000000.triggerChancePercent:"));
    }

    @Test
    void percentagesAndMultipliersUseEditorFacingUnits() {
        MoveEffectData percentage = effect("effect-000000", AbilityEffectType.HEAL_HP);
        percentage.valueMode = AbilityEffectType.ValueMode.PERCENT.name();
        percentage.doubleValue = 0.25;
        percentage.masteryProgression = Map.of(
            TechniqueMasteryProgressions.DOUBLE_VALUE,
            benchmarks(0, 25, 100, 40));

        MoveEffectData multiplier = effect("effect-000001", AbilityEffectType.DAMAGE_MULTIPLY);
        multiplier.doubleValue = 1.1;
        multiplier.masteryProgression = Map.of(
            TechniqueMasteryProgressions.DOUBLE_VALUE,
            benchmarks(0, 110, 100, 150));

        String description = "Heal :effect-000000.doubleValue:%; deal x"
            + ":effect-000001.doubleValue:.";

        assertEquals("Heal 40%; deal x1.5.", MoveDescriptionVariables.resolve(
            description, List.of(percentage, multiplier), 100));

        percentage.masteryProgression = null;
        percentage.doubleValue = 0.29;
        assertEquals("Heal 29%; deal x1.5.", MoveDescriptionVariables.resolve(
            description, List.of(percentage, multiplier), 100));
    }

    @Test
    void moveEffectActivationChanceIsAvailableAsAPercentage() {
        MoveEffectData effect = effect("effect-000000", AbilityEffectType.HEAL_HP);
        effect.intValue = 10;
        effect.activationChanceEnabled = true;
        effect.activationChance = 0.25;
        effect.activationMasteryProgression = Map.of(
            TechniqueMasteryProgressions.ACTIVATION_CHANCE,
            benchmarks(0, 25, 150, 60));

        String token = ":effect-000000.activationChance:";

        assertEquals("Chance 25%", MoveDescriptionVariables.resolve(
            "Chance " + token + "%", List.of(effect), 100));
        assertEquals("Chance 60%", MoveDescriptionVariables.resolve(
            "Chance " + token + "%", List.of(effect), 150));
    }

    @Test
    void stableEffectIdsKeepTokensCorrectAfterRowsAreReordered() {
        MoveEffectData first = effect("effect-000000", AbilityEffectType.HEAL_HP);
        first.intValue = 3;
        MoveEffectData second = effect("effect-000001", AbilityEffectType.HEAL_HP);
        second.intValue = 7;

        assertEquals("Restore 3 HP", MoveDescriptionVariables.resolve(
            "Restore :effect-000000.intValue: HP", List.of(second, first), 0));
    }

    @Test
    void invalidReferencesAreDiagnosableWithoutDestroyingAuthoredText() {
        MoveEffectData effect = effect("effect-000000", AbilityEffectType.HEAL_HP);
        effect.intValue = 3;

        assertEquals("Restore :effect-999999.intValue: HP", MoveDescriptionVariables.resolve(
            "Restore :effect-999999.intValue: HP", List.of(effect), 0));
        assertEquals(
            "Unknown move description variable :effect-999999.intValue:.",
            MoveDescriptionVariables.validationError(
                "Restore :effect-999999.intValue: HP", List.of(effect)));
        assertEquals(
            "Close the move description variable that starts at character 9.",
            MoveDescriptionVariables.validationError(
                "Restore :effect-000000.intValue", List.of(effect)));
    }

    private static MoveEffectData ratioStacksEffect() {
        MoveEffectData effect = effect("effect-000000", AbilityEffectType.CODED_MOVE_ACTION);
        effect.codedAbilityKey = RatioAbility.KEY;
        effect.codedAction = RatioAbility.RATIO_EFFECT;
        effect.codedTarget = RatioAbility.CREATE_STACKS;
        effect.codedStackCount = 1;
        effect.codedParameters = Map.of(
            RatioAbility.STACK_DURATION_PARAMETER, 50,
            RatioAbility.TRIGGER_CHANCE_PERCENT, 70,
            RatioAbility.DEFENSE_PERCENT, 30);
        effect.masteryProgression = Map.of(
            TechniqueMasteryProgressions.CODED_STACK_COUNT,
            benchmarks(0, 1, 200, 2),
            RatioAbility.STACK_DURATION_PARAMETER,
            benchmarks(0, 45, 50, 50, 100, 55, 150, 60, 200, 65));
        return effect;
    }

    private static MoveEffectData effect(String id, AbilityEffectType type) {
        MoveEffectData effect = new MoveEffectData();
        effect.effectId = id;
        effect.type = type.name();
        return effect;
    }

    private static TechniqueMasteryProgressionData benchmarks(int... values) {
        TechniqueMasteryProgressionData progression = new TechniqueMasteryProgressionData();
        progression.mode = TechniqueMasteryProgressionData.BENCHMARKS;
        progression.benchmarks = new java.util.ArrayList<>();
        for (int index = 0; index < values.length; index += 2) {
            progression.benchmarks.add(new TechniqueMasteryProgressionData.BenchmarkData(
                values[index], values[index + 1]));
        }
        return progression;
    }
}
