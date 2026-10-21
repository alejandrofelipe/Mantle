package slimeknights.mantle.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.FuelValues;

import javax.annotation.Nullable;

public class BurnableBlockItem extends BlockItem {
  private final int burnTime;
  public BurnableBlockItem(Block blockIn, Properties builder, int burnTime) {
    super(blockIn, builder);
    this.burnTime = burnTime;
  }

  @Override
  public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType, FuelValues fuelValues) {
    return burnTime;
  }
}
