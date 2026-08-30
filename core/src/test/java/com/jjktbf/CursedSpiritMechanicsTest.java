package com.jjktbf;

import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityConditionData;
import com.jjktbf.model.character.AbilityConditionRuleData;
import com.jjktbf.model.character.AbilityConditionType;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
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
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.weapon.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursedSpiritMechanicsTest {

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
}
