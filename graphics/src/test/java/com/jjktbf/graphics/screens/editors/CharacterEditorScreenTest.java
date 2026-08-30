package com.jjktbf.graphics.screens.editors;

import com.jjktbf.graphics.ui.editor.AssignmentPanel;
import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityApplicator;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.BattleStatKey;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CombatStats;
import com.jjktbf.model.character.StatKey;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MovePool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CharacterEditorScreenTest {

    @Test
    void derivedPreviewIncludesPermanentCeModifiersAndWaiverThreshold() {
        CharacterData character = namedCharacter("Curse", 80);
        AbilityEffectData maxCe = AbilityEffectType.BATTLE_STAT_MODIFIER.createDefault();
        maxCe.stringValue = BattleStatKey.MAX_CE.name();
        maxCe.statOperation = AbilityEffectType.StatOperation.MULTIPLY.name();
        maxCe.doubleValue = 5.0;
        AbilityEffectData regeneration =
            AbilityEffectType.BATTLE_STAT_MODIFIER.createDefault();
        regeneration.stringValue = BattleStatKey.CE_REGENERATION.name();
        regeneration.statOperation = AbilityEffectType.StatOperation.CHANGE.name();
        regeneration.doubleValue = 0.95;
        AbilityEffectData waiver =
            AbilityEffectType.CE_COST_WAIVE_BY_STAT_TOTAL.createDefault();
        waiver.intValue = 100;
        AbilityData physiology = new AbilityData();
        physiology.id = "PHYSIOLOGY";
        physiology.name = "Cursed Spirit Physiology";
        physiology.category = "PASSIVE";
        physiology.effects = List.of(maxCe, regeneration, waiver);
        AbilityApplicator.ApplicationResult application = AbilityApplicator.apply(
            character.toCharacterStats(), List.of(new Ability(physiology)));
        CombatStats combatStats = new CombatStats(application.modifiedStats);

        assertEquals(combatStats.getMaxCursedEnergy() * 5.0,
            CharacterEditorScreen.previewBattleStat(
                application, BattleStatKey.MAX_CE, combatStats.getMaxCursedEnergy()));
        assertEquals(1.0, CharacterEditorScreen.previewBattleStat(
            application, BattleStatKey.CE_REGENERATION,
            com.jjktbf.model.combat.BattleCombatant
                .DEFAULT_CURSED_ENERGY_REGENERATION_PER_TICK));
        assertEquals(8, CharacterEditorScreen.ceWaiverThreshold(character, application));
    }

    @Test
    void grantErrorsDoNotCreateLearnableRows() {
        AssignmentPanel.Item item = CharacterEditorScreen.availableMoveItem(
            move(), "PHYSICAL", MovePool.COMBAT_ARTS,
            "This move must be granted by an ability.");

        assertNull(item);
    }

    @Test
    void otherEligibilityErrorsKeepMoveOutOfAvailableList() {
        AssignmentPanel.Item item = CharacterEditorScreen.availableMoveItem(
            move(), "PHYSICAL", MovePool.COMBAT_ARTS, "Needs Strength >= 100");

        assertNull(item);
    }

    @Test
    void eligibleMoveRemainsUnlocked() {
        AssignmentPanel.Item item = CharacterEditorScreen.availableMoveItem(
            move(), "PHYSICAL", MovePool.COMBAT_ARTS, null);

        assertFalse(item.locked);
    }

    @Test
    void characterRecordSectionsUseTheCanonicalBaseStatTier() {
        CharacterData character = new CharacterData();
        assertEquals("SEMI-GRADE 1", CharacterEditorScreen.characterTierSection(character));

        for (StatKey stat : StatKey.values()) stat.set(character, 300);
        assertEquals("CALAMITY", CharacterEditorScreen.characterTierSection(character));
    }

    @Test
    void characterRecordsOrderByBaseStatTotalThenName() {
        CharacterData weak = namedCharacter("Zeta the Weak", 10);
        CharacterData tiedBeta = namedCharacter("Beta", 40);
        CharacterData tiedAlpha = namedCharacter("Alpha", 40);
        CharacterData strong = namedCharacter("Alpha the Strong", 90);

        List<CharacterData> ordered = new ArrayList<>(List.of(strong, tiedBeta, weak, tiedAlpha));
        ordered.sort(CharacterEditorScreen.baseStatOrdering());

        assertEquals(
            List.of("Zeta the Weak", "Alpha", "Beta", "Alpha the Strong"),
            ordered.stream().map(c -> c.name).toList());
    }

    private static CharacterData namedCharacter(String name, int everyStat) {
        CharacterData character = new CharacterData();
        character.name = name;
        for (StatKey stat : StatKey.values()) stat.set(character, everyStat);
        return character;
    }

    @Test
    void directSummonReferencesAreFoundBeforeCharacterDeletion() {
        MoveData move = move();
        move.summonCharacterId = "000002";
        AbilityEffectData effect = new AbilityEffectData();
        effect.characterId = "000002";
        AbilityData ability = new AbilityData();
        ability.effects = List.of(effect);

        assertSame(move, CharacterEditorScreen.firstMoveSummoningCharacter(
            List.of(move), "000002"));
        assertSame(ability, CharacterEditorScreen.firstAbilitySummoningCharacter(
            List.of(ability), "000002"));
    }

    @Test
    void characterResequencingRemapsMoveAndAbilitySummonReferences() {
        MoveData move = move();
        move.summonCharacterId = "000003";
        AbilityEffectData effect = new AbilityEffectData();
        effect.characterId = "000003";
        AbilityData ability = new AbilityData();
        ability.effects = List.of(effect);
        Map<String, String> remapped = Map.of("000003", "000002");

        assertTrue(CharacterEditorScreen.remapMoveSummonReferences(
            List.of(move), remapped));
        assertTrue(CharacterEditorScreen.remapAbilitySummonReferences(
            List.of(ability), remapped));
        assertEquals("000002", move.summonCharacterId);
        assertEquals("000002", effect.characterId);
    }

    @Test
    void reorderingOneMovePoolPreservesOtherPoolPositions() {
        List<String> allIds = new ArrayList<>(List.of("combat-a", "jujutsu-a", "combat-b"));

        assertTrue(CharacterEditorScreen.replaceIndexedValues(
            allIds,
            List.of(0, 2),
            List.of("combat-a", "combat-b"),
            List.of("combat-b", "combat-a")));

        assertEquals(List.of("combat-b", "jujutsu-a", "combat-a"), allIds);
    }

    @Test
    void evaluationKeyChangesWithDomainRelevantDraftState() {
        CharacterData character = namedCharacter("Original", 80);
        character.moveIds = new ArrayList<>(List.of("move-a"));
        character.abilityIds = new ArrayList<>(List.of("ability-a"));
        character.equippedCursedToolIds = new ArrayList<>();

        CharacterEditorScreen.CharacterEvaluationKey original =
            CharacterEditorScreen.evaluationKey(character);
        assertThrows(UnsupportedOperationException.class,
            () -> original.moveIds().add("cannot-corrupt-cache-key"));
        character.description = "Changed editor-only description";
        assertEquals(original, CharacterEditorScreen.evaluationKey(character));

        character.name = "Renamed";
        assertNotEquals(original, CharacterEditorScreen.evaluationKey(character));
        character.name = "Original";
        character.strength++;
        assertNotEquals(original, CharacterEditorScreen.evaluationKey(character));
        character.strength--;
        character.moveIds.add("move-b");
        assertNotEquals(original, CharacterEditorScreen.evaluationKey(character));
        character.moveIds.remove("move-b");
        character.equippedCursedToolIds.add("tool-a");
        assertNotEquals(original, CharacterEditorScreen.evaluationKey(character));
    }

    @Test
    void evaluationKeyDistinguishesLegacyTreeFallbackFromAnEmptySelection() {
        CharacterData character = namedCharacter("Tree State", 80);
        character.moveIds = new ArrayList<>(List.of("move-a"));
        character.availableMoveIds = null;
        character.abilityIds = new ArrayList<>(List.of("ability-a"));
        character.availableAbilityIds = null;

        CharacterEditorScreen.CharacterEvaluationKey legacy =
            CharacterEditorScreen.evaluationKey(character);
        character.availableMoveIds = new ArrayList<>();
        assertNotEquals(legacy, CharacterEditorScreen.evaluationKey(character));

        character.availableMoveIds = null;
        character.availableAbilityIds = new ArrayList<>();
        assertNotEquals(legacy, CharacterEditorScreen.evaluationKey(character));
    }

    private static MoveData move() {
        MoveData move = new MoveData();
        move.id = "000001";
        move.name = "Test Move";
        return move;
    }
}
