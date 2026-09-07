package com.jjktbf.multiplayer.engine;

import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.coded.CursedSpeechAbility;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCharacterLookup;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleFormat;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.BattleStatMode;
import com.jjktbf.model.combat.BattleTeam;
import com.jjktbf.model.combat.BattleTeamId;
import com.jjktbf.model.combat.CeEfficiencyCalculator;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.CombatantId;
import com.jjktbf.model.combat.MoveTargetSelection;
import com.jjktbf.model.combat.MoveAvailability;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.combat.TeamBattlePlan;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.progression.TechniqueMasteryResolver;
import com.jjktbf.model.text.MoveDescriptionVariables;
import com.jjktbf.model.domain.DomainInstance;
import com.jjktbf.multiplayer.protocol.ActionCommand;
import com.jjktbf.multiplayer.protocol.ActionSegmentState;
import com.jjktbf.multiplayer.protocol.ActionSegmentStatus;
import com.jjktbf.multiplayer.protocol.BattleEventState;
import com.jjktbf.multiplayer.protocol.BattleEventType;
import com.jjktbf.multiplayer.protocol.BattlePhase;
import com.jjktbf.multiplayer.protocol.BoundedResourceTransactionState;
import com.jjktbf.multiplayer.protocol.CharacterState;
import com.jjktbf.multiplayer.protocol.CommandResult;
import com.jjktbf.multiplayer.protocol.CommandType;
import com.jjktbf.multiplayer.protocol.ErrorResponse;
import com.jjktbf.multiplayer.protocol.DomainBattlefieldState;
import com.jjktbf.multiplayer.protocol.DomainClashState;
import com.jjktbf.multiplayer.protocol.DomainState;
import com.jjktbf.multiplayer.protocol.HitComponentState;
import com.jjktbf.multiplayer.protocol.MatchState;
import com.jjktbf.multiplayer.protocol.MatchStatus;
import com.jjktbf.multiplayer.protocol.MoveState;
import com.jjktbf.multiplayer.protocol.PlanBoard;
import com.jjktbf.multiplayer.protocol.PlanPlacement;
import com.jjktbf.multiplayer.protocol.PlanState;
import com.jjktbf.multiplayer.protocol.PlayerSide;
import com.jjktbf.multiplayer.protocol.PlayerState;
import com.jjktbf.multiplayer.protocol.ProtocolVersion;
import com.jjktbf.multiplayer.protocol.ReinforcementMoveState;
import com.jjktbf.multiplayer.protocol.RoundStartCharacterState;
import com.jjktbf.multiplayer.protocol.StatusEffectState;
import com.jjktbf.multiplayer.protocol.SwitchSelection;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Synchronized, authoritative owner of one headless two-player battle.
 *
 * <p>Commands contain intent only. This class resolves canonical moves, costs,
 * boards, budgets, combat outcomes, and all version changes on the server side.
 */
public final class HeadlessBattleSession {

    public static final int DEFAULT_MAX_ROUNDS = 50;
    public static final int MAX_PLAN_PLACEMENTS = BattlePlan.GRID_LENGTH * 2;

    private static final String PLAYER_NOT_IN_MATCH = "PLAYER_NOT_IN_MATCH";
    private static final String WRONG_MATCH = "WRONG_MATCH";
    private static final String MATCH_ENDED = "MATCH_ENDED";
    private static final String MATCH_NOT_READY = "MATCH_NOT_READY";
    private static final String OPPONENT_DISCONNECTED = "OPPONENT_DISCONNECTED";
    private static final String DUPLICATE_COMMAND = "DUPLICATE_COMMAND";
    private static final String STALE_STATE_VERSION = "STALE_STATE_VERSION";
    private static final String WRONG_PHASE = "WRONG_PHASE";
    private static final String PLAN_ALREADY_SUBMITTED = "PLAN_ALREADY_SUBMITTED";
    private static final String READY_ALREADY_SUBMITTED = "READY_ALREADY_SUBMITTED";
    private static final String INVALID_ACTOR = "INVALID_ACTOR";
    private static final String INVALID_TARGET = "INVALID_TARGET";
    private static final String INVALID_MOVE = "INVALID_MOVE";
    private static final String MOVE_CAP_REACHED = "MOVE_CAP_REACHED";
    private static final String INVALID_PLACEMENT = "INVALID_PLACEMENT";
    private static final String INSUFFICIENT_AP = "INSUFFICIENT_AP";
    private static final String INSUFFICIENT_RESOURCE = "INSUFFICIENT_RESOURCE";
    private static final String MALFORMED_COMMAND = "MALFORMED_COMMAND";

    private final String matchId;
    private final long seed;
    private final int maxRounds;
    private final Clock clock;
    private final BattleStatMode statMode;
    private final BattleState battleState;
    private final CombatResolver resolver;
    /**
     * Character lookup for resolving summon ids at runtime. Injected by the
     * server so the engine materializes shikigami without loading files. May be
     * null (summons are then enqueued but not materialized).
     */
    private BattleCharacterLookup summonLookup;
    private final EnumMap<PlayerSide, ParticipantRuntime> participantsBySide =
        new EnumMap<>(PlayerSide.class);
    private final Map<String, ParticipantRuntime> participantsById = new LinkedHashMap<>();
    private final Set<String> acceptedCommandIds = new LinkedHashSet<>();

    private MatchStatus status;
    private PlayerSide winnerSide;
    private String winnerPlayerId;
    private String endReason;
    private long stateVersion;
    private long eventSequence;
    private int wireRoundNumber;
    private int wireCurrentTick;
    private Long planningDeadline;
    private List<BattleEventState> recentEvents;
    private List<RoundStartCharacterState> roundStartCharacterStates;
    private boolean battleStarted;
    private Long firstBattleReadyBaseVersion;
    private Long firstPlanBaseVersion;
    private Long firstReadyBaseVersion;

    public HeadlessBattleSession(
        String matchId,
        MatchParticipant first,
        MatchParticipant second,
        long seed
    ) {
        this(matchId, first, second, seed, DEFAULT_MAX_ROUNDS, Clock.systemUTC());
    }

    public HeadlessBattleSession(
        String matchId,
        MatchParticipant first,
        MatchParticipant second,
        long seed,
        int maxRounds
    ) {
        this(matchId, first, second, seed, maxRounds, Clock.systemUTC());
    }

    public HeadlessBattleSession(
        String matchId,
        MatchParticipant first,
        MatchParticipant second,
        long seed,
        Clock clock
    ) {
        this(matchId, first, second, seed, DEFAULT_MAX_ROUNDS, clock);
    }

    public HeadlessBattleSession(
        String matchId,
        MatchParticipant first,
        MatchParticipant second,
        long seed,
        Clock clock,
        BattleCharacterLookup summonLookup
    ) {
        this(matchId, first, second, seed, DEFAULT_MAX_ROUNDS, clock, summonLookup);
    }

    public HeadlessBattleSession(
        String matchId,
        MatchParticipant first,
        MatchParticipant second,
        long seed,
        Clock clock,
        BattleCharacterLookup summonLookup,
        BattleStatMode statMode
    ) {
        this(matchId, first, second, seed, DEFAULT_MAX_ROUNDS, clock, summonLookup, statMode);
    }

    public HeadlessBattleSession(
        String matchId,
        List<MatchParticipant> participants,
        long seed
    ) {
        this(matchId, participantAt(participants, 0), participantAt(participants, 1), seed);
    }

    public HeadlessBattleSession(
        String matchId,
        List<MatchParticipant> participants,
        long seed,
        int maxRounds,
        Clock clock
    ) {
        this(
            matchId,
            participantAt(participants, 0),
            participantAt(participants, 1),
            seed,
            maxRounds,
            clock
        );
    }

    public HeadlessBattleSession(
        String matchId,
        MatchParticipant first,
        MatchParticipant second,
        long seed,
        int maxRounds,
        Clock clock
    ) {
        this(matchId, first, second, seed, maxRounds, clock, null);
    }

    public HeadlessBattleSession(
        String matchId,
        MatchParticipant first,
        MatchParticipant second,
        long seed,
        int maxRounds,
        Clock clock,
        BattleCharacterLookup summonLookup
    ) {
        this(
            matchId,
            first,
            second,
            seed,
            maxRounds,
            clock,
            summonLookup,
            BattleStatMode.STANDARD
        );
    }

    public HeadlessBattleSession(
        String matchId,
        MatchParticipant first,
        MatchParticipant second,
        long seed,
        int maxRounds,
        Clock clock,
        BattleCharacterLookup summonLookup,
        BattleStatMode statMode
    ) {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId cannot be blank");
        }
        Objects.requireNonNull(first, "first participant");
        Objects.requireNonNull(second, "second participant");
        if (first.playerId().equals(second.playerId())) {
            throw new IllegalArgumentException("Participant player IDs must be unique");
        }
        if (first.side() == second.side()
            || Set.of(first.side(), second.side()).size() != PlayerSide.values().length) {
            throw new IllegalArgumentException("A match requires exactly PLAYER_ONE and PLAYER_TWO");
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds must be at least 1");
        }

        this.matchId = matchId;
        this.seed = seed;
        this.maxRounds = maxRounds;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.statMode = Objects.requireNonNull(statMode, "statMode");

        MatchParticipant playerOne = first.side() == PlayerSide.PLAYER_ONE ? first : second;
        MatchParticipant playerTwo = first.side() == PlayerSide.PLAYER_TWO ? first : second;
        ParticipantRuntime playerOneRuntime = new ParticipantRuntime(
            playerOne,
            BattleTeamId.PLAYER,
            statMode
        );
        ParticipantRuntime playerTwoRuntime = new ParticipantRuntime(
            playerTwo,
            BattleTeamId.ENEMY,
            statMode
        );
        participantsBySide.put(PlayerSide.PLAYER_ONE, playerOneRuntime);
        participantsBySide.put(PlayerSide.PLAYER_TWO, playerTwoRuntime);
        participantsById.put(playerOne.playerId(), playerOneRuntime);
        participantsById.put(playerTwo.playerId(), playerTwoRuntime);

        BattleTeam playerTeam = BattleState.teamOfFighters(
            BattleTeamId.PLAYER,
            playerOneRuntime.combatants
        );
        BattleTeam enemyTeam = BattleState.teamOfFighters(
            BattleTeamId.ENEMY,
            playerTwoRuntime.combatants
        );
        this.battleState = playerOneRuntime.combatants.size()
                == BattleFormat.SIX_V_SIX.fightersPerSide()
            && playerTwoRuntime.combatants.size()
                == BattleFormat.SIX_V_SIX.fightersPerSide()
            ? new BattleState(playerTeam, enemyTeam, BattleFormat.SIX_V_SIX)
            : new BattleState(playerTeam, enemyTeam);
        this.summonLookup = summonLookup;
        this.resolver = new CombatResolver(new SeededRandomSource(seed), summonLookup);
        this.status = MatchStatus.WAITING;
        this.stateVersion = 0;
        this.wireRoundNumber = battleState.getRoundNumber();
        this.wireCurrentTick = 0;

        this.recentEvents = List.of();
        this.roundStartCharacterStates = captureRoundStartCharacterStates();
    }

    /** Applies one authenticated intent atomically. Rejections never alter session state. */
    public synchronized CommandResult applyCommand(
        String authenticatedPlayerId,
        ActionCommand command
    ) {
        ParticipantRuntime participant = participantsById.get(authenticatedPlayerId);
        String commandId = command == null ? null : command.commandId();
        if (participant == null) {
            return reject(
                commandId,
                PLAYER_NOT_IN_MATCH,
                "Authenticated player is not a participant in this match."
            );
        }
        if (command == null
            || isBlank(command.commandId())
            || isBlank(command.matchId())
            || command.type() == null) {
            return reject(commandId, MALFORMED_COMMAND, "Command metadata is incomplete.");
        }
        if (!matchId.equals(command.matchId())) {
            return reject(commandId, WRONG_MATCH, "Command targets a different match.");
        }
        if (acceptedCommandIds.contains(command.commandId())) {
            return reject(
                commandId,
                DUPLICATE_COMMAND,
                "Command ID has already been accepted."
            );
        }
        if (isTerminal()) {
            return reject(commandId, MATCH_ENDED, "The match has ended and cannot accept commands.");
        }
        if (status == MatchStatus.WAITING) {
            return reject(commandId, MATCH_NOT_READY, "Both players must join before commands can be accepted.");
        }
        if (status == MatchStatus.OPPONENT_DISCONNECTED) {
            return reject(
                commandId,
                OPPONENT_DISCONNECTED,
                "Commands cannot be accepted while a player is disconnected."
            );
        }
        if (command.type() == CommandType.READY_FOR_BATTLE && battleStarted) {
            return reject(
                commandId,
                WRONG_PHASE,
                "Battle readiness can only be submitted before planning begins."
            );
        }
        if (command.type() == CommandType.READY_FOR_BATTLE && participant.readyForBattle) {
            return reject(
                commandId,
                READY_ALREADY_SUBMITTED,
                "This player is already ready to start the battle."
            );
        }
        if (command.type() == CommandType.READY_NEXT_ROUND
            && battleState.getCurrentPhase() != BattleState.Phase.ROUND_END) {
            return reject(
                commandId,
                WRONG_PHASE,
                "Next-round readiness can only be submitted during round end."
            );
        }
        if (command.type() == CommandType.READY_NEXT_ROUND
            && battleState.getCurrentPhase() == BattleState.Phase.ROUND_END
            && participant.readyForNextRound) {
            return reject(
                commandId,
                READY_ALREADY_SUBMITTED,
                "This player is already ready for the next round."
            );
        }
        if (command.expectedStateVersion() != stateVersion
            && !canUseSharedCommandVersion(participant, command)) {
            return reject(
                commandId,
                STALE_STATE_VERSION,
                "Command was based on a stale match state.",
                Map.of(
                    "expectedStateVersion", Long.toString(command.expectedStateVersion()),
                    "currentStateVersion", Long.toString(stateVersion)
                )
            );
        }
        if (command.type() == CommandType.READY_NEXT_ROUND) {
            return applyReadyNextRound(participant, command);
        }
        if (command.type() == CommandType.READY_FOR_BATTLE) {
            return applyReadyForBattle(participant, command);
        }
        if (!battleStarted || battleState.getCurrentPhase() != BattleState.Phase.PLANNING) {
            return reject(commandId, WRONG_PHASE, "Plans can only be submitted during planning.");
        }
        if (participant.planSubmitted) {
            return reject(
                commandId,
                PLAN_ALREADY_SUBMITTED,
                "This player already submitted a plan for the current round."
            );
        }
        if (command.type() != CommandType.SUBMIT_PLAN || command.payload() == null) {
            return reject(commandId, MALFORMED_COMMAND, "Only a plan payload can be submitted.");
        }

        List<PlanPlacement> placements = command.payload().placements();
        List<SwitchSelection> requestedSwitches = command.payload().switches();
        List<BattleCombatant> activeActors = activeCombatants(participant);
        long maximumPlacements = (long) MAX_PLAN_PLACEMENTS
            * Math.max(1, activeActors.size());
        if (placements == null || placements.size() > maximumPlacements) {
            return reject(
                commandId,
                MALFORMED_COMMAND,
                "Plan contains an invalid number of placements.",
                Map.of("maximumPlacements", Long.toString(maximumPlacements))
            );
        }

        if (requestedSwitches == null || requestedSwitches.size() > activeActors.size()) {
            return reject(commandId, MALFORMED_COMMAND,
                "Plan contains an invalid number of switches.");
        }

        Map<CombatantId, CombatantId> canonicalSwitches = new LinkedHashMap<>();
        Set<CombatantId> selectedReserves = new LinkedHashSet<>();
        for (SwitchSelection requested : requestedSwitches) {
            if (requested == null || isBlank(requested.actorId())
                || isBlank(requested.reserveId())) {
                return reject(commandId, MALFORMED_COMMAND,
                    "Each switch must identify an actor and reserve fighter.");
            }
            BattleCombatant actor = battleState.combatant(new CombatantId(requested.actorId()));
            BattleCombatant reserve = battleState.combatant(new CombatantId(requested.reserveId()));
            if (actor == null || !actor.isActive() || !actor.isFighter()
                || !participant.teamId.equals(actor.getTeamId())) {
                return reject(commandId, INVALID_ACTOR,
                    "Switch actor is not an active fighter controlled by this participant.");
            }
            if (reserve == null || !reserve.isReserve() || !reserve.isFighter()
                || reserve.isDefeated() || !participant.teamId.equals(reserve.getTeamId())) {
                return reject(commandId, INVALID_ACTOR,
                    "Switch target is not a living reserve controlled by this participant.");
            }
            if (canonicalSwitches.putIfAbsent(
                    actor.getInstanceId(), reserve.getInstanceId()) != null
                || !selectedReserves.add(reserve.getInstanceId())) {
                return reject(commandId, MALFORMED_COMMAND,
                    "A fighter or reserve cannot appear in more than one switch.");
            }
        }

        Map<CombatantId, BattlePlan> canonicalPlans = new LinkedHashMap<>();
        Map<CombatantId, List<SegmentRuntime>> canonicalSegments = new LinkedHashMap<>();
        int canonicalGridLength = battleGridLength();
        for (BattleCombatant actor : activeActors) {
            canonicalPlans.put(
                actor.getInstanceId(),
                BattlePlan.forCombatant(actor, canonicalGridLength)
            );
            canonicalSegments.put(actor.getInstanceId(), new ArrayList<>());
        }

        for (int index = 0; index < placements.size(); index++) {
            PlanPlacement placement = placements.get(index);
            if (placement == null || isBlank(placement.moveId())) {
                return rejectPlacement(
                    commandId,
                    MALFORMED_COMMAND,
                    "Placement must identify a move.",
                    index,
                    null
                );
            }

            BattleCombatant actor;
            if (placement.actorId() == null) {
                if (activeActors.size() != 1) {
                    return rejectPlacement(
                        commandId,
                        INVALID_ACTOR,
                        "Placement must identify its active combatant actor.",
                        index,
                        placement.moveId()
                    );
                }
                actor = activeActors.get(0);
            } else {
                if (placement.actorId().isBlank()) {
                    return rejectPlacement(
                        commandId,
                        INVALID_ACTOR,
                        "Placement actor ID cannot be blank.",
                        index,
                        placement.moveId()
                    );
                }
                actor = battleState.combatant(new CombatantId(placement.actorId()));
                if (actor == null
                    || !actor.isActive()
                    || !participant.teamId.equals(actor.getTeamId())
                    || battleState.teamOf(actor) == null) {
                    return rejectPlacement(
                        commandId,
                        INVALID_ACTOR,
                        "Placement actor is not an active combatant controlled by this participant.",
                        index,
                        placement.moveId()
                    );
                }
            }

            BattlePlan canonicalPlan = canonicalPlans.get(actor.getInstanceId());
            if (canonicalPlan == null) {
                return rejectPlacement(
                    commandId,
                    INVALID_ACTOR,
                    "Placement actor is not active for this round.",
                    index,
                    placement.moveId()
                );
            }
            if (canonicalSwitches.containsKey(actor.getInstanceId())) {
                return rejectPlacement(
                    commandId,
                    INVALID_ACTOR,
                    "A switching fighter cannot also place a move.",
                    index,
                    placement.moveId()
                );
            }

            Move move = findKnownMove(actor, placement.moveId()).orElse(null);
            if (move == null) {
                return rejectPlacement(
                    commandId,
                    INVALID_MOVE,
                    "Move is not known by the authoritative actor.",
                    index,
                    placement.moveId()
                );
            }
            if (placement.reinforced() && !actor.canReinforce(move)) {
                return rejectPlacement(
                    commandId,
                    INVALID_MOVE,
                    "Actor cannot reinforce this move.",
                    index,
                    move.getId()
                );
            }
            if (!canonicalPlan.hasRemainingUses(move)) {
                return rejectPlacement(
                    commandId,
                    MOVE_CAP_REACHED,
                    "Move has reached its per-round use cap.",
                    index,
                    move.getId()
                );
            }

            List<CombatantId> targetIds = new ArrayList<>();
            List<String> requestedTargetIds = placement.targetIds();
            String countError = MoveTargetSelection.targetCountError(
                move, requestedTargetIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(CombatantId::new)
                    .toList());
            if (countError != null || requestedTargetIds.stream().anyMatch(HeadlessBattleSession::isBlank)) {
                return rejectPlacement(
                    commandId,
                    INVALID_TARGET,
                    countError != null ? countError : "Placement target IDs cannot be blank.",
                    index,
                    move.getId()
                );
            }
            Set<String> uniqueTargetIds = new LinkedHashSet<>();
            for (String requestedTargetId : requestedTargetIds) {
                if (isBlank(requestedTargetId)) {
                    return rejectPlacement(
                        commandId,
                        INVALID_TARGET,
                        "Placement target IDs cannot be blank.",
                        index,
                        move.getId()
                    );
                }
                if (!uniqueTargetIds.add(requestedTargetId)) {
                    return rejectPlacement(
                        commandId,
                        INVALID_TARGET,
                        "Placement target IDs must not contain duplicates.",
                        index,
                        move.getId()
                    );
                }
                targetIds.add(new CombatantId(requestedTargetId));
            }
            String targetError = MoveTargetSelection.validationError(
                battleState, actor, move, targetIds);
            if (targetError != null) {
                return rejectPlacement(
                    commandId, INVALID_TARGET, targetError, index, move.getId());
            }

            int reinforcementCeCost = placement.reinforced()
                ? actor.computeReinforcementCeCost(move) : 0;
            int ceCost = Math.addExact(
                actor.computeMoveCeCost(move), reinforcementCeCost);
            int effectiveApCost = canonicalPlan.effectiveApCost(move);
            int effectiveUnleashPoint = canonicalPlan.effectiveUnleashPoint(move);
            long endTick = (long) placement.startTick() + effectiveApCost - 1L;
            long fireTick = (long) placement.startTick() + effectiveUnleashPoint - 1L;
            long finalImpactTick = fireTick + move.getMaxHitDelayTicks();
            int planGridLength = canonicalPlan.gridLength();
            if (move.getApCost() < 1
                || move.getUnleashPoint() < 1
                || move.getUnleashPoint() > move.getApCost()
                || ceCost < 0
                || placement.startTick() < 1
                || endTick > planGridLength
                || finalImpactTick > planGridLength) {
                return rejectPlacement(
                    commandId,
                    INVALID_PLACEMENT,
                    "Placement is outside the canonical planning board.",
                    index,
                    move.getId()
                );
            }

            BattlePlan.Board board = BattlePlan.boardFor(move);
            if (!canonicalPlan.boardTimeline(board).isRangeFree(
                placement.startTick(),
                (int) endTick
            )) {
                return rejectPlacement(
                    commandId,
                    INVALID_PLACEMENT,
                    "Placement overlaps another move on the same board.",
                    index,
                    move.getId()
                );
            }
            if (effectiveApCost > canonicalPlan.remainingApBudget()) {
                return rejectPlacement(
                    commandId,
                    INSUFFICIENT_AP,
                    "Plan exceeds the participant's AP budget.",
                    index,
                    move.getId()
                );
            }
            if (ceCost > canonicalPlan.remainingCe()) {
                return rejectPlacement(
                    commandId,
                    INSUFFICIENT_RESOURCE,
                    "Plan exceeds the participant's CE budget.",
                    index,
                    move.getId()
                );
            }

            ActionSegment segment = canonicalPlan.placeWithTargets(
                move,
                placement.startTick(),
                ceCost,
                targetIds,
                placement.reinforced(),
                reinforcementCeCost
            );
            if (segment == null) {
                return rejectPlacement(
                    commandId,
                    INVALID_PLACEMENT,
                    "Placement could not be added to the canonical plan.",
                    index,
                    move.getId()
                );
            }
            List<SegmentRuntime> actorSegments = canonicalSegments.get(actor.getInstanceId());
            actorSegments.add(new SegmentRuntime(
                segmentId(actor.getInstanceId(), actorSegments.size()),
                actor.getInstanceId(),
                segment,
                board,
                targetIds
            ));
        }

        if (participantsBySide.values().stream().noneMatch(runtime -> runtime.planSubmitted)) {
            firstPlanBaseVersion = command.expectedStateVersion();
        }
        TeamBattlePlan teamPlan = new TeamBattlePlan(participant.teamId, canonicalGridLength);
        for (BattleCombatant actor : activeActors) {
            CombatantId reserve = canonicalSwitches.get(actor.getInstanceId());
            if (reserve == null) {
                teamPlan.put(actor.getInstanceId(), canonicalPlans.get(actor.getInstanceId()));
            } else {
                teamPlan.switchTo(actor.getInstanceId(), reserve);
            }
        }
        String teamPlanError = teamPlan.validationError(battleState);
        if (teamPlanError != null) {
            return reject(commandId, INVALID_PLACEMENT, teamPlanError);
        }
        attachPlans(participant, canonicalPlans, canonicalSegments, canonicalSwitches);
        participant.planSubmitted = true;
        acceptedCommandIds.add(command.commandId());
        stateVersion++;

        if (!allPlansSubmitted()) {
            recentEvents = List.of();
            MatchState state = snapshot();
            return CommandResult.accepted(command.commandId(), List.of(), state);
        }

        recentEvents = List.copyOf(resolveSubmittedRound());
        MatchState state = snapshot();
        return CommandResult.accepted(command.commandId(), recentEvents, state);
    }

    /**
     * Inject the character lookup used to resolve summon character ids, so the
     * authoritative session can materialize shikigami during resolution without
     * loading files inside the engine. Pass an internal character lookup/catalog
     * so summon ids resolve without file I/O inside the engine.
     */
    public synchronized void setSummonLookup(BattleCharacterLookup lookup) {
        this.summonLookup = lookup;
        this.resolver.withSummonLookup(lookup);
    }

    /** Adds an initial fighter before submission; intended only for team-session tests. */
    synchronized String addFighterForTesting(String playerId, Character character) {
        ParticipantRuntime participant = requireParticipant(playerId);
        Objects.requireNonNull(character, "character");
        if (battleState.getCurrentPhase() != BattleState.Phase.PLANNING
            || participantsBySide.values().stream().anyMatch(runtime -> runtime.planSubmitted)) {
            throw new IllegalStateException("Test fighters can only be added before planning starts");
        }
        BattleCombatant fighter = new BattleCombatant(
            character, character.getAbilities(), statMode);
        battleState.addFighter(participant.teamId, fighter);
        roundStartCharacterStates = captureRoundStartCharacterStates();
        return fighter.getInstanceId().value();
    }

    /** Returns a complete immutable wire snapshot of the current authoritative state. */
    public synchronized MatchState snapshot() {
        return snapshot(null);
    }

    /** Returns a snapshot that conceals the opponent's unresolved plan from this viewer. */
    public synchronized MatchState snapshotFor(String viewerPlayerId) {
        requireParticipant(viewerPlayerId);
        return snapshot(viewerPlayerId);
    }

    private MatchState snapshot(String viewerPlayerId) {
        return new MatchState(
            matchId,
            status,
            ProtocolVersion.GAME_VERSION,
            ProtocolVersion.PROTOCOL_VERSION,
            statMode.rulesetId(),
            currentPhase(),
            wireRoundNumber,
            wireCurrentTick,
            List.of(
                playerState(participantsBySide.get(PlayerSide.PLAYER_ONE), viewerPlayerId),
                playerState(participantsBySide.get(PlayerSide.PLAYER_TWO), viewerPlayerId)
            ),
            roundStartCharacterStates,
            winnerSide,
            winnerPlayerId,
            endReason,
            stateVersion,
            recentEvents,
            domainBattlefieldState(),
            planningDeadline,
            clock.millis()
        );
    }

    /** Publishes the server-owned deadline for the active planning phase. */
    public synchronized MatchState setPlanningDeadline(long deadline) {
        if (currentPhase() != BattlePhase.PLANNING || isTerminal()) {
            throw new IllegalStateException("A planning deadline requires an active planning phase");
        }
        if (deadline <= clock.millis()) {
            throw new IllegalArgumentException("planning deadline must be in the future");
        }
        if (Objects.equals(planningDeadline, deadline)) {
            return snapshot();
        }
        planningDeadline = deadline;
        stateVersion++;
        return snapshot();
    }

    /** Removes a published deadline while planning is paused by connectivity. */
    public synchronized MatchState clearPlanningDeadline() {
        if (planningDeadline == null) {
            return snapshot();
        }
        planningDeadline = null;
        stateVersion++;
        return snapshot();
    }

    /** Locks every missing participant to an empty plan and resolves the round. */
    public synchronized MatchState expirePlanning() {
        if (currentPhase() != BattlePhase.PLANNING || isTerminal()) {
            return snapshot();
        }

        for (ParticipantRuntime participant : participantsBySide.values()) {
            if (participant.planSubmitted) {
                continue;
            }
            Map<CombatantId, BattlePlan> plans = new LinkedHashMap<>();
            Map<CombatantId, List<SegmentRuntime>> segments = new LinkedHashMap<>();
            for (BattleCombatant actor : activeCombatants(participant)) {
                plans.put(
                    actor.getInstanceId(),
                    BattlePlan.forCombatant(actor, battleGridLength())
                );
                segments.put(actor.getInstanceId(), List.of());
            }
            attachPlans(participant, plans, segments);
            participant.planSubmitted = true;
        }

        firstPlanBaseVersion = null;
        stateVersion++;
        recentEvents = List.copyOf(resolveSubmittedRound());
        return snapshot();
    }

    /**
     * Updates participant connectivity. The deadline is an epoch-millisecond value
     * owned by the caller; core records it but does not apply grace-period timing.
     */
    public synchronized MatchState setConnected(
        String playerId,
        boolean connected,
        Long disconnectDeadline
    ) {
        ParticipantRuntime participant = requireParticipant(playerId);
        Long normalizedDeadline = connected ? null : disconnectDeadline;

        boolean connectionChanged = participant.connected != connected
            || !Objects.equals(participant.disconnectDeadline, normalizedDeadline)
            || (connected && !participant.joined);

        participant.connected = connected;
        participant.joined |= connected;
        participant.disconnectDeadline = normalizedDeadline;
        MatchStatus nextStatus = isTerminal() ? status : ongoingConnectionStatus();

        if (!connectionChanged && nextStatus == status) {
            return snapshot();
        }

        status = nextStatus;
        stateVersion++;
        return snapshot();
    }

    public synchronized MatchState setConnected(String playerId, boolean connected) {
        return setConnected(playerId, connected, null);
    }

    /** Force a terminal match state, optionally naming a winning participant. */
    public synchronized MatchState forceEnd(
        String winnerPlayerId,
        MatchStatus terminalStatus,
        String reason
    ) {
        validateTerminalStatus(terminalStatus);
        if (isBlank(reason)) {
            throw new IllegalArgumentException("A terminal reason is required");
        }
        if (isTerminal()) {
            return snapshot();
        }

        ParticipantRuntime winner = winnerPlayerId == null
            ? null
            : requireParticipant(winnerPlayerId);
        BattleState.Phase previousPhase = battleState.getCurrentPhase();
        this.status = terminalStatus;
        this.winnerPlayerId = winner == null ? null : winner.participant.playerId();
        this.winnerSide = winner == null ? null : winner.participant.side();
        this.endReason = reason;
        planningDeadline = null;
        battleState.transitionTo(BattleState.Phase.BATTLE_OVER);
        stateVersion++;

        ParticipantRuntime loser = winner == null ? null : opponentOf(winner);
        BattleEventState terminalEvent = battleOverEvent(
            wireRoundNumber,
            wireCurrentTick,
            winner,
            loser,
            reason
        );
        if (previousPhase == BattleState.Phase.ROUND_END) {
            List<BattleEventState> events = new ArrayList<>(recentEvents);
            events.add(terminalEvent);
            recentEvents = List.copyOf(events);
        } else {
            recentEvents = List.of(terminalEvent);
        }
        return snapshot();
    }

    /** Convenience ordering for callers that lead with lifecycle status. */
    public synchronized MatchState forceEnd(
        MatchStatus terminalStatus,
        String winnerPlayerId,
        String reason
    ) {
        return forceEnd(winnerPlayerId, terminalStatus, reason);
    }

    /** Ends the match as a forfeit and awards the opponent the win. */
    public synchronized MatchState forfeit(String forfeitingPlayerId, String reason) {
        ParticipantRuntime forfeiting = requireParticipant(forfeitingPlayerId);
        return forceEnd(opponentOf(forfeiting).participant.playerId(), MatchStatus.ENDED, reason);
    }

    public String getMatchId() {
        return matchId;
    }

    public long getSeed() {
        return seed;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public synchronized MatchStatus getStatus() {
        return status;
    }

    public synchronized long getStateVersion() {
        return stateVersion;
    }

    public synchronized BattlePhase getPhase() {
        return currentPhase();
    }

    public synchronized int getRoundNumber() {
        return wireRoundNumber;
    }

    public String getPlayerOneId() {
        return participantsBySide.get(PlayerSide.PLAYER_ONE).participant.playerId();
    }

    public String getPlayerTwoId() {
        return participantsBySide.get(PlayerSide.PLAYER_TWO).participant.playerId();
    }

    public List<String> getParticipantIds() {
        return List.of(getPlayerOneId(), getPlayerTwoId());
    }

    public synchronized boolean isEnded() {
        return isTerminal();
    }

    public synchronized boolean isConnected(String playerId) {
        return requireParticipant(playerId).connected;
    }

    public synchronized boolean hasJoined(String playerId) {
        return requireParticipant(playerId).joined;
    }

    private List<BattleEventState> resolveSubmittedRound() {
        int resolvedRound = battleState.getRoundNumber();
        wireRoundNumber = resolvedRound;
        planningDeadline = null;
        resetRoundReadiness();
        battleState.transitionTo(BattleState.Phase.RESOLUTION);
        List<CombatEvent> resolutionEvents = new ArrayList<>(resolver.beginResolution(battleState));
        syncSegmentTargetsFromExecution();
        while (resolver.hasMoreTicks()) {
            resolutionEvents.addAll(resolver.resolveTick(battleState));
            if (battleState.checkAndResolveBattleOver()) {
                break;
            }
        }
        wireCurrentTick = Math.min(
            battleGridLength(),
            Math.max(
                resolutionEvents.stream().mapToInt(CombatEvent::getTick).max().orElse(0),
                Math.max(0, battleState.getCurrentTick() - 1)
            )
        );

        List<BattleEventState> events = new ArrayList<>(
            toWireEvents(resolutionEvents, resolvedRound)
        );
        updateSegmentStatuses(resolutionEvents);

        if (battleState.checkAndResolveBattleOver()) {
            ParticipantRuntime winner = runtimeFor(battleState.getWinnerTeam());
            ParticipantRuntime loser = winner == null ? null : opponentOf(winner);
            status = MatchStatus.ENDED;
            winnerSide = winner == null ? null : winner.participant.side();
            winnerPlayerId = winner == null ? null : winner.participant.playerId();
            endReason = winner == null ? "DOUBLE_KNOCKOUT" : "KNOCKOUT";
            if (events.stream().noneMatch(event -> event.type() == BattleEventType.BATTLE_OVER)) {
                events.add(battleOverEvent(
                    resolvedRound,
                    wireCurrentTick,
                    winner,
                    loser,
                    endReason
                ));
            }
            return events;
        }

        if (resolvedRound >= maxRounds) {
            status = MatchStatus.ENDED;
            winnerSide = null;
            winnerPlayerId = null;
            endReason = "MAX_ROUNDS_REACHED";
            battleState.transitionTo(BattleState.Phase.BATTLE_OVER);
            events.add(battleOverEvent(
                resolvedRound,
                wireCurrentTick,
                null,
                null,
                endReason
            ));
            return events;
        }

        battleState.transitionTo(BattleState.Phase.ROUND_END);
        events.addAll(toWireEvents(resolver.processRoundEnd(battleState), resolvedRound));
        if (battleState.isBattleOver()) {
            finishFromBattleState();
            return events;
        }
        status = ongoingConnectionStatus();
        return events;
    }

    private CommandResult applyReadyForBattle(
        ParticipantRuntime participant,
        ActionCommand command
    ) {
        if (battleStarted) {
            return reject(
                command.commandId(),
                WRONG_PHASE,
                "Battle readiness can only be submitted before planning begins."
            );
        }
        if (command.payload() != null) {
            return reject(
                command.commandId(),
                MALFORMED_COMMAND,
                "Battle readiness must not contain a payload."
            );
        }
        if (participant.readyForBattle) {
            return reject(
                command.commandId(),
                READY_ALREADY_SUBMITTED,
                "This player is already ready to start the battle."
            );
        }

        if (participantsBySide.values().stream().noneMatch(runtime -> runtime.readyForBattle)) {
            firstBattleReadyBaseVersion = command.expectedStateVersion();
        }
        participant.readyForBattle = true;
        acceptedCommandIds.add(command.commandId());
        stateVersion++;

        if (!allPlayersReadyForBattle()) {
            return CommandResult.accepted(command.commandId(), List.of(), snapshot());
        }

        battleStarted = true;
        firstBattleReadyBaseVersion = null;
        wireRoundNumber = battleState.getRoundNumber();
        wireCurrentTick = 0;
        status = ongoingConnectionStatus();

        List<BattleEventState> startEvents = new ArrayList<>();
        startEvents.add(systemEvent(
            BattleEventType.ROUND_START,
            wireRoundNumber,
            0,
            "Round " + wireRoundNumber + " started."
        ));
        List<CombatEvent> roundStartEvents = resolver.processRoundStart(battleState);
        startEvents.addAll(toWireEvents(roundStartEvents, wireRoundNumber));
        if (battleState.isBattleOver()) finishFromBattleState();
        recentEvents = List.copyOf(startEvents);
        roundStartCharacterStates = captureRoundStartCharacterStates();
        MatchState state = snapshot();
        return CommandResult.accepted(command.commandId(), recentEvents, state);
    }

    private CommandResult applyReadyNextRound(
        ParticipantRuntime participant,
        ActionCommand command
    ) {
        if (battleState.getCurrentPhase() != BattleState.Phase.ROUND_END) {
            return reject(
                command.commandId(),
                WRONG_PHASE,
                "Next-round readiness can only be submitted during round end."
            );
        }
        if (command.payload() != null) {
            return reject(
                command.commandId(),
                MALFORMED_COMMAND,
                "Next-round readiness must not contain a payload."
            );
        }
        if (participant.readyForNextRound) {
            return reject(
                command.commandId(),
                READY_ALREADY_SUBMITTED,
                "This player is already ready for the next round."
            );
        }

        if (participantsBySide.values().stream().noneMatch(runtime -> runtime.readyForNextRound)) {
            firstReadyBaseVersion = command.expectedStateVersion();
        }
        participant.readyForNextRound = true;
        acceptedCommandIds.add(command.commandId());
        stateVersion++;

        if (!allPlayersReadyForNextRound()) {
            return CommandResult.accepted(command.commandId(), List.of(), snapshot());
        }

        clearCompletedRound();
        battleState.transitionTo(BattleState.Phase.PLANNING);
        wireRoundNumber = battleState.getRoundNumber();
        wireCurrentTick = 0;
        status = ongoingConnectionStatus();

        List<BattleEventState> startEvents = new ArrayList<>();
        startEvents.add(systemEvent(
            BattleEventType.ROUND_START,
            wireRoundNumber,
            0,
            "Round " + wireRoundNumber + " started."
        ));
        List<CombatEvent> roundStartEvents = resolver.processRoundStart(battleState);
        startEvents.addAll(toWireEvents(roundStartEvents, wireRoundNumber));
        if (battleState.isBattleOver()) finishFromBattleState();
        recentEvents = List.copyOf(startEvents);
        roundStartCharacterStates = captureRoundStartCharacterStates();
        MatchState state = snapshot();
        return CommandResult.accepted(command.commandId(), recentEvents, state);
    }

    private void finishFromBattleState() {
        ParticipantRuntime winner = runtimeFor(battleState.getWinnerTeam());
        status = MatchStatus.ENDED;
        winnerSide = winner == null ? null : winner.participant.side();
        winnerPlayerId = winner == null ? null : winner.participant.playerId();
        endReason = winner == null ? "DOUBLE_KNOCKOUT" : "KNOCKOUT";
    }

    private void attachPlans(
        ParticipantRuntime participant,
        Map<CombatantId, BattlePlan> plans,
        Map<CombatantId, List<SegmentRuntime>> segmentsByActor
    ) {
        attachPlans(participant, plans, segmentsByActor, Map.of());
    }

    private void attachPlans(
        ParticipantRuntime participant,
        Map<CombatantId, BattlePlan> plans,
        Map<CombatantId, List<SegmentRuntime>> segmentsByActor,
        Map<CombatantId, CombatantId> switches
    ) {
        Map<CombatantId, BattlePlan> attachedPlans = new LinkedHashMap<>();
        Map<CombatantId, List<SegmentRuntime>> attachedSegments = new LinkedHashMap<>();
        TeamBattlePlan teamPlan = new TeamBattlePlan(participant.teamId, battleGridLength());
        for (BattleCombatant actor : activeCombatants(participant)) {
            CombatantId reserve = switches.get(actor.getInstanceId());
            if (reserve != null) {
                actor.setPlan(null);
                actor.setTimeline(null);
                teamPlan.switchTo(actor.getInstanceId(), reserve);
                continue;
            }
            BattlePlan plan = Objects.requireNonNull(
                plans.get(actor.getInstanceId()),
                "Missing canonical plan for " + actor.getInstanceId()
            );
            List<SegmentRuntime> segments = segmentsByActor.getOrDefault(
                actor.getInstanceId(), List.of());
            actor.setPlan(plan);

            Timeline executionTimeline = plan.toLegacyTimeline();
            actor.setTimeline(executionTimeline);
            List<ActionSegment> plannedOrder = plan.allSegments();
            List<ActionSegment> executionOrder = executionTimeline.getSegments();
            IdentityHashMap<ActionSegment, SegmentRuntime> byPlannedSegment =
                new IdentityHashMap<>();
            for (SegmentRuntime segment : segments) {
                byPlannedSegment.put(segment.plannedSegment, segment);
            }
            for (int index = 0; index < plannedOrder.size(); index++) {
                SegmentRuntime segment = byPlannedSegment.get(plannedOrder.get(index));
                if (segment != null) {
                    segment.executionSegment = executionOrder.get(index);
                    assignSegmentTargets(segment.executionSegment, segment.targetIds);
                }
            }
            attachedPlans.put(actor.getInstanceId(), plan);
            attachedSegments.put(actor.getInstanceId(), List.copyOf(segments));
            teamPlan.put(actor.getInstanceId(), plan);
        }
        battleState.queueSwitches(teamPlan);
        participant.plans = Collections.unmodifiableMap(attachedPlans);
        participant.segments = Collections.unmodifiableMap(attachedSegments);
    }

    private void updateSegmentStatuses(List<CombatEvent> events) {
        for (ParticipantRuntime participant : participantsBySide.values()) {
            for (List<SegmentRuntime> actorSegments : participant.segments.values()) {
                for (SegmentRuntime segment : actorSegments) {
                ActionSegment execution = segment.executionSegment;
                if (execution == null) {
                    continue;
                }
                if (execution.isStunned()) {
                    segment.status = ActionSegmentStatus.STUNNED;
                    segment.resolvedTick = findStunTick(segment, events)
                        .orElse(wireCurrentTick);
                } else if (battleState.isBattleOver() && execution.hasFired()) {
                    segment.status = ActionSegmentStatus.RESOLVED;
                    segment.resolvedTick = wireCurrentTick;
                } else if (wireCurrentTick >= execution.getFinalImpactTick()) {
                    segment.status = ActionSegmentStatus.RESOLVED;
                    segment.resolvedTick = execution.getFinalImpactTick();
                } else if (wireCurrentTick >= execution.getStartTick()) {
                    segment.status = ActionSegmentStatus.STARTED;
                    segment.resolvedTick = null;
                } else {
                    segment.status = ActionSegmentStatus.QUEUED;
                    segment.resolvedTick = null;
                }
                }
            }
        }
    }

    private void syncSegmentTargetsFromExecution() {
        for (ParticipantRuntime participant : participantsBySide.values()) {
            for (List<SegmentRuntime> actorSegments : participant.segments.values()) {
                for (SegmentRuntime segment : actorSegments) {
                    if (segment.executionSegment != null) {
                        segment.targetIds = List.copyOf(segment.executionSegment.getTargets());
                    }
                }
            }
        }
    }

    private Optional<Integer> findStunTick(
        SegmentRuntime segment,
        List<CombatEvent> events
    ) {
        BattleCombatant actor = battleState.combatant(segment.actorId);
        return events.stream()
            .filter(event -> event.getType() == CombatEvent.Type.MOVE_STUNNED
                || event.getType() == CombatEvent.Type.CE_DEPLETED)
            .filter(event -> {
                if (event.getType() == CombatEvent.Type.MOVE_STUNNED) {
                    return event.getTarget() == actor;
                }
                return event.getSource() == actor;
            })
            .filter(event -> event.getMove() == null
                || event.getType() == CombatEvent.Type.MOVE_STUNNED
                    && event.getComponentIndex() != null
                    && event.getTick() >= segment.plannedSegment.getStartTick()
                    && event.getTick() <= segment.plannedSegment.getEndTick()
                || event.getMove().getId().equals(segment.plannedSegment.getMove().getId()))
            .map(CombatEvent::getTick)
            .findFirst();
    }

    private void clearCompletedRound() {
        firstPlanBaseVersion = null;
        firstReadyBaseVersion = null;
        for (ParticipantRuntime participant : participantsBySide.values()) {
            participant.planSubmitted = false;
            participant.readyForNextRound = false;
            participant.plans = Map.of();
            participant.segments = Map.of();
            for (BattleCombatant combatant : allCombatants(participant)) {
                combatant.setPlan(null);
                combatant.setTimeline(null);
            }
        }
    }

    private void resetRoundReadiness() {
        firstReadyBaseVersion = null;
        for (ParticipantRuntime participant : participantsBySide.values()) {
            participant.readyForNextRound = false;
        }
    }

    private PlayerState playerState(ParticipantRuntime participant, String viewerPlayerId) {
        boolean concealPlan = viewerPlayerId != null
            && !participant.participant.playerId().equals(viewerPlayerId)
            && participant.planSubmitted
            && !allPlansSubmitted();
        return new PlayerState(
            participant.participant.playerId(),
            participant.participant.displayName(),
            participant.participant.side(),
            participant.connected,
            participant.readyForBattle,
            participant.planSubmitted,
            participant.readyForNextRound,
            participant.connected ? null : participant.disconnectDeadline,
            allCombatants(participant).stream()
                .map(combatant -> characterState(participant, combatant, concealPlan))
                .toList()
        );
    }

    private CharacterState characterState(
        ParticipantRuntime participant,
        BattleCombatant combatant,
        boolean concealPlan
    ) {
        BattlePlan plan = participant.plans.get(combatant.getInstanceId());
        int maxAp = combatant.getMaxApBar();
        int currentAp = plan == null || concealPlan
            ? maxAp
            : plan.remainingApBudget();
        Integer bfsExpiry = combatant.isInBlackFlashState()
            ? combatant.getBfsExpiresAfterRound()
            : null;

        return new CharacterState(
            combatant.getCharacter().getId(),
            combatant.getCharacter().getName(),
            combatant.getCurrentHp(),
            combatant.getMaxHp(),
            combatant.getCurrentCe(),
            combatant.getMaxCursedEnergy(),
            currentAp,
            maxAp,
            combatant.computeCurrentDefense(wireCurrentTick),
            combatant.isInBlackFlashState(),
            combatant.getConsecutiveBfsHits(),
            bfsExpiry,
            combatant.getActiveEffects().stream().map(this::statusEffectState).toList(),
            combatant.abilityStates(),
            combatant.getCharacter().getKnownMoves().stream()
                .map(move -> moveState(combatant, move))
                .toList(),
            planState(participant, combatant, concealPlan),
            combatant.getInstanceId().value(),
            combatant.getCharacter().getType().name(),
            combatant.getRole().name(),
            combatant.getLifecycle().name(),
            combatant.getSummonerId() == null ? null : combatant.getSummonerId().value(),
            combatant.getRosterOrder(),
            combatant.getAbilityFlags().maxActiveSummons
        );
    }

    private StatusEffectState statusEffectState(StatusEffect effect) {
        return new StatusEffectState(
            effect.getType().name(),
            effect.getType().displayName(),
            effect.getDurationRounds(),
            effect.getDurationTicks(),
            effect.getMagnitude()
        );
    }

    private MoveState moveState(BattleCombatant combatant, Move move) {
        int effectiveCeCost = combatant.computeMoveCeCost(move);
        return new MoveState(
            move.getId(),
            move.getName(),
            MoveDescriptionVariables.resolve(move, combatant),
            move.getCategory().name(),
            moveTags(move),
            planBoard(BattlePlan.boardFor(move)),
            move.getBasePower(),
            move.getHitComponents().stream()
                .map(component -> new HitComponentState(
                    component.getBasePower(),
                    component.getCategory().name(),
                    component.getTags().stream().map(MoveTag::name).toList(),
                    component.getDelayTicks(),
                    component.requiresPreviousConnection(),
                    component.isAvoidable(),
                    component.getBaseAccuracy(),
                    component.isReinforcementEligible(),
                    component.getReinforcementBonusPower()))
                .toList(),
            move.getBaseAccuracy(),
            move.isNeverMiss(),
            combatant.getEffectiveMoveApCost(move),
            combatant.getEffectiveMoveUnleashPoint(move),
            move.hasCeCost(),
            move.getBaseCeCost(),
            effectiveCeCost,
            move.getMinCeCost(),
            move.getMaxCeCost(),
            move.getMoveCap(),
            !isMoveRestricted(combatant, move),
            moveRestrictionReason(combatant, move),
            move.getSummonCharacterId(),
            MoveAvailability.summonedDefinitionIds(move),
            move.getAoeType() == null ? null : move.getAoeType().name(),
            move.getAoeTargetCount(),
            CursedSpeechAbility.commandMode(move),
            move.getRequiredTechniqueId(),
            move.getDefenseTargeting().name(),
            move.getDefenseTargetCount(),
            move.getTargeting().name(),
            move.getAttackLaunchMode() == null ? null : move.getAttackLaunchMode().name(),
            move.getAttackLaunchMoveId(),
            MoveAvailability.guaranteedBoundedResourceTransactions(combatant, move).stream()
                .map(effect -> new BoundedResourceTransactionState(
                    effect.sourceResourceKey,
                    effect.sourceResourceAmount == null ? 0 : effect.sourceResourceAmount,
                    effect.targetResourceKey,
                    effect.targetResourceAmount == null ? 0 : effect.targetResourceAmount))
                .toList(),
            new ReinforcementMoveState(
                move.canBeReinforced(),
                combatant.canReinforce(move),
                move.getReinforcementBaseCeCost(),
                combatant.canReinforce(move)
                    ? combatant.computeReinforcementCeCost(move) : 0,
                move.getReinforcementMinCeCost(),
                move.getReinforcementMaxCeCost(),
                move.getReinforcementDefenseType().name(),
                move.getReinforcementDefenseValue())
        );
    }

    private PlanState planState(
        ParticipantRuntime participant,
        BattleCombatant combatant,
        boolean concealPlan
    ) {
        BattlePlan plan = participant.plans.get(combatant.getInstanceId());
        if (concealPlan) {
            return new PlanState(
                wireRoundNumber,
                combatant.getMaxApBar(),
                0,
                combatant.getCurrentCe(),
                0,
                List.of(),
                List.of()
            );
        }
        int apBudget = plan == null ? combatant.getMaxApBar() : plan.apBudget();
        int apUsed = plan == null ? 0 : plan.totalApUsed();
        int ceBudget = plan == null ? combatant.getCurrentCe() : plan.ceBudget();
        int ceUsed = plan == null ? 0 : plan.totalCeUsed();

        List<ActionSegmentState> queued = new ArrayList<>();
        List<ActionSegmentState> resolved = new ArrayList<>();
        for (SegmentRuntime segment : participant.segments.getOrDefault(
            combatant.getInstanceId(), List.of())) {
            ActionSegmentState state = actionSegmentState(segment);
            if (segment.status == ActionSegmentStatus.RESOLVED
                || segment.status == ActionSegmentStatus.STUNNED) {
                resolved.add(state);
            } else {
                queued.add(state);
            }
        }
        return new PlanState(
            wireRoundNumber,
            apBudget,
            apUsed,
            ceBudget,
            ceUsed,
            queued,
            resolved
        );
    }

    private ActionSegmentState actionSegmentState(SegmentRuntime segment) {
        ActionSegment planned = segment.plannedSegment;
        return new ActionSegmentState(
            segment.segmentId,
            planned.getMove().getId(),
            planned.getMove().getName(),
            planBoard(segment.board),
            planned.getStartTick(),
            planned.getEndTick(),
            planned.getFireTick(),
            planned.getApCost(),
            planned.getActualCeCost(),
            segment.status,
            segment.resolvedTick,
            segment.actorId.value(),
            segment.targetIds.stream().map(CombatantId::value).toList(),
            planned.isReinforced(),
            planned.getReinforcementCeCost()
        );
    }

    private List<BattleEventState> toWireEvents(List<CombatEvent> events, int roundNumber) {
        List<BattleEventState> wireEvents = new ArrayList<>(events.size());
        for (CombatEvent event : events) {
            BattleCombatant sourceCombatant = event.getSource();
            BattleCombatant targetCombatant = event.getTarget();
            BattleCombatant relatedTargetCombatant = event.getRelatedTarget();
            ParticipantRuntime source = runtimeFor(sourceCombatant);
            ParticipantRuntime target = runtimeFor(targetCombatant);
            ParticipantRuntime relatedTarget = runtimeFor(relatedTargetCombatant);
            Move move = event.getMove();
            wireEvents.add(new BattleEventState(
                nextEventId(),
                BattleEventType.valueOf(event.getType().name()),
                roundNumber,
                event.getTick(),
                source == null ? null : source.participant.side(),
                sourceCombatant == null ? null : sourceCombatant.getCharacter().getId(),
                sourceCombatant == null ? null : sourceCombatant.getCharacter().getName(),
                target == null ? null : target.participant.side(),
                event.getCharacterId() != null
                    ? event.getCharacterId()
                    : targetCombatant == null ? null : targetCombatant.getCharacter().getId(),
                event.getCharacterName() != null
                    ? event.getCharacterName()
                    : targetCombatant == null ? null : targetCombatant.getCharacter().getName(),
                move == null ? null : move.getId(),
                move == null ? null : move.getName(),
                event.getComponentIndex(),
                eventValue(event),
                event.getCodedAbilityState(),
                event.getMessage(),
                sourceCombatant == null || sourceCombatant.getInstanceId() == null
                    ? null : sourceCombatant.getInstanceId().value(),
                targetCombatant == null || targetCombatant.getInstanceId() == null
                    ? null : targetCombatant.getInstanceId().value(),
                relatedTarget == null ? null : relatedTarget.participant.side(),
                relatedTargetCombatant == null
                    ? null : relatedTargetCombatant.getCharacter().getId(),
                relatedTargetCombatant == null
                    ? null : relatedTargetCombatant.getCharacter().getName(),
                relatedTargetCombatant == null || relatedTargetCombatant.getInstanceId() == null
                    ? null : relatedTargetCombatant.getInstanceId().value(),
                event.getDomainInstanceId(),
                event.getRelatedDomainInstanceId(),
                event.getDomainId(),
                event.getDomainName(),
                event.getDomainCollapseReason(),
                event.getReinforced(),
                event.getDefenseMoveId(),
                event.getDefenseReinforced()
            ));
        }
        return wireEvents;
    }

    private BattleEventState systemEvent(
        BattleEventType type,
        int roundNumber,
        int tick,
        String message
    ) {
        return new BattleEventState(
            nextEventId(),
            type,
            roundNumber,
            tick,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            message
        );
    }

    private BattleEventState battleOverEvent(
        int roundNumber,
        int tick,
        ParticipantRuntime winner,
        ParticipantRuntime loser,
        String reason
    ) {
        return new BattleEventState(
            nextEventId(),
            BattleEventType.BATTLE_OVER,
            roundNumber,
            tick,
            winner == null ? null : winner.participant.side(),
            winner == null ? null : winner.primaryCombatant.getCharacter().getId(),
            winner == null ? null : winner.primaryCombatant.getCharacter().getName(),
            loser == null ? null : loser.participant.side(),
            loser == null ? null : loser.primaryCombatant.getCharacter().getId(),
            loser == null ? null : loser.primaryCombatant.getCharacter().getName(),
            null,
            null,
            null,
            null,
            null,
            reason,
            winner == null ? null : winner.primaryCombatant.getInstanceId().value(),
            loser == null ? null : loser.primaryCombatant.getInstanceId().value()
        );
    }

    private static Integer eventValue(CombatEvent event) {
        return switch (event.getType()) {
            case DAMAGE_DEALT, DAMAGE_IGNORED, HP_RESTORED,
                 MAX_HP_CHANGED, MAX_CE_CHANGED, BLACK_FLASH,
                 CE_DRAINED, CE_RESTORED,
                 CHARACTER_TRANSFORMED, CHARACTER_REVERTED,
                 DOMAIN_BARRIER_DAMAGED -> event.getIntValue();
            default -> null;
        };
    }

    private DomainBattlefieldState domainBattlefieldState() {
        List<DomainState> domains = battleState.domainBattlefield().activeDomains().stream()
            .map(this::domainState)
            .toList();
        List<DomainClashState> clashes = battleState.domainBattlefield().clashes().stream()
            .map(clash -> new DomainClashState(
                clash.firstInstanceId(), clash.secondInstanceId(),
                clash.leaderInstanceId(), clash.takeoverProgress()))
            .toList();
        return new DomainBattlefieldState(domains, clashes);
    }

    private DomainState domainState(DomainInstance domain) {
        return new DomainState(
            domain.instanceId(),
            domain.definition().id(),
            domain.definition().name(),
            domain.ownerId().value(),
            domain.definition().antiDomain(),
            domain.definition().topology().name(),
            domain.definition().counterType().name(),
            domain.selectedTargetIds().stream().map(CombatantId::value).toList(),
            domain.memberIds().stream().map(CombatantId::value).toList(),
            domain.protectedIds().stream().map(CombatantId::value).toList(),
            domain.remainingRounds(),
            domain.remainingTicks(),
            domain.internalBarrierIntegrity(),
            domain.definition().clashValue(),
            domain.remainingCounterUses()
        );
    }

    private List<String> moveTags(Move move) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        move.getTags().stream().map(MoveTag::name).forEach(tags::add);
        move.getCategory().getTags().stream().map(MoveTag::name).forEach(tags::add);
        move.getHitComponents().stream()
            .flatMap(component -> component.getTags().stream())
            .map(MoveTag::name)
            .forEach(tags::add);
        if (move.hasTag("ATTACK")) tags.add(MoveTag.ATTACK.name());
        if (move.isGuardBreak()) tags.add(MoveTag.GUARD_BREAK.name());
        if (move.isHeavy()) tags.add(MoveTag.HEAVY.name());
        return List.copyOf(tags);
    }

    private Optional<Move> findKnownMove(BattleCombatant combatant, String moveId) {
        return combatant.getCharacter().getKnownMoves().stream()
            .filter(move -> move.getId().equals(moveId))
            .findFirst();
    }

    private boolean isMoveRestricted(BattleCombatant combatant, Move move) {
        return moveRestrictionReason(combatant, move) != null;
    }

    private String moveRestrictionReason(BattleCombatant combatant, Move move) {
        return MoveAvailability.restrictionReasonWithoutBoundedResources(
            battleState, combatant, move);
    }

    private boolean canUseSharedPlanningVersion(
        ParticipantRuntime participant,
        long expectedVersion
    ) {
        if (firstPlanBaseVersion == null
            || participant.planSubmitted
            || expectedVersion != firstPlanBaseVersion
            || stateVersion != firstPlanBaseVersion + 1
            || battleState.getCurrentPhase() != BattleState.Phase.PLANNING) {
            return false;
        }
        return participantsBySide.values().stream()
            .filter(runtime -> runtime.planSubmitted)
            .count() == 1;
    }

    private boolean canUseSharedCommandVersion(
        ParticipantRuntime participant,
        ActionCommand command
    ) {
        if (command.type() == CommandType.READY_FOR_BATTLE) {
            return canUseSharedBattleReadyVersion(
                participant, command.expectedStateVersion());
        }
        if (command.type() == CommandType.SUBMIT_PLAN) {
            return canUseSharedPlanningVersion(participant, command.expectedStateVersion());
        }
        return command.type() == CommandType.READY_NEXT_ROUND
            && canUseSharedReadyVersion(participant, command.expectedStateVersion());
    }

    private boolean canUseSharedBattleReadyVersion(
        ParticipantRuntime participant,
        long expectedVersion
    ) {
        if (battleStarted
            || firstBattleReadyBaseVersion == null
            || participant.readyForBattle
            || expectedVersion != firstBattleReadyBaseVersion
            || stateVersion != firstBattleReadyBaseVersion + 1) {
            return false;
        }
        return participantsBySide.values().stream()
            .filter(runtime -> runtime.readyForBattle)
            .count() == 1;
    }

    private boolean canUseSharedReadyVersion(
        ParticipantRuntime participant,
        long expectedVersion
    ) {
        if (firstReadyBaseVersion == null
            || participant.readyForNextRound
            || expectedVersion != firstReadyBaseVersion
            || stateVersion != firstReadyBaseVersion + 1
            || battleState.getCurrentPhase() != BattleState.Phase.ROUND_END) {
            return false;
        }
        return participantsBySide.values().stream()
            .filter(runtime -> runtime.readyForNextRound)
            .count() == 1;
    }

    private boolean allPlansSubmitted() {
        return participantsBySide.values().stream().allMatch(participant -> participant.planSubmitted);
    }

    private boolean allPlayersReadyForBattle() {
        return participantsBySide.values().stream()
            .allMatch(participant -> participant.readyForBattle);
    }

    private boolean allPlayersReadyForNextRound() {
        return participantsBySide.values().stream()
            .allMatch(participant -> participant.readyForNextRound);
    }

    private List<RoundStartCharacterState> captureRoundStartCharacterStates() {
        List<RoundStartCharacterState> states = new ArrayList<>();
        for (ParticipantRuntime participant : participantsBySide.values()) {
            for (BattleCombatant combatant : allCombatants(participant)) {
                states.add(roundStartCharacterState(participant, combatant));
            }
        }
        return List.copyOf(states);
    }

    private static RoundStartCharacterState roundStartCharacterState(
        ParticipantRuntime participant,
        BattleCombatant combatant
    ) {
        return new RoundStartCharacterState(
            participant.participant.side(),
            combatant.getCurrentHp(),
            combatant.getMaxHp(),
            combatant.getCurrentCe(),
            combatant.getMaxCursedEnergy(),
            combatant.abilityStates(),
            combatant.getInstanceId().value(),
            combatant.getCharacter().getId(),
            combatant.getCharacter().getName()
        );
    }

    private MatchStatus ongoingConnectionStatus() {
        if (participantsBySide.values().stream().anyMatch(participant -> !participant.joined)) {
            return MatchStatus.WAITING;
        }
        return participantsBySide.values().stream().anyMatch(participant -> !participant.connected)
            ? MatchStatus.OPPONENT_DISCONNECTED
            : MatchStatus.ACTIVE;
    }

    private BattlePhase currentPhase() {
        if (isTerminal()) {
            return BattlePhase.BATTLE_OVER;
        }
        if (!battleStarted) return BattlePhase.PRE_BATTLE;
        return BattlePhase.valueOf(battleState.getCurrentPhase().name());
    }

    private boolean isTerminal() {
        return status == MatchStatus.ENDED || status == MatchStatus.ABANDONED;
    }

    private ParticipantRuntime requireParticipant(String playerId) {
        ParticipantRuntime participant = participantsById.get(playerId);
        if (participant == null) {
            throw new IllegalArgumentException("Player is not a participant in this match: " + playerId);
        }
        return participant;
    }

    private ParticipantRuntime runtimeFor(BattleCombatant combatant) {
        return combatant == null ? null : runtimeFor(combatant.getTeamId());
    }

    private ParticipantRuntime runtimeFor(BattleTeamId teamId) {
        if (BattleTeamId.PLAYER.equals(teamId)) {
            return participantsBySide.get(PlayerSide.PLAYER_ONE);
        }
        if (BattleTeamId.ENEMY.equals(teamId)) {
            return participantsBySide.get(PlayerSide.PLAYER_TWO);
        }
        return null;
    }

    private List<BattleCombatant> activeCombatants(ParticipantRuntime participant) {
        BattleTeam team = battleState.teamOf(participant.teamId);
        return team == null ? List.of() : team.active();
    }

    private List<BattleCombatant> allCombatants(ParticipantRuntime participant) {
        BattleTeam team = battleState.teamOf(participant.teamId);
        return team == null ? List.of() : team.all();
    }

    private ParticipantRuntime opponentOf(ParticipantRuntime participant) {
        PlayerSide opponentSide = participant.participant.side() == PlayerSide.PLAYER_ONE
            ? PlayerSide.PLAYER_TWO
            : PlayerSide.PLAYER_ONE;
        return participantsBySide.get(opponentSide);
    }

    /** Battle-wide timeline grid length fixed at the start of the current round. */
    private int battleGridLength() {
        return battleState.getTimelineGridLength();
    }

    private CommandResult reject(String commandId, String code, String message) {
        return reject(commandId, code, message, Map.of());
    }

    private CommandResult reject(
        String commandId,
        String code,
        String message,
        Map<String, String> details
    ) {
        return CommandResult.rejected(
            commandId,
            new ErrorResponse(code, message, details),
            snapshot()
        );
    }

    private CommandResult rejectPlacement(
        String commandId,
        String code,
        String message,
        int index,
        String moveId
    ) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("placementIndex", Integer.toString(index));
        if (moveId != null) {
            details.put("moveId", moveId);
        }
        return reject(commandId, code, message, details);
    }

    private String segmentId(CombatantId actorId, int placementIndex) {
        return matchId + "-round-" + battleState.getRoundNumber()
            + "-" + actorId.value().toLowerCase(Locale.ROOT)
            + "-segment-" + (placementIndex + 1);
    }

    private String nextEventId() {
        eventSequence++;
        return matchId + "-event-" + eventSequence;
    }

    private static PlanBoard planBoard(BattlePlan.Board board) {
        return PlanBoard.valueOf(board.name());
    }

    private static String displayEnumName(String enumName) {
        StringBuilder display = new StringBuilder();
        for (String word : enumName.split("_")) {
            if (!display.isEmpty()) display.append(' ');
            if ("CE".equals(word) || "AP".equals(word) || "BFS".equals(word)) {
                display.append(word);
            } else {
                display.append(word.charAt(0))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return display.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Bridges the in-flight ActionSegment target migration without coupling this
     * transport workstream to either version of that class.
     */
    private static void assignSegmentTargets(
        ActionSegment segment,
        List<CombatantId> targetIds
    ) {
        List<CombatantId> immutableTargets = List.copyOf(targetIds);
        try {
            segment.getClass().getMethod("setTargets", List.class)
                .invoke(segment, immutableTargets);
            return;
        } catch (NoSuchMethodException ignored) {
            // Fall through to the pre-v12 singular API while the core migration lands.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not assign action segment targets", exception);
        }

        // The old core cannot retain MULTIPLE intent. SegmentRuntime still does,
        // and the list setter above becomes authoritative once that migration lands.
        if (immutableTargets.size() > 1) return;
        try {
            segment.getClass().getMethod("setTarget", CombatantId.class)
                .invoke(segment, immutableTargets.isEmpty() ? null : immutableTargets.get(0));
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("ActionSegment exposes no target setter", exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not assign action segment target", exception);
        }
    }

    private static MatchParticipant participantAt(List<MatchParticipant> participants, int index) {
        if (participants == null || participants.size() != 2) {
            throw new IllegalArgumentException("A match requires exactly two participants");
        }
        return participants.get(index);
    }

    private static void validateTerminalStatus(MatchStatus terminalStatus) {
        if (terminalStatus != MatchStatus.ENDED && terminalStatus != MatchStatus.ABANDONED) {
            throw new IllegalArgumentException("Terminal status must be ENDED or ABANDONED");
        }
    }

    private static final class ParticipantRuntime {
        private final MatchParticipant participant;
        private final BattleTeamId teamId;
        private final List<BattleCombatant> combatants;
        private final BattleCombatant primaryCombatant;
        private boolean connected;
        private boolean joined;
        private boolean readyForBattle;
        private boolean planSubmitted;
        private boolean readyForNextRound;
        private Long disconnectDeadline;
        private Map<CombatantId, BattlePlan> plans = Map.of();
        private Map<CombatantId, List<SegmentRuntime>> segments = Map.of();

        private ParticipantRuntime(
            MatchParticipant participant,
            BattleTeamId teamId,
            BattleStatMode statMode
        ) {
            this.participant = participant;
            this.teamId = teamId;
            this.combatants = participant.characters().stream()
                .map(character -> new BattleCombatant(
                    character, character.getAbilities(), statMode))
                .toList();
            this.primaryCombatant = this.combatants.get(0);
        }
    }

    private static final class SegmentRuntime {
        private final String segmentId;
        private final CombatantId actorId;
        private final ActionSegment plannedSegment;
        private final BattlePlan.Board board;
        private List<CombatantId> targetIds;
        private ActionSegment executionSegment;
        private ActionSegmentStatus status = ActionSegmentStatus.QUEUED;
        private Integer resolvedTick;

        private SegmentRuntime(
            String segmentId,
            CombatantId actorId,
            ActionSegment plannedSegment,
            BattlePlan.Board board,
            List<CombatantId> targetIds
        ) {
            this.segmentId = segmentId;
            this.actorId = actorId;
            this.plannedSegment = plannedSegment;
            this.board = board;
            this.targetIds = List.copyOf(targetIds);
        }
    }
}
