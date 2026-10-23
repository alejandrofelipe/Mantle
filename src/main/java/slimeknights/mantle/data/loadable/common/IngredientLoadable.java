package slimeknights.mantle.data.loadable.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import slimeknights.mantle.data.loadable.ErrorFactory;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.RegistryJsonOps;
import slimeknights.mantle.util.typed.TypedMap;

import java.util.Optional;

/** Loadable for ingredients, handling NeoForge custom ingredients via the vanilla codecs. */
public enum IngredientLoadable implements Loadable<Ingredient> {
  /** Allows the empty ingredient ({@code []}) */
  ALLOW_EMPTY(Ingredient.CODEC),
  /** Disallows the empty ingredient (1.21.4 dropped Ingredient.CODEC_NONEMPTY; the standard codec already rejects empty ingredients) */
  DISALLOW_EMPTY(Ingredient.CODEC);

  private final Codec<Ingredient> codec;

  IngredientLoadable(Codec<Ingredient> codec) {
    this.codec = codec;
  }

  @Override
  public Ingredient convert(JsonElement element, String key, TypedMap context) {
    // registry-aware ops: in 1.21.4 an Ingredient is a HolderSet<Item>, so a string/tag ingredient fails to parse
    // ("can't be dereferenced"/"without registry") on a registry-less JsonOps
    return codec.parse(RegistryJsonOps.get(context), element).getOrThrow(ErrorFactory.JSON_SYNTAX_ERROR::create);
  }

  @Override
  public JsonElement serialize(Ingredient object) {
    // 1.21.4 datagen: a plain (non-custom) tag-backed ingredient must serialize as "#tag" without resolving its contents.
    // Encoding through Ingredient.CODEC with a registry-less JsonOps would stream the items (and resolving/isEmpty would
    // touch the unbound tag), so emit the tag reference directly from its key.
    if (!object.isCustom()) {
      Optional<TagKey<Item>> tag = object.getValues().unwrapKey();
      if (tag.isPresent()) {
        return new JsonPrimitive("#" + tag.get().location());
      }
      // empty check only for non-custom ingredients: Ingredient#isEmpty streams a custom ingredient's values, which
      // resolves its (possibly unbound-at-datagen) tags and throws "Missing tag". A custom ingredient is never the
      // vanilla empty ingredient anyway, and its own codec serializes tag keys without resolving them.
      if (object.isEmpty() && this == DISALLOW_EMPTY) {
        throw new IllegalArgumentException("Ingredient cannot be empty");
      }
    }
    // registry-aware ops so a non-tag (item or custom) ingredient encodes its HolderSet against the real registries
    return codec.encodeStart(RegistryJsonOps.serialize(), object).getOrThrow(ErrorFactory.RUNTIME::create);
  }

  @Override
  public Ingredient decode(FriendlyByteBuf buffer, TypedMap context) {
    return Ingredient.CONTENTS_STREAM_CODEC.decode((RegistryFriendlyByteBuf) buffer);
  }

  @Override
  public void encode(FriendlyByteBuf buffer, Ingredient object) {
    Ingredient.CONTENTS_STREAM_CODEC.encode((RegistryFriendlyByteBuf) buffer, object);
  }
}
