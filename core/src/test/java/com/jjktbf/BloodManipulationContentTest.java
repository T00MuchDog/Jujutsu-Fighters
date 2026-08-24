package com.jjktbf;

import com.jjktbf.model.character.AbilityRepository;
import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterRepository;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveRepository;
import com.jjktbf.model.move.StatusEffectType;
import com.jjktbf.model.technique.TechniqueRepository;
import com.jjktbf.model.weapon.CursedToolRepository;
import com.jjktbf.model.weapon.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloodManipulationContentTest {

    @Test
    void goodwillKamoBuildsWithHisBowAndBloodManipulationKit() throws Exception {
        String previousAuthoring = System.getProperty(AppPaths.AUTHORING_SYSTEM_PROPERTY);
        String previousRoot = System.getProperty(AppPaths.AUTHORING_ROOT_SYSTEM_PROPERTY);
        try {
            System.setProperty(AppPaths.AUTHORING_SYSTEM_PROPERTY, "true");
            System.setProperty(AppPaths.AUTHORING_ROOT_SYSTEM_PROPERTY,
                System.getProperty("user.dir"));
            MoveRepository moves = new MoveRepository("data/moves");
            AbilityRepository abilities = new AbilityRepository("data/abilities");
            TechniqueRepository techniques = new TechniqueRepository("data/techniques");
            CursedToolRepository tools = new CursedToolRepository("data/tools");
            CharacterRepository characters = new CharacterRepository("data/characters");
            moves.load();
            abilities.load();
            techniques.load();
            tools.load();
            characters.load();

            CharacterData definition = characters.findById("000020").orElseThrow();
            Character kamo = definition.toCharacter(moves, abilities, techniques, tools);
            Map<String, Move> known = kamo.getKnownMoves().stream()
                .collect(Collectors.toMap(Move::getId, Function.identity()));

            assertTrue(kamo.getEquipment().hasWeaponOfType(WeaponType.BOW));
            assertEquals(14, known.size());
            assertTrue(known.get("000102").hasTag("BOW"));
            assertTrue(known.get("000103").hasTag("BOW"));
            assertTrue(known.get("000104").hasTag("BOW"));
            assertFalse(known.get("000102").effectsFor(MoveEffectTrigger.ON_START, -1)
                .stream().anyMatch(effect -> effect.sourceResourceAmount != null
                    && effect.sourceResourceAmount > 0),
                "ordinary arrows must not require ammunition charges");
            assertTrue(known.get("000106").effectsFor(MoveEffectTrigger.ON_HIT, 0)
                .stream().anyMatch(effect ->
                    StatusEffectType.RESTRAINED.name().equals(effect.stringValue)));
            assertEquals(List.of(
                    "FLOWING_RED_SCALE_STRENGTH", "FLOWING_RED_SCALE_SPEED"),
                known.get("000105").effectsFor(MoveEffectTrigger.ON_FIRE, -1).stream()
                    .map(effect -> effect.refreshGroup)
                    .toList());

            BattleCombatant combatant = new BattleCombatant(kamo);
            var supply = combatant.abilityState("BLOOD_SUPPLY").orElseThrow();
            var compression = combatant.abilityState("COMPRESSION").orElseThrow();
            assertEquals(3, supply.currentValue());
            assertEquals(3, supply.maximumValue());
            assertEquals(0, compression.currentValue());
            assertEquals(3, compression.maximumValue());
        } finally {
            restoreProperty(AppPaths.AUTHORING_SYSTEM_PROPERTY, previousAuthoring);
            restoreProperty(AppPaths.AUTHORING_ROOT_SYSTEM_PROPERTY, previousRoot);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }
}
