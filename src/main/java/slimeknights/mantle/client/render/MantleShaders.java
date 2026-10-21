package slimeknights.mantle.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import lombok.Getter;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.ShaderProgram;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.config.Config;

/** Handles any custom shaders registered by Mantle. */
@EventBusSubscriber(modid = Mantle.modId, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class MantleShaders {
  /**
   * 1.21.4: shaders are now registered as {@link ShaderProgram} handles (config id + vertex format + defines). The
   * compiled program is fetched from these handles at draw time via {@link com.mojang.blaze3d.systems.RenderSystem#setShader(ShaderProgram)}.
   */
  @Getter
  private static final ShaderProgram blockFullBrightShader = new ShaderProgram(Mantle.getResource("core/block_fullbright"), DefaultVertexFormat.BLOCK, ShaderDefines.EMPTY);
  @Getter
  private static final ShaderProgram fluidShader = new ShaderProgram(Mantle.getResource("core/fluid"), DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, ShaderDefines.EMPTY);

  /** Gets the shader to use for {@link MantleRenderTypes#FLUID_SHADER}, checking the config option to select which shader to use. */
  public static ShaderProgram getConfiguredFluidShader() {
    return Config.ENABLE_FLUID_FOG_FIX.get() ? fluidShader : CoreShaders.POSITION_COLOR_TEX_LIGHTMAP;
  }

  @SubscribeEvent
  static void registerShaders(RegisterShadersEvent event) {
    event.registerShader(blockFullBrightShader);
    event.registerShader(fluidShader);
  }
}
