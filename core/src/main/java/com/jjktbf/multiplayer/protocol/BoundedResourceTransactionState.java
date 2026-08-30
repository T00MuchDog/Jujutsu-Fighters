package com.jjktbf.multiplayer.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Wire-visible guaranteed move-start resource transaction used during planning. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BoundedResourceTransactionState(
    String sourceResourceKey,
    int sourceResourceAmount,
    String targetResourceKey,
    int targetResourceAmount
) { }
