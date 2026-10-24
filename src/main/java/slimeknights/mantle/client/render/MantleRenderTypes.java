package slimeknights.mantle.client.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import slimeknights.mantle.Mantle;

/**
 * Class for render types defined by Mantle.
 * 1.21.5: no longer extends {@link RenderType}; the shard constants it needed are public on {@link RenderStateShard} now, and
 * shader, transparency, cull, depth and write mask state moved into the {@link com.mojang.blaze3d.pipeline.RenderPipeline}s in {@link MantleShaders}.
 */
public class MantleRenderTypes {
  private MantleRenderTypes() {}

  /**
   * Render type used for the fluid renderer.
   * 1.21.4: {@code entityTranslucentCull} was removed; the surviving equivalent is the {@code entity_translucent} family, which
   * requires the normal-bearing {@code NEW_ENTITY} vertex format. We keep this custom type as our format has no normals.
   * 1.21.5: the config-driven shader choice is still resolved once when this class initializes, now as a pipeline.
   */
  public static final RenderType FLUID = RenderType.create(
    Mantle.modId + ":block_render_type", 256, false, true,
    MantleShaders.getConfiguredFluidPipeline(),
    RenderType.CompositeState.builder()
      .setLightmapState(RenderStateShard.LIGHTMAP)
      .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
      .createCompositeState(false));

  /**
   * Vertex format used for the structure renderer
   * @see MantleShaders#BLOCK_WITH_OVERLAY
   */
  public static final VertexFormat BLOCK_WITH_OVERLAY = MantleShaders.BLOCK_WITH_OVERLAY;

  /** Render type used for the structure renderer */
  public static final RenderType TRANSLUCENT_FULLBRIGHT = RenderType.create(
    Mantle.modId + ":translucent_fullbright", 256, false, false,
    MantleShaders.TRANSLUCENT_FULLBRIGHT,
    RenderType.CompositeState.builder()
      .setLightmapState(RenderStateShard.NO_LIGHTMAP)
      .setOverlayState(RenderStateShard.OVERLAY)
      .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
      .createCompositeState(false));
}
