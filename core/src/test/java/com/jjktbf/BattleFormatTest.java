package com.jjktbf;

import com.jjktbf.model.combat.BattleFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the {@link BattleFormat} contract that setup flows (local character
 * select, and later the multiplayer challenge/match chain) rely on.
 */
class BattleFormatTest {

    @Test
    void oneVOneFieldsExactlyOneFighterPerSide() {
        assertEquals(1, BattleFormat.ONE_V_ONE.fightersPerSide());
    }

    @Test
    void twoVTwoFieldsExactlyTwoFightersPerSide() {
        assertEquals(2, BattleFormat.TWO_V_TWO.fightersPerSide());
    }

    @Test
    void sixVSixCarriesSixFightersButFieldsThree() {
        assertEquals(6, BattleFormat.SIX_V_SIX.fightersPerSide());
        assertEquals(3, BattleFormat.SIX_V_SIX.activeFightersPerSide());
        assertEquals(3, BattleFormat.SIX_V_SIX.reserveFightersPerSide());
        assertTrue(BattleFormat.SIX_V_SIX.hasReserves());
    }
}
