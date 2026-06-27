package slimeknights.mantle.data;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;

import java.util.Arrays;

/** This class contains codecs for various vanilla things that we need to use in codecs. */
public class MantleCodecs {
  /** Codec for loot pool entries */
  public static final Codec<LootPoolEntryContainer> LOOT_ENTRY = LootPoolEntries.CODEC;
  /** Codec for an array of loot functions */
  public static final Codec<LootItemFunction[]> LOOT_FUNCTIONS = LootItemFunctions.ROOT_CODEC.listOf()
    .xmap(list -> list.toArray(new LootItemFunction[0]), Arrays::asList);
  /** Codec for ingredients, handling custom ingredient types */
  public static final Codec<Ingredient> INGREDIENT = Ingredient.CODEC;
}
