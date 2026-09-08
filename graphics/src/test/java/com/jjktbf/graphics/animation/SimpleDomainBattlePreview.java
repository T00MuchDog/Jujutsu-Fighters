package com.jjktbf.graphics.animation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.launch.DesktopLaunchOptions;
import com.jjktbf.graphics.screens.BattleScreen;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.SeededRandomSource;
import com.jjktbf.model.domain.DomainData;
import com.jjktbf.model.domain.DomainDefinition;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Opt-in real-GL battle-screen validation, not a headless unit test.
 * Uses in-code combat fixtures, the real resolver and blocking BattleView event path.
 * Test-only reflection automates UI confirmations; no production automation hooks.
 */
public final class SimpleDomainBattlePreview extends JJKGame {
    private BattleScreen battle;
    private volatile Throwable failure;
    private volatile String capture;
    private volatile CountDownLatch captured;
    private volatile String opening;
    private float openingTime;
    private final String output;

    private SimpleDomainBattlePreview(DesktopLaunchOptions options, String output) {
        super(options);
        this.output = output;
        setOnCreatedAction(this::startPreview);
    }

    public static void main(String[] args) {
        DesktopLaunchOptions options = DesktopLaunchOptions.parse(args);
        String output = System.getProperty("jjktbf.preview.output", "graphics/target/simple-domain-preview");
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Simple Domain - Scripted Battle Validation");
        config.setWindowedMode(options.windowWidth(), options.windowHeight());
        config.setHdpiMode(HdpiMode.Logical);
        config.setForegroundFPS(60);
        config.disableAudio(true);
        SimpleDomainBattlePreview preview = new SimpleDomainBattlePreview(options, output);
        new Lwjgl3Application(preview, config);
        if (preview.failure != null) throw new AssertionError("Battle preview failed", preview.failure);
        System.out.println("PASS: both shared activations, three later rounds each, exact collapse, hostile coexistence; " + output);
    }

    private void startPreview() {
        battle = (BattleScreen) field(this, JJKGame.class, "battleScreen");
        battle.prepareLocal();
        setScreen(battle);
        Thread worker = new Thread(() -> {
            try {
                runScenarios();
            } catch (Throwable problem) {
                failure = problem;
                problem.printStackTrace();
            } finally {
                Gdx.app.postRunnable(() -> {
                    battle.hide();
                    check(((Map<?, ?>) field(field(battle, "domainBackdrops"), "activeDomains")).isEmpty(),
                        "hide must clear persistent instances");
                    Gdx.app.exit();
                });
            }
        }, "simple-domain-preview-battle");
        worker.setDaemon(true);
        battle.setLocalBattleThread(worker);
        worker.start();
    }

    @Override public String multiplayerSpriteAsset(String characterId) {
        if ("Simple Domain User".equals(characterId)) return "assets/sprites/characters/miwa_frontsprite.png";
        if ("Hostile Domain User".equals(characterId)) return "assets/sprites/characters/mahito_frontsprite.png";
        return super.multiplayerSpriteAsset(characterId);
    }

    @Override public void render() {
        try {
            if (battle != null) {
                if (Boolean.TRUE.equals(field(battle, "awaitingBattleStart"))) set(battle, "battleStartConfirmed", true);
                invoke(battle, "flushTypingImmediately");
            }
            super.render();
            if (battle == null) return;
            BattleAnimationPlayer animations = (BattleAnimationPlayer) field(battle, "battleAnimations");
            if (opening != null && animations.isPlaying()) {
                openingTime += Gdx.graphics.getDeltaTime();
                if (openingTime >= .3f) {
                    screenshot(opening + "-activation");
                    opening = null;
                    openingTime = 0;
                }
            }
            if (capture != null) {
                screenshot(capture);
                capture = null;
                captured.countDown();
            }
        } catch (Throwable problem) {
            failure = problem;
            problem.printStackTrace();
            Gdx.app.exit();
        }
    }

    private void runScenarios() throws Exception {
        Move simple = opening("000138", "Simple Domain", "000000");
        Move shadow = opening("000026", "New Shadow Style Simple Domain", "000000");
        Move hostile = opening("000150", "Hostile Domain Fixture", "000001");
        BattleCombatant player = fighter("Simple Domain User", List.of(simple, shadow), List.of());
        BattleCombatant enemy = fighter("Hostile Domain User", List.of(hostile, shadow), List.of("000001"));
        BattleState state = new BattleState(player, enemy);
        Map<String, DomainDefinition> definitions = Map.of("000000", domain(true), "000001", domain(false));
        CombatResolver resolver = new CombatResolver(new SeededRandomSource(1))
            .withDomainLookup(id -> Optional.ofNullable(definitions.get(id)));
        battle.awaitBattleStart(state);
        Thread.sleep(1300);
        battle.displayResolutionStart(state);
        capture("00-inactive");

        for (Move move : List.of(simple, shadow)) {
            opening = move.getId();
            resolve(state, resolver, player, move, enemy, null);
            check(state.domainBattlefield().activeDomains().size() == 1, "domain establishment");
            for (int round = 0; round < 3; round++) {
                resolve(state, resolver, player, null, enemy, null);
                check(state.domainBattlefield().activeDomains().size() == 1, "field survives later round");
            }
            Thread.sleep(1800);
            capture(move.getId() + "-three-rounds-later");
            assertVisualCount(1);
            collapse(state, player);
            capture(move.getId() + "-collapsed");
            assertVisualCount(0);
        }

        resolve(state, resolver, player, null, enemy, hostile);
        capture("hostile-backdrop");
        opening = "inside-hostile";
        resolve(state, resolver, player, simple, enemy, null);
        for (int round = 0; round < 3; round++) resolve(state, resolver, player, null, enemy, null);
        check(state.domainBattlefield().activeDomains().size() == 2, "hostile and anti-domain coexist");
        capture("inside-hostile-three-rounds-later");
        assertVisualCount(2);
        collapse(state, player);
        capture("inside-hostile-collapsed");
        assertVisualCount(1);
        check(state.domainBattlefield().activeDomains().get(0).definition().id().equals("000001"),
            "hostile domain must remain after Simple Domain collapses");

        collapse(state, enemy);
        resolve(state, resolver, player, simple, enemy, shadow);
        check(state.domainBattlefield().activeDomains().size() == 2, "independent owner-local instances");
        capture("both-owners-active");
        assertVisualCount(2);
        collapse(state, player);
        capture("opponent-only-active");
        assertVisualCount(1);
    }

    private void resolve(BattleState state, CombatResolver resolver, BattleCombatant player, Move playerMove,
                         BattleCombatant enemy, Move enemyMove) {
        plan(player, playerMove);
        plan(enemy, enemyMove);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        battle.displayResolutionStart(state);
        battle.displayCombatEvents(resolver.resolveRound(state), state);
        state.transitionTo(BattleState.Phase.ROUND_END);
        battle.displayCombatEvents(resolver.processRoundEnd(state), state);
        battle.displayRoundEnd(state);
    }

    private void collapse(BattleState state, BattleCombatant owner) {
        List<CombatEvent> events = state.domainBattlefield().onOwnerHitDamage(state, owner,
            owner.getMaxHp() / 4, null, 1);
        check(events.stream().anyMatch(e -> e.getType() == CombatEvent.Type.DOMAIN_COLLAPSED), "core collapse event");
        battle.displayCombatEvents(events, state);
    }

    private void capture(String label) throws Exception {
        captured = new CountDownLatch(1);
        capture = label;
        check(captured.await(15, TimeUnit.SECONDS), "screenshot timeout: " + label);
    }

    private void assertVisualCount(int expected) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Gdx.app.postRunnable(() -> {
            try {
                check(((Map<?, ?>) field(field(battle, "domainBackdrops"), "activeDomains")).size() == expected,
                    "persistent state count must match core");
            } catch (Throwable problem) { failure = problem; }
            finally { done.countDown(); }
        });
        check(done.await(15, TimeUnit.SECONDS), "render assertion timeout");
        if (failure != null) throw new AssertionError(failure);
    }

    private void screenshot(String label) {
        var directory = Gdx.files.local(output);
        directory.mkdirs();
        Pixmap image = Pixmap.createFromFrameBuffer(0, 0,
            Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try { PixmapIO.writePNG(directory.child(label + ".png"), image, -1, true); }
        finally { image.dispose(); }
        System.out.println("Captured " + label);
    }

    private static DomainDefinition domain(boolean anti) {
        DomainData data = new DomainData();
        data.id = anti ? "000000" : "000001";
        data.name = anti ? "Simple Domain" : "Hostile Domain Fixture";
        data.antiDomain = anti;
        data.burnoutRounds = 0;
        data.durationRounds = -1;
        data.internalBarrierIntegrity = 100000;
        if (anti) data.counterType = "TECHNIQUE_CONTACT_NULLIFICATION";
        return data.toDomain();
    }

    private static Move opening(String id, String name, String domain) {
        MoveEffectData effect = AbilityEffectType.ESTABLISH_DOMAIN.createDefaultMoveEffect();
        effect.domainId = domain;
        effect.trigger = "ON_FIRE";
        return new Move.Builder(id).name(name).category(MoveCategory.UTILITY)
            .apCost(1).unleashPoint(1).effects(List.of(effect)).build();
    }

    private static BattleCombatant fighter(String name, List<Move> moves, List<String> domains) {
        CharacterStats stats = new CharacterStats.Builder().vitality(100).strength(100)
            .durability(100).speed(100).cursedEnergyReserves(100).cursedEnergyEfficiency(100)
            .cursedEnergyOutput(100).jujutsuSkill(100).combatAbility(100).cursedTechniqueMastery(100).build();
        return new BattleCombatant(new SorcererCharacter(name, name, stats, null, moves)
            .withAccessibleDomains(domains));
    }

    private static void plan(BattleCombatant fighter, Move move) {
        BattlePlan plan = new BattlePlan(fighter.getMaxApBar(), fighter.getCurrentCe());
        if (move != null) check(plan.place(move, 1, 0) != null, "fixture plan placement");
        fighter.setTimeline(plan.toLegacyTimeline());
    }

    private static Object field(Object owner, String name) { return field(owner, owner.getClass(), name); }

    private static Object field(Object owner, Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void set(Object owner, String name, Object value) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void invoke(Object owner, String name) {
        try {
            Method method = owner.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
