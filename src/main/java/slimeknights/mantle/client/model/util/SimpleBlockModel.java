package slimeknights.mantle.client.model.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Either;
import lombok.Getter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.SimpleUnbakedGeometry;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.SpriteGetter;
import net.minecraft.client.resources.model.UnbakedGeometry;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.ExtendedUnbakedGeometry;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedElementsHelper;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import slimeknights.mantle.client.model.util.ColoredBlockModel.ColorData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Simpler version of {@link BlockModel} as a reusable {@link UnbakedModel}, mainly serving as a shared base several
 * Mantle loaders chain into. Extends {@link AbstractUnbakedModel} to inherit the standard top-level parameters (parent,
 * textures, transforms, ambient occlusion, gui light, root transform and render type) while supplying the elements.
 * <p>
 * 1.21.5: unbaked models no longer bake; they expose an {@link UnbakedGeometry} that bakes into a {@link QuadCollection}
 * (see {@link Geometry}). Dynamic models rebake from data captured at bake time, see {@link ElementModelData}.
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
   * Gets the elements defined directly in this simple block model. To include elements inherited from parents, use
   * {@link #findElements(ResolvedModel)} on the resolved model.
   * @return  Elements in the model
   */
  public List<BlockElement> getElements() {
    return parts;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Returns null when the model has no elements, so the geometry is inherited from the parent like vanilla block models.
   */
  @Nullable
  @Override
  public UnbakedGeometry geometry() {
    if (parts.isEmpty()) {
      return null;
    }
    return new Geometry(this, List.of());
  }


  /* Resolved models */

  /**
   * Finds the elements a resolved model bakes: the first element-based geometry along its parent chain. Unlike 1.21.4,
   * vanilla parents are readable here as {@link BlockModel} exposes its {@link SimpleUnbakedGeometry}.
   * @param model  Resolved model, typically from {@link ModelBaker#getModel(ResourceLocation)}
   * @return  Elements, or an empty list if the geometry is not element based (e.g. a custom loader)
   */
  public static List<BlockElement> findElements(@Nullable ResolvedModel model) {
    for (; model != null; model = model.parent()) {
      UnbakedModel wrapped = model.wrapped();
      if (wrapped instanceof SimpleBlockModel simple) {
        if (!simple.parts.isEmpty()) {
          return simple.parts;
        }
        continue;
      }
      UnbakedGeometry geometry = wrapped.geometry();
      if (geometry instanceof SimpleUnbakedGeometry simple) {
        return simple.elements();
      }
      if (geometry instanceof Geometry simple) {
        return simple.model.parts;
      }
      // custom geometry, cannot read elements from it
      if (geometry != null) {
        return List.of();
      }
    }
    return List.of();
  }

  /**
   * Finds the resolved model node wrapping the given unbaked model in a resolved chain. Geometry bakes receive the top
   * resolved model as their debug name ({@link ResolvedModel#bakeTopGeometry}), so this lets a geometry find its own
   * position in the chain to read parent data.
   * @param debugName  Debug name passed to the geometry bake, normally the top resolved model
   * @param model      Unbaked model to locate
   * @return  Resolved model wrapping {@code model}, or null if not found
   */
  @Nullable
  public static ResolvedModel findResolved(ModelDebugName debugName, UnbakedModel model) {
    if (debugName instanceof ResolvedModel resolved) {
      for (; resolved != null; resolved = resolved.parent()) {
        if (resolved.wrapped() == model) {
          return resolved;
        }
      }
    }
    return null;
  }


  /* Sprites */

  /** Resolves a sprite for the given texture name, matching vanilla's missing-reference fallback */
  public static TextureAtlasSprite findSprite(Function<Material,TextureAtlasSprite> spriteGetter, TextureSlots textures, String name) {
    Material material = textures.getMaterial(name);
    if (material == null) {
      material = new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation());
    }
    return spriteGetter.apply(material);
  }

  /**
   * Resolves a sprite for the given texture name, checking the replacement map first. This is the primitive used for
   * dynamic retexturing: since {@link TextureSlots} is immutable and non-enumerable, callers pass a map of texture name
   * (without the leading {@code '#'}) to a replacement {@link Material}; any name present there wins over the slots.
   * @param spriteGetter  Sprite getter, typically {@link Material#sprite()} when rebaking outside the baking phase
   * @param textures      Original resolved texture slots, used for names not in the replacement map
   * @param replacements  Map of texture name (no {@code '#'}) to replacement material; may be empty
   * @param name          Texture name to resolve, with or without a leading {@code '#'}
   */
  public static TextureAtlasSprite findSprite(Function<Material,TextureAtlasSprite> spriteGetter, TextureSlots textures, Map<String,Material> replacements, String name) {
    if (!replacements.isEmpty()) {
      String key = (!name.isEmpty() && name.charAt(0) == '#') ? name.substring(1) : name;
      Material replacement = replacements.get(key);
      if (replacement != null) {
        return spriteGetter.apply(replacement);
      }
    }
    return findSprite(spriteGetter, textures, name);
  }

  /**
   * Creates a sprite lookup for baking time, resolving texture names through the baker's sprite getter so missing
   * textures and references are reported like vanilla.
   */
  public static Function<String,TextureAtlasSprite> spriteLookup(TextureSlots textures, SpriteGetter sprites, ModelDebugName name) {
    return texture -> sprites.resolveSlot(textures, texture, name);
  }

  /**
   * Creates a sprite lookup with dynamic texture replacements, see {@link #findSprite(Function, TextureSlots, Map, String)}.
   * Pass {@link Material#sprite()} as the sprite getter to rebake outside the baking phase.
   */
  public static Function<String,TextureAtlasSprite> spriteLookup(TextureSlots textures, Map<String,Material> replacements, Function<Material,TextureAtlasSprite> spriteGetter) {
    return texture -> findSprite(spriteGetter, textures, replacements, texture);
  }


  /* Baking */

  /** Composes the model's root transform property into the model state, matching vanilla element baking */
  public static ModelState applyRootTransform(ModelState state, ContextMap additionalProperties) {
    com.mojang.math.Transformation rootTransform = additionalProperties.getOptional(NeoForgeModelProperties.TRANSFORM);
    if (rootTransform != null) {
      return UnbakedElementsHelper.composeRootTransformIntoModelState(state, rootTransform);
    }
    return state;
  }

  /**
   * Bakes a single part of the model into the builder
   * @param builder          Quad builder
   * @param part             Part to bake
   * @param sprites          Lookup from face texture name (with or without {@code '#'}) to sprite
   * @param state            Model state (root transform already composed in)
   * @param quadTransformer  Additional quad transforms
   */
  public static void bakePart(QuadCollection.Builder builder, BlockElement part, Function<String,TextureAtlasSprite> sprites, ModelState state, IQuadTransformer quadTransformer) {
    for (Entry<Direction,BlockElementFace> entry : part.faces().entrySet()) {
      Direction direction = entry.getKey();
      BlockElementFace face = entry.getValue();
      BakedQuad quad = SimpleUnbakedGeometry.bakeFace(part, face, sprites.apply(face.texture()), direction, state);
      quadTransformer.processInPlace(quad);
      addFace(builder, face, quad, state);
    }
  }

  /** Adds a baked face to the builder, applying its cull face rotated by the model state */
  public static void addFace(QuadCollection.Builder builder, BlockElementFace face, BakedQuad quad, ModelState state) {
    if (face.cullForDirection() == null) {
      builder.addUnculledFace(quad);
    } else {
      builder.addCulledFace(Direction.rotate(state.transformation().getMatrix(), face.cullForDirection()), quad);
    }
  }

  /**
   * Bakes a single part of the model into the builder
   * @param builder          Quad builder
   * @param textures         Resolved texture slots
   * @param part             Part to bake
   * @param spriteGetter     Sprite getter
   * @param transform        Model state (root transform already composed in)
   * @param quadTransformer  Additional quad transforms
   */
  public static void bakePart(QuadCollection.Builder builder, TextureSlots textures, BlockElement part, Function<Material,TextureAtlasSprite> spriteGetter, ModelState transform, IQuadTransformer quadTransformer) {
    bakePart(builder, textures, Map.of(), part, spriteGetter, transform, quadTransformer);
  }

  /**
   * Bakes a single part of the model into the builder, resolving face textures through a dynamic replacement map.
   * See {@link #findSprite(Function, TextureSlots, Map, String)} for the replacement semantics.
   */
  public static void bakePart(QuadCollection.Builder builder, TextureSlots textures, Map<String,Material> replacements, BlockElement part, Function<Material,TextureAtlasSprite> spriteGetter, ModelState transform, IQuadTransformer quadTransformer) {
    bakePart(builder, part, spriteLookup(textures, replacements, spriteGetter), transform, quadTransformer);
  }

  /**
   * Bakes a list of block part elements into a quad collection
   * @param elements  Model elements
   * @param sprites   Lookup from face texture name to sprite
   * @param state     Model state (root transform already composed in)
   * @return  Baked quads
   */
  public static QuadCollection bakeElements(List<BlockElement> elements, Function<String,TextureAtlasSprite> sprites, ModelState state) {
    QuadCollection.Builder builder = new QuadCollection.Builder();
    for (BlockElement part : elements) {
      bakePart(builder, part, sprites, state, QuadTransformers.empty());
    }
    return builder.build();
  }


  /**
   * Element geometry for Mantle block models, with optional per-element color data. When the owning model has no
   * elements of its own, the elements are read from the parent chain (1.20 behavior, lost in 1.21.4), so color data
   * still applies to inherited elements.
   */
  public static class Geometry implements ExtendedUnbakedGeometry {
    protected final SimpleBlockModel model;
    protected final List<ColorData> colorData;

    public Geometry(SimpleBlockModel model, List<ColorData> colorData) {
      this.model = model;
      this.colorData = colorData;
    }

    @Override
    public QuadCollection bake(TextureSlots textureSlots, ModelBaker baker, ModelState state, ModelDebugName debugName, ContextMap additionalProperties) {
      List<BlockElement> elements = model.parts;
      if (elements.isEmpty()) {
        // find our position in the resolved chain, then use the parent's elements
        ResolvedModel self = findResolved(debugName, model);
        ResolvedModel parent = self != null ? self.parent() : null;
        elements = findElements(parent);
        if (elements.isEmpty()) {
          // parent geometry is not element based (or there is no parent): bake it directly, losing color data
          // PORT 1.21.5: only reachable for colored models on top of a custom loader, verify if such a model appears
          if (parent != null) {
            return parent.getTopGeometry().bake(textureSlots, baker, state, debugName, additionalProperties);
          }
          return QuadCollection.EMPTY;
        }
      }
      state = applyRootTransform(state, additionalProperties);
      Function<String,TextureAtlasSprite> sprites = spriteLookup(textureSlots, baker.sprites(), debugName);
      if (colorData.isEmpty()) {
        return bakeElements(elements, sprites, state);
      }
      return ColoredBlockModel.bakeElements(elements, colorData, sprites, state);
    }
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
