package slimeknights.mantle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;

/**
 * Client-side recipe lookup. Since 1.21.4 the client level has no {@link net.minecraft.world.item.crafting.RecipeManager}
 * ({@code Level#recipeAccess()} is a recipe container for the recipe book), so client code such as book pages reads
 * recipes from the integrated server in singleplayer, or from the {@link RecipeMap} synced through
 * {@link RecipesReceivedEvent} on a remote server (only the recipe types the server sent via
 * {@code OnDatapackSyncEvent#sendRecipes}).
 */
@EventBusSubscriber(modid = Mantle.modId, value = Dist.CLIENT)
public final class ClientRecipes {
  private ClientRecipes() {}

  /** Last recipe map synced from a server */
  private static RecipeMap syncedRecipes = RecipeMap.EMPTY;

  @SubscribeEvent
  static void onRecipesReceived(RecipesReceivedEvent event) {
    syncedRecipes = event.getRecipeMap();
  }

  /** Gets the recipe map visible to the client: the integrated server's in singleplayer, else the last synced map */
  public static RecipeMap getRecipeMap() {
    IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
    if (server != null) {
      return server.getRecipeManager().recipeMap();
    }
    return syncedRecipes;
  }

  /** Looks up a recipe by ID, or null if it is not available on the client */
  @Nullable
  public static Recipe<?> byKey(ResourceLocation id) {
    RecipeHolder<?> holder = getRecipeMap().byKey(ResourceKey.create(Registries.RECIPE, id));
    return holder != null ? holder.value() : null;
  }
}
