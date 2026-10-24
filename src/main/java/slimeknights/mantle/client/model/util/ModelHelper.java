package slimeknights.mantle.client.model.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.MatrixUtil;
import com.mojang.math.Transformation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.core.BlockMath;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import javax.annotation.Nullable;
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
  private static final Map<Block,ResourceLocation> TEXTURE_NAME_CACHE = new ConcurrentHashMap<>();
  /** Listener instance to clear cache */
  public static final ResourceManagerReloadListener LISTENER = manager -> TEXTURE_NAME_CACHE.clear();

  /**
   * Gets the texture name for a block from the model manager
   * @param block  Block to fetch
   * @return Texture name for the block
   */
  @SuppressWarnings("deprecation")
  private static ResourceLocation getParticleTextureInternal(Block block) {
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
  public static ResourceLocation getParticleTexture(Block block) {
    return TEXTURE_NAME_CACHE.computeIfAbsent(block, ModelHelper::getParticleTextureInternal);
  }

  /* Texture references */

  /** Atlas used for the probe materials in {@link #findReferences(ResolvedModel, Collection, Collection)}, never a real atlas */
  private static final ResourceLocation REFERENCE_PROBE = ResourceLocation.fromNamespaceAndPath("mantle", "reference_probe");

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
      Material probe = new Material(REFERENCE_PROBE, ResourceLocation.fromNamespaceAndPath("mantle", "probe_" + index));
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
   * Adds a layer of quads to an item render state, matching vanilla {@code BlockModelWrapper#update}. Replaces the
   * 1.21.4 {@code LayerRenderState#setupBlockModel(BakedModel, RenderType)} for dynamic item models.
   * @param renderState     Render state to fill
   * @param stack           Stack being rendered, for the foil and default render type
   * @param displayContext  Display context, selects the item transform
   * @param quads           Quads to render
   * @param properties      Item render properties (gui light, particle, transforms)
   * @param renderType      Render type for the layer, null to use the stack's default
   * @param extents         Model extents, see {@link BlockModelWrapper#computeExtents(List)}; should be memoized
   * @return  The added layer, for further customization such as tint layers
   */
  public static ItemStackRenderState.LayerRenderState addItemLayer(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, List<BakedQuad> quads,
                                                                   ModelRenderProperties properties, @Nullable RenderType renderType, Supplier<Vector3f[]> extents) {
    ItemStackRenderState.LayerRenderState layer = renderState.newLayer();
    if (stack.hasFoil()) {
      layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
    }
    layer.setExtents(extents);
    layer.setRenderType(renderType != null ? renderType : ItemBlockRenderTypes.getRenderType(stack));
    properties.applyToLayer(layer, displayContext);
    layer.prepareQuadList().addAll(quads);
    return layer;
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
    for (BlockModelRotation rotation : BlockModelRotation.values()) {
      if (rotation.transformation().equals(transformation)) {
        return uvlock ? rotation.withUvLock() : rotation;
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
