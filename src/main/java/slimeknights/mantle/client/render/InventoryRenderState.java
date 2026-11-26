package slimeknights.mantle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Render state for block entities that display their inventory at {@link RenderItem} positions.
 * 1.21.9: block entity rendering is split into extract and submit, so the items are resolved into {@link ItemStackRenderState}s
 * during extraction and the state never references the block entity.
 * Subclass it to add more extracted data (e.g. fluids) in other renderers.
 */
public class InventoryRenderState extends BlockEntityRenderState {
  /** Item placements, parallel to {@link #items} */
  public List<RenderItem> renderItems = List.of();
  /** Resolved item states, one per entry in {@link #renderItems} */
  public List<ItemStackRenderState> items = List.of();

  /**
   * Resolves the items to display. Call from {@code extractRenderState}.
   * @param resolver     Item model resolver from the renderer context
   * @param renderItems  Item placements, typically from {@link RenderItem#STATE_REGISTRY}
   * @param inventory    Gets the stack for the given placement index
   * @param level        Level for item model properties
   */
  public void extractItems(ItemModelResolver resolver, List<RenderItem> renderItems, IntFunction<ItemStack> inventory, @Nullable Level level) {
    this.renderItems = renderItems;
    if (renderItems.isEmpty()) {
      this.items = List.of();
      return;
    }
    List<ItemStackRenderState> items = new ArrayList<>(renderItems.size());
    for (int i = 0; i < renderItems.size(); i++) {
      ItemStackRenderState item = new ItemStackRenderState();
      RenderingHelper.extractItem(resolver, item, inventory.apply(i), renderItems.get(i), level);
      items.add(item);
    }
    this.items = items;
  }

  /** Returns true if there is nothing to submit */
  public boolean isEmpty() {
    for (ItemStackRenderState item : items) {
      if (!item.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Submits all items at their placements using {@link #lightCoords}. Any block rotation is up to the caller.
   * @param matrices   Pose stack
   * @param collector  Node collector
   */
  public void submitItems(PoseStack matrices, SubmitNodeCollector collector) {
    for (int i = 0; i < items.size(); i++) {
      RenderingHelper.submitItem(matrices, collector, items.get(i), renderItems.get(i), lightCoords);
    }
  }
}
