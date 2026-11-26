package slimeknights.mantle.client.uitest;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.apache.commons.io.FileUtils;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.render.RenderTargetCapture;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Client-tick state machine driving registered {@link UiTestScenario}s:
 * waits for the quick-play world, applies and verifies the world setup (music muted, mob spawning off, mobs discarded
 * and blocked from joining; recorded as {@link #SETUP_RESULT}), then per scenario prepare -> open -> capture -> close,
 * finally writes uitest-results.json and stops the client. Active only with -Dmantle.uitest=true.
 */
public class UiTestSuite {
  private enum Phase { WAIT_WORLD, SETUP, PREPARE_SETTLE, SETTLE, FINISH }

  /** Result key of the suite's own environment check (music muted, mobs disabled), recorded before the scenarios. */
  public static final String SETUP_RESULT = "mantle:suite_setup";
  /**
   * Entity tag (as in {@code /tag} or {@code Tags:[...]} in {@code /summon}) exempting a {@link Mob} from the suite's
   * mob blocking; a scenario that needs a mob adds it before spawning, e.g. {@code mob.addTag(ALLOW_MOB_TAG)}.
   */
  public static final String ALLOW_MOB_TAG = "mantle_uitest_allow_mob";
  /** Ticks with a player present before the suite starts (world/chunk warmup). */
  private static final int WORLD_WARMUP_TICKS = 100;
  /** Ticks between applying the world setup on the server and verifying it. */
  private static final int SETUP_SETTLE_TICKS = 20;
  /** Ceiling for the server to answer the setup verification. */
  private static final int SETUP_TIMEOUT_TICKS = 200;
  /** Radius around the player in which the client must see no mobs after setup. */
  private static final double SETUP_MOB_RADIUS = 128;
  /** Per-scenario hard ceiling; overruns record a fail and move on. */
  private static final int SCENARIO_TIMEOUT_TICKS = 20 * 60;
  /** Whole-suite ceiling; overruns finish (and report) whatever ran. */
  private static final int SUITE_TIMEOUT_TICKS = 20 * 60 * 10;

  private final Map<String, String> results = new LinkedHashMap<>();
  private Phase phase = Phase.WAIT_WORLD;
  private boolean finished = false;
  private int phaseTicks = 0;
  private int scenarioTicks = 0;
  private int suiteTicks = 0;
  private int index = 0;
  private List<UiTestScenario> scenarios;
  private File outputDir;
  /** Server-side half of the setup check: null until the server answered, empty when fine, else the problems. */
  private volatile String serverSetupCheck;

  public static void init() {
    NeoForge.EVENT_BUS.addListener(new UiTestSuite()::onClientTick);
    // keep captures deterministic: no mob ever joins a server level (saved mobs in lazily loaded chunks included)
    NeoForge.EVENT_BUS.addListener(UiTestSuite::onEntityJoinLevel);
    muteMusic(Minecraft.getInstance());
    Mantle.logger.info("uitest: suite armed; {} scenario(s) registered so far (registry is snapshotted at world load)",
      UiTestScenarios.all().size());
  }

  /** Silences the music source (only this run dir's options) and stops anything already playing; sound effects stay on. */
  private static void muteMusic(Minecraft mc) {
    mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
    mc.getMusicManager().stopPlaying();
  }

  private static boolean isBlockedMob(Entity entity) {
    return entity instanceof Mob && !entity.getTags().contains(ALLOW_MOB_TAG);
  }

  private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
    if (!event.getLevel().isClientSide() && isBlockedMob(event.getEntity())) {
      event.setCanceled(true);
    }
  }

  /** Server thread: disables mob spawning game rules and discards every mob already in a loaded level. */
  private static void applyWorldSetup(MinecraftServer server) {
    GameRules rules = server.overworld().getGameRules();
    for (GameRules.Key<GameRules.BooleanValue> rule : List.of(GameRules.RULE_DOMOBSPAWNING, GameRules.RULE_DO_PATROL_SPAWNING,
                                                              GameRules.RULE_DO_TRADER_SPAWNING, GameRules.RULE_DOINSOMNIA)) {
      rules.getRule(rule).set(false, server);
    }
    int discarded = 0;
    for (ServerLevel level : server.getAllLevels()) {
      List<Entity> mobs = new ArrayList<>();
      for (Entity entity : level.getAllEntities()) {
        if (isBlockedMob(entity)) {
          mobs.add(entity);
        }
      }
      mobs.forEach(Entity::discard);
      discarded += mobs.size();
    }
    Mantle.logger.info("uitest: world setup applied (mob spawning off, {} mob(s) discarded)", discarded);
  }

  /** Server thread: problems with the applied world setup, empty when fine. */
  private static String checkWorldSetup(MinecraftServer server) {
    List<String> problems = new ArrayList<>();
    if (server.overworld().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
      problems.add("doMobSpawning is true");
    }
    for (ServerLevel level : server.getAllLevels()) {
      int mobs = 0;
      for (Entity entity : level.getAllEntities()) {
        if (!entity.isRemoved() && isBlockedMob(entity)) {
          mobs++;
        }
      }
      if (mobs > 0) {
        problems.add(mobs + " mob(s) in " + level.dimension().identifier());
      }
    }
    return String.join("; ", problems);
  }

  /** Records {@link #SETUP_RESULT} from the server answer plus the client-side checks (music volume, visible mobs). */
  private void recordSetupResult(Minecraft mc) {
    List<String> problems = new ArrayList<>();
    String server = serverSetupCheck;
    if (server == null) {
      problems.add("server setup check timed out");
    } else if (!server.isEmpty()) {
      problems.add(server);
    }
    double music = mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get();
    if (music != 0) {
      problems.add("music volume is " + music);
    }
    if (mc.level != null && mc.player != null) {
      List<Mob> visible = mc.level.getEntitiesOfClass(Mob.class, mc.player.getBoundingBox().inflate(SETUP_MOB_RADIUS), UiTestSuite::isBlockedMob);
      if (!visible.isEmpty()) {
        problems.add(visible.size() + " mob(s) visible near the player: " + visible);
      }
    }
    if (problems.isEmpty()) {
      results.put(SETUP_RESULT, "ok");
    } else {
      String failure = "fail: " + String.join("; ", problems);
      Mantle.logger.error("uitest: suite setup check failed: {}", failure);
      results.put(SETUP_RESULT, failure);
    }
  }

  private void onClientTick(ClientTickEvent.Post event) {
    if (finished) {
      return; // client is stopping; ignore the few catch-up ticks the dying frame still delivers
    }
    Minecraft mc = Minecraft.getInstance();
    // Keep the framebuffer clean for captures: every in-world tick drop any queued/visible toasts
    // (e.g. the movement tutorial) and command feedback in chat (the scenarios' /tp). Doing this here
    // rather than inside capture() matters — a toast rendered on the previous frame would otherwise
    // still be on screen when the screenshot is taken.
    if (mc.level != null) {
      mc.getToastManager().clear();
      if (mc.gui != null) { // gui can be null very early in startup
        mc.gui.getChat().clearMessages(false);
      }
    }
    suiteTicks++;
    phaseTicks++;
    scenarioTicks++;
    if (suiteTicks > SUITE_TIMEOUT_TICKS && phase != Phase.FINISH) {
      results.put("suite", "fail: suite timeout");
      if (phase == Phase.PREPARE_SETTLE || phase == Phase.SETTLE) {
        cleanupScenario(mc); // the scenario in progress never reaches endScenario
      }
      enter(Phase.FINISH);
    }

    switch (phase) {
      case WAIT_WORLD -> {
        if (mc.level != null && mc.player != null && mc.screen == null) {
          if (phaseTicks >= WORLD_WARMUP_TICKS) {
            mc.getTutorial().setStep(TutorialSteps.NONE); // stop the movement tutorial toast at the source (also clears the active step)
            scenarios = filtered(UiTestScenarios.all());
            outputDir = new File(mc.gameDirectory, "uitest-screenshots");
            try {
              FileUtils.deleteDirectory(outputDir);
            } catch (Exception ignored) {}
            outputDir.mkdirs();
            muteMusic(mc); // re-assert in case something reset the option since arming
            MinecraftServer server = Objects.requireNonNull(mc.getSingleplayerServer(), "uitest: no integrated server");
            server.execute(() -> applyWorldSetup(server));
            enter(Phase.SETUP);
          }
        } else {
          phaseTicks = 0; // only count ticks while actually in-world at the title-free screen
        }
      }
      case SETUP -> {
        MinecraftServer server = mc.getSingleplayerServer();
        if (phaseTicks == SETUP_SETTLE_TICKS && server != null) {
          server.execute(() -> serverSetupCheck = checkWorldSetup(server));
        }
        if (phaseTicks > SETUP_SETTLE_TICKS && (serverSetupCheck != null || phaseTicks >= SETUP_TIMEOUT_TICKS)) {
          recordSetupResult(mc);
          startScenario(mc);
        }
      }
      case PREPARE_SETTLE -> {
        if (scenarioTimedOut(mc)) return;
        if (phaseTicks >= current().prepareSettleTicks()) {
          if (runGuarded(mc, "open", () -> current().open(new UiTestContext(mc)))) {
            enter(Phase.SETTLE);
          }
        }
      }
      case SETTLE -> {
        if (scenarioTimedOut(mc)) return;
        if (phaseTicks >= current().settleTicks()) {
          capture(mc);
        }
      }
      case FINISH -> finish(mc);
    }
  }

  /**
   * Applies the optional {@code -Dmantle.uitest.only=<tokens>} filter: keeps scenarios whose id path
   * contains any of the comma-separated (substring) tokens, so {@code station_} keeps every
   * {@code station_*} scenario. Unset/blank runs everything (default). An empty result is logged and
   * lets the suite finish gracefully rather than crash.
   */
  private static List<UiTestScenario> filtered(List<UiTestScenario> all) {
    String only = System.getProperty("mantle.uitest.only");
    if (only == null || only.isBlank()) {
      return all;
    }
    List<String> tokens = Arrays.stream(only.split(","))
      .map(String::trim).filter(s -> !s.isEmpty()).toList();
    List<UiTestScenario> kept = all.stream()
      .filter(s -> tokens.stream().anyMatch(t -> s.id().getPath().contains(t)))
      .toList();
    Mantle.logger.info("uitest: filtered to {} of {} scenario(s) via mantle.uitest.only={}",
      kept.size(), all.size(), only);
    if (kept.isEmpty()) {
      Mantle.logger.warn("uitest: mantle.uitest.only={} matched no scenarios; the suite will finish immediately", only);
    }
    return kept;
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
      // 1.21.5: Screenshot.takeScreenshot reads back asynchronously (callback on a later frame); capture synchronously so the
      // PNG is on disk before the scenario closes, and so the final scenario's file exists before finish() stops the client
      NativeImage image = RenderTargetCapture.capture(mc.getMainRenderTarget(), false);
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
    endScenario(mc);
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
      recordFailure(stage, t);
      endScenario(mc);
      return false;
    }
  }

  private void recordFailure(String stage, Throwable t) {
    Mantle.logger.error("uitest: {} failed during {}", current().id(), stage, t);
    results.put(current().id().toString(), "fail: " + stage + ": " + t);
  }

  /** Runs the current scenario's {@link UiTestScenario#cleanup}, the one teardown every exit path goes through. */
  private void cleanupScenario(Minecraft mc) {
    try {
      current().cleanup(new UiTestContext(mc));
    } catch (Throwable t) {
      // keep an earlier failure as the recorded cause, but never let a broken cleanup pass as ok
      String id = current().id().toString();
      if (!results.getOrDefault(id, "ok").startsWith("fail")) {
        recordFailure("cleanup", t);
      } else {
        Mantle.logger.error("uitest: {} failed during cleanup", id, t);
      }
    }
  }

  /** Ends the current scenario on any path (success, failed phase, timeout): cleans up, then starts the next one. */
  private void endScenario(Minecraft mc) {
    cleanupScenario(mc);
    index++;
    startScenario(mc);
  }

  private boolean scenarioTimedOut(Minecraft mc) {
    if (scenarioTicks > SCENARIO_TIMEOUT_TICKS) {
      results.put(current().id().toString(), "fail: timeout");
      endScenario(mc);
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
    finished = true;
    mc.stop();
  }
}
