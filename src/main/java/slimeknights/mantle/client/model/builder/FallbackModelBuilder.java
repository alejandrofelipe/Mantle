package slimeknights.mantle.client.model.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.TextureMapping;
import net.neoforged.neoforge.client.model.generators.template.CustomLoaderBuilder;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for {@link slimeknights.mantle.client.model.FallbackModelLoader}.
 * <p>
 * PORT 1.21.4: NeoForge removed {@code ModelBuilder}, so nested fallback models can no longer be passed as other
 * model builders. They are now supplied as a {@link ModelTemplate}/{@link TextureMapping} pair and serialized inline
 * via {@link CustomLoaderBuilder}'s nested-template mechanism, matching the new {@code CompositeModelBuilder}. The
 * emitted JSON format (a {@code "models"} array of model objects, each with an optional {@code "fallback_mod_id"}) is
 * unchanged, so the loader reads it identically.
 */
public class FallbackModelBuilder extends CustomLoaderBuilder {
  private final List<DomainModel> models = new ArrayList<>();

  public FallbackModelBuilder() {
    super(Mantle.getResource("fallback"), true);
  }

  /** Adds a fallback model with a domain restriction */
  public FallbackModelBuilder fallback(ModelTemplate template, TextureMapping textures, @Nullable String modId) {
    this.models.add(new DomainModel(template, textures, modId));
    return this;
  }

  /** Adds a fallback model using the loader ID as the domain restriction */
  public FallbackModelBuilder fallback(ModelTemplate template, TextureMapping textures) {
    return fallback(template, textures, null);
  }

  @Override
  protected CustomLoaderBuilder copyInternal() {
    FallbackModelBuilder builder = new FallbackModelBuilder();
    builder.models.addAll(this.models);
    return builder;
  }

  @Override
  public JsonObject toJson(JsonObject json) {
    json = super.toJson(json);
    if (this.models.size() < 2) {
      throw new IllegalStateException("Must have at least two models to use the fallback loader");
    }
    JsonArray fallbacks = new JsonArray();
    for (DomainModel model : models) {
      serializeNestedTemplate(model.template(), model.textures(), element -> {
        JsonObject object = element.getAsJsonObject();
        if (model.domain() != null) {
          object.addProperty("fallback_mod_id", model.domain());
        }
        fallbacks.add(object);
      });
    }
    json.add("models", fallbacks);
    return json;
  }

  /** Nested model with an optional domain restriction */
  private record DomainModel(ModelTemplate template, TextureMapping textures, @Nullable String domain) {}
}
