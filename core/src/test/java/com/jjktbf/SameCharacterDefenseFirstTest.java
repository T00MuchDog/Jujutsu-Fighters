package com.jjktbf;

import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.move.BlockStyle;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the same-character same-tick defense-first rule.
 *
 * <p>Rule (see CombatResolver#comparePriority): when several of ONE
 * character's own moves fire on the same tick, the defensive move fires
 * before the offensive one — regardless of unleash point. Cross-character
 * order is decided by instant status and Speed only (see
 * {@link SpeedPriorityDefenseTest}); move category never orders moves of
 * different characters.
 *
 * <p>This locks the rule for every instant/non-instant alignment: previously
 * an instant offense beat a delayed defense of the same character, and two
 * equal-priority moves of one character were ordered by the random tiebreak.
 */
public class SameCharacterDefenseFirstTest {

    /** Instant attack: fires at its start tick (unleashPoint 1). */
    private static final Move ATTACK_INSTANT = new Move.Builder("ATTACK_INSTANT")
        .name("Instant Strike")
        .category(MoveCategory.PHYSICAL)
        .basePower(10)
        .neverMiss(true)
        .apCost(2)
        .unleashPoint(1)
        .build();

    /** Delayed attack: fires 4 ticks into its own window. */
    private static final Move ATTACK_DELAYED = new Move.Builder("ATTACK_DELAYED")
        .name("Delayed Strike")
        .category(MoveCategory.PHYSICAL)
        .basePower(10)
        .neverMiss(true)
        .apCost(5)
        .unleashPoint(4)
        .build();

    /** Instant block: fires at its start tick. */
    private static final Move BLOCK_INSTANT = new Move.Builder("BLOCK_INSTANT")
        .name("Instant Block")
        .category(MoveCategory.DEFENSIVE)
        .defenseType(DefenseType.BLOCK)
        .blockStyle(BlockStyle.PERCENTAGE)
        .blockDamageReduction(100)
        .apCost(5)
        .unleashPoint(1)
        .build();

    /** Delayed block: fires 4 ticks into its own window. */
    private static final Move BLOCK_DELAYED = new Move.Builder("BLOCK_DELAYED")
        .name("Delayed Block")
        .category(MoveCategory.DEFENSIVE)
        .defenseType(DefenseType.BLOCK)
        .blockStyle(BlockStyle.PERCENTAGE)
        .blockDamageReduction(100)
        .apCost(5)
        .unleashPoint(4)
        .build();

    /** Defense first even against an instant offense (was: offense first). */
    @Test
    void delayedDefenseFiresBeforeOwnInstantAttack() {
        List<String> fired = resolveFiringOrder(ATTACK_INSTANT, 6, BLOCK_DELAYED, 3);

        assertEquals(List.of("BLOCK_DELAYED", "ATTACK_INSTANT"), fired,
            "A character's own same-tick defense must fire before their own "
            + "instant offense.");
    }

    /** Instant defense first against a delayed offense (unchanged outcome). */
    @Test
    void instantDefenseFiresBeforeOwnDelayedAttack() {
        List<String> fired = resolveFiringOrder(ATTACK_DELAYED, 3, BLOCK_INSTANT, 6);

        assertEquals(List.of("BLOCK_INSTANT", "ATTACK_DELAYED"), fired);
    }

    /** Neither instant: defense first (was: random tiebreak). */
    @Test
    void delayedDefenseFiresBeforeOwnDelayedAttack() {
        List<String> fired = resolveFiringOrder(ATTACK_DELAYED, 3, BLOCK_DELAYED, 3);

        assertEquals(List.of("BLOCK_DELAYED", "ATTACK_DELAYED"), fired,
            "Two equal-priority moves of one character must not be left to the "
            + "random tiebreak — defense commits first.");
    }

    // -------------------------------------------------------------------------

    /**
     * Place the attack (offensive board) and block (defensive board) so both
     * fire on tick 6, resolve a full round, and return the order of the user's
     * MOVE_FIRED events by move id.
     */
    private static List<String> resolveFiringOrder(
        Move attack, int attackStart, Move block, int blockStart
    ) {
        Character userChar = new SorcererCharacter(
            "U", "User",
            new CharacterStats.Builder().vitality(300).speed(100).build(),
            null, List.of(attack, block));
        Character enemyChar = new SorcererCharacter(
            "E", "Enemy",
            new CharacterStats.Builder().vitality(300).speed(100).build(),
            null, List.of());

        BattlePlan plan = new BattlePlan(60, 100, 20);
        assertNotNull(plan.place(attack, attackStart, 0), "attack placement");
        assertNotNull(plan.place(block, blockStart, 0), "block placement");

        BattleCombatant user = new BattleCombatant(userChar);
        BattleCombatant enemy = new BattleCombatant(enemyChar);
        user.setTimeline(plan.toLegacyTimeline());

        BattleState state = new BattleState(user, enemy);
        state.transitionTo(BattleState.Phase.RESOLUTION);

        List<CombatEvent> events = new CombatResolver(new FixedRandom(0.0)).resolveRound(state);
        List<String> fired = events.stream()
            .filter(e -> e.getType() == CombatEvent.Type.MOVE_FIRED)
            .filter(e -> e.getSource() == user)
            .map(e -> e.getMove().getId())
            .toList();
        assertEquals(2, fired.size(), "both moves should fire exactly once");
        return fired;
    }

    /** Deterministic RNG: always returns the same double, for reproducible rolls. */
    private static final class FixedRandom extends Random {
        private final double value;
        private FixedRandom(double value) { this.value = value; }
        @Override public double nextDouble() { return value; }
        @Override public boolean nextBoolean() { return value < 0.5; }
    }
}
