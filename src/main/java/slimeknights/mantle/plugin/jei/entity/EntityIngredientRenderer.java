package slimeknights.mantle.plugin.jei.entity;

import lombok.RequiredArgsConstructor;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.rendering.IIngredientRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.recipe.ingredient.EntityIngredient;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renderer for entity type ingredients
 */
@RequiredArgsConstructor
public class EntityIngredientRenderer implements IIngredientRenderer<EntityIngredient.EntityInput> {
  private static final Identifier MISSING = Mantle.getResource("textures/item/missingno.png");
  /** Entity types that will not render, as they either errored or are the wrong type */
  private static final Set<EntityType<?>> IGNORED_ENTITIES = new HashSet<>();

  /** Square size of the renderer in pixels */
  private final int size;

  /** Cache of entities for each entity type */
  private final Map<EntityType<?>,Entity> ENTITY_MAP = new HashMap<>();

  @Override
  public int getWidth() {
    return size;
  }

  @Override
  public int getHeight() {
    return size;
  }

  @Override
  public void render(GuiGraphicsExtractor graphics, EntityIngredient.@Nullable EntityInput input, int x, int y, IIngredientRenderContext context) {
    Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    try {
      pose.translate(x, y);
      renderAtOrigin(graphics, input);
    } finally {
      pose.popMatrix();
    }
  }

  /** JEI 27 still declares this overload abstract, but only calls the positioned one above */
  @SuppressWarnings("removal")
  @Override
  public void render(GuiGraphicsExtractor graphics, EntityIngredient.@Nullable EntityInput input) {
    render(graphics, input, 0, 0, IIngredientRenderContext.INGREDIENT);
  }

  /** Draws the entity in the {@code size} square at the pose origin */
  private void renderAtOrigin(GuiGraphicsExtractor graphics, EntityIngredient.@Nullable EntityInput input) {
    if (input != null) {
      Level world = Minecraft.getInstance().level;
      EntityType<?> type = input.type();
      if (world != null && !IGNORED_ENTITIES.contains(type)) {
        Entity entity;
        // players cannot be created using the type, but we can use the client player
        // side effect is it renders armor/items
        if (type == EntityType.PLAYER) {
          entity = Minecraft.getInstance().player;
        } else {
          // entity is created with the client world, but the entity map is thrown away when JEI restarts so they should be okay I think
          entity = ENTITY_MAP.computeIfAbsent(type, t -> t.create(world, EntitySpawnReason.SPAWN_ITEM_USE));
        }
        // only can draw living entities, plus non-living ones don't get recipes anyways
        if (entity instanceof LivingEntity livingEntity) {
          // scale down large mobs, but don't scale up small ones
          int scale = size / 2;
          float height = entity.getBbHeight();
          float width = entity.getBbWidth();
          if (height > 2 || width > 2) {
            scale = (int)(size / Math.max(height, width));
          }
          // catch exceptions drawing the entity to be safe, any caught exceptions blacklist the entity
          try {
            // 1.21: the entity is drawn in a rectangle (x1,y1,x2,y2), the 0..size square keeps the old anchor (center-x, bottom).
            // 0.0625F is the vanilla y-offset. The fixed tilt is what following a mouse at (0, 10) gave relative to the square.
            // 1.21.8: entities are drawn as a picture-in-picture, which takes absolute screen bounds and ignores the pose JEI
            // translated to the ingredient position, so transform the square to screen space and draw it under an identity pose
            // (the scissor the entity helper enables is also transformed by the pose, so it must see the absolute bounds too).
            Matrix3x2fStack pose = graphics.pose();
            Vector2f min = pose.transformPosition(0, 0, new Vector2f());
            Vector2f max = pose.transformPosition(size, size, new Vector2f());
            float poseScale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
            float angleX = (float) Math.atan((size / 2f) / 40f);
            float angleY = (float) Math.atan((size / 2f - 10) / 40f);
            pose.pushMatrix();
            pose.identity();
            try {
              InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, Math.round(min.x), Math.round(min.y), Math.round(max.x), Math.round(max.y),
                                                                  Math.round(scale * poseScale), 0.0625F, angleX, angleY, livingEntity);
            } finally {
              pose.popMatrix();
            }
            return;
          } catch (Exception e) {
            Mantle.logger.error("Error drawing entity " + BuiltInRegistries.ENTITY_TYPE.getKey(type), e);
            IGNORED_ENTITIES.add(type);
            ENTITY_MAP.remove(type);
          }
        } else {
          // not living, so might as well skip next time
          IGNORED_ENTITIES.add(type);
          ENTITY_MAP.remove(type);
        }
      }

      // fallback, draw a pink and black "spawn egg"
      // 1.21.8: GuiGraphicsExtractor.blit takes a render pipeline, and the color is part of each draw (white by default) rather than a global shader color
      int offset = (size - 16) / 2;
      graphics.blit(RenderPipelines.GUI_TEXTURED, MISSING, offset, offset, 0.0F, 0.0F, 16, 16, 16, 16);
    }
  }

  @Override
  public List<Component> getTooltip(EntityIngredient.EntityInput type, Item.TooltipContext context, @Nullable Player player, TooltipFlag flag) {
    List<Component> tooltip = new ArrayList<>();
    tooltip.add(type.type().getDescription());
    if (flag.isAdvanced()) {
      tooltip.add((Component.literal(BuiltInRegistries.ENTITY_TYPE.getKey(type.type()).toString())).withStyle(ChatFormatting.DARK_GRAY));
    }
    return tooltip;
  }

  /** JEI 27 still declares this overload abstract, but only calls the one with a tooltip context above */
  @SuppressWarnings("removal")
  @Override
  public List<Component> getTooltip(EntityIngredient.EntityInput type, TooltipFlag flag) {
    return getTooltip(type, Item.TooltipContext.EMPTY, null, flag);
  }
}
