package slimeknights.mantle.data.loadable;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.util.typed.TypedMap;

import java.util.function.Supplier;

/**
 * Supplies a registry-aware {@link DynamicOps} for the JSON (de)serialization of loadables that touch items, tags, or
 * holders. In 1.21.4 {@link net.minecraft.world.item.crafting.Ingredient} (and any other {@code HolderSet}-backed codec)
 * must run through a registry-aware ops or it throws "Can't decode element without registry"/"can't be dereferenced".
 *
 * <p>Deserialization threads the real {@link RegistryOps} supplied by the caller (e.g. the recipe codec receives it from
 * NeoForge) through the loadable {@link TypedMap context} under {@link ContextKey#OPS}. When that is absent (serialization,
 * GSON bridges, or callers that provide no ops) we fall back to a {@link RegistryOps} backed by {@link BuiltInRegistries},
 * which is frozen with item/block/fluid tags bound and so resolves both direct entries and tags at runtime and in tests.
 */
public final class RegistryJsonOps {
  private RegistryJsonOps() {}

  private static DynamicOps<JsonElement> fallback;

  /**
   * Holds the caller's registry-aware ops for the duration of a GSON-bridged decode. GSON deserializers cannot
   * receive a {@link DynamicOps} parameter, so {@link slimeknights.mantle.data.JsonCodec.GsonCodec} parks the ops
   * here (via {@link #withThreadedOps}) and the deserializer reads it through {@link #deserialize()}. This mirrors
   * the {@link ContextKey#OPS} threading that {@link slimeknights.mantle.data.loadable.LoadableCodec} does for
   * loadable-based codecs, letting tag/holder lookups resolve against the reload's staged registries instead of
   * the strict {@link BuiltInRegistries} fallback (e.g. a loot-modifier condition's {@code #c:ores/cobalt} ingredient).
   */
  private static final ThreadLocal<DynamicOps<JsonElement>> THREADED_OPS = new ThreadLocal<>();

  /** Lazily builds the {@link BuiltInRegistries}-backed fallback ops. */
  private static DynamicOps<JsonElement> fallback() {
    if (fallback == null) {
      fallback = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
    }
    return fallback;
  }

  /**
   * Runs {@code action} with {@code ops} threaded for any GSON deserializer it reaches (see {@link #deserialize()}).
   * Only a {@link RegistryOps} is threaded, re-parented to {@link JsonOps} (GsonCodec already converted the input to
   * JSON); any other ops leaves the {@link #fallback()} in place. The previous value is restored afterward so nested
   * decodes and reentrancy are safe.
   */
  public static <T> T withThreadedOps(DynamicOps<?> ops, Supplier<T> action) {
    DynamicOps<JsonElement> threaded = ops instanceof RegistryOps<?> registryOps ? registryOps.withParent(JsonOps.INSTANCE) : null;
    if (threaded == null) {
      return action.get();
    }
    DynamicOps<JsonElement> previous = THREADED_OPS.get();
    THREADED_OPS.set(threaded);
    try {
      return action.get();
    } finally {
      if (previous != null) {
        THREADED_OPS.set(previous);
      } else {
        THREADED_OPS.remove();
      }
    }
  }

  /**
   * Gets the registry-aware JSON ops for a GSON-bridged deserializer: the ops threaded by {@link #withThreadedOps}
   * if one is active on this thread, else the {@link BuiltInRegistries}-backed {@link #fallback()}.
   */
  public static DynamicOps<JsonElement> deserialize() {
    DynamicOps<JsonElement> threaded = THREADED_OPS.get();
    return threaded != null ? threaded : fallback();
  }

  /** Gets the registry-aware JSON ops for the given context, preferring the caller's threaded ops. */
  public static DynamicOps<JsonElement> get(TypedMap context) {
    DynamicOps<JsonElement> ops = context.get(ContextKey.OPS);
    return ops != null ? ops : fallback();
  }

  /** Gets the registry-aware JSON ops for serialization, where no parsing context is available. */
  public static DynamicOps<JsonElement> serialize() {
    return fallback();
  }
}
