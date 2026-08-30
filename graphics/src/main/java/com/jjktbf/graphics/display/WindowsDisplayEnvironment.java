package com.jjktbf.graphics.display;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics.Lwjgl3Monitor;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/** Native monitor pixels plus the Windows DPI/content scale reported by GLFW. */
public record WindowsDisplayEnvironment(
    int pixelWidth,
    int pixelHeight,
    float contentScaleX,
    float contentScaleY
) {
    public static WindowsDisplayEnvironment detect(
        Graphics.Monitor monitor,
        Graphics.DisplayMode mode
    ) {
        float scaleX = 1f;
        float scaleY = 1f;
        if (monitor instanceof Lwjgl3Monitor lwjglMonitor) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer x = stack.mallocFloat(1);
                FloatBuffer y = stack.mallocFloat(1);
                GLFW.glfwGetMonitorContentScale(lwjglMonitor.getMonitorHandle(), x, y);
                scaleX = positiveOrOne(x.get(0));
                scaleY = positiveOrOne(y.get(0));
            } catch (Throwable failure) {
                System.err.println("Warning: could not detect Windows display scaling: " + failure);
            }
        }
        return new WindowsDisplayEnvironment(mode.width, mode.height, scaleX, scaleY);
    }

    public int scalePercent() {
        return Math.round((contentScaleX + contentScaleY) * 50f);
    }

    private static float positiveOrOne(float value) {
        return Float.isFinite(value) && value > 0f ? value : 1f;
    }
}
