package slimeknights.mantle.client.model.connected;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.block.model.SingleVariant;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.SpriteGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.apache.commons.lang3.mutable.MutableObject;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.block.IMultipartConnectedBlock;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.DynamicBakedWrapper;
import slimeknights.mantle.client.model.util.ElementModelData;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.client.model.util.SimpleBlockModel;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Model that handles generating variants for connected textures.
 * <p>
 * 1.21.5: a model JSON can only produce static geometry, so this is split in two:
 * <ul>
 *   <li>The model loader ({@code "loader": "mantle:connected"}) holds the connection config and bakes the unconnected
 *   geometry, so plain {@code {"model": ...}} variants and item models still render.</li>
 *   <li>The block state model type ({@code {"type": "mantle:connected", "model": ...}} in the blockstate JSON, see
 *   {@link Unbaked}) reads that config from the resolved model and picks the connected variant from the neighbors in
 *   {@code collectParts}, replacing the old {@code ModelData} round trip.</li>
 * </ul>
 */
public class ConnectedModel extends ColoredBlockModel {
  /** Loader instance */
  public static UnbakedModelLoader<ConnectedModel> LOADER = ConnectedModel::deserialize;

  /** Map of texture name to index of suffixes (indexed as 0bENWS) */
  private final Map<String,String[]> connectedTextures;
  /** Function to run to check if this block connects to another */
  private final BiPredicate<BlockState,BlockState> connectionPredicate;
  /** List of sides to check when getting block directions */
  private final Set<Direction> sides;

  protected ConnectedModel(SimpleBlockModel model, Map<String,String[]> connectedTextures, BiPredicate<BlockState,BlockState> connectionPredicate, Set<Direction> sides) {
    super(model, ColoredBlockModel.getColorData(model));
    this.connectedTextures = connectedTextures;
    this.connectionPredicate = connectionPredicate;
    this.sides = sides;
  }

  /**
   * Finds the connected model in a resolved model chain, allowing child models without a loader to inherit the connection config
   * @param model  Resolved model
   * @return  Connected model, or null if the chain has none
   */
  @Nullable
  public static ConnectedModel find(@Nullable ResolvedModel model) {
    for (; model != null; model = model.parent()) {
      if (model.wrapped() instanceof ConnectedModel connected) {
        return connected;
      }
    }
    return null;
  }

  /**
   * Computes the extra connected textures (the {@code name_suffix} variants) from the resolved texture slots
   * @param textures  Resolved base texture slots
   * @return  Map of suffixed texture name to its material
   */
  private Map<String,Material> computeExtraTextures(TextureSlots textures) {
    Map<String,Material> extraTextures = new HashMap<>();
    for (Entry<String,String[]> entry : connectedTextures.entrySet()) {
      // fetch data from the base texture
      String name = entry.getKey();
      Material base = textures.getMaterial(name);
      // skip if missing
      if (base == null) {
        continue;
      }
      ResourceLocation atlas = base.atlasLocation();
      ResourceLocation texture = base.texture();
      String namespace = texture.getNamespace();
      String path = texture.getPath();

      // use base atlas and texture, but suffix the name
      String[] suffixes = entry.getValue();
      for (String suffix : suffixes) {
        if (suffix.isEmpty()) {
          continue;
        }
        // skip running if we have seen it before
        String suffixedName = name + "_" + suffix;
        if (!extraTextures.containsKey(suffixedName)) {
          Material mat;
          // allow overriding a specific texture
          Material override = textures.getMaterial(suffixedName);
          if (override != null) {
            mat = override;
          } else {
            mat = new Material(atlas, ResourceLocation.fromNamespaceAndPath(namespace, path + "/" + suffix));
          }
          // cache the texture name, we use it a lot in rebaking
          extraTextures.put(suffixedName, mat);
        }
      }
    }
    return extraTextures;
  }

  /**
   * Block state model for connected textures, referenced from the blockstate JSON as
   * {@code {"type": "mantle:connected", "model": "<connected model>", "x": 0, "y": 0, "uvlock": false}}.
   * Accepts the same fields as a vanilla variant.
   */
  public record Unbaked(Variant variant) implements CustomUnbakedBlockStateModel {
    public static final MapCodec<Unbaked> MAP_CODEC = Variant.MAP_CODEC.xmap(Unbaked::new, Unbaked::variant);

    /** Creates a connected block state model for the given model location */
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
      // unconnected part, used whenever there are no connections or no level
      BlockModelPart base = SimpleModelWrapper.bake(baker, resolved, state);
      ConnectedModel connected = find(resolved);
      if (connected == null) {
        Mantle.logger.warn("Block state model type mantle:connected used with {}, which is not a connected model", resolved.debugName());
        return new SingleVariant(base);
      }
      ElementModelData data = ElementModelData.of(baker, resolved, state, connected.getColorData());
      if (data.elements().isEmpty()) {
        Mantle.logger.warn("Connected model {} has no elements to connect, rendering it unconnected", resolved.debugName());
        return new SingleVariant(base);
      }
      return new Baked(connected, resolved, baker, data, state.transformation(), base);
    }
  }

  @SuppressWarnings("WeakerAccess")
  protected static class Baked extends DynamicBakedWrapper<SingleVariant> {
    private final ConnectedModel connected;
    private final ElementModelData data;
    /** Rotation of the block state variant, used to find the neighbor for each model side */
    private final Transformation rotation;
    /** Sprites for every texture name the connected variants can use, resolved at bake time */
    private final Map<String,TextureAtlasSprite> sprites;
    private final TextureAtlasSprite missing;
    private final AtomicReferenceArray<BlockModelPart> cache = new AtomicReferenceArray<>(64);
    /** Map of face texture name to the connected texture it references, computed at bake time */
    private final Map<String,String> connectedNames;

    public Baked(ConnectedModel connected, ResolvedModel resolved, ModelBaker baker, ElementModelData data, Transformation rotation, BlockModelPart base) {
      super(new SingleVariant(base));
      this.connected = connected;
      this.data = data;
      this.rotation = rotation;
      // all directions false gives cache key of 0, that is ourself
      this.cache.set(0, base);

      // resolve all sprites now while we have a baker: the base face textures plus all connected suffix variants
      SpriteGetter spriteGetter = baker.sprites();
      Map<String,Material> extraTextures = connected.computeExtraTextures(data.textures());
      Map<String,TextureAtlasSprite> sprites = new HashMap<>();
      for (BlockElement element : data.elements()) {
        for (BlockElementFace face : element.faces().values()) {
          String name = stripReference(face.texture());
          if (!sprites.containsKey(name)) {
            sprites.put(name, spriteGetter.resolveSlot(data.textures(), name, data.name()));
          }
        }
      }
      // follow each face texture back to the connected texture it references, through the whole parent chain
      // 1.21.5: unlike 1.21.4, this also follows references in vanilla parents such as block/cube_all
      this.connectedNames = ModelHelper.findReferences(resolved, connected.connectedTextures.keySet(), sprites.keySet());
      for (Entry<String,Material> entry : extraTextures.entrySet()) {
        sprites.put(entry.getKey(), spriteGetter.get(entry.getValue(), data.name()));
      }
      this.sprites = Map.copyOf(sprites);
      // the missing sprite is part of the atlas, so this lookup does not report anything
      this.missing = spriteGetter.get(new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation()), data.name());
    }

    /** Removes the leading {@code '#'} from a texture reference */
    private static String stripReference(String name) {
      return !name.isEmpty() && name.charAt(0) == '#' ? name.substring(1) : name;
    }

    /** Gets a sprite resolved at bake time */
    private TextureAtlasSprite getSprite(String name) {
      TextureAtlasSprite sprite = sprites.get(stripReference(name));
      return sprite != null ? sprite : missing;
    }

    /**
     * Gets the direction rotated
     * @param direction  Original direction to rotate
     * @param rotation   Rotation origin, aka the face of the block we are looking at. As a result, UP is identity
     * @return  Rotated direction
     */
    private static Direction rotateDirection(Direction direction, Direction rotation) {
      if (rotation == Direction.UP) {
        return direction;
      }
      if (rotation == Direction.DOWN) {
        // Z is backwards on the bottom
        if (direction.getAxis() == Axis.Z) {
          return direction.getOpposite();
        }
        // X is normal
        return direction;
      }
      // sides all just have the next side for left and right, and consistent up and down
      return switch (direction) {
        case NORTH -> Direction.UP;
        case SOUTH -> Direction.DOWN;
        case EAST -> rotation.getCounterClockWise();
        case WEST -> rotation.getClockWise();
        default -> throw new IllegalArgumentException("Direction must be horizontal axis");
      };
    }

    /**
     * Gets a transform function based on the block part UV and block face
     * @param face  Block face in question
     * @param original  Block face data, for the UVs and rotation
     * @return  Direction transform function
     */
    private static Function<Direction,Direction> getTransform(Direction face, BlockElementFace original) {
      // TODO: how do I apply UV lock?
      // final transform switches from face (NSWE) to world direction, the rest are composed in to apply first
      Function<Direction,Direction> transform = (d) -> rotateDirection(d, face);

      // flipping; 1.21.5: missing UVs mean the default UVs, which are never flipped
      BlockElementFace.UVs uvs = original.uvs();
      if (uvs != null) {
        boolean flipV = uvs.minV() > uvs.maxV();
        if (uvs.minU() > uvs.maxU()) {
          // flip both
          if (flipV) {
            transform = transform.compose(Direction::getOpposite);
          } else {
            // flip U
            transform = transform.compose((d) -> {
              if (d.getAxis() == Axis.X) {
                return d.getOpposite();
              }
              return d;
            });
          }
        } else if (flipV) {
          transform = transform.compose((d) -> {
            if (d.getAxis() == Axis.Z) {
              return d.getOpposite();
            }
            return d;
          });
        }
      }

      // rotation
      return switch (original.rotation()) {
        // 90 degrees
        case R90 -> transform.compose(Direction::getClockWise);
        case R180 -> transform.compose(Direction::getOpposite);
        case R270 -> transform.compose(Direction::getCounterClockWise);
        default -> transform;
      };
    }

    /**
     * Gets the name of this texture that supports connected textures, or empty if never is connected
     * @param key  Name of the part texture
     * @return  Name of the connected texture
     */
    private String getConnectedName(String key) {
      key = stripReference(key);
      // if the name is connected, we are done
      if (connected.connectedTextures.containsKey(key)) {
        return key;
      }
      return connectedNames.getOrDefault(key, "");
    }

    /**
     * Gets the texture suffix
     * @param texture      Texture name, must be a connected texture
     * @param connections  Connections byte
     * @param transform    Rotations to apply to faces
     * @return  Key used to cache it
     */
    private String getTextureSuffix(String texture, byte connections, Function<Direction,Direction> transform) {
      int key = 0;
      for (Direction dir : Plane.HORIZONTAL) {
        int flag = 1 << transform.apply(dir).get3DDataValue();
        if ((connections & flag) == flag) {
          key |= 1 << dir.get2DDataValue();
        }
      }
      // if empty, do not prefix
      String[] suffixes = connected.connectedTextures.get(texture);
      assert suffixes != null;
      String suffix = suffixes[key];
      if (suffix.isEmpty()) {
        return suffix;
      }
      return "_" + suffix;
    }

    /** Copies a face with a new texture; faces are always copied as {@link BlockElement} binds each face to its element */
    private static BlockElementFace copyFace(BlockElementFace face, String texture) {
      return new BlockElementFace(face.cullForDirection(), face.tintIndex(), texture, face.uvs(), face.rotation(), face.faceData(), new MutableObject<>());
    }

    /**
     * Gets the model based on the connections in the given model data
     * @param connections  Array of face connections, true at indexes of connected sides
     * @return  Model with connections applied
     */
    private BlockModelPart applyConnections(byte connections) {
      // copy each element with updated faces
      List<BlockElement> elements = Lists.newArrayList();
      for (BlockElement part : data.elements()) {
        Map<Direction,BlockElementFace> partFaces = new EnumMap<>(Direction.class);
        for (Map.Entry<Direction,BlockElementFace> entry : part.faces().entrySet()) {
          // first, determine which texture to use on this side
          Direction dir = entry.getKey();
          BlockElementFace original = entry.getValue();
          String texture = original.texture();

          // follow the texture name back to the original name
          // if it never reaches a connected texture, skip
          String connectedTexture = getConnectedName(original.texture());
          if (!connectedTexture.isEmpty()) {
            // if empty string, we can keep the old face
            String suffix = getTextureSuffix(connectedTexture, connections, getTransform(dir, original));
            if (!suffix.isEmpty()) {
              // suffix the texture
              texture = "#" + connectedTexture + suffix;
            }
          }
          // add the updated face
          partFaces.put(dir, copyFace(original, texture));
        }
        // add the updated parts into a new model part
        elements.add(new BlockElement(part.from(), part.to(), partFaces, part.rotation(), part.shade(), part.lightEmission(), part.faceData()));
      }

      // bake the model with the sprites resolved at bake time so the suffixed textures resolve
      return data.part(ColoredBlockModel.bakeElements(elements, data.colorData(), this::getSprite, data.state()));
    }

    /**
     * Gets an array of directions to whether a block exists on the side, indexed using direction indexes
     * @param predicate  Function that returns true if the block is connected on the given side
     * @return  Boolean array of data
     */
    private static byte getConnections(Predicate<Direction> predicate) {
      byte connections = 0;
      for (Direction dir : Direction.values()) {
        if (predicate.test(dir)) {
          connections |= (byte) (1 << dir.get3DDataValue());
        }
      }
      return connections;
    }

    /** Gets the connections for the block at the given position */
    private byte getConnections(BlockAndTintGetter level, BlockPos pos, BlockState state) {
      // no level (item rendering, deprecated context-free calls): fall back to the block state properties, used by multipart connected blocks
      if (level == EmptyBlockAndTintGetter.INSTANCE) {
        return getConnections(dir -> {
          if (!connected.sides.contains(dir)) {
            return false;
          }
          BooleanProperty prop = IMultipartConnectedBlock.CONNECTED_DIRECTIONS.get(rotation.rotateTransform(dir));
          return state.hasProperty(prop) && state.getValue(prop);
        });
      }
      return getConnections(dir -> connected.sides.contains(dir) && connected.connectionPredicate.test(state, level.getBlockState(pos.relative(rotation.rotateTransform(dir)))));
    }

    /** Gets the part for the given connections, baking it if needed */
    protected BlockModelPart getCachedPart(byte connections) {
      BlockModelPart part = cache.get(connections);
      if (part == null) {
        // baking twice on a race is harmless, both results are identical
        part = applyConnections(connections);
        cache.set(connections, part);
      }
      return part;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockModelPart> parts) {
      parts.add(getCachedPart(getConnections(level, pos, state)));
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
      return new GeometryKey(this, getConnections(level, pos, state));
    }

    /** Geometry key, the connections fully determine the geometry */
    private record GeometryKey(Baked model, byte connections) {}
  }

  /** Loader class containing singleton instance */
  public static ConnectedModel deserialize(JsonObject json, JsonDeserializationContext context) {
    ColoredBlockModel model = ColoredBlockModel.deserialize(json, context);

    // root object for all model data
    JsonObject data = GsonHelper.getAsJsonObject(json, "connection");

    // need at least one connected texture
    JsonObject connected = GsonHelper.getAsJsonObject(data, "textures");
    if (connected.size() == 0) {
      throw new JsonSyntaxException("Must have at least one texture in connected");
    }

    // build texture list
    Map<String,String[]> connectedTextures = new HashMap<>(connected.size());
    for (Entry<String,JsonElement> entry : connected.entrySet()) {
      // don't validate texture as it may be contained in a child model that is not yet loaded
      // get type, put in map
      String name = entry.getKey();
      connectedTextures.put(name, ConnectedModelRegistry.deserializeType(entry.getValue(), "textures[" + name + "]"));
    }

    // get a list of sides to pay attention to
    Set<Direction> sides;
    if (data.has("sides")) {
      JsonArray array = GsonHelper.getAsJsonArray(data, "sides");
      sides = EnumSet.noneOf(Direction.class);
      for (int i = 0; i < array.size(); i++) {
        String side = GsonHelper.convertToString(array.get(i), "sides[" + i + "]");
        Direction dir = Direction.byName(side);
        if (dir == null) {
          throw new JsonParseException("Invalid side " + side);
        }
        sides.add(dir);
      }
    } else {
      sides = EnumSet.allOf(Direction.class);
    }

    // other data
    BiPredicate<BlockState,BlockState> predicate = ConnectedModelRegistry.deserializePredicate(data, "predicate");

    // final model instance
    return new ConnectedModel(model, Map.copyOf(connectedTextures), predicate, sides);
  }
}
