# Recipes

Mantle's recipe toolkit: loadable-backed recipe serializers, reusable item/fluid outputs, displayable fluid/entity/sized ingredients, custom NeoForge ingredient types, and tag-driven datapack conditions.

## What it's for

Vanilla recipe serializers in 1.21 require you to hand-write a `MapCodec` and a `StreamCodec` for every recipe type, and vanilla `Ingredient` cannot match by stack size, fluid, or potion. Mantle layers a *loadable* abstraction on top so a single `RecordLoadable` describes your recipe (JSON parsing, JSON writing, and network sync all at once), and `LoadableRecipeSerializer` bridges it straight into a `RecipeSerializer`. On top of that it ships ready-made amount-aware and fluid ingredients, tag-preference-backed outputs, custom `ICustomIngredient` types, and `neoforge:conditions` that turn recipes on/off based on whether a tag is populated.

## Key types

| Type | Purpose |
| --- | --- |
| `slimeknights.mantle.recipe.ICommonRecipe` | `Recipe<C>` extension defaulting `assemble`, `canCraftInDimensions`, and `isSpecial`. |
| `slimeknights.mantle.recipe.helper.LoadableRecipeSerializer` | Wraps a `RecordLoadable<T>` into a `RecipeSerializer<T>` (the codec bridge). |
| `slimeknights.mantle.recipe.helper.LoadableIngredientSerializer` | Builds a NeoForge `IngredientType<T>` from a `RecordLoadable<T>`. |
| `slimeknights.mantle.recipe.helper.ItemOutput` | Item-stack result supporting direct stacks and tag-preference output. |
| `slimeknights.mantle.recipe.helper.FluidOutput` | Fluid-stack result supporting direct stacks and tag-preference output. |
| `slimeknights.mantle.recipe.ingredient.FluidIngredient` | Displayable fluid ingredient matching a fluid, tag, or list, with an amount. |
| `slimeknights.mantle.recipe.ingredient.SizedIngredient` | Wraps a vanilla `Ingredient` with a required count. |
| `slimeknights.mantle.recipe.ingredient.EntityIngredient` | Matches an `EntityType`, a set, or an entity-type tag. |
| `slimeknights.mantle.recipe.ingredient.ItemIngredient` | Abstract `ICustomIngredient` matching a list of items and/or a tag. |
| `slimeknights.mantle.recipe.ingredient.PotionIngredient` | `ItemIngredient` further matching a `Potion` data component. |
| `slimeknights.mantle.recipe.ingredient.FluidContainerIngredient` | `ICustomIngredient` matching an item that holds a given fluid. |
| `slimeknights.mantle.recipe.ingredient.MantleIngredients` | `DeferredRegister` of Mantle's custom `IngredientType`s. |
| `slimeknights.mantle.recipe.condition.TagFilledCondition` | `ICondition` (and loot condition) true when a tag has entries. |
| `slimeknights.mantle.recipe.condition.TagEmptyCondition` | Inverse of `TagFilledCondition`. |
| `slimeknights.mantle.recipe.condition.MantleConditions` | `DeferredRegister` of Mantle's condition codecs. |
| `slimeknights.mantle.recipe.container.IRecipeContainer` | `RecipeInput` extension for read-only recipe inputs. |
| `slimeknights.mantle.recipe.data.AbstractRecipeBuilder` | Base datagen builder writing to a `RecipeOutput`. |
| `slimeknights.mantle.recipe.data.IRecipeHelper` / `ICommonRecipeHelper` | Datagen helpers for ids, conditions, and common crafting recipes. |

## How to use

### Defining a custom recipe with a loadable

Describe the recipe once as a `RecordLoadable`, then hand it to `LoadableRecipeSerializer.of`. The loadable supplies both `mapCodec()` and `streamCodec()`, so you never write codecs by hand. This mirrors how Mantle's own `SmeltingResultRecipe` is built.

```java
public class GrindingRecipe implements ICommonRecipe<SingleRecipeInput> {
  /** One loadable drives JSON read/write and network sync. */
  public static final RecordLoadable<GrindingRecipe> LOADABLE = RecordLoadable.create(
    LoadableRecipeSerializer.RECIPE_GROUP,                                 // optional "group"
    SizedIngredient.LOADABLE.requiredField("input", r -> r.input),         // amount-aware input
    ItemOutput.Loadable.REQUIRED_STACK.requiredField("result", r -> r.result),
    IntLoadable.FROM_ONE.defaultField("time", 100, true, r -> r.time),
    GrindingRecipe::new);

  private final String group;
  private final SizedIngredient input;
  private final ItemOutput result;
  private final int time;

  public GrindingRecipe(String group, SizedIngredient input, ItemOutput result, int time) {
    this.group = group;
    this.input = input;
    this.result = result;
    this.time = time;
  }

  @Override
  public String getGroup() {
    return group;
  }

  @Override
  public boolean matches(SingleRecipeInput input, Level level) {
    return this.input.test(input.item());
  }

  @Override
  public ItemStack getResultItem(HolderLookup.Provider registries) {
    return result.get();
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return MyMod.GRINDING_SERIALIZER.get();
  }

  @Override
  public RecipeType<?> getType() {
    return MyMod.GRINDING_TYPE.get();
  }
}
```

Register the serializer with a `DeferredRegister<RecipeSerializer<?>>` on `Registries.RECIPE_SERIALIZER`, exactly as `MantleRecipes` does:

```java
private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
  DeferredRegister.create(Registries.RECIPE_SERIALIZER, MyMod.MOD_ID);

public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GrindingRecipe>> GRINDING_SERIALIZER =
  SERIALIZERS.register("grinding", () -> LoadableRecipeSerializer.of(GrindingRecipe.LOADABLE));
```

If your recipe needs its own `RecipeType` injected, use the type-aware overload
`LoadableRecipeSerializer.of(RecordLoadable, Supplier<RecipeType>)`, which returns a `TypeAwareRecipeSerializer`. The recipe id is **not** part of the loadable in 1.21 — it is supplied externally through the `RecipeHolder` (see the NeoForge notes).

### Outputs: `ItemOutput` and `FluidOutput`

Both abstract over "a stack, or a tag preference resolved at runtime". Construct them directly in code:

```java
ItemOutput stack = ItemOutput.fromStack(new ItemStack(Items.IRON_INGOT, 3));
ItemOutput item  = ItemOutput.fromItem(Items.GOLD_NUGGET, 4);
ItemOutput tagged = ItemOutput.fromTag(Tags.Items.INGOTS_COPPER, 1); // resolves via TagPreference

FluidOutput water = FluidOutput.fromFluid(Fluids.WATER, FluidType.BUCKET_VOLUME);
FluidOutput fluidTag = FluidOutput.fromTag(myFluidTag, 1000);
```

In a loadable, pick the matching enum constant as a field source. `ItemOutput.Loadable` offers
`OPTIONAL_ITEM`, `OPTIONAL_STACK`, `REQUIRED_ITEM`, and `REQUIRED_STACK` (the `*_STACK` variants read a `count`);
`FluidOutput.Loadable` offers `OPTIONAL` and `REQUIRED`. Resolve the result with `get()` (or `copy()`).

### Ingredients

**`SizedIngredient`** wraps a vanilla `Ingredient` plus a required count. Build it from items, a tag, or an existing ingredient, and serialize it with its `LOADABLE` field (`amount_needed`, defaulting to 1):

```java
SizedIngredient threeIron = SizedIngredient.fromItems(3, Items.IRON_INGOT);
SizedIngredient planks     = SizedIngredient.fromTag(ItemTags.PLANKS, 4);
boolean ok = threeIron.test(stack); // true only if count >= 3 and item matches
```

**`FluidIngredient`** matches a fluid, a fluid tag, or a compound list, each carrying a minimum amount. Use the `LOADABLE` field for serialization:

```java
FluidIngredient lava   = FluidIngredient.of(Fluids.LAVA, FluidType.BUCKET_VOLUME);
FluidIngredient molten = FluidIngredient.of(myMoltenTag, 90);
boolean matches = lava.test(fluidStack); // true if same fluid and amount >= required
```

**`EntityIngredient`** matches an `EntityType`, a set of types, or an entity-type tag (`EntityIngredient.of(...)`), exposing `getTypes()` and JEI display helpers.

**Custom `ICustomIngredient` types** subclass `ItemIngredient` (list of items and/or a tag). Mantle ships
`PotionIngredient` (additionally matching the `DataComponents.POTION_CONTENTS` potion) and
`FluidContainerIngredient` (matching any item whose fluid-handler capability contains the required fluid). Each exposes a `LoadableIngredientSerializer` `SERIALIZER` whose `ingredientType()` produces the `IngredientType`, registered in `MantleIngredients`:

```java
FluidContainerIngredient lavaBucket = FluidContainerIngredient.fromFluid(myFluidObject);
Ingredient asVanilla = lavaBucket.toVanilla(); // ICustomIngredient -> Ingredient for recipe slots
```

To add your own custom ingredient type, follow the same shape: extend `ItemIngredient` (or implement `ICustomIngredient` directly), expose a `LoadableIngredientSerializer<>(yourRecordLoadable)`, and register `serializer.ingredientType()` in a `DeferredRegister<IngredientType<?>>` on `NeoForgeRegistries.Keys.INGREDIENT_TYPES`.

### Recipe inputs / containers

Implement `slimeknights.mantle.recipe.container.IRecipeContainer` (a thin `RecipeInput` extension) when you need a read-only input for a non-crafting machine. `ISingleStackContainer` is a ready single-slot variant: implement `getStack()` and the rest is defaulted. Pass your container type as the `<C>` parameter of `ICommonRecipe<C>` / `Recipe<C>`.

### Conditions

`TagFilledCondition` and `TagEmptyCondition` are `ICondition`s (and also loot conditions) keyed on a `TagKey`. Wrap a recipe so it only loads when a tag is populated — the common case for "only register this recipe if some other mod contributed an ingredient":

```java
ICondition copperLoaded = new TagFilledCondition<>(Tags.Items.INGOTS_COPPER);
```

`TagCombinationCondition` (registered as `tag_combination_filled`) checks that a set of tags shares an entry. All three condition codecs are registered in `MantleConditions` against `NeoForgeRegistries.Keys.CONDITION_CODECS`.

## Datagen

Recipe builders write to a `net.minecraft.data.recipes.RecipeOutput`. Extend `AbstractRecipeBuilder<T>` for a custom recipe builder — it supplies `group(...)`, `unlockedBy(name, criterion)`, and `buildAdvancement(id, folder)`, and requires you to implement `save(RecipeOutput)` and `save(RecipeOutput, ResourceLocation)`:

```java
public class GrindingRecipeBuilder extends AbstractRecipeBuilder<GrindingRecipeBuilder> {
  // ... fields + fluent setters ...

  @Override
  public void save(RecipeOutput output, ResourceLocation id) {
    AdvancementHolder advancement = buildAdvancement(id, "grinding");
    output.accept(id, new GrindingRecipe(group, input, result, time), advancement);
  }
}
```

Implement `IRecipeHelper` (or `ICommonRecipeHelper`) in your `RecipeProvider` for id helpers (`id(item)`, `location(name)`, `wrap(...)`) and condition helpers. Attach conditions in datagen via `RecipeOutput.withConditions(...)`, exposed as `IRecipeHelper.withCondition(output, conditions...)`:

```java
RecipeOutput gated = withCondition(output, new TagFilledCondition<>(Tags.Items.INGOTS_COPPER));
new GrindingRecipeBuilder(...).save(gated, id("grind_copper"));
```

`ICommonRecipeHelper` additionally provides ready-made `packingRecipe`, `metalCrafting`, `slabStairsCrafting`,
`stairSlabWallCrafting`, and `woodCrafting` helpers that emit vanilla `ShapedRecipeBuilder`/`ShapelessRecipeBuilder` recipes for `MetalItemObject`, `BuildingBlockObject`, and `WoodBlockObject`. The datagen-only `FluidNameIngredient` and `ItemNameIngredient` let you reference another mod's fluid/item by id without it being loaded.

## NeoForge 1.21.1 notes

- `RecipeSerializer` no longer has read/write JSON+buffer methods; it exposes a `MapCodec<T> codec()` and a `StreamCodec<RegistryFriendlyByteBuf, T> streamCodec()`. `LoadableRecipeSerializer` implements both straight from the loadable's `mapCodec()`/`streamCodec()`.
- Recipes no longer carry their own id. The id lives on `RecipeHolder` and is supplied to `RecipeOutput.accept(id, recipe, advancement)` in datagen, so `LoadableRecipeSerializer.RECIPE_GROUP` is the only identity-ish field threaded through the loadable.
- Recipe inputs moved from `Container` to `RecipeInput` (`SingleRecipeInput`, `CraftingInput`, etc.); Mantle's `IRecipeContainer`/`ISingleStackContainer` now extend `RecipeInput`.
- Custom ingredients use NeoForge's `ICustomIngredient` + `IngredientType` registered on `NeoForgeRegistries.Keys.INGREDIENT_TYPES`; call `someIngredient.toVanilla()` to drop a custom ingredient into a vanilla recipe slot.
- Forge's `CraftingHelper.register(IConditionSerializer)` is gone. `neoforge:conditions` are datapack-registered `MapCodec<? extends ICondition>`s under `NeoForgeRegistries.Keys.CONDITION_CODECS`; each condition exposes a `CODEC` field, registered in `MantleConditions`.
- Potions/fluids are matched via data components (`DataComponents.POTION_CONTENTS`) and `FluidStack`, not NBT; `Potions.EMPTY` was removed, so `PotionIngredient` defaults to `Potions.WATER`.

See the [migration guide](../migration/1.20-to-1.21.1.md#7-recipes) for details.
