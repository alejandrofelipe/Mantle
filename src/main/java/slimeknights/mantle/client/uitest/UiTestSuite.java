package slimeknights.mantle.client.uitest;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.google.common.base.Suppliers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.LoadingErrorScreen;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import slimeknights.mantle.Mantle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Client-tick state machine driving registered {@link UiTestScenario}s:
 * waits for the quick-play world, applies and verifies the world setup (music muted, mob spawning off, mobs discarded
 * and blocked from joining; recorded as {@link #SETUP_RESULT}), then per scenario prepare -> open -> capture -> close,
 * finally writes uitest-results.json and stops the client. Active only with -Dmantle.uitest=true.
 * <p>
 * Scenarios are selected by {@code mantle.uitest.only} / {@code mantle.uitest.tags} ({@link #select}); while the suite runs, OS mouse
 * and keyboard input is kept away from the game ({@link UiTestInput}) and the progress shows on the window ({@link UiTestProgress}),
 * never on a captured frame.
 */
public class UiTestSuite {
  private enum Phase { WAIT_WORLD, SETUP, PREPARE_SETTLE, SETTLE, FINISH }

  /** Folder (in the game directory) holding this run's PNGs. */
  static final String SCREENSHOT_DIR = "uitest-screenshots";
  /** Folder holding the previous run's PNGs, kept for side-by-side comparison; replaced on every run. */
  static final String PREVIOUS_SCREENSHOT_DIR = "uitest-screenshots-previous";

  /** Result key of the suite's own environment check (music muted, mobs disabled), recorded before the scenarios. */
  public static final String SETUP_RESULT = "mantle:suite_setup";
  /** Result key recorded only when the world needed a plain upgrade on load: a {@code warn:} result, which the build reports without failing. */
  public static final String WORLD_UPGRADE_RESULT = "mantle:world_upgrade";
  /** Result key recorded only when mod loading warned about one of our own mods: a {@code fail:} result. */
  public static final String MOD_LOADING_RESULT = "mantle:mod_loading";
  /** Result key recorded only when a filter keeps no scenario or {@code mantle.uitest.tags} names an unknown tag ({@link UiTestTags}): a {@code fail:} result. */
  public static final String FILTER_RESULT = "mantle:filter";
  /** Backup prompt titles of a plain world upgrade, the only prompts the suite confirms ({@code LevelSummary.BackupStatus}) */
  private static final Set<String> PLAIN_UPGRADE_PROMPTS = Set.of("selectWorld.backupQuestion.file_fixing_required", "selectWorld.backupQuestion.snapshot");
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
  /** Ticks a due capture waits for a frame rendered without the progress bar. */
  private static final int CLEAN_FRAME_TICKS = 10;
  /** Whole-suite ceiling; overruns finish (and report) whatever ran. */
  private static final int SUITE_TIMEOUT_TICKS = 20 * 60 * 10;

  private final Map<String, String> results = new LinkedHashMap<>();
  private Phase phase = Phase.WAIT_WORLD;
  private boolean finished = false;
  private int phaseTicks = 0;
  private int scenarioTicks = 0;
  private int suiteTicks = 0;
  /** Ticks of the current modifier key hold, and of all holds, see {@link #trackModifier} */
  private int heldTicks = 0;
  private int modifierTicks = 0;
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
    // OS mouse and keyboard input can't reach the game while the suite runs, restored in finish()
    UiTestInput.isolate(Minecraft.getInstance());
    UiTestProgress.init();
    UiTestScenarios.register(new UiTestSelfChecks.InputIsolationScenario());
    UiTestScenarios.register(new UiTestSelfChecks.ProgressHiddenScenario());
    UiTestScenarios.register(new UiTestSelfChecks.ClearInventoryScenario());
    Mantle.logger.info("uitest: suite armed; {} scenario(s) registered so far (registry is snapshotted at world load)",
      UiTestScenarios.all().size());
  }

  /** Silences the music source (only this run dir's options) and stops anything already playing; sound effects stay on. */
  private static void muteMusic(Minecraft mc) {
    mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
    mc.getMusicManager().stopPlaying();
  }

  private static boolean isBlockedMob(Entity entity) {
    return entity instanceof Mob && !entity.entityTags().contains(ALLOW_MOB_TAG);
  }

  private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
    if (!event.getLevel().isClientSide() && isBlockedMob(event.getEntity())) {
      event.setCanceled(true);
    }
  }

  /** Server thread: disables mob spawning game rules and discards every mob already in a loaded level. */
  private static void applyWorldSetup(MinecraftServer server) {
    GameRules rules = server.overworld().getGameRules();
    // 1.21.11: SPAWN_MONSTERS is a sub-gate of SPAWN_MOBS (natural spawning and ServerLevel#isSpawningMonsters both
    // require SPAWN_MOBS), so turning SPAWN_MOBS off already keeps monsters out
    for (GameRule<Boolean> rule : List.of(GameRules.SPAWN_MOBS, GameRules.SPAWN_PATROLS,
                                          GameRules.SPAWN_WANDERING_TRADERS, GameRules.SPAWN_PHANTOMS)) {
      rules.set(rule, false, server);
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
    if (server.overworld().getGameRules().get(GameRules.SPAWN_MOBS)) {
      problems.add("spawn_mobs is true");
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
    // Shift, Ctrl and Alt are polled from the GLFW key state (Minecraft#hasShiftDown...), out of reach of the input isolation: while one is
    // held over the window the scenario clock stops, so a scenario neither opens nor captures with it (the suite timeout still runs)
    boolean held = (phase == Phase.PREPARE_SETTLE || phase == Phase.SETTLE) && (mc.hasShiftDown() || mc.hasControlDown() || mc.hasAltDown());
    trackModifier(held);
    if (!held) {
      phaseTicks++;
      scenarioTicks++;
    }
    if (suiteTicks > SUITE_TIMEOUT_TICKS && phase != Phase.FINISH) {
      results.put("suite", suiteTimeoutResult(modifierTicks));
      if (phase == Phase.PREPARE_SETTLE || phase == Phase.SETTLE) {
        cleanupScenario(mc); // the scenario in progress never reaches endScenario
      }
      enter(Phase.FINISH);
    } else if (held) {
      if (phase == Phase.SETTLE) {
        phaseTicks = heldPhaseTicks(phaseTicks, current().settleTicks());
      }
      // the bar on the held frames makes the capture wait for a frame rendered after the release (UiTestProgress#lastFrameClean)
      UiTestProgress.setHidden(false);
      return;
    }

    switch (phase) {
      case WAIT_WORLD -> {
        Screen screen = new UiTestContext(mc).screen();
        if (mc.level != null && mc.player != null && screen == null) {
          if (phaseTicks >= WORLD_WARMUP_TICKS) {
            mc.getTutorial().setStep(TutorialSteps.NONE); // stop the movement tutorial toast at the source (also clears the active step)
            scenarios = filtered(UiTestScenarios.all());
            outputDir = new File(mc.gameDirectory, SCREENSHOT_DIR);
            rotateScreenshots(outputDir, new File(mc.gameDirectory, PREVIOUS_SCREENSHOT_DIR));
            outputDir.mkdirs();
            muteMusic(mc); // re-assert in case something reset the option since arming
            MinecraftServer server = Objects.requireNonNull(mc.getSingleplayerServer(), "uitest: no integrated server");
            server.execute(() -> applyWorldSetup(server));
            enter(Phase.SETUP);
          }
        } else {
          phaseTicks = 0; // only count ticks while actually in-world at the title-free screen
          if (screen instanceof LoadingErrorScreen loadingScreen) {
            handleLoadingScreen(loadingScreen);
            return;
          }
          if (screen instanceof BackupConfirmScreen backupScreen) {
            handleBackupScreen(backupScreen);
            return;
          }
          // a screen that blocks quick play (world upgrade prompt, error screen, ...) shows in the log instead of a silent hang
          if (suiteTicks % 200 == 0) {
            Mantle.logger.info("uitest: waiting for the world, screen {} ({})", screen == null ? "none" : screen.getClass().getName(),
              screen == null ? "" : screen.getTitle().getString());
          }
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
        int settle = current().settleTicks();
        // the progress bar is hidden from the tick before; wait for a frame rendered without it (several ticks can run per frame),
        // past the wait the capture itself fails if the bar is still on the frame
        if (phaseTicks >= settle && (UiTestProgress.lastFrameClean() || phaseTicks >= settle + CLEAN_FRAME_TICKS)) {
          capture(mc);
        }
      }
      case FINISH -> finish(mc);
    }
    if (!finished) {
      // hide the bar for the frames before a capture: the scenarios read the captured frame again in close()
      UiTestProgress.setHidden(phase == Phase.SETTLE && phaseTicks >= current().settleTicks() - 1);
    }
  }

  /** Logs a modifier key hold once when it starts and once when it ends, and counts the held ticks for the suite timeout */
  private void trackModifier(boolean held) {
    if (held) {
      if (heldTicks == 0) {
        Mantle.logger.warn("uitest: Shift, Ctrl or Alt is held over the window; {} waits until it is released", current().id());
      }
      heldTicks++;
      modifierTicks++;
    } else if (heldTicks > 0) {
      Mantle.logger.info("uitest: modifier key released after {} tick(s)", heldTicks);
      heldTicks = 0;
    }
  }

  /**
   * Phase ticks in SETTLE after a tick with a modifier key held, which doesn't advance them: back to {@code settle - 1} at most, the tick
   * before the capture, so the capture comes again only after the release and, as the held frames show the progress bar, on a frame
   * rendered after it.
   */
  static int heldPhaseTicks(int phaseTicks, int settleTicks) {
    return Math.min(phaseTicks, settleTicks - 1);
  }

  /** The {@code suite} result of a suite timeout, naming the ticks a modifier key held the scenarios back */
  static String suiteTimeoutResult(int modifierTicks) {
    return "fail: suite timeout" + (modifierTicks > 0 ? " (Shift, Ctrl or Alt was held over the window for " + modifierTicks + " tick(s), pausing the scenarios)" : "");
  }

  /**
   * Drops the scenarios whose tags are missing or unknown ({@link UiTestTags#problems}), recording a {@code fail:} result for each,
   * then applies the optional filters (see {@link #select}). An empty result lets the suite finish gracefully rather than crash; when a
   * filter is set it also fails {@link #FILTER_RESULT}, as does a tag filter naming an unknown tag ({@link #filterFailure}).
   */
  private List<UiTestScenario> filtered(List<UiTestScenario> all) {
    Set<String> known = UiTestTags.known();
    List<UiTestScenario> valid = new ArrayList<>();
    for (UiTestScenario scenario : all) {
      List<String> problems = UiTestTags.problems(scenario.tags(), known);
      if (problems.isEmpty()) {
        valid.add(scenario);
      } else {
        Mantle.logger.error("uitest: {} not run, invalid tags {}: {}", scenario.id(), scenario.tags(), problems);
        results.put(scenario.id().toString(), "fail: tags: " + String.join("; ", problems));
      }
    }
    String only = System.getProperty("mantle.uitest.only");
    String tags = System.getProperty("mantle.uitest.tags");
    List<UiTestScenario> kept = select(valid, only, tags);
    if (!tokens(only).isEmpty() || !tokens(tags).isEmpty()) {
      Mantle.logger.info("uitest: filtered to {} of {} scenario(s) via mantle.uitest.only={}, mantle.uitest.tags={}",
        kept.size(), valid.size(), only, tags);
    }
    String failure = filterFailure(kept.size(), only, tags, known);
    if (failure != null) {
      Mantle.logger.error("uitest: {}", failure);
      results.put(FILTER_RESULT, failure);
    }
    if (kept.isEmpty()) {
      Mantle.logger.warn("uitest: no scenario to run; the suite will finish immediately");
    }
    return kept;
  }

  /**
   * The {@link #FILTER_RESULT} failure of the filters, null when fine: a tag filter naming an unknown tag, or any filter keeping no scenario,
   * so a typo or an empty intersection can't pass as a green run of nothing.
   * @param kept   Number of scenarios the filters kept ({@link #select})
   * @param known  Known tags ({@link UiTestTags#known()})
   */
  public static @Nullable String filterFailure(int kept, @Nullable String only, @Nullable String tags, Set<String> known) {
    List<String> problems = new ArrayList<>();
    List<String> unknown = tokens(tags).stream().filter(tag -> !known.contains(tag)).toList();
    if (!unknown.isEmpty()) {
      problems.add("unknown tag(s) " + unknown + " in mantle.uitest.tags, known: " + known);
    }
    if (kept == 0 && (!tokens(only).isEmpty() || !tokens(tags).isEmpty())) {
      problems.add("mantle.uitest.only=" + only + ", mantle.uitest.tags=" + tags + " matched no scenarios");
    }
    return problems.isEmpty() ? null : "fail: " + String.join("; ", problems);
  }

  /**
   * Keeps the last two runs' PNGs: the previous run's {@code current} folder becomes {@code previous} (replacing the older
   * copy) before this run writes its own. With no previous run, {@code previous} is left alone. If the move fails (e.g. a
   * PNG open in a viewer), {@code current} is cleared so this run never mixes with an older one.
   */
  static void rotateScreenshots(File current, File previous) {
    if (!current.isDirectory()) {
      return;
    }
    try {
      FileUtils.deleteDirectory(previous);
      Files.move(current.toPath(), previous.toPath());
    } catch (IOException e) {
      Mantle.logger.warn("uitest: could not keep the previous run's screenshots in {}, clearing them instead", previous, e);
      try {
        FileUtils.deleteDirectory(current);
      } catch (IOException ignored) {}
    }
  }

  /**
   * Applies the optional filters, both when both are given (intersection). Unset or blank runs everything (default).
   * <ul>
   *   <li>{@code only}: comma-separated substrings, keeps scenarios whose id path contains any of them ({@code station_} keeps every {@code station_*})</li>
   *   <li>{@code tags}: comma-separated tags, keeps scenarios having any of them ({@code jei,compat})</li>
   * </ul>
   */
  public static List<UiTestScenario> select(List<UiTestScenario> all, @Nullable String only, @Nullable String tags) {
    List<String> paths = tokens(only);
    List<String> wanted = tokens(tags);
    return all.stream()
      .filter(s -> paths.isEmpty() || paths.stream().anyMatch(t -> s.id().getPath().contains(t)))
      .filter(s -> wanted.isEmpty() || wanted.stream().anyMatch(s.tags()::contains))
      .toList();
  }

  /** Splits a comma-separated filter property, empty for unset or blank */
  private static List<String> tokens(@Nullable String property) {
    if (property == null || property.isBlank()) {
      return List.of();
    }
    return Arrays.stream(property.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
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
    UiTestProgress.update(index, scenarios.size(), current().id().toString());
    // each scenario starts with the cursor centered, where vanilla put it when grabbing and releasing it before OS input was isolated
    new UiTestContext(mc).centerMouse();
    if (runGuarded(mc, "prepare", () -> current().prepare(new UiTestContext(mc)))) {
      enter(Phase.PREPARE_SETTLE);
    }
  }

  private void capture(Minecraft mc) {
    if (!runGuarded(mc, "capture", () -> {
      // 1.21.5: Screenshot.takeScreenshot reads back asynchronously (callback on a later frame); capture synchronously so the
      // PNG is on disk before the scenario closes, and so the final scenario's file exists before finish() stops the client
      NativeImage image = new UiTestContext(mc).captureFrame();
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

  /**
   * The committed world may be saved by an older version, which opens a backup prompt before quick play continues. A plain upgrade
   * ({@code file_fixing_required}, or to a snapshot) proceeds without a backup (the run dir copy is re-extracted every run) and is recorded as
   * a {@code warn:} result under {@link #WORLD_UPGRADE_RESULT}, so the committed world gets upgraded once. Any other prompt (a downgrade, which
   * vanilla marks severe, or an experimental or customized world) fails {@link #SETUP_RESULT} and ends the run instead of opening that world.
   */
  private void handleBackupScreen(BackupConfirmScreen screen) {
    String title = screen.getTitle().getString();
    String key = screen.getTitle().getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
    if (!isPlainUpgradePrompt(key)) {
      Mantle.logger.error("uitest: world prompt '{}' ({}); only a plain upgrade proceeds", title, key);
      results.put(SETUP_RESULT, "fail: world prompt '" + title + "' (" + key + "), only a plain upgrade proceeds");
      enter(Phase.FINISH);
      return;
    }
    Mantle.logger.warn("uitest: {}; upgrading the world without a backup", title);
    results.put(WORLD_UPGRADE_RESULT, "warn: the world was upgraded without a backup (" + key + "); commit an upgraded uitest world");
    try {
      ((BackupConfirmScreen.Listener) BACKUP_PROCEED.get().get(screen)).proceed(false, false);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("uitest: cannot confirm the world upgrade", e);
    }
  }

  /** Checks if a backup prompt, by the translation key of its title, is a plain world upgrade the suite may confirm */
  public static boolean isPlainUpgradePrompt(String titleKey) {
    return PLAIN_UPGRADE_PROMPTS.contains(titleKey);
  }

  /**
   * Formats the mod loading issues attributed to one of the given mods, others are skipped. An issue is attributed by its mod
   * ({@link ModLoadingIssue#affectedMod()}) or, when it names none, by its mod file ({@link ModLoadingIssue#affectedModFile()}, any of the file's mods).
   */
  public static List<String> ownModWarnings(List<ModLoadingIssue> issues, Set<String> ownMods) {
    List<String> own = new ArrayList<>();
    for (ModLoadingIssue issue : issues) {
      String mod = null;
      if (issue.affectedMod() != null) {
        String id = issue.affectedMod().getModId();
        if (ownMods.contains(id)) {
          mod = id;
        }
      } else if (issue.affectedModFile() != null) {
        for (IModInfo info : issue.affectedModFile().getModInfos()) {
          if (ownMods.contains(info.getModId())) {
            mod = info.getModId();
            break;
          }
        }
      }
      if (mod != null) {
        own.add(mod + ": " + issue.translationKey() + " " + issue.translationArgs());
      }
    }
    return own;
  }

  /**
   * NeoForge shows mod loading warnings on a screen before quick play continues (26.1: e.g. any mod still declaring the deprecated
   * {@code logoFile}, third party mods included), which would stall the suite until its timeout. Logs every issue; errors fail the run,
   * warnings from our own mods (Mantle and the mods registering scenarios) fail {@link #MOD_LOADING_RESULT}, other warnings proceed like the
   * screen's button.
   */
  private void handleLoadingScreen(LoadingErrorScreen screen) {
    try {
      List<?> errors = (List<?>) LoadingScreenFields.ERRORS.get(screen);
      List<?> warnings = (List<?>) LoadingScreenFields.WARNINGS.get(screen);
      warnings.forEach(issue -> Mantle.logger.warn("uitest: mod loading warning: {}", issue));
      if (!errors.isEmpty()) {
        errors.forEach(issue -> Mantle.logger.error("uitest: mod loading error: {}", issue));
        results.put(SETUP_RESULT, "fail: " + errors.size() + " mod loading error(s), see the log");
        enter(Phase.FINISH);
        return;
      }
      Set<String> ownMods = new HashSet<>();
      ownMods.add(Mantle.modId);
      UiTestScenarios.all().forEach(scenario -> ownMods.add(scenario.id().getNamespace()));
      List<ModLoadingIssue> issues = new ArrayList<>();
      for (Object formatted : warnings) {
        issues.add((ModLoadingIssue) LoadingScreenFields.ISSUE.get().invoke(formatted));
      }
      List<String> own = ownModWarnings(issues, ownMods);
      if (!own.isEmpty()) {
        results.put(MOD_LOADING_RESULT, "fail: " + own.size() + " mod loading warning(s) from our mods: " + String.join("; ", own));
      }
      ((Runnable) LoadingScreenFields.NEXT.get(screen)).run();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("uitest: cannot read the mod loading screen", e);
    }
  }

  /** {@link BackupConfirmScreen#onProceed}, resolved once on first use */
  private static final Supplier<Field> BACKUP_PROCEED = Suppliers.memoize(() -> {
    try {
      Field field = BackupConfirmScreen.class.getDeclaredField("onProceed");
      field.setAccessible(true);
      return field;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("uitest: BackupConfirmScreen has no field onProceed", e);
    }
  });

  /** {@link LoadingErrorScreen} fields, resolved once on first use */
  private static final class LoadingScreenFields {
    private static final Field ERRORS = field("modLoadErrors");
    private static final Field WARNINGS = field("modLoadWarnings");
    private static final Field NEXT = field("nextScreenTask");
    /** Accessor of the package private {@code FormattedIssue} record the issue lists hold, resolved on the first warning */
    private static final Supplier<Method> ISSUE = Suppliers.memoize(() -> {
      try {
        Method method = Class.forName(LoadingErrorScreen.class.getName() + "$FormattedIssue").getDeclaredMethod("issue");
        method.setAccessible(true);
        return method;
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("uitest: LoadingErrorScreen.FormattedIssue has no method issue", e);
      }
    });

    private static Field field(String name) {
      try {
        Field field = LoadingErrorScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("uitest: LoadingErrorScreen has no field " + name, e);
      }
    }
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
    // every way out of the suite (done, suite timeout, a failed setup) ends here
    UiTestInput.restore(mc);
    UiTestProgress.stop(mc);
    finished = true;
    mc.stop();
  }
}
