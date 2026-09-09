package com.jjktbf.graphics.ui.battle;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.jjktbf.graphics.ui.text.KeywordTextLayout;
import com.jjktbf.model.move.Move;
import com.jjktbf.model.move.MoveCategory;
import com.jjktbf.model.move.MoveTag;
import com.jjktbf.model.text.KeywordDescriptionCatalog;

import java.util.ArrayList;
import java.util.List;

/** A pixel-art move card in the planner's bottom palette. */
public class MoveCardView {

    public static final float CARD_W = 240f;
    public static final float CARD_H = 224f;
    private static final Color ACTIVATION_DOT = new Color(0.075f, 0.080f, 0.100f, 1f);
    private static final Color TIMING_STRIP = new Color(0.270f, 0.305f, 0.375f, 1f);
    private static final Color TIMING_STRIP_EDGE = new Color(0.075f, 0.095f, 0.145f, 1f);
    private static final Color CURSED_TOOL = new Color(0.545f, 0.000f, 0.000f, 1f);
    private static final float CE_BAR_W = 36f;
    private static final float CE_BAR_H = 38f;
    private static final float ACTION_BAR_MAX_H = 24f;
    private static final float ROLE_ICON_SIZE = 18f;

    private final Move move;
    private final Rectangle bounds;
    private final float geometryScale;
    private final int descriptionLineCount;
    private final float minimumTextScale;
    private final boolean strictTextFloor;
    private String displayDescription;
    private boolean disabled;
    private boolean hovered;
    private boolean dragging;
    private boolean reinforced;
    private boolean compact;
    private int displayedApCost;
    private int displayedUnleashPoint;
    private KeywordTextLayout descriptionLayout;
    private float descriptionX;
    private float descriptionTop;

    public MoveCardView(Move move, float x, float y) {
        this(move, x, y, 1f);
    }

    public MoveCardView(Move move, float x, float y, float geometryScale) {
        this(move, x, y, geometryScale,
            CARD_W * Math.max(1f, geometryScale),
            CARD_H * Math.max(1f, geometryScale),
            5);
    }

    public MoveCardView(
        Move move,
        float x,
        float y,
        float geometryScale,
        float width,
        float height,
        int descriptionLineCount
    ) {
        this(move, x, y, geometryScale, width, height, descriptionLineCount, 0.3f, false);
    }

    /**
     * Creates a card with an explicit text-fitting floor. Existing planner cards
     * keep their historical fitting loop; profile previews can opt out of shrinking.
     */
    public MoveCardView(
        Move move,
        float x,
        float y,
        float geometryScale,
        float width,
        float height,
        int descriptionLineCount,
        float minimumTextScale
    ) {
        this(move, x, y, geometryScale, width, height,
            descriptionLineCount, minimumTextScale, true);
    }

    private MoveCardView(
        Move move,
        float x,
        float y,
        float geometryScale,
        float width,
        float height,
        int descriptionLineCount,
        float minimumTextScale,
        boolean strictTextFloor
    ) {
        this.move = move;
        this.geometryScale = Math.max(1f, geometryScale);
        this.bounds = new Rectangle(x, y, Math.max(1f, width), Math.max(1f, height));
        this.descriptionLineCount = Math.max(1, descriptionLineCount);
        this.minimumTextScale = Math.max(0.1f, Math.min(1f, minimumTextScale));
        this.strictTextFloor = strictTextFloor;
        this.displayDescription = move == null ? "" : move.getDescription();
        this.displayedApCost = move == null ? 0 : move.getApCost();
        this.displayedUnleashPoint = move == null ? 0 : move.getUnleashPoint();
    }

    public Move getMove()                    { return move; }
    public Rectangle getBounds()             { return bounds; }
    public boolean isDisabled()              { return disabled; }
    public void setDisabled(boolean value)   { disabled = value; }
    public void setHovered(boolean value)    { hovered = value; }
    public void setDragging(boolean value)   { dragging = value; }
    public boolean isReinforced()            { return reinforced; }
    public void setReinforced(boolean value) { reinforced = value; }
    /** Uses the battle planner's compact title-and-cost presentation. */
    public void setCompact(boolean value)      { compact = value; }
    public void setDisplayedTiming(int apCost, int unleashPoint) {
        displayedApCost = apCost;
        displayedUnleashPoint = unleashPoint;
    }
    public String getDisplayDescription()    { return displayDescription; }
    public void setDisplayDescription(String value) {
        displayDescription = value == null ? "" : value;
    }

    /** Returns the highlighted description term beneath the supplied planner coordinate. */
    public KeywordHover keywordAt(float x, float y) {
        if (descriptionLayout == null) return null;
        KeywordTextLayout.KeywordHit hit = descriptionLayout.keywordAt(
            x - descriptionX, y - descriptionTop);
        if (hit == null) return null;
        Rectangle local = hit.bounds();
        return new KeywordHover(
            hit.text(),
            hit.entry(),
            new Rectangle(
                descriptionX + local.x,
                descriptionTop + local.y,
                local.width,
                local.height));
    }

    public static Color typeColorFor(MoveCategory category) {
        if (category == null) return Color.GRAY;
        return switch (category) {
            case PHYSICAL -> new Color(0.850f, 0.380f, 0.190f, 1f);
            case INNATE_TECHNIQUE -> new Color(0.560f, 0.280f, 0.820f, 1f);
            case NON_INNATE_TECHNIQUE -> new Color(0.180f, 0.450f, 0.800f, 1f);
            case CURSED_ENERGY -> new Color(0.150f, 0.620f, 0.910f, 1f);
            case PHYSICAL_CURSED_ENERGY -> new Color(0.130f, 0.690f, 0.570f, 1f);
            case PHYSICAL_INNATE_TECHNIQUE -> new Color(0.760f, 0.300f, 0.500f, 1f);
            case PHYSICAL_NON_INNATE_TECHNIQUE -> new Color(0.610f, 0.350f, 0.560f, 1f);
            case INNATE_NON_INNATE_TECHNIQUE -> new Color(0.380f, 0.300f, 0.860f, 1f);
            case PHYSICAL_INNATE_NON_INNATE_TECHNIQUE -> new Color(0.660f, 0.260f, 0.700f, 1f);
            case UTILITY -> new Color(0.450f, 0.510f, 0.610f, 1f);
            case DEFENSIVE -> new Color(0.940f, 0.690f, 0.140f, 1f);
        };
    }

    /** Returns the color from the move's role palette and its underlying nature. */
    public static Color typeColorFor(Move move) {
        if (move == null) return Color.GRAY;
        if (isCursedTool(move)) {
            if (isDefensiveRole(move)) return new Color(0.400f, 0.090f, 0.110f, 1f);
            if (isUtilityRole(move)) return new Color(0.470f, 0.390f, 0.410f, 1f);
            return new Color(CURSED_TOOL);
        }

        MoveCategory nature = natureCategoryFor(move);
        if (isDefensiveRole(move)) return defensiveColorFor(nature);
        if (isUtilityRole(move)) return utilityColorFor(nature);
        return typeColorFor(nature);
    }

    public static Color typeColorFor(Move move, boolean reinforced) {
        if (!reinforced) return typeColorFor(move);
        return isDefensiveRole(move)
            ? defensiveColorFor(MoveCategory.PHYSICAL_CURSED_ENERGY)
            : typeColorFor(MoveCategory.PHYSICAL_CURSED_ENERGY);
    }

    private static Color defensiveColorFor(MoveCategory nature) {
        if (nature == null) return new Color(0.650f, 0.540f, 0.330f, 1f);
        return switch (nature) {
            case PHYSICAL -> typeColorFor(MoveCategory.DEFENSIVE);
            case CURSED_ENERGY -> new Color(0.100f, 0.330f, 0.520f, 1f);
            case INNATE_TECHNIQUE -> new Color(0.105f, 0.115f, 0.135f, 1f);
            case NON_INNATE_TECHNIQUE -> new Color(0.100f, 0.200f, 0.380f, 1f);
            case PHYSICAL_CURSED_ENERGY -> new Color(0.310f, 0.540f, 0.140f, 1f);
            case PHYSICAL_INNATE_TECHNIQUE -> new Color(0.330f, 0.120f, 0.240f, 1f);
            case PHYSICAL_NON_INNATE_TECHNIQUE -> new Color(0.220f, 0.190f, 0.330f, 1f);
            case INNATE_NON_INNATE_TECHNIQUE -> new Color(0.130f, 0.120f, 0.300f, 1f);
            case PHYSICAL_INNATE_NON_INNATE_TECHNIQUE -> new Color(0.260f, 0.100f, 0.260f, 1f);
            case UTILITY, DEFENSIVE -> typeColorFor(MoveCategory.DEFENSIVE);
        };
    }

    /** Desaturated versions of the attack palette, visually fused with utility grey. */
    private static Color utilityColorFor(MoveCategory nature) {
        if (nature == null) return new Color(0.360f, 0.400f, 0.470f, 1f);
        return switch (nature) {
            case PHYSICAL -> typeColorFor(MoveCategory.UTILITY);
            case CURSED_ENERGY -> new Color(0.510f, 0.650f, 0.750f, 1f);
            case INNATE_TECHNIQUE -> new Color(0.640f, 0.560f, 0.760f, 1f);
            case NON_INNATE_TECHNIQUE -> new Color(0.460f, 0.570f, 0.700f, 1f);
            case PHYSICAL_CURSED_ENERGY -> new Color(0.440f, 0.650f, 0.600f, 1f);
            case PHYSICAL_INNATE_TECHNIQUE -> new Color(0.700f, 0.540f, 0.620f, 1f);
            case PHYSICAL_NON_INNATE_TECHNIQUE -> new Color(0.580f, 0.530f, 0.640f, 1f);
            case INNATE_NON_INNATE_TECHNIQUE -> new Color(0.530f, 0.530f, 0.720f, 1f);
            case PHYSICAL_INNATE_NON_INNATE_TECHNIQUE -> new Color(0.640f, 0.500f, 0.670f, 1f);
            case UTILITY, DEFENSIVE -> typeColorFor(MoveCategory.UTILITY);
        };
    }

    private static MoveCategory natureCategoryFor(Move move) {
        boolean physical = hasNatureTag(move, MoveTag.PHYSICAL);
        boolean innate = hasNatureTag(move, MoveTag.INNATE_TECHNIQUE);
        boolean nonInnate = hasNatureTag(move, MoveTag.NON_INNATE_TECHNIQUE);

        if (innate && nonInnate) {
            return physical
                ? MoveCategory.PHYSICAL_INNATE_NON_INNATE_TECHNIQUE
                : MoveCategory.INNATE_NON_INNATE_TECHNIQUE;
        }
        if (innate) {
            return physical ? MoveCategory.PHYSICAL_INNATE_TECHNIQUE : MoveCategory.INNATE_TECHNIQUE;
        }
        if (nonInnate) {
            return physical
                ? MoveCategory.PHYSICAL_NON_INNATE_TECHNIQUE
                : MoveCategory.NON_INNATE_TECHNIQUE;
        }
        if (physical && hasNatureTag(move, MoveTag.CURSED_ENERGY)) {
            return MoveCategory.PHYSICAL_CURSED_ENERGY;
        }
        if (physical) return MoveCategory.PHYSICAL;
        if (hasNatureTag(move, MoveTag.CURSED_ENERGY)) return MoveCategory.CURSED_ENERGY;
        return null;
    }

    /** Returns the card's move-nature tag, excluding ATTACK, DEFENSIVE, and UTILITY. */
    public static String typeNameFor(Move move) {
        return typeNameFor(move, false);
    }

    public static String typeNameFor(Move move, boolean reinforced) {
        if (move == null) return "UNKNOWN";
        if (reinforced) return "REINFORCED";
        boolean physical = hasNatureTag(move, MoveTag.PHYSICAL);
        boolean innate = hasNatureTag(move, MoveTag.INNATE_TECHNIQUE);
        boolean nonInnate = hasNatureTag(move, MoveTag.NON_INNATE_TECHNIQUE);
        boolean cursedEnergy = hasNatureTag(move, MoveTag.CURSED_ENERGY);

        if (isCursedTool(move)) return "CURSED TOOL";
        // Innate technique takes precedence over physical for the category label,
        // so e.g. PHYSICAL + INNATE reads simply as INNATE TECHNIQUE.
        if (innate) return "INNATE TECHNIQUE";
        if (physical && innate && nonInnate) return "PHYSICAL + INNATE + NON-INNATE TECHNIQUE";
        if (physical && nonInnate) return "PHYSICAL + NON-INNATE TECHNIQUE";
        if (innate && nonInnate) return "INNATE + NON-INNATE TECHNIQUE";
        if (nonInnate) return "NON-INNATE TECHNIQUE";
        if (physical && cursedEnergy) return "PHYSICAL + CURSED ENERGY";
        if (physical) return "PHYSICAL";
        if (cursedEnergy) return "CURSED ENERGY";
        return "UNKNOWN";
    }

    /** Selects the tactical-role icon that sits beside the card title. */
    public static Texture roleIconFor(Move move, BattleUiAssets ui) {
        if (isDefensiveRole(move)) {
            return ui.defenseEffectIcon;
        }
        if (isUtilityRole(move)) {
            return ui.utilityEffectIcon;
        }
        return ui.attackEffectIcon;
    }

    private static boolean isDefensiveRole(Move move) {
        return move != null && (move.isDefensive() || move.hasTag("DEFENSIVE"));
    }

    private static boolean isUtilityRole(Move move) {
        return move != null && (move.hasTag("UTILITY") || move.getCategory() == MoveCategory.UTILITY);
    }

    private static boolean hasNatureTag(Move move, MoveTag tag) {
        return move.getTags().contains(tag)
            || move.getCategory() != null && move.getCategory().getTags().contains(tag);
    }

    private static boolean isCursedTool(Move move) {
        return hasNatureTag(move, MoveTag.PHYSICAL)
            && hasNatureTag(move, MoveTag.CURSED_ENERGY)
            && move.hasWeaponTag();
    }

    public void draw(Batch batch, BitmapFont font, BitmapFont statFont,
                     BattleUiAssets ui, int actualCeCost) {
        if (compact) {
            drawCompact(batch, font, ui, actualCeCost);
            return;
        }
        float x = bounds.x;
        float y = bounds.y;
        float w = bounds.width;
        float h = bounds.height;
        if (disabled) {
            ui.cardDisabled.draw(batch, x, y, w, h);
        } else if (hovered || dragging) {
            ui.cardOver.draw(batch, x, y, w, h);
        } else {
            ui.card.draw(batch, x, y, w, h);
        }

        Color type = typeColorFor(move, reinforced);
        if (disabled) type = new Color(type).lerp(Color.GRAY, 0.65f);
        batch.setColor(type);
        batch.draw(ui.pixel, x + scaled(10f), y + h / 2f,
            scaled(9f), h / 2f - scaled(12f));
        batch.setColor(Color.WHITE);

        Color ink = disabled ? BattleUiAssets.MUTED : BattleUiAssets.TEXT;
        float textX = x + scaled(30f);
        float textW = w - scaled(40f);
        float roleIconSize = scaled(ROLE_ICON_SIZE);
        font.setColor(ink);
        drawFitted(batch, font, reinforced ? move.getName() + " +" : move.getName(),
            textX, y + h - scaled(24f),
            textW - roleIconSize - scaled(4f), 1, minimumTextScale, strictTextFloor);
        drawRoleIcon(batch, ui, x + w - roleIconSize - scaled(10f),
            y + h - roleIconSize - scaled(18f), disabled);
        font.setColor(disabled ? BattleUiAssets.MUTED : type);
        drawFitted(batch, font, typeNameFor(move, reinforced), textX, y + h - scaled(48f),
            textW, 1, minimumTextScale, strictTextFloor);

        font.setColor(ink);
        descriptionX = textX;
        descriptionTop = y + h - scaled(74f);
        descriptionLayout = KeywordTextLayout.build(
            font, displayDescription, textW, descriptionLineCount, minimumTextScale, 0.7f);
        descriptionLayout.draw(
            batch,
            font,
            descriptionX,
            descriptionTop,
            ink,
            KeywordTextLayout.KEYWORD_ORANGE);
        float extraActionBarHeight = drawActionPointDots(batch, ui,
            x + scaled(20f), y + scaled(8f), w - scaled(40f),
            displayedApCost, displayedUnleashPoint, scaled(6f), scaled(4f), geometryScale);
        statFont.setColor(ink);
        drawStatColumn(batch, statFont, textX, y + scaled(55f) + extraActionBarHeight,
            y + scaled(35f) + extraActionBarHeight,
            accuracyLabel(move), powerLabel(move, reinforced), geometryScale,
            scaled(120f),
            minimumTextScale, strictTextFloor);
        if (move.hasCeCost() || reinforced) {
            drawCeCostBar(batch, statFont, ui, x + w - scaled(48f),
                y + scaled(24f) + extraActionBarHeight, actualCeCost,
                geometryScale, minimumTextScale, strictTextFloor);
        }
    }

    private void drawCompact(Batch batch, BitmapFont font, BattleUiAssets ui, int actualCeCost) {
        float x = bounds.x;
        float y = bounds.y;
        float w = bounds.width;
        float h = bounds.height;
        descriptionLayout = null;

        if (disabled) {
            ui.cardDisabled.draw(batch, x, y, w, h);
        } else if (hovered || dragging) {
            ui.cardOver.draw(batch, x, y, w, h);
        } else {
            ui.card.draw(batch, x, y, w, h);
        }
        if (disabled && (hovered || dragging)) {
            float edge = scaled(2f);
            batch.setColor(BattleUiAssets.YELLOW);
            batch.draw(ui.pixel, x, y, w, edge);
            batch.draw(ui.pixel, x, y + h - edge, w, edge);
            batch.draw(ui.pixel, x, y, edge, h);
            batch.draw(ui.pixel, x + w - edge, y, edge, h);
            batch.setColor(Color.WHITE);
        }

        Color type = typeColorFor(move, reinforced);
        if (disabled) type = new Color(type).lerp(Color.GRAY, 0.65f);
        batch.setColor(type);
        batch.draw(ui.pixel, x + scaled(8f), y + h - scaled(7f),
            w - scaled(16f), scaled(3f));
        batch.setColor(Color.WHITE);

        Color ink = disabled ? BattleUiAssets.MUTED : BattleUiAssets.TEXT;
        float textX = x + scaled(12f);
        float textW = w - scaled(24f);
        float roleIconSize = scaled(ROLE_ICON_SIZE);
        font.setColor(ink);
        drawTitleFitted(batch, font, reinforced ? move.getName() + " +" : move.getName(),
            textX, y + h - scaled(18f), textW - roleIconSize - scaled(5f),
            2);
        drawRoleIcon(batch, ui, x + w - roleIconSize - scaled(10f),
            y + h - roleIconSize - scaled(12f), disabled);

        font.setColor(disabled ? BattleUiAssets.MUTED : type);
        drawFitted(batch, font, typeNameFor(move, reinforced),
            textX, y + h - scaled(67f), textW, 1, 0.7f, true);

        font.setColor(ink);
        float valueY = y + scaled(21f);
        float ceWidth = scaled(60f);
        drawFitted(batch, font,
            displayedApCost + " AP / FIRE " + displayedUnleashPoint,
            textX, valueY, textW - ceWidth - scaled(8f), 1, 0.7f, true);
        drawRightFitted(batch, font, actualCeCost + " CE",
            textX + textW - ceWidth, valueY, ceWidth, 0.7f);
    }

    private float scaled(float value) {
        return value * geometryScale;
    }

    static String accuracyLabel(Move move) {
        if (move == null || move.getHitComponents().isEmpty()) return null;
        return move.isNeverMiss()
            ? "ACC N/A"
            : "ACC " + (int) Math.round(move.getBaseAccuracy() * 100d) + "%";
    }

    static String powerLabel(Move move) {
        return powerLabel(move, false);
    }

    static String powerLabel(Move move, boolean reinforced) {
        if (move == null) return null;
        if (reinforced && move.isBlock()) {
            return switch (move.getReinforcementDefenseType()) {
                case PERCENTAGE_BLOCK -> "BLOCK +" + move.getReinforcementDefenseValue() + "%";
                case FLAT_BLOCK -> "BLOCK +" + move.getReinforcementDefenseValue();
                default -> null;
            };
        }
        if (reinforced && move.isParry()
            && move.getReinforcementDefenseType()
                == com.jjktbf.model.move.ReinforcementDefenseType.STAGGER_LENGTH) {
            return "STAGGER +" + move.getReinforcementDefenseValue();
        }
        int hitCount = move.getHitComponents().size();
        if (move.getBasePower() <= 0 && hitCount <= 1) return null;
        int power = reinforced ? move.getHitComponents().stream()
            .mapToInt(hit -> hit.getBasePower()
                + (hit.isReinforcementEligible() ? hit.getReinforcementBonusPower() : 0))
            .sum() : move.getBasePower();
        return "PWR " + power
            + (hitCount > 1 ? " | " + hitCount + " HITS" : "");
    }

    /** Draws the AP duration dots and returns the height added by any extra rows. */
    private static float drawActionPointDots(Batch batch, BattleUiAssets ui, float x, float bottomY,
                                              float width, int apCost, int unleashPoint,
                                              float dotSize, float gap, float geometryScale) {
        if (apCost <= 0 || width <= 0f) return 0f;

        float size = Math.min(width, Math.max(1f, dotSize));
        float spacing = Math.max(0f, gap);
        int dotsPerRow = Math.max(1, (int) Math.floor((width + spacing) / (size + spacing)));
        int rowCount = (int) Math.ceil(apCost / (double) dotsPerRow);
        float minimumPixel = geometryScale;
        float rowStep = size + Math.max(minimumPixel, spacing);
        float stripY = bottomY - 3f * geometryScale;
        float stripHeight = rowCount * size
            + (rowCount - 1) * Math.max(minimumPixel, spacing) + 6f * geometryScale;

        // Long custom moves use smaller dots so the AP strip stays below the stat area.
        while (stripHeight > ACTION_BAR_MAX_H * geometryScale && size > minimumPixel) {
            size = size > 2f * geometryScale ? 2f * geometryScale : minimumPixel;
            spacing = minimumPixel;
            dotsPerRow = Math.max(1, (int) Math.floor((width + spacing) / (size + spacing)));
            rowCount = (int) Math.ceil(apCost / (double) dotsPerRow);
            rowStep = size + spacing;
            stripHeight = rowCount * size + (rowCount - 1) * spacing
                + 6f * geometryScale;
        }

        batch.setColor(TIMING_STRIP);
        batch.draw(ui.pixel, x, stripY, width, stripHeight);
        batch.setColor(TIMING_STRIP_EDGE);
        batch.draw(ui.pixel, x, stripY, width, geometryScale);
        batch.draw(ui.pixel, x, stripY + stripHeight - geometryScale,
            width, geometryScale);

        for (int row = 0; row < rowCount; row++) {
            int firstDot = row * dotsPerRow;
            int dotsInRow = Math.min(dotsPerRow, apCost - firstDot);
            float rowWidth = dotsInRow * size + (dotsInRow - 1) * spacing;
            float rowX = x + (width - rowWidth) / 2f;
            float rowY = bottomY + (rowCount - row - 1) * rowStep;

            for (int column = 0; column < dotsInRow; column++) {
                int dotNumber = firstDot + column + 1;
                float dotX = rowX + column * (size + spacing);
                batch.setColor(dotNumber == unleashPoint ? ACTIVATION_DOT : Color.WHITE);
                batch.draw(ui.pixel, dotX, rowY, size, size);
            }
        }
        batch.setColor(Color.WHITE);
        return (rowCount - 1) * rowStep;
    }

    private void drawRoleIcon(Batch batch, BattleUiAssets ui, float x, float y, boolean muted) {
        batch.setColor(muted ? BattleUiAssets.MUTED : Color.WHITE);
        float size = scaled(ROLE_ICON_SIZE);
        batch.draw(roleIconFor(move, ui), x, y, size, size);
        batch.setColor(Color.WHITE);
    }

    /** Draws every word inside a fixed card area, reducing pixel size only if needed. */
    static void drawFitted(Batch batch, BitmapFont font, String value, float x, float y,
                           float maxWidth, int maxLines, float minimumScale,
                           boolean strictTextFloor) {
        String text = value == null || value.isBlank() ? "-" : value;
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        List<String> lines = List.of(text);

        if (strictTextFloor) {
            float scale = 1f;
            while (true) {
                font.getData().setScale(originalScaleX * scale, originalScaleY * scale);
                lines = wrap(font, text, maxWidth);
                if (lines.size() <= maxLines || scale <= minimumScale + 0.001f) break;
                scale = Math.max(minimumScale, scale - 0.10f);
            }
        } else {
            for (float scale = 1f; scale >= 0.30f; scale -= 0.10f) {
                font.getData().setScale(originalScaleX * scale, originalScaleY * scale);
                lines = wrap(font, text, maxWidth);
                if (lines.size() <= maxLines) break;
            }
        }

        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
            int last = lines.size() - 1;
            lines.set(last, ellipsize(font, lines.get(last), maxWidth));
        }

        float lineHeight = font.getLineHeight() * 0.7f;
        for (int i = 0; i < lines.size(); i++) {
            font.draw(batch, lines.get(i), x, y - i * lineHeight);
        }
        font.getData().setScale(originalScaleX, originalScaleY);
    }

    /** Draws a title in full, shrinking it until it fits instead of adding an ellipsis. */
    static void drawTitleFitted(Batch batch, BitmapFont font, String value, float x, float y,
                                float maxWidth, int maxLines) {
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        try {
            List<String> lines = fitTitleLines(
                font, value, maxWidth, maxLines, originalScaleX, originalScaleY);
            drawLines(batch, font, lines, x, y);
        } finally {
            font.getData().setScale(originalScaleX, originalScaleY);
        }
    }

    /** Draws a fully fitted title centered vertically within the supplied region. */
    static void drawCenteredTitleFitted(Batch batch, BitmapFont font, String value,
                                        Rectangle area, int maxLines) {
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        try {
            List<String> lines = fitTitleLines(
                font, value, area.width, maxLines, originalScaleX, originalScaleY);
            float lineHeight = font.getLineHeight() * 0.7f;
            float titleHeight = font.getCapHeight() + (lines.size() - 1) * lineHeight;
            float titleTop = area.y + (area.height + titleHeight) / 2f;
            drawLines(batch, font, lines, area.x, titleTop);
        } finally {
            font.getData().setScale(originalScaleX, originalScaleY);
        }
    }

    private static List<String> fitTitleLines(BitmapFont font, String value, float maxWidth,
                                               int maxLines, float originalScaleX,
                                               float originalScaleY) {
        String text = value == null || value.isBlank() ? "-" : value;
        int lineLimit = Math.max(1, maxLines);
        float scale = 1f;
        List<String> lines = wrap(font, text, maxWidth);
        for (int attempt = 0; lines.size() > lineLimit && attempt < 200; attempt++) {
            scale *= 0.95f;
            font.getData().setScale(originalScaleX * scale, originalScaleY * scale);
            lines = wrap(font, text, maxWidth);
        }
        return lines;
    }

    private static void drawLines(Batch batch, BitmapFont font, List<String> lines,
                                  float x, float y) {
        float lineHeight = font.getLineHeight() * 0.7f;
        for (int i = 0; i < lines.size(); i++) {
            font.draw(batch, lines.get(i), x, y - i * lineHeight);
        }
    }

    private static void drawRightFitted(Batch batch, BitmapFont font, String value,
                                        float x, float y, float maxWidth, float minimumScale) {
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        float scale = 1f;
        while (width(font, value) > maxWidth && scale > minimumScale + 0.001f) {
            scale = Math.max(minimumScale, scale - 0.1f);
            font.getData().setScale(originalScaleX * scale, originalScaleY * scale);
        }
        String fitted = width(font, value) <= maxWidth ? value : ellipsize(font, value, maxWidth);
        font.draw(batch, fitted, x + maxWidth - width(font, fitted), y);
        font.getData().setScale(originalScaleX, originalScaleY);
    }

    /** Draws the accuracy and power stats in the left side of the stat area. */
    private static void drawStatColumn(Batch batch, BitmapFont font, float x,
                                       float upperY, float lowerY, String upper, String lower,
                                       float geometryScale, float columnWidth, float minimumScale,
                                       boolean strictTextFloor) {
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;

        if (strictTextFloor) {
            float scale = Math.max(0.70f, minimumScale);
            while (true) {
                font.getData().setScale(originalScaleX * scale, originalScaleY * scale);
                if (fits(font, upper, columnWidth) && fits(font, lower, columnWidth)) break;
                if (scale <= minimumScale + 0.001f) break;
                scale = Math.max(minimumScale, scale - 0.10f);
            }
        } else {
            for (float scale = 0.70f; scale >= 0.30f; scale -= 0.10f) {
                font.getData().setScale(originalScaleX * scale, originalScaleY * scale);
                if (fits(font, upper, columnWidth) && fits(font, lower, columnWidth)) break;
            }
        }

        drawStat(batch, font, upper, x, upperY, false, columnWidth, geometryScale);
        drawStat(batch, font, lower, x, lowerY, false, columnWidth, geometryScale);
        font.getData().setScale(originalScaleX, originalScaleY);
    }

    /** Draws the CE label and its cost in the reserved right-hand stat area. */
    private static void drawCeCostBar(Batch batch, BitmapFont font, BattleUiAssets ui,
                                      float x, float y, int cost, float geometryScale,
                                      float minimumScale, boolean strictTextFloor) {
        float barWidth = CE_BAR_W * geometryScale;
        float barHeight = CE_BAR_H * geometryScale;
        float edge = 2f * geometryScale;
        batch.setColor(Color.BLACK);
        batch.draw(ui.pixel, x, y, barWidth, barHeight);
        batch.setColor(BattleUiAssets.CURSED_ENERGY);
        batch.draw(ui.pixel, x + edge, y + edge,
            barWidth - edge * 2f, barHeight - edge * 2f);
        batch.setColor(Color.WHITE);

        font.setColor(Color.BLACK);
        drawCentered(batch, font, "CE", x,
            y + barHeight + 20f * geometryScale, barWidth, barWidth,
            minimumScale, strictTextFloor);
        font.setColor(Color.WHITE);
        drawCentered(batch, font, String.valueOf(cost), x,
            y + (barHeight + font.getCapHeight()) / 2f,
            barWidth, barWidth - 6f * geometryScale,
            minimumScale, strictTextFloor);
    }

    static void drawCentered(Batch batch, BitmapFont font, String value, float x, float y,
                             float width, float maxTextWidth, float minimumScale,
                             boolean strictTextFloor) {
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        if (strictTextFloor) {
            float scale = 1f;
            while (true) {
                font.getData().setScale(originalScaleX * scale, originalScaleY * scale);
                if (width(font, value) <= maxTextWidth) break;
                if (scale <= minimumScale + 0.001f) break;
                scale = Math.max(minimumScale, scale - 0.10f);
            }
        } else {
            for (float scale = 1f; scale >= 0.30f; scale -= 0.10f) {
                font.getData().setScale(originalScaleX * scale, originalScaleY * scale);
                if (width(font, value) <= maxTextWidth) break;
            }
        }
        font.draw(batch, value, x + (width - width(font, value)) / 2f, y);
        font.getData().setScale(originalScaleX, originalScaleY);
    }

    private static boolean fits(BitmapFont font, String value, float maxWidth) {
        return value == null || width(font, value) <= maxWidth;
    }

    private static void drawStat(Batch batch, BitmapFont font, String value, float x, float y,
                                 boolean rightAligned, float rowWidth, float geometryScale) {
        if (value == null) return;
        float drawX = rightAligned ? x + rowWidth - width(font, value) : x;
        font.draw(batch, value, drawX, y);
        // A one-pixel second pass keeps the larger stats legible in pixel art.
        font.draw(batch, value, drawX + geometryScale, y);
    }

    private static List<String> wrap(BitmapFont font, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(font, candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            while (width(font, word) > maxWidth && word.length() > 1) {
                int end = fittingPrefix(font, word, maxWidth);
                lines.add(word.substring(0, end));
                word = word.substring(end);
            }
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.isEmpty() ? List.of("-") : lines;
    }

    private static int fittingPrefix(BitmapFont font, String word, float maxWidth) {
        int end = 1;
        while (end < word.length() && width(font, word.substring(0, end + 1)) <= maxWidth) end++;
        return end;
    }

    private static String ellipsize(BitmapFont font, String value, float maxWidth) {
        String suffix = "...";
        String result = value;
        while (result.length() > 1 && width(font, result + suffix) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + suffix;
    }

    private static float width(BitmapFont font, String text) {
        return new GlyphLayout(font, text).width;
    }

    public record KeywordHover(
        String text,
        KeywordDescriptionCatalog.Entry entry,
        Rectangle bounds
    ) { }
}
