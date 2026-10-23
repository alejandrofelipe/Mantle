package slimeknights.mantle.recipe.condition;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Condition checking for a combination of tags having any entries
 * @param match  List of tags that the entry must match
 * @param ignore Entries in this tag will be ignored towards the match. If null, all entries are considered
 * @param <T>  Registry type
 */
@SuppressWarnings("unused")
public record TagCombinationCondition<T>(List<TagKey<T>> match, @Nullable TagKey<T> ignore) implements ICondition {
  public static final ResourceLocation ID = Mantle.getResource("tag_combination_filled");

  /** Codec for the condition, serializing the optional registry (defaulting to item), match tags, and optional ignore tag */
  @SuppressWarnings("unchecked")
  public static final MapCodec<TagCombinationCondition<?>> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
    // default to item registry if registry is unset, save some space in JSON by not setting registry if item (most common)
    ResourceLocation.CODEC.optionalFieldOf("registry", Registries.ITEM.location()).forGetter(c -> c.match.get(0).registry().location()),
    ResourceLocation.CODEC.listOf().fieldOf("match").forGetter(c -> c.match.stream().map(TagKey::location).toList()),
    ResourceLocation.CODEC.optionalFieldOf("ignore").forGetter(c -> Optional.ofNullable(c.ignore).map(TagKey::location))
  ).apply(inst, (registry, match, ignore) -> {
    ResourceKey<Registry<Object>> key = ResourceKey.createRegistryKey(registry);
    return new TagCombinationCondition<>(
      match.stream().map(id -> TagKey.create(key, id)).toList(),
      ignore.map(id -> TagKey.create(key, id)).orElse(null));
  }));

  public TagCombinationCondition {
    if (match.isEmpty()) {
      throw new IllegalArgumentException("Must match at least 1 tag");
    }
  }

  /** Creates a new instance ignoring the first tag and matching the rest */
  @SafeVarargs
  public static <T> TagCombinationCondition<T> match(@Nullable TagKey<T> ignore, TagKey<T>... match) {
    return new TagCombinationCondition<>(List.of(match), ignore);
  }

  /** Creates a new instance matching all the passed tags */
  @SafeVarargs
  public static <T> TagCombinationCondition<T> intersection(TagKey<T>... match) {
    return match(null, match);
  }

  /** Creates a new instance matching all the passed tags */
  public static <T> TagCombinationCondition<T> difference(TagKey<T> match, TagKey<T> ignore) {
    return match(ignore, match);
  }

  @Override
  public MapCodec<? extends ICondition> codec() {
    return CODEC;
  }

  @Override
  public boolean test(IContext context) {
    // 1.21.4: IContext exposes only isTagLoaded, not tag contents. Reading contents from the registries (as this did
    // during the port) is unreliable: this condition is evaluated while datapacks reload, before the static item tags
    // are bound, so a tag like minecraft:planks reads empty and the (sole) plank material recipe is silently dropped,
    // leaving planks unusable at the part builder. Gate purely on whether every match tag is loaded (non-empty), which
    // is the reliable reload-time signal; the exact intersection/difference is still enforced by the recipe's own
    // ingredient, so loading a recipe whose ingredient happens to resolve empty is harmless.
    for (TagKey<T> tag : match) {
      if (!context.isTagLoaded(tag)) {
        return false;
      }
    }
    return true;
  }
}
