package slimeknights.mantle.recipe.condition;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** Condition that checks when a tag is empty, for any registry. Doubles as a {@link net.neoforged.neoforge.common.conditions.ICondition} and a {@link LootItemCondition}. */
public class TagEmptyCondition<T> extends TagCondition<T> implements LootItemCondition {
  /** Codec serving as both the loot condition codec and the data load condition codec */
  @SuppressWarnings("unchecked")
  public static final MapCodec<TagEmptyCondition<?>> CODEC = TagCondition.makeCodec(tag -> new TagEmptyCondition<>((TagKey<Object>) tag));

  public TagEmptyCondition(TagKey<T> tag) {
    super(tag);
  }

  public TagEmptyCondition(ResourceKey<? extends Registry<T>> registry, Identifier name) {
    this(TagKey.create(registry, name));
  }

  @Override
  public MapCodec<TagEmptyCondition<?>> codec() {
    return CODEC;
  }

  @Override
  public boolean test(IContext context) {
    // 1.21.4: IContext#getTag was removed; a tag counts as empty when it is not loaded (matches NeoForge's own TagEmptyCondition)
    return !context.isTagLoaded(tag);
  }

  @Override
  public boolean test(LootContext context) {
    Registry<T> registry = registry(context);
    return registry != null && !registry.getTagOrEmpty(tag).iterator().hasNext();
  }
}
