package slimeknights.mantle.recipe.condition;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Function;

/** Common logic for {@link TagEmptyCondition} and {@link TagFilledCondition} */
@RequiredArgsConstructor
public abstract class TagCondition<T> implements ICondition {
  @Getter
  protected final TagKey<T> tag;
  @Nullable
  private Optional<Registry<T>> registry;

  /** Gets the registry */
  @Nullable
  protected Registry<T> registry(LootContext context) {
    // registry is not going to disappear within the lifetime of this object
    if (registry == null) {
      registry = context.getLevel().registryAccess().registry(tag.registry());
      if (registry.isEmpty()) {
        Mantle.logger.error("Failed to find registry for tag " + tag + " in " + getClass().getSimpleName() + ", this indicates a broken resource or datapack.");
      }
    }
    return registry.orElse(null);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(\"" + tag + "\")";
  }

  /**
   * Builds a map codec for a tag condition, serializing the optional registry (defaulting to item) and the tag.
   * @param constructor  Constructor taking a parsed tag key
   * @param <C>          Condition type
   * @return  Map codec for the condition
   */
  @SuppressWarnings("unchecked")
  public static <C extends TagCondition<?>> MapCodec<C> makeCodec(Function<TagKey<?>,C> constructor) {
    return RecordCodecBuilder.mapCodec(inst -> inst.group(
      // default to item registry if registry is unset, save some space in JSON by not setting registry if item (most common)
      ResourceLocation.CODEC.optionalFieldOf("registry", Registries.ITEM.location()).forGetter(c -> c.getTag().registry().location()),
      ResourceLocation.CODEC.fieldOf("tag").forGetter(c -> c.getTag().location())
    ).apply(inst, (registry, tag) -> constructor.apply(TagKey.create(ResourceKey.createRegistryKey(registry), tag))));
  }
}
