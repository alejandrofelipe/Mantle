package slimeknights.mantle.fluid.texture;

import com.mojang.blaze3d.vertex.PoseStack;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;
import org.joml.Vector3f;
import org.joml.Vector4f;
import slimeknights.mantle.client.render.FluidRenderer;

import org.jspecify.annotations.Nullable;

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
  public Identifier getStillTexture() {
    return FluidTextureManager.getStillTexture(type);
  }

  @Override
  public Identifier getFlowingTexture() {
    return FluidTextureManager.getFlowingTexture(type);
  }

  @Nullable
  @Override
  public Identifier getOverlayTexture() {
    return FluidTextureManager.getOverlayTexture(type);
  }

  @Nullable
  @Override
  public Identifier getRenderOverlayTexture(Minecraft mc) {
    return FluidTextureManager.getCameraTexture(type);
  }

  @Override
  public void renderOverlay(Minecraft mc, PoseStack poseStack, MultiBufferSource buffer) {
    FluidTexture data = FluidTextureManager.getData(type);
    Identifier camera = data.camera();
    if (camera != null) {
      FluidRenderer.renderCamera(mc, poseStack, buffer, camera, data.cameraOpacity(), data.color());
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
  public void modifyFogRender(Camera camera, @Nullable FogEnvironment environment, float renderDistance, float partialTick, FogData fogData) {
    // 1.21.6: fog is a mutable FogData with separate environmental (spherical) and render distance (cylindrical) ranges, and no shape.
    // Our start/end map to the environmental range, like vanilla lava/water/powder snow. Only override when it would tighten the existing fog.
    FluidTexture data = FluidTextureManager.getData(type);
    if (!data.overrideFog()) {
      return;
    }
    fogData.environmentalStart = Math.min(data.fogStart(), fogData.environmentalStart);
    fogData.environmentalEnd = Math.min(data.fogEnd(), fogData.environmentalEnd);
    // vanilla fluid environments also end the sky and clouds at the environmental end, so they do not show through the fluid
    fogData.skyEnd = Math.min(fogData.skyEnd, fogData.environmentalEnd);
    fogData.cloudEnd = Math.min(fogData.cloudEnd, fogData.environmentalEnd);
  }
}
