package slimeknights.mantle.client.model.util;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.DelegateBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Cross between {@link DelegateBakedModel} and {@link net.neoforged.neoforge.client.model.IDynamicBakedModel}.
 * Used to create a baked model wrapper that has a dynamic {@link #getQuads(BlockState, Direction, RandomSource, ModelData, RenderType)}
 * without worrying about overriding the deprecated variant.
 * <p>
 * In 1.21.4 {@code BakedModelWrapper} was replaced by vanilla's {@link DelegateBakedModel} (field {@code parent}).
 * @param <T>  Baked model parent
 */
@SuppressWarnings("WeakerAccess")
public abstract class DynamicBakedWrapper<T extends BakedModel> extends DelegateBakedModel {

  protected DynamicBakedWrapper(T originalModel) {
    super(originalModel);
  }

  /** @deprecated use {@link #getQuads(BlockState, Direction, RandomSource, ModelData, RenderType)} */
  @Override
  @Deprecated
  public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
    return this.getQuads(state, side, rand, ModelData.EMPTY, null);
  }

  @Override
  @Nonnull
  public abstract List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType);
}
