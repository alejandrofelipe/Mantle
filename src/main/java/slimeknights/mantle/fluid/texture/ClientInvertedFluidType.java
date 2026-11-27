package slimeknights.mantle.fluid.texture;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Client logic for {@link slimeknights.mantle.fluid.InvertedFluidType}: the fluid placed in the world uses the {@code _inverted} flowing texture,
 * fluid stacks keep the original one.
 * 26.1: the world texture is baked into the fluid model instead of the removed level-aware {@code getFlowingTexture}.
 */
public class ClientInvertedFluidType extends ClientTextureFluidType {
  public ClientInvertedFluidType(FluidType type) {
    super(type);
  }

  @Override
  protected Identifier getWorldFlowingTexture(Identifier flowing) {
    return flowing.withSuffix("_inverted");
  }
}
