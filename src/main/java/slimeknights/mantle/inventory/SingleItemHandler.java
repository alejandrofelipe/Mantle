package slimeknights.mantle.inventory;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import slimeknights.mantle.block.entity.MantleBlockEntity;

import org.slf4j.Logger;

import javax.annotation.Nonnull;

/**
 * Item handler containing exactly one item.
 * @deprecated PORT 1.21.10: implements the legacy {@link IItemHandlerModifiable}, which NeoForge removes. Kept for the Tinkers handlers that
 * extend it until they are ported (M5 phase 6b), where it is replaced by a native single slot handler with the same NBT shape.
 */
@Deprecated
@SuppressWarnings({"unused", "removal"})
@RequiredArgsConstructor
public abstract class SingleItemHandler<T extends MantleBlockEntity> implements IItemHandlerModifiable {
  private static final Logger LOGGER = LogUtils.getLogger();

  protected final T parent;
  private final int maxStackSize;

  /** Current item in this slot */
  @Getter
  private ItemStack stack = ItemStack.EMPTY;

  /**
   * Sets the stack in this duct
   * @param newStack  New stack
   */
  public void setStack(ItemStack newStack) {
    this.stack = newStack;
    parent.setChangedFast();
  }

  /**
   * Checks if the given stack is valid for this slot
   * @param stack  Stack
   * @return  True if valid
   */
  protected abstract boolean isItemValid(ItemStack stack);


  /* Properties */

  @Override
  public boolean isItemValid(int slot, ItemStack stack) {
    return slot == 0 && isItemValid(stack);
  }

  @Override
  public int getSlots() {
    return 1;
  }

  @Override
  public int getSlotLimit(int slot) {
    return maxStackSize;
  }

  @Nonnull
  @Override
  public ItemStack getStackInSlot(int slot) {
    if (slot == 0) {
      return stack;
    }
    return ItemStack.EMPTY;
  }


  /* Interaction */

  @Override
  public void setStackInSlot(int slot, ItemStack stack) {
    if (slot == 0) {
      setStack(stack);
    }
  }
  
  @Nonnull
  @Override
  public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
    if (stack.isEmpty()) {
      return ItemStack.EMPTY;
    }
    if (slot == 0) {
      ItemStack current = getStack();
      if (current.isEmpty()) {
        if (this.isItemValid(slot, stack)) {
          // insert up to the stack limit
          int size = Math.min(stack.getCount(), getSlotLimit(0));
          if (!simulate) {
            this.setStack(stack.copyWithCount(size));
          }
          return stack.copyWithCount(stack.getCount() - size);
        }
      } else if (ItemStack.isSameItemSameComponents(current, stack)) {
        // increase up to the stack limit
        int added = Math.min(stack.getCount(), getSlotLimit(0) - current.getCount());
        if (added > 0) {
          if (!simulate) {
            current.grow(added);
            setStack(current);
          }
          return stack.copyWithCount(stack.getCount() - added);
        }
      }
    }
    return stack;
  }

  @Nonnull
  @Override
  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    if (amount == 0 || slot != 0) {
      return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) {
      return ItemStack.EMPTY;
    }

    // if amount is less than our size, need to do some shrinking
    if (amount < stack.getCount()) {
      ItemStack result = stack.copyWithCount(amount);
      if (!simulate) {
        setStack(stack.copyWithCount(stack.getCount() - amount));
      }
      return result;
    }
    // equal to or bigger means we give them our stack directly
    if (simulate) {
      return stack.copy();
    } else {
      ItemStack ret = stack;
      setStack(ItemStack.EMPTY);
      return ret;
    }
  }

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
}
