package com.jjktbf;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectParameter;
import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.BattleStatKey;
import com.jjktbf.model.character.coded.CodedAbilityRegistry;
import com.jjktbf.model.character.coded.CursedSpeechAbility;
import com.jjktbf.model.character.coded.MiraclesAbility;
import com.jjktbf.model.character.coded.NewShadowStyleAbility;
import com.jjktbf.model.character.coded.RatioAbility;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.StatusEffectType;
import com.jjktbf.model.progression.TechniqueMasteryProgressions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityEffectTypeApplicabilityTest {

    @Test
    void statusSubtypesOnlyExposeConsumedAttributes() {
        AbilityEffectData effect = AbilityEffectType.APPLY_STATUS.createDefault();

        effect.stringValue = StatusEffectType.STAGGER.name();
        AbilityEffectType.APPLY_STATUS.prepare(effect);
        assertFalse(AbilityEffectType.APPLY_STATUS.uses(
            AbilityEffectParameter.MAGNITUDE, effect));
        assertNull(effect.magnitude);
        assertFalse(AbilityEffectType.APPLY_STATUS.masteryProgressionFields(effect)
            .contains(TechniqueMasteryProgressions.DURATION_ROUNDS));
        assertTrue(AbilityEffectType.APPLY_STATUS.masteryProgressionFields(effect)
            .contains(TechniqueMasteryProgressions.DURATION_TICKS));

        effect.stringValue = StatusEffectType.POISON.name();
        AbilityEffectType.APPLY_STATUS.prepare(effect);
        assertFalse(AbilityEffectType.APPLY_STATUS.uses(
            AbilityEffectParameter.MAGNITUDE, effect));
        assertTrue(AbilityEffectType.APPLY_STATUS.masteryProgressionFields(effect)
            .contains(TechniqueMasteryProgressions.DURATION_ROUNDS));
        assertFalse(AbilityEffectType.APPLY_STATUS.masteryProgressionFields(effect)
            .contains(TechniqueMasteryProgressions.DURATION_TICKS));

        effect.stringValue = StatusEffectType.RESTRAINED.name();
        AbilityEffectType.APPLY_STATUS.prepare(effect);
        assertTrue(AbilityEffectType.APPLY_STATUS.uses(
            AbilityEffectParameter.MAGNITUDE, effect));
        assertEquals(StatusEffectType.RESTRAINED_DEFAULT_MAGNITUDE, effect.magnitude);
        assertNull(AbilityEffectType.APPLY_STATUS.validationError(effect));
        effect.magnitude = 0.9;
        assertEquals("Restrained magnitude must be between 1 and 10.",
            AbilityEffectType.APPLY_STATUS.validationError(effect));
        effect.magnitude = 10.1;
        assertEquals("Restrained magnitude must be between 1 and 10.",
            AbilityEffectType.APPLY_STATUS.validationError(effect));

        effect.stringValue = StatusEffectType.STRENGTH_DECREASE.name();
        AbilityEffectType.APPLY_STATUS.prepare(effect);
        assertTrue(AbilityEffectType.APPLY_STATUS.uses(
            AbilityEffectParameter.MAGNITUDE, effect));
        assertTrue(effect.magnitude > 0.0);
    }

    @Test
    void fixedFrozenRemovalChanceIsNotAuthorable() {
        AbilityEffectData effect = AbilityEffectType.APPLY_STATUS.createDefault();
        effect.stringValue = StatusEffectType.FROZEN.name();
        effect.perTickRemovalChance = 0.75;

        AbilityEffectType.APPLY_STATUS.prepare(effect);

        assertFalse(AbilityEffectType.APPLY_STATUS.uses(
            AbilityEffectParameter.PER_TICK_REMOVAL_CHANCE, effect));
        assertNull(effect.perTickRemovalChance);
    }

    @Test
    void roundDurationValidationNamesTheSelectedStatus() {
        AbilityEffectData effect = AbilityEffectType.APPLY_STATUS.createDefault();
        effect.stringValue = StatusEffectType.SLEEP.name();
        effect.durationRounds = 0;
        effect.durationTicks = 80;

        assertEquals(
            "Sleep must use a positive round duration or be permanent, with 0 AP ticks.",
            AbilityEffectType.APPLY_STATUS.validationError(effect));

        effect.durationRounds = -1;
        effect.durationTicks = 0;
        assertNull(AbilityEffectType.APPLY_STATUS.validationError(effect));
    }

    @Test
    void statusUpkeepBelongsOnlyToTheMaintenanceEffect() {
        AbilityEffectData status = AbilityEffectType.APPLY_STATUS.createDefault();
        status.ceUpkeepPerTick = 1.5;

        AbilityEffectType.APPLY_STATUS.prepare(status);

        assertFalse(AbilityEffectType.APPLY_STATUS.uses(
            AbilityEffectParameter.CE_UPKEEP_PER_TICK));
        assertNull(status.ceUpkeepPerTick);

        AbilityEffectData maintenance =
            AbilityEffectType.MAINTAIN_STATUS_WITH_CE.createDefault();
        assertTrue(AbilityEffectType.MAINTAIN_STATUS_WITH_CE.uses(
            AbilityEffectParameter.STATUS_TYPE));
        assertTrue(AbilityEffectType.MAINTAIN_STATUS_WITH_CE.uses(
            AbilityEffectParameter.TARGET));
        assertTrue(AbilityEffectType.MAINTAIN_STATUS_WITH_CE.uses(
            AbilityEffectParameter.CE_UPKEEP_PER_TICK));
        assertTrue(AbilityEffectType.MAINTAIN_STATUS_WITH_CE.requiresActivation());
        assertTrue(AbilityEffectType.MAINTAIN_STATUS_WITH_CE.isMoveEffect());
        assertEquals(1.0, maintenance.ceUpkeepPerTick);
        assertNull(AbilityEffectType.MAINTAIN_STATUS_WITH_CE.validationError(maintenance));
    }

    @Test
    void statSubtypeMenusOnlyIncludeValidChoices() {
        AbilityEffectData timed = AbilityEffectType.TIMED_STAT_MODIFIER.createDefault();
        assertEquals(
            java.util.List.of(
                AbilityEffectType.StatOperation.CHANGE,
                AbilityEffectType.StatOperation.MULTIPLY),
            AbilityEffectType.TIMED_STAT_MODIFIER.statOperations(timed));

        timed.statType = AbilityEffectType.StatType.BATTLE.name();
        assertTrue(AbilityEffectType.TIMED_STAT_MODIFIER.statOperations(timed)
            .contains(AbilityEffectType.StatOperation.SET));
        assertEquals(java.util.List.of(BattleStatKey.BLACK_FLASH_CHANCE),
            AbilityEffectType.BATTLE_STAT_ODDS_MULTIPLY.battleStats());
    }

    @Test
    void codedActionsUseTheirOnlyApplicableRuntimeTarget() {
        assertEquals(AbilityEffectTarget.SELF,
            CodedAbilityRegistry.requiredEffectTarget(
                MiraclesAbility.KEY, MiraclesAbility.CREATE));
        assertEquals(AbilityEffectTarget.ENEMY,
            CodedAbilityRegistry.requiredEffectTarget(
                RatioAbility.KEY, RatioAbility.RATIO_EFFECT));
        assertEquals(AbilityEffectTarget.ENEMY,
            CodedAbilityRegistry.requiredEffectTarget(
                CursedSpeechAbility.KEY, CursedSpeechAbility.COMMAND));

        AbilityEffectData ratio = AbilityEffectType.CODED_MOVE_ACTION.createDefault();
        ratio.codedAbilityKey = RatioAbility.KEY;
        ratio.codedAction = RatioAbility.RATIO_EFFECT;
        CodedAbilityRegistry.prepareMoveEffect(ratio);
        assertEquals(AbilityEffectTarget.ENEMY.name(), ratio.target);

        assertTrue(CodedAbilityRegistry.supportsEffectTrigger(
            NewShadowStyleAbility.KEY, NewShadowStyleAbility.ACTIVATE_SIMPLE_DOMAIN,
            null, MoveEffectTrigger.ON_FIRE));
        assertFalse(CodedAbilityRegistry.supportsEffectTrigger(
            NewShadowStyleAbility.KEY, NewShadowStyleAbility.ACTIVATE_SIMPLE_DOMAIN,
            null, MoveEffectTrigger.ON_BLOCK));
        assertTrue(CodedAbilityRegistry.supportsEffectTrigger(
            RatioAbility.KEY, RatioAbility.RATIO_EFFECT,
            RatioAbility.CREATE_STACKS, MoveEffectTrigger.ON_FIRE));
        assertFalse(CodedAbilityRegistry.supportsEffectTrigger(
            RatioAbility.KEY, RatioAbility.RATIO_EFFECT,
            RatioAbility.APPLY_TO_MOVE, MoveEffectTrigger.ON_FIRE));
    }
}
