package com.jjktbf.graphics.ui.battle;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.jjktbf.graphics.audio.SoundCue;
import com.jjktbf.graphics.multiplayer.TargetListSupport;
import com.jjktbf.graphics.ui.AbilityStateMeter;
import com.jjktbf.graphics.ui.MiraclesMeter;
import com.jjktbf.graphics.ui.StatusEffectStrip;
import com.jjktbf.graphics.ui.profile.BattleUiLayout;
import com.jjktbf.graphics.ui.text.KeywordPopupPosition;
import com.jjktbf.graphics.ui.text.KeywordTextLayout;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.character.coded.CursedSpeechAbility;
import com.jjktbf.model.character.coded.MiraclesAbility;
import com.jjktbf.model.combat.ActionSegment;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.CeEfficiencyCalculator;
import com.jjktbf.model.combat.CombatantId;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.MoveTargetSelection;
import com.jjktbf.model.combat.Timeline;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.Targeting;
import com.jjktbf.model.progression.TechniqueMasteryResolver;
import com.jjktbf.model.text.MoveDescriptionVariables;
import com.jjktbf.multiplayer.protocol.PlanPlacement;
import com.jjktbf.multiplayer.protocol.StatusEffectState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Round planner with two discrete action timelines and a move-card dock in the
 * shared battle screen's bottom section. All placement remains
 * owned by {@link BattlePlan}; this class only maps input and renders the draft.
 */
public class PlanningPanel {

    public record TargetOption(String instanceId, String label, boolean summon) {
        public TargetOption(String instanceId, String label) {
            this(instanceId, label, false);
        }
    }

    record SegmentTargetDisplay(String compactLabel, List<String> details, boolean warning) {
        private static final SegmentTargetDisplay NONE =
            new SegmentTargetDisplay("", List.of(), false);

        SegmentTargetDisplay {
            compactLabel = compactLabel == null ? "" : compactLabel;
            details = details == null ? List.of() : List.copyOf(details);
        }
    }

    private static final float CARD_GAP = 10f;
    private static final float PALETTE_PADDING = 10f;
    private static final float SCROLLBAR_HEIGHT = 6f;
    private static final float SCROLLBAR_MIN_THUMB_WIDTH = 28f;
    private static final float PALETTE_SCROLL_SPEED = 0.2f;
    private static final float PALETTE_SCROLL_SMOOTHING = 12f;
    private static final float DRAG_THRESHOLD = 5f;
    private static final float COMPACT_CARD_HEIGHT = 127f;
    private static final float MOVE_DETAIL_GAP = 12f;
    private static final float UNIFIED_SECTION_HEIGHT = BattleCanvas.BOTTOM_SECTION_HEIGHT;
    private static final float UNIFIED_TIMELINE_LEFT = 470f;
    private static final float UNIFIED_TIMELINE_RIGHT = 2325f;
    private static final float UNIFIED_TIMELINE_FULL_WIDTH =
        UNIFIED_TIMELINE_RIGHT - UNIFIED_TIMELINE_LEFT;
    private static final float UNIFIED_STAT_GAP = 15f;
    private static final float UNIFIED_TIMELINE_HEIGHT = 78f;
    private static final float UNIFIED_CARD_SCALE = 1.4f * 0.85f;
    private static final float UNIFIED_CARD_TEXT_SCALE = 1f;
    private static final Color UNIFIED_DIVIDER = new Color(0.82f, 0.86f, 0.92f, 0.92f);
    private static final Color READ_ONLY_OVERLAY = new Color(0.32f, 0.32f, 0.34f, 0.62f);

    private final BattlePlan plan;
    private final int maxCe;
    private final String actorId;
    private String actorName = "";
    private final List<TargetOption> targetOptions;
    /**
     * Pickable ALLIED combatants for defensive moves that target an ally
     * (DefenseTargeting SINGLE_ALLY / MULTIPLE_ALLIES). Empty by default; the
     * battle flow populates it from the team (local) or planning spec (online).
     */
    private List<TargetOption> allyOptions = List.of();
    private final List<Move> knownMoves = new ArrayList<>();
    private final int ceEfficiency;
    private final int ceOutput;
    private final com.jjktbf.model.character.AbilityApplicator.AbilityFlags abilityFlags;
    private final Map<String, Integer> authoritativeCeCosts;
    private Set<String> reinforcedPaletteMoves = new LinkedHashSet<>();
    private final BattleCombatant localCombatant;
    private BattleState localBattleState;
    private List<CodedAbilityState> abilityStates = List.of();
    private final BattleUiAssets ui;
    private final MiraclesMeter miraclesMeter = new MiraclesMeter();
    private final AbilityStateMeter abilityStateMeter = new AbilityStateMeter();
    private final StatusEffectStrip statusStrip = new StatusEffectStrip();
    private BattleUiLayout.Planner layout = BattleUiLayout.defaults().planner;

    /**
     * Battle-wide timeline grid length (dot count), derived from the stronger
     * fighter's AP tier. Drives the bars' dot count and the tier-scaled width.
     */
    private final int gridLength;
    private TimelineBar offensiveBar;
    private TimelineBar defensiveBar;
    private final List<MoveCardView> cards = new ArrayList<>();
    private final MoveDetailView moveDetailView = new MoveDetailView();
    private final List<ActionSegmentView> offensiveViews = new ArrayList<>();
    private final List<ActionSegmentView> defensiveViews = new ArrayList<>();
    private final Rectangle headerBounds = new Rectangle();
    private final Rectangle paletteBounds = new Rectangle();
    private final Rectangle paletteViewportBounds = new Rectangle();
    private final Rectangle paletteScrollTrackBounds = new Rectangle();
    private final Rectangle paletteScrollThumbBounds = new Rectangle();
    private final Rectangle lockInBounds = new Rectangle();
    private final Rectangle miraclesBounds = new Rectangle();
    private final Rectangle sectionBounds = new Rectangle();
    private final Rectangle apStatBounds = new Rectangle();
    private final Rectangle ceStatBounds = new Rectangle();
    private final Rectangle offenseIconBounds = new Rectangle();
    private final Rectangle defenseIconBounds = new Rectangle();
    private final Rectangle offenseLabelBounds = new Rectangle();
    private final Rectangle defenseLabelBounds = new Rectangle();

    private float screenWidth;
    private float screenHeight;
    private float paletteContentWidth;
    private float paletteScrollX;
    private float paletteScrollTargetX;
    private float paletteScrollMax;
    private boolean draggingPaletteScrollbar;
    private float scrollbarDragStartX;
    private float scrollbarDragStartOffset;

    private Move draggingMove;
    private ActionSegment draggingSegment;
    private BattlePlan.Board draggingBoard;
    private int originalTick;
    private int originalCeCost;
    private boolean originalReinforced;
    private int originalReinforcementCeCost;
    private List<CombatantId> originalTargets = List.of();
    private boolean originalTargetsPending;
    private int draggingTick;
    private boolean snapValid;
    private boolean clickingMoveCard;
    private boolean dragSoundPlayed;
    private ActionSegment pressedSegment;
    private BattlePlan.Board pressedBoard;
    private float pressMouseX;
    private float pressMouseY;
    private float dragMouseX;
    private float dragMouseY;

    private ActionSegment hoveredSegment;
    private ActionSegment selectedSegment;
    private int hoveredCard = -1;
    private int inspectedCard = -1;
    private boolean lockHovered;
    private boolean confirmed;
    private boolean allowManualUnlock;
    private boolean readOnly;
    private boolean actionButtonShifted;
    private String lockError;
    private ActionSegment targetMenuSegment;
    private final List<Rectangle> targetOptionBounds = new ArrayList<>();
    private final Map<ActionSegment, List<CombatantId>> targetLists = new IdentityHashMap<>();
    private final Set<ActionSegment> pendingTargetSelections =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private Runnable onConfirm = () -> {};
    private Consumer<SoundCue> soundPlayer = cue -> {};
    private float viewportScale = 1f;
    private float viewportOffsetX;
    private float viewportOffsetY;
    private float physicalViewportHeight;

    public PlanningPanel(BattleCombatant combatant, BattleUiAssets ui, float screenWidth, float screenHeight) {
        this(Timeline.gridLengthForStrongestAp(combatant.getMaxApBar()), combatant,
            List.of(), ui, screenWidth, screenHeight);
    }

    /**
     * Builds the planner for a fight whose battle-wide grid length is known
     * ({@code Timeline.gridLengthForStrongestAp(max(player, enemy) AP)}). Local
     * play passes it in so the human's bar matches the AI's bar; the simpler
     * constructor above derives it from the combatant alone.
     */
    public PlanningPanel(
        int gridLength,
        BattleCombatant combatant,
        BattleUiAssets ui,
        float screenWidth,
        float screenHeight
    ) {
        this(gridLength, combatant, List.of(), ui, screenWidth, screenHeight);
    }

    public PlanningPanel(
        int gridLength,
        BattleCombatant combatant,
        List<BattleCombatant> targets,
        BattleUiAssets ui,
        float screenWidth,
        float screenHeight
    ) {
        this.gridLength = gridLength;
        this.plan = BattlePlan.forCombatant(combatant, gridLength);
        this.maxCe = combatant.getMaxCursedEnergy();
        this.actorId = combatant.getInstanceId() == null
            ? null : combatant.getInstanceId().value();
        this.actorName = combatant.getCharacter().getName();
        this.targetOptions = targetOptions(targets);
        this.ceEfficiency = combatant.getEffectiveStats().getCursedEnergyEfficiency();
        this.ceOutput = combatant.getEffectiveStats().getCursedEnergyOutput();
        this.abilityFlags = combatant.getAbilityFlags();
        this.authoritativeCeCosts = Map.of();
        this.localCombatant = combatant;
        this.ui = ui;
        setAbilityStates(combatant.abilityStates());
        statusStrip.setEffects(combatant);
        knownMoves.addAll(combatant.getCharacter().getKnownMoves());
        createBars();
        resize(screenWidth, screenHeight);
    }

    /**
     * Builds the same planner from server-declared online moves and budgets.
     * The server remains authoritative; this panel only creates placement intent.
     */
    public PlanningPanel(
        List<Move> moves,
        Map<String, Integer> ceCosts,
        int apBudget,
        int ceBudget,
        int maxCe,
        CodedAbilityState miraclesState,
        BattleUiAssets ui,
        float screenWidth,
        float screenHeight
    ) {
        this(Timeline.gridLengthForStrongestAp(apBudget),
            null, List.of(), moves, ceCosts, apBudget, ceBudget, maxCe, miraclesState,
            ui, screenWidth, screenHeight);
    }

    /** Online planner with an explicit battle-wide grid length (see local overload). */
    public PlanningPanel(
        int gridLength,
        List<Move> moves,
        Map<String, Integer> ceCosts,
        int apBudget,
        int ceBudget,
        int maxCe,
        CodedAbilityState miraclesState,
        BattleUiAssets ui,
        float screenWidth,
        float screenHeight
    ) {
        this(gridLength, null, List.of(), moves, ceCosts, apBudget, ceBudget, maxCe,
            miraclesState, ui, screenWidth, screenHeight);
    }

    public PlanningPanel(
        int gridLength,
        String actorId,
        List<TargetOption> targetOptions,
        List<Move> moves,
        Map<String, Integer> ceCosts,
        int apBudget,
        int ceBudget,
        int maxCe,
        CodedAbilityState miraclesState,
        BattleUiAssets ui,
        float screenWidth,
        float screenHeight
    ) {
        this.gridLength = gridLength;
        this.plan = new BattlePlan(apBudget, ceBudget, gridLength);
        this.maxCe = maxCe;
        this.actorId = actorId;
        this.targetOptions = targetOptions == null ? List.of() : List.copyOf(targetOptions);
        this.ceEfficiency = 0;
        this.ceOutput = 0;
        this.abilityFlags = null;
        this.authoritativeCeCosts = ceCosts == null ? Map.of() : Map.copyOf(ceCosts);
        this.localCombatant = null;
        this.ui = ui;
        miraclesMeter.setState(miraclesState);
        abilityStateMeter.setStates(miraclesState == null ? List.of() : List.of(miraclesState));
        if (moves != null) knownMoves.addAll(moves);
        createBars();
        resize(screenWidth, screenHeight);
    }

    public void setOnConfirm(Runnable onConfirm) {
        this.onConfirm = onConfirm == null ? () -> {} : onConfirm;
    }

    public void setSoundPlayer(Consumer<SoundCue> soundPlayer) {
        this.soundPlayer = soundPlayer == null ? cue -> {} : soundPlayer;
    }

    /** Uses battle-scoped preferences so move-card reinforcement survives planner rebuilds. */
    public void bindReinforcementPreferences(Set<String> preferences) {
        reinforcedPaletteMoves = preferences == null ? new LinkedHashSet<>() : preferences;
        for (MoveCardView card : cards) {
            card.setReinforced(isPaletteReinforced(card.getMove()));
        }
    }

    public void setBattleState(BattleState state) {
        this.localBattleState = state;
    }

    public void setAbilityStates(List<CodedAbilityState> states) {
        abilityStates = states == null ? List.of() : List.copyOf(states);
        abilityStateMeter.setStates(abilityStates);
    }

    /** Supplies authoritative online statuses for this fighter's planning page. */
    public void setStatusEffects(List<StatusEffectState> states) {
        statusStrip.setEffects(states);
        if (screenWidth > 0f && screenHeight > 0f) layoutUnifiedBattle();
    }

    /** Applies shared metrics and immediately reflows the production planner. */
    public void setLayout(BattleUiLayout battleLayout) {
        if (battleLayout == null) return;
        this.layout = battleLayout.copy().planner;
        if (screenWidth > 0f && screenHeight > 0f) layoutUnifiedBattle();
    }

    /** Maps physical input/scissors back into the fixed unified canvas. */
    public void setViewportTransform(
        float scale,
        float offsetX,
        float offsetY,
        float physicalHeight
    ) {
        viewportScale = Math.max(0.0001f, scale);
        viewportOffsetX = offsetX;
        viewportOffsetY = offsetY;
        physicalViewportHeight = Math.max(1f, physicalHeight);
        sectionBounds.set(-viewportOffsetX / viewportScale, 0f,
            BattleCanvas.WIDTH + 2f * viewportOffsetX / viewportScale, UNIFIED_SECTION_HEIGHT);
    }

    private float textGeometryScale() {
        return layout.textGeometryScale;
    }

    private float scaled(float value) {
        return value * textGeometryScale();
    }

    /** Builds the two bars at the fight's battle-wide grid length (placeholder bounds; set in {@link #resize}). */
    private void createBars() {
        offensiveBar = new TimelineBar(TimelineBar.Kind.OFFENSIVE, 0f, 0f, 1f, 1f, gridLength);
        defensiveBar = new TimelineBar(TimelineBar.Kind.DEFENSIVE, 0f, 0f, 1f, 1f, gridLength);
    }

    public BattlePlan getPlan() { return plan; }
    public boolean isConfirmed() { return confirmed; }
    public boolean isReadOnly() { return readOnly; }
    public String getActorId() { return actorId; }
    public String getLockError() { return lockError; }
    public List<TargetOption> getTargetOptions() { return targetOptions; }

    /**
     * Set the pickable allied combatants for defensive ally-targeting moves,
     * from a team snapshot. Used by the local battle flow, which has the team
     * combatants in hand.
     */
    public void setAllyTargets(List<BattleCombatant> allies) {
        this.allyOptions = targetOptions(allies);
    }

    /**
     * Set the pickable allied combatants as pre-built target options. Used by
     * the online planning flow, which receives options from the server spec.
     */
    public void setAllyOptions(List<TargetOption> allies) {
        this.allyOptions = allies == null ? List.of() : List.copyOf(allies);
    }

    /** Sets the character name displayed beneath the planning header. */
    public void setActorName(String actorName) {
        this.actorName = actorName == null ? "" : actorName;
    }

    public void setAllowManualUnlock(boolean allowManualUnlock) {
        this.allowManualUnlock = allowManualUnlock;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        if (!readOnly) return;
        cancelActiveDrag();
        closeTargetMenu();
        hoveredCard = -1;
        hoveredSegment = null;
        lockHovered = false;
    }

    /** Reserves space for the stacked playback controls beside the action slot. */
    public void setActionButtonShifted(boolean actionButtonShifted) {
        if (this.actionButtonShifted == actionButtonShifted) return;
        this.actionButtonShifted = actionButtonShifted;
        layoutUnifiedBattle();
    }

    /** Returns server-safe intent without exposing local domain objects. */
    public List<PlanPlacement> getPlacements() {
        return plan.allSegments().stream()
            .map(segment -> TargetListSupport.placement(
                segment.getMove().getId(),
                segment.getStartTick(),
                actorId,
                getSelectedTargetIds(segment),
                segment.isReinforced()))
            .toList();
    }

    /** Reopens a locally rejected online plan without discarding its placements. */
    public void unlock() {
        confirmed = false;
        lockError = null;
    }

    /** Displays an already accepted authoritative plan as immutable. */
    public void lock() {
        cancelActiveDrag();
        closeTargetMenu();
        pendingTargetSelections.clear();
        confirmed = true;
    }

    private void cancelActiveDrag() {
        if (draggingSegment != null) {
            selectedSegment = place(
                draggingSegment.getMove(), originalTick, originalCeCost, originalTargets,
                originalReinforced, originalReinforcementCeCost);
            if (originalTargetsPending && selectedSegment != null) {
                pendingTargetSelections.add(selectedSegment);
            }
        }
        draggingMove = null;
        draggingSegment = null;
        draggingBoard = null;
        pressedSegment = null;
        pressedBoard = null;
        draggingPaletteScrollbar = false;
        snapValid = false;
        clickingMoveCard = false;
        dragSoundPlayed = false;
        originalTargets = List.of();
        originalTargetsPending = false;
    }

    public boolean chooseTarget(ActionSegment segment, String targetId) {
        if (segment == null || !requiresExplicitTargets(segment.getMove())) return false;
        Move move = segment.getMove();
        List<CombatantId> selected = new ArrayList<>(targetsOf(segment));
        boolean orderedPair = isOrderedPairMove(move);
        int selectionIndex = orderedPair ? selected.size() : 0;
        boolean valid = eligibleTargetOptions(move, selectionIndex).stream()
            .anyMatch(option -> option.instanceId().equals(targetId));
        if (!valid) return false;

        CombatantId target = new CombatantId(targetId);
        if (orderedPair) {
            if (selected.size() >= targetCap(move) || selected.contains(target)) return false;
            selected.add(target);
            setTargets(segment, selected);
            if (selected.size() == targetCap(move)) {
                pendingTargetSelections.remove(segment);
                closeTargetMenu();
            } else {
                pendingTargetSelections.add(segment);
                if (targetMenuSegment == segment) layoutTargetMenu();
            }
        } else if (isMultipleTargetMove(move)) {
            pendingTargetSelections.add(segment);
            if (selected.remove(target)) {
                setTargets(segment, selected);
            } else {
                if (selected.size() >= targetCap(move)) return false;
                selected.add(target);
                setTargets(segment, selected);
            }
        } else {
            setTargets(segment, List.of(target));
            closeTargetMenu();
        }
        lockError = null;
        return true;
    }

    public boolean confirmTargetSelection(ActionSegment segment) {
        if (segment == null || !isMultipleTargetMove(segment.getMove())) return false;
        int count = targetsOf(segment).size();
        MoveTargetSelection.Requirements requirements =
            MoveTargetSelection.requirements(segment.getMove());
        if (count < requirements.minimumCount() || count > requirements.maximumCount()) return false;
        pendingTargetSelections.remove(segment);
        if (targetMenuSegment == segment) closeTargetMenu();
        lockError = null;
        return true;
    }

    public List<String> getSelectedTargetIds(ActionSegment segment) {
        return targetsOf(segment).stream().map(CombatantId::value).toList();
    }

    public int getTargetCap(ActionSegment segment) {
        return segment == null ? 0 : targetCap(segment.getMove());
    }

    private List<CombatantId> defaultTargets(Move move) {
        if (isOrderedPairMove(move)) return List.of();
        List<TargetOption> eligible = eligibleTargetOptions(move, 0);
        if (!requiresExplicitTargets(move) || eligible.isEmpty()) return List.of();
        if (isMultipleTargetMove(move) && eligible.size() != 1) return List.of();
        return List.of(new CombatantId(eligible.get(0).instanceId()));
    }

    public ActionSegment restorePlacement(Move move, int startTick, int ceCost, String targetId) {
        return restorePlacement(move, startTick, ceCost,
            targetId == null ? List.of() : List.of(targetId));
    }

    public ActionSegment restorePlacement(
        Move move,
        int startTick,
        int ceCost,
        List<String> targetIds
    ) {
        if (move == null) return null;
        return restorePlacement(move, startTick, ceCost, targetIds,
            plan.effectiveApCost(move), plan.effectiveUnleashPoint(move));
    }

    public ActionSegment restorePlacement(
        Move move,
        int startTick,
        int ceCost,
        List<String> targetIds,
        int apCost,
        int unleashPoint,
        boolean reinforced,
        int reinforcementCeCost
    ) {
        if (move == null) return null;
        List<CombatantId> targets = targetIds == null ? List.of() : targetIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .map(CombatantId::new)
            .distinct()
            .toList();
        ActionSegment segment = plan.restorePlacement(
            move, startTick, ceCost, targets, apCost, unleashPoint,
            reinforced, reinforcementCeCost);
        if (segment != null) setTargets(segment, targets);
        return segment;
    }

    public ActionSegment restorePlacement(
        Move move,
        int startTick,
        int ceCost,
        List<String> targetIds,
        int apCost,
        int unleashPoint
    ) {
        return restorePlacement(move, startTick, ceCost, targetIds, apCost,
            unleashPoint, false, 0);
    }

    private ActionSegment place(Move move, int startTick, int ceCost, List<CombatantId> targets) {
        boolean reinforced = isPaletteReinforced(move);
        return place(move, startTick, ceCost, targets, reinforced,
            reinforced ? reinforcementCeCost(move) : 0);
    }

    private ActionSegment place(
        Move move, int startTick, int ceCost, List<CombatantId> targets,
        boolean reinforced, int reinforcementCeCost
    ) {
        ActionSegment segment = plan.placeWithTargets(
            move, startTick, ceCost, targets, reinforced, reinforcementCeCost);
        if (segment != null) setTargets(segment, targets);
        return segment;
    }

    private ActionSegment placeFirstFit(Move move, int ceCost, List<CombatantId> targets) {
        boolean reinforced = isPaletteReinforced(move);
        int surcharge = reinforced ? reinforcementCeCost(move) : 0;
        ActionSegment segment = plan.placeFirstFitWithTargets(
            move, ceCost, targets, reinforced, surcharge);
        if (segment != null) setTargets(segment, targets);
        return segment;
    }

    private List<CombatantId> targetsOf(ActionSegment segment) {
        List<CombatantId> selected = targetLists.get(segment);
        return selected == null ? TargetListSupport.segmentTargets(segment) : selected;
    }

    private void setTargets(ActionSegment segment, List<CombatantId> targets) {
        List<CombatantId> normalized = targets == null ? List.of() :
            List.copyOf(new LinkedHashSet<>(targets));
        targetLists.put(segment, normalized);
        TargetListSupport.setSegmentTargets(segment, normalized);
    }

    private static boolean isMultipleTargetMove(Move move) {
        return move != null && MoveTargetSelection.requirements(move).maximumCount() > 1;
    }

    private static boolean isOrderedPairMove(Move move) {
        return move != null
            && move.getTargeting() == Targeting.ALLY_AND_ENEMY;
    }

    private static boolean requiresExplicitTargets(Move move) {
        return BattlePlan.requiresTarget(move) || isMultipleTargetMove(move);
    }

    private static int targetCap(Move move) {
        return move == null ? 0 : MoveTargetSelection.requirements(move).maximumCount();
    }

    private static List<TargetOption> targetOptions(List<BattleCombatant> targets) {
        if (targets == null) return List.of();
        List<TargetOption> options = new ArrayList<>();
        for (BattleCombatant target : targets) {
            if (target == null || target.getInstanceId() == null || !target.isActive()) continue;
            options.add(new TargetOption(
                target.getInstanceId().value(),
                target.getCharacter().getName() + " #" + (target.getRosterOrder() + 1),
                target.isSummon()));
        }
        return List.copyOf(options);
    }

    public PlanningInputProcessor inputProcessor() {
        return new PlanningInputProcessor();
    }

    /** Keeps control geometry canonical and updates its physical input mapping. */
    public void resize(float width, float height) {
        screenWidth = BattleCanvas.WIDTH;
        screenHeight = BattleCanvas.HEIGHT;
        BattleCanvas canvas = BattleCanvas.fit(width, height);
        setViewportTransform(canvas.scale(), canvas.offsetX(),
            canvas.offsetY(BattleCanvas.Anchor.BOTTOM), canvas.viewportHeight());
        layoutUnifiedBattle();
    }

    private void layoutUnifiedBattle() {
        headerBounds.set(0f, 0f, 0f, 0f);
        float actionX = actionButtonShifted
            ? BattleCanvas.PLAYBACK_ACTION_X : BattleCanvas.ACTION_X;
        lockInBounds.set(
            actionX,
            BattleCanvas.ACTION_Y,
            BattleCanvas.ACTION_WIDTH,
            BattleCanvas.ACTION_HEIGHT);
        float verticalShift = BattleCanvas.PLANNING_HEIGHT_REDUCTION;
        offenseIconBounds.set(350f, 495f - verticalShift, 24f, 24f);
        defenseIconBounds.set(350f, 401f - verticalShift, 24f, 24f);
        offenseLabelBounds.set(388f, 483f - verticalShift, 126f, 40f);
        defenseLabelBounds.set(388f, 389f - verticalShift, 126f, 40f);
        miraclesBounds.set(0f, 0f, 0f, 0f);
        abilityStateMeter.setBounds(0f, 0f, 0f, 0f);

        paletteBounds.set(18f, 18f, 2524f, 344f - verticalShift);
        buildPalette();

        float statX = UNIFIED_TIMELINE_RIGHT + UNIFIED_STAT_GAP;
        apStatBounds.set(statX, 485.25f - verticalShift, 180f, 43.5f);
        ceStatBounds.set(statX, 391.25f - verticalShift, 186f, 43.5f);
        defensiveBar.setBounds(
            UNIFIED_TIMELINE_LEFT, BattleCanvas.ACTION_Y,
            UNIFIED_TIMELINE_FULL_WIDTH, UNIFIED_TIMELINE_HEIGHT);
        offensiveBar.setBounds(
            UNIFIED_TIMELINE_LEFT, 468f - verticalShift,
            UNIFIED_TIMELINE_FULL_WIDTH, UNIFIED_TIMELINE_HEIGHT);
    }

    record LayoutSnapshot(
        Rectangle palette,
        Rectangle defensiveTimeline,
        Rectangle offensiveTimeline,
        Rectangle miracles,
        Rectangle header,
        float paletteScrollMaximum,
        Rectangle section,
        Rectangle lock,
        Rectangle apStat,
        Rectangle ceStat,
        Rectangle paletteViewport,
        MoveDetailView.LayoutSnapshot moveDetail,
        String inspectedMoveId,
        List<Rectangle> cards
    ) { }

    LayoutSnapshot layoutSnapshot() {
        return new LayoutSnapshot(
            new Rectangle(paletteBounds),
            new Rectangle(defensiveBar.getBounds()),
            new Rectangle(offensiveBar.getBounds()),
            new Rectangle(miraclesBounds),
            new Rectangle(headerBounds),
            paletteScrollMax,
            new Rectangle(sectionBounds),
            new Rectangle(lockInBounds),
            new Rectangle(apStatBounds),
            new Rectangle(ceStatBounds),
            new Rectangle(paletteViewportBounds),
            moveDetailView.layoutSnapshot(),
            inspectedMove() == null ? null : inspectedMove().getId(),
            cards.stream().map(card -> new Rectangle(card.getBounds())).toList());
    }

    private void buildPalette() {
        String inspectedMoveId = inspectedMove() == null ? null : inspectedMove().getId();
        cards.clear();
        float cardGeometryScale = UNIFIED_CARD_SCALE;
        for (int i = 0; i < knownMoves.size(); i++) {
            MoveCardView card = new MoveCardView(
                knownMoves.get(i), 0f, 0f, cardGeometryScale,
                cardWidth(), cardHeight(), 5);
            card.setCompact(true);
            card.setReinforced(isPaletteReinforced(knownMoves.get(i)));
            cards.add(card);
        }

        paletteViewportBounds.set(
            paletteBounds.x + scaled(PALETTE_PADDING),
            paletteBounds.y + scaled(PALETTE_PADDING),
            Math.max(0f, paletteBounds.width - scaled(PALETTE_PADDING) * 2f),
            Math.max(0f, paletteBounds.height - scaled(PALETTE_PADDING) * 2f));
        float detailHeight = Math.max(0f,
            MoveCardView.CARD_H * UNIFIED_CARD_SCALE - cardHeight() - MOVE_DETAIL_GAP);
        moveDetailView.setBounds(
            paletteViewportBounds.x, paletteViewportBounds.y,
            paletteViewportBounds.width, detailHeight);
        inspectedCard = inspectedMoveId == null ? -1 : indexOfMove(inspectedMoveId);
        if (inspectedCard < 0 && !cards.isEmpty()) inspectedCard = 0;
        int columns = knownMoves.size();
        paletteContentWidth = columns == 0
            ? 0f
            : columns * cardWidth() + (columns - 1) * scaled(CARD_GAP);
        paletteScrollMax = Math.max(0f, paletteContentWidth - paletteViewportBounds.width);
        paletteScrollX = clamp(paletteScrollX, 0f, paletteScrollMax);
        paletteScrollTargetX = clamp(paletteScrollTargetX, 0f, paletteScrollMax);
        draggingPaletteScrollbar = false;
        layoutPaletteCards();
        layoutPaletteScrollbar();
    }

    private void layoutPaletteCards() {
        for (int i = 0; i < cards.size(); i++) {
            float x = paletteViewportBounds.x
                + i * (cardWidth() + scaled(CARD_GAP)) - paletteScrollX;
            cards.get(i).getBounds().setPosition(
                x, moveDetailView.getBounds().y + moveDetailView.getBounds().height + MOVE_DETAIL_GAP);
        }
    }

    private void layoutPaletteScrollbar() {
        if (paletteScrollMax <= 0f) {
            paletteScrollTrackBounds.set(0f, 0f, 0f, 0f);
            paletteScrollThumbBounds.set(0f, 0f, 0f, 0f);
            return;
        }

        paletteScrollTrackBounds.set(
            paletteBounds.x + scaled(PALETTE_PADDING),
            paletteBounds.y + scaled(2f),
            Math.max(0f, paletteBounds.width - scaled(PALETTE_PADDING) * 2f),
            scaled(SCROLLBAR_HEIGHT));
        float thumbWidth = Math.max(
            scaled(SCROLLBAR_MIN_THUMB_WIDTH),
            paletteScrollTrackBounds.width * paletteViewportBounds.width / paletteContentWidth);
        thumbWidth = Math.min(paletteScrollTrackBounds.width, thumbWidth);
        float travel = paletteScrollTrackBounds.width - thumbWidth;
        float progress = paletteScrollMax == 0f ? 0f : paletteScrollX / paletteScrollMax;
        paletteScrollThumbBounds.set(
            paletteScrollTrackBounds.x + travel * progress,
            paletteScrollTrackBounds.y,
            thumbWidth,
            paletteScrollTrackBounds.height);
    }

    private void setPaletteScroll(float value) {
        paletteScrollX = clamp(value, 0f, paletteScrollMax);
        paletteScrollTargetX = paletteScrollX;
        layoutPaletteCards();
        layoutPaletteScrollbar();
    }

    private void setPaletteScrollTarget(float value) {
        paletteScrollTargetX = clamp(value, 0f, paletteScrollMax);
    }

    private void updatePaletteScrollAnimation(float delta) {
        float distance = paletteScrollTargetX - paletteScrollX;
        if (Math.abs(distance) < 0.1f) {
            if (distance == 0f) return;
            paletteScrollX = paletteScrollTargetX;
        } else {
            float elapsed = Math.min(Math.max(delta, 0f), 0.05f);
            float blend = 1f - (float) Math.exp(-PALETTE_SCROLL_SMOOTHING * elapsed);
            paletteScrollX += distance * blend;
        }
        layoutPaletteCards();
        layoutPaletteScrollbar();
        updateHoveredCard();
    }

    private float cardWidth() {
        // Keep the approved wider picker width; overflow remains horizontally scrollable.
        return MoveCardView.CARD_W * UNIFIED_CARD_SCALE * 1.2f;
    }

    private float cardHeight() {
        return COMPACT_CARD_HEIGHT;
    }

    private void refresh() {
        for (int i = 0; i < cards.size(); i++) {
            MoveCardView card = cards.get(i);
            Move move = card.getMove();
            if (localCombatant != null) {
                card.setDisplayDescription(MoveDescriptionVariables.resolve(
                    move, TechniqueMasteryResolver.masteryOf(localCombatant)));
            }
            card.setDisplayedTiming(
                plan.effectiveApCost(move), plan.effectiveUnleashPoint(move));
            card.setDisabled(readOnly || !plan.canPlace(move, ceCost(move)));
            card.setHovered(i == inspectedCard);
            card.setDragging(move == draggingMove);
        }

        offensiveViews.clear();
        defensiveViews.clear();
        for (ActionSegment segment : plan.offensiveTimeline().getSegments()) {
            ActionSegmentView view = new ActionSegmentView(segment, 0f, 0f, 0f,
                offensiveBar.getBounds().height - 12f);
            applyTargetDisplay(view);
            offensiveViews.add(view);
        }
        for (ActionSegment segment : plan.defensiveTimeline().getSegments()) {
            ActionSegmentView view = new ActionSegmentView(segment, 0f, 0f, 0f,
                defensiveBar.getBounds().height - 12f);
            applyTargetDisplay(view);
            defensiveViews.add(view);
        }
        offensiveBar.layoutSegments(offensiveViews);
        defensiveBar.layoutSegments(defensiveViews);
        for (ActionSegmentView view : offensiveViews) {
            view.setHighlighted(view.getSegment() == hoveredSegment || view.getSegment() == selectedSegment);
        }
        for (ActionSegmentView view : defensiveViews) {
            view.setHighlighted(view.getSegment() == hoveredSegment || view.getSegment() == selectedSegment);
        }
    }

    public void draw(Batch batch, BitmapFont font, BitmapFont titleFont, BitmapFont statFont) {
        updatePaletteScrollAnimation(Gdx.graphics.getDeltaTime());
        refresh();
        batch.begin();
        drawUnifiedChrome(batch, font);
        drawTimelineLabel(batch, font, offensiveBar, "OFFENSE", ui.offenseIcon, BattleUiAssets.OFFENSE);
        drawTimelineLabel(batch, font, defensiveBar, "DEFENSE", ui.defenseIcon, BattleUiAssets.DEFENSE);

        offensiveBar.draw(batch, ui, isDropTarget(BattlePlan.Board.OFFENSIVE));
        defensiveBar.draw(batch, ui, isDropTarget(BattlePlan.Board.DEFENSIVE));
        for (ActionSegmentView view : offensiveViews) view.draw(batch, font, ui);
        for (ActionSegmentView view : defensiveViews) view.draw(batch, font, ui);

        ui.palette.draw(batch, paletteBounds.x, paletteBounds.y, paletteBounds.width, paletteBounds.height);
        beginPaletteClip(batch);
        float originalTitleScaleX = titleFont.getData().scaleX;
        float originalTitleScaleY = titleFont.getData().scaleY;
        float originalStatScaleX = statFont.getData().scaleX;
        float originalStatScaleY = statFont.getData().scaleY;
        boolean sharedCardFont = titleFont == statFont;
        try {
            titleFont.getData().setScale(
                originalTitleScaleX * UNIFIED_CARD_TEXT_SCALE,
                originalTitleScaleY * UNIFIED_CARD_TEXT_SCALE);
            if (!sharedCardFont) {
                statFont.getData().setScale(
                    originalStatScaleX * UNIFIED_CARD_TEXT_SCALE,
                    originalStatScaleY * UNIFIED_CARD_TEXT_SCALE);
            }
            for (MoveCardView card : cards) {
                card.draw(batch, titleFont, statFont, ui, plannedCeCost(card.getMove()));
            }
            MoveCardView detailCard = inspectedCard >= 0 && inspectedCard < cards.size()
                ? cards.get(inspectedCard) : null;
            if (detailCard != null) {
                moveDetailView.draw(
                    batch, ui, font, titleFont, statFont,
                    detailCard.getMove(), detailCard.getDisplayDescription(),
                    detailCard.isReinforced());
            }
        } finally {
            titleFont.getData().setScale(originalTitleScaleX, originalTitleScaleY);
            if (!sharedCardFont) {
                statFont.getData().setScale(originalStatScaleX, originalStatScaleY);
            }
            endPaletteClip(batch);
        }
        drawPaletteScrollbar(batch);
        drawDragAvatar(batch, font);
        drawKeywordTooltip(batch, font, titleFont);
        drawSegmentTargetTooltip(batch, font);
        drawTargetMenu(batch, font);
        if (readOnly) {
            batch.setColor(READ_ONLY_OVERLAY);
            batch.draw(ui.pixel,
                sectionBounds.x, sectionBounds.y, sectionBounds.width, sectionBounds.height);
            batch.setColor(Color.WHITE);
        }
        batch.end();
    }

    private void drawUnifiedChrome(Batch batch, BitmapFont font) {
        ui.palette.draw(batch,
            sectionBounds.x, sectionBounds.y, sectionBounds.width, sectionBounds.height);
        batch.setColor(UNIFIED_DIVIDER);
        batch.draw(ui.pixel, sectionBounds.x, UNIFIED_SECTION_HEIGHT - 2f,
            sectionBounds.width, 2f);
        batch.setColor(Color.WHITE);

        drawStat(batch, font, apStatBounds.x, apStatBounds.y, apStatBounds.width,
            "AP", plan.remainingApBudget(), plan.apBudget(), BattleUiAssets.YELLOW);
        drawStat(batch, font, ceStatBounds.x, ceStatBounds.y, ceStatBounds.width,
            "CE", plan.remainingCe(), maxCe, BattleUiAssets.CURSED_ENERGY);

        if (confirmed) {
            ui.lockButtonDisabled.draw(batch,
                lockInBounds.x, lockInBounds.y, lockInBounds.width, lockInBounds.height);
        } else if (lockHovered) {
            ui.lockButtonOver.draw(batch,
                lockInBounds.x, lockInBounds.y, lockInBounds.width, lockInBounds.height);
        } else {
            ui.lockButton.draw(batch,
                lockInBounds.x, lockInBounds.y, lockInBounds.width, lockInBounds.height);
        }
        String label = confirmed ? "LOCKED" : "LOCK IN";
        GlyphLayout labelLayout = new GlyphLayout(font, label);
        font.setColor(Color.WHITE);
        font.draw(batch, label,
            lockInBounds.x + (lockInBounds.width - labelLayout.width) / 2f,
            lockInBounds.y + (lockInBounds.height + labelLayout.height) / 2f);
        if (lockError != null) {
            font.setColor(BattleUiAssets.YELLOW);
            font.draw(batch, lockError, 280f,
                368f - BattleCanvas.PLANNING_HEIGHT_REDUCTION);
        }
    }

    private void beginPaletteClip(Batch batch) {
        batch.flush();
        float scaleX = Gdx.graphics.getBackBufferWidth() / (float) Gdx.graphics.getWidth();
        float scaleY = Gdx.graphics.getBackBufferHeight() / (float) Gdx.graphics.getHeight();
        float clipX = paletteViewportBounds.x;
        float clipY = paletteViewportBounds.y;
        float clipWidth = paletteViewportBounds.width;
        float clipHeight = paletteViewportBounds.height;
        clipX = viewportOffsetX + clipX * viewportScale;
        clipY = viewportOffsetY + clipY * viewportScale;
        clipWidth *= viewportScale;
        clipHeight *= viewportScale;
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(
            Math.round(clipX * scaleX),
            Math.round(clipY * scaleY),
            Math.round(clipWidth * scaleX),
            Math.round(clipHeight * scaleY));
    }

    private void endPaletteClip(Batch batch) {
        batch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }


    private void drawPaletteScrollbar(Batch batch) {
        if (paletteScrollMax <= 0f) return;
        batch.setColor(BattleUiAssets.INK);
        batch.draw(
            ui.pixel,
            paletteScrollTrackBounds.x,
            paletteScrollTrackBounds.y,
            paletteScrollTrackBounds.width,
            paletteScrollTrackBounds.height);
        batch.setColor(BattleUiAssets.YELLOW);
        batch.draw(
            ui.pixel,
            paletteScrollThumbBounds.x,
            paletteScrollThumbBounds.y,
            paletteScrollThumbBounds.width,
            paletteScrollThumbBounds.height);
        batch.setColor(Color.WHITE);
    }

    private void drawKeywordTooltip(Batch batch, BitmapFont font, BitmapFont titleFont) {
        if (draggedMove() != null) return;
        MoveCardView.KeywordHover hover = moveDetailView.keywordAt(dragMouseX, dragMouseY);
        if (hover == null) return;

        float popupWidth = Math.max(1f, Math.min(scaled(320f), screenWidth - scaled(20f)));
        float padding = scaled(12f);
        float contentWidth = Math.max(1f, popupWidth - padding * 2f);
        GlyphLayout heading = new GlyphLayout(
            titleFont,
            hover.text(),
            KeywordTextLayout.KEYWORD_ORANGE,
            contentWidth,
            Align.left,
            true);
        GlyphLayout description = new GlyphLayout(
            font,
            hover.entry().description(),
            BattleUiAssets.TEXT,
            contentWidth,
            Align.left,
            true);
        float headingGap = scaled(6f);
        float popupHeight = padding * 2f + heading.height + headingGap + description.height;
        KeywordPopupPosition.Position position = KeywordPopupPosition.place(
            hover.bounds().x,
            hover.bounds().y,
            hover.bounds().width,
            hover.bounds().height,
            popupWidth,
            popupHeight,
            screenWidth,
            UNIFIED_SECTION_HEIGHT);

        ui.cardOver.draw(batch, position.x(), position.y(), popupWidth, popupHeight);
        float textTop = position.y() + popupHeight - padding;
        titleFont.draw(batch, heading, position.x() + padding, textTop);
        font.draw(batch, description, position.x() + padding,
            textTop - heading.height - headingGap);
    }

    private void applyTargetDisplay(ActionSegmentView view) {
        SegmentTargetDisplay display = targetDisplay(view.getSegment());
        view.setTargetWarning(display.warning());
    }

    SegmentTargetDisplay targetDisplay(ActionSegment segment) {
        if (segment == null) return SegmentTargetDisplay.NONE;
        Move move = segment.getMove();
        MoveTargetSelection.Requirements requirements = MoveTargetSelection.requirements(move);
        if (requirements.maximumCount() == 0) return SegmentTargetDisplay.NONE;

        List<CombatantId> selected = targetsOf(segment);
        List<String> details = new ArrayList<>();
        boolean warning = pendingTargetSelections.contains(segment)
            || MoveTargetSelection.targetCountError(move, selected) != null;
        boolean ordered = requirements.orderedRelationships().size() > 1;
        boolean stale = false;
        String compact;

        if (ordered) {
            List<String> slots = new ArrayList<>();
            for (int index = 0; index < requirements.maximumCount(); index++) {
                MoveTargetSelection.Relationship relationship = requirements.relationshipAt(index);
                String slot = relationship == MoveTargetSelection.Relationship.ALLY ? "A" : "E";
                if (index >= selected.size()) {
                    slots.add(slot + ": ?");
                    details.add(relationshipLabel(relationship) + " " + (index + 1) + ": NOT SELECTED");
                    continue;
                }
                CombatantId targetId = selected.get(index);
                TargetOption option = targetOption(relationship, targetId.value());
                if (option == null) {
                    stale = true;
                    String fallback = targetLabelFromAnyOption(targetId.value());
                    slots.add(slot + ": !" + fallback);
                    details.add(relationshipLabel(relationship) + " " + (index + 1)
                        + ": " + fallback + " (UNAVAILABLE)");
                } else {
                    slots.add(slot + ": " + option.label());
                    details.add(relationshipLabel(relationship) + " " + (index + 1)
                        + ": " + option.label());
                }
            }
            compact = String.join(" | ", slots);
        } else {
            MoveTargetSelection.Relationship relationship = requirements.relationshipAt(0);
            String marker = relationship == MoveTargetSelection.Relationship.ALLY ? "[A]" : "[E]";
            String count = requirements.maximumCount() > 1
                ? "[" + selected.size() + "/" + requirements.maximumCount() + "] " : "";
            if (selected.isEmpty()) {
                compact = count + marker + " TARGET?";
            } else {
                List<String> labels = new ArrayList<>();
                for (int index = 0; index < selected.size(); index++) {
                    CombatantId targetId = selected.get(index);
                    TargetOption option = targetOption(relationship, targetId.value());
                    String label;
                    if (option == null) {
                        stale = true;
                        label = "!" + targetLabelFromAnyOption(targetId.value());
                    } else {
                        label = option.label();
                    }
                    labels.add(label);
                    details.add(relationshipLabel(relationship) + " " + (index + 1)
                        + ": " + label + (option == null ? " (UNAVAILABLE)" : ""));
                }
                compact = count + marker + " " + labels.get(0)
                    + (labels.size() > 1 ? " +" + (labels.size() - 1) : "");
            }
        }

        if (stale) {
            warning = true;
            details.add(0, "! TARGET NO LONGER AVAILABLE");
        } else if (MoveTargetSelection.targetCountError(move, selected) != null) {
            details.add(0, "! INCOMPLETE TARGETS");
        } else if (pendingTargetSelections.contains(segment)) {
            details.add(0, "! CONFIRM TARGETS");
        }
        if (requirements.maximumCount() > 1) {
            details.add(0, "TARGETS " + selected.size() + "/" + requirements.maximumCount());
        }
        return new SegmentTargetDisplay(compact, details, warning);
    }

    private TargetOption targetOption(
        MoveTargetSelection.Relationship relationship,
        String targetId
    ) {
        List<TargetOption> options = relationship == MoveTargetSelection.Relationship.ALLY
            ? allyOptions : targetOptions;
        for (TargetOption option : options) {
            if (option.instanceId().equals(targetId)) return option;
        }
        return null;
    }

    private String targetLabelFromAnyOption(String targetId) {
        for (TargetOption option : allyOptions) {
            if (option.instanceId().equals(targetId)) return option.label();
        }
        for (TargetOption option : targetOptions) {
            if (option.instanceId().equals(targetId)) return option.label();
        }
        if (targetId == null || targetId.isBlank()) return "UNKNOWN";
        return targetId.length() <= 12 ? targetId : targetId.substring(0, 12);
    }

    private static String relationshipLabel(MoveTargetSelection.Relationship relationship) {
        return relationship == MoveTargetSelection.Relationship.ALLY ? "ALLY" : "ENEMY";
    }

    private void drawSegmentTargetTooltip(Batch batch, BitmapFont font) {
        ActionSegment segment = hoveredSegment;
        if (segment == null || segment == targetMenuSegment) return;
        SegmentTargetDisplay display = targetDisplay(segment);
        if (display.details().isEmpty()) return;
        ActionSegmentView view = viewFor(segment);
        if (view == null) return;

        float padding = scaled(10f);
        float width = Math.min(scaled(340f), Math.max(scaled(190f), screenWidth - scaled(20f)));
        float textWidth = width - padding * 2f;
        float planningTop = UNIFIED_SECTION_HEIGHT;
        float availableHeight = Math.max(1f, planningTop - scaled(20f));
        float availableContentHeight = Math.max(1f, availableHeight - padding * 2f);
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        float tooltipScale = 1f;
        float rowHeight = scaled(21f);
        float contentHeight = 0f;
        List<GlyphLayout> rows = List.of();
        for (int attempt = 0; attempt < 3; attempt++) {
            font.getData().setScale(
                originalScaleX * tooltipScale, originalScaleY * tooltipScale);
            rowHeight = scaled(21f) * tooltipScale;
            rows = new ArrayList<>();
            rows.add(new GlyphLayout(font, segment.getMove().getName() + " TARGETS",
                Color.WHITE, textWidth, Align.left, true));
            for (String line : display.details()) {
                rows.add(new GlyphLayout(font, line,
                    line.startsWith("!") ? BattleUiAssets.YELLOW : Color.WHITE,
                    textWidth, Align.left, true));
            }
            contentHeight = 0f;
            for (GlyphLayout row : rows) {
                contentHeight += Math.max(rowHeight, row.height + scaled(3f) * tooltipScale);
            }
            if (contentHeight <= availableContentHeight) break;
            tooltipScale *= availableContentHeight / contentHeight * 0.96f;
        }
        float height = Math.min(availableHeight, padding * 2f + contentHeight);
        float x = clamp(view.getBounds().x, scaled(10f),
            Math.max(scaled(10f), screenWidth - width - scaled(10f)));
        float y = view.getBounds().y + view.getBounds().height + scaled(5f);
        if (y + height > planningTop - scaled(10f)) {
            y = Math.max(scaled(10f), view.getBounds().y - height - scaled(5f));
        }
        try {
            ui.dialogue.draw(batch, x, y, width, height);
            float rowY = y + height - padding;
            for (GlyphLayout row : rows) {
                font.draw(batch, row, x + padding, rowY);
                rowY -= Math.max(rowHeight, row.height + scaled(3f) * tooltipScale);
            }
        } finally {
            font.getData().setScale(originalScaleX, originalScaleY);
        }
    }

    private void drawTargetMenu(Batch batch, BitmapFont font) {
        if (targetMenuSegment == null) return;
        List<TargetOption> eligibleTargets = eligibleTargetOptions(
            targetMenuSegment.getMove(), targetsOf(targetMenuSegment).size());
        if (eligibleTargets.isEmpty()) return;
        layoutTargetMenu();
        boolean multiple = isMultipleTargetMove(targetMenuSegment.getMove());
        List<String> selectedIds = getSelectedTargetIds(targetMenuSegment);
        for (int i = 0; i < eligibleTargets.size(); i++) {
            Rectangle bounds = targetOptionBounds.get(i);
            boolean hovered = bounds.contains(dragMouseX, dragMouseY);
            TargetOption option = eligibleTargets.get(i);
            boolean selected = selectedIds.contains(option.instanceId());
            (hovered || selected ? ui.cardOver : ui.card).draw(
                batch, bounds.x, bounds.y, bounds.width, bounds.height);
            font.setColor(BattleUiAssets.TEXT);
            String prefix = multiple && !isOrderedPairMove(targetMenuSegment.getMove())
                ? (selected ? "[x] " : "[ ] ") : "";
            font.draw(batch, prefix + option.label(),
                bounds.x + scaled(8f), bounds.y + scaled(20f));
        }
        if (multiple) {
            int count = selectedIds.size();
            int cap = targetCap(targetMenuSegment.getMove());
            font.setColor(BattleUiAssets.YELLOW);
            String selectionLabel = isOrderedPairMove(targetMenuSegment.getMove())
                ? (count == 0 ? "SELECT ALLY" : "SELECT ENEMY") : "SELECT TARGETS";
            Rectangle topOption = targetOptionBounds.get(0);
            font.draw(batch, selectionLabel + "  " + count + "/" + cap,
                topOption.x, topOption.y + topOption.height + scaled(20f));
        }
    }

    private void layoutTargetMenu() {
        targetOptionBounds.clear();
        ActionSegmentView selectedView = viewFor(targetMenuSegment);
        float width = Math.min(scaled(240f),
            Math.max(scaled(140f), screenWidth - scaled(20f)));
        float rowHeight = scaled(30f);
        float x = selectedView == null ? dragMouseX : selectedView.getBounds().x;
        float y = selectedView == null
            ? dragMouseY : selectedView.getBounds().y + selectedView.getBounds().height + scaled(4f);
        x = clamp(x, scaled(10f),
            Math.max(scaled(10f), screenWidth - width - scaled(10f)));
        boolean multiple = isMultipleTargetMove(targetMenuSegment.getMove());
        List<TargetOption> eligibleTargets = eligibleTargetOptions(
            targetMenuSegment.getMove(), targetsOf(targetMenuSegment).size());
        float totalHeight = rowHeight * (eligibleTargets.size() + (multiple ? 1 : 0));
        float planningTop = UNIFIED_SECTION_HEIGHT;
        if (y + totalHeight > planningTop - scaled(10f)) {
            y = Math.max(scaled(10f),
                (selectedView == null ? y : selectedView.getBounds().y)
                    - totalHeight - scaled(4f));
        }
        float optionsY = y;
        for (int i = 0; i < eligibleTargets.size(); i++) {
            targetOptionBounds.add(new Rectangle(
                x, optionsY + (eligibleTargets.size() - i - 1) * rowHeight, width, rowHeight));
        }
    }

    private ActionSegmentView viewFor(ActionSegment segment) {
        for (ActionSegmentView view : offensiveViews) {
            if (view.getSegment() == segment) return view;
        }
        for (ActionSegmentView view : defensiveViews) {
            if (view.getSegment() == segment) return view;
        }
        return null;
    }

    private void openTargetMenu(ActionSegment segment) {
        if (segment == null || !requiresExplicitTargets(segment.getMove())) return;
        int selectedCount = targetsOf(segment).size();
        if (isOrderedPairMove(segment.getMove())
            && selectedCount == targetCap(segment.getMove())) {
            setTargets(segment, List.of());
            selectedCount = 0;
        }
        List<TargetOption> eligibleTargets = eligibleTargetOptions(
            segment.getMove(), selectedCount);
        if (eligibleTargets.isEmpty()) return;
        if (eligibleTargets.size() == 1 && !isOrderedPairMove(segment.getMove())) {
            setTargets(segment, List.of(new CombatantId(eligibleTargets.get(0).instanceId())));
            pendingTargetSelections.remove(segment);
            closeTargetMenu();
            return;
        }
        if (isMultipleTargetMove(segment.getMove())) pendingTargetSelections.add(segment);
        targetMenuSegment = segment;
        layoutTargetMenu();
    }

    private void closeTargetMenu() {
        targetMenuSegment = null;
        targetOptionBounds.clear();
    }

    private boolean handleTargetMenuClick() {
        if (targetMenuSegment == null) return false;
        layoutTargetMenu();
        List<TargetOption> eligibleTargets = eligibleTargetOptions(
            targetMenuSegment.getMove(), targetsOf(targetMenuSegment).size());
        for (int i = 0; i < targetOptionBounds.size(); i++) {
            if (targetOptionBounds.get(i).contains(dragMouseX, dragMouseY)) {
                boolean changed = chooseTarget(targetMenuSegment, eligibleTargets.get(i).instanceId());
                soundPlayer.accept(changed ? SoundCue.UI_CONFIRM : SoundCue.UI_DENIED);
                return true;
            }
        }
        if (isMultipleTargetMove(targetMenuSegment.getMove())
            && confirmTargetSelection(targetMenuSegment)) {
            soundPlayer.accept(SoundCue.UI_CONFIRM);
        }
        closeTargetMenu();
        return false;
    }

    private String targetSelectionError() {
        for (ActionSegment segment : plan.allSegments()) {
            if (isMultipleTargetMove(segment.getMove())) {
                if (pendingTargetSelections.contains(segment)) {
                    return "Finish selecting targets for '" + segment.getMove().getName() + "'";
                }
            }
            String countError = MoveTargetSelection.targetCountError(
                segment.getMove(), targetsOf(segment));
            if (countError != null) return countError;
            if (localBattleState != null && actorId != null) {
                BattleCombatant actor = localBattleState.combatant(new CombatantId(actorId));
                String relationshipError = MoveTargetSelection.validationError(
                    localBattleState, actor, segment.getMove(), targetsOf(segment));
                if (relationshipError != null) return relationshipError;
            }
        }
        return plan.missingTargetError();
    }

    private List<TargetOption> eligibleTargetOptions(Move move) {
        return eligibleTargetOptions(move, 0);
    }

    private List<TargetOption> eligibleTargetOptions(Move move, int selectionIndex) {
        MoveTargetSelection.Relationship relationship =
            MoveTargetSelection.requirements(move).relationshipAt(selectionIndex);
        if (relationship == MoveTargetSelection.Relationship.ALLY) {
            return allyOptions;
        }
        if (!CursedSpeechAbility.RETURN.equalsIgnoreCase(
            CursedSpeechAbility.commandMode(move))) {
            return targetOptions;
        }
        return targetOptions.stream().filter(TargetOption::summon).toList();
    }

    private void drawStat(Batch batch, BitmapFont font, float x, float y, float width, String label,
                          int current, int maximum, Color fillColor) {
        float height = scaled(29f);
        float edge = scaled(4f);
        ui.statPill.draw(batch, x, y, width, height);
        float fillRatio = maximum <= 0 ? 0f : Math.max(0f, Math.min(1f, current / (float) maximum));
        batch.setColor(fillColor);
        batch.draw(ui.pixel, x + edge, y + edge,
            (width - edge * 2f) * fillRatio, scaled(21f));
        batch.setColor(Color.WHITE);
        font.setColor(BattleUiAssets.MUTED);
        font.draw(batch, label, x + scaled(8f), y + scaled(19f));
        font.setColor(BattleUiAssets.TEXT);
        font.draw(batch, current + "/" + maximum,
            x + scaled(31f), y + scaled(19f));
    }

    private void drawTimelineLabel(Batch batch, BitmapFont font, TimelineBar bar, String label,
                                   com.badlogic.gdx.graphics.Texture icon, Color color) {
        Rectangle iconBounds = bar.getKind() == TimelineBar.Kind.OFFENSIVE
            ? offenseIconBounds : defenseIconBounds;
        Rectangle labelBounds = bar.getKind() == TimelineBar.Kind.OFFENSIVE
            ? offenseLabelBounds : defenseLabelBounds;
        batch.draw(icon, iconBounds.x, iconBounds.y, iconBounds.width, iconBounds.height);
        font.setColor(color);
        float scaleX = font.getData().scaleX;
        float scaleY = font.getData().scaleY;
        float available = bar.getBounds().x - labelBounds.x - 8f;
        float fit = Math.min(1f, available / new GlyphLayout(font, label).width);
        font.getData().setScale(scaleX * fit, scaleY * fit);
        font.draw(batch, label, labelBounds.x, labelBounds.y + 27f);
        font.getData().setScale(scaleX, scaleY);
    }

    private void drawDragAvatar(Batch batch, BitmapFont font) {
        Move move = draggedMove();
        if (move == null) return;

        TimelineBar bar = barFor(draggingBoard);
        Rectangle barBounds = bar.getBounds();
        boolean overTrack = barBounds.contains(dragMouseX, dragMouseY);
        float width = overTrack
            ? bar.segmentWidth(plan.effectiveApCost(move))
            : Math.max(scaled(132f), bar.segmentWidth(plan.effectiveApCost(move)));
        float height = overTrack ? barBounds.height - 12f : scaled(48f);
        float x = overTrack ? bar.segmentLeft(draggingTick) : dragMouseX - width / 2f;
        float y = overTrack ? barBounds.y + 6f : dragMouseY - height / 2f;
        if (!overTrack) {
            x = clamp(x, 0f, Math.max(0f, BattleCanvas.WIDTH - width));
            y = clamp(y, 0f, Math.max(0f, UNIFIED_SECTION_HEIGHT - height));
        }
        ActionSegmentView ghost = new ActionSegmentView(move, x, y, width, height);
        ghost.setHighlighted(true);
        ghost.draw(batch, font, ui);
    }

    private int ceCost(Move move) {
        Integer authoritativeCost = authoritativeCeCosts.get(move.getId());
        if (authoritativeCost != null) return authoritativeCost;
        return localCombatant != null
            ? localCombatant.computeMoveCeCost(move)
            : CeEfficiencyCalculator.computeActualCost(move, ceEfficiency, ceOutput, abilityFlags);
    }

    private boolean canReinforce(Move move) {
        return move != null && move.canBeReinforced()
            && (localCombatant == null || localCombatant.canReinforce(move));
    }

    private boolean isPaletteReinforced(Move move) {
        return canReinforce(move) && reinforcedPaletteMoves.contains(move.getId());
    }

    private int reinforcementCeCost(Move move) {
        if (!canReinforce(move)) return 0;
        return localCombatant != null
            ? localCombatant.computeReinforcementCeCost(move)
            : move.getReinforcementBaseCeCost();
    }

    private int plannedCeCost(Move move) {
        return Math.addExact(ceCost(move),
            isPaletteReinforced(move) ? reinforcementCeCost(move) : 0);
    }

    private static CodedAbilityState findMiraclesState(List<CodedAbilityState> states) {
        if (states == null) return null;
        return states.stream()
            .filter(state -> MiraclesAbility.KEY.equals(state.key()))
            .findFirst()
            .orElse(null);
    }

    private TimelineBar barFor(BattlePlan.Board board) {
        return board == BattlePlan.Board.OFFENSIVE ? offensiveBar : defensiveBar;
    }

    private Move draggedMove() {
        return draggingMove != null ? draggingMove : draggingSegment == null ? null : draggingSegment.getMove();
    }

    private boolean isDropTarget(BattlePlan.Board board) {
        return draggedMove() != null && draggingBoard == board && barFor(board).getBounds().contains(dragMouseX, dragMouseY);
    }

    /** Returns the first AP tick at or to the right of {@code startTick} that fits the move. */
    private int firstAvailableTick(BattlePlan.Board board, int startTick, Move move) {
        TimelineBar bar = barFor(board);
        int lastStart = effectiveLastStartTick(move, bar.getDotCount());
        for (int tick = startTick; tick <= lastStart; tick++) {
            if (plan.boardTimeline(board).isRangeFree(
                tick, tick + plan.effectiveApCost(move) - 1)) return tick;
        }
        return -1;
    }

    /** Returns the nearest AP tick at or to the left of {@code startTick} that fits the move. */
    private int lastAvailableTick(BattlePlan.Board board, int startTick, Move move) {
        int lastStart = effectiveLastStartTick(move, barFor(board).getDotCount());
        for (int tick = Math.min(startTick, lastStart); tick >= 1; tick--) {
            if (plan.boardTimeline(board).isRangeFree(
                tick, tick + plan.effectiveApCost(move) - 1)) return tick;
        }
        return -1;
    }

    static int lastStartTick(Move move, int gridLength) {
        long occupancyLastStart = (long) gridLength - move.getApCost() + 1L;
        long impactLastStart = (long) gridLength - move.getUnleashPoint() + 1L
            - move.getMaxHitDelayTicks();
        long lastStart = Math.min(occupancyLastStart, impactLastStart);
        return lastStart < 1L ? 0 : (int) Math.min(Integer.MAX_VALUE, lastStart);
    }

    private int effectiveLastStartTick(Move move, int gridLength) {
        long occupancyLastStart = (long) gridLength - plan.effectiveApCost(move) + 1L;
        long impactLastStart = (long) gridLength - plan.effectiveUnleashPoint(move) + 1L
            - move.getMaxHitDelayTicks();
        long lastStart = Math.min(occupancyLastStart, impactLastStart);
        return lastStart < 1L ? 0 : (int) Math.min(Integer.MAX_VALUE, lastStart);
    }

    public class PlanningInputProcessor extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (readOnly) return false;
            updatePointer(screenX, screenY);
            refresh();

            if (confirmed) {
                if (allowManualUnlock && button == Buttons.LEFT
                    && lockInBounds.contains(dragMouseX, dragMouseY)) {
                    unlock();
                    soundPlayer.accept(SoundCue.UI_CONFIRM);
                    return true;
                }
                return false;
            }

            if (button == Buttons.LEFT && handleTargetMenuClick()) return true;

            if (button == Buttons.RIGHT) {
                if (paletteViewportBounds.contains(dragMouseX, dragMouseY)) {
                    for (int i = 0; i < cards.size(); i++) {
                        MoveCardView card = cards.get(i);
                        if (!card.getBounds().contains(dragMouseX, dragMouseY)
                            || !canReinforce(card.getMove())) continue;
                        inspectedCard = i;
                        String id = card.getMove().getId();
                        if (!reinforcedPaletteMoves.add(id)) reinforcedPaletteMoves.remove(id);
                        card.setReinforced(isPaletteReinforced(card.getMove()));
                        lockError = null;
                        return true;
                    }
                }
                ActionSegmentView hit = hitSegment();
                if (hit == null || !plan.remove(hit.getSegment())) return false;
                targetLists.remove(hit.getSegment());
                pendingTargetSelections.remove(hit.getSegment());
                if (selectedSegment == hit.getSegment()) selectedSegment = null;
                if (targetMenuSegment == hit.getSegment()) closeTargetMenu();
                hoveredSegment = null;
                soundPlayer.accept(SoundCue.UI_PLAN_REMOVE);
                return true;
            }

            if (button != Buttons.LEFT) return false;
            pressedSegment = null;
            pressedBoard = null;
            pressMouseX = dragMouseX;
            pressMouseY = dragMouseY;

            if (paletteScrollMax > 0f && paletteScrollTrackBounds.contains(dragMouseX, dragMouseY)) {
                if (!paletteScrollThumbBounds.contains(dragMouseX, dragMouseY)) {
                    scrollPaletteThumbTo(dragMouseX);
                }
                draggingPaletteScrollbar = true;
                scrollbarDragStartX = dragMouseX;
                scrollbarDragStartOffset = paletteScrollX;
                return true;
            }

            if (lockInBounds.contains(dragMouseX, dragMouseY)) {
                lockError = targetSelectionError();
                if (lockError != null) {
                    soundPlayer.accept(SoundCue.UI_DENIED);
                    return true;
                }
                confirmed = true;
                onConfirm.run();
                return true;
            }

            if (paletteViewportBounds.contains(dragMouseX, dragMouseY)) {
                for (int i = 0; i < cards.size(); i++) {
                    MoveCardView card = cards.get(i);
                    if (!card.isDisabled() && card.getBounds().contains(dragMouseX, dragMouseY)) {
                        inspectedCard = i;
                        selectedSegment = null;
                        draggingMove = card.getMove();
                        draggingSegment = null;
                        draggingBoard = BattlePlan.boardFor(draggingMove);
                        clickingMoveCard = true;
                        dragSoundPlayed = false;
                        updateSnap();
                        return true;
                    }
                }
            }

            ActionSegmentView hit = hitSegment();
            if (hit != null) {
                selectedSegment = hit.getSegment();
                pressedSegment = hit.getSegment();
                pressedBoard = BattlePlan.boardFor(hit.getMove());
                return true;
            }

            selectedSegment = null;
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (readOnly) return false;
            if (draggingPaletteScrollbar) {
                updatePointer(screenX, screenY);
                float trackTravel = paletteScrollTrackBounds.width - paletteScrollThumbBounds.width;
                if (trackTravel > 0f) {
                    setPaletteScroll(scrollbarDragStartOffset
                        + (dragMouseX - scrollbarDragStartX) * paletteScrollMax / trackTravel);
                    updateHover();
                }
                return true;
            }
            if (confirmed || (draggedMove() == null && pressedSegment == null)) return false;
            updatePointer(screenX, screenY);
            float deltaX = dragMouseX - pressMouseX;
            float deltaY = dragMouseY - pressMouseY;
            if (!dragSoundPlayed
                && deltaX * deltaX + deltaY * deltaY < DRAG_THRESHOLD * DRAG_THRESHOLD) {
                return true;
            }
            if (pressedSegment != null) {
                startMoveDrag(pressedSegment, pressedBoard);
                pressedSegment = null;
                pressedBoard = null;
            }
            if (!dragSoundPlayed) {
                soundPlayer.accept(SoundCue.UI_PICKUP);
                dragSoundPlayed = true;
            }
            clickingMoveCard = false;
            updateSnap();
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (readOnly) return false;
            if (confirmed || button != Buttons.LEFT) return false;
            updatePointer(screenX, screenY);
            if (draggingPaletteScrollbar) {
                draggingPaletteScrollbar = false;
                updateHover();
                return true;
            }
            if (pressedSegment != null) {
                ActionSegment clicked = pressedSegment;
                pressedSegment = null;
                pressedBoard = null;
                openTargetMenu(clicked);
                return true;
            }
            if (draggedMove() == null) return false;
            updateSnap();

            Move move = draggedMove();
            boolean newPlacement = draggingSegment == null;
            List<CombatantId> targets = draggingSegment == null
                ? defaultTargets(move) : originalTargets;
            boolean droppedOnTimeline = barFor(draggingBoard).getBounds().contains(dragMouseX, dragMouseY);
            boolean reinforced = newPlacement ? isPaletteReinforced(move) : originalReinforced;
            int surcharge = newPlacement
                ? (reinforced ? reinforcementCeCost(move) : 0)
                : originalReinforcementCeCost;
            int totalCost = newPlacement ? plannedCeCost(move) : originalCeCost;
            ActionSegment placed = clickingMoveCard
                ? placeFirstFit(move, plannedCeCost(move), targets)
                : droppedOnTimeline && snapValid
                    ? place(move, draggingTick, totalCost, targets, reinforced, surcharge) : null;
            if (placed != null) {
                selectedSegment = placed;
                if (originalTargetsPending) pendingTargetSelections.add(placed);
                soundPlayer.accept(SoundCue.UI_PLAN_PLACE);
            } else if (droppedOnTimeline && draggingSegment != null) {
                // A cancelled relocation must never destroy an already planned move.
                selectedSegment = place(
                    draggingSegment.getMove(), originalTick, originalCeCost, originalTargets,
                    originalReinforced, originalReinforcementCeCost);
                if (originalTargetsPending && selectedSegment != null) {
                    pendingTargetSelections.add(selectedSegment);
                }
                soundPlayer.accept(SoundCue.UI_DENIED);
            } else if (!droppedOnTimeline) {
                selectedSegment = null;
                soundPlayer.accept(draggingSegment == null
                    ? SoundCue.UI_DENIED : SoundCue.UI_PLAN_REMOVE);
            } else {
                soundPlayer.accept(SoundCue.UI_DENIED);
            }
            clearDrag();
            if (placed != null && newPlacement && isMultipleTargetMove(placed.getMove())) {
                openTargetMenu(placed);
            }
            return true;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            updatePointer(screenX, screenY);
            refresh();
            updateHover();
            return hoveredCard >= 0 || hoveredSegment != null || lockHovered
                || moveDetailView.getBounds().contains(dragMouseX, dragMouseY);
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (readOnly) return false;
            if (confirmed || draggedMove() != null || paletteScrollMax <= 0f) return false;
            updatePointer(Gdx.input.getX(), Gdx.input.getY());
            if (!paletteBounds.contains(dragMouseX, dragMouseY)) return false;

            float amount = Math.abs(amountX) > Math.abs(amountY) ? amountX : amountY;
            if (amount == 0f) return false;
            float step = Math.min(
                cardWidth() + scaled(CARD_GAP),
                Math.max(scaled(48f), paletteViewportBounds.width * 0.3f));
            setPaletteScrollTarget(
                paletteScrollTargetX + amount * step * PALETTE_SCROLL_SPEED);
            updateHover();
            return true;
        }

        private void scrollPaletteThumbTo(float pointerX) {
            float travel = paletteScrollTrackBounds.width - paletteScrollThumbBounds.width;
            if (travel <= 0f) return;
            float thumbX = clamp(
                pointerX - paletteScrollThumbBounds.width / 2f,
                paletteScrollTrackBounds.x,
                paletteScrollTrackBounds.x + travel);
            setPaletteScroll(
                (thumbX - paletteScrollTrackBounds.x) * paletteScrollMax / travel);
        }

        private void startMoveDrag(ActionSegment segment, BattlePlan.Board board) {
            originalTick = segment.getStartTick();
            originalCeCost = segment.getActualCeCost();
            originalReinforced = segment.isReinforced();
            originalReinforcementCeCost = segment.getReinforcementCeCost();
            originalTargets = targetsOf(segment);
            originalTargetsPending = pendingTargetSelections.remove(segment);
            closeTargetMenu();
            plan.remove(segment);
            targetLists.remove(segment);
            draggingSegment = segment;
            draggingMove = null;
            draggingBoard = board;
            dragSoundPlayed = false;
            updateSnap();
        }

        private void clearDrag() {
            draggingMove = null;
            draggingSegment = null;
            draggingBoard = null;
            pressedSegment = null;
            pressedBoard = null;
            snapValid = false;
            clickingMoveCard = false;
            dragSoundPlayed = false;
            originalTargetsPending = false;
            updateHover();
        }

        private void updatePointer(int screenX, int screenY) {
            float physicalBottomY = physicalViewportHeight - screenY;
            dragMouseX = (screenX - viewportOffsetX) / viewportScale;
            dragMouseY = (physicalBottomY - viewportOffsetY) / viewportScale;
        }

        private void updateSnap() {
            Move move = draggedMove();
            if (move == null || draggingBoard == null) {
                snapValid = false;
                return;
            }
            TimelineBar bar = barFor(draggingBoard);
            Rectangle bounds = bar.getBounds();
            if (!bounds.contains(dragMouseX, dragMouseY)) {
                snapValid = false;
                return;
            }
            int requestedTick = bar.tickAtX(dragMouseX);
            int requestedEnd = requestedTick + plan.effectiveApCost(move) - 1;
            int availableTick;
            if (requestedTick <= effectiveLastStartTick(move, bar.getDotCount())
                && plan.boardTimeline(draggingBoard).isRangeFree(requestedTick, requestedEnd)) {
                availableTick = requestedTick;
            } else {
                int leftTick = lastAvailableTick(draggingBoard, requestedTick, move);
                int rightTick = firstAvailableTick(draggingBoard, requestedTick, move);
                if (leftTick < 0) {
                    availableTick = rightTick;
                } else if (rightTick < 0) {
                    availableTick = leftTick;
                } else {
                    float snapMidpoint = (leftTick + rightTick
                        + plan.effectiveApCost(move) - 1) / 2f;
                    availableTick = requestedTick <= snapMidpoint ? leftTick : rightTick;
                }
            }
            draggingTick = availableTick > 0 ? availableTick : requestedTick;
            snapValid = availableTick > 0
                && plan.canPlace(move, ceCost(move));
        }

        private void updateHover() {
            lockHovered = !confirmed && lockInBounds.contains(dragMouseX, dragMouseY);
            updateHoveredCard();
            ActionSegmentView hit = hitSegment();
            hoveredSegment = hit == null ? null : hit.getSegment();
        }

        private ActionSegmentView hitSegment() {
            for (ActionSegmentView view : offensiveViews) {
                if (view.getBounds().contains(dragMouseX, dragMouseY)) return view;
            }
            for (ActionSegmentView view : defensiveViews) {
                if (view.getBounds().contains(dragMouseX, dragMouseY)) return view;
            }
            return null;
        }
    }

    private void updateHoveredCard() {
        hoveredCard = -1;
        if (!paletteViewportBounds.contains(dragMouseX, dragMouseY)) return;
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).getBounds().contains(dragMouseX, dragMouseY)) {
                hoveredCard = i;
                inspectedCard = i;
                return;
            }
        }
    }

    private Move inspectedMove() {
        return inspectedCard >= 0 && inspectedCard < cards.size()
            ? cards.get(inspectedCard).getMove() : null;
    }

    private int indexOfMove(String moveId) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).getMove().getId().equals(moveId)) return i;
        }
        return -1;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
