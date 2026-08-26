package com.jjktbf;

import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.DamageCalculator;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.BlockStyle;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveData;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementalStatusTest {

    @Test
    void elementalTagsRoundTripOnIndividualHitComponents() {
        Move move = attack("ELEMENTS", Set.of(
            MoveTag.PHYSICAL, MoveTag.MELEE, MoveTag.ICE, MoveTag.ELECTRIC, MoveTag.FIRE));

        MoveData data = MoveData.fromMove(move);
        Move restored = data.toMove();

        assertEquals(move.getHitComponents().get(0).getTags(),
            restored.getHitComponents().get(0).getTags());
        assertTrue(restored.hasTag(MoveTag.ICE.name()));
        assertTrue(restored.hasTag(MoveTag.ELECTRIC.name()));
        assertTrue(restored.hasTag(MoveTag.FIRE.name()));
    }

    @Test
    void wetSlowsSpeedAndFrozenHalvesDefense() {
        BattleCombatant combatant = combatant("TARGET", 100, 100, List.of());
        int normalDefense = combatant.computeCurrentDefense(1);

        combatant.addStatusEffect(new StatusEffect(StatusEffectType.WET, 1, 0.0));
        combatant.addStatusEffect(new StatusEffect(StatusEffectType.FROZEN, -1, 0.0));

        assertEquals(80, combatant.getEffectiveStats().getSpeed());
        assertEquals((int) Math.round(normalDefense * 0.5), combatant.computeCurrentDefense(1));
    }

    @Test
    void wetDoublesElectricDamageAndBurnedHalvesMeleeDamage() {
        Move electric = attack("ELECTRIC", Set.of(MoveTag.PHYSICAL, MoveTag.ELECTRIC));
        Move melee = attack("MELEE", Set.of(MoveTag.PHYSICAL, MoveTag.MELEE));
        BattleCombatant attacker = combatant("ATTACKER", 100, 100, List.of(electric, melee));
        BattleCombatant defender = combatant("DEFENDER", 100, 100, List.of());

        int normalElectric = damage(attacker, defender, electric);
        defender.addStatusEffect(new StatusEffect(StatusEffectType.WET, 1, 0.0));
        int wetElectric = damage(attacker, defender, electric);
        assertTrue(Math.abs(wetElectric - normalElectric * 2) <= 1);

        int normalMelee = damage(attacker, defender, melee);
        attacker.addStatusEffect(new StatusEffect(StatusEffectType.BURNED, 1, 0.0));
        int burnedMelee = damage(attacker, defender, melee);
        assertTrue(Math.abs(burnedMelee * 2 - normalMelee) <= 1);
    }

    @Test
    void electricHitHasTenPercentChanceToStunCurrentAction() {
        Move electric = attack("ELECTRIC", Set.of(MoveTag.PHYSICAL, MoveTag.ELECTRIC));
        Move response = utility("RESPONSE", 1, 1);
        BattleCombatant attacker = combatant("ATTACKER", 120, 100, List.of(electric));
        BattleCombatant defender = combatant("DEFENDER", 80, 100, List.of(response));
        Timeline attackerTimeline = new Timeline(10);
        Timeline defenderTimeline = new Timeline(10);
        attackerTimeline.placeAt(electric, 1, 0);
        ActionSegment responseSegment = defenderTimeline.placeAt(response, 1, 0);
        BattleState state = resolvingState(attacker, defender, attackerTimeline, defenderTimeline);

        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.5, 0.0))
            .resolveRound(state);

        assertTrue(responseSegment.isStunned());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.MOVE_STUNNED
                && event.getMessage().contains("electrically stunned")));
    }

    @Test
    void wetGuaranteesIceFreezeAndIceCanCureBurned() {
        Move ice = attack("ICE", Set.of(MoveTag.PHYSICAL, MoveTag.ICE));
        BattleCombatant attacker = combatant("ATTACKER", 120, 100, List.of(ice));
        BattleCombatant defender = combatant("DEFENDER", 80, 100, List.of());
        defender.addStatusEffect(new StatusEffect(StatusEffectType.WET, 1, 0.0));
        defender.addStatusEffect(new StatusEffect(StatusEffectType.BURNED, 1, 0.0));
        Timeline attackerTimeline = new Timeline(10);
        attackerTimeline.placeAt(ice, 1, 0);
        BattleState state = resolvingState(attacker, defender, attackerTimeline, new Timeline(10));

        new CombatResolver(new SequenceRandom(0.5, 0.0)).resolveRound(state);

        assertTrue(defender.hasEffect(StatusEffectType.FROZEN));
        assertFalse(defender.hasEffect(StatusEffectType.BURNED));
    }

    @Test
    void blockedIceHitStillFreezesWetTarget() {
        Move ice = attack("ICE", Set.of(MoveTag.PHYSICAL, MoveTag.ICE));
        Move block = fullBlock();
        BattleCombatant attacker = combatant("ATTACKER", 80, 100, List.of(ice));
        BattleCombatant defender = combatant("DEFENDER", 120, 100, List.of(block));
        defender.addStatusEffect(new StatusEffect(StatusEffectType.WET, 1, 0.0));
        Timeline attackerTimeline = new Timeline(10);
        Timeline defenderTimeline = new Timeline(10);
        attackerTimeline.placeAt(ice, 1, 0);
        defenderTimeline.placeAt(block, 1, 0);

        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.9))
            .resolveRound(resolvingState(
                attacker, defender, attackerTimeline, defenderTimeline));

        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.MOVE_BLOCKED));
        assertTrue(defender.hasEffect(StatusEffectType.FROZEN));
    }

    @Test
    void ordinaryIceHitHasFivePercentChanceToFreeze() {
        Move ice = attack("ICE", Set.of(MoveTag.PHYSICAL, MoveTag.ICE));
        BattleCombatant attacker = combatant("ATTACKER", 120, 100, List.of(ice));
        BattleCombatant defender = combatant("DEFENDER", 80, 100, List.of());
        Timeline attackerTimeline = new Timeline(10);
        attackerTimeline.placeAt(ice, 1, 0);
        BattleState state = resolvingState(attacker, defender, attackerTimeline, new Timeline(10));

        new CombatResolver(new SequenceRandom(0.5, 0.0)).resolveRound(state);

        assertTrue(defender.hasEffect(StatusEffectType.FROZEN));
    }

    @Test
    void frozenPreventsNonFireMovesButUsingFireThawsTheUser() {
        Move ordinary = utility("ORDINARY", 1, 1);
        BattleCombatant frozen = combatant("FROZEN", 100, 100, List.of(ordinary));
        BattleCombatant enemy = combatant("ENEMY", 100, 100, List.of());
        frozen.addStatusEffect(new StatusEffect(StatusEffectType.FROZEN, -1, 0.0));
        Timeline ordinaryTimeline = new Timeline(10);
        ActionSegment ordinarySegment = ordinaryTimeline.placeAt(ordinary, 1, 0);

        List<CombatEvent> blocked = new CombatResolver(new SequenceRandom(0.9))
            .resolveRound(resolvingState(frozen, enemy, ordinaryTimeline, new Timeline(10)));

        assertTrue(ordinarySegment.isStunned());
        assertFalse(blocked.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.MOVE_FIRED && event.getMove() == ordinary));

        Move fire = attack("FIRE", Set.of(MoveTag.PHYSICAL, MoveTag.FIRE));
        BattleCombatant fireUser = combatant("FIRE_USER", 120, 100, List.of(fire));
        BattleCombatant fireTarget = combatant("FIRE_TARGET", 80, 100, List.of());
        fireUser.addStatusEffect(new StatusEffect(StatusEffectType.FROZEN, -1, 0.0));
        Timeline fireTimeline = new Timeline(10);
        fireTimeline.placeAt(fire, 1, 0);

        List<CombatEvent> fired = new CombatResolver(new SequenceRandom(0.9, 0.5))
            .resolveRound(resolvingState(fireUser, fireTarget, fireTimeline, new Timeline(10)));

        assertFalse(fireUser.hasEffect(StatusEffectType.FROZEN));
        assertTrue(fired.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.MOVE_FIRED && event.getMove() == fire));
    }

    @Test
    void canceledFireActionDoesNotThawTheUser() {
        Move fire = attack("FIRE", Set.of(MoveTag.PHYSICAL, MoveTag.FIRE));
        BattleCombatant fireUser = combatant("FIRE_USER", 120, 100, List.of());
        BattleCombatant enemy = combatant("ENEMY", 80, 100, List.of());
        fireUser.addStatusEffect(new StatusEffect(StatusEffectType.FROZEN, -1, 0.0));
        fireUser.addRuntimeAbilityEffect(AbilityEffectType.CANCEL_NEXT_MOVE.createDefault());
        Timeline timeline = new Timeline(10);
        timeline.placeAt(fire, 1, 0);

        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.9))
            .resolveRound(resolvingState(fireUser, enemy, timeline, new Timeline(10)));

        assertTrue(fireUser.hasEffect(StatusEffectType.FROZEN));
        assertFalse(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.MOVE_FIRED && event.getMove() == fire));
    }

    @Test
    void frozenAlwaysUsesItsTenPercentBreakoutChance() {
        Move wait = utility("WAIT", 1, 1);
        BattleCombatant frozen = combatant("FROZEN", 100, 100, List.of(wait));
        BattleCombatant enemy = combatant("ENEMY", 100, 100, List.of());
        frozen.addStatusEffect(new StatusEffect(
            StatusEffectType.FROZEN, -1, 0, 0.0, 0.0));
        Timeline timeline = new Timeline(10);
        timeline.placeAt(wait, 1, 0);

        new CombatResolver(new SequenceRandom(0.05)).resolveRound(
            resolvingState(frozen, enemy, timeline, new Timeline(10)));

        assertFalse(frozen.hasEffect(StatusEffectType.FROZEN));
    }

    @Test
    void fireHitThawsFrozenTarget() {
        Move fire = attack("FIRE", Set.of(MoveTag.PHYSICAL, MoveTag.FIRE));
        BattleCombatant attacker = combatant("ATTACKER", 120, 100, List.of(fire));
        BattleCombatant defender = combatant("DEFENDER", 80, 100, List.of());
        defender.addStatusEffect(new StatusEffect(StatusEffectType.FROZEN, -1, 0.0));
        Timeline attackerTimeline = new Timeline(10);
        attackerTimeline.placeAt(fire, 1, 0);

        new CombatResolver(new SequenceRandom(0.9, 0.5)).resolveRound(
            resolvingState(attacker, defender, attackerTimeline, new Timeline(10)));

        assertFalse(defender.hasEffect(StatusEffectType.FROZEN));
    }

    @Test
    void burnedAccruesPointZeroThreePercentMaxHpPerTickWithoutRoundingUp() {
        Move wait = utility("WAIT", 10, 10);
        BattleCombatant burned = combatant("BURNED", 100, 300, List.of(wait));
        BattleCombatant enemy = combatant("ENEMY", 100, 100, List.of());
        burned.addStatusEffect(new StatusEffect(StatusEffectType.BURNED, 1, 0.0));
        Timeline timeline = new Timeline(10);
        timeline.placeAt(wait, 1, 0);
        int hpBefore = burned.getCurrentHp();
        int expected = (int) Math.floor(burned.getMaxHp() * 0.0003 * 10);

        new CombatResolver(new SequenceRandom(0.9)).resolveRound(
            resolvingState(burned, enemy, timeline, new Timeline(10)));

        assertEquals(expected, hpBefore - burned.getCurrentHp());
        assertTrue(expected > 0);
    }

    @Test
    void refreshingBurnedPreservesFractionalDamageProgress() {
        BattleCombatant burned = combatant("BURNED", 100, 100, List.of());
        burned.addStatusEffect(new StatusEffect(StatusEffectType.BURNED, 1, 0.0));

        assertEquals(0, burned.accrueBurnedDamageForTick(0.4 / burned.getMaxHp()));
        burned.addStatusEffect(new StatusEffect(StatusEffectType.BURNED, 1, 0.0));

        assertEquals(1, burned.accrueBurnedDamageForTick(0.7 / burned.getMaxHp()));
    }

    @Test
    void fatiguedAddsTwoTicksToMoveCostAndFiringPoint() {
        Move move = utility("MOVE", 10, 1);
        BattleCombatant combatant = combatant("USER", 100, 100, List.of(move));
        combatant.addStatusEffect(new StatusEffect(StatusEffectType.FATIGUED, 1, 0.0));
        BattlePlan plan = BattlePlan.forCombatant(combatant, 30);

        ActionSegment segment = plan.place(move, 1, 0);

        assertNotNull(segment);
        assertEquals(12, plan.totalApUsed());
        assertEquals(12, segment.getApCost());
        assertEquals(12, segment.getEndTick());
        assertEquals(3, segment.getUnleashPoint());
        assertEquals(3, segment.getFireTick());
        assertEquals(10, move.getApCost());
        assertEquals(1, move.getUnleashPoint());
    }

    private static int damage(BattleCombatant attacker, BattleCombatant defender, Move move) {
        return DamageCalculator.resolve(
            attacker, defender, move, move.getHitComponents().get(0),
            1, new SequenceRandom(0.5), 1).getFinalDamage();
    }

    private static Move attack(String id, Set<MoveTag> hitTags) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK))
            .hitComponents(List.of(new HitComponent(40, hitTags, 0, false, true)))
            .neverMiss(true)
            .apCost(1)
            .unleashPoint(1)
            .build();
    }

    private static Move utility(String id, int apCost, int unleashPoint) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY))
            .apCost(apCost)
            .unleashPoint(unleashPoint)
            .build();
    }

    private static Move fullBlock() {
        return new Move.Builder("FULL_BLOCK")
            .name("Full Block")
            .category(MoveCategory.DEFENSIVE)
            .defenseType(DefenseType.BLOCK)
            .blockStyle(BlockStyle.PERCENTAGE)
            .blockDamageReduction(100)
            .apCost(1)
            .unleashPoint(1)
            .build();
    }

    private static BattleCombatant combatant(
        String id,
        int speed,
        int vitality,
        List<Move> moves
    ) {
        CharacterStats stats = new CharacterStats.Builder()
            .vitality(vitality).strength(100).durability(100).speed(speed)
            .cursedEnergyReserves(100).cursedEnergyEfficiency(100)
            .cursedEnergyOutput(100).jujutsuSkill(100)
            .combatAbility(100).cursedTechniqueMastery(100)
            .build();
        return new BattleCombatant(new SorcererCharacter(id, id, stats, null, moves));
    }

    private static BattleState resolvingState(
        BattleCombatant first,
        BattleCombatant second,
        Timeline firstTimeline,
        Timeline secondTimeline
    ) {
        first.setTimeline(firstTimeline);
        second.setTimeline(secondTimeline);
        BattleState state = new BattleState(first, second);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        return state;
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
