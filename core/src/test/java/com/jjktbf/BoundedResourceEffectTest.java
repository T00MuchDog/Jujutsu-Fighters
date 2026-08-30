package com.jjktbf;

import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.MoveAvailability;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.progression.TechniqueMasteryProgressionData;
import com.jjktbf.model.progression.TechniqueMasteryProgressions;
import com.jjktbf.model.progression.TechniqueMasteryResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedResourceEffectTest {

    @Test
    void transactionsAreAtomicAndExposePlayerVisibleState() {
        BattleCombatant user = combatant("USER", null);
        user.defineBoundedResource("SUPPLY", "Supply", 3, 3);
        user.defineBoundedResource("CHARGE", "Charge", 1, 0);

        var converted = user.transactBoundedResources("SUPPLY", 1, "CHARGE", 1);
        assertTrue(converted.success());
        assertEquals(2, user.boundedResourceValue("supply").orElseThrow());
        assertEquals(1, user.boundedResourceValue("charge").orElseThrow());
        assertEquals(2, converted.changedStates().size());

        var rejected = user.transactBoundedResources("SUPPLY", 1, "CHARGE", 1);
        assertFalse(rejected.success(), "a full target must not consume the source");
        assertEquals(2, user.boundedResourceValue("SUPPLY").orElseThrow());
        assertEquals(1, user.boundedResourceValue("CHARGE").orElseThrow());
        assertTrue(user.abilityStates().stream().anyMatch(state ->
            state.key().equals("SUPPLY") && state.displayName().equals("Supply")));
    }

    @Test
    void resourceCapacityCanProgressFromTechniqueMastery() {
        MoveEffectData definition = AbilityEffectType.DEFINE_BOUNDED_RESOURCE
            .createDefaultMoveEffect();
        definition.resourceCapacity = 1;
        TechniqueMasteryProgressionData progression = new TechniqueMasteryProgressionData();
        progression.mode = TechniqueMasteryProgressionData.BENCHMARKS;
        progression.benchmarks = List.of(
            new TechniqueMasteryProgressionData.BenchmarkData(0, 1),
            new TechniqueMasteryProgressionData.BenchmarkData(80, 2),
            new TechniqueMasteryProgressionData.BenchmarkData(150, 3));
        definition.masteryProgression = Map.of(
            TechniqueMasteryProgressions.RESOURCE_CAPACITY, progression);

        assertEquals(2, TechniqueMasteryResolver.resolve(definition, 95).resourceCapacity);
        assertEquals(3, TechniqueMasteryResolver.resolve(definition, 200).resourceCapacity);
    }

    @Test
    void passiveDefinitionInitializesAtItsResolvedTechniqueMasteryCapacity() {
        var definition = AbilityEffectType.DEFINE_BOUNDED_RESOURCE.createDefault();
        definition.resourceKey = "CHARGE";
        definition.resourceLabel = "Charge";
        definition.resourceCapacity = 1;
        definition.resourceStartValue = 0;
        TechniqueMasteryProgressionData progression = new TechniqueMasteryProgressionData();
        progression.mode = TechniqueMasteryProgressionData.BENCHMARKS;
        progression.benchmarks = List.of(
            new TechniqueMasteryProgressionData.BenchmarkData(0, 1),
            new TechniqueMasteryProgressionData.BenchmarkData(80, 2));
        definition.masteryProgression = Map.of(
            TechniqueMasteryProgressions.RESOURCE_CAPACITY, progression);
        AbilityData data = new AbilityData();
        data.id = "RESOURCE";
        data.name = "Resource";
        data.category = "PASSIVE";
        data.sourceType = "TECHNIQUE";
        data.effects = List.of(definition);

        BattleCombatant user = new BattleCombatant(
            new SorcererCharacter("USER", "USER", stats(), null, List.of()),
            List.of(new Ability(data)));

        var state = user.abilityState("CHARGE").orElseThrow();
        assertEquals(0, state.currentValue());
        assertEquals(2, state.maximumValue());
    }

    @Test
    void planningReservesGuaranteedMoveStartCostsAcrossPlacements() {
        Move spend = spendingMove("SPEND", "SUPPLY", 1);
        BattleCombatant user = combatant("USER", spend);
        user.defineBoundedResource("SUPPLY", "Supply", 2, 2);

        assertNull(MoveAvailability.restrictionReason(null, user, spend, List.of(spend)));
        assertNotNull(MoveAvailability.restrictionReason(
            null, user, spend, List.of(spend, spend)));
    }

    @Test
    void planningReservesTargetCapacityAcrossResourceConversions() {
        Move convert = convertingMove("CONVERT", "SUPPLY", 1, "CHARGE", 1);
        BattleCombatant user = combatant("USER", convert);
        user.defineBoundedResource("SUPPLY", "Supply", 3, 3);
        user.defineBoundedResource("CHARGE", "Charge", 1, 0);

        assertNull(MoveAvailability.restrictionReason(null, user, convert, List.of()));
        assertNotNull(MoveAvailability.restrictionReason(null, user, convert, List.of(convert)));
        assertEquals(3, user.boundedResourceValue("SUPPLY").orElseThrow(),
            "planning simulation must not mutate live resources");
        assertEquals(0, user.boundedResourceValue("CHARGE").orElseThrow());
    }

    @Test
    void crossBoardResourcePlanningUsesChronologicalOrder() {
        Move convert = convertingMove("CONVERT", "SUPPLY", 1, "CHARGE", 1);
        Move spend = spendingAttackMove("SPEND", "CHARGE", 1);
        BattleCombatant user = combatant("USER", spend);
        user.defineBoundedResource("SUPPLY", "Supply", 1, 1);
        user.defineBoundedResource("CHARGE", "Charge", 1, 0);

        BattlePlan valid = new BattlePlan(user.getMaxApBar(), user.getCurrentCe());
        assertNotNull(valid.place(spend, 5, 0));
        assertNotNull(valid.place(convert, 1, 0));
        List<Move> validOrder = valid.allSegments().stream().map(ActionSegment::getMove).toList();
        assertEquals(List.of(convert, spend), validOrder);
        assertNull(MoveAvailability.boundedResourcePlanRestrictionReason(user, validOrder));

        BattlePlan invalid = new BattlePlan(user.getMaxApBar(), user.getCurrentCe());
        assertNotNull(invalid.place(spend, 1, 0));
        assertNotNull(invalid.place(convert, 5, 0));
        assertNotNull(MoveAvailability.boundedResourcePlanRestrictionReason(
            user, invalid.allSegments().stream().map(ActionSegment::getMove).toList()));
    }

    @Test
    void insufficientCeDoesNotConsumeMoveStartResource() {
        Move spend = spendingMove("SPEND", "CHARGE", 1);
        BattleCombatant user = combatant("USER", spend);
        BattleCombatant enemy = combatant("ENEMY", null);
        user.defineBoundedResource("CHARGE", "Charge", 1, 1);
        user.drainCe(user.getCurrentCe());
        BattleState state = new BattleState(user, enemy);

        BattlePlan plan = new BattlePlan(user.getMaxApBar(), 10);
        ActionSegment segment = plan.place(spend, 1, 10);
        assertNotNull(segment);
        user.setTimeline(plan.toLegacyTimeline());
        enemy.setTimeline(new BattlePlan(enemy.getMaxApBar(), enemy.getCurrentCe()).toLegacyTimeline());
        state.transitionTo(BattleState.Phase.RESOLUTION);

        List<CombatEvent> events = new CombatResolver(new ZeroRandom()).resolveRound(state);

        assertEquals(1, user.boundedResourceValue("CHARGE").orElseThrow());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.CE_DEPLETED));
        assertFalse(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.RESOURCE_CHANGED));
    }

    @Test
    void moveStartConsumesResourceBeforeTheMoveFires() {
        Move spend = spendingMove("SPEND", "CHARGE", 1);
        BattleCombatant user = combatant("USER", spend);
        BattleCombatant enemy = combatant("ENEMY", null);
        user.defineBoundedResource("CHARGE", "Charge", 1, 1);
        BattleState state = new BattleState(user, enemy);

        BattlePlan plan = new BattlePlan(user.getMaxApBar(), user.getCurrentCe());
        ActionSegment segment = plan.place(spend, 1, 0);
        assertNotNull(segment);
        user.setTimeline(plan.toLegacyTimeline());
        enemy.setTimeline(new BattlePlan(enemy.getMaxApBar(), enemy.getCurrentCe()).toLegacyTimeline());
        state.transitionTo(BattleState.Phase.RESOLUTION);

        List<CombatEvent> events = new CombatResolver(new ZeroRandom()).resolveRound(state);

        assertEquals(0, user.boundedResourceValue("CHARGE").orElseThrow());
        int resourceIndex = indexOf(events, CombatEvent.Type.RESOURCE_CHANGED);
        int firedIndex = indexOf(events, CombatEvent.Type.MOVE_FIRED);
        assertTrue(resourceIndex >= 0 && firedIndex > resourceIndex);
    }

    private static int indexOf(List<CombatEvent> events, CombatEvent.Type type) {
        for (int index = 0; index < events.size(); index++) {
            if (events.get(index).getType() == type) return index;
        }
        return -1;
    }

    private static Move spendingMove(String id, String resource, int amount) {
        MoveEffectData effect = AbilityEffectType.TRANSACT_BOUNDED_RESOURCE
            .createDefaultMoveEffect();
        effect.effectId = "effect-000000";
        effect.trigger = MoveEffectTrigger.ON_START.name();
        effect.sourceResourceKey = resource;
        effect.sourceResourceAmount = amount;
        effect.targetResourceKey = null;
        effect.targetResourceAmount = 0;
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY))
            .apCost(1)
            .unleashPoint(1)
            .effects(List.of(effect))
            .build();
    }

    private static Move convertingMove(
        String id,
        String sourceResource,
        int sourceAmount,
        String targetResource,
        int targetAmount
    ) {
        MoveEffectData effect = AbilityEffectType.TRANSACT_BOUNDED_RESOURCE
            .createDefaultMoveEffect();
        effect.effectId = "effect-000000";
        effect.trigger = MoveEffectTrigger.ON_START.name();
        effect.sourceResourceKey = sourceResource;
        effect.sourceResourceAmount = sourceAmount;
        effect.targetResourceKey = targetResource;
        effect.targetResourceAmount = targetAmount;
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY))
            .apCost(1)
            .unleashPoint(1)
            .effects(List.of(effect))
            .build();
    }

    private static Move spendingAttackMove(String id, String resource, int amount) {
        MoveEffectData effect = AbilityEffectType.TRANSACT_BOUNDED_RESOURCE
            .createDefaultMoveEffect();
        effect.effectId = "effect-000000";
        effect.trigger = MoveEffectTrigger.ON_START.name();
        effect.sourceResourceKey = resource;
        effect.sourceResourceAmount = amount;
        effect.targetResourceKey = null;
        effect.targetResourceAmount = 0;
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.ATTACK))
            .basePower(1)
            .apCost(1)
            .unleashPoint(1)
            .effects(List.of(effect))
            .build();
    }

    private static BattleCombatant combatant(String id, Move move) {
        return new BattleCombatant(new SorcererCharacter(
            id, id, stats(), null, move == null ? List.of() : List.of(move)));
    }

    private static CharacterStats stats() {
        return new CharacterStats.Builder()
            .vitality(100).strength(100).durability(100).speed(100)
            .cursedEnergyReserves(100).cursedEnergyEfficiency(100)
            .cursedEnergyOutput(100).jujutsuSkill(100)
            .combatAbility(100).cursedTechniqueMastery(100)
            .build();
    }

    private static final class ZeroRandom implements RandomSource {
        @Override public int nextInt(int bound) { return 0; }
        @Override public double nextDouble() { return 0.0; }
        @Override public boolean nextBoolean() { return false; }
    }
}
