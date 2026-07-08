package slimeknights.mantle.client.uitest;

import net.minecraft.resources.ResourceLocation;

/** One automated GUI capture: prepare a rig, open a screen, settle, screenshot, close. */
public interface UiTestScenario {
  /** Stable id; the last path segment names the PNG. */
  ResourceLocation id();

  /** Build the scene (server-side mutations go through {@link UiTestContext#runOnServer}). */
  void prepare(UiTestContext ctx);

  /** Ticks to wait after prepare before opening (multiblock formation, chunk/BE sync). */
  default int prepareSettleTicks() {
    return 60;
  }

  /** Open the target screen (or aim the camera for an in-world capture). */
  void open(UiTestContext ctx);

  /** Ticks to wait after open before capturing. */
  default int settleTicks() {
    return 20;
  }

  /** Restore state; default closes any open screen. */
  default void close(UiTestContext ctx) {
    ctx.mc().setScreen(null);
  }
}
