package com.jjktbf.graphics.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleScreenPlanningCountdownTest {

    @Test
    void formatsPlanningTimeAsAStableMinuteSecondCountdown() {
        assertEquals("1:30", BattleScreen.formatPlanningCountdown(90_000L));
        assertEquals("1:30", BattleScreen.formatPlanningCountdown(89_999L));
        assertEquals("1:00", BattleScreen.formatPlanningCountdown(60_000L));
        assertEquals("1:00", BattleScreen.formatPlanningCountdown(59_001L));
        assertEquals("0:59", BattleScreen.formatPlanningCountdown(59_000L));
        assertEquals("0:01", BattleScreen.formatPlanningCountdown(1L));
        assertEquals("0:00", BattleScreen.formatPlanningCountdown(0L));
    }

    @Test
    void autoLockUsesASmallNetworkSafetyWindow() {
        assertFalse(BattleScreen.shouldAutoLockPlanning(251L));
        assertTrue(BattleScreen.shouldAutoLockPlanning(250L));
        assertTrue(BattleScreen.shouldAutoLockPlanning(0L));
        assertFalse(BattleScreen.shouldAutoLockPlanning(-1L));
    }
}
