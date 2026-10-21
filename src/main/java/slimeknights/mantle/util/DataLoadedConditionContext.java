package slimeknights.mantle.util;

import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Condition context to use when data has already been loaded, used in books for processing their conditions for instance. */
public enum DataLoadedConditionContext implements ICondition.IContext {
  INSTANCE;

  @Override
  public <T> boolean isTagLoaded(TagKey<T> key) {
    // 1.21.4: IContext only exposes whether a tag is loaded. As this context runs after data load, treat a tag with any
    // entries as loaded, matching the old behavior where an empty/missing tag counted as unloaded.
    return RegistryHelper.getTagStream(key).findAny().isPresent();
  }
}
