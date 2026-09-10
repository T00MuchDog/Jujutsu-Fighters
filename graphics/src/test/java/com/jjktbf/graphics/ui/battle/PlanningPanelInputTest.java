package com.jjktbf.graphics.ui.battle;

import com.badlogic.gdx.Input.Buttons;
import com.jjktbf.graphics.audio.SoundCue;
import com.jjktbf.graphics.multiplayer.TargetListSupport;
import com.jjktbf.graphics.ui.profile.BattleUiLayout;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.coded.CursedSpeechAbility;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.CombatantId;
import com.jjktbf.model.move.AoeType;
import com.jjktbf.model.move.Targeting;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningPanelInputTest {
    private static final int WIDTH = 2560;
    private static final int HEIGHT = 1440;

    @Test
    void clickingCardPlacesMoveAtFirstFreeTickOnItsAssignedTimeline() {
        Move cardMove = move("CARD", 10);
        Move existingMove = move("EXISTING", 10);
        PlanningPanel panel = panel(cardMove, 150);
        assertNotNull(panel.getPlan().place(existingMove, 1, 0));

        clickCard(panel.inputProcessor());

        ActionSegment placed = panel.getPlan().offensiveTimeline().getSegments().stream()
            .filter(segment -> segment.getMove() == cardMove)
            .findFirst()
            .orElseThrow();
        assertEquals(11, placed.getStartTick());
        assertEquals(0, panel.getPlan().defensiveTimeline().getSegments().size());
    }

    @Test
    void clickingCardDefaultsToTheOnlyOpponentAndCanLockWithoutTargetSelection() {
        Move move = move("TARGETED", 10);
        PlanningPanel panel = targetedPanel(move);
        PlanningPanel.PlanningInputProcessor input = panel.inputProcessor();

        clickCard(input);

        ActionSegment placed = panel.getPlan().offensiveTimeline().getSegments().get(0);
        assertEquals(new CombatantId("target-1"), placed.getTarget());
        input.touchDown(160, HEIGHT - 410, 0, Buttons.LEFT);
        assertTrue(panel.isConfirmed());
    }

    @Test
    void draggingCardDefaultsToTheFirstOpponent() {
        Move move = move("TARGETED", 10);
        PlanningPanel panel = targetedPanel(move, List.of(
            new PlanningPanel.TargetOption("target-1", "First target"),
            new PlanningPanel.TargetOption("target-2", "Second target")
        ));
        PlanningPanel.PlanningInputProcessor input = panel.inputProcessor();

        input.touchDown(50, HEIGHT - 220, 0, Buttons.LEFT);
        input.touchDragged(800, HEIGHT - 450, 0);
        input.touchUp(800, HEIGHT - 450, 0, Buttons.LEFT);

        ActionSegment placed = panel.getPlan().offensiveTimeline().getSegments().get(0);
        assertEquals(new CombatantId("target-1"), placed.getTarget());
    }

    @Test
    void clickingCardDoesNothingWhenItsTimelineHasNoFreeRange() {
        Move cardMove = move("FULL", 150);
        // A 150-dot grid is exactly filled by one 150-AP move, so a second
        // placement is rejected by the grid (no free range) even though AP
        // would still allow it.
        PlanningPanel panel = panel(cardMove, 300, 150);

        clickCard(panel.inputProcessor());
        clickCard(panel.inputProcessor());

        assertEquals(1, panel.getPlan().offensiveTimeline().getSegments().size());
        assertEquals(150, panel.getPlan().totalApUsed());
    }

    @Test
    void rightClickingSegmentRemovesItAndRefundsItsBudget() {
        Move move = move("REMOVE", 10);
        // Top-tier grid keeps the bar at full width so the fixed click
        // coordinates land on the placed segment.
        PlanningPanel panel = panel(move, 150, 300);
        assertNotNull(panel.getPlan().place(move, 1, 0));
        List<SoundCue> cues = new ArrayList<>();
        panel.setSoundPlayer(cues::add);

        PlanningPanel.PlanningInputProcessor input = panel.inputProcessor();
        assertTrue(input.touchDown(480, HEIGHT - 450, 0, Buttons.RIGHT));

        assertEquals(0, panel.getPlan().offensiveTimeline().getSegments().size());
        assertEquals(0, panel.getPlan().totalApUsed());
        assertEquals(List.of(SoundCue.UI_PLAN_REMOVE), cues);
    }

    @Test
    void successfulCardPlacementEmitsPlannerFeedback() {
        Move move = move("FEEDBACK", 10);
        PlanningPanel panel = panel(move, 150);
        List<SoundCue> cues = new ArrayList<>();
        panel.setSoundPlayer(cues::add);

        clickCard(panel.inputProcessor());

        assertEquals(List.of(SoundCue.UI_PLAN_PLACE), cues);
    }

    @Test
    void reinforcementPreferenceSurvivesPlannerRebuild() {
        Move move = reinforcedMove("PERSISTENT_REINFORCEMENT");
        Set<String> preferences = new HashSet<>();
        PlanningPanel firstRound = reinforcementPanel(move);
        firstRound.bindReinforcementPreferences(preferences);

        assertTrue(firstRound.inputProcessor().touchDown(
            50, HEIGHT - 220, 0, Buttons.RIGHT));
        assertEquals(Set.of(move.getId()), preferences);

        PlanningPanel secondRound = reinforcementPanel(move);
        secondRound.bindReinforcementPreferences(preferences);
        clickCard(secondRound.inputProcessor());

        ActionSegment placed = secondRound.getPlan().allSegments().get(0);
        assertTrue(placed.isReinforced());
        assertEquals(5, placed.getReinforcementCeCost());
    }

    @Test
    void dragStartsOnlyAfterThresholdAndThenEmitsPickupBeforePlacement() {
        Move move = move("DRAG", 10);
        // Top-tier grid keeps the bar at full width so the fixed drag
        // coordinates land on the timeline.
        PlanningPanel panel = panel(move, 150, 300);
        List<SoundCue> cues = new ArrayList<>();
        panel.setSoundPlayer(cues::add);
        PlanningPanel.PlanningInputProcessor input = panel.inputProcessor();

        input.touchDown(50, HEIGHT - 220, 0, Buttons.LEFT);
        input.touchDragged(52, HEIGHT - 222, 0);
        input.touchUp(52, HEIGHT - 222, 0, Buttons.LEFT);
        assertEquals(List.of(SoundCue.UI_PLAN_PLACE), cues);

        cues.clear();
        input.touchDown(50, HEIGHT - 220, 0, Buttons.LEFT);
        input.touchDragged(800, HEIGHT - 450, 0);
        input.touchUp(800, HEIGHT - 450, 0, Buttons.LEFT);
        assertEquals(List.of(SoundCue.UI_PICKUP, SoundCue.UI_PLAN_PLACE), cues);
    }

    @Test
    void clickingExistingSegmentSelectsItWithoutMovingOrSounding() {
        Move move = move("SELECT", 10);
        PlanningPanel panel = panel(move, 150);
        ActionSegment original = panel.getPlan().place(move, 1, 0);
        assertNotNull(original);
        List<SoundCue> cues = new ArrayList<>();
        panel.setSoundPlayer(cues::add);
        PlanningPanel.PlanningInputProcessor input = panel.inputProcessor();

        input.touchDown(480, HEIGHT - 450, 0, Buttons.LEFT);
        input.touchUp(480, HEIGHT - 450, 0, Buttons.LEFT);

        assertEquals(List.of(original), panel.getPlan().offensiveTimeline().getSegments());
        assertTrue(cues.isEmpty());
    }

    @Test
    void dragPrecheckIncludesTheFinalHitDelay() {
        MoveData data = new MoveData();
        data.id = "DELAYED";
        data.name = "Delayed";
        data.tags = List.of("ATTACK", "PHYSICAL");
        data.apCost = 5;
        data.unleashPoint = 5;
        MoveData.HitComponentData hit = new MoveData.HitComponentData();
        hit.basePower = 10;
        hit.tags = List.of("PHYSICAL");
        hit.delayTicks = 5;
        data.hitComponents = List.of(hit);

        assertEquals(1, PlanningPanel.lastStartTick(data.toMove(), 10));
    }

    @Test
    void targetSelectionIsValidatedAndIncludedInWirePlacements() {
        Move move = move("TARGETED", 10);
        PlanningPanel panel = targetedPanel(move);
        ActionSegment segment = panel.restorePlacement(move, 1, 0, (String) null);

        assertNotNull(segment);
        assertFalse(panel.chooseTarget(segment, "unknown"));
        assertTrue(panel.chooseTarget(segment, "target-1"));
        assertEquals(new CombatantId("target-1"), segment.getTarget());
        assertEquals("actor-1", panel.getPlacements().get(0).actorId());
        assertEquals("target-1", panel.getPlacements().get(0).targetId());
    }

    @Test
    void queuedSingleTargetShowsEnemyNameWithoutWarning() {
        Move move = move("TARGET_DISPLAY", 10);
        PlanningPanel panel = targetedPanel(move, List.of(
            new PlanningPanel.TargetOption("target-1", "First target")));
        ActionSegment segment = panel.restorePlacement(move, 1, 0, "target-1");

        PlanningPanel.SegmentTargetDisplay display = panel.targetDisplay(segment);

        assertEquals("[E] First target", display.compactLabel());
        assertEquals(List.of("ENEMY 1: First target"), display.details());
        assertFalse(display.warning());
    }

    @Test
    void queuedMultipleTargetsShowCountAndConfirmationWarning() {
        Move move = multipleMove("MULTI_DISPLAY", 3);
        PlanningPanel panel = targetedPanel(move, List.of(
            new PlanningPanel.TargetOption("target-1", "First target"),
            new PlanningPanel.TargetOption("target-2", "Second target")));
        ActionSegment segment = panel.restorePlacement(move, 1, 0, List.of());

        assertEquals("[0/3] [E] TARGET?", panel.targetDisplay(segment).compactLabel());
        assertTrue(panel.targetDisplay(segment).warning());

        assertTrue(panel.chooseTarget(segment, "target-1"));
        assertTrue(panel.chooseTarget(segment, "target-2"));
        PlanningPanel.SegmentTargetDisplay pending = panel.targetDisplay(segment);
        assertEquals("[2/3] [E] First target +1", pending.compactLabel());
        assertTrue(pending.warning());
        assertTrue(pending.details().contains("! CONFIRM TARGETS"));

        assertTrue(panel.confirmTargetSelection(segment));
        assertFalse(panel.targetDisplay(segment).warning());
    }

    @Test
    void queuedOrderedPairShowsAllyAndEnemySlots() {
        Move move = new Move.Builder("PAIR_DISPLAY")
            .name("Pair")
            .category(MoveCategory.UTILITY)
            .targeting(Targeting.ALLY_AND_ENEMY)
            .apCost(5)
            .unleashPoint(1)
            .build();
        PlanningPanel panel = targetedPanel(move, List.of(
            new PlanningPanel.TargetOption("enemy", "Enemy")));
        panel.setAllyOptions(List.of(new PlanningPanel.TargetOption("ally", "Ally")));
        ActionSegment segment = panel.restorePlacement(move, 1, 0, List.of());

        assertTrue(panel.chooseTarget(segment, "ally"));
        assertEquals("A: Ally | E: ?", panel.targetDisplay(segment).compactLabel());
        assertTrue(panel.targetDisplay(segment).warning());

        assertTrue(panel.chooseTarget(segment, "enemy"));
        assertEquals("A: Ally | E: Enemy", panel.targetDisplay(segment).compactLabel());
        assertFalse(panel.targetDisplay(segment).warning());
    }

    @Test
    void queuedStaleTargetShowsShortIdAndWarning() {
        Move move = move("STALE_DISPLAY", 10);
        PlanningPanel panel = targetedPanel(move);
        ActionSegment segment = panel.restorePlacement(
            move, 1, 0, "removed-target-123456789");

        PlanningPanel.SegmentTargetDisplay display = panel.targetDisplay(segment);

        assertEquals("[E] !removed-targ", display.compactLabel());
        assertTrue(display.warning());
        assertTrue(display.details().contains("! TARGET NO LONGER AVAILABLE"));
    }

    @Test
    void readOnlyQueuedTargetCanStillBeHoveredForDetails() {
        Move move = move("READ_ONLY_TARGET", 10);
        PlanningPanel panel = targetedPanel(move);
        assertNotNull(panel.restorePlacement(move, 1, 0, "target-1"));
        panel.setReadOnly(true);

        assertTrue(panel.inputProcessor().mouseMoved(480, HEIGHT - 450));
    }

    @Test
    void relocatingSegmentPreservesItsSelectedTarget() {
        Move move = move("RELOCATE_TARGETED", 10);
        PlanningPanel panel = targetedPanel(move);
        assertNotNull(panel.restorePlacement(move, 1, 0, "target-1"));
        PlanningPanel.PlanningInputProcessor input = panel.inputProcessor();

        input.touchDown(480, HEIGHT - 450, 0, Buttons.LEFT);
        input.touchDragged(800, HEIGHT - 450, 0);
        input.touchUp(800, HEIGHT - 450, 0, Buttons.LEFT);

        ActionSegment relocated = panel.getPlan().offensiveTimeline().getSegments().get(0);
        assertEquals(new CombatantId("target-1"), relocated.getTarget());
    }

    @Test
    void multipleTargetSelectionTogglesUpToCapAndLocksOnClickOff() {
        Move move = multipleMove("CURSED_SPEECH", 3);
        PlanningPanel panel = targetedPanel(move, List.of(
            new PlanningPanel.TargetOption("target-1", "First target"),
            new PlanningPanel.TargetOption("target-2", "Second target"),
            new PlanningPanel.TargetOption("target-3", "Third target"),
            new PlanningPanel.TargetOption("target-4", "Fourth target")
        ));
        PlanningPanel.PlanningInputProcessor input = panel.inputProcessor();

        clickCard(input);
        ActionSegment segment = panel.getPlan().offensiveTimeline().getSegments().get(0);
        assertEquals(List.of(), panel.getSelectedTargetIds(segment));
        assertEquals(3, panel.getTargetCap(segment));
        assertFalse(panel.confirmTargetSelection(segment));

        assertTrue(panel.chooseTarget(segment, "target-1"));
        assertTrue(panel.chooseTarget(segment, "target-1"));
        assertEquals(List.of(), panel.getSelectedTargetIds(segment));
        assertTrue(panel.chooseTarget(segment, "target-1"));
        assertTrue(panel.chooseTarget(segment, "target-2"));
        assertTrue(panel.chooseTarget(segment, "target-3"));
        assertFalse(panel.chooseTarget(segment, "target-4"));
        assertEquals(List.of("target-1", "target-2", "target-3"),
            panel.getSelectedTargetIds(segment));
        List<String> wireTargets = TargetListSupport.targetIds(panel.getPlacements().get(0));
        if (hasRecordComponent(panel.getPlacements().get(0).getClass(), "targetIds")) {
            assertEquals(List.of("target-1", "target-2", "target-3"), wireTargets);
        } else {
            assertEquals(List.of("target-1"), wireTargets);
        }

        input.touchDown(160, HEIGHT - 410, 0, Buttons.LEFT);
        assertTrue(panel.isConfirmed(), "clicking off the target menu locks in the selected targets");
    }

    @Test
    void clickingOffAnIncompleteMultipleTargetSelectionBlocksLocking() {
        Move move = multipleMove("INCOMPLETE", 3);
        PlanningPanel panel = targetedPanel(move, List.of(
            new PlanningPanel.TargetOption("target-1", "First target"),
            new PlanningPanel.TargetOption("target-2", "Second target")
        ));
        PlanningPanel.PlanningInputProcessor input = panel.inputProcessor();

        clickCard(input);
        ActionSegment segment = panel.getPlan().offensiveTimeline().getSegments().get(0);

        input.touchDown(160, HEIGHT - 410, 0, Buttons.LEFT);
        assertFalse(panel.isConfirmed(), "an incomplete target selection must not lock");

        assertTrue(panel.chooseTarget(segment, "target-1"));
        assertTrue(panel.confirmTargetSelection(segment));
        input.touchDown(160, HEIGHT - 410, 0, Buttons.LEFT);
        assertTrue(panel.isConfirmed());
    }

    @Test
    void multipleTargetMoveDefaultsToTheOnlyOpponentWithoutOpeningTargetSelection() {
        Move move = multipleMove("SOLE_TARGET", 3);
        PlanningPanel panel = targetedPanel(move);
        PlanningPanel.PlanningInputProcessor input = panel.inputProcessor();

        clickCard(input);

        ActionSegment placed = panel.getPlan().offensiveTimeline().getSegments().get(0);
        assertEquals(List.of("target-1"), panel.getSelectedTargetIds(placed));
        input.touchDown(160, HEIGHT - 410, 0, Buttons.LEFT);
        assertTrue(panel.isConfirmed());
    }

    @Test
    void returnOffersOnlySummonTargets() {
        Move move = returnCommand();
        PlanningPanel panel = targetedPanel(move, List.of(
            new PlanningPanel.TargetOption("fighter", "Fighter"),
            new PlanningPanel.TargetOption("summon", "Summon", true)
        ));
        ActionSegment segment = panel.restorePlacement(move, 1, 0, List.of());

        assertNotNull(segment);
        assertFalse(panel.chooseTarget(segment, "fighter"));
        assertTrue(panel.chooseTarget(segment, "summon"));
        assertEquals(List.of("summon"), panel.getSelectedTargetIds(segment));
    }

    @Test
    void mixedPairSelectionChoosesAllyThenEnemyAndPreservesOrder() {
        Move move = new Move.Builder("PAIR")
            .name("Pair")
            .category(MoveCategory.UTILITY)
            .targeting(Targeting.ALLY_AND_ENEMY)
            .apCost(5)
            .unleashPoint(1)
            .build();
        PlanningPanel panel = targetedPanel(move, List.of(
            new PlanningPanel.TargetOption("enemy", "Enemy")));
        panel.setAllyOptions(List.of(new PlanningPanel.TargetOption("ally", "Ally")));
        ActionSegment segment = panel.restorePlacement(move, 1, 0, List.of());

        assertFalse(panel.chooseTarget(segment, "enemy"));
        assertTrue(panel.chooseTarget(segment, "ally"));
        assertEquals(List.of("ally"), panel.getSelectedTargetIds(segment));
        assertFalse(panel.chooseTarget(segment, "ally"));
        assertTrue(panel.chooseTarget(segment, "enemy"));
        assertEquals(List.of("ally", "enemy"), panel.getSelectedTargetIds(segment));
        assertEquals(List.of("ally", "enemy"),
            TargetListSupport.targetIds(panel.getPlacements().get(0)));
    }

    @Test
    void relocatingMultipleTargetSegmentPreservesOrderedTargetList() {
        Move move = multipleMove("RELOCATE_MULTIPLE", 3);
        PlanningPanel panel = targetedPanel(move, List.of(
            new PlanningPanel.TargetOption("target-1", "First target"),
            new PlanningPanel.TargetOption("target-2", "Second target"),
            new PlanningPanel.TargetOption("target-3", "Third target")
        ));
        ActionSegment original = panel.restorePlacement(
            move, 1, 0, List.of("target-2", "target-1"));
        PlanningPanel.PlanningInputProcessor input = panel.inputProcessor();

        input.touchDown(480, HEIGHT - 450, 0, Buttons.LEFT);
        input.touchDragged(800, HEIGHT - 450, 0);
        input.touchUp(800, HEIGHT - 450, 0, Buttons.LEFT);

        ActionSegment relocated = panel.getPlan().offensiveTimeline().getSegments().get(0);
        assertEquals(List.of("target-2", "target-1"), panel.getSelectedTargetIds(relocated));
        assertFalse(original == relocated);
    }

    @Test
    void editedMultipleTargetsCannotLockUntilSelectionIsConfirmed() {
        Move move = multipleMove("EDIT_MULTIPLE", 3);
        PlanningPanel panel = targetedPanel(move, List.of(
            new PlanningPanel.TargetOption("target-1", "First target"),
            new PlanningPanel.TargetOption("target-2", "Second target")
        ));
        ActionSegment segment = panel.restorePlacement(
            move, 1, 0, List.of("target-1"));

        assertTrue(panel.chooseTarget(segment, "target-2"));
        panel.inputProcessor().touchDown(160, HEIGHT - 410, 0, Buttons.LEFT);
        assertFalse(panel.isConfirmed());
        assertTrue(panel.getLockError().contains("Finish selecting targets"));

        assertTrue(panel.confirmTargetSelection(segment));
        panel.inputProcessor().touchDown(160, HEIGHT - 410, 0, Buttons.LEFT);
        assertTrue(panel.isConfirmed());
    }

    @Test
    void movePaletteExtendsRightInOneScrollableRow() {
        List<Move> moves = java.util.stream.IntStream.range(0, 12)
            .mapToObj(index -> move("PALETTE_" + index, 10)).toList();
        PlanningPanel panel = new PlanningPanel(
            300, moves, Map.of(), 150, 0, 0, null, null, WIDTH, HEIGHT);
        PlanningPanel.LayoutSnapshot snapshot = panel.layoutSnapshot();
        assertEquals(12, snapshot.cards().size());
        for (int i = 1; i < snapshot.cards().size(); i++) {
            var previous = snapshot.cards().get(i - 1);
            var current = snapshot.cards().get(i);
            assertEquals(previous.y, current.y, 0.001f);
            assertTrue(current.x > previous.x + previous.width);
        }
        assertTrue(snapshot.paletteScrollMaximum() > 0f);
    }

    @Test
    void hoveringACompactCardChangesThePersistentDetailWithoutQueuingIt() {
        Move first = move("FIRST_DETAIL", 10);
        Move second = move("SECOND_DETAIL", 10);
        PlanningPanel panel = new PlanningPanel(
            300, List.of(first, second), Map.of(), 150, 0, 0,
            null, null, WIDTH, HEIGHT);

        assertEquals("FIRST_DETAIL", panel.layoutSnapshot().inspectedMoveId());
        assertTrue(panel.inputProcessor().mouseMoved(400, HEIGHT - 220));

        assertEquals("SECOND_DETAIL", panel.layoutSnapshot().inspectedMoveId());
        assertTrue(panel.getPlan().allSegments().isEmpty());
    }

    @Test
    void clickingTheDetailPanelDoesNotQueueTheDisplayedMove() {
        PlanningPanel panel = panel(move("DETAIL_ONLY", 10), 150);

        assertFalse(panel.inputProcessor().touchDown(800, HEIGHT - 80, 0, Buttons.LEFT));

        assertTrue(panel.getPlan().allSegments().isEmpty());
        assertEquals("DETAIL_ONLY", panel.layoutSnapshot().inspectedMoveId());
    }

    @ParameterizedTest
    @CsvSource({"2560,1440", "1920,1080", "1366,768", "1512,982", "2000,1243", "2560,1600", "3440,1440"})
    void cardDragAndLockUseTheSameTransformAfterResize(int width, int height) {
        Move move = move("RESPONSIVE_INPUT", 10);
        PlanningPanel panel = targetedPanel(move);
        panel.resize(width, height);
        panel.setLayout(BattleUiLayout.defaults());
        panel.setStatusEffects(List.of());
        BattleCanvas canvas = BattleCanvas.fit(width, height);
        var snapshot = panel.layoutSnapshot();
        var card = canvas.physicalBounds(snapshot.cards().get(0), BattleCanvas.Anchor.BOTTOM);
        var bar = canvas.physicalBounds(snapshot.offensiveTimeline(), BattleCanvas.Anchor.BOTTOM);
        var lock = canvas.physicalBounds(snapshot.lock(), BattleCanvas.Anchor.BOTTOM);
        var input = panel.inputProcessor();
        int cardX = Math.round(card.x + card.width / 2f);
        int cardY = Math.round(height - card.y - card.height / 2f);
        int barX = Math.round(bar.x + bar.width / 3f);
        int barY = Math.round(height - bar.y - bar.height / 2f);
        assertTrue(input.touchDown(cardX, cardY, 0, Buttons.LEFT));
        assertTrue(input.touchDragged(barX, barY, 0));
        assertTrue(input.touchUp(barX, barY, 0, Buttons.LEFT));
        assertEquals(1, panel.getPlan().allSegments().size());
        assertTrue(input.touchDown(Math.round(lock.x + lock.width / 2f),
            Math.round(height - lock.y - lock.height / 2f), 0, Buttons.LEFT));
        assertTrue(panel.isConfirmed(), panel.getLockError());
    }

    @Test
    void sharedLockInputMapsFromTheScaledBottomCanvas() {
        PlanningPanel panel = panel(move("SHARED_LOCK", 10), 150);
        panel.setLayout(BattleUiLayout.defaults());
        panel.setViewportTransform(0.5f, 30f, 0f, 720f);

        assertTrue(panel.inputProcessor().touchDown(113, 514, 0, Buttons.LEFT));
        assertTrue(panel.isConfirmed());
    }

    @Test
    void readOnlySharedPlannerRejectsCardAndActionClicks() {
        PlanningPanel panel = panel(move("SHARED_READ_ONLY", 10), 150);
        panel.setLayout(BattleUiLayout.defaults());
        panel.setViewportTransform(0.5f, 30f, 0f, 720f);
        panel.setReadOnly(true);
        PlanningPanel.PlanningInputProcessor input = panel.inputProcessor();

        assertFalse(input.touchDown(55, 695, 0, Buttons.LEFT));
        assertFalse(input.touchDown(113, 514, 0, Buttons.LEFT));
        assertTrue(panel.isReadOnly());
        assertFalse(panel.isConfirmed());
        assertTrue(panel.getPlan().allSegments().isEmpty());
    }

    @Test
    void authoritativeRestoreBypassesPostResolutionResourceValidation() {
        Move move = resourceSpendingMove();
        PlanningPanel panel = panel(move, 150);
        panel.setAbilityStates(List.of(new CodedAbilityState("SUPPLY", "Supply", 0, 3, true)));

        assertNotNull(panel.restorePlacement(move, 1, 0, List.of()));
    }

    private static PlanningPanel panel(Move move, int apBudget) {
        return panel(move, apBudget, apBudget);
    }

    /**
     * Builds a panel with an explicit battle grid length. Shared grids share
     * one full-width track; the grid length controls spacing and tick mapping.
     */
    private static PlanningPanel panel(Move move, int apBudget, int gridLength) {
        return new PlanningPanel(
            gridLength, List.of(move), Map.of(move.getId(), 0), apBudget, 0, 0,
            null, null, WIDTH, HEIGHT
        );
    }

    private static PlanningPanel targetedPanel(Move move) {
        return targetedPanel(move, List.of(new PlanningPanel.TargetOption("target-1", "Target")));
    }

    private static PlanningPanel targetedPanel(
        Move move,
        List<PlanningPanel.TargetOption> targets
    ) {
        return new PlanningPanel(
            300,
            "actor-1",
            targets,
            List.of(move),
            Map.of(move.getId(), 0),
            150,
            0,
            0,
            null,
            null,
            WIDTH,
            HEIGHT
        );
    }

    private static PlanningPanel reinforcementPanel(Move move) {
        return new PlanningPanel(
            300, List.of(move), Map.of(move.getId(), 0), 150, 20, 20,
            null, null, WIDTH, HEIGHT
        );
    }

    private static void clickCard(PlanningPanel.PlanningInputProcessor input) {
        input.touchDown(50, HEIGHT - 220, 0, Buttons.LEFT);
        input.touchUp(50, HEIGHT - 220, 0, Buttons.LEFT);
    }

    private static Move move(String id, int apCost) {
        MoveData data = new MoveData();
        data.id = id;
        data.name = id;
        data.tags = List.of("ATTACK");
        data.apCost = apCost;
        data.unleashPoint = 1;
        return data.toMove();
    }

    private static Move reinforcedMove(String id) {
        MoveData data = new MoveData();
        data.id = id;
        data.name = id;
        data.tags = List.of("ATTACK");
        data.apCost = 10;
        data.unleashPoint = 1;
        data.canBeReinforced = true;
        data.reinforcementBaseCeCost = 5;
        data.reinforcementMinCeCost = 5;
        data.reinforcementMaxCeCost = 5;
        return data.toMove();
    }

    private static Move multipleMove(String id, int targetCount) {
        MoveData data = new MoveData();
        data.id = id;
        data.name = id;
        data.tags = List.of("ATTACK", "AOE");
        data.apCost = 10;
        data.unleashPoint = 1;
        data.aoeType = AoeType.MULTIPLE.name();
        data.aoeTargetCount = targetCount;
        return data.toMove();
    }

    private static Move resourceSpendingMove() {
        MoveEffectData effect = AbilityEffectType.TRANSACT_BOUNDED_RESOURCE
            .createDefaultMoveEffect();
        effect.effectId = "effect-000000";
        effect.trigger = MoveEffectTrigger.ON_START.name();
        effect.sourceResourceKey = "SUPPLY";
        effect.sourceResourceAmount = 1;
        effect.targetResourceKey = null;
        effect.targetResourceAmount = 0;
        return new Move.Builder("SPEND_RESOURCE")
            .name("Spend Resource")
            .category(MoveCategory.UTILITY)
            .tags(Set.of(MoveTag.UTILITY))
            .apCost(1)
            .unleashPoint(1)
            .effects(List.of(effect))
            .build();
    }

    private static Move returnCommand() {
        StatusEffect command = StatusEffect.coded(
            CursedSpeechAbility.KEY,
            CursedSpeechAbility.COMMAND,
            CursedSpeechAbility.RETURN,
            null,
            Map.of(),
            null);
        return new Move.Builder("RETURN")
            .name("Return")
            .category(MoveCategory.CURSED_ENERGY)
            .tags(Set.of(MoveTag.ATTACK, MoveTag.AOE, MoveTag.CURSED_ENERGY))
            .basePower(0)
            .neverMiss(true)
            .apCost(10)
            .unleashPoint(1)
            .aoeType(AoeType.MULTIPLE)
            .aoeTargetCount(3)
            .onHitEffects(List.of(command))
            .build();
    }

    private static boolean hasRecordComponent(Class<?> type, String name) {
        return java.util.Arrays.stream(type.getRecordComponents())
            .anyMatch(component -> component.getName().equals(name));
    }
}
