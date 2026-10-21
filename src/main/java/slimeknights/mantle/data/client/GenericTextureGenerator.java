package slimeknights.mantle.data.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.PathProvider;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.GenericDataProvider;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Data generator to create png image files */
public abstract class GenericTextureGenerator extends GenericDataProvider {
  /**
   * Resource source for reading existing textures. Was an {@code ExistingFileHelper} in 1.20/1.21.1; that class was
   * removed in 1.21.4, so datagen now reads through the {@link ResourceManager} exposed by {@code GatherDataEvent#getResourceManager}.
   */
  @Nullable
  protected final ResourceManager resourceManager;

  /** Constructor which reads existing textures through the given resource manager */
  public GenericTextureGenerator(PackOutput packOutput, @Nullable ResourceManager resourceManager, String folder) {
    super(packOutput, Target.RESOURCE_PACK, folder);
    this.resourceManager = resourceManager;
  }

  /** Saves the given image to the given location */
  protected CompletableFuture<?> saveImage(CachedOutput cache, ResourceLocation location, NativeImage image) {
    return saveImage(cache, pathProvider, location, image);
  }

  /** Saves metadata for the given image */
  protected CompletableFuture<?> saveMetadata(CachedOutput cache, ResourceLocation location, JsonObject metadata) {
    return DataProvider.saveStable(cache, metadata, this.pathProvider.file(location, "png.mcmeta"));
  }


  /* Helpers */

  /** Reads an image from disk. Note the caller is responsible for closing the resource */
  public static NativeImage read(ResourceManager resourceManager, String folder, ResourceLocation path) throws IOException {
    ResourceLocation full = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), folder + '/' + path.getPath() + ".png");
    try {
      Resource resource = resourceManager.getResource(full).orElseThrow(() -> new NoSuchElementException("Missing texture resource " + full));
      try (InputStream stream = resource.open()) {
        return NativeImage.read(stream);
      }
    } catch (IOException | NoSuchElementException e) {
      Mantle.logger.error("Failed to read image at {}", path, e);
      throw e;
    }
  }

  /** Saves the given image to the given location */
  public static CompletableFuture<?> saveImage(CachedOutput cache, PathProvider pathProvider, ResourceLocation location, NativeImage image) {
    return CompletableFuture.runAsync(() -> {
      try {
        Path path = pathProvider.file(location, "png");
        // PORT 1.21.4: NativeImage#asByteArray was removed with no public replacement, so we can no longer hand raw PNG
        // bytes to CachedOutput#writeIfNeeded. Write straight to the target path instead; this produces an identical PNG
        // but bypasses the cache's hash-based dedup and stale-file tracking. Only used by downstream texture datagen
        // (e.g. Tinkers fluid cameras); Mantle's own data generation does not exercise this path.
        image.writeToFile(path);
      } catch (IOException e) {
        Mantle.logger.error("Couldn't write image for {}", location, e);
        throw new CompletionException(e);
      }
    }, Util.backgroundExecutor());
  }
}
