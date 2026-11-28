package slimeknights.mantle.client.screen.book.element.pip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.book.structure.StructureInfo;
import slimeknights.mantle.client.book.structure.level.TemplateLevel;
import slimeknights.mantle.client.render.MantleRenderTypes;

/**
 * Renders a book structure preview into its own texture, which the GUI then blits.
 * 1.21.8: replaces the immediate 3D drawing {@link slimeknights.mantle.client.screen.book.element.StructureElement} did inside the GUI.
 * Registered for {@link StructureRenderState} in {@link slimeknights.mantle.client.ClientEvents}; NeoForge pools one renderer per state on screen.
 * <p>
 * Each renderer owns a color and depth texture of {@code (x1 - x0) * guiScale} by {@code (y1 - y0) * guiScale} pixels, so large structures
 * at a high GUI scale allocate large textures.
 * 26.2: the renderer gets no buffer source; the blocks are tesselated in a custom geometry node submitted to the collector, which the
 * picture-in-picture base draws right after {@link #renderToTexture} (same frame, so the captured level and structure are still valid).
 */
public class StructurePiPRenderer extends PictureInPictureRenderer<StructureRenderState> {
  /** Position highlighted with the red overlay */
  private static final BlockPos HIGHLIGHT_POS = new BlockPos(1, 1, 1);
  /**
   * Last time a render error was logged, to avoid logging every frame.
   * This is a single global limit shared by all structures, so while one structure keeps failing, errors from others may be skipped.
   */
  private static long lastPrintedErrorTimeMs = -1;


  @Override
  public Class<StructureRenderState> getRenderStateClass() {
    return StructureRenderState.class;
  }

  @Override
  protected String getTextureLabel() {
    return "mantle_structure";
  }

  @Override
  protected float getTranslateY(int height, int guiScale) {
    // center the structure vertically, like the entity renderer
    return height / 2f;
  }

  @Override
  protected void renderToTexture(StructureRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
    // the fullbright block shader ignores the lights, but keep the GUI block lighting bound like the vanilla 3D picture-in-picture renderers
    Minecraft minecraft = Minecraft.getInstance();
    minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);

    // the pose arrives centered on the texture, y down, scaled to state.scale() GUI units per unit with z pointing away from the viewer
    float pixelsPerBlock = minecraft.getWindow().getGuiScale() * state.scale();
    poseStack.translate(state.offsetX() / state.scale(), state.offsetY() / state.scale(), 0);
    // flip to y up and z toward the viewer, as the old GUI transform did. Depth is kept at one unit per block like the old z scale of 1,
    // which keeps every structure well inside the +-1000 depth range of the texture projection
    poseStack.scale(1, -1, -1 / pixelsPerBlock);

    TemplateLevel level = state.level();
    StructureInfo info = state.info();
    int structureLength = info.structureLength;
    int structureWidth = info.structureWidth;
    int structureHeight = info.structureHeight;

    poseStack.mulPose(state.transform());
    poseStack.translate(structureLength / -2f, structureHeight / -2f, structureWidth / -2f);
    collector.submitCustomGeometry(poseStack, MantleRenderTypes.TRANSLUCENT_FULLBRIGHT, (structurePose, buffer) -> tesselate(minecraft, level, info, structurePose, buffer));
  }

  /** Tesselates every block of the structure into the fullbright translucent buffer, from the structure's corner pose */
  private static void tesselate(Minecraft minecraft, TemplateLevel level, StructureInfo info, PoseStack.Pose structurePose, VertexConsumer buffer) {
    int structureLength = info.structureLength;
    int structureWidth = info.structureWidth;
    int structureHeight = info.structureHeight;
    try {
      PoseStack poseStack = new PoseStack();
      poseStack.last().set(structurePose);

      // 26.1: BlockRenderDispatcher is gone; tesselate like vanilla moving blocks, without face culling
      ModelBlockRenderer blockRenderer = new ModelBlockRenderer(minecraft.options.ambientOcclusion().get(), false, minecraft.getBlockColors());
      BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
      // overlay of the block being drawn, the quad instance carries it since 26.1
      int[] overlay = {OverlayTexture.NO_OVERLAY};
      // every chunk layer goes into the single fullbright translucent buffer, offset to the block position
      BlockQuadOutput output = (x, y, z, quad, instance) -> {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        instance.setOverlayCoords(overlay[0]);
        buffer.putBakedQuad(poseStack.last(), quad, instance);
        poseStack.popPose();
      };
      for (int h = 0; h < structureHeight; h++) {
        for (int l = 0; l < structureLength; l++) {
          for (int w = 0; w < structureWidth; w++) {
            BlockPos pos = new BlockPos(l, h, w);
            BlockState blockState = level.getBlockState(pos);

            if (!blockState.isAir()) {
              if (pos.equals(HIGHLIGHT_POS))
                overlay[0] = OverlayTexture.pack(0, true);
              else
                overlay[0] = OverlayTexture.NO_OVERLAY;

              // block models collect parts against the level (block entity data is read through TemplateLevel#getModelData)
              blockRenderer.tesselateBlock(output, l, h, w, level, pos, blockState, models.get(blockState), blockState.getSeed(pos));
            }
          }
        }
      }
    } catch (Exception e) {
      // the pose stack is local to this call, so no need to unwind it
      long now = System.currentTimeMillis();
      if (now > lastPrintedErrorTimeMs + 1000) {
        Mantle.logger.error("Error rendering book structure", e);
        lastPrintedErrorTimeMs = now;
      }
    }
  }
}
