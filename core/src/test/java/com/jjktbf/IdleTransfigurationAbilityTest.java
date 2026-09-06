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
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.character.coded.IdleTransfigurationAbility;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern CHANCE_PATTERN = Pattern.compile("\\((\\d+)%\\)");

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
        } else {
            assertFalse(enemy.isDefeated());
            assertTrue(attempts.get(0).getMessage().contains("stack 1"),
                attempts.get(0).getMessage());
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
            assertTrue(attempts(events).get(0).getMessage().contains("stack " + round));
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
        Matcher matcher = CHANCE_PATTERN.matcher(attempts.get(0).getMessage());
        assertTrue(matcher.find(), attempts.get(0).getMessage());
        return Integer.parseInt(matcher.group(1));
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
        Matcher matcher = CHANCE_PATTERN.matcher(attempts(events).get(0).getMessage());
        assertTrue(matcher.find());
        return Integer.parseInt(matcher.group(1));
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
        assertTrue(attempts(events).get(0).getMessage().contains("stack 1"));
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

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private static List<CombatEvent> attempts(List<CombatEvent> events) {
        return events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.ABILITY_ACTIVATED)
            .filter(event -> event.getMessage() != null
                && (event.getMessage().contains("transfigures")
                    || event.getMessage().contains("resists transfiguration")))
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
