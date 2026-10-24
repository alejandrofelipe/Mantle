package slimeknights.mantle.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import slimeknights.mantle.block.entity.INameableMenuProvider;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.mantle.inventory.BaseContainerMenu;

import javax.annotation.Nullable;

/**
 * Base class for blocks with an inventory
 */
@SuppressWarnings("WeakerAccess")
public abstract class InventoryBlock extends Block implements EntityBlock {

  protected InventoryBlock(BlockBehaviour.Properties builder) {
    super(builder);
  }

  /**
   * Called when the block is activated to open the UI. Override to return false for blocks with no inventory
   * @param player Player instance
   * @param world  World instance
   * @param pos    Block position
   * @return true if the GUI opened, false if not
   */
  protected boolean openGui(Player player, Level world, BlockPos pos) {
    if (!world.isClientSide()) {
      MenuProvider container = this.getMenuProvider(world.getBlockState(pos), world, pos);
      if (container != null && player instanceof ServerPlayer serverPlayer) {
        serverPlayer.openMenu(container, pos);
        if (player.containerMenu instanceof BaseContainerMenu<?> menu) {
          menu.syncOnOpen(serverPlayer);
        }
      }
    }

    return true;
  }

  @SuppressWarnings("deprecation")
  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult rayTraceResult) {
    if (player.isSuppressingBounce()) {
      return InteractionResult.PASS;
    }
    if (!world.isClientSide) {
      return this.openGui(player, world, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
    return InteractionResult.SUCCESS;
  }


  /* Naming */

  @Override
  public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(worldIn, pos, state, placer, stack);

    // set custom name from named stack
    Component customName = stack.get(DataComponents.CUSTOM_NAME);
    if (customName != null) {
      BlockEntity tileentity = worldIn.getBlockEntity(pos);
      if (tileentity instanceof INameableMenuProvider provider) {
        provider.setCustomName(customName);
      }
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  @Nullable
  public MenuProvider getMenuProvider(BlockState state, Level worldIn, BlockPos pos) {
    BlockEntity be = worldIn.getBlockEntity(pos);
    return be instanceof MenuProvider ? (MenuProvider) be : null;
  }


  /* Inventory handling */

  /**
   * Called by {@link MantleBlockEntity#preRemoveSideEffects(BlockPos, BlockState)} when this block is replaced by a different block,
   * while the block entity is still present. Server side only.
   * <p>
   * PORT 1.21.5: replaces the drop logic of the removed {@code onRemove}. The chunk now removes the block entity <em>before</em>
   * {@link #affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean)} runs, so drops must happen from the block entity.
   * Block entities for this block must extend {@link MantleBlockEntity}, or call this from their own {@code preRemoveSideEffects}.
   * @param state        Block state being removed
   * @param level        Level instance
   * @param pos          Block position
   * @param blockEntity  Block entity being removed
   */
  public void preRemoveSideEffects(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity) {
    // the level already holds the new state, so pass the old state and block entity to find the right capability
    IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, null);
    if (inventory != null) {
      dropInventoryItems(state, level, pos, inventory);
    }
  }

  @Override
  protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    Containers.updateNeighboursAfterDestroy(state, level, pos);
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
  }

  /**
   * Called when the block is replaced to drop contained items.
   * @param state       Block state
   * @param worldIn     Tile world
   * @param pos         Tile position
   * @param inventory   Item handler
   */
  protected void dropInventoryItems(BlockState state, Level worldIn, BlockPos pos, IItemHandler inventory) {
    dropInventoryItems(worldIn, pos, inventory);
  }

  /**
   * Drops all items from the given inventory in world
   * @param world      World instance
   * @param pos        Position to drop
   * @param inventory  Inventory instance
   */
  public static void dropInventoryItems(Level world, BlockPos pos, IItemHandler inventory) {
    double x = pos.getX();
    double y = pos.getY();
    double z = pos.getZ();
    for(int i = 0; i < inventory.getSlots(); ++i) {
      Containers.dropItemStack(world, x, y, z, inventory.getStackInSlot(i));
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public boolean triggerEvent(BlockState state, Level worldIn, BlockPos pos, int id, int param) {
    super.triggerEvent(state, worldIn, pos, id, param);
    BlockEntity be = worldIn.getBlockEntity(pos);
    return be != null && be.triggerEvent(id, param);
  }
}
