package com.jjktbf.graphics.screens;

import com.badlogic.gdx.math.Rectangle;
import com.jjktbf.graphics.BattleSpriteScaleConfig;
import com.jjktbf.graphics.ui.CombatantPanel;
import com.jjktbf.graphics.ui.battle.WindowsBattleCanvas;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleScreenSpriteBoundsTest {

    @Test
    void windowsPartitionsLogExecutionAndPlannerAtTheAnnotatedEdges() {
        assertEquals(new Rectangle(581f, 537.96f, 1979f, 902.04f),
            BattleScreen.windowsExecutionBounds());
        assertEquals(new Rectangle(0f, 537.96f, 581f, 902.04f),
            BattleScreen.windowsLogBounds());
        assertEquals(new Rectangle(72f, 326.96f, 186f, 172f),
            BattleScreen.windowsActionBounds());
        assertEquals(537.96f, BattleScreen.WINDOWS_BOTTOM_SECTION_HEIGHT, 0.0001f);
    }

    @Test
    void windowsFightersAndBaseplatesAreTwentyFivePercentLarger() {
        BattleScreen.WindowsExecutionGeometry geometry = BattleScreen.windowsExecutionGeometry(
            1, 1);

        assertEquals(448f, geometry.spriteSize(), 0.0001f);
        assertEquals(537.96f, geometry.playerSpriteY(), 0.0001f);
        assertEquals(944.96f, geometry.enemySpriteY(), 0.0001f);
        assertEquals(896f, geometry.playerPlate().width, 0.0001f);
        assertEquals(896f, geometry.enemyPlate().width, 0.0001f);
        assertEquals(89.96f, geometry.playerPlate().y, 0.0001f);
        assertEquals(577.04f, geometry.enemyPlate().y, 0.0001f);
        assertEquals(BattleScreen.WINDOWS_BOTTOM_SECTION_HEIGHT,
            geometry.playerPlate().y + geometry.playerPlate().height / 2f, 0.0001f);
        assertEquals(BattleScreen.WINDOWS_ENEMY_FIGHTER_BOTTOM_Y + 98f,
            geometry.enemyPlate().y + geometry.enemyPlate().height * 0.52f, 0.0001f);
        assertEquals(new Rectangle(693f, 1155.1162f, 620.33f, 136.6875f), geometry.enemyHud());
        assertEquals(new Rectangle(1803.275f, 666.1163f, 644.725f, 136.6875f),
            geometry.playerHud());
        assertEquals(112f, WindowsBattleCanvas.WIDTH
            - geometry.playerHud().x - geometry.playerHud().width, 0.0001f);
        assertEquals(112f, geometry.enemyHud().x - BattleScreen.WINDOWS_EXECUTION_X, 0.0001f);
    }

    @Test
    void windowsTeamPlatesAndFormationOffsetsUseTheAuthoredSizes() {
        BattleScreen.WindowsExecutionGeometry one = BattleScreen.windowsExecutionGeometry(1, 1);
        BattleScreen.WindowsExecutionGeometry two = BattleScreen.windowsExecutionGeometry(2, 2);
        BattleScreen.WindowsExecutionGeometry three = BattleScreen.windowsExecutionGeometry(3, 3);
        BattleScreen.WindowsExecutionGeometry four = BattleScreen.windowsExecutionGeometry(4, 4);

        assertEquals(896f, two.enemyPlate().width, 0.0001f);
        assertEquals(896f, two.playerPlate().width, 0.0001f);
        assertEquals(1070f, three.enemyPlate().width, 0.0001f);
        assertEquals(1286f, three.playerPlate().width, 0.0001f);
        assertEquals(1490f, three.enemyPlate().x, 0.0001f);
        assertEquals(WindowsBattleCanvas.WIDTH,
            three.enemyPlate().x + three.enemyPlate().width, 0.0001f);
        assertEquals(610f, three.playerPlate().x, 0.0001f);
        assertEquals(one.enemyPlate().width * 2f, four.enemyPlate().width, 0.0001f);
        assertEquals(one.playerPlate().width * 2f, four.playerPlate().width, 0.0001f);

        assertEquals(-152.32f,
            BattleScreen.fighterOffset(0, 2, two.playerPlate().width, false), 0.0001f);
        assertEquals(1612f,
            BattleScreen.windowsCombatantCenterX(
                1, 3, three.playerPlate(), false), 0.0001f);
        assertEquals(-456.96f,
            BattleScreen.fighterOffset(2, 4, four.playerPlate().width, false), 0.0001f);
        assertEquals(456.96f,
            BattleScreen.fighterOffset(3, 4, four.enemyPlate().width, true), 0.0001f);
    }

    @Test
    void windowsLargeHudMatchesTheCompactPairAcrossFormats() {
        BattleScreen.WindowsExecutionGeometry geometry = BattleScreen.windowsExecutionGeometry(
            3, 3);
        BattleScreen.WindowsExecutionGeometry one = BattleScreen.windowsExecutionGeometry(1, 1);
        BattleScreen.WindowsExecutionGeometry two = BattleScreen.windowsExecutionGeometry(2, 2);

        assertEquals(302.6f, geometry.enemyHud().width, 0.0001f);
        assertEquals(314.5f, geometry.playerHud().width, 0.0001f);
        assertEquals(15.13f, BattleScreen.windowsHudColumnGap(3, true), 0.0001f);
        assertEquals(15.725f, BattleScreen.windowsHudColumnGap(3, false), 0.0001f);
        assertEquals(9.568125f, BattleScreen.windowsHudRowGap(), 0.0001f);
        assertEquals(593f, geometry.enemyHud().x, 0.0001f);
        assertEquals(1909.275f, geometry.playerHud().x, 0.001f);
        assertEquals(1081.9884f, geometry.enemyHud().y, 0.001f);
        assertEquals(739.2441f, geometry.playerHud().y, 0.001f);

        Rectangle lonePlayerHud = BattleScreen.combatantHudBounds(
            1, 3, geometry.playerHud(), 644.725f,
            BattleScreen.windowsHudColumnGap(3, false),
            BattleScreen.windowsHudRowGap(), false);
        assertEquals(644.725f, lonePlayerHud.width, 0.0001f);
        assertEquals(geometry.playerHud().x, lonePlayerHud.x, 0.001f);
        assertEquals(
            geometry.playerHud().width * 2f + BattleScreen.windowsHudColumnGap(3, false),
            lonePlayerHud.width,
            0.001f);

        Rectangle loneEnemyHud = BattleScreen.combatantHudBounds(
            1, 3, geometry.enemyHud(), 620.33f,
            BattleScreen.windowsHudColumnGap(3, true),
            BattleScreen.windowsHudRowGap(), true);
        assertEquals(620.33f, loneEnemyHud.width, 0.0001f);
        assertEquals(geometry.enemyHud().x, loneEnemyHud.x, 0.001f);
        assertEquals(
            geometry.enemyHud().width * 2f + BattleScreen.windowsHudColumnGap(3, true),
            one.enemyHud().width,
            0.001f);
        assertEquals(
            geometry.playerHud().width * 2f + BattleScreen.windowsHudColumnGap(3, false),
            one.playerHud().width,
            0.001f);
        assertEquals(one.enemyHud().width, two.enemyHud().width, 0.001f);
        assertEquals(one.playerHud().width, two.playerHud().width, 0.001f);
    }

    @Test
    void windowsTeamSpritesReuseTheirSingleFighterRenderedScale() {
        BattleScreen.WindowsExecutionGeometry four = BattleScreen.windowsExecutionGeometry(4, 4);
        float enemyCenterX = BattleScreen.windowsCombatantCenterX(
            3, 4, four.enemyPlate(), true);
        float playerCenterX = BattleScreen.windowsCombatantCenterX(
            3, 4, four.playerPlate(), false);

        Rectangle singleEnemy = BattleScreen.windowsScaledSpriteBounds(
            BattleScreen.WINDOWS_ENEMY_FIGHTER_CENTER_X,
            BattleScreen.WINDOWS_ENEMY_FIGHTER_BOTTOM_Y,
            four.spriteSize(), 1.5f, true);
        Rectangle teamEnemy = BattleScreen.windowsScaledSpriteBounds(
            enemyCenterX, four.enemySpriteY(), four.spriteSize(), 1.5f, true);
        Rectangle singlePlayer = BattleScreen.windowsScaledSpriteBounds(
            BattleScreen.WINDOWS_PLAYER_FIGHTER_CENTER_X,
            BattleScreen.WINDOWS_PLAYER_FIGHTER_BOTTOM_Y,
            four.spriteSize(), 1.5f, false);
        Rectangle teamPlayer = BattleScreen.windowsScaledSpriteBounds(
            playerCenterX, four.playerSpriteY(), four.spriteSize(), 1.5f, false);

        assertEquals(448f, singleEnemy.width, 0.0001f);
        assertEquals(singleEnemy.width, teamEnemy.width, 0.0001f);
        assertEquals(672f, singlePlayer.width, 0.0001f);
        assertEquals(singlePlayer.width, teamPlayer.width, 0.0001f);
        assertEquals(enemyCenterX, teamEnemy.x + teamEnemy.width / 2f, 0.0001f);
        assertEquals(playerCenterX, teamPlayer.x + teamPlayer.width / 2f, 0.0001f);
    }

    @Test
    void windowsThreeFighterPlatesAndSpritesFollowTheAnnotatedMarkers() {
        BattleScreen.WindowsExecutionGeometry threeVsTwo =
            BattleScreen.windowsExecutionGeometry(2, 3);
        BattleScreen.WindowsExecutionGeometry threeVsThree =
            BattleScreen.windowsExecutionGeometry(3, 3);

        assertEquals(610f, threeVsTwo.playerPlate().x, 0.0001f);
        assertEquals(1286f, threeVsTwo.playerPlate().width, 0.0001f);
        assertEquals(threeVsTwo.playerPlate(), threeVsThree.playerPlate());
        assertEquals(1211f, BattleScreen.windowsCombatantCenterX(
            0, 3, threeVsThree.playerPlate(), false), 0.0001f);
        assertEquals(1612f, BattleScreen.windowsCombatantCenterX(
            1, 3, threeVsThree.playerPlate(), false), 0.0001f);
        assertEquals(882f, BattleScreen.windowsCombatantCenterX(
            2, 3, threeVsThree.playerPlate(), false), 0.0001f);

        assertEquals(1490f, threeVsThree.enemyPlate().x, 0.0001f);
        assertEquals(1070f, threeVsThree.enemyPlate().width, 0.0001f);
        assertEquals(WindowsBattleCanvas.WIDTH,
            threeVsThree.enemyPlate().x + threeVsThree.enemyPlate().width, 0.0001f);
        assertEquals(2023f, BattleScreen.windowsCombatantCenterX(
            0, 3, threeVsThree.enemyPlate(), true), 0.0001f);
        assertEquals(2360f, BattleScreen.windowsCombatantCenterX(
            1, 3, threeVsThree.enemyPlate(), true), 0.0001f);
        assertEquals(1695f, BattleScreen.windowsCombatantCenterX(
            2, 3, threeVsThree.enemyPlate(), true), 0.0001f);
    }

    @Test
    void windowsEnemyPlatesShareTheSameVerticalCenterAndFooting() {
        BattleScreen.WindowsExecutionGeometry one = BattleScreen.windowsExecutionGeometry(1, 1);
        BattleScreen.WindowsExecutionGeometry two = BattleScreen.windowsExecutionGeometry(2, 2);
        BattleScreen.WindowsExecutionGeometry three = BattleScreen.windowsExecutionGeometry(3, 3);
        BattleScreen.WindowsExecutionGeometry four = BattleScreen.windowsExecutionGeometry(4, 4);

        assertEquals(577.04f, one.enemyPlate().y, 0.0001f);
        assertEquals(577.04f, two.enemyPlate().y, 0.0001f);
        assertEquals(490.04f, three.enemyPlate().y, 0.0001f);
        assertEquals(129.04f, four.enemyPlate().y, 0.0001f);
        float centerY = one.enemyPlate().y + one.enemyPlate().height / 2f;
        assertEquals(centerY, two.enemyPlate().y + two.enemyPlate().height / 2f, 0.0001f);
        assertEquals(centerY, three.enemyPlate().y + three.enemyPlate().height / 2f, 0.0001f);
        assertEquals(centerY, four.enemyPlate().y + four.enemyPlate().height / 2f, 0.0001f);
        assertEquals(BattleScreen.WINDOWS_ENEMY_FIGHTER_BOTTOM_Y, four.enemySpriteY(), 0.0001f);
    }

    @Test
    void planningHighlightOnlyDisambiguatesTeamPages() {
        assertFalse(BattleScreen.shouldDrawPlanningHighlight(true, 0));
        assertFalse(BattleScreen.shouldDrawPlanningHighlight(false, 1));
        assertTrue(BattleScreen.shouldDrawPlanningHighlight(false, 2));
    }

    @Test
    void scaledSpriteKeepsItsCenterAndBottomAnchor() {
        Rectangle bounds = BattleScreen.scaledSpriteBounds(
            160f, 42f, 80f, BattleSpriteScaleConfig.Scale.X_1_5.factor());

        assertEquals(100f, bounds.x, 0.0001f);
        assertEquals(42f, bounds.y, 0.0001f);
        assertEquals(120f, bounds.width, 0.0001f);
        assertEquals(120f, bounds.height, 0.0001f);
        assertEquals(160f, bounds.x + bounds.width / 2f, 0.0001f);
    }

    @Test
    void spriteDrawOrderPlacesTheRightmostFighterInFront() {
        assertTrue(BattleScreen.primarySpriteDrawsFirst(120f, 200f));
        assertFalse(BattleScreen.primarySpriteDrawsFirst(200f, 120f));
    }

    @Test
    void threeFighterFormationCentersFirstAndEvenlySpacesTeammates() {
        assertEquals(0f, BattleScreen.formationOffset(0, 3, 80f), 0.0001f);
        assertEquals(80f, BattleScreen.formationOffset(1, 3, 80f), 0.0001f);
        assertEquals(-80f, BattleScreen.formationOffset(2, 3, 80f), 0.0001f);
    }

    @Test
    void fourFighterFormationPlacesThirdLeftmostAndFourthRightmost() {
        assertEquals(-40f, BattleScreen.formationOffset(0, 4, 80f), 0.0001f);
        assertEquals(40f, BattleScreen.formationOffset(1, 4, 80f), 0.0001f);
        assertEquals(-120f, BattleScreen.formationOffset(2, 4, 80f), 0.0001f);
        assertEquals(120f, BattleScreen.formationOffset(3, 4, 80f), 0.0001f);
    }

    @Test
    void plateScalingOnlyChangesAtThreeAndFourFighters() {
        assertEquals(1f, BattleScreen.plateScale(1), 0.0001f);
        assertEquals(1f, BattleScreen.plateScale(2), 0.0001f);
        assertEquals(1.5f, BattleScreen.plateScale(3), 0.0001f);
        assertEquals(2f, BattleScreen.plateScale(4), 0.0001f);
    }

    @Test
    void threeFighterHudKeepsPairCompactAndCentersTheFullWidthLoneHud() {
        Rectangle primaryHud = new Rectangle(100f, 200f, 150f, 100f);

        Rectangle first = BattleScreen.combatantHudBounds(
            0, 3, primaryHud, 300f, 10f, 8f, false);
        Rectangle second = BattleScreen.combatantHudBounds(
            2, 3, primaryHud, 300f, 10f, 8f, false);
        Rectangle lone = BattleScreen.combatantHudBounds(
            1, 3, primaryHud, 300f, 10f, 8f, false);

        assertEquals(150f, first.width, 0.0001f);
        assertEquals(150f, second.width, 0.0001f);
        assertEquals(100f, first.x, 0.0001f);
        assertEquals(260f, second.x, 0.0001f);
        assertEquals(300f, lone.width, 0.0001f);
        assertEquals(105f, lone.x, 0.0001f);
        assertEquals(255f, lone.x + lone.width / 2f, 0.0001f);
        assertEquals(255f, (first.x + second.x + second.width) / 2f, 0.0001f);
    }

    @Test
    void twoFighterFormationRetainsLegacySideOrientation() {
        assertEquals(-17f, BattleScreen.fighterOffset(0, 2, 100f, false), 0.0001f);
        assertEquals(17f, BattleScreen.fighterOffset(1, 2, 100f, false), 0.0001f);
        assertEquals(17f, BattleScreen.fighterOffset(0, 2, 100f, true), 0.0001f);
        assertEquals(-17f, BattleScreen.fighterOffset(1, 2, 100f, true), 0.0001f);
    }

    @Test
    void enemyFourFighterShiftRetainsMirroredLeftDelta() {
        float shift = BattleScreen.enemyFourFighterLeftShift(20f, 600f, 300f);

        assertEquals(16f, shift, 0.0001f);
        assertEquals(20f, 300f - shift - 300f + 36f, 0.0001f);
    }

    @Test
    void enemyPlateMovesAbovePlayerHudWithClearance() {
        assertEquals(22f,
            BattleScreen.enemyPlateClearanceShift(0f, 100f, 48f), 0.0001f);
        assertEquals(0f,
            BattleScreen.enemyPlateClearanceShift(40f, 100f, 48f), 0.0001f);
    }

    @Test
    void playerGroupMovesHalfItsRightEdgeGap() {
        assertEquals(20f,
            BattleScreen.halfRightEdgeGap(1000f, 760f, 200f), 0.0001f);
        assertEquals(0f,
            BattleScreen.halfRightEdgeGap(1000f, 810f, 200f), 0.0001f);
    }

    @Test
    void hudRowsFillTopDownForPlayerAndBottomUpForEnemy() {
        assertEquals(200f, BattleScreen.hudRowY(200f, 0, 100f, 10f, false), 0.0001f);
        assertEquals(90f, BattleScreen.hudRowY(200f, 1, 100f, 10f, false), 0.0001f);
        assertEquals(200f, BattleScreen.hudRowY(200f, 0, 100f, 10f, true), 0.0001f);
        assertEquals(310f, BattleScreen.hudRowY(200f, 1, 100f, 10f, true), 0.0001f);
    }

    @Test
    void singleHudIsCenteredBetweenTwoFighterRows() {
        assertEquals(145f, BattleScreen.centeredHudY(200f, 100f, 10f), 0.0001f);
    }

    @Test
    void windowsHudWidthIsConstrainedAfterScaling() {
        float inwardOffset = 44.8f + 23.04f;
        float width = BattleScreen.scaledHudWidth(
            600f, 1.25f, 1280f, 32f, 12f, inwardOffset, true);
        assertEquals(534.16f, width, 0.0001f);
        float availableCenterGap = 1280f - 64f - width * 2f;
        float shift = Math.min(44.8f, (availableCenterGap - 12f) / 2f);
        assertEquals(12f,
            availableCenterGap - (shift + 23.04f) * 2f, 0.0001f);
        assertEquals(750f,
            BattleScreen.scaledHudWidth(
                600f, 1.25f, 1280f, 32f, 12f, inwardOffset, false),
            0.0001f);
    }

    @Test
    void speedControlsAlignAboveTheNextRoundButton() {
        Rectangle nextRound = new Rectangle(800f, 17f, 210f, 54f);
        Rectangle fastForward = new Rectangle();
        Rectangle skip = new Rectangle();

        BattleScreen.layoutSpeedControls(nextRound, 153f, fastForward, skip);

        assertEquals(54f, fastForward.width, 0.0001f);
        assertEquals(54f, fastForward.height, 0.0001f);
        assertEquals(8f, skip.x - fastForward.x - fastForward.width, 0.0001f);
        assertEquals(nextRound.x + nextRound.width, skip.x + skip.width, 0.0001f);
        assertTrue(fastForward.y > nextRound.y + nextRound.height);
        assertTrue(skip.y + skip.height < 153f);
    }

    @Test
    void windowsNextRoundGeometryDoesNotEnlargeSpeedIcons() {
        Rectangle nextRound = new Rectangle(900f, 21f, 315f, 81f);
        Rectangle fastForward = new Rectangle();
        Rectangle skip = new Rectangle();

        BattleScreen.layoutSpeedControls(nextRound, 218f, fastForward, skip);

        assertEquals(54f, fastForward.width, 0.0001f);
        assertEquals(54f, skip.width, 0.0001f);
    }

    @Test
    void windowsPlaybackControlsStackBesideTheSharedActionButton() {
        Rectangle fastForward = BattleScreen.windowsFastForwardBounds();
        Rectangle skip = BattleScreen.windowsSkipBounds();
        Rectangle action = BattleScreen.windowsActionBounds(true);

        assertEquals(new Rectangle(18f, 416.96f, 82f, 82f), fastForward);
        assertEquals(new Rectangle(18f, 326.96f, 82f, 82f), skip);
        assertEquals(8f, fastForward.y - skip.y - skip.height, 0.0001f);
        assertEquals(action.y, skip.y, 0.0001f);
        assertEquals(action.y + action.height,
            fastForward.y + fastForward.height, 0.0001f);
        assertEquals(62f, action.x - skip.x - skip.width, 0.0001f);
    }

    @Test
    void faintSlideQuicklyPassesBelowTheOriginalFootLine() {
        assertEquals(0f, BattleScreen.faintSlideRatio(0f), 0.0001f);
        assertEquals(0.75f, BattleScreen.faintSlideRatio(0.5f), 0.0001f);
        assertEquals(1f, BattleScreen.faintSlideRatio(1f), 0.0001f);
        assertEquals(0f, BattleScreen.faintSlideRatio(-1f), 0.0001f);
        assertEquals(1f, BattleScreen.faintSlideRatio(2f), 0.0001f);
    }

    @Test
    void entranceGrowsFromTinyToFullScale() {
        assertEquals(0.1f, CombatantPanel.entranceGrowScale(0f), 0.0001f);
        assertEquals(0.55f, CombatantPanel.entranceGrowScale(0.5f), 0.0001f);
        assertEquals(1f, CombatantPanel.entranceGrowScale(1f), 0.0001f);
        assertEquals(1f, CombatantPanel.entranceGrowScale(2f), 0.0001f);
    }

    @Test
    void entranceHudSlidesFromEachRequestedScreenEdge() {
        assertEquals(400f,
            CombatantPanel.entranceHudOffset(0f, true, 1000f, 600f, 200f),
            0.0001f);
        assertEquals(-300f,
            CombatantPanel.entranceHudOffset(0f, false, 1000f, 100f, 200f),
            0.0001f);
        assertEquals(100f,
            CombatantPanel.entranceHudOffset(0.5f, true, 1000f, 600f, 200f),
            0.0001f);
        assertEquals(0f,
            CombatantPanel.entranceHudOffset(1f, false, 1000f, 100f, 200f),
            0.0001f);
    }

}
