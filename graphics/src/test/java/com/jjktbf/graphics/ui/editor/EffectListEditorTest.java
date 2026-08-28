package com.jjktbf.graphics.ui.editor;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectParameter;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterType;
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

    private static CharacterData character(String id, String type) {
        CharacterData character = new CharacterData();
        character.id = id;
        character.type = type;
        return character;
    }
}
