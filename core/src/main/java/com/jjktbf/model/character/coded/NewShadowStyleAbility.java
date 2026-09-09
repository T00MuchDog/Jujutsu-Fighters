package com.jjktbf.model.character.coded;

import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.combat.AbilityTrigger;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.domain.DomainBattlefield;
import com.jjktbf.model.domain.DomainCollapseReason;
import com.jjktbf.model.domain.DomainInstance;
import com.jjktbf.model.move.DefenseType;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Runtime for New Shadow Style: Simple Domain and Miwa's binding vow.
 *
 * <p>The stance move establishes the real Simple Domain anti-Domain through its
 * authored {@code ESTABLISH_DOMAIN} row (a legacy coded activation row is still
 * recognised). While the stance's own establishment stands, this runtime grants
 * a one-use parry that fully blocks the next incoming ATTACK-tagged move —
 * ranged or melee, but never one whose hits are INTANGIBLE (those are invisible
 * to every parry and block) — and answers a MELEE attacker with the stance
 * move's referenced counter move. The parry re-arms only when the stance
 * establishes a fresh anti-Domain, and drops as soon as that Domain instance
 * leaves the battlefield.</p>
 *
 * <p>With the {@code SIMPLE_DOMAIN_BINDING_VOW} feature, using any ATTACK or
 * DODGE move dismisses <em>every</em> Simple Domain the owner maintains,
 * however it was established — the New Shadow Style stance or a plain Simple
 * Domain — together with any unused parry; without the vow the Domains endure
 * until broken or no longer maintainable.</p>
 */
public final class NewShadowStyleAbility implements CodedAbilityRuntime {

    public static final String KEY = "NEW_SHADOW_STYLE";
    public static final String ACTIVATE_SIMPLE_DOMAIN = "ACTIVATE_SIMPLE_DOMAIN";
    public static final String SIMPLE_DOMAIN_BINDING_VOW = "SIMPLE_DOMAIN_BINDING_VOW";

    private final BattleCombatant owner;
    private final Move simpleDomainMove;
    private String observedInstanceId;
    private boolean parryAvailable;
    /** Set when the stance move fires, so only its own establishment arms the parry. */
    private boolean stanceEstablishmentPending;

    NewShadowStyleAbility(BattleCombatant owner, Set<String> features) {
        this.owner = owner;
        this.simpleDomainMove = owner.getCharacter().getKnownMoves().stream()
            .filter(NewShadowStyleAbility::isStanceMove)
            .findFirst()
            .orElseGet(() -> owner.getCharacter().getKnownMoves().stream()
                .filter(NewShadowStyleAbility::hasLegacyActivationRow)
                .findFirst().orElse(null));
    }

    @Override
    public List<CombatEvent> onTrigger(
        BattleState state,
        AbilityTrigger trigger,
        Predicate<String> featureActive
    ) {
        List<CombatEvent> events = new ArrayList<>();
        if (simpleDomainMove != null) {
            reconcile(state, trigger.tick(), events);
            if (trigger.type() == AbilityTrigger.Type.MOVE_USED
                && trigger.actor() == owner && trigger.move() != null
                && trigger.move().getId().equals(simpleDomainMove.getId())) {
                stanceEstablishmentPending = true;
            }
        }
        if (trigger.type() != AbilityTrigger.Type.MOVE_USED
            || trigger.actor() != owner || trigger.move() == null
            || activatesSimpleDomain(trigger.move())) {
            return events;
        }
        if (!featureActive.test(SIMPLE_DOMAIN_BINDING_VOW)) return events;
        if (!dismissesStance(trigger.move())) return events;
        boolean maintainsSimpleDomain = state.domainBattlefield().activeDomains().stream()
            .anyMatch(instance -> instance.ownerId().equals(owner.getInstanceId())
                && instance.definition().antiDomain());
        if (!maintainsSimpleDomain) return events;

        parryAvailable = false;
        stanceEstablishmentPending = false;
        events.addAll(state.domainBattlefield().collapseOwnedDomains(
            state, owner, DomainCollapseReason.OWNER_ACTED, null, trigger.tick(),
            instance -> instance.definition().antiDomain()));
        events.add(event(trigger.tick(), "Using " + trigger.move().getName()
            + " breaks the binding vow; " + owner.getCharacter().getName()
            + "'s Simple Domain is dismissed."));
        return events;
    }

    @Override
    public CodedMoveResponse beforeIncomingMove(
        BattleState state,
        BattleCombatant attacker,
        BattleCombatant defender,
        Move move,
        int tick,
        Predicate<String> featureActive
    ) {
        if (simpleDomainMove == null) return CodedMoveResponse.none();
        List<CombatEvent> events = new ArrayList<>();
        boolean domainPresent = reconcile(state, tick, events);
        if (defender != owner || !domainPresent || !parryAvailable
            || move == null || !move.hasTag(MoveTag.ATTACK.name())
            || move.isIntangible()) {
            return new CodedMoveResponse(false, List.of(), events);
        }

        parryAvailable = false;
        events.add(CombatEvent.of(CombatEvent.Type.MOVE_PARRIED)
            .source(attacker).target(owner).move(move).tick(tick)
            .defenseMoveId(simpleDomainMove.getId())
            .message(owner.getCharacter().getName() + "'s New Shadow Style parries "
                + move.getName() + "!").build());
        List<Move> counters = List.of();
        if (move.isMelee() && simpleDomainMove.getAttackLaunchMove() != null) {
            counters = List.of(simpleDomainMove.getAttackLaunchMove());
            events.add(event(tick, owner.getCharacter().getName()
                + " answers the MELEE attack with "
                + simpleDomainMove.getAttackLaunchMove().getName() + "!"));
        }
        return new CodedMoveResponse(true, counters, events);
    }

    @Override
    public List<CombatEvent> drainPendingEvents(int tick) {
        return List.of();
    }

    @Override
    public CodedAbilityState state() {
        return new CodedAbilityState(KEY, "Simple Domain",
            parryAvailable ? 2 : observedInstanceId != null ? 1 : 0, 2, false);
    }

    public static boolean supportsFeature(String feature) {
        return SIMPLE_DOMAIN_BINDING_VOW.equals(feature);
    }

    public static boolean supportsTarget(String target, Integer stackCount) {
        return stackCount == null && (target == null || target.isBlank() || target.matches("\\d{6}"));
    }

    /**
     * Track the owner's live anti-Domain instance: a fresh establishment by the
     * stance move arms the one-use parry, and a vanished instance disarms any
     * unused parry.
     */
    private boolean reconcile(BattleState state, int tick, List<CombatEvent> events) {
        DomainInstance mine = state.domainBattlefield().activeDomains().stream()
            .filter(instance -> instance.ownerId().equals(owner.getInstanceId()))
            .filter(instance -> instance.definition().antiDomain())
            .findFirst().orElse(null);
        if (mine == null) {
            if (observedInstanceId != null) {
                observedInstanceId = null;
                stanceEstablishmentPending = false;
                if (parryAvailable) {
                    parryAvailable = false;
                    events.add(event(tick, owner.getCharacter().getName()
                        + "'s Simple Domain is gone; the New Shadow Style stance ends."));
                }
            }
            return false;
        }
        if (!mine.instanceId().equals(observedInstanceId)) {
            observedInstanceId = mine.instanceId();
            parryAvailable = stanceEstablishmentPending;
            stanceEstablishmentPending = false;
        }
        return true;
    }

    /** The vow breaks on attacking moves and dodge moves, never on the stance itself. */
    private static boolean dismissesStance(Move move) {
        return move.hasTag(MoveTag.ATTACK.name())
            || move.getDefenseType() == DefenseType.DODGE;
    }

    /** Whether this move establishes the Simple Domain this runtime tracks. */
    public static boolean activatesSimpleDomain(Move move) {
        if (move == null) return false;
        if (move.usesUnifiedEffects()) {
            return move.effectsFor(MoveEffectTrigger.ON_FIRE, -1).stream()
                .anyMatch(NewShadowStyleAbility::establishesDomain);
        }
        return hasLegacyActivationRow(move);
    }

    /** The stance is the establishing move that also carries its counter answer. */
    private static boolean isStanceMove(Move move) {
        return activatesSimpleDomain(move) && move.referencesAttackMove();
    }

    private static boolean hasLegacyActivationRow(Move move) {
        return !move.getSelfEffects().stream()
            .filter(NewShadowStyleAbility::isActivation)
            .toList().isEmpty();
    }

    private static boolean establishesDomain(MoveEffectData effect) {
        return effect != null
            && AbilityEffectType.ESTABLISH_DOMAIN.name().equalsIgnoreCase(effect.type);
    }

    private static boolean isActivation(StatusEffect effect) {
        return effect != null && KEY.equalsIgnoreCase(effect.getCodedAbilityKey())
            && ACTIVATE_SIMPLE_DOMAIN.equalsIgnoreCase(effect.getCodedAction());
    }

    private CombatEvent event(int tick, String message) {
        return CombatEvent.of(CombatEvent.Type.ABILITY_ACTIVATED)
            .source(owner).target(owner).tick(tick)
            .codedAbilityState(state())
            .message(message).build();
    }
}
