package slimeknights.mantle.recipe.ingredient;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.mantle.Mantle;

/** Registration for Mantle's custom {@link net.neoforged.neoforge.common.crafting.ICustomIngredient} types */
@SuppressWarnings("unused")
public class MantleIngredients {
  private static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, Mantle.modId);

  private MantleIngredients() {}

  /** Registers this to the bus */
  public static void init(IEventBus bus) {
    INGREDIENT_TYPES.register(bus);
  }

  public static final DeferredHolder<IngredientType<?>,IngredientType<PotionIngredient>> POTION = INGREDIENT_TYPES.register("potion", () -> PotionIngredient.SERIALIZER.ingredientType());
  public static final DeferredHolder<IngredientType<?>,IngredientType<PotionDisplayIngredient>> POTION_DISPLAY = INGREDIENT_TYPES.register("potion_display", () -> PotionDisplayIngredient.SERIALIZER.ingredientType());
  public static final DeferredHolder<IngredientType<?>,IngredientType<FluidContainerIngredient>> FLUID_CONTAINER = INGREDIENT_TYPES.register("fluid_container", () -> FluidContainerIngredient.SERIALIZER.ingredientType());
}
