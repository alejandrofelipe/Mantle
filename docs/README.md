# Mantle Documentation

Documentation for **Mantle** on **Minecraft 1.21.1 / NeoForge 21.1.234** (the `alejandrofelipe` fork). Mantle is the shared library that SlimeKnights-style mods (e.g. Tinkers' Construct) build on.

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

## Build & commands

- [COMMANDS.md](COMMANDS.md) — canonical, retry-proof build/run commands (JDK 21, NeoGradle, Windows/PowerShell notes).

## Port design & history

The notes from porting Mantle from 1.20.1/Forge to 1.21.1/NeoForge:

- [Port design spec](superpowers/specs/2026-06-27-mantle-1.21.1-neoforge-port-design.md)
- [Port implementation plan](superpowers/plans/2026-06-27-mantle-1.21.1-neoforge-port.md)
- [Documentation design spec](superpowers/specs/2026-06-27-mantle-documentation-design.md)
- [Documentation plan](superpowers/plans/2026-06-27-mantle-documentation.md)
