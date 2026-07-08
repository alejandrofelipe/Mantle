package slimeknights.mantle.client.uitest;

import java.util.ArrayList;
import java.util.List;

/** Static scenario registry; consumers register during client setup when {@link #isActive()}. */
public final class UiTestScenarios {
  private static final List<UiTestScenario> SCENARIOS = new ArrayList<>();
  private UiTestScenarios() {}

  /** True only when the suite was requested on the command line. */
  public static boolean isActive() {
    return Boolean.getBoolean("mantle.uitest");
  }

  public static void register(UiTestScenario scenario) {
    SCENARIOS.add(scenario);
  }

  static List<UiTestScenario> all() {
    return List.copyOf(SCENARIOS);
  }
}
