package slimeknights.mantle.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import slimeknights.mantle.util.TranslationHelper;

import java.util.function.Consumer;

/** Armor item with automatic tooltip support. 1.21.5: {@code ArmorItem} was removed, armor is a plain item with armor properties. */
public class ArmorTooltipItem extends Item {

  public ArmorTooltipItem(ArmorMaterial armorMaterial, ArmorType type, Properties builder) {
    super(builder.humanoidArmor(armorMaterial, type));
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flagIn) {
    TranslationHelper.addOptionalTooltip(stack, tooltip);
    super.appendHoverText(stack, context, display, tooltip, flagIn);
  }
}
