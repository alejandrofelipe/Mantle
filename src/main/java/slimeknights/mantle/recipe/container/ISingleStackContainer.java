package slimeknights.mantle.recipe.container;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/**
 * {@link Recipe} input wrapper containing a single item. Primarily used for furnace like recipes.
 */
public interface ISingleStackContainer extends IRecipeContainer {
  /**
   * Gets the relevant item in this inventory
   * @return  Contained item
   */
  ItemStack getStack();

  @Override
  default ItemStack getItem(int index) {
    return index == 0 ? getStack() : ItemStack.EMPTY;
  }

  @Override
  default boolean isEmpty() {
    return getStack().isEmpty();
  }

  /** @deprecated always 1, not useful */
  @Deprecated
  @Override
  default int size() {
    return 1;
  }
}
