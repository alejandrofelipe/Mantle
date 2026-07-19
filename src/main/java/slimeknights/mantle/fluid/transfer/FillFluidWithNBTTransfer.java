package slimeknights.mantle.fluid.transfer;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
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
    // carry the potion contents from the fluid back onto the item, mirroring EmptyPotionTransfer. 1.20.1 copied the
    // whole CompoundTag; here we bridge the one component the potion fluid carries (this transfer is wired for potions).
    PotionContents contents = drained.get(DataComponents.POTION_CONTENTS);
    if (contents != null) {
      filled.set(DataComponents.POTION_CONTENTS, contents);
    }
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
