package slimeknights.mantle.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.network.packet.DropLecternBookPacket;
import slimeknights.mantle.network.packet.OpenLecternBookPacket;
import slimeknights.mantle.network.packet.OpenNamedBookPacket;
import slimeknights.mantle.network.packet.SwingArmPacket;
import slimeknights.mantle.network.packet.UpdateHeldPagePacket;
import slimeknights.mantle.network.packet.UpdateInventoryPagePacket;
import slimeknights.mantle.network.packet.UpdateLecternPagePacket;

public class MantleNetwork {
  /**
   * Network instance
   * 1: 1.11.101 and before
   * 2: 1.11.102 - New predicate types, enum loadable nullable field optimization
   */
  public static final NetworkWrapper INSTANCE = new NetworkWrapper(Mantle.getResource("network"), "2");

  /**
   * Registers all Mantle payloads into the network. Wire this onto {@link RegisterPayloadHandlersEvent} on the mod bus.
   * @param event  Payload registration event
   */
  public static void registerPayloads(RegisterPayloadHandlersEvent event) {
    // NOTE: RegisterPayloadHandlersEvent#registrar(String) takes the network VERSION (not a namespace);
    // the payload namespace comes from each packet's CustomPacketPayload.Type ResourceLocation.
    PayloadRegistrar registrar = event.registrar(INSTANCE.version);
    // PLAY_TO_CLIENT
    registrar.playToClient(OpenLecternBookPacket.TYPE, OpenLecternBookPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(SwingArmPacket.TYPE, SwingArmPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToClient(OpenNamedBookPacket.TYPE, OpenNamedBookPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    // PLAY_TO_SERVER
    registrar.playToServer(UpdateHeldPagePacket.TYPE, UpdateHeldPagePacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToServer(UpdateInventoryPagePacket.TYPE, UpdateInventoryPagePacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToServer(UpdateLecternPagePacket.TYPE, UpdateLecternPagePacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    registrar.playToServer(DropLecternBookPacket.TYPE, DropLecternBookPacket.STREAM_CODEC, (payload, context) -> payload.handle(context));
    // TODO PORT (later stage): FluidContainerTransferPacket lives in the fluid.transfer package (out of scope here);
    //  register it via registrar.playToClient(...) when that package is ported.
  }
}
