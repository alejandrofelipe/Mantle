package slimeknights.mantle.fluid.transfer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition.IContext;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.gson.GenericRegisteredSerializer;
import slimeknights.mantle.data.loadable.RegistryJsonOps;
import slimeknights.mantle.network.MantleNetwork;
import slimeknights.mantle.util.JsonHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Logic for filling and emptying fluid containers that are not fluid handlers */
@Log4j2
public class FluidContainerTransferManager extends SimpleJsonResourceReloadListener<JsonElement> {
  /** Map of all modifier types that are expected to load in data packs */
  public static final GenericRegisteredSerializer<IFluidContainerTransfer> TRANSFER_LOADERS = new GenericRegisteredSerializer<>();
  /** Folder for saving the logic */
  public static final String FOLDER = "mantle/fluid_transfer";
  /**
   * DynamicOps used by transfer {@code serialize()} during datagen. A registry-aware ops lets tag-backed HolderSet
   * ingredients serialize as "#tag" references without the (possibly optional/unbound) tag being populated; plain
   * {@link JsonOps#INSTANCE} streams the holder set's contents and throws on such a tag. Runtime only deserializes, so
   * this stays {@code JsonOps.INSTANCE} there; {@link AbstractFluidContainerTransferProvider#run} swaps it around its run.
   */
  public static DynamicOps<JsonElement> SERIALIZE_OPS = JsonOps.INSTANCE;
  /**
   * DynamicOps used by transfer {@code DESERIALIZER}s at runtime reload. In 1.21.4 an {@link Ingredient} is a
   * {@code HolderSet<Item>}, so parsing a string/tag ingredient needs a registry-aware ops; a {@link JsonOps#INSTANCE}
   * throws "without registry" and a {@link BuiltInRegistries}-backed fallback throws "Missing tag" because datapack tags
   * are not bound there during the first server reload. {@link #apply} sets this to the reload's
   * {@link AddServerReloadListenersEvent#getRegistryAccess() registry access} (tags bound by then), falling back to
   * {@link RegistryJsonOps#serialize()} only when no reload access was captured (e.g. direct test calls).
   */
  public static DynamicOps<JsonElement> DESERIALIZE_OPS = RegistryJsonOps.serialize();
  /** GSON instance */
  public static final Gson GSON = (new GsonBuilder())
    .registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer())
    .registerTypeHierarchyAdapter(IFluidContainerTransfer.class, TRANSFER_LOADERS)
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create();
  /** Singleton instance of the manager */
  public static final FluidContainerTransferManager INSTANCE = new FluidContainerTransferManager();

  /** List of loaded transfer logic, only exists serverside */
  private List<IFluidContainerTransfer> transfers = Collections.emptyList();

  /** Set of all items that match a recipe, exists on both sides */
  @Setter @Nullable
  private Set<Item> containerItems = Collections.emptySet();

  /** Condition context for tags */
  private IContext context = IContext.EMPTY;

  private FluidContainerTransferManager() {
    // 1.21.4: SimpleJsonResourceReloadListener is codec-based; pass a passthrough JSON codec so #apply still receives raw JsonElements (the custom GSON handles decoding)
    super(ExtraCodecs.JSON, FileToIdConverter.json(FOLDER));
  }

  /** Lazily initializes the set of container items */
  protected Set<Item> getContainerItems() {
    if (this.containerItems == null) {
      List<Item> builder = new ArrayList<>();
      Consumer<Item> consumer = builder::add;
      for (IFluidContainerTransfer transfer : transfers) {
        transfer.addRepresentativeItems(consumer);
      }
      this.containerItems = Set.copyOf(builder);
    }
    return this.containerItems;
  }

  /** For internal use only */
  public void init() {
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, AddServerReloadListenersEvent.class, e -> {
      e.addListener(Mantle.getResource("fluid_container_transfer"), this);
      this.context = e.getConditionContext();
    });
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, OnDatapackSyncEvent.class, e -> JsonHelper.syncPackets(e, MantleNetwork.INSTANCE, new FluidContainerTransferPacket(this.getContainerItems())));
  }

  /** Loads transfer from JSON */
  @Nullable
  private IFluidContainerTransfer loadFluidTransfer(ResourceLocation key, JsonObject json) {
    try {
      // TODO PORT (stage 5 recipe conditions): the Forge CraftingHelper.processConditions(JsonArray, IContext) gate is
      //  gone; NeoForge load conditions are codec-based and resolved via a ConditionalOps-backed decode using the
      //  IContext from AddReloadListenerEvent#getConditionContext (stored in `context`). Until the recipe condition
      //  stack is ported, a "conditions" array in the JSON is ignored and the transfer is always loaded.
      return GSON.fromJson(json, IFluidContainerTransfer.class);
    } catch (JsonSyntaxException e) {
      log.error("Failed to load fluid container transfer info from {}", key, e);
    }
    return null;
  }

  @Override
  protected void apply(Map<ResourceLocation,JsonElement> splashList, ResourceManager manager, ProfilerFiller profiler) {
    long time = System.nanoTime();
    // NeoForge injects the reload's registry lookup (its lookupWithUpdatedTags, with the staged datapack item tags) into
    // every ContextAwareReloadListener; SimpleJsonResourceReloadListener is one. Build the deserialize ops from it so a
    // tag-backed HolderSet ingredient resolves exactly like vanilla recipe loading does. Fall back to the
    // BuiltInRegistries-backed ops only when no lookup was injected (e.g. a direct unit-test call to apply).
    HolderLookup.Provider lookup = getRegistryLookup();
    DESERIALIZE_OPS = lookup != RegistryAccess.EMPTY ? lookup.createSerializationContext(JsonOps.INSTANCE) : RegistryJsonOps.serialize();
    this.transfers = splashList.entrySet().stream()
                               .map(entry -> loadFluidTransfer(entry.getKey(), entry.getValue().getAsJsonObject()))
                               .filter(Objects::nonNull)
                               .toList();
    this.containerItems = null;
    log.info("Loaded {} dynamic modifiers in {} ms", transfers.size(), (System.nanoTime() - time) / 1000000f);
  }

  /**
   * Checks if the given stack could possibly match, used client side to determine if the fluid transfer falls back to opening the UI
   * @param item  Item to check
   * @return  True if a match is possible, basically just checks item ID
   */
  public boolean mayHaveTransfer(ItemLike item) {
    return getContainerItems().contains(item.asItem());
  }

  /**
   * Checks if the given stack could possibly match, used client side to determine if the fluid transfer falls back to opening the UI
   * @param stack  Stack to check
   * @return  True if a match is possible, basically just checks item ID
   */
  public boolean mayHaveTransfer(ItemStack stack) {
    return getContainerItems().contains(stack.getItem());
  }

  /** Gets the transfer for the given item and fluid, or null if its not a valid item and fluid */
  @Nullable
  public IFluidContainerTransfer getTransfer(ItemStack stack, FluidStack fluid) {
    for (IFluidContainerTransfer transfer : transfers) {
      if (transfer.matches(stack, fluid)) {
        return transfer;
      }
    }
    return null;
  }
}
