package slimeknights.mantle.command.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.book.BookLoader;

import java.util.function.Consumer;

/**
 * Root command for all commands in mantle
 */
public class MantleClientCommand {
  /** Suggestion provider that lists registered book ids */
  public static SuggestionProvider<CommandSourceStack> REGISTERED_BOOKS;
  /** Suggestion provider that lists registered book domains */
  public static SuggestionProvider<CommandSourceStack> REGISTERED_BOOK_DOMAINS;

  /** Well-known vanilla atlas ids, used for {@code mantle sources atlases} suggestions since the live atlas set is private */
  private static final java.util.List<ResourceLocation> VANILLA_ATLASES = java.util.stream.Stream.of(
    "blocks", "banner_patterns", "beds", "chests", "shield_patterns", "shulker_boxes",
    "signs", "mob_effects", "paintings", "particles", "armor_trims", "decorated_pot", "gui"
  ).map(name -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/" + name + ".png")).toList();


  /** Registers all Mantle client command related content */
  @SuppressWarnings("deprecation")
  public static void init() {
    // register arguments
    REGISTERED_BOOKS = SuggestionProviders.register(Mantle.getResource("registered_books"), (context, builder) ->
      SharedSuggestionProvider.suggestResource(BookLoader.getAllBooks(), builder));
    REGISTERED_BOOK_DOMAINS = SuggestionProviders.register(Mantle.getResource("registered_book_domains"), (context, builder) ->
      SharedSuggestionProvider.suggest(BookLoader.getAllBooks().stream().map(ResourceLocation::getNamespace).distinct(), builder));

    // source command suggestions
    // ModelManager#atlases (and AtlasSet#atlases) are private in 1.21.1 with no public enumeration API,
    // so suggest the well-known vanilla atlas ids instead of reflecting the live atlas set.
    ClientSourcesCommand.registerMinecraft("atlases", (context, builder)
      -> SharedSuggestionProvider.suggestResource(VANILLA_ATLASES.stream(), builder));
    ClientSourcesCommand.registerMinecraft("blockstates", (context, builder)
      -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), builder));
    ClientSourcesCommand.register("item_models", "models/item", ".json", (context, builder)
      -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder));

    // add command listener
    NeoForge.EVENT_BUS.addListener(MantleClientCommand::registerCommand);
  }

  /** Registers a sub command for the root Mantle client command */
  private static void register(LiteralArgumentBuilder<CommandSourceStack> root, String name, Consumer<LiteralArgumentBuilder<CommandSourceStack>> consumer) {
    LiteralArgumentBuilder<CommandSourceStack> subCommand = Commands.literal(name);
    consumer.accept(subCommand);
    root.then(subCommand);
  }

  /** Event listener to register the Mantle client command */
  private static void registerCommand(RegisterClientCommandsEvent event) {
    LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("mantle");

    // sub commands
    register(builder, "book", BookCommand::register);
    register(builder, "clear_book_cache", ClearBookCacheCommand::register);
    // sources assets is registered as a client command
    register(builder, "sources", b -> {
      register(b, "assets", ClientSourcesCommand::register);
    });

    // register final command
    event.getDispatcher().register(builder);
  }
}
