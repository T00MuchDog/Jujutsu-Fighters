package com.jjktbf;

import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.AbilityConditionType;
import com.jjktbf.model.character.AbilityConditionRuleData;
import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectTarget;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.CursedSpiritCharacter;
import com.jjktbf.model.character.ShikigamiCharacter;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.character.coded.IdleTransfigurationAbility;
import com.jjktbf.model.character.coded.MiraclesAbility;
import com.jjktbf.model.combat.AbilityActivationEngine;
import com.jjktbf.model.combat.AbilityTrigger;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.move.StatusEffectType;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maintaining the Soul, Malleable Body and Soul Manipulation, expressed
 * through the shared coded runtime. Everything is authored in code; nothing
 * depends on the bundled data files.
 */
class IdleTransfigurationAbilityTest {

    // ── Maintaining the Soul ───────────────────────────────────────────────────

    @Test
    void maintainingTheSoulDrainsTwoCeEveryResolutionTick() {
        BattleCombatant mahito = mahito(maintainingTheSoul());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0));

        int before = mahito.getCurrentCe();
        List<CombatEvent> events = engine.process(state, AbilityTrigger.tick(1));

        assertEquals(before - 2, mahito.getCurrentCe());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.CE_DRAINED && event.getTarget() == mahito));
    }

    @Test
    void everyRestorationCeBracketPaysItsOwnCost() {
        assertRestorationCost(1, 10);
        assertRestorationCost(10, 10);
        assertRestorationCost(11, 30);
        assertRestorationCost(30, 30);
        assertRestorationCost(31, 50);
        assertRestorationCost(50, 50);
        assertRestorationCost(51, 100);
        assertRestorationCost(100, 100);
        assertRestorationCost(101, 300);
        assertRestorationCost(150, 300);
    }

    private void assertRestorationCost(int damage, int expectedCost) {
        BattleCombatant mahito = mahito(maintainingTheSoul());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0));

        int hpBefore = mahito.getCurrentHp();
        int ceBefore = mahito.getCurrentCe();
        mahito.receiveDamage(damage);
        engine.process(state, AbilityTrigger.damage(enemy, mahito, damage, false, 1));

        assertEquals(hpBefore, mahito.getCurrentHp(),
            "damage " + damage + " should be fully restored");
        assertEquals(ceBefore - expectedCost, mahito.getCurrentCe(),
            "damage " + damage + " should cost " + expectedCost + " CE");
    }

    @Test
    void normalDamageIsRestoredExactlyAndPreviousSoulDamageIsNot() {
        BattleCombatant mahito = mahito(maintainingTheSoul());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0));

        int maxHp = mahito.getMaxHp();
        // Soul damage first: it must never be restored.
        mahito.receiveDamage(120);
        engine.process(state, AbilityTrigger.damage(enemy, mahito, 120, true, 1));
        int afterSoulDamage = mahito.getCurrentHp();
        assertEquals(maxHp - 120, afterSoulDamage);
        assertTrue(engine.process(state, AbilityTrigger.damage(enemy, mahito, 120, true, 1))
            .stream().noneMatch(event -> event.getType() == CombatEvent.Type.HP_RESTORED));

        // Ordinary damage afterwards: restored exactly, not up to maximum.
        mahito.receiveDamage(40);
        engine.process(state, AbilityTrigger.damage(enemy, mahito, 40, false, 2));
        assertEquals(afterSoulDamage, mahito.getCurrentHp(),
            "restoration must return to the post-soul-damage value, not to maximum HP");
    }

    @Test
    void insufficientCePreventsRestoration() {
        BattleCombatant mahito = mahito(maintainingTheSoul());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0));

        // 40 damage sits in the 50-CE bracket; leave only 30 CE.
        mahito.drainCe(mahito.getCurrentCe() - 30);
        mahito.receiveDamage(40);
        int hpAfterDamage = mahito.getCurrentHp();
        List<CombatEvent> events =
            engine.process(state, AbilityTrigger.damage(enemy, mahito, 40, false, 1));

        assertEquals(hpAfterDamage, mahito.getCurrentHp(), "no restoration without CE");
        assertEquals(30, mahito.getCurrentCe());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.EFFECT_FAILED && event.getTarget() == mahito));
    }

    @Test
    void singleInstanceAboveMaximumHpDefeatsWithoutRestoration() {
        BattleCombatant mahito = mahito(maintainingTheSoul());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0));

        int ceBefore = mahito.getCurrentCe();
        int overkill = mahito.getMaxHp() + 500;
        mahito.receiveDamage(overkill);
        List<CombatEvent> events =
            engine.process(state, AbilityTrigger.damage(enemy, mahito, overkill, false, 1));

        assertTrue(mahito.isDefeated());
        assertEquals(0, mahito.getCurrentHp());
        assertEquals(ceBefore, mahito.getCurrentCe(), "no CE may be spent on a lethal instance");
        assertTrue(events.stream().noneMatch(event ->
            event.getType() == CombatEvent.Type.HP_RESTORED));
    }

    @Test
    void statusDamageInstancesRestoreIndependently() {
        BattleCombatant mahito = mahito(maintainingTheSoul());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0));

        int hpBefore = mahito.getCurrentHp();
        int ceBefore = mahito.getCurrentCe();
        // Mirrors the resolver's damaging-status path: fractional max-HP damage,
        // then the DAMAGE trigger, once per instance per tick. BURNED is used
        // because it deals damage without rescaling any stat.
        mahito.addStatusEffect(
            new StatusEffect(StatusEffectType.BURNED, 2, 0, 0, 0.0),
            (BattleState.Phase) null);
        for (int tick = 1; tick <= 3; tick++) {
            int applied = mahito.accrueStatusDamageForTick(
                StatusEffectType.BURNED, 0.01);
            assertTrue(applied > 0);
            mahito.receiveDamage(applied);
            engine.process(state, AbilityTrigger.damage(mahito, mahito, applied, false, tick));
            assertEquals(hpBefore, mahito.getCurrentHp(), "tick " + tick);
        }
        assertTrue(mahito.getCurrentCe() < ceBefore);
    }

    @Test
    void restorationRunsInsideOrdinaryRoundResolution() {
        BattleCombatant mahito = mahito(maintainingTheSoul());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        Move strike = neverMissMelee("STRIKE", 30);
        place(enemy, strike, List.of(mahito));

        int hpBefore = mahito.getCurrentHp();
        int ceBefore = mahito.getCurrentCe();
        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.5))
            .resolveRound(state);

        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.HP_RESTORED && event.getTarget() == mahito));
        assertEquals(hpBefore, mahito.getCurrentHp(),
            "the hit's damage is fully restored by the end of the round");
        assertTrue(mahito.getCurrentCe() < ceBefore);
    }

    // ── Malleable Body ─────────────────────────────────────────────────────────

    @Test
    void malleableBodyShedsBodilyInjuryWhenRestorationSucceeds() {
        BattleCombatant mahito = mahito(maintainingTheSoul(), malleableBody());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0));

        mahito.addStatusEffect(
            new StatusEffect(StatusEffectType.BURNED, 2, 0, 0, 0.0),
            (BattleState.Phase) null);
        mahito.addStatusEffect(
            new StatusEffect(StatusEffectType.STAGGER, 0, 2, 0, 0.0),
            (BattleState.Phase) null);
        mahito.addStatusEffect(
            new StatusEffect(StatusEffectType.CURSED_ENERGY_PARASITE, 2, 0, 0, 0.0),
            (BattleState.Phase) null);

        mahito.receiveDamage(25);
        engine.process(state, AbilityTrigger.damage(enemy, mahito, 25, false, 1));

        assertFalse(mahito.hasEffect(StatusEffectType.BURNED),
            "bodily injury is shed with the restored damage");
        assertTrue(mahito.hasEffect(StatusEffectType.STAGGER),
            "control statuses are unrelated to anatomy");
        assertTrue(mahito.hasEffect(StatusEffectType.CURSED_ENERGY_PARASITE),
            "cursed-energy disruption is not a bodily injury");
    }

    @Test
    void withoutMalleableBodyBodilyInjuryPersists() {
        BattleCombatant mahito = mahito(maintainingTheSoul());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0));

        mahito.addStatusEffect(
            new StatusEffect(StatusEffectType.BURNED, 2, 0, 0, 0.0),
            (BattleState.Phase) null);
        mahito.receiveDamage(25);
        engine.process(state, AbilityTrigger.damage(enemy, mahito, 25, false, 1));

        assertTrue(mahito.hasEffect(StatusEffectType.BURNED));
    }

    @Test
    void malleableBodyDoesNotCleanseWhenRestorationCannotAfford() {
        BattleCombatant mahito = mahito(maintainingTheSoul(), malleableBody());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0));

        mahito.addStatusEffect(
            new StatusEffect(StatusEffectType.BURNED, 2, 0, 0, 0.0),
            (BattleState.Phase) null);
        mahito.drainCe(mahito.getCurrentCe());
        mahito.receiveDamage(25);
        engine.process(state, AbilityTrigger.damage(enemy, mahito, 25, false, 1));

        assertTrue(mahito.hasEffect(StatusEffectType.BURNED),
            "damage that is not restored drags its injury along");
    }

    // ── Soul Manipulation: passive melee proc ─────────────────────────────────

    @Test
    void meleeProcRollsFivePercentOnSuccessfulHits() {
        // No proc: the roll lands above 5%.
        runProcScenario(new SequenceRandom(0.5, 0.5, 0.0), false, false);
        // Proc + successful attempt.
        runProcScenario(new SequenceRandom(0.5, 0.04, 0.04), true, true);
        // Proc + resisted attempt.
        runProcScenario(new SequenceRandom(0.5, 0.04, 0.06), true, false);
    }

    private void runProcScenario(RandomSource rng, boolean expectProc, boolean expectSuccess) {
        // CTM 80 against an 80-resistance target pins the attempt chance at
        // exactly 5%, so the scripted rolls address the authored percentages.
        BattleCombatant mahito = mahitoWithCtm(80);
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        Move strike = neverMissMelee("STRIKE", 20);
        place(mahito, strike, List.of(enemy));

        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(rng).resolveRound(state);

        List<CombatEvent> attempts = attempts(events);
        if (!expectProc) {
            assertTrue(attempts.isEmpty(), "no attempt should happen above the proc chance");
            assertFalse(enemy.isDefeated());
            return;
        }
        assertEquals(1, attempts.size());
        if (expectSuccess) {
            assertTrue(enemy.isDefeated());
            assertEquals("Mahito's Soul Manipulation activates. TARGET fails to resist "
                + "and is transformed into something inhuman.", attempts.get(0).getMessage());
        } else {
            assertFalse(enemy.isDefeated());
            assertEquals("Mahito's Soul Manipulation activates. TARGET resists. Mahito "
                + "gains a deeper understanding of TARGET's soul.", attempts.get(0).getMessage());
        }
    }

    @Test
    void rangedHitsNeverProcThePassiveSoulAttempt() {
        BattleCombatant mahito = mahito(soulManipulation());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        Move beam = neverMissRanged("BEAM", 20);
        place(mahito, beam, List.of(enemy));

        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.0)).resolveRound(state);

        assertTrue(attempts(events).isEmpty());
        assertFalse(enemy.isDefeated());
    }

    // ── Soul Manipulation: stacks and scaling ──────────────────────────────────

    @Test
    void failedAttemptsStackAndRaiseLaterSuccessProbability() {
        BattleCombatant mahito = mahito(soulManipulation());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        Move strike = neverMissMelee("STRIKE", 20);

        // Base chance is 5% (CTM 80, resisted by 80 CE). Two failures stack
        // +12% each; 0.15 then succeeds where it would have failed unstacked.
        for (int round = 1; round <= 2; round++) {
            place(mahito, strike, List.of(enemy));
            state.transitionTo(BattleState.Phase.RESOLUTION);
            List<CombatEvent> events =
                new CombatResolver(new SequenceRandom(0.5, 0.04, 0.9)).resolveRound(state);
            assertEquals(1, attempts(events).size());
            assertFalse(enemy.isDefeated());
        }

        place(mahito, strike, List.of(enemy));
        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events =
            new CombatResolver(new SequenceRandom(0.5, 0.04, 0.15)).resolveRound(state);
        assertTrue(enemy.isDefeated(), "5% + 2 stacks = 29% beats a 0.15 roll");
    }

    @Test
    void cursedTechniqueMasteryRaisesTheAttemptChance() {
        int weak = reportedChancePercent(80);
        int strong = reportedChancePercent(165);
        assertTrue(strong > weak,
            "CTM 165 (" + strong + "%) must out-scale CTM 80 (" + weak + "%)");
    }

    @Test
    void resistanceScalesWithCurrentCeAndIsCappedByCeOutput() {
        int fullPool = reportedChancePercent(fighterWithPool(80, 640, 80));
        int drained = reportedChancePercent(fighterWithPool(80, 20, 80));
        assertTrue(drained > fullPool, "less current CE means less resistance");

        int lowOutput = reportedChancePercent(fighterWithPool(80, 640, 20));
        int highOutput = reportedChancePercent(fighterWithPool(80, 640, 200));
        assertTrue(lowOutput > highOutput,
            "cursed energy output caps the resisting pool");
        assertEquals(reportedChancePercent(fighterWithPool(80, 20, 200)), lowOutput,
            "output caps the resistance at the lower of the two");
    }

    private int reportedChancePercent(BattleCombatant target) {
        BattleCombatant mahito = mahito(soulManipulation());
        BattleState state = new BattleState(mahito, target);
        Move strike = neverMissMelee("STRIKE", 20);
        place(mahito, strike, List.of(target));

        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.5, 0.04, 0.99))
            .resolveRound(state);
        List<CombatEvent> attempts = attempts(events);
        assertEquals(1, attempts.size());
        return attempts.get(0).getIntValue();
    }

    private int reportedChancePercent(int mahitoCtm) {
        BattleCombatant mahito = mahitoWithCtm(mahitoCtm);
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        Move strike = neverMissMelee("STRIKE", 20);
        place(mahito, strike, List.of(enemy));

        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.5, 0.04, 0.99))
            .resolveRound(state);
        return attempts(events).get(0).getIntValue();
    }

    @Test
    void attemptsAreDeterministicUnderASeededSource() {
        boolean first = runSeededAttempt(1234L);
        boolean second = runSeededAttempt(1234L);
        assertEquals(first, second);
        // A different seed is still a legal outcome; only equality of the same
        // seed is guaranteed.
        runSeededAttempt(4321L);
    }

    private boolean runSeededAttempt(long seed) {
        BattleCombatant mahito = mahito(soulManipulation());
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        Move strike = neverMissMelee("STRIKE", 20);
        place(mahito, strike, List.of(enemy));
        state.transitionTo(BattleState.Phase.RESOLUTION);
        new CombatResolver(new SeededRandomSource(seed)).resolveRound(state);
        return enemy.isDefeated();
    }

    // ── Soul Manipulation: dedicated coded move row ────────────────────────────

    @Test
    void dedicatedIdleTransfigurationMoveGuaranteesAnAttempt() {
        // Success: the single roll (the row's attempt) defeats the target. The
        // move is a known move so the coded row instantiates the runtime.
        Move touch = idleTransfigurationTouch();
        BattleCombatant mahito = mahitoKnowing(touch);
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        place(mahito, touch, List.of(enemy));

        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.5, 0.04))
            .resolveRound(state);

        assertEquals(1, attempts(events).size());
        assertTrue(enemy.isDefeated());
    }

    @Test
    void dedicatedIdleTransfigurationMoveFailureStacks() {
        Move touch = idleTransfigurationTouch();
        BattleCombatant mahito = mahitoKnowing(touch);
        BattleCombatant enemy = fighter("ENEMY");
        BattleState state = new BattleState(mahito, enemy);
        place(mahito, touch, List.of(enemy));

        state.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.5, 0.9))
            .resolveRound(state);

        assertEquals(1, attempts(events).size());
        assertFalse(enemy.isDefeated());
        assertEquals("Mahito's Soul Manipulation activates. TARGET resists. Mahito "
            + "gains a deeper understanding of TARGET's soul.",
            attempts(events).get(0).getMessage());
    }

    @Test
    void soulManipulationNeverTargetsAllies() {
        BattleCombatant mahito = mahito(soulManipulation());
        BattleCombatant ally = new BattleCombatant(new SorcererCharacter(
            "ALLY", "ALLY", stats(80), null, List.of()));
        BattleState state = new BattleState(
            BattleState.teamOfFighters(
                com.jjktbf.model.combat.BattleTeamId.PLAYER, List.of(mahito, ally)),
            BattleState.teamOfFighters(
                com.jjktbf.model.combat.BattleTeamId.ENEMY, List.of(fighter("ENEMY"))));
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0));

        StatusEffect coded = StatusEffect.coded(
            IdleTransfigurationAbility.KEY,
            IdleTransfigurationAbility.ACTION_SOUL_MANIPULATION,
            null, null, Map.of(), null);
        List<CombatEvent> events = mahito.getCodedAbilities()
            .onEffectFired(state, coded, mahito, ally, 1, new SequenceRandom(0.0));

        assertTrue(events.isEmpty());
        assertFalse(ally.isDefeated());
    }

    @Test
    void runtimeExposesACapabilityStateForMultiplayerSnapshots() {
        BattleCombatant mahito = mahito(soulManipulation(), maintainingTheSoul());
        Optional<CodedAbilityState> state = mahito.getCodedAbilities()
            .state(IdleTransfigurationAbility.KEY);
        assertTrue(state.isPresent());
        assertEquals("Idle Transfiguration", state.get().displayName());
    }

    @Test
    void soulAwareMeleeAndRangedHitsBypassRestorationWithoutChangingAuthoredHits() {
        MoveEffectData damageRow = AbilityEffectType.DEAL_DIRECT_DAMAGE.createDefaultMoveEffect();
        damageRow.effectId = "damage";
        damageRow.trigger = MoveEffectTrigger.ON_HIT.name();
        damageRow.target = AbilityEffectTarget.ENEMY.name();
        damageRow.intValue = 30;
        Move effectAttack = new Move.Builder("EFFECT_ATTACK").name("Effect Attack")
            .category(MoveCategory.CURSED_ENERGY).tags(Set.of(MoveTag.ATTACK, MoveTag.CURSED_ENERGY))
            .basePower(0).neverMiss(true).apCost(1).unleashPoint(1)
            .effects(List.of(damageRow)).build();
        for (Move strike : List.of(neverMissMelee("MELEE", 30), neverMissRanged("RANGED", 30), effectAttack)) {
            AbilityData aware = new AbilityData();
            aware.id = "AWARE";
            aware.name = "Aware";
            aware.category = "PASSIVE";
            aware.sourceType = "CHARACTER";
            aware.effects = List.of(AbilityEffectType.SOUL_AWARE_ATTACKS.createDefault());
            BattleCombatant attacker = new BattleCombatant(new SorcererCharacter(
                "ATTACKER", "Attacker", stats(80), null, List.of(strike), List.of(new Ability(aware))));
            BattleCombatant target = mahito(maintainingTheSoul());
            BattleState state = new BattleState(attacker, target);
            place(attacker, strike, List.of(target));
            state.transitionTo(BattleState.Phase.RESOLUTION);

            List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.5)).resolveRound(state);

            assertTrue(target.getCurrentHp() < target.getMaxHp());
            assertTrue(events.stream().noneMatch(e -> e.getType() == CombatEvent.Type.HP_RESTORED));
            assertFalse(strike.getHitComponents().get(0).isSoulDamage(), "authored move is unchanged");
        }
    }

    @Test
    void explicitlySoulDamagingHitBypassesRestorationWithoutAwareness() {
        Move strike = new Move.Builder("SOUL_STRIKE").name("Soul Strike")
            .category(MoveCategory.PHYSICAL).tags(Set.of(MoveTag.ATTACK, MoveTag.PHYSICAL))
            .basePower(30).neverMiss(true).apCost(1).unleashPoint(1)
            .hitComponents(List.of(new HitComponent(
                30, Set.of(MoveTag.PHYSICAL, MoveTag.MELEE), 0, false, true,
                1.0, List.of(), false, 0, true))).build();
        BattleCombatant attacker = fighter("ATTACKER");
        BattleCombatant target = mahito(maintainingTheSoul());
        BattleState state = new BattleState(attacker, target);
        place(attacker, strike, List.of(target));
        state.transitionTo(BattleState.Phase.RESOLUTION);

        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.5)).resolveRound(state);

        assertFalse(attacker.hasSoulAwareAttacks());
        assertTrue(target.getCurrentHp() < target.getMaxHp());
        assertTrue(events.stream().noneMatch(e -> e.getType() == CombatEvent.Type.HP_RESTORED));
    }

    @Test
    void passiveSoulAttemptsRetaliateEveryTimeAndOnlyAgainstTheManipulator() {
        BattleCombatant attacker = mahito(soulManipulation(), maintainingTheSoul());
        BattleCombatant bystander = fighter("BYSTANDER");
        BattleCombatant vessel = retaliatingFighter(0.25);
        BattleState state = new BattleState(
            BattleState.teamOfFighters(com.jjktbf.model.combat.BattleTeamId.PLAYER,
                List.of(attacker, bystander)),
            BattleState.teamOfFighters(com.jjktbf.model.combat.BattleTeamId.ENEMY, List.of(vessel)));
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0, 0.0));
        Move strike = neverMissMelee("STRIKE", 10);
        int damage = (int) Math.round(attacker.getMaxHp() * 0.25);
        int ceBefore = attacker.getCurrentCe();

        for (int tick = 1; tick <= 2; tick++) {
            List<CombatEvent> events = engine.process(state, AbilityTrigger.attackHit(
                attacker, vessel, strike, strike.getHitComponents().get(0), tick));
            assertEquals(attacker.getMaxHp() - tick * damage, attacker.getCurrentHp());
            assertEquals(1, events.stream().filter(e -> e.getType() == CombatEvent.Type.DAMAGE_DEALT
                && e.getSource() == vessel && e.getTarget() == attacker).count());
        }
        assertEquals(ceBefore, attacker.getCurrentCe(), "soul retaliation is not restored");
        assertEquals(bystander.getMaxHp(), bystander.getCurrentHp());
    }

    @Test
    void attacksThatDoNotProcSoulManipulationDoNotRetaliate() {
        BattleCombatant attacker = mahito(soulManipulation());
        BattleCombatant vessel = retaliatingFighter(0.75);
        BattleState state = new BattleState(attacker, vessel);
        Move strike = neverMissMelee("STRIKE", 10);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.99));

        engine.process(state, AbilityTrigger.attackHit(
            attacker, vessel, strike, strike.getHitComponents().get(0), 1));

        assertEquals(attacker.getMaxHp(), attacker.getCurrentHp());
    }

    @Test
    void codedMoveRetaliationNegatesSoulManipulationEvenWhenManipulatorSurvives() {
        Move touch = idleTransfigurationTouch();
        BattleCombatant attacker = mahitoKnowing(touch);
        BattleCombatant vessel = retaliatingFighter(0.75);
        BattleState state = new BattleState(attacker, vessel);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0));

        List<CombatEvent> events = engine.processMoveEffects(
            state, attacker, vessel, touch, MoveEffectTrigger.ON_HIT, 0, 1);

        assertEquals(attacker.getMaxHp() - (int) Math.round(attacker.getMaxHp() * 0.75),
            attacker.getCurrentHp());
        assertFalse(vessel.isDefeated());
        assertEquals(vessel.getMaxHp(), vessel.getCurrentHp());
        assertTrue(attempts(events).isEmpty());
        assertTrue(events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.SOUL_MANIPULATION_NEGATED));
    }

    @Test
    void domainSoulEffectRetaliatesAndLethalRetaliationStopsTheAttempt() {
        Move touch = idleTransfigurationTouch();
        BattleCombatant attacker = mahitoKnowing(touch);
        BattleCombatant vessel = retaliatingFighter(0.75);
        BattleState state = new BattleState(attacker, vessel);
        attacker.receiveDamage(attacker.getMaxHp() / 2);
        AbilityActivationEngine engine = new AbilityActivationEngine(new SequenceRandom(0.0));
        AbilityEffectData row = AbilityEffectType.CODED_MOVE_ACTION.createDefault();
        row.codedAbilityKey = IdleTransfigurationAbility.KEY;
        row.codedAction = IdleTransfigurationAbility.ACTION_SOUL_MANIPULATION;

        List<CombatEvent> events = engine.executeDomainEffect(state, attacker, vessel, row, 1, "DOMAIN");

        assertTrue(attacker.isDefeated());
        assertEquals(vessel.getMaxHp(), vessel.getCurrentHp());
        assertTrue(attempts(events).isEmpty(), "a defeated manipulator cannot finish transfiguration");
    }

    @Test
    void lethalRetaliationReconcilesDefeatDuringRoundResolution() {
        Move touch = idleTransfigurationTouch();
        BattleCombatant attacker = mahitoKnowing(touch);
        BattleCombatant vessel = retaliatingFighter(0.75);
        BattleState state = new BattleState(attacker, vessel);
        attacker.receiveDamage(attacker.getMaxHp() / 2);
        place(attacker, touch, List.of(vessel));
        state.transitionTo(BattleState.Phase.RESOLUTION);

        List<CombatEvent> events = new CombatResolver(new SequenceRandom(0.5, 0.0)).resolveRound(state);

        assertTrue(attacker.isDefeated());
        assertFalse(attacker.isActive());
        assertEquals(vessel.getMaxHp(), vessel.getCurrentHp());
        assertTrue(attempts(events).isEmpty());
    }

    // ── Successful transfiguration kills immediately, before the rest of the tick ──

    @Test
    void successfulTouchKillPreemptsTheMovesRemainingRows() {
        Move touch = touchWithFollowUpRow();
        BattleCombatant attacker = mahitoKnowing(touch);
        BattleCombatant enemy = fighter("TARGET");
        BattleState state = new BattleState(attacker, enemy);
        place(attacker, touch, List.of(enemy));
        state.transitionTo(BattleState.Phase.RESOLUTION);

        new CombatResolver(new SequenceRandom(0.0, 0.0, 0.0)).resolveRound(state);

        assertTrue(enemy.isDefeated());
        assertFalse(enemy.isActive());
        assertFalse(enemy.hasEffect(StatusEffectType.STRENGTH_DECREASE),
            "the instant kill preempts the move's remaining effect rows");
    }

    @Test
    void successfulPassiveProcKillPreemptsTheHitsOnHitRows() {
        Move strike = staggerOnHitMelee();
        BattleCombatant attacker = mahito(soulManipulation());
        BattleCombatant enemy = fighter("TARGET");
        BattleState state = new BattleState(attacker, enemy);
        place(attacker, strike, List.of(enemy));
        state.transitionTo(BattleState.Phase.RESOLUTION);

        new CombatResolver(new SequenceRandom(0.0, 0.0, 0.0, 0.0)).resolveRound(state);

        assertTrue(enemy.isDefeated());
        assertFalse(enemy.hasEffect(StatusEffectType.STRENGTH_DECREASE),
            "a proc-based kill at attack-hit time preempts the on-hit rows");
    }

    @Test
    void miraclesAvertASuccessfulSoulManipulationWithoutTeachingTheManipulator() {
        Move touch = idleTransfigurationTouch();
        BattleCombatant attacker = mahitoKnowing(touch);
        BattleCombatant haruta = miracleFighter();
        BattleState state = new BattleState(attacker, haruta);
        new CombatResolver(new SequenceRandom(0.5)).processRoundStart(state);

        List<CombatEvent> first = new AbilityActivationEngine(new SequenceRandom(0.0, 0.0))
            .processMoveEffects(state, attacker, haruta, touch, MoveEffectTrigger.ON_HIT, 0, 1);

        assertFalse(haruta.isDefeated());
        assertEquals(MiraclesAbility.MAX_MIRACLES - 1, miracleCount(haruta));
        assertEquals(1, attempts(first).size());
        assertTrue(attempts(first).get(0).getMessage().contains(
            "fails to resist and the transformation turns fatal"));
        int attemptAt = first.indexOf(attempts(first).get(0));
        int aversionAt = -1;
        for (int i = 0; i < first.size(); i++) {
            String message = first.get(i).getMessage();
            if (message != null && message.contains("avert a fatal blow")) {
                aversionAt = i;
                break;
            }
        }
        assertTrue(aversionAt > attemptAt,
            "the aversion is announced within the attempt's own exchange");
        int chance = attempts(first).get(0).getIntValue();

        List<CombatEvent> second = new AbilityActivationEngine(new SequenceRandom(0.0, 0.0))
            .processMoveEffects(state, attacker, haruta, touch, MoveEffectTrigger.ON_HIT, 0, 1);

        assertEquals(MiraclesAbility.MAX_MIRACLES - 2, miracleCount(haruta));
        assertEquals(chance, attempts(second).get(0).getIntValue(),
            "an averted success is not a resisted soul: it leaves no stack");
    }

    @Test
    void avertedSoulManipulationDoesNotPreemptTheRestOfTheTick() {
        Move touch = touchWithFollowUpRow();
        BattleCombatant attacker = mahitoKnowing(touch);
        BattleCombatant haruta = miracleFighter();
        BattleState state = new BattleState(attacker, haruta);
        place(attacker, touch, List.of(haruta));
        state.transitionTo(BattleState.Phase.RESOLUTION);

        new CombatResolver(new SequenceRandom(0.0, 0.0, 0.0)).resolveRound(state);

        assertFalse(haruta.isDefeated());
        assertTrue(haruta.hasEffect(StatusEffectType.STRENGTH_DECREASE),
            "only the kill preempts the tick; an averted attempt does not");
        assertEquals(MiraclesAbility.MAX_MIRACLES - 1, miracleCount(haruta));
    }

    private static BattleCombatant retaliatingFighter(double fraction) {
        AbilityData ability = new AbilityData();
        ability.id = "RETALIATION";
        ability.name = "Retaliation";
        ability.category = "ACTIVE";
        ability.sourceType = "CHARACTER";
        AbilityEffectData effect = AbilityEffectType.DEAL_DIRECT_DAMAGE.createDefault();
        effect.effectId = "damage";
        effect.target = AbilityEffectTarget.CURRENT_ENEMY.name();
        effect.valueMode = "PERCENT";
        effect.intValue = null;
        effect.doubleValue = fraction;
        effect.soulDamage = true;
        AbilityEffectData negate = AbilityEffectType.NEGATE_SOUL_MANIPULATION.createDefault();
        negate.effectId = "negate";
        ability.effects = List.of(effect, negate);
        ability.activationConditions = List.of(AbilityConditionRuleData.allEffects(
            AbilityConditionType.SOUL_MANIPULATION_TARGETED.createDefault()));
        return new BattleCombatant(new SorcererCharacter(
            "VESSEL", "Vessel", stats(80), null, List.of(), List.of(new Ability(ability))));
    }

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private static List<CombatEvent> attempts(List<CombatEvent> events) {
        return events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.ABILITY_ACTIVATED)
            .filter(event -> event.getMessage() != null
                && event.getMessage().contains("Soul Manipulation activates"))
            .toList();
    }

    private static Ability maintainingTheSoul() {
        return codedPassive(
            "MAINTAIN", IdleTransfigurationAbility.MAINTAINING_THE_SOUL,
            Map.of(IdleTransfigurationAbility.CE_DRAIN_PER_TICK, 2));
    }

    private static Ability malleableBody() {
        return codedPassive(
            "MALLEABLE", IdleTransfigurationAbility.MALLEABLE_BODY, null);
    }

    private static Ability soulManipulation() {
        return codedPassive(
            "SOUL_MANIP", IdleTransfigurationAbility.SOUL_MANIPULATION,
            Map.of(IdleTransfigurationAbility.PROC_CHANCE_PERCENT, 5,
                IdleTransfigurationAbility.BASE_SUCCESS_PERCENT, 5,
                IdleTransfigurationAbility.CTM_SUCCESS_PER_TEN_POINTS, 1,
                IdleTransfigurationAbility.SUCCESS_PER_STACK_PERCENT, 12,
                IdleTransfigurationAbility.RESIST_PER_TEN_CE, 1,
                IdleTransfigurationAbility.MIN_SUCCESS_PERCENT, 1,
                IdleTransfigurationAbility.MAX_SUCCESS_PERCENT, 95));
    }

    private static Ability codedPassive(String id, String feature, Map<String, Integer> params) {
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
        effect.codedParameters = params;
        ability.effects = List.of(effect);
        return new Ability(ability);
    }

    private static BattleCombatant mahito(Ability... abilities) {
        return mahito(List.of(abilities), 165);
    }

    private static BattleCombatant mahito(List<Ability> abilities, int ctm) {
        Character character = new CursedSpiritCharacter(
            "MAHITO", "Mahito", stats(ctm), "Idle Transfiguration", List.of(), abilities);
        return new BattleCombatant(character);
    }

    private static BattleCombatant mahitoWithCtm(int ctm) {
        return mahito(List.of(soulManipulation()), ctm);
    }

    /** Mahito whose character knows a move; coded move rows need this. */
    private static BattleCombatant mahitoKnowing(Move move) {
        Character character = new CursedSpiritCharacter(
            "MAHITO", "Mahito", stats(165), "Idle Transfiguration",
            List.of(move), List.of());
        return new BattleCombatant(character);
    }

    private static BattleCombatant fighter(String id) {
        return fighterWithPool(80, 640, 80);
    }

    /** Target with explicit cursed-energy pool and output, CTM irrelevant. */
    private static BattleCombatant fighterWithPool(
        int ctm, int ceAfterDrain, int ceOutput
    ) {
        Character character = new SorcererCharacter(
            "TARGET", "TARGET", stats(ctm, ceOutput), null, List.of());
        BattleCombatant combatant = new BattleCombatant(character);
        combatant.drainCe(combatant.getCurrentCe() - ceAfterDrain);
        return combatant;
    }

    private static CharacterStats stats(int ctm) {
        return stats(ctm, 80);
    }

    private static CharacterStats stats(int ctm, int ceOutput) {
        return new CharacterStats.Builder()
            .vitality(80).strength(80).durability(80).speed(80)
            .cursedEnergyReserves(80).cursedEnergyEfficiency(80)
            .cursedEnergyOutput(ceOutput).jujutsuSkill(80)
            .combatAbility(80).cursedTechniqueMastery(ctm)
            .build();
    }

    private static Move neverMissMelee(String id, int power) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.ATTACK, MoveTag.PHYSICAL))
            .basePower(power)
            .neverMiss(true)
            .apCost(1)
            .unleashPoint(1)
            .hitComponents(List.of(new HitComponent(
                power, Set.of(MoveTag.PHYSICAL, MoveTag.MELEE), 0, false, true,
                1.0, List.of())))
            .build();
    }

    private static Move neverMissRanged(String id, int power) {
        return new Move.Builder(id)
            .name(id)
            .category(MoveCategory.CURSED_ENERGY)
            .tags(Set.of(MoveTag.ATTACK, MoveTag.CURSED_ENERGY))
            .basePower(power)
            .neverMiss(true)
            .apCost(1)
            .unleashPoint(1)
            .hitComponents(List.of(new HitComponent(
                power, Set.of(MoveTag.CURSED_ENERGY, MoveTag.RANGED), 0, false, true,
                1.0, List.of())))
            .build();
    }

    /** The dedicated technique move: 0 damage, soul-damaging touch, coded row. */
    private static Move idleTransfigurationTouch() {
        MoveEffectData soulRow = AbilityEffectType.CODED_MOVE_ACTION.createDefaultMoveEffect();
        soulRow.effectId = "effect-000000";
        soulRow.trigger = MoveEffectTrigger.ON_HIT.name();
        soulRow.target = AbilityEffectTarget.ENEMY.name();
        soulRow.codedAbilityKey = IdleTransfigurationAbility.KEY;
        soulRow.codedAction = IdleTransfigurationAbility.ACTION_SOUL_MANIPULATION;
        soulRow.codedParameters = null;

        return new Move.Builder("TOUCH")
            .name("Idle Transfiguration")
            .category(MoveCategory.INNATE_TECHNIQUE)
            .tags(Set.of(MoveTag.ATTACK, MoveTag.INNATE_TECHNIQUE))
            .basePower(0)
            .neverMiss(true)
            .apCost(1)
            .unleashPoint(1)
            .hitComponents(List.of(new HitComponent(
                0, Set.of(MoveTag.INNATE_TECHNIQUE, MoveTag.MELEE), 0, false, true,
                1.0, List.of(), false, 0, true)))
            .effects(List.of(soulRow))
            .requiredTechniqueId("Idle Transfiguration")
            .prerequisites(Map.of("cursedTechniqueMastery", 20))
            .build();
    }

    /** Touch whose coded soul row is followed by one more on-hit row. */
    private static Move touchWithFollowUpRow() {
        MoveEffectData soulRow = AbilityEffectType.CODED_MOVE_ACTION.createDefaultMoveEffect();
        soulRow.effectId = "effect-000000";
        soulRow.trigger = MoveEffectTrigger.ON_HIT.name();
        soulRow.target = AbilityEffectTarget.ENEMY.name();
        soulRow.codedAbilityKey = IdleTransfigurationAbility.KEY;
        soulRow.codedAction = IdleTransfigurationAbility.ACTION_SOUL_MANIPULATION;
        soulRow.codedParameters = null;

        return new Move.Builder("TOUCH_PLUS")
            .name("Idle Transfiguration")
            .category(MoveCategory.INNATE_TECHNIQUE)
            .tags(Set.of(MoveTag.ATTACK, MoveTag.INNATE_TECHNIQUE))
            .basePower(0)
            .neverMiss(true)
            .apCost(1)
            .unleashPoint(1)
            .hitComponents(List.of(new HitComponent(
                0, Set.of(MoveTag.INNATE_TECHNIQUE, MoveTag.MELEE), 0, false, true,
                1.0, List.of(), false, 0, true)))
            .effects(List.of(soulRow, debuffRow("effect-000001")))
            .requiredTechniqueId("Idle Transfiguration")
            .prerequisites(Map.of("cursedTechniqueMastery", 20))
            .build();
    }

    /** Melee strike carrying one on-hit stagger row after its damage. */
    private static Move staggerOnHitMelee() {
        return new Move.Builder("STRIKE_PLUS")
            .name("Strike Plus")
            .category(MoveCategory.PHYSICAL)
            .tags(Set.of(MoveTag.ATTACK, MoveTag.PHYSICAL))
            .basePower(10)
            .neverMiss(true)
            .apCost(1)
            .unleashPoint(1)
            .hitComponents(List.of(new HitComponent(
                10, Set.of(MoveTag.PHYSICAL, MoveTag.MELEE), 0, false, true,
                1.0, List.of())))
            .effects(List.of(debuffRow("effect-000000")))
            .build();
    }

    /** Round-scaled debuff row: survives a full resolveRound, unlike a tick status. */
    private static MoveEffectData debuffRow(String effectId) {
        MoveEffectData row = AbilityEffectType.APPLY_STATUS.createDefaultMoveEffect();
        row.effectId = effectId;
        row.trigger = MoveEffectTrigger.ON_HIT.name();
        row.target = AbilityEffectTarget.ENEMY.name();
        row.stringValue = StatusEffectType.STRENGTH_DECREASE.name();
        row.durationRounds = 2;
        row.durationTicks = 0;
        row.codedParameters = null;
        return row;
    }

    /** Defender carrying Miracles: reservoir plus fatal reprieve, no conditions. */
    private static BattleCombatant miracleFighter() {
        return new BattleCombatant(new SorcererCharacter(
            "TARGET", "TARGET", stats(80), "Miracles", List.of(),
            List.of(miracleFeature("RESERVOIR", MiraclesAbility.RESERVOIR),
                miracleFeature("REPRIEVE", MiraclesAbility.FATEFUL_REPRIEVE))));
    }

    private static Ability miracleFeature(String id, String feature) {
        AbilityData ability = new AbilityData();
        ability.id = id;
        ability.name = id;
        ability.category = "PASSIVE";
        ability.sourceType = "TECHNIQUE";
        ability.sourceValue = "Miracles";
        AbilityEffectData coded = AbilityEffectType.CODED.createDefault();
        coded.effectId = "effect-000000";
        coded.codedAbilityKey = MiraclesAbility.KEY;
        coded.codedFeature = feature;
        ability.effects = List.of(coded);
        return new Ability(ability);
    }

    private static int miracleCount(BattleCombatant combatant) {
        return combatant.getCodedAbilities().states().stream()
            .filter(state -> MiraclesAbility.KEY.equals(state.key()))
            .findFirst()
            .orElseThrow()
            .currentValue();
    }

    private static void place(BattleCombatant actor, Move move, List<BattleCombatant> targets) {
        BattlePlan plan = new BattlePlan(actor.getMaxApBar(), actor.getCurrentCe());
        var segment = plan.place(move, 1, 0);
        assertNotNull(segment);
        segment.setTargets(targets.stream().map(BattleCombatant::getInstanceId).toList());
        actor.setTimeline(plan.toLegacyTimeline());
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
