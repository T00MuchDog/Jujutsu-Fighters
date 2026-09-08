package com.jjktbf.graphics.ui.battle;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleCanvasTest {

    @Test
    void referenceViewportUsesOneToOneCoordinates() {
        BattleCanvas canvas = BattleCanvas.fit(2560f, 1440f);

        assertEquals(1f, canvas.scale(), 0.0001f);
        assertEquals(0f, canvas.offsetX(), 0.0001f);
        assertEquals(0f, canvas.offsetY(BattleCanvas.Anchor.BOTTOM), 0.0001f);
        assertEquals(0f, canvas.offsetY(BattleCanvas.Anchor.TOP), 0.0001f);
        assertEquals(524f, canvas.logicalX(524f), 0.0001f);
        assertEquals(458f,
            canvas.logicalY(458f, BattleCanvas.Anchor.BOTTOM), 0.0001f);
    }

    @Test
    void sixteenByNineViewportScalesTheWholeCompositionUniformly() {
        BattleCanvas canvas = BattleCanvas.fit(1600f, 900f);
        Rectangle physical = canvas.physicalBounds(
            new Rectangle(581f, 537.96f, 1979f, 902.04f),
            BattleCanvas.Anchor.TOP);

        assertEquals(0.625f, canvas.scale(), 0.0001f);
        assertEquals(363.125f, physical.x, 0.0001f);
        assertEquals(336.225f, physical.y, 0.0001f);
        assertEquals(1236.875f, physical.width, 0.0001f);
        assertEquals(563.775f, physical.height, 0.0001f);
        assertEquals(581f, canvas.logicalX(physical.x), 0.0001f);
        assertEquals(537.96f,
            canvas.logicalY(physical.y, BattleCanvas.Anchor.TOP), 0.0001f);
    }

    @Test
    void tallViewportKeepsBattlefieldAndPlannerOnTheSameAnchor() {
        BattleCanvas canvas = BattleCanvas.fit(2560f, 1600f);
        Rectangle logical = new Rectangle(
            0f, 0f, 2560f, BattleCanvas.BOTTOM_SECTION_HEIGHT);

        assertEquals(0f, canvas.offsetY(BattleCanvas.Anchor.BOTTOM), 0.0001f);
        assertEquals(0f, canvas.offsetY(BattleCanvas.Anchor.TOP), 0.0001f);
        assertEquals(0f,
            canvas.physicalBounds(logical, BattleCanvas.Anchor.BOTTOM).y, 0.0001f);
        assertEquals(0f,
            canvas.physicalBounds(logical, BattleCanvas.Anchor.TOP).y, 0.0001f);
        assertEquals(250f, canvas.logicalX(250f), 0.0001f);
        assertEquals(460f,
            canvas.logicalY(460f, BattleCanvas.Anchor.TOP), 0.0001f);
    }

    @Test
    void tallViewportKeepsSharedActionButtonBottomAnchored() {
        BattleCanvas canvas = BattleCanvas.fit(2560f, 1600f);
        Rectangle action = new Rectangle(
            BattleCanvas.ACTION_X,
            BattleCanvas.ACTION_Y,
            BattleCanvas.ACTION_WIDTH,
            BattleCanvas.ACTION_HEIGHT);

        Rectangle bottomAnchored = canvas.physicalBounds(
            action, BattleCanvas.Anchor.BOTTOM);
        Rectangle topAnchored = canvas.physicalBounds(
            action, BattleCanvas.Anchor.TOP);

        assertEquals(326.96f, bottomAnchored.y, 0.0001f);
        assertEquals(bottomAnchored.y, topAnchored.y, 0.0001f);
        assertEquals(460f,
            canvas.logicalY(460f, BattleCanvas.Anchor.BOTTOM), 0.0001f);
    }

    @ParameterizedTest
    @CsvSource({"2560,1440", "1920,1080", "1366,768", "1512,982", "2000,1243", "2560,1600", "3440,1440"})
    void surfacesTileViewportAndBothAnchorsRoundTrip(float width, float height) {
        BattleCanvas canvas = BattleCanvas.fit(width, height);
        float scale = Math.min(width / 2560f, height / 1440f);
        assertEquals(scale, canvas.scale(), 0.0001f);
        assertEquals((width - 2560f * scale) / 2f, canvas.offsetX(), 0.0001f);
        assertEquals(0f, canvas.offsetY(BattleCanvas.Anchor.BOTTOM), 0.0001f);
        assertEquals(0f,
            canvas.offsetY(BattleCanvas.Anchor.TOP), 0.0001f);

        Rectangle planner = canvas.physicalBounds(canvas.planningSurface(), BattleCanvas.Anchor.BOTTOM);
        Rectangle log = canvas.physicalBounds(canvas.logSurface(), BattleCanvas.Anchor.TOP);
        Rectangle execution = canvas.physicalBounds(canvas.executionSurface(), BattleCanvas.Anchor.TOP);
        assertEquals(0f, planner.x, 0.001f);
        assertEquals(0f, planner.y, 0.001f);
        assertEquals(width, planner.width, 0.001f);
        assertEquals(0f, log.x, 0.001f);
        assertEquals(planner.height, log.y, 0.001f);
        assertEquals(planner.height, execution.y, 0.001f);
        assertEquals(log.x + log.width, execution.x, 0.001f);
        assertEquals(height, log.y + log.height, 0.001f);
        assertEquals(height, execution.y + execution.height, 0.001f);
        assertEquals(width, execution.x + execution.width, 0.001f);
        assertEquals(width * height,
            planner.area() + log.area() + execution.area(), 1f);

        Rectangle safeField = new Rectangle(581f, 537.96f, 1979f, 902.04f);
        Rectangle surface = canvas.executionSurface();
        assertTrue(surface.y <= safeField.y + 0.001f);
        assertTrue(surface.y + surface.height >= safeField.y + safeField.height - 0.001f);
        for (BattleCanvas.Anchor anchor : BattleCanvas.Anchor.values()) {
            for (Rectangle logical : new Rectangle[] {
                safeField, new Rectangle(72f, 326.96f, 186f, 172f),
                canvas.planningSurface(), canvas.logSurface(), canvas.executionSurface()
            }) {
                Rectangle physical = canvas.physicalBounds(logical, anchor);
                assertEquals(logical.x, canvas.logicalX(physical.x), 0.001f);
                assertEquals(logical.y, canvas.logicalY(physical.y, anchor), 0.001f);
                assertEquals(logical.width, physical.width / scale, 0.001f);
                assertEquals(logical.height, physical.height / scale, 0.001f);
                assertEquals(logical.x + logical.width,
                    canvas.logicalX(physical.x + physical.width), 0.001f);
                assertEquals(logical.y + logical.height,
                    canvas.logicalY(physical.y + physical.height, anchor), 0.001f);
            }
        }
    }
}
