package slimeknights.mantle.recipe.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.recipe.MantleRecipes;

import java.util.List;

/**
 * Shaped recipe that fails to match if any of the listed alternative recipes match the same input.
 * Used to give an alternative recipe priority over this one.
 */
@SuppressWarnings("WeakerAccess")
public class ShapedFallbackRecipe extends ShapedRecipe {

  /** Recipes to skip if they match */
  private final List<Identifier> alternatives;
  private List<CraftingRecipe> alternativeCache;

  /**
   * Creates a recipe using a shaped recipe as a base
   * @param base          Shaped recipe to copy data from
   * @param alternatives  List of recipe names to fail this match if they match
   */
  public ShapedFallbackRecipe(ShapedRecipe base, List<Identifier> alternatives) {
    // 26.1: the shaped recipe takes the common and book info records; ShapedRecipe.pattern is public and the result is made
    // public by Mantle's access transformer
    super(new Recipe.CommonInfo(base.showNotification()), new CraftingRecipe.CraftingBookInfo(base.category(), base.group()), base.pattern, base.result);
    this.alternatives = alternatives;
  }

  @Override
  public boolean matches(CraftingInput input, Level world) {
    // if this recipe does not match, fail it
    if (!super.matches(input, world)) {
      return false;
    }

    // fetch all alternatives, fail if any match
    // cache to save effort down the line. 1.21.4: byKey takes a ResourceKey and the manager comes from Level#recipeAccess
    if (alternativeCache == null && world.recipeAccess() instanceof RecipeManager manager) {
      alternativeCache = alternatives.stream()
                                     .map(location -> manager.byKey(ResourceKey.create(Registries.RECIPE, location)))
                                     .filter(java.util.Optional::isPresent)
                                     .map(java.util.Optional::get)
                                     .map(RecipeHolder::value)
                                     .filter(recipe -> {
                                       // only allow exact shaped or shapeless match, prevent infinite recursion due to complex recipes
                                       Class<?> clazz = recipe.getClass();
                                       return clazz == ShapedRecipe.class || clazz == ShapelessRecipe.class;
                                     })
                                     .map(recipe -> (CraftingRecipe) recipe)
                                     .toList();
    }
    // if the manager was unavailable (e.g. client-side), behave as if no alternatives matched
    if (alternativeCache == null) {
      return true;
    }
    // fail if any alternative matches
    return this.alternativeCache.stream().noneMatch(recipe -> recipe.matches(input, world));
  }

  @SuppressWarnings("unchecked")
  @Override
  public RecipeSerializer<ShapedRecipe> getSerializer() {
    // cast to the type the vanilla parent declares (invariant RecipeSerializer)
    return (RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>) MantleRecipes.CRAFTING_SHAPED_FALLBACK.get();
  }

  /* Serializer composing the base shaped recipe with the alternatives list; 26.1: RecipeSerializer is a record of the two codecs */
  public static final MapCodec<ShapedFallbackRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
    ShapedRecipe.MAP_CODEC.forGetter(r -> r),
    Loadables.RESOURCE_LOCATION.list().codec().fieldOf("alternatives").forGetter(r -> r.alternatives)
  ).apply(inst, ShapedFallbackRecipe::new));

  public static final StreamCodec<RegistryFriendlyByteBuf,ShapedFallbackRecipe> STREAM_CODEC = StreamCodec.composite(
    ShapedRecipe.STREAM_CODEC, r -> r,
    Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), r -> r.alternatives,
    ShapedFallbackRecipe::new);
}
