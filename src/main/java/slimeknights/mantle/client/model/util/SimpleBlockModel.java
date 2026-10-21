package slimeknights.mantle.client.model.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Transformation;
import lombok.Getter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel.Builder;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedElementsHelper;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Simpler version of {@link BlockModel} as a reusable {@link UnbakedModel}, mainly serving as a shared base several
 * Mantle loaders chain into. In 1.21.4 the geometry-loader system was replaced by {@link UnbakedModel}-based loaders,
 * so this extends {@link AbstractUnbakedModel} to inherit parent resolution and the standard top-level parameters
 * (textures, transforms, ambient occlusion, gui light, root transform and render type) while supplying the elements.
 */
@SuppressWarnings("WeakerAccess")
public class SimpleBlockModel extends AbstractUnbakedModel {
  /** Model loader for the vanilla-style block model, mainly intended for use in fallback registration */
  public static final UnbakedModelLoader<SimpleBlockModel> LOADER = SimpleBlockModel::deserialize;

  /** Model parts for the baked model */
  private final List<BlockElement> parts;
  /**
   * Raw texture map parsed from JSON, preserving {@code #reference} chains that the resolved {@link TextureSlots} flattens
   * away. Needed by wrapping models (e.g. connected textures) to follow texture references by name.
   */
  @Getter
  private final Map<String,Either<Material,String>> textures;

  /**
   * Creates a new simple block model
   * @param parameters  Standard top-level model parameters (parent, textures, transforms, etc.)
   * @param textures    Raw texture reference map parsed from JSON
   * @param parts       List of parts in the model
   */
  public SimpleBlockModel(StandardModelParameters parameters, Map<String,Either<Material,String>> textures, List<BlockElement> parts) {
    super(parameters);
    this.textures = textures;
    this.parts = parts;
  }

  public SimpleBlockModel(SimpleBlockModel base) {
    super(base.parameters);
    this.textures = base.textures;
    this.parts = base.parts;
  }


  /* Properties */

  /** Gets the standard parameters, allowing wrapping models to reuse the same configuration */
  public StandardModelParameters getParameters() {
    return parameters;
  }

  /**
   * Gets the elements in this simple block model.
   * <p>
   * Note: unlike 1.20, parent elements cannot be read here as {@link BlockModel#getElements()} is package-private in
   * 1.21.4; when this model has no elements of its own, baking delegates to the parent instead (see
   * {@link #bake(TextureSlots, ModelBaker, ModelState, boolean, boolean, ItemTransforms, ContextMap)}).
   * @return  Elements in the model
   */
  public List<BlockElement> getElements() {
    return parts;
  }


  /* Baking */

  /** Creates a new baked model builder, matching how a vanilla block model bakes (gui3d is always true) */
  public static Builder bakedBuilder(boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms transforms) {
    return new SimpleBakedModel.Builder(useAmbientOcclusion, useBlockLight, true, transforms);
  }

  /** Resolves a sprite for the given texture name, matching vanilla's missing-reference fallback */
  public static TextureAtlasSprite findSprite(Function<Material,TextureAtlasSprite> spriteGetter, TextureSlots textures, String name) {
    Material material = textures.getMaterial(name);
    if (material == null) {
      material = new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation());
    }
    return spriteGetter.apply(material);
  }

  /**
   * Bakes a single part of the model into the builder
   * @param builder          Baked model builder
   * @param textures         Resolved texture slots
   * @param part             Part to bake
   * @param spriteGetter     Sprite getter
   * @param transform        Model transforms (root transform already composed in)
   * @param quadTransformer  Additional quad transforms
   */
  public static void bakePart(Builder builder, TextureSlots textures, BlockElement part, Function<Material,TextureAtlasSprite> spriteGetter, ModelState transform, IQuadTransformer quadTransformer) {
    for (Direction direction : part.faces.keySet()) {
      BlockElementFace face = part.faces.get(direction);
      // TextureSlots#getMaterial strips the leading '#' reference marker itself
      TextureAtlasSprite sprite = findSprite(spriteGetter, textures, face.texture());
      BakedQuad bakedQuad = SimpleBakedModel.bakeFace(part, face, sprite, direction, transform);
      quadTransformer.processInPlace(bakedQuad);
      // apply cull face
      if (face.cullForDirection() == null) {
        builder.addUnculledFace(bakedQuad);
      } else {
        builder.addCulledFace(Direction.rotate(transform.getRotation().getMatrix(), face.cullForDirection()), bakedQuad);
      }
    }
  }

  /**
   * Bakes a list of block part elements into a model
   * @param textures            Resolved texture slots
   * @param elements            Model elements
   * @param spriteGetter        Sprite getter instance
   * @param transform           Model transform
   * @param useAmbientOcclusion Whether the model uses ambient occlusion
   * @param useBlockLight       Whether the model uses block light
   * @param itemTransforms      Item display transforms
   * @param rootTransform       Root transform to compose into the model state
   * @param renderType          Render type group for the baked model
   * @return  Baked model
   */
  public static BakedModel bakeModel(TextureSlots textures, List<BlockElement> elements, Function<Material,TextureAtlasSprite> spriteGetter, ModelState transform,
                                     boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, Transformation rootTransform, RenderTypeGroup renderType) {
    TextureAtlasSprite particle = findSprite(spriteGetter, textures, "particle");
    Builder builder = bakedBuilder(useAmbientOcclusion, useBlockLight, itemTransforms).particle(particle);
    // in 1.21.4 the root transform is composed into the model state rather than applied as a quad transformer
    if (!rootTransform.isIdentity()) {
      transform = UnbakedElementsHelper.composeRootTransformIntoModelState(transform, rootTransform);
    }
    for (BlockElement part : elements) {
      bakePart(builder, textures, part, spriteGetter, transform, QuadTransformers.empty());
    }
    return builder.build(renderType);
  }

  @Override
  public BakedModel bake(TextureSlots textures, ModelBaker baker, ModelState transform, boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, ContextMap additionalProperties) {
    // no elements of our own: delegate to the parent, matching vanilla BlockModel behavior
    if (parts.isEmpty() && getParent() != null) {
      return getParent().bake(textures, baker, transform, useAmbientOcclusion, useBlockLight, itemTransforms, additionalProperties);
    }
    Transformation rootTransform = additionalProperties.getOrDefault(NeoForgeModelProperties.TRANSFORM, Transformation.identity());
    RenderTypeGroup renderType = additionalProperties.getOrDefault(NeoForgeModelProperties.RENDER_TYPE, RenderTypeGroup.EMPTY);
    return bakeModel(textures, getElements(), baker.sprites()::get, transform, useAmbientOcclusion, useBlockLight, itemTransforms, rootTransform, renderType);
  }

  /**
   * Bakes a custom list of elements for this model, used by wrapping models (e.g. connected textures) that rebake with
   * modified faces. Uses {@link Material#sprite()} so it can run outside the baking phase.
   */
  public BakedModel bakeWithElements(TextureSlots textures, List<BlockElement> elements, ModelState transform,
                                     boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, Transformation rootTransform, RenderTypeGroup renderType) {
    return bakeModel(textures, elements, Material::sprite, transform, useAmbientOcclusion, useBlockLight, itemTransforms, rootTransform, renderType);
  }


  /* Deserializing */

  /** Replicates the now-private {@code BlockModel.Deserializer} texture parsing: a leading '#' marks a reference, else a material */
  private static Either<Material,String> parseTextureLocationOrReference(ResourceLocation atlas, String name) {
    if (!name.isEmpty() && name.charAt(0) == '#') {
      return Either.right(name.substring(1));
    }
    ResourceLocation texture = ResourceLocation.tryParse(name);
    if (texture == null) {
      throw new JsonSyntaxException(name + " is not valid resource location");
    }
    return Either.left(new Material(atlas, texture));
  }

  /** Parses the raw texture reference map from JSON */
  public static Map<String,Either<Material,String>> getTextures(JsonObject json) {
    if (json.has("textures")) {
      ResourceLocation atlas = TextureAtlas.LOCATION_BLOCKS;
      JsonObject textures = GsonHelper.getAsJsonObject(json, "textures");
      Map<String,Either<Material,String>> builder = new HashMap<>(textures.size());
      for (Entry<String,JsonElement> entry : textures.entrySet()) {
        builder.put(entry.getKey(), parseTextureLocationOrReference(atlas, entry.getValue().getAsString()));
      }
      return Map.copyOf(builder);
    }
    return Map.of();
  }

  /**
   * Deserializes a SimpleBlockModel from JSON
   * @param json     Json element containing the model
   * @param context  Json Context
   * @return  Parsed model
   */
  public static SimpleBlockModel deserialize(JsonObject json, JsonDeserializationContext context) {
    StandardModelParameters parameters = StandardModelParameters.parse(json, context);
    Map<String,Either<Material,String>> textures = getTextures(json);
    List<BlockElement> parts;
    if (json.has("elements")) {
      parts = getModelElements(context, GsonHelper.getAsJsonArray(json, "elements"), "elements");
    } else {
      parts = List.of();
    }
    return new SimpleBlockModel(parameters, textures, parts);
  }

  /**
   * Gets a list of models from a JSON array
   * @param context  Json Context
   * @param element  Json array
   * @return  Model list
   */
  public static List<BlockElement> getModelElements(JsonDeserializationContext context, JsonElement element, String name) {
    // if just one element, array is optional
    if (element.isJsonObject()) {
      // cast ensures we call List.of(BlockElement) instead of List.of(BlockElement[]) as the type is vague
      return List.of((BlockElement)context.deserialize(element.getAsJsonObject(), BlockElement.class));
    }
    // if an array, get array of elements
    if (element.isJsonArray()) {
      JsonArray array = element.getAsJsonArray();
      List<BlockElement> builder = new ArrayList<>(array.size());
      for (JsonElement json : array) {
        builder.add(context.deserialize(json, BlockElement.class));
      }
      return List.copyOf(builder);
    }

    throw new JsonSyntaxException("Missing " + name + ", expected to find a JsonArray or JsonObject");
  }
}
