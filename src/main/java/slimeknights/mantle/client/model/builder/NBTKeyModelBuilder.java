package slimeknights.mantle.client.model.builder;

import com.google.gson.JsonObject;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.template.CustomLoaderBuilder;
import slimeknights.mantle.Mantle;

/** Loader for {@link slimeknights.mantle.client.model.NBTKeyModel} */
@Setter
@Accessors(fluent = true)
public class NBTKeyModelBuilder extends CustomLoaderBuilder {
  private String key = null;
  private ResourceLocation extraTexturesKey = null;

  public NBTKeyModelBuilder() {
    super(Mantle.getResource("nbt_key"), false);
  }

  @Override
  protected CustomLoaderBuilder copyInternal() {
    NBTKeyModelBuilder builder = new NBTKeyModelBuilder();
    builder.key = this.key;
    builder.extraTexturesKey = this.extraTexturesKey;
    return builder;
  }

  @Override
  public JsonObject toJson(JsonObject json) {
    if (key == null) {
      throw new IllegalStateException("Must set key to use NBTKeyModel");
    }
    json = super.toJson(json);
    json.addProperty("nbt_key", key);
    if (extraTexturesKey != null) {
      json.addProperty("extra_textures_key", extraTexturesKey.toString());
    }
    return json;
  }
}
