package com.jjktbf.multiplayer.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Persistent wire view of the authoritative battle-wide Domain subsystem. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainBattlefieldState(
    List<DomainState> activeDomains,
    List<DomainClashState> clashes
) {
    public static final DomainBattlefieldState EMPTY =
        new DomainBattlefieldState(List.of(), List.of());

    public DomainBattlefieldState {
        activeDomains = activeDomains == null ? List.of() : List.copyOf(activeDomains);
        clashes = clashes == null ? List.of() : List.copyOf(clashes);
    }
}
