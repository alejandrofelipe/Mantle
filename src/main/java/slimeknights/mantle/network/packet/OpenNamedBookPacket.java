package slimeknights.mantle.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import slimeknights.mantle.Mantle;

public record OpenNamedBookPacket(ResourceLocation book) implements IThreadsafePacket {
  public static final CustomPacketPayload.Type<OpenNamedBookPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Mantle.modId, "open_named_book"));
  public static final StreamCodec<RegistryFriendlyByteBuf, OpenNamedBookPacket> STREAM_CODEC = StreamCodec.composite(
    ResourceLocation.STREAM_CODEC, OpenNamedBookPacket::book,
    OpenNamedBookPacket::new);

  @Override
  public CustomPacketPayload.Type<OpenNamedBookPacket> type() {
    return TYPE;
  }

  @Override
  public void handleThreadsafe(IPayloadContext context) {
    // TODO PORT (stage 6 client): BookLoader.getBook / BookData.openGui / BookCommand.bookNotFound not ported yet
    /*
    BookData bookData = BookLoader.getBook(book);
    if(bookData != null) {
      bookData.openGui(Component.literal("Book"), "", null, null);
    } else {
      ClientOnly.errorStatus(book);
    }
    */
  }

  static class ClientOnly {
    static void errorStatus(ResourceLocation book) {
      // TODO PORT (stage 6 client): BookCommand.bookNotFound not ported yet
    }
  }
}
