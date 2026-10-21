package slimeknights.mantle.recipe.crafting;

import lombok.RequiredArgsConstructor;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Builder for a shaped recipe with fallbacks */
@SuppressWarnings("unused")
@RequiredArgsConstructor(staticName = "fallback")
public class ShapedFallbackRecipeBuilder {
  private final ShapedRecipeBuilder base;
  private final List<ResourceLocation> alternatives = new ArrayList<>();

  /**
   * Adds a single alternative to this recipe. Any matching alternative causes this recipe to fail
   * @param location  Alternative
   * @return  Builder instance
   */
  public ShapedFallbackRecipeBuilder addAlternative(ResourceLocation location) {
    this.alternatives.add(location);
    return this;
  }

  /**
   * Adds a list of alternatives to this recipe. Any matching alternative causes this recipe to fail
   * @param locations  Alternative list
   * @return  Builder instance
   */
  public ShapedFallbackRecipeBuilder addAlternatives(Collection<ResourceLocation> locations) {
    this.alternatives.addAll(locations);
    return this;
  }

  /**
   * Builds the recipe using the given ID
   * @param output  Recipe output
   * @param id      Recipe ID
   */
  public void build(RecipeOutput output, ResourceLocation id) {
    // 1.21.4: vanilla RecipeBuilder#save takes a ResourceKey<Recipe<?>>
    base.save(new FallbackOutput(output, List.copyOf(alternatives)), ResourceKey.create(Registries.RECIPE, id));
  }

  /** Recipe output that wraps the built shaped recipe into a {@link ShapedFallbackRecipe} before forwarding */
  private record FallbackOutput(RecipeOutput delegate, List<ResourceLocation> alternatives) implements RecipeOutput {
    @Override
    public void accept(ResourceKey<Recipe<?>> id, Recipe<?> recipe, @Nullable AdvancementHolder advancement) {
      accept(id, recipe, advancement, new ICondition[0]);
    }

    @Override
    public void accept(ResourceKey<Recipe<?>> id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
      Recipe<?> wrapped = recipe instanceof ShapedRecipe shaped ? new ShapedFallbackRecipe(shaped, alternatives) : recipe;
      delegate.accept(id, wrapped, advancement, conditions);
    }

    @Override
    public net.minecraft.advancements.Advancement.Builder advancement() {
      return delegate.advancement();
    }

    @Override
    public void includeRootAdvancement() {
      delegate.includeRootAdvancement();
    }
  }
}
