package com.jjktbf.graphics.animation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.badlogic.gdx.math.Rectangle;
import com.jjktbf.AppPaths;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.launch.DesktopLaunchOptions;
import com.jjktbf.graphics.screens.BattleScreen;
import com.jjktbf.graphics.ui.CombatantPanel;
import com.jjktbf.model.character.AbilityEffectType;
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.BattlePlan;
import com.jjktbf.model.combat.BattleState;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.combat.CombatResolver;
import com.jjktbf.model.combat.RandomSource;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveEffectData;
import com.jjktbf.model.move.MoveEffectTrigger;
import com.jjktbf.model.move.MoveTag;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Opt-in, bounded real-LWJGL BattleScreen/core-resolver preview, not a JUnit test.
 * From the repository root, compile without packaging:
 * <pre>
 * mvn -pl graphics -am test-compile -DskipTests
 * java -XstartOnFirstThread -cp 'graphics/target/test-classes:graphics/target/classes:core/target/classes:graphics/target/graphics-1.4.1.jar' com.jjktbf.graphics.animation.BloodManipulationBattlePreview --windowed --width=1280 --height=720 --ui-profile=mac
 * </pre>
 * The last classpath entry must be an existing shaded jar (dependencies/natives only).
 * Packaging, if needed, must be coordinated separately. Omit -XstartOnFirstThread
 * off macOS and use the platform's classpath separator. Override jjktbf.preview.output
 * for captures; animation overrides use the normal jjktbf.animationsDir property.
 *
 * Creates and prints a fresh minimal temporary data root before JJKGame startup,
 * overriding authoring/data-root settings, so dirty bundled gameplay cannot load.
 * No authored gameplay values are used. IDs only select production animation bindings.
 * Both owners convert blood, wait three complete empty rounds, then spend compression.
 * A fixed successful accuracy roll and explicitly CE-free moves keep all four
 * contacts deterministic rather than allowing a seeded stream's later miss.
 * Each direction runs at the normal diagonal layout and at half its horizontal
 * center separation with both sprite centers at their original mean Y. Only sprite
 * and plate rectangles move, on the render thread; HUDs, sizes, and gameplay rows
 * are unchanged. These are geometry probes, not a multi-row team-battle test.
 *
 * The actual BattleScreen renders at 1/60 s, shortened to land on requested active
 * playback times. No seeking, replacement animation, or forced cleanup is used.
 * Expected final art: Convergence 24 x 50 ms; Piercing 18 x 40 ms, contact .24 s,
 * source sphere [0,.32), beam [.24,.36). PNG names/logs record actual elapsed time.
 * Piercing also captures the .24 s contact boundary. Live panel poses/foot anchors
 * must remain neutral through contact, then recoil in the correct mirrored direction;
 * Convergence must have no recipient, target visual, or opponent pose change.
 * Cleanup asserts identity and empty finite/persistent players, not pixel recognition
 * of an orb. Screenshots still need visual review. This does not exercise manual
 * planning, online replay, audio, or OS-specific rendering beyond the host desktop.
 * A 240-second process watchdog also bounds startup and blocking BattleView calls.
 */
public final class BloodManipulationBattlePreview extends JJKGame {
    private static final float STEP = 1f / 60f;
    private static final float EPSILON = .00001f;
    private static final String BLOOD = "BLOOD";
    private static final String COMPRESSION = "COMPRESSION";
    private final String output;
    private final Map<CombatantPanel, Rectangle[]> originalBounds = new IdentityHashMap<>();
    private BattleScreen battle;
    private BattleAnimationPlayer animations;
    private volatile Throwable failure;
    private volatile boolean completed;
    private boolean compact;
    private CaptureSequence pending;
    private String still;
    private CountDownLatch stillCaptured;

    private static final class CaptureSequence {
        final String label;
        final String effect;
        final float[] times;
        final boolean fromRight;
        Object playback;
        int next;
        boolean finished;

        CaptureSequence(String label, boolean piercing, boolean fromRight) {
            this.label = label;
            this.fromRight = fromRight;
            effect = piercing ? "ct-piercing-blood" : "ct-convergence";
            times = piercing ? new float[] {.12f, .24f, .245f, .29f, .4f} : new float[] {.15f, .55f, .9f};
        }
    }

    private static final class HitRandom implements RandomSource {
        @Override public int nextInt(int bound) { return 0; }
        @Override public double nextDouble() { return 0.0; }
        @Override public boolean nextBoolean() { return false; }
    }

    private BloodManipulationBattlePreview(DesktopLaunchOptions options, String output) {
        super(options);
        this.output = output;
        setOnCreatedAction(this::startPreview);
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch stopped = new CountDownLatch(1);
        Thread watchdog = new Thread(() -> {
            try {
                if (!stopped.await(240, TimeUnit.SECONDS)) {
                    System.err.println("FAIL: Blood Manipulation preview exceeded 240 seconds");
                    System.exit(1);
                }
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }, "blood-preview-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        try {
            Path root = Files.createTempDirectory("jjktbf-blood-preview-");
            System.setProperty(AppPaths.AUTHORING_SYSTEM_PROPERTY, "false");
            System.setProperty(AppPaths.DATA_ROOT_SYSTEM_PROPERTY, root.toString());
            for (String catalog : List.of("moves", "characters", "abilities", "techniques", "domains", "tools")) {
                Path directory = Files.createDirectories(root.resolve("data").resolve(catalog));
                Files.writeString(directory.resolve("all_" + catalog + ".json"), "[]\n");
            }
            Files.writeString(root.resolve("data/keyword_descriptions.json"), "[]\n");
            System.out.println("Isolated preview data (retained for diagnostics): " + root);
            DesktopLaunchOptions options = DesktopLaunchOptions.parse(args);
            String output = System.getProperty("jjktbf.preview.output", "graphics/target/blood-manipulation-preview");
            var config = new Lwjgl3ApplicationConfiguration();
            config.setTitle("Blood Manipulation - Scripted Battle Validation");
            config.setWindowedMode(options.windowWidth(), options.windowHeight());
            config.setHdpiMode(HdpiMode.Logical);
            config.setForegroundFPS(60);
            config.setResizable(false);
            config.disableAudio(true);
            var preview = new BloodManipulationBattlePreview(options, output);
            new Lwjgl3Application(preview, config);
            if (preview.failure != null) throw new AssertionError("Battle preview failed", preview.failure);
            check(preview.completed, "window closed before all scenarios completed");
            System.out.println("PASS: both directions at two geometries, three intervening rounds each, "
                + "timed frames, resource conversion/spend, identity and no persistent orb; " + output);
        } finally { stopped.countDown(); }
    }

    private void startPreview() {
        battle = (BattleScreen) field(this, JJKGame.class, "battleScreen");
        battle.prepareLocal();
        setScreen(battle);
        animations = (BattleAnimationPlayer) field(battle, "battleAnimations");
        Thread worker = new Thread(() -> {
            try {
                runScenarios();
                onRender(() -> {
                    assertIdle();
                    battle.hide();
                    assertIdle();
                    completed = true;
                });
            } catch (Throwable problem) { fail(problem); }
            finally { Gdx.app.postRunnable(() -> Gdx.app.exit()); }
        }, "blood-preview-battle");
        worker.setDaemon(true);
        battle.setLocalBattleThread(worker);
        worker.start();
    }

    @Override public String multiplayerSpriteAsset(String id) {
        if ("Blood Fixture Left".equals(id)) return "assets/sprites/characters/miwa_frontsprite.png";
        if ("Blood Fixture Right".equals(id)) return "assets/sprites/characters/mahito_frontsprite.png";
        return super.multiplayerSpriteAsset(id);
    }

    @Override public void render() {
        try {
            if (battle == null) { super.render(); return; }
            if (completed || failure != null) return;
            if (Boolean.TRUE.equals(field(battle, "awaitingBattleStart"))) set(battle, "battleStartConfirmed", true);
            set(battle, "fastForwardActive", false);
            invoke(battle, "flushTypingImmediately");
            positionPanels();
            Object active = field(animations, "active");
            CaptureSequence sample = pending;
            if (sample != null && sample.playback == null && active != null) {
                for (Object sequence : (List<?>) field(active, "sequences")) {
                    for (Object visual : (List<?>) field(sequence, "visuals")) {
                        BattleEffectPack.Effect effect = (BattleEffectPack.Effect) field(field(visual, "ref"), "effect");
                        if (!effect.id().equals(sample.effect)) continue;
                        sample.playback = active;
                        checkTimeline(active, sequence, effect, sample);
                        System.out.println("Playback " + sample.label + " duration=" + field(active, "duration")
                            + " impact=" + field(active, "impact"));
                        printGeometry();
                    }
                }
            }
            float delta = STEP;
            if (sample != null && sample.playback == active && active != null && sample.next < sample.times.length) {
                float elapsed = (float) field(active, "elapsed");
                float remaining = sample.times[sample.next] - elapsed;
                check(remaining >= -EPSILON, "missed animation sample: " + sample.label);
                delta = Math.min(delta, Math.max(0, remaining));
            }
            // Render the real screen once, with a controlled presentation delta, not Game's wall-time delta.
            battle.render(delta);
            if (sample != null && sample.playback != null && !sample.finished) {
                Object after = field(animations, "active");
                if (after == sample.playback) {
                    float elapsed = (float) field(after, "elapsed");
                    checkPoses(sample, elapsed);
                    if (sample.next < sample.times.length && Math.abs(elapsed - sample.times[sample.next]) <= EPSILON) {
                        screenshot(sample.label + String.format(Locale.ROOT, "-t%.3f", elapsed));
                        sample.next++;
                    }
                } else if (after != sample.playback) {
                    check(after == null, "sampled playback replaced before natural completion");
                    check(sample.next == sample.times.length, "animation ended before every capture: " + sample.label);
                    assertIdle();
                    screenshot(sample.label + "-complete");
                    sample.finished = true;
                }
            }
            if (still != null && ((List<?>) field(battle, "entranceAnimations")).isEmpty()) {
                assertIdle();
                screenshot(still);
                still = null;
                stillCaptured.countDown();
            }
        } catch (Throwable problem) {
            fail(problem);
            Gdx.app.exit();
        }
    }

    private void checkTimeline(Object playback, Object sequence, BattleEffectPack.Effect effect, CaptureSequence sample) {
        boolean piercing = effect.id().equals("ct-piercing-blood");
        close((float) field(playback, "elapsed"), 0, "capture must observe playback before its first update");
        close((float) field(playback, "duration"), piercing ? .72f : 1.2f, "final playback duration");
        check(effect.frameCount() == (piercing ? 18 : 24), "final sheet frame count: " + effect.id());
        close(effect.frameSeconds(), piercing ? .04f : .05f, "final sheet frame duration");
        check(!effect.loop(), "blood effects must be finite");
        Object source = ((Supplier<?>) field(sequence, "source")).get();
        Object target = ((Supplier<?>) field(sequence, "target")).get();
        check(source == field(battle, sample.fromRight ? "enemyPanel" : "playerPanel"), "actual source panel binding");
        check((boolean) field(sequence, "mirrored") == sample.fromRight, "actual playback mirroring");
        if (!piercing) {
            check(target == null, "Convergence must not bind a recipient panel");
            for (Object visual : (List<?>) field(sequence, "visuals")) {
                check(((String) field(visual, "placement")).startsWith("source"), "Convergence has only source-local visuals");
            }
            return;
        }
        check(target == field(battle, sample.fromRight ? "playerPanel" : "enemyPanel"), "actual Piercing recipient panel binding");
        close((float) field(playback, "impact"), .24f, "Piercing shot/contact");
        boolean sphere = false;
        boolean beam = false;
        for (Object visual : (List<?>) field(sequence, "visuals")) {
            String placement = (String) field(visual, "placement");
            if (placement.equals("source")) {
                close((float) field(visual, "start"), 0, "loaded sphere start");
                close((float) field(visual, "duration"), .32f, "loaded sphere lifetime");
                sphere = true;
            } else if (placement.equals("beam")) {
                close((float) field(visual, "start"), .24f, "beam start");
                close((float) field(visual, "duration"), .12f, "beam lifetime");
                beam = true;
            }
        }
        check(sphere && beam, "Piercing must include both loaded source sphere and beam");
    }

    private void checkPoses(CaptureSequence sample, float elapsed) {
        CombatantPanel source = (CombatantPanel) field(battle, sample.fromRight ? "enemyPanel" : "playerPanel");
        CombatantPanel target = (CombatantPanel) field(battle, sample.fromRight ? "playerPanel" : "enemyPanel");
        BattleChoreography.Pose sourcePose = animations.poseFor(source);
        BattleChoreography.Pose targetPose = animations.poseFor(target);
        float[] neutralFeet = target.spriteGroundAnchor(BattleChoreography.Pose.IDENTITY);
        float[] actualFeet = animations.transformedFeet(target);
        boolean piercing = sample.effect.equals("ct-piercing-blood");
        if (!piercing || elapsed <= .24f) {
            check(BattleChoreography.Pose.IDENTITY.equals(targetPose), "opponent must remain neutral: " + sample.label + " at " + elapsed);
            close(actualFeet[0], neutralFeet[0], "neutral target foot X");
            close(actualFeet[1], neutralFeet[1], "neutral target foot Y");
            if (piercing) check(BattleChoreography.Pose.IDENTITY.equals(sourcePose), "no source recoil before/at contact");
        } else if (elapsed >= .245f && elapsed <= .4f) {
            float direction = Math.signum(target.spriteCenterX() - source.spriteCenterX());
            check(direction == (sample.fromRight ? -1 : 1), "fixture firing geometry matches direction");
            check(targetPose.x() * direction > EPSILON && targetPose.rotation() * direction < -EPSILON,
                "actual target recoils away and rotates after contact: " + sample.label + " at " + elapsed);
            check((actualFeet[0] - neutralFeet[0]) * direction > EPSILON, "rendered target feet move away from source");
            check(sourcePose.x() * direction < -EPSILON, "source recoil opposes the shot");
        }
    }

    private void runScenarios() throws Exception {
        Move convergence = move(false);
        Move piercing = move(true);
        BattleCombatant left = fighter("Blood Fixture Left", List.of(convergence, piercing));
        BattleCombatant right = fighter("Blood Fixture Right", List.of(convergence, piercing));
        BattleState state = new BattleState(left, right);
        CombatResolver resolver = new CombatResolver(new HitRandom());
        battle.awaitBattleStart(state);
        battle.displayResolutionStart(state);
        capture("00-inactive");
        for (boolean shorter : new boolean[] {false, true}) {
            onRender(() -> compact = shorter);
            for (boolean fromRight : new boolean[] {false, true}) {
                BattleCombatant source = fromRight ? right : left;
                BattleCombatant target = fromRight ? left : right;
                String label = (shorter ? "short-level" : "default-diagonal") + (fromRight ? "-right-to-left" : "-left-to-right");
                int bloodBefore = resource(source, BLOOD);
                check(resource(source, COMPRESSION) == 0, "no initial compression");
                arm(label + "-convergence", false, fromRight);
                resolve(state, resolver, left, fromRight ? null : convergence, right, fromRight ? convergence : null);
                verifyCaptured();
                check(resource(source, BLOOD) == bloodBefore - 1, "Convergence consumes one blood");
                check(resource(source, COMPRESSION) == 1, "Convergence creates one compression");
                int laterRound = state.getRoundNumber();
                for (int round = 0; round < 3; round++) {
                    resolve(state, resolver, left, null, right, null);
                    check(resource(source, BLOOD) == bloodBefore - 1, "blood preserved through empty round");
                    check(resource(source, COMPRESSION) == 1, "compression preserved through empty round");
                    capture(label + "-empty-round-" + (round + 1));
                }
                check(state.getRoundNumber() == laterRound + 3, "three actual intervening rounds");
                check(resource(source, COMPRESSION) == 1, "compression still loaded immediately before spending");
                int hpBefore = target.getCurrentHp();
                arm(label + "-piercing", true, fromRight);
                resolve(state, resolver, left, fromRight ? null : piercing, right, fromRight ? piercing : null);
                verifyCaptured();
                check(resource(source, COMPRESSION) == 0, "Piercing consumes compression");
                check(resource(source, BLOOD) == bloodBefore - 1, "Piercing does not consume more blood");
                check(target.getCurrentHp() < hpBefore, "Piercing reaches its resolved recipient");
                check(!state.isBattleOver(), "fixture attack must not end the battle");
                capture(label + "-spent-idle");
            }
        }
    }

    private void resolve(BattleState state, CombatResolver resolver, BattleCombatant left, Move leftMove,
                         BattleCombatant right, Move rightMove) {
        state.transitionTo(BattleState.Phase.PLANNING);
        battle.displayCombatEvents(resolver.processRoundStart(state), state);
        plan(left, leftMove);
        plan(right, rightMove);
        state.transitionTo(BattleState.Phase.RESOLUTION);
        battle.displayResolutionStart(state);
        int leftCe = left.getCurrentCe();
        int rightCe = right.getCurrentCe();
        List<CombatEvent> events = resolver.resolveRound(state);
        check(left.getCurrentCe() == leftCe && right.getCurrentCe() == rightCe, "CE-free fixture must preserve CE");
        if (leftMove != null || rightMove != null) {
            int changed = -1;
            int fired = -1;
            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).getType() == CombatEvent.Type.RESOURCE_CHANGED && changed < 0) changed = i;
                if (events.get(i).getType() == CombatEvent.Type.MOVE_FIRED && fired < 0) fired = i;
            }
            check(changed >= 0 && fired > changed, "ON_START resource transaction precedes firing");
            Move selected = leftMove != null ? leftMove : rightMove;
            BattleCombatant source = leftMove != null ? left : right;
            BattleCombatant target = leftMove != null ? right : left;
            if (selected.getTags().contains(MoveTag.ATTACK)) {
                check(events.stream().anyMatch(event -> event.getType() == CombatEvent.Type.DAMAGE_DEALT
                    && event.getMove() == selected && event.getSource() == source && event.getTarget() == target),
                    "fixture must produce Piercing contact, CE=" + source.getCurrentCe() + ", events="
                        + events.stream().map(CombatEvent::getType).toList());
            } else {
                check(events.stream().filter(event -> event.getMove() == selected).allMatch(event -> event.getTarget() == null
                    || event.getTarget() == source), "Convergence must not resolve an opponent recipient");
            }
        }
        battle.displayCombatEvents(events, state);
        state.transitionTo(BattleState.Phase.ROUND_END);
        battle.displayCombatEvents(resolver.processRoundEnd(state), state);
        battle.displayRoundEnd(state);
    }

    private void arm(String label, boolean piercing, boolean fromRight) throws Exception {
        onRender(() -> {
            assertIdle();
            check(pending == null, "previous capture must be verified");
            pending = new CaptureSequence(label, piercing, fromRight);
        });
    }

    private void verifyCaptured() throws Exception {
        onRender(() -> {
            check(pending != null && pending.finished, "real move animation and every timed capture required");
            pending = null;
            assertIdle();
        });
    }

    private void capture(String label) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        onRender(() -> { stillCaptured = done; still = label; });
        check(done.await(15, TimeUnit.SECONDS), "screenshot timeout: " + label);
        if (failure != null) throw new AssertionError(failure);
    }

    private void onRender(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Gdx.app.postRunnable(() -> {
            try { if (failure == null) action.run(); }
            catch (Throwable problem) { fail(problem); }
            finally { done.countDown(); }
        });
        check(done.await(15, TimeUnit.SECONDS), "render-thread fence timeout");
        if (failure != null) throw new AssertionError(failure);
    }

    private void assertIdle() {
        check(!animations.isPlaying() && field(animations, "active") == null, "no finite sphere/beam remains");
        check(BattleChoreography.Pose.IDENTITY.equals(animations.backgroundPose()), "background identity");
        for (String name : List.of("playerPanel", "enemyPanel")) {
            CombatantPanel panel = (CombatantPanel) field(battle, name);
            check(panel != null, "fixture panel exists");
            check(BattleChoreography.Pose.IDENTITY.equals(animations.poseFor(panel)), name + " identity");
        }
        check(((Map<?, ?>) field(field(battle, "domainBackdrops"), "activeDomains")).isEmpty(),
            "compression must not create a persistent visual instance");
        check(((Set<?>) field(animations, "failedEffects")).isEmpty(), "no silently failed animation assets");
    }

    private void positionPanels() {
        CombatantPanel left = (CombatantPanel) field(battle, "playerPanel");
        CombatantPanel right = (CombatantPanel) field(battle, "enemyPanel");
        if (left == null || right == null) return;
        for (CombatantPanel panel : List.of(left, right)) {
            originalBounds.computeIfAbsent(panel, key -> new Rectangle[] {
                new Rectangle((Rectangle) field(key, "spriteBounds")), new Rectangle((Rectangle) field(key, "plateBounds"))
            });
        }
        Rectangle a = originalBounds.get(left)[0];
        Rectangle b = originalBounds.get(right)[0];
        float dx = (b.x + b.width / 2 - a.x - a.width / 2) / 4;
        float dy = (b.y + b.height / 2 - a.y - a.height / 2) / 2;
        for (CombatantPanel panel : List.of(left, right)) {
            float sign = panel == left ? 1 : -1;
            Rectangle[] original = originalBounds.get(panel);
            for (int i = 0; i < 2; i++) {
                Rectangle bounds = (Rectangle) field(panel, i == 0 ? "spriteBounds" : "plateBounds");
                bounds.set(original[i]);
                if (compact) { bounds.x += sign * dx; bounds.y += sign * dy; }
            }
        }
    }

    private void printGeometry() {
        CombatantPanel left = (CombatantPanel) field(battle, "playerPanel");
        CombatantPanel right = (CombatantPanel) field(battle, "enemyPanel");
        System.out.printf(Locale.ROOT, "Geometry: left=(%.3f,%.3f), right=(%.3f,%.3f), center distance=%.3f canvas units%n",
            left.spriteCenterX(), left.spriteCenterY(), right.spriteCenterX(), right.spriteCenterY(),
            Math.hypot(right.spriteCenterX() - left.spriteCenterX(), right.spriteCenterY() - left.spriteCenterY()));
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

    private static Move move(boolean piercing) {
        MoveEffectData effect = AbilityEffectType.TRANSACT_BOUNDED_RESOURCE.createDefaultMoveEffect();
        effect.effectId = "effect-000000";
        effect.trigger = MoveEffectTrigger.ON_START.name();
        effect.sourceResourceKey = piercing ? COMPRESSION : BLOOD;
        effect.sourceResourceAmount = 1;
        effect.targetResourceKey = piercing ? null : COMPRESSION;
        effect.targetResourceAmount = piercing ? 0 : 1;
        return new Move.Builder(piercing ? "000098" : "000097")
            .name(piercing ? "Piercing Blood" : "Convergence")
            .category(piercing ? MoveCategory.PHYSICAL : MoveCategory.UTILITY)
            .tags(piercing ? Set.of(MoveTag.ATTACK, MoveTag.RANGED) : Set.of(MoveTag.UTILITY))
            .baseCeCost(0).hasCeCost(false)
            .basePower(piercing ? 1 : 0).apCost(1).unleashPoint(1).effects(List.of(effect)).build();
    }

    private static BattleCombatant fighter(String name, List<Move> moves) {
        CharacterStats stats = new CharacterStats.Builder().vitality(100).strength(100)
            .durability(100).speed(100).cursedEnergyReserves(100).cursedEnergyEfficiency(100)
            .cursedEnergyOutput(100).jujutsuSkill(100).combatAbility(100).cursedTechniqueMastery(100).build();
        BattleCombatant fighter = new BattleCombatant(new SorcererCharacter(name, name, stats, null, moves));
        fighter.defineBoundedResource(BLOOD, "Blood", 3, 3);
        fighter.defineBoundedResource(COMPRESSION, "Compression", 1, 0);
        return fighter;
    }

    private static int resource(BattleCombatant fighter, String key) {
        return fighter.boundedResourceValue(key).orElseThrow();
    }

    private static void plan(BattleCombatant fighter, Move move) {
        BattlePlan plan = new BattlePlan(fighter.getMaxApBar(), fighter.getCurrentCe());
        if (move != null) check(plan.place(move, 1, 0) != null, "fixture plan placement");
        fighter.setTimeline(plan.toLegacyTimeline());
    }

    private void fail(Throwable problem) {
        if (failure == null) { failure = problem; problem.printStackTrace(); }
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

    private static void close(float actual, float expected, String message) {
        check(Math.abs(actual - expected) <= EPSILON, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
