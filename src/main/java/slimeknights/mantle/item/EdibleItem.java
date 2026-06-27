package slimeknights.mantle.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.util.TranslationHelper;

import java.util.List;
import java.util.Objects;

public class EdibleItem extends Item {
  public EdibleItem(FoodProperties foodIn) {
    this(new Properties().food(foodIn));
  }

  public EdibleItem(Item.Properties properties) {
    super(properties);
    // food is now a data component (DataComponents.FOOD) rather than the removed Item#foodProperties field
    Objects.requireNonNull(components().get(DataComponents.FOOD), "Must set food to make an EdibleItem");
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flagIn) {
    TranslationHelper.addOptionalTooltip(stack, tooltip);
    // TODO: use ContainerFoodItem helper for more potion like effects?
    FoodProperties food = stack.getFoodProperties(null);
    if (food != null) {
      for (FoodProperties.PossibleEffect possible : food.effects()) {
        if (possible.effect() != null) {
          tooltip.add(Component.literal(I18n.get(possible.effect().getDescriptionId()).trim()).withStyle(ChatFormatting.GRAY));
        }
      }
    }
  }
}
