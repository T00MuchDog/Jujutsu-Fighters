package com.jjktbf.graphics.screens;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Align;
import com.jjktbf.graphics.AssetLoader;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.audio.AudioChannel;
import com.jjktbf.graphics.audio.AudioSettings;
import com.jjktbf.graphics.audio.BattleMusicSelection;
import com.jjktbf.graphics.audio.SoundCue;
import com.jjktbf.graphics.display.WindowsResolution;
import com.jjktbf.graphics.ui.ContentSizedDialog;
import com.jjktbf.graphics.ui.DynamicSelectBox;
import com.jjktbf.graphics.ui.editor.HoverTextField;
import com.jjktbf.graphics.ui.profile.UiProfile;

import java.util.Objects;
import java.util.function.IntConsumer;

/** Shared settings dialog used by menu and battle screens. */
final class SettingsDialogController {
    private final JJKGame game;
    private final AssetLoader assets;
    private final Stage stage;
    private final boolean liveBattleMusic;
    private final Runnable visibilityChanged;
    private final boolean windowsLayout;
    private ContentSizedDialog dialog;

    SettingsDialogController(
        JJKGame game,
        AssetLoader assets,
        Stage stage,
        boolean liveBattleMusic,
        Runnable visibilityChanged
    ) {
        this.game = Objects.requireNonNull(game, "game");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.stage = Objects.requireNonNull(stage, "stage");
        this.liveBattleMusic = liveBattleMusic;
        this.visibilityChanged = Objects.requireNonNull(visibilityChanged, "visibilityChanged");
        this.windowsLayout = game.activeUiProfile() == UiProfile.WINDOWS;
    }

    ImageButton createButton() {
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.imageUp = assets.editorSkin.getDrawable("settings-icon");
        style.imageOver = assets.editorSkin.getDrawable("settings-icon-highlighted");
        style.imageDown = style.imageOver;

        ImageButton button = new ImageButton(style);
        button.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.audio().play(SoundCue.UI_CONFIRM);
                show();
            }
        });
        return button;
    }

    void show() {
        if (isOpen()) return;

        Skin skin = assets.editorSkin;
        ContentSizedDialog nextDialog = new ContentSizedDialog(
            "SETTINGS", skin, game.activeUiProfile());
        nextDialog.setModal(true);
        nextDialog.setMovable(false);
        nextDialog.setResizable(false);

        TextButton close = new TextButton("X", closeButtonStyle(skin));
        close.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.audio().play(SoundCue.UI_BACK);
                close();
            }
        });
        float closeSize = windowsLayout ? 45f : 30f;
        nextDialog.getTitleTable().add(close).right().size(closeSize)
            .padLeft(8f).padRight(2f);

        AudioSettings settings = game.audio().settings();
        Table content = nextDialog.getContentTable();
        content.pad(10f, 16f, 14f, 16f);
        if (game.supportsResolutionSelection()) addResolutionRow(content);
        addBattleMusicRow(content, settings);
        addVolumeRow(content, "MUSIC", Math.round(settings.musicVolume() * 100f), value -> {
            AudioSettings current = game.audio().settings();
            game.audio().previewSettings(
                current.withChannelVolume(AudioChannel.MUSIC, value / 100f));
        });
        int effectsVolume = Math.round(
            (settings.uiSfxVolume() + settings.battleSfxVolume()) * 50f);
        addVolumeRow(content, "EFFECTS", effectsVolume, value -> {
            float volume = value / 100f;
            AudioSettings current = game.audio().settings();
            game.audio().previewSettings(
                current.withChannelVolume(AudioChannel.UI_SFX, volume)
                    .withChannelVolume(AudioChannel.BATTLE_SFX, volume));
        });

        dialog = nextDialog;
        nextDialog.show(stage);
        visibilityChanged.run();
    }

    boolean isOpen() {
        return dialog != null && dialog.getStage() != null;
    }

    void recenter() {
        if (!isOpen()) return;
        dialog.setPosition((stage.getWidth() - dialog.getWidth()) / 2f,
            (stage.getHeight() - dialog.getHeight()) / 2f);
    }

    void close() {
        if (dialog == null) return;
        stage.cancelTouchFocus();
        stage.setKeyboardFocus(null);
        stage.setScrollFocus(null);
        game.audio().persistSettings();
        dialog.remove();
        dialog = null;
        visibilityChanged.run();
    }

    private void addBattleMusicRow(Table content, AudioSettings settings) {
        Label nameLabel = new Label("BATTLE MUSIC", assets.editorSkin);
        DynamicSelectBox<BattleMusicSelection> choices = new DynamicSelectBox<>(
            assets.editorSkin, game.activeUiProfile());
        choices.setItems(BattleMusicSelection.values());
        choices.setSelected(settings.battleMusic());
        choices.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                game.audio().previewSettings(
                    game.audio().settings().withBattleMusic(choices.getSelected()));
                if (liveBattleMusic) game.audio().refreshBattleMusic();
            }
        });

        content.add(nameLabel).colspan(4).left().padTop(6f).padBottom(2f);
        content.row();
        content.add(choices).colspan(4).growX()
            .height(windowsLayout ? 66f : 44f).padBottom(8f);
        content.row();
    }

    private void addResolutionRow(Table content) {
        Label nameLabel = new Label("RESOLUTION", assets.editorSkin);
        DynamicSelectBox<WindowsResolution> resolutions = new DynamicSelectBox<>(
            assets.editorSkin, game.activeUiProfile());
        resolutions.setItems(game.availableWindowsResolutions());
        WindowsResolution current = game.currentWindowsResolution();
        resolutions.setSelected(current);

        boolean[] syncing = {false};
        resolutions.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (syncing[0]) return;
                WindowsResolution requested = resolutions.getSelected();
                WindowsResolution previous = game.currentWindowsResolution();
                if (requested == previous || game.applyWindowsResolution(requested)) return;
                syncing[0] = true;
                resolutions.setSelected(previous);
                syncing[0] = false;
            }
        });

        content.add(nameLabel).colspan(4).left().padTop(6f).padBottom(2f);
        content.row();
        content.add(resolutions).colspan(4).growX()
            .height(windowsLayout ? 66f : 44f).padBottom(8f);
        content.row();
    }

    private void addVolumeRow(
        Table content,
        String name,
        int initialValue,
        IntConsumer onChange
    ) {
        int initial = Math.max(0, Math.min(100, initialValue));
        Label nameLabel = new Label(name, assets.editorSkin);
        Label minimum = new Label("0", assets.editorSkin, "small");
        Label maximum = new Label("100", assets.editorSkin, "small");
        minimum.setColor(assets.editorSkin.get("text-dim", Color.class));
        maximum.setColor(assets.editorSkin.get("text-dim", Color.class));

        Slider slider = new Slider(0f, 100f, 1f, false, assets.editorSkin);
        slider.setValue(initial);
        TextField valueField = new HoverTextField(String.valueOf(initial), assets.editorSkin);
        valueField.setTextFieldFilter((field, character) -> Character.isDigit(character));
        valueField.setMaxLength(3);
        valueField.setAlignment(Align.center);

        boolean[] syncing = {false};
        int[] appliedValue = {initial};
        slider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (syncing[0]) return;
                int value = Math.round(slider.getValue());
                valueField.setText(String.valueOf(value));
                if (value == appliedValue[0]) return;
                appliedValue[0] = value;
                onChange.accept(value);
            }
        });

        Runnable commitField = () -> {
            int value = parseVolumePercent(valueField.getText(), Math.round(slider.getValue()));
            boolean changed = value != appliedValue[0];
            syncing[0] = true;
            slider.setValue(value);
            valueField.setText(String.valueOf(value));
            syncing[0] = false;
            if (changed) {
                appliedValue[0] = value;
                onChange.accept(value);
            }
        };
        valueField.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ENTER) return false;
                commitField.run();
                return true;
            }
        });
        valueField.addListener(new FocusListener() {
            @Override public void keyboardFocusChanged(
                FocusEvent event,
                Actor actor,
                boolean focused
            ) {
                if (!focused) commitField.run();
            }
        });

        content.add(nameLabel).colspan(4).left().padTop(6f).padBottom(2f);
        content.row();
        float minimumWidth = windowsLayout ? 30f : 20f;
        float maximumWidth = windowsLayout ? 51f : 34f;
        float valueWidth = windowsLayout ? 87f : 58f;
        float valueHeight = windowsLayout ? 51f : 34f;
        content.add(minimum).right().width(minimumWidth).padRight(5f);
        content.add(slider).growX().minWidth(100f).prefWidth(180f).height(32f).padRight(5f);
        content.add(maximum).left().width(maximumWidth).padRight(8f);
        content.add(valueField).width(valueWidth).height(valueHeight);
        content.row();
    }

    private static TextButton.TextButtonStyle closeButtonStyle(Skin skin) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(
            skin.get(TextButton.TextButtonStyle.class));
        style.up = null;
        style.down = null;
        style.over = null;
        style.fontColor = Color.GRAY;
        style.downFontColor = Color.DARK_GRAY;
        style.overFontColor = Color.LIGHT_GRAY;
        return style;
    }

    static int parseVolumePercent(String text, int fallback) {
        int safeFallback = Math.max(0, Math.min(100, fallback));
        if (text == null || text.isBlank()) return safeFallback;
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(text.trim())));
        } catch (NumberFormatException ignored) {
            return safeFallback;
        }
    }
}
