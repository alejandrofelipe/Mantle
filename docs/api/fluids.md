# Fluids

Mantle's fluid toolkit: a one-call deferred register that creates the still/flowing/bucket/block forms together, a model-driven `FluidType` for textures, a data-driven container fill/empty system, and per-fluid tooltip units.

## What it's for

Registering a fluid in vanilla/NeoForge means juggling four interdependent registry entries (the `FluidType`, the still and flowing `Fluid`s, the `LiquidBlock`, and the `BucketItem`), each needing suppliers for the others before any exists. `FluidDeferredRegister` collapses that into one builder chain and hands you a `FluidObject`/`FlowingFluidObject` that exposes every form plus recipe ingredient/output helpers. On top of that, Mantle gives consuming mods a JSON-driven way to make arbitrary items fillable/emptiable (`mantle/fluid_transfer`), a JSON-driven fluid texture/fog system so you don't write client code per fluid, and a tooltip system that displays fluid amounts in your own units (ingots, gems, etc.) instead of raw millibuckets.

## Key types

| Class | Purpose |
| --- | --- |
| `slimeknights.mantle.registration.deferred.FluidDeferredRegister` | Deferred register; `.register(name)` returns a `Builder` for the fluid set |
| `slimeknights.mantle.registration.object.FluidObject` | Result holding the still fluid, type, and bucket (no flowing form) |
| `slimeknights.mantle.registration.object.FlowingFluidObject` | `FluidObject` subclass adding flowing fluid, block, and the local/common tags |
| `slimeknights.mantle.fluid.TextureFluidType` | `FluidType` whose color/textures come from the JSON texture system |
| `slimeknights.mantle.fluid.InvertedFluidType` | `FluidType` adding a flipped in-world texture |
| `slimeknights.mantle.fluid.texture.FluidTextureManager` | Client reload listener resolving textures/color/fog per `FluidType` |
| `slimeknights.mantle.fluid.texture.FluidTexture` | Record describing a fluid's still/flowing/overlay/camera/fog data, plus its `Builder` |
| `slimeknights.mantle.fluid.texture.AbstractFluidTextureProvider` | Datagen base for `mantle/fluid_texture` JSON |
| `slimeknights.mantle.fluid.transfer.FluidContainerTransferManager` | Reload listener loading `mantle/fluid_transfer` JSON; `INSTANCE` is the singleton |
| `slimeknights.mantle.fluid.transfer.IFluidContainerTransfer` | A single fill/empty rule; `TransferResult` + `TransferDirection` live here |
| `slimeknights.mantle.fluid.transfer.EmptyFluidContainerTransfer` | Transfer that empties a fixed fluid out of an item |
| `slimeknights.mantle.fluid.transfer.FillFluidContainerTransfer` | Transfer that fills a `FluidIngredient` into an item |
| `slimeknights.mantle.fluid.transfer.AbstractFluidContainerTransferProvider` | Datagen base for `mantle/fluid_transfer` JSON |
| `slimeknights.mantle.fluid.FluidTransferHelper` | Runtime helper to move fluid between handlers / interact with tanks |
| `slimeknights.mantle.fluid.tooltip.FluidTooltipHandler` | Client handler turning amounts into unit tooltips; `INSTANCE` singleton |
| `slimeknights.mantle.fluid.tooltip.AbstractFluidTooltipProvider` | Datagen base for `mantle/fluid_tooltips` JSON |

## How to use

### Registering a fluid

Create one `FluidDeferredRegister` per mod and register it on the mod event bus alongside your other deferred registers. Each `.register(name)` call starts a `Builder`; chain a type, an optional bucket, an optional block, then finish with `flowing()` (still + flowing + block) or `unplacable()` (still only, no block).

```java
public static final FluidDeferredRegister FLUIDS = new FluidDeferredRegister(MOD_ID);

// A placeable fluid: TextureFluidType, default bucket, a tinted/lit LiquidBlock, and a forge "molten_copper" common tag.
public static final FlowingFluidObject<BaseFlowingFluid> MOLTEN_COPPER =
  FLUIDS.register("molten_copper")
        .type(FluidType.Properties.create().lightLevel(15).density(2000).viscosity(2000).temperature(1000))
        .commonTag()                       // common tag named after the fluid ("molten_copper")
        .bucket()                          // default BucketItem registered as "molten_copper_bucket"
        .block(MapColor.COLOR_ORANGE, 15)  // default LiquidBlock registered as "molten_copper_fluid"
        .flowing();                        // builds BaseFlowingFluid.Source + .Flowing

// An item-only fluid with no block (e.g. a "fluid" that only exists in tanks).
public static final FluidObject<UnplaceableFluid> POTION =
  FLUIDS.register("potion")
        .type()        // shorthand for a TextureFluidType with default properties
        .bucket()
        .unplacable();

// register on the mod bus
FLUIDS.register(modEventBus);
```

Builder notes drawn from the source:

- `.type(FluidType.Properties)` / `.type()` use `TextureFluidType`; `.invertedType(...)` / `.invertedType()` use `InvertedFluidType`; `.type(Supplier<? extends FluidType>)` lets you supply your own. Calling a type method twice throws.
- `.commonTag()` names the common (forge-namespace) tag after the fluid; `.commonTag(String)` sets a custom name. Without it the fluid only gets the local tag.
- `.bucket()` registers the default `BucketItem` as `<name>_bucket`; `.bucket(Function<Supplier<? extends Fluid>, Item>)` supplies a custom bucket. `.block(MapColor, int)` registers a default `LiquidBlock` as `<name>_fluid`; `.burningBlock(...)` and `.mobEffectBlock(...)` register the burning/effect variants.
- `.flowing()` returns a `FlowingFluidObject<BaseFlowingFluid>`; `.invertedFlowing()` uses `InvertedFluid`; `.flowing(createStill, createFlowing)` takes custom constructors. `.unplacable()` returns a `FluidObject` and throws if a block was set.

Consuming a `FluidObject` at runtime / in recipes:

```java
Fluid still = MOLTEN_COPPER.get();          // still fluid (alias: getStill())
Fluid flowing = MOLTEN_COPPER.getFlowing(); // flowing fluid (FlowingFluidObject only)
LiquidBlock block = MOLTEN_COPPER.getBlock();
Item bucket = MOLTEN_COPPER.getBucket();    // null if no bucket
TagKey<Fluid> tag = MOLTEN_COPPER.getTag(); // common tag if set, else local tag

// recipe helpers
FluidIngredient in = MOLTEN_COPPER.ingredient(FluidType.BUCKET_VOLUME);
FluidOutput out = MOLTEN_COPPER.result(FluidType.BUCKET_VOLUME);
```

### Client textures and fog

`TextureFluidType` / `InvertedFluidType` delegate `initializeClient` to `ClientTextureFluidType` (resp. `ClientInvertedFluidType`), which read everything from `FluidTextureManager`. So you do **not** write an `IClientFluidTypeExtensions` per fluid; you ship a `mantle/fluid_texture/<fluid_type_path>.json` (see datagen below) describing the still/flowing/overlay textures, tint color, and fog. The manager is a client reload listener and must be registered:

```java
// in your client mod setup, listening for RegisterClientReloadListenersEvent
FluidTextureManager.init(event);
FluidTooltipHandler.init(event); // if you use unit tooltips, register this here too
```

`FluidTextureManager.getData(type)` returns the `FluidTexture` (falling back to water), with convenience accessors `getStillTexture`, `getFlowingTexture`, `getOverlayTexture`, `getCameraTexture`, and `getColor`.

### Data-driven container fill/empty

`FluidContainerTransferManager` loads transfer rules from `data/<namespace>/mantle/fluid_transfer/`. Each rule is an `IFluidContainerTransfer`; the two built-ins are `EmptyFluidContainerTransfer` (`mantle:empty_item`, empties a fixed `FluidOutput` out of the matched item) and `FillFluidContainerTransfer` (`mantle:fill_item`, fills a `FluidIngredient` into the matched item). The manager must be initialized once on the server side:

```java
FluidContainerTransferManager.INSTANCE.init(); // wires up reload + datapack sync
```

To actually run a transfer against a tank, use `FluidTransferHelper`, which checks the JSON rules first and then falls back to the item's `Capabilities.FluidHandler.ITEM` capability:

```java
// on a tank block's useItemOn, after grabbing the BlockHitResult `hit`:
FluidInteractionResult result =
  FluidTransferHelper.interactWithContainer(level, pos, player, hand, hit);
if (result.didTransfer()) {
  // fluid moved; result is FILLED_STACK or DRAINED_STACK
}

// for GUI slots, get a TransferResult you can resolve with handleUIResult:
TransferResult tr = FluidTransferHelper.interactWithStack(tankHandler, stack, TransferDirection.AUTO);
stack = FluidTransferHelper.handleUIResult(player, stack, tr);

// raw handler-to-handler move:
FluidStack moved = FluidTransferHelper.tryTransfer(inputHandler, outputHandler, FluidType.BUCKET_VOLUME);
```

`TransferDirection` (on `IFluidContainerTransfer`) controls direction: `AUTO` (empty then fill), `EMPTY_ITEM`, `FILL_ITEM`, `REVERSE` (fill then empty).

### Fluid tooltips

`FluidTooltipHandler` formats a fluid amount into your own units. Out of the box it knows buckets/millibuckets; loading `mantle/fluid_tooltips/<id>.json` lets a fluid (matched by tag) report amounts in ingots, gems, etc., falling through to mB for the remainder. From a tooltip-building context:

```java
FluidStack fluid = tank.getFluid();
List<Component> tooltip = FluidTooltipHandler.getFluidTooltip(fluid); // name + units + mod name

// or append units into an existing list (handles the "hold shift for buckets" line)
FluidTooltipHandler.appendMaterial(fluid, tooltip);
FluidTooltipHandler.appendBuckets(fluid.getAmount(), tooltip); // force bucket/mB units
```

## Datagen

### Fluid transfer (`AbstractFluidContainerTransferProvider`)

Extend it, implement `addTransfers()`, and use `addTransfer` / `addFillEmpty`. `addFillEmpty` registers a matching empty+fill pair in one call and accepts a `FluidObject` directly:

```java
public class MyFluidTransferProvider extends AbstractFluidContainerTransferProvider {
  public MyFluidTransferProvider(PackOutput output) {
    super(output, MOD_ID);
  }

  @Override
  protected void addTransfers() {
    // explicit empty + fill rules for a glass bottle <-> potion-style item
    addTransfer("molten_copper/empty", new EmptyFluidContainerTransfer(
      Ingredient.of(MOLTEN_COPPER.getBucket()),       // matched item
      ItemOutput.fromItem(Items.BUCKET),              // result after emptying
      MOLTEN_COPPER.result(FluidType.BUCKET_VOLUME))); // fluid removed
    addTransfer("molten_copper/fill", new FillFluidContainerTransfer(
      Ingredient.of(Items.BUCKET),
      ItemOutput.fromItem(MOLTEN_COPPER.getBucket()),
      MOLTEN_COPPER.ingredient(FluidType.BUCKET_VOLUME)));

    // shorthand: registers "<prefix>empty" and "<prefix>fill" together (nbt=false here)
    addFillEmpty("molten_copper/", MOLTEN_COPPER.getBucket(), Items.BUCKET, MOLTEN_COPPER, FluidType.BUCKET_VOLUME, false);
  }
}
```

### Fluid textures (`AbstractFluidTextureProvider`)

Extend it, implement `addTextures()`, and build per-`FluidType` entries. Passing a non-null `modId` to the constructor makes it throw if any of your fluid types lacks a texture (use `skip(...)` to exempt one). The `FluidTexture.Builder` supports `root(...)` + `still()`/`flowing()`/`overlay()`/`camera()` auto-naming, or `wrapId(...)` to derive paths from the fluid-type id:

```java
public class MyFluidTextureProvider extends AbstractFluidTextureProvider {
  public MyFluidTextureProvider(PackOutput output) {
    super(output, MOD_ID);
  }

  @Override
  public void addTextures() {
    texture(MOLTEN_COPPER)                                       // accepts FluidObject
      .root(ResourceLocation.fromNamespaceAndPath(MOD_ID, "block/fluid/molten_metal/"))
      .still().flowing()                                          // <root>still, <root>flowing
      .color(0xFF_E0_7A_30)
      .fog(FogShape.SPHERE, 0.25f, 6f);
  }
}
```

### Fluid tooltips (`AbstractFluidTooltipProvider`)

Extend it, implement `addFluids()`, and use `add(...)` to start a `FluidUnitListBuilder` keyed by a fluid tag, then chain `addUnit(...)`. Order matters: list largest unit first, since each unit consumes its share and passes the remainder down (millibuckets are appended automatically by the handler).

```java
public class MyFluidTooltipProvider extends AbstractFluidTooltipProvider {
  public MyFluidTooltipProvider(PackOutput output) {
    super(output, MOD_ID);
  }

  @Override
  protected void addFluids() {
    add("molten_copper", MOLTEN_COPPER.getTag())
      .addUnit("block", 90 * 9)  // gui.<modid>.fluid.block
      .addUnit("ingot", 90);     // gui.<modid>.fluid.ingot

    addRedirect(id("molten_bronze"), id("molten_copper")); // reuse another list
  }
}
```

## NeoForge 1.21.1 notes

- **Component-based `FluidStack`.** `FluidStack` is no longer NBT-tagged; it carries a `DataComponentPatch`. Mantle uses `fluid.copyWithAmount(int)` (e.g. in `FluidTransferHelper.tryTransfer` and `FillFluidContainerTransfer`) instead of the old `new FluidStack(stack, amount)`, and equality is component-aware (`isSameFluidSameComponents`). Treat `FluidStack` as immutable-ish and copy before mutating.
- **`ForgeFlowingFluid` → `BaseFlowingFluid`.** The default flowing fluid built by `.flowing()` is `BaseFlowingFluid.Source` / `BaseFlowingFluid.Flowing`, and properties use `BaseFlowingFluid.Properties` (built in `FluidBuilder#build`). `FluidType` properties are created via `FluidType.Properties.create()`.
- **Registries are NeoForge keys.** Fluid types register under `NeoForgeRegistries.Keys.FLUID_TYPES` and resolve via `NeoForgeRegistries.FLUID_TYPES`; the register returns `DeferredHolder` rather than a Forge `RegistryObject`.
- **Capabilities.** Item/block fluid handlers come from `Capabilities.FluidHandler.ITEM` / `.BLOCK` (queried with a `Direction`), replacing the old capability-token lookups.
- **Datapack conditions.** Transfer-rule load conditions now serialize under the `neoforge:conditions` key via `ICondition.LIST_CODEC` (see `AbstractFluidContainerTransferProvider`); the old Forge `CraftingHelper` condition gate on load is not yet ported, so a `conditions` array in hand-written JSON is currently ignored.

See the [migration guide](../migration/1.20-to-1.21.1.md#6-fluids) for details.
