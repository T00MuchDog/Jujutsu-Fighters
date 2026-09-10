package com.jjktbf.graphics.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.jjktbf.graphics.AssetLoader;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.audio.SoundCue;
import com.jjktbf.graphics.ui.HoverScrollStage;
import com.jjktbf.graphics.ui.UiScaleSystem;
import com.jjktbf.graphics.ui.menu.MainMenuAction;
import com.jjktbf.graphics.ui.menu.MainMenuLayout;
import com.jjktbf.graphics.ui.menu.MenuIllustration;
import com.jjktbf.graphics.ui.menu.MenuTile;

import java.util.ArrayList;
import java.util.List;

/** Evaluation variant: artwork-led hub with gameplay first and a separate editor workspace. */
public final class RedesignedMainMenuScreen implements Screen {
    /** The outline and hold-to-exit threshold intentionally share this duration. */
    static final float ESCAPE_HOLD_SECONDS = 1.25f;
    private final JJKGame game;
    private final AssetLoader assets;
    private final Stage stage;
    private final Group content = new Group();
    private final MenuIllustration illustration;
    private final MainMenuSupport support;
    private final List<MenuTile> controls = new ArrayList<>();
    private final List<MenuTile> topControls = new ArrayList<>();
    private final List<MenuTile> pageControls = new ArrayList<>();
    private final List<MenuTile> bottomControls = new ArrayList<>();
    private boolean editors;
    private int pageColumns;
    private int focused = -1;
    private boolean disposed;
    private MenuTile exitTile;
    private boolean escapeHeld;
    private boolean escapeExitTriggered;
    private float escapeHoldElapsed;

    public RedesignedMainMenuScreen(JJKGame game, AssetLoader assets) {
        this.game = game;
        this.assets = assets;
        stage = new HoverScrollStage(UiScaleSystem.newViewport(game.activeUiProfile()));
        illustration = new MenuIllustration(assets);
        support = new MainMenuSupport(game, assets, stage, this::clearFocus);
        stage.addActor(content);
        stage.addCaptureListener(new InputListener() {
            @Override public boolean mouseMoved(InputEvent event, float x, float y) {
                if (!support.isModalOpen()) focusPointerAt(event.getStageX(), event.getStageY());
                return false;
            }

            @Override public boolean keyDown(InputEvent event, int key) {
                if (support.isModalOpen()) {
                    boolean handled = support.modalKey(key);
                    if (key == Input.Keys.ESCAPE || key == Input.Keys.BACK) event.cancel();
                    return handled;
                }
                switch (key) {
                    case Input.Keys.F8 -> support.switchVariant();
                    case Input.Keys.F1 -> support.manual();
                    case Input.Keys.F2 -> support.credits();
                    case Input.Keys.TAB -> { }
                    case Input.Keys.RIGHT, Input.Keys.LEFT -> moveHorizontal(key == Input.Keys.RIGHT ? 1 : -1);
                    case Input.Keys.UP, Input.Keys.DOWN -> moveVertical(key == Input.Keys.DOWN ? 1 : -1);
                    case Input.Keys.ENTER, Input.Keys.SPACE -> {
                        if (focused < 0) focus(pageControls.get(0));
                        else controls.get(focused).activate();
                    }
                    case Input.Keys.ESCAPE -> {
                        if (editors) changeSection(false);
                        else beginEscapeHold();
                    }
                    case Input.Keys.BACK -> {
                        if (editors) changeSection(false);
                        else focus(exitTile);
                    }
                    default -> { return false; }
                }
                return true;
            }

            @Override public boolean keyUp(InputEvent event, int key) {
                if (key != Input.Keys.ESCAPE || support.isModalOpen()) return false;
                cancelEscapeHold();
                return true;
            }
        });
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void changeSection(boolean value) {
        if (editors == value) return;
        editors = value;
        game.audio().play(SoundCue.UI_CONFIRM);
        rebuild();
    }

    private void rebuild() {
        String previous = focused >= 0 && focused < controls.size() ? controls.get(focused).getName() : null;
        clearFocus();
        content.clearChildren();
        controls.clear();
        topControls.clear();
        pageControls.clear();
        bottomControls.clear();
        List<MainMenuAction> actions = editors ? MainMenuAction.editors()
            : MainMenuAction.modes(game.isAuthorBattleAvailable());
        MainMenuLayout layout = MainMenuLayout.calculate(stage.getWidth(), stage.getHeight(),
            Gdx.graphics.getWidth(), editors, actions.size());
        pageColumns = layout.columns();
        float w = layout.width(), h = layout.height();
        content.setSize(w, h);
        content.setScale(layout.scale());
        illustration.setSize(w, h);
        illustration.setEditors(editors);
        content.addActor(illustration);

        text("JUJUTSU FIGHTERS", 26, MenuIllustration.PAPER, 48, h - 83, 420, 40);
        shortcut(topControls, "GAME MODES", () -> changeSection(false), w - 660, h - 91, 206, 48);
        shortcut(topControls, "EDITORS", () -> changeSection(true), w - 438, h - 91, 166, 48);
        shortcut(topControls, "SETTINGS", support::showSettings, w - 256, h - 91, 208, 48);

        int rows = (actions.size() + layout.columns() - 1) / layout.columns();
        float gridBottom = 148, gap = 16;
        float gridTop = gridBottom + rows * layout.cardHeight() + (rows - 1) * gap;
        boolean dense = rows > 2 || (!editors && rows > 1);
        text(editors ? "CREATE / CUSTOMIZE / REFINE" : "A JUJUTSU KAISEN TACTICAL FIGHTER",
            22, MenuIllustration.ENERGY, 52, h - 177, w * .5f, 30);
        if (!editors && !dense) {
            text("JUJUTSU", 110, MenuIllustration.PAPER, 48, h - 294, w * .52f, 112);
            text("FIGHTERS", 110, MenuIllustration.PAPER, 48, h - 386, w * .52f, 112);
        } else {
            text(editors ? "THE WORKSHOP" : "JUJUTSU FIGHTERS", dense ? 68 : 82,
                MenuIllustration.PAPER, 48, h - 275, w * .7f, 96);
            if (!dense) text("Build your own corner of the jujutsu world.", 27,
                MenuIllustration.MUTED, 52, h - 325, w * .52f, 42);
        }
        text(editors ? "EDITORS / CONTENT TOOLS" : "GAME MODES / CHOOSE YOUR BATTLE", 24,
            MenuIllustration.YELLOW, 48, gridTop + 22, w - 96, 34);

        float cardWidth = (w - 96 - gap * (layout.columns() - 1)) / layout.columns();
        for (int i = 0; i < actions.size(); i++) {
            MainMenuAction action = actions.get(i);
            boolean compact = editors || layout.cardHeight() < 150;
            MenuTile tile = new MenuTile(assets, illustration, action.title, action.category,
                action.description, compact,
                () -> support.activate(action));
            add(pageControls, tile, 48 + (i % layout.columns()) * (cardWidth + gap),
                gridTop - (i / layout.columns() + 1) * layout.cardHeight() - (i / layout.columns()) * gap,
                cardWidth, layout.cardHeight());
        }
        text(editors ? "LOCAL CONTENT  /  Online battles use the server's canonical roster."
                : "FIRST TIME?  Start with Single Player > 1V1. The Field Manual covers your first round.",
            23, MenuIllustration.MUTED, 48, 94, w - 96, 36);

        shortcut(bottomControls, "MANUAL / F1", support::manual, 48, 17, 188, 43);
        shortcut(bottomControls, "CREDITS", support::credits, 248, 17, 144, 43);
        shortcut(bottomControls, "LEGACY MENU / F8", support::switchVariant, w - 462, 17, 270, 43);
        exitTile = shortcut(bottomControls, "EXIT", support::exit, w - 176, 17, 128, 43);
        exitTile.setHoldOutlineProgress(escapeHoldElapsed / ESCAPE_HOLD_SECONDS);
        if (previous != null) {
            for (int i = 0; i < controls.size(); i++) {
                if (previous.equals(controls.get(i).getName())) { focus(controls.get(i)); break; }
            }
        }
    }

    private void text(String text, float size, com.badlogic.gdx.graphics.Color color,
                      float x, float y, float width, float height) {
        Label label = MenuTile.label(assets, text, size, color);
        label.setBounds(x, y, width, height);
        label.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        content.addActor(label);
    }

    private MenuTile shortcut(List<MenuTile> tier, String title, Runnable action,
                              float x, float y, float width, float height) {
        MenuTile tile = new MenuTile(assets, illustration, title, "", "", true, action);
        add(tier, tile, x, y, width, height);
        return tile;
    }

    private void add(List<MenuTile> tier, MenuTile tile, float x, float y, float width, float height) {
        tile.setBounds(x, y, width, height);
        tier.add(tile);
        controls.add(tile);
        content.addActor(tile);
    }

    private void clearFocus() {
        focused = -1;
        for (MenuTile control : controls) {
            control.setKeyboardFocused(false);
            control.setPointerHighlightEnabled(true);
        }
    }

    private void focus(MenuTile tile) {
        clearFocus();
        if (tile == null || tile.isDisabled()) return;
        for (MenuTile control : controls) control.setPointerHighlightEnabled(false);
        focused = controls.indexOf(tile);
        tile.setKeyboardFocused(true);
    }

    private void focusPointerAt(float stageX, float stageY) {
        Actor target = stage.hit(stageX, stageY, true);
        for (MenuTile control : controls) {
            if (target == control || target != null && target.isDescendantOf(control)) {
                focus(control);
                return;
            }
        }
        clearFocus();
    }

    private void moveHorizontal(int direction) {
        MenuTile current = focusedControl();
        if (current == null) {
            focusAndSound(activeTopControl());
            return;
        }
        List<MenuTile> tier = tierOf(current);
        int index = tier.indexOf(current);
        int next = index + direction;
        if (tier == pageControls) {
            int rowStart = index / pageColumns * pageColumns;
            int rowEnd = Math.min(rowStart + pageColumns, pageControls.size()) - 1;
            if (next < rowStart || next > rowEnd) return;
        } else if (next < 0 || next >= tier.size()) {
            return;
        }
        focusAndSound(tier.get(next));
    }

    private void moveVertical(int direction) {
        MenuTile current = focusedControl();
        if (current == null) {
            focusAndSound(direction > 0 ? pageControls.get(0) : activeTopControl());
            return;
        }
        List<MenuTile> tier = tierOf(current);
        if (tier == topControls) {
            if (direction > 0) focusAndSound(pageControls.get(0));
            return;
        }
        if (tier == bottomControls) {
            if (direction < 0) {
                int lastRowStart = (pageControls.size() - 1) / pageColumns * pageColumns;
                focusAndSound(pageControls.get(lastRowStart));
            }
            return;
        }

        int index = pageControls.indexOf(current);
        int row = index / pageColumns;
        int lastRow = (pageControls.size() - 1) / pageColumns;
        if (direction < 0 && row == 0) {
            focusAndSound(topControls.get(0));
        } else if (direction > 0 && row == lastRow) {
            focusAndSound(bottomControls.get(0));
        } else {
            int targetRow = row + direction;
            int start = targetRow * pageColumns;
            focusAndSound(nearestByX(pageControls, start,
                Math.min(start + pageColumns, pageControls.size()), centerX(current)));
        }
    }

    private MenuTile nearestByX(List<MenuTile> tier, int start, int end, float x) {
        MenuTile best = null;
        float distance = Float.MAX_VALUE;
        for (int i = start; i < end; i++) {
            MenuTile candidate = tier.get(i);
            float candidateDistance = Math.abs(centerX(candidate) - x);
            if (!candidate.isDisabled() && candidateDistance < distance) {
                best = candidate;
                distance = candidateDistance;
            }
        }
        return best;
    }

    private void focusAndSound(MenuTile tile) {
        if (tile == null || tile == focusedControl()) return;
        focus(tile);
        game.audio().play(SoundCue.UI_NAVIGATE);
    }

    private MenuTile focusedControl() {
        return focused >= 0 && focused < controls.size() ? controls.get(focused) : null;
    }

    private List<MenuTile> tierOf(MenuTile control) {
        if (topControls.contains(control)) return topControls;
        if (pageControls.contains(control)) return pageControls;
        return bottomControls;
    }

    private MenuTile activeTopControl() {
        return topControls.get(0);
    }

    private static float centerX(Actor actor) {
        return actor.getX() + actor.getWidth() / 2f;
    }

    private void beginEscapeHold() {
        if (escapeHeld || escapeExitTriggered) return;
        escapeHeld = true;
        escapeHoldElapsed = 0f;
        game.audio().play(SoundCue.UI_BACK);
        exitTile.setHoldOutlineProgress(0f);
    }

    private void cancelEscapeHold() {
        escapeHeld = false;
        escapeHoldElapsed = 0f;
        if (exitTile != null) exitTile.setHoldOutlineProgress(0f);
    }

    private void updateEscapeHold(float delta) {
        if (!escapeHeld || escapeExitTriggered || exitTile == null) return;
        if (support.isModalOpen() || editors) {
            cancelEscapeHold();
            return;
        }
        escapeHoldElapsed = Math.min(ESCAPE_HOLD_SECONDS,
            escapeHoldElapsed + Math.max(0f, delta));
        exitTile.setHoldOutlineProgress(escapeHoldElapsed / ESCAPE_HOLD_SECONDS);
        if (escapeHoldElapsed < ESCAPE_HOLD_SECONDS) return;
        escapeExitTriggered = true;
        escapeHeld = false;
        support.exitImmediately();
    }

    @Override public void show() {
        stage.unfocusAll();
        clearFocus();
        Gdx.input.setInputProcessor(stage);
    }
    @Override public void render(float delta) {
        Gdx.gl.glClearColor(.063f, .094f, .153f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        updateEscapeHold(delta);
        stage.act(delta);
        stage.draw();
    }
    @Override public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        stage.getViewport().update(width, height, true);
        rebuild();
        support.resize();
    }
    @Override public void hide() {
        cancelEscapeHold();
        support.close();
    }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        support.dispose();
        stage.dispose();
        illustration.dispose();
    }
}
