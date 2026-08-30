package com.jjktbf.model.combat;

import com.jjktbf.model.move.Move;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Battle-scoped target transpositions installed by reusable move effects. */
public final class TargetExchangeRegistry {

    public record ExchangeStep(
        BattleCombatant owner,
        BattleCombatant previousTarget,
        BattleCombatant replacementTarget
    ) { }

    public record Result(BattleCombatant target, List<ExchangeStep> steps) {
        public Result {
            steps = List.copyOf(steps);
        }
    }

    private final List<Window> windows = new ArrayList<>();

    public boolean register(
        BattleCombatant owner,
        BattleCombatant first,
        BattleCombatant second,
        String moveTag,
        int durationRounds,
        int durationTicks,
        int uses
    ) {
        if (!active(owner) || !active(first) || !active(second) || first == second) return false;
        if (durationRounds < -1 || durationTicks < 0
            || (durationRounds == 0 && durationTicks == 0)
            || (uses != -1 && uses < 1)) {
            return false;
        }
        windows.add(new Window(
            owner, first, second, normalize(moveTag), durationRounds, durationTicks, uses));
        return true;
    }

    /** Apply every eligible transposition once, in registration order. */
    public Result resolve(
        BattleCombatant attacker,
        BattleCombatant intendedTarget,
        Move incomingMove
    ) {
        if (!active(attacker) || !active(intendedTarget) || incomingMove == null
            || incomingMove.isAoe()
            || incomingMove.getHitComponents().isEmpty()) {
            return new Result(intendedTarget, List.of());
        }

        BattleCombatant resolved = intendedTarget;
        List<ExchangeStep> steps = new ArrayList<>();
        Iterator<Window> iterator = windows.iterator();
        while (iterator.hasNext()) {
            Window window = iterator.next();
            if (!window.valid()) {
                iterator.remove();
                continue;
            }
            if (!window.matches(incomingMove)) continue;
            BattleCombatant replacement = window.exchange(resolved);
            if (replacement == null) continue;
            steps.add(new ExchangeStep(window.owner, resolved, replacement));
            resolved = replacement;
            if (window.consume()) iterator.remove();
        }
        return new Result(resolved, steps);
    }

    public void advanceTick() {
        Iterator<Window> iterator = windows.iterator();
        while (iterator.hasNext()) {
            Window window = iterator.next();
            if (!window.valid()) {
                iterator.remove();
                continue;
            }
            if (window.remainingRounds == 0 && window.remainingTicks > 0) {
                window.remainingTicks--;
            }
            if (window.expired()) iterator.remove();
        }
    }

    public void endRound() {
        Iterator<Window> iterator = windows.iterator();
        while (iterator.hasNext()) {
            Window window = iterator.next();
            if (!window.valid()) {
                iterator.remove();
                continue;
            }
            if (window.remainingRounds > 0) window.remainingRounds--;
            if (window.expired()) iterator.remove();
        }
    }

    int size() {
        return windows.size();
    }

    private static boolean active(BattleCombatant combatant) {
        return combatant != null && combatant.isActive();
    }

    private static String normalize(String moveTag) {
        return moveTag == null || moveTag.isBlank() ? null : moveTag.trim().toUpperCase();
    }

    private static final class Window {
        private final BattleCombatant owner;
        private final BattleCombatant first;
        private final BattleCombatant second;
        private final String moveTag;
        private int remainingRounds;
        private int remainingTicks;
        private int remainingUses;

        private Window(
            BattleCombatant owner,
            BattleCombatant first,
            BattleCombatant second,
            String moveTag,
            int remainingRounds,
            int remainingTicks,
            int remainingUses
        ) {
            this.owner = owner;
            this.first = first;
            this.second = second;
            this.moveTag = moveTag;
            this.remainingRounds = remainingRounds;
            this.remainingTicks = remainingTicks;
            this.remainingUses = remainingUses;
        }

        private boolean valid() {
            return active(owner) && active(first) && active(second) && !expired();
        }

        private boolean expired() {
            return remainingRounds == 0 && remainingTicks == 0;
        }

        private boolean matches(Move move) {
            return moveTag == null || move.hasTag(moveTag);
        }

        private BattleCombatant exchange(BattleCombatant target) {
            if (target == first) return second;
            if (target == second) return first;
            return null;
        }

        /** Returns true when this use exhausts the window. */
        private boolean consume() {
            if (remainingUses == -1) return false;
            remainingUses--;
            return remainingUses == 0;
        }
    }
}
