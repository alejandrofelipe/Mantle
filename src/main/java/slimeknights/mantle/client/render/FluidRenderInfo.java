package slimeknights.mantle.client.render;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import javax.annotation.Nullable;

/**
 * Everything a world fluid draw needs, resolved from a {@link FluidStack} during render state extraction. Render states keep this instead of
 * the stack, so the geometry submitted through {@link FluidRenderer} never looks up the fluid type later in the frame.
 * @param still       Still sprite on the block atlas
 * @param flowing     Flowing sprite on the block atlas
 * @param color       Tint color, ARGB
 * @param lightLevel  Block light level the fluid emits, 0-15
 * @param isGas       If true, the fluid is lighter than air: gases draw upside down where the renderer flips gases
 */
public record FluidRenderInfo(TextureAtlasSprite still, TextureAtlasSprite flowing, int color, int lightLevel, boolean isGas) {
  /**
   * Resolves the render info for a fluid. Call during render state extraction, on the render thread.
   * @param fluid  Fluid to render
   * @return  Render info, or null if the fluid is empty (nothing to draw)
   */
  @Nullable
  public static FluidRenderInfo of(FluidStack fluid) {
    if (fluid.isEmpty()) {
      return null;
    }
    IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(fluid.getFluid());
    FluidType type = fluid.getFluid().getFluidType();
    return new FluidRenderInfo(
      FluidRenderer.getBlockSprite(clientFluid.getStillTexture(fluid)),
      FluidRenderer.getBlockSprite(clientFluid.getFlowingTexture(fluid)),
      clientFluid.getTintColor(fluid), type.getLightLevel(fluid), type.isLighterThanAir());
  }

  /**
   * Fades the fluid by multiplying its alpha, e.g. for the end of casting.
   * @param opacity  Opacity from 0 (invisible) to 255 (unchanged)
   * @return  Render info with the faded color, or this if the opacity is 255 or more
   */
  public FluidRenderInfo withOpacity(int opacity) {
    if (opacity >= 0xFF) {
      return this;
    }
    // alpha is the top 8 bits, multiply by opacity and divide out the remainder
    int alpha = ((color >> 24) & 0xFF) * opacity / 0xFF;
    return new FluidRenderInfo(still, flowing, (color & 0xFFFFFF) | (alpha << 24), lightLevel, isGas);
  }

  /**
   * Gets the packed light to draw this fluid with: the larger of the fluid's light level and the block light at its position.
   * @param combinedLight  Packed light at the fluid position
   * @return  Packed light including the fluid's light level
   * @see FluidRenderer#withBlockLight(int, int)
   */
  public int light(int combinedLight) {
    return FluidRenderer.withBlockLight(combinedLight, lightLevel);
  }
}
