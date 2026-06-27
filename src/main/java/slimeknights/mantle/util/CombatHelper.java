package slimeknights.mantle.util;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
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
    // serverside, use the last item stack instead of the current. Should be the same, but if they mismatch then last item stack has correct attributes
    return entity.getLastHandItem(EquipmentSlot.MAINHAND);
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
   * Gets the attribute for the offhand by subtracting mainhand attributes and adding in offhand stack attributes.
   * <p>
   * TODO PORT (stage 8): {@code ItemStack#getAttributeModifiers(EquipmentSlot)} was replaced by the
   * {@code ItemAttributeModifiers} data component / {@code forEachModifier} in 1.21, and {@link Operation} constants were
   * renamed. This method reimplements Tinkers' offhand attribute logic and depends on that combat redesign, so it is
   * stubbed to the cached attribute value until the combat layer is ported. Behaviour: returns the entity's current
   * attribute value (ignoring the mainhand/offhand swap), which is safe but not offhand-accurate.
   */
  public static float getOffhandAttribute(ItemStack stack, LivingEntity entity, Attribute attribute) {
    Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute);
    AttributeInstance instance = entity.getAttribute(holder);
    if (instance == null) {
      return (float) entity.getAttributeBaseValue(holder);
    }
    return (float) instance.getValue();
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

  /**
   * Performs an attack, mimicking {@link Player#attack(Entity)}.
   * For use in {@link net.minecraft.world.item.Item#interactLivingEntity(ItemStack, Player, LivingEntity, InteractionHand)} primarily,
   * but can also be used to fake an attack.
   *
   * @param stack         Stack used for attacking.
   * @param target        Entity target
   * @param targetLiving  Living entity target. May be different in the case of multipart entities.
   * @param hand          Hand used for attacking.
   */
  public static boolean attack(ItemStack stack, Player player, Entity target, @Nullable LivingEntity targetLiving, InteractionHand hand) {
    return attack(stack, player, target, targetLiving, hand, player.damageSources().playerAttack(player));
  }

  /**
   * Performs an attack, mimicking {@link Player#attack(Entity)} but allowing the damage source to be swapped.
   *
   * @param stack         Stack used for attacking.
   * @param target        Entity target
   * @param targetLiving  Living entity target. May be different in the case of multipart entities.
   * @param hand          Hand used for attacking.
   * @param damageSource  Damage source to apply
   */
  public static boolean attack(ItemStack stack, Player player, Entity target, @Nullable LivingEntity targetLiving, InteractionHand hand, DamageSource damageSource) {
    // TODO PORT (stage 8): this method is a full reimplementation of Player#attack and depended entirely on APIs that
    //  were removed or fundamentally reshaped in 1.20.5-1.21:
    //   - net.minecraft.world.entity.MobType was deleted; LivingEntity#getMobType is gone.
    //   - EnchantmentHelper#getDamageBonus(stack, MobType), #getKnockbackBonus(player), #getFireAspect(player),
    //     #getSweepingDamageRatio(player) were all removed; enchantments are data-driven and require a ServerLevel
    //     (EnchantmentHelper#modifyDamage / getDamage / runIterationOnItem).
    //   - net.minecraftforge.common.ForgeHooks#getCriticalHit -> net.neoforged.neoforge.common.CommonHooks#fireCriticalHit
    //     (CriticalHitEvent#isCriticalHit / #getDamageMultiplier), and ForgeEventFactory#onPlayerDestroyItem ->
    //     net.neoforged.neoforge.event.EventHooks#onPlayerDestroyItem.
    //   - Entity#setSecondsOnFire -> #igniteForSeconds; ItemStack#getSweepHitBox / Player#getEntityReach moved.
    //   - net.minecraftforge.entity.PartEntity -> net.neoforged.neoforge.entity.PartEntity.
    //   - getOffhandAttribute (above) is itself stubbed pending the attribute-system port.
    //  Faithfully porting it requires the Tinkers combat/enchantment redesign, which is out of scope for the shared
    //  util package. Stubbed to a basic vanilla-equivalent hurt so callers keep compiling and a stack still deals
    //  damage; replace with the real implementation when the combat layer is ported.
    if (!isAttackable(player, target)) {
      return false;
    }
    float damage = hand == InteractionHand.OFF_HAND
                   ? getOffhandAttribute(stack, player, Attributes.ATTACK_DAMAGE.value())
                   : (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
    float cooldown = hand == InteractionHand.OFF_HAND ? OffhandCooldownTracker.getCooldown(player) : player.getAttackStrengthScale(0.5F);
    damage *= 0.2F + cooldown * cooldown * 0.8F;
    if (damage > 0) {
      target.hurt(damageSource, damage);
      if (!player.level().isClientSide && !stack.isEmpty() && target instanceof LivingEntity living) {
        stack.hurtEnemy(living, player);
      }
    }
    if (hand == InteractionHand.OFF_HAND) {
      OffhandCooldownTracker.applyCooldown(player, getOffhandAttribute(stack, player, Attributes.ATTACK_SPEED.value()), 20);
    } else {
      player.resetAttackStrengthTicker();
    }
    return true;
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
