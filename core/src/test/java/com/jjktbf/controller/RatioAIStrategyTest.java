package com.jjktbf.controller;

import com.jjktbf.model.character.Ability;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.BattleTeamId;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.TeamBattlePlan;
import com.jjktbf.model.move.Move;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Nanami (Ratio) archetype: effort assessment, Ratio stack
 * sequencing (a strike never rides on a pending stack), offence caps by
 * effort, capacity-gated marking, and dispatcher routing.
 */
class RatioAIStrategyTest {

    private final RatioAIStrategy strategy = new RatioAIStrategy();
    private final List<Move> canonical = loadMoves();

    // --- effortTier -----------------------------------------------------------

    @Test
    void effortTierMatchesOpponentStrengthAndOwnPlight() {
        // Weak opponent, healthy: moderate effort suffices.
        assertEquals(RatioAIStrategy.EffortTier.MODERATE,
            RatioAIStrategy.effortTier(1000, 600, 1.0));
        // Opponent at 90% of his own base-stat total: the situation calls for it.
        assertEquals(RatioAIStrategy.EffortTier.ALL_OUT,
            RatioAIStrategy.effortTier(1000, 900, 1.0));
        assertEquals(RatioAIStrategy.EffortTier.ALL_OUT,
            RatioAIStrategy.effortTier(1000, 1200, 1.0));
        // Badly hurt: a professional sees it through, regardless of the enemy.
        assertEquals(RatioAIStrategy.EffortTier.ALL_OUT,
            RatioAIStrategy.effortTier(1000, 600, 0.30));
        assertEquals(RatioAIStrategy.EffortTier.MODERATE,
            RatioAIStrategy.effortTier(1000, 600, 0.50));
    }

    // --- RatioPlanning --------------------------------------------------------

    @Test
    void classifiesCanonicalRatioMovesByTheirEffectRows() {
        assertTrue(RatioPlanning.isRatioStackMove(move("000023")), "Ratio Mark creates stacks");
        assertEquals(1, RatioPlanning.stacksCreatedBy(move("000023")));

        assertTrue(RatioPlanning.isRatioStrike(move("000024")), "Ratio Strike applies Ratio itself");
        assertTrue(RatioPlanning.isRatioStrike(move("000089")), "Overhead Ratio Cleave applies Ratio itself");
        assertFalse(RatioPlanning.isRatioStrike(move("000022")), "Cursed Slash is a normal attack");
        assertFalse(RatioPlanning.isRatioStackMove(move("000024")));
    }

    @Test
    void pendingStacksBookkeeping() {
        Move mark = move("000023"), strike = move("000024"), normal = move("000000");
        assertEquals(1, RatioPlanning.pendingStacksAfter(mark, 0, 3), "a mark adds its stacks");
        assertEquals(3, RatioPlanning.pendingStacksAfter(mark, 2, 3), "bounded by capacity");
        assertEquals(3, RatioPlanning.pendingStacksAfter(mark, 3, 3), "no overflow at capacity");
        assertEquals(0, RatioPlanning.pendingStacksAfter(normal, 1, 3), "a normal attack uses a stack up");
        assertEquals(0, RatioPlanning.pendingStacksAfter(normal, 0, 3), "never negative");
        assertEquals(1, RatioPlanning.pendingStacksAfter(strike, 1, 3), "strikes leave the count untouched");
    }

    // --- Sequencing (the core rule) -------------------------------------------

    @Test
    void neverPlacesARatioStrikeWhileAStackIsPending() throws IOException {
        BattleCombatant nanami = nanami(List.of(AIFixtures.loadCanonicalAbility("000004")));
        BattleCombatant strongEnemy = AIFixtures.cursedSpeechSorcerer("hi", move("000000"));
        int capacity = nanami.abilityState("RATIO").orElseThrow().maximumValue();
        assertTrue(capacity > 0, "fixture compiles the Ratio runtime with capacity");

        for (long seed = 1; seed <= 40; seed++) {
            BattlePlan plan = strategy.buildPlan(
                state(nanami, strongEnemy), nanami, new SeededRandomSource(seed));
            assertStrikeOnlyAfterStacksAreUsedUp(plan, 0, capacity);
        }
    }

    /** Walks the plan in fire order replaying the stack bookkeeping. */
    private static void assertStrikeOnlyAfterStacksAreUsedUp(
        BattlePlan plan, int heldStacks, int capacity
    ) {
        List<ActionSegment> offense = new ArrayList<>(plan.offensiveTimeline().getSegments());
        offense.sort(Comparator.comparingInt(ActionSegment::getFireTick));
        int pending = heldStacks;
        for (ActionSegment segment : offense) {
            Move m = segment.getMove();
            if (RatioPlanning.isRatioStrike(m)) {
                assertEquals(0, pending,
                    "ratio strike placed while a stack is still pending: " + planSummary(plan));
            }
            pending = RatioPlanning.pendingStacksAfter(m, pending, capacity);
        }
    }

    // --- Effort shaping -------------------------------------------------------

    @Test
    void moderateEffortKeepsAShortOffensiveAgainstWeakEnemies() throws IOException {
        BattleCombatant nanami = nanami(List.of(AIFixtures.loadCanonicalAbility("000004")));
        BattleCombatant weakEnemy = AIFixtures.lowCeSorcererEnemy("lo");
        BattleState battle = state(nanami, weakEnemy);

        for (long seed = 1; seed <= 20; seed++) {
            BattlePlan plan = strategy.buildPlan(battle, nanami, new SeededRandomSource(seed));
            assertTrue(offensiveCount(plan) <= 2,
                "moderate effort caps the offence at two placements: " + planSummary(plan));
        }
    }

    @Test
    void allOutAgainstStrongEnemiesSpendsMore() throws IOException {
        BattleCombatant nanami = nanami(List.of(AIFixtures.loadCanonicalAbility("000004")));
        BattleCombatant strongEnemy = AIFixtures.cursedSpeechSorcerer("hi", move("000000"));
        BattleState battle = state(nanami, strongEnemy);

        int moderateTotal = 0, allOutTotal = 0;
        for (long seed = 1; seed <= 20; seed++) {
            allOutTotal += offensiveCount(
                strategy.buildPlan(battle, nanami, new SeededRandomSource(seed)));
            moderateTotal += offensiveCount(strategy.buildPlan(
                state(nanami, AIFixtures.lowCeSorcererEnemy("lo")),
                nanami, new SeededRandomSource(seed)));
        }
        assertTrue(allOutTotal > moderateTotal,
            "all-out offence should outspend moderate effort on average");
        assertTrue(allOutTotal / 20.0 > 2.0,
            "all out against a peer uses more than the moderate cap");
    }

    @Test
    void noRatioMarkWithoutStackCapacity() {
        // No Ratio Reinforcement ability: capacity 0, so marking creates nothing.
        BattleCombatant nanami = nanami(List.of());
        assertEquals(0, nanami.abilityState("RATIO").orElseThrow().maximumValue());
        BattleCombatant strongEnemy = AIFixtures.cursedSpeechSorcerer("hi", move("000000"));

        for (long seed = 1; seed <= 20; seed++) {
            BattlePlan plan = strategy.buildPlan(
                state(nanami, strongEnemy), nanami, new SeededRandomSource(seed));
            assertTrue(plan.allSegments().stream()
                    .noneMatch(s -> RatioPlanning.isRatioStackMove(s.getMove())),
                "never plans a stack move with zero capacity: " + planSummary(plan));
        }
    }

    // --- Routing --------------------------------------------------------------

    @Test
    void dispatcherRoutesRatioSorcerer() throws IOException {
        ArchetypeAIStrategy dispatcher = new ArchetypeAIStrategy();
        BattleCombatant nanami = nanami(List.of(AIFixtures.loadCanonicalAbility("000004")));
        BattleState battle = state(nanami, AIFixtures.lowCeSorcererEnemy("lo"));

        for (long seed = 1; seed <= 10; seed++) {
            TeamBattlePlan teamPlan = dispatcher.selectTeamPlan(
                battle, battle.playerTeam().active(), new SeededRandomSource(seed));
            BattlePlan plan = teamPlan.get(nanami.getInstanceId());
            assertTrue(offensiveCount(plan) <= 2,
                "dispatcher routes Ratio to the measured archetype (cap 2 vs weak enemy), "
                    + "not the always-spending greedy default: " + planSummary(plan));
        }
    }

    // --- helpers --------------------------------------------------------------

    /** Nanami's canonical kit (normals, blocks, utilities, Ratio Mark/Strike/Cleave). */
    private BattleCombatant nanami(List<Ability> abilities) {
        return AIFixtures.ratioSorcerer(abilities.isEmpty() ? "000001" : "000001",
            List.copyOf(abilities),
            move("000000"), // Basic Strike
            move("000002"), // Jab
            move("000011"), // Cursed Energy Burst
            move("000022"), // Cursed Slash
            move("000023"), // Ratio Mark
            move("000024"), // Ratio Strike
            move("000089"), // Overhead Ratio Cleave
            move("000001"), // Basic Block
            move("000027"), // Sidestep
            move("000034")  // Keen Observation
        );
    }

    /** Attacks + stack moves placed on the offensive board (utility reads excluded). */
    private static int offensiveCount(BattlePlan plan) {
        return (int) plan.offensiveTimeline().getSegments().stream()
            .filter(s -> s.getMove().hasTag("ATTACK") || RatioPlanning.isRatioStackMove(s.getMove()))
            .count();
    }

    private static String planSummary(BattlePlan plan) {
        List<ActionSegment> offense = new ArrayList<>(plan.offensiveTimeline().getSegments());
        offense.sort(Comparator.comparingInt(ActionSegment::getFireTick));
        StringBuilder sb = new StringBuilder("[");
        for (ActionSegment s : offense) {
            if (sb.length() > 1) sb.append(" -> ");
            sb.append(s.getMove().getName());
        }
        return sb.append(']').toString();
    }

    private Move move(String id) {
        return AIFixtures.canonicalMoveById(canonical, id);
    }

    private static BattleState state(BattleCombatant nanami, BattleCombatant enemy) {
        return new BattleState(
            BattleState.teamOfFighters(BattleTeamId.PLAYER, List.of(nanami)),
            BattleState.teamOfFighters(BattleTeamId.ENEMY, List.of(enemy)));
    }

    private static List<Move> loadMoves() {
        try {
            return AIFixtures.loadCanonicalMoves();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not load canonical moves", e);
        }
    }
}
