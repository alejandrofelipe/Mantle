package slimeknights.mantle.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * Extension of {@link Recipe} to set some methods that always set.
 * @param <C>  Recipe input type
 */
public interface ICommonRecipe<C extends RecipeInput> extends Recipe<C> {
  /**
   * Gets the recipe result. {@link Recipe} dropped {@code getResultItem} in 1.21.4, so this is now Mantle's own
   * accessor, used by {@link #assemble} and by code that displays recipe outputs.
   * @param access  Registry access. 26.1: {@link #assemble(RecipeInput)} has no registries, so it passes {@link RegistryAccess#EMPTY}
   * @return  Recipe output
   */
  ItemStack getResultItem(HolderLookup.Provider access);

  /** 26.1: {@link Recipe#assemble} lost its registry access, so the default passes {@link RegistryAccess#EMPTY} to {@link #getResultItem(HolderLookup.Provider)} */
  @Override
  default ItemStack assemble(C inv) {
    return getResultItem(RegistryAccess.EMPTY).copy();
  }

  /**
   * Mantle recipes are not shown in the vanilla recipe book (see {@link #isSpecial()}), so they are never placed.
   * @return  {@link PlacementInfo#NOT_PLACEABLE}
   */
  @Override
  default PlacementInfo placementInfo() {
    return PlacementInfo.NOT_PLACEABLE;
  }

  /**
   * Recipe book category. These recipes are special with an empty {@link #display()}, so this is never used by vanilla;
   * a vanilla constant is returned to satisfy the interface until a Tinkers category exists.
   * @return  {@link RecipeBookCategories#CRAFTING_MISC}
   */
  @Override
  default RecipeBookCategory recipeBookCategory() {
    return RecipeBookCategories.CRAFTING_MISC;
  }

  /**
   * Returns true to hide this recipe from the recipe book. Needed until NeoForge has proper recipe book support.
   * @return  True
   */
  @Override
  default boolean isSpecial() {
    return true;
  }
}
