package slimeknights.mantle.client.screen.book.element;

import net.minecraft.client.input.MouseButtonInfo;
import slimeknights.mantle.client.screen.book.ArrowButton;

public class AnimationToggleElement extends ArrowElement {

  private final int arrowColorActive;
  private final int arrowColorInactive;
  private boolean toggled = false;

  public AnimationToggleElement(int x, int y, ArrowButton.ArrowType arrowType, int arrowColor, int arrowColorHover, int arrowColorActive, StructureElement structureElement) {
    super(x, y, arrowType, arrowColor, arrowColorHover, (button) -> {
      structureElement.canTick = !structureElement.canTick;
      structureElement.lastStep = -1;
    });

    this.arrowColorInactive = arrowColor;
    this.arrowColorActive = arrowColorActive;
  }

  @Override
  public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
    if (this.button != null && this.isHovered(mouseX, mouseY)) {
      // 1.21.9: onPress takes the input; Button ignores it, so pass the clicked button without modifiers
      this.button.onPress(new MouseButtonInfo(mouseButton, 0));
      this.toggled = !this.toggled;
      this.updateColor();
    }
  }

  protected void updateColor() {
    this.button.color = this.toggled ? this.arrowColorActive : this.arrowColorInactive;
  }
}
