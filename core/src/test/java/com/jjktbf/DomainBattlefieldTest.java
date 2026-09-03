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
import com.jjktbf.model.domain.DomainTrigger;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffectType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainBattlefieldTest {

    @Test
    void captureAlwaysIncludesOwnerAndUsesOnlyEveryoneOrSelectedTargets() {
        DomainData everyoneData = ordinaryDomainData(
            "EVERYONE", "Everyone Domain", "Everyone Technique");
        everyoneData.capturePolicy = DomainCapturePolicy.EVERYONE.name();
        DomainDefinition everyone = everyoneData.toDomain();
        BattleCombatant everyoneOwner = combatant(
            "EVERYONE_OWNER", "Everyone Technique", null, everyone.id());
        BattleCombatant everyoneEnemy = combatant("EVERYONE_ENEMY", null, null);
        BattleState everyoneState = new BattleState(everyoneOwner, everyoneEnemy);

        assertTrue(everyoneState.domainBattlefield().queueDeclaration(
            everyoneState, everyone, everyoneOwner, List.of(), 1).accepted());
        everyoneState.domainBattlefield().resolveDeclarations(everyoneState, null, 1);
        DomainInstance everyoneInstance = everyoneState.domainBattlefield().activeDomains().get(0);
        assertEquals(Set.of(everyoneOwner.getInstanceId(), everyoneEnemy.getInstanceId()),
            everyoneInstance.memberIds());

        DomainData selectedData = ordinaryDomainData(
            "SELECTED", "Selected Domain", "Selected Technique");
        selectedData.capturePolicy = DomainCapturePolicy.SELECTED_TARGETS.name();
        DomainDefinition selected = selectedData.toDomain();
        BattleCombatant selectedOwner = combatant(
            "SELECTED_OWNER", "Selected Technique", null, selected.id());
        BattleCombatant selectedEnemy = combatant("SELECTED_ENEMY", null, null);
        BattleState selectedState = new BattleState(selectedOwner, selectedEnemy);

        assertTrue(selectedState.domainBattlefield().queueDeclaration(
            selectedState, selected, selectedOwner, List.of(selectedEnemy), 1).accepted());
        selectedState.domainBattlefield().resolveDeclarations(selectedState, null, 1);
        assertEquals(Set.of(selectedOwner.getInstanceId(), selectedEnemy.getInstanceId()),
            selectedState.domainBattlefield().activeDomains().get(0).memberIds());
    }

    @Test
    void antiDomainsRejectSureHitFieldAndCasterPrograms() {
        for (int channel = 0; channel < 3; channel++) {
            DomainData data = new DomainData();
            data.id = "COUNTER_" + channel;
            data.name = data.id;
            data.antiDomain = true;
            data.burnoutRounds = 0;
            data.counterType = DomainCounterType.SURE_HIT_NULLIFICATION.name();
            AbilityEffectData effect = new AbilityEffectData();
            switch (channel) {
                case 0 -> data.sureHitEffects = List.of(effect);
                case 1 -> data.fieldEffects = List.of(effect);
                case 2 -> data.casterEffects = List.of(effect);
                default -> throw new AssertionError("Unexpected channel");
            }

            assertThrows(IllegalArgumentException.class, data::validate);
        }
    }

    @Test
    void sameTickDeclarationsEstablishAtomicallyBeforeSureHits() {
        DomainDefinition firstDomain = ordinaryDomain(
            "DOMAIN_A", "First Domain", "First Technique",
            directDamage(DomainDeliveryClass.ATTACK, DomainTrigger.ON_ESTABLISH, 25));
        DomainDefinition secondDomain = ordinaryDomain(
            "DOMAIN_B", "Second Domain", "Second Technique",
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
    void clashDifferenceBreaksTheOutmatchedDomainAndKeepsTheWinner() {
        AbilityEffectData winnersField = ownerStrength(DomainTrigger.ON_ESTABLISH);
        DomainDefinition winnerDomain = ordinaryDomain(
            "DOMINANT", "Dominant Domain", "Winner Technique", winnersField);
        DomainDefinition loserDomain = ordinaryDomain(
            "OUTMATCHED", "Outmatched Domain", "Loser Technique", winnersField.copy());
        BattleCombatant winner = clashCombatant(
            "WINNER", "Winner Technique", winnerDomain.id(), 100, 100);
        BattleCombatant loser = clashCombatant(
            "LOSER", "Loser Technique", loserDomain.id(), 0, 0);
        BattleState state = new BattleState(winner, loser);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, winnerDomain, winner, List.of(), 1).accepted());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, loserDomain, loser, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(state, effects::executeDomainEffect, 1);
        assertTrue(winner.hasEffect(StatusEffectType.STRENGTH_INCREASE));
        assertTrue(loser.hasEffect(StatusEffectType.STRENGTH_INCREASE));

        List<CombatEvent> events = new ArrayList<>();
        for (int tick = 2; tick < 100
            && state.domainBattlefield().activeDomains().size() > 1; tick++) {
            events.addAll(state.domainBattlefield().processTick(
                state, effects::executeDomainEffect, tick));
        }

        List<DomainInstance> active = state.domainBattlefield().activeDomains();
        assertEquals(1, active.size());
        assertEquals(winnerDomain.id(), active.get(0).definition().id());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
                && "INTERNAL_BARRIER_BROKEN".equals(event.getDomainCollapseReason())
                && loserDomain.id().equals(event.getDomainId())));
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_CLASH_ENDED));
        // The loser loses its leased effects and eats technique burnout; the
        // winner keeps its field and stays open.
        assertFalse(loser.hasEffect(StatusEffectType.STRENGTH_INCREASE));
        assertTrue(loser.isTechniqueLocked("Loser Technique"));
        assertTrue(winner.hasEffect(StatusEffectType.STRENGTH_INCREASE));
        assertFalse(winner.isTechniqueLocked("Winner Technique"));
    }

    @Test
    void evenClashAccumulatesNothingUntilOneSideCannotMaintain() {
        DomainData data = ordinaryDomainData(
            "MIRROR", "Mirror Domain", "Mirror Technique");
        data.durationRounds = -1;
        data.ceUpkeepPerTick = 5.0;
        DomainDefinition domain = data.toDomain();
        BattleCombatant first = clashCombatant(
            "FIRST", "Mirror Technique", domain.id(), 50, 50);
        BattleCombatant second = clashCombatant(
            "SECOND", "Mirror Technique", domain.id(), 50, 50);
        BattleState state = new BattleState(first, second);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, domain, first, List.of(), 1).accepted());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, domain, second, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(state, effects::executeDomainEffect, 1);

        for (int tick = 2; tick <= 4; tick++) {
            state.domainBattlefield().processTick(state, effects::executeDomainEffect, tick);
        }
        assertEquals(2, state.domainBattlefield().activeDomains().size());
        assertEquals(1, state.domainBattlefield().clashes().size());
        assertNull(state.domainBattlefield().clashes().get(0).leaderInstanceId());
        assertEquals(0.0, state.domainBattlefield().clashes().get(0).takeoverProgress());

        // Identical owners drain identically, so the mirror grind ends with
        // both sides failing upkeep on the same tick — never a takeover.
        int collapses = 0;
        for (int tick = 5; collapses == 0 && tick < 100_000; tick++) {
            collapses = (int) state.domainBattlefield().processTick(
                state, effects::executeDomainEffect, tick).stream()
                .filter(event -> event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED)
                .count();
        }
        assertTrue(state.domainBattlefield().activeDomains().isEmpty());
        assertEquals(2, collapses);
        assertTrue(state.domainBattlefield().clashes().isEmpty());
    }

    @Test
    void clashSubtractsTheScoreDifferenceFromTheWeakerDomainsIntegrity() {
        DomainData data = ordinaryDomainData(
            "DIFFERENCE", "Difference Domain", "Difference Technique");
        data.durationRounds = -1;
        DomainDefinition domain = data.toDomain();
        BattleCombatant leader = clashCombatant(
            "LEADER", "Difference Technique", domain.id(), 50, 50);
        BattleCombatant trailer = clashCombatant(
            "TRAILER", "Difference Technique", domain.id(), 40, 40);
        BattleState state = new BattleState(leader, trailer);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, domain, leader, List.of(), 1).accepted());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, domain, trailer, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(state, effects::executeDomainEffect, 1);
        DomainInstance leaderDomain = state.domainBattlefield().activeDomains().stream()
            .filter(instance -> instance.ownerId().equals(leader.getInstanceId()))
            .findFirst().orElseThrow();
        DomainInstance trailerDomain = state.domainBattlefield().activeDomains().stream()
            .filter(instance -> instance.ownerId().equals(trailer.getInstanceId()))
            .findFirst().orElseThrow();

        state.domainBattlefield().processTick(state, effects::executeDomainEffect, 2);

        assertEquals(350, leaderDomain.internalBarrierIntegrity());
        assertEquals(318, trailerDomain.internalBarrierIntegrity());
        assertEquals(leaderDomain.instanceId(),
            state.domainBattlefield().clashes().get(0).leaderInstanceId());
        assertEquals(2.0 / 320.0,
            state.domainBattlefield().clashes().get(0).takeoverProgress(), 1.0e-12);
    }

    @Test
    void liveCursedEnergyPoolChangesClashDamageAndLeadership() {
        DomainData data = ordinaryDomainData(
            "POOL", "Pool Domain", "Pool Technique");
        data.durationRounds = -1;
        DomainDefinition domain = data.toDomain();
        BattleCombatant first = clashCombatant(
            "FIRST", "Pool Technique", domain.id(), 50, 50);
        BattleCombatant second = clashCombatant(
            "SECOND", "Pool Technique", domain.id(), 50, 50);
        BattleState state = new BattleState(first, second);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, domain, first, List.of(), 1).accepted());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, domain, second, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(state, effects::executeDomainEffect, 1);

        // Even pools: no leader, no progress.
        state.domainBattlefield().processTick(state, effects::executeDomainEffect, 2);
        assertNull(state.domainBattlefield().clashes().get(0).leaderInstanceId());

        // One side's pool empties: its CE pressure collapses and the clash
        // score immediately favours the other side.
        second.drainCe(second.getCurrentCe());
        state.domainBattlefield().processTick(state, effects::executeDomainEffect, 3);

        var clash = state.domainBattlefield().clashes().get(0);
        String firstInstance = state.domainBattlefield().activeDomains().stream()
            .filter(instance -> instance.ownerId().equals(first.getInstanceId()))
            .findFirst().orElseThrow().instanceId();
        assertEquals(firstInstance, clash.leaderInstanceId());
        DomainInstance secondDomain = state.domainBattlefield().activeDomains().stream()
            .filter(instance -> instance.ownerId().equals(second.getInstanceId()))
            .findFirst().orElseThrow();
        assertEquals(345, secondDomain.internalBarrierIntegrity());
        assertEquals(5.0 / 350.0, clash.takeoverProgress(), 1.0e-12);

        second.restoreCe(second.getMaxCursedEnergy());
        state.domainBattlefield().processTick(state, effects::executeDomainEffect, 4);
        assertNull(state.domainBattlefield().clashes().get(0).leaderInstanceId());
        assertEquals(0.0, state.domainBattlefield().clashes().get(0).takeoverProgress());
    }

    @Test
    void positiveFractionalClashDifferenceStillDealsIntegrityDamage() {
        DomainData data = ordinaryDomainData(
            "FRACTION", "Fraction Domain", "Fraction Technique");
        data.durationRounds = -1;
        DomainDefinition domain = data.toDomain();
        BattleCombatant first = combatant(
            "FIRST", "Fraction Technique", null, domain.id());
        BattleCombatant second = combatant(
            "SECOND", "Fraction Technique", null, domain.id());
        BattleState state = new BattleState(first, second);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        state.domainBattlefield().queueDeclaration(state, domain, first, List.of(), 1);
        state.domainBattlefield().queueDeclaration(state, domain, second, List.of(), 1);
        state.domainBattlefield().resolveDeclarations(state, effects::executeDomainEffect, 1);
        second.drainCe(second.getCurrentCe() - 99);
        DomainInstance weaker = state.domainBattlefield().activeDomains().stream()
            .filter(instance -> instance.ownerId().equals(second.getInstanceId()))
            .findFirst().orElseThrow();

        state.domainBattlefield().processTick(state, effects::executeDomainEffect, 2);

        assertEquals(499, weaker.internalBarrierIntegrity());
    }

    @Test
    void hostileDomainAppliesItsFullClashScoreToAnAntiDomainWithoutClashing() {
        DomainDefinition hostile = ordinaryDomainData(
            "HOSTILE", "Hostile Domain", "Hostile Technique").toDomain();
        DomainDefinition counter = counterDomain(
            "COUNTER", DomainCounterType.SURE_HIT_NULLIFICATION, 100, -1);
        BattleCombatant attacker = clashCombatant(
            "ATTACKER", "Hostile Technique", hostile.id(), 50, 50);
        BattleCombatant defender = combatant("DEFENDER", null, null);
        BattleState state = new BattleState(attacker, defender);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, hostile, attacker, List.of(), 1).accepted());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, counter, defender, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(state, effects::executeDomainEffect, 1);
        DomainInstance antiDomain = state.domainBattlefield().activeDomains().stream()
            .filter(instance -> instance.definition().antiDomain())
            .findFirst().orElseThrow();

        assertTrue(state.domainBattlefield().clashes().isEmpty());
        assertEquals(5000, antiDomain.internalBarrierIntegrity());
        List<CombatEvent> events = state.domainBattlefield().processTick(
            state, effects::executeDomainEffect, 2);

        assertEquals(4982, antiDomain.internalBarrierIntegrity());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_BARRIER_DAMAGED
                && antiDomain.instanceId().equals(event.getDomainInstanceId())
                && event.getIntValue() == 18));
        assertTrue(state.domainBattlefield().clashes().isEmpty());
    }

    @Test
    void unlimitedTechniqueContactNullificationBlocksEverySureHitWhileMaintained() {
        DomainDefinition hostile = ordinaryDomain(
            "HOSTILE", "Hostile Domain", "Hostile Technique",
            directDamage(DomainDeliveryClass.ATTACK, DomainTrigger.EACH_TICK, 20));
        DomainDefinition simpleDomain = counterDomain(
            "SIMPLE", DomainCounterType.TECHNIQUE_CONTACT_NULLIFICATION, 100, -1);
        BattleCombatant attacker = combatant(
            "ATTACKER", "Hostile Technique", null, hostile.id());
        BattleCombatant defender = combatant("DEFENDER", null, null);
        BattleState state = new BattleState(attacker, defender);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());
        int hpBefore = defender.getCurrentHp();

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, hostile, attacker, List.of(), 1).accepted());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, simpleDomain, defender, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);

        List<CombatEvent> events = new ArrayList<>();
        events.addAll(state.domainBattlefield().processTick(
            state, effects::executeDomainEffect, 1));
        events.addAll(state.domainBattlefield().processTick(
            state, effects::executeDomainEffect, 2));

        assertEquals(hpBefore, defender.getCurrentHp());
        assertEquals(2, state.domainBattlefield().activeDomains().size());
        assertEquals(2, events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.DOMAIN_SURE_HIT_NEGATED)
            .count());
        assertFalse(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
                && simpleDomain.id().equals(event.getDomainId())));
    }

    @Test
    void hitOverEightPercentCollapsesTheDomainMaintainedByTheDefender() {
        DomainDefinition ordinary = ordinaryDomainData(
            "ORDINARY", "Ordinary", "Owner Technique").toDomain();
        BattleCombatant owner = combatant(
            "OWNER", "Owner Technique", null, ordinary.id());
        BattleCombatant enemy = combatant("ENEMY", null, null);
        BattleState state = new BattleState(owner, enemy);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, ordinary, owner, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);

        int eightPercentOrLess = owner.getMaxHp() * 8 / 100;
        owner.receiveDamage(eightPercentOrLess);
        assertTrue(state.domainBattlefield().onOwnerHitDamage(
            state, owner, eightPercentOrLess, effects::executeDomainEffect, 2).isEmpty());
        assertEquals(1, state.domainBattlefield().activeDomains().size());

        int overwhelmingDamage = eightPercentOrLess + 1;
        owner.receiveDamage(overwhelmingDamage);
        List<CombatEvent> events = state.domainBattlefield().onOwnerHitDamage(
            state, owner, overwhelmingDamage, effects::executeDomainEffect, 3);

        assertTrue(state.domainBattlefield().activeDomains().isEmpty());
        assertEquals(1, events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
                && "OWNER_DAMAGED".equals(event.getDomainCollapseReason()))
            .count());
        assertTrue(owner.isTechniqueLocked("Owner Technique"));
    }

    @Test
    void combatResolverAppliesThePerHitMaintenanceFailure() {
        DomainDefinition domain = ordinaryDomainData(
            "MAINTAINED", "Maintained Domain", "Owner Technique").toDomain();
        Move overwhelmingStrike = new Move.Builder("OVERWHELMING_STRIKE")
            .name("Overwhelming Strike")
            .category(MoveCategory.PHYSICAL)
            .neverMiss(true)
            .apCost(1)
            .unleashPoint(1)
            .hitComponents(List.of(new HitComponent(
                100_000, Set.of(MoveTag.PHYSICAL), 0, false, true)))
            .build();
        BattleCombatant attacker = combatant(
            "ATTACKER", null, overwhelmingStrike);
        BattleCombatant defender = combatant(
            "DEFENDER", "Owner Technique", null, domain.id());
        BattleState state = new BattleState(attacker, defender);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, domain, defender, List.of(), 0).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 0);
        plan(attacker, overwhelmingStrike);
        state.transitionTo(BattleState.Phase.RESOLUTION);

        List<CombatEvent> events = new CombatResolver(new ZeroRandom()).resolveRound(state);

        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
                && "OWNER_DAMAGED".equals(event.getDomainCollapseReason())));
    }

    @Test
    void domainCannotBeOpenedOrMaintainedBelowFifteenPercentHp() {
        DomainDefinition domain = ordinaryDomainData(
            "LOW_HP", "Low HP Domain", "Owner Technique").toDomain();
        BattleCombatant owner = combatant(
            "OWNER", "Owner Technique", null, domain.id());
        BattleCombatant enemy = combatant("ENEMY", null, null);
        BattleState state = new BattleState(owner, enemy);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, domain, owner, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);
        int hpBelowThreshold = (owner.getMaxHp() * 15 - 1) / 100;
        owner.receiveDamage(owner.getCurrentHp() - hpBelowThreshold);

        List<CombatEvent> events = state.domainBattlefield().onOwnerHealthChanged(
            state, owner, effects::executeDomainEffect, 2);

        assertTrue(state.domainBattlefield().activeDomains().isEmpty());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
                && "OWNER_LOW_HP".equals(event.getDomainCollapseReason())));
        assertFalse(state.domainBattlefield().queueDeclaration(
            state, domain, owner, List.of(), 3).accepted());
    }

    @Test
    void nonInnateOrdinaryDomainDoesNotApplyTechniqueBurnout() {
        DomainData data = ordinaryDomainData(
            "NON_INNATE", "Non-Innate Domain", null);
        data.burnoutRounds = 0;
        DomainDefinition domain = data.toDomain();
        BattleCombatant owner = combatant("OWNER", null, null, domain.id());
        BattleCombatant enemy = combatant("ENEMY", null, null);
        BattleState state = new BattleState(owner, enemy);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, domain, owner, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);

        int overwhelmingDamage = owner.getMaxHp() * 8 / 100 + 1;
        owner.receiveDamage(overwhelmingDamage);
        state.domainBattlefield().onOwnerHitDamage(
            state, owner, overwhelmingDamage, effects::executeDomainEffect, 2);

        assertTrue(state.domainBattlefield().activeDomains().isEmpty());
        assertFalse(owner.isTechniqueLocked("Non-Innate Technique"));
    }

    @Test
    void barrierRowsBreakInternalIntegrityWithoutTouchingTheClash() {
        // Authored barrier programs still deal direct integrity damage.
        DomainData attackerData = ordinaryDomainData(
            "BREAKER", "Breaker Domain", "Breaker Technique");
        attackerData.barrierEffects = List.of(flatRow(
            AbilityEffectType.DEAL_DIRECT_DAMAGE,
            DomainTrigger.ON_ESTABLISH, DomainAudience.BARRIER,
            DomainDeliveryClass.EFFECT, 20));
        DomainData targetData = ordinaryDomainData(
            "BRITTLE", "Brittle Domain", "Brittle Technique");
        targetData.internalBarrierIntegrity = 5;
        DomainDefinition attacker = attackerData.toDomain();
        DomainDefinition target = targetData.toDomain();
        BattleCombatant attackerOwner = clashCombatant(
            "BREAKER_OWNER", "Breaker Technique", attacker.id(), 50, 50);
        BattleCombatant targetOwner = clashCombatant(
            "BRITTLE_OWNER", "Brittle Technique", target.id(), 50, 50);
        BattleState state = new BattleState(attackerOwner, targetOwner);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, attacker, attackerOwner, List.of(), 1).accepted());
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, target, targetOwner, List.of(), 1).accepted());
        List<CombatEvent> events = state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);

        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_BARRIER_DAMAGED
            && target.id().equals(event.getDomainId())));
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
                && "INTERNAL_BARRIER_BROKEN".equals(event.getDomainCollapseReason())
                && target.id().equals(event.getDomainId())));
        assertEquals(1, state.domainBattlefield().activeDomains().size());
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
        data.capturePolicy = DomainCapturePolicy.SELECTED_TARGETS.name();
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
    void declaringWhileAnotherFieldIsActiveFailsWithoutReplacingIt() {
        DomainData firstData = ordinaryDomainData(
            "FIRST_DOMAIN", "First Domain", "Owner Technique");
        DomainData secondData = ordinaryDomainData(
            "SECOND_DOMAIN", "Second Domain", "Owner Technique");
        DomainDefinition first = firstData.toDomain();
        DomainDefinition second = secondData.toDomain();
        DomainDefinition counter = counterDomain(
            "COUNTER", DomainCounterType.SURE_HIT_NULLIFICATION, 100, -1);
        BattleCombatant owner = combatant(
            "OWNER", "Owner Technique", null, first.id(), second.id());
        BattleCombatant enemy = combatant("ENEMY", null, null);
        BattleState state = new BattleState(owner, enemy);
        AbilityActivationEngine effects = new AbilityActivationEngine(new ZeroRandom());

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, first, owner, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, effects::executeDomainEffect, 1);
        assertFalse(state.domainBattlefield().queueDeclaration(
            state, second, owner, List.of(), 2).accepted());
        assertFalse(state.domainBattlefield().queueDeclaration(
            state, counter, owner, List.of(), 2).accepted());

        List<DomainInstance> active = state.domainBattlefield().activeDomains();
        assertEquals(1, active.size());
        assertEquals(first.id(), active.get(0).definition().id());
    }

    @Test
    void secondFieldDeclarationFailsWhileTheFirstIsPending() {
        DomainDefinition ordinary = ordinaryDomainData(
            "ORDINARY", "Ordinary", "Owner Technique").toDomain();
        DomainDefinition counter = counterDomain(
            "COUNTER", DomainCounterType.SURE_HIT_NULLIFICATION, 100, -1);
        BattleCombatant owner = combatant(
            "OWNER", "Owner Technique", null, ordinary.id());
        BattleState state = new BattleState(owner, combatant("ENEMY", null, null));

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, ordinary, owner, List.of(), 1).accepted());
        assertFalse(state.domainBattlefield().queueDeclaration(
            state, counter, owner, List.of(), 1).accepted());
    }

    private static void assertCounterResult(
        DomainDeliveryClass delivery,
        boolean expectedNegated
    ) {
        DomainDefinition hostile = ordinaryDomain(
            "HOSTILE_" + delivery, "Hostile " + delivery, "Hostile Technique",
            directDamage(delivery, DomainTrigger.ON_ESTABLISH, 20));
        DomainDefinition counter = counterDomain(
            "COUNTER_" + delivery, DomainCounterType.SURE_HIT_INTERCEPTION, 100, 1);
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
        AbilityEffectData effect
    ) {
        return ordinaryDomainData(id, name, technique, effect).toDomain();
    }

    private static DomainData ordinaryDomainData(String id, String name, String technique) {
        return ordinaryDomainData(id, name, technique, null);
    }

    private static DomainData ordinaryDomainData(
        String id,
        String name,
        String technique,
        AbilityEffectData effect
    ) {
        DomainData data = new DomainData();
        data.id = id;
        data.name = name;
        data.requiredTechniqueName = technique;
        data.durationRounds = 2;
        data.clashValue = 100;
        data.protectionPolicy = DomainProtectionPolicy.OWNER.name();
        if (effect != null) {
            if (DomainAudience.OWNER.name().equals(effect.domainAudience)
                || DomainAudience.BARRIER.name().equals(effect.domainAudience)) {
                if (DomainAudience.BARRIER.name().equals(effect.domainAudience)) {
                    data.barrierEffects = List.of(effect);
                } else {
                    data.fieldEffects = List.of(effect);
                }
            } else {
                data.sureHitEffects = List.of(effect);
            }
        }
        return data;
    }

    private static AbilityEffectData ownerStrength(DomainTrigger trigger) {
        AbilityEffectData fieldStatus = domainRow(
            AbilityEffectType.APPLY_STATUS,
            trigger,
            DomainAudience.OWNER,
            DomainDeliveryClass.EFFECT);
        fieldStatus.stringValue = StatusEffectType.STRENGTH_INCREASE.name();
        fieldStatus.durationRounds = -1;
        fieldStatus.durationTicks = 0;
        fieldStatus.magnitude = 20.0;
        return fieldStatus;
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
        data.capturePolicy = DomainCapturePolicy.SELECTED_TARGETS.name();
        data.protectionPolicy = DomainProtectionPolicy.OWNER.name();
        data.counterType = type.name();
        data.counterPotency = potency;
        data.counterUses = uses;
        data.internalBarrierIntegrity = DomainData.DEFAULT_ANTI_DOMAIN_INTEGRITY;
        data.durationRounds = 2;
        return data.toDomain();
    }

    private static AbilityEffectData directDamage(
        DomainDeliveryClass delivery,
        DomainTrigger trigger,
        int amount
    ) {
        return flatRow(AbilityEffectType.DEAL_DIRECT_DAMAGE, trigger,
            DomainAudience.ENEMY_MEMBERS, delivery, amount);
    }

    private static AbilityEffectData flatRow(
        AbilityEffectType type,
        DomainTrigger trigger,
        DomainAudience audience,
        DomainDeliveryClass delivery,
        int amount
    ) {
        AbilityEffectData effect = domainRow(type, trigger, audience, delivery);
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

    /**
     * Clash-focused combatant: the given Jujutsu Skill and Cursed Technique
     * Mastery decide refinement. A small pool with ample output keeps the
     * CE-pool cap from binding, so a full pool reads as the full 100 pressure.
     */
    private static BattleCombatant clashCombatant(
        String id,
        String technique,
        String domainId,
        int jujutsuSkill,
        int cursedTechniqueMastery
    ) {
        CharacterStats clashStats = new CharacterStats.Builder()
            .vitality(100)
            .strength(100)
            .durability(100)
            .speed(100)
            .cursedEnergyReserves(10)
            .cursedEnergyEfficiency(100)
            .cursedEnergyOutput(100)
            .jujutsuSkill(jujutsuSkill)
            .combatAbility(100)
            .cursedTechniqueMastery(cursedTechniqueMastery)
            .build();
        Character character = new SorcererCharacter(
            id, id, clashStats, technique, List.of())
            .withAccessibleDomains(List.of(domainId));
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
