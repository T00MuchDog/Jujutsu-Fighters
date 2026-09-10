package com.jjktbf.graphics.screens;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.SnapshotArray;
import com.jjktbf.graphics.AssetLoader;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.audio.GameAudio;
import com.jjktbf.graphics.launch.DesktopLaunchOptions;
import com.jjktbf.graphics.launch.DesktopPlatform;
import com.jjktbf.graphics.ui.menu.MainMenuAction;
import com.jjktbf.graphics.ui.menu.MenuTile;
import com.jjktbf.graphics.ui.profile.UiProfile;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in real-GL preview for both main-menu presentations. It deliberately
 * does not use {@link JJKGame#create()} or any repository: the fake game only
 * records navigation, while the real screen, Stage, assets and audio are used.
 *
 * Run with the graphics test classpath, for example:
 * {@code -Djjktbf.preview.hdpi=2 ... MainMenuPreview}.
 */
public final class MainMenuPreview extends ApplicationAdapter {
    private static final int[][] SIZES = {
        {2560, 1440}, {1920, 1080}, {1366, 768}, {1512, 982}
    };

    private final int hdpi = Integer.getInteger("jjktbf.preview.hdpi", 1);
    private Throwable failure;

    private MainMenuPreview() { }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Main menu preview");
        configuration.setWindowedMode(960, 600);
        configuration.setHdpiMode(HdpiMode.Logical);
        configuration.disableAudio(true);

        MainMenuPreview preview = new MainMenuPreview();
        new Lwjgl3Application(preview, configuration);
        if (preview.failure != null) {
            throw new AssertionError("Main-menu preview failed", preview.failure);
        }
    }

    @Override public void create() {
        Graphics realGraphics = Gdx.graphics;
        Application realApplication = Gdx.app;
        try {
            for (UiProfile profile : UiProfile.values()) {
                if (hdpi > 1 && profile != UiProfile.MAC) continue;
                AssetLoader assets = new AssetLoader(profile);
                GameAudio audio = null;
                try {
                    assets.load();
                    audio = new GameAudio();
                    for (boolean authorAvailable : new boolean[] {false, true}) {
                        runMatrix(realGraphics, realApplication, assets, audio,
                            profile, authorAvailable);
                    }
                } finally {
                    if (audio != null) audio.dispose();
                    assets.dispose();
                }
            }
            System.out.println("PASS: main-menu GL matrix captured at all requested sizes; HDPI=" + hdpi);
        } catch (Throwable problem) {
            failure = problem;
            problem.printStackTrace();
        } finally {
            Gdx.graphics = realGraphics;
            Gdx.app = realApplication;
            Gdx.app.exit();
        }
    }

    private void runMatrix(
        Graphics realGraphics, Application realApplication, AssetLoader assets,
        GameAudio audio, UiProfile profile, boolean authorAvailable
    ) {
        RecordingGame game = new RecordingGame(profile, authorAvailable, audio);
        for (int[] size : SIZES) {
            if (hdpi > 1 && size[0] != 1512) continue;
            int width = size[0], height = size[1];
            Gdx.graphics = dimensions(realGraphics, width, height, hdpi);

            Screen redesigned = new RedesignedMainMenuScreen(game, assets);
            Screen legacy = new MainMenuScreen(game, assets);
            try {
                redesigned.show();
                redesigned.resize(width, height);
                capture(redesigned, profile, authorAvailable, width, height, "redesigned-modes");
                validateRedesigned((RedesignedMainMenuScreen) redesigned, width, height);
                exerciseRedesigned((RedesignedMainMenuScreen) redesigned, game,
                    profile, authorAvailable, width, height);

                legacy.show();
                legacy.resize(width, height);
                capture(legacy, profile, authorAvailable, width, height, "legacy");
                validateLegacy((MainMenuScreen) legacy, width, height);
                exerciseLegacy((MainMenuScreen) legacy, game,
                    profile, authorAvailable, width, height);
                verifyDeferredF8(realApplication, stage(legacy), game);
            } finally {
                redesigned.dispose();
                legacy.dispose();
            }
        }
    }

    private void exerciseRedesigned(
        RedesignedMainMenuScreen screen, RecordingGame game, UiProfile profile,
        boolean authorAvailable, int width, int height
    ) {
        Stage stage = stage(screen);
        List<Actor> controls = actors(screen, "controls");
        for (MainMenuAction action : MainMenuAction.modes(authorAvailable)) {
            clickNamed(stage, controls, action.title, game, action);
        }

        Actor editorsTab = named(controls, "EDITORS");
        movePointer(stage, editorsTab);
        clickNamed(stage, controls, "EDITORS", game, null);
        check("EDITORS".equals(focused(screen).getName()),
            "Selecting Editors did not retain focus in the top tier");
        capture(screen, profile, authorAvailable, width, height, "editors-grid");
        validateRedesigned(screen, width, height);
        verifyTierNavigation(screen, stage);
        controls = actors(screen, "controls");
        for (MainMenuAction action : MainMenuAction.editors()) {
            clickNamed(stage, controls, action.title, game, action);
        }

        Actor modesTab = named(controls, "GAME MODES");
        movePointer(stage, modesTab);
        clickNamed(stage, controls, "GAME MODES", game, null);
        check("GAME MODES".equals(focused(screen).getName()),
            "Selecting Game Modes did not retain focus in the top tier");
        controls = actors(screen, "controls");
        verifyTierNavigation(screen, stage);
        Actor first = named(controls, MainMenuAction.modes(authorAvailable).get(0).title);
        screen.show();
        stage.keyDown(Input.Keys.DOWN);
        capture(screen, profile, authorAvailable, width, height, "redesigned-focus");
        game.routes.clear();
        check(stage.keyDown(Input.Keys.ENTER), "Redesigned focus did not consume Enter");
        check(game.routes.equals(List.of(MainMenuAction.modes(authorAvailable).get(0).name())),
            "Redesigned Enter activated the wrong route: " + game.routes);
        movePointer(stage, first);
        capture(screen, profile, authorAvailable, width, height, "redesigned-hover");
        press(stage, first);
        capture(screen, profile, authorAvailable, width, height, "redesigned-pressed");
        release(stage, first);

        ((Button) first).setDisabled(true);
        game.routes.clear();
        press(stage, first);
        release(stage, first);
        check(game.routes.isEmpty(), "Disabled redesigned card invoked a route");
        capture(screen, profile, authorAvailable, width, height, "redesigned-disabled");
        ((Button) first).setDisabled(false);

        stage.keyDown(Input.Keys.ESCAPE);
        invoke(screen, "updateEscapeHold", new Class<?>[] {float.class},
            RedesignedMainMenuScreen.ESCAPE_HOLD_SECONDS / 2f);
        capture(screen, profile, authorAvailable, width, height, "redesigned-exit-hold");
        stage.keyUp(Input.Keys.ESCAPE);
        MenuTile exit = (MenuTile) field(screen, "exitTile");
        check(exit.holdOutlineProgress() == 0f, "Releasing Escape did not clear exit progress");

        clickNamed(stage, controls, "SETTINGS", game, null);
        check(modalOpen(screen), "Redesigned settings did not open");
        verifySettingsKeyboard(screen, game);
        capture(screen, profile, authorAvailable, width, height, "redesigned-settings");
        closeModal(stage, screen);
        verifyDeferredF8(Gdx.app, stage, game);
        stage.keyDown(Input.Keys.F1);
        check(modalOpen(screen), "Redesigned manual did not open");
        capture(screen, profile, authorAvailable, width, height, "redesigned-manual");
        verifyOpenModalResize(screen, width, height);
        closeModal(stage, screen);
        stage.keyDown(Input.Keys.F2);
        check(modalOpen(screen), "Redesigned credits did not open");
        capture(screen, profile, authorAvailable, width, height, "redesigned-credits");
        closeModal(stage, screen);
    }

    private static void verifyTierNavigation(RedesignedMainMenuScreen screen, Stage stage) {
        List<Actor> top = actors(screen, "topControls");
        List<Actor> page = actors(screen, "pageControls");
        List<Actor> bottom = actors(screen, "bottomControls");

        screen.show();
        stage.keyDown(Input.Keys.UP);
        Actor focused = focused(screen);
        check(focused == top.get(0), "Up from no focus did not enter top at its leftmost option");
        stage.keyDown(Input.Keys.UP);
        check(focused(screen) == focused, "Up escaped the top tier");

        stage.keyDown(Input.Keys.DOWN);
        check(focused(screen) == page.get(0), "Down from top did not enter page at its leftmost option");
        Actor pageStart = focused(screen);
        stage.keyDown(Input.Keys.RIGHT);
        check(page.contains(focused(screen)), "Right escaped the page tier");
        stage.keyDown(Input.Keys.UP);
        check(focused(screen) == top.get(0), "Up from page did not enter top at its leftmost option");

        screen.show();
        stage.keyDown(Input.Keys.DOWN);
        int columns = (int) field(screen, "pageColumns");
        int rows = (page.size() + columns - 1) / columns;
        for (int row = 1; row < rows; row++) {
            stage.keyDown(Input.Keys.DOWN);
            check(page.contains(focused(screen)), "Down left page tier before its bottom row");
        }
        stage.keyDown(Input.Keys.DOWN);
        check(focused(screen) == bottom.get(0), "Down from page did not enter bottom at its leftmost option");
        Actor bottomFocus = focused(screen);
        stage.keyDown(Input.Keys.DOWN);
        check(focused(screen) == bottomFocus, "Down escaped the bottom tier");
        stage.keyDown(Input.Keys.LEFT);
        check(bottom.contains(focused(screen)), "Left escaped the bottom tier");
        stage.keyDown(Input.Keys.UP);
        int lastRowStart = (page.size() - 1) / columns * columns;
        check(focused(screen) == page.get(lastRowStart),
            "Up from bottom did not enter the last page row at its leftmost option");

        Actor credits = named(bottom, "CREDITS");
        movePointer(stage, credits);
        check(focused(screen) == credits, "Mouse movement did not transfer focus to hovered control");
        check(pageStart != null, "Page tier has no controls");
    }

    private static Actor focused(RedesignedMainMenuScreen screen) {
        int index = (int) field(screen, "focused");
        List<Actor> controls = actors(screen, "controls");
        return index < 0 ? null : controls.get(index);
    }

    private void exerciseLegacy(
        MainMenuScreen screen, RecordingGame game, UiProfile profile,
        boolean authorAvailable, int width, int height
    ) {
        Stage stage = stage(screen);
        List<Actor> buttons = actors(screen, "menuButtons");
        List<MainMenuAction> actions = MainMenuAction.available(authorAvailable);
        ScrollPane scroll = (ScrollPane) field(screen, "commandsScroll");
        for (int i = 0; i < actions.size(); i++) {
            if (scroll != null) {
                Actor button = buttons.get(i);
                scroll.scrollTo(button.getX(), button.getY(),
                    button.getWidth(), button.getHeight(), false, true);
                scroll.updateVisualScroll();
            }
            click(stage, buttons.get(i), game, actions.get(i));
        }

        if (scroll != null) {
            scroll.setScrollPercentY(0f);
            scroll.updateVisualScroll();
        }
        Actor first = buttons.get(0);
        screen.show();
        stage.keyDown(Input.Keys.TAB);
        capture(screen, profile, authorAvailable, width, height, "legacy-focus");
        game.routes.clear();
        check(stage.keyDown(Input.Keys.ENTER), "Legacy focus did not consume Enter");
        check(game.routes.equals(List.of(actions.get(0).name())),
            "Legacy Enter activated the wrong route: " + game.routes);
        movePointer(stage, first);
        capture(screen, profile, authorAvailable, width, height, "legacy-hover");
        press(stage, first);
        capture(screen, profile, authorAvailable, width, height, "legacy-pressed");
        release(stage, first);

        stage.keyDown(Input.Keys.F1);
        check(modalOpen(screen), "Legacy manual did not open");
        capture(screen, profile, authorAvailable, width, height, "legacy-manual");
        closeModal(stage, screen);
        stage.keyDown(Input.Keys.F2);
        check(modalOpen(screen), "Legacy credits did not open");
        capture(screen, profile, authorAvailable, width, height, "legacy-credits");
        closeModal(stage, screen);
        Actor settings = (Actor) field(screen, "settingsButton");
        click(stage, settings, game, null);
        check(modalOpen(screen), "Legacy settings did not open");
        verifySettingsKeyboard(screen, game);
        capture(screen, profile, authorAvailable, width, height, "legacy-settings");
        closeModal(stage, screen);
    }

    private static void verifySettingsKeyboard(Screen screen, RecordingGame game) {
        MainMenuSupport support = (MainMenuSupport) field(screen, "support");
        Actor dialog = (Actor) field(support.settings, "dialog");
        TextField volume = firstTextField(dialog);
        check(volume != null, "Settings has no editable volume field");
        Stage stage = stage(screen);
        stage.setKeyboardFocus(volume);
        volume.setText("61");
        stage.keyDown(Input.Keys.ENTER);
        check(Math.round(game.audio().settings().musicVolume() * 100) == 61,
            "Settings volume Enter did not reach the focused text field");
        game.routes.clear();
        stage.keyDown(Input.Keys.NUM_1);
        check(game.routes.isEmpty(), "Settings leaked shortcut to the menu");
    }

    private static TextField firstTextField(Actor actor) {
        if (actor instanceof TextField field) return field;
        for (Actor child : children(actor)) {
            TextField result = firstTextField(child);
            if (result != null) return result;
        }
        return null;
    }

    private void verifyOpenModalResize(Screen screen, int width, int height) {
        if (width != 2560) return;
        Graphics previous = Gdx.graphics;
        try {
            Gdx.graphics = dimensions(previous, 1366, 768, hdpi);
            screen.resize(1366, 768);
            stage(screen).act(0f);
            Object support = field(screen, "support");
            Actor dialog = (Actor) field(support, "information");
            guard(stage(screen), dialog, null);
            guard(stage(screen), (Actor) field(support, "informationClose"), null);
        } finally {
            Gdx.graphics = previous;
            screen.resize(width, height);
        }
    }

    private static void verifyDeferredF8(
        Application realApplication, Stage stage, RecordingGame game
    ) {
        game.routes.clear();
        Application immediatePost = (Application) Proxy.newProxyInstance(
            Application.class.getClassLoader(), new Class<?>[] {Application.class},
            (proxy, method, args) -> {
                if (method.getName().equals("postRunnable")) {
                    ((Runnable) args[0]).run();
                    return null;
                }
                return method.invoke(realApplication, args);
            });
        Gdx.app = immediatePost;
        try {
            stage.keyDown(Input.Keys.F8);
        } finally {
            Gdx.app = realApplication;
        }
        check(game.routes.contains("toggleMainMenuVariant"),
            "F8 did not use deferred toggleMainMenuVariant route");
    }

    private static void clickNamed(
        Stage stage, List<Actor> actors, String name, RecordingGame game, MainMenuAction expected
    ) {
        Actor actor = named(actors, name);
        click(stage, actor, game, expected);
    }

    private static void click(Stage stage, Actor actor, RecordingGame game, MainMenuAction expected) {
        game.routes.clear();
        press(stage, actor);
        release(stage, actor);
        if (expected != null) {
            check(game.routes.equals(List.of(expected.name())),
                "Wrong route for " + expected + ": " + game.routes);
        }
    }

    private static void press(Stage stage, Actor actor) {
        Vector2 point = center(stage, actor);
        check(stage.touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT),
            "Stage touchDown missed " + actor.getName());
    }

    private static void release(Stage stage, Actor actor) {
        Vector2 point = center(stage, actor);
        check(stage.touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT),
            "Stage touchUp missed " + actor.getName());
    }

    private static void movePointer(Stage stage, Actor actor) {
        Vector2 point = center(stage, actor);
        stage.mouseMoved(Math.round(point.x), Math.round(point.y));
        stage.act(0f);
    }

    private static Vector2 center(Stage stage, Actor actor) {
        Vector2 stagePoint = actor.localToStageCoordinates(
            new Vector2(actor.getWidth() / 2f, actor.getHeight() / 2f));
        Vector2 screenPoint = stage.getViewport().project(stagePoint);
        screenPoint.y = Gdx.graphics.getHeight() - screenPoint.y;
        return screenPoint;
    }

    private static void closeModal(Stage stage, Screen screen) {
        check(stage.keyDown(Input.Keys.ESCAPE), "Modal did not consume Escape");
        check(!modalOpen(screen), "Modal remained open after Escape");
    }

    private static void validateRedesigned(RedesignedMainMenuScreen screen, int width, int height) {
        Stage stage = stage(screen);
        List<Rectangle> bounds = new ArrayList<>();
        for (Actor actor : actors(screen, "controls")) bounds.add(guard(stage, actor, null));
        checkNonoverlap(bounds, "redesigned controls");
        checkLabels(stage.getRoot(), stage.getWidth(), stage.getHeight());
    }

    private static void validateLegacy(MainMenuScreen screen, int width, int height) {
        Stage stage = stage(screen);
        List<Rectangle> bounds = new ArrayList<>();
        ScrollPane viewport = (ScrollPane) field(screen, "commandsScroll");
        Rectangle clip = viewport == null ? null : bounds(stage, viewport);
        for (Actor actor : actors(screen, "menuButtons")) {
            Rectangle rect = bounds(stage, actor);
            if (clip != null && !rect.overlaps(clip)) continue;
            bounds.add(guard(stage, actor, clip));
        }
        bounds.add(guard(stage, (Actor) field(screen, "settingsButton"), null));
        Actor evaluation = (Actor) field(screen, "evaluationBar");
        for (Actor child : children(evaluation)) bounds.add(guard(stage, child, null));
        checkNonoverlap(bounds, "legacy controls");
        checkLabels(stage.getRoot(), stage.getWidth(), stage.getHeight());
    }

    private static Rectangle guard(Stage stage, Actor actor, Rectangle clip) {
        Rectangle rect = bounds(stage, actor);
        if (clip != null) rect = intersection(rect, clip);
        Rectangle screen = new Rectangle(0, 0, stage.getWidth(), stage.getHeight());
        check(screen.contains(rect.x, rect.y)
            && screen.contains(rect.x + rect.width, rect.y + rect.height),
            "Visible control outside stage: " + actor.getName() + " " + rect);
        check(rect.width > 0f && rect.height > 0f, "Empty visible control: " + actor.getName());
        return rect;
    }

    private static Rectangle intersection(Rectangle left, Rectangle right) {
        float x = Math.max(left.x, right.x);
        float y = Math.max(left.y, right.y);
        float maxX = Math.min(left.x + left.width, right.x + right.width);
        float maxY = Math.min(left.y + left.height, right.y + right.height);
        return new Rectangle(x, y, Math.max(0f, maxX - x), Math.max(0f, maxY - y));
    }

    private static void checkNonoverlap(List<Rectangle> bounds, String label) {
        for (int i = 0; i < bounds.size(); i++) {
            for (int j = i + 1; j < bounds.size(); j++) {
                check(!bounds.get(i).overlaps(bounds.get(j)),
                    label + " overlap: " + bounds.get(i) + " / " + bounds.get(j));
            }
        }
    }

    private static void checkLabels(Actor actor, float width, float height) {
        if (actor instanceof Label label && actor.getWidth() > 0f && actor.getHeight() > 0f) {
            label.validate();
            Rectangle rect = bounds(null, actor);
            check(rect.x + rect.width >= -1f && rect.y + rect.height >= -1f
                && rect.x <= width + 1f && rect.y <= height + 1f,
                 "Label outside preview: " + ((Label) actor).getText());
            check(label.getGlyphLayout().width <= label.getWidth() + 2f,
                "Text exceeds label width: " + label.getText());
            check(label.getGlyphLayout().height <= label.getHeight() + 2f,
                "Text exceeds label height: " + label.getText());
        }
        for (Actor child : children(actor)) checkLabels(child, width, height);
    }

    private static Rectangle bounds(Stage stage, Actor actor) {
        Vector2 a = actor.localToStageCoordinates(new Vector2(0, 0));
        Vector2 b = actor.localToStageCoordinates(new Vector2(actor.getWidth(), actor.getHeight()));
        return new Rectangle(Math.min(a.x, b.x), Math.min(a.y, b.y),
            Math.abs(b.x - a.x), Math.abs(b.y - a.y));
    }

    private static List<Actor> children(Actor actor) {
        List<Actor> result = new ArrayList<>();
        if (actor instanceof Group group) {
            SnapshotArray<Actor> children = group.getChildren();
            for (Actor child : children) result.add(child);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Actor> actors(Object owner, String name) {
        return (List<Actor>) field(owner, name);
    }

    private static Actor named(List<Actor> actors, String name) {
        for (Actor actor : actors) if (name.equals(actor.getName())) return actor;
        throw new AssertionError("No menu control named " + name);
    }

    private static Stage stage(Object screen) { return (Stage) field(screen, "stage"); }

    private static boolean modalOpen(Object screen) {
        Object support = field(screen, "support");
        try {
            var method = support.getClass().getDeclaredMethod("isModalOpen");
            method.setAccessible(true);
            return (boolean) method.invoke(support);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Object invoke(Object owner, String name, Class<?>[] parameterTypes, Object... args) {
        try {
            var method = owner.getClass().getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(owner, args);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private void capture(Screen screen, UiProfile profile, boolean authorAvailable,
                         int width, int height, String name) {
        int pixelWidth = width * hdpi, pixelHeight = height * hdpi;
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, pixelWidth, pixelHeight, false);
        try {
            buffer.begin();
            for (int i = 0; i < 35; i++) screen.render(1f / 60f);
            check(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error while capturing " + name);
            Pixmap image = Pixmap.createFromFrameBuffer(0, 0, pixelWidth, pixelHeight);
            try {
                String variant = profile.fileStem() + (authorAvailable ? "-author" : "-standard")
                    + (hdpi > 1 ? "-hdpi" + hdpi : "");
                String root = System.getProperty("jjktbf.preview.output",
                    "graphics/target/main-menu-preview");
                var directory = Gdx.files.local(root + "/" + variant);
                directory.mkdirs();
                String filename = width + "x" + height + "-" + name + ".png";
                PixmapIO.writePNG(directory.child(filename),
                    image, -1, true);
                String gallery = System.getProperty("jjktbf.preview.gallery");
                boolean representative = hdpi == 1 && (
                    profile == UiProfile.WINDOWS && name.equals("redesigned-modes")
                        && (!authorAvailable && (width == 2560 || width == 1920) || authorAvailable && width == 1366)
                    || profile == UiProfile.MAC && !authorAvailable && width == 1512
                        && (name.equals("redesigned-modes") || name.equals("editors-grid"))
                    || profile == UiProfile.WINDOWS && !authorAvailable && width == 1366 && name.equals("editors-grid"));
                if (gallery != null && representative) {
                    var galleryDir = Gdx.files.local(gallery);
                    galleryDir.mkdirs();
                    PixmapIO.writePNG(galleryDir.child(variant + "-" + filename), image, -1, true);
                }
            } finally {
                image.dispose();
            }
        } finally {
            buffer.end();
            buffer.dispose();
        }
    }

    private static Graphics dimensions(Graphics real, int width, int height, int hdpi) {
        return (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
            new Class<?>[] {Graphics.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getWidth" -> width;
                case "getHeight" -> height;
                case "getBackBufferWidth" -> width * hdpi;
                case "getBackBufferHeight" -> height * hdpi;
                case "getDeltaTime", "getRawDeltaTime" -> 0f;
                default -> method.invoke(real, args);
            });
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class RecordingGame extends JJKGame {
        private final UiProfile profile;
        private final boolean authorAvailable;
        private final GameAudio suppliedAudio;
        private final List<String> routes = new ArrayList<>();

        RecordingGame(UiProfile profile, boolean authorAvailable, GameAudio audio) {
            super(new DesktopLaunchOptions(
                profile == UiProfile.WINDOWS ? DesktopPlatform.WINDOWS : DesktopPlatform.OTHER,
                profile, true, profile.defaultReferenceWidth(), profile.defaultReferenceHeight()));
            this.profile = profile;
            this.authorAvailable = authorAvailable;
            this.suppliedAudio = audio;
        }

        @Override public UiProfile activeUiProfile() { return profile; }
        @Override public boolean isAuthorBattleAvailable() { return authorAvailable; }
        @Override public GameAudio audio() { return suppliedAudio; }
        @Override public void showMainMenu() { routes.add("showMainMenu"); }
        @Override public void toggleMainMenuVariant() { routes.add("toggleMainMenuVariant"); }
        @Override public void showSinglePlayerBattle() { routes.add(MainMenuAction.SINGLE_PLAYER.name()); }
        @Override public void showMultiplayerMenu() { routes.add(MainMenuAction.MULTIPLAYER.name()); }
        @Override public void showAuthorBattle() { routes.add(MainMenuAction.AUTHOR_BATTLE.name()); }
        @Override public void showCharacterEditor() { routes.add(MainMenuAction.CHARACTER_EDITOR.name()); }
        @Override public void showMoveEditor() { routes.add(MainMenuAction.MOVE_EDITOR.name()); }
        @Override public void showAbilityEditor() { routes.add(MainMenuAction.ABILITY_EDITOR.name()); }
        @Override public void showTechniqueEditor() { routes.add(MainMenuAction.TECHNIQUE_EDITOR.name()); }
        @Override public void showDomainEditor() { routes.add(MainMenuAction.DOMAIN_EDITOR.name()); }
        @Override public void showCursedToolEditor() { routes.add(MainMenuAction.CURSED_TOOL_EDITOR.name()); }
    }
}
