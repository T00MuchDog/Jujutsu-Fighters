package com.jjktbf;

import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.DamageCalculator;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.BlockStyle;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import com.jjktbf.model.character.Equipment;
import com.jjktbf.model.weapon.WeaponType;

/**
 * Perfect reads: a FIXED-timing defence whose fire tick exactly matches the
 * incoming impact tick escalates — a block negates fully regardless of its
 * authored reduction, a dodge auto-succeeds, a parry staggers longer.
 */
public class PerfectReadTest {

    private static final Move ATTACK = new Move.Builder("PR_ATTACK")
        .name("Strike")
        .category(MoveCategory.PHYSICAL)
        .basePower(30)
        .neverMiss(true)
        .apCost(2)
        .unleashPoint(1)
        .build();

    private static final Move RANGED_ATTACK = new Move.Builder("PR_RANGED_ATTACK")
        .name("Cursed Bolt")
        .category(MoveCategory.PHYSICAL)
        .basePower(30)
        .neverMiss(true)
        .apCost(2)
        .unleashPoint(1)
        .tags(java.util.Set.of(
            com.jjktbf.model.move.MoveTag.PHYSICAL,
            com.jjktbf.model.move.MoveTag.ATTACK,
            com.jjktbf.model.move.MoveTag.RANGED))
        .build();

    private static final Move PARTIAL_BLOCK = new Move.Builder("PR_BLOCK")
        .name("Guard")
        .category(MoveCategory.DEFENSIVE)
        .defenseType(DefenseType.BLOCK)
        .blockStyle(BlockStyle.PERCENTAGE)
        .blockDamageReduction(50)
        .blockDuration(4)
        .canBeReinforced(true)
        .apCost(5)
        .unleashPoint(1)
        .build();

    private static final Move WEAK_DODGE = new Move.Builder("PR_DODGE")
        .name("Slip")
        .category(MoveCategory.DEFENSIVE)
        .defenseType(DefenseType.DODGE)
        .dodgeChance(10)
        .blockDuration(4)
        .apCost(5)
        .unleashPoint(1)
        .build();

    private static final Move PARRY = new Move.Builder("PR_PARRY")
        .name("Riposte Guard")
        .category(MoveCategory.DEFENSIVE)
        .defenseType(DefenseType.PARRY)
        .parryStaggerTicks(3)
        .blockDuration(4)
        .apCost(5)
        .unleashPoint(1)
        .build();

    /** Exact-tick partial block escalates to a full negate. */
    @Test
    void exactTickBlockFullyNegates() {
        List<CombatEvent> events = resolveRound(PARTIAL_BLOCK, 1, 1);

        assertTrue(events.stream().anyMatch(e -> e.getType() == CombatEvent.Type.MOVE_BLOCKED),
            "A perfect-read block must fully negate despite its 50% authored reduction.");
        assertTrue(events.stream().anyMatch(e -> e.getMessage() != null
                && e.getMessage().contains("PERFECT READ")),
            "A perfect read is announced in the battle log.");
        assertEquals(0, count(events, CombatEvent.Type.DAMAGE_DEALT));
    }

    /** One tick off: the same block only applies its authored 50% reduction. */
    @Test
    void offByOneTickBlockOnlyReduces() {
        List<CombatEvent> events = resolveRound(PARTIAL_BLOCK, 1, 2);

        assertEquals(1, count(events, CombatEvent.Type.MOVE_BLOCK_REDUCED),
            "The block window still covers tick 2, so it reduces.");
        assertEquals(0, count(events, CombatEvent.Type.MOVE_BLOCKED),
            "No perfect read one tick off — no full negate.");
        assertEquals(1, count(events, CombatEvent.Type.DAMAGE_DEALT),
            "A 50% block still lets damage through.");
    }

    @Test
    void blockAndReducedDamageEventsCarryTheActualDefenseMetadata() {
        Resolution blocked = resolveRoundWithCombatants(ATTACK, PARTIAL_BLOCK, 1, 1, true);
        CombatEvent blockedEvent = blocked.events().stream()
            .filter(e -> e.getType() == CombatEvent.Type.MOVE_BLOCKED)
            .findFirst().orElseThrow();

        assertEquals("PR_BLOCK", blockedEvent.getDefenseMoveId());
        assertEquals(Boolean.TRUE, blockedEvent.getDefenseReinforced());
        assertEquals("PR_ATTACK", blockedEvent.getMove().getId());

        Resolution reduced = resolveRoundWithCombatants(ATTACK, PARTIAL_BLOCK, 1, 2, false);
        CombatEvent reducedEvent = reduced.events().stream()
            .filter(e -> e.getType() == CombatEvent.Type.MOVE_BLOCK_REDUCED)
            .findFirst().orElseThrow();
        CombatEvent reducedDamage = reduced.events().stream()
            .filter(e -> e.getType() == CombatEvent.Type.DAMAGE_DEALT)
            .findFirst().orElseThrow();

        assertEquals("PR_BLOCK", reducedEvent.getDefenseMoveId());
        assertEquals(Boolean.FALSE, reducedEvent.getDefenseReinforced());
        assertEquals("PR_BLOCK", reducedDamage.getDefenseMoveId());
        assertEquals(Boolean.FALSE, reducedDamage.getDefenseReinforced());

        CombatEvent unblockedDamage = resolveRoundWithCombatants(ATTACK, null, 1, 1)
            .events().stream()
            .filter(e -> e.getType() == CombatEvent.Type.DAMAGE_DEALT)
            .findFirst().orElseThrow();
        assertNull(unblockedDamage.getDefenseMoveId());
        assertNull(unblockedDamage.getDefenseReinforced());
    }

    @Test
    void parryMetadataIsNotCopiedToItsReflectedDamageEvent() {
        Resolution resolution = resolveRoundWithCombatants(RANGED_ATTACK, PARRY, 1, 1);
        CombatEvent parried = resolution.events().stream()
            .filter(e -> e.getType() == CombatEvent.Type.MOVE_PARRIED)
            .findFirst().orElseThrow();
        CombatEvent reflected = resolution.events().stream()
            .filter(e -> e.getType() == CombatEvent.Type.DAMAGE_DEALT
                && e.getTarget() == resolution.attacker())
            .findFirst().orElseThrow();

        assertEquals("PR_PARRY", parried.getDefenseMoveId());
        assertEquals(Boolean.FALSE, parried.getDefenseReinforced());
        assertNull(reflected.getDefenseMoveId());
        assertNull(reflected.getDefenseReinforced());
    }

    /** Exact-tick dodge auto-succeeds despite a 10% authored chance. */
    @Test
    void exactTickDodgeAutoSucceeds() {
        List<CombatEvent> events = resolveRound(WEAK_DODGE, 1, 1);

        assertEquals(1, countDodges(events),
            "A perfect-read dodge must evade without rolling its 10% chance.");
        assertEquals(0, count(events, CombatEvent.Type.DAMAGE_DEALT));
    }

    /** One tick off, the same 10% dodge fails its roll (rng = 0.5). */
    @Test
    void offByOneTickDodgeRollsItsChance() {
        List<CombatEvent> events = resolveRound(WEAK_DODGE, 1, 2);

        assertEquals(0, countDodges(events),
            "No perfect read one tick off — the 10% roll fails (rng 0.5).");
        assertEquals(1, count(events, CombatEvent.Type.DAMAGE_DEALT));
    }

    /** Exact-tick parry adds the perfect-read bonus stagger ticks (resolved directly). */
    @Test
    void exactTickParryGainsBonusStagger() {
        BattleCombatant attacker = new BattleCombatant(new SorcererCharacter(
            "A", "Attacker",
            new CharacterStats.Builder().vitality(300).speed(80).build(),
            null, List.of(ATTACK)));
        BattleCombatant defender = new BattleCombatant(new SorcererCharacter(
            "D", "Defender",
            new CharacterStats.Builder().vitality(300).speed(120).build(),
            null, List.of(PARRY), List.of(), Equipment.base(WeaponType.KATANA)));
        Timeline timeline = new Timeline(10);
        assertNotNull(timeline.placeAt(PARRY, 1, 0));
        timeline.getSegments().forEach(ActionSegment::markFired);
        defender.setTimeline(timeline);

        DamageCalculator.DamageResult exact = DamageCalculator.resolve(
            attacker, defender, ATTACK, ATTACK.getHitComponents().get(0),
            1, new SeededRandomSource(new Random(1)), 1, false, true);
        assertTrue(exact.isParried());
        assertTrue(exact.isPerfectRead());
        assertEquals(5, exact.getParryStaggerTicks(),
            "Authored 3 stagger ticks + 2 perfect-read bonus ticks.");

        // One tick later (still inside the parry window) is an ordinary parry.
        DamageCalculator.DamageResult offByOne = DamageCalculator.resolve(
            attacker, defender, ATTACK, ATTACK.getHitComponents().get(0),
            2, new SeededRandomSource(new Random(1)), 1, false, true);
        assertTrue(offByOne.isParried());
        assertFalse(offByOne.isPerfectRead());
        assertEquals(3, offByOne.getParryStaggerTicks(),
            "Off the exact tick, only the authored stagger applies.");
    }

    /**
     * Exact-tick parry of a RANGED attack sends it back: through the full
     * resolver, the attacker takes the reflected damage and is not staggered,
     * and the defender takes nothing.
     */
    @Test
    void exactTickParryOfRangedAttackSendsItBack() {
        Resolution r = resolveRoundWithCombatants(RANGED_ATTACK, PARRY, 1, 1);

        assertTrue(r.events().stream().anyMatch(e -> e.getType() == CombatEvent.Type.MOVE_PARRIED),
            "The parry still fires first.");
        assertTrue(r.attacker().getCurrentHp() < r.attacker().getMaxHp(),
            "The reflected ranged attack damages its own user.");
        assertEquals(r.defender().getMaxHp(), r.defender().getCurrentHp(),
            "The defender takes no damage from a parried attack.");
        assertTrue(r.events().stream().anyMatch(e ->
                e.getType() == CombatEvent.Type.DAMAGE_DEALT
                    && e.getTarget() == r.attacker()
                    && e.getMessage() != null && e.getMessage().contains("sent")),
            "The reflection is announced as damage sent back at the attacker.");
        assertFalse(r.events().stream().anyMatch(e ->
                e.getType() == CombatEvent.Type.STATUS_APPLIED
                    && e.getMessage() != null && e.getMessage().contains("staggered")),
            "A parried ranged attack never staggers its user.");
    }

    // -------------------------------------------------------------------------

    private static List<CombatEvent> resolveRound(Move defense, int defenseStart, int attackStart) {
        return resolveRoundWithCombatants(defense, defenseStart, attackStart).events();
    }

    private static Resolution resolveRoundWithCombatants(
        Move defense, int defenseStart, int attackStart
    ) {
        return resolveRoundWithCombatants(ATTACK, defense, defenseStart, attackStart, false);
    }

    private static Resolution resolveRoundWithCombatants(
        Move attack, Move defense, int defenseStart, int attackStart
    ) {
        return resolveRoundWithCombatants(attack, defense, defenseStart, attackStart, false);
    }

    private static Resolution resolveRoundWithCombatants(
        Move attack, Move defense, int defenseStart, int attackStart, boolean defenseReinforced
    ) {
        // Faster defender so an exact-tick defence wins the same-tick ordering
        // and has fired before the attack resolves.
        BattleCombatant attacker = new BattleCombatant(new SorcererCharacter(
            "A", "Attacker",
            new CharacterStats.Builder().vitality(300).speed(80).build(),
            null, List.of(attack)));
        // A parry requires its wielder to be armed.
        BattleCombatant defender = new BattleCombatant(new SorcererCharacter(
            "D", "Defender",
            new CharacterStats.Builder().vitality(300).speed(120).build(),
            null, defense == null ? List.of() : List.of(defense), List.of(),
            defense != null && defense.isParry()
                ? Equipment.base(WeaponType.KATANA) : Equipment.NONE));

        Timeline attackerTimeline = new Timeline(10);
        assertNotNull(attackerTimeline.placeAt(attack, attackStart, 0));
        Timeline defenderTimeline = new Timeline(10);
        if (defense != null) {
            BattlePlan defenderPlan = new BattlePlan(100, 100);
            assertNotNull(defenderPlan.placeWithTargets(
                defense, defenseStart, 0, List.of(), defenseReinforced, 0));
            defenderTimeline = defenderPlan.toLegacyTimeline();
        }
        attacker.setTimeline(attackerTimeline);
        defender.setTimeline(defenderTimeline);

        BattleState state = new BattleState(attacker, defender);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(new FixedRandom()).resolveRound(state);
        return new Resolution(attacker, defender, events);
    }

    private static long count(List<CombatEvent> events, CombatEvent.Type type) {
        return events.stream().filter(e -> e.getType() == type).count();
    }

    /** Primary dodge outcomes — excludes the separate PERFECT READ announcement event. */
    private static long countDodges(List<CombatEvent> events) {
        return events.stream()
            .filter(e -> e.getType() == CombatEvent.Type.MOVE_DODGED)
            .filter(e -> e.getMessage() != null && e.getMessage().contains("dodged"))
            .count();
    }

    private record Resolution(BattleCombatant attacker, BattleCombatant defender,
                              List<CombatEvent> events) {}

    /** Deterministic mid-range RNG: dodge/damage rolls land on their ordinary outcomes. */
    private static final class FixedRandom extends Random {
        @Override public double nextDouble() { return 0.5; }
    }
}
