package com.jjktbf.model.combat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A team's atomic round plan: one {@link BattlePlan} per active controlled
 * combatant, keyed by actor {@link CombatantId}.
 *
 * <p>Team submission is atomic — every living controlled combatant's page must be
 * locked before the round begins. This container holds those per-actor drafts
 * together so the controller/view can submit them as one unit and so a rejected
 * submission restores every page's state.
 *
 * <p>Draft state is keyed by combatant instance id, never by Java object identity
 * or character-definition id (the latter cannot distinguish duplicate summons).
 */
public final class TeamBattlePlan {

    private final BattleTeamId teamId;
    private final int gridLength;
    private final Map<CombatantId, BattlePlan> plansByActor = new LinkedHashMap<>();
    private final Map<CombatantId, CombatantId> switchesByActor = new LinkedHashMap<>();

    public TeamBattlePlan(BattleTeamId teamId, int gridLength) {
        this.teamId = Objects.requireNonNull(teamId, "teamId");
        this.gridLength = gridLength;
    }

    public BattleTeamId teamId() {
        return teamId;
    }

    /** The common planning-grid length shared by every plan in this team plan. */
    public int gridLength() {
        return gridLength;
    }

    /** Associate (or replace) the plan for an actor. */
    public void put(CombatantId actor, BattlePlan plan) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(plan, "plan");
        switchesByActor.remove(actor);
        plansByActor.put(actor, plan);
    }

    /** Spend this actor's round switching to a living reserve fighter. */
    public void switchTo(CombatantId actor, CombatantId incomingReserve) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(incomingReserve, "incomingReserve");
        plansByActor.remove(actor);
        switchesByActor.put(actor, incomingReserve);
    }

    /** The plan for an actor, or {@code null} if none is drafted. */
    public BattlePlan get(CombatantId actor) {
        return actor == null ? null : plansByActor.get(actor);
    }

    public boolean has(CombatantId actor) {
        return actor != null
            && (plansByActor.containsKey(actor) || switchesByActor.containsKey(actor));
    }

    public boolean isSwitch(CombatantId actor) {
        return actor != null && switchesByActor.containsKey(actor);
    }

    public CombatantId switchTarget(CombatantId actor) {
        return actor == null ? null : switchesByActor.get(actor);
    }

    /** Every actor id with a draft, in insertion order. */
    public List<CombatantId> actors() {
        java.util.LinkedHashSet<CombatantId> actors = new java.util.LinkedHashSet<>();
        actors.addAll(plansByActor.keySet());
        actors.addAll(switchesByActor.keySet());
        return List.copyOf(actors);
    }

    /** An unmodifiable view of actor → plan. */
    public Map<CombatantId, BattlePlan> plans() {
        return Collections.unmodifiableMap(plansByActor);
    }

    public Map<CombatantId, CombatantId> switches() {
        return Collections.unmodifiableMap(switchesByActor);
    }

    public int size() {
        return plansByActor.size() + switchesByActor.size();
    }

    public boolean isEmpty() {
        return plansByActor.isEmpty() && switchesByActor.isEmpty();
    }

    /**
     * If any actor's plan is missing a required single-target selection, return
     * a human-readable description; otherwise {@code null}. Used to reject atomic
     * team submission when one page is incomplete.
     */
    public String missingTargetError() {
        for (Map.Entry<CombatantId, BattlePlan> entry : plansByActor.entrySet()) {
            String error = entry.getValue().missingTargetError();
            if (error != null) {
                return error + " (combatant " + entry.getKey() + ")";
            }
        }
        return null;
    }

    /**
     * Validate this submission against the authoritative current battle state.
     * A valid team plan covers exactly every active member of its team, uses the
     * round's common grid, and contains only currently valid opposing targets.
     */
    public String validationError(BattleState state) {
        if (state == null) return "Battle state is required";
        BattleTeam team = state.teamOf(teamId);
        if (team == null) return "Unknown team " + teamId;

        int expectedGridLength = gridLengthForRound(state);
        if (gridLength != expectedGridLength) {
            return "Team plan grid length must be " + expectedGridLength;
        }

        List<BattleCombatant> active = team.active();
        if (size() != active.size()) {
            return "Team plan must include every active combatant exactly once";
        }
        for (BattleCombatant combatant : active) {
            if (!has(combatant.getInstanceId())) {
                return "Missing plan or switch for active combatant "
                    + combatant.getInstanceId();
            }
        }


        java.util.Set<CombatantId> selectedReserves = new java.util.HashSet<>();
        for (Map.Entry<CombatantId, CombatantId> entry : switchesByActor.entrySet()) {
            BattleCombatant actor = state.combatant(entry.getKey());
            BattleCombatant incoming = state.combatant(entry.getValue());
            if (actor == null || !actor.isActive() || !actor.isFighter()
                || state.teamOf(actor) != team) {
                return "Switch actor " + entry.getKey() + " is not an active fighter on team "
                    + teamId;
            }
            if (incoming == null || !incoming.isReserve() || !incoming.isFighter()
                || incoming.isDefeated() || state.teamOf(incoming) != team) {
                return "Switch target " + entry.getValue() + " is not a living reserve on team "
                    + teamId;
            }
            if (!selectedReserves.add(entry.getValue())) {
                return "A reserve fighter can only be selected for one switch";
            }
        }

        for (Map.Entry<CombatantId, BattlePlan> entry : plansByActor.entrySet()) {
            BattleCombatant actor = state.combatant(entry.getKey());
            if (actor == null || !actor.isActive() || state.teamOf(actor) != team) {
                return "Plan actor " + entry.getKey() + " is not active on team " + teamId;
            }
            BattlePlan plan = entry.getValue();
            if (plan.gridLength() != gridLength) {
                return "Plan for " + entry.getKey() + " does not use the common grid";
            }
            for (ActionSegment segment : plan.allSegments()) {
                List<CombatantId> targetIds = segment.getTargets();
                String targetError = MoveTargetSelection.validationError(
                    state, actor, segment.getMove(), targetIds);
                if (targetError != null) {
                    return targetError + " (combatant " + entry.getKey() + ")";
                }
            }
        }
        return null;
    }

    /** The round's shared grid, fixed from all active combatants at round start. */
    public static int gridLengthForRound(BattleState state) {
        return state.getTimelineGridLength();
    }
}
