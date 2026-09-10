package com.jjktbf.graphics.ui.menu;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Disposable;
import com.jjktbf.graphics.AssetLoader;

/** Batched, low-motion manga illustration using the game's own pixel art. */
public final class MenuIllustration extends Actor implements Disposable {
    public static final Color INK = Color.valueOf("101827");
    public static final Color PANEL = Color.valueOf("232E46");
    public static final Color PAPER = Color.valueOf("F6F0DC");
    public static final Color MUTED = Color.valueOf("ADB9CB");
    public static final Color YELLOW = Color.valueOf("FFD52E");
    public static final Color ENERGY = Color.valueOf("60D8E5");
    private final Texture pixel;
    private final AssetLoader assets;
    private final Texture yuji;
    private final Texture megumi;
    private final Texture nanami;
    private boolean editors;

    public MenuIllustration(AssetLoader assets) {
        this.assets = assets;
        Pixmap map = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        map.setColor(Color.WHITE);
        map.fill();
        pixel = new Texture(map);
        map.dispose();
        yuji = assets.characterSprite("assets/sprites/characters/yuji_frontsprite.png", assets.playerSprite);
        megumi = assets.characterSprite("assets/sprites/characters/megumi_frontsprite.png", assets.enemySprite);
        nanami = assets.characterSprite("assets/sprites/characters/nanami_frontsprite.png", assets.enemySprite);
        setTouchable(Touchable.disabled);
    }

    public void setEditors(boolean editors) { this.editors = editors; }

    @Override public void draw(Batch batch, float parentAlpha) {
        float w = getWidth(), h = getHeight();
        rect(batch, 0, 0, w, h, INK);
        // Quiet print grain, fixed rather than flickering particles.
        for (int x = 12; x < w; x += 18) {
            for (int y = 80; y < h - 100; y += 18) {
                rect(batch, x, y, 1, 1, PANEL);
            }
        }
        float artX = w * 0.52f, artY = editors ? h - 350f : h - 470f;
        float artW = w - artX - 30, artH = editors ? 210 : 330;
        batch.setColor(0.38f, 0.46f, 0.61f, 0.65f);
        batch.draw(assets.battleExecutionBackground, artX, artY, artW, artH,
            0f, 0.82f, 1f, 0.05f);
        // Diagonal ink cuts turn the existing courtyard into a manga panel.
        for (int i = 0; i < 36; i++) {
            rect(batch, artX, artY + i * artH / 36f, (36 - i) * 3f, artH / 36f + 1, INK);
        }
        line(batch, artX + 110, artY + artH + 8, w - 34, artY + artH + 8, 3, ENERGY);
        if (!editors) {
            sprite(batch, megumi, artX + artW * 0.13f, artY + 8, 230, 0.70f);
            sprite(batch, nanami, artX + artW * 0.65f, artY + 6, 232, 0.74f);
            sprite(batch, yuji, artX + artW * 0.34f, artY - 6, 300, 1f);
        }
        // Ink underlines echo the planner's hard-edged frames.
        line(batch, 48, h - 114, w - 48, h - 114, 1, MUTED);
        rect(batch, 48, h - 118, 92, 5, YELLOW);
        rect(batch, 0, 0, w, 76, PAPER);
        batch.setColor(Color.WHITE);
    }

    private void sprite(Batch batch, Texture texture, float x, float y, float height, float brightness) {
        batch.setColor(brightness, brightness, brightness, 1f);
        batch.draw(texture, x, y, height * texture.getWidth() / texture.getHeight(), height);
    }

    public void rect(Batch batch, float x, float y, float w, float h, Color color) {
        batch.setColor(color);
        batch.draw(pixel, x, y, w, h);
    }

    public void line(Batch batch, float x1, float y1, float x2, float y2, float thickness, Color color) {
        batch.setColor(color);
        float dx = x2 - x1, dy = y2 - y1;
        batch.draw(pixel, x1, y1, 0, thickness / 2, (float) Math.sqrt(dx * dx + dy * dy), thickness,
            1, 1, MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees, 0, 0, 1, 1, false, false);
    }

    @Override public void dispose() { pixel.dispose(); }
}
