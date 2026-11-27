package slimeknights.mantle.client.uitest;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Scenarios checking the suite itself, registered by {@link UiTestSuite#init()} */
final class UiTestSelfChecks {
  private UiTestSelfChecks() {}

  /**
   * Feeds fake OS events through the GLFW callbacks installed on the window, the entry point of real OS input: a focus gain, cursor
   * moves, a left click, a scroll, the W and E keys and a typed character. While the suite isolates input ({@link UiTestInput}), none
   * of them may reach the game: the parked mouse stays put, no screen opens (E is the inventory key), the player neither moves (W) nor
   * turns, the hotbar slot is kept (scroll), and the cursor is not grabbed although {@code setScreen(null)} ran after the focus gain
   * (vanilla grabs it there when the window is focused).
   */
  static final class InputIsolationScenario implements UiTestScenario {
    private static final double PARK_X = 37, PARK_Y = 23;
    private double mouseX, mouseY;
    private float yRot, xRot;
    private Vec3 position = Vec3.ZERO;
    private int slot;
    private List<String> grabbedOnOpen = List.of();

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
      ctx.mc().setScreen(null);
    }

    @Override
    public int prepareSettleTicks() {
      return 20;
    }

    @Override
    public void open(UiTestContext ctx) {
      Minecraft mc = ctx.mc();
      long window = mc.getWindow().handle();
      // vanilla grabs the cursor on setScreen(null) and on a click in the world, but only while the window is focused
      focusCallback(window).invoke(window, true);
      mc.setScreen(null);
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
      charCallback(window).invoke(window, 'e');
    }

    @Override
    public void close(UiTestContext ctx) {
      Minecraft mc = ctx.mc();
      LocalPlayer player = ctx.player();
      List<String> problems = new ArrayList<>();
      if (mc.screen != null) {
        problems.add("a screen opened: " + mc.screen.getClass().getSimpleName());
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
      mc.setScreen(null);
    }
  }

  /**
   * The progress bar never reaches a capture: shows the bar in the world during the prepare wait (control: frames with the bar were
   * rendered meanwhile), then opens a screen filled with one color and checks that the left part of the captured frame, where the bar
   * and its label are drawn, is exactly that color. Independent of the suite's own bookkeeping ({@link UiTestProgress#lastFrameClean()}).
   * Also checks the window title shows the progress.
   */
  static final class ProgressHiddenScenario implements UiTestScenario {
    /** Opaque fill of the screen, ARGB as {@link NativeImage#getPixel} returns it */
    private static final int COLOR = 0xFF3060A0;
    /** Width in GUI pixels of the checked strip on the left edge, covering the bar and its label */
    private static final int STRIP = 48;
    private long framesBefore;

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
      ctx.mc().setScreen(null);
      framesBefore = UiTestProgress.framesWithBar();
    }

    @Override
    public void open(UiTestContext ctx) {
      ctx.mc().setScreen(new SolidScreen());
      ctx.parkMouse();
    }

    @Override
    public void close(UiTestContext ctx) {
      if (!(ctx.mc().screen instanceof SolidScreen)) {
        throw new IllegalStateException("expected the solid screen, found " + ctx.mc().screen);
      }
      // pixels first: a bar drawn but not recorded by the suite must fail here, before the control below
      try (NativeImage image = ctx.captureFrame()) {
        double scale = (double) image.getWidth() / ctx.mc().getWindow().getGuiScaledWidth();
        int width = Math.min(image.getWidth(), (int) Math.ceil(STRIP * scale));
        int wrong = 0;
        String first = "";
        for (int y = 0; y < image.getHeight(); y++) {
          for (int x = 0; x < width; x++) {
            int color = image.getPixel(x, y);
            if (color != COLOR) {
              if (wrong == 0) {
                first = " (first at " + x + ", " + y + ": #" + Integer.toHexString(color) + ")";
              }
              wrong++;
            }
          }
        }
        if (wrong > 0) {
          throw new IllegalStateException(wrong + " pixel(s) of the left " + width + " px strip are not the screen color #" + Integer.toHexString(COLOR) + first + ": the progress display is on the captured frame");
        }
      }
      if (UiTestProgress.framesWithBar() <= framesBefore) {
        throw new IllegalStateException("control: no frame showed the progress bar during this scenario, so the strip check proves nothing");
      }
      String title = GLFW.glfwGetWindowTitle(ctx.mc().getWindow().handle());
      if (title == null || !title.startsWith("uitest ") || !title.endsWith(" — " + id())) {
        throw new IllegalStateException("the window title '" + title + "' does not show the progress 'uitest n/N — " + id() + "'");
      }
      ctx.mc().setScreen(null);
    }

    @Override
    public void cleanup(UiTestContext ctx) {
      ctx.mc().setScreen(null);
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
      ctx.mc().setScreen(null);
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
