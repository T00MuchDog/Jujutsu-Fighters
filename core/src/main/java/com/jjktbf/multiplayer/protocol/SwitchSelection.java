package com.jjktbf.multiplayer.protocol;

import java.util.Objects;

/** Player intent to spend one active fighter's round entering a reserve. */
public record SwitchSelection(String actorId, String reserveId) {
    public SwitchSelection {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(reserveId, "reserveId");
    }
}
