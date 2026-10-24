package slimeknights.mantle.client.model.util;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ResolvedModel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Iterates over the raw texture maps ({@code #reference} chains preserved) of a model chain, child first. Texture
 * references are flattened at bake time into {@link net.minecraft.client.renderer.block.model.TextureSlots}, so Mantle
 * models keep their raw maps (see {@link SimpleBlockModel#getTextures()}) to let wrapping models follow references by
 * name. Vanilla {@code BlockModel} texture references are not readable ({@code TextureSlots.Reference} is package-private),
 * so iteration stops at the first non-Mantle model in the chain.
 * <p>
 * 1.21.5: unbaked models only know their parent's location, so walking parents requires a {@link ResolvedModel}.
 */
public class ModelTextureIteratable implements Iterable<Map<String,Either<Material, String>>> {
  /** Maps to iterate, child first */
  private final List<Map<String,Either<Material, String>>> maps;

  /**
   * Creates an iterable over an optional initial map followed by the given resolved model chain
   * @param startMap    Initial map, or null for none
   * @param startModel  Resolved model to iterate, or null for none
   */
  public ModelTextureIteratable(@Nullable Map<String,Either<Material, String>> startMap, @Nullable ResolvedModel startModel) {
    this.maps = new ArrayList<>();
    if (startMap != null) {
      maps.add(startMap);
    }
    for (ResolvedModel model = startModel; model != null && model.wrapped() instanceof SimpleBlockModel simple; model = model.parent()) {
      maps.add(simple.getTextures());
    }
  }

  /**
   * Creates an iterable over the given resolved model chain
   * @param model  Model to iterate
   * @return  Iterable over the model texture maps
   */
  public static ModelTextureIteratable of(ResolvedModel model) {
    return new ModelTextureIteratable(null, model);
  }

  /**
   * Creates an iterable over just the given model's own texture map. Parents cannot be walked from an unbaked model in
   * 1.21.5; use {@link #of(ResolvedModel)} to include them.
   * @param model  Model
   * @return  Iterable over the model's texture map
   */
  public static ModelTextureIteratable of(SimpleBlockModel model) {
    return new ModelTextureIteratable(model.getTextures(), null);
  }

  @Override
  public Iterator<Map<String,Either<Material, String>>> iterator() {
    return maps.iterator();
  }
}
