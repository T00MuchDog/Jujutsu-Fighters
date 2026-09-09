package com.jjktbf;

import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.move.StatusEffectType;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestrainedStatusTest {

    @Test
    void restrainedHalvesCurrentSpeedAndRefreshesInsteadOfStacking() {
        BattleCombatant combatant = combatant("USER", null, 85, 100);
        combatant.addStatusEffect(new StatusEffect(StatusEffectType.RESTRAINED, 1, 5.0));
        combatant.addStatusEffect(new StatusEffect(StatusEffectType.RESTRAINED, 2, 8.0));

        assertEquals(43, combatant.getEffectiveStats().getSpeed());
        assertEquals(1, combatant.getActiveEffects().stream()
            .filter(effect -> effect.getType() == StatusEffectType.RESTRAINED)
            .count());
        assertEquals(2, combatant.getActiveEffects().stream()
            .filter(effect -> effect.getType() == StatusEffectType.RESTRAINED)
            .findFirst().orElseThrow().getDurationRounds());
        assertEquals(8.0, combatant.getActiveEffects().stream()
            .filter(effect -> effect.getType() == StatusEffectType.RESTRAINED)
            .findFirst().orElseThrow().getMagnitude());
    }

    @Test
    void magnitudeFivePreservesTheExistingStrengthCurve() {
        assertEquals(0.01, CombatResolver.restraintBreakoutChance(10, 5), 0.000001);
        assertEquals(0.05, CombatResolver.restraintBreakoutChance(50, 5), 0.000001);
        assertEquals(0.40, CombatResolver.restraintBreakoutChance(400, 5), 0.000001);
        assertEquals(0.45, CombatResolver.restraintBreakoutChance(450, 5), 0.000001);
        assertEquals(0.50, CombatResolver.restraintBreakoutChance(472, 5), 0.000001);
        assertEquals(0.50, CombatResolver.restraintBreakoutChance(600, 5), 0.000001);
    }

    @Test
    void eachMagnitudeHasItsConfiguredMinimumAndMaximumBreakoutChance() {
        double[] minimums = {
            0.10, 0.06, 0.035, 0.02, 0.01, 0.006, 0.003, 0.0015, 0.0008, 0.0005
        };
        double[] maximums = {
            0.995, 0.875, 0.75, 0.625, 0.50, 0.35, 0.23, 0.14, 0.08, 0.05
        };
        for (int magnitude = 1; magnitude <= 10; magnitude++) {
            assertEquals(minimums[magnitude - 1],
                CombatResolver.restraintBreakoutChance(10, magnitude), 0.000001);
            assertEquals(maximums[magnitude - 1],
                CombatResolver.restraintBreakoutChance(472, magnitude), 0.000001);
        }
    }

    @Test
    void fractionalMagnitudesInterpolateBetweenLevels() {
        assertEquals(0.0045, CombatResolver.restraintBreakoutChance(10, 6.5), 0.000001);
        assertEquals(0.29, CombatResolver.restraintBreakoutChance(472, 6.5), 0.000001);
    }

    @Test
    void restrainedMagnitudeMustBeBetweenOneAndTen() {
        assertThrows(IllegalArgumentException.class,
            () -> new StatusEffect(StatusEffectType.RESTRAINED, 1, 0.9));
        assertThrows(IllegalArgumentException.class,
            () -> new StatusEffect(StatusEffectType.RESTRAINED, 1, 10.1));
    }

    @Test
    void failedBreakoutThenFivePercentRollCanStunTheCurrentAction() {
        Move move = utility("MOVE");
        BattleCombatant user = combatant("USER", move, 80, 100);
        BattleCombatant enemy = combatant("ENEMY", null, 80, 100);
        user.addStatusEffect(new StatusEffect(StatusEffectType.RESTRAINED, 1, 5.0));
        BattleState state = stateWithMove(user, enemy, move);

        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.99, 0.0))
            .resolveRound(state);

        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.MOVE_STUNNED));
        assertFalse(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.MOVE_FIRED && event.getMove() == move));
        assertTrue(user.hasEffect(StatusEffectType.RESTRAINED));
    }

    @Test
    void successfulBreakoutSuppressesTheStunRoll() {
        Move move = utility("MOVE");
        BattleCombatant user = combatant("USER", move, 80, 100);
        BattleCombatant enemy = combatant("ENEMY", null, 80, 100);
        user.addStatusEffect(new StatusEffect(StatusEffectType.RESTRAINED, 1, 5.0));
        BattleState state = stateWithMove(user, enemy, move);
        SequenceRandom random = new SequenceRandom(0.0);

        List<CombatEvent> events = new CombatResolver(random).resolveRound(state);

        assertFalse(user.hasEffect(StatusEffectType.RESTRAINED));
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.STATUS_EXPIRED
                && event.getMessage().contains("breaks free")));
        assertFalse(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.MOVE_STUNNED));
        assertEquals(1, random.calls());
    }

    private static BattleState stateWithMove(
        BattleCombatant user,
        BattleCombatant enemy,
        Move move
    ) {
        BattleState state = new BattleState(user, enemy);
        BattlePlan plan = new BattlePlan(user.getMaxApBar(), user.getCurrentCe());
        assertNotNull(plan.place(move, 1, 0));
        user.setTimeline(plan.toLegacyTimeline());
        enemy.setTimeline(new BattlePlan(enemy.getMaxApBar(), enemy.getCurrentCe()).toLegacyTimeline());
        state.transitionTo(BattleState.Phase.RESOLUTION);
        return state;
    }

    private static Move utility(String id) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY))
            .apCost(1)
            .unleashPoint(1)
            .build();
    }

    private static BattleCombatant combatant(
        String id,
        Move move,
        int speed,
        int strength
    ) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(100).strength(strength).durability(100).speed(speed)
            .cursedEnergyReserves(100).cursedEnergyEfficiency(100)
            .cursedEnergyOutput(100).jujutsuSkill(100)
            .combatAbility(100).cursedTechniqueMastery(100)
            .build();
        return new BattleCombatant(new SorcererCharacter(
            id, id, stats, null, move == null ? List.of() : List.of(move)));
    }

    private static final class SequenceRandom implements RandomSource {
        private final ArrayDeque<Double> values = new ArrayDeque<>();
        private int calls;

        private SequenceRandom(double... values) {
            for (double value : values) this.values.add(value);
        }

        private int calls() {
            return calls;
        }

        @Override public int nextInt(int bound) { return 0; }
        @Override public double nextDouble() {
            calls++;
            return values.isEmpty() ? 1.0 : values.removeFirst();
        }
        @Override public boolean nextBoolean() { return false; }
    }
}
