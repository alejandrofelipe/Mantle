# Registration

Mantle's `registration` package wraps NeoForge's `DeferredRegister` with mod-id-aware helpers and grouped "object" wrappers, so a consuming mod can register a block (and its block item), a full set of building variants, a metal trio, or a fluid with one call.

## What it's for

Registering content on NeoForge means juggling a `DeferredRegister` per registry, deferred suppliers, and the relationship between a block and its `BlockItem` (or a fluid and its bucket/block/flowing form). Mantle's deferred-register wrappers take your mod id once and expose convenience `register*` methods that handle the paired registrations for you. The result of every call is a NeoForge `DeferredHolder` (or a Mantle wrapper around several of them), which you resolve later with `.get()`. The grouped wrapper objects (`BuildingBlockObject`, `WoodBlockObject`, `MetalItemObject`, `FluidObject`, `EnumObject`) bundle related entries so you can pass a whole family of blocks/items around as a single value and iterate it in datagen.

## Key types

| Class | Purpose |
| --- | --- |
| `slimeknights.mantle.registration.deferred.DeferredRegisterWrapper` | Abstract base; holds the synchronized register and your mod id, exposes `register(IEventBus)` |
| `slimeknights.mantle.registration.deferred.SynchronizedDeferredRegister` | Thread-safe wrapper around `DeferredRegister`, used internally by every wrapper |
| `slimeknights.mantle.registration.deferred.BlockDeferredRegister` | Registers blocks and (optionally) their block items, plus building/wood/metal/enum helpers |
| `slimeknights.mantle.registration.deferred.ItemDeferredRegister` | Registers items, including enum-keyed variants |
| `slimeknights.mantle.registration.deferred.FluidDeferredRegister` | Registers fluids, fluid types, buckets, and fluid blocks via a fluent `Builder` |
| `slimeknights.mantle.registration.deferred.EntityTypeDeferredRegister` | Registers entity types from a builder, optionally with a spawn egg |
| `slimeknights.mantle.registration.deferred.BlockEntityTypeDeferredRegister` | Registers block entity types bound to one or many blocks |
| `slimeknights.mantle.registration.deferred.MenuTypeDeferredRegister` | Registers menu (container) types from an `IContainerFactory` |
| `slimeknights.mantle.registration.deferred.EnumDeferredRegister` | Generic register for any registry, with enum-keyed variant helpers |
| `slimeknights.mantle.registration.object.ItemObject` | Supplier + `ItemLike` wrapper around a single registered block/item |
| `slimeknights.mantle.registration.object.BuildingBlockObject` | Block + slab + stairs group |
| `slimeknights.mantle.registration.object.WoodBlockObject` | Full wood set (planks, logs, fence, doors, signs, ...) |
| `slimeknights.mantle.registration.object.MetalItemObject` | Block + ingot + nugget group, with common tags |
| `slimeknights.mantle.registration.object.FluidObject` / `FlowingFluidObject` | Fluid (+ type, bucket; flowing variant adds flowing fluid and block) |
| `slimeknights.mantle.registration.object.EntityObject` | Entity type + spawn egg group |
| `slimeknights.mantle.registration.object.EnumObject` | Map of an enum value to a registered entry |
| `slimeknights.mantle.registration.RegistrationHelper` | Static helpers: `BUCKET_PROPS`, wood-type registration, casted holders |
| `slimeknights.mantle.registration.FluidBuilder` | Builds `BaseFlowingFluid.Properties` for the fluid register |

## How to use

### Create a register and hook it to the mod event bus

Construct each wrapper with your mod id (no need to pass the registry — each subclass knows its own), then call `register(IEventBus)` from your mod constructor. `DeferredRegisterWrapper` and its subclasses register on the mod event bus, exactly like a raw `DeferredRegister`.

```java
public final class MyMod {
  public static final String MOD_ID = "mymod";

  public static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(MOD_ID);
  public static final ItemDeferredRegister  ITEMS  = new ItemDeferredRegister(MOD_ID);

  public MyMod(IEventBus modBus) {
    BLOCKS.register(modBus);
    ITEMS.register(modBus);
  }
}
```

### Register a block + item, or a block with no item

`BlockDeferredRegister.register` returns an `ItemObject<B>` (it registers both the block and a `BlockItem` for you). `registerNoItem` returns a plain `DeferredHolder<Block, B>` with no item form. Resolve either later with `.get()`.

```java
// block + BlockItem in one call
public static final ItemObject<Block> SLATE =
  BLOCKS.register("slate", BlockBehaviour.Properties.of(), block -> new BlockItem(block, new Item.Properties()));

// block only (e.g. a fluid block or technical block)
public static final DeferredHolder<Block, Block> SEARED_DRAIN =
  BLOCKS.registerNoItem("seared_drain", BlockBehaviour.Properties.of());

// later, after registration has run
Block slate = SLATE.get();
```

### Register plain items

`ItemDeferredRegister.register` returns an `ItemObject<I>`. There are overloads for a supplier, an `Item.Properties`, or nothing (defaults).

```java
public static final ItemObject<Item> RUBY = ITEMS.register("ruby");                       // default props
public static final ItemObject<Item> COIN = ITEMS.register("coin", new Item.Properties()); // explicit props
public static final ItemObject<SwordItem> SWORD =
  ITEMS.register("ruby_sword", () -> new SwordItem(Tiers.IRON, new Item.Properties()));
```

`ItemObject` implements both `Supplier<I>` and `ItemLike`, and exposes `getId()`, `get()`, and `getOrNull()` (null instead of throwing if the entry has not resolved).

### Grouped helper objects

**`BuildingBlockObject` — block + slab + stairs.** `registerBuilding` registers all three from one set of properties (slab/stairs names are derived as `<name>_slab` / `<name>_stairs`). Related helpers add a wall (`registerWallBuilding` → `WallBuildingBlockObject`) or a fence (`registerFenceBuilding` → `FenceBuildingBlockObject`).

```java
public static final BuildingBlockObject MARBLE =
  BLOCKS.registerBuilding("marble", BlockBehaviour.Properties.of(),
                          block -> new BlockItem(block, new Item.Properties()));

Block      base   = MARBLE.get();
SlabBlock  slab   = MARBLE.getSlab();
StairBlock stairs = MARBLE.getStairs();
```

**`WoodBlockObject` — a full wood set.** `registerWood` builds planks (with slab/stairs), logs and wood (stripped variants too), fence, fence gate, door, trapdoor, pressure plate, button, and the four sign blocks, plus a `WoodType`. The `flammable` flag picks burnable item forms automatically.

```java
public static final WoodBlockObject GREENHEART =
  BLOCKS.registerWood("greenheart", variant -> BlockBehaviour.Properties.of(), true);

WoodType type = GREENHEART.getWoodType();
Block    log  = GREENHEART.getLog();
DoorBlock door = GREENHEART.getDoor();
```

**`MetalItemObject` — block + ingot + nugget.** `registerMetal` registers a storage block (named `<name>_block`) plus `<name>_ingot` and `<name>_nugget` items, and pre-creates the common (`c:`) tags for all three.

```java
public static final MetalItemObject COBALT =
  BLOCKS.registerMetal("cobalt", BlockBehaviour.Properties.of(),
                       block -> new BlockItem(block, new Item.Properties()), new Item.Properties());

Block block = COBALT.get();
Item  ingot = COBALT.getIngot();
Item  nugget = COBALT.getNugget();
TagKey<Item> ingotTag = COBALT.getIngotTag(); // c:ingots/cobalt
```

**`EnumObject` — enum-keyed registration.** Both `BlockDeferredRegister` and `ItemDeferredRegister` (and the generic `EnumDeferredRegister`) have `registerEnum` overloads. They register one entry per enum value, naming each `<value>_<name>` (prefix) or `<name>_<value>` (suffix) depending on argument order, and return an `EnumObject<E, V>` you query by enum value.

```java
public enum GemType implements StringRepresentable {
  RUBY, SAPPHIRE;
  @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}

public static final EnumObject<GemType, Item> GEMS =
  ITEMS.registerEnum(GemType.values(), "gem", type -> new Item(new Item.Properties()));
// registers "ruby_gem" and "sapphire_gem"

Item ruby = GEMS.get(GemType.RUBY);
```

`EnumObject` also offers `getOrNull`, `keys()`, `values()`, `entries()`, and `forEach` (which silently skips entries that have not yet resolved — handy during registry events).

### Fluids

`FluidDeferredRegister` exposes a fluent `Builder` via `register(name)`. Chain a `type`, an optional `bucket` and `block`, then finish with `flowing()` (returns `FlowingFluidObject`) or `unplacable()` (returns `FluidObject`). The builder wires up the still/flowing suppliers for you through a `DelayedSupplier`, solving the chicken-and-egg problem of fluid registration.

```java
public static final FluidDeferredRegister FLUIDS = new FluidDeferredRegister(MyMod.MOD_ID);

public static final FlowingFluidObject<BaseFlowingFluid> MOLTEN_COBALT =
  FLUIDS.register("molten_cobalt")
        .type(FluidType.Properties.create().lightLevel(15))
        .bucket()
        .block(MapColor.COLOR_BLUE, 15)
        .flowing();

FlowingFluid still   = MOLTEN_COBALT.getStill();
FlowingFluid flowing = MOLTEN_COBALT.getFlowing();
LiquidBlock  block   = MOLTEN_COBALT.getBlock();
```

Use `commonTag()` on the builder to also create a `c:` fluid tag; `FluidObject.ingredient(amount)` / `result(amount)` then build recipe ingredients/outputs from that tag.

### Entities, block entities, and menus

```java
public static final EntityTypeDeferredRegister ENTITIES = new EntityTypeDeferredRegister(MOD_ID);
public static final EntityObject<MyMob> MY_MOB =
  ENTITIES.registerWithEgg("my_mob", () -> EntityType.Builder.of(MyMob::new, MobCategory.CREATURE), 0xFF0000, 0x00FF00);

public static final BlockEntityTypeDeferredRegister BLOCK_ENTITIES = new BlockEntityTypeDeferredRegister(MOD_ID);
public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MyBlockEntity>> MY_BE =
  BLOCK_ENTITIES.register("my_be", MyBlockEntity::new, SLATE);   // pass an ItemObject/Supplier<Block>

public static final MenuTypeDeferredRegister MENUS = new MenuTypeDeferredRegister(MOD_ID);
public static final DeferredHolder<MenuType<?>, MenuType<MyMenu>> MY_MENU =
  MENUS.register("my_menu", MyMenu::new);   // MyMenu::new matches IContainerFactory
```

`registerWithEgg` returns an `EntityObject<T>` (entity type + spawn egg). `BlockEntityTypeDeferredRegister.register` also accepts an `EnumObject` or a block-collecting `Consumer` to bind many blocks to one block entity type.

## Datagen

The grouped objects implement `MultiObject<T>`, so you can iterate every member in a data provider with `forEach` (or `values()`), avoiding the need to list each block by hand:

```java
@Override
protected void buildRecipes(RecipeOutput out) {
  MARBLE.forEach(block -> { /* e.g. add a stonecutting recipe per variant */ });
}

// loot tables, models, tags, ...
GREENHEART.forEach(block -> /* block in the wood set */);
```

`MetalItemObject` exposes the auto-created common tags (`getBlockTag`, `getBlockItemTag`, `getIngotTag`, `getNuggetTag`) for tag providers, and `FluidObject`/`FlowingFluidObject` expose `getCommonTag` / `getLocalTag` / `getTag` plus `ingredient(amount)` and `result(amount)` for recipe datagen. `WoodBlockObject` exposes `getLogBlockTag` / `getLogItemTag` and `getWoodType()`. Note `EnumObject.forEach` (and the grouped objects' `forEach`) intentionally skips unresolved suppliers, so it is safe to call inside registry-bound events.

## NeoForge 1.21.1 notes

- The wrappers build on `net.neoforged.neoforge.registries.DeferredRegister` (created with `DeferredRegister.create(ResourceKey<Registry<T>>, modID)`), wrapped by Mantle's `SynchronizedDeferredRegister`.
- Every `register*` call returns a `net.neoforged.neoforge.registries.DeferredHolder` (the 1.21 replacement for the old `RegistryObject`); resolve with `.get()` and read the id with `.getId()`.
- Registry keys come from `net.minecraft.core.registries.Registries` (e.g. `Registries.BLOCK`, `Registries.ITEM`, `Registries.MENU`); fluid types use `NeoForgeRegistries.Keys.FLUID_TYPES`. Holders for existing entries are looked up against `net.minecraft.core.registries.BuiltInRegistries`.
- Fluids use NeoForge's `FluidType` and `BaseFlowingFluid` (`Source`/`Flowing`); spawn eggs use `net.neoforged.neoforge.common.DeferredSpawnEggItem`; menus use `IMenuTypeExtension.create` with an `IContainerFactory`.
- `RegistrationHelper.handleMissingMappings` was removed: NeoForge 1.21 dropped `MissingMappingsEvent` / registry remapping with no direct replacement (see the TODO in `RegistrationHelper`).
- `ResourceLocation` is built via `ResourceLocation.fromNamespaceAndPath(namespace, path)` (the old constructor is gone), which is what `DeferredRegisterWrapper.resource(...)` uses internally.

See the [migration guide](../migration/1.20-to-1.21.1.md#3-registration) for details.
