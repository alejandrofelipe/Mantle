package slimeknights.mantle.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.config.Config;

/**
 * Handles any custom shaders registered by Mantle.
 * 1.21.5: core shader JSONs are gone; each shader is now part of a {@link RenderPipeline}, which also owns the uniforms, samplers,
 * vertex format, blend, depth and cull state that used to live in the shader JSON and in {@link net.minecraft.client.renderer.RenderStateShard}s.
 * Pipelines must be registered in {@link RegisterRenderPipelinesEvent} so they are compiled with the vanilla ones.
 */
@EventBusSubscriber(modid = Mantle.modId, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class MantleShaders {
  /** Vertex format for the structure renderer, block format plus overlay UVs. Defined here rather than {@link MantleRenderTypes} so pipelines can be built without loading render types. */
  public static final VertexFormat BLOCK_WITH_OVERLAY = VertexFormat.builder()
    .add("Position", VertexFormatElement.POSITION)
    .add("Color", VertexFormatElement.COLOR)
    .add("UV0", VertexFormatElement.UV0)
    .add("UV1", VertexFormatElement.UV1)
    .add("UV2", VertexFormatElement.UV2)
    .add("Normal", VertexFormatElement.NORMAL)
    .padding(1)
    .build();

  /** Shared fluid state: translucent, culled, depth tested and written, {@code POSITION_COLOR_TEX_LIGHTMAP} quads. Has no shaders, see {@link #FLUID_SNIPPET} and {@link #FLUID_NO_FOG_SNIPPET}. */
  private static final RenderPipeline.Snippet FLUID_BASE_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_FOG_SNIPPET)
    .withSampler("Sampler0")
    .withBlend(BlendFunction.TRANSLUCENT)
    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS)
    .buildSnippet();
  /** Fluid pipeline state using Mantle's fluid shader, which applies vanilla fog. Use as the base for fluid pipeline variants, e.g. one with cull disabled. */
  public static final RenderPipeline.Snippet FLUID_SNIPPET = RenderPipeline.builder(FLUID_BASE_SNIPPET)
    .withVertexShader(Mantle.getResource("core/fluid"))
    .withFragmentShader(Mantle.getResource("core/fluid"))
    .buildSnippet();
  /** Fluid pipeline state using vanilla's {@code position_color_tex_lightmap} shader, which ignores fog but works with shader packs. */
  public static final RenderPipeline.Snippet FLUID_NO_FOG_SNIPPET = RenderPipeline.builder(FLUID_BASE_SNIPPET)
    .withVertexShader("core/position_color_tex_lightmap")
    .withFragmentShader("core/position_color_tex_lightmap")
    .buildSnippet();

  /** Fluid pipeline with the fog fix */
  public static final RenderPipeline FLUID = RenderPipeline.builder(FLUID_SNIPPET).withLocation(pipeline("fluid")).build();
  /** Fluid pipeline without the fog fix, for shader compatibility */
  public static final RenderPipeline FLUID_NO_FOG = RenderPipeline.builder(FLUID_NO_FOG_SNIPPET).withLocation(pipeline("fluid_no_fog")).build();
  /** Translucent block pipeline ignoring the lightmap, used for book structures */
  public static final RenderPipeline TRANSLUCENT_FULLBRIGHT = RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_FOG_SNIPPET)
    .withLocation(pipeline("translucent_fullbright"))
    .withVertexShader(Mantle.getResource("core/block_fullbright"))
    .withFragmentShader(Mantle.getResource("core/block_fullbright"))
    .withSampler("Sampler0")
    .withSampler("Sampler1")
    .withBlend(BlendFunction.TRANSLUCENT)
    .withVertexFormat(BLOCK_WITH_OVERLAY, VertexFormat.Mode.QUADS)
    .build();

  /** Creates a pipeline location under Mantle's namespace */
  private static ResourceLocation pipeline(String name) {
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

  @SubscribeEvent
  static void registerPipelines(RegisterRenderPipelinesEvent event) {
    // register both fluid variants, the config is read when the render type is created
    event.registerPipeline(FLUID);
    event.registerPipeline(FLUID_NO_FOG);
    event.registerPipeline(TRANSLUCENT_FULLBRIGHT);
  }
}
