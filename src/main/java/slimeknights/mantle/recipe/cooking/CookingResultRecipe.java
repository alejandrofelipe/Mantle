package slimeknights.mantle.recipe.cooking;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.EnumLoadable;
import slimeknights.mantle.data.loadable.primitive.FloatLoadable;
import slimeknights.mantle.recipe.helper.ItemOutput;

import java.util.List;

/** Simplifies the serializers for result recipes */
public interface CookingResultRecipe {
  LoadableField<CookingBookCategory, AbstractCookingRecipe> CATEGORY_FIELD = new EnumLoadable<>(CookingBookCategory.class).defaultField("category", CookingBookCategory.MISC, true, AbstractCookingRecipe::category);
  LoadableField<Float, AbstractCookingRecipe> EXPERIENCE_FIELD = FloatLoadable.FROM_ZERO.defaultField("experience",0f, AbstractCookingRecipe::experience);
  LoadableField<ItemOutput,CookingResultRecipe> RESULT_FIELD = ItemOutput.Loadable.REQUIRED_STACK.requiredField("result", CookingResultRecipe::getResult);

  /** Gets the recipe result */
  ItemOutput getResult();

  /**
   * 26.1: the vanilla cooking recipe needs a non-empty result template, but an {@link ItemOutput} (e.g. a tag) cannot be
   * resolved when recipes load. Result recipes pass this placeholder, then replace {@code assemble} and {@code display},
   * the only users of the vanilla result, so it is never crafted nor shown.
   */
  static ItemStackTemplate placeholderResult() {
    return new ItemStackTemplate(Items.BARRIER);
  }

  /**
   * Builds the recipe book display. The result slot stays empty, as with 1.21.11 where these recipes passed an empty
   * vanilla result.
   */
  static List<RecipeDisplay> display(AbstractCookingRecipe recipe, Item furnaceIcon) {
    return List.of(new FurnaceRecipeDisplay(
      recipe.input().display(), SlotDisplay.AnyFuel.INSTANCE, SlotDisplay.Empty.INSTANCE, new SlotDisplay.ItemSlotDisplay(furnaceIcon),
      recipe.cookingTime(), recipe.experience()));
  }
}
