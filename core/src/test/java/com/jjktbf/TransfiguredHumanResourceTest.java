package com.jjktbf;

import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.CursedSpiritCharacter;
import com.jjktbf.model.character.ShikigamiCharacter;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.MoveAvailability;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Transfigured Human bounded resource: defined at start, consumed by the
 * summon and assault rows, locked at zero, and visible through the same
 * ability-state snapshot the multiplayer protocol serializes.
 */
class TransfiguredHumanResourceTest {

    private static final String RESOURCE = "TRANSFIGURED_HUMANS";

    @Test
    void stockpileStartsAtEightAndIsVisibleAsAbilityState() {
        BattleCombatant mahito = mahito();

        assertEquals(8, mahito.boundedResourceValue(RESOURCE).getAsInt());
        Optional<CodedAbilityState> state = mahito.abilityStates().stream()
            .filter(entry -> RESOURCE.equals(entry.key()))
            .findFirst();
        assertTrue(state.isPresent(), "resource must ride the serialized state list");
        assertEquals(8, state.get().currentValue());
        assertEquals(8, state.get().maximumValue());
        assertEquals("Transfigured Humans", state.get().displayName());
    }

    @Test
    void releasingASummonConsumesOneAndMaterializesIt() {
        BattleCombatant mahito = mahitoKnowing(summonMove());
        BattleCombatant enemy = fighter();
        BattleState state = new BattleState(mahito, enemy);
        place(mahito, mahito.getCharacter().getKnownMoves().get(0), List.of(enemy));

        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.5), id ->
                Optional.of(transfiguredHuman()))
            .resolveRound(state);

        assertEquals(7, mahito.boundedResourceValue(RESOURCE).getAsInt());
        assertTrue(state.activeCombatants().stream()
            .anyMatch(combatant -> combatant.isSummon()
                && "TRANSFIGURED".equals(combatant.getCharacter().getName())));
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.RESOURCE_CHANGED));
    }

    @Test
    void transfiguredHumanAssaultConsumesOneWhenItFires() {
        Move assault = assaultMove();
        BattleCombatant mahito = mahitoKnowing(assault);
        BattleCombatant enemy = fighter();
        BattleState state = new BattleState(mahito, enemy);
        place(mahito, assault, List.of(enemy));

        state.transitionTo(BattleState.Phase.RESOLUTION);
        new CombatResolver(new SequenceRandom(0.5)).resolveRound(state);

        assertEquals(7, mahito.boundedResourceValue(RESOURCE).getAsInt());
    }

    @Test
    void resourceMovesAreLockedAtZero() {
        Move summon = summonMove();
        BattleCombatant mahito = mahitoKnowing(summon);
        BattleCombatant enemy = fighter();
        BattleState state = new BattleState(mahito, enemy);

        // Spend the whole stockpile through the same atomic transaction.
        for (int i = 0; i < 8; i++) {
            assertTrue(mahito.transactBoundedResources(RESOURCE, 1, null, 0).success());
        }
        assertEquals(0, mahito.boundedResourceValue(RESOURCE).getAsInt());

        String reason = MoveAvailability.restrictionReason(state, mahito, summon);
        assertNotNull(reason, "the summon must be locked at zero stock");
        assertTrue(reason.contains("TRANSFIGURED_HUMANS"), reason);
    }

    @Test
    void theResourceNeverDropsBelowZero() {
        BattleCombatant mahito = mahito();
        for (int i = 0; i < 12; i++) {
            mahito.transactBoundedResources(RESOURCE, 1, null, 0);
        }
        assertEquals(0, mahito.boundedResourceValue(RESOURCE).getAsInt());
        assertFalse(mahito.transactBoundedResources(RESOURCE, 1, null, 0).success(),
            "an empty stockpile refuses the transaction");
    }

    @Test
    void consumptionIsReflectedInTheSerializedAbilityState() {
        BattleCombatant mahito = mahito();
        mahito.transactBoundedResources(RESOURCE, 1, null, 0);
        mahito.transactBoundedResources(RESOURCE, 1, null, 0);

        Optional<CodedAbilityState> state = mahito.abilityStates().stream()
            .filter(entry -> RESOURCE.equals(entry.key()))
            .findFirst();
        assertTrue(state.isPresent());
        assertEquals(6, state.get().currentValue());
        assertEquals(8, state.get().maximumValue());
    }

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private static Ability stockpileAbility() {
        AbilityData ability = new AbilityData();
        ability.id = "STOCKPILE";
        ability.name = "Transfigured Human Stockpile";
        ability.category = "PASSIVE";
        ability.sourceType = "TECHNIQUE";
        ability.sourceValue = "Idle Transfiguration";
        AbilityEffectData effect =
            AbilityEffectType.DEFINE_BOUNDED_RESOURCE.createDefault();
        effect.effectId = "effect-000000";
        effect.resourceKey = RESOURCE;
        effect.resourceLabel = "Transfigured Humans";
        effect.resourceCapacity = 8;
        effect.resourceStartValue = 8;
        ability.effects = List.of(effect);
        return new Ability(ability);
    }

    private static BattleCombatant mahito() {
        return new BattleCombatant(new CursedSpiritCharacter(
            "MAHITO", "Mahito", stats(), "Idle Transfiguration",
            List.of(), List.of(stockpileAbility())));
    }

    private static BattleCombatant mahitoKnowing(Move move) {
        return new BattleCombatant(new CursedSpiritCharacter(
            "MAHITO", "Mahito", stats(), "Idle Transfiguration",
            List.of(move), List.of(stockpileAbility())));
    }

    private static BattleCombatant fighter() {
        return new BattleCombatant(new com.jjktbf.model.character.SorcererCharacter(
            "ENEMY", "ENEMY", stats(), null, List.of()));
    }

    private static Character transfiguredHuman() {
        return new ShikigamiCharacter(
            "000022", "TRANSFIGURED", stats(), null, List.of(), List.of(),
            com.jjktbf.model.character.Equipment.NONE);
    }

    private static CharacterStats stats() {
        return new CharacterStats.Builder()
            .vitality(80).strength(80).durability(80).speed(80)
            .cursedEnergyReserves(80).cursedEnergyEfficiency(80)
            .cursedEnergyOutput(80).jujutsuSkill(80)
            .combatAbility(80).cursedTechniqueMastery(80)
            .build();
    }

    private static MoveEffectData spendRow() {
        MoveEffectData row =
            AbilityEffectType.TRANSACT_BOUNDED_RESOURCE.createDefaultMoveEffect();
        row.effectId = "effect-000000";
        row.trigger = MoveEffectTrigger.ON_START.name();
        row.target = AbilityEffectTarget.SELF.name();
        row.sourceResourceKey = RESOURCE;
        row.sourceResourceAmount = 1;
        row.targetResourceKey = null;
        row.targetResourceAmount = 0;
        return row;
    }

    private static Move summonMove() {
        MoveEffectData summon = AbilityEffectType.SUMMON_CHARACTER.createDefaultMoveEffect();
        summon.effectId = "effect-000001";
        summon.characterId = "000022";
        summon.trigger = MoveEffectTrigger.ON_FIRE.name();
        summon.target = AbilityEffectTarget.SELF.name();

        return new Move.Builder("RELEASE")
            .name("Release Transfigured Human")
            .category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY, MoveTag.INNATE_TECHNIQUE, MoveTag.CURSED_ENERGY))
            .apCost(1)
            .unleashPoint(1)
            .requiredTechniqueId("Idle Transfiguration")
            .prerequisites(java.util.Map.of("cursedTechniqueMastery", 0))
            .effects(List.of(spendRow(), summon))
            .build();
    }

    private static Move assaultMove() {
        return new Move.Builder("ASSAULT")
            .name("Transfigured Human Assault")
            .category(MoveCategory.INNATE_TECHNIQUE)
            .tags(Set.of(MoveTag.ATTACK, MoveTag.INNATE_TECHNIQUE))
            .basePower(45)
            .neverMiss(true)
            .apCost(1)
            .unleashPoint(1)
            .hitComponents(List.of(new HitComponent(
                45, Set.of(MoveTag.INNATE_TECHNIQUE, MoveTag.RANGED), 0, false, true,
                1.0, List.of())))
            .requiredTechniqueId("Idle Transfiguration")
            .prerequisites(java.util.Map.of("cursedTechniqueMastery", 0))
            .effects(List.of(spendRow()))
            .build();
    }

    private static void place(BattleCombatant actor, Move move, List<BattleCombatant> targets) {
        BattlePlan plan = new BattlePlan(actor.getMaxApBar(), actor.getCurrentCe());
        var segment = plan.place(move, 1, 0);
        assertNotNull(segment);
        segment.setTargets(targets.stream().map(BattleCombatant::getInstanceId).toList());
        actor.setTimeline(plan.toLegacyTimeline());
    }

    private static final class SequenceRandom implements RandomSource {
        private final ArrayDeque<Double> values;

        SequenceRandom(double... rolls) {
            this.values = new ArrayDeque<>();
            for (double roll : rolls) values.addLast(roll);
        }

        @Override public int nextInt(int bound) { return 0; }
        @Override public double nextDouble() {
            return values.isEmpty() ? 0.99 : values.removeFirst();
        }
        @Override public boolean nextBoolean() { return false; }
    }
}
