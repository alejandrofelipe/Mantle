package slimeknights.mantle.client.model;

import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.ExtendedUnbakedGeometry;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import org.joml.Vector3f;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.util.JsonHelper;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Model which uses a key in NBT to select which texture variant to load.
 * <p>
 * 1.21.5: the model loader ({@code "loader": "mantle:nbt_key"}) defines the textures and key, and its geometry bakes the
 * {@code default} texture. The variant selection lives in the item model type {@code mantle:nbt_key} (see
 * {@link ItemUnbaked}), used from {@code assets/<ns>/items/<id>.json} as
 * {@code {"model":{"type":"mantle:nbt_key","model":"<nbt key model>"}}}. It replaces the removed {@code ItemOverrides}.
 */
public class NBTKeyModel extends AbstractUnbakedModel {
  /** Model loader instance */
  public static final UnbakedModelLoader<NBTKeyModel> LOADER = NBTKeyModel::deserialize;

  /** Map of statically registered extra textures, used for addon mods */
  private static final Multimap<ResourceLocation,Pair<String,ResourceLocation>> EXTRA_TEXTURES = HashMultimap.create();

  /**
   * Registers an extra variant texture for the model with the given key. Note that resource packs can override the extra texture
   * @param key          Model key, should be defined in the model JSON if supported
   * @param textureName  Name of the texture defined, corresponds to a possible value of the NBT key
   * @param texture      Texture to use, same format as in resource packs
   */
  @SuppressWarnings("unused")  // API
  public static void registerExtraTexture(ResourceLocation key, String textureName, ResourceLocation texture) {
    EXTRA_TEXTURES.put(key, Pair.of(textureName, texture));
  }

  /** Key to check in item NBT */
  private final String nbtKey;
  /** Key denoting which extra textures to fetch from the map */
  @Nullable
  private final ResourceLocation extraTexturesKey;
  /** Texture slots, with the particle defaulting to the default texture */
  private final TextureSlots.Data textureSlots;

  public NBTKeyModel(StandardModelParameters parameters, String nbtKey, @Nullable ResourceLocation extraTexturesKey) {
    super(parameters);
    this.nbtKey = nbtKey;
    this.extraTexturesKey = extraTexturesKey;
    this.textureSlots = MantleItemLayerModel.withDefaultParticle(parameters.textures(), "default");
  }

  /** Gets the key read from the stack's custom data */
  public String getNbtKey() {
    return nbtKey;
  }

  /** Gets the extra textures registered for this model's key */
  public Collection<Pair<String,ResourceLocation>> getExtraTextures() {
    return extraTexturesKey != null ? EXTRA_TEXTURES.get(extraTexturesKey) : List.of();
  }

  @Override
  public TextureSlots.Data textureSlots() {
    return textureSlots;
  }

  @Override
  public ExtendedUnbakedGeometry geometry() {
    return this::bake;
  }

  /** Bakes the default texture, used when the model is rendered without the item model type */
  private QuadCollection bake(TextureSlots textures, ModelBaker baker, ModelState state, ModelDebugName debugName, ContextMap additionalProperties) {
    Material defaultTexture = textures.getMaterial("default");
    if (defaultTexture == null) {
      defaultTexture = new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation());
    }
    QuadCollection.Builder builder = new QuadCollection.Builder();
    bakeQuads(baker.sprites().get(defaultTexture, debugName), state, additionalProperties).forEach(builder::addUnculledFace);
    return builder.build();
  }

  /** Bakes the item layer quads for a single texture */
  private static List<BakedQuad> bakeQuads(TextureAtlasSprite sprite, ModelState state, ContextMap additionalProperties) {
    Transformation rootTransform = additionalProperties.getOrDefault(NeoForgeModelProperties.TRANSFORM, Transformation.identity());
    Transformation transform = MantleItemLayerModel.applyTransform(state, rootTransform).transformation();
    return MantleItemLayerModel.getQuadsForSprite(-1, -1, sprite, transform, 0);
  }

  /**
   * Finds the NBT key model in a resolved model chain
   * @param model  Resolved model
   * @return  NBT key model, or null if the chain has none
   */
  @Nullable
  public static NBTKeyModel find(@Nullable ResolvedModel model) {
    for (; model != null; model = model.parent()) {
      if (model.wrapped() instanceof NBTKeyModel nbtKey) {
        return nbtKey;
      }
    }
    return null;
  }

  /**
   * Gets all variant textures for this model: every texture defined along the resolved chain, then the registered extras.
   * @param resolved  Resolved model using this model
   * @return  Map of variant name to texture
   */
  public Map<String,Material> getTextures(ResolvedModel resolved) {
    TextureSlots slots = resolved.getTopTextureSlots();
    // texture slot data keys are readable, so collect all names defined along the chain
    Set<String> names = ModelHelper.getTextureNames(resolved);
    names.remove("particle");
    Map<String,Material> textures = new HashMap<>();
    for (String name : names) {
      Material material = slots.getMaterial(name);
      if (material != null) {
        textures.put(name, material);
      }
    }
    // fetch extra textures
    for (Pair<String,ResourceLocation> extra : getExtraTextures()) {
      textures.putIfAbsent(extra.getFirst(), new Material(TextureAtlas.LOCATION_BLOCKS, extra.getSecond()));
    }
    return Map.copyOf(textures);
  }

  /** Deserializes this model from JSON */
  public static NBTKeyModel deserialize(JsonObject json, JsonDeserializationContext context) {
    StandardModelParameters parameters = StandardModelParameters.parse(json, context);
    String key = GsonHelper.getAsString(json, "nbt_key");
    ResourceLocation extraTexturesKey = null;
    if (json.has("extra_textures_key")) {
      extraTexturesKey = JsonHelper.getResourceLocation(json, "extra_textures_key");
    }
    return new NBTKeyModel(parameters, key, extraTexturesKey);
  }

  /**
   * Item model selecting the texture variant from the stack's custom data, registered as {@code mantle:nbt_key}.
   * The item JSON is {@code {"model":{"type":"mantle:nbt_key","model":"<nbt key model>"}}}.
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
      NBTKeyModel nbtModel = find(resolved);
      if (nbtModel == null) {
        Mantle.logger.warn("Item model type mantle:nbt_key used with {}, which is not an NBT key model", resolved.debugName());
        return new BlockModelWrapper.Unbaked(model, List.of()).bake(context);
      }
      TextureSlots slots = resolved.getTopTextureSlots();
      ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, resolved, slots);
      ContextMap additionalProperties = resolved.getTopAdditionalProperties();
      RenderType renderType = MantleItemLayerModel.getDefaultRenderType(additionalProperties.getOrDefault(NeoForgeModelProperties.RENDER_TYPE, RenderTypeGroup.EMPTY)).entity();

      // bake all variants now, the textures are all known
      Map<String,Material> textures = nbtModel.getTextures(resolved);
      Map<String,Variant> variants = new HashMap<>(textures.size());
      for (Entry<String,Material> entry : textures.entrySet()) {
        TextureAtlasSprite sprite = baker.sprites().get(entry.getValue(), resolved);
        variants.put(entry.getKey(), new Variant(bakeQuads(sprite, BlockModelRotation.X0_Y0, additionalProperties), new ModelRenderProperties(properties.usesBlockLight(), sprite, properties.transforms())));
      }
      Variant defaultVariant = variants.get("default");
      if (defaultVariant == null) {
        defaultVariant = new Variant(resolved.bakeTopGeometry(slots, baker, BlockModelRotation.X0_Y0).getAll(), properties);
      }
      return new Baked(nbtModel.nbtKey, textures, Map.copyOf(variants), defaultVariant, renderType);
    }
  }

  /** Quads and properties for a single variant, with lazily computed extents */
  private record Variant(List<BakedQuad> quads, ModelRenderProperties properties, Supplier<Vector3f[]> extents) {
    private Variant(List<BakedQuad> quads, ModelRenderProperties properties) {
      this(quads, properties, Suppliers.memoize(() -> BlockModelWrapper.computeExtents(quads)));
    }
  }

  /** Baked item model for an NBT key model, replaces the old {@code NBTKeyModel.Overrides} */
  public static class Baked implements ItemModel {
    private final String nbtKey;
    private final Map<String,Material> textures;
    private final Map<String,Variant> variants;
    private final Variant defaultVariant;
    private final RenderType renderType;

    private Baked(String nbtKey, Map<String,Material> textures, Map<String,Variant> variants, Variant defaultVariant, RenderType renderType) {
      this.nbtKey = nbtKey;
      this.textures = textures;
      this.variants = variants;
      this.defaultVariant = defaultVariant;
      this.renderType = renderType;
    }

    /**
     * Gets the given texture from the model, falling back to the default texture.
     * Look up the baked model with {@code ModelManager#getItemModel(itemModelId)} to use this, e.g. for JEI.
     */
    @Nullable
    public Material getTexture(String name) {
      Material texture = textures.get(name);
      return texture != null ? texture : textures.get("default");
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
      Variant variant = defaultVariant;
      CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
      if (data.contains(nbtKey)) {
        variant = data.copyTag().getString(nbtKey).map(variants::get).orElse(defaultVariant);
      }
      ModelHelper.addItemLayer(renderState, stack, displayContext, variant.quads, variant.properties, renderType, variant.extents);
    }
  }
}
