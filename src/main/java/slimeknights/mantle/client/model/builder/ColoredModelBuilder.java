package slimeknights.mantle.client.model.builder;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.template.CustomLoaderBuilder;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.model.util.ColoredBlockModel.ColorData;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for {@link slimeknights.mantle.client.model.util.ColoredBlockModel}, used as a base for other model builders.
 * <p>
 * PORT 1.21.4: the NeoForge model-datagen rewrite removed {@code ModelBuilder} and {@code ExistingFileHelper}, and
 * {@link CustomLoaderBuilder} is no longer generic. The old self-type generic {@code <T extends ModelBuilder<T>>} is
 * gone, so the {@code color}/{@code colorData}/{@code luminosity} helpers inherited into subclasses now return
 * {@code ColoredModelBuilder} rather than the subclass type (minor loss of fluent chaining across subclass methods).
 */
public class ColoredModelBuilder extends CustomLoaderBuilder {
  protected final List<ColorData> colors = new ArrayList<>();

  public ColoredModelBuilder() {
    this(Mantle.getResource("colored_block"));
  }

  protected ColoredModelBuilder(ResourceLocation loaderId) {
    super(loaderId, true);
  }

  /** Adds a full color data for the next element */
  public ColoredModelBuilder colorData(ColorData data) {
    colors.add(data);
    return this;
  }

  /** Sets the color for the next element */
  public ColoredModelBuilder color(int color) {
    return colorData(new ColorData(color, -1, null));
  }

  /** Sets the luminosity for the next element */
  public ColoredModelBuilder luminosity(int luminosity) {
    return colorData(new ColorData(-1, luminosity, null));
  }

  @Override
  protected CustomLoaderBuilder copyInternal() {
    ColoredModelBuilder builder = new ColoredModelBuilder(loaderId);
    builder.colors.addAll(this.colors);
    return builder;
  }

  @Override
  public JsonObject toJson(JsonObject json) {
    json = super.toJson(json);
    if (!colors.isEmpty()) {
      json.add("colors", ColorData.LIST_LOADABLE.serialize(colors));
    }
    return json;
  }
}
