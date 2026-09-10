package com.jjktbf.graphics.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Timer;
import com.jjktbf.graphics.AssetLoader;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.audio.SoundCue;
import com.jjktbf.graphics.ui.ContentSizedDialog;
import com.jjktbf.graphics.ui.menu.MainMenuAction;
import com.jjktbf.graphics.ui.menu.MenuIllustration;
import com.jjktbf.graphics.ui.menu.MenuTile;

/** Behavior shared by both presentations, including local, offline-readable help. */
final class MainMenuSupport {
    private final JJKGame game;
    private final AssetLoader assets;
    private final Stage stage;
    private final Runnable resetNavigation;
    final SettingsDialogController settings;
    private ContentSizedDialog information;
    private ScrollPane informationScroll;
    private Label informationBody;
    private TextButton informationClose;
    private Timer.Task exitTask;

    MainMenuSupport(JJKGame game, AssetLoader assets, Stage stage, Runnable resetNavigation) {
        this.game = game;
        this.assets = assets;
        this.stage = stage;
        this.resetNavigation = resetNavigation;
        settings = new SettingsDialogController(game, assets, stage, false, resetNavigation);
    }

    void activate(MainMenuAction action) {
        game.audio().play(SoundCue.UI_CONFIRM);
        action.invoke(game);
    }

    void switchVariant() {
        game.audio().play(SoundCue.UI_CONFIRM);
        // Let Scene2D finish dispatching before the old stage is disposed.
        Gdx.app.postRunnable(game::toggleMainMenuVariant);
    }

    void showSettings() {
        game.audio().play(SoundCue.UI_CONFIRM);
        settings.show();
    }

    boolean isModalOpen() {
        return settings.isOpen() || information != null;
    }

    boolean modalKey(int key) {
        if (!isModalOpen()) return false;
        if (key == Input.Keys.ESCAPE || key == Input.Keys.BACK) {
            game.audio().play(SoundCue.UI_BACK);
            close();
        } else if (information != null && (key == Input.Keys.ENTER || key == Input.Keys.SPACE)) {
            close();
        } else if (information != null && (key == Input.Keys.DOWN || key == Input.Keys.UP)) {
            ScrollPane pane = (ScrollPane) information.getContentTable().getChildren().first();
            pane.setScrollY(pane.getScrollY() + (key == Input.Keys.DOWN ? 60f : -60f));
        } else if (information == null) {
            return false; // Let Settings' selectors, sliders and text fields handle their keys.
        }
        return true;
    }

    void manual() {
        information("FIELD MANUAL", "YOUR FIRST BATTLE\n\n"
            + "1. Choose Single Player, then 1V1 and Standard stats.\n"
            + "2. Select your fighter and the CPU fighter. Use Learned Moves to build a move set, or choose Random Fighter.\n"
            + "3. Select Start Battle. Click a move to queue it, or drag it onto the Offense or Defense timeline.\n"
            + "4. Drag a queued move to change its timing. Right-click to remove it. Check targets in team battles.\n"
            + "5. Lock In every fighter's plan. Watch the round, then choose Next Round.\n\n"
            + "READ THE TIMELINE\n\n"
            + "AP is your round's planning budget. CE is Cursed Energy, spent when moves begin. Offense and Defense have separate timelines but share these budgets.\n\n"
            + "Unleash marks when a move's effect fires. Time your defense before the opponent's attack, and keep CE for later rounds. Defeat every opposing fighter to win.\n\n"
            + "MORE WAYS TO PLAY\n\n"
            + "Multiplayer opens online challenges: host a match or browse opponents. Author Battle, when available, lets you control both teams locally. Editors customize the game's authored content.\n\n"
            + "MENU CONTROLS\n\n"
            + "Mouse to select. On the redesigned menu, Left and Right stay within a tier; Up and Down cross between the top navigation, page choices and footer only at row boundaries. Enter or Space activates. F1 opens this manual. F8 switches Legacy / Redesigned. Escape closes a panel; on the redesigned Game Modes screen, hold Escape for 1.25 seconds to exit.\n\n"
            + "In battle, hover highlighted keywords for definitions. The repository's GLOSSARY.txt includes the full mechanics reference.");
    }

    void credits() {
        information("CREDITS", "JUJUTSU FIGHTERS\n\n"
            + "A fan-made tactical fighting game.\n\n"
            + "GAME & CONTRIBUTORS\n"
            + "T00MuchDog / Jujutsu-Fighters project and its contributors.\n\n"
            + "ORIGINAL SERIES\n"
            + "Jujutsu Kaisen by Gege Akutami.\n\n"
            + "BUILT WITH\n"
            + "Java, LibGDX and LWJGL.\n\n"
            + "VISUALS\n"
            + "The game's existing pixel-art fighters and courtyard, Atlantis International typeface, and original procedural menu framing and seal motifs.\n\n"
            + "Thank you for playing.");
    }

    private void information(String title, String text) {
        if (isModalOpen()) return;
        resetNavigation.run();
        game.audio().play(SoundCue.UI_CONFIRM);
        information = new ContentSizedDialog(title, assets.editorSkin, game.activeUiProfile());
        information.setMovable(false);
        float scale = Math.min(stage.getWidth() / 1440f, stage.getHeight() / 900f);
        Window.WindowStyle windowStyle = new Window.WindowStyle(information.getStyle());
        windowStyle.stageBackground = assets.editorSkin.newDrawable("battle-header", new Color(0, 0, 0, .62f));
        information.setStyle(windowStyle);
        information.getTitleLabel().setStyle(new Label.LabelStyle(assets.gameplayFontXLarge, MenuIllustration.INK));
        information.getTitleLabel().setFontScale(30f * scale * 1.4f / (75f * AssetLoader.FONT_OVERSAMPLE));
        information.padTop(48f * scale);
        Label body = MenuTile.label(assets, text, 26f * scale, MenuIllustration.INK);
        body.setWrap(true);
        ScrollPane scroll = new ScrollPane(body, assets.editorSkin);
        scroll.setScrollingDisabled(true, false);
        scroll.setFadeScrollBars(false);
        information.getContentTable().add(scroll)
            .width(Math.min(stage.getWidth() - 100f, 720f * scale))
            .height(Math.min(stage.getHeight() - 160f, 570f * scale)).pad(24f);
        TextButton close = new TextButton("CLOSE  [ESC]", assets.editorSkin, "primary");
        close.getLabel().setStyle(new Label.LabelStyle(assets.gameplayFontXLarge, Color.WHITE));
        close.getLabel().setFontScale(23f * scale * 1.4f / (75f * AssetLoader.FONT_OVERSAMPLE));
        close.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) { MainMenuSupport.this.close(); }
        });
        information.getButtonTable().add(close).height(46f * scale).width(220f * scale).pad(10f);
        informationScroll = scroll;
        informationBody = body;
        informationClose = close;
        information.show(stage);
        stage.setScrollFocus(scroll);
        stage.setKeyboardFocus(close);
    }

    void resize() {
        settings.recenter();
        if (information == null) return;
        float scale = Math.min(stage.getWidth() / 1440f, stage.getHeight() / 900f);
        informationBody.setFontScale(26f * scale * 1.4f / (75f * AssetLoader.FONT_OVERSAMPLE));
        information.getContentTable().getCell(informationScroll)
            .width(Math.min(stage.getWidth() - 100f, 720f * scale))
            .height(Math.min(stage.getHeight() - 160f, 570f * scale));
        information.getTitleLabel().setFontScale(30f * scale * 1.4f / (75f * AssetLoader.FONT_OVERSAMPLE));
        information.padTop(48f * scale);
        informationClose.getLabel().setFontScale(23f * scale * 1.4f / (75f * AssetLoader.FONT_OVERSAMPLE));
        information.getButtonTable().getCell(informationClose).height(46f * scale).width(220f * scale);
        information.pack();
        information.setPosition((stage.getWidth() - information.getWidth()) / 2,
            (stage.getHeight() - information.getHeight()) / 2);
    }

    void exit() {
        if (exitTask != null) return;
        game.audio().play(SoundCue.UI_BACK);
        exitTask = Timer.schedule(new Timer.Task() {
            @Override public void run() { Gdx.app.exit(); }
        }, 0.16f);
    }

    void exitImmediately() {
        Gdx.app.exit();
    }

    void close() {
        settings.close();
        if (information != null) {
            information.remove();
            information = null;
            informationScroll = null;
            informationBody = null;
            informationClose = null;
            stage.unfocusAll();
            resetNavigation.run();
        }
    }

    void dispose() {
        close();
        if (exitTask != null) exitTask.cancel();
    }
}
