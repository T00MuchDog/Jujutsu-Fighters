package com.jjktbf.model.domain;

/** Authoritative reason an active Domain stopped existing. */
public enum DomainCollapseReason {
    DURATION_EXPIRED,
    OWNER_DEFEATED,
    OWNER_REMOVED,
    UPKEEP_FAILED,
    INTERNAL_BARRIER_BROKEN,
    EXTERNAL_BARRIER_BROKEN,
    COUNTER_EXHAUSTED,
    OWNER_ACTED,
    REPLACED,
    VOLUNTARY,
    BATTLE_ENDED
}
