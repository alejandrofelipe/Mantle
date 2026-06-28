# Client Models & Fluid Rendering

Mantle's custom model loaders (retexture, connected textures, NBT-keyed, per-element color, item-layer, fallback) plus helpers for rendering fluids inside a `BlockEntityRenderer`.

## What it's for

Vanilla block/item models are static JSON: one set of textures, one shape. Consuming mods often need models that change at runtime — a chisel that shows the block it copied, a wall that connects to its neighbours, an item that swaps its texture based on a data component, or a tank that draws the fluid it holds. Mantle ships a family of `IGeometryLoader` implementations that cover these cases entirely from JSON (no per-block baked-model code), a set of `ModelBuilder` extensions so datagen can emit that JSON, and a `FluidRenderer`/`FluidCuboid` pair for drawing fluid volumes in block-entity renderers.

All loaders are registered by Mantle under the `mantle` namespace during `ModelEvent.RegisterGeometryLoaders`, so you reference them from a model's `"loader"` field; you do not need to register anything yourself.

## Key types

| Class | Purpose |
| --- | --- |
| `slimeknights.mantle.client.model.RetexturedModel` | Swaps textures at runtime from a block stored in model data / item data (`mantle:retextured`) |
| `slimeknights.mantle.client.model.connected.ConnectedModel` | Connected-texture (CTM) model picking texture suffixes from neighbour connections (`mantle:connected`) |
| `slimeknights.mantle.client.model.connected.ConnectedModelRegistry` | Registers named connection predicates used by `ConnectedModel` |
| `slimeknights.mantle.client.model.NBTKeyModel` | Picks a texture variant by reading a key from an item's `CUSTOM_DATA` (`mantle:nbt_key`) |
| `slimeknights.mantle.client.model.util.ColoredBlockModel` | Block model with per-element color, luminosity, and UV-lock (`mantle:colored_block`) |
| `slimeknights.mantle.client.model.util.MantleItemLayerModel` | Item-layer model baking a hardcoded color/luminosity per layer (`mantle:item_layer`) |
| `slimeknights.mantle.client.model.FallbackModelLoader` | Loads the first model whose required mod is present, ideal for optional CTM (`mantle:fallback`) |
| `slimeknights.mantle.client.model.util.SimpleBlockModel` | Lightweight `IUnbakedGeometry` reused as the base of the loaders above (`mantle:`-internal) |
| `slimeknights.mantle.client.render.MantleRenderTypes` | Mantle's `RenderType`s, including the fluid render type with the fog-fix shader |
| `slimeknights.mantle.client.render.MantleShaders` | Registers and selects Mantle's `fluid` and `block_fullbright` shaders |
| `slimeknights.mantle.client.render.FluidRenderer` | Static helpers to render fluid cuboids/cameras into a `VertexConsumer` |
| `slimeknights.mantle.client.render.FluidCuboid` | Loadable data class describing a fluid box (`from`/`to`/per-face flow + rotation) |

Datagen builders live in `slimeknights.mantle.client.model.builder`: `RetexturedModelBuilder`, `ConnectedModelBuilder`, `NBTKeyModelBuilder`, `ColoredModelBuilder`, `MantleItemLayerBuilder`, `FallbackModelBuilder`.

## How to use

### Retextured model — `mantle:retextured`

Extends `ColoredBlockModel`. List the texture names that should be swapped under `"retextured"`. At runtime, the model reads a `Block` from the model's `ModelData` (block) or item data (item) via `RetexturedHelper` and rebakes with that block's particle texture in place of the listed names. Parent textures that point at a retextured name are pulled in automatically (`RetexturedModel.getAllRetextured`).

```json
{
  "loader": "mantle:retextured",
  "parent": "minecraft:block/cube_all",
  "textures": { "all": "mantle:block/placeholder" },
  "retextured": ["all"]
}
```

`RetexturedModel.Baked` overrides `getQuads`, `getParticleIcon`, and `getOverrides`; the item override resolves the texture from the stack and falls back to the base model when no block is stored (`Blocks.AIR`).

### Connected model — `mantle:connected`

Also extends the colored block model. Each entry in `connection.textures` maps a texture name to a connection *type* registered in `ConnectedModelRegistry`. Mantle resolves neighbour connections into a 6-bit key and bakes per-key variants, appending the suffix as a subfolder of the base texture path (e.g. `block/wall` -> `block/wall/n`). Register custom predicates if equality-by-block is not enough:

```java
// e.g. during client setup
ConnectedModelRegistry.registerPredicate("my_panes", (a, b) -> a.getBlock() == b.getBlock());
```

```json
{
  "loader": "mantle:connected",
  "parent": "mantle:block/connected_base",
  "textures": { "texture": "mymod:block/wall" },
  "connection": {
    "textures": { "texture": "full" },
    "sides": ["north", "south", "east", "west"],
    "predicate": "block"
  }
}
```

### NBT-keyed model — `mantle:nbt_key`

Selects a baked variant by reading a string key out of the stack's `DataComponents.CUSTOM_DATA`. Define a `default` texture plus one texture per possible value; the value of `nbt_key` chooses the variant. Addon mods can inject extra variants via `NBTKeyModel.registerExtraTexture(key, textureName, texture)` when an `extra_textures_key` is set.

```json
{
  "loader": "mantle:nbt_key",
  "nbt_key": "variant",
  "textures": {
    "default": "mymod:item/tool",
    "broken":  "mymod:item/tool_broken"
  }
}
```

### Colored block model — `mantle:colored_block`

A `SimpleBlockModel` with a parallel `colors` list (one `ColorData` per element). Each entry sets an ARGB `color` (applied as a quad-level tint, no `BlockColors` needed), an `emissivity`/`luminosity` 0–15 for fullbright, and an optional `uvlock`. Useful for tinting individual elements statically rather than at render time.

### Item-layer model — `mantle:item_layer`

A faster clone of NeoForge's `ItemLayerModel` that bakes a hardcoded color and luminosity into each `layerN`, so you skip per-frame item-color lookups. Each layer accepts `color`, `luminosity`, `no_tint`, and an optional `render_type`. The static helpers `MantleItemLayerModel.getQuadsForSprite(...)` and `getQuadForGui(...)` are public if you need to generate item-layer quads in your own baked model.

### Fallback model — `mantle:fallback`

Takes a `models` array of at least two child models. It loads the first child whose required mod is present — determined by an explicit `fallback_mod_id`, otherwise by the namespace of that child's `loader`. The selected child is baked as a normal vanilla `BlockModel`. This is the clean way to ship a connected-texture model that degrades gracefully when an optional CTM mod is absent.

## Datagen

Every loader has a `CustomLoaderBuilder` you attach to a NeoForge `ModelBuilder` (a `BlockModelBuilder` or `ItemModelBuilder` from your `BlockStateProvider` / `ItemModelProvider`). All builders share `toJson` so they compose with the standard texture/parent setters.

```java
// inside a BlockStateProvider — retextured + colored example
public void retexturedBlock(Block block) {
  BlockModelBuilder model = models().getBuilder("my_retextured");
  model.parent(models().getExistingFile(mcLoc("block/cube_all")))
       .texture("all", "mantle:block/placeholder")
       .customLoader(RetexturedModelBuilder::new)
         .retexture("all")               // swap the "all" texture at runtime
         .color(0xFFAAAAAA)              // per-element tint (from ColoredModelBuilder)
         .luminosity(7)                  // fullbright 0-15
       .end();
  simpleBlock(block, model);
}

// item-layer with a static tint
ItemModelBuilder item = itemModels().getBuilder("my_tool");
item.texture("layer0", "mymod:item/tool")
    .customLoader(MantleItemLayerBuilder::new)
      .color(0xFF4488CC)
    .end();

// fallback: connected model when a CTM mod is loaded, plain cube otherwise
BlockModelBuilder fallback = models().getBuilder("my_wall");
fallback.customLoader((parent, helper) -> new FallbackModelBuilder<>(Mantle.getResource("fallback"), parent, helper))
        .fallback(connectedVariant, "ctm")   // requires mod "ctm"
        .fallback(plainVariant)              // always-valid default
        .end();
```

`ConnectedModelBuilder` adds `.connected(name, type)`, `.setSides(...)`, and `.setPredicate(name)`; `NBTKeyModelBuilder` exposes fluent `.key(...)` and `.extraTexturesKey(...)`. The connection `type` and `predicate` strings must match names registered in `ConnectedModelRegistry`.

## Rendering fluids in a BlockEntityRenderer

`FluidCuboid` describes a box in 0–16 model space with per-face flow and rotation; `FluidRenderer` turns it plus a `FluidStack` into quads on the `MantleRenderTypes.FLUID` render type. Use `renderScaledCuboid` to fill a cuboid proportionally to how full a tank is — it pulls the still/flowing sprites, tint, light level, and gas flag straight off the fluid's `IClientFluidTypeExtensions`/`FluidType`.

```java
public class TankRenderer implements BlockEntityRenderer<TankBlockEntity> {
  // a 14^3 box inset 1px on each side, in model (0-16) space
  private static final FluidCuboid CUBOID = FluidCuboid.builder()
    .from(1, 1, 1).to(15, 15, 15) // builder setters per FluidCuboid.Builder
    .build();

  @Override
  public void render(TankBlockEntity tank, float partialTicks, PoseStack matrices,
                     MultiBufferSource buffer, int light, int overlay) {
    FluidStack fluid = tank.getFluid();
    int capacity = tank.getCapacity();
    // fills the cuboid from the bottom up based on fluid amount / capacity
    FluidRenderer.renderScaledCuboid(matrices, buffer, CUBOID, fluid, 0f, capacity, light, false);
  }
}
```

For a fixed (always-full) volume use `FluidRenderer.renderCuboids(matrices, buffer.getBuffer(MantleRenderTypes.FLUID), List.of(CUBOID), fluid, light)`, or `renderCuboid(...)` for a single box with an explicit `from`/`to`. `FluidCuboid` also has a data-driven `REGISTRY` (`mantle/model/block_fluids`) if you want to define the boxes per blockstate in JSON instead of in code.

The fluid render type uses `MantleRenderTypes.FLUID_SHADER`, which `MantleShaders.getConfiguredFluidShader()` swaps between Mantle's custom `fluid` shader (correct fog) and vanilla's `position_color_tex_lightmap` shader based on the `ENABLE_FLUID_FOG_FIX` client config — so fog renders correctly on the fluid while staying compatible with shader packs when the user opts out. The shaders are registered in `MantleShaders.registerShaders` on `RegisterShadersEvent`.

## NeoForge 1.21.1 notes

- `IUnbakedGeometry#bake` now takes `(IGeometryBakingContext owner, ModelBaker baker, Function<Material,TextureAtlasSprite> spriteGetter, ModelState transform, ItemOverrides overrides)` — there is no longer a `ResourceLocation` parameter; Mantle uses an internal `SimpleBlockModel.BAKE_LOCATION` constant where it needs one.
- `BlockElementFace` is now a record: read texture/cull via `face.texture()` and `face.cullForDirection()` (not the old public fields). `BlockModel.FACE_BAKERY` is gone, so `ColoredBlockModel` keeps its own `FaceBakery` instance.
- Quad baking is explicit: `QuadBakingVertexConsumer` no longer auto-emits, so `MantleItemLayerModel` calls `bakeQuad()` after writing the four vertices. Vertex writes use the new builder API (`addVertex().setColor().setUv().setUv2().setNormal()`).
- Model loaders register on `ModelEvent.RegisterGeometryLoaders` and shaders on `RegisterShadersEvent`, both fired on the mod event bus (`Dist.CLIENT`) — see `ClientEvents.registerModelLoaders` and `MantleShaders.registerShaders`.
- Item textures resolve through `DataComponents.CUSTOM_DATA` / `CustomData` (`NBTKeyModel`) rather than raw stack NBT.

See the [migration guide](../migration/1.20-to-1.21.1.md#10-client--models) for details.
