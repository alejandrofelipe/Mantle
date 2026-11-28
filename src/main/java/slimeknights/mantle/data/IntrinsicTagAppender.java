package slimeknights.mantle.data;

import net.minecraft.core.Registry;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Tag appender that takes registry objects as well as keys. MC 26.2 removed {@code IntrinsicHolderTagsProvider}: every
 * {@link TagAppender} only takes {@link ResourceKey}s, and NeoForge's block/item tag providers became plain
 * {@link TagsProvider}s. A provider keeps adding objects ({@code tag(BlockTags.X).add(Blocks.Y)}) by returning this appender
 * from its {@code tag} methods:
 * <pre>{@code
 * @Override
 * protected IntrinsicTagAppender<Block> tag(TagKey<Block> tag) {
 *   return IntrinsicTagAppender.of(super.tag(tag), BuiltInRegistries.BLOCK);
 * }
 * }</pre>
 * Every method writes to the wrapped appender in call order, so the generated tag files are the same as with the old
 * intrinsic appender. All methods return this appender, so key and object calls chain freely. Collections stay key based
 * ({@link #addAll(Collection)}): an object overload would have the same erasure.
 * @param <T> Registry object type
 */
public final class IntrinsicTagAppender<T> implements TagAppender<T> {
  private final TagAppender<T> delegate;
  private final Function<T, ResourceKey<T>> keyExtractor;

  private IntrinsicTagAppender(TagAppender<T> delegate, Function<T, ResourceKey<T>> keyExtractor) {
    this.delegate = delegate;
    this.keyExtractor = keyExtractor;
  }

  /** Wraps an appender with a custom key lookup */
  public static <T> IntrinsicTagAppender<T> of(TagAppender<T> delegate, Function<T, ResourceKey<T>> keyExtractor) {
    return new IntrinsicTagAppender<>(delegate, keyExtractor);
  }

  /** Wraps an appender, looking keys up in the given registry; an unregistered object throws */
  public static <T> IntrinsicTagAppender<T> of(TagAppender<T> delegate, Registry<T> registry) {
    return of(delegate, value -> registry.getResourceKey(value).orElseThrow(
      () -> new IllegalArgumentException("Object " + value + " is not registered in " + registry.key().identifier())));
  }

  /** Key of the given object */
  public ResourceKey<T> key(T element) {
    return keyExtractor.apply(element);
  }

  /* Objects */

  /** Adds a required object */
  public IntrinsicTagAppender<T> add(T element) {
    delegate.add(key(element));
    return this;
  }

  /** Adds required objects, in order */
  @SafeVarargs
  public final IntrinsicTagAppender<T> add(T... elements) {
    for (T element : elements) {
      add(element);
    }
    return this;
  }

  /** Adds an optional object */
  public IntrinsicTagAppender<T> addOptional(T element) {
    delegate.addOptional(key(element));
    return this;
  }

  /** Removes an object (NeoForge tag file {@code remove} list) */
  public IntrinsicTagAppender<T> remove(T element) {
    delegate.remove(key(element));
    return this;
  }

  /** Removes objects (NeoForge tag file {@code remove} list), in order */
  @SafeVarargs
  public final IntrinsicTagAppender<T> remove(T first, T... elements) {
    remove(first);
    for (T element : elements) {
      remove(element);
    }
    return this;
  }

  /* Keys and tags: same as the wrapped appender, typed for chaining */

  @Override
  public IntrinsicTagAppender<T> add(ResourceKey<T> element) {
    delegate.add(element);
    return this;
  }

  @SafeVarargs
  @Override
  public final IntrinsicTagAppender<T> add(ResourceKey<T>... elements) {
    for (ResourceKey<T> element : elements) {
      add(element);
    }
    return this;
  }

  @Override
  public IntrinsicTagAppender<T> addAll(Collection<ResourceKey<T>> elements) {
    elements.forEach(this::add);
    return this;
  }

  @Override
  public IntrinsicTagAppender<T> addAll(Stream<ResourceKey<T>> elements) {
    elements.forEach(this::add);
    return this;
  }

  @Override
  public IntrinsicTagAppender<T> addOptional(ResourceKey<T> element) {
    delegate.addOptional(element);
    return this;
  }

  @Override
  public IntrinsicTagAppender<T> addTag(TagKey<T> tag) {
    delegate.addTag(tag);
    return this;
  }

  @Override
  public IntrinsicTagAppender<T> addOptionalTag(TagKey<T> tag) {
    delegate.addOptionalTag(tag);
    return this;
  }

  @SafeVarargs
  @Override
  public final IntrinsicTagAppender<T> addTags(TagKey<T>... tags) {
    for (TagKey<T> tag : tags) {
      addTag(tag);
    }
    return this;
  }

  @SafeVarargs
  @Override
  public final IntrinsicTagAppender<T> addOptionalTags(TagKey<T>... tags) {
    for (TagKey<T> tag : tags) {
      addOptionalTag(tag);
    }
    return this;
  }

  @Override
  public IntrinsicTagAppender<T> add(TagEntry entry) {
    delegate.add(entry);
    return this;
  }

  @Override
  public IntrinsicTagAppender<T> replace() {
    return replace(true);
  }

  @Override
  public IntrinsicTagAppender<T> replace(boolean value) {
    delegate.replace(value);
    return this;
  }

  @Override
  public IntrinsicTagAppender<T> remove(ResourceKey<T> element) {
    delegate.remove(element);
    return this;
  }

  @SafeVarargs
  @Override
  public final IntrinsicTagAppender<T> remove(ResourceKey<T> first, ResourceKey<T>... elements) {
    remove(first);
    for (ResourceKey<T> element : elements) {
      remove(element);
    }
    return this;
  }

  @Override
  public IntrinsicTagAppender<T> remove(TagKey<T> tag) {
    delegate.remove(tag);
    return this;
  }

  @SafeVarargs
  @Override
  public final IntrinsicTagAppender<T> remove(TagKey<T> first, TagKey<T>... tags) {
    remove(first);
    for (TagKey<T> tag : tags) {
      remove(tag);
    }
    return this;
  }
}
