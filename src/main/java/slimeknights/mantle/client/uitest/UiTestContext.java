package slimeknights.mantle.client.uitest;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.Objects;

/** Helpers handed to scenarios; client-thread unless noted. */
public record UiTestContext(Minecraft mc) {
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

  /** Sends a command as the player (cheats are on in the uitest world). */
  public void sendCommand(String command) {
    player().connection.sendCommand(command);
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
