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
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Null-safe lookups of the NeoForge 21.9+ transfer capabilities ({@link Capabilities.Fluid}, {@link Capabilities.Item}).
 * <p>
 * PORT 1.21.10 (transfer step 1): the old {@code Capabilities.FluidHandler}/{@code ItemHandler} objects were removed and NeoForge
 * ships no legacy-to-new adapter, so handlers registered as capabilities are native {@link ResourceHandler}s. This class offers two
 * families of lookups:
 * <ul>
 *   <li><strong>Native</strong> ({@code get*ResourceHandler}): return the capability as is. Use them for new code, and always inside an
 *   open transaction, passing that transaction to {@code insert}/{@code extract}.</li>
 *   <li><strong>Legacy</strong> ({@code getLegacy*}, {@code asLegacy*}, all {@link Deprecated}): wrap the capability in NeoForge's new-to-legacy
 *   adapters ({@link IFluidHandler#of(ResourceHandler)}, {@link IItemHandler#of(ResourceHandler)}, {@link FluidResourceHandlerItemAdapter}) so
 *   code on the deprecated interfaces keeps working. Mantle itself no longer uses them (M5 phase 6a); they stay for the Tinkers code that is
 *   still on the legacy interfaces and are removed once it is ported (phase 6b).
 *   <strong>They must not be used inside an open transaction:</strong> every {@code fill}/{@code drain}/{@code insertItem}/{@code extractItem}
 *   of the adapters opens a root transaction, which throws if a transaction is already open on the thread. The legacy lookups throw an
 *   {@link IllegalStateException} up front when called inside one.</li>
 * </ul>
 */
@SuppressWarnings("removal")  // the legacy interfaces are the point of the legacy lookups until phase 6b
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
   * Checks if a transaction is open on this thread, in which case the legacy lookups below must not be used.
   * <p>
   * Returns false while a transaction is {@linkplain Transaction.Lifecycle#CLOSING closing} (inside a journal's revert or release callback),
   * but no transaction can be opened then: {@link Transaction#openRoot()} throws, and so do {@link #openNestedOrRoot()}, the legacy lookups below
   * and the legacy adapters. Code reachable from journal callbacks must only restore its own state, never query handlers. During
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


  /* Legacy bridges */

  /** Throws if a transaction is open or closing on this thread, as the legacy adapters open root transactions */
  private static void checkNoOpenTransaction(String method) {
    checkNotClosing(method);
    if (Transaction.getLifecycle() == Transaction.Lifecycle.OPEN) {
      throw new IllegalStateException("TransferHelper." + method + " returns a legacy handler, whose fill/drain/insert/extract open a root transaction, "
                                      + "but a transaction is already open on this thread. Use the native lookup (get*ResourceHandler) with the open transaction instead.");
    }
  }

  /**
   * Null-safe {@link IFluidHandler#of(ResourceHandler)}.
   * <strong>Must not be called inside an open transaction</strong>, see the class javadoc.
   */
  @Deprecated  // legacy bridge, removed with phase 6b
  @Nullable
  public static IFluidHandler asLegacyFluidHandler(@Nullable ResourceHandler<FluidResource> handler) {
    checkNoOpenTransaction("asLegacyFluidHandler");
    return handler != null ? IFluidHandler.of(handler) : null;
  }

  /**
   * Null-safe {@link IItemHandler#of(ResourceHandler)}.
   * <strong>Must not be called inside an open transaction</strong>, see the class javadoc.
   */
  @Deprecated  // legacy bridge, removed with phase 6b
  @Nullable
  public static IItemHandler asLegacyItemHandler(@Nullable ResourceHandler<ItemResource> handler) {
    checkNoOpenTransaction("asLegacyItemHandler");
    return handler != null ? IItemHandler.of(handler) : null;
  }


  /* Legacy block lookups */

  /**
   * Gets the fluid handler at the given position bridged to a legacy handler, or null if the block exposes none.
   * <strong>Must not be called inside an open transaction</strong>, use {@link #getFluidResourceHandler(Level, BlockPos, Direction)} there.
   */
  @Deprecated  // legacy bridge, removed with phase 6b
  @Nullable
  public static IFluidHandler getLegacyFluidHandler(Level level, BlockPos pos, @Nullable Direction side) {
    checkNoOpenTransaction("getLegacyFluidHandler");
    return asLegacyFluidHandler(getFluidResourceHandler(level, pos, side));
  }

  /**
   * Gets the fluid handler at the given position bridged to a legacy handler, or null if the block exposes none.
   * Pass the state and block entity when already known, or when the level no longer holds them (e.g. during removal).
   * <strong>Must not be called inside an open transaction</strong>, use {@link #getFluidResourceHandler(Level, BlockPos, BlockState, BlockEntity, Direction)} there.
   */
  @Deprecated  // legacy bridge, removed with phase 6b
  @Nullable
  public static IFluidHandler getLegacyFluidHandler(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
    checkNoOpenTransaction("getLegacyFluidHandler");
    return asLegacyFluidHandler(getFluidResourceHandler(level, pos, state, blockEntity, side));
  }

  /**
   * Gets the item handler at the given position bridged to a legacy handler, or null if the block exposes none.
   * Note the adapter's {@code getStackInSlot} returns copies, so changing the returned stacks does not change the block.
   * <strong>Must not be called inside an open transaction</strong>, use {@link #getItemResourceHandler(Level, BlockPos, Direction)} there.
   */
  @Deprecated  // legacy bridge, removed with phase 6b
  @Nullable
  public static IItemHandler getLegacyItemHandler(Level level, BlockPos pos, @Nullable Direction side) {
    checkNoOpenTransaction("getLegacyItemHandler");
    return asLegacyItemHandler(getItemResourceHandler(level, pos, side));
  }

  /**
   * Gets the item handler at the given position bridged to a legacy handler, or null if the block exposes none.
   * Pass the state and block entity when already known, or when the level no longer holds them (e.g. during removal).
   * Note the adapter's {@code getStackInSlot} returns copies, so changing the returned stacks does not change the block.
   * <strong>Must not be called inside an open transaction</strong>, use {@link #getItemResourceHandler(Level, BlockPos, BlockState, BlockEntity, Direction)} there.
   */
  @Deprecated  // legacy bridge, removed with phase 6b
  @Nullable
  public static IItemHandler getLegacyItemHandler(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
    checkNoOpenTransaction("getLegacyItemHandler");
    return asLegacyItemHandler(getItemResourceHandler(level, pos, state, blockEntity, side));
  }


  /* Legacy item lookups */

  /**
   * Gets the fluid handler of a <strong>single container</strong> from the stack, bridged to a legacy item handler, like the removed
   * legacy item capability on a single item. This is what most callers want.
   * <p>
   * The handler works on a one-item copy of the stack: the passed stack is <strong>never modified</strong>, and its count is ignored.
   * Amounts are those of one container, and {@link IFluidHandlerItem#getContainer()} returns the single resulting container after a
   * fill or drain, which the caller is responsible for giving back (e.g. {@link net.minecraft.world.item.ItemUtils#createFilledResult}).
   * <strong>Must not be called inside an open transaction</strong>, use {@link #getFluidResourceHandler(ItemAccess)} there.
   * @param stack  Stack to look up, never modified
   * @return  Legacy handler, or null if the stack is empty or has no fluid capability
   * @see #getLegacyFluidHandlerForWholeStack(ItemStack)
   */
  @Deprecated  // legacy bridge, removed with phase 6b
  @Nullable
  public static IFluidHandlerItem getLegacyFluidHandler(ItemStack stack) {
    return getLegacyFluidHandler(stack, TransferHelper::getFluidResourceHandler);
  }

  /**
   * Same as {@link #getLegacyFluidHandler(ItemStack)}, but with a custom lookup of the native handler from the item access.
   * Useful to bridge a known handler (e.g. a handler constructor) without going through the capability.
   * @param stack   Stack to look up, never modified; only a single item is used
   * @param lookup  Native handler lookup, may return null
   * @return  Legacy handler, or null if the stack is empty or the lookup found no handler
   */
  @Deprecated  // legacy bridge, removed with phase 6b
  @Nullable
  public static IFluidHandlerItem getLegacyFluidHandler(ItemStack stack, Function<ItemAccess, ResourceHandler<FluidResource>> lookup) {
    checkNoOpenTransaction("getLegacyFluidHandler");
    return stack.isEmpty() ? null : bridgeStack(stack.copyWithCount(1), lookup);
  }

  /**
   * Gets the fluid handler of the <strong>whole stack</strong>, bridged to a legacy item handler. For the rare callers that want to
   * work on all items at once, such as filling every item of a loot stack.
   * <p>
   * The handler works on a copy of the whole stack, the passed stack is <strong>never modified</strong>. Amounts and capacities are
   * <strong>totals over all items</strong> of the stack (as with any {@link ItemAccess} based handler): a fill is split between
   * the items, so fill {@code amount * count} to give each item {@code amount}. {@link IFluidHandlerItem#getContainer()} returns the
   * resulting stack.
   * <strong>Must not be called inside an open transaction</strong>, use {@link #getFluidResourceHandler(ItemAccess)} there.
   * @param stack  Stack to look up, never modified
   * @return  Legacy handler, or null if the stack is empty or has no fluid capability
   * @see #getLegacyFluidHandler(ItemStack)
   */
  @Deprecated  // legacy bridge, removed with phase 6b
  @Nullable
  public static IFluidHandlerItem getLegacyFluidHandlerForWholeStack(ItemStack stack) {
    return getLegacyFluidHandlerForWholeStack(stack, TransferHelper::getFluidResourceHandler);
  }

  /**
   * Same as {@link #getLegacyFluidHandlerForWholeStack(ItemStack)}, but with a custom lookup of the native handler from the item access.
   * @param stack   Stack to look up, never modified
   * @param lookup  Native handler lookup, may return null
   * @return  Legacy handler, or null if the stack is empty or the lookup found no handler
   */
  @Deprecated  // legacy bridge, removed with phase 6b
  @Nullable
  public static IFluidHandlerItem getLegacyFluidHandlerForWholeStack(ItemStack stack, Function<ItemAccess, ResourceHandler<FluidResource>> lookup) {
    checkNoOpenTransaction("getLegacyFluidHandlerForWholeStack");
    return stack.isEmpty() ? null : bridgeStack(stack.copy(), lookup);
  }

  /** Puts the copy in a single slot and bridges the handler the lookup finds on it */
  @Nullable
  private static IFluidHandlerItem bridgeStack(ItemStack copy, Function<ItemAccess, ResourceHandler<FluidResource>> lookup) {
    ItemAccess access = access(isolate(copy, copy.getCount()));
    ResourceHandler<FluidResource> handler = lookup.apply(access);
    return handler != null ? new FluidResourceHandlerItemAdapter(handler, access) : null;
  }
}
