package slimeknights.mantle.client.uitest;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWPreeditCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWWindowFocusCallback;
import org.lwjgl.system.Callback;
import slimeknights.mantle.Mantle;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps OS mouse and keyboard input away from the game while the suite runs, so moving the mouse over the window or typing into it
 * can't change a capture: the GLFW input callbacks vanilla installs ({@code MouseHandler#setup}, {@code KeyboardHandler#setup}) are
 * swapped for ones that drop the events, and restored when the suite ends. Scenarios keep driving the game directly
 * ({@link UiTestContext#parkMouse()}, synthetic screen events, {@code setScreen}), which never goes through these callbacks.
 * <p>
 * The cursor stays free and visible: vanilla grabs it ({@code MouseHandler#grabMouse}, disabled cursor warped to the window center)
 * on {@code setScreen(null)} or a click in the world, but only while the window is focused ({@code Minecraft#isWindowActive}). The
 * focus callback is swapped too, keeping the window unfocused for the game, as during an unattended run in the background; pausing on
 * lost focus is turned off meanwhile, since it would open the pause menu over in-world captures.
 */
final class UiTestInput {
  private static @Nullable Saved saved = null;

  private UiTestInput() {}

  /** Previous callbacks and settings, restored by {@link #restore} */
  private record Saved(long window, List<Runnable> restores, List<Callback> dropping, boolean pauseOnLostFocus) {}

  /** Swaps the input callbacks for dropping ones, releases a grabbed cursor without moving it, and keeps the window unfocused for the game. */
  static void isolate(Minecraft mc) {
    if (saved != null) {
      return;
    }
    Window window = mc.getWindow();
    long handle = window.handle();
    List<Runnable> restores = new ArrayList<>();
    List<Callback> dropping = new ArrayList<>();
    // mouse: move, buttons, scroll, file drop
    GLFWCursorPosCallback move = keep(dropping, GLFWCursorPosCallback.create((w, x, y) -> {}));
    GLFWCursorPosCallback oldMove = GLFW.glfwSetCursorPosCallback(handle, move);
    restores.add(() -> GLFW.glfwSetCursorPosCallback(handle, oldMove));
    GLFWMouseButtonCallback button = keep(dropping, GLFWMouseButtonCallback.create((w, b, action, mods) -> {}));
    GLFWMouseButtonCallback oldButton = GLFW.glfwSetMouseButtonCallback(handle, button);
    restores.add(() -> GLFW.glfwSetMouseButtonCallback(handle, oldButton));
    GLFWScrollCallback scroll = keep(dropping, GLFWScrollCallback.create((w, x, y) -> {}));
    GLFWScrollCallback oldScroll = GLFW.glfwSetScrollCallback(handle, scroll);
    restores.add(() -> GLFW.glfwSetScrollCallback(handle, oldScroll));
    GLFWDropCallback drop = keep(dropping, GLFWDropCallback.create((w, count, names) -> {}));
    GLFWDropCallback oldDrop = GLFW.glfwSetDropCallback(handle, drop);
    restores.add(() -> GLFW.glfwSetDropCallback(handle, oldDrop));
    // keyboard: keys, characters, IME composition
    GLFWKeyCallback key = keep(dropping, GLFWKeyCallback.create((w, k, scancode, action, mods) -> {}));
    GLFWKeyCallback oldKey = GLFW.glfwSetKeyCallback(handle, key);
    restores.add(() -> GLFW.glfwSetKeyCallback(handle, oldKey));
    GLFWCharCallback character = keep(dropping, GLFWCharCallback.create((w, codepoint) -> {}));
    GLFWCharCallback oldChar = GLFW.glfwSetCharCallback(handle, character);
    restores.add(() -> GLFW.glfwSetCharCallback(handle, oldChar));
    GLFWPreeditCallback preedit = keep(dropping, GLFWPreeditCallback.create((w, size, text, blocks, blockSizes, focused, caret) -> {}));
    GLFWPreeditCallback oldPreedit = GLFW.glfwSetPreeditCallback(handle, preedit);
    restores.add(() -> GLFW.glfwSetPreeditCallback(handle, oldPreedit));
    // focus: dropped too, the game keeps seeing an unfocused window (restore reads the real focus from GLFW)
    GLFWWindowFocusCallback focus = keep(dropping, GLFWWindowFocusCallback.create((w, focused) -> {}));
    GLFWWindowFocusCallback oldFocus = GLFW.glfwSetWindowFocusCallback(handle, focus);
    restores.add(() -> GLFW.glfwSetWindowFocusCallback(handle, oldFocus));
    setField(WINDOW_FOCUSED, window, false);

    // already grabbed (e.g. the world loaded with the window focused): release in place, MouseHandler#releaseMouse would warp the cursor
    if (mc.mouseHandler.isMouseGrabbed()) {
      setField(MOUSE_GRABBED, mc.mouseHandler, false);
      GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }
    KeyMapping.releaseAll();
    boolean pause = mc.options.pauseOnLostFocus;
    mc.options.pauseOnLostFocus = false;
    saved = new Saved(handle, restores, dropping, pause);
    Mantle.logger.info("uitest: OS mouse and keyboard input isolated until the suite ends; the cursor stays free");
  }

  /** Puts the vanilla callbacks, the real window focus and the pause setting back. */
  static void restore(Minecraft mc) {
    Saved current = saved;
    if (current == null) {
      return;
    }
    saved = null;
    current.restores.forEach(Runnable::run);
    current.dropping.forEach(Callback::free);
    setField(WINDOW_FOCUSED, mc.getWindow(), GLFW.glfwGetWindowAttrib(current.window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
    mc.options.pauseOnLostFocus = current.pauseOnLostFocus;
    Mantle.logger.info("uitest: OS input restored");
  }

  private static <T extends Callback> T keep(List<Callback> list, T callback) {
    list.add(callback);
    return callback;
  }

  private static final Field WINDOW_FOCUSED = field(Window.class, "focused");
  private static final Field MOUSE_GRABBED = field(MouseHandler.class, "mouseGrabbed");

  private static Field field(Class<?> owner, String name) {
    try {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      return field;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("uitest: " + owner.getSimpleName() + " has no field " + name, e);
    }
  }

  private static void setField(Field field, Object owner, boolean value) {
    try {
      field.setBoolean(owner, value);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("uitest: cannot set " + field, e);
    }
  }
}
