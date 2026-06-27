package slimeknights.mantle.data.loadable.common;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.mantle.data.loadable.ErrorFactory;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;

import javax.annotation.Nullable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/** Loadable for a fluid stack */
@SuppressWarnings("unused")  // API
public class FluidStackLoadable {
  private FluidStackLoadable() {}

  /* reused lambdas */
  /** Getter for an item from a stack */
  private static final Function<FluidStack,Fluid> FLUID_GETTER = FluidStack::getFluid;
  /** Checks if a stack can be serialized to a primitive, ignoring count */
  private static final Predicate<FluidStack> COMPACT_COMPONENTS = FluidStack::isComponentsPatchEmpty;
  /** Maps a fluid stack that may be empty to a strictly not empty one */
  private static final BiFunction<FluidStack,ErrorFactory,FluidStack> NOT_EMPTY = (stack, error) -> {
    if (stack.isEmpty()) {
      throw error.create("FluidStack cannot be empty");
    }
    return stack;
  };

  /* fields */
  /** Field for an optional fluid */
  private static final LoadableField<Fluid,FluidStack> FLUID = Loadables.FLUID.defaultField("fluid", Fluids.EMPTY, false, FLUID_GETTER);
  /** Field for fluid stack count that allows empty */
  private static final LoadableField<Integer,FluidStack> AMOUNT = IntLoadable.FROM_ZERO.requiredField("amount", FluidStack::getAmount);
  /** Loadable for the component patch on a fluid stack, replacing the 1.20 NBT compound. */
  private static final Loadable<DataComponentPatch> PATCH_LOADABLE = new CodecLoadable<>(DataComponentPatch.CODEC);
  /** Field for fluid stack components, defaulting to an empty patch */
  private static final LoadableField<DataComponentPatch,FluidStack> PATCH = PATCH_LOADABLE.defaultField("components", DataComponentPatch.EMPTY, false, FluidStack::getComponentsPatch);


  /* Optional */
  /** Single item which may be empty with an amount of 1000 */
  public static final Loadable<FluidStack> OPTIONAL_BUCKET = fixedSize(FluidType.BUCKET_VOLUME);
  /** Loadable for a stack that may be empty with variable count */
  public static final RecordLoadable<FluidStack> OPTIONAL_STACK = RecordLoadable.create(FLUID, AMOUNT, (fluid, count) -> makeStack(fluid, count, DataComponentPatch.EMPTY));
  /** Loadable for a stack that may be empty with components and an amount of 1000 */
  public static final RecordLoadable<FluidStack> OPTIONAL_BUCKET_NBT = fixedSizeComponents(FluidType.BUCKET_VOLUME);
  /** Loadable for a stack that may be empty with variable count and components */
  public static final RecordLoadable<FluidStack> OPTIONAL_STACK_NBT = RecordLoadable.create(FLUID, AMOUNT, PATCH, FluidStackLoadable::makeStack);


  /* Required */
  /** Single item which may not be empty with an amount of 1000 */
  public static final Loadable<FluidStack> REQUIRED_BUCKET = notEmpty(OPTIONAL_BUCKET);
  /** Loadable for a stack that may not be empty with variable count */
  public static final RecordLoadable<FluidStack> REQUIRED_STACK = notEmpty(OPTIONAL_STACK);
  /** Loadable for a stack that may not be empty with components and an amount of 1000 */
  public static final RecordLoadable<FluidStack> REQUIRED_BUCKET_NBT = notEmpty(OPTIONAL_BUCKET_NBT);
  /** Loadable for a stack that may not be empty with variable count and components */
  public static final RecordLoadable<FluidStack> REQUIRED_STACK_NBT = notEmpty(OPTIONAL_STACK_NBT);


  /* Helpers */

  /** Makes a fluid stack from the given parameters */
  private static FluidStack makeStack(Fluid fluid, int amount, @Nullable DataComponentPatch patch) {
    if (fluid == Fluids.EMPTY || amount <= 0) {
      return FluidStack.EMPTY;
    }
    if (patch == null || patch.isEmpty()) {
      return new FluidStack(fluid, amount);
    }
    return new FluidStack(fluid, amount, patch);
  }

  /** Creates a loadable for a stack with a single item */
  public static Loadable<FluidStack> fixedSize(int amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("Count must be positive, received " + amount);
    }
    return Loadables.FLUID.flatXmap(fluid -> makeStack(fluid, amount, DataComponentPatch.EMPTY), FLUID_GETTER);
  }

  /** Creates a loadable for a stack with a single item, parsing components */
  public static RecordLoadable<FluidStack> fixedSizeComponents(int amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("Amount must be positive, received " + amount);
    }
    return RecordLoadable.create(FLUID, PATCH, (fluid, patch) -> makeStack(fluid, amount, patch))
                         .compact(OPTIONAL_BUCKET, COMPACT_COMPONENTS);
  }

  /** Creates a non-empty variant of the loadable */
  public static Loadable<FluidStack> notEmpty(Loadable<FluidStack> loadable) {
    return loadable.validate(NOT_EMPTY);
  }

  /** Creates a non-empty variant of the loadable */
  public static RecordLoadable<FluidStack> notEmpty(RecordLoadable<FluidStack> loadable) {
    return loadable.validate(NOT_EMPTY);
  }
}
