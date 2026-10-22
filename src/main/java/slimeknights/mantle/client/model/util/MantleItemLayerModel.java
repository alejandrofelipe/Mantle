package slimeknights.mantle.client.model.util;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.NamedRenderTypeManager;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.AbstractUnbakedModel;
import net.neoforged.neoforge.client.model.NeoForgeModelProperties;
import net.neoforged.neoforge.client.model.StandardModelParameters;
import net.neoforged.neoforge.client.model.UnbakedCompositeModel;
import net.neoforged.neoforge.client.model.UnbakedElementsHelper;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.neoforged.neoforge.client.model.pipeline.TransformingVertexPipeline;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.common.ColorLoadable;
import slimeknights.mantle.data.loadable.primitive.BooleanLoadable;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.ItemLayerPixels;
import slimeknights.mantle.util.LogicHelper;
import slimeknights.mantle.util.ReversedListBuilder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.function.Function;

/**
 * Clone of NeoForge's old {@code ItemLayerModel} to propagate a hardcoded color in, allows reducing rendering time by bypassing item colors for a static color.
 * Also supports luminosity, and when used as a model loader supports telling a layer to not use a tint index.
 * <p>
 * In 1.21.4 this is an {@link UnbakedModel}: textures come from the resolved {@link TextureSlots} passed to bake, and the
 * multi-render-type output is built via {@link UnbakedCompositeModel.Baked.Builder}.
 */
public class MantleItemLayerModel extends AbstractUnbakedModel {
  /** Model loader instance */
  public static final UnbakedModelLoader<MantleItemLayerModel> LOADER = MantleItemLayerModel::deserialize;

  private static final Direction[] HORIZONTALS = {Direction.UP, Direction.DOWN};
  private static final Direction[] VERTICALS = {Direction.WEST, Direction.EAST};

  /** Layers in the model */
  private final List<LayerData> layers;

  public MantleItemLayerModel(StandardModelParameters parameters, List<LayerData> layers) {
    super(parameters);
    this.layers = layers;
  }

  /** Gets the layer at the given index */
  private LayerData getLayer(int index) {
    return LogicHelper.getOrDefault(layers, index, LayerData.DEFAULT);
  }

  /** Gets the default render type for an item layer, using the model's render type hint if present */
  public static RenderTypeGroup getDefaultRenderType(RenderTypeGroup renderTypeHint) {
    if (!renderTypeHint.isEmpty()) {
      return renderTypeHint;
    } else {
      return new RenderTypeGroup(RenderType.translucent(), NeoForgeRenderTypes.ITEM_UNSORTED_TRANSLUCENT.get());
    }
  }

  /**
   * Applies the transformation to the model state for an item layer model.
   */
  public static ModelState applyTransform(ModelState modelState, Transformation transformation) {
    if (transformation.isIdentity()) {
      return modelState;
    } else {
      return UnbakedElementsHelper.composeRootTransformIntoModelState(modelState, transformation);
    }
  }

  @Override
  public BakedModel bake(TextureSlots textureSlots, ModelBaker baker, ModelState modelTransform, boolean useAmbientOcclusion, boolean useBlockLight, ItemTransforms itemTransforms, ContextMap additionalProperties) {
    // gather the layer materials from the resolved texture slots
    List<Material> textures = new ArrayList<>();
    for (int i = 0; ; i++) {
      Material material = textureSlots.getMaterial("layer" + i);
      if (material == null) {
        break;
      }
      textures.add(material);
    }
    if (textures.isEmpty()) {
      throw new IllegalStateException("Empty textures list");
    }
    Function<Material,TextureAtlasSprite> spriteGetter = baker.sprites()::get;
    // determine particle texture
    Material particleMaterial = textureSlots.getMaterial("particle");
    TextureAtlasSprite particle = spriteGetter.apply(particleMaterial != null ? particleMaterial : textures.get(0));

    // setup quad building
    record QuadGroup(RenderTypeGroup renderType, Collection<BakedQuad> quads) {}
    ReversedListBuilder<QuadGroup> quadBuilder = new ReversedListBuilder<>();
    ItemLayerPixels pixels = textures.size() == 1 ? null : new ItemLayerPixels();
    Transformation rootTransform = additionalProperties.getOrDefault(NeoForgeModelProperties.TRANSFORM, Transformation.identity());
    modelTransform = applyTransform(modelTransform, rootTransform);

    // setup render types
    RenderTypeGroup normalRenderTypes = getDefaultRenderType(additionalProperties.getOrDefault(NeoForgeModelProperties.RENDER_TYPE, RenderTypeGroup.EMPTY));

    // skip the pixel tracking if using a single texture only
    Transformation transform = modelTransform.getRotation();
    for (int i = textures.size() - 1; i >= 0; i--) {
      TextureAtlasSprite sprite = spriteGetter.apply(textures.get(i));
      LayerData data = getLayer(i);
      quadBuilder.add(new QuadGroup(data.getRenderType(normalRenderTypes), getQuadsForSprite(data.color(), data.noTint() ? -1 : i, sprite, transform, data.luminosity(), pixels)));
    }

    // build final model (item layers are flat, so gui3d is false)
    UnbakedCompositeModel.Baked.Builder modelBuilder = UnbakedCompositeModel.Baked.builder(useAmbientOcclusion, false, useBlockLight, particle, itemTransforms);
    quadBuilder.build(quadGroup -> modelBuilder.addQuads(quadGroup.renderType, quadGroup.quads));
    return modelBuilder.build();
  }

  /**
   * Gets all quads for an item layer for the given sprite
   * @param color       Color for the sprite in AARRGGBB format.
   * @param tint        Tint index for {@link net.minecraft.client.color.block.BlockColors} and {@link net.minecraft.client.color.item.ItemColors}. Generally unused
   * @param sprite      Sprite to convert into quads
   * @param transform   Transforms to apply
   * @param luminosity  Extra light to add to the quad from 0-15, makes it appear to glow a bit
   * @return  List of baked quads
   */
  public static List<BakedQuad> getQuadsForSprite(int color, int tint, TextureAtlasSprite sprite, Transformation transform, int luminosity) {
    return getQuadsForSprite(color, tint, sprite, transform, luminosity, null);
  }

  /**
   * Gets all quads including side quads for an item layer for the given sprite.
   * @param color       Color for the sprite in AARRGGBB format.
   * @param tint        Tint index for {@link net.minecraft.client.color.block.BlockColors} and {@link net.minecraft.client.color.item.ItemColors}. Generally unused
   * @param sprite      Sprite to convert into quads
   * @param transform   Transforms to apply
   * @param emissivity  Extra light to add to the quad from 0-15, makes it appear to glow a bit
   * @param pixels      Object to keep track of used pixels across multiple layers to help prevent z-fighting. To effective use, sprites must be built in reverse order. Use null to skip this logic
   * @return  List of baked quads
   * @see #getQuadForGui(int, int, TextureAtlasSprite, Transformation, int)
   */
  public static List<BakedQuad> getQuadsForSprite(int color, int tint, TextureAtlasSprite sprite, Transformation transform, int emissivity, @Nullable ItemLayerPixels pixels) {
    return getMaskedQuadsForSprite(color, tint, sprite, sprite, transform, emissivity, pixels);
  }

  /**
   * Like {@link #getQuadsForSprite(int, int, TextureAtlasSprite, Transformation, int, ItemLayerPixels)}, but takes the
   * item-layer shape (opaque pixel outline + side quads) from {@code maskSprite} while mapping {@code textureSprite} onto
   * it. This reproduces the removed {@code UnbakedGeometryHelper.createUnbakedItemMaskElements}/{@code bakeElements}
   * mask-then-bake flow, used to clip a fluid texture to a container/template shape.
   * <p>
   * Both sprites are expected to share the same pixel dimensions (both are 16x16 for Tinkers' templates and fluids); the
   * geometry grid is taken from the mask, so a differently sized texture sprite is simply UV-mapped across that grid.
   * @param color         Color for the sprite in AARRGGBB format.
   * @param tint          Tint index. Generally unused (colors are baked in)
   * @param maskSprite    Sprite whose opaque pixels define the item-layer shape
   * @param textureSprite Sprite mapped onto the shape
   * @param transform     Transforms to apply
   * @param emissivity    Extra light to add to the quad from 0-15
   * @param pixels        Pixel tracker to reduce z-fighting between layers, or null
   */
  public static List<BakedQuad> getMaskedQuadsForSprite(int color, int tint, TextureAtlasSprite maskSprite, TextureAtlasSprite textureSprite, Transformation transform, int emissivity, @Nullable ItemLayerPixels pixels) {
    List<BakedQuad> builder = new ArrayList<>();

    SpriteContents contents = maskSprite.contents();
    int uMax = contents.width();
    int vMax = contents.height();
    FaceData faceData = new FaceData(uMax, vMax);
    boolean translucent = false;

    PrimitiveIterator.OfInt iterator = maskSprite.contents().getUniqueFrames().iterator();
    boolean hasFrames = iterator.hasNext();
    while (iterator.hasNext()) {
      int f = iterator.nextInt();
      boolean ptu;
      boolean[] ptv = new boolean[uMax];
      Arrays.fill(ptv, true);
      for(int v = 0; v < vMax; v++) {
        ptu = true;
        for(int u = 0; u < uMax; u++) {
          int alpha = maskSprite.getPixelRGBA(f, u, vMax - v - 1) >> 24 & 0xFF;
          boolean t = alpha / 255f <= 0.1f;

          if (!t && alpha < 255) {
            translucent = true;
          }

          if(ptu && !t) { // left - transparent, right - opaque
            faceData.set(Direction.WEST, u, v);
          }
          if(!ptu && t) { // left - opaque, right - transparent
            faceData.set(Direction.EAST, u-1, v);
          }
          if(ptv[u] && !t) { // up - transparent, down - opaque
            faceData.set(Direction.UP, u, v);
          }
          if(!ptv[u] && t) { // up - opaque, down - transparent
            faceData.set(Direction.DOWN, u, v-1);
          }

          ptu = t;
          ptv[u] = t;
        }
        if(!ptu) { // last - opaque
          faceData.set(Direction.EAST, uMax-1, v);
        }
      }
      // last line
      for(int u = 0; u < uMax; u++) {
        if(!ptv[u]) {
          faceData.set(Direction.DOWN, u, vMax-1);
        }
      }
    }

    // setup quad builder; in 1.21.1 the consumer no longer auto-emits, so collect baked quads into the output list
    QuadBakingVertexConsumer quadBuilder = new QuadBakingVertexConsumer() {
      @Override
      public BakedQuad bakeQuad() {
        BakedQuad quad = super.bakeQuad();
        builder.add(quad);
        return quad;
      }
    };
    // common settings
    quadBuilder.setSprite(textureSprite);
    quadBuilder.setTintIndex(tint);
    // TODO: should we customize these?
    quadBuilder.setShade(false);
    quadBuilder.setHasAmbientOcclusion(true);
    // only need to set up transforms once, isn't that nice?
    VertexConsumer quadConsumer = quadBuilder;
    if (!transform.isIdentity()) {
      quadConsumer = new TransformingVertexPipeline(quadBuilder, transform);
    }

    // horizontal quads
    for (Direction facing : HORIZONTALS) {
      for (int v = 0; v < vMax; v++) {
        int uStart = 0, uEnd = uMax;
        boolean building = false;
        for (int u = 0; u < uMax; u++) {
          boolean canDraw = pixels == null || !pixels.get(u, v, uMax, vMax);
          boolean face = canDraw && faceData.get(facing, u, v);
          // set the end for translucent to draw right after this pixel
          if (face) {
            uEnd = u + 1;
            // if not currently building and we have data, start new quad
            if (!building) {
              building = true;
              uStart = u;
            }
          }
          // make quad [uStart, u]
          else if (building) {
            // finish current quad if translucent (minimize overdraw) or we are forbidden from touching this pixel (previous layer drew here)
            if (!canDraw || translucent) {
              int off = facing == Direction.DOWN ? 1 : 0;
              buildSideQuad(quadBuilder, quadConsumer, facing, color, textureSprite,uStart, v + off, uEnd - uStart, emissivity);
              building = false;
            }
          }
        }
        if (building) { // build remaining quad
          // make quad [uStart, uEnd]
          int off = facing == Direction.DOWN ? 1 : 0;
          buildSideQuad(quadBuilder, quadConsumer, facing, color, textureSprite,uStart, v+off, uEnd-uStart, emissivity);
        }
      }
    }

    // vertical quads
    for (Direction facing : VERTICALS) {
      for (int u = 0; u < uMax; u++) {
        int vStart = 0, vEnd = vMax;
        boolean building = false;
        for (int v = 0; v < vMax; v++) {
          boolean canDraw = pixels == null || !pixels.get(u, v, uMax, vMax);
          boolean face = canDraw && faceData.get(facing, u, v);
          // set the end for translucent to draw right after this pixel
          if (face) {
            vEnd = v + 1;
            // if not currently building and we have data, start new quad
            if (!building) {
              building = true;
              vStart = v;
            }
          }
          // make quad [vStart, v]
          else if (building) {
            // finish current quad if translucent (minimize overdraw) or we are forbidden from touching this pixel (future layer drew here)
            if (!canDraw || translucent) {
              int off = facing == Direction.EAST ? 1 : 0;
              buildSideQuad(quadBuilder, quadConsumer, facing, color, textureSprite,u + off, vStart, vEnd - vStart, emissivity);
              building = false;
            }
          }
        }
        if (building) { // build remaining quad
          // make quad [vStart, vEnd]
          int off = facing == Direction.EAST ? 1 : 0;
          buildSideQuad(quadBuilder, quadConsumer, facing, color, textureSprite,u+off, vStart, vEnd-vStart, emissivity);
        }
      }
    }

    // back
    buildQuad(quadBuilder, quadConsumer, Direction.NORTH, color, emissivity,
              0, 0, 7.5f / 16f, textureSprite.getU0(), textureSprite.getV1(),
              0, 1, 7.5f / 16f, textureSprite.getU0(), textureSprite.getV0(),
              1, 1, 7.5f / 16f, textureSprite.getU1(), textureSprite.getV0(),
              1, 0, 7.5f / 16f, textureSprite.getU1(), textureSprite.getV1());
    // front
    buildQuad(quadBuilder, quadConsumer, Direction.SOUTH, color, emissivity,
              0, 0, 8.5f / 16f, textureSprite.getU0(), textureSprite.getV1(),
              1, 0, 8.5f / 16f, textureSprite.getU1(), textureSprite.getV1(),
              1, 1, 8.5f / 16f, textureSprite.getU1(), textureSprite.getV0(),
              0, 1, 8.5f / 16f, textureSprite.getU0(), textureSprite.getV0());

    // fill in the pixel map with new pixels from the sprite
    if (pixels != null) {
      // animated textures are tricky, as we have three choices:
      //  1. if a pixel is only potentially there, don't draw lower layers - leads to gaps
      //  2. if a pixel is only potentially there, always draw lower layers - leads to z-fighting
      //  3. only use the first frame
      // of these, 2 would give the most accurate result. However, its also the hardest to calculate
      // of the remaining methods, 3 is both more accurate and easier to calculate than 1, so I opted for that approach
      if (hasFrames) {
        for(int v = 0; v < vMax; v++) {
          for(int u = 0; u < uMax; u++) {
            int alpha = maskSprite.getPixelRGBA(0, u, vMax - v - 1) >> 24 & 0xFF;
            if (alpha / 255f > 0.1f) {
              pixels.set(u, v, uMax, vMax);
            }
          }
        }
      }
    }

    return List.copyOf(builder);
  }

  /**
   * Gets the quad to display in GUIs for the given sprite. Unlike {@link #getQuadsForSprite(int, int, TextureAtlasSprite, Transformation, int, ItemLayerPixels)}, this method will not build side quads as GUIs don't see those.
   * This method is notably more efficient when you are only building a GUI model, if you want a full model its probably more efficient to call the other method then filter out {@link Direction#SOUTH} quads.
   * @param color       Color for the sprite in AARRGGBB format.
   * @param tint        Tint index for {@link net.minecraft.client.color.block.BlockColors} and {@link net.minecraft.client.color.item.ItemColors}. Generally unused
   * @param sprite      Sprite to convert into quads
   * @param transform   Transforms to apply
   * @param emissivity  Extra light to add to the quad from 0-15, makes it appear to glow a bit
   * @return  List of baked quads
   * @see #getQuadsForSprite(int, int, TextureAtlasSprite, Transformation, int, ItemLayerPixels)
   */
  @SuppressWarnings("unused")  // API
  public static BakedQuad getQuadForGui(int color, int tint, TextureAtlasSprite sprite, Transformation transform, int emissivity) {
    // setup quad builder
    QuadBakingVertexConsumer quadBuilder = new QuadBakingVertexConsumer();
    // common settings
    quadBuilder.setSprite(sprite);
    quadBuilder.setTintIndex(tint);
    // TODO: should we customize these?
    quadBuilder.setShade(false);
    quadBuilder.setHasAmbientOcclusion(true);
    // only need to set up transforms once, isn't that nice?
    VertexConsumer quadConsumer = quadBuilder;
    if (!transform.isIdentity()) {
      quadConsumer = new TransformingVertexPipeline(quadBuilder, transform);
    }
    // only need south; buildQuad bakes and returns the quad after writing the four vertices
    return buildQuad(quadBuilder, quadConsumer, Direction.SOUTH, color, emissivity,
              0, 0, 8.5f / 16f, sprite.getU0(), sprite.getV1(),
              1, 0, 8.5f / 16f, sprite.getU1(), sprite.getV1(),
              1, 1, 8.5f / 16f, sprite.getU1(), sprite.getV0(),
              0, 1, 8.5f / 16f, sprite.getU0(), sprite.getV0());
  }

  /**
   * Builds a single quad on the side of the sprite
   * @param builder      Quad builder instance, just used to set direction so we can avoid redundancy
   * @param consumer     Quad consumer, where we place the actual quad
   * @param side       Side to build
   * @param color      Color for the sprite
   * @param sprite     Sprite to render
   * @param u          Sprite U
   * @param v          Sprite V
   * @param size       Size of the quad in the correct direction (depth is always 1 pixel)
   * @param luminosity Extra light to add to the quad between 0 and 15
   */
  private static void buildSideQuad(QuadBakingVertexConsumer builder, VertexConsumer consumer, Direction side, int color, TextureAtlasSprite sprite, int u, int v, int size, int luminosity) {
    final float eps = 1e-2f;
    SpriteContents contents = sprite.contents();
    int width = contents.width();
    int height = contents.height();
    float x0 = (float) u / width;
    float y0 = (float) v / height;
    float x1 = x0, y1 = y0;
    float z0 = 7.5f / 16f, z1 = 8.5f / 16f;
    switch(side) {
      case WEST:
        z0 = 8.5f / 16f;
        z1 = 7.5f / 16f;
        // continue into EAST
      case EAST:
        y1 = (float) (v + size) / height;
        break;
      case DOWN:
        z0 = 8.5f / 16f;
        z1 = 7.5f / 16f;
        // continue into UP
      case UP:
        x1 = (float) (u + size) / width;
        break;
      default:
        throw new IllegalArgumentException("can't handle z-oriented side");
    }

    // for the side, Y axis's use of getOpposite is related to the swapping of V direction
    float dx = side.getStepX() * eps / width;
    float dy = side.getStepY() * eps / height;
    // 1.21: TextureAtlasSprite.getU/getV take a normalized [0,1] coordinate (1.20 took [0,16]) — no *16
    float u0 = x0 - dx;
    float u1 = x1 - dx;
    float v0 = 1f - y0 - dy;
    float v1 = 1f - y1 - dy;
    buildQuad(builder, consumer, (side.getAxis() == Axis.Y ? side.getOpposite() : side),
      color, luminosity,
      x0, y0, z0, sprite.getU(u0), sprite.getV(v0),
      x1, y1, z0, sprite.getU(u1), sprite.getV(v1),
      x1, y1, z1, sprite.getU(u1), sprite.getV(v1),
      x0, y0, z1, sprite.getU(u0), sprite.getV(v0));
  }

  /**
   * Builds a single quad in the model, based on the method in {@link ItemLayerModel} but with color added
   * @param builder      Quad builder instance, just used to set direction so we can avoid redundancy
   * @param consumer     Quad consumer, where we place the actual quad
   * @param side         Quad side
   * @param color        Color for the sprite in AARRGGBB format
   * @param luminosity Extra light to add to the quad between 0 and 15
   */
  public static BakedQuad buildQuad(QuadBakingVertexConsumer builder, VertexConsumer consumer, Direction side, int color, int luminosity,
                                     float x0, float y0, float z0, float u0, float v0,
                                     float x1, float y1, float z1, float u1, float v1,
                                     float x2, float y2, float z2, float u2, float v2,
                                     float x3, float y3, float z3, float u3, float v3) {
    builder.setDirection(side);
    putVertex(consumer, side, x0, y0, z0, u0, v0, color, luminosity);
    putVertex(consumer, side, x1, y1, z1, u1, v1, color, luminosity);
    putVertex(consumer, side, x2, y2, z2, u2, v2, color, luminosity);
    putVertex(consumer, side, x3, y3, z3, u3, v3, color, luminosity);
    // in 1.21.1 the quad must be explicitly baked after the four vertices are written
    return builder.bakeQuad();
  }

  /**
   * Clone of the method in {@link ItemLayerModel} with the color parameter added
   * @param consumer   Vertex consumer
   * @param side       Side for the quad
   * @param x          Quad X position
   * @param y          Quad Y position
   * @param z          Quad Z position
   * @param u          Quad texture U
   * @param v          Quad texture V
   * @param color      Quad color in AARRGGBB format
   * @param luminosity Extra light to add to the quad between 0 and 15
   */
  private static void putVertex(VertexConsumer consumer, Direction side, float x, float y, float z, float u, float v, int color, int luminosity) {
    // format is always DefaultVertexFormat#BLOCK, though order does not matter too much
    int light = (luminosity << 4);
    consumer.addVertex(x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setUv2(light, light)
            .setNormal(side.getStepX(), side.getStepY(), side.getStepZ());
  }

  /** Cloned from {@link ItemLayerModel}'s FaceData subclass */
  private static class FaceData {
    private final EnumMap<Direction,BitSet> data = new EnumMap<>(Direction.class);
    private final int vMax;

    FaceData(int uMax, int vMax) {
      this.vMax = vMax;

      data.put(Direction.WEST, new BitSet(uMax * vMax));
      data.put(Direction.EAST, new BitSet(uMax * vMax));
      data.put(Direction.UP,   new BitSet(uMax * vMax));
      data.put(Direction.DOWN, new BitSet(uMax * vMax));
    }

    public void set(Direction facing, int u, int v) {
      data.get(facing).set(getIndex(u, v));
    }

    public boolean get(Direction facing, int u, int v) {
      return data.get(facing).get(getIndex(u, v));
    }

    private int getIndex(int u, int v) {
      return v * vMax + u;
    }
  }

  /**
   * Class holding details about a single layer in the model
   */
  public record LayerData(int color, int luminosity, boolean noTint, @Nullable ResourceLocation renderType) {
    public static final LayerData DEFAULT = new LayerData(-1, 0, false, null);
    public static final RecordLoadable<LayerData> LOADABLE = RecordLoadable.create(
      ColorLoadable.ALPHA.defaultField("color", false, LayerData::color),
      // TODO: rename this field?
      IntLoadable.range(0, 15).defaultField("luminosity", 0, LayerData::luminosity),
      BooleanLoadable.INSTANCE.defaultField("no_tint", false, false, LayerData::noTint),
      Loadables.RESOURCE_LOCATION.nullableField("render_type", LayerData::renderType),
      LayerData::new);
    public static final Loadable<List<LayerData>> LIST_LOADABLE = LOADABLE.list(1);

    /** Gets the render type for this layer, falling back to the passed type if not requested */
    public RenderTypeGroup getRenderType(RenderTypeGroup defaultType) {
      if (renderType == null) {
        return defaultType;
      }
      return NamedRenderTypeManager.get(renderType);
    }

    /** @deprecated use {@link #LOADABLE} */
    @Deprecated(forRemoval = true)
    public static LayerData fromJson(JsonObject json) {
      return LOADABLE.deserialize(json);
    }

    /** @deprecated use {@link #LOADABLE} */
    @Deprecated(forRemoval = true)
    public JsonObject toJson() {
      JsonObject json = new JsonObject();
      LOADABLE.serialize(this, json);
      return json;
    }
  }

  /** Deserializes this model from JSON */
  public static MantleItemLayerModel deserialize(JsonObject json, JsonDeserializationContext context) {
    return new MantleItemLayerModel(StandardModelParameters.parse(json, context), LayerData.LIST_LOADABLE.getOrDefault(json, "layers", List.of()));
  }
}
