package slimeknights.mantle.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import slimeknights.mantle.util.TranslationHelper;

import java.util.Objects;
import java.util.function.Consumer;

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
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flagIn) {
    TranslationHelper.addOptionalTooltip(stack, tooltip);
    // TODO: use ContainerFoodItem helper for more potion like effects?
    // 1.21.4: status effects moved from FoodProperties to the Consumable component
    Consumable consumable = stack.get(DataComponents.CONSUMABLE);
    if (consumable != null) {
      for (ConsumeEffect consumeEffect : consumable.onConsumeEffects()) {
        if (consumeEffect instanceof ApplyStatusEffectsConsumeEffect applyEffects) {
          for (MobEffectInstance effect : applyEffects.effects()) {
            tooltip.accept(Component.literal(I18n.get(effect.getDescriptionId()).trim()).withStyle(ChatFormatting.GRAY));
          }
        }
      }
    }
  }
}
