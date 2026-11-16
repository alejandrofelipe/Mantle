package slimeknights.mantle.client.screen.book.element;

import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.FontContext;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import slimeknights.mantle.client.screen.book.BookScreen;

import java.util.List;
import java.util.stream.Stream;

public abstract class BookElement {

  /** TODO 1.21: make this field protected instead of public to ensure setter is used. */
  @Setter
  public BookScreen parent;

  protected Minecraft mc = Minecraft.getInstance();
  protected TextureManager renderEngine = this.mc.getTextureManager();

  public int x, y;

  public BookElement(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public abstract void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, Font fontRenderer);

  public void drawOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, Font fontRenderer) {
  }

  public void mouseClicked(double mouseX, double mouseY, int mouseButton) {

  }

  public void mouseReleased(double mouseX, double mouseY, int clickedMouseButton) {

  }

  public void mouseDragged(double clickX, double clickY, double mx, double my, double lastX, double lastY, int button) {

  }

  public void renderToolTip(GuiGraphics graphics, Font fontRenderer, ItemStack stack, int x, int y) {
    List<Component> list = stack.getTooltipLines(Item.TooltipContext.of(this.mc.level), this.mc.player, this.mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);

    Font font = IClientItemExtensions.of(stack).getFont(stack, FontContext.TOOLTIP);
    if (font == null) {
      font = fontRenderer;
    }

    this.drawTooltip(graphics, list, x, y, font);
  }


  private static Stream<FormattedCharSequence> splitLine(FormattedText text, Font font, int maxWidth) {
    if (text instanceof Component component) {
      if (component.getString().isEmpty()) {
        return Stream.of(component.getVisualOrderText());
      }
    }
    return font.split(text, maxWidth).stream();
  }

  /** Tooltip positioner to keep the tooltip within the page for the relative mouse positions */
  private static final ClientTooltipPositioner POSITIONER = (screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight) -> {
    Vector2i pos = (new Vector2i(mouseX, mouseY)).add(12, -12);
    if (pos.x + tooltipWidth > BookScreen.PAGE_WIDTH) {
      pos.x = Math.max(pos.x - 24 - tooltipWidth, 4);
    }
    tooltipHeight += 3;
    if (pos.y + tooltipHeight > BookScreen.PAGE_HEIGHT) {
      pos.y = BookScreen.PAGE_HEIGHT - tooltipHeight;
    }
    return pos;
  };

  /**
   * Renders a tooltip in a book.
   * Based on {@link net.neoforged.neoforge.client.ClientHooks#gatherTooltipComponents(ItemStack, List, int, int, int, Font)}, but with three notable changes:
   * Uses the book page size (since mouseX and mouseY tend to be page relative), actually uses the updated tooltipX position, and drops the unused non-text component code.
   */
  @SuppressWarnings("UnstableApiUsage")  // this is a javadoc my dude
  public void drawTooltip(GuiGraphics graphics, List<Component> textLines, int mouseX, int mouseY, Font font) {
    // find max width of the tooltip
    int tooltipTextWidth = textLines.stream().mapToInt(font::width).max().orElse(0);
    boolean needsWrap = false;
    int tooltipX = mouseX + 12;
    // if the max width plus the position is too wide, need to fix
    if (tooltipX + tooltipTextWidth + 4 > BookScreen.PAGE_WIDTH) {
      // if repositioning fails, we will need to wrap the tooltip
      tooltipX = mouseX - 16 - tooltipTextWidth;
      if (tooltipX < 4) {
        if (mouseX > BookScreen.PAGE_WIDTH / 2) {
          tooltipTextWidth = mouseX - 12 - 8;
        } else {
          tooltipTextWidth = BookScreen.PAGE_WIDTH - 16 - mouseX;
        }

        needsWrap = true;
      }
    }

    // map to ordered text lines, wrapping if needed
    int tooltipTextWidthF = tooltipTextWidth;
    List<FormattedCharSequence> lines = needsWrap
      ? textLines.stream().flatMap(text -> splitLine(text, font, tooltipTextWidthF)).toList()
      : textLines.stream().map(Component::getVisualOrderText).toList();

    // 1.21.8: tooltips are deferred to the end of the frame and drawn without the page transform that is active now,
    // so hand the screen space mouse position over and run the page relative positioner through the captured transform
    Matrix3x2f pageToScreen = new Matrix3x2f(graphics.pose());
    Matrix3x2f screenToPage = pageToScreen.invert(new Matrix3x2f());
    ClientTooltipPositioner positioner = (screenWidth, screenHeight, x, y, width, height) -> {
      Vector2f pageMouse = screenToPage.transformPosition(x, y, new Vector2f());
      Vector2ic pagePos = POSITIONER.positionTooltip(screenWidth, screenHeight, Math.round(pageMouse.x), Math.round(pageMouse.y), width, height);
      Vector2f screenPos = pageToScreen.transformPosition(pagePos.x(), pagePos.y(), new Vector2f());
      return new Vector2i(Math.round(screenPos.x), Math.round(screenPos.y));
    };
    Vector2f screenMouse = pageToScreen.transformPosition(mouseX, mouseY, new Vector2f());
    graphics.setTooltipForNextFrame(font, lines, positioner, Math.round(screenMouse.x), Math.round(screenMouse.y), false);
  }

  /**
   * True if this element contains any text,
   * will not be included for HTML export
   */
  public boolean isText() {
    return false;
  }
}
