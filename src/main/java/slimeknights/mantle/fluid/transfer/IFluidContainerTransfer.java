package slimeknights.mantle.fluid.transfer;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import slimeknights.mantle.data.gson.GenericRegisteredSerializer.IJsonSerializable;
import slimeknights.mantle.fluid.FluidTransferHelper;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/** Interface for transferring fluid either to or from an item */
public interface IFluidContainerTransfer extends IJsonSerializable {
  /** Adds any items matched by this recipe for the sake of enabling transfer client side */
  void addRepresentativeItems(Consumer<Item> consumer);

  /**
   * Checks if this recipe uniquely matches the given item
   * @param stack  Stack to match
   * @param fluid  Current fluid the handler allows draining. Does not mean the handler may not accept other fluids
   *               On client side, this will always be empty. Return true if this stack
   * @return  True if this handler can transfer
   */
  boolean matches(ItemStack stack, FluidStack fluid);

  /**
   * Performs the actual transfer into or out of the handler, as part of the given transaction.
   * <p>
   * The changes to the handler only apply once the caller commits the transaction. When this returns null, the transfer must have left
   * no change in the transaction (implementations open a nested transaction and only commit it on success).
   * @param stack        Stack to transfer, not modified
   * @param fluid        Current fluid the handler allows draining. Does not mean the handler may not accept other fluids
   * @param handler      Handler either receiving or giving fluid
   * @param direction    Determines whether to try and fill or empty the container
   * @param transaction  Transaction for the changes to the handler
   * @return  container after the transfer and the fluid transferred, null if the transfer failed
   */
  @Nullable
  TransferResult transfer(ItemStack stack, FluidStack fluid, ResourceHandler<FluidResource> handler, TransferDirection direction, TransactionContext transaction);

  /**
   * Result after transferring a fluid
   * @param stack    Item stack result, may be modified
   * @param fluid    Fluid, generally should not be modified
   * @param didFill  If true, the item stack was filled. If false, it was drained
   */
  record TransferResult(ItemStack stack, FluidStack fluid, boolean didFill) {
    /** Gets the sound for this result */
    public SoundEvent getSound() {
      return didFill ? FluidTransferHelper.getFillSound(fluid) : FluidTransferHelper.getEmptySound(fluid);
    }
  }

  /** Represents the direction to allow transfer */
  enum TransferDirection {
    /** Attempts to empty the item. If that fails, attempts to fill the item. */
    AUTO,
    /** Empties the item into the tank */
    EMPTY_ITEM,
    /** Fills the item from the tank */
    FILL_ITEM,
    /** Attempts to fill the item. If that fails, attempts to empty the item. */
    REVERSE;

    /** If true, may fill the item */
    public boolean canEmpty() {
      return this != FILL_ITEM;
    }

    /** If true, may empty the item */
    public boolean canFill() {
      return this != EMPTY_ITEM;
    }
  }
}
