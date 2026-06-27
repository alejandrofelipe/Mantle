package slimeknights.mantle.loot;

import com.mojang.serialization.MapCodec;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.loot.condition.BlockTagLootCondition;
import slimeknights.mantle.loot.condition.ContainsItemModifierLootCondition;
import slimeknights.mantle.loot.condition.EmptyModifierLootCondition;
import slimeknights.mantle.loot.condition.HasLootContextSetCondition;
import slimeknights.mantle.loot.condition.InvertedModifierLootCondition;
import slimeknights.mantle.loot.entry.TagPreferenceLootEntry;
import slimeknights.mantle.loot.function.RetexturedLootFunction;
import slimeknights.mantle.loot.function.SetFluidLootFunction;
import slimeknights.mantle.recipe.condition.TagEmptyCondition;
import slimeknights.mantle.recipe.condition.TagFilledCondition;

import static slimeknights.mantle.loot.condition.ILootModifierCondition.MODIFIER_CONDITIONS;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MantleLoot {
  /* Deferred registers for each loot registry; registered to the mod bus in stage 7 (Mantle.java) via {@link #init(IEventBus)}. */
  private static final DeferredRegister<LootItemConditionType> LOOT_CONDITIONS = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Mantle.modId);
  private static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Mantle.modId);
  private static final DeferredRegister<LootPoolEntryType> LOOT_ENTRIES = DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, Mantle.modId);
  private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIERS = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Mantle.modId);

  /* Holders are resolved lazily via getType(); do not call get() before registration fires on the mod bus. */
  // TODO PORT (recipe.condition stage): TagEmptyCondition / TagFilledCondition currently live in the still-Forge
  // recipe.condition package and do not yet expose a MapCodec. The recipe.condition port must add a
  // `public static final MapCodec<TagEmptyCondition<?>> CODEC` (and the filled variant) serializing the optional
  // registry (default item) + tag, then these two registrations compile. Their getType() must also switch to
  // MantleLoot.TAG_EMPTY.get() / TAG_FILLED.get() since these are now DeferredHolders.
  /** Matches if the passed tag is empty */
  public static final DeferredHolder<LootItemConditionType,LootItemConditionType> TAG_EMPTY = LOOT_CONDITIONS.register("tag_empty", () -> new LootItemConditionType(TagEmptyCondition.CODEC));
  /** Matches if the passed tag is filled */
  public static final DeferredHolder<LootItemConditionType,LootItemConditionType> TAG_FILLED = LOOT_CONDITIONS.register("tag_filled", () -> new LootItemConditionType(TagFilledCondition.CODEC));
  /** Condition to match a block tag and property predicate */
  public static final DeferredHolder<LootItemConditionType,LootItemConditionType> BLOCK_TAG_CONDITION = LOOT_CONDITIONS.register("block_tag", () -> new LootItemConditionType(BlockTagLootCondition.CODEC));
  /** Condition for global loot modifiers that ensures a context set is present. Useful to check if we are in a specific context like entity. */
  public static final DeferredHolder<LootItemConditionType,LootItemConditionType> HAS_CONTEXT_SET = LOOT_CONDITIONS.register("has_context_set", () -> new LootItemConditionType(HasLootContextSetCondition.CODEC));
  /** Function to add block entity texture to a dropped item */
  public static final DeferredHolder<LootItemFunctionType<?>,LootItemFunctionType<?>> RETEXTURED_FUNCTION = LOOT_FUNCTIONS.register("fill_retextured_block", () -> new LootItemFunctionType<>(RetexturedLootFunction.CODEC));
  /** Function to add a fluid to an item fluid capability */
  public static final DeferredHolder<LootItemFunctionType<?>,LootItemFunctionType<?>> SET_FLUID_FUNCTION = LOOT_FUNCTIONS.register("set_fluid", () -> new LootItemFunctionType<>(SetFluidLootFunction.CODEC));
  /** Entry to pull a value from a tag preference */
  public static final DeferredHolder<LootPoolEntryType,LootPoolEntryType> TAG_PREFERENCE = LOOT_ENTRIES.register("tag_preference", () -> new LootPoolEntryType(TagPreferenceLootEntry.CODEC));

  static {
    // global loot modifiers
    GLOBAL_LOOT_MODIFIERS.register("add_entry", () -> AddEntryLootModifier.CODEC);
    GLOBAL_LOOT_MODIFIERS.register("replace_item", () -> ReplaceItemLootModifier.CODEC);

    // loot modifier conditions (Mantle GSON dispatch used inside GLM codecs, see ILootModifierCondition)
    MODIFIER_CONDITIONS.registerDeserializer(InvertedModifierLootCondition.ID, InvertedModifierLootCondition::deserialize);
    MODIFIER_CONDITIONS.registerDeserializer(EmptyModifierLootCondition.ID, EmptyModifierLootCondition.INSTANCE);
    MODIFIER_CONDITIONS.registerDeserializer(ContainsItemModifierLootCondition.ID, ContainsItemModifierLootCondition::deserialize);
  }

  /**
   * Registers all loot deferred registers to the mod event bus.
   * Called from the mod constructor in stage 7 ({@link slimeknights.mantle.Mantle}).
   */
  public static void init(IEventBus modBus) {
    LOOT_CONDITIONS.register(modBus);
    LOOT_FUNCTIONS.register(modBus);
    LOOT_ENTRIES.register(modBus);
    GLOBAL_LOOT_MODIFIERS.register(modBus);
  }
}
