package slimeknights.mantle.recipe.helper;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.function.Supplier;

/**
 * Simple implementation of a recipe serializer with no serialized properties.
 * 26.1: {@link RecipeSerializer} is a final record, so this class only builds serializers.
 */
public final class SimpleRecipeSerializer {
  private SimpleRecipeSerializer() {}

  /** Creates a serializer building a new recipe from the constructor for each decode */
  public static <T extends Recipe<?>> RecipeSerializer<T> of(Supplier<T> constructor) {
    // StreamCodec.unit validates value.equals(captured) on encode. These recipes have no equals() override, so the
    // instance loaded from the datapack (via codec()) never equals a freshly-constructed one, making update_recipes
    // sync throw "Can't encode ...". We carry no network data, so write nothing and rebuild a fresh instance on decode.
    return new RecipeSerializer<>(MapCodec.unit(constructor), StreamCodec.of((buffer, recipe) -> {}, buffer -> constructor.get()));
  }
}
