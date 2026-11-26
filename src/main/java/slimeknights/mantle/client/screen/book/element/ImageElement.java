package slimeknights.mantle.client.screen.book.element;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2fStack;
import slimeknights.mantle.client.book.data.element.ImageData;
import slimeknights.mantle.client.screen.book.BookScreen;

import static java.util.Objects.requireNonNullElse;

public class ImageElement extends SizedBookElement {

  public ImageData image;
  public int colorMultiplier;

  private ItemElement itemElement;

  public ImageElement(ImageData image) {
    this(image, 0xFFFFFF);
  }

  public ImageElement(ImageData image, int colorMultiplier) {
    this(image.x, image.y, image.width, image.height, image, colorMultiplier);
  }

  public ImageElement(int x, int y, int width, int height, ImageData image) {
    this(x, y, width, height, image, image.colorMultiplier);
  }

  public ImageElement(int x, int y, int width, int height, ImageData image, int colorMultiplier) {
    super(x, y, width, height);

    this.image = image;

    if (image.x != -1) {
      x = image.x;
    }
    if (image.y != -1) {
      y = image.y;
    }
    if (image.width != -1) {
      width = image.width;
    }
    if (image.height != -1) {
      height = image.height;
    }
    if (image.colorMultiplier != 0xFFFFFF) {
      colorMultiplier = image.colorMultiplier;
    }

    this.x = x == -1 ? 0 : x;
    this.y = y == -1 ? 0 : y;
    this.width = width;
    this.height = height;
    this.colorMultiplier = colorMultiplier;

    if(image.item != null) {
      this.itemElement = new ItemElement(0, 0, 1F, image.item.getItems());
    }
  }

  @Override
  public void setParent(BookScreen parent) {
    super.setParent(parent);
    if (itemElement != null) {
      itemElement.setParent(parent);
    }
  }

  /**
   * Draws this element untinted by delegating to {@link #draw(GuiGraphics, int, int, float, Font, int)}.
   * Subclasses changing how the image is drawn must override the tint overload instead of this method,
   * as callers such as {@link SelectionElement} call the tint overload directly.
   */
  @Override
  public void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, Font fontRenderer) {
    this.draw(graphics, mouseX, mouseY, partialTicks, fontRenderer, 0xFFFFFFFF);
  }

  /**
   * Draws this element with an extra ARGB tint multiplied into the color multiplier.
   * Replaces wrapping the draw in {@code RenderSystem.setShaderColor}, which no longer exists in 1.21.8.
   * The tint does not apply to item images, as GUI item rendering takes no color.
   * This is the method subclasses should override, {@link #draw(GuiGraphics, int, int, float, Font)} delegates to it.
   */
  public void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, Font fontRenderer, int tint) {
    if (this.image.item == null) {
      int color = ARGB.multiply(0xFF000000 | (this.colorMultiplier & 0xFFFFFF), tint);
      Identifier texture = requireNonNullElse(this.image.location, TextureManager.INTENTIONAL_MISSING_TEXTURE);
      graphics.blit(RenderPipelines.GUI_TEXTURED, texture, this.x, this.y, (float) this.image.u, (float) this.image.v, this.width, this.height, this.image.uw, this.image.vh, this.image.texWidth, this.image.texHeight, color);
    }
    else {
      Matrix3x2fStack matrices = graphics.pose();
      matrices.pushMatrix();
      matrices.translate(this.x, this.y);
      matrices.scale(this.width / 16F, this.height / 16F);

      this.itemElement.draw(graphics, mouseX, mouseY, partialTicks, fontRenderer);

      matrices.popMatrix();
    }
  }
}
