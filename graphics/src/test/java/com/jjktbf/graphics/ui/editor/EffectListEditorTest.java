package com.jjktbf.graphics.ui.editor;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectParameter;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterType;
import com.jjktbf.model.character.coded.CursedSpeechAbility;
import com.jjktbf.model.character.coded.NewShadowStyleAbility;
import com.jjktbf.model.move.MoveEffectTrigger;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectListEditorTest {

    @Test
    void cursedSpiritPhysiologyEffectsExposeGenericEditorControls() {
        AbilityEffectData battleStat = AbilityEffectType.BATTLE_STAT_MODIFIER.createDefault();
        assertTrue(AbilityEffectType.BATTLE_STAT_MODIFIER.uses(
            AbilityEffectParameter.BATTLE_STAT, battleStat));
        assertTrue(AbilityEffectType.BATTLE_STAT_MODIFIER.uses(
            AbilityEffectParameter.STAT_OPERATION, battleStat));
        assertTrue(AbilityEffectType.BATTLE_STAT_MODIFIER.uses(
            AbilityEffectParameter.DECIMAL, battleStat));

        AbilityEffectData waiver =
            AbilityEffectType.CE_COST_WAIVE_BY_STAT_TOTAL.createDefault();
        assertTrue(AbilityEffectType.CE_COST_WAIVE_BY_STAT_TOTAL.uses(
            AbilityEffectParameter.MOVE_SCOPE, waiver));
        assertTrue(AbilityEffectType.CE_COST_WAIVE_BY_STAT_TOTAL.uses(
            AbilityEffectParameter.INTEGER, waiver));
    }

    @Test
    void flowerOfferingEffectsExposeResourceControls() {
        AbilityEffectData transaction =
            AbilityEffectType.TRANSACT_BOUNDED_RESOURCE.createDefault();
        assertTrue(AbilityEffectType.TRANSACT_BOUNDED_RESOURCE.uses(
            AbilityEffectParameter.SOURCE_RESOURCE, transaction));
        assertTrue(AbilityEffectType.TRANSACT_BOUNDED_RESOURCE.uses(
            AbilityEffectParameter.TARGET_RESOURCE, transaction));

        AbilityEffectData consumer =
            AbilityEffectType.CONSUME_BOUNDED_RESOURCE_FOR_BASE_POWER.createDefault();
        assertTrue(AbilityEffectType.CONSUME_BOUNDED_RESOURCE_FOR_BASE_POWER.uses(
            AbilityEffectParameter.SOURCE_RESOURCE, consumer));
        assertFalse(AbilityEffectType.CONSUME_BOUNDED_RESOURCE_FOR_BASE_POWER.uses(
            AbilityEffectParameter.TARGET_RESOURCE, consumer));
    }

    @Test
    void summonSelectorOnlyRecognizesShikigamiDefinitions() {
        CharacterData sorcerer = character("000001", null);
        CharacterData shikigami = character("000002", CharacterType.SHIKIGAMI.name());

        assertFalse(EffectListEditor.isShikigamiReference(
            List.of(sorcerer, shikigami), "000001"));
        assertTrue(EffectListEditor.isShikigamiReference(
            List.of(sorcerer, shikigami), "000002"));
        assertFalse(EffectListEditor.isShikigamiReference(
            List.of(sorcerer, shikigami), "missing"));
    }

    @Test
    void transformationSelectorRecognizesEveryCharacterDefinition() {
        CharacterData sorcerer = character("000001", null);
        CharacterData shikigami = character("000002", CharacterType.SHIKIGAMI.name());

        assertTrue(EffectListEditor.isCharacterReference(
            List.of(sorcerer, shikigami), "000001"));
        assertTrue(EffectListEditor.isCharacterReference(
            List.of(sorcerer, shikigami), "000002"));
        assertFalse(EffectListEditor.isCharacterReference(
            List.of(sorcerer, shikigami), "missing"));
    }

    @Test
    void abilityMenusOnlyContainEffectsSupportedByTheirActivationMode() {
        assertTrue(EffectListEditor.abilityEffectTypes(true)
            .contains(AbilityEffectType.AUTO_STATUS_APPLY));
        assertFalse(EffectListEditor.abilityEffectTypes(true)
            .contains(AbilityEffectType.HEAL_HP));

        assertTrue(EffectListEditor.abilityEffectTypes(false)
            .contains(AbilityEffectType.HEAL_HP));
        assertFalse(EffectListEditor.abilityEffectTypes(false)
            .contains(AbilityEffectType.AUTO_STATUS_APPLY));
    }

    @Test
    void preHitCodedActionsOnlyAppearOnHit() {
        assertTrue(EffectListEditor.codedActionsForTrigger(MoveEffectTrigger.ON_HIT).stream()
            .anyMatch(action -> CursedSpeechAbility.KEY.equals(action.key())));
        assertFalse(EffectListEditor.codedActionsForTrigger(MoveEffectTrigger.ON_BLOCK).stream()
            .anyMatch(action -> CursedSpeechAbility.KEY.equals(action.key())));
        assertFalse(EffectListEditor.codedActionsForTrigger(MoveEffectTrigger.ON_BLOCK).stream()
            .anyMatch(action -> NewShadowStyleAbility.KEY.equals(action.key())));
        assertTrue(EffectListEditor.codedActionsForTrigger(MoveEffectTrigger.ON_FIRE).stream()
            .anyMatch(action -> NewShadowStyleAbility.KEY.equals(action.key())));
    }

    private static CharacterData character(String id, String type) {
        CharacterData character = new CharacterData();
        character.id = id;
        character.type = type;
        return character;
    }
}
