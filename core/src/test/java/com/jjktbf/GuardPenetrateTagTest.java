package com.jjktbf;

import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.DamageCalculator;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.BlockStyle;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveTag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuardPenetrateTagTest {
    @Test
    void halvesPercentageAndFlatBlockReduction() {
        Move plain = attack();
        Move penetrate = attack(MoveTag.GUARD_PENETRATE);
        for (BlockStyle style : BlockStyle.values()) {
            for (int reduction : style == BlockStyle.FLAT ? List.of(1000, 2000) : List.of(40, 100)) {
                int actual = resolve(penetrate, block(style, reduction), 2, false).getFinalDamage();
                int expected = resolve(plain, block(style, reduction / 2), 2, false).getFinalDamage();
                assertEquals(expected, actual, style + " " + reduction);
                assertTrue(actual > resolve(plain, block(style, reduction), 2, false).getFinalDamage(),
                    style + " " + reduction);
                assertTrue(actual < resolve(plain, null, 2, false).getFinalDamage());
            }
        }
    }

    @Test
    void piercingGrantsPenetrationOnceAndGuardBreakTakesPrecedence() {
        Move piercing = attack(MoveTag.PIERCING);
        assertTrue(piercing.getHitComponents().get(0).hasTag(MoveTag.GUARD_PENETRATE));
        assertTrue(piercing.hasTag(MoveTag.GUARD_PENETRATE.name()));
        Move block = block(BlockStyle.PERCENTAGE, 100);
        int penetrated = resolve(attack(MoveTag.GUARD_PENETRATE), block, 2, false).getFinalDamage();
        assertEquals(penetrated, resolve(piercing, block, 2, false).getFinalDamage());
        assertEquals(penetrated, resolve(attack(MoveTag.PIERCING, MoveTag.GUARD_PENETRATE),
            block, 2, false).getFinalDamage());
        assertEquals(resolve(attack(), null, 2, false).getFinalDamage(),
            resolve(attack(MoveTag.PIERCING, MoveTag.GUARD_BREAK), block, 2, false).getFinalDamage());
    }

    @Test
    void perfectAndForcedFullBlocksBecomeFiftyPercentBlocks() {
        Move penetrate = attack(MoveTag.GUARD_PENETRATE);
        int halfBlocked = resolve(attack(), block(BlockStyle.PERCENTAGE, 50), 2, false).getFinalDamage();
        for (BlockStyle style : BlockStyle.values()) {
            var perfect = resolve(penetrate, block(style, 40), 1, false);
            assertTrue(perfect.isHit());
            assertTrue(perfect.isPerfectRead());
            assertEquals(halfBlocked, perfect.getFinalDamage());
            assertTrue(resolve(attack(), block(style, 40), 1, false).isBlocked());
        }
        assertEquals(halfBlocked, resolve(penetrate, null, 2, true).getFinalDamage());
        assertTrue(resolve(attack(), null, 2, true).isBlocked());
    }

    @Test
    void parryAndDodgeStillNegatePenetratingHits() {
        Move penetrate = attack(MoveTag.GUARD_PENETRATE);
        Move parry = defense(DefenseType.PARRY).parryStaggerTicks(2).build();
        var parried = resolve(penetrate, parry, 2, false);
        assertTrue(parried.isParried());
        assertTrue(parried.staggersAttacker());
        Move dodge = defense(DefenseType.DODGE).dodgeChance(100).dodgeScope("BOTH").build();
        assertTrue(resolve(penetrate, dodge, 2, false).isDodged());
    }

    @Test
    void penetrationRoundTripsPerHitWithoutLeakingToOtherComponents() {
        Move move = new Move.Builder("MIXED")
            .category(MoveCategory.PHYSICAL)
            .hitComponents(List.of(attack(MoveTag.GUARD_PENETRATE).getHitComponents().get(0),
                attack().getHitComponents().get(0)))
            .apCost(10).unleashPoint(1).build();
        MoveData data = MoveData.fromMove(move);
        assertFalse(data.tags.contains(MoveTag.GUARD_PENETRATE.name()));
        Move restored = data.toMove();
        assertTrue(restored.getHitComponents().get(0).isGuardPenetrate());
        assertFalse(restored.getHitComponents().get(1).isGuardPenetrate());
    }

    private static Move attack(MoveTag... extraTags) {
        EnumSet<MoveTag> tags = EnumSet.of(MoveTag.PHYSICAL, MoveTag.MELEE);
        tags.addAll(List.of(extraTags));
        return new Move.Builder("ATTACK").name("Attack").category(MoveCategory.PHYSICAL)
            .hitComponents(List.of(new HitComponent(100, tags, 0, false, true)))
            .neverMiss(true).apCost(10).unleashPoint(1).build();
    }

    private static Move.Builder defense(DefenseType type) {
        return new Move.Builder("DEFENSE").name("Defense").category(MoveCategory.DEFENSIVE)
            .defenseType(type).blockDuration(10).apCost(10).unleashPoint(1);
    }

    private static Move block(BlockStyle style, int reduction) {
        Move.Builder builder = defense(DefenseType.BLOCK).blockStyle(style);
        if (style == BlockStyle.FLAT) builder.blockFlatReduction(reduction);
        else builder.blockDamageReduction(reduction);
        return builder.build();
    }

    private static DamageCalculator.DamageResult resolve(
        Move attack, Move defense, int tick, boolean forced
    ) {
        BattleCombatant attacker = fighter("A");
        BattleCombatant defender = fighter("D");
        if (defense != null) {
            Timeline timeline = new Timeline(30);
            assertNotNull(timeline.placeAt(defense, 1, 0));
            defender.setTimeline(timeline);
        }
        return DamageCalculator.resolve(attacker, defender, attack,
            attack.getHitComponents().get(0), tick, new SeededRandomSource(42), 1, forced);
    }

    private static BattleCombatant fighter(String id) {
        CharacterStats stats = new CharacterStats.Builder().build();
        return new BattleCombatant(new SorcererCharacter(id, id, stats, null, List.of()));
    }
}
