package slimeknights.mantle.recipe.helper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import slimeknights.mantle.data.loadable.LoadableCodec;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.common.ItemStackLoadable;
import slimeknights.mantle.data.loadable.common.NBTLoadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.typed.TypedMap;

import org.jspecify.annotations.Nullable;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Class representing an item stack output. Supports both direct stacks and tag output, behaving like an ingredient used for output.
 * <p>
 * Outputs never hold an {@link ItemStack}: stacks need the item's default data components, which are bound only after a
 * datapack (re)load, so outputs keep the item, count and component patch and build a new stack on every {@link #get()}.
 * This lets outputs be decoded with recipes and loot, and built in datagen.
 */
public abstract class ItemOutput implements Supplier<ItemStack> {
  /* Codecs - just adding these as needed */
  /** Codec for an output that may not be empty with any size */
  public static Codec<ItemOutput> REQUIRED_STACK_CODEC = new LoadableCodec<>(Loadable.REQUIRED_STACK);

  /** Empty instance */
  public static final ItemOutput EMPTY = new Empty();


  /**
   * Gets the item output of this recipe. Builds a new stack on each call, so needs bound data components (after the datapack
   * load), but the result may be modified freely.
   * @return  Item output
   */
  @Override
  public abstract ItemStack get();

  /**
   * Gets a copy of the result stack
   * @return  Item output
   */
  public final ItemStack copy() {
    return get().copy();
  }

  /** Gets the size of the output without resolving the stack */
  public abstract int getCount();

  /** Checks if the contents are empty without resolving the stack */
  public boolean isEmpty() {
    return getCount() <= 0;
  }

  /** Gets the tag for this output. Will be {@code null} if this is not a tag output. */
  @Nullable
  public TagKey<Item> getTag() {
    return null;
  }

  /**
   * Writes this output to JSON
   * @param  writeCount  If true, serializes the count
   * @return  Json element
   */
  public abstract JsonElement serialize(boolean writeCount);

  /**
   * Creates a new output for the given stack
   * @param stack  Stack
   * @return  Output
   */
  public static ItemOutput fromStack(ItemStack stack) {
    if (stack.isEmpty()) {
      return EMPTY;
    }
    return new OfStack(ItemStackTemplate.fromNonEmptyStack(stack));
  }

  /**
   * Creates a new output for the given template. Unlike {@link #fromStack(ItemStack)}, works before data components are bound (e.g. datagen).
   * @param template  Template
   * @return  Output
   */
  public static ItemOutput fromTemplate(ItemStackTemplate template) {
    return new OfStack(template);
  }

  /**
   * Creates a new output for the given item
   * @param item  Item
   * @param count Stack count
   * @return  Output
   */
  public static ItemOutput fromItem(ItemLike item, int count) {
    return new OfItem(item.asItem(), count);
  }

  /**
   * Creates a new output for the given item
   * @param item  Item
   * @return  Output
   */
  public static ItemOutput fromItem(ItemLike item) {
    return fromItem(item, 1);
  }

  /**
   * Creates a new output for the given tag
   * @param tag   Tag
   * @param count Stack count
   * @param nbt   Stack NBT
   * @return Output
   */
  public static ItemOutput fromTag(TagKey<Item> tag, int count, @Nullable CompoundTag nbt) {
    return new OfTagPreference(tag, count, nbt);
  }

  /**
   * Creates a new output for the given tag
   * @param tag   Tag
   * @param count Stack count
   * @return Output
   */
  public static ItemOutput fromTag(TagKey<Item> tag, int count) {
    return fromTag(tag, count, null);
  }

  /**
   * Creates a new output for the given tag
   * @param tag  Tag
   * @return Output
   */
  public static ItemOutput fromTag(TagKey<Item> tag) {
    return fromTag(tag, 1);
  }

  /**
   * Writes this output to the packet buffer
   * @param buffer  Packet buffer instance
   */
  public void write(RegistryFriendlyByteBuf buffer) {
    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, get());
  }

  /**
   * Reads an item output from the packet buffer
   * @param buffer  Buffer instance
   * @return  Item output
   */
  public static ItemOutput read(RegistryFriendlyByteBuf buffer) {
    return fromStack(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
  }

  /** Empty output */
  private static class Empty extends ItemOutput {
    @Override
    public ItemStack get() {
      return ItemStack.EMPTY;
    }

    @Override
    public int getCount() {
      return 0;
    }

    @Override
    public JsonElement serialize(boolean writeCount) {
      // the empty stack needs no bound components, so the stack loadables still write it
      if (writeCount) {
        return ItemStackLoadable.OPTIONAL_STACK_NBT.serialize(ItemStack.EMPTY);
      }
      return ItemStackLoadable.OPTIONAL_ITEM_NBT.serialize(ItemStack.EMPTY);
    }
  }

  /** Class for an output that is just an item, simplifies NBT for serializing as vanilla forces NBT to be set for tools and forge goes through extra steps when NBT is set */
  @RequiredArgsConstructor
  private static class OfItem extends ItemOutput {
    private final Item item;
    @Getter
    private final int count;

    @Override
    public ItemStack get() {
      return new ItemStack(item, count);
    }

    @Override
    public JsonElement serialize(boolean writeCount) {
      JsonElement item = Loadables.ITEM.serialize(this.item);
      if (writeCount && count > 1) {
        JsonObject json = new JsonObject();
        json.add("item", item);
        json.addProperty("count", count);
        return json;
      } else {
        return item;
      }
    }
  }

  /** Class for an output that is a stack with components, kept as a template */
  @RequiredArgsConstructor
  private static class OfStack extends ItemOutput {
    private final ItemStackTemplate template;

    @Override
    public ItemStack get() {
      return new ItemStack(template.item(), template.count(), template.components());
    }

    @Override
    public int getCount() {
      return template.count();
    }

    @Override
    public JsonElement serialize(boolean writeCount) {
      if (writeCount) {
        return ItemStackLoadable.REQUIRED_STACK_NBT_TEMPLATE.serialize(template);
      }
      return ItemStackLoadable.REQUIRED_ITEM_NBT_TEMPLATE.serialize(template);
    }
  }

  /** Class for an output from a tag preference */
  @RequiredArgsConstructor
  private static class OfTagPreference extends ItemOutput {
    @Getter
    private final TagKey<Item> tag;
    @Getter
    private final int count;
    @Nullable
    private final CompoundTag nbt;
    @Nullable
    private Item cachedResult = null;

    @Override
    public ItemStack get() {
      // cache the result from the tag preference to save effort, especially helpful if the tag becomes invalid
      // this object should only exist in recipes so no need to invalidate the cache
      // 26.1: cache the item, not the stack, as stacks hold the default components bound by the datapack load
      if (cachedResult == null) {
        // if the preference is empty, do not cache it.
        // This should only happen if someone scans recipes before tag are computed in which case we cache the wrong result.
        // We protect against empty tags in our recipes via conditions.
        Optional<Item> preference = TagPreference.getPreference(tag);
        if (preference.isEmpty()) {
          return ItemStack.EMPTY;
        }
        cachedResult = preference.orElseThrow();
        // TODO PORT (stage 7): apply nbt as a DataComponentPatch once the item NBT -> component boundary is finalized
      }
      return new ItemStack(cachedResult, count);
    }

    @Override
    public JsonElement serialize(boolean writeCount) {
      JsonObject json = new JsonObject();
      if (!writeCount || count > 0) {
        json.addProperty("tag", tag.location().toString());
      }
      if (writeCount) {
        json.addProperty("count", count);
      }
      if (count > 0 && nbt != null) {
        json.add("nbt", NBTLoadable.ALLOW_STRING.serialize(nbt));
      }
      return json;
    }
  }

  /** Loadable logic for an ItemOutput */
  public enum Loadable implements RecordLoadable<ItemOutput> {
    /** Loadable for an output that may be empty with a fixed size of 1 */
    OPTIONAL_ITEM(false, false),
    /** Loadable for an output that may be empty with any size */
    OPTIONAL_STACK(false, true),
    /** Loadable for an output that may not empty with a fixed size of 1 */
    REQUIRED_ITEM(true, false),
    /** Loadable for an output that may not be empty with any size */
    REQUIRED_STACK(true, true);

    private final boolean nonEmpty;
    private final boolean readCount;
    /** Template loadable for the non-empty case; 26.1: data decodes into templates as stacks need bound components */
    private final RecordLoadable<ItemStackTemplate> template;
    Loadable(boolean nonEmpty, boolean readCount) {
      this.nonEmpty = nonEmpty;
      this.readCount = readCount;
      // figure out the template serializer to use based on the count parameter
      // we always do NBT, empty is handled before reading the template
      this.template = readCount ? ItemStackLoadable.REQUIRED_STACK_NBT_TEMPLATE : ItemStackLoadable.REQUIRED_ITEM_NBT_TEMPLATE;
    }

    @Override
    public ItemOutput deserialize(JsonObject json, TypedMap context) {
      if (json.has("tag")) {
        TagKey<Item> tag = Loadables.ITEM_TAG.getIfPresent(json, "tag", context);
        int count = 1;
        // 0 count field means we load count from JSON
        if (readCount) {
          count = IntLoadable.FROM_ONE.getOrDefault(json, "count", 1, context);
        }
        return fromTag(tag, count, NBTLoadable.ALLOW_STRING.getOrDefault(json, "nbt", null));
      }
      // optional outputs are empty for a missing or air item, or a 0 count, as the optional stack loadables were
      if (!nonEmpty && (Loadables.ITEM.getOrDefault(json, "item", Items.AIR, context) == Items.AIR
                        || readCount && IntLoadable.FROM_ZERO.getOrDefault(json, "count", 1, context) == 0)) {
        return EMPTY;
      }
      return fromTemplate(template.deserialize(json, context));
    }

    @Override
    public ItemOutput convert(JsonElement element, String key, TypedMap context) {
      // if it's a primitive, parse it directly with the template logic
      // that handles single items and ensures non-empty
      if (element.isJsonPrimitive()) {
        if (!nonEmpty && Loadables.ITEM.convert(element, key, context) == Items.AIR) {
          return EMPTY;
        }
        return fromTemplate(template.convert(element, key, context));
      }
      return deserialize(GsonHelper.convertToJsonObject(element, key), context);
    }

    @Override
    public void serialize(ItemOutput object, JsonObject json) {
      JsonElement element = serialize(object);
      if (element.isJsonObject()) {
        for (Entry<String,JsonElement> entry : element.getAsJsonObject().entrySet()) {
          json.add(entry.getKey(), entry.getValue());
        }
      } else {
        // if its a primitive, it must be the item field, so add that directly
        json.add("item", element);
      }
    }

    @Override
    public JsonElement serialize(ItemOutput output) {
      if (nonEmpty && output.isEmpty()) {
        throw new IllegalArgumentException("ItemOutput cannot be empty for this recipe");
      }
      return output.serialize(readCount);
    }

    @Override
    public ItemOutput decode(FriendlyByteBuf buffer, TypedMap context) {
      // tolerate an empty stack on the wire: the non-empty check is meant for JSON parsing, but vanilla's update_recipes
      // sync streams every loaded recipe and must not crash (and disconnect the player) if a result happens to be empty
      return fromStack(ItemStack.OPTIONAL_STREAM_CODEC.decode((RegistryFriendlyByteBuf) buffer));
    }

    @Override
    public void encode(FriendlyByteBuf buffer, ItemOutput object) {
      ItemStack.OPTIONAL_STREAM_CODEC.encode((RegistryFriendlyByteBuf) buffer, object.get());
    }


    /* Defaulting behavior */

    /** Gets the output, defaulting to empty. Note this will not stop you from getting empty with a non-empty loadable, thats on you for weirdly calling. */
    public ItemOutput getOrEmpty(JsonObject parent, String key) {
      return getOrDefault(parent, key, ItemOutput.EMPTY);
    }

    /** Creates a field defaulting to empty */
    public <P> LoadableField<ItemOutput,P> emptyField(String key, boolean serializeDefault, Function<P,ItemOutput> getter) {
      return defaultField(key, ItemOutput.EMPTY, serializeDefault, getter);
    }

    /** Creates a field defaulting to empty that does not serialize if empty */
    public <P> LoadableField<ItemOutput,P> emptyField(String key, Function<P,ItemOutput> getter) {
      return emptyField(key, false, getter);
    }
  }
}
