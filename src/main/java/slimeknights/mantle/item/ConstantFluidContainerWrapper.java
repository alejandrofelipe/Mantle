package slimeknights.mantle.item;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;

/**
 * Represents a fluid handler for a container item with a single constant fluid that becomes empty once drained.
 * Containers can only be drained whole, and cannot be filled.
 * <p>
 * Register it centrally on {@code RegisterCapabilitiesEvent} (see {@link ContainerFoodItem.FluidContainerFoodItem}):
 * {@code event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new ConstantFluidContainerWrapper(fluid, access), THE_ITEM)}
 * <p>
 * PORT 1.21.10: ported from the removed {@code IFluidHandlerItem} capability to NeoForge's transfer API. The handler keeps no state
 * of its own: draining exchanges the filled item for its empty container through the {@link ItemAccess}, which takes part in the
 * transaction, so simulating (an aborted transaction) has no effect. Like every {@link ItemAccess} based handler, amounts are totals
 * over all items in the accessed stack.
 */
public class ConstantFluidContainerWrapper implements ResourceHandler<FluidResource> {
  /** Contained fluid resource */
  private final FluidResource fluid;
  /** Amount of fluid in a single container */
  private final int amount;
  /** Access to the container item */
  private final ItemAccess access;
  /** Filled item this handler was created for; any other item in the access means the container was drained */
  private final ItemResource filled;
  /** Empty version of the container, {@link ItemResource#EMPTY} if the container is consumed */
  private final ItemResource emptyContainer;

  /**
   * @param fluid           Fluid in a single container
   * @param access          Access to the filled container item
   * @param emptyContainer  Item left after draining, {@link ItemResource#EMPTY} to consume the container
   */
  public ConstantFluidContainerWrapper(FluidStack fluid, ItemAccess access, ItemResource emptyContainer) {
    this.fluid = FluidResource.of(fluid);
    this.amount = fluid.getAmount();
    this.access = access;
    this.filled = access.getResource();
    this.emptyContainer = emptyContainer;
  }

  /** Creates a handler using the crafting remainder of the item as the empty container */
  public ConstantFluidContainerWrapper(FluidStack fluid, ItemAccess access) {
    this(fluid, access, ItemResource.of(access.getResource().toStack().getCraftingRemainder()));
  }

  /** Number of filled containers currently in the access */
  private int filledContainers() {
    if (amount <= 0 || fluid.isEmpty() || filled.isEmpty() || !filled.equals(access.getResource())) {
      return 0;
    }
    return access.getAmount();
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public FluidResource getResource(int index) {
    Objects.checkIndex(index, size());
    return filledContainers() > 0 ? fluid : FluidResource.EMPTY;
  }

  @Override
  public long getAmountAsLong(int index) {
    Objects.checkIndex(index, size());
    return (long) filledContainers() * amount;
  }

  @Override
  public long getCapacityAsLong(int index, FluidResource resource) {
    Objects.checkIndex(index, size());
    return resource.isEmpty() || resource.equals(fluid) ? (long) access.getAmount() * amount : 0;
  }

  @Override
  public boolean isValid(int index, FluidResource resource) {
    Objects.checkIndex(index, size());
    return resource.equals(fluid);
  }

  @Override
  public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, size());
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    return 0;
  }

  @Override
  public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, size());
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    // cannot drain if: already drained, requested the wrong type, or requested less than a whole container
    int available = filledContainers();
    if (available <= 0 || !resource.equals(fluid)) {
      return 0;
    }
    int containers = Math.min(amount / this.amount, available);
    if (containers <= 0) {
      return 0;
    }
    int drained = emptyContainer.isEmpty()
      ? access.extract(filled, containers, transaction)
      : access.exchange(emptyContainer, containers, transaction);
    return drained * this.amount;
  }

  @Override
  public String toString() {
    return "ConstantFluidContainerWrapper[" + amount + " " + fluid + " in " + access + "]";
  }
}
