package slimeknights.mantle.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.fml.ModList;

/**
 * Loads the first model from a list of models that has a loaded mod ID, ideal for optional CTM model support.
 * <p>
 * In 1.21.4 the geometry-loader system was replaced by {@link UnbakedModelLoader}, which returns an
 * {@link net.minecraft.client.resources.model.UnbakedModel} directly; the chosen child {@link BlockModel} is itself an
 * unbaked model, so it is returned as-is (no wrapper needed).
 */
public enum FallbackModelLoader implements UnbakedModelLoader<BlockModel> {
  INSTANCE;

  @Override
  public BlockModel read(JsonObject data, JsonDeserializationContext context) {
    JsonArray models = GsonHelper.getAsJsonArray(data, "models");
    if (models.size() < 2) {
      throw new JsonSyntaxException("Fallback model must contain at least 2 models");
    }

    // try loading each model
    for (int i = 0; i < models.size(); i++) {
      String debugName = "models[" + i + "]";
      JsonObject entry = GsonHelper.convertToJsonObject(models.get(i), debugName);

      // first, determine required mod ID
      String modId = null;
      if (entry.has("fallback_mod_id")) {
        modId = GsonHelper.getAsString(entry, "fallback_mod_id");
      } else if (entry.has("loader")) {
        ResourceLocation loader = ResourceLocation.parse(GsonHelper.getAsString(entry, "loader"));
        modId = loader.getNamespace();
      }

      // if the mod is loaded, try loading the given model
      if (modId == null || ModList.get().isLoaded(modId)) {
        try {
          // the deserialized block model is already an UnbakedModel; return it directly so it bakes via vanilla logic
          return context.deserialize(entry, BlockModel.class);
        } catch (JsonSyntaxException e) {
          // wrap exceptions to make it more clear what failed
          throw new JsonSyntaxException("Failed to parse fallback model " + debugName, e);
        }
      }
    }

    // no model was successful, sadness
    throw new JsonSyntaxException("Failed to load fallback model, all " + models.size() + " variants had a failed condition");
  }
}
