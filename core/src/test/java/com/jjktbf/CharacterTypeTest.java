package com.jjktbf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityResolver;
import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CursedCorpseCharacter;
import com.jjktbf.model.character.CursedSpiritCharacter;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterType;
import com.jjktbf.model.character.ShikigamiCharacter;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 coverage for the character-definition model: the SHIKIGAMI type,
 * the stored type + directlySelectable fields, subclass routing, and legacy
 * JSON defaulting.
 */
class CharacterTypeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void legacyCharacterJsonDefaultsToSelectableSorcerer() throws Exception {
        // No "type" or "directlySelectable" fields — legacy shape.
        String json = """
            {
              "id": "000000",
              "name": "Legacy Sorcerer",
              "moveIds": []
            }
            """;
        CharacterData data = mapper.readValue(json, CharacterData.class);

        assertEquals(CharacterType.SORCERER, data.effectiveType());
        assertTrue(data.effectiveSelectable(),
            "legacy sorcerers must default to directly selectable");
    }

    @Test
    void unknownStoredTypeFailsLoudly() throws Exception {
        String json = """
            { "id": "000000", "name": "X", "type": "BOSS", "moveIds": [] }
            """;
        CharacterData data = mapper.readValue(json, CharacterData.class);
        assertThrows(IllegalArgumentException.class, data::effectiveType);
    }

    @Test
    void shikigamiTypeRoundTripsThroughJson() throws Exception {
        CharacterData data = new CharacterData();
        data.id = "000010";
        data.name = "Divine Dog";
        data.type = CharacterType.SHIKIGAMI.name();
        data.baseCeDrainPerTick = 0.2;
        data.moveIds = java.util.List.of();

        String json = mapper.writeValueAsString(data);
        CharacterData roundTrip = mapper.readValue(json, CharacterData.class);

        assertEquals(CharacterType.SHIKIGAMI, roundTrip.effectiveType());
        assertEquals(0.2, roundTrip.baseCeDrainPerTick);
        assertFalse(roundTrip.effectiveSelectable(),
            "shikigami must default to NOT directly selectable");
    }

    @Test
    void shikigamiCanBeExplicitlyMarkedSelectable() throws Exception {
        CharacterData data = new CharacterData();
        data.id = "000011";
        data.name = "Selectable Shikigami";
        data.type = CharacterType.SHIKIGAMI.name();
        data.directlySelectable = Boolean.TRUE;
        data.moveIds = java.util.List.of();

        assertTrue(data.effectiveSelectable());
    }

    @Test
    void shikigamiSelectableOverrideRoundTrips() throws Exception {
        CharacterData data = new CharacterData();
        data.id = "000012";
        data.name = "Override Shikigami";
        data.type = CharacterType.SHIKIGAMI.name();
        data.directlySelectable = Boolean.TRUE;
        data.moveIds = java.util.List.of();

        String json = mapper.writeValueAsString(data);
        CharacterData roundTrip = mapper.readValue(json, CharacterData.class);
        assertTrue(roundTrip.effectiveSelectable());
        assertEquals(CharacterType.SHIKIGAMI, roundTrip.effectiveType());
    }

    @Test
    void constructTypedCharacterBuildsCorrectSubclass() {
        CharacterData sorcerer = new CharacterData();
        sorcerer.id = "000001";
        sorcerer.name = "S";
        sorcerer.moveIds = java.util.List.of();
        Character sorcererChar = sorcerer.constructTypedCharacter(
            sorcerer.toCharacterStats(), java.util.List.of(), java.util.List.of());
        assertInstanceOf(SorcererCharacter.class, sorcererChar);
        assertEquals(CharacterType.SORCERER, sorcererChar.getType());

        CharacterData shikigami = new CharacterData();
        shikigami.id = "000002";
        shikigami.name = "D";
        shikigami.type = CharacterType.SHIKIGAMI.name();
        shikigami.baseCeDrainPerTick = 0.25;
        shikigami.moveIds = java.util.List.of();
        Character shikigamiChar = shikigami.constructTypedCharacter(
            shikigami.toCharacterStats(), java.util.List.of(), java.util.List.of());
        assertInstanceOf(ShikigamiCharacter.class, shikigamiChar);
        assertEquals(CharacterType.SHIKIGAMI, shikigamiChar.getType());
        assertEquals(0.25, shikigamiChar.getBaseCeDrainPerTick());

        CharacterData cursedSpirit = new CharacterData();
        cursedSpirit.id = "000003";
        cursedSpirit.name = "Curse";
        cursedSpirit.type = CharacterType.CURSED_SPIRIT.name();
        Character cursedSpiritChar = cursedSpirit.constructTypedCharacter(
            cursedSpirit.toCharacterStats(), java.util.List.of(), java.util.List.of());
        assertInstanceOf(CursedSpiritCharacter.class, cursedSpiritChar);
        assertEquals(CharacterType.CURSED_SPIRIT, cursedSpiritChar.getType());

        CharacterData cursedCorpse = new CharacterData();
        cursedCorpse.id = "000004";
        cursedCorpse.name = "Corpse";
        cursedCorpse.type = CharacterType.CURSED_CORPSE.name();
        Character cursedCorpseChar = cursedCorpse.constructTypedCharacter(
            cursedCorpse.toCharacterStats(), java.util.List.of(), java.util.List.of());
        assertInstanceOf(CursedCorpseCharacter.class, cursedCorpseChar);
        assertEquals(CharacterType.CURSED_CORPSE, cursedCorpseChar.getType());
    }

    @Test
    void characterTypesEnforceMoveClassEligibility() {
        Move sorcererMove = move("SORCERER_MOVE", MoveType.SORCERER);
        Move cursedSpiritMove = move("CURSED_SPIRIT_MOVE", MoveType.CURSED_SPIRIT);
        Move shikigamiMove = move("SHIKIGAMI_MOVE", MoveType.SHIKIGAMI);
        var stats = new CharacterData().toCharacterStats();

        assertDoesNotThrow(() -> new CursedSpiritCharacter(
            "000020", "Curse", stats, null, java.util.List.of(cursedSpiritMove)));
        assertThrows(IllegalArgumentException.class, () -> new CursedSpiritCharacter(
            "000020", "Curse", stats, null, java.util.List.of(sorcererMove)));

        assertDoesNotThrow(() -> new CursedCorpseCharacter(
            "000021", "Corpse", stats, null,
            java.util.List.of(sorcererMove, shikigamiMove)));
        assertThrows(IllegalArgumentException.class, () -> new CursedCorpseCharacter(
            "000021", "Corpse", stats, null, java.util.List.of(cursedSpiritMove)));

        assertThrows(IllegalArgumentException.class, () -> new SorcererCharacter(
            "000022", "Sorcerer", stats, null, java.util.List.of(shikigamiMove)));

        assertDoesNotThrow(() -> new ShikigamiCharacter(
            "000023", "Shikigami", stats, null, java.util.List.of(shikigamiMove)));
        assertThrows(IllegalArgumentException.class, () -> new ShikigamiCharacter(
            "000023", "Shikigami", stats, null, java.util.List.of(sorcererMove)));
    }

    @Test
    void moveCanBeLearnedByMultipleCharacterTypes() {
        Move sharedMove = new Move.Builder("SHARED_MOVE")
            .name("Shared Move")
            .moveTypes(java.util.EnumSet.of(MoveType.SORCERER, MoveType.CURSED_SPIRIT))
            .category(MoveCategory.UTILITY)
            .tags(java.util.Set.of(
                com.jjktbf.model.move.MoveTag.UTILITY,
                com.jjktbf.model.move.MoveTag.CURSED_ENERGY))
            .freeMove(true)
            .build();
        var stats = new CharacterData().toCharacterStats();

        assertDoesNotThrow(() -> new SorcererCharacter(
            "000024", "Sorcerer", stats, null, java.util.List.of(sharedMove)));
        assertDoesNotThrow(() -> new CursedSpiritCharacter(
            "000025", "Curse", stats, null, java.util.List.of(sharedMove)));
        assertThrows(IllegalArgumentException.class, () -> new ShikigamiCharacter(
            "000026", "Shikigami", stats, null, java.util.List.of(sharedMove)));
    }

    @Test
    void cursedCorpseAbilitySourceIsTypeRestricted() {
        AbilityData physiology = new AbilityData();
        physiology.id = "PHYSIOLOGY";
        physiology.name = "Cursed Corpse Physiology";
        physiology.category = "PASSIVE";
        physiology.sourceType = "CURSED_CORPSE";

        CharacterData cursedCorpse = new CharacterData();
        cursedCorpse.type = CharacterType.CURSED_CORPSE.name();
        cursedCorpse.abilityIds = java.util.List.of(physiology.id);
        CharacterData sorcerer = new CharacterData();
        sorcerer.abilityIds = java.util.List.of(physiology.id);

        assertTrue(AbilityResolver.resolve(cursedCorpse, java.util.List.of(physiology))
            .containsAbility(physiology.id));
        assertFalse(AbilityResolver.resolve(sorcerer, java.util.List.of(physiology))
            .availableAbilityIds().contains(physiology.id));
    }

    @Test
    void cursedSpiritSourcesExposeOptionalAbilitiesAndAssignMandatoryAbilities() {
        AbilityData mandatory = new AbilityData();
        mandatory.id = "MANDATORY";
        mandatory.name = "Cursed Spirit Physiology";
        mandatory.category = "PASSIVE";
        mandatory.sourceType = "CURSED_SPIRIT";
        mandatory.automaticallyAssigned = true;

        AbilityData optional = new AbilityData();
        optional.id = "OPTIONAL";
        optional.name = "Amorphous Anatomy";
        optional.category = "PASSIVE";
        optional.sourceType = "CURSED_SPIRIT";

        CharacterData cursedSpirit = new CharacterData();
        cursedSpirit.type = CharacterType.CURSED_SPIRIT.name();
        cursedSpirit.abilityIds = java.util.List.of();
        CharacterData sorcerer = new CharacterData();
        sorcerer.abilityIds = java.util.List.of();

        AbilityResolver.Result spiritResult = AbilityResolver.resolve(
            cursedSpirit, java.util.List.of(optional, mandatory));
        assertTrue(spiritResult.availableAbilityIds().containsAll(
            java.util.Set.of(mandatory.id, optional.id)));
        assertTrue(spiritResult.containsAbility(mandatory.id));
        assertFalse(spiritResult.containsAbility(optional.id));

        AbilityResolver.Result sorcererResult = AbilityResolver.resolve(
            sorcerer, java.util.List.of(optional, mandatory));
        assertFalse(sorcererResult.availableAbilityIds().contains(mandatory.id));
        assertFalse(sorcererResult.availableAbilityIds().contains(optional.id));
    }

    @Test
    void moveTypeSupportsCanonicalAndLegacyStoredValues() throws Exception {
        MoveData legacyShikigami = new MoveData();
        legacyShikigami.shikigamiMove = true;
        assertEquals(MoveType.SHIKIGAMI, legacyShikigami.effectiveMoveType());

        MoveData cursedSpirit = new MoveData();
        cursedSpirit.moveType = MoveType.CURSED_SPIRIT.name();
        String json = mapper.writeValueAsString(cursedSpirit);
        MoveData roundTrip = mapper.readValue(json, MoveData.class);
        assertEquals(MoveType.CURSED_SPIRIT, roundTrip.effectiveMoveType());

        MoveData shared = new MoveData();
        shared.id = "000027";
        shared.apCost = 1;
        shared.unleashPoint = 1;
        shared.moveTypes = java.util.List.of(
            MoveType.SORCERER.name(), MoveType.CURSED_SPIRIT.name());
        shared.tags = java.util.List.of("UTILITY", "CURSED_ENERGY");
        Move sharedMove = shared.toMove();
        assertEquals(java.util.Set.of(MoveType.SORCERER, MoveType.CURSED_SPIRIT),
            sharedMove.getMoveTypes());

        MoveData canonical = MoveData.fromMove(sharedMove);
        assertEquals(java.util.List.of(
            MoveType.SORCERER.name(), MoveType.CURSED_SPIRIT.name()), canonical.moveTypes);
        assertNull(canonical.moveType);
    }

    @Test
    void techniqueMovesAreLearnableByAnyCharacterTypeWithTheTechnique() {
        Move techniqueMove = new Move.Builder("TECHNIQUE_MOVE")
            .name("Technique Move")
            .requiredTechniqueId("Disaster Plants")
            .category(MoveCategory.UTILITY)
            .tags(java.util.Set.of(
                com.jjktbf.model.move.MoveTag.UTILITY,
                com.jjktbf.model.move.MoveTag.CURSED_ENERGY))
            .freeMove(true)
            .build();
        assertTrue(techniqueMove.isTechniqueMove());
        assertTrue(techniqueMove.getMoveTypes().isEmpty());
        var stats = new CharacterData().toCharacterStats();

        // Technique moves carry no class: every character type that possesses
        // the technique may learn them.
        assertDoesNotThrow(() -> new SorcererCharacter(
            "000030", "Sorcerer", stats, "Disaster Plants", java.util.List.of(techniqueMove)));
        assertDoesNotThrow(() -> new CursedSpiritCharacter(
            "000031", "Curse", stats, "Disaster Plants", java.util.List.of(techniqueMove)));
        assertDoesNotThrow(() -> new ShikigamiCharacter(
            "000032", "Shikigami", stats, "Disaster Plants", java.util.List.of(techniqueMove)));

        // Possession of the technique is still required.
        assertThrows(IllegalArgumentException.class, () -> new SorcererCharacter(
            "000033", "Sorcerer", stats, null, java.util.List.of(techniqueMove)));
    }

    @Test
    void techniqueMovesDropAuthoredClassTypes() {
        Move move = new Move.Builder("TECHNIQUE_CLASSY")
            .name("Classy Technique Move")
            .moveType(MoveType.SHIKIGAMI)
            .requiredTechniqueId("Ten Shadows")
            .category(MoveCategory.UTILITY)
            .tags(java.util.Set.of(
                com.jjktbf.model.move.MoveTag.UTILITY,
                com.jjktbf.model.move.MoveTag.CURSED_ENERGY))
            .freeMove(true)
            .build();

        assertTrue(move.isTechniqueMove());
        assertTrue(move.getMoveTypes().isEmpty());
    }

    @Test
    void techniqueMoveDataCarriesNoClassFields() throws Exception {
        MoveData data = new MoveData();
        data.id = "000028";
        data.name = "Technique Move";
        data.apCost = 1;
        data.unleashPoint = 1;
        data.requiredTechniqueId = "Ten Shadows";
        data.moveTypes = java.util.List.of(MoveType.SORCERER.name());
        data.tags = java.util.List.of("UTILITY", "CURSED_ENERGY");

        assertTrue(data.isTechniqueMove());
        assertTrue(data.effectiveMoveTypes().isEmpty());
        Move built = data.toMove();
        assertTrue(built.getMoveTypes().isEmpty());

        MoveData saved = MoveData.fromMove(built);
        assertNull(saved.moveTypes);
        assertFalse(mapper.writeValueAsString(saved).contains("moveTypes"));

        // Classed moves keep requiring a class.
        MoveData classed = new MoveData();
        classed.id = "000029";
        classed.name = "Classed Move";
        classed.apCost = 1;
        classed.unleashPoint = 1;
        classed.tags = java.util.List.of("UTILITY");
        assertEquals(java.util.Set.of(MoveType.SORCERER), classed.effectiveMoveTypes());
        assertThrows(IllegalStateException.class, () -> new Move.Builder("NO_CLASS")
            .name("No Class")
            .moveTypes(java.util.Set.of())
            .category(MoveCategory.UTILITY)
            .freeMove(true)
            .build());
    }

    @Test
    void fromCharacterPreservesType() {
        CharacterData sorcererData = new CharacterData();
        sorcererData.id = "000003";
        sorcererData.name = "S";
        sorcererData.moveIds = java.util.List.of();
        Character sorcererChar = sorcererData.constructTypedCharacter(
            sorcererData.toCharacterStats(), java.util.List.of(), java.util.List.of());
        CharacterData back = CharacterData.fromCharacter(sorcererChar);
        assertEquals(CharacterType.SORCERER, back.effectiveType());

        CharacterData shikigamiData = new CharacterData();
        shikigamiData.id = "000004";
        shikigamiData.name = "D";
        shikigamiData.type = CharacterType.SHIKIGAMI.name();
        shikigamiData.baseCeDrainPerTick = 0.35;
        shikigamiData.moveIds = java.util.List.of();
        Character shikigamiChar = shikigamiData.constructTypedCharacter(
            shikigamiData.toCharacterStats(), java.util.List.of(), java.util.List.of());
        CharacterData back2 = CharacterData.fromCharacter(shikigamiChar);
        assertEquals(CharacterType.SHIKIGAMI, back2.effectiveType());
        assertEquals(0.35, back2.baseCeDrainPerTick);
    }

    @Test
    void shikigamiRequiresPositiveBaseCeDrain() {
        CharacterData shikigami = new CharacterData();
        shikigami.id = "000006";
        shikigami.name = "D";
        shikigami.type = CharacterType.SHIKIGAMI.name();
        shikigami.moveIds = java.util.List.of();

        assertThrows(IllegalArgumentException.class, () -> shikigami.constructTypedCharacter(
            shikigami.toCharacterStats(), java.util.List.of(), java.util.List.of()));

        shikigami.baseCeDrainPerTick = 0.0;
        assertThrows(IllegalArgumentException.class, () -> shikigami.constructTypedCharacter(
            shikigami.toCharacterStats(), java.util.List.of(), java.util.List.of()));
    }

    @Test
    void cursedSpiritMovesRequireAnExplicitCursedEnergyTag() {
        assertThrows(IllegalStateException.class, () -> new Move.Builder("UNCURSED")
            .name("Uncursed")
            .moveType(MoveType.CURSED_SPIRIT)
            .category(MoveCategory.PHYSICAL_CURSED_ENERGY)
            .build());

        assertDoesNotThrow(() -> new Move.Builder("CURSED")
            .name("Cursed")
            .moveType(MoveType.CURSED_SPIRIT)
            .category(MoveCategory.UTILITY)
            .tags(java.util.Set.of(
                com.jjktbf.model.move.MoveTag.UTILITY,
                com.jjktbf.model.move.MoveTag.CURSED_ENERGY))
            .freeMove(true)
            .build());
    }

    private static Move move(String id, MoveType type) {
        Move.Builder builder = new Move.Builder(id)
            .name(id)
            .moveType(type)
            .category(MoveCategory.UTILITY)
            .freeMove(true);
        if (type == MoveType.CURSED_SPIRIT) {
            builder.tags(java.util.Set.of(
                com.jjktbf.model.move.MoveTag.UTILITY,
                com.jjktbf.model.move.MoveTag.CURSED_ENERGY));
        }
        return builder.build();
    }
}
