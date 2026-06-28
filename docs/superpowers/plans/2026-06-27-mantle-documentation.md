# Mantle Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce Mantle's consumer documentation for MC 1.21.1/NeoForge — a rewritten README, nine per-subsystem "how to use" API guides, a 1.20→1.21.1 migration guide, and a docs index.

**Architecture:** Hand-written English Markdown under a structured `docs/` folder (Approach A from the spec). Each API guide is one focused file documenting one subsystem; the migration guide is one file with per-subsystem before→after sections; the README stays slim and links out.

**Tech Stack:** Markdown. Content is sourced from the actual ported code in `src/main/java/slimeknights/mantle/**` (read it for real class/method names) and the port's git history (for migration before→after). Optional online research: the SlimeKnights wiki / KnightMiner's docs.

**Design spec:** [`docs/superpowers/specs/2026-06-27-mantle-documentation-design.md`](../specs/2026-06-27-mantle-documentation-design.md)

---

## Verification model (read first — this is documentation, not code)

There are no unit tests. Each doc task is verified by:
- **Accuracy:** every class/method/field named in the doc must exist in the code. Verify with `grep`/Read against `src/main/java` before committing. Do NOT invent API names — read the actual classes listed in each task.
- **Examples compile-in-spirit:** code examples use real Mantle/NeoForge 1.21.1 types and the real method signatures (cross-check against the ported source). They are illustrative snippets, not a test suite.
- **Links resolve:** relative Markdown links point to files that exist (or will exist by plan end). The README and docs index are written last (Tasks 11–12) so their links target already-created files.
- **Commit after each doc** (one file per task), conventional-commits messages.

Run any shell command per [`docs/COMMANDS.md`](../../COMMANDS.md) (PowerShell on this Windows box). No Gradle is needed for documentation.

## Shared style (apply to every API guide)

Each `docs/api/*.md` guide follows this shape and tone:
1. `# <Subsystem>` title + a one-sentence summary.
2. **What it's for** — 2–4 sentences: the problem it solves for a consuming mod.
3. **Key types** — a short table of the main classes (class → one-line purpose), with package paths.
4. **How to use** — prose + `java` code fences showing the common usage, using REAL class/method names.
5. **Datagen** — where the subsystem has providers/builders, show the datagen usage.
6. **NeoForge 1.21.1 notes / gotchas** — short bullets (e.g. capability registration is central, codec bridge, etc.), linking to the migration guide section for detail.

Code fences use ```java. Reference classes by their simple name in prose and give the full package in the Key-types table. Keep each guide focused and readable in one sitting.

## File Structure

| File | Responsibility |
|------|----------------|
| `docs/api/registration.md` | DeferredRegister wrappers + helper objects |
| `docs/api/data-loadables.md` | The `Loadable`/`RecordLoadable` serialization framework |
| `docs/api/predicates.md` | The `IJsonPredicate` system |
| `docs/api/fluids.md` | Fluids: object, type, transfer, tooltips, textures |
| `docs/api/recipes.md` | Recipe helpers, ingredients, conditions, builders |
| `docs/api/networking.md` | `CustomPacketPayload` helpers |
| `docs/api/client-models.md` | Model loaders + render helpers |
| `docs/api/books.md` | The in-game book system |
| `docs/api/utilities.md` | Item/block/loot/gui/command/config helpers |
| `docs/migration/1.20-to-1.21.1.md` | Per-subsystem before→after migration guide |
| `README.md` | Rewritten project README (slim, links out) |
| `docs/README.md` | Documentation index |

---

## Task 1: `docs/api/registration.md`

**Files:**
- Create: `docs/api/registration.md`
- Read for accuracy: `src/main/java/slimeknights/mantle/registration/deferred/*.java`, `src/main/java/slimeknights/mantle/registration/object/*.java`, `registration/RegistrationHelper.java`, `registration/FluidBuilder.java`

- [ ] **Step 1: Read the real API**

Read the deferred registers (`BlockDeferredRegister`, `ItemDeferredRegister`, `FluidDeferredRegister`, `EntityTypeDeferredRegister`, `BlockEntityTypeDeferredRegister`, `MenuTypeDeferredRegister`, `SynchronizedDeferredRegister`, `EnumDeferredRegister`, `DeferredRegisterWrapper`) and the helper objects (`ItemObject`, `BuildingBlockObject`, `WoodBlockObject`, `MetalItemObject`, `FluidObject`, `EnumObject`, `EntityObject`). Note the actual factory method names and that registered values are now `DeferredHolder<R,T>`.

- [ ] **Step 2: Write the guide** (per Shared style)

Cover: creating a `BlockDeferredRegister`/`ItemDeferredRegister` and registering, the convenience `register*` helpers, building grouped objects (`BuildingBlockObject` = block+slab+stairs, `WoodBlockObject`, `MetalItemObject` = block+ingot+nugget, `FluidObject`), and `EnumObject` for enum-keyed registration. Include a `java` example registering a block + item and reading the `DeferredHolder`/`.get()`. NeoForge notes: `DeferredRegister` is `net.neoforged.neoforge.registries.DeferredRegister`, values are `DeferredHolder`, registries come from `BuiltInRegistries`/`NeoForgeRegistries`; link migration §3.

- [ ] **Step 3: Verify names exist**

Run: `grep -rl "class BlockDeferredRegister\|class BuildingBlockObject\|class MetalItemObject\|class EnumObject" src/main/java/slimeknights/mantle/registration`
Expected: matching files found. Spot-check any class named in the doc with `grep`.

- [ ] **Step 4: Commit**

```bash
git add docs/api/registration.md
git commit -m "docs: add registration API guide"
```

---

## Task 2: `docs/api/data-loadables.md`

**Files:**
- Create: `docs/api/data-loadables.md`
- Read: `src/main/java/slimeknights/mantle/data/loadable/Loadable.java`, `data/loadable/record/RecordLoadable.java`, `data/loadable/Loadables.java`, `data/loadable/common/*.java`, `data/registry/NamedComponentRegistry.java`, `data/registry/GenericRegisteredSerializer.java`

- [ ] **Step 1: Read the real API**

Read `Loadable<T>` (note `convert`/`serialize` for JSON, `encode`/`decode` for network, and the added `codec()`/`streamCodec()` bridge), `RecordLoadable` (and its `mapCodec()`), the standard `Loadables` constants (`ITEM`, `BLOCK`, `FLUID`, the `*_TAG` loadables, `RESOURCE_LOCATION`, `ENTITY_TYPE`, etc.), `ItemStackLoadable`/`FluidStackLoadable`/`IngredientLoadable`, `NamedComponentRegistry`, and `GenericRegisteredSerializer`.

- [ ] **Step 2: Write the guide**

This is Mantle's signature API — give it depth. Cover: what a `Loadable<T>` is (JSON + network (de)serialization in one object), building a `RecordLoadable` for a record with `RecordLoadable.create(field1, field2, Constructor::new)` and `requiredField`/`defaultField`, the standard `Loadables` you compose from, `NamedComponentRegistry` for name-keyed registries, `GenericRegisteredSerializer` for type-dispatched JSON, and the codec bridge (`recordLoadable.mapCodec()` / `loadable.codec()` / `loadable.streamCodec()`) for feeding NeoForge `RecipeSerializer`/`IngredientType`/`ICondition`. Include a worked `java` example: define a small record + its `RecordLoadable` + use it. NeoForge notes: NBT→data-component loadables, the new codec bridge; link migration §4.

- [ ] **Step 3: Verify names exist**

Run: `grep -rn "mapCodec\|streamCodec\|public static.*codec()" src/main/java/slimeknights/mantle/data/loadable/Loadable.java src/main/java/slimeknights/mantle/data/loadable/record/RecordLoadable.java`
Expected: the bridge methods exist. Spot-check `Loadables` constant names used in the doc.

- [ ] **Step 4: Commit**

```bash
git add docs/api/data-loadables.md
git commit -m "docs: add data/loadables API guide"
```

---

## Task 3: `docs/api/predicates.md`

**Files:**
- Create: `docs/api/predicates.md`
- Read: `src/main/java/slimeknights/mantle/data/predicate/IJsonPredicate.java`, `data/predicate/block/BlockPredicate.java`, `data/predicate/entity/LivingEntityPredicate.java`, `data/predicate/damage/DamageSourcePredicate.java`, `data/predicate/fluid/FluidPredicate.java`, `data/predicate/PredicateRegistry.java`

- [ ] **Step 1: Read the real API**

Read `IJsonPredicate<T>`, the four predicate families (`BlockPredicate`, `LivingEntityPredicate`, `DamageSourcePredicate`, `FluidPredicate`) with their built-in constants (e.g. `LivingEntityPredicate.FIRE_IMMUNE`, `BlockPredicate.REQUIRES_TOOL`) and combinators (and/or/inverted), and how each family's `LOADER` (`PredicateRegistry`) registers named predicates.

- [ ] **Step 2: Write the guide**

Cover: what a JSON predicate is, using the built-in predicates, composing them (and/or/not), registering a custom predicate into the family `LOADER`, and using a predicate in your own loadable via the family's loadable. Include a `java` example: register a custom `LivingEntityPredicate` and a JSON snippet showing its serialized form. NeoForge notes: `MobTypePredicate` now uses entity-type tags (link migration §5).

- [ ] **Step 3: Verify names exist**

Run: `grep -rn "class BlockPredicate\|class LivingEntityPredicate\|interface IJsonPredicate" src/main/java/slimeknights/mantle/data/predicate`
Expected: found. Spot-check any predicate constant named in the doc.

- [ ] **Step 4: Commit**

```bash
git add docs/api/predicates.md
git commit -m "docs: add predicates API guide"
```

---

## Task 4: `docs/api/fluids.md`

**Files:**
- Create: `docs/api/fluids.md`
- Read: `src/main/java/slimeknights/mantle/registration/object/FluidObject.java`, `registration/deferred/FluidDeferredRegister.java`, `fluid/transfer/*.java`, `fluid/tooltip/FluidTooltipHandler.java`, `fluid/texture/*.java`, `fluid/FluidTransferHelper.java`

- [ ] **Step 1: Read the real API**

Read `FluidDeferredRegister`/`FluidObject` (still/flowing/block/bucket), the `FluidType` subclasses (`TextureFluidType`, `UnplaceableFluid`), the transfer system (`FluidContainerTransferManager`, `IFluidContainerTransfer`, `EmptyFluidContainerTransfer`, `FillFluidContainerTransfer`), `FluidTooltipHandler`, the texture providers, and `FluidTransferHelper`.

- [ ] **Step 2: Write the guide**

Cover: registering a fluid with `FluidDeferredRegister` → `FluidObject` (still/flowing/bucket/block), the client `FluidType` extensions for textures, the data-driven fluid-container transfer system (JSON `fluid_transfer` + the manager) and its datagen provider, and fluid tooltips. Include a `java` example registering a `FluidObject` + a `MantleFluidTransferProvider`-style datagen snippet. NeoForge notes: component-based `FluidStack` (`copyWithAmount`, `isSameFluidSameComponents`), `ForgeFlowingFluid`→`BaseFlowingFluid`; link migration §6.

- [ ] **Step 3: Verify names exist**

Run: `grep -rln "class FluidObject\|class FluidContainerTransferManager\|class FluidTooltipHandler" src/main/java/slimeknights/mantle`
Expected: found.

- [ ] **Step 4: Commit**

```bash
git add docs/api/fluids.md
git commit -m "docs: add fluids API guide"
```

---

## Task 5: `docs/api/recipes.md`

**Files:**
- Create: `docs/api/recipes.md`
- Read: `src/main/java/slimeknights/mantle/recipe/ICommonRecipe.java`, `recipe/helper/*.java` (`LoadableRecipeSerializer`, `ItemOutput`, `FluidOutput`, `FluidIngredient`, `SizedIngredient`), `recipe/ingredient/*.java`, `recipe/condition/*.java`, `recipe/data/*.java`

- [ ] **Step 1: Read the real API**

Read `ICommonRecipe`/`ICustomOutputRecipe`, `LoadableRecipeSerializer` (how a recipe's `RecordLoadable` becomes a `RecipeSerializer` via the codec bridge), `ItemOutput`/`FluidOutput`, the ingredients (`FluidIngredient`, `SizedIngredient`, `EntityIngredient`, the custom `ItemIngredient`/`PotionIngredient`/`FluidContainerIngredient` + `MantleIngredients`), the tag conditions (`TagFilledCondition`/`TagEmptyCondition` + `MantleConditions`), the recipe inputs/containers, and the datagen builders (`AbstractRecipeBuilder`, `ICommonRecipeHelper`).

- [ ] **Step 2: Write the guide**

Cover: defining a custom recipe with a `RecordLoadable` + `LoadableRecipeSerializer`, the output/ingredient helpers, fluid ingredients/outputs, custom ingredient types, the tag conditions, and the datagen recipe builders (`RecipeOutput`-based). Include a `java` example: a minimal custom recipe + its serializer via the loadable bridge. NeoForge notes: `RecipeSerializer`→`MapCodec`+`StreamCodec`, `RecipeInput`/`RecipeHolder`, `ICustomIngredient`, `neoforge:conditions`; link migration §7.

- [ ] **Step 3: Verify names exist**

Run: `grep -rln "class LoadableRecipeSerializer\|class FluidIngredient\|class TagFilledCondition\|class SizedIngredient" src/main/java/slimeknights/mantle/recipe`
Expected: found.

- [ ] **Step 4: Commit**

```bash
git add docs/api/recipes.md
git commit -m "docs: add recipes API guide"
```

---

## Task 6: `docs/api/networking.md`

**Files:**
- Create: `docs/api/networking.md`
- Read: `src/main/java/slimeknights/mantle/network/MantleNetwork.java`, `network/NetworkWrapper.java`, `network/packet/ISimplePacket.java`, `network/packet/IThreadsafePacket.java`, `network/packet/SwingArmPacket.java`

- [ ] **Step 1: Read the real API**

Read `NetworkWrapper` (its `registrar`/send helpers), `MantleNetwork.registerPayloads`, `ISimplePacket`/`IThreadsafePacket`, and a sample packet (`SwingArmPacket`) showing the `CustomPacketPayload.Type` + `StreamCodec` + `handle(IPayloadContext)` shape.

- [ ] **Step 2: Write the guide**

Cover: creating a `NetworkWrapper`, defining a packet as a `record` implementing `ISimplePacket`/`IThreadsafePacket` with a `Type` + `StreamCodec`, registering payloads on `RegisterPayloadHandlersEvent`, and sending (`sendToServer`/`sendToPlayer` via `PacketDistributor`). Include a full `java` example of a small packet + its registration + send. NeoForge notes: `SimpleChannel`→payloads, the registrar takes the protocol version string; link migration §8.

- [ ] **Step 3: Verify names exist**

Run: `grep -rn "registerPayloads\|class NetworkWrapper\|interface ISimplePacket" src/main/java/slimeknights/mantle/network`
Expected: found.

- [ ] **Step 4: Commit**

```bash
git add docs/api/networking.md
git commit -m "docs: add networking API guide"
```

---

## Task 7: `docs/api/client-models.md`

**Files:**
- Create: `docs/api/client-models.md`
- Read: `src/main/java/slimeknights/mantle/client/model/*.java` (`RetexturedModel`, `ConnectedModel`, `NBTKeyModel`, `ColoredBlockModel`, `MantleItemLayerModel`, `FallbackModelLoader`, `SimpleBlockModel`), `client/model/builder/*.java`, `client/render/MantleRenderTypes.java`, `client/render/FluidRenderer.java`, `client/render/FluidCuboid.java`

- [ ] **Step 1: Read the real API**

Read each model loader and what it does (retextured = swap textures via item data, connected = CTM, NBTKey = pick model by component, colored = per-element tint, item-layer), their datagen `builder/` classes, `MantleRenderTypes`, and the fluid render helpers (`FluidRenderer`, `FluidCuboid`).

- [ ] **Step 2: Write the guide**

Cover: each custom model loader (when to use it + the JSON `loader` id + the datagen builder), `MantleRenderTypes`/the fluid fog-fix shader, and rendering fluids in a BlockEntityRenderer with `FluidRenderer`/`FluidCuboid`. Include a `java` datagen example using one model builder + a `java` `FluidRenderer` render snippet. NeoForge notes: `IUnbakedGeometry.bake` signature, `BlockElementFace` record, client events; link migration §10.

- [ ] **Step 3: Verify names exist**

Run: `grep -rln "class RetexturedModel\|class ConnectedModel\|class FluidRenderer\|class MantleRenderTypes" src/main/java/slimeknights/mantle/client`
Expected: found.

- [ ] **Step 4: Commit**

```bash
git add docs/api/client-models.md
git commit -m "docs: add client/models API guide"
```

---

## Task 8: `docs/api/books.md`

**Files:**
- Create: `docs/api/books.md`
- Read: `src/main/java/slimeknights/mantle/client/book/BookLoader.java`, `client/book/data/BookData.java`, `client/book/data/SectionData.java`, `client/book/data/PageData.java`, `client/book/data/content/*.java` (list the content types), `client/screen/book/BookScreen.java`, `item/LecternBookItem.java`

- [ ] **Step 1: Read the real API**

Read `BookLoader` (registering a book + content types), `BookData`/`SectionData`/`PageData` (the data model), the built-in `content/` page types, `BookScreen`, and the lectern book item.

- [ ] **Step 2: Write the guide**

Cover: registering a book with `BookLoader`, the resource layout (book.json, sections, pages), the built-in page/content types and registering a custom content type, opening the book (item / lectern / command), and the screen. Include a `java` example registering a book + a JSON page example. NeoForge notes: book NBT→`CUSTOM_DATA`, screen/render signature changes; link migration §10/§11.

- [ ] **Step 3: Verify names exist**

Run: `grep -rln "class BookLoader\|class BookData\|class BookScreen\|class PageData" src/main/java/slimeknights/mantle/client`
Expected: found.

- [ ] **Step 4: Commit**

```bash
git add docs/api/books.md
git commit -m "docs: add books API guide"
```

---

## Task 9: `docs/api/utilities.md`

**Files:**
- Create: `docs/api/utilities.md`
- Read: `src/main/java/slimeknights/mantle/item/*.java` (`TooltipItem`, `ContainerFoodItem`, `LecternBookItem`), `block/InventoryBlock.java`, `block/RetexturedBlock.java`, `block/entity/InventoryBlockEntity.java`, `loot/*.java` (`MantleLoot`, `LootTableInjector`), `inventory/BaseContainerMenu.java`, `command/MantleCommand.java`, `config/Config.java`

- [ ] **Step 1: Read the real API**

Read the item helpers (tooltip/food/lectern), block helpers (`InventoryBlock` + `InventoryBlockEntity`, `RetexturedBlock`), loot helpers (`MantleLoot` conditions/functions, `LootTableInjector`, tag preference), the menu base (`BaseContainerMenu`), the `/mantle` command, and `Config`.

- [ ] **Step 2: Write the guide**

A grab-bag of smaller helpers, one short subsection each: tooltip/food items, inventory & retextured blocks (+ the capability note that downstream mods register `Capabilities.ItemHandler.BLOCK` for their concrete BE type), loot conditions/functions + `LootTableInjector`, menu/GUI helpers, the `/mantle` command, and Mantle's config options. Short `java` examples per subsection where useful. NeoForge notes: `Block.use`→`useWithoutItem`, capability registration is central, food API; link migration §11.

- [ ] **Step 3: Verify names exist**

Run: `grep -rln "class InventoryBlock\|class LootTableInjector\|class MantleCommand\|class ContainerFoodItem" src/main/java/slimeknights/mantle`
Expected: found.

- [ ] **Step 4: Commit**

```bash
git add docs/api/utilities.md
git commit -m "docs: add utilities API guide"
```

---

## Task 10: `docs/migration/1.20-to-1.21.1.md`

**Files:**
- Create: `docs/migration/1.20-to-1.21.1.md`
- Read for before→after: the port commits — `git log --oneline 34389eef..HEAD` and `git show <sha>` for the relevant `port:`/`fix(port):` commits; and the design spec §Section 4.

- [ ] **Step 1: Gather the diffs**

For each subsystem, identify the actual before→after from the port commits (registration, data, network, fluid, block, item, recipe, loot, command, client, build) — e.g. `git show a0f781ea` (registration), `bc59ac53` (network), `cf7cbe38` (fluid), the data/ and convergence commits. These give the real old→new API pairs.

- [ ] **Step 2: Write the migration guide**

Per the spec's Section B, 12 sections (Intro, Build & toolchain, Registration, Data/Loadables, Predicates, Fluids, Recipes, Networking, Capabilities, Client/Models, Items/Blocks, Entrypoint). Each section: a short intro, a **before → after** Markdown table (1.20.1/Forge symbol → 1.21.1/NeoForge symbol), a *why* line, and a *how to adapt* note. Use the real symbols from the commits. Cross-link each section to the matching `docs/api/*.md` guide. Include the headline conceptual shifts up top (data components, capabilities rework, payload networking, codec-based serialization, mojmap ATs).

- [ ] **Step 3: Verify**

Spot-check 5 before→after rows against the code: the "after" symbol must exist (`grep` in `src/main/java`), the "before" symbol must NOT (it was Forge). Confirm all 12 sections present and every section links to its API guide.

- [ ] **Step 4: Commit**

```bash
git add docs/migration/1.20-to-1.21.1.md
git commit -m "docs: add 1.20 -> 1.21.1 migration guide"
```

---

## Task 11: Rewrite `README.md`

**Files:**
- Modify: `README.md` (full rewrite)

- [ ] **Step 1: Write the new README**

Replace the contents with: the Mantle logo + title; a one-paragraph description (shared library for SlimeKnights-style mods, now on **MC 1.21.1 / NeoForge 21.1.234**, this `alejandrofelipe` fork); a **Documentation** section linking `docs/README.md`, the API guides, and the migration guide; a **Build from source** section (JDK 21 + NeoGradle, link `docs/COMMANDS.md`); a **Using Mantle as a dependency** section (Gradle snippet + note that this fork isn't on an official maven, so use `publishToMavenLocal`/JitPack/included build); an **Issue reporting** section updated for NeoForge (MC version, NeoForge version, Mantle version, `logs/latest.log`); and the existing **MIT License** block plus a fork/port credit line acknowledging SlimeKnights.

```markdown
![Mantle logo](https://raw.github.com/SlimeKnights/Mantle/master/src/main/resources/Mantle.png)
# Mantle
**Shared library for NeoForge mods — Minecraft 1.21.1 / NeoForge 21.1.234**

> This is the `alejandrofelipe` fork, ported to MC 1.21.1 / NeoForge from the SlimeKnights 1.20.1/Forge original.

## Documentation
- [Documentation index](docs/README.md)
- API guides: [registration](docs/api/registration.md) · [data & loadables](docs/api/data-loadables.md) · [predicates](docs/api/predicates.md) · [fluids](docs/api/fluids.md) · [recipes](docs/api/recipes.md) · [networking](docs/api/networking.md) · [client & models](docs/api/client-models.md) · [books](docs/api/books.md) · [utilities](docs/api/utilities.md)
- [1.20 → 1.21.1 migration guide](docs/migration/1.20-to-1.21.1.md)

## Build from source
Requires JDK 21. Build toolchain is NeoGradle. See [docs/COMMANDS.md](docs/COMMANDS.md) for the exact commands.
... (build / dependency / issue-reporting / license sections per Step 1) ...
```

(Write out the remaining sections fully in the file — the block above is the head; do not leave it truncated in the actual README.)

- [ ] **Step 2: Verify links**

Run: `grep -oE "\]\(docs/[^)]+\)" README.md` then confirm each path exists with `ls`.
Expected: every linked doc file exists.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: rewrite README for the 1.21.1/NeoForge fork"
```

---

## Task 12: `docs/README.md` (docs index)

**Files:**
- Create: `docs/README.md`

- [ ] **Step 1: Write the index**

A short landing page: a title, one-line intro, then grouped links — **API guides** (all nine `docs/api/*.md`), **Migration** (`docs/migration/1.20-to-1.21.1.md`), **Build & commands** (`docs/COMMANDS.md`), and **Port design/plan** (the superpowers spec + plan for the port). One line per link with a short description.

- [ ] **Step 2: Verify links**

Run: `grep -oE "\]\([^)]+\)" docs/README.md` and confirm each relative path resolves (`ls` from `docs/`).
Expected: all links resolve.

- [ ] **Step 3: Commit**

```bash
git add docs/README.md
git commit -m "docs: add documentation index"
```

---

## Done criteria

- All 12 files exist and are committed.
- Every class/method named in the guides exists in `src/main/java` (no invented APIs).
- The migration guide has all 12 sections with before→after tables and links to the API guides.
- README and `docs/README.md` links all resolve.

**Out of scope (per spec):** generated javadoc / hosted site, publishing to maven, in-game book content, documenting Tinkers' Construct.
