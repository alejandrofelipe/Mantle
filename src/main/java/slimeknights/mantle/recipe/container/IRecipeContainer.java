package slimeknights.mantle.recipe.container;

import net.minecraft.world.item.crafting.RecipeInput;

/**
 * {@link RecipeInput} extension for a recipe that only needs read access.
 * Used to control which slots a recipe gets and to prevent the need to implement a full container to get the recipe.
 */
public interface IRecipeContainer extends RecipeInput {
}
