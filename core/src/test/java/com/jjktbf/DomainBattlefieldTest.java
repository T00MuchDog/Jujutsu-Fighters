package com.jjktbf;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.Equipment;
import com.jjktbf.model.character.ShikigamiCharacter;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.AbilityActivationEngine;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.domain.DomainAudience;
import com.jjktbf.model.domain.DomainCapturePolicy;
import com.jjktbf.model.domain.DomainCounterType;
import com.jjktbf.model.domain.DomainData;
import com.jjktbf.model.domain.DomainDefinition;
import com.jjktbf.model.domain.DomainDeliveryClass;
import com.jjktbf.model.domain.DomainInstance;
import com.jjktbf.model.domain.DomainProtectionPolicy;
import com.jjktbf.model.domain.DomainTopology;
import com.jjktbf.model.domain.DomainTrigger;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffectType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainBattlefieldTest {

    @Test
    void sameTickDeclarationsEstablishAtomicallyBeforeSureHits() {
        DomainDefinition firstDomain = ordinaryDomain(
            "DOMAIN_A", "First Domain", "First Technique", 0,
            directDamage(DomainDeliveryClass.ATTACK, DomainTrigger.ON_ESTABLISH, 25));
        DomainDefinition secondDomain = ordinaryDomain(
            "DOMAIN_B", "Second Domain", "Second Technique", 0,
            directDamage(DomainDeliveryClass.ATTACK, DomainTrigger.ON_ESTABLISH, 25));
        Move firstOpening = openingMove("OPEN_A", firstDomain.id());
        Move secondOpening = openingMove("OPEN_B", secondDomain.id());
        BattleCombatant first = combatant(
            "FIRST", "First Technique", firstOpening, firstDomain.id());
        BattleCombatant second = combatant(
            "SECOND", "Second Technique", secondOpening, secondDomain.id());
        BattleState state = new BattleState(first, second);
        plan(first, firstOpening);
        plan(second, secondOpening);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        Map<String, DomainDefinition> domains = Map.of(
            firstDomain.id(), firstDomain, secondDomain.id(), secondDomain);

        List<CombatEvent> events = new CombatResolver(new ZeroRandom())
            .withDomainLookup(id -> Optional.ofNullable(domains.get(id)))
            .resolveRound(state);

        assertEquals(first.getMaxHp(), first.getCurrentHp());
        assertEquals(second.getMaxHp(), second.getCurrentHp());
        assertEquals(2, state.domainBattlefield().activeDomains().size());
        assertEquals(2, events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.DOMAIN_ESTABLISHED)
            .count());
        assertEquals(1, events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.DOMAIN_CLASH_STARTED)
            .count());
        assertEquals(2, events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.DOMAIN_SURE_HIT_NEGATED)
            .count());
    }

    @Test
    void clashCollapseCleansLeasedEffectsAndAppliesTechniqueBurnoutOnce() {
        AbilityEffectData fieldStatus = domainRow(
            AbilityEffectType.APPLY_STATUS,
            DomainTrigger.ON_ESTABLISH,
            DomainAudience.OWNER,
            DomainDeliveryClass.EFFECT);
        fieldStatus.stringValue = StatusEffectType.STRENGTH_INCREASE.name();
        fieldStatus.durationRounds = -1;
        fieldStatus.durationTicks = 0;
        fieldStatus.magnitude = 20.0;
        DomainDefinition firstDomain = ordinaryDomain(
            "FRAGILE_A", "Fragile A", "Technique A", 5, fieldStatus);
        DomainDefinition secondDomain = ordinaryDomain(
            "FRAGILE_B", "Fragile B", "Technique B", 5, fieldStatus.copy());
        BattleCombatant first = combatant("FIRST", "Technique A", null, firstDomain.id());
        BattleCombatant second = combatant("SECOND", "Technique B", null, secondDomain.id());
        BattleState state = new BattleState(first, second);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, firstDomain, first, List.of(), 1).accepted());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, secondDomain, second, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);
        assertTrue(first.hasEffect(StatusEffectType.STRENGTH_INCREASE));
        assertTrue(second.hasEffect(StatusEffectType.STRENGTH_INCREASE));

        List<CombatEvent> events = state.domainBattlefield().processTick(
            state, effects::executeDomainEffect, 1);

        assertTrue(state.domainBattlefield().activeDomains().isEmpty());
        assertFalse(first.hasEffect(StatusEffectType.STRENGTH_INCREASE));
        assertFalse(second.hasEffect(StatusEffectType.STRENGTH_INCREASE));
        assertTrue(first.isTechniqueLocked("Technique A"));
        assertTrue(second.isTechniqueLocked("Technique B"));
        assertEquals(2, events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED)
            .count());
    }

    @Test
    void interceptionCountersAttackDeliveryButNotEffectDelivery() {
        assertCounterResult(DomainDeliveryClass.ATTACK, true);
        assertCounterResult(DomainDeliveryClass.EFFECT, false);
    }

    @Test
    void unpaidUpkeepCollapsesTheDomainOnItsNextTick() {
        DomainData data = ordinaryDomainData(
            "UPKEEP_DOMAIN", "Upkeep Domain", "Upkeep Technique");
        data.ceUpkeepPerTick = 5.0;
        data.durationRounds = -1;
        DomainDefinition domain = data.toDomain();
        BattleCombatant owner = combatant("OWNER", "Upkeep Technique", null, domain.id());
        BattleCombatant enemy = combatant("ENEMY", null, null);
        BattleState state = new BattleState(owner, enemy);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, domain, owner, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);
        assertEquals(1, state.domainBattlefield().activeDomains().size());
        owner.drainCe(owner.getCurrentCe());

        List<CombatEvent> events = state.domainBattlefield().processTick(
            state, effects::executeDomainEffect, 2);

        assertTrue(state.domainBattlefield().activeDomains().isEmpty());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
            && "UPKEEP_FAILED".equals(event.getDomainCollapseReason())));
    }

    @Test
    void defeatedAndRemovedOwnersCollapseTheirDomains() {
        DomainData defeatData = ordinaryDomainData(
            "DEFEAT_DOMAIN", "Defeat Domain", "Defeat Technique");
        DomainDefinition defeatDomain = defeatData.toDomain();
        BattleCombatant owner = combatant("OWNER", "Defeat Technique", null, defeatDomain.id());
        BattleCombatant enemy = combatant("ENEMY", null, null);
        BattleState state = new BattleState(owner, enemy);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, defeatDomain, owner, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);
        owner.receiveDamage(owner.getCurrentHp());

        List<CombatEvent> events = state.domainBattlefield().processTick(
            state, effects::executeDomainEffect, 2);

        assertTrue(state.domainBattlefield().activeDomains().isEmpty());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
            && "OWNER_DEFEATED".equals(event.getDomainCollapseReason())));

        // Removal path: a summon that owns an anti-Domain collapses when its
        // tree is voluntarily dismissed mid-battle.
        DomainDefinition counter = counterDomain(
            "REMOVAL_COUNTER", DomainCounterType.SURE_HIT_NULLIFICATION, 10, -1);
        BattleCombatant summoner = combatant("SUMMONER", null, null);
        BattleCombatant foe = combatant("FOE", null, null);
        BattleState summonState = new BattleState(summoner, foe);
        summonState.enqueueSummon(summoner, "DOG");
        BattleCombatant summon = summonState
            .drainPendingSummons(id -> Optional.of(shikigami("Divine Dog"))).get(0);
        assertTrue(summonState.domainBattlefield().queueDeclaration(
            summonState, counter, summon, List.of(), 1).accepted());
        summonState.domainBattlefield().resolveDeclarations(
            summonState, effects::executeDomainEffect, 1);
        assertEquals(1, summonState.domainBattlefield().activeDomains().size());

        summonState.voluntarilyDesummon(summon);
        List<CombatEvent> removed = summonState.domainBattlefield().reconcileOwners(
            summonState, effects::executeDomainEffect, 2);

        assertTrue(summonState.domainBattlefield().activeDomains().isEmpty());
        assertTrue(removed.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
            && "OWNER_REMOVED".equals(event.getDomainCollapseReason())));
    }

    @Test
    void summonsFollowTheirSummonerIntoAnEstablishedDomain() {
        AbilityEffectData drag = domainRow(
            AbilityEffectType.APPLY_STATUS,
            DomainTrigger.ON_MEMBER_ENTER,
            DomainAudience.ENTERING_MEMBER,
            DomainDeliveryClass.EFFECT);
        drag.stringValue = StatusEffectType.SPEED_DECREASE.name();
        drag.durationRounds = 1;
        drag.durationTicks = 0;
        drag.magnitude = 15.0;
        DomainData data = ordinaryDomainData(
            "GARDEN", "Garden", "Garden Technique", drag);
        data.capturePolicy = DomainCapturePolicy.OWNER_AND_SELECTED.name();
        DomainDefinition domain = data.toDomain();
        BattleCombatant owner = combatant("OWNER", "Garden Technique", null, domain.id());
        BattleCombatant enemy = combatant("ENEMY", null, null);
        BattleState state = new BattleState(owner, enemy);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, domain, owner, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);
        state.enqueueSummon(owner, "DOG");
        BattleCombatant summon = state
            .drainPendingSummons(id -> Optional.of(shikigami("Divine Dog"))).get(0);

        List<CombatEvent> events = state.domainBattlefield().onCombatantEntered(
            state, summon, effects::executeDomainEffect, 2);

        assertTrue(state.domainBattlefield().activeDomains().get(0)
            .contains(summon.getInstanceId()));
        assertTrue(summon.hasEffect(StatusEffectType.SPEED_DECREASE));
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.STATUS_APPLIED
            && event.getTarget() == summon));
    }

    @Test
    void openTopologyPressesClosedExteriorsWhileClosedPairsStaySealed() {
        DomainData openData = ordinaryDomainData(
            "OPEN_FIELD", "Open Field", "Open Technique");
        openData.topology = DomainTopology.OPEN.name();
        openData.externalPressurePerTick = 10;
        openData.externalBarrierIntegrity = 100;
        DomainDefinition open = openData.toDomain();
        DomainData closedData = ordinaryDomainData(
            "CLOSED_FIELD", "Closed Field", "Closed Technique");
        closedData.externalBarrierIntegrity = 15;
        DomainDefinition closed = closedData.toDomain();
        BattleCombatant openOwner = combatant(
            "OPEN_OWNER", "Open Technique", null, open.id());
        BattleCombatant closedOwner = combatant(
            "CLOSED_OWNER", "Closed Technique", null, closed.id());
        BattleState state = new BattleState(openOwner, closedOwner);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, open, openOwner, List.of(), 1).accepted());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, closed, closedOwner, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);
        DomainInstance closedInstance = state.domainBattlefield().activeDomains().stream()
            .filter(instance -> instance.definition() == closed).findFirst().orElseThrow();

        List<CombatEvent> firstTick = state.domainBattlefield().processTick(
            state, effects::executeDomainEffect, 2);

        assertEquals(5, closedInstance.externalBarrierIntegrity());
        assertTrue(firstTick.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_BARRIER_DAMAGED
            && closedInstance.instanceId().equals(event.getDomainInstanceId())));

        List<CombatEvent> secondTick = state.domainBattlefield().processTick(
            state, effects::executeDomainEffect, 3);

        assertTrue(state.domainBattlefield().activeDomains().stream()
            .noneMatch(instance -> instance.definition() == closed));
        assertTrue(secondTick.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
            && "EXTERNAL_BARRIER_BROKEN".equals(event.getDomainCollapseReason())));

        // Control: two sealed Domains with no clash pressure never touch each
        // other's exterior barrier.
        DomainData firstClosed = ordinaryDomainData(
            "SEALED_A", "Sealed A", "Sealed A Technique");
        firstClosed.externalBarrierIntegrity = 15;
        DomainData secondClosed = ordinaryDomainData(
            "SEALED_B", "Sealed B", "Sealed B Technique");
        secondClosed.externalBarrierIntegrity = 15;
        BattleCombatant first = combatant(
            "SEALED_A_OWNER", "Sealed A Technique", null, firstClosed.id);
        BattleCombatant second = combatant(
            "SEALED_B_OWNER", "Sealed B Technique", null, secondClosed.id);
        BattleState sealedState = new BattleState(first, second);
        sealedState.domainBattlefield().queueDeclaration(
            sealedState, firstClosed.toDomain(), first, List.of(), 1);
        sealedState.domainBattlefield().queueDeclaration(
            sealedState, secondClosed.toDomain(), second, List.of(), 1);
        sealedState.domainBattlefield().resolveDeclarations(
            sealedState, effects::executeDomainEffect, 1);
        sealedState.domainBattlefield().processTick(sealedState, effects::executeDomainEffect, 2);
        assertEquals(2, sealedState.domainBattlefield().activeDomains().size());
        sealedState.domainBattlefield().activeDomains().forEach(
            instance -> assertEquals(15, instance.externalBarrierIntegrity()));
    }

    @Test
    void redeclaringReplacesTheOwnersActiveDomain() {
        DomainData firstData = ordinaryDomainData(
            "FIRST_DOMAIN", "First Domain", "Replace Technique");
        DomainData secondData = ordinaryDomainData(
            "SECOND_DOMAIN", "Second Domain", "Replace Technique");
        DomainDefinition first = firstData.toDomain();
        DomainDefinition second = secondData.toDomain();
        BattleCombatant owner = combatant(
            "OWNER", "Replace Technique", null, first.id(), second.id());
        BattleCombatant enemy = combatant("ENEMY", null, null);
        BattleState state = new BattleState(owner, enemy);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, first, owner, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, second, owner, List.of(), 2).accepted());
        List<CombatEvent> events = state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 2);

        List<DomainInstance> active = state.domainBattlefield().activeDomains();
        assertEquals(1, active.size());
        assertEquals(second.id(), active.get(0).definition().id());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
            && "REPLACED".equals(event.getDomainCollapseReason())));
    }

    private static void assertCounterResult(
        DomainDeliveryClass delivery,
        boolean expectedNegated
    ) {
        DomainDefinition hostile = ordinaryDomain(
            "HOSTILE_" + delivery, "Hostile " + delivery, "Hostile Technique", 3,
            directDamage(delivery, DomainTrigger.ON_ESTABLISH, 20));
        DomainDefinition counter = counterDomain(
            "COUNTER_" + delivery, DomainCounterType.SURE_HIT_INTERCEPTION, 3, 1);
        BattleCombatant attacker = combatant(
            "ATTACKER", "Hostile Technique", null, hostile.id());
        BattleCombatant defender = combatant("DEFENDER", null, null);
        BattleState state = new BattleState(attacker, defender);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());
        int hpBefore = defender.getCurrentHp();

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, hostile, attacker, List.of(), 1).accepted());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, counter, defender, List.of(), 1).accepted());
        List<CombatEvent> events = state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);

        assertEquals(expectedNegated ? hpBefore : hpBefore - 20, defender.getCurrentHp());
        assertEquals(expectedNegated, events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_SURE_HIT_NEGATED));
        assertEquals(expectedNegated ? 1 : 2,
            state.domainBattlefield().activeDomains().size());
        if (expectedNegated) {
            assertTrue(events.stream().anyMatch(event ->
                event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
                    && "COUNTER_EXHAUSTED".equals(event.getDomainCollapseReason())));
        }
    }

    private static DomainDefinition ordinaryDomain(
        String id,
        String name,
        String technique,
        int clashPressure,
        AbilityEffectData effect
    ) {
        return ordinaryDomainData(id, name, technique, clashPressure, effect).toDomain();
    }

    private static DomainData ordinaryDomainData(String id, String name, String technique) {
        return ordinaryDomainData(id, name, technique, 0, null);
    }

    private static DomainData ordinaryDomainData(
        String id,
        String name,
        String technique,
        AbilityEffectData effect
    ) {
        return ordinaryDomainData(id, name, technique, 0, effect);
    }

    private static DomainData ordinaryDomainData(
        String id,
        String name,
        String technique,
        int clashPressure,
        AbilityEffectData effect
    ) {
        DomainData data = new DomainData();
        data.id = id;
        data.name = name;
        data.requiredTechniqueName = technique;
        data.durationRounds = 2;
        data.internalBarrierIntegrity = 5;
        data.externalBarrierIntegrity = 5;
        data.clashPressurePerTick = clashPressure;
        data.protectionPolicy = DomainProtectionPolicy.OWNER.name();
        if (effect != null) {
            if (DomainAudience.OWNER.name().equals(effect.domainAudience)) {
                data.fieldEffects = List.of(effect);
            } else {
                data.sureHitEffects = List.of(effect);
            }
        }
        return data;
    }

    private static ShikigamiCharacter shikigami(String name) {
        return new ShikigamiCharacter(
            name.toLowerCase(), name, new CharacterStats.Builder().build(),
            null, List.of(), List.of(), Equipment.NONE);
    }

    private static DomainDefinition counterDomain(
        String id,
        DomainCounterType type,
        int potency,
        int uses
    ) {
        DomainData data = new DomainData();
        data.id = id;
        data.name = id;
        data.antiDomain = true;
        data.burnoutRounds = 0;
        data.burnoutTicks = 0;
        data.capturePolicy = DomainCapturePolicy.OWNER_AND_SELECTED.name();
        data.protectionPolicy = DomainProtectionPolicy.OWNER.name();
        data.counterType = type.name();
        data.counterPotency = potency;
        data.counterUses = uses;
        data.durationRounds = 2;
        return data.toDomain();
    }

    private static AbilityEffectData directDamage(
        DomainDeliveryClass delivery,
        DomainTrigger trigger,
        int amount
    ) {
        AbilityEffectData effect = domainRow(
            AbilityEffectType.DEAL_DIRECT_DAMAGE,
            trigger,
            DomainAudience.ENEMY_MEMBERS,
            delivery);
        effect.valueMode = AbilityEffectType.ValueMode.FLAT.name();
        effect.intValue = amount;
        effect.doubleValue = null;
        return effect;
    }

    private static AbilityEffectData domainRow(
        AbilityEffectType type,
        DomainTrigger trigger,
        DomainAudience audience,
        DomainDeliveryClass delivery
    ) {
        AbilityEffectData effect = type.createDefault();
        effect.effectId = type.name() + "_ROW";
        effect.domainTrigger = trigger.name();
        effect.domainAudience = audience.name();
        effect.domainDeliveryClass = delivery.name();
        effect.domainIntervalTicks = 1;
        effect.domainActivationChanceEnabled = false;
        effect.domainActivationChance = 1.0;
        return effect;
    }

    private static Move openingMove(String id, String domainId) {
        MoveEffectData establish = AbilityEffectType.ESTABLISH_DOMAIN.createDefaultMoveEffect();
        establish.effectId = id + "_DOMAIN";
        establish.domainId = domainId;
        establish.trigger = MoveEffectTrigger.ON_FIRE.name();
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY, MoveTag.CURSED_ENERGY))
            .apCost(1)
            .unleashPoint(1)
            .effects(List.of(establish))
            .build();
    }

    private static BattleCombatant combatant(
        String id,
        String technique,
        Move move,
        String... domainIds
    ) {
        Character character = new SorcererCharacter(
            id, id, stats(), technique, move == null ? List.of() : List.of(move))
            .withAccessibleDomains(List.of(domainIds));
        return new BattleCombatant(character);
    }

    private static void plan(BattleCombatant combatant, Move move) {
        BattlePlan plan = new BattlePlan(combatant.getMaxApBar(), combatant.getCurrentCe());
        ActionSegment segment = plan.place(move, 1, 0);
        assertTrue(segment != null);
        combatant.setTimeline(plan.toLegacyTimeline());
    }

    private static CharacterStats stats() {
        return new CharacterStats.Builder()
            .vitality(100)
            .strength(100)
            .durability(100)
            .speed(100)
            .cursedEnergyReserves(100)
            .cursedEnergyEfficiency(100)
            .cursedEnergyOutput(100)
            .jujutsuSkill(100)
            .combatAbility(100)
            .cursedTechniqueMastery(100)
            .build();
    }

    private static final class ZeroRandom implements RandomSource {
        @Override public int nextInt(int bound) { return 0; }
        @Override public double nextDouble() { return 0.0; }
        @Override public boolean nextBoolean() { return false; }
    }
}
