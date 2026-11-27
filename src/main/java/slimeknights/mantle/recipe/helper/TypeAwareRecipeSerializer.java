package slimeknights.mantle.recipe.helper;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.function.Supplier;

/**
 * Type aware recipe serializer, for the sake of recipes that serializers to swap out the type.
 * 26.1: {@link RecipeSerializer} is a final record, so this pairs the registered serializer with its type instead of extending it.
 * @param serializer    Serializer to register and to return from {@link Recipe#getSerializer()}
 * @param typeSupplier  Recipe type created by this serializer
 */
public record TypeAwareRecipeSerializer<T extends Recipe<?>>(RecipeSerializer<T> serializer, Supplier<? extends RecipeType<?>> typeSupplier) {
  /** Gets the type of this recipe */
  public RecipeType<?> getType() {
    return typeSupplier.get();
  }
}
