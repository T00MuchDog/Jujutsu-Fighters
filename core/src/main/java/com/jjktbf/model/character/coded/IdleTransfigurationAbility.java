package com.jjktbf.model.character.coded;

import com.jjktbf.model.character.StatKey;
import com.jjktbf.model.combat.AbilityTrigger;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatantId;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.move.StatusEffectType;
import com.jjktbf.model.progression.TechniqueMasteryResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Function;

/**
 * Runtime implementation for Idle Transfiguration.
 *
 * <p>Owns three editable features plus one coded move action:</p>
 * <ul>
 *   <li>{@code MAINTAINING_THE_SOUL} — pays CE every resolution tick and
 *       restores the HP lost to each non-soul damage instance, at a CE cost
 *       bracketed by the final applied damage.</li>
 *   <li>{@code MALLEABLE_BODY} — integrates with the restoration: a restored
 *       body does not keep anatomy-dependent injury statuses.</li>
 *   <li>{@code SOUL_MANIPULATION} — every successful melee hit rolls a small
 *       chance to attempt transfiguring the target's soul through the shared
 *       {@link #attemptSoulManipulation} resolver.</li>
 *   <li>action {@code SOUL_MANIPULATION} — the same resolver invoked as a
 *       guaranteed coded row, both from the dedicated technique move and from
 *       Domain sure-hit programs.</li>
 * </ul>
 */
public final class IdleTransfigurationAbility implements CodedAbilityRuntime {

    public static final String KEY = "IDLE_TRANSFIGURATION";
    public static final String MAINTAINING_THE_SOUL = "MAINTAINING_THE_SOUL";
    public static final String MALLEABLE_BODY = "MALLEABLE_BODY";
    public static final String SOUL_MANIPULATION = "SOUL_MANIPULATION";

    /** Coded move action: attempt to transfigure the target's soul. */
    public static final String ACTION_SOUL_MANIPULATION = "SOUL_MANIPULATION";

    // ── Maintaining the Soul parameters ──────────────────────────────────────
    public static final String CE_DRAIN_PER_TICK = "ceDrainPerTick";
    public static final int DEFAULT_CE_DRAIN_PER_TICK = 2;

    /**
     * CE cost of restoring one damage instance, by final applied damage.
     * Spec-mandated mechanic, not a tuning value.
     */
    private static final int[][] RESTORATION_CE_BRACKETS = {
        {10, 10}, {30, 30}, {50, 50}, {100, 100}, {Integer.MAX_VALUE, 300}
    };

    // ── Soul Manipulation parameters ─────────────────────────────────────────
    public static final String PROC_CHANCE_PERCENT = "procChancePercent";
    public static final String BASE_SUCCESS_PERCENT = "baseSuccessPercent";
    public static final String CTM_SUCCESS_PER_TEN_POINTS = "ctmSuccessPerTenPoints";
    public static final String SUCCESS_PER_STACK_PERCENT = "successPerStackPercent";
    public static final String RESIST_PER_TEN_CE = "resistPerTenCe";
    public static final String MIN_SUCCESS_PERCENT = "minSuccessPercent";
    public static final String MAX_SUCCESS_PERCENT = "maxSuccessPercent";

    public static final int DEFAULT_PROC_CHANCE_PERCENT = 5;
    public static final int DEFAULT_BASE_SUCCESS_PERCENT = 5;
    public static final int DEFAULT_CTM_SUCCESS_PER_TEN_POINTS = 1;
    public static final int DEFAULT_SUCCESS_PER_STACK_PERCENT = 12;
    public static final int DEFAULT_RESIST_PER_TEN_CE = 1;
    public static final int DEFAULT_MIN_SUCCESS_PERCENT = 1;
    public static final int DEFAULT_MAX_SUCCESS_PERCENT = 95;

    private final BattleCombatant owner;
    private final Set<String> features;
    private final Map<String, List<CodedAbilityBinding>> bindingsByFeature;
    /** Failed transfiguration attempts per target, persistent for the battle. */
    private final Map<CombatantId, Integer> soulManipulationStacks = new LinkedHashMap<>();

    IdleTransfigurationAbility(
        BattleCombatant owner,
        Set<String> features,
        Map<String, List<CodedAbilityBinding>> bindingsByFeature
    ) {
        this.owner = owner;
        this.features = features == null ? Set.of() : Set.copyOf(features);
        this.bindingsByFeature = bindingsByFeature == null ? Map.of() : bindingsByFeature;
    }

    // ── Passive behaviour ─────────────────────────────────────────────────────

    @Override
    public List<CombatEvent> onTrigger(
        BattleState state,
        AbilityTrigger trigger,
        Predicate<String> featureActive,
        RandomSource rng
    ) {
        return onTrigger(state, trigger, featureActive, rng, ignored -> List.of());
    }

    @Override
    public List<CombatEvent> onTrigger(
        BattleState state, AbilityTrigger trigger, Predicate<String> featureActive,
        RandomSource rng, Function<AbilityTrigger, List<CombatEvent>> reactions
    ) {
        if (owner == null || !owner.isActive()) return List.of();
        switch (trigger.type()) {
            case TIMELINE_TICK: {
                if (!featureActive.test(MAINTAINING_THE_SOUL)) return List.of();
                return drainSoulMaintenance(trigger.tick());
            }
            case DAMAGE: {
                if (trigger.target() != owner || trigger.amount() <= 0) return List.of();
                if (trigger.soulDamage()) return List.of();
                if (!featureActive.test(MAINTAINING_THE_SOUL)) return List.of();
                return maintainTheSoul(trigger.amount(), trigger.tick());
            }
            case ATTACK_HIT: {
                if (!featureActive.test(SOUL_MANIPULATION) || rng == null) return List.of();
                if (trigger.actor() != owner || trigger.target() == null) return List.of();
                if (trigger.hitComponent() == null || !trigger.hitComponent().isMelee()) {
                    return List.of();
                }
                return rollPassiveSoulProc(trigger.target(), rng, trigger.tick(), reactions);
            }
            default:
                return List.of();
        }
    }

    @Override
    public List<CombatEvent> onTrigger(
        BattleState state,
        AbilityTrigger trigger,
        Predicate<String> featureActive
    ) {
        return onTrigger(state, trigger, featureActive, null);
    }

    // ── Coded rows (move + Domain sure-hit) ──────────────────────────────────

    @Override
    public List<CombatEvent> onEffectFired(
        BattleState state,
        StatusEffect effect,
        BattleCombatant attacker,
        BattleCombatant defender,
        int tick,
        RandomSource rng
    ) {
        return onEffectFired(state, effect, attacker, defender, tick, rng, ignored -> List.of());
    }

    @Override
    public List<CombatEvent> onEffectFired(
        BattleState state, StatusEffect effect, BattleCombatant attacker,
        BattleCombatant defender, int tick, RandomSource rng,
        Function<AbilityTrigger, List<CombatEvent>> reactions
    ) {
        if (!KEY.equalsIgnoreCase(effect.getCodedAbilityKey())
            || !ACTION_SOUL_MANIPULATION.equalsIgnoreCase(effect.getCodedAction())) {
            return List.of();
        }
        if (attacker != owner || defender == null) return List.of();
        if (!defender.isActive() || defender.isDefeated() || defender.isAlliedWith(owner)) {
            return List.of();
        }
        if (rng == null) return List.of();
        return attemptSoulManipulation(defender, rng, tick, reactions);
    }

    // ── Maintaining the Soul ──────────────────────────────────────────────────

    private List<CombatEvent> drainSoulMaintenance(int tick) {
        int drain = featureParameter(
            MAINTAINING_THE_SOUL, CE_DRAIN_PER_TICK, DEFAULT_CE_DRAIN_PER_TICK);
        int drained = owner.drainCe(drain);
        if (drained <= 0) return List.of();
        return List.of(CombatEvent.of(CombatEvent.Type.CE_DRAINED)
            .source(owner).target(owner).intValue(drained).tick(tick)
            .message(owner.getCharacter().getName()
                + " spends CE maintaining the shape of their soul.")
            .build());
    }

    private List<CombatEvent> maintainTheSoul(int damage, int tick) {
        List<CombatEvent> events = new ArrayList<>();
        // An instance large enough to exceed maximum HP defeats the owner
        // outright; the soul's shape is not restored from it.
        if (owner.isDefeated() || damage >= owner.getMaxHp()) return events;
        int cost = restorationCeCost(damage);
        if (owner.getCurrentCe() < cost) {
            events.add(CombatEvent.of(CombatEvent.Type.EFFECT_FAILED)
                .source(owner).target(owner).intValue(damage).tick(tick)
                .message(owner.getCharacter().getName() + " cannot afford to reshape "
                    + damage + " damage (needs " + cost + " CE).")
                .build());
            return events;
        }
        owner.drainCe(cost);
        int restored = owner.heal(damage);
        events.add(CombatEvent.of(CombatEvent.Type.CE_DRAINED)
            .source(owner).target(owner).intValue(cost).tick(tick).build());
        if (restored > 0) {
            events.add(CombatEvent.of(CombatEvent.Type.HP_RESTORED)
                .source(owner).target(owner).intValue(restored).tick(tick)
                .message(owner.getCharacter().getName() + "'s body reforms to the shape "
                    + "of their soul, restoring " + restored + " HP.")
                .build());
        }
        // Malleable Body integrates with the restoration rather than forming a
        // second healing system: only a body that actually reformed sheds its
        // anatomy-dependent injuries.
        if (features.contains(MALLEABLE_BODY)) {
            events.addAll(reshapeBodilyInjuries(tick));
        }
        return events;
    }

    /** Malleable Body: a restored body does not keep anatomy-dependent injuries. */
    private List<CombatEvent> reshapeBodilyInjuries(int tick) {
        List<CombatEvent> events = new ArrayList<>();
        for (StatusEffectType type : StatusEffectType.values()) {
            if (!type.isBodilyInjury() || !owner.hasEffect(type)) continue;
            int removed = owner.removeStatusEffects(type);
            if (removed <= 0) continue;
            events.add(CombatEvent.of(CombatEvent.Type.STATUS_EXPIRED)
                .source(owner).target(owner).tick(tick)
                .message(owner.getCharacter().getName() + "'s reformed body sheds "
                    + type.displayName() + ".")
                .build());
        }
        return events;
    }

    /** CE cost of restoring one damage instance, from the final applied damage. */
    public static int restorationCeCost(int damage) {
        for (int[] bracket : RESTORATION_CE_BRACKETS) {
            if (damage <= bracket[0]) return bracket[1];
        }
        return RESTORATION_CE_BRACKETS[RESTORATION_CE_BRACKETS.length - 1][1];
    }

    // ── Soul Manipulation ─────────────────────────────────────────────────────

    private List<CombatEvent> rollPassiveSoulProc(
        BattleCombatant defender,
        RandomSource rng,
        int tick,
        Function<AbilityTrigger, List<CombatEvent>> reactions
    ) {
        if (!defender.isActive() || defender.isDefeated() || defender.isAlliedWith(owner)) {
            return List.of();
        }
        int procChance = featureParameter(
            SOUL_MANIPULATION, PROC_CHANCE_PERCENT, DEFAULT_PROC_CHANCE_PERCENT);
        if (rng.nextDouble() >= procChance / 100.0) return List.of();
        return attemptSoulManipulation(defender, rng, tick, reactions);
    }

    /**
     * The one canonical transfiguration resolver. Success probability rises
     * with the attacker's effective Cursed Technique Mastery and with each
     * previously failed attempt on the target, and falls with the target's
     * soul resistance — their current cursed energy capped by their effective
     * Cursed Energy Output. Success transfigures (defeats) the target unless
     * fatal protection intervenes; failure leaves a persistent stack.
     */
    public List<CombatEvent> attemptSoulManipulation(
        BattleCombatant defender,
        RandomSource rng,
        int tick
    ) {
        return attemptSoulManipulation(defender, rng, tick, ignored -> List.of());
    }

    private List<CombatEvent> attemptSoulManipulation(
        BattleCombatant defender, RandomSource rng, int tick,
        Function<AbilityTrigger, List<CombatEvent>> reactions
    ) {
        if (!owner.isActive() || owner.isDefeated() || !defender.isActive()
            || defender.isDefeated() || defender.isAlliedWith(owner)) {
            return List.of();
        }
        List<CombatEvent> events = new ArrayList<>(reactions.apply(AbilityTrigger.move(
            AbilityTrigger.Type.SOUL_MANIPULATION_ATTEMPT, owner, defender, null, tick)));
        if (events.stream().anyMatch(event ->
            event.getType() == CombatEvent.Type.SOUL_MANIPULATION_NEGATED)) return events;
        if (!owner.isActive() || owner.isDefeated()
            || !defender.isActive() || defender.isDefeated()) return events;
        int chance = successChancePercent(defender);
        boolean succeeds = rng.nextDouble() < chance / 100.0;
        String defenderName = defender.getCharacter().getName();
        if (succeeds) {
            // Honors compiled fatal protection (e.g. Miracles) and any installed
            // SURVIVE_FATAL_DAMAGE runtime effect inside receiveInstantKill.
            defender.receiveInstantKill(fatalAmount ->
                defender.getCodedAbilities().preventFatalDamage(feature -> true));
            if (defender.isDefeated()) {
                soulManipulationStacks.remove(defender.getInstanceId());
                events.add(CombatEvent.of(CombatEvent.Type.ABILITY_ACTIVATED)
                    .source(owner).target(defender).tick(tick)
                    .intValue(chance)
                    .message(owner.getCharacter().getName()
                        + "'s Soul Manipulation activates. " + defenderName
                        + " fails to resist and is transformed into something inhuman.")
                    .build());
                return events;
            }
            // The roll succeeded, so the soul did not resist: fatal protection
            // averted the kill itself. A landed attempt teaches nothing new,
            // so unlike a resisted roll it leaves no stack behind.
            events.add(CombatEvent.of(CombatEvent.Type.ABILITY_ACTIVATED)
                .source(owner).target(defender).tick(tick)
                .intValue(chance)
                .message(owner.getCharacter().getName()
                    + "'s Soul Manipulation activates. " + defenderName
                    + " fails to resist and the transformation turns fatal — but "
                    + "death is averted.")
                .build());
            // The aversion belongs to this instant, not the next drain
            // checkpoint, so the attempt narrates as one atomic exchange.
            events.addAll(defender.getCodedAbilities().drainPendingEvents(tick));
            return events;
        }
        soulManipulationStacks.merge(defender.getInstanceId(), 1, Integer::sum);
        events.add(CombatEvent.of(CombatEvent.Type.ABILITY_ACTIVATED)
            .source(owner).target(defender).tick(tick)
            .intValue(chance)
            .message(owner.getCharacter().getName()
                + "'s Soul Manipulation activates. " + defenderName + " resists. "
                + owner.getCharacter().getName() + " gains a deeper understanding of "
                + defenderName + "'s soul.")
            .build());
        return events;
    }

    /** Success probability in percent for one attempt against the target. */
    public int successChancePercent(BattleCombatant defender) {
        int mastery = TechniqueMasteryResolver.masteryOf(owner);
        int resistance = Math.min(
            defender.getCurrentCe(),
            defender.getRuntimeStat(StatKey.CURSED_ENERGY_OUTPUT));
        int stacks = soulManipulationStacks.getOrDefault(defender.getInstanceId(), 0);
        int chance = featureParameter(
                SOUL_MANIPULATION, BASE_SUCCESS_PERCENT, DEFAULT_BASE_SUCCESS_PERCENT)
            + featureParameter(SOUL_MANIPULATION, CTM_SUCCESS_PER_TEN_POINTS,
                DEFAULT_CTM_SUCCESS_PER_TEN_POINTS) * (mastery / 10)
            + featureParameter(SOUL_MANIPULATION, SUCCESS_PER_STACK_PERCENT,
                DEFAULT_SUCCESS_PER_STACK_PERCENT) * stacks
            - featureParameter(SOUL_MANIPULATION, RESIST_PER_TEN_CE,
                DEFAULT_RESIST_PER_TEN_CE) * (resistance / 10);
        int minimum = featureParameter(
            SOUL_MANIPULATION, MIN_SUCCESS_PERCENT, DEFAULT_MIN_SUCCESS_PERCENT);
        int maximum = featureParameter(
            SOUL_MANIPULATION, MAX_SUCCESS_PERCENT, DEFAULT_MAX_SUCCESS_PERCENT);
        return Math.max(minimum, Math.min(maximum, chance));
    }

    /** Failed attempts recorded against one target this battle. */
    public int soulManipulationStacks(BattleCombatant defender) {
        return soulManipulationStacks.getOrDefault(defender.getInstanceId(), 0);
    }

    public static boolean supportsFeature(String feature) {
        return MAINTAINING_THE_SOUL.equals(feature)
            || MALLEABLE_BODY.equals(feature)
            || SOUL_MANIPULATION.equals(feature);
    }

    // ── Baseline interface methods ────────────────────────────────────────────

    @Override
    public List<CombatEvent> drainPendingEvents(int tick) {
        return List.of();
    }

    @Override
    public CodedAbilityState state() {
        // Capability marker (maximum 0): the current value reports how many
        // failed transfiguration attempts this combatant has learned from, so
        // AI valuation and serialized snapshots can see soul vulnerability.
        int learnedShapes = soulManipulationStacks.values().stream()
            .mapToInt(Integer::intValue).sum();
        return new CodedAbilityState(KEY, "Idle Transfiguration", learnedShapes, 0, false);
    }

    private int featureParameter(String feature, String parameter, int fallback) {
        List<CodedAbilityBinding> bindings = bindingsByFeature.getOrDefault(feature, List.of());
        if (bindings.isEmpty() || bindings.get(0).effect() == null) return fallback;
        var resolved = TechniqueMasteryResolver.resolve(
            bindings.get(0).effect(), owner);
        return TechniqueMasteryResolver.codedParameter(
            resolved.codedParameters, parameter, fallback);
    }
}
