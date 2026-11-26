package slimeknights.mantle.client.model.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.MatrixUtil;
import com.mojang.math.Quadrant;
import com.mojang.math.Transformation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.core.BlockMath;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import slimeknights.mantle.Mantle;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Utilities to help in custom models
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelHelper {
  private static final Map<Block,Identifier> TEXTURE_NAME_CACHE = new ConcurrentHashMap<>();
  /** Models already warned about, keyed by their model identity (the extents supplier); cleared on resource reload */
  private static final Set<Object> WARNED_MODELS = ConcurrentHashMap.newKeySet();

  /** Listener instance to clear cache */
  public static final ResourceManagerReloadListener LISTENER = manager -> {
    TEXTURE_NAME_CACHE.clear();
    WARNED_MODELS.clear();
  };

  /**
   * Gets the texture name for a block from the model manager
   * @param block  Block to fetch
   * @return Texture name for the block
   */
  @SuppressWarnings("deprecation")
  private static Identifier getParticleTextureInternal(Block block) {
    // 1.21.5: BlockStateModel#particleIcon() is the context-free particle (deprecated by NeoForge in favor of the level-aware overload)
    TextureAtlasSprite particle = Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(block.defaultBlockState()).particleIcon();
    //noinspection ConstantConditions  dumb mods returning null particle icons
    if (particle != null) {
      return particle.contents().name();
    }
    return MissingTextureAtlasSprite.getLocation();
  }

  /**
   * Gets the name of a particle texture for a block, using the cached value if present
   * @param block Block to fetch
   * @return Texture name for the block
   */
  public static Identifier getParticleTexture(Block block) {
    return TEXTURE_NAME_CACHE.computeIfAbsent(block, ModelHelper::getParticleTextureInternal);
  }

  /* Texture references */

  /** Atlas used for the probe materials in {@link #findReferences(ResolvedModel, Collection, Collection)}, never a real atlas */
  private static final Identifier REFERENCE_PROBE = Identifier.fromNamespaceAndPath("mantle", "reference_probe");

  /** Gets all texture names defined along the resolved model chain */
  public static Set<String> getTextureNames(ResolvedModel model) {
    Set<String> names = new LinkedHashSet<>();
    for (; model != null; model = model.parent()) {
      names.addAll(model.wrapped().textureSlots().values().keySet());
    }
    return names;
  }

  /**
   * Finds which of the target texture names each of the given names resolves to through {@code #reference} chains along
   * the whole resolved model chain. Vanilla reference contents ({@code TextureSlots.Reference}) are package-private in
   * 1.21.5, so this resolves the chain with each target replaced by a unique probe material: any name resolving to a
   * probe references that target. Resolution stops at the first target in a chain, matching how Mantle models followed
   * references by name before.
   * @param model    Resolved model
   * @param targets  Texture names to look for, without {@code '#'}
   * @param names    Texture names to check, with or without {@code '#'}
   * @return  Map from each name (without {@code '#'}) that references a target to that target. Targets map to themselves.
   */
  public static Map<String,String> findReferences(ResolvedModel model, Collection<String> targets, Collection<String> names) {
    if (targets.isEmpty() || names.isEmpty()) {
      return Map.of();
    }
    TextureSlots.Resolver resolver = new TextureSlots.Resolver();
    for (ResolvedModel parent = model; parent != null; parent = parent.parent()) {
      resolver.addLast(parent.wrapped().textureSlots());
    }
    // probes have the highest priority so they replace whatever the targets resolved to
    TextureSlots.Data.Builder probes = new TextureSlots.Data.Builder();
    Map<SpriteId,String> probeTargets = new HashMap<>();
    int index = 0;
    for (String target : targets) {
      SpriteId probe = new SpriteId(REFERENCE_PROBE, Identifier.fromNamespaceAndPath("mantle", "probe_" + index));
      index++;
      probes.addTexture(target, probe);
      probeTargets.put(probe, target);
    }
    TextureSlots resolved = resolver.addFirst(probes.build()).resolve(model);
    Map<String,String> references = new HashMap<>();
    for (String name : names) {
      SpriteId material = resolved.getMaterial(name);
      if (material != null) {
        String target = probeTargets.get(material);
        if (target != null) {
          references.put(!name.isEmpty() && name.charAt(0) == '#' ? name.substring(1) : name, target);
        }
      }
    }
    return Map.copyOf(references);
  }


  /* Item models */

  /**
   * Adds the quads to an item render state, matching vanilla {@code CuboidItemModelWrapper#update}. Replaces the
   * 1.21.4 {@code LayerRenderState#setupBlockModel(BakedModel, RenderType)} for dynamic item models.
   * <p>
   * 1.21.11: blocks and items have separate atlases and a layer binds a single one, so quads mixing both atlases are split
   * into one layer per atlas (in order of first appearance, keeping the quad order within each atlas), each with the render
   * type for its atlas, see {@link #addItemLayers}. An explicit {@code renderType} binds a single atlas and cannot be split:
   * mixed quads then log a warning once per model. Prefer {@link #addItemLayers} with the model's {@link RenderTypeGroup}.
   * @param renderState     Render state to fill
   * @param stack           Stack being rendered, for the foil and default render type
   * @param displayContext  Display context, selects the item transform
   * @param quads           Quads to render
   * @param properties      Item render properties (gui light, particle, transforms)
   * @param renderType      Render type for the layer, null to use the stack's default for the atlas of the quads
   * @param extents         Model extents, see {@link CuboidItemModelWrapper#computeExtents(List)}. Must be memoized and created
   *                        once per cached variant: it doubles as the variant's model identity (see below).
   * @param animated        Whether any quad uses an animated sprite, see {@link #hasAnimatedSprite(List)}; compute once per variant
   * @return  The added layer (the first one if the quads were split per atlas), for further customization such as tint layers
   */
  public static ItemStackRenderState.LayerRenderState addItemLayer(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, List<BakedQuad> quads,
                                                                   ModelRenderProperties properties, @Nullable RenderType renderType, Supplier<Vector3fc[]> extents, boolean animated) {
    if (renderType == null) {
      return addItemLayers(renderState, stack, displayContext, quads, properties, null, extents, animated).getFirst();
    }
    List<Identifier> atlases = findAtlases(quads);
    if (atlases.size() > 1) {
      warnOnce(extents, "Item quads mix the atlases {} but are rendered with the single render type {}, so some quads sample the wrong atlas. Pass the model's render type group instead.",
               atlases, renderType);
    }
    appendModelIdentity(renderState, stack, extents, animated);
    return addLayer(renderState, stack, displayContext, quads, properties, renderType, extents);
  }

  /**
   * Adds the quads to an item render state, one layer per atlas used by their sprites (in order of first appearance, keeping
   * the quad order within each atlas). Each layer uses the render type for its atlas: from the group if set
   * ({@link #getEntityRenderType(RenderTypeGroup, List)}), else the stack's default ({@link #getDefaultItemRenderType(ItemStack, List)}).
   * See {@link #addItemLayer} for the other parameters; the model identity is appended once.
   * @param renderTypes  Render type group from the model's {@code render_type}, null to use the stack's defaults
   * @return  The added layers, never empty
   */
  public static List<ItemStackRenderState.LayerRenderState> addItemLayers(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, List<BakedQuad> quads,
                                                                         ModelRenderProperties properties, @Nullable RenderTypeGroup renderTypes, Supplier<Vector3fc[]> extents, boolean animated) {
    appendModelIdentity(renderState, stack, extents, animated);
    // common case: a single atlas, no need to copy the quads
    if (findAtlases(quads).size() <= 1) {
      return List.of(addLayer(renderState, stack, displayContext, quads, properties, getItemRenderType(stack, quads, renderTypes, extents), extents));
    }
    Map<Identifier,List<BakedQuad>> byAtlas = new LinkedHashMap<>();
    for (BakedQuad quad : quads) {
      byAtlas.computeIfAbsent(quad.sprite().atlasLocation(), atlas -> new ArrayList<>()).add(quad);
    }
    List<ItemStackRenderState.LayerRenderState> layers = new ArrayList<>(byAtlas.size());
    for (List<BakedQuad> atlasQuads : byAtlas.values()) {
      layers.add(addLayer(renderState, stack, displayContext, atlasQuads, properties, getItemRenderType(stack, atlasQuads, renderTypes, extents), extents));
    }
    return layers;
  }

  /** Appends the model identity, foil and animation state for the item, shared by every layer of the model */
  private static void appendModelIdentity(ItemStackRenderState renderState, ItemStack stack, Supplier<Vector3fc[]> extents, boolean animated) {
    // 1.21.6+: GUI items are drawn once into an atlas keyed by the render state's model identity, so every distinct
    // look must append a distinct element (vanilla appends the CuboidItemModelWrapper itself). Without one, all dynamic items
    // share the empty identity and show whichever was drawn first. The memoized extents supplier is unique per variant
    // and compares by identity, so it is a cheap stable key.
    renderState.appendModelIdentityElement(extents);
    if (stack.hasFoil()) {
      // glint animates, so re-render the atlas slot every frame like vanilla
      renderState.setAnimated();
      renderState.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);
    }
    // animated sprites must re-render their atlas slot every frame too
    if (animated) {
      renderState.setAnimated();
    }
  }

  /** Adds a single layer with the given quads and render type */
  private static ItemStackRenderState.LayerRenderState addLayer(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, List<BakedQuad> quads,
                                                                ModelRenderProperties properties, RenderType renderType, Supplier<Vector3fc[]> extents) {
    ItemStackRenderState.LayerRenderState layer = renderState.newLayer();
    if (stack.hasFoil()) {
      layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
    }
    layer.setExtents(extents);
    layer.setRenderType(renderType);
    properties.applyToLayer(layer, displayContext);
    layer.prepareQuadList().addAll(quads);
    return layer;
  }

  /** Gets the render type for quads from a single atlas, warning once per model if item render types cannot bind the atlas */
  private static RenderType getItemRenderType(ItemStack stack, List<BakedQuad> quads, @Nullable RenderTypeGroup renderTypes, Object model) {
    if (!quads.isEmpty()) {
      Identifier atlas = quads.getFirst().sprite().atlasLocation();
      if (!atlas.equals(TextureAtlas.LOCATION_BLOCKS) && !atlas.equals(TextureAtlas.LOCATION_ITEMS)) {
        warnOnce(model, "Item quads use the atlas {}, which item render types cannot bind; rendering them with the items atlas", atlas);
      }
    }
    return renderTypes != null ? getEntityRenderType(renderTypes, quads) : getDefaultItemRenderType(stack, quads);
  }

  /** Gets the distinct atlases of the quads' sprites, in order of first appearance. Does not allocate for a single atlas. */
  private static List<Identifier> findAtlases(List<BakedQuad> quads) {
    if (quads.isEmpty()) {
      return List.of();
    }
    Identifier first = quads.getFirst().sprite().atlasLocation();
    List<Identifier> atlases = null;
    for (BakedQuad quad : quads) {
      Identifier atlas = quad.sprite().atlasLocation();
      if (!atlas.equals(first) && (atlases == null || !atlases.contains(atlas))) {
        if (atlases == null) {
          atlases = new ArrayList<>(2);
          atlases.add(first);
        }
        atlases.add(atlas);
      }
    }
    return atlases == null ? List.of(first) : atlases;
  }

  /** Logs a warning once per model */
  private static void warnOnce(Object model, String message, Object... args) {
    if (WARNED_MODELS.add(model)) {
      Mantle.logger.warn(message, args);
    }
  }

  /**
   * Gets the default render type for item quads from a single atlas, matching vanilla {@code CuboidItemModelWrapper#detectRenderType}:
   * quads from the items atlas (or no quads) use {@link Sheets#translucentItemSheet()}, quads from the blocks atlas use
   * {@link Sheets#cutoutBlockSheet()} for non-translucent block items and {@link Sheets#translucentBlockItemSheet()} otherwise.
   * 1.21.11: replaces {@code ItemBlockRenderTypes.getRenderType(ItemStack)}; item textures moved to their own atlas, so the
   * render type must bind the atlas the sprites came from. The first quad decides; split mixed quads with {@link #addItemLayers}.
   */
  public static RenderType getDefaultItemRenderType(ItemStack stack, List<BakedQuad> quads) {
    if (quads.isEmpty() || !quads.getFirst().sprite().atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS)) {
      return Sheets.translucentItemSheet();
    }
    if (stack.getItem() instanceof BlockItem blockItem) {
      @SuppressWarnings("deprecation")  // same call as vanilla CuboidItemModelWrapper
      ChunkSectionLayer layer = ItemBlockRenderTypes.getChunkRenderType(blockItem.getBlock().defaultBlockState());
      if (layer != ChunkSectionLayer.TRANSLUCENT) {
        return Sheets.cutoutBlockSheet();
      }
    }
    return Sheets.translucentBlockItemSheet();
  }

  /**
   * Picks the entity render type of a render type group for item quads from a single atlas, matching NeoForge's
   * {@code RenderTypeHelper#detectItemModelRenderType}: blocks atlas uses {@link RenderTypeGroup#entityBlock()},
   * anything else (items atlas, no quads) uses {@link RenderTypeGroup#entityItem()}. Replaces the 1.21.10 {@code RenderTypeGroup#entity()}.
   * The first quad decides; split mixed quads with {@link #addItemLayers}.
   * @param group  Render type group, must not be empty
   * @param quads  Quads that will be rendered with the type
   * @return  Entity render type binding the atlas of the quads
   */
  public static RenderType getEntityRenderType(RenderTypeGroup group, List<BakedQuad> quads) {
    if (!quads.isEmpty() && quads.getFirst().sprite().atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS)) {
      return group.entityBlock();
    }
    return group.entityItem();
  }


  /* Quads */

  /**
   * Part cache for {@link net.minecraft.client.resources.model.cuboid.FaceBakery#bakeQuad} outside the model bakery, which
   * only exists during baking. The bakery's cache interns positions to save memory; this one keeps them as baked, so the
   * quads are identical either way.
   */
  public static final ModelBaker.PartCache UNCACHED_PARTS = vector -> vector;

  /**
   * Copies a quad with new baked colors. Replaces writing the vertex colors through NeoForge's removed {@code IQuadTransformer}.
   * @param quad    Quad to copy
   * @param colors  Colors in ARGB format, multiplied with the tint at render time
   * @return  Quad with the given colors
   */
  public static BakedQuad withColors(BakedQuad quad, BakedColors colors) {
    if (quad.bakedColors().equals(colors)) {
      return quad;
    }
    return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                         quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
                         quad.tintIndex(), quad.direction(), quad.sprite(), quad.shade(), quad.lightEmission(),
                         quad.bakedNormals(), colors, quad.hasAmbientOcclusion());
  }


  /* Sprites */

  /**
   * Gets a sprite for a material at runtime, outside the model bake. Unlike {@link AtlasManager#get(SpriteId)}, supports
   * {@link ModelManager#BLOCK_OR_ITEM}, the atlas vanilla assigns to every texture parsed from a model JSON since 1.21.11:
   * like the model bakery's sprite getter, the items atlas is checked first, then the blocks atlas (missing sprite if neither).
   * @param material  SpriteId to fetch
   * @return  Sprite, or the missing sprite of the atlas
   */
  public static TextureAtlasSprite getSprite(SpriteId material) {
    AtlasManager atlases = Minecraft.getInstance().getAtlasManager();
    if (material.atlasLocation().equals(ModelManager.BLOCK_OR_ITEM)) {
      TextureAtlasSprite sprite = atlases.getAtlasOrThrow(AtlasIds.ITEMS).getTextures().get(material.texture());
      if (sprite != null) {
        return sprite;
      }
      return atlases.getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(material.texture());
    }
    return atlases.get(material);
  }

  /** Checks if any of the quads uses an animated sprite, matching vanilla's {@code CuboidItemModelWrapper} constructor. Call once per baked variant, not per render. */
  public static boolean hasAnimatedSprite(List<BakedQuad> quads) {
    for (BakedQuad quad : quads) {
      if (quad.sprite().contents().isAnimated()) {
        return true;
      }
    }
    return false;
  }


  /* Model states */

  /**
   * Checks if the given model state locks UVs. In 1.21.5 {@code ModelState#isUvLocked} is gone; UV lock is expressed as
   * per-face UV transforms, so a state is UV locked if any face transform is not the identity. Identity rotations report
   * false, which is harmless as UV lock has no effect on them.
   */
  public static boolean isUvLocked(ModelState state) {
    for (Direction direction : Direction.values()) {
      if (!MatrixUtil.isIdentity(state.faceTransformation(direction))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets a model state with the same transformation as the given state, but with UV lock forced on or off.
   * Used for per-element UV lock overrides, replacing the removed NeoForge {@code SimpleModelState(transform, uvlock)}.
   * @param state   Original model state
   * @param uvlock  Whether to lock UVs
   * @return  Model state with the requested UV lock, or the original state if it already matches
   */
  public static ModelState withUvLock(ModelState state, boolean uvlock) {
    if (isUvLocked(state) == uvlock) {
      return state;
    }
    // vanilla rotations have a cached UV lock variant, keep using those so FaceBakery still recalculates winding
    if (state instanceof BlockModelRotation rotation) {
      return uvlock ? rotation.withUvLock() : rotation;
    }
    if (state instanceof BlockModelRotation.WithUvLock locked) {
      return uvlock ? locked : locked.parent();
    }
    return modelState(state.transformation(), uvlock, state.mayApplyArbitraryRotation());
  }

  /**
   * Creates a model state from a transformation and UV lock, matching the removed NeoForge {@code SimpleModelState}.
   * Returns the vanilla {@link BlockModelRotation} when the transformation is one of the 90 degree rotations.
   */
  public static ModelState modelState(Transformation transformation, boolean uvlock) {
    // 1.21.11: BlockModelRotation is no longer an enum; check the same 16 X/Y rotations the enum had
    for (Quadrant x : Quadrant.values()) {
      for (Quadrant y : Quadrant.values()) {
        BlockModelRotation rotation = BlockModelRotation.get(Quadrant.fromXYAngles(x, y));
        if (rotation.transformation().equals(transformation)) {
          return uvlock ? rotation.withUvLock() : rotation;
        }
      }
    }
    return modelState(transformation, uvlock, true);
  }

  /** Creates a model state, computing UV lock face transforms like {@link BlockModelRotation} if requested */
  private static ModelState modelState(Transformation transformation, boolean uvlock, boolean arbitraryRotation) {
    if (!uvlock) {
      return new TransformModelState(transformation, Map.of(), Map.of(), arbitraryRotation);
    }
    Map<Direction,Matrix4fc> faces = new EnumMap<>(Direction.class);
    Map<Direction,Matrix4fc> inverse = new EnumMap<>(Direction.class);
    for (Direction direction : Direction.values()) {
      Matrix4fc matrix = BlockMath.getFaceTransformation(transformation, direction).getMatrix();
      faces.put(direction, matrix);
      inverse.put(direction, matrix.invertAffine(new Matrix4f()));
    }
    return new TransformModelState(transformation, faces, inverse, arbitraryRotation);
  }

  /** Model state with a fixed transformation and optional UV lock face transforms */
  private record TransformModelState(Transformation transformation, Map<Direction,Matrix4fc> faces, Map<Direction,Matrix4fc> inverseFaces, boolean arbitraryRotation) implements ModelState {
    @Override
    public Matrix4fc faceTransformation(Direction facing) {
      return faces.getOrDefault(facing, NO_TRANSFORM);
    }

    @Override
    public Matrix4fc inverseFaceTransformation(Direction facing) {
      return inverseFaces.getOrDefault(facing, NO_TRANSFORM);
    }

    @Override
    public boolean mayApplyArbitraryRotation() {
      return arbitraryRotation;
    }
  }


  /* JSON */

  /**
   * Converts a JSON float array to the specified object
   * @param json    JSON object
   * @param name    Name of the array in the object to fetch
   * @param size    Expected array size
   * @param mapper  Functon to map from the array to the output type
   * @param <T> Output type
   * @return  Vector3f of data
   * @throws JsonParseException  If there is no array or the length is wrong
   * @deprecated use {@link slimeknights.mantle.data.loadable.array.FloatArrayLoadable}
   */
  @Deprecated(forRemoval = true)
  public static <T> T arrayToObject(JsonObject json, String name, int size, Function<float[], T> mapper) {
    JsonArray array = GsonHelper.getAsJsonArray(json, name);
    if (array.size() != size) {
      throw new JsonParseException("Expected " + size + " " + name + " values, found: " + array.size());
    }
    float[] vec = new float[size];
    for(int i = 0; i < size; ++i) {
      vec[i] = GsonHelper.convertToFloat(array.get(i), name + "[" + i + "]");
    }
    return mapper.apply(vec);
  }

  /**
   * Converts a JSON array with 3 elements into a Vector3f
   * @param json  JSON object
   * @param name  Name of the array in the object to fetch
   * @return  Vector3f of data
   * @throws JsonParseException  If there is no array or the length is wrong
   * @deprecated use {@link slimeknights.mantle.data.loadable.common.Vector3fLoadable}
   */
  @Deprecated(forRemoval = true)
  public static Vector3f arrayToVector(JsonObject json, String name) {
    return arrayToObject(json, name, 3, arr -> new Vector3f(arr[0], arr[1], arr[2]));
  }

  /** @deprecated use {@link slimeknights.mantle.data.loadable.common.Vector3fLoadable} */
  @Deprecated(forRemoval = true)
  public static JsonArray vectorToJson(Vector3f vector) {
    JsonArray array = new JsonArray();
    array.add(vector.x());
    array.add(vector.y());
    array.add(vector.z());
    return array;
  }

  /** Validates the given rotation is in 90 degree increments */
  public static boolean checkRotation(float rotation) {
    return rotation >= 0 && rotation % 90 == 0 && rotation <= 270;
  }

  /**
   * Gets a rotation from JSON
   * @param json  JSON parent
   * @return  Integer of 0, 90, 180, or 270
   */
  public static int getRotation(JsonObject json, String key) {
    int i = GsonHelper.getAsInt(json, key, 0);
    if (checkRotation(i)) {
      return i;
    } else {
      throw new JsonParseException("Invalid '" + key + "' " + i + " found, only 0/90/180/270 allowed");
    }
  }
}
