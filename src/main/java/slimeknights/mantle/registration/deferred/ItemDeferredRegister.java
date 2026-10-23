package slimeknights.mantle.registration.deferred;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.mantle.registration.object.ItemObject;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Deferred register that registers items with wrappers
 */
@SuppressWarnings("unused")
public class ItemDeferredRegister extends DeferredRegisterWrapper<Item> {

  public ItemDeferredRegister(String modID) {
    super(Registries.ITEM, modID);
  }

  /**
   * Adds a new item to the list to be registered, using the given supplier
   * @param name   Item name
   * @param sup    Supplier returning an item
   * @return  Item registry object
   */
  public <I extends Item> ItemObject<I> register(String name, Supplier<? extends I> sup) {
    return new ItemObject<>(register.register(name, sup));
  }

  /**
   * Adds a new item to the list to be registered, building it from the given properties with the ID set
   * @param name     Item name
   * @param factory  Function to create the item from the properties
   * @param props    Item properties
   * @return  Item registry object
   */
  public <I extends Item> ItemObject<I> register(String name, Function<Item.Properties, ? extends I> factory, Item.Properties props) {
    ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, resource(name));
    return register(name, () -> factory.apply(props.setId(key)));
  }

  /**
   * Adds a new item to the list to be registered, based on the given item properties
   * @param name   Item name
   * @param props  Item properties
   * @return  Item registry object
   */
  public ItemObject<Item> register(String name, Item.Properties props) {
    return register(name, Item::new, props);
  }

  /**
   * Adds a new item to the list to be registered, with default item properties
   * @param name   Item name
   * @return  Item registry object
   */
  public ItemObject<Item> register(String name) {
    return register(name, new Item.Properties());
  }


  /* Specialty */

  /**
   * Registers an item with multiple variants, prefixing the name with the value name
   * @param values   Enum values to use for this item
   * @param name     Name of the block
   * @param mapper   Function to get a item for the given enum value
   * @return  EnumObject mapping between different item types
   */
  public <T extends Enum<T>, I extends Item> EnumObject<T,I> registerEnum(T[] values, String name, Function<T,? extends I> mapper) {
    return registerEnum(values, name, (String fullName, T type) -> register(fullName, () -> mapper.apply(type)));
  }

  /**
   * Registers an item with multiple variants, suffixing the name with the value name
   * @param values   Enum values to use for this item
   * @param name     Name of the block
   * @param mapper   Function to get a item for the given enum value
   * @return  EnumObject mapping between different item types
   */
  public <T extends Enum<T>, I extends Item> EnumObject<T,I> registerEnum(String name, T[] values, Function<T,? extends I> mapper) {
    return registerEnum(name, values, (String fullName, T type) -> register(fullName, () -> mapper.apply(type)));
  }

  /* Enum variants that set the item id (1.21.4 requires Properties.setId before construction) */

  /** Creates an item resource key for the given name */
  private ResourceKey<Item> itemKey(String name) {
    return ResourceKey.create(Registries.ITEM, resource(name));
  }

  /**
   * Factory for an enum item variant that receives the item's resource key, so it can call
   * {@link Item.Properties#setId(ResourceKey)} before constructing the item. A dedicated interface (rather than
   * {@code BiFunction}) avoids an erasure clash with the base enum-register helper.
   */
  @FunctionalInterface
  public interface KeyedItemFactory<T, I extends Item> {
    I create(ResourceKey<Item> id, T value);
  }

  /**
   * Registers an item with multiple variants, prefixing the name with the value name. The factory receives the item's
   * resource key so it can call {@link Item.Properties#setId(ResourceKey)} before constructing the item.
   * @param values   Enum values to use for this item
   * @param name     Name of the item
   * @param factory  Factory to get an item for the given key and enum value
   * @return  EnumObject mapping between different item types
   */
  public <T extends Enum<T>, I extends Item> EnumObject<T,I> registerEnum(T[] values, String name, KeyedItemFactory<T,? extends I> factory) {
    return registerEnum(values, name, (String fullName, T type) -> register(fullName, () -> factory.create(itemKey(fullName), type)));
  }

  /**
   * Registers an item with multiple variants, suffixing the name with the value name. The factory receives the item's
   * resource key so it can call {@link Item.Properties#setId(ResourceKey)} before constructing the item.
   * @param name     Name of the item
   * @param values   Enum values to use for this item
   * @param factory  Factory to get an item for the given key and enum value
   * @return  EnumObject mapping between different item types
   */
  public <T extends Enum<T>, I extends Item> EnumObject<T,I> registerEnum(String name, T[] values, KeyedItemFactory<T,? extends I> factory) {
    return registerEnum(name, values, (String fullName, T type) -> register(fullName, () -> factory.create(itemKey(fullName), type)));
  }
}
