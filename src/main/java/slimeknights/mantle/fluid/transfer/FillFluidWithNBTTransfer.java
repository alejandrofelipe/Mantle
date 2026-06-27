package slimeknights.mantle.fluid.transfer;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;

/** Fluid transfer info that fills a fluid into an item, copying its NBT */
public class FillFluidWithNBTTransfer extends FillFluidContainerTransfer {
  public static final ResourceLocation ID = Mantle.getResource("fill_nbt");
  public FillFluidWithNBTTransfer(Ingredient input, ItemOutput filled, FluidIngredient fluid) {
    super(input, filled, fluid);
  }

  @Override
  protected ItemStack getFilled(FluidStack drained) {
    ItemStack filled = super.getFilled(drained);
    // TODO PORT (stage 4 fluid components): in 1.20.1 this copied the drained fluid's CompoundTag back onto the item
    //  (filled.setTag(drained.getTag().copy())). FluidStack no longer carries a generic CompoundTag and ItemStack#setTag
    //  is gone; both now use DataComponentPatch. Restoring this round-trip requires deciding which DataComponentType(s)
    //  carry the data on the fluid and applying the matching patch to the item here (mirrors EmptyFluidWithNBTTransfer).
    return filled;
  }

  @Override
  public JsonObject serialize(JsonSerializationContext context) {
    JsonObject json = super.serialize(context);
    json.addProperty("type", ID.toString());
    return json;
  }

  /**
   * Unique loader instance
   */
  public static final JsonDeserializer<FillFluidWithNBTTransfer> DESERIALIZER = new Deserializer<>(FillFluidWithNBTTransfer::new);
}
