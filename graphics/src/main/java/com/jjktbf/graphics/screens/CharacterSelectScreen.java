package com.jjktbf.graphics.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jjktbf.controller.BattleController;
import com.jjktbf.graphics.AssetLoader;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.audio.SoundCue;
import com.jjktbf.graphics.ui.StatusBar;
import com.jjktbf.graphics.ui.UiScaleSystem;
import com.jjktbf.graphics.ui.battle.BattleUiAssets;
import com.jjktbf.graphics.ui.editor.ScrollAxes;
import com.jjktbf.graphics.ui.battle.ActionSegmentView;
import com.jjktbf.graphics.ui.battle.MoveCardView;
import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityRepository;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterRepository;
import com.jjktbf.model.character.CombatStats;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SlotBudgetEnforcer;
import com.jjktbf.model.character.StatKey;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MovePool;
import com.jjktbf.model.move.MoveRepository;
import com.jjktbf.model.progression.TechniqueMasteryResolver;
import com.jjktbf.model.technique.InnateTechniqueData;
import com.jjktbf.model.technique.TechniqueRepository;
import com.jjktbf.model.text.ContentNameTokens;
import com.jjktbf.model.text.MoveDescriptionVariables;
import com.jjktbf.multiplayer.protocol.MatchCharacterSelectionRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/** Master-detail character selection screen for player and CPU choices. */
public class CharacterSelectScreen implements Screen {

    private static final String CHAR_DATA_DIR = "data/characters";
    private static final String MOVE_DATA_DIR = "data/moves";
    private static final String ABILITY_DATA_DIR = "data/abilities";
    private static final String TECHNIQUE_DATA_DIR = "data/techniques";
    private static final float ROW_HEIGHT = 66f;
    private static final float MOVE_PANEL_PADDING = 15f;
    private static final float MOVE_PANEL_HEADER_HEIGHT = 36f;
    private static final float HEADER_HEIGHT = 87f;
    /** The roster panel occupies one fifth of the logical screen width. */
    private static final float ROSTER_WIDTH_RATIO = 0.20f;
    private static final float PROFILE_PADDING = 24f;
    private static final float PROFILE_TITLE_GAP = 60f;
    private static final float PROFILE_SUMMARY_HEIGHT = 580f;
    private static final float PROFILE_SECTION_GAP = 16f;
    private static final float PROFILE_SPRITE_SIZE = 444f;
    private static final float PROFILE_BAR_HEIGHT = 34f;
    private static final float FULL_MOVE_CARD_WIDTH = 324f;
    private static final float FULL_MOVE_CARD_HEIGHT = 302f;
    private static final float FULL_MOVE_CARD_SCALE = 1.35f;
    private static final float MOVE_SET_SEGMENT_WIDTH = 225f;
    private static final float MOVE_SET_SEGMENT_HEIGHT = 81f;
    private static final float MOVE_SET_SEGMENT_GAP = 12f;
    private static final float MOVE_SET_ACTION_HEIGHT = 39f;
    private static final float MOVE_SET_ACTION_GAP = 12f;
    private static final float LEARNED_DRAWER_PADDING = 18f;
    private static final float LEARNED_DRAWER_HEADER_HEIGHT = 51f;
    private static final float LEARNED_DRAWER_SCROLLBAR_WIDTH = 12f;
    private static final float LEARNED_DRAWER_CARD_GAP = 15f;
    private static final float MOVE_DRAG_THRESHOLD = 8f;
    private static final float STATS_FONT_SCALE = 1.20f;
    private static final float BST_FONT_SCALE = 1.60f;
    private static final Color STAT_MIN_COLOR = new Color(0.920f, 0.220f, 0.180f, 1f);
    private static final Color STAT_MID_COLOR = new Color(1f, 1f, 0f, 1f);
    private static final Color STAT_MAX_COLOR = new Color(0.260f, 0.820f, 0.360f, 1f);
    private static final Color STAT_TRACK_COLOR = new Color(0.770f, 0.790f, 0.720f, 1f);
    private static final String RANDOM_ROW_LABEL = "Random";
    private static final String[] STAT_LABELS = {
        "Vitality", "Strength", "Durability", "Speed", "Combat Ability",
        "CE Reserves", "CE Efficiency", "CE Output", "Jujutsu Skill", "CT Mastery"
    };

    private enum Phase { PLAYER, CPU }

    /**
     * Roster format for the battle being set up. ONE_V_ONE picks one fighter
     * per side (the legacy flow); larger formats fill their full ordered roster. Set on entry via
     * {@link #prepare(BattleFormat)}.
     */
    private com.jjktbf.model.combat.BattleFormat format =
        com.jjktbf.model.combat.BattleFormat.ONE_V_ONE;
    private com.jjktbf.model.combat.BattleStatMode statMode =
        com.jjktbf.model.combat.BattleStatMode.STANDARD;
    private BattleController.ControlMode controlMode =
        BattleController.ControlMode.PLAYER_VS_AI;
    private boolean multiplayerSelection;
    private Consumer<MatchCharacterSelectionRequest> onMultiplayerSelected;
    private Runnable onSelectionExit;

    private final JJKGame game;
    private final AssetLoader assets;
    private final SpriteBatch batch;
    private final Viewport viewport;
    private final Vector2 pointerCoordinates = new Vector2();
    private final Rectangle scissorBounds = new Rectangle();
    private boolean scissorPushed;
    private final CharacterRepository charRepo;
    private final MoveRepository moveRepo;
    private final AbilityRepository abilityRepo;
    private final TechniqueRepository techniqueRepo;
    private final com.jjktbf.model.weapon.CursedToolRepository cursedToolRepo;
    /** Guards against double-dispose of native batch resources. */
    private boolean disposed;
    private final Rectangle headerBounds = new Rectangle();
    private final Rectangle listBounds = new Rectangle();
    private final Rectangle rosterViewportBounds = new Rectangle();
    private final Rectangle detailBounds = new Rectangle();
    private final Rectangle summaryBounds = new Rectangle();
    private final Rectangle techniqueBounds = new Rectangle();
    private final Rectangle moveSetPanelBounds = new Rectangle();
    private final Rectangle moveSetViewportBounds = new Rectangle();
    private final Rectangle learnedDrawerBounds = new Rectangle();
    private final Rectangle learnedDrawerViewportBounds = new Rectangle();
    private final Rectangle learnedDrawerToggleBounds = new Rectangle();
    private final Rectangle recommendedMoveSetBounds = new Rectangle();
    private final Rectangle customizeMoveSetBounds = new Rectangle();
    private final Rectangle randomizeMoveSetBounds = new Rectangle();
    private final Color statBarFillColor = new Color();
    private final InputAdapter inputAdapter = new InputAdapter() {
        @Override
        public boolean scrolled(float amountX, float amountY) {
            float[] dominant = ScrollAxes.dominant(amountX, amountY);
            if (scrollLearnedDrawer(dominant[1])) return true;
            if (scrollMoveSet(dominant[0] != 0f ? dominant[0] : dominant[1])) return true;
            if (scrollRoster(dominant[1])) return true;
            return false;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            Vector2 world = worldPointer(screenX, screenY);
            return handleMoveSetTouchDown(world.x, world.y, button);
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            Vector2 world = worldPointer(screenX, screenY);
            return handleMoveSetTouchDragged(world.x, world.y);
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            Vector2 world = worldPointer(screenX, screenY);
            return handleMoveSetTouchUp(world.x, world.y, button);
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            Vector2 world = worldPointer(screenX, screenY);
            movePointerX = world.x;
            movePointerY = world.y;
            hoverRowAt(movePointerX, movePointerY);
            return learnedDrawerExpanded && learnedDrawerBounds.contains(movePointerX, movePointerY)
                || moveSetPanelBounds.contains(movePointerX, movePointerY);
        }
    };

    private List<CharacterData> characters = List.of();
    private CharacterData movesCharacter;
    private List<Move> learnedMoves = List.of();
    private List<Ability> profileAbilities = List.of();
    private List<Move> profileTechniqueMoves = List.of();
    private List<Ability> profileTechniqueAbilities = List.of();
    private InnateTechniqueData profileTechnique;
    private BattleCombatant profileCombatant;
    private com.jjktbf.model.character.Character profileCharacter;
    private List<Integer> learnedMoveCeCosts = List.of();
    private int cursorIndex;
    private final Random random = new Random();
    private Phase phase = Phase.PLAYER;
    /**
     * Picks for the current side, in slot order. For ONE_V_ONE each side fills
     * one slot (playerChoice is kept as a convenience for the 1-slot case); for
     * TWO_V_TWO each side fills two. A character may not be picked twice within
     * the same side.
     */
    private final java.util.List<CharacterData> playerPicks = new java.util.ArrayList<>();
    private final java.util.List<CharacterData> cpuPicks = new java.util.ArrayList<>();
    private final java.util.List<List<String>> playerMoveSets = new java.util.ArrayList<>();
    private final java.util.List<List<String>> cpuMoveSets = new java.util.ArrayList<>();
    private final Map<String, List<String>> moveSetDrafts = new LinkedHashMap<>();
    private CharacterData playerChoice;
    private String loadError;
    private String learnedMovesError;
    private String moveSetSaveError;
    private String moveSetRequiredWarning;
    private float rosterScrollOffset;
    private float rosterScrollMax;
    private List<MoveCardView> learnedDrawerCards = List.of();
    private List<ActionSegmentView> moveSetViews = List.of();
    private boolean learnedDrawerExpanded;
    private float learnedDrawerScrollOffset;
    private float learnedDrawerScrollMax;
    private float moveSetScrollOffset;
    private float moveSetScrollMax;
    private Move pressedLearnedMove;
    private boolean draggingLearnedMove;
    private float movePressX;
    private float movePressY;
    private float movePointerX;
    private float movePointerY;
    private boolean moveUiConsumedPointer;
    /**
     * Set on entry so the first frame ignores input. Guards against a stale
     * ENTER poll leaking across the event-driven transition from the main menu.
     */
    private boolean inputSuspended;

    public CharacterSelectScreen(JJKGame game, AssetLoader assets) {
        this.game = game;
        this.assets = assets;
        onSelectionExit = game::showMainMenu;
        batch = new SpriteBatch();
        viewport = UiScaleSystem.newGameplayViewport();
        charRepo = new CharacterRepository(CHAR_DATA_DIR);
        moveRepo = new MoveRepository(MOVE_DATA_DIR);
        abilityRepo = new AbilityRepository(ABILITY_DATA_DIR);
        techniqueRepo = new TechniqueRepository(TECHNIQUE_DATA_DIR);
        cursedToolRepo = new com.jjktbf.model.weapon.CursedToolRepository("data/tools");
    }

    /**
     * Set the roster format before the screen is shown. Defaults to ONE_V_ONE
     * so callers that never call this (and the legacy no-arg
     * {@link JJKGame#showCharacterSelect()}) keep the original 1-pick behaviour.
     */
    public void prepare(com.jjktbf.model.combat.BattleFormat format) {
        prepare(format, BattleController.ControlMode.PLAYER_VS_AI);
    }

    public void prepare(
        com.jjktbf.model.combat.BattleFormat format,
        BattleController.ControlMode controlMode
    ) {
        prepare(
            format,
            com.jjktbf.model.combat.BattleStatMode.STANDARD,
            controlMode);
    }

    public void prepare(
        com.jjktbf.model.combat.BattleFormat format,
        com.jjktbf.model.combat.BattleStatMode statMode,
        BattleController.ControlMode controlMode
    ) {
        this.format = format != null ? format
            : com.jjktbf.model.combat.BattleFormat.ONE_V_ONE;
        this.statMode = statMode != null ? statMode
            : com.jjktbf.model.combat.BattleStatMode.STANDARD;
        this.controlMode = controlMode != null ? controlMode
            : BattleController.ControlMode.PLAYER_VS_AI;
        multiplayerSelection = false;
        onMultiplayerSelected = null;
        onSelectionExit = game::showMainMenu;
    }

    public void prepareMultiplayer(
        com.jjktbf.model.combat.BattleFormat format,
        com.jjktbf.model.combat.BattleStatMode statMode,
        Consumer<MatchCharacterSelectionRequest> onSelected,
        Runnable onExit
    ) {
        this.format = format != null ? format
            : com.jjktbf.model.combat.BattleFormat.ONE_V_ONE;
        this.statMode = statMode != null ? statMode
            : com.jjktbf.model.combat.BattleStatMode.STANDARD;
        this.controlMode = BattleController.ControlMode.PLAYER_VS_AI;
        multiplayerSelection = true;
        onMultiplayerSelected = Objects.requireNonNull(onSelected, "onSelected");
        onSelectionExit = Objects.requireNonNull(onExit, "onExit");
    }

    @Override
    public void show() {
        // Keyboard input is polled, while this adapter receives mouse-wheel events
        // for the learned-moves panel. Taking ownership also prevents the previous
        // screen's Stage from handling keyboard input while this screen is visible.
        Gdx.input.setInputProcessor(inputAdapter);
        // The main menu switches here in response to an event-driven ENTER. That
        // event is consumed by Scene2D, but the underlying "just pressed" flag is
        // only cleared at the next frame boundary — so this screen's first render()
        // would otherwise see a phantom ENTER and auto-confirm the first row.
        // Ignore input for one frame until the stale flag has drained.
        inputSuspended = true;
        phase = Phase.PLAYER;
        cursorIndex = 0;
        playerChoice = null;
        playerPicks.clear();
        cpuPicks.clear();
        playerMoveSets.clear();
        cpuMoveSets.clear();
        moveSetDrafts.clear();
        loadError = null;
        movesCharacter = null;
        learnedMoves = List.of();
        profileAbilities = List.of();
        profileTechniqueMoves = List.of();
        profileTechniqueAbilities = List.of();
        profileTechnique = null;
        profileCombatant = null;
        profileCharacter = null;
        learnedMoveCeCosts = List.of();
        learnedDrawerCards = List.of();
        moveSetViews = List.of();
        learnedDrawerExpanded = false;
        learnedDrawerScrollOffset = 0f;
        learnedDrawerScrollMax = 0f;
        moveSetScrollOffset = 0f;
        moveSetScrollMax = 0f;
        clearLearnedMoveDrag();
        learnedMovesError = null;
        moveSetSaveError = null;
        moveSetRequiredWarning = null;
        resetRosterScroll();
        resetMoveScroll();
        try {
            moveRepo.load();
            abilityRepo.load();
            techniqueRepo.load();
            charRepo.load();
            cursedToolRepo.load();
            // Battles resolve combatant sprites through JJKGame's shared
            // repository, which is otherwise only loaded at startup — reload it
            // here so editor saves reach battles without an app restart.
            game.reloadMultiplayerRoster();
            // Only directly-selectable definitions appear in the fighter roster.
            // Hidden definitions (e.g. summon-only shikigami) are filtered out.
            characters = charRepo.getAll().stream()
                .filter(CharacterData::effectiveSelectable)
                .toList();
            if (characters.isEmpty()) {
                loadError = "No characters found. Use Character Editor to create one.";
            }
            setCursor(0);
        } catch (IOException e) {
            loadError = "Failed to load data: " + e.getMessage();
        }
    }

    @Override
    public void render(float delta) {
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        clearScreen();
        layout(viewport.getWorldWidth(), viewport.getWorldHeight());
        handleInput();
        BitmapFont font = assets.gameplayFontSmall;
        float scaleX = font.getData().scaleX;
        float scaleY = font.getData().scaleY;
        float textScale = UiScaleSystem.bodyTextScale(
            viewport.getScreenWidth() / viewport.getWorldWidth(), font.getCapHeight());
        font.getData().setScale(scaleX * textScale, scaleY * textScale);
        try {
            draw();
        } finally {
            font.getData().setScale(scaleX, scaleY);
        }
    }

    @Override public void resize(int width, int height) {
        viewport.update(width, height, true);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        layout(viewport.getWorldWidth(), viewport.getWorldHeight());
    }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        batch.dispose();
    }

    private void handleInput() {
        // Skip the first frame after entering: the main menu's event-driven
        // ENTER is consumed by Scene2D, but its polled "just pressed" flag only
        // clears at the next frame boundary, so this frame would otherwise see a
        // phantom ENTER. By ignoring input once, the flag drains naturally.
        if (inputSuspended) {
            inputSuspended = false;
            return;
        }
        if (loadError != null) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                game.audio().play(SoundCue.UI_BACK);
                onSelectionExit.run();
            }
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            setCursor((cursorIndex - 1 + rosterRowCount()) % rosterRowCount());
            revealRosterCursor();
            resetMoveScroll();
            game.audio().play(SoundCue.UI_NAVIGATE);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            setCursor((cursorIndex + 1) % rosterRowCount());
            revealRosterCursor();
            resetMoveScroll();
            game.audio().play(SoundCue.UI_NAVIGATE);
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && !moveUiConsumedPointer) {
            Vector2 world = worldPointer(Gdx.input.getX(), Gdx.input.getY());
            selectRowAt(world.x, world.y);
        }
        moveUiConsumedPointer = false;
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) confirmSelection();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.audio().play(SoundCue.UI_BACK);
            if (!undoLastPick()) {
                onSelectionExit.run();
            }
        }
    }

    private void selectRowAt(float x, float y) {
        int index = rosterRowAt(x, y);
        if (index < 0) return;
        if (index != cursorIndex) {
            setCursor(index);
            resetMoveScroll();
        }
        confirmSelection();
    }

    private void hoverRowAt(float x, float y) {
        int index = rosterRowAt(x, y);
        if (index < 0 || index == cursorIndex) return;
        setCursor(index);
    }

    private int rosterRowAt(float x, float y) {
        return rosterRowAt(rosterViewportBounds, rosterScrollOffset, rosterRowCount(), x, y);
    }

    static int rosterRowAt(Rectangle rosterViewport, float scrollOffset, int rowCount, float x, float y) {
        if (!rosterViewport.contains(x, y)) return -1;
        float firstRowTop = rosterViewport.y + rosterViewport.height;
        int index = (int) ((firstRowTop + scrollOffset - y) / ROW_HEIGHT);
        return index >= 0 && index < rowCount ? index : -1;
    }

    /** Roster rows shown in the list: the pinned Random row plus every fighter. */
    private int rosterRowCount() {
        return characters.size() + 1;
    }

    private boolean cursorOnRandomRow() {
        return cursorIndex == 0;
    }

    private CharacterData cursorCharacter() {
        return characters.get(cursorIndex - 1);
    }

    /** Fighter shown on the detail page, or null while the Random row is highlighted. */
    private CharacterData detailCharacter() {
        return cursorOnRandomRow() ? null : cursorCharacter();
    }

    /** Move the cursor; the Random row shows no profile, so hide its drawer. */
    private void setCursor(int index) {
        cursorIndex = index;
        if (cursorOnRandomRow()) learnedDrawerExpanded = false;
    }

    private CharacterData pickRandomCharacter() {
        return randomCandidate(characters, currentPicks(), random);
    }

    /**
     * Battle-only move set for a random pick whose authored move set is empty:
     * every slot-free learned move plus random learned moves filling each pool's
     * slot budget. Never persisted — it applies to this battle's pick only.
     */
    private List<String> randomBattleMoveSet() {
        if (profileCharacter == null) return List.of();
        return randomMoveSetIds(
            profileCharacter.getLearnedMoves(),
            profileCharacter::consumesMoveSetSlot,
            SlotBudgetEnforcer.slotBudgetFor(
                profileCharacter.getCombatStats(), MovePool.COMBAT_ARTS),
            SlotBudgetEnforcer.slotBudgetFor(
                profileCharacter.getCombatStats(), MovePool.JUJUTSU_ARTS),
            random);
    }

    /**
     * Random equipped-move ids within the per-pool slot budgets. Slot-free
     * moves are always equipped; chosen ids retain learned order.
     */
    static List<String> randomMoveSetIds(
        List<Move> learned,
        java.util.function.Predicate<Move> consumesSlot,
        int combatArtsSlots,
        int jujutsuArtsSlots,
        Random random
    ) {
        Map<MovePool, Integer> remaining = new EnumMap<>(MovePool.class);
        remaining.put(MovePool.COMBAT_ARTS, Math.max(0, combatArtsSlots));
        remaining.put(MovePool.JUJUTSU_ARTS, Math.max(0, jujutsuArtsSlots));
        Set<String> chosen = new HashSet<>();
        List<Move> shuffled = new ArrayList<>(learned);
        java.util.Collections.shuffle(shuffled, random);
        for (Move move : shuffled) {
            if (!consumesSlot.test(move)) {
                chosen.add(move.getId());
            } else {
                MovePool pool = move.getPool();
                int left = remaining.getOrDefault(pool, 0);
                if (left > 0) {
                    remaining.put(pool, left - 1);
                    chosen.add(move.getId());
                }
            }
        }
        return learned.stream().map(Move::getId).filter(chosen::contains).toList();
    }

    /**
     * Uniformly random roster fighter among those not already picked for the
     * side being filled, or null when every fighter is already on that side.
     */
    static CharacterData randomCandidate(
        java.util.List<CharacterData> roster,
        java.util.List<CharacterData> sidePicks,
        Random random
    ) {
        java.util.List<CharacterData> candidates = new ArrayList<>(roster.size());
        for (CharacterData candidate : roster) {
            if (sidePicks.stream().noneMatch(picked -> picked.id.equals(candidate.id))) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    private void confirmSelection() {
        boolean randomPick = cursorOnRandomRow();
        CharacterData picked = randomPick ? pickRandomCharacter() : cursorCharacter();
        if (picked == null) {
            game.audio().play(SoundCue.UI_DENIED);
            return;
        }
        learnedMovesFor(picked);
        if (profileCharacter == null) {
            game.audio().play(SoundCue.UI_DENIED);
            return;
        }
        java.util.List<CharacterData> currentPicks = currentPicks();
        // Disallow picking the same fighter twice within one side.
        if (currentPicks.stream().anyMatch(c -> c.id.equals(picked.id))) {
            game.audio().play(SoundCue.UI_DENIED);
            return;
        }
        List<String> selectedMoveSet = List.copyOf(moveSetIdsFor(picked));
        if (randomPick && selectedMoveSet.isEmpty()) {
            // Authored move set never configured: equip a battle-only random set.
            selectedMoveSet = randomBattleMoveSet();
        }
        if (selectedMoveSet.isEmpty()) {
            // Fighters ship with no move set: the player must choose one on
            // the detail page before the fighter can be picked.
            moveSetRequiredWarning = "CHOOSE A MOVE SET FIRST";
            game.audio().play(SoundCue.UI_DENIED);
            return;
        }
        moveSetRequiredWarning = null;
        game.audio().play(SoundCue.UI_CONFIRM);
        currentPicks.add(picked);
        currentMoveSets().add(selectedMoveSet);
        if (phase == Phase.PLAYER) {
            playerChoice = picked; // convenience for the 1-slot header display
        }

        if (currentPicks.size() < format.fightersPerSide()) {
            // More slots to fill on this side.
            setCursor(0);
            resetRosterScroll();
            resetMoveScroll();
            return;
        }

        // This side is full. Move to the other side, or start the battle.
        if (multiplayerSelection) {
            inputSuspended = true;
            onMultiplayerSelected.accept(new MatchCharacterSelectionRequest(
                playerPicks.stream().map(character -> character.id).toList(),
                playerMoveSets));
        } else if (phase == Phase.PLAYER) {
            phase = Phase.CPU;
            setCursor(0);
            resetRosterScroll();
            resetMoveScroll();
        } else {
            startConfiguredBattle();
        }
    }

    /** Picks accumulated so far for the side currently being filled. */
    private java.util.List<CharacterData> currentPicks() {
        return phase == Phase.PLAYER ? playerPicks : cpuPicks;
    }

    private java.util.List<List<String>> currentMoveSets() {
        return phase == Phase.PLAYER ? playerMoveSets : cpuMoveSets;
    }

    /**
     * Number of slots already filled across both sides. Used to step back on ESC
     * and to decide whether a side has more slots to fill.
     */
    private int totalPicksFilled() {
        return playerPicks.size() + cpuPicks.size();
    }

    /**
     * Pop the most recent pick (ESC-to-undo). Returns true if a pick was undone,
     * false if there is nothing to undo (the caller then exits the screen).
     */
    private boolean undoLastPick() {
        if (!cpuPicks.isEmpty()) {
            cpuPicks.remove(cpuPicks.size() - 1);
            cpuMoveSets.remove(cpuMoveSets.size() - 1);
            setCursor(0);
            resetRosterScroll();
            resetMoveScroll();
            return true;
        }
        if (!playerPicks.isEmpty()) {
            playerPicks.remove(playerPicks.size() - 1);
            playerMoveSets.remove(playerMoveSets.size() - 1);
            playerChoice = playerPicks.isEmpty() ? null : playerPicks.get(playerPicks.size() - 1);
            phase = Phase.PLAYER;
            setCursor(0);
            resetRosterScroll();
            resetMoveScroll();
            return true;
        }
        return false;
    }

    private void startConfiguredBattle() {
        if (format.fightersPerSide() > 1) {
            game.startTeamBattle(
                new java.util.ArrayList<>(playerPicks),
                new java.util.ArrayList<>(playerMoveSets),
                new java.util.ArrayList<>(cpuPicks),
                new java.util.ArrayList<>(cpuMoveSets),
                moveRepo, abilityRepo, techniqueRepo, controlMode, statMode, format);
        } else {
            // ONE_V_ONE (or any single-fighter format): use the legacy entry point.
            game.startBattle(
                playerPicks.get(0), playerMoveSets.get(0),
                cpuPicks.get(0), cpuMoveSets.get(0),
                moveRepo, abilityRepo, techniqueRepo, controlMode, statMode);
        }
    }

    private void clearScreen() {
        Gdx.gl.glClearColor(0.804f, 0.863f, 0.980f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private void layout(float width, float height) {
        layoutPanelBounds(width, height, headerBounds, listBounds, rosterViewportBounds, detailBounds);
        rosterScrollMax = Math.max(0f,
            rosterRowCount() * ROW_HEIGHT - rosterViewportBounds.height);
        rosterScrollOffset = clamp(rosterScrollOffset, 0f, rosterScrollMax);
        revealRosterCursor();
        layoutProfileBounds(detailBounds, summaryBounds, techniqueBounds, moveSetPanelBounds);
        layoutLearnedDrawer(width, height);
    }

    static void layoutPanelBounds(
        float width, float height, Rectangle header, Rectangle roster,
        Rectangle rosterViewport, Rectangle detail
    ) {
        float margin = Math.min(36f, Math.max(20f, width * 0.035f));
        header.set(margin, height - margin - HEADER_HEIGHT, width - margin * 2f, HEADER_HEIGHT);
        float contentTop = header.y - 14f;
        roster.set(margin, margin, rosterWidth(width), contentTop - margin);
        rosterViewport.set(roster.x + 8f, roster.y + 8f,
            Math.max(0f, roster.width - 16f), Math.max(0f, roster.height - 77f));
        float detailX = roster.x + roster.width + 14f;
        detail.set(detailX, margin, width - detailX - margin, contentTop - margin);
    }

    static void layoutProfileBounds(
        Rectangle detail, Rectangle summary, Rectangle technique, Rectangle moves
    ) {
        // The gameplay viewport keeps the full reference layout usable; extra
        // logical height goes to technique details, not an enlarged portrait.
        float x = detail.x + PROFILE_PADDING;
        float width = Math.max(0f, detail.width - PROFILE_PADDING * 2f);
        float top = detail.y + detail.height - PROFILE_PADDING - PROFILE_TITLE_GAP;
        float bottom = detail.y + PROFILE_PADDING;
        float movesHeight = Math.min(moveSetPanelHeight(), Math.max(0f, top - bottom));
        moves.set(x, bottom, width, movesHeight);
        float infoBottom = bottom + movesHeight + PROFILE_SECTION_GAP;
        float summaryHeight = Math.min(PROFILE_SUMMARY_HEIGHT, Math.max(0f, top - infoBottom));
        summary.set(x, top - summaryHeight, width, summaryHeight);
        technique.set(x, infoBottom, width,
            Math.max(0f, summary.y - PROFILE_SECTION_GAP - infoBottom));
    }

    private void draw() {
        batch.begin();
        if (loadError != null) {
            drawError();
            batch.end();
            return;
        }

        drawHeader();
        drawRoster();
        CharacterData detail = detailCharacter();
        if (detail != null) {
            drawCharacterPage(detail);
            drawLearnedDrawer(detail);
        } else {
            drawRandomPlaceholder();
        }
        drawLearnedMoveDragAvatar();
        batch.end();
    }

    /** Blank detail page shown while the Random row is highlighted — no profile. */
    private void drawRandomPlaceholder() {
        clearMoveSetActionBounds();
        assets.battleUi.card.draw(batch, detailBounds.x, detailBounds.y,
            detailBounds.width, detailBounds.height);
        String mark = "?";
        assets.gameplayFontXLarge.setColor(BattleUiAssets.TEXT);
        assets.gameplayFontXLarge.draw(batch, mark,
            detailBounds.x + (detailBounds.width - textWidth(assets.gameplayFontXLarge, mark)) / 2f,
            detailBounds.y + detailBounds.height * 0.60f);
        String caption = "RANDOM FIGHTER";
        assets.gameplayFontMedium.setColor(new Color(0.720f, 0.800f, 0.950f, 1f));
        assets.gameplayFontMedium.draw(batch, caption,
            detailBounds.x + (detailBounds.width - textWidth(assets.gameplayFontMedium, caption)) / 2f,
            detailBounds.y + detailBounds.height * 0.60f - 60f);
    }

    private void drawError() {
        assets.battleUi.header.draw(batch, headerBounds.x, headerBounds.y,
            headerBounds.width, headerBounds.height);
        assets.gameplayFontSmall.setColor(Color.RED);
        assets.gameplayFontSmall.draw(batch, loadError, headerBounds.x + 18f,
            headerBounds.y + 51f);
    }

    private void drawHeader() {
        assets.battleUi.header.draw(batch, headerBounds.x, headerBounds.y,
            headerBounds.width, headerBounds.height);
        int slot = currentPicks().size() + 1; // 1-indexed slot being filled now
        int slots = format.fightersPerSide();
        String opposingSide = controlMode == BattleController.ControlMode.HUMAN_CONTROLS_BOTH_TEAMS
            ? "ENEMY" : "CPU";
        String title = phase == Phase.PLAYER
            ? (slots == 1 ? "SELECT YOUR CHARACTER"
                          : "SELECT PLAYER FIGHTER " + slot + "/" + slots)
            : (slots == 1 ? "SELECT " + opposingSide + " CHARACTER"
                         : "SELECT " + opposingSide + " FIGHTER " + slot + "/" + slots);
        assets.gameplayFontMedium.setColor(BattleUiAssets.YELLOW);
        assets.gameplayFontMedium.draw(batch, title, headerBounds.x + 18f,
            headerBounds.y + 58.5f);
        assets.gameplayFontSmall.setColor(new Color(0.720f, 0.800f, 0.950f, 1f));
        String state = !multiplayerSelection && phase == Phase.CPU && !playerPicks.isEmpty()
            ? picksSummary("PLAYER", playerPicks) + "  |  " + statMode + "  |  ENTER: START"
            : "UP/DOWN: SELECT  |  ENTER: CONFIRM  |  LEARNED MOVES: CUSTOMIZE  |  "
                + statMode;
        if (format.hasReserves()) {
            state = "ROSTER ORDER: FIRST 3 ACTIVE / LAST 3 RESERVE  |  " + statMode;
        }
        float stateRight = format.hasReserves()
            ? headerBounds.x + headerBounds.width * 0.47f - 12f : learnedDrawerToggleBounds.x - 12f;
        assets.gameplayFontSmall.draw(batch,
            fitOrEllipsize(assets.gameplayFontSmall, state, stateRight - headerBounds.x - 20f), headerBounds.x + 20f,
            headerBounds.y + 25.5f);
        if (format.hasReserves()) drawTeamCompositionPanel();
    }

    /** Compact six-slot party tray: field fighters above, reserves below. */
    private void drawTeamCompositionPanel() {
        List<CharacterData> picks = currentPicks();
        Rectangle tray = teamTrayBounds(headerBounds, learnedDrawerToggleBounds);
        float panelX = tray.x;
        float panelWidth = tray.width;
        float gap = 7f;
        float slotWidth = (panelWidth - gap * 2f) / 3f;
        float slotHeight = (headerBounds.height - gap * 3f) / 2f;
        for (int slot = 0; slot < format.fightersPerSide(); slot++) {
            int column = slot % 3;
            int row = slot / 3;
            float x = panelX + column * (slotWidth + gap);
            float y = headerBounds.y + headerBounds.height - gap
                - (row + 1) * slotHeight - row * gap;
            boolean activeSlot = slot < format.activeFightersPerSide();
            batch.setColor(activeSlot
                ? new Color(0.44f, 0.78f, 0.96f, 1f)
                : new Color(1f, 0.78f, 0.28f, 1f));
            assets.battleUi.card.draw(batch, x, y, slotWidth, slotHeight);
            batch.setColor(Color.WHITE);
            String position = (activeSlot ? "A" : "R")
                + (activeSlot ? slot + 1 : slot - format.activeFightersPerSide() + 1);
            String name = slot < picks.size() ? picks.get(slot).name : "EMPTY";
            BitmapFont font = assets.gameplayFontSmall;
            font.setColor(activeSlot ? new Color(0.12f, 0.30f, 0.55f, 1f)
                : new Color(0.48f, 0.27f, 0.04f, 1f));
            String label = fitOrEllipsize(font, position + "  " + name, slotWidth - 12f);
            font.draw(batch, label, x + 6f, y + slotHeight * 0.64f);
        }
    }

    static Rectangle teamTrayBounds(Rectangle header, Rectangle drawerToggle) {
        float x = header.x + header.width * 0.47f;
        float right = Math.min(header.x + header.width - 12f, drawerToggle.x - 12f);
        return new Rectangle(x, header.y, Math.max(0f, right - x), header.height);
    }

    private static String picksSummary(String label, java.util.List<CharacterData> picks) {
        StringBuilder sb = new StringBuilder(label).append(": ");
        for (int i = 0; i < picks.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(picks.get(i).name);
        }
        return sb.toString();
    }

    private void drawRoster() {
        assets.battleUi.palette.draw(batch, listBounds.x, listBounds.y, listBounds.width, listBounds.height);
        assets.gameplayFontSmall.setColor(new Color(0.720f, 0.800f, 0.950f, 1f));
        assets.gameplayFontSmall.draw(batch, "AVAILABLE CHARACTERS", listBounds.x + 14f,
            listBounds.y + listBounds.height - 22.5f);

        float rowHeight = rowHeight();
        float rowTop = listBounds.y + listBounds.height - 69f;
        java.util.List<CharacterData> sidePicks = currentPicks();
        beginClip(rosterViewportBounds);
        for (int row = 0; row < rosterRowCount(); row++) {
            float rowY = rowTop - (row + 1) * rowHeight + rosterScrollOffset;
            if (row == cursorIndex) {
                assets.battleUi.cardOver.draw(batch, listBounds.x + 8f, rowY,
                    listBounds.width - 16f, rowHeight - 6f);
            }
            if (row == 0) {
                // Pinned Random row; confirming it picks a random fighter.
                assets.gameplayFontMedium.setColor(row == cursorIndex
                    ? BattleUiAssets.TEXT
                    : BattleUiAssets.YELLOW);
                String label = fitOrEllipsize(
                    assets.gameplayFontMedium, RANDOM_ROW_LABEL, listBounds.width - 36f);
                assets.gameplayFontMedium.draw(batch, label, listBounds.x + 18f,
                    rowY + 40.5f);
                continue;
            }
            CharacterData character = characters.get(row - 1);
            // Dim a character already picked on the side currently being filled.
            boolean alreadyPicked = sidePicks.stream().anyMatch(c -> c.id.equals(character.id));
            BitmapFont rosterFont = assets.gameplayFontMedium;
            rosterFont.setColor(row == cursorIndex
                ? BattleUiAssets.TEXT
                : (alreadyPicked ? new Color(0.55f, 0.55f, 0.55f, 1f) : Color.WHITE));
            String rosterName = fitOrEllipsize(
                assets.gameplayFontMedium, character.name, listBounds.width - 36f);
            rosterFont.draw(batch, rosterName, listBounds.x + 18f,
                rowY + 40.5f);
        }
        // Pick badges (P1/P2 on player side, C1/C2 on cpu side) next to names.
        drawPickBadges(rowTop, "P", playerPicks);
        drawPickBadges(rowTop,
            controlMode == BattleController.ControlMode.HUMAN_CONTROLS_BOTH_TEAMS ? "E" : "C",
            cpuPicks);
        endClip();
    }

    /** Draw a side's slot badges (P1/P2 or C1/C2) next to picked roster rows. */
    private void drawPickBadges(float rowTop, String prefix, java.util.List<CharacterData> picks) {
        float rowHeight = rowHeight();
        for (int slot = 0; slot < picks.size(); slot++) {
            CharacterData picked = picks.get(slot);
            int row = characters.stream().filter(c -> c.id.equals(picked.id))
                .mapToInt(characters::indexOf).findFirst().orElse(-1);
            if (row < 0) continue;
            // Character rows sit one row below the pinned Random row.
            float rowY = rowTop - (row + 2) * rowHeight + rosterScrollOffset;
            assets.gameplayFontSmall.setColor(BattleUiAssets.YELLOW);
            assets.gameplayFontSmall.draw(batch, prefix + (slot + 1), listBounds.x + 14f,
                rowY + 21f);
        }
    }

    private void drawCharacterPage(CharacterData character) {
        assets.battleUi.card.draw(batch, detailBounds.x, detailBounds.y,
            detailBounds.width, detailBounds.height);

        float innerLeft = detailBounds.x + PROFILE_PADDING;
        float innerTop = detailBounds.y + detailBounds.height - PROFILE_PADDING;
        BitmapFont nameFont = assets.gameplayFontXLarge;
        nameFont.setColor(BattleUiAssets.TEXT);
        drawBold(nameFont, fitOrEllipsize(nameFont, character.name,
            detailBounds.width - PROFILE_PADDING * 2f), innerLeft, innerTop);
        List<Move> moves = learnedMovesFor(character);
        if (summaryBounds.height > 0f) {
            beginClip(summaryBounds);
            drawProfileSummary(character, summaryBounds.x,
                summaryBounds.y + summaryBounds.height, summaryBounds.y);
            endClip();
        }
        if (techniqueBounds.height > 0f) {
            beginClip(techniqueBounds);
            drawTechniqueSection(character, techniqueBounds.x, techniqueBounds.width,
                techniqueBounds.y + techniqueBounds.height, techniqueBounds.y);
            endClip();
        }
        drawMoveSet(character, moves, moveSetPanelBounds.x, moveSetPanelBounds.y,
            moveSetPanelBounds.width, moveSetPanelBounds.height);
    }

    private void drawProfileSummary(
        CharacterData character,
        float x,
        float top,
        float bottom
    ) {
        assets.battleUi.palette.draw(batch, x, top - 460f, 460f, 460f);
        Texture sprite = assets.characterSprite(character.spriteAsset, assets.playerSprite);
        batch.draw(sprite, x + 8f, top - 452f, PROFILE_SPRITE_SIZE, PROFILE_SPRITE_SIZE);
        CombatStats fallbackStats = new CombatStats(character.toCharacterStats(), statMode);
        int maximumHp = profileCombatant != null
            ? profileCombatant.getMaxHp() : fallbackStats.getMaxHp();
        int maximumCe = profileCombatant != null
            ? profileCombatant.getMaxCursedEnergy() : fallbackStats.getMaxCursedEnergy();
        StatusBar hp = new StatusBar(
            "HP", new Color(STAT_MAX_COLOR), 1.5f);
        hp.setBounds(x, top - 518f, 460f, PROFILE_BAR_HEIGHT);
        hp.setValues(maximumHp, maximumHp);
        hp.draw(batch, assets.gameplayFontSmall, assets.battleUi, true);
        StatusBar ce = new StatusBar(
            "CE", new Color(0.220f, 0.500f, 0.940f, 1f), 1.5f);
        ce.setBounds(x, top - 560f, 460f, PROFILE_BAR_HEIGHT);
        ce.setValues(maximumCe, maximumCe);
        ce.draw(batch, assets.gameplayFontSmall, assets.battleUi, true);

        drawStats(character, x + 580f, top, bottom);
    }

    private void drawStats(
        CharacterData character,
        float x,
        float top,
        float bottom
    ) {
        int[] values = displayStatValues(character);
        float extraWidth = Math.max(0f, summaryBounds.width - 1914f);
        float barOffset = 250f;
        float barWidth = 880f + extraWidth;
        float valueRight = 1165f + extraWidth;
        float descriptionWidth = 1260f + extraWidth;
        float rowHeight = 40f;
        float barHeight = 20f;
        BitmapFont font = assets.gameplayFontSmall;
        float bstBaseline = top - 24f;
        if (bstBaseline - 22.5f < bottom) return;
        drawBst(character, font, x, bstBaseline);
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        font.getData().setScale(
            originalScaleX * STATS_FONT_SCALE,
            originalScaleY * STATS_FONT_SCALE);
        try {
            for (int i = 0; i < values.length; i++) {
                float baseline = top - 78f - i * rowHeight;
                if (baseline - rowHeight < bottom) break;
                String value = String.valueOf(values[i]);
                font.setColor(BattleUiAssets.TEXT);
                drawBold(font, STAT_LABELS[i], x, baseline);
                drawStatBar(
                    values[i], x + barOffset, baseline - barHeight + 2f, barWidth, barHeight);
                drawBold(font, value, Math.max(x + barOffset + barWidth + 12f,
                    x + valueRight - textWidth(font, value)), baseline);
            }

            float descriptionTop = top - 475f;
            if (descriptionTop - 32f <= bottom) return;
            font.setColor(BattleUiAssets.MUTED);
            font.draw(batch, "CHARACTER PROFILE", x, descriptionTop);
            drawWrappedText(
                displayDescription(character),
                x,
                descriptionTop - 32f,
                descriptionWidth,
                bottom,
                BattleUiAssets.TEXT,
                30f);
        } finally {
            font.getData().setScale(originalScaleX, originalScaleY);
        }
    }

    private void drawBst(
        CharacterData character,
        BitmapFont font,
        float x,
        float baseline
    ) {
        String total = String.valueOf(baseStatTotal(character));
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        font.getData().setScale(
            originalScaleX * BST_FONT_SCALE,
            originalScaleY * BST_FONT_SCALE);
        font.setColor(BattleUiAssets.TEXT);
        drawBold(font, "BST:", x, baseline);
        drawBold(font, total, x + Math.max(82f, textWidth(font, "BST:") + 16f), baseline);
        font.getData().setScale(originalScaleX, originalScaleY);
    }

    private void drawStatBar(
        int value,
        float x,
        float y,
        float width,
        float height
    ) {
        float edge = 2f;
        batch.setColor(BattleUiAssets.INK);
        batch.draw(assets.battleUi.pixel, x, y, width, height);
        batch.setColor(STAT_TRACK_COLOR);
        batch.draw(assets.battleUi.pixel,
            x + edge, y + edge, width - edge * 2f, height - edge * 2f);
        float innerWidth = Math.max(0f, width - edge * 2f);
        float fillWidth = innerWidth * statBarFillRatio(value);
        if (fillWidth > 0f) {
            batch.setColor(statBarColor(value, statBarFillColor));
            batch.draw(assets.battleUi.pixel,
                x + edge, y + edge, fillWidth, height - edge * 2f);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawTechniqueSection(
        CharacterData character,
        float x,
        float width,
        float top,
        float bottom
    ) {
        float columnWidth = (width - 24f) / 2f;
        float rightColumnOffset = columnWidth + 24f;
        float dividerOffset = columnWidth + 12f;
        float listWidth = (columnWidth - 60f) / 3f;
        float secondMovesOffset = listWidth + 20f;
        float abilitiesOffset = secondMovesOffset * 2f;

        String techniqueName = hasCursedTechnique(character)
            ? character.innateTechniqueName : "NONE";
        BitmapFont titleFont = assets.gameplayFontMedium;
        titleFont.setColor(BattleUiAssets.TEXT);
        String title = "CURSED TECHNIQUE: " + techniqueName;
        titleFont.draw(batch, fitOrEllipsize(titleFont, title, width), x, top);

        float contentTop = top - 36f;
        if (contentTop - 24f <= bottom) return;
        float rightX = x + rightColumnOffset;
        float dividerX = x + dividerOffset;
        batch.setColor(new Color(0.560f, 0.640f, 0.800f, 1f));
        batch.draw(assets.battleUi.pixel,
            dividerX, bottom, 2f, Math.max(0f, contentTop - bottom));
        batch.setColor(Color.WHITE);

        assets.gameplayFontSmall.setColor(BattleUiAssets.MUTED);
        assets.gameplayFontSmall.draw(batch, "TECHNIQUE DESCRIPTION", x, contentTop);
        drawWrappedText(
            profileTechniqueDescription(character),
            x,
            contentTop - 24f,
            columnWidth,
            bottom,
            BattleUiAssets.TEXT);

        List<String> moveNames = profileTechniqueMoves.stream().map(Move::getName).toList();
        int secondMovesStart = (moveNames.size() + 1) / 2;
        List<String> firstMoves = moveNames.subList(0, secondMovesStart);
        List<String> secondMoves = moveNames.subList(secondMovesStart, moveNames.size());
        float secondMovesX = rightX + secondMovesOffset;
        float abilitiesX = rightX + abilitiesOffset;
        assets.gameplayFontSmall.setColor(BattleUiAssets.MUTED);
        assets.gameplayFontSmall.draw(batch, "MOVES", rightX, contentTop);
        if (!secondMoves.isEmpty()) {
            assets.gameplayFontSmall.draw(batch, "CONT.", secondMovesX, contentTop);
        }
        assets.gameplayFontSmall.draw(batch, "ABILITIES", abilitiesX, contentTop);
        drawPointList(
            firstMoves,
            rightX,
            contentTop - 24f,
            listWidth,
            bottom);
        if (!secondMoves.isEmpty()) {
            drawPointList(
                secondMoves,
                secondMovesX,
                contentTop - 24f,
                listWidth,
                bottom);
        }
        drawPointList(
            profileTechniqueAbilities.stream().map(Ability::getName).toList(),
            abilitiesX,
            contentTop - 24f,
            listWidth,
            bottom);
    }

    private String profileTechniqueDescription(CharacterData character) {
        if (profileTechnique != null
            && profileTechnique.description != null
            && !profileTechnique.description.isBlank()) {
            return displayDescription(profileTechnique.description);
        }
        return hasCursedTechnique(character)
            ? "No technique description available."
            : "This character does not possess an innate cursed technique.";
    }

    private void drawWrappedText(
        String value,
        float x,
        float top,
        float width,
        float bottom,
        Color color
    ) {
        drawWrappedText(value, x, top, width, bottom, color, 22.5f);
    }

    private void drawWrappedText(
        String value,
        float x,
        float top,
        float width,
        float bottom,
        Color color,
        float lineStep
    ) {
        String text = value == null || value.isBlank() ? "-" : value;
        lineStep = Math.max(lineStep, assets.gameplayFontSmall.getCapHeight() * 1.8f);
        assets.gameplayFontSmall.setColor(color);
        float baseline = top;
        for (String line : wrap(assets.gameplayFontSmall, text, width)) {
            if (baseline < bottom + 4f) break;
            assets.gameplayFontSmall.draw(batch, line, x, baseline);
            baseline -= lineStep;
        }
    }

    private void drawPointList(
        List<String> values,
        float x,
        float top,
        float width,
        float bottom
    ) {
        if (values.isEmpty()) {
            if (top < bottom + 4f) return;
            assets.gameplayFontSmall.setColor(BattleUiAssets.MUTED);
            assets.gameplayFontSmall.draw(batch, "None learned.", x, top);
            return;
        }
        float lineStep = Math.max(22.5f, assets.gameplayFontSmall.getCapHeight() * 1.8f);
        int capacity = Math.max(0,
            1 + (int) Math.floor((top - bottom - 4f) / lineStep));
        if (capacity == 0) return;
        int visibleCount = Math.min(values.size(), capacity);
        boolean overflow = values.size() > capacity;
        float baseline = top;
        for (int i = 0; i < visibleCount; i++) {
            String value = overflow && i == visibleCount - 1
                ? "+" + (values.size() - visibleCount + 1) + " more"
                : values.get(i);
            batch.setColor(BattleUiAssets.YELLOW);
            batch.draw(assets.battleUi.pixel, x, baseline - 10f, 6f, 6f);
            batch.setColor(Color.WHITE);
            assets.gameplayFontSmall.setColor(BattleUiAssets.TEXT);
            assets.gameplayFontSmall.draw(batch,
                fitOrEllipsize(assets.gameplayFontSmall, value, width - 15f), x + 15f, baseline);
            baseline -= lineStep;
        }
    }

    /**
     * Character-page stat values in {@link #STAT_LABELS} order with passive
     * ability modifiers applied, so the preview matches the stats the character
     * enters battle with (e.g. Nine-to-Five Binding Vow's 0.8 multipliers).
     * Falls back to raw authored values when the profile combatant could not
     * be built.
     */
    private int[] displayStatValues(CharacterData character) {
        learnedMovesFor(character);
        return statDisplayValues(character,
            profileCombatant != null ? profileCombatant.getEffectiveStats() : null);
    }

    /**
     * Map stats to the display order. {@code effective} may be null (raw
     * fallback). StatKey declaration order matches STAT_LABELS order.
     */
    static int[] statDisplayValues(CharacterData character, CharacterStats effective) {
        if (effective == null) {
            return new int[] {
                character.vitality, character.strength, character.durability, character.speed,
                character.combatAbility, character.cursedEnergyReserves,
                character.cursedEnergyEfficiency, character.cursedEnergyOutput,
                character.jujutsuSkill, character.cursedTechniqueMastery
            };
        }
        StatKey[] keys = StatKey.values();
        int[] values = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            values[i] = keys[i].get(effective);
        }
        return values;
    }

    private int baseStatTotal(CharacterData character) {
        int total = 0;
        for (int value : displayStatValues(character)) total += value;
        return total;
    }

    /**
     * Display copy for a character blurb: resolves {@code *move:id*} /
     * {@code *ability:id*} reference tokens against the loaded repositories so
     * roster text never shows raw tokens or stale names.
     */
    private String displayDescription(CharacterData character) {
        return displayDescription(character.description);
    }

    private String displayDescription(String description) {
        return ContentNameTokens.resolve(description,
            CharacterData.descriptionNameLookup(moveRepo, abilityRepo));
    }

    private List<Move> learnedMovesFor(CharacterData character) {
        if (movesCharacter == character) return learnedMoves;

        movesCharacter = character;
        resetMoveScroll();
        learnedMovesError = null;
        moveSetSaveError = null;
        moveSetRequiredWarning = null;
        profileAbilities = List.of();
        profileTechniqueMoves = List.of();
        profileTechniqueAbilities = List.of();
        profileTechnique = hasCursedTechnique(character)
            ? techniqueRepo.findByName(character.innateTechniqueName).orElse(null)
            : null;
        profileCombatant = null;
        profileCharacter = null;
        learnedMoveCeCosts = List.of();
        learnedDrawerCards = List.of();
        try {
            com.jjktbf.model.character.Character resolved = resolveProfileCharacter(
                character, moveRepo, abilityRepo, techniqueRepo, cursedToolRepo);
            profileCharacter = resolved;
            learnedMoves = resolved.getLearnedMoves();
            profileAbilities = resolved.getAbilities();
            try {
                profileCombatant = new BattleCombatant(
                    resolved, profileAbilities, statMode);
            } catch (RuntimeException ignored) {
                profileCombatant = null;
            }

            List<Integer> costs = new ArrayList<>(learnedMoves.size());
            for (Move move : learnedMoves) {
                costs.add(profileCombatant != null
                    ? profileCombatant.computeMoveCeCost(move) : move.getBaseCeCost());
            }
            learnedMoveCeCosts = List.copyOf(costs);
            rebuildLearnedDrawerCards();

            if (hasCursedTechnique(character)) {
                profileTechniqueMoves = learnedMoves.stream()
                    .filter(move -> character.innateTechniqueName.equalsIgnoreCase(
                        move.getRequiredTechniqueId()))
                    .toList();
                profileTechniqueAbilities = profileAbilities.stream()
                    .filter(ability -> "TECHNIQUE".equalsIgnoreCase(ability.getSourceType()))
                    .filter(ability -> character.innateTechniqueName.equalsIgnoreCase(
                        ability.getSourceValue()))
                    .toList();
            }
        } catch (Exception e) {
            learnedMoves = List.of();
            learnedMovesError = e.getMessage();
        }
        return learnedMoves;
    }

    static com.jjktbf.model.character.Character resolveProfileCharacter(
        CharacterData character,
        MoveRepository moveRepo,
        AbilityRepository abilityRepo,
        TechniqueRepository techniqueRepo,
        com.jjktbf.model.weapon.CursedToolRepository cursedToolRepo
    ) {
        try {
            return character.toCharacter(
                moveRepo, abilityRepo, techniqueRepo, cursedToolRepo);
        } catch (IllegalArgumentException savedMoveSetFailure) {
            if (character.moveSetIds == null) throw savedMoveSetFailure;
            try {
                return character.toCharacter(
                    moveRepo, abilityRepo, techniqueRepo, cursedToolRepo, List.of());
            } catch (IllegalArgumentException profileFailure) {
                profileFailure.addSuppressed(savedMoveSetFailure);
                throw profileFailure;
            }
        }
    }

    private void rebuildLearnedDrawerCards() {
        float cardWidth = learnedDrawerCardWidth();
        float cardHeight = learnedDrawerCardHeight();
        float geometryScale = FULL_MOVE_CARD_SCALE;
        List<MoveCardView> cards = new ArrayList<>(learnedMoves.size());
        for (Move move : learnedMoves) {
            MoveCardView card = new MoveCardView(
                move, 0f, 0f, geometryScale, cardWidth, cardHeight, 5,
                1f);
            if (profileCombatant != null) {
                card.setDisplayDescription(MoveDescriptionVariables.resolve(
                    move, TechniqueMasteryResolver.masteryOf(profileCombatant)));
            }
            cards.add(card);
        }
        learnedDrawerCards = List.copyOf(cards);
    }

    private List<String> moveSetIdsFor(CharacterData character) {
        learnedMovesFor(character);
        if (profileCharacter == null) return List.of();
        return moveSetDrafts.computeIfAbsent(character.id, ignored ->
            seedMoveSetDraft(character, learnedMoves,
                profileCharacter.getRecommendedMoveSet()));
    }

    /**
     * Draft move-set ids for a fighter whose detail page is opened for the first
     * time this visit. A saved non-empty choice remains authoritative. Otherwise
     * the fighter's deterministic legal recommendation is used, so setup never
     * requires authoring a player or CPU loadout before play.
     */
    static List<String> seedMoveSetDraft(
        CharacterData character,
        List<Move> learnedMoves,
        List<Move> recommendedMoves
    ) {
        List<String> draft = new ArrayList<>();
        Set<String> learnedIds = new HashSet<>();
        for (Move move : learnedMoves) learnedIds.add(move.getId());
        if (character.moveSetIds != null) {
            for (String moveId : character.moveSetIds) {
                if (moveId != null && learnedIds.contains(moveId)) draft.add(moveId);
            }
        }
        if (!draft.isEmpty()) return draft;
        if (recommendedMoves == null) return draft;
        for (Move move : recommendedMoves) {
            if (move != null && learnedIds.contains(move.getId())) draft.add(move.getId());
        }
        return draft;
    }

    private List<String> recommendedBattleMoveSet() {
        if (profileCharacter == null) return List.of();
        return profileCharacter.getRecommendedMoveSet().stream().map(Move::getId).toList();
    }

    private boolean replaceBattleMoveSetDraft(CharacterData character, List<String> moveIds) {
        learnedMovesFor(character);
        if (profileCharacter == null) return false;
        try {
            com.jjktbf.model.character.Character configured =
                profileCharacter.withMoveSet(moveIds);
            List<String> canonicalIds = configured.getMoveSet().stream()
                .map(Move::getId)
                .toList();
            moveSetDrafts.put(character.id, new ArrayList<>(canonicalIds));
            profileCharacter = configured;
            moveSetRequiredWarning = null;
            moveSetScrollOffset = 0f;
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private List<Move> moveSetMovesFor(CharacterData character) {
        Map<String, Move> learnedById = new LinkedHashMap<>();
        for (Move move : learnedMovesFor(character)) learnedById.put(move.getId(), move);
        List<Move> selected = new ArrayList<>();
        for (String moveId : moveSetIdsFor(character)) {
            Move move = learnedById.get(moveId);
            if (move != null) selected.add(move);
        }
        return selected;
    }

    private boolean updateMoveSet(CharacterData character, List<String> proposedIds) {
        learnedMovesFor(character);
        if (profileCharacter == null) return false;
        try {
            com.jjktbf.model.character.Character configured =
                profileCharacter.withMoveSet(proposedIds);
            List<String> canonicalIds = configured.getMoveSet().stream()
                .map(Move::getId)
                .toList();
            List<String> current = moveSetIdsFor(character);
            if (current.equals(canonicalIds)) return false;
            List<String> previousSavedIds = character.moveSetIds == null
                ? null : new ArrayList<>(character.moveSetIds);
            character.moveSetIds = new ArrayList<>(canonicalIds);
            try {
                charRepo.update(character);
                charRepo.save();
            } catch (Exception exception) {
                character.moveSetIds = previousSavedIds;
                try {
                    charRepo.update(character);
                } catch (RuntimeException ignored) {
                    // The in-memory repository was concurrently replaced; the disk save failed.
                }
                moveSetSaveError = "MOVE SET SAVE FAILED";
                System.err.println("[WARN] Could not save move set for character '"
                    + character.name + "': " + exception.getMessage());
                return false;
            }
            moveSetSaveError = null;
            moveSetRequiredWarning = null;
            profileCharacter = configured;
            moveSetDrafts.put(character.id, new ArrayList<>(canonicalIds));
            moveSetScrollOffset = clamp(moveSetScrollOffset, 0f, moveSetScrollMax);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean addMoveToSet(CharacterData character, Move move) {
        if (move == null) return false;
        List<String> proposed = new ArrayList<>(moveSetIdsFor(character));
        if (proposed.contains(move.getId())) return false;
        proposed.add(move.getId());
        return updateMoveSet(character, proposed);
    }

    private boolean removeMoveFromSet(CharacterData character, int index) {
        List<String> proposed = new ArrayList<>(moveSetIdsFor(character));
        if (index < 0 || index >= proposed.size()) return false;
        proposed.remove(index);
        return updateMoveSet(character, proposed);
    }

    private void drawMoveSet(
        CharacterData character,
        List<Move> learned,
        float x,
        float y,
        float width,
        float height
    ) {
        moveSetPanelBounds.set(x, y, width, Math.max(0f, height));
        moveSetViewportBounds.set(0f, 0f, 0f, 0f);
        moveSetViews = List.of();
        moveSetScrollMax = 0f;
        clearMoveSetActionBounds();
        if (height <= 0f) return;

        assets.battleUi.palette.draw(batch, x, y, width, height);
        float padding = movePanelPadding();
        List<Move> selected = moveSetMovesFor(character);
        String title = selected.isEmpty() ? "MOVE SET" : "MOVE SET (" + selected.size() + ")";
        assets.gameplayFontSmall.setColor(new Color(0.720f, 0.800f, 0.950f, 1f));
        assets.gameplayFontSmall.draw(batch, title, x + padding,
            y + height - 12f);
        drawMoveSetCounters(selected, x, y, width, height, padding);

        if (learnedMovesError != null) {
            assets.gameplayFontSmall.setColor(Color.RED);
            assets.gameplayFontSmall.draw(batch, "MOVE DATA UNAVAILABLE", x + padding, y + height / 2f);
            return;
        }
        if (moveSetSaveError != null) {
            assets.gameplayFontSmall.setColor(Color.RED);
            assets.gameplayFontSmall.draw(batch, moveSetSaveError,
                x + padding, y + height / 2f);
        }
        if (moveSetRequiredWarning != null) {
            assets.gameplayFontSmall.setColor(Color.RED);
            assets.gameplayFontSmall.draw(batch, moveSetRequiredWarning,
                x + padding, y + height / 2f - 22f);
        }

        layoutMoveSetBounds(moveSetPanelBounds, moveSetViewportBounds,
            recommendedMoveSetBounds, customizeMoveSetBounds, randomizeMoveSetBounds);
        drawMoveSetActions();
        float viewportWidth = moveSetViewportBounds.width;
        float viewportHeight = moveSetViewportBounds.height;
        if (viewportWidth <= 0f || viewportHeight <= 0f) return;
        if (selected.isEmpty()) {
            assets.gameplayFontSmall.setColor(BattleUiAssets.MUTED);
            assets.gameplayFontSmall.draw(batch,
                "Open Learned Moves and click or drag a card here.",
                moveSetViewportBounds.x,
                moveSetViewportBounds.y + moveSetViewportBounds.height / 2f);
            return;
        }

        float segmentWidth = moveSetSegmentWidth();
        float gap = moveSetSegmentGap();
        float contentWidth = selected.size() * segmentWidth
            + Math.max(0, selected.size() - 1) * gap;
        moveSetScrollMax = Math.max(0f, contentWidth - viewportWidth);
        moveSetScrollOffset = clamp(moveSetScrollOffset, 0f, moveSetScrollMax);
        List<ActionSegmentView> views = new ArrayList<>(selected.size());
        beginClip(moveSetViewportBounds);
        for (int index = 0; index < selected.size(); index++) {
            float segmentX = moveSetViewportBounds.x
                + index * (segmentWidth + gap) - moveSetScrollOffset;
            float segmentY = moveSetViewportBounds.y
                + (moveSetViewportBounds.height - moveSetSegmentHeight()) / 2f;
            ActionSegmentView view = new ActionSegmentView(
                selected.get(index), segmentX, segmentY,
                segmentWidth, moveSetSegmentHeight());
            view.setHighlighted(view.getBounds().contains(movePointerX, movePointerY));
            view.draw(batch, assets.gameplayFontSmall, assets.battleUi);
            views.add(view);
        }
        if (draggingLearnedMove && isVisibleMoveSetDropTarget(movePointerX, movePointerY)) {
            int insertion = learnedOrderInsertionIndex(pressedLearnedMove, selected);
            float markerX = moveSetViewportBounds.x
                + insertion * (segmentWidth + gap) - moveSetScrollOffset - gap / 2f;
            batch.setColor(BattleUiAssets.YELLOW);
            batch.draw(assets.battleUi.pixel, markerX, moveSetViewportBounds.y,
                4f, moveSetViewportBounds.height);
            batch.setColor(Color.WHITE);
        }
        endClip();
        moveSetViews = List.copyOf(views);

        if (moveSetScrollMax > 0f) {
            float trackHeight = 7f;
            float trackY = y + Math.max(1f, padding / 4f);
            batch.setColor(BattleUiAssets.INK);
            batch.draw(assets.battleUi.pixel, moveSetViewportBounds.x, trackY,
                moveSetViewportBounds.width, trackHeight);
            float thumbWidth = Math.max(42f,
                moveSetViewportBounds.width * moveSetViewportBounds.width / contentWidth);
            float travel = moveSetViewportBounds.width - thumbWidth;
            float progress = moveSetScrollMax == 0f ? 0f
                : moveSetScrollOffset / moveSetScrollMax;
            batch.setColor(BattleUiAssets.YELLOW);
            batch.draw(assets.battleUi.pixel,
                moveSetViewportBounds.x + travel * progress, trackY,
                thumbWidth, trackHeight);
            batch.setColor(Color.WHITE);
        }
    }

    private void drawMoveSetCounters(
        List<Move> selected,
        float x,
        float y,
        float width,
        float height,
        float padding
    ) {
        if (profileCharacter == null) return;
        Map<MovePool, Integer> usage = new EnumMap<>(MovePool.class);
        for (Move move : selected) {
            if (profileCharacter.consumesMoveSetSlot(move)) {
                usage.merge(move.getPool(), 1, Integer::sum);
            }
        }
        int combatLimit = SlotBudgetEnforcer.slotBudgetFor(
            profileCharacter.getCombatStats(), MovePool.COMBAT_ARTS);
        int jujutsuLimit = SlotBudgetEnforcer.slotBudgetFor(
            profileCharacter.getCombatStats(), MovePool.JUJUTSU_ARTS);
        String counters = "COMBAT ARTS " + usage.getOrDefault(MovePool.COMBAT_ARTS, 0)
            + "/" + combatLimit + "  |  JUJUTSU ARTS "
            + usage.getOrDefault(MovePool.JUJUTSU_ARTS, 0) + "/" + jujutsuLimit;
        assets.gameplayFontSmall.setColor(new Color(0.720f, 0.800f, 0.950f, 1f));
        assets.gameplayFontSmall.draw(batch, counters,
            x + width - padding - textWidth(assets.gameplayFontSmall, counters),
            y + height - 12f);
    }

    static void layoutMoveSetBounds(
        Rectangle panel, Rectangle moveViewport, Rectangle recommended,
        Rectangle customize, Rectangle randomize
    ) {
        float viewportHeight = Math.min(MOVE_SET_SEGMENT_HEIGHT, Math.max(0f,
            panel.height - MOVE_PANEL_HEADER_HEIGHT - MOVE_PANEL_PADDING * 2f
                - MOVE_SET_ACTION_HEIGHT - MOVE_SET_ACTION_GAP));
        float width = Math.max(0f, panel.width - MOVE_PANEL_PADDING * 2f);
        float x = panel.x + MOVE_PANEL_PADDING;
        moveViewport.set(x, panel.y + MOVE_PANEL_PADDING, width, viewportHeight);
        float y = moveViewport.y + viewportHeight + MOVE_SET_ACTION_GAP;
        float gap = 9f;
        float available = Math.max(0f, width - gap * 2f);
        float recommendedWidth = available * 0.40f;
        float customizeWidth = available * 0.30f;
        float randomizeWidth = Math.max(0f, available - recommendedWidth - customizeWidth);
        recommended.set(x, y, recommendedWidth, MOVE_SET_ACTION_HEIGHT);
        customize.set(x + recommendedWidth + gap, y, customizeWidth, MOVE_SET_ACTION_HEIGHT);
        randomize.set(customize.x + customizeWidth + gap, y, randomizeWidth, MOVE_SET_ACTION_HEIGHT);
    }

    private void drawMoveSetActions() {
        drawMoveSetAction(recommendedMoveSetBounds, "PLAY RECOMMENDED");
        drawMoveSetAction(customizeMoveSetBounds,
            learnedDrawerExpanded ? "CLOSE CUSTOMIZE" : "CUSTOMIZE");
        drawMoveSetAction(randomizeMoveSetBounds, "RANDOMIZE");
    }

    private void drawMoveSetAction(Rectangle bounds, String label) {
        boolean hovered = bounds.contains(movePointerX, movePointerY);
        (hovered ? assets.battleUi.cardOver : assets.battleUi.card).draw(
            batch, bounds.x, bounds.y, bounds.width, bounds.height);
        String fitted = fitOrEllipsize(
            assets.gameplayFontSmall, label, Math.max(1f, bounds.width - 12f));
        assets.gameplayFontSmall.setColor(BattleUiAssets.TEXT);
        assets.gameplayFontSmall.draw(batch, fitted,
            bounds.x + (bounds.width - textWidth(assets.gameplayFontSmall, fitted)) / 2f,
            bounds.y + bounds.height / 2f + assets.gameplayFontSmall.getCapHeight() / 2f);
    }

    private void layoutLearnedDrawer(float screenWidth, float screenHeight) {
        float padding = learnedDrawerPadding();
        float drawerWidth = learnedDrawerCardWidth() + padding * 2f
            + learnedDrawerScrollbarWidth() + 6f;
        float drawerX = Math.max(0f, screenWidth - drawerWidth);
        float drawerTop = headerBounds.y + headerBounds.height;
        learnedDrawerBounds.set(
            drawerX, listBounds.y, drawerWidth, drawerTop - listBounds.y);
        float toggleWidth = 174f * UiScaleSystem.bodyTextScale(
            viewport.getScreenWidth() / viewport.getWorldWidth(), assets.gameplayFontSmall.getCapHeight());
        float toggleHeight = 48f;
        float toggleX = learnedDrawerExpanded
            ? Math.max(0f, learnedDrawerBounds.x - toggleWidth)
            : Math.max(0f, screenWidth - toggleWidth);
        learnedDrawerToggleBounds.set(
            toggleX,
            Math.min(screenHeight - toggleHeight,
                drawerTop - toggleHeight),
            toggleWidth,
            toggleHeight);
        learnedDrawerViewportBounds.set(
            learnedDrawerBounds.x + padding,
            learnedDrawerBounds.y + padding,
            learnedDrawerCardWidth(),
            Math.max(0f, learnedDrawerBounds.height
                - learnedDrawerHeaderHeight() - padding * 2f));
    }

    private void drawLearnedDrawer(CharacterData character) {
        if (!learnedDrawerExpanded) {
            drawLearnedDrawerToggle();
            return;
        }
        learnedMovesFor(character);
        assets.battleUi.palette.draw(batch,
            learnedDrawerBounds.x, learnedDrawerBounds.y,
            learnedDrawerBounds.width, learnedDrawerBounds.height);
        Set<String> selectedIds = Set.copyOf(moveSetIdsFor(character));
        int availableCount = (int) learnedMoves.stream()
            .filter(move -> !selectedIds.contains(move.getId()))
            .count();
        String title = "LEARNED MOVES (" + availableCount + "/" + learnedMoves.size() + ")";
        assets.gameplayFontSmall.setColor(new Color(0.720f, 0.800f, 0.950f, 1f));
        assets.gameplayFontSmall.draw(batch, title,
            learnedDrawerBounds.x + learnedDrawerPadding(),
            learnedDrawerBounds.y + learnedDrawerBounds.height
                - 18f);

        if (learnedMovesError != null) {
            assets.gameplayFontSmall.setColor(Color.RED);
            assets.gameplayFontSmall.draw(batch, "MOVE DATA UNAVAILABLE",
                learnedDrawerViewportBounds.x,
                learnedDrawerViewportBounds.y + learnedDrawerViewportBounds.height / 2f);
            drawLearnedDrawerToggle();
            return;
        }

        float stride = learnedDrawerCardHeight() + learnedDrawerCardGap();
        float contentHeight = availableCount == 0 ? 0f
            : availableCount * learnedDrawerCardHeight()
                + Math.max(0, availableCount - 1) * learnedDrawerCardGap();
        learnedDrawerScrollMax = Math.max(
            0f, contentHeight - learnedDrawerViewportBounds.height);
        learnedDrawerScrollOffset = clamp(
            learnedDrawerScrollOffset, 0f, learnedDrawerScrollMax);
        int visibleIndex = 0;
        beginClip(learnedDrawerViewportBounds);
        for (int index = 0; index < learnedDrawerCards.size(); index++) {
            MoveCardView card = learnedDrawerCards.get(index);
            if (selectedIds.contains(card.getMove().getId())) continue;
            float cardY = learnedDrawerViewportBounds.y
                + learnedDrawerViewportBounds.height - learnedDrawerCardHeight()
                - visibleIndex * stride + learnedDrawerScrollOffset;
            card.getBounds().setPosition(learnedDrawerViewportBounds.x, cardY);
            card.setHovered(learnedDrawerViewportBounds.contains(movePointerX, movePointerY)
                && card.getBounds().contains(movePointerX, movePointerY));
            card.setDragging(card.getMove() == pressedLearnedMove);
            int ceCost = index < learnedMoveCeCosts.size()
                ? learnedMoveCeCosts.get(index) : card.getMove().getBaseCeCost();
            card.draw(batch, assets.gameplayFontSmall, assets.gameplayFontSmall, assets.battleUi, ceCost);
            visibleIndex++;
        }
        endClip();
        if (availableCount == 0) {
            assets.gameplayFontSmall.setColor(BattleUiAssets.MUTED);
            assets.gameplayFontSmall.draw(batch, "Every learned move is in the move set.",
                learnedDrawerViewportBounds.x,
                learnedDrawerViewportBounds.y + learnedDrawerViewportBounds.height / 2f);
        }
        if (learnedDrawerScrollMax > 0f) drawLearnedDrawerScrollbar(contentHeight);
        drawLearnedDrawerToggle();
    }

    private void drawLearnedDrawerScrollbar(float contentHeight) {
        float width = learnedDrawerScrollbarWidth();
        float x = learnedDrawerBounds.x + learnedDrawerBounds.width
            - learnedDrawerPadding() - width;
        float y = learnedDrawerViewportBounds.y;
        float height = learnedDrawerViewportBounds.height;
        batch.setColor(BattleUiAssets.INK);
        batch.draw(assets.battleUi.pixel, x, y, width, height);
        float thumbHeight = Math.max(36f,
            height * height / contentHeight);
        float progress = learnedDrawerScrollMax == 0f ? 0f
            : learnedDrawerScrollOffset / learnedDrawerScrollMax;
        float thumbY = y + (height - thumbHeight) * (1f - progress);
        batch.setColor(BattleUiAssets.YELLOW);
        batch.draw(assets.battleUi.pixel, x + 2f, thumbY,
            Math.max(1f, width - 4f), thumbHeight);
        batch.setColor(Color.WHITE);
    }

    private void drawLearnedDrawerToggle() {
        assets.battleUi.cardOver.draw(batch,
            learnedDrawerToggleBounds.x, learnedDrawerToggleBounds.y,
            learnedDrawerToggleBounds.width, learnedDrawerToggleBounds.height);
        String label = learnedDrawerExpanded ? "CLOSE >" : "< LEARNED MOVES";
        assets.gameplayFontSmall.setColor(BattleUiAssets.TEXT);
        assets.gameplayFontSmall.draw(batch, label,
            learnedDrawerToggleBounds.x
                + (learnedDrawerToggleBounds.width - textWidth(assets.gameplayFontSmall, label)) / 2f,
            learnedDrawerToggleBounds.y
                + learnedDrawerToggleBounds.height / 2f
                + assets.gameplayFontSmall.getCapHeight() / 2f);
    }

    private void drawLearnedMoveDragAvatar() {
        if (!draggingLearnedMove || pressedLearnedMove == null) return;
        float width = moveSetSegmentWidth();
        float height = moveSetSegmentHeight();
        float x = movePointerX - width / 2f;
        float y = movePointerY - height / 2f;
        ActionSegmentView ghost = new ActionSegmentView(
            pressedLearnedMove, x, y, width, height);
        ghost.setHighlighted(true);
        ghost.draw(batch, assets.gameplayFontSmall, assets.battleUi);
    }

    private boolean handleMoveSetTouchDown(float x, float y, int button) {
        movePointerX = x;
        movePointerY = y;
        if (button == Input.Buttons.LEFT && recommendedMoveSetBounds.contains(x, y)) {
            moveUiConsumedPointer = true;
            CharacterData detail = detailCharacter();
            if (detail != null
                && replaceBattleMoveSetDraft(detail, recommendedBattleMoveSet())) {
                confirmSelection();
            } else {
                game.audio().play(SoundCue.UI_DENIED);
            }
            return true;
        }
        if (button == Input.Buttons.LEFT && customizeMoveSetBounds.contains(x, y)) {
            moveUiConsumedPointer = true;
            learnedDrawerExpanded = !learnedDrawerExpanded;
            learnedDrawerScrollOffset = 0f;
            clearLearnedMoveDrag();
            game.audio().play(SoundCue.UI_TOGGLE);
            return true;
        }
        if (button == Input.Buttons.LEFT && randomizeMoveSetBounds.contains(x, y)) {
            moveUiConsumedPointer = true;
            CharacterData detail = detailCharacter();
            boolean replaced = detail != null
                && replaceBattleMoveSetDraft(detail, randomBattleMoveSet());
            game.audio().play(replaced ? SoundCue.UI_TOGGLE : SoundCue.UI_DENIED);
            return true;
        }
        if (button == Input.Buttons.LEFT && learnedDrawerToggleBounds.contains(x, y)) {
            moveUiConsumedPointer = true;
            learnedDrawerExpanded = !learnedDrawerExpanded;
            learnedDrawerScrollOffset = 0f;
            clearLearnedMoveDrag();
            game.audio().play(SoundCue.UI_TOGGLE);
            return true;
        }
        if (learnedDrawerExpanded && learnedDrawerBounds.contains(x, y)
            && button != Input.Buttons.LEFT) {
            moveUiConsumedPointer = true;
            return true;
        }
        if (button == Input.Buttons.RIGHT) {
            int segmentIndex = hitMoveSetIndex(x, y);
            if (segmentIndex < 0) return false;
            moveUiConsumedPointer = true;
            CharacterData detail = detailCharacter();
            if (detail != null && removeMoveFromSet(detail, segmentIndex)) {
                game.audio().play(SoundCue.UI_PLAN_REMOVE);
            } else {
                game.audio().play(SoundCue.UI_DENIED);
            }
            return true;
        }
        if (button != Input.Buttons.LEFT) return false;
        if (learnedDrawerExpanded && learnedDrawerBounds.contains(x, y)) {
            moveUiConsumedPointer = true;
            MoveCardView card = learnedCardAt(x, y);
            if (card != null) {
                pressedLearnedMove = card.getMove();
                draggingLearnedMove = false;
                movePressX = x;
                movePressY = y;
            }
            return true;
        }
        if (moveSetPanelBounds.contains(x, y)) {
            moveUiConsumedPointer = true;
            return true;
        }
        return false;
    }

    private boolean handleMoveSetTouchDragged(float x, float y) {
        if (pressedLearnedMove == null) return false;
        movePointerX = x;
        movePointerY = y;
        moveUiConsumedPointer = true;
        float deltaX = x - movePressX;
        float deltaY = y - movePressY;
        if (!draggingLearnedMove
            && deltaX * deltaX + deltaY * deltaY >= MOVE_DRAG_THRESHOLD * MOVE_DRAG_THRESHOLD) {
            draggingLearnedMove = true;
            game.audio().play(SoundCue.UI_PICKUP);
        }
        return true;
    }

    private boolean handleMoveSetTouchUp(float x, float y, int button) {
        if (button != Input.Buttons.LEFT || pressedLearnedMove == null) return false;
        movePointerX = x;
        movePointerY = y;
        moveUiConsumedPointer = true;
        CharacterData character = detailCharacter();
        if (character == null) {
            game.audio().play(SoundCue.UI_DENIED);
            clearLearnedMoveDrag();
            return true;
        }
        boolean placed;
        if (!draggingLearnedMove) {
            placed = addMoveToSet(character, pressedLearnedMove);
        } else if (isVisibleMoveSetDropTarget(x, y)) {
            placed = addMoveToSet(character, pressedLearnedMove);
        } else {
            placed = false;
        }
        game.audio().play(placed ? SoundCue.UI_PLAN_PLACE : SoundCue.UI_DENIED);
        clearLearnedMoveDrag();
        return true;
    }

    private MoveCardView learnedCardAt(float x, float y) {
        if (!learnedDrawerViewportBounds.contains(x, y)) return null;
        CharacterData detail = detailCharacter();
        if (detail == null) return null;
        Set<String> selected = Set.copyOf(moveSetIdsFor(detail));
        for (MoveCardView card : learnedDrawerCards) {
            if (!selected.contains(card.getMove().getId()) && card.getBounds().contains(x, y)) {
                return card;
            }
        }
        return null;
    }

    private int hitMoveSetIndex(float x, float y) {
        if (!moveSetViewportBounds.contains(x, y)) return -1;
        for (int index = 0; index < moveSetViews.size(); index++) {
            if (moveSetViews.get(index).getBounds().contains(x, y)) return index;
        }
        return -1;
    }

    private int learnedOrderInsertionIndex(Move move, List<Move> selectedMoves) {
        if (move == null) return selectedMoves.size();
        Set<String> selectedIds = selectedMoves.stream()
            .map(Move::getId)
            .collect(java.util.stream.Collectors.toSet());
        int insertion = 0;
        for (Move learned : learnedMoves) {
            if (learned.getId().equals(move.getId())) return insertion;
            if (selectedIds.contains(learned.getId())) insertion++;
        }
        return selectedMoves.size();
    }

    private boolean isVisibleMoveSetDropTarget(float x, float y) {
        return moveSetViewportBounds.contains(x, y)
            && (!learnedDrawerExpanded || !learnedDrawerBounds.contains(x, y));
    }

    private boolean scrollLearnedDrawer(float amount) {
        if (!learnedDrawerExpanded) return false;
        Vector2 world = worldPointer(Gdx.input.getX(), Gdx.input.getY());
        float x = world.x;
        float y = world.y;
        if (!learnedDrawerBounds.contains(x, y)) return false;
        if (amount != 0f && learnedDrawerScrollMax > 0f) {
            float step = Math.max(36f, learnedDrawerCardHeight() * 0.35f);
            learnedDrawerScrollOffset = clamp(
                learnedDrawerScrollOffset + amount * step, 0f, learnedDrawerScrollMax);
        }
        return true;
    }

    private boolean scrollMoveSet(float amount) {
        if (amount == 0f || moveSetScrollMax <= 0f) return false;
        Vector2 world = worldPointer(Gdx.input.getX(), Gdx.input.getY());
        float x = world.x;
        float y = world.y;
        if (!moveSetPanelBounds.contains(x, y)) return false;
        moveSetScrollOffset = clamp(
            moveSetScrollOffset + amount * (moveSetSegmentWidth() + moveSetSegmentGap()) * 0.6f,
            0f, moveSetScrollMax);
        return true;
    }

    private void clearLearnedMoveDrag() {
        pressedLearnedMove = null;
        draggingLearnedMove = false;
    }

    private float moveSetSegmentWidth() {
        return MOVE_SET_SEGMENT_WIDTH;
    }

    private float moveSetSegmentHeight() {
        return MOVE_SET_SEGMENT_HEIGHT;
    }

    private float moveSetSegmentGap() {
        return MOVE_SET_SEGMENT_GAP;
    }

    private void clearMoveSetActionBounds() {
        recommendedMoveSetBounds.set(0f, 0f, 0f, 0f);
        customizeMoveSetBounds.set(0f, 0f, 0f, 0f);
        randomizeMoveSetBounds.set(0f, 0f, 0f, 0f);
    }

    private float learnedDrawerPadding() {
        return LEARNED_DRAWER_PADDING;
    }

    private float learnedDrawerHeaderHeight() {
        return LEARNED_DRAWER_HEADER_HEIGHT;
    }

    private float learnedDrawerScrollbarWidth() {
        return LEARNED_DRAWER_SCROLLBAR_WIDTH;
    }

    private float learnedDrawerCardGap() {
        return LEARNED_DRAWER_CARD_GAP;
    }

    private float learnedDrawerCardWidth() {
        return FULL_MOVE_CARD_WIDTH;
    }

    private float learnedDrawerCardHeight() {
        return FULL_MOVE_CARD_HEIGHT;
    }

    private static String ellipsize(BitmapFont font, String text, float width) {
        String suffix = "...";
        String result = text;
        while (result.length() > 1 && textWidth(font, result + suffix) > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + suffix;
    }

    private void beginClip(Rectangle bounds) {
        batch.flush();
        viewport.calculateScissors(batch.getTransformMatrix(), bounds, scissorBounds);
        scissorPushed = ScissorStack.pushScissors(scissorBounds);
    }

    private void endClip() {
        batch.flush();
        if (scissorPushed) ScissorStack.popScissors();
        scissorPushed = false;
    }

    private boolean scrollRoster(float amount) {
        if (amount == 0f || rosterScrollMax <= 0f) return false;
        Vector2 world = worldPointer(Gdx.input.getX(), Gdx.input.getY());
        float pointerX = world.x;
        float pointerY = world.y;
        if (!rosterViewportBounds.contains(pointerX, pointerY)) return false;

        rosterScrollOffset = clamp(
            rosterScrollOffset + amount * ROW_HEIGHT,
            0f,
            rosterScrollMax);
        return true;
    }

    private Vector2 worldPointer(float screenX, float screenY) {
        return viewport.unproject(pointerCoordinates.set(screenX, screenY));
    }

    private void revealRosterCursor() {
        if (characters.isEmpty()) return;
        rosterScrollOffset = rosterScrollOffsetForSelection(
            rosterScrollOffset,
            cursorIndex,
            rosterRowCount(),
            ROW_HEIGHT,
            rosterViewportBounds.height);
    }

    static float rosterScrollOffsetForSelection(
        float currentOffset,
        int selectedIndex,
        int rowCount,
        float rowHeight,
        float viewportHeight
    ) {
        float maximumOffset = Math.max(0f, rowCount * rowHeight - viewportHeight);
        float offset = clamp(currentOffset, 0f, maximumOffset);
        float rowTop = selectedIndex * rowHeight;
        float rowBottom = rowTop + rowHeight;
        if (rowTop < offset) {
            offset = rowTop;
        } else if (rowBottom > offset + viewportHeight) {
            offset = rowBottom - viewportHeight;
        }
        return clamp(offset, 0f, maximumOffset);
    }

    static float rosterWidth(float screenWidth) {
        return Math.max(0f, screenWidth * ROSTER_WIDTH_RATIO);
    }

    private static float moveSetPanelHeight() {
        return MOVE_PANEL_HEADER_HEIGHT + MOVE_PANEL_PADDING * 2f
            + MOVE_SET_SEGMENT_HEIGHT + MOVE_SET_ACTION_HEIGHT + MOVE_SET_ACTION_GAP;
    }

    static float statBarFillRatio(int value) {
        return clamp((value - 10f) / 290f, 0f, 1f);
    }

    static Color statBarColor(int value, Color output) {
        Objects.requireNonNull(output, "output");
        if (value <= 80) {
            float blend = clamp((value - 10f) / 70f, 0f, 1f);
            return output.set(STAT_MIN_COLOR).lerp(STAT_MID_COLOR, blend);
        }
        float blend = clamp((value - 80f) / 220f, 0f, 1f);
        return output.set(STAT_MID_COLOR).lerp(STAT_MAX_COLOR, blend);
    }

    private static boolean hasCursedTechnique(CharacterData character) {
        return character.innateTechniqueName != null
            && !character.innateTechniqueName.isBlank();
    }

    private float rowHeight() {
        return ROW_HEIGHT;
    }

    private float movePanelPadding() {
        return MOVE_PANEL_PADDING;
    }

    private void resetRosterScroll() {
        rosterScrollOffset = 0f;
    }

    private void resetMoveScroll() {
        moveSetScrollOffset = 0f;
        moveSetScrollMax = 0f;
        learnedDrawerScrollOffset = 0f;
        learnedDrawerScrollMax = 0f;
        clearLearnedMoveDrag();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void drawBold(BitmapFont font, String text, float x, float y) {
        font.draw(batch, text, x, y);
        font.draw(batch, text, x + 1f, y);
    }

    private static float textWidth(BitmapFont font, String text) {
        return new GlyphLayout(font, text).width;
    }

    private static String fitOrEllipsize(BitmapFont font, String text, float width) {
        return textWidth(font, text) <= width ? text : ellipsize(font, text, width);
    }

    private static List<String> wrap(BitmapFont font, String text, float width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textWidth(font, candidate) <= width) {
                line.setLength(0);
                line.append(candidate);
            } else {
                if (!line.isEmpty()) lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
