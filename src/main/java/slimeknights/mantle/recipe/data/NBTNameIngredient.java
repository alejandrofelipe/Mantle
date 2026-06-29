package slimeknights.mantle.recipe.data;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.common.CodecLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableIngredientSerializer;
import slimeknights.mantle.recipe.ingredient.MantleIngredients;

import javax.annotation.Nullable;
import java.util.stream.Stream;

/**
 * Ingredient for a component-sensitive item from another mod, used to reference items from optional mods during datagen.
 *
 * <p>In 1.20/Forge this extended {@code StrictNBTIngredient} and serialized to the {@code forge:nbt} JSON. In NeoForge
 * 1.21 NBT is replaced by data components and vanilla ingredients require the referenced item to exist, so instead this
 * is a custom {@link ICustomIngredient} serialized as {@code mantle:nbt_name} with a name and a component patch. Like
 * the strict ingredient it replaces, {@link #test} requires an exact component match.
 */
public class NBTNameIngredient implements ICustomIngredient {
  /** Serializer instance, registered as an {@link IngredientType} in {@link MantleIngredients} */
  public static final LoadableIngredientSerializer<NBTNameIngredient> SERIALIZER = new LoadableIngredientSerializer<>(RecordLoadable.create(
    Loadables.RESOURCE_LOCATION.requiredField("name", NBTNameIngredient::getName),
    new CodecLoadable<>(DataComponentPatch.CODEC).defaultField("components", DataComponentPatch.EMPTY, false, NBTNameIngredient::getComponents),
    NBTNameIngredient::new));

  private final ResourceLocation name;
  private final DataComponentPatch components;

  protected NBTNameIngredient(ResourceLocation name, DataComponentPatch components) {
    this.name = name;
    this.components = components;
  }

  /** Creates an ingredient for the given name and component patch */
  public static NBTNameIngredient from(ResourceLocation name, DataComponentPatch components) {
    return new NBTNameIngredient(name, components);
  }

  /** Creates an ingredient for an item that must have no extra components */
  public static NBTNameIngredient from(ResourceLocation name) {
    return new NBTNameIngredient(name, DataComponentPatch.EMPTY);
  }

  /** Gets the referenced item name */
  public ResourceLocation getName() {
    return name;
  }

  /** Gets the referenced component patch */
  public DataComponentPatch getComponents() {
    return components;
  }

  /** Builds the matching stack, or empty if the referenced item is absent */
  private ItemStack makeStack() {
    if (!BuiltInRegistries.ITEM.containsKey(name)) {
      return ItemStack.EMPTY;
    }
    ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(name));
    if (!components.isEmpty()) {
      stack.applyComponents(components);
    }
    return stack;
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    // strict match, mirroring the 1.20 StrictNBTIngredient this replaces
    return stack != null && !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, makeStack());
  }

  @Override
  public Stream<ItemStack> getItems() {
    ItemStack stack = makeStack();
    return stack.isEmpty() ? Stream.empty() : Stream.of(stack);
  }

  @Override
  public boolean isSimple() {
    return false;
  }

  @Override
  public IngredientType<?> getType() {
    return MantleIngredients.NBT_NAME.get();
  }
}
