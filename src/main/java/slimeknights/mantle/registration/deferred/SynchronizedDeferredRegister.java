package slimeknights.mantle.registration.deferred;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Deferred register instance that synchronizes register calls */
public class SynchronizedDeferredRegister<T> {
  private final DeferredRegister<T> internal;

  /** Wraps the given deferred register */
  public SynchronizedDeferredRegister(DeferredRegister<T> internal) {
    this.internal = internal;
  }

  /** Creates a new instance wrapping the given deferred register */
  public static <T> SynchronizedDeferredRegister<T> create(DeferredRegister<T> internal) {
    return new SynchronizedDeferredRegister<>(internal);
  }

  /** Creates a new instance for the given resource key */
  public static <T> SynchronizedDeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String modid) {
    return create(DeferredRegister.create(key, modid));
  }

  /** Creates a new instance for the given registry */
  public static <B> SynchronizedDeferredRegister<B> create(Registry<B> registry, String modid) {
    return create(DeferredRegister.create(registry, modid));
  }

  /** Registers the given object, synchronized over the internal register */
  public <I extends T> DeferredHolder<T, I> register(final String name, final Supplier<? extends I> sup) {
    synchronized (internal) {
      return internal.register(name, sup);
    }
  }

  /**
   * Registers the internal register with the event bus
   */
  public void register(IEventBus bus) {
    internal.register(bus);
  }
}
