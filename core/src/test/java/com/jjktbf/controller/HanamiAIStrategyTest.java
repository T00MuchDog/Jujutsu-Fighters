package com.jjktbf.controller;

import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.CursedSpiritCharacter;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.MoveAvailability;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.TeamBattlePlan;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.MoveType;
import com.jjktbf.model.move.AoeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HanamiAIStrategyTest {

    @Test
    void emptyOfferingSequencesAbsorptionBeforeBeam() {
        Move absorption = absorption();
        Move beam = beam();
        BattleCombatant hanami = hanami(absorption, beam);
        BattleCombatant enemy = enemy();
        BattleState state = stateAtRound(hanami, enemy, 5);

        BattlePlan plan = new HanamiAIStrategy().buildPlan(state, hanami, new SeededRandomSource(1L));
        List<ActionSegment> segments = chronologicalSegments(plan);

        assertEquals(List.of(absorption, beam),
            segments.stream().map(ActionSegment::getMove).toList());
        assertTrue(segments.get(0).getFireTick() < segments.get(1).getStartTick());
    }

    @Test
    void loadedOfferingFiresBeamWithoutWastingTimeOnAbsorption() {
        Move absorption = absorption();
        Move beam = beam();
        BattleCombatant hanami = hanami(absorption, beam);
        hanami.transactBoundedResources(null, 0, "FLOWER_OFFERING", 2);
        BattleCombatant enemy = enemy();
        BattleState state = stateAtRound(hanami, enemy, 4);

        BattlePlan plan = new HanamiAIStrategy().buildPlan(state, hanami, new SeededRandomSource(1L));

        assertTrue(plan.allSegments().stream().anyMatch(segment -> segment.getMove() == beam));
        assertFalse(plan.allSegments().stream()
            .anyMatch(segment -> segment.getMove() == absorption));
    }

    @Test
    void dispatcherPreservesTheResourceSequence() {
        Move absorption = absorption();
        Move beam = beam();
        BattleCombatant hanami = hanami(absorption, beam);
        BattleCombatant enemy = enemy();
        BattleState state = stateAtRound(hanami, enemy, 5);

        TeamBattlePlan teamPlan = new ArchetypeAIStrategy().selectTeamPlan(
            state, state.playerTeam().active(), new SeededRandomSource(1L));
        BattlePlan plan = teamPlan.get(hanami.getInstanceId());

        assertEquals(List.of(absorption, beam),
            chronologicalSegments(plan).stream().map(ActionSegment::getMove).toList());
    }

    @Test
    void eachRoundRaisesOrdinaryOffensivePressure() {
        Move pressure = attack("pressure", 10, false);
        BattleCombatant hanami = hanami(pressure);
        BattleState state = new BattleState(hanami, enemy());
        HanamiAIStrategy strategy = new HanamiAIStrategy();
        int lastAttackAp = 0;
        for (int round = 1; round <= 5; round++) {
            BattlePlan plan = strategy.buildPlan(state, hanami, new SeededRandomSource(42));
            int attackAp = plan.allSegments().stream().filter(s -> s.getMove().hasTag("ATTACK"))
                .mapToInt(ActionSegment::getApCost).sum();
            assertTrue(attackAp > lastAttackAp, "offense should grow in round " + round);
            lastAttackAp = attackAp;
            state.endRound();
        }
    }

    @Test
    void earlyRoundsBuildOfferingAndHoldLoadedBigMoves() {
        Move absorption = absorption();
        Move beam = beam();
        Move ordinary = attack("ordinary", 10, false);
        for (int round = 1; round <= 3; round++) {
            BattleCombatant hanami = hanami(absorption, beam, ordinary);
            hanami.transactBoundedResources(null, 0, "FLOWER_OFFERING", 1);
            BattleState state = stateAtRound(hanami, enemy(), round);
            BattlePlan plan = new HanamiAIStrategy().buildPlan(state, hanami, new SeededRandomSource(1));
            assertEquals(0, plan.selectedUses(beam));
            assertEquals(1, plan.selectedUses(absorption), "bank one charge while holding the beam");
            assertTrue(plan.selectedUses(ordinary) > 0, "reserved should still apply pressure");
            assertEquals(1, hanami.boundedResourceValue("FLOWER_OFFERING").orElseThrow(),
                "planning must not spend or generate actual charges");
        }
    }

    @Test
    void corneredHanamiUnleashesEvenInRoundOneWithASingleCharge() {
        Move beam = beam();
        Move absorption = absorption();
        BattleCombatant hanami = hanami(beam, absorption, attack("ordinary", 10, false));
        hanami.transactBoundedResources(null, 0, "FLOWER_OFFERING", 1);
        hanami.receiveDamage(hanami.getCurrentHp() * 7 / 10);
        BattleState state = new BattleState(hanami, enemy());
        BattlePlan plan = new HanamiAIStrategy().buildPlan(state, hanami, new SeededRandomSource(1));
        assertEquals(1, plan.selectedUses(beam));
        assertEquals(0, plan.selectedUses(absorption), "do not delay a loaded emergency shot");
        assertTrue(plan.allSegments().stream().filter(s -> s.getMove().hasTag("ATTACK")).count() > 2);
    }

    @Test
    void majorAreaAttacksWaitForLaterRoundsOrCornering() {
        Move ordinary = attack("ordinary", 10, false);
        Move area = attack("area", 10, true);
        BattleCombatant hanami = hanami(ordinary, area);
        BattleState state = new BattleState(hanami, enemy());
        HanamiAIStrategy strategy = new HanamiAIStrategy();
        for (int seed = 0; seed < 20; seed++) {
            assertEquals(0, strategy.buildPlan(state, hanami, new SeededRandomSource(seed)).selectedUses(area));
        }
        state.endRound();
        state.endRound();
        state.endRound();
        int lateUses = 0;
        for (int seed = 0; seed < 20; seed++) {
            lateUses += strategy.buildPlan(state, hanami, new SeededRandomSource(seed)).selectedUses(area);
        }
        assertTrue(lateUses > 0, "major AoE becomes part of her late offense");
    }

    @Test
    void dispatcherDoesNotBypassEarlyRestraintWithLethalPromotion() {
        Move beam = beam();
        BattleCombatant hanami = hanami(beam, attack("ordinary", 10, false));
        hanami.transactBoundedResources(null, 0, "FLOWER_OFFERING", 3);
        BattleCombatant enemy = enemy();
        enemy.receiveDamage(enemy.getCurrentHp() - 1);
        BattleState state = new BattleState(hanami, enemy);
        assertTrue(SmartAIScoring.estimatedDamage(beam, hanami, enemy) >= enemy.getCurrentHp());
        BattlePlan plan = new ArchetypeAIStrategy().selectTeamPlan(
            state, state.playerTeam().active(), new SeededRandomSource(1)).get(hanami.getInstanceId());
        assertEquals(0, plan.selectedUses(beam));
        assertFalse(plan.allSegments().isEmpty());
    }

    @Test
    void directTeamPlannerAlsoRespectsRoundProgressionAndReservations() {
        Move beam = beam();
        BattleCombatant hanami = hanami(beam, attack("ordinary", 10, false));
        hanami.transactBoundedResources(null, 0, "FLOWER_OFFERING", 3);
        BattleCombatant enemy = enemy();
        enemy.receiveDamage(enemy.getCurrentHp() - 1);
        BattleState state = new BattleState(hanami, enemy);
        HanamiAIStrategy strategy = new HanamiAIStrategy();
        BattlePlan early = strategy.selectTeamPlan(state, state.playerTeam().active(), new SeededRandomSource(1))
            .get(hanami.getInstanceId());
        assertEquals(0, early.selectedUses(beam));
        while (state.getRoundNumber() < 4) state.endRound();
        BattlePlan late = strategy.selectTeamPlan(state, state.playerTeam().active(), new SeededRandomSource(1))
            .get(hanami.getInstanceId());
        assertEquals(1, late.selectedUses(beam));
    }

    @Test
    void roundFourTopsUpASmallOfferingBeforeFiring() {
        Move absorption = absorption();
        Move beam = beam();
        BattleCombatant hanami = hanami(absorption, beam);
        hanami.transactBoundedResources(null, 0, "FLOWER_OFFERING", 1);
        BattleState state = stateAtRound(hanami, enemy(), 4);
        BattlePlan plan = new HanamiAIStrategy().buildPlan(state, hanami, new SeededRandomSource(1));
        List<ActionSegment> segments = chronologicalSegments(plan);
        assertEquals(List.of(absorption, beam), segments.stream().map(ActionSegment::getMove).toList());
        assertTrue(segments.get(0).getFireTick() < segments.get(1).getStartTick());
        assertNull(MoveAvailability.boundedResourcePlanRestrictionReason(hanami,
            segments.stream().map(ActionSegment::getMove).toList()));
    }

    @Test
    void fullOfferingDoesNotCauseWastefulAbsorptionWhileReserved() {
        Move absorption = absorption();
        Move beam = beam();
        Move ordinary = attack("ordinary", 10, false);
        BattleCombatant hanami = hanami(absorption, beam, ordinary);
        hanami.transactBoundedResources(null, 0, "FLOWER_OFFERING", 3);
        BattlePlan plan = new HanamiAIStrategy().buildPlan(
            new BattleState(hanami, enemy()), hanami, new SeededRandomSource(1));
        assertEquals(0, plan.selectedUses(absorption));
        assertEquals(0, plan.selectedUses(beam));
        assertTrue(plan.selectedUses(ordinary) > 0);
    }

    @Test
    void bankUsesAuthoredChargeAmountAndRespectsCapacity() {
        MoveEffectData gain = AbilityEffectType.TRANSACT_BOUNDED_RESOURCE.createDefaultMoveEffect();
        gain.sourceResourceAmount = 0;
        gain.targetResourceKey = "FLOWER_OFFERING";
        gain.targetResourceAmount = 2;
        gain.trigger = MoveEffectTrigger.ON_FIRE.name();
        Move generator = new Move.Builder("double-charge").name("double-charge")
            .moveType(MoveType.CURSED_SPIRIT).category(MoveCategory.UTILITY)
            .apCost(4).unleashPoint(3).effects(List.of(gain)).build();
        Move beam = beam();
        BattleCombatant hanami = hanami(generator, beam);
        BattlePlan latePlan = new HanamiAIStrategy().buildPlan(
            stateAtRound(hanami, enemy(), 4), hanami, new SeededRandomSource(1));
        assertEquals(1, latePlan.selectedUses(beam), "two authored charges meet the round-four threshold");
        assertNull(MoveAvailability.boundedResourcePlanRestrictionReason(hanami,
            latePlan.allSegments().stream().map(ActionSegment::getMove).toList()));
        hanami.transactBoundedResources(null, 0, "FLOWER_OFFERING", 2);
        BattlePlan earlyPlan = new HanamiAIStrategy().buildPlan(
            new BattleState(hanami, enemy()), hanami, new SeededRandomSource(1));
        assertEquals(0, earlyPlan.selectedUses(generator), "cannot add two charges into one free slot");
    }

    @Test
    void emergencyChargeIsRolledBackWhenTheBeamCannotFit() {
        MoveEffectData gain = AbilityEffectType.TRANSACT_BOUNDED_RESOURCE.createDefaultMoveEffect();
        gain.sourceResourceAmount = 0;
        gain.targetResourceKey = "FLOWER_OFFERING";
        gain.targetResourceAmount = 1;
        gain.trigger = MoveEffectTrigger.ON_FIRE.name();
        Move generator = new Move.Builder("slow-charge").name("slow-charge")
            .moveType(MoveType.CURSED_SPIRIT).category(MoveCategory.UTILITY)
            .apCost(20).unleashPoint(20).effects(List.of(gain)).build();
        MoveEffectData consume = AbilityEffectType.CONSUME_BOUNDED_RESOURCE_FOR_BASE_POWER.createDefaultMoveEffect();
        consume.sourceResourceKey = "FLOWER_OFFERING";
        consume.trigger = MoveEffectTrigger.ON_START.name();
        BattleCombatant budgetSource = hanami();
        Move beam = new Move.Builder("wide-beam").name("wide-beam").moveType(MoveType.CURSED_SPIRIT)
            .category(MoveCategory.CURSED_ENERGY).basePower(60)
            .apCost(budgetSource.getMaxApBar()).unleashPoint(1).effects(List.of(consume)).build();
        Move ordinary = attack("ordinary", 10, false);
        BattleCombatant hanami = hanami(generator, beam, ordinary);
        hanami.receiveDamage(hanami.getCurrentHp() * 7 / 10);
        BattlePlan plan = new HanamiAIStrategy().buildPlan(
            new BattleState(hanami, enemy()), hanami, new SeededRandomSource(1));
        assertEquals(0, plan.selectedUses(generator));
        assertEquals(0, plan.selectedUses(beam));
        assertTrue(plan.selectedUses(ordinary) > 2);
    }

    @Test
    void planningIsRepeatableAndIgnoresEnemyCommittedTimeline() {
        Move ordinary = attack("ordinary", 10, false);
        Move area = attack("area", 10, true);
        BattleCombatant hanami = hanami(absorption(), beam(), ordinary, area);
        Move enemyAttack = AIFixtures.meleeAttack("enemy-attack", 20, 10);
        BattleCombatant enemy = AIFixtures.sorcerer("enemy", enemyAttack);
        BattleState state = stateAtRound(hanami, enemy, 4);
        HanamiAIStrategy strategy = new HanamiAIStrategy();
        BattlePlan first = strategy.buildPlan(state, hanami, new SeededRandomSource(8));
        BattlePlan enemyPlan = new BattlePlan(enemy.getMaxApBar(), enemy.getCurrentCe(), 200);
        assertNotNull(enemyPlan.place(enemyAttack, 40, 0));
        enemy.setPlan(enemyPlan);
        enemy.setTimeline(enemyPlan.toLegacyTimeline());
        BattlePlan second = strategy.buildPlan(state, hanami, new SeededRandomSource(8));
        assertEquals(signature(first), signature(second));
        assertEquals(4, state.getRoundNumber());
    }

    private static List<String> signature(BattlePlan plan) {
        return chronologicalSegments(plan).stream()
            .map(s -> s.getMove().getId() + ":" + s.getStartTick() + ":" + s.getActualCeCost()).toList();
    }

    private static BattleState stateAtRound(BattleCombatant hanami, BattleCombatant enemy, int round) {
        BattleState state = new BattleState(hanami, enemy);
        while (state.getRoundNumber() < round) state.endRound();
        return state;
    }

    private static Move attack(String id, int ap, boolean area) {
        Move.Builder builder = new Move.Builder(id).name(id).moveType(MoveType.CURSED_SPIRIT)
            .category(MoveCategory.CURSED_ENERGY).basePower(30).apCost(ap).unleashPoint(1);
        if (area) builder.tags(Set.of(MoveTag.ATTACK, MoveTag.CURSED_ENERGY, MoveTag.AOE))
            .aoeType(AoeType.ALL_ENEMIES);
        else builder.tags(Set.of(MoveTag.ATTACK, MoveTag.CURSED_ENERGY));
        return builder.build();
    }

    private static BattleCombatant hanami(Move... moves) {
        CharacterStats stats = new CharacterStats.Builder()
            .speed(200)
            .combatAbility(200)
            .cursedEnergyReserves(200)
            .cursedEnergyEfficiency(200)
            .build();
        BattleCombatant combatant = new BattleCombatant(new CursedSpiritCharacter(
            "HANAMI", "Hanami", stats, "Disaster Plants", List.of(moves)));
        combatant.defineBoundedResource("FLOWER_OFFERING", "Flower Offering", 3, 0);
        return combatant;
    }

    private static BattleCombatant enemy() {
        return new BattleCombatant(new SorcererCharacter(
            "ENEMY", "Enemy", new CharacterStats.Builder().build(), null, List.of()));
    }

    private static Move absorption() {
        MoveEffectData effect = AbilityEffectType.TRANSACT_BOUNDED_RESOURCE
            .createDefaultMoveEffect();
        effect.effectId = "effect-000000";
        effect.sourceResourceKey = null;
        effect.sourceResourceAmount = 0;
        effect.targetResourceKey = "FLOWER_OFFERING";
        effect.targetResourceAmount = 1;
        effect.trigger = MoveEffectTrigger.ON_FIRE.name();
        return new Move.Builder("ABSORB")
            .name("Absorb")
            .moveType(MoveType.CURSED_SPIRIT)
            .category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY, MoveTag.CURSED_ENERGY))
            .apCost(2)
            .unleashPoint(1)
            .effects(List.of(effect))
            .build();
    }

    private static Move beam() {
        MoveEffectData effect = AbilityEffectType.CONSUME_BOUNDED_RESOURCE_FOR_BASE_POWER
            .createDefaultMoveEffect();
        effect.effectId = "effect-000000";
        effect.sourceResourceKey = "FLOWER_OFFERING";
        effect.trigger = MoveEffectTrigger.ON_START.name();
        return new Move.Builder("BEAM")
            .name("Beam")
            .moveType(MoveType.CURSED_SPIRIT)
            .category(MoveCategory.CURSED_ENERGY)
            .tags(Set.of(MoveTag.ATTACK, MoveTag.CURSED_ENERGY))
            .basePower(60)
            .apCost(2)
            .unleashPoint(1)
            .effects(List.of(effect))
            .build();
    }

    private static List<ActionSegment> chronologicalSegments(BattlePlan plan) {
        return plan.allSegments().stream()
            .sorted(java.util.Comparator.comparingInt(ActionSegment::getStartTick))
            .toList();
    }
}
