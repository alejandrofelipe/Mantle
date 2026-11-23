package slimeknights.mantle.data.listener;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModLoader;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Same as {@link ISafeManagerReloadListener}, but reloads earlier. Needed to work with some parts of models. */
public interface IEarlySafeManagerReloadListener extends PreparableReloadListener {
  @Override
  default CompletableFuture<Void> reload(SharedState sharedState, Executor backgroundExecutor, PreparationBarrier stage, Executor gameExecutor) {
    ResourceManager resourceManager = sharedState.resourceManager();
    return CompletableFuture.runAsync(() -> {
      if (!ModLoader.hasErrors()) {
        onReloadSafe(resourceManager);
      }
    }, backgroundExecutor).thenCompose(stage::wait);
  }

  /**
   * Safely handle a resource manager reload. Only runs if the mod loading state is valid
   * @param resourceManager  Resource manager
   */
  void onReloadSafe(ResourceManager resourceManager);
}
