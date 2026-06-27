package slimeknights.mantle.registration;

import net.minecraft.world.level.block.entity.BlockEntityType;
import slimeknights.mantle.block.entity.MantleHangingSignBlockEntity;
import slimeknights.mantle.block.entity.MantleSignBlockEntity;

import javax.annotation.Nullable;

import static slimeknights.mantle.registration.RegistrationHelper.injected;

/**
 * Various objects registered under Mantle
 */
public class MantleRegistrations {
  private MantleRegistrations() {}

  // TODO PORT (later stage): NeoForge 1.21 removed Forge's @ObjectHolder injection
  // (net.minecraftforge.registries.ObjectHolder no longer exists). These two block entity types
  // were previously injected by the @ObjectHolder annotation after registration. They are now plain
  // mutable fields that must be assigned by the registration code (currently Mantle.java, which lives
  // outside the registration package) once the block entity types are registered. Until that wiring is
  // updated they will remain null. Suggested replacement: register the sign/hanging_sign block entity
  // types through a DeferredRegister/DeferredHolder and read the holder, or assign these fields in the
  // RegisterEvent handler.
  @Nullable
  public static BlockEntityType<MantleSignBlockEntity> SIGN = injected();
  @Nullable
  public static BlockEntityType<MantleHangingSignBlockEntity> HANGING_SIGN = injected();
}
