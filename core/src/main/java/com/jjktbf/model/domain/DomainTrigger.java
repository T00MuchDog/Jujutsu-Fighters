package com.jjktbf.model.domain;

/** Runtime moment that activates an effect row belonging to a Domain channel. */
public enum DomainTrigger {
    ON_ESTABLISH,
    ON_MEMBER_ENTER,
    EACH_TICK,
    ON_ROUND_START,
    ON_ROUND_END,
    ON_COLLAPSE
}
