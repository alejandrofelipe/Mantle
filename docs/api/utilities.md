# Utilities

A grab-bag of smaller Mantle helpers: ready-to-extend item base classes, inventory/retextured block plumbing, loot conditions/functions plus a loot-table injector, a GUI menu base class, the `/mantle` command tree, and Mantle's own config options.

**What it's for** — Most mods re-implement the same boilerplate: an item that shows a tooltip, a block that opens a GUI and drops its inventory, a loot function that copies block-entity data onto a dropped item, or a container menu that lays out the player inventory correctly. Mantle ships small, focused base classes for all of these so a consuming mod can extend one class instead of copy-pasting vanilla glue. This page is a quick tour; each subsystem is independent, so jump to whichever subsection you need.

**Key types**

| Class | Purpose |
| --- | --- |
| `slimeknights.mantle.item.TooltipItem` | `Item` that auto-appends an optional `<id>.tooltip` translation. |
| `slimeknights.mantle.item.EdibleItem` | Food item (food set as a data component) that lists its effects in the tooltip. |
| `slimeknights.mantle.item.ContainerFoodItem` | Food/drink item that returns its crafting-remainder container when eaten. |
| `slimeknights.mantle.item.ContainerFoodItem.FluidContainerFoodItem` | `ContainerFoodItem` variant exposing a constant fluid for capability registration. |
| `slimeknights.mantle.item.LecternBookItem` | Abstract book item that can be placed on, and opened from, a lectern. |
| `slimeknights.mantle.block.InventoryBlock` | Abstract block that opens a `MenuProvider` GUI and drops its inventory on break. |
| `slimeknights.mantle.block.entity.InventoryBlockEntity` | Abstract block entity implementing `Container` + `MenuProvider` with an `IItemHandlerModifiable`. |
| `slimeknights.mantle.block.RetexturedBlock` | Abstract block that stores a "texture" item in its block entity and copies it on pick/drop. |
| `slimeknights.mantle.inventory.BaseContainerMenu` | `AbstractContainerMenu` base with correct player-inventory layout and quick-move. |
| `slimeknights.mantle.loot.MantleLoot` | Registry holder for Mantle's loot conditions, functions, and entry types. |
| `slimeknights.mantle.loot.LootTableInjector` | Datapack-driven injector that adds entries into existing loot tables. |
| `slimeknights.mantle.loot.entry.TagPreferenceLootEntry` | Loot entry that drops the preferred item from a tag. |
| `slimeknights.mantle.command.MantleCommand` | Root `/mantle` command and shared permission-level constants. |
| `slimeknights.mantle.config.Config` | Mantle's own client/server config options (heart renderer, fluid fog fix, tag preferences). |

---

## Item helpers

### TooltipItem

`TooltipItem` is an `Item` that, on hover, appends the translation `<item descriptionId>.tooltip` if it exists (via `TranslationHelper.addOptionalTooltip`). Newlines in the translation are split into separate lines. Just extend it; no override needed.

```java
public class MyManualItem extends TooltipItem {
  public MyManualItem(Properties properties) {
    super(properties);
  }
}
```

```json
// lang file
{
  "item.mymod.manual": "Manual",
  "item.mymod.manual.tooltip": "Right-click to read.\nKeep it dry."
}
```

### EdibleItem

`EdibleItem` is a food `Item` whose tooltip lists the food's status effects in gray. Food is now a data component, so the constructor that takes raw `FoodProperties` builds `new Properties().food(foodIn)` for you, and the `Properties` constructor asserts `DataComponents.FOOD` is present.

```java
// food directly
Item apple = new EdibleItem(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3f).build());

// or via Properties (must call .food(...))
Item bread = new EdibleItem(new Item.Properties().food(MyFoods.BREAD));
```

### ContainerFoodItem

`ContainerFoodItem` is a food/drink item that gives the player its container back when consumed. The container is the stack's crafting remainder (`stack.getCraftingRemainingItem()`), so set the remainder on the item `Properties`. It works for stackable foods and uses the `DRINK` animation by default (pass a `UseAnim` to change it). The static `ContainerFoodItem.addEffectTooltip(FoodProperties, List<Component>)` renders potion-style effect lines and is reusable from other items.

```java
Item milkBottle = new ContainerFoodItem(
  new Item.Properties()
    .food(MyFoods.MILK)
    .craftingRemainder(Items.GLASS_BOTTLE),
  UseAnim.DRINK);
```

`FluidContainerFoodItem` additionally carries a `Supplier<FluidStack>` (exposed via `getFluid()`) so a downstream mod can register a fluid-handler capability for it centrally — see the NeoForge notes below.

### LecternBookItem

`LecternBookItem` is an abstract `TooltipItem` implementing `ILecternBookItem`. `useOn` places the book on a clicked lectern, and the static `LecternBookItem.interactWithBlock(PlayerInteractEvent.RightClickBlock)` event handler opens the custom lectern screen instead of the vanilla one. Subscribe that handler to the NeoForge game bus and implement `openLecternScreenClient` (and override `openLecternScreen` if you need custom server logic). By default `openLecternScreen` sends Mantle's `OpenLecternBookPacket` to the player.

```java
public class MyBookItem extends LecternBookItem {
  public MyBookItem(Properties properties) {
    super(properties);
  }

  @Override
  public void openLecternScreenClient(BlockPos pos, ItemStack book) {
    MyBooks.GUIDE.openGui(pos, book);
  }
}

// register the lectern handler on the NeoForge game bus
NeoForge.EVENT_BUS.addListener(LecternBookItem::interactWithBlock);
```

---

## Inventory & retextured blocks

### InventoryBlock / InventoryBlockEntity

`InventoryBlock` is an abstract `Block` + `EntityBlock` that:

- opens its block entity's `MenuProvider` GUI on use (`openGui` → `ServerPlayer.openMenu`), syncing through `BaseContainerMenu.syncOnOpen` when the BE-menu is a `BaseContainerMenu`;
- drops the inventory on break, reading the item handler from `Capabilities.ItemHandler.BLOCK`;
- copies a named stack's `CUSTOM_NAME` onto an `INameableMenuProvider` block entity in `setPlacedBy`.

`InventoryBlockEntity` is the matching abstract block entity: it implements `Container` + `MenuProvider`, holds a `NonNullList<ItemStack>`, and exposes an `IItemHandlerModifiable` (an `InvWrapper`) via the `@Getter` `getItemHandler()`. It serializes items (and optionally the size) to NBT. It does **not** override `createMenu`, so your subclass provides the menu.

```java
public class CrucibleBlockEntity extends InventoryBlockEntity {
  public CrucibleBlockEntity(BlockPos pos, BlockState state) {
    // name, saveSizeToNBT, inventorySize
    super(MyRegistration.CRUCIBLE_BE.get(), pos, state,
          Component.translatable("gui.mymod.crucible"), false, 9);
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
    return new CrucibleMenu(id, playerInv, this);
  }
}
```

> **Capability registration is on you.** `InventoryBlockEntity.getItemHandler(Direction)` returns the handler, but the handler is *not* auto-exposed. A downstream mod must register `Capabilities.ItemHandler.BLOCK` for its concrete block-entity type on `RegisterCapabilitiesEvent`:
>
> ```java
> private void registerCapabilities(RegisterCapabilitiesEvent event) {
>   event.registerBlockEntity(
>     Capabilities.ItemHandler.BLOCK,
>     MyRegistration.CRUCIBLE_BE.get(),
>     (be, side) -> be.getItemHandler(side));
> }
> ```
>
> Mantle itself registers none, because `InventoryBlockEntity` is abstract and Mantle ships no concrete subtype.

### RetexturedBlock

`RetexturedBlock` is an abstract `Block` + `EntityBlock` whose block entity (an `IRetexturedBlockEntity`) stores a "texture" item; the block then renders as that texture (for connected-textures / camouflage style blocks). It wires the texture through placement, pick-block, and tooltip for you:

- `setPlacedBy` → `RetexturedBlock.updateTextureBlock(world, pos, stack)` copies the texture from the placed stack to the BE;
- `getCloneItemStack` → `RetexturedBlock.getPickBlock(world, pos, state)` returns a stack carrying the BE's current texture;
- `appendHoverText` → `RetexturedHelper.addTooltip(...)` shows the texture name.

Use it together with `IRetexturedBlockEntity` and `RetexturedHelper`. The static `updateTextureBlock` and `getPickBlock` are reusable from your own overrides.

---

## Loot: conditions, functions, the injector, and tag preference

`MantleLoot` registers Mantle's loot extensions to the vanilla registries (you reference them by id in JSON; the `DeferredHolder` fields are mostly for internal codec wiring):

| Id | Type | Purpose |
| --- | --- | --- |
| `mantle:block_tag` | condition | Matches a block by tag (+ optional state-property predicate) — a tag-based `LootItemBlockStatePropertyCondition`. |
| `mantle:tag_empty` / `mantle:tag_filled` | condition | Matches when a tag is empty / non-empty. |
| `mantle:has_context_set` | condition | For global loot modifiers: requires a given loot-context set to be present. |
| `mantle:fill_retextured_block` | function | Copies a retextured block entity's texture onto the dropped item (no config). |
| `mantle:set_fluid` | function | Fills the item's fluid-handler capability with a fixed `FluidStack`. |
| `mantle:tag_preference` | pool entry | Drops the preferred item from a tag (see below). |

`MantleLoot` also registers two global loot modifiers, `mantle:add_entry` (`AddEntryLootModifier`) and `mantle:replace_item` (`ReplaceItemLootModifier`).

### Block tag condition

In code, `BlockTagLootCondition` can be built directly for use in datagen:

```java
LootItemCondition.Builder condition =
  () -> new BlockTagLootCondition(MyTags.Blocks.ORES);
```

### Tag preference

`TagPreferenceLootEntry` drops a single item — the "preferred" entry of an item tag — chosen by `TagPreference.getPreference(TagKey)`, which sorts by the namespaces in `Config.TAG_PREFERENCES` (so e.g. `minecraft:` wins over a random addon). Use the builder in datagen:

```java
LootPoolSingletonContainer.Builder<?> entry =
  TagPreferenceLootEntry.tagPreference(MyTags.Items.COPPER_INGOTS);
```

You can query the same preference anywhere outside loot:

```java
Optional<Item> preferred = TagPreference.getPreference(MyTags.Items.COPPER_INGOTS);
```

### LootTableInjector

`LootTableInjector` is a singleton (`LootTableInjector.INSTANCE`) reload listener that *adds* entries to existing loot tables from datapack JSON under `data/<namespace>/mantle/loot_injectors/` (constant `LootTableInjector.FOLDER`). Each file names a target table and one or more pools; entries are appended to those pools on `LootTableLoadEvent`. Files support a `neoforge:conditions` block, and an empty file is ignored (handy for removals). Mantle wires it up via `LootTableInjector.init()`; consuming mods only need to ship the JSON (or generate it — see Datagen).

---

## Menu / GUI helper — BaseContainerMenu

`BaseContainerMenu<TILE extends BlockEntity>` is an `AbstractContainerMenu` base that fixes the two things every modded GUI gets wrong:

- **Player inventory layout.** Call `addInventorySlots()` (or `addInventorySlots(Inventory)`) to add the 3×9 + hotbar at the right offsets. Override `getInventoryXOffset()` / `getInventoryYOffset()` to move it. It enforces that the player inventory is added **last**: adding any slot afterward throws.
- **Quick-move + merge.** `quickMoveStack` shuttles between your slots and the player inventory, and the overridden `moveItemStackTo` respects `Slot.getMaxStackSize` (fixing a vanilla merge bug).

It also offers cross-player sync hooks — override `syncNewContainer(ServerPlayer)` and `syncWithOtherContainer(BaseContainerMenu<?>, ServerPlayer)`; `InventoryBlock` calls `syncOnOpen` for you. `getTile()` returns the bound block entity, and the static `BaseContainerMenu.getTileEntityFromBuf(buf, type)` reads a BE from a packet buffer (client side).

```java
public class CrucibleMenu extends BaseContainerMenu<CrucibleBlockEntity> {
  public CrucibleMenu(int id, Inventory playerInv, CrucibleBlockEntity tile) {
    super(MyRegistration.CRUCIBLE_MENU.get(), id, playerInv, tile);
    // add YOUR slots first ...
    this.addSlot(new SlotItemHandler(tile.getItemHandler(), 0, 80, 35));
    // ... then the player inventory LAST
    this.addInventorySlots();
  }
}
```

---

## The /mantle command

`MantleCommand.init()` registers the `/mantle` root command tree (it adds a `RegisterCommandsEvent` listener). Useful subcommands include:

- `/mantle tags view|entries|dump|for|preference` and `/mantle tags add|remove|...` (`ModifyTagCommand`) — inspect and runtime-modify tags.
- `/mantle dump_loot_modifiers` — list active global loot modifiers.
- `/mantle harvest_tiers` — dump harvest tier info.
- `/mantle remove recipes|...` — runtime data removal (requires permission level 2).
- `/mantle sources data` — show which datapacks provide a given loot table / recipe.
- `/mantle hunger` — hunger/saturation debug.

`MantleCommand` also exposes the standard permission-level constants other commands reuse: `PERMISSION_EDIT_SPAWN` (1), `PERMISSION_GAME_COMMANDS` (2), `PERMISSION_PLAYER_COMMANDS` (3), `PERMISSION_OWNER` (4), and the helper `requiresDebugInfoOrOp(source, reducedDebugLevel)` for gating debug-info commands.

---

## Mantle's Config

`Config` (`@Internal`) holds Mantle's own options across two specs, `Config.CLIENT_SPEC` and `Config.SERVER_SPEC`:

| Option | Spec | Default | Meaning |
| --- | --- | --- | --- |
| `Config.HEART_RENDERER` (`HeartRenderer` enum: `DISABLE` / `NO_MAX` / `WITH_MAX`) | client | `WITH_MAX` | Mantle's stacked-heart renderer; `DISABLE` falls back to the Forge renderer. |
| `Config.ENABLE_FLUID_FOG_FIX` | client | `true` | Corrects fluid lighting under fog (e.g. blindness); disable for shader compatibility. |
| `Config.TAG_PREFERENCES` (`List<? extends String>`) | server | namespace list (`minecraft`, `tconstruct`, …) | Namespace priority used by `TagPreference` for tag-based recipe/loot outputs. |

Read a value with `.get()`:

```java
if (Config.HEART_RENDERER.get() != Config.HeartRenderer.DISABLE) {
  // Mantle's heart renderer is active
}
```

`TAG_PREFERENCES` is the same list that drives `TagPreference` and the `mantle:tag_preference` loot entry, so changing it reorders which mod's item "wins" a tag.

---

## Datagen

### Loot table injectors

Extend `AbstractLootTableInjectionProvider` (a `GenericDataProvider`) and implement `addTables()`. The `inject(...)`, `injectChest(...)`, and `injectGameplay(...)` helpers return a `LootTableInjection.Builder`; call `addToPool(poolName, entries...)`. Optional `ICondition`s are written into the `neoforge:conditions` block.

```java
public class MyInjectors extends AbstractLootTableInjectionProvider {
  public MyInjectors(PackOutput output) {
    super(output, "mymod");
  }

  @Override
  protected void addTables() {
    // append a tag-preference entry into the "main" pool of the village chest table
    injectChest("village/village_armorer")
      .addToPool("main", TagPreferenceLootEntry.tagPreference(MyTags.Items.COPPER_INGOTS).build());
  }
}
```

### Retextured loot function

In a vanilla `LootTableProvider`/`BlockLootSubProvider`, apply `new RetexturedLootFunction()` to a block's drop so the broken block keeps its texture. (`RetexturedLootFunction` has a no-arg constructor for exactly this.)

---

## NeoForge 1.21.1 notes

- **`Block.use` is gone.** Blocks that open a GUI override `useWithoutItem(state, level, pos, player, hit)` (used when the player's hand is empty), not the old `use`. `InventoryBlock` already does this; item-in-hand interaction is a separate path (`useItemOn`).
- **Capability registration is central.** There is no per-block-entity `initCapabilities` and no per-item provider. Expose item handlers and fluid handlers on `RegisterCapabilitiesEvent` — `event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, beType, (be, side) -> be.getItemHandler(side))` for an `InventoryBlockEntity` subclass, and `event.registerItem(Capabilities.FluidHandler.ITEM, ..., item)` for a `FluidContainerFoodItem`. Mantle exposes the getters but registers nothing concrete itself.
- **Food is a data component.** `EdibleItem`/`ContainerFoodItem` read food from `DataComponents.FOOD` (set via `Properties.food(...)`); the removed `Item#foodProperties` field is gone, and tooltips iterate `FoodProperties.PossibleEffect`.
- **Loot is registry-driven.** Loot conditions/functions/entry types are registered through `DeferredRegister` to the vanilla registries (`Registries.LOOT_CONDITION_TYPE`, etc.) and use `MapCodec`s; `MantleLoot.init(modBus)` wires them. Loot tables are a reloadable registry, which is why `/mantle sources data` queries `reloadableRegistries()`.

See the [migration guide](../migration/1.20-to-1.21.1.md#11-items--blocks) for details.
