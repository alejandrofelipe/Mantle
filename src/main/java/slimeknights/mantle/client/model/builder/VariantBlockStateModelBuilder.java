package slimeknights.mantle.client.model.builder;

import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.block.model.VariantMutator;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.client.model.generators.blockstate.CustomBlockStateModelBuilder;
import net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator;
import slimeknights.mantle.client.model.RetexturedModel;
import slimeknights.mantle.client.model.connected.ConnectedModel;

import java.util.function.Function;

/**
 * Datagen builder for block state model types wrapping a single {@link Variant}, such as {@code mantle:connected} and
 * {@code mantle:retextured}. Unlike {@link CustomBlockStateModelBuilder.Simple}, this applies {@link VariantMutator}s
 * (x/y rotation, uvlock from a {@code PropertyDispatch}) to the wrapped variant, so it works with rotated variants.
 * <p>
 * Usage: {@code MultiVariantGenerator.dispatch(block, VariantBlockStateModelBuilder.connected(modelId).multiVariant())},
 * emitting {@code {"type": "mantle:connected", "model": "<modelId>"}}.
 */
@SuppressWarnings("unused")  // API
public class VariantBlockStateModelBuilder extends CustomBlockStateModelBuilder {
  private final Function<Variant,? extends CustomUnbakedBlockStateModel> factory;
  private final Variant variant;

  public VariantBlockStateModelBuilder(Function<Variant,? extends CustomUnbakedBlockStateModel> factory, Variant variant) {
    this.factory = factory;
    this.variant = variant;
  }

  /** Creates a builder for a {@code mantle:connected} block state model */
  public static VariantBlockStateModelBuilder connected(ResourceLocation model) {
    return new VariantBlockStateModelBuilder(ConnectedModel.Unbaked::new, new Variant(model));
  }

  /** Creates a builder for a {@code mantle:retextured} block state model */
  public static VariantBlockStateModelBuilder retextured(ResourceLocation model) {
    return new VariantBlockStateModelBuilder(RetexturedModel.Unbaked::new, new Variant(model));
  }

  /** Wraps this builder in a multi variant for use in the vanilla block state generators */
  public MultiVariant multiVariant() {
    return MultiVariant.of(this);
  }

  @Override
  public VariantBlockStateModelBuilder with(VariantMutator variantMutator) {
    return new VariantBlockStateModelBuilder(factory, variantMutator.apply(variant));
  }

  @Override
  public CustomBlockStateModelBuilder with(UnbakedMutator variantMutator) {
    return new CustomBlockStateModelBuilder.Simple(variantMutator.apply(toUnbaked()));
  }

  @Override
  public CustomUnbakedBlockStateModel toUnbaked() {
    return factory.apply(variant);
  }
}
