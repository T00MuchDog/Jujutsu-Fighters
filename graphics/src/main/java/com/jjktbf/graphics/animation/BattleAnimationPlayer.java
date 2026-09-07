package com.jjktbf.graphics.animation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.jjktbf.graphics.ui.CombatantPanel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.jjktbf.graphics.animation.BattleChoreography.*;

/** Render-thread player shared by local and authoritative online event playback. */
public final class BattleAnimationPlayer implements Disposable {
    public static final String DIRECTORY_PROPERTY = "jjktbf.animationsDir";
    private final List<BattleEffectPack> packs = new ArrayList<>();
    private final Set<String> failedEffects = new HashSet<>();
    private BattleChoreography choreography;
    // The local controller may observe the clock, but only the render thread mutates it.
    private volatile Playback active;

    private record EffectRef(BattleEffectPack pack, BattleEffectPack.Effect effect) { }
    private record Visual(EffectRef ref, BattleEffectPack.Clip clip, String placement, String plane,
                          float start, float duration, float size, Track transform) { }

    private record Sequence(Profile profile, List<Visual> visuals,
                            Supplier<CombatantPanel> source, Supplier<CombatantPanel> target,
                            boolean mirrored, boolean reinforced, float duration, float impact) { }

    private static final class Playback {
        final List<Sequence> sequences;
        final float duration;
        final float impact;
        volatile float elapsed;

        Playback(List<Sequence> sequences) {
            this.sequences = List.copyOf(sequences);
            float contact = 0;
            for (Sequence sequence : sequences) contact = Math.max(contact, sequence.impact());
            impact = contact;
            float end = 0;
            for (Sequence sequence : sequences) {
                end = Math.max(end, impact - sequence.impact() + sequence.duration());
            }
            duration = end;
        }

        float time(Sequence sequence) { return elapsed - (impact - sequence.impact()); }
    }

    /** Reloaded at battle entry, so edits/additions do not require a game rebuild. */
    public void reload() {
        dispose();
        failedEffects.clear();
        try {
            JsonValue catalog = new JsonReader().parse(file("catalog.json"));
            if (catalog.getInt("schemaVersion", 0) != 1) throw new IllegalArgumentException("catalog schemaVersion");
            JsonValue roots = catalog.require("packs");
            if (!roots.isArray()) throw new IllegalArgumentException("catalog packs must be an array");
            Set<String> ids = new HashSet<>();
            for (JsonValue root : roots) {
                String path = root.asString();
                BattleEffectPack pack = new BattleEffectPack(file(path + "/manifest.json").parent());
                packs.add(pack);
                for (BattleEffectPack.Effect effect : pack.effects()) {
                    if (!ids.add(effect.id())) throw new IllegalArgumentException("duplicate effect id: " + effect.id());
                }
            }
            for (BattleEffectPack pack : packs) {
                for (BattleEffectPack.Effect effect : pack.effects()) {
                    if (effect.castEffect() != null) {
                        EffectRef cast = effect(effect.castEffect());
                        if (cast == null || !cast.effect().placement().equals("source")) {
                            throw new IllegalArgumentException("cast effect must name a source effect: " + effect.castEffect());
                        }
                    }
                }
            }
            choreography = new BattleChoreography(file("choreography.json"));
            for (Profile profile : choreography.profiles()) {
                for (Layer layer : profile.layers()) {
                    if (effect(layer.effect()) == null) throw new IllegalArgumentException("unknown layer effect: " + layer.effect());
                }
            }
        } catch (RuntimeException failure) {
            report("Cannot load animation catalog; using existing battle visuals", failure);
            dispose();
        }
    }

    static FileHandle file(String relative) {
        if (relative.isBlank() || relative.startsWith("/") || relative.contains("\\")
            || relative.contains(":") || List.of(relative.split("/", -1)).stream()
                .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
            throw new IllegalArgumentException("Unsafe animation path: " + relative);
        }
        String override = System.getProperty(DIRECTORY_PROPERTY);
        if (override != null && !override.isBlank()) {
            FileHandle external = Gdx.files.absolute(override).child(relative);
            if (external.exists()) return external;
        }
        FileHandle local = Gdx.files.local("animations").child(relative);
        if (local.exists()) return local;
        // Classpath avoids working-directory-dependent behavior in a packaged desktop app.
        return Gdx.files.classpath("assets/animations/" + relative);
    }

    public boolean hasMove(String moveId) {
        EffectRef ref = moveEffect(moveId);
        return ref != null && !failedEffects.contains(ref.effect().id())
            && !failedEffects.contains(ref.effect().castEffect());
    }

    public boolean handlesEvent(String event) {
        if (choreography == null) return false;
        if (choreography.forEvent(event) != null) return true;
        for (BattleEffectPack pack : packs) {
            if (pack.effectForEvent(event) != null) return true;
        }
        return event.equals("MOVE_FIRED") || event.equals("MOVE_TARGETED") || event.equals("DEFENSE_GRANTED")
            || event.equals("MOVE_BLOCKED") || event.equals("MOVE_BLOCK_REDUCED")
            || event.equals("DAMAGE_DEALT") || event.equals("DAMAGE_IGNORED");
    }

    private EffectRef moveEffect(String moveId) {
        if (moveId == null) return null;
        for (BattleEffectPack pack : packs) {
            BattleEffectPack.Effect effect = pack.effectForMove(moveId);
            if (effect != null) return new EffectRef(pack, effect);
        }
        return null;
    }

    private EffectRef effect(String id) {
        for (BattleEffectPack pack : packs) {
            BattleEffectPack.Effect effect = pack.effect(id);
            if (effect != null) return new EffectRef(pack, effect);
        }
        return null;
    }

    /** Both clients route the same semantic events, never parsed log text or move names. */
    public boolean play(String event, String moveId, Integer componentIndex,
                        Supplier<CombatantPanel> source, Supplier<CombatantPanel> target,
                        boolean mirrored, boolean reinforced) {
        return play(event, moveId, componentIndex, source, target, mirrored, reinforced, null, false);
    }

    public boolean play(String event, String moveId, Integer componentIndex,
                        Supplier<CombatantPanel> source, Supplier<CombatantPanel> target,
                        boolean mirrored, boolean reinforced, String defenseMoveId, boolean defenseReinforced) {
        if (choreography == null) return false;
        boolean damage = event.equals("DAMAGE_DEALT") || event.equals("DAMAGE_IGNORED");
        boolean blocked = event.equals("MOVE_BLOCKED") || event.equals("MOVE_BLOCK_REDUCED")
            || event.equals("MOVE_PARRIED") || (damage && defenseMoveId != null);
        if (blocked) {
            try {
                List<Sequence> sequences = new ArrayList<>();
                EffectRef attack = moveEffect(moveId);
                if (attack != null && attack.effect().role().equals("attack")) {
                    Profile profile = choreography.forEffect(attack.effect());
                    // A defended contact uses the guard's pose, not the attack's damage flinch.
                    profile = new Profile(profile.durationSeconds(), profile.impactSeconds(), profile.size(),
                        profile.source(), Track.EMPTY, profile.background(), profile.layers());
                    sequences.add(sequence(attack, profile, componentIndex, true, source, target, mirrored, reinforced));
                }
                EffectRef guard = moveEffect(defenseMoveId);
                if (guard != null) {
                    boolean targetPlaced = guard.effect().placement().equals("target");
                    sequences.add(sequence(guard, choreography.forEffect(guard.effect()), null, false,
                        targetPlaced ? source : target, targetPlaced ? target : source, !mirrored, defenseReinforced));
                } else {
                    EffectRef eventEffect = null;
                    for (BattleEffectPack pack : packs) {
                        BattleEffectPack.Effect bound = pack.effectForEvent(event);
                        if (bound != null) { eventEffect = new EffectRef(pack, bound); break; }
                    }
                    Profile reaction = choreography.forEvent(event);
                    if (reaction == null) reaction = choreography.forEvent("MOVE_BLOCKED");
                    if (reaction != null || eventEffect != null) {
                        sequences.add(sequence(eventEffect, reaction == null
                            ? choreography.forEffect(eventEffect.effect()) : reaction, null, false,
                            source, target, mirrored, false));
                    }
                }
                if (sequences.isEmpty()) return false;
                active = new Playback(sequences);
                return true;
            } catch (RuntimeException failure) {
                report("Cannot play blocked animation for " + moveId, failure);
                clear();
                return false;
            }
        }
        EffectRef main = moveEffect(moveId);
        if (event.equals("MOVE_FIRED") && main != null && main.effect().castEffect() != null) {
            if (failedEffects.contains(main.effect().castEffect())) return false;
            try {
                EffectRef cast = effect(main.effect().castEffect());
                if (cast == null) return false;
                // Fire has no resolved recipient. Cast once here, never once per AOE target or recoil hit.
                Profile castProfile = choreography.forEffect(cast.effect());
                Sequence sequence = sequence(cast, castProfile, null, false,
                    source, target, mirrored, reinforced);
                if (sequence.duration() <= 0) return false;
                active = new Playback(List.of(sequence));
                return true;
            } catch (RuntimeException failure) {
                failedEffects.add(main.effect().castEffect());
                report("Cannot play cast animation for " + moveId, failure);
                clear();
                return false;
            }
        }
        EffectRef ref = null;
        boolean contact = false;
        for (BattleEffectPack pack : packs) {
            BattleEffectPack.Effect bound = pack.effectForEvent(event);
            if (bound != null) { ref = new EffectRef(pack, bound); break; }
        }
        Profile profile = choreography.forEvent(event);
        EffectRef targeted = moveEffect(moveId);
        // A targeted move owns MOVE_TARGETED even when choreography supplies a
        // generic event fallback. This keeps the fallback from replacing or
        // duplicating the move's source-to-target visual.
        if (event.equals("MOVE_TARGETED") && targeted != null && targeted.effect().role().equals("targeted")) {
            ref = targeted;
            profile = choreography.forEffect(ref.effect());
        }
        if (ref == null && profile == null) {
            ref = moveEffect(moveId);
            if (ref == null) return false;
            // Guard activation arms gameplay protection; its visual belongs to the collision.
            if (ref.effect().role().equals("guard")) return false;
            if (ref.effect().role().equals("attack")) {
                contact = damage;
                if (!contact) return false;
            } else if (ref.effect().role().equals("targeted")) {
                if (!event.equals("MOVE_TARGETED")) return false;
            } else if (!(event.equals("MOVE_FIRED") && ref.effect().placement().equals("source"))
                && !(event.equals("DEFENSE_GRANTED") && ref.effect().placement().equals("target"))) {
                return false;
            }
        }
        if (ref != null && failedEffects.contains(ref.effect().id())) return false;
        if (profile == null) profile = choreography.forEffect(ref.effect());
        try {
            Sequence sequence = sequence(ref, profile, componentIndex, contact, source, target, mirrored, reinforced);
            if (sequence.duration() <= 0) return false;
            active = new Playback(List.of(sequence));
            return true;
        } catch (RuntimeException failure) {
            if (ref != null) failedEffects.add(ref.effect().id());
            report("Cannot play animation for " + event + " / " + moveId, failure);
            clear();
            return false;
        }
    }

    private Sequence sequence(EffectRef ref, Profile profile, Integer componentIndex, boolean contact,
                              Supplier<CombatantPanel> source, Supplier<CombatantPanel> target,
                              boolean mirrored, boolean reinforced) {
        List<Visual> visuals = new ArrayList<>();
        float duration = profile.durationSeconds();
        float impact = profile.impactSeconds();
        if (ref != null) {
            BattleEffectPack.Clip clip = contact
                ? ref.pack().clip(ref.effect(), componentIndex) : ref.pack().fullClip(ref.effect());
            if (duration == 0) duration = clip.durationSeconds();
            if (impact < 0) impact = Math.max(0, clip.impactSeconds()) / clip.durationSeconds() * duration;
            ref.pack().prepare(ref.effect());
            visuals.add(new Visual(ref, clip, ref.effect().placement(), "front", 0,
                duration, profile.size(), Track.EMPTY));
        }
        for (Layer layer : profile.layers()) {
            EffectRef extra = effect(layer.effect());
            extra.pack().prepare(extra.effect());
            float length = layer.durationSeconds() > 0 ? layer.durationSeconds() : extra.effect().durationSeconds();
            visuals.add(new Visual(extra, extra.pack().fullClip(extra.effect()), layer.placement(),
                layer.plane(), layer.startSeconds(), length, layer.size(), layer.transform()));
            duration = Math.max(duration, layer.startSeconds() + length);
        }
        return new Sequence(profile, List.copyOf(visuals), source, target, mirrored, reinforced,
            duration, Math.max(0, Math.min(duration, impact)));
    }

    public boolean isPlaying() { return active != null; }

    public boolean isBeforeImpact() {
        Playback playback = active;
        return playback != null && playback.elapsed < playback.impact;
    }

    public void update(float seconds) {
        Playback playback = active;
        if (playback == null) return;
        playback.elapsed += Float.isFinite(seconds) ? Math.max(0, seconds) : 0;
        if (playback.elapsed >= playback.duration) clear();
    }

    public void clear() { active = null; }

    public Pose poseFor(CombatantPanel panel) {
        Playback playback = active;
        if (playback == null || panel == null) return Pose.IDENTITY;
        Pose pose = Pose.IDENTITY;
        for (Sequence sequence : playback.sequences) {
            float time = playback.time(sequence);
            if (time < 0 || time >= sequence.duration()) continue;
            Track track = panel == sequence.source().get() ? sequence.profile().source()
                : panel == sequence.target().get() ? sequence.profile().target() : Track.EMPTY;
            pose = compose(pose, oriented(track.sample(time / sequence.duration()), sequence.mirrored()));
        }
        return pose;
    }

    private static Pose compose(Pose a, Pose b) {
        return new Pose(a.x() + b.x(), a.y() + b.y(), a.scaleX() * b.scaleX(), a.scaleY() * b.scaleY(),
            a.rotation() + b.rotation(), a.alpha() * b.alpha(),
            a.red() * b.red(), a.green() * b.green(), a.blue() * b.blue());
    }

    private static Pose oriented(Pose pose, boolean mirrored) {
        if (!mirrored || (pose.x() == 0 && pose.rotation() == 0)) return pose;
        return new Pose(-pose.x(), pose.y(), pose.scaleX(), pose.scaleY(), -pose.rotation(),
            pose.alpha(), pose.red(), pose.green(), pose.blue());
    }

    public Pose backgroundPose() {
        Playback playback = active;
        if (playback == null) return Pose.IDENTITY;
        Pose pose = Pose.IDENTITY;
        for (Sequence sequence : playback.sequences) {
            float time = playback.time(sequence);
            if (time >= 0 && time < sequence.duration()) {
                pose = compose(pose, sequence.profile().background().sample(time / sequence.duration()));
            }
        }
        return pose;
    }

    /** Called around fighter rendering, before the HUD, in the existing viewport/clip. */
    public void draw(Batch batch, Rectangle viewport, String plane) {
        Playback playback = active;
        if (playback == null) return;
        float previous = batch.getPackedColor();
        try {
            for (Sequence sequence : playback.sequences) {
                for (Visual visual : sequence.visuals()) {
                float time = playback.time(sequence) - visual.start();
                if (!visual.plane().equals(plane) || time < 0 || time >= visual.duration()) continue;
                CombatantPanel panel = switch (visual.placement()) {
                    case "source" -> sequence.source().get();
                    case "target" -> sequence.target().get();
                    default -> null;
                };
                boolean screen = visual.placement().equals("screen");
                boolean path = visual.placement().equals("beam") || visual.placement().equals("projectile");
                CombatantPanel source = path ? sequence.source().get() : null;
                CombatantPanel target = path ? sequence.target().get() : null;
                if (!screen && !path && panel == null) continue;
                if (path && (source == null || target == null)) continue;
                Pose pose = oriented(visual.transform().sample(time / visual.duration()), sequence.mirrored());
                Pose fighter = path ? Pose.IDENTITY : poseFor(panel);
                float unit = screen ? Math.min(viewport.width, viewport.height)
                    : path ? (source.spriteHeight() + target.spriteHeight()) / 2f : panel.spriteHeight();
                float x;
                float y;
                float width;
                float heightScale = pose.scaleY() / pose.scaleX();
                float rotation = pose.rotation();
                if (path) {
                    float[] from = transformedCenter(source);
                    float[] to = transformedCenter(target);
                    float dx = to[0] - from[0];
                    float dy = to[1] - from[1];
                    float distance = (float) Math.hypot(dx, dy);
                    if (distance <= 0.0001f) continue;
                    float direction = (float) Math.atan2(dy, dx) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
                    float progress = visual.placement().equals("projectile") ? time / visual.duration() : .5f;
                    x = from[0] + dx * progress;
                    y = from[1] + dy * progress;
                    width = visual.placement().equals("beam") ? distance : unit * visual.size();
                    if (visual.placement().equals("beam")) {
                        float thickness = unit * visual.size() * pose.scaleY();
                        heightScale = thickness * visual.ref().pack().aspectRatio() / (width * pose.scaleX());
                    }
                    rotation += direction;
                } else {
                    float angle = fighter.rotation() * com.badlogic.gdx.math.MathUtils.degreesToRadians;
                    x = screen ? viewport.x + viewport.width / 2 : panel.spriteCenterX()
                        + fighter.x() * unit - (float) Math.sin(angle) * unit / 2 * fighter.scaleY();
                    y = screen ? viewport.y + viewport.height / 2 : panel.spriteCenterY()
                        + fighter.y() * unit + ((float) Math.cos(angle) * fighter.scaleY() - 1) * unit / 2;
                    width = screen ? Math.max(viewport.width,
                        viewport.height * visual.ref().pack().aspectRatio()) * visual.size()
                        : unit * visual.size();
                }
                batch.setColor(pose.red(), pose.green(), pose.blue(), pose.alpha());
                // Stretch a clip's frame timing to its configured lifetime; never spill into another hit.
                float clipTime = visual.ref().effect().loop() ? time
                    : Math.min(Math.nextDown(visual.clip().durationSeconds()),
                        time / visual.duration() * visual.clip().durationSeconds());
                // Path rotation already faces the recipient, including right-to-left shots.
                visual.ref().pack().draw(batch, visual.clip(), clipTime,
                    x + pose.x() * unit, y + pose.y() * unit, width * pose.scaleX(),
                    !screen && !path && sequence.mirrored(), sequence.reinforced(),
                    heightScale, rotation);
                }
            }
        } finally {
            batch.setPackedColor(previous);
        }
    }

    /** Center after the active fighter pose, matching the existing footline transform. */
    private float[] transformedCenter(CombatantPanel panel) {
        Pose pose = poseFor(panel);
        float unit = panel.spriteHeight();
        float angle = pose.rotation() * com.badlogic.gdx.math.MathUtils.degreesToRadians;
        return new float[] {
            panel.spriteCenterX() + pose.x() * unit - (float) Math.sin(angle) * unit / 2 * pose.scaleY(),
            panel.spriteCenterY() + pose.y() * unit + ((float) Math.cos(angle) * pose.scaleY() - 1) * unit / 2
        };
    }

    private static void report(String message, RuntimeException failure) {
        if (Gdx.app != null) Gdx.app.error("BattleAnimation", message, failure);
    }

    @Override public void dispose() {
        clear();
        for (BattleEffectPack pack : packs) pack.dispose();
        packs.clear();
        choreography = null;
    }
}
