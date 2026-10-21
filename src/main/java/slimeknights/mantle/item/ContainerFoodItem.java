package slimeknights.mantle.item;

import lombok.Getter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * Food item with a container that is returned when the item is consumed. Supports eating stackable items with containers.
 * Technically also works for items with no container.
 */
@SuppressWarnings("unused") // API
public class ContainerFoodItem extends Item {
  private final ItemUseAnimation useAnim;
  public ContainerFoodItem(Properties props, ItemUseAnimation useAnim) {
    super(props);
    this.useAnim = useAnim;
  }

  public ContainerFoodItem(Properties props) {
    this(props, ItemUseAnimation.DRINK);
  }

  @Override
  public ItemUseAnimation getUseAnimation(ItemStack pStack) {
    return useAnim;
  }

  /** Adds effects to the tooltip. 1.21.4 moves status effects from {@link net.minecraft.world.food.FoodProperties} to the {@link Consumable} component. */
  public static void addEffectTooltip(Consumable consumable, List<Component> tooltip) {
    // add effects to the tooltip, code based on potion items
    for (ConsumeEffect consumeEffect : consumable.onConsumeEffects()) {
      if (consumeEffect instanceof ApplyStatusEffectsConsumeEffect applyEffects) {
        for (MobEffectInstance effect : applyEffects.effects()) {
          MutableComponent mutable = Component.translatable(effect.getDescriptionId());
          if (effect.getAmplifier() > 0) {
            mutable = Component.translatable("potion.withAmplifier", mutable, Component.translatable("potion.potency." + effect.getAmplifier()));
          }
          if (effect.getDuration() > 20) {
            mutable = Component.translatable("potion.withDuration", mutable, MobEffectUtil.formatDuration(effect, 1.0f, 20.0f));
          }
          tooltip.add(mutable.withStyle(effect.getEffect().value().getCategory().getTooltipFormatting()));
        }
      }
    }
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flagIn) {
    Consumable consumable = stack.get(DataComponents.CONSUMABLE);
    if (consumable != null) {
      addEffectTooltip(consumable, tooltip);
    }
  }

  @Override
  public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
    ItemStack container = stack.getCraftingRemainder();
    ItemStack result = super.finishUsingItem(stack, level, living);
    Player player = living instanceof Player p ? p : null;
    if (!container.isEmpty() && (player == null || !player.getAbilities().instabuild)) {
      container = container.copy();
      if (result.isEmpty()) {
        return container;
      }
      if (player != null) {
        if (!player.getInventory().add(container)) {
          player.drop(container, false);
        }
      }
    }
    return result;
  }

  /**
   * Fluid containing variant of {@link ContainerFoodItem}.
   * <p>
   * The fluid handler capability is no longer exposed via {@code initCapabilities}; register it centrally on
   * {@code RegisterCapabilitiesEvent} in stage 7 using {@link #getFluid()}:
   * {@code event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new ConstantFluidContainerWrapper(item.getFluid().get(), stack), item)}
   */
  public static class FluidContainerFoodItem extends ContainerFoodItem {
    /** Supplier for the contained fluid, exposed for central capability registration in stage 7 */
    @Getter
    private final Supplier<FluidStack> fluid;
    public FluidContainerFoodItem(Properties props, Supplier<FluidStack> fluid) {
      super(props);
      this.fluid = fluid;
    }
  }
}
