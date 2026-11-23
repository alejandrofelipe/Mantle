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

import javax.annotation.Nullable;
import java.util.List;

/**
 * Renders the items of an inventory block entity at the positions from {@link RenderItem#STATE_REGISTRY}.
 * Does not override {@link #shouldRenderOffScreen()}: since 1.21.6 it no longer receives the block entity so it cannot check for an empty inventory,
 * so the default of rendering with the chunk section is used.
 * 1.21.9: two phase rendering, the items are resolved into an {@link InventoryRenderState} on extraction.
 */
public class InventoryBlockEntityRenderer<T extends BlockEntity & Container> implements BlockEntityRenderer<T, InventoryRenderState> {
  protected final ItemModelResolver itemModelResolver;

  public InventoryBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    this.itemModelResolver = context.itemModelResolver();
  }

  @Override
  public InventoryRenderState createRenderState() {
    return new InventoryRenderState();
  }

  @Override
  public void extractRenderState(T inventory, InventoryRenderState state, float partialTicks, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
    BlockEntityRenderer.super.extractRenderState(inventory, state, partialTicks, cameraPos, breakProgress);
    if (!inventory.isEmpty()) {
      state.extractItems(itemModelResolver, RenderItem.STATE_REGISTRY.get(state.blockState, List.of()), inventory::getItem, inventory.getLevel());
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
