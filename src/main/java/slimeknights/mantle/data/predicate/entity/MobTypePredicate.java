package slimeknights.mantle.data.predicate.entity;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.record.RecordLoadable;

/**
 * Predicate matching entities whose type is in the given entity type tag.
 * <p>
 * Replaces the 1.20 {@code MobType} categories, removed in MC 1.21. Use entity type tags such as
 * {@code minecraft:undead}, {@code minecraft:arthropod}, {@code minecraft:illager} or {@code minecraft:aquatic}.
 */
public record MobTypePredicate(TagKey<EntityType<?>> tag) implements LivingEntityPredicate {
  /** Loader for a mob type predicate */
  public static final RecordLoadable<MobTypePredicate> LOADER = RecordLoadable.create(
    Loadables.ENTITY_TYPE_TAG.requiredField("tag", MobTypePredicate::tag), MobTypePredicate::new);

  @Override
  public boolean matches(LivingEntity input) {
    return input.getType().builtInRegistryHolder().is(tag);
  }

  @Override
  public RecordLoadable<? extends LivingEntityPredicate> getLoader() {
    return LOADER;
  }
}
