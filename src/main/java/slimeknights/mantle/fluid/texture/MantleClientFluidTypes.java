package slimeknights.mantle.fluid.texture;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import slimeknights.mantle.fluid.InvertedFluidType;
import slimeknights.mantle.fluid.TextureFluidType;
import slimeknights.mantle.registration.deferred.SynchronizedDeferredRegister;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side registration of {@link net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions} for the
 * fluid types created by {@link slimeknights.mantle.registration.deferred.FluidDeferredRegister}.
 * 1.21.4 replaces {@code FluidType.initializeClient} with {@link RegisterClientExtensionsEvent}, so the client extension is
 * attached here rather than on the fluid type itself. This class is only loaded on the client (guarded by a dist check at
 * the call site), keeping the client-only references off the dedicated server.
 */
public class MantleClientFluidTypes {
  private MantleClientFluidTypes() {}

  /**
   * Adds listeners registering the client extensions and the fluid models for every Mantle fluid type held by the given register.
   * 26.1: fluid textures and tint are no longer part of the client extensions, every fluid of a type with a {@link ClientTextureFluidType} gets the
   * fluid model from {@link ClientTextureFluidType#createFluidModel()}.
   */
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
    // posted on every resource reload while the models bake
    modBus.addListener(RegisterFluidModelsEvent.class, event -> {
      Set<FluidType> types = new HashSet<>();
      for (DeferredHolder<FluidType, ? extends FluidType> holder : register.getEntries()) {
        types.add(holder.get());
      }
      // the models read the fluid textures of this reload
      FluidTextureManager.awaitTextures();
      Map<FluidType,FluidModel.Unbaked> models = new HashMap<>();
      for (Fluid fluid : BuiltInRegistries.FLUID) {
        FluidType type = fluid.getFluidType();
        if (types.contains(type) && IClientFluidTypeExtensions.of(type) instanceof ClientTextureFluidType client) {
          // still and flowing fluids share a model
          event.register(models.computeIfAbsent(type, t -> client.createFluidModel()), fluid);
        }
      }
    });
  }
}
