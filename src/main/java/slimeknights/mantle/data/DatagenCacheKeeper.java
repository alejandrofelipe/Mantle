package slimeknights.mantle.data;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent.DataGeneratorConfig;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Keeps the outputs of the other data run. Since 1.21.4 NeoForge splits datagen into a client run ({@code clientData}, assets) and a server
 * run ({@code serverData}, data) writing to the same folder, but the vanilla data cache deletes every file in that folder that no provider
 * of the current run produced, so each run deleted the other run's outputs.
 * <p>
 * The data cache keeps the files of providers that are registered but not run ({@link DataGenerator#addProvider(boolean, DataProvider)}
 * with {@code false}), so {@link #keepOtherRunOutputs(GatherDataEvent)} registers such a placeholder for every provider with a cache file
 * in the output folder that the current run does not have. Call it from a {@link net.neoforged.bus.api.EventPriority#LOWEST lowest}
 * priority listener of both {@link GatherDataEvent.Client} and {@link GatherDataEvent.Server}, after every provider is registered.
 * <p>
 * It relies on the cache files under {@code .cache} in the output folder: when they are missing (e.g. not committed, on a fresh clone),
 * a run still deletes the other run's files until both runs ran once. A provider that is removed or renamed keeps its files until its
 * cache file is deleted.
 * <p>
 * Runs generating several mods into one folder ({@code --flat}) are left alone: NeoForge gives each mod its own generator and merges
 * those sharing a folder before running, which fails on a placeholder standing for a provider of another of those mods.
 */
public final class DatagenCacheKeeper {
  /** Start of the first line of a cache file, followed by the version, the date and the provider name separated by tabs */
  private static final String HEADER = "// ";

  private DatagenCacheKeeper() {}

  /** Registers a placeholder for every cached provider the current run lacks, so the run keeps its files */
  public static void keepOtherRunOutputs(GatherDataEvent event) {
    if (sharesFolderWithOtherMods(event)) {
      Mantle.logger.info("Data run for several mods into one folder, the other data run's outputs will be deleted");
      return;
    }
    keepOtherRunOutputs(event.getGenerator());
  }

  /**
   * Checks if the event's generator is merged with the generators of other mods before running. The event does not expose its config, so it is
   * read by reflection; if that fails, assumes it is shared.
   */
  @VisibleForTesting
  static boolean sharesFolderWithOtherMods(GatherDataEvent event) {
    DataGeneratorConfig config;
    try {
      Field field = GatherDataEvent.class.getDeclaredField("config");
      field.setAccessible(true);
      config = (DataGeneratorConfig) field.get(event);
    } catch (ReflectiveOperationException | RuntimeException e) {
      Mantle.logger.warn("Failed to read the data generator config, not keeping the other data run's outputs", e);
      return true;
    }
    return config == null || config.getMods().size() > 1 && config.isFlat();
  }

  /** Registers a placeholder in the given generator for every cached provider it lacks */
  @VisibleForTesting
  static void keepOtherRunOutputs(DataGenerator generator) {
    Path cacheFolder = generator.getPackOutput().getOutputFolder().resolve(".cache");
    if (!Files.isDirectory(cacheFolder)) {
      return;
    }
    // fast path: the providers to run; a provider registered without running (e.g. addProvider(false, ...)) is not in this view,
    // registering again throws, see below
    Set<String> registered = generator.getProvidersView().keySet();
    List<Path> cacheFiles;
    try (Stream<Path> files = Files.list(cacheFolder)) {
      cacheFiles = files.filter(Files::isRegularFile).toList();
    } catch (IOException e) {
      Mantle.logger.error("Failed to list the data cache folder {}, the other data run's outputs will be deleted", cacheFolder, e);
      return;
    }
    for (Path cacheFile : cacheFiles) {
      String name = readProviderName(cacheFile);
      if (name != null && !registered.contains(name)) {
        try {
          generator.addProvider(false, new Placeholder(name));
        } catch (IllegalStateException e) {
          // "Duplicate provider": registered but not run (only the providers to run are visible), so its files are kept already
        }
      }
    }
  }

  /** Reads the provider name from the header of a cache file, null if it has none */
  @Nullable
  private static String readProviderName(Path cacheFile) {
    try (BufferedReader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
      String header = reader.readLine();
      if (header != null && header.startsWith(HEADER)) {
        String[] parts = header.substring(HEADER.length()).split("\t", 3);
        if (parts.length == 3) {
          return parts[2];
        }
      }
    } catch (IOException e) {
      Mantle.logger.error("Failed to read the data cache file {}", cacheFile, e);
    }
    return null;
  }

  /** Provider that is never run, it only stands for a provider of the other data run */
  private static class Placeholder implements DataProvider {
    private final String name;

    private Placeholder(String name) {
      this.name = name;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public String getName() {
      return name;
    }
  }
}
