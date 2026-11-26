package slimeknights.mantle.client.screen.book.element.pip;

import com.mojang.math.Transformation;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import slimeknights.mantle.client.book.structure.StructureInfo;
import slimeknights.mantle.client.book.structure.level.TemplateLevel;

import org.jspecify.annotations.Nullable;

/**
 * Picture-in-picture state for a book structure preview, drawn by {@link StructurePiPRenderer}.
 * 1.21.8: GUIs are deferred, so 3D content is rendered into its own texture which the GUI then blits. All bounds are absolute screen
 * coordinates, the GUI pose is not applied to picture-in-picture states.
 * @param level        Structure to draw, its block states already hide blocks past the current animation step
 * @param info         Structure dimensions and animation state
 * @param transform    Rotation applied to the structure around its center, from the book camera angle and mouse dragging
 * @param offsetX      Horizontal offset of the structure center from the center of the area, in screen GUI units
 * @param offsetY      Vertical offset of the structure center from the center of the area, in screen GUI units
 * @param x0           Left edge of the area, large enough to fit the structure at any rotation
 * @param y0           Top edge of the area
 * @param x1           Right edge of the area
 * @param y1           Bottom edge of the area
 * @param scale        Size of a block in screen GUI units
 * @param scissorArea  Scissor in effect when the state was submitted
 * @param bounds       Visible part of the area, used by the GUI to layer the blit. Derived from the area and scissor through
 *                     {@link PictureInPictureRenderState#getBounds}, as the vanilla states do; use the constructor without it
 */
public record StructureRenderState(
  TemplateLevel level,
  StructureInfo info,
  Transformation transform,
  float offsetX,
  float offsetY,
  int x0,
  int y0,
  int x1,
  int y1,
  float scale,
  @Nullable ScreenRectangle scissorArea,
  @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
  public StructureRenderState(TemplateLevel level, StructureInfo info, Transformation transform, float offsetX, float offsetY, int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea) {
    this(level, info, transform, offsetX, offsetY, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
  }
}
