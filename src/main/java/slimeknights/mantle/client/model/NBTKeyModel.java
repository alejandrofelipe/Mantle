package slimeknights.mantle.client.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedCompositeModel;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.mantle.util.JsonHelper;

import javax.annotation.Nullable;

/**
 * Model which uses a key in NBT to select which texture variant to load.
 * <p>
 * PORT 1.21.4: reimplement item-variant selection via net.minecraft.client.renderer.item.ItemModel.Unbaked +
 * assets/&lt;ns&gt;/items/*.json. The 1.21.1 {@code ItemOverrides.resolve} mechanism this relied on was removed in 1.21.4
 * (there is no {@code ItemOverrides}/{@code BakedOverrides} on {@code BakedModel} anymore). For now this bakes only the
 * {@code default} texture variant with no NBT-driven switching; the registered extra-texture metadata is preserved as a
 * foundation for the dedicated cross-repo item-model task (which also covers Tinkers' ToolModel/MaterialModel).
 */
public class NBTKeyModel extends AbstractUnbakedModel {
  /** Model loader instance */
  public static final UnbakedModelLoader<NBTKeyModel> LOADER = NBTKeyModel::deserialize;

  /** Map of statically registered extra textures, used for addon mods */
  private static final Multimap<ResourceLocation,Pair<String,ResourceLocation>> EXTRA_TEXTURES = HashMultimap.create();

  /**
   * Registers an extra variant texture for the model with the given key. Note that resource packs can override the extra texture
   * @param key          Model key, should be defined in the model JSON if supported
   * @param textureName  Name of the texture defined, corresponds to a possible value of the NBT key
   * @param texture      Texture to use, same format as in resource packs
   */
  @SuppressWarnings("unused")  // API
  public static void registerExtraTexture(ResourceLocation key, String textureName, ResourceLocation texture) {
    EXTRA_TEXTURES.put(key, Pair.of(textureName, texture));
  }

  /** Key to check in item NBT */
  private final String nbtKey;
  /** Key denoting which extra textures to fetch from the map */
  @Nullable
  private final ResourceLocation extraTexturesKey;

  public NBTKeyModel(StandardModelParameters parameters, String nbtKey, @Nullable ResourceLocation extraTexturesKey) {
    super(parameters);
    this.nbtKey = nbtKey;
    this.extraTexturesKey = extraTexturesKey;
  }

  /** Gets the extra textures registered for this model's key, for use by the future item-model reimplementation */
  @SuppressWarnings("unused")  // API foundation for the item-model task
  public java.util.Collection<Pair<String,ResourceLocation>> getExtraTextures() {
    return extraTexturesKey != null ? EXTRA_TEXTURES.get(extraTexturesKey) : java.util.List.of();
  }

  @Override
  public BakedModel bake(TextureSlots textures, ModelBaker baker, ModelState modelTransform, boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, ContextMap additionalProperties) {
    // PORT 1.21.4: only bakes the default texture; NBT variant switching must move to ItemModel.Unbaked
    Material defaultTexture = textures.getMaterial("default");
    if (defaultTexture == null) {
      defaultTexture = new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation());
    }
    Transformation rootTransform = additionalProperties.getOrDefault(NeoForgeModelProperties.TRANSFORM, Transformation.identity());
    Transformation transform = MantleItemLayerModel.applyTransform(modelTransform, rootTransform).getRotation();
    TextureAtlasSprite sprite = baker.sprites().get(defaultTexture);
    RenderTypeGroup renderType = MantleItemLayerModel.getDefaultRenderType(additionalProperties.getOrDefault(NeoForgeModelProperties.RENDER_TYPE, RenderTypeGroup.EMPTY));
    UnbakedCompositeModel.Baked.Builder builder = UnbakedCompositeModel.Baked.builder(useAmbientOcclusion, false, useBlockLight, sprite, itemTransforms);
    builder.addQuads(renderType, MantleItemLayerModel.getQuadsForSprite(-1, -1, sprite, transform, 0));
    return builder.build();
  }

  /** Deserializes this model from JSON */
  public static NBTKeyModel deserialize(JsonObject json, JsonDeserializationContext context) {
    StandardModelParameters parameters = StandardModelParameters.parse(json, context);
    String key = GsonHelper.getAsString(json, "nbt_key");
    ResourceLocation extraTexturesKey = null;
    if (json.has("extra_textures_key")) {
      extraTexturesKey = JsonHelper.getResourceLocation(json, "extra_textures_key");
    }
    return new NBTKeyModel(parameters, key, extraTexturesKey);
  }
}
