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
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel.Builder;
import net.minecraft.core.Direction;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.SimpleModelState;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedElementsHelper;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import org.joml.Vector3f;
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
 * Block model for setting color, luminosity, and per element uv lock. Similar to {@link MantleItemLayerModel} but for blocks
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

  /**
   * Bakes a single part of the model into the builder
   * @param builder          Baked model builder
   * @param textures         Resolved texture slots
   * @param part             Part to bake
   * @param emissivity       Emissivity for fullbright, -1 will leave forge in charge, 0-15 will override the forge value
   * @param spriteGetter     Sprite getter
   * @param transform        Transform for the face
   * @param quadTransformer  Quad transformations for the face, this is notably where you should handle color transformations
   * @param uvlock           UV lock for the face, separated to allow overriding the model state
   */
  public static void bakePart(Builder builder, TextureSlots textures, BlockElement part, int emissivity, Function<Material,TextureAtlasSprite> spriteGetter, Transformation transform, IQuadTransformer quadTransformer, boolean uvlock) {
    bakePart(builder, textures, Map.of(), part, emissivity, spriteGetter, transform, quadTransformer, uvlock);
  }

  /**
   * Variant of {@link #bakePart(Builder, TextureSlots, BlockElement, int, Function, Transformation, IQuadTransformer, boolean)}
   * that resolves face textures through a dynamic replacement map, for runtime retexturing.
   * See {@link SimpleBlockModel#findSprite(Function, TextureSlots, Map, String)}.
   */
  public static void bakePart(Builder builder, TextureSlots textures, Map<String,Material> replacements, BlockElement part, int emissivity, Function<Material,TextureAtlasSprite> spriteGetter, Transformation transform, IQuadTransformer quadTransformer, boolean uvlock) {
    for (Entry<Direction, BlockElementFace> entry : part.faces.entrySet()) {
      BlockElementFace face = entry.getValue();
      // bake the face with the extra colors; TextureSlots#getMaterial strips the leading '#' itself
      TextureAtlasSprite sprite = findSprite(spriteGetter, textures, replacements, face.texture());
      BakedQuad quad = bakeFace(part, face, sprite, entry.getKey(), transform, uvlock, emissivity);
      quadTransformer.processInPlace(quad);
      // apply cull face
      if (face.cullForDirection() == null) {
        builder.addUnculledFace(quad);
      } else {
        builder.addCulledFace(Direction.rotate(transform.getMatrix(), face.cullForDirection()), quad);
      }
    }
  }

  /**
   * Bakes a list of block part elements into a model
   * @param textures            Resolved texture slots
   * @param elements            Model elements
   * @param colorData           Per-element color data
   * @param spriteGetter        Sprite getter instance
   * @param modelState          Model transform
   * @param useAmbientOcclusion Whether the model uses ambient occlusion
   * @param useBlockLight       Whether the model uses block light
   * @param itemTransforms      Item display transforms
   * @param rootTransform       Root transform to compose into the model state
   * @param renderType          Render type group for the baked model
   * @return  Baked model
   */
  public static BakedModel bakeModel(TextureSlots textures, List<BlockElement> elements, List<ColorData> colorData, Function<Material,TextureAtlasSprite> spriteGetter, ModelState modelState,
                                     boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, Transformation rootTransform, RenderTypeGroup renderType) {
    // compose the root transform into the model state, matching vanilla element baking
    if (!rootTransform.isIdentity()) {
      modelState = UnbakedElementsHelper.composeRootTransformIntoModelState(modelState, rootTransform);
    }
    TextureAtlasSprite particle = findSprite(spriteGetter, textures, "particle");
    Builder builder = bakedBuilder(useAmbientOcclusion, useBlockLight, itemTransforms).particle(particle);
    int size = elements.size();
    Transformation transformation = modelState.getRotation();
    boolean uvlock = modelState.isUvLocked();
    for (int i = 0; i < size; i++) {
      BlockElement part = elements.get(i);
      ColorData colors = LogicHelper.getOrDefault(colorData, i, ColorData.DEFAULT);
      IQuadTransformer partTransformer = colors.color == -1 ? QuadTransformers.empty() : applyColorQuadTransformer(colors.color);
      bakePart(builder, textures, part, colors.luminosity, spriteGetter, transformation, partTransformer, colors.isUvLock(uvlock));
    }
    return builder.build(renderType);
  }

  @Override
  public BakedModel bake(TextureSlots textures, ModelBaker baker, ModelState modelTransform, boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, ContextMap additionalProperties) {
    // no elements of our own: delegate to the parent, matching SimpleBlockModel#bake and vanilla BlockModel behavior.
    // the dynamic bakeReplaced/bakeWithElements paths cannot delegate here as parent BlockElements are package-private
    // in 1.21.4 and those methods have no ModelBaker; thin wrappers (retextured/connected) inheriting parent geometry
    // therefore render the base model only, which is enough for inventory/item icons and the non-dynamic block model.
    if (getElements().isEmpty() && getParent() != null) {
      return getParent().bake(textures, baker, modelTransform, useAmbientOcclusion, useBlockLight, itemTransforms, additionalProperties);
    }
    Transformation rootTransform = additionalProperties.getOrDefault(NeoForgeModelProperties.TRANSFORM, Transformation.identity());
    RenderTypeGroup renderType = additionalProperties.getOrDefault(NeoForgeModelProperties.RENDER_TYPE, RenderTypeGroup.EMPTY);
    return bakeModel(textures, getElements(), colorData, baker.sprites()::get, modelTransform, useAmbientOcclusion, useBlockLight, itemTransforms, rootTransform, renderType);
  }

  @Override
  public BakedModel bakeWithElements(TextureSlots textures, List<BlockElement> elements, ModelState transform,
                                     boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, Transformation rootTransform, RenderTypeGroup renderType) {
    return bakeModel(textures, elements, colorData, Material::sprite, transform, useAmbientOcclusion, useBlockLight, itemTransforms, rootTransform, renderType);
  }

  @Override
  public BakedModel bakeReplaced(TextureSlots textures, Map<String,Material> replacements, ModelState modelState,
                                 boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, Transformation rootTransform, RenderTypeGroup renderType) {
    // same as bakeModel, but resolves face textures through the dynamic replacement map and preserves per-element color data
    if (!rootTransform.isIdentity()) {
      modelState = UnbakedElementsHelper.composeRootTransformIntoModelState(modelState, rootTransform);
    }
    Function<Material,TextureAtlasSprite> spriteGetter = Material::sprite;
    TextureAtlasSprite particle = findSprite(spriteGetter, textures, replacements, "particle");
    Builder builder = bakedBuilder(useAmbientOcclusion, useBlockLight, itemTransforms).particle(particle);
    List<BlockElement> elements = getElements();
    int size = elements.size();
    Transformation transformation = modelState.getRotation();
    boolean uvlock = modelState.isUvLocked();
    for (int i = 0; i < size; i++) {
      BlockElement part = elements.get(i);
      ColorData colors = LogicHelper.getOrDefault(colorData, i, ColorData.DEFAULT);
      IQuadTransformer partTransformer = colors.color() == -1 ? QuadTransformers.empty() : applyColorQuadTransformer(colors.color());
      bakePart(builder, textures, replacements, part, colors.luminosity(), spriteGetter, transformation, partTransformer, colors.isUvLock(uvlock));
    }
    return builder.build(renderType);
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
      int[] vertices = quad.getVertices();
      for (int i = 0; i < 4; i++) {
        vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.COLOR] = abgr;
      }
    };
  }

  /**
   * Extension of {@link net.minecraft.client.resources.model.SimpleBakedModel#bakeFace} with emissivity and UV lock arguments
   * @param part        Part containing the face
   * @param face        Face data
   * @param sprite      Sprite for the face
   * @param facing      Direction of the face
   * @param transform   Transform for the face
   * @param uvlock      UV lock for the face, separated to allow overriding the model state
   * @param emissivity  Emissivity for fullbright, -1 will leave forge in charge, 0-15 will override the forge value
   */
  public static BakedQuad bakeFace(BlockElement part, BlockElementFace face, TextureAtlasSprite sprite, Direction facing, Transformation transform, boolean uvlock, int emissivity) {
    return bakeQuad(part.from, part.to, face, sprite, facing, transform, uvlock, part.rotation, part.shade, emissivity);
  }

  /**
   * Extension of {@link net.minecraft.client.renderer.block.model.FaceBakery#bakeQuad} with emissivity and UV lock overrides
   * @param posFrom        Face start position
   * @param posTo          Face end position
   * @param face           Face data
   * @param sprite         Sprite for the face
   * @param facing         Direction of the face
   * @param transform      Transform for the face
   * @param uvlock         UV lock for the face, separated to allow overriding the model state
   * @param partRotation   Rotation for the part
   * @param shade          If true, shades the part
   * @param emissivity     Emissivity for fullbright, -1 will leave forge in charge, 0-15 will override the forge value
   * @return  Baked quad
   */
  public static BakedQuad bakeQuad(Vector3f posFrom, Vector3f posTo, BlockElementFace face, TextureAtlasSprite sprite,
                                   Direction facing, Transformation transform, boolean uvlock, @Nullable BlockElementRotation partRotation,
                                   boolean shade, int emissivity) {
    // delegate to the vanilla face bakery (now static), which handles UV lock, shrink ratio, winding, normals, and faceData emissivity
    // we wrap the transform and uvlock into a model state, then apply our explicit emissivity override (if any) via a quad transformer
    ModelState modelState = new SimpleModelState(transform, uvlock);
    BakedQuad quad = FaceBakery.bakeQuad(posFrom, posTo, face, sprite, facing, modelState, partRotation, shade, 0);
    // use our override if specified, fallback to the faceData value already applied by the vanilla bakery
    if (emissivity > 0) {
      QuadTransformers.settingEmissivity(emissivity).processInPlace(quad);
    }
    return quad;
  }
}
