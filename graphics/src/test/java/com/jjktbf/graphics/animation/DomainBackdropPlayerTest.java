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
import com.jjktbf.model.character.CharacterStats;
import com.jjktbf.model.character.Equipment;
import com.jjktbf.model.character.SorcererCharacter;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveData;
import com.jjktbf.multiplayer.protocol.BattleEventState;
import com.jjktbf.multiplayer.protocol.BattleEventType;
import com.jjktbf.multiplayer.protocol.BattlePhase;
import com.jjktbf.multiplayer.protocol.DomainBattlefieldState;
import com.jjktbf.multiplayer.protocol.DomainState;
import com.jjktbf.multiplayer.protocol.MatchState;
import com.jjktbf.multiplayer.protocol.MatchStatus;
import com.jjktbf.multiplayer.protocol.PlayerSide;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainBackdropPlayerTest {
    private static final float INITIAL_PACKED_COLOR = 0.25f;
    private static final Move OPENING_MOVE = move("fixture-move");

    @TempDir
    Path root;

    private String oldAnimationsDirectory;
    private DomainBackdropPlayer player;
    private BattleAnimationPlayer animations;
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
        if (animations != null) animations.dispose();
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
    void localOpeningRequiresSuccessfulSameOwnerSameTickEstablishment() throws IOException {
        loadFixture();
        BattleCombatant owner = fighter();
        CombatEvent firing = local(CombatEvent.Type.MOVE_FIRED, owner, 7, null, null);
        assertTrue(player.beginOpening(List.of(firing,
            local(CombatEvent.Type.DOMAIN_DECLARED, owner, 7, null, DOMAIN_A),
            local(CombatEvent.Type.DOMAIN_ESTABLISHED, owner, 7, "local-instance", DOMAIN_A)), firing));
        BatchRecorder batch = new BatchRecorder();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertTrue(batch.calls.isEmpty(), "opening begins at zero opacity");
        player.update(.5f);
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertEquals(1, batch.calls.size());
        assertTrue(batch.calls.get(0).alpha > 0 && batch.calls.get(0).alpha < 1);
    }

    @Test
    void onlineOpeningRequiresSuccessfulSameOwnerSameRoundAndTick() throws IOException {
        loadFixture();
        BattleEventState firing = online(BattleEventType.MOVE_FIRED, 3, 7, "owner-1", null, null);
        BattleEventState established = online(BattleEventType.DOMAIN_ESTABLISHED, 3, 7, "owner-1",
            "online-instance", DOMAIN_A);
        assertTrue(player.beginOpening(List.of(firing,
            online(BattleEventType.DOMAIN_DECLARED, 3, 7, "owner-1", null, DOMAIN_A), established), firing));

        player.update(.5f);
        BatchRecorder batch = new BatchRecorder();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertEquals(1, batch.calls.size());
        assertTrue(batch.calls.get(0).alpha > 0 && batch.calls.get(0).alpha < 1);
    }

    @Test
    void doesNotPreviewDeclarationFailureWrongOwnerOrLaterTick() throws IOException {
        loadFixture();
        BattleCombatant owner = fighter();
        CombatEvent firing = local(CombatEvent.Type.MOVE_FIRED, owner, 7, null, null);
        CombatEvent declared = local(CombatEvent.Type.DOMAIN_DECLARED, owner, 7, null, DOMAIN_A);
        List<List<CombatEvent>> invalid = List.of(
            List.of(firing, declared),
            List.of(firing, declared, local(CombatEvent.Type.DOMAIN_ESTABLISHED, fighter(), 7, "i", DOMAIN_A)),
            List.of(firing, declared, local(CombatEvent.Type.DOMAIN_ESTABLISHED, owner, 8, "i", DOMAIN_A)),
            List.of(firing, declared, local(CombatEvent.Type.DOMAIN_ESTABLISHED, owner, 7, "i", DOMAIN_B)),
            List.of(firing, local(CombatEvent.Type.DOMAIN_ESTABLISHED, owner, 7, "i", DOMAIN_A)));
        for (List<CombatEvent> events : invalid) {
            BatchRecorder batch = new BatchRecorder();
            player.beginOpening(events, firing);
            player.update(2);
            player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
            assertTrue(batch.calls.isEmpty());
        }

        BattleEventState onlineFiring = online(BattleEventType.MOVE_FIRED, 3, 7, "owner-1", null, null);
        for (BattleEventState established : List.of(
            online(BattleEventType.DOMAIN_ESTABLISHED, 3, 7, "owner-2", "i", DOMAIN_A),
            online(BattleEventType.DOMAIN_ESTABLISHED, 3, 8, "owner-1", "i", DOMAIN_A),
            online(BattleEventType.DOMAIN_ESTABLISHED, 4, 7, "owner-1", "i", DOMAIN_A))) {
            BatchRecorder batch = new BatchRecorder();
            player.beginOpening(List.of(onlineFiring,
                online(BattleEventType.DOMAIN_DECLARED, 3, 7, "owner-1", null, DOMAIN_A), established), onlineFiring);
            player.update(2);
            player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
            assertTrue(batch.calls.isEmpty());
        }
    }

    @Test
    void persistsAfterManySecondsAndDuplicateEstablishmentDoesNotResetFade() throws IOException {
        loadFixture();
        BattleCombatant owner = fighter();
        CombatEvent firing = local(CombatEvent.Type.MOVE_FIRED, owner, 1, null, null);
        CombatEvent established = local(CombatEvent.Type.DOMAIN_ESTABLISHED, owner, 1, "i", DOMAIN_A);
        player.beginOpening(List.of(firing,
            local(CombatEvent.Type.DOMAIN_DECLARED, owner, 1, null, DOMAIN_A), established), firing);
        player.update(.4f);
        BatchRecorder batch = new BatchRecorder();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        float beforeDuplicate = batch.calls.get(0).alpha;
        batch.clear();
        player.apply(established);
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertEquals(beforeDuplicate, batch.calls.get(0).alpha, 0.0001f);
        player.update(20);
        batch.clear();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertEquals(1, batch.calls.get(0).alpha, 0.0001f);
    }

    @Test
    void sameTickFollowupMoveCannotStealTheDomainOpening() throws IOException {
        loadFixture();
        BattleCombatant owner = fighter();
        CombatEvent firing = local(CombatEvent.Type.MOVE_FIRED, owner, 7, null, null);
        CombatEvent followup = CombatEvent.of(CombatEvent.Type.MOVE_FIRED).source(owner)
            .move(move("followup")).tick(7).build();
        List<CombatEvent> events = List.of(firing,
            local(CombatEvent.Type.DOMAIN_DECLARED, owner, 7, null, DOMAIN_A), followup,
            local(CombatEvent.Type.DOMAIN_ESTABLISHED, owner, 7, "i", DOMAIN_A));
        player.beginOpening(events, firing);
        player.update(2);
        BatchRecorder batch = new BatchRecorder();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertEquals(1, batch.calls.size());
        player.clear();
        batch.clear();
        player.beginOpening(events, followup);
        player.update(2);
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertTrue(batch.calls.isEmpty());

        BattleEventState onlineFiring = online(BattleEventType.MOVE_FIRED, 3, 7, "owner-1", null, null);
        BattleEventState onlineFollowup = new BattleEventState("followup", BattleEventType.MOVE_FIRED,
            3, 7, PlayerSide.PLAYER_ONE, null, null, null, null, null, "followup", null,
            null, null, null, null, "owner-1", null);
        List<BattleEventState> onlineEvents = List.of(onlineFiring,
            online(BattleEventType.DOMAIN_DECLARED, 3, 7, "owner-1", null, DOMAIN_A), onlineFollowup,
            online(BattleEventType.DOMAIN_ESTABLISHED, 3, 7, "owner-1", "i", DOMAIN_A));
        player.beginOpening(onlineEvents, onlineFiring);
        player.update(2);
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertEquals(1, batch.calls.size());
        player.clear();
        batch.clear();
        player.beginOpening(onlineEvents, onlineFollowup);
        player.update(2);
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertTrue(batch.calls.isEmpty());
    }

    @Test
    void matchingCollapseClearsUnrelatedCollapseKeepsAndDuplicateInstancesCoexist() throws IOException {
        loadFixture();
        BatchRecorder batch = new BatchRecorder();
        player.apply(local(CombatEvent.Type.DOMAIN_ESTABLISHED, null, 0, "first", DOMAIN_A));
        player.apply(local(CombatEvent.Type.DOMAIN_COLLAPSED, null, 0, "unrelated", DOMAIN_A));
        player.update(2);
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertEquals(1, batch.calls.size());

        player.apply(local(CombatEvent.Type.DOMAIN_COLLAPSED, null, 0, "first", DOMAIN_A));
        batch.clear();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertTrue(batch.calls.isEmpty());

        player.apply(local(CombatEvent.Type.DOMAIN_ESTABLISHED, null, 0, "first", DOMAIN_A));
        player.apply(local(CombatEvent.Type.DOMAIN_ESTABLISHED, null, 0, "second", DOMAIN_A));
        player.apply(local(CombatEvent.Type.DOMAIN_COLLAPSED, null, 0, "first", DOMAIN_A));
        player.update(2);
        batch.clear();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertEquals(1, batch.calls.size(), "the second same-domain instance remains visible");
        player.apply(local(CombatEvent.Type.DOMAIN_COLLAPSED, null, 0, "second", DOMAIN_A));
        batch.clear();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertTrue(batch.calls.isEmpty());
    }

    @Test
    void syncRestoresAndClearsSnapshotDomains() throws IOException {
        loadFixture();
        BatchRecorder batch = new BatchRecorder();
        player.sync(Map.of("snapshot-instance", new DomainBackdropPlayer.DomainVisualState(DOMAIN_B, null)));
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertEquals(1, batch.calls.size());
        player.sync(Map.of());
        batch.clear();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertTrue(batch.calls.isEmpty());
    }

    @Test
    void collapsingNewerSceneryRestoresTheEarlierDomain() throws IOException {
        loadFixture();
        player.apply(local(CombatEvent.Type.DOMAIN_ESTABLISHED, null, 0, "earlier", DOMAIN_A));
        player.update(2);
        BatchRecorder batch = new BatchRecorder();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        Texture earlier = batch.calls.get(0).texture;
        player.apply(local(CombatEvent.Type.DOMAIN_ESTABLISHED, null, 0, "newer", DOMAIN_B));
        player.update(2);
        batch.clear();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertNotEquals(earlier, batch.calls.get(0).texture);
        player.apply(local(CombatEvent.Type.DOMAIN_COLLAPSED, null, 0, "newer", DOMAIN_B));
        batch.clear();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertEquals(earlier, batch.calls.get(0).texture);
        assertEquals(1, batch.calls.get(0).alpha);
    }

    @Test
    void onlineRewindUndoesEstablishCollapseAndBornThenCollapsedBatch() throws IOException {
        loadFixture();
        BattleEventState establish = online(BattleEventType.DOMAIN_ESTABLISHED, 1, 4, "owner-1", "i", DOMAIN_A);
        BattleEventState collapse = online(BattleEventType.DOMAIN_COLLAPSED, 1, 5, "owner-1", "i", DOMAIN_A);
        BatchRecorder batch = new BatchRecorder();

        player.sync(state(List.of(domain("i", DOMAIN_A)), List.of(establish)), List.of(establish));
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertTrue(batch.calls.isEmpty(), "the establish event is rewound from the snapshot");

        player.sync(state(List.of(), List.of(collapse)), List.of(collapse));
        batch.clear();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertEquals(1, batch.calls.size(), "the collapse event is rewound from the snapshot");

        player.sync(state(List.of(), List.of(establish, collapse)), List.of(establish, collapse));
        batch.clear();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 100));
        assertTrue(batch.calls.isEmpty(), "a domain born and collapsed in one batch stays absent");
    }

    @Test
    void usesAspectCoverAndRestoresPackedBatchColor() throws IOException {
        loadFixture();
        player.sync(Map.of("instance", new DomainBackdropPlayer.DomainVisualState(DOMAIN_A, null)));
        BatchRecorder batch = new BatchRecorder();

        player.draw(batch.proxy, new Rectangle(0, 0, 400, 100));
        DrawCall wide = batch.calls.get(0);
        assertEquals(-0f, wide.x, 0.0001f);
        assertEquals(-50f, wide.y, 0.0001f);
        assertEquals(400f, wide.width, 0.0001f);
        assertEquals(200f, wide.height, 0.0001f);
        assertEquals(INITIAL_PACKED_COLOR, batch.packedColor, 0f);

        batch.clear();
        player.draw(batch.proxy, new Rectangle(0, 0, 100, 400));
        DrawCall tall = batch.calls.get(0);
        assertEquals(-350f, tall.x, 0.0001f);
        assertEquals(0f, tall.y, 0.0001f);
        assertEquals(800f, tall.width, 0.0001f);
        assertEquals(400f, tall.height, 0.0001f);
        assertEquals(INITIAL_PACKED_COLOR, batch.packedColor, 0f);
    }

    @Test
    void clearKeepsReusableTextureButDisposeReleasesIt() throws IOException {
        loadFixture();
        player.sync(Map.of("instance", new DomainBackdropPlayer.DomainVisualState(DOMAIN_A, null)));
        player.draw(new BatchRecorder().proxy, new Rectangle(0, 0, 100, 100));
        assertEquals(1, textures.generated.size());
        player.clear();
        BatchRecorder cleared = new BatchRecorder();
        player.draw(cleared.proxy, new Rectangle(0, 0, 100, 100));
        assertTrue(cleared.calls.isEmpty());
        assertTrue(textures.deleted.isEmpty());
        player.dispose();
        assertEquals(textures.generated, textures.deleted);
    }

    @Test
    void bundledSceneryDecodesAsOpaqueBackgrounds() throws IOException {
        JsonValue domains = new JsonReader().parse(Gdx.files.classpath("assets/animations/domain-backdrops.json"))
            .require("domains");
        for (JsonValue domain : domains) {
            if (domain.getString("placement", "backdrop").equals("owner-local")) continue;
            try (var input = Gdx.files.classpath("assets/animations/" + domain.getString("sheet")).read()) {
                BufferedImage image = ImageIO.read(input);
                assertNotNull(image, domain.name);
                assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        assertEquals(255, image.getRGB(x, y) >>> 24);
                    }
                }
            }
        }
    }

    @Test
    void ownerLocalFieldsDrawForBothOwnersAlongsideBackdropAndCollapseExactInstance() throws IOException {
        loadOwnerLocalFixture();
        player.apply(online(BattleEventType.DOMAIN_ESTABLISHED, 1, 1, null, "background", DOMAIN_A));
        player.apply(online(BattleEventType.DOMAIN_ESTABLISHED, 1, 1, "owner-a", "local-a", LOCAL_DOMAIN));
        player.apply(online(BattleEventType.DOMAIN_ESTABLISHED, 1, 1, "owner-b", "local-b", LOCAL_DOMAIN));
        player.update(1);

        BatchRecorder background = new BatchRecorder();
        player.draw(background.proxy, new Rectangle(0, 0, 320, 180));
        assertEquals(1, background.calls.size(), "owner-local domains must not replace the global backdrop");

        CombatantPanel ownerA = panel(10, 20, 40, 80);
        CombatantPanel ownerB = panel(100, 20, 40, 80);
        BatchRecorder fields = new BatchRecorder();
        player.drawOwner(fields.proxy, "owner-a", ownerA, animations, "behind");
        player.drawOwner(fields.proxy, "owner-a", ownerA, animations, "front");
        player.drawOwner(fields.proxy, "owner-b", ownerB, animations, "front");
        assertEquals(3, fields.calls.size(), "both owners expose behind/front field layers");

        fields.clear();
        player.apply(online(BattleEventType.DOMAIN_COLLAPSED, 1, 2, "owner-a", "local-a", LOCAL_DOMAIN));
        player.drawOwner(fields.proxy, "owner-a", ownerA, animations, "front");
        assertTrue(fields.calls.isEmpty(), "collapse removes only the exact domain instance");
        player.drawOwner(fields.proxy, "owner-b", ownerB, animations, "front");
        assertEquals(1, fields.calls.size(), "the other owner-local instance remains active");
        background.clear();
        player.draw(background.proxy, new Rectangle(0, 0, 320, 180));
        assertEquals(1, background.calls.size(), "collapsing a field leaves the backdrop active");
    }

    @Test
    void reconnectAndRewindRestoresOwnerLocalFieldWithCorrectOwnerIdentity() throws IOException {
        loadOwnerLocalFixture();
        BattleEventState establish = online(BattleEventType.DOMAIN_ESTABLISHED, 2, 4,
            "owner-a", "local-a", LOCAL_DOMAIN);
        BattleEventState collapse = online(BattleEventType.DOMAIN_COLLAPSED, 2, 5,
            "owner-a", "local-a", LOCAL_DOMAIN);
        CombatantPanel ownerA = panel(10, 20, 40, 80);
        CombatantPanel ownerB = panel(100, 20, 40, 80);
        BatchRecorder fields = new BatchRecorder();

        player.sync(state(List.of(domain("local-a", LOCAL_DOMAIN, "owner-a")), List.of()), List.of());
        player.drawOwner(fields.proxy, "owner-a", ownerA, animations, "front");
        assertEquals(1, fields.calls.size(), "snapshot restores the field for its owner");
        fields.clear();
        player.drawOwner(fields.proxy, "owner-b", ownerB, animations, "front");
        assertTrue(fields.calls.isEmpty(), "snapshot owner identity is not global");

        player.sync(state(List.of(), List.of(collapse)), List.of(collapse));
        fields.clear();
        player.drawOwner(fields.proxy, "owner-a", ownerA, animations, "front");
        assertEquals(1, fields.calls.size(), "rewinding collapse restores the field");
        fields.clear();
        player.sync(state(List.of(), List.of(establish)), List.of(establish));
        player.drawOwner(fields.proxy, "owner-a", ownerA, animations, "front");
        assertTrue(fields.calls.isEmpty(), "rewinding establishment removes the field");
    }

    @Test
    void animationPlayerClearDoesNotRemoveFieldAndDisposeReleasesItsTextures() throws IOException {
        loadOwnerLocalFixture();
        player.apply(online(BattleEventType.DOMAIN_ESTABLISHED, 1, 1, "owner-a", "local-a", LOCAL_DOMAIN));
        player.update(1);
        CombatantPanel owner = panel(10, 20, 40, 80);
        BatchRecorder fields = new BatchRecorder();
        player.drawOwner(fields.proxy, "owner-a", owner, animations, "front");
        assertEquals(1, fields.calls.size());
        assertEquals(1, textures.generated.size(), "the persistent field lazily loads one sheet");

        animations.clear();
        fields.clear();
        player.drawOwner(fields.proxy, "owner-a", owner, animations, "front");
        assertEquals(1, fields.calls.size(), "clearing finite animation playback does not clear domain state");
        assertTrue(textures.deleted.isEmpty(), "clear retains reusable loaded textures");

        animations.dispose();
        assertEquals(textures.generated, textures.deleted, "disposing the animation player releases field textures");
        animations = null;
    }

    private static final String DOMAIN_A = "fixture-domain-a";

    @Test
    void counterOpeningHasNoMaintainedFieldUntilConfirmedBoundaryTime() throws IOException {
        loadOwnerLocalFixture();
        BattleCombatant owner = fighter();
        new com.jjktbf.model.combat.BattleState(owner, fighter());
        String ownerId = owner.getInstanceId().value();
        CombatantPanel panel = panel(10, 20, 40, 80);
        CombatEvent firing = local(CombatEvent.Type.MOVE_FIRED, owner, 1, null, null);
        CombatEvent declaration = local(CombatEvent.Type.DOMAIN_DECLARED, owner, 1, null, LOCAL_DOMAIN);
        CombatEvent established = local(CombatEvent.Type.DOMAIN_COUNTER_ESTABLISHED, owner, 1, "i", LOCAL_DOMAIN);
        BatchRecorder batch = new BatchRecorder();
        assertFalse(player.beginOpening(List.of(firing, declaration), firing));
        player.update(20);
        player.drawOwner(batch.proxy, ownerId, panel, animations, "front");
        assertTrue(batch.calls.isEmpty(), "a declaration alone never leaves a field");
        assertTrue(player.beginOpening(List.of(firing, declaration, established), firing));
        player.update(.1f);
        player.drawOwner(batch.proxy, ownerId, panel, animations, "front");
        assertTrue(batch.calls.isEmpty(), "activation has not yet expanded to the maintained boundary");
        player.update(.3f);
        player.drawOwner(batch.proxy, ownerId, panel, animations, "front");
        assertEquals(1, batch.calls.size());
        assertTrue(batch.calls.get(0).alpha > 0 && batch.calls.get(0).alpha < 1);
        float opacity = batch.calls.get(0).alpha;
        player.apply(established);
        batch.clear();
        player.drawOwner(batch.proxy, ownerId, panel, animations, "front");
        assertEquals(opacity, batch.calls.get(0).alpha, .0001f, "later event does not restart the opening");

        player.clear();
        batch.clear();
        BattleEventState onlineFire = online(BattleEventType.MOVE_FIRED, 1, 1, ownerId, null, null);
        assertTrue(player.beginOpening(List.of(onlineFire,
            online(BattleEventType.DOMAIN_DECLARED, 1, 1, ownerId, null, LOCAL_DOMAIN),
            online(BattleEventType.DOMAIN_COUNTER_ESTABLISHED, 1, 1, ownerId, "online", LOCAL_DOMAIN)), onlineFire));
        player.update(1);
        player.drawOwner(batch.proxy, "not-the-owner", panel, animations, "front");
        assertTrue(batch.calls.isEmpty());
        player.drawOwner(batch.proxy, ownerId, panel, animations, "front");
        assertEquals(1, batch.calls.size());
    }

    @Test
    void laterEventsAndAuthoritativeSyncPreserveLoopPhaseButAbsentInstanceClearsIt() throws IOException {
        loadOwnerLocalFixture();
        CombatantPanel owner = panel(10, 20, 40, 80);
        player.apply(online(BattleEventType.DOMAIN_COUNTER_ESTABLISHED, 1, 1, "owner", "i", LOCAL_DOMAIN));
        player.update(20.93f);
        BatchRecorder batch = new BatchRecorder();
        player.drawOwner(batch.proxy, "owner", owner, animations, "front");
        int frameX = batch.calls.get(0).srcX;
        assertTrue(frameX > 0);
        player.apply(online(BattleEventType.MOVE_FIRED, 4, 1, "other", null, null));
        player.sync(Map.of("i", new DomainBackdropPlayer.DomainVisualState(LOCAL_DOMAIN, "owner")));
        batch.clear();
        player.drawOwner(batch.proxy, "owner", null, animations, "front");
        assertTrue(batch.calls.isEmpty(), "an absent render panel is safe");
        player.drawOwner(batch.proxy, "owner", owner, animations, "front");
        assertEquals(frameX, batch.calls.get(0).srcX, "round sync does not restart the maintained loop");
        player.sync(Map.of());
        batch.clear();
        player.drawOwner(batch.proxy, "owner", owner, animations, "front");
        assertTrue(batch.calls.isEmpty());
    }
    private static final String DOMAIN_B = "fixture-domain-b";
    private static final String LOCAL_DOMAIN = "fixture-owner-local";

    private void loadFixture() throws IOException {
        writePng(root.resolve("domain-sheet.png"), 16, 8);
        writePng(root.resolve("other-sheet.png"), 16, 8);
        Files.writeString(root.resolve("domain-backdrops.json"), """
            {"schemaVersion":1,"domains":{
              "fixture-domain-a":{"sheet":"domain-sheet.png","fadeSeconds":1},
              "fixture-domain-b":{"sheet":"other-sheet.png","fadeSeconds":1},
              "fixture-owner-local":{"placement":"owner-local","fadeSeconds":0.5,
                "openingDelaySeconds":0.2,"size":2.4,"offsetX":0.1,"offsetY":-0.2,
                "layers":[{"effect":"simple-domain-field-back","plane":"behind"},
                          {"effect":"simple-domain-field-front","plane":"front"}]}
            }}
            """);
        System.setProperty(BattleAnimationPlayer.DIRECTORY_PROPERTY, root.toString());
        player = new DomainBackdropPlayer();
        player.reload();
    }

    private void loadOwnerLocalFixture() throws IOException {
        loadFixture();
        writeAnimationFixture(root);
        animations = new BattleAnimationPlayer();
        animations.reload();
        assertTrue(animations.hasMove("000138"));
        assertTrue(animations.hasMove("000026"));
    }

    private static BattleCombatant fighter() {
        return new BattleCombatant(new SorcererCharacter("fixture", "Fixture",
            new CharacterStats.Builder().build(), null, List.of(), List.of(), Equipment.NONE), List.of());
    }

    private static Move move(String id) {
        MoveData data = new MoveData();
        data.id = id;
        data.name = id;
        data.apCost = 1;
        data.unleashPoint = 1;
        return data.toMove();
    }

    private static CombatEvent local(CombatEvent.Type type, BattleCombatant source, int tick,
                                     String instanceId, String domainId) {
        return CombatEvent.of(type).source(source).move(OPENING_MOVE)
            .tick(tick).domainInstanceId(instanceId).domainId(domainId).build();
    }

    private static BattleEventState online(BattleEventType type, int round, int tick,
                                           String sourceInstanceId, String domainInstanceId,
                                           String domainId) {
        return new BattleEventState("fixture-event", type, round, tick, PlayerSide.PLAYER_ONE,
            null, null, null, null, null, "fixture-move", null, null, null, null, null,
            sourceInstanceId, null, null, null, null, null, domainInstanceId, null,
            domainId, null, null);
    }

    private static MatchState state(List<DomainState> domains, List<BattleEventState> rewindEvents) {
        return new MatchState("fixture-match", MatchStatus.ACTIVE, "fixture", 1, "fixture",
            BattlePhase.ROUND_END, 1, 0, List.of(), List.of(), null, null, null, 1L,
            rewindEvents, new DomainBattlefieldState(domains, List.of()), null, 0L);
    }

    private static DomainState domain(String instanceId, String domainId) {
        return domain(instanceId, domainId, "owner-1");
    }

    private static DomainState domain(String instanceId, String domainId, String ownerId) {
        return new DomainState(instanceId, domainId, "fixture", ownerId, false,
            "CLOSED", "NONE", List.of(), List.of(), List.of(), 1, 1, 1, 1, 1);
    }

    private static CombatantPanel panel(float x, float y, float width, float height) {
        return new CombatantPanel(null, null, null, new Rectangle(),
            new Rectangle(x, y, width, height), new Rectangle(0, 0, 100, 100), 1f, false);
    }

    private static void writeAnimationFixture(Path root) throws IOException {
        Path pack = root.resolve("simple-domain");
        Files.createDirectories(pack);
        for (String sheet : List.of("establish-front.png", "establish-back.png",
            "field-front.png", "field-back.png")) {
            writePng(pack.resolve(sheet), 96, 4);
        }
        Files.writeString(root.resolve("catalog.json"), """
            {"schemaVersion":1,"packs":["simple-domain"]}
            """);
        Files.writeString(pack.resolve("manifest.json"), """
            {"schemaVersion":1,"frameWidth":4,"frameHeight":4,"columns":24,
             "sheetOrder":"row-major-top-left","effects":[
              {"id":"simple-domain-establish-front","sheet":"establish-front.png",
               "frameCount":24,"frameDurationMs":50,"loop":false,"anchor":[0.5,1],
               "placement":"source-feet","role":"domain","moveIds":["000138","000026"]},
              {"id":"simple-domain-establish-back","sheet":"establish-back.png",
               "frameCount":24,"frameDurationMs":50,"loop":false,"anchor":[0.5,1],
               "placement":"source-feet","role":"domain"},
              {"id":"simple-domain-field-front","sheet":"field-front.png",
               "frameCount":12,"frameDurationMs":150,"loop":true,"anchor":[0.5,1],
               "placement":"source-feet","role":"utility"},
              {"id":"simple-domain-field-back","sheet":"field-back.png",
               "frameCount":12,"frameDurationMs":150,"loop":true,"anchor":[0.5,1],
               "placement":"source-feet","role":"utility"}
             ]}
            """);
        Files.writeString(root.resolve("choreography.json"), """
            {"schemaVersion":1,"profiles":{
              "domain":{"durationSeconds":0,"impactSeconds":-1,
                "source":[{"at":0,"x":0.5,"y":0.25}]}
             },"effects":{"simple-domain-establish-front":"domain",
               "simple-domain-establish-back":"domain",
               "simple-domain-field-front":"domain","simple-domain-field-back":"domain"},
             "roles":{"domain":"domain"}}
            """);
    }

    private static void writePng(Path file, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.setRGB(x, y, 0xff000000 | (x << 8) | y);
        }
        ImageIO.write(image, "png", file.toFile());
    }

    private record DrawCall(Texture texture, float x, float y, float width, float height, float alpha, int srcX) { }

    private static final class BatchRecorder {
        private final List<DrawCall> calls = new ArrayList<>();
        private float packedColor = INITIAL_PACKED_COLOR;
        private float alpha = 1f;
        private final Batch proxy = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
            new Class<?>[]{Batch.class}, (proxy, method, args) -> {
                if (method.getName().equals("getPackedColor")) return packedColor;
                if (method.getName().equals("setPackedColor") && args != null && args.length == 1) {
                    packedColor = (Float) args[0];
                }
                if (method.getName().equals("setColor") && args != null && args.length == 4) {
                    alpha = (Float) args[3];
                    packedColor = .75f;
                }
                if (method.getName().equals("draw") && args != null && args.length == 5) {
                    calls.add(new DrawCall((Texture) args[0], (Float) args[1], (Float) args[2],
                        (Float) args[3], (Float) args[4], alpha, 0));
                } else if (method.getName().equals("draw") && args != null && args.length == 16) {
                    calls.add(new DrawCall((Texture) args[0], (Float) args[1], (Float) args[2],
                        (Float) args[5], (Float) args[6], alpha, (Integer) args[10]));
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
}
