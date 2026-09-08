package com.jjktbf.graphics.animation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.jjktbf.model.combat.CombatEvent;
import com.jjktbf.multiplayer.protocol.BattleEventState;
import com.jjktbf.multiplayer.protocol.BattleEventType;
import com.jjktbf.multiplayer.protocol.MatchState;
import com.jjktbf.graphics.ui.CombatantPanel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Persistent scenery and owner-local fields, paced by authoritative domain instances. */
public final class DomainBackdropPlayer implements Disposable {
    private record Layer(String effect, String plane) { }
    private record Backdrop(String sheet, float fadeSeconds, float openingDelaySeconds,
                            float size, float offsetX, float offsetY, List<Layer> layers) {
        boolean ownerLocal() { return sheet == null; }
    }

    public record DomainVisualState(String domainId, String ownerInstanceId) { }

    private final Map<String, Backdrop> definitions = new LinkedHashMap<>();
    private final Map<String, DomainVisualState> activeDomains = new LinkedHashMap<>();
    private final Map<String, Float> localClocks = new LinkedHashMap<>();
    private final Map<String, Texture> textures = new LinkedHashMap<>();
    private Backdrop visible;
    private float elapsed;

    public void reload() {
        dispose();
        try {
            JsonValue root = new JsonReader().parse(BattleAnimationPlayer.file("domain-backdrops.json"));
            if (root.getInt("schemaVersion", 0) != 1) throw new IllegalArgumentException("domain backdrop schemaVersion");
            JsonValue domains = root.require("domains");
            if (!domains.isObject()) throw new IllegalArgumentException("domains must be an object");
            for (JsonValue domain : domains) {
                String placement = domain.getString("placement", "backdrop");
                if (!placement.equals("backdrop") && !placement.equals("owner-local")) {
                    throw new IllegalArgumentException("Invalid domain placement: " + placement);
                }
                String sheet = placement.equals("backdrop") ? domain.getString("sheet") : null;
                float fade = domain.getFloat("fadeSeconds", .7f);
                float delay = domain.getFloat("openingDelaySeconds", 0);
                float size = domain.getFloat("size", 2.4f);
                float x = domain.getFloat("offsetX", 0);
                float y = domain.getFloat("offsetY", 0);
                List<Layer> layers = new ArrayList<>();
                if (sheet == null) {
                    JsonValue rows = domain.require("layers");
                    if (!rows.isArray() || rows.size == 0) throw new IllegalArgumentException("Missing domain layers");
                    for (JsonValue row : rows) {
                        String plane = row.getString("plane");
                        String effect = row.getString("effect");
                        if (!List.of("behind", "front").contains(plane) || effect.isBlank()) {
                            throw new IllegalArgumentException("Invalid owner-local domain layer");
                        }
                        layers.add(new Layer(effect, plane));
                    }
                }
                if (!Float.isFinite(fade) || fade < 0 || !Float.isFinite(delay) || delay < 0
                    || !Float.isFinite(size) || size <= 0 || !Float.isFinite(x) || !Float.isFinite(y)
                    || (sheet != null && !BattleAnimationPlayer.file(sheet).exists())) {
                    throw new IllegalArgumentException("Invalid domain backdrop: " + domain.name);
                }
                definitions.put(domain.name, new Backdrop(sheet, fade, delay, size, x, y, List.copyOf(layers)));
            }
        } catch (RuntimeException failure) {
            if (Gdx.app != null) Gdx.app.error("DomainBackdrop", "Cannot load domain scenery", failure);
            dispose();
        }
    }

    /** Only preview a successful establishment from this firing's resolved tick. */
    public boolean beginOpening(List<CombatEvent> events, CombatEvent firing) {
        if (firing.getType() != CombatEvent.Type.MOVE_FIRED
            || firing.getSource() == null || firing.getMove() == null) return false;
        int index = events.indexOf(firing);
        if (index < 0) return false;
        String declaredDomain = null;
        for (int i = index + 1; i < events.size(); i++) {
            CombatEvent event = events.get(i);
            if (event.getTick() != firing.getTick()) break;
            if (event.getSource() != firing.getSource()) continue;
            if (declaredDomain == null && event.getType() == CombatEvent.Type.MOVE_FIRED
                && event.getMove() == firing.getMove()) break;
            if (event.getType() == CombatEvent.Type.DOMAIN_DECLARED && event.getMove() == firing.getMove()) {
                declaredDomain = event.getDomainId();
            }
            if (declaredDomain != null && establishment(event.getType().name())
                && declaredDomain.equals(event.getDomainId())) {
                establish(event.getDomainInstanceId(), event.getDomainId(), owner(event), true);
                return event.getDomainInstanceId() != null;
            }
        }
        return false;
    }

    public boolean beginOpening(List<BattleEventState> events, BattleEventState firing) {
        if (firing.type() != BattleEventType.MOVE_FIRED
            || firing.sourceInstanceId() == null || firing.moveId() == null) return false;
        int index = events.indexOf(firing);
        if (index < 0) return false;
        String declaredDomain = null;
        for (int i = index + 1; i < events.size(); i++) {
            BattleEventState event = events.get(i);
            if (event.tick() != firing.tick() || event.roundNumber() != firing.roundNumber()) break;
            if (!Objects.equals(event.sourceInstanceId(), firing.sourceInstanceId())) continue;
            if (declaredDomain == null && event.type() == BattleEventType.MOVE_FIRED
                && Objects.equals(event.moveId(), firing.moveId())) break;
            if (event.type() == BattleEventType.DOMAIN_DECLARED && Objects.equals(event.moveId(), firing.moveId())) {
                declaredDomain = event.domainId();
            }
            if (declaredDomain != null && establishment(event.type().name()) && declaredDomain.equals(event.domainId())) {
                establish(event.domainInstanceId(), event.domainId(), event.sourceInstanceId(), true);
                return event.domainInstanceId() != null;
            }
        }
        return false;
    }

    public void apply(CombatEvent event) {
        apply(event.getType().name(), event.getDomainInstanceId(), event.getDomainId(), owner(event));
    }

    public void apply(BattleEventState event) {
        apply(event.type().name(), event.domainInstanceId(), event.domainId(), event.sourceInstanceId());
    }

    private static String owner(CombatEvent event) {
        return event.getSource() == null || event.getSource().getInstanceId() == null
            ? null : event.getSource().getInstanceId().value();
    }

    private void apply(String type, String instanceId, String domainId, String ownerId) {
        if (establishment(type)) establish(instanceId, domainId, ownerId, false);
        else if (type.equals("DOMAIN_COLLAPSED")) {
            activeDomains.remove(instanceId);
            localClocks.remove(instanceId);
            select(false);
        }
    }

    private static boolean establishment(String type) {
        return type.equals("DOMAIN_ESTABLISHED") || type.equals("DOMAIN_COUNTER_ESTABLISHED");
    }

    private void establish(String instanceId, String domainId, String ownerId, boolean opening) {
        if (instanceId == null || domainId == null) return;
        if (activeDomains.putIfAbsent(instanceId, new DomainVisualState(domainId, ownerId)) == null) {
            Backdrop definition = definitions.get(domainId);
            if (definition != null && definition.ownerLocal()) {
                localClocks.put(instanceId, opening ? -definition.openingDelaySeconds() : 0);
            }
        }
        select(true);
    }

    /** Reconciliation is immediate, including skip and an already-active domain on reconnect. */
    public void sync(Map<String, DomainVisualState> domains) {
        localClocks.keySet().retainAll(domains.keySet());
        domains.forEach((id, state) -> {
            Backdrop definition = definitions.get(state.domainId());
            if (definition != null && definition.ownerLocal()) {
                // Keep the loop phase across round/state updates; skip ends only the opening fade.
                localClocks.compute(id, (key, clock) -> Math.max(definition.fadeSeconds(), clock == null ? 0 : clock));
            } else localClocks.remove(id);
        });
        activeDomains.clear();
        activeDomains.putAll(domains);
        select(false);
        if (visible != null) elapsed = visible.fadeSeconds();
    }

    public void sync(MatchState state, List<BattleEventState> rewindEvents) {
        Map<String, DomainVisualState> domains = new LinkedHashMap<>();
        state.domainBattlefield().activeDomains().forEach(domain -> domains.put(domain.instanceId(),
            new DomainVisualState(domain.domainId(), domain.ownerInstanceId())));
        // A round-end snapshot is ahead of playback. Undo only its domain lifecycle
        // events so reconnects can replay openings/collapses at their actual moments.
        for (int i = rewindEvents.size() - 1; i >= 0; i--) {
            BattleEventState event = rewindEvents.get(i);
            if (establishment(event.type().name())) domains.remove(event.domainInstanceId());
            else if (event.type() == BattleEventType.DOMAIN_COLLAPSED
                && event.domainInstanceId() != null && event.domainId() != null) {
                domains.put(event.domainInstanceId(), new DomainVisualState(event.domainId(), event.sourceInstanceId()));
            }
        }
        sync(domains);
    }

    private void select(boolean animate) {
        Backdrop next = null;
        for (DomainVisualState domain : activeDomains.values()) {
            Backdrop backdrop = definitions.get(domain.domainId());
            if (backdrop != null && !backdrop.ownerLocal()) next = backdrop;
        }
        if (!Objects.equals(next, visible)) {
            visible = next;
            elapsed = animate || next == null ? 0 : next.fadeSeconds();
        }
    }

    public void update(float seconds) {
        if (Float.isFinite(seconds)) localClocks.replaceAll((id, clock) -> clock + Math.max(0, seconds));
        if (visible != null && Float.isFinite(seconds)) {
            elapsed = Math.min(visible.fadeSeconds(), elapsed + Math.max(0, seconds));
        }
    }

    /** Called immediately around this owner's sprite, after its plate and before all HUDs. */
    public void drawOwner(Batch batch, String ownerId, CombatantPanel panel,
                          BattleAnimationPlayer animations, String plane) {
        if (ownerId == null || panel == null) return;
        for (Map.Entry<String, DomainVisualState> entry : activeDomains.entrySet()) {
            if (!ownerId.equals(entry.getValue().ownerInstanceId())) continue;
            Backdrop definition = definitions.get(entry.getValue().domainId());
            if (definition == null || !definition.ownerLocal()) continue;
            float time = localClocks.getOrDefault(entry.getKey(), 0f);
            if (time < 0) continue;
            float progress = definition.fadeSeconds() == 0 ? 1 : Math.min(1, time / definition.fadeSeconds());
            if (progress <= 0) continue;
            for (Layer layer : definition.layers()) {
                if (layer.plane().equals(plane)) animations.drawPersistent(batch, layer.effect(), panel,
                    time, definition.size(), definition.offsetX(), definition.offsetY(),
                    progress * progress * (3 - 2 * progress));
            }
        }
    }

    public void draw(Batch batch, Rectangle bounds) {
        if (visible == null || bounds.width <= 0 || bounds.height <= 0) return;
        float progress = visible.fadeSeconds() == 0 ? 1 : elapsed / visible.fadeSeconds();
        if (progress <= 0) return;
        float previous = batch.getPackedColor();
        try {
            Texture texture = textures.computeIfAbsent(visible.sheet(), sheet -> {
                Texture loaded = new Texture(BattleAnimationPlayer.file(sheet));
                loaded.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                return loaded;
            });
            float scale = Math.max(bounds.width / texture.getWidth(), bounds.height / texture.getHeight());
            float width = texture.getWidth() * scale;
            float height = texture.getHeight() * scale;
            batch.setColor(1, 1, 1, progress * progress * (3 - 2 * progress));
            batch.draw(texture, bounds.x + (bounds.width - width) / 2, bounds.y + (bounds.height - height) / 2,
                width, height);
        } catch (RuntimeException failure) {
            Backdrop failed = visible;
            definitions.values().removeIf(failed::equals);
            select(false);
            if (Gdx.app != null) Gdx.app.error("DomainBackdrop", "Cannot draw domain scenery", failure);
        } finally {
            batch.setPackedColor(previous);
        }
    }

    public void clear() {
        activeDomains.clear();
        localClocks.clear();
        visible = null;
        elapsed = 0;
    }

    @Override public void dispose() {
        clear();
        textures.values().forEach(Texture::dispose);
        textures.clear();
        definitions.clear();
    }
}
