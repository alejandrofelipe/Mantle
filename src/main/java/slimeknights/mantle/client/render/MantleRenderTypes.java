package slimeknights.mantle.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import slimeknights.mantle.Mantle;

/**
 * Class for render types defined by Mantle
 */
public class MantleRenderTypes extends RenderType {

  private MantleRenderTypes(String name, VertexFormat format, Mode mode, int bufferSize, boolean useDelegate, boolean needsSorting, Runnable setupTaskIn, Runnable clearTaskIn) {
    super(name, format, mode, bufferSize, useDelegate, needsSorting, setupTaskIn, clearTaskIn);
  }

  /**
   * Extension of {@code POSITION_COLOR_TEX_LIGHTMAP} with fog information. 1.21.4: {@link RenderStateShard.ShaderStateShard}
   * now takes a fixed {@link net.minecraft.client.renderer.ShaderProgram}, so the config-driven shader choice is resolved
   * once when this class initializes rather than per draw.
   */
  public static final RenderStateShard.ShaderStateShard FLUID_SHADER = new RenderStateShard.ShaderStateShard(MantleShaders.getConfiguredFluidShader());

  /**
   * Render type used for the fluid renderer.
   * 1.21.4: {@code entityTranslucentCull} was removed; the surviving equivalent is the {@code entity_translucent} family
   * ({@link net.minecraft.client.renderer.CoreShaders#RENDERTYPE_ENTITY_TRANSLUCENT}), which requires the normal-bearing
   * {@code NEW_ENTITY} vertex format. We keep this custom type as our format has no normals.
   */
  public static final RenderType FLUID = create(
    Mantle.modId + ":block_render_type",
    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true,
    RenderType.CompositeState.builder()
      .setLightmapState(LIGHTMAP)
      .setShaderState(FLUID_SHADER)
      .setTextureState(BLOCK_SHEET_MIPPED)
      .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
      .createCompositeState(false));

  /**
   * Render type used for the structure renderer
   */
  public static final VertexFormat BLOCK_WITH_OVERLAY = VertexFormat.builder()
    .add("Position", VertexFormatElement.POSITION)
    .add("Color", VertexFormatElement.COLOR)
    .add("UV0", VertexFormatElement.UV0)
    .add("UV1", VertexFormatElement.UV1)
    .add("UV2", VertexFormatElement.UV2)
    .add("Normal", VertexFormatElement.NORMAL)
    .padding(1)
    .build();

  public static final RenderType TRANSLUCENT_FULLBRIGHT = create(
    Mantle.modId + ":translucent_fullbright",
    BLOCK_WITH_OVERLAY, Mode.QUADS, 256, false, false,
    RenderType.CompositeState.builder()
      .setShaderState(new RenderStateShard.ShaderStateShard(MantleShaders.getBlockFullBrightShader()))
      .setLightmapState(new RenderStateShard.LightmapStateShard(false))
      .setOverlayState(OVERLAY)
      .setTextureState(BLOCK_SHEET_MIPPED)
      .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
      .createCompositeState(false));
}
