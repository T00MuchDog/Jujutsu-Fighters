package com.jjktbf.graphics.animation;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Presentation timelines only. No gameplay durations or combat outcomes live here. */
public final class BattleChoreography {
    public record Pose(float x, float y, float scaleX, float scaleY, float rotation,
                       float alpha, float red, float green, float blue) {
        public static final Pose IDENTITY = new Pose(0, 0, 1, 1, 0, 1, 1, 1, 1);

        Pose interpolate(Pose other, float t) {
            return new Pose(mix(x, other.x, t), mix(y, other.y, t),
                mix(scaleX, other.scaleX, t), mix(scaleY, other.scaleY, t),
                mix(rotation, other.rotation, t), mix(alpha, other.alpha, t),
                mix(red, other.red, t), mix(green, other.green, t), mix(blue, other.blue, t));
        }

        private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    }

    public record Keyframe(float at, Pose pose, String easing) { }

    public record Track(List<Keyframe> keys) {
        public static final Track EMPTY = new Track(List.of());
        public Track { keys = List.copyOf(keys); }

        public Pose sample(float progress) {
            if (keys.isEmpty()) return Pose.IDENTITY;
            Keyframe previous = keys.get(0);
            if (progress <= previous.at()) return previous.pose();
            for (int i = 1; i < keys.size(); i++) {
                Keyframe next = keys.get(i);
                if (progress <= next.at()) {
                    float t = (progress - previous.at()) / (next.at() - previous.at());
                    t = switch (next.easing()) {
                        case "smooth" -> t * t * (3f - 2f * t);
                        case "step" -> t < 1f ? 0f : 1f;
                        default -> t;
                    };
                    return previous.pose().interpolate(next.pose(), t);
                }
                previous = next;
            }
            return previous.pose();
        }
    }

    /** Effect times are seconds; transform keyframe positions are fractions of that layer's lifetime. */
    public record Layer(String effect, String placement, String plane, float startSeconds,
                        float durationSeconds, float size, Track transform) { }

    public record Profile(float durationSeconds, float impactSeconds, float size,
                          Track source, Track target, Track background, List<Layer> layers) {
        public static final Profile DEFAULT = new Profile(0, -1, 1.6f,
            Track.EMPTY, Track.EMPTY, Track.EMPTY, List.of());
        public Profile { layers = List.copyOf(layers); }
    }

    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final Map<String, String> effects = new LinkedHashMap<>();
    private final Map<String, String> events = new LinkedHashMap<>();
    private final Map<String, String> roles = new LinkedHashMap<>();

    public BattleChoreography(FileHandle file) {
        JsonValue root = new JsonReader().parse(file);
        if (root.getInt("schemaVersion", 0) != 1) throw invalid("unsupported schemaVersion");
        JsonValue entries = root.require("profiles");
        if (!entries.isObject()) throw invalid("profiles must be an object");
        for (JsonValue entry : entries) {
            float duration = number(entry, "durationSeconds", 0, 0, 60);
            float impact = number(entry, "impactSeconds", -1, -1, 60);
            if (duration > 0 && impact > duration) throw invalid("impact is after duration");
            List<Layer> layers = new ArrayList<>();
            JsonValue layerArray = entry.get("layers");
            if (layerArray != null) {
                if (!layerArray.isArray()) throw invalid("layers must be an array");
                for (JsonValue layer : layerArray) {
                    String placement = choice(layer, "placement", "target", "source", "target", "screen", "beam", "projectile");
                    String plane = choice(layer, "plane", "front", "background", "behind", "front");
                    layers.add(new Layer(layer.getString("effect"), placement, plane,
                        number(layer, "startSeconds", 0, 0, 60),
                        number(layer, "durationSeconds", 0, 0, 60),
                        number(layer, "size", 1.6f, 0.01f, 20), track(layer.get("transform"))));
                }
            }
            if (profiles.putIfAbsent(entry.name, new Profile(duration, impact,
                number(entry, "size", 1.6f, 0.01f, 20), track(entry.get("source")),
                track(entry.get("target")), track(entry.get("background")), layers)) != null) {
                throw invalid("duplicate profile " + entry.name);
            }
        }
        bindings(root.get("effects"), effects);
        bindings(root.get("events"), events);
        bindings(root.get("roles"), roles);
    }

    public Profile forEffect(BattleEffectPack.Effect effect) {
        String id = effects.getOrDefault(effect.id(), roles.get(effect.role()));
        return profiles.getOrDefault(id, Profile.DEFAULT);
    }

    public Profile forEvent(String event) { return profiles.get(events.get(event)); }

    public List<Profile> profiles() { return List.copyOf(profiles.values()); }

    private void bindings(JsonValue entries, Map<String, String> destination) {
        if (entries == null) return;
        if (!entries.isObject()) throw invalid("bindings must be an object");
        for (JsonValue entry : entries) {
            if (!entry.isString() || !profiles.containsKey(entry.asString())) {
                throw invalid("unknown profile for " + entry.name);
            }
            if (destination.putIfAbsent(entry.name, entry.asString()) != null) {
                throw invalid("duplicate binding " + entry.name);
            }
        }
    }

    private static Track track(JsonValue entries) {
        if (entries == null) return Track.EMPTY;
        if (!entries.isArray()) throw invalid("track must be a keyframe array");
        List<Keyframe> keys = new ArrayList<>();
        float previous = -1;
        for (JsonValue entry : entries) {
            float at = number(entry, "at", 0, 0, 1);
            if (at <= previous) throw invalid("keyframes must be strictly increasing");
            previous = at;
            Pose pose = new Pose(number(entry, "x", 0, -10, 10), number(entry, "y", 0, -10, 10),
                number(entry, "scaleX", 1, 0.01f, 10), number(entry, "scaleY", 1, 0.01f, 10),
                number(entry, "rotation", 0, -3600, 3600), number(entry, "alpha", 1, 0, 1),
                number(entry, "red", 1, 0, 1), number(entry, "green", 1, 0, 1),
                number(entry, "blue", 1, 0, 1));
            keys.add(new Keyframe(at, pose, choice(entry, "easing", "linear", "linear", "smooth", "step")));
        }
        return new Track(keys);
    }

    private static float number(JsonValue value, String key, float fallback, float min, float max) {
        JsonValue field = value.get(key);
        if (field == null) return fallback;
        if (!field.isNumber()) throw invalid(key + " must be a number");
        float number = field.asFloat();
        if (!Float.isFinite(number) || number < min || number > max) throw invalid("invalid " + key);
        return number;
    }

    private static String choice(JsonValue value, String key, String fallback, String... choices) {
        String selected = value.getString(key, fallback);
        if (!List.of(choices).contains(selected)) throw invalid("invalid " + key + ": " + selected);
        return selected;
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Invalid battle choreography: " + reason);
    }
}
