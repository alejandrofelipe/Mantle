package slimeknights.mantle.plugin.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.recipe.crafting.ShapedRetexturedRecipe;

import java.util.List;
import java.util.Objects;

/**
 * JEI crafting extension to properly show {@link ShapedRetexturedRecipe} instances.
 * <p>
 * JEI 20 (MC 1.21.4) replaced the JEI 19 {@code setRecipe(RecipeHolder, IRecipeLayoutBuilder, ICraftingGridHelper,
 * IFocusGroup)} model with a {@link SlotDisplay}-based one: the extension supplies the grid inputs via
 * {@link #getIngredients(RecipeHolder)} (laid out by {@link #getWidth}/{@link #getHeight}), while the output is derived
 * by JEI from the recipe's own {@link net.minecraft.world.item.crafting.Recipe#display()}. The textured output variants
 * are injected through {@link #onDisplayedIngredientsUpdate} using slot display overrides, since the layout builder (and
 * therefore the old {@code builder.createFocusLink(...)}) is no longer handed to extensions. See the task report for the
 * one behavior that could not be carried over: the output&harr;texture-input focus link.
 */
public class RetexturableRecipeExtension implements ICraftingCategoryExtension<ShapedRetexturedRecipe> {
  /** Builds the list of textured display outputs for the recipe */
  private static List<ItemStack> displayOutputs(ShapedRetexturedRecipe recipe) {
    // set the output to display all variants from the texture ingredient
    Ingredient texture = recipe.getTexture();
    // fetch all stacks from the ingredient, note any variants that are not blocks will get a blank look
    RegistryAccess access = Objects.requireNonNull(SafeClientAccess.getRegistryAccess());
    List<ItemStack> displayOutputs = texture.items()
                                           .map(holder -> recipe.getResultItem(holder.value(), access))
                                           .toList();
    // empty display means the tag found nothing, so just use the original output
    return displayOutputs.isEmpty() ? List.of(recipe.getResultItem(access)) : displayOutputs;
  }

  @Override
  public List<SlotDisplay> getIngredients(RecipeHolder<ShapedRetexturedRecipe> recipeHolder) {
    // mirror vanilla ShapedRecipe#display(): one slot display per pattern cell, empty cells become SlotDisplay.Empty
    return recipeHolder.value().getIngredients().stream().map(Ingredient::optionalIngredientToDisplay).toList();
  }

  @Override
  public int getWidth(RecipeHolder<ShapedRetexturedRecipe> recipeHolder) {
    return recipeHolder.value().getWidth();
  }

  @Override
  public int getHeight(RecipeHolder<ShapedRetexturedRecipe> recipeHolder) {
    return recipeHolder.value().getHeight();
  }

  @Override
  public void onDisplayedIngredientsUpdate(RecipeHolder<ShapedRetexturedRecipe> recipeHolder, List<IRecipeSlotDrawable> recipeSlots, IFocusGroup focuses) {
    // the recipe's own display only carries the blank output, so override the output slot to cycle every textured variant
    List<ItemStack> outputs = displayOutputs(recipeHolder.value());
    for (IRecipeSlotDrawable slot : recipeSlots) {
      if (slot.getRole() == RecipeIngredientRole.OUTPUT) {
        slot.clearDisplayOverrides();
        slot.createDisplayOverrides().addItemStacks(outputs);
        break;
      }
    }
  }
}
