package com.jjktbf.graphics.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.jjktbf.graphics.AssetLoader;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.audio.BattleAudioRouter;
import com.jjktbf.graphics.audio.SoundCue;
import com.jjktbf.graphics.multiplayer.MatchWebSocketClient;
import com.jjktbf.graphics.multiplayer.MultiplayerMatchService;
import com.jjktbf.graphics.multiplayer.MultiplayerSession;
import com.jjktbf.graphics.ui.CombatantPanel;
import com.jjktbf.graphics.ui.AbilityStateMeter;
import com.jjktbf.graphics.ui.MiraclesMeter;
import com.jjktbf.graphics.ui.RatioMeter;
import com.jjktbf.graphics.ui.battle.BattleUiAssets;
import com.jjktbf.graphics.ui.battle.PlanningPanel;
import com.jjktbf.graphics.ui.battle.TeamPlanningPanel;
import com.jjktbf.graphics.ui.battle.WindowsBattleCanvas;
import com.jjktbf.graphics.ui.profile.BattleUiLayout;
import com.jjktbf.graphics.ui.profile.UiProfile;
import com.jjktbf.graphics.multiplayer.TargetListSupport;
import com.jjktbf.model.character.Character;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.character.coded.CursedSpeechAbility;
import com.jjktbf.model.character.coded.MiraclesAbility;
import com.jjktbf.model.character.coded.RatioAbility;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.BattleTeam;
import com.jjktbf.model.combat.BattleTeamId;
import com.jjktbf.model.combat.CeEfficiencyCalculator;
import com.jjktbf.model.combat.CombatantId;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.TeamBattlePlan;
import com.jjktbf.model.domain.DomainClash;
import com.jjktbf.model.domain.DomainInstance;
import com.jjktbf.model.move.HitComponent;
import com.jjktbf.model.move.AttackLaunchMode;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.multiplayer.protocol.BattleEventState;
import com.jjktbf.multiplayer.protocol.BattleEventType;
import com.jjktbf.multiplayer.protocol.BattlePhase;
import com.jjktbf.multiplayer.protocol.ActionSegmentState;
import com.jjktbf.multiplayer.protocol.ActionSegmentStatus;
import com.jjktbf.multiplayer.protocol.CharacterState;
import com.jjktbf.multiplayer.protocol.DomainBattlefieldState;
import com.jjktbf.multiplayer.protocol.DomainClashState;
import com.jjktbf.multiplayer.protocol.DomainState;
import com.jjktbf.multiplayer.protocol.ErrorResponse;
import com.jjktbf.multiplayer.protocol.HitComponentState;
import com.jjktbf.multiplayer.protocol.MatchSetup;
import com.jjktbf.multiplayer.protocol.MatchState;
import com.jjktbf.multiplayer.protocol.MatchStatus;
import com.jjktbf.multiplayer.protocol.MoveState;
import com.jjktbf.multiplayer.protocol.PlayerSide;
import com.jjktbf.multiplayer.protocol.PlayerState;
import com.jjktbf.multiplayer.protocol.RoundStartCharacterState;
import com.jjktbf.multiplayer.protocol.SocketMessage;
import com.jjktbf.view.BattleView;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Graphics implementation of BattleView.
 *
 * Mac keeps the dedicated planner and classic execution layout. Windows uses a
 * unified composition with a persistent top-left battle log, a top-anchored
 * execution field, and a bottom-anchored planning/action section.
 *
 * Threading note:
 *   BattleController calls promptBattlePlan() synchronously.
 *   The graphics loop must therefore run the controller on a background thread
 *   (started by JJKGame.startBattle) while posting render state back to the
 *   LibGDX render thread via Gdx.app.postRunnable().
 *
 *   promptBattlePlan() blocks the controller thread until the player
 *   clicks "Lock In" to confirm their plan.
 */
public class BattleScreen implements Screen, BattleView {

    private enum BattleMode { LOCAL, MULTIPLAYER }

    /** Max raw messages retained in the battle log; older ones are dropped. */
    private static final int   LOG_MAX_STORED = 50;
    /**
     * Vertical step between battle-log lines, as a multiple of the font's cap
     * height (the visible glyph height). Cap height is used rather than line
     * height because the ratio of line-height to cap-height varies a lot between
     * fonts (Press Start 2P ≈ 1.15, Atlantis International ≈ 1.8), so spacing
     * relative to line height would drift whenever the font changes. Cap height
     * tracks the actual on-screen text size, keeping the gap consistent.
     */
    private static final float LOG_LINE_SPACING = 1.7f;
    /** Per-tick hold during resolution, in milliseconds. */
    private static final int   TICK_DURATION_MS        = 100;
    private static final long  PLANNING_AUTO_SUBMIT_LEAD_MILLIS = 250L;
    private static final float FAST_FORWARD_MULTIPLIER = 2f;
    private static final float SKIP_ACTIVE_FLASH_SECONDS = 0.16f;
    private static final float SPEED_CONTROL_GAP = 8f;
    private static final float SPEED_CONTROL_PANEL_INSET = 8f;
    private static final float SPEED_CONTROL_SIZE_MAX = 54f;
    /**
     * Move-unleash animation length. This is visual-only and does not delay
     * resolution after a move's dialogue finishes.
     */
    private static final float MOVE_EFFECT_DURATION_SECONDS = 3f;
    /** Successful blocks use the same grow/fade treatment in a much shorter burst. */
    private static final float BLOCK_EFFECT_DURATION_SECONDS = 0.75f;
    /**
     * Per-hit impact flash length. On a multi-hit move every connecting hit
     * punches its own icon onto the defender's sprite so each hit reads as a
     * distinct strike, not a single unleash.
     */
    private static final float HIT_FLASH_DURATION_SECONDS  = 0.6f;
    /** Fast classic monster-battle sink used when a combatant is defeated. */
    private static final float FAINT_SLIDE_DURATION_SECONDS = 0.42f;
    /**
     * The one entrance animation played whenever a combatant arrives or
     * changes on the field — summoned or transformed, ally or enemy: the
     * sprite starts tiny and pure white, then grows to full size while the
     * white overlay fades into its true palette.
     */
    private static final float ENTRANCE_GROW_DURATION_SECONDS = 0.5f;
    /** Chars revealed per second when typing a log line out letter-by-letter. */
    private static final float LOG_TYPE_RATE_CPS       = 40f;
    /**
     * Beat held after a log line finishes typing before it commits and the
     * battle advances, so each sentence gets a moment to land.
     */
    private static final float LOG_TYPE_TAIL_SECONDS   = 0.2f;
    /**
     * Pixels the log scrolls per mouse-wheel notch, in {@link #LOG_LINE_SPACING}
     * units (cap height). One notch ≈ one wrapped row, so a flick covers a few
     * lines without overshooting.
     */
    private static final float LOG_SCROLL_STEP_ROWS    = 1f;
    private static final float COMBATANT_HUD_SCALE     = 1.25f;
    private static final float BASE_PLATE_VISIBLE_LEFT_RATIO = 0.06f;
    private static final float BASE_PLATE_VISIBLE_BOTTOM_RATIO = 0.38f;
    private static final float BASE_PLATE_VISIBLE_HEIGHT_RATIO = 0.28f;
    private static final float WINDOWS_ENEMY_PLATE_UPWARD_NUDGE = 10f;
    private static final float HUD_PLATE_CLEARANCE = 12f;
    private static final int   MAX_VISIBLE_COMBATANTS_PER_SIDE = 4;
    static final float WINDOWS_EXECUTION_X = WindowsBattleCanvas.LEFT_COLUMN_WIDTH;
    static final float WINDOWS_EXECUTION_Y = WindowsBattleCanvas.BOTTOM_SECTION_HEIGHT;
    static final float WINDOWS_EXECUTION_WIDTH =
        WindowsBattleCanvas.WIDTH - WINDOWS_EXECUTION_X;
    static final float WINDOWS_EXECUTION_HEIGHT =
        WindowsBattleCanvas.HEIGHT - WINDOWS_EXECUTION_Y;
    private static final float WINDOWS_FIGHTER_SCALE = 1.25f;
    static final float WINDOWS_FIGHTER_SPRITE_SIZE = 358.4f * WINDOWS_FIGHTER_SCALE;
    private static final float WINDOWS_BASE_PLATE_SIZE = WINDOWS_FIGHTER_SPRITE_SIZE * 2f;
    static final float WINDOWS_BOTTOM_SECTION_HEIGHT = WindowsBattleCanvas.BOTTOM_SECTION_HEIGHT;
    static final float WINDOWS_PLAYER_FIGHTER_ZONE_X = 581f;
    static final float WINDOWS_PLAYER_FIGHTER_ZONE_WIDTH = 1132f;
    static final float WINDOWS_PLAYER_FIGHTER_ZONE_Y = WINDOWS_BOTTOM_SECTION_HEIGHT;
    static final float WINDOWS_PLAYER_FIGHTER_ZONE_HEIGHT = WINDOWS_EXECUTION_HEIGHT;
    static final float WINDOWS_ENEMY_FIGHTER_ZONE_X = 1605f;
    static final float WINDOWS_ENEMY_FIGHTER_ZONE_WIDTH = 955f;
    static final float WINDOWS_ENEMY_FIGHTER_ZONE_Y =
        WindowsBattleCanvas.HEIGHT - WINDOWS_FIGHTER_SPRITE_SIZE
            - WindowsBattleCanvas.PLANNING_HEIGHT_REDUCTION;
    static final float WINDOWS_ENEMY_FIGHTER_ZONE_HEIGHT = WINDOWS_FIGHTER_SPRITE_SIZE;
    static final float WINDOWS_PLAYER_FIGHTER_CENTER_X =
        WINDOWS_PLAYER_FIGHTER_ZONE_X + WINDOWS_PLAYER_FIGHTER_ZONE_WIDTH / 2f;
    static final float WINDOWS_ENEMY_FIGHTER_CENTER_X =
        WINDOWS_ENEMY_FIGHTER_ZONE_X + WINDOWS_ENEMY_FIGHTER_ZONE_WIDTH / 2f;
    static final float WINDOWS_PLAYER_FIGHTER_BOTTOM_Y = WINDOWS_BOTTOM_SECTION_HEIGHT;
    static final float WINDOWS_ENEMY_FIGHTER_BOTTOM_Y = WINDOWS_ENEMY_FIGHTER_ZONE_Y;
    private static final float WINDOWS_HUD_OUTER_MARGIN = 112f;
    private static final float WINDOWS_HUD_PANEL_WIDTH_SCALE = 0.85f;
    private static final float WINDOWS_HUD_PANEL_HEIGHT_SCALE = 0.9f;
    private static final float WINDOWS_ENEMY_HUD_REGION_X = WINDOWS_EXECUTION_X
        + WINDOWS_HUD_OUTER_MARGIN;
    private static final float WINDOWS_ENEMY_HUD_REGION_Y =
        1110f - WindowsBattleCanvas.PLANNING_HEIGHT_REDUCTION;
    private static final float WINDOWS_ENEMY_HUD_REGION_WIDTH =
        712f * WINDOWS_HUD_PANEL_WIDTH_SCALE;
    private static final float WINDOWS_ENEMY_HUD_REGION_HEIGHT = 321f;
    private static final float WINDOWS_PLAYER_HUD_REGION_Y =
        599f - WindowsBattleCanvas.PLANNING_HEIGHT_REDUCTION;
    private static final float WINDOWS_PLAYER_HUD_REGION_WIDTH =
        740f * WINDOWS_HUD_PANEL_WIDTH_SCALE;
    private static final float WINDOWS_PLAYER_HUD_REGION_HEIGHT = 365f;
    private static final float WINDOWS_PLAYER_HUD_REGION_X = WindowsBattleCanvas.WIDTH
        - WINDOWS_HUD_OUTER_MARGIN - WINDOWS_PLAYER_HUD_REGION_WIDTH;
    private static final float WINDOWS_HUD_HEIGHT =
        151.875f * WINDOWS_HUD_PANEL_HEIGHT_SCALE;
    private static final float WINDOWS_HUD_COLUMN_GAP_SCALE = 0.05f;
    private static final float WINDOWS_HUD_COLUMN_GAP_MIN = 10f;
    private static final float WINDOWS_HUD_ROW_GAP_SCALE = 0.07f;
    private static final float WINDOWS_HUD_ROW_GAP_MIN = 8f;
    private static final float WINDOWS_EXPANDED_HUD_EDGE_MARGIN = 12f;
    private static final float WINDOWS_EXPANDED_PLAYER_CENTER_NUDGE = 28f;
    // Authored from the annotated 2000x1125 screenshot, converted to 2560x1440.
    private static final float WINDOWS_PLAYER_THREE_PLATE_X = 610f;
    private static final float WINDOWS_PLAYER_THREE_PLATE_SIZE = 1286f;
    private static final float WINDOWS_PLAYER_THREE_LEFT_X = 882f;
    private static final float WINDOWS_PLAYER_THREE_MIDDLE_X = 1211f;
    private static final float WINDOWS_PLAYER_THREE_RIGHT_X = 1612f;
    private static final float WINDOWS_ENEMY_PLATE_UPWARD_SHIFT = 88f;
    private static final float WINDOWS_ENEMY_THREE_PLATE_LEFT_X = 1490f;
    private static final float WINDOWS_ENEMY_THREE_LEFT_X = 1695f;
    private static final float WINDOWS_ENEMY_THREE_MIDDLE_X = 2023f;
    private static final float WINDOWS_ENEMY_THREE_RIGHT_X = 2360f;
    private static final float WINDOWS_HUD_TEXT_SCALE = 0.8f;
    private static final float WINDOWS_HUD_BAR_HEIGHT_SCALE = 0.75f;
    private static final float WINDOWS_HUD_BAR_BORDER_SCALE = 0.75f;
    private static final Color WINDOWS_SECTION_DIVIDER =
        new Color(0.82f, 0.86f, 0.92f, 0.92f);
    /** Set to true when debugging timeline playback. */
    private static final boolean SHOW_TICK_COUNTER      = false;

    private final JJKGame     game;
    private final AssetLoader assets;
    private final SpriteBatch batch;
    private BattleUiLayout uiLayout;
    private WindowsBattleCanvas windowsCanvas = WindowsBattleCanvas.fit(
        WindowsBattleCanvas.WIDTH, WindowsBattleCanvas.HEIGHT);
    private final Matrix4 batchProjection = new Matrix4();
    private final Matrix4 batchTransform = new Matrix4();

    /** Guards against double-dispose of native batch resources. */
    private boolean disposed;

    // ── Panels ────────────────────────────────────────────────────────────────
    private CombatantPanel playerPanel;
    private CombatantPanel enemyPanel;
    /** All visible panels in roster order; index zero owns the side's shared plate. */
    private List<CombatantPanel> playerPanels = List.of();
    private List<CombatantPanel> enemyPanels = List.of();
    /** Status occupancy that the current panel geometry was built for. */
    private String executionHudStatusLayoutSignature = "";
    /** Prevents the panel update at the end of layout from recursively rebuilding it. */
    private boolean layingOutExecutionUi;
    private final MiraclesMeter miraclesMeter = new MiraclesMeter();
    private final RatioMeter ratioMeter = new RatioMeter();
    private final AbilityStateMeter abilityStateMeter = new AbilityStateMeter();
    private Texture playerSprite;
    private Texture enemySprite;
    /** Per-side execution sprites in the same order as the local or online render roster. */
    private java.util.List<Texture> playerTeamSprites = java.util.List.of();
    private java.util.List<Texture> enemyTeamSprites = java.util.List.of();
    /** Local render-state snapshots in the same roster order as the panel lists. */
    private volatile List<BattleCombatant> renderPlayerTeam = List.of();
    private volatile List<BattleCombatant> renderEnemyTeam = List.of();
    private volatile BattleState renderLocalState;
    /** Multiplayer presentation rosters are mutated in step with summon/removal playback events. */
    private List<CharacterState> renderOnlinePlayerTeam = List.of();
    private List<CharacterState> renderOnlineEnemyTeam = List.of();
    /** Event-synchronised local HP snapshots, keyed by combatant identity. */
    private final Map<BattleCombatant, LocalHpState> localHpStates =
        new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<BattleCombatant, List<CodedAbilityState>> localAbilityStates =
        new java.util.concurrent.ConcurrentHashMap<>();
    private final Rectangle logBounds = new Rectangle();
    private final Rectangle nextRoundBounds = new Rectangle();
    private final Rectangle fastForwardBounds = new Rectangle();
    private final Rectangle skipBounds = new Rectangle();
    private final Rectangle windowsExecutionClip = windowsExecutionBounds();

    // ── Event log ─────────────────────────────────────────────────────────────
    private final List<String> logLines = new ArrayList<>();
    /**
     * Progressive "typewriter" log reveal. Messages are queued (concurrently —
     * the battle thread enqueues, the render thread drains) and revealed one
     * character at a time by {@link #updateTyping(float)}, then committed to
     * {@link #logLines} after a short tail. {@link #committedLogSeq} is bumped
     * on each commit and read by the battle thread to gate advancement.
     * {@link #displayMessage} bypasses the gate while the execution UI is still
     * hidden (before the first planning phase), since there is nothing on screen
     * for the player to read along with — those lines still type out and commit,
     * they just don't block.
     */
    private final Queue<String> pendingTypingQueue = new ConcurrentLinkedQueue<>();
    private String typingLine;
    private int typingChars;
    private float typingCharTimer;
    private float typingTailTimer;
    /** Bumped on the render thread each time a typing line commits; read by the battle thread. */
    private volatile int committedLogSeq = 0;
    /**
     * Vertical scroll offset of the battle log, in pixels measured up from the
     * newest (bottom) line. Zero rests at the bottom; positive scrolls toward
     * older history. Only adjusted while awaiting the next round (dialogue idle);
     * reset to zero whenever a new line commits so the log snaps back to newest
     * as soon as dialogue resumes.
     */
    private float logScrollOffset = 0f;

    // ── Active Domain banners ────────────────────────────────────────────────
    /**
     * One status line per active Domain/anti-Domain, kept in step with playback
     * events during resolution and re-synced from the authoritative battlefield
     * (local {@code BattleState} or server {@code MatchState}) after each round
     * and on reconnect. Updated on the battle thread as immutable snapshots so
     * the render thread always sees a consistent list.
     */
    private volatile List<DomainBanner> domainBanners = List.of();
    /**
     * Wheel listener installed as the input processor only while awaiting the
     * next round, so the log scrolls on actual scroll-wheel events (LibGDX has
     * no polled wheel API — it is delivered as a {@code scrolled} event). Mirrors
     * the editors' hover-scroll approach, but scoped to this single window so it
     * never competes with the planning panel's own input processor.
     */
    private final InputAdapter logScrollInput = new InputAdapter() {
        @Override
        public boolean scrolled(float amountX, float amountY) {
            if ((!awaitingBattleStart && !awaitingNextRound) || typingInProgress()) return false;
            float x = bottomInputX(Gdx.input.getX());
            float y = windowsUnified()
                ? topInputY(Gdx.input.getY()) : bottomInputY(Gdx.input.getY());
            if (!logBounds.contains(x, y)) return false;
            // amountY < 0 = wheel up (toward older history); invert so up scrolls back.
            adjustLogScroll(-amountY * LOG_SCROLL_STEP_ROWS);
            return true;
        }
    };
    /** Tracks whether {@link #logScrollInput} currently owns Gdx.input, to avoid re-setting it every frame. */
    private boolean logScrollInputAttached = false;

    // ── Move unleash animation (render-thread state) ──────────────────────────
    private Texture unleashedMoveIcon;
    private float unleashedMoveElapsed;
    private float unleashedMoveDurationSeconds = MOVE_EFFECT_DURATION_SECONDS;
    private CombatantPanel unleashedMoveTargetPanel;

    /**
     * Per-hit impact flashes for multi-hit moves. Each connecting hit spawns a
     * short targeted burst on the defender's sprite (attack icon for a damaging
     * hit, defense icon for a blocked/parried/dodged one). Bounded so a long
     * multi-hit chain cannot grow unbounded; oldest flashes age out and drop.
     */
    private final java.util.List<HitFlash> hitFlashes = new java.util.ArrayList<>();

    /** At most one defeat is played at a time; the battle reflows when it completes. */
    private final List<FaintAnimation> faintAnimations = new ArrayList<>();
    /** Prevents terminal compatibility backfill from replaying fighters that already fainted. */
    private final Set<CombatantId> presentedLocalFaints = new HashSet<>();
    /** Summon entrances playing right now; the sprite stays once each completes. */
    private final List<EntranceAnimation> entranceAnimations = new ArrayList<>();

    // ── Move selection state ──────────────────────────────────────────────────
    private volatile boolean inputConfirmed = false;
    private volatile boolean awaitingBattleStart = false;
    private volatile boolean battleStartConfirmed = false;
    private volatile boolean awaitingNextRound = false;
    private volatile boolean nextRoundConfirmed = false;
    /** True while resolution tick calls are streaming; used to pace between ticks. */
    private volatile boolean resolvingTicks = false;
    private volatile boolean playbackControlsOpen = false;
    private boolean nextRoundHovered = false;
    private boolean battleEntranceStarted = false;
    private boolean battleIntroLogged = false;
    private volatile boolean fastForwardActive = false;
    private volatile boolean skipRoundRequested = false;
    private boolean fastForwardHovered = false;
    private boolean skipHovered = false;
    private float skipActiveFlashRemaining = 0f;

    /**
     * Set on the render thread when the player presses Escape to leave a battle
     * early. Read by the controller (battle) thread via {@link #isAborted()} to
     * unwind the loop, and polled by this screen's own blocking spin-waits and
     * paced sleeps so an abort unblocks promptly instead of running to a KO.
     */
    private volatile boolean abortRequested = false;

    // ── Planning panel (two-board timeline UI) ─────────────────────────────────
    private PlanningPanel planningPanel;
    private TeamPlanningPanel teamPlanningPanel;

    // ── Shared render state (written by controller thread, read by render) ────
    private volatile BattleCombatant renderPlayer;
    private volatile BattleCombatant renderEnemy;
    private volatile boolean         battleOver  = false;
    private volatile String          battleResult = "";
    private volatile String          battleResultReason = "";
    private volatile int             currentExecutionTick = 0;
    /**
     * Latched true when the opening execution scene is published and kept true
     * through the rest of the battle.
     */
    private volatile boolean         executionUiActive = false;
    private volatile Thread          localBattleThread;

    // ── Online mode ───────────────────────────────────────────────────────────
    private BattleMode mode = BattleMode.LOCAL;
    private MatchSetup multiplayerSetup;
    private MultiplayerSession multiplayerSession;
    private MultiplayerMatchService multiplayerMatchService;
    private MultiplayerMatchService.Listener multiplayerListener;
    private MultiplayerSession.ConnectionState multiplayerConnectionState =
        MultiplayerSession.ConnectionState.DISCONNECTED;
    private MatchState multiplayerState;
    private PlayerState onlinePlayer;
    private PlayerState onlineEnemy;
    private Map<String, Move> onlineMoves = Map.of();
    private int onlinePlanningRound = -1;
    private int soundedOnlineRound = -1;
    private final Set<String> loggedOnlineEventIds = new HashSet<>();
    private final Set<String> soundedOnlineEventIds = new HashSet<>();
    private boolean onlineCommandPending;
    private long onlinePlanningDeadline = -1L;
    private long onlinePlanningDeadlineNanos;
    private long onlinePlanningServerTimestamp = -1L;
    private boolean onlinePlanningTimedOut;
    private boolean preserveMultiplayerSession;
    private long multiplayerRun;

    private List<BattleEventState> playbackEvents = List.of();
    private List<Integer> playbackActionTicks = List.of();
    private int playbackRound = -1;
    private int playbackEventIndex;
    private int playbackActionIndex;
    private float playbackTickElapsedMs;
    private boolean playbackComplete;
    private boolean playbackReturnsToPlanning;
    private int playedPlanningEventsRound = -1;
    private CodedAbilityState onlinePlayerMiracles;
    private CodedAbilityState onlinePlayerRatio;
    /** Multiplayer resource playback for every displayed fighter, not just roster slot zero. */
    private final Map<OnlineCombatantKey, OnlineResourceState> onlineResourceStates =
        new HashMap<>();
    /** Event-synchronised ability states for multiplayer playback. */
    private final Map<OnlineCombatantKey, List<CodedAbilityState>> onlineAbilityStates =
        new HashMap<>();

    public BattleScreen(JJKGame game, AssetLoader assets) {
        this(game, assets, BattleUiLayout.defaults(UiProfile.MAC));
    }

    public BattleScreen(JJKGame game, AssetLoader assets, BattleUiLayout uiLayout) {
        this.game   = game;
        this.assets = assets;
        this.batch  = new SpriteBatch();
        this.uiLayout = Objects.requireNonNull(uiLayout, "uiLayout").copy();
        this.playerSprite = assets.playerSprite;
        this.enemySprite = assets.enemySprite;
    }

    /** Selects the blocking local controller path before this reusable screen is shown. */
    public void prepareLocal() {
        abortRequested = true;
        localBattleThread = null;
        detachMultiplayerListener();
        mode = BattleMode.LOCAL;
        multiplayerSetup = null;
        multiplayerSession = null;
        multiplayerMatchService = null;
        multiplayerState = null;
        // Reset team-battle render state so a prior team battle doesn't leak into a 1v1.
        renderPlayerTeam = List.of();
        renderEnemyTeam = List.of();
        renderLocalState = null;
        renderOnlinePlayerTeam = List.of();
        renderOnlineEnemyTeam = List.of();
        localHpStates.clear();
        localAbilityStates.clear();
        onlineResourceStates.clear();
        onlineAbilityStates.clear();
        playerPanels = List.of();
        enemyPanels = List.of();
        playerTeamSprites = java.util.List.of();
        enemyTeamSprites = java.util.List.of();
    }

    /** Selects the asynchronous authoritative path before this reusable screen is shown. */
    public void prepareMultiplayer(
        MatchSetup setup,
        MultiplayerSession session,
        MultiplayerMatchService matchService
    ) {
        abortRequested = true;
        localBattleThread = null;
        detachMultiplayerListener();
        mode = BattleMode.MULTIPLAYER;
        multiplayerSetup = Objects.requireNonNull(setup, "setup");
        multiplayerSession = Objects.requireNonNull(session, "session");
        multiplayerMatchService = Objects.requireNonNull(matchService, "matchService");
        renderPlayerTeam = List.of();
        renderEnemyTeam = List.of();
        renderLocalState = null;
        renderOnlinePlayerTeam = List.of();
        renderOnlineEnemyTeam = List.of();
        localHpStates.clear();
        localAbilityStates.clear();
        onlineResourceStates.clear();
        onlineAbilityStates.clear();
    }

    /** Associates local controller callbacks with the current battle run. */
    public void setLocalBattleThread(Thread battleThread) {
        localBattleThread = Objects.requireNonNull(battleThread, "battleThread");
    }

    /** Set the selected characters' side-appropriate battle sprites. */
    public void setCombatantSprites(Texture playerSprite, Texture enemySprite) {
        this.playerSprite = playerSprite != null ? playerSprite : assets.playerSprite;
        this.enemySprite = enemySprite != null ? enemySprite : assets.enemySprite;
        this.playerTeamSprites = java.util.List.of(this.playerSprite);
        this.enemyTeamSprites = java.util.List.of(this.enemySprite);
    }

    /**
     * Set per-side sprite lists for a team battle. Index 0 also seeds the legacy
     * single-sprite fields so 1-fighter rendering is unchanged.
     */
    public void setTeamSprites(java.util.List<Texture> playerSprites,
                               java.util.List<Texture> enemySprites) {
        this.playerTeamSprites = playerSprites == null
            ? java.util.List.of(assets.playerSprite)
            : playerSprites.stream()
                .map(t -> t != null ? t : assets.playerSprite)
                .collect(java.util.stream.Collectors.toList());
        this.enemyTeamSprites = enemySprites == null
            ? java.util.List.of(assets.enemySprite)
            : enemySprites.stream()
                .map(t -> t != null ? t : assets.enemySprite)
                .collect(java.util.stream.Collectors.toList());
        this.playerSprite = this.playerTeamSprites.get(0);
        this.enemySprite = this.enemyTeamSprites.get(0);
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void show() {
        windowsCanvas = WindowsBattleCanvas.fit(
            Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        applyPhysicalBatchTransform();
        Gdx.input.setInputProcessor(null);
        logScrollInputAttached = false;
        planningPanel = null;
        teamPlanningPanel = null;
        logLines.clear();
        pendingTypingQueue.clear();
        typingLine = null;
        typingChars = 0;
        typingCharTimer = 0f;
        typingTailTimer = 0f;
        logScrollOffset = 0f;
        inputConfirmed = false;
        awaitingBattleStart = false;
        battleStartConfirmed = false;
        awaitingNextRound = false;
        nextRoundConfirmed = false;
        resolvingTicks = false;
        playbackControlsOpen = false;
        fastForwardActive = false;
        skipRoundRequested = false;
        fastForwardHovered = false;
        skipHovered = false;
        skipActiveFlashRemaining = 0f;
        battleOver     = false;
        battleResultReason = "";
        abortRequested = false;
        executionUiActive = false;
        currentExecutionTick = 0;
        unleashedMoveIcon = null;
        unleashedMoveElapsed = 0f;
        hitFlashes.clear();
        faintAnimations.clear();
        entranceAnimations.clear();
        presentedLocalFaints.clear();
        localHpStates.clear();
        localAbilityStates.clear();
        onlineResourceStates.clear();
        onlineAbilityStates.clear();
        playbackEvents = List.of();
        playbackActionTicks = List.of();
        playbackRound = -1;
        playbackEventIndex = 0;
        playbackActionIndex = 0;
        playbackTickElapsedMs = 0f;
        playbackComplete = false;
        playbackReturnsToPlanning = false;
        playedPlanningEventsRound = -1;
        onlinePlanningRound = -1;
        soundedOnlineRound = -1;
        loggedOnlineEventIds.clear();
        soundedOnlineEventIds.clear();
        domainBanners = List.of();
        onlineCommandPending = false;
        resetMultiplayerPlanningCountdown();
        battleEntranceStarted = false;
        battleIntroLogged = false;
        preserveMultiplayerSession = false;
        multiplayerState = null;
        onlinePlayer = null;
        onlineEnemy = null;
        onlinePlayerMiracles = null;
        onlinePlayerRatio = null;
        miraclesMeter.clear();
        ratioMeter.clear();
        onlineMoves = Map.of();

        if (mode == BattleMode.MULTIPLAYER) {
            startMultiplayer();
        }
    }

    /** Last frame's delta, shared with widgets that animate (e.g. HP bars). */
    private float frameDelta = 0f;

    @Override
    public void render(float delta) {
        // Scene2D screens use a letterboxed viewport. Restore the full live
        // window before applying the battle canvas transform.
        HdpiUtils.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        float realDelta = Math.max(0f, delta);
        skipActiveFlashRemaining = Math.max(0f, skipActiveFlashRemaining - realDelta);
        float presentationDelta = realDelta * playbackSpeedMultiplier();
        frameDelta = presentationDelta;
        updateMoveUnleashAnimation(presentationDelta);
        updateHitFlashes(presentationDelta);
        updateFaintAnimations(presentationDelta);
        updateEntranceAnimations(presentationDelta);
        if (skipRoundRequested) {
            flushTypingImmediately();
            clearTransientAnimations();
            completeFaintAnimationsImmediately();
            completeEntranceAnimationsImmediately();
            snapPanelAnimations();
        } else {
            updateTyping(presentationDelta);
        }
        if (mode == BattleMode.MULTIPLAYER) {
            updateMultiplayerPlanningTimeout();
            updateMultiplayerPlayback(presentationDelta);
        }
        clearScreen();
        // Escape aborts from any phase, including planning (where the
        // PlanningInputProcessor owns Gdx.input, so handleInput() never runs).
        // isKeyJustPressed() is polled, so it fires regardless of the active
        // input processor.
        if (!battleOver && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            abortBattle();
        }
        handleInput();
        drawAll();
        drawMultiplayerPlanningCountdown();
    }

    /**
     * Leave the battle early and return to the main menu. Sets the abort flag
     * (polled by the controller thread to unwind the loop) and, since the
     * controller may be blocked in one of our own view calls, unblocks those
     * spins too. The actual screen switch happens via postRunnable so it lands
     * on the render thread; hide()/show() tear the planning panel down.
     */
    private void abortBattle() {
        if (abortRequested) return; // already leaving — don't re-trigger
        abortRequested = true;
        game.audio().play(SoundCue.UI_BACK);

        if (mode == BattleMode.MULTIPLAYER) {
            leaveMultiplayer();
            return;
        }

        // Unblock whichever controller-thread view call is parked right now.
        inputConfirmed    = true; // promptBattlePlan
        battleStartConfirmed = true; // awaitBattleStart
        nextRoundConfirmed = true; // awaitNextRound

        // If the planning panel holds the input processor, release it so the
        // main menu's own processor takes over cleanly on the next screen.
        Gdx.app.postRunnable(() -> {
            planningPanel = null;
            teamPlanningPanel = null;
            Gdx.input.setInputProcessor(null);
            game.showMainMenu();
        });
    }

    @Override public void resize(int w, int h) {
        windowsCanvas = WindowsBattleCanvas.fit(w, h);
        applyPhysicalBatchTransform();
        if (planningPanel != null) planningPanel.resize(w, h);
        if (teamPlanningPanel != null) teamPlanningPanel.resize(w, h);
        configurePlanningViewport();
        layoutExecutionUi(w, h);
    }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override
    public void hide() {
        if (mode == BattleMode.MULTIPLAYER) {
            closePlanningPanel();
            detachMultiplayerListener();
        }
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        detachMultiplayerListener();
        batch.dispose();
    }

    // -------------------------------------------------------------------------
    // Input (render thread only)
    // -------------------------------------------------------------------------

    private void handleInput() {
        updateWindowsActionBounds();
        // The new two-board PlanningPanel owns its own drag input processor and
        // Lock In button — skip the legacy click-to-toggle / ENTER flow entirely
        // while it is active.
        if (planningUiEditable()) {
            fastForwardHovered = false;
            skipHovered = false;
            return;
        }

        float x = bottomInputX(Gdx.input.getX());
        float y = bottomInputY(Gdx.input.getY());
        boolean controlsEnabled = playbackControlsEnabled();
        fastForwardHovered = controlsEnabled && fastForwardBounds.contains(x, y);
        skipHovered = controlsEnabled && skipBounds.contains(x, y);
        if (controlsEnabled && Gdx.input.justTouched()) {
            if (skipHovered) {
                requestRoundSkip();
                return;
            }
            if (fastForwardHovered) {
                toggleFastForward();
                return;
            }
        }

        if (awaitingBattleStart || awaitingNextRound) {
            // Install the wheel listener for the duration of this window so the
            // log scrolls on actual scroll-wheel events (LibGDX delivers the
            // wheel only as a `scrolled` event, not via a polled API). The
            // listener only handles the wheel, so the NEXT ROUND click below
            // still works through justTouched() polling.
            if (!logScrollInputAttached) {
                Gdx.input.setInputProcessor(logScrollInput);
                logScrollInputAttached = true;
            }

            if (awaitingBattleStart) {
                nextRoundHovered = battleStartButtonEnabled() && nextRoundBounds.contains(x, y);
                if (Gdx.input.justTouched() && nextRoundHovered) {
                    if (mode == BattleMode.MULTIPLAYER) {
                        if (submitReadyForBattle()) {
                            game.audio().play(SoundCue.UI_CONFIRM);
                        } else {
                            game.audio().play(SoundCue.UI_DENIED);
                        }
                    } else {
                        game.audio().play(SoundCue.UI_CONFIRM);
                        battleStartConfirmed = true;
                    }
                }
                return;
            }

            nextRoundHovered = nextRoundButtonEnabled() && nextRoundBounds.contains(x, y);

            if (Gdx.input.justTouched() && nextRoundHovered) {
                if (mode == BattleMode.MULTIPLAYER) {
                    if (submitReadyNextRound()) {
                        game.audio().play(SoundCue.UI_CONFIRM);
                    } else {
                        game.audio().play(SoundCue.UI_DENIED);
                    }
                } else {
                    game.audio().play(SoundCue.UI_CONFIRM);
                    nextRoundConfirmed = true;
                }
            }
            return;
        }

        // Left the await-next-round window — release the wheel listener so it
        // doesn't swallow input meant for the planning panel or next screen.
        if (logScrollInputAttached) {
            Gdx.input.setInputProcessor(null);
            logScrollInputAttached = false;
        }
        nextRoundHovered = false;
    }

    // -------------------------------------------------------------------------
    // Draw
    // -------------------------------------------------------------------------

    private void clearScreen() {
        // #CDDCFA — light blue, shared across all screens
        Gdx.gl.glClearColor(0.804f, 0.863f, 0.980f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private void drawAll() {
        if (windowsUnified()) {
            drawUnifiedWindows();
            return;
        }
        if (battleOver) { drawBattleOver(); return; }

        // Until the battle thread reaches the first planning phase there is
        // nothing to show — drawing the execution HUD here would flash it for a
        // few frames before the planning panel appears.
        if (!executionUiActive) return;

        // Planning is a dedicated workspace. Drawing the combat HUD behind it
        // made both the board and the move cards compete for attention.
        if (planningPanel != null) {
            planningPanel.draw(batch, assets.fontSmall, assets.fontMedium, assets.fontLarge);
            return;
        }
        if (teamPlanningPanel != null) {
            teamPlanningPanel.draw(batch, assets.fontSmall, assets.fontMedium, assets.fontLarge);
            return;
        }

        float sw = Gdx.graphics.getWidth();
        float sh = Gdx.graphics.getHeight();

        batch.begin();
        drawExecutionBackground(sw, sh);
        List<CombatantHud> enemyHuds = enemyPanel != null && hasEnemyRenderState()
            ? combatantHuds(false) : List.of();
        List<CombatantHud> playerHuds = playerPanel != null && hasPlayerRenderState()
            ? combatantHuds(true) : List.of();
        drawCombatantField(enemyPanels, enemyHuds.size());
        drawCombatantField(playerPanels, playerHuds.size());
        drawCombatantHuds(enemyPanels, enemyHuds, false);
        if (!playerHuds.isEmpty()) {
            drawCombatantHuds(playerPanels, playerHuds, true);
            if (playerPanel != null && faintAnimationFor(playerPanel) == null) {
                miraclesMeter.draw(batch, assets.battleUi, assets.fontLarge);
                ratioMeter.draw(batch, assets.battleUi, assets.fontLarge);
                abilityStateMeter.draw(batch, assets.battleUi, assets.fontSmall);
            }
        }
        drawLog(sw, sh);
        if (speedControlsVisible()) drawSpeedControls();
        drawBattleActionButton();
        if (SHOW_TICK_COUNTER) drawTickCounter(sw, sh);
        drawDomainBanners(sw, sh);
        drawMoveUnleashAnimation(sw, sh);
        drawHitFlashes(sw, sh);
        drawCombatantStatusTooltips(enemyPanels);
        drawCombatantStatusTooltips(playerPanels);
        batch.end();

    }

    private void drawUnifiedWindows() {
        if (!executionUiActive) return;
        updateDisplayedAbilityMeters();

        List<CombatantHud> enemyHuds = enemyPanel != null && hasEnemyRenderState()
            ? combatantHuds(false) : List.of();
        List<CombatantHud> playerHuds = playerPanel != null && hasPlayerRenderState()
            ? combatantHuds(true) : List.of();
        CombatantPanel plannedPanel = activePlannerCombatantPanel();

        applyUnifiedBatchTransform(WindowsBattleCanvas.Anchor.TOP);
        batch.begin();
        beginUnifiedClip(windowsExecutionClip, WindowsBattleCanvas.Anchor.TOP);
        try {
            drawExecutionBackground(windowsExecutionClip);
            drawCombatantField(enemyPanels, enemyHuds.size(), plannedPanel);
            drawCombatantField(playerPanels, playerHuds.size(), plannedPanel);
            drawCombatantHuds(enemyPanels, enemyHuds, false);
            if (!playerHuds.isEmpty()) {
                drawCombatantHuds(playerPanels, playerHuds, true);
                if (playerPanel != null && faintAnimationFor(playerPanel) == null) {
                    miraclesMeter.draw(batch, assets.battleUi, assets.fontLarge);
                    ratioMeter.draw(batch, assets.battleUi, assets.fontLarge);
                    abilityStateMeter.draw(batch, assets.battleUi, assets.fontSmall);
                }
            }
            if (SHOW_TICK_COUNTER) {
                drawTickCounter(WindowsBattleCanvas.WIDTH, WindowsBattleCanvas.HEIGHT);
            }
            drawDomainBanners(WindowsBattleCanvas.WIDTH, WindowsBattleCanvas.HEIGHT);
            if (!planningUiEditable()) {
                drawMoveUnleashAnimation(
                    WINDOWS_EXECUTION_WIDTH, WINDOWS_EXECUTION_HEIGHT,
                    WINDOWS_EXECUTION_X, WINDOWS_EXECUTION_Y);
                drawHitFlashes(
                    WINDOWS_EXECUTION_WIDTH, WINDOWS_EXECUTION_HEIGHT,
                    WINDOWS_EXECUTION_X, WINDOWS_EXECUTION_Y);
            }
        } finally {
            endUnifiedClip();
        }
        drawCombatantStatusTooltips(enemyPanels);
        drawCombatantStatusTooltips(playerPanels);
        batch.end();

        applyUnifiedBatchTransform(WindowsBattleCanvas.Anchor.TOP);
        batch.begin();
        drawLog(
            WindowsBattleCanvas.WIDTH,
            WindowsBattleCanvas.HEIGHT,
            WindowsBattleCanvas.Anchor.TOP);
        batch.end();

        applyUnifiedBatchTransform(WindowsBattleCanvas.Anchor.BOTTOM);
        updateWindowsActionBounds();
        if (planningPanel != null) {
            planningPanel.draw(batch, assets.fontSmall, assets.fontMedium, assets.fontLarge);
        } else if (teamPlanningPanel != null) {
            teamPlanningPanel.draw(batch, assets.fontSmall, assets.fontMedium, assets.fontLarge);
        } else {
            batch.begin();
            drawWindowsPlanningSectionBackground();
            batch.end();
        }
        batch.begin();
        if (speedControlsVisible()) drawSpeedControls();
        drawBattleActionButton();
        batch.end();

        applyPhysicalBatchTransform();
        if (battleOver) drawBattleOver();
    }

    /** Draw one side's shared plate and fighters from left to right. */
    private void drawCombatantField(List<CombatantPanel> panels, int visibleCount) {
        drawCombatantField(panels, visibleCount, null);
    }

    private void drawCombatantField(
        List<CombatantPanel> panels,
        int visibleCount,
        CombatantPanel plannedPanel
    ) {
        int count = Math.min(panels.size(), visibleCount);
        if (count == 0) return;
        // SpriteBatch composites later draws on top, so the rightmost fighter stays in front.
        List<CombatantPanel> drawOrder = new ArrayList<>(panels.subList(0, count));
        drawOrder.sort((left, right) -> Float.compare(left.spriteCenterX(), right.spriteCenterX()));
        panels.get(0).drawPlate(batch);
        for (CombatantPanel panel : drawOrder) {
            if (panel == plannedPanel) {
                panel.drawPlanningHighlight(batch, assets.whiteSilhouette(panel.spriteTexture()));
            }
            FaintAnimation faint = faintAnimationFor(panel);
            EntranceAnimation entrance = entranceAnimationFor(panel);
            if (faint != null) {
                panel.drawFaintingSprite(batch, faintSlideRatio(faint.progress()));
            } else if (entrance != null) {
                panel.drawEnteringSpriteGrow(
                    batch, entrance.progress(), entrance.whiteSprite);
            } else {
                panel.drawSprite(batch, frameDelta);
            }
        }
    }

    /** Draw one side's HUD grid after both teams' battlefield sprites. */
    private void drawCombatantHuds(
        List<CombatantPanel> panels,
        List<CombatantHud> huds,
        boolean playerSide
    ) {
        int count = Math.min(panels.size(), huds.size());
        float pointerX = bottomInputX(Gdx.input.getX());
        float pointerY = windowsUnified()
            ? topInputY(Gdx.input.getY()) : bottomInputY(Gdx.input.getY());
        for (int i = 0; i < count; i++) {
            CombatantPanel panel = panels.get(i);
            if (faintAnimationFor(panel) == null) {
                CombatantHud hud = huds.get(i);
                EntranceAnimation entrance = entranceAnimationFor(panel);
                if (entrance != null && entrance.animateHud) {
                    panel.drawEnteringHud(batch, assets.fontMedium, assets.fontSmall,
                        hud.name(), frameDelta, entrance.progress(), playerSide,
                        windowsUnified() ? WindowsBattleCanvas.WIDTH : Gdx.graphics.getWidth());
                } else {
                    panel.drawHud(batch, assets.fontMedium, assets.fontSmall,
                        hud.name(), frameDelta, pointerX, pointerY);
                }
            }
        }
    }

    private void drawCombatantStatusTooltips(List<CombatantPanel> panels) {
        float viewportWidth = windowsUnified()
            ? WindowsBattleCanvas.WIDTH : Gdx.graphics.getWidth();
        float viewportHeight = windowsUnified()
            ? WindowsBattleCanvas.HEIGHT : Gdx.graphics.getHeight();
        for (CombatantPanel panel : panels) {
            panel.drawStatusTooltip(
                batch, assets.fontSmall, viewportWidth, viewportHeight);
        }
    }

    static boolean primarySpriteDrawsFirst(float primaryCenterX, float secondaryCenterX) {
        return primaryCenterX <= secondaryCenterX;
    }

    /** Draw the selected backdrop without distorting it at different viewport sizes. */
    private void drawExecutionBackground(float screenWidth, float screenHeight) {
        drawExecutionBackground(new Rectangle(0f, 0f, screenWidth, screenHeight));
    }

    /** Cover one execution rectangle while preserving the backdrop's aspect ratio. */
    private void drawExecutionBackground(Rectangle bounds) {
        Texture background = assets.battleExecutionBackground;
        if (background == null) return;

        float scale = Math.max(
            bounds.width / background.getWidth(),
            bounds.height / background.getHeight()
        );
        float width = background.getWidth() * scale;
        float height = background.getHeight() * scale;
        batch.setColor(Color.WHITE);
        batch.draw(background,
            bounds.x + (bounds.width - width) / 2f,
            bounds.y + (bounds.height - height) / 2f,
            width,
            height);
    }

    private void drawWindowsPlanningSectionBackground() {
        assets.battleUi.palette.draw(
            batch, 0f, 0f,
            WindowsBattleCanvas.WIDTH, WindowsBattleCanvas.BOTTOM_SECTION_HEIGHT);
        batch.setColor(WINDOWS_SECTION_DIVIDER);
        batch.draw(
            assets.battleUi.pixel,
            0f,
            WindowsBattleCanvas.BOTTOM_SECTION_HEIGHT - 2f,
            WindowsBattleCanvas.WIDTH,
            2f);
        batch.setColor(Color.WHITE);
    }

    /** Temporary execution readout for checking timeline playback. */
    // -------------------------------------------------------------------------
    // Active Domain banners
    // -------------------------------------------------------------------------

    /** One immutable status line for an active Domain or anti-Domain. */
    private static final class DomainBanner {
        final String instanceId;
        final String name;
        final String ownerName;
        final boolean antiDomain;
        final String duration;
        final boolean clashing;
        /** Takeover percent toward breaking the opposing Domain; -1 when unknown. */
        final int clashTakeoverPercent;

        DomainBanner(
            String instanceId, String name, String ownerName,
            boolean antiDomain, String duration, boolean clashing
        ) {
            this(instanceId, name, ownerName, antiDomain, duration, clashing, -1);
        }

        DomainBanner(
            String instanceId, String name, String ownerName, boolean antiDomain,
            String duration, boolean clashing, int clashTakeoverPercent
        ) {
            this.instanceId = instanceId;
            this.name = name;
            this.ownerName = ownerName == null ? "" : ownerName;
            this.antiDomain = antiDomain;
            this.duration = duration == null ? "" : duration;
            this.clashing = clashing;
            this.clashTakeoverPercent = clashTakeoverPercent;
        }

        DomainBanner withClash(boolean clashing) {
            return clashing == this.clashing && !clashing ? this
                : new DomainBanner(instanceId, name, ownerName, antiDomain,
                    duration, clashing, -1);
        }

        DomainBanner withClashTakeover(int percent) {
            return new DomainBanner(instanceId, name, ownerName, antiDomain,
                duration, true, percent);
        }

        String label() {
            StringBuilder text = new StringBuilder(antiDomain ? "[ANTI-DOMAIN] " : "[DOMAIN] ");
            text.append(name);
            if (!ownerName.isBlank()) text.append(" - ").append(ownerName);
            if (!duration.isEmpty()) text.append(" (").append(duration).append(')');
            if (clashing) {
                text.append(clashTakeoverPercent >= 0
                    ? "  <<CLASH " + clashTakeoverPercent + "%>>" : "  <<CLASH>>");
            }
            return text.toString();
        }
    }

    /** Track a local playback event so banners pace with the battle log. */
    private void applyLocalDomainEvent(CombatEvent event) {
        switch (event.getType()) {
            case DOMAIN_ESTABLISHED, DOMAIN_COUNTER_ESTABLISHED -> addDomainBanner(
                event.getDomainInstanceId(), event.getDomainName(),
                event.getSource() == null || event.getSource().getCharacter() == null
                    ? "" : event.getSource().getCharacter().getName(),
                event.getType() == CombatEvent.Type.DOMAIN_COUNTER_ESTABLISHED);
            case DOMAIN_COLLAPSED -> removeDomainBanner(event.getDomainInstanceId());
            case DOMAIN_CLASH_STARTED -> {
                setDomainClash(event.getDomainInstanceId(), true);
                setDomainClash(event.getRelatedDomainInstanceId(), true);
            }
            case DOMAIN_CLASH_ENDED -> {
                setDomainClash(event.getDomainInstanceId(), false);
                setDomainClash(event.getRelatedDomainInstanceId(), false);
            }
            default -> { }
        }
    }

    /** Track an online playback event so banners pace with the battle log. */
    private void applyOnlineDomainEvent(BattleEventState event) {
        switch (event.type()) {
            case DOMAIN_ESTABLISHED, DOMAIN_COUNTER_ESTABLISHED -> addDomainBanner(
                event.domainInstanceId(), event.domainName(),
                event.sourceCharacterName() == null ? "" : event.sourceCharacterName(),
                event.type() == BattleEventType.DOMAIN_COUNTER_ESTABLISHED);
            case DOMAIN_COLLAPSED -> removeDomainBanner(event.domainInstanceId());
            case DOMAIN_CLASH_STARTED -> {
                setDomainClash(event.domainInstanceId(), true);
                setDomainClash(event.relatedDomainInstanceId(), true);
            }
            case DOMAIN_CLASH_ENDED -> {
                setDomainClash(event.domainInstanceId(), false);
                setDomainClash(event.relatedDomainInstanceId(), false);
            }
            default -> { }
        }
    }

    /** Rebuild banners from the authoritative local battlefield. */
    private void syncLocalDomainBanners(BattleState state) {
        if (state == null) return;
        List<DomainBanner> banners = new ArrayList<>();
        for (DomainInstance instance : state.domainBattlefield().activeDomains()) {
            BattleCombatant owner = state.combatant(instance.ownerId());
            banners.add(new DomainBanner(
                instance.instanceId(), instance.definition().name(),
                owner == null || owner.getCharacter() == null
                    ? "" : owner.getCharacter().getName(),
                instance.definition().antiDomain(),
                domainDurationText(
                    instance.remainingRounds(), instance.remainingTicks()),
                false));
        }
        domainBanners = List.copyOf(banners);
        for (DomainClash clash : state.domainBattlefield().clashes()) {
            setDomainClash(clash.firstInstanceId(), true);
            setDomainClash(clash.secondInstanceId(), true);
            if (clash.leaderInstanceId() != null) {
                setDomainClashTakeover(clash.leaderInstanceId(), clash.takeoverProgress());
            }
        }
    }

    /** Rebuild banners from a server snapshot (also the reconnect path). */
    private void syncOnlineDomainBanners(MatchState state) {
        if (state == null) return;
        DomainBattlefieldState battlefield = state.domainBattlefield();
        List<DomainBanner> banners = new ArrayList<>();
        for (DomainState domain : battlefield.activeDomains()) {
            banners.add(new DomainBanner(
                domain.instanceId(), domain.name(),
                onlineDomainOwnerName(state, domain.ownerInstanceId()),
                domain.antiDomain(),
                domainDurationText(domain.remainingRounds(), domain.remainingTicks()),
                false));
        }
        domainBanners = List.copyOf(banners);
        for (DomainClashState clash : battlefield.clashes()) {
            setDomainClash(clash.firstDomainInstanceId(), true);
            setDomainClash(clash.secondDomainInstanceId(), true);
            if (clash.leaderDomainInstanceId() != null) {
                setDomainClashTakeover(clash.leaderDomainInstanceId(),
                    clash.takeoverProgress());
            }
        }
    }

    private static String onlineDomainOwnerName(MatchState state, String ownerInstanceId) {
        if (ownerInstanceId == null) return "";
        for (PlayerSide side : PlayerSide.values()) {
            var player = state.player(side);
            if (player.isEmpty()) continue;
            for (CharacterState combatant : player.get().combatants()) {
                if (ownerInstanceId.equals(combatant.instanceId())) {
                    return combatant.name() == null ? "" : combatant.name();
                }
            }
        }
        return "";
    }

    private static String domainDurationText(int rounds, int ticks) {
        if (rounds < 0) return "held";
        if (rounds == 0) return ticks > 0 ? ticks + "t" : "";
        return ticks <= 0 ? rounds + "r" : rounds + "r " + ticks + "t";
    }

    private void addDomainBanner(
        String instanceId, String name, String ownerName, boolean antiDomain
    ) {
        if (instanceId == null || name == null) return;
        List<DomainBanner> banners = new ArrayList<>(domainBanners);
        banners.removeIf(banner -> instanceId.equals(banner.instanceId));
        banners.add(new DomainBanner(instanceId, name, ownerName, antiDomain, "", false));
        domainBanners = List.copyOf(banners);
    }

    private void removeDomainBanner(String instanceId) {
        if (instanceId == null) return;
        List<DomainBanner> banners = new ArrayList<>(domainBanners);
        banners.removeIf(banner -> instanceId.equals(banner.instanceId));
        domainBanners = List.copyOf(banners);
    }

    private void setDomainClash(String instanceId, boolean clashing) {
        if (instanceId == null) return;
        boolean present = domainBanners.stream()
            .anyMatch(banner -> instanceId.equals(banner.instanceId)
                && banner.clashing != clashing);
        if (!present) return;
        List<DomainBanner> banners = new ArrayList<>(domainBanners);
        banners.replaceAll(banner -> instanceId.equals(banner.instanceId)
            ? banner.withClash(clashing) : banner);
        domainBanners = List.copyOf(banners);
    }

    /** Stamp the weaker Domain's integrity-loss percent onto the stronger banner. */
    private void setDomainClashTakeover(String instanceId, double progress) {
        if (instanceId == null) return;
        int percent = (int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * 100.0);
        boolean present = domainBanners.stream()
            .anyMatch(banner -> instanceId.equals(banner.instanceId)
                && (!banner.clashing || banner.clashTakeoverPercent != percent));
        if (!present) return;
        List<DomainBanner> banners = new ArrayList<>(domainBanners);
        banners.replaceAll(banner -> instanceId.equals(banner.instanceId)
            ? banner.withClashTakeover(percent) : banner);
        domainBanners = List.copyOf(banners);
    }

    /** Status strip under the top edge: one centered line per active Domain. */
    private void drawDomainBanners(float screenWidth, float screenHeight) {
        List<DomainBanner> banners = domainBanners;
        if (banners.isEmpty()) return;
        float y = screenHeight - 14f;
        for (int index = 0; index < banners.size(); index++) {
            DomainBanner banner = banners.get(index);
            String label = banner.label();
            GlyphLayout layout = new GlyphLayout(assets.fontSmall, label);
            float x = (screenWidth - layout.width) / 2f;
            Color text = banner.clashing
                ? new Color(1f, 0.45f, 0.30f, 1f)
                : banner.antiDomain
                    ? new Color(0.50f, 0.85f, 1f, 1f)
                    : new Color(0.72f, 0.55f, 1f, 1f);
            assets.fontSmall.setColor(Color.BLACK);
            assets.fontSmall.draw(batch, label, x + 1f, y - 1f);
            assets.fontSmall.setColor(text);
            assets.fontSmall.draw(batch, label, x, y);
            y -= 18f;
        }
        // Restore a neutral color so later draws never inherit the banner tint.
        assets.fontSmall.setColor(Color.WHITE);
    }

    private void drawTickCounter(float screenWidth, float screenHeight) {
        String label = "TICK: " + currentExecutionTick;
        GlyphLayout layout = new GlyphLayout(assets.fontSmall, label);
        float x = (screenWidth - layout.width) / 2f;
        float y = screenHeight - 16f;
        assets.fontSmall.setColor(Color.BLACK);
        assets.fontSmall.draw(batch, label, x + 1f, y - 1f);
        assets.fontSmall.setColor(Color.YELLOW);
        assets.fontSmall.draw(batch, label, x, y);
    }

    private void drawMultiplayerPlanningCountdown() {
        if (mode != BattleMode.MULTIPLAYER
            || multiplayerState == null
            || multiplayerState.phase() != BattlePhase.PLANNING
            || multiplayerState.planningDeadline() == null
            || isTerminal(multiplayerState.status())) {
            return;
        }

        long remainingMillis = multiplayerPlanningRemainingMillis();
        if (remainingMillis < 0L) {
            return;
        }

        float screenWidth;
        float screenHeight;
        BitmapFont font;
        if (windowsUnified()) {
            applyUnifiedBatchTransform(WindowsBattleCanvas.Anchor.TOP);
            screenWidth = WindowsBattleCanvas.WIDTH;
            screenHeight = WindowsBattleCanvas.HEIGHT;
            font = assets.fontSmall;
        } else {
            applyPhysicalBatchTransform();
            screenWidth = Gdx.graphics.getWidth();
            screenHeight = Gdx.graphics.getHeight();
            font = assets.fontMedium;
        }

        String label = formatPlanningCountdown(remainingMillis);
        GlyphLayout glyph = new GlyphLayout(font, label);
        float x = (screenWidth - glyph.width) / 2f;
        float baseline = screenHeight - (windowsUnified() ? 9f : 7f);
        float paddingX = windowsUnified() ? 10f : 8f;
        float paddingY = 5f;

        batch.begin();
        batch.setColor(0f, 0f, 0f, 0.62f);
        batch.draw(
            assets.battleUi.pixel,
            x - paddingX,
            baseline - glyph.height - paddingY,
            glyph.width + paddingX * 2f,
            glyph.height + paddingY * 2f
        );
        batch.setColor(Color.WHITE);
        font.setColor(Color.BLACK);
        font.draw(batch, label, x + 1f, baseline - 1f);
        font.setColor(BattleUiAssets.YELLOW);
        font.draw(batch, label, x, baseline);
        font.setColor(Color.WHITE);
        batch.end();

        if (windowsUnified()) {
            applyPhysicalBatchTransform();
        }
    }

    static String formatPlanningCountdown(long remainingMillis) {
        long totalSeconds = remainingMillis <= 0L
            ? 0L : (remainingMillis + 999L) / 1000L;
        long seconds = totalSeconds % 60L;
        return totalSeconds / 60L + ":" + (seconds < 10L ? "0" : "") + seconds;
    }

    /**
     * Render the battle log: newest entry at the bottom, older entries stacked
     * above it. While awaiting the next round (dialogue idle), the player can
     * scroll up through history with the mouse wheel; at all other times the
     * log is pinned to the newest line. Long lines wrap to a new row, and the
     * font stays fixed.
     */
    private void drawLog(float sw, float sh) {
        drawLog(sw, sh, WindowsBattleCanvas.Anchor.BOTTOM);
    }

    private void drawLog(
        float sw,
        float sh,
        WindowsBattleCanvas.Anchor canvasAnchor
    ) {
        float textGeometryScale = executionTextGeometryScale();
        assets.battleUi.dialogue.draw(batch, logBounds.x, logBounds.y, logBounds.width, logBounds.height);
        assets.fontSmall.setColor(new Color(0.980f, 0.870f, 0.540f, 1f));
        assets.fontSmall.draw(batch, "BATTLE LOG",
            logBounds.x + 14f * textGeometryScale,
            logBounds.y + logBounds.height - 14f * textGeometryScale);

        BitmapFont logFont = assets.fontLog;
        float speedControlSpace = !windowsUnified() && speedControlsVisible()
            ? logBounds.x + logBounds.width - fastForwardBounds.x
                + 14f * textGeometryScale : 0f;
        float buttonSpace = Math.max(speedControlSpace,
            !windowsUnified() && (awaitingBattleStart || awaitingNextRound)
                ? nextRoundBounds.width + 24f * textGeometryScale : 0f);
        float textWidth = Math.max(
            1f, logBounds.width - 28f * textGeometryScale - buttonSpace);
        // Wrap the retained messages to the panel width (fixed font; no scaling).
        List<String> lines = wrapAll(logFont, textWidth);
        // Append the in-progress typing line (newest) — wrapped from the
        // revealed substring so multi-line messages reveal row by row.
        if (typingLine != null) {
            int shown = Math.min(typingChars, typingLine.length());
            lines.addAll(wrapText(logFont, typingLine.substring(0, shown), textWidth));
        }

        float lineStep = logFont.getCapHeight() * uiLayout.execution.logLineSpacing;
        // Drawable band inside the panel (below the title, above the baseplate).
        float bottomY = logBounds.y + 25f * textGeometryScale;
        float topY = logBounds.y + logBounds.height
            - 34f * textGeometryScale + lineStep;
        float visibleHeight = topY - bottomY;
        float contentHeight = lines.size() * lineStep;
        // Keep the offset within the now-current content range: it can grow
        // stale if lines were trimmed or the panel resized since the last wheel.
        logScrollOffset = clampLogScroll(logScrollOffset, contentHeight, visibleHeight);

        // Scissor to the panel interior so scrolled history never paints over
        // the title (drawn above) or spills past the panel edge. The batch uses
        // an ortho2D projection where world units map 1:1 to screen pixels
        // (see resize()), so the clip rectangle in world coords maps to pixels.
        // The title is drawn before this push, and the NEXT ROUND button is
        // drawn after this method returns, so neither is affected.
        float clipInset = 6f * textGeometryScale;
        Rectangle clip = new Rectangle(logBounds.x + clipInset, logBounds.y + clipInset,
            logBounds.width - clipInset * 2f, logBounds.height - clipInset * 2f);
        float scaleX = Gdx.graphics.getBackBufferWidth() / (float) Gdx.graphics.getWidth();
        float scaleY = Gdx.graphics.getBackBufferHeight() / (float) Gdx.graphics.getHeight();
        Rectangle physicalClip = windowsUnified()
            ? windowsCanvas.physicalBounds(clip, canvasAnchor)
            : clip;
        boolean pushed = clip.width > 0f && clip.height > 0f;
        if (pushed) {
            batch.flush();
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glScissor(
                Math.round(physicalClip.x * scaleX), Math.round(physicalClip.y * scaleY),
                Math.round(physicalClip.width * scaleX), Math.round(physicalClip.height * scaleY));
        }

        // Bottom-anchor: walk newest → oldest, shifted down by the scroll
        // offset so older lines enter from the top. Lines scrolled below the
        // panel are drawn but clipped away; stop once a line's baseline clears
        // the top of the drawable band (older lines are all higher still).
        logFont.setColor(Color.WHITE);
        float y = bottomY - logScrollOffset;
        try {
            for (int i = lines.size() - 1; i >= 0; i--) {
                if (y > topY) break;
                logFont.draw(batch, lines.get(i),
                    logBounds.x + 14f * textGeometryScale, y);
                y += lineStep;
            }
            if (pushed) batch.flush();
        } finally {
            if (pushed) Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        }
    }

    /**
     * Clamp a candidate scroll offset to the valid range for the current log
     * content. {@code maxScroll} is how far history exceeds the visible band; a
     * log that fits entirely has no scroll room (offset pinned to zero).
     */
    private static float clampLogScroll(float offset, float contentHeight, float visibleHeight) {
        float maxScroll = Math.max(0f, contentHeight - visibleHeight);
        if (maxScroll <= 0f) return 0f;
        return Math.max(0f, Math.min(offset, maxScroll));
    }

    /**
     * Apply a wheel delta (in rows; positive = scroll up toward older history)
     * to the log scroll offset, clamped to the current content. Only meaningful
     * while awaiting the next round — the renderer ignores the offset otherwise.
     */
    private void adjustLogScroll(float rows) {
        if (rows == 0f) return;
        BitmapFont logFont = assets.fontLog;
        float textGeometryScale = executionTextGeometryScale();
        float lineStep = logFont.getCapHeight() * uiLayout.execution.logLineSpacing;
        float bottomY = logBounds.y + 25f * textGeometryScale;
        float topY = logBounds.y + logBounds.height
            - 34f * textGeometryScale + lineStep;
        float visibleHeight = topY - bottomY;
        float textWidth = logTextWidth(textGeometryScale);
        int lineCount = wrapAll(logFont, textWidth).size();
        if (typingLine != null) {
            int shown = Math.min(typingChars, typingLine.length());
            lineCount += wrapText(logFont, typingLine.substring(0, shown), textWidth).size();
        }
        float contentHeight = lineCount * lineStep;
        logScrollOffset = clampLogScroll(logScrollOffset + rows * lineStep, contentHeight, visibleHeight);
    }

    private float logTextWidth(float textGeometryScale) {
        if (windowsUnified()) {
            return Math.max(1f, logBounds.width - 28f * textGeometryScale);
        }
        float speedControlSpace = speedControlsVisible()
            ? logBounds.x + logBounds.width - fastForwardBounds.x
                + 14f * textGeometryScale : 0f;
        float buttonSpace = Math.max(speedControlSpace,
            awaitingBattleStart || awaitingNextRound
                ? nextRoundBounds.width + 24f * textGeometryScale : 0f);
        return Math.max(1f, logBounds.width - 28f * textGeometryScale - buttonSpace);
    }

    /** Wrap every retained message to {@code width} and return the flat list of lines (oldest first). */
    private List<String> wrapAll(BitmapFont font, float width) {
        List<String> all = new ArrayList<>();
        for (String message : logLines) {
            all.addAll(wrapText(font, message, width));
        }
        return all;
    }

    private void drawBattleActionButton() {
        if (!battleActionButtonVisible()) return;
        boolean enabled = awaitingBattleStart
            ? battleStartButtonEnabled()
            : nextRoundButtonEnabled();
        if (!enabled) {
            assets.battleUi.lockButtonDisabled.draw(batch, nextRoundBounds.x, nextRoundBounds.y,
                nextRoundBounds.width, nextRoundBounds.height);
        } else if (nextRoundHovered) {
            assets.battleUi.lockButtonOver.draw(batch, nextRoundBounds.x, nextRoundBounds.y,
                nextRoundBounds.width, nextRoundBounds.height);
        } else {
            assets.battleUi.lockButton.draw(batch, nextRoundBounds.x, nextRoundBounds.y,
                nextRoundBounds.width, nextRoundBounds.height);
        }
        BitmapFont actionFont = windowsUnified() ? assets.fontSmall : assets.fontMedium;
        actionFont.setColor(Color.WHITE);
        String label;
        if (awaitingBattleStart) {
            label = battleStartWaiting() ? "WAITING..." : "START BATTLE";
        } else {
            label = nextRoundWaiting()
                ? "WAITING..." : "NEXT ROUND";
        }
        GlyphLayout layout = new GlyphLayout(actionFont, label);
        actionFont.draw(batch, label,
            nextRoundBounds.x + (nextRoundBounds.width - layout.width) / 2f,
            nextRoundBounds.y + (nextRoundBounds.height + layout.height) / 2f);
    }

    private boolean battleActionButtonVisible() {
        return awaitingBattleStart || awaitingNextRound;
    }

    private boolean nextRoundButtonEnabled() {
        if (!roundPresentationComplete()) return false;
        if (mode == BattleMode.LOCAL) return !nextRoundConfirmed;
        return multiplayerState != null
            && multiplayerState.status() == MatchStatus.ACTIVE
            && multiplayerState.phase() == BattlePhase.ROUND_END
            && multiplayerConnectionState == MultiplayerSession.ConnectionState.CONNECTED
            && !onlineCommandPending
            && !localReadyForNextRound();
    }

    private boolean roundPresentationComplete() {
        if (!awaitingNextRound || battleOver || resolvingTicks
            || typingInProgress() || faintAnimationInProgress()
            || entranceAnimationInProgress()) {
            return false;
        }
        return mode == BattleMode.LOCAL || playbackComplete;
    }

    private boolean nextRoundWaiting() {
        return mode == BattleMode.LOCAL
            ? nextRoundConfirmed
            : onlineCommandPending || localReadyForNextRound();
    }

    private void drawSpeedControls() {
        Texture fastForward = fastForwardActive
            ? assets.battleUi.fastForwardActive : assets.battleUi.fastForwardInactive;
        Texture skip = skipActiveFlashRemaining > 0f
            ? assets.battleUi.skipActive : assets.battleUi.skipInactive;
        drawSpeedControl(fastForward, fastForwardBounds, fastForwardHovered);
        drawSpeedControl(skip, skipBounds, skipHovered);
    }

    private void drawSpeedControl(Texture texture, Rectangle bounds, boolean hovered) {
        batch.setColor(Color.WHITE);
        batch.draw(texture, bounds.x, bounds.y, bounds.width, bounds.height);
        if (!hovered) return;

        float edge = Math.max(2f, Math.min(3f, bounds.width * 0.06f));
        batch.setColor(BattleUiAssets.YELLOW);
        batch.draw(assets.battleUi.pixel, bounds.x, bounds.y, bounds.width, edge);
        batch.draw(assets.battleUi.pixel, bounds.x, bounds.y + bounds.height - edge,
            bounds.width, edge);
        batch.draw(assets.battleUi.pixel, bounds.x, bounds.y, edge, bounds.height);
        batch.draw(assets.battleUi.pixel, bounds.x + bounds.width - edge, bounds.y,
            edge, bounds.height);
        batch.setColor(Color.WHITE);
    }

    private float playbackSpeedMultiplier() {
        return fastForwardActive ? FAST_FORWARD_MULTIPLIER : 1f;
    }

    private boolean playbackControlsEnabled() {
        return speedControlsVisible() && playbackControlsOpen && !awaitingNextRound
            && !battleOver && !skipRoundRequested;
    }

    private boolean speedControlsVisible() {
        return executionUiActive && !planningUiEditable()
            && !awaitingBattleStart && !battleOver;
    }

    private void toggleFastForward() {
        synchronized (this) {
            if (!playbackControlsEnabled()) return;
            fastForwardActive = !fastForwardActive;
        }
        game.audio().play(SoundCue.UI_CONFIRM);
    }

    private void requestRoundSkip() {
        synchronized (this) {
            if (!playbackControlsEnabled()) return;
            skipRoundRequested = true;
            fastForwardActive = false;
        }
        skipActiveFlashRemaining = SKIP_ACTIVE_FLASH_SECONDS;
        game.audio().play(SoundCue.UI_CONFIRM);
        clearTransientAnimations();
        flushTypingImmediately();
        completeFaintAnimationsImmediately();
        completeEntranceAnimationsImmediately();
        snapPanelAnimations();
    }

    private void resetPlaybackControls() {
        synchronized (this) {
            playbackControlsOpen = false;
            fastForwardActive = false;
            skipRoundRequested = false;
        }
        fastForwardHovered = false;
        skipHovered = false;
    }

    private void clearTransientAnimations() {
        unleashedMoveIcon = null;
        unleashedMoveTargetPanel = null;
        hitFlashes.clear();
    }

    private void completeFaintAnimationsImmediately() {
        if (faintAnimations.isEmpty()) return;
        List<Runnable> completions = faintAnimations.stream()
            .map(faint -> faint.onComplete)
            .toList();
        faintAnimations.clear();
        for (Runnable completion : completions) completion.run();
    }

    private void snapPanelAnimations() {
        playerPanels.forEach(CombatantPanel::snapAnimations);
        enemyPanels.forEach(CombatantPanel::snapAnimations);
    }

    private void updateMoveUnleashAnimation(float delta) {
        if (unleashedMoveIcon == null) return;
        unleashedMoveElapsed += Math.max(0f, delta);
        if (unleashedMoveElapsed >= unleashedMoveDurationSeconds) {
            unleashedMoveIcon = null;
        }
    }

    private void drawMoveUnleashAnimation(float screenWidth, float screenHeight) {
        drawMoveUnleashAnimation(screenWidth, screenHeight, 0f, 0f);
    }

    private void drawMoveUnleashAnimation(
        float screenWidth,
        float screenHeight,
        float originX,
        float originY
    ) {
        if (unleashedMoveIcon == null) return;

        float progress = Math.min(1f, unleashedMoveElapsed / unleashedMoveDurationSeconds);
        float easedGrowth = 1f - (1f - progress) * (1f - progress);
        float viewportSize = Math.min(screenWidth, screenHeight);
        float startSize = Math.max(40f, Math.min(64f, viewportSize * 0.09f));
        float endSize = Math.max(startSize, Math.min(240f, viewportSize * 0.34f));
        float size = startSize + (endSize - startSize) * easedGrowth;
        float width = size * unleashedMoveIcon.getWidth() / (float) unleashedMoveIcon.getHeight();
        float centerX = originX + screenWidth / 2f;
        float centerY = originY + screenHeight / 2f;
        if (unleashedMoveTargetPanel != null) {
            centerX = unleashedMoveTargetPanel.spriteCenterX();
            centerY = unleashedMoveTargetPanel.spriteCenterY();
        }

        batch.setColor(1f, 1f, 1f, 1f - progress);
        batch.draw(unleashedMoveIcon,
            centerX - width / 2f,
            centerY - size / 2f,
            width,
            size);
        batch.setColor(Color.WHITE);
    }

    private void playMoveUnleashAnimation(Move move) {
        if (move == null) return;
        unleashedMoveIcon = assets.battleUi.moveEffectIcon(move);
        unleashedMoveElapsed = 0f;
        unleashedMoveDurationSeconds = MOVE_EFFECT_DURATION_SECONDS;
        unleashedMoveTargetPanel = null;
    }

    /** Plays the successful Ratio-stack proc with the same center-screen treatment as a move effect. */
    private void playRatioUnleashAnimation() {
        unleashedMoveIcon = assets.battleUi.ratioStack;
        unleashedMoveElapsed = 0f;
        unleashedMoveDurationSeconds = MOVE_EFFECT_DURATION_SECONDS;
        unleashedMoveTargetPanel = null;
    }

    private void playSuccessfulBlockAnimation(CombatantPanel targetPanel) {
        if (targetPanel == null) return;
        unleashedMoveIcon = assets.battleUi.defenseEffectIcon;
        unleashedMoveElapsed = 0f;
        unleashedMoveDurationSeconds = BLOCK_EFFECT_DURATION_SECONDS;
        unleashedMoveTargetPanel = targetPanel;
    }

    private void playSuccessfulBlockAnimation(CombatEvent event) {
        CombatantPanel panel = panelForCombatant(event.getTarget());
        if (panel == null) return;
        unleashedMoveIcon = assets.battleUi.defenseEffectIcon;
        unleashedMoveElapsed = 0f;
        unleashedMoveDurationSeconds = BLOCK_EFFECT_DURATION_SECONDS;
        unleashedMoveTargetPanel = panel;
    }

    /**
     * Spawn a per-hit impact flash on the defender's sprite. On a multi-hit
     * move each connecting hit gets its own short burst (attack icon for damage,
     * defense icon for a blocked/parried/dodged hit) instead of sharing the
     * single center-screen unleash slot.
     */
    private void spawnHitFlash(CombatEvent event) {
        if (event == null || event.getMove() == null) return;
        spawnHitFlash(event.getMove(), event.getType(), panelForCombatant(event.getTarget()));
    }

    private void spawnHitFlash(
        Move move,
        CombatEvent.Type type,
        CombatantPanel targetPanel
    ) {
        // Only multi-hit moves need per-hit flashes; single-hit moves already
        // get the center-screen unleash + this would double up the visual.
        if (move.getHitComponents().size() <= 1 || targetPanel == null) return;

        Texture icon;
        if (type == CombatEvent.Type.MOVE_BLOCKED
            || type == CombatEvent.Type.MOVE_BLOCK_REDUCED
            || type == CombatEvent.Type.MOVE_DODGED
            || type == CombatEvent.Type.MOVE_PARRIED) {
            icon = assets.battleUi.defenseEffectIcon;
        } else {
            icon = assets.battleUi.attackEffectIcon;
        }
        hitFlashes.add(new HitFlash(icon, targetPanel, HIT_FLASH_DURATION_SECONDS));
        // Bound the list so a runaway chain cannot grow without limit.
        while (hitFlashes.size() > 8) hitFlashes.remove(0);
    }

    private void updateHitFlashes(float delta) {
        if (hitFlashes.isEmpty()) return;
        java.util.Iterator<HitFlash> it = hitFlashes.iterator();
        while (it.hasNext()) {
            HitFlash flash = it.next();
            flash.elapsed += Math.max(0f, delta);
            if (flash.elapsed >= flash.duration) it.remove();
        }
    }

    private void drawHitFlashes(float screenWidth, float screenHeight) {
        drawHitFlashes(screenWidth, screenHeight, 0f, 0f);
    }

    private void drawHitFlashes(
        float screenWidth,
        float screenHeight,
        float originX,
        float originY
    ) {
        for (HitFlash flash : hitFlashes) {
            float progress = Math.min(1f, flash.elapsed / flash.duration);
            float eased = 1f - (1f - progress) * (1f - progress);
            float viewportSize = Math.min(screenWidth, screenHeight);
            float startSize = Math.max(28f, Math.min(44f, viewportSize * 0.06f));
            float endSize = Math.max(startSize, Math.min(150f, viewportSize * 0.22f));
            float size = startSize + (endSize - startSize) * eased;
            float width = size * flash.icon.getWidth() / (float) flash.icon.getHeight();

            float centerX = originX + screenWidth / 2f;
            float centerY = originY + screenHeight / 2f;
            if (flash.targetPanel != null) {
                centerX = flash.targetPanel.spriteCenterX();
                centerY = flash.targetPanel.spriteCenterY();
            }
            batch.setColor(1f, 1f, 1f, 1f - progress);
            batch.draw(flash.icon, centerX - width / 2f, centerY - size / 2f, width, size);
            batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        }
    }

    /** One transient per-hit impact flash on a combatant's sprite. */
    private static final class HitFlash {
        final Texture icon;
        private CombatantPanel targetPanel;
        final float duration;
        float elapsed;
        HitFlash(Texture icon, CombatantPanel targetPanel, float duration) {
            this.icon = icon;
            this.targetPanel = targetPanel;
            this.duration = duration;
            this.elapsed = 0f;
        }
    }

    private void updateFaintAnimations(float delta) {
        if (faintAnimations.isEmpty()) return;
        List<Runnable> completed = new ArrayList<>();
        java.util.Iterator<FaintAnimation> iterator = faintAnimations.iterator();
        while (iterator.hasNext()) {
            FaintAnimation faint = iterator.next();
            faint.elapsed += Math.max(0f, delta);
            if (faint.elapsed >= FAINT_SLIDE_DURATION_SECONDS) {
                iterator.remove();
                completed.add(faint.onComplete);
            }
        }
        // Completion rebuilds panel lists, so run it only after iteration ends.
        for (Runnable callback : completed) callback.run();
    }

    private boolean startFaintAnimation(FaintAnimation faint) {
        if (faint == null || faint.panel == null) return false;
        for (FaintAnimation active : faintAnimations) {
            if (active.sameCombatant(faint)) return false;
        }
        faint.panel.prepareFaint();
        // A defeat mid-entrance supersedes the arrival: the faint owns the panel.
        entranceAnimations.removeIf(entrance -> entrance.panel == faint.panel);
        faintAnimations.add(faint);
        return true;
    }

    private FaintAnimation faintAnimationFor(CombatantPanel panel) {
        if (panel == null) return null;
        for (FaintAnimation faint : faintAnimations) {
            if (faint.panel == panel) return faint;
        }
        return null;
    }

    private boolean faintAnimationInProgress() {
        return !faintAnimations.isEmpty();
    }

    /** Ease-out gives the short, decisive downward pull of classic faint animations. */
    static float faintSlideRatio(float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        return 1f - (1f - clamped) * (1f - clamped);
    }

    private static final class FaintAnimation {
        final BattleCombatant localCombatant;
        final OnlineCombatantKey onlineCombatant;
        final boolean playerSide;
        final Runnable onComplete;
        CombatantPanel panel;
        float elapsed;

        private FaintAnimation(
            BattleCombatant localCombatant,
            OnlineCombatantKey onlineCombatant,
            boolean playerSide,
            CombatantPanel panel,
            Runnable onComplete
        ) {
            this.localCombatant = localCombatant;
            this.onlineCombatant = onlineCombatant;
            this.playerSide = playerSide;
            this.panel = panel;
            this.onComplete = onComplete;
        }

        static FaintAnimation local(
            BattleCombatant combatant,
            boolean playerSide,
            CombatantPanel panel,
            Runnable onComplete
        ) {
            return new FaintAnimation(combatant, null, playerSide, panel, onComplete);
        }

        static FaintAnimation online(
            OnlineCombatantKey combatant,
            boolean playerSide,
            CombatantPanel panel,
            Runnable onComplete
        ) {
            return new FaintAnimation(null, combatant, playerSide, panel, onComplete);
        }

        float progress() {
            return Math.min(1f, elapsed / FAINT_SLIDE_DURATION_SECONDS);
        }

        boolean sameCombatant(FaintAnimation other) {
            return localCombatant != null
                ? localCombatant == other.localCombatant
                : Objects.equals(onlineCombatant, other.onlineCombatant);
        }
    }

    /**
     * An arrival animation in progress — a summon joining or a form changing.
     * The sprite grows in from a white flash (see
     * {@link CombatantPanel#drawEnteringSpriteGrow}). Unlike a faint there is
     * no completion callback — the panel simply keeps drawing afterwards.
     */
    private static final class EntranceAnimation {
        final BattleCombatant localCombatant;
        final OnlineCombatantKey onlineCombatant;
        final boolean playerSide;
        final Texture whiteSprite;
        final boolean animateHud;
        CombatantPanel panel;
        float elapsed;

        private EntranceAnimation(
            BattleCombatant localCombatant,
            OnlineCombatantKey onlineCombatant,
            boolean playerSide,
            CombatantPanel panel,
            Texture whiteSprite,
            boolean animateHud
        ) {
            this.localCombatant = localCombatant;
            this.onlineCombatant = onlineCombatant;
            this.playerSide = playerSide;
            this.panel = panel;
            this.whiteSprite = whiteSprite;
            this.animateHud = animateHud;
        }

        static EntranceAnimation local(
            BattleCombatant combatant,
            boolean playerSide,
            CombatantPanel panel,
            Texture whiteSprite
        ) {
            return new EntranceAnimation(
                combatant, null, playerSide, panel, whiteSprite, false);
        }

        static EntranceAnimation initialLocal(
            BattleCombatant combatant,
            boolean playerSide,
            CombatantPanel panel,
            Texture whiteSprite
        ) {
            return new EntranceAnimation(
                combatant, null, playerSide, panel, whiteSprite, true);
        }

        static EntranceAnimation online(
            OnlineCombatantKey combatant,
            boolean playerSide,
            CombatantPanel panel,
            Texture whiteSprite
        ) {
            return new EntranceAnimation(
                null, combatant, playerSide, panel, whiteSprite, false);
        }

        static EntranceAnimation initialOnline(
            OnlineCombatantKey combatant,
            boolean playerSide,
            CombatantPanel panel,
            Texture whiteSprite
        ) {
            return new EntranceAnimation(
                null, combatant, playerSide, panel, whiteSprite, true);
        }

        float durationSeconds() {
            return ENTRANCE_GROW_DURATION_SECONDS;
        }

        float progress() {
            return Math.min(1f, elapsed / durationSeconds());
        }

        boolean sameCombatant(EntranceAnimation other) {
            return localCombatant != null
                ? localCombatant == other.localCombatant
                : Objects.equals(onlineCombatant, other.onlineCombatant);
        }
    }

    private void updateEntranceAnimations(float delta) {
        if (entranceAnimations.isEmpty()) return;
        java.util.Iterator<EntranceAnimation> iterator = entranceAnimations.iterator();
        while (iterator.hasNext()) {
            EntranceAnimation entrance = iterator.next();
            entrance.elapsed += Math.max(0f, delta);
            if (entrance.elapsed >= entrance.durationSeconds()) {
                iterator.remove();
            }
        }
    }

    private boolean startEntranceAnimation(EntranceAnimation entrance) {
        if (entrance == null || entrance.panel == null) return false;
        for (EntranceAnimation active : entranceAnimations) {
            if (active.sameCombatant(entrance)) return false;
        }
        entranceAnimations.add(entrance);
        return true;
    }

    private EntranceAnimation entranceAnimationFor(CombatantPanel panel) {
        if (panel == null) return null;
        for (EntranceAnimation entrance : entranceAnimations) {
            if (entrance.panel == panel) return entrance;
        }
        return null;
    }

    private boolean entranceAnimationInProgress() {
        return !entranceAnimations.isEmpty();
    }

    private void completeEntranceAnimationsImmediately() {
        entranceAnimations.clear();
    }

    private void drawBattleOver() {
        float sw = Gdx.graphics.getWidth();
        float sh = Gdx.graphics.getHeight();
        float textGeometryScale = executionTextGeometryScale();
        batch.begin();
        float width = Math.min(420f * textGeometryScale, sw - 48f * textGeometryScale);
        float x = (sw - width) / 2f;
        float y = sh * 0.35f;
        assets.battleUi.header.draw(batch, x, y, width, 200f * textGeometryScale);
        assets.fontLarge.setColor(Color.WHITE);
        assets.fontLarge.draw(batch, "BATTLE OVER",
            x + 36f * textGeometryScale, y + 132f * textGeometryScale);
        assets.fontMedium.setColor(Color.YELLOW);
        assets.fontMedium.draw(batch, battleResult,
            x + 36f * textGeometryScale, y + 86f * textGeometryScale);
        assets.fontSmall.setColor(Color.LIGHT_GRAY);
        if (!battleResultReason.isBlank()) {
            assets.fontSmall.draw(batch, battleResultReason,
                x + 36f * textGeometryScale, y + 57f * textGeometryScale);
        }
        assets.fontSmall.draw(batch, "ESC: MAIN MENU",
            x + 36f * textGeometryScale, y + 28f * textGeometryScale);
        batch.end();

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.audio().play(SoundCue.UI_BACK);
            if (mode == BattleMode.MULTIPLAYER) {
                leaveMultiplayer();
            } else {
                game.showMainMenu();
            }
        }
    }

    /** Append a message to the log and trim the oldest entries beyond the storage cap. */
    private void addLogLine(String message) {
        logLines.add(message);
        while (logLines.size() > LOG_MAX_STORED) logLines.remove(0);
        // New dialogue pins the view to the newest line; scrolling is only
        // useful while reviewing settled history between rounds.
        logScrollOffset = 0f;
    }

    /**
     * Queue a message for progressive letter-by-letter reveal. The battle
     * thread pairs this with {@link #waitForLogLine()} so a tick does not
     * advance until its log finishes typing.
     */
    private void queueLogLine(String message) {
        if (message == null || message.isBlank()) return;
        pendingTypingQueue.add(message);
    }

    /** Commit all current and queued dialogue without typewriter delays. */
    private void flushTypingImmediately() {
        if (typingLine != null) {
            addLogLine(typingLine);
            committedLogSeq++;
        }
        typingLine = null;
        typingChars = 0;
        typingCharTimer = 0f;
        typingTailTimer = 0f;

        String queued;
        while ((queued = pendingTypingQueue.poll()) != null) {
            addLogLine(queued);
            committedLogSeq++;
        }
    }

    /**
     * Advance the typewriter reveal on the render thread: pop queued messages
     * one at a time, reveal a character at {@link #LOG_TYPE_RATE_CPS}, then
     * hold for {@link #LOG_TYPE_TAIL_SECONDS} before committing the finished
     * line to {@link #logLines} and bumping {@link #committedLogSeq}.
     */
    private void updateTyping(float delta) {
        if (delta <= 0f) return;
        if (typingLine == null) {
            typingLine = pendingTypingQueue.poll();
            if (typingLine == null) return;
            typingChars = 0;
            typingCharTimer = 0f;
            typingTailTimer = 0f;
        }
        int total = typingLine.length();
        if (typingChars < total) {
            typingCharTimer += delta;
            float perChar = 1f / LOG_TYPE_RATE_CPS;
            while (typingCharTimer >= perChar && typingChars < total) {
                typingCharTimer -= perChar;
                typingChars++;
            }
            if (typingChars < total) return;
        }
        // Fully revealed — hold the tail, then commit.
        typingTailTimer += delta;
        if (typingTailTimer >= LOG_TYPE_TAIL_SECONDS) {
            addLogLine(typingLine);
            committedLogSeq++;
            typingLine = null;
            typingChars = 0;
            typingCharTimer = 0f;
            typingTailTimer = 0f;
        }
    }

    /**
     * Whether any log line is still mid-reveal or queued. Used by the
     * multiplayer playback accumulator to stall tick advancement until the log
     * catches up.
     */
    private boolean typingInProgress() {
        return typingLine != null || !pendingTypingQueue.isEmpty();
    }

    /**
     * Block the battle thread until the line queued immediately before this
     * call finishes typing and commits. Each LOCAL call queues exactly one
     * line then waits for the next {@link #committedLogSeq} bump, so multiple
     * lines in a tick type out in order. Returns promptly on abort.
     */
    private void waitForLogLine() {
        // Wait until the typewriter is fully idle — no line mid-reveal and the
        // queue drained — rather than just the next commit. A "next commit"
        // gate would mis-fire if earlier lines (e.g. the BATTLE START banner,
        // which doesn't block) were still queued ahead of this one.
        while (typingInProgress() && !skipRoundRequested
            && !abortRequested && isCurrentLocalBattleThread()) {
            sleepMs(16);
        }
    }

    private static List<String> wrapText(BitmapFont font, String text, float width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.isEmpty() && new GlyphLayout(font, word).width > width) {
                for (int i = 0; i < word.length(); i++) {
                    String candidate = line + String.valueOf(word.charAt(i));
                    if (!line.isEmpty() && new GlyphLayout(font, candidate).width > width) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    line.append(word.charAt(i));
                }
                continue;
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (new GlyphLayout(font, candidate).width <= width) {
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

    // -------------------------------------------------------------------------
    // BattleView implementation (called from controller background thread)
    // -------------------------------------------------------------------------

    @Override
    public void awaitBattleStart(BattleState state) {
        if (state == null || abortRequested || !isCurrentLocalBattleThread()) return;
        battleStartConfirmed = false;
        postLocal(() -> {
            syncLocalBattlefield(state);
            syncLocalHpFromModel();
            syncLocalAbilityStatesFromModel();
            initPanels();
            updatePanels();
            showLocalPreBattlePlanner(state);
            resetPlaybackControls();
            executionUiActive = true;
            awaitingBattleStart = true;
            nextRoundHovered = false;
            for (BattleCombatant combatant : renderPlayerTeam) {
                startLocalPanelEntrance(combatant, true, true);
            }
            for (BattleCombatant combatant : renderEnemyTeam) {
                startLocalPanelEntrance(combatant, false, true);
            }
        });

        while (!battleStartConfirmed && !abortRequested && isCurrentLocalBattleThread()) {
            sleepMs(16);
        }
    }

    @Override
    public void displayRoundStart(BattleState state) {
        if (!isCurrentLocalBattleThread()) return;
        // Publish the battlefield with its planner below so no render boundary
        // can observe the next round's execution scene by itself.
        postLocal(() -> game.audio().play(SoundCue.BATTLE_ROUND_START));
        abortableSleepMs(200);
    }

    /** Rebind the four visual slots per side to the active round-start roster. */
    private void syncLocalBattlefield(BattleState state) {
        renderLocalState = state;
        syncLocalBattlefield(
            visibleCombatants(state.playerTeam()),
            visibleCombatants(state.enemyTeam()));
    }

    private void syncLocalBattlefield(
        List<BattleCombatant> players,
        List<BattleCombatant> enemies
    ) {
        renderPlayerTeam = List.copyOf(players);
        renderEnemyTeam = List.copyOf(enemies);
        renderPlayer = players.isEmpty() ? null : players.get(0);
        renderEnemy = enemies.isEmpty() ? null : enemies.get(0);

        playerTeamSprites = battleSprites(players, false);
        enemyTeamSprites = battleSprites(enemies, true);
        if (!playerTeamSprites.isEmpty()) playerSprite = playerTeamSprites.get(0);
        if (!enemyTeamSprites.isEmpty()) enemySprite = enemyTeamSprites.get(0);
        syncLocalHpFromModel();
    }

    static List<BattleCombatant> visibleCombatants(BattleTeam team) {
        if (team == null) return List.of();
        return team.active().stream()
            .limit(MAX_VISIBLE_COMBATANTS_PER_SIDE)
            .toList();
    }

    private List<Texture> battleSprites(List<BattleCombatant> combatants, boolean opponent) {
        Texture fallback = opponent ? assets.enemySprite : assets.playerSprite;
        if (combatants.isEmpty()) return List.of();
        return combatants.stream()
            .map(combatant -> assets.characterBattleSprite(
                game.multiplayerSpriteAsset(combatant.getCharacter().getId()),
                opponent,
                fallback))
            .toList();
    }

    /**
     * Build and run the two-board timeline planning UI. Posts panel construction
     * to the render thread, installs the panel's drag input processor, and blocks
     * the controller thread until the player clicks "Lock In".
     *
     * <p>The plan is built live by the panel; on confirm we return it directly.
     */
    @Override
    public BattlePlan promptBattlePlan(BattleCombatant combatant, BattleCombatant opponent) {
        // Battle-wide grid length: the same value the AI's plan uses, derived
        // from the stronger fighter's AP tier so both timelines match.
        int gridLength = com.jjktbf.model.combat.Timeline.gridLengthForStrongestAp(
            Math.max(combatant.getMaxApBar(), opponent.getMaxApBar()));
        if (abortRequested || !isCurrentLocalBattleThread()) {
            return BattlePlan.forCombatant(combatant, gridLength);
        }
        // This must happen on the controller thread before its wait loop. If it
        // only happens in the posted render callback, a prior round's confirmed
        // value can skip planning entirely.
        inputConfirmed = false;
        postLocal(() -> {
            syncLocalBattlefield(
                List.of(combatant),
                opponent == null ? List.of() : List.of(opponent));
            syncLocalHpFromModel();
            syncLocalAbilityStatesFromModel();
            initPanels();
            awaitingBattleStart = false;
            awaitingNextRound = false;
            nextRoundHovered = false;
            executionUiActive = true;
            clearTransientAnimations();
            teamPlanningPanel = null;
            planningPanel = new com.jjktbf.graphics.ui.battle.PlanningPanel(
                gridLength, combatant, List.of(opponent), assets.battleUi,
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            planningPanel.setLayout(uiLayout);
            configurePlanningViewport();
            planningPanel.setSoundPlayer(game.audio()::play);
            planningPanel.setOnConfirm(() -> {
                game.audio().play(SoundCue.UI_PLAN_LOCK);
                inputConfirmed = true;
            });
            Gdx.input.setInputProcessor(planningPanel.inputProcessor());
            logScrollInputAttached = false;
            updatePanels();
            inputConfirmed = false;
        });

        while (!inputConfirmed && !abortRequested && isCurrentLocalBattleThread()) {
            sleepMs(16);
        }

        // On abort, return an empty plan immediately — the controller will see
        // isAborted() and unwind without ever running this plan.
        if (abortRequested || !isCurrentLocalBattleThread()) {
            return BattlePlan.forCombatant(combatant, gridLength);
        }

        // Read the plan on the render thread to avoid racing a drag-commit.
        final java.util.concurrent.atomic.AtomicReference<com.jjktbf.model.combat.BattlePlan> holder =
            new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.CountDownLatch panelClosed = new java.util.concurrent.CountDownLatch(1);
        Thread run = Thread.currentThread();
        Gdx.app.postRunnable(() -> {
            if (mode == BattleMode.LOCAL
                && localBattleThread == run
                && !abortRequested
                && game.getScreen() == this) {
                holder.set(planningPanel == null ? null : planningPanel.getPlan());
                Gdx.input.setInputProcessor(null);
                // Keep the locked planner visible until the next planner or the
                // resolution UI replaces it, preventing an execution-HUD flash.
            }
            panelClosed.countDown();
        });
        // Wait for the render-thread cleanup. Blocking on the latch lets the
        // thread park instead of busy-spinning; an interrupt (e.g. during
        // shutdown) restores the flag and returns the plan gathered so far.
        try {
            panelClosed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        BattlePlan result = holder.get();
        if (result == null) {
            // Fallback: empty plan (bank the round) — should not normally happen.
            result = BattlePlan.forCombatant(combatant, gridLength);
        }
        return result;
    }

    @Override
    public TeamBattlePlan promptTeamBattlePlan(
        List<BattleCombatant> controlled,
        BattleState state
    ) {
        int gridLength = TeamBattlePlan.gridLengthForRound(state);
        TeamBattlePlan empty = emptyTeamPlan(controlled, state, gridLength);
        if (controlled == null || controlled.isEmpty()
            || abortRequested || !isCurrentLocalBattleThread()) {
            return empty;
        }

        inputConfirmed = false;
        postLocal(() -> {
            syncLocalBattlefield(state);
            syncLocalHpFromModel();
            syncLocalAbilityStatesFromModel();
            initPanels();
            awaitingBattleStart = false;
            awaitingNextRound = false;
            nextRoundHovered = false;
            executionUiActive = true;
            clearTransientAnimations();
            planningPanel = null;
            teamPlanningPanel = new TeamPlanningPanel(
                gridLength,
                controlled,
                state,
                assets.battleUi,
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight());
            teamPlanningPanel.setLayout(uiLayout);
            configurePlanningViewport();
            teamPlanningPanel.setSoundPlayer(game.audio()::play);
            teamPlanningPanel.setOnConfirm(() -> {
                game.audio().play(SoundCue.UI_PLAN_LOCK);
                inputConfirmed = true;
            });
            Gdx.input.setInputProcessor(teamPlanningPanel.inputProcessor());
            logScrollInputAttached = false;
            updatePanels();
            inputConfirmed = false;
        });

        while (!inputConfirmed && !abortRequested && isCurrentLocalBattleThread()) {
            sleepMs(16);
        }
        if (abortRequested || !isCurrentLocalBattleThread()) return empty;

        java.util.concurrent.atomic.AtomicReference<TeamBattlePlan> holder =
            new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch panelClosed = new java.util.concurrent.CountDownLatch(1);
        Thread run = Thread.currentThread();
        Gdx.app.postRunnable(() -> {
            if (mode == BattleMode.LOCAL
                && localBattleThread == run
                && !abortRequested
                && game.getScreen() == this) {
                holder.set(teamPlanningPanel == null ? null : teamPlanningPanel.getTeamPlan());
                Gdx.input.setInputProcessor(null);
                // Keep the locked planner visible until the next planner or the
                // resolution UI replaces it, preventing an execution-HUD flash.
            }
            panelClosed.countDown();
        });
        try {
            panelClosed.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return holder.get() == null ? empty : holder.get();
    }

    private static TeamBattlePlan emptyTeamPlan(
        List<BattleCombatant> controlled,
        BattleState state,
        int gridLength
    ) {
        com.jjktbf.model.combat.BattleTeamId teamId = controlled == null || controlled.isEmpty()
            ? state.playerTeam().id() : controlled.get(0).getTeamId();
        TeamBattlePlan plan = new TeamBattlePlan(teamId, gridLength);
        if (controlled != null) {
            for (BattleCombatant combatant : controlled) {
                plan.put(combatant.getInstanceId(),
                    BattlePlan.forCombatant(combatant, gridLength));
            }
        }
        return plan;
    }



    @Override
    public void displayCombatEvents(List<CombatEvent> events, BattleState state) {
        if (abortRequested || !isCurrentLocalBattleThread()) return;
        if (!skipRoundRequested) ensureLocalLifecycleVisualsAndWait(events, state);

        for (CombatEvent e : events) {
            if (abortRequested || !isCurrentLocalBattleThread()) return;
            // Legacy 1v1 resolution does not emit COMBATANT_DEFEATED. Catch its
            // loser before the BATTLE_OVER line so every KO gets the same exit.
            if (e.getType() == CombatEvent.Type.BATTLE_OVER) {
                playMissingLocalFaints(state);
            }
            applyLocalDomainEvent(e);
            if (!skipRoundRequested) {
                BattleAudioRouter.cueFor(e)
                    .ifPresent(cue -> postLocal(() -> game.audio().play(cue)));
            }
            if (!skipRoundRequested && e.getType() == CombatEvent.Type.MOVE_FIRED) {
                Move unleashedMove = e.getMove();
                postLocal(() -> playMoveUnleashAnimation(unleashedMove));
            }
            // Per-hit impact visuals. For multi-hit moves each connecting hit
            // (damage, block, dodge, parry) spawns its own targeted flash so the
            // hits read as distinct strikes; single-hit moves keep using the
            // shared center-screen unleash slot.
            if (!skipRoundRequested && (e.getType() == CombatEvent.Type.DAMAGE_DEALT
                || e.getType() == CombatEvent.Type.DAMAGE_IGNORED
                || e.getType() == CombatEvent.Type.MOVE_BLOCKED
                || e.getType() == CombatEvent.Type.MOVE_BLOCK_REDUCED
                || e.getType() == CombatEvent.Type.MOVE_DODGED
                || e.getType() == CombatEvent.Type.MOVE_PARRIED)) {
                final CombatEvent impactEvent = e;
                postLocal(() -> spawnHitFlash(impactEvent));
            }
            if (!skipRoundRequested && (e.getType() == CombatEvent.Type.MOVE_BLOCKED
                || e.getType() == CombatEvent.Type.MOVE_BLOCK_REDUCED)) {
                CombatEvent blockEvent = e;
                // Single-hit blocked moves use the shared center unleash; a
                // multi-hit move's per-hit blocks are drawn as flashes above.
                if (blockEvent.getMove() == null
                    || blockEvent.getMove().getHitComponents().size() <= 1) {
                    postLocal(() -> playSuccessfulBlockAnimation(blockEvent));
                }
            }
            if (!skipRoundRequested && e.getType() == CombatEvent.Type.RATIO_TRIGGERED) {
                postLocal(this::playRatioUnleashAnimation);
            }
            if (e.getType() == CombatEvent.Type.CHARACTER_TRANSFORMED
                || e.getType() == CombatEvent.Type.CHARACTER_REVERTED) {
                final CombatEvent formEvent = e;
                postLocal(() -> {
                    refreshLocalFormSprite(formEvent);
                    // The new form arrives with the same grow-from-white
                    // entrance as a summon; a skipped round just swaps.
                    if (!skipRoundRequested && formEvent.getTarget() != null) {
                        startLocalPanelEntrance(
                            formEvent.getTarget(),
                            localSummonIsOnPlayerSide(state, formEvent.getTarget()));
                    }
                });
            }
            // A summon joins the field the instant its join broadcast plays —
            // sprite, HUD, and entrance animation all land on the summon tick.
            // The join log line below paces the battle thread while the
            // entrance runs, so the next event cannot outrun the arrival.
            if (e.getType() == CombatEvent.Type.COMBATANT_SUMMONED) {
                final CombatEvent summonEvent = e;
                final boolean playerSide = localSummonIsOnPlayerSide(state, e.getTarget());
                postLocal(() -> {
                    if (skipRoundRequested) {
                        addLocalCombatantToField(summonEvent.getTarget(), playerSide);
                    } else {
                        startLocalSummonEntrance(summonEvent.getTarget(), playerSide);
                    }
                });
            }
            if (e.getType() == CombatEvent.Type.COMBATANT_SWITCHED
                || e.getType() == CombatEvent.Type.COMBATANT_REPLACED) {
                BattleCombatant incoming = e.getTarget();
                postLocal(() -> {
                    syncLocalBattlefield(state);
                    initPanels();
                    updatePanels();
                    if (!skipRoundRequested && incoming != null) {
                        startLocalPanelEntrance(
                            incoming, localSummonIsOnPlayerSide(state, incoming));
                    }
                });
            }
            applyLocalAbilityEvent(e);
            if (hasLocalPlaybackEffect(e)) {
                final CombatEvent ev = e;
                // Apply this event's resource delta and enqueue any log line ON
                // THE BATTLE THREAD, before posting the render work. Doing this
                // inside the posted lambda left the queue empty at the moment
                // waitForLogLine() checked it, so the gate returned instantly
                // and every event's runnable piled up and ran back-to-back —
                // making HP appear to drop at the unleash line. Enqueuing here
                // guarantees the queue is non-empty when we wait.
                applyLocalHpEvent(ev);
                if (shouldLog(e)) queueLogLine(e.getMessage());
                if (!skipRoundRequested) {
                    postLocal(() -> {
                        if (!skipRoundRequested) flashLocalDamageSprite(ev);
                        updatePanels();
                    });
                }
                // Round-start ability events fire before the first planning
                // phase flips executionUiActive; gating there would stall the
                // battle thread behind a blank screen. The lines still type
                // out, they just don't block (see displayMessage).
                if (executionUiActive && shouldLog(e)) waitForLogLine();
            } else if (e.getCodedAbilityState() != null && !skipRoundRequested) {
                postLocal(this::updatePanels);
            }
            if (e.getType() == CombatEvent.Type.COMBATANT_DEFEATED) {
                if (skipRoundRequested) {
                    markAndRemoveLocalCombatantImmediately(e.getTarget());
                } else {
                    playLocalFaintAndWait(e.getTarget());
                }
            } else if (e.getType() == CombatEvent.Type.COMBATANT_REMOVED) {
                if (skipRoundRequested) {
                    removeLocalCombatantImmediately(e.getTarget());
                } else {
                    removeLocalCombatantAndWait(e.getTarget());
                }
            }
        }
        // Authoritative re-sync: playback banner updates pace with the log,
        // this corrects any drift (durations, barriers, clashes) after the round.
        if (!abortRequested && isCurrentLocalBattleThread()) {
            syncLocalDomainBanners(state);
        }
    }

    private static boolean hasLocalPlaybackEffect(CombatEvent event) {
        return shouldLog(event) || switch (event.getType()) {
            case DAMAGE_DEALT, HP_RESTORED, MAX_HP_CHANGED,
                 CE_DRAINED, CE_RESTORED, CE_DEPLETED, MAX_CE_CHANGED,
                 STATUS_APPLIED, STATUS_EXPIRED -> true;
            default -> false;
        };
    }

    /** Which side's render roster a just-summoned combatant belongs to. */
    static boolean localSummonIsOnPlayerSide(BattleState state, BattleCombatant summon) {
        return state != null && summon != null
            && state.playerTeam().all().stream()
                .anyMatch(candidate -> candidate == summon);
    }

    private static boolean shouldLog(CombatEvent event) {
        if (event == null || event.getMessage() == null || event.getMessage().isBlank()) {
            return false;
        }
        return shouldLog(event.getType(), event.getSource(), event.getTarget(), event.getMove());
    }

    private static boolean shouldLog(BattleEventState event) {
        if (event == null || event.message() == null || event.message().isBlank()) return false;
        return shouldLog(event.type(), event.sourceCharacterId(), event.targetCharacterId(),
            event.moveId());
    }

    private static boolean shouldLog(
        CombatEvent.Type type,
        Object source,
        Object target,
        Object move
    ) {
        return switch (type) {
            case CE_DRAINED, CE_RESTORED,
                 HP_RESTORED, MAX_HP_CHANGED, MAX_CE_CHANGED,
                 MOVE_SUMMON, BFS_EXPIRED,
                 DOMAIN_BARRIER_DAMAGED -> false;
            case CE_DEPLETED -> move != null;
            case DAMAGE_DEALT, DAMAGE_IGNORED -> move != null && source != target;
            default -> true;
        };
    }

    private static boolean shouldLog(
        BattleEventType type,
        String sourceId,
        String targetId,
        String moveId
    ) {
        return switch (type) {
            case CE_DRAINED, CE_RESTORED,
                 HP_RESTORED, MAX_HP_CHANGED, MAX_CE_CHANGED,
                 MOVE_SUMMON, BFS_ENTERED, BFS_EXPIRED,
                 DOMAIN_BARRIER_DAMAGED -> false;
            case CE_DEPLETED -> moveId != null;
            case DAMAGE_DEALT, DAMAGE_IGNORED -> moveId != null
                && (sourceId == null || !sourceId.equals(targetId));
            default -> true;
        };
    }

    private void ensureLocalLifecycleVisualsAndWait(
        List<CombatEvent> events,
        BattleState state
    ) {
        if (state == null || abortRequested || !isCurrentLocalBattleThread()) return;
        Set<BattleCombatant> lifecycleTargets = pendingLocalLifecycleTargets(
            events, state, presentedLocalFaints);
        if (lifecycleTargets.isEmpty()
            || lifecycleTargets.stream().allMatch(target -> panelForCombatant(target) != null)) {
            return;
        }

        List<BattleCombatant> players = lifecycleVisualRoster(
            state.playerTeam(), renderPlayerTeam, lifecycleTargets);
        List<BattleCombatant> enemies = lifecycleVisualRoster(
            state.enemyTeam(), renderEnemyTeam, lifecycleTargets);
        if (players.isEmpty() && enemies.isEmpty()) return;

        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        postLocal(() -> {
            renderLocalState = state;
            syncLocalBattlefield(players, enemies);
            rewindLocalHpEvents(events);
            initPanels();
            showExecutionUi();
            updatePanels();
            ready.countDown();
        });
        while (ready.getCount() > 0L && !abortRequested && isCurrentLocalBattleThread()) {
            sleepMs(16);
        }
    }

    static Set<BattleCombatant> pendingLocalLifecycleTargets(
        List<CombatEvent> events,
        BattleState state,
        Set<CombatantId> presentedFaints
    ) {
        Set<BattleCombatant> lifecycleTargets = new HashSet<>();
        boolean battleEnding = state.isBattleOver();
        for (CombatEvent event : events) {
            if (event.getType() == CombatEvent.Type.COMBATANT_DEFEATED
                || event.getType() == CombatEvent.Type.COMBATANT_REMOVED) {
                if (event.getTarget() != null) lifecycleTargets.add(event.getTarget());
            }
            battleEnding |= event.getType() == CombatEvent.Type.BATTLE_OVER;
        }
        if (battleEnding) {
            state.playerTeam().all().stream()
                .filter(BattleCombatant::isDefeated).forEach(lifecycleTargets::add);
            state.enemyTeam().all().stream()
                .filter(BattleCombatant::isDefeated).forEach(lifecycleTargets::add);
        }
        lifecycleTargets.removeIf(combatant ->
            presentedFaints.contains(combatant.getInstanceId()));
        return lifecycleTargets;
    }

    static List<BattleCombatant> lifecycleVisualRoster(
        BattleTeam team,
        List<BattleCombatant> displayed,
        Set<BattleCombatant> lifecycleTargets
    ) {
        List<BattleCombatant> roster = new ArrayList<>(displayed);
        if (roster.isEmpty()) {
            for (BattleCombatant combatant : team.all()) {
                if (combatant.isActive() || lifecycleTargets.contains(combatant)) {
                    roster.add(combatant);
                    if (roster.size() == MAX_VISIBLE_COMBATANTS_PER_SIDE) break;
                }
            }
            return List.copyOf(roster);
        }
        for (BattleCombatant combatant : team.all()) {
            if (roster.size() == MAX_VISIBLE_COMBATANTS_PER_SIDE) break;
            if (lifecycleTargets.contains(combatant)
                && roster.stream().noneMatch(current -> current == combatant)) {
                roster.add(combatant);
            }
        }
        return List.copyOf(roster);
    }

    private void playMissingLocalFaints(BattleState state) {
        if (state == null) return;
        List<BattleCombatant> displayed = new ArrayList<>(renderPlayerTeam);
        displayed.addAll(renderEnemyTeam);
        for (BattleCombatant combatant : displayed) {
            if (!combatant.isDefeated()) continue;
            if (skipRoundRequested) {
                markAndRemoveLocalCombatantImmediately(combatant);
            } else {
                playLocalFaintAndWait(combatant);
            }
        }
    }

    private void markAndRemoveLocalCombatantImmediately(BattleCombatant combatant) {
        if (combatant != null && combatant.getInstanceId() != null) {
            presentedLocalFaints.add(combatant.getInstanceId());
        }
        removeLocalCombatantImmediately(combatant);
    }

    private void removeLocalCombatantImmediately(BattleCombatant combatant) {
        if (combatant != null) postLocal(() -> removeLocalCombatantFromField(combatant));
    }

    private void playLocalFaintAndWait(BattleCombatant combatant) {
        if (combatant == null || panelForCombatant(combatant) == null
            || abortRequested || !isCurrentLocalBattleThread()) {
            return;
        }
        CombatantId instanceId = combatant.getInstanceId();
        if (instanceId != null && !presentedLocalFaints.add(instanceId)) return;

        java.util.concurrent.CountDownLatch complete = new java.util.concurrent.CountDownLatch(1);
        postLocal(() -> {
            CombatantPanel panel = panelForCombatant(combatant);
            boolean playerSide = renderPlayerTeam.stream()
                .anyMatch(candidate -> candidate == combatant);
            FaintAnimation faint = FaintAnimation.local(
                combatant, playerSide, panel,
                () -> {
                    removeLocalCombatantFromField(combatant);
                    complete.countDown();
                });
            if (!startFaintAnimation(faint)) complete.countDown();
        });
        while (complete.getCount() > 0L && !abortRequested && isCurrentLocalBattleThread()) {
            sleepMs(16);
        }
    }

    private void removeLocalCombatantAndWait(BattleCombatant combatant) {
        if (combatant == null || panelForCombatant(combatant) == null
            || abortRequested || !isCurrentLocalBattleThread()) {
            return;
        }
        java.util.concurrent.CountDownLatch complete = new java.util.concurrent.CountDownLatch(1);
        postLocal(() -> {
            removeLocalCombatantFromField(combatant);
            complete.countDown();
        });
        while (complete.getCount() > 0L && !abortRequested && isCurrentLocalBattleThread()) {
            sleepMs(16);
        }
    }

    @Override
    public void displayResolutionStart(BattleState state) {
        if (abortRequested || !isCurrentLocalBattleThread()) return;
        synchronized (this) {
            playbackControlsOpen = true;
            fastForwardActive = false;
            skipRoundRequested = false;
        }
        postLocal(this::showExecutionUi);
    }

    @Override
    public void displayResolutionTick(int tick, BattleState state) {
        if (abortRequested || !isCurrentLocalBattleThread()) return;
        // Hold the previous tick before advancing, regardless of whether it
        // fired a move. Move animations continue independently of this pace.
        if (resolvingTicks) {
            abortableSleepMs(TICK_DURATION_MS);
        } else {
            postLocal(this::showExecutionUi);
        }
        currentExecutionTick = tick;
        resolvingTicks = true;
    }

    @Override
    public void displayRoundEnd(BattleState state) {
        if (!isCurrentLocalBattleThread()) return;
        synchronized (this) {
            playbackControlsOpen = false;
            fastForwardActive = false;
        }
        // The final tick's brief hold runs here because no later
        // displayResolutionTick call follows it.
        if (resolvingTicks) {
            abortableSleepMs(TICK_DURATION_MS);
        }
        resolvingTicks = false;
        postLocal(() -> {
            showExecutionUi();
            // Re-seed from the model so end-of-round maintenance (poison, max-HP
            // changes, etc.) converges the deferred bars back to the true HP.
            syncLocalHpFromModel();
            updatePanels();
            if (skipRoundRequested) {
                flushTypingImmediately();
                clearTransientAnimations();
                completeFaintAnimationsImmediately();
            }
            snapPanelAnimations();
            resetPlaybackControls();
        });
    }

    @Override
    public void awaitNextRound(BattleState state) {
        if (!isCurrentLocalBattleThread()) return;
        nextRoundConfirmed = false;
        postLocal(() -> {
            awaitingNextRound = true;
            nextRoundHovered = false;
        });

        while (!nextRoundConfirmed && !abortRequested && isCurrentLocalBattleThread()) {
            sleepMs(16);
        }

        // Keep the completed round visible until the next planning panel is
        // ready. Clearing this here exposed the execution HUD for a frame.
    }

    @Override
    public void displayBattleOver(BattleCombatant winner, BattleState state) {
        if (!isCurrentLocalBattleThread()) return;
        synchronized (this) {
            playbackControlsOpen = false;
            fastForwardActive = false;
        }
        // Compatibility fallback for a resolver that omitted lifecycle events.
        ensureLocalLifecycleVisualsAndWait(List.of(), state);
        playMissingLocalFaints(state);
        SoundCue resultCue = winner == null
            ? SoundCue.BATTLE_DRAW
            : BattleTeamId.PLAYER.equals(state.getWinnerTeam())
                ? SoundCue.BATTLE_VICTORY : SoundCue.BATTLE_DEFEAT;
        postLocal(() -> {
            if (winner == null) {
                battleResult = "DRAW!";
            } else {
                battleResult = winner.getCharacter().getName() + " WINS!";
            }
            if (skipRoundRequested) {
                flushTypingImmediately();
                clearTransientAnimations();
                completeFaintAnimationsImmediately();
                syncLocalHpFromModel();
                updatePanels();
                snapPanelAnimations();
            }
            game.audio().play(resultCue);
            resetPlaybackControls();
            battleOver = true;
        });
    }

    @Override
    public void displayMessage(String message) {
        if (abortRequested || !isCurrentLocalBattleThread()) return;
        // Enqueue on the battle thread so the queue is non-empty when we gate
        // (see displayCombatEvents for why enqueuing inside postLocal races).
        queueLogLine(message);
        // The opening line is queued before awaitBattleStart publishes the
        // execution scene. Its button remains disabled until typing finishes,
        // so this callback must not block before that scene is visible.
        if (executionUiActive) waitForLogLine();
    }

    /** Polled by the controller thread to unwind the loop on an Escape abort. */
    @Override
    public boolean isAborted() {
        return abortRequested || !isCurrentLocalBattleThread();
    }

    // -------------------------------------------------------------------------
    // Authoritative multiplayer flow (render thread)
    // -------------------------------------------------------------------------

    private void startMultiplayer() {
        if (multiplayerSetup == null || multiplayerMatchService == null) return;

        executionUiActive = true;
        multiplayerConnectionState = MultiplayerSession.ConnectionState.DISCONNECTED;
        long run = ++multiplayerRun;

        // Load one sprite per fighter id per side so team fields show the full roster.
        // Each side's sprite list is in MatchSetup roster order; the layout
        // builder maps up to four entries onto the shared plate and HUD grid.
        java.util.List<Texture> playerSprites = new java.util.ArrayList<>();
        for (String id : multiplayerSetup.playerCharacterIds()) {
            playerSprites.add(assets.characterBattleSprite(
                game.multiplayerSpriteAsset(id), false, assets.playerSprite));
        }
        java.util.List<Texture> enemySprites = new java.util.ArrayList<>();
        for (String id : multiplayerSetup.opponentCharacterIds()) {
            enemySprites.add(assets.characterBattleSprite(
                game.multiplayerSpriteAsset(id), true, assets.enemySprite));
        }
        setTeamSprites(playerSprites, enemySprites);

        multiplayerListener = new MultiplayerBattleListener(run, multiplayerSetup.matchId());
        multiplayerMatchService.addListener(multiplayerListener);
        if (multiplayerSetup.state() != null) applyMultiplayerState(multiplayerSetup.state());

        multiplayerMatchService.connect(multiplayerSetup).whenComplete((ignored, failure) -> {
            if (failure != null) {
                postMultiplayer(run, () -> addLogLine(
                    "Could not connect to the authoritative match: " + safeMessage(failure)));
            }
        });
    }

    private void applyMultiplayerState(MatchState state) {
        if (state == null || multiplayerSetup == null
            || !multiplayerSetup.matchId().equals(state.matchId())) {
            return;
        }

        PlayerState local = state.player(multiplayerSetup.playerSide()).orElse(null);
        PlayerState opponent = state.player(opposite(multiplayerSetup.playerSide())).orElse(null);
        if (local == null || opponent == null
            || local.character() == null || opponent.character() == null) {
            addLogLine("The server returned an incomplete battle state.");
            return;
        }

        multiplayerState = state;
        onlinePlayer = local;
        onlineEnemy = opponent;
        syncOnlineDomainBanners(state);
        syncMultiplayerPlanningCountdown(state);
        initOnlineMoves(local, opponent);

        if (state.phase() == BattlePhase.PRE_BATTLE && !isTerminal(state.status())) {
            if (!windowsUnified()) closePlanningPanel();
            resetPlaybackControls();
            awaitingBattleStart = true;
            awaitingNextRound = false;
            resolvingTicks = false;
            playbackComplete = false;
            currentExecutionTick = 0;
            syncOnlineBattlefield(
                activeOnlineCombatants(local), activeOnlineCombatants(opponent));
            seedOnlineResourcesFromCurrentState();
            CharacterState displayedPlayer = displayedOnlinePrimary(true);
            onlinePlayerMiracles = displayedPlayer == null
                ? null : findMiraclesState(displayedPlayer.codedAbilities());
            onlinePlayerRatio = displayedPlayer == null
                ? null : findRatioState(displayedPlayer.codedAbilities());
            initPanels();
            updatePanels();
            ensureOnlinePlanner(state.roundNumber(), local, opponent, true);
            if (teamPlanningPanel != null) teamPlanningPanel.setActionButtonShifted(false);
            if (!battleIntroLogged) {
                battleIntroLogged = true;
                queueLogLine("The battle between " + onlineTeamName(local)
                    + " and " + onlineTeamName(opponent) + " begins.");
            }
            if (!battleEntranceStarted) {
                battleEntranceStarted = true;
                for (CharacterState combatant : renderOnlinePlayerTeam) {
                    startOnlinePanelEntrance(
                        multiplayerSetup.playerSide(), combatant, true);
                }
                PlayerSide opponentSide = opposite(multiplayerSetup.playerSide());
                for (CharacterState combatant : renderOnlineEnemyTeam) {
                    startOnlinePanelEntrance(opponentSide, combatant, true);
                }
            }
            return;
        }

        if (state.phase() == BattlePhase.PLANNING && !isTerminal(state.status())) {
            if (soundedOnlineRound != state.roundNumber()) {
                soundedOnlineRound = state.roundNumber();
                game.audio().play(SoundCue.BATTLE_ROUND_START);
            }
            if (playbackReturnsToPlanning && resolvingTicks
                && playbackRound == state.roundNumber()) {
                return;
            }
            if (hasUnplayedPlanningEvents(state)) {
                startMultiplayerPlayback(state, true);
                return;
            }
            resetPlaybackControls();
            awaitingBattleStart = false;
            awaitingNextRound = false;
            nextRoundHovered = false;
            resolvingTicks = false;
            playbackComplete = false;
            currentExecutionTick = 0;
            syncOnlineBattlefield(
                activeOnlineCombatants(local), activeOnlineCombatants(opponent));
            seedOnlineResourcesFromCurrentState();
            CharacterState displayedPlayer = displayedOnlinePrimary(true);
            onlinePlayerMiracles = displayedPlayer == null
                ? null : findMiraclesState(displayedPlayer.codedAbilities());
            onlinePlayerRatio = displayedPlayer == null
                ? null : findRatioState(displayedPlayer.codedAbilities());
            initPanels();
            updatePanels();
            logOnlineEvents(state.recentEvents());

            ensureOnlinePlanner(state.roundNumber(), local, opponent);
            return;
        }

        if ((state.phase() == BattlePhase.ROUND_END
            || state.phase() == BattlePhase.BATTLE_OVER)
            && playbackRound != state.roundNumber()) {
            startMultiplayerPlayback(state);
        } else if (state.phase() == BattlePhase.BATTLE_OVER) {
            refreshTerminalPlayback(state);
        }
        updatePanels();
    }

    private void ensureOnlinePlanner(
        int roundNumber,
        PlayerState local,
        PlayerState opponent
    ) {
        ensureOnlinePlanner(roundNumber, local, opponent, false);
    }

    private void ensureOnlinePlanner(
        int roundNumber,
        PlayerState local,
        PlayerState opponent,
        boolean readOnly
    ) {
        if (onlinePlanningRound == roundNumber && teamPlanningPanel != null) {
            configureOnlinePlannerAvailability(local, readOnly);
            return;
        }
        if (multiplayerState == null
            || multiplayerState.status() != MatchStatus.ACTIVE
            || (!readOnly && (multiplayerConnectionState
                != MultiplayerSession.ConnectionState.CONNECTED
                || resolvingTicks
                || onlineCommandPending))) {
            return;
        }

        int gridLength = onlineBattleGridLength();
        List<PlanningPanel.TargetOption> targets = opponent.combatants().stream()
            .filter(BattleScreen::isActiveCombatant)
            .map(combatant -> new PlanningPanel.TargetOption(
                combatant.instanceId(),
                combatant.name() + " #" + (combatant.rosterOrder() + 1),
                "SUMMON".equalsIgnoreCase(combatant.role())))
            .toList();
        List<TeamPlanningPanel.PageSpec> pages = new ArrayList<>();
        List<TeamPlanningPanel.PartyMember> party = local.combatants().stream()
            .filter(character -> "FIGHTER".equalsIgnoreCase(character.role()))
            .map(character -> new TeamPlanningPanel.PartyMember(
                character.instanceId(),
                character.name(),
                character.currentHp(),
                character.maxHp(),
                isActiveCombatant(character),
                "RESERVE".equalsIgnoreCase(character.lifecycle()),
                character.currentHp() <= 0
                    || "DEFEATED".equalsIgnoreCase(character.lifecycle()),
                character.rosterOrder()))
            .toList();
        for (CharacterState character : local.combatants()) {
            if (!isActiveCombatant(character)) continue;
            List<PlanningPanel.TargetOption> allies = local.combatants().stream()
                .filter(BattleScreen::isActiveCombatant)
                .filter(candidate -> !character.instanceId().equals(candidate.instanceId()))
                .map(combatant -> new PlanningPanel.TargetOption(
                    combatant.instanceId(),
                    combatant.name() + " #" + (combatant.rosterOrder() + 1),
                    "SUMMON".equalsIgnoreCase(combatant.role())))
                .toList();
            Map<String, Integer> ceCosts = new HashMap<>();
            List<Move> availableMoves = new ArrayList<>();
            for (MoveState moveState : character.knownMoves()) {
                try {
                    Move move = toDisplayMove(moveState);
                    availableMoves.add(move);
                    ceCosts.put(move.getId(), moveState.effectiveCeCost());
                } catch (RuntimeException failure) {
                    addLogLine("Could not display move " + moveState.name() + ".");
                }
            }
            int apBudget = character.plan() == null
                ? character.maxAp() : character.plan().apBudget();
            int ceBudget = character.plan() == null
                ? character.currentCe() : character.plan().ceBudget();
            pages.add(new TeamPlanningPanel.PageSpec(
                character.instanceId(),
                character.name(),
                availableMoves,
                ceCosts,
                apBudget,
                ceBudget,
                character.maxCe(),
                findMiraclesState(character.codedAbilities()),
                targets,
                character.plan(),
                allies,
                character.codedAbilities(),
                character.statusEffects()));
        }
        if (pages.isEmpty()) return;
        clearTransientAnimations();
        planningPanel = null;
        teamPlanningPanel = new TeamPlanningPanel(
            BattleTeamId.PLAYER,
            gridLength,
            pages,
            party,
            assets.battleUi,
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight()
        );
        teamPlanningPanel.setLayout(uiLayout);
        configurePlanningViewport();
        teamPlanningPanel.setSoundPlayer(game.audio()::play);
        teamPlanningPanel.setOnConfirm(this::submitOnlinePlan);
        onlinePlanningRound = roundNumber;
        configureOnlinePlannerAvailability(local, readOnly);
    }

    private void configureOnlinePlannerAvailability(PlayerState local, boolean readOnly) {
        if (teamPlanningPanel == null) return;
        if (readOnly) {
            teamPlanningPanel.lock();
            teamPlanningPanel.setReadOnly(true);
            Gdx.input.setInputProcessor(null);
            logScrollInputAttached = false;
            return;
        }
        if (onlinePlanningTimedOut
            || shouldAutoLockPlanning(multiplayerPlanningRemainingMillis())) {
            teamPlanningPanel.lock();
            teamPlanningPanel.setReadOnly(true);
            Gdx.input.setInputProcessor(null);
            logScrollInputAttached = false;
            return;
        }
        teamPlanningPanel.setReadOnly(false);
        teamPlanningPanel.setActionButtonShifted(false);
        if (onlineCommandPending || (local != null && local.planSubmitted())) {
            teamPlanningPanel.lock();
        } else {
            teamPlanningPanel.unlock();
        }
        Gdx.input.setInputProcessor(teamPlanningPanel.inputProcessor());
        logScrollInputAttached = false;
    }

    private void initOnlineMoves(PlayerState... players) {
        Map<String, Move> converted = new HashMap<>();
        for (PlayerState player : players) {
            if (player == null) continue;
            for (CharacterState character : player.combatants()) {
                for (MoveState state : character.knownMoves()) {
                    try {
                        converted.putIfAbsent(state.moveId(), toDisplayMove(state));
                    } catch (RuntimeException failure) {
                        addLogLine("Could not display move " + state.name() + ".");
                    }
                }
            }
        }
        onlineMoves = Map.copyOf(converted);
    }

    private static boolean isActiveCombatant(CharacterState combatant) {
        return combatant != null && "ACTIVE".equals(combatant.lifecycle());
    }

    private boolean hasUnplayedPlanningEvents(MatchState state) {
        return playedPlanningEventsRound != state.roundNumber()
            && state.recentEvents().stream()
                .anyMatch(event -> event.roundNumber() == state.roundNumber());
    }

    static Move toDisplayMove(MoveState state) {
        MoveCategory category = MoveCategory.valueOf(state.category());
        EnumSet<MoveTag> tags = EnumSet.noneOf(MoveTag.class);
        for (String tagName : state.tags()) {
            try {
                tags.add(MoveTag.valueOf(tagName));
            } catch (IllegalArgumentException ignored) {
                // Unknown future tags are presentation-only on an older client.
            }
        }
        Map<String, Integer> prerequisites = new HashMap<>();
        boolean innateTechnique = tags.contains(MoveTag.INNATE_TECHNIQUE)
            || category.getTags().contains(MoveTag.INNATE_TECHNIQUE);
        boolean nonInnateTechnique = tags.contains(MoveTag.NON_INNATE_TECHNIQUE)
            || category.getTags().contains(MoveTag.NON_INNATE_TECHNIQUE);
        if (innateTechnique) {
            prerequisites.put("cursedTechniqueMastery", 0);
        }
        if (nonInnateTechnique) {
            prerequisites.put("jujutsuSkill", 0);
        }
        List<StatusEffect> commandEffects = state.commandMode() == null
            || state.commandMode().isBlank()
            ? List.of()
            : List.of(StatusEffect.coded(
                CursedSpeechAbility.KEY,
                CursedSpeechAbility.COMMAND,
                state.commandMode(),
                null,
                Map.of(),
                null));
        List<MoveEffectData> planningEffects = new ArrayList<>();
        for (var transaction : state.boundedResourceTransactions()) {
            MoveEffectData effect = AbilityEffectType.TRANSACT_BOUNDED_RESOURCE
                .createDefaultMoveEffect();
            effect.effectId = "online-resource-" + planningEffects.size();
            effect.trigger = MoveEffectTrigger.ON_START.name();
            effect.sourceResourceKey = transaction.sourceResourceKey();
            effect.sourceResourceAmount = transaction.sourceResourceAmount();
            effect.targetResourceKey = transaction.targetResourceKey();
            effect.targetResourceAmount = transaction.targetResourceAmount();
            planningEffects.add(effect);
        }
        for (String definitionId : state.summonedCharacterIds()) {
            MoveEffectData effect = AbilityEffectType.SUMMON_CHARACTER.createDefaultMoveEffect();
            effect.effectId = "online-summon-" + planningEffects.size();
            effect.trigger = MoveEffectTrigger.ON_FIRE.name();
            effect.characterId = definitionId;
            planningEffects.add(effect);
        }

        Set<MoveTag> builderTags = EnumSet.copyOf(tags);
        if (!state.hitComponents().isEmpty()) {
            builderTags.removeAll(MoveTag.HIT_ONLY_TAGS);
        }
        Move.Builder builder = new Move.Builder(state.moveId())
            .name(state.name())
            .description(state.description())
            .category(category)
            .tags(builderTags)
            .basePower(state.basePower())
            .baseAccuracy(state.baseAccuracy())
            .neverMiss(state.neverMiss())
            .guardBreak(state.hitComponents().isEmpty()
                && tags.contains(MoveTag.GUARD_BREAK))
            .heavy(tags.contains(MoveTag.HEAVY))
            .apCost(state.apCost())
            .unleashPoint(state.unleashPoint())
            .baseCeCost(state.baseCeCost())
            .hasCeCost(state.hasCeCost())
            .minCeCost(state.minCeCost())
            .maxCeCost(state.maxCeCost())
            .moveCap(state.moveCap())
            .summonCharacterId(state.summonCharacterId())
            .selfEffects(state.summonedCharacterIds().stream()
                .filter(id -> !id.equals(state.summonCharacterId()))
                .map(StatusEffect::new)
                .toList())
            .onHitEffects(commandEffects)
            .prerequisites(prerequisites)
            .defenseTargeting(TargetListSupport.moveStateDefenseTargeting(state))
            .defenseTargetCount(TargetListSupport.moveStateDefenseTargetCount(state))
            .targeting(TargetListSupport.moveStateTargeting(state))
            .freeMove(true);
        if (!planningEffects.isEmpty()) builder.effects(planningEffects);
        if (TargetListSupport.moveStateAoeType(state) != null) {
            builder.aoeType(TargetListSupport.moveStateAoeType(state))
                .aoeTargetCount(TargetListSupport.moveStateAoeTargetCount(state));
        }
        if (!state.hitComponents().isEmpty()) {
            builder.hitComponents(state.hitComponents().stream()
                .map(component -> toDisplayHitComponent(component, commandEffects))
                .toList());
        }
        AttackLaunchMode launchMode = AttackLaunchMode.fromName(state.attackLaunchMode());
        if (launchMode != null) {
            builder.attackLaunchMode(launchMode)
                .attackLaunchMoveId(state.attackLaunchMoveId());
        }
        if (innateTechnique) {
            String requiredTechniqueId = state.requiredTechniqueId();
            builder.requiredTechniqueId(requiredTechniqueId == null || requiredTechniqueId.isBlank()
                ? "ONLINE_DISPLAY" : requiredTechniqueId);
        }
        return builder.build();
    }

    private static HitComponent toDisplayHitComponent(
        HitComponentState state,
        List<StatusEffect> onHitEffects
    ) {
        EnumSet<MoveTag> tags = EnumSet.noneOf(MoveTag.class);
        for (String tagName : state.tags()) {
            try {
                MoveTag tag = MoveTag.valueOf(tagName);
                if (MoveTag.HIT_TAGS.contains(tag)) tags.add(tag);
            } catch (IllegalArgumentException ignored) {
                // Unknown future hit tags can fall back to the wire category.
            }
        }
        if (!tags.isEmpty()) {
            return new HitComponent(
                state.basePower(), tags, state.delayTicks(),
                state.requiresPreviousConnection(), state.avoidable(),
                state.baseAccuracy(), onHitEffects);
        }
        return new HitComponent(
            state.basePower(), MoveCategory.valueOf(state.category()).getTags(),
            state.delayTicks(), state.requiresPreviousConnection(), state.avoidable(),
            state.baseAccuracy(), onHitEffects);
    }

    private void submitOnlinePlan() {
        submitOnlinePlan(false);
    }

    private void submitOnlinePlan(boolean timedOut) {
        if (!canSubmitOnlinePlan() || teamPlanningPanel == null) {
            if (!timedOut && teamPlanningPanel != null) teamPlanningPanel.unlock();
            if (!timedOut) game.audio().play(SoundCue.UI_DENIED);
            return;
        }
        MultiplayerMatchService.PlanSubmission submission =
            multiplayerMatchService.submitPlan(
                teamPlanningPanel.getPlacements(), teamPlanningPanel.getSwitches());
        if (!submission.sent()) {
            if (!timedOut) teamPlanningPanel.unlock();
            if (!timedOut) game.audio().play(SoundCue.UI_DENIED);
            addLogLine(submissionMessage(submission.status()));
            return;
        }
        onlineCommandPending = true;
        game.audio().play(SoundCue.UI_PLAN_LOCK);
        addLogLine(timedOut
            ? "Time expired. Plan locked."
            : "Plan locked. Waiting for the opponent.");
    }

    private void syncMultiplayerPlanningCountdown(MatchState state) {
        Long deadline = state.planningDeadline();
        if (state.phase() != BattlePhase.PLANNING || deadline == null
            || isTerminal(state.status())) {
            resetMultiplayerPlanningCountdown();
            return;
        }

        boolean changed = onlinePlanningDeadline != deadline;
        if (!changed && state.serverTimestamp() <= onlinePlanningServerTimestamp) {
            return;
        }

        long remainingMillis = Math.max(0L, deadline - state.serverTimestamp());
        long remainingNanos = remainingMillis > Long.MAX_VALUE / 1_000_000L
            ? Long.MAX_VALUE : remainingMillis * 1_000_000L;
        long now = System.nanoTime();
        long candidateDeadlineNanos = remainingNanos == Long.MAX_VALUE
            || now > Long.MAX_VALUE - remainingNanos
                ? Long.MAX_VALUE : now + remainingNanos;
        onlinePlanningDeadlineNanos = changed
            ? candidateDeadlineNanos
            : Math.min(onlinePlanningDeadlineNanos, candidateDeadlineNanos);
        onlinePlanningDeadline = deadline;
        onlinePlanningServerTimestamp = state.serverTimestamp();
        if (changed) {
            onlinePlanningTimedOut = false;
        }
    }

    private void resetMultiplayerPlanningCountdown() {
        onlinePlanningDeadline = -1L;
        onlinePlanningDeadlineNanos = 0L;
        onlinePlanningServerTimestamp = -1L;
        onlinePlanningTimedOut = false;
    }

    private long multiplayerPlanningRemainingMillis() {
        if (onlinePlanningDeadline < 0L) {
            return -1L;
        }
        long remainingNanos = onlinePlanningDeadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            return 0L;
        }
        return Math.max(1L, (remainingNanos + 999_999L) / 1_000_000L);
    }

    private void updateMultiplayerPlanningTimeout() {
        if (multiplayerState == null
            || multiplayerState.phase() != BattlePhase.PLANNING
            || multiplayerState.planningDeadline() == null
            || !shouldAutoLockPlanning(multiplayerPlanningRemainingMillis())
            || onlinePlanningTimedOut) {
            return;
        }
        if (onlinePlayer != null && onlinePlayer.planSubmitted()) {
            onlinePlanningTimedOut = true;
            return;
        }
        if (teamPlanningPanel == null) {
            return;
        }

        onlinePlanningTimedOut = true;
        teamPlanningPanel.lock();
        teamPlanningPanel.setReadOnly(true);
        Gdx.input.setInputProcessor(null);
        logScrollInputAttached = false;
        submitOnlinePlan(true);
    }

    static boolean shouldAutoLockPlanning(long remainingMillis) {
        return remainingMillis >= 0L
            && remainingMillis <= PLANNING_AUTO_SUBMIT_LEAD_MILLIS;
    }

    private boolean canSubmitOnlinePlan() {
        return multiplayerState != null
            && multiplayerState.status() == MatchStatus.ACTIVE
            && multiplayerState.phase() == BattlePhase.PLANNING
            && multiplayerConnectionState == MultiplayerSession.ConnectionState.CONNECTED
            && !onlineCommandPending
            && onlinePlayer != null
            && !onlinePlayer.planSubmitted();
    }

    private void startMultiplayerPlayback(MatchState state) {
        startMultiplayerPlayback(state, false);
    }

    private void startMultiplayerPlayback(MatchState state, boolean returnsToPlanning) {
        ensureOnlinePlanner(state.roundNumber(), onlinePlayer, onlineEnemy, true);
        retainReadOnlyPlanningUi();
        resetPlaybackControls();
        playbackControlsOpen = true;
        playbackReturnsToPlanning = returnsToPlanning;
        if (returnsToPlanning) playedPlanningEventsRound = state.roundNumber();
        playbackRound = state.roundNumber();
        playbackComplete = false;
        playbackEventIndex = 0;
        playbackActionTicks = returnsToPlanning ? List.of() : onlineActionTicks(state);
        playbackActionIndex = 0;
        playbackTickElapsedMs = 0f;
        currentExecutionTick = 0;
        resolvingTicks = true;
        awaitingBattleStart = false;
        awaitingNextRound = false;
        battleOver = false;

        playbackEvents = state.recentEvents().stream()
            .filter(event -> event.roundNumber() == playbackRound)
            .toList();

        syncOnlineBattlefield(
            roundStartOnlineCombatants(state, multiplayerSetup.playerSide(), onlinePlayer),
            roundStartOnlineCombatants(state, opposite(multiplayerSetup.playerSide()), onlineEnemy));
        applyRoundStartFormSprites(state);
        seedOnlineResourcesFromRoundStart(state);
        boolean tickZeroRoundStart = playbackEvents.stream().allMatch(event -> event.tick() == 0)
            && playbackEvents.stream().anyMatch(event -> event.type() == BattleEventType.ROUND_START);
        if (returnsToPlanning || tickZeroRoundStart) {
            rewindOnlineResourceEvents(playbackEvents);
        }
        initPanels();
        CharacterState displayedPlayer = displayedOnlinePrimary(true);
        onlinePlayerMiracles = roundStartMiraclesState(
            state, multiplayerSetup.playerSide(), onlineKey(
                multiplayerSetup.playerSide(), displayedPlayer),
            displayedPlayer == null ? List.of() : displayedPlayer.codedAbilities());
        onlinePlayerRatio = roundStartRatioState(
            state, multiplayerSetup.playerSide(), onlineKey(
                multiplayerSetup.playerSide(), displayedPlayer),
            displayedPlayer == null ? List.of() : displayedPlayer.codedAbilities());
        boolean pausedForFaint = false;
        if (!typingInProgress() && !faintAnimationInProgress() && !entranceAnimationInProgress()) {
            pausedForFaint = processPlaybackEventsThrough(0);
        }
        updatePanels();
        if (playbackActionTicks.isEmpty() && !pausedForFaint
            && !typingInProgress() && !faintAnimationInProgress() && !entranceAnimationInProgress()) {
            finishMultiplayerPlayback();
        }
    }

    private void updateMultiplayerPlayback(float delta) {
        if (!resolvingTicks || playbackComplete || multiplayerState == null) return;
        if (skipRoundRequested) {
            finishMultiplayerPlayback();
            return;
        }
        // Don't advance (or accumulate) while a log line is still typing, so
        // a tick that just queued messages can't outpace the typewriter.
        if (typingInProgress() || faintAnimationInProgress() || entranceAnimationInProgress()) return;
        // A defeat pauses event consumption mid-tick. Finish the remaining
        // events at that same tick before the timeline advances again.
        if (processPlaybackEventsThrough(currentExecutionTick)) return;
        if (typingInProgress() || faintAnimationInProgress() || entranceAnimationInProgress()) return;
        playbackTickElapsedMs += Math.max(0f, delta) * 1000f;

        while (resolvingTicks) {
            if (playbackTickElapsedMs < TICK_DURATION_MS) return;
            playbackTickElapsedMs -= TICK_DURATION_MS;

            if (playbackActionIndex >= playbackActionTicks.size()) {
                finishMultiplayerPlayback();
                return;
            }
            currentExecutionTick = playbackActionTicks.get(playbackActionIndex++);
            if (processPlaybackEventsThrough(currentExecutionTick)) return;
            // If advancing the tick just queued log lines, hold further
            // advancement until they type out.
            if (typingInProgress() || faintAnimationInProgress() || entranceAnimationInProgress()) return;
        }
    }

    private boolean processPlaybackEventsThrough(int tick) {
        while (playbackEventIndex < playbackEvents.size()
            && playbackEvents.get(playbackEventIndex).tick() <= tick) {
            BattleEventState event = playbackEvents.get(playbackEventIndex++);
            if (applyPlaybackEvent(event)) {
                updatePanels();
                return true;
            }
            if (typingInProgress()) {
                updatePanels();
                return false;
            }
        }
        updatePanels();
        return false;
    }

    private void refreshTerminalPlayback(MatchState state) {
        List<BattleEventState> merged = new ArrayList<>(playbackEvents);
        Set<String> eventIds = new HashSet<>();
        for (BattleEventState event : playbackEvents) {
            if (event.eventId() != null) eventIds.add(event.eventId());
        }
        boolean added = false;
        for (BattleEventState event : state.recentEvents()) {
            boolean unseen = event.eventId() == null
                ? !merged.contains(event) : eventIds.add(event.eventId());
            if (event.roundNumber() == state.roundNumber() && unseen) {
                merged.add(event);
                added = true;
            }
        }
        playbackEvents = List.copyOf(merged);
        // A late force-end event reopens playback. Event consumption remains in
        // updateMultiplayerPlayback so duplicate terminal callbacks cannot race
        // ahead of an active typewriter line or faint animation.
        if (added && playbackComplete) {
            playbackComplete = false;
            resolvingTicks = true;
            awaitingNextRound = false;
            nextRoundHovered = false;
            playbackTickElapsedMs = 0f;
        }
        if (!resolvingTicks && playbackComplete
            && !typingInProgress() && !faintAnimationInProgress() && !entranceAnimationInProgress()) {
            showMultiplayerResult(state);
        }
    }

    private boolean applyPlaybackEvent(BattleEventState event) {
        // Domain banners track playback so the status strip paces with the log
        // instead of snapping to the post-round snapshot.
        applyOnlineDomainEvent(event);
        // The summon joins with an entrance animation; playback holds on it the
        // same way it holds on a faint (flag returned after the log line below
        // has been queued so the join message still types out first). A skipped
        // round adds the combatant without ceremony.
        boolean startedEntrance = false;
        if (event.type() == BattleEventType.COMBATANT_SUMMONED) {
            if (skipRoundRequested) {
                addOnlineCombatantToField(event.targetSide(), onlineCombatantForEvent(
                    event.targetSide(), event.targetInstanceId(), event.targetCharacterId()));
            } else {
                startedEntrance = startOnlineSummonEntrance(event.targetSide(),
                    onlineCombatantForEvent(event.targetSide(),
                        event.targetInstanceId(), event.targetCharacterId()));
            }
        }
        if (event.type() == BattleEventType.COMBATANT_SWITCHED
            || event.type() == BattleEventType.COMBATANT_REPLACED) {
            CharacterState outgoing = onlineVisualForEvent(
                event.sourceSide(), event.sourceInstanceId(), event.sourceCharacterId());
            CharacterState incoming = onlineCombatantForEvent(
                event.targetSide(), event.targetInstanceId(), event.targetCharacterId());
            removeOnlineCombatantImmediately(event.sourceSide(), outgoing);
            if (skipRoundRequested) {
                addOnlineCombatantToField(event.targetSide(), incoming);
            } else {
                startedEntrance = startOnlineSummonEntrance(event.targetSide(), incoming);
            }
        }
        CharacterState target = onlineVisualForEvent(
            event.targetSide(), event.targetInstanceId(), event.targetCharacterId());
        CharacterState source = onlineVisualForEvent(
            event.sourceSide(), event.sourceInstanceId(), event.sourceCharacterId());
        OnlineCombatantKey targetKey = onlineKey(event.targetSide(), target);
        OnlineCombatantKey sourceKey = onlineKey(event.sourceSide(), source);
        CombatantPanel targetPanel = onlinePanelFor(event.targetSide(), target);
        Integer value = event.value();
        OnlineResourceState targetResources = onlineResourceStates.get(targetKey);
        if (targetResources != null && value != null) {
            switch (event.type()) {
                case DAMAGE_DEALT -> {
                    if (value > 0) {
                        targetResources.hp = Math.max(0, targetResources.hp - value);
                        if (targetPanel != null && !skipRoundRequested) targetPanel.flashDamage();
                    }
                }
                case HP_RESTORED -> targetResources.hp += value;
                case MAX_HP_CHANGED -> {
                    targetResources.maxHp = Math.max(1, value);
                    targetResources.hp = Math.min(targetResources.hp, targetResources.maxHp);
                }
                case MAX_CE_CHANGED -> {
                    targetResources.maxCe = Math.max(0, value);
                    targetResources.ce = Math.min(targetResources.ce, targetResources.maxCe);
                }
                case CHARACTER_TRANSFORMED, CHARACTER_REVERTED ->
                    targetResources.hp = Math.max(0, value);
                default -> { }
            }
        }
        if (event.type() == BattleEventType.CHARACTER_TRANSFORMED
            || event.type() == BattleEventType.CHARACTER_REVERTED) {
            refreshOnlineFormSprite(event.targetSide(), target, event.targetCharacterId());
            // Panels capture their texture at build time — relayout so the new
            // form shows on the transform broadcast, then play the shared
            // grow-from-white entrance (a skipped round just swaps).
            layoutExecutionUi(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            if (!skipRoundRequested && target != null) {
                startedEntrance = startOnlinePanelEntrance(event.targetSide(), target);
            }
        }
        if (value != null && (event.type() == BattleEventType.CE_DRAINED
            || event.type() == BattleEventType.CE_RESTORED)) {
            OnlineCombatantKey resourceKey = event.targetSide() != null ? targetKey : sourceKey;
            OnlineResourceState resources = onlineResourceStates.get(resourceKey);
            if (resources != null) {
                resources.ce = event.type() == BattleEventType.CE_DRAINED
                    ? Math.max(0, resources.ce - value)
                    : resources.ce + value;
            }
        }

        boolean displayedPrimarySource = sourceKey != null
            && sourceKey.equals(onlineKey(multiplayerSetup.playerSide(), displayedOnlinePrimary(true)));
        CodedAbilityState codedAbilityState = event.codedAbilityState();
        OnlineCombatantKey abilityOwnerKey = event.type() == BattleEventType.RESOURCE_CHANGED
            && targetKey != null ? targetKey : sourceKey;
        if (codedAbilityState != null && abilityOwnerKey != null) {
            onlineAbilityStates.put(abilityOwnerKey, withAbilityState(
                onlineAbilityStates.getOrDefault(abilityOwnerKey, List.of()),
                codedAbilityState));
        }
        if (displayedPrimarySource && event.sourceSide() == multiplayerSetup.playerSide()
            && codedAbilityState != null
            && MiraclesAbility.KEY.equals(codedAbilityState.key())) {
            onlinePlayerMiracles = codedAbilityState;
        } else if (displayedPrimarySource && event.sourceSide() == multiplayerSetup.playerSide()
            && codedAbilityState != null
            && RatioAbility.KEY.equals(codedAbilityState.key())) {
            onlinePlayerRatio = codedAbilityState;
        }

        Move unleashedMove = event.moveId() == null
            ? null : findOnlineMove(event.sourceSide(), event.moveId());
        if (!skipRoundRequested && event.type() == BattleEventType.MOVE_FIRED
            && unleashedMove != null) {
            playMoveUnleashAnimation(unleashedMove);
        }
        // Per-hit impact flash for multi-hit moves (online path mirrors local).
        if (!skipRoundRequested && unleashedMove != null
            && unleashedMove.getHitComponents().size() > 1
            && (event.type() == BattleEventType.DAMAGE_DEALT
                || event.type() == BattleEventType.MOVE_BLOCKED
                || event.type() == BattleEventType.MOVE_BLOCK_REDUCED
                || event.type() == BattleEventType.MOVE_DODGED
                || event.type() == BattleEventType.MOVE_PARRIED)) {
            if (targetPanel != null) {
                CombatEvent.Type flashType = event.type() == BattleEventType.DAMAGE_DEALT
                    ? CombatEvent.Type.DAMAGE_DEALT : CombatEvent.Type.MOVE_BLOCKED;
                spawnHitFlash(unleashedMove, flashType, targetPanel);
            }
        }
        if (!skipRoundRequested && (event.type() == BattleEventType.MOVE_BLOCKED
            || event.type() == BattleEventType.MOVE_BLOCK_REDUCED)) {
            // Only single-hit moves use the shared center-slot block animation;
            // multi-hit per-hit blocks are rendered as flashes above.
            if (unleashedMove == null
                || unleashedMove.getHitComponents().size() <= 1) {
                playSuccessfulBlockAnimation(targetPanel);
            }
        }
        if (event.eventId() == null || soundedOnlineEventIds.add(event.eventId())) {
            if (!skipRoundRequested) {
                BattleAudioRouter.cueFor(event, unleashedMove).ifPresent(game.audio()::play);
            }
        }
        if (!skipRoundRequested && event.type() == BattleEventType.RATIO_TRIGGERED) {
            playRatioUnleashAnimation();
        }
        if (shouldLog(event)
            && (event.eventId() == null || loggedOnlineEventIds.add(event.eventId()))) {
            queueLogLine(event.message());
        }
        if (event.type() == BattleEventType.COMBATANT_DEFEATED) {
            if (skipRoundRequested) {
                removeOnlineCombatantImmediately(event.targetSide(), target);
                return false;
            }
            return startOnlineFaint(event.targetSide(), target);
        }
        if (event.type() == BattleEventType.COMBATANT_REMOVED) {
            removeOnlineCombatantImmediately(event.targetSide(), target);
        }
        return startedEntrance;
    }

    private void logOnlineEvents(List<BattleEventState> events) {
        for (BattleEventState event : events) {
            if (event.eventId() == null || soundedOnlineEventIds.add(event.eventId())) {
                BattleAudioRouter.cueFor(event, null).ifPresent(game.audio()::play);
            }
            if (shouldLog(event)
                && (event.eventId() == null || loggedOnlineEventIds.add(event.eventId()))) {
                queueLogLine(event.message());
            }
        }
    }

    private Move findOnlineMove(PlayerSide sourceSide, String moveId) {
        PlayerState source = sourceSide == multiplayerSetup.playerSide()
            ? onlinePlayer : onlineEnemy;
        if (source != null) {
            for (CharacterState combatant : source.combatants()) {
                for (MoveState state : combatant.knownMoves()) {
                    if (moveId.equals(state.moveId())) {
                        try {
                            return toDisplayMove(state);
                        } catch (RuntimeException ignored) {
                            return null;
                        }
                    }
                }
            }
        }
        return onlineMoves.get(moveId);
    }

    private void finishMultiplayerPlayback() {
        boolean skipped = skipRoundRequested;
        if (skipped) {
            completeFaintAnimationsImmediately();
            while (playbackEventIndex < playbackEvents.size()) {
                applyPlaybackEvent(playbackEvents.get(playbackEventIndex++));
            }
            flushTypingImmediately();
            clearTransientAnimations();
            updatePanels();
            snapPanelAnimations();
        }
        if (processPlaybackEventsThrough(Integer.MAX_VALUE)
            || typingInProgress() || faintAnimationInProgress() || entranceAnimationInProgress()) {
            return;
        }
        playbackComplete = true;
        resolvingTicks = false;
        if (playbackReturnsToPlanning
            && multiplayerState.phase() == BattlePhase.PLANNING
            && !isTerminal(multiplayerState.status())) {
            playbackReturnsToPlanning = false;
            playbackRound = -1;
            awaitingNextRound = false;
            syncOnlineBattlefield(
                activeOnlineCombatants(onlinePlayer), activeOnlineCombatants(onlineEnemy));
            seedOnlineResourcesFromCurrentState();
            CharacterState displayedPlayer = displayedOnlinePrimary(true);
            onlinePlayerMiracles = displayedPlayer == null
                ? null : findMiraclesState(displayedPlayer.codedAbilities());
            onlinePlayerRatio = displayedPlayer == null
                ? null : findRatioState(displayedPlayer.codedAbilities());
            initPanels();
            updatePanels();
            if (skipped) snapPanelAnimations();
            ensureOnlinePlanner(
                multiplayerState.roundNumber(), onlinePlayer, onlineEnemy);
            if (onlinePlayer.planSubmitted() && teamPlanningPanel != null) {
                teamPlanningPanel.lock();
            }
            resetPlaybackControls();
            return;
        }
        playbackReturnsToPlanning = false;
        seedOnlineResourcesFromCurrentState();
        CharacterState displayedPlayer = displayedOnlinePrimary(true);
        onlinePlayerMiracles = displayedPlayer == null
            ? null : findMiraclesState(displayedPlayer.codedAbilities());
        onlinePlayerRatio = displayedPlayer == null
            ? null : findRatioState(displayedPlayer.codedAbilities());
        updatePanels();
        if (skipped) snapPanelAnimations();

        if (isTerminal(multiplayerState.status())
            || multiplayerState.phase() == BattlePhase.BATTLE_OVER) {
            showMultiplayerResult(multiplayerState);
            return;
        }
        awaitingNextRound = true;
        nextRoundHovered = false;
        resetPlaybackControls();
    }

    private boolean submitReadyNextRound() {
        if (!nextRoundButtonEnabled() || multiplayerMatchService == null) return false;
        MultiplayerMatchService.PlanSubmission submission =
            multiplayerMatchService.readyNextRound();
        if (!submission.sent()) {
            addLogLine(submissionMessage(submission.status()));
            return false;
        }
        onlineCommandPending = true;
        return true;
    }

    private boolean battleStartButtonEnabled() {
        if (!awaitingBattleStart || battleStartWaiting()
            || typingInProgress() || entranceAnimationInProgress()) {
            return false;
        }
        return mode == BattleMode.LOCAL
            || (multiplayerState != null
                && multiplayerState.status() == MatchStatus.ACTIVE
                && multiplayerState.phase() == BattlePhase.PRE_BATTLE
                && multiplayerConnectionState == MultiplayerSession.ConnectionState.CONNECTED);
    }

    private boolean battleStartWaiting() {
        return mode == BattleMode.LOCAL
            ? battleStartConfirmed
            : onlineCommandPending || localReadyForBattle();
    }

    private boolean submitReadyForBattle() {
        if (!battleStartButtonEnabled() || multiplayerMatchService == null) return false;
        MultiplayerMatchService.PlanSubmission submission =
            multiplayerMatchService.readyForBattle();
        if (!submission.sent()) {
            addLogLine(submissionMessage(submission.status()));
            return false;
        }
        onlineCommandPending = true;
        return true;
    }

    private boolean localReadyForBattle() {
        return onlinePlayer != null && onlinePlayer.readyForBattle();
    }

    private boolean localReadyForNextRound() {
        return onlinePlayer != null && onlinePlayer.readyForNextRound();
    }

    private void showMultiplayerResult(MatchState state) {
        boolean firstResult = !battleOver;
        awaitingBattleStart = false;
        awaitingNextRound = false;
        SoundCue resultCue;
        if (state.winnerSide() == null) {
            battleResult = "DRAW!";
            resultCue = SoundCue.BATTLE_DRAW;
        } else if (state.winnerSide() == multiplayerSetup.playerSide()) {
            battleResult = "VICTORY!";
            resultCue = SoundCue.BATTLE_VICTORY;
        } else {
            battleResult = "DEFEAT!";
            resultCue = SoundCue.BATTLE_DEFEAT;
        }
        battleResultReason = state.endReason() == null
            ? "" : state.endReason().replace('_', ' ');
        if (firstResult) game.audio().play(resultCue);
        resetPlaybackControls();
        battleOver = true;
    }

    private void closePlanningPanel() {
        planningPanel = null;
        teamPlanningPanel = null;
        Gdx.input.setInputProcessor(null);
        logScrollInputAttached = false;
    }

    private boolean planningUiEditable() {
        if (planningPanel != null) return !planningPanel.isReadOnly();
        return teamPlanningPanel != null && !teamPlanningPanel.isReadOnly();
    }

    private void updateWindowsActionBounds() {
        if (windowsUnified()) {
            nextRoundBounds.set(windowsActionBounds(speedControlsVisible()));
        }
    }

    private void retainReadOnlyPlanningUi() {
        if (!windowsUnified()) {
            closePlanningPanel();
            return;
        }
        if (planningPanel != null) {
            planningPanel.lock();
            planningPanel.setReadOnly(true);
            planningPanel.setActionButtonShifted(true);
        }
        if (teamPlanningPanel != null) {
            teamPlanningPanel.lock();
            teamPlanningPanel.setReadOnly(true);
            teamPlanningPanel.setActionButtonShifted(true);
        }
        if (teamPlanningPanel != null) {
            Gdx.input.setInputProcessor(teamPlanningPanel.inputProcessor());
        } else if (planningPanel != null) {
            Gdx.input.setInputProcessor(planningPanel.inputProcessor());
        } else {
            Gdx.input.setInputProcessor(null);
        }
        logScrollInputAttached = false;
    }

    private boolean windowsUnified() {
        return uiLayout.storedProfile() == UiProfile.WINDOWS;
    }

    private void applyPhysicalBatchTransform() {
        float width = Math.max(1f, Gdx.graphics.getWidth());
        float height = Math.max(1f, Gdx.graphics.getHeight());
        batch.setProjectionMatrix(batchProjection.setToOrtho2D(0f, 0f, width, height));
        batch.setTransformMatrix(batchTransform.idt());
    }

    private void applyUnifiedBatchTransform(WindowsBattleCanvas.Anchor anchor) {
        float width = Math.max(1f, Gdx.graphics.getWidth());
        float height = Math.max(1f, Gdx.graphics.getHeight());
        batch.setProjectionMatrix(batchProjection.setToOrtho2D(0f, 0f, width, height));
        batch.setTransformMatrix(batchTransform.idt()
            .translate(windowsCanvas.offsetX(), windowsCanvas.offsetY(anchor), 0f)
            .scale(windowsCanvas.scale(), windowsCanvas.scale(), 1f));
    }

    private void beginUnifiedClip(Rectangle logicalBounds, WindowsBattleCanvas.Anchor anchor) {
        Rectangle physical = windowsCanvas.physicalBounds(logicalBounds, anchor);
        float backBufferScaleX = Gdx.graphics.getBackBufferWidth()
            / (float) Gdx.graphics.getWidth();
        float backBufferScaleY = Gdx.graphics.getBackBufferHeight()
            / (float) Gdx.graphics.getHeight();
        batch.flush();
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(
            Math.round(physical.x * backBufferScaleX),
            Math.round(physical.y * backBufferScaleY),
            Math.round(physical.width * backBufferScaleX),
            Math.round(physical.height * backBufferScaleY));
    }

    private void endUnifiedClip() {
        batch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private float bottomInputX(float physicalX) {
        return windowsUnified() ? windowsCanvas.logicalX(physicalX) : physicalX;
    }

    private float bottomInputY(float physicalTopY) {
        float physicalBottomY = Gdx.graphics.getHeight() - physicalTopY;
        return windowsUnified()
            ? windowsCanvas.logicalY(physicalBottomY, WindowsBattleCanvas.Anchor.BOTTOM)
            : physicalBottomY;
    }

    private float topInputY(float physicalTopY) {
        float physicalBottomY = Gdx.graphics.getHeight() - physicalTopY;
        return windowsCanvas.logicalY(physicalBottomY, WindowsBattleCanvas.Anchor.TOP);
    }

    private void configurePlanningViewport() {
        if (!windowsUnified()) return;
        float offsetY = windowsCanvas.offsetY(WindowsBattleCanvas.Anchor.BOTTOM);
        if (planningPanel != null) {
            planningPanel.setViewportTransform(
                windowsCanvas.scale(), windowsCanvas.offsetX(), offsetY,
                windowsCanvas.viewportHeight());
        }
        if (teamPlanningPanel != null) {
            teamPlanningPanel.setViewportTransform(
                windowsCanvas.scale(), windowsCanvas.offsetX(), offsetY,
                windowsCanvas.viewportHeight());
        }
    }

    private void showLocalPreBattlePlanner(BattleState state) {
        if (!windowsUnified()) return;
        List<BattleCombatant> controlled = visibleCombatants(state.playerTeam());
        if (controlled.isEmpty()) return;
        planningPanel = null;
        teamPlanningPanel = new TeamPlanningPanel(
            TeamBattlePlan.gridLengthForRound(state),
            controlled,
            state,
            assets.battleUi,
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight());
        teamPlanningPanel.setLayout(uiLayout);
        teamPlanningPanel.lock();
        teamPlanningPanel.setReadOnly(true);
        teamPlanningPanel.setActionButtonShifted(false);
        configurePlanningViewport();
        Gdx.input.setInputProcessor(null);
    }

    private CombatantPanel activePlannerCombatantPanel() {
        if (!planningUiEditable()) return null;
        int teamPageCount = teamPlanningPanel == null ? 0 : teamPlanningPanel.pageCount();
        if (!shouldDrawPlanningHighlight(planningPanel != null, teamPageCount)) return null;
        String actorId = teamPlanningPanel.activeActorId();
        if (actorId == null || actorId.isBlank()) return null;

        if (mode == BattleMode.MULTIPLAYER) {
            for (int i = 0; i < Math.min(renderOnlinePlayerTeam.size(), playerPanels.size()); i++) {
                if (actorId.equals(renderOnlinePlayerTeam.get(i).instanceId())) {
                    return playerPanels.get(i);
                }
            }
            for (int i = 0; i < Math.min(renderOnlineEnemyTeam.size(), enemyPanels.size()); i++) {
                if (actorId.equals(renderOnlineEnemyTeam.get(i).instanceId())) {
                    return enemyPanels.get(i);
                }
            }
            return null;
        }

        for (int i = 0; i < Math.min(renderPlayerTeam.size(), playerPanels.size()); i++) {
            CombatantId id = renderPlayerTeam.get(i).getInstanceId();
            if (id != null && actorId.equals(id.value())) return playerPanels.get(i);
        }
        for (int i = 0; i < Math.min(renderEnemyTeam.size(), enemyPanels.size()); i++) {
            CombatantId id = renderEnemyTeam.get(i).getInstanceId();
            if (id != null && actorId.equals(id.value())) return enemyPanels.get(i);
        }
        return null;
    }

    static boolean shouldDrawPlanningHighlight(boolean hasSinglePlanner, int teamPageCount) {
        return !hasSinglePlanner && teamPageCount > 1;
    }

    /** Retain the locked Windows planner beneath execution controls. */
    private void showExecutionUi() {
        retainReadOnlyPlanningUi();
        awaitingBattleStart = false;
        awaitingNextRound = false;
        nextRoundHovered = false;
        executionUiActive = true;
    }

    private void leaveMultiplayer() {
        closePlanningPanel();
        detachMultiplayerListener();
        multiplayerRun++;
        if (multiplayerMatchService != null && !preserveMultiplayerSession) {
            multiplayerMatchService.disconnect();
        }
        game.showMultiplayerMenu();
    }

    private void detachMultiplayerListener() {
        if (multiplayerListener != null && multiplayerMatchService != null) {
            multiplayerMatchService.removeListener(multiplayerListener);
        }
        multiplayerListener = null;
    }

    private void postMultiplayer(long run, Runnable callback) {
        Gdx.app.postRunnable(() -> {
            if (mode == BattleMode.MULTIPLAYER
                && multiplayerRun == run
                && game.getScreen() == this) {
                callback.run();
            }
        });
    }

    private static List<ActionSegmentState> onlineSegments(MatchState state) {
        List<ActionSegmentState> segments = new ArrayList<>();
        for (PlayerState player : state.players()) {
            for (CharacterState combatant : player.combatants()) {
                if (combatant.plan() == null) continue;
                segments.addAll(combatant.plan().queuedSegments());
                segments.addAll(combatant.plan().resolvedSegments());
            }
        }
        return segments;
    }

    private static List<Integer> onlineActionTicks(MatchState state) {
        return actionTicks(onlineSegments(state));
    }

    static List<Integer> actionTicks(Iterable<ActionSegmentState> segments) {
        Set<Integer> ticks = new TreeSet<>();
        for (ActionSegmentState segment : segments) {
            if (segment == null || segment.status() == ActionSegmentStatus.STUNNED) continue;
            int playbackEnd = Math.max(segment.endTick(),
                segment.resolvedTick() == null ? segment.endTick() : segment.resolvedTick());
            for (int tick = Math.max(1, segment.startTick()); tick <= playbackEnd; tick++) {
                ticks.add(tick);
            }
        }
        return List.copyOf(ticks);
    }

    private static PlayerSide opposite(PlayerSide side) {
        return side == PlayerSide.PLAYER_ONE ? PlayerSide.PLAYER_TWO : PlayerSide.PLAYER_ONE;
    }

    static String onlineTeamName(PlayerState player) {
        if (player == null) return "an unknown team";
        List<String> names = player.combatants().stream()
            .filter(combatant -> "FIGHTER".equalsIgnoreCase(combatant.role()))
            .map(CharacterState::name)
            .toList();
        if (names.isEmpty()) {
            names = player.combatants().stream().map(CharacterState::name).toList();
        }
        if (names.isEmpty()) return player.displayName();
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1))
            + " and " + names.get(names.size() - 1);
    }

    /**
     * Battle-wide timeline grid length for the current online round, derived
     * from the stronger fighter's AP tier. Matches the server's authoritative
     * length (both players' {@code maxAp} are in the MatchState and are never
     * concealed). Falls back to the local combatant's tier if the opponent's
     * state isn't present yet.
     */
    private int onlineBattleGridLength() {
        int strongestAp = 0;
        if (multiplayerState != null) {
            for (PlayerState player : multiplayerState.players()) {
                for (CharacterState combatant : player.combatants()) {
                    if (isActiveCombatant(combatant)) {
                        strongestAp = Math.max(strongestAp, combatant.maxAp());
                    }
                }
            }
        }
        return com.jjktbf.model.combat.Timeline.gridLengthForStrongestAp(strongestAp);
    }

    private static boolean isTerminal(MatchStatus status) {
        return status == MatchStatus.ENDED || status == MatchStatus.ABANDONED;
    }

    private static String submissionMessage(MultiplayerMatchService.SubmissionStatus status) {
        return switch (status) {
            case NO_MATCH -> "No active match is available.";
            case NOT_CONNECTED -> "The match is not connected yet.";
            case ALREADY_PENDING -> "A command is already waiting for the server.";
            case MATCH_ENDED -> "The match has already ended.";
            case SERVICE_CLOSED -> "The multiplayer service is closed.";
            case SENT -> "Command sent.";
        };
    }

    private static String safeMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null
            && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
            ? "The multiplayer service is unavailable." : message;
    }

    private final class MultiplayerBattleListener implements MultiplayerMatchService.Listener {
        private final long run;
        private final String matchId;

        private MultiplayerBattleListener(long run, String matchId) {
            this.run = run;
            this.matchId = matchId;
        }

        @Override
        public void onConnectionStateChanged(MultiplayerSession.ConnectionState state) {
            postMultiplayer(run, () -> {
                multiplayerConnectionState = state;
                if (state == MultiplayerSession.ConnectionState.CONNECTED) {
                    addLogLine(multiplayerState != null
                        && multiplayerState.phase() == BattlePhase.PRE_BATTLE
                        ? "Connected. Both players must start the battle."
                        : "Connected. Both players can now plan their round.");
                    if (multiplayerState != null && onlinePlayer != null
                        && multiplayerState.phase() == BattlePhase.PLANNING
                        && !onlinePlayer.planSubmitted()) {
                        ensureOnlinePlanner(
                            multiplayerState.roundNumber(), onlinePlayer, onlineEnemy);
                    }
                }
            });
        }

        @Override
        public void onReconnecting(int attempt, Duration delay) {
            postMultiplayer(run, () -> {
                onlineCommandPending = false;
                addLogLine("Connection interrupted. Retrying in "
                    + delay.toSeconds() + " second(s).");
            });
        }

        @Override
        public void onDisconnected(MatchWebSocketClient.DisconnectReason reason) {
            postMultiplayer(run, () -> {
                multiplayerConnectionState = MultiplayerSession.ConnectionState.DISCONNECTED;
                onlineCommandPending = false;
                if (reason == MatchWebSocketClient.DisconnectReason.RETRIES_EXHAUSTED
                    && (multiplayerState == null || !isTerminal(multiplayerState.status()))) {
                    preserveMultiplayerSession = true;
                    game.showMultiplayerDisconnected("Reconnect attempts were exhausted.");
                }
            });
        }

        @Override
        public void onMatchState(MatchState state) {
            if (matchId.equals(state.matchId())) {
                postMultiplayer(run, () -> applyMultiplayerState(state));
            }
        }

        @Override
        public void onPlayerConnectionChanged(SocketMessage message) {
            postMultiplayer(run, () -> {
                if (message.playerSide() != multiplayerSetup.playerSide()) {
                    addLogLine(message.type().name().contains("DISCONNECTED")
                        ? "Opponent disconnected. Waiting for their return."
                        : "Opponent connected.");
                }
            });
        }

        @Override
        public void onCommandCompleted(MultiplayerMatchService.CommandOutcome outcome) {
            postMultiplayer(run, () -> {
                onlineCommandPending = false;
                if (!outcome.accepted()) unlockPlannerIfPlanOpen();
            });
        }

        @Override
        public void onCommandRejected(String commandId, ErrorResponse error) {
            postMultiplayer(run, () -> {
                onlineCommandPending = false;
                unlockPlannerIfPlanOpen();
                addLogLine(error == null
                    ? "The server rejected the command."
                    : error.message());
            });
        }

        @Override
        public void onMatchEnded(MatchState state) {
            if (matchId.equals(state.matchId())) {
                postMultiplayer(run, () -> applyMultiplayerState(state));
            }
        }

        @Override
        public void onError(String code, String userMessage, Throwable cause) {
            postMultiplayer(run, () -> addLogLine("[" + code + "] " + userMessage));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void removeLocalCombatantFromField(BattleCombatant defeated) {
        CombatantPanel oldPanel = panelForCombatant(defeated);
        List<BattleCombatant> players = new ArrayList<>(renderPlayerTeam);
        List<BattleCombatant> enemies = new ArrayList<>(renderEnemyTeam);
        boolean removed = players.removeIf(combatant -> combatant == defeated)
            | enemies.removeIf(combatant -> combatant == defeated);
        if (!removed) return;
        if (renderLocalState != null) {
            backfillLocalVisualRoster(players, renderLocalState.playerTeam());
            backfillLocalVisualRoster(enemies, renderLocalState.enemyTeam());
        }

        renderPlayerTeam = List.copyOf(players);
        renderEnemyTeam = List.copyOf(enemies);
        renderPlayer = players.isEmpty() ? null : players.get(0);
        renderEnemy = enemies.isEmpty() ? null : enemies.get(0);
        playerTeamSprites = battleSprites(players, false);
        enemyTeamSprites = battleSprites(enemies, true);
        if (!playerTeamSprites.isEmpty()) playerSprite = playerTeamSprites.get(0);
        if (!enemyTeamSprites.isEmpty()) enemySprite = enemyTeamSprites.get(0);
        localHpStates.remove(defeated);
        localAbilityStates.remove(defeated);
        for (BattleCombatant combatant : players) {
            localHpStates.putIfAbsent(combatant,
                new LocalHpState(combatant.getCurrentHp(), combatant.getMaxHp()));
        }
        for (BattleCombatant combatant : enemies) {
            localHpStates.putIfAbsent(combatant,
                new LocalHpState(combatant.getCurrentHp(), combatant.getMaxHp()));
        }
        clearTransientPanelReferences(oldPanel);
        layoutExecutionUi(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    static void backfillLocalVisualRoster(
        List<BattleCombatant> displayed,
        BattleTeam team
    ) {
        for (BattleCombatant candidate : team.active()) {
            if (displayed.size() == MAX_VISIBLE_COMBATANTS_PER_SIDE) return;
            if (displayed.stream().noneMatch(current -> current == candidate)) {
                displayed.add(candidate);
            }
        }
    }

    /**
     * Add a just-summoned combatant to the local battlefield the moment its
     * join broadcast plays (render thread). Mirrors the removal path: appends
     * to the render roster if a slot is free, rebuilds the side's sprites, and
     * relayouts so a panel (and HUD) exist for it immediately.
     */
    private boolean addLocalCombatantToField(BattleCombatant summon, boolean playerSide) {
        if (summon == null) return false;
        List<BattleCombatant> roster = new ArrayList<>(playerSide
            ? renderPlayerTeam : renderEnemyTeam);
        if (roster.size() >= MAX_VISIBLE_COMBATANTS_PER_SIDE
            || roster.stream().anyMatch(current -> current == summon)) {
            return false;
        }
        roster.add(summon);
        if (playerSide) {
            renderPlayerTeam = List.copyOf(roster);
            playerTeamSprites = battleSprites(renderPlayerTeam, false);
            if (!playerTeamSprites.isEmpty()) playerSprite = playerTeamSprites.get(0);
        } else {
            renderEnemyTeam = List.copyOf(roster);
            enemyTeamSprites = battleSprites(renderEnemyTeam, true);
            if (!enemyTeamSprites.isEmpty()) enemySprite = enemyTeamSprites.get(0);
        }
        localHpStates.putIfAbsent(summon,
            new LocalHpState(summon.getCurrentHp(), summon.getMaxHp()));
        localAbilityStates.putIfAbsent(summon, summon.abilityStates());
        layoutExecutionUi(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        return true;
    }

    /** Adds the summon's panel and starts its entrance animation (render thread). */
    private boolean startLocalSummonEntrance(BattleCombatant summon, boolean playerSide) {
        addLocalCombatantToField(summon, playerSide);
        return startLocalPanelEntrance(summon, playerSide);
    }

    /** Starts the shared grow-in entrance on an already-displayed combatant. */
    private boolean startLocalPanelEntrance(BattleCombatant combatant, boolean playerSide) {
        return startLocalPanelEntrance(combatant, playerSide, false);
    }

    private boolean startLocalPanelEntrance(
        BattleCombatant combatant,
        boolean playerSide,
        boolean animateHud
    ) {
        CombatantPanel panel = panelForCombatant(combatant);
        if (panel == null) return false;
        Texture whiteSprite = assets.whiteSilhouette(panel.spriteTexture());
        return startEntranceAnimation(animateHud
            ? EntranceAnimation.initialLocal(
                combatant, playerSide, panel, whiteSprite)
            : EntranceAnimation.local(
                combatant, playerSide, panel, whiteSprite));
    }

    private void syncOnlineBattlefield(
        List<CharacterState> players,
        List<CharacterState> enemies
    ) {
        renderOnlinePlayerTeam = players.stream()
            .limit(MAX_VISIBLE_COMBATANTS_PER_SIDE).toList();
        renderOnlineEnemyTeam = enemies.stream()
            .limit(MAX_VISIBLE_COMBATANTS_PER_SIDE).toList();
        playerTeamSprites = onlineBattleSprites(renderOnlinePlayerTeam, false);
        enemyTeamSprites = onlineBattleSprites(renderOnlineEnemyTeam, true);
        if (!playerTeamSprites.isEmpty()) playerSprite = playerTeamSprites.get(0);
        if (!enemyTeamSprites.isEmpty()) enemySprite = enemyTeamSprites.get(0);
    }

    private List<Texture> onlineBattleSprites(List<CharacterState> combatants, boolean opponent) {
        Texture fallback = opponent ? assets.enemySprite : assets.playerSprite;
        return combatants.stream()
            .map(combatant -> assets.characterBattleSprite(
                game.multiplayerSpriteAsset(combatant.characterId()), opponent, fallback))
            .toList();
    }

    private void applyRoundStartFormSprites(MatchState state) {
        if (state == null) return;
        for (RoundStartCharacterState start : state.roundStartCharacterStates()) {
            CharacterState combatant = onlineVisualForEvent(
                start.side(), start.instanceId(), start.characterId());
            refreshOnlineFormSprite(start.side(), combatant, start.characterId());
        }
    }

    private void refreshOnlineFormSprite(
        PlayerSide side,
        CharacterState combatant,
        String characterId
    ) {
        if (side == null || combatant == null || characterId == null || characterId.isBlank()) {
            return;
        }
        boolean playerSide = side == multiplayerSetup.playerSide();
        List<CharacterState> combatants = playerSide
            ? renderOnlinePlayerTeam : renderOnlineEnemyTeam;
        int index = -1;
        OnlineCombatantKey key = onlineKey(side, combatant);
        for (int i = 0; i < combatants.size(); i++) {
            if (key.equals(onlineKey(side, combatants.get(i)))) {
                index = i;
                break;
            }
        }
        if (index < 0) return;
        List<Texture> sprites = new ArrayList<>(playerSide
            ? playerTeamSprites : enemyTeamSprites);
        if (index >= sprites.size()) return;
        Texture fallback = playerSide ? assets.playerSprite : assets.enemySprite;
        sprites.set(index, assets.characterBattleSprite(
            game.multiplayerSpriteAsset(characterId), !playerSide, fallback));
        if (playerSide) {
            playerTeamSprites = List.copyOf(sprites);
            if (!sprites.isEmpty()) playerSprite = sprites.get(0);
        } else {
            enemyTeamSprites = List.copyOf(sprites);
            if (!sprites.isEmpty()) enemySprite = sprites.get(0);
        }
    }

    static List<CharacterState> roundStartOnlineCombatants(
        MatchState state,
        PlayerSide side,
        PlayerState player
    ) {
        if (player == null) return List.of();
        Set<String> summonedThisRound = eventTargetIds(
            state, side, BattleEventType.COMBATANT_SUMMONED);
        Set<String> removedThisRound = eventTargetIds(
            state, side, BattleEventType.COMBATANT_REMOVED);
        List<CharacterState> combatants = player.combatants().stream()
            .filter(combatant -> !summonedThisRound.contains(combatant.instanceId()))
            .filter(combatant -> !"REMOVED".equals(combatant.lifecycle())
                || removedThisRound.contains(combatant.instanceId()))
            .toList();
        List<RoundStartCharacterState> starts = state.roundStartCharacterStates().stream()
            .filter(start -> start.side() == side && start.currentHp() > 0)
            .toList();
        Set<String> startIds = starts.stream()
            .map(RoundStartCharacterState::instanceId)
            .filter(id -> id != null && !id.isBlank())
            .collect(java.util.stream.Collectors.toSet());
        state.recentEvents().stream()
            .filter(event -> event.roundNumber() == state.roundNumber()
                && event.type() == BattleEventType.COMBATANT_DEFEATED
                && event.targetSide() == side)
            .map(BattleEventState::targetInstanceId)
            .filter(id -> id != null && !id.isBlank())
            .forEach(startIds::add);
        if (!startIds.isEmpty()) {
            return combatants.stream()
                .filter(combatant -> startIds.contains(combatant.instanceId()))
                .limit(MAX_VISIBLE_COMBATANTS_PER_SIDE)
                .toList();
        }
        if (!starts.isEmpty()) {
            return combatants.stream()
                .limit(Math.min(starts.size(), MAX_VISIBLE_COMBATANTS_PER_SIDE)).toList();
        }

        List<CharacterState> active = combatants.stream()
            .filter(BattleScreen::isActiveCombatant)
            .limit(MAX_VISIBLE_COMBATANTS_PER_SIDE)
            .toList();
        // Protocol-v8 has one fighter and may omit round-start instance ids.
        return active.isEmpty() && combatants.size() == 1 ? combatants : active;
    }

    private static Set<String> eventTargetIds(
        MatchState state,
        PlayerSide side,
        BattleEventType type
    ) {
        return state.recentEvents().stream()
            .filter(event -> event.roundNumber() == state.roundNumber()
                && event.type() == type && event.targetSide() == side)
            .map(BattleEventState::targetInstanceId)
            .filter(id -> id != null && !id.isBlank())
            .collect(java.util.stream.Collectors.toSet());
    }

    private void seedOnlineResourcesFromCurrentState() {
        onlineResourceStates.clear();
        onlineAbilityStates.clear();
        seedOnlineResourcesFromCurrentState(
            multiplayerSetup.playerSide(), renderOnlinePlayerTeam);
        seedOnlineResourcesFromCurrentState(
            opposite(multiplayerSetup.playerSide()), renderOnlineEnemyTeam);
    }

    private void seedOnlineResourcesFromCurrentState(
        PlayerSide side,
        List<CharacterState> combatants
    ) {
        for (CharacterState combatant : combatants) {
            OnlineCombatantKey key = onlineKey(side, combatant);
            onlineResourceStates.put(key,
                OnlineResourceState.from(combatant));
            onlineAbilityStates.put(key, combatant.codedAbilities());
        }
    }

    private void seedOnlineResourcesFromRoundStart(MatchState state) {
        onlineResourceStates.clear();
        onlineAbilityStates.clear();
        seedOnlineResourcesFromRoundStart(
            state, multiplayerSetup.playerSide(), renderOnlinePlayerTeam);
        seedOnlineResourcesFromRoundStart(
            state, opposite(multiplayerSetup.playerSide()), renderOnlineEnemyTeam);
    }

    private void seedOnlineResourcesFromRoundStart(
        MatchState state,
        PlayerSide side,
        List<CharacterState> combatants
    ) {
        List<RoundStartCharacterState> sideStarts = state.roundStartCharacterStates().stream()
            .filter(start -> start.side() == side && start.currentHp() > 0)
            .toList();
        for (int i = 0; i < combatants.size(); i++) {
            CharacterState combatant = combatants.get(i);
            RoundStartCharacterState start = sideStarts.stream()
                .filter(candidate -> candidate.instanceId() != null
                    && candidate.instanceId().equals(combatant.instanceId()))
                .findFirst()
                .orElse(i < sideStarts.size() ? sideStarts.get(i) : null);
            OnlineResourceState resources = start == null
                ? OnlineResourceState.from(combatant)
                : new OnlineResourceState(
                    start.currentHp(), start.maxHp(), start.currentCe(), start.maxCe());
            onlineResourceStates.put(onlineKey(side, combatant), resources);
            onlineAbilityStates.put(onlineKey(side, combatant), start == null
                ? combatant.codedAbilities() : start.codedAbilities());
        }
    }

    private static List<CodedAbilityState> withAbilityState(
        List<CodedAbilityState> states,
        CodedAbilityState changed
    ) {
        List<CodedAbilityState> updated = new ArrayList<>(states == null ? List.of() : states);
        for (int index = 0; index < updated.size(); index++) {
            if (updated.get(index).key().equalsIgnoreCase(changed.key())) {
                updated.set(index, changed);
                return List.copyOf(updated);
            }
        }
        updated.add(changed);
        return List.copyOf(updated);
    }

    /** Restores pre-event resources when the server's tick-zero snapshot is post-resolution. */
    private void rewindOnlineResourceEvents(List<BattleEventState> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            BattleEventState event = events.get(i);
            Integer amount = event.value();
            if (amount == null) continue;
            CharacterState target = onlineVisualForEvent(
                event.targetSide(), event.targetInstanceId(), event.targetCharacterId());
            CharacterState source = onlineVisualForEvent(
                event.sourceSide(), event.sourceInstanceId(), event.sourceCharacterId());
            OnlineCombatantKey key = event.targetSide() != null
                ? onlineKey(event.targetSide(), target)
                : onlineKey(event.sourceSide(), source);
            OnlineResourceState resources = onlineResourceStates.get(key);
            if (resources == null) continue;
            switch (event.type()) {
                case DAMAGE_DEALT -> resources.hp = Math.min(
                    resources.maxHp, resources.hp + amount);
                case HP_RESTORED -> resources.hp = Math.max(0, resources.hp - amount);
                case CE_DRAINED -> resources.ce = Math.min(
                    resources.maxCe, resources.ce + amount);
                case CE_RESTORED -> resources.ce = Math.max(0, resources.ce - amount);
                default -> { }
            }
        }
    }

    private CharacterState onlineVisualForEvent(
        PlayerSide side,
        String instanceId,
        String characterId
    ) {
        if (side == null || multiplayerSetup == null) return null;
        List<CharacterState> combatants = side == multiplayerSetup.playerSide()
            ? renderOnlinePlayerTeam : renderOnlineEnemyTeam;
        return findOnlineCombatant(combatants, instanceId, characterId);
    }

    private CharacterState onlineCombatantForEvent(
        PlayerSide side,
        String instanceId,
        String characterId
    ) {
        if (side == null || multiplayerSetup == null) return null;
        PlayerState player = side == multiplayerSetup.playerSide() ? onlinePlayer : onlineEnemy;
        return player == null
            ? null : findOnlineCombatant(player.combatants(), instanceId, characterId);
    }

    private static CharacterState findOnlineCombatant(
        List<CharacterState> combatants,
        String instanceId,
        String characterId
    ) {
        if (instanceId != null && !instanceId.isBlank()) {
            for (CharacterState combatant : combatants) {
                if (instanceId.equals(combatant.instanceId())) return combatant;
            }
            return null;
        }
        if (characterId != null && !characterId.isBlank()) {
            for (CharacterState combatant : combatants) {
                if (characterId.equals(combatant.characterId())) return combatant;
            }
            return null;
        }
        return combatants.size() == 1 ? combatants.get(0) : null;
    }

    private boolean addOnlineCombatantToField(PlayerSide side, CharacterState combatant) {
        if (side == null || combatant == null) return false;
        boolean playerSide = side == multiplayerSetup.playerSide();
        List<CharacterState> current = playerSide
            ? renderOnlinePlayerTeam : renderOnlineEnemyTeam;
        List<CharacterState> updated = withOnlineCombatantVisible(current, combatant);
        if (updated.equals(current)) return false;

        onlineResourceStates.put(onlineKey(side, combatant),
            OnlineResourceState.full(combatant));
        onlineAbilityStates.put(onlineKey(side, combatant), combatant.codedAbilities());
        if (playerSide) {
            syncOnlineBattlefield(updated, renderOnlineEnemyTeam);
        } else {
            syncOnlineBattlefield(renderOnlinePlayerTeam, updated);
        }
        layoutExecutionUi(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        return true;
    }

    /** Adds the summon's panel and starts its entrance animation (render thread). */
    private boolean startOnlineSummonEntrance(PlayerSide side, CharacterState combatant) {
        if (!addOnlineCombatantToField(side, combatant)) return false;
        return startOnlinePanelEntrance(side, combatant);
    }

    /** Starts the shared grow-in entrance on an already-displayed combatant. */
    private boolean startOnlinePanelEntrance(PlayerSide side, CharacterState combatant) {
        return startOnlinePanelEntrance(side, combatant, false);
    }

    private boolean startOnlinePanelEntrance(
        PlayerSide side,
        CharacterState combatant,
        boolean animateHud
    ) {
        CombatantPanel panel = onlinePanelFor(side, combatant);
        if (panel == null) return false;
        boolean playerSide = side == multiplayerSetup.playerSide();
        Texture whiteSprite = assets.whiteSilhouette(panel.spriteTexture());
        return startEntranceAnimation(animateHud
            ? EntranceAnimation.initialOnline(
                onlineKey(side, combatant), playerSide, panel, whiteSprite)
            : EntranceAnimation.online(
                onlineKey(side, combatant), playerSide, panel, whiteSprite));
    }

    static List<CharacterState> withOnlineCombatantVisible(
        List<CharacterState> displayed,
        CharacterState combatant
    ) {
        if (combatant == null || displayed.size() >= MAX_VISIBLE_COMBATANTS_PER_SIDE) {
            return displayed;
        }
        String identity = onlineIdentity(combatant);
        if (displayed.stream().anyMatch(current ->
            identity.equals(onlineIdentity(current)))) {
            return displayed;
        }
        List<CharacterState> updated = new ArrayList<>(displayed);
        updated.add(combatant);
        return List.copyOf(updated);
    }

    private CombatantPanel onlinePanelFor(PlayerSide side, CharacterState combatant) {
        if (side == null || combatant == null) return null;
        boolean playerSide = side == multiplayerSetup.playerSide();
        List<CharacterState> combatants = playerSide
            ? renderOnlinePlayerTeam : renderOnlineEnemyTeam;
        List<CombatantPanel> panels = playerSide ? playerPanels : enemyPanels;
        OnlineCombatantKey key = onlineKey(side, combatant);
        for (int i = 0; i < Math.min(combatants.size(), panels.size()); i++) {
            if (key.equals(onlineKey(side, combatants.get(i)))) return panels.get(i);
        }
        return null;
    }

    private CharacterState displayedOnlinePrimary(boolean playerSide) {
        List<CharacterState> combatants = playerSide
            ? renderOnlinePlayerTeam : renderOnlineEnemyTeam;
        return combatants.isEmpty() ? null : combatants.get(0);
    }

    private boolean startOnlineFaint(PlayerSide side, CharacterState defeated) {
        if (side == null || defeated == null) return false;
        boolean playerSide = side == multiplayerSetup.playerSide();
        OnlineCombatantKey key = onlineKey(side, defeated);
        CombatantPanel panel = onlinePanelFor(side, defeated);
        return startFaintAnimation(FaintAnimation.online(
            key, playerSide, panel, () -> removeOnlineCombatantFromField(key)));
    }

    private void removeOnlineCombatantImmediately(PlayerSide side, CharacterState combatant) {
        OnlineCombatantKey key = onlineKey(side, combatant);
        if (key != null) removeOnlineCombatantFromField(key);
    }

    private void removeOnlineCombatantFromField(OnlineCombatantKey defeated) {
        boolean playerSide = defeated.side() == multiplayerSetup.playerSide();
        List<CharacterState> current = new ArrayList<>(playerSide
            ? renderOnlinePlayerTeam : renderOnlineEnemyTeam);
        List<CombatantPanel> currentPanels = playerSide ? playerPanels : enemyPanels;
        CombatantPanel oldPanel = null;
        boolean removed = false;
        for (int i = 0; i < current.size(); i++) {
            if (defeated.equals(onlineKey(defeated.side(), current.get(i)))) {
                if (i < currentPanels.size()) oldPanel = currentPanels.get(i);
                current.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) return;
        if (playerSide) {
            syncOnlineBattlefield(current, renderOnlineEnemyTeam);
        } else {
            syncOnlineBattlefield(renderOnlinePlayerTeam, current);
        }
        onlineResourceStates.remove(defeated);
        onlineAbilityStates.remove(defeated);
        clearTransientPanelReferences(oldPanel);
        if (playerSide) {
            CharacterState primary = displayedOnlinePrimary(true);
            onlinePlayerMiracles = primary == null
                ? null : findMiraclesState(primary.codedAbilities());
            onlinePlayerRatio = primary == null
                ? null : findRatioState(primary.codedAbilities());
        }
        layoutExecutionUi(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private static OnlineCombatantKey onlineKey(PlayerSide side, CharacterState combatant) {
        if (side == null || combatant == null) return null;
        return new OnlineCombatantKey(side, onlineIdentity(combatant));
    }

    private static String onlineIdentity(CharacterState combatant) {
        String identity = combatant.instanceId();
        if (identity == null || identity.isBlank()) {
            identity = combatant.characterId() + "#" + combatant.rosterOrder();
        }
        return identity;
    }

    private void remapFaintAnimationPanels() {
        for (FaintAnimation faint : faintAnimations) {
            if (faint.localCombatant != null) {
                List<BattleCombatant> combatants = faint.playerSide
                    ? renderPlayerTeam : renderEnemyTeam;
                List<CombatantPanel> panels = faint.playerSide ? playerPanels : enemyPanels;
                faint.panel = null;
                for (int i = 0; i < Math.min(combatants.size(), panels.size()); i++) {
                    if (combatants.get(i) == faint.localCombatant) {
                        faint.panel = panels.get(i);
                        faint.panel.prepareFaint();
                        break;
                    }
                }
            } else {
                List<CharacterState> combatants = faint.playerSide
                    ? renderOnlinePlayerTeam : renderOnlineEnemyTeam;
                List<CombatantPanel> panels = faint.playerSide ? playerPanels : enemyPanels;
                faint.panel = null;
                for (int i = 0; i < Math.min(combatants.size(), panels.size()); i++) {
                    if (faint.onlineCombatant.equals(onlineKey(
                        faint.onlineCombatant.side(), combatants.get(i)))) {
                        faint.panel = panels.get(i);
                        faint.panel.prepareFaint();
                        break;
                    }
                }
            }
        }
    }

    /** Rebind in-flight summon entrances to the rebuilt panels after a relayout. */
    private void remapEntranceAnimationPanels() {
        for (EntranceAnimation entrance : entranceAnimations) {
            if (entrance.localCombatant != null) {
                List<BattleCombatant> combatants = entrance.playerSide
                    ? renderPlayerTeam : renderEnemyTeam;
                List<CombatantPanel> panels = entrance.playerSide ? playerPanels : enemyPanels;
                entrance.panel = null;
                for (int i = 0; i < Math.min(combatants.size(), panels.size()); i++) {
                    if (combatants.get(i) == entrance.localCombatant) {
                        entrance.panel = panels.get(i);
                        break;
                    }
                }
            } else {
                List<CharacterState> combatants = entrance.playerSide
                    ? renderOnlinePlayerTeam : renderOnlineEnemyTeam;
                List<CombatantPanel> panels = entrance.playerSide ? playerPanels : enemyPanels;
                entrance.panel = null;
                for (int i = 0; i < Math.min(combatants.size(), panels.size()); i++) {
                    if (entrance.onlineCombatant.equals(onlineKey(
                        entrance.onlineCombatant.side(), combatants.get(i)))) {
                        entrance.panel = panels.get(i);
                        break;
                    }
                }
            }
        }
    }

    /** Keeps short-lived effects anchored to the newly rebuilt status-aware cards. */
    private void remapTransientEffectPanels(
        List<CombatantPanel> previousPlayerPanels,
        List<CombatantPanel> previousEnemyPanels
    ) {
        unleashedMoveTargetPanel = replacementPanel(
            unleashedMoveTargetPanel, previousPlayerPanels, previousEnemyPanels);
        java.util.Iterator<HitFlash> iterator = hitFlashes.iterator();
        while (iterator.hasNext()) {
            HitFlash flash = iterator.next();
            flash.targetPanel = replacementPanel(
                flash.targetPanel, previousPlayerPanels, previousEnemyPanels);
            if (flash.targetPanel == null) iterator.remove();
        }
    }

    private CombatantPanel replacementPanel(
        CombatantPanel previousPanel,
        List<CombatantPanel> previousPlayerPanels,
        List<CombatantPanel> previousEnemyPanels
    ) {
        if (previousPanel == null) return null;
        int playerIndex = previousPlayerPanels.size() == playerPanels.size()
            ? previousPlayerPanels.indexOf(previousPanel) : -1;
        if (playerIndex >= 0) {
            return playerPanels.get(playerIndex);
        }
        int enemyIndex = previousEnemyPanels.size() == enemyPanels.size()
            ? previousEnemyPanels.indexOf(previousPanel) : -1;
        if (enemyIndex >= 0) {
            return enemyPanels.get(enemyIndex);
        }
        return null;
    }

    private void clearTransientPanelReferences(CombatantPanel panel) {
        if (panel == null) return;
        if (unleashedMoveTargetPanel == panel) unleashedMoveTargetPanel = null;
        hitFlashes.removeIf(flash -> flash.targetPanel == panel);
        entranceAnimations.removeIf(entrance -> entrance.panel == panel);
    }

    private record OnlineCombatantKey(PlayerSide side, String identity) { }

    private static final class OnlineResourceState {
        int hp;
        int maxHp;
        int ce;
        int maxCe;

        OnlineResourceState(int hp, int maxHp, int ce, int maxCe) {
            this.hp = hp;
            this.maxHp = maxHp;
            this.ce = ce;
            this.maxCe = maxCe;
        }

        static OnlineResourceState from(CharacterState combatant) {
            return new OnlineResourceState(
                combatant.currentHp(), combatant.maxHp(),
                combatant.currentCe(), combatant.maxCe());
        }

        static OnlineResourceState full(CharacterState combatant) {
            return new OnlineResourceState(
                combatant.maxHp(), combatant.maxHp(),
                combatant.maxCe(), combatant.maxCe());
        }
    }

    private void initPanels() {
        layoutExecutionUi(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    /** Recreates all execution widgets from the live viewport after a resize. */
    private void layoutExecutionUi(float width, float height) {
        layoutExecutionUi(width, height, false);
    }

    private void layoutExecutionUi(
        float width,
        float height,
        boolean remapTransientEffects
    ) {
        boolean unifiedWindows = windowsUnified();
        float originX = unifiedWindows ? WINDOWS_EXECUTION_X : 0f;
        float originY = unifiedWindows ? WINDOWS_EXECUTION_Y : 0f;
        if (unifiedWindows) {
            width = WINDOWS_EXECUTION_WIDTH;
            height = WINDOWS_EXECUTION_HEIGHT;
        }
        BattleUiLayout.Execution layout = uiLayout.execution;
        float textGeometryScale = executionTextGeometryScale();
        float margin = Math.min(layout.outerMarginMax,
            Math.max(layout.outerMarginMin,
                Math.min(width, height) * layout.outerMarginFraction));
        List<Texture> visibleEnemySprites = visibleTeamSprites(enemyTeamSprites);
        List<Texture> visiblePlayerSprites = visibleTeamSprites(playerTeamSprites);
        List<Boolean> visibleEnemyStatuses = visibleStatusPresence(false);
        List<Boolean> visiblePlayerStatuses = visibleStatusPresence(true);
        executionHudStatusLayoutSignature = statusLayoutSignature(
            visiblePlayerStatuses, visibleEnemyStatuses);
        int enemyCount = visibleEnemySprites.size();
        int playerCount = visiblePlayerSprites.size();

        Rectangle fieldLogBounds = unifiedWindows ? new Rectangle() : logBounds;
        float logHeight = Math.min(layout.logHeightMax,
            Math.max(layout.logHeightMin, height * layout.logHeightFraction));
        float logTop = margin + logHeight;
        fieldLogBounds.set(0f, 3f, width, logTop - 3f);

        float fieldBottom = fieldLogBounds.y + fieldLogBounds.height + layout.fieldLogGap;
        float fieldTop = height - margin;
        float fieldHeight = Math.max(1f, fieldTop - fieldBottom);

        float fullHudWidth = Math.min(layout.hudWidthMax,
            Math.max(layout.hudWidthMin, width * layout.hudWidthFraction));
        fullHudWidth = Math.min(fullHudWidth,
            Math.max(1f, (width - margin * 2f - layout.hudCenterGap) / 2f));
        float requestedHudShift = Math.min(
            layout.hudSideShiftMax, width * layout.hudSideShiftFraction);
        float hudHorizontalNudge = Math.min(
            layout.hudHorizontalNudgeMax,
            width * layout.hudHorizontalNudgeFraction);
        fullHudWidth = scaledHudWidth(fullHudWidth, layout.hudScale,
            width, margin, layout.hudCenterGap,
            requestedHudShift + hudHorizontalNudge,
            uiLayout.storedProfile() == UiProfile.WINDOWS);
        float hudHeight = Math.min(layout.hudHeightMax,
            Math.max(layout.hudHeightMin,
                fieldHeight * layout.hudHeightFraction)) * layout.hudScale;
        float playerHudY = fieldBottom + fieldHeight * layout.playerHudYOffsetFraction;
        float playerBottomHudY = fieldLogBounds.y + fieldLogBounds.height + layout.fieldLogGap;
        float hudVerticalNudge = Math.max(0f, playerHudY - playerBottomHudY);
        float availableCenterGap = width - margin * 2f - fullHudWidth * 2f;
        float hudShift = Math.min(
            requestedHudShift,
            Math.max(0f, (availableCenterGap - layout.hudCenterGap) / 2f));
        float enemyHudWidth = fullHudWidth * (enemyCount <= 2
            ? 1f : layout.multiCombatantHudWidthScale);
        float playerHudWidth = fullHudWidth * (playerCount <= 2
            ? 1f : layout.multiCombatantHudWidthScale);
        float enemyHudColumnGap = Math.max(
            layout.hudColumnGapMin, enemyHudWidth * layout.hudColumnGapFraction);
        float playerHudColumnGap = Math.max(
            layout.hudColumnGapMin, playerHudWidth * layout.hudColumnGapFraction);
        float hudRowGap = Math.max(
            layout.hudRowGapMin, hudHeight * layout.hudRowGapFraction);
        float enemyFullHudWidth = fullHudWidth;
        float playerFullHudWidth = fullHudWidth;
        float playerHudGroupWidth = hudGroupWidth(
            playerCount, playerHudWidth, playerHudColumnGap);

        float enemyPlateBaseSize = Math.min(
            fieldHeight * layout.enemyPlateHeightFraction,
            width * layout.enemyPlateWidthFraction);
        float playerPlateBaseSize = Math.min(
            fieldHeight * layout.playerPlateHeightFraction,
            width * layout.playerPlateWidthFraction);
        float enemyPlateSize = enemyPlateBaseSize * plateScale(enemyCount);
        float playerPlateSize = playerPlateBaseSize * plateScale(playerCount);
        float enemyCenterX = width - margin - width * layout.sideCenterInsetFraction;
        float playerCenterX = margin + width * layout.sideCenterInsetFraction;
        float expandedPlayerCenterX = Math.max(
            playerCenterX + Math.min(
                layout.expandedPlayerCenterNudgeMax,
                width * layout.expandedPlayerCenterNudgeFraction),
            playerPlateBaseSize);
        float enemyFourFighterLeftShift = enemyFourFighterLeftShift(
            margin, playerPlateBaseSize * 2f, expandedPlayerCenterX);
        if (enemyCount == 4) enemyCenterX -= enemyFourFighterLeftShift;

        float enemySpriteSize = Math.min(
            fieldHeight * layout.enemySpriteHeightFraction,
            width * layout.enemySpriteWidthFraction);
        float playerSpriteSize = Math.min(
            fieldHeight * layout.playerSpriteHeightFraction,
            width * layout.playerSpriteWidthFraction);
        // Drop both complete fighter groups by the player's former gap to the log.
        float fighterDrop = layout.fighterDrop + fieldHeight * layout.fighterDropFraction;
        float enemySpriteY = fieldTop - enemySpriteSize - fighterDrop;
        float playerSpriteY = fieldBottom
            + fieldHeight * layout.playerSpriteBottomFraction - fighterDrop;
        // Then lower each plate again relative to its sprite, matching the authored footing.
        float plateDrop = fieldHeight * layout.plateDropFraction;
        // The visible stone ellipse occupies about 28% of its square texture.
        // Seven percent of the texture is therefore roughly one quarter of the visible height.
        float enemyPlateCenterY = enemySpriteY
            + enemySpriteSize * layout.spriteFootFraction
            - plateDrop + enemyPlateBaseSize * layout.enemyPlateLiftFraction;
        float playerPlateCenterY = playerSpriteY
            + playerSpriteSize * layout.spriteFootFraction - plateDrop;

        float enemyHudY = enemySpriteY + enemySpriteSize - hudHeight;
        float playerTopHudY = playerHudY + hudHeight + hudRowGap;
        float playerPrimaryHudY = playerCount == 1
            ? centeredHudY(playerTopHudY, hudHeight, hudRowGap)
            : playerTopHudY;

        Rectangle enemyPlate = new Rectangle(
            enemyCenterX - enemyPlateSize / 2f,
            enemyPlateCenterY
                - enemyPlateBaseSize * layout.plateTextureYOffsetFraction
                - enemyPlateSize / 2f,
            enemyPlateSize,
            enemyPlateSize
        );
        float enemyTopHudY = enemyHudY + hudVerticalNudge;
        float enemyPrimaryHudY = enemyCount == 1
            ? centeredHudY(enemyTopHudY, hudHeight, hudRowGap)
            : enemyTopHudY - hudHeight - hudRowGap;
        Rectangle enemyHud = new Rectangle(
            enemyCount <= 2 ? margin + hudShift + hudHorizontalNudge : margin,
            enemyPrimaryHudY,
            enemyHudWidth,
            hudHeight);

        Rectangle playerHud = new Rectangle(
            playerCount <= 2
                ? width - margin - fullHudWidth - hudShift - hudHorizontalNudge
                : width - margin - playerHudGroupWidth,
            playerPrimaryHudY - hudVerticalNudge,
            playerHudWidth,
            hudHeight);

        if (playerCount >= 3) {
            float playerRightShift = halfRightEdgeGap(
                width, playerHud.x, playerHudGroupWidth);
            playerHud.x += playerRightShift;
            playerCenterX += playerRightShift;
        }
        if (playerCount == 3 && enemyCount == 3) {
            playerCenterX += layout.threeVsThreePlayerShift;
        }

        if (enemyCount == 4) {
            float playerHudTop = playerHud.y + playerHud.height;
            float enemyClearanceShift = enemyPlateClearanceShift(
                enemyPlate.y, enemyPlate.height, playerHudTop);
            enemyPlate.y += enemyClearanceShift;
            enemySpriteY += enemyClearanceShift;
        }

        Rectangle playerPlate = new Rectangle(
            playerCenterX - playerPlateSize / 2f,
            playerPlateCenterY
                - playerPlateBaseSize * layout.plateTextureYOffsetFraction
                - playerPlateSize / 2f,
            playerPlateSize,
            playerPlateSize
        );

        if (unifiedWindows) {
            WindowsExecutionGeometry geometry = windowsExecutionGeometry(enemyCount, playerCount);
            enemyPlate.set(geometry.enemyPlate());
            playerPlate.set(geometry.playerPlate());
            enemyHud.set(geometry.enemyHud());
            playerHud.set(geometry.playerHud());
            enemySpriteY = geometry.enemySpriteY();
            playerSpriteY = geometry.playerSpriteY();
            enemySpriteSize = geometry.spriteSize();
            playerSpriteSize = geometry.spriteSize();
            enemyFullHudWidth = windowsHudLargeWidth(true);
            playerFullHudWidth = windowsHudLargeWidth(false);
            enemyHudColumnGap = windowsHudColumnGap(enemyCount, true);
            playerHudColumnGap = windowsHudColumnGap(playerCount, false);
            hudHeight = WINDOWS_HUD_HEIGHT;
            hudRowGap = windowsHudRowGap();
        } else {
            enemyPlate.x += originX;
            enemyPlate.y += originY;
            playerPlate.x += originX;
            playerPlate.y += originY;
            enemyHud.x += originX;
            enemyHud.y += originY;
            playerHud.x += originX;
            playerHud.y += originY;
            enemySpriteY += originY;
            playerSpriteY += originY;
        }

        List<CombatantPanel> previousEnemyPanels = enemyPanels;
        List<CombatantPanel> previousPlayerPanels = playerPanels;
        enemyPanels = buildCombatantPanels(
            visibleEnemySprites, enemyPlate, enemySpriteY, enemySpriteSize,
            enemyHud, enemyFullHudWidth, enemyHudColumnGap, hudRowGap,
            visibleEnemyStatuses, true);
        playerPanels = buildCombatantPanels(
            visiblePlayerSprites, playerPlate, playerSpriteY, playerSpriteSize,
            playerHud, playerFullHudWidth, playerHudColumnGap, hudRowGap,
            visiblePlayerStatuses, false);
        enemyPanel = enemyPanels.isEmpty() ? null : enemyPanels.get(0);
        playerPanel = playerPanels.isEmpty() ? null : playerPanels.get(0);
        if (remapTransientEffects) {
            remapTransientEffectPanels(previousPlayerPanels, previousEnemyPanels);
        }
        remapFaintAnimationPanels();
        remapEntranceAnimationPanels();
        updateDisplayedAbilityMeters();

        float miracleSize = Math.min(
            MiraclesMeter.sizeForViewport(height, textGeometryScale),
            Math.min(playerHud.height, width * layout.miraclesWidthFraction));
        miraclesMeter.setBounds(
            Math.max(originX + margin, playerHud.x - miracleSize - layout.meterHudGap),
            playerHud.y + (playerHud.height - miracleSize) / 2f,
            miracleSize,
            textGeometryScale
        );
        float ratioHeight = Math.min(
            RatioMeter.heightForViewport(height, textGeometryScale),
            Math.min(playerHud.height * 0.75f, width * layout.ratioWidthFraction));
        float ratioWidth = RatioMeter.widthForHeight(ratioHeight);
        ratioMeter.setBounds(
            Math.max(originX + margin, playerHud.x - ratioWidth - layout.meterHudGap),
            playerHud.y + (playerHud.height - ratioHeight) / 2f,
            ratioHeight,
            textGeometryScale
        );
        float resourceWidth = Math.min(250f * textGeometryScale,
            Math.max(170f * textGeometryScale, playerHud.width * 0.42f));
        float resourceRowHeight = Math.min(38f * textGeometryScale,
            Math.max(28f * textGeometryScale, playerHud.height * 0.24f));
        float resourceHeight = abilityStateMeter.stateCount() == 0 ? 0f
            : abilityStateMeter.stateCount() * (resourceRowHeight + 4f) - 4f;
        float bespokeMeterWidth = 0f;
        if (miraclesMeter.isVisible()) bespokeMeterWidth = miracleSize;
        if (ratioMeter.isVisible()) bespokeMeterWidth = Math.max(bespokeMeterWidth, ratioWidth);
        abilityStateMeter.setBounds(
            Math.max(originX + margin, playerHud.x - bespokeMeterWidth
                - (bespokeMeterWidth > 0f ? layout.meterHudGap : 0f)
                - resourceWidth - layout.meterHudGap),
            playerHud.y + (playerHud.height - resourceHeight) / 2f,
            resourceWidth,
            resourceRowHeight);

        if (unifiedWindows) {
            logBounds.set(windowsLogBounds());
            nextRoundBounds.set(windowsActionBounds());
            fastForwardBounds.set(windowsFastForwardBounds());
            skipBounds.set(windowsSkipBounds());
        } else {
            float nextRoundWidth = Math.min(layout.nextRoundWidthMax,
                Math.max(layout.nextRoundWidthMin, width * layout.nextRoundWidthFraction));
            float nextRoundHeight = Math.min(
                layout.nextRoundHeightMax,
                Math.max(1f, logBounds.height - layout.nextRoundVerticalPadding));
            nextRoundBounds.set(
                logBounds.x + logBounds.width - nextRoundWidth - layout.nextRoundInset,
                logBounds.y + layout.nextRoundInset,
                nextRoundWidth,
                nextRoundHeight
            );
            layoutSpeedControls(nextRoundBounds, logBounds.y + logBounds.height,
                SPEED_CONTROL_SIZE_MAX, fastForwardBounds, skipBounds);
        }
        boolean wasLayingOut = layingOutExecutionUi;
        layingOutExecutionUi = true;
        try {
            updatePanels();
        } finally {
            layingOutExecutionUi = wasLayingOut;
        }
    }

    record WindowsExecutionGeometry(
        Rectangle enemyPlate,
        Rectangle playerPlate,
        Rectangle enemyHud,
        Rectangle playerHud,
        float enemySpriteY,
        float playerSpriteY,
        float spriteSize
    ) { }

    static Rectangle windowsExecutionBounds() {
        return new Rectangle(
            WINDOWS_EXECUTION_X,
            WINDOWS_EXECUTION_Y,
            WINDOWS_EXECUTION_WIDTH,
            WINDOWS_EXECUTION_HEIGHT);
    }

    static Rectangle windowsLogBounds() {
        return new Rectangle(
            0f,
            WINDOWS_BOTTOM_SECTION_HEIGHT,
            WindowsBattleCanvas.LEFT_COLUMN_WIDTH,
            WindowsBattleCanvas.HEIGHT - WINDOWS_BOTTOM_SECTION_HEIGHT);
    }

    static Rectangle windowsActionBounds() {
        return windowsActionBounds(false);
    }

    static Rectangle windowsActionBounds(boolean withPlaybackControls) {
        return new Rectangle(
            withPlaybackControls
                ? WindowsBattleCanvas.PLAYBACK_ACTION_X : WindowsBattleCanvas.ACTION_X,
            WindowsBattleCanvas.ACTION_Y,
            WindowsBattleCanvas.ACTION_WIDTH,
            WindowsBattleCanvas.ACTION_HEIGHT);
    }

    static Rectangle windowsFastForwardBounds() {
        return new Rectangle(
            WindowsBattleCanvas.SPEED_CONTROL_X,
            WindowsBattleCanvas.ACTION_Y
                + WindowsBattleCanvas.SPEED_CONTROL_SIZE
                + WindowsBattleCanvas.SPEED_CONTROL_GAP,
            WindowsBattleCanvas.SPEED_CONTROL_SIZE,
            WindowsBattleCanvas.SPEED_CONTROL_SIZE);
    }

    static Rectangle windowsSkipBounds() {
        return new Rectangle(
            WindowsBattleCanvas.SPEED_CONTROL_X,
            WindowsBattleCanvas.ACTION_Y,
            WindowsBattleCanvas.SPEED_CONTROL_SIZE,
            WindowsBattleCanvas.SPEED_CONTROL_SIZE);
    }

    static WindowsExecutionGeometry windowsExecutionGeometry(int enemyCount, int playerCount) {
        float enemyPlateSize = enemyCount == 3
            ? WindowsBattleCanvas.WIDTH - WINDOWS_ENEMY_THREE_PLATE_LEFT_X
            : windowsPlateSize(enemyCount);
        float playerPlateSize = playerCount == 3
            ? WINDOWS_PLAYER_THREE_PLATE_SIZE : windowsPlateSize(playerCount);
        float enemyCenterX = enemyCount == 3
            ? WINDOWS_ENEMY_THREE_PLATE_LEFT_X + enemyPlateSize / 2f
            : windowsFighterCenterX(true);
        if (enemyCount == 4) enemyCenterX -= windowsEnemyFourFighterLeftShift();
        float playerCenterX = playerCount == 3
            ? WINDOWS_PLAYER_THREE_PLATE_X + playerPlateSize / 2f
            : windowsFighterCenterX(false) + windowsExpandedPlayerRightShift(playerCount);

        // Grow team plates around the current one-fighter centers.
        Rectangle enemyPlate = windowsCenteredPlateBounds(
            enemyCenterX, windowsEnemyPlateCenterY(), enemyPlateSize);
        Rectangle playerPlate = windowsCenteredPlateBounds(
            playerCenterX, WINDOWS_BOTTOM_SECTION_HEIGHT, playerPlateSize);

        Rectangle enemyHud = new Rectangle(
            windowsHudX(enemyCount, true),
            windowsPrimaryHudY(enemyCount, true),
            windowsHudWidth(enemyCount, true),
            WINDOWS_HUD_HEIGHT);
        Rectangle playerHud = new Rectangle(
            windowsHudX(playerCount, false),
            windowsPrimaryHudY(playerCount, false),
            windowsHudWidth(playerCount, false),
            WINDOWS_HUD_HEIGHT);

        enemyPlate.y += WINDOWS_ENEMY_PLATE_UPWARD_SHIFT;
        return new WindowsExecutionGeometry(
            enemyPlate, playerPlate, enemyHud, playerHud,
            WINDOWS_ENEMY_FIGHTER_BOTTOM_Y, WINDOWS_PLAYER_FIGHTER_BOTTOM_Y,
            WINDOWS_FIGHTER_SPRITE_SIZE);
    }

    private static Rectangle windowsCenteredPlateBounds(
        float centerX,
        float centerY,
        float size
    ) {
        return new Rectangle(
            centerX - size / 2f,
            centerY - size / 2f,
            size,
            size);
    }

    private static float windowsEnemyPlateCenterY() {
        return WINDOWS_ENEMY_FIGHTER_BOTTOM_Y
            + WINDOWS_BASE_PLATE_SIZE * (0.5f
                - BASE_PLATE_VISIBLE_BOTTOM_RATIO
                - BASE_PLATE_VISIBLE_HEIGHT_RATIO / 2f)
            + WINDOWS_ENEMY_PLATE_UPWARD_NUDGE;
    }

    private static float windowsPlateSize(int combatantCount) {
        return WINDOWS_BASE_PLATE_SIZE * plateScale(combatantCount);
    }

    private static float windowsFighterCenterX(boolean opponent) {
        return opponent ? WINDOWS_ENEMY_FIGHTER_CENTER_X : WINDOWS_PLAYER_FIGHTER_CENTER_X;
    }

    private static float windowsHudWidth(int combatantCount, boolean opponent) {
        return combatantCount <= 2
            ? windowsHudLargeWidth(opponent) : windowsHudSmallWidth(opponent);
    }

    private static float windowsHudSmallWidth(boolean opponent) {
        float regionWidth = opponent
            ? WINDOWS_ENEMY_HUD_REGION_WIDTH : WINDOWS_PLAYER_HUD_REGION_WIDTH;
        return regionWidth * 0.5f;
    }

    private static float windowsHudLargeWidth(boolean opponent) {
        float smallWidth = windowsHudSmallWidth(opponent);
        return smallWidth * 2f + windowsHudCompactGap(opponent);
    }

    private static float windowsHudX(int combatantCount, boolean opponent) {
        if (combatantCount <= 2) {
            return opponent
                ? WINDOWS_ENEMY_HUD_REGION_X
                : WindowsBattleCanvas.WIDTH - WINDOWS_HUD_OUTER_MARGIN
                    - windowsHudLargeWidth(false);
        }
        if (opponent) {
            return WINDOWS_EXECUTION_X + WINDOWS_EXPANDED_HUD_EDGE_MARGIN;
        }
        float groupWidth = hudGroupWidth(
            combatantCount,
            windowsHudWidth(combatantCount, false),
            windowsHudColumnGap(combatantCount, false));
        float x = WindowsBattleCanvas.WIDTH
            - WINDOWS_EXPANDED_HUD_EDGE_MARGIN - groupWidth;
        return x + halfRightEdgeGap(WindowsBattleCanvas.WIDTH, x, groupWidth);
    }

    static float windowsHudColumnGap(int combatantCount, boolean opponent) {
        return windowsHudCompactGap(opponent);
    }

    private static float windowsHudCompactGap(boolean opponent) {
        return Math.max(
            WINDOWS_HUD_COLUMN_GAP_MIN,
            windowsHudSmallWidth(opponent) * WINDOWS_HUD_COLUMN_GAP_SCALE);
    }

    static float windowsHudRowGap() {
        return Math.max(
            WINDOWS_HUD_ROW_GAP_MIN,
            WINDOWS_HUD_HEIGHT * WINDOWS_HUD_ROW_GAP_SCALE);
    }

    private static float windowsPrimaryHudY(int combatantCount, boolean opponent) {
        float regionY = opponent
            ? WINDOWS_ENEMY_HUD_REGION_Y : WINDOWS_PLAYER_HUD_REGION_Y;
        float regionHeight = opponent
            ? WINDOWS_ENEMY_HUD_REGION_HEIGHT : WINDOWS_PLAYER_HUD_REGION_HEIGHT;
        float singleHudY = regionY + (regionHeight - WINDOWS_HUD_HEIGHT) / 2f;
        if (combatantCount <= 1) return singleHudY;
        float rowOffset = (WINDOWS_HUD_HEIGHT + windowsHudRowGap()) / 2f;
        return opponent ? singleHudY - rowOffset : singleHudY + rowOffset;
    }

    private static float windowsExpandedPlayerRightShift(int combatantCount) {
        if (combatantCount < 3) return 0f;
        float groupWidth = hudGroupWidth(
            combatantCount,
            windowsHudWidth(combatantCount, false),
            windowsHudColumnGap(combatantCount, false));
        float groupX = WindowsBattleCanvas.WIDTH
            - WINDOWS_EXPANDED_HUD_EDGE_MARGIN - groupWidth;
        return halfRightEdgeGap(WindowsBattleCanvas.WIDTH, groupX, groupWidth);
    }

    private static float windowsEnemyFourFighterLeftShift() {
        float expandedPlayerCenterX = Math.max(
            WINDOWS_PLAYER_FIGHTER_CENTER_X + WINDOWS_EXPANDED_PLAYER_CENTER_NUDGE,
            WINDOWS_EXECUTION_X + WINDOWS_BASE_PLATE_SIZE);
        return enemyFourFighterLeftShift(
            WINDOWS_EXECUTION_X + WINDOWS_EXPANDED_HUD_EDGE_MARGIN,
            WINDOWS_BASE_PLATE_SIZE * 2f,
            expandedPlayerCenterX);
    }

    static void layoutSpeedControls(
        Rectangle nextRound,
        float logTop,
        Rectangle fastForward,
        Rectangle skip
    ) {
        layoutSpeedControls(
            nextRound, logTop, SPEED_CONTROL_SIZE_MAX, fastForward, skip);
    }

    private static void layoutSpeedControls(
        Rectangle nextRound,
        float logTop,
        float sizeMaximum,
        Rectangle fastForward,
        Rectangle skip
    ) {
        float availableHeight = Math.max(1f,
            logTop - SPEED_CONTROL_PANEL_INSET
                - (nextRound.y + nextRound.height)
                - SPEED_CONTROL_GAP);
        float availableWidth = Math.max(1f,
            (nextRound.width - SPEED_CONTROL_GAP) / 2f);
        float size = Math.min(sizeMaximum,
            Math.min(nextRound.height, Math.min(availableHeight, availableWidth)));
        float y = nextRound.y + nextRound.height + SPEED_CONTROL_GAP;
        skip.set(nextRound.x + nextRound.width - size, y, size, size);
        fastForward.set(skip.x - SPEED_CONTROL_GAP - size, y, size, size);
    }

    private List<CombatantPanel> buildCombatantPanels(
        List<Texture> teamSprites,
        Rectangle plate,
        float spriteY,
        float spriteSize,
        Rectangle primaryHud,
        float fullHudWidth,
        float hudColumnGap,
        float hudRowGap,
        List<Boolean> statusPresence,
        boolean opponent
    ) {
        List<CombatantPanel> panels = new ArrayList<>(teamSprites.size());
        float plateCenterX = plate.x + plate.width / 2f;
        float textGeometryScale = executionTextGeometryScale();
        float hudTextScale = windowsUnified() ? WINDOWS_HUD_TEXT_SCALE : 1f;
        float statusBandHeight = CombatantPanel.statusBandHeight(textGeometryScale);
        float upperRowStatusBandHeight = upperRowStatusBandHeight(
            statusPresence, teamSprites.size(), opponent, statusBandHeight);
        for (int i = 0; i < teamSprites.size(); i++) {
            Texture spriteTexture = teamSprites.get(i);
            float fighterCenterX = windowsUnified()
                ? windowsCombatantCenterX(
                    i, teamSprites.size(), plate, opponent)
                : plateCenterX
                    + fighterOffset(i, teamSprites.size(), plate.width, opponent);
            Rectangle sprite = spriteBounds(
                spriteTexture, fighterCenterX, spriteY, spriteSize, opponent);
            boolean hasStatus = i < statusPresence.size() && statusPresence.get(i);
            Rectangle hud = statusAwareCombatantHudBounds(
                i, teamSprites.size(), primaryHud, fullHudWidth,
                hudColumnGap, hudRowGap, opponent, hasStatus,
                statusBandHeight, upperRowStatusBandHeight);
            float barHeightScale = windowsUnified() ? WINDOWS_HUD_BAR_HEIGHT_SCALE : 1f;
            float barBorderScale = windowsUnified() ? WINDOWS_HUD_BAR_BORDER_SCALE : 1f;
            panels.add(new CombatantPanel(spriteTexture,
                i == 0 ? assets.stoneBasePlate : null,
                assets.battleUi, plate, sprite, hud, uiLayout.execution.hudScale, !opponent,
                textGeometryScale, hudTextScale, barHeightScale, barBorderScale));
        }
        return List.copyOf(panels);
    }

    /** Grows a HUD downward and optionally moves it below an expanded upper row. */
    static Rectangle expandedHudBounds(
        Rectangle primaryHud,
        float extraHeight,
        float lowerRowShift
    ) {
        float safeExtra = Math.max(0f, extraHeight);
        float safeShift = Math.max(0f, lowerRowShift);
        return new Rectangle(
            primaryHud.x,
            primaryHud.y - safeExtra - safeShift,
            primaryHud.width,
            primaryHud.height + safeExtra);
    }

    static Rectangle statusAwareCombatantHudBounds(
        int fighterIndex,
        int fighterCount,
        Rectangle primaryHud,
        float fullHudWidth,
        float columnGap,
        float rowGap,
        boolean opponent,
        boolean hasStatus,
        float statusBandHeight,
        float upperRowStatusBandHeight
    ) {
        Rectangle normalBounds = combatantHudBounds(
            fighterIndex, fighterCount, primaryHud, fullHudWidth, columnGap, rowGap, opponent);
        boolean upperRow = isUpperHudRow(fighterIndex % 2, opponent);
        return expandedHudBounds(
            normalBounds,
            hasStatus ? statusBandHeight : 0f,
            upperRow ? 0f : upperRowStatusBandHeight);
    }

    static float upperRowStatusBandHeight(
        List<Boolean> statusPresence,
        int fighterCount,
        boolean opponent,
        float statusBandHeight
    ) {
        if (statusPresence == null || fighterCount < 2) return 0f;
        int count = Math.min(fighterCount, statusPresence.size());
        for (int i = 0; i < count; i++) {
            if (Boolean.TRUE.equals(statusPresence.get(i))
                && isUpperHudRow(i % 2, opponent)) {
                return Math.max(0f, statusBandHeight);
            }
        }
        return 0f;
    }

    private static boolean isUpperHudRow(int row, boolean opponent) {
        return opponent ? row == 1 : row == 0;
    }

    static float windowsCombatantCenterX(
        int fighterIndex,
        int fighterCount,
        Rectangle plate,
        boolean opponent
    ) {
        if (fighterCount == 3) {
            if (opponent) {
                return switch (fighterIndex) {
                    case 0 -> WINDOWS_ENEMY_THREE_MIDDLE_X;
                    case 1 -> WINDOWS_ENEMY_THREE_RIGHT_X;
                    default -> WINDOWS_ENEMY_THREE_LEFT_X;
                };
            }
            return switch (fighterIndex) {
                case 0 -> WINDOWS_PLAYER_THREE_MIDDLE_X;
                case 1 -> WINDOWS_PLAYER_THREE_RIGHT_X;
                default -> WINDOWS_PLAYER_THREE_LEFT_X;
            };
        }
        float plateCenterX = plate.x + plate.width / 2f;
        return plateCenterX + fighterOffset(
            fighterIndex, fighterCount, plate.width, opponent);
    }

    private float executionTextGeometryScale() {
        return uiLayout.storedProfile() == UiProfile.WINDOWS
            ? uiLayout.execution.textGeometryScale : 1f;
    }

    static float scaledHudWidth(
        float unscaledWidth,
        float hudScale,
        float viewportWidth,
        float margin,
        float centerGap,
        float inwardOffset,
        boolean constrainAfterScaling
    ) {
        float scaledWidth = unscaledWidth * hudScale;
        if (!constrainAfterScaling) return scaledWidth;
        return Math.min(scaledWidth,
            Math.max(1f,
                (viewportWidth - margin * 2f - centerGap) / 2f - inwardOffset));
    }

    private static List<Texture> visibleTeamSprites(List<Texture> teamSprites) {
        if (teamSprites == null || teamSprites.isEmpty()) return List.of();
        return teamSprites.stream().limit(MAX_VISIBLE_COMBATANTS_PER_SIDE).toList();
    }

    private static float hudGroupWidth(int combatantCount, float hudWidth, float columnGap) {
        return combatantCount > 2 ? hudWidth * 2f + columnGap : hudWidth;
    }

    static Rectangle combatantHudBounds(
        int fighterIndex,
        int fighterCount,
        Rectangle primaryHud,
        float fullHudWidth,
        float columnGap,
        float rowGap,
        boolean opponent
    ) {
        int column = fighterIndex / 2;
        int row = fighterIndex % 2;
        // The second row is unpaired only for a three-fighter formation.
        boolean unpairedHudRow = fighterCount == 3 && row == 1;
        float hudWidth = unpairedHudRow ? fullHudWidth : primaryHud.width;
        float hudX = unpairedHudRow
            ? primaryHud.x + (hudGroupWidth(fighterCount, primaryHud.width, columnGap)
                - hudWidth) / 2f
            : primaryHud.x + column * (primaryHud.width + columnGap);
        return new Rectangle(
            hudX,
            hudRowY(primaryHud.y, row, primaryHud.height, rowGap, opponent),
            hudWidth,
            primaryHud.height);
    }

    static float halfRightEdgeGap(float screenWidth, float groupX, float groupWidth) {
        return Math.max(0f, screenWidth - groupX - groupWidth) / 2f;
    }

    static float hudRowY(
        float primaryY,
        int row,
        float hudHeight,
        float rowGap,
        boolean opponent
    ) {
        float rowOffset = row * (hudHeight + rowGap);
        return opponent ? primaryY + rowOffset : primaryY - rowOffset;
    }

    static float centeredHudY(float topHudY, float hudHeight, float rowGap) {
        return topHudY - (hudHeight + rowGap) / 2f;
    }

    static float plateScale(int combatantCount) {
        if (combatantCount <= 2) return 1f;
        return combatantCount == 3 ? 1.5f : 2f;
    }

    static float fighterOffset(
        int fighterIndex,
        int fighterCount,
        float plateWidth,
        boolean opponent
    ) {
        float spacing = plateWidth * (fighterCount <= 2 ? 0.34f : 0.17f);
        float offset = formationOffset(fighterIndex, fighterCount, spacing);
        return opponent && fighterCount == 2 ? -offset : offset;
    }

    static float enemyFourFighterLeftShift(
        float margin,
        float playerPlateSize,
        float currentPlayerCenterX
    ) {
        float visiblePlateLeft = currentPlayerCenterX - playerPlateSize / 2f
            + playerPlateSize * BASE_PLATE_VISIBLE_LEFT_RATIO;
        return Math.max(0f, visiblePlateLeft - margin);
    }

    static float enemyPlateClearanceShift(
        float enemyPlateY,
        float enemyPlateHeight,
        float playerHudTop
    ) {
        float visiblePlateBottom = enemyPlateY
            + enemyPlateHeight * BASE_PLATE_VISIBLE_BOTTOM_RATIO;
        return Math.max(0f, playerHudTop + HUD_PLATE_CLEARANCE - visiblePlateBottom);
    }

    /**
     * Roster order is visually arranged as [third, first, second, fourth]. This
     * keeps the first fighter centered for a trio and all three/four slots evenly spaced.
     */
    static float formationOffset(int fighterIndex, int fighterCount, float spacing) {
        int slot = switch (fighterCount) {
            case 1 -> 0;
            case 2 -> fighterIndex;
            case 3 -> switch (fighterIndex) {
                case 0 -> 1;
                case 1 -> 2;
                default -> 0;
            };
            default -> switch (fighterIndex) {
                case 0 -> 1;
                case 1 -> 2;
                case 2 -> 0;
                default -> 3;
            };
        };
        return (slot - (fighterCount - 1) / 2f) * spacing;
    }

    private Rectangle spriteBounds(
        Texture sprite,
        float centerX,
        float bottomY,
        float baseSize,
        boolean opponent
    ) {
        float scale = assets.battleSpriteScale(sprite);
        if (!windowsUnified()) {
            return scaledSpriteBounds(centerX, bottomY, baseSize, scale);
        }
        return windowsScaledSpriteBounds(centerX, bottomY, baseSize, scale, opponent);
    }

    /** Scales a square sprite around its center X while preserving its ground/log-bar anchor. */
    static Rectangle scaledSpriteBounds(float centerX, float bottomY, float baseSize, float scale) {
        float scaledSize = baseSize * scale;
        return new Rectangle(centerX - scaledSize / 2f, bottomY, scaledSize, scaledSize);
    }

    /** Fits a configured sprite scale inside its Windows fighter zone without moving its feet. */
    static Rectangle fittedScaledSpriteBounds(
        float centerX,
        float bottomY,
        float baseSize,
        float scale,
        Rectangle zone
    ) {
        float horizontalCapacity = Math.max(0f, 2f * Math.min(
            centerX - zone.x,
            zone.x + zone.width - centerX));
        float verticalCapacity = Math.max(0f, zone.y + zone.height - bottomY);
        float fittedSize = Math.min(baseSize * scale,
            Math.min(horizontalCapacity, verticalCapacity));
        return new Rectangle(centerX - fittedSize / 2f, bottomY, fittedSize, fittedSize);
    }

    /** Reuses the current one-fighter fitted size at every Windows formation slot. */
    static Rectangle windowsScaledSpriteBounds(
        float centerX,
        float bottomY,
        float baseSize,
        float scale,
        boolean opponent
    ) {
        Rectangle oneFighterBounds = fittedScaledSpriteBounds(
            windowsFighterCenterX(opponent),
            opponent ? WINDOWS_ENEMY_FIGHTER_BOTTOM_Y : WINDOWS_PLAYER_FIGHTER_BOTTOM_Y,
            baseSize,
            scale,
            windowsFighterZoneBounds(opponent));
        return new Rectangle(
            centerX - oneFighterBounds.width / 2f,
            bottomY,
            oneFighterBounds.width,
            oneFighterBounds.height);
    }

    private static Rectangle windowsFighterZoneBounds(boolean opponent) {
        return opponent
            ? new Rectangle(
                WINDOWS_ENEMY_FIGHTER_ZONE_X,
                WINDOWS_ENEMY_FIGHTER_ZONE_Y,
                WINDOWS_ENEMY_FIGHTER_ZONE_WIDTH,
                WINDOWS_ENEMY_FIGHTER_ZONE_HEIGHT)
            : new Rectangle(
                WINDOWS_PLAYER_FIGHTER_ZONE_X,
                WINDOWS_PLAYER_FIGHTER_ZONE_Y,
                WINDOWS_PLAYER_FIGHTER_ZONE_WIDTH,
                WINDOWS_PLAYER_FIGHTER_ZONE_HEIGHT);
    }

    /**
     * Seed the LOCAL deferred HP ints from the live model. Called at round
     * boundaries so the bars start each round accurate and end-of-round
     * maintenance (poison, max-HP changes) converges them back to the model.
     */
    private void syncLocalHpFromModel() {
        localHpStates.clear();
        for (BattleCombatant combatant : renderPlayerTeam) {
            localHpStates.put(combatant,
                new LocalHpState(combatant.getCurrentHp(), combatant.getMaxHp()));
        }
        for (BattleCombatant combatant : renderEnemyTeam) {
            localHpStates.put(combatant,
                new LocalHpState(combatant.getCurrentHp(), combatant.getMaxHp()));
        }
        if (renderLocalState != null) {
            for (BattleCombatant combatant : renderLocalState.playerTeam().active()) {
                localHpStates.putIfAbsent(combatant,
                    new LocalHpState(combatant.getCurrentHp(), combatant.getMaxHp()));
            }
            for (BattleCombatant combatant : renderLocalState.enemyTeam().active()) {
                localHpStates.putIfAbsent(combatant,
                    new LocalHpState(combatant.getCurrentHp(), combatant.getMaxHp()));
            }
        }
    }

    private void syncLocalAbilityStatesFromModel() {
        localAbilityStates.clear();
        for (BattleCombatant combatant : renderPlayerTeam) {
            localAbilityStates.put(combatant, combatant.abilityStates());
        }
        for (BattleCombatant combatant : renderEnemyTeam) {
            localAbilityStates.put(combatant, combatant.abilityStates());
        }
    }

    /** Restores pre-batch HP when the field had to be bound after the model advanced. */
    private void rewindLocalHpEvents(List<CombatEvent> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            CombatEvent event = events.get(i);
            BattleCombatant target = event.getTarget();
            if (target == null) continue;
            int amount = event.getIntValue();
            localHpStates.computeIfPresent(target, (ignored, current) -> switch (event.getType()) {
                case DAMAGE_DEALT -> new LocalHpState(
                    Math.min(current.maxHp(), current.hp() + amount), current.maxHp());
                case HP_RESTORED -> new LocalHpState(
                    Math.max(0, current.hp() - amount), current.maxHp());
                default -> current;
            });
        }
    }

    /**
     * Apply an event's HP delta to the LOCAL deferred ints. Called in the same
     * posted runnable as the event's log line, so the bar/HP-text change lands
     * when that line plays. Only damage/heal/max-HP events touch HP; everything
     * else (notably MOVE_FIRED) leaves it alone — which is the whole point.
     * Target identity is by reference against the live render combatants.
     */
    private void applyLocalHpEvent(CombatEvent e) {
        int amount = e.getIntValue();
        BattleCombatant target = e.getTarget();
        if (target == null) return;
        localHpStates.computeIfPresent(target, (ignored, current) -> switch (e.getType()) {
            case DAMAGE_DEALT -> new LocalHpState(
                Math.max(0, current.hp() - amount), current.maxHp());
            case HP_RESTORED -> new LocalHpState(
                current.hp() + amount, current.maxHp());
            case MAX_HP_CHANGED -> {
                int maximum = Math.max(1, amount);
                yield new LocalHpState(Math.min(current.hp(), maximum), maximum);
            }
            case CHARACTER_TRANSFORMED, CHARACTER_REVERTED ->
                new LocalHpState(Math.max(0, amount), current.maxHp());
            default -> current;
        });
    }

    private void applyLocalAbilityEvent(CombatEvent event) {
        CodedAbilityState changed = event.getCodedAbilityState();
        if (changed == null) return;
        BattleCombatant owner = event.getType() == CombatEvent.Type.RESOURCE_CHANGED
            && event.getTarget() != null ? event.getTarget() : event.getSource();
        if (owner == null) return;
        localAbilityStates.compute(owner, (ignored, states) ->
            withAbilityState(states == null ? List.of() : states, changed));
    }

    private void refreshLocalFormSprite(CombatEvent event) {
        BattleCombatant combatant = event == null ? null : event.getTarget();
        String characterId = event == null ? null : event.getCharacterId();
        if (combatant == null || characterId == null || characterId.isBlank()) return;
        boolean playerSide = renderPlayerTeam.stream().anyMatch(candidate -> candidate == combatant);
        List<BattleCombatant> combatants = playerSide ? renderPlayerTeam : renderEnemyTeam;
        int index = -1;
        for (int i = 0; i < combatants.size(); i++) {
            if (combatants.get(i) == combatant) {
                index = i;
                break;
            }
        }
        if (index < 0) return;
        List<Texture> sprites = new ArrayList<>(playerSide
            ? playerTeamSprites : enemyTeamSprites);
        if (index >= sprites.size()) return;
        Texture fallback = playerSide ? assets.playerSprite : assets.enemySprite;
        sprites.set(index, assets.characterBattleSprite(
            game.multiplayerSpriteAsset(characterId), !playerSide, fallback));
        if (playerSide) {
            playerTeamSprites = List.copyOf(sprites);
            if (!sprites.isEmpty()) playerSprite = sprites.get(0);
        } else {
            enemyTeamSprites = List.copyOf(sprites);
            if (!sprites.isEmpty()) enemySprite = sprites.get(0);
        }
        // Panels capture their texture at build time, so the swap only shows
        // once the field is relaid out — on the transform broadcast's tick.
        layoutExecutionUi(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private record LocalHpState(int hp, int maxHp) { }

    /** Called on the render thread alongside the matching local HP-bar update. */
    private void flashLocalDamageSprite(CombatEvent event) {
        if (event.getType() != CombatEvent.Type.DAMAGE_DEALT || event.getIntValue() <= 0) return;
        CombatantPanel panel = panelForCombatant(event.getTarget());
        if (panel != null) panel.flashDamage();
    }

    private CombatantPanel panelForCombatant(BattleCombatant combatant) {
        if (combatant == null) return null;
        for (int i = 0; i < Math.min(renderPlayerTeam.size(), playerPanels.size()); i++) {
            if (renderPlayerTeam.get(i) == combatant) return playerPanels.get(i);
        }
        for (int i = 0; i < Math.min(renderEnemyTeam.size(), enemyPanels.size()); i++) {
            if (renderEnemyTeam.get(i) == combatant) return enemyPanels.get(i);
        }
        if (combatant == renderPlayer) return playerPanel;
        if (combatant == renderEnemy) return enemyPanel;
        return null;
    }

    private void updatePanels() {
        if (!layingOutExecutionUi
            && (!playerPanels.isEmpty() || !enemyPanels.isEmpty())
            && !Objects.equals(executionHudStatusLayoutSignature, statusLayoutSignature())) {
            layoutExecutionUi(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
            return;
        }
        updateDisplayedAbilityMeters();
        if (mode == BattleMode.MULTIPLAYER) {
            updateOnlineTeamPanels(
                playerPanels, renderOnlinePlayerTeam, multiplayerSetup.playerSide());
            updateOnlineTeamPanels(
                enemyPanels, renderOnlineEnemyTeam, opposite(multiplayerSetup.playerSide()));
            return;
        }
        // LOCAL: HP follows each damage log line; CE stays live because it drains
        // at a move's start tick, before that move fires.
        updateLocalTeamPanels(playerPanels, renderPlayerTeam);
        updateLocalTeamPanels(enemyPanels, renderEnemyTeam);
    }

    private List<Boolean> visibleStatusPresence(boolean playerSide) {
        if (mode == BattleMode.MULTIPLAYER) {
            List<CharacterState> combatants = playerSide
                ? renderOnlinePlayerTeam : renderOnlineEnemyTeam;
            return combatants.stream()
                .limit(MAX_VISIBLE_COMBATANTS_PER_SIDE)
                .map(combatant -> !combatant.statusEffects().isEmpty())
                .toList();
        }
        List<BattleCombatant> combatants = playerSide ? renderPlayerTeam : renderEnemyTeam;
        return combatants.stream()
            .limit(MAX_VISIBLE_COMBATANTS_PER_SIDE)
            .map(combatant -> !combatant.getActiveEffects().isEmpty())
            .toList();
    }

    private String statusLayoutSignature() {
        return statusLayoutSignature(
            visibleStatusPresence(true), visibleStatusPresence(false));
    }

    private static String statusLayoutSignature(
        List<Boolean> playerStatuses,
        List<Boolean> enemyStatuses
    ) {
        return String.valueOf(playerStatuses) + ':' + enemyStatuses;
    }

    private void updateDisplayedAbilityMeters() {
        List<CodedAbilityState> activePlannerStates = activePlannerCodedAbilityStates();
        if (activePlannerStates != null) {
            miraclesMeter.setState(findMiraclesState(activePlannerStates));
            ratioMeter.setState(findRatioState(activePlannerStates));
            abilityStateMeter.setStates(activePlannerStates);
            return;
        }
        if (mode == BattleMode.MULTIPLAYER) {
            miraclesMeter.setState(onlinePlayerMiracles);
            ratioMeter.setState(onlinePlayerRatio);
            CharacterState displayedPlayer = displayedOnlinePrimary(true);
            OnlineCombatantKey key = onlineKey(multiplayerSetup.playerSide(), displayedPlayer);
            abilityStateMeter.setStates(displayedPlayer == null ? List.of()
                : onlineAbilityStates.getOrDefault(key, displayedPlayer.codedAbilities()));
            return;
        }
        List<CodedAbilityState> primaryStates = renderPlayer == null
            ? List.of() : localAbilityStates.getOrDefault(
                renderPlayer, renderPlayer.abilityStates());
        miraclesMeter.setState(findMiraclesState(primaryStates));
        ratioMeter.setState(findRatioState(primaryStates));
        abilityStateMeter.setStates(primaryStates);
    }

    /** Returns null outside Windows team planning so execution keeps its primary-fighter meters. */
    private List<CodedAbilityState> activePlannerCodedAbilityStates() {
        if (!windowsUnified() || teamPlanningPanel == null) return null;
        String actorId = teamPlanningPanel.activeActorId();
        if (actorId == null || actorId.isBlank()) return null;

        if (mode == BattleMode.MULTIPLAYER) {
            for (CharacterState combatant : renderOnlinePlayerTeam) {
                if (actorId.equals(combatant.instanceId())) {
                    return onlineAbilityStates.getOrDefault(
                        onlineKey(multiplayerSetup.playerSide(), combatant),
                        combatant.codedAbilities());
                }
            }
            for (CharacterState combatant : renderOnlineEnemyTeam) {
                if (actorId.equals(combatant.instanceId())) {
                    PlayerSide enemySide = opposite(multiplayerSetup.playerSide());
                    return onlineAbilityStates.getOrDefault(
                        onlineKey(enemySide, combatant), combatant.codedAbilities());
                }
            }
            return null;
        }

        for (BattleCombatant combatant : renderPlayerTeam) {
            CombatantId id = combatant.getInstanceId();
            if (id != null && actorId.equals(id.value())) {
                return localAbilityStates.getOrDefault(combatant, combatant.abilityStates());
            }
        }
        for (BattleCombatant combatant : renderEnemyTeam) {
            CombatantId id = combatant.getInstanceId();
            if (id != null && actorId.equals(id.value())) {
                return localAbilityStates.getOrDefault(combatant, combatant.abilityStates());
            }
        }
        return null;
    }

    private void updateOnlineTeamPanels(
        List<CombatantPanel> panels,
        List<CharacterState> combatants,
        PlayerSide side
    ) {
        int count = Math.min(panels.size(), combatants.size());
        for (int i = 0; i < count; i++) {
            CharacterState combatant = combatants.get(i);
            OnlineResourceState resources = onlineResourceStates.get(onlineKey(side, combatant));
            if (resources == null) {
                panels.get(i).update(combatant.currentHp(), combatant.maxHp(),
                    combatant.currentCe(), combatant.maxCe());
            } else {
                panels.get(i).update(
                    resources.hp, resources.maxHp, resources.ce, resources.maxCe);
            }
            panels.get(i).updateStatusEffects(combatant.statusEffects());
        }
    }

    private void updateLocalTeamPanels(
        List<CombatantPanel> panels,
        List<BattleCombatant> combatants
    ) {
        int count = Math.min(panels.size(), combatants.size());
        for (int i = 0; i < count; i++) {
            BattleCombatant combatant = combatants.get(i);
            LocalHpState hp = localHpStates.get(combatant);
            if (hp == null) {
                panels.get(i).update(combatant);
            } else {
                panels.get(i).update(hp.hp(), hp.maxHp(),
                    combatant.getCurrentCe(), combatant.getMaxCursedEnergy());
                panels.get(i).updateStatusEffects(combatant);
            }
        }
    }

    private static CodedAbilityState findMiraclesState(List<CodedAbilityState> states) {
        if (states == null) return null;
        return states.stream()
            .filter(state -> MiraclesAbility.KEY.equals(state.key()))
            .findFirst()
            .orElse(null);
    }

    private static CodedAbilityState findRatioState(List<CodedAbilityState> states) {
        if (states == null) return null;
        return states.stream()
            .filter(state -> RatioAbility.KEY.equals(state.key()))
            .findFirst()
            .orElse(null);
    }

    private static CodedAbilityState roundStartMiraclesState(
        MatchState state,
        PlayerSide side,
        OnlineCombatantKey combatant,
        List<CodedAbilityState> fallback
    ) {
        return state.roundStartCharacterStates().stream()
            .filter(character -> character.side() == side)
            .filter(character -> combatant == null || character.instanceId() == null
                || combatant.identity().equals(character.instanceId()))
            .map(character -> findMiraclesState(character.codedAbilities()))
            .filter(Objects::nonNull)
            .findFirst()
            .orElseGet(() -> findMiraclesState(fallback));
    }

    private static CodedAbilityState roundStartRatioState(
        MatchState state,
        PlayerSide side,
        OnlineCombatantKey combatant,
        List<CodedAbilityState> fallback
    ) {
        return state.roundStartCharacterStates().stream()
            .filter(character -> character.side() == side)
            .filter(character -> combatant == null || character.instanceId() == null
                || combatant.identity().equals(character.instanceId()))
            .map(character -> findRatioState(character.codedAbilities()))
            .filter(Objects::nonNull)
            .findFirst()
            .orElseGet(() -> findRatioState(fallback));
    }

    private boolean hasPlayerRenderState() {
        return mode == BattleMode.MULTIPLAYER ? onlinePlayer != null : renderPlayer != null;
    }

    private boolean hasEnemyRenderState() {
        return mode == BattleMode.MULTIPLAYER ? onlineEnemy != null : renderEnemy != null;
    }

    private List<CombatantHud> combatantHuds(boolean playerSide) {
        if (mode == BattleMode.MULTIPLAYER) {
            List<CharacterState> side = playerSide
                ? renderOnlinePlayerTeam : renderOnlineEnemyTeam;
            return side.stream()
                .map(character -> new CombatantHud(character.name()))
                .toList();
        }
        List<BattleCombatant> team = playerSide ? renderPlayerTeam : renderEnemyTeam;
        if (!team.isEmpty()) {
            return team.stream()
                .map(combatant -> new CombatantHud(combatant.getCharacter().getName()))
                .toList();
        }
        BattleCombatant primary = playerSide ? renderPlayer : renderEnemy;
        return primary == null ? List.of() : List.of(
            new CombatantHud(primary.getCharacter().getName()));
    }

    private record CombatantHud(String name) { }

    static List<CharacterState> activeOnlineCombatants(PlayerState player) {
        if (player == null) return List.of();
        return player.combatants().stream()
            .filter(BattleScreen::isActiveCombatant)
            .limit(MAX_VISIBLE_COMBATANTS_PER_SIDE)
            .toList();
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            // Restore the interrupt flag so callers' abort/shutdown checks can
            // observe it; matches HttpApiClient / MatchWebSocketClient.
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sleep that wakes promptly on an abort. Used for the paced pauses during
     * resolution (event delay, tick delay) and message display — without it, an
     * Escape press during a multi-tick resolution would leave the battle thread
     * grinding through long sleeps in the background after the player has
     * already returned to the menu.
     */
    private void abortableSleepMs(long ms) {
        double elapsedPlaybackMs = 0d;
        long previousNanos = System.nanoTime();
        while (!abortRequested && !skipRoundRequested && isCurrentLocalBattleThread()
            && elapsedPlaybackMs < ms) {
            sleepMs(8L);
            long currentNanos = System.nanoTime();
            elapsedPlaybackMs += (currentNanos - previousNanos) / 1_000_000d
                * playbackSpeedMultiplier();
            previousNanos = currentNanos;
        }
    }

    private boolean isCurrentLocalBattleThread() {
        return mode == BattleMode.LOCAL
            && localBattleThread != null
            && Thread.currentThread() == localBattleThread;
    }

    private void unlockPlannerIfPlanOpen() {
        if (teamPlanningPanel != null
            && !onlinePlanningTimedOut
            && (onlinePlayer == null || !onlinePlayer.planSubmitted())) {
            teamPlanningPanel.unlock();
        }
    }

    private void postLocal(Runnable callback) {
        Thread run = Thread.currentThread();
        Gdx.app.postRunnable(() -> {
            if (mode == BattleMode.LOCAL
                && localBattleThread == run
                && !abortRequested
                && game.getScreen() == this) {
                callback.run();
            }
        });
    }
}
