package slimeknights.mantle.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.config.Config;

/**
 * Handles any custom shaders registered by Mantle.
 * 1.21.5: core shader JSONs are gone; each shader is now part of a {@link RenderPipeline}, which also owns the uniforms, samplers,
 * vertex format, blend, depth and cull state that used to live in the shader JSON and in {@code RenderStateShard}s (removed in 1.21.11).
 * Pipelines must be registered in {@link RegisterRenderPipelinesEvent} so they are compiled with the vanilla ones.
 * 1.21.6: uniforms are std140 blocks; Mantle's shaders import the {@code DynamicTransforms}, {@code Projection} and {@code Fog} blocks, matching {@link RenderPipelines#MATRICES_FOG_SNIPPET}.
 * 26.2: samplers and uniforms are declared as bind group layouts ({@link BindGroupLayouts}; a name may appear only once per pipeline), the vertex
 * format is a vertex binding plus a primitive topology, and depth is reverse-Z: keep {@link DepthStencilState#DEFAULT}, never a hand-written compare op.
 */
@EventBusSubscriber(modid = Mantle.modId, value = Dist.CLIENT)
public class MantleShaders {
  /**
   * Vertex format for the structure renderer, block format plus overlay UVs. Defined here rather than {@link MantleRenderTypes} so pipelines can be built without loading render types.
   * 26.2: the hand-built format had exactly the attributes (and padding) of {@link DefaultVertexFormat#ENTITY}, and typed formats can no longer be
   * built from the removed {@code VertexFormatElement} constants, so this is that format.
   */
  public static final VertexFormat BLOCK_WITH_OVERLAY = DefaultVertexFormat.ENTITY;

  /**
   * Shared fluid state: translucent, culled, depth tested and written, {@code POSITION_COLOR_TEX_LIGHTMAP} quads. Has no shaders, see {@link #FLUID_SNIPPET} and {@link #FLUID_NO_FOG_SNIPPET}.
   * 26.1: a pipeline without a depth stencil state has no depth test (up to 1.21.11 the builder defaulted to a depth test and write), so the
   * state is set explicitly; without it fluids drew over every block in front of them.
   */
  private static final RenderPipeline.Snippet FLUID_BASE_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
    .withDepthStencilState(DepthStencilState.DEFAULT)
    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
    .withPrimitiveTopology(PrimitiveTopology.QUADS)
    .buildSnippet();
  /** Fluid pipeline state using Mantle's fluid shader, which applies vanilla fog. Use as the base for fluid pipeline variants, e.g. one with cull disabled. */
  public static final RenderPipeline.Snippet FLUID_SNIPPET = RenderPipeline.builder(FLUID_BASE_SNIPPET)
    .withVertexShader(Mantle.getResource("core/fluid"))
    .withFragmentShader(Mantle.getResource("core/fluid"))
    .buildSnippet();
  /**
   * Fluid pipeline state ignoring fog, for shader compatibility.
   * 1.21.9: vanilla removed the {@code position_color_tex_lightmap} shader this used; a missing shader in any registered pipeline makes the whole
   * shader reload fail (and with it every vanilla GUI pipeline), so Mantle ships a copy as {@code mantle:core/fluid_no_fog}.
   */
  public static final RenderPipeline.Snippet FLUID_NO_FOG_SNIPPET = RenderPipeline.builder(FLUID_BASE_SNIPPET)
    .withVertexShader(Mantle.getResource("core/fluid_no_fog"))
    .withFragmentShader(Mantle.getResource("core/fluid_no_fog"))
    .buildSnippet();

  /** Fluid pipeline with the fog fix */
  public static final RenderPipeline FLUID = RenderPipeline.builder(FLUID_SNIPPET).withLocation(pipeline("fluid")).build();
  /** Fluid pipeline without the fog fix, for shader compatibility */
  public static final RenderPipeline FLUID_NO_FOG = RenderPipeline.builder(FLUID_NO_FOG_SNIPPET).withLocation(pipeline("fluid_no_fog")).build();
  /** {@link #FLUID} with cull disabled, so both sides of each face show */
  public static final RenderPipeline FLUID_NO_CULL = RenderPipeline.builder(FLUID_SNIPPET).withLocation(pipeline("fluid_no_cull")).withCull(false).build();
  /** {@link #FLUID_NO_FOG} with cull disabled, so both sides of each face show */
  public static final RenderPipeline FLUID_NO_FOG_NO_CULL = RenderPipeline.builder(FLUID_NO_FOG_SNIPPET).withLocation(pipeline("fluid_no_fog_no_cull")).withCull(false).build();
  /** Translucent block pipeline ignoring the lightmap, used for book structures */
  public static final RenderPipeline TRANSLUCENT_FULLBRIGHT = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
    .withLocation(pipeline("translucent_fullbright"))
    .withVertexShader(Mantle.getResource("core/block_fullbright"))
    .withFragmentShader(Mantle.getResource("core/block_fullbright"))
    .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
    .withDepthStencilState(DepthStencilState.DEFAULT) // 26.1: no depth test unless set, see FLUID_BASE_SNIPPET
    .withVertexBinding(0, BLOCK_WITH_OVERLAY)
    .withPrimitiveTopology(PrimitiveTopology.QUADS)
    .build();

  /** Creates a pipeline location under Mantle's namespace */
  private static Identifier pipeline(String name) {
    return Mantle.getResource("pipeline/" + name);
  }

  /** Checks the config option to see if the fluid fog fix is enabled */
  public static boolean useFluidFogFix() {
    return Config.ENABLE_FLUID_FOG_FIX.get();
  }

  /** Gets the pipeline to use for {@link MantleRenderTypes#FLUID}, checking the config option to select which shader to use. */
  public static RenderPipeline getConfiguredFluidPipeline() {
    return useFluidFogFix() ? FLUID : FLUID_NO_FOG;
  }

  /** Gets the pipeline to use for {@link MantleRenderTypes#FLUID_NO_CULL}, checking the config option to select which shader to use. */
  public static RenderPipeline getConfiguredFluidNoCullPipeline() {
    return useFluidFogFix() ? FLUID_NO_CULL : FLUID_NO_FOG_NO_CULL;
  }

  @SubscribeEvent
  static void registerPipelines(RegisterRenderPipelinesEvent event) {
    // register both fluid variants, the config is read when the render type is created
    event.registerPipeline(FLUID);
    event.registerPipeline(FLUID_NO_FOG);
    event.registerPipeline(FLUID_NO_CULL);
    event.registerPipeline(FLUID_NO_FOG_NO_CULL);
    event.registerPipeline(TRANSLUCENT_FULLBRIGHT);
  }
}
