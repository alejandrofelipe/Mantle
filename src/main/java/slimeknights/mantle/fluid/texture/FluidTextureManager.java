package slimeknights.mantle.fluid.texture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Util;
import net.minecraft.core.Registry;
import net.neoforged.fml.ModLoader;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.mantle.Mantle;

import org.jspecify.annotations.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Manager for handling fluid textures.
 * <p>
 * 26.1: the fluid textures feed the fluid models ({@link MantleClientFluidTypes}), which the model manager bakes in its preparation phase, in parallel
 * with this listener. So the textures load from {@link #prepareSharedState}, which runs for every listener before any preparation starts, and the
 * fluid model registration waits for them through {@link #awaitTextures()}.
 */
public class FluidTextureManager implements PreparableReloadListener {
  /** Folder containing the logic */
  public static final String FOLDER = "mantle/fluid_texture";

  /* Instance data */
  private static final FluidTextureManager INSTANCE = new FluidTextureManager();
  /** Map of fluid type to texture */
  private volatile Map<FluidType,FluidTexture> textures = Collections.emptyMap();
  /** Textures of the current reload, loading in the background */
  private volatile CompletableFuture<Map<FluidType,FluidTexture>> pending = CompletableFuture.completedFuture(Collections.emptyMap());
  /** Fallback texture instance */
  private static final FluidTexture FALLBACK = new FluidTexture(Identifier.parse("block/water_still"), Identifier.parse("block/water_flow"), null, null, 0, -1, -1, false, false, 0, 0);

  private FluidTextureManager() {}


  /**
   * Initializes this manager, registering it with the resource manager
   */
  public static void init(AddClientReloadListenersEvent event) {
    event.addListener(Mantle.getResource("fluid_texture_manager"), INSTANCE);
  }

  @Override
  public void prepareSharedState(SharedState currentReload) {
    ResourceManager resourceManager = currentReload.resourceManager();
    // keep the old textures if mod loading failed, like the other safe listeners
    pending = CompletableFuture.supplyAsync(() -> ModLoader.hasErrors() ? textures : load(resourceManager), Util.backgroundExecutor());
  }

  @Override
  public CompletableFuture<Void> reload(SharedState currentReload, Executor taskExecutor, PreparationBarrier preparationBarrier, Executor reloadExecutor) {
    return pending.thenCompose(preparationBarrier::wait).thenAccept(map -> {});
  }

  /**
   * Waits for the fluid textures of the reload in progress, so {@link #getData(FluidType)} returns them while the model manager bakes the fluid models.
   * Outside a reload, returns immediately.
   */
  static void awaitTextures() {
    INSTANCE.pending.join();
  }

  /** Loads all fluid textures from the given resource manager, and makes them the current textures */
  private Map<FluidType,FluidTexture> load(ResourceManager resourceManager) {
    long time = System.nanoTime();
    // fetch JSONs
    Map<Identifier,JsonElement> jsons = new HashMap<>();
    SimpleJsonResourceReloadListener.scanDirectory(resourceManager, FileToIdConverter.json(FOLDER), JsonOps.INSTANCE, ExtraCodecs.JSON, jsons);

    // start building fluid type map
    Map<FluidType, FluidTexture> map = new HashMap<>();
    Registry<FluidType> fluidTypeRegistry = NeoForgeRegistries.FLUID_TYPES;


    for (Map.Entry<Identifier,JsonElement> entry : jsons.entrySet()) {
      Identifier id = entry.getKey();
      // first step is to find the matching fluid type, if there is none ignore the file
      FluidType type = fluidTypeRegistry.getValue(id);
      if (type == null || !id.equals(fluidTypeRegistry.getKey(type))) {
        Mantle.logger.debug("Ignoring fluid texture {} as no fluid type exists with that name", id);
      } else {
        // parse it if valid
        JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "fluid_texture");
        // 1.21.6: fog shape no longer exists, it is still accepted but ignored
        if (json.has("fog") && GsonHelper.getAsJsonObject(json, "fog").has("shape")) {
          Mantle.logger.warn("Fluid texture {} sets fog 'shape', which is no longer supported as of Minecraft 1.21.6 and will be ignored. Set just 'start' and 'end' instead", id);
        }
        map.put(type, FluidTexture.deserialize(json));
      }
    }
    this.textures = map;
    Mantle.logger.info("Loaded {} fluid textures in {} ms", map.size(), (System.nanoTime() - time) / 1000000f);
    return map;
  }

  /** Gets the texture for the given fluid */
  public static FluidTexture getData(FluidType fluid) {
    return INSTANCE.textures.getOrDefault(fluid, FALLBACK);
  }

  /** Gets the still texture for the given fluid */
  public static Identifier getStillTexture(FluidType fluid) {
    return getData(fluid).still();
  }

  /** Gets the flowing texture for the given fluid */
  public static Identifier getFlowingTexture(FluidType fluid) {
    return getData(fluid).flowing();
  }

  /** Gets the overlay texture for the given fluid */
  @Nullable
  public static Identifier getOverlayTexture(FluidType fluid) {
    return getData(fluid).overlay();
  }

  /** Gets the camera texture for the given fluid */
  @Nullable
  public static Identifier getCameraTexture(FluidType fluid) {
    return getData(fluid).camera();
  }

  /** Gets the still texture for the given fluid */
  public static int getColor(FluidType fluid) {
    return getData(fluid).color();
  }
}
