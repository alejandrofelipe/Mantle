package slimeknights.mantle.recipe.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import slimeknights.mantle.recipe.MantleRecipes;
import slimeknights.mantle.util.RetexturedHelper;

/** Recipe which sets the texture for a {@link slimeknights.mantle.block.RetexturedBlock} based on an ingredient input. */
// TODO 1.21: rework to be more like the ShapedMaterialsRecipe from Tinkers for more efficient network syncing
@SuppressWarnings("WeakerAccess")
public class ShapedRetexturedRecipe extends ShapedRecipe {
  /** Ingredient used to determine the texture on the output */
  @Getter
  private final Ingredient texture;
  private final boolean matchAll;

  /** Creates a new recipe using the passed parameters */
  protected ShapedRetexturedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification, Ingredient texture, boolean matchAll) {
    super(group, category, pattern, result, showNotification);
    this.texture = texture;
    this.matchAll = matchAll;
  }

  /**
   * Creates a new recipe using an existing shaped recipe
   * @param orig       Shaped recipe to copy
   * @param texture    Ingredient to use for the texture
   * @param matchAll   If true, all inputs must match for the recipe to match
   */
  protected ShapedRetexturedRecipe(ShapedRecipe orig, Ingredient texture, boolean matchAll) {
    this(orig.getGroup(), orig.category(), orig.getPattern(), orig.result, orig.showNotification(), texture, matchAll);
  }

  /**
   * Gets the output using the given texture
   * @param texture  Texture to use
   * @return  Output with texture. Will be blank if the input is not a block
   */
  public ItemStack getResultItem(Item texture, HolderLookup.Provider access) {
    return RetexturedHelper.setTexture(getResultItem(access).copy(), Block.byItem(texture));
  }

  @Override
  public ItemStack assemble(CraftingInput input, HolderLookup.Provider access) {
    ItemStack result = super.assemble(input, access);
    Block currentTexture = null;
    for (int i = 0; i < input.size(); i++) {
      ItemStack stack = input.getItem(i);
      if (!stack.isEmpty() && texture.test(stack)) {
        // fetch texture from the block if it has one
        Block block = RetexturedHelper.getTexture(stack);
        // assuming it does not, use the block itself as the texture (provided it is not the result that is)
        if (block == Blocks.AIR && stack.getItem() != result.getItem()) {
          block = Block.byItem(stack.getItem());
        }
        // if no texture, skip
        if (block == Blocks.AIR) {
          continue;
        }

        // if we have not found a texture yet, store the found block
        if (currentTexture == null) {
          currentTexture = block;
          // match all means we must check the rest. If not match all, we can be done
          if (!matchAll) {
            break;
          }

          // if we found a texture before, must match or we do no texture
        } else if (currentTexture != block) {
          currentTexture = null;
          break;
        }
      }
    }

    // set the texture if found. No texture will use the fallback
    if (currentTexture != null) {
      return RetexturedHelper.setTexture(result, currentTexture);
    }
    return result;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return MantleRecipes.CRAFTING_SHAPED_RETEXTURED.get();
  }

  /**
   * Serializer for {@link ShapedRetexturedRecipe}.
   * TODO PORT (stage 7 datagen ingredients): the 1.20 serializer supported a single-character {@code texture} key that
   * referenced a symbol in the shaped {@code key} map. In 1.21 the {@link ShapedRecipePattern} is opaque (no key-map
   * access from the recipe), so {@code texture} is now always a full ingredient object. If the key-char sugar is needed
   * for datagen ergonomics it must be reintroduced in the builder (see {@link ShapedRetexturedRecipeBuilder}).
   */
  public static class Serializer implements RecipeSerializer<ShapedRetexturedRecipe> {
    private static final MapCodec<ShapedRetexturedRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
      Ingredient.CODEC.fieldOf("texture").forGetter(r -> r.texture),
      com.mojang.serialization.Codec.BOOL.optionalFieldOf("match_all", false).forGetter(r -> r.matchAll),
      ShapedRecipe.Serializer.CODEC.forGetter(r -> (ShapedRecipe) r)
    ).apply(inst, (texture, matchAll, base) -> new ShapedRetexturedRecipe(base, texture, matchAll)));

    private static final StreamCodec<RegistryFriendlyByteBuf,ShapedRetexturedRecipe> STREAM_CODEC = StreamCodec.composite(
      ShapedRecipe.Serializer.STREAM_CODEC, r -> r,
      Ingredient.CONTENTS_STREAM_CODEC, r -> r.texture,
      ByteBufCodecs.BOOL, r -> r.matchAll,
      (base, texture, matchAll) -> new ShapedRetexturedRecipe(base, texture, matchAll));

    @Override
    public MapCodec<ShapedRetexturedRecipe> codec() {
      return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf,ShapedRetexturedRecipe> streamCodec() {
      return STREAM_CODEC;
    }
  }
}
