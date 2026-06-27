package slimeknights.mantle.data.predicate.entity;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.record.RecordLoadable;

/**
 * Predicate that checks if the given entity has the given enchantment on any of their equipment.
 * <p>
 * In 1.21 enchantments are a datapack registry referenced via {@link Holder}, so the predicate stores a
 * {@link ResourceKey} and resolves the holder from the entity's registry access at match time.
 */
public record HasEnchantmentEntityPredicate(ResourceKey<Enchantment> enchantment) implements LivingEntityPredicate {
  public static final RecordLoadable<HasEnchantmentEntityPredicate> LOADER = RecordLoadable.create(Loadables.ENCHANTMENT.requiredField("enchantment", HasEnchantmentEntityPredicate::enchantment), HasEnchantmentEntityPredicate::new);

  @Override
  public boolean matches(LivingEntity entity) {
    Holder<Enchantment> holder = entity.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(enchantment);
    return EnchantmentHelper.getEnchantmentLevel(holder, entity) > 0;
  }

  @Override
  public RecordLoadable<HasEnchantmentEntityPredicate> getLoader() {
    return LOADER;
  }
}
