package com.jjktbf;

import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveStartedEventTest {

    @Test
    void windUpMoveAnnouncesItselfOnItsStartTickBeforeFiring() {
        Move windUp = move("Wind-Up", 2);
        BattleCombatant user = combatant("USER", windUp);
        BattleCombatant enemy = combatant("ENEMY", null);
        BattleState state = new BattleState(user, enemy);
        Timeline timeline = new Timeline(6);
        timeline.placeAt(windUp, 1, 0);
        user.setTimeline(timeline);
        enemy.setTimeline(new Timeline(6));
        state.transitionTo(BattleState.Phase.RESOLUTION);
        CombatResolver resolver = new CombatResolver(new ZeroRandom());

        resolver.beginResolution(state);
        List<CombatEvent> startEvents = resolver.resolveTick(state);

        CombatEvent started = startEvents.stream()
            .filter(event -> event.getType() == CombatEvent.Type.MOVE_STARTED)
            .findFirst().orElseThrow();
        assertEquals(user, started.getSource());
        assertEquals(windUp, started.getMove());
        assertEquals(1, started.getTick());
        assertTrue(started.getMessage().contains("prepares"));
        assertTrue(started.getMessage().contains(windUp.getName()));
        assertTrue(startEvents.stream().noneMatch(
            event -> event.getType() == CombatEvent.Type.MOVE_FIRED));

        List<CombatEvent> fireEvents = resolver.resolveTick(state);
        assertTrue(fireEvents.stream().anyMatch(
            event -> event.getType() == CombatEvent.Type.MOVE_FIRED
                && event.getMove() == windUp));
    }

    @Test
    void instantMoveDoesNotDuplicateTheStartLine() {
        Move instant = move("Instant", 1);
        BattleCombatant user = combatant("USER", instant);
        BattleCombatant enemy = combatant("ENEMY", null);
        BattleState state = new BattleState(user, enemy);
        Timeline timeline = new Timeline(6);
        timeline.placeAt(instant, 1, 0);
        user.setTimeline(timeline);
        enemy.setTimeline(new Timeline(6));
        state.transitionTo(BattleState.Phase.RESOLUTION);
        CombatResolver resolver = new CombatResolver(new ZeroRandom());

        resolver.beginResolution(state);
        List<CombatEvent> events = resolver.resolveTick(state);

        assertTrue(events.stream().noneMatch(
            event -> event.getType() == CombatEvent.Type.MOVE_STARTED));
        assertTrue(events.stream().anyMatch(
            event -> event.getType() == CombatEvent.Type.MOVE_FIRED
                && event.getMove() == instant));
    }

    @Test
    void moveWithoutCeNeverStarts() {
        Move costly = new Move.Builder("COSTLY")
            .name("Costly Wind-Up")
            .category(MoveCategory.CURSED_ENERGY)
            .basePower(10)
            .apCost(2)
            .unleashPoint(2)
            .baseCeCost(10)
            .hasCeCost(true)
            .build();
        BattleCombatant user = combatant("USER", costly);
        user.drainCe(user.getCurrentCe());
        BattleCombatant enemy = combatant("ENEMY", null);
        BattleState state = new BattleState(user, enemy);
        Timeline timeline = new Timeline(6);
        timeline.placeAt(costly, 1, 10);
        user.setTimeline(timeline);
        enemy.setTimeline(new Timeline(6));
        state.transitionTo(BattleState.Phase.RESOLUTION);
        CombatResolver resolver = new CombatResolver(new ZeroRandom());

        List<CombatEvent> events = resolver.resolveRound(state);

        assertTrue(events.stream().noneMatch(
            event -> event.getType() == CombatEvent.Type.MOVE_STARTED));
    }

    private static Move move(String name, int unleashPoint) {
        return new Move.Builder(name.toUpperCase().replace('-', '_'))
            .name(name)
            .category(MoveCategory.CURSED_ENERGY)
            .basePower(10)
            .apCost(2)
            .unleashPoint(unleashPoint)
            .build();
    }

    private static BattleCombatant combatant(String id, Move move) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(100)
            .cursedEnergyReserves(150)
            .cursedEnergyEfficiency(80)
            .build();
        return new BattleCombatant(new SorcererCharacter(
            id, id, stats, null, move == null ? List.of() : List.of(move)));
    }

    private static final class ZeroRandom implements RandomSource {
        @Override public int nextInt(int bound) { return 0; }
        @Override public double nextDouble() { return 0.0; }
        @Override public boolean nextBoolean() { return false; }
    }
}
