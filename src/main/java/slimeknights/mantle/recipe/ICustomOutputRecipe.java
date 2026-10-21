package slimeknights.mantle.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

import java.util.List;

/**
 * Recipe that has an output other than an {@link ItemStack}
 * @param <C>  Recipe input type
 */
public interface ICustomOutputRecipe<C extends RecipeInput> extends ICommonRecipe<C> {
  /** @deprecated Item stack output not supported */
  @Override
  @Deprecated
  default ItemStack getResultItem(HolderLookup.Provider access) {
    return ItemStack.EMPTY;
  }

  /** @deprecated Item stack output not supported */
  @Override
  @Deprecated
  default ItemStack assemble(C inv, HolderLookup.Provider access) {
    return ItemStack.EMPTY;
  }

  /** Custom output recipes are never placeable in the recipe book */
  @Override
  default PlacementInfo placementInfo() {
    return PlacementInfo.NOT_PLACEABLE;
  }

  /** Custom output recipes have no vanilla recipe display */
  @Override
  default List<RecipeDisplay> display() {
    return List.of();
  }
}
