package com.jjktbf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityConditionData;
import com.jjktbf.model.character.AbilityConditionRuleData;
import com.jjktbf.model.character.AbilityConditionType;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterType;
import com.jjktbf.model.character.CursedCorpseCharacter;
import com.jjktbf.model.character.CursedSpiritCharacter;
import com.jjktbf.model.character.Equipment;
import com.jjktbf.model.character.ShikigamiCharacter;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.AbilityActivationEngine;
import com.jjktbf.model.combat.AbilityTrigger;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.BlockStyle;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.MoveType;
import com.jjktbf.model.weapon.WeaponType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursedSpiritMechanicsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void bundledPackageAssignsItsMandatoryAbilities() throws IOException {
        List<AbilityData> abilities = MAPPER.readValue(
            dataPath("abilities", "all_abilities.json").toFile(), new TypeReference<>() { });
        List<AbilityData> cursedSpiritAbilities = abilities.stream()
            .filter(ability -> idInRange(ability.id, 51, 64))
            .sorted(java.util.Comparator.comparing(ability -> ability.id))
            .toList();
        List<String> expectedIds = paddedIds(51, 64);

        assertEquals(expectedIds,
            cursedSpiritAbilities.stream().map(ability -> ability.id).toList());
        assertTrue(cursedSpiritAbilities.stream()
            .allMatch(ability -> CharacterType.CURSED_SPIRIT.name().equals(ability.sourceType)));
        assertEquals(List.of("000051", "000052"), cursedSpiritAbilities.stream()
            .filter(ability -> Boolean.TRUE.equals(ability.automaticallyAssigned))
            .map(ability -> ability.id)
            .toList());

        CharacterData spirit = new CharacterData();
        spirit.type = CharacterType.CURSED_SPIRIT.name();
        spirit.abilityIds = List.of();
        var resolved = com.jjktbf.model.character.AbilityResolver.resolve(
            spirit, cursedSpiritAbilities);
        assertEquals(Set.copyOf(expectedIds), Set.copyOf(resolved.availableAbilityIds()));
        assertEquals(Set.of("000051", "000052"), cursedSpiritAbilities.stream()
            .map(ability -> ability.id)
            .filter(resolved::containsAbility)
            .collect(java.util.stream.Collectors.toSet()));

        AbilityData physiology = cursedSpiritAbilities.get(0);
        assertTrue(physiology.effects.stream().anyMatch(effect ->
            AbilityEffectType.GRANT_MOVE.name().equals(effect.type)
                && "000132".equals(effect.moveId)));
    }

    @Test
    void bundledMovesFormAValidCursedSpiritRoster() throws IOException {
        List<MoveData> allMoves = MAPPER.readValue(
            dataPath("moves", "all_moves.json").toFile(), new TypeReference<>() { });
        List<MoveData> moves = allMoves.stream()
            .filter(move -> idInRange(move.id, 110, 139))
            .sorted(java.util.Comparator.comparing(move -> move.id))
            .toList();

        assertEquals(paddedIds(110, 139), moves.stream().map(move -> move.id).toList());
        for (MoveData move : moves) {
            assertEquals(Set.of(MoveType.CURSED_SPIRIT), move.effectiveMoveTypes(), move.name);
            assertTrue(move.tags.contains(MoveTag.CURSED_ENERGY.name()), move.name);
            assertTrue(Boolean.TRUE.equals(move.hasCeCost), move.name);
            assertTrue(move.baseCeCost > 0, move.name);
            assertEquals(Math.max(1, (int) Math.round(move.baseCeCost * 0.15)),
                move.minCeCost, move.name);
            assertEquals(move.baseCeCost * 4, move.maxCeCost, move.name);
            move.toMove();
        }

        assertEquals(Set.of("000110", "000124", "000125", "000132"), moves.stream()
            .filter(move -> move.isFreeMove)
            .map(move -> move.id)
            .collect(java.util.stream.Collectors.toSet()));
        MoveData reconstitute = moveById(moves, "000132");
        assertTrue(reconstitute.mustBeGranted);
        assertEquals(1, reconstitute.moveCap);

        assertEquals("MULTIPLE", moveById(moves, "000116").aoeType);
        assertEquals(3, moveById(moves, "000116").aoeTargetCount);
        assertEquals("ALL_OTHERS", moveById(moves, "000117").aoeType);
        assertEquals("ALL_ENEMIES", moveById(moves, "000123").aoeType);
        assertEquals("ON_DEFENCE", moveById(moves, "000130").attackLaunchMode);

        Map<String, List<String>> expectedEffects = Map.ofEntries(
            Map.entry("000113", List.of("APPLY_STATUS")),
            Map.entry("000114", List.of("HEAL_HP")),
            Map.entry("000119", List.of("APPLY_STATUS")),
            Map.entry("000120", List.of("APPLY_STATUS")),
            Map.entry("000121", List.of("APPLY_STATUS")),
            Map.entry("000122", List.of("DEAL_DIRECT_DAMAGE")),
            Map.entry("000123", List.of("TIMED_STAT_MODIFIER")),
            Map.entry("000127", List.of("HEAL_HP")),
            Map.entry("000128", List.of("TIMED_STAT_MODIFIER")),
            Map.entry("000131", List.of("DEAL_DIRECT_DAMAGE")),
            Map.entry("000132", List.of("HEAL_HP")),
            Map.entry("000133", List.of("TIMED_STAT_MODIFIER", "TIMED_STAT_MODIFIER")),
            Map.entry("000134", List.of("TIMED_STAT_MODIFIER")),
            Map.entry("000135", List.of("DRAIN_CE", "RESTORE_CE")),
            Map.entry("000136", List.of("TIMED_STAT_MODIFIER", "TIMED_STAT_MODIFIER")),
            Map.entry("000137", List.of("CLEAR_STATUSES", "HEAL_HP")),
            Map.entry("000138", List.of("TIMED_STAT_MODIFIER", "TIMED_STAT_MODIFIER")),
            Map.entry("000139", List.of("DEAL_DIRECT_DAMAGE", "RESTORE_CE")));
        expectedEffects.forEach((moveId, effectTypes) -> assertEquals(effectTypes,
            moveById(moves, moveId).effects.stream().map(effect -> effect.type).toList(), moveId));
        assertFalse(moves.stream().anyMatch(move -> move.tags == null || move.tags.isEmpty()));
    }

    @Test
    void uncursedPhysicalHitsRepeatedlyStopAtOneHp() {
        Move move = attack("PHYSICAL", Set.of(MoveTag.PHYSICAL));
        HitComponent component = move.getHitComponents().get(0);
        BattleCombatant attacker = sorcerer("ATTACKER");
        BattleCombatant defender = exorcismTarget("SPIRIT");
        BattleState state = new BattleState(attacker, defender);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SeededRandomSource(1L));

        defender.receiveDamage(defender.getCurrentHp() + 10, fatalAmount ->
            engine.preventFatalDamage(state, AbilityTrigger.fatalDamage(
                attacker, defender, move, component, fatalAmount, 1)));
        assertEquals(1, defender.getCurrentHp());

        defender.receiveDamage(10, fatalAmount ->
            engine.preventFatalDamage(state, AbilityTrigger.fatalDamage(
                attacker, defender, move, component, fatalAmount, 2)));
        assertEquals(1, defender.getCurrentHp());
    }

    @Test
    void cursedEnergyCursedBeingsCursedToolsAndNonHitDamageCanExorcise() {
        Move cursedEnergy = attack(
            "CURSED_ENERGY", Set.of(MoveTag.PHYSICAL, MoveTag.CURSED_ENERGY));
        assertExorcises(
            sorcerer("CE_ATTACKER"), cursedEnergy, cursedEnergy.getHitComponents().get(0));

        Move innateTechnique = attack("INNATE_TECHNIQUE",
            Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK),
            Set.of(MoveTag.PHYSICAL, MoveTag.INNATE_TECHNIQUE));
        assertExorcises(
            sorcerer("INNATE_ATTACKER"), innateTechnique,
            innateTechnique.getHitComponents().get(0));

        Move nonInnateTechnique = attack("NON_INNATE_TECHNIQUE",
            Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK),
            Set.of(MoveTag.PHYSICAL, MoveTag.NON_INNATE_TECHNIQUE));
        assertExorcises(
            sorcerer("NON_INNATE_ATTACKER"), nonInnateTechnique,
            nonInnateTechnique.getHitComponents().get(0));

        Move physical = attack("CURSED_BEING", Set.of(MoveTag.PHYSICAL));
        BattleCombatant cursedSpirit = new BattleCombatant(new CursedSpiritCharacter(
            "CURSED_ATTACKER", "Cursed attacker", stats(), null, List.of()));
        assertExorcises(cursedSpirit, physical, physical.getHitComponents().get(0));

        BattleCombatant cursedCorpse = new BattleCombatant(new CursedCorpseCharacter(
            "CORPSE_ATTACKER", "Corpse attacker", stats(), null, List.of()));
        assertExorcises(cursedCorpse, physical, physical.getHitComponents().get(0));

        BattleCombatant shikigami = new BattleCombatant(new ShikigamiCharacter(
            "SHIKIGAMI_ATTACKER", "Shikigami attacker", stats(), null, List.of()));
        assertExorcises(shikigami, physical, physical.getHitComponents().get(0));

        Move cursedToolMove = attack(
            "CURSED_TOOL", Set.of(MoveTag.PHYSICAL, MoveTag.KATANA));
        BattleCombatant cursedToolWielder = new BattleCombatant(new SorcererCharacter(
            "TOOL_ATTACKER", "Tool attacker", stats(), null, List.of(), List.of(),
            Equipment.cursedTool(WeaponType.KATANA)));
        assertExorcises(
            cursedToolWielder, cursedToolMove, cursedToolMove.getHitComponents().get(0));

        assertExorcises(sorcerer("DIRECT_ATTACKER"), null, null);
    }

    @Test
    void mandatoryDefinitionsResolveIntoAWorkingCursedSpirit() throws IOException {
        List<AbilityData> abilities = MAPPER.readValue(
            dataPath("abilities", "all_abilities.json").toFile(), new TypeReference<>() { });
        AbilityData physiologyData = abilityById(abilities, "000051");
        AbilityData exorcismData = abilityById(abilities, "000052");

        BattleCombatant plain = new BattleCombatant(new CursedSpiritCharacter(
            "PLAIN", "Plain", stats(), null, List.of()));
        BattleCombatant spirit = spiritWithAbilities(
            "SPIRIT", stats(), new Ability(physiologyData), new Ability(exorcismData));
        BattleCombatant uncursedAttacker = sorcerer("UNCURSED_ATTACKER");
        BattleState state = new BattleState(uncursedAttacker, spirit);

        assertEquals(plain.getMaxCursedEnergy() * 5, spirit.getMaxCursedEnergy(),
            "000051 multiplies max CE by 5.");
        assertEquals(spirit.getMaxCursedEnergy(), spirit.getCurrentCe());
        assertEquals(1.0, spirit.getCursedEnergyRegenerationPerTick(), 0.000001);
        spirit.drainCe(spirit.getCurrentCe());
        assertEquals(1, spirit.regenerateCursedEnergyForTick(),
            "000051 regenerates exactly 1 CE per tick.");

        int waiverThreshold =
            spirit.getCharacter().getBaseStats().baseStatTotal() / 100;
        assertEquals(0, spirit.computeMoveCeCost(ceMove("WAIVED", waiverThreshold)),
            "000051 waives CE costs up to the raw stat-total threshold.");
        assertTrue(spirit.computeMoveCeCost(ceMove("CHARGED", waiverThreshold + 1)) > 0,
            "Costs above the threshold are still charged.");

        AbilityActivationEngine engine = new AbilityActivationEngine(new SeededRandomSource(1L));
        Move physical = attack("PHYSICAL", Set.of(MoveTag.PHYSICAL));
        spirit.receiveDamage(spirit.getCurrentHp() + 10, fatalAmount ->
            engine.preventFatalDamage(state, AbilityTrigger.fatalDamage(
                uncursedAttacker, spirit, physical, physical.getHitComponents().get(0),
                fatalAmount, 1)));
        assertEquals(1, spirit.getCurrentHp(),
            "000052 leaves the curse at 1 HP against a fatal uncursed physical hit.");

        BattleCombatant ceAttacker = sorcerer("CE_ATTACKER");
        BattleCombatant exorcised = spiritWithAbilities(
            "EXORCISED", stats(), new Ability(physiologyData), new Ability(exorcismData));
        BattleState ceState = new BattleState(ceAttacker, exorcised);
        Move cursedEnergy = attack(
            "CURSED_ENERGY", Set.of(MoveTag.PHYSICAL, MoveTag.CURSED_ENERGY));
        exorcised.receiveDamage(exorcised.getCurrentHp() + 10, fatalAmount ->
            engine.preventFatalDamage(ceState, AbilityTrigger.fatalDamage(
                ceAttacker, exorcised, cursedEnergy, cursedEnergy.getHitComponents().get(0),
                fatalAmount, 1)));
        assertTrue(exorcised.isDefeated(),
            "A cursed-energy hit exorcises straight through 000052.");
    }

    @Test
    void onHitDirectDamageIsEffectDamageNotAnOrdinaryHit() {
        AbilityEffectData direct = AbilityEffectType.DEAL_DIRECT_DAMAGE.createDefault();
        direct.target = AbilityEffectTarget.ENEMY.name();
        direct.valueMode = AbilityEffectType.ValueMode.PERCENT.name();
        direct.doubleValue = 1.0;
        MoveEffectData onHit = new MoveEffectData();
        onHit.copyFrom(direct);
        onHit.effectId = "effect-000000";
        onHit.trigger = MoveEffectTrigger.ON_HIT.name();
        HitComponent component = new HitComponent(100, Set.of(MoveTag.PHYSICAL), 0, false, true);
        Move strike = new Move.Builder("DIRECT_STRIKE")
            .name("Direct Strike")
            .category(component.getCategory())
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK))
            .hitComponents(List.of(component))
            .apCost(1)
            .unleashPoint(1)
            .effects(List.of(onHit))
            .build();

        BattleCombatant attacker = sorcerer("ATTACKER");
        BattleCombatant spirit = exorcismTarget("SPIRIT");
        BattleState state = new BattleState(attacker, spirit);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SeededRandomSource(1L));

        List<CombatEvent> events = engine.processMoveEffects(
            state, attacker, List.of(spirit), strike,
            MoveEffectTrigger.ON_HIT, 0, 1, List.of());

        assertTrue(events.stream().anyMatch(
            event -> event.getType() == CombatEvent.Type.DAMAGE_DEALT));
        assertTrue(spirit.isDefeated(),
            "An ON_HIT direct-damage rider is effect damage, not an ordinary uncursed hit.");
    }

    @Test
    void reflectedAttackKeepsItsOriginalAttackerProvenance() {
        Move parry = new Move.Builder("REFLECT_PARRY")
            .name("Riposte Guard")
            .category(MoveCategory.DEFENSIVE)
            .defenseType(DefenseType.PARRY)
            .parryStaggerTicks(3)
            .blockDuration(4)
            .apCost(5)
            .unleashPoint(1)
            .build();
        Move ranged = attack("REFLECTED", Set.of(MoveTag.PHYSICAL, MoveTag.RANGED));
        BattleCombatant spirit = spiritWithAbilities("SPIRIT_ATTACKER",
            new CharacterStats.Builder().vitality(300).speed(80).build(),
            new Ability(exorcismAbility()));
        BattleCombatant defender = new BattleCombatant(new SorcererCharacter(
            "PARRY_DEFENDER", "Parry defender",
            new CharacterStats.Builder().vitality(300).speed(120).build(),
            null, List.of(parry), List.of(), Equipment.base(WeaponType.KATANA)));
        spirit.receiveDamage(spirit.getCurrentHp() - 1, fatalAmount -> false);
        assertEquals(1, spirit.getCurrentHp());

        List<CombatEvent> events = resolveParryRound(spirit, defender, ranged, parry);

        assertTrue(events.stream().anyMatch(event ->
                event.getType() == CombatEvent.Type.MOVE_PARRIED),
            "Sanity: the parry fires.");
        assertTrue(events.stream().anyMatch(event ->
                event.getType() == CombatEvent.Type.DAMAGE_DEALT
                    && event.getTarget() == spirit
                    && event.getMessage() != null && event.getMessage().contains("sent")),
            "Sanity: the ranged attack is reflected back at its cursed-spirit user.");
        assertTrue(spirit.isDefeated(),
            "The reflected strike keeps its original attacker's cursed provenance "
                + "instead of being judged as the reflector's uncursed hit.");
        assertEquals(0, spirit.getCurrentHp());
    }

    private static void assertExorcises(
        BattleCombatant attacker,
        Move move,
        HitComponent component
    ) {
        BattleCombatant defender = exorcismTarget("SPIRIT_" + attacker.getCharacter().getId());
        BattleState state = new BattleState(attacker, defender);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SeededRandomSource(1L));

        defender.receiveDamage(defender.getCurrentHp(), fatalAmount ->
            engine.preventFatalDamage(state, AbilityTrigger.fatalDamage(
                attacker, defender, move, component, fatalAmount, 1)));

        assertTrue(defender.isDefeated());
        assertEquals(0, defender.getCurrentHp());
    }

    private static AbilityData exorcismAbility() {
        AbilityEffectData survive = AbilityEffectType.SURVIVE_FATAL_DAMAGE.createDefault();
        survive.effectId = "effect-000000";
        AbilityConditionData fatal = AbilityConditionType.FATAL_DAMAGE.createDefault();
        AbilityConditionData uncursed =
            AbilityConditionType.INCOMING_HIT_LACKS_CURSED_ENERGY.createDefault();
        AbilityConditionRuleData rule = AbilityConditionRuleData.allEffects(
            AbilityConditionData.all(List.of(fatal, uncursed)));
        rule.targetEffectIds = List.of(survive.effectId);
        rule.matchSameTrigger = true;
        assertNull(AbilityConditionRuleData.validationError(List.of(rule), List.of(survive)));

        AbilityData ability = new AbilityData();
        ability.id = "EXORCISM";
        ability.name = "Exorcism Requires Cursed Energy";
        ability.category = "ACTIVE";
        ability.sourceType = "CURSED_SPIRIT";
        ability.effects = List.of(survive);
        ability.activationConditions = List.of(rule);
        return ability;
    }

    private static BattleCombatant exorcismTarget(String id) {
        return new BattleCombatant(new CursedSpiritCharacter(
            id, id, stats(), null, List.of(), List.of(new Ability(exorcismAbility()))));
    }

    private static BattleCombatant spiritWithAbilities(
        String id, CharacterStats baseStats, Ability... abilities) {
        return new BattleCombatant(new CursedSpiritCharacter(
            id, id, baseStats, null, List.of(), List.of(abilities)));
    }

    private static BattleCombatant sorcerer(String id) {
        return new BattleCombatant(new SorcererCharacter(
            id, id, stats(), null, List.of()));
    }

    private static Move attack(String id, Set<MoveTag> tags) {
        Set<MoveTag> hitTags = tags.stream()
            .filter(MoveTag.HIT_TAGS::contains)
            .collect(java.util.stream.Collectors.toSet());
        return attack(id, tags, hitTags);
    }

    /**
     * Attack whose move-level and hit-component tags differ — technique tags
     * ride on the hit component so the move itself needs no technique
     * prerequisite authoring.
     */
    private static Move attack(String id, Set<MoveTag> moveTags, Set<MoveTag> componentTags) {
        HitComponent component = new HitComponent(100, componentTags, 0, false, true);
        return new Move.Builder(id)
            .name(id)
            .category(component.getCategory())
            .tags(moveTags)
            .hitComponents(List.of(component))
            .apCost(1)
            .unleashPoint(1)
            .build();
    }

    private static CharacterStats stats() {
        return new CharacterStats.Builder().build();
    }

    private static MoveData moveById(List<MoveData> moves, String id) {
        return moves.stream().filter(move -> id.equals(move.id)).findFirst().orElseThrow();
    }

    private static AbilityData abilityById(List<AbilityData> abilities, String id) {
        return abilities.stream()
            .filter(ability -> id.equals(ability.id))
            .findFirst().orElseThrow();
    }

    private static Move ceMove(String id, int baseCost) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.CURSED_ENERGY)
            .tags(Set.of(MoveTag.CURSED_ENERGY))
            .apCost(1)
            .unleashPoint(1)
            .baseCeCost(baseCost)
            .hasCeCost(true)
            .minCeCost(1)
            .maxCeCost(baseCost * 4)
            .build();
    }

    private static List<CombatEvent> resolveParryRound(
        BattleCombatant attacker, BattleCombatant defender, Move attack, Move parry) {
        Timeline attackerTimeline = new Timeline(10);
        assertNotNull(attackerTimeline.placeAt(attack, 1, 0));
        Timeline defenderTimeline = new Timeline(10);
        assertNotNull(defenderTimeline.placeAt(parry, 1, 0));
        attacker.setTimeline(attackerTimeline);
        defender.setTimeline(defenderTimeline);
        BattleState state = new BattleState(attacker, defender);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        return new CombatResolver(new FixedRandom()).resolveRound(state);
    }

    /** Deterministic mid-range RNG: damage and accuracy rolls land on ordinary outcomes. */
    private static final class FixedRandom extends Random {
        @Override public double nextDouble() { return 0.5; }
    }

    private static boolean idInRange(String id, int minimum, int maximum) {
        if (id == null || !id.matches("\\d{6}")) return false;
        int value = Integer.parseInt(id);
        return value >= minimum && value <= maximum;
    }

    private static List<String> paddedIds(int minimum, int maximum) {
        return IntStream.rangeClosed(minimum, maximum)
            .mapToObj(value -> String.format("%06d", value))
            .toList();
    }

    private static Path dataPath(String directory, String file) throws IOException {
        for (Path path : List.of(
                Path.of("data", directory, file),
                Path.of("..", "data", directory, file))) {
            if (Files.isRegularFile(path)) return path;
        }
        throw new IOException("Could not locate data/" + directory + "/" + file);
    }
}
