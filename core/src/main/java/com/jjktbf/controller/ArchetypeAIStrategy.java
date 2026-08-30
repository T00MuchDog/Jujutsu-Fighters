package com.jjktbf.controller;

import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.StatKey;
import com.jjktbf.model.character.CharacterType;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.DomainDefinitionLookup;
import com.jjktbf.model.combat.MoveAvailability;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.combat.TeamBattlePlan;
import com.jjktbf.model.move.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Routes AI planning to a per-archetype strategy based on the combatant's
 * character definition.
 *
 * <p>AI type is a <b>code-only</b> mapping — it is never a data or editor field:
 * <ul>
 *   <li>Shikigami → {@link ShikigamiAIStrategy} (offense-first summon).</li>
 *   <li>Technique-less sorcerer → {@link AggressiveSorcererAIStrategy} or
 *       {@link PassiveSorcererAIStrategy}, decided by {@link #archetypeFor}.</li>
 *   <li>Cursed Speech sorcerer → {@link CursedSpeechAIStrategy} (state-aware
 *       multitarget planning; routed through {@link #selectTeamPlan}).</li>
 *   <li>Ratio sorcerer → {@link RatioAIStrategy} (state-aware effort assessment
 *       and Ratio stack sequencing; routed through {@link #selectTeamPlan}).</li>
 *   <li>Blood Manipulation sorcerer → {@link BloodManipulationAIStrategy}
 *       (cautious blood-economy planning that turns aggressive inside a Flowing
 *       Red Scale window; routed through {@link #selectTeamPlan}).</li>
 *   <li>Disaster Plants cursed spirit → {@link HanamiAIStrategy} (Flower Offering
 *       sequencing and durable battlefield control).</li>
 *   <li>Everyone else → {@link GreedyAIStrategy}.</li>
 * </ul>
 *
 * <p>This dispatcher owns {@link #selectTeamPlan} because Cursed Speech needs the
 * full enemy roster (state) for recoil-budgeted multitargeting, which the
 * single-opponent {@link AIStrategy#selectPlan} does not receive. Each combatant
 * is planned with its archetype, then uniformly pruned, normalised, and given
 * explicit targets.
 */
public class ArchetypeAIStrategy implements AIStrategy {

    private static final double RESERVE_SWITCH_HP_THRESHOLD = 0.25;

    private static final String CURSED_SPEECH = "Cursed Speech";
    private static final String TEN_SHADOWS = "Ten Shadows";
    private static final String RATIO = "Ratio";
    private static final String BLOOD_MANIPULATION = "Blood Manipulation";
    private static final String DISASTER_PLANTS = "Disaster Plants";

    private final GreedyAIStrategy sorcererStrategy = new GreedyAIStrategy();
    private final ShikigamiAIStrategy shikigamiStrategy = new ShikigamiAIStrategy();
    private final AggressiveSorcererAIStrategy aggressiveStrategy = new AggressiveSorcererAIStrategy();
    private final PassiveSorcererAIStrategy passiveStrategy = new PassiveSorcererAIStrategy();
    private final CursedSpeechAIStrategy cursedSpeechStrategy = new CursedSpeechAIStrategy();
    private final TenShadowsAIStrategy tenShadowsStrategy = new TenShadowsAIStrategy();
    private final RatioAIStrategy ratioStrategy = new RatioAIStrategy();
    private final BloodManipulationAIStrategy bloodManipulationStrategy = new BloodManipulationAIStrategy();
    private final HanamiAIStrategy hanamiStrategy = new HanamiAIStrategy();

    /** Hardcoded archetype assignment for the final technique-less sorcerer roster. */
    private static final Set<String> AGGRESSIVE_IDS = Set.of("000003", "000005"); // Yuji Itadori, Maki Zenin
    private static final Set<String> PASSIVE_IDS = Set.of("000002");              // Miwa Kasumi

    private DomainDefinitionLookup domainLookup;

    @Override
    public AIStrategy withDomainLookup(DomainDefinitionLookup lookup) {
        this.domainLookup = lookup;
        return this;
    }

    @Override
    public DomainDefinitionLookup domainLookup() {
        return domainLookup;
    }

    // -------------------------------------------------------------------------
    // Team plan (the real entry point) — owns pruning/normalisation/targeting
    // -------------------------------------------------------------------------

    @Override
    public TeamBattlePlan selectTeamPlan(
        BattleState state, List<BattleCombatant> aiTeam, RandomSource rng
    ) {
        int commonGridLength = TeamBattlePlan.gridLengthForRound(state);
        TeamBattlePlan teamPlan = new TeamBattlePlan(
            aiTeam.isEmpty() ? null : aiTeam.get(0).getTeamId(), commonGridLength);
        List<BattleCombatant> availableReserves = aiTeam.isEmpty()
            ? new ArrayList<>()
            : new ArrayList<>(state.teamOf(aiTeam.get(0)).reserves());

        for (BattleCombatant ai : aiTeam) {
            if (ai.isFighter() && !availableReserves.isEmpty()
                && (double) ai.getCurrentHp() / Math.max(1, ai.getMaxHp())
                    <= RESERVE_SWITCH_HP_THRESHOLD) {
                BattleCombatant incoming = availableReserves.stream()
                    .max(java.util.Comparator
                        .comparingDouble((BattleCombatant reserve) ->
                            (double) reserve.getCurrentHp() / Math.max(1, reserve.getMaxHp()))
                        .thenComparingInt(reserve -> -reserve.getRosterOrder()))
                    .orElse(null);
                if (incoming != null) {
                    teamPlan.switchTo(ai.getInstanceId(), incoming.getInstanceId());
                    availableReserves.remove(incoming);
                    continue;
                }
            }
            BattlePlan plan = planFor(state, ai, rng);
            plan = SmartAIScoring.pruneRestrictedDomainOpenings(
                domainLookup, state, ai, plan);
            plan = SmartAIScoring.promoteDomainOpenings(domainLookup, state, ai, plan);

            List<Move> alreadyPlanned = new ArrayList<>();
            for (ActionSegment segment : new ArrayList<>(plan.allSegments())) {
                if (MoveAvailability.restrictionReason(state, ai, segment.getMove(), alreadyPlanned) != null) {
                    plan.remove(segment);
                } else {
                    alreadyPlanned.add(segment.getMove());
                }
            }
            if (plan.gridLength() != commonGridLength) {
                plan = normalise(plan, commonGridLength);
            }
            plan = SmartAIScoring.promoteGuaranteedKillOpening(state, ai, plan, rng);
            alreadyPlanned.clear();
            for (ActionSegment segment : new ArrayList<>(plan.allSegments())) {
                if (MoveAvailability.restrictionReason(
                    state, ai, segment.getMove(), alreadyPlanned) != null) {
                    plan.remove(segment);
                } else {
                    alreadyPlanned.add(segment.getMove());
                }
            }
            assignExplicitTargets(state, plan, ai, rng);
            teamPlan.put(ai.getInstanceId(), plan);
        }
        return teamPlan;
    }

    /** Build one combatant's plan with the archetype that matches its definition. */
    private BattlePlan planFor(BattleState state, BattleCombatant ai, RandomSource rng) {
        BattleCombatant opponent = state.firstActiveEnemyOf(ai);
        Character character = ai == null ? null : ai.getCharacter();
        if (character == null) {
            return sorcererStrategy.selectPlan(ai, opponent, rng);
        }
        if (character.getType() == CharacterType.SHIKIGAMI) {
            return shikigamiStrategy.selectPlan(ai, opponent, rng);
        }
        if (character.getType() == CharacterType.SORCERER && !character.hasInnateTechnique()) {
            return archetypeFor(ai).selectPlan(ai, opponent, rng);
        }
        if (CURSED_SPEECH.equalsIgnoreCase(character.getInnateTechniqueName())) {
            return cursedSpeechStrategy.buildPlan(state, ai, rng); // state-aware multitarget
        }
        if (TEN_SHADOWS.equalsIgnoreCase(character.getInnateTechniqueName())) {
            return tenShadowsStrategy.buildPlan(state, ai, rng); // state-aware summoning
        }
        if (RATIO.equalsIgnoreCase(character.getInnateTechniqueName())) {
            return ratioStrategy.buildPlan(state, ai, rng); // state-aware effort assessment
        }
        if (BLOOD_MANIPULATION.equalsIgnoreCase(character.getInnateTechniqueName())) {
            return bloodManipulationStrategy.buildPlan(state, ai, rng); // state-aware blood economy
        }
        if (DISASTER_PLANTS.equalsIgnoreCase(character.getInnateTechniqueName())) {
            return hanamiStrategy.buildPlan(state, ai, rng); // state-aware Flower Offering
        }
        return sorcererStrategy.selectPlan(ai, opponent, rng);
    }

    private static BattlePlan normalise(BattlePlan plan, int commonGridLength) {
        BattlePlan normalized = new BattlePlan(
            plan.apBudget(), plan.ceBudget(), commonGridLength, plan.actionTickDelay());
        for (ActionSegment segment : plan.allSegments()) {
            ActionSegment ns = normalized.place(segment.getMove(), segment.getStartTick(), segment.getActualCeCost());
            if (ns == null) {
                throw new IllegalArgumentException("AI plan cannot be normalized to the battle grid");
            }
            ns.setTargets(segment.getTargets());
        }
        return normalized;
    }

    // -------------------------------------------------------------------------
    // Single-opponent router (interface contract / direct calls / tests)
    // -------------------------------------------------------------------------

    @Override
    public BattlePlan selectPlan(BattleCombatant ai, BattleCombatant opponent, RandomSource rng) {
        Character character = ai == null ? null : ai.getCharacter();
        if (character == null) {
            return sorcererStrategy.selectPlan(ai, opponent, rng);
        }
        if (character.getType() == CharacterType.SHIKIGAMI) {
            return shikigamiStrategy.selectPlan(ai, opponent, rng);
        }
        if (character.getType() == CharacterType.SORCERER && !character.hasInnateTechnique()) {
            return archetypeFor(ai).selectPlan(ai, opponent, rng);
        }
        if (CURSED_SPEECH.equalsIgnoreCase(character.getInnateTechniqueName())) {
            // No state here: degrade to single-opponent CS planning.
            return cursedSpeechStrategy.selectPlan(ai, opponent, rng);
        }
        if (TEN_SHADOWS.equalsIgnoreCase(character.getInnateTechniqueName())) {
            // No state here: degrade to single-opponent Ten Shadows planning.
            return tenShadowsStrategy.selectPlan(ai, opponent, rng);
        }
        if (RATIO.equalsIgnoreCase(character.getInnateTechniqueName())) {
            // No state here: degrade to single-opponent Ratio planning.
            return ratioStrategy.selectPlan(ai, opponent, rng);
        }
        if (BLOOD_MANIPULATION.equalsIgnoreCase(character.getInnateTechniqueName())) {
            // No state here: degrade to single-opponent Blood Manipulation planning.
            return bloodManipulationStrategy.selectPlan(ai, opponent, rng);
        }
        if (DISASTER_PLANTS.equalsIgnoreCase(character.getInnateTechniqueName())) {
            return hanamiStrategy.selectPlan(ai, opponent, rng);
        }
        return sorcererStrategy.selectPlan(ai, opponent, rng);
    }

    /**
     * Pick the technique-less sorcerer archetype: the hardcoded id map decides
     * for the known roster; any unmapped technique-less sorcerer falls back to a
     * stat-derived pick (offense-leaning → Aggressive, defense-leaning → Passive).
     */
    private AIStrategy archetypeFor(BattleCombatant combatant) {
        Character character = combatant.getCharacter();
        String id = character.getId();
        if (AGGRESSIVE_IDS.contains(id)) return aggressiveStrategy;
        if (PASSIVE_IDS.contains(id)) return passiveStrategy;
        return statLeansAggressive(combatant) ? aggressiveStrategy : passiveStrategy;
    }

    private static boolean statLeansAggressive(BattleCombatant combatant) {
        int offense = combatant.getRuntimeStat(StatKey.STRENGTH)
            + combatant.getRuntimeStat(StatKey.COMBAT_ABILITY)
            + combatant.getRuntimeStat(StatKey.CURSED_ENERGY_OUTPUT);
        int defense = combatant.getRuntimeStat(StatKey.DURABILITY)
            + combatant.getRuntimeStat(StatKey.VITALITY)
            + combatant.getRuntimeStat(StatKey.JUJUTSU_SKILL);
        return offense >= defense;
    }
}
