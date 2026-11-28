package slimeknights.mantle.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import slimeknights.mantle.Mantle;

import java.util.function.Supplier;

/**
 * Class for render types defined by Mantle.
 * 1.21.5: no longer extends {@link RenderType}; shader, transparency, cull, depth and write mask state moved into the
 * {@link com.mojang.blaze3d.pipeline.RenderPipeline}s in {@link MantleShaders}.
 * 1.21.11: {@code RenderStateShard} and {@code CompositeState} are gone; each type is a {@link RenderSetup} on the same pipeline.
 * The mipmapped block sheet ({@code BLOCK_SHEET_MIPPED}) is the block atlas sampled with {@link #BLOCK_SHEET_MIPPED_SAMPLER}.
 */
public class MantleRenderTypes {
  private MantleRenderTypes() {}

  /**
   * Sampler matching the 1.21.10 {@code RenderStateShard.BLOCK_SHEET_MIPPED} filtering: the block atlas filters with NEAREST and the shard
   * turned mipmaps on, giving a {@code NEAREST_MIPMAP_LINEAR} min and {@code NEAREST} mag filter. Vanilla's {@code RenderTypes.MOVING_BLOCK_SAMPLER}
   * uses a LINEAR min filter, so it is not reused here. Clamped like the 1.21.11 atlas sampler; UVs stay inside the atlas, so this matches
   * the old repeat addressing.
   */
  private static final Supplier<GpuSampler> BLOCK_SHEET_MIPPED_SAMPLER = () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);

  /**
   * Render type used for the fluid renderer.
   * 1.21.4: {@code entityTranslucentCull} was removed; the surviving equivalent is the {@code entity_translucent} family, which
   * requires the normal-bearing {@code NEW_ENTITY} vertex format. We keep this custom type as our format has no normals.
   * 1.21.5: the config-driven shader choice is still resolved once when this class initializes, now as a pipeline.
   */
  public static final RenderType FLUID = RenderType.create(
    Mantle.modId + ":block_render_type",
    RenderSetup.builder(MantleShaders.getConfiguredFluidPipeline())
      .useLightmap()
      .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS, BLOCK_SHEET_MIPPED_SAMPLER)
      .sortOnUpload()
      .createRenderSetup());

  /**
   * {@link #FLUID} with culling off, so both sides of each face show, e.g. the inside of a multiblock tank's fluid.
   * Like {@link #FLUID}, the config-driven shader choice is resolved once when this class initializes.
   * @see FluidRenderer#submitTiledBox
   */
  public static final RenderType FLUID_NO_CULL = RenderType.create(
    Mantle.modId + ":fluid_no_cull",
    RenderSetup.builder(MantleShaders.getConfiguredFluidNoCullPipeline())
      .useLightmap()
      .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS, BLOCK_SHEET_MIPPED_SAMPLER)
      .sortOnUpload()
      .createRenderSetup());

  /**
   * Vertex format used for the structure renderer
   * @see MantleShaders#BLOCK_WITH_OVERLAY
   */
  public static final VertexFormat BLOCK_WITH_OVERLAY = MantleShaders.BLOCK_WITH_OVERLAY;

  /** Render type used for the structure renderer */
  public static final RenderType TRANSLUCENT_FULLBRIGHT = RenderType.create(
    Mantle.modId + ":translucent_fullbright",
    RenderSetup.builder(MantleShaders.TRANSLUCENT_FULLBRIGHT)
      .useOverlay()
      .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS, BLOCK_SHEET_MIPPED_SAMPLER)
      .createRenderSetup());
}
