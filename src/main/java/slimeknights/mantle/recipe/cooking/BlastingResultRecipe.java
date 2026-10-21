package slimeknights.mantle.recipe.cooking;

import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.MantleRecipes;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;

/** Extension of {@link BlastingRecipe} to support {@link ItemOutput} */
@Getter
public class BlastingResultRecipe extends BlastingRecipe implements CookingResultRecipe {
  public static LoadableField<Integer, AbstractCookingRecipe> COOKING_TIME_FIELD = IntLoadable.FROM_ONE.defaultField("cooking_time", 100, true, AbstractCookingRecipe::cookingTime);
  public static final RecordLoadable<BlastingResultRecipe> LOADABLE = RecordLoadable.create(
    LoadableRecipeSerializer.RECIPE_GROUP, CookingResultRecipe.CATEGORY_FIELD,
    IngredientLoadable.DISALLOW_EMPTY.requiredField("ingredient", r -> r.input()),
    RESULT_FIELD, EXPERIENCE_FIELD, COOKING_TIME_FIELD,
    BlastingResultRecipe::new);

  private final ItemOutput result;
  // 1.21: vanilla cooking recipes no longer carry a ResourceLocation id (the RecipeHolder does); constructor matches AbstractCookingRecipe(String, CookingBookCategory, Ingredient, ItemStack, float, int)
  public BlastingResultRecipe(String group, CookingBookCategory category, Ingredient ingredient, ItemOutput result, float experience, int cookingTime) {
    super(group, category, ingredient, ItemStack.EMPTY, experience, cookingTime);
    this.result = result;
  }

  @SuppressWarnings("unchecked")
  @Override
  public RecipeSerializer<BlastingRecipe> getSerializer() {
    // Mantle's own serializer deserializes into this subclass; cast to the type the vanilla parent declares (invariant RecipeSerializer)
    return (RecipeSerializer<BlastingRecipe>) (RecipeSerializer<?>) MantleRecipes.BLASTING.get();
  }

  /** Mantle's own result accessor; {@link net.minecraft.world.item.crafting.Recipe} dropped getResultItem in 1.21.4 */
  public ItemStack getResultItem(HolderLookup.Provider registries) {
    return result.get();
  }

  @Override
  public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
    return result.copy();
  }
}
