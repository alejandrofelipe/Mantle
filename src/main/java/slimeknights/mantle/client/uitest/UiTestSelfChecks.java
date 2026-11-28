package slimeknights.mantle.client.uitest;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWWindowFocusCallback;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.render.RenderTargetCapture;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;

/** Scenarios checking the suite itself, registered by {@link UiTestSuite#init()} */
final class UiTestSelfChecks {
  private UiTestSelfChecks() {}

  /**
   * Feeds fake OS events through the GLFW callbacks installed on the window, the entry point of real OS input: a typed character into a
   * focused edit box, then a focus gain, cursor moves, a left click, a scroll and the W and E keys in the world. While the suite isolates
   * input ({@link UiTestInput}), none of them may reach the game: the edit box only holds the character typed into the screen directly
   * (control), the parked mouse stays put, no screen opens (E is the inventory key), the player neither moves (W) nor turns, the hotbar
   * slot is kept (scroll), and the cursor is not grabbed although {@code setScreen(null)} ran after the focus gain (vanilla grabs it there
   * when the window is focused).
   */
  static final class InputIsolationScenario implements UiTestScenario {
    private static final double PARK_X = 37, PARK_Y = 23;
    private double mouseX, mouseY;
    private float yRot, xRot;
    private Vec3 position = Vec3.ZERO;
    private int slot;
    private List<String> grabbedOnOpen = List.of();
    private List<String> typedOnOpen = List.of();

    @Override
    public Identifier id() {
      return Mantle.getResource("input_isolation");
    }

    @Override
    public Set<String> tags() {
      return Set.of(UiTestTags.SUITE, UiTestTags.SMOKE, UiTestTags.WORLD);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      // vanilla types characters only into a screen (KeyboardHandler#charTyped): a focused edit box, given one character directly as control
      TextScreen screen = new TextScreen();
      ctx.setScreen(screen);
      screen.charTyped(new CharacterEvent(CONTROL_CHAR));
      long window = ctx.mc().getWindow().handle();
      // vanilla's callback defers to the client thread (Minecraft#execute), so the box is read in open()
      charCallback(window).invoke(window, 'e');
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      Minecraft mc = ctx.mc();
      if (!(ctx.screen() instanceof TextScreen screen)) {
        throw new IllegalStateException("expected the edit box screen, found " + ctx.screen());
      }
      String typed = screen.box.getValue();
      if (typed.isEmpty()) {
        throw new IllegalStateException("control: the edit box took no character typed into the screen, so the OS character check proves nothing");
      }
      typedOnOpen = typed.equals(String.valueOf(CONTROL_CHAR)) ? List.of() : List.of("a typed character reached the edit box: '" + typed + "'");
      long window = mc.getWindow().handle();
      // vanilla grabs the cursor on setScreen(null) and on a click in the world, but only while the window is focused
      focusCallback(window).invoke(window, true);
      ctx.setScreen(null);
      // checked right away too: the E key below would open the inventory, which releases a grabbed cursor again
      grabbedOnOpen = cursorProblems(mc);
      ctx.moveMouse(PARK_X, PARK_Y);
      mouseX = mc.mouseHandler.xpos();
      mouseY = mc.mouseHandler.ypos();
      LocalPlayer player = ctx.player();
      yRot = player.getYRot();
      xRot = player.getXRot();
      position = player.position();
      slot = player.getInventory().getSelectedSlot();

      GLFWCursorPosCallback move = cursorCallback(window);
      move.invoke(window, mouseX + 300, mouseY + 200);
      move.invoke(window, mouseX + 500, mouseY + 350);
      mouseButtonCallback(window).invoke(window, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
      scrollCallback(window).invoke(window, 0, -3);
      GLFWKeyCallback key = keyCallback(window);
      key.invoke(window, GLFW.GLFW_KEY_W, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_W), GLFW.GLFW_PRESS, 0);
      key.invoke(window, GLFW.GLFW_KEY_E, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_E), GLFW.GLFW_PRESS, 0);
    }

    @Override
    public void close(UiTestContext ctx) {
      Minecraft mc = ctx.mc();
      LocalPlayer player = ctx.player();
      List<String> problems = new ArrayList<>();
      if (ctx.screen() != null) {
        problems.add("a screen opened: " + ctx.screen().getClass().getSimpleName());
      }
      if (mc.mouseHandler.xpos() != mouseX || mc.mouseHandler.ypos() != mouseY) {
        problems.add("the mouse moved from " + mouseX + ", " + mouseY + " to " + mc.mouseHandler.xpos() + ", " + mc.mouseHandler.ypos());
      }
      if (player.getYRot() != yRot || player.getXRot() != xRot) {
        problems.add("the player turned from " + yRot + "/" + xRot + " to " + player.getYRot() + "/" + player.getXRot());
      }
      if (player.position().distanceTo(position) > 0.05) {
        problems.add("the player moved from " + position + " to " + player.position());
      }
      if (player.getInventory().getSelectedSlot() != slot) {
        problems.add("the hotbar slot changed from " + slot + " to " + player.getInventory().getSelectedSlot());
      }
      if (mc.options.keyUp.isDown() || mc.options.keyAttack.isDown()) {
        problems.add("a key mapping is down (forward " + mc.options.keyUp.isDown() + ", attack " + mc.options.keyAttack.isDown() + ")");
      }
      problems.addAll(typedOnOpen);
      grabbedOnOpen.forEach(problem -> problems.add(problem + " after setScreen(null) on a focused window"));
      problems.addAll(cursorProblems(mc));
      if (!problems.isEmpty()) {
        throw new IllegalStateException("OS input reached the game: " + String.join("; ", problems));
      }
    }

    /** The cursor must stay free and visible: not grabbed by the mouse handler, normal GLFW cursor mode */
    private static List<String> cursorProblems(Minecraft mc) {
      List<String> problems = new ArrayList<>();
      if (mc.mouseHandler.isMouseGrabbed()) {
        problems.add("the mouse handler grabbed the cursor");
      }
      int mode = GLFW.glfwGetInputMode(mc.getWindow().handle(), GLFW.GLFW_CURSOR);
      if (mode != GLFW.GLFW_CURSOR_NORMAL) {
        problems.add("the cursor input mode is " + mode + ", not normal");
      }
      return problems;
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      Minecraft mc = ctx.mc();
      long window = mc.getWindow().handle();
      // release what was pressed, then report the real focus again
      mouseButtonCallback(window).invoke(window, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
      GLFWKeyCallback key = keyCallback(window);
      key.invoke(window, GLFW.GLFW_KEY_W, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_W), GLFW.GLFW_RELEASE, 0);
      key.invoke(window, GLFW.GLFW_KEY_E, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_E), GLFW.GLFW_RELEASE, 0);
      focusCallback(window).invoke(window, GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
      ctx.setScreen(null);
    }
  }

  /**
   * The progress bar never reaches a capture: opens a screen filled with one color in {@code prepare}, then checks the left part of the
   * window, where the bar and its label are drawn, on two frames of that screen. Control: the last frame before the suite starts hiding the
   * bar, read raw in {@code open}, has the bar's fill color there. Check: the captured frame has exactly the screen color there. Both are
   * pixel reads, independent of the suite's own bookkeeping ({@link UiTestProgress#lastFrameClean()}). Also checks the window title shows
   * the progress.
   */
  static final class ProgressHiddenScenario implements UiTestScenario {
    /** Opaque fill of the screen, ARGB as {@link NativeImage#getPixel} returns it */
    private static final int COLOR = 0xFF3060A0;
    /** Width in GUI pixels of the checked strip on the left edge, covering the bar and its label */
    private static final int STRIP = 48;
    /** Per channel tolerance when looking for the bar's fill color */
    private static final int FILL_TOLERANCE = 8;

    @Override
    public Identifier id() {
      return Mantle.getResource("progress_hidden");
    }

    @Override
    public Set<String> tags() {
      return Set.of(UiTestTags.SUITE, UiTestTags.SMOKE, UiTestTags.GUI);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      ctx.setScreen(new SolidScreen());
      ctx.parkMouse();
    }

    @Override
    public void open(UiTestContext ctx) {
      requireSolidScreen(ctx);
      // control: the frame before the capture wait, read raw (captureFrame refuses a frame with the bar), shows the bar over this screen
      try (NativeImage image = RenderTargetCapture.capture(ctx.mainRenderTarget(), false)) {
        int[] fill = scanStrip(ctx, image, color -> isNear(color, UiTestProgress.FILL));
        if (fill[0] == 0) {
          throw new IllegalStateException("control: the uncaptured frame of the solid screen has no pixel of the progress bar fill #"
                                          + Integer.toHexString(UiTestProgress.FILL) + " in the left strip, so the capture check proves nothing");
        }
      }
    }

    @Override
    public void close(UiTestContext ctx) {
      requireSolidScreen(ctx);
      try (NativeImage image = ctx.captureFrame()) {
        int[] wrong = scanStrip(ctx, image, color -> color != COLOR);
        if (wrong[0] > 0) {
          throw new IllegalStateException(wrong[0] + " pixel(s) of the left strip are not the screen color #" + Integer.toHexString(COLOR)
                                          + " (first at " + wrong[1] + ", " + wrong[2] + ": #" + Integer.toHexString(wrong[3]) + "): the progress display is on the captured frame");
        }
      }
      String title = GLFW.glfwGetWindowTitle(ctx.mc().getWindow().handle());
      if (title == null || !title.startsWith("uitest ") || !title.endsWith(" — " + id())) {
        throw new IllegalStateException("the window title '" + title + "' does not show the progress 'uitest n/N — " + id() + "'");
      }
      ctx.setScreen(null);
    }

    private static void requireSolidScreen(UiTestContext ctx) {
      if (!(ctx.screen() instanceof SolidScreen)) {
        throw new IllegalStateException("expected the solid screen, found " + ctx.screen());
      }
    }

    /** Scans the left {@link #STRIP} GUI pixels of the frame: the count of pixels matching, then x, y and color of the first one */
    private static int[] scanStrip(UiTestContext ctx, NativeImage image, IntPredicate match) {
      double scale = (double) image.getWidth() / ctx.mc().getWindow().getGuiScaledWidth();
      int width = Math.min(image.getWidth(), (int) Math.ceil(STRIP * scale));
      int[] result = new int[4];
      for (int y = 0; y < image.getHeight(); y++) {
        for (int x = 0; x < width; x++) {
          int color = image.getPixel(x, y);
          if (match.test(color)) {
            if (result[0] == 0) {
              result[1] = x;
              result[2] = y;
              result[3] = color;
            }
            result[0]++;
          }
        }
      }
      return result;
    }

    /** Checks each color channel of the pixel is within {@link #FILL_TOLERANCE} of the expected color */
    private static boolean isNear(int color, int expected) {
      for (int shift = 0; shift <= 16; shift += 8) {
        if (Math.abs(((color >> shift) & 0xFF) - ((expected >> shift) & 0xFF)) > FILL_TOLERANCE) {
          return false;
        }
      }
      return true;
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      ctx.setScreen(null);
    }
  }

  /**
   * {@link UiTestContext#clearInventory} empties the inventory like {@code /clear @s} (main inventory, armor, offhand) and in order with
   * {@link UiTestContext#runOnServer}: the server thread is held while the clear and a following give are scheduled, and the client waits long
   * enough for a command to reach the server queue meanwhile, so a clear sent as a command (network thread first) would land after the give
   * and wipe it. The order matters as a scenario's {@code cleanup} and the next one's {@code prepare} run in the same tick.
   */
  static final class ClearInventoryScenario implements UiTestScenario {
    /** Client wait with the server held, far longer than a command needs to reach the server queue */
    private static final int HOLD_MILLIS = 100;
    private static final int SEEDED_SLOT = 5;

    @Override
    public Identifier id() {
      return Mantle.getResource("clear_inventory");
    }

    @Override
    public Set<String> tags() {
      return Set.of(UiTestTags.SUITE, UiTestTags.SMOKE, UiTestTags.WORLD);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      ctx.setScreen(null);
      UUID id = ctx.player().getUUID();
      ctx.runOnServer(() -> {
        ServerPlayer player = serverPlayer(ctx, id);
        player.getInventory().setItem(SEEDED_SLOT, new ItemStack(Items.DIRT));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
      });
      CountDownLatch release = new CountDownLatch(1);
      ctx.runOnServer(() -> {
        try {
          if (!release.await(5, TimeUnit.SECONDS)) {
            Mantle.logger.error("uitest: {} held the server thread without release", id());
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
      try {
        ctx.clearInventory();
        ctx.runOnServer(() -> serverPlayer(ctx, id).getInventory().setItem(0, new ItemStack(Items.STONE)));
        Thread.sleep(HOLD_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        release.countDown();
      }
    }

    private static ServerPlayer serverPlayer(UiTestContext ctx, UUID id) {
      return Objects.requireNonNull(ctx.serverLevel().getServer().getPlayerList().getPlayer(id), "uitest: no server player");
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {}

    @Override
    public void close(UiTestContext ctx) {
      LocalPlayer player = ctx.player();
      List<String> problems = new ArrayList<>();
      if (!player.getInventory().getItem(0).is(Items.STONE)) {
        problems.add("the stone given after the clear is missing, slot 0 holds " + player.getInventory().getItem(0) + ": the clear ran after it");
      }
      if (!player.getInventory().getItem(SEEDED_SLOT).isEmpty()) {
        problems.add("slot " + SEEDED_SLOT + " still holds " + player.getInventory().getItem(SEEDED_SLOT));
      }
      for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.OFFHAND)) {
        if (!player.getItemBySlot(slot).isEmpty()) {
          problems.add(slot + " still holds " + player.getItemBySlot(slot));
        }
      }
      if (!problems.isEmpty()) {
        throw new IllegalStateException("clearInventory: " + String.join("; ", problems));
      }
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      ctx.clearInventory();
    }
  }

  /**
   * The suite's capture path reads the frame the right way up, with the right channel order, and the latest frame (M8 plan R1 guard S2: a rewrite of
   * {@link RenderTargetCapture} on a new buffer or texture format API can flip the rows, swap red and blue or return an older frame while every
   * scenario still reports ok). The screen fills the four quadrants with colors whose channels all differ and draws a marker square encoding a
   * counter of its extracted frames. Checked twice: the frame read by {@link UiTestContext#captureFrame()} and the PNG the suite wrote from it must
   * have the exact quadrant color in each corner, and a marker equal to the counter of the last extracted frame.
   */
  static final class CaptureOrientationScenario implements UiTestScenario {
    /** Quadrant colors, ARGB as {@link NativeImage#getPixel} returns it: no two share a channel value, so a swap or a flip changes them all */
    static final int TOP_LEFT = 0xFFE01020, TOP_RIGHT = 0xFF20C040, BOTTOM_LEFT = 0xFF3050D0, BOTTOM_RIGHT = 0xFFF0B060;
    /** Half the size of the marker square in GUI pixels */
    private static final int MARKER = 4;

    @Override
    public Identifier id() {
      return Mantle.getResource("capture_orientation");
    }

    @Override
    public Set<String> tags() {
      return Set.of(UiTestTags.SUITE, UiTestTags.SMOKE, UiTestTags.GUI, UiTestTags.RENDER);
    }

    @Override
    public void prepare(UiTestContext ctx) {
      ctx.setScreen(new QuadrantScreen());
      ctx.parkMouse();
    }

    @Override
    public int prepareSettleTicks() {
      return 5;
    }

    @Override
    public void open(UiTestContext ctx) {
      requireScreen(ctx);
    }

    @Override
    public int settleTicks() {
      return 5;
    }

    @Override
    public void close(UiTestContext ctx) {
      QuadrantScreen screen = requireScreen(ctx);
      int frame = screen.frames;
      List<String> problems = new ArrayList<>();
      try (NativeImage image = ctx.captureFrame()) {
        check(ctx, image, "captured frame", frame, problems);
      }
      File png = new File(new File(ctx.mc().gameDirectory, UiTestSuite.SCREENSHOT_DIR), id().getPath() + ".png");
      try (NativeImage image = NativeImage.read(Files.newInputStream(png.toPath()))) {
        check(ctx, image, "written PNG", frame, problems);
      } catch (IOException e) {
        problems.add("cannot read back " + png + ": " + e);
      }
      if (!problems.isEmpty()) {
        throw new IllegalStateException("capture: " + String.join("; ", problems));
      }
      ctx.setScreen(null);
    }

    private static QuadrantScreen requireScreen(UiTestContext ctx) {
      if (!(ctx.screen() instanceof QuadrantScreen screen)) {
        throw new IllegalStateException("expected the quadrant screen, found " + ctx.screen());
      }
      return screen;
    }

    /** Checks the corners and the marker of one image of the screen */
    private static void check(UiTestContext ctx, NativeImage image, String name, int frame, List<String> problems) {
      int width = image.getWidth(), height = image.getHeight();
      int[][] corners = {{0, 0, TOP_LEFT}, {width - 1, 0, TOP_RIGHT}, {0, height - 1, BOTTOM_LEFT}, {width - 1, height - 1, BOTTOM_RIGHT}};
      String[] names = {"top left", "top right", "bottom left", "bottom right"};
      for (int i = 0; i < corners.length; i++) {
        int color = image.getPixel(corners[i][0], corners[i][1]);
        if (color != corners[i][2]) {
          problems.add(String.format(Locale.ROOT, "%s: the %s corner is #%08X, expected #%08X", name, names[i], color, corners[i][2]));
        }
      }
      int marker = image.getPixel(width / 2, height / 2);
      if (marker != markerColor(frame)) {
        int seen = marker & 0xFFFF;
        problems.add(String.format(Locale.ROOT, "%s: the frame marker is #%08X (frame %d), expected frame %d: not the latest frame", name, marker, seen, frame & 0xFFFF));
      }
      if (width != ctx.mc().getWindow().getWidth() || height != ctx.mc().getWindow().getHeight()) {
        problems.add(name + " is " + width + "x" + height + ", the window " + ctx.mc().getWindow().getWidth() + "x" + ctx.mc().getWindow().getHeight());
      }
    }

    /** Marker color of the extracted frame: the low 16 bits of the counter in green and blue, red fixed */
    static int markerColor(int frame) {
      return 0xFF800000 | (frame & 0xFFFF);
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      ctx.setScreen(null);
    }
  }

  /** Screen for {@link CaptureOrientationScenario}: four colored quadrants and a frame counter marker in the middle */
  private static final class QuadrantScreen extends Screen {
    /** Frames extracted so far */
    private int frames;

    private QuadrantScreen() {
      super(Component.empty());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      frames++;
      int halfWidth = this.width / 2, halfHeight = this.height / 2;
      graphics.fill(0, 0, halfWidth, halfHeight, CaptureOrientationScenario.TOP_LEFT);
      graphics.fill(halfWidth, 0, this.width, halfHeight, CaptureOrientationScenario.TOP_RIGHT);
      graphics.fill(0, halfHeight, halfWidth, this.height, CaptureOrientationScenario.BOTTOM_LEFT);
      graphics.fill(halfWidth, halfHeight, this.width, this.height, CaptureOrientationScenario.BOTTOM_RIGHT);
      int marker = CaptureOrientationScenario.MARKER;
      graphics.fill(halfWidth - marker, halfHeight - marker, halfWidth + marker, halfHeight + marker, CaptureOrientationScenario.markerColor(frames));
    }
  }

  /** Character typed into the edit box directly by {@link InputIsolationScenario}, the only one it may hold */
  private static final char CONTROL_CHAR = 'x';

  /** Screen with one focused edit box, for {@link InputIsolationScenario} */
  private static final class TextScreen extends Screen {
    private EditBox box;

    private TextScreen() {
      super(Component.empty());
    }

    @Override
    protected void init() {
      box = addRenderableWidget(new EditBox(font, width / 2 - 100, height / 2 - 10, 200, 20, Component.empty()));
      setInitialFocus(box);
    }
  }

  /** Screen filling the whole window with {@link ProgressHiddenScenario#COLOR} */
  private static final class SolidScreen extends Screen {
    private SolidScreen() {
      super(Component.empty());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      graphics.fill(0, 0, this.width, this.height, ProgressHiddenScenario.COLOR);
    }
  }

  /* The callbacks currently installed on the window: GLFW only returns them when swapping, so swap in nothing and put them back */

  private static GLFWCursorPosCallback cursorCallback(long window) {
    GLFWCursorPosCallback callback = GLFW.glfwSetCursorPosCallback(window, null);
    GLFW.glfwSetCursorPosCallback(window, callback);
    return Objects.requireNonNull(callback, "no cursor position callback");
  }

  private static GLFWMouseButtonCallback mouseButtonCallback(long window) {
    GLFWMouseButtonCallback callback = GLFW.glfwSetMouseButtonCallback(window, null);
    GLFW.glfwSetMouseButtonCallback(window, callback);
    return Objects.requireNonNull(callback, "no mouse button callback");
  }

  private static GLFWScrollCallback scrollCallback(long window) {
    GLFWScrollCallback callback = GLFW.glfwSetScrollCallback(window, null);
    GLFW.glfwSetScrollCallback(window, callback);
    return Objects.requireNonNull(callback, "no scroll callback");
  }

  private static GLFWKeyCallback keyCallback(long window) {
    GLFWKeyCallback callback = GLFW.glfwSetKeyCallback(window, null);
    GLFW.glfwSetKeyCallback(window, callback);
    return Objects.requireNonNull(callback, "no key callback");
  }

  private static GLFWCharCallback charCallback(long window) {
    GLFWCharCallback callback = GLFW.glfwSetCharCallback(window, null);
    GLFW.glfwSetCharCallback(window, callback);
    return Objects.requireNonNull(callback, "no char callback");
  }

  private static GLFWWindowFocusCallback focusCallback(long window) {
    GLFWWindowFocusCallback callback = GLFW.glfwSetWindowFocusCallback(window, null);
    GLFW.glfwSetWindowFocusCallback(window, callback);
    return Objects.requireNonNull(callback, "no window focus callback");
  }
}
