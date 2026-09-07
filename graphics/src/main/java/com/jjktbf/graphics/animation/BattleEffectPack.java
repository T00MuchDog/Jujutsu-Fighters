package com.jjktbf.graphics.animation;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lazy, metadata-driven renderer for the generated pixel-effects pack. */
public final class BattleEffectPack implements Disposable {
    public record Effect(String id, String sheet, int frameCount, float frameSeconds,
                         boolean loop, float anchorX, float anchorY, String placement,
                         String role, List<Integer> impactFrames, String reinforcementSheet,
                         String castEffect) {
        public Effect {
            impactFrames = List.copyOf(impactFrames == null ? List.of() : impactFrames);
        }

        public float durationSeconds() {
            return frameCount * frameSeconds;
        }
    }

    /** A contiguous, end-exclusive portion of an effect's sprite sheet. */
    public record Clip(Effect effect, int firstFrame, int endFrame, int impactFrame) {
        public float durationSeconds() {
            return (endFrame - firstFrame) * effect.frameSeconds();
        }

        public float impactSeconds() {
            return impactFrame < 0 ? -1f : (impactFrame - firstFrame) * effect.frameSeconds();
        }
    }

    private final FileHandle root;
    private final int frameWidth;
    private final int frameHeight;
    private final int columns;
    private final Map<String, Effect> effects = new LinkedHashMap<>();
    private final Map<String, Effect> moveEffects = new LinkedHashMap<>();
    private final Map<String, Effect> eventEffects = new LinkedHashMap<>();
    private final Map<String, Effect> statusEffects = new LinkedHashMap<>();
    private final Map<String, Texture> textures = new LinkedHashMap<>();
    private boolean disposed;

    public BattleEffectPack(FileHandle root) {
        if (root == null) {
            throw new IllegalArgumentException("Effect-pack root cannot be null");
        }
        this.root = root;

        FileHandle manifestFile = root.child("manifest.json");
        if (!manifestFile.exists() || manifestFile.isDirectory()) {
            throw invalid("missing manifest.json");
        }
        JsonValue manifest = new JsonReader().parse(manifestFile);
        if (!manifest.isObject()) {
            throw invalid("manifest must be an object");
        }
        if (positiveInt(manifest, "schemaVersion") != 1) {
            throw invalid("unsupported schemaVersion");
        }
        if (!"row-major-top-left".equals(requiredString(manifest, "sheetOrder"))) {
            throw invalid("sheetOrder must be row-major-top-left");
        }
        frameWidth = positiveInt(manifest, "frameWidth");
        frameHeight = positiveInt(manifest, "frameHeight");
        columns = positiveInt(manifest, "columns");

        JsonValue effectArray = manifest.get("effects");
        if (effectArray == null || !effectArray.isArray()) {
            throw invalid("manifest.effects must be an array");
        }
        for (JsonValue value : effectArray) {
            readEffect(value);
        }
    }

    public Effect effectForMove(String moveId) {
        return moveEffects.get(moveId);
    }

    public Effect effectForEvent(String eventType) {
        return eventEffects.get(eventType);
    }

    public Effect effect(String id) {
        return effects.get(id);
    }

    public Collection<Effect> effects() {
        return Collections.unmodifiableCollection(effects.values());
    }

    /**
     * Selects one authored contact from a move. Contacts are separated at the
     * midpoint between adjacent impact frames, so no returned clip contains a
     * second contact. A missing component selects the first contact; an index
     * beyond the authored contacts selects the final contact.
     */
    public Clip clip(Effect effect, Integer componentIndex) {
        requireKnown(effect);
        List<Integer> impacts = effect.impactFrames();
        if (impacts.size() <= 1) {
            return fullClip(effect);
        }
        int index = componentIndex == null ? 0 : Math.max(0, componentIndex);
        index = Math.min(index, impacts.size() - 1);

        int first = index == 0 ? 0 : splitBoundary(impacts.get(index - 1), impacts.get(index));
        int end = index == impacts.size() - 1
                ? effect.frameCount()
                : splitBoundary(impacts.get(index), impacts.get(index + 1));
        return new Clip(effect, first, end, impacts.get(index));
    }

    /** Returns the complete animation, intended for activation/event effects. */
    public Clip fullClip(Effect effect) {
        requireKnown(effect);
        int impact = effect.impactFrames().isEmpty() ? -1 : effect.impactFrames().get(0);
        return new Clip(effect, 0, effect.frameCount(), impact);
    }

    /** Loads and validates an effect's textures on the render thread. */
    public boolean prepare(Effect effect) {
        ensureOpen();
        requireKnown(effect);
        textureFor(effect.sheet(), effect.frameCount(), effect.id());
        if (effect.reinforcementSheet() != null) {
            textureFor(effect.reinforcementSheet(), effect.frameCount(), effect.id());
        }
        return true;
    }

    /**
     * Draws the selected clip without changing the batch's tint or blend
     * function. The supplied width is scaled to the source tile's aspect ratio.
     */
    public boolean draw(Batch batch, Clip clip, float elapsedSeconds, float centerX, float centerY,
                         float width, boolean mirrored, boolean reinforced) {
        return draw(batch, clip, elapsedSeconds, centerX, centerY, width, mirrored, reinforced, 1, 0);
    }

    public boolean draw(Batch batch, Clip clip, float elapsedSeconds, float centerX, float centerY,
                        float width, boolean mirrored, boolean reinforced, float heightScale, float rotation) {
        ensureOpen();
        if (batch == null) {
            throw new IllegalArgumentException("batch cannot be null");
        }
        if (!Float.isFinite(elapsedSeconds) || elapsedSeconds < 0f
                || !Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(width) || width <= 0f || !Float.isFinite(heightScale)
                || heightScale <= 0 || !Float.isFinite(rotation)) {
            throw new IllegalArgumentException("draw coordinates, elapsed time, and width must be finite");
        }
        if (clip == null) {
            throw new IllegalArgumentException("clip cannot be null");
        }
        requireKnown(clip.effect());
        if (clip.firstFrame() < 0 || clip.endFrame() > clip.effect().frameCount()
                || clip.firstFrame() >= clip.endFrame()) {
            throw invalid("clip frame range is outside effect: " + clip.effect().id());
        }

        float duration = clip.durationSeconds();
        if (!clip.effect().loop() && elapsedSeconds >= duration) {
            return false;
        }
        float localTime = clip.effect().loop() ? elapsedSeconds % duration : elapsedSeconds;
        int frame = clip.firstFrame() + Math.min(
                (int) Math.floor(localTime / clip.effect().frameSeconds()),
                clip.endFrame() - clip.firstFrame() - 1);

        prepare(clip.effect());
        Texture base = textures.get(clip.effect().sheet());
        int column = frame % columns;
        int row = frame / columns;
        int srcX = column * frameWidth;
        int srcY = row * frameHeight;
        float height = width * frameHeight / (float) frameWidth * heightScale;
        float originX = width * (mirrored ? 1f - clip.effect().anchorX() : clip.effect().anchorX());
        float originY = height * clip.effect().anchorY();
        float x = centerX - originX;
        float y = centerY - originY;

        if (reinforced && clip.effect().reinforcementSheet() != null) {
            batch.draw(textures.get(clip.effect().reinforcementSheet()), x, y, originX, originY,
                    width, height, 1, 1, rotation, srcX, srcY, frameWidth, frameHeight, mirrored, false);
        }
        batch.draw(base, x, y, originX, originY, width, height, 1, 1, rotation,
                srcX, srcY, frameWidth, frameHeight, mirrored, false);
        return true;
    }

    public float aspectRatio() { return frameWidth / (float) frameHeight; }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (Texture texture : textures.values()) {
            texture.dispose();
        }
        textures.clear();
    }

    private void readEffect(JsonValue value) {
        if (!value.isObject()) {
            throw invalid("each effects entry must be an object");
        }
        String id = requiredString(value, "id");
        if (effects.containsKey(id)) {
            throw invalid("duplicate effect id: " + id);
        }
        String sheet = requiredString(value, "sheet");
        validateSheet(sheet);
        requireSheet(sheet, "effect sheet");

        int frameCount = positiveInt(value, "frameCount");
        float frameSeconds = positiveNumber(value, "frameDurationMs") / 1000f;
        if (!Float.isFinite(frameSeconds) || frameSeconds <= 0f
                || !Float.isFinite(frameCount * frameSeconds)) {
            throw invalid("frameDurationMs must produce finite positive timing for effect: " + id);
        }
        boolean loop = requiredBoolean(value, "loop");

        JsonValue anchor = value.get("anchor");
        if (anchor == null || !anchor.isArray() || anchor.size != 2) {
            throw invalid("anchor must contain two numbers for effect: " + id);
        }
        float anchorX = finiteNumber(anchor.get(0), "anchor[0]");
        float anchorY = finiteNumber(anchor.get(1), "anchor[1]");
        if (anchorX < 0f || anchorX > 1f || anchorY < 0f || anchorY > 1f) {
            throw invalid("anchor values must be between 0 and 1 for effect: " + id);
        }

        String placement = requiredString(value, "placement");
        if (!placement.equals("source") && !placement.equals("target")
                && !placement.equals("beam") && !placement.equals("projectile")) {
            throw invalid("placement must be source, target, beam, or projectile for effect: " + id);
        }
        String role = value.get("role") == null
                ? (placement.equals("target") ? "attack" : "utility")
                : requiredString(value, "role");
        List<Integer> impactFrames = impactFrames(value, frameCount, id);
        String reinforcementSheet = readReinforcement(value, id, frameCount, frameSeconds);
        String castEffect = optionalString(value, "castEffect");

        Effect effect = new Effect(id, sheet, frameCount, frameSeconds, loop, anchorX, anchorY,
                placement, role, impactFrames, reinforcementSheet, castEffect);
        effects.put(id, effect);
        bind(moveEffects, bindings(value, "moveIds"), effect, "moveIds");
        bind(eventEffects, bindings(value, "eventTypes"), effect, "eventTypes");
        bind(statusEffects, bindings(value, "statusTypes"), effect, "statusTypes");
    }

    private List<Integer> impactFrames(JsonValue effect, int frameCount, String id) {
        JsonValue values = effect.get("impactFrames");
        if (values == null) {
            return List.of();
        }
        if (!values.isArray()) {
            throw invalid("impactFrames must be an array for effect: " + id);
        }
        List<Integer> result = new ArrayList<>();
        int previous = -1;
        for (JsonValue value : values) {
            int frame = integer(value, "impactFrames");
            if (frame < 0 || frame >= frameCount || frame <= previous) {
                throw invalid("impactFrames must be ascending and in bounds for effect: " + id);
            }
            result.add(frame);
            previous = frame;
        }
        return List.copyOf(result);
    }

    private String readReinforcement(JsonValue effect, String id, int frameCount, float frameSeconds) {
        JsonValue reinforcement = effect.get("reinforcement");
        if (reinforcement == null) {
            return null;
        }
        if (!reinforcement.isObject()) {
            throw invalid("reinforcement must be an object for effect: " + id);
        }
        if (!"cursed-energy-coating".equals(requiredString(reinforcement, "type"))) {
            throw invalid("unsupported reinforcement type for effect: " + id);
        }
        String sheet = requiredString(reinforcement, "sheet");
        validateSheet(sheet);
        requireSheet(sheet, "reinforcement sheet");
        int reinforcementFrameCount = positiveInt(reinforcement, "frameCount");
        float reinforcementSeconds = positiveNumber(reinforcement, "frameDurationMs") / 1000f;
        if (reinforcementFrameCount != frameCount || Float.compare(reinforcementSeconds, frameSeconds) != 0) {
            throw invalid("reinforcement timing must match base effect: " + id);
        }
        if (!"behind".equals(requiredString(reinforcement, "drawOrder"))) {
            throw invalid("reinforcement drawOrder must be behind for effect: " + id);
        }
        return sheet;
    }

    private Texture textureFor(String sheet, int frameCount, String effectId) {
        Texture existing = textures.get(sheet);
        if (existing != null) {
            validateTexture(existing, sheet, frameCount, effectId);
            return existing;
        }
        Texture texture = new Texture(root.child(sheet));
        try {
            validateTexture(texture, sheet, frameCount, effectId);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            textures.put(sheet, texture);
            return texture;
        } catch (RuntimeException failure) {
            texture.dispose();
            throw failure;
        }
    }

    private void validateTexture(Texture texture, String sheet, int frameCount, String effectId) {
        if (texture.getWidth() != (long) columns * frameWidth
                || texture.getHeight() <= 0 || texture.getHeight() % frameHeight != 0) {
            throw invalid("sheet dimensions are not a complete configured tile grid: " + sheet);
        }
        long capacity = (long) columns * (texture.getHeight() / frameHeight);
        if (frameCount > capacity) {
            throw invalid("frameCount does not fit sheet grid: " + effectId);
        }
    }

    private void requireSheet(String sheet, String label) {
        validateExternalContainment(sheet);
        FileHandle file = root.child(sheet);
        if (!file.exists() || file.isDirectory()) {
            throw invalid("missing " + label + ": " + sheet);
        }
    }

    private void validateExternalContainment(String relative) {
        if (root.type() != FileType.External && root.type() != FileType.Local
                && root.type() != FileType.Absolute) {
            return;
        }
        try {
            Path base = root.file().toPath().toRealPath();
            Path candidate = root.child(relative).file().toPath().toRealPath();
            if (!candidate.startsWith(base)) {
                throw invalid("path escapes effect-pack root: " + relative);
            }
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof IllegalArgumentException argument) {
                throw argument;
            }
            throw invalid("cannot validate effect-pack path: " + relative);
        }
    }

    private static List<String> bindings(JsonValue effect, String field) {
        JsonValue values = effect.get(field);
        if (values == null) {
            return List.of();
        }
        if (!values.isArray()) {
            throw invalid(field + " must be an array");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (JsonValue value : values) {
            if (!value.isString() || value.asString().isBlank() || !unique.add(value.asString())) {
                throw invalid(field + " must contain unique, non-empty strings");
            }
        }
        return List.copyOf(unique);
    }

    private static void bind(Map<String, Effect> target, List<String> keys, Effect effect, String field) {
        for (String key : keys) {
            if (target.putIfAbsent(key, effect) != null) {
                throw invalid("duplicate " + field + " binding: " + key);
            }
        }
    }

    private static int splitBoundary(int leftImpact, int rightImpact) {
        // Keep the left contact in the left slice even when contacts are adjacent.
        return Math.max(leftImpact + 1, (leftImpact + rightImpact) / 2);
    }

    private void requireKnown(Effect effect) {
        if (effect == null || effects.get(effect.id()) != effect) {
            throw new IllegalArgumentException("Effect does not belong to this pack");
        }
    }

    private void ensureOpen() {
        if (disposed) {
            throw new IllegalStateException("Effect pack is disposed");
        }
    }

    private static void validateSheet(String sheet) {
        if (sheet.startsWith("/") || sheet.startsWith("\\") || sheet.indexOf('\0') >= 0
                || sheet.indexOf(':') >= 0 || sheet.contains("\\")) {
            throw invalid("unsafe sheet path: " + sheet);
        }
        for (String part : sheet.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw invalid("unsafe sheet path: " + sheet);
            }
        }
    }

    private static int positiveInt(JsonValue object, String field) {
        JsonValue value = object.get(field);
        int result = integer(value, field);
        if (result <= 0) {
            throw invalid(field + " must be a positive integer");
        }
        return result;
    }

    private static int integer(JsonValue value, String field) {
        if (value == null || !value.isNumber() || !value.isLong()
                || value.asLong() < Integer.MIN_VALUE || value.asLong() > Integer.MAX_VALUE) {
            throw invalid(field + " must be an integer");
        }
        return (int) value.asLong();
    }

    private static float positiveNumber(JsonValue object, String field) {
        JsonValue value = object.get(field);
        float result = finiteNumber(value, field);
        if (result <= 0f) {
            throw invalid(field + " must be positive");
        }
        return result;
    }

    private static float finiteNumber(JsonValue value, String field) {
        if (value == null || !value.isNumber() || !Float.isFinite(value.asFloat())) {
            throw invalid(field + " must be a finite number");
        }
        return value.asFloat();
    }

    private static boolean requiredBoolean(JsonValue object, String field) {
        JsonValue value = object.get(field);
        if (value == null || !value.isBoolean()) {
            throw invalid(field + " must be boolean");
        }
        return value.asBoolean();
    }

    private static String requiredString(JsonValue object, String field) {
        JsonValue value = object.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw invalid(field + " must be a non-empty string");
        }
        return value.asString();
    }

    private static String optionalString(JsonValue object, String field) {
        JsonValue value = object.get(field);
        if (value == null) return null;
        if (!value.isString() || value.asString().isBlank()) {
            throw invalid(field + " must be a non-empty string when supplied");
        }
        return value.asString();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid effect pack: " + message);
    }
}
