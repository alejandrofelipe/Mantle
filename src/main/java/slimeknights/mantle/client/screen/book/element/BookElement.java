package slimeknights.mantle.client.screen.book.element;

import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
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
import org.joml.Vector2i;
import org.joml.Vector2ic;
import slimeknights.mantle.client.screen.book.BookScreen;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
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

    setPageTooltip(graphics, font, lines, POSITIONER, mouseX, mouseY);
  }

  /**
   * Sets an already wrapped tooltip for the book page currently being drawn.
   * Together with {@link #setPageTooltip(GuiGraphics, Font, List, int, int)} this is the single place handling the 1.21.6+ deferred tooltips for the book:
   * <ul>
   *   <li>{@link GuiGraphics#setTooltipForNextFrame} draws the tooltip at the end of the frame, after the page transform active now has been popped.
   *   So the page relative mouse position is moved to screen space here, and {@code pagePositioner} is run in page space and its result moved back.</li>
   *   <li>This assumes an unscaled page transform (a pure translation, as {@link BookScreen#PAGE_SCALE} is 1): only the pose translation is used,
   *   and the tooltip size given to the positioner is in screen space while it compares against page sizes.</li>
   *   <li>Ordering is mixed. Up to 1.21.5 tooltips were drawn immediately, so the last one drawn ended on top; the deferred queue keeps the first
   *   tooltip of the frame unless it is set as focused. The only public focused overload takes pre-split {@link FormattedCharSequence} lines
   *   (there is no public way to submit gathered {@code ClientTooltipComponent}s as focused), so this method sets focused tooltips and the last
   *   one wins, while the {@link Component} overload keeps the NeoForge gather/wrap of 1.21.5 and is first-wins. Vanilla widgets on the book screen
   *   render after the book with unfocused tooltips, so they cannot replace a book tooltip either way.</li>
   * </ul>
   * @param graphics        Graphics instance, with the page transform active
   * @param font            Font for the tooltip
   * @param lines           Tooltip lines, already wrapped
   * @param pagePositioner  Positioner working in page relative coordinates, or null to use the vanilla screen positioner
   * @param mouseX          Page relative mouse X
   * @param mouseY          Page relative mouse Y
   */
  public static void setPageTooltip(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines, @Nullable ClientTooltipPositioner pagePositioner, int mouseX, int mouseY) {
    Matrix3x2f pose = graphics.pose();
    int offsetX = Math.round(pose.m20());
    int offsetY = Math.round(pose.m21());
    ClientTooltipPositioner positioner = DefaultTooltipPositioner.INSTANCE;
    if (pagePositioner != null) {
      positioner = (screenWidth, screenHeight, x, y, width, height) -> {
        Vector2ic pagePos = pagePositioner.positionTooltip(screenWidth, screenHeight, x - offsetX, y - offsetY, width, height);
        return new Vector2i(pagePos.x() + offsetX, pagePos.y() + offsetY);
      };
    }
    graphics.setTooltipForNextFrame(font, lines, positioner, mouseX + offsetX, mouseY + offsetY, true);
  }

  /**
   * Sets a component tooltip for the book page currently being drawn, with the vanilla screen positioner.
   * Goes through {@link net.neoforged.neoforge.client.ClientHooks#gatherTooltipComponents} like the 1.21.5 {@code renderTooltip(Font, List, Optional, int, int)},
   * so it keeps the screen width wrapping and the GatherComponents event, but it is first-wins in the deferred queue.
   * See {@link #setPageTooltip(GuiGraphics, Font, List, ClientTooltipPositioner, int, int)} for the page transform handling and ordering.
   * @param graphics  Graphics instance, with the page transform active
   * @param font      Font for the tooltip
   * @param lines     Tooltip lines
   * @param mouseX    Page relative mouse X
   * @param mouseY    Page relative mouse Y
   */
  public static void setPageTooltip(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY) {
    Matrix3x2f pose = graphics.pose();
    graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX + Math.round(pose.m20()), mouseY + Math.round(pose.m21()));
  }

  /**
   * True if this element contains any text,
   * will not be included for HTML export
   */
  public boolean isText() {
    return false;
  }
}
