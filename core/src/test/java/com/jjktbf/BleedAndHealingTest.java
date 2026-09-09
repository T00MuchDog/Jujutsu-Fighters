package com.jjktbf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityConditionType;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.AbilityActivationEngine;
import com.jjktbf.model.combat.AbilityTrigger;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.move.StatusEffectType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BleedAndHealingTest {
    @Test
    void authoredBleedDefaultsToSixtyTicksAndSurvivesSerialization() throws Exception {
        MoveEffectData effect = bleedEffect();
        effect.durationRounds = null;
        effect.durationTicks = null;
        ObjectMapper mapper = new ObjectMapper();
        effect = mapper.readValue(mapper.writeValueAsString(effect), MoveEffectData.class);
        AbilityEffectType.APPLY_STATUS.prepare(effect);

        assertEquals(0, effect.durationRounds);
        assertEquals(60, effect.durationTicks);
        assertNull(AbilityEffectType.APPLY_STATUS.validationError(effect));
        assertThrows(IllegalArgumentException.class,
            () -> new StatusEffect(StatusEffectType.BLEED, 1, 0.0));
        assertTrue(StatusEffectType.BLEED.isBodilyInjury());
    }

    @Test
    void sixthWoundReplacesOnlyTheOldestAcrossAllSources() {
        BattleCombatant target = fighter("TARGET", List.of());
        BattleCombatant first = fighter("FIRST", List.of());
        BattleCombatant second = fighter("SECOND", List.of());
        for (int i = 0; i < 5; i++) {
            target.addStatusEffect(wound(60), null, first);
            target.tickTimelineEffects();
        }
        assertEquals(List.of(55, 56, 57, 58, 59), woundTicks(target));
        target.addStatusEffect(wound(60), null, second);

        assertEquals(List.of(56, 57, 58, 59, 60), woundTicks(target));
        assertSame(first, target.statusSource(target.getActiveEffects().get(0)).orElseThrow());
        assertSame(second, target.statusSource(StatusEffectType.BLEED).orElseThrow());
        for (int i = 0; i < 56; i++) target.tickTimelineEffects();
        assertEquals(List.of(1, 2, 3, 4), woundTicks(target));
        assertSame(second, target.statusSource(StatusEffectType.BLEED).orElseThrow());
    }

    @Test
    void refreshingAtCapPreservesFractionalDamageAndCleansingDiscardsIt() {
        BattleCombatant target = fighter("TARGET", List.of());
        for (int i = 0; i < 5; i++) target.addStatusEffect(wound(60));
        double fraction = 0.12 / target.getMaxHp();
        assertEquals(0, target.accrueStatusDamageForTick(StatusEffectType.BLEED, fraction));
        target.addStatusEffect(wound(60));
        assertEquals(1, target.accrueStatusDamageForTick(StatusEffectType.BLEED, fraction));
        target.removeStatusEffects(StatusEffectType.BLEED);
        target.addStatusEffect(wound(60));
        assertEquals(0, target.accrueStatusDamageForTick(
            StatusEffectType.BLEED, 0.85 / target.getMaxHp()));
    }

    @Test
    void everyHitCanApplyAWoundAndFiveStacksDealExactlySixtyTicksOfDamage() {
        Move attack = bleedingAttack(6);
        BattleCombatant attacker = fighter("ATTACKER", List.of(attack));
        BattleCombatant target = fighter("TARGET", List.of());
        BattleState state = resolvingState(attacker, target, attack, 1, 150);
        CombatResolver resolver = resolver();
        List<CombatEvent> events = new ArrayList<>(resolver.beginResolution(state));
        events.addAll(resolver.resolveTick(state));

        assertEquals(5, woundTicks(target).size());
        assertEquals(List.of(59, 59, 59, 59, 59), woundTicks(target));
        assertEquals(6, events.stream().filter(event ->
            event.getType() == CombatEvent.Type.STATUS_APPLIED).count());
        while (resolver.hasMoreTicks()) events.addAll(resolver.resolveTick(state));

        assertFalse(target.hasEffect(StatusEffectType.BLEED));
        assertEquals((int) Math.floor(target.getMaxHp() * 0.0004 * 5 * 60),
            bleedDamage(events));
    }

    @Test
    void lateWoundCarriesItsRemainingTicksAndFractionAcrossRoundBoundary() {
        Move attack = bleedingAttack(1);
        BattleCombatant attacker = fighter("ATTACKER", List.of(attack));
        BattleCombatant target = fighter("TARGET", List.of());
        BattleState state = resolvingState(attacker, target, attack, 149, 150);
        CombatResolver resolver = resolver();
        List<CombatEvent> events = new ArrayList<>(resolver.beginResolution(state));
        while (resolver.hasMoreTicks()) events.addAll(resolver.resolveTick(state));
        assertEquals(List.of(58), woundTicks(target));
        resolver.processRoundEnd(state);
        assertEquals(List.of(58), woundTicks(target));

        attacker.setTimeline(new Timeline(150));
        target.setTimeline(new Timeline(150));
        events.addAll(resolver.beginResolution(state));
        while (resolver.hasMoreTicks()) events.addAll(resolver.resolveTick(state));
        assertFalse(target.hasEffect(StatusEffectType.BLEED));
        assertEquals((int) Math.floor(target.getMaxHp() * 0.0004 * 60), bleedDamage(events));
    }

    @Test
    void healingMoveCuresAllWoundsPoisonAndBurnBeforePercentageHealing() {
        Move heal = healingMove(AbilityEffectTarget.SELF);
        BattleCombatant user = fighter("USER", List.of(heal));
        BattleCombatant enemy = fighter("ENEMY", List.of());
        int healthyMaxHp = user.getMaxHp();
        int healthyMaxCe = user.getMaxCursedEnergy();
        afflict(user);
        user.receiveDamage(healthyMaxHp / 3);
        int hpBefore = user.getCurrentHp();
        assertTrue(user.getMaxHp() < healthyMaxHp);

        List<CombatEvent> events = fireHealing(new BattleState(user, enemy), user, enemy, heal);

        assertCured(user);
        assertTrue(user.hasEffect(StatusEffectType.FATIGUED));
        assertTrue(user.hasEffect(StatusEffectType.CURSED_ENERGY_PARASITE));
        assertEquals(healthyMaxHp, user.getMaxHp());
        assertEquals(healthyMaxCe, user.getMaxCursedEnergy());
        assertEquals(hpBefore + (int) Math.round(healthyMaxHp * 0.15), user.getCurrentHp());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.STATUS_EXPIRED));
    }

    @Test
    void fullHpRecipientIsCuredAndTheCasterIsNotCuredByHealingSomeoneElse() {
        Move heal = healingMove(AbilityEffectTarget.ENEMY);
        BattleCombatant user = fighter("USER", List.of(heal));
        BattleCombatant recipient = fighter("RECIPIENT", List.of());
        user.addStatusEffect(wound(60));
        recipient.addStatusEffect(wound(60));
        recipient.addStatusEffect(new StatusEffect(StatusEffectType.BURNED, 1, 0.0));
        assertEquals(recipient.getMaxHp(), recipient.getCurrentHp());

        List<CombatEvent> events = fireHealing(
            new BattleState(user, recipient), user, recipient, heal);

        assertCured(recipient);
        assertTrue(user.hasEffect(StatusEffectType.BLEED));
        assertFalse(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.HP_RESTORED));
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.STATUS_EXPIRED && event.getTarget() == recipient));
    }

    @Test
    void cancelledHealingMoveDoesNotCureAndSuccessfulHealingStopsThatTicksBleed() {
        for (boolean cancelled : List.of(false, true)) {
            Move heal = healingMove(AbilityEffectTarget.SELF);
            BattleCombatant user = fighter("USER", List.of(heal));
            BattleCombatant enemy = fighter("ENEMY", List.of());
            for (int i = 0; i < 5; i++) user.addStatusEffect(wound(60));
            BattleState state = resolvingState(user, enemy, heal, 1, 150);
            if (cancelled) user.getTimeline().getSegments().get(0).stun();
            CombatResolver resolver = resolver();
            resolver.beginResolution(state);
            List<CombatEvent> events = resolver.resolveTick(state);

            assertEquals(cancelled, user.hasEffect(StatusEffectType.BLEED));
            assertEquals(cancelled ? (int) Math.floor(user.getMaxHp() * 0.002) : 0,
                bleedDamage(events));
        }
    }

    @Test
    void automaticHealingAbilitiesDoNotBecomeHealingMoves() {
        AbilityEffectData heal = AbilityEffectType.HEAL_HP.createDefault();
        heal.intValue = 20;
        AbilityData data = new AbilityData();
        data.id = "AUTOMATIC_HEAL";
        data.name = "Automatic heal";
        data.category = "ACTIVE";
        data.activationCondition = AbilityConditionType.BATTLE_STARTED.createDefault();
        data.effects = List.of(heal);
        BattleCombatant user = fighter("USER", List.of(), List.of(new Ability(data)));
        BattleCombatant enemy = fighter("ENEMY", List.of());
        afflict(user);
        user.receiveDamage(50);
        int before = user.getCurrentHp();

        engine().process(new BattleState(user, enemy),
            AbilityTrigger.simple(AbilityTrigger.Type.BATTLE_START));

        assertEquals(before + 20, user.getCurrentHp());
        assertEquals(5, woundTicks(user).size());
        assertTrue(user.hasEffect(StatusEffectType.POISON));
        assertTrue(user.hasEffect(StatusEffectType.BURNED));
    }

    private static void afflict(BattleCombatant target) {
        for (int i = 0; i < 5; i++) target.addStatusEffect(wound(60));
        target.addStatusEffect(StatusEffect.poison(2));
        target.addStatusEffect(new StatusEffect(StatusEffectType.BURNED, 2, 0.0));
        target.addStatusEffect(new StatusEffect(StatusEffectType.FATIGUED, 2, 0.0));
        target.addStatusEffect(new StatusEffect(StatusEffectType.CURSED_ENERGY_PARASITE, 2, 0.0));
    }

    private static void assertCured(BattleCombatant target) {
        assertFalse(target.hasEffect(StatusEffectType.BLEED));
        assertFalse(target.hasEffect(StatusEffectType.POISON));
        assertFalse(target.hasEffect(StatusEffectType.BURNED));
    }

    private static StatusEffect wound(int ticks) {
        return new StatusEffect(StatusEffectType.BLEED, 0, ticks, 0.0);
    }

    private static List<Integer> woundTicks(BattleCombatant target) {
        return target.getActiveEffects().stream()
            .filter(effect -> effect.getType() == StatusEffectType.BLEED)
            .map(StatusEffect::getDurationTicks).toList();
    }

    private static int bleedDamage(List<CombatEvent> events) {
        return events.stream().filter(event -> event.getType() == CombatEvent.Type.DAMAGE_DEALT
                && event.getMessage() != null && event.getMessage().contains("Bleed"))
            .mapToInt(CombatEvent::getIntValue).sum();
    }

    private static MoveEffectData bleedEffect() {
        MoveEffectData effect = AbilityEffectType.APPLY_STATUS.createDefaultMoveEffect();
        effect.effectId = "wound";
        effect.trigger = MoveEffectTrigger.ON_HIT.name();
        effect.target = AbilityEffectTarget.ENEMY.name();
        effect.stringValue = StatusEffectType.BLEED.name();
        effect.durationRounds = 0;
        effect.durationTicks = 60;
        return effect;
    }

    private static Move bleedingAttack(int hits) {
        return new Move.Builder("WOUNDING_ATTACK").name("Wounding attack")
            .category(MoveCategory.PHYSICAL).tags(Set.of(MoveTag.ATTACK, MoveTag.PHYSICAL))
            .apCost(1).unleashPoint(1).neverMiss(true)
            .hitComponents(IntStream.range(0, hits)
                .mapToObj(i -> new HitComponent(1,
                    Set.of(MoveTag.PHYSICAL, MoveTag.MELEE), 0, false, true))
                .toList())
            .effects(List.of(bleedEffect())).build();
    }

    private static Move healingMove(AbilityEffectTarget target) {
        MoveEffectData heal = AbilityEffectType.HEAL_HP.createDefaultMoveEffect();
        heal.effectId = "heal";
        heal.trigger = MoveEffectTrigger.ON_FIRE.name();
        heal.target = target.name();
        heal.valueMode = AbilityEffectType.ValueMode.PERCENT.name();
        heal.doubleValue = 0.15;
        return new Move.Builder("HEAL").name("Healing move")
            .category(MoveCategory.UTILITY).tags(Set.of(MoveTag.UTILITY))
            .apCost(1).unleashPoint(1).effects(List.of(heal)).build();
    }

    private static List<CombatEvent> fireHealing(
        BattleState state, BattleCombatant user, BattleCombatant target, Move heal
    ) {
        return engine().processMoveEffects(state, user, target, heal,
            MoveEffectTrigger.ON_FIRE, -1, 1);
    }

    private static BattleCombatant fighter(String id, List<Move> moves) {
        return fighter(id, moves, List.of());
    }

    private static BattleCombatant fighter(String id, List<Move> moves, List<Ability> abilities) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(100).strength(100).durability(100).speed(100)
            .combatAbility(100).cursedEnergyReserves(100).cursedEnergyEfficiency(100)
            .cursedEnergyOutput(100).jujutsuSkill(100).cursedTechniqueMastery(100).build();
        return new BattleCombatant(new SorcererCharacter(id, id, stats, null, moves, abilities));
    }

    private static BattleState resolvingState(
        BattleCombatant user, BattleCombatant enemy, Move move, int start, int grid
    ) {
        Timeline timeline = new Timeline(grid);
        assertNotNull(timeline.placeAt(move, start, 0));
        user.setTimeline(timeline);
        enemy.setTimeline(new Timeline(grid));
        BattleState state = new BattleState(user, enemy);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        return state;
    }

    private static CombatResolver resolver() {
        return new CombatResolver(new SeededRandomSource(new Random(42)));
    }

    private static AbilityActivationEngine engine() {
        return new AbilityActivationEngine(new SeededRandomSource(new Random(42)));
    }
}
