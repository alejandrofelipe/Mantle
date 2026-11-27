package slimeknights.mantle.client.uitest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Shows the suite's progress while it runs: a thin vertical bar on the left edge of the window, colored like the XP bar and filling up
 * as scenarios finish, with an {@code n/N} label, plus the window title {@code uitest n/N — <scenario id>}.
 * <p>
 * The bar must never reach a capture. Captures read the main render target, which holds the last rendered frame, so the suite
 * {@linkplain #setHidden hides} the bar shortly before each capture and only captures once a frame was rendered without it
 * ({@link #lastFrameClean()}); {@link UiTestContext#captureFrame()} fails if the frame it reads still had the bar. The bar is extracted as
 * the last GUI element: after the HUD layers ({@link RenderGuiEvent.Post}) with no screen open, else after the screen
 * ({@link ScreenEvent.Render.Post}), in a new stratum so it draws over blur and tooltips.
 */
final class UiTestProgress {
  /** XP bar colors: the bright green of the filled part and of the level number (also the self-check's control), a dark track, a black outline */
  static final int FILL = 0xFF80FF20;
  private static final int TRACK = 0xFF2A2A2A;
  private static final int OUTLINE = 0xFF000000;
  /** Bar geometry in GUI pixels: left edge, width including the outline, top and bottom margins */
  private static final int LEFT = 1, WIDTH = 4, TOP = 12, BOTTOM = 4;

  private static boolean active = false;
  private static boolean hidden = false;
  private static int current = 0;
  private static int total = 0;
  private static String scenario = "";
  /** Whether the frame being extracted got the bar, and whether the last fully rendered frame had it */
  private static boolean barThisFrame = false;
  private static boolean barLastFrame = false;
  private static boolean listening = false;

  private UiTestProgress() {}

  /** Registers the render listeners, once */
  static void init() {
    if (!listening) {
      listening = true;
      NeoForge.EVENT_BUS.addListener(UiTestProgress::onRenderGui);
      NeoForge.EVENT_BUS.addListener(UiTestProgress::onRenderScreen);
      NeoForge.EVENT_BUS.addListener(UiTestProgress::onFramePre);
      NeoForge.EVENT_BUS.addListener(UiTestProgress::onFramePost);
    }
  }

  /** Shows scenario {@code index + 1} of {@code total} */
  static void update(int index, int count, @Nullable String id) {
    active = count > 0;
    current = index;
    total = count;
    scenario = id == null ? "" : id;
  }

  /** Requests the bar hidden (capture coming) or shown again */
  static void setHidden(boolean value) {
    hidden = value;
  }

  /** Stops the display and gives the window its vanilla title back */
  static void stop(Minecraft mc) {
    active = false;
    mc.updateTitle();
  }

  /** True if the last rendered frame, the one the main render target holds, was drawn without the bar */
  static boolean lastFrameClean() {
    return !barLastFrame;
  }

  /** Throws if the frame in the main render target has the bar, call before reading its pixels */
  static void checkCapture() {
    if (barLastFrame) {
      throw new IllegalStateException("uitest: the progress bar is on the frame being captured");
    }
  }

  private static void onRenderGui(RenderGuiEvent.Post event) {
    // with a screen open the HUD is drawn below it, so the screen event draws the bar instead
    if (Minecraft.getInstance().screen == null) {
      draw(event.getGuiGraphics());
    }
  }

  private static void onRenderScreen(ScreenEvent.Render.Post event) {
    draw(event.getGuiGraphics());
  }

  private static void draw(GuiGraphicsExtractor graphics) {
    if (!active || hidden) {
      return;
    }
    graphics.nextStratum();
    int bottom = graphics.guiHeight() - BOTTOM;
    graphics.fill(LEFT, TOP, LEFT + WIDTH, bottom, OUTLINE);
    graphics.fill(LEFT + 1, TOP + 1, LEFT + WIDTH - 1, bottom - 1, TRACK);
    int inner = bottom - 1 - (TOP + 1);
    int filled = inner * current / total;
    graphics.fill(LEFT + 1, bottom - 1 - filled, LEFT + WIDTH - 1, bottom - 1, FILL);
    graphics.text(Minecraft.getInstance().font, (current + 1) + "/" + total, LEFT, 2, FILL, true);
    barThisFrame = true;
  }

  /** Extraction is done before this, so the frame's bar state is known; the title is set here as screens reset it ({@code Minecraft#updateTitle}) */
  private static void onFramePre(RenderFrameEvent.Pre event) {
    if (active) {
      Minecraft mc = Minecraft.getInstance();
      String title = "uitest " + (current + 1) + "/" + total + " — " + scenario;
      long window = mc.getWindow().handle();
      if (!title.equals(GLFW.glfwGetWindowTitle(window))) {
        mc.getWindow().setTitle(title);
      }
    }
  }

  private static void onFramePost(RenderFrameEvent.Post event) {
    barLastFrame = barThisFrame;
    barThisFrame = false;
  }
}
