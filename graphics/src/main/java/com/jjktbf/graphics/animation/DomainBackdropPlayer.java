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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Render-thread scenery whose lifetime follows domain instances, not animation clips. */
public final class DomainBackdropPlayer implements Disposable {
    private record Backdrop(String sheet, float fadeSeconds) { }

    private final Map<String, Backdrop> definitions = new LinkedHashMap<>();
    private final Map<String, String> activeDomains = new LinkedHashMap<>();
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
                String sheet = domain.getString("sheet");
                float fade = domain.getFloat("fadeSeconds", .7f);
                if (!Float.isFinite(fade) || fade < 0 || !BattleAnimationPlayer.file(sheet).exists()) {
                    throw new IllegalArgumentException("Invalid domain backdrop: " + domain.name);
                }
                definitions.put(domain.name, new Backdrop(sheet, fade));
            }
        } catch (RuntimeException failure) {
            if (Gdx.app != null) Gdx.app.error("DomainBackdrop", "Cannot load domain scenery", failure);
            dispose();
        }
    }

    /** Only preview a successful establishment from this firing's resolved tick. */
    public void beginOpening(List<CombatEvent> events, CombatEvent firing) {
        if (firing.getType() != CombatEvent.Type.MOVE_FIRED
            || firing.getSource() == null || firing.getMove() == null) return;
        int index = events.indexOf(firing);
        if (index < 0) return;
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
                establish(event.getDomainInstanceId(), event.getDomainId());
                return;
            }
        }
    }

    public void beginOpening(List<BattleEventState> events, BattleEventState firing) {
        if (firing.type() != BattleEventType.MOVE_FIRED
            || firing.sourceInstanceId() == null || firing.moveId() == null) return;
        int index = events.indexOf(firing);
        if (index < 0) return;
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
                establish(event.domainInstanceId(), event.domainId());
                return;
            }
        }
    }

    public void apply(CombatEvent event) {
        apply(event.getType().name(), event.getDomainInstanceId(), event.getDomainId());
    }

    public void apply(BattleEventState event) {
        apply(event.type().name(), event.domainInstanceId(), event.domainId());
    }

    private void apply(String type, String instanceId, String domainId) {
        if (establishment(type)) establish(instanceId, domainId);
        else if (type.equals("DOMAIN_COLLAPSED")) {
            activeDomains.remove(instanceId);
            select(false);
        }
    }

    private static boolean establishment(String type) {
        return type.equals("DOMAIN_ESTABLISHED") || type.equals("DOMAIN_COUNTER_ESTABLISHED");
    }

    private void establish(String instanceId, String domainId) {
        if (instanceId == null || domainId == null) return;
        activeDomains.putIfAbsent(instanceId, domainId);
        select(true);
    }

    /** Reconciliation is immediate, including skip and an already-active domain on reconnect. */
    public void sync(Map<String, String> domains) {
        activeDomains.clear();
        activeDomains.putAll(domains);
        select(false);
        if (visible != null) elapsed = visible.fadeSeconds();
    }

    public void sync(MatchState state, List<BattleEventState> rewindEvents) {
        Map<String, String> domains = new LinkedHashMap<>();
        state.domainBattlefield().activeDomains().forEach(domain -> domains.put(domain.instanceId(), domain.domainId()));
        // A round-end snapshot is ahead of playback. Undo only its domain lifecycle
        // events so reconnects can replay openings/collapses at their actual moments.
        for (int i = rewindEvents.size() - 1; i >= 0; i--) {
            BattleEventState event = rewindEvents.get(i);
            if (establishment(event.type().name())) domains.remove(event.domainInstanceId());
            else if (event.type() == BattleEventType.DOMAIN_COLLAPSED
                && event.domainInstanceId() != null && event.domainId() != null) {
                domains.put(event.domainInstanceId(), event.domainId());
            }
        }
        sync(domains);
    }

    private void select(boolean animate) {
        Backdrop next = null;
        for (String domain : activeDomains.values()) {
            Backdrop backdrop = definitions.get(domain);
            if (backdrop != null) next = backdrop;
        }
        if (!Objects.equals(next, visible)) {
            visible = next;
            elapsed = animate || next == null ? 0 : next.fadeSeconds();
        }
    }

    public void update(float seconds) {
        if (visible != null && Float.isFinite(seconds)) {
            elapsed = Math.min(visible.fadeSeconds(), elapsed + Math.max(0, seconds));
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
