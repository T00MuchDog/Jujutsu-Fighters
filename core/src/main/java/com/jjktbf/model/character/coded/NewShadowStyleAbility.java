package com.jjktbf.model.character.coded;

import com.jjktbf.model.combat.AbilityTrigger;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.character.AbilityEffectType;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Runtime for New Shadow Style's Simple Domain state and Miwa's binding vow. */
public final class NewShadowStyleAbility implements CodedAbilityRuntime {

    public static final String KEY = "NEW_SHADOW_STYLE";
    public static final String ACTIVATE_SIMPLE_DOMAIN = "ACTIVATE_SIMPLE_DOMAIN";
    public static final String SIMPLE_DOMAIN_BINDING_VOW = "SIMPLE_DOMAIN_BINDING_VOW";

    private final BattleCombatant owner;
    private boolean simpleDomainActive;
    private final String simpleDomainMoveId;

    NewShadowStyleAbility(BattleCombatant owner, Set<String> features) {
        this.owner = owner;
        this.simpleDomainMoveId = owner.getCharacter().getKnownMoves().stream()
            .filter(NewShadowStyleAbility::activatesSimpleDomain)
            .map(Move::getId)
            .findFirst().orElse(null);
    }

    @Override
    public List<CombatEvent> onTrigger(
        BattleState state,
        AbilityTrigger trigger,
        Predicate<String> featureActive
    ) {
        if (!simpleDomainActive
            || trigger.type() != AbilityTrigger.Type.MOVE_USED || trigger.actor() != owner
            || trigger.move() == null || simpleDomainMoveId == null
            || trigger.move().getId().equals(simpleDomainMoveId)) {
            return List.of();
        }
        if (!featureActive.test(SIMPLE_DOMAIN_BINDING_VOW)) return List.of();
        simpleDomainActive = false;
        if (owner.getTimeline() != null) {
            owner.getTimeline().cancelArmedReaction(simpleDomainMoveId);
        }
        return List.of(event(trigger.tick(), "Using " + trigger.move().getName()
            + " dispels " + owner.getCharacter().getName() + "'s Simple Domain."));
    }

    @Override
    public List<CombatEvent> onEffectFired(
        BattleState state,
        StatusEffect effect,
        BattleCombatant attacker,
        BattleCombatant defender,
        int tick
    ) {
        if (attacker != owner || !isActivation(effect)) return List.of();
        simpleDomainActive = true;
        return List.of(event(tick, owner.getCharacter().getName()
            + " establishes a 2.21 metre Simple Domain."));
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
        if (!simpleDomainActive || defender != owner || !move.hasTag(MoveTag.ATTACK.name())) {
            return CodedMoveResponse.none();
        }

        simpleDomainActive = false;
        return new CodedMoveResponse(false, List.of(), List.of(event(tick,
            owner.getCharacter().getName() + "'s Simple Domain reacts to "
                + move.getName() + " and is dispelled.")));
    }

    @Override
    public List<CombatEvent> drainPendingEvents(int tick) {
        return List.of();
    }

    @Override
    public CodedAbilityState state() {
        return new CodedAbilityState(KEY, "Simple Domain", simpleDomainActive ? 1 : 0, 1);
    }

    public static boolean supportsFeature(String feature) {
        return SIMPLE_DOMAIN_BINDING_VOW.equals(feature);
    }

    public static boolean supportsTarget(String target, Integer stackCount) {
        return stackCount == null && (target == null || target.isBlank() || target.matches("\\d{6}"));
    }

    private static boolean activatesSimpleDomain(Move move) {
        return !activationEffects(move).isEmpty();
    }

    private static List<StatusEffect> activationEffects(Move move) {
        if (move == null) return List.of();
        if (!move.usesUnifiedEffects()) {
            return move.getSelfEffects().stream()
                .filter(NewShadowStyleAbility::isActivation)
                .toList();
        }
        return move.effectsFor(MoveEffectTrigger.ON_FIRE, -1).stream()
            .filter(effect -> AbilityEffectType.CODED_MOVE_ACTION.name()
                .equalsIgnoreCase(effect.type))
            .filter(effect -> KEY.equalsIgnoreCase(effect.codedAbilityKey)
                && ACTIVATE_SIMPLE_DOMAIN.equalsIgnoreCase(effect.codedAction))
            .map(MoveEffectData::toCodedStatusEffect)
            .toList();
    }

    private static boolean isActivation(StatusEffect effect) {
        return effect != null && KEY.equalsIgnoreCase(effect.getCodedAbilityKey())
            && ACTIVATE_SIMPLE_DOMAIN.equalsIgnoreCase(effect.getCodedAction());
    }

    private CombatEvent event(int tick, String message) {
        return CombatEvent.of(CombatEvent.Type.ABILITY_ACTIVATED)
            .source(owner).target(owner).tick(tick)
            .codedAbilityState(state())
            .message(message)
            .build();
    }
}
