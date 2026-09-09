package com.jjktbf.graphics.ui.battle;

import com.badlogic.gdx.math.Rectangle;
import com.jjktbf.graphics.ui.profile.BattleUiLayout;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.character.coded.MiraclesAbility;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningPanelLayoutTest {

    @Test
    void sharedUsesTheUnifiedBottomPlanningSection() {
        PlanningPanel.LayoutSnapshot snapshot = sharedPanel(2560f, 1440f).layoutSnapshot();
        Rectangle palette = snapshot.palette();
        Rectangle defense = snapshot.defensiveTimeline();
        Rectangle offense = snapshot.offensiveTimeline();

        assertBounds(new Rectangle(0f, 0f, 2560f, 537.96f), snapshot.section());
        assertEquals(new Rectangle(18f, 18f, 2524f, 296.96f), palette);
        assertEquals(12f, defense.y - palette.y - palette.height, 0.0001f);
        assertEquals(78f, defense.height, 0.0001f);
        assertEquals(78f, offense.height, 0.0001f);
        assertEquals(16f, offense.y - defense.y - defense.height, 0.0001f);
        assertEquals(172f, offense.y + offense.height - defense.y, 0.0001f);
        assertEquals(new Rectangle(470f, 326.96f, 1855f, 78f), defense);
        assertEquals(new Rectangle(470f, 420.96f, 1855f, 78f), offense);
        assertEquals(1397.5f, defense.x + defense.width / 2f, 0.0001f);
        assertEquals(1397.5f, offense.x + offense.width / 2f, 0.0001f);
        assertEquals(new Rectangle(72f, 326.96f, 186f, 172f), snapshot.lock());
        assertEquals(15f, snapshot.apStat().x - offense.x - offense.width, 0.0001f);
        assertEquals(15f, snapshot.ceStat().x - defense.x - defense.width, 0.0001f);
        assertEquals(0f, snapshot.miracles().width, 0.0001f);
        assertEquals(0f, snapshot.header().height, 0.0001f);

        assertEquals(6, snapshot.cards().size());
        for (Rectangle card : snapshot.cards()) {
            assertEquals(342.72f, card.width, 0.0001f);
            assertEquals(127f, card.height, 0.0001f);
            assertEquals(172.56f, card.y, 0.0001f);
        }
        assertEquals(new Rectangle(33f, 33f, 2494f, 127.56f),
            snapshot.moveDetail().bounds());
        assertEquals("LAYOUT_MOVE_0", snapshot.inspectedMoveId());
        assertEquals(new Rectangle(49f, 49f, 400f, 95.56f),
            snapshot.moveDetail().title());
        assertEquals(new Rectangle(465f, 49f, 500f, 95.56f),
            snapshot.moveDetail().values());
        assertEquals(new Rectangle(981f, 49f, 1530f, 95.56f),
            snapshot.moveDetail().description());
        assertEquals(357.72f,
            snapshot.cards().get(1).x - snapshot.cards().get(0).x, 0.0001f);
        assertEquals(0f, snapshot.paletteScrollMaximum(), 0.0001f);
    }

    @Test
    void sharedCompactViewportKeepsTheSameLogicalComposition() {
        PlanningPanel.LayoutSnapshot snapshot = sharedPanel(1280f, 720f).layoutSnapshot();

        assertBounds(new Rectangle(0f, 0f, 2560f, 537.96f), snapshot.section());
        assertEquals(new Rectangle(72f, 326.96f, 186f, 172f), snapshot.lock());
        assertEquals(342.72f, snapshot.cards().get(0).width, 0.0001f);
    }

    @Test
    void everyTimelineFillsTheTrackAndLowerTiersSpaceDotsFurtherApart() {
        PlanningPanel.LayoutSnapshot full = sharedPanel(300, 2560f, 1440f).layoutSnapshot();
        PlanningPanel.LayoutSnapshot shortGrid = sharedPanel(70, 2560f, 1440f).layoutSnapshot();
        Rectangle fullBar = full.offensiveTimeline();
        Rectangle shortBar = shortGrid.offensiveTimeline();

        assertEquals(fullBar, shortBar);
        assertTrue(shortBar.width / 70f > fullBar.width / 300f);
        assertEquals(15f, shortGrid.apStat().x - shortBar.x - shortBar.width, 0.0001f);
        assertEquals(15f,
            shortGrid.ceStat().x - shortGrid.defensiveTimeline().x
                - shortGrid.defensiveTimeline().width,
            0.0001f);
    }

    @ParameterizedTest
    @CsvSource({"2560,1440", "1920,1080", "1366,768", "1512,982", "2000,1243", "2560,1600", "3440,1440"})
    void allViewportsKeepSafeControlsAndExtendOnlyPlannerChrome(float width, float height) {
        PlanningPanel.LayoutSnapshot reference = sharedPanel(2560f, 1440f).layoutSnapshot();
        PlanningPanel panel = sharedPanel(width, height);
        panel.setStatusEffects(List.of());
        PlanningPanel.LayoutSnapshot snapshot = panel.layoutSnapshot();
        BattleCanvas canvas = BattleCanvas.fit(width, height);
        assertBounds(canvas.planningSurface(), snapshot.section());
        assertEquals(reference.palette(), snapshot.palette());
        assertEquals(reference.cards(), snapshot.cards());
        assertEquals(reference.offensiveTimeline(), snapshot.offensiveTimeline());
        assertEquals(reference.defensiveTimeline(), snapshot.defensiveTimeline());
        assertEquals(reference.lock(), snapshot.lock());
        assertEquals(reference.apStat(), snapshot.apStat());
        assertEquals(reference.ceStat(), snapshot.ceStat());

        panel.resize(2560f, 1440f);
        panel.resize(width, height);
        assertEquals(snapshot, panel.layoutSnapshot());
    }

    @ParameterizedTest
    @CsvSource({"7,0", "8,352.76", "12,1783.64"})
    void widerCardsKeepTheirGapAndScrollInsteadOfShrinking(int count, float overflow) {
        List<Move> moves = IntStream.range(0, count).mapToObj(PlanningPanelLayoutTest::move).toList();
        PlanningPanel panel = new PlanningPanel(
            300, moves, costs(moves), 150, 0, 100, null, null, 2560f, 1440f);
        var snapshot = panel.layoutSnapshot();
        assertEquals(overflow, snapshot.paletteScrollMaximum(), 0.001f);
        for (int i = 0; i < count; i++) {
            Rectangle card = snapshot.cards().get(i);
            assertEquals(342.72f, card.width, 0.001f);
            assertEquals(127f, card.height, 0.001f);
            assertTrue(card.y + card.height <= snapshot.paletteViewport().y + snapshot.paletteViewport().height);
            assertEquals(12f, card.y - snapshot.moveDetail().bounds().y
                - snapshot.moveDetail().bounds().height, 0.001f);
            if (i > 0) {
                Rectangle previous = snapshot.cards().get(i - 1);
                assertEquals(15f, card.x - previous.x - previous.width, 0.001f);
            }
        }
    }

    private static PlanningPanel sharedPanel(float width, float height) {
        return sharedPanel(300, width, height);
    }

    private static void assertBounds(Rectangle expected, Rectangle actual) {
        assertEquals(expected.x, actual.x, 0.001f);
        assertEquals(expected.y, actual.y, 0.001f);
        assertEquals(expected.width, actual.width, 0.001f);
        assertEquals(expected.height, actual.height, 0.001f);
    }

    private static PlanningPanel sharedPanel(int gridLength, float width, float height) {
        List<Move> moves = moves();
        Map<String, Integer> costs = costs(moves);
        CodedAbilityState miracles = new CodedAbilityState(
            MiraclesAbility.KEY, "Miracles", 6, 6, false);
        PlanningPanel panel = new PlanningPanel(
            gridLength, moves, costs, 150, 0, 100, miracles, null, width, height);
        panel.setActorName("Shared Layout Fighter");
        panel.setLayout(BattleUiLayout.defaults());
        return panel;
    }

    private static List<Move> moves() {
        return IntStream.range(0, 6)
            .mapToObj(PlanningPanelLayoutTest::move)
            .toList();
    }

    private static Map<String, Integer> costs(List<Move> moves) {
        return moves.stream().collect(Collectors.toMap(Move::getId, ignored -> 0));
    }

    private static Move move(int index) {
        MoveData data = new MoveData();
        data.id = "LAYOUT_MOVE_" + index;
        data.name = "Layout Move " + index;
        data.description = "A move used to verify planner layout bands.";
        data.tags = List.of("ATTACK", "PHYSICAL");
        data.apCost = 10;
        data.unleashPoint = 1;
        return data.toMove();
    }
}
