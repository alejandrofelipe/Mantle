# Fixture APIs shared by Test-FindApiRenames.ps1 and Test-CompilerRenames.ps1 (dot-source it): a tiny "old" and "new"
# version of a Minecraft-like API under net.minecraft.fix (the default --prefix of FindApiRenames.java), with one case
# per matching rule: moved class, renamed outer class (inner-class votes), cross-package rename, enum rename, the
# Material -> SpriteId semantic swap, a reused FQN (BlockModel -> CuboidModel), member renames incl. a same-descriptor
# collision (render / renderBackground), a static field rename and a removed abstract method (renderBg).

$OldApi = @{
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
  'net/minecraft/fix/a/Thing.java'            = 'package net.minecraft.fix.a; public class Thing { public int size() { return 0; } public String name() { return ""; } public void tick() {} }'
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
