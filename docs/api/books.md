# Books

Mantle's data-driven book system lets a mod ship a fully interactive, resource-pack-overridable manual (sections, pages, crafting recipes, structures, images) that opens in a custom GUI from an item, a lectern, or a command.

## What it's for

A consuming mod registers a book once (pointing it at one or more repositories of JSON resources) and then authors all of its content as assets — no code per page. The library handles loading, language selection, advancement-gated sections, the page-turning GUI (`BookScreen`), and even HTML/image export. You get a Patchouli-style guide that artists and translators can edit without recompiling, plus the ability to add your own page types when the built-ins are not enough.

## Key types

| Type | Purpose |
| --- | --- |
| `slimeknights.mantle.client.book.BookLoader` | Static registry: register books, register page/content types, register GSON adapters; also the reload listener. |
| `slimeknights.mantle.client.book.data.BookData` | Runtime model of one book (sections, appearance, transformers); also opens the GUI. |
| `slimeknights.mantle.client.book.data.SectionData` | A named group of pages, loaded from one JSON file; supports advancement requirements. |
| `slimeknights.mantle.client.book.data.PageData` | A single page: a `type` (content id) plus a `data` file. |
| `slimeknights.mantle.client.book.data.content.PageContent` | Abstract base for all page content; subclass it for custom page types. |
| `slimeknights.mantle.client.book.repository.BookRepository` | Abstract source of sections/resources; `FileRepository` reads from assets. |
| `slimeknights.mantle.client.book.repository.FileRepository` | Loads a book from `assets/<namespace>/<path>/`, with language folders. |
| `slimeknights.mantle.client.screen.book.BookScreen` | The `Screen` that renders the open book. |
| `slimeknights.mantle.client.book.BookHelper` | Reads/writes the saved page on a book `ItemStack` (via `CUSTOM_DATA`). |
| `slimeknights.mantle.item.LecternBookItem` / `ILecternBookItem` | Item base / interface for books that can be placed on and opened from a lectern. |

## How to use

### 1. Register the book and its repository

`BookLoader.registerBook` returns a `BookData` reference immediately, but it is **not** populated until resources load (or reload). Call it during client setup. The returned object is the handle you keep to open the GUI later.

```java
import net.minecraft.resources.ResourceLocation;
import slimeknights.mantle.client.book.BookLoader;
import slimeknights.mantle.client.book.data.BookData;
import slimeknights.mantle.client.book.repository.FileRepository;

public static BookData MY_BOOK;

// during FMLClientSetupEvent (or enqueueWork)
MY_BOOK = BookLoader.registerBook(
    ResourceLocation.fromNamespaceAndPath("examplemod", "guide"),
    new FileRepository(ResourceLocation.fromNamespaceAndPath("examplemod", "books/guide")));
```

`registerBook(id, repositories...)` adds an index page and a per-section table of contents by default. Use the overload `registerBook(id, appendIndex, appendContentTable, repositories...)` to disable either. A `FileRepository` points at `assets/<namespace>/<path>/`.

The `BookLoader` itself is a `ResourceManagerReloadListener` — Mantle registers an instance via `RegisterClientReloadListenersEvent` (`event.registerReloadListener(new BookLoader())`), which is what triggers `BookData.load()` and re-reads every registered book whenever resource packs or the language change. You do not need to register your own loader; just call the static `registerBook`.

### 2. Lay out the resources

Under the repository root (`assets/examplemod/books/guide/`):

```
books/guide/
  index.json                 # list of sections (SectionData[])
  appearance.json            # optional: cover title, colors, fonts
  sections/basic.json        # the pages of a section (PageData[])
  en_us/                     # language folder (FileRepository prefers this)
    text.json                # a page's content file
  images/icon.png            # textures referenced by pages
```

`FileRepository.getSections()` reads `index.json` as a `SectionData[]`. Each section's `data` points at a file containing a `PageData[]`. Path lookup tries `<root>/<selected_lang>/<path>`, then `<root>/en_us/<path>`, then `<root>/<path>`, so language-specific page text overrides the default automatically.

`index.json` — the sections:

```json
[
  {
    "name": "basic_pages",
    "data": "sections/basic.json",
    "icon": { "item": "minecraft:book" }
  }
]
```

`sections/basic.json` — the pages of that section. `name` is used for navigation links (`section.page`), `type` is the registered content id, `data` is the per-page content file:

```json
[
  { "name": "intro", "type": "text",  "data": "intro.json" },
  { "name": "recipe", "type": "crafting", "data": "first_recipe.json" }
]
```

`en_us/intro.json` — the content for a `text` page (`ContentText` fields: `title`, `text[]`):

```json
{
  "title": "Welcome",
  "text": [
    { "text": "Lorem ipsum dolor sit amet, consectetur adipiscing elit." },
    { "text": "A new paragraph with gold text.", "paragraph": true, "color": "gold" }
  ]
}
```

Each entry in `text[]` is a `TextData` (fields include `text`, `color`, `bold`, `italic`, `underlined`, `paragraph`, `linebreak`, `scale`, `action`, `tooltip`).

### 3. Built-in content types

Registered automatically in the `BookLoader` constructor. The JSON `"type"` is the path of the content id (namespace `mantle`):

| `type` | Class | Notes |
| --- | --- | --- |
| `blank` | `ContentBlank` | Empty page. |
| `text` | `ContentText` | Title + `TextData[]`. |
| `left_padding` / `right_padding` | `ContentPadding.ContentLeftPadding` / `ContentRightPadding` | Forces the next page onto a specific side. |
| `image` | `ContentImage` | Full-page `ImageData`. |
| `image_text` | `ContentImageText` | Image on top, text below. |
| `text_image` | `ContentTextImage` | Text on top, image below. |
| `text_left_image` / `text_right_image` | `ContentTextLeftImage` / `ContentTextRightImage` | Text wrapping an image. |
| `crafting` | `ContentCrafting` | Manual `grid`/`result` or an auto-populated vanilla `recipe`. |
| `smelting` | `ContentSmelting` | Furnace recipe. |
| `smithing` | `ContentSmithing` | Smithing recipe. |
| `block_interaction` | `ContentBlockInteraction` | Input item + block. |
| `structure` | `ContentStructure` | Renders a `.nbt` structure template. |
| `index` | `ContentIndex` | Auto-generated index (extends `ContentListing`). |
| `showcase` | `ContentShowcase` | Single item with text below. |

For example, a `crafting` page can auto-fill from a registered recipe:

```json
{ "title": "Stick", "recipe": "minecraft:stick" }
```

or be populated manually with `grid_size`, a `grid` of ingredients, and a `result` (see `ContentCrafting`).

### 4. Add a custom content type

Subclass `PageContent`, give it a public id, deserialize fields straight from JSON (GSON populates public non-transient fields), and build the on-screen elements in `build`. Register the type with `BookLoader.registerPageType` **before** the book loads (e.g. in client setup, alongside `registerBook`).

```java
import net.minecraft.resources.ResourceLocation;
import slimeknights.mantle.client.book.data.BookData;
import slimeknights.mantle.client.book.data.content.PageContent;
import slimeknights.mantle.client.book.data.element.TextData;
import slimeknights.mantle.client.screen.book.BookScreen;
import slimeknights.mantle.client.screen.book.element.BookElement;
import slimeknights.mantle.client.screen.book.element.TextElement;
import slimeknights.mantle.util.html.HtmlGroup;
import slimeknights.mantle.util.html.HtmlSerializable;

import java.util.ArrayList;

public class ContentGreeting extends PageContent {
  public static final ResourceLocation ID =
      ResourceLocation.fromNamespaceAndPath("examplemod", "greeting");

  // public, non-transient fields are filled by GSON from the page's data file
  public String title = null;
  public TextData[] body;

  @Override
  public void build(BookData book, ArrayList<BookElement> list, boolean rightSide) {
    int y = 0;
    if (title != null && !title.isEmpty()) {
      addTitle(list, title);          // helper from PageContent
      y = getTitleHeight();
    }
    if (body != null && body.length > 0) {
      list.add(new TextElement(0, y, BookScreen.PAGE_WIDTH, BookScreen.PAGE_HEIGHT - y, body));
    }
  }

  @Override
  public HtmlSerializable toHTML(BookData book) {
    return HtmlGroup.indent().add(makeTitleHTML(), TextData.toHtml(body, book));
  }
}
```

```java
// register before the book loads
BookLoader.registerPageType(ContentGreeting.ID, ContentGreeting.class);
```

Now `"type": "examplemod:greeting"` (or just `"greeting"` if you reused the `mantle` namespace) is valid in a section. If you need a custom JSON shape for a shared type, register a GSON adapter with `BookLoader.registerGsonTypeAdapter(type, adapter)`; the shared `Gson` is rebuilt lazily on next use.

### 5. Open the book

`BookData` implements `BookScreenOpener` and exposes several entry points. The lowest-level one is `openGui(Component title, String page, Consumer<String> pageUpdater)`; `page` is a `"section.page"` location (empty string starts at the cover).

**From a held / inventory book item** — the `openGui(InteractionHand, ItemStack)` and `openGui(int slot, ItemStack)` overloads read the saved page out of the stack via `BookHelper.getCurrentSavedPage` and wire up a page updater for you:

```java
// client-side, e.g. in Item#use after sending to client
MY_BOOK.openGui(hand, heldStack);
```

**From a lectern** — make your item extend `LecternBookItem` (which implements `ILecternBookItem`) and implement `openLecternScreenClient` to call `BookData.openGui(BlockPos, ItemStack)`. `LecternBookItem.useOn` already places the book on a lectern, and the static `LecternBookItem.interactWithBlock(PlayerInteractEvent.RightClickBlock)` event handler opens it when the lectern is clicked. `openGui(BlockPos, ItemStack)` adds a "take book" button that drops the lectern's copy.

```java
public class GuideItem extends LecternBookItem {
  public GuideItem(Properties props) { super(props); }

  @Override
  public void openLecternScreenClient(BlockPos pos, ItemStack book) {
    MyMod.MY_BOOK.openGui(pos, book);   // client only
  }
}
```

**From a command** — Mantle registers `/mantle book open <id>` (see `BookCommand`), which looks the book up with `BookLoader.getBook(id)` and calls `openGui`. It also provides `export_images` and `export_html` subcommands that drive `BookScreen` headlessly.

### 6. The BookScreen

`BookScreen` is a vanilla `Screen` that draws a two-page spread plus a cover. You rarely construct it directly — `BookData.openGui` does that — but it is useful to know its shape:

```java
new BookScreen(Component title, BookData book, String page,
               @Nullable Consumer<String> pageUpdater,
               @Nullable Consumer<?> bookPickup);
```

It exposes `PAGE_WIDTH` / `PAGE_HEIGHT` constants (used by content `build` methods), `nextPage()` / `previousPage()` / `openPage(int)` navigation, and a `BookScreen.AdvancementCache` that gates locked sections (`SectionData.requirements`). Arrow keys, A/D, and scroll turn pages; F3 toggles a debug overlay.

## Datagen

The book system is **resource-driven, not provider-driven** — there is no data generator for books in Mantle. You author `index.json`, section files, and per-page content files by hand under `assets/<namespace>/<path>/`. The closest thing to "generation" is the runtime export command, `/mantle book export_images` / `export_html`, which renders each page of a registered book to PNG (and optionally HTML), implemented in `BookCommand`.

## NeoForge 1.21.1 notes

- **Saved page now lives in `CUSTOM_DATA`, not raw NBT.** `BookHelper.getCurrentSavedPage` / `writeSavedPageToBook` read and write through `DataComponents.CUSTOM_DATA` / `CustomData.update`, under the `mantle` → `book` → `current_page` compound, instead of `ItemStack#getTag`. See the [migration guide](../migration/1.20-to-1.21.1.md#11-items--blocks) for details.
- **`BookScreen` render signatures use `GuiGraphics`.** `render`, page background blits, and string drawing all go through `GuiGraphics` (with `PoseStack` from `graphics.pose()`); there is no `Matrix4f`/`blit`-by-`PoseStack` path anymore. The advancement cache implements `ClientAdvancements.Listener` keyed by `AdvancementHolder`/`AdvancementNode`. See the [migration guide](../migration/1.20-to-1.21.1.md#10-client--models) for details.
- **Font access is reduced.** `BookScreen.getAltFont()` / `getUniformFont()` now fall back to the default `Minecraft#font` because `FontManager`'s font sets are private in 1.21.1 (marked `FIXME CONVERGE` in the source). To force the uniform/alt font, apply `Style#withFont(...)` at the call site instead of swapping the `Font` instance. See the [migration guide](../migration/1.20-to-1.21.1.md#10-client--models) for details.
- **`ContentStructure` reads its template palette reflectively.** `StructureTemplate#palettes` is private with no public getter in 1.21.1, so enumerating blocks uses reflection (`FIXME CONVERGE`); templates load with `BuiltInRegistries.BLOCK.asLookup()`. See the [migration guide](../migration/1.20-to-1.21.1.md#10-client--models) for details.
- **`OpenNamedBookPacket` is currently stubbed.** Server-driven "open named book" is marked `TODO PORT` and does nothing yet; open books client-side via `BookData.openGui` or the `/mantle book open` command. See the [migration guide](../migration/1.20-to-1.21.1.md#10-client--models) for details.
