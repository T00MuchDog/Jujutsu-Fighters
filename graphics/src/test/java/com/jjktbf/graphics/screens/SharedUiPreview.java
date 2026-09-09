package com.jjktbf.graphics.screens;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.jjktbf.graphics.AssetLoader;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.animation.DomainBackdropPlayer;
import com.jjktbf.graphics.audio.GameAudio;
import com.jjktbf.graphics.launch.DesktopLaunchOptions;
import com.jjktbf.graphics.ui.AbilityStateMeter;
import com.jjktbf.graphics.ui.battle.TeamPlanningPanel;
import com.jjktbf.graphics.ui.profile.BattleUiLayoutStore;
import com.jjktbf.model.character.Ability;
import com.jjktbf.model.character.AbilityData;
import com.jjktbf.model.character.CharacterData;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattleFormat;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.BattleTeamId;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.model.technique.InnateTechniqueData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Opt-in real-GL layout matrix. Offscreen buffers allow exact sizes larger than
 * the host desktop; only Graphics dimensions/input are substituted, not rendering.
 * Fixtures never load or save editable game content. Reflection stays test-only.
 */
public final class SharedUiPreview extends ApplicationAdapter {
    private static final int[][] SIZES = {
        {2560, 1440}, {1512, 982}, {2000, 1243}, {1920, 1080}, {1366, 768}, {2560, 1600}, {3440, 1440}
    };
    private final DesktopLaunchOptions options;
    private final int hdpi = Integer.getInteger("jjktbf.preview.hdpi", 1);
    private int compared;
    private Throwable failure;

    private SharedUiPreview(DesktopLaunchOptions options) { this.options = options; }

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Shared UI validation");
        config.setWindowedMode(960, 600);
        config.setHdpiMode(HdpiMode.Logical);
        config.disableAudio(true);
        var preview = new SharedUiPreview(DesktopLaunchOptions.parse(args));
        new Lwjgl3Application(preview, config);
        if (preview.failure != null) throw new AssertionError("UI preview failed", preview.failure);
    }

    @Override public void create() {
        Graphics graphics = Gdx.graphics;
        Input input = Gdx.input;
        AssetLoader assets = new AssetLoader(options.uiProfile());
        GameAudio audio = new GameAudio();
        JJKGame game = new JJKGame(options) {
            @Override public GameAudio audio() { return audio; }
            @Override public String multiplayerSpriteAsset(String id) {
                return id.startsWith("Player")
                    ? "assets/sprites/characters/mahito_frontsprite.png"
                    : "assets/sprites/characters/miwa_frontsprite.png";
            }
        };
        CharacterSelectScreen select = null;
        BattleScreen battle = null;
        try {
            assets.load();
            verifyResourceMeterBounds(assets);
            System.out.println("Gameplay cap heights: small=" + assets.gameplayFontSmall.getCapHeight()
                + ", medium=" + assets.gameplayFontMedium.getCapHeight());
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
                new Class<?>[] {Input.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getX") || method.getName().equals("getY")) return -100;
                    if (method.getReturnType() == boolean.class) return false;
                    return method.invoke(input, args);
                });
            List<Move> moves = moves();
            CharacterData data = character();
            var fighter = new SorcererCharacter(data.id, data.name, data.toCharacterStats(), null, moves.subList(0, 8));
            select = new CharacterSelectScreen(game, assets);
            // Supply the resolved profile cache directly: no repository or selection mutations.
            List<CharacterData> roster = new ArrayList<>(IntStream.range(0, 30).mapToObj(i -> {
                CharacterData row = character();
                row.id = "fixture-" + i;
                row.name = "Fighter " + (i + 1);
                return row;
            }).toList());
            roster.set(0, data);
            set(select, "characters", roster);
            set(select, "cursorIndex", 1);
            set(select, "movesCharacter", data);
            set(select, "learnedMoves", moves);
            set(select, "learnedMoveCeCosts", moves.stream().map(m -> 0).toList());
            set(select, "profileCharacter", fighter);
            set(select, "profileCombatant", new BattleCombatant(fighter));
            set(select, "profileTechniqueMoves", moves);
            var technique = new InnateTechniqueData();
            technique.name = data.innateTechniqueName;
            technique.description = "A reusable technique with precise timing and flexible targeting. "
                + "The description wraps within its own column without displacing the moves or abilities.";
            set(select, "profileTechnique", technique);
            AbilityData ability = new AbilityData();
            ability.id = "fixture-ability";
            ability.name = "Focused Energy";
            ability.mechanicText = "Strengthens the next attack.";
            set(select, "profileTechniqueAbilities", List.of(new Ability(ability)));
            ((Map<String, List<String>>) field(select, "moveSetDrafts")).put(data.id,
                moves.subList(0, 6).stream().map(Move::getId).toList());
            invoke(select, "rebuildLearnedDrawerCards");

            battle = new BattleScreen(game, assets, new BattleUiLayoutStore().load());
            for (int[] size : SIZES) {
                int width = size[0], height = size[1];
                if (hdpi > 1 && width != 1512) continue;
                Gdx.graphics = dimensions(graphics, width, height, hdpi);
                select.prepare(BattleFormat.ONE_V_ONE);
                set(select, "learnedDrawerExpanded", false);
                capture(select, width, height, "select");
                select.prepare(BattleFormat.SIX_V_SIX);
                capture(select, width, height, "select-team");
                set(select, "learnedDrawerExpanded", true);
                capture(select, width, height, "select-drawer");
                for (int count : new int[] {1, 3, 4}) {
                    BattleState state = state(moves, count);
                    battle.prepareLocal();
                    battle.show();
                    invoke(battle, "syncLocalBattlefield", new Class<?>[] {BattleState.class}, state);
                    invoke(battle, "syncLocalAbilityStatesFromModel");
                    var abilityStates = (Map<BattleCombatant, List<CodedAbilityState>>) field(battle, "localAbilityStates");
                    for (BattleCombatant player : state.playerTeam().active()) {
                        abilityStates.put(player, List.of(new CodedAbilityState(
                            "fixture-resource", "Long Resource Display Name", 8, 8, true)));
                    }
                    invoke(battle, "initPanels");
                    invoke(battle, "updatePanels");
                    TeamPlanningPanel planner = new TeamPlanningPanel(
                        150, state.playerTeam().active(), state, assets.battleUi, width, height);
                    planner.setLayout(new BattleUiLayoutStore().load());
                    String target = state.enemyTeam().active().get(0).getInstanceId().value();
                    check(planner.activePlanningPanel().restorePlacement(moves.get(0), 10, 0, target) != null,
                        "First queued fixture move");
                    check(planner.activePlanningPanel().restorePlacement(moves.get(1), 35, 0, target) != null,
                        "Second queued fixture move");
                    set(battle, "teamPlanningPanel", planner);
                    invoke(battle, "configurePlanningViewport");
                    set(battle, "executionUiActive", true);
                    ((List<String>) field(battle, "logLines")).addAll(List.of(
                        "The battle begins.", "Choose moves and targets, then lock the plan.",
                        "This log expands with the viewport while the fighters retain their proportions."));
                    capture(battle, width, height, "planning-" + count);
                    invoke(battle, "showExecutionUi");
                    set(battle, "playbackControlsOpen", true);
                    capture(battle, width, height, "execution-" + count);
                    if (count == 1) {
                        DomainBackdropPlayer domains = (DomainBackdropPlayer) field(battle, "domainBackdrops");
                        domains.sync(Map.of("preview-domain",
                            new DomainBackdropPlayer.DomainVisualState("000001", null)));
                        domains.update(1f);
                        capture(battle, width, height, "domain");
                        domains.clear();
                    }
                }
            }
            System.out.println("PASS: rendered both screens, drawer, team selection, domain backdrop, and 1/3/4 fighter planning/execution at all seven sizes; "
                + options.uiProfile() + " shell profile; " + compared + " matching opposite-profile images; HDPI=" + hdpi
                + " (HDPI>1 runs the MacBook size only)");
        } catch (Throwable problem) {
            failure = problem;
            problem.printStackTrace();
        } finally {
            Gdx.graphics = graphics;
            Gdx.input = input;
            if (select != null) select.dispose();
            if (battle != null) battle.dispose();
            assets.dispose();
            audio.dispose();
            Gdx.app.exit();
        }
    }

    private void capture(Screen screen, int width, int height, String stage) {
        int pixelWidth = width * hdpi, pixelHeight = height * hdpi;
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, pixelWidth, pixelHeight, false);
        try {
            buffer.begin();
            screen.resize(width, height);
            screen.render(0f);
            check(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error: " + stage);
            Pixmap image = Pixmap.createFromFrameBuffer(0, 0, pixelWidth, pixelHeight);
            try {
                String root = System.getProperty("jjktbf.preview.output", "graphics/target/shared-ui-preview");
                String suffix = hdpi == 1 ? "" : "-hdpi" + hdpi;
                var output = Gdx.files.local(root + "/" + options.uiProfile().fileStem() + suffix);
                output.mkdirs();
                String name = width + "x" + height + "-" + stage + ".png";
                var file = output.child(name);
                PixmapIO.writePNG(file, image, -1, true);
                var opposite = Gdx.files.local(root + "/"
                    + (options.uiProfile().fileStem().equals("mac") ? "windows" : "mac") + suffix + "/" + name);
                if (options.uiProfile().fileStem().equals("windows") && opposite.exists()) {
                    check(Arrays.equals(file.readBytes(), opposite.readBytes()), "Profile-dependent render: " + name);
                    compared++;
                }
            } finally { image.dispose(); }
            buffer.end();
        } finally { buffer.dispose(); }
    }

    private static void verifyResourceMeterBounds(AssetLoader assets) {
        var font = assets.gameplayFontSmall;
        float scaleX = font.getData().scaleX, scaleY = font.getData().scaleY;
        Color fontColor = new Color(font.getColor());
        var meter = new AbilityStateMeter();
        meter.setStates(List.of(new CodedAbilityState(
            "fixture-resource", "A Resource Name Much Longer Than Its Bar", 8, 8, true)));
        try {
            for (float textScale : new float[] {1f, 1.6f}) {
                font.getData().setScale(scaleX * textScale, scaleY * textScale);
                for (float width : new float[] {270f, 100f, 30f}) {
                    meter.setBounds(0f, 0f, width, 42f);
                    Color batchColor = new Color(Color.WHITE);
                    int[] glyphDraws = {0};
                    Batch recording = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                        new Class<?>[] {Batch.class}, (proxy, method, args) -> {
                            if (method.getName().equals("getColor")) return batchColor;
                            if (method.getName().equals("setColor")) {
                                if (args.length == 1) batchColor.set((Color) args[0]);
                                else batchColor.set((float) args[0], (float) args[1], (float) args[2], (float) args[3]);
                            }
                            if (method.getName().equals("draw") && args[1] instanceof float[] vertices
                                && args[0] == font.getRegion().getTexture()) {
                                glyphDraws[0]++;
                                int start = (int) args[2], end = start + (int) args[3];
                                for (int i = start; i < end; i += 5) {
                                    check(vertices[i] >= 0f && vertices[i] <= width,
                                        "Resource text escaped meter width " + width);
                                    check(vertices[i + 1] >= 0f && vertices[i + 1] <= 42f,
                                        "Resource text escaped meter height");
                                }
                            }
                            return null;
                        });
                    meter.draw(recording, assets.battleUi, font);
                    check(glyphDraws[0] > 0, "Resource value must remain visible");
                    check(font.getData().scaleX == scaleX * textScale
                        && font.getData().scaleY == scaleY * textScale, "Resource meter changed font scale");
                    check(font.getColor().equals(fontColor) && batchColor.equals(Color.WHITE),
                        "Resource meter changed drawing colors");
                }
            }
        } finally {
            font.getData().setScale(scaleX, scaleY);
        }
    }

    private static Graphics dimensions(Graphics real, int width, int height, int hdpi) {
        return (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
            new Class<?>[] {Graphics.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getWidth" -> width;
                case "getHeight" -> height;
                case "getBackBufferWidth" -> width * hdpi;
                case "getBackBufferHeight" -> height * hdpi;
                case "getDeltaTime", "getRawDeltaTime" -> 0f;
                default -> method.invoke(real, args);
            });
    }

    private static BattleState state(List<Move> moves, int count) {
        return new BattleState(
            BattleState.teamOfFighters(BattleTeamId.PLAYER, fighters(moves, count, "Player")),
            BattleState.teamOfFighters(BattleTeamId.ENEMY, fighters(moves, count, "Opponent")));
    }

    private static List<BattleCombatant> fighters(List<Move> moves, int count, String side) {
        CharacterStats stats = character().toCharacterStats();
        return IntStream.range(0, count).mapToObj(i -> new BattleCombatant(
            new SorcererCharacter(side + i, side + " " + (i + 1), stats, null, moves.subList(0, 8)))).toList();
    }

    private static CharacterData character() {
        CharacterData data = new CharacterData();
        data.id = "fixture";
        data.name = "Shared UI Fighter";
        data.type = "SORCERER";
        data.description = "A fixture with readable stats, a full move list, and technique information. "
            + "Both operating systems display the same design.";
        data.spriteAsset = "assets/sprites/characters/miwa_frontsprite.png";
        data.innateTechniqueName = "Focused Technique";
        data.vitality = data.strength = data.durability = data.speed = data.combatAbility = 100;
        data.cursedEnergyReserves = data.cursedEnergyEfficiency = data.cursedEnergyOutput = 100;
        data.jujutsuSkill = data.cursedTechniqueMastery = 100;
        return data;
    }

    private static List<Move> moves() {
        return IntStream.range(0, 18).mapToObj(i -> {
            MoveData data = new MoveData();
            data.id = "fixture-move-" + i;
            data.name = switch (i) {
                case 0 -> "Barrage";
                case 1 -> "Flower Offering: Life-Force Beam";
                case 2 -> "Don't Move";
                case 3 -> "Root Barrier";
                case 4 -> "Flowing Red Scale Dodge";
                default -> "Technique Move " + (i + 1);
            };
            data.description = switch (i) {
                case 0 -> "Strike three times with PHYSICAL CURSED ENERGY MELEE hits for 60 combined BASE POWER.";
                case 1 -> "Consume every FLOWER OFFERING charge and release 60 BASE POWER per charge as a Potency 3 RANGED GUARD BREAK.";
                case 2 -> "Command up to three enemies to halt. Success STAGGERs them for 6 AP TICKS and drops their EVASION to 0.";
                case 3 -> "Construct a barrier of roots that BLOCKs incoming damage for a short duration.";
                case 4 -> "DODGE MELEE attacks with a focused burst of movement.";
                default -> "A controlled attack. Select a target and place it on the timeline.";
            };
            data.tags = i == 2
                ? List.of("ATTACK", "CURSED_ENERGY", "AOE")
                : i == 3 || i == 4
                    ? List.of("DEFENSIVE", "PHYSICAL", "CURSED_ENERGY")
                    : List.of("ATTACK", "PHYSICAL", "CURSED_ENERGY");
            data.apCost = i == 0 || i == 1 ? 22 : i == 2 ? 7 : 10;
            data.unleashPoint = i == 0 ? 4 : i == 1 ? 17 : 1;
            data.baseCeCost = i == 0 ? 7 : i == 1 ? 72 : i == 2 ? 32 : 10;
            data.hasCeCost = true;
            if (i == 0) {
                data.baseAccuracy = 0.86;
                data.hitComponents = List.of(
                    previewHit(18, 0.86), previewHit(18, 0.86), previewHit(24, 0.86));
            } else if (i == 1) {
                data.baseAccuracy = 0.84;
                data.potency = 3;
                data.hitComponents = List.of(previewHit(60, 0.84));
            } else if (i == 2) {
                data.neverMiss = true;
                data.aoeType = "MULTIPLE";
                data.aoeTargetCount = 3;
                MoveData.HitComponentData hit = previewHit(0, 1.0);
                hit.tags = List.of("CURSED_ENERGY", "RANGED", "INTANGIBLE");
                data.hitComponents = List.of(hit);
            } else if (i == 3) {
                data.defenseType = "BLOCK";
                data.blockStyle = "FLAT";
                data.blockFlatReduction = 30;
                data.blockDuration = 8;
            } else if (i == 4) {
                data.defenseType = "DODGE";
                data.dodgeChance = 90;
                data.dodgeScope = "MELEE";
                data.blockDuration = 6;
            }
            return data.toMove();
        }).toList();
    }

    private static MoveData.HitComponentData previewHit(int power, double accuracy) {
        MoveData.HitComponentData hit = new MoveData.HitComponentData();
        hit.basePower = power;
        hit.baseAccuracy = accuracy;
        hit.tags = List.of("PHYSICAL", "CURSED_ENERGY", "MELEE");
        return hit;
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void set(Object owner, String name, Object value) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void invoke(Object owner, String name) { invoke(owner, name, new Class<?>[0]); }

    private static void invoke(Object owner, String name, Class<?>[] types, Object... args) {
        try {
            Method method = owner.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(owner, args);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
