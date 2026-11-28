package slimeknights.mantle.data;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Registry;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.TagKey;

import java.util.concurrent.CompletableFuture;

/**
 * Tag provider for any registry from {@link net.minecraft.core.registries.BuiltInRegistries} that lacks a standard tag provider.
 * Its {@link #tag(TagKey)} takes registry objects as well as keys ({@link IntrinsicTagAppender}).
 */
public abstract class BuiltinRegistryTagProvider<T> extends TagsProvider<T> {
  private final Registry<T> registry;

  public BuiltinRegistryTagProvider(PackOutput packOutput, Registry<T> registry, CompletableFuture<Provider> lookupProvider, String modId) {
    super(packOutput, registry.key(), lookupProvider, modId);
    this.registry = registry;
  }

  @Override
  protected IntrinsicTagAppender<T> tag(TagKey<T> tag) {
    return IntrinsicTagAppender.of(super.tag(tag), registry);
  }

  @Override
  protected IntrinsicTagAppender<T> tag(TagKey<T> tag, boolean replace) {
    return IntrinsicTagAppender.of(super.tag(tag, replace), registry);
  }
}
