package com.jjktbf.graphics.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.jjktbf.graphics.AssetLoader;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.audio.SoundCue;
import com.jjktbf.graphics.ui.HoverScrollStage;
import com.jjktbf.graphics.ui.UiScaleSystem;
import com.jjktbf.graphics.ui.profile.UiProfile;
import com.jjktbf.graphics.ui.menu.MainMenuAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Main menu screen, using the same framed command palette as battle planning.
 *
 * Options are clickable and can be selected with the arrow keys; number keys
 * remain available as shortcuts.
 *
 * Mouse cursor is visible here (Scene2D Stage handles hit-testing).
 */
public class MainMenuScreen implements Screen {

    private static final float MAC_MAX_RESPONSIVE_SCALE = 1.30f;
    private static final float WINDOWS_MAX_RESPONSIVE_SCALE = 1.75f;
    private static final float WINDOWS_HEADER_X = 49f;
    private static final float WINDOWS_HEADER_WIDTH_INSET = 98f;
    private static final float WINDOWS_HEADER_HEIGHT = 100.8f;
    private static final float WINDOWS_HEADER_TOP_FROM_TOP = 28f;
    private static final float WINDOWS_SETTINGS_X = 70f;
    private static final float WINDOWS_SETTINGS_TOP_FROM_TOP = 158f;
    private static final float WINDOWS_SETTINGS_SIZE_PER_SCALE = 99.36f;
    private static final float WINDOWS_MENU_TOP_INSET = 128.8f;
    private static final float WINDOWS_MENU_HALF_WIDTH = 567f;
    private static final float WINDOWS_MENU_FIXED_HEIGHT = 70f;
    private static final float WINDOWS_MENU_SCALED_HEIGHT = 1075.1f;
    private static final float WINDOWS_AUTHOR_MENU_SCALED_HEIGHT = 1188.9f;
    private static final float WINDOWS_MENU_SIDE_CLEARANCE_BASE = 74f;
    private static final float WINDOWS_MENU_SIDE_CLEARANCE_SCALED = 322f;
    private static final float WINDOWS_MENU_MIN_HALF_WIDTH = 99f;
    private static final float WINDOWS_COMMAND_PADDING = 33.6f;
    private static final float WINDOWS_BUTTON_HEIGHT = 96.6f;
    private static final float WINDOWS_BUTTON_PADDING = 8.4f;
    private static final float WINDOWS_TITLE_FONT_SCALE = 0.25f;
    private static final float WINDOWS_BUTTON_FONT_SCALE = 0.39375f;

    private enum NavigationMode {
        NONE,
        CURSOR,
        KEYBOARD
    }

    private final JJKGame     game;
    private final AssetLoader assets;
    private final Stage       stage;
    private final Table       root;
    private final boolean     windowsLayout;
    private final boolean     authoringMenu;
    private final List<MenuButton> menuButtons = new ArrayList<>();
    private final List<Cell<MenuButton>> menuButtonCells = new ArrayList<>();
    private int selectedButtonIndex = -1;
    private int hoveredButtonIndex = -1;
    private int lastHighlightedButtonIndex = -1;
    private NavigationMode navigationMode = NavigationMode.NONE;
    /** Guards against double-dispose of native stage resources. */
    private boolean disposed;
    private Table header;
    private Label title;
    private FixedSizeTable commands;
    private ScrollPane commandsScroll;
    private Label commandTitle;
    private Cell<?> commandsCell;
    private ImageButton settingsButton;
    private Cell<ImageButton> settingsButtonCell;
    private final SettingsDialogController settingsDialog;
    private final MainMenuSupport support;
    private Table evaluationBar;

    public MainMenuScreen(JJKGame game, AssetLoader assets) {
        this.game   = game;
        this.assets = assets;
        this.stage  = new HoverScrollStage(UiScaleSystem.newViewport(game.activeUiProfile()));
        this.windowsLayout = game.activeUiProfile() == UiProfile.WINDOWS;
        this.authoringMenu = game.isAuthorBattleAvailable();
        this.support = new MainMenuSupport(game, assets, stage, this::resetNavigation);
        this.settingsDialog = support.settings;

        this.root = new Table();
        if (!windowsLayout) {
            root.setFillParent(true);
            root.pad(28);
            stage.addActor(root);
        }

        stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        buildMenu();
        layoutMenu(Math.round(stage.getWidth()), Math.round(stage.getHeight()));
    }

    private void buildMenu() {
        header = new Table(assets.editorSkin);
        header.setBackground(assets.editorSkin.getDrawable("battle-header"));
        header.pad(14);

        title = new Label("JJK TURN BASED FIGHTER", assets.editorSkin, "title");
        if (windowsLayout) {
            title.setAlignment(Align.center);
            header.add(title).grow().center();
            stage.addActor(header);
        } else {
            title.setAlignment(Align.left);
            header.add(title).left();
            root.add(header).growX().padBottom(10).row();
        }

        // This pointer-only control must never participate in arrow-key navigation.
        settingsButton = settingsDialog.createButton();
        if (windowsLayout) {
            settingsButton.getImageCell().expand().fill();
            stage.addActor(settingsButton);
        } else {
            settingsButtonCell = root.add(settingsButton).left().size(46).padBottom(8);
            root.row();
        }

        commands = new FixedSizeTable(assets.editorSkin);
        commands.setBackground(assets.editorSkin.getDrawable("battle-palette"));
        commands.pad(16);
        commandTitle = new Label("SELECT MODE", assets.editorSkin, "title");
        commandTitle.setColor(new Color(1.000f, 0.835f, 0.180f, 1f));
        commands.add(commandTitle).left().padBottom(10).row();

        List<MenuButton> buttons = new ArrayList<>();
        for (MainMenuAction action : MainMenuAction.available(authoringMenu)) {
            String label = action == MainMenuAction.AUTHOR_BATTLE
                ? "AUTHOR BATTLE (CONTROL BOTH SIDES)" : action.title;
            buttons.add(makeButton(label, () -> action.invoke(game)));
        }
        buttons.add(makeButton("QUIT", this::exitApplication));
        for (MenuButton button : buttons) {
            menuButtons.add(button);
            menuButtonCells.add(commands.add(button).growX().height(46).pad(4));
            commands.row();
        }
        if (windowsLayout) {
            commandsScroll = new ScrollPane(commands, new ScrollPane.ScrollPaneStyle());
            commandsScroll.setScrollingDisabled(true, false);
            commandsScroll.setOverscroll(false, false);
            commandsScroll.setFlickScroll(false);
            stage.addActor(commandsScroll);
        } else {
            commandsCell = root.add(commands).width(540);
            root.row();
        }

        // Compact evaluation/help strip; the original command stack remains intact.
        evaluationBar = new Table();
        evaluationBar.add(secondaryButton("REDESIGNED MENU [F8]", support::switchVariant)).pad(4);
        evaluationBar.add(secondaryButton("MANUAL [F1]", support::manual)).pad(4);
        evaluationBar.add(secondaryButton("CREDITS [F2]", support::credits)).pad(4);
        stage.addActor(evaluationBar);

        // Capture movement before child widgets so a mouse move always leaves keyboard mode.
        stage.addCaptureListener(new InputListener() {
            @Override public boolean mouseMoved(InputEvent event, float x, float y) {
                if (support.isModalOpen()) return false;
                enterCursorMode(event.getStageX(), event.getStageY());
                return false;
            }

            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (support.isModalOpen()) {
                    boolean handled = support.modalKey(keycode);
                    if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) event.cancel();
                    return handled;
                }
                if (keycode == Input.Keys.F8) { support.switchVariant(); return true; }
                if (keycode == Input.Keys.F1) { support.manual(); return true; }
                if (keycode == Input.Keys.F2) { support.credits(); return true; }
                if (keycode == Input.Keys.S) { support.showSettings(); return true; }
                if (activateShortcut(keycode)) return true;
                if (keycode == Input.Keys.UP) moveSelection(-1);
                else if (keycode == Input.Keys.DOWN || keycode == Input.Keys.TAB) moveSelection(1);
                else if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) activateSelection();
                else if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.Q) exitApplication();
                else return false;
                return true;
            }
        });

    }

    private void moveSelection(int direction) {
        enterKeyboardMode();
        if (selectedButtonIndex < 0) {
            selectKeyboardButton(direction > 0 ? 0 : menuButtons.size() - 1);
            return;
        }
        selectKeyboardButton((selectedButtonIndex + direction + menuButtons.size()) % menuButtons.size());
    }

    private void enterKeyboardMode() {
        if (navigationMode == NavigationMode.KEYBOARD) return;

        selectedButtonIndex = hoveredButtonIndex >= 0
            ? hoveredButtonIndex : lastHighlightedButtonIndex;
        hoveredButtonIndex = -1;
        navigationMode = NavigationMode.KEYBOARD;
        if (selectedButtonIndex >= 0) lastHighlightedButtonIndex = selectedButtonIndex;
        updateHighlights();
    }

    private void enterCursorMode(float stageX, float stageY) {
        navigationMode = NavigationMode.CURSOR;
        selectedButtonIndex = -1;
        hoveredButtonIndex = findButtonAt(stageX, stageY);
        if (hoveredButtonIndex >= 0) lastHighlightedButtonIndex = hoveredButtonIndex;
        updateHighlights();
    }

    private void selectKeyboardButton(int index) {
        boolean changed = selectedButtonIndex != index;
        selectedButtonIndex = index;
        lastHighlightedButtonIndex = index;
        updateHighlights();
        revealWindowsMenuButton(index);
        if (changed) game.audio().play(SoundCue.UI_NAVIGATE);
    }

    private void activateSelection() {
        if (navigationMode == NavigationMode.CURSOR) enterKeyboardMode();
        if (navigationMode == NavigationMode.KEYBOARD && selectedButtonIndex >= 0) {
            menuButtons.get(selectedButtonIndex).activate();
        }
    }

    private int findButtonAt(float stageX, float stageY) {
        Actor target = stage.hit(stageX, stageY, true);
        for (int i = 0; i < menuButtons.size(); i++) {
            MenuButton button = menuButtons.get(i);
            if (target == button || (target != null && target.isDescendantOf(button))) return i;
        }
        return -1;
    }

    private void updateHighlights() {
        int highlightedButtonIndex = switch (navigationMode) {
            case CURSOR -> hoveredButtonIndex;
            case KEYBOARD -> selectedButtonIndex;
            case NONE -> -1;
        };
        for (int i = 0; i < menuButtons.size(); i++) {
            menuButtons.get(i).setHighlighted(i == highlightedButtonIndex);
        }
    }

    private void resetNavigation() {
        navigationMode = NavigationMode.NONE;
        selectedButtonIndex = -1;
        hoveredButtonIndex = -1;
        lastHighlightedButtonIndex = -1;
        updateHighlights();
        if (windowsLayout && commandsScroll != null) {
            commandsScroll.setScrollPercentY(0f);
        }
    }

    private MenuButton makeButton(String label, Runnable onClick) {
        MenuButton b = new MenuButton(label, assets.editorSkin, () -> {
            if (!"QUIT".equals(label)) game.audio().play(SoundCue.UI_CONFIRM);
            onClick.run();
        });
        b.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { b.activate(); }
        });
        return b;
    }

    private TextButton secondaryButton(String label, Runnable action) {
        TextButton button = new TextButton(label, assets.editorSkin);
        button.setName(label);
        button.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) { action.run(); }
        });
        return button;
    }

    private boolean activateShortcut(int keycode) {
        int buttonIndex = switch (keycode) {
            case Input.Keys.NUM_1 -> 0;
            case Input.Keys.NUM_2 -> 1;
            case Input.Keys.NUM_3 -> 2;
            case Input.Keys.NUM_4 -> 3;
            case Input.Keys.NUM_5 -> 4;
            case Input.Keys.NUM_6 -> 5;
            case Input.Keys.NUM_7 -> 6;
            case Input.Keys.NUM_8 -> 7;
            case Input.Keys.NUM_9 -> 8;
            default -> -1;
        };
        if (buttonIndex < 0 || buttonIndex >= menuButtons.size()) return false;
        menuButtons.get(buttonIndex).activate();
        return true;
    }

    private void exitApplication() {
        support.exit();
    }

    private void layoutMenu(int width, int height) {
        evaluationBar.pack();
        evaluationBar.setPosition(width - evaluationBar.getWidth() - 28f,
            height - (windowsLayout ? 200f : 140f));
        float maxResponsiveScale = windowsLayout
            ? WINDOWS_MAX_RESPONSIVE_SCALE : MAC_MAX_RESPONSIVE_SCALE;
        float scale = Math.min(maxResponsiveScale, Math.max(0.80f,
            Math.min(width / 1024f, height / 600f)));
        if (windowsLayout) {
            layoutWindowsMenu(width, height, scale);
            return;
        }

        // Preserve the legacy stack while fitting its actual optional row count.
        scale = Math.min(scale, Math.max(.65f, (height - 240f) / (54f * menuButtons.size() + 32f)));

        float panelWidth = Math.min(width - 56f * scale, 540f * scale);
        root.pad(28f * scale);
        commands.pad(16f * scale);
        commandsCell.width(panelWidth);
        for (int i = 0; i < menuButtons.size(); i++) {
            // Editor fonts are oversampled (glyphs rendered FONT_OVERSAMPLE× too
            // large). Scene2D's setFontScale is absolute and overwrites the font's
            // base scale, so divide by FONT_OVERSAMPLE to keep on-screen size correct.
            menuButtons.get(i).getLabel().setFontScale(scale / AssetLoader.FONT_OVERSAMPLE);
            menuButtonCells.get(i).height(46f * scale).pad(4f * scale);
        }
        settingsButtonCell.size(46f * scale);
        root.invalidateHierarchy();
    }

    private void layoutWindowsMenu(int width, int height, float responsiveScale) {
        float scaledMenuHeight = authoringMenu
            ? WINDOWS_AUTHOR_MENU_SCALED_HEIGHT : WINDOWS_MENU_SCALED_HEIGHT;
        float referenceScale = windowsMenuReferenceScale(
            height, responsiveScale / WINDOWS_MAX_RESPONSIVE_SCALE, scaledMenuHeight);
        float menuHeight = WINDOWS_MENU_FIXED_HEIGHT + scaledMenuHeight * referenceScale;
        float menuY = windowsMenuY(height, menuHeight);
        float menuHalfWidth = windowsCommandViewportHalfWidth(width, referenceScale);
        float menuWidth = menuHalfWidth * 2f;
        float settingsSize = WINDOWS_SETTINGS_SIZE_PER_SCALE * responsiveScale;

        header.setBounds(
            WINDOWS_HEADER_X,
            height - WINDOWS_HEADER_TOP_FROM_TOP - WINDOWS_HEADER_HEIGHT,
            width - WINDOWS_HEADER_WIDTH_INSET,
            WINDOWS_HEADER_HEIGHT);
        settingsButton.setBounds(
            WINDOWS_SETTINGS_X,
            height - WINDOWS_SETTINGS_TOP_FROM_TOP
                - WINDOWS_SETTINGS_SIZE_PER_SCALE * responsiveScale,
            settingsSize,
            settingsSize);
        commandsScroll.setBounds(
            width * 0.5f - menuHalfWidth,
            menuY,
            menuWidth,
            menuHeight);
        commands.setExplicitPrefSize(menuWidth, menuHeight);
        title.setFontScale(WINDOWS_TITLE_FONT_SCALE);
        commandTitle.setFontScale(WINDOWS_TITLE_FONT_SCALE);
        commands.pad(WINDOWS_COMMAND_PADDING * referenceScale);
        for (int i = 0; i < menuButtons.size(); i++) {
            menuButtons.get(i).getLabel().setFontScale(
                WINDOWS_BUTTON_FONT_SCALE * referenceScale);
            menuButtonCells.get(i)
                .height(WINDOWS_BUTTON_HEIGHT * referenceScale)
                .pad(WINDOWS_BUTTON_PADDING * referenceScale);
        }
        header.invalidateHierarchy();
        commands.invalidateHierarchy();
        commandsScroll.invalidateHierarchy();
        commandsScroll.validate();
        if (selectedButtonIndex >= 0) {
            revealWindowsMenuButton(selectedButtonIndex);
        }
    }

    static float windowsMenuReferenceScale(
        float screenHeight,
        float requestedReferenceScale,
        float scaledMenuHeight
    ) {
        if (screenHeight <= 0f) return requestedReferenceScale;
        float availableHeight = Math.max(
            WINDOWS_MENU_FIXED_HEIGHT, screenHeight - WINDOWS_MENU_TOP_INSET);
        float heightLimitedScale = (availableHeight - WINDOWS_MENU_FIXED_HEIGHT)
            / scaledMenuHeight;
        return Math.min(requestedReferenceScale, heightLimitedScale);
    }

    static float windowsCommandViewportHalfWidth(float screenWidth, float referenceScale) {
        float availableHalfWidth = screenWidth * 0.5f
            - WINDOWS_MENU_SIDE_CLEARANCE_BASE
            - WINDOWS_MENU_SIDE_CLEARANCE_SCALED * referenceScale;
        return Math.min(WINDOWS_MENU_HALF_WIDTH * referenceScale,
            Math.max(WINDOWS_MENU_MIN_HALF_WIDTH, availableHalfWidth));
    }

    static float windowsMenuY(float screenHeight, float menuHeight) {
        return Math.max(0f,
            (screenHeight - WINDOWS_MENU_TOP_INSET - menuHeight) * 0.5f);
    }

    private void revealWindowsMenuButton(int index) {
        if (!windowsLayout || commandsScroll == null || index < 0
            || index >= menuButtons.size()) {
            return;
        }
        float responsiveScale = Math.min(1.75f, Math.max(0.80f,
            Math.min(stage.getWidth() / 1024f, stage.getHeight() / 600f)));
        float scaledMenuHeight = authoringMenu
            ? WINDOWS_AUTHOR_MENU_SCALED_HEIGHT : WINDOWS_MENU_SCALED_HEIGHT;
        float referenceScale = windowsMenuReferenceScale(
            stage.getHeight(),
            responsiveScale / WINDOWS_MAX_RESPONSIVE_SCALE,
            scaledMenuHeight);
        float rowHeight = (WINDOWS_BUTTON_HEIGHT + WINDOWS_BUTTON_PADDING * 2f)
            * referenceScale;
        float rowY = WINDOWS_COMMAND_PADDING * referenceScale
            + (menuButtons.size() - 1 - index) * rowHeight;
        commandsScroll.scrollTo(0f, rowY, 0f, rowHeight, false, true);
        commandsScroll.updateVisualScroll();
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void show() {
        stage.unfocusAll();
        resetNavigation();
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        // #CDDCFA — light blue, shared across all screens
        Gdx.gl.glClearColor(0.804f, 0.863f, 0.980f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) {
        if (windowsLayout && (width <= 0 || height <= 0)) return;
        stage.getViewport().update(width, height, true);
        layoutMenu(Math.round(stage.getWidth()), Math.round(stage.getHeight()));
        support.resize();
    }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   { support.close(); }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        support.dispose();
        stage.dispose();
    }

    /** Draws a hover state only when selected by the active input mode. */
    private static final class MenuButton extends TextButton {
        private final Runnable action;
        private boolean highlighted;

        private MenuButton(String text, com.badlogic.gdx.scenes.scene2d.ui.Skin skin, Runnable action) {
            super(text, skin, "primary");
            this.action = action;
        }

        private void activate() {
            setChecked(false);
            action.run();
        }

        private void setHighlighted(boolean highlighted) {
            this.highlighted = highlighted;
        }

        @Override
        public boolean isOver() {
            return highlighted;
        }
    }

    private static final class FixedSizeTable extends Table {
        private float explicitPrefWidth;
        private float explicitPrefHeight;

        private FixedSizeTable(Skin skin) {
            super(skin);
        }

        private void setExplicitPrefSize(float width, float height) {
            explicitPrefWidth = width;
            explicitPrefHeight = height;
            invalidateHierarchy();
        }

        @Override
        public float getPrefWidth() {
            return explicitPrefWidth > 0f ? explicitPrefWidth : super.getPrefWidth();
        }

        @Override
        public float getPrefHeight() {
            return explicitPrefHeight > 0f ? explicitPrefHeight : super.getPrefHeight();
        }
    }
}
