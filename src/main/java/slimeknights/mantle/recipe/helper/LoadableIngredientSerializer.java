package slimeknights.mantle.recipe.helper;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.data.loadable.record.RecordLoadable;

/**
 * Helper for building a NeoForge {@link IngredientType} from a {@link RecordLoadable}.
 * @param <T>  Custom ingredient type
 */
public record LoadableIngredientSerializer<T extends ICustomIngredient>(RecordLoadable<T> loadable) {
  /** Creates the ingredient type for this serializer */
  public IngredientType<T> ingredientType() {
    StreamCodec<RegistryFriendlyByteBuf,T> streamCodec = loadable.streamCodec();
    return new IngredientType<>(loadable.mapCodec(), streamCodec);
  }
}
