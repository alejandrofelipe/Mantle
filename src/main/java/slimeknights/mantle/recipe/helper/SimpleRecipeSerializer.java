package slimeknights.mantle.recipe.helper;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.function.Supplier;

/** Simple implementation of a recipe serializer with no serialized properties. */
public record SimpleRecipeSerializer<T extends Recipe<?>>(Supplier<T> constructor) implements RecipeSerializer<T> {
  @Override
  public MapCodec<T> codec() {
    return MapCodec.unit(constructor.get());
  }

  @Override
  public StreamCodec<RegistryFriendlyByteBuf,T> streamCodec() {
    // StreamCodec.unit validates value.equals(captured) on encode. These recipes have no equals() override, so the
    // instance loaded from the datapack (via codec()) never equals a freshly-constructed one, making update_recipes
    // sync throw "Can't encode ...". We carry no network data, so write nothing and rebuild a fresh instance on decode.
    return StreamCodec.of((buffer, recipe) -> {}, buffer -> constructor.get());
  }
}
