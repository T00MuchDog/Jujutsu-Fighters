package com.jjktbf.controller;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.StatKey;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.MoveAvailability;
import com.jjktbf.model.combat.PowerCalculator;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.StatusEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI archetype for Noritoshi Kamo (Blood Manipulation).
 *
 * <p>A cautious, intelligent clan heir who fights a measured ranged game and
 * manages a finite blood economy:
 * <ul>
 *   <li><b>Cautious by default</b> — pokes with cheap attacks, keeps two layers
 *       of defense aligned to committed threats, and banks idle blood into
 *       {@code COMPRESSION} only while the enemy pressure is low.</li>
 *   <li><b>Flowing Red Scale escalation</b> — while his self core-stat surge is
 *       active (or the round he deliberately opens one) he flips aggressive:
 *       more attacks, one layer of defense, free blood spending, and a
 *       technique-attack press. He escalates deliberately when the enemy enters
 *       execution range, when he is cornered, or when a loaded finisher can
 *       kill.</li>
 *   <li><b>Blood move AI</b> — {@code BLOOD SUPPLY} never regenerates, so while
 *       cautious he keeps enough ammunition (blood + compression) to reach a
 *       Piercing Blood (convert + fire) before spending on anything else; a
 *       lethal spend is always allowed. A loaded lethal Piercing Blood is fired
 *       immediately and does not wait.</li>
 *   <li><b>Marksman intelligence</b> — opens cautious rounds with an accuracy
 *       focus when he has ranged pressure to land, presses bow attacks inside a
 *       Blood-Guided Arrow never-miss window (placing the aiming shot so later
 *       arrows fire inside it), and weights Crimson Binding against opponents
 *       committing many attacks or outrunning him.</li>
 * </ul>
 *
 * <p>Moves are classified structurally from their guaranteed ON_START resource
 * transactions and effect rows (blood spenders, compression converters, self
 * core-stat surges, never-miss grants, restraints) — never by id or name.
 * State-aware ({@link #buildPlan}) like the other technique archetypes;
 * {@link #selectPlan} is a single-opponent fallback.
 */
public class BloodManipulationAIStrategy implements AIStrategy {

    /** Bounded-resource keys of the Blood Manipulation kit (case-insensitive). */
    private static final String BLOOD = "BLOOD_SUPPLY";
    private static final String COMPRESSION = "COMPRESSION";

    // --- Stance tunables (code-only) ---
    /** Enemy HP fraction at or below which Flowing Red Scale escalation begins. */
    static final double EXECUTION_ENEMY_HP = 0.35;
    /** Own HP fraction at or below which caution is abandoned. */
    static final double DESPERATE_OWN_HP = 0.35;
    /** Blood + compression kept reachable for a convert-and-fire finisher while cautious. */
    static final int FINISHER_AMMO_RESERVE = 2;
    static final int ATTACK_CAP_CAUTIOUS = 2;
    static final int ATTACK_CAP_AGGRESSIVE = 3;
    static final int DEFENSE_CAP_CAUTIOUS = 2;
    static final int DEFENSE_CAP_AGGRESSIVE = 1;

    // --- Move-AI tunables (code-only) ---
    /** Conversions are only banked while at most this many enemy attacks are committed. */
    static final int QUIET_COMMITTED_ATTACKS = 1;
    /** Bow attack weight inside an active or in-plan Blood-Guided Arrow never-miss window. */
    static final double NEVER_MISS_BOW_BONUS = 1.8;
    static final int RESTRAINT_VS_ATTACKS_THRESHOLD = 2;
    static final double RESTRAINT_VS_ATTACKS_BOOST = 1.6;
    static final double RESTRAINT_VS_FASTER_BOOST = 1.6;
    /** Technique attack weight while aggressive (the surge window is for pressing). */
    static final double AGGRESSIVE_TECHNIQUE_BONUS = 1.5;
    /** Weight of a blood attack whose estimated damage can end the target. */
    static final double LETHAL_BLOOD_BONUS = 3.0;
    /** Cautious cheapness reference: weight scales with this over the move's AP cost. */
    static final double CHEAPNESS_REF = 30.0;
    /** Cautious slight dodge preference — a measured fighter keeps his distance. */
    static final double CAUTIOUS_DODGE_PREFERENCE = 1.25;
    /** AP room that must remain after an accuracy focus so a follow-up attack still fits. */
    static final int MIN_ATTACK_AP_ROOM = 12;

    /** A move paired with its guaranteed ON_START resource transactions. */
    private record AttackCard(Move move, List<AbilityEffectData> transactions) { }

    // -------------------------------------------------------------------------
    // Entries
    // -------------------------------------------------------------------------

    /** State-aware entry used by the dispatcher's team-plan build. */
    public BattlePlan buildPlan(BattleState state, BattleCombatant ai, RandomSource rng) {
        return placeMoves(state, ai, state.firstActiveEnemyOf(ai), rng);
    }

    /** Single-opponent fallback (interface contract). */
    @Override
    public BattlePlan selectPlan(BattleCombatant ai, BattleCombatant opponent, RandomSource rng) {
        return placeMoves(null, ai, opponent, rng);
    }

    // -------------------------------------------------------------------------
    // Placement
    // -------------------------------------------------------------------------

    private BattlePlan placeMoves(BattleState state, BattleCombatant ai,
                                  BattleCombatant opponent, RandomSource rng) {
        int gridLength = Timeline.gridLengthForStrongestAp(
            Math.max(ai.getMaxApBar(), opponent == null ? 0 : opponent.getMaxApBar()));
        BattlePlan plan = BattlePlan.forCombatant(ai, gridLength);
        OpponentIntel intel = OpponentIntel.forOpponent(opponent);

        List<AttackCard> attacks = new ArrayList<>();
        List<Move> defenses = new ArrayList<>();
        List<Move> surges = new ArrayList<>();     // Flowing Red Scale shape
        List<Move> converters = new ArrayList<>(); // Convergence shape
        List<Move> focus = new ArrayList<>();      // plain self accuracy utility
        for (Move move : ai.getCharacter().getKnownMoves()) {
            if (state != null && !MoveAvailability.isAvailable(state, ai, move)) continue;
            if (!MoveAvailability.isAvailable(null, ai, move)) continue;
            List<AbilityEffectData> transactions =
                MoveAvailability.guaranteedBoundedResourceTransactions(ai, move);
            if (gainsResource(transactions, COMPRESSION)) {
                converters.add(move);
            } else if (isSurge(move, transactions)) {
                surges.add(move);
            } else if (move.hasTag("ATTACK")) {
                attacks.add(new AttackCard(move, transactions));
            } else if (move.isActiveDefense()) {
                defenses.add(move);
            } else if (isAccuracyFocus(move, transactions)) {
                focus.add(move);
            }
        }

        int blood = resourceValue(ai, BLOOD);
        int compression = resourceValue(ai, COMPRESSION);
        int compressionCapacity = resourceCapacity(ai, COMPRESSION);
        boolean surgeActive = hasActiveSurge(ai);
        boolean neverMissActive = ai.hasActiveRuntimeEffect(
            BloodManipulationAIStrategy::isSelfNeverMiss);
        boolean lethalLoaded = compression >= 1 && opponent != null
            && lethalCompressionFinisher(attacks, ai, opponent) != null;

        // Aggressive while the surge window is open — or this round, if he opens one now.
        boolean escalate = wantsEscalation(
            surgeActive, blood, hpRatio(opponent), hpRatio(ai), lethalLoaded);
        boolean aggressive = surgeActive || escalate;

        // Attacks are placed at/after the last setup's fire tick so their ON_FIRE
        // windows (never-miss, the surge) are already running when they land.
        int afterSetups = 1;

        // --- Loaded lethal finisher first: the kill does not wait. ---
        AttackCard lethal = lethalLoaded
            ? lethalCompressionFinisher(attacks, ai, opponent) : null;
        if (lethal != null) {
            ActionSegment seg = SmartAIScoring.placeAtOrAfter(
                plan, lethal.move(), ai.computeMoveCeCost(lethal.move()), 1);
            if (seg != null) afterSetups = Math.max(afterSetups, seg.getFireTick() + 1);
        }

        // --- Flowing Red Scale: deliberate escalation. ---
        if (escalate) {
            afterSetups = Math.max(afterSetups, placeFirst(surges, ai, plan));
        }

        // --- Convergence: bank idle blood into compression when it is safe to stand still. ---
        if (wantsConversion(compression, compressionCapacity, blood, aggressive,
                intel.committedAttackFireTicks.size())) {
            Move converter = firstPlaceable(converters, ai, plan);
            if (converter != null) {
                ActionSegment seg = SmartAIScoring.placeAtOrAfter(
                    plan, converter, ai.computeMoveCeCost(converter), 1);
                if (seg != null) {
                    blood -= spentAmount(ai, converter, BLOOD);
                    compression += gainedAmount(ai, converter, COMPRESSION);
                }
            }
        }

        // --- Accuracy focus opener (cautious marksman, no never-miss window running). ---
        if (!aggressive && !neverMissActive && hasBowPressure(attacks)) {
            Move aim = firstPlaceable(focus, ai, plan);
            if (aim != null
                    && plan.remainingApBudget() - plan.effectiveApCost(aim) >= MIN_ATTACK_AP_ROOM) {
                ActionSegment seg = SmartAIScoring.placeAtOrAfter(
                    plan, aim, ai.computeMoveCeCost(aim), 1);
                if (seg != null) afterSetups = Math.max(afterSetups, seg.getFireTick() + 1);
            }
        }

        // --- Cautious: secure the defense layers first, then poke with what
        //     remains. Aggressive: press the offence, then keep one layer. ---
        int attackCap = aggressive ? ATTACK_CAP_AGGRESSIVE : ATTACK_CAP_CAUTIOUS;
        if (aggressive) {
            placeOffence(plan, ai, opponent, attacks, intel, rng,
                blood, compression, aggressive, neverMissActive, attackCap, afterSetups);
            placeDefenses(ai, opponent, plan, defenses, intel, aggressive);
        } else {
            placeDefenses(ai, opponent, plan, defenses, intel, aggressive);
            placeOffence(plan, ai, opponent, attacks, intel, rng,
                blood, compression, aggressive, neverMissActive, attackCap, afterSetups);
        }
        return plan;
    }

    // -------------------------------------------------------------------------
    // Offence
    // -------------------------------------------------------------------------

    private void placeOffence(
        BattlePlan plan, BattleCombatant ai, BattleCombatant opponent, List<AttackCard> attacks,
        OpponentIntel intel, RandomSource rng,
        int blood, int compression, boolean aggressive, boolean neverMissActive,
        int attackCap, int fromTick
    ) {
        Set<Move> stuck = new HashSet<>();
        boolean aimGrantedInPlan = neverMissActive;
        int cursor = fromTick;
        int placed = 0;
        while (placed < attackCap) {
            List<Move> pool = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (AttackCard card : attacks) {
                Move m = card.move();
                if (stuck.contains(m) || !plan.canPlace(m, ai.computeMoveCeCost(m))) continue;
                if (spendsResource(card.transactions(), BLOOD)
                        && !bloodSpendAllowed(aggressive, blood, compression)
                        && !isLethal(m, ai, opponent)) {
                    continue; // cautious finisher reserve
                }
                if (spendsResource(card.transactions(), COMPRESSION)
                        && !aggressive && !isLethal(m, ai, opponent)) {
                    continue; // the loaded finisher waits for a kill or the surge window
                }
                pool.add(m);
                weights.add(attackWeight(m, ai, opponent, intel, aggressive, aimGrantedInPlan));
            }
            Move pick = SmartAIScoring.weightedRandomPick(pool, weights, rng);
            if (pick == null) break;
            int ceCost = ai.computeMoveCeCost(pick);
            ActionSegment seg = SmartAIScoring.placeAtOrAfter(plan, pick, ceCost, cursor);
            if (seg == null) {
                stuck.add(pick);
            } else {
                placed++;
                if (spendsResource(transactionsOf(pick, attacks), BLOOD)) {
                    blood -= spentAmount(ai, pick, BLOOD);
                }
                if (spendsResource(transactionsOf(pick, attacks), COMPRESSION)) {
                    compression -= spentAmount(ai, pick, COMPRESSION);
                }
                if (grantsNeverMiss(pick)) {
                    // Later arrows fire inside the aiming shot's never-miss window.
                    aimGrantedInPlan = true;
                    cursor = Math.max(cursor, seg.getFireTick() + 1);
                }
            }
        }
    }

    private static double attackWeight(
        Move move, BattleCombatant ai, BattleCombatant opponent, OpponentIntel intel,
        boolean aggressive, boolean aimWindow
    ) {
        double power = Math.max(1, move.getTotalBasePower())
            * Math.max(1, PowerCalculator.compute(
                move.getCategory(), ai.getEffectiveStats(), ai.getStatMode()));
        double weight = power
            * SmartAIScoring.effectMultiplier(move)
            * SmartAIScoring.dodgeExposureMultiplier(move, intel)
            * SmartAIScoring.reinforcementAttackMultiplier(move, intel)
            * SmartAIScoring.defenseCrackMultiplier(move, intel);
        if (move.hasTag("BOW") && aimWindow) {
            weight *= NEVER_MISS_BOW_BONUS;
        }
        if (restrainsTarget(move) && opponent != null) {
            if (intel.committedAttackFireTicks.size() >= RESTRAINT_VS_ATTACKS_THRESHOLD) {
                weight *= RESTRAINT_VS_ATTACKS_BOOST;
            }
            if (opponent.getRuntimeStat(StatKey.SPEED) > ai.getRuntimeStat(StatKey.SPEED)) {
                weight *= RESTRAINT_VS_FASTER_BOOST;
            }
        }
        if (isLethal(move, ai, opponent)) {
            weight *= LETHAL_BLOOD_BONUS;
        } else if (aggressive && move.hasTag("INNATE_TECHNIQUE")) {
            weight *= AGGRESSIVE_TECHNIQUE_BONUS;
        } else if (!aggressive) {
            // Measured pokes: prefer leaving AP for defense.
            weight *= CHEAPNESS_REF / Math.max(1, move.getApCost());
        }
        return weight;
    }

    // -------------------------------------------------------------------------
    // Defense
    // -------------------------------------------------------------------------

    private void placeDefenses(
        BattleCombatant ai, BattleCombatant opponent, BattlePlan plan,
        List<Move> defenses, OpponentIntel intel, boolean aggressive
    ) {
        int cap = aggressive ? DEFENSE_CAP_AGGRESSIVE : DEFENSE_CAP_CAUTIOUS;
        List<Move> useful = new ArrayList<>();
        for (Move d : defenses) {
            double value = SmartAIScoring.defenseValue(d, intel);
            if (!aggressive && d.isDodge()) value *= CAUTIOUS_DODGE_PREFERENCE;
            if (value > 0 && plan.canPlace(d, ai.computeMoveCeCost(d))) useful.add(d);
        }
        useful.sort(Comparator.comparingDouble(
            (Move d) -> SmartAIScoring.defenseValue(d, intel)).reversed());
        int placed = 0;
        for (Move d : useful) {
            if (placed >= cap) break;
            int ceCost = ai.computeMoveCeCost(d);
            ActionSegment seg = null;
            if (placed == 0 && !intel.committedAttackFireTicks.isEmpty()) {
                // Align the first layer to the earliest committed threat.
                seg = SmartAIScoring.placeAlignedToThreat(
                    plan, d, ceCost, intel.committedAttackFireTicks.get(0), ai, opponent);
            }
            if (seg == null) {
                seg = SmartAIScoring.placeAtOrAfter(plan, d, ceCost, 1);
            }
            if (seg != null) placed++;
        }
    }

    // -------------------------------------------------------------------------
    // Decision rules (package-private for testing)
    // -------------------------------------------------------------------------

    /**
     * Whether Kamo should open a Flowing Red Scale window this round: never while
     * one is already active or blood is empty; deliberately when the enemy is in
     * execution range, when he is cornered, or when a loaded finisher can kill.
     */
    static boolean wantsEscalation(
        boolean surgeActive, int blood, double enemyHpRatio,
        double ownHpRatio, boolean lethalLoaded
    ) {
        if (surgeActive || blood < 1) return false;
        return enemyHpRatio <= EXECUTION_ENEMY_HP
            || ownHpRatio <= DESPERATE_OWN_HP
            || lethalLoaded;
    }

    /**
     * Whether banking blood into compression is right now: only with conversion
     * headroom and blood in hand, freely while aggressive, otherwise only while
     * the enemy's committed pressure is quiet.
     */
    static boolean wantsConversion(
        int compression, int compressionCapacity, int blood,
        boolean aggressive, int committedEnemyAttacks
    ) {
        if (compressionCapacity <= 0 || compression >= compressionCapacity || blood < 1) {
            return false;
        }
        return aggressive || committedEnemyAttacks <= QUIET_COMMITTED_ATTACKS;
    }

    /**
     * Whether spending one blood is allowed: aggressive spends freely; cautious
     * keeps enough ammunition (blood + compression) to still reach a
     * convert-and-fire Piercing Blood afterwards.
     */
    static boolean bloodSpendAllowed(boolean aggressive, int blood, int compression) {
        if (blood < 1) return false;
        return aggressive || blood - 1 + compression >= FINISHER_AMMO_RESERVE;
    }

    // -------------------------------------------------------------------------
    // Structural move classification (data-driven, no ids or names)
    // -------------------------------------------------------------------------

    /** A non-attacking blood spender that grants a self core-stat surge. */
    private static boolean isSurge(Move move, List<AbilityEffectData> transactions) {
        if (move.hasTag("ATTACK") || !spendsResource(transactions, BLOOD)) return false;
        for (MoveEffectData effect : move.getEffects()) {
            if (isSelfCoreStatBoost(effect)) return true;
        }
        return false;
    }

    /** A free self accuracy buff (the plain Cursed Energy Focus shape). */
    private static boolean isAccuracyFocus(Move move, List<AbilityEffectData> transactions) {
        if (!transactions.isEmpty()) return false;
        for (MoveEffectData effect : move.getEffects()) {
            if (!AbilityEffectType.TIMED_STAT_MODIFIER.name().equalsIgnoreCase(effect.type)) continue;
            if (!targetsSelf(effect)) continue;
            if (AbilityEffectType.statType(effect) != AbilityEffectType.StatType.BATTLE) continue;
            double magnitude = effect.doubleValue == null ? 0.0 : effect.doubleValue;
            if (magnitude > 0.0) return true;
        }
        return false;
    }

    private static boolean isSelfCoreStatBoost(AbilityEffectData effect) {
        if (!AbilityEffectType.TIMED_STAT_MODIFIER.name().equalsIgnoreCase(effect.type)) return false;
        if (!targetsSelf(effect)) return false;
        if (AbilityEffectType.statType(effect) != AbilityEffectType.StatType.CORE) return false;
        double magnitude = effect.doubleValue == null ? 0.0 : effect.doubleValue;
        return magnitude > 0.0;
    }

    private static boolean isSelfNeverMiss(AbilityEffectData effect) {
        return AbilityEffectType.APPLY_NEVER_MISS.name().equalsIgnoreCase(effect.type)
            && targetsSelf(effect);
    }

    private static boolean grantsNeverMiss(Move move) {
        for (MoveEffectData effect : move.getEffects()) {
            if (isSelfNeverMiss(effect)) return true;
        }
        return false;
    }

    /** An attack that applies RESTRAINED (the Crimson Binding shape). */
    private static boolean restrainsTarget(Move move) {
        for (MoveEffectData effect : move.getEffects()) {
            if (!AbilityEffectType.APPLY_STATUS.name().equalsIgnoreCase(effect.type)) continue;
            if (effect.stringValue != null
                    && StatusEffectType.RESTRAINED.name().equalsIgnoreCase(effect.stringValue.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean targetsSelf(AbilityEffectData effect) {
        return effect.target != null && "SELF".equalsIgnoreCase(effect.target.trim());
    }

    private static boolean spendsResource(List<AbilityEffectData> transactions, String key) {
        for (AbilityEffectData t : transactions) {
            int amount = t.sourceResourceAmount == null ? 0 : t.sourceResourceAmount;
            if (amount > 0 && key.equalsIgnoreCase(t.sourceResourceKey)) return true;
        }
        return false;
    }

    private static boolean gainsResource(List<AbilityEffectData> transactions, String key) {
        for (AbilityEffectData t : transactions) {
            int amount = t.targetResourceAmount == null ? 0 : t.targetResourceAmount;
            if (amount > 0 && key.equalsIgnoreCase(t.targetResourceKey)) return true;
        }
        return false;
    }

    private static int spentAmount(BattleCombatant ai, Move move, String key) {
        return MoveAvailability.guaranteedBoundedResourceTransactions(ai, move).stream()
            .filter(t -> key.equalsIgnoreCase(t.sourceResourceKey))
            .mapToInt(t -> t.sourceResourceAmount == null ? 0 : t.sourceResourceAmount)
            .max().orElse(0);
    }

    private static int gainedAmount(BattleCombatant ai, Move move, String key) {
        return MoveAvailability.guaranteedBoundedResourceTransactions(ai, move).stream()
            .filter(t -> key.equalsIgnoreCase(t.targetResourceKey))
            .mapToInt(t -> t.targetResourceAmount == null ? 0 : t.targetResourceAmount)
            .max().orElse(0);
    }

    // -------------------------------------------------------------------------
    // State helpers
    // -------------------------------------------------------------------------

    /** True while a self core-stat surge (the Flowing Red Scale window) is active. */
    private static boolean hasActiveSurge(BattleCombatant ai) {
        return ai.hasActiveRuntimeEffect(BloodManipulationAIStrategy::isSelfCoreStatBoost);
    }

    private static int resourceValue(BattleCombatant ai, String key) {
        return ai.boundedResourceValue(key).orElse(0);
    }

    private static int resourceCapacity(BattleCombatant ai, String key) {
        return ai.abilityState(key)
            .map(state -> state.maximumValue())
            .orElse(0);
    }

    private static double hpRatio(BattleCombatant c) {
        return c == null || c.getMaxHp() <= 0 ? 1.0
            : (double) c.getCurrentHp() / c.getMaxHp();
    }

    private static boolean isLethal(Move move, BattleCombatant ai, BattleCombatant opponent) {
        return opponent != null
            && SmartAIScoring.estimatedDamage(move, ai, opponent) >= opponent.getCurrentHp();
    }

    /** The deadliest compression spender whose estimated damage can end the target. */
    private static AttackCard lethalCompressionFinisher(
        List<AttackCard> attacks, BattleCombatant ai, BattleCombatant opponent
    ) {
        AttackCard best = null;
        int bestDamage = -1;
        for (AttackCard card : attacks) {
            if (!spendsResource(card.transactions(), COMPRESSION)) continue;
            int damage = SmartAIScoring.estimatedDamage(card.move(), ai, opponent);
            if (damage >= opponent.getCurrentHp() && damage > bestDamage) {
                best = card;
                bestDamage = damage;
            }
        }
        return best;
    }

    private static boolean hasBowPressure(List<AttackCard> attacks) {
        for (AttackCard card : attacks) {
            if (card.move().hasTag("BOW")) return true;
        }
        return false;
    }

    private static List<AbilityEffectData> transactionsOf(Move move, List<AttackCard> attacks) {
        for (AttackCard card : attacks) {
            if (card.move() == move) return card.transactions();
        }
        return List.of();
    }

    /** Place the cheapest placeable candidate of a setup pool; returns its fire tick (0 when none placed). */
    private static int placeFirst(List<Move> pool, BattleCombatant ai, BattlePlan plan) {
        Move pick = firstPlaceable(pool, ai, plan);
        if (pick == null) return 0;
        ActionSegment seg = SmartAIScoring.placeAtOrAfter(
            plan, pick, ai.computeMoveCeCost(pick), 1);
        return seg == null ? 0 : seg.getFireTick();
    }

    private static Move firstPlaceable(List<Move> pool, BattleCombatant ai, BattlePlan plan) {
        List<Move> candidates = new ArrayList<>(pool);
        candidates.sort(Comparator.comparingInt(ai::computeMoveCeCost));
        for (Move m : candidates) {
            if (plan.canPlace(m, ai.computeMoveCeCost(m))) return m;
        }
        return null;
    }
}
