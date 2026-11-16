package slimeknights.mantle.client.screen.book.element;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;

import java.util.List;
import java.util.Optional;

public class TooltipElement extends SizedBookElement {

  private final List<Component> tooltips;

  public TooltipElement(List<Component> tooltip, int x, int y, int width, int height) {
    super(x, y, width, height);

    this.tooltips = tooltip;
  }

  @Override
  public void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, Font fontRenderer) {
  }

  @Override
  public void drawOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, Font fontRenderer) {
    if (this.isHovered(mouseX, mouseY)) {
      // 1.21.8: tooltips are deferred to the end of the frame and drawn without the page transform, so convert the page relative mouse to screen space
      Vector2f screen = graphics.pose().transformPosition(mouseX, mouseY, new Vector2f());
      graphics.setTooltipForNextFrame(fontRenderer, this.tooltips, Optional.empty(), Math.round(screen.x), Math.round(screen.y));
    }
  }
}
