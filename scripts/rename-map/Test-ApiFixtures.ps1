# Fixture APIs shared by Test-FindApiRenames.ps1 and Test-CompilerRenames.ps1 (dot-source it): a tiny "old" and "new"
# version of a Minecraft-like API under net.minecraft.fix (the default --prefix of FindApiRenames.java), with one case
# per matching rule: moved class, renamed outer class (inner-class votes), cross-package rename, enum rename, the
# Material -> SpriteId semantic swap, a reused FQN (BlockModel -> CuboidModel), member renames incl. a same-descriptor
# collision (render / renderBackground), a static field rename, a removed abstract method (renderBg) and a same-name swap
# (Types: cutoutNoCull -> cutout while the old cutout became cutoutCull; decal removed; cutoutAlt removed with the same
# descriptor as the reserved cutoutCull, so it must not be paired with it).
# M8 additions: static fields moved to another class (EntityType.PIG -> EntityTypes.PIG), a static field pulled up into a
# supertype (Slime.MAX_SIZE -> Cube, no rule), constants folded into collections (Items.<COLOR>_DYE ->
# Items.DYE.pick(DyeColor.<COLOR>), Items.[WAXED_][EXPOSED_|...]COPPER -> Items.COPPER_BLOCK.weathering()/.waxed() accessors,
# COPPER_BLOCK keeps its name with a new type), and members moved behind another object (Minecraft#screen ->
# gui.screen(), #setScreen -> gui.setScreen, #getMainRenderTarget -> gameRenderer.mainRenderTarget) for 'moved' lines.
# 0.5a follow-ups: a DYED_ collection (Items.<COLOR>_TERRACOTTA -> Items.DYED_TERRACOTTA.pick), an incomplete copper family
# (COPPER_BARS was already a CopperItems record: warning, no rule) and a weak moved field (Pond.CODEC -> Serializers.CODEC).

$FixColors = 'WHITE', 'ORANGE', 'MAGENTA', 'LIGHT_BLUE', 'YELLOW', 'LIME', 'PINK', 'GRAY', 'LIGHT_GRAY', 'CYAN', 'PURPLE', 'BLUE', 'BROWN', 'GREEN', 'RED', 'BLACK'
$FixCopper = 'COPPER_BLOCK', 'EXPOSED_COPPER', 'WEATHERED_COPPER', 'OXIDIZED_COPPER', 'WAXED_COPPER_BLOCK', 'WAXED_EXPOSED_COPPER', 'WAXED_WEATHERED_COPPER', 'WAXED_OXIDIZED_COPPER'
$oldItemFields = ((@('STICK') + ($FixColors | ForEach-Object { "${_}_DYE" }) + ($FixColors | ForEach-Object { "${_}_TERRACOTTA" }) + $FixCopper | ForEach-Object { "  public static final Item $_ = new Item();" }) + '  public static final CopperItems COPPER_BARS = null;') -join "`n"

$OldApi = @{
  'net/minecraft/fix/Item.java'               = 'package net.minecraft.fix; public class Item {}'
  'net/minecraft/fix/CopperItems.java'        = 'package net.minecraft.fix; public record CopperItems(Item unaffected, Item exposed, Item weathered, Item oxidized) {}'
  'net/minecraft/fix/Pond.java'               = 'package net.minecraft.fix; public class Pond { public static final String CODEC = ""; public void fill() {} }'
  'net/minecraft/fix/Items.java'              = "package net.minecraft.fix;`npublic class Items {`n$oldItemFields`n}"
  'net/minecraft/fix/EntityType.java'         = 'package net.minecraft.fix; public class EntityType { public static final EntityType PIG = new EntityType(); public static final EntityType COW = new EntityType(); public static final int UNRELATED_COUNT = 3; public String id() { return ""; } }'
  'net/minecraft/fix/Slime.java'              = 'package net.minecraft.fix; public class Slime { public static final int MAX_SIZE = 4; public int size() { return 1; } }'
  'net/minecraft/fix/Minecraft.java'          = 'package net.minecraft.fix; public class Minecraft { public Screen screen; public static Minecraft getInstance() { return null; } public void setScreen(Screen s) {} public Object getMainRenderTarget() { return null; } public void tick() {} }'
  'net/minecraft/fix/Graphics.java'           = @'
package net.minecraft.fix;
public class Graphics {
  public static final int OLD_CONST = 1;
  public void drawString(String s, int x) {}
  public void fill(int a, int b) {}
  public void blit(String id, int x, int y) {}
  public Pose pose() { return new Pose(); }
  public static class Pose { public void push() {} public void pop() {} }
  public static class Scissor { public void enable() {} }
}
'@
  'net/minecraft/fix/Renderable.java'         = @'
package net.minecraft.fix;
public interface Renderable { void render(Graphics g, int x, int y, float pt); }
'@
  'net/minecraft/fix/Screen.java'             = @'
package net.minecraft.fix;
public abstract class Screen implements Renderable {
  public void render(Graphics g, int x, int y, float pt) {}
  public void renderBackground(Graphics g, int x, int y, float pt) {}
  protected void renderLabels(Graphics g, int x, int y) {}
  protected abstract void renderBg(Graphics g, float pt, int x, int y);
  public void init() {}
}
'@
  'net/minecraft/fix/Other.java'              = 'package net.minecraft.fix; public class Other { public void drawString(String s, int x) {} }'
  'net/minecraft/fix/Unrelated.java'          = 'package net.minecraft.fix; public class Unrelated { public void paint() {} }'
  'net/minecraft/fix/Types.java'              = 'package net.minecraft.fix; public class Types { public static String cutout(String t) { return t; } public static String cutoutNoCull(String t) { return t; } public static String decal(String t) { return t; } public static String cutoutAlt(String t) { return t; } }'
  'net/minecraft/fix/a/Thing.java'           = 'package net.minecraft.fix.a; public class Thing { public int size() { return 0; } public String name() { return ""; } public void tick() {} }'
  'net/minecraft/fix/a/Gone.java'             = 'package net.minecraft.fix.a; public class Gone { public void x() {} }'
  'net/minecraft/fix/model/BlockElement.java' = @'
package net.minecraft.fix.model;
public class BlockElement {
  public float[] from; public float[] to; public boolean shade; public int lightEmission;
  public float[] from() { return from; } public float[] to() { return to; } public boolean shade() { return shade; } public int lightEmission() { return lightEmission; }
}
'@
  'net/minecraft/fix/model/Material.java'     = @'
package net.minecraft.fix.model;
public record Material(String atlasLocation, String texture) {
  public static final java.util.Comparator<Material> COMPARATOR = null;
  public Object renderType(java.util.function.Function<String, Object> f) { return null; }
  public Object buffer(Object source, java.util.function.Function<String, Object> f) { return null; }
}
'@
  'net/minecraft/fix/inv/ClickType.java'      = 'package net.minecraft.fix.inv; public enum ClickType { PICKUP, QUICK_MOVE, SWAP, CLONE, THROW }'
  'net/minecraft/fix/BlockModel.java'         = @'
package net.minecraft.fix;
public record BlockModel(String parent, Object geometry, Boolean ambientOcclusion, Object transforms) {
  public static BlockModel fromStream(java.io.Reader reader) { return null; }
  public void fillAdditionalProperties(Object builder) {}
}
'@
}

$NewApi = @{
  'net/minecraft/fix/Item.java'                       = 'package net.minecraft.fix; public class Item {}'
  'net/minecraft/fix/CopperItems.java'                = 'package net.minecraft.fix; public record CopperItems(Item unaffected, Item exposed, Item weathered, Item oxidized) {}'
  'net/minecraft/fix/Pond.java'                       = 'package net.minecraft.fix; public class Pond { public void fill() {} }'
  'net/minecraft/fix/Serializers.java'                = 'package net.minecraft.fix; public class Serializers { public static final String CODEC = ""; }'
  'net/minecraft/fix/DyeColor.java'                   = "package net.minecraft.fix; public enum DyeColor { $($FixColors -join ', ') }"
  'net/minecraft/fix/ColorCollection.java'            = 'package net.minecraft.fix; public record ColorCollection<T>(T white) { public T pick(DyeColor c) { return white; } }'
  'net/minecraft/fix/WeatheringCopperCollection.java' = 'package net.minecraft.fix; public record WeatheringCopperCollection<T>(ByState<T> weathering, ByState<T> waxed) { public record ByState<T>(T unaffected, T exposed, T weathered, T oxidized) {} }'
  'net/minecraft/fix/Items.java'                      = 'package net.minecraft.fix; public class Items { public static final Item STICK = new Item(); public static final ColorCollection<Item> DYE = null; public static final ColorCollection<Item> DYED_TERRACOTTA = null; public static final WeatheringCopperCollection<Item> COPPER_BLOCK = null; public static final WeatheringCopperCollection<Item> COPPER_BARS = null; }'
  'net/minecraft/fix/EntityType.java'                 = 'package net.minecraft.fix; public class EntityType { public String id() { return ""; } }'
  'net/minecraft/fix/EntityTypes.java'                = 'package net.minecraft.fix; public class EntityTypes { public static final EntityType PIG = new EntityType(); public static final EntityType COW = new EntityType(); }'
  'net/minecraft/fix/Cube.java'                       = 'package net.minecraft.fix; public class Cube { public static final int MAX_SIZE = 4; public int size() { return 1; } }'
  'net/minecraft/fix/Slime.java'                      = 'package net.minecraft.fix; public class Slime extends Cube { public void split() {} }'
  'net/minecraft/fix/Gui.java'                        = 'package net.minecraft.fix; public class Gui { public Screen screen() { return null; } public void setScreen(Screen s) {} }'
  'net/minecraft/fix/GameRenderer.java'               = 'package net.minecraft.fix; public class GameRenderer { public Object mainRenderTarget() { return null; } }'
  'net/minecraft/fix/Minecraft.java'                  = 'package net.minecraft.fix; public class Minecraft { public final Gui gui = new Gui(); public final GameRenderer gameRenderer = new GameRenderer(); public static Minecraft getInstance() { return null; } public void tick() {} }'
  'net/minecraft/fix/GraphicsExtractor.java'          = @'
package net.minecraft.fix;
public class GraphicsExtractor {
  public static final int NEW_CONST = 1;
  public void text(String s, int x) {}
  public void fill(int a, int b) {}
  public void blit(String id, int x, int y) {}
  public Pose pose() { return new Pose(); }
  public static class Pose { public void push() {} public void pop() {} }
  public static class Scissor { public void enable() {} }
}
'@
  'net/minecraft/fix/Renderable.java'                 = @'
package net.minecraft.fix;
public interface Renderable { void extractRenderState(GraphicsExtractor g, int x, int y, float pt); }
'@
  'net/minecraft/fix/Screen.java'                     = @'
package net.minecraft.fix;
public abstract class Screen implements Renderable {
  public void extractRenderState(GraphicsExtractor g, int x, int y, float pt) {}
  public void extractBackground(GraphicsExtractor g, int x, int y, float pt) {}
  protected void extractLabels(GraphicsExtractor g, int x, int y) {}
  public void init() {}
}
'@
  'net/minecraft/fix/Other.java'                      = 'package net.minecraft.fix; public class Other { public void drawString(String s, int x) {} }'
  'net/minecraft/fix/Unrelated.java'                  = 'package net.minecraft.fix; public class Unrelated { public void paint() {} }'
  'net/minecraft/fix/Types.java'                      = 'package net.minecraft.fix; public class Types { public static String cutoutCull(String t) { return t; } public static String cutout(String t) { return t; } }'
  'net/minecraft/fix/b/Thing.java'                    = 'package net.minecraft.fix.b; public class Thing { public int size() { return 0; } public String name() { return ""; } public void tick() {} }'
  'net/minecraft/fix/b/Replacement.java'              = 'package net.minecraft.fix.b; public class Replacement { public void y(int a) {} }'
  'net/minecraft/fix/cuboid/CuboidModelElement.java'  = @'
package net.minecraft.fix.cuboid;
public class CuboidModelElement {
  public float[] from; public float[] to; public boolean shade; public int lightEmission;
  public float[] from() { return from; } public float[] to() { return to; } public boolean shade() { return shade; } public int lightEmission() { return lightEmission; }
}
'@
  'net/minecraft/fix/model/sprite/Material.java'      = 'package net.minecraft.fix.model.sprite; public record Material(String sprite, boolean forceTranslucent) {}'
  'net/minecraft/fix/model/sprite/SpriteId.java'      = @'
package net.minecraft.fix.model.sprite;
public record SpriteId(String atlasLocation, String texture) {
  public static final java.util.Comparator<SpriteId> COMPARATOR = null;
  public Object renderType(java.util.function.Function<String, Object> f) { return null; }
  public Object buffer(Object source, java.util.function.Function<String, Object> f) { return null; }
}
'@
  'net/minecraft/fix/inv/ContainerInput.java'         = 'package net.minecraft.fix.inv; public enum ContainerInput { PICKUP, QUICK_MOVE, SWAP, CLONE, THROW }'
  'net/minecraft/fix/BlockModel.java'                 = 'package net.minecraft.fix; public interface BlockModel { void update(Object state, long seed); }'
  'net/minecraft/fix/cuboid/CuboidModel.java'         = @'
package net.minecraft.fix.cuboid;
public record CuboidModel(String parent, Object geometry, Boolean ambientOcclusion, Object transforms) {
  public static CuboidModel fromStream(java.io.Reader reader) { return null; }
  public void fillAdditionalProperties(Object builder) {}
}
'@
}
