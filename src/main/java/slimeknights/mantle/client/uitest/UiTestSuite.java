package slimeknights.mantle.client.uitest;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.commons.io.FileUtils;
import slimeknights.mantle.Mantle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-tick state machine driving registered {@link UiTestScenario}s:
 * waits for the quick-play world, then per scenario prepare -> open -> capture -> close,
 * finally writes uitest-results.json and stops the client. Active only with -Dmantle.uitest=true.
 */
public class UiTestSuite {
  private enum Phase { WAIT_WORLD, PREPARE, PREPARE_SETTLE, OPEN, SETTLE, CAPTURE, CLOSE, FINISH }

  /** Ticks with a player present before the suite starts (world/chunk warmup). */
  private static final int WORLD_WARMUP_TICKS = 100;
  /** Per-scenario hard ceiling; overruns record a fail and move on. */
  private static final int SCENARIO_TIMEOUT_TICKS = 20 * 60;
  /** Whole-suite ceiling; overruns finish (and report) whatever ran. */
  private static final int SUITE_TIMEOUT_TICKS = 20 * 60 * 10;

  public static void init() {
    NeoForge.EVENT_BUS.addListener(new UiTestSuite()::onClientTick);
    Mantle.logger.info("uitest: suite armed, {} scenario(s) will run once the world loads",
      UiTestScenarios.all().size());
  }

  private final Map<String, String> results = new LinkedHashMap<>();
  private Phase phase = Phase.WAIT_WORLD;
  private int phaseTicks = 0;
  private int scenarioTicks = 0;
  private int suiteTicks = 0;
  private int index = 0;
  private List<UiTestScenario> scenarios;
  private File outputDir;

  private void onClientTick(ClientTickEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    suiteTicks++;
    phaseTicks++;
    scenarioTicks++;
    if (suiteTicks > SUITE_TIMEOUT_TICKS && phase != Phase.FINISH) {
      results.put("suite", "fail: suite timeout");
      enter(Phase.FINISH);
    }

    switch (phase) {
      case WAIT_WORLD -> {
        if (mc.level != null && mc.player != null && mc.screen == null) {
          if (phaseTicks >= WORLD_WARMUP_TICKS) {
            scenarios = UiTestScenarios.all();
            outputDir = new File(mc.gameDirectory, "uitest-screenshots");
            try {
              FileUtils.deleteDirectory(outputDir);
            } catch (Exception ignored) {}
            outputDir.mkdirs();
            startScenario(mc);
          }
        } else {
          phaseTicks = 0; // only count ticks while actually in-world at the title-free screen
        }
      }
      case PREPARE_SETTLE -> {
        if (scenarioTimedOut()) return;
        if (phaseTicks >= current().prepareSettleTicks()) {
          if (runGuarded(mc, "open", () -> current().open(new UiTestContext(mc)))) {
            enter(Phase.SETTLE);
          }
        }
      }
      case SETTLE -> {
        if (scenarioTimedOut()) return;
        if (phaseTicks >= current().settleTicks()) {
          capture(mc);
        }
      }
      case FINISH -> finish(mc);
      default -> {}
    }
  }

  private UiTestScenario current() {
    return scenarios.get(index);
  }

  private void startScenario(Minecraft mc) {
    if (scenarios.isEmpty() || index >= scenarios.size()) {
      enter(Phase.FINISH);
      return;
    }
    scenarioTicks = 0;
    Mantle.logger.info("uitest: [{}/{}] {}", index + 1, scenarios.size(), current().id());
    if (runGuarded(mc, "prepare", () -> current().prepare(new UiTestContext(mc)))) {
      enter(Phase.PREPARE_SETTLE);
    }
  }

  private void capture(Minecraft mc) {
    if (!runGuarded(mc, "capture", () -> {
      NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
      try (image) {
        image.writeToFile(new File(outputDir, current().id().getPath() + ".png"));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      results.putIfAbsent(current().id().toString(), "ok");
    })) {
      return;
    }
    if (!runGuarded(mc, "close", () -> current().close(new UiTestContext(mc)))) {
      return;
    }
    index++;
    startScenario(mc);
  }

  /**
   * Runs a scenario phase, converting any throwable into a fail result and advancing to the next scenario.
   * Returns false when it failed (and already advanced) so the caller can bail out instead of also advancing
   * or acting on the now-stale {@link #current()} scenario.
   */
  private boolean runGuarded(Minecraft mc, String stage, Runnable work) {
    try {
      work.run();
      return true;
    } catch (Throwable t) {
      Mantle.logger.error("uitest: {} failed during {}", current().id(), stage, t);
      results.put(current().id().toString(), "fail: " + stage + ": " + t);
      index++;
      startScenario(mc);
      return false;
    }
  }

  private boolean scenarioTimedOut() {
    if (scenarioTicks > SCENARIO_TIMEOUT_TICKS) {
      results.put(current().id().toString(), "fail: timeout");
      index++;
      startScenario(Minecraft.getInstance());
      return true;
    }
    return false;
  }

  private void enter(Phase next) {
    phase = next;
    phaseTicks = 0;
  }

  private void finish(Minecraft mc) {
    try {
      File resultFile = new File(mc.gameDirectory, "uitest-results.json");
      Files.writeString(resultFile.toPath(),
        new GsonBuilder().setPrettyPrinting().create().toJson(results), StandardCharsets.UTF_8);
      Mantle.logger.info("uitest: done — {} result(s) written to {}", results.size(), resultFile);
    } catch (Exception e) {
      Mantle.logger.error("uitest: failed to write results", e);
    }
    phase = Phase.WAIT_WORLD; // prevent re-entry while stopping
    mc.stop();
  }
}
