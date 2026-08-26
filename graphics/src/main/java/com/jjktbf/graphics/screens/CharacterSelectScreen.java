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
import com.jjktbf.controller.BattleController;
import com.jjktbf.graphics.AssetLoader;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.audio.SoundCue;
import com.jjktbf.graphics.ui.StatusBar;
import com.jjktbf.graphics.ui.battle.BattleUiAssets;
import com.jjktbf.graphics.ui.editor.ScrollAxes;
import com.jjktbf.graphics.ui.battle.ActionSegmentView;
import com.jjktbf.graphics.ui.battle.MoveCardView;
import com.jjktbf.graphics.ui.profile.UiProfile;
import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityRepository;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterRepository;
import com.jjktbf.model.character.CombatStats;
import com.jjktbf.model.character.SlotBudgetEnforcer;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Master-detail character selection screen for player and CPU choices. */
public class CharacterSelectScreen implements Screen {

    private static final String CHAR_DATA_DIR = "data/characters";
    private static final String MOVE_DATA_DIR = "data/moves";
    private static final String ABILITY_DATA_DIR = "data/abilities";
    private static final String TECHNIQUE_DATA_DIR = "data/techniques";
    private static final float ROW_HEIGHT = 44f;
    private static final float WINDOWS_ROW_HEIGHT = 66f;
    private static final float MOVE_PANEL_GAP = 12f;
    private static final float WINDOWS_MOVE_PANEL_GAP = 18f;
    private static final float MOVE_PANEL_PADDING = 10f;
    private static final float WINDOWS_MOVE_PANEL_PADDING = 15f;
    private static final float MOVE_PANEL_HEADER_HEIGHT = 24f;
    private static final float WINDOWS_MOVE_PANEL_HEADER_HEIGHT = 36f;
    private static final float MIN_CHARACTER_INFO_HEIGHT = 205f;
    private static final float WINDOWS_MIN_CHARACTER_INFO_HEIGHT = 307.5f;
    private static final float DESCRIPTION_TARGET_HEIGHT = 45f;
    private static final float WINDOWS_DESCRIPTION_TARGET_HEIGHT = 67.5f;
    private static final float HEADER_HEIGHT = 58f;
    private static final float WINDOWS_HEADER_HEIGHT = 87f;
    /** Windows roster panel occupies one fifth of the screen width. */
    private static final float WINDOWS_ROSTER_WIDTH_RATIO = 0.20f;
    private static final float WINDOWS_PROFILE_PADDING = 24f;
    private static final float WINDOWS_PROFILE_TITLE_GAP = 60f;
    private static final float WINDOWS_PROFILE_SUMMARY_HEIGHT = 580f;
    private static final float WINDOWS_COMPACT_PROFILE_SUMMARY_HEIGHT = 155f;
    private static final float WINDOWS_PROFILE_SECTION_GAP = 16f;
    private static final float WINDOWS_COMPACT_PROFILE_SECTION_GAP = 10f;
    private static final float WINDOWS_PROFILE_SPRITE_SIZE = 444f;
    private static final float WINDOWS_COMPACT_PROFILE_SPRITE_SIZE = 100f;
    private static final float WINDOWS_PROFILE_BAR_HEIGHT = 34f;
    private static final float WINDOWS_COMPACT_PROFILE_BAR_HEIGHT = 20f;
    private static final float WINDOWS_FULL_MOVE_CARD_WIDTH = 324f;
    private static final float WINDOWS_FULL_MOVE_CARD_HEIGHT = 302f;
    private static final float WINDOWS_FULL_MOVE_CARD_SCALE = 1.35f;
    private static final float MOVE_SET_SEGMENT_WIDTH = 150f;
    private static final float WINDOWS_MOVE_SET_SEGMENT_WIDTH = 225f;
    private static final float MOVE_SET_SEGMENT_HEIGHT = 54f;
    private static final float WINDOWS_MOVE_SET_SEGMENT_HEIGHT = 81f;
    private static final float MOVE_SET_SEGMENT_GAP = 8f;
    private static final float WINDOWS_MOVE_SET_SEGMENT_GAP = 12f;
    private static final float LEARNED_DRAWER_PADDING = 12f;
    private static final float WINDOWS_LEARNED_DRAWER_PADDING = 18f;
    private static final float LEARNED_DRAWER_HEADER_HEIGHT = 34f;
    private static final float WINDOWS_LEARNED_DRAWER_HEADER_HEIGHT = 51f;
    private static final float LEARNED_DRAWER_SCROLLBAR_WIDTH = 8f;
    private static final float WINDOWS_LEARNED_DRAWER_SCROLLBAR_WIDTH = 12f;
    private static final float LEARNED_DRAWER_CARD_GAP = 10f;
    private static final float WINDOWS_LEARNED_DRAWER_CARD_GAP = 15f;
    private static final float MOVE_DRAG_THRESHOLD = 8f;
    /** fontSmall is logical size 15, matching Windows editor ID text. */
    private static final float WINDOWS_MIN_SMALL_FONT_SCALE = 1f;
    private static final float WINDOWS_STATS_FONT_SCALE = 1.20f;
    private static final float WINDOWS_BST_FONT_SCALE = 1.60f;
    private static final Color STAT_MIN_COLOR = new Color(0.920f, 0.220f, 0.180f, 1f);
    private static final Color STAT_MID_COLOR = new Color(1f, 1f, 0f, 1f);
    private static final Color STAT_MAX_COLOR = new Color(0.260f, 0.820f, 0.360f, 1f);
    private static final Color STAT_TRACK_COLOR = new Color(0.770f, 0.790f, 0.720f, 1f);
    private static final String[] STAT_LABELS = {
        "Vitality", "Strength", "Durability", "Speed", "Combat Ability",
        "CE Reserves", "CE Efficiency", "CE Output", "Jujutsu Skill", "CT Mastery"
    };

    private enum Phase { PLAYER, CPU }

    /**
     * Roster format for the battle being set up. ONE_V_ONE picks one fighter
     * per side (the legacy flow); TWO_V_TWO picks two. Set on entry via
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
    private final boolean windowsLayout;
    private final SpriteBatch batch;
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
    private final Rectangle moveSetPanelBounds = new Rectangle();
    private final Rectangle moveSetViewportBounds = new Rectangle();
    private final Rectangle learnedDrawerBounds = new Rectangle();
    private final Rectangle learnedDrawerViewportBounds = new Rectangle();
    private final Rectangle learnedDrawerToggleBounds = new Rectangle();
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
            return handleMoveSetTouchDown(
                screenX, Gdx.graphics.getHeight() - screenY, button);
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            return handleMoveSetTouchDragged(
                screenX, Gdx.graphics.getHeight() - screenY);
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            return handleMoveSetTouchUp(
                screenX, Gdx.graphics.getHeight() - screenY, button);
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            movePointerX = screenX;
            movePointerY = Gdx.graphics.getHeight() - screenY;
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
        windowsLayout = game.activeUiProfile() == UiProfile.WINDOWS;
        batch = new SpriteBatch();
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
        } catch (IOException e) {
            loadError = "Failed to load data: " + e.getMessage();
        }
    }

    @Override
    public void render(float delta) {
        clearScreen();
        layout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        handleInput();
        draw();
    }

    @Override public void resize(int width, int height) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        layout(width, height);
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
            cursorIndex = (cursorIndex - 1 + characters.size()) % characters.size();
            revealRosterCursor();
            resetMoveScroll();
            game.audio().play(SoundCue.UI_NAVIGATE);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            cursorIndex = (cursorIndex + 1) % characters.size();
            revealRosterCursor();
            resetMoveScroll();
            game.audio().play(SoundCue.UI_NAVIGATE);
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && !moveUiConsumedPointer) {
            selectRowAt(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
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
        if (!(windowsLayout ? rosterViewportBounds : listBounds).contains(x, y)) return;
        float firstRowTop = listBounds.y + listBounds.height
            - (windowsLayout ? 69f : 46f);
        int index = (int) ((firstRowTop + rosterScrollOffset - y) / rowHeight());
        if (index >= 0 && index < characters.size()) {
            if (index == cursorIndex) {
                confirmSelection();
            } else {
                cursorIndex = index;
                resetMoveScroll();
                game.audio().play(SoundCue.UI_NAVIGATE);
            }
        }
    }

    private void confirmSelection() {
        CharacterData picked = characters.get(cursorIndex);
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
        game.audio().play(SoundCue.UI_CONFIRM);
        currentPicks.add(picked);
        currentMoveSets().add(selectedMoveSet);
        if (phase == Phase.PLAYER) {
            playerChoice = picked; // convenience for the 1-slot header display
        }

        if (currentPicks.size() < format.fightersPerSide()) {
            // More slots to fill on this side.
            cursorIndex = 0;
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
            cursorIndex = 0;
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
            cursorIndex = 0;
            resetRosterScroll();
            resetMoveScroll();
            return true;
        }
        if (!playerPicks.isEmpty()) {
            playerPicks.remove(playerPicks.size() - 1);
            playerMoveSets.remove(playerMoveSets.size() - 1);
            playerChoice = playerPicks.isEmpty() ? null : playerPicks.get(playerPicks.size() - 1);
            phase = Phase.PLAYER;
            cursorIndex = 0;
            resetRosterScroll();
            resetMoveScroll();
            return true;
        }
        return false;
    }

    private void startConfiguredBattle() {
        if (format == com.jjktbf.model.combat.BattleFormat.TWO_V_TWO) {
            game.startTeamBattle(
                new java.util.ArrayList<>(playerPicks),
                new java.util.ArrayList<>(playerMoveSets),
                new java.util.ArrayList<>(cpuPicks),
                new java.util.ArrayList<>(cpuMoveSets),
                moveRepo, abilityRepo, techniqueRepo, controlMode, statMode);
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
        float margin = Math.min(36f, Math.max(20f, width * 0.035f));
        float headerHeight = windowsLayout ? WINDOWS_HEADER_HEIGHT : HEADER_HEIGHT;
        headerBounds.set(margin, height - margin - headerHeight, width - margin * 2f, headerHeight);
        float contentTop = headerBounds.y - 14f;
        float listWidth = windowsLayout
            ? windowsRosterWidth(width)
            : Math.max(230f, width * 0.29f);
        listBounds.set(margin, margin, listWidth, contentTop - margin);
        if (windowsLayout) {
            rosterViewportBounds.set(
                listBounds.x + 8f,
                listBounds.y + 8f,
                Math.max(0f, listBounds.width - 16f),
                Math.max(0f, listBounds.height - 77f));
            rosterScrollMax = Math.max(0f,
                characters.size() * WINDOWS_ROW_HEIGHT - rosterViewportBounds.height);
            rosterScrollOffset = clamp(rosterScrollOffset, 0f, rosterScrollMax);
            revealRosterCursor();
        }
        if (windowsLayout) {
            float detailX = margin + windowsRosterWidth(width) + 14f;
            detailBounds.set(
                detailX, margin, width - detailX - margin, contentTop - margin);
        } else {
            detailBounds.set(listBounds.x + listBounds.width + 14f, margin,
                width - (listBounds.x + listBounds.width + 14f) - margin, contentTop - margin);
        }
        layoutLearnedDrawer(width, height);
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
        drawCharacterPage(characters.get(cursorIndex));
        drawLearnedDrawer(characters.get(cursorIndex));
        drawLearnedMoveDragAvatar();
        batch.end();
    }

    private void drawError() {
        assets.battleUi.header.draw(batch, headerBounds.x, headerBounds.y,
            headerBounds.width, headerBounds.height);
        assets.fontSmall.setColor(Color.RED);
        assets.fontSmall.draw(batch, loadError, headerBounds.x + 18f,
            headerBounds.y + (windowsLayout ? 51f : 34f));
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
        assets.fontMedium.setColor(BattleUiAssets.YELLOW);
        assets.fontMedium.draw(batch, title, headerBounds.x + 18f,
            headerBounds.y + (windowsLayout ? 58.5f : 39f));
        assets.fontSmall.setColor(new Color(0.720f, 0.800f, 0.950f, 1f));
        String state = !multiplayerSelection && phase == Phase.CPU && !playerPicks.isEmpty()
            ? picksSummary("PLAYER", playerPicks) + "  |  " + statMode + "  |  ENTER: START"
            : "UP/DOWN: SELECT  |  ENTER: CONFIRM  |  LEARNED MOVES: CUSTOMIZE  |  "
                + statMode;
        assets.fontSmall.draw(batch, state, headerBounds.x + 20f,
            headerBounds.y + (windowsLayout ? 25.5f : 17f));
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
        assets.fontSmall.setColor(new Color(0.720f, 0.800f, 0.950f, 1f));
        assets.fontSmall.draw(batch, "AVAILABLE CHARACTERS", listBounds.x + 14f,
            listBounds.y + listBounds.height - (windowsLayout ? 22.5f : 15f));

        float rowHeight = rowHeight();
        float rowTop = listBounds.y + listBounds.height - (windowsLayout ? 69f : 46f);
        java.util.List<CharacterData> sidePicks = currentPicks();
        if (windowsLayout) beginClip(rosterViewportBounds);
        for (int i = 0; i < characters.size(); i++) {
            float rowY = rowTop - (i + 1) * rowHeight + rosterScrollOffset;
            if (i == cursorIndex) {
                assets.battleUi.cardOver.draw(batch, listBounds.x + 8f, rowY,
                    listBounds.width - 16f, rowHeight - (windowsLayout ? 6f : 4f));
            }
            CharacterData character = characters.get(i);
            // Dim a character already picked on the side currently being filled.
            boolean alreadyPicked = sidePicks.stream().anyMatch(c -> c.id.equals(character.id));
            BitmapFont rosterFont = assets.fontMedium;
            rosterFont.setColor(i == cursorIndex
                ? BattleUiAssets.TEXT
                : (alreadyPicked ? new Color(0.55f, 0.55f, 0.55f, 1f) : Color.WHITE));
            String rosterName = windowsLayout
                ? fitOrEllipsize(assets.fontMedium, character.name, listBounds.width - 36f)
                : character.name;
            rosterFont.draw(batch, rosterName, listBounds.x + 18f,
                rowY + (windowsLayout ? 40.5f : 27f));
        }
        // Pick badges (P1/P2 on player side, C1/C2 on cpu side) next to names.
        drawPickBadges(rowTop, "P", playerPicks);
        drawPickBadges(rowTop,
            controlMode == BattleController.ControlMode.HUMAN_CONTROLS_BOTH_TEAMS ? "E" : "C",
            cpuPicks);
        if (windowsLayout) endClip();
    }

    /** Draw a side's slot badges (P1/P2 or C1/C2) next to picked roster rows. */
    private void drawPickBadges(float rowTop, String prefix, java.util.List<CharacterData> picks) {
        float rowHeight = rowHeight();
        for (int slot = 0; slot < picks.size(); slot++) {
            CharacterData picked = picks.get(slot);
            int row = characters.stream().filter(c -> c.id.equals(picked.id))
                .mapToInt(characters::indexOf).findFirst().orElse(-1);
            if (row < 0) continue;
            float rowY = rowTop - (row + 1) * rowHeight + rosterScrollOffset;
            assets.fontSmall.setColor(BattleUiAssets.YELLOW);
            assets.fontSmall.draw(batch, prefix + (slot + 1), listBounds.x + 14f,
                rowY + (windowsLayout ? 21f : 14f));
        }
    }

    private void drawCharacterPage(CharacterData character) {
        if (windowsLayout) {
            drawWindowsCharacterPage(character);
            return;
        }

        assets.battleUi.card.draw(batch, detailBounds.x, detailBounds.y,
            detailBounds.width, detailBounds.height);

        float pad = windowsLayout ? 30f : 20f;
        float innerLeft = detailBounds.x + pad;
        float innerRight = detailBounds.x + detailBounds.width - pad;
        float innerTop = detailBounds.y + detailBounds.height - pad;
        float innerWidth = innerRight - innerLeft;

        // Name — top-left corner, prominent.
        assets.fontXLarge.setColor(BattleUiAssets.TEXT);
        drawBold(assets.fontXLarge, character.name, innerLeft, innerTop);
        String baseStatTotalText = "Base Stat Total: " + baseStatTotal(character);
        assets.fontMedium.setColor(BattleUiAssets.TEXT);
        drawBold(assets.fontMedium, baseStatTotalText,
            innerRight - textWidth(assets.fontMedium, baseStatTotalText), innerTop);

        // Content region sits below the name.
        float contentTop = innerTop - (windowsLayout ? 72f : 48f);
        float contentBottom = detailBounds.y + pad;
        float contentHeight = contentTop - contentBottom;
        List<Move> moves = learnedMovesFor(character);
        float movePanelPadding = movePanelPadding();
        float movePanelHeaderHeight = movePanelHeaderHeight();
        float desiredMovesHeight = movePanelHeaderHeight + movePanelPadding * 2f
            + moveSetSegmentHeight();
        float minimumInfoHeight = Math.min(
            windowsLayout ? WINDOWS_MIN_CHARACTER_INFO_HEIGHT : MIN_CHARACTER_INFO_HEIGHT,
            contentHeight * 0.58f);
        float maximumMovesHeight = Math.max(0f,
            contentHeight - minimumInfoHeight - movePanelGap());
        float movesPanelHeight = Math.min(desiredMovesHeight, maximumMovesHeight);
        float sectionGap = movesPanelHeight > 0f ? movePanelGap() : 0f;
        float infoBottom = contentBottom + movesPanelHeight + sectionGap;
        float infoHeight = contentTop - infoBottom;

        // Left column: profile sprite with HP/CE bars, sized around the moves panel.
        float leftWidth = Math.min(innerWidth * 0.52f, windowsLayout ? 540f : 360f);
        float leftCenterX = innerLeft + leftWidth / 2f;
        float barHeight = windowsLayout ? 42f : 28f;
        float barGap = windowsLayout ? 12f : 8f;
        boolean hasCursedTechnique = character.innateTechniqueName != null
            && !character.innateTechniqueName.isBlank();
        float techniqueGap = hasCursedTechnique ? (windowsLayout ? 24f : 16f) : 0f;
        float techniqueHeight = hasCursedTechnique ? assets.fontSmall.getCapHeight() * 2f : 0f;
        float barsAndSpacing = (windowsLayout ? 36f : 24f)
            + barHeight * 2f + barGap + techniqueGap + techniqueHeight;
        float spriteSize = Math.min(leftWidth, Math.max(0f, infoHeight - barsAndSpacing));
        spriteSize = Math.min(spriteSize, windowsLayout ? 504f : 336f);
        if (spriteSize > 0f) {
            float spriteX = leftCenterX - spriteSize / 2f;
            float spriteY = contentTop - spriteSize;
            assets.battleUi.palette.draw(batch, spriteX - 10f, spriteY - 10f,
                spriteSize + 20f, spriteSize + 20f);
            Texture sprite = assets.characterSprite(character.spriteAsset, assets.playerSprite);
            batch.draw(sprite, spriteX, spriteY, spriteSize, spriteSize);

            float barWidth = spriteSize;
            float barX = leftCenterX - barWidth / 2f;
            float hpY = spriteY - (windowsLayout ? 36f : 24f) - barHeight;
            float ceY = hpY - barGap - barHeight;
            CombatStats combat = new CombatStats(character.toCharacterStats(), statMode);
            float statusBarTextGeometryScale = windowsLayout ? 1.5f : 1f;
            StatusBar hp = new StatusBar(
                "HP", new Color(0.260f, 0.820f, 0.360f, 1f), statusBarTextGeometryScale);
            hp.setBounds(barX, hpY, barWidth, barHeight);
            hp.setValues(combat.getMaxHp(), combat.getMaxHp());
            hp.draw(batch, assets.fontMedium, assets.battleUi, true);
            StatusBar ce = new StatusBar(
                "CE", new Color(0.220f, 0.500f, 0.940f, 1f), statusBarTextGeometryScale);
            ce.setBounds(barX, ceY, barWidth, barHeight);
            ce.setValues(combat.getMaxCursedEnergy(), combat.getMaxCursedEnergy());
            ce.draw(batch, assets.fontMedium, assets.battleUi, true);
            if (hasCursedTechnique) {
                assets.fontSmall.setColor(Color.BLACK);
                float originalScaleX = assets.fontSmall.getData().scaleX;
                float originalScaleY = assets.fontSmall.getData().scaleY;
                assets.fontSmall.getData().setScale(originalScaleX * 2f, originalScaleY * 2f);
                float techniqueX = leftCenterX - textWidth(assets.fontSmall,
                    character.innateTechniqueName) / 2f;
                assets.fontSmall.draw(batch, character.innateTechniqueName, techniqueX,
                    ceY - techniqueGap);
                assets.fontSmall.getData().setScale(originalScaleX, originalScaleY);
            }
        }

        // Right column: compact stats leave the remaining vertical space for the description.
        float rightX = innerLeft + leftWidth + (windowsLayout ? 36f : 24f);
        float rightWidth = innerRight - rightX;
        float descriptionTargetHeight = windowsLayout
            ? WINDOWS_DESCRIPTION_TARGET_HEIGHT : DESCRIPTION_TARGET_HEIGHT;
        float statsRowHeight = Math.min(windowsLayout ? 34.5f : 23f,
            Math.max(windowsLayout ? 22.5f : 15f,
                (infoHeight - descriptionTargetHeight - (windowsLayout ? 21f : 14f))
                    / STAT_LABELS.length));
        BitmapFont detailFont = statsRowHeight < (windowsLayout ? 33f : 22f)
            ? assets.fontSmall : assets.fontMedium;
        drawStats(character, rightX, rightWidth, contentTop, statsRowHeight, detailFont);
        float descriptionTop = contentTop - STAT_LABELS.length * statsRowHeight
            - (windowsLayout ? 21f : 14f);
        drawDescription(displayDescription(character), rightX, rightWidth, descriptionTop, infoBottom, detailFont);

        drawMoveSet(character, moves, innerLeft, contentBottom, innerWidth, movesPanelHeight);
    }

    private void drawWindowsCharacterPage(CharacterData character) {
        assets.battleUi.card.draw(batch, detailBounds.x, detailBounds.y,
            detailBounds.width, detailBounds.height);

        float innerLeft = detailBounds.x + WINDOWS_PROFILE_PADDING;
        float innerRight = detailBounds.x + detailBounds.width - WINDOWS_PROFILE_PADDING;
        float innerTop = detailBounds.y + detailBounds.height - WINDOWS_PROFILE_PADDING;
        float innerWidth = Math.max(0f, innerRight - innerLeft);

        boolean compactLayout = detailBounds.height < 1100f;
        BitmapFont nameFont = compactLayout ? assets.fontMedium : assets.fontXLarge;
        nameFont.setColor(BattleUiAssets.TEXT);
        drawBold(nameFont, character.name, innerLeft, innerTop);

        float contentTop = innerTop - WINDOWS_PROFILE_TITLE_GAP;
        float contentBottom = detailBounds.y + WINDOWS_PROFILE_PADDING;
        float contentHeight = Math.max(0f, contentTop - contentBottom);
        if (compactLayout && character.description != null && !character.description.isBlank()) {
            assets.fontSmall.setColor(BattleUiAssets.MUTED);
            assets.fontSmall.draw(batch,
                fitOrEllipsize(assets.fontSmall, displayDescription(character), innerWidth),
                innerLeft,
                innerTop - 36f);
        }
        float requestedMovesHeight = windowsMoveSetPanelHeight();
        float movesPanelHeight = Math.min(requestedMovesHeight, contentHeight);
        float sectionGap = contentHeight > movesPanelHeight
            ? (compactLayout
                ? WINDOWS_COMPACT_PROFILE_SECTION_GAP : WINDOWS_PROFILE_SECTION_GAP)
            : 0f;
        float infoBottom = contentBottom + movesPanelHeight + sectionGap;
        float infoHeight = Math.max(0f, contentTop - infoBottom);
        List<Move> moves = learnedMovesFor(character);

        if (infoHeight > 0f) {
            Rectangle infoClip = new Rectangle(innerLeft, infoBottom, innerWidth, infoHeight);
            beginClip(infoClip);
            float requestedSummaryHeight = compactLayout
                ? WINDOWS_COMPACT_PROFILE_SUMMARY_HEIGHT : WINDOWS_PROFILE_SUMMARY_HEIGHT;
            float summaryHeight = Math.min(requestedSummaryHeight, infoHeight);
            float summaryBottom = contentTop - summaryHeight;
            drawWindowsProfileSummary(
                character, innerLeft, contentTop, summaryBottom, compactLayout);

            float techniqueGap = compactLayout
                ? WINDOWS_COMPACT_PROFILE_SECTION_GAP : WINDOWS_PROFILE_SECTION_GAP;
            float techniqueTop = summaryBottom - techniqueGap;
            if (techniqueTop > infoBottom) {
                drawWindowsTechniqueSection(
                    character, innerLeft, innerWidth, techniqueTop, infoBottom, compactLayout);
            }
            endClip();
        }

        drawMoveSet(character, moves, innerLeft, contentBottom, innerWidth, movesPanelHeight);
    }

    private void drawWindowsProfileSummary(
        CharacterData character,
        float x,
        float top,
        float bottom,
        boolean compactLayout
    ) {
        int screenWidth = Gdx.graphics.getWidth();
        boolean compactPortrait = compactLayout || screenWidth < 2000;
        float frameX;
        float spriteX;
        float resourceX;
        float statsX;
        if (!compactPortrait) {
            frameX = x;
            spriteX = x + 8f;
            resourceX = x;
            statsX = x + 580f;
        } else if (screenWidth >= 2000) {
            frameX = x;
            spriteX = x + 6f;
            resourceX = x;
            statsX = x + 390f;
        } else if (screenWidth >= 1450) {
            frameX = x;
            spriteX = x + 6f;
            resourceX = x;
            statsX = x + 254f;
        } else {
            frameX = x;
            spriteX = x + 6f;
            resourceX = x;
            statsX = x + 198f;
        }

        float spriteY = compactPortrait ? top - 106f : top - 452f;
        float spriteSize = compactPortrait
            ? WINDOWS_COMPACT_PROFILE_SPRITE_SIZE : WINDOWS_PROFILE_SPRITE_SIZE;
        assets.battleUi.palette.draw(batch,
            frameX,
            compactPortrait ? top - 112f : top - 460f,
            compactPortrait ? 112f : 460f,
            compactPortrait ? 112f : 460f);
        Texture sprite = assets.characterSprite(character.spriteAsset, assets.playerSprite);
        batch.draw(sprite, spriteX, spriteY, spriteSize, spriteSize);

        float barHeight = compactPortrait
            ? WINDOWS_COMPACT_PROFILE_BAR_HEIGHT : WINDOWS_PROFILE_BAR_HEIGHT;
        float resourceWidth = compactPortrait ? 112f : 460f;
        float hpY = compactPortrait ? top - 132f : top - 518f;
        float ceY = compactPortrait ? top - 155f : top - 560f;
        CombatStats fallbackStats = new CombatStats(character.toCharacterStats(), statMode);
        int maximumHp = profileCombatant != null
            ? profileCombatant.getMaxHp() : fallbackStats.getMaxHp();
        int maximumCe = profileCombatant != null
            ? profileCombatant.getMaxCursedEnergy() : fallbackStats.getMaxCursedEnergy();
        StatusBar hp = new StatusBar(
            "HP", new Color(STAT_MAX_COLOR), 1.5f);
        hp.setBounds(resourceX, hpY, resourceWidth, barHeight);
        hp.setValues(maximumHp, maximumHp);
        hp.draw(batch, assets.fontSmall, assets.battleUi, !compactPortrait);
        StatusBar ce = new StatusBar(
            "CE", new Color(0.220f, 0.500f, 0.940f, 1f), 1.5f);
        ce.setBounds(resourceX, ceY, resourceWidth, barHeight);
        ce.setValues(maximumCe, maximumCe);
        ce.draw(batch, assets.fontSmall, assets.battleUi, !compactPortrait);

        drawWindowsStats(character, statsX, top, bottom, compactLayout);
    }

    private void drawWindowsStats(
        CharacterData character,
        float x,
        float top,
        float bottom,
        boolean compactLayout
    ) {
        int[] values = {
            character.vitality, character.strength, character.durability, character.speed,
            character.combatAbility, character.cursedEnergyReserves,
            character.cursedEnergyEfficiency, character.cursedEnergyOutput,
            character.jujutsuSkill, character.cursedTechniqueMastery
        };
        int screenWidth = Gdx.graphics.getWidth();
        if (compactLayout) {
            drawWindowsCompactStats(character, values, x, top, bottom, screenWidth);
            return;
        }

        float barOffset;
        float barWidth;
        float valueRight;
        float descriptionWidth;
        if (screenWidth >= 2000) {
            barOffset = 250f;
            barWidth = 880f;
            valueRight = 1165f;
            descriptionWidth = 1260f;
        } else if (screenWidth >= 1450) {
            barOffset = 190f;
            barWidth = 260f;
            valueRight = 485f;
            descriptionWidth = 485f;
        } else {
            barOffset = 150f;
            barWidth = 170f;
            valueRight = 350f;
            descriptionWidth = 350f;
        }
        float rowHeight = 40f;
        float barHeight = 20f;
        BitmapFont font = assets.fontSmall;
        float bstBaseline = top - 24f;
        if (bstBaseline - 22.5f < bottom) return;
        drawWindowsBst(character, font, x, bstBaseline);
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        font.getData().setScale(
            originalScaleX * WINDOWS_STATS_FONT_SCALE,
            originalScaleY * WINDOWS_STATS_FONT_SCALE);
        try {
            for (int i = 0; i < values.length; i++) {
                float baseline = top - 78f - i * rowHeight;
                if (baseline - rowHeight < bottom) break;
                String value = String.valueOf(values[i]);
                font.setColor(BattleUiAssets.TEXT);
                drawBold(font, STAT_LABELS[i], x, baseline);
                drawStatBar(
                    values[i], x + barOffset, baseline - barHeight + 2f, barWidth, barHeight);
                drawBold(font, value, x + valueRight - textWidth(font, value), baseline);
            }

            float descriptionTop = top - 475f;
            if (descriptionTop - 32f <= bottom) return;
            font.setColor(BattleUiAssets.MUTED);
            font.draw(batch, "CHARACTER PROFILE", x, descriptionTop);
            drawWindowsWrappedText(
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

    private void drawWindowsCompactStats(
        CharacterData character,
        int[] values,
        float x,
        float top,
        float bottom,
        int screenWidth
    ) {
        float secondColumnOffset;
        float barOffset;
        float barWidth;
        float valueRight;
        if (screenWidth >= 2000) {
            secondColumnOffset = 430f;
            barOffset = 180f;
            barWidth = 165f;
            valueRight = 395f;
        } else if (screenWidth >= 1450) {
            secondColumnOffset = 300f;
            barOffset = 150f;
            barWidth = 85f;
            valueRight = 285f;
        } else {
            secondColumnOffset = 240f;
            barOffset = 125f;
            barWidth = 55f;
            valueRight = 225f;
        }

        BitmapFont font = assets.fontSmall;
        float bstBaseline = top;
        if (bstBaseline - 22.5f >= bottom) {
            drawWindowsBst(character, font, x, bstBaseline);
        }
        for (int i = 0; i < values.length; i++) {
            int column = i / 5;
            int row = i % 5;
            float itemX = x + column * secondColumnOffset;
            float baseline = top - 36f - row * 24f;
            if (baseline - 22.5f < bottom) continue;
            String value = String.valueOf(values[i]);
            font.setColor(BattleUiAssets.TEXT);
            drawBold(font, STAT_LABELS[i], itemX, baseline);
            drawStatBar(values[i], itemX + barOffset, baseline - 8f, barWidth, 10f);
            drawBold(font, value,
                itemX + valueRight - textWidth(font, value), baseline);
        }
    }

    private void drawWindowsBst(
        CharacterData character,
        BitmapFont font,
        float x,
        float baseline
    ) {
        String total = String.valueOf(baseStatTotal(character));
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        font.getData().setScale(
            originalScaleX * WINDOWS_BST_FONT_SCALE,
            originalScaleY * WINDOWS_BST_FONT_SCALE);
        font.setColor(BattleUiAssets.TEXT);
        drawBold(font, "BST:", x, baseline);
        drawBold(font, total, x + 82f, baseline);
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

    private void drawWindowsTechniqueSection(
        CharacterData character,
        float x,
        float width,
        float top,
        float bottom,
        boolean compactLayout
    ) {
        int screenWidth = Gdx.graphics.getWidth();
        float columnWidth;
        float rightColumnOffset;
        float dividerOffset;
        float listWidth;
        float secondMovesOffset;
        float abilitiesOffset;
        if (screenWidth >= 2000) {
            columnWidth = 945f;
            rightColumnOffset = 969f;
            dividerOffset = 957f;
            listWidth = 295f;
            secondMovesOffset = 315f;
            abilitiesOffset = 630f;
        } else if (screenWidth >= 1450) {
            columnWidth = 535f;
            rightColumnOffset = 559f;
            dividerOffset = 547f;
            listWidth = 165f;
            secondMovesOffset = 185f;
            abilitiesOffset = 370f;
        } else {
            columnWidth = 414f;
            rightColumnOffset = 434f;
            dividerOffset = 424f;
            listWidth = 124f;
            secondMovesOffset = 145f;
            abilitiesOffset = 290f;
        }

        String techniqueName = hasCursedTechnique(character)
            ? character.innateTechniqueName : "NONE";
        BitmapFont titleFont = compactLayout ? assets.fontSmall : assets.fontMedium;
        titleFont.setColor(BattleUiAssets.TEXT);
        String title = "CURSED TECHNIQUE: " + techniqueName;
        titleFont.draw(batch, fitOrEllipsize(titleFont, title, width), x, top);

        float contentTop = top - (compactLayout ? 24f : 36f);
        if (contentTop - 24f <= bottom) return;
        float rightX = x + rightColumnOffset;
        float dividerX = x + dividerOffset;
        batch.setColor(new Color(0.560f, 0.640f, 0.800f, 1f));
        batch.draw(assets.battleUi.pixel,
            dividerX, bottom, 2f, Math.max(0f, contentTop - bottom));
        batch.setColor(Color.WHITE);

        assets.fontSmall.setColor(BattleUiAssets.MUTED);
        assets.fontSmall.draw(batch, "TECHNIQUE DESCRIPTION", x, contentTop);
        String description = profileTechnique != null
            && profileTechnique.description != null
            && !profileTechnique.description.isBlank()
                ? displayDescription(profileTechnique.description)
                : (hasCursedTechnique(character)
                    ? "No technique description available."
                    : "This character does not possess an innate cursed technique.");
        drawWindowsWrappedText(
            description,
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
        assets.fontSmall.setColor(BattleUiAssets.MUTED);
        assets.fontSmall.draw(batch, "MOVES", rightX, contentTop);
        if (!secondMoves.isEmpty()) {
            assets.fontSmall.draw(batch, "CONT.", secondMovesX, contentTop);
        }
        assets.fontSmall.draw(batch, "ABILITIES", abilitiesX, contentTop);
        drawWindowsPointList(
            firstMoves,
            rightX,
            contentTop - 24f,
            listWidth,
            bottom);
        if (!secondMoves.isEmpty()) {
            drawWindowsPointList(
                secondMoves,
                secondMovesX,
                contentTop - 24f,
                listWidth,
                bottom);
        }
        drawWindowsPointList(
            profileTechniqueAbilities.stream().map(Ability::getName).toList(),
            abilitiesX,
            contentTop - 24f,
            listWidth,
            bottom);
    }

    private void drawWindowsWrappedText(
        String value,
        float x,
        float top,
        float width,
        float bottom,
        Color color
    ) {
        drawWindowsWrappedText(value, x, top, width, bottom, color, 22.5f);
    }

    private void drawWindowsWrappedText(
        String value,
        float x,
        float top,
        float width,
        float bottom,
        Color color,
        float lineStep
    ) {
        String text = value == null || value.isBlank() ? "-" : value;
        assets.fontSmall.setColor(color);
        float baseline = top;
        for (String line : wrap(assets.fontSmall, text, width)) {
            if (baseline < bottom + 4f) break;
            assets.fontSmall.draw(batch, line, x, baseline);
            baseline -= lineStep;
        }
    }

    private void drawWindowsPointList(
        List<String> values,
        float x,
        float top,
        float width,
        float bottom
    ) {
        if (values.isEmpty()) {
            if (top < bottom + 4f) return;
            assets.fontSmall.setColor(BattleUiAssets.MUTED);
            assets.fontSmall.draw(batch, "None learned.", x, top);
            return;
        }
        int capacity = Math.max(0,
            1 + (int) Math.floor((top - bottom - 4f) / 22.5f));
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
            assets.fontSmall.setColor(BattleUiAssets.TEXT);
            assets.fontSmall.draw(batch,
                fitOrEllipsize(assets.fontSmall, value, width - 15f), x + 15f, baseline);
            baseline -= 22.5f;
        }
    }

    private void drawStats(CharacterData character, float x, float width, float topY, float rowHeight,
                           BitmapFont font) {
        int[] values = {
            character.vitality, character.strength, character.durability, character.speed, character.combatAbility,
            character.cursedEnergyReserves, character.cursedEnergyEfficiency, character.cursedEnergyOutput,
            character.jujutsuSkill, character.cursedTechniqueMastery
        };
        for (int i = 0; i < values.length; i++) {
            float y = topY - i * rowHeight;
            String value = String.valueOf(values[i]);
            float valueX = x + width - textWidth(font, value);
            float barX = x + width * 0.43f;
            float barWidth = Math.max(0f, valueX - barX - 8f);
            font.setColor(BattleUiAssets.TEXT);
            drawBold(font, STAT_LABELS[i], x, y);
            drawStatBar(values[i], barX, y - rowHeight * 0.55f,
                barWidth, Math.max(8f, rowHeight * 0.43f));
            drawBold(font, value, valueX, y);
        }
    }

    private static int baseStatTotal(CharacterData character) {
        return character.vitality + character.strength + character.durability + character.speed
            + character.combatAbility + character.cursedEnergyReserves + character.cursedEnergyEfficiency
            + character.cursedEnergyOutput + character.jujutsuSkill + character.cursedTechniqueMastery;
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

    private void drawDescription(String description, float x, float width, float topY, float bottomY,
                                 BitmapFont font) {
        if (topY < bottomY + font.getCapHeight()) return;
        String text = description == null || description.isBlank() ? "No character description." : description;
        font.setColor(BattleUiAssets.MUTED);
        font.draw(batch, "DESCRIPTION", x, topY);
        font.setColor(BattleUiAssets.TEXT);
        List<String> lines = wrap(font, text, width);
        float lineY = topY - (windowsLayout ? 27f : 18f);
        for (String line : lines) {
            if (lineY < bottomY + (windowsLayout ? 15f : 10f)) break;
            font.draw(batch, line, x, lineY);
            lineY -= font.getLineHeight() + (windowsLayout ? 4.5f : 3f);
        }
    }

    private List<Move> learnedMovesFor(CharacterData character) {
        if (movesCharacter == character) return learnedMoves;

        movesCharacter = character;
        resetMoveScroll();
        learnedMovesError = null;
        moveSetSaveError = null;
        profileAbilities = List.of();
        profileTechniqueMoves = List.of();
        profileTechniqueAbilities = List.of();
        profileTechnique = windowsLayout && hasCursedTechnique(character)
            ? techniqueRepo.findByName(character.innateTechniqueName).orElse(null)
            : null;
        profileCombatant = null;
        profileCharacter = null;
        learnedMoveCeCosts = List.of();
        learnedDrawerCards = List.of();
        try {
            com.jjktbf.model.character.Character resolved = character.toCharacter(
                moveRepo, abilityRepo, techniqueRepo, cursedToolRepo);
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

            if (windowsLayout && hasCursedTechnique(character)) {
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

    private void rebuildLearnedDrawerCards() {
        float cardWidth = learnedDrawerCardWidth();
        float cardHeight = learnedDrawerCardHeight();
        float geometryScale = windowsLayout ? WINDOWS_FULL_MOVE_CARD_SCALE : 1f;
        List<MoveCardView> cards = new ArrayList<>(learnedMoves.size());
        for (Move move : learnedMoves) {
            MoveCardView card = new MoveCardView(
                move, 0f, 0f, geometryScale, cardWidth, cardHeight, 5,
                windowsLayout ? WINDOWS_MIN_SMALL_FONT_SCALE : 0.3f);
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
        return moveSetDrafts.computeIfAbsent(character.id, ignored -> {
            return new ArrayList<>(profileCharacter.getMoveSet().stream()
                .map(Move::getId)
                .toList());
        });
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
        if (height <= 0f) return;

        assets.battleUi.palette.draw(batch, x, y, width, height);
        float padding = movePanelPadding();
        float headerHeight = movePanelHeaderHeight();
        List<Move> selected = moveSetMovesFor(character);
        String title = selected.isEmpty() ? "MOVE SET" : "MOVE SET (" + selected.size() + ")";
        assets.fontSmall.setColor(new Color(0.720f, 0.800f, 0.950f, 1f));
        assets.fontSmall.draw(batch, title, x + padding,
            y + height - (windowsLayout ? 12f : 8f));
        drawMoveSetCounters(selected, x, y, width, height, padding);

        if (learnedMovesError != null) {
            assets.fontSmall.setColor(Color.RED);
            assets.fontSmall.draw(batch, "MOVE DATA UNAVAILABLE", x + padding, y + height / 2f);
            return;
        }
        if (moveSetSaveError != null) {
            assets.fontSmall.setColor(Color.RED);
            assets.fontSmall.draw(batch, moveSetSaveError,
                x + padding, y + height / 2f);
        }

        float viewportHeight = Math.min(
            moveSetSegmentHeight(), Math.max(0f, height - headerHeight - padding * 2f));
        float viewportWidth = Math.max(0f, width - padding * 2f);
        moveSetViewportBounds.set(x + padding, y + padding, viewportWidth, viewportHeight);
        if (viewportWidth <= 0f || viewportHeight <= 0f) return;
        if (selected.isEmpty()) {
            assets.fontSmall.setColor(BattleUiAssets.MUTED);
            assets.fontSmall.draw(batch,
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
            view.draw(batch, assets.fontSmall, assets.battleUi);
            views.add(view);
        }
        if (draggingLearnedMove && isVisibleMoveSetDropTarget(movePointerX, movePointerY)) {
            int insertion = learnedOrderInsertionIndex(pressedLearnedMove, selected);
            float markerX = moveSetViewportBounds.x
                + insertion * (segmentWidth + gap) - moveSetScrollOffset - gap / 2f;
            batch.setColor(BattleUiAssets.YELLOW);
            batch.draw(assets.battleUi.pixel, markerX, moveSetViewportBounds.y,
                windowsLayout ? 4f : 3f, moveSetViewportBounds.height);
            batch.setColor(Color.WHITE);
        }
        endClip();
        moveSetViews = List.copyOf(views);

        if (moveSetScrollMax > 0f) {
            float trackHeight = windowsLayout ? 7f : 5f;
            float trackY = y + Math.max(1f, padding / 4f);
            batch.setColor(BattleUiAssets.INK);
            batch.draw(assets.battleUi.pixel, moveSetViewportBounds.x, trackY,
                moveSetViewportBounds.width, trackHeight);
            float thumbWidth = Math.max(windowsLayout ? 42f : 28f,
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
        assets.fontSmall.setColor(new Color(0.720f, 0.800f, 0.950f, 1f));
        assets.fontSmall.draw(batch, counters,
            x + width - padding - textWidth(assets.fontSmall, counters),
            y + height - (windowsLayout ? 12f : 8f));
    }

    private void layoutLearnedDrawer(float screenWidth, float screenHeight) {
        float padding = learnedDrawerPadding();
        float drawerWidth = learnedDrawerCardWidth() + padding * 2f
            + learnedDrawerScrollbarWidth() + (windowsLayout ? 6f : 4f);
        float drawerX = Math.max(0f, screenWidth - drawerWidth);
        float drawerTop = headerBounds.y + headerBounds.height;
        learnedDrawerBounds.set(
            drawerX, listBounds.y, drawerWidth, drawerTop - listBounds.y);
        float toggleWidth = windowsLayout ? 174f : 116f;
        float toggleHeight = windowsLayout ? 48f : 32f;
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
        assets.fontSmall.setColor(new Color(0.720f, 0.800f, 0.950f, 1f));
        assets.fontSmall.draw(batch, title,
            learnedDrawerBounds.x + learnedDrawerPadding(),
            learnedDrawerBounds.y + learnedDrawerBounds.height
                - (windowsLayout ? 18f : 12f));

        if (learnedMovesError != null) {
            assets.fontSmall.setColor(Color.RED);
            assets.fontSmall.draw(batch, "MOVE DATA UNAVAILABLE",
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
            card.draw(batch, assets.fontSmall, assets.fontSmall, assets.battleUi, ceCost);
            visibleIndex++;
        }
        endClip();
        if (availableCount == 0) {
            assets.fontSmall.setColor(BattleUiAssets.MUTED);
            assets.fontSmall.draw(batch, "Every learned move is in the move set.",
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
        float thumbHeight = Math.max(windowsLayout ? 36f : 24f,
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
        assets.fontSmall.setColor(BattleUiAssets.TEXT);
        assets.fontSmall.draw(batch, label,
            learnedDrawerToggleBounds.x
                + (learnedDrawerToggleBounds.width - textWidth(assets.fontSmall, label)) / 2f,
            learnedDrawerToggleBounds.y
                + learnedDrawerToggleBounds.height / 2f
                + assets.fontSmall.getCapHeight() / 2f);
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
        ghost.draw(batch, assets.fontSmall, assets.battleUi);
    }

    private boolean handleMoveSetTouchDown(float x, float y, int button) {
        movePointerX = x;
        movePointerY = y;
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
            if (removeMoveFromSet(characters.get(cursorIndex), segmentIndex)) {
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
        CharacterData character = characters.get(cursorIndex);
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
        Set<String> selected = Set.copyOf(moveSetIdsFor(characters.get(cursorIndex)));
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
        float x = Gdx.input.getX();
        float y = Gdx.graphics.getHeight() - Gdx.input.getY();
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
        float x = Gdx.input.getX();
        float y = Gdx.graphics.getHeight() - Gdx.input.getY();
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
        return windowsLayout ? WINDOWS_MOVE_SET_SEGMENT_WIDTH : MOVE_SET_SEGMENT_WIDTH;
    }

    private float moveSetSegmentHeight() {
        return windowsLayout ? WINDOWS_MOVE_SET_SEGMENT_HEIGHT : MOVE_SET_SEGMENT_HEIGHT;
    }

    private float moveSetSegmentGap() {
        return windowsLayout ? WINDOWS_MOVE_SET_SEGMENT_GAP : MOVE_SET_SEGMENT_GAP;
    }

    private float learnedDrawerPadding() {
        return windowsLayout ? WINDOWS_LEARNED_DRAWER_PADDING : LEARNED_DRAWER_PADDING;
    }

    private float learnedDrawerHeaderHeight() {
        return windowsLayout
            ? WINDOWS_LEARNED_DRAWER_HEADER_HEIGHT : LEARNED_DRAWER_HEADER_HEIGHT;
    }

    private float learnedDrawerScrollbarWidth() {
        return windowsLayout
            ? WINDOWS_LEARNED_DRAWER_SCROLLBAR_WIDTH : LEARNED_DRAWER_SCROLLBAR_WIDTH;
    }

    private float learnedDrawerCardGap() {
        return windowsLayout ? WINDOWS_LEARNED_DRAWER_CARD_GAP : LEARNED_DRAWER_CARD_GAP;
    }

    private float learnedDrawerCardWidth() {
        return windowsLayout ? WINDOWS_FULL_MOVE_CARD_WIDTH : MoveCardView.CARD_W;
    }

    private float learnedDrawerCardHeight() {
        return windowsLayout ? WINDOWS_FULL_MOVE_CARD_HEIGHT : MoveCardView.CARD_H;
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
        float scaleX = Gdx.graphics.getBackBufferWidth() / (float) Gdx.graphics.getWidth();
        float scaleY = Gdx.graphics.getBackBufferHeight() / (float) Gdx.graphics.getHeight();
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(Math.round(bounds.x * scaleX), Math.round(bounds.y * scaleY),
            Math.round(bounds.width * scaleX), Math.round(bounds.height * scaleY));
    }

    private void endClip() {
        batch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private boolean scrollRoster(float amount) {
        if (!windowsLayout || amount == 0f || rosterScrollMax <= 0f) return false;
        float pointerX = Gdx.input.getX();
        float pointerY = Gdx.graphics.getHeight() - Gdx.input.getY();
        if (!rosterViewportBounds.contains(pointerX, pointerY)) return false;

        rosterScrollOffset = clamp(
            rosterScrollOffset + amount * WINDOWS_ROW_HEIGHT,
            0f,
            rosterScrollMax);
        return true;
    }

    private void revealRosterCursor() {
        if (!windowsLayout || characters.isEmpty()) return;
        rosterScrollOffset = rosterScrollOffsetForSelection(
            rosterScrollOffset,
            cursorIndex,
            characters.size(),
            WINDOWS_ROW_HEIGHT,
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

    static float windowsRosterWidth(float screenWidth) {
        return Math.max(0f, screenWidth * WINDOWS_ROSTER_WIDTH_RATIO);
    }

    static float windowsTechniqueSectionHeight(float detailHeight) {
        boolean compact = detailHeight < 1100f;
        float contentHeight = Math.max(0f,
            detailHeight - WINDOWS_PROFILE_PADDING * 2f - WINDOWS_PROFILE_TITLE_GAP);
        float movesHeight = Math.min(windowsMoveSetPanelHeight(), contentHeight);
        float panelGap = contentHeight > movesHeight
            ? (compact
                ? WINDOWS_COMPACT_PROFILE_SECTION_GAP : WINDOWS_PROFILE_SECTION_GAP)
            : 0f;
        float infoHeight = Math.max(0f, contentHeight - movesHeight - panelGap);
        float summaryHeight = Math.min(
            compact
                ? WINDOWS_COMPACT_PROFILE_SUMMARY_HEIGHT : WINDOWS_PROFILE_SUMMARY_HEIGHT,
            infoHeight);
        float techniqueGap = compact
            ? WINDOWS_COMPACT_PROFILE_SECTION_GAP : WINDOWS_PROFILE_SECTION_GAP;
        return Math.max(0f, infoHeight - summaryHeight - techniqueGap);
    }

    private static float windowsMoveSetPanelHeight() {
        return WINDOWS_MOVE_PANEL_HEADER_HEIGHT + WINDOWS_MOVE_PANEL_PADDING * 2f
            + WINDOWS_MOVE_SET_SEGMENT_HEIGHT;
    }

    static int windowsTechniqueVisibleRows(float detailHeight) {
        boolean compact = detailHeight < 1100f;
        float reservedHeight = compact ? 52f : 64f;
        float available = windowsTechniqueSectionHeight(detailHeight) - reservedHeight;
        return available < 0f ? 0 : 1 + (int) Math.floor(available / 22.5f);
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
        return windowsLayout ? WINDOWS_ROW_HEIGHT : ROW_HEIGHT;
    }

    private float movePanelGap() {
        return windowsLayout ? WINDOWS_MOVE_PANEL_GAP : MOVE_PANEL_GAP;
    }

    private float movePanelPadding() {
        return windowsLayout ? WINDOWS_MOVE_PANEL_PADDING : MOVE_PANEL_PADDING;
    }

    private float movePanelHeaderHeight() {
        return windowsLayout ? WINDOWS_MOVE_PANEL_HEADER_HEIGHT : MOVE_PANEL_HEADER_HEIGHT;
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
