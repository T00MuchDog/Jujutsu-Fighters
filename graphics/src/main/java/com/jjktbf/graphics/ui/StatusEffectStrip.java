package com.jjktbf.graphics.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.jjktbf.graphics.ui.battle.BattleUiAssets;
import com.jjktbf.model.combat.BattleCombatant;
import com.jjktbf.model.move.StatusEffect;
import com.jjktbf.model.move.StatusEffectType;
import com.jjktbf.multiplayer.protocol.StatusEffectState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compact active-status badges shared by planning and execution HUDs. */
public final class StatusEffectStrip {

    private static final Color POSITIVE = new Color(0.18f, 0.66f, 0.42f, 1f);
    private static final Color NEGATIVE = new Color(0.88f, 0.28f, 0.22f, 1f);
    private static final Color CONTROL = new Color(0.92f, 0.62f, 0.12f, 1f);
    private static final Color PROTECTION = new Color(0.24f, 0.50f, 0.88f, 1f);
    private static final Color NEUTRAL = new Color(0.45f, 0.49f, 0.58f, 1f);
    private static final Color CHIP_BACKGROUND = new Color(0.035f, 0.050f, 0.090f, 0.96f);

    record Entry(
        StatusEffectType type,
        String displayName,
        int rounds,
        int ticks,
        double magnitude,
        double removalChance,
        double ceUpkeep,
        String source,
        int stacks
    ) { }

    private final GlyphLayout layout = new GlyphLayout();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Rectangle> chipBounds = new ArrayList<>();
    private int hoveredIndex = -1;

    /** Copies live local effects so rendering never retains the mutable engine list. */
    public void setEffects(BattleCombatant combatant) {
        entries.clear();
        if (combatant == null) return;
        for (StatusEffect effect : List.copyOf(combatant.getActiveEffects())) {
            BattleCombatant source = combatant.statusSource(effect).orElse(null);
            String sourceName = source == null
                ? "Unknown"
                : source == combatant
                    ? "Self"
                    : source.getCharacter().getName();
            add(new Entry(
                effect.getType(),
                effect.getType().displayName(),
                effect.getDurationRounds(),
                effect.getDurationTicks(),
                effect.getMagnitude(),
                effect.getPerTickRemovalChance(),
                combatant.getStatusCeUpkeepPerTick(effect.getType()),
                sourceName,
                1));
        }
    }

    /** Copies immutable server snapshots for online planning and execution. */
    public void setEffects(List<StatusEffectState> states) {
        entries.clear();
        if (states == null) return;
        for (StatusEffectState state : states) {
            if (state == null) continue;
            StatusEffectType type = typeOf(state.type());
            String name = state.displayName();
            if (name == null || name.isBlank()) {
                name = type == null ? readableName(state.type()) : type.displayName();
            }
            add(new Entry(
                type,
                name,
                state.remainingRounds(),
                state.remainingTicks(),
                state.magnitude(),
                Double.NaN,
                Double.NaN,
                "",
                1));
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** Conservative clearance for wrapped planner badges before fonts are drawn. */
    public float estimatedHeight(float maxWidth, float geometryScale) {
        if (entries.isEmpty() || maxWidth <= 0f) return 0f;
        float scale = Math.max(0.5f, geometryScale);
        int badgesPerRow = Math.max(1, (int) (maxWidth / (96f * scale)));
        int rows = (entries.size() + badgesPerRow - 1) / badgesPerRow;
        return rows * 21f * scale + (rows + 1) * 3f * scale;
    }

    /**
     * Draws every status, wrapping outward from the HUD rather than hiding
     * overflow. {@code above} chooses whether additional rows grow upward or
     * downward from {@code anchorY}.
     */
    public void draw(
        Batch batch,
        BitmapFont font,
        BattleUiAssets ui,
        float x,
        float anchorY,
        float maxWidth,
        boolean above,
        float pointerX,
        float pointerY,
        float geometryScale,
        float fontScale
    ) {
        draw(batch, font, ui, x, anchorY, maxWidth, above,
            pointerX, pointerY, geometryScale, fontScale, true);
    }

    /** Draws either a wrapping planner strip or a fitted single HUD row. */
    public void draw(
        Batch batch,
        BitmapFont font,
        BattleUiAssets ui,
        float x,
        float anchorY,
        float maxWidth,
        boolean above,
        float pointerX,
        float pointerY,
        float geometryScale,
        float fontScale,
        boolean wrapRows
    ) {
        chipBounds.clear();
        hoveredIndex = -1;
        if (entries.isEmpty() || maxWidth <= 0f) return;

        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        Color originalFontColor = new Color(font.getColor());
        Color originalBatchColor = new Color(batch.getColor());
        float safeGeometryScale = Math.max(0.5f, geometryScale);
        float safeFontScale = Math.max(0.35f, fontScale);
        font.getData().setScale(
            originalScaleX * safeFontScale,
            originalScaleY * safeFontScale);

        float paddingX = 6f * safeGeometryScale;
        float gap = 3f * safeGeometryScale;
        if (!wrapRows) {
            float totalWidth = Math.max(0f, (entries.size() - 1) * gap);
            for (Entry entry : entries) {
                layout.setText(font, badgeText(entry));
                totalWidth += layout.width + paddingX * 2f;
            }
            if (totalWidth > maxWidth) {
                float fit = maxWidth / totalWidth;
                safeGeometryScale *= fit;
                safeFontScale *= fit;
                font.getData().setScale(
                    originalScaleX * safeFontScale,
                    originalScaleY * safeFontScale);
                paddingX = 6f * safeGeometryScale;
                gap = 3f * safeGeometryScale;
            }
        }
        float border = Math.max(1f, 2f * safeGeometryScale);
        float chipHeight = Math.max(18f * safeGeometryScale,
            font.getCapHeight() + 7f * safeGeometryScale);
        float cursorX = x;
        int row = 0;

        for (Entry entry : entries) {
            String text = badgeText(entry);
            layout.setText(font, text);
            float chipWidth = Math.min(maxWidth, layout.width + paddingX * 2f);
            if (wrapRows && cursorX > x && cursorX + chipWidth > x + maxWidth) {
                cursorX = x;
                row++;
            }
            float chipY = above
                ? anchorY + gap + row * (chipHeight + gap)
                : anchorY - gap - chipHeight - row * (chipHeight + gap);
            Rectangle bounds = new Rectangle(cursorX, chipY, chipWidth, chipHeight);
            chipBounds.add(bounds);
            if (Float.isFinite(pointerX) && Float.isFinite(pointerY)
                && bounds.contains(pointerX, pointerY)) {
                hoveredIndex = chipBounds.size() - 1;
            }
            cursorX += chipWidth + gap;
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            Rectangle bounds = chipBounds.get(i);
            batch.setColor(i == hoveredIndex ? BattleUiAssets.YELLOW : colorOf(entry.type()));
            batch.draw(ui.pixel, bounds.x, bounds.y, bounds.width, bounds.height);
            batch.setColor(CHIP_BACKGROUND);
            batch.draw(ui.pixel,
                bounds.x + border,
                bounds.y + border,
                Math.max(0f, bounds.width - border * 2f),
                Math.max(0f, bounds.height - border * 2f));
            String text = badgeText(entry);
            layout.setText(font, text);
            font.setColor(Color.WHITE);
            font.draw(batch, text,
                bounds.x + Math.max(2f * safeGeometryScale, (bounds.width - layout.width) / 2f),
                bounds.y + (bounds.height + font.getCapHeight()) / 2f);
        }

        font.getData().setScale(originalScaleX, originalScaleY);
        font.setColor(originalFontColor);
        batch.setColor(originalBatchColor);
    }

    private static String badgeText(Entry entry) {
        return shortLabel(entry.type(), entry.displayName())
            + stackText(entry.stacks());
    }

    /** Draws details last so later HUDs and battlefield effects cannot cover them. */
    public void drawTooltip(
        Batch batch,
        BitmapFont font,
        BattleUiAssets ui,
        float viewportWidth,
        float viewportHeight,
        float geometryScale,
        float fontScale
    ) {
        if (hoveredIndex < 0 || hoveredIndex >= entries.size()
            || hoveredIndex >= chipBounds.size()) {
            return;
        }

        Entry entry = entries.get(hoveredIndex);
        Rectangle chip = chipBounds.get(hoveredIndex);
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        Color originalColor = new Color(font.getColor());
        Color originalBatchColor = new Color(batch.getColor());
        float safeGeometryScale = Math.max(0.5f, geometryScale);
        float safeFontScale = Math.max(0.45f, fontScale);
        font.getData().setScale(
            originalScaleX * safeFontScale,
            originalScaleY * safeFontScale);

        float padding = 11f * safeGeometryScale;
        float popupWidth = Math.min(360f * safeGeometryScale,
            Math.max(1f, viewportWidth - padding * 2f));
        float contentWidth = Math.max(1f, popupWidth - padding * 2f);
        String title = entry.displayName() + "  " + durationText(entry.rounds(), entry.ticks());
        GlyphLayout titleLayout = new GlyphLayout(
            font, title, BattleUiAssets.TEXT, contentWidth, Align.left, true);
        GlyphLayout detailLayout = new GlyphLayout(
            font, details(entry), BattleUiAssets.MUTED, contentWidth, Align.left, true);
        float titleGap = 7f * safeGeometryScale;
        float popupHeight = padding * 2f + titleLayout.height + titleGap + detailLayout.height;
        float popupX = clamp(chip.x, padding, Math.max(padding, viewportWidth - popupWidth - padding));
        float popupY = chip.y + chip.height + 5f * safeGeometryScale;
        if (popupY + popupHeight > viewportHeight - padding) {
            popupY = chip.y - popupHeight - 5f * safeGeometryScale;
        }
        popupY = clamp(popupY, padding, Math.max(padding, viewportHeight - popupHeight - padding));

        batch.setColor(Color.WHITE);
        ui.cardOver.draw(batch, popupX, popupY, popupWidth, popupHeight);
        float textTop = popupY + popupHeight - padding;
        font.draw(batch, titleLayout, popupX + padding, textTop);
        font.draw(batch, detailLayout, popupX + padding,
            textTop - titleLayout.height - titleGap);

        font.getData().setScale(originalScaleX, originalScaleY);
        font.setColor(originalColor);
        batch.setColor(originalBatchColor);
    }

    static String durationText(int rounds, int ticks) {
        if (rounds < 0) return "PERM";
        if (rounds > 0 && ticks > 0) return rounds + "R " + ticks + "T";
        if (rounds > 0) return rounds + "R";
        return Math.max(0, ticks) + "T";
    }

    static String remainingText(int rounds, int ticks) {
        if (rounds < 0) return "Permanent";
        List<String> parts = new ArrayList<>(2);
        if (rounds > 0) parts.add(rounds + (rounds == 1 ? " round" : " rounds"));
        if (ticks > 0) parts.add(ticks + (ticks == 1 ? " AP tick" : " AP ticks"));
        return parts.isEmpty() ? "Expires now" : String.join(" + ", parts);
    }

    static String shortLabel(StatusEffectType type, String fallbackName) {
        if (type == null) {
            String readable = readableName(fallbackName).replace(" ", "");
            return readable.length() <= 6 ? readable.toUpperCase(Locale.ROOT)
                : readable.substring(0, 6).toUpperCase(Locale.ROOT);
        }
        return switch (type) {
            case VITALITY_INCREASE -> "VIT+";
            case VITALITY_DECREASE -> "VIT-";
            case STRENGTH_INCREASE -> "STR+";
            case STRENGTH_DECREASE -> "STR-";
            case DURABILITY_INCREASE -> "DUR+";
            case DURABILITY_DECREASE -> "DUR-";
            case SPEED_INCREASE -> "SPD+";
            case SPEED_DECREASE -> "SPD-";
            case COMBAT_ABILITY_INCREASE -> "COM+";
            case COMBAT_ABILITY_DECREASE -> "COM-";
            case CURSED_ENERGY_RESERVES_INCREASE, MAX_CURSED_ENERGY_INCREASE -> "CE+";
            case CURSED_ENERGY_RESERVES_DECREASE, MAX_CURSED_ENERGY_DECREASE -> "CE-";
            case CURSED_ENERGY_EFFICIENCY_INCREASE -> "EFF+";
            case CURSED_ENERGY_EFFICIENCY_DECREASE -> "EFF-";
            case CURSED_ENERGY_OUTPUT_INCREASE -> "OUT+";
            case CURSED_ENERGY_OUTPUT_DECREASE -> "OUT-";
            case JUJUTSU_SKILL_INCREASE -> "JJK+";
            case JUJUTSU_SKILL_DECREASE -> "JJK-";
            case CURSED_TECHNIQUE_MASTERY_INCREASE -> "CTM+";
            case CURSED_TECHNIQUE_MASTERY_DECREASE -> "CTM-";
            case MAX_HP_INCREASE -> "HP+";
            case MAX_HP_DECREASE -> "HP-";
            case MAX_AP_INCREASE -> "AP+";
            case MAX_AP_DECREASE -> "AP-";
            case ACCURACY_INCREASE -> "ACC+";
            case ACCURACY_DECREASE -> "ACC-";
            case EVASION_INCREASE -> "EVA+";
            case EVASION_DECREASE -> "EVA-";
            case POWER_INCREASE -> "PWR+";
            case POWER_DECREASE -> "PWR-";
            case DEFENSE_INCREASE -> "DEF+";
            case DEFENSE_DECREASE -> "DEF-";
            case STAGGER -> "STAG";
            case SLEEP -> "SLEEP";
            case RESTRAINED -> "BIND";
            case WET -> "WET";
            case FROZEN -> "FRZ";
            case BURNED -> "BURN";
            case BLEED -> "BLEED";
            case FATIGUED -> "FAT";
            case CURSED_SPEECH_WARD -> "WARD";
            case CURSED_ENERGY_PARASITE -> "PARA";
            case POISON -> "PSN";
        };
    }

    static String effectText(StatusEffectType type, double magnitude) {
        if (type == null) return magnitude > 0.0
            ? "Magnitude " + number(magnitude) : "Active combat status.";
        if (type.isStatModifier()) {
            String statName = type.displayName()
                .replaceFirst("^Increase ", "")
                .replaceFirst("^Decrease ", "");
            double signed = type.signedMagnitude(magnitude);
            return statName + " " + (signed >= 0 ? "+" : "") + number(signed) + ".";
        }
        return switch (type) {
            case STAGGER -> "Interrupts the holder's active action.";
            case SLEEP -> "Prevents the holder from acting and ends when damaged or recovered.";
            case RESTRAINED -> "Halves Speed; each AP tick can break the restraint or lose an action.";
            case WET -> "Reduces Speed by 20% and amplifies electric hits.";
            case FROZEN -> "Halves Defense and interrupts non-fire actions.";
            case BURNED -> "Halves outgoing melee damage and deals damage each active AP tick. Cured by healing moves.";
            case BLEED -> "Each wound deals 0.04% max HP per AP tick after actions. Up to 5 wounds with independent timers; new wounds replace the oldest at the cap. Healing moves cure all wounds.";
            case FATIGUED -> "Adds 2 AP ticks to move cost and firing time.";
            case CURSED_SPEECH_WARD -> "Blocks incoming Cursed Speech commands.";
            case CURSED_ENERGY_PARASITE -> "Voluntary move CE payments also damage the holder.";
            case POISON -> "Reduces every base stat by 20% and deals damage each active AP tick. Cured by healing moves.";
            default -> type.displayName() + ".";
        };
    }

    static String restrictionText(StatusEffectType type) {
        if (type == StatusEffectType.SLEEP) return "Cannot plan or execute actions.";
        if (type == StatusEffectType.FROZEN) return "Only fire actions can proceed while Frozen.";
        if (type == StatusEffectType.STAGGER) return "The current action is interrupted.";
        return "None.";
    }

    private static String details(Entry entry) {
        StringBuilder details = new StringBuilder()
            .append("Effect: ").append(effectText(entry.type(), entry.magnitude()))
            .append("\nRemaining: ").append(remainingText(entry.rounds(), entry.ticks()));
        if (entry.source() != null && !entry.source().isBlank()) {
            details.append("\nSource: ").append(entry.source());
        }
        details.append("\nStacks: ").append(entry.stacks())
            .append("\nRestrictions: ").append(restrictionText(entry.type()));
        if (Double.isFinite(entry.removalChance()) && entry.removalChance() > 0.0) {
            details.append("\nRemoval: ")
                .append(number(entry.removalChance() * 100.0))
                .append("% chance each AP tick.");
        }
        if (Double.isFinite(entry.ceUpkeep()) && entry.ceUpkeep() > 0.0) {
            details.append("\nUpkeep: ").append(number(entry.ceUpkeep()))
                .append(" CE each AP tick.");
        }
        return details.toString();
    }

    private void add(Entry candidate) {
        for (int i = 0; i < entries.size(); i++) {
            Entry existing = entries.get(i);
            if (existing.type() == candidate.type()
                && existing.displayName().equals(candidate.displayName())
                && existing.rounds() == candidate.rounds()
                && existing.ticks() == candidate.ticks()
                && Double.compare(existing.magnitude(), candidate.magnitude()) == 0
                && Double.compare(existing.removalChance(), candidate.removalChance()) == 0
                && Double.compare(existing.ceUpkeep(), candidate.ceUpkeep()) == 0
                && existing.source().equals(candidate.source())) {
                entries.set(i, new Entry(
                    existing.type(),
                    existing.displayName(),
                    existing.rounds(),
                    existing.ticks(),
                    existing.magnitude(),
                    existing.removalChance(),
                    existing.ceUpkeep(),
                    existing.source(),
                    existing.stacks() + candidate.stacks()));
                return;
            }
        }
        entries.add(candidate);
    }

    private static String stackText(int stacks) {
        return stacks > 1 ? "x" + stacks : "";
    }

    private static StatusEffectType typeOf(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return StatusEffectType.fromName(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Color colorOf(StatusEffectType type) {
        if (type == null) return NEUTRAL;
        if (type.name().endsWith("_INCREASE")) return POSITIVE;
        if (type.name().endsWith("_DECREASE") || type == StatusEffectType.BURNED
            || type == StatusEffectType.POISON || type == StatusEffectType.BLEED
            || type == StatusEffectType.WET
            || type == StatusEffectType.CURSED_ENERGY_PARASITE) {
            return NEGATIVE;
        }
        if (type == StatusEffectType.CURSED_SPEECH_WARD) return PROTECTION;
        return CONTROL;
    }

    private static String readableName(String value) {
        if (value == null || value.isBlank()) return "Status";
        String[] words = value.trim().replace('-', '_').split("_+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)))
                .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.isEmpty() ? "Status" : result.toString();
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value)
            .replaceAll("0+$", "")
            .replaceAll("\\.$", "");
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
