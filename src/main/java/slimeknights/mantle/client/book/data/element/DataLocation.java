package slimeknights.mantle.client.book.data.element;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import slimeknights.mantle.client.book.repository.BookRepository;

public class DataLocation implements IDataElement {

  public String file;
  public transient ResourceLocation location;

  @Override
  public void load(BookRepository source) {
    this.location = "$BLOCK_ATLAS".equals(this.file) ? TextureAtlas.LOCATION_BLOCKS : source.getResourceLocation(this.file, true);
  }
}
