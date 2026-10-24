package slimeknights.mantle.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import slimeknights.mantle.block.RetexturedBlock;
import slimeknights.mantle.block.entity.IRetexturedBlockEntity;
import slimeknights.mantle.util.RetexturedHelper;

import java.util.function.Consumer;

/**
 * Block item for a retexturable block, adding the texture block to the tooltip.
 * 1.21.5 removed {@code Block#appendHoverText}, so the tooltip formerly added by {@link RetexturedBlock} now lives on the item.
 * <p>
 * Use alongside {@link IRetexturedBlockEntity} and {@link RetexturedBlock}
 */
public class RetexturedBlockItem extends BlockTooltipItem {
  public RetexturedBlockItem(Block block, Item.Properties builder) {
    super(block, builder);
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flagIn) {
    RetexturedHelper.addTooltip(stack, tooltip, flagIn);
    super.appendHoverText(stack, context, display, tooltip, flagIn);
  }
}
