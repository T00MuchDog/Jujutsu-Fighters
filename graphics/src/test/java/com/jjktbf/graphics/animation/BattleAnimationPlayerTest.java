package com.jjktbf.graphics.animation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.jjktbf.graphics.ui.CombatantPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class BattleAnimationPlayerTest {
    @TempDir
    Path root;

    private String oldAnimationsDirectory;
    private BattleAnimationPlayer player;
    private TextureLifecycle textures;
    private com.badlogic.gdx.Files previousFiles;
    private Graphics previousGraphics;
    private GL20 previousGl;
    private GL20 previousGl20;

    @BeforeEach
    void installHeadlessGdx() {
        previousFiles = Gdx.files;
        previousGraphics = Gdx.graphics;
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        oldAnimationsDirectory = System.getProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY);
        textures = new TextureLifecycle();
        GdxNativesLoader.load();
        GL20 gl = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class},
            (proxy, method, args) -> {
                if (method.getName().equals("glGenTexture")) return textures.generate();
                if (method.getName().equals("glGenTextures") && args != null && args.length == 2
                    && args[1] instanceof IntBuffer buffer) {
                    for (int i = 0; i < (Integer) args[0]; i++) buffer.put(i, textures.generate());
                    return null;
                }
                if (method.getName().equals("glDeleteTexture") && args != null && args.length == 1) {
                    textures.delete((Integer) args[0]);
                    return null;
                }
                if (method.getName().equals("glDeleteTextures") && args != null && args.length == 2
                    && args[1] instanceof IntBuffer buffer) {
                    for (int i = 0; i < (Integer) args[0]; i++) textures.delete(buffer.get(i));
                    return null;
                }
                if (method.getName().equals("glGetError")) return GL20.GL_NO_ERROR;
                if (method.getName().equals("glCheckFramebufferStatus")) return GL20.GL_FRAMEBUFFER_COMPLETE;
                if (method.getName().equals("glGetString")) return "";
                if (method.getName().equals("glGetIntegerv") && args != null && args.length == 2
                    && args[1] instanceof IntBuffer buffer) {
                    buffer.put(0, 16384);
                    return null;
                }
                return defaultValue(method.getReturnType());
            });
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
            new Class<?>[]{Graphics.class}, (proxy, method, args) -> {
                if (method.getName().equals("getGL20")) return gl;
                if (method.getName().equals("supportsExtension")) return false;
                return defaultValue(method.getReturnType());
            });
        Gdx.files = new com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files();
    }

    @AfterEach
    void restoreGlobalState() {
        if (player != null) player.dispose();
        Gdx.files = previousFiles;
        Gdx.graphics = previousGraphics;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        if (oldAnimationsDirectory == null) {
            System.clearProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY);
        } else {
            System.setProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY, oldAnimationsDirectory);
        }
    }

    @Test
    void routesMultihitContactsAndGatesImpactAndCompletion() throws IOException {
        writeFixture(root);
        player = loadedPlayer(root);
        CombatantPanel source = panel(10, 20, 40, 80);
        CombatantPanel target = panel(100, 20, 40, 80);

        assertFalse(player.play("MOVE_FIRED", "multi", null, () -> source, () -> target, false, false),
            "an attack activation must not play the complete multi-contact sheet");
        assertFalse(player.isPlaying());

        assertTrue(player.play("DAMAGE_DEALT", "multi", 1, () -> source, () -> target, false, false));
        BatchRecorder batch = new BatchRecorder();
        player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(1, batch.calls.size(), "one contact owns one visual draw");
        assertEquals(8, batch.calls.get(0).srcX,
            "component two starts at its isolated contact slice, not frame zero");
        assertEquals(0, batch.calls.get(0).srcY);
        assertTrue(player.isBeforeImpact());
        player.update(0.19f);
        assertTrue(player.isBeforeImpact());
        player.update(0.02f);
        assertFalse(player.isBeforeImpact(), "impact gate should open at the clip's impact");
        player.update(0.3f);
        assertFalse(player.isPlaying(), "the isolated contact should complete without hanging");
    }

    @Test
    void restrictsGuardActivationToItsPlacementAndDefenseGrant() throws IOException {
        writeFixture(root);
        player = loadedPlayer(root);
        CombatantPanel source = panel(10, 20, 40, 80);
        CombatantPanel target = panel(100, 20, 40, 80);

        assertTrue(player.play("MOVE_FIRED", "sourceGuard", null,
            () -> source, () -> target, false, false));
        player.update(0.1f);
        assertEquals(0.5f, player.poseFor(source).x(), 0.0001f);
        assertEquals(0f, player.poseFor(target).x(), 0.0001f,
            "source activation must not move the target");
        player.clear();

        assertFalse(player.play("MOVE_FIRED", "targetGuard", null,
            () -> source, () -> target, false, false));
        assertTrue(player.play("DEFENSE_GRANTED", "targetGuard", null,
            () -> source, () -> target, false, false));
        player.update(0.1f);
        assertEquals(0f, player.poseFor(source).x(), 0.0001f,
            "target defense activation must not move the source");
        assertEquals(-0.5f, player.poseFor(target).x(), 0.0001f);
    }

    @Test
    void guardWaitsForCollisionAndSharesItsImpactWithTheIncomingHit() throws IOException {
        writeFixture(root);
        player = loadedPlayer(root);
        CombatantPanel attacker = panel(10, 20, 40, 80);
        CombatantPanel defender = panel(100, 20, 40, 80);
        assertFalse(player.play("MOVE_FIRED", "guard", null, () -> defender, () -> null, true, false));
        assertFalse(player.play("DEFENSE_GRANTED", "guard", null, () -> attacker, () -> defender, false, false));
        assertFalse(player.isPlaying());

        for (String event : List.of("MOVE_BLOCKED", "MOVE_BLOCK_REDUCED", "MOVE_PARRIED",
            "DAMAGE_DEALT", "DAMAGE_IGNORED")) {
            assertTrue(player.play(event, "multi", 1, () -> attacker, () -> defender,
                false, false, "guard", false));
            player.update(0.19f);
            assertTrue(player.isBeforeImpact(), event);
            player.update(0.02f);
            assertFalse(player.isBeforeImpact(), event);
            BatchRecorder batch = new BatchRecorder();
            player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
            assertEquals(2, batch.calls.size(), event + " draws attack and guard in the same frame");
            DrawCall attack = batch.calls.get(0);
            DrawCall guard = batch.calls.get(1);
            assertNotEquals(attack.texture, guard.texture);
            assertEquals(4, attack.srcX, "second hit's contact frame, not the entire chain");
            assertEquals(4, attack.srcY);
            assertEquals(4, guard.srcX, "the guard reaches its own contact frame at the same time");
            assertEquals(0, guard.srcY);
            assertFalse(attack.flipX);
            assertTrue(guard.flipX, "the guard faces the incoming attack");
            assertEquals(0f, player.poseFor(defender).x(), 0.0001f, "blocked contacts must not use the damage flinch");
            assertEquals(0.8f, player.poseFor(defender).scaleY(), 0.0001f);
            player.update(0.20f);
            assertTrue(player.isPlaying(), "wait for the guard's longer tail, not just the attack clip");
            player.update(0.10f);
            assertFalse(player.isPlaying());
            assertEquals(BattleChoreography.Pose.IDENTITY, player.poseFor(defender));
        }
    }

    @Test
    void blockedContactKeepsAttackAndDefenseReinforcementIndependentAndSkippable() throws IOException {
        writeFixture(root);
        player = loadedPlayer(root);
        CombatantPanel attacker = panel(10, 20, 40, 80);
        CombatantPanel defender = panel(100, 20, 40, 80);
        for (boolean mirrored : List.of(false, true)) {
            for (boolean attackReinforced : List.of(false, true)) {
                for (boolean defenseReinforced : List.of(false, true)) {
                    player.play("MOVE_BLOCKED", "multi", 1, () -> attacker, () -> defender,
                        mirrored, attackReinforced, "guard", defenseReinforced);
                    player.update(0.21f);
                    BatchRecorder batch = new BatchRecorder();
                    player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
                    int attackDraws = attackReinforced ? 2 : 1;
                    assertEquals(attackDraws + (defenseReinforced ? 2 : 1), batch.calls.size());
                    for (int i = 0; i < batch.calls.size(); i++) {
                        assertEquals(i < attackDraws ? mirrored : !mirrored, batch.calls.get(i).flipX);
                    }
                    player.clear();
                    assertFalse(player.isPlaying());
                    assertFalse(player.isBeforeImpact());
                    assertEquals(BattleChoreography.Pose.IDENTITY, player.poseFor(defender));
                    batch.clear();
                    player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
                    assertTrue(batch.calls.isEmpty());
                }
            }
        }
    }

    @Test
    void fullBlockWithoutAnAuthoredGuardStillPlaysTheAttackAndReactionTogether() throws IOException {
        writeFixture(root);
        player = loadedPlayer(root);
        CombatantPanel attacker = panel(10, 20, 40, 80);
        CombatantPanel defender = panel(100, 20, 40, 80);
        assertTrue(player.play("MOVE_BLOCKED", "multi", 1, () -> attacker, () -> defender, false, false));
        player.update(0.21f);
        BatchRecorder batch = new BatchRecorder();
        player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(1, batch.calls.size());
        assertEquals(0.7f, player.poseFor(defender).scaleY(), 0.0001f);
    }

    @Test
    void drawsReinforcementOverlayAndGenericDodgeMissEvents() throws IOException {
        writeFixture(root);
        player = loadedPlayer(root);
        CombatantPanel source = panel(10, 20, 40, 80);
        CombatantPanel target = panel(100, 20, 40, 80);
        BatchRecorder batch = new BatchRecorder();

        assertTrue(player.play("REINFORCE", null, null, () -> source, () -> target, false, false));
        player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(1, batch.calls.size(), "the unreinforced baseline draws only its base sheet");
        Texture base = batch.calls.get(0).texture;
        batch.clear();

        assertTrue(player.play("REINFORCE", null, null, () -> source, () -> target, false, true));
        player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(2, batch.calls.size(), "reinforcement draws coating behind the base");
        assertNotEquals(batch.calls.get(0).texture, batch.calls.get(1).texture);
        assertEquals(base, batch.calls.get(1).texture);
        player.clear();
        batch.clear();

        assertTrue(player.play("DODGE", null, null, () -> source, () -> target, true, false));
        player.clear();
        assertTrue(player.play("MISS", null, null, () -> source, () -> target, false, false));
    }

    @Test
    void preservesMirrorSourceRectangleAndLayerTint() throws IOException {
        writeFixture(root);
        player = loadedPlayer(root);
        CombatantPanel source = panel(10, 20, 40, 80);
        CombatantPanel target = panel(100, 20, 40, 80);
        BatchRecorder batch = new BatchRecorder();

        assertTrue(player.play("TINTED", null, null, () -> source, () -> target, true, false));
        player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(1, batch.calls.size());
        DrawCall draw = batch.calls.get(0);
        assertEquals(0, draw.srcX);
        assertEquals(0, draw.srcY);
        assertTrue(draw.flipX && !draw.flipY, "mirroring flips only the X source axis");
        assertEquals(0.25f, draw.red, 0.0001f);
        assertEquals(0.5f, draw.green, 0.0001f);
        assertEquals(0.75f, draw.blue, 0.0001f);
    }

    @Test
    void drawsBeamAcrossTransformedCentersIncludingAProjectileLayer() throws IOException {
        writeFixture(root);
        player = loadedPlayer(root);
        CombatantPanel source = panel(10, 20, 40, 80);
        CombatantPanel target = panel(170, 50, 20, 120);

        assertFalse(player.play("MOVE_FIRED", "beamMove", null, () -> source, () -> target, false, false));
        assertFalse(player.play("DAMAGE_DEALT", "beamMove", null, () -> source, () -> target, false, false));
        assertTrue(player.play("DODGE", "beamMove", null, () -> source, () -> target, false, false),
            "a targeted attempt must not suppress subsequent generic outcome choreography");
        player.clear();
        assertTrue(player.handlesEvent("MOVE_TARGETED"));
        assertTrue(player.play("MOVE_TARGETED", "beamMove", null, () -> source, () -> target, false, false));
        BatchRecorder batch = new BatchRecorder();
        player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(2, batch.calls.size(), "primary beam and its path-placed layer draw together");

        DrawCall beam = batch.calls.get(0);
        assertEquals(115f, beam.centerX(), 0.0001f);
        assertEquals(100f, beam.centerY(), 0.0001f);
        assertEquals((float) Math.hypot(130, 80), beam.width, 0.0001f);
        assertEquals(100f, beam.height, 0.0001f, "beam thickness is average sprite height times size");
        assertEquals((float) Math.toDegrees(Math.atan2(80, 130)), beam.rotation, 0.0001f);
        assertEquals(BatchRecorder.INITIAL_PACKED_COLOR, batch.packedColor, 0f,
            "path rendering restores the caller's batch tint state");

        DrawCall projectile = batch.calls.get(1);
        assertEquals(50f, projectile.centerX(), 0.0001f);
        assertEquals(60f, projectile.centerY(), 0.0001f, "layer starts at the transformed source center");
        assertTrue(player.isBeforeImpact());
        player.update(0.11f);
        assertFalse(player.isBeforeImpact(), "targeted visuals use the same impact gate as other sequences");
        player.clear();
        assertEquals(BattleChoreography.Pose.IDENTITY, player.poseFor(source));
        assertEquals(BattleChoreography.Pose.IDENTITY, player.poseFor(target));
    }

    @Test
    void projectileTravelsTheActualRightToLeftPathWithoutTargetFallback() throws IOException {
        writeFixture(root);
        player = loadedPlayer(root);
        CombatantPanel source = panel(200, 30, 30, 100);
        CombatantPanel target = panel(20, 90, 50, 60);
        assertTrue(player.play("MOVE_TARGETED", "projectileMove", null, () -> source, () -> target, true, false));
        player.update(0.1f);
        BatchRecorder batch = new BatchRecorder();
        player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(1, batch.calls.size());
        DrawCall draw = batch.calls.get(0);
        assertEquals(172.5f, draw.centerX(), 0.0001f);
        assertEquals(90f, draw.centerY(), 0.0001f);
        assertEquals((float) Math.toDegrees(Math.atan2(40, -170)), draw.rotation, 0.0001f);
        assertFalse(draw.flipX, "path rotation must not be combined with a second horizontal mirror");

        player.clear();
        assertFalse(player.isPlaying());
        assertEquals(BattleChoreography.Pose.IDENTITY, player.poseFor(source));
        assertEquals(BattleChoreography.Pose.IDENTITY, player.poseFor(target));
        batch.clear();
        player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertTrue(batch.calls.isEmpty());

        assertTrue(player.play("MOVE_TARGETED", "projectileMove", null, () -> source, () -> null, true, false));
        assertTimeout(Duration.ofSeconds(1), () -> player.draw(
            batch.proxy, new Rectangle(0, 0, 320, 200), "front"));
        assertTrue(batch.calls.isEmpty(), "a missing target skips path rendering rather than using the source");
        player.clear();
        assertTrue(player.play("MOVE_TARGETED", "beamMove", null, () -> source, () -> source, false, false));
        assertTimeout(Duration.ofSeconds(1), () -> player.draw(
            batch.proxy, new Rectangle(0, 0, 320, 200), "front"));
        assertTrue(batch.calls.isEmpty(), "coincident endpoints are safely omitted");
    }

    @Test
    void clearStopsPlaybackAndReloadDisposesTextures() throws IOException {
        writeFixture(root);
        player = loadedPlayer(root);
        CombatantPanel source = panel(10, 20, 40, 80);
        CombatantPanel target = panel(100, 20, 40, 80);
        assertTrue(player.play("REINFORCE", null, null, () -> source, () -> target, false, true));
        player.draw(new BatchRecorder().proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(2, textures.generated.size());
        player.clear();
        assertFalse(player.isPlaying());
        assertTrue(textures.deleted.isEmpty(), "clear ends playback but keeps the reusable pack loaded");

        player.reload();
        assertEquals(2, textures.deleted.size(), "reload must release both loaded sheets");
    }

    @Test
    void bundledSheetsDecodeAndFitTheirDeclaredFrames() {
        // Asset contract smoke test, not an assertion about editable combat content.
        JsonValue catalog = new JsonReader().parse(Gdx.files.classpath("assets/animations/catalog.json"));
        for (JsonValue entry : catalog.require("packs")) {
            BattleEffectPack pack = new BattleEffectPack(Gdx.files.classpath("assets/animations/" + entry.asString()));
            try {
                for (BattleEffectPack.Effect effect : pack.effects()) {
                    pack.prepare(effect);
                    BatchRecorder batch = new BatchRecorder();
                    BattleEffectPack.Clip clip = pack.fullClip(effect);
                    assertTrue(pack.draw(batch.proxy, clip, Math.nextDown(clip.durationSeconds()),
                        100, 100, 192, true, true), effect.id());
                    assertFalse(batch.calls.isEmpty(), effect.id());
                }
            } finally {
                pack.dispose();
            }
        }
        assertEquals(textures.generated.size(), textures.deleted.size());
    }

    @Test
    void bundledSimpleDomainSharesOneActivationBindingWithoutLegacyIdsOrDuplicates() {
        System.clearProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY);
        player = new BattleAnimationPlayer();
        player.reload();
        JsonValue catalog = new JsonReader().parse(Gdx.files.classpath("assets/animations/catalog.json"));
        Set<String> boundMoves = new HashSet<>();
        Set<String> domainMoves = new HashSet<>();
        Set<String> oldIds = Set.of("simple-domain", "new-shadow-style-simple-domain");

        for (JsonValue entry : catalog.require("packs")) {
            JsonValue manifest = new JsonReader().parse(Gdx.files.classpath(
                "assets/animations/" + entry.asString() + "/manifest.json"));
            for (JsonValue effect : manifest.require("effects")) {
                assertFalse(oldIds.contains(effect.getString("id")), effect.getString("id"));
                JsonValue moves = effect.get("moveIds");
                if (moves == null) continue;
                for (JsonValue move : moves) {
                    String moveId = move.asString();
                    if (!moveId.equals("000138") && !moveId.equals("000026")) continue;
                    assertTrue(boundMoves.add(moveId), "duplicate move binding: " + moveId);
                    domainMoves.add(moveId);
                    assertEquals("simple-domain-establish", effect.getString("id"));
                }
            }
        }

        JsonValue choreography = new JsonReader().parse(
            Gdx.files.classpath("assets/animations/choreography.json"));
        for (String oldId : oldIds) {
            assertFalse(choreography.require("effects").has(oldId), oldId);
        }
        assertEquals(Set.of("000138", "000026"), domainMoves);
    }

    @Test
    void bundledDomainActivationUsesTheEstablishedFlagAndSharesSheetTimingForBothMoves() {
        System.clearProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY);
        player = new BattleAnimationPlayer();
        player.reload();
        CombatantPanel source = panel(20, 30, 45, 100);
        CombatantPanel target = panel(260, 80, 40, 80);
        Rectangle viewport = new Rectangle(0, 0, 640, 360);
        DrawCall expectedFront = null;
        DrawCall expectedBehind = null;

        for (String moveId : List.of("000138", "000026")) {
            assertFalse(player.play("MOVE_FIRED", moveId, null, () -> source, () -> target,
                false, false, null, false, false), moveId + " needs lifecycle confirmation");
            assertFalse(player.play("DAMAGE_DEALT", moveId, null, () -> source, () -> target,
                false, false, null, false, true), moveId + " only establishes on fire");
            assertTrue(player.play("MOVE_FIRED", moveId, null, () -> source, () -> target,
                moveId.equals("000026"), false, null, false, true), moveId);

            BatchRecorder global = new BatchRecorder();
            player.draw(global.proxy, viewport, "behind");
            player.draw(global.proxy, viewport, "front");
            assertTrue(global.calls.isEmpty(), "global drawing must omit foot layers");

            player.update(0.4f);
            BatchRecorder behind = new BatchRecorder();
            BatchRecorder front = new BatchRecorder();
            player.drawOwner(behind.proxy, source, "behind");
            player.drawOwner(front.proxy, source, "front");
            assertEquals(1, behind.calls.size(), moveId + " behind layer");
            assertEquals(1, front.calls.size(), moveId + " front layer");
            assertFalse(behind.calls.get(0).flipX, "ground wave orientation must match persistence on either side");
            assertFalse(front.calls.get(0).flipX, "rotating waves must not reverse at the handoff");
            if (expectedBehind == null) {
                expectedBehind = behind.calls.get(0);
                expectedFront = front.calls.get(0);
            } else {
                assertSame(expectedBehind.texture, behind.calls.get(0).texture);
                assertEquals(expectedBehind.srcX, behind.calls.get(0).srcX);
                assertEquals(expectedBehind.srcY, behind.calls.get(0).srcY);
                assertSame(expectedFront.texture, front.calls.get(0).texture);
                assertEquals(expectedFront.srcX, front.calls.get(0).srcX);
                assertEquals(expectedFront.srcY, front.calls.get(0).srcY);
            }
            assertTrue(player.isPlaying(), moveId + " has the shared activation lifetime");
            player.update(0.81f);
            assertFalse(player.isPlaying(), moveId + " completes at the shared timing");
        }
    }

    @Test
    void parriedIncomingMultiWithDomainDefenseDoesNotReplayEstablishment() throws IOException {
        writeDomainFixture(root);
        player = loadedDomainPlayer(root);
        CombatantPanel attacker = panel(10, 20, 40, 80);
        CombatantPanel defender = panel(100, 20, 40, 80);

        assertTrue(player.play("MOVE_FIRED", "000138", null, () -> defender, () -> null,
            false, false, null, false, true));
        BatchRecorder establishment = new BatchRecorder();
        player.drawOwner(establishment.proxy, defender, "front");
        assertEquals(1, establishment.calls.size());
        Texture establishmentTexture = establishment.calls.get(0).texture;
        player.clear();

        assertTrue(player.play("MOVE_PARRIED", "multi", 1, () -> attacker, () -> defender,
            false, false, "000138", false));
        BatchRecorder parried = new BatchRecorder();
        player.draw(parried.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(1, parried.calls.size(), "the incoming contact plays without re-establishing the domain");
        assertNotEquals(establishmentTexture, parried.calls.get(0).texture);
    }

    @Test
    void footLayersUseTranslatedPoseAndCurrentPanelWhileGlobalDrawOmitsThem() throws IOException {
        writeDomainFixture(root);
        player = loadedDomainPlayer(root);
        var current = new java.util.concurrent.atomic.AtomicReference<>(panel(10, 20, 40, 80));
        CombatantPanel old = current.get();
        assertTrue(player.play("MOVE_FIRED", "000138", null, current::get, () -> null,
            false, false, null, false, true));
        player.update(0.1f);

        BatchRecorder global = new BatchRecorder();
        player.draw(global.proxy, new Rectangle(0, 0, 320, 200), "behind");
        player.draw(global.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertTrue(global.calls.isEmpty());

        BatchRecorder initialBehind = new BatchRecorder();
        BatchRecorder initialFront = new BatchRecorder();
        player.drawOwner(initialBehind.proxy, old, "behind");
        player.drawOwner(initialFront.proxy, old, "front");
        assertEquals(1, initialBehind.calls.size());
        assertEquals(1, initialFront.calls.size());
        assertEquals(70f, initialFront.calls.get(0).centerX(), 0.0001f,
            "the source translation is applied at the foot pivot");

        current.set(panel(20, 30, 80, 160));
        assertEquals(BattleChoreography.Pose.IDENTITY, player.poseFor(old));
        BatchRecorder relaidOut = new BatchRecorder();
        player.drawOwner(relaidOut.proxy, old, "front");
        assertTrue(relaidOut.calls.isEmpty(), "the old panel is no longer the active owner");
        player.drawOwner(relaidOut.proxy, current.get(), "front");
        assertEquals(1, relaidOut.calls.size());
        assertEquals(140f, relaidOut.calls.get(0).centerX(), 0.0001f);
        assertEquals(30f, relaidOut.calls.get(0).centerY(), 0.0001f);
    }

    @Test
    void bundledBindingsPlayWithTheirProfilesAndRestoreTheScene() {
        System.clearProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY);
        player = new BattleAnimationPlayer();
        player.reload();
        CombatantPanel source = panel(20, 30, 45, 100);
        CombatantPanel target = panel(260, 80, 40, 80);
        Rectangle viewport = new Rectangle(0, 0, 640, 360);
        JsonValue catalog = new JsonReader().parse(Gdx.files.classpath("assets/animations/catalog.json"));
        for (JsonValue entry : catalog.require("packs")) {
            JsonValue manifest = new JsonReader().parse(Gdx.files.classpath(
                "assets/animations/" + entry.asString() + "/manifest.json"));
            for (JsonValue effect : manifest.require("effects")) {
                JsonValue moves = effect.get("moveIds");
                if (moves == null) continue;
                for (JsonValue move : moves) {
                    String id = move.asString();
                    String role = effect.getString("role",
                        effect.getString("placement").equals("target") ? "attack" : "utility");
                    String event = switch (role) {
                        case "attack" -> "DAMAGE_DEALT";
                        case "guard" -> "MOVE_BLOCKED";
                        case "targeted" -> "MOVE_TARGETED";
                        default -> effect.getString("placement").equals("target")
                            ? "DEFENSE_GRANTED" : "MOVE_FIRED";
                    };
                    assertTrue(player.hasMove(id), effect.getString("id"));
                    for (boolean mirrored : List.of(false, true)) {
                        if (effect.has("castEffect")) {
                            assertTrue(player.play("MOVE_FIRED", id, null,
                                () -> source, () -> null, mirrored, false), id + " cast");
                            player.update(0.15f);
                            BatchRecorder cast = new BatchRecorder();
                            player.draw(cast.proxy, viewport, "front");
                            assertFalse(cast.calls.isEmpty(), id + " must draw its cast without a target");
                            player.clear();
                        }
                        assertTrue(player.play(event, role.equals("guard") ? null : id, 0,
                            () -> source, () -> target, mirrored, false,
                            role.equals("guard") ? id : null, false, role.equals("domain")), id);
                        int draws = 0;
                        for (int step = 0; step < 1200 && player.isPlaying(); step++) {
                            BatchRecorder batch = new BatchRecorder();
                            for (String plane : List.of("background", "behind", "front")) {
                                player.draw(batch.proxy, viewport, plane);
                                player.drawOwner(batch.proxy, source, plane);
                                player.drawOwner(batch.proxy, target, plane);
                            }
                            for (DrawCall call : batch.calls) {
                                assertTrue(Float.isFinite(call.centerX()) && Float.isFinite(call.centerY())
                                    && Float.isFinite(call.rotation) && call.width > 0 && call.height > 0, id);
                            }
                            draws += batch.calls.size();
                            player.update(0.05f);
                        }
                        assertTrue(draws > 0, id + " must draw its bound sheet");
                        assertFalse(player.isPlaying(), id + " must complete");
                        assertEquals(BattleChoreography.Pose.IDENTITY, player.poseFor(source), id);
                        assertEquals(BattleChoreography.Pose.IDENTITY, player.poseFor(target), id);
                        assertEquals(BattleChoreography.Pose.IDENTITY, player.backgroundPose(), id);
                    }
                }
            }
        }
    }

    @Test
    void paddedSpritesAnchorAtVisibleSolesAndSizeFieldsFromVisibleHeight() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y <= 4; y++) for (int x = 2; x <= 5; x++) image.setRGB(x, y, 0xffffffff);
        Path file = root.resolve("padded-fighter.png");
        ImageIO.write(image, "png", file.toFile());
        Texture texture = new Texture(Gdx.files.absolute(file.toString()));
        try {
            CombatantPanel panel = new CombatantPanel(texture, null, null, new Rectangle(),
                new Rectangle(10, 20, 80, 80), new Rectangle(0, 0, 100, 100), 1, false);
            assertEquals(40, panel.spriteContentHeight());
            float[] soles = panel.spriteGroundAnchor(BattleChoreography.Pose.IDENTITY);
            assertEquals(50, soles[0]);
            assertEquals(50, soles[1]);
            panel.setSizeMultiplier(2);
            panel.snapAnimations();
            assertEquals(80, panel.spriteContentHeight());
            soles = panel.spriteGroundAnchor(new BattleChoreography.Pose(.25f, .5f, 1, 1, 90, 1, 1, 1, 1));
            assertEquals(10, soles[0], .0001f, "visible soles rotate around the translated canvas pivot");
            assertEquals(60, soles[1], .0001f);
        } finally { texture.dispose(); }
    }

    @Test
    void resolvesCurrentPanelsAfterRelayoutAndRestoresPosesOnClear() throws IOException {
        writeFixture(root);
        player = loadedPlayer(root);
        var current = new java.util.concurrent.atomic.AtomicReference<>(panel(10, 20, 40, 80));
        CombatantPanel old = current.get();
        player.play("MOVE_FIRED", "sourceGuard", null, current::get, () -> null, true, false);
        player.update(0.1f);
        assertEquals(-0.5f, player.poseFor(old).x(), 0.0001f);
        current.set(panel(20, 30, 80, 160));
        assertEquals(BattleChoreography.Pose.IDENTITY, player.poseFor(old));
        assertEquals(-0.5f, player.poseFor(current.get()).x(), 0.0001f);
        current.set(null);
        assertTimeout(Duration.ofSeconds(1), () -> player.draw(
            new BatchRecorder().proxy, new Rectangle(0, 0, 640, 400), "front"));
        player.clear();
        assertFalse(player.isBeforeImpact());
        assertEquals(BattleChoreography.Pose.IDENTITY, player.poseFor(old));
        assertEquals(BattleChoreography.Pose.IDENTITY, player.backgroundPose());
    }

    @Test
    void playsMoveCastOnlyOnFireAndPreservesTargetAndDamageRouting() throws IOException {
        writeCastFixture(root, "cast");
        player = loadedCastPlayer(root);
        CombatantPanel source = panel(10, 20, 40, 80);
        CombatantPanel target = panel(100, 20, 40, 80);
        BatchRecorder batch = new BatchRecorder();

        assertTrue(player.play("MOVE_FIRED", "castMove", null,
            () -> source, () -> target, false, false));
        player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(1, batch.calls.size(), "MOVE_FIRED draws the cast, not the main attack");
        Texture castTexture = batch.calls.get(0).texture;
        assertEquals(0.5f, player.poseFor(source).x(), 0.0001f,
            "cast profile applies to the source");
        assertEquals(BattleChoreography.Pose.IDENTITY, player.poseFor(target));
        player.clear();
        batch.clear();

        assertFalse(player.play("MOVE_TARGETED", "castMove", null,
            () -> source, () -> target, false, false),
            "a cast must not recast during target resolution");
        assertTrue(player.play("DAMAGE_DEALT", "castMove", null,
            () -> source, () -> target, false, false));
        player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(1, batch.calls.size(), "damage keeps the main attack visual");
        assertNotEquals(castTexture, batch.calls.get(0).texture);
        player.clear();
        batch.clear();

        assertTrue(player.play("MOVE_FIRED", "targetedMove", null,
            () -> source, () -> null, false, false));
        player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(castTexture, batch.calls.get(0).texture);
        player.clear();
        batch.clear();
        for (int recipient = 0; recipient < 3; recipient++) {
            assertTrue(player.play("MOVE_TARGETED", "targetedMove", null,
                () -> source, () -> target, false, false));
            player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
            assertEquals(1, batch.calls.size(), "each target gets the command, not another cast");
            assertNotEquals(castTexture, batch.calls.get(0).texture);
            player.clear();
            batch.clear();
        }
        assertFalse(player.play("DAMAGE_DEALT", "targetedMove", null,
            () -> source, () -> source, false, false), "self recoil must not replay the command");

        assertTrue(player.play("DAMAGE_DEALT", "hitMove", null,
            () -> source, () -> target, false, false));
        player.draw(batch.proxy, new Rectangle(0, 0, 320, 200), "front");
        assertEquals(1, batch.calls.size(), "damage keeps attack routing");
        assertNotEquals(castTexture, batch.calls.get(0).texture);
    }

    @Test
    void rejectsCatalogWhenCastEffectIsUnknown() throws IOException {
        writeCastFixture(root, "missing-cast");
        System.setProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY, root.toString());
        player = new BattleAnimationPlayer();
        assertTimeout(Duration.ofSeconds(1), player::reload);
        assertFalse(player.hasMove("castMove"));
        assertFalse(player.play("MOVE_FIRED", "castMove", null,
            () -> null, () -> null, false, false));
    }

    @Test
    void rejectsTargetPlacedCastAndFallsBackAfterCastTextureFailure() throws IOException {
        writeCastFixture(root, "main");
        System.setProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY, root.toString());
        player = new BattleAnimationPlayer();
        player.reload();
        assertFalse(player.hasMove("castMove"), "fire cannot position a target-only cast");

        writeCastFixture(root, "cast");
        Files.writeString(root.resolve("cast-pack/cast.png"), "invalid image data");
        player.reload();
        assertTrue(player.hasMove("castMove"), "images load lazily");
        assertFalse(player.play("MOVE_FIRED", "castMove", null,
            () -> panel(0, 0, 40, 80), () -> null, false, false));
        assertFalse(player.hasMove("castMove"), "failed cast permits the existing icon fallback");
        assertFalse(player.play("MOVE_FIRED", "castMove", null,
            () -> null, () -> null, false, false), "failed texture is not retried every cast");
    }

    @Test
    void fallsBackPromptlyForMalformedCatalogAndMissingSheet() throws IOException {
        Path malformed = root.resolve("malformed");
        Files.createDirectories(malformed);
        Files.writeString(malformed.resolve("catalog.json"), "not json");
        System.setProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY, malformed.toString());
        player = new BattleAnimationPlayer();
        assertTimeout(Duration.ofSeconds(1), player::reload);
        assertFalse(player.hasMove("multi"));
        assertFalse(player.play("MOVE_FIRED", "multi", null, () -> null, () -> null, false, false));

        Path missing = root.resolve("missing");
        Files.createDirectories(missing.resolve("pack"));
        Files.writeString(missing.resolve("catalog.json"), """
            {"schemaVersion":1,"packs":["pack"]}
            """);
        Files.writeString(missing.resolve("pack/manifest.json"), manifest("missing.png", false));
        Files.writeString(missing.resolve("choreography.json"), choreography());
        System.setProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY, missing.toString());
        assertTimeout(Duration.ofSeconds(1), player::reload);
        assertFalse(player.hasMove("multi"));
        assertFalse(player.isPlaying());
    }

    private BattleAnimationPlayer loadedPlayer(Path directory) {
        System.setProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY, directory.toString());
        player = new BattleAnimationPlayer();
        player.reload();
        assertTrue(player.hasMove("multi"));
        return player;
    }

    private BattleAnimationPlayer loadedDomainPlayer(Path directory) {
        System.setProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY, directory.toString());
        player = new BattleAnimationPlayer();
        player.reload();
        assertTrue(player.hasMove("000138"));
        assertTrue(player.hasMove("000026"));
        return player;
    }

    private BattleAnimationPlayer loadedCastPlayer(Path directory) {
        System.setProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY, directory.toString());
        player = new BattleAnimationPlayer();
        player.reload();
        assertTrue(player.hasMove("castMove"));
        return player;
    }

    private static CombatantPanel panel(float x, float y, float width, float height) {
        return new CombatantPanel(null, null, null, new Rectangle(),
            new Rectangle(x, y, width, height), new Rectangle(0, 0, 100, 100), 1f, false);
    }

    private static void writeFixture(Path root) throws IOException {
        Path pack = root.resolve("pack");
        Files.createDirectories(pack);
        writePng(pack.resolve("sheet.png"), 12, 8);
        writePng(pack.resolve("coating.png"), 12, 8);
        writePng(pack.resolve("spark.png"), 12, 8);
        writePng(pack.resolve("guard.png"), 12, 8);
        writePng(pack.resolve("guard-coating.png"), 12, 8);
        Files.writeString(root.resolve("catalog.json"), """
            {"schemaVersion":1,"packs":["pack"]}
            """);
        Files.writeString(pack.resolve("manifest.json"), manifest("sheet.png", true));
        Files.writeString(root.resolve("choreography.json"), choreography());
    }

    private static void writeDomainFixture(Path root) throws IOException {
        writeFixture(root);
        Path pack = root.resolve("pack");
        writePng(pack.resolve("domain-front.png"), 12, 8);
        writePng(pack.resolve("domain-back.png"), 12, 8);
        String manifest = manifest("sheet.png", true);
        String domainEffects = """
                  ,{"id":"domain","sheet":"domain-front.png","frameCount":2,"frameDurationMs":100,"loop":false,
                    "anchor":[0.5,0.5],"placement":"source-feet","role":"domain","moveIds":["000138","000026"]}
                  ,{"id":"domain-back","sheet":"domain-back.png","frameCount":2,"frameDurationMs":100,"loop":false,
                    "anchor":[0.5,0.5],"placement":"source-feet","role":"layer"}
""";
        int effectsEnd = manifest.lastIndexOf(']');
        Files.writeString(pack.resolve("manifest.json"), manifest.substring(0, effectsEnd)
            + domainEffects + manifest.substring(effectsEnd));
        String choreography = choreography()
            .replace("\"profiles\":{", "\"profiles\":{\n"
                + "                \"domain\":{\"durationSeconds\":0.2,\"impactSeconds\":0.1,\"size\":1,"
                + "\"source\":[{\"at\":0,\"x\":0.5}],\"layers\":[{\"effect\":\"domain-back\","
                + "\"placement\":\"source-feet\",\"plane\":\"behind\",\"durationSeconds\":0.2,\"size\":1}]},")
            .replace("\"effects\":{\"source-guard\"", "\"effects\":{\"domain\":\"domain\",\"domain-back\":\"domain\",\"source-guard\"")
            .replace("\"roles\":{\"attack\"", "\"roles\":{\"domain\":\"domain\",\"attack\"");
        Files.writeString(root.resolve("choreography.json"), choreography);
    }

    private static void writeCastFixture(Path root, String castEffect) throws IOException {
        Path pack = root.resolve("pack");
        Path castPack = root.resolve("cast-pack");
        Files.createDirectories(pack);
        Files.createDirectories(castPack);
        for (String sheet : List.of("main.png", "targeted.png", "hit.png", "fallback.png")) {
            writePng(pack.resolve(sheet), 12, 8);
        }
        writePng(castPack.resolve("cast.png"), 12, 8);
        Files.writeString(root.resolve("catalog.json"), """
            {"schemaVersion":1,"packs":["pack","cast-pack"]}
            """);
        Files.writeString(pack.resolve("manifest.json"), castManifest(castEffect));
        Files.writeString(castPack.resolve("manifest.json"), """
            {"schemaVersion":1,"frameWidth":4,"frameHeight":4,"columns":3,
             "sheetOrder":"row-major-top-left","effects":[
              {"id":"cast","sheet":"cast.png","frameCount":2,"frameDurationMs":100,
               "loop":false,"anchor":[0.5,0.5],"placement":"source","role":"utility"}
             ]}
            """);
        Files.writeString(root.resolve("choreography.json"), castChoreography());
    }

    private static String castManifest(String castEffect) {
        return """
            {"schemaVersion":1,"frameWidth":4,"frameHeight":4,"columns":3,
             "sheetOrder":"row-major-top-left","effects":[
              {"id":"main","sheet":"main.png","frameCount":2,"frameDurationMs":100,
               "loop":false,"anchor":[0.5,0.5],"placement":"target","role":"attack",
               "moveIds":["castMove"],"castEffect":"%s"},
              {"id":"targeted","sheet":"targeted.png","frameCount":2,"frameDurationMs":100,
               "loop":false,"anchor":[0.5,0.5],"placement":"target","role":"targeted",
                "moveIds":["targetedMove"],"castEffect":"%s"},
              {"id":"hit","sheet":"hit.png","frameCount":2,"frameDurationMs":100,
               "loop":false,"anchor":[0.5,0.5],"placement":"target","role":"attack",
               "moveIds":["hitMove"]},
              {"id":"fallback","sheet":"fallback.png","frameCount":2,"frameDurationMs":100,
               "loop":false,"anchor":[0.5,0.5],"placement":"source","role":"utility",
               "eventTypes":["MOVE_FIRED"]}
             ]}
            """.formatted(castEffect, castEffect);
    }

    private static String castChoreography() {
        return """
            {"schemaVersion":1,
             "profiles":{
              "cast":{"durationSeconds":0.2,"impactSeconds":0.1,
                "source":[{"at":0,"x":0.5}]},
              "attack":{"durationSeconds":0.2,"impactSeconds":0.1,
                "target":[{"at":0,"scaleY":0.7}]},
              "targeted":{"durationSeconds":0.2,"impactSeconds":0.1,
                "target":[{"at":0,"x":0.4}]},
              "fallback":{"durationSeconds":0.2,"impactSeconds":0.1}
             },
             "effects":{"cast":"cast","main":"attack","targeted":"targeted","hit":"attack",
               "fallback":"fallback"},
             "events":{"MOVE_FIRED":"fallback"},
             "roles":{"attack":"attack","targeted":"targeted","utility":"fallback"}}
            """;
    }

    private static String manifest(String multiSheet, boolean complete) {
        String optionalSheet = complete ? "sheet.png" : multiSheet;
        return """
            {
              "schemaVersion":1,"frameWidth":4,"frameHeight":4,"columns":3,
              "sheetOrder":"row-major-top-left","effects":[
                {"id":"multi","sheet":"%s","frameCount":6,"frameDurationMs":100,"loop":false,
                  "anchor":[0.5,0.5],"placement":"target","role":"attack","moveIds":["multi"],
                  "impactFrames":[1,4],
                  "reinforcement":{"type":"cursed-energy-coating","sheet":"coating.png",
                    "frameCount":6,"frameDurationMs":100,"drawOrder":"behind"}},
                {"id":"guard","sheet":"guard.png","frameCount":4,"frameDurationMs":100,"loop":false,
                 "anchor":[0.5,0.5],"placement":"source","role":"guard","moveIds":["guard"],
                 "impactFrames":[1],
                 "reinforcement":{"type":"cursed-energy-coating","sheet":"guard-coating.png",
                    "frameCount":4,"frameDurationMs":100,"drawOrder":"behind"}},
                {"id":"source-guard","sheet":"%s","frameCount":2,"frameDurationMs":100,"loop":false,
                 "anchor":[0.5,0.5],"placement":"source","role":"utility","moveIds":["sourceGuard"]},
                {"id":"target-guard","sheet":"%s","frameCount":2,"frameDurationMs":100,"loop":false,
                 "anchor":[0.5,0.5],"placement":"target","role":"utility","moveIds":["targetGuard"]},
                {"id":"reinforce","sheet":"%s","frameCount":2,"frameDurationMs":100,"loop":false,
                 "anchor":[0.5,0.5],"placement":"source","role":"utility","eventTypes":["REINFORCE"],
                 "reinforcement":{"type":"cursed-energy-coating","sheet":"coating.png",
                   "frameCount":2,"frameDurationMs":100,"drawOrder":"behind"}},
                {"id":"dodge","sheet":"%s","frameCount":2,"frameDurationMs":100,"loop":false,
                 "anchor":[0.5,0.5],"placement":"source","role":"utility","eventTypes":["DODGE"]},
                {"id":"miss","sheet":"%s","frameCount":2,"frameDurationMs":100,"loop":false,
                 "anchor":[0.5,0.5],"placement":"target","role":"utility","eventTypes":["MISS"]},
                 {"id":"spark","sheet":"spark.png","frameCount":2,"frameDurationMs":100,"loop":false,
                  "anchor":[0.5,0.5],"placement":"source","role":"utility"}
                ,{"id":"beam","sheet":"%s","frameCount":2,"frameDurationMs":100,"loop":false,
                  "anchor":[0.5,0.5],"placement":"beam","role":"targeted","moveIds":["beamMove"]}
                ,{"id":"projectile","sheet":"%s","frameCount":2,"frameDurationMs":100,"loop":false,
                  "anchor":[0.5,0.5],"placement":"projectile","role":"targeted","moveIds":["projectileMove"]}
               ]
             }
            """.formatted(optionalSheet, optionalSheet, optionalSheet, optionalSheet,
                optionalSheet, optionalSheet, optionalSheet, optionalSheet);
    }

    private static String choreography() {
        return """
            {
              "schemaVersion":1,
              "profiles":{
                "attack":{"size":1,"durationSeconds":0,"impactSeconds":-1,"target":[{"at":0,"x":1}]},
                "guard":{"source":[{"at":0,"scaleY":0.8}]},
                "blocked":{"durationSeconds":0.4,"impactSeconds":0.1,"target":[{"at":0,"scaleY":0.7}]},
                "utility":{"size":1,"durationSeconds":0,"impactSeconds":-1},
                "sourceGuard":{"durationSeconds":0.2,"impactSeconds":0.1,
                  "source":[{"at":0,"x":0},{"at":1,"x":1}],"target":[{"at":0,"x":0}]},
                "targetGuard":{"durationSeconds":0.2,"impactSeconds":0.1,
                  "source":[{"at":0,"x":0}],"target":[{"at":0,"x":0},{"at":1,"x":-1}]},
                "tinted":{"durationSeconds":0.2,"layers":[{"effect":"spark","placement":"source",
                  "plane":"front","transform":[{"at":0,"red":0.25,"green":0.5,"blue":0.75}]}]},
                "beam":{"durationSeconds":0.2,"impactSeconds":0.1,"size":1,"source":[{"at":0,"x":0.25}],
                  "target":[{"at":0,"y":0.25}],"layers":[{"effect":"projectile","placement":"projectile",
                  "plane":"front","durationSeconds":0.2,"size":1}]},
                "projectile":{"durationSeconds":0.4,"size":1}
              },
              "effects":{"source-guard":"sourceGuard","target-guard":"targetGuard","beam":"beam","projectile":"projectile"},
              "roles":{"attack":"attack","utility":"utility","guard":"guard","targeted":"projectile"},
              "events":{"REINFORCE":"utility","DODGE":"utility","MISS":"utility","TINTED":"tinted","MOVE_BLOCKED":"blocked"}
            }
            """;
    }

    private static void writePng(Path file, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0xff000000 | ((x * 17) << 8) | (y * 31));
            }
        }
        ImageIO.write(image, "png", file.toFile());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return (char) 0;
        return 0;
    }

    private record DrawCall(Texture texture, int srcX, int srcY, boolean flipX, boolean flipY,
                            float red, float green, float blue, float alpha, float x, float y,
                            float originX, float originY, float width, float height, float rotation) {
        float centerX() { return x + originX; }
        float centerY() { return y + originY; }
    }

    private static final class BatchRecorder {
        private static final float INITIAL_PACKED_COLOR = 0.25f;
        private final List<DrawCall> calls = new ArrayList<>();
        private float packedColor = INITIAL_PACKED_COLOR;
        private float red = 1f;
        private float green = 1f;
        private float blue = 1f;
        private float alpha = 1f;
        private final Batch proxy = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
            new Class<?>[]{Batch.class}, (proxy, method, args) -> {
                if (method.getName().equals("getPackedColor")) return packedColor;
                if (method.getName().equals("setPackedColor") && args != null && args.length == 1) {
                    packedColor = (Float) args[0];
                }
                if (method.getName().equals("setColor") && args != null && args.length == 4) {
                    red = (Float) args[0];
                    green = (Float) args[1];
                    blue = (Float) args[2];
                    alpha = (Float) args[3];
                }
                if (method.getName().equals("draw") && args != null) {
                    Texture texture = (Texture) args[0];
                    if (args.length == 11) {
                        calls.add(new DrawCall(texture, (Integer) args[5], (Integer) args[6],
                            (Boolean) args[9], (Boolean) args[10], red, green, blue, alpha,
                            (Float) args[1], (Float) args[2], 0, 0, (Float) args[3], (Float) args[4], 0));
                    } else if (args.length == 16) {
                        calls.add(new DrawCall(texture, (Integer) args[10], (Integer) args[11],
                            (Boolean) args[14], (Boolean) args[15], red, green, blue, alpha,
                            (Float) args[1], (Float) args[2], (Float) args[3], (Float) args[4],
                            (Float) args[5], (Float) args[6], (Float) args[9]));
                    }
                }
                return defaultValue(method.getReturnType());
            });

        private void clear() {
            calls.clear();
        }
    }

    private static final class TextureLifecycle {
        private int nextId = 1;
        private final List<Integer> generated = new ArrayList<>();
        private final List<Integer> deleted = new ArrayList<>();

        private int generate() {
            generated.add(nextId);
            return nextId++;
        }

        private void delete(int id) {
            deleted.add(id);
        }
    }
}
