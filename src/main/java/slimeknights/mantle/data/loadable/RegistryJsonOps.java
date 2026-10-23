package slimeknights.mantle.data.loadable;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.util.typed.TypedMap;

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

  /** Lazily builds the {@link BuiltInRegistries}-backed fallback ops. */
  private static DynamicOps<JsonElement> fallback() {
    if (fallback == null) {
      fallback = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
    }
    return fallback;
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
