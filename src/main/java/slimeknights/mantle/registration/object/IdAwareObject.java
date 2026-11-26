package slimeknights.mantle.registration.object;

import net.minecraft.resources.Identifier;

/**
 * Interface for an object that holds its own name, used to simplify some utilities
 * @param <T> Type of the ID returned by {@link #getId()}. Usually {@link Identifier}, but may be a typed
 *            wrapper (e.g. an ID class that wraps a {@link Identifier}) for consumers that want type safety.
 */
public interface IdAwareObject<T> {
  /** Gets the ID for this object */
  T getId();
}
