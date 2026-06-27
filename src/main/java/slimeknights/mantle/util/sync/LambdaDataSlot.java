package slimeknights.mantle.util.sync;

import net.minecraft.world.inventory.DataSlot;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Data slot implementation using lambdas for the getter and setter
 */
public class LambdaDataSlot extends DataSlot {
  private final IntSupplier getter;
  private final IntConsumer setter;
  // DataSlot#prevValue is private in 1.21.1 with no setter, so we track our own previous value to support a custom
  // starting value (used to control when the first checkAndClearUpdateFlag reports a change).
  private int lastKnownValue;

  public LambdaDataSlot(IntSupplier getter, IntConsumer setter) {
    this(0, getter, setter);
  }

  /** Constructor to let you start from a value other than 0 */
  public LambdaDataSlot(int startingValue, IntSupplier getter, IntConsumer setter) {
    this.getter = getter;
    this.setter = setter;
    this.lastKnownValue = startingValue;
  }

  @Override
  public int get() {
    return getter.getAsInt();
  }

  @Override
  public void set(int value) {
    setter.accept(value);
  }

  @Override
  public boolean checkAndClearUpdateFlag() {
    int value = this.get();
    boolean changed = value != this.lastKnownValue;
    this.lastKnownValue = value;
    return changed;
  }
}
