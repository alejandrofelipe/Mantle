package slimeknights.mantle.transfer;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Native single fluid tank, the replacement for NeoForge's deprecated {@code fluids.capability.templates.FluidTank}.
 * <p>
 * The getters and setters match the legacy tank ({@link #getFluid()}, {@link #setFluid(FluidStack)}, {@link #getCapacity()}, {@link #isFluidValid(FluidStack)}…),
 * and {@link #serialize(ValueOutput)}/{@link #deserialize(ValueInput)} write the same data: the fluid under {@code Fluid} through
 * {@link FluidStack#CODEC}, nothing when empty. Native inserts and extracts change the fluid silently inside the transaction, journaled, and
 * {@link #onContentsChanged()} runs once the root transaction commits with a different fluid, so a simulation (an aborted transaction) never
 * notifies the owner. Like the legacy tank, {@link #setFluid(FluidStack)} is a plain setter and does not notify.
 */
public class SingleFluidTank implements ResourceHandler<FluidResource> {
  /** Key of the fluid in the saved data, same as the legacy tank */
  public static final String TAG_FLUID = "Fluid";

  /** Fluids accepted by this tank */
  protected Predicate<FluidStack> validator;
  /** Current fluid, never null */
  protected FluidStack fluid = FluidStack.EMPTY;
  /** Maximum amount of fluid */
  protected int capacity;

  /** Journal of {@link #fluid} for native transfers */
  private final SnapshotJournal<FluidStack> journal = new SnapshotJournal<>() {
    @Override
    protected FluidStack createSnapshot() {
      return fluid.copy();
    }

    @Override
    protected void revertToSnapshot(FluidStack snapshot) {
      fluid = snapshot;
    }

    @Override
    protected void onRootCommit(FluidStack original) {
      SingleFluidTank.this.onRootCommit(original);
    }
  };

  public SingleFluidTank(int capacity) {
    this(capacity, fluid -> true);
  }

  public SingleFluidTank(int capacity, Predicate<FluidStack> validator) {
    this.capacity = capacity;
    this.validator = validator;
  }


  /* Properties */

  /** Sets the capacity of this tank. Does not change the contained fluid. */
  public SingleFluidTank setCapacity(int capacity) {
    this.capacity = capacity;
    return this;
  }

  /** Sets the fluids accepted by this tank, ignored if null */
  public SingleFluidTank setValidator(Predicate<FluidStack> validator) {
    if (validator != null) {
      this.validator = validator;
    }
    return this;
  }

  /** Checks if the fluid is accepted by this tank */
  public boolean isFluidValid(FluidStack stack) {
    return validator.test(stack);
  }

  /** Gets the capacity of this tank */
  public int getCapacity() {
    return capacity;
  }

  /** Gets the contained fluid. Do not modify it, use {@link #setFluid(FluidStack)}. */
  public FluidStack getFluid() {
    return fluid;
  }

  /** Gets the amount of contained fluid */
  public int getFluidAmount() {
    return fluid.getAmount();
  }

  /** Sets the contained fluid without notifying the owner, like the legacy tank. Not journaled: do not call inside a transaction that changed this tank. */
  public void setFluid(FluidStack stack) {
    this.fluid = stack;
  }

  /** Checks if the tank is empty */
  public boolean isEmpty() {
    return fluid.isEmpty();
  }

  /** Gets the remaining space in the tank */
  public int getSpace() {
    return Math.max(0, capacity - fluid.getAmount());
  }


  /* Change hooks */

  /**
   * Called once the root transaction commits after a native change to this tank.
   * By default calls {@link #onContentsChanged()} if the fluid differs from the one before the transaction.
   * @param original  Fluid before the transaction
   */
  protected void onRootCommit(FluidStack original) {
    if (!FluidStack.matches(original, fluid)) {
      onContentsChanged();
    }
  }

  /** Called when the contents changed through a committed native transfer */
  protected void onContentsChanged() {}

  /** Records the current state in the journal of the given transaction, call before changing the fluid inside a transaction */
  protected void updateSnapshots(TransactionContext transaction) {
    journal.updateSnapshots(transaction);
  }


  /* Native transfer */

  @Override
  public int size() {
    return 1;
  }

  @Override
  public FluidResource getResource(int index) {
    Objects.checkIndex(index, 1);
    return FluidResource.of(fluid);
  }

  @Override
  public long getAmountAsLong(int index) {
    Objects.checkIndex(index, 1);
    return fluid.getAmount();
  }

  @Override
  public long getCapacityAsLong(int index, FluidResource resource) {
    Objects.checkIndex(index, 1);
    return resource.isEmpty() || isFluidValid(resource.toStack(1)) ? capacity : 0;
  }

  @Override
  public boolean isValid(int index, FluidResource resource) {
    Objects.checkIndex(index, 1);
    return !resource.isEmpty() && isFluidValid(resource.toStack(1));
  }

  @Override
  public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, 1);
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    if (amount == 0 || !isFluidValid(resource.toStack(amount)) || (!fluid.isEmpty() && !resource.matches(fluid))) {
      return 0;
    }
    int inserted = Math.min(amount, capacity - fluid.getAmount());
    if (inserted <= 0) {
      return 0;
    }
    updateSnapshots(transaction);
    fluid = resource.toStack(fluid.getAmount() + inserted);
    return inserted;
  }

  @Override
  public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
    Objects.checkIndex(index, 1);
    TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
    if (amount == 0 || fluid.isEmpty() || !resource.matches(fluid)) {
      return 0;
    }
    int extracted = Math.min(amount, fluid.getAmount());
    updateSnapshots(transaction);
    fluid = extracted == fluid.getAmount() ? FluidStack.EMPTY : fluid.copyWithAmount(fluid.getAmount() - extracted);
    return extracted;
  }


  /* Saving */

  /** Reads the tank written by {@link #serialize(ValueOutput)}, or by the legacy tank. A missing fluid means empty. */
  public void deserialize(ValueInput input) {
    this.fluid = input.read(TAG_FLUID, FluidStack.CODEC).orElse(FluidStack.EMPTY);
  }

  /** Writes the tank like the legacy tank: the fluid under {@value #TAG_FLUID}, nothing when empty */
  public void serialize(ValueOutput output) {
    if (!fluid.isEmpty()) {
      output.store(TAG_FLUID, FluidStack.CODEC, fluid);
    }
  }
}
