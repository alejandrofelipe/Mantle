package slimeknights.mantle.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;

import javax.annotation.Nullable;

/**
 * Helpers to aid in reading and writing of NBT
 */
@SuppressWarnings("unused")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TagHelper {
  /* BlockPos */

  /** @deprecated use {@link BlockPos#CODEC} */
  @Deprecated(forRemoval = true)
  public static CompoundTag writePos(BlockPos pos) {
    CompoundTag tag = new CompoundTag();
    tag.putInt("X", pos.getX());
    tag.putInt("Y", pos.getY());
    tag.putInt("Z", pos.getZ());
    return tag;
  }

  /** @deprecated use {@link BlockPos#CODEC} */
  @Nullable
  @Deprecated(forRemoval = true)
  public static BlockPos readPos(CompoundTag tag) {
    if (tag.get("X") instanceof NumericTag x && tag.get("Y") instanceof NumericTag y && tag.get("Z") instanceof NumericTag z) {
      return new BlockPos(x.intValue(), y.intValue(), z.intValue());
    }
    return null;
  }
}
