package slimeknights.mantle.recipe.ingredient;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableIngredientSerializer;
import slimeknights.mantle.registration.object.FluidObject;
import slimeknights.mantle.transfer.TransferHelper;

import javax.annotation.Nullable;
import java.util.stream.Stream;

/** Ingredient that matches a container of fluid */
@SuppressWarnings("unused")  // API
public class FluidContainerIngredient implements ICustomIngredient {
  public static final ResourceLocation ID = Mantle.getResource("fluid_container");

  /** Loadable field for the fluid ingredient */
  private static final LoadableField<FluidIngredient,FluidContainerIngredient> FLUID_FIELD = FluidIngredient.LOADABLE.requiredField("fluid", i -> i.fluidIngredient);
  /** Loadable field for the optional display ingredient */
  private static final LoadableField<Ingredient,FluidContainerIngredient> DISPLAY_FIELD = slimeknights.mantle.data.loadable.common.IngredientLoadable.ALLOW_EMPTY.nullableField("display", i -> i.display);

  /** Serializer instance for the ingredient type */
  public static final LoadableIngredientSerializer<FluidContainerIngredient> SERIALIZER = new LoadableIngredientSerializer<>(RecordLoadable.create(
    FLUID_FIELD, DISPLAY_FIELD,
    FluidContainerIngredient::new));

  /** Ingredient to use for matching */
  private final FluidIngredient fluidIngredient;
  /** Internal ingredient to display the ingredient in recipe viewers */
  @Nullable
  private final Ingredient display;
  protected FluidContainerIngredient(FluidIngredient fluidIngredient, @Nullable Ingredient display) {
    this.fluidIngredient = fluidIngredient;
    this.display = display;
  }

  /** Creates an instance from a fluid ingredient with a display container */
  public static FluidContainerIngredient fromIngredient(FluidIngredient ingredient, Ingredient display) {
    return new FluidContainerIngredient(ingredient, display);
  }

  /** Creates an instance from a fluid ingredient with no display, not recommended */
  public static FluidContainerIngredient fromIngredient(FluidIngredient ingredient) {
    return new FluidContainerIngredient(ingredient, null);
  }

  /** Creates an instance from a fluid ingredient with a display container */
  public static FluidContainerIngredient fromFluid(FluidObject<?> fluid) {
    return fromIngredient(fluid.ingredient(FluidType.BUCKET_VOLUME), Ingredient.of(fluid));
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    // first, must have a fluid capability. PORT 1.21.10: the bridge works on a copy of a single item (amounts are totals over the stack),
    // so the same handler can be drained below without touching the passed stack
    IFluidHandlerItem cap = TransferHelper.getFluidHandler(stack.copyWithCount(1));
    if (cap == null || cap.getTanks() != 1) {
      return false;
    }
    // second, must contain enough fluid
    FluidStack contained = cap.getFluidInTank(0);
    if (contained.isEmpty() || fluidIngredient.getAmount(contained.getFluid()) != contained.getAmount() || !fluidIngredient.test(contained.getFluid())) {
      return false;
    }
    // alright, we know it has the fluid, the question is just whether draining the fluid will give us the desired result
    Fluid fluid = contained.getFluid();
    int amount = fluidIngredient.getAmount(fluid);
    FluidStack drained = cap.drain(amount, FluidAction.EXECUTE);
    // we need an exact match, and we need the resulting container item to be the same as the item stack's container item
    return drained.getFluid() == fluid && drained.getAmount() == amount && ItemStack.matches(stack.getCraftingRemainder(), cap.getContainer());
  }

  @Override
  public Stream<Holder<Item>> items() {
    // no container? unfortunately hard to display this recipe so show nothing
    if (display == null) {
      return Stream.empty();
    }
    return display.items();
  }

  @Override
  public boolean isSimple() {
    return false;
  }

  @Override
  public IngredientType<?> getType() {
    return MantleIngredients.FLUID_CONTAINER.get();
  }
}
