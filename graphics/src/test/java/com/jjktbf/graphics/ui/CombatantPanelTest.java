package com.jjktbf.graphics.ui;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatantPanelTest {

    @Test
    void transitionsIntoAndOutOfTemporarySizes() {
        CombatantPanel panel = new CombatantPanel(
            null, null, null, new Rectangle(), new Rectangle(), new Rectangle(), 1f, false);

        panel.setSizeMultiplier(0.38);
        panel.updateSizeMultiplier(0.42f);
        assertEquals(0.38f, panel.sizeMultiplier(), 0.0001f);

        panel.setSizeMultiplier(1.0);
        panel.updateSizeMultiplier(0.1f);
        panel.snapAnimations();
        assertEquals(1f, panel.sizeMultiplier(), 0.0001f);
    }
}
