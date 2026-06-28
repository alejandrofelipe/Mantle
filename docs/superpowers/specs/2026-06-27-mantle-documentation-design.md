# Mantle Documentation — Design

**Date:** 2026-06-27
**Repo:** `alejandrofelipe/Mantle`, branch `1.21.1`
**Status:** Approved design — ready for implementation planning

---

## Context

Mantle has just been ported from MC 1.20.1/Forge to **MC 1.21.1 / NeoForge 21.1.234** (build green,
loads in-game). The repo's documentation is stale: the README still describes a Forge library with generic
build steps, and there is no consumer-facing API documentation or migration guidance. This project produces
three documentation deliverables so that modders consuming Mantle (e.g. Tinkers' Construct) can use its APIs
and port their own mods.

## Goal

1. **Rewrite `README.md`** for this fork, NeoForge 1.21.1, with links into the docs.
2. **Write "how to use" API guides**, one per Mantle subsystem.
3. **Write a 1.20 → 1.21.1 migration guide**, organized by subsystem with before→after mappings.

## Decisions (locked)

- **Language:** English (open-source / SlimeKnights ecosystem convention; eases upstream contribution).
- **API docs format:** practical "how to use" guides per subsystem (what it's for + how to use + code
  examples), not exhaustive per-class reference.
- **Migration doc granularity:** comprehensive by subsystem, before→after tables for each meaningful public
  API change (skip trivial 1:1 import renames). Not function-by-function.
- **Structure (Approach A):** a `docs/` folder with an index, one file per API subsystem under `docs/api/`,
  and the migration guide under `docs/migration/`. README stays slim and links out.
- **Sources:** API guides combine knowledge from the port with online research (SlimeKnights wiki /
  KnightMiner's docs) where helpful; the migration guide is written directly from the port's diffs.

## File structure

```
README.md                              (rewritten)
docs/README.md                         (new — docs index)
docs/api/registration.md               (new)
docs/api/data-loadables.md             (new)
docs/api/predicates.md                 (new)
docs/api/fluids.md                     (new)
docs/api/recipes.md                    (new)
docs/api/networking.md                 (new)
docs/api/client-models.md              (new)
docs/api/books.md                      (new)
docs/api/utilities.md                  (new)
docs/migration/1.20-to-1.21.1.md       (new)
```

Each API guide is small and self-contained (one subsystem, readable in one sitting). The migration guide is
a single file with per-subsystem sections.

## Section A — API guides (`docs/api/`)

Nine guides. Each follows the same shape: **What it's for** → **Key types** → **How to use (with code
examples)** → **Datagen** (where relevant) → **Gotchas / NeoForge notes**.

| Guide | Scope | Packages |
|-------|-------|----------|
| `registration.md` | DeferredRegister wrappers (Block/Item/Fluid/Entity/BlockEntity/Menu/Attribute/Potion/ArgumentType) and the helper objects (`ItemObject`, `BuildingBlockObject`, `WoodBlockObject`, `MetalItemObject`, `FluidObject`, `EnumObject`), `RegistrationHelper` | `registration/` |
| `data-loadables.md` | Mantle's signature serialization framework: `Loadable<T>` / `RecordLoadable<T>`, field types, the standard `Loadables` registry, `NamedComponentRegistry`, `GenericRegisteredSerializer`, and the codec bridge (`codec()` / `streamCodec()` / `mapCodec()`) | `data/loadable`, `data/registry`, `data/gson` |
| `predicates.md` | The `IJsonPredicate` system (block / living-entity / damage-source / fluid predicates) and their registries | `data/predicate` |
| `fluids.md` | `FluidObject`, `FluidType` setup, fluid container transfer, fluid tooltips, fluid textures | `fluid/` |
| `recipes.md` | Recipe helpers (`ICommonRecipe`, loadable-based serializers), ingredients (`FluidIngredient`, `SizedIngredient`, custom `ICustomIngredient`), conditions (`TagFilled`/`TagEmpty`), recipe inputs/containers, datagen builders | `recipe/` |
| `networking.md` | `MantleNetwork` / `NetworkWrapper`, defining a `CustomPacketPayload` packet with a `StreamCodec`, the `ISimplePacket`/`IThreadsafePacket` helpers, sending | `network/` |
| `client-models.md` | The model loaders (`RetexturedModel`, `ConnectedModel`, `NBTKeyModel`, `ColoredBlockModel`, `MantleItemLayerModel`, `FallbackModelLoader`), their datagen builders, `MantleRenderTypes`, fluid rendering (`FluidRenderer`, `FluidCuboid`) | `client/model`, `client/render` |
| `books.md` | The in-game book/documentation system (`BookData`, `BookLoader`, the content/element/page model, `BookScreen`, lectern books) | `client/book`, `client/screen/book`, lectern items |
| `utilities.md` | Smaller helpers: item helpers (tooltip items, food items, lectern book item), block helpers (`InventoryBlock`, `RetexturedBlock`, building-block helpers), loot (conditions/functions, `LootTableInjector`, tag preference), GUI/menu helpers, the `/mantle` command, config | `item`, `block`, `loot`, `inventory`, `command`, `config` |

## Section B — Migration guide (`docs/migration/1.20-to-1.21.1.md`)

Single file, per-subsystem sections, each with a **before → after** table plus *why* and *how to adapt*.
Sections:

1. **Intro** — scope (a 4-version Minecraft jump *and* a Forge → NeoForge loader change), how to read the
   tables.
2. **Build & toolchain** — ForgeGradle → NeoGradle 7.1.38 / Gradle 9.2.1, `gradle.properties` coordinates,
   `mods.toml` → `neoforge.mods.toml`, access transformers SRG → mojmap + `minecraft.accessTransformers`.
3. **Registration** — `RegistryObject` → `DeferredHolder`, `ForgeRegistries` → `BuiltInRegistries` /
   `NeoForgeRegistries`, `ObjectHolder` removed, `ForgeSpawnEggItem` → `DeferredSpawnEggItem`,
   `IForgeMenuType` → `IMenuTypeExtension`.
4. **Data / Loadables** — the added `codec()`/`streamCodec()`/`mapCodec()` bridge, `FluidStackLoadable` /
   `ItemStackLoadable` → data components, condition loadables → NeoForge `ICondition`, `ItemDisplayContext`
   now a `StringRepresentable` enum (not a registry).
5. **Predicates** — `MobType` removed → entity-type tags; `Enchantment`/`MobEffect` → `Holder<...>`;
   `wasEyeInWater` → `isEyeInFluid`.
6. **Fluids** — `FluidStack` → component-based (`copyWithAmount`, `isSameFluidSameComponents`, no NBT),
   `ForgeFlowingFluid` → `BaseFlowingFluid`, fluid-handler capability access.
7. **Recipes** — `RecipeSerializer` → `MapCodec` + `StreamCodec`, `Container` → `RecipeInput`,
   `Recipe.getId()` removed → `RecipeHolder`, custom ingredients → `ICustomIngredient` + `IngredientType`,
   Forge conditions → `neoforge:conditions`, datagen `Consumer<FinishedRecipe>` → `RecipeOutput`.
8. **Networking** — `SimpleChannel` / `registerMessage` → `CustomPacketPayload` + `StreamCodec` +
   `RegisterPayloadHandlersEvent` (the registrar takes the protocol version, not the namespace), static
   `PacketDistributor`.
9. **Capabilities** — `Capability<T>` / `LazyOptional` / `ICapabilityProvider` → `BlockCapability` /
   `ItemCapability` registered in `RegisterCapabilitiesEvent`, nullable returns.
10. **Client / Models** — `IUnbakedGeometry.bake` (dropped trailing `ResourceLocation`), `BlockElementFace`
    is a record, `FaceBakery` internals, client events (`ModelEvent`, `RegisterColorHandlersEvent`,
    `RegisterMenuScreensEvent`), screen signature changes (`renderBackground`, `mouseScrolled`), the
    `fog_distance` shader signature.
11. **Items / Blocks** — `Block.use` → `useWithoutItem`, item/block NBT → data components,
    `appendHoverText` → `Item.TooltipContext`, the food API (`getEffects()` → `effects()`,
    `DataComponents.FOOD`), `ToolActions` → `ItemAbilities`, BE NBT overrides take `HolderLookup.Provider`.
12. **Entrypoint** — `@Mod` constructor injection `(IEventBus, ModContainer)`, config registered on the
    `ModContainer`, central wiring (payloads, capabilities, condition codecs, ingredient types, data
    components, datagen) replacing the Forge `RegisterEvent` / `CraftingHelper.register` block. Note the
    synchronous in-constructor registration of in-memory loaders (so datagen sees them).

## Section C — README + docs index

**`README.md`** (rewritten, slim):
- Mantle logo + title; one-paragraph description: shared library for SlimeKnights-style mods, now on
  **MC 1.21.1 / NeoForge 21.1.234** (this `alejandrofelipe` fork).
- **Documentation** — links to `docs/README.md`, the API guides, and the migration guide.
- **Build from source** — JDK 21 + NeoGradle; link to `docs/COMMANDS.md` for the canonical commands.
- **Using Mantle as a dependency** — a Gradle snippet; note that this fork is not on an official maven, so
  consumers use a local `publishToMavenLocal` / JitPack / included build.
- **Issue reporting** — updated for NeoForge (NeoForge version, the `logs/latest.log`, MC + Mantle versions).
- **License** — keep the existing MIT block, add the fork/port credit line acknowledging SlimeKnights.

**`docs/README.md`** (docs index): short landing page linking every API guide, the migration guide,
`COMMANDS.md`, and the port spec/plan.

## Out of scope

- Generated javadoc / a hosted docs site (the guides are hand-written markdown).
- Publishing Mantle to a public maven repository.
- In-game book content (this documents the book *system*, not Mantle's own book pages).
- Documenting Tinkers' Construct (separate project).
- Re-deriving the port itself — the code is already ported; this is documentation of it.
