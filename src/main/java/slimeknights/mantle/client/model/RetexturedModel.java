package slimeknights.mantle.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.data.ModelData;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.DynamicBakedWrapper;
import slimeknights.mantle.client.model.util.ModelHelper;
import slimeknights.mantle.client.model.util.ModelTextureIteratable;
import slimeknights.mantle.client.model.util.SimpleBlockModel;
import slimeknights.mantle.util.RetexturedHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model that dynamically retextures a list of textures based on data from {@link slimeknights.mantle.util.RetexturedHelper}.
 * <p>
 * PORT 1.21.4: reimplement the dynamic retexturing (both the block-side {@code ModelData} path and the item-side
 * {@code ItemOverrides.resolve} path) via net.minecraft.client.renderer.item.ItemModel.Unbaked +
 * assets/&lt;ns&gt;/items/*.json and a 1.21.4 dynamic baked model. The 1.21.1 {@code ItemOverrides}/geometry-context
 * baking this relied on was removed in 1.21.4. For now this bakes only the base model with no retexturing; the
 * retextured-name parsing and expansion are preserved as a foundation for the dedicated cross-repo item-model task
 * (which also covers Tinkers' ToolModel/MaterialModel).
 */
@SuppressWarnings("WeakerAccess")
public class RetexturedModel extends AbstractUnbakedModel {
  /** Loader instance */
  public static UnbakedModelLoader<RetexturedModel> LOADER = RetexturedModel::deserialize;

  private final SimpleBlockModel model;
  private final Set<String> retextured;

  protected RetexturedModel(SimpleBlockModel model, Set<String> retextured) {
    super(model.getParameters());
    this.model = model;
    this.retextured = retextured;
  }

  @Override
  public void resolveDependencies(Resolver resolver) {
    super.resolveDependencies(resolver);
    model.resolveDependencies(resolver);
  }

  @Override
  public BakedModel bake(TextureSlots textures, ModelBaker baker, ModelState transform, boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, ContextMap additionalProperties) {
    // bake the base model, then wrap it so it can dynamically retexture based on the contained block (block-entity ModelData)
    BakedModel baked = model.bake(textures, baker, transform, useAmbientOcclusion, useBlockLight, itemTransforms, additionalProperties);
    Transformation rootTransform = additionalProperties.getOrDefault(NeoForgeModelProperties.TRANSFORM, Transformation.identity());
    RenderTypeGroup renderType = additionalProperties.getOrDefault(NeoForgeModelProperties.RENDER_TYPE, RenderTypeGroup.EMPTY);
    Set<String> allRetextured = getAllRetextured(model, this.retextured);
    return new Baked(baked, model, textures, transform, useAmbientOcclusion, useBlockLight, itemTransforms, rootTransform, renderType, allRetextured);
  }

  /**
   * Baked model that dynamically retextures based on {@link RetexturedHelper#BLOCK_PROPERTY} in the block-entity model
   * data. The item-side selection (reading the texture data component) is handled by the dedicated item-model system.
   * <p>
   * PORT 1.21.4: replaces the former {@code ItemOverrides}-based retexturing with the {@link SimpleBlockModel#bakeReplaced}
   * primitive; only the block (ModelData) path lives here now.
   */
  public static class Baked extends DynamicBakedWrapper<BakedModel> {
    private final SimpleBlockModel model;
    private final TextureSlots textures;
    private final ModelState transform;
    private final boolean useAmbientOcclusion;
    private final boolean useBlockLight;
    private final ItemTransforms itemTransforms;
    private final Transformation rootTransform;
    private final RenderTypeGroup renderType;
    private final Set<String> retextured;
    /** Cache of baked models keyed by the contained block's particle texture */
    private final Map<ResourceLocation,BakedModel> cache = new ConcurrentHashMap<>();

    protected Baked(BakedModel original, SimpleBlockModel model, TextureSlots textures, ModelState transform, boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, Transformation rootTransform, RenderTypeGroup renderType, Set<String> retextured) {
      super(original);
      this.model = model;
      this.textures = textures;
      this.transform = transform;
      this.useAmbientOcclusion = useAmbientOcclusion;
      this.useBlockLight = useBlockLight;
      this.itemTransforms = itemTransforms;
      this.rootTransform = rootTransform;
      this.renderType = renderType;
      this.retextured = retextured;
    }

    /** Rebakes the model, pointing all retextured names at the given block's particle texture */
    private BakedModel bakeWithBlock(Block block) {
      Material material = new Material(TextureAtlas.LOCATION_BLOCKS, ModelHelper.getParticleTexture(block));
      Map<String,Material> replacements = new HashMap<>();
      for (String name : retextured) {
        replacements.put(name, material);
      }
      return model.bakeReplaced(textures, replacements, transform, useAmbientOcclusion, useBlockLight, itemTransforms, rootTransform, renderType);
    }

    /** Gets the cached model for the given block */
    public BakedModel getCachedModel(Block block) {
      return cache.computeIfAbsent(ModelHelper.getParticleTexture(block), tex -> bakeWithBlock(block));
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
      Block block = data.get(RetexturedHelper.BLOCK_PROPERTY);
      if (block != null) {
        return getCachedModel(block).getParticleIcon(data);
      }
      return parent.getParticleIcon(data);
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
      Block block = extraData.get(RetexturedHelper.BLOCK_PROPERTY);
      if (block != null) {
        return getCachedModel(block).getQuads(state, side, rand, extraData, renderType);
      }
      return parent.getQuads(state, side, rand, extraData, renderType);
    }
  }

  /**
   * Gets a list of all names to retexture based on the block model texture references
   * @param model        Model fallback
   * @param originalSet  Original list of names to retexture
   * @return  Set of textures including parent textures
   */
  public static Set<String> getAllRetextured(SimpleBlockModel model, Set<String> originalSet) {
    Set<String> retextured = Sets.newHashSet(originalSet);
    for (Map<String,Either<Material, String>> textures : ModelTextureIteratable.of(model)) {
      textures.forEach((name, either) ->
        either.ifRight(parent -> {
          if (retextured.contains(parent)) {
            retextured.add(name);
          }
        })
      );
    }
    return Set.copyOf(retextured);
  }

  /** Deserializes a retextured model from JSON */
  public static RetexturedModel deserialize(JsonObject json, JsonDeserializationContext context) {
    // get base model
    ColoredBlockModel model = ColoredBlockModel.deserialize(json, context);
    // get list of textures to retexture
    Set<String> retextured = getRetexturedNames(json);
    // return retextured model
    return new RetexturedModel(model, retextured);
  }

  /**
   * Gets the list of retextured textures from the model
   * @param json  Model json
   * @return  Set of textures
   */
  public static Set<String> getRetexturedNames(JsonObject json) {
    if (json.has("retextured")) {
      // if an array, set from each texture in array
      JsonElement retextured = json.get("retextured");
      if (retextured.isJsonArray()) {
        JsonArray array = retextured.getAsJsonArray();
        if (array.isEmpty()) {
          throw new JsonSyntaxException("Must have at least one texture in retextured");
        }
        List<String> builder = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
          builder.add(GsonHelper.convertToString(array.get(i), "retextured[" + i + "]"));
        }
        return Set.copyOf(builder);
      }
      // if string, single texture
      if (retextured.isJsonPrimitive()) {
        return Set.of(retextured.getAsString());
      }
    }
    // if neither or missing, error
    throw new JsonSyntaxException("Missing retextured, expected to find a String or a JsonArray");
  }
}
