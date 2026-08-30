package com.jjktbf.model.character.coded;

/**
 * Immutable state exposed by a compiled ability runtime. States with a
 * non-positive maximum are capability markers and are not rendered as meters.
 */
public record CodedAbilityState(
    String key,
    String displayName,
    int currentValue,
    int maximumValue
) {
}
