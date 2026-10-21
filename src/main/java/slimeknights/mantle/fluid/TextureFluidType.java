package slimeknights.mantle.fluid;

import net.neoforged.neoforge.fluids.FluidType;

/**
 * Fluid type whose color and textures are determined by the model.
 * 1.21.4: {@code FluidType.initializeClient} was removed; the matching {@link slimeknights.mantle.fluid.texture.ClientTextureFluidType}
 * is registered via {@link net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent} in
 * {@link slimeknights.mantle.fluid.texture.MantleClientFluidTypes}.
 */
public class TextureFluidType extends FluidType {
  public TextureFluidType(Properties properties) {
    super(properties);
  }
}
