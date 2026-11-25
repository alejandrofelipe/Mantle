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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import javax.annotation.Nullable;

/**
 * Null-safe lookups of the NeoForge 21.9+ transfer capabilities ({@link Capabilities.Fluid}, {@link Capabilities.Item}), helpers for
 * working on isolated item copies, and transaction helpers.
 * <p>
 * PORT 1.21.10 (transfer step 1): the old {@code Capabilities.FluidHandler}/{@code ItemHandler} objects were removed, so handlers
 * registered as capabilities are native {@link ResourceHandler}s, returned as is. Use them inside an open transaction, passing that
 * transaction to {@code insert}/{@code extract}. The deprecated legacy bridges ({@code getLegacy*}, {@code asLegacy*}) were removed in
 * M5 phase 6b(v), once nothing used the legacy interfaces any more.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TransferHelper {
  /* Native lookups */

  /** Gets the native fluid handler at the given position, or null if the block exposes none */
  @Nullable
  public static ResourceHandler<FluidResource> getFluidResourceHandler(Level level, BlockPos pos, @Nullable Direction side) {
    return level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
  }

  /**
   * Gets the native fluid handler at the given position, or null if the block exposes none.
   * Pass the state and block entity when already known, or when the level no longer holds them (e.g. during removal).
   */
  @Nullable
  public static ResourceHandler<FluidResource> getFluidResourceHandler(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
    return level.getCapability(Capabilities.Fluid.BLOCK, pos, state, blockEntity, side);
  }

  /** Checks if the block at the given position exposes a fluid handler */
  public static boolean hasFluidHandler(Level level, BlockPos pos, @Nullable Direction side) {
    return getFluidResourceHandler(level, pos, side) != null;
  }

  /** Gets the native item handler at the given position, or null if the block exposes none */
  @Nullable
  public static ResourceHandler<ItemResource> getItemResourceHandler(Level level, BlockPos pos, @Nullable Direction side) {
    return level.getCapability(Capabilities.Item.BLOCK, pos, side);
  }

  /**
   * Gets the native item handler at the given position, or null if the block exposes none.
   * Pass the state and block entity when already known, or when the level no longer holds them (e.g. during removal).
   */
  @Nullable
  public static ResourceHandler<ItemResource> getItemResourceHandler(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
    return level.getCapability(Capabilities.Item.BLOCK, pos, state, blockEntity, side);
  }

  /** Gets the native fluid handler of the item in the given access, or null if it exposes none. Amounts are totals over the accessed items. */
  @Nullable
  public static ResourceHandler<FluidResource> getFluidResourceHandler(ItemAccess access) {
    return access.getCapability(Capabilities.Fluid.ITEM);
  }


  /* Isolated item copies */

  /**
   * Puts a copy of {@code count} items of the stack in a new single slot handler, so its item access (see {@link #access(ItemStacksResourceHandler)})
   * can be used without touching the stack: native item handlers exchange the item in the slot, read the result back with {@link #getStack(ItemStacksResourceHandler)}.
   * @param stack  Stack to copy, never modified
   * @param count  Number of items to put in the slot
   * @return  Single slot handler, empty if the stack is empty
   */
  public static ItemStacksResourceHandler isolate(ItemStack stack, int count) {
    ItemStacksResourceHandler slot = new ItemStacksResourceHandler(1);
    if (!stack.isEmpty() && count > 0) {
      slot.set(0, ItemResource.of(stack), count);
    }
    return slot;
  }

  /** Puts a one item copy of the stack in a new single slot handler, see {@link #isolate(ItemStack, int)} */
  public static ItemStacksResourceHandler isolate(ItemStack stack) {
    return isolate(stack, 1);
  }

  /** Gets the item access of the single slot of an {@linkplain #isolate(ItemStack, int) isolated copy} */
  public static ItemAccess access(ItemStacksResourceHandler slot) {
    return ItemAccess.forHandlerIndex(slot, 0);
  }

  /** Gets the stack in the single slot of an {@linkplain #isolate(ItemStack, int) isolated copy} */
  public static ItemStack getStack(ItemStacksResourceHandler slot) {
    return ItemUtil.getStack(slot, 0);
  }

  /**
   * Gets the native fluid handler of the item in an {@linkplain #isolate(ItemStack, int) isolated copy}, or null if it exposes none.
   * Safe to query inside a transaction, as changes only reach the copy.
   */
  @Nullable
  public static ResourceHandler<FluidResource> getFluidResourceHandler(ItemStacksResourceHandler slot) {
    return slot.getResource(0).isEmpty() ? null : getFluidResourceHandler(access(slot));
  }


  /* Transactions */

  /**
   * Opens a transaction nested in the transaction currently open on this thread, or a root transaction if none is open.
   * Lets code that may run inside a transaction it was not handed (e.g. recipe matching started from a native fill) query native handlers safely.
   * Only use it for changes to isolated copies (such as a one-item {@link ItemStacksResourceHandler}), as committing a nested transaction
   * leaves the final outcome to the outer transaction; close it without committing to discard the changes.
   */
  @SuppressWarnings("deprecation")  // getCurrentOpenedTransaction is the only way to nest into a transaction we were not handed
  public static Transaction openNestedOrRoot() {
    checkNotClosing("openNestedOrRoot");
    if (isTransactionOpen()) {
      return Transaction.open(Transaction.getCurrentOpenedTransaction());
    }
    return Transaction.openRoot();
  }

  /**
   * Checks if a transaction is open on this thread, in which case code must nest into it rather than open a root transaction.
   * <p>
   * Returns false while a transaction is {@linkplain Transaction.Lifecycle#CLOSING closing} (inside a journal's revert or release callback),
   * but no transaction can be opened then: {@link Transaction#openRoot()} throws, and so does {@link #openNestedOrRoot()}. Code reachable from journal callbacks must only restore its own state, never query handlers. During
   * {@linkplain Transaction.Lifecycle#ROOT_CLOSING root commit callbacks} it also returns false, and opening a new root transaction is allowed there.
   */
  public static boolean isTransactionOpen() {
    return Transaction.getLifecycle() == Transaction.Lifecycle.OPEN;
  }

  /** Throws a descriptive exception if a transaction is closing on this thread, where no transaction can be opened */
  private static void checkNotClosing(String method) {
    if (Transaction.getLifecycle() == Transaction.Lifecycle.CLOSING) {
      throw new IllegalStateException("TransferHelper." + method + " was called while a transaction is closing on this thread (e.g. from a journal's "
                                      + "revert callback), where no transaction can be opened. Journal callbacks must only restore their own state.");
    }
  }
}
