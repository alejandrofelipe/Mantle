package slimeknights.mantle.recipe.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableIngredientSerializer;
import slimeknights.mantle.recipe.ingredient.MantleIngredients;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Ingredient matching items by registry name, used to reference items from optional mods during datagen.
 *
 * <p>In 1.20/Forge this serialized to the vanilla {@code {"item": id}} JSON via {@code VanillaIngredientSerializer},
 * letting recipes reference items from mods that are not loaded at datagen time. In NeoForge 1.21 vanilla ingredients
 * are backed by a {@code HolderSet} that requires the referenced item to exist, so instead this is a custom
 * {@link ICustomIngredient} serialized as {@code mantle:item_name} with a list of names. Recipes using it are guarded
 * by mod/item conditions, so {@link #test} and {@link #getItems} only run when the referenced items are present.
 */
public class ItemNameIngredient implements ICustomIngredient {
  /** Serializer instance, registered as an {@link IngredientType} in {@link MantleIngredients} */
  public static final LoadableIngredientSerializer<ItemNameIngredient> SERIALIZER = new LoadableIngredientSerializer<>(RecordLoadable.create(
    Loadables.RESOURCE_LOCATION.list(1).requiredField("names", ItemNameIngredient::getNames),
    ItemNameIngredient::new));

  private final List<ResourceLocation> names;
  protected ItemNameIngredient(List<ResourceLocation> names) {
    this.names = names;
  }

  /** Creates a new ingredient from a list of names */
  public static ItemNameIngredient from(List<ResourceLocation> names) {
    return new ItemNameIngredient(names);
  }

  /** Creates a new ingredient from a list of names */
  public static ItemNameIngredient from(ResourceLocation... names) {
    return from(Arrays.asList(names));
  }

  /** Gets the names referenced by this ingredient */
  public List<ResourceLocation> getNames() {
    return names;
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    return stack != null && names.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
  }

  @Override
  public Stream<ItemStack> getItems() {
    // only the items that are actually present in the registry; absent names (unloaded mods) are skipped
    return names.stream()
                .filter(BuiltInRegistries.ITEM::containsKey)
                .map(BuiltInRegistries.ITEM::get)
                .map(ItemStack::new);
  }

  @Override
  public boolean isSimple() {
    return true;
  }

  @Override
  public IngredientType<?> getType() {
    return MantleIngredients.ITEM_NAME.get();
  }
}
