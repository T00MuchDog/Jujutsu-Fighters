package com.jjktbf.graphics.screens;

import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityApplicator;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CharacterSelectScreenStatsTest {

    @Test
    void withoutEffectiveStatsDisplayFallsBackToAuthoredValuesInDisplayOrder() {
        CharacterData character = authoredCharacter();

        assertArrayEquals(new int[] {
            100, 110, 110, 95, 105, 100, 120, 90, 125, 75
        }, CharacterSelectScreen.statDisplayValues(character, null));
    }

    @Test
    void passiveStatMultipliersAreReflectedInDisplayOrder() {
        CharacterData character = authoredCharacter();
        CharacterStats effective = AbilityApplicator.apply(
            character.toCharacterStats(),
            List.of(new Ability(nineToFiveStyleVow()))).modifiedStats;

        assertArrayEquals(new int[] {
            80, 88, 88, 76, 105, 80, 120, 72, 125, 75
        }, CharacterSelectScreen.statDisplayValues(character, effective));
    }

    /** A passive built in code mirroring a binding vow that scales stats to 80%. */
    private static AbilityData nineToFiveStyleVow() {
        AbilityData vow = new AbilityData();
        vow.id = "000900";
        vow.name = "Vow";
        vow.category = "PASSIVE";
        vow.sourceType = "CHARACTER";
        vow.effects = List.of(
            statMultiply("vitality"),
            statMultiply("strength"),
            statMultiply("durability"),
            statMultiply("speed"),
            statMultiply("cursedEnergyReserves"),
            statMultiply("cursedEnergyOutput"));
        return vow;
    }

    private static AbilityEffectData statMultiply(String stat) {
        AbilityEffectData effect = AbilityEffectType.STAT_MULTIPLY.createDefault();
        effect.stat = stat;
        effect.doubleValue = 0.8;
        return effect;
    }

    private static CharacterData authoredCharacter() {
        CharacterData character = new CharacterData();
        character.vitality = 100;
        character.strength = 110;
        character.durability = 110;
        character.speed = 95;
        character.combatAbility = 105;
        character.cursedEnergyReserves = 100;
        character.cursedEnergyEfficiency = 120;
        character.cursedEnergyOutput = 90;
        character.jujutsuSkill = 125;
        character.cursedTechniqueMastery = 75;
        return character;
    }
}
