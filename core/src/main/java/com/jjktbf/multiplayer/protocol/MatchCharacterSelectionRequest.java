package com.jjktbf.multiplayer.protocol;

import java.util.List;

/** Ordered canonical fighter roster and per-fighter move sets selected for a match. */
public record MatchCharacterSelectionRequest(
    List<String> characterIds,
    List<List<String>> moveSetIds
) {
    public MatchCharacterSelectionRequest {
        characterIds = characterIds == null ? List.of() : List.copyOf(characterIds);
        moveSetIds = moveSetIds == null ? List.of() : moveSetIds.stream()
            .map(ids -> ids == null ? List.<String>of() : List.copyOf(ids))
            .toList();
    }

    /** Legacy/default selection: the server uses each canonical default move set. */
    public MatchCharacterSelectionRequest(List<String> characterIds) {
        this(characterIds, List.of());
    }
}
