package com.jjktbf.model.domain;

/** Which combatants become members when a Domain is established. */
public enum DomainCapturePolicy {
    NONE,
    ALL_ACTIVE,
    ALL_ENEMIES,
    SELECTED_TARGETS,
    OWNER_AND_SELECTED
}
