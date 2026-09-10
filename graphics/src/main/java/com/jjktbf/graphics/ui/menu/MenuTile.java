package com.jjktbf.graphics.ui.menu;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.jjktbf.graphics.AssetLoader;

/** Accessible Scene2D hit target with distinct hover, focus, press and disabled ink frames. */
public final class MenuTile extends Button {
    private final MenuIllustration art;
    private final Runnable action;
    private final boolean compact;
    private final boolean contentCard;
    private boolean keyboardFocused;
    private boolean pointerHighlightEnabled = true;
    private float highlight;
    private float holdOutlineProgress;

    public MenuTile(AssetLoader assets, MenuIllustration art, String title, String category,
                    String description, boolean compact, Runnable action) {
        super(new ButtonStyle());
        this.art = art;
        this.action = action;
        this.compact = compact;
        this.contentCard = !description.isBlank();
        setName(title);
        pad(compact ? (contentCard ? 22 : 12) : 25);
        if (!compact) {
            Label eyebrow = label(assets, category, 21, MenuIllustration.MUTED);
            add(eyebrow).growX().left().padBottom(15).row();
        }
        Label heading = label(assets, title, compact ? (contentCard ? 33 : 25) : 37, MenuIllustration.PAPER);
        heading.setWrap(true);
        add(heading).growX().left().padRight(contentCard ? 42 : 0).row();
        if (compact && contentCard) {
            Label body = label(assets, description, 24, MenuIllustration.MUTED);
            body.setWrap(true);
            add(body).growX().left().padTop(14);
        }
        if (!compact) {
            Label body = label(assets, description, 24, MenuIllustration.PAPER);
            body.setWrap(true);
            add(body).growX().left().padTop(12).expandY().top().row();
            Label prompt = label(assets, "OPEN  >", 21, MenuIllustration.YELLOW);
            add(prompt).growX().left().padTop(15);
        }
        addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) { activate(); }
        });
    }

    public static Label label(AssetLoader assets, String text, float size, Color color) {
        Label label = new Label(text, new Label.LabelStyle(assets.gameplayFontXLarge, color));
        // Scene2D font scales are absolute; gameplay XL is generated at 75 * oversample.
        // The bundled face's cap height is roughly half its em. Lift supporting
        // text independently so small displays retain readable labels, not microtype.
        label.setFontScale(size * (size < 60f ? 1.4f : 1f) / (75f * AssetLoader.FONT_OVERSAMPLE));
        label.setAlignment(Align.left);
        return label;
    }

    public void activate() {
        if (isDisabled()) return;
        setChecked(false);
        action.run();
    }

    public void setKeyboardFocused(boolean value) { keyboardFocused = value; }
    public boolean isKeyboardFocused() { return keyboardFocused; }
    public void setPointerHighlightEnabled(boolean value) { pointerHighlightEnabled = value; }
    public void setHoldOutlineProgress(float progress) {
        holdOutlineProgress = MathUtils.clamp(progress, 0f, 1f);
    }
    public float holdOutlineProgress() { return holdOutlineProgress; }
    @Override public boolean isOver() { return pointerHighlightEnabled && super.isOver(); }

    @Override public void act(float delta) {
        super.act(delta);
        float target = isOver() || keyboardFocused ? 1 : 0;
        highlight += (target - highlight) * Math.min(1f, delta * 14f);
    }

    @Override public void draw(Batch batch, float parentAlpha) {
        validate();
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        // While holding Escape, the progress stroke is the only yellow border;
        // the normal focus edge must not make the unfinished perimeter look complete.
        boolean focus = holdOutlineProgress <= 0f && (isOver() || keyboardFocused);
        Color fill = isDisabled() ? MenuIllustration.INK
            : isPressed() ? MenuIllustration.INK : MenuIllustration.PANEL;
        Color edge = isDisabled() ? MenuIllustration.PANEL
            : focus ? MenuIllustration.YELLOW : MenuIllustration.MUTED;
        art.rect(batch, x, y - 5, w, h, Color.BLACK);
        art.rect(batch, x, y, w, h, edge);
        art.rect(batch, x + 2, y + 2, w - 4, h - 4, fill);
        art.rect(batch, x, y, focus ? 6 : 3, h, edge);
        if (contentCard) {
            // Cut the upper corner like a clipped manga panel, with an offset ink edge.
            for (int i = 0; i < 16; i++) {
                art.rect(batch, x + w - 16 + i, y + h - i - 1, 16 - i, 1, MenuIllustration.INK);
            }
            art.line(batch, x + w - 16, y + h - 1, x + w - 1, y + h - 16, 2, edge);
        }
        if (highlight > 0.01f) art.rect(batch, x + 2, y + h - 4, (w - 4) * highlight, 3, MenuIllustration.YELLOW);
        drawHoldOutline(batch, x, y, w, h);
        batch.setColor(Color.WHITE);
        super.draw(batch, parentAlpha * (isDisabled() ? .4f : 1f));
    }

    /** Draw clockwise from the upper-left; completion exactly closes the perimeter. */
    private void drawHoldOutline(Batch batch, float x, float y, float w, float h) {
        if (holdOutlineProgress <= 0f) return;
        float thickness = 4f;
        float remaining = holdOutlineProgress * (w * 2f + h * 2f);
        float length = Math.min(remaining, w);
        art.rect(batch, x, y + h - thickness, length, thickness, MenuIllustration.YELLOW);
        remaining -= length;
        if (remaining <= 0f) return;
        length = Math.min(remaining, h);
        art.rect(batch, x + w - thickness, y + h - length, thickness, length, MenuIllustration.YELLOW);
        remaining -= length;
        if (remaining <= 0f) return;
        length = Math.min(remaining, w);
        art.rect(batch, x + w - length, y, length, thickness, MenuIllustration.YELLOW);
        remaining -= length;
        if (remaining <= 0f) return;
        length = Math.min(remaining, h);
        art.rect(batch, x, y, thickness, length, MenuIllustration.YELLOW);
    }
}
