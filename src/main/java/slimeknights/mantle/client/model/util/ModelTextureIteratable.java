package slimeknights.mantle.client.model.util;

import com.mojang.datafixers.util.Either;
import lombok.AllArgsConstructor;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.UnbakedModel;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Iterates over the raw texture maps ({@code #reference} chains preserved) of a {@link SimpleBlockModel} and its
 * {@link SimpleBlockModel} parents. In 1.21.4 texture references are flattened at bake time into {@link
 * net.minecraft.client.renderer.block.model.TextureSlots}, so Mantle models keep their raw maps (see
 * {@link SimpleBlockModel#getTextures()}) to let wrapping models follow references by name. Vanilla {@code BlockModel}
 * parents cannot be walked here (their texture slot contents are package-private), so iteration stops at the first
 * non-Mantle parent.
 */
@AllArgsConstructor
public class ModelTextureIteratable implements Iterable<Map<String,Either<Material, String>>> {
  /** Initial map for iteration */
  @Nullable
  private final Map<String,Either<Material, String>> startMap;
  /** Initial model for iteration */
  @Nullable
  private final SimpleBlockModel startModel;

  /**
   * Creates an iterable over the given model
   * @param model  Model
   */
  public ModelTextureIteratable(SimpleBlockModel model) {
    this(null, model);
  }

  /**
   * Creates an iterable over the given simple block model chain
   * @param model  Model to iterate
   * @return  Iterable over the model texture maps
   */
  public static ModelTextureIteratable of(SimpleBlockModel model) {
    return new ModelTextureIteratable(null, model);
  }

  @Override
  public MapIterator iterator() {
    return new MapIterator(startMap, startModel);
  }

  @AllArgsConstructor
  private static class MapIterator implements Iterator<Map<String,Either<Material, String>>> {
    /** Initial map for iteration */
    @Nullable
    private Map<String,Either<Material, String>> initial;
    /** current model in the iterator */
    @Nullable
    private UnbakedModel model;

    @Override
    public boolean hasNext() {
      return initial != null || model instanceof SimpleBlockModel;
    }

    @Override
    public Map<String,Either<Material,String>> next() {
      if (initial != null) {
        Map<String,Either<Material, String>> map = initial;
        initial = null;
        return map;
      }
      if (model instanceof SimpleBlockModel simple) {
        Map<String,Either<Material, String>> map = simple.getTextures();
        model = simple.getParent();
        return map;
      }
      throw new NoSuchElementException();
    }
  }
}
