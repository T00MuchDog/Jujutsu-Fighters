package com.jjktbf.model.domain;

/** Authoritative reason an active Domain stopped existing. */
public enum DomainCollapseReason {
    DURATION_EXPIRED,
    OWNER_DEFEATED,
    OWNER_REMOVED,
    OWNER_DAMAGED,
    OWNER_LOW_HP,
    UPKEEP_FAILED,
    /** Lost a Domain clash: the opposing Domain fully took this one over. */
    CLASH_LOST,
    INTERNAL_BARRIER_BROKEN,
    COUNTER_EXHAUSTED,
    OWNER_ACTED,
    VOLUNTARY,
    BATTLE_ENDED
}
