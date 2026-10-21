package slimeknights.mantle.fluid.texture;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import slimeknights.mantle.fluid.InvertedFluidType;
import slimeknights.mantle.fluid.TextureFluidType;
import slimeknights.mantle.registration.deferred.SynchronizedDeferredRegister;

/**
 * Client-side registration of {@link net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions} for the
 * fluid types created by {@link slimeknights.mantle.registration.deferred.FluidDeferredRegister}.
 * 1.21.4 replaces {@code FluidType.initializeClient} with {@link RegisterClientExtensionsEvent}, so the client extension is
 * attached here rather than on the fluid type itself. This class is only loaded on the client (guarded by a dist check at
 * the call site), keeping the client-only references off the dedicated server.
 */
public class MantleClientFluidTypes {
  private MantleClientFluidTypes() {}

  /** Adds a listener registering the client extensions for every Mantle fluid type held by the given register */
  public static void registerClientExtensions(IEventBus modBus, SynchronizedDeferredRegister<FluidType> register) {
    modBus.addListener(RegisterClientExtensionsEvent.class, event -> {
      for (DeferredHolder<FluidType, ? extends FluidType> holder : register.getEntries()) {
        FluidType type = holder.get();
        // inverted must be checked first as it extends the texture client type conceptually
        if (type instanceof InvertedFluidType) {
          event.registerFluidType(new ClientInvertedFluidType(type), type);
        } else if (type instanceof TextureFluidType) {
          event.registerFluidType(new ClientTextureFluidType(type), type);
        }
      }
    });
  }
}
