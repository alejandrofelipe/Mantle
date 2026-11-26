package slimeknights.mantle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class RenderingHelper {
  /* Rotation */

  /**
   * Applies horizontal rotation to the given TESR
   * @param matrices  Matrix stack
   * @param state     Block state, checked for {@link BlockStateProperties#HORIZONTAL_FACING}
   * @return  True if rotation was applied. Caller is expected to call {@link PoseStack#popPose()} if true
   */
  public static boolean applyRotation(PoseStack matrices, BlockState state) {
    if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
      return applyRotation(matrices, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }
    return false;
  }

  /**
   * Applies horizontal rotation to the given TESR
   * @param matrices  Matrix stack
   * @param facing    Direction of rotation
   * @return  True if rotation was applied. Caller is expected to call {@link PoseStack#popPose()} if true
   */
  public static boolean applyRotation(PoseStack matrices, Direction facing) {
    // south has a facing of 0, no rotation needed
    if (facing.getAxis().isHorizontal() && facing != Direction.SOUTH) {
      matrices.pushPose();
      matrices.translate(0.5, 0, 0.5);
      matrices.mulPose(Axis.YP.rotationDegrees(-90f * (facing.get2DDataValue())));
      matrices.translate(-0.5, 0, -0.5);
      return true;
    }
    return false;
  }


  /* Items */

  /**
   * Resolves an item for {@link #submitItem(PoseStack, SubmitNodeCollector, ItemStackRenderState, RenderItem, int)}. Call during render state extraction.
   * 1.21.9: replaces the item half of the old {@code renderItem}, as {@code ItemRenderer.renderStatic} is gone.
   * @param resolver    Item model resolver, from the block entity renderer context
   * @param state       Item state to fill, cleared if the item is hidden or empty
   * @param item        Item to display
   * @param renderItem  Render item for the display context
   * @param level       Level for item model properties
   */
  public static void extractItem(ItemModelResolver resolver, ItemStackRenderState state, ItemStack item, RenderItem renderItem, @Nullable Level level) {
    if (renderItem.isHidden()) {
      state.clear();
    } else {
      // seed 0 matches the old renderStatic call
      resolver.updateForTopItem(state, item, renderItem.getTransform(), level, null, 0);
    }
  }

  /**
   * Submits a single item resolved by {@link #extractItem(ItemModelResolver, ItemStackRenderState, ItemStack, RenderItem, Level)} in a block entity renderer
   * @param matrices    Matrix stack instance
   * @param collector   Node collector
   * @param item        Resolved item
   * @param renderItem  Render item for render information
   * @param light       Model light
   */
  public static void submitItem(PoseStack matrices, SubmitNodeCollector collector, ItemStackRenderState item, RenderItem renderItem, int light) {
    // if the item says skip, skip
    if (renderItem.isHidden()) return;
    // if no stack, skip
    if (item.isEmpty()) return;

    // start rendering
    matrices.pushPose();
    Vector3f center = renderItem.getCenterScaled();
    matrices.translate(center.x(), center.y(), center.z());

    // scale
    float scale = renderItem.getSizeScaled();
    matrices.scale(scale, scale, scale);

    // rotate X, then Y
    float x = renderItem.getX();
    if (x != 0) {
      matrices.mulPose(Axis.XP.rotationDegrees(x));
    }
    float y = renderItem.getY();
    if (y != 0) {
      matrices.mulPose(Axis.YP.rotationDegrees(y));
    }

    // submit the actual item
    item.submit(matrices, collector, light, OverlayTexture.NO_OVERLAY, 0);
    matrices.popPose();
  }


  /* Faucet fluids */

  /**
   * Finds the faucet fluid cuboids below the given position. Call during render state extraction, as it reads the level.
   * 1.21.9: replaces the level half of the old {@code renderFaucetFluids}.
   * @param world      World instance
   * @param pos        Base position
   * @param direction  Direction to render
   * @return  Cuboids per block below the position: element {@code i} is drawn {@code i + 1} blocks below {@code pos}
   */
  public static List<List<FluidCuboid>> getFaucetFluids(LevelAccessor world, BlockPos pos, Direction direction) {
    List<List<FluidCuboid>> fluids = new ArrayList<>();
    int i = 0;
    FaucetFluid faucetFluid;
    do {
      // get the faucet data for the block
      i++;
      faucetFluid = FaucetFluid.REGISTRY.get(world.getBlockState(pos.below(i)));
      fluids.add(faucetFluid.getFluids(direction));
    } while (faucetFluid.isContinued());
    return fluids;
  }

  /**
   * Renders faucet fluids found by {@link #getFaucetFluids(LevelAccessor, BlockPos, Direction)}, typically inside {@link SubmitNodeCollector#submitCustomGeometry}
   * @param pose        Pose instance, not modified
   * @param buffer      Builder instance
   * @param fluids      Cuboids per block below, from {@link #getFaucetFluids(LevelAccessor, BlockPos, Direction)}
   * @param still       Still fluid texture
   * @param flowing     Flowing fluid texture
   * @param color       Color to tint fluid
   * @param light       Fluid light value
   */
  public static void renderFaucetFluids(PoseStack.Pose pose, VertexConsumer buffer, List<List<FluidCuboid>> fluids, TextureAtlasSprite still, TextureAtlasSprite flowing, int color, int light) {
    for (int i = 0; i < fluids.size(); i++) {
      // render all down cubes with the given offset
      PoseStack.Pose offset = pose.copy();
      offset.translate(0, -(i + 1), 0);
      for (FluidCuboid cube : fluids.get(i)) {
        FluidRenderer.renderCuboid(offset, buffer, cube, still, flowing, cube.getFromScaled(), cube.getToScaled(), color, light, false);
      }
    }
  }
}
