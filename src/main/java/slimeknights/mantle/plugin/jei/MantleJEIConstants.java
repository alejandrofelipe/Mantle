package slimeknights.mantle.plugin.jei;

import com.mojang.serialization.Codec;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.core.registries.BuiltInRegistries;
import slimeknights.mantle.recipe.ingredient.EntityIngredient.EntityInput;

public class MantleJEIConstants {
  /** Ingredient for an entity */
  public static final IIngredientType<EntityInput> ENTITY_TYPE = () -> EntityInput.class;

  /** Codec for entity ingredients, required by JEI 20's {@code IModIngredientRegistration#register} */
  public static final Codec<EntityInput> ENTITY_CODEC = BuiltInRegistries.ENTITY_TYPE.byNameCodec().xmap(EntityInput::new, EntityInput::type);

  /** Borrowed from {@link ICraftingGridHelper} implementation {@code CraftingGridHelper}. Ideally I'd call it from the API, but the API lacks all the information I need for that. */
  public static int getCraftingIndex(int i, int width, int height) {
    int index;
    if (width == 1) {
      if (height == 3) {
        index = (i * 3) + 1;
      } else if (height == 2) {
        index = (i * 3) + 1;
      } else {
        index = 4;
      }
    } else if (height == 1) {
      index = i + 3;
    } else if (width == 2) {
      index = i;
      if (i > 1) {
        index++;
        if (i > 3) {
          index++;
        }
      }
    } else if (height == 2) {
      index = i + 3;
    } else {
      index = i;
    }
    return index;
  }
}
