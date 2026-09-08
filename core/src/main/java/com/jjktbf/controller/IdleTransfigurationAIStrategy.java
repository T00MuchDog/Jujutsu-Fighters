package com.jjktbf.controller;

import com.jjktbf.model.character.AbilityEffectData;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.MoveAvailability;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveEffectData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI archetype for Idle Transfiguration (Mahito).
 *
 * <p>A playful, patient finisher who chips at the enemy's soul rather than
 * their body:
 * <ul>
 *   <li>Values the guaranteed Soul Manipulation touch more as the target's
 *       cursed energy drains and as failed attempts accumulate (both inputs
 *       are read from battle state, never from named opponents).</li>
 *   <li>Spends the Transfigured Human stock deliberately: the assault only
 *       fires at healthy targets, and a summon appears only when nothing of
 *       his own is fielded and the opponent's committed offence justifies
 *       the pressure.</li>
 *   <li>Combos control into transfiguration: a stunning attack placed a
 *       couple of ticks ahead of the touch.</li>
 *   <li>Shapes legs for mobility only when outsped, and leans on the spike
 *       ball as wide offence once the shaping is done.</li>
 *   <li>Considers the Domain once the target's soul resistance is low.</li>
 * </ul>
 *
 * <p>State-aware ({@link #buildPlan}); {@link #selectPlan} is the degraded
 * single-opponent fallback.
 */
public class IdleTransfigurationAIStrategy implements AIStrategy {

    /** Stock never spent below this many Transfigured Humans unless closing a kill. */
    private static final int STOCK_RESERVE = 2;
    /** Enemy current-CE fraction at which soul attempts become very attractive. */
    private static final double ENEMY_CE_DRAINED_FRACTION = 0.35;
    private static final int ATTACK_CAP = 3;

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

    private BattlePlan placeMoves(
        BattleState state, BattleCombatant ai, BattleCombatant opponent, RandomSource rng
    ) {
        int gridLength = Timeline.gridLengthForStrongestAp(
            Math.max(ai.getMaxApBar(), opponent == null ? 0 : opponent.getMaxApBar()));
        BattlePlan plan = BattlePlan.forCombatant(ai, gridLength);
        OpponentIntel intel = OpponentIntel.forOpponent(opponent);

        Kit kit = classify(ai, state, opponent);
        int stock = kit.stockKey == null ? 0 : stockOf(ai, kit.stockKey);
        boolean closingKill = opponent != null && opponent.getCurrentHp() <= lethalishHp(ai);
        boolean healthyTarget = opponent == null
            || opponent.getCurrentHp() >= opponent.getMaxHp() / 2;

        // --- Domain: opened against drained, soul-vulnerable enemies. ---
        if (kit.domainMove != null) {
            double value = domainValue(state, ai, opponent, kit.domainMove);
            if (value > 0) {
                SmartAIScoring.placeAtOrAfter(
                    plan, kit.domainMove, ai.computeMoveCeCost(kit.domainMove), 1);
            }
        }

        // --- Control into touch: the signature sequence. ---
        if (kit.soulTouch != null && plan.canPlace(
                kit.soulTouch, ai.computeMoveCeCost(kit.soulTouch))) {
            var touchSegment = SmartAIScoring.placeAtOrAfter(
                plan, kit.soulTouch, ai.computeMoveCeCost(kit.soulTouch), 2);
            if (kit.control != null && touchSegment != null && plan.canPlace(
                    kit.control, ai.computeMoveCeCost(kit.control))) {
                // Land the pin just before the hand reaches the soul.
                SmartAIScoring.placeAtOrAfter(
                    plan, kit.control, ai.computeMoveCeCost(kit.control),
                    Math.max(1, touchSegment.getStartTick() - 2));
            }
        }

        // --- Mobility shaping only when outsped. ---
        if (kit.mobilityMove != null && opponent != null
                && opponent.getRuntimeStat(com.jjktbf.model.character.StatKey.SPEED)
                    > ai.getRuntimeStat(com.jjktbf.model.character.StatKey.SPEED)
                && plan.canPlace(kit.mobilityMove, ai.computeMoveCeCost(kit.mobilityMove))) {
            SmartAIScoring.placeAtOrAfter(
                plan, kit.mobilityMove, ai.computeMoveCeCost(kit.mobilityMove), 1);
        }

        // --- Resource spenders: the stock exists to be spent, deliberately. ---
        if (stock > 0 && (stock > STOCK_RESERVE || closingKill)) {
            if (kit.assault != null && healthyTarget && plan.canPlace(
                    kit.assault, ai.computeMoveCeCost(kit.assault))) {
                SmartAIScoring.placeAtOrAfter(
                    plan, kit.assault, ai.computeMoveCeCost(kit.assault), 3);
            }
            boolean fielded = state == null
                || state.directActiveSummonCount(ai) + state.directPendingSummonCount(ai) > 0;
            if (kit.summon != null && !fielded && intel.committedAttackFireTicks.size() >= 2
                && plan.canPlace(kit.summon, ai.computeMoveCeCost(kit.summon))) {
                SmartAIScoring.placeAtOrAfter(
                    plan, kit.summon, ai.computeMoveCeCost(kit.summon), 1);
            }
        }

        // --- Straight offence fills the rest of the plan. ---
        placeOffence(ai, plan, kit.technique, kit.plainAttacks);

        // --- Counter the likely incoming shape. ---
        placeDefenses(ai, plan, kit, intel, opponent);
        return plan;
    }

    // -------------------------------------------------------------------------
    // Valuation
    // -------------------------------------------------------------------------

    /**
     * The Domain's value rises as the target's soul resistance falls (low
     * current CE) and as failed transfigurations accumulate. Reports 0 when
     * restricted by the generic Domain-opening rules.
     */
    private double domainValue(
        BattleState state, BattleCombatant ai, BattleCombatant opponent, Move domainMove
    ) {
        if (state == null || opponent == null || domainLookup() == null) return 0.0;
        if (SmartAIScoring.domainOpeningRestriction(
                domainLookup(), state, ai, domainMove) != null) {
            return 0.0;
        }
        double resistance = enemyCeFraction(opponent);
        double learned = learnedShapes(ai);
        double value = SmartAIScoring.domainMoveValue(
            domainLookup(), state, ai, domainMove);
        if (resistance <= ENEMY_CE_DRAINED_FRACTION) value *= 2.0;
        value *= 1.0 + learned * 0.25;
        return value;
    }

    private static int learnedShapes(BattleCombatant ai) {
        return ai.getCodedAbilities().state(com.jjktbf.model.character.coded.IdleTransfigurationAbility.KEY)
            .map(state -> state.currentValue())
            .orElse(0);
    }

    private static double enemyCeFraction(BattleCombatant opponent) {
        int max = opponent.getMaxCursedEnergy();
        return max <= 0 ? 1.0 : Math.min(1.0, opponent.getCurrentCe() / (double) max);
    }

    private static int stockOf(BattleCombatant ai, String key) {
        return ai.boundedResourceValue(key).orElse(0);
    }

    /** Rough one-or-two-hit lethal band; the stock is never hoarded for a kill. */
    private static int lethalishHp(BattleCombatant ai) {
        return Math.max(20, ai.getMaxHp() / 10);
    }

    // -------------------------------------------------------------------------
    // Structural classification (data-driven, no ids or names)
    // -------------------------------------------------------------------------

    private record Kit(
        Move soulTouch,
        Move control,
        Move mobilityMove,
        Move assault,
        Move summon,
        Move domainMove,
        Move counterDefense,
        Move evasionDefense,
        String stockKey,
        List<Move> technique,
        List<Move> plainAttacks
    ) { }

    private static Kit classify(BattleCombatant ai, BattleState state, BattleCombatant opponent) {
        Move soulTouch = null;
        Move control = null;
        Move mobility = null;
        Move assault = null;
        Move summon = null;
        Move domain = null;
        Move counter = null;
        Move evasion = null;
        String stockKey = null;
        List<Move> technique = new ArrayList<>();
        List<Move> plainAttacks = new ArrayList<>();

        for (Move move : ai.getCharacter().getKnownMoves()) {
            if (state != null && !MoveAvailability.isAvailable(state, ai, move)) continue;
            if (!MoveAvailability.isAvailable(null, ai, move)) continue;

            if (establishesDomain(move)) {
                domain = move;
                continue;
            }
            if (move.summonsCharacter()) {
                summon = move;
                continue;
            }
            String spentStock = spentResource(move);
            if (spentStock != null) {
                stockKey = spentStock;
                assault = move;  // the only stock-spending attack shape
                continue;
            }
            if (move.isActiveDefense()) {
                if (move.getDefenseType() == DefenseType.DODGE && move.getDodgeScope()
                        .equals(com.jjktbf.model.move.DodgeScope.BOTH)) {
                    evasion = move;
                } else if (move.getDefenseType() == DefenseType.BLOCK
                        && dealsCounterDamage(move)) {
                    counter = move;
                }
                continue;
            }
            if (move.hasTag("ATTACK")) {
                if (hasSoulManipulationRow(move)) {
                    soulTouch = move;
                } else if (staggersTarget(move) && control == null) {
                    control = move;
                } else {
                    (move.hasTag("INNATE_TECHNIQUE") ? technique : plainAttacks)
                        .add(move);
                }
                continue;
            }
            if (mobility == null && isSelfSpeedBoost(move)) {
                mobility = move;
            }
        }
        return new Kit(soulTouch, control, mobility, assault, summon, domain,
            counter, evasion, stockKey, technique, plainAttacks);
    }

    private static boolean establishesDomain(Move move) {
        for (MoveEffectData effect : move.getEffects()) {
            if (AbilityEffectType.ESTABLISH_DOMAIN.name().equalsIgnoreCase(effect.type)) {
                return true;
            }
        }
        return false;
    }

    /** Resource key consumed by this move's guaranteed start transaction, if any. */
    private static String spentResource(Move move) {
        for (MoveEffectData effect : move.getEffects()) {
            if (!AbilityEffectType.TRANSACT_BOUNDED_RESOURCE.name().equalsIgnoreCase(effect.type)) {
                continue;
            }
            if (effect.sourceResourceKey != null
                    && effect.sourceResourceAmount != null && effect.sourceResourceAmount > 0) {
                return effect.sourceResourceKey;
            }
        }
        return null;
    }

    private static boolean hasSoulManipulationRow(Move move) {
        for (MoveEffectData effect : move.getEffects()) {
            if (AbilityEffectType.CODED_MOVE_ACTION.name().equalsIgnoreCase(effect.type)
                    && com.jjktbf.model.character.coded.IdleTransfigurationAbility.KEY
                        .equalsIgnoreCase(effect.codedAbilityKey)) {
                return true;
            }
        }
        return false;
    }

    private static boolean staggersTarget(Move move) {
        for (AbilityEffectData effect : move.getEffects()) {
            if (!AbilityEffectType.APPLY_STATUS.name().equalsIgnoreCase(effect.type)) continue;
            String status = effect.stringValue == null ? "" : effect.stringValue.trim().toUpperCase();
            if (status.equals("STAGGER") || status.equals("RESTRAINED")) return true;
        }
        return false;
    }

    private static boolean isSelfSpeedBoost(Move move) {
        for (MoveEffectData effect : move.getEffects()) {
            if (!AbilityEffectType.TIMED_STAT_MODIFIER.name().equalsIgnoreCase(effect.type)) continue;
            if (!"CORE".equalsIgnoreCase(effect.statType) || effect.stat == null) continue;
            if (!effect.stat.equalsIgnoreCase(
                    com.jjktbf.model.character.StatKey.SPEED.fieldName)) continue;
            double magnitude = effect.doubleValue == null ? 0.0 : effect.doubleValue;
            if (magnitude > 0.0) return true;
        }
        return false;
    }

    private static boolean dealsCounterDamage(Move move) {
        for (MoveEffectData effect : move.getEffects()) {
            if (AbilityEffectType.DEAL_DIRECT_DAMAGE.name().equalsIgnoreCase(effect.type)
                    && "ENEMY".equalsIgnoreCase(effect.target)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Offence and defense
    // -------------------------------------------------------------------------

    private void placeOffence(
        BattleCombatant ai,
        BattlePlan plan,
        List<Move> technique,
        List<Move> plainAttacks
    ) {
        int budget = ATTACK_CAP;
        // Technique attacks first — they are what the stockpile and slots exist
        // for; plain attacks only fill leftover budget.
        List<Move> ranked = new ArrayList<>(technique);
        ranked.addAll(plainAttacks);
        ranked.sort(java.util.Comparator.comparingInt(Move::getBasePower).reversed());
        for (Move move : ranked) {
            if (budget <= 0) break;
            if (!plan.canPlace(move, ai.computeMoveCeCost(move))) continue;
            SmartAIScoring.placeAtOrAfter(
                plan, move, ai.computeMoveCeCost(move), 4);
            budget--;
        }
    }

    private void placeDefenses(
        BattleCombatant ai, BattlePlan plan, Kit kit, OpponentIntel intel,
        BattleCombatant opponent) {
        // A block-counter answers melee pressure when the kit carries one;
        // everything else — or a ranged-leaning enemy — gets the wings.
        boolean meleeHeavy = !intel.attacks.isEmpty()
            && intel.attacks.stream().filter(IdleTransfigurationAIStrategy::isMelee).count()
                > intel.attacks.size() / 2;
        Move preferred = meleeHeavy && kit.counterDefense != null
            ? kit.counterDefense : kit.evasionDefense;
        if (preferred != null && plan.canPlace(
                preferred, ai.computeMoveCeCost(preferred))
                && !intel.committedAttackFireTicks.isEmpty()) {
            SmartAIScoring.placeAlignedToThreat(
                plan, preferred, ai.computeMoveCeCost(preferred),
                intel.committedAttackFireTicks.get(0), ai, opponent);
        }
    }

    private static boolean isMelee(Move move) {
        return move.getHitComponents().stream()
            .anyMatch(component -> component.isMelee());
    }
}
