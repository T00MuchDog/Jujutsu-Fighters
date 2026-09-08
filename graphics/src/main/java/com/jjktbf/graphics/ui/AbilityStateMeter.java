package com.jjktbf.graphics.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.Align;
import com.jjktbf.graphics.ui.battle.BattleUiAssets;
import com.jjktbf.model.character.coded.CodedAbilityState;

import java.util.List;

/** Compact meter for data-defined bounded resources. */
public final class AbilityStateMeter {

    private static final float TEXT_PADDING = 8f;
    private static final float TEXT_GAP = 5f;

    private List<CodedAbilityState> states = List.of();
    private float x;
    private float y;
    private float width;
    private float rowHeight;

    public void setStates(List<CodedAbilityState> states) {
        this.states = states == null ? List.of() : states.stream()
            .filter(state -> state != null && state.genericMeter())
            .toList();
    }

    public void setBounds(float x, float y, float width, float rowHeight) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0f, width);
        this.rowHeight = Math.max(0f, rowHeight);
    }

    public boolean isVisible() {
        return !states.isEmpty() && width > 0f && rowHeight > 0f;
    }

    public int stateCount() {
        return states.size();
    }

    public void draw(Batch batch, BattleUiAssets ui, BitmapFont font) {
        if (!isVisible()) return;
        Color previousBatchColor = new Color(batch.getColor());
        Color previousFontColor = new Color(font.getColor());
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        try {
            GlyphLayout valueLayout = new GlyphLayout();
            GlyphLayout nameLayout = new GlyphLayout();
            for (int index = 0; index < states.size(); index++) {
                CodedAbilityState state = states.get(index);
                float rowY = y + index * (rowHeight + 4f);
                ui.statPill.draw(batch, x, rowY, width, rowHeight);
                float fraction = Math.max(0f, Math.min(1f,
                    state.currentValue() / (float) Math.max(1, state.maximumValue())));
                batch.setColor(BattleUiAssets.CURSED_ENERGY);
                batch.draw(ui.pixel, x + 5f, rowY + 5f,
                    Math.max(0f, (width - 10f) * fraction), Math.max(0f, rowHeight - 10f));
                batch.setColor(Color.WHITE);

                String value = state.currentValue() + "/" + state.maximumValue();
                font.getData().setScale(originalScaleX, originalScaleY);
                font.setColor(BattleUiAssets.TEXT);
                valueLayout.setText(font, value);
                float textWidth = width - TEXT_PADDING * 2f;
                float textHeight = rowHeight - 8f;
                if (textWidth <= 0f || textHeight <= 0f) continue;
                float valueScale = Math.min(1f, Math.min(
                    textWidth / Math.max(1f, valueLayout.width),
                    textHeight / Math.max(1f, font.getCapHeight())));
                font.getData().setScale(
                    originalScaleX * valueScale, originalScaleY * valueScale);
                valueLayout.setText(font, value);
                float valueWidth = valueLayout.width;

                float availableNameWidth = Math.max(0f,
                    textWidth - TEXT_GAP - valueWidth);
                nameLayout.setText(font, "...");
                if (availableNameWidth >= nameLayout.width) {
                    String displayName = state.displayName() == null ? "" : state.displayName();
                    nameLayout.setText(font, displayName, 0, displayName.length(),
                        BattleUiAssets.TEXT, availableNameWidth, Align.left, false, "...");
                } else {
                    nameLayout.setText(font, "");
                }

                float textY = rowY + (rowHeight + font.getCapHeight()) / 2f;
                if (nameLayout.glyphCount > 0) {
                    font.draw(batch, nameLayout, x + TEXT_PADDING, textY);
                }

                font.draw(batch, valueLayout, x + width - TEXT_PADDING - valueWidth, textY);
                font.getData().setScale(originalScaleX, originalScaleY);
            }
        } finally {
            font.getData().setScale(originalScaleX, originalScaleY);
            font.setColor(previousFontColor);
            batch.setColor(previousBatchColor);
        }
    }
}
