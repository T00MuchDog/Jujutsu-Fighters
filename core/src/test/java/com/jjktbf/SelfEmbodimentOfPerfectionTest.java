package com.jjktbf;

import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.CursedSpiritCharacter;
import com.jjktbf.model.character.ShikigamiCharacter;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.character.coded.IdleTransfigurationAbility;
import com.jjktbf.model.combat.AbilityActivationEngine;
import com.jjktbf.model.combat.AbilityTrigger;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.domain.DomainData;
import com.jjktbf.model.domain.DomainDefinition;
import com.jjktbf.model.domain.DomainDeliveryClass;
import com.jjktbf.model.progression.TechniqueMasteryResolver;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Domain Expansion: Self-Embodiment of Perfection — establishment, the shared
 * Soul Manipulation sure-hit on member entry and every tick, Simple Domain
 * nullification, upkeep/burnout economics, and the generic soul-damage
 * metadata that any Domain sure-hit can carry.
 */
class SelfEmbodimentOfPerfectionTest {

    // ── Establishment and sure-hit wiring ─────────────────────────────────────

    @Test
    void establishesCapturingEveryoneAndProtectingOnlyTheOwner() {
        BattleState state = establishedDomain(1.0, 1.0);
        var battlefield = state.domainBattlefield();

        assertEquals(1, battlefield.activeDomains().size());
        var instance = battlefield.activeDomains().get(0);
        assertTrue(instance.contains(mahito(state).getInstanceId()));
        assertTrue(instance.contains(enemy(state).getInstanceId()));
        assertTrue(instance.protects(mahito(state).getInstanceId()));
        assertFalse(instance.protects(enemy(state).getInstanceId()));
    }

    @Test
    void establishmentAttemptsSoulManipulationOnceThroughMemberEntryOnly() {
        // The initial capture funnels each captured enemy through the
        // ON_MEMBER_ENTER program: exactly one attempt, never an extra
        // ON_ESTABLISH-triggered one.
        List<CombatEvent> events = establish(0.0, 1.0).events;

        assertEquals(1, attempts(events).size());
        assertTrue(enemyOf(events).isDefeated()
            || attempts(events).get(0).getMessage().contains("resists"),
            "the single entry attempt must resolve");
    }

    @Test
    void theOwnerNeverReceivesTheSureHit() {
        List<CombatEvent> events = establish(0.0, 1.0).events;

        assertTrue(attempts(events).stream()
            .noneMatch(event -> event.getTarget() == null
                || "MAHITO".equals(event.getTarget().getCharacter().getName())));
    }

    @Test
    void everyActiveTickAttemptsSoulManipulationAgainstEnemyMembers() {
        Established established = establish(0.99, 0.99);
        BattleState state = established.state;

        // Resistance keeps every attempt failing, so each tick leaves exactly
        // one new stack event.
        for (int tick = 1; tick <= 3; tick++) {
            List<CombatEvent> events = state.domainBattlefield().processTick(
                state, established.engine::executeDomainEffect, tick);
            assertEquals(1, attempts(events).size(),
                "tick " + tick + " must attempt once");
        }
        assertFalse(established.enemy.isDefeated());
    }

    @Test
    void sureHitRowsDeliverAsEffectsAndCarrySoulDamage() {
        DomainDefinition definition = selfEmbodiment().toDomain();
        assertEquals(2, definition.sureHitEffects().size());
        for (AbilityEffectData row : definition.sureHitEffects()) {
            assertEquals(DomainDeliveryClass.EFFECT.name(), row.domainDeliveryClass);
            assertTrue(Boolean.TRUE.equals(row.soulDamage));
        }
    }

    @Test
    void soulDamageMetadataSurvivesCopyingAndMasteryResolution() {
        DomainData data = selfEmbodiment();
        for (AbilityEffectData row : data.sureHitEffects) {
            assertTrue(Boolean.TRUE.equals(row.soulDamage));
            assertTrue(Boolean.TRUE.equals(row.copy().soulDamage), "copy() must preserve the flag");
            assertTrue(Boolean.TRUE.equals(TechniqueMasteryResolver.resolve(row.copy(), 165).soulDamage),
                "mastery resolution must preserve the flag");
        }
        DomainData copied = data.copy();
        for (AbilityEffectData row : copied.sureHitEffects) {
            assertTrue(Boolean.TRUE.equals(row.soulDamage), "DomainData.copy() must preserve the flag");
        }
    }

    // ── Simple Domain and clashes ──────────────────────────────────────────────

    @Test
    void simpleDomainNullifiesTechniqueContactSureHits() {
        BattleCombatant mahito = mahito();
        BattleCombatant enemy = enemyWithSimpleDomain();
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine =
            new AbilityActivationEngine(new SequenceRandom(0.0));
        var battlefield = state.domainBattlefield();

        assertTrue(battlefield.queueDeclaration(
            state, selfEmbodiment().toDomain(), mahito, List.of(enemy), 1).accepted());
        assertTrue(battlefield.queueDeclaration(
            state, simpleDomain().toDomain(), enemy, List.of(mahito), 1).accepted());
        List<CombatEvent> events = battlefield.resolveDeclarations(
            state, engine::executeDomainEffect, 1);

        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_SURE_HIT_NEGATED),
            "the anti-Domain must negate the entry sure-hit");
        assertTrue(attempts(events).isEmpty(),
            "a negated sure-hit never reaches the soul resolver");
        assertFalse(enemy.isDefeated());
        assertEquals(2, battlefield.activeDomains().size());
    }

    @Test
    void aDomainClashSuppressesTheSureHit() {
        BattleCombatant mahito = mahito();
        BattleCombatant enemy = enemyWithOrdinaryDomain("ENEMY_DOMAIN");
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine =
            new AbilityActivationEngine(new SequenceRandom(0.99));
        var battlefield = state.domainBattlefield();

        assertTrue(battlefield.queueDeclaration(
            state, selfEmbodiment().toDomain(), mahito, List.of(enemy), 1).accepted());
        assertTrue(battlefield.queueDeclaration(
            state, ordinaryHostileDomain("ENEMY_DOMAIN").toDomain(), enemy,
            List.of(mahito), 1).accepted());
        battlefield.resolveDeclarations(state, engine::executeDomainEffect, 1);

        List<CombatEvent> tickEvents = battlefield.processTick(
            state, engine::executeDomainEffect, 2);
        assertTrue(tickEvents.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_SURE_HIT_NEGATED
                && event.getMessage() != null
                && event.getMessage().contains("clash")),
            "a clashing Domain's sure-hit stays suppressed");
    }

    // ── Economics: upkeep, duration, burnout ──────────────────────────────────

    @Test
    void upkeepDrainsCursedEnergyEachTick() {
        Established established = establish(0.99, 0.99);
        int before = established.mahito.getCurrentCe();
        established.state.domainBattlefield().processTick(
            established.state, established.engine::executeDomainEffect, 1);
        assertEquals(before - 35, established.mahito.getCurrentCe());
    }

    @Test
    void unpaidUpkeepCollapsesTheDomainAndBurnsOutTheTechnique() {
        Established established = establish(0.99, 0.99);
        established.mahito.drainCe(established.mahito.getCurrentCe());

        List<CombatEvent> events = established.state.domainBattlefield().processTick(
            established.state, established.engine::executeDomainEffect, 1);

        assertTrue(established.state.domainBattlefield().activeDomains().isEmpty());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED
                && "UPKEEP_FAILED".equals(event.getDomainCollapseReason())));
        assertTrue(established.mahito.isTechniqueLocked("Idle Transfiguration"),
            "collapse must burn out the technique");
    }

    @Test
    void expiringDurationCollapsesTheDomainWithBurnout() {
        Established established = establish(0.99, 0.99);
        var battlefield = established.state.domainBattlefield();
        List<CombatEvent> events = List.of();
        for (int round = 1; round <= 2; round++) {
            events = battlefield.processRoundEnd(
                established.state, established.engine::executeDomainEffect);
        }
        assertTrue(battlefield.activeDomains().isEmpty());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED));
        assertTrue(established.mahito.isTechniqueLocked("Idle Transfiguration"));
    }

    @Test
    void anOverwhelmingHitCollapsesTheDomainEvenThoughTheBodyRestores() {
        Established established = establish(0.99, 0.99);
        BattleCombatant mahito = established.mahito;
        int maxHp = mahito.getMaxHp();

        int overwhelming = maxHp * 9 / 100 + 1;
        mahito.receiveDamage(overwhelming);
        List<CombatEvent> collapseEvents = established.state.domainBattlefield()
            .onOwnerHitDamage(
                established.state, mahito, overwhelming,
                established.engine::executeDomainEffect, 2);
        assertTrue(established.state.domainBattlefield().activeDomains().isEmpty());
        assertTrue(collapseEvents.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DOMAIN_COLLAPSED));

        // Only after the collapse ruling does Maintaining the Soul restore the
        // bodily damage - the Domain is still gone.
        established.engine.process(established.state, AbilityTrigger.damage(
            established.enemy, mahito, overwhelming, false, 2));
        assertEquals(maxHp, mahito.getCurrentHp(),
            "the body restores fully after the ruling");
        assertTrue(established.state.domainBattlefield().activeDomains().isEmpty());
    }

    // ── Generic soul-damage Domain sure-hits vs. Maintaining the Soul ─────────

    @Test
    void aForeignSoulDamagingSureHitBypassesMaintainingTheSoul() {
        ForeignDomain domain = foreignDomain(true);
        int hpBefore = domain.mahito.getCurrentHp();
        int ceBefore = domain.mahito.getCurrentCe();

        List<CombatEvent> events = domain.state.domainBattlefield().processTick(
            domain.state, domain.engine::executeDomainEffect, 1);

        assertEquals(hpBefore - 40, domain.mahito.getCurrentHp(),
            "soul damage from a Domain must not be restored");
        assertEquals(ceBefore, domain.mahito.getCurrentCe(),
            "no restoration means no CE cost");
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.DAMAGE_DEALT
                && event.getTarget() == domain.mahito));
    }

    @Test
    void ordinaryForeignSureHitDamageStillRestores() {
        ForeignDomain domain = foreignDomain(false);
        int hpBefore = domain.mahito.getCurrentHp();
        int ceBefore = domain.mahito.getCurrentCe();

        domain.state.domainBattlefield().processTick(
            domain.state, domain.engine::executeDomainEffect, 1);

        assertEquals(hpBefore, domain.mahito.getCurrentHp(),
            "ordinary Domain damage interacts with the restoration normally");
        assertTrue(domain.mahito.getCurrentCe() < ceBefore,
            "the restoration pays its CE bracket");
    }

    @Test
    void alliedSummonEntrantsAreNotSoulManipulated() {
        Established established = establish(0.0, 1.0);
        BattleState state = established.state;

        // Mirror the resolver's materialization path: drain the pending summon,
        // then notify the battlefield that the combatant entered.
        state.enqueueSummon(established.mahito, "000022");
        List<BattleCombatant> materialized = state.drainPendingSummons(characterId ->
            java.util.Optional.of(new ShikigamiCharacter("000022", "TRANSFIGURED",
                new CharacterStats.Builder().build(), null, List.of(), List.of(),
                com.jjktbf.model.character.Equipment.NONE)));
        assertEquals(1, materialized.size());
        BattleCombatant summon = materialized.get(0);
        List<CombatEvent> events = state.domainBattlefield().onCombatantEntered(
            state, summon, established.engine::executeDomainEffect, 2);

        assertTrue(state.domainBattlefield().activeDomains().stream()
            .anyMatch(instance -> instance.contains(summon.getInstanceId())),
            "FOLLOW_SUMMONER admits the summoner's summon as a member");
        assertTrue(attempts(events).stream().noneMatch(event ->
                event.getTarget() != null && event.getTarget().isSummon()),
            "the coded resolver must refuse allied targets even though the row fires");
        assertFalse(summon.isDefeated());
    }

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private record Established(
        BattleState state,
        AbilityActivationEngine engine,
        BattleCombatant mahito,
        BattleCombatant enemy,
        List<CombatEvent> events
    ) { }

    private static BattleCombatant mahito(BattleState state) {
        return state.activeCombatants().stream()
            .filter(combatant -> "Mahito".equals(combatant.getCharacter().getName()))
            .findFirst().orElseThrow();
    }

    private static BattleCombatant enemy(BattleState state) {
        return state.activeCombatants().stream()
            .filter(combatant -> "ENEMY".equals(combatant.getCharacter().getName()))
            .findFirst().orElseThrow();
    }

    private static BattleCombatant enemyOf(List<CombatEvent> events) {
        return events.stream().map(CombatEvent::getTarget)
            .filter(target -> target != null
                && "ENEMY".equals(target.getCharacter().getName()))
            .findFirst().orElseThrow();
    }

    private static List<CombatEvent> attempts(List<CombatEvent> events) {
        return events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.ABILITY_ACTIVATED)
            .filter(event -> event.getMessage() != null
                && event.getMessage().contains("Soul Manipulation activates"))
            .toList();
    }

    private static BattleState establishedDomain(double entryRoll, double tickRoll) {
        return establish(entryRoll, tickRoll).state;
    }

    private static Established establish(double entryRoll, double tickRoll) {
        BattleCombatant mahito = mahito();
        BattleCombatant enemy = enemy();
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine =
            new AbilityActivationEngine(new SequenceRandom(tickRoll, entryRoll));
        var battlefield = state.domainBattlefield();
        assertTrue(battlefield.queueDeclaration(
            state, selfEmbodiment().toDomain(), mahito, List.of(enemy), 1).accepted());
        List<CombatEvent> events =
            battlefield.resolveDeclarations(state, engine::executeDomainEffect, 1);
        return new Established(state, engine, mahito, enemy, events);
    }

    /** The authored domain, mirrored in code so the test owns its behaviour. */
    private static DomainData selfEmbodiment() {
        DomainData data = new DomainData();
        data.id = "000001";
        data.name = "Self-Embodiment of Perfection";
        data.requiredTechniqueName = "Idle Transfiguration";
        data.topology = "CLOSED";
        data.capturePolicy = "EVERYONE";
        data.entrantPolicy = "FOLLOW_SUMMONER";
        data.protectionPolicy = "OWNER";
        data.durationRounds = 2;
        data.ceUpkeepPerTick = 35.0;
        data.internalBarrierIntegrity = 90;
        data.clashValue = 90;
        data.burnoutRounds = 2;
        data.sureHitEffects = List.of(
            soulManipulationRow("ON_MEMBER_ENTER", "ENTERING_MEMBER"),
            soulManipulationRow("EACH_TICK", "ENEMY_MEMBERS"));
        return data;
    }

    private static AbilityEffectData soulManipulationRow(String trigger, String audience) {
        AbilityEffectData row =
            AbilityEffectType.CODED_MOVE_ACTION.createDefault();
        row.effectId = "effect-000000";
        row.type = AbilityEffectType.CODED_MOVE_ACTION.name();
        row.codedAbilityKey = IdleTransfigurationAbility.KEY;
        row.codedAction = IdleTransfigurationAbility.ACTION_SOUL_MANIPULATION;
        row.codedParameters = null;
        row.codedTarget = null;
        row.codedStackCount = null;
        row.target = "ENEMY";
        row.soulDamage = true;
        row.domainTrigger = trigger;
        row.domainAudience = audience;
        row.domainDeliveryClass = "EFFECT";
        row.domainIntervalTicks = 1;
        row.domainActivationChanceEnabled = false;
        row.domainActivationChance = 1.0;
        return row;
    }

    private static DomainData simpleDomain() {
        DomainData data = new DomainData();
        data.id = "000000";
        data.name = "Simple Domain";
        data.antiDomain = true;
        data.topology = "INCOMPLETE";
        data.capturePolicy = "SELECTED_TARGETS";
        data.entrantPolicy = "SNAPSHOT";
        data.protectionPolicy = "OWNER";
        data.durationRounds = -1;
        data.burnoutRounds = 0;
        data.ceUpkeepPerTick = 0.0;
        data.internalBarrierIntegrity = DomainData.DEFAULT_ANTI_DOMAIN_INTEGRITY;
        data.counterType = "TECHNIQUE_CONTACT_NULLIFICATION";
        data.counterPotency = 100;
        data.counterUses = -1;
        return data;
    }

    private static DomainData ordinaryHostileDomain(String id) {
        DomainData data = new DomainData();
        data.id = id;
        data.name = id;
        data.requiredTechniqueName = "Enemy Technique";
        data.durationRounds = 2;
        data.ceUpkeepPerTick = 0.0;
        data.clashValue = 200;
        return data;
    }

    private record ForeignDomain(
        BattleState state,
        AbilityActivationEngine engine,
        BattleCombatant mahito
    ) { }

    /** An enemy-owned Domain whose sure-hit is flat direct damage, optionally soul damage. */
    private static ForeignDomain foreignDomain(boolean soul) {
        BattleCombatant enemy = new BattleCombatant(new SorcererCharacter(
            "ENEMY", "ENEMY", stats(80), "Enemy Technique", List.of())
            .withAccessibleDomains(List.of("FOREIGN")));
        BattleCombatant mahito = mahito();
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine =
            new AbilityActivationEngine(new SequenceRandom(0.99));

        DomainData data = new DomainData();
        data.id = "FOREIGN";
        data.name = "Foreign Domain";
        data.requiredTechniqueName = "Enemy Technique";
        data.durationRounds = 2;
        data.ceUpkeepPerTick = 0.0;
        AbilityEffectData damage = AbilityEffectType.DEAL_DIRECT_DAMAGE.createDefault();
        damage.effectId = "effect-000000";
        damage.valueMode = "FLAT";
        damage.intValue = 40;
        damage.target = "ENEMY";
        damage.soulDamage = soul;
        damage.domainTrigger = "EACH_TICK";
        damage.domainAudience = "ENEMY_MEMBERS";
        damage.domainDeliveryClass = "EFFECT";
        damage.domainIntervalTicks = 1;
        data.sureHitEffects = List.of(damage);

        assertTrue(state.domainBattlefield().queueDeclaration(
            state, data.toDomain(), enemy, List.of(mahito), 1).accepted());
        state.domainBattlefield().resolveDeclarations(
            state, engine::executeDomainEffect, 1);
        return new ForeignDomain(state, engine, mahito);
    }

    private static BattleCombatant mahito() {
        Character character = new CursedSpiritCharacter(
            "MAHITO", "Mahito", stats(165), "Idle Transfiguration",
            List.of(), List.of(maintainingTheSoul(), soulManipulation()))
            .withAccessibleDomains(List.of("000001"));
        return new BattleCombatant(character);
    }

    private static BattleCombatant enemy() {
        return new BattleCombatant(new SorcererCharacter(
            "ENEMY", "ENEMY", stats(80), null, List.of()));
    }

    private static BattleCombatant enemyWithSimpleDomain() {
        return new BattleCombatant(new SorcererCharacter(
            "ENEMY", "ENEMY", stats(80), null, List.of())
            .withAccessibleDomains(List.of("000000")));
    }

    private static BattleCombatant enemyWithOrdinaryDomain(String id) {
        return new BattleCombatant(new SorcererCharacter(
            "ENEMY", "ENEMY", stats(80), "Enemy Technique", List.of())
            .withAccessibleDomains(List.of(id)));
    }

    private static Ability maintainingTheSoul() {
        return codedPassive("MAINTAIN", IdleTransfigurationAbility.MAINTAINING_THE_SOUL);
    }

    private static Ability soulManipulation() {
        return codedPassive("SOUL_MANIP", IdleTransfigurationAbility.SOUL_MANIPULATION);
    }

    private static Ability codedPassive(String id, String feature) {
        AbilityData ability = new AbilityData();
        ability.id = id;
        ability.name = id;
        ability.category = "PASSIVE";
        ability.sourceType = "TECHNIQUE";
        ability.sourceValue = "Idle Transfiguration";
        AbilityEffectData effect = AbilityEffectType.CODED.createDefault();
        effect.effectId = "effect-000000";
        effect.codedAbilityKey = IdleTransfigurationAbility.KEY;
        effect.codedFeature = feature;
        effect.codedParameters = null;
        ability.effects = List.of(effect);
        return new Ability(ability);
    }

    private static CharacterStats stats(int ctm) {
        return new CharacterStats.Builder()
            .vitality(80).strength(80).durability(80).speed(80)
            .cursedEnergyReserves(80).cursedEnergyEfficiency(80)
            .cursedEnergyOutput(80).jujutsuSkill(80)
            .combatAbility(80).cursedTechniqueMastery(ctm)
            .build();
    }

    /** Deterministic scripted rolls, consumed in resolution order. */
    private static final class SequenceRandom implements RandomSource {
        private final ArrayDeque<Double> values;
        private final double fallback;

        SequenceRandom(double... rolls) {
            this.values = new ArrayDeque<>();
            for (double roll : rolls) values.addLast(roll);
            this.fallback = 0.99;
        }

        @Override public int nextInt(int bound) { return 0; }
        @Override public double nextDouble() {
            return values.isEmpty() ? fallback : values.removeFirst();
        }
        @Override public boolean nextBoolean() { return false; }
    }
}
