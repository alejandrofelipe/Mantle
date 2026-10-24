package slimeknights.mantle.client.model.util;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Transformation;
import lombok.Getter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.resources.model.UnbakedGeometry;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import org.joml.Vector3fc;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.common.ColorLoadable;
import slimeknights.mantle.data.loadable.primitive.BooleanLoadable;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.LogicHelper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Block model for setting color, luminosity, and per element uv lock. Similar to {@link MantleItemLayerModel} but for blocks.
 * <p>
 * 1.21.5: the colors are applied by the shared {@link SimpleBlockModel.Geometry}; the static helpers bake into a
 * {@link QuadCollection.Builder} instead of the removed {@code SimpleBakedModel.Builder}.
 */
@SuppressWarnings("unused")  // API
public class ColoredBlockModel extends SimpleBlockModel {
  /** Model loader to allow doing basic coloring outside of other models */
  public static final UnbakedModelLoader<SimpleBlockModel> LOADER = ColoredBlockModel::deserialize;

  /** Colors to use for each piece */
  @Getter
  private final List<ColorData> colorData;

  /**
   * Creates a new colored block model
   * @param parameters  Standard top-level model parameters
   * @param textures    Raw texture reference map parsed from JSON
   * @param parts       List of parts in the model
   * @param colorData   Additional information about colors in the model
   */
  public ColoredBlockModel(StandardModelParameters parameters, Map<String,Either<Material,String>> textures, List<BlockElement> parts, List<ColorData> colorData) {
    super(parameters, textures, parts);
    this.colorData = colorData;
  }

  public ColoredBlockModel(SimpleBlockModel base, List<ColorData> colorData) {
    super(base);
    this.colorData = colorData;
  }

  /** Gets the color data of the given model, or an empty list if it is not a colored model */
  public static List<ColorData> getColorData(SimpleBlockModel model) {
    return model instanceof ColoredBlockModel colored ? colored.colorData : List.of();
  }

  /**
   * {@inheritDoc}
   * <p>
   * With color data, the geometry is always present: if this model has no elements, the parent's elements are colored.
   */
  @Nullable
  @Override
  public UnbakedGeometry geometry() {
    if (colorData.isEmpty()) {
      return super.geometry();
    }
    return new Geometry(this, colorData);
  }


  /* Baking */

  /**
   * Bakes a list of elements, applying per-element color, luminosity and UV lock.
   * @param elements   Model elements
   * @param colorData  Per-element color data, missing entries use {@link ColorData#DEFAULT}
   * @param sprites    Lookup from face texture name to sprite
   * @param state      Model state (root transform already composed in)
   * @return  Baked quads
   */
  public static QuadCollection bakeElements(List<BlockElement> elements, List<ColorData> colorData, Function<String,TextureAtlasSprite> sprites, ModelState state) {
    QuadCollection.Builder builder = new QuadCollection.Builder();
    bakeElements(builder, elements, colorData, sprites, state);
    return builder.build();
  }

  /** Bakes a list of colored elements into the given builder, see {@link #bakeElements(List, List, Function, ModelState)} */
  public static void bakeElements(QuadCollection.Builder builder, List<BlockElement> elements, List<ColorData> colorData, Function<String,TextureAtlasSprite> sprites, ModelState state) {
    int size = elements.size();
    for (int i = 0; i < size; i++) {
      ColorData colors = LogicHelper.getOrDefault(colorData, i, ColorData.DEFAULT);
      IQuadTransformer partTransformer = colors.color() == -1 ? QuadTransformers.empty() : applyColorQuadTransformer(colors.color());
      ModelState partState = colors.uvlock() == null ? state : ModelHelper.withUvLock(state, colors.uvlock());
      bakePart(builder, elements.get(i), colors.luminosity(), sprites, partState, partTransformer);
    }
  }

  /**
   * Bakes a single part of the model into the builder
   * @param builder          Quad builder
   * @param part             Part to bake
   * @param emissivity       Emissivity for fullbright, -1 will leave forge in charge, 0-15 will override the forge value
   * @param sprites          Lookup from face texture name to sprite
   * @param state            Model state for the part, including any UV lock override
   * @param quadTransformer  Quad transformations for the face, this is notably where you should handle color transformations
   */
  public static void bakePart(QuadCollection.Builder builder, BlockElement part, int emissivity, Function<String,TextureAtlasSprite> sprites, ModelState state, IQuadTransformer quadTransformer) {
    for (Entry<Direction,BlockElementFace> entry : part.faces().entrySet()) {
      BlockElementFace face = entry.getValue();
      BakedQuad quad = bakeFace(part, face, sprites.apply(face.texture()), entry.getKey(), state, emissivity);
      quadTransformer.processInPlace(quad);
      addFace(builder, face, quad, state);
    }
  }

  /**
   * Bakes a single part of the model into the builder
   * @param builder          Quad builder
   * @param textures         Resolved texture slots
   * @param part             Part to bake
   * @param emissivity       Emissivity for fullbright, -1 will leave forge in charge, 0-15 will override the forge value
   * @param spriteGetter     Sprite getter
   * @param transform        Transform for the face
   * @param quadTransformer  Quad transformations for the face, this is notably where you should handle color transformations
   * @param uvlock           UV lock for the face, separated to allow overriding the model state
   */
  public static void bakePart(QuadCollection.Builder builder, TextureSlots textures, BlockElement part, int emissivity, Function<Material,TextureAtlasSprite> spriteGetter, Transformation transform, IQuadTransformer quadTransformer, boolean uvlock) {
    bakePart(builder, textures, Map.of(), part, emissivity, spriteGetter, transform, quadTransformer, uvlock);
  }

  /**
   * Variant of {@link #bakePart(QuadCollection.Builder, TextureSlots, BlockElement, int, Function, Transformation, IQuadTransformer, boolean)}
   * that resolves face textures through a dynamic replacement map, for runtime retexturing.
   * See {@link SimpleBlockModel#findSprite(Function, TextureSlots, Map, String)}.
   */
  public static void bakePart(QuadCollection.Builder builder, TextureSlots textures, Map<String,Material> replacements, BlockElement part, int emissivity, Function<Material,TextureAtlasSprite> spriteGetter, Transformation transform, IQuadTransformer quadTransformer, boolean uvlock) {
    bakePart(builder, part, emissivity, spriteLookup(textures, replacements, spriteGetter), ModelHelper.modelState(transform, uvlock), quadTransformer);
  }

  /**
   * Data class for setting properties when baking colored elements
   */
  public record ColorData(int color, @Deprecated int luminosity, @Nullable Boolean uvlock) {
    public static final ColorData DEFAULT = new ColorData(-1, -1, null);
    public static final RecordLoadable<ColorData> LOADABLE = RecordLoadable.create(
      ColorLoadable.ALPHA.defaultField("color", false, ColorData::color),
      IntLoadable.range(-1, 15).defaultField("luminosity", -1, ColorData::luminosity),
      BooleanLoadable.INSTANCE.nullableField("uvlock", ColorData::uvlock),
      ColorData::new);
    public static final Loadable<List<ColorData>> LIST_LOADABLE = LOADABLE.list(0);

    /** Gets the UV lock for the given part */
    public boolean isUvLock(boolean defaultLock) {
      if (uvlock == null) {
        return defaultLock;
      }
      return uvlock;
    }

    /** @deprecated use {@link #LOADABLE} */
    @Deprecated(forRemoval = true)
    public static ColorData fromJson(JsonObject json) {
      return LOADABLE.deserialize(json);
    }

    /** @deprecated use {@link #LOADABLE} */
    @Deprecated(forRemoval = true)
    public JsonObject toJson() {
      JsonObject json = new JsonObject();
      LOADABLE.serialize(this, json);
      return json;
    }
  }


  /* Deserializing */

  /** Deserializes the model from JSON */
  public static ColoredBlockModel deserialize(JsonObject json, JsonDeserializationContext context) {
    SimpleBlockModel model = SimpleBlockModel.deserialize(json, context);
    List<ColorData> colorData = ColorData.LIST_LOADABLE.getOrDefault(json, "colors", List.of());
    return new ColoredBlockModel(model, colorData);
  }


  /* Face bakery */

  /**
   * Converts an ARGB color to an ABGR color, as the commonly used color format is not the format colors end up packed into.
   * This function doubles as its own inverse, not that its needed.
   * @param color  ARGB color
   * @return  ABGR color
   */
  public static int swapColorRedBlue(int color) {
    return (color & 0xFF00FF00) // alpha and green same spot
           | ((color >> 16) & 0x000000FF) // red moves to blue
           | ((color << 16) & 0x00FF0000); // blue moves to red
  }

  /** Quad transformer applying a static color */
  public static IQuadTransformer applyColorQuadTransformer(int color) {
    int abgr = swapColorRedBlue(color);
    return quad -> {
      int[] vertices = quad.vertices();
      for (int i = 0; i < 4; i++) {
        vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.COLOR] = abgr;
      }
    };
  }

  /**
   * Bakes a face with an emissivity override
   * @param part        Part containing the face
   * @param face        Face data
   * @param sprite      Sprite for the face
   * @param facing      Direction of the face
   * @param state       Model state for the face, including UV lock
   * @param emissivity  Emissivity for fullbright, -1 will leave forge in charge, 0-15 will override the forge value
   */
  public static BakedQuad bakeFace(BlockElement part, BlockElementFace face, TextureAtlasSprite sprite, Direction facing, ModelState state, int emissivity) {
    return bakeQuad(part.from(), part.to(), face, sprite, facing, state, part.rotation(), part.shade(), part.lightEmission(), emissivity);
  }

  /**
   * Extension of {@link FaceBakery#bakeQuad} with an emissivity override
   * @param posFrom        Face start position
   * @param posTo          Face end position
   * @param face           Face data
   * @param sprite         Sprite for the face
   * @param facing         Direction of the face
   * @param state          Model state for the face, including UV lock (see {@link ModelHelper#withUvLock(ModelState, boolean)})
   * @param partRotation   Rotation for the part
   * @param shade          If true, shades the part
   * @param lightEmission  Vanilla element light emission
   * @param emissivity     Emissivity for fullbright, -1 will leave forge in charge, 0-15 will override the forge value
   * @return  Baked quad
   */
  public static BakedQuad bakeQuad(Vector3fc posFrom, Vector3fc posTo, BlockElementFace face, TextureAtlasSprite sprite, Direction facing, ModelState state,
                                   @Nullable BlockElementRotation partRotation, boolean shade, int lightEmission, int emissivity) {
    // delegate to the vanilla face bakery, which handles UV lock, shrink ratio, winding, normals, and neoforge face data
    BakedQuad quad = FaceBakery.bakeQuad(posFrom, posTo, face, sprite, facing, state, partRotation, shade, lightEmission);
    // use our override if specified, fallback to the faceData value already applied by the vanilla bakery
    // PORT 1.21.5: emissivity is still baked into the vertex lightmap (UV2) rather than BakedQuad#lightEmission; verify
    // glowing elements (luminosity) stay fullbright on blocks and items in the uitest phase
    if (emissivity > 0) {
      QuadTransformers.settingEmissivity(emissivity).processInPlace(quad);
    }
    return quad;
  }

  /**
   * Variant of {@link #bakeQuad(Vector3fc, Vector3fc, BlockElementFace, TextureAtlasSprite, Direction, ModelState, BlockElementRotation, boolean, int, int)}
   * taking a transformation and UV lock flag, matching the 1.21.4 signature.
   */
  public static BakedQuad bakeQuad(Vector3fc posFrom, Vector3fc posTo, BlockElementFace face, TextureAtlasSprite sprite, Direction facing, Transformation transform, boolean uvlock,
                                   @Nullable BlockElementRotation partRotation, boolean shade, int emissivity) {
    return bakeQuad(posFrom, posTo, face, sprite, facing, ModelHelper.modelState(transform, uvlock), partRotation, shade, 0, emissivity);
  }
}
