package slimeknights.mantle.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A small network implementation/wrapper around NeoForge's {@link CustomPacketPayload} system.
 * Instantiate in your mod class, register your packets in {@code registerPayloads}, and use the send
 * helpers to dispatch packets.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class NetworkWrapper {
  /** Unique channel name for this network, also used as the payload registrar namespace */
  public final ResourceLocation channelName;
  /** Network protocol version string */
  public final String version;

  /**
   * Creates a new network wrapper
   * @param channelName  Unique packet channel name
   * @deprecated Give your channel a version number.
   */
  @Deprecated
  public NetworkWrapper(ResourceLocation channelName) {
    this(channelName, "1");
  }

  public NetworkWrapper(ResourceLocation channelName, String version) {
    this.channelName = channelName;
    this.version = version;
  }


  /* Sending packets */

  /**
   * Sends a packet to the server
   * @param payload  Packet to send
   */
  public void sendToServer(CustomPacketPayload payload) {
    PacketDistributor.sendToServer(payload);
  }

  /**
   * Sends a vanilla packet to the given entity
   * @param packet  Packet
   * @param player  Player receiving the packet
   */
  public void sendVanillaPacket(Packet<?> packet, Entity player) {
    if (player instanceof ServerPlayer sPlayer) {
      sPlayer.connection.send(packet);
    }
  }

  /**
   * Sends a packet to a player
   * @param payload  Packet
   * @param player   Player to send
   */
  public void sendTo(CustomPacketPayload payload, Player player) {
    if (player instanceof ServerPlayer serverPlayer) {
      sendTo(payload, serverPlayer);
    }
  }

  /**
   * Sends a packet to a player
   * @param payload  Packet
   * @param player   Player to send
   */
  public void sendTo(CustomPacketPayload payload, ServerPlayer player) {
    if (!(player instanceof FakePlayer)) {
      PacketDistributor.sendToPlayer(player, payload);
    }
  }

  /**
   * Sends a packet to players near a location
   * @param payload      Packet to send
   * @param serverWorld  World instance
   * @param position     Position within range
   */
  public void sendToClientsAround(CustomPacketPayload payload, ServerLevel serverWorld, BlockPos position) {
    PacketDistributor.sendToPlayersTrackingChunk(serverWorld, new ChunkPos(position), payload);
  }

  /**
   * Sends a packet to all entities tracking the given entity, plus the entity itself if it is a player
   * @param payload  Packet
   * @param entity   Entity to check
   */
  public void sendToTrackingAndSelf(CustomPacketPayload payload, Entity entity) {
    PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
  }

  /**
   * Sends a packet to all entities tracking the given entity
   * @param payload  Packet
   * @param entity   Entity to check
   */
  public void sendToTracking(CustomPacketPayload payload, Entity entity) {
    PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
  }
}
