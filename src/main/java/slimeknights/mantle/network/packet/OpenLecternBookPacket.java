package slimeknights.mantle.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.item.ILecternBookItem;

/**
 * Packet to open a book on a lectern
 */
public record OpenLecternBookPacket(BlockPos pos, ItemStack book) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<OpenLecternBookPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Mantle.modId, "open_lectern_book"));
  public static final StreamCodec<RegistryFriendlyByteBuf, OpenLecternBookPacket> STREAM_CODEC = StreamCodec.composite(
    BlockPos.STREAM_CODEC, OpenLecternBookPacket::pos,
    ItemStack.OPTIONAL_STREAM_CODEC, OpenLecternBookPacket::book,
    OpenLecternBookPacket::new);

  @Override
  public CustomPacketPayload.Type<OpenLecternBookPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    if (book.getItem() instanceof ILecternBookItem) {
      ((ILecternBookItem)book.getItem()).openLecternScreenClient(pos, book);
    }
  }
}
