# Mantle Documentation

Documentation for **Mantle** on **Minecraft 26.1.2 / NeoForge 26.1.2.130** (the `alejandrofelipe` fork, branch `26.1.2`; the API guides were written for 1.21.1 — see the version-to-version gotchas below for what changed since). Mantle is the shared library that SlimeKnights-style mods (e.g. Tinkers' Construct) build on.

## API guides

How to use each Mantle subsystem from a consuming mod:

- [Registration](api/registration.md) — `DeferredRegister` wrappers and grouped helper objects (blocks, items, fluids, building sets).
- [Data & Loadables](api/data-loadables.md) — Mantle's JSON + network serialization framework (`Loadable`/`RecordLoadable`, the codec bridge, registries).
- [Predicates](api/predicates.md) — the `IJsonPredicate` system for blocks, entities, damage sources, and fluids.
- [Fluids](api/fluids.md) — fluid registration, fluid types/textures, container transfer, and tooltips.
- [Recipes](api/recipes.md) — recipe helpers, ingredients, conditions, and datagen builders.
- [Networking](api/networking.md) — defining and sending `CustomPacketPayload` packets the Mantle way.
- [Client & Models](api/client-models.md) — the custom model loaders (retextured, connected, NBT-key, colored) and fluid rendering.
- [Books](api/books.md) — the in-game book / documentation system.
- [Utilities](api/utilities.md) — item/block helpers, loot, GUI/menus, the `/mantle` command, and config.

## Migration

- [1.20 → 1.21.1 migration guide](migration/1.20-to-1.21.1.md) — every API change between MC 1.20.1/Forge and MC 1.21.1/NeoForge, before → after, organized by subsystem. Read this to port a Mantle-dependent mod.
- [1.21.1 → 1.21.4 gotchas](migration/1.21.1-to-1.21.4.md) — roadmap M1 (1.21.2/1.21.3 folded in): recipes as a datapack registry, item/block `setId`, the item model rewrite.
- [1.21.4 → 1.21.5 gotchas](migration/1.21.4-to-1.21.5.md) — roadmap M3: `BakedModel` removed, block-state-level dynamic models, `RenderPipeline`, NBT `Optional` getters, gametest framework.
- [1.21.5 → 1.21.8 gotchas](migration/1.21.5-to-1.21.8.md) — roadmap M4 (1.21.6/1.21.7 folded in): deferred GUI rendering and picture-in-picture, GUI item model identity, `ValueInput`/`ValueOutput`, UBO shaders and fog, Mantle API changes for downstream mods.
- [1.21.8 → 1.21.10 gotchas](migration/1.21.8-to-1.21.10.md) — roadmap M5 (1.21.9 folded in): the NeoForge transfer API rework (`ResourceHandler`, `ItemAccess`, transactions) and Mantle's full migration to it, two-phase rendering, event-based input, the removed `verifyComponentsAfterLoad`, datagen through the cache, Mantle API changes for downstream mods (incl. the breaking `refactor(transfer)!`).
- [1.21.10 → 1.21.11 gotchas](migration/1.21.10-to-1.21.11.md) — roadmap M6: the scripted `ResourceLocation` → `Identifier` rename (rename-map tooling, access transformer caveat), `package-info` → JSpecify `@NullMarked`, render types on `RenderSetup`, `BakedQuad` records, the item atlas split and per-atlas item layers, worn armor through equipment assets + Tinkers' own layer, permissions and game rules, JEI 27, Mantle API changes for downstream mods (int-level permission helpers, atlas-aware model helpers).
- [1.21.11 → 26.1.2 gotchas](migration/1.21.11-to-26.1.2.md) — roadmap M7 (26.1/26.1.1 folded in): Java 25 and no Parchment, the jar-diff + compiler-driven rename tooling, stacks that need bound data components (templates, `DatagenComponents`, unit-test binding), the `RecipeSerializer` record and loot `MapCodec` registries, lazy missing tags and GLM priorities, trades as data, per-quad render layers (`render_type` removed, `force_translucent`), `SpriteId` vs `Material`, fluid models, pipeline depth state, the GUI `extract*` rename and `extractContainerBackground`, JEI 29 + `mezz_config`, Mantle API changes for downstream mods.

## Build & commands

- [COMMANDS.md](COMMANDS.md) — canonical, retry-proof build/run commands (JDK 21 for 1.21.x, JDK 25 for 26.1.2; NeoGradle, Windows/PowerShell notes).
- [RELEASE-TAGS.md](RELEASE-TAGS.md) — release tag naming and the annotated tag message template (milestone and patch releases, Mantle and Tinkers).

## Port design & history

The notes from porting Mantle from 1.20.1/Forge to 1.21.1/NeoForge:

- [Port design spec](superpowers/specs/2026-06-27-mantle-1.21.1-neoforge-port-design.md)
- [Port implementation plan](superpowers/plans/2026-06-27-mantle-1.21.1-neoforge-port.md)
- [Documentation design spec](superpowers/specs/2026-06-27-mantle-documentation-design.md)
- [Documentation plan](superpowers/plans/2026-06-27-mantle-documentation.md)
