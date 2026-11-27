package slimeknights.mantle.recipe.helper;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.Nullable;
import slimeknights.mantle.data.JsonCodec;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.mantle.util.typed.TypedMapBuilder;

import java.util.function.Supplier;

/**
 * Recipe serializer factory using loadables. In 1.21 the recipe ID is provided externally via {@link net.minecraft.world.item.crafting.RecipeHolder}, so it is no longer threaded through the loadable context.
 * 26.1: {@link RecipeSerializer} is a final record of a codec and a stream codec, so this class only builds serializers.
 */
public final class LoadableRecipeSerializer {
  /** Context key to use if you want the recipe serializer passed into your recipe */
  public static final ContextKey<RecipeSerializer<?>> SERIALIZER = new ContextKey<>("serializer");
  /** Context key to use if you want a type aware serializer in the recipe, requires {@link #of(RecordLoadable, Supplier)} for your serializer. */
  public static final ContextKey<TypeAwareRecipeSerializer<?>> TYPED_SERIALIZER = new ContextKey<>("typed_serializer");
  /** Context key to use if you want the recipe type passed into your recipe, requires {@link #of(RecordLoadable, Supplier)} for your serializer. */
  public static final ContextKey<RecipeType<?>> TYPE = new ContextKey<>("type");
  /** Field for a group key in a recipe (common requirement) */
  public static final LoadableField<String,Recipe<?>> RECIPE_GROUP = StringLoadable.DEFAULT.defaultField("group", "", Recipe::group);

  private LoadableRecipeSerializer() {}

  /** Creates a standard serializer from a loadable */
  public static <T extends Recipe<?>> RecipeSerializer<T> of(RecordLoadable<T> loadable) {
    LazyContext context = new LazyContext();
    RecipeSerializer<T> serializer = new RecipeSerializer<>(codec(loadable, context), streamCodec(loadable, context));
    context.factory = () -> TypedMapBuilder.builder().put(SERIALIZER, serializer).build();
    return serializer;
  }

  /**
   * Creates a type aware serializer from a loadable. Register {@link TypeAwareRecipeSerializer#serializer()}, the recipe
   * reads the pair from {@link #TYPED_SERIALIZER}.
   */
  public static <T extends R, R extends Recipe<?>> TypeAwareRecipeSerializer<T> of(RecordLoadable<T> loadable, Supplier<? extends RecipeType<R>> type) {
    LazyContext context = new LazyContext();
    RecipeSerializer<T> serializer = new RecipeSerializer<>(codec(loadable, context), streamCodec(loadable, context));
    TypeAwareRecipeSerializer<T> typed = new TypeAwareRecipeSerializer<>(serializer, type);
    // built on first use: the recipe type is registered after the serializer supplier runs
    context.factory = () -> TypedMapBuilder.builder().put(SERIALIZER, serializer).put(TYPED_SERIALIZER, typed).put(TYPE, type.get()).build();
    return typed;
  }

  /**
   * Builds the codec for a loadable. 1.21 recipe codecs only receive the recipe value (the ID is the external
   * {@link net.minecraft.world.item.crafting.RecipeHolder} key), so the serializer itself is the only context we can supply.
   * Recipes that need {@link #SERIALIZER}/{@link #TYPED_SERIALIZER}/{@link #TYPE} read it from the context.
   */
  private static <T> MapCodec<T> codec(RecordLoadable<T> loadable, Supplier<TypedMap> context) {
    // inject the context (notably the serializer) since NeoForge's codec-based recipe loading provides none
    return MapCodec.assumeMapUnsafe(new JsonCodec<T>() {
      @Override
      public T deserialize(JsonElement element, DynamicOps<?> ops) {
        // thread the registry-aware ops NeoForge passes here into the loadable context so holder/tag/item loadables
        // (notably IngredientLoadable) can resolve against the real registries instead of a registry-less JsonOps
        return loadable.convert(element, "codec", jsonContext(context.get(), ops));
      }

      @Override
      public JsonElement serialize(T object, DynamicOps<?> ops) {
        return loadable.serialize(object);
      }
    });
  }

  /** Builds a context including the caller's registry-aware JSON ops, used so loadables can resolve registry entries. */
  @SuppressWarnings("unchecked")
  private static TypedMap jsonContext(TypedMap base, DynamicOps<?> ops) {
    return TypedMapBuilder.builder().putAll(base).put(ContextKey.OPS, (DynamicOps<JsonElement>) ops).build();
  }

  /** Builds the stream codec for a loadable */
  private static <T> StreamCodec<RegistryFriendlyByteBuf,T> streamCodec(RecordLoadable<T> loadable, Supplier<TypedMap> context) {
    // network decode needs the same injected context as codec(): a recipe's RecordLoadable may include ContextFields
    // (notably TYPED_SERIALIZER) that are rebuilt purely from context and write nothing to the buffer. Loadable#streamCodec
    // would decode with an empty context and throw "Unable to fetch typed_serializer from context" during update_recipes sync.
    return StreamCodec.of(
      (buffer, value) -> loadable.encode(buffer, value),
      buffer -> loadable.decode(buffer, context.get()));
  }

  /** Context built on first use, as it references the serializer that is created with the codecs */
  private static final class LazyContext implements Supplier<TypedMap> {
    @Nullable
    private Supplier<TypedMap> factory;
    @Nullable
    private TypedMap context;

    @Override
    public TypedMap get() {
      if (context == null) {
        if (factory == null) {
          throw new IllegalStateException("Recipe serializer context used before the serializer was created");
        }
        context = factory.get();
      }
      return context;
    }
  }
}
