package slimeknights.mantle.recipe.helper;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import slimeknights.mantle.data.JsonCodec;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.mantle.util.typed.TypedMapBuilder;

import java.util.function.Supplier;

/**
 * Recipe serializer instance using loadables. In 1.21 the recipe ID is provided externally via {@link net.minecraft.world.item.crafting.RecipeHolder}, so it is no longer threaded through the loadable context.
 * @param <T>  Recipe type
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class LoadableRecipeSerializer<T extends Recipe<?>> implements RecipeSerializer<T> {
  /** Context key to use if you want the recipe serializer passed into your recipe */
  public static final ContextKey<RecipeSerializer<?>> SERIALIZER = new ContextKey<>("serializer");
  /** Context key to use if you want a type aware serializer in the recipe, requires {@link #of(RecordLoadable, Supplier)} for your serializer. */
  public static final ContextKey<TypeAwareRecipeSerializer<?>> TYPED_SERIALIZER = new ContextKey<>("typed_serializer");
  /** Context key to use if you want the recipe type passed into your recipe, requires {@link #of(RecordLoadable, Supplier)} for your serializer. */
  public static final ContextKey<RecipeType<?>> TYPE = new ContextKey<>("type");
  /** Field for a group key in a recipe (common requirement) */
  public static final LoadableField<String,Recipe<?>> RECIPE_GROUP = StringLoadable.DEFAULT.defaultField("group", "", Recipe::getGroup);


  protected final RecordLoadable<T> loadable;

  /** Creates a standard serializer from a loadable */
  public static <T extends Recipe<?>> RecipeSerializer<T> of(RecordLoadable<T> loadable) {
    return new LoadableRecipeSerializer<>(loadable);
  }

  /** Creates a type aware serializer from a loadable */
  public static <T extends R, R extends Recipe<?>> TypeAwareRecipeSerializer<T> of(RecordLoadable<T> loadable, Supplier<? extends RecipeType<R>> type) {
    return new TypeAware<>(loadable, type);
  }

  /**
   * Builds the deserialization context for this serializer. 1.21 recipe codecs only receive the recipe value (the ID is
   * the external {@link net.minecraft.world.item.crafting.RecipeHolder} key), so the serializer itself is the only
   * context we can supply. Recipes that need {@link #SERIALIZER}/{@link #TYPED_SERIALIZER}/{@link #TYPE} read it from here.
   */
  protected TypedMap context() {
    return TypedMapBuilder.builder().put(SERIALIZER, this).build();
  }

  @Override
  public MapCodec<T> codec() {
    // inject the context (notably the serializer) since NeoForge's codec-based recipe loading provides none
    TypedMap context = context();
    return MapCodec.assumeMapUnsafe(new JsonCodec<T>() {
      @Override
      public T deserialize(JsonElement element, DynamicOps<?> ops) {
        return loadable.convert(element, "codec", context);
      }

      @Override
      public JsonElement serialize(T object, DynamicOps<?> ops) {
        return loadable.serialize(object);
      }
    });
  }

  @Override
  public StreamCodec<RegistryFriendlyByteBuf,T> streamCodec() {
    // network decode needs the same injected context as codec(): a recipe's RecordLoadable may include ContextFields
    // (notably TYPED_SERIALIZER) that are rebuilt purely from context and write nothing to the buffer. Loadable#streamCodec
    // would decode with an empty context and throw "Unable to fetch typed_serializer from context" during update_recipes sync.
    TypedMap context = context();
    return StreamCodec.of(
      (buffer, value) -> loadable.encode(buffer, value),
      buffer -> loadable.decode(buffer, context));
  }

  public static class TypeAware<T extends Recipe<?>> extends LoadableRecipeSerializer<T> implements TypeAwareRecipeSerializer<T> {
    private final Supplier<? extends RecipeType<?>> type;
    protected TypeAware(RecordLoadable<T> loadable, Supplier<? extends RecipeType<?>> type) {
      super(loadable);
      this.type = type;
    }

    @Override
    public RecipeType<?> getType() {
      return type.get();
    }

    @Override
    protected TypedMap context() {
      return TypedMapBuilder.builder().put(SERIALIZER, this).put(TYPED_SERIALIZER, this).put(TYPE, type.get()).build();
    }
  }
}
