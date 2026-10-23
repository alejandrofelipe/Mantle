package slimeknights.mantle.fluid.transfer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jetbrains.annotations.Nullable;
import slimeknights.mantle.data.GenericDataProvider;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.mantle.registration.object.FluidObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Data gen for fluid transfer logic */
@SuppressWarnings("unused")
public abstract class AbstractFluidContainerTransferProvider extends GenericDataProvider {
  private final Map<ResourceLocation,TransferJson> allTransfers = new HashMap<>();
  private final String modId;
  /** Datagen item lookup for building tag-backed ingredients (see {@link #run}). */
  private final CompletableFuture<HolderLookup.Provider> lookupProvider;
  /**
   * Lazy item lookup, available once {@link #run} has resolved the datagen provider. Subclasses build tag-backed
   * ingredients with {@code Ingredient.of(itemLookup.getOrThrow(tag))}: in 1.21.4 the static registry's
   * {@code getOrThrow(TagKey)} is eager and throws for an optional tag no provider populated, while the datagen
   * lookup returns a lazy {@link net.minecraft.core.HolderSet.Named} that tolerates an unbound tag.
   */
  @Nullable
  protected HolderGetter<Item> itemLookup;

  public AbstractFluidContainerTransferProvider(PackOutput packOutput, String modId, CompletableFuture<HolderLookup.Provider> lookupProvider) {
    super(packOutput, Target.DATA_PACK, FluidContainerTransferManager.FOLDER, FluidContainerTransferManager.GSON);
    this.modId = modId;
    this.lookupProvider = lookupProvider;
  }

  /** Function to add all relevant transfers */
  protected abstract void addTransfers();

  /** Adds a transfer to be saved */
  protected void addTransfer(ResourceLocation id, IFluidContainerTransfer transfer, ICondition... conditions) {
    TransferJson previous = allTransfers.putIfAbsent(id, new TransferJson(transfer, conditions));
    if (previous != null) {
      throw new IllegalArgumentException("Duplicate fluid container transfer " + id);
    }
  }

  /** Adds a transfer to be saved */
  protected void addTransfer(String name, IFluidContainerTransfer transfer, ICondition... conditions) {
    addTransfer(ResourceLocation.fromNamespaceAndPath(modId, name), transfer, conditions);
  }

  /** Adds generic fill and empty for a container */
  protected void addFillEmpty(String prefix, ItemLike item, ItemLike container, FluidOutput fill, FluidIngredient drain, boolean nbt, ICondition... conditions) {
    if (nbt) {
      // EmptyFluidWithNBTTransfer was a no-op stub (== EmptyFluidContainerTransfer) in 1.21 and was removed; only the
      // fill side carries data (a component, e.g. potion contents). The empty side is the plain container transfer.
      addTransfer(prefix + "empty", new EmptyFluidContainerTransfer(Ingredient.of(item), ItemOutput.fromItem(container), fill), conditions);
      addTransfer(prefix + "fill", new FillFluidWithNBTTransfer(Ingredient.of(container), ItemOutput.fromItem(item), drain), conditions);
    } else {
      addTransfer(prefix + "empty", new EmptyFluidContainerTransfer(Ingredient.of(item), ItemOutput.fromItem(container), fill), conditions);
      addTransfer(prefix + "fill", new FillFluidContainerTransfer(Ingredient.of(container), ItemOutput.fromItem(item), drain), conditions);
    }
  }

  /** Adds generic fill and empty for a container */
  protected void addFillEmpty(String prefix, ItemLike item, ItemLike container, Fluid fluid, TagKey<Fluid> tag, int amount, boolean nbt, ICondition... conditions) {
    addFillEmpty(prefix, item, container, FluidOutput.fromFluid(fluid, amount), FluidIngredient.of(tag, amount), nbt, conditions);
  }

  /** Adds generic fill and empty for a container */
  protected void addFillEmpty(String prefix, ItemLike item, ItemLike container, TagKey<Fluid> tag, int amount, boolean nbt, ICondition... conditions) {
    addFillEmpty(prefix, item, container, FluidOutput.fromTag(tag, amount), FluidIngredient.of(tag, amount), nbt, conditions);
  }

  /** Adds generic fill and empty for a container */
  protected void addFillEmpty(String prefix, ItemLike item, ItemLike container, FluidObject<?> fluid, int amount, boolean nbt, ICondition... conditions) {
    addFillEmpty(prefix, item, container, fluid.result(amount), fluid.ingredient(amount), nbt, conditions);
  }

  /** @deprecated use {@link #addFillEmpty(String, ItemLike, ItemLike, Fluid, TagKey, int, boolean, ICondition...)} */
  @Deprecated(forRemoval = true)
  protected void addFillEmpty(String prefix, ItemLike item, ItemLike container, Fluid fluid, TagKey<Fluid> tag, int amount, ICondition... conditions) {
    addFillEmpty(prefix, item, container, fluid, tag, amount, false, conditions);
  }

  /** @deprecated use {@link #addFillEmpty(String, ItemLike, ItemLike, Fluid, TagKey, int, boolean, ICondition...)} */
  @Deprecated(forRemoval = true)
  protected void addFillEmptyNBT(String prefix, ItemLike item, ItemLike container, Fluid fluid, TagKey<Fluid> tag, int amount, ICondition... conditions) {
    addFillEmpty(prefix, item, container, fluid, tag, amount, true, conditions);
  }

  @Override
  public CompletableFuture<?> run(CachedOutput cache) {
    return lookupProvider.thenCompose(provider -> {
      this.itemLookup = provider.lookupOrThrow(Registries.ITEM);
      // serialize ingredients with a registry-aware ops so tag-backed HolderSets encode as "#tag" references
      // (JsonOps.INSTANCE would stream their contents and throw on an unbound optional tag). Runtime never calls
      // serialize(), so SERIALIZE_OPS only matters here; allOf() forces toJson() synchronously before the finally runs.
      DynamicOps<JsonElement> previous = FluidContainerTransferManager.SERIALIZE_OPS;
      FluidContainerTransferManager.SERIALIZE_OPS = provider.createSerializationContext(JsonOps.INSTANCE);
      try {
        addTransfers();
        return allOf(allTransfers.entrySet().stream().map(entry -> saveJson(cache, entry.getKey(), entry.getValue().toJson())));
      } finally {
        FluidContainerTransferManager.SERIALIZE_OPS = previous;
      }
    });
  }

  /** Json with transfer and condition */
  private record TransferJson(IFluidContainerTransfer transfer, ICondition[] conditions) {
    /** Serializes this to JSON */
    public JsonElement toJson() {
      JsonElement element = FluidContainerTransferManager.GSON.toJsonTree(transfer, IFluidContainerTransfer.class);
      assert element.isJsonObject();
      if (conditions.length != 0) {
        // NeoForge conditions are codec-based and live under the "neoforge:conditions" key
        element.getAsJsonObject().add("neoforge:conditions",
          ICondition.LIST_CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, java.util.List.of(conditions)).getOrThrow());
      }
      return element;
    }
  }
}
