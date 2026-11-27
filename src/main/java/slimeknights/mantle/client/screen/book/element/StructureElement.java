package slimeknights.mantle.client.screen.book.element;

import com.mojang.math.Transformation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.joml.AxisAngle4f;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import slimeknights.mantle.client.book.structure.StructureInfo;
import slimeknights.mantle.client.book.structure.level.TemplateLevel;
import slimeknights.mantle.client.screen.book.BookScreen;
import slimeknights.mantle.client.screen.book.element.pip.StructureRenderState;

import java.util.List;
import java.util.stream.IntStream;

public class StructureElement extends SizedBookElement {

  public boolean canTick = false;

  public float scale = 50f;
  public float transX = 0;
  public float transY = 0;
  public Transformation additionalTransform;
  public final StructureInfo renderInfo;
  public final TemplateLevel structureWorld;

  public long lastStep = -1;

  public StructureElement(int x, int y, int width, int height, StructureTemplate template, List<StructureTemplate.StructureBlockInfo> structure) {
    super(x, y, width, height);

    int[] size = {template.getSize().getX(), template.getSize().getY(), template.getSize().getZ()};

    this.scale = 100f / (float) IntStream.of(size).max().getAsInt();

    float sx = (float) width / (float) BookScreen.PAGE_WIDTH;
    float sy = (float) height / (float) BookScreen.PAGE_HEIGHT;

    this.scale *= Math.min(sx, sy);

    this.renderInfo = new StructureInfo(structure);

    this.structureWorld = new TemplateLevel(structure, renderInfo);

    this.transX = x + width / 2F;
    this.transY = y + height / 2F;

    this.additionalTransform = new Transformation(null, new Quaternionf().rotateYXZ(0, (float)(25 * Math.PI / 180f), 0), null, new Quaternionf().rotateYXZ((float)(-45 * Math.PI / 180f), 0, 0));
  }

  @Override
  public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, Font fontRenderer) {
    long currentTime = System.currentTimeMillis();

    if (this.lastStep < 0)
      this.lastStep = currentTime;
    else if (this.canTick && currentTime - this.lastStep > 200) {
      this.renderInfo.step();
      this.lastStep = currentTime;
    }

    if (!this.canTick) {
      this.renderInfo.reset();
    }

    // 1.21.8: GUIs are deferred, so the structure is drawn by StructurePiPRenderer into its own texture and blitted by the GUI.
    // Picture-in-picture bounds are absolute and ignore the pose, so move the page position and scale to screen space here.
    Matrix3x2fStack pose = graphics.pose();
    Vector2f center = pose.transformPosition(this.transX, this.transY, new Vector2f());
    float screenScale = this.scale * (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
    // the old drawing was not clipped to the element, so size the area to fit the structure at any rotation
    int length = this.renderInfo.structureLength;
    int height = this.renderInfo.structureHeight;
    int width = this.renderInfo.structureWidth;
    int radius = Mth.ceil(Math.sqrt(length * length + height * height + width * width) / 2 * screenScale) + 1;
    int centerX = Math.round(center.x);
    int centerY = Math.round(center.y);
    graphics.submitPictureInPictureRenderState(new StructureRenderState(
      this.structureWorld, this.renderInfo, this.additionalTransform, center.x - centerX, center.y - centerY,
      centerX - radius, centerY - radius, centerX + radius, centerY + radius, screenScale, graphics.peekScissorStack()));
  }

  @Override
  public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
    super.mouseClicked(mouseX, mouseY, mouseButton);
  }

  @Override
  public void mouseDragged(double clickX, double clickY, double mouseX, double mouseY, double lastX, double lastY, int button) {
    double dx = mouseX - lastX;
    double dy = mouseY - lastY;
    this.additionalTransform = forRotation(dx * 80D / 104, dy * 0.8).compose(this.additionalTransform);
  }

  @Override
  public void mouseReleased(double mouseX, double mouseY, int clickedMouseButton) {
    super.mouseReleased(mouseX, mouseY, clickedMouseButton);
  }

  private Transformation forRotation(double rX, double rY) {
    Vector3f axis = new Vector3f((float) rY, (float) rX, 0);
    float dot = axis.dot(axis);
    if (dot < Float.MIN_NORMAL) {
      return Transformation.IDENTITY;
    }

    float angle = (float) (Math.sqrt(axis.dot(axis)) * Math.PI / 180f);
    axis.normalize();
    return new Transformation(null, new Quaternionf(new AxisAngle4f(angle, axis)), null, null);
  }
}
