package slimeknights.mantle.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Binds the default data components of every registry element for a data run. Since 26.1 items (and other registry elements) get their default
 * components only when the server or client binds them from the loaded registries, so a data provider building an {@link net.minecraft.world.item.ItemStack}
 * or {@link net.neoforged.neoforge.fluids.FluidStack} fails with "Components not bound yet". Providers should write templates
 * ({@link net.minecraft.world.item.ItemStackTemplate}, Mantle's {@code ItemOutput}/{@code FluidOutput}) instead; this is the exception for
 * providers that truly need a stack, such as display stacks built by item logic.
 * <p>
 * Call {@link #bind(CompletableFuture)} from a {@link net.neoforged.neoforge.data.event.GatherDataEvent} listener with the registries the
 * providers use (including the mod's own datapack registry entries when an item's default components reference them): providers only run
 * after the event. Tags are not loaded during datagen, so any tag a component initializer looks up resolves to an empty tag; templates
 * written from such stacks only hold the stack's changes to the defaults, so their JSON does not depend on it.
 */
public final class DatagenComponents {
  private DatagenComponents() {}

  /** Waits for the registries and binds the default components from them */
  public static void bind(CompletableFuture<HolderLookup.Provider> registries) {
    bind(registries.join());
  }

  /** Binds the default components of every element of the given registries. Rebinding is allowed. */
  public static void bind(HolderLookup.Provider registries) {
    HolderLookup.Provider withTags = HolderLookup.Provider.create(registries.listRegistries().map(DatagenComponents::withEmptyTags));
    BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(withTags).forEach(DataComponentInitializers.PendingComponents::apply);
  }

  /** Wraps the lookup so missing, unbound or placeholder tags resolve to a bound empty tag */
  private static <T> HolderLookup.RegistryLookup<?> withEmptyTags(HolderLookup.RegistryLookup<T> lookup) {
    return new HolderLookup.RegistryLookup.Delegate<T>() {
      @Override
      public HolderLookup.RegistryLookup<T> parent() {
        return lookup;
      }

      @Override
      public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
        Optional<HolderSet.Named<T>> found;
        try {
          found = lookup.get(tag);
        } catch (IllegalStateException e) {
          // built-in registry whose tags were never bound
          found = Optional.empty();
        }
        // datagen lookups answer with HolderSet#emptyNamed placeholders, which cannot be dereferenced and fail NeoForge's component validation
        return found.isPresent() && found.get().getClass() == HolderSet.Named.class ? found : Optional.of(emptyTag(lookup, tag));
      }
    };
  }

  /** Creates a bound, empty named holder set (constructor and {@code bind} opened by Mantle's access transformer) */
  private static <T> HolderSet.Named<T> emptyTag(HolderOwner<T> owner, TagKey<T> tag) {
    HolderSet.Named<T> named = new HolderSet.Named<>(owner, tag);
    named.bind(List.of());
    return named;
  }
}
