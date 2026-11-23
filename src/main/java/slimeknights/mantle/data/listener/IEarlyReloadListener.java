package slimeknights.mantle.data.listener;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * This interface is similar to {@link net.minecraft.server.packs.resources.ResourceManagerReloadListener}, except it runs during {@link net.minecraft.server.packs.resources.SimplePreparableReloadListener}'s prepare phase.
 * This is used mainly as models load during the prepare phase, so it ensures they are loaded soon enough.
 * <p>
 * TODO 1.19: is there any reason to keep this alongside {@link IEarlySafeManagerReloadListener}?
 */
public interface IEarlyReloadListener extends PreparableReloadListener {
  @Override
  default CompletableFuture<Void> reload(SharedState sharedState, Executor backgroundExecutor, PreparationBarrier stage, Executor gameExecutor) {
    ResourceManager resourceManager = sharedState.resourceManager();
    return CompletableFuture.runAsync(() -> {
      this.onResourceManagerReload(resourceManager);
    }, backgroundExecutor).thenCompose(stage::wait);
  }

  /** @param resourceManager the resource manager being reloaded */
  void onResourceManagerReload(ResourceManager resourceManager);
}
