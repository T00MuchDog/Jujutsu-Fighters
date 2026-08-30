package com.jjktbf.multiplayer.protocol;

import java.util.List;

/** Typed payload for {@link CommandType#SUBMIT_PLAN}. */
public record SubmitPlanPayload(
    List<PlanPlacement> placements,
    List<SwitchSelection> switches
) {
    public SubmitPlanPayload {
        placements = placements == null ? List.of() : List.copyOf(placements);
        switches = switches == null ? List.of() : List.copyOf(switches);
    }

    public SubmitPlanPayload(List<PlanPlacement> placements) {
        this(placements, List.of());
    }
}
