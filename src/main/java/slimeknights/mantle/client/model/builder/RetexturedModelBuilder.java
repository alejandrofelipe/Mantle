package slimeknights.mantle.client.model.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.neoforge.client.model.generators.template.CustomLoaderBuilder;
import slimeknights.mantle.Mantle;

public class RetexturedModelBuilder extends ColoredModelBuilder {
  private final JsonArray retextured = new JsonArray();

  public RetexturedModelBuilder() {
    super(Mantle.getResource("retextured"));
  }

  /** Marks the given texture as retextured. Uses the texture name, not path. */
  public RetexturedModelBuilder retexture(String name) {
    this.retextured.add(name);
    return this;
  }

  @Override
  protected CustomLoaderBuilder copyInternal() {
    RetexturedModelBuilder builder = new RetexturedModelBuilder();
    builder.colors.addAll(this.colors);
    this.retextured.forEach(builder.retextured::add);
    return builder;
  }

  @Override
  public JsonObject toJson(JsonObject json) {
    json = super.toJson(json);
    json.add("retextured", retextured);
    return json;
  }
}
