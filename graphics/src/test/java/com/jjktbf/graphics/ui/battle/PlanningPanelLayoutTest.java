package com.jjktbf.graphics.ui.battle;

import com.badlogic.gdx.math.Rectangle;
import com.jjktbf.graphics.ui.profile.BattleUiLayout;
import com.jjktbf.graphics.ui.profile.UiProfile;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.character.coded.MiraclesAbility;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningPanelLayoutTest {

    @Test
    void windowsUsesTheUnifiedBottomPlanningSection() {
        PlanningPanel.LayoutSnapshot snapshot = windowsPanel(2560f, 1440f).layoutSnapshot();
        Rectangle palette = snapshot.palette();
        Rectangle defense = snapshot.defensiveTimeline();
        Rectangle offense = snapshot.offensiveTimeline();

        assertTrue(snapshot.unifiedWindows());
        assertEquals(new Rectangle(0f, 0f, 2560f, 537.96f), snapshot.section());
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
            assertEquals(285.6f, card.width, 0.0001f);
            assertEquals(266.56f, card.height, 0.0001f);
            assertEquals(33f, card.y, 0.0001f);
        }
        assertEquals(300.6f,
            snapshot.cards().get(1).x - snapshot.cards().get(0).x, 0.0001f);
        assertEquals(0f, snapshot.paletteScrollMaximum(), 0.0001f);
    }

    @Test
    void windowsCompactViewportKeepsTheSameLogicalComposition() {
        PlanningPanel.LayoutSnapshot snapshot = windowsPanel(1280f, 720f).layoutSnapshot();

        assertTrue(snapshot.unifiedWindows());
        assertFalse(snapshot.shortViewport());
        assertEquals(new Rectangle(0f, 0f, 2560f, 537.96f), snapshot.section());
        assertEquals(new Rectangle(72f, 326.96f, 186f, 172f), snapshot.lock());
        assertEquals(285.6f, snapshot.cards().get(0).width, 0.0001f);
    }

    @Test
    void everyWindowsTimelineFillsTheTrackAndLowerTiersSpaceDotsFurtherApart() {
        PlanningPanel.LayoutSnapshot full = windowsPanel(300, 2560f, 1440f).layoutSnapshot();
        PlanningPanel.LayoutSnapshot shortGrid = windowsPanel(70, 2560f, 1440f).layoutSnapshot();
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

    @Test
    void macRetainsTheDedicatedFullScreenPlanner() {
        List<Move> moves = moves();
        Map<String, Integer> costs = costs(moves);
        PlanningPanel panel = new PlanningPanel(
            300, moves, costs, 150, 0, 100, null, null, 1512f, 982f);
        panel.setActorName("Mac Layout Fighter");
        panel.setLayout(BattleUiLayout.defaults(UiProfile.MAC));

        PlanningPanel.LayoutSnapshot snapshot = panel.layoutSnapshot();
        assertFalse(snapshot.unifiedWindows());
        assertTrue(snapshot.palette().y + snapshot.palette().height
            <= snapshot.defensiveTimeline().y);
        assertTrue(snapshot.defensiveTimeline().y + snapshot.defensiveTimeline().height
            <= snapshot.offensiveTimeline().y);
        assertTrue(snapshot.offensiveTimeline().y + snapshot.offensiveTimeline().height
            <= snapshot.header().y);
    }

    private static PlanningPanel windowsPanel(float width, float height) {
        return windowsPanel(300, width, height);
    }

    private static PlanningPanel windowsPanel(int gridLength, float width, float height) {
        List<Move> moves = moves();
        Map<String, Integer> costs = costs(moves);
        CodedAbilityState miracles = new CodedAbilityState(
            MiraclesAbility.KEY, "Miracles", 6, 6);
        PlanningPanel panel = new PlanningPanel(
            gridLength, moves, costs, 150, 0, 100, miracles, null, width, height);
        panel.setActorName("Windows Layout Fighter");
        panel.setLayout(BattleUiLayout.defaults(UiProfile.WINDOWS));
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
