package slimeknights.mantle.data.loadable.common;

import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.mapping.EnumMapLoadable;
import slimeknights.mantle.data.loadable.primitive.ResourceLocationLoadable;
import slimeknights.mantle.util.typed.TypedMap;

import java.util.Map;

/**
 * Special loadable for display contexts.
 * In 1.21 {@link ItemDisplayContext} is no longer a registry; it is a {@link net.minecraft.util.StringRepresentable} enum,
 * so values are keyed by their serialized name (parsed as a {@link ResourceLocation}).
 */
public enum DisplayContextLoadable implements ResourceLocationLoadable<ItemDisplayContext> {
  INSTANCE;

  /** Resolves a context from its serialized name. The serialized name doubles as the key path (and namespace for modded entries). */
  private static ItemDisplayContext byName(ResourceLocation name) {
    String serialized = name.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) ? name.getPath() : name.toString();
    for (ItemDisplayContext context : ItemDisplayContext.values()) {
      if (context.getSerializedName().equals(serialized)) {
        return context;
      }
    }
    return null;
  }

  @Override
  public ItemDisplayContext fromKey(ResourceLocation name, String key, TypedMap context) {
    ItemDisplayContext value = byName(name);
    if (value != null) {
      return value;
    }
    throw new JsonSyntaxException("Unable to parse " + key + " as no ItemDisplayContext matches ID " + name);
  }

  @Override
  public ResourceLocation getKey(ItemDisplayContext object) {
    return ResourceLocation.parse(object.getSerializedName());
  }

  @Override
  public ItemDisplayContext decode(FriendlyByteBuf buffer, TypedMap context) {
    return ItemDisplayContext.BY_ID.apply(buffer.readVarInt());
  }

  @Override
  public void encode(FriendlyByteBuf buffer, ItemDisplayContext value) {
    buffer.writeVarInt(value.getId());
  }

  @Override
  public <V> Loadable<Map<ItemDisplayContext,V>> mapWithValues(Loadable<V> valueLoadable, int minSize) {
    return new EnumMapLoadable<>(ItemDisplayContext.class, this, valueLoadable, minSize);
  }
}
