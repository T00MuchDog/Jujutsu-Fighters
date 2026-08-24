package com.jjktbf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.Equipment;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.controller.AIStrategy;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.BattleTeamId;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.MoveTargetSelection;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.TeamBattlePlan;
import com.jjktbf.model.move.CombatantPairTargeting;
import com.jjktbf.model.move.DefenseTargeting;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.character.AbilityEffectType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatantPairTargetingTest {
    @Test
    void moveDataRoundTripsPairTargetingAndDefaultsSafely() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        MoveData data = moveData("PAIR");
        data.pairTargeting = CombatantPairTargeting.ALLY_AND_ENEMY.name();

        MoveData restored = mapper.readValue(mapper.writeValueAsString(data), MoveData.class);
        Move move = restored.toMove();

        assertEquals(CombatantPairTargeting.ALLY_AND_ENEMY, move.getPairTargeting());
        assertEquals("ALLY_AND_ENEMY", MoveData.fromMove(move).pairTargeting);
        assertEquals(CombatantPairTargeting.NONE,
            moveData("DEFAULT").toMove().getPairTargeting());
        assertEquals(CombatantPairTargeting.NONE,
            CombatantPairTargeting.fromName("unknown"));
    }

    @Test
    void mixedPairRequiresOrderedDistinctActiveAllyThenEnemy() {
        BattleCombatant actor = fighter("Actor");
        BattleCombatant ally = fighter("Ally");
        BattleCombatant enemy = fighter("Enemy");
        BattleState state = state(actor, ally, enemy);
        Move move = pairMove("PAIR", CombatantPairTargeting.ALLY_AND_ENEMY);

        assertNotNull(MoveTargetSelection.validationError(
            state, actor, move, List.of(enemy.getInstanceId(), ally.getInstanceId())));
        assertNotNull(MoveTargetSelection.validationError(
            state, actor, move, List.of(ally.getInstanceId(), ally.getInstanceId())));
        assertNotNull(MoveTargetSelection.validationError(
            state, actor, move, List.of(actor.getInstanceId(), enemy.getInstanceId())));
        assertNull(MoveTargetSelection.validationError(
            state, actor, move, List.of(ally.getInstanceId(), enemy.getInstanceId())));
    }

    @Test
    void battleAndTeamPlansSharePairAndAllyDefenseValidation() {
        BattleCombatant actor = fighter("Actor");
        BattleCombatant ally = fighter("Ally");
        BattleCombatant enemy = fighter("Enemy");
        BattleState state = state(actor, ally, enemy);
        int grid = TeamBattlePlan.gridLengthForRound(state);

        Move pair = pairMove("PAIR", CombatantPairTargeting.ALLY_AND_ENEMY);
        BattlePlan actorPlan = new BattlePlan(actor.getMaxApBar(), actor.getCurrentCe(), grid);
        actorPlan.placeWithTargets(pair, 1, 0, List.of(ally.getInstanceId()));
        assertNotNull(actorPlan.missingTargetError());
        actorPlan.allSegments().get(0).setTargets(
            List.of(ally.getInstanceId(), enemy.getInstanceId()));
        assertNull(actorPlan.missingTargetError());

        Move allyDefense = new Move.Builder("ALLY_DEFENSE")
            .name("Ally Defense")
            .category(MoveCategory.DEFENSIVE)
            .defenseType(DefenseType.DODGE)
            .defenseTargeting(DefenseTargeting.SINGLE_ALLY)
            .apCost(5)
            .unleashPoint(1)
            .build();
        actorPlan.clear();
        actorPlan.place(allyDefense, 1, 0, ally.getInstanceId());

        TeamBattlePlan teamPlan = new TeamBattlePlan(BattleTeamId.PLAYER, grid);
        teamPlan.put(actor.getInstanceId(), actorPlan);
        teamPlan.put(ally.getInstanceId(),
            new BattlePlan(ally.getMaxApBar(), ally.getCurrentCe(), grid));
        assertNull(teamPlan.validationError(state));

        actorPlan.allSegments().get(0).setTarget(enemy.getInstanceId());
        assertNotNull(teamPlan.validationError(state));
    }

    @Test
    void aiAssignsMixedPairInDeterministicRelationshipOrder() {
        BattleCombatant actor = fighter("Actor");
        BattleCombatant ally = fighter("Ally");
        BattleCombatant enemy = fighter("Enemy");
        BattleState state = state(actor, ally, enemy);
        Move pair = pairMove("PAIR", CombatantPairTargeting.ALLY_AND_ENEMY);
        BattlePlan plan = new BattlePlan(actor.getMaxApBar(), actor.getCurrentCe(),
            TeamBattlePlan.gridLengthForRound(state));
        plan.place(pair, 1, 0);
        AIStrategy strategy = (ai, opponent, rng) -> plan;

        strategy.assignExplicitTargets(state, plan, actor, new SeededRandomSource(7L));

        assertEquals(List.of(ally.getInstanceId(), enemy.getInstanceId()),
            plan.allSegments().get(0).getTargets());
    }

    @Test
    void pairTargetingRejectsDamagingMovesAndDelayedPairEffects() {
        assertThrows(IllegalStateException.class, () -> new Move.Builder("ATTACK_PAIR")
            .name("Attack Pair")
            .category(MoveCategory.PHYSICAL)
            .tags(java.util.Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK))
            .pairTargeting(CombatantPairTargeting.SELF_AND_ENEMY)
            .apCost(5)
            .unleashPoint(1)
            .build());

        Move pairedDefense = new Move.Builder("PAIRED_DEFENSE")
            .name("Paired Defense")
            .category(MoveCategory.DEFENSIVE)
            .defenseType(DefenseType.DODGE)
            .pairTargeting(CombatantPairTargeting.SELF_AND_ALLY)
            .apCost(5)
            .unleashPoint(1)
            .build();
        assertEquals(CombatantPairTargeting.SELF_AND_ALLY,
            pairedDefense.getPairTargeting());

        assertThrows(IllegalStateException.class, () -> new Move.Builder("PAIR_COUNTER")
            .name("Pair Counter")
            .category(MoveCategory.DEFENSIVE)
            .tags(java.util.Set.of(MoveTag.DEFENSIVE, MoveTag.ATTACK, MoveTag.PHYSICAL))
            .defenseType(DefenseType.DODGE)
            .pairTargeting(CombatantPairTargeting.SELF_AND_ALLY)
            .attackLaunchMode(com.jjktbf.model.move.AttackLaunchMode.ON_DEFENCE)
            .hitComponents(List.of(new com.jjktbf.model.move.HitComponent(
                10, java.util.Set.of(MoveTag.PHYSICAL), 0, false, true)))
            .apCost(5)
            .unleashPoint(1)
            .build());

        MoveEffectData effect = AbilityEffectType.APPLY_STATUS.createDefaultMoveEffect();
        effect.target = AbilityEffectTarget.PAIR_FIRST.name();
        effect.trigger = MoveEffectTrigger.ON_HIT.name();
        assertThrows(IllegalStateException.class, () -> new Move.Builder("DELAYED_PAIR")
            .name("Delayed Pair")
            .category(MoveCategory.UTILITY)
            .pairTargeting(CombatantPairTargeting.SELF_AND_ENEMY)
            .effects(List.of(effect))
            .apCost(5)
            .unleashPoint(1)
            .build());
    }

    @Test
    void pairedDefenseProtectsBothSelfAndSelectedAlly() {
        BattleCombatant actor = fighter("Actor");
        BattleCombatant ally = fighter("Ally");
        BattleCombatant firstEnemy = fighter("First Enemy");
        BattleCombatant secondEnemy = fighter("Second Enemy");
        BattleState state = new BattleState(
            BattleState.teamOfFighters(BattleTeamId.PLAYER, List.of(actor, ally)),
            BattleState.teamOfFighters(
                BattleTeamId.ENEMY, List.of(firstEnemy, secondEnemy)));
        Move defense = new Move.Builder("RAPID_EXCHANGE")
            .name("Rapid Exchange")
            .category(MoveCategory.DEFENSIVE)
            .defenseType(DefenseType.DODGE)
            .dodgeChance(100)
            .blockDuration(10)
            .defenseUses(1)
            .pairTargeting(CombatantPairTargeting.SELF_AND_ALLY)
            .apCost(5)
            .unleashPoint(1)
            .build();
        Move attack = new Move.Builder("ATTACK")
            .name("Attack")
            .category(MoveCategory.PHYSICAL)
            .tags(java.util.Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK, MoveTag.MELEE))
            .neverMiss(true)
            .hitComponents(List.of(new HitComponent(
                80, java.util.Set.of(MoveTag.PHYSICAL), 0, false, true)))
            .apCost(8)
            .unleashPoint(5)
            .build();
        BattlePlan actorPlan = new BattlePlan(300, 300, 60);
        actorPlan.place(defense, 1, 0, ally.getInstanceId());
        actor.setTimeline(actorPlan.toLegacyTimeline());
        ally.setTimeline(new BattlePlan(300, 300, 60).toLegacyTimeline());
        BattlePlan firstPlan = new BattlePlan(300, 300, 60);
        firstPlan.place(attack, 1, 0, actor.getInstanceId());
        firstEnemy.setTimeline(firstPlan.toLegacyTimeline());
        BattlePlan secondPlan = new BattlePlan(300, 300, 60);
        secondPlan.place(attack, 1, 0, ally.getInstanceId());
        secondEnemy.setTimeline(secondPlan.toLegacyTimeline());
        int actorHp = actor.getCurrentHp();
        int allyHp = ally.getCurrentHp();

        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(
            new SeededRandomSource(1L)).resolveRound(state);

        assertEquals(actorHp, actor.getCurrentHp());
        assertEquals(allyHp, ally.getCurrentHp());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DEFENSE_GRANTED
                && event.getTarget() == ally));
    }

    private static MoveData moveData(String id) {
        MoveData data = new MoveData();
        data.id = id;
        data.name = id;
        data.tags = List.of("UTILITY");
        data.apCost = 5;
        data.unleashPoint = 1;
        return data;
    }

    private static Move pairMove(String id, CombatantPairTargeting targeting) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.UTILITY)
            .pairTargeting(targeting)
            .apCost(5)
            .unleashPoint(1)
            .build();
    }

    private static BattleState state(
        BattleCombatant actor,
        BattleCombatant ally,
        BattleCombatant enemy
    ) {
        return new BattleState(
            BattleState.teamOfFighters(BattleTeamId.PLAYER, List.of(actor, ally)),
            BattleState.teamOfFighters(BattleTeamId.ENEMY, List.of(enemy)));
    }

    private static BattleCombatant fighter(String name) {
        SorcererCharacter character = new SorcererCharacter(
            name.toLowerCase(), name, new CharacterStats.Builder().vitality(100).build(),
            null, List.of(), List.of(), Equipment.NONE);
        return new BattleCombatant(character, List.of());
    }
}
