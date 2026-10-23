package slimeknights.mantle.data.loadable;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.RegistryOps;
import slimeknights.mantle.data.JsonCodec;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.mantle.util.typed.TypedMapBuilder;

/** Implementation of a codec using a loadable. Note this will be inefficient comparatively when using {@link net.minecraft.nbt.NbtOps} */
public record LoadableCodec<T>(Loadable<T> loadable) implements JsonCodec<T> {
  @Override
  public T deserialize(JsonElement element, DynamicOps<?> ops) {
    // thread the caller's registry-aware ops into the loadable context so nested holder/tag/item loadables (notably
    // IngredientLoadable, e.g. a custom ingredient decoded via LoadableIngredientSerializer) resolve against the real
    // registries (with the reload's staged tags) instead of RegistryJsonOps' strict BuiltInRegistries fallback.
    return loadable.convert(element, "codec", jsonContext(ops));
  }

  @Override
  public JsonElement serialize(T object, DynamicOps<?> ops) {
    return loadable.serialize(object);
  }

  /**
   * Builds the loadable context carrying the registry-aware JSON ops. Only a {@link RegistryOps} (which includes
   * {@link net.neoforged.neoforge.common.conditions.ConditionalOps}) is threaded: a plain {@code JsonOps} carries no
   * registry, so forwarding it would strip the {@link RegistryJsonOps} fallback and reintroduce "without registry".
   */
  @SuppressWarnings("unchecked")
  private static TypedMap jsonContext(DynamicOps<?> ops) {
    if (ops instanceof RegistryOps) {
      return TypedMapBuilder.builder().put(ContextKey.OPS, (DynamicOps<JsonElement>) ops).build();
    }
    return TypedMap.EMPTY;
  }
}
