package com.jjktbf;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.character.StatKey;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleStatMode;
import com.jjktbf.model.progression.TechniqueMasteryProgressionData;
import com.jjktbf.model.progression.TechniqueMasteryProgressions;
import com.jjktbf.model.progression.TechniqueMasteryResolver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Mechanics for per-field effect progressions that scale with any selection of
 * core stats. All content is constructed in code, never read from data/.
 */
class StatScaledProgressionTest {

    @Test
    void legacyProgressionsWithoutScalingStatsStillUseCtm() {
        TechniqueMasteryProgressionData progression = formula("ctm / 2");
        assertNull(progression.scalingStats);
        assertEquals(TechniqueMasteryProgressions.CTM_VARIABLE,
            progression.formulaVariable());
        assertEquals(40, progression.resolve(80, "ctm"));
        assertNull(progression.validationError());

        // Legacy alias input still resolves through the combatant path.
        BattleCombatant owner = combatant(stats -> stats
            .cursedTechniqueMastery(80)
            .jujutsuSkill(200));
        AbilityEffectData effect = AbilityEffectType.STAT_ADD.createDefault();
        effect.intValue = 10;
        effect.masteryProgression = Map.of(
            TechniqueMasteryProgressions.INT_VALUE, formula("ctm / 4"));
        assertEquals(20, TechniqueMasteryResolver.resolve(effect, owner).intValue);
    }

    @Test
    void singleStatProgressionUsesItsOwnFormulaVariable() {
        TechniqueMasteryProgressionData progression = formula("js / 2");
        progression.scalingStats = List.of("jujutsuSkill");

        assertEquals("js", progression.formulaVariable());
        assertEquals(StatKey.JUJUTSU_SKILL.label, progression.variableLabel());
        assertNull(progression.validationError());
        assertEquals(100, progression.resolve(200, "ctm"));

        BattleCombatant owner = combatant(stats -> stats
            .jujutsuSkill(200)
            .cursedTechniqueMastery(0));
        AbilityEffectData effect = AbilityEffectType.MODIFY_AP_BAR.createDefault();
        effect.intValue = 10;
        effect.masteryProgression = Map.of(
            TechniqueMasteryProgressions.INT_VALUE, progression);
        assertEquals(100, TechniqueMasteryResolver.resolve(effect, owner).intValue);
    }

    @Test
    void multipleStatsAreSummedBeforeTheProgressionApplies() {
        TechniqueMasteryProgressionData progression = formula("value / 2");
        progression.scalingStats = List.of("cursedTechniqueMastery", "jujutsuSkill");

        assertEquals("value", progression.formulaVariable());
        assertEquals(600, progression.maxScalingInput());
        assertNull(progression.validationError());

        BattleCombatant owner = combatant(stats -> stats
            .cursedTechniqueMastery(80)
            .jujutsuSkill(120));
        AbilityEffectData effect = AbilityEffectType.MODIFY_AP_BAR.createDefault();
        effect.intValue = 10;
        effect.masteryProgression = Map.of(
            TechniqueMasteryProgressions.INT_VALUE, progression);
        // 80 + 120 = 200 combined, 200 / 2 = 100.
        assertEquals(100, TechniqueMasteryResolver.resolve(effect, owner).intValue);
    }

    @Test
    void benchmarkBoundariesMayExceedSingleStatRangeWhenStatsCombine() {
        TechniqueMasteryProgressionData progression = benchmarks(
            0, 5,
            400, 10);
        progression.scalingStats = List.of("ctm", "js");
        assertNull(progression.validationError());

        BattleCombatant low = combatant(stats -> stats
            .cursedTechniqueMastery(150).jujutsuSkill(150));
        BattleCombatant high = combatant(stats -> stats
            .cursedTechniqueMastery(300).jujutsuSkill(200));

        assertEquals(5, resolveIntValue(progression, low));
        assertEquals(10, resolveIntValue(progression, high));

        // The same boundary table is invalid for a single-stat progression.
        TechniqueMasteryProgressionData single = benchmarks(
            0, 5,
            400, 10);
        assertNotNull(single.validationError());
    }

    @Test
    void eachStatIsClampedToTheAuthoredRangeBeforeSumming() {
        // Runtime CE output effects can push a stat past 300; the progression
        // input stays capped at the authored maximum.
        CharacterStats boostedStats = new CharacterStats.Builder()
            .jujutsuSkill(300).build()
            .withCursedEnergyOutput(999);
        BattleCombatant boosted = new BattleCombatant(new SorcererCharacter(
            "BOOSTED", "Boosted", boostedStats, null, List.of(), List.of()),
            List.of(), BattleStatMode.STANDARD);

        assertEquals(300, TechniqueMasteryResolver.statForProgression(
            boosted, StatKey.CURSED_ENERGY_OUTPUT));

        TechniqueMasteryProgressionData progression = formula("value");
        progression.scalingStats = List.of("cursedEnergyOutput", "jujutsuSkill");
        AbilityEffectData effect = AbilityEffectType.MODIFY_AP_BAR.createDefault();
        effect.intValue = 0;
        effect.masteryProgression = Map.of(
            TechniqueMasteryProgressions.INT_VALUE, progression);
        // 300 (clamped output) + 300 = 600 combined.
        assertEquals(600, TechniqueMasteryResolver.resolve(effect, boosted).intValue);
    }

    @Test
    void equalizedBattlesScaleEveryProgressionStat() {
        SorcererCharacter character = new SorcererCharacter(
            "EQ", "EQ",
            new CharacterStats.Builder()
                .jujutsuSkill(300).cursedTechniqueMastery(300).build(),
            null, List.of(), List.of());
        BattleCombatant equalized = new BattleCombatant(
            character, character.getAbilities(), BattleStatMode.EQUALIZED);

        assertEquals(BattleStatMode.EQUALIZED.scale(300),
            TechniqueMasteryResolver.statForProgression(
                equalized, StatKey.JUJUTSU_SKILL));
        assertEquals(TechniqueMasteryResolver.masteryOf(equalized),
            TechniqueMasteryResolver.statForProgression(
                equalized, StatKey.CURSED_TECHNIQUE_MASTERY));
    }

    @Test
    void scalingStatsAcceptAliasesAndDeduplicate() {
        TechniqueMasteryProgressionData progression = formula("value / 2");
        progression.scalingStats = List.of("js", "jujutsuSkill", "CTMastery");

        assertEquals(List.of(StatKey.JUJUTSU_SKILL, StatKey.CURSED_TECHNIQUE_MASTERY),
            progression.effectiveScalingStats());
        assertEquals("value", progression.formulaVariable());
        assertNull(progression.validationError());
    }

    @Test
    void invalidScalingStatsAreRejectedByValidation() {
        TechniqueMasteryProgressionData empty = formula("ctm / 2");
        empty.scalingStats = new ArrayList<>();
        assertEquals("Select at least one scaling stat.", empty.validationError());

        TechniqueMasteryProgressionData unknown = formula("ctm / 2");
        unknown.scalingStats = List.of("banana");
        assertEquals("Unknown scaling stat: banana", unknown.validationError());
    }

    @Test
    void formulasMustReferenceTheActiveVariable() {
        TechniqueMasteryProgressionData progression = formula("ctm / 2");
        progression.scalingStats = List.of("jujutsuSkill");
        assertNotNull(progression.validationError());

        progression.formula = "js / 2";
        assertNull(progression.validationError());
    }

    @Test
    void effectValidationProbesTheCombinedStatRange() {
        AbilityEffectData effect = AbilityEffectType.STAT_SET_VALUE.createDefault();
        effect.intValue = 10;
        TechniqueMasteryProgressionData progression = benchmarks(
            0, 10,
            450, 300);
        progression.scalingStats = List.of("ctm", "js");
        effect.masteryProgression = new LinkedHashMap<>();
        effect.masteryProgression.put(TechniqueMasteryProgressions.INT_VALUE, progression);
        assertNull(AbilityEffectType.STAT_SET_VALUE.validationError(effect));

        // Negative stat values are invalid once the combined value crosses 450.
        progression.benchmarks.get(1).value = -1;
        assertNotNull(AbilityEffectType.STAT_SET_VALUE.validationError(effect));
    }

    @Test
    void conditionProgressionsScaleWithSelectedStats() {
        var condition = com.jjktbf.model.character.AbilityConditionType.DAMAGE_DEALT_AT_LEAST
            .createDefault();
        TechniqueMasteryProgressionData progression = formula("str");
        progression.scalingStats = List.of("strength");
        condition.masteryProgression = Map.of(
            TechniqueMasteryProgressions.AMOUNT, progression);

        BattleCombatant owner = combatant(stats -> stats.strength(64));
        assertEquals(64, TechniqueMasteryResolver.resolve(condition, owner).amount);
    }

    @Test
    void scalingStatListSurvivesCopy() {
        TechniqueMasteryProgressionData progression = formula("value");
        progression.scalingStats = List.of("ctm", "js");
        TechniqueMasteryProgressionData copy = progression.copy();
        assertEquals(progression.scalingStats, copy.scalingStats);
        assertEquals(progression.formulaVariable(), copy.formulaVariable());
    }

    private static int resolveIntValue(
        TechniqueMasteryProgressionData progression,
        BattleCombatant owner
    ) {
        AbilityEffectData effect = AbilityEffectType.MODIFY_AP_BAR.createDefault();
        effect.intValue = 0;
        effect.masteryProgression = Map.of(
            TechniqueMasteryProgressions.INT_VALUE, progression);
        return TechniqueMasteryResolver.resolve(effect, owner).intValue;
    }

    private interface StatsConfig {
        CharacterStats.Builder configure(CharacterStats.Builder builder);
    }

    private static BattleCombatant combatant(StatsConfig config) {
        CharacterStats stats = config.configure(new CharacterStats.Builder()).build();
        SorcererCharacter character = new SorcererCharacter(
            "OWNER", "Owner", stats, null, List.of(), List.of());
        return new BattleCombatant(character, character.getAbilities(),
            BattleStatMode.STANDARD);
    }

    private static TechniqueMasteryProgressionData formula(String expression) {
        TechniqueMasteryProgressionData data = new TechniqueMasteryProgressionData();
        data.mode = TechniqueMasteryProgressionData.FORMULA;
        data.formula = expression;
        return data;
    }

    private static TechniqueMasteryProgressionData benchmarks(int... pairs) {
        TechniqueMasteryProgressionData data = new TechniqueMasteryProgressionData();
        data.mode = TechniqueMasteryProgressionData.BENCHMARKS;
        data.benchmarks = new ArrayList<>();
        for (int index = 0; index < pairs.length; index += 2) {
            data.benchmarks.add(new TechniqueMasteryProgressionData.BenchmarkData(
                pairs[index], pairs[index + 1]));
        }
        return data;
    }
}
