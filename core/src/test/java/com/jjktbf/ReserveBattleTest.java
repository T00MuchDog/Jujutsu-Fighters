package com.jjktbf;

import com.jjktbf.controller.ArchetypeAIStrategy;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.Equipment;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleFormat;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.BattleTeam;
import com.jjktbf.model.combat.BattleTeamId;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.TeamBattlePlan;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReserveBattleTest {

    @Test
    void sixVSixStartsWithThreeFieldedAndThreeInReserve() {
        Fixture fixture = fixture();

        assertEquals(3, fixture.state.playerTeam().activeFighters().size());
        assertEquals(List.of("P4", "P5", "P6"), fixture.state.playerTeam().reserves().stream()
            .map(combatant -> combatant.getCharacter().getName()).toList());
        assertEquals(6, fixture.state.playerTeam().livingFighters().size());
        assertFalse(fixture.state.checkAndResolveBattleOver());
    }

    @Test
    void manualSwitchUsesTheRoundAndIncomingOccupantTakesThePlannedAttack() {
        Fixture fixture = fixture();
        BattleCombatant outgoing = fixture.players.get(0);
        BattleCombatant incoming = fixture.players.get(3);
        BattleCombatant attacker = fixture.enemies.get(0);
        int outgoingHp = outgoing.getCurrentHp();
        int incomingHp = incoming.getCurrentHp();

        TeamBattlePlan players = emptyTeamPlan(fixture.state, fixture.state.playerTeam());
        players.switchTo(outgoing.getInstanceId(), incoming.getInstanceId());
        TeamBattlePlan enemies = emptyTeamPlan(fixture.state, fixture.state.enemyTeam());
        BattlePlan attackPlan = BattlePlan.forCombatant(
            attacker, fixture.state.getTimelineGridLength());
        attackPlan.place(attack(), 1, 0, outgoing.getInstanceId());
        enemies.put(attacker.getInstanceId(), attackPlan);
        attach(fixture.state, fixture.state.playerTeam(), players);
        attach(fixture.state, fixture.state.enemyTeam(), enemies);

        fixture.state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(new SeededRandomSource(4L))
            .resolveRound(fixture.state);

        assertTrue(outgoing.isReserve());
        assertTrue(incoming.isActive());
        assertEquals(outgoingHp, outgoing.getCurrentHp());
        assertTrue(incoming.getCurrentHp() < incomingHp);
        assertNull(outgoing.getTimeline());
        assertNull(incoming.getTimeline());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.COMBATANT_SWITCHED
                && event.getSource() == outgoing
                && event.getTarget() == incoming));
        assertFalse(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.TARGET_RETARGETED));
    }

    @Test
    void defeatedFieldFighterIsAutomaticallyReplacedAtRoundEnd() {
        Fixture fixture = fixture();
        BattleCombatant defeated = fixture.players.get(0);
        BattleCombatant replacement = fixture.players.get(3);
        defeated.receiveDamage(Integer.MAX_VALUE);
        fixture.state.transitionTo(BattleState.Phase.ROUND_END);

        List<CombatEvent> events = new CombatResolver(new SeededRandomSource(2L))
            .processRoundEnd(fixture.state);

        assertTrue(defeated.isLifecycleDefeated());
        assertTrue(replacement.isActive());
        assertEquals(replacement, fixture.state.playerTeam().fighterAt(0));
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.COMBATANT_REPLACED
                && event.getSource() == defeated
                && event.getTarget() == replacement));
    }

    @Test
    void livingReservesPreventDefeatAndVacantSlotTargetsDoNotJumpSlots() {
        Fixture fixture = fixture();
        BattleCombatant fallen = fixture.players.get(0);
        BattleCombatant otherActive = fixture.players.get(1);
        BattleCombatant killer = fixture.enemies.get(0);
        BattleCombatant attacker = fixture.enemies.get(1);
        int otherHp = otherActive.getCurrentHp();

        BattlePlan lethalPlan = BattlePlan.forCombatant(
            killer, fixture.state.getTimelineGridLength());
        lethalPlan.place(lethalAttack(), 1, 0, fallen.getInstanceId());
        killer.setPlan(lethalPlan);
        killer.setTimeline(lethalPlan.toLegacyTimeline());
        BattlePlan plan = BattlePlan.forCombatant(attacker, fixture.state.getTimelineGridLength());
        plan.place(attack(), 1, 0, fallen.getInstanceId());
        attacker.setPlan(plan);
        attacker.setTimeline(plan.toLegacyTimeline());
        fixture.state.transitionTo(BattleState.Phase.RESOLUTION);
        new CombatResolver(new SeededRandomSource(3L)).resolveRound(fixture.state);

        assertFalse(fixture.state.isBattleOver());
        assertEquals(otherHp, otherActive.getCurrentHp());
        assertEquals(3, fixture.state.playerTeam().reserves().size());
    }

    @Test
    void lowHealthAiSpendsItsRoundSwitchingToTheFirstHealthiestReserve() {
        Fixture fixture = fixture();
        BattleCombatant weakened = fixture.enemies.get(0);
        weakened.receiveDamage(weakened.getMaxHp() - weakened.getMaxHp() / 4);

        TeamBattlePlan plan = new ArchetypeAIStrategy().selectTeamPlan(
            fixture.state,
            fixture.state.enemyTeam().active(),
            new SeededRandomSource(5L));

        assertTrue(plan.isSwitch(weakened.getInstanceId()));
        assertEquals(fixture.enemies.get(3).getInstanceId(),
            plan.switchTarget(weakened.getInstanceId()));
        assertNull(plan.get(weakened.getInstanceId()));
    }

    @Test
    void switchesDoNotResizeTheActionGridAfterPlansAreLocked() {
        Fixture fixture = fixture();
        assertTrue(fixture.players.get(0).getMaxApBar()
            > fixture.players.get(1).getMaxApBar());
        int plannedGridLength = fixture.state.getTimelineGridLength();
        TeamBattlePlan players = emptyTeamPlan(fixture.state, fixture.state.playerTeam());
        players.switchTo(
            fixture.players.get(0).getInstanceId(),
            fixture.players.get(3).getInstanceId());
        TeamBattlePlan enemies = emptyTeamPlan(fixture.state, fixture.state.enemyTeam());
        enemies.switchTo(
            fixture.enemies.get(0).getInstanceId(),
            fixture.enemies.get(3).getInstanceId());
        attach(fixture.state, fixture.state.playerTeam(), players);
        attach(fixture.state, fixture.state.enemyTeam(), enemies);

        fixture.state.transitionTo(BattleState.Phase.RESOLUTION);
        new CombatResolver(new SeededRandomSource(6L)).beginResolution(fixture.state);

        assertEquals(plannedGridLength, fixture.state.getTimelineGridLength());
    }

    private static TeamBattlePlan emptyTeamPlan(BattleState state, BattleTeam team) {
        TeamBattlePlan plan = new TeamBattlePlan(team.id(), state.getTimelineGridLength());
        for (BattleCombatant actor : team.active()) {
            plan.put(actor.getInstanceId(),
                BattlePlan.forCombatant(actor, state.getTimelineGridLength()));
        }
        return plan;
    }

    private static void attach(BattleState state, BattleTeam team, TeamBattlePlan teamPlan) {
        for (BattleCombatant actor : team.all()) {
            actor.setPlan(null);
            actor.setTimeline(null);
        }
        for (BattleCombatant actor : team.active()) {
            if (teamPlan.isSwitch(actor.getInstanceId())) continue;
            BattlePlan plan = teamPlan.get(actor.getInstanceId());
            actor.setPlan(plan);
            actor.setTimeline(plan.toLegacyTimeline());
        }
        state.queueSwitches(teamPlan);
    }

    private static Move attack() {
        return new Move.Builder("reserve-test-attack")
            .name("Reserve Test Attack")
            .category(MoveCategory.PHYSICAL)
            .neverMiss(true)
            .apCost(2)
            .unleashPoint(1)
            .hitComponents(List.of(new HitComponent(
                30, Set.of(MoveTag.PHYSICAL), 0, false, true)))
            .build();
    }

    private static Move lethalAttack() {
        return new Move.Builder("reserve-test-lethal")
            .name("Reserve Test Lethal")
            .category(MoveCategory.PHYSICAL)
            .neverMiss(true)
            .apCost(2)
            .unleashPoint(1)
            .hitComponents(List.of(new HitComponent(
                100_000, Set.of(MoveTag.PHYSICAL), 0, false, true)))
            .build();
    }

    private static Fixture fixture() {
        List<BattleCombatant> players = fighters("P");
        List<BattleCombatant> enemies = fighters("E");
        BattleState state = new BattleState(
            BattleState.teamOfFighters(BattleTeamId.PLAYER, players),
            BattleState.teamOfFighters(BattleTeamId.ENEMY, enemies),
            BattleFormat.SIX_V_SIX);
        return new Fixture(state, players, enemies);
    }

    private static List<BattleCombatant> fighters(String prefix) {
        List<BattleCombatant> fighters = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            String name = prefix + index;
            CharacterStats stats = new CharacterStats.Builder()
                .vitality(200)
                .strength(100)
                .speed(index == 1 ? 1000 : 1)
                .build();
            SorcererCharacter character = new SorcererCharacter(
                name.toLowerCase(), name, stats, null, List.of(), List.of(), Equipment.NONE);
            fighters.add(new BattleCombatant(character, List.of()));
        }
        return List.copyOf(fighters);
    }

    private record Fixture(
        BattleState state,
        List<BattleCombatant> players,
        List<BattleCombatant> enemies
    ) { }
}
