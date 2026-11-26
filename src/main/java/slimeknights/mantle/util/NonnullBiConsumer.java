package slimeknights.mantle.util;

import java.util.function.BiConsumer;

/**
 * Equivalent to {@link BiConsumer}, except with nonnull contract.
 *
 * @see BiConsumer
 */
@FunctionalInterface
public interface NonnullBiConsumer<T,U> {
  void accept(T t, U u);
}
