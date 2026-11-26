package slimeknights.mantle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Does not override {@link #shouldRenderOffScreen()}: since 1.21.6 it no longer receives the block entity so it cannot check for an empty inventory.
 * @deprecated use {@link InventoryBlockEntityRenderer} for the new render item registry.
 */
@Deprecated(forRemoval = true)
public class InventoryTileEntityRenderer<T extends BlockEntity & Container> implements BlockEntityRenderer<T, InventoryRenderState> {
  protected final ItemModelResolver itemModelResolver;

  public InventoryTileEntityRenderer(BlockEntityRendererProvider.Context context) {
    this.itemModelResolver = context.itemModelResolver();
  }

  @Override
  public InventoryRenderState createRenderState() {
    return new InventoryRenderState();
  }

  @Override
  public void extractRenderState(T inventory, InventoryRenderState state, float partialTicks, Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(inventory, state, partialTicks, cameraPos, breakProgress);
    if (!inventory.isEmpty()) {
      state.extractItems(itemModelResolver, RenderItem.REGISTRY.get(state.blockState.getBlock(), List.of()), inventory::getItem, inventory.getLevel());
    }
  }

  @Override
  public void submit(InventoryRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera) {
    if (state.isEmpty()) return;

    // if the block is rotatable, rotate item display
    boolean isRotated = RenderingHelper.applyRotation(matrices, state.blockState);
    state.submitItems(matrices, collector);
    // pop back rotation
    if (isRotated) {
      matrices.popPose();
    }
  }
}
