package slimeknights.mantle.fluid.texture;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.vertex.PoseStack;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;
import org.joml.Vector3f;
import org.joml.Vector4f;
import slimeknights.mantle.client.render.FluidRenderer;

import javax.annotation.Nullable;

/** Implementation of {@link IClientFluidTypeExtensions} using {@link FluidTexture} */
@RequiredArgsConstructor
public class ClientTextureFluidType implements IClientFluidTypeExtensions {
  protected final FluidType type;
  private Vector3f fogColor;

  @Override
  public int getTintColor() {
    return FluidTextureManager.getColor(type);
  }

  @Override
  public ResourceLocation getStillTexture() {
    return FluidTextureManager.getStillTexture(type);
  }

  @Override
  public ResourceLocation getFlowingTexture() {
    return FluidTextureManager.getFlowingTexture(type);
  }

  @Nullable
  @Override
  public ResourceLocation getOverlayTexture() {
    return FluidTextureManager.getOverlayTexture(type);
  }

  @Nullable
  @Override
  public ResourceLocation getRenderOverlayTexture(Minecraft mc) {
    return FluidTextureManager.getCameraTexture(type);
  }

  @Override
  public void renderOverlay(Minecraft mc, PoseStack poseStack, MultiBufferSource buffer) {
    FluidTexture data = FluidTextureManager.getData(type);
    ResourceLocation camera = data.camera();
    if (camera != null) {
      FluidRenderer.renderCamera(mc, poseStack, camera, data.cameraOpacity(), data.color());
    }
  }

  @Override
  public Vector4f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector4f fluidFogColor) {
    // nothing to do if fog color is white
    int fluidColor = FluidTextureManager.getData(type).fogColor();
    if (fluidColor != -1) {
      // cache the vector for fog color to reduce computation time
      if (fogColor == null) {
        fogColor = new Vector3f(ARGB.red(fluidColor) / 255f, ARGB.green(fluidColor) / 255f, ARGB.blue(fluidColor) / 255f);
      }
      fluidFogColor.x *= fogColor.x;
      fluidFogColor.y *= fogColor.y;
      fluidFogColor.z *= fogColor.z;
    }
    return fluidFogColor;
  }

  @Override
  public FogParameters modifyFogRender(Camera camera, FogMode mode, float renderDistance, float partialTick, FogParameters fogParameters) {
    // 1.21.4: fog is now an immutable FogParameters record (the RenderSystem.setShaderFog* setters were removed), so
    // return a copy with our overrides applied. Only override start/end when they would tighten the existing fog.
    FluidTexture data = FluidTextureManager.getData(type);
    FogShape overrideShape = data.fogShape();
    if (overrideShape == null) {
      return fogParameters;
    }
    return new FogParameters(
      Math.min(data.fogStart(), fogParameters.start()),
      Math.min(data.fogEnd(), fogParameters.end()),
      overrideShape,
      fogParameters.red(), fogParameters.green(), fogParameters.blue(), fogParameters.alpha());
  }
}
