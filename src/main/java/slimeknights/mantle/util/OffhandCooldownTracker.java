package slimeknights.mantle.util;

import lombok.RequiredArgsConstructor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.network.MantleNetwork;
import slimeknights.mantle.network.packet.SwingArmPacket;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Logic to handle offhand having its own cooldown
 */
@RequiredArgsConstructor
public class OffhandCooldownTracker {
  public static final ResourceLocation KEY = Mantle.getResource("offhand_cooldown");
  /** @deprecated use {@link #get(Player)} */
  @Deprecated(forRemoval = true)
  public static final Function<OffhandCooldownTracker,Float> COOLDOWN_TRACKER = OffhandCooldownTracker::getCooldown;

  /**
   * Capability instance for offhand cooldown. In NeoForge 1.21 Forge's attached capability providers and
   * {@code AttachCapabilitiesEvent} no longer exist; the tracker is now exposed as an {@link EntityCapability}
   * registered for the player entity type on {@link RegisterCapabilitiesEvent}.
   */
  public static final EntityCapability<OffhandCooldownTracker,Void> CAPABILITY = EntityCapability.createVoid(KEY, OffhandCooldownTracker.class);

  /**
   * Per-player tracker storage. Forge attached the provider directly to the entity; NeoForge entity capability
   * providers are stateless, so we keep the mutable per-player state here. Weak keys let the entries be collected
   * with their players.
   * <p>
   * TODO PORT (stage 7): a {@code net.neoforged.neoforge.attachment.AttachmentType} on the player would be the
   * idiomatic replacement (survives respawn/dimension change and serializes if desired). This map preserves the old
   * "not serialized, reset on relog" behavior and keeps the class self-contained.
   */
  private static final Map<Player,OffhandCooldownTracker> TRACKERS = new WeakHashMap<>();

  /** Gets (creating if needed) the tracker for the given player. */
  private static OffhandCooldownTracker getOrCreate(Player player) {
    return TRACKERS.computeIfAbsent(player, OffhandCooldownTracker::new);
  }

  /** Registers any non-capability event listeners. No longer needed under NeoForge but kept for the stage-7 caller. */
  public static void init() {}

  /**
   * Registers the offhand cooldown capability for the player entity type. Wire onto {@link RegisterCapabilitiesEvent}.
   * <p>
   * TODO PORT (stage 7): registration uses {@code EntityType.PLAYER}; if a different player entity type is desired,
   * adjust here. Central wiring already calls this from {@code Mantle#registerCapabilities}.
   */
  public static void register(RegisterCapabilitiesEvent event) {
    event.registerEntity(CAPABILITY, net.minecraft.world.entity.EntityType.PLAYER, (player, ctx) -> getOrCreate(player));
  }

  /** Player receiving cooldowns */
  @Nullable
  private final Player player;
  /** Scale of the last cooldown */
  private int lastCooldown = 0;
  /** Time in ticks when the player can next attack for full power */
  private int attackReady = 0;

  /** Enables the cooldown tracker if above 0. Intended to be set in equipment change events, not serialized */
  private int enabled = 0;

  /** Null safe way to get the player's ticks existed */
  private int getTicksExisted() {
    if (player == null) {
      return 0;
    }
    return player.tickCount;
  }

  /** If true, the tracker is enabled despite a cooldown item not being held */
  @Deprecated(forRemoval = true)
  public boolean isEnabled() {
    return enabled > 0;
  }

  /**
   * Call this method when your item causing offhand cooldown to be needed is enabled and disabled. If multiple placces call this, the tracker will automatically keep enabled until all places disable
   * @param enable  If true, enable. If false, disable
   * @deprecated No longer used, so you can just remove calls.
   */
  @Deprecated(forRemoval = true)
  public void setEnabled(boolean enable) {
    if (enable) {
      enabled++;
    } else {
      enabled--;
    }
  }

  /**
   * Applies the given amount of cooldown
   * @param cooldown  Coolddown amount
   */
  public void applyCooldown(int cooldown) {
    this.lastCooldown = cooldown;
    this.attackReady = getTicksExisted() + cooldown;
  }

  /**
   * Returns a number from 0 to 1 denoting the current cooldown amount, akin to {@link Player#getAttackStrengthScale(float)}
   * @return  number from 0 to 1, with 1 being no cooldown
   */
  public float getCooldown() {
    int ticksExisted = getTicksExisted();
    if (ticksExisted > this.attackReady || this.lastCooldown == 0) {
      return 1.0f;
    }
    return Mth.clamp((this.lastCooldown + ticksExisted - this.attackReady) / (float) this.lastCooldown, 0f, 1f);
  }

  /**
   * Checks if we can perform another attack yet.
   * This counteracts rapid attacks via click macros, in a similar way to vanilla by limiting to once every 10 ticks
   */
  public boolean isAttackReady() {
    return getTicksExisted() + this.lastCooldown > this.attackReady;
  }


  /* Helpers */

  /** Gets the tracker instance for the target entity */
  @Nullable
  public static OffhandCooldownTracker get(Player player) {
    return player.getCapability(OffhandCooldownTracker.CAPABILITY);
  }

  /**
   * Gets the offhand cooldown for the given player
   * @param player  Player
   * @return  Offhand cooldown
   */
  public static float getCooldown(Player player) {
    OffhandCooldownTracker tracker = get(player);
    return tracker != null ? tracker.getCooldown() : 1.0f;
  }

  /**
   * Applies cooldown to the given player
   * @param player  Player
   * @param cooldown  Cooldown to apply
   */
  public static void applyCooldown(Player player, int cooldown) {
    OffhandCooldownTracker tracker = get(player);
    if (tracker != null) {
      tracker.applyCooldown(cooldown);
    }
  }

  /**
   * Applies cooldown to the given player
   * @param player  Player
   */
  public static boolean isAttackReady(Player player) {
    OffhandCooldownTracker tracker = get(player);
    return tracker == null || tracker.isAttackReady();
  }

  /**
   * Applies cooldown using attack speed
   * @param attackSpeed   Attack speed of the held item
   * @param cooldownTime  Relative cooldown time for the given source, 20 is vanilla
   */
  public static void applyCooldown(Player player, float attackSpeed, int cooldownTime) {
    applyCooldown(player, Math.round(cooldownTime / attackSpeed));
  }

  /** Swings the entities hand without resetting cooldown */
  public static void swingHand(LivingEntity entity, InteractionHand hand, boolean updateSelf) {
    if (!entity.swinging || entity.swingTime >= entity.getCurrentSwingDuration() / 2 || entity.swingTime < 0) {
      entity.swingTime = -1;
      entity.swinging = true;
      entity.swingingArm = hand;
      if (!entity.level().isClientSide) {
        SwingArmPacket packet = new SwingArmPacket(entity, hand);
        if (updateSelf) {
          MantleNetwork.INSTANCE.sendToTrackingAndSelf(packet, entity);
        } else {
          MantleNetwork.INSTANCE.sendToTracking(packet, entity);
        }
      }
    }
  }
}
