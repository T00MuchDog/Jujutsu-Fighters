package com.jjktbf.graphics.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.jjktbf.graphics.ui.battle.BattleUiAssets;
import com.jjktbf.model.character.coded.CodedAbilityState;
import com.jjktbf.model.character.coded.MiraclesAbility;
import com.jjktbf.model.character.coded.RatioAbility;

import java.util.List;

/** Compact meter for data-defined bounded resources. */
public final class AbilityStateMeter {

    private List<CodedAbilityState> states = List.of();
    private float x;
    private float y;
    private float width;
    private float rowHeight;

    public void setStates(List<CodedAbilityState> states) {
        this.states = states == null ? List.of() : states.stream()
            .filter(state -> state != null && state.maximumValue() > 0)
            .filter(state -> !MiraclesAbility.KEY.equalsIgnoreCase(state.key()))
            .filter(state -> !RatioAbility.KEY.equalsIgnoreCase(state.key()))
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
        GlyphLayout layout = new GlyphLayout();
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
            String label = state.displayName() + "  "
                + state.currentValue() + "/" + state.maximumValue();
            layout.setText(font, label);
            font.setColor(BattleUiAssets.TEXT);
            font.draw(batch, label, x + Math.max(8f, (width - layout.width) / 2f),
                rowY + (rowHeight + font.getCapHeight()) / 2f);
        }
        font.setColor(previousFontColor);
        batch.setColor(previousBatchColor);
    }
}
