package com.jjktbf.multiplayer.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Wire snapshot of one active battle-scoped Domain or anti-Domain. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainState(
    String instanceId,
    String domainId,
    String name,
    String ownerInstanceId,
    boolean antiDomain,
    String topology,
    String counterType,
    List<String> selectedTargetInstanceIds,
    List<String> memberInstanceIds,
    List<String> protectedInstanceIds,
    int remainingRounds,
    int remainingTicks,
    int internalBarrierIntegrity,
    int clashValue,
    int remainingCounterUses
) {
    public DomainState {
        selectedTargetInstanceIds = immutable(selectedTargetInstanceIds);
        memberInstanceIds = immutable(memberInstanceIds);
        protectedInstanceIds = immutable(protectedInstanceIds);
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
