package com.jjktbf;

import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.move.StatusEffectType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HanamiMechanicsTest {

    @Test
    void cursedEnergyParasiteUsesPaidMoveCostTiersBeforeFire() {
        assertParasiteDamage(10, 0.02);
        assertParasiteDamage(25, 0.04);
        assertParasiteDamage(45, 0.06);
    }

    @Test
    void resourceScaledPowerConsumesAllChargesAndSnapshotsSixtyPerCharge() {
        for (int charges = 1; charges <= 3; charges++) {
            Move beam = resourceScaledBeam();
            BattleCombatant user = combatant("USER", beam);
            BattleCombatant enemy = combatant("ENEMY", null);
            user.defineBoundedResource("FLOWER_OFFERING", "Flower Offering", 3, charges);
            BattleState state = new BattleState(user, enemy);

            Timeline timeline = new Timeline(6);
            ActionSegment segment = timeline.placeAt(beam, 1, 0);
            user.setTimeline(timeline);
            enemy.setTimeline(new Timeline(6));
            state.transitionTo(BattleState.Phase.RESOLUTION);

            new CombatResolver(new ZeroRandom()).resolveRound(state);

            assertEquals(0, user.boundedResourceValue("FLOWER_OFFERING").orElseThrow());
            assertEquals(charges, segment.getExecutionBasePowerMultiplier());
            assertEquals(charges * 60,
                (int) Math.round(beam.getTotalBasePower()
                    * segment.getExecutionBasePowerMultiplier()));
        }
    }

    @Test
    void interruptedBeamStillLosesCapturedOffering() {
        Move beam = resourceScaledBeam();
        BattleCombatant user = combatant("USER", beam);
        BattleCombatant enemy = combatant("ENEMY", null);
        user.defineBoundedResource("FLOWER_OFFERING", "Flower Offering", 3, 3);
        BattleState state = new BattleState(user, enemy);
        Timeline timeline = new Timeline(6);
        ActionSegment segment = timeline.placeAt(beam, 1, 0);
        user.setTimeline(timeline);
        enemy.setTimeline(new Timeline(6));
        state.transitionTo(BattleState.Phase.RESOLUTION);
        CombatResolver resolver = new CombatResolver(new ZeroRandom());

        resolver.beginResolution(state);
        resolver.resolveTick(state);
        assertEquals(0, user.boundedResourceValue("FLOWER_OFFERING").orElseThrow());
        assertEquals(3, segment.getExecutionBasePowerMultiplier());

        segment.stun();
        List<CombatEvent> remainingEvents = resolver.resolveTick(state);
        assertTrue(remainingEvents.stream().noneMatch(event ->
            event.getType() == CombatEvent.Type.MOVE_FIRED && event.getMove() == beam));
        assertEquals(0, user.boundedResourceValue("FLOWER_OFFERING").orElseThrow());
    }

    @Test
    void parasiteRefreshesOneEntryAndUpdatesItsSource() {
        BattleCombatant holder = combatant("HOLDER", null);
        BattleCombatant firstSource = combatant("FIRST", null);
        BattleCombatant secondSource = combatant("SECOND", null);
        holder.addStatusEffect(
            new StatusEffect(StatusEffectType.CURSED_ENERGY_PARASITE, 1, 0.0), firstSource);

        holder.addStatusEffect(
            new StatusEffect(StatusEffectType.CURSED_ENERGY_PARASITE, 1, 0.0), secondSource);

        assertEquals(1, holder.getActiveEffects().stream()
            .filter(status -> status.getType() == StatusEffectType.CURSED_ENERGY_PARASITE)
            .count());
        assertSame(secondSource,
            holder.statusSource(StatusEffectType.CURSED_ENERGY_PARASITE).orElseThrow());
    }

    @Test
    void offeringGenerationOccursOnFireRatherThanMoveStart() {
        Move absorption = offeringGenerator();
        BattleCombatant user = combatant("USER", absorption);
        BattleCombatant enemy = combatant("ENEMY", null);
        user.defineBoundedResource("FLOWER_OFFERING", "Flower Offering", 3, 0);
        BattleState state = new BattleState(user, enemy);
        Timeline timeline = new Timeline(6);
        timeline.placeAt(absorption, 1, 0);
        user.setTimeline(timeline);
        enemy.setTimeline(new Timeline(6));
        state.transitionTo(BattleState.Phase.RESOLUTION);
        CombatResolver resolver = new CombatResolver(new ZeroRandom());

        resolver.beginResolution(state);
        resolver.resolveTick(state);
        assertEquals(0, user.boundedResourceValue("FLOWER_OFFERING").orElseThrow());

        List<CombatEvent> fireEvents = resolver.resolveTick(state);
        assertEquals(1, user.boundedResourceValue("FLOWER_OFFERING").orElseThrow());
        assertTrue(fireEvents.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.MOVE_FIRED));
    }

    private static void assertParasiteDamage(int paidCe, double maxHpFraction) {
        Move move = paidUtility(paidCe);
        BattleCombatant holder = combatant("HOLDER", move);
        BattleCombatant source = combatant("SOURCE", null);
        BattleState state = new BattleState(holder, source);
        holder.addStatusEffect(
            new StatusEffect(StatusEffectType.CURSED_ENERGY_PARASITE, 1, 0.0), source);
        int maxHp = holder.getMaxHp();

        Timeline holderTimeline = new Timeline(6);
        holderTimeline.placeAt(move, 1, paidCe);
        holder.setTimeline(holderTimeline);
        source.setTimeline(new Timeline(6));
        state.transitionTo(BattleState.Phase.RESOLUTION);

        List<CombatEvent> events = new CombatResolver(new ZeroRandom()).resolveRound(state);

        assertEquals((int) Math.round(maxHp * maxHpFraction),
            maxHp - holder.getCurrentHp());
        CombatEvent parasite = events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.DAMAGE_DEALT)
            .filter(event -> event.getTarget() == holder && event.getMove() == null)
            .findFirst().orElseThrow();
        assertSame(source, parasite.getSource());
        assertTrue(indexOf(events, parasite) < indexOf(events, CombatEvent.Type.MOVE_FIRED));
    }

    private static Move paidUtility(int cost) {
        return new Move.Builder("PAY-" + cost)
            .name("Paid utility")
            .category(MoveCategory.UTILITY)
            .apCost(3)
            .unleashPoint(3)
            .baseCeCost(cost)
            .hasCeCost(true)
            .build();
    }

    private static Move resourceScaledBeam() {
        MoveEffectData effect = AbilityEffectType.CONSUME_BOUNDED_RESOURCE_FOR_BASE_POWER
            .createDefaultMoveEffect();
        effect.effectId = "effect-000000";
        effect.sourceResourceKey = "FLOWER_OFFERING";
        effect.trigger = MoveEffectTrigger.ON_START.name();
        return new Move.Builder("BEAM")
            .name("Life-Force Beam")
            .category(MoveCategory.CURSED_ENERGY)
            .basePower(60)
            .neverMiss(true)
            .apCost(2)
            .unleashPoint(2)
            .effects(List.of(effect))
            .build();
    }

    private static Move offeringGenerator() {
        MoveEffectData effect = AbilityEffectType.TRANSACT_BOUNDED_RESOURCE
            .createDefaultMoveEffect();
        effect.effectId = "effect-000000";
        effect.sourceResourceKey = null;
        effect.sourceResourceAmount = 0;
        effect.targetResourceKey = "FLOWER_OFFERING";
        effect.targetResourceAmount = 1;
        effect.trigger = MoveEffectTrigger.ON_FIRE.name();
        return new Move.Builder("ABSORB")
            .name("Life Force Absorption")
            .category(MoveCategory.UTILITY)
            .apCost(2)
            .unleashPoint(2)
            .effects(List.of(effect))
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

    private static int indexOf(List<CombatEvent> events, CombatEvent event) {
        return events.indexOf(event);
    }

    private static int indexOf(List<CombatEvent> events, CombatEvent.Type type) {
        for (int index = 0; index < events.size(); index++) {
            if (events.get(index).getType() == type) return index;
        }
        return -1;
    }

    private static final class ZeroRandom implements RandomSource {
        @Override public int nextInt(int bound) { return 0; }
        @Override public double nextDouble() { return 0.0; }
        @Override public boolean nextBoolean() { return false; }
    }
}
