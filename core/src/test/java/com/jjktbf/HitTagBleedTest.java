package com.jjktbf;

import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.BlockStyle;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.HitComponent;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitTagBleedTest {

    @Test
    void eachBleedTagUsesItsOwnThreshold() {
        assertBleedThreshold(MoveTag.PIERCING, 0.0999, true);
        assertBleedThreshold(MoveTag.PIERCING, 0.10, false);
        assertBleedThreshold(MoveTag.SLASHING, 0.2999, true);
        assertBleedThreshold(MoveTag.SLASHING, 0.30, false);
    }

    @Test
    void electricCanStunButNeverAppliesBleed() {
        Move electric = attack("ELECTRIC", Set.of(
            MoveTag.PHYSICAL, MoveTag.MELEE, MoveTag.ELECTRIC));
        Move response = new Move.Builder("RESPONSE")
            .name("Response")
            .category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY))
            .apCost(1)
            .unleashPoint(1)
            .build();

        Scenario stun = resolveWithResponse(
            electric, response, new SequenceRandom(0.5, 0.0, 0.0));
        assertTrue(stun.responseSegment().isStunned());
        assertEquals(0, woundCount(stun.defender()));

        Scenario noStun = resolveWithResponse(
            electric, response, new SequenceRandom(0.5, 0.9, 0.0));
        assertFalse(noStun.responseSegment().isStunned());
        assertEquals(0, woundCount(noStun.defender()));
    }

    @Test
    void matchingTagsStackPerHitAndRetainSourceDurationAndComponentIndex() {
        HitComponent allTags = new HitComponent(1, Set.of(
            MoveTag.PHYSICAL, MoveTag.MELEE, MoveTag.ELECTRIC,
            MoveTag.PIERCING, MoveTag.SLASHING), 0, false, true);
        HitComponent slashing = new HitComponent(1, Set.of(
            MoveTag.PHYSICAL, MoveTag.MELEE, MoveTag.SLASHING), 0, false, true);
        Move move = new Move.Builder("MULTI_TAG_HITS")
            .name("Multi-tag hits")
            .category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK))
            .hitComponents(List.of(allTags, slashing))
            .neverMiss(true)
            .apCost(1)
            .unleashPoint(1)
            .build();

        // Component 0: damage, stun (fails), piercing/slashing bleed.
        // Component 1: damage, slashing bleed.
        Scenario scenario = resolve(move, null, 80, 120,
            new SequenceRandom(0.5, 0.9, 0.0, 0.0, 0.5, 0.0));
        List<StatusEffect> wounds = wounds(scenario.defender());
        List<CombatEvent> applied = bleedEvents(scenario.events());

        assertEquals(60, StatusEffectType.BLEED.defaultDurationTicks());
        assertEquals(3, wounds.size());
        assertEquals(List.of(59, 59, 59),
            wounds.stream().map(StatusEffect::getDurationTicks).toList());
        assertEquals(List.of(0, 0, 1),
            applied.stream().map(CombatEvent::getComponentIndex).toList());
        assertEquals(3, applied.size());
        for (StatusEffect wound : wounds) {
            assertSame(scenario.attacker(), scenario.defender()
                .statusSource(wound).orElseThrow());
        }
        for (CombatEvent event : applied) {
            assertSame(scenario.attacker(), event.getSource());
            assertSame(scenario.defender(), event.getTarget());
            assertSame(move, event.getMove());
        }
    }

    @Test
    void blockedHitCanOpenAWound() {
        Move slashing = attack("BLOCKED_SLASH", Set.of(
            MoveTag.PHYSICAL, MoveTag.MELEE, MoveTag.SLASHING));
        Move block = new Move.Builder("FULL_BLOCK")
            .name("Full block")
            .category(MoveCategory.DEFENSIVE)
            .tags(Set.of(MoveTag.UTILITY))
            .defenseType(DefenseType.BLOCK)
            .blockStyle(BlockStyle.PERCENTAGE)
            .blockDamageReduction(100)
            .blockDuration(1)
            .apCost(1)
            .unleashPoint(1)
            .build();

        Scenario scenario = resolve(slashing, block, 80, 120,
            new SequenceRandom(0.0));

        assertTrue(scenario.events().stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.MOVE_BLOCKED));
        assertEquals(1, woundCount(scenario.defender()));
    }

    @Test
    void parryDodgeAndNaturalMissDoNotOpenWounds() {
        Move slashing = attack("DEFENDED_SLASH", Set.of(
            MoveTag.PHYSICAL, MoveTag.MELEE, MoveTag.SLASHING));
        Move dodge = new Move.Builder("DODGE")
            .name("Dodge")
            .category(MoveCategory.DEFENSIVE)
            .tags(Set.of(MoveTag.UTILITY))
            .defenseType(DefenseType.DODGE)
            .dodgeChance(100)
            .dodgeScope("BOTH")
            .apCost(1)
            .unleashPoint(1)
            .build();
        Move parry = new Move.Builder("PARRY")
            .name("Parry")
            .category(MoveCategory.DEFENSIVE)
            .tags(Set.of(MoveTag.UTILITY))
            .defenseType(DefenseType.PARRY)
            .apCost(1)
            .unleashPoint(1)
            .build();

        Scenario dodged = resolve(slashing, dodge, 80, 120,
            new SequenceRandom(0.0));
        Scenario parried = resolve(slashing, parry, 80, 120,
            new SequenceRandom(0.0));
        Move miss = attack("NATURAL_MISS", Set.of(
            MoveTag.PHYSICAL, MoveTag.MELEE, MoveTag.SLASHING), false);
        Scenario missed = resolve(miss, null, 80, 120,
            new SequenceRandom(0.0));

        assertTrue(hasOutcome(dodged.events(), CombatEvent.Type.MOVE_DODGED));
        assertTrue(hasOutcome(parried.events(), CombatEvent.Type.MOVE_PARRIED));
        assertTrue(hasOutcome(missed.events(), CombatEvent.Type.MOVE_MISSED));
        assertEquals(0, woundCount(dodged.defender()));
        assertEquals(0, woundCount(parried.defender()));
        assertEquals(0, woundCount(missed.defender()));
    }

    private static void assertBleedThreshold(
        MoveTag tag, double roll, boolean expected
    ) {
        Move move = attack(tag.name(), Set.of(MoveTag.PHYSICAL, MoveTag.MELEE, tag));
        Scenario scenario = resolve(move, null, 80, 120, new SequenceRandom(0.5, roll));
        assertEquals(expected ? 1 : 0, woundCount(scenario.defender()),
            tag + " roll " + roll);
    }

    private static Move attack(String id, Set<MoveTag> tags) {
        return attack(id, tags, true);
    }

    private static Move attack(String id, Set<MoveTag> tags, boolean neverMiss) {
        Move.Builder builder = new Move.Builder(id)
            .name(id)
            .category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK))
            .hitComponents(List.of(new HitComponent(1, tags, 0, false, true)))
            .neverMiss(neverMiss)
            .apCost(1)
            .unleashPoint(1);
        if (!neverMiss) builder.baseAccuracy(0.0);
        return builder.build();
    }

    private static Scenario resolve(
        Move attack, Move defense, int attackerSpeed, int defenderSpeed,
        RandomSource random
    ) {
        BattleCombatant attacker = fighter("ATTACKER", attackerSpeed, List.of(attack));
        BattleCombatant defender = fighter("DEFENDER", defenderSpeed,
            defense == null ? List.of() : List.of(defense));
        Timeline attackerTimeline = new Timeline(20);
        assertTrue(attackerTimeline.placeAt(attack, 1, 0) != null);
        Timeline defenderTimeline = new Timeline(20);
        if (defense != null) assertTrue(defenderTimeline.placeAt(defense, 1, 0) != null);
        BattleState state = resolvingState(
            attacker, defender, attackerTimeline, defenderTimeline);
        CombatResolver resolver = new CombatResolver(random);
        List<CombatEvent> events = new java.util.ArrayList<>(resolver.beginResolution(state));
        events.addAll(resolver.resolveTick(state));
        return new Scenario(attacker, defender, events, null);
    }

    private static Scenario resolveWithResponse(
        Move attack, Move response, RandomSource random
    ) {
        BattleCombatant attacker = fighter("ATTACKER", 120, List.of(attack));
        BattleCombatant defender = fighter("DEFENDER", 80, List.of(response));
        Timeline attackerTimeline = new Timeline(20);
        assertTrue(attackerTimeline.placeAt(attack, 1, 0) != null);
        Timeline defenderTimeline = new Timeline(20);
        ActionSegment responseSegment = defenderTimeline.placeAt(response, 1, 0);
        assertTrue(responseSegment != null);
        BattleState state = resolvingState(
            attacker, defender, attackerTimeline, defenderTimeline);
        CombatResolver resolver = new CombatResolver(random);
        List<CombatEvent> events = new java.util.ArrayList<>(resolver.beginResolution(state));
        events.addAll(resolver.resolveTick(state));
        return new Scenario(attacker, defender, events, responseSegment);
    }

    private static BattleCombatant fighter(String id, int speed, List<Move> moves) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(1000).strength(100).durability(100).speed(speed)
            .cursedEnergyReserves(100).cursedEnergyEfficiency(100)
            .cursedEnergyOutput(100).jujutsuSkill(100)
            .combatAbility(100).cursedTechniqueMastery(100)
            .build();
        return new BattleCombatant(new SorcererCharacter(id, id, stats, null, moves));
    }

    private static BattleState resolvingState(
        BattleCombatant first, BattleCombatant second,
        Timeline firstTimeline, Timeline secondTimeline
    ) {
        first.setTimeline(firstTimeline);
        second.setTimeline(secondTimeline);
        BattleState state = new BattleState(first, second);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        return state;
    }

    private static List<StatusEffect> wounds(BattleCombatant target) {
        return target.getActiveEffects().stream()
            .filter(effect -> effect.getType() == StatusEffectType.BLEED)
            .toList();
    }

    private static int woundCount(BattleCombatant target) {
        return wounds(target).size();
    }

    private static List<CombatEvent> bleedEvents(List<CombatEvent> events) {
        return events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.STATUS_APPLIED)
            .filter(event -> event.getMessage() != null
                && event.getMessage().contains("bleeding wound"))
            .toList();
    }

    private static boolean hasOutcome(List<CombatEvent> events, CombatEvent.Type type) {
        return events.stream().anyMatch(event -> event.getType() == type);
    }

    private record Scenario(
        BattleCombatant attacker,
        BattleCombatant defender,
        List<CombatEvent> events,
        ActionSegment responseSegment
    ) {
    }

    private static final class SequenceRandom implements RandomSource {
        private final ArrayDeque<Double> values = new ArrayDeque<>();

        private SequenceRandom(double... values) {
            for (double value : values) this.values.add(value);
        }

        @Override public int nextInt(int bound) { return 0; }

        @Override public double nextDouble() {
            return values.isEmpty() ? 0.9 : values.removeFirst();
        }

        @Override public boolean nextBoolean() { return false; }
    }
}
