package com.jjktbf.controller;

import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.combat.DomainDefinitionLookup;
import com.jjktbf.model.domain.DomainData;
import com.jjktbf.model.domain.DomainDefinition;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Domain-opening valuation and planning for the shared smart-AI helpers. */
class DomainPlanningTest {

    @Test
    void ordinaryOpeningIsValuedWhileEligibleAndRestrictedWhenLocked() {
        DomainDefinition domain = ordinaryDomain("VOID", "Limitless");
        DomainDefinitionLookup lookup = lookup(domain);
        BattleCombatant owner = combatant("OWNER", "Limitless", domain.id());
        BattleCombatant enemy = combatant("ENEMY", null);
        BattleState state = new BattleState(owner, enemy);
        Move opening = openingMove("OPEN_VOID", domain.id());

        assertNull(SmartAIScoring.domainOpeningRestriction(lookup, state, owner, opening));
        assertEquals(SmartAIScoring.DOMAIN_OPENING_VALUE,
            SmartAIScoring.domainMoveValue(lookup, state, owner, opening), 0.0);

        AbilityEffectData lock = AbilityEffectType.TEMP_LOCK_TECHNIQUE.createDefault();
        lock.stringValue = "Limitless";
        owner.addRuntimeAbilityEffect(lock);
        assertEquals("Required technique locked",
            SmartAIScoring.domainOpeningRestriction(lookup, state, owner, opening));
        assertEquals(0.0,
            SmartAIScoring.domainMoveValue(lookup, state, owner, opening), 0.0);
    }

    @Test
    void nonInnateOrdinaryOpeningNeedsNoTechnique() {
        DomainData data = new DomainData();
        data.id = "NON_INNATE";
        data.name = "Non-Innate Field";
        data.requiredTechniqueName = null;
        data.burnoutRounds = 0;
        DomainDefinition domain = data.toDomain();
        DomainDefinitionLookup lookup = lookup(domain);
        BattleCombatant owner = combatant("OWNER", null, domain.id());
        BattleState state = new BattleState(owner, combatant("ENEMY", null));
        Move opening = openingMove("OPEN_NON_INNATE", domain.id());

        assertNull(SmartAIScoring.domainOpeningRestriction(lookup, state, owner, opening));
        assertEquals(SmartAIScoring.DOMAIN_OPENING_VALUE,
            SmartAIScoring.domainMoveValue(lookup, state, owner, opening), 0.0);
    }

    @Test
    void ordinaryOpeningIsRestrictedWhenNotOwnedOrAlreadyActive() {
        DomainDefinition domain = ordinaryDomain("VOID", "Limitless");
        DomainDefinitionLookup lookup = lookup(domain);
        Move opening = openingMove("OPEN_VOID", domain.id());

        BattleCombatant unranked = combatant("UNRANKED", "Limitless");
        BattleState unrankedState = new BattleState(
            unranked, combatant("ENEMY", null));
        assertEquals("Domain not unlocked",
            SmartAIScoring.domainOpeningRestriction(
                lookup, unrankedState, unranked, opening));

        BattleCombatant owner = combatant("OWNER", "Limitless", domain.id());
        BattleState state = new BattleState(owner, combatant("ENEMY", null));
        assertTrue(state.domainBattlefield().queueDeclaration(
            state, domain, owner, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(state, null, 1);
        assertEquals("Own Domain already active",
            SmartAIScoring.domainOpeningRestriction(lookup, state, owner, opening));
        DomainDefinition counter = counterDomain("SIMPLE");
        assertEquals("Own Domain already active",
            SmartAIScoring.domainOpeningRestriction(
                lookup(counter), state, owner, openingMove("SIMPLE_DOMAIN", counter.id())));
    }

    @Test
    void antiDomainAnswersAreRestrictedUntilAnEnemyDomainStands() {
        DomainDefinition counter = counterDomain("SIMPLE");
        DomainDefinitionLookup lookup = lookup(counter);
        Move answer = openingMove("SIMPLE_DOMAIN", counter.id());
        BattleCombatant defender = combatant("DEFENDER", null);
        BattleCombatant attacker = combatant(
            "ATTACKER", "Limitless", ordinaryDomain("VOID", "Limitless").id());
        BattleState state = new BattleState(attacker, defender);

        assertEquals("No enemy Domain to answer",
            SmartAIScoring.domainOpeningRestriction(lookup, state, defender, answer));

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, ordinaryDomain("VOID", "Limitless"), attacker, List.of(), 1).accepted());
        state.domainBattlefield().resolveDeclarations(state, null, 1);

        assertNull(SmartAIScoring.domainOpeningRestriction(
            lookup, state, defender, answer));
        assertEquals(SmartAIScoring.DOMAIN_ANSWER_VALUE,
            SmartAIScoring.domainMoveValue(lookup, state, defender, answer), 0.0);
    }

    @Test
    void restrictedOpeningsArePrunedAndValuedOpeningsLeadThePlan() {
        DomainDefinition domain = ordinaryDomain("VOID", "Limitless");
        DomainDefinitionLookup lookup = lookup(domain);
        BattleCombatant owner = combatant("OWNER", "Limitless", domain.id());
        BattleCombatant enemy = combatant("ENEMY", null);
        BattleState state = new BattleState(owner, enemy);
        Move opening = openingMove("OPEN_VOID", domain.id());

        // Restricted: a character that has not unlocked the Domain.
        BattleCombatant unranked = combatant("UNRANKED", "Limitless");
        BattlePlan wasted = new BattlePlan(owner.getMaxApBar(), owner.getCurrentCe());
        wasted.place(opening, 1, 0);
        wasted.place(basicAttackMove(), 4, 0);
        SmartAIScoring.pruneRestrictedDomainOpenings(lookup, state, unranked, wasted);
        assertTrue(wasted.allSegments().stream()
            .noneMatch(segment -> segment.getMove() == opening));

        // Eligible: the Domain opening is pulled ahead of the later attack.
        BattlePlan plan = new BattlePlan(owner.getMaxApBar(), owner.getCurrentCe());
        plan.place(basicAttackMove(), 1, 0);
        plan.place(opening, 5, 0).setTargets(List.of(enemy.getInstanceId()));
        SmartAIScoring.promoteDomainOpenings(lookup, state, owner, plan);

        assertEquals(2, plan.allSegments().size());
        var domainSegment = plan.allSegments().stream()
            .filter(segment -> segment.getMove() == opening).findFirst().orElseThrow();
        // The utility opening lives on its own timeline board, so promotion
        // moves its declaration to the very first tick of the round.
        assertEquals(1, domainSegment.getFireTick());
        assertEquals(List.of(enemy.getInstanceId()), domainSegment.getTargets());
    }

    private static DomainDefinitionLookup lookup(DomainDefinition... domains) {
        Map<String, DomainDefinition> catalog = new java.util.HashMap<>();
        for (DomainDefinition domain : domains) catalog.put(domain.id(), domain);
        return id -> Optional.ofNullable(catalog.get(id));
    }

    private static DomainDefinition ordinaryDomain(String id, String technique) {
        DomainData data = new DomainData();
        data.id = id;
        data.name = id;
        data.requiredTechniqueName = technique;
        data.durationRounds = 2;
        return data.toDomain();
    }

    private static DomainDefinition counterDomain(String id) {
        DomainData data = new DomainData();
        data.id = id;
        data.name = id;
        data.antiDomain = true;
        data.burnoutRounds = 0;
        data.burnoutTicks = 0;
        data.counterType = com.jjktbf.model.domain.DomainCounterType
            .SURE_HIT_NULLIFICATION.name();
        data.counterPotency = 10;
        data.counterUses = 1;
        data.durationRounds = 2;
        return data.toDomain();
    }

    private static Move basicAttackMove() {
        return basicAttack();
    }

    private static Move basicAttack() {
        return new Move.Builder("STRIKE")
            .name("Strike")
            .category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.PHYSICAL, MoveTag.ATTACK))
            .basePower(10)
            .neverMiss(true)
            .apCost(2)
            .unleashPoint(1)
            .build();
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
        String id, String technique, String... domainIds
    ) {
        List<String> accessible = domainIds == null
            ? List.of() : List.of(domainIds);

        SorcererCharacter character = new SorcererCharacter(
            id, id, stats(), technique, List.of());
        if (!accessible.isEmpty()) {
            return new BattleCombatant(
                character.withAccessibleDomains(accessible));
        }
        return new BattleCombatant(character);
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
}
