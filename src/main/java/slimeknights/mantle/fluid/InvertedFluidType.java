package slimeknights.mantle.fluid;

import net.neoforged.neoforge.fluids.FluidType;

/**
 * Fluid type adding an extra flipped texture for the in world block.
 * 1.21.4: {@code FluidType.initializeClient} was removed; the matching {@link slimeknights.mantle.fluid.texture.ClientInvertedFluidType}
 * is registered via {@link net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent} in
 * {@link slimeknights.mantle.fluid.texture.MantleClientFluidTypes}.
 */
public class InvertedFluidType extends FluidType {
  public InvertedFluidType(Properties properties) {
    super(properties);
  }
}
