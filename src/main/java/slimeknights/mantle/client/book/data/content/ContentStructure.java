package slimeknights.mantle.client.book.data.content;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.book.data.BookData;
import slimeknights.mantle.client.book.data.element.TextData;
import slimeknights.mantle.client.book.repository.BookRepository;
import slimeknights.mantle.client.screen.book.ArrowButton;
import slimeknights.mantle.client.screen.book.BookScreen;
import slimeknights.mantle.client.screen.book.element.AnimationToggleElement;
import slimeknights.mantle.client.screen.book.element.BookElement;
import slimeknights.mantle.client.screen.book.element.StructureElement;
import slimeknights.mantle.client.screen.book.element.TextElement;
import slimeknights.mantle.util.html.HtmlElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ContentStructure extends PageContent {

  public static final transient ResourceLocation ID = Mantle.getResource("structure");

  @Getter
  public String title;
  public String data;

  public TextData[] description;

  public final transient StructureTemplate template = new StructureTemplate();
  public transient List<StructureTemplate.StructureBlockInfo> templateBlocks = new ArrayList<>();

  @Override
  public void load() {
    BookRepository repo = this.parent.source;

    if (this.data == null || this.data.isEmpty()) {
      return;
    }

    ResourceLocation location = repo.getResourceLocation(this.data);
    Resource resource = repo.getResource(location);

    if (resource == null) {
      return;
    }

    try {
      CompoundTag compoundnbt = NbtIo.readCompressed(resource.open(), NbtAccounter.unlimitedHeap());
      this.template.load(BuiltInRegistries.BLOCK.asLookup(), compoundnbt);
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }

    // FIXME CONVERGE: StructureTemplate#palettes is private in 1.21.1 with no public getter, and Palette#blocks(Block) NPEs on a null block,
    // so there is no clean public API to enumerate every block. Reflectively reading the first palette's block list preserves the prior behavior.
    this.templateBlocks = readFirstPaletteBlocks(this.template);

    for (int i = 0; i < this.templateBlocks.size(); i++) {
      StructureTemplate.StructureBlockInfo info = this.templateBlocks.get(i);
      if (info.state() == Blocks.AIR.defaultBlockState()) {
        this.templateBlocks.remove(i);
        i--;
      } else if (info.state().isAir())
        // Usually means it contains a block that has been renamed
        Mantle.logger.error("Found non-default air block in template " + this.data);
    }
  }

  /** Reads the block list from the first palette of a structure template. See FIXME above: no public API exists in 1.21.1. */
  @SuppressWarnings("unchecked")
  private static List<StructureTemplate.StructureBlockInfo> readFirstPaletteBlocks(StructureTemplate template) {
    try {
      java.lang.reflect.Field palettesField = StructureTemplate.class.getDeclaredField("palettes");
      palettesField.setAccessible(true);
      List<?> palettes = (List<?>) palettesField.get(template);
      if (palettes.isEmpty()) {
        return new ArrayList<>();
      }
      Object palette = palettes.get(0);
      return new ArrayList<>((List<StructureTemplate.StructureBlockInfo>) palette.getClass().getMethod("blocks").invoke(palette));
    } catch (ReflectiveOperationException e) {
      Mantle.logger.error("Failed to read structure template palette blocks", e);
      return new ArrayList<>();
    }
  }

  @Override
  public void build(BookData book, ArrayList<BookElement> list, boolean rightSide) {
    int y = getTitleHeight();

    if (this.title == null || this.title.isEmpty()) {
      y = 0;
    } else {
      this.addTitle(list, this.title);
    }

    int offset = 0;
    int structureSizeX = BookScreen.PAGE_WIDTH;
    int structureSizeY = BookScreen.PAGE_HEIGHT - y - 10;


    if (this.description != null && this.description.length > 0) {
      offset = 15;
      structureSizeX -= 2 * offset;
      structureSizeY -= 2 * offset;
      list.add(new TextElement(0, BookScreen.PAGE_HEIGHT - 10 - 2 * offset, BookScreen.PAGE_WIDTH, 2 * offset, this.description));
    }

    if (this.template != null && this.template.getSize() != BlockPos.ZERO) {
      boolean showButtons = this.template.getSize().getY() > 1;

      StructureElement structureElement = new StructureElement(offset, y, structureSizeX, structureSizeY, this.template, this.templateBlocks);
      list.add(structureElement);

      if (showButtons) {
        int col = book.appearance.structureButtonColor;
        int colHover = book.appearance.structureButtonColorHovered;
        int colToggled = book.appearance.structureButtonColorToggled;

        list.add(new AnimationToggleElement(BookScreen.PAGE_WIDTH - ArrowButton.ArrowType.REFRESH.w, 0, ArrowButton.ArrowType.REFRESH, col, colHover, colToggled, structureElement));
      }
    }
  }

  @Override
  public HtmlElement toHTML(BookData book) {
    return makeTitleHTML();
  }
}
