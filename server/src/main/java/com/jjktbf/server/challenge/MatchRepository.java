package com.jjktbf.server.challenge;

import com.jjktbf.multiplayer.protocol.MatchStatus;
import com.jjktbf.multiplayer.protocol.PlayerSide;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

final class MatchRepository {
    void insertMatch(
        Connection connection,
        String matchId,
        String challengeId,
        MatchStatus status,
        long serverSeed,
        String gameVersion,
        int protocolVersion,
        String ruleset,
        long createdAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO match_record ("
                + "id, challenge_id, status, server_seed, game_version, "
                + "protocol_version, ruleset, created_at, started_at, ended_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)")) {
            statement.setString(1, matchId);
            statement.setString(2, challengeId);
            statement.setString(3, status.name());
            statement.setLong(4, serverSeed);
            statement.setString(5, gameVersion);
            statement.setInt(6, protocolVersion);
            statement.setString(7, ruleset);
            statement.setLong(8, createdAt);
            statement.executeUpdate();
        }
    }

    void insertParticipant(
        Connection connection,
        String matchId,
        String playerId,
        PlayerSide side,
        List<String> characterIds
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO match_participant "
                + "(match_id, player_id, side, character_ids, move_set_ids, "
                + "joined_at, disconnected_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, matchId);
            statement.setString(2, playerId);
            statement.setString(3, side.name());
            statement.setString(4, RosterCodec.encode(characterIds));
            statement.setString(5, MoveSetCodec.encode(List.of()));
            statement.setNull(6, Types.BIGINT);
            statement.setNull(7, Types.BIGINT);
            statement.executeUpdate();
        }
    }

    int selectParticipantCharacters(
        Connection connection,
        String matchId,
        String playerId,
        List<String> characterIds
    ) throws SQLException {
        return selectParticipantCharacters(
            connection, matchId, playerId, characterIds, List.of());
    }

    int selectParticipantCharacters(
        Connection connection,
        String matchId,
        String playerId,
        List<String> characterIds,
        List<List<String>> moveSetIds
    ) throws SQLException {
        String encoded = RosterCodec.encode(characterIds);
        String encodedMoveSets = MoveSetCodec.encode(moveSetIds);
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE match_participant SET character_ids = ?, move_set_ids = ? "
                + "WHERE match_id = ? AND player_id = ? "
                + "AND (character_ids = '' OR (character_ids = ? "
                + "AND (move_set_ids = '[]' OR move_set_ids = ?)))")) {
            statement.setString(1, encoded);
            statement.setString(2, encodedMoveSets);
            statement.setString(3, matchId);
            statement.setString(4, playerId);
            statement.setString(5, encoded);
            statement.setString(6, encodedMoveSets);
            return statement.executeUpdate();
        }
    }

    Optional<PersistedParticipantSelection> findParticipantSelection(
        Connection connection,
        String matchId,
        String playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT character_ids, move_set_ids FROM match_participant "
                + "WHERE match_id = ? AND player_id = ?")) {
            statement.setString(1, matchId);
            statement.setString(2, playerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                try {
                    return Optional.of(new PersistedParticipantSelection(
                        RosterCodec.decodeOrEmpty(result.getString("character_ids")),
                        MoveSetCodec.decode(result.getString("move_set_ids"))));
                } catch (IllegalArgumentException exception) {
                    throw new SQLException("Stored participant selection is invalid", exception);
                }
            }
        }
    }

    Optional<PersistedMatch> findByChallenge(Connection connection, String challengeId)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id, status, server_seed, created_at FROM match_record WHERE challenge_id = ?")) {
            statement.setString(1, challengeId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PersistedMatch(
                    result.getString("id"),
                    MatchStatus.valueOf(result.getString("status")),
                    result.getLong("server_seed"),
                    result.getLong("created_at")
                ));
            }
        }
    }

    record PersistedMatch(
        String matchId,
        MatchStatus status,
        long serverSeed,
        long createdAt
    ) {
    }

    record PersistedParticipantSelection(
        List<String> characterIds,
        List<List<String>> moveSetIds
    ) {
        PersistedParticipantSelection {
            characterIds = characterIds == null ? List.of() : List.copyOf(characterIds);
            moveSetIds = moveSetIds == null ? List.of() : moveSetIds.stream()
                .map(ids -> ids == null ? List.<String>of() : List.copyOf(ids))
                .toList();
        }
    }
}
