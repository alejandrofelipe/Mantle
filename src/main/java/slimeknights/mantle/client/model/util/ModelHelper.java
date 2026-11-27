package slimeknights.mantle.client.model.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.MatrixUtil;
import com.mojang.math.Quadrant;
import com.mojang.math.Transformation;
import com.mojang.blaze3d.platform.Transparency;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.CuboidRotation;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.BlockMath;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
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

  /** Listener instance to clear cache */
  public static final ResourceManagerReloadListener LISTENER = manager -> TEXTURE_NAME_CACHE.clear();

  /**
   * Gets the texture name for a block from the model manager
   * @param block  Block to fetch
   * @return Texture name for the block
   */
  private static Identifier getParticleTextureInternal(Block block) {
    // 26.1: block models live in the model manager's block state model set; the context-free particle uses an empty level
    Material.Baked particle = Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(block.defaultBlockState());
    //noinspection ConstantConditions  dumb mods returning null particle icons
    if (particle != null && particle.sprite() != null) {
      return particle.sprite().contents().name();
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
    Map<Material,String> probeTargets = new HashMap<>();
    int index = 0;
    for (String target : targets) {
      // never a real texture
      Material probe = new Material(Identifier.fromNamespaceAndPath("mantle", "reference_probe/probe_" + index));
      index++;
      probes.addTexture(target, probe);
      probeTargets.put(probe, target);
    }
    TextureSlots resolved = resolver.addFirst(probes.build()).resolve(model);
    Map<String,String> references = new HashMap<>();
    for (String name : names) {
      Material material = resolved.getMaterial(name);
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
   * Adds the quads to an item render state as a single layer, matching vanilla {@code CuboidItemModelWrapper#update}. Replaces the
   * 1.21.4 {@code LayerRenderState#setupBlockModel(BakedModel, RenderType)} for dynamic item models.
   * <p>
   * 26.1: every quad carries its own item render type ({@link BakedQuad.MaterialInfo#itemRenderType()}), so a layer may mix the blocks and items
   * atlases and has no render type: the 1.21.11 per-atlas layer split and the render type (group) parameter are gone. Pick the render type when
   * baking the quads instead.
   * @param renderState     Render state to fill
   * @param stack           Stack being rendered, for the foil
   * @param displayContext  Display context, selects the item transform
   * @param quads           Quads to render
   * @param properties      Item render properties (gui light, particle, transforms)
   * @param transformation  Transformation the item model was baked with ({@link ItemModel.Unbaked#bake(ItemModel.BakingContext, Matrix4fc)}),
   *                        applied as the layer's local transform like vanilla
   * @param extents         Model extents, see {@link CuboidItemModelWrapper#computeExtents(List)}. Must be memoized and created
   *                        once per cached variant: it doubles as the variant's model identity (see below).
   * @param animated        Whether any quad uses an animated sprite, see {@link #hasAnimatedSprite(List)}; compute once per variant
   * @return  The added layer, for further customization such as tint layers
   */
  public static ItemStackRenderState.LayerRenderState addItemLayer(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, List<BakedQuad> quads,
                                                                   ModelRenderProperties properties, Matrix4fc transformation, Supplier<Vector3fc[]> extents, boolean animated) {
    // 1.21.6+: GUI items are drawn once into an atlas keyed by the render state's model identity, so every distinct
    // look must append a distinct element (vanilla appends the CuboidItemModelWrapper itself). Without one, all dynamic items
    // share the empty identity and show whichever was drawn first. The memoized extents supplier is unique per variant
    // and compares by identity, so it is a cheap stable key.
    renderState.appendModelIdentityElement(extents);
    ItemStackRenderState.LayerRenderState layer = renderState.newLayer();
    if (stack.hasFoil()) {
      layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
      // glint animates, so re-render the atlas slot every frame like vanilla
      renderState.setAnimated();
      renderState.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);
    }
    // animated sprites must re-render their atlas slot every frame too
    if (animated) {
      renderState.setAnimated();
    }
    layer.setExtents(extents);
    layer.setLocalTransform(transformation);
    properties.applyToLayer(layer, displayContext);
    layer.prepareQuadList().addAll(quads);
    return layer;
  }

  /**
   * Creates item render properties with the given particle sprite.
   * 26.1: {@link ModelRenderProperties} holds a {@link Material.Baked} particle.
   */
  public static ModelRenderProperties itemProperties(ModelRenderProperties properties, TextureAtlasSprite particle) {
    return new ModelRenderProperties(properties.usesBlockLight(), new Material.Baked(particle, false), properties.transforms());
  }


  /* Quads */

  /**
   * Interner for {@link FaceBakery} outside the model bakery, which only exists during baking. The bakery's interner shares positions and
   * material infos to save memory; this one keeps them as baked, so the quads are identical either way.
   * 26.1: replaces the 1.21.11 {@code UNCACHED_PARTS} ({@code ModelBaker.PartCache} became {@link ModelBaker.Interner}).
   */
  public static final ModelBaker.Interner UNCACHED_INTERNER = new ModelBaker.Interner() {
    @Override
    public Vector3fc vector(Vector3fc vector) {
      return vector;
    }

    @Override
    public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) {
      return material;
    }
  };

  /**
   * Bakes an element face outside the model bakery, matching vanilla {@code FaceBakery#bakeQuad(ModelBaker, ...)}.
   * 26.1: the layer and item render type of the quad come from the transparency of the face's UV region of the sprite, or translucent if the
   * material forces it ({@link BakedQuad.MaterialInfo#of}), replacing the model's 1.21.11 {@code render_type}.
   * @param from           Element start
   * @param to             Element end
   * @param face           Face data
   * @param material       Material for the face, wrap a runtime sprite with {@code new Material.Baked(sprite, false)}
   * @param facing         Direction of the face
   * @param state          Model state, including UV lock
   * @param rotation       Element rotation
   * @param shade          If true, shades the face
   * @param lightEmission  Light emission of the element, the face's own emission wins if larger
   * @return  Baked quad
   */
  public static BakedQuad bakeQuad(Vector3fc from, Vector3fc to, CuboidFace face, Material.Baked material, Direction facing, ModelState state,
                                   @Nullable CuboidRotation rotation, boolean shade, int lightEmission) {
    CuboidFace.UVs uvs = face.uvs();
    if (uvs == null) {
      uvs = FaceBakery.defaultFaceUV(from, to, facing);
    }
    ExtraFaceData faceData = face.faceData();
    BakedQuad.MaterialInfo materialInfo = BakedQuad.MaterialInfo.of(material, getTransparency(material, uvs), face.tintIndex(), shade,
                                                                     Math.max(lightEmission, faceData.lightEmission()), faceData.ambientOcclusion());
    return FaceBakery.bakeQuad(UNCACHED_INTERNER, from, to, uvs, face.rotation(), materialInfo, facing, state, rotation, faceData);
  }

  /** Gets the transparency of a face, matching the private {@code FaceBakery#computeMaterialTransparency} */
  private static Transparency getTransparency(Material.Baked material, CuboidFace.UVs uvs) {
    if (material.forceTranslucent()) {
      return Transparency.TRANSLUCENT;
    }
    return material.sprite().contents().computeTransparency(
      Math.min(uvs.minU(), uvs.maxU()) / 16f, Math.min(uvs.minV(), uvs.maxV()) / 16f,
      Math.max(uvs.minU(), uvs.maxU()) / 16f, Math.max(uvs.minV(), uvs.maxV()) / 16f);
  }

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
                         quad.direction(), quad.materialInfo(), quad.bakedNormals(), colors);
  }


  /* Sprites */

  /**
   * Gets a sprite for a sprite ID at runtime, outside the model bake.
   * 26.1: model textures no longer name an atlas (they are {@link Material}s, see {@link #getSprite(Material)}), so the 1.21.11 {@code block_or_item}
   * pseudo atlas is gone and every sprite ID names a real atlas.
   * @param sprite  Sprite ID to fetch
   * @return  Sprite, or the missing sprite of the atlas
   */
  public static TextureAtlasSprite getSprite(SpriteId sprite) {
    return Minecraft.getInstance().getAtlasManager().get(sprite);
  }

  /**
   * Gets the sprite for a model texture at runtime, outside the model bake. Like the model bakery's material baker, the blocks atlas is checked
   * first, then the items atlas, falling back to the missing sprite of the blocks atlas.
   * @param material  Model texture, typically from {@link TextureSlots#getMaterial(String)}
   * @return  Sprite
   */
  public static TextureAtlasSprite getSprite(Material material) {
    AtlasManager atlases = Minecraft.getInstance().getAtlasManager();
    TextureAtlas blocks = atlases.getAtlasOrThrow(AtlasIds.BLOCKS);
    TextureAtlasSprite sprite = blocks.getTextures().get(material.sprite());
    if (sprite != null) {
      return sprite;
    }
    sprite = atlases.getAtlasOrThrow(AtlasIds.ITEMS).getTextures().get(material.sprite());
    return sprite != null ? sprite : blocks.missingSprite();
  }

  /** Checks if any of the quads uses an animated sprite, matching vanilla's {@code CuboidItemModelWrapper} constructor. Call once per baked variant, not per render. */
  public static boolean hasAnimatedSprite(List<BakedQuad> quads) {
    for (BakedQuad quad : quads) {
      if (quad.materialInfo().sprite().contents().isAnimated()) {
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
