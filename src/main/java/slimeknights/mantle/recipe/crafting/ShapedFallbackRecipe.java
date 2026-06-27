package slimeknights.mantle.recipe.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
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
  private final List<ResourceLocation> alternatives;
  private List<CraftingRecipe> alternativeCache;

  /**
   * Creates a recipe using a shaped recipe as a base
   * @param base          Shaped recipe to copy data from
   * @param alternatives  List of recipe names to fail this match if they match
   */
  public ShapedFallbackRecipe(ShapedRecipe base, List<ResourceLocation> alternatives) {
    // ShapedRecipe.pattern is public final; result is package-private so read it via getResultItem (returns the stored result, ignoring registries)
    super(base.getGroup(), base.category(), base.pattern, base.getResultItem(null), base.showNotification());
    this.alternatives = alternatives;
  }

  @Override
  public boolean matches(CraftingInput input, Level world) {
    // if this recipe does not match, fail it
    if (!super.matches(input, world)) {
      return false;
    }

    // fetch all alternatives, fail if any match
    // cache to save effort down the line
    if (alternativeCache == null) {
      RecipeManager manager = world.getRecipeManager();
      alternativeCache = alternatives.stream()
                                     .map(manager::byKey)
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
    // fail if any alternative matches
    return this.alternativeCache.stream().noneMatch(recipe -> recipe.matches(input, world));
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return MantleRecipes.CRAFTING_SHAPED_FALLBACK.get();
  }

  /** Serializer composing the base shaped recipe with the alternatives list */
  public static class Serializer implements RecipeSerializer<ShapedFallbackRecipe> {
    private static final MapCodec<ShapedFallbackRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
      ShapedRecipe.Serializer.CODEC.forGetter(r -> (ShapedRecipe) r),
      Loadables.RESOURCE_LOCATION.list().codec().fieldOf("alternatives").forGetter(r -> r.alternatives)
    ).apply(inst, ShapedFallbackRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf,ShapedFallbackRecipe> STREAM_CODEC = StreamCodec.composite(
      ShapedRecipe.Serializer.STREAM_CODEC, r -> r,
      ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), r -> r.alternatives,
      ShapedFallbackRecipe::new);

    @Override
    public MapCodec<ShapedFallbackRecipe> codec() {
      return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf,ShapedFallbackRecipe> streamCodec() {
      return STREAM_CODEC;
    }
  }
}
