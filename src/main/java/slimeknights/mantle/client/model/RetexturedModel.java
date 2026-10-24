package slimeknights.mantle.client.model;

import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.block.model.SingleVariant;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.joml.Vector3f;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.DynamicBakedWrapper;
import slimeknights.mantle.client.model.util.ElementModelData;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.client.model.util.ModelTextureIteratable;
import slimeknights.mantle.client.model.util.SimpleBlockModel;
import slimeknights.mantle.util.RetexturedHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Model that dynamically retextures a list of textures based on data from {@link RetexturedHelper}.
 * <p>
 * 1.21.5: split like {@link slimeknights.mantle.client.model.connected.ConnectedModel}:
 * <ul>
 *   <li>The model loader ({@code "loader": "mantle:retextured"}) holds the retextured names and bakes the base geometry.</li>
 *   <li>The block state model type ({@code {"type": "mantle:retextured", "model": ...}}, see {@link Unbaked}) reads the
 *   texture block from the block entity model data ({@code level.getModelData(pos)}) and rebakes per texture.</li>
 *   <li>The item model type ({@code {"type": "mantle:retextured", "model": ...}} in {@code assets/<ns>/items}, see
 *   {@link ItemUnbaked}) reads the texture block from the stack.</li>
 * </ul>
 * Unlike 1.21.4, models inheriting their elements from a parent (e.g. the Tinkers tables) retexture correctly.
 */
@SuppressWarnings("WeakerAccess")
public class RetexturedModel extends ColoredBlockModel {
  /** Loader instance */
  public static UnbakedModelLoader<RetexturedModel> LOADER = RetexturedModel::deserialize;

  /** Texture names to retexture, not including names referencing them */
  private final Set<String> retextured;

  protected RetexturedModel(SimpleBlockModel model, Set<String> retextured) {
    super(model, ColoredBlockModel.getColorData(model));
    this.retextured = retextured;
  }

  /** Gets the texture names to retexture, not including names referencing them */
  public Set<String> getRetextured() {
    return retextured;
  }

  /**
   * Finds the retextured model in a resolved model chain, allowing child models without a loader to inherit the config
   * @param model  Resolved model
   * @return  Retextured model, or null if the chain has none
   */
  @Nullable
  public static RetexturedModel find(@Nullable ResolvedModel model) {
    for (; model != null; model = model.parent()) {
      if (model.wrapped() instanceof RetexturedModel retextured) {
        return retextured;
      }
    }
    return null;
  }

  /**
   * Shared dynamic retexturing logic for the block state and item models: rebakes the captured elements with all
   * retextured names pointing at the texture block's particle texture, caching per texture.
   */
  public static class Retexturer {
    private final ElementModelData data;
    private final Set<String> retextured;
    /** Cache of baked variants keyed by the texture block's particle texture */
    private final Map<ResourceLocation,SimpleModelWrapper> cache = new ConcurrentHashMap<>();

    /**
     * @param data        Element data captured at bake time
     * @param retextured  All names to retexture, including references, see {@link #getAllRetextured(ResolvedModel, Set)}
     */
    public Retexturer(ElementModelData data, Set<String> retextured) {
      this.data = data;
      this.retextured = retextured;
    }

    /** Creates a retexturer for the given resolved model, or null if it has no retextured model in its chain */
    @Nullable
    public static Retexturer of(ModelBaker baker, ResolvedModel resolved, ModelState state) {
      RetexturedModel model = find(resolved);
      if (model == null) {
        return null;
      }
      return new Retexturer(ElementModelData.of(baker, resolved, state, model.getColorData()), getAllRetextured(resolved, model.retextured));
    }

    /** Gets the captured element data */
    public ElementModelData getData() {
      return data;
    }

    /** Rebakes the model, pointing all retextured names at the given texture */
    private SimpleModelWrapper bake(ResourceLocation texture) {
      Material material = new Material(TextureAtlas.LOCATION_BLOCKS, texture);
      Map<String,Material> replacements = new HashMap<>();
      for (String name : retextured) {
        replacements.put(name, material);
      }
      return data.part(data.bakeReplaced(replacements), data.particle(replacements));
    }

    /** Gets the cached model for the given block */
    public SimpleModelWrapper getCachedModel(Block block) {
      return cache.computeIfAbsent(ModelHelper.getParticleTexture(block), this::bake);
    }
  }

  /**
   * Block state model for retextured blocks, referenced from the blockstate JSON as
   * {@code {"type": "mantle:retextured", "model": "<retextured model>", "x": 0, "y": 0, "uvlock": false}}.
   * Accepts the same fields as a vanilla variant.
   */
  public record Unbaked(Variant variant) implements CustomUnbakedBlockStateModel {
    public static final MapCodec<Unbaked> MAP_CODEC = Variant.MAP_CODEC.xmap(Unbaked::new, Unbaked::variant);

    /** Creates a retextured block state model for the given model location */
    public Unbaked(ResourceLocation model) {
      this(new Variant(model));
    }

    @Override
    public MapCodec<Unbaked> codec() {
      return MAP_CODEC;
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
      variant.resolveDependencies(resolver);
    }

    @Override
    public BlockStateModel bake(ModelBaker baker) {
      ResolvedModel resolved = baker.getModel(variant.modelLocation());
      ModelState state = variant.modelState().asModelState();
      BlockModelPart base = SimpleModelWrapper.bake(baker, resolved, state);
      Retexturer retexturer = Retexturer.of(baker, resolved, state);
      if (retexturer == null) {
        Mantle.logger.warn("Block state model type mantle:retextured used with {}, which is not a retextured model", resolved.debugName());
        return new SingleVariant(base);
      }
      return new Baked(base, retexturer);
    }
  }

  /**
   * Block state model that dynamically retextures based on {@link RetexturedHelper#BLOCK_PROPERTY} in the block entity
   * model data, read from the level.
   */
  public static class Baked extends DynamicBakedWrapper<SingleVariant> {
    private final Retexturer retexturer;

    protected Baked(BlockModelPart base, Retexturer retexturer) {
      super(new SingleVariant(base));
      this.retexturer = retexturer;
    }

    /** Gets the texture block from the level, or null if unset */
    @Nullable
    private static Block getTexture(BlockAndTintGetter level, BlockPos pos) {
      Block block = level.getModelData(pos).get(RetexturedHelper.BLOCK_PROPERTY);
      return block == Blocks.AIR ? null : block;
    }

    /** Gets the cached model for the given block */
    public BlockModelPart getCachedModel(Block block) {
      return retexturer.getCachedModel(block);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockModelPart> parts) {
      Block block = getTexture(level, pos);
      if (block != null) {
        parts.add(getCachedModel(block));
      } else {
        parent.collectParts(random, parts);
      }
    }

    @Override
    public TextureAtlasSprite particleIcon(BlockAndTintGetter level, BlockPos pos, BlockState state) {
      Block block = getTexture(level, pos);
      if (block != null) {
        return getCachedModel(block).particleIcon();
      }
      return super.particleIcon(level, pos, state);
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
      Block block = getTexture(level, pos);
      return new GeometryKey(this, block == null ? null : ModelHelper.getParticleTexture(block));
    }

    /** Geometry key, the texture fully determines the geometry */
    private record GeometryKey(Baked model, @Nullable ResourceLocation texture) {}
  }

  /**
   * Gets a list of all names to retexture based on the block model texture references
   * @param model        Resolved model, its whole chain (including vanilla parents) is searched for references
   * @param originalSet  Original list of names to retexture
   * @return  Set of textures including parent textures
   */
  public static Set<String> getAllRetextured(ResolvedModel model, Set<String> originalSet) {
    Set<String> retextured = Sets.newHashSet(originalSet);
    retextured.addAll(ModelHelper.findReferences(model, originalSet, ModelHelper.getTextureNames(model)).keySet());
    return Set.copyOf(retextured);
  }

  /**
   * Gets a list of all names to retexture based on the block model texture references
   * @param model        Model fallback
   * @param originalSet  Original list of names to retexture
   * @return  Set of textures including parent textures
   * @deprecated 1.21.5: unbaked models cannot walk their parents, so this only reads the model's own textures. Use {@link #getAllRetextured(ResolvedModel, Set)}.
   */
  @Deprecated
  public static Set<String> getAllRetextured(SimpleBlockModel model, Set<String> originalSet) {
    return getAllRetextured(ModelTextureIteratable.of(model), originalSet);
  }

  /** Shared logic for {@link #getAllRetextured(SimpleBlockModel, Set)} */
  private static Set<String> getAllRetextured(ModelTextureIteratable maps, Set<String> originalSet) {
    Set<String> retextured = Sets.newHashSet(originalSet);
    for (Map<String,Either<Material, String>> textures : maps) {
      textures.forEach((name, either) ->
        either.ifRight(parent -> {
          if (retextured.contains(parent)) {
            retextured.add(name);
          }
        })
      );
    }
    return Set.copyOf(retextured);
  }

  /** Deserializes a retextured model from JSON */
  public static RetexturedModel deserialize(JsonObject json, JsonDeserializationContext context) {
    // get base model
    ColoredBlockModel model = ColoredBlockModel.deserialize(json, context);
    // get list of textures to retexture
    Set<String> retextured = getRetexturedNames(json);
    // return retextured model
    return new RetexturedModel(model, retextured);
  }

  /**
   * Gets the list of retextured textures from the model
   * @param json  Model json
   * @return  Set of textures
   */
  public static Set<String> getRetexturedNames(JsonObject json) {
    if (json.has("retextured")) {
      // if an array, set from each texture in array
      JsonElement retextured = json.get("retextured");
      if (retextured.isJsonArray()) {
        JsonArray array = retextured.getAsJsonArray();
        if (array.isEmpty()) {
          throw new JsonSyntaxException("Must have at least one texture in retextured");
        }
        List<String> builder = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
          builder.add(GsonHelper.convertToString(array.get(i), "retextured[" + i + "]"));
        }
        return Set.copyOf(builder);
      }
      // if string, single texture
      if (retextured.isJsonPrimitive()) {
        return Set.of(retextured.getAsString());
      }
    }
    // if neither or missing, error
    throw new JsonSyntaxException("Missing retextured, expected to find a String or a JsonArray");
  }

  /**
   * Item model that renders a {@link RetexturedModel}, resolving the per-stack texture block. Registered via
   * {@code RegisterItemModelsEvent}; the item JSON is {@code {"model":{"type":"mantle:retextured","model":"<block model>"}}}.
   * Without a texture on the stack, renders the base model like {@code minecraft:model}.
   */
  public record ItemUnbaked(ResourceLocation model) implements ItemModel.Unbaked {
    public static final MapCodec<ItemUnbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
      ResourceLocation.CODEC.fieldOf("model").forGetter(ItemUnbaked::model)
    ).apply(inst, ItemUnbaked::new));

    @Override
    public MapCodec<ItemUnbaked> type() {
      return MAP_CODEC;
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
      resolver.markDependency(model);
    }

    @Override
    public ItemModel bake(ItemModel.BakingContext context) {
      ModelBaker baker = context.blockModelBaker();
      ResolvedModel resolved = baker.getModel(model);
      // base model, matching vanilla BlockModelWrapper
      TextureSlots textures = resolved.getTopTextureSlots();
      List<BakedQuad> quads = resolved.bakeTopGeometry(textures, baker, BlockModelRotation.X0_Y0).getAll();
      ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, resolved, textures);
      Retexturer retexturer = Retexturer.of(baker, resolved, BlockModelRotation.X0_Y0);
      return new Baked(new ItemVariant(quads, properties), retexturer, ElementModelData.itemRenderType(resolved));
    }

    /** Quads and properties for a single item variant, with lazily computed extents */
    private record ItemVariant(List<BakedQuad> quads, ModelRenderProperties properties, Supplier<Vector3f[]> extents) {
      private ItemVariant(List<BakedQuad> quads, ModelRenderProperties properties) {
        this(quads, properties, Suppliers.memoize(() -> BlockModelWrapper.computeExtents(quads)));
      }
    }

    /** Baked item model selecting the retextured variant for the stack */
    public static class Baked implements ItemModel {
      private final ItemVariant base;
      @Nullable
      private final Retexturer retexturer;
      @Nullable
      private final RenderType renderType;
      private final Map<Block,ItemVariant> cache = new ConcurrentHashMap<>();

      private Baked(ItemVariant base, @Nullable Retexturer retexturer, @Nullable RenderType renderType) {
        this.base = base;
        this.retexturer = retexturer;
        this.renderType = renderType;
      }

      /** Gets the variant for the given block */
      private ItemVariant getVariant(Block block) {
        assert retexturer != null;
        return cache.computeIfAbsent(block, b -> {
          SimpleModelWrapper part = retexturer.getCachedModel(b);
          return new ItemVariant(part.quads().getAll(), retexturer.getData().itemProperties(part.particleIcon()));
        });
      }

      @Override
      public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        ItemVariant variant = base;
        if (retexturer != null && !stack.isEmpty()) {
          Block block = RetexturedHelper.getTexture(stack);
          if (block != Blocks.AIR) {
            variant = getVariant(block);
          }
        }
        ModelHelper.addItemLayer(renderState, stack, displayContext, variant.quads, variant.properties, renderType, variant.extents);
      }
    }
  }
}
