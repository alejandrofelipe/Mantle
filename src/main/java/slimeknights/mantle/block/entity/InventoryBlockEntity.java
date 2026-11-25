package slimeknights.mantle.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import slimeknights.mantle.util.ItemStackList;

import javax.annotation.Nullable;
import java.util.Optional;

// Updated version of InventoryLogic in Mantle. Also contains a few bugfixes DOES NOT OVERRIDE createMenu
public abstract class InventoryBlockEntity extends NameableBlockEntity implements Container, MenuProvider, Nameable {
  private static final String TAG_INVENTORY_SIZE = "InventorySize";
  private static final String TAG_ITEMS = "Items";
  private static final String TAG_SLOT = "Slot";

  private NonNullList<ItemStack> inventory;
  /** If true, the inventory size is saved to NBT, false means you are responsible for serializing it if it changes */
  private final boolean saveSizeToNBT;
  protected int stackSizeLimit;
  /**
   * Item handler exposed by {@link #getItemHandler(Direction)}, null to expose NeoForge's {@link VanillaContainerWrapper} of this container.
   * Subclasses may assign their own native handler in the constructor.
   * PORT 1.21.10: was a legacy {@code IItemHandlerModifiable} defaulting to an {@code InvWrapper}; registered handlers are native now.
   */
  @Nullable
  protected ResourceHandler<ItemResource> itemHandler;

  /**
   * @param name Localization String for the inventory title. Can be overridden through setCustomName
   */
  public InventoryBlockEntity(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state, Component name, boolean saveSizeToNBT, int inventorySize) {
    this(tileEntityTypeIn, pos, state, name, saveSizeToNBT, inventorySize, 64);
  }

  /**
   * @param name Localization String for the inventory title. Can be overridden through setCustomName
   */
  public InventoryBlockEntity(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state, Component name, boolean saveSizeToNBT, int inventorySize, int maxStackSize) {
    super(tileEntityTypeIn, pos, state, name);
    this.saveSizeToNBT = saveSizeToNBT;
    this.inventory = NonNullList.withSize(inventorySize, ItemStack.EMPTY);
    this.stackSizeLimit = maxStackSize;
  }

  /**
   * Gets the native item handler of this block entity.
   * @return  {@link #itemHandler} if set, otherwise NeoForge's transactional wrapper of this container
   */
  public ResourceHandler<ItemResource> getItemHandler() {
    // NeoForge caches the wrapper per container, and of() refreshes its size after a resize
    return this.itemHandler != null ? this.itemHandler : VanillaContainerWrapper.of(this);
  }

  /**
   * Gets the item handler capability for this block entity. Registered centrally via {@code RegisterCapabilitiesEvent} on
   * {@link net.neoforged.neoforge.capabilities.Capabilities.Item#BLOCK}.
   * @param side  Side requesting the handler, unused as the same handler is exposed on all sides
   * @return  Item handler, null for no handler on that side
   */
  @Nullable
  public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
    return this.getItemHandler();
  }

  /* Inventory management */

  @Override
  public ItemStack getItem(int slot) {
    if (slot < 0 || slot >= this.inventory.size()) {
      return ItemStack.EMPTY;
    }

    return this.inventory.get(slot);
  }

  public boolean isStackInSlot(int slot) {
    return !this.getItem(slot).isEmpty();
  }

  /**
   * Same as resize, but does not call markDirty. Used on loading from NBT
   */
  private void resizeInternal(int size) {
    // save effort if the size did not change
    if (size == this.inventory.size()) {
      return;
    }
    ItemStackList newInventory = ItemStackList.withSize(size);

    for (int i = 0; i < size && i < this.inventory.size(); i++) {
      newInventory.set(i, this.inventory.get(i));
    }
    this.inventory = newInventory;
  }

  public void resize(int size) {
    this.resizeInternal(size);
    this.setChangedFast();
  }

  @Override
  public int getContainerSize() {
    return this.inventory.size();
  }

  @Override
  public int getMaxStackSize() {
    return this.stackSizeLimit;
  }

  @Override
  public void setItem(int slot, ItemStack itemstack) {
    if (slot < 0 || slot >= this.inventory.size()) {
      return;
    }

    ItemStack current = this.inventory.get(slot);
    this.inventory.set(slot, itemstack);

    if (!itemstack.isEmpty() && itemstack.getCount() > this.getMaxStackSize()) {
      itemstack.setCount(this.getMaxStackSize());
    }
    if (!ItemStack.matches(current, itemstack)) {
      this.setChangedFast();
    }
  }

  @Override
  public ItemStack removeItem(int slot, int quantity) {
    if (quantity <= 0) {
      return ItemStack.EMPTY;
    }
    ItemStack itemStack = this.getItem(slot);

    if (itemStack.isEmpty()) {
      return ItemStack.EMPTY;
    }

    // whole itemstack taken out
    if (itemStack.getCount() <= quantity) {
      this.setItem(slot, ItemStack.EMPTY);
      this.setChangedFast();
      return itemStack;
    }

    // split itemstack
    itemStack = itemStack.split(quantity);
    // slot is empty, set to ItemStack.EMPTY
    // isn't this redundant to the above check?
    if (this.getItem(slot).getCount() == 0) {
      this.setItem(slot, ItemStack.EMPTY);
    }

    this.setChangedFast();
    // return remainder
    return itemStack;
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    ItemStack itemStack = this.getItem(slot);
    this.setItem(slot, ItemStack.EMPTY);
    return itemStack;
  }

  @Override
  public boolean canPlaceItem(int slot, ItemStack itemstack) {
    if (slot < this.getContainerSize()) {
      return this.inventory.get(slot).isEmpty() || itemstack.getCount() + this.inventory.get(slot).getCount() <= this.getMaxStackSize();
    }
    return false;
  }

  @Override
  public void clearContent() {
    for (int i = 0; i < this.inventory.size(); i++) {
      this.inventory.set(i, ItemStack.EMPTY);
    }
  }

  /* Supporting methods */
  @Override
  public boolean stillValid(Player entityplayer) {
    // block changed/got broken?
    if (level == null || this.level.getBlockEntity(this.worldPosition) != this || this.getBlockState().getBlock() == Blocks.AIR) {
      return false;
    }

    return entityplayer.distanceToSqr(this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 0.5D, this.worldPosition.getZ() + 0.5D) <= 64D;
  }

  @Override
  public void startOpen(ContainerUser user) {}

  @Override
  public void stopOpen(ContainerUser user) {}

  /* NBT */

  @Override
  protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    if (saveSizeToNBT) {
      this.resizeInternal(input.getIntOr(TAG_INVENTORY_SIZE, 0));
    }
    this.readInventory(input);
  }

  @Override
  protected void saveSynced(ValueOutput output) {
    super.saveSynced(output);
    // only sync the size to the client by default
    if (saveSizeToNBT) {
      output.putInt(TAG_INVENTORY_SIZE, this.inventory.size());
    }
  }

  @Override
  public void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    this.writeInventory(output);
  }

  /**
   * Writes the contents of the inventory to the output, as a list under {@code Items} with each entry holding the stack fields plus a {@code Slot} byte.
   * Replaces {@code writeInventoryToNBT(CompoundTag, HolderLookup.Provider)}.
   */
  public void writeInventory(ValueOutput output) {
    ValueOutput.TypedOutputList<ItemStackWithSlot> list = output.list(TAG_ITEMS, ItemStackWithSlot.CODEC);
    for (int i = 0; i < this.getContainerSize(); i++) {
      ItemStack stack = this.getItem(i);
      if (!stack.isEmpty()) {
        list.add(new ItemStackWithSlot(i, stack));
      }
    }
  }

  /**
   * Reads an inventory from the input. When the input has an {@code Items} list, it replaces the whole inventory: slots absent from the list
   * are emptied. Without the list the inventory is left unchanged (block entities that sync their items by packet leave it out of the update tag).
   * Replaces {@code readInventoryFromNBT(CompoundTag, HolderLookup.Provider)}.
   */
  public void readInventory(ValueInput input) {
    Optional<ValueInput.TypedInputList<ItemStackWithSlot>> items = input.list(TAG_ITEMS, ItemStackWithSlot.CODEC);
    if (items.isEmpty()) {
      return;
    }
    // the list skips empty slots, so clear them first (a client copy receiving the list in an update tag may still hold old stacks)
    for (int i = 0; i < this.inventory.size(); i++) {
      this.inventory.set(i, ItemStack.EMPTY);
    }
    int limit = this.getMaxStackSize();
    for (ItemStackWithSlot entry : items.get()) {
      if (entry.isValidInContainer(this.inventory.size())) {
        ItemStack stack = entry.stack();
        if (!stack.isEmpty() && stack.getCount() > limit) {
          stack.setCount(limit);
        }
        this.inventory.set(entry.slot(), stack);
      }
    }
  }

  @Override
  public boolean isEmpty() {
    for (ItemStack itemstack : this.inventory) {
      if (!itemstack.isEmpty()) {
        return false;
      }
    }

    return true;
  }
}
