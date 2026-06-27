package slimeknights.mantle.fluid.transfer;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.mantle.recipe.helper.ItemOutput;

/** Fluid transfer info that empties a fluid from an item, copying the fluid's NBT to the stack */
public class EmptyFluidWithNBTTransfer extends EmptyFluidContainerTransfer {
  public static final ResourceLocation ID = Mantle.getResource("empty_nbt");
  public EmptyFluidWithNBTTransfer(Ingredient input, ItemOutput filled, FluidOutput fluid) {
    super(input, filled, fluid);
  }

  /** @deprecated use {@link #EmptyFluidWithNBTTransfer(Ingredient, ItemOutput, FluidOutput)} */
  @Deprecated(forRemoval = true)
  public EmptyFluidWithNBTTransfer(Ingredient input, ItemOutput filled, FluidStack fluid) {
    this(input, filled, FluidOutput.fromStack(fluid));
  }

  @Override
  protected FluidStack getFluid(ItemStack stack) {
    // TODO PORT (stage 4 fluid components): in 1.20.1 this copied the item's raw CompoundTag onto the fluid stack
    //  (new FluidStack(fluid, amount, stack.getTag())). With data components there is no generic item-NBT -> fluid-NBT
    //  bridge; the data carried needs to be remapped to specific DataComponentTypes (and the matching component must be
    //  copied back in FillFluidWithNBTTransfer). Until the component schema for these containers is decided, we return
    //  the configured fluid without copying item data. The fill side mirrors this.
    return fluid.copy();
  }

  @Override
  public JsonObject serialize(JsonSerializationContext context) {
    JsonObject json = super.serialize(context);
    json.addProperty("type", ID.toString());
    return json;
  }

  /** Unique loader instance */
  public static final JsonDeserializer<EmptyFluidContainerTransfer> DESERIALIZER = new Deserializer<>(EmptyFluidWithNBTTransfer::new);
}
