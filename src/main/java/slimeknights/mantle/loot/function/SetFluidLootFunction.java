package slimeknights.mantle.loot.function;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.transfer.TransferHelper;

import java.util.List;

/**
 * Loot function to set the fluid on a dropped item
 */
public class SetFluidLootFunction extends LootItemConditionalFunction {
  public static final MapCodec<SetFluidLootFunction> CODEC = RecordCodecBuilder.mapCodec(inst ->
    commonFields(inst).and(
      FluidStackTemplate.CODEC.fieldOf("fluid").forGetter(fn -> fn.fluid)
    ).apply(inst, SetFluidLootFunction::new));

  /** Fluid to add to the item. 26.1: a template, as loot tables decode before fluid components are bound */
  private final FluidStackTemplate fluid;
  protected SetFluidLootFunction(List<LootItemCondition> conditionsIn, FluidStackTemplate fluid) {
    super(conditionsIn);
    this.fluid = fluid;
  }

  @Override
  protected ItemStack run(ItemStack stack, LootContext context) {
    // PORT 1.21.10: fill every item of the stack. The handler works on an isolated copy of the whole stack, where amounts are totals over
    // the items, so the fill is split between them
    if (stack.isEmpty()) {
      return stack;
    }
    ItemStacksResourceHandler slot = TransferHelper.isolate(stack, stack.getCount());
    ResourceHandler<FluidResource> handler = TransferHelper.getFluidResourceHandler(slot);
    if (handler == null) {
      return stack;
    }
    // the changes only reach the copy, so read the result inside the transaction and close it without committing:
    // committing a nested transaction would tie the copy's journal to an outer transaction that may be open
    try (Transaction tx = TransferHelper.openNestedOrRoot()) {
      handler.insert(FluidResource.of(fluid), fluid.amount() * stack.getCount(), tx);
      return TransferHelper.getStack(slot);
    }
  }

  @Override
  public MapCodec<SetFluidLootFunction> codec() {
    return CODEC;
  }

  /**
   * Creates a new builder with the given fluid
   * @param fluid  Fluid to set
   * @return  Builder instance
   */
  public static Builder<?> builder(FluidStackTemplate fluid) {
    return simpleBuilder(conditions -> new SetFluidLootFunction(conditions, fluid));
  }
}
