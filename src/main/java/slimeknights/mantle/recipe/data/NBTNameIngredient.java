package slimeknights.mantle.recipe.data;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import javax.annotation.Nullable;
import java.util.stream.Stream;

/**
 * Ingredient for a component-sensitive item from another mod, should never be used outside datagen.
 *
 * <p>TODO PORT (stage 7 datagen ingredients): in 1.20/Forge this extended {@code StrictNBTIngredient} and serialized to
 * the {@code forge:nbt} JSON with a raw NBT tag, allowing recipes to reference items from mods not loaded at datagen
 * time. In NeoForge 1.21 {@code StrictNBTIngredient} is replaced by {@link net.neoforged.neoforge.common.crafting.DataComponentIngredient}
 * (NBT replaced by data components), and vanilla ingredients require the referenced item to exist in the registry. The
 * cross-mod datagen schema for an unloaded item plus a component patch is undecided and there are no internal Mantle
 * callers, so this is left as a minimal {@link ICustomIngredient} stub. Stage 7 must decide the JSON form and whether
 * to register an {@link IngredientType} for it.
 */
public class NBTNameIngredient implements ICustomIngredient {
  private final ResourceLocation name;
  @Nullable
  private final DataComponentPatch components;

  protected NBTNameIngredient(ResourceLocation name, @Nullable DataComponentPatch components) {
    this.name = name;
    this.components = components;
  }

  /**
   * Creates an ingredient for the given name and component patch
   * @param name        Item name
   * @param components  Component patch
   * @return  Ingredient
   */
  public static NBTNameIngredient from(ResourceLocation name, DataComponentPatch components) {
    return new NBTNameIngredient(name, components);
  }

  /**
   * Creates an ingredient for an item that must have no extra components
   * @param name  Item name
   * @return  Ingredient
   */
  public static NBTNameIngredient from(ResourceLocation name) {
    return new NBTNameIngredient(name, null);
  }

  /** Gets the referenced item name */
  public ResourceLocation getName() {
    return name;
  }

  /** Gets the referenced component patch, or null if none */
  @Nullable
  public DataComponentPatch getComponents() {
    return components;
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Stream<Holder<Item>> items() {
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
    throw new UnsupportedOperationException("NBTNameIngredient is not yet ported to a NeoForge IngredientType (stage 7)");
  }
}
