package slimeknights.mantle.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Command to dump the tool tier ordering.
 * TODO PORT (stage 7): TierSortingRegistry and the {@code forge:item_tier_ordering.json} concept were removed
 *  in 1.20.5+ (verified: NeoForge 1.20.5 port notes mark TierSortingRegistry as "Removed"; vanilla replaced
 *  tier ordering with a tag/datapack-driven system and {@code Tier.getTag()} no longer exists). There is no
 *  direct replacement, so the command bodies are stubbed to report unavailability. Either drop this command or
 *  rebuild it around the vanilla {@code Tiers}/incorrect-blocks-for-drops tags when the harvest-tier feature is
 *  revisited centrally.
 */
public class HarvestTiersCommand {
  /** Sent to the user explaining the command is no longer available */
  private static final Component UNAVAILABLE = Component.translatable("command.mantle.harvest_tiers.unavailable");

  /**
   * Registers this sub command with the root command
   * @param subCommand  Command builder
   */
  public static void register(LiteralArgumentBuilder<CommandSourceStack> subCommand) {
    subCommand.requires(sender -> sender.hasPermission(MantleCommand.PERMISSION_EDIT_SPAWN))
              .then(Commands.literal("save").executes(source -> run(source, true)))
              .then(Commands.literal("log").executes(source -> run(source, false)))
              .then(Commands.literal("list").executes(HarvestTiersCommand::list));
  }

  /** Runs the command, dumping the tag */
  private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    // TODO PORT (stage 7): TierSortingRegistry removed; no sorted-tier list available in 1.21.
    context.getSource().sendSuccess(() -> UNAVAILABLE, true);
    return 0;
  }

  /** Runs the command, dumping the tag */
  private static int run(CommandContext<CommandSourceStack> context, boolean saveFile) throws CommandSyntaxException {
    // TODO PORT (stage 7): TierSortingRegistry removed; cannot dump item_tier_ordering.json in 1.21.
    context.getSource().sendSuccess(() -> UNAVAILABLE, true);
    return 0;
  }
}
