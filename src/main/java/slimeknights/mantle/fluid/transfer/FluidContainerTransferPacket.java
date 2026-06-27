package slimeknights.mantle.fluid.transfer;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.network.packet.IThreadsafePacket;

import java.util.HashSet;
import java.util.Set;

/** Packet to sync fluid container transfer */
public record FluidContainerTransferPacket(Set<Item> items) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<FluidContainerTransferPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Mantle.modId, "fluid_container_transfer"));
  public static final StreamCodec<RegistryFriendlyByteBuf,FluidContainerTransferPacket> STREAM_CODEC = ByteBufCodecs.<RegistryFriendlyByteBuf,Item,Set<Item>>collection(HashSet::new, ByteBufCodecs.registry(Registries.ITEM))
    .map(FluidContainerTransferPacket::new, packet -> new HashSet<>(packet.items()));

  @Override
  public CustomPacketPayload.Type<FluidContainerTransferPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    FluidContainerTransferManager.INSTANCE.setContainerItems(items);
  }
}
