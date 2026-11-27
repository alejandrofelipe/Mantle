package slimeknights.mantle.client.model.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import lombok.Getter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.cuboid.CuboidModelElement;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.ExtendedUnbakedGeometry;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedElementsHelper;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import slimeknights.mantle.client.model.util.ColoredBlockModel.ColorData;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Simpler version of {@link CuboidModel} as a reusable {@link UnbakedModel}, mainly serving as a shared base several
 * Mantle loaders chain into. Extends {@link AbstractUnbakedModel} to inherit the standard top-level parameters (parent,
 * textures, transforms, ambient occlusion, gui light and root transform) while supplying the elements. 26.1: the model format has no
 * {@code render_type} anymore, each quad picks its render layer from its texture, see {@link ModelHelper#bakeQuad}.
 * <p>
 * 1.21.5: unbaked models no longer bake; they expose an {@link UnbakedGeometry} that bakes into a {@link QuadCollection}
 * (see {@link Geometry}). Dynamic models rebake from data captured at bake time, see {@link ElementModelData}.
 */
@SuppressWarnings("WeakerAccess")
public class SimpleBlockModel extends AbstractUnbakedModel {
  /** Model loader for the vanilla-style block model, mainly intended for use in fallback registration */
  public static final UnbakedModelLoader<SimpleBlockModel> LOADER = SimpleBlockModel::deserialize;

  /** Model parts for the baked model */
  private final List<CuboidModelElement> parts;
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
  public SimpleBlockModel(StandardModelParameters parameters, Map<String,Either<Material,String>> textures, List<CuboidModelElement> parts) {
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
  public List<CuboidModelElement> getElements() {
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
   * vanilla parents are readable here as {@link CuboidModel} exposes its {@link UnbakedCuboidGeometry}.
   * @param model  Resolved model, typically from {@link ModelBaker#getModel(Identifier)}
   * @return  Elements, or an empty list if the geometry is not element based (e.g. a custom loader)
   */
  public static List<CuboidModelElement> findElements(@Nullable ResolvedModel model) {
    for (; model != null; model = model.parent()) {
      UnbakedModel wrapped = model.wrapped();
      if (wrapped instanceof SimpleBlockModel simple) {
        if (!simple.parts.isEmpty()) {
          return simple.parts;
        }
        continue;
      }
      UnbakedGeometry geometry = wrapped.geometry();
      if (geometry instanceof UnbakedCuboidGeometry simple) {
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

  /**
   * Resolves the material for the given texture name at runtime, matching vanilla's missing-reference fallback.
   * 26.1: model textures are {@link Material}s without an atlas, resolved like the model bakery by {@link ModelHelper#getMaterial(Material)},
   * so this no longer takes a sprite getter. Returns the baked material rather than the sprite so {@code force_translucent} reaches the quad.
   */
  public static Material.Baked findMaterial(TextureSlots textures, String name) {
    Material material = textures.getMaterial(name);
    if (material == null) {
      material = new Material(MissingTextureAtlasSprite.getLocation());
    }
    return ModelHelper.getMaterial(material);
  }

  /**
   * Resolves the material for the given texture name, checking the replacement map first. This is the primitive used for
   * dynamic retexturing: since {@link TextureSlots} is immutable and non-enumerable, callers pass a map of texture name
   * (without the leading {@code '#'}) to a replacement {@link SpriteId}; any name present there wins over the slots.
   * A replacement is not forced translucent: its layer follows its own transparency.
   * @param spriteGetter  Sprite getter for the replacements, typically {@link ModelHelper#getSprite(SpriteId)} when rebaking outside the baking phase
   * @param textures      Original resolved texture slots, used for names not in the replacement map, see {@link #findMaterial(TextureSlots, String)}
   * @param replacements  Map of texture name (no {@code '#'}) to replacement sprite; may be empty
   * @param name          Texture name to resolve, with or without a leading {@code '#'}
   */
  public static Material.Baked findMaterial(Function<SpriteId,TextureAtlasSprite> spriteGetter, TextureSlots textures, Map<String,SpriteId> replacements, String name) {
    if (!replacements.isEmpty()) {
      String key = (!name.isEmpty() && name.charAt(0) == '#') ? name.substring(1) : name;
      SpriteId replacement = replacements.get(key);
      if (replacement != null) {
        return new Material.Baked(spriteGetter.apply(replacement), false);
      }
    }
    return findMaterial(textures, name);
  }

  /**
   * Creates a material lookup for baking time, resolving texture names through the baker's materials so missing
   * textures and references are reported like vanilla, and {@code force_translucent} is kept like vanilla's material baker.
   * 26.1: takes the {@link ModelBaker} as the sprite getter became {@link ModelBaker#materials()}.
   */
  public static Function<String,Material.Baked> materialLookup(TextureSlots textures, ModelBaker baker, ModelDebugName name) {
    return texture -> baker.materials().resolveSlot(textures, texture, name);
  }

  /**
   * Creates a material lookup with dynamic texture replacements, see {@link #findMaterial(Function, TextureSlots, Map, String)}.
   * Pass {@link ModelHelper#getSprite(SpriteId)} as the sprite getter to rebake outside the baking phase.
   */
  public static Function<String,Material.Baked> materialLookup(TextureSlots textures, Map<String,SpriteId> replacements, Function<SpriteId,TextureAtlasSprite> spriteGetter) {
    return texture -> findMaterial(spriteGetter, textures, replacements, texture);
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
   * @param materials        Lookup from face texture name (with or without {@code '#'}) to baked material
   * @param state            Model state (root transform already composed in)
   * @param quadTransformer  Additional quad transforms, {@link UnaryOperator#identity()} for none.
   *                         1.21.11: quads are immutable, so this replaces NeoForge's removed {@code IQuadTransformer} by returning the transformed quad.
   */
  public static void bakePart(QuadCollection.Builder builder, CuboidModelElement part, Function<String,Material.Baked> materials, ModelState state, UnaryOperator<BakedQuad> quadTransformer) {
    for (Entry<Direction,CuboidFace> entry : part.faces().entrySet()) {
      Direction direction = entry.getKey();
      CuboidFace face = entry.getValue();
      BakedQuad quad = bakeFace(part, face, materials.apply(face.texture()), direction, state);
      addFace(builder, face, quadTransformer.apply(quad), state);
    }
  }

  /**
   * Bakes a single face of an element, matching vanilla {@link UnbakedCuboidGeometry#bake} (1.21.11 removed {@code UnbakedCuboidGeometry.bakeFace}).
   * Faces of flat elements are baked too, like 1.21.10.
   * 26.1: the quad's render layer follows the sprite's transparency, or translucent if the material forces it, see {@link ModelHelper#bakeQuad}.
   */
  public static BakedQuad bakeFace(CuboidModelElement part, CuboidFace face, Material.Baked material, Direction direction, ModelState state) {
    return ModelHelper.bakeQuad(part.from(), part.to(), face, material, direction, state, part.rotation(), part.shade(), part.lightEmission());
  }

  /** Adds a baked face to the builder, applying its cull face rotated by the model state */
  public static void addFace(QuadCollection.Builder builder, CuboidFace face, BakedQuad quad, ModelState state) {
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
  public static void bakePart(QuadCollection.Builder builder, TextureSlots textures, CuboidModelElement part, Function<SpriteId,TextureAtlasSprite> spriteGetter, ModelState transform, UnaryOperator<BakedQuad> quadTransformer) {
    bakePart(builder, textures, Map.of(), part, spriteGetter, transform, quadTransformer);
  }

  /**
   * Bakes a single part of the model into the builder, resolving face textures through a dynamic replacement map.
   * See {@link #findMaterial(Function, TextureSlots, Map, String)} for the replacement semantics.
   */
  public static void bakePart(QuadCollection.Builder builder, TextureSlots textures, Map<String,SpriteId> replacements, CuboidModelElement part, Function<SpriteId,TextureAtlasSprite> spriteGetter, ModelState transform, UnaryOperator<BakedQuad> quadTransformer) {
    bakePart(builder, part, materialLookup(textures, replacements, spriteGetter), transform, quadTransformer);
  }

  /**
   * Bakes a list of block part elements into a quad collection
   * @param elements   Model elements
   * @param materials  Lookup from face texture name to baked material
   * @param state      Model state (root transform already composed in)
   * @return  Baked quads
   */
  public static QuadCollection bakeElements(List<CuboidModelElement> elements, Function<String,Material.Baked> materials, ModelState state) {
    QuadCollection.Builder builder = new QuadCollection.Builder();
    for (CuboidModelElement part : elements) {
      bakePart(builder, part, materials, state, UnaryOperator.identity());
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
      List<CuboidModelElement> elements = model.parts;
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
      Function<String,Material.Baked> materials = materialLookup(textureSlots, baker, debugName);
      if (colorData.isEmpty()) {
        return bakeElements(elements, materials, state);
      }
      return ColoredBlockModel.bakeElements(elements, colorData, materials, state);
    }
  }


  /* Deserializing */

  /**
   * Replicates the now-private {@code TextureSlots} texture parsing: a leading '#' marks a reference, else a material.
   * 26.1: materials no longer name an atlas (the model bakery checks the blocks atlas, then the items atlas), and may be an object with
   * {@code sprite} and {@code force_translucent}.
   */
  private static Either<Material,String> parseTextureLocationOrReference(String key, JsonElement element) {
    if (element.isJsonObject()) {
      return Either.left(Material.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow(error -> new JsonSyntaxException("Invalid texture " + key + ": " + error)));
    }
    String name = GsonHelper.convertToString(element, key);
    if (!name.isEmpty() && name.charAt(0) == '#') {
      return Either.right(name.substring(1));
    }
    Identifier texture = Identifier.tryParse(name);
    if (texture == null) {
      throw new JsonSyntaxException(name + " is not valid resource location");
    }
    return Either.left(new Material(texture));
  }

  /** Parses the raw texture reference map from JSON */
  public static Map<String,Either<Material,String>> getTextures(JsonObject json) {
    if (json.has("textures")) {
      JsonObject textures = GsonHelper.getAsJsonObject(json, "textures");
      Map<String,Either<Material,String>> builder = new HashMap<>(textures.size());
      for (Entry<String,JsonElement> entry : textures.entrySet()) {
        builder.put(entry.getKey(), parseTextureLocationOrReference(entry.getKey(), entry.getValue()));
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
    List<CuboidModelElement> parts;
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
  public static List<CuboidModelElement> getModelElements(JsonDeserializationContext context, JsonElement element, String name) {
    // if just one element, array is optional
    if (element.isJsonObject()) {
      // cast ensures we call List.of(CuboidModelElement) instead of List.of(CuboidModelElement[]) as the type is vague
      return List.of((CuboidModelElement)context.deserialize(element.getAsJsonObject(), CuboidModelElement.class));
    }
    // if an array, get array of elements
    if (element.isJsonArray()) {
      JsonArray array = element.getAsJsonArray();
      List<CuboidModelElement> builder = new ArrayList<>(array.size());
      for (JsonElement json : array) {
        builder.add(context.deserialize(json, CuboidModelElement.class));
      }
      return List.copyOf(builder);
    }

    throw new JsonSyntaxException("Missing " + name + ", expected to find a JsonArray or JsonObject");
  }
}
