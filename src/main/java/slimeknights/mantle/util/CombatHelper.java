package slimeknights.mantle.util;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Helpers for attacking with weapons */
public class CombatHelper {
  /** Resource location id for the anti-knockback attribute modifier. 1.21 attribute modifiers are keyed by {@link ResourceLocation} instead of a UUID/name string. */
  private static final ResourceLocation ANTI_KNOCKBACK_ID = Mantle.getResource("anti_knockback");
  /** Attribute modifier to disable knockback on a target */
  private static final AttributeModifier ANTI_KNOCKBACK_MODIFIER = new AttributeModifier(ANTI_KNOCKBACK_ID, 1f, Operation.ADD_VALUE);
  /** Item ability to disable the base knockback of the weapon. Requires replacing left click behavior of your weapon. */
  public static final ItemAbility NO_BASE_KNOCKBACK = ItemAbility.get("no_base_knockback");
  /** Item ability for a sword sweep attack, exposed for the stage-8 combat reimplementation. */
  public static final ItemAbility SWORD_SWEEP = ItemAbilities.SWORD_SWEEP;

  private CombatHelper() {}

  /** Gets the item stack in the main hand that contributes to attributes. Exposed for benefit of Tinkers' Construct which can optimize these methods for its tools. */
  public static ItemStack getMainhandAttributeStack(LivingEntity entity) {
    // clientside does not use last item stack, so our best choice is the mainhand stack
    if (entity.level().isClientSide) {
      return entity.getMainHandItem();
    }
    // serverside, the original used LivingEntity#getLastHandItem for attribute-accurate values, but that method is now
    // private in 1.21.1 with no public accessor. Fall back to the current mainhand stack (matches the clientside branch).
    // FIXME CONVERGE (stage 8 combat): revisit if/when an attribute-accurate "last hand item" accessor is needed.
    return entity.getMainHandItem();
  }

  /**
   * Gets a modifiable map that is a copy of the modifiers from the given attribute instance. All operations are guaranteed to have a valid set.
   * Note we use a map instead of a full attribute instance as we don't need the cache or other data structures.
   * <p>
   * TODO PORT (stage 8): 1.21 reworked the attribute system. {@link AttributeInstance#getModifiers()} now returns a flat
   * collection keyed by {@link ResourceLocation}; there is no per-{@link Operation} getter ({@code getModifiersOrEmpty}
   * was removed) and {@link AttributeModifier} is a record ({@code operation()}/{@code amount()}). The whole offhand
   * attribute computation mirrors Tinkers' combat reimplementation, which is out of scope for this shared-util port.
   * Reimplement against the 1.21 attribute API when porting the combat layer.
   */
  public static Map<Operation, Set<AttributeModifier>> copyModifiers(AttributeInstance instance) {
    Map<Operation, Set<AttributeModifier>> modifiers = new EnumMap<>(Operation.class);
    for (Operation operation : Operation.values()) {
      modifiers.put(operation, new HashSet<>());
    }
    for (AttributeModifier modifier : instance.getModifiers()) {
      modifiers.get(modifier.operation()).add(modifier);
    }
    return modifiers;
  }

  /**
   * Computes the value for the given attribute. Copied from {@link AttributeInstance#calculateValue}
   * <p>
   * TODO PORT (stage 8): {@link Operation} constants were renamed in 1.21 ({@code ADDITION}->{@code ADD_VALUE},
   * {@code MULTIPLY_BASE}->{@code ADD_MULTIPLIED_BASE}, {@code MULTIPLY_TOTAL}->{@code ADD_MULTIPLIED_TOTAL}) and
   * {@link AttributeModifier} is now a record. Logic ported to the new names; revisit alongside the combat port.
   */
  public static double computeAttribute(Attribute attribute, double base, Map<Operation,Set<AttributeModifier>> modifiers) {
    // addition modifiers
    for (AttributeModifier modifier : modifiers.get(Operation.ADD_VALUE)) {
      base += modifier.amount();
    }
    // multiply base
    double value = base;
    for (AttributeModifier modifier : modifiers.get(Operation.ADD_MULTIPLIED_BASE)) {
      value += base * modifier.amount();
    }
    // multiply total
    for (AttributeModifier modifier : modifiers.get(Operation.ADD_MULTIPLIED_TOTAL)) {
      value *= 1.0 + modifier.amount();
    }
    return attribute.sanitizeValue(value);
  }

  /** Checks if the given entity can be attacked. */
  public static boolean isAttackable(Entity attacker, Entity target) {
    return target.isAttackable() && !target.skipAttackInteraction(attacker);
  }


  /* Damage source creation */

  /** Makes a damage source from the given key */
  public static Holder<DamageType> damageType(RegistryAccess access, ResourceKey<DamageType> key) {
    return access.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
  }

  /** Makes a damage source from the given key */
  public static DamageSource damageSource(RegistryAccess access, ResourceKey<DamageType> key) {
    return new DamageSource(damageType(access, key));
  }

  /** Makes a damage source from the given key */
  public static DamageSource damageSource(Level level, ResourceKey<DamageType> key) {
    return new DamageSource(damageType(level.registryAccess(), key));
  }

  /** Makes a damage source from the given key for direct damage from an entity. */
  public static DamageSource damageSource(ResourceKey<DamageType> key, Entity entity) {
    return new DamageSource(damageType(entity.level().registryAccess(), key), entity);
  }

  /** Makes a damage source from the given key for indirect damage, such as from a projectile. */
  public static DamageSource damageSource(ResourceKey<DamageType> key, Entity direct, @Nullable Entity causing) {
    return new DamageSource(damageType(direct.level().registryAccess(), key), direct, causing);
  }
}
