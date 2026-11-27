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
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
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
  protected ShapedRetexturedRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern, ItemStackTemplate result, Ingredient texture, boolean matchAll) {
    super(commonInfo, bookInfo, pattern, result);
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
    // ShapedRecipe.pattern is public final and Mantle's access transformer makes the result public. Read it directly rather
    // than via ShapedRecipe#display(): building the display streams the recipe's ingredients, which fails at datagen for
    // tag/custom ingredients whose contents are not bound yet. 26.1: the common and book info are rebuilt from the accessors.
    this(new Recipe.CommonInfo(orig.showNotification()), new CraftingRecipe.CraftingBookInfo(orig.category(), orig.group()), orig.pattern, orig.result, texture, matchAll);
  }

  /** Mantle's own result accessor; {@link net.minecraft.world.item.crafting.Recipe} dropped getResultItem in 1.21.4. 26.1: builds a new stack from the result template */
  public ItemStack getResultItem(HolderLookup.Provider access) {
    return result.create();
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
  public ItemStack assemble(CraftingInput input) {
    ItemStack result = super.assemble(input);
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

  @SuppressWarnings("unchecked")
  @Override
  public RecipeSerializer<ShapedRecipe> getSerializer() {
    // cast to the type the vanilla parent declares (invariant RecipeSerializer)
    return (RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>) MantleRecipes.CRAFTING_SHAPED_RETEXTURED.get();
  }

  /**
   * Serializer codecs for {@link ShapedRetexturedRecipe}; 26.1: RecipeSerializer is a record of the two codecs.
   * TODO PORT (stage 7 datagen ingredients): the 1.20 serializer supported a single-character {@code texture} key that
   * referenced a symbol in the shaped {@code key} map. In 1.21 the {@link ShapedRecipePattern} is opaque (no key-map
   * access from the recipe), so {@code texture} is now always a full ingredient object. If the key-char sugar is needed
   * for datagen ergonomics it must be reintroduced in the builder (see {@link ShapedRetexturedRecipeBuilder}).
   */
  public static final MapCodec<ShapedRetexturedRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
    Ingredient.CODEC.fieldOf("texture").forGetter(r -> r.texture),
    com.mojang.serialization.Codec.BOOL.optionalFieldOf("match_all", false).forGetter(r -> r.matchAll),
    ShapedRecipe.MAP_CODEC.forGetter(r -> r)
  ).apply(inst, (texture, matchAll, base) -> new ShapedRetexturedRecipe(base, texture, matchAll)));

  public static final StreamCodec<RegistryFriendlyByteBuf,ShapedRetexturedRecipe> STREAM_CODEC = StreamCodec.composite(
    ShapedRecipe.STREAM_CODEC, r -> r,
    Ingredient.CONTENTS_STREAM_CODEC, r -> r.texture,
    ByteBufCodecs.BOOL, r -> r.matchAll,
    (base, texture, matchAll) -> new ShapedRetexturedRecipe(base, texture, matchAll));
}
