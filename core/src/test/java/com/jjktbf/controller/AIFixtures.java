package com.jjktbf.controller;

import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.ShikigamiCharacter;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.BlockStyle;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffectType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.jjktbf.model.character.Equipment;
import com.jjktbf.model.weapon.WeaponType;

/**
 * Shared builders for AI-strategy tests: hand-authored moves and combatants so
 * each test can control exactly the tags, potency, and block coverage the
 * scoring logic reads.
 */
final class AIFixtures {

    private AIFixtures() { }

    // --- Attacks ----------------------------------------------------------

    static Move meleeAttack(String id, int basePower, int apCost) {
        return attack(id, basePower, apCost,
            Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK, MoveTag.MELEE), false, 1);
    }

    static Move rangedAttack(String id, int basePower, int apCost) {
        return attack(id, basePower, apCost,
            Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK, MoveTag.RANGED), false, 1);
    }

    /** An authored PHYSICAL melee attack that can be reinforced at execution. */
    static Move ceAttack(String id, int basePower, int apCost) {
        return attackWithReinforcementMetadata(id, basePower, apCost,
            Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK, MoveTag.MELEE), false, 1,
            true, true);
    }

    /** A pure cursed-energy attack (CURSED_ENERGY only, no PHYSICAL) — not "reinforcement". */
    static Move pureCeAttack(String id, int basePower, int apCost) {
        return attack(id, basePower, apCost,
            Set.of(MoveTag.CURSED_ENERGY, MoveTag.ATTACK, MoveTag.RANGED), false, 1);
    }

    static Move guardBreakAttack(String id, int basePower, int apCost) {
        return attack(id, basePower, apCost,
            Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK, MoveTag.MELEE), true, 1);
    }

    static Move intangibleAttack(String id, int basePower, int apCost) {
        return attack(id, basePower, apCost,
            Set.of(MoveTag.PHYSICAL, MoveTag.CURSED_ENERGY, MoveTag.ATTACK, MoveTag.MELEE, MoveTag.INTANGIBLE),
            false, 1);
    }

    /** Attack with an explicit potency (for block potency-gate tests). */
    static Move attack(String id, int basePower, int apCost, Set<MoveTag> tags,
                       boolean guardBreak, int potency) {
        return attackWithReinforcementMetadata(
            id, basePower, apCost, tags, guardBreak, potency, false, false);
    }

    /**
     * Attack fixture with explicit authored reinforcement metadata. The move-wide
     * flag and per-hit eligibility are intentionally independent so classification
     * tests can cover both parts of the authored contract.
     */
    static Move attackWithReinforcementMetadata(
        String id, int basePower, int apCost, Set<MoveTag> tags,
        boolean guardBreak, int potency,
        boolean canBeReinforced, boolean reinforcementEligible
    ) {
        // The category (and thus the hit-component tag set) is determined by the
        // damage-nature tags present; modifier tags (ATTACK/MELEE/RANGED/INTANGIBLE)
        // ride on top via the move's tag set.
        Set<MoveTag> damageTags = new HashSet<>();
        for (MoveTag t : tags) {
            if (MoveTag.TYPE_TAGS.contains(t)) damageTags.add(t);
        }
        if (damageTags.isEmpty()) damageTags.add(MoveTag.PHYSICAL);
        MoveCategory category = MoveCategory.fromTags(damageTags);
        return new Move.Builder(id)
            .name(id).category(category)
            .tags(tags).potency(potency)
            .hitComponents(List.of(new HitComponent(
                basePower, damageTags, 0, false, true,
                HitComponent.INHERIT_MOVE_ACCURACY, List.of(),
                reinforcementEligible, reinforcementEligible ? 20 : 0)))
            .guardBreak(guardBreak)
            .canBeReinforced(canBeReinforced)
            .reinforcementCeCosts(
                canBeReinforced ? 15 : 0,
                canBeReinforced ? 1 : 0,
                canBeReinforced ? 80 : 0)
            .apCost(apCost).unleashPoint(1)
            .build();
    }

    // --- Defenses ---------------------------------------------------------

    static Move block(String id, List<String> affectedTags) {
        return block(id, affectedTags, 1, 50, 5);
    }

    static Move block(String id, List<String> affectedTags, int potency, int reduction) {
        return block(id, affectedTags, potency, reduction, 5);
    }

    static Move block(String id, List<String> affectedTags, int potency, int reduction, int apCost) {
        return new Move.Builder(id)
            .name(id).category(MoveCategory.DEFENSIVE)
            .tags(Set.of(MoveTag.DEFENSIVE, MoveTag.PHYSICAL))
            .potency(potency)
            .defenseType(DefenseType.BLOCK).blockStyle(BlockStyle.PERCENTAGE)
            .blockDamageReduction(reduction)
            .apCost(apCost).unleashPoint(1)
            .build();
    }

    /** A melee attack carrying an on-hit effect row (for the effect-multiplier test). */
    static Move meleeAttackWithEffect(String id, int basePower, int apCost) {
        MoveEffectData effect = AbilityEffectType.APPLY_STATUS.createDefaultMoveEffect();
        effect.effectId = "effect-" + id;
        effect.trigger = MoveEffectTrigger.ON_HIT.name();
        effect.target = AbilityEffectTarget.ENEMY.name();
        effect.stringValue = StatusEffectType.STRENGTH_DECREASE.name();
        effect.durationRounds = 2;
        effect.magnitude = 10.0;
        return new Move.Builder(id)
            .name(id).category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK, MoveTag.MELEE))
            .hitComponents(List.of(new HitComponent(basePower, Set.of(MoveTag.PHYSICAL), 0, false, true)))
            .effects(List.of(effect))
            .apCost(apCost).unleashPoint(1)
            .build();
    }

    static Move dodge(String id, String scope) {
        return new Move.Builder(id)
            .name(id).category(MoveCategory.DEFENSIVE)
            .tags(Set.of(MoveTag.DEFENSIVE))
            .defenseType(DefenseType.DODGE).dodgeScope(scope).dodgeChance(50)
            .apCost(5).unleashPoint(1)
            .build();
    }

    static Move parry(String id) {
        return new Move.Builder(id)
            .name(id).category(MoveCategory.DEFENSIVE)
            .tags(Set.of(MoveTag.DEFENSIVE, MoveTag.PHYSICAL))
            .defenseType(DefenseType.PARRY).parryStaggerTicks(2)
            .apCost(5).unleashPoint(1)
            .build();
    }

    // --- Summons ------------------------------------------------------------

    /**
     * A summon move drawing the given shikigami, mirroring the canonical Ten
     * Shadows composition (ON_FIRE SUMMON_CHARACTER row, utility technique tags).
     * Built in code so summon-picking tests stay independent of data renumbering.
     */
    static Move summonMove(String id, String characterId, int ceCost) {
        MoveEffectData effect = AbilityEffectType.SUMMON_CHARACTER.createDefaultMoveEffect();
        effect.effectId = "effect-" + id;
        effect.characterId = characterId;
        effect.trigger = MoveEffectTrigger.ON_FIRE.name();
        return new Move.Builder(id)
            .name(id).category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY, MoveTag.INNATE_TECHNIQUE, MoveTag.CURSED_ENERGY))
            .requiredTechniqueId("Ten Shadows")
            .prerequisites(java.util.Map.of("cursedTechniqueMastery", 0))
            .effects(List.of(effect))
            .apCost(8).unleashPoint(4)
            .baseCeCost(ceCost)
            .build();
    }

    // --- Combatants -------------------------------------------------------

    static BattleCombatant sorcerer(String id, Move... moves) {
        return sorcerer(id, true, baseStats(), null, moves);
    }

    static BattleCombatant sorcerer(String id, boolean hasWeapon, CharacterStats stats,
                                   String technique, Move... moves) {
        SorcererCharacter c = new SorcererCharacter(
            id, id, stats, technique, List.of(moves), List.of(),
            hasWeapon ? Equipment.base(WeaponType.KATANA) : Equipment.NONE);
        return new BattleCombatant(c, List.of());
    }

    static CharacterStats baseStats() {
        return new CharacterStats.Builder()
            .vitality(100).speed(80).combatAbility(80).strength(80).durability(80).build();
    }

    /** Offense-leaning profile (for the stat-derived Aggressive fallback). */
    static CharacterStats offenseStats() {
        return new CharacterStats.Builder()
            .vitality(40).speed(90).combatAbility(120).strength(120).durability(40)
            .cursedEnergyOutput(90).jujutsuSkill(20).build();
    }

    /** Defense-leaning profile (for the stat-derived Passive fallback). */
    static CharacterStats defenseStats() {
        return new CharacterStats.Builder()
            .vitality(120).speed(60).combatAbility(60).strength(40).durability(120)
            .cursedEnergyOutput(30).jujutsuSkill(90).build();
    }

    /** Very strong AP profile (300/300 -> 300 AP), used to widen the battle grid. */
    static CharacterStats strongStats() {
        return new CharacterStats.Builder()
            .vitality(300).speed(300).combatAbility(300).strength(300).durability(300).build();
    }

    /** Strong cursed-energy profile so CE-category attacks compute competitive power. */
    static CharacterStats ceStrongStats() {
        return new CharacterStats.Builder()
            .vitality(100).speed(80).combatAbility(80).strength(80).durability(80)
            .cursedEnergyReserves(200).cursedEnergyEfficiency(200).cursedEnergyOutput(200).build();
    }

    /** Place moves onto a combatant's committed timeline at sequential ticks. */
    static void commitTimeline(BattleCombatant c, int grid, Move... moves) {
        Timeline timeline = new Timeline(grid);
        int cursor = 1;
        for (Move m : moves) {
            timeline.placeAt(m, cursor, 0);
            cursor += m.getApCost() + 1;
        }
        c.setTimeline(timeline);
    }

    /** A Cursed Speech sorcerer with strong CE/CTM so his authored commands are usable. */
    static BattleCombatant cursedSpeechSorcerer(String id, Move... moves) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(120).speed(90).combatAbility(90).strength(90).durability(90)
            .cursedEnergyReserves(160).cursedEnergyEfficiency(160).cursedEnergyOutput(160)
            .jujutsuSkill(120).cursedTechniqueMastery(120).build();
        SorcererCharacter c = new SorcererCharacter(
            id, id, stats, "Cursed Speech", List.of(moves), List.of(), Equipment.NONE);
        return new BattleCombatant(c, List.of());
    }

    /** A Ten Shadows sorcerer with strong CE/CTM so his authored summons are usable. */
    static BattleCombatant tenShadowsSorcerer(String id, Move... moves) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(100).speed(80).combatAbility(80).strength(80).durability(80)
            .cursedEnergyReserves(200).cursedEnergyEfficiency(200).cursedEnergyOutput(200)
            .jujutsuSkill(200).cursedTechniqueMastery(200).build();
        SorcererCharacter c = new SorcererCharacter(
            id, id, stats, "Ten Shadows", List.of(moves), List.of(), Equipment.NONE);
        return new BattleCombatant(c, List.of());
    }

    /**
     * A Ratio sorcerer (Nanami-shaped stats: strong CE efficiency and CTM) so
     * his authored technique moves are usable. Abilities (e.g. Ratio
     * Reinforcement 000004) compile the Ratio runtime with a stack capacity.
     */
    static BattleCombatant ratioSorcerer(String id, List<Ability> abilities, Move... moves) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(120).speed(95).combatAbility(105).strength(110).durability(110)
            .cursedEnergyReserves(220).cursedEnergyEfficiency(160).cursedEnergyOutput(120)
            .jujutsuSkill(125).cursedTechniqueMastery(120).build();
        SorcererCharacter c = new SorcererCharacter(
            id, id, stats, "Ratio", List.of(moves), List.of(), Equipment.base(WeaponType.KATANA));
        return new BattleCombatant(c, abilities);
    }

    /** An enemy shikigami (low CE, so recoil against it stays small and predictable). */
    static BattleCombatant shikigamiEnemy(String id) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(80).speed(60).combatAbility(60).strength(60).durability(60)
            .cursedEnergyReserves(10).cursedEnergyEfficiency(10).cursedEnergyOutput(10)
            .jujutsuSkill(10).cursedTechniqueMastery(0).build();
        ShikigamiCharacter c = new ShikigamiCharacter(id, id, stats, null, List.of());
        return new BattleCombatant(c, List.of());
    }

    /** A low-CE sorcerer enemy. */
    static BattleCombatant lowCeSorcererEnemy(String id) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(80).speed(70).combatAbility(70).strength(70).durability(70)
            .cursedEnergyReserves(10).cursedEnergyEfficiency(10).cursedEnergyOutput(10)
            .jujutsuSkill(30).cursedTechniqueMastery(0).build();
        SorcererCharacter c = new SorcererCharacter(
            id, id, stats, null, List.of(), List.of(), Equipment.base(WeaponType.KATANA));
        return new BattleCombatant(c, List.of());
    }

    // --- Canonical data loading -------------------------------------------------

    static List<Move> loadCanonicalMoves() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<MoveData> datas = mapper.readValue(
            movesPath().toFile(), new TypeReference<>() { });
        List<Move> moves = new ArrayList<>();
        for (MoveData data : datas) {
            moves.add(data.toMove());
        }
        return moves;
    }

    static Move canonicalMoveById(List<Move> moves, String id) {
        return moves.stream()
            .filter(m -> m.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing canonical move " + id));
    }

    /** Load one canonical authored ability (e.g. Ratio Reinforcement 000004). */
    static Ability loadCanonicalAbility(String id) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<AbilityData> datas = mapper.readValue(
            abilitiesPath().toFile(), new TypeReference<>() { });
        AbilityData data = datas.stream()
            .filter(a -> id.equals(a.id))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing canonical ability " + id));
        return new Ability(data);
    }

    private static Path movesPath() throws IOException {
        return List.of(
                Path.of("data", "moves", "all_moves.json"),
                Path.of("..", "data", "moves", "all_moves.json"))
            .stream()
            .filter(Files::isRegularFile)
            .findFirst()
            .orElseThrow(() -> new IOException("Could not locate canonical moves"));
    }

    private static Path abilitiesPath() throws IOException {
        return List.of(
                Path.of("data", "abilities", "all_abilities.json"),
                Path.of("..", "data", "abilities", "all_abilities.json"))
            .stream()
            .filter(Files::isRegularFile)
            .findFirst()
            .orElseThrow(() -> new IOException("Could not locate canonical abilities"));
    }
}
