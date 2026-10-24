package slimeknights.mantle.client.model.util;

import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;

import java.util.List;

/**
 * Cross between {@link DelegateBlockStateModel} and {@link DynamicBlockStateModel}: a block state model wrapping a base
 * model (particle icon and default parts) that dynamically chooses its parts from the level in
 * {@link #collectParts(BlockAndTintGetter, BlockPos, BlockState, RandomSource, List)}.
 * <p>
 * 1.21.5: {@code BakedModel} is gone, so this wraps a {@link BlockStateModel} instead. The old ModelData-driven
 * {@code getQuads} becomes {@code collectParts}; read block entity data there with {@code level.getModelData(pos)}.
 * The context-free {@link #collectParts(RandomSource, List)} routes to the dynamic overload with an empty level, so
 * subclasses must fall back to their base parts when the level has no data. Override
 * {@link #createGeometryKey(BlockAndTintGetter, BlockPos, BlockState, RandomSource)} with a key of the dynamic data
 * to allow chunk meshing to reuse the geometry.
 * @param <T>  Base block state model
 */
@SuppressWarnings("WeakerAccess")
public abstract class DynamicBakedWrapper<T extends BlockStateModel> extends DelegateBlockStateModel implements DynamicBlockStateModel {
  /** Base model, typed */
  protected final T parent;

  protected DynamicBakedWrapper(T originalModel) {
    super(originalModel);
    this.parent = originalModel;
  }

  /** @deprecated use {@link #collectParts(BlockAndTintGetter, BlockPos, BlockState, RandomSource, List)} */
  @Override
  @Deprecated
  public void collectParts(RandomSource random, List<BlockModelPart> parts) {
    collectParts(EmptyBlockAndTintGetter.INSTANCE, BlockPos.ZERO, Blocks.AIR.defaultBlockState(), random, parts);
  }

  @Override
  public abstract void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockModelPart> parts);
}
