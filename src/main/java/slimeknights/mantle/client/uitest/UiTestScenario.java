package slimeknights.mantle.client.uitest;

import net.minecraft.resources.Identifier;

import java.util.Set;

/** One automated GUI capture: prepare a rig, open a screen, settle, screenshot, close. */
public interface UiTestScenario {
  /** Stable id; the last path segment names the PNG. */
  Identifier id();

  /**
   * Tags selecting this scenario with {@code -Dmantle.uitest.tags} (Gradle {@code -PuitestTags}): at least one, all known to
   * {@link UiTestTags}. A method rather than an annotation, so instances of one class can differ (e.g. one smoke instance).
   * The suite fails a scenario with no or unknown tags instead of running it.
   */
  Set<String> tags();

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

  /** Restore state; default closes any open screen. Runs only after a successful capture, see {@link #cleanup}. */
  default void close(UiTestContext ctx) {
    ctx.setScreen(null);
  }

  /**
   * Teardown that runs once on every exit path of the scenario: after {@link #close} on success, and after a failing phase,
   * a scenario timeout or the suite timeout. Unregister listeners and restore global state here so a failure can't leak
   * them into the following scenarios.
   */
  default void cleanup(UiTestContext ctx) {}
}
