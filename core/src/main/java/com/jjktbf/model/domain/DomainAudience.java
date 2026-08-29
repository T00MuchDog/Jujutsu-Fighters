package com.jjktbf.model.domain;

/** Domain-relative audience resolved before a leaf effect is applied. */
public enum DomainAudience {
    OWNER,
    ENTERING_MEMBER,
    ENEMY_MEMBERS,
    ALLY_MEMBERS,
    ALL_MEMBERS,
    UNPROTECTED_MEMBERS,
    BARRIER,
    EXTERIOR_ENEMIES
}
