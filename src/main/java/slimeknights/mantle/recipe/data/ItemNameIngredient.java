package slimeknights.mantle.recipe.data;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Ingredient for a non-NBT sensitive item from another mod, should never be used outside datagen.
 *
 * <p>TODO PORT (stage 7 datagen ingredients): in 1.20/Forge this extended {@code AbstractIngredient} and serialized to
 * the vanilla {@code {"item": id}} JSON via {@code VanillaIngredientSerializer}, allowing recipes to reference items
 * from mods that are not loaded at datagen time. In NeoForge 1.21 vanilla ingredients are backed by a {@code HolderSet}
 * which requires the referenced item to exist in the registry, so there is no drop-in equivalent. The cross-mod
 * datagen schema is undecided and there are no internal Mantle callers, so this is left as a minimal
 * {@link ICustomIngredient} stub. Stage 7 must decide whether to register an {@link IngredientType} for it (to
 * {@code NeoForgeRegistries.INGREDIENT_TYPES}) and what JSON form downstream mods (Tinkers) expect.
 */
public class ItemNameIngredient implements ICustomIngredient {
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
    throw new UnsupportedOperationException();
  }

  @Override
  public Stream<Holder<Item>> getItems() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSimple() {
    return false;
  }

  @Override
  public IngredientType<?> getType() {
    // TODO PORT (stage 7 datagen ingredients): no IngredientType registered; this ingredient is datagen-only and its
    // cross-mod JSON schema is undecided. See class javadoc.
    throw new UnsupportedOperationException("ItemNameIngredient is not yet ported to a NeoForge IngredientType (stage 7)");
  }
}
