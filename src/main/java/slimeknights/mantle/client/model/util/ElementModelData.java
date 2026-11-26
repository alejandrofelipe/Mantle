package slimeknights.mantle.client.model.util;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.cuboid.CuboidModelElement;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import slimeknights.mantle.client.model.util.ColoredBlockModel.ColorData;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Everything needed to (re)bake an element based model outside the model bake, captured from a {@link ResolvedModel}
 * while a {@link ModelBaker} is available.
 * <p>
 * 1.21.5: replaces the {@code BakedModel} + {@code DynamicBakedWrapper} + {@code bakeReplaced} pattern. A {@link ModelBaker}
 * only exists during model baking, while dynamic models (retextured blocks, tanks, material blocks) only know their
 * textures at render time, so they keep this record and rebake quads lazily, typically with the atlas manager ({@link ModelHelper#getSprite(SpriteId)})
 * through {@link #runtimeSprites(Map)}, caching the result per texture.
 *
 * @param elements             Elements to bake, including elements inherited from parents
 * @param colorData            Per-element color data, empty for plain elements
 * @param textures             Resolved texture slots of the model
 * @param state                Model state with the root transform composed in
 * @param useAmbientOcclusion  Ambient occlusion flag of the model
 * @param particle             Default particle sprite
 * @param renderType           Render type group from the model's {@code render_type}, or {@link RenderTypeGroup#EMPTY}
 * @param itemProperties       Item render properties (gui light, transforms, particle) for item models
 * @param name                 Debug name for error reporting
 */
public record ElementModelData(List<CuboidModelElement> elements, List<ColorData> colorData, TextureSlots textures, ModelState state, boolean useAmbientOcclusion,
                               TextureAtlasSprite particle, RenderTypeGroup renderType, ModelRenderProperties itemProperties, ModelDebugName name) {
  /**
   * Captures the data for a resolved model
   * @param baker      Model baker, used to resolve the particle sprite
   * @param model      Resolved model
   * @param state      Model state, root transform is composed in from the model properties
   * @param colorData  Per-element color data
   * @return  Captured data
   */
  public static ElementModelData of(ModelBaker baker, ResolvedModel model, ModelState state, List<ColorData> colorData) {
    TextureSlots textures = model.getTopTextureSlots();
    ContextMap properties = model.getTopAdditionalProperties();
    RenderTypeGroup renderType = properties.getOptional(NeoForgeModelProperties.RENDER_TYPE);
    return new ElementModelData(
      SimpleBlockModel.findElements(model), colorData, textures, SimpleBlockModel.applyRootTransform(state, properties),
      model.getTopAmbientOcclusion(), model.resolveParticleSprite(textures, baker), renderType == null ? RenderTypeGroup.EMPTY : renderType,
      ModelRenderProperties.fromResolvedModel(baker, model, textures), model);
  }

  /** Captures the data for a resolved model, reading color data from the first colored model in its chain */
  public static ElementModelData of(ModelBaker baker, ResolvedModel model, ModelState state) {
    return of(baker, model, state, findColorData(model));
  }

  /** Finds the color data for the first Mantle model in the resolved chain */
  public static List<ColorData> findColorData(@Nullable ResolvedModel model) {
    for (; model != null; model = model.parent()) {
      if (model.wrapped() instanceof SimpleBlockModel simple) {
        List<ColorData> colors = ColoredBlockModel.getColorData(simple);
        // colored model without elements colors its parent elements, so stop at the first one with colors or elements
        if (!colors.isEmpty() || !simple.getElements().isEmpty()) {
          return colors;
        }
      } else if (model.wrapped().geometry() != null) {
        break;
      }
    }
    return List.of();
  }


  /* Sprites */

  /** Sprite lookup for the baking phase, reporting missing textures through the baker */
  public Function<String,TextureAtlasSprite> bakingSprites(ModelBaker baker) {
    return SimpleBlockModel.spriteLookup(textures, baker.sprites(), name);
  }

  /**
   * Sprite lookup at runtime with the given texture replacements, through the atlas manager.
   * 1.21.9: {@code SpriteId#sprite()} is gone; {@link net.minecraft.client.resources.model.sprite.AtlasManager} is the runtime {@link MaterialSet}
   * holding every stitched sprite (missing sprite for unknown textures, like the old atlas lookup).
   * 1.21.11: model textures use the {@code block_or_item} pseudo atlas, which the atlas manager rejects; {@link ModelHelper#getSprite(SpriteId)}
   * resolves it like the model bakery.
   */
  public Function<String,TextureAtlasSprite> runtimeSprites(Map<String,SpriteId> replacements) {
    return SimpleBlockModel.spriteLookup(textures, replacements, ModelHelper::getSprite);
  }

  /** Gets the particle for the given replacements, falling back to the default particle */
  public TextureAtlasSprite particle(Map<String,SpriteId> replacements) {
    SpriteId particle = replacements.get("particle");
    return particle != null ? ModelHelper.getSprite(particle) : this.particle;
  }


  /* Baking */

  /** Bakes the elements into the given builder, allowing callers to add extra quads (e.g. a fluid) */
  public void bake(QuadCollection.Builder builder, Function<String,TextureAtlasSprite> sprites) {
    ColoredBlockModel.bakeElements(builder, elements, colorData, sprites, state);
  }

  /** Bakes the elements with the given sprite lookup */
  public QuadCollection bake(Function<String,TextureAtlasSprite> sprites) {
    return ColoredBlockModel.bakeElements(elements, colorData, sprites, state);
  }

  /** Bakes the elements with the given texture replacements at runtime */
  public QuadCollection bakeReplaced(Map<String,SpriteId> replacements) {
    return bake(runtimeSprites(replacements));
  }

  /** Gets the chunk render type for block parts, or null to use the block's default */
  @Nullable
  public ChunkSectionLayer blockRenderType() {
    return renderType.isEmpty() ? null : renderType.block();
  }

  /**
   * Gets the entity render type for item rendering of the given quads, or null to use the item's default.
   * 1.21.11: the group holds a type per atlas, so the quads pick it, see {@link ModelHelper#getEntityRenderType(RenderTypeGroup, List)}.
   */
  @Nullable
  public RenderType itemRenderType(List<BakedQuad> quads) {
    return renderType.isEmpty() ? null : ModelHelper.getEntityRenderType(renderType, quads);
  }

  /** Gets the render type group from the model's {@code render_type}, or null if unset */
  @Nullable
  public static RenderTypeGroup itemRenderTypes(ResolvedModel model) {
    RenderTypeGroup renderType = model.getTopAdditionalProperties().getOptional(NeoForgeModelProperties.RENDER_TYPE);
    return renderType == null || renderType.isEmpty() ? null : renderType;
  }

  /**
   * Gets the entity render type for item rendering of the given model and quads, or null to use the item's default.
   * 1.21.11: the group holds a type per atlas, so the quads pick it, see {@link ModelHelper#getEntityRenderType(RenderTypeGroup, List)}.
   * For quads that may mix the blocks and items atlases, pass {@link #itemRenderTypes(ResolvedModel)} to {@link ModelHelper#addItemLayers} instead.
   */
  @Nullable
  public static RenderType itemRenderType(ResolvedModel model, List<BakedQuad> quads) {
    RenderTypeGroup renderType = itemRenderTypes(model);
    return renderType == null ? null : ModelHelper.getEntityRenderType(renderType, quads);
  }

  /** Creates a block model part for the given quads, matching vanilla {@link SimpleModelWrapper#bake} */
  public SimpleModelWrapper part(QuadCollection quads, TextureAtlasSprite particle) {
    return new SimpleModelWrapper(quads, useAmbientOcclusion, particle, blockRenderType());
  }

  /** Creates a block model part with the default particle */
  public SimpleModelWrapper part(QuadCollection quads) {
    return part(quads, particle);
  }

  /** Creates item render properties using the given particle */
  public ModelRenderProperties itemProperties(TextureAtlasSprite particle) {
    return new ModelRenderProperties(itemProperties.usesBlockLight(), particle, itemProperties.transforms());
  }
}
