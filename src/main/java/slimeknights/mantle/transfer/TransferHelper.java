package slimeknights.mantle.transfer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.FluidResourceHandlerItemAdapter;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Null-safe lookups of the NeoForge 21.9+ transfer capabilities ({@link Capabilities.Fluid}, {@link Capabilities.Item}),
 * bridged to the deprecated legacy interfaces through NeoForge's new-to-legacy adapters
 * ({@link IFluidHandler#of(ResourceHandler)}, {@link IItemHandler#of(ResourceHandler)}, {@link FluidResourceHandlerItemAdapter}).
 * <p>
 * PORT 1.21.10 (transfer step 1): the old {@code Capabilities.FluidHandler}/{@code ItemHandler} objects were removed and NeoForge
 * ships no legacy-to-new adapter, so handlers registered as capabilities are native {@link ResourceHandler}s, while code consuming
 * other blocks and items keeps working on the legacy interfaces through these lookups until the full migration (M5 phase 6).
 */
@SuppressWarnings("removal")  // the legacy interfaces are the point of this bridge until phase 6
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TransferHelper {
  /* Bridges */

  /** Null-safe {@link IFluidHandler#of(ResourceHandler)} */
  @Nullable
  public static IFluidHandler bridgeFluid(@Nullable ResourceHandler<FluidResource> handler) {
    return handler != null ? IFluidHandler.of(handler) : null;
  }

  /** Null-safe {@link IItemHandler#of(ResourceHandler)} */
  @Nullable
  public static IItemHandler bridgeItem(@Nullable ResourceHandler<ItemResource> handler) {
    return handler != null ? IItemHandler.of(handler) : null;
  }


  /* Blocks */

  /** Gets the fluid handler at the given position as a legacy handler, or null if the block exposes none */
  @Nullable
  public static IFluidHandler getFluidHandler(Level level, BlockPos pos, @Nullable Direction side) {
    return bridgeFluid(level.getCapability(Capabilities.Fluid.BLOCK, pos, side));
  }

  /**
   * Gets the fluid handler at the given position as a legacy handler, or null if the block exposes none.
   * Pass the state and block entity when already known, or when the level no longer holds them (e.g. during removal).
   */
  @Nullable
  public static IFluidHandler getFluidHandler(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
    return bridgeFluid(level.getCapability(Capabilities.Fluid.BLOCK, pos, state, blockEntity, side));
  }

  /** Checks if the block at the given position exposes a fluid handler, without creating a bridge */
  public static boolean hasFluidHandler(Level level, BlockPos pos, @Nullable Direction side) {
    return level.getCapability(Capabilities.Fluid.BLOCK, pos, side) != null;
  }

  /** Gets the item handler at the given position as a legacy handler, or null if the block exposes none */
  @Nullable
  public static IItemHandler getItemHandler(Level level, BlockPos pos, @Nullable Direction side) {
    return bridgeItem(level.getCapability(Capabilities.Item.BLOCK, pos, side));
  }

  /**
   * Gets the item handler at the given position as a legacy handler, or null if the block exposes none.
   * Pass the state and block entity when already known, or when the level no longer holds them (e.g. during removal).
   */
  @Nullable
  public static IItemHandler getItemHandler(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
    return bridgeItem(level.getCapability(Capabilities.Item.BLOCK, pos, state, blockEntity, side));
  }


  /* Items */

  /**
   * Gets the fluid handler of a stack as a legacy item handler, or null if the item exposes none.
   * <p>
   * The passed stack is <strong>never modified</strong>: the handler works on a copy held in a single slot, so read the result
   * through {@link IFluidHandlerItem#getContainer()}. Amounts are totals over the whole stack, as with any {@link ItemAccess}
   * based handler, so pass {@code stack.copyWithCount(1)} to work with a single container like the legacy capability did.
   * @param stack  Stack to look up
   * @return  Legacy handler, or null if the stack is empty or has no fluid capability
   */
  @Nullable
  public static IFluidHandlerItem getFluidHandler(ItemStack stack) {
    return getFluidHandler(stack, access -> access.getCapability(Capabilities.Fluid.ITEM));
  }

  /**
   * Same as {@link #getFluidHandler(ItemStack)}, but with a custom lookup of the native handler from the item access.
   * Useful to bridge a known handler (e.g. a handler constructor) without going through the capability.
   * @param stack   Stack to look up, never modified
   * @param lookup  Native handler lookup, may return null
   * @return  Legacy handler, or null if the stack is empty or the lookup found no handler
   */
  @Nullable
  public static IFluidHandlerItem getFluidHandler(ItemStack stack, Function<ItemAccess, ResourceHandler<FluidResource>> lookup) {
    if (stack.isEmpty()) {
      return null;
    }
    ItemStacksResourceHandler slot = new ItemStacksResourceHandler(1);
    slot.set(0, ItemResource.of(stack), stack.getCount());
    ItemAccess access = ItemAccess.forHandlerIndex(slot, 0);
    ResourceHandler<FluidResource> handler = lookup.apply(access);
    return handler != null ? new FluidResourceHandlerItemAdapter(handler, access) : null;
  }
}
