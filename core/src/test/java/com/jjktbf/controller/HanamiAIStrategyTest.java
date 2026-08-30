package com.jjktbf.controller;

import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.CursedSpiritCharacter;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.TeamBattlePlan;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.MoveType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HanamiAIStrategyTest {

    @Test
    void emptyOfferingSequencesAbsorptionBeforeBeam() {
        Move absorption = absorption();
        Move beam = beam();
        BattleCombatant hanami = hanami(absorption, beam);
        BattleCombatant enemy = enemy();
        new BattleState(hanami, enemy);

        BattlePlan plan = new HanamiAIStrategy().selectPlan(
            hanami, enemy, new SeededRandomSource(1L));
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
        new BattleState(hanami, enemy);

        BattlePlan plan = new HanamiAIStrategy().selectPlan(
            hanami, enemy, new SeededRandomSource(1L));

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
        BattleState state = new BattleState(hanami, enemy);

        TeamBattlePlan teamPlan = new ArchetypeAIStrategy().selectTeamPlan(
            state, state.playerTeam().active(), new SeededRandomSource(1L));
        BattlePlan plan = teamPlan.get(hanami.getInstanceId());

        assertEquals(List.of(absorption, beam),
            chronologicalSegments(plan).stream().map(ActionSegment::getMove).toList());
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
