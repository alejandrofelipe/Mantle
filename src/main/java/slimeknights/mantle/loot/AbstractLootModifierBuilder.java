package slimeknights.mantle.loot;

import lombok.RequiredArgsConstructor;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** Base builder for a global loot modifier during datagen, intended to be used with {@link GlobalLootModifierProvider} */
public abstract class AbstractLootModifierBuilder<B extends AbstractLootModifierBuilder<B>> {
  private final List<LootItemCondition> conditions = new ArrayList<>();
  private int priority = IGlobalLootModifier.DEFAULT_PRIORITY;

  /** Adds a condition to the builder */
  @SuppressWarnings("unchecked")
  public B addCondition(LootItemCondition condition) {
    conditions.add(condition);
    return (B) this;
  }

  /**
   * Sets the priority of the modifier. 26.1: NeoForge applies the modifiers sorted by priority, highest first; modifiers of the same priority run
   * in no fixed order, since the {@code global_loot_modifiers.json} index that ordered them is gone.
   */
  @SuppressWarnings("unchecked")
  public B priority(int priority) {
    this.priority = priority;
    return (B) this;
  }

  /** Gets the built list of conditions */
  protected LootItemCondition[] getConditions() {
    return conditions.toArray(new LootItemCondition[0]);
  }

  /** Gets the priority, {@link IGlobalLootModifier#DEFAULT_PRIORITY} unless set */
  protected int getPriority() {
    return priority;
  }

  /** Generic builder for a modifier that just takes conditions and the priority */
  @RequiredArgsConstructor
  public static class GenericLootModifierBuilder<M extends LootModifier> extends AbstractLootModifierBuilder<GenericLootModifierBuilder<M>> {
    private final BiFunction<LootItemCondition[],Integer,M> constructor;

    /** Builds the final instance */
    public M build() {
      return constructor.apply(getConditions(), getPriority());
    }
  }
}