package slimeknights.mantle.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import slimeknights.mantle.util.BlockEntityHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BaseContainerMenu<TILE extends BlockEntity> extends AbstractContainerMenu {

  public static double MAX_DISTANCE = 64;
  public static int BASE_Y_OFFSET = 84;

  @Nullable
  protected final TILE tile;

  @Nullable
  protected final Inventory inv;

  protected BaseContainerMenu(MenuType<?> type, int id, @Nullable Inventory inv, @Nullable TILE tile) {
    super(type, id);
    this.inv = inv;
    this.tile = tile;
  }

  @Nullable
  public TILE getTile() {
    return this.tile;
  }

  public void syncOnOpen(ServerPlayer playerOpened) {
    // find another player that already has the gui for this tile open
    ServerLevel server = playerOpened.level();

    for (Player player : server.players()) {
      if (player == playerOpened) {
        continue;
      }

      if (player.containerMenu instanceof BaseContainerMenu<?> baseMenu) {
        if (this.sameGui(baseMenu)) {
          this.syncWithOtherContainer(baseMenu, playerOpened);
          return;
        }
      }
    }

    // no player has a container open for the tile
    this.syncNewContainer(playerOpened);
  }

  /**
   * Called when the container is opened and another player already has a container for this tile open
   * Sync to the same state here.
   */
  protected void syncWithOtherContainer(BaseContainerMenu<?> otherContainer, ServerPlayer player) {
  }

  /**
   * Called when the container is opened and no other player has it open.
   * Set the default state here.
   */
  protected void syncNewContainer(ServerPlayer player) {
  }

  public boolean sameGui(BaseContainerMenu<?> otherContainer) {
    if (this.tile == null) {
      return false;
    }

    return this.tile == otherContainer.tile;
  }

  @Override
  public boolean stillValid(Player playerIn) {
    if (this.tile == null) {
      return true;
    }

    if (!tile.isRemoved()) {
      //prevent Containers from remaining valid after the chunk has unloaded;
      Level world = tile.getLevel();

      if (world == null) {
        return false;
      }

      return world.isLoaded(tile.getBlockPos());
    }

    return false;
  }

  @Override
  public NonNullList<ItemStack> getItems() {
    return super.getItems();
  }

  /*
   * Call this to add the player's inventory to the GUI.
   */
  protected void addInventorySlots() {
    if (this.inv != null) {
      this.addInventorySlots(this.inv);
    }
  }

  protected int playerInventoryStart = -1;

  /*
   * Override this to set the X offset for the inventory slots.
   */
  protected int getInventoryXOffset() {
    return 8;
  }

  /*
  * Override this to set the Y offset for the inventory slots.
   */
  protected int getInventoryYOffset() {
    return BASE_Y_OFFSET;
  }

  protected void addInventorySlots(Inventory inv) {
    int yOffset = this.getInventoryYOffset();
    int xOffset = this.getInventoryXOffset();

    int start = this.slots.size();

    for (int slotY = 0; slotY < 3; slotY++) {
      for (int slotX = 0; slotX < 9; slotX++) {
        addSlot(new Slot(inv, slotX + slotY * 9 + 9, xOffset + slotX * 18, yOffset + slotY * 18));
      }
    }

    yOffset += 58;
    for (int slotY = 0; slotY < 9; slotY++) {
      addSlot(new Slot(inv, slotY, xOffset + slotY * 18, yOffset));
    }

    this.playerInventoryStart = start;
  }

  @Override
  protected Slot addSlot(Slot slotIn) {
    if (this.playerInventoryStart >= 0) {
      throw new IllegalStateException("BaseContainer: Player inventory has to be last slots. Add all slots before adding the player inventory.");
    }
    return super.addSlot(slotIn);
  }

  @Override
  public ItemStack quickMoveStack(Player playerIn, int index) {
    // we can only support inventory <-> playerInventory
    if (this.playerInventoryStart < 0) {
      // so we don't do anything if no player inventory is present because we don't know what to do
      return ItemStack.EMPTY;
    }

    ItemStack itemstack = ItemStack.EMPTY;
    Slot slot = this.slots.get(index);

    // slot that was clicked on not empty?
    if (slot != null && slot.hasItem()) {
      ItemStack itemstack1 = slot.getItem();
      itemstack = itemstack1.copy();
      int end = this.slots.size();

      // Is it a slot in the main inventory? (aka not player inventory)
      if (index < this.playerInventoryStart) {
        // try to put it into the player inventory (if we have a player inventory)
        if (!this.moveItemStackTo(itemstack1, this.playerInventoryStart, end, true)) {
          return ItemStack.EMPTY;
        }
      }
      // Slot is in the player inventory (if it exists), transfer to main inventory
      else if (!this.moveItemStackTo(itemstack1, 0, this.playerInventoryStart, false)) {
        return ItemStack.EMPTY;
      }

      if (itemstack1.isEmpty()) {
        slot.set(ItemStack.EMPTY);
      } else {
        slot.setChanged();
      }
    }

    return itemstack;
  }

  // Fix for a vanilla bug: doesn't take Slot.getMaxStackSize into account
  @Override
  protected boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean useEndIndex) {
    boolean ret = this.mergeItemStackRefill(stack, startIndex, endIndex, useEndIndex);
    if (!stack.isEmpty() && stack.getCount() > 0) {
      ret |= this.mergeItemStackMove(stack, startIndex, endIndex, useEndIndex);
    }
    return ret;
  }

  // only refills items that are already present
  protected boolean mergeItemStackRefill(ItemStack stack, int startIndex, int endIndex, boolean useEndIndex) {
    if (stack.getCount() <= 0) {
      return false;
    }

    boolean flag1 = false;
    int k = startIndex;

    if (useEndIndex) {
      k = endIndex - 1;
    }

    Slot slot;
    ItemStack itemstack1;

    if (stack.isStackable()) {
      while (stack.getCount() > 0 && (!useEndIndex && k < endIndex || useEndIndex && k >= startIndex)) {
        slot = this.slots.get(k);
        itemstack1 = slot.getItem();

        // respect mayPlace like the empty slot pass: a slot may hold an item it does not accept more of (e.g. a result slot, or a
        // native handler refusing inserts, where the source would shrink for a set the slot refuses)
        if (!itemstack1.isEmpty() && ItemStack.isSameItemSameComponents(stack, itemstack1) && slot.mayPlace(stack) && this.canTakeItemForPickAll(stack, slot)) {
          int before = itemstack1.getCount();
          int limit = Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack));
          int target = Math.min(before + stack.getCount(), limit);

          if (target > before) {
            ItemStack old = itemstack1.copy();
            List<ItemStack> others = copyOtherSlots(slot);
            itemstack1.setCount(target);
            slot.setChanged();
            // take from the source only what the slot took: a slot may refuse the change (e.g. one over a native handler)
            int accepted = acceptedBySlot(slot, stack, old, target, others);
            if (accepted > 0) {
              stack.shrink(accepted);
              flag1 = true;
            }
          }
        }

        if (useEndIndex) {
          --k;
        } else {
          ++k;
        }
      }
    }

    return flag1;
  }

  // only moves items into empty slots
  protected boolean mergeItemStackMove(ItemStack stack, int startIndex, int endIndex, boolean useEndIndex) {
    if (stack.getCount() <= 0) {
      return false;
    }

    boolean flag1 = false;
    int k;

    if (useEndIndex) {
      k = endIndex - 1;
    } else {
      k = startIndex;
    }

    while (!useEndIndex && k < endIndex || useEndIndex && k >= startIndex) {
      Slot slot = this.slots.get(k);
      ItemStack itemstack1 = slot.getItem();

      // Forge: Make sure to respect isItemValid in the slot.
      if (itemstack1.isEmpty() && slot.mayPlace(stack) && this.canTakeItemForPickAll(stack, slot)) {
        int count = Math.min(stack.getCount(), slot.getMaxStackSize(stack));
        if (count > 0) {
          List<ItemStack> others = copyOtherSlots(slot);
          slot.set(stack.copyWithCount(count));
          // take from the source only what the slot took: a slot may refuse the set (e.g. one over a native handler). Measured before
          // notifying: a slot working on copies (NeoForge's StackCopySlot) writes back on setChanged the stack it was last set to, which
          // a container that consumed or moved it in setItem no longer holds (a casting tank processing its input into its output), so
          // writing it back would credit the item twice; reading the slot first refreshes that copy to the real contents
          int accepted = acceptedBySlot(slot, stack, ItemStack.EMPTY, count, others);
          slot.setChanged();
          if (accepted > 0) {
            stack.shrink(accepted);
            flag1 = true;
          }
        }

        if (stack.isEmpty()) {
          break;
        }
      }

      if (useEndIndex) {
        --k;
      } else {
        ++k;
      }
    }

    return flag1;
  }

  /**
   * Copies the contents of every slot of this menu except the given one, for {@link #acceptedBySlot}. The given slot is not read: on a
   * slot working on copies (NeoForge's StackCopySlot), reading it would replace the copy the refill merge changes and writes back.
   */
  private List<ItemStack> copyOtherSlots(Slot slot) {
    List<ItemStack> copies = new ArrayList<>(this.slots.size());
    for (Slot other : this.slots) {
      copies.add(other == slot ? ItemStack.EMPTY : other.getItem().copy());
    }
    return copies;
  }

  /**
   * Measures how many items a merge into the slot took. The merges above change the slot before shrinking the source, so a slot refusing
   * or capping the change (a menu slot over a native handler, which can reject a set) never makes the source lose items, while a container
   * consuming or transforming what it is given in {@code setItem} (a casting tank processing its input into its output slot, leaving the
   * input as empty as before) still takes them:
   * <ul>
   *   <li>another slot of the menu changed: the container took the items, all of them are taken;</li>
   *   <li>the slot still holds the merged item: the increase is taken (capped or refused when it is less than requested);</li>
   *   <li>the slot is unchanged: refused, nothing is taken;</li>
   *   <li>the slot holds something else: the container transformed the items, all of them are taken.</li>
   * </ul>
   * @param slot    Slot merged into
   * @param stack   Source stack, of the item merged
   * @param before  Copy of what the slot held before the merge, empty or the merged item
   * @param target  Amount the slot was set to
   * @param others  Contents of the other slots before the merge, from {@link #copyOtherSlots(Slot)}
   * @return  Items to take from the source, between 0 and {@code target - before}
   */
  private int acceptedBySlot(Slot slot, ItemStack stack, ItemStack before, int target, List<ItemStack> others) {
    ItemStack after = slot.getItem();
    int requested = target - before.getCount();
    for (int i = 0; i < this.slots.size(); i++) {
      Slot other = this.slots.get(i);
      if (other != slot && !ItemStack.matches(other.getItem(), others.get(i))) {
        return requested;
      }
    }
    if (ItemStack.isSameItemSameComponents(after, stack)) {
      return Math.max(0, Math.min(after.getCount(), target) - before.getCount());
    }
    return ItemStack.matches(after, before) ? 0 : requested;
  }

  /**
   * Gets a tile entity from a packet buffer
   * @param buf     Packet buffer instance
   * @param type    Tile entity class
   * @param <TILE>  Tile entity type
   * @return Tile entity, or null if unable to find
   */
  @Nullable
  public static <TILE extends BlockEntity> TILE getTileEntityFromBuf(@Nullable FriendlyByteBuf buf, Class<TILE> type) {
    if (buf == null) {
      return null;
    }
    if (FMLEnvironment.getDist() == Dist.CLIENT) {
      return ClientOnly.getTileEntityFromBuf(buf, type);
    }
    return null;
  }

  /** This class is only loaded on the client, so is safe to reference client only methods */
  private static class ClientOnly {
    @Nullable
    public static <TILE extends BlockEntity> TILE getTileEntityFromBuf(FriendlyByteBuf buf, Class<TILE> type) {
      return BlockEntityHelper.get(type, Minecraft.getInstance().level, buf.readBlockPos()).orElse(null);
    }
  }
}
