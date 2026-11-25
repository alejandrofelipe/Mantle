package slimeknights.mantle.inventory;

import com.mojang.logging.LogUtils;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.slf4j.Logger;
import slimeknights.mantle.block.entity.MantleBlockEntity;

import java.util.Objects;

/**
 * Item handler containing exactly one item.
 * <p>
 * PORT 1.21.10: native {@link ResourceHandler} (was the legacy {@code IItemHandlerModifiable}). Changes made through a transaction are
 * silent ({@link #setStackSilently(ItemStack)}) until the root transaction commits, then the final stack is replayed once through
 * {@link #setStack(ItemStack)}, so subclasses keep their side effects (syncing, block updates) in {@link #setStack(ItemStack)} and simulations
 * never reach them. The capacity is the slot limit capped to the item's max stack size. {@link #writeToNBT(HolderLookup.Provider)} keeps the
 * legacy shape: the bare {@link ItemStack#CODEC} compound, {@code {}} when empty.
 */
public abstract class SingleItemHandler<T extends MantleBlockEntity> implements ResourceHandler<ItemResource> {
  private static final Logger LOGGER = LogUtils.getLogger();

  protected final T parent;
  private final int maxStackSize;
  /** Journal for transactions over {@link #stack} */
  private final Journal journal = new Journal();

  /** Current item in this slot */
  @Getter
  private ItemStack stack = ItemStack.EMPTY;

  protected SingleItemHandler(T parent, int maxStackSize) {
    this.parent = parent;
    this.maxStackSize = maxStackSize;
  }

  /**
   * Sets the stack in this slot as a normal change, running side effects. Overrides add their side effects and call super.
   * Called directly for changes outside transactions, and once with the final stack when a root transaction commits.
   * @param newStack  New stack
   */
  public void setStack(ItemStack newStack) {
    this.stack = newStack;
    parent.setChangedFast();
  }

  /**
   * Sets the stack without side effects, used inside transactions and to revert them. Overrides may only clear caches.
   * @param newStack  New stack
   */
  protected void setStackSilently(ItemStack newStack) {
    this.stack = newStack;
  }

  /**
   * Checks if the given stack is valid for this slot
   * @param stack  Stack
   * @return  True if valid
   */
  protected abstract boolean isItemValid(ItemStack stack);

  /**
   * Checks if the given resource is valid for this slot, called by native inserts (inside transactions, so no side effects).
   * @param resource  Resource, never empty
   * @return  True if valid
   */
  protected boolean isItemValid(ItemResource resource) {
    return isItemValid(resource.toStack());
  }

  /** Gets the maximum stack size of this slot, before capping to the item's max stack size */
  public int getSlotLimit() {
    return maxStackSize;
  }


  /* Native handler */

  @Override
  public int size() {
    return 1;
  }

  @Override
  public ItemResource getResource(int index) {
    return journal.getResource(index);
  }

  @Override
  public long getAmountAsLong(int index) {
    return journal.getAmountAsLong(index);
  }

  @Override
  public long getCapacityAsLong(int index, ItemResource resource) {
    return journal.getCapacityAsLong(index, resource);
  }

  @Override
  public boolean isValid(int index, ItemResource resource) {
    return journal.isValid(index, resource);
  }

  @Override
  public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
    return journal.insert(index, resource, amount, transaction);
  }

  @Override
  public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
    return journal.extract(index, resource, amount, transaction);
  }

  @Override
  public String toString() {
    return getClass().getName() + "[" + stack + "]";
  }


  /* NBT */

  /**
   * Writes this module to NBT
   * @param registries  Registry access for serializing the stack
   * @return  Module in NBT
   */
  public CompoundTag writeToNBT(HolderLookup.Provider registries) {
    if (!stack.isEmpty()) {
      // 1.21.8: ItemStack.save was removed, encode with the codec through a registry-aware NBT context (same tag shape)
      Tag tag = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow(IllegalStateException::new);
      if (tag instanceof CompoundTag compound) {
        return compound;
      }
    }
    return new CompoundTag();
  }

  /**
   * Reads this module from NBT
   * @param registries  Registry access for deserializing the stack
   * @param nbt  NBT
   */
  public void readFromNBT(HolderLookup.Provider registries, CompoundTag nbt) {
    // an empty tag is the saved form of an empty stack
    stack = nbt.isEmpty() ? ItemStack.EMPTY : ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), nbt).resultOrPartial(err -> LOGGER.error("Failed to read item stack for {}: {}", parent, err)).orElse(ItemStack.EMPTY);
  }

  /** Snapshot journal of the stack, silent until the root commit */
  private class Journal extends ItemStackResourceHandler {
    @Override
    protected ItemStack getStack() {
      return stack;
    }

    @Override
    protected void setStack(ItemStack newStack) {
      setStackSilently(newStack);
    }

    @Override
    protected boolean isValid(ItemResource resource) {
      return isItemValid(resource);
    }

    @Override
    protected int getCapacity(ItemResource resource) {
      return resource.isEmpty() ? maxStackSize : Math.min(maxStackSize, resource.getMaxStackSize());
    }

    @Override
    protected void onRootCommit(ItemStack original) {
      // replay the final stack over the original, so the owner sees a single normal change
      ItemStack current = Objects.requireNonNull(stack);
      setStackSilently(original);
      SingleItemHandler.this.setStack(current);
    }
  }
}
