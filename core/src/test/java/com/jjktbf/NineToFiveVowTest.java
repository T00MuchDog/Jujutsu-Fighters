package com.jjktbf;

import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityConditionData;
import com.jjktbf.model.character.AbilityConditionRuleData;
import com.jjktbf.model.character.AbilityConditionType;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.Equipment;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.SeededRandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two-phase binding vow shape: a battle-start stat penalty with a finite
 * duration hands off to a permanent bonus once a set number of rounds have
 * passed — the mechanic behind the reworked Nine-to-Five Binding Vow, authored
 * here against in-code characters.
 */
class NineToFiveVowTest {

    private static final String[] VOW_STATS = {
        "vitality", "strength", "durability", "speed",
        "cursedEnergyReserves", "cursedEnergyOutput"
    };
    private static final int WORK_HOURS_ROUNDS = 5;
    private static final int OVERTIME_ROUND = 6;

    @Test
    void vowHoldsBackThroughRoundFiveThenOvertimeKicksIn() {
        BattleCombatant owner = combatantWithVow();
        BattleCombatant enemy = combatantWithoutVow();
        BattleState state = new BattleState(owner, enemy);
        CombatResolver resolver = new CombatResolver(new SeededRandomSource(1L));

        for (int round = 1; round <= 7; round++) {
            state.transitionTo(BattleState.Phase.PLANNING);
            resolver.processRoundStart(state);
            int expectedStrength = round < OVERTIME_ROUND ? 80 : 120;
            assertEquals(expectedStrength, owner.getEffectiveStats().getStrength(),
                "round " + round + " strength should be base 100 x "
                    + (round < OVERTIME_ROUND ? "0.8" : "1.2"));
            assertEquals(110, owner.getEffectiveStats().getCombatAbility(),
                "stats outside the vow never change");
            state.transitionTo(BattleState.Phase.ROUND_END);
            resolver.processRoundEnd(state);
        }
    }

    @Test
    void startingPoolsAreClampedToTheNerfedMaximumAtBattleStart() {
        BattleCombatant owner = combatantWithVow();
        BattleCombatant baseline = combatantWithoutVow();
        assertEquals(baseline.getMaxHp(), owner.getMaxHp(),
            "before battle-start triggers the vow has not applied yet");
        BattleState state = new BattleState(owner, combatantWithoutVow());
        CombatResolver resolver = new CombatResolver(new SeededRandomSource(1L));

        state.transitionTo(BattleState.Phase.PLANNING);
        resolver.processRoundStart(state);

        assertTrue(owner.getMaxHp() < baseline.getMaxHp(),
            "the nerfed vitality lowers max HP");
        assertEquals(owner.getMaxHp(), owner.getCurrentHp(),
            "the pool is clamped down to the nerfed maximum at battle start");
        assertEquals(owner.getMaxCursedEnergy(), owner.getCurrentCe(),
            "CE is clamped down to the nerved maximum at battle start");
    }

    @Test
    void overtimeRaisesTheResourceMaximaFromRoundSix() {
        BattleCombatant owner = combatantWithVow();
        BattleCombatant enemy = combatantWithoutVow();
        BattleState state = new BattleState(owner, enemy);
        CombatResolver resolver = new CombatResolver(new SeededRandomSource(1L));

        runRound(state, resolver);
        runRound(state, resolver);
        runRound(state, resolver);
        runRound(state, resolver);
        runRound(state, resolver);
        int nerfedMaxHp = owner.getMaxHp();
        int nerfedMaxCe = owner.getMaxCursedEnergy();

        runRound(state, resolver);

        assertTrue(owner.getMaxHp() > nerfedMaxHp,
            "overtime vitality grows max HP");
        assertTrue(owner.getMaxCursedEnergy() > nerfedMaxCe,
            "overtime cursed energy reserves grow max CE");
    }

    private static void runRound(BattleState state, CombatResolver resolver) {
        state.transitionTo(BattleState.Phase.PLANNING);
        resolver.processRoundStart(state);
        state.transitionTo(BattleState.Phase.ROUND_END);
        resolver.processRoundEnd(state);
    }

    /** The reworked vow: 0.8 on the vow stats for five rounds, 1.2 afterwards. */
    private static Ability nineToFiveVow() {
        AbilityData data = new AbilityData();
        data.id = "000040";
        data.name = "Nine-to-Five Binding Vow";
        data.category = "ACTIVE";
        data.sourceType = "CHARACTER";

        List<AbilityEffectData> effects = new ArrayList<>();
        List<String> workHoursEffectIds = new ArrayList<>();
        List<String> overtimeEffectIds = new ArrayList<>();
        for (String stat : VOW_STATS) {
            String effectId = "effect-" + String.format("%06d", workHoursEffectIds.size());
            effects.add(timedMultiply(stat, 0.8, WORK_HOURS_ROUNDS, effectId));
            workHoursEffectIds.add(effectId);
        }
        for (String stat : VOW_STATS) {
            String effectId = "effect-" + String.format("%06d", VOW_STATS.length + overtimeEffectIds.size());
            effects.add(timedMultiply(stat, 1.2, -1, effectId));
            overtimeEffectIds.add(effectId);
        }
        data.effects = effects;

        AbilityConditionRuleData workHours = new AbilityConditionRuleData();
        workHours.condition = AbilityConditionType.BATTLE_STARTED.createDefault();
        workHours.targetEffectIds = workHoursEffectIds;
        workHours.matchSameTrigger = true;

        AbilityConditionRuleData overtime = new AbilityConditionRuleData();
        AbilityConditionData overtimeCondition =
            AbilityConditionType.ROUND_REACHED.createDefault();
        overtimeCondition.round = OVERTIME_ROUND;
        overtime.condition = overtimeCondition;
        overtime.targetEffectIds = overtimeEffectIds;

        data.activationConditions = List.of(workHours, overtime);
        return new Ability(data);
    }

    private static AbilityEffectData timedMultiply(
        String stat, double multiplier, int durationRounds, String effectId
    ) {
        AbilityEffectData effect = AbilityEffectType.TIMED_STAT_MODIFIER.createDefault();
        effect.effectId = effectId;
        effect.statType = "CORE";
        effect.statOperation = "MULTIPLY";
        effect.valueMode = null;
        effect.stat = stat;
        effect.intValue = null;
        effect.doubleValue = multiplier;
        effect.target = "SELF";
        effect.durationRounds = durationRounds;
        effect.durationTicks = 0;
        return effect;
    }

    private static BattleCombatant combatantWithVow() {
        return new BattleCombatant(character(List.of(nineToFiveVow())));
    }

    private static BattleCombatant combatantWithoutVow() {
        return new BattleCombatant(character(List.of()));
    }

    private static SorcererCharacter character(List<Ability> abilities) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(100).strength(100).durability(100).speed(100)
            .cursedEnergyReserves(100).combatAbility(110).build();
        return new SorcererCharacter(
            "SORCERER", "Sorcerer", stats, null, List.of(), abilities, Equipment.NONE);
    }
}
