# Data Loadables

Mantle's `Loadable<T>` is a single object that knows how to read/write a type from JSON **and** the network, so one declaration drives datagen, data loading, and packet sync.

## What it's for

In a data-driven mod you usually have to write the same shape three times: a JSON parser, a JSON serializer (for datagen), and a `FriendlyByteBuf` read/write pair (for client sync). A `Loadable<T>` bundles all of that into one immutable, composable value. You build small loadables for fields (items, fluids, tags, ints, strings) and compose them into a `RecordLoadable<T>` for your whole object, getting consistent JSON + network handling for free. Mantle uses this throughout its own recipes, modifiers, and book content, and exposes it for consuming mods to do the same. On 1.21.1 it also bridges cleanly into the Mojang/NeoForge `Codec` world that recipe serializers, ingredient types, and conditions now demand.

## Key types

| Type | Purpose |
| --- | --- |
| `slimeknights.mantle.data.loadable.Loadable<T>` | Core interface: JSON `convert`/`serialize` + network `encode`/`decode` in one object. |
| `slimeknights.mantle.data.loadable.record.RecordLoadable<T>` | A `Loadable` that always reads/writes a JSON object; built from fields via `RecordLoadable.create(...)`. |
| `slimeknights.mantle.data.loadable.field.RecordField<T,P>` | A single field inside a `RecordLoadable` (a loadable + key + getter). Produced by `requiredField`, `defaultField`, etc. |
| `slimeknights.mantle.data.loadable.field.LoadableField<T,P>` | Public field interface; `RecordField` plus standalone JSON/network helpers. |
| `slimeknights.mantle.data.loadable.Loadables` | Static constants for common types: `ITEM`, `BLOCK`, `FLUID`, `*_TAG`, `RESOURCE_LOCATION`, `ENTITY_TYPE`, ... |
| `slimeknights.mantle.data.loadable.primitive.StringLoadable<T>` | Loadable whose JSON form is a single string (and can be used as a map key). |
| `slimeknights.mantle.data.loadable.primitive.IntLoadable` | Bounded int loadable; constants `FROM_ZERO`, `FROM_ONE`, `ANY_SHORT`, ... |
| `slimeknights.mantle.data.loadable.common.ItemStackLoadable` | Item stack loadables (`OPTIONAL_STACK`, `REQUIRED_STACK_NBT`, ...). |
| `slimeknights.mantle.data.loadable.common.FluidStackLoadable` | Fluid stack loadables. |
| `slimeknights.mantle.data.loadable.common.IngredientLoadable` | `Loadable<Ingredient>` (`ALLOW_EMPTY` / `DISALLOW_EMPTY`). |
| `slimeknights.mantle.data.loadable.common.NBTLoadable` | `RecordLoadable<CompoundTag>` for reading NBT from JSON. |
| `slimeknights.mantle.data.registry.NamedComponentRegistry<T>` | A name-keyed registry that is itself a `Loadable<T>` (resource-location dispatch). |
| `slimeknights.mantle.data.gson.GenericRegisteredSerializer<T>` | Type-dispatched GSON (de)serializer using a `"type"` field. |

## How to use

### What a `Loadable<T>` gives you

Every loadable implements four operations (the rest are default helpers):

```java
// JSON (slimeknights.mantle.data.loadable.Loadable)
T convert(JsonElement element, String key, TypedMap context);
JsonElement serialize(T object);

// Network (slimeknights.mantle.data.loadable.Streamable)
T decode(FriendlyByteBuf buffer, TypedMap context);
void encode(FriendlyByteBuf buffer, T value);
```

`TypedMap context` is extra parsing context (recipe serializers stash their id/serializer there); pass `TypedMap.EMPTY` (or the no-context overloads) when you don't need it.

You rarely build a loadable from scratch. Instead you start from a constant in `Loadables` and map/compose it:

```java
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;

Item item = Loadables.ITEM.convert(json.get("item"), "item");     // ResourceLocation -> Item
Loadables.ITEM.serialize(Items.DIAMOND);                          // -> JsonPrimitive("minecraft:diamond")
int count = IntLoadable.FROM_ZERO.decode(buffer);                 // read a non-negative int from the network
```

### The `Loadables` constants you compose from

`Loadables` holds ready-made loadables for the common Minecraft/NeoForge types. The registry ones are `ResourceLocationLoadable<T>` (JSON is a resource-location string, network is the resource location):

```java
Loadables.RESOURCE_LOCATION   // StringLoadable<ResourceLocation>
Loadables.ITEM                // ResourceLocationLoadable<Item>
Loadables.BLOCK               // ResourceLocationLoadable<Block>
Loadables.FLUID               // ResourceLocationLoadable<Fluid>
Loadables.ENTITY_TYPE         // ResourceLocationLoadable<EntityType<?>>
Loadables.SOUND_EVENT, Loadables.MOB_EFFECT, Loadables.ATTRIBUTE, ...

Loadables.ITEM_TAG            // StringLoadable<TagKey<Item>>  (a "#namespace:path" string)
Loadables.BLOCK_TAG, Loadables.FLUID_TAG, Loadables.ENTITY_TYPE_TAG, ...
```

There are also non-empty variants (`NON_EMPTY_ITEM`, `NON_EMPTY_BLOCK`, `NON_EMPTY_FLUID`) and helpers `Loadables.tagKey(...)` / `Loadables.resourceKey(...)` to build tag/key loadables for any registry.

### Building a `RecordLoadable`

A `RecordLoadable<T>` reads and writes a JSON object. You assemble it from fields and a constructor with `RecordLoadable.create(field1, field2, ..., Constructor::new)`. Fields come from a loadable:

- `requiredField(key, getter)` — the key must be present.
- `defaultField(key, default, getter)` — falls back to `default` when absent; the 4-arg overload `defaultField(key, default, serializeDefault, getter)` controls whether the default is written back out.
- `nullableField(key, getter)` — optional, falling back to `null`.

The `getter` extracts that field's value from the finished object so `serialize`/`encode` can write it back. The order of the fields must match the constructor's argument order.

Worked example — a small "drop" record (an item produced with a weight):

```java
package com.example.mymod;

import net.minecraft.world.item.Item;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;

public record WeightedDrop(Item item, int count, int weight) {
  public static final RecordLoadable<WeightedDrop> LOADABLE = RecordLoadable.create(
    Loadables.ITEM.requiredField("item", WeightedDrop::item),
    IntLoadable.FROM_ONE.defaultField("count", 1, WeightedDrop::count),
    IntLoadable.FROM_ONE.requiredField("weight", WeightedDrop::weight),
    WeightedDrop::new);
}
```

That single `LOADABLE` now handles JSON both ways and the network both ways:

```java
// JSON: {"item": "minecraft:diamond", "weight": 5}  (count defaults to 1)
WeightedDrop drop = WeightedDrop.LOADABLE.deserialize(json);   // RecordLoadable adds deserialize(JsonObject)
JsonElement out  = WeightedDrop.LOADABLE.serialize(drop);

// Network sync
WeightedDrop.LOADABLE.encode(buffer, drop);
WeightedDrop received = WeightedDrop.LOADABLE.decode(buffer);
```

`RecordLoadable` adds the object-typed `deserialize(JsonObject, TypedMap)` / `serialize(T, JsonObject)` methods on top of the base `Loadable` ones, plus a `compact(...)` helper (used by `ItemStackLoadable.OPTIONAL_STACK` so a stack of count 1 can serialize as a bare item string).

### Mapping and collections

Because loadables compose, you can derive new ones without writing parsing code:

```java
// derive a type from an existing loadable
StringLoadable<MyEnum> ENUM = StringLoadable.DEFAULT.flatXmap(MyEnum::byName, MyEnum::getName);

// validate a parsed value
Loadable<Integer> POSITIVE = IntLoadable.ANY_SHORT.validate((value, error) -> {
  if (value <= 0) throw error.create("must be positive");
  return value;
});

// collections built from any loadable
Loadable<List<Item>>  ITEM_LIST = Loadables.ITEM.list();          // min size 1
Loadable<Set<Item>>   ITEM_SET  = Loadables.ITEM.set();
```

### Ready-made common loadables

For the awkward vanilla types, use the `common` package instead of rolling your own:

```java
ItemStackLoadable.REQUIRED_STACK        // RecordLoadable<ItemStack>, non-empty, variable count
ItemStackLoadable.OPTIONAL_STACK_NBT    // stack with data components, may be empty
FluidStackLoadable.REQUIRED_STACK_NBT   // FluidStack with components
IngredientLoadable.DISALLOW_EMPTY       // Loadable<Ingredient>, rejects empty
NBTLoadable.ALLOW_STRING                // RecordLoadable<CompoundTag>
```

### Name-keyed vs. type-dispatched registries

Two helpers cover the "pick an implementation from data" pattern:

**`NamedComponentRegistry<T>`** maps a `ResourceLocation` to a value and *is itself a `Loadable<T>`* (it extends `AbstractNamedComponentRegistry`, which implements `ResourceLocationLoadable`). Register your values, then use the registry directly as a field loadable — JSON is the value's name, network is the resource location:

```java
public static final NamedComponentRegistry<BoxExpansion> EXPANSIONS =
  new NamedComponentRegistry<>("Unknown box expansion");

static {
  EXPANSIONS.register(ResourceLocation.fromNamespaceAndPath("mymod", "cube"), CUBE);
}

// because EXPANSIONS is a Loadable<BoxExpansion>, it drops straight into a record:
RecordField<BoxExpansion,MyConfig> field = EXPANSIONS.requiredField("expansion", MyConfig::expansion);
```

**`GenericRegisteredSerializer<T>`** is a GSON `JsonSerializer`/`JsonDeserializer` that dispatches on a `"type"` field, where `T extends IJsonSerializable`. Register a `JsonDeserializer` per type id; on serialize each object writes its own `"type"`. Use it when the *shape* of the JSON varies by type (vs. `NamedComponentRegistry`, where the name selects a fixed singleton):

```java
public static final GenericRegisteredSerializer<MyTransform> SERIALIZER = new GenericRegisteredSerializer<>();

static {
  SERIALIZER.registerDeserializer(
    ResourceLocation.fromNamespaceAndPath("mymod", "scale"), ScaleTransform.DESERIALIZER);
}
```

### The codec bridge (feeding NeoForge APIs)

On 1.21.1 most vanilla/NeoForge extension points take `Codec`/`MapCodec`/`StreamCodec` rather than raw JSON. Every loadable can produce those without you re-describing the format:

```java
// Codec<T> backed by the loadable's JSON convert/serialize (runs through JsonOps)
Codec<WeightedDrop> codec = WeightedDrop.LOADABLE.codec();

// StreamCodec<RegistryFriendlyByteBuf,T> backed by encode/decode
StreamCodec<RegistryFriendlyByteBuf,WeightedDrop> streamCodec = WeightedDrop.LOADABLE.streamCodec();

// RecordLoadable additionally offers a MapCodec, which is what recipe serializers /
// ingredient types / ICondition want (a record always serializes to a JSON object,
// so the wrap is safe):
MapCodec<WeightedDrop> mapCodec = WeightedDrop.LOADABLE.mapCodec();
```

`recordLoadable.mapCodec()` plugs directly into a NeoForge `RecipeSerializer`, `IngredientType`, or `ICondition`; `loadable.codec()` / `loadable.streamCodec()` cover everywhere a plain `Codec`/`StreamCodec` is required. Internally `codec()` returns a `LoadableCodec<T>` and `mapCodec()` wraps it with `MapCodec.assumeMapUnsafe(...)`.

## Datagen

Loadables are the serialization half of datagen: when a Mantle (or your own) data provider writes a JSON file, it calls `loadable.serialize(value)` to produce the object, and the data loader on the other side calls `loadable.deserialize(json)` / `convert(...)`. So defining one `RecordLoadable<T>` is what makes your custom JSON both *generatable* and *loadable*:

```java
// in a data provider
JsonObject json = (JsonObject) WeightedDrop.LOADABLE.serialize(
  new WeightedDrop(Items.DIAMOND, 1, 5));
// -> {"item":"minecraft:diamond","weight":5}   (count omitted: it matches the default)
```

Use `defaultField(key, default, serializeDefault, getter)` with `serializeDefault = false` (the default) to keep generated files clean — values equal to the default are skipped, as `count` is above. Pass `true` when you want the field always present in output for clarity.

## NeoForge 1.21.1 notes

- **NBT → data components.** `ItemStackLoadable`/`FluidStackLoadable` no longer read a raw NBT compound for stack data; they read a `DataComponentPatch` under a `"components"` key (via a `CodecLoadable<>(DataComponentPatch.CODEC)`) and sync through the vanilla `ItemStack.OPTIONAL_STREAM_CODEC`. `NBTLoadable` still exists for genuine `CompoundTag` data (e.g. block entity NBT).
- **The codec bridge is new.** `Loadable.codec()` / `streamCodec()` and `RecordLoadable.mapCodec()` exist because 1.21.1 recipe serializers, ingredient types, and `ICondition` are now codec-based. Prefer these over hand-writing codecs.
- **Ingredients go through vanilla codecs.** `IngredientLoadable` delegates to `Ingredient.CODEC` / `CODEC_NONEMPTY` and `Ingredient.CONTENTS_STREAM_CODEC`, so NeoForge custom ingredients work automatically.
- **Registry changes.** Some registries moved: `Enchantment` is now a datapack registry (no `BuiltInRegistries.ENCHANTMENT`), so `Loadables.ENCHANTMENT` is a `ResourceKey`-based loadable resolved at use time, while `ENCHANTMENT_TAG` is still a tag-key loadable.
- **`RegistryFriendlyByteBuf`.** `streamCodec()` returns a `StreamCodec<RegistryFriendlyByteBuf,T>` because component/registry sync needs registry access on the buffer; `RegistryFriendlyByteBuf` extends `FriendlyByteBuf`, so existing `encode`/`decode` implementations are reused as-is.

See the [migration guide](../migration/1.20-to-1.21.1.md#4-data--loadables) for details.
