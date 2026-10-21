package slimeknights.mantle.registration.deferred;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import slimeknights.mantle.registration.object.EntityObject;

import java.util.function.Supplier;

/**
 * Deferred register for an entity, building the type from a builder instance and adding an egg
 */
@SuppressWarnings("unused")
public class EntityTypeDeferredRegister extends DeferredRegisterWrapper<EntityType<?>> {

  private final SynchronizedDeferredRegister<Item> itemRegistry;
  public EntityTypeDeferredRegister(String modID) {
    super(Registries.ENTITY_TYPE, modID);
    itemRegistry = SynchronizedDeferredRegister.create(Registries.ITEM, modID);
  }

  @Override
  public void register(IEventBus bus) {
    super.register(bus);
    itemRegistry.register(bus);
  }

  /**
   * Registers a entity type for the given entity type builder
   * @param name  Entity name
   * @param sup   Entity builder instance
   * @param <T>   Entity class type
   * @return  Entity registry object
   */
  public <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(String name, Supplier<EntityType.Builder<T>> sup) {
    return register.register(name, () -> sup.get().build(ResourceKey.create(Registries.ENTITY_TYPE, resource(name))));
  }

  /**
   * Registers a entity type for the given entity type builder, and registers a spawn egg for it
   * @param name       Entity name
   * @param sup        Entity builder instance
   * @param primary    Primary egg color
   * @param secondary  Secondary egg color
   * @param <T>   Entity class type
   * @return  Entity registry object
   */
  public <T extends Mob> EntityObject<T> registerWithEgg(String name, Supplier<EntityType.Builder<T>> sup, int primary, int secondary) {
    DeferredHolder<EntityType<?>, EntityType<T>> object = register(name, sup);
    // 1.21.4 dropped DeferredSpawnEggItem and spawn-egg colors; vanilla SpawnEggItem resolves the type at construction (eggs register after entity types)
    return new EntityObject<>(object, itemRegistry.register(name + "_spawn_egg", () -> new SpawnEggItem(object.get(), new Item.Properties().setId(ResourceKey.create(Registries.ITEM, resource(name + "_spawn_egg"))))));
  }
}
