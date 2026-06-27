package slimeknights.mantle.recipe.data;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.common.conditions.ICondition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link RecipeOutput} wrapper, which adds conditions to every recipe passed through it.
 *
 * <p>In 1.20/Forge this wrapped {@code Consumer<FinishedRecipe>} and could additionally override the serializer
 * ({@code type}) of the produced JSON. In NeoForge 1.21 recipes serialize through their own serializer codec rather than
 * a {@code FinishedRecipe}, and conditions are applied via {@link RecipeOutput#withConditions(ICondition...)}, so the
 * condition path is the supported behavior. The serializer-override variants are retained for API compatibility but no
 * longer rewrite the recipe type; see {@link #wrap(RecipeSerializer)} / {@link #wrap(ResourceLocation)}.
 */
@SuppressWarnings("unused")  // API
public class ConsumerWrapperBuilder {
  private final List<ICondition> conditions = new ArrayList<>();
  @Nullable
  private final RecipeSerializer<?> override;
  @Nullable
  private final ResourceLocation overrideName;

  private ConsumerWrapperBuilder(@Nullable RecipeSerializer<?> override, @Nullable ResourceLocation overrideName) {
    this.override = override;
    this.overrideName = overrideName;
  }

  /**
   * Creates a wrapper builder with the default serializer
   * @return Default serializer builder
   */
  public static ConsumerWrapperBuilder wrap() {
    return new ConsumerWrapperBuilder(null, null);
  }

  /**
   * Creates a wrapper builder with a serializer override.
   * TODO PORT (stage 7 datagen ingredients): serializer overrides no longer rewrite the recipe type as recipes
   * serialize via their own codec in 1.21. The override is stored but currently unused; revisit if a downstream caller
   * needs to re-type a recipe during datagen.
   * @param override Serializer override
   * @return Default serializer builder
   */
  public static ConsumerWrapperBuilder wrap(RecipeSerializer<?> override) {
    return new ConsumerWrapperBuilder(override, null);
  }

  /**
   * Creates a wrapper builder with a serializer name override.
   * TODO PORT (stage 7 datagen ingredients): see {@link #wrap(RecipeSerializer)}.
   * @param override Serializer override
   * @return Default serializer builder
   */
  public static ConsumerWrapperBuilder wrap(ResourceLocation override) {
    return new ConsumerWrapperBuilder(null, override);
  }

  /**
   * Adds a conditional to the consumer
   * @param condition Condition to add
   * @return Added condition
   */
  @CanIgnoreReturnValue
  public ConsumerWrapperBuilder addCondition(ICondition condition) {
    conditions.add(condition);
    return this;
  }

  /**
   * Builds the wrapped recipe output, applying all stored conditions to every recipe.
   * @param output Base recipe output
   * @return Wrapped recipe output
   */
  public RecipeOutput build(RecipeOutput output) {
    if (conditions.isEmpty()) {
      return output;
    }
    return new Wrapped(output, conditions.toArray(new ICondition[0]));
  }

  /** Recipe output that adds the given conditions to every accepted recipe */
  private record Wrapped(RecipeOutput original, ICondition[] conditions) implements RecipeOutput {
    @Override
    public Advancement.Builder advancement() {
      return original.advancement();
    }

    @Override
    public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
      ICondition[] merged;
      if (conditions.length == 0) {
        merged = this.conditions;
      } else {
        merged = new ICondition[this.conditions.length + conditions.length];
        System.arraycopy(this.conditions, 0, merged, 0, this.conditions.length);
        System.arraycopy(conditions, 0, merged, this.conditions.length, conditions.length);
      }
      original.accept(id, recipe, advancement, merged);
    }
  }
}
