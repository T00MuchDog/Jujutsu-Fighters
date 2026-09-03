package com.jjktbf.multiplayer.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wire snapshot of one active Domain clash. When a side is stronger,
 * {@code leaderDomainInstanceId} names it and {@code takeoverProgress}
 * reports the fraction of integrity lost by the weaker Domain (0 to 1).
 * The progress field name is retained for protocol compatibility.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainClashState(
    String firstDomainInstanceId,
    String secondDomainInstanceId,
    String leaderDomainInstanceId,
    double takeoverProgress
) {
    public DomainClashState(
        String firstDomainInstanceId,
        String secondDomainInstanceId
    ) {
        this(firstDomainInstanceId, secondDomainInstanceId, null, 0.0);
    }
}
