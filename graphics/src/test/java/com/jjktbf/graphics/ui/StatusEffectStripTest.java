package com.jjktbf.graphics.ui;

import com.jjktbf.model.move.StatusEffectType;
import com.jjktbf.multiplayer.protocol.StatusEffectState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusEffectStripTest {

    @Test
    void compactDurationAlwaysListsTheRemainingUnit() {
        assertEquals("PERM", StatusEffectStrip.durationText(-1, 0));
        assertEquals("2R", StatusEffectStrip.durationText(2, 0));
        assertEquals("7T", StatusEffectStrip.durationText(0, 7));
        assertEquals("2R 7T", StatusEffectStrip.durationText(2, 7));
    }

    @Test
    void inspectionDurationUsesReadableRoundAndApTickNames() {
        assertEquals("Permanent", StatusEffectStrip.remainingText(-1, 0));
        assertEquals("1 round", StatusEffectStrip.remainingText(1, 0));
        assertEquals("4 AP ticks", StatusEffectStrip.remainingText(0, 4));
        assertEquals("2 rounds + 1 AP tick", StatusEffectStrip.remainingText(2, 1));
    }

    @Test
    void badgesIdentifyStatusWithoutDependingOnColor() {
        assertEquals("SPD+", StatusEffectStrip.shortLabel(
            StatusEffectType.SPEED_INCREASE, "ignored"));
        assertEquals("FRZ", StatusEffectStrip.shortLabel(
            StatusEffectType.FROZEN, "ignored"));
        assertEquals("MYSTER", StatusEffectStrip.shortLabel(null, "Mystery Status"));
    }

    @Test
    void inspectionExplainsEffectsAndActionRestrictions() {
        assertEquals("Speed +12.", StatusEffectStrip.effectText(
            StatusEffectType.SPEED_INCREASE, 12.0));
        assertEquals("Halves Defense and interrupts non-fire actions.",
            StatusEffectStrip.effectText(StatusEffectType.FROZEN, 0.0));
        assertEquals("Only fire actions can proceed while Frozen.",
            StatusEffectStrip.restrictionText(StatusEffectType.FROZEN));
        assertEquals("Cannot plan or execute actions.",
            StatusEffectStrip.restrictionText(StatusEffectType.SLEEP));
    }

    @Test
    void onlineSnapshotsPopulateTheSameStatusStrip() {
        StatusEffectStrip strip = new StatusEffectStrip();
        strip.setEffects(List.of(
            new StatusEffectState("FROZEN", "Frozen", -1, 0, 0.0),
            new StatusEffectState("STAGGER", "Stagger", 0, 3, 0.0)));

        assertEquals(2, strip.size());
    }

    @Test
    void identicalStatusesCollapseIntoOneStackButDifferentDurationsRemainVisible() {
        StatusEffectStrip strip = new StatusEffectStrip();
        strip.setEffects(List.of(
            new StatusEffectState("STAGGER", "Stagger", 0, 3, 0.0),
            new StatusEffectState("STAGGER", "Stagger", 0, 3, 0.0),
            new StatusEffectState("STAGGER", "Stagger", 0, 1, 0.0)));

        assertEquals(2, strip.size());
    }
}
