package slimeknights.mantle.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import slimeknights.mantle.command.argument.TagSourceArgument;
import slimeknights.mantle.command.tags.ModifyTagCommand;

import java.util.function.Consumer;

/**
 * Root command for all commands in mantle
 */
public class MantleCommand {
  /** Permission level that allows a user to build in spawn protected areas */
  public static final int PERMISSION_EDIT_SPAWN = 1;
  /** Permission level that can run standard game commands, used by command blocks and functions */
  public static final int PERMISSION_GAME_COMMANDS = 2;
  /** Standard permission level for server operators */
  public static final int PERMISSION_PLAYER_COMMANDS = 3;
  /** Permission level for the server owner, server console, or the player in single player */
  public static final int PERMISSION_OWNER = 4;

  /** @deprecated use {@link RegistryArgument#TAG} or {@link TagSourceArgument#TAG} */
  @Deprecated(forRemoval = true)
  public static SuggestionProvider<CommandSourceStack> VALID_TAGS;
  /** @deprecated use {@link RegistryArgument#VALUE} or {@link TagSourceArgument#VALUE} */
  @Deprecated(forRemoval = true)
  public static SuggestionProvider<CommandSourceStack> REGISTRY_VALUES;
  /** @deprecated use {@link RegistryArgument#REGISTRY} or {@link TagSourceArgument#SOURCE} */
  @Deprecated(forRemoval = true)
  public static SuggestionProvider<CommandSourceStack> REGISTRY;

  /** Registers all Mantle command related content */
  public static void init() {
    RegistryArgument.registerSuggestions();
    VALID_TAGS = RegistryArgument.TAG;
    REGISTRY_VALUES = RegistryArgument.VALUE;
    REGISTRY = RegistryArgument.REGISTRY;
    TagSourceArgument.registerSuggestions();

    // register interesting sources
    // 1.21: getLootData()/LootDataType were removed; loot tables are now a reloadable registry.
    // ReloadableServerRegistries.Holder#getKeys lists the ids for a given registry key.
    SourcesCommand.register(Registries.LOOT_TABLE.identifier().getPath(), (context, builder)
      -> SharedSuggestionProvider.suggestResource(
        context.getSource().getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds().map(ResourceKey::identifier), builder));
    // 1.21: the datapack folder is now "recipe" (was "recipes"); the subcommand keeps its "recipes" literal
    SourcesCommand.register("recipes", "recipe", ".json", (context, builder)
      // 1.21.4: CommandSourceStack#getRecipeNames was removed; recipe ids come from the server's RecipeManager
      -> SharedSuggestionProvider.suggestResource(context.getSource().getServer().getRecipeManager().getRecipes().stream().map(holder -> holder.id().identifier()), builder));

    // add command listener
    NeoForge.EVENT_BUS.addListener(MantleCommand::registerCommand);
  }

  /** Registers a sub command for the root Mantle command */
  private static void register(LiteralArgumentBuilder<CommandSourceStack> root, String name, Consumer<LiteralArgumentBuilder<CommandSourceStack>> consumer) {
    LiteralArgumentBuilder<CommandSourceStack> subCommand = Commands.literal(name);
    consumer.accept(subCommand);
    root.then(subCommand);
  }

  /** Event listener to register the Mantle command */
  private static void registerCommand(RegisterCommandsEvent event) {
    LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("mantle");
    CommandBuildContext context = event.getBuildContext();

    // sub commands
    register(builder, "tags", b -> {
      register(b, "view", ViewTagCommand::register);
      register(b, "entries", DumpTagCommand::register);
      register(b, "dump", DumpAllTagsCommand::register);
      register(b, "for", TagsForCommand::register);
      register(b, "preference", TagPreferenceCommand::register);
      ModifyTagCommand.register(b);
    });
    register(builder, "dump_loot_modifiers", DumpLootModifiers::register);
    register(builder, "harvest_tiers", HarvestTiersCommand::register);
    register(builder, "remove", b -> {
      b = b.requires(sender -> hasPermission(sender, PERMISSION_GAME_COMMANDS));
      register(b, "recipes", b2 -> RemoveRecipesCommand.register(b2, context));
      RemoveDataCommand.register(b);
    });
    // sources assets is registered as a client command
    register(builder, "sources", b -> {
      register(b, "data", SourcesCommand::register);
    });
    register(builder, "hunger", HungerCommand::register);

    // register final command
    event.getDispatcher().register(builder);
  }

  /* Helpers */

  /**
   * Returns true if the source either does not have reduced debug info or they have the proper level
   * Allows limiting a command that prints debug info to not work in reduced debug info
   * @param source             Command source
   * @param reducedDebugLevel  Level to use when reduced debug info is true
   * @return  True if the command can be run
   */
  public static boolean requiresDebugInfoOrOp(CommandSourceStack source, int reducedDebugLevel) {
    return !source.getLevel().getGameRules().get(GameRules.REDUCED_DEBUG_INFO) || hasPermission(source, reducedDebugLevel);
  }

  /**
   * Checks if the command source has the given operator permission level.
   * <p>
   * Exists so Mantle and downstream mods keep expressing permissions as the stable integer op levels ({@link #PERMISSION_EDIT_SPAWN} and friends)
   * instead of naming the vanilla permission types, which changed in 1.21.11 ({@code PermissionSet}/{@code Permission}) and may change again.
   * Use as {@code requires(source -> MantleCommand.hasPermission(source, MantleCommand.PERMISSION_GAME_COMMANDS))}.
   * @param source  Command source
   * @param level   Op level, 0 to 4, matching the old {@code CommandSourceStack#hasPermission(int)}
   * @return  True if the source has at least that level
   */
  public static boolean hasPermission(CommandSourceStack source, int level) {
    return hasPermission(source.permissions(), level);
  }

  /**
   * Checks if the player has the given operator permission level, replacing the removed {@code Player#hasPermissions(int)}.
   * @param player  Player to check
   * @param level   Op level, 0 to 4
   * @return  True if the player has at least that level
   * @see #hasPermission(CommandSourceStack, int)
   */
  public static boolean hasPermission(Player player, int level) {
    return hasPermission(player.permissions(), level);
  }

  /** Maps an op level to the matching vanilla permission; like the old int check, levels at or below 0 always pass and levels above 4 never do */
  private static boolean hasPermission(PermissionSet permissions, int level) {
    return switch (level) {
      case PERMISSION_EDIT_SPAWN -> permissions.hasPermission(Permissions.COMMANDS_MODERATOR);
      case PERMISSION_GAME_COMMANDS -> permissions.hasPermission(Permissions.COMMANDS_GAMEMASTER);
      case PERMISSION_PLAYER_COMMANDS -> permissions.hasPermission(Permissions.COMMANDS_ADMIN);
      case PERMISSION_OWNER -> permissions.hasPermission(Permissions.COMMANDS_OWNER);
      default -> level <= 0;
    };
  }
}
