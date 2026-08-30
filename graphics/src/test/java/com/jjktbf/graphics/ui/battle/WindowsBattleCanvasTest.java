package com.jjktbf.graphics.ui.battle;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowsBattleCanvasTest {

    @Test
    void referenceViewportUsesOneToOneCoordinates() {
        WindowsBattleCanvas canvas = WindowsBattleCanvas.fit(2560f, 1440f);

        assertEquals(1f, canvas.scale(), 0.0001f);
        assertEquals(0f, canvas.offsetX(), 0.0001f);
        assertEquals(0f, canvas.offsetY(WindowsBattleCanvas.Anchor.BOTTOM), 0.0001f);
        assertEquals(0f, canvas.offsetY(WindowsBattleCanvas.Anchor.TOP), 0.0001f);
        assertEquals(524f, canvas.logicalX(524f), 0.0001f);
        assertEquals(458f,
            canvas.logicalY(458f, WindowsBattleCanvas.Anchor.BOTTOM), 0.0001f);
    }

    @Test
    void sixteenByNineViewportScalesTheWholeCompositionUniformly() {
        WindowsBattleCanvas canvas = WindowsBattleCanvas.fit(1600f, 900f);
        Rectangle physical = canvas.physicalBounds(
            new Rectangle(581f, 537.96f, 1979f, 902.04f),
            WindowsBattleCanvas.Anchor.TOP);

        assertEquals(0.625f, canvas.scale(), 0.0001f);
        assertEquals(363.125f, physical.x, 0.0001f);
        assertEquals(336.225f, physical.y, 0.0001f);
        assertEquals(1236.875f, physical.width, 0.0001f);
        assertEquals(563.775f, physical.height, 0.0001f);
        assertEquals(581f, canvas.logicalX(physical.x), 0.0001f);
        assertEquals(537.96f,
            canvas.logicalY(physical.y, WindowsBattleCanvas.Anchor.TOP), 0.0001f);
    }

    @Test
    void tallViewportCentersTheWholeCompositionWithOneUniformTransform() {
        WindowsBattleCanvas canvas = WindowsBattleCanvas.fit(2560f, 1600f);
        Rectangle logical = new Rectangle(
            0f, 0f, 2560f, WindowsBattleCanvas.BOTTOM_SECTION_HEIGHT);

        assertEquals(80f, canvas.offsetY(WindowsBattleCanvas.Anchor.BOTTOM), 0.0001f);
        assertEquals(80f, canvas.offsetY(WindowsBattleCanvas.Anchor.TOP), 0.0001f);
        assertEquals(80f,
            canvas.physicalBounds(logical, WindowsBattleCanvas.Anchor.BOTTOM).y, 0.0001f);
        assertEquals(80f,
            canvas.physicalBounds(logical, WindowsBattleCanvas.Anchor.TOP).y, 0.0001f);
        assertEquals(250f, canvas.logicalX(250f), 0.0001f);
        assertEquals(380f,
            canvas.logicalY(460f, WindowsBattleCanvas.Anchor.TOP), 0.0001f);
    }

    @Test
    void tallViewportKeepsSharedActionButtonInTheCenteredCanvas() {
        WindowsBattleCanvas canvas = WindowsBattleCanvas.fit(2560f, 1600f);
        Rectangle action = new Rectangle(
            WindowsBattleCanvas.ACTION_X,
            WindowsBattleCanvas.ACTION_Y,
            WindowsBattleCanvas.ACTION_WIDTH,
            WindowsBattleCanvas.ACTION_HEIGHT);

        Rectangle bottomAnchored = canvas.physicalBounds(
            action, WindowsBattleCanvas.Anchor.BOTTOM);
        Rectangle topAnchored = canvas.physicalBounds(
            action, WindowsBattleCanvas.Anchor.TOP);

        assertEquals(406.96f, bottomAnchored.y, 0.0001f);
        assertEquals(406.96f, topAnchored.y, 0.0001f);
        assertEquals(380f,
            canvas.logicalY(460f, WindowsBattleCanvas.Anchor.BOTTOM), 0.0001f);
    }
}
