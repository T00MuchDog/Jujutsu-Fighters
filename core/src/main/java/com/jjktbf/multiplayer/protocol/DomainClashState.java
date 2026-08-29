package com.jjktbf.multiplayer.protocol;

/** Wire snapshot of one active continuous Domain clash. */
public record DomainClashState(
    String firstDomainInstanceId,
    String secondDomainInstanceId
) { }
