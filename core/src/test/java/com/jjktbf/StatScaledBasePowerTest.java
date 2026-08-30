package com.jjktbf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.AbilityResolver;
import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.move.StatusEffectType;
import com.jjktbf.model.weapon.CursedToolData;
import com.jjktbf.model.weapon.WeaponType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatScaledBasePowerTest {

    @Test
    void defaultEffectIsAuthorableAndValid() {
        AbilityEffectData effect = AbilityEffectType.MOVE_BASE_POWER_SCALE_BY_STAT.createDefault();

        assertEquals(0.5, effect.minimumStatMultiplier);
        assertEquals(2.0, effect.maximumStatMultiplier);
        assertNull(AbilityEffectType.MOVE_BASE_POWER_SCALE_BY_STAT.validationError(effect));
    }

    @Test
    void scalesMatchingMovesThroughMinimumBaselineAndScaledMaximum() {
        Move staff = move("STAFF", MoveTag.STAFF);

        assertEquals(0.2, multiplier(combatant(10), staff), 1e-9);
        assertEquals(1.0, multiplier(combatant(80), staff), 1e-9);
        assertEquals(5.0, multiplier(combatant(300), staff), 1e-9);
    }

    @Test
    void interpolatesLinearlyAndReadsCurrentStrength() {
        Move staff = move("STAFF", MoveTag.STAFF);
        BattleCombatant combatant = combatant(45);
        assertEquals(0.6, multiplier(combatant, staff), 1e-9);

        combatant.addStatusEffect(new StatusEffect(
            StatusEffectType.STRENGTH_INCREASE, 1, 35.0));

        assertEquals(1.0, multiplier(combatant, staff), 1e-9);
    }

    @Test
    void leavesNonMatchingMovesUnchanged() {
        Move katana = move("KATANA", MoveTag.KATANA);

        assertEquals(1.0, multiplier(combatant(300), katana), 1e-9);
    }

    @Test
    void bundledPlayfulCloudIsAStaffWithItsAutomaticPassive() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<CursedToolData> tools = mapper.readValue(
            dataPath("tools", "all_tools.json").toFile(), new TypeReference<>() { });
        List<AbilityData> abilities = mapper.readValue(
            dataPath("abilities", "all_abilities.json").toFile(), new TypeReference<>() { });
        CursedToolData playfulCloud = tools.stream()
            .filter(tool -> "Playful Cloud".equals(tool.name))
            .findFirst().orElseThrow();
        AbilityData passive = abilities.stream()
            .filter(ability -> playfulCloud.id.equals(ability.sourceValue))
            .filter(ability -> "CURSED_TOOL".equals(ability.sourceType))
            .findFirst().orElseThrow();
        AbilityEffectData effect = passive.effects.get(0);

        assertEquals(WeaponType.STAFF, playfulCloud.effectiveWeaponType());
        assertEquals("Strength Amplification", playfulCloud.imbuedTechniqueName);
        assertEquals(AbilityEffectType.MOVE_BASE_POWER_SCALE_BY_STAT.name(), effect.type);
        assertEquals("strength", effect.stat);
        assertEquals(0.2, effect.minimumStatMultiplier);
        assertEquals(5.0, effect.maximumStatMultiplier);
        assertEquals(MoveTag.STAFF.name(), effect.moveTag);
        CharacterData equipped = new CharacterData();
        equipped.equippedCursedToolIds = List.of(playfulCloud.id);
        assertTrue(AbilityResolver.resolve(equipped, abilities).containsAbility(passive.id));
    }

    private static double multiplier(BattleCombatant combatant, Move move) {
        return combatant.getAbilityFlags()
            .basePowerMultiplierFor(move, combatant::getRuntimeStat);
    }

    private static BattleCombatant combatant(int strength) {
        AbilityEffectData effect = AbilityEffectType.MOVE_BASE_POWER_SCALE_BY_STAT.createDefault();
        effect.stat = "strength";
        effect.moveTag = MoveTag.STAFF.name();
        effect.minimumStatMultiplier = 0.2;
        effect.maximumStatMultiplier = 5.0;

        AbilityData data = new AbilityData();
        data.id = "PLAYFUL_CLOUD";
        data.name = "Strength Amplification";
        data.category = "PASSIVE";
        data.sourceType = "CURSED_TOOL";
        data.sourceValue = "000002";
        data.effects = List.of(effect);
        Ability ability = new Ability(data);

        Character character = new SorcererCharacter(
            "USER",
            "User",
            new CharacterStats.Builder().strength(strength).build(),
            null,
            List.of(),
            List.of(ability));
        return new BattleCombatant(character);
    }

    private static Move move(String id, MoveTag weaponTag) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK, weaponTag))
            .basePower(10)
            .baseAccuracy(100)
            .build();
    }

    private static Path dataPath(String directory, String file) throws IOException {
        return List.of(
                Path.of("data", directory, file),
                Path.of("..", "data", directory, file))
            .stream()
            .filter(Files::isRegularFile)
            .findFirst()
            .orElseThrow(() -> new IOException("Could not locate " + file));
    }
}
