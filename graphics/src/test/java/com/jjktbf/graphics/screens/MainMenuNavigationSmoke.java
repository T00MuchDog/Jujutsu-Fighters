package com.jjktbf.graphics.screens;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Timer;
import com.jjktbf.graphics.JJKGame;
import com.jjktbf.graphics.launch.DesktopLaunchOptions;
import com.jjktbf.graphics.launch.DesktopPlatform;
import com.jjktbf.graphics.ui.menu.MainMenuAction;
import com.jjktbf.graphics.ui.menu.MainMenuVariant;
import com.jjktbf.graphics.ui.profile.UiProfile;
import com.jjktbf.graphics.screens.editors.AbilityEditorScreen;
import com.jjktbf.graphics.screens.editors.CharacterEditorScreen;
import com.jjktbf.graphics.screens.editors.CursedToolEditorScreen;
import com.jjktbf.graphics.screens.editors.DomainEditorScreen;
import com.jjktbf.graphics.screens.editors.MoveEditorScreen;
import com.jjktbf.graphics.screens.editors.TechniqueEditorScreen;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Opt-in, real-GL smoke test for main-menu navigation and lifecycle ownership.
 *
 * <p>Run this class directly (on macOS, with {@code -XstartOnFirstThread}). It
 * is deliberately not a JUnit test: constructing a {@link Lwjgl3Application}
 * is inappropriate during the normal test suite.</p>
 */
public final class MainMenuNavigationSmoke extends ApplicationAdapter {
    private static final String AUTHORING = "jjktbf.authoring";
    private static final String MENU_PROPERTY = "jjktbf.mainMenu";

    private Throwable failure;
    private RecordingApplication recordingApplication;

    private MainMenuNavigationSmoke() {}

    public static void main(String[] args) {
        // These must be set before JJKGame.create() can touch AppPaths.
        System.setProperty("jjktbf.data.root", freshSandbox().toString());
        System.setProperty(AUTHORING, "false");
        System.clearProperty(MENU_PROPERTY);

        Lwjgl3ApplicationConfiguration configuration =
            new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("JJK main-menu navigation smoke");
        configuration.setWindowedMode(960, 600);
        configuration.setHdpiMode(HdpiMode.Logical);
        configuration.disableAudio(true);

        MainMenuNavigationSmoke smoke = new MainMenuNavigationSmoke();
        new Lwjgl3Application(smoke, configuration);
        if (smoke.failure != null) {
            throw new AssertionError("Main-menu navigation smoke failed", smoke.failure);
        }
    }

    @Override
    public void create() {
        Application realApplication = Gdx.app;
        JJKGame game = null;
        recordingApplication = new RecordingApplication(realApplication);
        Gdx.app = recordingApplication.proxy();
        try {
            DesktopLaunchOptions options = new DesktopLaunchOptions(
                DesktopPlatform.MAC, UiProfile.MAC, true, 960, 600);

            game = new JJKGame(options);
            game.create();
            check(game.mainMenuVariant() == MainMenuVariant.REDESIGNED,
                "create() did not select the redesigned menu");
            check(game.getScreen() instanceof RedesignedMainMenuScreen,
                "create() did not show RedesignedMainMenuScreen");

            verifyDeferredVariantToggle(game);
            verifyRoutesAndReturn(game, MainMenuVariant.REDESIGNED);
            verifyRoutesAndReturn(game, MainMenuVariant.LEGACY);
            verifyAuthorRoute(game);
            ensureVariant(game, MainMenuVariant.REDESIGNED);
            verifyEscapeHoldExit(game);
            verifyExitTask(game);

            // The map-backed preference store is shared by game instances, so
            // this also verifies that toggleMainMenuVariant flushes the enum.
            ensureVariant(game, MainMenuVariant.LEGACY);
            check(menuPreferences().getString("variant", "")
                    .equals(MainMenuVariant.LEGACY.name()),
                "legacy variant was not saved");
            game.dispose();
            game = new JJKGame(options);
            game.create();
            check(game.mainMenuVariant() == MainMenuVariant.LEGACY,
                "a new JJKGame did not restore the saved menu variant");
            check(game.getScreen() instanceof MainMenuScreen,
                "a new JJKGame did not restore the legacy menu screen");

            verifyExitTask(game);
            System.out.println("PASS: real JJKGame main-menu navigation/lifecycle smoke");
        } catch (Throwable problem) {
            failure = problem;
            problem.printStackTrace();
        } finally {
            if (game != null) {
                try {
                    game.dispose();
                } catch (Throwable disposeFailure) {
                    if (failure == null) failure = disposeFailure;
                    else failure.addSuppressed(disposeFailure);
                }
            }
            Gdx.app = realApplication;
            realApplication.exit();
        }
    }

    private void verifyDeferredVariantToggle(JJKGame game) {
        ScreenSnapshot first = snapshot(game);
        Stage stage = (Stage) field(first.screen, "stage");
        check(stage.keyDown(Input.Keys.F8), "F8 was not consumed by the main menu");
        check(game.mainMenuVariant() == MainMenuVariant.REDESIGNED,
            "F8 changed the variant before the posted runnable was drained");
        check(game.getScreen() == first.screen,
            "F8 replaced the screen before the posted runnable was drained");
        check(recordingApplication.postedCount() == 1,
            "F8 did not post exactly one navigation runnable");

        recordingApplication.drainPosted();
        check(game.mainMenuVariant() == MainMenuVariant.LEGACY,
            "draining F8 did not select the legacy variant");
        check(game.getScreen() instanceof MainMenuScreen,
            "draining F8 did not create the legacy menu");
        check(disposed(first.screen), "the previous redesigned menu was not disposed");
        check(menuPreferences().getString("variant", "")
                .equals(MainMenuVariant.LEGACY.name()),
            "legacy variant was not persisted after F8");

        ScreenSnapshot second = snapshot(game);
        Stage legacyStage = (Stage) field(second.screen, "stage");
        check(legacyStage.keyDown(Input.Keys.F8), "legacy F8 was not consumed");
        check(game.mainMenuVariant() == MainMenuVariant.LEGACY,
            "legacy F8 changed the variant before draining");
        recordingApplication.drainPosted();
        check(game.mainMenuVariant() == MainMenuVariant.REDESIGNED,
            "second F8 did not return to the redesigned variant");
        check(game.getScreen() instanceof RedesignedMainMenuScreen,
            "second F8 did not recreate the redesigned menu");
        check(disposed(second.screen), "the previous legacy menu was not disposed");
    }

    private void verifyRoutesAndReturn(JJKGame game, MainMenuVariant variant) {
        ensureVariant(game, variant);
        for (MainMenuAction action : MainMenuAction.values()) {
            if (action == MainMenuAction.AUTHOR_BATTLE) continue;
            game.showMainMenu();
            check(game.mainMenuVariant() == variant,
                "showMainMenu changed variant before " + action);

            // MainMenuPreview exercises Scene2D clicks for both presentations;
            // this smoke intentionally focuses on real JJKGame route ownership.
            action.invoke(game);
            Class<?> expected = expectedDestination(action);
            check(expected.isInstance(game.getScreen()),
                action + " routed to " + game.getScreen().getClass().getName()
                    + ", expected " + expected.getName());
            game.render();

            game.showMainMenu();
            check(game.mainMenuVariant() == variant,
                "returning from " + action + " changed the menu variant");
            check(expectedMenu(variant).isInstance(game.getScreen()),
                "returning from " + action + " did not restore " + variant);
        }
    }

    private void verifyExitTask(JJKGame game) throws ReflectiveOperationException {
        recordingApplication.exitRequested = false;
        Object support = field(game.getScreen(), "support");
        Method exit = support.getClass().getDeclaredMethod("exit");
        exit.setAccessible(true);
        exit.invoke(support);
        Timer.Task task = (Timer.Task) field(support, "exitTask");
        check(task != null, "menu exit did not schedule a Timer.Task");
        task.run();
        check(recordingApplication.exitRequested,
            "running the menu exit Timer.Task did not call Application.exit");
        task.cancel();
    }

    private void verifyEscapeHoldExit(JJKGame game) throws ReflectiveOperationException {
        recordingApplication.exitRequested = false;
        RedesignedMainMenuScreen menu = (RedesignedMainMenuScreen) game.getScreen();
        Stage stage = (Stage) field(menu, "stage");
        check(stage.keyDown(Input.Keys.ESCAPE), "Escape hold was not consumed");
        Method update = menu.getClass().getDeclaredMethod("updateEscapeHold", float.class);
        update.setAccessible(true);
        update.invoke(menu, RedesignedMainMenuScreen.ESCAPE_HOLD_SECONDS - .01f);
        check(!recordingApplication.exitRequested, "Escape exited before the outline completed");
        update.invoke(menu, .01f);
        check(recordingApplication.exitRequested, "Escape did not exit when the outline completed");
        check((float) field(menu, "escapeHoldElapsed") == RedesignedMainMenuScreen.ESCAPE_HOLD_SECONDS,
            "Escape hold did not finish at the documented duration");
        stage.keyUp(Input.Keys.ESCAPE);
    }

    private void verifyAuthorRoute(JJKGame game) {
        // Only the format-screen handoff is entered under author permission.
        // No repository loads/saves or character selection happen in this block.
        try {
            System.setProperty(AUTHORING, "true");
            for (MainMenuVariant variant : MainMenuVariant.values()) {
                ensureVariant(game, variant);
                game.showMainMenu();
                check(game.isAuthorBattleAvailable(), "Author menu did not enable");
                MainMenuAction.AUTHOR_BATTLE.invoke(game);
                check(game.getScreen() instanceof BattleFormatScreen, "Author battle did not open format selection");
                game.render();
                System.setProperty("jjktbf.menu.authorBattle", "false");
                game.showMainMenu();
                check(!game.isAuthorBattleAvailable(), "Author menu hide flag did not disable the entry");
                System.clearProperty("jjktbf.menu.authorBattle");
            }
        } finally {
            System.setProperty(AUTHORING, "false");
            System.clearProperty("jjktbf.menu.authorBattle");
        }
    }

    private void ensureVariant(JJKGame game, MainMenuVariant variant) {
        if (game.mainMenuVariant() != variant) {
            game.toggleMainMenuVariant();
        }
        check(game.mainMenuVariant() == variant,
            "could not select menu variant " + variant);
    }

    private Preferences menuPreferences() {
        return recordingApplication.preferences("jjktbf-menu");
    }

    private static Class<?> expectedDestination(MainMenuAction action) {
        return switch (action) {
            case SINGLE_PLAYER -> com.jjktbf.graphics.screens.BattleFormatScreen.class;
            case MULTIPLAYER -> com.jjktbf.graphics.screens.MultiplayerMenuScreen.class;
            case CHARACTER_EDITOR -> CharacterEditorScreen.class;
            case MOVE_EDITOR -> MoveEditorScreen.class;
            case ABILITY_EDITOR -> AbilityEditorScreen.class;
            case TECHNIQUE_EDITOR -> TechniqueEditorScreen.class;
            case DOMAIN_EDITOR -> DomainEditorScreen.class;
            case CURSED_TOOL_EDITOR -> CursedToolEditorScreen.class;
            case AUTHOR_BATTLE -> throw new AssertionError("author battle is excluded");
        };
    }

    private static Class<?> expectedMenu(MainMenuVariant variant) {
        return variant == MainMenuVariant.LEGACY
            ? MainMenuScreen.class : RedesignedMainMenuScreen.class;
    }

    private static ScreenSnapshot snapshot(JJKGame game) {
        return new ScreenSnapshot(game.getScreen());
    }

    private static boolean disposed(Object screen) {
        return (boolean) field(screen, "disposed");
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not read " + name + " from "
                + owner.getClass().getName(), failure);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Path freshSandbox() {
        try {
            return Files.createTempDirectory(Path.of("graphics/target"), "menu-navigation-");
        } catch (IOException failure) {
            throw new IllegalStateException("Could not create smoke-test sandbox", failure);
        }
    }

    private record ScreenSnapshot(com.badlogic.gdx.Screen screen) {}

    /** Application proxy with deferred runnables and an in-memory preference namespace. */
    private static final class RecordingApplication implements InvocationHandler {
        private final Application delegate;
        private final Map<String, Preferences> preferences = new LinkedHashMap<>();
        private final List<Runnable> posted = new ArrayList<>();
        private final Application proxy;
        private boolean exitRequested;

        RecordingApplication(Application delegate) {
            this.delegate = Objects.requireNonNull(delegate);
            proxy = (Application) Proxy.newProxyInstance(
                Application.class.getClassLoader(), new Class<?>[] {Application.class}, this);
        }

        Application proxy() { return proxy; }

        Preferences preferences(String name) {
            return preferences.computeIfAbsent(name, ignored -> new MapPreferences());
        }

        int postedCount() { return posted.size(); }

        void drainPosted() {
            while (!posted.isEmpty()) {
                List<Runnable> batch = new ArrayList<>(posted);
                posted.clear();
                batch.forEach(Runnable::run);
            }
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getPreferences")) {
                return preferences((String) args[0]);
            }
            if (method.getName().equals("postRunnable")) {
                posted.add((Runnable) args[0]);
                return null;
            }
            if (method.getName().equals("exit")) {
                exitRequested = true;
                return null;
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }

    /** Minimal isolated Preferences implementation; flush intentionally does no I/O. */
    private static final class MapPreferences implements Preferences {
        private final Map<String, Object> values = new LinkedHashMap<>();

        @Override public Preferences putBoolean(String key, boolean value) { values.put(key, value); return this; }
        @Override public Preferences putInteger(String key, int value) { values.put(key, value); return this; }
        @Override public Preferences putLong(String key, long value) { values.put(key, value); return this; }
        @Override public Preferences putFloat(String key, float value) { values.put(key, value); return this; }
        @Override public Preferences putString(String key, String value) { values.put(key, value); return this; }
        @Override public Preferences put(Map<String, ?> values) { this.values.putAll(values); return this; }

        @Override public boolean getBoolean(String key) { return getBoolean(key, false); }
        @Override public int getInteger(String key) { return getInteger(key, 0); }
        @Override public long getLong(String key) { return getLong(key, 0L); }
        @Override public float getFloat(String key) { return getFloat(key, 0f); }
        @Override public String getString(String key) { return getString(key, null); }
        @Override public boolean getBoolean(String key, boolean defaultValue) {
            Object value = values.get(key); return value instanceof Boolean ? (Boolean) value : defaultValue;
        }
        @Override public int getInteger(String key, int defaultValue) {
            Object value = values.get(key); return value instanceof Number ? ((Number) value).intValue() : defaultValue;
        }
        @Override public long getLong(String key, long defaultValue) {
            Object value = values.get(key); return value instanceof Number ? ((Number) value).longValue() : defaultValue;
        }
        @Override public float getFloat(String key, float defaultValue) {
            Object value = values.get(key); return value instanceof Number ? ((Number) value).floatValue() : defaultValue;
        }
        @Override public String getString(String key, String defaultValue) {
            Object value = values.get(key); return value instanceof String ? (String) value : defaultValue;
        }
        @Override public Map<String, ?> get() { return Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public void clear() { values.clear(); }
        @Override public void remove(String key) { values.remove(key); }
        @Override public void flush() {}
    }
}
