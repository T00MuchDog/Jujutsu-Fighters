package com.jjktbf.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.jjktbf.AppPaths;
import com.jjktbf.graphics.display.DisplaySettingsStore;
import com.jjktbf.graphics.display.WindowsDisplayEnvironment;
import com.jjktbf.graphics.display.WindowsResolution;
import com.jjktbf.graphics.launch.DesktopLaunchOptions;
import com.jjktbf.graphics.launch.DesktopPlatform;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.SharedLibrary;

import java.io.IOException;
import java.nio.IntBuffer;

import static org.lwjgl.system.APIUtil.apiGetFunctionAddress;
import static org.lwjgl.system.MemoryUtil.memAddress;

/**
 * Desktop entry point for the graphics mode.
 *
 * Configures the LibGDX LWJGL3 window and launches JJKGame.
 *
 * Build from the repository root with:
 *   mvn -Drevision=1.4.1 -pl graphics -am package
 *
 * See README.md for profile-specific launch commands on macOS and Windows.
 * UI profile overrides are parsed centrally by DesktopLaunchOptions.
 */
public class GraphicsMain {

    public static void main(String[] args) {
        DesktopLaunchOptions launchOptions;
        try {
            launchOptions = DesktopLaunchOptions.parse(args);
        } catch (IllegalArgumentException invalidOptions) {
            System.err.println("Could not launch: " + invalidOptions.getMessage());
            return;
        }

        // First-run / upgrade-safe seeding: copy bundled game-data JSON into the
        // per-user data directory. Editor data persists for a game version and
        // is replaced only after launching a newer release.
        // Must run before any repository is constructed (those read the files).
        try {
            AppPaths.seedDataIfAbsent();
        } catch (Throwable t) {
            // Seeding failure is non-fatal: repositories fall back to their
            // built-in seeds. Log so it is diagnosable.
            System.err.println("Warning: could not seed user data dir: " + t);
        }

        // Capture any thread's uncaught exception to a file so crashes (which
        // often die silently or show a native dialog that hides the trace) are
        // recoverable. Written to the per-user logs dir so it is reachable from
        // a packaged app regardless of working directory.
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.PrintWriter pw = new java.io.PrintWriter(
                    new java.io.FileWriter(AppPaths.logFile().toFile(), true));
                pw.println("===== " + java.time.Instant.now()
                           + "  (thread: " + t.getName() + ") =====");
                e.printStackTrace(pw);
                pw.close();
            } catch (Exception ignored) {}
        });

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();

        config.setTitle(AppPaths.APP_NAME);
        // Layout and input stay in GLFW logical window coordinates. LibGDX maps
        // OpenGL viewports/scissors to the physical framebuffer exactly once.
        config.setHdpiMode(HdpiMode.Logical);
        // Configure the host display policy. The mechanism differs by OS:
        //   - macOS: the "green traffic-light" fullscreen is a distinct native
        //     API (NSWindow -toggleFullScreen:). GLFW's exclusive fullscreen
        //     does NOT produce it — it just stretches a borderless window over
        //     the desktop. So on macOS we start as a normal decorated window
        //     and invoke the native toggle once the window exists. This is
        //     exactly what pressing the green button does: the app gets its
        //     own Space.
        //   - Windows: use a centered borderless window at the selected rendering
        //     resolution so focus changes do not trigger an exclusive-mode transition.
        //   - Linux: use standard exclusive fullscreen.
        boolean mac = launchOptions.hostPlatform() == DesktopPlatform.MAC;
        boolean windows = launchOptions.hostPlatform() == DesktopPlatform.WINDOWS;
        boolean macNativeFullscreen = mac && !launchOptions.windowed();
        DisplaySettingsStore displaySettingsStore = windows
            ? new DisplaySettingsStore() : null;
        WindowsResolution selectedResolution = null;
        Graphics.Monitor windowsMonitor = null;
        Graphics.DisplayMode windowsDisplayMode = null;
        if (windows) {
            windowsMonitor = Lwjgl3ApplicationConfiguration.getPrimaryMonitor();
            windowsDisplayMode = Lwjgl3ApplicationConfiguration.getDisplayMode(windowsMonitor);
            WindowsDisplayEnvironment display = WindowsDisplayEnvironment.detect(
                windowsMonitor, windowsDisplayMode);
            selectedResolution = resolveWindowsResolution(
                displaySettingsStore, display, launchOptions);
            System.out.println("Windows display: " + display.pixelWidth() + "x"
                + display.pixelHeight() + " at " + display.scalePercent()
                + "% scaling; UI resolution "
                + (selectedResolution == null
                    ? launchOptions.windowWidth() + "x" + launchOptions.windowHeight()
                    : selectedResolution.id()));
        }
        if (launchOptions.windowed()) {
            config.setWindowedMode(launchOptions.windowWidth(), launchOptions.windowHeight());
        } else if (windows) {
            config.setWindowedMode(selectedResolution.width(), selectedResolution.height());
            config.setWindowPosition(
                windowsMonitor.virtualX
                    + (windowsDisplayMode.width - selectedResolution.width()) / 2,
                windowsMonitor.virtualY
                    + (windowsDisplayMode.height - selectedResolution.height()) / 2);
            config.setDecorated(false);
        } else if (!mac) {
            config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        } else {
            // Start windowed; the green-button toggle needs a real window.
            config.setWindowedMode(1024, 600);
        }
        // Cocoa native fullscreen requires a resizable NSWindow. Keep the old
        // normal-Mac behavior while still allowing explicit fixed policies later.
        config.setResizable(macNativeFullscreen || launchOptions.windowed());
        if (windows && !launchOptions.windowed()) {
            config.setWindowListener(new Lwjgl3WindowAdapter() {
                @Override public void created(Lwjgl3Window window) {
                    disableWindowsWindowTransitions(window.getWindowHandle());
                }
            });
        }
        config.setForegroundFPS(60);
        config.useVsync(true);

        JJKGame game = new JJKGame(
            launchOptions, displaySettingsStore, selectedResolution);
        if (macNativeFullscreen) {
            // toggleFullScreen: must be called on the UI/render thread AFTER
            // the GLFW window exists. Hooking the end of create() and posting
            // a runnable defers the call to the next render frame, by which
            // point the window + GL context are live.
            game.setOnCreatedAction(() -> Gdx.app.postRunnable(
                GraphicsMain::enterMacNativeFullscreen));
        }

        // The launching JVM still requires -XstartOnFirstThread on macOS.
        new Lwjgl3Application(game, config);
    }

    private static WindowsResolution resolveWindowsResolution(
        DisplaySettingsStore store,
        WindowsDisplayEnvironment display,
        DesktopLaunchOptions launchOptions
    ) {
        if (launchOptions.windowed()) {
            return WindowsResolution.exact(
                launchOptions.windowWidth(), launchOptions.windowHeight()).orElse(null);
        }

        WindowsResolution resolution = null;
        try {
            resolution = store.load().orElse(null);
        } catch (IOException failure) {
            System.err.println("Warning: could not load display settings: "
                + failure.getMessage());
        }
        if (resolution == null || !resolution.fits(display.pixelWidth(), display.pixelHeight())) {
            resolution = WindowsResolution.bestFor(display.pixelWidth(), display.pixelHeight());
            try {
                store.save(resolution);
            } catch (IOException failure) {
                System.err.println("Warning: could not save automatic display settings: "
                    + failure.getMessage());
            }
        }
        return resolution;
    }

    private static void disableWindowsWindowTransitions(long glfwWindowHandle) {
        final int dwmTransitionsForceDisabled = 3;
        try (SharedLibrary dwmApi = Library.loadNative(
                 GraphicsMain.class, "org.lwjgl", "dwmapi");
             MemoryStack stack = MemoryStack.stackPush()) {
            long windowHandle = GLFWNativeWin32.glfwGetWin32Window(glfwWindowHandle);
            long setWindowAttribute = apiGetFunctionAddress(
                dwmApi, "DwmSetWindowAttribute");
            IntBuffer disabled = stack.ints(1);
            int result = JNI.invokePPI(
                windowHandle,
                dwmTransitionsForceDisabled,
                memAddress(disabled),
                Integer.BYTES,
                setWindowAttribute);
            if (result != 0) {
                System.err.println(
                    "Warning: could not disable Windows window transitions: " + result);
            }
        } catch (Throwable failure) {
            System.err.println(
                "Warning: could not disable Windows window transitions: " + failure);
        }
    }

    /**
     * Toggle the window into macOS native fullscreen — the green-button
     * "separate Space" mode — by calling NSWindow -toggleFullScreen: via the
     * Objective-C runtime. Wrapped so any failure (non-mac build, missing
     * native binding, headless) is caught and logged rather than killing the
     * app; the game still runs windowed in that case.
     */
    private static void enterMacNativeFullscreen() {
        try {
            long windowHandle = ((Lwjgl3Graphics) Gdx.graphics)
                .getWindow().getWindowHandle();
            long cocoaWindow = org.lwjgl.glfw.GLFWNativeCocoa
                .glfwGetCocoaWindow(windowHandle);
            long toggleSelector = org.lwjgl.system.macosx.ObjCRuntime
                .sel_registerName("toggleFullScreen:");
            long objcMsgSend = org.lwjgl.system.macosx.ObjCRuntime
                .getLibrary().getFunctionAddress("objc_msgSend");
            // objc_msgSend(cocoaWindow, "toggleFullScreen:", nil)
            org.lwjgl.system.JNI.invokePPV(
                cocoaWindow, toggleSelector, org.lwjgl.system.macosx.ObjCRuntime.nil,
                objcMsgSend);
        } catch (Throwable t) {
            System.err.println("Warning: could not enter native fullscreen: " + t);
        }
    }
}
