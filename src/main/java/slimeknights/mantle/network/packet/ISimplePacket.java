package slimeknights.mantle.network.packet;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Base packet interface for Mantle's networking. In NeoForge every packet is a {@link CustomPacketPayload};
 * this interface simply adds the {@link #handle(IPayloadContext)} method used by the payload registrar.
 */
public interface ISimplePacket extends CustomPacketPayload {
  /**
   * Handles receiving the packet
   * @param context  Payload context, used to enqueue main thread work and fetch the player
   */
  void handle(IPayloadContext context);
}
