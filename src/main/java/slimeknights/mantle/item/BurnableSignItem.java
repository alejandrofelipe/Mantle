package slimeknights.mantle.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.FuelValues;

import javax.annotation.Nullable;

public class BurnableSignItem extends SignItem {
  private final int burnTime;
  public BurnableSignItem(Properties propertiesIn, Block floorBlockIn, Block wallBlockIn, int burnTime) {
    super(floorBlockIn, wallBlockIn, propertiesIn);
    this.burnTime = burnTime;
  }

  @Override
  public int getBurnTime(ItemStack itemStack, @Nullable RecipeType<?> recipeType, FuelValues fuelValues) {
    return burnTime;
  }
}
