package slimeknights.mantle.client.uitest;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import slimeknights.mantle.client.render.RenderTargetCapture;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.UUID;

/**
 * Helpers handed to scenarios; client-thread unless noted. Scenarios reach the screen and the main render target through these accessors
 * rather than {@link Minecraft} directly, so a version that moves them (26.2: {@code Minecraft#screen} to {@code Gui#screen()}) changes one place.
 */
public record UiTestContext(Minecraft mc) {
  /** Context of the running client, for code without one handed in (tick listeners a scenario registers) */
  public static UiTestContext current() {
    return new UiTestContext(Minecraft.getInstance());
  }

  /** The open screen, null in the world */
  @Nullable
  public Screen screen() {
    return mc.gui.screen();
  }

  /** Opens the screen, or closes the open one with null */
  public void setScreen(@Nullable Screen screen) {
    mc.gui.setScreen(screen);
  }

  /** The render target the game draws its frames into */
  public RenderTarget mainRenderTarget() {
    return mc.gameRenderer.mainRenderTarget();
  }

  public LocalPlayer player() {
    return Objects.requireNonNull(mc.player, "uitest: no player");
  }

  /** The integrated server's overworld (uitest always runs singleplayer via quick play). */
  public ServerLevel serverLevel() {
    MinecraftServer server = Objects.requireNonNull(mc.getSingleplayerServer(), "uitest: no integrated server");
    return server.overworld();
  }

  /** Schedules work on the server thread (rig building, BE mutation). */
  public void runOnServer(Runnable work) {
    Objects.requireNonNull(mc.getSingleplayerServer()).execute(work);
  }

  /**
   * Sends a command as the player (cheats are on in the uitest world). A command takes a longer way to the server than {@link #runOnServer}
   * (network thread first, then the server queue), so work scheduled with {@link #runOnServer} right after it, also by the next scenario's
   * {@code prepare} in the same tick, can run before the command. Use {@link #clearInventory} rather than {@code clear @s}.
   */
  public void sendCommand(String command) {
    player().connection.sendCommand(command);
  }

  /**
   * Empties the player's inventory on the server like {@code /clear @s} (armor, offhand, 2x2 crafting grid and the carried stack included),
   * scheduled through {@link #runOnServer}, so it runs in order with the server work scheduled before and after it.
   */
  public void clearInventory() {
    UUID id = player().getUUID();
    runOnServer(() -> {
      ServerPlayer player = Objects.requireNonNull(serverLevel().getServer().getPlayerList().getPlayer(id), "uitest: no server player");
      player.getInventory().clearOrCountMatchingItems(stack -> true, -1, player.inventoryMenu.getCraftSlots());
      player.containerMenu.broadcastChanges();
      player.inventoryMenu.slotsChanged(player.getInventory());
    });
  }

  /** Client-side block interaction — opens the block's real menu with real server data. Always hits the NORTH face at block center; not suitable for face- or hit-position-sensitive interactions (e.g. channels). */
  public void useBlock(BlockPos pos) {
    Vec3 hit = Vec3.atCenterOf(pos);
    Objects.requireNonNull(mc.gameMode).useItemOn(player(), InteractionHand.MAIN_HAND,
      new BlockHitResult(hit, Direction.NORTH, pos, false));
  }

  /** Aims the player camera at a position (for in-world captures). */
  public void lookAt(BlockPos pos) {
    player().lookAt(Anchor.EYES, Vec3.atCenterOf(pos));
  }

  /**
   * Moves the GUI mouse position to the top left corner of the window. Screens open with the cursor centered, where it
   * shows hover tooltips over whatever sits in the middle of the capture. Sets the handler's position directly rather than
   * moving the OS cursor, which GLFW ignores while the window is unfocused.
   */
  public void parkMouse() {
    setMouse(0, 0);
  }

  /**
   * Moves the mouse to a position in GUI coordinates, for code that reads the cursor from {@link MouseHandler} instead of
   * taking it as a parameter (e.g. key handlers looking up what is under the mouse). Like {@link #parkMouse()}, sets the
   * handler's position in window pixels directly, as the exact inverse of {@link MouseHandler#getScaledXPos} and
   * {@link MouseHandler#getScaledYPos} (window size over GUI scaled size, not the rounded GUI scale).
   */
  public void moveMouse(double guiX, double guiY) {
    Window window = mc.getWindow();
    setMouse(guiX * window.getScreenWidth() / window.getGuiScaledWidth(), guiY * window.getScreenHeight() / window.getGuiScaledHeight());
  }

  /**
   * Reads the last rendered frame from the main render target, e.g. for pixel asserts in {@link UiTestScenario#close} on the frame the
   * suite just captured. Use this rather than {@link RenderTargetCapture} directly: it fails if that frame shows the suite's progress
   * bar, which must never reach a pixel read. Caller closes the image.
   */
  public NativeImage captureFrame() {
    UiTestProgress.checkCapture();
    return RenderTargetCapture.capture(mainRenderTarget(), false);
  }

  /** Moves the mouse to the window center, where each scenario starts */
  void centerMouse() {
    Window window = mc.getWindow();
    setMouse(window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0);
  }

  private void setMouse(double x, double y) {
    try {
      MouseFields.X.setDouble(mc.mouseHandler, x);
      MouseFields.Y.setDouble(mc.mouseHandler, y);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("uitest: failed to move the mouse", e);
    }
  }

  /** {@link MouseHandler} position fields, resolved once on first use (scenarios may park the mouse every tick). */
  private static final class MouseFields {
    private static final Field X = field("xpos");
    private static final Field Y = field("ypos");

    private static Field field(String name) {
      try {
        Field field = MouseHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("uitest: MouseHandler has no field " + name, e);
      }
    }
  }
}
