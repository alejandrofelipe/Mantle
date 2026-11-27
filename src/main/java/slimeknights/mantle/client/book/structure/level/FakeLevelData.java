// Credit to Immersive Engineering and blusunrize for this class
// See: https://github.com/BluSunrize/ImmersiveEngineering/blob/1.18/src/main/java/blusunrize/immersiveengineering/common/util/fakeworld/FakeSpawnInfo.java
package slimeknights.mantle.client.book.structure.level;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.storage.WritableLevelData;

public class FakeLevelData implements WritableLevelData {

  // 26.1: day time and weather moved off the level data (world clocks, weather on the level)
  // 1.21.9: spawn position + angle merged into LevelData.RespawnData (dimension, pos, yaw, pitch)
  private RespawnData respawnData = RespawnData.DEFAULT;

  @Override
  public void setSpawn(RespawnData spawn) {
    this.respawnData = spawn;
  }

  @Override
  public RespawnData getRespawnData() {
    return this.respawnData;
  }

  @Override
  public long getGameTime() {
    return 0;
  }

  @Override
  public boolean isHardcore() {
    return false;
  }

  @Override
  public Difficulty getDifficulty() {
    return Difficulty.PEACEFUL;
  }

  @Override
  public boolean isDifficultyLocked() {
    return false;
  }
}
