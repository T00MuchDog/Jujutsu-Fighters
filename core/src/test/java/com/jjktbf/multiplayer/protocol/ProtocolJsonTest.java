package com.jjktbf.multiplayer.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.combat.BattleStatMode;
import com.jjktbf.model.combat.CombatEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void characterSelectionRoundTripsOrderedMoveSetsAndCopiesNestedLists() throws Exception {
        List<String> firstMoveSet = new ArrayList<>(List.of("move-b", "move-a"));
        MatchCharacterSelectionRequest request = new MatchCharacterSelectionRequest(
            List.of("fighter-a", "fighter-b"),
            List.of(firstMoveSet, List.of("move-c")));
        firstMoveSet.clear();

        String json = mapper.writeValueAsString(request);
        MatchCharacterSelectionRequest restored = mapper.readValue(
            json, MatchCharacterSelectionRequest.class);

        assertEquals(request, restored);
        assertEquals(List.of("move-b", "move-a"), request.moveSetIds().get(0));
        assertThrows(UnsupportedOperationException.class,
            () -> request.moveSetIds().get(0).add("move-d"));
    }

    @Test
    void completeMatchStateRoundTrips() throws Exception {
        MatchState state = completeMatchState();

        String json = mapper.writeValueAsString(state);
        MatchState restored = mapper.readValue(json, MatchState.class);
        JsonNode tree = mapper.readTree(json);

        assertEquals(state, restored);
        assertEquals(42L, tree.get("stateVersion").longValue());
        assertEquals("Divergent Fist", tree.at("/players/0/combatants/0/knownMoves/0/name").textValue());
        assertEquals(ActionSegmentStatus.QUEUED, restored.players().get(0).character()
            .plan().queuedSegments().get(0).status());
        assertEquals(88, restored.player(PlayerSide.PLAYER_TWO).orElseThrow()
            .character().currentDefense());
        assertEquals(4, tree.at("/players/0/combatants/0/codedAbilities/0/currentValue").intValue());
        assertEquals(205, tree.at("/roundStartCharacterStates/0/currentHp").intValue());
        assertEquals(2, restored.players().get(0).character()
            .knownMoves().get(0).hitComponents().size());
        assertEquals(1, restored.players().get(0).character()
            .knownMoves().get(0).moveCap());
        assertEquals(4, tree.at(
            "/players/0/combatants/0/knownMoves/0/hitComponents/1/delayTicks").intValue());
        assertEquals(1, restored.recentEvents().get(0).componentIndex());
        assertEquals(List.of("ENEMY-f1", "ENEMY-f2"), restored.players().get(0).character()
            .plan().queuedSegments().get(0).targetIds());
        assertTrue(tree.at(
            "/players/0/combatants/0/plan/queuedSegments/0/targetIds").isArray());
        assertFalse(tree.at(
            "/players/0/combatants/0/plan/queuedSegments/0").has("targetId"));
        assertTrue(restored.players().get(0).readyForNextRound());
        assertTrue(restored.players().get(0).readyForBattle());
        assertThrows(UnsupportedOperationException.class, () -> restored.players().add(null));
        assertThrows(UnsupportedOperationException.class,
            () -> restored.roundStartCharacterStates().clear());
        assertThrows(UnsupportedOperationException.class,
            () -> restored.recentEvents().clear());
    }

    @Test
    void combatAndWireEventEnumsRemainInExactParity() {
        Set<String> core = java.util.Arrays.stream(CombatEvent.Type.values())
            .map(Enum::name).collect(java.util.stream.Collectors.toSet());
        Set<String> wire = java.util.Arrays.stream(BattleEventType.values())
            .map(Enum::name).collect(java.util.stream.Collectors.toSet());

        assertEquals(core, wire);
    }

    @Test
    void domainEventMetadataRoundTrips() throws Exception {
        BattleEventState event = new BattleEventState(
            "domain-event", BattleEventType.DOMAIN_COLLAPSED, 3, 7,
            PlayerSide.PLAYER_ONE, "caster", "Caster",
            null, null, null, null, null,
            null, 25, null, "The Domain collapses.",
            "PLAYER-f1", null, null, null, null, null,
            "domain-1", "domain-2", "UNLIMITED_VOID", "Unlimited Void",
            "INTERNAL_BARRIER_BROKEN");

        BattleEventState restored = mapper.readValue(
            mapper.writeValueAsString(event), BattleEventState.class);

        assertEquals(event, restored);
        assertEquals("domain-1", restored.domainInstanceId());
        assertEquals("INTERNAL_BARRIER_BROKEN", restored.domainCollapseReason());
    }

    @Test
    void summonPlanningMetadataRoundTrips() throws Exception {
        MoveState summon = new MoveState(
            "SUMMON_DOG", "Summon Dog", "Manifest a Divine Dog.", "UTILITY",
            List.of("UTILITY"), PlanBoard.DEFENSIVE, 0, List.of(), 1.0, true,
            5, 1, true, 24, 24, 12, 42, 1, true, null, "DOG", List.of("DOG"));
        CharacterState megumi = new CharacterState(
            "MEGUMI", "Megumi", 100, 100, 100, 100, 60, 60, 20,
            false, 0, null, List.of(), List.of(), List.of(summon), null,
            "PLAYER-f1", "SORCERER", "FIGHTER", "ACTIVE", null, 0, 2);

        CharacterState restored = mapper.readValue(
            mapper.writeValueAsString(megumi), CharacterState.class);

        assertEquals(2, restored.maxActiveSummons());
        assertEquals("DOG", restored.knownMoves().get(0).summonCharacterId());
        assertEquals(List.of("DOG"), restored.knownMoves().get(0).summonedCharacterIds());
    }

    @Test
    void aoePlanningMetadataRoundTrips() throws Exception {
        MoveState multiple = new MoveState(
            "MULTI", "Multiple", "Choose several enemies.", "PHYSICAL",
            List.of("ATTACK", "AOE", "PHYSICAL"), PlanBoard.OFFENSIVE,
            20, List.of(), 1.0, true, 5, 1, false, 0, 0, 0, 0,
            0, true, null, null, List.of(), "MULTIPLE", 3, "RETURN", "Cursed Speech");

        String json = mapper.writeValueAsString(multiple);
        MoveState restored = mapper.readValue(json, MoveState.class);
        JsonNode tree = mapper.readTree(json);

        assertEquals(multiple, restored);
        assertEquals("MULTIPLE", tree.get("aoeType").textValue());
        assertEquals(3, tree.get("aoeTargetCount").intValue());
        assertEquals("RETURN", tree.get("commandMode").textValue());
        assertEquals("Cursed Speech", tree.get("requiredTechniqueId").textValue());
    }

    @Test
    void pairAndDefenseTargetingMetadataRoundTripsWithSafeDefaults() throws Exception {
        MoveState pair = new MoveState(
            "PAIR", "Pair", "Choose endpoints.", "UTILITY", List.of("UTILITY"),
            PlanBoard.DEFENSIVE, 0, List.of(), 1.0, true, 5, 1, false,
            0, 0, 0, 0, 0, true, null, null, List.of(), null, 0, null, null,
            "SINGLE_ALLY", 4, "ALLY_AND_ENEMY");

        MoveState restored = mapper.readValue(mapper.writeValueAsString(pair), MoveState.class);
        MoveState legacy = mapper.readValue("{\"moveId\":\"OLD\"}", MoveState.class);

        assertEquals("SINGLE_ALLY", restored.defenseTargeting());
        assertEquals(4, restored.defenseTargetCount());
        assertEquals("ALLY_AND_ENEMY", restored.targeting());
        assertEquals("SELF", legacy.defenseTargeting());
        assertEquals(2, legacy.defenseTargetCount());
        assertEquals("DEFAULT", legacy.targeting());
    }

    @Test
    void targetExchangeEventRoundTripsBothCombatantEndpoints() throws Exception {
        BattleEventState event = new BattleEventState(
            "event-swap", BattleEventType.TARGETS_EXCHANGED, 2, 14,
            PlayerSide.PLAYER_ONE, "000019", "Aoi Todo",
            PlayerSide.PLAYER_ONE, "000019", "Aoi Todo",
            "000092", "Boogie Woogie", null, null, null,
            "Todo exchanged the attack targets.",
            "PLAYER-f1", "PLAYER-f1", PlayerSide.PLAYER_TWO,
            "000005", "Hanami", "ENEMY-f1");

        BattleEventState restored = mapper.readValue(
            mapper.writeValueAsString(event), BattleEventState.class);

        assertEquals(event, restored);
        assertEquals("PLAYER-f1", restored.targetInstanceId());
        assertEquals(PlayerSide.PLAYER_TWO, restored.relatedTargetSide());
        assertEquals("ENEMY-f1", restored.relatedTargetInstanceId());
    }

    @Test
    void actionCommandRoundTripsAndCopiesIntent() throws Exception {
        List<PlanPlacement> placements = new ArrayList<>();
        placements.add(new PlanPlacement(
            "000004", 13, "PLAYER-f1", List.of("ENEMY-f1", "ENEMY-f2"), true));
        placements.add(new PlanPlacement("000001", 48));
        ActionCommand command = ActionCommand.submitPlan("command-1", "match-1", 41, placements);
        placements.clear();

        String json = mapper.writeValueAsString(command);
        ActionCommand restored = mapper.readValue(json, ActionCommand.class);
        JsonNode tree = mapper.readTree(json);

        assertEquals(command, restored);
        assertEquals(CommandType.SUBMIT_PLAN, restored.type());
        assertEquals(2, restored.payload().placements().size());
        assertTrue(command.payload().placements().get(0).reinforced());
        assertTrue(restored.payload().placements().get(0).reinforced());
        assertTrue(tree.at("/payload/placements/0/reinforced").booleanValue());
        assertEquals(5, tree.at("/payload/placements/0").size());
        assertEquals("PLAYER-f1", restored.payload().placements().get(0).actorId());
        assertEquals(List.of("ENEMY-f1", "ENEMY-f2"),
            restored.payload().placements().get(0).targetIds());
        assertEquals("ENEMY-f1", restored.payload().placements().get(0).targetId());
        assertTrue(tree.at("/payload/placements/0/targetIds").isArray());
        assertFalse(tree.at("/payload/placements/0").has("targetId"));
        assertEquals(List.of(), new SubmitPlanPayload(null).placements());
        assertThrows(UnsupportedOperationException.class,
            () -> restored.payload().placements().get(0).targetIds().add("ENEMY-f3"));
        assertThrows(UnsupportedOperationException.class,
            () -> restored.payload().placements().add(new PlanPlacement("other", 1)));
    }

    @Test
    void readyNextRoundCommandRoundTripsWithoutPayload() throws Exception {
        ActionCommand command = ActionCommand.readyNextRound("ready-1", "match-1", 42);

        String json = mapper.writeValueAsString(command);
        ActionCommand restored = mapper.readValue(json, ActionCommand.class);
        JsonNode tree = mapper.readTree(json);

        assertEquals(command, restored);
        assertEquals(CommandType.READY_NEXT_ROUND, restored.type());
        assertNull(restored.payload());
        assertTrue(tree.get("payload").isNull());
    }

    @Test
    void readyForBattleCommandRoundTripsWithoutPayload() throws Exception {
        ActionCommand command = ActionCommand.readyForBattle("start-1", "match-1", 42);

        String json = mapper.writeValueAsString(command);
        ActionCommand restored = mapper.readValue(json, ActionCommand.class);
        JsonNode tree = mapper.readTree(json);

        assertEquals(command, restored);
        assertEquals(CommandType.READY_FOR_BATTLE, restored.type());
        assertNull(restored.payload());
        assertTrue(tree.get("payload").isNull());
    }

    @Test
    void challengeSummaryRoundTrips() throws Exception {
        ChallengeSummary summary = new ChallengeSummary(
            "challenge-1",
            "player-1",
            "Guest Mantis",
            List.of("character-1"),
            List.of("Yuji Itadori"),
            ChallengeStatus.OPEN,
            com.jjktbf.model.combat.BattleFormat.ONE_V_ONE,
            ProtocolVersion.GAME_VERSION,
            ProtocolVersion.PROTOCOL_VERSION,
            ProtocolVersion.STANDARD_RULESET,
            1_700_000_000_000L,
            1_700_000_300_000L,
            "request-1",
            "player-2",
            List.of("character-2"),
            1_700_000_010_000L,
            null,
            null
        );

        String json = mapper.writeValueAsString(summary);
        ChallengeSummary restored = mapper.readValue(json, ChallengeSummary.class);
        JsonNode tree = mapper.readTree(json);

        assertEquals(summary, restored);
        assertEquals("OPEN", tree.get("status").textValue());
        assertEquals("Yuji Itadori", restored.hostCharacterName());
        assertEquals("character-1", restored.hostCharacterId());
        assertEquals(List.of("character-1"), restored.hostCharacterIds());
        assertEquals("player-2", restored.requestedPlayerId());
        assertEquals("request-1", restored.joinRequestId());
        assertFalse(tree.has("matchId"));
    }

    @Test
    void socketMessagesRoundTripForEveryExplicitType() throws Exception {
        MatchState state = completeMatchState();
        ActionCommand command = ActionCommand.submitPlan(
            "command-1", state.matchId(), state.stateVersion(),
            List.of(new PlanPlacement("000004", 13)),
            List.of(new SwitchSelection("PLAYER-f2", "PLAYER-f4")));
        ErrorResponse error = new ErrorResponse(
            "STATE_VERSION_MISMATCH",
            "The match state changed.",
            Map.of("expected", "42", "actual", "43")
        );

        List<SocketMessage> messages = List.of(
            SocketMessage.joinMatch(state.matchId()),
            SocketMessage.matchJoined(state.matchId(), "player-1", "Guest Mantis", PlayerSide.PLAYER_ONE, state),
            SocketMessage.submitAction(command),
            SocketMessage.matchState(state),
            SocketMessage.commandRejected(state.matchId(), command.commandId(), error, state),
            SocketMessage.playerConnected(state.matchId(), "player-2", "Guest Crane", PlayerSide.PLAYER_TWO),
            SocketMessage.playerDisconnected(
                state.matchId(), "player-2", "Guest Crane", PlayerSide.PLAYER_TWO, 1_700_000_060_000L),
            SocketMessage.matchEnded(state),
            SocketMessage.ping(1_700_000_000_100L),
            SocketMessage.pong(1_700_000_000_100L),
            SocketMessage.error(state.matchId(), error)
        );

        assertEquals(List.of(MessageType.values()), messages.stream().map(SocketMessage::type).toList());
        for (SocketMessage message : messages) {
            String json = mapper.writeValueAsString(message);
            assertEquals(message, mapper.readValue(json, SocketMessage.class), message.type().name());
        }

        SocketMessage joined = messages.get(1);
        assertEquals(ProtocolVersion.GAME_VERSION, joined.gameVersion());
        assertEquals(ProtocolVersion.PROTOCOL_VERSION, joined.protocolVersion());
        assertEquals(24, joined.protocolVersion());
        assertEquals(List.of(new SwitchSelection("PLAYER-f2", "PLAYER-f4")),
            command.payload().switches());
        assertEquals(42L, joined.stateVersion());
        assertEquals(1_700_000_060_000L, messages.get(6).disconnectDeadline());
        assertEquals(1_700_000_090_000L, joined.state().planningDeadline());
        assertTrue(ProtocolVersion.isCompatible(
            joined.gameVersion(), joined.protocolVersion(), joined.ruleset()));
        assertFalse(ProtocolVersion.isCompatible(
            joined.gameVersion(), 11, joined.ruleset()));
        assertTrue(ProtocolVersion.isCompatible(
            ProtocolVersion.GAME_VERSION,
            ProtocolVersion.PROTOCOL_VERSION,
            BattleStatMode.EQUALIZED.rulesetId()));
        assertFalse(ProtocolVersion.isCompatible(
            ProtocolVersion.GAME_VERSION,
            ProtocolVersion.PROTOCOL_VERSION,
            "UNKNOWN_RULESET"));
    }

    private static MatchState completeMatchState() {
        MoveState divergentFist = new MoveState(
            "000004",
            "Divergent Fist",
            "A delayed cursed-energy strike.",
            "PHYSICAL_CURSED_ENERGY",
            List.of("PHYSICAL", "CURSED_ENERGY", "ATTACK"),
            PlanBoard.OFFENSIVE,
            75,
            List.of(
                new HitComponentState(
                    50, "PHYSICAL", List.of("PHYSICAL"), 0, false, true, 1.0),
                new HitComponentState(
                    25, "CURSED_ENERGY", List.of("CURSED_ENERGY"), 4, true, false, 1.0)),
            0.90,
            false,
            25,
            12,
            true,
            40,
            32,
            10,
            80,
            1,
            true,
            null
        );
        MoveState basicBlock = new MoveState(
            "000001",
            "Basic Block",
            "Guard against an incoming attack.",
            "DEFENSIVE",
            List.of("PHYSICAL", "DEFENSIVE"),
            PlanBoard.DEFENSIVE,
            0,
            1.0,
            true,
            10,
            1,
            false,
            0,
            0,
            0,
            0,
            true,
            null
        );
        ActionSegmentState queued = new ActionSegmentState(
            "segment-3-1",
            divergentFist.moveId(),
            divergentFist.name(),
            PlanBoard.OFFENSIVE,
            13,
            37,
            24,
            25,
            32,
            ActionSegmentStatus.QUEUED,
            null,
            "PLAYER-f1",
            List.of("ENEMY-f1", "ENEMY-f2")
        );
        ActionSegmentState resolved = new ActionSegmentState(
            "segment-2-2",
            basicBlock.moveId(),
            basicBlock.name(),
            PlanBoard.DEFENSIVE,
            5,
            14,
            5,
            10,
            0,
            ActionSegmentStatus.RESOLVED,
            5
        );
        PlanState playerOnePlan = new PlanState(
            3, 90, 35, 320, 32, List.of(queued), List.of(resolved));
        CharacterState playerOneCharacter = new CharacterState(
            "character-1",
            "Yuji Itadori",
            205,
            267,
            288,
            400,
            55,
            90,
            91,
            true,
            2,
            4,
            List.of(new StatusEffectState(
                "ACCURACY_INCREASE", "Increase Accuracy", 1, 5, 10.0)),
            List.of(new CodedAbilityState("MIRACLES", "Miracles", 4, 6)),
            List.of(divergentFist, basicBlock),
            playerOnePlan
        );
        CharacterState playerTwoCharacter = new CharacterState(
            "character-2",
            "Megumi Fushiguro",
            171,
            240,
            350,
            450,
            84,
            84,
            88,
            false,
            0,
            null,
            List.of(new StatusEffectState(
                "DEFENSE_INCREASE", "Increase Defense", -1, 0, 12.0)),
            List.of(),
            List.of(basicBlock),
            new PlanState(3, 84, 0, 350, 0, null, null)
        );
        PlayerState playerOne = new PlayerState(
            "player-1", "Guest Mantis", PlayerSide.PLAYER_ONE,
            true, true, true, true, null, playerOneCharacter);
        PlayerState playerTwo = new PlayerState(
            "player-2", "Guest Crane", PlayerSide.PLAYER_TWO,
            false, false, false, false, 1_700_000_060_000L, playerTwoCharacter);
        BattleEventState event = new BattleEventState(
            "event-17",
            BattleEventType.DAMAGE_DEALT,
            2,
            24,
            PlayerSide.PLAYER_ONE,
            playerOneCharacter.characterId(),
            playerOneCharacter.name(),
            PlayerSide.PLAYER_TWO,
            playerTwoCharacter.characterId(),
            playerTwoCharacter.name(),
            divergentFist.moveId(),
            divergentFist.name(),
            1,
            39,
            new CodedAbilityState("MIRACLES", "Miracles", 4, 6),
            "Yuji Itadori dealt 39 damage."
        );

        return new MatchState(
            "match-1",
            MatchStatus.OPPONENT_DISCONNECTED,
            ProtocolVersion.GAME_VERSION,
            ProtocolVersion.PROTOCOL_VERSION,
            ProtocolVersion.STANDARD_RULESET,
            BattlePhase.PLANNING,
            3,
            0,
            List.of(playerOne, playerTwo),
            List.of(
                new RoundStartCharacterState(PlayerSide.PLAYER_ONE, 205, 214, 320, 400,
                    List.of(new CodedAbilityState("MIRACLES", "Miracles", 4, 6))),
                new RoundStartCharacterState(PlayerSide.PLAYER_TWO, 171, 214, 350, 400, List.of())
            ),
            null,
            null,
            null,
            42,
            List.of(event),
            new DomainBattlefieldState(
                List.of(new DomainState(
                    "domain-1", "UNLIMITED_VOID", "Unlimited Void", "PLAYER-f1",
                    false, "CLOSED", "NONE", List.of("ENEMY-f1"),
                    List.of("PLAYER-f1", "ENEMY-f1"), List.of("PLAYER-f1"),
                    1, 4, 80, 100, -1)),
                List.of(new DomainClashState(
                    "domain-1", "domain-2", "domain-1", 0.25))),
            1_700_000_090_000L,
            1_700_000_000_000L
        );
    }
}
