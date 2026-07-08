package slimeknights.mantle.client.uitest;

import java.util.ArrayList;
import java.util.List;

/** Static scenario registry; consumers register during client setup when {@link #isActive()}. */
public final class UiTestScenarios {
  private static final List<UiTestScenario> SCENARIOS = new ArrayList<>();
  private UiTestScenarios() {}

  /** True only when the suite was requested on the command line via -Dmantle.uitest=true (JVM system property, not an env var). */
  public static boolean isActive() {
    return Boolean.getBoolean("mantle.uitest");
  }

  public static synchronized void register(UiTestScenario scenario) {
    SCENARIOS.add(scenario);
  }

  static synchronized List<UiTestScenario> all() {
    return List.copyOf(SCENARIOS);
  }
}
