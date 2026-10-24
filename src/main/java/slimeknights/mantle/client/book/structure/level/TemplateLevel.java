// Credit to Immersive Engineering and blusunrize for this class
// See: https://github.com/BluSunrize/ImmersiveEngineering/blob/1.18/src/main/java/blusunrize/immersiveengineering/common/util/fakeworld/TemplateWorld.java
package slimeknights.mantle.client.book.structure.level;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.model.data.ModelData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * World implementation for the book structures
 */
public class TemplateLevel extends Level {

  private final Map<MapId, MapItemSavedData> maps = new HashMap<>();
  private final Scoreboard scoreboard = new Scoreboard();
  private final net.minecraft.world.TickRateManager tickRateManager = new net.minecraft.world.TickRateManager();
  private final TemplateChunkSource chunkSource;

  public TemplateLevel(List<StructureBlockInfo> blocks, Predicate<BlockPos> shouldShow) {
    // 1.21.4: the Level constructor dropped the profiler supplier argument
    super(
      new FakeLevelData(), Level.OVERWORLD, Objects.requireNonNull(Minecraft.getInstance().level).registryAccess(),
      Objects.requireNonNull(Minecraft.getInstance().level).registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD),
      true, false, 0, 0
    );

    this.chunkSource = new TemplateChunkSource(blocks, this, shouldShow);
  }

  @Override
  public void sendBlockUpdated(@Nonnull BlockPos pos, @Nonnull BlockState oldState, @Nonnull BlockState newState, int flags) {}

  // 1.21.5: the sound and level event sources widened from Player to Entity
  @Override
  public void playSeededSound(@Nullable Entity pEntity, double pX, double pY, double pZ, Holder<SoundEvent> pSound, SoundSource pSource, float pVolume, float pPitch, long pSeed) {}

  @Override
  public void playSeededSound(@Nullable Entity pEntity, Entity pSourceEntity, Holder<SoundEvent> pSound, SoundSource pCategory, float pVolume, float pPitch, long pSeed) {}

  @Override
  public String gatherChunkSourceStats() {
    return chunkSource.gatherStats();
  }

  @Nullable
  @Override
  public Entity getEntity(int id) {
    return null;
  }

  @Nullable
  @Override
  public MapItemSavedData getMapData(@Nonnull MapId mapId) {
    return this.maps.get(mapId);
  }

  // 1.21.5: setMapData/getFreeMapId moved off Level (server-side map storage), nothing to override

  @Override
  public void destroyBlockProgress(int breakerId, @Nonnull BlockPos pos, int progress) {}

  @Nonnull
  @Override
  public Scoreboard getScoreboard() {
    return this.scoreboard;
  }

  @Nonnull
  @Override
  public RecipeAccess recipeAccess() {
    // 1.21.4: Level#getRecipeManager became Level#recipeAccess; delegate to the real client level
    return Objects.requireNonNull(Minecraft.getInstance().level).recipeAccess();
  }

  @Nonnull
  @Override
  public FuelValues fuelValues() {
    // 1.21.4: new abstract Level method; delegate to the real client level
    return Objects.requireNonNull(Minecraft.getInstance().level).fuelValues();
  }

  @Override
  public void explode(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator calculator, double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction interaction, ParticleOptions smallParticle, ParticleOptions largeParticle, Holder<SoundEvent> sound) {}

  @Nonnull
  @Override
  public Collection<PartEntity<?>> dragonParts() {
    return List.of();
  }

  @Override
  protected LevelEntityGetter<Entity> getEntities() {
    return FakeEntityGetter.INSTANCE;
  }

  @Nonnull
  @Override
  public LevelTickAccess<Block> getBlockTicks() {
    return BlackholeTickAccess.emptyLevelList();
  }

  @Nonnull
  @Override
  public LevelTickAccess<Fluid> getFluidTicks() {
    return BlackholeTickAccess.emptyLevelList();
  }

  @Nonnull
  @Override
  public ChunkSource getChunkSource() {
    return this.chunkSource;
  }

  /** 1.21.5: block state models read block entity data through the level, so serve it straight from the template's block entities */
  @Override
  public ModelData getModelData(BlockPos pos) {
    BlockEntity blockEntity = getBlockEntity(pos);
    return blockEntity != null ? blockEntity.getModelData() : ModelData.EMPTY;
  }

  @Override
  public void levelEvent(@Nullable Entity entity, int type, @Nonnull BlockPos pos, int data) {}

  @Override
  public void gameEvent(Holder<GameEvent> pEvent, Vec3 pPosition, Context pContext) {}

  @Override
  public void setDayTimePerTick(float dayTimePerTick) {}

  @Override
  public float getDayTimePerTick() {
    return 0;
  }

  @Override
  public void setDayTimeFraction(float dayTimeFraction) {}

  @Override
  public float getDayTimeFraction() {
    return 0;
  }

  @Override
  public net.minecraft.world.TickRateManager tickRateManager() {
    return this.tickRateManager;
  }

  @Override
  public net.minecraft.world.item.alchemy.PotionBrewing potionBrewing() {
    return net.minecraft.world.item.alchemy.PotionBrewing.EMPTY;
  }

  @Override
  public FeatureFlagSet enabledFeatures() {
    return FeatureFlagSet.of();
  }

  @Override
  public float getShade(@Nonnull Direction p_230487_1_, boolean p_230487_2_) {
    return 1;
  }

  @Override
  public int getSeaLevel() {
    // 1.21.4: LevelReader#getSeaLevel became abstract; the fake world mimics the overworld
    return 63;
  }

  @Nonnull
  @Override
  public List<? extends Player> players() {
    return List.of();
  }

  @Nonnull
  @Override
  public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
    return registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
  }
}
