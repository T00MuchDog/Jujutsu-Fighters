package com.jjktbf.server.challenge;

import com.jjktbf.model.character.Character;
import com.jjktbf.multiplayer.protocol.PlayerSide;

import java.util.List;
import java.util.Objects;

/**
 * Canonical participant data used to construct a later authoritative match.
 *
 * <p>{@code characterIds}/{@code characters} form an ordered roster (one entry
 * for 1v1, two for 2v2); {@link #primaryCharacterId()}/{@link #primaryCharacter()}
 * return the first entry for legacy single-fighter callers.
 */
public record AcceptedMatchParticipant(
    String playerId,
    String displayName,
    PlayerSide side,
    List<String> characterIds,
    List<Character> characters,
    List<List<String>> moveSetIds
) {
    public AcceptedMatchParticipant {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(side, "side");
        characterIds = characterIds == null ? List.of() : List.copyOf(characterIds);
        characters = characters == null ? List.of() : List.copyOf(characters);
        moveSetIds = moveSetIds == null ? List.of() : moveSetIds.stream()
            .map(ids -> ids == null ? List.<String>of() : List.copyOf(ids))
            .toList();
        if (characterIds.size() != characters.size()) {
            throw new IllegalArgumentException("character ids and definitions must align");
        }
        if (characterIds.size() != moveSetIds.size()) {
            throw new IllegalArgumentException("character ids and move sets must align");
        }
    }

    /** Roster constructor deriving each configured character's current move set. */
    public AcceptedMatchParticipant(
        String playerId,
        String displayName,
        PlayerSide side,
        List<String> characterIds,
        List<Character> characters
    ) {
        this(playerId, displayName, side, characterIds, characters,
            characters == null ? List.of() : characters.stream()
                .map(character -> character.getMoveSet().stream()
                    .map(com.jjktbf.model.move.Move::getId)
                    .toList())
                .toList());
    }

    /** Legacy single-fighter constructor. */
    public AcceptedMatchParticipant(
        String playerId,
        String displayName,
        PlayerSide side,
        String characterId,
        Character character
    ) {
        this(playerId, displayName, side,
            characterId == null ? List.of() : List.of(characterId),
            character == null ? List.of() : List.of(character));
    }

    /** The first (primary) fighter's canonical id. */
    public String primaryCharacterId() {
        return characterIds.get(0);
    }

    /** The first (primary) fighter. */
    public Character primaryCharacter() {
        return characters.get(0);
    }

    public boolean charactersSelected() {
        return !characterIds.isEmpty();
    }
}
