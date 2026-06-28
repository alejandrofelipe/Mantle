# JSON Predicates

Serializable, composable boolean tests over game objects (blocks, entities, damage sources, fluids) that you can author in datagen or hand-written JSON and combine with `and`/`or`/`inverted`.

## What it's for

Many features need a configurable "does this thing match?" check — a tool trait that only fires on undead mobs, a modifier that only works underwater, a recipe that only accepts source fluids. Mantle's predicate system lets your mod expose such checks as data: each predicate serializes to a small JSON object via a `RecordLoadable`, so designers (and other mods) can recombine your built-in checks without touching code. Every predicate is also a plain Java object you can build and evaluate directly with `matches(input)`.

## Key types

| Type | Purpose |
| --- | --- |
| `slimeknights.mantle.data.predicate.IJsonPredicate<I>` | Base interface: a serializable predicate over input `I`, with `matches(I)`, `inverted()`, and `getLoader()`. |
| `slimeknights.mantle.data.predicate.PredicateRegistry<T>` | Loader registry for one predicate family; supplies the shared `any` / `none` / `inverted` / `and` / `or` types and lets you register custom predicate loaders. |
| `slimeknights.mantle.data.predicate.TagPredicateRegistry<R,T>` | `PredicateRegistry` that also adds a `tag` predicate. |
| `slimeknights.mantle.data.predicate.RegistryPredicateRegistry<R,T>` | `TagPredicateRegistry` that also adds a `set` predicate (match against a fixed set of registry entries). |
| `slimeknights.mantle.data.predicate.block.BlockPredicate` | Family over `BlockState`. |
| `slimeknights.mantle.data.predicate.entity.LivingEntityPredicate` | Family over `LivingEntity`. |
| `slimeknights.mantle.data.predicate.damage.DamageSourcePredicate` | Family over `DamageSource`. |
| `slimeknights.mantle.data.predicate.fluid.FluidPredicate` | Family over `Fluid`. |
| `slimeknights.mantle.data.predicate.entity.MobTypePredicate` | Living-entity predicate matching an entity-type tag (replaces the removed 1.20 `MobType` categories). |

## How it works

`IJsonPredicate<I>` is the whole contract:

```java
public interface IJsonPredicate<I> extends IHaveLoader {
  boolean matches(I input);
  IJsonPredicate<I> inverted();
  RecordLoadable<? extends IJsonPredicate<I>> getLoader();
}
```

A *family* is an interface extending `IJsonPredicate<T>` for a concrete `T`, exposing a static `LOADER` (a `PredicateRegistry` subclass) plus a set of built-in constants. The `LOADER` is what serializes any predicate in that family and what you register custom types into.

### The four families and their built-ins

Each family ships ready-made constants you can use directly in code or reference by their registered id in JSON.

**`BlockPredicate`** (over `BlockState`) — `ANY`, `NONE`, `REQUIRES_TOOL`, `BLOCKS_MOTION`, `CAN_BE_REPLACED`. Plus statics `BlockPredicate.set(Block...)`, `BlockPredicate.tag(TagKey<Block>)`, `and(...)`, `or(...)`.

**`LivingEntityPredicate`** (over `LivingEntity`) — `ANY`, `NONE`, `FIRE_IMMUNE`, `ON_FIRE`, `WATER_SENSITIVE`, `CAN_FREEZE`, `IS_FREEZING`, `IS_IN_POWDERED_SNOW`, `ON_GROUND`, `CROUCHING`, `SPRINTING`, `BLOCKING`, `ELYTRA_FLYING`, `EYES_IN_WATER`, `FEET_IN_WATER`, `UNDERWATER`, `RAINING`. Plus `set(EntityType<?>...)`, `tag(TagKey<EntityType<?>>)`, `and(...)`, `or(...)`.

**`DamageSourcePredicate`** (over `DamageSource`) — `ANY`, `NONE`, `IS_INDIRECT`, `HAS_ENTITY`, `CAN_PROTECT`. Plus `tag(TagKey<DamageType>)`, `and(...)`, `or(...)` (no `set`, since damage sources are matched by damage-type tag).

**`FluidPredicate`** (over `Fluid`) — `ANY`, `NONE`, `SOURCE`, `HAS_BUCKET`, `LIGHTER_THAN_AIR`. Plus `set(Fluid...)`, `tag(TagKey<Fluid>)`, `and(...)`, `or(...)`.

### Combinators

Every family inherits the `and`/`or`/`inverted` combinators from `PredicateRegistry`:

```java
// require a block that needs a tool AND is solid
IJsonPredicate<BlockState> hard = BlockPredicate.and(BlockPredicate.REQUIRES_TOOL, BlockPredicate.BLOCKS_MOTION);

// any entity that is underwater OR on fire
IJsonPredicate<LivingEntity> wetOrBurning = LivingEntityPredicate.or(LivingEntityPredicate.UNDERWATER, LivingEntityPredicate.ON_FIRE);

// negate any predicate
IJsonPredicate<LivingEntity> notFireImmune = LivingEntityPredicate.FIRE_IMMUNE.inverted();

// evaluate directly
boolean result = wetOrBurning.matches(someLivingEntity);
```

`inverted()` is smart: inverting an already-inverted predicate returns the original, and the `LOADER` knows how to round-trip it through JSON. The `set` and `tag` helpers come from `RegistryPredicateRegistry`/`TagPredicateRegistry`:

```java
IJsonPredicate<BlockState> ironOrGold = BlockPredicate.set(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK);
IJsonPredicate<LivingEntity> undead    = LivingEntityPredicate.tag(EntityTypeTags.UNDEAD);
```

### Using a predicate in your own loadable

Because a family `LOADER` is itself a `Loadable`, you embed predicates as fields in your own `RecordLoadable` records. The registry is configured with an `any` default, so a missing field deserializes to "match anything":

```java
public record MyEffect(IJsonPredicate<LivingEntity> target, int level) {
  public static final RecordLoadable<MyEffect> LOADER = RecordLoadable.create(
    // omitting "target" in JSON defaults to the registry's "any" instance (matches everything)
    LivingEntityPredicate.LOADER.defaultField("target", MyEffect::target),
    IntLoadable.FROM_ONE.requiredField("level", MyEffect::level),
    MyEffect::new);

  public void apply(LivingEntity entity) {
    if (target.matches(entity)) {
      // ...
    }
  }
}
```

### Registering a custom predicate

To add a new predicate type to a family, write a class implementing the family interface with its own `RecordLoadable`, then register that loader into the family `LOADER` under a `ResourceLocation` id. Do this during mod construction (the same place Mantle registers its own, e.g. in your mod's constructor or a common setup listener).

```java
package com.example.mymod.predicate;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.data.predicate.entity.LivingEntityPredicate;

/** Matches living entities whose type is in the given entity-type tag. */
public record InTagPredicate(TagKey<EntityType<?>> tag) implements LivingEntityPredicate {
  public static final RecordLoadable<InTagPredicate> LOADER = RecordLoadable.create(
    Loadables.ENTITY_TYPE_TAG.requiredField("tag", InTagPredicate::tag),
    InTagPredicate::new);

  @Override
  public boolean matches(LivingEntity entity) {
    return entity.getType().builtInRegistryHolder().is(tag);
  }

  @Override
  public RecordLoadable<? extends IJsonPredicate<LivingEntity>> getLoader() {
    return LOADER;
  }
}
```

Register the loader once at startup:

```java
// e.g. in your mod constructor / FMLCommonSetupEvent enqueueWork
LivingEntityPredicate.LOADER.register(
  ResourceLocation.fromNamespaceAndPath("mymod", "in_tag"),
  InTagPredicate.LOADER);
```

The `type` key in JSON is the registered id. With the registration above, your predicate is authored as:

```json
{
  "type": "mymod:in_tag",
  "tag": "minecraft:skeletons"
}
```

The shared types are always available in any family without extra registration: `mantle:any`, `mantle:none`, `mantle:inverted` (field `inverted_type`), `mantle:and` / `mantle:or` (field `predicates`: a list), plus `mantle:tag` and (for registry families) `mantle:set`. A composed JSON predicate therefore looks like:

```json
{
  "type": "mantle:or",
  "predicates": [
    { "type": "mantle:in_tag_example" },
    { "type": "mantle:inverted", "inverted_type": { "type": "mymod:in_tag", "tag": "minecraft:undead" } }
  ]
}
```

## Datagen

Predicates serialize through their family `LOADER`. When a predicate is a field in one of your own `RecordLoadable` data objects, your existing provider writes it automatically — build the predicate with the Java helpers and the loadable emits the JSON above. For conditional datagen, `PredicateRegistry` exposes a `conditional(...)` builder that swaps which predicate is loaded based on NeoForge `ICondition`s (so an optional-dependency mod can degrade gracefully):

```java
// during datagen: use the undead tag if present, otherwise match nothing
IJsonPredicate<LivingEntity> target = LivingEntityPredicate.LOADER.conditional(
  LivingEntityPredicate.tag(EntityTypeTags.UNDEAD), // ifTrue
  LivingEntityPredicate.NONE,                       // ifFalse
  new TagEmptyCondition<>(/* ... */));              // ICondition(s)
```

The single-argument `conditional(ifTrue, conditions...)` overload falls back to the family's registered `none` when the conditions fail.

## NeoForge 1.21.1 notes

- **`MobType` is gone.** MC 1.21 removed `net.minecraft.world.entity.MobType` and its categories (undead, arthropod, illager, water). `MobTypePredicate` was reworked to match an **entity-type tag** instead — use vanilla tags such as `minecraft:undead`, `minecraft:arthropod`, `minecraft:illager`, or `minecraft:aquatic`. Note: as of this port its loader registration in `Mantle.java` (`getResource("mob_type")`) is still commented out pending wiring, so prefer the generic `mantle:tag` predicate (or your own tag predicate) until it is enabled.
- **`DamageSource#isIndirect` was removed.** `DamageSourcePredicate.IS_INDIRECT` now replicates it via `source.getDirectEntity() != source.getEntity()`.
- **`LivingEntity#hasEffect` takes a `Holder<MobEffect>`.** Effect-based predicates wrap the value with `BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect)`.

See the [migration guide](../migration/1.20-to-1.21.1.md#5-predicates) for details.
