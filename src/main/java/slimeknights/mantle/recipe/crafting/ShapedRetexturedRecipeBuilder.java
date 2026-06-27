package slimeknights.mantle.recipe.crafting;

import lombok.RequiredArgsConstructor;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;

import javax.annotation.Nullable;

// PORT NOTE (stage 7 datagen): the 1.20 builder allowed referencing a key-char from the shaped pattern as the texture source
//   (setSource(char)). In 1.21 the ShapedRecipePattern is opaque so the texture is always a full Ingredient. setSource(char)
//   is therefore unsupported; use setSource(Ingredient)/setSource(TagKey) instead.
@SuppressWarnings("unused")
@RequiredArgsConstructor(staticName = "fromShaped")
public class ShapedRetexturedRecipeBuilder {
  private final ShapedRecipeBuilder parent;
  private Ingredient texture = null;
  private boolean matchAll = false;

  /**
   * Sets the texture source to the given ingredient
   * @param texture Ingredient to use for texture
   * @return Builder instance
   */
  public ShapedRetexturedRecipeBuilder setSource(Ingredient texture) {
    this.texture = texture;
    return this;
  }

  /**
   * Sets the texture source to the given tag
   * @param tag Tag to use for texture
   * @return Builder instance
   */
  public ShapedRetexturedRecipeBuilder setSource(TagKey<Item> tag) {
    return setSource(Ingredient.of(tag));
  }

  /**
   * Sets the match first property on the recipe.
   * If set, the recipe uses the first ingredient match for the texture. If unset, all items that match the ingredient must be the same or no texture is applied
   * @return Builder instance
   */
  public ShapedRetexturedRecipeBuilder setMatchAll() {
    this.matchAll = true;
    return this;
  }

  /**
   * Builds the recipe using the given ID
   * @param output    Recipe output
   * @param location  Recipe location
   */
  public void build(RecipeOutput output, ResourceLocation location) {
    this.validate();
    parent.save(new RetexturedOutput(output, texture, matchAll), location);
  }

  /**
   * Ensures this recipe can be built
   * @throws IllegalStateException If the recipe cannot be built
   */
  private void validate() {
    if (texture == null) {
      throw new IllegalStateException("No texture defined for texture recipe");
    }
  }

  /** Recipe output that wraps the built shaped recipe into a {@link ShapedRetexturedRecipe} before forwarding */
  private record RetexturedOutput(RecipeOutput delegate, Ingredient texture, boolean matchAll) implements RecipeOutput {
    @Override
    public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
      Recipe<?> wrapped = recipe instanceof ShapedRecipe shaped ? new ShapedRetexturedRecipe(shaped, texture, matchAll) : recipe;
      delegate.accept(id, wrapped, advancement, conditions);
    }

    @Override
    public net.minecraft.advancements.Advancement.Builder advancement() {
      return delegate.advancement();
    }
  }
}
