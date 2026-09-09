package com.jjktbf.graphics.ui.battle;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Rectangle;
import com.jjktbf.graphics.ui.text.KeywordTextLayout;
import com.jjktbf.model.move.AoeType;
import com.jjktbf.model.move.BlockStyle;
import com.jjktbf.model.move.Move;

import java.util.ArrayList;
import java.util.List;

/** Full-width details for the move highlighted in the compact battle palette. */
final class MoveDetailView {
    static final float TITLE_WIDTH = 400f;
    static final float VALUES_WIDTH = 500f;
    private static final float PADDING = 16f;
    private static final float GAP = 16f;
    private static final float EDGE = 2f;
    private static final Color BACKGROUND = new Color(0.115f, 0.150f, 0.235f, 1f);

    private final Rectangle bounds = new Rectangle();
    private final Rectangle titleBounds = new Rectangle();
    private final Rectangle valuesBounds = new Rectangle();
    private final Rectangle descriptionBounds = new Rectangle();
    private KeywordTextLayout descriptionLayout;
    private float descriptionX;
    private float descriptionTop;

    Rectangle getBounds() {
        return bounds;
    }

    void setBounds(float x, float y, float width, float height) {
        bounds.set(x, y, Math.max(0f, width), Math.max(0f, height));
        float innerX = x + PADDING;
        float innerY = y + PADDING;
        float innerHeight = Math.max(0f, height - PADDING * 2f);
        float availableWidth = Math.max(0f, width - PADDING * 2f - GAP * 2f);
        float titleWidth = Math.min(TITLE_WIDTH, availableWidth);
        float valuesWidth = Math.min(VALUES_WIDTH, Math.max(0f, availableWidth - titleWidth));
        float descriptionWidth = Math.max(0f, availableWidth - titleWidth - valuesWidth);
        titleBounds.set(innerX, innerY, titleWidth, innerHeight);
        valuesBounds.set(innerX + titleWidth + GAP, innerY, valuesWidth, innerHeight);
        descriptionBounds.set(valuesBounds.x + valuesWidth + GAP,
            innerY, descriptionWidth, innerHeight);
    }

    void draw(Batch batch, BattleUiAssets ui, BitmapFont labelFont, BitmapFont bodyFont,
              BitmapFont titleFont, Move move, String description, boolean reinforced) {
        if (move == null || bounds.width <= 0f || bounds.height <= 0f) {
            descriptionLayout = null;
            return;
        }

        batch.setColor(BACKGROUND);
        batch.draw(ui.pixel, bounds.x, bounds.y, bounds.width, bounds.height);

        Color type = MoveCardView.typeColorFor(move, reinforced);
        batch.setColor(type);
        batch.draw(ui.pixel, bounds.x, bounds.y, bounds.width, EDGE);
        batch.draw(ui.pixel, bounds.x, bounds.y + bounds.height - EDGE, bounds.width, EDGE);
        batch.draw(ui.pixel, bounds.x, bounds.y, EDGE, bounds.height);
        batch.draw(ui.pixel, bounds.x + bounds.width - EDGE, bounds.y, EDGE, bounds.height);
        batch.setColor(Color.WHITE);

        String title = reinforced ? move.getName() + " +" : move.getName();
        titleFont.setColor(Color.WHITE);
        MoveCardView.drawCenteredTitleFitted(batch, titleFont, title, titleBounds, 2);

        drawValues(batch, labelFont, bodyFont, moveValues(move, reinforced));

        bodyFont.setColor(Color.WHITE);
        descriptionX = descriptionBounds.x;
        descriptionTop = descriptionBounds.y + descriptionBounds.height - 4f;
        descriptionLayout = KeywordTextLayout.build(
            bodyFont, description, descriptionBounds.width, 4, 0.7f, 0.8f);
        descriptionLayout.draw(batch, bodyFont, descriptionX, descriptionTop,
            Color.WHITE, KeywordTextLayout.KEYWORD_ORANGE);
        batch.setColor(Color.WHITE);
    }

    MoveCardView.KeywordHover keywordAt(float x, float y) {
        if (descriptionLayout == null || !descriptionBounds.contains(x, y)) return null;
        KeywordTextLayout.KeywordHit hit = descriptionLayout.keywordAt(
            x - descriptionX, y - descriptionTop);
        if (hit == null) return null;
        Rectangle local = hit.bounds();
        return new MoveCardView.KeywordHover(
            hit.text(), hit.entry(), new Rectangle(
                descriptionX + local.x,
                descriptionTop + local.y,
                local.width,
                local.height));
    }

    private void drawValues(Batch batch, BitmapFont labelFont, BitmapFont bodyFont,
                            List<MoveValue> values) {
        if (values.isEmpty()) return;
        ValueLayout layout = valueLayout(values.size());
        float originalLabelScaleX = labelFont.getData().scaleX;
        float originalLabelScaleY = labelFont.getData().scaleY;
        float originalBodyScaleX = bodyFont.getData().scaleX;
        float originalBodyScaleY = bodyFont.getData().scaleY;
        boolean sharedFont = labelFont == bodyFont;
        try {
            labelFont.getData().setScale(
                originalLabelScaleX * layout.fontScale(),
                originalLabelScaleY * layout.fontScale());
            if (!sharedFont) {
                bodyFont.getData().setScale(
                    originalBodyScaleX * layout.fontScale(),
                    originalBodyScaleY * layout.fontScale());
            }

            int rowCount = layout.secondRowCount() == 0 ? 1 : 2;
            float cellHeight = valuesBounds.height / rowCount;
            for (int i = 0; i < values.size(); i++) {
                int row = i < layout.firstRowCount() ? 0 : 1;
                int firstIndex = row == 0 ? 0 : layout.firstRowCount();
                int columns = row == 0
                    ? layout.firstRowCount() : layout.secondRowCount();
                int column = i - firstIndex;
                float cellWidth = valuesBounds.width / columns;
                float x = valuesBounds.x + column * cellWidth;
                float centerY = valuesBounds.y + valuesBounds.height
                    - (row + 0.5f) * cellHeight;
                float baselineGap = Math.max(
                    labelFont.getCapHeight(), bodyFont.getCapHeight()) + 4f;

                labelFont.setColor(Color.WHITE);
                MoveCardView.drawCentered(batch, labelFont, values.get(i).label(),
                    x, centerY + baselineGap * 0.55f,
                    cellWidth, cellWidth - 20f, 0.7f, true);
                bodyFont.setColor(Color.WHITE);
                MoveCardView.drawCentered(batch, bodyFont, values.get(i).value(),
                    x, centerY - baselineGap * 0.45f,
                    cellWidth, cellWidth - 20f, 0.7f, true);
            }
        } finally {
            labelFont.getData().setScale(originalLabelScaleX, originalLabelScaleY);
            if (!sharedFont) {
                bodyFont.getData().setScale(originalBodyScaleX, originalBodyScaleY);
            }
        }
    }

    static ValueLayout valueLayout(int valueCount) {
        return switch (Math.max(1, Math.min(6, valueCount))) {
            case 1 -> new ValueLayout(1, 0, 1.35f);
            case 2 -> new ValueLayout(2, 0, 1.25f);
            case 3 -> new ValueLayout(3, 0, 1.15f);
            case 4 -> new ValueLayout(2, 2, 1.10f);
            case 5 -> new ValueLayout(3, 2, 1.05f);
            default -> new ValueLayout(3, 3, 1f);
        };
    }

    static List<MoveValue> moveValues(Move move, boolean reinforced) {
        if (move == null) return List.of();
        List<MoveValue> values = new ArrayList<>();
        int hitCount = move.getHitComponents().size();
        if (move.getBasePower() > 0 || hitCount > 1) {
            int power = reinforced ? move.getHitComponents().stream()
                .mapToInt(hit -> hit.getBasePower()
                    + (hit.isReinforcementEligible() ? hit.getReinforcementBonusPower() : 0))
                .sum() : move.getBasePower();
            values.add(new MoveValue("POWER", String.valueOf(power)));
        }
        if (hitCount > 0) {
            values.add(new MoveValue("ACCURACY", move.isNeverMiss()
                ? "N/A" : (int) Math.round(move.getBaseAccuracy() * 100d) + "%"));
        }
        if (hitCount > 1) values.add(new MoveValue("HITS", String.valueOf(hitCount)));

        if (move.isBlock()) {
            String block = move.getBlockStyle() == BlockStyle.FLAT
                ? String.valueOf(move.getBlockFlatReduction())
                : move.getBlockDamageReduction() + "%";
            values.add(new MoveValue("BLOCK", block));
        } else if (move.isDodge()) {
            values.add(new MoveValue("DODGE", move.getDodgeChance() + "%"));
            values.add(new MoveValue("SCOPE", move.getDodgeScope()));
        } else if (move.isParry()) {
            values.add(new MoveValue("PARRY", "ACTIVE"));
            if (move.getParryStaggerTicks() > 0) {
                values.add(new MoveValue("STAGGER", move.getParryStaggerTicks() + " AP"));
            }
        }
        if (move.isActiveDefense() && move.getBlockDuration() > 0) {
            values.add(new MoveValue("WINDOW", move.getBlockDuration() + " AP"));
        }
        if (move.isActiveDefense() && move.getDefenseUses() > 0) {
            values.add(new MoveValue("USES", String.valueOf(move.getDefenseUses())));
        }
        if (reinforced && move.isActiveDefense()
            && move.getReinforcementDefenseValue() > 0) {
            String suffix = move.getReinforcementDefenseType().name().contains("PERCENTAGE")
                ? "%" : "";
            values.add(new MoveValue("REINFORCED",
                "+" + move.getReinforcementDefenseValue() + suffix));
        }
        if (move.getPotency() > 1) {
            values.add(new MoveValue("POTENCY", String.valueOf(move.getPotency())));
        }
        if (move.getAoeType() == AoeType.MULTIPLE) {
            values.add(new MoveValue("TARGETS", String.valueOf(move.getAoeTargetCount())));
        }
        if (values.isEmpty()) {
            values.add(new MoveValue("POTENCY", String.valueOf(move.getPotency())));
        }
        return List.copyOf(values.subList(0, Math.min(6, values.size())));
    }

    LayoutSnapshot layoutSnapshot() {
        return new LayoutSnapshot(
            new Rectangle(bounds), new Rectangle(titleBounds),
            new Rectangle(valuesBounds), new Rectangle(descriptionBounds));
    }

    record MoveValue(String label, String value) { }

    record ValueLayout(int firstRowCount, int secondRowCount, float fontScale) { }

    record LayoutSnapshot(
        Rectangle bounds,
        Rectangle title,
        Rectangle values,
        Rectangle description
    ) { }
}
