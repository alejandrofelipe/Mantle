package slimeknights.mantle.loot;

import com.mojang.serialization.MapCodec;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
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
  // 26.1: the loot registries hold the MapCodecs directly (the LootItemConditionType/LootItemFunctionType/LootPoolEntryType wrappers are gone)
  private static final DeferredRegister<MapCodec<? extends LootItemCondition>> LOOT_CONDITIONS = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Mantle.modId);
  private static final DeferredRegister<MapCodec<? extends LootItemFunction>> LOOT_FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Mantle.modId);
  private static final DeferredRegister<MapCodec<? extends LootPoolEntryContainer>> LOOT_ENTRIES = DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, Mantle.modId);
  private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIERS = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Mantle.modId);

  /* Each loot object's codec() returns its CODEC constant, which is the registered value. */
  /** Matches if the passed tag is empty */
  public static final DeferredHolder<MapCodec<? extends LootItemCondition>,MapCodec<TagEmptyCondition<?>>> TAG_EMPTY = LOOT_CONDITIONS.register("tag_empty", () -> TagEmptyCondition.CODEC);
  /** Matches if the passed tag is filled */
  public static final DeferredHolder<MapCodec<? extends LootItemCondition>,MapCodec<TagFilledCondition<?>>> TAG_FILLED = LOOT_CONDITIONS.register("tag_filled", () -> TagFilledCondition.CODEC);
  /** Condition to match a block tag and property predicate */
  public static final DeferredHolder<MapCodec<? extends LootItemCondition>,MapCodec<BlockTagLootCondition>> BLOCK_TAG_CONDITION = LOOT_CONDITIONS.register("block_tag", () -> BlockTagLootCondition.CODEC);
  /** Condition for global loot modifiers that ensures a context set is present. Useful to check if we are in a specific context like entity. */
  public static final DeferredHolder<MapCodec<? extends LootItemCondition>,MapCodec<HasLootContextSetCondition>> HAS_CONTEXT_SET = LOOT_CONDITIONS.register("has_context_set", () -> HasLootContextSetCondition.CODEC);
  /** Function to add block entity texture to a dropped item */
  public static final DeferredHolder<MapCodec<? extends LootItemFunction>,MapCodec<RetexturedLootFunction>> RETEXTURED_FUNCTION = LOOT_FUNCTIONS.register("fill_retextured_block", () -> RetexturedLootFunction.CODEC);
  /** Function to add a fluid to an item fluid capability */
  public static final DeferredHolder<MapCodec<? extends LootItemFunction>,MapCodec<SetFluidLootFunction>> SET_FLUID_FUNCTION = LOOT_FUNCTIONS.register("set_fluid", () -> SetFluidLootFunction.CODEC);
  /** Entry to pull a value from a tag preference */
  public static final DeferredHolder<MapCodec<? extends LootPoolEntryContainer>,MapCodec<TagPreferenceLootEntry>> TAG_PREFERENCE = LOOT_ENTRIES.register("tag_preference", () -> TagPreferenceLootEntry.CODEC);
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
