package com.jjktbf.model.combat;

import com.jjktbf.model.character.coded.CursedSpeechAbility;
import com.jjktbf.model.move.AoeType;
import com.jjktbf.model.move.CombatantPairTargeting;
import com.jjktbf.model.move.DefenseTargeting;
import com.jjktbf.model.move.Move;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Canonical explicit-target count, order, and relationship rules for moves. */
public final class MoveTargetSelection {
    public enum Relationship { ALLY, ENEMY }

    public record Requirements(
        int minimumCount,
        int maximumCount,
        List<Relationship> orderedRelationships
    ) {
        public Requirements {
            orderedRelationships = List.copyOf(orderedRelationships);
        }

        public boolean requiresTargets() {
            return minimumCount > 0;
        }

        public boolean exactCount() {
            return minimumCount == maximumCount;
        }

        /** Relationship for an ordered slot; variable selections repeat their sole relationship. */
        public Relationship relationshipAt(int index) {
            if (orderedRelationships.isEmpty()) return null;
            return orderedRelationships.get(Math.min(index, orderedRelationships.size() - 1));
        }
    }

    private static final Requirements NONE = new Requirements(0, 0, List.of());

    private MoveTargetSelection() { }

    /** Pair targeting takes precedence over ordinary attack and defense targeting. */
    public static Requirements requirements(Move move) {
        if (move == null) return NONE;
        return requirements(
            move.getPairTargeting(),
            DefenseTargeting.forMove(move),
            move.getDefenseTargetCount(),
            move.isHostile(),
            move.isAoe(),
            move.getAoeType(),
            move.getAoeTargetCount());
    }

    /** Shared projection used by protocol DTOs before they are reconstructed as a Move. */
    public static Requirements requirements(
        CombatantPairTargeting pair,
        DefenseTargeting defense,
        int defenseTargetCount,
        boolean hostile,
        boolean areaOfEffect,
        AoeType aoeType,
        int aoeTargetCount
    ) {
        pair = pair == null ? CombatantPairTargeting.NONE : pair;
        switch (pair) {
            case SELF_AND_ENEMY:
                return new Requirements(1, 1, List.of(Relationship.ENEMY));
            case SELF_AND_ALLY:
                return new Requirements(1, 1, List.of(Relationship.ALLY));
            case ALLY_AND_ENEMY:
                return new Requirements(2, 2,
                    List.of(Relationship.ALLY, Relationship.ENEMY));
            case NONE:
                break;
        }

        defense = defense == null ? DefenseTargeting.SELF : defense;
        if (defense == DefenseTargeting.SINGLE_ALLY) {
            return new Requirements(1, 1, List.of(Relationship.ALLY));
        }
        if (defense == DefenseTargeting.MULTIPLE_ALLIES) {
            return new Requirements(1, Math.max(1, defenseTargetCount),
                List.of(Relationship.ALLY));
        }

        if (!hostile) return NONE;
        if (!areaOfEffect) {
            return new Requirements(1, 1, List.of(Relationship.ENEMY));
        }
        if (aoeType == AoeType.MULTIPLE) {
            return new Requirements(1, Math.max(1, aoeTargetCount),
                List.of(Relationship.ENEMY));
        }
        return NONE;
    }

    public static String targetCountError(Move move, List<CombatantId> targetIds) {
        Requirements requirements = requirements(move);
        int count = targetIds == null ? 0 : targetIds.size();
        if (count >= requirements.minimumCount() && count <= requirements.maximumCount()) {
            return null;
        }
        String moveName = move == null ? "Move" : "Move '" + move.getName() + "'";
        if (requirements.exactCount()) {
            return moveName + " requires exactly " + requirements.minimumCount()
                + (requirements.minimumCount() == 1 ? " target" : " targets");
        }
        return moveName + " requires between " + requirements.minimumCount() + " and "
            + requirements.maximumCount() + " targets";
    }

    /** Validates ordered IDs against the current actor/team snapshot. */
    public static String validationError(
        BattleState state,
        BattleCombatant actor,
        Move move,
        List<CombatantId> targetIds
    ) {
        List<CombatantId> selected = targetIds == null ? List.of() : targetIds;
        String countError = targetCountError(move, selected);
        if (countError != null) return countError;
        if (state == null || actor == null) return "Battle state and actor are required";

        BattleTeam actorTeam = state.teamOf(actor);
        if (actorTeam == null || !actor.isActive()) return "Move actor must be active";
        Requirements requirements = requirements(move);
        Set<CombatantId> distinct = new HashSet<>();
        for (int index = 0; index < selected.size(); index++) {
            CombatantId targetId = selected.get(index);
            if (targetId == null || !distinct.add(targetId)) {
                return "Move targets must be distinct";
            }
            BattleCombatant target = state.combatant(targetId);
            if (target == null || !target.isActive() || target == actor) {
                return "Move target " + (index + 1) + " must be a distinct active combatant";
            }
            Relationship expected = requirements.relationshipAt(index);
            boolean ally = state.teamOf(target) == actorTeam;
            if (expected == Relationship.ALLY && !ally) {
                return "Move target " + (index + 1) + " must be an active ally";
            }
            if (expected == Relationship.ENEMY
                && (ally || state.teamOf(target) == null
                    || !CursedSpeechAbility.canTarget(move, target))) {
                return "Move target " + (index + 1) + " must be an active enemy";
            }
        }
        return null;
    }
}
