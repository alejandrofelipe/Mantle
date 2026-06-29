package slimeknights.mantle.registration.object;

import net.minecraft.resources.ResourceLocation;

/**
 * Interface for an object that holds its own name, used to simplify some utilities
 * @param <T> Type of the ID returned by {@link #getId()}. Usually {@link ResourceLocation}, but may be a typed
 *            wrapper (e.g. an ID class that wraps a {@link ResourceLocation}) for consumers that want type safety.
 */
public interface IdAwareObject<T> {
  /** Gets the ID for this object */
  T getId();
}
