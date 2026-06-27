package slimeknights.mantle.recipe.condition;

import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.mantle.Mantle;

/**
 * Registration for Mantle's data load {@link ICondition} codecs.
 * <p>
 * Forge's {@code CraftingHelper.register(IConditionSerializer)} was replaced in NeoForge 1.21 by a datapack registry of
 * {@link MapCodec}s keyed under {@link NeoForgeRegistries.Keys#CONDITION_CODECS}. Each condition exposes a
 * {@code MapCodec<? extends ICondition>} (its {@code CODEC} field) which is registered here. Wire {@link #init(IEventBus)}
 * onto the mod event bus in {@link slimeknights.mantle.Mantle}.
 */
@SuppressWarnings("unused")
public class MantleConditions {
  private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS = DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Mantle.modId);

  private MantleConditions() {}

  /** Registers the condition codec register to the mod event bus */
  public static void init(IEventBus bus) {
    CONDITION_CODECS.register(bus);
  }

  /** Matches when the given tag is empty */
  public static final DeferredHolder<MapCodec<? extends ICondition>,MapCodec<TagEmptyCondition<?>>> TAG_EMPTY = CONDITION_CODECS.register("tag_empty", () -> TagEmptyCondition.CODEC);
  /** Matches when the given tag is filled */
  public static final DeferredHolder<MapCodec<? extends ICondition>,MapCodec<TagFilledCondition<?>>> TAG_FILLED = CONDITION_CODECS.register("tag_filled", () -> TagFilledCondition.CODEC);
  /** Matches when a combination of tags has a shared entry */
  public static final DeferredHolder<MapCodec<? extends ICondition>,MapCodec<TagCombinationCondition<?>>> TAG_COMBINATION_FILLED = CONDITION_CODECS.register("tag_combination_filled", () -> TagCombinationCondition.CODEC);
}
